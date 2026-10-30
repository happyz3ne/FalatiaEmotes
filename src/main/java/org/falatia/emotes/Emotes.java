package org.falatia.emotes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

import org.falatia.emotes.emotemenu.EmoteWheel;
import org.falatia.emotes.emotestypes.EmoteEffects;
import org.falatia.emotes.emotestypes.EmoteIcons;
import org.falatia.emotes.emotestypes.EmoteLoader;
import org.falatia.emotes.interfaces.EmoteEvent;
import org.falatia.emotes.interfaces.EmoteRenderStateHolder;
import org.falatia.emotes.interfaces.HurtTracker;
import org.jetbrains.annotations.Nullable;

public final class Emotes {

    private static final float FADE_TICKS = 4.0F;

    private static Map<String, Emote> emotes = Map.of();
    private static Map<String, Emote> loaded = Map.of();
    private static Map<String, Emote> server = Map.of();
    private static int generation;
    private static Map<Long, Emote> byHash = Map.of();
    private static final Map<UUID, Track> tracks = new HashMap<>();
    private static boolean cameraForced;
    private static long hurtTimeOnPlay;

    private Emotes() {
    }

    public static void register() {
        reload();
        EmoteWatcher.start();
        ClientTickEvents.END_CLIENT_TICK.register(Emotes::tick);
        ClientTickEvents.END_CLIENT_TICK.register(EmotePair::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            setServer(Map.of());
            tracks.clear();
            EmotePair.end();
            restoreCamera(client);
        }));
    }

    public static int reload() {
        loaded = EmoteLoader.load();
        merge();
        EmoteWheel.load();
        FalatiaEmotes.LOGGER.info("Emotes: loaded {}", loaded.size());
        return emotes.size();
    }

    static void setServer(Map<String, Emote> emotes) {
        if (emotes.isEmpty() && server.isEmpty()) {
            return;
        }
        server = emotes;
        merge();
        if (!emotes.isEmpty()) {
            FalatiaEmotes.LOGGER.info("Emotes: {} from the server", emotes.size());
        }
    }

    public static boolean fromServer(Emote emote) {
        return server.get(emote.id()) == emote;
    }

    private static void merge() {
        Map<String, Emote> all = new LinkedHashMap<>(loaded);
        all.putAll(server);
        emotes = all;
        generation++;
        Map<Long, Emote> hashes = new HashMap<>();
        for (Emote emote : all.values()) {
            if (emote.source() != null) {
                hashes.putIfAbsent(emote.source().hash(), emote);
            }
        }
        byHash = hashes;
        EmoteIcons.clear();
    }

    public static int generation() {
        return generation;
    }

    public static Path directory() {
        return EmoteLoader.DIR;
    }

    public static List<String> ids() {
        List<String> ids = new ArrayList<>(emotes.keySet());
        Collections.sort(ids);
        return ids;
    }

    public static @Nullable Emote get(String id) {
        return emotes.get(id);
    }

    public static boolean play(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }
        Emote emote = emotes.get(id);
        if (emote == null) {
            player.sendOverlayMessage(Component.translatable("falatia_emotes.unknown", id));
            return false;
        }
        if (!canEmote(player, EmoteWheel.walking(id))) {
            player.sendOverlayMessage(Component.translatable("falatia_emotes.blocked"));
            return false;
        }
        if (emote.pair() != null) {
            Vec3 anchor = player.position();
            float yaw = player.yBodyRot;
            startWaiting(player, emote, 0, yaw);
            EmotePair.invite(player, emote, anchor, yaw);
            EmoteSync.invited(emote, anchor, yaw);
        } else {
            start(player, emote, 0);
            EmoteSync.played(emote);
        }
        startedLocally(minecraft, player);
        return true;
    }

    private static void startedLocally(Minecraft minecraft, LocalPlayer player) {
        hurtTimeOnPlay = ((HurtTracker) player).falatia$lastHurtTime();
        if (EmoteWheel.thirdPerson() && minecraft.options.getCameraType().isFirstPerson()) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            cameraForced = true;
        }
    }

    public static boolean canStart() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && canEmote(player, false);
    }

    static void startWaiting(Avatar entity, Emote emote, int elapsedTicks, float yaw) {
        start(entity, emote, elapsedTicks);
        Playback playback = tracks.get(entity.getUUID()).current;
        playback.waiting = true;
        playback.bodyYaw = yaw;
    }

    static void startRole(Avatar entity, Emote emote, float seconds, float yaw, UUID mate) {
        Track track = tracks.get(entity.getUUID());
        boolean continues = track != null && track.entityId == entity.getId() && track.current != null
                && track.current.emote == emote && track.current.waiting && !track.current.stopping();
        if (!continues) {
            start(entity, emote, 0);
        }
        Playback playback = tracks.get(entity.getUUID()).current;
        playback.waiting = false;
        playback.holdAt = Float.NaN;
        playback.start = entity.tickCount - seconds * 20.0F;
        playback.startedMillis = Util.getMillis() - (long) (seconds * 1000.0F);
        playback.fired = seconds;
        playback.bodyYaw = yaw;
        playback.mate = mate;
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == minecraft.player && !continues) {
            startedLocally(minecraft, minecraft.player);
        }
    }

    public static boolean paired(Entity entity) {
        Track track = tracks.get(entity.getUUID());
        return track != null && track.entityId == entity.getId() && track.current != null
                && track.current.mate != null && !track.current.stopping();
    }

    public static @Nullable UUID mate() {
        LocalPlayer player = Minecraft.getInstance().player;
        Track track = player == null ? null : tracks.get(player.getUUID());
        return track != null && track.current != null && !track.current.stopping() ? track.current.mate : null;
    }

    public static void stop() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Track track = tracks.get(player.getUUID());
        if (track != null && track.current != null && !track.current.stopping()) {
            track.current.stop(player.tickCount);
            EmoteSync.stopped();
        }
    }

    static @Nullable Emote byHash(long hash) {
        return byHash.get(hash);
    }

    public static @Nullable Emote current(Entity entity) {
        Track track = tracks.isEmpty() ? null : tracks.get(entity.getUUID());
        return track != null && track.entityId == entity.getId() && track.current != null && !track.current.stopping()
                ? track.current.emote : null;
    }

    public static float started(Entity entity) {
        Track track = tracks.get(entity.getUUID());
        return track == null || track.entityId != entity.getId() || track.current == null ? Float.NaN : track.current.start;
    }

    public static float seconds(Entity entity, float partialTicks) {
        Track track = tracks.get(entity.getUUID());
        return track == null || track.current == null ? 0.0F : track.current.time(entity.tickCount + partialTicks);
    }

    public static boolean waiting(Entity entity) {
        Track track = tracks.get(entity.getUUID());
        return track != null && track.entityId == entity.getId() && track.current != null && track.current.waiting;
    }

    static void hold(Avatar entity, Emote emote, float seconds) {
        start(entity, emote, 0);
        tracks.get(entity.getUUID()).current.holdAt = seconds;
    }

    static void join(Entity entity, float seconds) {
        Track track = tracks.get(entity.getUUID());
        if (track == null || track.current == null) {
            return;
        }
        Playback playback = track.current;
        playback.waiting = false;
        playback.holdAt = Float.NaN;
        playback.start = entity.tickCount - seconds * 20.0F;
        playback.startedMillis = Util.getMillis() - (long) (seconds * 1000.0F);
        playback.fired = seconds;
    }

    public static @Nullable Emote own(@Nullable Emote emote) {
        if (emote == null) {
            return null;
        }
        Emote same = emote.source() != null ? byHash.get(emote.source().hash()) : null;
        return same != null ? same : emotes.get(emote.id());
    }

    public static boolean playing() {
        LocalPlayer player = Minecraft.getInstance().player;
        Track track = player == null ? null : tracks.get(player.getUUID());
        return track != null && track.entityId == player.getId() && track.current != null && !track.current.stopping();
    }

    public static boolean emoting(UUID player) {
        Track track = tracks.get(player);
        return track != null && (track.current != null || track.previous != null);
    }

    public static boolean playing(String id) {
        LocalPlayer player = Minecraft.getInstance().player;
        Track track = player == null ? null : tracks.get(player.getUUID());
        return track != null && track.entityId == player.getId() && track.current != null
                && !track.current.stopping() && track.current.emote.id().equals(id);
    }

    public static void toggle(String id) {
        if (playing(id)) {
            stop();
        } else {
            play(id);
        }
    }

    static void start(Avatar entity, Emote emote, int elapsedTicks) {
        Track track = tracks.get(entity.getUUID());
        if (track == null || track.entityId != entity.getId()) {
            track = new Track(entity.getId());
            tracks.put(entity.getUUID(), track);
        }
        float age = entity.tickCount;
        if (track.current != null) {
            track.current.stop(age);
            track.previous = track.current;
        }
        track.current = new Playback(emote, age - elapsedTicks);
        track.current.startedMillis = Util.getMillis() - elapsedTicks * 50L;
        if (elapsedTicks > 0) {
            track.current.fired = elapsedTicks / 20.0F;
        }
    }

    static void stopRemote(UUID player) {
        Track track = tracks.get(player);
        Minecraft minecraft = Minecraft.getInstance();
        if (track == null || track.current == null || minecraft.level == null
                || !(minecraft.level.getEntity(track.entityId) instanceof Avatar entity)) {
            return;
        }
        track.current.stop(entity.tickCount);
    }

    public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
        EmotePose pose = ((EmoteRenderStateHolder) state).falatia$emotePose();
        Track track = tracks.isEmpty() ? null : tracks.get(entity.getUUID());
        if (track == null || track.entityId != entity.getId()) {
            pose.clear();
            return;
        }
        float age = entity.tickCount + partialTicks;
        fill(track.previous, pose.previous, age);
        fill(track.current, pose.current, age);
        if (track.current != null && !Float.isNaN(track.current.bodyYaw)) {
            state.yRot += state.bodyRot - track.current.bodyYaw;
            state.bodyRot = track.current.bodyYaw;
        }
    }

    private static void fill(@Nullable Playback playback, EmotePose.Layer layer, float age) {
        layer.weight = playback == null ? 0.0F : playback.weight(age);
        if (layer.weight > 0.0F) {
            playback.emote.sample(playback.time(age), layer);
        }
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (EmoteWatcher.poll()) {
            int count = reload();
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("falatia_emotes.updated", count));
            }
        }
        if (player == null || minecraft.level == null) {
            tracks.clear();
            restoreCamera(minecraft);
            return;
        }
        Track local = tracks.get(player.getUUID());
        if (local != null && local.current != null && !local.current.stopping()
                && interrupted(player, EmoteWheel.walking(local.current.emote.id()))) {
            local.current.stop(player.tickCount);
            EmoteSync.stopped();
        }
        Iterator<Map.Entry<UUID, Track>> iterator = tracks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Track> entry = iterator.next();
            Track track = entry.getValue();
            Entity entity = minecraft.level.getEntity(track.entityId);
            if (!(entity instanceof Avatar) || !entity.getUUID().equals(entry.getKey())) {
                iterator.remove();
                continue;
            }
            float age = entity.tickCount;
            Playback current = track.current;
            if (current != null && !current.stopping() && !current.paused()) {
                float now = (age - current.start) / 20.0F;
                if (now > current.fired) {
                    float from = current.fired;
                    current.fired = now;
                    boolean quiet = echoes(minecraft, entity, current);
                    current.emote.events(from, now, event -> {
                        if (!quiet || !(event instanceof EmoteEvent.Sound)) {
                            EmoteEffects.play((Avatar) entity, current.emote, event, now);
                        }
                    });
                }
            }
            if (current != null && !current.stopping() && !current.paused() && current.emote.loop() == Emote.Loop.NONE
                    && age - current.start >= current.emote.length() * 20.0F) {
                current.stop(age);
                if (current.mate != null && entity == player) {
                    EmoteSync.stopped();
                }
            }
            if (track.previous != null && track.previous.faded(age)) {
                track.previous = null;
            }
            if (current != null && current.faded(age)) {
                track.current = null;
            }
            if (track.current == null && track.previous == null) {
                iterator.remove();
            }
        }
        local = tracks.get(player.getUUID());
        if (local == null || local.current == null || local.current.stopping()) {
            restoreCamera(minecraft);
        }
    }

    private static final double ECHO_RANGE = 16.0;

    private static boolean echoes(Minecraft minecraft, Entity entity, Playback mine) {
        for (Track other : tracks.values()) {
            Playback playback = other.current;
            if (playback == null || playback == mine || playback.stopping() || playback.paused() || !same(playback.emote, mine.emote)) {
                continue;
            }
            Entity player = minecraft.level.getEntity(other.entityId);
            if (player == null || player.distanceToSqr(entity) > ECHO_RANGE * ECHO_RANGE) {
                continue;
            }
            if (playback.startedMillis < mine.startedMillis
                    || playback.startedMillis == mine.startedMillis && other.entityId < entity.getId()) {
                return true;
            }
        }
        return false;
    }

    private static boolean same(Emote a, Emote b) {
        return a == b || a.source() != null && b.source() != null && a.source().hash() == b.source().hash();
    }

    private static boolean canEmote(LocalPlayer player, boolean walking) {
        return player.isAlive() && !player.isSpectator()
                && !player.isSwimming() && !player.isFallFlying() && !player.isSleeping()
                && (walking || player.isPassenger() || player.onGround() && !player.isUsingItem());
    }

    private static boolean interrupted(LocalPlayer player, boolean walking) {
        Input input = player.input.keyPresses;
        if (walking) {
            return input.shift() || !canEmote(player, true);
        }
        if (player.isPassenger()) {
            return input.shift() || !canEmote(player, false)
                    || ((HurtTracker) player).falatia$lastHurtTime() != hurtTimeOnPlay;
        }
        return input.forward() || input.backward() || input.left() || input.right() || input.jump() || input.shift()
                || !canEmote(player, false)
                || ((HurtTracker) player).falatia$lastHurtTime() != hurtTimeOnPlay;
    }

    private static void restoreCamera(Minecraft minecraft) {
        if (!cameraForced) {
            return;
        }
        cameraForced = false;
        if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private static final class Track {
        final int entityId;
        @Nullable Playback current;
        @Nullable Playback previous;

        Track(int entityId) {
            this.entityId = entityId;
        }
    }

    private static final class Playback {
        final Emote emote;
        float start;
        float stop = Float.NaN;
        boolean waiting;
        float holdAt = Float.NaN;
        float bodyYaw = Float.NaN;
        @Nullable UUID mate;
        long startedMillis;
        float fired = -1.0F;

        Playback(Emote emote, float start) {
            this.emote = emote;
            this.start = start;
        }

        boolean stopping() {
            return !Float.isNaN(this.stop);
        }

        boolean paused() {
            return this.waiting || !Float.isNaN(this.holdAt);
        }

        float time(float age) {
            if (!Float.isNaN(this.holdAt)) {
                return this.holdAt;
            }
            float seconds = (age - this.start) / 20.0F;
            Emote.Pair pair = this.emote.pair();
            return this.waiting && pair != null ? pair.waitTime(seconds) : seconds;
        }

        void stop(float age) {
            if (!this.stopping()) {
                this.stop = age;
            }
        }

        boolean faded(float age) {
            return this.stopping() && age - this.stop >= FADE_TICKS;
        }

        float weight(float age) {
            float in = Math.min(1.0F, (age - this.start) / FADE_TICKS);
            float out = this.stopping() ? 1.0F - (age - this.stop) / FADE_TICKS : 1.0F;
            return Math.clamp(Math.min(in, out), 0.0F, 1.0F);
        }
    }
}
