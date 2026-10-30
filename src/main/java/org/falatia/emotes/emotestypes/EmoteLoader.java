package org.falatia.emotes.emotestypes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.util.GsonHelper;

import org.falatia.emotes.Emote;
import org.falatia.emotes.EmoteSource;
import org.falatia.emotes.FalatiaEmotes;
import org.falatia.emotes.MovesEmote;
import org.jetbrains.annotations.Nullable;

public final class EmoteLoader {

    public static final String SERVER_PREFIX = "server/";

    public static final Path DIR = FabricLoader.getInstance().getConfigDir()
            .resolve(FalatiaEmotes.MOD_ID).resolve("emotes");

    private static final int MAX_DEPTH = 4;

    private static final List<FileSystem> archives = new ArrayList<>();

    private EmoteLoader() {
    }

    public static Map<String, Emote> load() {
        for (FileSystem archive : archives) {
            try {
                archive.close();
            } catch (IOException ignored) {
            }
        }
        archives.clear();
        Map<String, Emote> emotes = new LinkedHashMap<>();
        FabricLoader.getInstance().getModContainer(FalatiaEmotes.MOD_ID)
                .flatMap(mod -> mod.findPath("assets/" + FalatiaEmotes.MOD_ID + "/emotes"))
                .ifPresent(dir -> loadDirectory(dir, emotes));
        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            FalatiaEmotes.LOGGER.warn("Emotes: can't create {}: {}", DIR, e.toString());
        }
        loadDirectory(DIR, emotes);
        return emotes;
    }

    private static void loadDirectory(Path dir, Map<String, Emote> emotes) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir, MAX_DEPTH)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            FalatiaEmotes.LOGGER.warn("Emotes: can't list {}: {}", dir, e.toString());
            return;
        }
        for (Path file : files) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".json")) {
                loadFile(file, emotes);
            } else if (name.endsWith(".zip")) {
                loadArchive(file, emotes);
            }
        }
    }

    private static void loadArchive(Path zip, Map<String, Emote> emotes) {
        try {
            FileSystem archive = FileSystems.newFileSystem(zip);
            archives.add(archive);
            for (Path root : archive.getRootDirectories()) {
                loadDirectory(root, emotes);
            }
        } catch (IOException | RuntimeException e) {
            FalatiaEmotes.LOGGER.warn("Emotes: can't open {}: {}", zip, e.toString());
        }
    }

    private static void loadFile(Path file, Map<String, Emote> emotes) {
        try {
            Path dir = file.getParent();
            loadJson(file.getFileName().toString(), Files.readAllBytes(file), emotes, false, (emote, name) -> {
                Path icon = dir.resolve(name);
                if (!Files.isRegularFile(icon)) {
                    return false;
                }
                emote.setIcon(icon);
                return true;
            });
        } catch (IOException | RuntimeException e) {
            FalatiaEmotes.LOGGER.warn("Emotes: can't read {}: {}", file, e.toString());
        }
    }

    public static Map<String, Emote> loadServer(Map<String, byte[]> files) {
        Map<String, Emote> emotes = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String path = entry.getKey();
            if (!path.toLowerCase(Locale.ROOT).endsWith(".json")) {
                continue;
            }
            int slash = path.lastIndexOf('/');
            String dir = path.substring(0, slash + 1);
            try {
                loadJson(path.substring(slash + 1), entry.getValue(), emotes, true, (emote, name) -> {
                    byte[] icon = files.get(resolve(dir, name));
                    if (icon == null) {
                        return false;
                    }
                    emote.setIconData(icon);
                    return true;
                });
            } catch (RuntimeException e) {
                FalatiaEmotes.LOGGER.warn("Emotes: can't read server emote {}: {}", path, e.toString());
            }
        }
        Map<String, Emote> prefixed = new LinkedHashMap<>();
        for (Emote emote : emotes.values()) {
            emote.prefixId(SERVER_PREFIX);
            prefixed.put(emote.id(), emote);
        }
        return prefixed;
    }

    private interface Icons {
        boolean attach(Emote emote, String name);
    }

    private static void loadJson(String fileName, byte[] bytes, Map<String, Emote> emotes, boolean server, Icons icons) {
        JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject root = json.getAsJsonObject();
        if (MovesEmote.matches(root)) {
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            Emote emote = MovesEmote.parse(root, baseName);
            icons.attach(emote, baseName + ".png");
            emote.setSource(share(EmoteSource.MOVES, baseName, bytes, server, fileName));
            emotes.put(emote.id(), emote);
        } else if (root.has("animations")) {
            JsonObject animations = GsonHelper.getAsJsonObject(root, "animations");
            Set<String> partners = Emote.partnerKeys(animations);
            String fallbackIcon = animations.size() - partners.size() == 1 ? sibling(fileName, ".png") : null;
            for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
                if (partners.contains(entry.getKey())) {
                    continue;
                }
                Emote emote = Emote.parse(animations, entry.getKey());
                if (!icons.attach(emote, Emote.iconName(animations, entry.getKey())) && fallbackIcon != null) {
                    icons.attach(emote, fallbackIcon);
                }
                emote.setSource(share(EmoteSource.BEDROCK, entry.getKey(), bytes, server, fileName));
                emotes.put(emote.id(), emote);
            }
        }
    }

    private static String sibling(String fileName, String extension) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        int end = lower.endsWith(".animation.json") ? fileName.length() - ".animation.json".length() : fileName.lastIndexOf('.');
        return fileName.substring(0, end) + extension;
    }

    private static String resolve(String dir, String name) {
        List<String> result = new ArrayList<>();
        for (String part : (dir + name).replace('\\', '/').split("/")) {
            if (part.equals("..")) {
                if (!result.isEmpty()) {
                    result.removeLast();
                }
            } else if (!part.isEmpty() && !part.equals(".")) {
                result.add(part);
            }
        }
        return String.join("/", result);
    }

    private static @Nullable EmoteSource share(int format, String key, byte[] json, boolean server, String file) {
        if (server) {
            return EmoteSource.packServer(format, key, json);
        }
        EmoteSource source = EmoteSource.pack(format, key, json);
        if (source == null) {
            FalatiaEmotes.LOGGER.warn("Emotes: {} is too big to share, others won't see it", file);
        }
        return source;
    }
}
