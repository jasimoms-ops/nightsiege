package com.mki.nightsiege.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CrosshairMathTest {
    @Test
    void centerIsTheCrosshairsMiddlePixel() {
        // vanilla draws the crosshair at (w - 15) / 2 with 15 pixels: its middle is 7 further
        for (int w = 300; w <= 1000; w++) {
            int left = (w - 15) / 2;
            assertEquals(left + 7, CrosshairMath.center(w));
        }
        assertEquals(479, CrosshairMath.center(960));   // 1920 px at GUI scale 2
        assertEquals(319, CrosshairMath.center(640));   // scale 3
        assertEquals(239, CrosshairMath.center(480));   // scale 4
    }

    @Test
    void ringPointsAreSymmetric() {
        int dots = 32;   // as StrikeRing.DOTS
        double radius = 6.5;
        for (int i = 0; i < dots; i++) {
            double a = 2.0 * Math.PI * i / dots - Math.PI / 2.0;
            double b = a + Math.PI;   // the opposite point
            assertEquals(-CrosshairMath.symRound(Math.cos(a) * radius), CrosshairMath.symRound(Math.cos(b) * radius));
            assertEquals(-CrosshairMath.symRound(Math.sin(a) * radius), CrosshairMath.symRound(Math.sin(b) * radius));
        }
        assertEquals(7, CrosshairMath.symRound(6.5));
        assertEquals(-7, CrosshairMath.symRound(-6.5));
    }
}
