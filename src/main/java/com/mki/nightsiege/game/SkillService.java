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
                instance.setBaseValue(effect.playerBase() + tree.totalBonus(effect, skills));
            }
        }
        capHealth(player);
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
            default -> null;
        };
    }
}
