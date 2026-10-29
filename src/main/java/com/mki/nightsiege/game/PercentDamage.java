package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * Damage by share of the max health (Simon, 28 October 2026: "Wir sollten mehr mit Max-Health-
 * Damage arbeiten für die Bosse wie für die Maschinen"). It comes on top of the usual hit as a
 * second hit of its own type {@code nightsiege:crushing}: armour and protection do not lessen
 * it, it gives no knock-back, a raised shield blocks it like any blow. The class factors and the
 * guardian's protections still count; the share grows a little with the level.
 */
public final class PercentDamage {

    /** The damage type of the share (data pack: tagged to pass armour and enchantments, no knock-back). */
    public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, ModContent.id("crushing"));
    /** The same share through a raised shield (the tank breakers, content update 29 October 2026). */
    public static final ResourceKey<DamageType> THROUGH = ResourceKey.create(Registries.DAMAGE_TYPE, ModContent.id("crushing_through"));

    private static final ThreadLocal<Boolean> IN_HIT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private PercentDamage() {
    }

    /** Factor on the share at a level: 1 + step × (level − 10), at least the minimum (level 6 ×0.8, 10 ×1, 20 ×1.5). */
    public static double levelFactor(int level, SiegeConfig cfg) {
        double step = cfg == null ? 0.05 : cfg.percentDamageLevelStep;
        double min = cfg == null ? 0.5 : cfg.percentDamageMin;
        int cap = cfg == null ? 20 : Math.max(10, cfg.softCapLevel);
        int capped = Math.min(Math.max(1, level), cap);
        double f = 1.0 + step * (capped - 10);
        // Above the soft cap it grows on slowly, like the monsters do.
        double above = cfg == null ? 0.25 : cfg.growthAboveSoftCap;
        f += step * above * Math.max(0, level - cap);
        return Math.max(min, f);
    }

    /** The damage a share of {@code percent} % comes to for this player at this level. */
    public static float amount(ServerPlayer player, double percent, int level, SiegeConfig cfg, double factor) {
        if (player == null || percent <= 0 || factor <= 0) {
            return 0f;
        }
        return (float) (player.getMaxHealth() * percent / 100.0 * levelFactor(level, cfg) * factor);
    }

    /**
     * Hits a player for {@code percent} % of his max health (times the level factor and the
     * config factor). {@code attacker} is the boss, the machine's hit box or the shooter; a
     * shield facing it blocks the hit. Returns the damage aimed (before shield and class factors).
     */
    public static float hit(ServerLevel level, ServerPlayer player, Entity attacker, Entity direct, double percent,
                            int siegeLevel, SiegeConfig cfg, double factor) {
        return deal(level, player, attacker, direct, percent, siegeLevel, cfg, factor, TYPE);
    }

    /** As {@link #hit}, but no shield stops it. */
    public static float hitThrough(ServerLevel level, ServerPlayer player, Entity attacker, Entity direct, double percent,
                                   int siegeLevel, SiegeConfig cfg, double factor) {
        return deal(level, player, attacker, direct, percent, siegeLevel, cfg, factor, THROUGH);
    }

    private static float deal(ServerLevel level, ServerPlayer player, Entity attacker, Entity direct, double percent,
                              int siegeLevel, SiegeConfig cfg, double factor, ResourceKey<DamageType> kind) {
        if (level == null || player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) {
            return 0f;
        }
        float amount = amount(player, percent, siegeLevel, cfg, factor);
        if (amount <= 0f) {
            return 0f;
        }
        DamageSource source = source(level, direct == null ? attacker : direct, attacker, kind);
        IN_HIT.set(Boolean.TRUE);
        try {
            player.invulnerableTime = 0;
            player.hurtServer(level, source, amount);
        } finally {
            IN_HIT.set(Boolean.FALSE);
        }
        return amount;
    }

    /**
     * Hits a player for exactly {@code percent} % of his max health, without the level factor (the
     * dungeon champions' normal blows, Simon 29.10.: the share goes by level bands itself).
     * {@code through}: no shield stops it. Returns the damage aimed.
     */
    public static float hitFlat(ServerLevel level, ServerPlayer player, Entity attacker, Entity direct, double percent,
                                boolean through) {
        if (level == null || player == null || !player.isAlive() || player.isCreative() || player.isSpectator()
                || percent <= 0) {
            return 0f;
        }
        float amount = (float) (player.getMaxHealth() * percent / 100.0);
        DamageSource source = source(level, direct == null ? attacker : direct, attacker, through ? THROUGH : TYPE);
        IN_HIT.set(Boolean.TRUE);
        try {
            player.invulnerableTime = 0;
            player.hurtServer(level, source, amount);
        } finally {
            IN_HIT.set(Boolean.FALSE);
        }
        return amount;
    }

    /** True while a share is being dealt (its own after-damage call must not add another one). */
    public static boolean inHit() {
        return IN_HIT.get();
    }

    /** True for a hit of the share's own type. */
    public static boolean is(DamageSource source) {
        return source != null && (source.is(TYPE) || source.is(THROUGH));
    }

    private static DamageSource source(ServerLevel level, Entity direct, Entity causing, ResourceKey<DamageType> kind) {
        try {
            Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(kind).orElse(null);
            if (type != null) {
                return new DamageSource(type, direct, causing);
            }
        } catch (RuntimeException e) {
            // the data pack did not load: fall back to magic (armour does not count, protection does)
        }
        return causing == null ? level.damageSources().magic() : level.damageSources().indirectMagic(direct, causing);
    }
}
