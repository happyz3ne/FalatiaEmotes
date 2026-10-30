package org.falatia.emotes;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

import org.falatia.emotes.emotestypes.EmoteIcons;
import org.falatia.emotes.gui.FalatiaTheme;
import org.falatia.emotes.gui.PixelPanel;

import org.jetbrains.annotations.Nullable;

public final class EmoteTile {

    public static final int CUT = 2;
    private static final int HOVER_GROW = 3;

    private static final int BORDER_HOVER = ARGB.white(0.55F);
    private static final int BG_HOVER = ARGB.color(0xE6, 0x22202A);
    private static final int SHEEN_HOVER = ARGB.white(0.08F);
    public static final String[] PAIR = {
            ".##...##.",
            ".##...##.",
            ".........",
            "###...###",
            "#########",
            ".##...##.",
            ".##...##.",
            ".#.#.#.#.",
            ".#.#.#.#."};
    private static final int PAIR_BG = ARGB.color(0xC8, 0x0B0A10);

    private EmoteTile() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, @Nullable Emote emote, int cx, int cy, int size,
                            float hover, float alpha) {
        int grow = Math.round(HOVER_GROW * hover);
        int x = cx - size / 2 - grow;
        int y = cy - size / 2 - grow;
        int w = size + grow * 2;
        if (emote == null) {
            PixelPanel.outlineStepped(graphics, x, y, w, w, CUT, ARGB.multiplyAlpha(FalatiaTheme.HAIRLINE, alpha));
            return;
        }
        PixelPanel.outlinedCard(graphics, x, y, w, w, CUT,
                ARGB.multiplyAlpha(ARGB.srgbLerp(hover, FalatiaTheme.HAIRLINE, BORDER_HOVER), alpha),
                ARGB.multiplyAlpha(ARGB.srgbLerp(hover, FalatiaTheme.BG_GLASS, BG_HOVER), alpha),
                ARGB.multiplyAlpha(ARGB.srgbLerp(hover, FalatiaTheme.CARD_SHEEN_TOP, SHEEN_HOVER), alpha),
                ARGB.multiplyAlpha(FalatiaTheme.CARD_SHEEN_BOTTOM, alpha));
        icon(graphics, font, emote, cx, cy, Math.round(size * 0.74F) + grow, hover, alpha, w - 6);
        if (emote.pair() != null) {
            int gx = x + w - PAIR[0].length() - 4;
            int gy = y + 3;
            PixelPanel.fillStepped(graphics, gx - 2, gy - 2, PAIR[0].length() + 4, PAIR.length + 4, CUT, ARGB.multiplyAlpha(PAIR_BG, alpha));
            glyph(graphics, PAIR, gx, gy, ARGB.multiplyAlpha(ARGB.srgbLerp(hover, FalatiaTheme.MUTED, FalatiaTheme.TEXT), alpha));
        }
    }

    public static void glyph(GuiGraphicsExtractor graphics, String[] rows, int x, int y, int color) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == '#') {
                    graphics.fill(x + column, y + row, x + column + 1, y + row + 1, color);
                }
            }
        }
    }

    public static void icon(GuiGraphicsExtractor graphics, Font font, Emote emote, int cx, int cy, int size,
                            float hover, float alpha, int textWidth) {
        EmoteIcons.Icon icon = EmoteIcons.get(emote);
        if (icon != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon.id(), cx - size / 2, cy - size / 2, 0.0F, 0.0F,
                    size, size, icon.width(), icon.height(), icon.width(), icon.height(), ARGB.white(alpha));
        } else {
            lines(graphics, font, emote.name(), cx, cy, textWidth,
                    ARGB.multiplyAlpha(ARGB.srgbLerp(hover, FalatiaTheme.MUTED, FalatiaTheme.TEXT), alpha));
        }
    }

    public static void lines(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        int count = Math.min(2, lines.size());
        int top = y - count * 5 + 1;
        for (int i = 0; i < count; i++) {
            graphics.centeredText(font, lines.get(i), x, top + i * 10, color);
        }
    }
}
