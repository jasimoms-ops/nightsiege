package com.mki.nightsiege.client;

import com.mki.nightsiege.mixin.client.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * The night keys (Tor 3.2b, Chef 09.11. 01:38): the ability places Q, E, F, C, the ult X, the second
 * ult R, the gains 1, 2, 3 and the inventory I. While the server says they are Night Siege's (in the
 * night, in a fight, with the kit on) a press on their key reaches only them: not vanilla (drop item,
 * swap hands, inventory on E, the hotbar on 1-3) and not another mod's mapping on the same key
 * (Just Zoom on C, for example). By day E and 1-3 are vanilla's again. Simple Voice Chat keeps its
 * own keys (N, M, V, G, H), none of which is a night key.
 */
public final class NightKeys {

    private static volatile boolean owned;

    private NightKeys() {
    }

    /** From the server once a second: whether the night keys are Night Siege's alone right now. */
    public static void setOwned(boolean value) {
        owned = value;
    }

    public static boolean owned() {
        return owned;
    }

    /** Every night key that is bound. */
    static List<KeyMapping> abilities() {
        List<KeyMapping> out = new ArrayList<>(13);
        add(out, NightSiegeClient.ultimateKey());
        for (int i = 0; i < GainClient.SLOTS; i++) {
            add(out, GainClient.slotKey(i));
        }
        add(out, GainClient.secondUltKey());
        for (int i = 0; i < GainClient.GAIN_KEYS; i++) {
            add(out, GainClient.gainKey(i));
        }
        add(out, GainClient.inventoryKey());
        return out;
    }

    private static void add(List<KeyMapping> out, KeyMapping m) {
        if (m != null && !m.isUnbound()) {
            out.add(m);
        }
    }

    /** A key press: true when it went to a night key alone. */
    public static boolean click(InputConstants.Key key) {
        if (!owned || key == null) {
            return false;
        }
        boolean mine = false;
        for (KeyMapping m : abilities()) {
            if (m.matches(key)) {
                KeyMappingAccessor counter = (KeyMappingAccessor) m;
                counter.nightsiege$setClickCount(counter.nightsiege$clickCount() + 1);
                mine = true;
            }
        }
        return mine;
    }

    /** A key going down or up: true when no other mapping on it sees it. */
    public static boolean set(InputConstants.Key key, boolean down) {
        if (!owned || key == null) {
            return false;
        }
        boolean mine = false;
        for (KeyMapping m : abilities()) {
            if (m.matches(key)) {
                m.setDown(down);
                mine = true;
            }
        }
        return mine;
    }
}
