package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Siege monsters use ladders, vines and scaffolding to get over walls. Minecraft's
 * own path finding never plans a way up a ladder, so a monster that cannot reach
 * its target looks for a climbable column nearby, walks to its foot and climbs.
 * Spiders climb anyway and big monsters do not fit.
 *
 * <p>While a monster is on its way to a ladder, on it, or just over the wall, a goal
 * of the highest priority holds its movement: otherwise its attack goal keeps
 * re-planning the (blocked) way to its target and drags it away from the ladder.
 *
 * <p>Only a monster that really finds no way to its goal looks for a ladder, and only
 * for one whose foot it can walk to. Before (Simon's playtest, 25 October 2026)
 * monsters were pulled to any ladder close by, also to the ladders on the inner side
 * of the walls, and pressed against the wall in front of them.
 */
public final class LadderClimb {

    /** How far around a stuck monster a ladder is looked for. */
    private static final int SEARCH = 20;
    /** How far up or down from the monster the foot of a ladder may be. */
    private static final int SEARCH_UP = 4;
    /** Off only for the gametest that shows the old behaviour. */
    public static boolean holdGoals = true;
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
    /** Monsters this class steers right now (the hold goal lets them be). */
    private final Set<UUID> holding = new HashSet<>();
    /** Monsters that already carry the hold goal. */
    private final Set<UUID> equipped = new HashSet<>();
    /** The ladder a monster last gave up on: it does not pick it again straight away. */
    private final Map<UUID, BlockPos> gaveUp = new HashMap<>();

    public void reset() {
        goals.clear();
        goalSince.clear();
        lastSearch.clear();
        climbedAt.clear();
        holding.clear();
        equipped.clear();
        gaveUp.clear();
    }

    public void forget(UUID id) {
        goals.remove(id);
        goalSince.remove(id);
        lastSearch.remove(id);
        climbedAt.remove(id);
        holding.remove(id);
        equipped.remove(id);
        gaveUp.remove(id);
    }

    /** True while the monster is on its way up a ladder (for the gametest). */
    public boolean busy(UUID id) {
        return holding.contains(id);
    }

    /** Gives the monster the goal that holds its movement while it climbs, once. */
    private void equip(Mob mob) {
        if (!holdGoals || !equipped.add(mob.getUUID())) {
            return;
        }
        for (var wrapped : mob.getGoalSelector().getAvailableGoals()) {
            if (wrapped.getGoal() instanceof HoldGoal) {
                return;
            }
        }
        mob.getGoalSelector().addGoal(0, new HoldGoal(this, mob.getUUID()));
    }

    /** Takes the movement away from the monster's other goals while this class steers it. */
    private static final class HoldGoal extends Goal {
        private final LadderClimb owner;
        private final UUID id;

        HoldGoal(LadderClimb owner, UUID id) {
            this.owner = owner;
            this.id = id;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return holdGoals && owner.holding.contains(id);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }
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
            equip(mob);
            holding.remove(id);
            if (mob.onClimbable()) {
                holding.add(id);
                // On the way up: the walk to the ladder is over.
                goals.remove(id);
                goalSince.remove(id);
                climbedAt.put(id, now);
                climb(world, mob, anchor);
                continue;
            }
            LivingEntity target = mob.getTarget();
            BlockPos goal = target != null ? target.blockPosition() : anchor;
            // The target came into reach after all (no wall in between): let the monster fight.
            boolean inReach = target != null && mob.distanceToSqr(target) < 16.0
                    && Math.abs(mob.getY() - target.getY()) < 2.0 && mob.getSensing().hasLineOfSight(target);
            Long climbed = climbedAt.get(id);
            if (climbed != null && now - climbed < OVER_THE_TOP && !inReach) {
                holding.add(id);
                overTheTop(mob, goal);
                continue;
            }
            BlockPos foot = goals.get(id);
            if (foot != null && (!isClimbable(world.getBlockState(foot)) || inReach
                    || now - goalSince.getOrDefault(id, now) > GIVE_UP)) {
                if (now - goalSince.getOrDefault(id, now) > GIVE_UP) {
                    gaveUp.put(id, foot);
                }
                goals.remove(id);
                goalSince.remove(id);
                foot = null;
            }
            if (foot == null) {
                if (inReach || !stuck(mob, goal)) {
                    continue;
                }
                long last = lastSearch.getOrDefault(id, Long.MIN_VALUE / 2);
                if (now - last < SEARCH_EVERY) {
                    continue;
                }
                lastSearch.put(id, now);
                if (canWalkTo(mob, goal)) {
                    continue;   // there is a way after all: no ladder needed
                }
                foot = findLadder(world, mob, goal, gaveUp.get(id));
                if (foot == null) {
                    continue;
                }
                goals.put(id, foot);
                goalSince.put(id, now);
            }
            holding.add(id);
            double fx = foot.getX() + 0.5;
            double fz = foot.getZ() + 0.5;
            double dx = fx - mob.getX();
            double dz = fz - mob.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 <= 6.25 && Math.abs(mob.getY() - foot.getY()) < 1.5 && clearWalk(world, mob, foot)) {
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

    /** True when the monster's path finding finds a full way to the spot. */
    private static boolean canWalkTo(Mob mob, BlockPos spot) {
        Path path = mob.getNavigation().createPath(spot, 0);
        return path != null && path.canReach();
    }

    /** True when nothing solid stands between the monster's feet and the ladder's foot. */
    private static boolean clearWalk(ServerLevel world, Mob mob, BlockPos foot) {
        Vec3 from = mob.position().add(0, 0.5, 0);
        Vec3 to = Vec3.atBottomCenterOf(foot).add(0, 0.5, 0);
        Vec3 step = to.subtract(from);
        int steps = (int) Math.ceil(step.length() * 3.0);
        for (int i = 1; i < steps; i++) {
            BlockPos at = BlockPos.containing(from.add(step.scale(i / (double) steps)));
            if (at.equals(foot)) {
                continue;
            }
            if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The foot of a climbable column (at least two blocks high) near the monster that it
     * can walk to, the one that lies best on the way to the goal. Ladders behind a wall
     * (on its inner side, in a tower) do not count.
     */
    static BlockPos findLadder(ServerLevel world, Mob mob, BlockPos goal, BlockPos skip) {
        BlockPos from = mob.blockPosition();
        java.util.List<BlockPos> feet = new java.util.ArrayList<>();
        java.util.List<Double> scores = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                for (int dy = -SEARCH_UP; dy <= SEARCH_UP; dy++) {
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
                    if (pos.equals(skip)) {
                        continue;   // given up on a moment ago
                    }
                    feet.add(pos.immutable());
                    scores.add(Math.sqrt(pos.distSqr(from)) + Math.sqrt(pos.distSqr(goal)));
                }
            }
        }
        // The best few by the way they lie; the first one the monster can walk to wins.
        Integer[] order = new Integer[feet.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(scores::get));
        for (int i = 0; i < Math.min(4, order.length); i++) {
            BlockPos foot = feet.get(order[i]);
            if (canWalkTo(mob, foot)) {
                return foot;
            }
        }
        return null;
    }
}
