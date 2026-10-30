package org.falatia.emotes;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

import org.joml.Quaternionf;

public final class EmotePose {

    public static final int VALUES_PER_BONE = 12;

    public static final class Layer {
        public final float[] values = new float[EmoteBone.VALUES.length * VALUES_PER_BONE];
        public final boolean[] animated = new boolean[EmoteBone.VALUES.length];
        float weight;
    }

    final Layer previous = new Layer();
    final Layer current = new Layer();

    public boolean active() {
        return this.previous.weight > 0.0F || this.current.weight > 0.0F;
    }

    void clear() {
        this.previous.weight = 0.0F;
        this.current.weight = 0.0F;
    }

    public void apply(ModelPart part, EmoteBone bone) {
        apply(this.previous, part, bone);
        apply(this.current, part, bone);
    }

    private static void apply(Layer layer, ModelPart part, EmoteBone bone) {
        if (layer.weight <= 0.0F || !layer.animated[bone.ordinal()]) {
            return;
        }
        float[] v = layer.values;
        int i = bone.ordinal() * VALUES_PER_BONE;
        float w = layer.weight;
        PartPose rest = part.getInitialPose();
        part.xRot = Mth.lerp(w, part.xRot, rest.xRot() + v[i]);
        part.yRot = Mth.lerp(w, part.yRot, rest.yRot() + v[i + 1]);
        part.zRot = Mth.lerp(w, part.zRot, rest.zRot() + v[i + 2]);
        part.x = Mth.lerp(w, part.x, rest.x() + v[i + 3]);
        part.y = Mth.lerp(w, part.y, rest.y() - v[i + 4]);
        part.z = Mth.lerp(w, part.z, rest.z() + v[i + 5]);
        part.xScale = Mth.lerp(w, part.xScale, rest.xScale() * v[i + 6]);
        part.yScale = Mth.lerp(w, part.yScale, rest.yScale() * v[i + 7]);
        part.zScale = Mth.lerp(w, part.zScale, rest.zScale() * v[i + 8]);
    }

    public boolean bend(EmoteBone bone, float[] out) {
        out[0] = 0.0F;
        out[1] = 0.0F;
        bend(this.previous, bone, out);
        bend(this.current, bone, out);
        return out[1] != 0.0F;
    }

    private static void bend(Layer layer, EmoteBone bone, float[] out) {
        if (layer.weight <= 0.0F || !layer.animated[bone.ordinal()]) {
            return;
        }
        int i = bone.ordinal() * VALUES_PER_BONE + Emote.BEND * 3;
        out[1] = Mth.lerp(layer.weight, out[1], layer.values[i + 1]);
        if (layer.values[i + 1] != 0.0F) {
            out[0] = layer.values[i];
        }
    }

    public void applyRoot(PoseStack poseStack) {
        applyRoot(this.previous, poseStack);
        applyRoot(this.current, poseStack);
    }

    private static void applyRoot(Layer layer, PoseStack poseStack) {
        if (layer.weight <= 0.0F || !layer.animated[EmoteBone.ROOT.ordinal()]) {
            return;
        }
        float[] v = layer.values;
        int i = EmoteBone.ROOT.ordinal() * VALUES_PER_BONE;
        float w = layer.weight;
        poseStack.translate(-v[i + 3] * w / 16.0F, v[i + 4] * w / 16.0F, v[i + 5] * w / 16.0F);
        poseStack.rotate(new Quaternionf().rotationZYX(v[i + 2] * w, -v[i + 1] * w, -v[i] * w));
        poseStack.scale(Mth.lerp(w, 1.0F, v[i + 6]), Mth.lerp(w, 1.0F, v[i + 7]), Mth.lerp(w, 1.0F, v[i + 8]));
    }
}
