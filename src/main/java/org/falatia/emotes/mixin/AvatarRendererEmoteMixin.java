package org.falatia.emotes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;

import org.falatia.emotes.interfaces.BendablePart;
import org.falatia.emotes.emotestypes.EmoteNearby;
import org.falatia.emotes.EmotePair;
import org.falatia.emotes.interfaces.EmoteRenderStateHolder;
import org.falatia.emotes.Emotes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererEmoteMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void falatia$extractEmote(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        Emotes.extract(entity, state, partialTicks);
        EmoteNearby.extract(entity, state, partialTicks);
        EmotePair.extract(entity, state);
    }

    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("TAIL"))
    private void falatia$emoteLabel(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        EmoteNearby.submit(state, poseStack, collector, camera);
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void falatia$unbendHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
            Identifier skinTexture, ModelPart arm, boolean hasSleeve, CallbackInfo ci) {
        for (ModelPart part : arm.getAllParts()) {
            ((BendablePart) (Object) part).falatia$setBend(0.0F, 0.0F, false);
        }
    }

    @Inject(
            method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void falatia$emoteRoot(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
        ((EmoteRenderStateHolder) state).falatia$emotePose().applyRoot(poseStack);
    }
}
