package org.falatia.emotes.emotestypes;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.falatia.emotes.*;
import org.falatia.emotes.emotemenu.EmoteWheel;
import org.falatia.emotes.interfaces.EmoteRenderStateHolder;
import org.jetbrains.annotations.Nullable;

public final class EmoteNearby {

    private static final double RANGE = 4.0;
    private static final double JOIN_RANGE = 1.5;
    private static final Identifier BADGE = Identifier.fromNamespaceAndPath(FalatiaEmotes.MOD_ID, "textures/gui/emote_copy.png");
    private static final float TEXTURE_WIDTH = 32.0F;
    private static final float TEXTURE_HEIGHT = 16.0F;
    private static final float KEY_HEIGHT = 13.0F;
    private static final float KEY_CAP = 4.0F;
    private static final float EDGE = 4.0F;
    private static final float PADDING = 3.0F;
    private static final float BADGE_SCALE = 0.014F;
    private static final double BADGE_FORWARD = 1.1;
    private static final double BADGE_FORWARD_SHARE = 0.6;
    private static final int SEE_THROUGH_ALPHA = 0x50;
    private static final int NAME_COLOR = 0xD2B8FF;

    private static KeyMapping key;
    private static int target = -1;
    private static final Map<Integer, Boolean> sight = new HashMap<>();
    private static int copiedEntity = -1;
    private static float copiedStart = Float.NaN;

    public static final class Label {
        @Nullable Component name;
        @Nullable Component key;
        boolean join;
        @Nullable Vec3 body;
    }

    private EmoteNearby() {
    }

    public static void register() {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.falatia_emotes.copy", InputConstants.KEY_G, FalatiaEmotes.KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(EmoteNearby::tick);
    }

