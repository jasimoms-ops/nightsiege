package com.mki.nightsiege.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which key fires which ability, for every subclass (Tor 3.1, Simon 08.11.: "Nur Tasten"). The one
 * place the server (UltState.Slot.key), the HUD, the tooltips and the client's default keys read.
 * The right click is the weapon only.
 *
 * <p>Tor 3.2b (Simon 09.11., Chef 01:38): the abilities are named by their place, not by a letter.
 * Place 1 / 2 / 3 = Q / E / F, place 4 = C (the fourth ability of the alchemist and the necromancer,
 * the tanks' arrow kinds), X the ultimate, R the second ultimate of a crate, 1 / 2 / 3 the gains,
 * I the inventory in the night (E is an ability then). No "sneak + key" any more. All of them are
 * Night Siege's alone in the night; by day E and 1-3 are vanilla's again.
 *
 * <p>Ability ids are the ids of the ability bar ({@code UltState.Slot.id}) plus {@link #F_ACTION}
 * (the class's own field on place 2: arrow rain, warrior art, remote order, staff order) and {@link #ULT}.
 */
public final class AbilityKeys {

    /**
     * The keys, each with its default (a letter or digit: the client's KeyMapping default, so the
     * player can rebind it). The ability places come first, in their order.
     */
    public enum Key {
        ABILITY_1("Q"), ABILITY_2("E"), ABILITY_3("F"), ABILITY_4("C"), ULT("X"), ULT2("R"),
        GAIN_1("1"), GAIN_2("2"), GAIN_3("3"), INVENTORY("I");

        private final String letter;

        Key(String letter) {
            this.letter = letter;
        }

        /** The default key as printed on the keyboard ("Q", "1"). */
        public String letter() {
            return letter;
        }

        /** The default key's GLFW code: for A-Z and 0-9 it is the character itself. */
        public int glfw() {
            return letter.charAt(0);
        }

        /** True for the four ability places. */
        public boolean ability() {
            return ordinal() <= ABILITY_4.ordinal();
        }

        /** The place of an ability key, 0 for place 1; -1 for the other keys. */
        public int place() {
            return ability() ? ordinal() : -1;
        }
    }

    /** The ability places in order (place 1 to 4). */
    public static final List<Key> ABILITY_PLACES = List.of(Key.ABILITY_1, Key.ABILITY_2, Key.ABILITY_3, Key.ABILITY_4);
    /** The gain places 1 to 3. */
    public static final List<Key> GAIN_PLACES = List.of(Key.GAIN_1, Key.GAIN_2, Key.GAIN_3);

    public static final String ULT = "ult";
    /** The F field of the class (UltState.hasAb2). */
    public static final String F_ACTION = "f";
    /** The guardian's shield throw (was: block + left click). */
    public static final String SHIELD = "shield";
    /** The arrow kinds of the arrow gain. */
    public static final String QUIVER = "quiver";
    /** The summoner's call on the way of the light. */
    public static final String CALL = "call";

    /** One ability of a subclass and its key. */
    public record Entry(String id, Key key) {
    }

    private static final Map<String, List<Entry>> TABLE = new LinkedHashMap<>();

    static {
        // Places: 1 = Q, 2 = E, 3 = F, 4 = C (Tor 3.2b). Place 2 is the class's own field (F_ACTION).
        // archers: the arrow rain on place 2, the arrow kinds on 3, the heavy archer's blast jump on 1
        put(SkillTree.SUB_SWIFT, e(F_ACTION, Key.ABILITY_2), e("quiver", Key.ABILITY_3));
        put(SkillTree.SUB_HEAVY, e("escape", Key.ABILITY_1), e(F_ACTION, Key.ABILITY_2), e("quiver", Key.ABILITY_3));
        put(SkillTree.SUB_BOLTER, e(F_ACTION, Key.ABILITY_2), e("quiver", Key.ABILITY_3));
        put(SkillTree.SUB_TRAPPER, e(F_ACTION, Key.ABILITY_2), e("quiver", Key.ABILITY_3));
        // warriors: the dash on 1, the Kampfgeist art on 2
        for (String sub : List.of(SkillTree.SUB_BLADEMASTER, SkillTree.SUB_BERSERKER, SkillTree.SUB_AXE, SkillTree.SUB_PALADIN)) {
            put(sub, e("dash", Key.ABILITY_1), e(F_ACTION, Key.ABILITY_2), e("quiver", Key.ABILITY_3));
        }
        // guardians: no F field of their own any more, every healing on its key
        // Tor 4.2: the tanks' self heal on place 3; their arrows, rare for a tank, on place 4 (C, Tor 3.2b)
        put(SkillTree.SUB_SHIELDFIGHTER, e(SHIELD, Key.ABILITY_1), e("ward", Key.ABILITY_2), e(TankHeal.ID, Key.ABILITY_3), e("quiver", Key.ABILITY_4));
        put(SkillTree.SUB_BULWARK, e(SHIELD, Key.ABILITY_1), e("ward", Key.ABILITY_2), e(TankHeal.ID, Key.ABILITY_3), e("quiver", Key.ABILITY_4));
        put(SkillTree.SUB_MEDIC, e("hand", Key.ABILITY_1), e("selfheal", Key.ABILITY_2), e("quiver", Key.ABILITY_3));
        put(SkillTree.SUB_WARDEN, e("ward", Key.ABILITY_1), e("dome", Key.ABILITY_2), e("quiver", Key.ABILITY_3));
        // engineers: the blast jump on 1, the remote on 2; the alchemist's potions on 3, the acid on 4
        put(SkillTree.SUB_GUNNER, e("escape", Key.ABILITY_1), e(F_ACTION, Key.ABILITY_2));
        put(SkillTree.SUB_ELECTRICIAN, e("escape", Key.ABILITY_1), e(F_ACTION, Key.ABILITY_2));
        put(SkillTree.SUB_DEMOLITION, e("escape", Key.ABILITY_1), e(F_ACTION, Key.ABILITY_2));
        put(SkillTree.SUB_ALCHEMIST, e("escape", Key.ABILITY_1), e(F_ACTION, Key.ABILITY_2), e("brew", Key.ABILITY_3), e("acid", Key.ABILITY_4));
        // summoners: the order on 2; the call or the spells on 1, 3 and 4
        put(SkillTree.SUB_TAMER, e("call", Key.ABILITY_1), e(F_ACTION, Key.ABILITY_2), e("quiver", Key.ABILITY_3));
        put(SkillTree.SUB_GOLEMSMITH, e("call", Key.ABILITY_1), e(F_ACTION, Key.ABILITY_2), e("quiver", Key.ABILITY_3));
        put(SkillTree.SUB_NECROMANCER, e("raise", Key.ABILITY_1), e(F_ACTION, Key.ABILITY_2), e("wall", Key.ABILITY_3), e("curse", Key.ABILITY_4));
        put(SkillTree.SUB_SOULCATCHER, e("swarm", Key.ABILITY_1), e("fear", Key.ABILITY_2));
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
     * Tor 3.6: the slots the bar adds beside the class's own wheel (spells, potions, healings):
     * the arrow kinds with the arrow gain (beside a wheel only where the table gives them a key),
     * and the summoner's call when he has no spell wheel. No ability may lose its key to another.
     */
    public static List<String> barExtras(String subclass, boolean classWheel, boolean quiverGain, boolean summoner) {
        List<String> out = new java.util.ArrayList<>();
        if (quiverGain && (!classWheel || keyOf(subclass, QUIVER) != null)) {
            out.add(QUIVER);
        }
        if (summoner && !classWheel) {
            out.add(CALL);
        }
        return out;
    }

    /**
     * The key of an ability of this subclass: {@link Key#ULT} for the ultimate, the table's key, or
     * null when the subclass does not have it (the caller then shows and fires it on {@link Key#ABILITY_4}).
     */
    public static Key keyOf(String subclass, String abilityId) {
        if (ULT.equals(abilityId)) {
            return Key.ULT;
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
