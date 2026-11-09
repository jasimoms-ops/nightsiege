package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tor 9b P2: elite life by monster type. */
class Tor9bEliteLifeTest {

    @Test
    void eliteLifeByType() {
        double life = new SiegeConfig().eliteMobLife;   // 1.75: +75 %
        assertEquals(0.75, EliteLife.bonus("minecraft:zombie", life), 1e-9);
        assertEquals(0.75, EliteLife.bonus("minecraft:skeleton", life), 1e-9);
        assertEquals(0.75, EliteLife.bonus("minecraft:blaze", life), 1e-9);   // big and slow: full bonus
        assertEquals(0.375, EliteLife.bonus("minecraft:breeze", life), 1e-9);  // halved
        assertEquals(0.375, EliteLife.bonus("minecraft:vex", life), 1e-9);     // halved
        assertEquals(0.0, EliteLife.bonus("minecraft:breeze", 1.0), 1e-9);     // no bonus stays none
    }
}
