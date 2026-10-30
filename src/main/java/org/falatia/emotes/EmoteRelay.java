package org.falatia.emotes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.falatia.emotes.EmoteSync.EmotePayload;

import org.jetbrains.annotations.Nullable;

final class EmoteRelay {

    private static final int PACKETS_PER_SECOND = 40;
    private static final int UPLOADS_PER_MINUTE = 20;
    private static final long CACHE_LIMIT = 16L * 1024L * 1024L;
    private static final int MAX_UPLOAD = 32767;
    private static final double PAIR_DISTANCE = 2.5;
    private static final double ANCHOR_SLACK = 3.0;

    private static final class Active {
        final String id;
        final long hash;
        final int startTick;
        final boolean invite;
        final double x;
        final double y;
        final double z;
        final float yaw;
        UUID partner;
        int joinTick;

        Active(String id, long hash, int startTick, boolean invite, double x, double y, double z, float yaw) {
            this.id = id;
            this.hash = hash;
            this.startTick = startTick;
            this.invite = invite;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }

    private static final Map<UUID, Active> active = new HashMap<>();
    private static final Map<UUID, UUID> partnerOf = new HashMap<>();
    private static final LinkedHashMap<Long, byte[]> files = new LinkedHashMap<>(16, 0.75F, true);
    private static long filesBytes;
    private static final Map<UUID, Integer> packets = new HashMap<>();
    private static final Map<UUID, Integer> uploads = new HashMap<>();
    private static final List<Runnable> nextTick = new ArrayList<>();
    private static @Nullable MinecraftServer server;
    private static int tick;

