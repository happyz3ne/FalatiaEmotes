package org.falatia.emotes.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import org.falatia.emotes.interfaces.BendablePart;
import org.falatia.emotes.EmoteBend;
import org.falatia.emotes.EmoteBone;
import org.falatia.emotes.EmotePose;
import org.falatia.emotes.interfaces.EmoteRenderStateHolder;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelEmoteMixin {

    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Unique
    private final float[] falatia$bend = new float[2];

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("TAIL"))
    private void falatia$applyEmote(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof EmoteRenderStateHolder holder)) {
            return;
        }
        EmotePose pose = holder.falatia$emotePose();
        if (!pose.active()) {
            return;
        }
        pose.apply(this.head, EmoteBone.HEAD);
        pose.apply(this.body, EmoteBone.BODY);
        pose.apply(this.rightArm, EmoteBone.RIGHT_ARM);
        pose.apply(this.leftArm, EmoteBone.LEFT_ARM);
        boolean legs = !state.isPassenger;
        if (legs) {
            pose.apply(this.rightLeg, EmoteBone.RIGHT_LEG);
            pose.apply(this.leftLeg, EmoteBone.LEFT_LEG);
        }

        this.falatia$bend(pose, this.rightArm, EmoteBone.RIGHT_ARM, false);
        this.falatia$bend(pose, this.leftArm, EmoteBone.LEFT_ARM, false);
        if (legs) {
            this.falatia$bend(pose, this.rightLeg, EmoteBone.RIGHT_LEG, false);
            this.falatia$bend(pose, this.leftLeg, EmoteBone.LEFT_LEG, false);
        }
        if (this.falatia$bend(pose, this.body, EmoteBone.BODY, true)) {
            this.falatia$followTorso();
        }
    }

    @Unique
    private boolean falatia$bend(EmotePose pose, ModelPart part, EmoteBone bone, boolean upper) {
        if (!pose.bend(bone, this.falatia$bend)) {
            return false;
        }
        for (ModelPart layer : part.getAllParts()) {
            if (layer == part || layer.getInitialPose().equals(PartPose.ZERO)) {
                ((BendablePart) (Object) layer).falatia$setBend(this.falatia$bend[0], this.falatia$bend[1], upper);
            }
        }
        return true;
    }

    @Unique
    private void falatia$followTorso() {
        Vector3f joint = ((BendablePart) (Object) this.body).falatia$joint();
        if (joint == null) {
            return;
        }
        Matrix4f body = falatia$matrix(this.body);
        Matrix4f bend = new Matrix4f().translate(joint)
                .rotate(EmoteBend.rotation(this.falatia$bend[0], this.falatia$bend[1]))
                .translate(-joint.x, -joint.y, -joint.z);
        Matrix4f follow = new Matrix4f(body).mul(bend).mul(new Matrix4f(body).invert());
        for (ModelPart part : new ModelPart[] {this.head, this.rightArm, this.leftArm}) {
            Matrix4f moved = new Matrix4f(follow).mul(falatia$matrix(part));
            Vector3f position = moved.getTranslation(new Vector3f());
            Vector3f angles = moved.getUnnormalizedRotation(new Quaternionf()).getEulerAnglesZYX(new Vector3f());
            part.x = position.x;
            part.y = position.y;
            part.z = position.z;
            part.xRot = angles.x;
            part.yRot = angles.y;
            part.zRot = angles.z;
        }
    }

    @Unique
    private static Matrix4f falatia$matrix(ModelPart part) {
        return new Matrix4f().translate(part.x, part.y, part.z)
                .rotate(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
    }
}
