package org.falatia.emotes.mixin.emf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.falatia.emotes.interfaces.BendablePart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import traben.entity_model_features.models.parts.EMFModelPart;

@Mixin(EMFModelPart.class)
public abstract class EMFModelPartBendMixin {

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void falatia$compileBent(PoseStack.Pose pose, VertexConsumer builder, int lightCoords, int overlayCoords, int color, CallbackInfo ci) {
        if (((BendablePart) (Object) this).falatia$compileBent(pose, builder, lightCoords, overlayCoords, color)) {
            ci.cancel();
        }
    }
}
