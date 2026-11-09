package com.mki.nightsiege.core;

import com.mki.nightsiege.game.EngineerAbilities;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 4b.2 (Variante B "Turm als Waffe"): places, no remote, the 13 old remote nodes and their points. */
class Tor4bTechnikerTest {

    /** The remote's 13 nodes (konzept.md) – now improvements of the keys. */
    static final List<String> REMOTE_NODES = List.of("e_battery", "e_fastcharge", "e_backup", "e_longrange", "e_circuit",
            "e_overvolt", "e_seeker", "e_beacon", "e_network", "e_fieldtech", "e_fortress", "e_detonator", "e_targetblast");

    @Test
    void everyEngineerHasHisAbilitiesOnPlaces2And3() {
        Map<String, List<String>> want = Map.of(
                SkillTree.SUB_GUNNER, List.of("pierce", "suppress"),
                SkillTree.SUB_ELECTRICIAN, List.of("chain", "static"),
                SkillTree.SUB_DEMOLITION, List.of("grenade", "target"),
                SkillTree.SUB_ALCHEMIST, List.of("boil", "brew"));
        for (Map.Entry<String, List<String>> e : want.entrySet()) {
            String sub = e.getKey();
            assertEquals(AbilityKeys.Key.ABILITY_1, AbilityKeys.keyOf(sub, "escape"), sub);
            assertEquals(AbilityKeys.Key.ABILITY_2, AbilityKeys.keyOf(sub, e.getValue().get(0)), sub);
            assertEquals(AbilityKeys.Key.ABILITY_3, AbilityKeys.keyOf(sub, e.getValue().get(1)), sub);
            assertNull(AbilityKeys.keyOf(sub, AbilityKeys.F_ACTION), sub + ": no remote field any more");
            for (String id : EngineerAbilities.of(sub)) {
                assertNotNull(AbilityKeys.keyOf(sub, id), sub + " " + id + " has a key");
                assertTrue(SkillValues.get("t_" + id, "pause", -1) > 0, id + " has a pause");
            }
        }
        assertEquals(AbilityKeys.Key.ABILITY_4, AbilityKeys.keyOf(SkillTree.SUB_ALCHEMIST, "acid"));
        assertEquals(7, EngineerAbilities.ALL.size());
    }

    @Test
    void theEngineerHasNoRemoteField() {
        SkillTree tree = new SkillTree();
        for (String sub : List.of(SkillTree.SUB_GUNNER, SkillTree.SUB_ELECTRICIAN, SkillTree.SUB_DEMOLITION, SkillTree.SUB_ALCHEMIST)) {
            PlayerSkills p = new PlayerSkills();
            p.chosenClass = SkillClass.ENGINEER.name();
            SkillTree.SUBCLASSES.stream().filter(i -> i.key().equals(sub)).forEach(i -> p.setLevel(i.node(), 1));
            assertNull(AbilityTips.fName(tree, p), sub);
            List<AbilityTip> bar = AbilityTips.barTips(tree, p);
            for (String id : EngineerAbilities.of(sub)) {
                assertTrue(bar.stream().anyMatch(t -> t.slot.equals(id)), sub + " tooltip " + id);
            }
        }
    }

    @Test
    void theRemoteNodesStayWithTheirLevelsSoNoPointIsLost() {
        // Every node keeps its id and its levels: a saved profile loads with the same points on it.
        SkillTree tree = new SkillTree();
        Map<String, Integer> levels = Map.of("e_battery", 3, "e_fastcharge", 3, "e_longrange", 3, "e_circuit", 3, "e_detonator", 3);
        for (String id : REMOTE_NODES) {
            SkillNode n = tree.get(id);
            assertNotNull(n, id);
            assertEquals(levels.getOrDefault(id, 1), n.maxLevel(), id);
        }
        PlayerSkills saved = new PlayerSkills();
        saved.chosenClass = SkillClass.ENGINEER.name();
        saved.setLevel("e_battery", 3);
        saved.setLevel("e_overvolt", 1);
        assertEquals(3, saved.levelOf("e_battery"));
        assertEquals(1, saved.levelOf("e_overvolt"));
    }

