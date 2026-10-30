package org.falatia.emotes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector3f;

public final class MovesEmote {

    private record Part(EmoteBone bone, float x, float y, float z) {
    }

    private static final Map<String, Part> PARTS = Map.of(
            "head", new Part(EmoteBone.HEAD, 0.0F, 0.0F, 0.0F),
            "torso", new Part(EmoteBone.BODY, 0.0F, 0.0F, 0.0F),
            "rightArm", new Part(EmoteBone.RIGHT_ARM, -5.0F, 2.0F, 0.0F),
            "leftArm", new Part(EmoteBone.LEFT_ARM, 5.0F, 2.0F, 0.0F),
            "rightLeg", new Part(EmoteBone.RIGHT_LEG, -1.9F, 12.0F, 0.0F),
            "leftLeg", new Part(EmoteBone.LEFT_LEG, 1.9F, 12.0F, 0.0F),
            "body", new Part(EmoteBone.ROOT, 0.0F, 0.0F, 0.0F));

    private static final String[] AXES = {"pitch", "yaw", "roll", "x", "y", "z", "axis", "bend"};
    private static final int BEND_AXIS = 6;

    private static final float BODY_PIVOT = 0.7F;

    private record Key(int tick, float value, Ease ease) {
    }

    private MovesEmote() {
    }

    public static boolean matches(JsonObject root) {
        return root.has("emote") && root.get("emote").isJsonObject();
    }

    private static final int MAX_TICKS = 20 * 60 * 10;

