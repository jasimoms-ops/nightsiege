package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
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
                if (effect == SkillEffect.ATTACK_SPEED) {
                    bonus += weaponPathSpeed(skills, tree);
                }
                instance.setBaseValue(effect.playerBase() + bonus);
            }
        }
        capHealth(player);
    }

    /**
     * Melee paths swing their weapon faster: axe, sword and spear feel slow in
     * Minecraft. Grows with the first node of the path.
     */
    private static double weaponPathSpeed(PlayerSkills skills, SkillTree tree) {
        String path = skills.pathTaken(tree);
        String first;
        double full;
        switch (path) {
            case SkillTree.PATH_AXE -> { first = "sunder"; full = 0.6; }
            case SkillTree.PATH_BLADE -> { first = "keen_edge"; full = 0.4; }
            case SkillTree.PATH_BERSERKER -> { first = "bloodlust"; full = 0.3; }
            case SkillTree.PATH_SPEAR -> { first = "long_reach"; full = 0.5; }
            default -> { return 0.0; }
        }
        com.mki.nightsiege.core.SkillNode node = tree.get(first);
        if (node == null) {
            return 0.0;
        }
        return full * Math.min(1.0, skills.levelOf(first) / (double) node.maxLevel());
    }

    /** Puts every attribute back to Minecraft's default. */
    public static void clear(ServerPlayer player) {
        if (player == null) {
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