    @Test
    void thePausesFollowTheOldRemoteNodes() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.ENGINEER.name();
        assertEquals(1.0, EngineerAbilities.pauseFactor(tree, p, 1.0), 1e-9);
        p.setLevel("e_battery", 3);        // −12 %
        p.setLevel("e_fastcharge", 3);     // −15 %
        assertEquals(0.73, EngineerAbilities.pauseFactor(tree, p, 1.0), 1e-6);
        p.setLevel("e_backup", 1);         // under 30 % health twice as fast
        assertEquals(0.365, EngineerAbilities.pauseFactor(tree, p, 0.2), 1e-6);
        PlayerSkills fort = new PlayerSkills();
        fort.chosenClass = SkillClass.ENGINEER.name();
        fort.setLevel("e_fortress", 1);
        assertEquals(1.2, EngineerAbilities.pauseFactor(tree, fort, 1.0), 1e-6);
    }

    /**
     * Auflage 1: the demolition expert's abilities per minute at level scale 1 (bench reckoning in
     * qa/tor-4b/umbau.md): grenade 6 dmg × 3 monsters every 10 s + the target point's salvo 2 shells
     * × mortar hit 10 × 3 monsters every 20 s = 108 + 180 = 288 per minute on top of his towers.
     */
    @Test
    void theDemolitionAbilitiesStayWithinTheBudget() {
        double grenade = SkillValues.get("t_grenade", "dmg", 0) * 3 * 1200.0 / SkillValues.get("t_grenade", "pause", 1);
        double salvo = SkillValues.get("t_target", "shells", 0) * SkillValues.get("mortar_tower", "hit", 0) * 3
                * 1200.0 / SkillValues.get("t_target", "pause", 1);
        assertEquals(108, grenade, 1e-6);
        assertEquals(180, salvo, 1e-6);
        assertTrue(grenade + salvo <= 300);
    }

    @Test
    void blastPowerWidensTheSalvoAndTheBigThrows() {
        // Sprengkraft (e_blastpower) had no effect after the remote went: now Zielpunkt's salvo and
        // Siedepunkt's big throws take its +50 % on their radius, as the remote's salvo did.
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.ENGINEER.name();
        assertEquals(1.0, tree.blastScale(p), 1e-9);
        p.setLevel("e_blastpower", 1);
        assertEquals(1.5, tree.blastScale(p), 1e-9);
    }

    @Test
    void theThrowFliesFurtherFromAnArrowTurretOrTeslaSeat() {
        // Chef 09.11. 05:35: no mortar seat; seated in an arrow turret or a tesla coil the grenade
        // and the alchemist's potion fly +50 % further (14 -> 21 blocks, potion 10 -> 15).
        double grenade = SkillValues.get("t_grenade", "range", 0);
        double potion = SkillValues.get("potion_tower", "zonerange", 0);
        assertEquals(14, grenade * EngineerAbilities.seatThrow(null), 1e-9);
        assertEquals(21, grenade * EngineerAbilities.seatThrow(TrapKind.TURRET.id()), 1e-9);
        assertEquals(21, grenade * EngineerAbilities.seatThrow(TrapKind.TESLA.id()), 1e-9);
        assertEquals(14, grenade * EngineerAbilities.seatThrow(TrapKind.MORTAR.id()), 1e-9);
        assertEquals(10, potion * EngineerAbilities.seatThrow(null), 1e-9);
        assertEquals(15, potion * EngineerAbilities.seatThrow(TrapKind.TURRET.id()), 1e-9);
        assertEquals(15, potion * EngineerAbilities.seatThrow(TrapKind.TESLA.id()), 1e-9);
        assertEquals(-1, SkillValues.get("t_grenade", "seatrange", -1), 1e-9);
    }

    /** 4b.7 (Chef 08:05): gunner group on the bench St.20 7104/min -> ~4500; levers that hit groups, not one target. */
    @Test
    void gunnerGroupLevers() {
        assertEquals(3, SkillValues.get("t_pierce", "targets", -1), 1e-9);
        assertEquals(0.3, SkillValues.get("t_suppress", "rate", -1), 1e-9);
        assertEquals(2.5, SkillValues.get("t_pierce", "dmg", -1), 1e-9);    // one target unchanged
        assertEquals(0.6, SkillValues.get("t_suppress", "dmg", -1), 1e-9);
    }
}
