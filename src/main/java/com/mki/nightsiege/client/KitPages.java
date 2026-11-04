package com.mki.nightsiege.client;

import com.mki.nightsiege.core.CrateGains;
import com.mki.nightsiege.core.CrateRarity;
import com.mki.nightsiege.core.KitArrange;
import com.mki.nightsiege.core.KitPageView;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.game.Json;
import com.mki.nightsiege.game.KitService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The kit page of the core window (update 6, Simon 4 November 2026: "ein Feature, um sein Kit-Inventar
 * plus ausgewählte Items für die Nacht zu personalisieren"; the crate items with a place of their own
 * go only into the hotbar, "so viele, wie in die Hotbar passen"; the gain keys on the same page).
 *
 * <p>The kit of one own class as it comes tonight: armour and off hand to look at, the inventory and
 * the hotbar to arrange by drag and drop, the crate items on the right to take into the hotbar or to
 * leave at home, the gains that always count, and the gain keys. Every change goes to the server at
 * once ("kit:save"), which answers with the kit as it will really be.</p>
 */
final class KitPages {

    private KitPages() {
    }

    private static KitPageView view;
    private static long askedAt;
    /** The class shown (null: the one played). */
    private static String shown;

    // The working copy of the slots (the server's answer replaces it).
    private static final String[] keys = new String[KitArrange.SLOTS];
    private static final String[] gains = new String[KitArrange.SLOTS];
    private static final ItemStack[] stacks = new ItemStack[KitArrange.SLOTS];
    private static final ItemStack[] armor = new ItemStack[4];
    private static ItemStack offhand = ItemStack.EMPTY;
    /** Option (crate gain) → its stacks and keys. */
    private static final Map<String, List<ItemStack>> optionStacks = new LinkedHashMap<>();

    // Dragging.
    private static int dragSlot = -1;
    private static String dragOption;
    private static double pressX;
    private static double pressY;
    private static String message = "";
    private static int messageColor = 0xFFE05A5A;
    private static long messageAt;

    private static Consumer<String> sender = a -> { };

    // The places of the last frame, for the mouse.
    private static final int[] slotX = new int[KitArrange.SLOTS];
    private static final int[] slotY = new int[KitArrange.SLOTS];
    private static int slotSize = 18;
    private static final Map<String, int[]> optionRows = new LinkedHashMap<>();
    private static final Map<String, int[]> classButtons = new LinkedHashMap<>();
    private static final Map<String, int[]> keyBoxes = new LinkedHashMap<>();
    private static final Map<String, int[]> ownedAbilities = new LinkedHashMap<>();
    private static int[] autoButton;
    private static int[] optionsArea;

    private static final int GOLD = 0xFFFFD86B;
    private static final int RED = 0xFFE05A5A;
    private static final int GREEN = 0xFF7FE08A;

    // ------------------------------------------------------------------ data

    static void receive(KitPageView v) {
        if (v == null) {
            return;
        }
        view = v;
        shown = v.clazz;
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var regs = mc.level.registryAccess();
        for (int i = 0; i < KitArrange.SLOTS; i++) {
            KitPageView.Slot s = i < v.slots.size() ? v.slots.get(i) : new KitPageView.Slot();
            keys[i] = s.key == null ? "" : s.key;
            gains[i] = s.gain == null ? "" : s.gain;
            stacks[i] = s.item == null || s.item.isEmpty() ? ItemStack.EMPTY : KitService.decode(s.item, regs);
        }
        for (int i = 0; i < 4; i++) {
            String json = i < v.armor.size() ? v.armor.get(i) : "";
            armor[i] = json == null || json.isEmpty() ? ItemStack.EMPTY : KitService.decode(json, regs);
        }
        offhand = v.offhand == null || v.offhand.isEmpty() ? ItemStack.EMPTY : KitService.decode(v.offhand, regs);
        optionStacks.clear();
        for (KitPageView.Option o : v.options) {
            List<ItemStack> list = new ArrayList<>();
            for (String json : o.items) {
                list.add(KitService.decode(json, regs));
            }
            optionStacks.put(o.gain, list);
        }
    }

    static boolean wantsData() {
        return view == null || System.currentTimeMillis() - askedAt > 3000;
    }

