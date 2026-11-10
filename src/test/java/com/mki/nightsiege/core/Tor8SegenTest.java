package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tor 8 (Simon 10.11.): the aura of blessing heals +50 % (1.0 -> 1.5 life per pulse); migration 28. */
class Tor8SegenTest {

    private static SiegeConfig withBlessing(double amount) {
        SiegeConfig server = new SiegeConfig();
        server.settingsVersion = 27;
        SiegeConfig.SkillTweak t = new SiegeConfig.SkillTweak();
        t.amount = amount;
        server.skillTweaks.put("w_blessing", t);
        server.sanitize();
        return server;
    }

    @Test
    void blessingHealsHalfMoreThePaceStays() {
        SkillTree tree = new SkillTree();
        assertEquals(1.5, tree.get("w_blessing").amountPerLevel(), 1e-9);
        assertEquals(1, tree.get("w_blessing").maxLevel());
        assertEquals(60, SkillValues.get("w_blessing", "every", -1), 1e-9);
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        p.setLevel("w_blessing", 1);
        assertEquals(1.5, tree.totalBonus(SkillEffect.HEAL_AURA, p), 1e-9);
    }

    @Test
    void freshConfigWritesNoTweak() {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, fresh.settingsVersion);
        assertNull(fresh.skillTweaks.get("w_blessing"));
    }

    @Test
    void oldServerDefaultBecomesTargetOwnValueStays() {
        assertEquals(1.5, withBlessing(1.0).skillTweaks.get("w_blessing").amount, 1e-9);
        assertEquals(2.0, withBlessing(2.0).skillTweaks.get("w_blessing").amount, 1e-9);
        SiegeConfig twice = withBlessing(1.0);
        twice.sanitize();   // no second step
        assertEquals(1.5, twice.skillTweaks.get("w_blessing").amount, 1e-9);
    }
}
