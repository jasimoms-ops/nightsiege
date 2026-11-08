package com.mki.nightsiege.client;

import net.minecraft.client.KeyMapping;

/**
 * Tor 3 (08.11.): the one place the HUD and the tooltips ask which key an ability sits on. They show
 * what the player has bound (Options → Controls), never a fixed letter. Tor 3's new key layout
 * (tor3/steuerung: everything on Q/F/Y, the ult on X) only has to change {@link #mapping}.
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
            case SLOT -> GainClient.slotKey(index);
            case GAIN -> GainClient.gainKey(index);
        };
    }

    /** The bound key's name ("Q"), or null when unbound or unknown. */
    public static String labelFor(Ability ability, int index) {
        return labelFor(mapping(ability, index));
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

    /** True for the crate gains' keys (their tooltips show no key: the gain keys are going, tor3/gewinne). */
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
