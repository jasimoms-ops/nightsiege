package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.CrateRoll;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Tor 10 / K3 (Chef 13.11. 02:20, Simon 02:13): the free class pick of the war leader's round and the Letzte
 * Bastion. Every defender plays any subclass on level 20 (the lent profile of the fun mode, Update 7): the
 * points of that level to spend freely or "Empfohlener Aufbau", the kit of level 20, 20 crates with "Alle
 * öffnen". Only lent: the lent profile and its crates live outside the save, so a round's end, a cancel, a
 * log-out and a crash leave the own progress as it was. One code for both rounds.
 */
public final class FreeClassPick {

    /** Where the pick comes from (Tor 11 adds its chapter lock on top). */
    public enum Mode { WAR, BASTION }

    /**
     * The recommended build (the bots' way of the bench and the team simulation, MeleeSim.build): the trunk
     * and the subclass's way, of its subclass group only the subclass, learned in the tree's order like a
     * player ({@link Playtest#learnLikeAPlayer}) with the points of level 20. Left out per subclass:
     */
    static final Map<String, Set<String>> LEAVE_OUT = Map.of(
            "s_golemsmith", Set.of("s_wild_hunt"),     // the titan, not the wolves' wild hunt (27.10.)
            "g_bastion", Set.of("shield_charge"));     // the taunt (the tank's ult and its mastery), not the charge (30.10.)

    private final SiegeManager m;
    private final Random random = new Random();
    /** Crates opened per player and class in this pick (only lent, never saved). */
    private final Map<UUID, Map<String, Integer>> opened = new HashMap<>();

    FreeClassPick(SiegeManager m) {
        this.m = m;
    }

    // ------------------------------------------------------------------ pure parts (JUnit)

    /** The points of the free pick: those of a defender at the kit level (level 20), as the simulation's bots. */
    public static int budget(SiegeConfig cfg) {
        return TestKit.pointsBefore(Math.max(1, cfg.funKitLevel), cfg);
    }

    /** The level a war leader's round fights at: the sim's forced level, else warLevel (K3: 25) with the lent profiles. */
    public static int roundLevel(SiegeConfig cfg, int forced, int worldLevel) {
        return forced > 0 ? forced : cfg.funModeLoans ? Math.max(1, cfg.warLevel) : Math.max(1, worldLevel);
    }

    /** Crates still to open: the configured count minus those opened for this class. */
    public static int due(SiegeConfig cfg, int openedForClass) {
        return Math.max(0, cfg.funCrates - openedForClass);
    }