    public static Emote parse(JsonObject root, String baseName) {
        String id = baseName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        JsonObject emote = GsonHelper.getAsJsonObject(root, "emote");

        int beginTick = Mth.clamp(GsonHelper.getAsInt(emote, "beginTick", 0), 0, MAX_TICKS - 1);
        int endTick = Mth.clamp(GsonHelper.getAsInt(emote, "endTick", beginTick + 1), beginTick + 1, MAX_TICKS);
        int stopTick = Mth.clamp(GsonHelper.getAsInt(emote, "stopTick", endTick), endTick, MAX_TICKS);
        boolean loop = bool(emote.get("isLoop"));
        int returnTick = Mth.clamp(GsonHelper.getAsInt(emote, "returnTick", 0), 0, endTick);
        boolean degrees = !emote.has("degrees") || bool(emote.get("degrees"));
        boolean easeBefore = emote.has("easeBeforeKeyframe") && bool(emote.get("easeBeforeKeyframe"));
        boolean legacyTorso = GsonHelper.getAsInt(root, "version", 1) < 3;

        Map<String, Map<String, List<Key>>> keys = new HashMap<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(emote, "moves")) {
            JsonObject move = element.getAsJsonObject();
            int tick = GsonHelper.getAsInt(move, "tick", 0);
            Ease ease = Ease.byName(GsonHelper.getAsString(move, "easing", "linear"));
            int turn = GsonHelper.getAsInt(move, "turn", 0);
            for (Map.Entry<String, JsonElement> entry : move.entrySet()) {
                if (!PARTS.containsKey(entry.getKey()) || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject axes = entry.getValue().getAsJsonObject();
                String partName = legacyTorso && entry.getKey().equals("torso") ? "body" : entry.getKey();
                for (int axis = 0; axis < AXES.length; axis++) {
                    if (!axes.has(AXES[axis])) {
                        continue;
                    }
                    float value = axes.get(AXES[axis]).getAsFloat();
                    if (axis < 3 || axis >= BEND_AXIS) {
                        value = degrees ? value * Mth.DEG_TO_RAD : value;
                    }
                    if (axis < 3) {
                        value += turn * Mth.TWO_PI;
                    }
                    keys.computeIfAbsent(partName, part -> new HashMap<>())
                            .computeIfAbsent(AXES[axis], name -> new ArrayList<>())
                            .add(new Key(tick, value, ease));
                }
            }
        }

        int lastTick = loop ? endTick + 1 : stopTick;
        EmoteChannel[][] channels = new EmoteChannel[EmoteBone.VALUES.length][Emote.CHANNELS];
        float[][] bodyBend = null;
        float[][] torsoBend = null;
        for (Map.Entry<String, Map<String, List<Key>>> part : keys.entrySet()) {
            Part info = PARTS.get(part.getKey());
            float[] rest = {0.0F, 0.0F, 0.0F, info.x(), info.y(), info.z(), 0.0F, 0.0F};
            List<List<Key>> axes = new ArrayList<>();
            for (String axis : AXES) {
                List<Key> list = new ArrayList<>(part.getValue().getOrDefault(axis, List.of()));
                list.sort(Comparator.comparingInt(Key::tick));
                axes.add(list);
            }
            float[] times = new float[lastTick + 1];
            float[][] rotation = new float[lastTick + 1][];
            float[][] position = new float[lastTick + 1][];
            float[][] bend = new float[lastTick + 1][];
            boolean bends = !axes.get(BEND_AXIS).isEmpty() || !axes.get(BEND_AXIS + 1).isEmpty();
            boolean whole = info.bone() == EmoteBone.ROOT;
            Matrix3f turn = new Matrix3f();
            Vector3f pivot = new Vector3f();
            for (int tick = 0; tick <= lastTick; tick++) {
                int at = loop && tick > endTick ? returnTick : tick;
                float[] v = new float[AXES.length];
                for (int axis = 0; axis < AXES.length; axis++) {
                    v[axis] = valueAt(axes.get(axis), at, rest[axis], endTick, stopTick, loop, easeBefore);
                }
                times[tick] = tick / 20.0F;
                if (whole) {
                    turn.rotationZYX(v[2], v[1], v[0]);
                    turn.transform(pivot.set(0.0F, BODY_PIVOT, 0.0F));
                    rotation[tick] = new float[] {-v[0], -v[1], v[2]};
                    position[tick] = new float[] {-16.0F * (v[3] - pivot.x), 16.0F * (v[4] + BODY_PIVOT - pivot.y),
                            16.0F * (v[5] - pivot.z)};
                } else {
                    rotation[tick] = new float[] {v[0], v[1], v[2]};
                    position[tick] = new float[] {v[3] - rest[3], -(v[4] - rest[4]), v[5] - rest[5]};
                }
                bend[tick] = new float[] {v[6], v[7], 0.0F};
            }
            EmoteChannel[] bone = channels[info.bone().ordinal()];
            bone[Emote.ROTATION] = EmoteChannel.linear(times, rotation);
            bone[Emote.POSITION] = EmoteChannel.linear(times, position);
            if (whole) {
                bodyBend = bends ? bend : null;
            } else if (info.bone() == EmoteBone.BODY) {
                torsoBend = bends ? bend : null;
            } else if (bends) {
                bone[Emote.BEND] = EmoteChannel.linear(times, bend);
            }
        }
        if (bodyBend != null || torsoBend != null) {
            float[][] bend = new float[lastTick + 1][];
            float[] times = new float[lastTick + 1];
            for (int tick = 0; tick <= lastTick; tick++) {
                times[tick] = tick / 20.0F;
                bend[tick] = new float[3];
                for (float[][] source : new float[][][] {bodyBend, torsoBend}) {
                    if (source != null) {
                        bend[tick][0] += source[tick][0];
                        bend[tick][1] += source[tick][1];
                    }
                }
            }
            channels[EmoteBone.BODY.ordinal()][Emote.BEND] = EmoteChannel.linear(times, bend);
        }

        String name = text(root.get("name"), baseName);
        String description = text(root.get("description"), "");
        String author = text(root.get("author"), "");
        float length = lastTick / 20.0F;
        Emote result = new Emote(id, name, description, author, length, loop ? Emote.Loop.LOOP : Emote.Loop.NONE,
                loop ? returnTick / 20.0F : 0.0F, channels, List.of());
        JsonObject pair = GsonHelper.getAsJsonObject(GsonHelper.getAsJsonObject(root, "falatia", new JsonObject()), "pair", null);
        if (pair != null && GsonHelper.isObjectNode(pair, "partner") && matches(GsonHelper.getAsJsonObject(pair, "partner"))) {
            Emote.attachPair(result, pair, parse(GsonHelper.getAsJsonObject(pair, "partner"), baseName + ".partner"));
        }
        return result;
    }

    private static float valueAt(List<Key> keys, int tick, float rest, int endTick, int stopTick, boolean loop, boolean easeBefore) {
        if (keys.isEmpty()) {
            return rest;
        }
        Key before = null;
        Key after = null;
        for (Key key : keys) {
            if (key.tick() <= tick) {
                before = key;
            } else {
                after = key;
                break;
            }
        }
        if (before == null) {
            before = new Key(0, rest, Ease.LINEAR);
        }
        if (after == null) {
            if (loop || tick <= endTick || stopTick <= endTick) {
                return before.value();
            }
            float alpha = (tick - endTick) / (float) (stopTick - endTick);
            return Mth.lerp(Ease.IN_OUT_SINE.apply(alpha), before.value(), rest);
        }
        float alpha = (tick - before.tick()) / (float) (after.tick() - before.tick());
        Ease ease = easeBefore ? before.ease() : after.ease();
        return Mth.lerp(ease.apply(alpha), before.value(), after.value());
    }

