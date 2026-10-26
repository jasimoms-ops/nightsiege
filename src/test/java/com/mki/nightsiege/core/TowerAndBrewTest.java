package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Manned towers and the alchemist's potions (Simon, 26 October 2026). */
class TowerAndBrewTest {

    @Test
    void potionsOpenAlongTheAlchemistsTree() {
        PlayerSkills p = new PlayerSkills();
        assertNull(BrewKind.firstUnlocked(p), "no potion without the potion thrower");
        p.setLevel(TrapKind.BREWER.id(), 1);
        assertTrue(BrewKind.HEAL.unlocked(p));
        assertTrue(BrewKind.POISON.unlocked(p));
        assertFalse(BrewKind.SLOW.unlocked(p));
        assertEquals(BrewKind.HEAL, BrewKind.firstUnlocked(p));
        p.setLevel(TrapKind.BREWER.id(), 3);
        assertTrue(BrewKind.SLOW.unlocked(p));
        assertTrue(BrewKind.REGEN.unlocked(p));
        assertFalse(BrewKind.WEAK.unlocked(p));
        p.setLevel("e_acid", 1);
        assertTrue(BrewKind.WEAK.unlocked(p), "either ring IV talent of the alchemist opens weakness");
        assertFalse(BrewKind.STRENGTH.unlocked(p));
        p.setLevel("toxic_cloud", 1);
        assertTrue(BrewKind.STRENGTH.unlocked(p));
        p.setLevel("e_acid_fog", 1);
        assertTrue(BrewKind.SPEED.unlocked(p));
    }

    @Test
    void everyRequiredNodeExistsInTheTree() {
        SkillTree tree = new SkillTree();
        for (BrewKind kind : BrewKind.values()) {
            for (String option : kind.requirement().split("\\|")) {
                String id = option.split(":")[0];
                assertNotNull(tree.get(id), kind + " needs " + id);
            }
            assertSame(kind, BrewKind.byId(kind.id()));
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
        assertEquals(300, SkillValues.get("potion_tower", "flashpause", -1), 1e-9);
    }
}
