package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tor 8.3 (Chef 10.11.): Schwung +100 % -> +70 %, Scharfe Klinge 20 -> 26 %, Gewitter 8/10/12 -> 10/13/15. */
class Tor8WerteTest {

    @Test
    void momentumKeenThunderstorm() {
        SkillTree tree = new SkillTree();
        assertEquals(0.7, tree.get("w_momentum").amountPerLevel(), 1e-9);
        assertEquals(0.26, tree.get("m_cross_keen").amountPerLevel(), 1e-9);
        assertEquals(10, SkillValues.get("thunderstorm", "dmg1", -1), 1e-9);
        assertEquals(13, SkillValues.get("thunderstorm", "dmg2", -1), 1e-9);
        assertEquals(15, SkillValues.get("thunderstorm", "dmg3", -1), 1e-9);
    }
}
