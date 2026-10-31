package com.mki.nightsiege.client;

import com.mki.nightsiege.mixin.client.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * The ability keys (Simon, 27 October 2026): Fähigkeit 1 (Q, the ultimate), Fähigkeit 2 (F, the
 * orders), the second ultimate (X) and the six keys of the ability bar (R, G, Y, C, V, B: every
 * spell, potion, healing ability or arrow kind on its own key, instead of the wheel). While the
 * server says they are Night Siege's (in the night, or with a class item in hand) a press on their
 * key reaches only them and other mods' mappings: vanilla's "drop item" and "swap hands" on the same
 * key wait until the morning (31.10.: Simple Voice Chat's N, M, V, G work in the night again).
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
        List<KeyMapping> out = new ArrayList<>(13);
        for (KeyMapping m : new KeyMapping[] {NightSiegeClient.ultimateKey(), GainClient.orderKey(), GainClient.secondUltKey()}) {
            if (m != null && !m.isUnbound()) {
                out.add(m);
            }
        }
        for (int i = 0; i < GainClient.SLOTS; i++) {
            KeyMapping m = GainClient.slotKey(i);
            if (m != null && !m.isUnbound()) {
                out.add(m);
            }
        }
        // The gain keys (balance update 2): in the kit they win over the map, the night vision and the like.
        for (int i = 0; i < GainClient.GAIN_KEYS; i++) {
            KeyMapping m = GainClient.gainKey(i);
            if (m != null && !m.isUnbound()) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * The mappings of vanilla Minecraft itself (the fields of Options, the hotbar keys among them):
     * only these give way to a night ability on the same key. Another mod's mapping (Simple Voice
     * Chat: N, M, V, G by default) still gets the press (Simon 31.10.: survival safety).
     */
    private static java.util.Set<KeyMapping> vanilla;

    private static java.util.Set<KeyMapping> vanilla() {
        java.util.Set<KeyMapping> known = vanilla;
        if (known != null) {
            return known;
        }
        java.util.Set<KeyMapping> out = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        net.minecraft.client.Options options = net.minecraft.client.Minecraft.getInstance().options;
        if (options == null) {
            return out;   // too early: asked again next time
        }
        for (java.lang.reflect.Field f : net.minecraft.client.Options.class.getFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Object value = f.get(options);
                if (value instanceof KeyMapping m) {
                    out.add(m);
                } else if (value instanceof KeyMapping[] all && all != options.keyMappings) {
                    java.util.Collections.addAll(out, all);   // the hotbar keys (keyMappings holds every mod's too)
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                // a field we cannot read is no mapping of ours to hold back
            }
        }
        vanilla = out;
        return out;
    }

    /** True for a mapping of another mod: it gets the press although a night ability has the key. */
    private static boolean otherMod(KeyMapping m, List<KeyMapping> mine) {
        for (KeyMapping own : mine) {
            if (own == m) {
                return false;
            }
        }
        String name = m.getName();
        return (name == null || !name.startsWith("key.nightsiege.")) && !vanilla().contains(m);   // the guide key and the like stay held back
    }

    /** The mappings bound to this key, as vanilla's click and set see them. */
    private static List<KeyMapping> boundTo(InputConstants.Key key) {
        try {
            java.util.Map<InputConstants.Key, List<KeyMapping>> map = KeyMappingAccessor.nightsiege$map();
            List<KeyMapping> list = map == null ? null : map.get(key);
            return list == null ? List.of() : List.copyOf(list);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** A key press: true when it went to an ability alone (and to other mods' mappings on the key). */
    public static boolean click(InputConstants.Key key) {
        if (!owned || key == null) {
            return false;
        }
        boolean mine = false;
        List<KeyMapping> own = abilities();
        for (KeyMapping m : own) {
            if (m.matches(key)) {
                KeyMappingAccessor counter = (KeyMappingAccessor) m;
                counter.nightsiege$setClickCount(counter.nightsiege$clickCount() + 1);
                mine = true;
            }
        }
        if (mine) {
            for (KeyMapping m : boundTo(key)) {
                if (otherMod(m, own)) {
                    KeyMappingAccessor counter = (KeyMappingAccessor) m;
                    counter.nightsiege$setClickCount(counter.nightsiege$clickCount() + 1);
                }
            }
        }
        return mine;
    }

    /** A key going down or up: true when vanilla's mappings on it do not see it (other mods' do). */
    public static boolean set(InputConstants.Key key, boolean down) {
        if (!owned || key == null) {
            return false;
        }
        boolean mine = false;
        List<KeyMapping> own = abilities();
        for (KeyMapping m : own) {
            if (m.matches(key)) {
                m.setDown(down);
                mine = true;
            }
        }
        if (mine) {
            for (KeyMapping m : boundTo(key)) {
                if (otherMod(m, own)) {
                    m.setDown(down);
                }
            }
        }
        return mine;
    }
}
