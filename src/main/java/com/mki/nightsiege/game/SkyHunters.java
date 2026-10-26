package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Vex;

/**
 * Hunters from the sky (Simon, 26 October 2026): vexes and phantoms of a siege go for the
 * defenders who stand high up first – on a wall, a tower, a roof – as long as one is in reach.
 * Only when nobody stands high do they take the nearest defender, like every monster.
 */
public final class SkyHunters {

    /** Per player: game time of the last check and whether he stood high then. */
    private final Map<UUID, long[]> elevated = new HashMap<>();

    /** Every second: point every vex and phantom of the night at a high-standing defender. */
    public void tick(ServerLevel world, Collection<UUID> live, Collection<ServerPlayer> defenders,
                     SiegeConfig config, long now) {
        if (now % 20 != 0 || defenders.isEmpty() || config.skyHunterRange <= 0) {
            return;
        }
        double range = config.skyHunterRange;
        for (UUID id : live) {
            Entity entity = world.getEntity(id);
            if (!(entity instanceof Mob mob) || !(mob instanceof Vex || mob instanceof Phantom) || !mob.isAlive()) {
                continue;
            }
            ServerPlayer best = null;
            double bestDistance = range * range;
            for (ServerPlayer player : defenders) {
                if (player.level() != world || !player.isAlive() || player.isSpectator() || Drones.isDrone(player)) {
                    continue;
                }
                double d = mob.distanceToSqr(player);
                if (d <= bestDistance && standsHigh(world, player, config, now)) {
                    bestDistance = d;
                    best = player;
                }
            }
            if (best != null && mob.getTarget() != best) {
                mob.setTarget(best);
            }
        }
    }

    /**
     * True when the player stands at least {@code skyHunterHeight} blocks above the ground
     * around him (three blocks out on each side): on top of a wall, not just on a hill.
     */
    private boolean standsHigh(ServerLevel world, ServerPlayer player, SiegeConfig config, long now) {
        long[] cached = elevated.get(player.getUUID());
        if (cached != null && now - cached[0] < 20) {
            return cached[1] == 1;
        }
        int feet = player.getBlockY();
        int lowest = feet;
        BlockPos base = player.blockPosition();
        int[][] probes = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
        for (int[] p : probes) {
            int x = base.getX() + p[0];
            int z = base.getZ() + p[1];
            int y = feet;
            // Walk down to the first block someone could stand on, at most 12 below the feet.
            while (y > feet - 12 && world.getBlockState(new BlockPos(x, y - 1, z)).getCollisionShape(world, new BlockPos(x, y - 1, z)).isEmpty()) {
                y--;
            }
            lowest = Math.min(lowest, y);
        }
        boolean high = feet - lowest >= config.skyHunterHeight;
        elevated.put(player.getUUID(), new long[]{now, high ? 1 : 0});
        return high;
    }

    /** Forget the per-player checks between nights. */
    public void clear() {
        elevated.clear();
    }
}
