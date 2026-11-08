package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 3.1 ("Nur Tasten"): every ability of all 20 subclasses on one key out of Q, F, Y, X; none twice. */
class AbilityKeysTest {

    private static final List<String> SUBS = List.of(
            SkillTree.SUB_SWIFT, SkillTree.SUB_HEAVY, SkillTree.SUB_BOLTER, SkillTree.SUB_TRAPPER,
            SkillTree.SUB_BLADEMASTER, SkillTree.SUB_BERSERKER, SkillTree.SUB_AXE, SkillTree.SUB_PALADIN,
            SkillTree.SUB_SHIELDFIGHTER, SkillTree.SUB_BULWARK, SkillTree.SUB_MEDIC, SkillTree.SUB_WARDEN,
            SkillTree.SUB_GUNNER, SkillTree.SUB_ELECTRICIAN, SkillTree.SUB_DEMOLITION, SkillTree.SUB_ALCHEMIST,
            SkillTree.SUB_TAMER, SkillTree.SUB_GOLEMSMITH, SkillTree.SUB_NECROMANCER, SkillTree.SUB_SOULCATCHER);

    /** The two subclasses with a fourth ability: on sneak + Y (Leiter 08.11.). */
    private static final Set<String> OVERFLOW = Set.of(SkillTree.SUB_ALCHEMIST + ":acid", SkillTree.SUB_NECROMANCER + ":curse");

    @Test
    void allTwentySubclassesHaveATable() {
        assertEquals(20, SUBS.size());
        assertEquals(new HashSet<>(SUBS), AbilityKeys.table().keySet());
    }

    @Test
    void everyAbilityOneKeyNoneTwice() {
        Set<AbilityKeys.Key> allowed = EnumSet.of(AbilityKeys.Key.Q, AbilityKeys.Key.F, AbilityKeys.Key.Y);
        Set<String> overflow = new HashSet<>();
        for (String sub : SUBS) {
            Set<AbilityKeys.Key> used = EnumSet.of(AbilityKeys.Key.X);   // the ult
            assertEquals(AbilityKeys.Key.X, AbilityKeys.keyOf(sub, AbilityKeys.ULT), sub);
            Set<String> ids = new HashSet<>();
            for (AbilityKeys.Entry e : AbilityKeys.table().get(sub)) {
                assertTrue(ids.add(e.id()), sub + ": " + e.id() + " twice");
                assertEquals(e.key(), AbilityKeys.keyOf(sub, e.id()), sub + ":" + e.id());
                if (e.key() == AbilityKeys.Key.SNEAK_Y) {
                    overflow.add(sub + ":" + e.id());
                    continue;
                }
                assertTrue(allowed.contains(e.key()), sub + ":" + e.id() + " on " + e.key());
                assertTrue(used.add(e.key()), sub + ": key " + e.key() + " twice");
            }
            assertFalse(AbilityKeys.table().get(sub).isEmpty(), sub);
        }
        assertEquals(OVERFLOW, overflow);
    }

    @Test
    void unknownAbilityHasNoKey() {
        assertNull(AbilityKeys.keyOf(SkillTree.SUB_SWIFT, "raise"));
        assertNull(AbilityKeys.keyOf(null, "dash"));
    }
}
