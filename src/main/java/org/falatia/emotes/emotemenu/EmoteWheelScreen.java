package org.falatia.emotes.emotemenu;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import org.falatia.emotes.Emote;
import org.falatia.emotes.EmoteTile;
import org.falatia.emotes.Emotes;
import org.falatia.emotes.gui.FalatiaTheme;
import org.falatia.emotes.gui.PixelPanel;

import org.jetbrains.annotations.Nullable;

final class EmoteWheelScreen extends Screen {

    private static final long HOLD_MS = 250L;
    private static final float APPEAR_MS = 220.0F;
    private static final float STAGGER_MS = 18.0F;
    private static final float CLOSE_MS = 110.0F;
    private static final float FADE_OUT = 22.0F;

    private static final int TILE = 48;
    private static final int CENTER = 64;
    private static final int DEAD_ZONE = 26;
    private static final int BUTTON_HEIGHT = 18;
    private static final int DESCRIPTION_WIDTH = 240;

    private static final int POINTER = ARGB.white(0.4F);

    private final long openedAt = Util.getMillis();
    private long closingAt = -1L;
    private long lastFrame = this.openedAt;
    private final List<@Nullable Emote> slots = EmoteWheel.slots();
    private final int pages = Math.max(1, Mth.positiveCeilDiv(this.slots.size(), EmoteWheel.SLOTS));

    private final float[] hover = new float[EmoteWheel.SLOTS];
    private float centerHover;
    private float buttonHover;
    private @Nullable Emote shown;
    private float pointerAngle = Float.NaN;
    private float pointerAlpha;

    EmoteWheelScreen() {
        super(Component.translatable("falatia_emotes.wheel"));
        EmoteWheel.page = Math.min(EmoteWheel.page, this.pages - 1);
    }

    private int radius() {
        return Math.min(92, (this.height - TILE) / 2 - 26);
    }

    private Component buttonLabel() {
        return Component.translatable("falatia_emotes.wheel.settings");
    }

    private int buttonWidth() {
        return this.font.width(this.buttonLabel()) + 16;
    }

    private boolean overButton(double mouseX, double mouseY) {
        int x = this.width - this.buttonWidth() - 8;
        int y = this.buttonY();
        return mouseX >= x && mouseX < this.width - 8 && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    private int buttonY() {
        return this.height - BUTTON_HEIGHT - 8;
    }

    private int bottomTextWidth() {
        return this.width - 2 * (this.buttonWidth() + 16);
    }

    private int hovered(double mouseX, double mouseY) {
        if (this.overButton(mouseX, mouseY)) {
            return -2;
        }
        double dx = mouseX - this.width / 2.0;
        double dy = mouseY - this.height / 2.0;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
            return -1;
        }
        double angle = Math.atan2(dx, -dy);
        int slot = (int) Math.floor((angle + Math.PI / EmoteWheel.SLOTS) / (2.0 * Math.PI / EmoteWheel.SLOTS));
        return Math.floorMod(slot, EmoteWheel.SLOTS);
    }

    private @Nullable Emote emoteAt(int slot) {
        int index = EmoteWheel.page * EmoteWheel.SLOTS + slot;
        return slot >= 0 && index < this.slots.size() ? this.slots.get(index) : null;
    }

