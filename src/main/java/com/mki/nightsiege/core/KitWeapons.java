package com.mki.nightsiege.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The kit weapon of every subclass (Simon, 27 October 2026: "for every subclass a kit weapon,
 * like the armour"). Only the look and the name change: the kit sets the item model component
 * to {@code nightsiege:kit/<key>} on its main weapon; damage, enchantments and material stay
 * those of the kit. The textures come from tools/weapons/designs.py.
 */
public final class KitWeapons {

    /** What kind of kit item carries the look. */
    public enum Kind { SWORD, AXE, BOW, CROSSBOW, STAFF }

    /** The look of one subclass: resource key and the kit item it goes on. */
    public record Look(String subclass, String key, Kind kind) {
        public String itemModel() {
            return "nightsiege:kit/" + key;
        }

        public String nameKey() {
            return "nightsiege.kitweapon." + key;
        }
    }

    private static final Map<String, Look> LOOKS = new LinkedHashMap<>();

    static {
        add(SkillTree.SUB_BLADEMASTER, "blademaster_saber", Kind.SWORD);
        add(SkillTree.SUB_BERSERKER, "berserker_bloodblade", Kind.SWORD);
        add(SkillTree.SUB_AXE, "axe_bearded", Kind.AXE);
        add(SkillTree.SUB_PALADIN, "paladin_sunaxe", Kind.AXE);
        add(SkillTree.SUB_SWIFT, "swift_huntbow", Kind.BOW);
        add(SkillTree.SUB_HEAVY, "heavy_siegebow", Kind.BOW);
        add(SkillTree.SUB_BOLTER, "bolter_launcher", Kind.CROSSBOW);
        add(SkillTree.SUB_TRAPPER, "trapper_netbow", Kind.CROSSBOW);
        add(SkillTree.SUB_SHIELDFIGHTER, "shieldfighter_crestsword", Kind.SWORD);
        add(SkillTree.SUB_BULWARK, "bulwark_wallbreaker", Kind.SWORD);
        add(SkillTree.SUB_MEDIC, "medic_lancet", Kind.SWORD);
        add(SkillTree.SUB_WARDEN, "warden_wardblade", Kind.SWORD);
        add(SkillTree.SUB_GUNNER, "gunner_wrenchblade", Kind.SWORD);
        add(SkillTree.SUB_ELECTRICIAN, "electrician_coilblade", Kind.SWORD);
        add(SkillTree.SUB_DEMOLITION, "demolition_fusemachete", Kind.SWORD);
        add(SkillTree.SUB_ALCHEMIST, "alchemist_vialknife", Kind.SWORD);
        add(SkillTree.SUB_TAMER, "tamer_packstaff", Kind.STAFF);
        add(SkillTree.SUB_GOLEMSMITH, "golemsmith_forgestaff", Kind.STAFF);
        add(SkillTree.SUB_NECROMANCER, "necromancer_bonestaff", Kind.STAFF);
        add(SkillTree.SUB_SOULCATCHER, "soulcatcher_lanternstaff", Kind.STAFF);
    }

    private KitWeapons() {
    }

    private static void add(String subclass, String key, Kind kind) {
        LOOKS.put(subclass, new Look(subclass, key, kind));
    }

    /** The look of a subclass, or null (no subclass yet). */
    public static Look of(String subclass) {
        return subclass == null ? null : LOOKS.get(subclass);
    }

    public static Iterable<Look> all() {
        return LOOKS.values();
    }

    /** The texture files a look needs (without .png): bows and crossbows have their drawing pictures. */
    public static String[] textures(Look look) {
        String k = look.key();
        return switch (look.kind()) {
            case BOW -> new String[] {k, k + "_pulling_0", k + "_pulling_1", k + "_pulling_2"};
            case CROSSBOW -> new String[] {k + "_standby", k + "_pulling_0", k + "_pulling_1", k + "_pulling_2",
                    k + "_arrow", k + "_firework"};
            default -> new String[] {k};
        };
    }
}
