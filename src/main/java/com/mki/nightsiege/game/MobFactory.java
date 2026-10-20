package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/** Creates the siege mobs and scales their stats to the siege level. */
public final class MobFactory {

    /** Entity tag every siege mob carries, so they can be told apart from normal mobs. */
    public static final String TAG = "nightsiege";

    private MobFactory() {
    }

    /** True when this entity id is not registered, usually because the mod is missing. */
    public static boolean isUnavailable(String entityId) {
        Identifier id = Identifier.tryParse(entityId);
        return id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    /**
     * Spawns one mob near the anchor.
     *
     * @return the mob, or null when nothing could be placed
     */
    public static Mob spawn(ServerLevel world, BlockPos anchor, String entityId,
                            int siegeLevel, SiegeConfig cfg, Random random) {
        Identifier id = Identifier.tryParse(entityId);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        if (type == null) {
            return null;
        }
        BlockPos spot = findSpawnSpot(world, anchor, cfg, random);
        if (spot == null) {
            return null;
        }
        Entity entity = type.create(world, EntitySpawnReason.EVENT);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
        applyScaling(mob, siegeLevel, cfg);
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        if (!world.addFreshEntity(mob)) {
            mob.discard();
            return null;
        }
        return mob;
    }

    private static void applyScaling(Mob mob, int siegeLevel, SiegeConfig cfg) {
        double healthMul = SiegeMath.healthMultiplier(siegeLevel, cfg);
        double damageMul = SiegeMath.damageMultiplier(siegeLevel, cfg);

        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * healthMul);
            mob.setHealth((float) health.getBaseValue());
        }
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * damageMul);
        }
        // Without a long follow range the mobs lose interest halfway to the base.
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(Math.max(follow.getBaseValue(), 72.0));
        }
    }

    /** Looks for open ground in a ring around the anchor. */
    private static BlockPos findSpawnSpot(ServerLevel world, BlockPos anchor, SiegeConfig cfg, Random random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = cfg.spawnMinDistance
                    + random.nextDouble() * (cfg.spawnMaxDistance - cfg.spawnMinDistance);
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!world.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spot = new BlockPos(x, y, z);
            // Needs two free blocks of room and no water or lava underfoot.
            if (!world.getBlockState(spot).isAir() || !world.getBlockState(spot.above()).isAir()) {
                continue;
            }
            if (!world.getBlockState(spot.below()).getFluidState().isEmpty()) {
                continue;
            }
            return spot;
        }
        NightSiegeMod.LOGGER.debug("[Night Siege] kein Spawnplatz gefunden");
        return null;
    }
}
