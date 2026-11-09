package com.mki.nightsiege.core;

import com.mki.nightsiege.game.SiegeSave;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 5.7: the tidy inventory (qa/tor-5/inventar.md, Chef 09.11. 09:12) – charges, lock, stacking, migration. */
class Tor57InventarTest {

    private static java.util.function.ToIntFunction<String> levels(Map<String, Integer> m) {
        return id -> m.getOrDefault(id, 0);
    }

    @Test
    void chargesPerKitAsTheItemsWere() {
        assertEquals(0, GainCharges.perKit(GainCharges.POTION, levels(Map.of()), false));
        assertEquals(2, GainCharges.perKit(GainCharges.POTION, levels(Map.of("heiltrank", 1)), false));
        assertEquals(3, GainCharges.perKit(GainCharges.POTION, levels(Map.of("heiltrank", 2)), false));
        assertEquals(1, GainCharges.perKit(GainCharges.APPLE, levels(Map.of("notration", 1)), false));
        assertEquals(3, GainCharges.perKit(GainCharges.APPLE, levels(Map.of("notration", 3)), false));
        assertEquals(2, GainCharges.perKit(GainCharges.PEARL, levels(Map.of("wurfperlen", 1)), false));
        assertEquals(3, GainCharges.perKit(GainCharges.BARRICADE, levels(Map.of("barrikaden", 1)), false));
        assertEquals(0, GainCharges.perKit("kampfhorn", levels(Map.of("kampfhorn", 1)), false));
    }

    @Test
    void theSameThingsStackInOneField() {
        // potion belt 3 + first-aid kit 2 + the blessing's 2: all in the field "Heiltrank"
        assertEquals(7, GainCharges.perKit(GainCharges.POTION, levels(Map.of("heiltrank", 2, "verbandskasten", 2)), true));
        // the first-aid kit alone opens the field too
        assertEquals(1, GainCharges.perKit(GainCharges.POTION, levels(Map.of("verbandskasten", 1)), false));
        assertEquals(2, GainCharges.perKit(GainCharges.POTION, levels(Map.of()), true));
        assertTrue(CrateGains.keyAbility("verbandskasten"), "first-aid kit counts as on a key (no own field)");
        assertFalse(CrateGains.KEY_ABILITIES.contains("verbandskasten"), "no field of its own");
        for (String f : GainCharges.FIELDS) {
            assertTrue(CrateGains.KEY_ABILITIES.contains(f), f);
            assertTrue(CrateGains.keyAbility(f), f);
            assertTrue(CrateGains.get(f) != null, f);
        }
    }

    @Test
    void chargesAndTheLockAgainstADoublePress() {
        assertEquals(3, GainCharges.left(3, 0));
        assertEquals(2, GainCharges.left(3, 1));
        assertEquals(0, GainCharges.left(3, 5));
        assertTrue(GainCharges.LOCK_TICKS >= 10 && GainCharges.LOCK_TICKS <= 40, "short lock");
        long now = 1000;
        assertTrue(GainCharges.mayFire(2, now, 0));
        long locked = now + GainCharges.LOCK_TICKS;
        assertFalse(GainCharges.mayFire(1, now + 1, locked), "a second press right after: locked");
        assertTrue(GainCharges.mayFire(1, locked, locked), "after the lock: fires");
        assertFalse(GainCharges.mayFire(0, locked + 100, locked), "no charge left");
    }

    @Test
    void theGainsAreAsDecided() {
        assertNull(CrateGains.get("wurfspeere"));
        assertNull(CrateGains.get("wurfbeile"));
        CrateGains.Gain whet = CrateGains.get("wetzstein");
        assertEquals(SkillClass.WARRIOR, whet.clazz());
        assertEquals(CrateRarity.COMMON, whet.rarity());
        assertEquals(3, whet.topStage());
        assertEquals(3, whet.level(3));
        assertFalse(CrateGains.keyAbility("wetzstein"), "passive");
        assertFalse(CrateGains.keyAbility("kriegsbanner"), "passive");
        assertEquals(KeyHint.NONE, KeyHint.forGain("kriegsbanner"));
        assertEquals(KeyHint.GAIN_KEY, KeyHint.forGain("heiltrank"));
        int general = 0;
        for (CrateGains.Gain g : CrateGains.all()) {
            general += g.general() ? 1 : 0;
        }
        assertEquals(15, general, "general gains after the spears left");
    }

    @Test
    void migrationPaysTheSpearsAndAxesBackOnce() {
        SiegeSave save = new SiegeSave();
        PlayerSkills own = new PlayerSkills();
        own.setGainStage(SkillClass.WARRIOR, "wurfbeile", 3);
        own.setGainStage(SkillClass.WARRIOR, "wurfspeere", 1);
        own.setGainStage(SkillClass.ARCHER, "wurfspeere", 1);
        own.setGainStage(SkillClass.ARCHER, "wurfbeile", 2);        // not an archer gain: untouched
        own.setGainStage(SkillClass.WARRIOR, "schaerfe", 2);        // stays
        PlayerSkills other = new PlayerSkills();
        other.setGainStage(SkillClass.GUARDIAN, "heiltrank", 2);    // a charge gain: stays as it is
        save.skills = new HashMap<>(Map.of("p", own, "q", other));
        int before = own.points;
        int want = 3 * CrateRarity.COMMON.duplicatePoints() + 2 * CrateRarity.UNCOMMON.duplicatePoints();
        assertEquals(want, save.migrateLoot57());
        assertEquals(before + want, own.points);
        assertNull(own.gains.get(SkillClass.WARRIOR.name()).get("wurfbeile"));
        assertNull(own.gains.get(SkillClass.WARRIOR.name()).get("wurfspeere"));
        assertNull(own.gains.get(SkillClass.ARCHER.name()).get("wurfspeere"));
        assertEquals(2, own.gains.get(SkillClass.ARCHER.name()).get("wurfbeile"));
        assertEquals(2, own.gains.get(SkillClass.WARRIOR.name()).get("schaerfe"));
        assertEquals(2, other.gainStage(SkillClass.GUARDIAN, "heiltrank"));
        assertEquals(0, save.migrateLoot57(), "once only");
        assertEquals(before + want, own.points);
        assertEquals(0, new SiegeSave().migrateLoot57(), "a fresh save");
    }
}
