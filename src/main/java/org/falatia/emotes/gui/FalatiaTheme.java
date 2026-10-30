package org.falatia.emotes.gui;

public final class FalatiaTheme {

    private FalatiaTheme() {
    }

    public static final int BG = 0xFF07070A;
    public static final int BG2 = 0xFF0B0A10;
    public static final int TEXT = 0xFFF3EFF7;
    public static final int MUTED = 0xFF9A92A8;
    public static final int ACCENT = 0xFFC457DA;
    public static final int ONLINE = 0xFF3CFF97;

    public static final int ACCENT_LIGHT = 0xFFF0B8FA;
    public static final int ACCENT_SOFT = argb(0x8C, 0xC4, 0x57, 0xDA);
    public static final int ACCENT_SHEEN = argb(0x26, 0xC4, 0x57, 0xDA);
    public static final int BG_GLASS = argb(0xB8, 0x07, 0x07, 0x0A);
    public static final int BG2_GLASS = argb(0xC8, 0x0B, 0x0A, 0x10);
    public static final int BG2_HOVER = argb(0xE6, 0x1C, 0x10, 0x26);

    public static final int HAIRLINE = argb(0x17, 0xFF, 0xFF, 0xFF);
    public static final int CARD_SHEEN_TOP = argb(0x0D, 0xFF, 0xFF, 0xFF);
    public static final int CARD_SHEEN_BOTTOM = argb(0x04, 0xFF, 0xFF, 0xFF);
    public static final int BACKDROP_TINT = argb(0x59, 0x07, 0x07, 0x0A);

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
