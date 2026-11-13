package com.mki.nightsiege.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Paket 13.11b: footer rows and the chapter line never overlap (text widths about 6 px a letter, as the MC font). */
class UiFitTest {

    private static final int GAP = 4;
    // The skill tree footer of the free class pick: back, recommended, crates, other class, ready (gold, right).
    private static final int[] ORDER = {4, 2, 1, 3, 0};   // as the ranks in CoreScreen
    private static final int[] MIN = {60, 70, 70, 70, 90};

    /** Widths close to the MC font: most letters 6, narrow ones less, arrows and ticks 8. */
    private static int w(String s) {
        int sum = 0;
        for (char c : s.toCharArray()) {
            sum += c == ' ' ? 4 : "i!.,:;|·".indexOf(c) >= 0 ? 2 : c == 'l' ? 3 : "tI()[]".indexOf(c) >= 0 ? 4
                    : "fk".indexOf(c) >= 0 ? 5 : c > 0x2000 ? 8 : 6;
        }
        return sum;
    }

    private static UiFit.Row fun(String[] full, String[] shorter, int avail) {
        int[] f = new int[full.length];
        int[] s = new int[full.length];
        for (int i = 0; i < full.length; i++) {
            f[i] = w(full[i]);
            s[i] = w(shorter[i]);
        }
        return UiFit.row(f, s, MIN, ORDER, GAP, avail);
    }

    private static final String[] EN = {"← Classes", "Recommended build", "Open crates (20)", "Other class", "Ready"};
    private static final String[] EN_SHORT = {"← Classes", "Recommended", "Crates (20)", "Other class", "Ready"};
    private static final String[] DE = {"← Klassen", "Empfohlener Aufbau", "Kisten öffnen (20)", "Andere Klasse", "✔ Bereit"};
    private static final String[] DE_SHORT = {"← Klassen", "Empfohlen", "Kisten (20)", "Andere Klasse", "✔ Bereit"};

    /** Window 427 px wide (GUI 2 at 854x480, GUI 3 at 1280x720): W 411, row from left+8 to left+W-66. */
    private static final int AVAIL_427 = 411 - 74;
    /** Window 284 px wide (GUI 3 at 854x480): W 280. */
    private static final int AVAIL_284 = 280 - 74;

    @Test
    void funFooterFitsWithoutSmallerTextInEnglishAndGerman() {
        for (String[][] lang : new String[][][] {{EN, EN_SHORT}, {DE, DE_SHORT}}) {
            UiFit.Row row = fun(lang[0], lang[1], AVAIL_427);
            assertTrue(row.total(GAP) <= AVAIL_427, "row " + row.total(GAP) + " > " + AVAIL_427);
            for (int i = 0; i < EN.length; i++) {
                String shown = row.shortUsed()[i] ? lang[1][i] : lang[0][i];
                assertTrue(row.widths()[i] >= w(shown) + UiFit.PAD_TIGHT, "label " + shown + " whole at full size");
            }
        }
    }

    @Test
    void tinyWindowStillNoOverlap() {
        for (String[][] lang : new String[][][] {{EN, EN_SHORT}, {DE, DE_SHORT}}) {
            UiFit.Row row = fun(lang[0], lang[1], AVAIL_284);
            assertTrue(row.total(GAP) <= AVAIL_284, "row " + row.total(GAP) + " > " + AVAIL_284);
            for (int width : row.widths()) {
                assertTrue(width >= UiFit.PAD_TIGHT, "every button keeps a width (" + width + ")");
            }
        }
    }

    @Test
    void roomyRowKeepsTheOldWidths() {
        UiFit.Row row = fun(EN, EN_SHORT, 600);
        assertArrayEquals(new int[] {Math.max(60, w(EN[0]) + 12), w(EN[1]) + 12, w(EN[2]) + 12, Math.max(70, w(EN[3]) + 12), 90},
                row.widths());
        for (boolean b : row.shortUsed()) {
            assertFalse(b);
        }
    }

    @Test
    void shortLabelsInTheGivenOrder() {
        // Full texts need 51+93+86+59+30 + 5*12 = 379 of 364: the crates go short first (the gold button's short
        // label is as long), the recommended build keeps its words.
        UiFit.Row row = fun(EN, EN_SHORT, 380);
        assertTrue(row.shortUsed()[2], "crates short");
        assertFalse(row.shortUsed()[1], "recommended build keeps its words");
        assertFalse(row.shortUsed()[3], "other class keeps its words");
        assertTrue(row.total(GAP) <= 380);
    }

    @Test
    void chapterLineNeverOverTheLevel() {
        // Big window: level at 1.5; compact (GUI 3 at 1280x720, card 187 wide): level at 1.
        int big = (int) Math.ceil(w("Level 20") * 1.5);
        int level = w("Level 20");
        assertEquals(UiFit.Place.BESIDE, UiFit.beside(big, w("Chapter 1 · Night 3"), 264));
        assertEquals(UiFit.Place.BESIDE_SMALL, UiFit.beside(level, w("Chapter 1 · Night 20 · Finale"), 171));
        assertEquals(UiFit.Place.BELOW, UiFit.beside(level, w("Kapitel 1 · Nacht 20 · Finale  ★5"), 160));
        for (int row = 60; row <= 400; row += 7) {
            for (int text = 10; text <= 300; text += 11) {
                UiFit.Place p = UiFit.beside(level, text, row);
                if (p != UiFit.Place.BELOW) {
                    float s = UiFit.scaleFor(text, row - level - 6);
                    assertTrue(Math.ceil(text * s) <= row - level - 6 + 1e-3, "beside: room " + (row - level - 6) + " text " + text);
                }
            }
        }
    }
}
