package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Klaffende Wunden, the anti-heal of League of Legends (Simon, 25 October 2026): some
 * monsters (spiders and witches by default, config antiHealMobs) leave wounds from
 * level 5 on. While a player is wounded every heal is halved: life steal, potions,
 * regeneration, healers, natural regeneration. Each hit renews the wounds.
 */
public final class Wounds {

    private static final Map<UUID, Long> UNTIL = new HashMap<>();
    private static final DustParticleOptions BLOOD = new DustParticleOptions(0x5A0A0A, 1.0f);

    private Wounds() {
    }

    /** A monster hit a player: wounds if it is one of the anti-heal monsters and the night is high enough. */
    public static void afterDamage(LivingEntity victim, DamageSource source, float taken) {
        if (!(victim instanceof ServerPlayer player) || taken <= 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity attacker = source.getEntity();
        SiegeManager m = SiegeManager.get();
        if (attacker == null || m == null || !m.isActive() || Companions.isCompanion(attacker)) {
            return;
        }
        SiegeConfig cfg = m.config();
        if (cfg.antiHealShare <= 0 || m.currentLevel() < cfg.antiHealFromLevel || cfg.antiHealMobs == null) {
            return;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString();
        if (!cfg.antiHealMobs.contains(id)) {
            return;
        }
        long now = level.getGameTime();
        Long before = UNTIL.put(player.getUUID(), now + Math.round(cfg.antiHealSeconds * 20.0));
        if (before == null || before < now) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.wounded",
                    Math.round(cfg.antiHealShare * 100), Math.round(cfg.antiHealSeconds)).withStyle(ChatFormatting.DARK_RED));
        }
        level.sendParticles(BLOOD, player.getX(), player.getY(0.6), player.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
    }

    /** The heal a player really gets (called for every heal). */
    public static float heal(LivingEntity entity, float amount) {
        if (amount <= 0 || !(entity instanceof ServerPlayer player) || UNTIL.isEmpty()) {
            return amount;
        }
        Long until = UNTIL.get(player.getUUID());
        if (until == null) {
            return amount;
        }
        if (player.level().getGameTime() > until) {
            UNTIL.remove(player.getUUID());
            return amount;
        }
        SiegeManager m = SiegeManager.get();
        double share = m == null ? 0.5 : m.config().antiHealShare;
        return (float) (amount * Math.max(0.0, 1.0 - share));
    }

    /** True while the player carries wounds (for the tests). */
    public static boolean wounded(ServerPlayer player) {
        Long until = UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    /** The night ended: no wounds carry over. */
    public static void clear() {
        UNTIL.clear();
    }
}
