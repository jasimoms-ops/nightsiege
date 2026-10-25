package com.mki.nightsiege.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.Set;

/**
 * A light aura around the event monsters near the player: blood drips at the blood moon,
 * shadow and gold motes in the eclipse, ash in the fog, sparks in the storm, embers at a
 * rift, snow in the frost night. Few particles on purpose: at most a dozen a tick, only
 * within 24 blocks.
 */
public final class EventAura {

    private static final Set<EntityType<?>> DRESSED = Set.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.DROWNED,
            EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.PARCHED, EntityTypes.WITHER_SKELETON,
            EntityTypes.CREEPER, EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER);
    private static final int BUDGET = 12;
    private static final DustParticleOptions BLOOD = new DustParticleOptions(0x8A0A10, 0.8f);
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xE8B53A, 0.6f);
    private static final BlockParticleOption DRIP = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.REDSTONE_BLOCK.defaultBlockState());

    private EventAura() {
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.getGameTime() % 2 != 0) {
            return;
        }
        String event = EventTextures.shownEvent();
        if (event == null) {
            return;
        }
        RandomSource r = mc.level.getRandom();
        AABB box = mc.player.getBoundingBox().inflate(24.0);
        int left = BUDGET;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, box, e -> DRESSED.contains(e.getType()) && e.isAlive())) {
            if (left <= 0) {
                break;
            }
            if (r.nextInt(4) != 0) {
                continue;
            }
            left--;
            double x = e.getX() + (r.nextDouble() - 0.5) * e.getBbWidth();
            double y = e.getY() + r.nextDouble() * e.getBbHeight();
            double z = e.getZ() + (r.nextDouble() - 0.5) * e.getBbWidth();
            ParticleOptions p;
            double vy = 0;
            switch (event) {
                case "blood_moon" -> p = r.nextInt(3) == 0 ? BLOOD : DRIP;
                case "eclipse" -> {
                    p = r.nextInt(3) == 0 ? GOLD : ParticleTypes.SMOKE;
                    vy = 0.02;
                }
                case "fog" -> p = ParticleTypes.WHITE_ASH;
                case "thunder" -> p = r.nextInt(3) == 0 ? ParticleTypes.FALLING_WATER : ParticleTypes.ELECTRIC_SPARK;
                case "nether_rift" -> {
                    p = r.nextInt(4) == 0 ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME;
                    vy = 0.03;
                }
                case "frost" -> {
                    p = ParticleTypes.SNOWFLAKE;
                    vy = -0.02;
                }
                default -> {
                    continue;
                }
            }
            mc.level.addParticle(p, x, y, z, 0, vy, 0);
        }
    }
}
