package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Manned towers and the alchemist's potions (Simon, 26 October 2026). */
class TowerAndBrewTest {

    @Test
    void potionsAreNodesBehindTheAlchemistsTree() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        String key = p.addProfile(SkillClass.ENGINEER, SkillTree.SUB_ALCHEMIST);
        p.chosenClass = key;
        p.points = 10000;
        assertNull(BrewKind.firstUnlocked(p), "no potion without its node");
        for (String id : new String[] {TrapKind.SPIKE.id(), TrapKind.SNARE.id(), "e_detonator"}) {
            p.setLevel(id, 3);
        }
        assertEquals("locked", tree.blockedReason(BrewKind.HEAL.nodeId(), p), "the potion thrower first");
        p.setLevel(TrapKind.BREWER.id(), 1);
        assertNull(tree.blockedReason(BrewKind.HEAL.nodeId(), p));
        assertNull(tree.blockedReason(BrewKind.POISON.nodeId(), p));
        assertEquals("locked", tree.blockedReason(BrewKind.SLOW.nodeId(), p));
        assertFalse(BrewKind.HEAL.unlocked(p), "the thrower alone teaches no potion any more");
        assertTrue(tree.unlock(BrewKind.HEAL.nodeId(), p));
        assertTrue(BrewKind.HEAL.unlocked(p));
        assertEquals(BrewKind.HEAL, BrewKind.firstUnlocked(p));
        p.setLevel(TrapKind.BREWER.id(), 3);
        assertNull(tree.blockedReason(BrewKind.REGEN.nodeId(), p));
        assertEquals("locked", tree.blockedReason(BrewKind.STRENGTH.nodeId(), p), "strength still needs the toxic cloud");
        assertEquals(SkillEffect.BREW, tree.get(BrewKind.SPEED.nodeId()).effect());
        assertEquals(KeyHint.BAR, KeyHint.forNode(tree, BrewKind.SPEED.nodeId()));
        assertEquals(KeyHint.BAR, KeyHint.forNode(tree, "e_acid_flask"));
    }

    /** Simon, 28 October 2026: three of the seven potions, and the acid flask in exchange. */
    @Test
    void onlyThreeOfTheSevenPotions() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        String key = p.addProfile(SkillClass.ENGINEER, SkillTree.SUB_ALCHEMIST);
        p.chosenClass = key;
        p.points = 10000;
        for (String id : new String[] {TrapKind.SPIKE.id(), TrapKind.SNARE.id(), "e_detonator", TrapKind.BREWER.id()}) {
            p.setLevel(id, 3);
        }
        assertEquals(3, SkillTree.brewLimit());
        assertTrue(tree.unlock(BrewKind.HEAL.nodeId(), p));
        assertTrue(tree.unlock(BrewKind.SLOW.nodeId(), p));
        assertTrue(tree.unlock(BrewKind.REGEN.nodeId(), p));
        assertEquals("choice", tree.blockedReason(BrewKind.POISON.nodeId(), p));
        assertEquals(List.of(BrewKind.HEAL, BrewKind.SLOW, BrewKind.REGEN), BrewKind.learned(p));
        assertNull(tree.blockedReason("e_acid_flask", p), "the acid flask needs the thrower at level 2");
        // A demolition expert never reaches a potion, not even the deep ones behind the shared ultimate.
        PlayerSkills d = new PlayerSkills();
        d.chosenClass = SkillClass.ENGINEER.name();
        d.points = 10000;
        d.setLevel(TrapKind.MORTAR.id(), 3);
        d.setLevel("toxic_cloud", 1);
        d.setLevel("e_acid_fog", 1);
        assertNotNull(tree.blockedReason(BrewKind.STRENGTH.nodeId(), d));
        assertNotNull(tree.blockedReason(BrewKind.SPEED.nodeId(), d));
    }

    @Test
    void oldAlchemistsGetTheirFirstThreePotions() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.ENGINEER.name();
        p.setLevel(TrapKind.BREWER.id(), 3);
        p.setLevel("e_acid", 1);
        assertEquals(1, tree.migrateBrews(p));
        assertEquals(List.of(BrewKind.HEAL, BrewKind.POISON, BrewKind.SLOW), BrewKind.learned(p));
        assertEquals(0, p.levelOf(BrewKind.REGEN.nodeId()));
        // Too many potion nodes: the extra ones are paid back.
        PlayerSkills many = new PlayerSkills();
        many.chosenClass = SkillClass.ENGINEER.name();
        many.setLevel(TrapKind.BREWER.id(), 3);
        for (BrewKind kind : BrewKind.values()) {
            many.setLevel(kind.nodeId(), 1);
        }
        assertEquals(1, tree.migrateBrews(many));
        assertEquals(3, SkillTree.brewsLearned(many, null));
        assertTrue(many.points() > 0);
        // A demolition expert keeps what he has.
        PlayerSkills demo = new PlayerSkills();
        demo.chosenClass = SkillClass.ENGINEER.name();
        demo.setLevel(TrapKind.MORTAR.id(), 3);
        assertEquals(0, tree.migrateBrews(demo));
    }

    @Test
    void everyRequiredNodeExistsInTheTree() {
        SkillTree tree = new SkillTree();
        for (BrewKind kind : BrewKind.values()) {
            for (String all : kind.requirement().split(",")) {
                for (String option : all.split("\\|")) {
                    String id = option.split(":")[0];
                    assertNotNull(tree.get(id), kind + " needs " + id);
                }
            }
            assertNotNull(tree.get(kind.nodeId()), kind + " has no node");
            assertSame(kind, BrewKind.byId(kind.id()));
            assertSame(kind, BrewKind.byNode(kind.nodeId()));
        }
    }

    @Test
    void fourPotionsHelpAndThreeHurt() {
        int help = 0;
        for (BrewKind kind : BrewKind.values()) {
            if (kind.forPlayers()) {
                help++;
            }
        }
        assertEquals(4, help);
        assertEquals(3, BrewKind.values().length - help);
    }

    @Test
    void mannedTowersBeatLonelyOnes() {
        for (String tower : new String[] {TrapKind.TURRET.id(), TrapKind.TESLA.id()}) {
            assertEquals(0.6, SkillValues.get(tower, "unmanned", -1), 1e-9);
            assertEquals(1.4, SkillValues.get(tower, "manned", -1), 1e-9);
        }
        assertEquals(10, SkillValues.get("potion_tower", "zonerange", -1), 1e-9);
        assertEquals(400, SkillValues.get("potion_tower", "zonepause", -1), 1e-9);
        assertEquals(400, SkillValues.get(BrewKind.HEAL.nodeId(), "pause", -1), 1e-9);
        assertEquals(300, SkillValues.get("potion_tower", "flashpause", -1), 1e-9);
    }

    @Test
    void theSeatGuardsAgainstShots() {
        assertEquals(0.7, SkillValues.get(TrapKind.TURRET.id(), "seatguard", -1), 1e-9);
        assertEquals(0.7, SkillValues.get(TrapKind.TESLA.id(), "seatguard", -1), 1e-9);
    }

    /** Simon, 27 October 2026: two of the four salvos, not all four on top of the rings. */
    @Test
    void onlyTwoOfTheFourSalvos() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        String key = p.addProfile(SkillClass.ENGINEER, SkillTree.SUB_DEMOLITION);
        p.chosenClass = key;
        p.points = 10000;
        for (String id : new String[] {TrapKind.SPIKE.id(), TrapKind.SNARE.id(), "e_detonator", TrapKind.MORTAR.id()}) {
            p.setLevel(id, 3);
        }
        assertEquals(2, SkillTree.salvoLimit());
        assertNull(tree.blockedReason("e_barrage", p));
        p.setLevel("e_barrage", 1);
        assertNull(tree.blockedReason("e_fire_salvo", p));
        p.setLevel("e_fire_salvo", 1);
        assertEquals("choice", tree.blockedReason("e_area_fire", p));
        assertEquals("choice", tree.blockedReason("e_smoke_salvo", p));
    }

    /**
     * Simon, 27 October 2026: picking the first class and its subclass opened a second, empty
     * profile for the subclass while the played one had none, so both ways stood open.
     */
    @Test
    void theFirstClassGetsOneProfileWithItsSubclass() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        String key = p.addProfile(SkillClass.ENGINEER, "gunner");
        p.chosenClass = key;
        p.points = 10000;
        assertEquals("ENGINEER", key);
        assertEquals(1, p.profilesOf(SkillClass.ENGINEER).size());
        assertEquals("gunner", p.plannedSubclass(p.chosenClass));
        p.setLevel(TrapKind.SPIKE.id(), 3);
        p.setLevel(TrapKind.SNARE.id(), 3);
        assertEquals("subclass_planned", tree.blockedReason("e_detonator", p), "the other way is shut");
        p.setLevel("e_circuit", 3);
        assertEquals("subclass_planned", tree.blockedReason(TrapKind.TESLA.id(), p), "the other subclass is shut");
        assertNull(tree.blockedReason(TrapKind.TURRET.id(), p));
    }
}
