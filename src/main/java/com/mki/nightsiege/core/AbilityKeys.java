package com.mki.nightsiege.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which key fires which ability, for every subclass (Tor 3.1, Simon 08.11.: "Nur Tasten"). The one
 * place the server (UltState.Slot.key), the HUD and the tooltips read: Q, F and Y for the abilities,
 * X for the ultimate, sneak + X for the second ultimate of a crate. The right click is the weapon only.
 *
 * <p>Ability ids are the ids of the ability bar ({@code UltState.Slot.id}) plus {@link #F_ACTION}
 * (the class's F field: arrow rain, warrior art, remote order, staff order) and {@link #ULT}.
 * {@link Key#SNEAK_Y} is sneak + Y: the fourth ability of the alchemist and the necromancer (Leiter 08.11.).
 */
public final class AbilityKeys {

    /** The keys; each has a KeyMapping of its own on the client, so the player can rebind it. */
    public enum Key { Q, F, Y, X, SNEAK_Y }

    public static final String ULT = "ult";
    /** The F field of the class (UltState.hasAb2). */
    public static final String F_ACTION = "f";
    /** The guardian's shield throw (was: block + left click). */
    public static final String SHIELD = "shield";

    /** One ability of a subclass and its key. */
    public record Entry(String id, Key key) {
    }

    private static final Map<String, List<Entry>> TABLE = new LinkedHashMap<>();

    static {
        // archers: the arrow rain on F, the arrow kinds on Y, the heavy archer's blast jump on Q
        put(SkillTree.SUB_SWIFT, e(F_ACTION, Key.F), e("quiver", Key.Y));
        put(SkillTree.SUB_HEAVY, e("escape", Key.Q), e(F_ACTION, Key.F), e("quiver", Key.Y));
        put(SkillTree.SUB_BOLTER, e(F_ACTION, Key.F), e("quiver", Key.Y));
        put(SkillTree.SUB_TRAPPER, e(F_ACTION, Key.F), e("quiver", Key.Y));
        // warriors: the dash on Q, the Kampfgeist art on F
        for (String sub : List.of(SkillTree.SUB_BLADEMASTER, SkillTree.SUB_BERSERKER, SkillTree.SUB_AXE, SkillTree.SUB_PALADIN)) {
            put(sub, e("dash", Key.Q), e(F_ACTION, Key.F), e("quiver", Key.Y));
        }
        // guardians: no F field of their own any more, every healing on its key
        put(SkillTree.SUB_SHIELDFIGHTER, e(SHIELD, Key.Q), e("ward", Key.F), e("quiver", Key.Y));
        put(SkillTree.SUB_BULWARK, e(SHIELD, Key.Q), e("ward", Key.F), e("quiver", Key.Y));
        put(SkillTree.SUB_MEDIC, e("hand", Key.Q), e("selfheal", Key.F), e("quiver", Key.Y));
        put(SkillTree.SUB_WARDEN, e("ward", Key.Q), e("dome", Key.F), e("quiver", Key.Y));
        // engineers: the blast jump on Q, the remote on F; the alchemist's potions on Y
        put(SkillTree.SUB_GUNNER, e("escape", Key.Q), e(F_ACTION, Key.F));
        put(SkillTree.SUB_ELECTRICIAN, e("escape", Key.Q), e(F_ACTION, Key.F));
        put(SkillTree.SUB_DEMOLITION, e("escape", Key.Q), e(F_ACTION, Key.F));
        put(SkillTree.SUB_ALCHEMIST, e("escape", Key.Q), e(F_ACTION, Key.F), e("brew", Key.Y), e("acid", Key.SNEAK_Y));
        // summoners: the order on F; the call or the spells on Q and Y
        put(SkillTree.SUB_TAMER, e("call", Key.Q), e(F_ACTION, Key.F), e("quiver", Key.Y));
        put(SkillTree.SUB_GOLEMSMITH, e("call", Key.Q), e(F_ACTION, Key.F), e("quiver", Key.Y));
        put(SkillTree.SUB_NECROMANCER, e("raise", Key.Q), e(F_ACTION, Key.F), e("wall", Key.Y), e("curse", Key.SNEAK_Y));
        put(SkillTree.SUB_SOULCATCHER, e("swarm", Key.Q), e("fear", Key.F));
    }

    private AbilityKeys() {
    }

    private static Entry e(String id, Key key) {
        return new Entry(id, key);
    }

    private static void put(String sub, Entry... entries) {
        TABLE.put(sub, List.of(entries));
    }

    /** Every subclass with its abilities (without the ultimate, which is X for all). */
    public static Map<String, List<Entry>> table() {
        return java.util.Collections.unmodifiableMap(TABLE);
    }

    /**
     * The key of an ability of this subclass: X for the ultimate, the table's key, or null when
     * the subclass does not have it (the caller then shows and fires it on {@link Key#SNEAK_Y}).
     */
    public static Key keyOf(String subclass, String abilityId) {
        if (ULT.equals(abilityId)) {
            return Key.X;
        }
        List<Entry> entries = subclass == null ? null : TABLE.get(subclass);
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry.id().equals(abilityId)) {
                    return entry.key();
                }
            }
        }
        return null;
    }
}
