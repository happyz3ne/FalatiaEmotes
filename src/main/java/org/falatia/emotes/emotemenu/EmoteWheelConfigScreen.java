package org.falatia.emotes.emotemenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import org.falatia.emotes.*;
import org.falatia.emotes.emotestypes.EmoteIcons;
import org.falatia.emotes.emotestypes.EmoteLoader;
import org.falatia.emotes.gui.FalatiaTheme;
import org.falatia.emotes.gui.PixelPanel;

import org.jetbrains.annotations.Nullable;

final class EmoteWheelConfigScreen extends Screen {

    private static final int MARGIN = 10;
    private static final int GAP = 8;
    private static final int PAD = 8;
    private static final int HEADER = 16;
    private static final int ROW = 24;
    private static final int LINE = 10;
    private static final int DESCRIPTION_LINES = 2;
    private static final int SEARCH = 16;
    private static final int SETTING = 15;
    private static final int DOTS = 12;
    private static final int MENU_WIDTH = 168;
    private static final int MENU_ROW = 16;
    private static final int MENU_HEADER = 18;
    private static final int MENU_BG = ARGB.color(0xFF, 0x1E1C26);
    private static final int MENU_BORDER = ARGB.white(0.8F);
    private static final int MENU_SHADOW = ARGB.color(0x80, 0x000000);
    private static final int MENU_DIM = ARGB.color(0x88, 0x000000);
    private static final String[] WALKER = {
            "...##..",
            "...##..",
            "..###..",
            ".#.###.",
            "#..##.#",
            "...##..",
            "..#..#.",
            ".#...#.",
            ".#....#",
            "#.....#"};
    private static final int MENU_FIRST_ROW = 26;
    private static final int BUTTON_HEIGHT = 16;
    private static final double DRAG_THRESHOLD = 3.0;

    private static final int BACKDROP = ARGB.color(0xB4, 0x07070A);
    private static final int ROW_HOVER = ARGB.white(0.05F);
    private static final int ROW_SELECTED = ARGB.white(0.1F);
    private static final int ACTIVE = ARGB.white(0.55F);
    private static final int CONFLICT = ARGB.color(0xFF, 0xE0675F);
    private static final int DESCRIPTION = ARGB.color(0xFF, 0x716A7E);
    private static final int SWITCH_ON = ARGB.color(0xFF, 0xE6E1EC);
    private static final int SWITCH_OFF = ARGB.white(0.16F);

    private static final String[] SETTINGS = {
            "falatia_emotes.config.third_person", "falatia_emotes.config.release_selects"};

    private final List<String> layout = new ArrayList<>();
    private final List<Emote> allEmotes = new ArrayList<>();
    private int generation;
    private final List<Emote> emotes = new ArrayList<>();
    private @Nullable EditBox search;
    private @Nullable String menuFor;
    private boolean capturing;
    private @Nullable Component taken;
    private int menuX;
    private int menuY;
    private int page = EmoteWheel.page;
    private int listScroll;
    private final Map<Emote, Integer> rowHeights = new IdentityHashMap<>();
    private @Nullable String selected;

    private @Nullable String dragId;
    private int dragFrom = -1;
    private double pressX;
    private double pressY;
    private boolean dragged;
    private int scrollGrab = -1;

    private int wheelLeft;
    private int wheelTop;
    private int wheelSize;
    private int wheelX;
    private int wheelY;
    private int radius;
    private int tile;
    private int titleY;
    private int sideLeft;
    private int sideWidth;
    private int listTop;
    private int listBottom;
    private int settingsTop;
    private int bottomY;

    private final @Nullable Screen parent;

    EmoteWheelConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("falatia_emotes.config"));
        this.parent = parent;
        for (Emote emote : EmoteWheel.slots()) {
            this.layout.add(emote == null ? "" : emote.id());
        }
        this.readEmotes();
        this.emotes.addAll(this.allEmotes);
    }

    private void readEmotes() {
        this.generation = Emotes.generation();
        this.rowHeights.clear();
        this.allEmotes.clear();
        for (String id : Emotes.ids()) {
            this.allEmotes.add(Emotes.get(id));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.generation != Emotes.generation()) {
            this.readEmotes();
            this.filter();
        }
    }

    @Override
    protected void init() {
        this.wheelSize = Math.min(this.height - 26 - MARGIN - GAP - BUTTON_HEIGHT, 260);
        int top = Math.max(26, (this.height - this.wheelSize - GAP - BUTTON_HEIGHT) / 2 + 8);
        this.titleY = top - 16;
        this.bottomY = top + this.wheelSize + GAP;
        this.sideWidth = Math.min(280, this.width - 2 * MARGIN - GAP - this.wheelSize);
        this.rowHeights.clear();
        int left = (this.width - this.wheelSize - GAP - this.sideWidth) / 2;
        this.wheelLeft = left;
        this.wheelTop = top;
        this.sideLeft = left + this.wheelSize + GAP;

        int wheelArea = this.wheelSize - HEADER - 20;
        this.tile = Mth.clamp(this.wheelSize / 7, 26, 38);
        this.radius = Math.max(36, (wheelArea - this.tile) / 2 - 4);
        this.wheelX = this.wheelLeft + this.wheelSize / 2;
        this.wheelY = this.wheelTop + HEADER + wheelArea / 2 + 2;

        int settingsHeight = HEADER + SETTINGS.length * SETTING + PAD;
        this.settingsTop = top + this.wheelSize - settingsHeight;
        this.listTop = top;
        this.listBottom = this.settingsTop - GAP;
        this.page = Math.min(this.page, this.pages() - 1);

        String query = this.search != null ? this.search.getValue() : "";
        this.search = new EditBox(this.font, this.sideLeft + PAD + 5, this.searchTop() + 4, this.sideWidth - PAD * 2 - 10, 9,
                Component.translatable("falatia_emotes.config.search"));
        this.search.setBordered(false);
        this.search.setTextColor(FalatiaTheme.TEXT);
        this.search.setTextShadow(false);
        this.search.setMaxLength(64);
        this.search.setValue(query);
        this.search.setResponder(text -> this.filter());
        this.addWidget(this.search);
        this.setInitialFocus(this.search);
        this.filter();
    }

    private void filter() {
        String query = this.search == null ? "" : this.search.getValue().strip().toLowerCase(Locale.ROOT);
        this.emotes.clear();
        for (Emote emote : this.allEmotes) {
            if (query.isEmpty() || emote.name().toLowerCase(Locale.ROOT).contains(query)
                    || emote.description().toLowerCase(Locale.ROOT).contains(query)
                    || emote.author().toLowerCase(Locale.ROOT).contains(query) || emote.id().contains(query)) {
                this.emotes.add(emote);
            }
        }
        this.clampScroll();
    }

    private int pages() {
        int last = -1;
        for (int i = 0; i < this.layout.size(); i++) {
            if (!this.layout.get(i).isEmpty()) {
                last = i;
            }
        }
        return last < 0 ? 1 : Mth.clamp(last / EmoteWheel.SLOTS + 2, 1, EmoteWheel.MAX_PAGES);
    }

    private String idAt(int index) {
        return index < this.layout.size() ? this.layout.get(index) : "";
    }

    private void set(int index, String id) {
        while (this.layout.size() <= index) {
            this.layout.add("");
        }
        this.layout.set(index, id);
    }

    private void assign(String id, int index) {
        for (int i = 0; i < this.layout.size(); i++) {
            if (this.layout.get(i).equals(id)) {
                this.layout.set(i, "");
            }
        }
        this.set(index, id);
    }

    private void swap(int a, int b) {
        String first = this.idAt(a);
        this.set(a, this.idAt(b));
        this.set(b, first);
    }

    private int slotX(int slot) {
        return this.wheelX + Math.round(Mth.sin(slot * Mth.TWO_PI / EmoteWheel.SLOTS) * this.radius);
    }

    private int slotY(int slot) {
        return this.wheelY - Math.round(Mth.cos(slot * Mth.TWO_PI / EmoteWheel.SLOTS) * this.radius);
    }

    private int slotAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < EmoteWheel.SLOTS; slot++) {
            if (Math.abs(mouseX - this.slotX(slot)) <= this.tile / 2.0 + 2 && Math.abs(mouseY - this.slotY(slot)) <= this.tile / 2.0 + 2) {
                return this.page * EmoteWheel.SLOTS + slot;
            }
        }
        return -1;
    }

    private int pageRowY() {
        return this.wheelTop + this.wheelSize - 16;
    }

    private int arrowAt(double mouseX, double mouseY) {
        int y = this.pageRowY();
        if (mouseY < y - 3 || mouseY >= y + 11) {
            return 0;
        }
        if (Math.abs(mouseX - (this.wheelX - 46)) <= 8) {
            return -1;
        }
        if (Math.abs(mouseX - (this.wheelX + 46)) <= 8) {
            return 1;
        }
        return 0;
    }

    private int searchTop() {
        return this.listTop + HEADER + 5;
    }

    private int listRowsTop() {
        return this.searchTop() + SEARCH + 3;
    }

    private boolean overList(double mouseX, double mouseY) {
        return mouseX >= this.sideLeft && mouseX < this.sideLeft + this.sideWidth && mouseY >= this.listTop && mouseY < this.listBottom;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!this.overList(mouseX, mouseY) || mouseY < this.listRowsTop()) {
            return -1;
        }
        int y = this.listRowsTop();
        for (int row = this.listScroll; row < this.emotes.size(); row++) {
            y += this.rowHeight(this.emotes.get(row));
            if (mouseY < y) {
                return row;
            }
        }
        return -1;
    }

    private int rowTop(int row) {
        int y = this.listRowsTop();
        for (int i = this.listScroll; i < row; i++) {
            y += this.rowHeight(this.emotes.get(i));
        }
        return y;
    }

    private int visibleRows() {
        int space = this.listSpace();
        int rows = 0;
        for (int row = this.listScroll; row < this.emotes.size(); row++) {
            space -= this.rowHeight(this.emotes.get(row));
            if (space < 0) {
                break;
            }
            rows++;
        }
        return Math.max(1, rows);
    }

    private int maxScroll() {
        int space = this.listSpace();
        for (int row = this.emotes.size() - 1; row >= 0; row--) {
            space -= this.rowHeight(this.emotes.get(row));
            if (space < 0) {
                return row + 1;
            }
        }
        return 0;
    }

    private int listSpace() {
        return this.listBottom - this.listRowsTop() - 4;
    }

    private void clampScroll() {
        this.listScroll = Mth.clamp(this.listScroll, 0, this.maxScroll());
    }

    private boolean scrollable() {
        return this.maxScroll() > 0;
    }

    private int textLeft(Emote emote) {
        return this.sideLeft + 4 + (EmoteIcons.get(emote) != null ? 27 : 6);
    }

    private int subtitleWidth(Emote emote) {
        return this.sideLeft + this.sideWidth - 8 - DOTS - 6 - this.textLeft(emote);
    }

    private List<String> descriptionLines(Emote emote) {
        if (emote.description().isEmpty()) {
            return List.of();
        }
        int width = this.subtitleWidth(emote);
        List<String> lines = new ArrayList<>();
        for (FormattedText line : this.font.getSplitter().splitLines(emote.description(), width, Style.EMPTY)) {
            lines.add(line.getString().strip());
        }
        if (lines.size() > DESCRIPTION_LINES) {
            String rest = String.join(" ", lines.subList(DESCRIPTION_LINES - 1, lines.size()));
            lines = new ArrayList<>(lines.subList(0, DESCRIPTION_LINES - 1));
            lines.add(this.fit(rest + "…", width));
        }
        return lines;
    }

    private int rowHeight(Emote emote) {
        return this.rowHeights.computeIfAbsent(emote, e -> {
            int lines = 1 + this.descriptionLines(e).size() + (e.author().isEmpty() ? 0 : 1);
            return Math.max(ROW, 4 + lines * LINE);
        });
    }

    private int trackHeight() {
        return this.listBottom - 4 - this.listRowsTop();
    }

    private int thumbHeight() {
        return Math.max(10, this.trackHeight() * this.visibleRows() / this.emotes.size());
    }

    private int thumbTop() {
        int maxScroll = this.maxScroll();
        return this.listRowsTop() + (this.trackHeight() - this.thumbHeight()) * this.listScroll / Math.max(1, maxScroll);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        int barRight = this.sideLeft + this.sideWidth - 4;
        return this.scrollable() && mouseX >= barRight - 6 && mouseX < barRight + 2
                && mouseY >= this.listRowsTop() && mouseY < this.listRowsTop() + this.trackHeight();
    }

    private void dragScroll(double mouseY) {
        int range = this.trackHeight() - this.thumbHeight();
        int maxScroll = this.maxScroll();
        double top = mouseY - this.scrollGrab - this.listRowsTop();
        this.listScroll = range <= 0 ? 0 : (int) Math.round(top * maxScroll / range);
        this.clampScroll();
    }

    private int settingAt(double mouseX, double mouseY) {
        int top = this.settingsTop + HEADER;
        if (mouseX < this.sideLeft || mouseX >= this.sideLeft + this.sideWidth || mouseY < top) {
            return -1;
        }
        int row = (int) ((mouseY - top) / SETTING);
        return row < SETTINGS.length ? row : -1;
    }

    private Component folderLabel() {
        return Component.translatable("falatia_emotes.config.folder");
    }

    private Component doneLabel() {
        return Component.translatable("gui.done");
    }

    private int buttonWidth(Component label) {
        return this.font.width(label) + 20;
    }

    private int buttonAt(double mouseX, double mouseY) {
        if (mouseY < this.bottomY || mouseY >= this.bottomY + BUTTON_HEIGHT) {
            return 0;
        }
        int right = this.sideLeft + this.sideWidth;
        int done = this.buttonWidth(this.doneLabel());
        if (mouseX >= right - done && mouseX < right) {
            return 1;
        }
        int folder = this.buttonWidth(this.folderLabel());
        if (mouseX >= right - done - 6 - folder && mouseX < right - done - 6) {
            return 2;
        }
        return 0;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, BACKDROP);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, this.titleY, FalatiaTheme.TEXT);
        this.wheel(graphics, mouseX, mouseY);
        this.list(graphics, mouseX, mouseY, a);
        this.settings(graphics, mouseX, mouseY);
        this.footer(graphics, mouseX, mouseY);
        this.menu(graphics, mouseX, mouseY);
        if (this.dragId != null && this.dragged) {
            Emote emote = Emotes.get(this.dragId);
            if (emote != null) {
                EmoteTile.draw(graphics, this.font, emote, mouseX, mouseY, this.tile, 1.0F, 0.85F);
            }
        }
    }

    private void card(GuiGraphicsExtractor graphics, int x, int y, int w, int h, Component title) {
        PixelPanel.outlinedCard(graphics, x, y, w, h, EmoteTile.CUT, FalatiaTheme.HAIRLINE, FalatiaTheme.BG_GLASS,
                FalatiaTheme.CARD_SHEEN_TOP, FalatiaTheme.CARD_SHEEN_BOTTOM);
        graphics.text(this.font, title, x + PAD, y + 6, FalatiaTheme.MUTED, false);
        graphics.fill(x + PAD, y + HEADER + 1, x + w - PAD, y + HEADER + 2, FalatiaTheme.HAIRLINE);
    }

    private void wheel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.card(graphics, this.wheelLeft, this.wheelTop, this.wheelSize, this.wheelSize, Component.translatable("falatia_emotes.config.wheel"));
        int hoveredSlot = this.dragId != null && !this.dragged ? -1 : this.slotAt(mouseX, mouseY);
        String hoveredName = null;
        for (int slot = 0; slot < EmoteWheel.SLOTS; slot++) {
            int index = this.page * EmoteWheel.SLOTS + slot;
            String id = this.idAt(index);
            Emote emote = id.isEmpty() ? null : Emotes.get(id);
            boolean hover = index == hoveredSlot;
            if (hover && emote != null && !this.dragged) {
                hoveredName = emote.name();
            }
            float alpha = this.dragged && index == this.dragFrom ? 0.3F : 1.0F;
            boolean target = hover && (this.dragged || emote == null && this.selected != null);
            EmoteTile.draw(graphics, this.font, emote, this.slotX(slot), this.slotY(slot), this.tile, hover || target ? 1.0F : 0.0F, alpha);
            if (target && emote == null) {
                PixelPanel.outlineStepped(graphics, this.slotX(slot) - this.tile / 2 - 3, this.slotY(slot) - this.tile / 2 - 3,
                        this.tile + 6, this.tile + 6, EmoteTile.CUT, ACTIVE);
            }
        }
        if (hoveredName != null) {
            EmoteTile.lines(graphics, this.font, hoveredName, this.wheelX, this.wheelY, this.radius * 2 - this.tile - 6, FalatiaTheme.TEXT);
        }

        int y = this.pageRowY();
        int arrow = this.arrowAt(mouseX, mouseY);
        boolean back = this.page > 0;
        boolean next = this.page < this.pages() - 1;
        graphics.centeredText(this.font, "<", this.wheelX - 46, y, back ? (arrow == -1 ? FalatiaTheme.TEXT : FalatiaTheme.MUTED) : FalatiaTheme.HAIRLINE);
        graphics.centeredText(this.font, ">", this.wheelX + 46, y, next ? (arrow == 1 ? FalatiaTheme.TEXT : FalatiaTheme.MUTED) : FalatiaTheme.HAIRLINE);
        graphics.centeredText(this.font, Component.translatable("falatia_emotes.config.page", this.page + 1, this.pages()),
                this.wheelX, y, FalatiaTheme.MUTED);
    }

    private void list(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.card(graphics, this.sideLeft, this.listTop, this.sideWidth, this.listBottom - this.listTop,
                Component.translatable("falatia_emotes.config.all", this.allEmotes.size()));
        PixelPanel.outlinedCard(graphics, this.sideLeft + PAD, this.searchTop(), this.sideWidth - PAD * 2, SEARCH, EmoteTile.CUT,
                this.search != null && this.search.isFocused() ? ACTIVE : FalatiaTheme.HAIRLINE,
                FalatiaTheme.BG, FalatiaTheme.CARD_SHEEN_TOP, FalatiaTheme.CARD_SHEEN_BOTTOM);
        if (this.search != null) {
            this.search.extractRenderState(graphics, mouseX, mouseY, a);
            if (this.search.getValue().isEmpty()) {
                graphics.text(this.font, Component.translatable("falatia_emotes.config.search"),
                        this.search.getX() + 7, this.search.getY(), DESCRIPTION, false);
            }
        }
        int left = this.sideLeft + 4;
        int right = this.sideLeft + this.sideWidth - 4;
        if (this.emotes.isEmpty()) {
            String key = this.allEmotes.isEmpty() ? "falatia_emotes.wheel.empty" : "falatia_emotes.config.nothing";
            EmoteTile.lines(graphics, this.font, Component.translatable(key).getString(),
                    (left + right) / 2, (this.listRowsTop() + this.listBottom) / 2, right - left - 8, FalatiaTheme.MUTED);
            return;
        }
        Map<String, Integer> placed = new HashMap<>();
        for (int i = 0; i < this.layout.size(); i++) {
            if (!this.layout.get(i).isEmpty()) {
                placed.putIfAbsent(this.layout.get(i), i);
            }
        }
        boolean scrollable = this.scrollable();
        if (scrollable) {
            right -= 4;
        }
        int hoveredRow = this.dragged ? -1 : this.rowAt(mouseX, mouseY);
        graphics.enableScissor(left, this.listRowsTop(), right, this.listBottom - 3);
        int y = this.listRowsTop();
        for (int row = this.listScroll; row < this.emotes.size() && y < this.listBottom; row++) {
            Emote emote = this.emotes.get(row);
            int height = this.rowHeight(emote);
            if (emote.id().equals(this.selected)) {
                graphics.fill(left, y, right, y + height, ROW_SELECTED);
            } else if (row == hoveredRow) {
                graphics.fill(left, y, right, y + height, ROW_HOVER);
            }
            int textLeft = this.textLeft(emote);
            if (EmoteIcons.get(emote) != null) {
                EmoteTile.icon(graphics, this.font, emote, left + 13, y + height / 2, 18, 0.0F, 1.0F, 18);
            }
            Integer index = placed.get(emote.id());
            String where = index == null ? "" : (index / EmoteWheel.SLOTS + 1) + "·" + (index % EmoteWheel.SLOTS + 1);
            List<String> description = this.descriptionLines(emote);
            boolean single = description.isEmpty() && emote.author().isEmpty();
            int mid = single ? y + ROW / 2 : y + 7;
            int badges = this.badges(graphics, emote, right - DOTS - 2, y, mid, mouseX, mouseY, row == hoveredRow);
            int whereWidth = this.font.width(where);
            int textWidth = right - DOTS - 6 - badges - textLeft - whereWidth - 10;
            String name = this.fit(emote.name(), textWidth);
            int textY = single ? y + (ROW - 8) / 2 : y + 3;
            graphics.text(this.font, name, textLeft, textY, index == null ? FalatiaTheme.MUTED : FalatiaTheme.TEXT, false);
            for (String line : description) {
                textY += LINE;
                graphics.text(this.font, line, textLeft, textY, DESCRIPTION, false);
            }
            if (!emote.author().isEmpty()) {
                textY += LINE;
                String author = Component.translatable("falatia_emotes.author", emote.author()).getString();
                graphics.text(this.font, this.fit(author, this.subtitleWidth(emote)), textLeft, textY, FalatiaTheme.MUTED, false);
            }
            if (index != null) {
                graphics.text(this.font, where, right - DOTS - 6 - badges - whereWidth, mid - 4, FalatiaTheme.MUTED, false);
            }
            y += height;
        }
        graphics.disableScissor();
        if (scrollable) {
            int thumbTop = this.thumbTop();
            boolean active = this.scrollGrab >= 0 || !this.dragged && this.overScrollbar(mouseX, mouseY);
            graphics.fill(right + 1, thumbTop, right + 3, thumbTop + this.thumbHeight(), active ? FalatiaTheme.TEXT : FalatiaTheme.MUTED);
        }
    }

    private int badges(GuiGraphicsExtractor graphics, Emote emote, int right, int y, int mid, int mouseX, int mouseY, boolean hovered) {
        boolean open = emote.id().equals(this.menuFor);
        if (hovered || open) {
            boolean overDots = mouseX >= right && mouseX < right + DOTS && mouseY >= y && mouseY < y + ROW;
            int color = overDots || open ? FalatiaTheme.TEXT : FalatiaTheme.MUTED;
            int cx = right + DOTS / 2;
            for (int dot = -1; dot <= 1; dot++) {
                int dy = mid + dot * 4;
                graphics.fill(cx - 1, dy - 1, cx + 1, dy + 1, color);
            }
        }
        int x = right - 4;
        if (Emotes.fromServer(emote)) {
            String server = Component.translatable("falatia_emotes.server_badge").getString();
            int w = this.font.width(server) + 8;
            x -= w;
            PixelPanel.outlineStepped(graphics, x, mid - 6, w, 12, EmoteTile.CUT, FalatiaTheme.MUTED);
            graphics.text(this.font, server, x + 4, mid - 4, FalatiaTheme.MUTED, false);
            x -= 4;
        }
        InputConstants.Key key = EmoteWheel.emoteKey(emote.id());
        if (key != null) {
            String name = EmoteWheel.keyName(key).getString();
            int w = this.font.width(name) + 8;
            x -= w;
            boolean conflict = EmoteWheel.conflict(key) != null;
            PixelPanel.outlineStepped(graphics, x, mid - 6, w, 12, EmoteTile.CUT, conflict ? CONFLICT : FalatiaTheme.MUTED);
            graphics.text(this.font, name, x + 4, mid - 4, conflict ? CONFLICT : FalatiaTheme.TEXT, false);
            x -= 4;
        }
        if (EmoteWheel.walking(emote.id())) {
            x -= WALKER[0].length();
            EmoteTile.glyph(graphics, WALKER, x, mid - WALKER.length / 2, FalatiaTheme.MUTED);
            x -= 4;
        }
        if (emote.pair() != null) {
            x -= EmoteTile.PAIR[0].length();
            EmoteTile.glyph(graphics, EmoteTile.PAIR, x, mid - EmoteTile.PAIR.length / 2, FalatiaTheme.MUTED);
            x -= 4;
        }
        return right - 4 - x;
    }

    private int dotsAt(double mouseX, double mouseY) {
        int right = this.sideLeft + this.sideWidth - 4 - (this.scrollable() ? 4 : 0);
        if (mouseX < right - DOTS - 2 || mouseX >= right - 2) {
            return -1;
        }
        return this.rowAt(mouseX, mouseY);
    }

    private int menuHeight() {
        return MENU_HEADER + MENU_FIRST_ROW + MENU_ROW * (EmoteWheel.emoteKey(this.menuFor) != null ? 2 : 1) + 4;
    }

    private int menuRowAt(double mouseX, double mouseY) {
        if (this.menuFor == null || mouseX < this.menuX || mouseX >= this.menuX + MENU_WIDTH
                || mouseY < this.menuY || mouseY >= this.menuY + this.menuHeight()) {
            return -1;
        }
        int y = (int) mouseY - this.menuY - 2 - MENU_HEADER;
        if (y < 0) {
            return -2;
        }
        if (y < MENU_FIRST_ROW) {
            return 0;
        }
        return Math.min(2, 1 + (y - MENU_FIRST_ROW) / MENU_ROW);
    }

    private void openMenu(Emote emote, int row) {
        this.menuFor = emote.id();
        this.capturing = false;
        int right = this.sideLeft + this.sideWidth - 4;
        int rowBottom = this.rowTop(row) + this.rowHeight(this.emotes.get(row));
        this.menuX = right - MENU_WIDTH;
        this.menuY = Math.min(rowBottom, this.height - this.menuHeight() - 4);
    }

    private void closeMenu() {
        this.menuFor = null;
        this.capturing = false;
        this.taken = null;
    }

    private void menu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.menuFor == null) {
            return;
        }
        int hovered = this.menuRowAt(mouseX, mouseY);
        graphics.fill(0, 0, this.width, this.height, MENU_DIM);
        PixelPanel.fillStepped(graphics, this.menuX + 3, this.menuY + 3, MENU_WIDTH, this.menuHeight(), EmoteTile.CUT, MENU_SHADOW);
        PixelPanel.outlinedCard(graphics, this.menuX, this.menuY, MENU_WIDTH, this.menuHeight(), EmoteTile.CUT,
                MENU_BORDER, MENU_BG, FalatiaTheme.CARD_SHEEN_TOP, FalatiaTheme.CARD_SHEEN_BOTTOM);
        int x = this.menuX + 8;
        int y = this.menuY + 2;
        Emote emote = Emotes.get(this.menuFor);
        String title = emote != null ? emote.name() : this.menuFor;
        graphics.text(this.font, this.fit(title, MENU_WIDTH - 16), x, y + 4, FalatiaTheme.TEXT, false);
        graphics.fill(x, y + MENU_HEADER - 3, this.menuX + MENU_WIDTH - 8, y + MENU_HEADER - 2, FalatiaTheme.HAIRLINE);
        y += MENU_HEADER;
        boolean walking = EmoteWheel.walking(this.menuFor);
        boolean paired = EmoteWheel.paired(this.menuFor);
        if (hovered == 0 && !paired) {
            graphics.fill(this.menuX + 2, y, this.menuX + MENU_WIDTH - 2, y + MENU_FIRST_ROW, ROW_HOVER);
        }
        PixelPanel.fillStepped(graphics, x, y + 5, 16, 8, 2, walking ? SWITCH_ON : SWITCH_OFF);
        PixelPanel.fillStepped(graphics, walking ? x + 9 : x + 1, y + 6, 6, 6, 2, walking ? FalatiaTheme.BG : paired ? SWITCH_OFF : FalatiaTheme.MUTED);
        graphics.text(this.font, Component.translatable("falatia_emotes.config.walking"), x + 22, y + 5,
                paired ? FalatiaTheme.MUTED : FalatiaTheme.TEXT, false);
        EmoteTile.glyph(graphics, WALKER, this.menuX + MENU_WIDTH - 8 - WALKER[0].length(), y + 4, walking ? FalatiaTheme.TEXT : FalatiaTheme.MUTED);
        graphics.text(this.font, this.fit(Component.translatable(paired ? "falatia_emotes.config.walking_paired"
                        : "falatia_emotes.config.walking_hint").getString(), MENU_WIDTH - 16),
                x, y + 16, DESCRIPTION, false);
        y += MENU_FIRST_ROW;
        InputConstants.Key key = EmoteWheel.emoteKey(this.menuFor);
        if (hovered == 1 || this.capturing) {
            graphics.fill(this.menuX + 2, y, this.menuX + MENU_WIDTH - 2, y + MENU_ROW, ROW_HOVER);
        }
        KeyMapping busy = key != null ? EmoteWheel.conflict(key) : null;
        Component label;
        int color;
        if (this.capturing && this.taken != null) {
            label = this.taken;
            color = CONFLICT;
        } else if (this.capturing) {
            label = Component.translatable("falatia_emotes.config.press_key");
            color = FalatiaTheme.TEXT;
        } else if (busy != null) {
            label = Component.translatable("falatia_emotes.config.key_busy", EmoteWheel.keyName(key), Component.translatable(busy.getName()));
            color = CONFLICT;
        } else if (key != null) {
            label = Component.translatable("falatia_emotes.config.key", EmoteWheel.keyName(key));
            color = hovered == 1 ? FalatiaTheme.TEXT : FalatiaTheme.MUTED;
        } else {
            label = Component.translatable("falatia_emotes.config.bind");
            color = hovered == 1 ? FalatiaTheme.TEXT : FalatiaTheme.MUTED;
        }
        graphics.text(this.font, this.fit(label.getString(), MENU_WIDTH - 16), x, y + 4, color, false);
        if (key != null) {
            y += MENU_ROW;
            if (hovered == 2) {
                graphics.fill(this.menuX + 2, y, this.menuX + MENU_WIDTH - 2, y + MENU_ROW, ROW_HOVER);
            }
            graphics.text(this.font, Component.translatable("falatia_emotes.config.unbind"), x, y + 4,
                    hovered == 2 ? FalatiaTheme.TEXT : FalatiaTheme.MUTED, false);
        }
    }

    private String fit(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, width - this.font.width("…")).stripTrailing() + "…";
    }

    private void settings(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int height = this.wheelTop + this.wheelSize - this.settingsTop;
        this.card(graphics, this.sideLeft, this.settingsTop, this.sideWidth, height, Component.translatable("falatia_emotes.config.settings"));
        int hovered = this.settingAt(mouseX, mouseY);
        boolean[] values = {EmoteWheel.thirdPerson, EmoteWheel.releaseSelects};
        for (int i = 0; i < SETTINGS.length; i++) {
            int y = this.settingsTop + HEADER + 4 + i * SETTING;
            int x = this.sideLeft + PAD;
            PixelPanel.fillStepped(graphics, x, y + 1, 16, 8, 2, values[i] ? SWITCH_ON : SWITCH_OFF);
            int knob = values[i] ? x + 9 : x + 1;
            PixelPanel.fillStepped(graphics, knob, y + 2, 6, 6, 2, values[i] ? FalatiaTheme.BG : FalatiaTheme.MUTED);
            String label = this.font.plainSubstrByWidth(Component.translatable(SETTINGS[i]).getString(), this.sideWidth - PAD * 2 - 22);
            graphics.text(this.font, label, x + 22, y + 1, hovered == i ? FalatiaTheme.TEXT : FalatiaTheme.MUTED, false);
        }
    }

    private void footer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int hovered = this.buttonAt(mouseX, mouseY);
        int right = this.sideLeft + this.sideWidth;
        int done = this.buttonWidth(this.doneLabel());
        int hintWidth = right - done - 6 - this.buttonWidth(this.folderLabel()) - 8 - this.wheelLeft;
        String hint = this.font.plainSubstrByWidth(Component.translatable("falatia_emotes.config.hint").getString(), hintWidth);
        graphics.text(this.font, hint, this.wheelLeft + 2, this.bottomY + 4, FalatiaTheme.MUTED, false);
        this.button(graphics, right - done, done, this.doneLabel(), hovered == 1);
        int folder = this.buttonWidth(this.folderLabel());
        this.button(graphics, right - done - 6 - folder, folder, this.folderLabel(), hovered == 2);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int w, Component label, boolean hover) {
        PixelPanel.outlinedCard(graphics, x, this.bottomY, w, BUTTON_HEIGHT, EmoteTile.CUT,
                hover ? ACTIVE : FalatiaTheme.HAIRLINE, hover ? ARGB.color(0xE6, 0x22202A) : FalatiaTheme.BG_GLASS,
                FalatiaTheme.CARD_SHEEN_TOP, FalatiaTheme.CARD_SHEEN_BOTTOM);
        graphics.centeredText(this.font, label, x + w / 2, this.bottomY + 4, hover ? FalatiaTheme.TEXT : FalatiaTheme.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        if (this.menuFor != null) {
            int item = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? this.menuRowAt(x, y) : -1;
            switch (item) {
                case 0 -> {
                    if (!EmoteWheel.paired(this.menuFor)) {
                        EmoteWheel.setWalking(this.menuFor, !EmoteWheel.walking(this.menuFor));
                    }
                }
                case 1 -> {
                    this.capturing = !this.capturing;
                    this.taken = null;
                }
                case 2 -> {
                    EmoteWheel.setEmoteKey(this.menuFor, null);
                    this.capturing = false;
                }
                case -2 -> {
                }
                default -> this.closeMenu();
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int dots = this.dotsAt(x, y);
            if (dots >= 0) {
                this.openMenu(this.emotes.get(dots), dots);
                return true;
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            int slot = this.slotAt(x, y);
            if (slot >= 0) {
                this.set(slot, "");
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        if (this.overScrollbar(x, y)) {
            int thumbTop = this.thumbTop();
            boolean onThumb = y >= thumbTop && y < thumbTop + this.thumbHeight();
            this.scrollGrab = onThumb ? (int) y - thumbTop : this.thumbHeight() / 2;
            this.dragScroll(y);
            return true;
        }
        int arrow = this.arrowAt(x, y);
        if (arrow != 0) {
            this.page = Mth.clamp(this.page + arrow, 0, this.pages() - 1);
            return true;
        }
        switch (this.settingAt(x, y)) {
            case 0 -> EmoteWheel.thirdPerson = !EmoteWheel.thirdPerson;
            case 1 -> EmoteWheel.releaseSelects = !EmoteWheel.releaseSelects;
            default -> {
                switch (this.buttonAt(x, y)) {
                    case 1 -> this.onClose();
                    case 2 -> Blaze3D.openPath(Emotes.directory());
                    default -> {
                        return this.press(x, y) || super.mouseClicked(event, doubleClick);
                    }
                }
            }
        }
        return true;
    }

    private boolean press(double x, double y) {
        this.pressX = x;
        this.pressY = y;
        this.dragged = false;
        int slot = this.slotAt(x, y);
        if (slot >= 0) {
            if (!this.idAt(slot).isEmpty()) {
                this.dragId = this.idAt(slot);
                this.dragFrom = slot;
            } else if (this.selected != null) {
                this.assign(this.selected, slot);
            }
            return true;
        }
        int row = this.rowAt(x, y);
        if (row >= 0) {
            this.selected = this.emotes.get(row).id();
            this.dragId = this.selected;
            this.dragFrom = -1;
            return true;
        }
        this.selected = null;
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.scrollGrab >= 0) {
            this.dragScroll(event.y());
            return true;
        }
        if (this.dragId != null && !this.dragged
                && Math.hypot(event.x() - this.pressX, event.y() - this.pressY) > DRAG_THRESHOLD) {
            this.dragged = true;
        }
        return this.dragId != null || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.scrollGrab >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.scrollGrab = -1;
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || this.dragId == null) {
            return super.mouseReleased(event);
        }
        int target = this.slotAt(event.x(), event.y());
        if (this.dragged) {
            if (target >= 0) {
                if (this.dragFrom >= 0) {
                    this.swap(this.dragFrom, target);
                } else {
                    this.assign(this.dragId, target);
                }
            } else if (this.dragFrom >= 0 && this.overList(event.x(), event.y())) {
                this.set(this.dragFrom, "");
            }
        } else if (this.dragFrom >= 0 && this.selected != null && !this.selected.equals(this.dragId)) {
            this.assign(this.selected, this.dragFrom);
        }
        this.dragId = null;
        this.dragFrom = -1;
        this.dragged = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        this.closeMenu();
        if (this.overList(x, y)) {
            this.listScroll += scrollY < 0.0 ? 1 : -1;
            this.clampScroll();
        } else {
            this.page = Mth.clamp(this.page + (scrollY < 0.0 ? 1 : -1), 0, this.pages() - 1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.menuFor != null && this.capturing) {
            int key = event.key();
            if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                EmoteWheel.setEmoteKey(this.menuFor, null);
            } else if (key != InputConstants.KEY_ESCAPE) {
                InputConstants.Key pressed = InputConstants.getKey(event);
                KeyMapping busy = EmoteWheel.conflict(pressed);
                if (busy != null) {
                    this.taken = Component.translatable("falatia_emotes.config.key_taken", EmoteWheel.keyName(pressed),
                            Component.translatable(busy.getName()));
                    return true;
                }
                EmoteWheel.setEmoteKey(this.menuFor, pressed);
            }
            this.capturing = false;
            this.taken = null;
            return true;
        }
        if (this.menuFor != null && event.key() == InputConstants.KEY_ESCAPE) {
            this.closeMenu();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        List<String> kept = new ArrayList<>(this.layout);
        List<String> before = EmoteWheel.layout();
        for (int i = 0; i < before.size(); i++) {
            String id = before.get(i);
            if (id.startsWith(EmoteLoader.SERVER_PREFIX) && Emotes.get(id) == null && !kept.contains(id)) {
                while (kept.size() <= i) {
                    kept.add("");
                }
                if (kept.get(i).isEmpty()) {
                    kept.set(i, id);
                }
            }
        }
        EmoteWheel.setLayout(kept);
        EmoteWheel.page = this.page;
        EmoteWheel.save();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
