package org.falatia.emotes;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.falatia.emotes.emotestypes.EmoteLoader;
import org.jetbrains.annotations.Nullable;

public final class EmoteSync {

    private static final int PROTOCOL = 1;

    static final int PLAY = 0;
    static final int STOP = 1;
    static final int DATA = 2;
    static final int REQUEST = 3;
    static final int INVITE = 4;
    static final int JOIN = 5;
    static final int PAIRED = 6;
    static final int CATALOG = 7;
    static final int FILE = 8;

    private static final int MAX_ID = 64;
    private static final int MAX_DATA = 1 << 20;
    private static final long WAIT_MS = 10_000L;
    private static final int MAX_PATH = 256;
    private static final int MAX_SERVER_FILES = 1024;
    private static final int MAX_SERVER_BYTES = 64 * 1024 * 1024;
    private static final int MAX_SERVER_FILE = 8 * 1024 * 1024;

    private static final Set<Long> uploaded = new HashSet<>();
    private static final Map<Long, Emote> received = new HashMap<>();
    private static final Set<Long> requested = new HashSet<>();
    private static final Map<UUID, Waiting> waiting = new HashMap<>();

    private static final Map<String, byte[]> serverFiles = new LinkedHashMap<>();
    private static int serverExpected;
    private static long serverBytes;

    private record Waiting(EmotePayload payload, long since) {
    }

