package com.mki.nightsiege.game;

import com.mki.nightsiege.core.CasterCurb;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tor 9b: blindness and slowness from illager casters on players are cut ({@link CasterCurb}).
 * Called from LivingEntityEffectMixin for every addEffect; only players, only these two effects,
 * only when the source (or its owner, or with no source the mob that hit the player this very tick)
 * is a caster. Everything else passes unchanged.
 */
public final class CasterCurbs {

    private static final ThreadLocal<boolean[]> APPLYING = ThreadLocal.withInitial(() -> new boolean[1]);
    /** Per player: game time the last caster blindness / slowness began. */
    private static final Map<UUID, long[]> LAST = new HashMap<>();

    private CasterCurbs() {
    }

    /** The effect to give: the same one, a cut one, or null to give none. */
    public static MobEffectInstance curb(LivingEntity self, MobEffectInstance effect, Entity source) {
        if (APPLYING.get()[0] || !(self instanceof ServerPlayer player) || effect == null) {
            return effect;
        }
        CasterCurb.Kind kind = effect.is(MobEffects.BLINDNESS) ? CasterCurb.Kind.BLIND
                : effect.is(MobEffects.SLOWNESS) ? CasterCurb.Kind.SLOW : null;
        if (kind == null || !fromCaster(player, source)) {
            return effect;
        }
        long now = player.level().getGameTime();
        long[] last = LAST.computeIfAbsent(player.getUUID(), k -> new long[]{-1, -1});
        int[] cut = CasterCurb.cap(kind, effect.getDuration(), effect.getAmplifier(), now, last[kind.ordinal()]);
        if (cut == null) {
            return null;
        }
        last[kind.ordinal()] = now;
        if (cut[0] == effect.getDuration() && cut[1] == effect.getAmplifier()) {
            return effect;
        }
        return new MobEffectInstance(effect.getEffect(), cut[0], cut[1], effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }

    /** Gives the cut effect without curbing it a second time. */
    public static boolean apply(LivingEntity self, MobEffectInstance effect, Entity source) {
        boolean[] flag = APPLYING.get();
        flag[0] = true;
        try {
            return self.addEffect(effect, source);
        } finally {
            flag[0] = false;
        }
    }

    private static boolean fromCaster(ServerPlayer player, Entity source) {
        if (source == null) {
            LivingEntity hit = player.getLastHurtByMob();
            return hit != null && player.getLastHurtByMobTimestamp() == player.tickCount && caster(hit);
        }
        if (caster(source)) {
            return true;
        }
        return source instanceof TraceableEntity traced && traced.getOwner() != null && caster(traced.getOwner());
    }

    private static boolean caster(Entity e) {
        return e instanceof SpellcasterIllager || CasterCurb.casterId(EntityType.getKey(e.getType()).toString());
    }

    /** For the tests: forget the locks. */
    public static void clear() {
        LAST.clear();
    }
}