    private static void tick(Minecraft minecraft) {
        target = -1;
        sight.clear();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            while (key.consumeClick()) {
            }
            return;
        }
        Player best = null;
        if (minecraft.crosshairPickEntity instanceof Player picked && emoting(player, picked) && offer(player, picked) != Offer.NONE) {
            best = picked;
        } else {
            double nearest = RANGE * RANGE;
            for (Player other : minecraft.level.players()) {
                double distance = other.distanceToSqr(player);
                if (distance < nearest && emoting(player, other) && offer(player, other) != Offer.NONE) {
                    nearest = distance;
                    best = other;
                }
            }
        }
        Offer offer = best == null ? Offer.NONE : offer(player, best);
        if (offer != Offer.NONE) {
            target = best.getId();
        }
        while (key.consumeClick()) {
            if (offer == Offer.NONE || minecraft.gui.screen() != null) {
                continue;
            }
            if (offer == Offer.JOIN) {
                if (Emotes.canStart()) {
                    EmoteSync.join(best.getUUID());
                    remember(best);
                } else {
                    player.sendOverlayMessage(Component.translatable("falatia_emotes.blocked"));
                }
            } else if (Emotes.play(Emotes.own(Emotes.current(best)).id())) {
                remember(best);
            }
        }
    }

    private enum Offer {
        NONE,
        COPY,
        JOIN
    }

    private static Offer offer(LocalPlayer self, Entity other) {
        Emote emote = Emotes.current(other);
        if (emote == null || Emotes.paired(other)) {
            return Offer.NONE;
        }
        if (Emotes.waiting(other) && emote.pair() != null) {
            return Emotes.mate() == null && !Emotes.waiting(self) && other.distanceToSqr(self) <= JOIN_RANGE * JOIN_RANGE
                    ? Offer.JOIN : Offer.NONE;
        }
        Emote own = Emotes.own(emote);
        return own != null && !Emotes.playing(own.id()) ? Offer.COPY : Offer.NONE;
    }

    private static void remember(Player other) {
        copiedEntity = other.getId();
        copiedStart = Emotes.started(other);
    }

    private static boolean copied(Entity other) {
        return other.getId() == copiedEntity && Emotes.started(other) == copiedStart;
    }

    private static boolean inSight(LocalPlayer self, Entity other) {
        return sight.computeIfAbsent(other.getId(), id -> {
            Vec3 eye = self.getEyePosition();
            for (Vec3 point : new Vec3[] {other.getEyePosition(), other.position().add(0.0, other.getBbHeight() * 0.5, 0.0)}) {
                if (self.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self)).getType()
                        == HitResult.Type.MISS) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean emoting(LocalPlayer self, Entity other) {
        return other != self && !(other instanceof EmotePair.Ghost) && Emotes.current(other) != null && !copied(other) && !other.isInvisibleTo(self)
                && other.distanceToSqr(self) < RANGE * RANGE && inSight(self, other);
    }

    public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
        Label label = ((EmoteRenderStateHolder) state).falatia$emoteLabel();
        label.name = null;
        label.key = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.hud.isHidden() || !emoting(minecraft.player, entity)) {
            return;
        }
        Offer offer = offer(minecraft.player, entity);
        if (offer == Offer.NONE) {
            return;
        }
        Emote emote = Emotes.current(entity);
        label.name = Component.literal(emote.name()).withColor(NAME_COLOR);
        label.join = offer == Offer.JOIN;
        if (entity.getId() == target && !key.isUnbound()) {
            label.key = EmoteWheel.keyName(KeyMappingHelper.getBoundKeyOf(key)).copy().withStyle(ChatFormatting.BOLD);
        }
        Vec3 body = EmoteLocator.locate(entity, emote, Emotes.seconds(entity, partialTicks), EmoteLocator.BODY, new float[3]);
        label.body = body.subtract(entity.position());
    }

    public static void submit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Label label = ((EmoteRenderStateHolder) state).falatia$emoteLabel();
        if (label.name != null && label.body != null) {
            badge(label, state, poseStack, collector, camera);
        }
    }

    private static void badge(Label label, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable(label.join ? "falatia_emotes.join" : "falatia_emotes.copy");
        float nameWidth = font.width(label.name);
        float keyTextWidth = label.key == null ? 0.0F : font.width(label.key) - 1.0F;
        float keyWidth = label.key == null ? 0.0F : Math.max(16.0F, keyTextWidth + 8.0F);
        float rowWidth = label.key == null ? 0.0F : keyWidth + 4.0F + font.width(text);
        float width = Math.max(nameWidth, rowWidth) + PADDING * 2.0F;
        float height = PADDING * 2.0F + 8.0F + (label.key == null ? 0.0F : 3.0F + KEY_HEIGHT);
        float left = -width / 2.0F;
        float top = -height / 2.0F;
        float keyLeft = -rowWidth / 2.0F;
        float keyTop = top + PADDING + 11.0F;
        int light = LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, 2);

        Vec3 toCamera = camera.pos.subtract(state.x, state.y, state.z).subtract(label.body);
        double distance = toCamera.length();
        double forward = Math.min(BADGE_FORWARD, distance * BADGE_FORWARD_SHARE);
        float scale = BADGE_SCALE * (float) ((distance - forward) / Math.max(distance, 1.0E-3));
        Vec3 at = label.body.add(toCamera.scale(forward / Math.max(distance, 1.0E-3)));

        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.rotate(camera.orientation);
        poseStack.scale(scale, -scale, scale);
        for (boolean seeThrough : new boolean[] {true, false}) {
            int alpha = seeThrough ? SEE_THROUGH_ALPHA : 0xFF;
            int white = ARGB.color(alpha, 0xFFFFFF);
            collector.submitCustomGeometry(poseStack, seeThrough ? RenderTypes.textSeeThrough(BADGE) : RenderTypes.text(BADGE),
                    (pose, buffer) -> {
                        nineSlice(pose, buffer, left, top, left + width, top + height, 0.0F, white, light);
                        if (label.key == null) {
                            return;
                        }
                        float keyRight = keyLeft + keyWidth;
                        float z = 0.05F;
                        quad(pose, buffer, keyLeft, keyTop, keyLeft + KEY_CAP, keyTop + KEY_HEIGHT, z, 0.0F, 0.0F, KEY_CAP, KEY_HEIGHT, white, light);
                        quad(pose, buffer, keyLeft + KEY_CAP, keyTop, keyRight - KEY_CAP, keyTop + KEY_HEIGHT, z,
                                KEY_CAP, 0.0F, 16.0F - KEY_CAP, KEY_HEIGHT, white, light);
                        quad(pose, buffer, keyRight - KEY_CAP, keyTop, keyRight, keyTop + KEY_HEIGHT, z,
                                16.0F - KEY_CAP, 0.0F, 16.0F, KEY_HEIGHT, white, light);
                    });
            Font.DisplayMode mode = seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.1F);
            collector.submitText(poseStack, -nameWidth / 2.0F, top + PADDING, label.name.getVisualOrderText(), false, mode, light,
                    ARGB.color(alpha, 0xFFFFFF), 0, 0);
            if (label.key != null) {
                collector.submitText(poseStack, keyLeft + (keyWidth - keyTextWidth) / 2.0F, keyTop + 2.5F,
                        label.key.getVisualOrderText(), false, mode, light, ARGB.color(alpha, 0x26262E), 0, 0);
                collector.submitText(poseStack, keyLeft + keyWidth + 4.0F, keyTop + 3.0F,
                        text.getVisualOrderText(), false, mode, light, ARGB.color(alpha, 0xDDDDDD), 0, 0);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void nineSlice(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float z,
            int color, int light) {
        float[] xs = {x0, x0 + EDGE, x1 - EDGE, x1};
        float[] ys = {y0, y0 + EDGE, y1 - EDGE, y1};
        float[] us = {16.0F, 16.0F + EDGE, 32.0F - EDGE, 32.0F};
        float[] vs = {0.0F, EDGE, 16.0F - EDGE, 16.0F};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                quad(pose, buffer, xs[column], ys[row], xs[column + 1], ys[row + 1], z,
                        us[column], vs[row], us[column + 1], vs[row + 1], color, light);
            }
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float z,
            float u0, float v0, float u1, float v1, int color, int light) {
        u0 /= TEXTURE_WIDTH;
        u1 /= TEXTURE_WIDTH;
        v0 /= TEXTURE_HEIGHT;
        v1 /= TEXTURE_HEIGHT;
        buffer.addVertex(pose, x0, y0, z).setColor(color).setUv(u0, v0).setLight(light);
        buffer.addVertex(pose, x0, y1, z).setColor(color).setUv(u0, v1).setLight(light);
        buffer.addVertex(pose, x1, y1, z).setColor(color).setUv(u1, v1).setLight(light);
        buffer.addVertex(pose, x1, y0, z).setColor(color).setUv(u1, v0).setLight(light);
    }
}