    /** The recommended build's plan for a subclass: node to level, in the tree's order. */
    public static LinkedHashMap<String, Integer> plan(SkillTree tree, SkillClass clazz, String subclassNode) {
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = clazz.name();
        SkillTree.SubclassInfo info = SkillTree.subclassByNode(subclassNode);
        if (info != null && info.clazz() == clazz) {
            p.planSubclass(p.chosenClass, info.key());
        }
        p.freeSubclassVersion = PlayerSkills.FREE_SUBCLASS_VERSION;
        SkillNode sub = tree.get(subclassNode);
        String way = sub == null ? "" : sub.path();
        Set<String> leaveOut = LEAVE_OUT.getOrDefault(subclassNode, Set.of());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SkillNode n : tree.ofClass(clazz)) {
                if (!(n.onTrunk() || way.equals(n.path())) || leaveOut.contains(n.id())) {
                    continue;
                }
                if (sub != null && n.inGroup() && n.group().equals(sub.group()) && !n.id().equals(subclassNode)) {
                    continue;
                }
                while (p.levelOf(n.id()) < n.maxLevel()) {
                    String why = tree.blockedReason(n.id(), p);
                    if (why != null && !why.equals("points")) {
                        break;
                    }
                    p.setLevel(n.id(), p.levelOf(n.id()) + 1);
                    changed = true;
                }
            }
        }
        LinkedHashMap<String, Integer> planned = new LinkedHashMap<>();
        for (SkillNode n : tree.ofClass(clazz)) {
            if (p.levelOf(n.id()) > 0) {
                planned.put(n.id(), p.levelOf(n.id()));
            }
        }
        return planned;
    }

    /**
     * "Empfohlener Aufbau" on the played lent profile: its tree back to the free subclass level, then the
     * plan learned with the budget. Returns the levels learned (0 without a class or subclass).
     */
    public static int recommend(SkillTree tree, PlayerSkills lent, int budget) {
        SkillClass clazz = lent == null ? null : lent.clazz();
        String key = clazz == null ? null : lent.chosenClass;
        SkillTree.SubclassInfo info = clazz == null ? null : SkillTree.subclassByKey(clazz, lent.plannedSubclass(key));
        if (info == null) {
            return 0;
        }
        // Points beyond the budget (a crate's duplicate) stay.
        int extra = Math.max(0, lent.points + tree.pointsSpentProfile(lent, key) - Math.max(0, budget));
        lent.levels = new HashMap<>();
        lent.history = new ArrayList<>();
        lent.grantFreeSubclass(key);
        lent.points = Math.max(0, budget) + extra;
        return Playtest.learnLikeAPlayer(tree, lent, plan(tree, clazz, info.node()));
    }

    // ------------------------------------------------------------------ the server

    /**
     * The entry (also for Tor 11): lends the player the full profile of every subclass on level 20 with its
     * crates and opens the class window. The round's own ready round is the caller's.
     */
    public void open(ServerPlayer player, Mode mode) {
        if (player == null) {
            return;
        }
        m.lendFun(player);
        if (m.playsLent(player)) {
            m.campClassPick(player, 0);
            NightSiegeMod.LOGGER.info("[Night Siege] Freie Klassenwahl ({}) für {}", mode, player.getScoreboardName());
        }
    }

    /** Crates still waiting for the player's played lent class. */
    public int due(ServerPlayer player) {
        if (player == null || !m.playsLent(player)) {
            return 0;
        }
        PlayerSkills lent = m.save().skillsOf(player.getUUID().toString());
        SkillClass clazz = lent.clazz();
        if (clazz == null) {
            return 0;
        }
        Map<String, Integer> mine = opened.get(player.getUUID());
        return due(m.config(), mine == null ? 0 : mine.getOrDefault(clazz.name(), 0));
    }

    /** "Alle öffnen": every crate of the played lent class at once, on the lent profile (gains on 1-3 as usual). */
    public List<CrateRoll> openAll(ServerPlayer player, boolean window) {
        List<CrateRoll> rolls = new ArrayList<>();
        int due = due(player);
        if (due <= 0) {
            if (window) {
                player.sendSystemMessage(Component.translatable("nightsiege.playtest.no_crates").withStyle(ChatFormatting.GRAY));
            }
            return rolls;
        }
        PlayerSkills lent = m.save().skillsOf(player.getUUID().toString());
        SkillClass clazz = lent.clazz();
        SiegeConfig cfg = m.config();
        Map<String, Integer> mine = opened.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        int done = mine.getOrDefault(clazz.name(), 0);
        for (int i = 0; i < due; i++) {
            done++;
            // No infernite ingot and no statistics: the inventory and the records stay the own ones.
            rolls.add(m.crates().roll(lent, clazz, kindOf(done, cfg), Math.max(1, cfg.funKitLevel)));
        }
        mine.put(clazz.name(), done);
        if (window && !rolls.isEmpty()) {
            m.crates().finishAll(player, rolls);
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Freie Klassenwahl: {} öffnet {} Kisten ({})", player.getScoreboardName(),
                rolls.size(), clazz.name());
        return rolls;
    }

    /**
     * Tor 10: the team simulation's defender bots get the free pick's crates (funCrates) straight on their
     * profile, as a player's "Alle öffnen". Returns the crates opened.
     */
    public int openForSim(PlayerSkills skills) {
        SkillClass clazz = skills == null ? null : skills.clazz();
        if (clazz == null) {
            return 0;
        }
        SiegeConfig cfg = m.config();
        int n = due(cfg, 0);
        for (int i = 1; i <= n; i++) {
            m.crates().roll(skills, clazz, kindOf(i, cfg), Math.max(1, cfg.funKitLevel));
        }
        return n;
    }

    /** The kind of the n-th crate, as in the playtest: a fragment crate for every boss night, event crates by chance. */
    private CrateKind kindOf(int index, SiegeConfig cfg) {
        if (cfg.bossesEnabled && cfg.bossEvery > 0
                && index % Math.max(1, (int) Math.round(cfg.bossEvery * (1.0 + cfg.bonusCrateChance))) == 0) {
            return CrateKind.FRAGMENT;
        }
        return random.nextDouble() < cfg.eventChance ? CrateKind.EVENT : CrateKind.NORMAL;
    }

    /** The lent profile went back: its crates go with it. */
    void forget(UUID id) {
        opened.remove(id);
    }
}
