package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.BlockGuard;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Besiegers with a job (Simon, 24 October 2026):
 * <ul>
 *   <li><b>Ladder carriers</b>: about 7 % of every wave are zombies carrying ladders. They
 *   walk to the wall and build a complete ladder up to its top (night blocks), then fight
 *   (and climb) like the others.</li>
 *   <li><b>Sapper creepers</b>: from a level on, one per wave walks to the wall where it is
 *   closest to the core on its side and blows a 3x3 hole into it. At most one hole per side
 *   (north, east, south, west) and night: "a hole on every side is plenty". The rebuilding
 *   of the core tree mends the holes after the night.</li>
 * </ul>
 * While at their job they pick no fights (see {@link #busy}).
 */
public final class Besiegers {

    public static final String CARRIER = "nightsiege_ladderman";
    public static final String SAPPER = "nightsiege_sapper";
    /** Ticks a job may take before the monster gives up and just fights. */
    private static final int GIVE_UP = 900;
    /** Ticks between two rungs of a ladder. */
    private static final int RUNG_EVERY = 4;
    /** Ticks a sapper fizzes at the wall before it blows. */
    private static final int FUSE = 30;
    private static final int HIGHEST_LADDER = 24;

    private static final Set<UUID> BUSY = new HashSet<>();

    private final Map<UUID, Job> jobs = new HashMap<>();
    /** Sides (0 east, 1 south, 2 west, 3 north) that already got their hole tonight. */
    private final Set<Integer> breached = new HashSet<>();
    /** Sides with a sapper on its way. */
    private final Set<Integer> sapperOnWay = new HashSet<>();

    private static final class Job {
        final boolean sapper;
        final int side;
        final long started;
        Vec3 approach;
        BlockPos wall;
        Direction face;
        int rung;
        long next;
        long fuseUntil = -1;

        Job(boolean sapper, int side, long started) {
            this.sapper = sapper;
            this.side = side;
            this.started = started;
        }
    }

    public void reset() {
        for (UUID id : jobs.keySet()) {
            BUSY.remove(id);
        }
        jobs.clear();
        breached.clear();
        sapperOnWay.clear();
    }

    /** True while the monster is at its job (it then targets nobody). */
    public static boolean busy(Entity mob) {
        return mob != null && BUSY.contains(mob.getUUID());
    }

    public int breaches() {
        return breached.size();
    }

    // ------------------------------------------------------------------ after a wave

    /**
     * Makes some of the wave's zombies ladder carriers and sends a sapper creeper (one per
     * wave, on a side without a hole yet). New monsters go to {@code add}.
     */
    public void afterWave(ServerLevel world, BlockPos anchor, BaseScan base, List<Mob> wave, int level, SiegeConfig cfg,
                          Random random, SiegeEvent event, Consumer<Mob> add) {
        if (world == null || anchor == null) {
            return;
        }
        long now = world.getGameTime();
        // Ladder carriers: a share of the wave (random rounding), taken from its zombies.
        if (cfg.ladderCarrierShare > 0 && base != null && base.closed) {
            double want = wave.size() * cfg.ladderCarrierShare;
            int count = (int) Math.floor(want) + (random.nextDouble() < want - Math.floor(want) ? 1 : 0);
            for (Mob mob : wave) {
                if (count <= 0) {
                    break;
                }
                if (mob instanceof Zombie zombie && !zombie.isBaby() && !mob.isPassenger() && !jobs.containsKey(mob.getUUID())) {
                    mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.LADDER, 8));
                    mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
                    mob.addTag(CARRIER);
                    mob.setCustomName(Component.translatable("nightsiege.mob.ladderman"));
                    mob.setTarget(null);
                    jobs.put(mob.getUUID(), new Job(false, sideOf(anchor, mob.position()), now));
                    BUSY.add(mob.getUUID());
                    count--;
                }
            }
        }
        // A sapper creeper for one side that has no hole yet (and none on the way).
        if (cfg.sapperFromLevel > 0 && level >= cfg.sapperFromLevel && base != null && base.closed) {
            List<Integer> open = new ArrayList<>();
            for (int side = 0; side < 4; side++) {
                if (!breached.contains(side) && !sapperOnWay.contains(side)) {
                    open.add(side);
                }
            }
            if (!open.isEmpty()) {
                int side = open.get(random.nextInt(open.size()));
                Vec3 approach = weakSpot(anchor, base, side);
                BlockPos spot = spawnSpotOnSide(world, anchor, base, side, random);
                Mob creeper = spot == null ? null
                        : MobFactory.spawnAt(world, spot, "minecraft:creeper", level, cfg, random, event, false);
                if (creeper != null) {
                    creeper.addTag(SAPPER);
                    creeper.setCustomName(Component.translatable("nightsiege.mob.sapper"));
                    creeper.setTarget(null);
                    Job job = new Job(true, side, now);
                    job.approach = approach;
                    jobs.put(creeper.getUUID(), job);
                    BUSY.add(creeper.getUUID());
                    sapperOnWay.add(side);
                    add.accept(creeper);
                    world.playSound(null, spot, SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 0.6f, 0.6f);
                }
            }
        }
    }

    /** 0 east, 1 south, 2 west, 3 north of the core. */
    static int sideOf(BlockPos anchor, Vec3 at) {
        double a = Math.atan2(at.z - (anchor.getZ() + 0.5), at.x - (anchor.getX() + 0.5));
        int side = (int) Math.round(a / (Math.PI / 2));
        return Math.floorMod(side, 4);
    }

    /** Just outside the wall where it comes closest to the core on that side. */
    private static Vec3 weakSpot(BlockPos anchor, BaseScan base, int side) {
        double center = side * Math.PI / 2;
        double best = Double.MAX_VALUE;
        double bestAngle = center;
        for (int i = -8; i <= 8; i++) {
            double a = center + i * Math.PI / 36;   // +-40 degrees
            double edge = base.edgeTowards(anchor.getX() + 0.5 + Math.cos(a) * 8, anchor.getZ() + 0.5 + Math.sin(a) * 8);
            if (edge < best) {
                best = edge;
                bestAngle = a;
            }
        }
        double d = best + 3.0;
        return new Vec3(anchor.getX() + 0.5 + Math.cos(bestAngle) * d, anchor.getY(), anchor.getZ() + 0.5 + Math.sin(bestAngle) * d);
    }

    private static BlockPos spawnSpotOnSide(ServerLevel world, BlockPos anchor, BaseScan base, int side, Random random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double a = side * Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI / 2.2;
            double edge = base.edgeTowards(anchor.getX() + 0.5 + Math.cos(a) * 8, anchor.getZ() + 0.5 + Math.sin(a) * 8);
            double d = edge + 8.0 + random.nextDouble() * 8.0;
            int x = anchor.getX() + (int) Math.round(Math.cos(a) * d);
            int z = anchor.getZ() + (int) Math.round(Math.sin(a) * d);
            if (!world.hasChunk(x >> 4, z >> 4) || base.contains(x, z)) {
                continue;
            }
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spot = new BlockPos(x, y, z);
            if (world.getBlockState(spot).isAir() && world.getBlockState(spot.above()).isAir()
                    && world.getBlockState(spot.below()).getFluidState().isEmpty()) {
                return spot;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ every few ticks

    public void tick(ServerLevel world, Collection<UUID> live, BlockPos anchor, long now) {
        if (jobs.isEmpty() || world == null || anchor == null) {
            return;
        }
        for (UUID id : new ArrayList<>(jobs.keySet())) {
            Job job = jobs.get(id);
            Entity entity = world.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive() || !live.contains(id)) {
                finish(id, job, null);
                continue;
            }
            if (now - job.started > GIVE_UP) {
                finish(id, job, mob);   // stuck: just fight
                continue;
            }
            mob.setTarget(null);
            if (job.sapper) {
                tickSapper(world, mob, job, anchor, now);
            } else {
                tickCarrier(world, mob, job, anchor, now);
            }
        }
    }

    private void finish(UUID id, Job job, Mob mob) {
        jobs.remove(id);
        BUSY.remove(id);
        if (job != null && job.sapper) {
            sapperOnWay.remove(job.side);
        }
        if (mob != null) {
            mob.removeTag(CARRIER);
            mob.removeTag(SAPPER);
            if (!job.sapper) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
    }

    /** Looks from the monster towards the core for the wall: the first solid block at foot or head height. */
    private static boolean findWall(ServerLevel world, Mob mob, BlockPos anchor, Job job, double reach) {
        Vec3 from = mob.position();
        double dx = anchor.getX() + 0.5 - from.x;
        double dz = anchor.getZ() + 0.5 - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0) {
            return false;
        }
        dx /= len;
        dz /= len;
        BlockPos last = mob.blockPosition();
        for (double t = 0.5; t <= reach; t += 0.5) {
            BlockPos at = BlockPos.containing(from.x + dx * t, from.y + 0.2, from.z + dz * t);
            if (at.equals(last)) {
                continue;
            }
            if (solid(world, at) || solid(world, at.above())) {
                Direction face = Direction.getApproximateNearest(-dx, 0, -dz);
                job.wall = at;
                job.face = face;
                job.approach = Vec3.atBottomCenterOf(job.wall.relative(face));
                return true;
            }
            last = at;
        }
        return false;
    }

    private static boolean solid(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    // ------------------------------------------------------------------ ladder carriers

    private void tickCarrier(ServerLevel world, Mob mob, Job job, BlockPos anchor, long now) {
        if (job.wall == null) {
            // On the way: towards the core until the wall is in front.
            if (findWall(world, mob, anchor, job, 2.5)) {
                mob.getNavigation().stop();
                job.next = now;
                return;
            }
            if (mob.getNavigation().isDone() || now % 20 == 0) {
                mob.getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 1.0);
            }
            return;
        }
        // At the wall: one rung after the other, from the ground up to the top.
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(Vec3.atCenterOf(job.wall.above(job.rung)));
        if (now < job.next) {
            return;
        }
        job.next = now + RUNG_EVERY;
        BlockPos column = job.wall.relative(job.face);
        BlockPos rungPos = column.above(job.rung);
        BlockPos support = job.wall.above(job.rung);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, job.face);
        boolean wallThere = world.getBlockState(support).isFaceSturdy(world, support, job.face);
        if (!wallThere || job.rung >= HIGHEST_LADDER || !ladder.canSurvive(world, rungPos)
                || !(TempBlocks.place(world, rungPos, ladder) || world.getBlockState(rungPos).is(Blocks.LADDER))) {
            if (job.rung > 0) {
                world.playSound(null, column, SoundEvents.LADDER_PLACE, SoundSource.HOSTILE, 1.0f, 0.8f);
            }
            finish(mob.getUUID(), job, mob);   // the ladder stands (or there was nothing to lean it on)
            return;
        }
        world.playSound(null, rungPos, SoundEvents.LADDER_PLACE, SoundSource.HOSTILE, 0.7f, 1.0f + job.rung * 0.05f);
        job.rung++;
    }

    // ------------------------------------------------------------------ sapper creepers

    private void tickSapper(ServerLevel world, Mob mob, Job job, BlockPos anchor, long now) {
        if (job.fuseUntil >= 0) {
            mob.getNavigation().stop();
            world.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY(1.0), mob.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
            world.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY(0.9), mob.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
            if (now >= job.fuseUntil) {
                blow(world, mob, job);
            }
            return;
        }
        if (job.wall == null && findWall(world, mob, anchor, job, 2.5)) {
            job.fuseUntil = now + FUSE;
            world.playSound(null, mob.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.2f, 0.7f);
            return;
        }
        if (now % 20 == 0) {
            world.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY(1.1), mob.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
        }
        // To the weak spot of its side; close to it, straight at the core (into the wall).
        Vec3 goal = job.approach;
        if (goal == null || mob.position().distanceToSqr(goal.x, mob.getY(), goal.z) < 4.0) {
            goal = new Vec3(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            job.approach = null;
        }
        if (mob.getNavigation().isDone() || now % 20 == 0) {
            mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.1);
        }
    }

    /** The sapper blows: a 3x3 hole through the wall (up to 3 deep), its blast hurts like a creeper's. */
    private void blow(ServerLevel world, Mob mob, Job job) {
        Vec3 at = mob.position();
        SiegeConfig cfg = SiegeManager.get() == null ? null : SiegeManager.get().config();
        // The blast itself (damage and knockback), but no block damage of its own.
        world.explode(mob, at.x, at.y + 0.5, at.z, mob instanceof Creeper c && c.isPowered() ? 4.5f : 3.0f,
                Level.ExplosionInteraction.NONE);
        int removed = 0;
        Direction in = job.face.getOpposite();
        Direction across = in.getClockWise();
        for (int depth = 0; depth < 3; depth++) {
            boolean any = false;
            for (int w = -1; w <= 1; w++) {
                for (int h = 0; h <= 2; h++) {
                    BlockPos pos = job.wall.relative(in, depth).relative(across, w).above(h);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    any = true;
                    if (ModContent.isCore(state) || ModContent.trapOf(state) != null
                            || state.is(Blocks.BEDROCK) || state.hasBlockEntity()
                            || cfg != null && BlockGuard.isProtected(Demolition.blockId(state), cfg)) {
                        continue;
                    }
                    if (world.destroyBlock(pos, false)) {
                        removed++;
                    }
                }
            }
            if (!any) {
                break;   // through the wall
            }
        }
        breached.add(job.side);
        NightSiegeMod.LOGGER.info("[Night Siege] Sprengcreeper: Loch in der Mauer (Seite {}), {} Blöcke", job.side, removed);
        finish(mob.getUUID(), job, null);
        mob.discard();
    }
}