    private static boolean bool(@Nullable JsonElement json) {
        return json != null && json.isJsonPrimitive()
                && (json.getAsJsonPrimitive().isBoolean() ? json.getAsBoolean() : Boolean.parseBoolean(json.getAsString()));
    }

    private static String text(@Nullable JsonElement json, String fallback) {
        String text = null;
        if (json != null && json.isJsonPrimitive()) {
            text = json.getAsString();
        } else if (json != null && json.isJsonObject()) {
            JsonObject component = json.getAsJsonObject();
            if (component.has("text")) {
                text = GsonHelper.getAsString(component, "text");
            } else if (component.has("translate")) {
                String key = GsonHelper.getAsString(component, "translate");
                text = Language.getInstance().getOrDefault(key, GsonHelper.getAsString(component, "fallback", key));
            }
        }
        if (text == null) {
            return fallback;
        }
        String plain = ChatFormatting.stripFormatting(text);
        return plain == null || plain.isBlank() ? fallback : plain.strip().replaceAll("\\s+", " ");
    }

    enum Ease {
        LINEAR, CONSTANT,
        IN_SINE, OUT_SINE, IN_OUT_SINE,
        IN_QUAD, OUT_QUAD, IN_OUT_QUAD,
        IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC,
        IN_QUART, OUT_QUART, IN_OUT_QUART,
        IN_QUINT, OUT_QUINT, IN_OUT_QUINT,
        IN_EXPO, OUT_EXPO, IN_OUT_EXPO,
        IN_CIRC, OUT_CIRC, IN_OUT_CIRC,
        IN_BACK, OUT_BACK, IN_OUT_BACK,
        IN_ELASTIC, OUT_ELASTIC, IN_OUT_ELASTIC,
        IN_BOUNCE, OUT_BOUNCE, IN_OUT_BOUNCE;

        static Ease byName(String name) {
            String key = name.toUpperCase(Locale.ROOT).replace("_", "");
            if (key.startsWith("EASE")) {
                key = key.substring(4);
            }
            for (Ease ease : values()) {
                if (ease.name().replace("_", "").equals(key)) {
                    return ease;
                }
            }
            return LINEAR;
        }

        float apply(float t) {
            String type = this.name().substring(this.name().lastIndexOf('_') + 1);
            if (this == LINEAR) {
                return t;
            }
            if (this == CONSTANT) {
                return t < 1.0F ? 0.0F : 1.0F;
            }
            if (this.name().startsWith("IN_OUT_")) {
                return t < 0.5F ? in(type, t * 2.0F) / 2.0F : 1.0F - in(type, (1.0F - t) * 2.0F) / 2.0F;
            }
            if (this.name().startsWith("OUT_")) {
                return 1.0F - in(type, 1.0F - t);
            }
            return in(type, t);
        }

        private static float in(String type, float t) {
            return switch (type) {
                case "SINE" -> 1.0F - Mth.cos(t * Mth.HALF_PI);
                case "QUAD" -> t * t;
                case "CUBIC" -> t * t * t;
                case "QUART" -> t * t * t * t;
                case "QUINT" -> t * t * t * t * t;
                case "EXPO" -> t <= 0.0F ? 0.0F : (float) Math.pow(2.0, 10.0 * t - 10.0);
                case "CIRC" -> 1.0F - Mth.sqrt(1.0F - t * t);
                case "BACK" -> 2.70158F * t * t * t - 1.70158F * t * t;
                case "ELASTIC" -> t <= 0.0F || t >= 1.0F ? t
                        : (float) (-Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * (2.0 * Math.PI / 3.0)));
                case "BOUNCE" -> 1.0F - bounceOut(1.0F - t);
                default -> t;
            };
        }

        private static float bounceOut(float t) {
            if (t < 1.0F / 2.75F) {
                return 7.5625F * t * t;
            }
            if (t < 2.0F / 2.75F) {
                t -= 1.5F / 2.75F;
                return 7.5625F * t * t + 0.75F;
            }
            if (t < 2.5F / 2.75F) {
                t -= 2.25F / 2.75F;
                return 7.5625F * t * t + 0.9375F;
            }
            t -= 2.625F / 2.75F;
            return 7.5625F * t * t + 0.984375F;
        }
    }
}
