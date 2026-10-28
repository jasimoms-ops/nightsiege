package com.mki.nightsiege.core;

/**
 * Kit inventory (Simon, 28 October 2026): while the kit is worn and the inventory (E) is open, the
 * 2x2 crafting grid and the recipe book are hidden; the crest and the kit's values take their place.
 * Set by the client only (a dedicated server never sees it true).
 */
public final class KitView {

    /** True while the client shows the kit inventory. */
    public static volatile boolean hideCrafting;

    private KitView() {
    }
}
