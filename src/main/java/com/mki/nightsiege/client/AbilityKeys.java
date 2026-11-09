package com.mki.nightsiege.client;

import net.minecraft.client.KeyMapping;

/**
 * Tor 3 (08.11.): the one place the HUD and the tooltips ask which key an ability sits on. They show
 * what the player has bound (Options → Controls), never a fixed letter. Tor 3's new key layout
 * Tor 3.1 (tor3/steuerung): which ability sits on Q, F or Y comes only from core.AbilityKeys via
 * UltState.Slot.key ({@link #forSlot}); this class is the thin client shell around it.
 */
public final class AbilityKeys {

    /** The kinds of ability fields; {@code SLOT} and {@code GAIN} take an index. */
    public enum Ability { ULT, ABILITY2, ULT2, SLOT, GAIN }

    private AbilityKeys() {
    }

    /** The key mapping of an ability, or null if there is none. */
    public static KeyMapping mapping(Ability ability, int index) {
        return switch (ability) {
            case ULT -> NightSiegeClient.ultimateKey();
            case ABILITY2 -> GainClient.orderKey();
            case ULT2 -> GainClient.secondUltKey();
            case SLOT -> GainClient.slotKey(index);   // index = place 0-3 (Q, E, F, C), see forSlot
            case GAIN -> GainClient.gainKey(index);
        };
    }

    /** The bound key's name ("Q"), or null when unbound or unknown. */
    public static String labelFor(Ability ability, int index) {
        return labelFor(mapping(ability, index));
    }

    /** The key of a box of the bar (Tor 3.1: from UltState.Slot.key, the server's core.AbilityKeys). */
    public static KeyMapping forSlot(com.mki.nightsiege.core.UltState.Slot slot) {
        return GainClient.keyOf(slot);
    }

    /** The full key text of a HUD box, "Shift + X" for the second ult and a fourth ability (Tor 3.1). */
    static String labelFor(UltHud.Cell cell) {
        String key = labelFor(cell.key());
        if (key == null || !cell.sneak()) {
            return key;
        }
        return net.minecraft.client.Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage().getString() + " + " + key;
    }

    /** The bound key's name of a mapping, or null when unbound or unknown. */
    public static String labelFor(KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) {
            return null;
        }
        String text = mapping.getTranslatedKeyMessage().getString();
        return text == null || text.isBlank() ? null : text;
    }

    /** The same, cut to three letters for a box ("?" when unbound). */
    public static String shortLabel(KeyMapping mapping) {
        String text = labelFor(mapping);
        if (text == null) {
            return "?";
        }
        return text.length() > 3 ? text.substring(0, 3) : text;
    }

    /** True for the crate gains' keys 1, 2, 3 (Tor 3.2b). */
    public static boolean isGainKey(KeyMapping mapping) {
        if (mapping == null) {
            return false;
        }
        for (int i = 0; i < GainClient.GAIN_KEYS; i++) {
            if (mapping == GainClient.gainKey(i)) {
                return true;
            }
        }
        return false;
    }
}
