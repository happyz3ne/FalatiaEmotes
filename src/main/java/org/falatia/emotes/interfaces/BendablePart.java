package org.falatia.emotes.interfaces;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Vector3f;

public interface BendablePart {

    void falatia$setBend(float axis, float angle, boolean upper);

    float falatia$bendAxis();

    float falatia$bendAngle();

    Vector3f falatia$joint();

    boolean falatia$compileBent(PoseStack.Pose pose, VertexConsumer builder, int lightCoords, int overlayCoords, int color);
}
