package org.falatia.emotes.emotestypes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import org.falatia.emotes.Emote;
import org.falatia.emotes.Emotes;
import org.falatia.emotes.FalatiaEmotes;
import org.falatia.emotes.SmoothTexture;
import org.jetbrains.annotations.Nullable;

public final class EmoteIcons {

    public record Icon(Identifier id, int width, int height) {
    }

    private static final Map<String, @Nullable Icon> icons = new HashMap<>();

    private EmoteIcons() {
    }

    public static @Nullable Icon get(Emote emote) {
        Emote current = Emotes.get(emote.id());
        if (current != null) {
            emote = current;
        }
        if (icons.containsKey(emote.id())) {
            return icons.get(emote.id());
        }
        Icon icon = load(emote);
        icons.put(emote.id(), icon);
        return icon;
    }

    static final int DISPLAY_SIZE = 36;

    private static final int MAX_SERVER_SIZE = 1024;

    private static @Nullable Icon load(Emote emote) {
        Path file = emote.icon();
        byte[] data = emote.iconData();
        if (data != null && !fits(data)) {
            FalatiaEmotes.LOGGER.warn("Emotes: server icon of {} is not a PNG up to {}px", emote.id(), MAX_SERVER_SIZE);
            return null;
        }
        if (file == null && data == null) {
            return null;
        }
        try (InputStream input = data != null ? new ByteArrayInputStream(data) : Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(input);
            int screen = DISPLAY_SIZE * Minecraft.getInstance().getWindow().getGuiScale();
            while (image.getWidth() >= screen * 2 && image.getHeight() >= screen * 2) {
                NativeImage half = halve(image);
                image.close();
                image = half;
            }
            Identifier id = FalatiaEmotes.id("emote_icon/" + emote.id());
            DynamicTexture texture = image.getWidth() < screen
                    ? new DynamicTexture(id::toString, image)
                    : new SmoothTexture(id::toString, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return new Icon(id, image.getWidth(), image.getHeight());
        } catch (IOException | RuntimeException e) {
            FalatiaEmotes.LOGGER.warn("Emotes: can't read icon of {}: {}", emote.id(), e.toString());
            return null;
        }
    }

    private static boolean fits(byte[] png) {
        if (png.length < 24 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
            return false;
        }
        ByteBuffer header = ByteBuffer.wrap(png, 16, 8);
        int width = header.getInt();
        int height = header.getInt();
        return width > 0 && height > 0 && width <= MAX_SERVER_SIZE && height <= MAX_SERVER_SIZE;
    }

    private static NativeImage halve(NativeImage image) {
        int width = image.getWidth() / 2;
        int height = image.getHeight() / 2;
        NativeImage half = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                for (int i = 0; i < 4; i++) {
                    int pixel = image.getPixel(x * 2 + (i & 1), y * 2 + (i >> 1));
                    int alpha = ARGB.alpha(pixel);
                    a += alpha;
                    r += ARGB.red(pixel) * alpha;
                    g += ARGB.green(pixel) * alpha;
                    b += ARGB.blue(pixel) * alpha;
                }
                half.setPixel(x, y, a == 0 ? 0 : ARGB.color(a / 4, r / a, g / a, b / a));
            }
        }
        return half;
    }

    public static void clear() {
        for (Icon icon : icons.values()) {
            if (icon != null) {
                Minecraft.getInstance().getTextureManager().release(icon.id());
            }
        }
        icons.clear();
    }
}
