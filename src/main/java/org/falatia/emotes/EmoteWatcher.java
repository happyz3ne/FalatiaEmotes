package org.falatia.emotes;

import org.falatia.emotes.emotestypes.EmoteLoader;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Locale;
import java.util.stream.Stream;

final class EmoteWatcher {

    private static final long QUIET_MS = 400L;

    private static volatile long changedAt;

    private EmoteWatcher() {
    }

    static void start() {
        Thread thread = new Thread(EmoteWatcher::run, "Falatia emote watcher");
        thread.setDaemon(true);
        thread.start();
    }

    static boolean poll() {
        long at = changedAt;
        if (at == 0L || System.currentTimeMillis() - at < QUIET_MS) {
            return false;
        }
        changedAt = 0L;
        return true;
    }

    private static void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            registerTree(watcher, EmoteLoader.DIR);
            while (true) {
                WatchKey key = watcher.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        changedAt = System.currentTimeMillis();
                    } else if (event.context() instanceof Path name && key.watchable() instanceof Path dir) {
                        Path file = dir.resolve(name);
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(file)) {
                            registerTree(watcher, file);
                            changedAt = System.currentTimeMillis();
                        } else if (relevant(name.toString()) || event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            changedAt = System.currentTimeMillis();
                        }
                    }
                }
                if (!key.reset() && key.watchable().equals(EmoteLoader.DIR)) {
                    FalatiaEmotes.LOGGER.warn("Emotes: {} is gone, no longer watching", EmoteLoader.DIR);
                    return;
                }
            }
        } catch (IOException e) {
            FalatiaEmotes.LOGGER.warn("Emotes: can't watch {}: {}", EmoteLoader.DIR, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void registerTree(WatchService watcher, Path root) throws IOException {
        try (Stream<Path> dirs = Files.walk(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            }
        }
    }

    private static boolean relevant(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.contains(".tmp") && (lower.endsWith(".json") || lower.endsWith(".png") || lower.endsWith(".zip"));
    }
}
