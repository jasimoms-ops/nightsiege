package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SkillState;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Writes the learned skills onto the player's attributes.
 *
 * <p>Every value is recomputed from Minecraft's own default plus the bonus, so
 * applying it twice changes nothing and the numbers can never creep upwards.</p>
 */
public final class SkillService {

    private SkillService() {
    }

    public static void apply(ServerPlayer player, SkillState skills, SkillTree tree) {
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
            if (instance == null) {
                continue;
            }
            instance.setBaseValue(effect.playerBase() + tree.totalBonus(effect, skills));
        }
        // Raising max health does not fill the new hearts, lowering it can leave
        // the player above the cap.
        float max = player.getMaxHealth();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    /**
     * Puts every player attribute back to Minecraft's default. Called when a
     * siege ends, so the skills only ever apply during the night itself.
     */
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
            case WAVE_DELAY, SPAWN_DISTANCE, ARROW_DOUBLE, ARROW_SPEED -> null;
        };
    }
}
