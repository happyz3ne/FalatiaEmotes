package org.falatia.emotes.emotestypes;

import java.util.Map;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;

import org.falatia.emotes.Emote;
import org.falatia.emotes.EmoteBone;
import org.falatia.emotes.EmotePose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class EmoteLocator {

    public static final String BODY = "body";

    private record Point(EmoteBone bone, float pivotX, float pivotY, float pivotZ, float x, float y, float z) {
    }

    private static final Map<String, Point> POINTS = Map.of(
            "head", new Point(EmoteBone.HEAD, 0.0F, 0.0F, 0.0F, 0.0F, -4.0F, 0.0F),
            BODY, new Point(EmoteBone.BODY, 0.0F, 0.0F, 0.0F, 0.0F, 6.0F, 0.0F),
            "right_hand", new Point(EmoteBone.RIGHT_ARM, -5.0F, 2.0F, 0.0F, -1.0F, 10.0F, 0.0F),
            "left_hand", new Point(EmoteBone.LEFT_ARM, 5.0F, 2.0F, 0.0F, 1.0F, 10.0F, 0.0F),
            "right_foot", new Point(EmoteBone.RIGHT_LEG, -1.9F, 12.0F, 0.0F, 0.0F, 12.0F, 0.0F),
            "left_foot", new Point(EmoteBone.LEFT_LEG, 1.9F, 12.0F, 0.0F, 0.0F, 12.0F, 0.0F),
            "feet", new Point(EmoteBone.ROOT, 0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));

    private EmoteLocator() {
    }

    static Vec3 locate(Avatar entity, Emote emote, float seconds, String locator, float[] offset) {
        Point point = POINTS.getOrDefault(locator, POINTS.get(BODY));
        EmotePose.Layer layer = new EmotePose.Layer();
        emote.sample(seconds, layer);

        Vector3f p = new Vector3f(point.x(), point.y(), point.z());
        int bone = point.bone().ordinal();
        if (point.bone() != EmoteBone.ROOT && layer.animated[bone]) {
            int i = bone * EmotePose.VALUES_PER_BONE;
            float[] v = layer.values;
            new Quaternionf().rotationZYX(v[i + 2], v[i + 1], v[i]).transform(p);
            p.add(v[i + 3], -v[i + 4], v[i + 5]);
        }
        p.add(point.pivotX(), point.pivotY(), point.pivotZ());

        Vector3f e = new Vector3f(-p.x / 16.0F, (24.0F - p.y) / 16.0F, p.z / 16.0F);
        int root = EmoteBone.ROOT.ordinal();
        if (layer.animated[root]) {
            int i = root * EmotePose.VALUES_PER_BONE;
            float[] v = layer.values;
            new Quaternionf().rotationZYX(v[i + 2], -v[i + 1], -v[i]).transform(e);
            e.add(-v[i + 3] / 16.0F, v[i + 4] / 16.0F, v[i + 5] / 16.0F);
        }
        e.add(offset[0], offset[1], -offset[2]);
        e.mul(entity.getScale());
        new Quaternionf().rotationY((float) Math.toRadians(180.0F - entity.yBodyRot)).transform(e);
        return entity.position().add(e.x, e.y, e.z);
    }
}
