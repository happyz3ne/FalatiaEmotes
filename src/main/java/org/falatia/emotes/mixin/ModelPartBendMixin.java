package org.falatia.emotes.mixin;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;

import org.falatia.emotes.interfaces.BendablePart;
import org.falatia.emotes.EmoteBend;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartBendMixin implements BendablePart {

    @Shadow @Final private List<ModelPart.Cube> cubes;

    @Unique
    private float falatia$bendAxis;
    @Unique
    private float falatia$bendAngle;
    @Unique
    private boolean falatia$bendUpper;

    @Override
    public void falatia$setBend(float axis, float angle, boolean upper) {
        this.falatia$bendAxis = axis;
        this.falatia$bendAngle = angle;
        this.falatia$bendUpper = upper;
    }

    @Override
    public float falatia$bendAxis() {
        return this.falatia$bendAxis;
    }

    @Override
    public float falatia$bendAngle() {
        return this.falatia$bendAngle;
    }

    @Override
    public Vector3f falatia$joint() {
        return this.cubes.isEmpty() ? null : EmoteBend.joint(this.cubes.getFirst());
    }

    @Inject(method = "resetPose", at = @At("TAIL"))
    private void falatia$resetBend(CallbackInfo ci) {
        this.falatia$bendAngle = 0.0F;
    }

    @Override
    public boolean falatia$compileBent(PoseStack.Pose pose, VertexConsumer builder, int lightCoords, int overlayCoords, int color) {
        if (this.falatia$bendAngle == 0.0F || this.cubes.isEmpty()) {
            return false;
        }
        EmoteBend.compile(this.cubes, pose, builder, lightCoords, overlayCoords, color,
                this.falatia$bendAxis, this.falatia$bendAngle, this.falatia$bendUpper);
        return true;
    }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void falatia$compileBent(PoseStack.Pose pose, VertexConsumer builder, int lightCoords, int overlayCoords, int color, CallbackInfo ci) {
        if (this.falatia$compileBent(pose, builder, lightCoords, overlayCoords, color)) {
            ci.cancel();
        }
    }
}
