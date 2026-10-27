package com.mki.nightsiege.client;

import com.mki.nightsiege.mixin.client.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * The three ability keys (Simon, 27 October 2026): Fähigkeit 1 (Q, the ultimate), Fähigkeit 2
 * (F, the orders and the second ultimate, formerly X) and Fähigkeit 3 (Y, quiver, call and spell
 * pick, formerly G). While the server says they are Night Siege's (in the night, or with a class
 * item in hand) a press on their key reaches only them: vanilla's "drop item" and "swap hands"
 * and any other mapping on the same key wait until the morning.
 */
public final class NightKeys {

    private static volatile boolean owned;

    private NightKeys() {
    }

    /** From the server once a second: whether the ability keys are Night Siege's alone right now. */
    public static void setOwned(boolean value) {
        owned = value;
    }

    public static boolean owned() {
        return owned;
    }

    private static List<KeyMapping> abilities() {
        List<KeyMapping> out = new ArrayList<>(3);
        for (KeyMapping m : new KeyMapping[] {NightSiegeClient.ultimateKey(), GainClient.secondUltKey(), GainClient.quiverKey()}) {
            if (m != null && !m.isUnbound()) {
                out.add(m);
            }
        }
        return out;
    }

    /** A key press: true when it went to an ability alone. */
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

    /** A key going down or up: true when only the abilities see it. */
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
