package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Life steal only against the damage a player really took (Simon 05.11. 15:0x: "Lebensraub an den
 * eingesteckten Schaden koppeln", 100 % of the last 5 s). Every point of life a player loses goes
 * into his pool for lifestealTakenSeconds; a life-steal heal (hits, kills, combo, the berserker,
 * the summoner's ghoul hunger and soul pact) takes from the oldest points first and heals no more
 * than is in it. Healers, potions, regeneration and abilities stay free. So a fighter whom nobody
 * hits heals nothing, and nobody heals himself up past what the monsters took.
 *
 * <p>The loss is measured as the life before and after the hit (after armour, the shield, the
 * reductions and absorption), so the statistics get the life that was really lost too.
 */
public final class StealPool {

    /** Per player: [game time, life left to heal back] of each loss, oldest first. */
    private static final Map<UUID, ArrayDeque<double[]>> LOST = new HashMap<>();
    /**
     * Tor 4 (Simon 08.11.): the same, kept always (also without the coupling), for the late hit life
     * steal (config hitLifestealLate): from hitLifestealLateLevel the axe fighter heals no more than
     * hitLifestealLateTakenShare of what he lost in the last lifestealTakenSeconds.
     */
    private static final Map<UUID, ArrayDeque<double[]>> LATE = new HashMap<>();
    /** Per player: life and absorption before a hit that is being dealt, and whether it is counted already. */
    private static final Map<UUID, float[]> OPEN = new HashMap<>();
    /** No more than this many losses are kept per player. */
    private static final int MAX_ENTRIES = 200;

    private StealPool() {
    }

    private static SiegeConfig cfg() {
        SiegeManager m = SiegeManager.get();
        return m == null ? null : m.config();
    }

    // ------------------------------------------------------------------ the hit

    /** From the hurt mixin, before a hit is dealt: remembers the life. True when this call opened it. */
    public static boolean begin(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || player.level().isClientSide() || OPEN.containsKey(player.getUUID())) {
            return false;
        }
        OPEN.put(player.getUUID(), new float[]{player.getHealth(), player.getAbsorptionAmount(), 0f});
        return true;
    }

    /**
     * The hit landed (first thing in AFTER_DAMAGE, before a totem or anything else heals): what was
     * lost goes into the pool and the statistics.
     */
    public static void settle(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        float[] open = OPEN.get(player.getUUID());
        if (open == null || open[2] > 0f) {
            return;
        }
        open[2] = 1f;
        report(player, source, open[0] - player.getHealth(), open[1] - player.getAbsorptionAmount());
    }

    /** From the hurt mixin, after the hit: a killing hit (no AFTER_DAMAGE) is counted here. */
    public static void end(LivingEntity entity, DamageSource source, boolean began) {
        if (!began || !(entity instanceof ServerPlayer player)) {
            return;
        }
        float[] open = OPEN.remove(player.getUUID());
        if (open != null && open[2] == 0f) {
            report(player, source, open[0] - Math.max(0f, player.getHealth()), open[1] - player.getAbsorptionAmount());
        }
    }

    private static void report(ServerPlayer player, DamageSource source, float lost, float absorbed) {
        lost = Math.max(0f, lost);
        absorbed = Math.max(0f, absorbed);
        if (lost <= 0f && absorbed <= 0f) {
            return;
        }
        try {
            if (lost > 0f) {
                lost(player, lost, player.level().getGameTime());
            }
            StatsRecorder.onLost(player, source, lost, absorbed);
        } catch (RuntimeException e) {
            // Never in the way of a hit.
        }
    }

    // ------------------------------------------------------------------ the pool

    /** A player lost this much life now. */
    public static void lost(ServerPlayer player, double amount, long now) {
        SiegeConfig cfg = cfg();
        if (amount <= 0 || cfg == null) {
            return;
        }
        if (cfg.hitLifestealLateTakenShare > 0) {
            add(LATE.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>()), now, amount * cfg.hitLifestealLateTakenShare, cfg);
        }
        if (cfg.lifestealCoupled) {
            add(LOST.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>()), now, amount * cfg.lifestealTakenShare, cfg);
        }
    }

    private static void add(ArrayDeque<double[]> q, long now, double amount, SiegeConfig cfg) {
        prune(q, now, cfg);
        q.addLast(new double[]{now, amount});
        while (q.size() > MAX_ENTRIES) {
            q.pollFirst();
        }
    }

    /**
     * The cap of the late hit life steal (Tor 4): how much of {@code amount} may heal, at most what is
     * in the player's late pool (hitLifestealLateTakenShare of his losses of the last lifestealTakenSeconds),
     * taken from it. With hitLifestealLateTakenShare 0 the whole heal.
     */
    public static float takeLate(ServerPlayer player, float amount, long now) {
        SiegeConfig cfg = cfg();
        if (amount <= 0f) {
            return 0f;
        }
        if (cfg == null || cfg.hitLifestealLateTakenShare <= 0) {
            return amount;
        }
        double want = Math.min(amount, Math.max(0.0, player.getMaxHealth() - player.getHealth()));
        return (float) drain(LATE.get(player.getUUID()), want, now, cfg);
    }

    private static double drain(ArrayDeque<double[]> q, double want, long now, SiegeConfig cfg) {
        if (want <= 0 || q == null) {
            return 0.0;
        }
        prune(q, now, cfg);
        double given = 0;
        while (want - given > 1.0e-6 && !q.isEmpty()) {
            double[] e = q.peekFirst();
            double use = Math.min(e[1], want - given);
            e[1] -= use;
            given += use;
            if (e[1] <= 1.0e-6) {
                q.pollFirst();
            }
        }
        return given;
    }

    /**
     * How much of a life-steal heal may land now (at most the missing life and what is in the pool),
     * taken from the pool. Without the coupling the whole heal.
     */
    public static float take(ServerPlayer player, float amount, long now) {
        SiegeConfig cfg = cfg();
        if (amount <= 0f) {
            return 0f;
        }
        if (cfg == null || !cfg.lifestealCoupled) {
            return amount;
        }
        double want = Math.min(amount, Math.max(0.0, player.getMaxHealth() - player.getHealth()));
        return (float) drain(LOST.get(player.getUUID()), want, now, cfg);
    }

    /** What life steal could still heal back now (for the tests and the HUD). */
    public static double available(ServerPlayer player, long now) {
        SiegeConfig cfg = cfg();
        ArrayDeque<double[]> q = LOST.get(player.getUUID());
        if (cfg == null || q == null) {
            return 0.0;
        }
        prune(q, now, cfg);
        double sum = 0;
        for (double[] e : q) {
            sum += e[1];
        }
        return sum;
    }

    private static void prune(ArrayDeque<double[]> q, long now, SiegeConfig cfg) {
        long keep = Math.round(cfg.lifestealTakenSeconds * 20.0);
        while (!q.isEmpty() && (now - (long) q.peekFirst()[0] > keep || now < (long) q.peekFirst()[0])) {
            q.pollFirst();
        }
    }

    /** A player's pool is gone (death, the end of the night, a test). */
    public static void forget(UUID player) {
        LOST.remove(player);
        LATE.remove(player);
    }

    /** The night ended. */
    public static void clear() {
        LOST.clear();
        LATE.clear();
        OPEN.clear();
    }
}
