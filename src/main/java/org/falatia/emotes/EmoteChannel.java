package org.falatia.emotes;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;

final class EmoteChannel {

    enum Lerp { LINEAR, CATMULLROM, STEP }

    private final float[] times;
    private final float[][] pre;
    private final float[][] post;
    private final Lerp[] lerp;

    private EmoteChannel(float[] times, float[][] pre, float[][] post, Lerp[] lerp) {
        this.times = times;
        this.pre = pre;
        this.post = post;
        this.lerp = lerp;
    }

    static EmoteChannel linear(float[] times, float[][] values) {
        Lerp[] lerp = new Lerp[times.length];
        Arrays.fill(lerp, Lerp.LINEAR);
        return new EmoteChannel(times, values, values, lerp);
    }

    float lastTime() {
        return this.times[this.times.length - 1];
    }

    void sample(float t, float[] out, int offset) {
        int last = this.times.length - 1;
        if (t <= this.times[0]) {
            copy(this.pre[0], out, offset);
            return;
        }
        if (t >= this.times[last]) {
            copy(this.post[last], out, offset);
            return;
        }
        int i = 0;
        while (this.times[i + 1] <= t) {
            i++;
        }
        float[] from = this.post[i];
        if (this.lerp[i] == Lerp.STEP) {
            copy(from, out, offset);
            return;
        }
        float[] to = this.pre[i + 1];
        float alpha = (t - this.times[i]) / (this.times[i + 1] - this.times[i]);
        if (this.lerp[i] == Lerp.CATMULLROM || this.lerp[i + 1] == Lerp.CATMULLROM) {
            float[] before = i > 0 ? this.post[i - 1] : from;
            float[] after = i + 2 <= last ? this.pre[i + 2] : to;
            for (int axis = 0; axis < 3; axis++) {
                out[offset + axis] = Mth.catmullrom(alpha, before[axis], from[axis], to[axis], after[axis]);
            }
        } else {
            for (int axis = 0; axis < 3; axis++) {
                out[offset + axis] = Mth.lerp(alpha, from[axis], to[axis]);
            }
        }
    }

    private static void copy(float[] value, float[] out, int offset) {
        System.arraycopy(value, 0, out, offset, 3);
    }

    static @Nullable EmoteChannel parse(@Nullable JsonElement json, float scale) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (!json.isJsonObject()) {
            float[] value = vector(json, scale);
            return new EmoteChannel(new float[] {0.0F}, new float[][] {value}, new float[][] {value}, new Lerp[] {Lerp.LINEAR});
        }
        TreeMap<Float, JsonElement> frames = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            frames.put(Float.parseFloat(entry.getKey()), entry.getValue());
        }
        if (frames.isEmpty()) {
            return null;
        }
        int count = frames.size();
        float[] times = new float[count];
        float[][] pre = new float[count][];
        float[][] post = new float[count][];
        Lerp[] lerp = new Lerp[count];
        int i = 0;
        for (Map.Entry<Float, JsonElement> frame : frames.entrySet()) {
            times[i] = frame.getKey();
            JsonElement value = frame.getValue();
            if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                JsonElement before = object.has("pre") ? object.get("pre") : object.get("post");
                JsonElement after = object.has("post") ? object.get("post") : before;
                pre[i] = vector(before, scale);
                post[i] = vector(after, scale);
                lerp[i] = lerp(object.has("lerp_mode") ? object.get("lerp_mode").getAsString() : "linear");
            } else {
                pre[i] = vector(value, scale);
                post[i] = pre[i];
                lerp[i] = Lerp.LINEAR;
            }
            i++;
        }
        return new EmoteChannel(times, pre, post, lerp);
    }

    private static Lerp lerp(String mode) {
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "catmullrom" -> Lerp.CATMULLROM;
            case "step" -> Lerp.STEP;
            default -> Lerp.LINEAR;
        };
    }

    private static float[] vector(@Nullable JsonElement json, float scale) {
        if (json == null || json.isJsonNull()) {
            return new float[3];
        }
        if (!json.isJsonArray()) {
            float value = number(json) * scale;
            return new float[] {value, value, value};
        }
        JsonArray array = json.getAsJsonArray();
        float[] value = new float[3];
        for (int axis = 0; axis < 3 && axis < array.size(); axis++) {
            value[axis] = number(array.get(axis)) * scale;
        }
        return value;
    }

    private static float number(JsonElement json) {
        if (json.getAsJsonPrimitive().isNumber()) {
            return json.getAsFloat();
        }
        try {
            return Float.parseFloat(json.getAsString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Molang expressions are not supported: " + json.getAsString());
        }
    }
}
