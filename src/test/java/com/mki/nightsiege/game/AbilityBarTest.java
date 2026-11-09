package com.mki.nightsiege.game;

import com.mki.nightsiege.core.AbilityKeys;
import com.mki.nightsiege.core.AbilityTips;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.TankHeal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tor 4c.1 (Chef 08:40): the boxes of {@code SiegeManager.abilitySlots} for all 20 subclasses, with the
 * whole tree of their own way and with only the subclass node, with and without the arrow gain: every box
 * has an entry of its own in {@link AbilityKeys} (none falls back to C), no key twice, the F field's place
 * stays free, and never more boxes than places (nothing cut off).
 * The boxes are built here in the order of {@code SiegeManager.abilitySlots} from the same sources.
 */
class AbilityBarTest {

    private final SkillTree tree = new SkillTree();

    /** The subclass node, and with {@code full} every node of the class on its own way (first ultimate only). */
    private PlayerSkills skills(SkillTree.SubclassInfo info, boolean full) {
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = info.clazz().name();
        p.setLevel(info.node(), 1);
        if (!full) {
            return p;
        }
        Set<String> subNodes = new HashSet<>();
        SkillTree.SUBCLASSES.forEach(i -> subNodes.add(i.node()));
        boolean ult = false;
        for (SkillNode n : tree.all()) {
            if (n.clazz() != info.clazz() || subNodes.contains(n.id())) {
                continue;
            }
            if (!n.onTrunk() && !n.clazz().isShared() && !info.way().equals(n.path())) {
                continue;   // only the own way (as the scenes: pathTaken)
            }
            if (n.effect().target() == SkillEffect.Target.ULTIMATE) {
                if (ult || !info.way().equals(n.path())) {
                    continue;
                }
                ult = true;
            }
            p.setLevel(n.id(), n.maxLevel());
        }
        return p;
    }

    /** The ids of the bar in the order of SiegeManager.abilitySlots. */
    private List<String> bar(PlayerSkills own, boolean quiverGain) {
        String sub = tree.subclassOf(own);
        List<String> out = new ArrayList<>();
        boolean wheel = false;
        if (own.clazz() == SkillClass.ENGINEER) {
            out.add(EngineerEscape.ID);
            out.addAll(EngineerAbilities.of(sub));
            if (SkillTree.SUB_ALCHEMIST.equals(sub)) {
                out.add(TrapService.BREW_SLOT);
                if (SkillTree.has(own, "e_acid_flask")) {
                    out.add("acid");
                }
            }
        } else {
            List<String> spells = own.clazz() == SkillClass.SUMMONER && SkillTree.WAY_DEAD.equals(own.pathTaken(tree))
                    ? Companions.spellsOf(own) : List.of();
            List<String> heals = AbilityTips.heals(tree, own);
            List<String> options = !spells.isEmpty() ? spells : heals;
            wheel = !options.isEmpty();
            out.addAll(options);
            boolean summoner = own.clazz() == SkillClass.SUMMONER;
            List<String> extras = AbilityKeys.barExtras(sub, wheel, quiverGain, summoner);
            if (extras.contains(AbilityKeys.QUIVER)) {
                out.add(GainGear.QUIVER_SLOT);
            }
            if (extras.contains(AbilityKeys.CALL)) {
                out.add(AbilityKeys.CALL);
            }
        }
        if (SkillTree.SUB_HEAVY.equals(sub)) {
            out.add(0, EngineerEscape.ID);
        }
        if (own.clazz() == SkillClass.WARRIOR) {
            out.add(0, WarriorDash.ID);
        }
        if (SkillTree.SUB_MEDIC.equals(sub) && AbilityTips.heals(tree, own).contains("hand")) {
            out.add(GuardianEffects.SELF_HEAL);
        }
        if (own.clazz() == SkillClass.GUARDIAN && TankHeal.isTank(sub)) {
            out.add(TankHeal.ID);
        }
        if (own.clazz() == SkillClass.GUARDIAN && tree.totalBonus(SkillEffect.SHIELD_THROW, own) > 0) {
            out.add(0, AbilityKeys.SHIELD);
        }
        return out;
    }

    @Test
    void everyBoxOfEverySubclassHasAPlaceOfItsOwn() {
        List<String> bad = new ArrayList<>();
        int cases = 0;
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            for (boolean full : new boolean[]{false, true}) {
                for (boolean quiver : new boolean[]{false, true}) {
                    PlayerSkills own = skills(info, full);
                    String sub = tree.subclassOf(own);
                    assertEquals(info.key(), sub, "subclass of " + info.key());
                    List<String> ids = bar(own, quiver);
                    String what = sub + (full ? " full" : " node") + (quiver ? " +arrows" : "") + " " + ids;
                    cases++;
                    Set<AbilityKeys.Key> used = new HashSet<>();
                    AbilityKeys.Key f = AbilityKeys.keyOf(sub, AbilityKeys.F_ACTION);
                    if (f != null) {
                        used.add(f);   // the F field (place 2) is the class's own: no box on it
                    }
                    for (String id : ids) {
                        AbilityKeys.Key key = AbilityKeys.keyOf(sub, id);
                        if (key == null) {
                            bad.add(what + ": " + id + " has no entry (would land on C)");
                        } else if (!key.ability() || !used.add(key)) {
                            bad.add(what + ": " + id + " on " + key + " twice");
                        }
                    }
                    if (ids.size() > AbilityKeys.ABILITY_PLACES.size()) {
                        bad.add(what + ": " + ids.size() + " boxes for " + AbilityKeys.ABILITY_PLACES.size() + " places");
                    }
                    List<String> problems = new ArrayList<>();
                    List<AbilityKeys.Key> keys = AbilityKeys.assign(sub, ids, problems);
                    assertEquals(ids.size(), keys.size(), what);
                    if (!problems.isEmpty()) {
                        bad.add(what + ": assign " + problems);
                    }
                }
            }
        }
        assertEquals(80, cases);
        assertTrue(bad.isEmpty(), bad.size() + " of " + cases + ": " + String.join("\n", bad));
    }

    @Test
    void aBoxWithoutEntryIsReportedAndTakesAFreePlaceNotC() {
        List<String> problems = new ArrayList<>();
        List<AbilityKeys.Key> keys = AbilityKeys.assign(SkillTree.SUB_SWIFT, List.of("quiver", "unknown"), problems);
        assertEquals(AbilityKeys.Key.ABILITY_3, keys.get(0));
        assertEquals(AbilityKeys.Key.ABILITY_1, keys.get(1));   // place 2 is the arrow rain's
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("unknown"), problems.toString());
    }

    @Test
    void moreBoxesThanPlacesAreReported() {
        List<String> problems = new ArrayList<>();
        AbilityKeys.assign(SkillTree.SUB_ALCHEMIST, List.of("escape", "boil", "brew", "acid", "x"), problems);
        assertTrue(problems.stream().anyMatch(s -> s.contains("5")), problems.toString());
    }
}
