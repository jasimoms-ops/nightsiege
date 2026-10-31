package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Wächter-Schutz (Simon, 30 October 2026): the tanks gave up much of their damage, in return their
 * teammates near a bulwark or a shield fighter take less damage from monsters and less knockback.
 * Only the strongest tank near a player counts (two tanks make nobody unbreakable); a tank does not
 * shield himself. It works wherever there is fighting: nights, dungeons and the war camp.
 */
public final class TankGuard {

    private static final Identifier KNOCKBACK = ModContent.id("tank_guard_knockback");
    /** The share of monster damage each protected player is spared right now. */
    private static final Map<UUID, Double> DAMAGE_CUT = new HashMap<>();
    private static final Map<UUID, Double> KNOCKBACK_CUT = new HashMap<>();
    private static int counter;

    private TankGuard() {
    }

    private record Tank(ServerPlayer player, double radius, double damage, double knockback) {
    }

    /**
     * Twice a second: who stands near which tank (the server tick). Since 31.10. (survival safety 2)
     * only between players who fight right now ({@code fighting}: defenders of the night, fighters in a
     * dungeon or at the war camp); everyone else loses the knockback modifier at once.
     */
    static void tick(MinecraftServer server, SiegeConfig cfg, Function<ServerPlayer, String> subclassOf,
                     Predicate<ServerPlayer> fighting) {
        if (++counter % 10 != 0 || cfg == null) {
            return;
        }
        Map<UUID, Double> damage = new HashMap<>();
        Map<UUID, Double> knockback = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> players = new ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (fighting.test(p)) {
                    players.add(p);
                }
            }
            if (players.size() < 2) {
                continue;
            }
            List<Tank> tanks = new ArrayList<>();
            for (ServerPlayer p : players) {
                if (!p.isAlive() || p.isSpectator()) {
                    continue;
                }
                String sub = subclassOf.apply(p);
                if (SkillTree.SUB_BULWARK.equals(sub) && cfg.bulwarkGuardRadius > 0) {
                    tanks.add(new Tank(p, cfg.bulwarkGuardRadius, cfg.bulwarkGuardDamage, cfg.bulwarkGuardKnockback));
                } else if (SkillTree.SUB_SHIELDFIGHTER.equals(sub) && cfg.shieldfighterGuardRadius > 0) {
                    tanks.add(new Tank(p, cfg.shieldfighterGuardRadius, cfg.shieldfighterGuardDamage, cfg.shieldfighterGuardKnockback));
                }
            }
            if (tanks.isEmpty()) {
                continue;
            }
            for (ServerPlayer p : players) {
                if (!p.isAlive() || p.isSpectator()) {
                    continue;
                }
                double bestDamage = 0.0;
                double bestKnockback = 0.0;
                for (Tank t : tanks) {
                    if (t.player() == p || t.player().distanceToSqr(p) > t.radius() * t.radius()) {
                        continue;
                    }
                    bestDamage = Math.max(bestDamage, t.damage());
                    bestKnockback = Math.max(bestKnockback, t.knockback());
                }
                if (bestDamage > 0) {
                    damage.put(p.getUUID(), bestDamage);
                }
                if (bestKnockback > 0) {
                    knockback.put(p.getUUID(), bestKnockback);
                }
            }
        }
        DAMAGE_CUT.clear();
        DAMAGE_CUT.putAll(damage);
        // The knockback as a modifier on the player's knockback resistance, taken off when he leaves.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            setKnockback(p, knockback.getOrDefault(p.getUUID(), 0.0));
        }
        for (UUID gone : new ArrayList<>(KNOCKBACK_CUT.keySet())) {
            if (!knockback.containsKey(gone)) {
                KNOCKBACK_CUT.remove(gone);
            }
        }
        KNOCKBACK_CUT.putAll(knockback);
    }

    private static void setKnockback(ServerPlayer p, double cut) {
        AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb == null) {
            return;
        }
        AttributeModifier now = kb.getModifier(KNOCKBACK);
        if (cut > 0) {
            if (now == null || now.amount() != cut) {
                kb.addOrUpdateTransientModifier(new AttributeModifier(KNOCKBACK, cut, AttributeModifier.Operation.ADD_VALUE));
            }
        } else if (now != null) {
            kb.removeModifier(KNOCKBACK);
        }
    }

    /** Factor on the damage a monster deals to this player (1 = no tank near). */
    public static float damageFactor(ServerPlayer hurt) {
        Double cut = DAMAGE_CUT.get(hurt.getUUID());
        return cut == null ? 1f : (float) Math.max(0.1, 1.0 - cut);
    }

    /** For the tests: the share this player is spared right now. */
    public static double damageCut(UUID id) {
        return DAMAGE_CUT.getOrDefault(id, 0.0);
    }
}
