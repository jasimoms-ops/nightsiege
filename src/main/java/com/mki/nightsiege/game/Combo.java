package com.mki.nightsiege.game;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The blademaster's combo (Simon, 24 October 2026): with a sword in hand the attack cooldown
 * is nearly gone, like Minecraft's old combat, but at most {@link #HITS_PER_SECOND} full hits a
 * second, on whatever targets (the attack speed is set to exactly that); no sweep.
 * Only during a night (skills only hold then); the kit sword is iron at most.
 */
public final class Combo {

    private static final Identifier SPEED = ModContent.id("combo_speed");
    /** Full hits a second with the combo (Simon: four, whatever the targets). */
    public static final double HITS_PER_SECOND = 4.0;
    /** Players whose sword fights the old way right now (the sweep mixin asks). */
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private Combo() {
    }

    public static boolean active(Player player) {
        return player != null && ACTIVE.contains(player.getUUID());
    }

    /** Every few ticks for a defender: on with the skill and a sword in hand, off otherwise. */
    public static void tick(ServerPlayer player, boolean learned) {
        boolean on = learned && player.getMainHandItem().is(ItemTags.SWORDS);
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier have = speed.getModifier(SPEED);
        if (on) {
            // Exactly HITS_PER_SECOND, whatever the sword and the other bonuses give.
            double without = speed.getValue() - (have == null ? 0.0 : have.amount());
            double want = Math.max(0.0, HITS_PER_SECOND - without);
            if (have == null || Math.abs(have.amount() - want) > 1.0e-3) {
                if (have != null) {
                    speed.removeModifier(SPEED);
                }
                speed.addTransientModifier(new AttributeModifier(SPEED, want, AttributeModifier.Operation.ADD_VALUE));
            }
        } else if (have != null) {
            speed.removeModifier(SPEED);
        }
        if (on) {
            ACTIVE.add(player.getUUID());
        } else {
            ACTIVE.remove(player.getUUID());
        }
    }

    /** End of the night, a class change: back to the normal cooldown. */
    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null && speed.getModifier(SPEED) != null) {
            speed.removeModifier(SPEED);
        }
        ACTIVE.remove(player.getUUID());
    }
}
