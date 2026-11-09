package com.mki.nightsiege.game;

import com.mki.nightsiege.core.KillingHit;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Killing hits for the counters only (GOLEMDIAG 3, 09.11.): AFTER_DAMAGE leaves them out, so the
 * hurt mixin measures them here, the same way as StealPool. Game effects stay on AFTER_DAMAGE.
 */
public final class KillingHits {

    /** Per entity being hit: life and absorption before, and whether AFTER_DAMAGE reported it. */
    private static final Map<LivingEntity, float[]> OPEN = new IdentityHashMap<>();

    private KillingHits() {
    }

    /** From the hurt mixin, before the hit. True when this call opened it. */
    public static boolean begin(LivingEntity entity) {
        if (entity.level().isClientSide() || entity.isDeadOrDying() || OPEN.containsKey(entity)) {
            return false;
        }
        OPEN.put(entity, new float[]{entity.getHealth(), entity.getAbsorptionAmount(), 0f});
        return true;
    }

    /** From AFTER_DAMAGE: this hit is counted there already. */
    public static void reported(LivingEntity entity) {
        float[] open = OPEN.get(entity);
        if (open != null) {
            open[2] = 1f;
        }
    }

    /** From the hurt mixin, after the hit: a killing hit goes to the counters. */
    public static void end(LivingEntity entity, DamageSource source, boolean began) {
        if (!began) {
            return;
        }
        float[] open = OPEN.remove(entity);
        if (open == null || !entity.isDeadOrDying()) {
            return;
        }
        float lost = KillingHit.lost(open[2] > 0f, open[0], open[1], entity.getHealth(), entity.getAbsorptionAmount());
        SiegeManager m = SiegeManager.get();
        if (lost > 0f && m != null) {
            try {
                m.killingHit(entity, source, lost);
            } catch (RuntimeException e) {
                // Never in the way of a hit.
            }
        }
    }
}
