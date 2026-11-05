package com.mki.nightsiege.game;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Betäubung (Simon 05.11.: "ein Mob, der stunnen kann, als Konter gegen die Nahkampfklassen"; the
 * stun: "komplett starr"). A stunned player cannot move or jump (speed and jump strength to zero),
 * hits nothing, uses no item and fires no Night Siege key (abilities, ultimates, gain keys) until it
 * wears off. Stars over the head show it, the action bar says so.
 */
public final class Stuns {

    private static final Map<UUID, Long> UNTIL = new HashMap<>();
    private static final Identifier SLOW = Identifier.fromNamespaceAndPath("nightsiege", "stun_slow");
    private static final Identifier JUMP = Identifier.fromNamespaceAndPath("nightsiege", "stun_jump");

    private Stuns() {
    }

    /** Stuns the player for this many ticks (a running stun is only ever lengthened). */
    public static void stun(ServerPlayer player, int ticks) {
        if (player == null || ticks <= 0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        long until = player.level().getGameTime() + ticks;
        Long before = UNTIL.get(player.getUUID());
        if (before != null && before >= until) {
            return;
        }
        UNTIL.put(player.getUUID(), until);
        hold(player, true);
        player.stopUsingItem();
        player.sendOverlayMessage(Component.translatable("nightsiege.msg.stunned").withStyle(ChatFormatting.GOLD));
    }

    /** True while the player is stunned. */
    public static boolean stunned(Player player) {
        if (player == null) {
            return false;
        }
        Long until = UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    /** The Night Siege keys a stunned player cannot fire. */
    public static boolean blocks(String verb) {
        return switch (verb) {
            case "ult", "ult2", "ult2x", "staffhit", "quiver", "slot", "gain", "jump", "shield", "man" -> true;
            default -> false;
        };
    }

    /** Every tick: stars over the stunned, and the stun taken off when it is over. */
    public static void tick(MinecraftServer server) {
        if (UNTIL.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Long>> it = UNTIL.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            long now = player.level().getGameTime();
            if (now >= e.getValue() || !player.isAlive()) {
                hold(player, false);
                it.remove();
                continue;
            }
            if (now % 4 == 0 && player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + player.getBbHeight() + 0.3, player.getZ(),
                        3, 0.3, 0.05, 0.3, 0.02);
            }
        }
    }

    /** All stuns off (the night or the server ends). */
    public static void clear(MinecraftServer server) {
        if (server != null) {
            for (UUID id : UNTIL.keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    hold(player, false);
                }
            }
        }
        UNTIL.clear();
    }

    private static void hold(ServerPlayer player, boolean on) {
        modify(player.getAttribute(Attributes.MOVEMENT_SPEED), SLOW, on);
        modify(player.getAttribute(Attributes.JUMP_STRENGTH), JUMP, on);
    }

    private static void modify(AttributeInstance attribute, Identifier id, boolean on) {
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(id);
        if (on) {
            attribute.addTransientModifier(new AttributeModifier(id, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
