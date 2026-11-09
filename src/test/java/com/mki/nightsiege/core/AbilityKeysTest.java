package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tor 3.1 ("Nur Tasten"), Tor 3.2b (Chef 09.11. 01:38): every ability of all 20 subclasses on one place
 * out of 1-4 (Q, E, F, C), the ult on X; no key twice, no "sneak + key" any more.
 */
class AbilityKeysTest {

    private static final List<String> SUBS = List.of(
            SkillTree.SUB_SWIFT, SkillTree.SUB_HEAVY, SkillTree.SUB_BOLTER, SkillTree.SUB_TRAPPER,
            SkillTree.SUB_BLADEMASTER, SkillTree.SUB_BERSERKER, SkillTree.SUB_AXE, SkillTree.SUB_PALADIN,
            SkillTree.SUB_SHIELDFIGHTER, SkillTree.SUB_BULWARK, SkillTree.SUB_MEDIC, SkillTree.SUB_WARDEN,
            SkillTree.SUB_GUNNER, SkillTree.SUB_ELECTRICIAN, SkillTree.SUB_DEMOLITION, SkillTree.SUB_ALCHEMIST,
            SkillTree.SUB_TAMER, SkillTree.SUB_GOLEMSMITH, SkillTree.SUB_NECROMANCER, SkillTree.SUB_SOULCATCHER);

    /** The fourth abilities on place 4 (C): alchemist, necromancer, and the tanks' arrows (their self heal is on 3). */
    private static final Set<String> FOURTH = Set.of(SkillTree.SUB_ALCHEMIST + ":acid", SkillTree.SUB_NECROMANCER + ":curse",
            SkillTree.SUB_SHIELDFIGHTER + ":quiver", SkillTree.SUB_BULWARK + ":quiver");

    @Test
    void allTwentySubclassesHaveATable() {
        assertEquals(20, SUBS.size());
        assertEquals(new HashSet<>(SUBS), AbilityKeys.table().keySet());
    }

    @Test
    void everyAbilityOneKeyNoneTwice() {
        Set<String> fourth = new HashSet<>();
        for (String sub : SUBS) {
            Set<AbilityKeys.Key> used = EnumSet.of(AbilityKeys.Key.ULT);
            assertEquals(AbilityKeys.Key.ULT, AbilityKeys.keyOf(sub, AbilityKeys.ULT), sub);
            Set<String> ids = new HashSet<>();
            for (AbilityKeys.Entry e : AbilityKeys.table().get(sub)) {
                assertTrue(ids.add(e.id()), sub + ": " + e.id() + " twice");
                assertEquals(e.key(), AbilityKeys.keyOf(sub, e.id()), sub + ":" + e.id());
                assertTrue(e.key().ability(), sub + ":" + e.id() + " on " + e.key());
                assertTrue(used.add(e.key()), sub + ": key " + e.key() + " twice");
                if (e.key() == AbilityKeys.Key.ABILITY_4) {
                    fourth.add(sub + ":" + e.id());
                }
            }
            assertFalse(AbilityKeys.table().get(sub).isEmpty(), sub);
        }
        assertEquals(FOURTH, fourth);
    }

    /** Chef 01:38: Q / E / F, C, X, R, 1-3, I; every default key once, none with sneak, Y free. */
    @Test
    void nightLayoutEveryKeyOnce() {
        StringBuilder all = new StringBuilder();
        Set<String> letters = new HashSet<>();
        for (AbilityKeys.Key key : AbilityKeys.Key.values()) {
            assertTrue(letters.add(key.letter()), key + " twice: " + key.letter());
            assertFalse(key.name().contains("SNEAK"), key.name());
            all.append(key.letter());
        }
        assertEquals("QEFCXR123I", all.toString());
        assertFalse(letters.contains("Y"), "Y is free again (Xaeros Minimap)");
        assertEquals(List.of("Q", "E", "F", "C"), AbilityKeys.ABILITY_PLACES.stream().map(AbilityKeys.Key::letter).toList());
        assertEquals(List.of("1", "2", "3"), AbilityKeys.GAIN_PLACES.stream().map(AbilityKeys.Key::letter).toList());
        for (int i = 0; i < AbilityKeys.ABILITY_PLACES.size(); i++) {
            assertEquals(i, AbilityKeys.ABILITY_PLACES.get(i).place());
        }
        assertEquals(-1, AbilityKeys.Key.ULT2.place());
        assertEquals('Q', AbilityKeys.Key.ABILITY_1.glfw());   // GLFW_KEY_Q
        assertEquals('1', AbilityKeys.Key.GAIN_1.glfw());      // GLFW_KEY_1
    }

