package org.falatia.emotes.interfaces;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.GsonHelper;

import com.mojang.serialization.JsonOps;

import org.falatia.emotes.emotestypes.EmoteLocator;
import org.jetbrains.annotations.Nullable;

public sealed interface EmoteEvent permits EmoteEvent.Sound, EmoteEvent.Particles {

    float time();

    record Sound(float time, Identifier id, float volume, float pitch) implements EmoteEvent {
    }

    final class Particles implements EmoteEvent {
        final float time;
        final JsonObject particle;
        public final String locator;
        public final int count;
        public final float[] spread;
        public final float speed;
        public final float[] offset;
        private @Nullable RegistryAccess parsedFor;
        private @Nullable ParticleOptions parsed;

        Particles(float time, JsonObject particle, String locator, int count, float[] spread, float speed, float[] offset) {
            this.time = time;
            this.particle = particle;
            this.locator = locator;
            this.count = count;
            this.spread = spread;
            this.speed = speed;
            this.offset = offset;
        }

        @Override
        public float time() {
            return this.time;
        }

        @Nullable
        public ParticleOptions options(RegistryAccess registries) {
            if (this.parsedFor != registries) {
                this.parsedFor = registries;
                this.parsed = ParticleTypes.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), this.particle)
                        .result().orElse(null);
            }
            return this.parsed;
        }
    }

    int MAX_COUNT = 64;

    Map<String, String> PRESETS = Map.ofEntries(
            Map.entry("hearts", "{\"effect\":\"minecraft:heart\",\"locator\":\"head\",\"count\":4,\"spread\":[0.4,0.3,0.4]}"),
            Map.entry("notes", "{\"effect\":\"minecraft:note\",\"locator\":\"head\",\"count\":3,\"spread\":[0.5,0.3,0.5]}"),
            Map.entry("happy", "{\"effect\":\"minecraft:happy_villager\",\"locator\":\"body\",\"count\":8,\"spread\":[0.5,0.6,0.5]}"),
            Map.entry("angry", "{\"effect\":\"minecraft:angry_villager\",\"locator\":\"head\",\"count\":3,\"spread\":[0.4,0.2,0.4]}"),
            Map.entry("sparkles", "{\"effect\":\"minecraft:end_rod\",\"locator\":\"body\",\"count\":6,\"spread\":[0.4,0.6,0.4],\"speed\":0.02}"),
            Map.entry("magic", "{\"effect\":\"minecraft:enchant\",\"locator\":\"body\",\"count\":20,\"spread\":[0.6,0.8,0.6],\"speed\":0.5}"),
            Map.entry("smoke", "{\"effect\":\"minecraft:poof\",\"locator\":\"feet\",\"count\":8,\"spread\":[0.4,0.1,0.4],\"speed\":0.02}"),
            Map.entry("splash", "{\"effect\":\"minecraft:splash\",\"locator\":\"head\",\"count\":10,\"spread\":[0.3,0.2,0.3]}"),
            Map.entry("tears", "{\"effect\":\"minecraft:falling_water\",\"locator\":\"head\",\"count\":4,\"spread\":[0.2,0.05,0.2]}"),
            Map.entry("fire", "{\"effect\":\"minecraft:flame\",\"locator\":\"body\",\"count\":6,\"spread\":[0.3,0.5,0.3],\"speed\":0.02}"),
            Map.entry("confetti", "["
                    + "{\"effect\":\"minecraft:dust\",\"options\":{\"color\":[1.0,0.3,0.3],\"scale\":1.0},\"locator\":\"head\",\"count\":4,\"spread\":[0.6,0.4,0.6]},"
                    + "{\"effect\":\"minecraft:dust\",\"options\":{\"color\":[1.0,0.85,0.2],\"scale\":1.0},\"locator\":\"head\",\"count\":4,\"spread\":[0.6,0.4,0.6]},"
                    + "{\"effect\":\"minecraft:dust\",\"options\":{\"color\":[0.3,0.9,0.4],\"scale\":1.0},\"locator\":\"head\",\"count\":4,\"spread\":[0.6,0.4,0.6]},"
                    + "{\"effect\":\"minecraft:dust\",\"options\":{\"color\":[0.3,0.6,1.0],\"scale\":1.0},\"locator\":\"head\",\"count\":4,\"spread\":[0.6,0.4,0.6]}"
                    + "]"));

    static List<EmoteEvent> parse(JsonObject animation) {
        List<EmoteEvent> events = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(animation, "sound_effects", new JsonObject()).entrySet()) {
            float time = Float.parseFloat(entry.getKey());
            for (JsonObject json : objects(entry.getValue())) {
                Identifier id = Identifier.tryParse(GsonHelper.getAsString(json, "effect", ""));
                if (id != null) {
                    events.add(new Sound(time, id, GsonHelper.getAsFloat(json, "volume", 1.0F), GsonHelper.getAsFloat(json, "pitch", 1.0F)));
                }
            }
        }
        for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(animation, "particle_effects", new JsonObject()).entrySet()) {
            float time = Float.parseFloat(entry.getKey());
            for (JsonObject json : objects(entry.getValue())) {
                String effect = GsonHelper.getAsString(json, "effect", "");
                if (effect.startsWith("preset:")) {
                    String preset = PRESETS.get(effect.substring("preset:".length()));
                    if (preset != null) {
                        for (JsonObject part : objects(JsonParser.parseString(preset))) {
                            if (json.has("locator")) {
                                part.add("locator", json.get("locator"));
                            }
                            particles(time, part, events);
                        }
                    }
                } else {
                    particles(time, json, events);
                }
            }
        }
        events.sort(Comparator.comparingDouble(EmoteEvent::time));
        return List.copyOf(events);
    }

    private static void particles(float time, JsonObject json, List<EmoteEvent> events) {
        String effect = GsonHelper.getAsString(json, "effect", "");
        if (Identifier.tryParse(effect) == null) {
            return;
        }
        JsonObject particle = GsonHelper.getAsJsonObject(json, "options", new JsonObject()).deepCopy();
        particle.addProperty("type", effect);
        int count = Math.clamp(GsonHelper.getAsInt(json, "count", 1), 1, MAX_COUNT);
        events.add(new Particles(time, particle, GsonHelper.getAsString(json, "locator", EmoteLocator.BODY), count,
                vector(json.get("spread")), GsonHelper.getAsFloat(json, "speed", 0.0F), vector(json.get("offset"))));
    }

    private static List<JsonObject> objects(JsonElement json) {
        List<JsonObject> objects = new ArrayList<>();
        if (json.isJsonObject()) {
            objects.add(json.getAsJsonObject());
        } else if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    objects.add(element.getAsJsonObject());
                }
            }
        }
        return objects;
    }

    private static float[] vector(@Nullable JsonElement json) {
        float[] value = new float[3];
        if (json instanceof JsonArray array) {
            for (int i = 0; i < 3 && i < array.size(); i++) {
                value[i] = array.get(i).getAsFloat();
            }
        } else if (json != null && json.isJsonPrimitive()) {
            value[0] = value[1] = value[2] = json.getAsFloat();
        }
        return value;
    }
}
