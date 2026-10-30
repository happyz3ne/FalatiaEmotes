package org.falatia.emotes.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public final class PixelPanel {

    private PixelPanel() {
    }

    public static void fillStepped(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int cut, int color) {
        forEachRow(x, y, w, h, cut, (x1, y1, x2, y2, rowCenter) -> extractor.fill(x1, y1, x2, y2, color));
    }

    public static void fillSteppedGradient(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int cut, int topColor, int bottomColor) {
        forEachRow(x, y, w, h, cut, (x1, y1, x2, y2, rowCenter) ->
                extractor.fill(x1, y1, x2, y2, lerpArgb(topColor, bottomColor, rowCenter)));
    }

    public static void card(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int cut, int borderColor, int bgColor, int sheenTop, int sheenBottom) {
        fillStepped(extractor, x, y, w, h, cut, borderColor);
        fillStepped(extractor, x + 1, y + 1, w - 2, h - 2, cut, bgColor);
        fillSteppedGradient(extractor, x + 1, y + 1, w - 2, h - 2, cut, sheenTop, sheenBottom);
    }

    public static void outlinedCard(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int cut, int borderColor, int bgColor, int sheenTop, int sheenBottom) {
        fillStepped(extractor, x, y, w, h, cut, bgColor);
        fillSteppedGradient(extractor, x, y, w, h, cut, sheenTop, sheenBottom);
        outlineStepped(extractor, x, y, w, h, cut, borderColor);
    }

    public static void outlineStepped(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int cut, int color) {
        int half = cut / 2;
        int right = x + w;
        for (int side = 0; side < 2; side++) {
            int edge = side == 0 ? y : y + h - 1;
            int dir = side == 0 ? 1 : -1;
            hline(extractor, x + cut, right - cut, edge, color);
            columns(extractor, x + cut, right - cut - 1, edge + dir, dir, half - 1, color);
            int step = edge + dir * half;
            hline(extractor, x + half, x + cut, step, color);
            hline(extractor, right - cut, right - half, step, color);
            columns(extractor, x + half, right - half - 1, step + dir, dir, cut - half - 1, color);
            int corner = edge + dir * cut;
            hline(extractor, x, x + half, corner, color);
            hline(extractor, right - half, right, corner, color);
        }
        if (h - 2 * cut - 2 > 0) {
            extractor.fill(x, y + cut + 1, x + 1, y + h - cut - 1, color);
            extractor.fill(right - 1, y + cut + 1, right, y + h - cut - 1, color);
        }
    }

    private static void hline(GuiGraphicsExtractor extractor, int x1, int x2, int row, int color) {
        if (x2 > x1) {
            extractor.fill(x1, row, x2, row + 1, color);
        }
    }

    private static void columns(GuiGraphicsExtractor extractor, int left, int right, int start, int dir, int count, int color) {
        if (count <= 0) {
            return;
        }
        int top = dir > 0 ? start : start - count + 1;
        extractor.fill(left, top, left + 1, top + count, color);
        extractor.fill(right, top, right + 1, top + count, color);
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(int x1, int y1, int x2, int y2, float rowCenterFraction);
    }

    private static void forEachRow(int x, int y, int w, int h, int cut, RowConsumer row) {
        int half = cut / 2;
        int top = y;
        int bottom = y + h;

        row.accept(x + cut, top, x + w - cut, top + half, rowFraction(top, top + half, h, y));
        row.accept(x + half, top + half, x + w - half, top + cut, rowFraction(top + half, top + cut, h, y));
        row.accept(x, top + cut, x + w, bottom - cut, rowFraction(top + cut, bottom - cut, h, y));
        row.accept(x + half, bottom - cut, x + w - half, bottom - half, rowFraction(bottom - cut, bottom - half, h, y));
        row.accept(x + cut, bottom - half, x + w - cut, bottom, rowFraction(bottom - half, bottom, h, y));
    }

    private static float rowFraction(int rowStart, int rowEnd, int totalHeight, int originY) {
        float center = ((rowStart + rowEnd) / 2.0f) - originY;
        return Mth.clamp(center / totalHeight, 0.0f, 1.0f);
    }

    private static int lerpArgb(int colorA, int colorB, float delta) {
        int aA = (colorA >>> 24) & 0xFF;
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;
        int aB = (colorB >>> 24) & 0xFF;
        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;

        int a = Math.round(Mth.lerp(delta, aA, aB));
        int r = Math.round(Mth.lerp(delta, rA, rB));
        int g = Math.round(Mth.lerp(delta, gA, gB));
        int b = Math.round(Mth.lerp(delta, bA, bB));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