    /**
     * Tor 3.6 (mod-pruefer): the bar took only one wheel, so a tamer with the arrow gain lost his
     * call and a healer lost his arrows. With and without the gain, beside a wheel or not: every
     * arrow kind and call of the table gets its slot.
     */
    @Test
    void quiverAndCallKeepTheirSlot() {
        Set<String> summoners = Set.of(SkillTree.SUB_TAMER, SkillTree.SUB_GOLEMSMITH, SkillTree.SUB_NECROMANCER, SkillTree.SUB_SOULCATCHER);
        // the class's own wheel: spells (way of the dead), healings (guardians), never for the way of the light
        Set<String> wheels = Set.of(SkillTree.SUB_NECROMANCER, SkillTree.SUB_SOULCATCHER,
                SkillTree.SUB_SHIELDFIGHTER, SkillTree.SUB_BULWARK, SkillTree.SUB_MEDIC, SkillTree.SUB_WARDEN);
        Set<String> engineers = Set.of(SkillTree.SUB_GUNNER, SkillTree.SUB_ELECTRICIAN, SkillTree.SUB_DEMOLITION, SkillTree.SUB_ALCHEMIST);
        for (String sub : SUBS) {
            if (engineers.contains(sub)) {
                continue;   // their bar is TrapService.engineerSlots, without extras
            }
            for (boolean gain : new boolean[] {false, true}) {
                List<String> extras = AbilityKeys.barExtras(sub, wheels.contains(sub), gain, summoners.contains(sub));
                String what = sub + (gain ? " with" : " without") + " arrow gain";
                for (AbilityKeys.Entry e : AbilityKeys.table().get(sub)) {
                    if (e.id().equals(AbilityKeys.QUIVER)) {
                        assertEquals(gain, extras.contains(AbilityKeys.QUIVER), what);
                    }
                    if (e.id().equals(AbilityKeys.CALL)) {
                        assertTrue(extras.contains(AbilityKeys.CALL), what);
                    }
                }
                for (String id : extras) {
                    assertNotNull(AbilityKeys.keyOf(sub, id), what + ": " + id + " without a key");
                }
            }
        }
        // without a subclass and without a wheel the arrows still get a slot (place 4, C)
        assertEquals(List.of(AbilityKeys.QUIVER), AbilityKeys.barExtras("", false, true, false));
    }

    @Test
    void unknownAbilityHasNoKey() {
        assertNull(AbilityKeys.keyOf(SkillTree.SUB_SWIFT, "raise"));
        assertNull(AbilityKeys.keyOf(null, "dash"));
    }

    /** Tor 4b, Auflage 5: the statistics count each key on its own (not only "faehigkeit_f"). */
    @Test
    void statsCountEachKeyOnItsOwn() {
        assertEquals("ULT", AbilityKeys.statsKey("ult", null));
        assertEquals("ABILITY_2", AbilityKeys.statsKey("ult2", null));
        assertEquals("ULT2", AbilityKeys.statsKey("ult2x", null));
        Set<String> seen = new HashSet<>();
        for (AbilityKeys.Key key : AbilityKeys.ABILITY_PLACES) {
            seen.add(AbilityKeys.statsKey("slot", key.name()));
        }
        assertEquals(Set.of("ABILITY_1", "ABILITY_2", "ABILITY_3", "ABILITY_4"), seen);
        assertNull(AbilityKeys.statsKey("slot", "GAIN_1"));
        assertNull(AbilityKeys.statsKey("slot", null));
        assertNull(AbilityKeys.statsKey("gain", "ABILITY_1"));
        assertNull(AbilityKeys.statsKey(null, null));
    }

    /** Prüfer-Fund 4b: hammering a key in the pause or without a target counts nothing; one real use counts once. */
    @Test
    void onlyRealUsesCount() {
        int counted = 0;
        int cooldown = 0;
        for (int press = 0; press < 10; press++) {   // ten presses, the first fires (pause 200 starts)
            int after = press == 0 ? 200 : cooldown;
            if (AbilityKeys.fired(cooldown, after, 200)) {
                counted++;
            }
            cooldown = after;
        }
        assertEquals(1, counted);
        assertFalse(AbilityKeys.fired(0, 0, 200));    // no target: no pause started
        assertFalse(AbilityKeys.fired(0, -1, 200));   // the box went away
        assertTrue(AbilityKeys.fired(0, 0, 0));       // a box without a pause counts as before
        assertFalse(AbilityKeys.fired(15, 15, 0));
    }
}
