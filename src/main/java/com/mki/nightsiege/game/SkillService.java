package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Writes one player's own skills onto their attributes.
 *
 * <p>Every value is recomputed from Minecraft's own default plus the bonus, so
 * applying it twice changes nothing and the numbers can never creep upwards.
 * Skills only hold during a siege, so {@link #clear} puts everything back.</p>
 */
public final class SkillService {

    private SkillService() {
    }

    public static void apply(ServerPlayer player, PlayerSkills skills, SkillTree tree) {
        if (player == null || skills == null || tree == null) {
            return;
        }
        for (SkillEffect effect : SkillEffect.values()) {
            if (effect.target() != SkillEffect.Target.PLAYER) {
                continue;
            }
            Holder<Attribute> attribute = attributeOf(effect);
            if (attribute == null) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                double bonus = tree.totalBonus(effect, skills);
                bonus += keystone(effect, skills);
                if (effect == SkillEffect.MAX_HEALTH) {
                    bonus += subclassHealth(tree, skills);
                }
                instance.setBaseValue(effect.playerBase() + bonus);
            }
        }
        capHealth(player);
    }

    /**
     * Simon 26.10.: life of the subclass on top of the skills: the axe fighter 10 less, the
     * paladin 5 less, every archer subclass 4 more (two hearts). The number sits on the subclass
     * node as SkillValue "hp".
     */
    static double subclassHealth(SkillTree tree, PlayerSkills skills) {
        String node = switch (tree.subclassOf(skills)) {
            case SkillTree.SUB_AXE -> "heavy_blow";
            case SkillTree.SUB_PALADIN -> "smite";
            case SkillTree.SUB_SWIFT -> "swift_draw";
            case SkillTree.SUB_HEAVY -> "heavy_draw";
            case SkillTree.SUB_BOLTER -> "shrapnel";
            case SkillTree.SUB_TRAPPER -> "a_netbolt";
            default -> null;
        };
        return node == null ? 0.0 : SkillValues.get(node, "hp", 0.0);
    }

    /** Keystones change several values at once. */
    private static double keystone(SkillEffect effect, PlayerSkills skills) {
        double v = 0.0;
        switch (effect) {
            case MAX_HEALTH -> {
                if (SkillTree.has(skills, "w_glass_cannon")) v -= SkillValues.get("w_glass_cannon", "hp", 8.0);
                if (SkillTree.has(skills, "w_colossus")) v += SkillValues.get("w_colossus", "hp", 12.0);
                if (SkillTree.has(skills, "w_martyr")) v -= SkillValues.get("w_martyr", "hp", 4.0);
                if (SkillTree.has(skills, "a_deadeye")) v -= SkillValues.get("a_deadeye", "health", 8.0);
                if (SkillTree.has(skills, "a_watchful")) v -= SkillValues.get("a_watchful", "health", 4.0);
                if (SkillTree.has(skills, "g_living_wall")) v += SkillValues.get("g_living_wall", "hp", 12.0);
                if (SkillTree.has(skills, "g_bladeshield")) v -= SkillValues.get("g_bladeshield", "hp", 8.0);
                if (SkillTree.has(skills, "g_patron")) v -= SkillValues.get("g_patron", "hp", 4.0);
                if (SkillTree.has(skills, "s_beastlord")) v -= SkillValues.get("s_beastlord", "cost", 6.0);
                if (SkillTree.has(skills, "d_lich")) v -= SkillValues.get("d_lich", "cost", 8.0);
            }
            case MOVEMENT_SPEED -> {
                // The slow is a share of the base walking speed (0.1).
                if (SkillTree.has(skills, "w_unstoppable")) v -= 0.1 * SkillValues.get("w_unstoppable", "slow", 0.10);
                if (SkillTree.has(skills, "w_colossus")) v -= 0.1 * SkillValues.get("w_colossus", "slow", 0.10);
                if (SkillTree.has(skills, "a_skirmisher")) v += 0.1 * SkillValues.get("a_skirmisher", "speed", 0.15);
                if (SkillTree.has(skills, "a_siegeengine")) v -= 0.1 * SkillValues.get("a_siegeengine", "speed", 0.15);
                if (SkillTree.has(skills, "g_living_wall")) v -= 0.01;
            }
            case KNOCKBACK_RESIST -> {
                if (SkillTree.has(skills, "w_unstoppable")) v += 1.0;
            }
            default -> {
            }
        }
        return v;
    }

    /** Puts every attribute back to Minecraft's default. */
    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Combo.clear(player);
        for (SkillEffect effect : SkillEffect.values()) {
            if (effect.target() != SkillEffect.Target.PLAYER) {
                continue;
            }
            Holder<Attribute> attribute = attributeOf(effect);
            if (attribute == null) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(effect.playerBase());
            }
        }
        capHealth(player);
    }

    /** Lowering max health can leave a player above the new cap. */
    private static void capHealth(ServerPlayer player) {
        float max = player.getMaxHealth();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    private static Holder<Attribute> attributeOf(SkillEffect effect) {
        return switch (effect) {
            case MAX_HEALTH -> Attributes.MAX_HEALTH;
            case ATTACK_DAMAGE -> Attributes.ATTACK_DAMAGE;
            case ATTACK_SPEED -> Attributes.ATTACK_SPEED;
            case ARMOR -> Attributes.ARMOR;
            case MOVEMENT_SPEED -> Attributes.MOVEMENT_SPEED;
            case KNOCKBACK_RESIST -> Attributes.KNOCKBACK_RESISTANCE;
            case REACH -> Attributes.ENTITY_INTERACTION_RANGE;
            default -> null;
        };
    }
}
