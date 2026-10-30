package org.falatia.emotes;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class EmoteBend {

    private static final float EPSILON = 1.0E-3F;

    private EmoteBend() {
    }

    public static Quaternionf rotation(float axis, float angle) {
        return new Quaternionf().rotationAxis(angle, Mth.cos(axis), 0.0F, -Mth.sin(axis));
    }

    public static Vector3f joint(ModelPart.Cube cube) {
        return new Vector3f((cube.minX + cube.maxX) / 2.0F, (cube.minY + cube.maxY) / 2.0F, (cube.minZ + cube.maxZ) / 2.0F);
    }

    public static void apply(PoseStack poseStack, Vector3fc joint, float axis, float angle) {
        poseStack.translate(joint.x() / 16.0F, joint.y() / 16.0F, joint.z() / 16.0F);
        poseStack.rotate(rotation(axis, angle));
        poseStack.translate(-joint.x() / 16.0F, -joint.y() / 16.0F, -joint.z() / 16.0F);
    }

    public static void compile(List<ModelPart.Cube> cubes, PoseStack.Pose pose, VertexConsumer builder,
            int lightCoords, int overlayCoords, int color, float axis, float angle, boolean upper) {
        Quaternionf full = rotation(axis, angle);
        Quaternionf half = rotation(axis, angle / 2.0F);
        Bender bender = new Bender(pose, builder, lightCoords, overlayCoords, color, full, half, upper);
        for (ModelPart.Cube cube : cubes) {
            bender.joint = joint(cube);
            for (ModelPart.Polygon polygon : cube.polygons) {
                bender.polygon(polygon);
            }
        }
    }

    private static final class Bender {
        final PoseStack.Pose pose;
        final Matrix4f matrix;
        final VertexConsumer builder;
        final int light;
        final int overlay;
        final int color;
        final Quaternionf full;
        final Quaternionf half;
        final boolean upper;
        Vector3f joint = new Vector3f();
        final Vector3f scratch = new Vector3f();
        final Vector3f normal = new Vector3f();

        Bender(PoseStack.Pose pose, VertexConsumer builder, int light, int overlay, int color,
                Quaternionf full, Quaternionf half, boolean upper) {
            this.pose = pose;
            this.matrix = pose.pose();
            this.builder = builder;
            this.light = light;
            this.overlay = overlay;
            this.color = color;
            this.full = full;
            this.half = half;
            this.upper = upper;
        }

        float side(float y) {
            if (Math.abs(y - this.joint.y) <= EPSILON) {
                return 0.5F;
            }
            return (this.upper ? y < this.joint.y : y > this.joint.y) ? 1.0F : 0.0F;
        }

        void polygon(ModelPart.Polygon polygon) {
            ModelPart.Vertex[] v = polygon.vertices();
            if (v.length != 4) {
                this.quad(polygon.normal(), v);
                return;
            }
            boolean pairs03 = Math.abs(v[0].y() - v[1].y()) <= EPSILON && Math.abs(v[2].y() - v[3].y()) <= EPSILON;
            boolean pairs01 = Math.abs(v[1].y() - v[2].y()) <= EPSILON && Math.abs(v[3].y() - v[0].y()) <= EPSILON;
            float minY = Math.min(Math.min(v[0].y(), v[1].y()), Math.min(v[2].y(), v[3].y()));
            float maxY = Math.max(Math.max(v[0].y(), v[1].y()), Math.max(v[2].y(), v[3].y()));
            boolean crosses = minY < this.joint.y - EPSILON && maxY > this.joint.y + EPSILON;
            if (crosses && pairs03) {
                ModelPart.Vertex m0 = middle(v[0], v[3]);
                ModelPart.Vertex m1 = middle(v[1], v[2]);
                this.quad(polygon.normal(), v[0], v[1], m1, m0);
                this.quad(polygon.normal(), m0, m1, v[2], v[3]);
            } else if (crosses && pairs01) {
                ModelPart.Vertex m0 = middle(v[0], v[1]);
                ModelPart.Vertex m3 = middle(v[3], v[2]);
                this.quad(polygon.normal(), v[0], m0, m3, v[3]);
                this.quad(polygon.normal(), m0, v[1], v[2], m3);
            } else {
                this.quad(polygon.normal(), v);
            }
        }

        ModelPart.Vertex middle(ModelPart.Vertex a, ModelPart.Vertex b) {
            float t = (this.joint.y - a.y()) / (b.y() - a.y());
            return new ModelPart.Vertex(Mth.lerp(t, a.x(), b.x()), this.joint.y, Mth.lerp(t, a.z(), b.z()),
                    Mth.lerp(t, a.u(), b.u()), Mth.lerp(t, a.v(), b.v()));
        }

        void quad(Vector3fc faceNormal, ModelPart.Vertex... vertices) {
            float centerY = 0.0F;
            for (ModelPart.Vertex vertex : vertices) {
                centerY += vertex.y();
            }
            float side = this.side(centerY / vertices.length);
            this.normal.set(faceNormal);
            if (side > 0.0F) {
                (side >= 1.0F ? this.full : this.half).transform(this.normal);
            }
            Vector3f n = this.pose.transformNormal(this.normal, this.normal);
            float nx = n.x();
            float ny = n.y();
            float nz = n.z();
            for (ModelPart.Vertex vertex : vertices) {
                Vector3f p = this.scratch.set(vertex.x(), vertex.y(), vertex.z());
                float vertexSide = this.side(vertex.y());
                if (vertexSide > 0.0F) {
                    p.sub(this.joint);
                    (vertexSide >= 1.0F ? this.full : this.half).transform(p);
                    p.add(this.joint);
                }
                p = this.matrix.transformPosition(p.x() / 16.0F, p.y() / 16.0F, p.z() / 16.0F, p);
                this.builder.addVertex(p.x(), p.y(), p.z(), this.color, vertex.u(), vertex.v(), this.overlay, this.light, nx, ny, nz);
            }
        }
    }
}
