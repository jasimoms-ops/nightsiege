package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Balance update 2 (Simon 29.10.): the dungeon champions (Wächter) hit with a share of the max health
 * in their normal blows and shots too, not only in their skills. Every blow or shot that lands takes
 * {@link SiegeConfig#championPercent} of the target's max health on top, through {@link PercentDamage}
 * like a boss's; a raised shield lets only {@code championPercentShield} of it through. This holds for
 * the champion in his dungeon and for the warden at the end of a night (both wear
 * {@link WardenNight#TAG}); the night's other monsters stay as they are.
 */
final class ChampionStrikes {

    private ChampionStrikes() {
    }

    /** A dungeon champion, in his dungeon or in a night. */
    static boolean isChampion(Entity entity) {
        return entity != null && (entity.entityTags().contains(Expeditions.CHAMPION_TAG)
                || entity.entityTags().contains(WardenNight.TAG));
    }

    /** True for the champion's own blow or one of his arrows, potions or fireballs (not a skill's share). */
    static boolean normalHit(DamageSource source) {
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (!isChampion(attacker) || direct == null) {
            return false;
        }
        return direct == attacker || direct instanceof Projectile shot && shot.getOwner() == attacker;
    }

    /** After a hit on a player: the champion's share on top (half of it through a raised shield). */
    static void afterHit(SiegeManager m, LivingEntity victim, DamageSource source, boolean blocked) {
        if (m == null || !(victim instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)
                || PercentDamage.inHit() || PercentDamage.is(source) || !normalHit(source)) {
            return;
        }
        SiegeConfig cfg = m.config();
        double percent = cfg.championPercent(m.currentLevel()) * 100.0;
        if (blocked) {
            percent *= cfg.championPercentShield;
        }
        if (percent <= 0) {
            return;
        }
        PercentDamage.hitFlat(level, player, source.getEntity(), source.getDirectEntity(), percent, blocked);
    }
}
