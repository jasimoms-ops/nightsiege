package com.mki.nightsiege.core;

/**
 * Z4 (Simon 12.11.: "alle Animationen aussagekräftiger und auffälliger, größer"): the colours of the
 * ability effects. The base is the class colour (warrior red, archer green, guardian blue, engineer
 * orange, summoner turquoise), the subclass sets the accent. Plain RGB, no alpha.
 */
public final class FxColors {

    private FxColors() {
    }

    /** The class colour; a light grey without a class. */
    public static int base(SkillClass clazz) {
        if (clazz == null) {
            return 0xD8D8D8;
        }
        return switch (clazz) {
            case WARRIOR -> 0xE0302A;
            case ARCHER -> 0x3FC24A;
            case GUARDIAN -> 0x3A7BF0;
            case ENGINEER -> 0xF5891F;
            case SUMMONER -> 0x22CFC4;
            default -> 0xD8D8D8;
        };
    }

    /** The subclass accent; the class colour lightened for an unknown or missing subclass. */
    public static int accent(SkillClass clazz, String sub) {
        if (sub != null) {
            switch (sub) {
                case SkillTree.SUB_SWIFT: return 0xC6FF7A;
                case SkillTree.SUB_HEAVY: return 0x7E9B2C;
                case SkillTree.SUB_BOLTER: return 0xD0D6DC;
                case SkillTree.SUB_TRAPPER: return 0x9A6A35;
                case SkillTree.SUB_BLADEMASTER: return 0xF4F4FF;
                case SkillTree.SUB_BERSERKER: return 0x7A0000;
                case SkillTree.SUB_AXE: return 0xFFB22E;
                case SkillTree.SUB_PALADIN: return 0xFFE36B;
                case SkillTree.SUB_SHIELDFIGHTER: return 0xC4CEDF;
                case SkillTree.SUB_BULWARK: return 0x5C6577;
                case SkillTree.SUB_MEDIC: return 0x8CFFB8;
                case SkillTree.SUB_WARDEN: return 0xB57CFF;
                case SkillTree.SUB_GUNNER: return 0xFFD84A;
                case SkillTree.SUB_ELECTRICIAN: return 0xA8EEFF;
                case SkillTree.SUB_DEMOLITION: return 0xFF4A1A;
                case SkillTree.SUB_ALCHEMIST: return 0xA6E22E;
                case SkillTree.SUB_TAMER: return 0x9CCB5E;
                case SkillTree.SUB_GOLEMSMITH: return 0xB0A592;
                case SkillTree.SUB_NECROMANCER: return 0x7B1FC4;
                case SkillTree.SUB_SOULCATCHER: return 0xCFF4FF;
                default: break;
            }
        }
        return lighten(base(clazz));
    }

    /** Half way to white. */
    public static int lighten(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return ((r + 255) / 2) << 16 | ((g + 255) / 2) << 8 | (b + 255) / 2;
    }
}
