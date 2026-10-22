package com.mki.nightsiege.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The handbook: everything about the mod in one window, open at any time with
 * its key, the handbook item or the core's window.
 *
 * <p>Chapters run down the left, the open chapter fills the right in pages.
 * Chapter text lives in the language files; a line starting with {@code ##} is
 * a sub-heading, {@code -} a bullet, {@code @item|text} a line with an item
 * icon, and an empty line a small gap.</p>
 */
public class GuideScreen extends Screen {

    private static final int W = 404;
    private static final int H = 248;
    private static final int BANNER = 26;
    private static final int SIDEBAR = 122;
    private static final int GOLD = 0xFFFFC94A;
    private static final int TEXT = 0xFFE6E2EC;
    private static final int MUTED = 0xFF9A96A4;
    private static final Identifier BACKDROP = Identifier.parse("minecraft:textures/block/dark_oak_planks.png");

    private record Chapter(String id, String icon) {
    }

    private static final List<Chapter> CHAPTERS = List.of(
            new Chapter("intro", "nightsiege:siege_core"),
            new Chapter("core", "minecraft:respawn_anchor"),
            new Chapter("siege", "minecraft:zombie_head"),
            new Chapter("events", "minecraft:redstone"),
            new Chapter("bonus", "minecraft:emerald"),
            new Chapter("classes", "minecraft:nether_star"),
            new Chapter("ult", "minecraft:blaze_powder"),
            new Chapter("archer", "minecraft:bow"),
            new Chapter("warrior", "minecraft:iron_sword"),
            new Chapter("guardian", "minecraft:shield"),
            new Chapter("engineer", "nightsiege:arrow_turret"),
            new Chapter("traps", "nightsiege:spike_trap"),
            new Chapter("infernite", "nightsiege:infernite_ingot"),
            new Chapter("dungeons", "nightsiege:dungeon_map"),
            new Chapter("perks", "nightsiege:night_fragment_blood"),
            new Chapter("commands", "minecraft:command_block"));

    /** One drawn line of a chapter. */
    private record Line(int kind, FormattedCharSequence text, ItemStack icon, int height) {
        static final int PARAGRAPH = 0;
        static final int HEADING = 1;
        static final int BULLET = 2;
        static final int ICON = 3;
        static final int GAP = 4;
    }

    /** Remembered between openings, so the handbook opens where it was left. */
    private static int chapter;
    private static int page;

    private final List<List<Line>> pages = new ArrayList<>();
    private int left;
    private int top;

    public GuideScreen() {
        super(Component.translatable("nightsiege.guide.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        layoutChapter();

        for (int i = 0; i < CHAPTERS.size(); i++) {
            int index = i;
            Button button = Button.builder(Component.empty(), b -> {
                chapter = index;
                page = 0;
                rebuildWidgets();
            }).bounds(sidebarX(), rowY(i), SIDEBAR - 4, 11).build();
            button.setAlpha(0f);
            addRenderableWidget(button);
        }
        int pagerY = top + H - 24;
        int cx = contentX();
        int cw = contentW();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
                .bounds(cx + cw - 70, pagerY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
                .bounds(cx + cw - 22, pagerY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 8, pagerY, 60, 18).build());
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages.size() - 1, page + delta));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            turn(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------
    // layout
    // ------------------------------------------------------------------

    private int innerX() {
        return left + 9;
    }

    private int innerY() {
        return top + 6 + BANNER;
    }

    private int innerH() {
        return H - 6 - BANNER - 30;
    }

    private int sidebarX() {
        return innerX() + 3;
    }

    private int rowY(int index) {
        return innerY() + 3 + index * 11;
    }

    private int contentX() {
        return innerX() + SIDEBAR + 4;
    }

    private int contentW() {
        return W - 18 - SIDEBAR - 4;
    }

    /** Breaks the open chapter into lines and the lines into pages. */
    private void layoutChapter() {
        pages.clear();
        Font font = Minecraft.getInstance().font;
        int width = contentW() - 16;
        Chapter open = CHAPTERS.get(Math.max(0, Math.min(CHAPTERS.size() - 1, chapter)));
        String key = "nightsiege.guide." + open.id() + ".body";
        String body = Language.getInstance().has(key) ? Language.getInstance().getOrDefault(key) : key;

        List<Line> lines = new ArrayList<>();
        for (String raw : body.split("\n")) {
            if (raw.isBlank()) {
                lines.add(new Line(Line.GAP, null, ItemStack.EMPTY, 5));
            } else if (raw.startsWith("## ")) {
                for (FormattedCharSequence part : font.split(FormattedText.of(raw.substring(3)), width)) {
                    lines.add(new Line(Line.HEADING, part, ItemStack.EMPTY, 12));
                }
            } else if (raw.startsWith("- ")) {
                List<FormattedCharSequence> parts = font.split(FormattedText.of(raw.substring(2)), width - 10);
                for (int i = 0; i < parts.size(); i++) {
                    lines.add(new Line(i == 0 ? Line.BULLET : Line.PARAGRAPH, parts.get(i), ItemStack.EMPTY, 10));
                }
            } else if (raw.startsWith("@") && raw.contains("|")) {
                String itemId = raw.substring(1, raw.indexOf('|'));
                String text = raw.substring(raw.indexOf('|') + 1);
                List<FormattedCharSequence> parts = font.split(FormattedText.of(text), width - 22);
                ItemStack icon = iconOf(itemId);
                for (int i = 0; i < parts.size(); i++) {
                    boolean first = i == 0;
                    lines.add(new Line(Line.ICON, parts.get(i), first ? icon : ItemStack.EMPTY,
                            first && parts.size() == 1 ? 18 : 10));
                }
            } else {
                for (FormattedCharSequence part : font.split(FormattedText.of(raw), width)) {
                    lines.add(new Line(Line.PARAGRAPH, part, ItemStack.EMPTY, 10));
                }
            }
        }

        int room = innerH() - 30;
        List<Line> current = new ArrayList<>();
        int used = 0;
        for (Line line : lines) {
            if (used + line.height() > room && !current.isEmpty()) {
                pages.add(current);
                current = new ArrayList<>();
                used = 0;
                if (line.kind() == Line.GAP) {
                    continue;
                }
            }
            current.add(line);
            used += line.height();
        }
        if (!current.isEmpty() || pages.isEmpty()) {
            pages.add(current);
        }
        page = Math.max(0, Math.min(pages.size() - 1, page));
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        drawWindow(gui, font);
        drawSidebar(gui, font, mouseX, mouseY);
        drawContent(gui, font);
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void drawWindow(GuiGraphicsExtractor gui, Font font) {
        int x2 = left + W;
        int y2 = top + H;
        gui.fill(left, top, x2, y2, 0xFF000000);
        gui.fill(left + 1, top + 1, x2 - 1, y2 - 1, 0xFFC6C6C6);
        gui.fill(left + 1, top + 1, x2 - 1, top + 3, 0xFFFFFFFF);
        gui.fill(left + 1, top + 1, left + 3, y2 - 1, 0xFFFFFFFF);
        gui.fill(left + 1, y2 - 3, x2 - 1, y2 - 1, 0xFF555555);
        gui.fill(x2 - 3, top + 1, x2 - 1, y2 - 1, 0xFF555555);

        // Banner.
        int bx1 = left + 4;
        int bx2 = x2 - 4;
        int by1 = top + 4;
        int by2 = by1 + BANNER;
        gui.fill(bx1, by1, bx2, by2, 0xFF17161B);
        int strips = 48;
        int span = (bx2 - bx1) * 2 / 3;
        for (int i = 0; i < strips; i++) {
            int alpha = (int) (0x58 * (1.0 - i / (double) strips));
            gui.fill(bx1 + span * i / strips, by1, bx1 + span * (i + 1) / strips, by2, (alpha << 24) | (GOLD & 0xFFFFFF));
        }
        gui.fill(bx1, by2 - 1, bx2, by2, GOLD);
        gui.fill(bx1, by1, bx2, by1 + 1, 0x40FFFFFF);
        gui.item(iconOf("nightsiege:siege_guide"), bx1 + 6, by1 + 5);
        String title = Component.translatable("nightsiege.guide.title").getString();
        gui.pose().pushMatrix();
        gui.pose().translate(bx1 + 28, by1 + 5);
        gui.pose().scale(2.0f, 2.0f);
        gui.text(font, title, 0, 0, GOLD, true);
        gui.pose().popMatrix();
        gui.text(font, Component.translatable("nightsiege.guide.subtitle").getString(),
                bx1 + 28 + font.width(title) * 2 + 10, by1 + 12, 0xFFB8B4C0, true);

        // Backdrop under both panes.
        int ix = innerX();
        int iy = innerY();
        int iw = W - 18;
        int ih = innerH();
        gui.fill(ix - 1, iy - 1, ix + iw + 1, iy + ih + 1, 0xFF373737);
        gui.fill(ix, iy + ih, ix + iw + 1, iy + ih + 1, 0xFFFFFFFF);
        gui.fill(ix + iw, iy, ix + iw + 1, iy + ih + 1, 0xFFFFFFFF);
        gui.enableScissor(ix, iy, ix + iw, iy + ih);
        for (int ty = iy; ty < iy + ih; ty += 16) {
            for (int tx = ix; tx < ix + iw; tx += 16) {
                gui.blit(RenderPipelines.GUI_TEXTURED, BACKDROP, tx, ty, 0f, 0f, 16, 16, 16, 16);
            }
        }
        gui.disableScissor();
        gui.fill(ix, iy, ix + iw, iy + ih, 0x70000000);
    }

    private void drawSidebar(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        int x = sidebarX();
        gui.fill(x - 3, innerY(), x + SIDEBAR - 3, innerY() + innerH(), 0x90000000);
        for (int i = 0; i < CHAPTERS.size(); i++) {
            int y = rowY(i);
            boolean active = i == chapter;
            boolean hover = mouseX >= x && mouseX < x + SIDEBAR - 4 && mouseY >= y && mouseY < y + 11;
            if (active) {
                gui.fill(x, y, x + SIDEBAR - 6, y + 11, 0x60FFC94A);
                gui.fill(x, y, x + 2, y + 11, GOLD);
            } else if (hover) {
                gui.fill(x, y, x + SIDEBAR - 6, y + 11, 0x30FFFFFF);
            }
            gui.pose().pushMatrix();
            gui.pose().translate(x + 4, y + 0.5f);
            gui.pose().scale(0.65f, 0.65f);
            gui.item(iconOf(CHAPTERS.get(i).icon()), 0, 0);
            gui.pose().popMatrix();
            String name = Component.translatable("nightsiege.guide." + CHAPTERS.get(i).id() + ".title").getString();
            gui.text(font, name, x + 16, y + 2, active ? 0xFFFFFFFF : hover ? 0xFFE0DCE8 : MUTED, true);
        }
    }

    private void drawContent(GuiGraphicsExtractor gui, Font font) {
        int x = contentX();
        int y = innerY() + 4;
        int w = contentW() - 3;
        Chapter open = CHAPTERS.get(Math.max(0, Math.min(CHAPTERS.size() - 1, chapter)));

        gui.pose().pushMatrix();
        gui.pose().translate(x + 6, y + 2);
        gui.pose().scale(1.5f, 1.5f);
        gui.text(font, Component.translatable("nightsiege.guide." + open.id() + ".title").getString(), 0, 0, GOLD, true);
        gui.pose().popMatrix();
        gui.fill(x + 6, y + 17, x + w - 6, y + 18, 0x50FFC94A);

        int ly = y + 23;
        if (!pages.isEmpty()) {
            for (Line line : pages.get(page)) {
                switch (line.kind()) {
                    case Line.HEADING -> gui.text(font, line.text(), x + 6, ly + 2, GOLD, true);
                    case Line.BULLET -> {
                        gui.fill(x + 8, ly + 3, x + 11, ly + 6, GOLD);
                        gui.text(font, line.text(), x + 16, ly, TEXT, true);
                    }
                    case Line.ICON -> {
                        if (!line.icon().isEmpty()) {
                            gui.item(line.icon(), x + 6, ly);
                            int textY = line.height() == 18 ? ly + 4 : ly;
                            gui.text(font, line.text(), x + 26, textY, TEXT, true);
                        } else {
                            gui.text(font, line.text(), x + 26, ly, TEXT, true);
                        }
                    }
                    case Line.PARAGRAPH -> gui.text(font, line.text(), x + 6, ly, TEXT, true);
                    default -> {
                    }
                }
                ly += line.height();
            }
        }
        String pager = Component.translatable("nightsiege.guide.page", page + 1, pages.size()).getString();
        gui.text(font, pager, x + contentW() - 46 - font.width(pager) / 2, top + H - 19, 0xFF404040, false);
    }

    private static ItemStack iconOf(String itemId) {
        Identifier id = Identifier.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