    private EmoteRelay() {
    }

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(EmotePayload.TYPE, (payload, context) -> receive(context.player(), payload));
        EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
            if (entity instanceof ServerPlayer player && busy(player.getUUID())) {
                nextTick.add(() -> {
                    if (busy(player.getUUID()) && PlayerLookup.tracking(player).contains(viewer)) {
                        sendState(viewer, player);
                    }
                });
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> quit(handler.getPlayer())));
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> clear());
        ServerTickEvents.END_SERVER_TICK.register(EmoteRelay::tick);
    }

    private static void tick(MinecraftServer current) {
        tick++;
        if (tick % 20 == 0) {
            packets.clear();
        }
        if (tick % 1200 == 0) {
            uploads.clear();
        }
        if (!nextTick.isEmpty()) {
            List<Runnable> tasks = new ArrayList<>(nextTick);
            nextTick.clear();
            tasks.forEach(Runnable::run);
        }
    }

    private static void clear() {
        active.clear();
        partnerOf.clear();
        files.clear();
        filesBytes = 0;
        packets.clear();
        uploads.clear();
        nextTick.clear();
        server = null;
    }

    private static void receive(ServerPlayer player, EmotePayload payload) {
        if (packets.merge(player.getUUID(), 1, Integer::sum) > PACKETS_PER_SECOND) {
            return;
        }
        switch (payload.action()) {
            case EmoteSync.PLAY -> play(player, payload.id(), payload.hash());
            case EmoteSync.INVITE -> invite(player, payload);
            case EmoteSync.JOIN -> {
                if (payload.player() != null) {
                    join(player, payload.player());
                }
            }
            case EmoteSync.STOP -> stop(player);
            case EmoteSync.DATA -> store(player, payload.hash(), payload.data());
            case EmoteSync.REQUEST -> {
                byte[] data = files.get(payload.hash());
                if (data != null) {
                    send(player, new EmotePayload(EmoteSync.DATA, null, null, "", payload.hash(), data, 0, 0.0, 0.0, 0.0, 0.0F));
                }
            }
            default -> {
            }
        }
    }

    private static void play(ServerPlayer player, String id, long hash) {
        leavePair(player);
        active.put(player.getUUID(), new Active(id, hash, tick, false, player.getX(), player.getY(), player.getZ(), player.getYRot()));
        for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
            sendState(viewer, player);
        }
    }

    private static void invite(ServerPlayer player, EmotePayload payload) {
        leavePair(player);
        double x = payload.x();
        double y = payload.y();
        double z = payload.z();
        float yaw = payload.yaw();
        boolean near = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Float.isFinite(yaw)
                && player.distanceToSqr(x, y, z) <= ANCHOR_SLACK * ANCHOR_SLACK;
        Active emote = near
                ? new Active(payload.id(), payload.hash(), tick, true, x, y, z, yaw)
                : new Active(payload.id(), payload.hash(), tick, true, player.getX(), player.getY(), player.getZ(), player.getYRot());
        active.put(player.getUUID(), emote);
        for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
            sendState(viewer, player);
        }
    }

    private static void join(ServerPlayer joiner, UUID leaderId) {
        ServerPlayer leader = player(leaderId);
        Active emote = active.get(leaderId);
        if (leader == null || leader == joiner || emote == null || !emote.invite || emote.partner != null
                || leader.level() != joiner.level()
                || joiner.distanceToSqr(leader) > PAIR_DISTANCE * PAIR_DISTANCE
                || !joiner.hasLineOfSight(leader)) {
            return;
        }
        stop(joiner);
        emote.partner = joiner.getUUID();
        emote.joinTick = tick;
        partnerOf.put(joiner.getUUID(), leaderId);
        for (ServerPlayer viewer : pairAudience(leader, joiner)) {
            sendPaired(viewer, leader.getUUID(), emote);
        }
    }

    private static void stop(ServerPlayer player) {
        UUID id = player.getUUID();
        UUID leader = partnerOf.get(id);
        Active own = active.get(id);
        if (leader != null) {
            endPair(leader);
        } else if (own != null && own.partner != null) {
            endPair(id);
        } else if (active.remove(id) != null) {
            EmotePayload packet = stopPacket(id);
            for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
                if (sees(viewer, player)) {
                    send(viewer, packet);
                }
            }
        }
    }

    private static void quit(ServerPlayer player) {
        UUID id = player.getUUID();
        if (partnerOf.containsKey(id) || active.containsKey(id) && active.get(id).partner != null) {
            stop(player);
        } else if (active.remove(id) != null && server != null) {
            EmotePayload packet = stopPacket(id);
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                if (sees(viewer, player)) {
                    send(viewer, packet);
                }
            }
        }
        packets.remove(id);
        uploads.remove(id);
    }

    private static void leavePair(ServerPlayer player) {
        UUID leader = partnerOf.get(player.getUUID());
        Active own = active.get(player.getUUID());
        if (leader != null || own != null && own.partner != null) {
            stop(player);
        }
    }

    private static void endPair(UUID leaderId) {
        Active emote = active.remove(leaderId);
        if (emote == null || emote.partner == null) {
            return;
        }
        partnerOf.remove(emote.partner);
        ServerPlayer leader = player(leaderId);
        ServerPlayer partner = player(emote.partner);
        EmotePayload leaderStop = stopPacket(leaderId);
        EmotePayload partnerStop = stopPacket(emote.partner);
        for (ServerPlayer viewer : pairAudience(leader, partner)) {
            if (viewer != leader) {
                send(viewer, leaderStop);
            }
            if (viewer != partner) {
                send(viewer, partnerStop);
            }
        }
    }

    private static Set<ServerPlayer> pairAudience(@Nullable ServerPlayer leader, @Nullable ServerPlayer partner) {
        Set<ServerPlayer> audience = new LinkedHashSet<>();
        for (ServerPlayer member : new ServerPlayer[] {leader, partner}) {
            if (member == null) {
                continue;
            }
            if (!member.hasDisconnected() && ServerPlayNetworking.canSend(member, EmotePayload.TYPE)) {
                audience.add(member);
            }
            for (ServerPlayer viewer : PlayerLookup.tracking(member)) {
                if (sees(viewer, member)) {
                    audience.add(viewer);
                }
            }
        }
        return audience;
    }

    private static EmotePayload stopPacket(UUID player) {
        return new EmotePayload(EmoteSync.STOP, player, null, "", 0L, new byte[0], 0, 0.0, 0.0, 0.0, 0.0F);
    }

    private static void store(ServerPlayer player, long hash, byte[] data) {
        if (data.length > MAX_UPLOAD || files.containsKey(hash)
                || uploads.merge(player.getUUID(), 1, Integer::sum) > UPLOADS_PER_MINUTE) {
            return;
        }
        files.put(hash, data);
        filesBytes += data.length;
        Iterator<byte[]> iterator = files.values().iterator();
        while (filesBytes > CACHE_LIMIT && iterator.hasNext()) {
            filesBytes -= iterator.next().length;
            iterator.remove();
        }
    }

    private static void sendState(ServerPlayer viewer, ServerPlayer player) {
        if (!sees(viewer, player)) {
            return;
        }
        UUID leaderId = partnerOf.get(player.getUUID());
        if (leaderId != null) {
            Active emote = active.get(leaderId);
            if (emote != null) {
                sendPaired(viewer, leaderId, emote);
            }
            return;
        }
        Active emote = active.get(player.getUUID());
        if (emote == null) {
            return;
        }
        if (emote.partner != null) {
            sendPaired(viewer, player.getUUID(), emote);
            return;
        }
        int elapsed = Math.max(0, tick - emote.startTick);
        send(viewer, emote.invite
                ? new EmotePayload(EmoteSync.INVITE, player.getUUID(), null, emote.id, emote.hash, new byte[0], elapsed,
                        emote.x, emote.y, emote.z, emote.yaw)
                : new EmotePayload(EmoteSync.PLAY, player.getUUID(), null, emote.id, emote.hash, new byte[0], elapsed,
                        0.0, 0.0, 0.0, 0.0F));
    }

    private static void sendPaired(ServerPlayer viewer, UUID leader, Active emote) {
        int elapsed = Math.max(0, tick - emote.joinTick);
        send(viewer, new EmotePayload(EmoteSync.PAIRED, leader, emote.partner, emote.id, emote.hash, new byte[0], elapsed,
                emote.x, emote.y, emote.z, emote.yaw));
    }

    private static boolean sees(ServerPlayer viewer, ServerPlayer player) {
        return viewer != player && !viewer.hasDisconnected() && ServerPlayNetworking.canSend(viewer, EmotePayload.TYPE);
    }

    private static void send(ServerPlayer viewer, EmotePayload payload) {
        ServerPlayNetworking.send(viewer, payload);
    }

    private static boolean busy(UUID player) {
        return active.containsKey(player) || partnerOf.containsKey(player);
    }

    private static @Nullable ServerPlayer player(UUID id) {
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }
}
