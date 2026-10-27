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
 *   <li><b>Sapper creepers</b>: from a level on, several per wave walk to the wall (where it is
 *   closest to the core on their side, each at another spot) and blow a crater into it. Up to
 *   sapperHolesPerSide holes per side and night (27 October 2026: was one; the base is rebuilt
 *   after every night).</li>
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
    /** The running instance, for {@link #disrupt}. */
    private static Besiegers current;

    private final Map<UUID, Job> jobs = new HashMap<>();
    /** Holes per side (0 east, 1 south, 2 west, 3 north) tonight. */
    private final Map<Integer, Integer> breached = new HashMap<>();
    /** Sappers on their way, per side. */
    private final Map<Integer, Integer> sapperOnWay = new HashMap<>();
    /** Where the sappers of each side go or went tonight, so the next one picks another spot. */
    private final Map<Integer, List<Vec3>> sapperSpots = new HashMap<>();

    private static final class Job {
        final boolean sapper;
        final int side;
        final long started;
        Vec3 approach;
        BlockPos wall;
        Direction face;
        int rung;
        /** Rungs this ladder needs (wall height), known once a free column was found. */
        int height = HIGHEST_LADDER;
        /** Walking sideways to a free column before building (since this tick, -1 = not). */
        long walkSince = -1;
        long next;
        long fuseUntil = -1;

        Job(boolean sapper, int side, long started) {
            this.sapper = sapper;
            this.side = side;
            this.started = started;
        }
    }

    public Besiegers() {
        current = this;
    }

    /** Teernetz (crate gain, 27 October 2026): a netted ladder carrier or sapper gives up its job. */
    public static void disrupt(Mob mob) {
        Besiegers b = current;
        if (b == null || mob == null) {
            return;
        }
        Job job = b.jobs.get(mob.getUUID());
        if (job != null) {
            b.finish(mob.getUUID(), job, mob);
        }
    }

    public void reset() {
        for (UUID id : jobs.keySet()) {
            BUSY.remove(id);
        }
        jobs.clear();
        breached.clear();
        sapperOnWay.clear();
        sapperSpots.clear();
    }

    /** True while the monster is at its job (it then targets nobody). */
    public static boolean busy(Entity mob) {
        return mob != null && BUSY.contains(mob.getUUID());
    }

    public int breaches() {
        int n = 0;
        for (int holes : breached.values()) {
            n += holes;
        }
        return n;
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
        // Also in a base with a gate (a door or gate makes it count as open), as long as there are
        // walls around it; before, a single door kept every ladder carrier away (Simon, 25 October 2026).
        if (cfg.ladderCarrierShare > 0 && base != null && (base.closed || base.reach() > 6.0)) {
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
        // Sapper creepers (Simon, 27 October 2026: "more sappers, the base is rebuilt after the
        // night, it may be bombed properly"): 1 + sappersPerLevel per level each wave (at most
        // sapperMaxPerWave), up to sapperHolesPerSide holes per side and night, each at another spot.
        if (cfg.sapperFromLevel > 0 && level >= cfg.sapperFromLevel && base != null && (base.closed || base.reach() > 6.0)) {
            double want = 1.0 + Math.max(0.0, cfg.sappersPerLevel) * level;
            int count = (int) Math.floor(want) + (random.nextDouble() < want - Math.floor(want) ? 1 : 0);
            count = Math.min(count, Math.max(1, cfg.sapperMaxPerWave));
            for (int n = 0; n < count; n++) {
                int side = freestSide(cfg, random);
                if (side < 0) {
                    break;   // every side has its holes tonight
                }
                Vec3 approach = weakSpot(anchor, base, side, sapperSpots.computeIfAbsent(side, k -> new ArrayList<>()), random);
                BlockPos spot = spawnSpotOnSide(world, anchor, base, side, random);
                Mob creeper = spot == null ? null
                        : MobFactory.spawnAt(world, spot, "minecraft:creeper", level, cfg, random, event, false);
                if (creeper == null) {
                    continue;
                }
                creeper.addTag(SAPPER);
                creeper.setCustomName(Component.translatable("nightsiege.mob.sapper"));
                creeper.setTarget(null);
                Job job = new Job(true, side, now);
                job.approach = approach;
                jobs.put(creeper.getUUID(), job);
                BUSY.add(creeper.getUUID());
                sapperOnWay.merge(side, 1, Integer::sum);
                sapperSpots.get(side).add(approach);
                add.accept(creeper);
                world.playSound(null, spot, SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 0.6f, 0.6f);
            }
        }
    }

    /** The side with the fewest holes and sappers on the way that still has room, or -1. */
    private int freestSide(SiegeConfig cfg, Random random) {
        int best = -1;
        int bestUsed = Integer.MAX_VALUE;
        int start = random.nextInt(4);
        for (int i = 0; i < 4; i++) {
            int side = (start + i) % 4;
            int used = breached.getOrDefault(side, 0) + sapperOnWay.getOrDefault(side, 0);
            if (used < Math.max(1, cfg.sapperHolesPerSide) && used < bestUsed) {
                best = side;
                bestUsed = used;
            }
        }
        return best;
    }

    /** 0 east, 1 south, 2 west, 3 north of the core. */
    static int sideOf(BlockPos anchor, Vec3 at) {
        double a = Math.atan2(at.z - (anchor.getZ() + 0.5), at.x - (anchor.getX() + 0.5));
        int side = (int) Math.round(a / (Math.PI / 2));
        return Math.floorMod(side, 4);
    }

    /**
     * Just outside the wall on that side: where it comes closest to the core, the next sappers of
     * the side at least 5 blocks away from the earlier ones (else a random spot of the side).
     */
    private static Vec3 weakSpot(BlockPos anchor, BaseScan base, int side, List<Vec3> taken, Random random) {
        double center = side * Math.PI / 2;
        double best = Double.MAX_VALUE;
        Vec3 bestSpot = null;
        for (int i = -8; i <= 8; i++) {
            double a = center + i * Math.PI / 36;   // +-40 degrees
            double edge = base.edgeTowards(anchor.getX() + 0.5 + Math.cos(a) * 8, anchor.getZ() + 0.5 + Math.sin(a) * 8);
            Vec3 spot = new Vec3(anchor.getX() + 0.5 + Math.cos(a) * (edge + 3.0), anchor.getY(),
                    anchor.getZ() + 0.5 + Math.sin(a) * (edge + 3.0));
            boolean free = true;
            for (Vec3 t : taken) {
                if (t.distanceToSqr(spot.x, t.y, spot.z) < 25.0) {
                    free = false;
                    break;
                }
            }
            if (free && edge < best) {
                best = edge;
                bestSpot = spot;
            }
        }
        if (bestSpot != null) {
            return bestSpot;
        }
        double a = center + (random.nextDouble() - 0.5) * Math.PI * 4 / 9;
        double edge = base.edgeTowards(anchor.getX() + 0.5 + Math.cos(a) * 8, anchor.getZ() + 0.5 + Math.sin(a) * 8);
        return new Vec3(anchor.getX() + 0.5 + Math.cos(a) * (edge + 3.0), anchor.getY(), anchor.getZ() + 0.5 + Math.sin(a) * (edge + 3.0));
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
            sapperOnWay.computeIfPresent(job.side, (k, v) -> v > 1 ? v - 1 : null);
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
                // Only where the way up is free (Simon, 27 October 2026: no ladders into an overhang).
                if (!pickColumn(world, job, now)) {
                    finish(mob.getUUID(), job, mob);   // nowhere to lean it: just fight
                    return;
                }
                job.next = now;
                return;
            }
            if (mob.getNavigation().isDone() || now % 20 == 0) {
                mob.getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 1.0);
            }
            return;
        }
        if (job.walkSince >= 0) {
            // To the free column a few blocks along the wall.
            Vec3 goal = job.approach;
            if (mob.position().distanceToSqr(goal.x, mob.getY(), goal.z) > 1.6 && now - job.walkSince < 100) {
                if (mob.getNavigation().isDone() || now % 20 == 0) {
                    mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
                }
                return;
            }
            job.walkSince = -1;
            if (climbHeight(world, job.wall, job.face) < 0) {
                finish(mob.getUUID(), job, mob);   // blocked in the meantime
                return;
            }
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
        if (!wallThere || job.rung >= job.height || job.rung >= HIGHEST_LADDER || !ladder.canSurvive(world, rungPos)
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

    /**
     * The column the ladder goes into: straight ahead if the way up is free there, else the
     * nearest free one up to 3 blocks along the wall (the carrier walks there first). False when
     * there is none.
     */
    private static boolean pickColumn(ServerLevel world, Job job, long now) {
        Direction across = job.face.getClockWise();
        for (int o : new int[] {0, 1, -1, 2, -2, 3, -3}) {
            BlockPos wall = job.wall.relative(across, o);
            BlockPos column = wall.relative(job.face);
            if (o != 0 && !solid(world, column.below()) && !world.getBlockState(column.below()).is(Blocks.LADDER)) {
                continue;   // no ground to start from
            }
            int h = climbHeight(world, wall, job.face);
            if (h > 0) {
                job.wall = wall;
                job.height = h;
                job.approach = Vec3.atBottomCenterOf(column);
                job.walkSince = o == 0 ? -1 : now;
                return true;
            }
        }
        return false;
    }

    /**
     * Rungs a ladder in front of {@code wall} needs to reach the top, or -1 if the way up is
     * blocked: a block in the column above (an overhang, a roof, a torch), the top closed off,
     * or the wall higher than a ladder goes.
     */
    public static int climbHeight(ServerLevel world, BlockPos wall, Direction face) {
        int h = 0;
        while (h <= HIGHEST_LADDER && world.getBlockState(wall.above(h)).isFaceSturdy(world, wall.above(h), face)) {
            h++;
        }
        if (h == 0 || h > HIGHEST_LADDER) {
            return -1;
        }
        BlockPos column = wall.relative(face);
        for (int i = 0; i <= h + 1; i++) {   // the ladder itself, then room for feet and head at the top
            BlockState state = world.getBlockState(column.above(i));
            if (!state.is(Blocks.LADDER) && !(state.isAir() || state.canBeReplaced())) {
                return -1;
            }
        }
        // Off the ladder onto the wall: free above the top (a slab on top is fine with room above it).
        BlockPos top = wall.above(h);
        if (solid(world, top.above()) || solid(world, top) && solid(world, top.above(2))) {
            return -1;
        }
        return h;
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

    /**
     * The sapper blows: a crater into the wall (a ball of sapperBlastRadius blocks, +1 when powered,
     * at least the old 3x3 hole up to 3 deep); its blast hurts like a creeper's. Everything comes back
     * after the night; the core, traps, containers and protected blocks stay.
     */
    private void blow(ServerLevel world, Mob mob, Job job) {
        Vec3 at = mob.position();
        SiegeConfig cfg = SiegeManager.get() == null ? null : SiegeManager.get().config();
        boolean powered = mob instanceof Creeper c && c.isPowered();
        // The blast itself (damage and knockback), but no block damage of its own.
        world.explode(mob, at.x, at.y + 0.5, at.z, powered ? 4.5f : 3.0f, Level.ExplosionInteraction.NONE);
        double radius = (cfg == null ? 3.0 : Math.max(1.5, cfg.sapperBlastRadius)) + (powered ? 1.0 : 0.0);
        Direction in = job.face.getOpposite();
        Direction across = in.getClockWise();
        // The middle of the crater: one block into the wall, a block above the ground.
        BlockPos middle = job.wall.relative(in, 1).above(1);
        int r = (int) Math.ceil(radius);
        int removed = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = middle.offset(dx, dy, dz);
                    // The old hole (3 wide, 3 high, 3 deep) always; around it the ball.
                    int depth = (pos.getX() - job.wall.getX()) * in.getStepX() + (pos.getZ() - job.wall.getZ()) * in.getStepZ();
                    int side = (pos.getX() - job.wall.getX()) * across.getStepX() + (pos.getZ() - job.wall.getZ()) * across.getStepZ();
                    int h = pos.getY() - job.wall.getY();
                    boolean hole = depth >= 0 && depth < 3 && Math.abs(side) <= 1 && h >= 0 && h <= 2;
                    double d2 = dx * dx + dy * dy * 1.3 + dz * dz;
                    if (!hole && (d2 > radius * radius || h < -1)) {
                        continue;   // a ball, at most one block into the ground
                    }
                    if (!hole && d2 > (radius - 0.8) * (radius - 0.8) && world.getRandom().nextFloat() < 0.4f) {
                        continue;   // a ragged edge
                    }
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (ModContent.isCore(state) || ModContent.trapOf(state) != null
                            || state.is(Blocks.BEDROCK) || state.hasBlockEntity()
                            || cfg != null && BlockGuard.isProtected(Demolition.blockId(state), cfg)) {
                        continue;
                    }
                    NightDamage.record(world, pos, state);   // back after the night
                    if (world.destroyBlock(pos, false)) {
                        removed++;
                    }
                }
            }
        }
        breached.merge(job.side, 1, Integer::sum);
        NightSiegeMod.LOGGER.info("[Night Siege] Sprengcreeper: Krater in der Mauer (Seite {}), {} Blöcke", job.side, removed);
        finish(mob.getUUID(), job, null);
        mob.discard();
    }
}
