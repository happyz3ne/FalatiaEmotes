package org.falatia.emotes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;

import org.falatia.emotes.interfaces.BendablePart;
import org.falatia.emotes.EmoteBend;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelBendMixin {

    @Inject(
            method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("TAIL"))
    private void falatia$bendHand(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        BendablePart part = (BendablePart) (Object) ((PlayerModel) (Object) this).getArm(arm);
        Vector3f joint = part.falatia$joint();
        if (part.falatia$bendAngle() != 0.0F && joint != null) {
            EmoteBend.apply(poseStack, joint, part.falatia$bendAxis(), part.falatia$bendAngle());
        }
    }
}