    /** The action that asks for the page: the class shown, else the one played. */
    static String request(String played) {
        askedAt = System.currentTimeMillis();
        return "kit:open:" + (shown != null ? shown : played == null ? "" : played);
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the page into the window's inner area; returns the tooltip under the mouse, or null. */
    static List<String> draw(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h, int mouseX, int mouseY,
                             Consumer<String> send) {
        sender = send == null ? a -> { } : send;
        optionRows.clear();
        classButtons.clear();
        keyBoxes.clear();
        ownedAbilities.clear();
        autoButton = null;
        optionsArea = null;
        if (view == null) {
            String t = tr("nightsiege.kitpage.loading");
            gui.text(font, t, x + (w - font.width(t)) / 2, y + h / 2 - 4, PurpleUi.GREY, true);
            return null;
        }
        if (view.classes.isEmpty()) {
            String t = tr("nightsiege.kitpage.no_class");
            gui.text(font, t, x + (w - font.width(t)) / 2, y + h / 2 - 4, PurpleUi.GREY, true);
            return null;
        }
        List<String> tip = null;

        // The pennants of the own classes.
        int bx = x + 4;
        for (String c : view.classes) {
            SkillClass clazz = SkillClass.byName(c);
            String label = clazz == null ? c : tr(clazz.translationKey());
            int bw = font.width(label) + 12;
            boolean on = c.equals(view.clazz);
            boolean hover = in(mouseX, mouseY, bx, y + 2, bw, 14);
            PurpleUi.button(gui, font, bx, y + 2, bw, 14, label, true, hover, on);
            if (clazz != null) {
                gui.fill(bx + 1, y + 15, bx + bw - 1, y + 16, CoreColors.classColor(clazz));
            }
            classButtons.put(c, new int[]{bx, y + 2, bw, 14});
            bx += bw + 3;
        }
        String lvl = tr("nightsiege.kitpage.level", view.level);
        gui.text(font, lvl, x + w - 4 - font.width(lvl), y + 5, PurpleUi.GREY, true);

        // Left: armour, the inventory and the hotbar.
        int top = y + 22;
        int leftW = Math.min(w * 3 / 5, 300);
        slotSize = Math.max(16, Math.min(22, (leftW - 30) / 10));
        int s = slotSize;
        int gx = x + 6 + s + 8;
        gui.text(font, tr("nightsiege.kitpage.inventory"), gx, top, PurpleUi.LIGHT, true);
        int iy = top + 11;
        for (int i = 9; i < KitArrange.SLOTS; i++) {
            int col = (i - 9) % 9;
            int row = (i - 9) / 9;
            slotX[i] = gx + col * s;
            slotY[i] = iy + row * s;
        }
        int hy = iy + 3 * s + 14;
        gui.text(font, tr("nightsiege.kitpage.hotbar"), gx, hy - 11, GOLD, true);
        for (int i = 0; i < 9; i++) {
            slotX[i] = gx + i * s;
            slotY[i] = hy;
        }
        // the hotbar's gold frame
        gui.fill(gx - 2, hy - 2, gx + 9 * s + 2, hy + s + 2, 0xFF8A6A2A);
        gui.fill(gx - 1, hy - 1, gx + 9 * s + 1, hy + s + 1, 0xFF1A1622);
        for (int i = 0; i < KitArrange.SLOTS; i++) {
            boolean hover = in(mouseX, mouseY, slotX[i], slotY[i], s, s);
            boolean crate = !gains[i].isEmpty();
            int frame = crate ? rarityColor(gains[i]) : PurpleUi.LINE;
            boolean target = (dragSlot >= 0 || dragOption != null) && hover;
            if (target) {
                frame = allowedDrop(i) ? GREEN : RED;
            }
            gui.fill(slotX[i], slotY[i], slotX[i] + s - 1, slotY[i] + s - 1, frame);
            gui.fill(slotX[i] + 1, slotY[i] + 1, slotX[i] + s - 2, slotY[i] + s - 2, hover ? 0xFF2A2436 : 0xFF0E0C14);
            if (i != dragSlot && !stacks[i].isEmpty()) {
                int o = (s - 1 - 16) / 2;
                gui.item(stacks[i], slotX[i] + o, slotY[i] + o);
                gui.itemDecorations(font, stacks[i], slotX[i] + o, slotY[i] + o);
            }
            if (hover && !stacks[i].isEmpty() && dragSlot < 0 && dragOption == null) {
                tip = slotTip(i);
            }
        }
        // armour and off hand (to look at)
        int ax = x + 6;
        for (int i = 0; i < 4; i++) {
            int ay = iy + i * s;
            gui.fill(ax, ay, ax + s - 1, ay + s - 1, PurpleUi.LINE);
            gui.fill(ax + 1, ay + 1, ax + s - 2, ay + s - 2, 0xFF15121C);
            if (!armor[i].isEmpty()) {
                int o = (s - 1 - 16) / 2;
                gui.item(armor[i], ax + o, ay + o);
                if (in(mouseX, mouseY, ax, ay, s, s)) {
                    tip = List.of(armor[i].getHoverName().getString(), "§8" + tr("nightsiege.kitpage.fixed_armor"));
                }
            }
        }
        gui.fill(ax, hy, ax + s - 1, hy + s - 1, PurpleUi.LINE);
        gui.fill(ax + 1, hy + 1, ax + s - 2, hy + s - 2, 0xFF15121C);
        if (!offhand.isEmpty()) {
            int o = (s - 1 - 16) / 2;
            gui.item(offhand, ax + o, hy + o);
            if (in(mouseX, mouseY, ax, hy, s, s)) {
                tip = List.of(offhand.getHoverName().getString(), "§8" + tr("nightsiege.kitpage.fixed_offhand"));
            }
        }
        // Under the hotbar: how many places are free, and "automatic".
        int free = 0;
        for (int i = 0; i < 9; i++) {
            if (stacks[i].isEmpty()) {
                free++;
            }
        }
        int ly = hy + s + 6;
        small(gui, font, tr("nightsiege.kitpage.free", free), gx, ly, free > 0 ? PurpleUi.LIGHT : PurpleUi.GREY, 0.8f);
        String state = tr(view.chosen ? "nightsiege.kitpage.state_chosen" : "nightsiege.kitpage.state_auto");
        small(gui, font, state, gx, ly + 9, view.chosen ? GREEN : PurpleUi.GREY, 0.8f);
        String auto = tr("nightsiege.kitpage.auto");
        int aw = font.width(auto) + 12;
        int axb = gx + 9 * s - aw;
        boolean ah = in(mouseX, mouseY, axb, ly, aw, 14);
        PurpleUi.button(gui, font, axb, ly, aw, 14, auto, view.chosen, ah, false);
        autoButton = new int[]{axb, ly, aw, 14};
        if (ah) {
            tip = List.of(auto, "§7" + tr("nightsiege.kitpage.auto_tip"));
        }
        if (!message.isEmpty() && System.currentTimeMillis() - messageAt < 5000) {
            small(gui, font, font.plainSubstrByWidth(message, (int) ((leftW + 40) / 0.8f)), x + 6, ly + 20, messageColor, 0.8f);
        }

        // Right: the crate items, the gains that always count, the gain keys.
        int rx = x + 6 + leftW + 30;
        int rw = x + w - 6 - rx;
        if (rw < 120) {
            rx = x + w - 126;
            rw = 120;
        }
        int ry = top;
        gui.text(font, tr("nightsiege.kitpage.crate_head"), rx, ry, GOLD, true);
        ry += 11;
        int listTop = ry;
        if (view.options.isEmpty()) {
            small(gui, font, tr("nightsiege.kitpage.crate_none"), rx, ry, PurpleUi.GREY, 0.8f);
            ry += 10;
        }
        for (KitPageView.Option o : view.options) {
            boolean taken = taken(o.gain);
            int rowH = 18;
            boolean hover = in(mouseX, mouseY, rx, ry, rw, rowH);
            gui.fill(rx, ry, rx + rw, ry + rowH - 1, hover ? 0xFF2A2436 : taken ? 0xFF1E2A1C : 0xFF16131D);
            gui.fill(rx, ry, rx + 2, ry + rowH - 1, rarityColor(o.gain));
            List<ItemStack> list = optionStacks.getOrDefault(o.gain, List.of());
            if (!list.isEmpty() && !(dragOption != null && dragOption.equals(o.gain))) {
                gui.item(list.get(0), rx + 4, ry + 1);
            }
            String name = tr("nightsiege.gain." + o.gain);
            String places = list.size() > 1 ? " · " + tr("nightsiege.kitpage.places", list.size()) : "";
            String st = tr(taken ? "nightsiege.kitpage.in_hotbar" : "nightsiege.kitpage.at_home");
            int stw = (int) (font.width(st) * 0.7f);
            gui.text(font, font.plainSubstrByWidth(name + places, rw - 26 - stw - 4), rx + 23, ry + 5, taken ? 0xFFFFFFFF : PurpleUi.LIGHT, true);
            small(gui, font, st, rx + rw - 3 - stw, ry + 6, taken ? GREEN : PurpleUi.DIM, 0.7f);
            optionRows.put(o.gain, new int[]{rx, ry, rw, rowH});
            if (hover && dragSlot < 0 && dragOption == null) {
                List<String> t = new ArrayList<>();
                t.add(name);
                String desc = "nightsiege.gain." + o.gain + ".desc";
                if (net.minecraft.locale.Language.getInstance().has(desc)) {
                    t.add("§7" + tr(desc));
                }
                t.add("§8" + tr(taken ? "nightsiege.kitpage.click_home" : "nightsiege.kitpage.click_take"));
                tip = t;
            }
            ry += rowH;
        }
        optionsArea = new int[]{rx, listTop, rw, Math.max(18, ry - listTop)};
        ry += 6;
        // The gains that always count.
        if (!view.passive.isEmpty()) {
            gui.text(font, tr("nightsiege.kitpage.passive_head"), rx, ry, PurpleUi.LIGHT, true);
            ry += 11;
            int px = rx;
            for (String g : view.passive) {
                if (px + 18 > rx + rw) {
                    px = rx;
                    ry += 18;
                }
                gui.fill(px, ry, px + 17, ry + 17, rarityColor(g));
                gui.fill(px + 1, ry + 1, px + 16, ry + 16, 0xFF0E0C14);
                gui.item(CoreScreen.iconOf(CrateGains.icon(g)), px + 1, ry + 1);
                if (in(mouseX, mouseY, px, ry, 17, 17)) {
                    List<String> t = new ArrayList<>();
                    t.add(tr("nightsiege.gain." + g));
                    String desc = "nightsiege.gain." + g + ".desc";
                    if (net.minecraft.locale.Language.getInstance().has(desc)) {
                        t.add("§7" + tr(desc));
                    }
                    t.add("§8" + tr("nightsiege.kitpage.passive_tip"));
                    tip = t;
                }
                px += 19;
            }
            ry += 22;
        }
        // The gain keys.
        if (view.keyCount > 0 && (!view.keyOwned.isEmpty() || !view.keys.isEmpty())) {
            gui.text(font, tr("nightsiege.kitpage.keys_head"), rx, ry, PurpleUi.LIGHT, true);
            ry += 11;
            String[] letters = {"N", "M", "J"};
            int kx = rx;
            for (int k = 0; k < view.keyCount; k++) {
                String id = k < view.keys.size() ? view.keys.get(k) : "";
                boolean hover = in(mouseX, mouseY, kx, ry, 30, 20);
                gui.fill(kx, ry, kx + 30, ry + 20, id.isEmpty() ? PurpleUi.LINE : GOLD);
                gui.fill(kx + 1, ry + 1, kx + 29, ry + 19, hover ? 0xFF2A2436 : 0xFF0E0C14);
                gui.text(font, letters[Math.min(2, k)], kx + 3, ry + 6, GOLD, true);
                if (!id.isEmpty()) {
                    gui.item(CoreScreen.iconOf(CrateGains.icon(id)), kx + 12, ry + 2);
                    keyBoxes.put(id, new int[]{kx, ry, 30, 20});
                    if (hover) {
                        tip = List.of(tr("nightsiege.gain." + id), "§8" + tr("nightsiege.kitpage.key_remove"));
                    }
                }
                kx += 34;
            }
            ry += 24;
            int ox = rx;
            for (String id : view.keyOwned) {
                if (view.keys.contains(id)) {
                    continue;
                }
                if (ox + 18 > rx + rw) {
                    ox = rx;
                    ry += 18;
                }
                boolean hover = in(mouseX, mouseY, ox, ry, 17, 17);
                gui.fill(ox, ry, ox + 17, ry + 17, hover ? GOLD : PurpleUi.LINE);
                gui.fill(ox + 1, ry + 1, ox + 16, ry + 16, 0xFF0E0C14);
                gui.item(CoreScreen.iconOf(CrateGains.icon(id)), ox + 1, ry + 1);
                ownedAbilities.put(id, new int[]{ox, ry, 17, 17});
                if (hover) {
                    tip = List.of(tr("nightsiege.gain." + id), "§8" + tr("nightsiege.kitpage.key_put"));
                }
                ox += 19;
            }
        }

        // What the mouse carries.
        ItemStack carried = dragSlot >= 0 ? stacks[dragSlot]
                : dragOption != null && !optionStacks.getOrDefault(dragOption, List.of()).isEmpty()
                ? optionStacks.get(dragOption).get(0) : ItemStack.EMPTY;
        if (!carried.isEmpty()) {
            gui.nextStratum();
            gui.item(carried, mouseX - 8, mouseY - 8);
            gui.itemDecorations(font, carried, mouseX - 8, mouseY - 8);
        }
        return tip;
    }

    private static List<String> slotTip(int i) {
        List<String> t = new ArrayList<>();
        t.add(stacks[i].getHoverName().getString() + (stacks[i].getCount() > 1 ? " ×" + stacks[i].getCount() : ""));
        if (!gains[i].isEmpty()) {
            t.add("§6" + tr("nightsiege.kitpage.crate_item", tr("nightsiege.gain." + gains[i])));
            t.add("§8" + tr("nightsiege.kitpage.crate_item_tip"));
        } else {
            t.add("§8" + tr("nightsiege.kitpage.own_item_tip"));
        }
        return t;
    }

    // ------------------------------------------------------------------ mouse

    static boolean click(double mx, double my) {
        if (view == null) {
            return false;
        }
        for (Map.Entry<String, int[]> e : classButtons.entrySet()) {
            if (in(mx, my, e.getValue())) {
                if (!e.getKey().equals(view.clazz)) {
                    shown = e.getKey();
                    askedAt = System.currentTimeMillis();
                    sender.accept("kit:open:" + shown);
                }
                return true;
            }
        }
        if (autoButton != null && in(mx, my, autoButton) && view.chosen) {
            sender.accept("kit:auto:" + view.clazz);
            say(tr("nightsiege.kitpage.auto_done"), GREEN);
            return true;
        }
        for (Map.Entry<String, int[]> e : keyBoxes.entrySet()) {
            if (in(mx, my, e.getValue())) {
                List<String> order = new ArrayList<>(view.keys);
                order.remove(e.getKey());
                sendKeys(order);
                return true;
            }
        }
        for (Map.Entry<String, int[]> e : ownedAbilities.entrySet()) {
            if (in(mx, my, e.getValue())) {
                List<String> order = new ArrayList<>(view.keys);
                if (order.size() >= view.keyCount) {
                    order.remove(order.size() - 1);   // the last key gives way
                }
                order.add(e.getKey());
                sendKeys(order);
                return true;
            }
        }
        for (int i = 0; i < KitArrange.SLOTS; i++) {
            if (!stacks[i].isEmpty() && in(mx, my, slotX[i], slotY[i], slotSize, slotSize)) {
                dragSlot = i;
                pressX = mx;
                pressY = my;
                return true;
            }
        }
        for (Map.Entry<String, int[]> e : optionRows.entrySet()) {
            if (in(mx, my, e.getValue())) {
                dragOption = e.getKey();
                pressX = mx;
                pressY = my;
                return true;
            }
        }
        return false;
    }

    static boolean release(double mx, double my) {
        if (dragSlot < 0 && dragOption == null) {
            return false;
        }
        int target = slotAt(mx, my);
        boolean moved = Math.abs(mx - pressX) + Math.abs(my - pressY) > 3;
        if (dragSlot >= 0) {
            int from = dragSlot;
            dragSlot = -1;
            if (target >= 0 && target != from) {
                move(from, target);
            } else if (target < 0 && optionsArea != null && in(mx, my, optionsArea) && !gains[from].isEmpty()) {
                leaveHome(gains[from]);
            }
            return true;
        }
        String gain = dragOption;
        dragOption = null;
        if (!moved) {
            // A click on the row: take it along or leave it at home.
            if (taken(gain)) {
                leaveHome(gain);
            } else {
                take(gain, -1);
            }
        } else if (target >= 0) {
            take(gain, target);
        }
        return true;
    }

    private static int slotAt(double mx, double my) {
        for (int i = 0; i < KitArrange.SLOTS; i++) {
            if (in(mx, my, slotX[i], slotY[i], slotSize, slotSize)) {
                return i;
            }
        }
        return -1;
    }

    /** True when what is carried may be put into this slot. */
    private static boolean allowedDrop(int slot) {
        if (dragOption != null) {
            return slot < KitArrange.HOTBAR;
        }
        if (dragSlot < 0) {
            return false;
        }
        if (!gains[dragSlot].isEmpty() && slot >= KitArrange.HOTBAR) {
            return false;
        }
        return !(!gains[slot].isEmpty() && dragSlot >= KitArrange.HOTBAR);
    }

    /** Swaps two slots, if the rule allows it (crate items stay in the hotbar). */
    private static void move(int from, int to) {
        if (!gains[from].isEmpty() && to >= KitArrange.HOTBAR) {
            say(tr("nightsiege.kitpage.only_hotbar"), RED);
            return;
        }
        if (!gains[to].isEmpty() && from >= KitArrange.HOTBAR) {
            say(tr("nightsiege.kitpage.only_hotbar"), RED);
            return;
        }
        swap(from, to);
        save();
    }

    private static void swap(int a, int b) {
        String k = keys[a];
        keys[a] = keys[b];
        keys[b] = k;
        String g = gains[a];
        gains[a] = gains[b];
        gains[b] = g;
        ItemStack s = stacks[a];
        stacks[a] = stacks[b];
        stacks[b] = s;
    }

    /** Takes a crate gain's stacks into the hotbar (the first into the slot dropped on, if free). */
    private static void take(String gain, int wanted) {
        KitPageView.Option o = option(gain);
        if (o == null) {
            return;
        }
        if (taken(gain)) {
            if (wanted >= 0 && wanted < KitArrange.HOTBAR) {
                // already along: move its first stack there
                for (int i = 0; i < KitArrange.HOTBAR; i++) {
                    if (gain.equals(gains[i])) {
                        move(i, wanted);
                        return;
                    }
                }
            }
            return;
        }
        List<Integer> freeSlots = new ArrayList<>();
        if (wanted >= 0 && wanted < KitArrange.HOTBAR && stacks[wanted].isEmpty()) {
            freeSlots.add(wanted);
        }
        for (int i = 0; i < KitArrange.HOTBAR; i++) {
            if (stacks[i].isEmpty() && !freeSlots.contains(i)) {
                freeSlots.add(i);
            }
        }
        List<ItemStack> list = optionStacks.getOrDefault(gain, List.of());
        if (freeSlots.size() < list.size()) {
            say(tr("nightsiege.kitpage.no_room", list.size()), RED);
            return;
        }
        for (int k = 0; k < list.size(); k++) {
            int slot = freeSlots.get(k);
            stacks[slot] = list.get(k).copy();
            keys[slot] = k < o.keys.size() ? o.keys.get(k) : "";
            gains[slot] = gain;
        }
        save();
    }

    private static void leaveHome(String gain) {
        for (int i = 0; i < KitArrange.SLOTS; i++) {
            if (gain.equals(gains[i])) {
                stacks[i] = ItemStack.EMPTY;
                keys[i] = "";
                gains[i] = "";
            }
        }
        save();
    }

    private static boolean taken(String gain) {
        for (int i = 0; i < KitArrange.SLOTS; i++) {
            if (gain.equals(gains[i])) {
                return true;
            }
        }
        return false;
    }

    private static KitPageView.Option option(String gain) {
        for (KitPageView.Option o : view.options) {
            if (o.gain.equals(gain)) {
                return o;
            }
        }
        return null;
    }

    /** Sends the layout and the chosen crate gains; the server answers with the kit as it will be. */
    private static void save() {
        Map<String, Object> setup = new HashMap<>();
        Map<String, Integer> layout = new LinkedHashMap<>();
        List<String> choice = new ArrayList<>();
        for (int i = 0; i < KitArrange.SLOTS; i++) {
            if (!keys[i].isEmpty()) {
                layout.put(keys[i], i);
            }
            if (!gains[i].isEmpty() && !choice.contains(gains[i])) {
                choice.add(gains[i]);
            }
        }
        setup.put("layout", layout);
        setup.put("choice", choice);
        sender.accept("kit:save:" + view.clazz + ":" + Json.toJson(setup));
        say(tr("nightsiege.kitpage.saved"), GREEN);
    }

    private static void sendKeys(List<String> order) {
        sender.accept("kit:keys:" + view.clazz + ":" + String.join(",", order));
    }

    // ------------------------------------------------------------------ small helpers

    private static void say(String text, int color) {
        message = text;
        messageColor = color;
        messageAt = System.currentTimeMillis();
    }

    private static int rarityColor(String gain) {
        CrateGains.Gain g = CrateGains.get(gain);
        CrateRarity r = g == null ? null : g.rarity();
        return r == null ? PurpleUi.LINE : 0xFF000000 | r.color();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean in(double mx, double my, int[] r) {
        return r != null && in(mx, my, r[0], r[1], r[2], r[3]);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, true);
        gui.pose().popMatrix();
    }
}