    private static float slotAngle(int slot) {
        return slot * Mth.TWO_PI / EmoteWheel.SLOTS;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, ARGB.multiplyAlpha(FalatiaTheme.BACKDROP_TINT, this.fade()));
    }

    private float fade() {
        if (this.closingAt < 0L) {
            return 1.0F;
        }
        return 1.0F - Mth.clamp((Util.getMillis() - this.closingAt) / CLOSE_MS, 0.0F, 1.0F);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        long now = Util.getMillis();
        float dt = Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
        this.lastFrame = now;
        float fadeOut = (float) Math.exp(-FADE_OUT * dt);

        int cx = this.width / 2;
        int cy = this.height / 2;
        float fade = this.fade();
        float closeScale = 0.9F + 0.1F * fade;
        int hovered = this.closingAt < 0L ? this.hovered(mouseX, mouseY) : -3;

        for (int slot = 0; slot < EmoteWheel.SLOTS; slot++) {
            Emote emote = this.emoteAt(slot);
            boolean target = slot == hovered && emote != null;
            this.hover[slot] = target ? 1.0F : this.hover[slot] * fadeOut;
            if (target) {
                this.shown = emote;
            }
        }
        this.centerHover = hovered == -1 && Emotes.playing() ? 1.0F : this.centerHover * fadeOut;
        this.buttonHover = hovered == -2 ? 1.0F : this.buttonHover * fadeOut;

        int radius = this.radius();
        for (int slot = 0; slot < EmoteWheel.SLOTS; slot++) {
            float appear = Mth.clamp((now - this.openedAt - slot * STAGGER_MS) / APPEAR_MS, 0.0F, 1.0F);
            if (appear <= 0.0F) {
                continue;
            }
            float distance = radius * (0.35F + 0.65F * easeOutBack(appear)) * closeScale;
            float angle = slotAngle(slot);
            int x = cx + Math.round(Mth.sin(angle) * distance);
            int y = cy - Math.round(Mth.cos(angle) * distance);
            EmoteTile.draw(graphics, this.font, this.emoteAt(slot), x, y, TILE, this.hover[slot], easeOut(appear) * fade);
        }

        this.pointer(graphics, cx, cy, hovered, radius, fadeOut, fade);
        this.center(graphics, cx, cy, fade);
        this.button(graphics, fade);

        this.description(graphics, cx, cy, radius, fade);
    }

    private void description(GuiGraphicsExtractor graphics, int cx, int cy, int radius, float fade) {
        float named = 0.0F;
        for (float value : this.hover) {
            named = Math.max(named, value);
        }
        String description = this.shown != null ? this.shown.subtitle() : "";
        if (this.shown != null && this.shown.pair() != null) {
            String pair = Component.translatable("falatia_emotes.pair").getString();
            description = description.isEmpty() ? pair : pair + " · " + description;
        }
        float shown = description.isEmpty() ? 0.0F : named;
        int belowWheel = cy + radius + TILE / 2 + 12;
        boolean fits = belowWheel + 20 < this.height - 20;
        if (shown > 0.02F) {
            int color = ARGB.multiplyAlpha(FalatiaTheme.MUTED, shown * fade);
            if (fits) {
                EmoteTile.lines(graphics, this.font, description, cx, belowWheel + 9, DESCRIPTION_WIDTH, color);
            } else {
                graphics.centeredText(this.font, this.font.plainSubstrByWidth(description, this.bottomTextWidth()), cx, this.height - 16, color);
            }
        }
        Component hint = Component.translatable(this.slots.isEmpty() ? "falatia_emotes.wheel.empty_hint"
                : this.pages > 1 ? "falatia_emotes.wheel.hint_pages" : "falatia_emotes.wheel.hint");
        float hintAlpha = fits ? fade : fade * (1.0F - shown);
        if (hintAlpha > 0.02F) {
            graphics.centeredText(this.font, this.font.plainSubstrByWidth(hint.getString(), this.bottomTextWidth()), cx, this.height - 16,
                    ARGB.multiplyAlpha(FalatiaTheme.MUTED, hintAlpha));
        }
    }

    private void pointer(GuiGraphicsExtractor graphics, int cx, int cy, int hovered, int radius, float fadeOut, float fade) {
        boolean visible = hovered >= 0 && this.emoteAt(hovered) != null;
        if (visible) {
            this.pointerAngle = slotAngle(hovered);
        }
        this.pointerAlpha = visible ? 1.0F : this.pointerAlpha * fadeOut;
        float alpha = this.pointerAlpha * fade;
        if (alpha < 0.02F || Float.isNaN(this.pointerAngle)) {
            return;
        }
        float sin = Mth.sin(this.pointerAngle);
        float cos = Mth.cos(this.pointerAngle);
        int color = ARGB.multiplyAlpha(POINTER, alpha);
        for (int step = CENTER / 2 + 4; step < radius - TILE / 2 - 4; step += 5) {
            int x = cx + Math.round(sin * step);
            int y = cy - Math.round(cos * step);
            graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
    }

    private void center(GuiGraphicsExtractor graphics, int cx, int cy, float fade) {
        float appear = easeOut(Mth.clamp((Util.getMillis() - this.openedAt) / APPEAR_MS, 0.0F, 1.0F)) * fade;
        PixelPanel.outlinedCard(graphics, cx - CENTER / 2, cy - CENTER / 2, CENTER, CENTER, EmoteTile.CUT,
                ARGB.multiplyAlpha(ARGB.srgbLerp(this.centerHover, FalatiaTheme.HAIRLINE, ARGB.white(0.55F)), appear),
                ARGB.multiplyAlpha(FalatiaTheme.BG_GLASS, appear),
                ARGB.multiplyAlpha(FalatiaTheme.CARD_SHEEN_TOP, appear),
                ARGB.multiplyAlpha(FalatiaTheme.CARD_SHEEN_BOTTOM, appear));

        float named = 0.0F;
        for (float value : this.hover) {
            named = Math.max(named, value);
        }
        int width = CENTER - 8;
        int textY = cy - (this.pages > 1 ? 4 : 0);
        if (this.shown != null && named > 0.02F) {
            EmoteTile.lines(graphics, this.font, this.shown.name(), cx, textY, width,
                    ARGB.multiplyAlpha(FalatiaTheme.TEXT, named * appear));
        }
        if (this.centerHover > 0.02F) {
            EmoteTile.lines(graphics, this.font, Component.translatable("falatia_emotes.wheel.stop").getString(), cx, textY, width,
                    ARGB.multiplyAlpha(FalatiaTheme.TEXT, this.centerHover * appear));
        }
        float idle = 1.0F - Math.max(named, this.centerHover);
        if (idle > 0.02F) {
            String title = this.slots.isEmpty() ? Component.translatable("falatia_emotes.wheel.empty_wheel").getString() : this.title.getString();
            EmoteTile.lines(graphics, this.font, title, cx, textY, width, ARGB.multiplyAlpha(FalatiaTheme.MUTED, idle * appear));
        }
        if (this.pages > 1) {
            graphics.centeredText(this.font, (EmoteWheel.page + 1) + "/" + this.pages, cx, cy + 12,
                    ARGB.multiplyAlpha(FalatiaTheme.MUTED, appear));
        }
    }

    private void button(GuiGraphicsExtractor graphics, float fade) {
        int w = this.buttonWidth();
        int x = this.width - w - 8;
        int y = this.buttonY();
        PixelPanel.outlinedCard(graphics, x, y, w, BUTTON_HEIGHT, EmoteTile.CUT,
                ARGB.multiplyAlpha(ARGB.srgbLerp(this.buttonHover, FalatiaTheme.HAIRLINE, ARGB.white(0.55F)), fade),
                ARGB.multiplyAlpha(FalatiaTheme.BG_GLASS, fade),
                ARGB.multiplyAlpha(FalatiaTheme.CARD_SHEEN_TOP, fade),
                ARGB.multiplyAlpha(FalatiaTheme.CARD_SHEEN_BOTTOM, fade));
        graphics.centeredText(this.font, this.buttonLabel(), x + w / 2, y + (BUTTON_HEIGHT - 8) / 2,
                ARGB.multiplyAlpha(ARGB.srgbLerp(this.buttonHover, FalatiaTheme.MUTED, FalatiaTheme.TEXT), fade));
    }

    private static float easeOut(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float u = t - 1.0F;
        return 1.0F + (c1 + 1.0F) * u * u * u + c1 * u * u;
    }

    private void choose(double mouseX, double mouseY, boolean released) {
        if (this.closingAt >= 0L) {
            return;
        }
        int hovered = this.hovered(mouseX, mouseY);
        if (hovered == -2) {
            if (!released) {
                this.minecraft.gui.setScreen(new EmoteWheelConfigScreen(null));
            }
            return;
        }
        if (released && !EmoteWheel.releaseSelects) {
            return;
        }
        Emote emote = this.emoteAt(hovered);
        if (emote != null) {
            this.onClose();
            Emotes.play(emote.id());
        } else if (hovered == -1 && Emotes.playing()) {
            this.onClose();
            Emotes.stop();
        } else if (!released || Util.getMillis() - this.openedAt >= HOLD_MS) {
            this.onClose();
        }
    }

    @Override
    public void onClose() {
        if (this.closingAt < 0L) {
            this.closingAt = Util.getMillis();
        }
    }

    @Override
    public void tick() {
        if (this.closingAt >= 0L && Util.getMillis() - this.closingAt >= CLOSE_MS) {
            this.minecraft.gui.setScreen(null);
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (EmoteWheel.key().matches(event)) {
            this.choose(this.mouseX(), this.mouseY(), true);
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (EmoteWheel.key().matchesMouse(event)) {
            this.choose(event.x(), event.y(), true);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.choose(event.x(), event.y(), false);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.pages > 1 && scrollY != 0.0 && this.closingAt < 0L) {
            EmoteWheel.page = Math.floorMod(EmoteWheel.page + (scrollY < 0.0 ? 1 : -1), this.pages);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    private double mouseX() {
        return this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
    }

    private double mouseY() {
        return this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
