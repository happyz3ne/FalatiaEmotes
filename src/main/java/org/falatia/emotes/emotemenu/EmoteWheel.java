package org.falatia.emotes.emotemenu;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.falatia.emotes.Emote;
import org.falatia.emotes.Emotes;
import org.falatia.emotes.FalatiaEmotes;
import org.jetbrains.annotations.Nullable;

public final class EmoteWheel {

    static final int SLOTS = 8;
    static final int MAX_PAGES = 6;

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve(FalatiaEmotes.MOD_ID).resolve("emotes.properties");
    private static final String WHEEL = "wheel";
    private static final String THIRD_PERSON = "third_person";
    private static final String RELEASE_SELECTS = "release_selects";
    private static final String WALKING = "walking";
    private static final String KEY_PREFIX = "key.";

    private static KeyMapping key;
    private static List<String> layout = List.of();
    static boolean thirdPerson = true;
    static boolean releaseSelects = true;
    private static final Set<String> walking = new HashSet<>();
    private static final Map<String, InputConstants.Key> keys = new HashMap<>();
    private static final Set<String> held = new HashSet<>();
    static int page;

    private EmoteWheel() {
    }

    public static void register() {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.falatia_emotes.wheel", InputConstants.KEY_B, FalatiaEmotes.KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(EmoteWheel::tick);
    }

    public static Screen wheelScreen() {
        return new EmoteWheelScreen();
    }

    public static Screen configScreen() {
        return configScreen(null);
    }

    public static Screen configScreen(@Nullable Screen parent) {
        return new EmoteWheelConfigScreen(parent);
    }

    static KeyMapping key() {
        return key;
    }

    public static boolean thirdPerson() {
        return thirdPerson;
    }

    public static boolean walking(String id) {
        return walking.contains(id) && !paired(id);
    }

    static boolean paired(String id) {
        Emote emote = Emotes.get(id);
        return emote != null && emote.pair() != null;
    }

    static void setWalking(String id, boolean value) {
        if (value) {
            walking.add(id);
        } else {
            walking.remove(id);
        }
    }

    public static Component keyName(InputConstants.Key key) {
        String name = key.getName();
        String prefix = "key.keyboard.";
        if (name.startsWith(prefix) && name.length() == prefix.length() + 1) {
            return Component.literal(name.substring(prefix.length()).toUpperCase(Locale.ROOT));
        }
        return key.getDisplayName();
    }

    static @Nullable InputConstants.Key emoteKey(String id) {
        return keys.get(id);
    }

    static @Nullable KeyMapping conflict(InputConstants.Key key) {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.matches(key)) {
                return mapping;
            }
        }
        return null;
    }

    static void setEmoteKey(String id, @Nullable InputConstants.Key value) {
        if (value == null) {
            keys.remove(id);
        } else {
            keys.values().removeIf(value::equals);
            keys.put(id, value);
        }
    }

    private static void tick(Minecraft minecraft) {
        while (key.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new EmoteWheelScreen());
            }
        }
        if (minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }
        for (Map.Entry<String, InputConstants.Key> entry : keys.entrySet()) {
            if (!InputConstants.isKeyDown(entry.getValue().getValue())) {
                held.remove(entry.getKey());
            } else if (held.add(entry.getKey()) && conflict(entry.getValue()) == null) {
                Emotes.toggle(entry.getKey());
            }
        }
    }

    static List<@Nullable Emote> slots() {
        List<@Nullable Emote> slots = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (String id : layout) {
            Emote emote = id.isEmpty() ? null : Emotes.get(id);
            slots.add(emote != null && placed.add(id) ? emote : null);
        }
        while (!slots.isEmpty() && slots.getLast() == null) {
            slots.removeLast();
        }
        return slots;
    }

    static List<String> layout() {
        return new ArrayList<>(layout);
    }

    static void setLayout(List<String> ids) {
        List<String> trimmed = new ArrayList<>(ids);
        while (!trimmed.isEmpty() && trimmed.getLast().isEmpty()) {
            trimmed.removeLast();
        }
        layout = List.copyOf(trimmed);
    }

    public static void load() {
        Properties properties = new Properties();
        if (Files.isRegularFile(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                properties.load(reader);
            } catch (IOException e) {
                FalatiaEmotes.LOGGER.warn("Emotes: can't read {}: {}", FILE, e.toString());
            }
        }
        layout = Arrays.stream(properties.getProperty(WHEEL, "").split(",", -1))
                .map(id -> id.strip().toLowerCase(Locale.ROOT))
                .toList();
        setLayout(layout);
        thirdPerson = Boolean.parseBoolean(properties.getProperty(THIRD_PERSON, "true"));
        releaseSelects = Boolean.parseBoolean(properties.getProperty(RELEASE_SELECTS, "true"));
        walking.clear();
        for (String id : properties.getProperty(WALKING, "").split(",")) {
            if (!id.isBlank()) {
                walking.add(id.strip().toLowerCase(Locale.ROOT));
            }
        }
        keys.clear();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(KEY_PREFIX)) {
                try {
                    keys.put(name.substring(KEY_PREFIX.length()), InputConstants.getKey(properties.getProperty(name)));
                } catch (IllegalArgumentException e) {
                    FalatiaEmotes.LOGGER.warn("Emotes: unknown key {} for {}", properties.getProperty(name), name);
                }
            }
        }
    }

    static void save() {
        Properties properties = new Properties();
        properties.setProperty(WHEEL, String.join(",", layout));
        properties.setProperty(THIRD_PERSON, Boolean.toString(thirdPerson));
        properties.setProperty(RELEASE_SELECTS, Boolean.toString(releaseSelects));
        properties.setProperty(WALKING, String.join(",", walking.stream().sorted().toList()));
        for (Map.Entry<String, InputConstants.Key> entry : keys.entrySet()) {
            properties.setProperty(KEY_PREFIX + entry.getKey(), entry.getValue().getName());
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                properties.store(writer, "Falatia emote wheel");
            }
        } catch (IOException e) {
            FalatiaEmotes.LOGGER.warn("Emotes: can't save {}: {}", FILE, e.toString());
        }
    }
}
