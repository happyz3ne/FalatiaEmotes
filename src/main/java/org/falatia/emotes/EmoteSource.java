package org.falatia.emotes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.util.GsonHelper;

import org.jetbrains.annotations.Nullable;

public record EmoteSource(long hash, byte[] packed) {

    public static final int BEDROCK = 0;
    public static final int MOVES = 1;

    static final int MAX_PACKED = 30000;
    private static final int MAX_UNPACKED = 2 * 1024 * 1024;

    public static @Nullable EmoteSource pack(int format, String key, byte[] json) {
        return pack(format, key, json, MAX_PACKED);
    }

    public static EmoteSource packServer(int format, String key, byte[] json) {
        return pack(format, key, json, Integer.MAX_VALUE);
    }

    private static @Nullable EmoteSource pack(int format, String key, byte[] json, int limit) {
        try {
            byte[] content = content(format, key, json);
            ByteArrayOutputStream packed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(packed)) {
                gzip.write(content);
            }
            if (packed.size() > limit) {
                return null;
            }
            return new EmoteSource(hash(content), packed.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    boolean shareable() {
        return this.packed.length <= MAX_PACKED;
    }

    static @Nullable Emote unpack(long hash, byte[] packed) {
        try {
            byte[] content = unzip(packed, MAX_UNPACKED);
            if (hash(content) != hash) {
                return null;
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(content));
            int format = in.readUnsignedByte();
            String key = in.readUTF();
            byte[] json = in.readAllBytes();
            JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            Emote emote = switch (format) {
                case BEDROCK -> Emote.parse(GsonHelper.getAsJsonObject(root, "animations"), key);
                case MOVES -> MovesEmote.parse(root, key);
                default -> null;
            };
            if (emote != null) {
                emote.setSource(new EmoteSource(hash, packed));
            }
            return emote;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] content(int format, String key, byte[] json) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(format);
        out.writeUTF(key);
        out.write(json);
        return bytes.toByteArray();
    }

    static byte[] unzip(byte[] packed, int limit) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            byte[] content = in.readNBytes(limit + 1);
            if (content.length > limit) {
                throw new IOException("Emote too big");
            }
            return content;
        }
    }

    private static long hash(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = hash << 8 | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
