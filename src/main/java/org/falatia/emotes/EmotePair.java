package org.falatia.emotes;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

public final class EmotePair {

    private static final int SOLO_JOIN_TICKS = 30;
    private static final int TIMEOUT_TICKS = 20 * 20;
    private static int nextGhostId = -1_000_000;

    private static @Nullable Session session;

    private static final class Session {
        final Emote emote;
        final Vec3 anchor;
        final float yaw;
        final Ghost ghost;
        int ticks;
        boolean joined;

        Session(Emote emote, Vec3 anchor, float yaw, Ghost ghost) {
            this.emote = emote;
            this.anchor = anchor;
            this.yaw = yaw;
            this.ghost = ghost;
        }
    }

    public static final class Ghost extends RemotePlayer {

        Ghost(ClientLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "partner"));
        }

        @Override
        public boolean isPickable() {
            return false;
        }

        @Override
        public boolean isPushable() {
            return false;
        }
    }

    private EmotePair() {
    }

    static void invite(LocalPlayer player, Emote emote, Vec3 anchor, float yaw) {
        end();
        Emote.Pair pair = emote.pair();
        if (pair == null || !(player.level() instanceof ClientLevel level)) {
            return;
        }
        Ghost ghost = new Ghost(level);
        ghost.setId(nextGhostId--);
        Session started = new Session(emote, anchor, yaw, ghost);
        place(started);
        level.addEntity(ghost);
        Emotes.hold(ghost, pair.partner(), pair.waitEnd());
        session = started;
    }

    static void tick(Minecraft minecraft) {
        Session current = session;
        if (current == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || current.ghost.isRemoved() || Emotes.current(player) != current.emote) {
            end();
            return;
        }
        place(current);
        current.ticks++;
        if (!current.joined && alone(minecraft) && current.ticks >= SOLO_JOIN_TICKS) {
            float from = current.emote.pair().waitEnd();
            Emotes.join(player, from);
            Emotes.join(current.ghost, from);
            current.joined = true;
        } else if (!current.joined && current.ticks >= TIMEOUT_TICKS) {
            Emotes.stop();
            end();
        }
    }

    private static boolean alone(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        return server != null && server.getPlayerCount() <= 1;
    }

    static void joined() {
        end();
    }

    static void end() {
        Session current = session;
        session = null;
        if (current != null && !current.ghost.isRemoved() && current.ghost.level() instanceof ClientLevel level) {
            level.removeEntity(current.ghost.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    static Vec3 partnerPosition(Vec3 anchor, float yaw, Emote.Pair pair) {
        float radians = yaw * Mth.DEG_TO_RAD;
        double x = anchor.x - Mth.cos(radians) * pair.right() - Mth.sin(radians) * pair.forward();
        double z = anchor.z - Mth.sin(radians) * pair.right() + Mth.cos(radians) * pair.forward();
        return new Vec3(x, anchor.y + pair.up(), z);
    }

    private static void place(Session current) {
        Emote.Pair pair = current.emote.pair();
        Vec3 at = partnerPosition(current.anchor, current.yaw, pair);
        float facing = current.yaw + pair.facing();
        Ghost ghost = current.ghost;
        ghost.snapTo(at.x, at.y, at.z, facing, 0.0F);
        ghost.setYBodyRot(facing);
        ghost.setYHeadRot(facing);
        ghost.yBodyRotO = facing;
        ghost.yHeadRotO = facing;
    }

    public static void extract(Avatar entity, AvatarRenderState state) {
        if (entity instanceof Ghost) {
            state.isInvisible = true;
            state.isInvisibleToPlayer = false;
            state.nameTag = null;
            state.scoreText = null;
            state.shadowRadius = 0.0F;
        }
    }
}
