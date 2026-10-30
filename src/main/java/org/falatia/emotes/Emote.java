package org.falatia.emotes;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

import org.falatia.emotes.interfaces.EmoteEvent;
import org.jetbrains.annotations.Nullable;

public final class Emote {

    public enum Loop {
        NONE,
        LOOP,
        HOLD
    }

    static final int ROTATION = 0;
    static final int POSITION = 1;
    static final int SCALE = 2;
    static final int BEND = 3;
    static final int CHANNELS = 4;

    private String id;
    private final String name;
    private final String description;
    private final String author;
    private final float length;
    private final Loop loop;
    private final float loopStart;
    private @Nullable Path icon;
    private byte @Nullable [] iconData;
    private @Nullable EmoteSource source;
    private final EmoteChannel[][] channels;
    private final List<EmoteEvent> events;
    private @Nullable Pair pair;

    record Pair(Emote partner, float right, float up, float forward, float facing, float waitStart, float waitEnd) {

        float waitTime(float seconds) {
            if (seconds <= this.waitStart) {
                return seconds;
            }
            float span = this.waitEnd - this.waitStart;
            return span <= 1.0E-3F ? this.waitStart : this.waitStart + (seconds - this.waitStart) % span;
        }
    }

    Emote(String id, String name, String description, String author, float length, Loop loop, float loopStart,
            EmoteChannel[][] channels, List<EmoteEvent> events) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.author = author;
        this.length = length;
        this.loop = loop;
        this.loopStart = Mth.clamp(loopStart, 0.0F, length);
        this.channels = channels;
        this.events = events;
    }

    public String id() {
        return this.id;
    }

    public void prefixId(String prefix) {
        this.id = prefix + this.id;
    }

    public String name() {
        return Language.getInstance().getOrDefault(this.name);
    }

    public String description() {
        return this.description.isEmpty() ? "" : Language.getInstance().getOrDefault(this.description);
    }

    public String author() {
        return this.author;
    }

    public String subtitle() {
        String description = this.description();
        if (this.author.isEmpty()) {
            return description;
        }
        String author = Component.translatable("falatia_emotes.author", this.author).getString();
        return description.isEmpty() ? author : description + " · " + author;
    }

    public float length() {
        return this.length;
    }

    public Loop loop() {
        return this.loop;
    }

    public @Nullable Pair pair() {
        return this.pair;
    }

    public @Nullable EmoteSource source() {
        return this.source;
    }

    public void setSource(@Nullable EmoteSource source) {
        this.source = source;
    }

    public @Nullable Path icon() {
        return this.icon;
    }

    public void setIcon(@Nullable Path icon) {
        this.icon = icon;
    }

    public byte @Nullable [] iconData() {
        return this.iconData;
    }

    public void setIconData(byte @Nullable [] iconData) {
        this.iconData = iconData;
    }

    private float local(float seconds) {
        if (this.loop == Loop.LOOP && seconds > this.length && this.length > this.loopStart) {
            return this.loopStart + (seconds - this.loopStart) % (this.length - this.loopStart);
        }
        return Math.min(seconds, this.length);
    }

    void events(float from, float to, Consumer<EmoteEvent> action) {
        if (this.events.isEmpty() || to <= from) {
            return;
        }
        float a = from < 0.0F ? -1.0F : this.local(from);
        float b = this.local(to);
        if (b >= a) {
            fire(a, b, action);
        } else {
            fire(a, this.length, action);
            fire(this.loopStart - 1.0E-4F, b, action);
        }
    }

    private void fire(float from, float to, Consumer<EmoteEvent> action) {
        for (EmoteEvent event : this.events) {
            if (event.time() > from && event.time() <= to) {
                action.accept(event);
            }
        }
    }

    public void sample(float seconds, EmotePose.Layer layer) {
        float t = this.local(seconds);
        for (EmoteBone bone : EmoteBone.VALUES) {
            EmoteChannel[] boneChannels = this.channels[bone.ordinal()];
            boolean animated = false;
            int base = bone.ordinal() * EmotePose.VALUES_PER_BONE;
            for (int channel = ROTATION; channel < CHANNELS; channel++) {
                int offset = base + channel * 3;
                if (boneChannels[channel] != null) {
                    boneChannels[channel].sample(t, layer.values, offset);
                    animated = true;
                } else {
                    float rest = channel == SCALE ? 1.0F : 0.0F;
                    layer.values[offset] = rest;
                    layer.values[offset + 1] = rest;
                    layer.values[offset + 2] = rest;
                }
            }
            layer.animated[bone.ordinal()] = animated;
        }
    }

    public static Emote parse(JsonObject animations, String key) {
        JsonObject json = GsonHelper.getAsJsonObject(animations, key);
        Emote emote = parse(key, json);
        JsonObject pair = GsonHelper.getAsJsonObject(GsonHelper.getAsJsonObject(json, "falatia", new JsonObject()), "pair", null);
        if (pair == null) {
            return emote;
        }
        String partnerKey = GsonHelper.getAsString(pair, "partner", key + ".partner");
        if (!animations.has(partnerKey) || partnerKey.equals(key)) {
            return emote;
        }
        attachPair(emote, pair, parse(partnerKey, GsonHelper.getAsJsonObject(animations, partnerKey)));
        return emote;
    }

    static void attachPair(Emote emote, JsonObject pair, Emote partner) {
        float[] offset = floats(pair.get("offset"), new float[] {0.0F, 0.0F, 1.0F});
        float[] wait = floats(pair.get("wait"), new float[] {0.0F, 0.0F});
        float waitStart = Mth.clamp(wait[0], 0.0F, emote.length);
        float waitEnd = Mth.clamp(wait.length > 1 ? wait[1] : wait[0], waitStart, emote.length);
        emote.pair = new Pair(partner, offset[0], offset[1], offset[2], GsonHelper.getAsFloat(pair, "facing", 180.0F), waitStart, waitEnd);
    }

    public static Set<String> partnerKeys(JsonObject animations) {
        Set<String> keys = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject meta = GsonHelper.getAsJsonObject(entry.getValue().getAsJsonObject(), "falatia", new JsonObject());
            JsonObject pair = GsonHelper.getAsJsonObject(meta, "pair", null);
            if (pair != null) {
                String partner = GsonHelper.getAsString(pair, "partner", entry.getKey() + ".partner");
                if (animations.has(partner) && !partner.equals(entry.getKey())) {
                    keys.add(partner);
                }
            }
        }
        return keys;
    }

    private static float[] floats(@Nullable JsonElement json, float[] fallback) {
        if (json == null || !json.isJsonArray()) {
            return fallback;
        }
        float[] values = fallback.clone();
        for (int i = 0; i < values.length && i < json.getAsJsonArray().size(); i++) {
            values[i] = json.getAsJsonArray().get(i).getAsFloat();
        }
        return values;
    }

    static Emote parse(String key, JsonObject json) {
        String id = id(key);
        JsonElement loopJson = json.get("loop");
        Loop loop = Loop.NONE;
        if (loopJson != null && loopJson.isJsonPrimitive()) {
            if (loopJson.getAsJsonPrimitive().isBoolean()) {
                loop = loopJson.getAsBoolean() ? Loop.LOOP : Loop.NONE;
            } else if ("hold_on_last_frame".equals(loopJson.getAsString())) {
                loop = Loop.HOLD;
            }
        }
        EmoteChannel[][] channels = new EmoteChannel[EmoteBone.VALUES.length][CHANNELS];
        float lastFrame = 0.0F;
        for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, "bones", new JsonObject()).entrySet()) {
            EmoteBone bone = EmoteBone.byName(entry.getKey());
            if (bone == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject boneJson = entry.getValue().getAsJsonObject();
            EmoteChannel[] boneChannels = channels[bone.ordinal()];
            boneChannels[ROTATION] = EmoteChannel.parse(boneJson.get("rotation"), Mth.DEG_TO_RAD);
            boneChannels[POSITION] = EmoteChannel.parse(boneJson.get("position"), 1.0F);
            boneChannels[SCALE] = EmoteChannel.parse(boneJson.get("scale"), 1.0F);
            boneChannels[BEND] = EmoteChannel.parse(boneJson.get("bend"), Mth.DEG_TO_RAD);
            for (EmoteChannel channel : boneChannels) {
                if (channel != null) {
                    lastFrame = Math.max(lastFrame, channel.lastTime());
                }
            }
        }
        float length = GsonHelper.getAsFloat(json, "animation_length", lastFrame);
        JsonObject meta = GsonHelper.getAsJsonObject(json, "falatia", new JsonObject());
        String name = GsonHelper.getAsString(meta, "name", id);
        String description = GsonHelper.getAsString(meta, "description", "").strip();
        String author = GsonHelper.getAsString(meta, "author", "").strip();
        float loopStart = GsonHelper.getAsFloat(meta, "loop_start", 0.0F);
        return new Emote(id, name, description, author, length, loop, loopStart, channels, EmoteEvent.parse(json));
    }

    private static String id(String key) {
        return key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public static String iconName(JsonObject animations, String key) {
        JsonObject meta = GsonHelper.getAsJsonObject(GsonHelper.getAsJsonObject(animations, key), "falatia", new JsonObject());
        return GsonHelper.getAsString(meta, "icon", id(key) + ".png");
    }
}
