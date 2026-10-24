package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Siege monsters use ladders, vines and scaffolding to get over walls. Minecraft's
 * own path finding never plans a way up a ladder, so a monster that cannot reach
 * its target looks for a climbable column nearby, walks to its foot and climbs.
 * Spiders climb anyway and big monsters do not fit.
 */
public final class LadderClimb {

    /** How far around a stuck monster a ladder is looked for. */
    private static final int SEARCH = 12;
    /** Ticks between two searches of the same monster. */
    private static final int SEARCH_EVERY = 40;

    /** Ticks a monster may walk towards one ladder before it gives up on it. */
    private static final int GIVE_UP = 200;

    private final Map<UUID, BlockPos> goals = new HashMap<>();
    private final Map<UUID, Long> goalSince = new HashMap<>();
    private final Map<UUID, Long> lastSearch = new HashMap<>();
    /** When a monster last stood on a ladder: for a moment after, it goes on over the wall. */
    private final Map<UUID, Long> climbedAt = new HashMap<>();
    /** Ticks after the climb in which the monster heads on to its goal instead of looking for ladders. */
    private static final int OVER_THE_TOP = 60;

    public void reset() {
        goals.clear();
        goalSince.clear();
        lastSearch.clear();
        climbedAt.clear();
    }

    public void forget(UUID id) {
        goals.remove(id);
        goalSince.remove(id);
        lastSearch.remove(id);
        climbedAt.remove(id);
    }

    /** Called every other tick for the night's monsters. */
    public void tick(ServerLevel world, Collection<UUID> mobs, BlockPos anchor, long now) {
        for (UUID id : mobs) {
            Entity entity = world.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isPassenger() || mob.isVehicle()
                    || mob.getNavigation() instanceof WallClimberNavigation || mob.getBbWidth() > 1.2f
                    || mob.isNoGravity()) {
                continue;
            }
            if (mob.onClimbable()) {
                // On the way up: the walk to the ladder is over.
                goals.remove(id);
                goalSince.remove(id);
                climbedAt.put(id, now);
                climb(world, mob, anchor);
                continue;
            }
            LivingEntity target = mob.getTarget();
            BlockPos goal = target != null ? target.blockPosition() : anchor;
            Long climbed = climbedAt.get(id);
            if (climbed != null && now - climbed < OVER_THE_TOP) {
                overTheTop(mob, goal);
                continue;
            }
            BlockPos foot = goals.get(id);
            if (foot != null && (!isClimbable(world.getBlockState(foot))
                    || now - goalSince.getOrDefault(id, now) > GIVE_UP)) {
                goals.remove(id);
                goalSince.remove(id);
                foot = null;
            }
            if (foot == null) {
                if (!stuck(mob, goal)) {
                    continue;
                }
                long last = lastSearch.getOrDefault(id, Long.MIN_VALUE / 2);
                if (now - last < SEARCH_EVERY) {
                    continue;
                }
                lastSearch.put(id, now);
                foot = findLadder(world, mob.blockPosition(), goal);
                if (foot == null) {
                    continue;
                }
                goals.put(id, foot);
                goalSince.put(id, now);
            }
            double fx = foot.getX() + 0.5;
            double fz = foot.getZ() + 0.5;
            double dx = fx - mob.getX();
            double dz = fz - mob.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 <= 6.25 && Math.abs(mob.getY() - foot.getY()) < 1.5) {
                // Close to the foot: walk straight into the ladder (the path finder
                // stops in front of it), the climb takes over from there.
                mob.getNavigation().stop();
                double d = Math.sqrt(d2);
                if (d > 0.05) {
                    double step = Math.min(0.24, d);
                    mob.move(MoverType.SELF, new Vec3(dx / d * step, 0.0, dz / d * step));
                }
                mob.getMoveControl().setWantedPosition(fx, foot.getY(), fz, 1.0);
                mob.getLookControl().setLookAt(fx, foot.getY() + 1.5, fz);
            } else if (!foot.equals(mob.getNavigation().getTargetPos()) || mob.getNavigation().isDone()) {
                // The monster's own AI keeps steering to its target, so steer back.
                mob.getNavigation().moveTo(fx, foot.getY(), fz, 1.0);
            }
        }
    }

    /** Up on the wall: on towards the goal, and if no path leads there, simply off the edge. */
    private static void overTheTop(Mob mob, BlockPos goal) {
        PathNavigation nav = mob.getNavigation();
        Path path = nav.getPath();
        if (path != null && path.canReach() && !nav.isDone()) {
            return;
        }
        double dx = goal.getX() + 0.5 - mob.getX();
        double dz = goal.getZ() + 0.5 - mob.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0) {
            return;
        }
        if (nav.moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0)
                && nav.getPath() != null && nav.getPath().canReach()) {
            return;
        }
        nav.stop();
        mob.getMoveControl().setWantedPosition(mob.getX() + dx / len * 2.0, mob.getY(), mob.getZ() + dz / len * 2.0, 1.0);
    }

    /** True when the monster's path does not reach its goal (a wall is in the way). */
    private static boolean stuck(Mob mob, BlockPos goal) {
        PathNavigation nav = mob.getNavigation();
        Path path = nav.getPath();
        if (path != null && path.canReach() && !nav.isDone()) {
            return false;
        }
        // Standing right at the goal is not being stuck.
        return mob.blockPosition().distSqr(goal) > 9.0;
    }

    /** Inside a ladder: up it goes, and at the top over onto the wall. */
    private static void climb(ServerLevel world, Mob mob, BlockPos anchor) {
        BlockPos at = mob.blockPosition();
        BlockState here = world.getBlockState(at);
        Vec3 v = mob.getDeltaMovement();
        Direction toWall = null;
        if (here.getBlock() instanceof LadderBlock) {
            toWall = here.getValue(LadderBlock.FACING).getOpposite();
        }
        boolean top = !isClimbable(world.getBlockState(at.above()));
        double push = top ? 0.18 : 0.05;
        double px = 0;
        double pz = 0;
        if (toWall != null) {
            px = toWall.getStepX() * push;
            pz = toWall.getStepZ() * push;
        } else {
            // Vines and scaffolding: lean towards the core.
            double dx = anchor.getX() + 0.5 - mob.getX();
            double dz = anchor.getZ() + 0.5 - mob.getZ();
            double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
            px = dx / len * push;
            pz = dz / len * push;
        }
        mob.setDeltaMovement(v.x * 0.5 + px, Math.max(v.y, 0.22), v.z * 0.5 + pz);
    }

    static boolean isClimbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE);
    }

    /**
     * The foot of a climbable column (at least two blocks high) near the monster,
     * the one that lies best on the way to the goal.
     */
    static BlockPos findLadder(ServerLevel world, BlockPos from, BlockPos goal) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    pos.set(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
                    if (!isClimbable(world.getBlockState(pos)) || isClimbable(world.getBlockState(pos.below()))) {
                        continue;   // only the foot of a column
                    }
                    if (!world.getBlockState(pos.below()).blocksMotion()) {
                        continue;   // a ladder hanging in the air cannot be reached
                    }
                    if (!isClimbable(world.getBlockState(pos.above()))) {
                        continue;   // a single rung does not lead anywhere
                    }
                    double score = Math.sqrt(pos.distSqr(from)) + Math.sqrt(pos.distSqr(goal));
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }
}