    private EmoteSync() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(EmotePayload.TYPE, EmotePayload.TO_SERVER);
        PayloadTypeRegistry.clientboundPlay().register(EmotePayload.TYPE, EmotePayload.TO_CLIENT);
        ClientPlayNetworking.registerGlobalReceiver(EmotePayload.TYPE, (payload, context) -> receive(payload));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(EmoteSync::reset));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(EmoteSync::reset));
    }

    private static void reset() {
        uploaded.clear();
        requested.clear();
        waiting.clear();
        serverFiles.clear();
        serverExpected = 0;
        serverBytes = 0;
    }

    static void played(Emote emote) {
        if (upload(emote)) {
            send(EmotePayload.toServer(PLAY, emote.id(), emote.source().hash()));
        }
    }

    static void invited(Emote emote, Vec3 anchor, float yaw) {
        if (upload(emote)) {
            send(new EmotePayload(INVITE, null, null, emote.id(), emote.source().hash(), new byte[0], 0, anchor.x, anchor.y, anchor.z, yaw));
        }
    }

    public static void join(UUID leader) {
        if (ClientPlayNetworking.canSend(EmotePayload.TYPE)) {
            send(new EmotePayload(JOIN, leader, null, "", 0L, new byte[0], 0, 0.0, 0.0, 0.0, 0.0F));
        }
    }

    private static boolean upload(Emote emote) {
        EmoteSource source = emote.source();
        if (source == null || !ClientPlayNetworking.canSend(EmotePayload.TYPE)) {
            return false;
        }
        if (source.shareable() && uploaded.add(source.hash())) {
            send(new EmotePayload(DATA, null, null, "", source.hash(), source.packed(), 0, 0.0, 0.0, 0.0, 0.0F));
        }
        return true;
    }

    static void stopped() {
        if (ClientPlayNetworking.canSend(EmotePayload.TYPE)) {
            send(EmotePayload.toServer(STOP, "", 0L));
        }
    }

    private static void send(EmotePayload payload) {
        ClientPlayNetworking.send(payload);
    }

    private static void receive(EmotePayload payload) {
        switch (payload.action()) {
            case PLAY, INVITE, PAIRED -> {
                Emote emote = emote(payload.hash());
                if (emote != null) {
                    apply(payload, emote, 0);
                } else {
                    waiting.put(payload.player(), new Waiting(payload, System.currentTimeMillis()));
                    if (requested.add(payload.hash()) && ClientPlayNetworking.canSend(EmotePayload.TYPE)) {
                        send(EmotePayload.toServer(REQUEST, "", payload.hash()));
                    }
                }
            }
            case STOP -> {
                waiting.remove(payload.player());
                if (payload.player() != null && payload.player().equals(Emotes.mate())) {
                    Emotes.stop();
                }
                Emotes.stopRemote(payload.player());
            }
            case DATA -> {
                Emote emote = EmoteSource.unpack(payload.hash(), payload.data());
                if (emote == null) {
                    FalatiaEmotes.LOGGER.warn("Emotes: received a broken emote {}", Long.toHexString(payload.hash()));
                    return;
                }
                received.put(payload.hash(), emote);
                long now = System.currentTimeMillis();
                waiting.entrySet().removeIf(entry -> {
                    if (entry.getValue().payload().hash() == payload.hash()) {
                        apply(entry.getValue().payload(), emote, (int) ((now - entry.getValue().since()) / 50L));
                        return true;
                    }
                    return now - entry.getValue().since() > WAIT_MS;
                });
            }
            case CATALOG -> {
                serverFiles.clear();
                serverBytes = 0;
                serverExpected = Math.min(payload.elapsed(), MAX_SERVER_FILES);
                if (serverExpected <= 0) {
                    Emotes.setServer(Map.of());
                }
            }
            case FILE -> serverFile(payload.id(), payload.data());
            default -> {
            }
        }
    }

    private static void serverFile(String path, byte[] packed) {
        if (serverExpected <= 0) {
            return;
        }
        serverExpected--;
        try {
            byte[] file = EmoteSource.unzip(packed, MAX_SERVER_FILE);
            serverBytes += file.length;
            if (serverBytes <= MAX_SERVER_BYTES) {
                serverFiles.put(path, file);
            } else {
                FalatiaEmotes.LOGGER.warn("Emotes: server emotes are over {} MB, {} skipped", MAX_SERVER_BYTES >> 20, path);
            }
        } catch (IOException e) {
            FalatiaEmotes.LOGGER.warn("Emotes: broken server emote file {}: {}", path, e.toString());
        }
        if (serverExpected == 0) {
            Emotes.setServer(EmoteLoader.loadServer(serverFiles));
            serverFiles.clear();
            serverBytes = 0;
        }
    }

    private static @Nullable Emote emote(long hash) {
        Emote own = Emotes.byHash(hash);
        return own != null ? own : received.get(hash);
    }

    private static void apply(EmotePayload payload, Emote emote, int late) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.player() == null || minecraft.level == null) {
            return;
        }
        int elapsed = Math.max(0, payload.elapsed() + late);
        switch (payload.action()) {
            case PLAY -> {
                if (emote.loop() == Emote.Loop.NONE && elapsed >= emote.length() * 20.0F) {
                    return;
                }
                Player player = minecraft.level.getPlayerByUUID(payload.player());
                if (player != null && player != minecraft.player) {
                    Emotes.start(player, emote, elapsed);
                }
            }
            case INVITE -> {
                Player leader = minecraft.level.getPlayerByUUID(payload.player());
                if (leader != null && leader != minecraft.player && emote.pair() != null) {
                    Emotes.startWaiting(leader, emote, elapsed, payload.yaw());
                }
            }
            case PAIRED -> paired(minecraft, payload, emote, elapsed);
            default -> {
            }
        }
    }

    private static void paired(Minecraft minecraft, EmotePayload payload, Emote emote, int elapsed) {
        Emote.Pair pair = emote.pair();
        if (pair == null || payload.partner() == null) {
            return;
        }
        float seconds = pair.waitEnd() + elapsed / 20.0F;
        if (seconds >= emote.length() && emote.loop() == Emote.Loop.NONE) {
            return;
        }
        float facing = payload.yaw() + pair.facing();
        Player leader = minecraft.level.getPlayerByUUID(payload.player());
        Player partner = minecraft.level.getPlayerByUUID(payload.partner());
        LocalPlayer self = minecraft.player;
        if (leader != null) {
            if (leader == self) {
                EmotePair.joined();
            }
            Emotes.startRole(leader, emote, seconds, payload.yaw(), payload.partner());
        }
        if (partner != null) {
            if (partner == self) {
                Vec3 at = EmotePair.partnerPosition(new Vec3(payload.x(), payload.y(), payload.z()), payload.yaw(), pair);
                if (minecraft.level.noCollision(self, self.getBoundingBox().move(at.subtract(self.position())))) {
                    self.snapTo(at.x, at.y, at.z, facing, self.getXRot());
                } else {
                    self.setYRot(facing);
                }
                self.setYBodyRot(facing);
                self.setYHeadRot(facing);
            }
            Emotes.startRole(partner, pair.partner(), seconds, facing, payload.player());
        }
    }

    record EmotePayload(int action, @Nullable UUID player, @Nullable UUID partner, String id, long hash, byte[] data,
            int elapsed, double x, double y, double z, float yaw) implements CustomPacketPayload {

        static final Type<EmotePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("falatia", "emote"));
        static final StreamCodec<FriendlyByteBuf, EmotePayload> TO_SERVER =
                CustomPacketPayload.codec(EmotePayload::writeToServer, EmotePayload::readToServer);
        static final StreamCodec<FriendlyByteBuf, EmotePayload> TO_CLIENT =
                CustomPacketPayload.codec(EmotePayload::writeToClient, EmotePayload::readToClient);

        private static final EmotePayload SKIPPED = new EmotePayload(-1, null, null, "", 0L, new byte[0], 0, 0.0, 0.0, 0.0, 0.0F);

        static EmotePayload toServer(int action, String id, long hash) {
            return new EmotePayload(action, null, null, id, hash, new byte[0], 0, 0.0, 0.0, 0.0, 0.0F);
        }

        private void writeToServer(FriendlyByteBuf buf) {
            buf.writeVarInt(PROTOCOL);
            buf.writeVarInt(this.action);
            switch (this.action) {
                case PLAY -> {
                    buf.writeUtf(this.id, MAX_ID);
                    buf.writeLong(this.hash);
                }
                case DATA -> {
                    buf.writeLong(this.hash);
                    buf.writeByteArray(this.data);
                }
                case REQUEST -> buf.writeLong(this.hash);
                case INVITE -> {
                    buf.writeUtf(this.id, MAX_ID);
                    buf.writeLong(this.hash);
                    this.writeAnchor(buf);
                }
                case JOIN -> buf.writeUUID(this.player);
                default -> {
                }
            }
        }

        private static EmotePayload readToServer(FriendlyByteBuf buf) {
            buf.readVarInt();
            int action = buf.readVarInt();
            return switch (action) {
                case PLAY -> toServer(action, buf.readUtf(MAX_ID), buf.readLong());
                case DATA -> new EmotePayload(action, null, null, "", buf.readLong(), buf.readByteArray(MAX_DATA), 0, 0.0, 0.0, 0.0, 0.0F);
                case REQUEST -> toServer(action, "", buf.readLong());
                case INVITE -> new EmotePayload(action, null, null, buf.readUtf(MAX_ID), buf.readLong(), new byte[0], 0,
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
                case JOIN -> new EmotePayload(action, buf.readUUID(), null, "", 0L, new byte[0], 0, 0.0, 0.0, 0.0, 0.0F);
                default -> toServer(action, "", 0L);
            };
        }

        private void writeToClient(FriendlyByteBuf buf) {
            buf.writeVarInt(PROTOCOL);
            buf.writeVarInt(this.action);
            switch (this.action) {
                case PLAY -> {
                    buf.writeUUID(this.player);
                    buf.writeUtf(this.id, MAX_ID);
                    buf.writeLong(this.hash);
                    buf.writeVarInt(this.elapsed);
                }
                case STOP -> buf.writeUUID(this.player);
                case DATA -> {
                    buf.writeLong(this.hash);
                    buf.writeByteArray(this.data);
                }
                case INVITE -> {
                    buf.writeUUID(this.player);
                    buf.writeUtf(this.id, MAX_ID);
                    buf.writeLong(this.hash);
                    buf.writeVarInt(this.elapsed);
                    this.writeAnchor(buf);
                }
                case PAIRED -> {
                    buf.writeUUID(this.player);
                    buf.writeUUID(this.partner);
                    buf.writeUtf(this.id, MAX_ID);
                    buf.writeLong(this.hash);
                    buf.writeVarInt(this.elapsed);
                    this.writeAnchor(buf);
                }
                case CATALOG -> buf.writeVarInt(this.elapsed);
                case FILE -> {
                    buf.writeUtf(this.id, MAX_PATH);
                    buf.writeByteArray(this.data);
                }
                default -> {
                }
            }
        }

        private void writeAnchor(FriendlyByteBuf buf) {
            buf.writeDouble(this.x);
            buf.writeDouble(this.y);
            buf.writeDouble(this.z);
            buf.writeFloat(this.yaw);
        }

        private static EmotePayload readToClient(FriendlyByteBuf buf) {
            int protocol = buf.readVarInt();
            if (protocol != PROTOCOL) {
                buf.skipBytes(buf.readableBytes());
                return SKIPPED;
            }
            int action = buf.readVarInt();
            return switch (action) {
                case PLAY -> new EmotePayload(action, buf.readUUID(), null, buf.readUtf(MAX_ID), buf.readLong(), new byte[0],
                        buf.readVarInt(), 0.0, 0.0, 0.0, 0.0F);
                case STOP -> new EmotePayload(action, buf.readUUID(), null, "", 0L, new byte[0], 0, 0.0, 0.0, 0.0, 0.0F);
                case DATA -> new EmotePayload(action, null, null, "", buf.readLong(), buf.readByteArray(MAX_DATA), 0, 0.0, 0.0, 0.0, 0.0F);
                case INVITE -> new EmotePayload(action, buf.readUUID(), null, buf.readUtf(MAX_ID), buf.readLong(), new byte[0],
                        buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
                case PAIRED -> new EmotePayload(action, buf.readUUID(), buf.readUUID(), buf.readUtf(MAX_ID), buf.readLong(), new byte[0],
                        buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
                case CATALOG -> new EmotePayload(action, null, null, "", 0L, new byte[0], buf.readVarInt(), 0.0, 0.0, 0.0, 0.0F);
                case FILE -> new EmotePayload(action, null, null, buf.readUtf(MAX_PATH), 0L, buf.readByteArray(MAX_DATA), 0,
                        0.0, 0.0, 0.0, 0.0F);
                default -> {
                    buf.skipBytes(buf.readableBytes());
                    yield SKIPPED;
                }
            };
        }

        @Override
        public Type<EmotePayload> type() {
            return TYPE;
        }
    }
}
