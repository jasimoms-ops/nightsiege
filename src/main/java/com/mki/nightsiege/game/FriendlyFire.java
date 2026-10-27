package com.mki.nightsiege.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * No friendly fire in the night (Simon, 27 October 2026, after four defenders fell to their
 * friends: twice the tesla, twice the paladin's hammer): while a siege runs, the defenders of
 * the night cannot hurt each other – not with blows, arrows, bolts, potions, explosions, the
 * tesla, the mortar, a trap or a tower. Healing and strengthening still reach them; outside
 * the night everything stays as it was. The summoner's companions spare players anyway.
 */
public final class FriendlyFire {

    private FriendlyFire() {
    }

    /** Whether this hit may land (false for one defender of the night hitting another). */
    public static boolean allow(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer hurt) || source == null) {
            return true;
        }
        SiegeManager m = SiegeManager.get();
        if (m == null || !m.isActive() || m.config().nightFriendlyFire) {
            return true;
        }
        ServerPlayer from = responsible(source);
        if (from == null || from == hurt) {
            return true;
        }
        return !(m.isNightPlayer(hurt) && m.isNightPlayer(from));
    }

    /** The player behind a hit: the hitter, the shooter of a projectile, the owner of a pet or a trap. */
    static ServerPlayer responsible(DamageSource source) {
        Entity e = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        for (int depth = 0; depth < 4 && e != null; depth++) {
            if (e instanceof ServerPlayer player) {
                return player;
            }
            if (e instanceof Projectile projectile) {
                e = projectile.getOwner();
            } else if (e instanceof OwnableEntity pet) {
                e = pet.getOwner();
            } else if (e instanceof TraceableEntity traced) {
                e = traced.getOwner();
            } else {
                return null;
            }
        }
        return null;
    }
}
