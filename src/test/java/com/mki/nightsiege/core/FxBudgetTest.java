package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Z4 (Simon 12.11.): the particle budget per player and the class colours of the ability effects. */
class FxBudgetTest {

    @Test
    void oneSourceIsCappedAndRefills() {
        FxBudget b = new FxBudget();
        assertEquals(FxBudget.CAPACITY, b.take(1, 10_000, 0) + b.take(1, 10_000, 0));
        assertEquals(0, b.take(1, 50, 0));
        assertEquals(FxBudget.REFILL * 5, b.take(1, 10_000, 5));
    }

    @Test
    void sixPlayersStayUnderTheFrameCap() {
        FxBudget b = new FxBudget();
        // 6 players fire their ultimates in the same tick, every tick for 3 seconds
        long total = 0;
        for (int t = 0; t < 60; t++) {
            int frame = 0;
            for (int p = 0; p < 6; p++) {
                frame += b.take(p, 600, t);
            }
            assertTrue(frame <= FxBudget.FRAME_CAP, "tick " + t + ": " + frame);
            total += frame;
        }
        // on average no more than the six refills a tick after the first full buckets
        assertTrue(total <= 6L * FxBudget.maxOver(60), "total " + total);
    }

    @Test
    void scaleFollowsSimonsNumbers() {
        assertTrue(FxBudget.SIZE >= 1.5 && FxBudget.SIZE <= 2.0);
        assertEquals(1.5, FxBudget.COUNT);
        assertEquals(1.5, FxBudget.LIFE);
    }

    @Test
    void everySubclassHasItsOwnAccentAndEveryClassItsColour() {
        Set<Integer> bases = new HashSet<>();
        for (SkillClass c : SkillClass.values()) {
            bases.add(FxColors.base(c));
        }
        assertEquals(SkillClass.values().length, bases.size());
        Set<Integer> accents = new HashSet<>();
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            accents.add(FxColors.accent(info.clazz(), info.key()));
        }
        assertEquals(SkillTree.SUBCLASSES.size(), accents.size());
        assertEquals(0xE0302A, FxColors.base(SkillClass.WARRIOR));
    }
}
