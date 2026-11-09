package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 3.2b: a click on a gain place in the inventory brings the next free gain onto it. */
class GainPlacesTest {

    private static final List<String> OWNED = List.of("a", "b", "c", "d", "e");

    @Test
    void clickTakesTheNextFreeGain() {
        List<String> order = List.of("a", "b", "c");
        assertEquals(List.of("a", "d", "c"), GainPlaces.next(order, OWNED, 1));
        assertEquals(List.of("d", "b", "c"), GainPlaces.next(order, OWNED, 0));
        // after the last one it starts at the front again, skipping the gains on other places
        assertEquals(List.of("a", "c", "b"), GainPlaces.next(List.of("a", "c", "e"), OWNED, 2));
        assertEquals(List.of("a", "b", "c"), GainPlaces.next(List.of("e", "b", "c"), OWNED, 0));
    }

    @Test
    void nothingToChange() {
        assertNull(GainPlaces.next(List.of("a", "b"), List.of("a", "b"), 0));   // every gain has a place
        assertNull(GainPlaces.next(List.of("a", "b"), OWNED, 2));               // no such place
        assertNull(GainPlaces.next(List.of("a"), OWNED, -1));
        assertNull(GainPlaces.next(null, OWNED, 0));
    }
}
