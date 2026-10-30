package com.mki.nightsiege.game;

import com.mki.nightsiege.core.CrateGains;
import com.mki.nightsiege.core.CrateRarity;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.UltState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The gain keys (balance update 2, Simon 29 October 2026: "alle Sachen aus den Beutekisten, die
 * eine Fähigkeit geben, mit einem Knopf aktivieren, damit man nicht 30 Sachen im Kit hat – aber es
 * sollten auch nicht so viele Fähigkeiten sein"; "2 Gewinn-Tasten reichen, man kann eine 3.
 * gewinnen"). The crate gains with an ability of their own (grappling hook, horns, cloak, trowel,
 * bodyguard chain, spare battery, oil barrel, the summoner's whistle, steed, bowl and bell) are no
 * items any more: two of them (three with "Dritte Hand") sit on the gain keys N, M and J. Which
 * ones, the player picks in the collection of the core window; else the rarest come first.
 */
public final class GainKeys {

    /** Gain keys without the crate gain "Dritte Hand". */
    public static final int BASE_KEYS = 2;
    /** The crate gain that brings the third key. */
    public static final String THIRD = "dritte_hand";
    /** Oil barrels per kit (they were two flasks). */
    private static final int OIL_PER_KIT = 2;

    /** Player → gain → the game time it may be used again, and how long its pause was. */
    private static final Map<UUID, Map<String, long[]>> PAUSES = new HashMap<>();
    /** Player → gain → uses left in this kit (the oil barrel). */
    private static final Map<UUID, Map<String, Integer>> LEFT = new HashMap<>();

    private GainKeys() {
    }

    /** A kit was handed out or taken back: the uses per kit start again (the pauses run on). */
    public static void newKit(ServerPlayer player) {
        LEFT.remove(player.getUUID());
    }

    /** How many gain keys a class has: two, three with "Dritte Hand". */
    public static int keyCount(KitService kits, PlayerSkills skills, SkillClass clazz) {
        return BASE_KEYS + (kits.gainLevel(skills, clazz, THIRD) > 0 ? 1 : 0);
    }

    /** The gain abilities a class can use: drawn, and fitting the way (the bowl is the wild one's, the bell the dead one's). */
    public static List<String> owned(SiegeManager m, PlayerSkills skills, SkillClass clazz) {
        List<String> out = new ArrayList<>();
        if (skills == null || clazz == null) {
            return out;
        }
        String way = skills.as(clazz, () -> skills.pathTaken(m.skillTree()));
        if (way.isEmpty()) {
            String planned = m.skillTree().wayOfSubclass(clazz, m.skillTree().subclassOfProfile(skills, skills.profileFor(clazz)));
            way = planned == null ? "" : planned;
        }
        for (String id : CrateGains.KEY_ABILITIES) {
            CrateGains.Gain gain = CrateGains.get(id);
            if (gain == null || (gain.clazz() != null && gain.clazz() != clazz) || m.kits().gainLevel(skills, clazz, id) <= 0) {
                continue;
            }
            if (id.equals("futternapf") && way.equals(SkillTree.WAY_DEAD)) {
                continue;
            }
            if (id.equals("totenglocke") && !way.equals(SkillTree.WAY_DEAD)) {
                continue;
            }
            out.add(id);
        }
        return out;
    }

    /**
     * The gains on the keys of a class, key by key: the player's own order first, then the rarest
     * of the rest; as many as the class has keys.
     */
    public static List<String> assigned(SiegeManager m, PlayerSkills skills, SkillClass clazz) {
        List<String> owned = owned(m, skills, clazz);
        List<String> out = new ArrayList<>();
        List<String> chosen = skills == null || skills.gainKeyChoice == null || clazz == null ? null
                : skills.gainKeyChoice.get(clazz.name());
        if (chosen != null) {
            for (String id : chosen) {
                if (owned.contains(id) && !out.contains(id)) {
                    out.add(id);
                }
            }
        }
        List<String> rest = new ArrayList<>();
        for (String id : owned) {
            if (!out.contains(id)) {
                rest.add(id);
            }
        }
        rest.sort(Comparator.comparingInt((String id) -> -rarity(id).ordinal()));
        out.addAll(rest);
        int keys = clazz == null ? BASE_KEYS : keyCount(m.kits(), skills, clazz);
        return out.size() > keys ? new ArrayList<>(out.subList(0, keys)) : out;
    }

    private static CrateRarity rarity(String id) {
        CrateGains.Gain gain = CrateGains.get(id);
        return gain == null ? CrateRarity.COMMON : gain.rarity();
    }

    /**
     * A click in the collection: that gain goes onto the first key and the others move one key
     * on (the last one drops off when the keys are full).
     */
    public static boolean choose(SiegeManager m, ServerPlayer player, String className, String gainId) {
        SkillClass clazz = SkillClass.byName(className);
        PlayerSkills skills = m.save().skillsOf(player.getUUID().toString());
        if (clazz == null || skills == null || !owned(m, skills, clazz).contains(gainId)) {
            return false;
        }
        List<String> order = new ArrayList<>(assigned(m, skills, clazz));
        order.remove(gainId);
        order.add(0, gainId);
        int keys = keyCount(m.kits(), skills, clazz);
        while (order.size() > keys) {
            order.remove(order.size() - 1);
        }
        if (skills.gainKeyChoice == null) {
            skills.gainKeyChoice = new HashMap<>();
        }
        skills.gainKeyChoice.put(clazz.name(), order);
        return true;
    }

    /** A gain key (N, M, J): the ability on it, at once. */
    public static void onKey(SiegeManager m, ServerPlayer player, int index) {
        PlayerSkills skills = m.save().skillsOf(player.getUUID().toString());
        if (skills == null || !skills.hasClass() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<String> ids = assigned(m, skills, skills.clazz());
        if (index < 0 || index >= ids.size()) {
            GainGear.bar(player, Component.translatable(ids.isEmpty() ? "nightsiege.gainkey.none" : "nightsiege.gainkey.free"));
            return;
        }
        if (!m.isDefending(player)) {
            GainGear.bar(player, Component.translatable("nightsiege.gadget.night_only"));
            return;
        }
        String id = ids.get(index);
        long now = level.getGameTime();
        long[] pause = PAUSES.getOrDefault(player.getUUID(), Map.of()).get(id);
        if (pause != null && now < pause[0]) {
            GainGear.bar(player, Component.translatable("nightsiege.gainkey.wait",
                    Component.translatable("nightsiege.gain." + id), (pause[0] - now + 19) / 20));
            return;
        }
        int left = usesLeft(player, id);
        if (left == 0) {
            GainGear.bar(player, Component.translatable("nightsiege.gainkey.used_up", Component.translatable("nightsiege.gain." + id)));
            return;
        }
        if (GainGear.usedTonight(player, id)) {
            GainGear.bar(player, Component.translatable("nightsiege.gadget.once_a_night"));
            return;
        }
        int ticks = GainGear.useAbility(player, level, id);
        if (ticks < 0) {
            return;
        }
        if (left > 0) {
            LEFT.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(id, left - 1);
        }
        if (ticks > 0) {
            PAUSES.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(id, new long[]{now + ticks, ticks});
        }
    }

    /** Uses left in this kit, or -1 for no limit. */
    private static int usesLeft(ServerPlayer player, String id) {
        if (!id.equals("oelfass")) {
            return -1;
        }
        return LEFT.getOrDefault(player.getUUID(), Map.of()).getOrDefault(id, OIL_PER_KIT);
    }

    /** The boxes of the gain keys for the HUD: icon, name, pause, and spent ones greyed. */
    public static List<UltState.Slot> slots(SiegeManager m, ServerPlayer player, long now) {
        List<UltState.Slot> out = new ArrayList<>();
        PlayerSkills skills = m.save().skillsOf(player.getUUID().toString());
        if (skills == null || !skills.hasClass()) {
            return out;
        }
        for (String id : assigned(m, skills, skills.clazz())) {
            UltState.Slot slot = new UltState.Slot();
            slot.id = id;
            slot.icon = CrateGains.icon(id);
            slot.name = "nightsiege.gain." + id;
            long[] pause = PAUSES.getOrDefault(player.getUUID(), Map.of()).get(id);
            if (pause != null) {
                slot.totalTicks = (int) pause[1];
                slot.cooldownTicks = (int) Math.max(0L, pause[0] - now);
            }
            int left = usesLeft(player, id);
            if (left >= 0) {
                slot.sub = "nightsiege.wheel.count";
                slot.subArgs.add(String.valueOf(left));
            }
            slot.current = left == 0 || GainGear.usedTonight(player, id);   // spent: the HUD greys it out
            out.add(slot);
        }
        return out;
    }

    /** For the tests: the pause left on a gain, in ticks. */
    public static long pauseLeft(ServerPlayer player, String id) {
        long[] pause = PAUSES.getOrDefault(player.getUUID(), Map.of()).get(id);
        return pause == null ? 0L : Math.max(0L, pause[0] - player.level().getGameTime());
    }
}
