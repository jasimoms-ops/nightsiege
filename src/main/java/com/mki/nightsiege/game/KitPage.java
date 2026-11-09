package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.CrateGains;
import com.mki.nightsiege.core.KitArrange;
import com.mki.nightsiege.core.KitPageView;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.TrapKind;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The kit page of the core window (update 6, Simon 4 November 2026: "ein Feature, um sein Kit-Inventar
 * plus ausgewählte Items für die Nacht zu personalisieren"): the kit of each own class laid out as it
 * comes tonight; the player moves the things, chooses the crate items for the hotbar and the gain
 * keys. Actions: "kit:open:CLASS", "kit:save:CLASS:{json}", "kit:auto:CLASS", "kit:keys:CLASS:a,b,c".
 */
public final class KitPage {

    private KitPage() {
    }

    /** What the page sends when the player moved something: the layout and the chosen crate gains. */
    public static class Setup {
        public Map<String, Integer> layout = new LinkedHashMap<>();
        public List<String> choice = new ArrayList<>();
    }

    public static void onAction(SiegeManager m, ServerPlayer player, String argument) {
        String[] parts = argument == null ? new String[0] : argument.split(":", 3);
        if (parts.length < 2) {
            return;
        }
        PlayerSkills skills = m.save().skillsOf(player.getUUID().toString());
        SkillClass clazz = SkillClass.byName(parts[1]);
        if (skills == null || !skills.hasClass()) {
            return;
        }
        if (clazz == null || clazz.isShared() || !skills.hasUnlocked(clazz)) {
            clazz = skills.clazz();
        }
        String rest = parts.length > 2 ? parts[2] : "";
        switch (parts[0]) {
            case "save" -> save(m, player, skills, clazz, rest);
            case "auto" -> {
                // Back to the automatic kit: no layout, no choice.
                if (m.save().kitLayouts != null && m.save().kitLayouts.containsKey(player.getUUID().toString())) {
                    m.save().kitLayouts.get(player.getUUID().toString()).remove(clazz.name());
                }
                if (m.save().kitCrateChoice != null && m.save().kitCrateChoice.containsKey(player.getUUID().toString())) {
                    m.save().kitCrateChoice.get(player.getUUID().toString()).remove(clazz.name());
                }
                m.persistShortly();
            }
            case "keys" -> {
                SkillClass c = clazz;
                List<String> owned = GainKeys.owned(m, skills, c);
                List<String> order = new ArrayList<>();
                for (String id : rest.split(",")) {
                    if (owned.contains(id) && !order.contains(id)) {
                        order.add(id);
                    }
                }
                int keys = GainKeys.keyCount(m.kits(), skills, c);
                while (order.size() > keys) {
                    order.remove(order.size() - 1);
                }
                if (skills.gainKeyChoice == null) {
                    skills.gainKeyChoice = new HashMap<>();
                }
                skills.gainKeyChoice.put(c.name(), order);
                m.persistShortly();
                m.sendUltHud(player);
            }
            default -> {
                // "open": just the page
            }
        }
        send(m, player, skills, clazz);
    }

    private static void save(SiegeManager m, ServerPlayer player, PlayerSkills skills, SkillClass clazz, String json) {
        Setup setup;
        try {
            setup = Json.fromJson(json, Setup.class);
        } catch (RuntimeException e) {
            setup = null;
        }
        if (setup == null) {
            return;
        }
        Built built = build(m, player, skills, clazz);
        Set<String> gains = new HashSet<>();
        for (KitArrange.Thing t : built.things) {
            if (t.crate()) {
                gains.add(t.gain());
            }
        }
        List<String> choice = new ArrayList<>();
        if (setup.choice != null) {
            for (String g : setup.choice) {
                if (gains.contains(g) && !choice.contains(g)) {
                    choice.add(g);
                }
            }
        }
        Map<String, Integer> layout = KitArrange.clean(built.things, setup.layout);
        String uuid = player.getUUID().toString();
        if (m.save().kitLayouts == null) {
            m.save().kitLayouts = new HashMap<>();
        }
        if (m.save().kitCrateChoice == null) {
            m.save().kitCrateChoice = new HashMap<>();
        }
        m.save().kitLayouts.computeIfAbsent(uuid, k -> new HashMap<>()).put(clazz.name(), layout);
        m.save().kitCrateChoice.computeIfAbsent(uuid, k -> new HashMap<>()).put(clazz.name(), choice);
        m.persistShortly();
    }

    /** The kit of a class as the page shows it: the stacks and their arrangement things. */
    private record Built(KitService.Kit kit, List<ItemStack> stacks, List<KitArrange.Thing> things) {
    }

    private static Built build(SiegeManager m, ServerPlayer player, PlayerSkills skills, SkillClass clazz) {
        int level = m.levelForKit();
        return skills.as(clazz, () -> {
            KitService.Kit kit = m.kits().build(player, skills, level);
            List<ItemStack> stacks = new ArrayList<>(kit.items());
            if (clazz == SkillClass.ENGINEER) {
                // The engineer's traps and remote come after the kit, as at the handing out.
                for (Map.Entry<TrapKind, Integer> e : m.kits().traps().freeTrapCounts(skills).entrySet()) {
                    Item trap = ModContent.TRAP_ITEMS.get(e.getKey());
                    if (trap != null && e.getValue() > 0) {
                        stacks.add(new ItemStack(trap, e.getValue()));
                    }
                }
                if (m.kits().gainLevel(skills, clazz, "notfallmine") > 0 && ModContent.TRAP_ITEMS.get(TrapKind.MORTAR) != null) {
                    stacks.add(KitService.fromGain(new ItemStack(ModContent.TRAP_ITEMS.get(TrapKind.MORTAR), 1), "notfallmine"));
                }
            }
            return new Built(kit, stacks, KitService.things(stacks));
        });
    }

    public static void send(SiegeManager m, ServerPlayer player, PlayerSkills skills, SkillClass clazz) {
        try {
            ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("kit", Json.toJson(view(m, player, skills, clazz))));
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Kit-Seite: {}", e.toString());
        }
    }

    static KitPageView view(SiegeManager m, ServerPlayer player, PlayerSkills skills, SkillClass clazz) {
        KitPageView v = new KitPageView();
        v.clazz = clazz.name();
        v.level = m.levelForKit();
        for (SkillClass c : SkillClass.values()) {
            if (!c.isShared() && skills.hasUnlocked(c)) {
                v.classes.add(c.name());
            }
        }
        List<String> choice = m.kits().choiceOf(player, clazz);
        v.chosen = choice != null;
        Built built = build(m, player, skills, clazz);
        var regs = player.level().registryAccess();
        int[] slots = KitArrange.place(built.things, m.kits().layoutOf(player, clazz), choice);
        List<String> keys = KitArrange.numbered(built.things);
        for (int i = 0; i < KitArrange.SLOTS; i++) {
            v.slots.add(new KitPageView.Slot());
        }
        Map<String, KitPageView.Option> options = new LinkedHashMap<>();
        for (int i = 0; i < built.stacks.size(); i++) {
            ItemStack stack = built.stacks.get(i);
            KitArrange.Thing t = built.things.get(i);
            String json = KitService.encode(KitService.mark(stack.copy()), regs);
            if (t.crate()) {
                KitPageView.Option o = options.computeIfAbsent(t.gain(), g -> {
                    KitPageView.Option n = new KitPageView.Option();
                    n.gain = g;
                    CrateGains.Gain gain = CrateGains.get(g);
                    n.rarity = gain == null ? "" : gain.rarity().name();
                    return n;
                });
                o.items.add(json);
                o.keys.add(keys.get(i));
                if (slots[i] >= 0) {
                    o.taken = true;
                }
            }
            if (slots[i] >= 0) {
                KitPageView.Slot s = v.slots.get(slots[i]);
                s.item = json;
                s.key = keys.get(i);
                s.gain = t.gain();
            }
        }
        v.options.addAll(options.values());
        for (ItemStack piece : built.kit.armor()) {
            v.armor.add(piece == null ? "" : KitService.encode(KitService.mark(piece.copy()), regs));
        }
        v.offhand = built.kit.offhand() == null || built.kit.offhand().isEmpty() ? ""
                : KitService.encode(KitService.mark(built.kit.offhand().copy()), regs);
        // The gains without a place of their own count always.
        for (CrateGains.Gain g : CrateGains.all()) {
            if ((g.clazz() == null || g.clazz() == clazz) && !options.containsKey(g.id()) && !CrateGains.keyAbility(g.id())
                    && m.kits().gainLevel(skills, clazz, g.id()) > 0) {
                v.passive.add(g.id());
            }
        }
        v.keyCount = GainKeys.keyCount(m.kits(), skills, clazz);
        v.keys.addAll(GainKeys.assigned(m, skills, clazz));
        v.keyOwned.addAll(GainKeys.owned(m, skills, clazz));
        return v;
    }
}
