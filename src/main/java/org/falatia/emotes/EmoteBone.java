package org.falatia.emotes;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

public enum EmoteBone {
    HEAD,
    BODY,
    RIGHT_ARM,
    LEFT_ARM,
    RIGHT_LEG,
    LEFT_LEG,
    ROOT;

    static final EmoteBone[] VALUES = values();

    static @Nullable EmoteBone byName(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace("_", "");
        for (EmoteBone bone : VALUES) {
            if (bone.name().toLowerCase(Locale.ROOT).replace("_", "").equals(key)) {
                return bone;
            }
        }
        return null;
    }
}
