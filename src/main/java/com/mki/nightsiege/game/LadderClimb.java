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
 *
 * <p>Simon's playtest (28 October 2026): monsters got stuck at ladders with a block beside
 * them and at ladders ending one block above the ground. A monster is now kept to the middle
 * of the ladder (half beside it, it bumped its head on a block next to the ladder or into the
 * battlements beside the way over the wall); a ladder whose foot hangs one block above the
 * ground counts too (the monster jumps up to the first rung); a monster whose goal lies on its
 * own side of the ladder's wall (on a ladder inside the base) climbs down instead of being
 * pushed up and back onto the wall forever; and one that makes no headway on a ladder lets go.
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
    /**
     * Until when that ladder stays barred for it (Simon 30.10., 16:12: before, a ladder given up once
     * stayed barred for the whole night, while the raid kept sending the monster to its foot).
     */
    private final Map<UUID, Long> gaveUpUntil = new HashMap<>();
    /** Ticks a ladder given up on stays barred for that monster. */
    private static final int BARRED = 300;
    /**
     * One monster at a time gets onto a ladder (Simon 30.10., 16:12: "mehrere Monster sammeln sich
     * unten an der Leiter und keins kommt hoch"): the foot → the monster whose turn it is, and since when.
     * The others wait a few blocks out from the wall, so they do not push the climber off the rungs.
     */
    private final Map<BlockPos, UUID> turn = new HashMap<>();
    private final Map<BlockPos, Long> turnSince = new HashMap<>();
    /** Ticks a monster may take with its turn before the next one gets it. */
    private static final int TURN_MAX = 120;
    /** Height above the foot from which the next monster may get on. */
    private static final double NEXT_ON = 2.5;
    /** How far out from the wall the others wait. */
    private static final int WAIT_OUT = 3;
    /** Since when a monster waits for its turn (it gives up after a while and tries another way). */
    private final Map<UUID, Long> waitSince = new HashMap<>();
    private static final int WAIT_MAX = 600;
    /** On a ladder: the best height reached (negated on the way down), when, and the direction (1 up). */
    private final Map<UUID, double[]> progress = new HashMap<>();
    /** Monsters that let go of a ladder they made no headway on: left to their own AI until then. */
    private final Map<UUID, Long> released = new HashMap<>();
    /** Ticks on a ladder without headway before the monster lets go. */
    private static final int STALL = 60;
    /** Ticks a monster that let go is left to its own AI. */
    private static final int RELEASE = 100;

    /** What the ladders did in this night (for the log and the gametest, 30 October 2026). */
    public static final class Stats {
        public int searches;
        public int searchesWithSkip;
        public int found;
        public int boarded;
        public int topped;
        public int gaveUpWalk;
        public int letGo;
        public int waited;
        public int pushedOff;

        @Override
        public String toString() {
            return "Suchen " + searches + " (mit gesperrter Leiter " + searchesWithSkip + "), gefunden " + found
                    + ", aufgestiegen " + boarded + ", oben " + topped + ", Weg aufgegeben " + gaveUpWalk
                    + ", losgelassen " + letGo + ", gewartet " + waited + ", unten abgerutscht " + pushedOff;
        }
    }

    private Stats stats = new Stats();
    /** Monsters that reached the top on their current climb (counted once). */
    private final Set<UUID> toppedNow = new HashSet<>();

    public Stats stats() {
        return stats;
    }

    /** One monster's ladder state, for the gametest. */
    public String describe(UUID id, long now) {
        Long free = released.get(id);
        Long climbed = climbedAt.get(id);
        BlockPos g = goals.get(id);
        return "ziel=" + (g != null ? g.toShortString() : "-")
                + " dran=" + (g != null && id.equals(turn.get(g)))
                + " wartet_seit=" + (waitSince.containsKey(id) ? String.valueOf(now - waitSince.get(id)) : "-")
                + " gesperrt=" + (gaveUp.containsKey(id) ? gaveUp.get(id).toShortString() + " noch "
                + (gaveUpUntil.getOrDefault(id, now) - now) : "-")
                + " frei_in=" + (free == null ? "-" : String.valueOf(free - now))
                + " geklettert_vor=" + (climbed == null ? "-" : String.valueOf(now - climbed))
                + " gesteuert=" + holding.contains(id);
    }

    public void reset() {
        stats = new Stats();
        toppedNow.clear();
        gaveUpUntil.clear();
        turn.clear();
        turnSince.clear();
        waitSince.clear();
        goals.clear();
        goalSince.clear();
        lastSearch.clear();
        climbedAt.clear();
        holding.clear();
        equipped.clear();
        gaveUp.clear();
        progress.clear();
        released.clear();
    }

    public void forget(UUID id) {
        toppedNow.remove(id);
        gaveUpUntil.remove(id);
        waitSince.remove(id);
        turn.values().removeIf(id::equals);
        goals.remove(id);
        goalSince.remove(id);
        lastSearch.remove(id);
        climbedAt.remove(id);
        holding.remove(id);
        equipped.remove(id);
        gaveUp.remove(id);
        progress.remove(id);
        released.remove(id);
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
            Long free = released.get(id);
            if (free != null) {
                if (now < free) {
                    continue;   // let go of a ladder a moment ago: its own AI for a while
                }
                released.remove(id);
            }
            LivingEntity target = mob.getTarget();
            BlockPos goal = target != null ? target.blockPosition() : anchor;
            if (mob.onClimbable()) {
                boolean up = upward(world, mob, goal);
                if (!up && mob.onGround()) {
                    // At the foot of a ladder it does not want up (inside the base, or down again):
                    // its own AI walks on.
                    progress.remove(id);
                    continue;
                }
                if (stalled(id, mob, up, now)) {
                    letGo(world, mob, anchor, now);
                    continue;
                }
                holding.add(id);
                waitSince.remove(id);
                Long before = climbedAt.get(id);
                if (before == null || now - before > OVER_THE_TOP) {
                    stats.boarded++;
                    toppedNow.remove(id);
                }
                if (up) {
                    // Keep the ladder as its goal while it climbs: pushed off near the bottom, it
                    // walks straight back in (before, it took itself for over the wall and pressed
                    // against the wall beside the ladder).
                    BlockPos foot = goals.get(id);
                    if (foot == null || foot.getX() != mob.getBlockX() || foot.getZ() != mob.getBlockZ()) {
                        foot = footOf(world, mob.blockPosition());
                        goals.put(id, foot);
                    }
                    goalSince.put(id, now);
                    if (mob.getY() - foot.getY() >= NEXT_ON) {
                        endTurn(foot, id);   // up a bit: the next one may get on
                    }
                    if (!isClimbable(world.getBlockState(mob.blockPosition().above())) && toppedNow.add(id)) {
                        stats.topped++;
                        goals.remove(id);
                        goalSince.remove(id);
                        endTurn(foot, id);
                    }
                } else {
                    goals.remove(id);
                    goalSince.remove(id);
                }
                climbedAt.put(id, now);
                climb(world, mob, anchor, up);
                continue;
            }
            progress.remove(id);
            // The target came into reach after all (no wall in between): let the monster fight.
            boolean inReach = target != null && mob.distanceToSqr(target) < 16.0
                    && Math.abs(mob.getY() - target.getY()) < 2.0 && mob.getSensing().hasLineOfSight(target);
            Long climbed = climbedAt.get(id);
            if (climbed != null && now - climbed < OVER_THE_TOP && !inReach) {
                if (toppedNow.contains(id)) {
                    holding.add(id);
                    overTheTop(mob, goal);
                    continue;
                }
                // Off the ladder near the bottom (pushed off, or slipped): not over the wall, back to the ladder.
                climbedAt.remove(id);
                stats.pushedOff++;
            }
            BlockPos foot = goals.get(id);
            if (foot != null && (!isClimbable(world.getBlockState(foot)) || inReach
                    || now - goalSince.getOrDefault(id, now) > GIVE_UP)) {
                if (now - goalSince.getOrDefault(id, now) > GIVE_UP) {
                    bar(id, foot, now);
                    stats.gaveUpWalk++;
                }
                endTurn(foot, id);
                goals.remove(id);
                goalSince.remove(id);
                waitSince.remove(id);
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
                stats.searches++;
                BlockPos skip = gaveUp.get(id);
                if (skip != null && now >= gaveUpUntil.getOrDefault(id, 0L)) {
                    gaveUp.remove(id);   // barred long enough: it may try that ladder again
                    gaveUpUntil.remove(id);
                    skip = null;
                }
                if (skip != null) {
                    stats.searchesWithSkip++;
                }
                foot = findLadder(world, mob, goal, skip);
                if (foot == null) {
                    continue;
                }
                stats.found++;
                goals.put(id, foot);
                goalSince.put(id, now);
            }
            holding.add(id);
            // Where the monster stands to get on: the foot itself, or the ground under a ladder
            // that ends one block above it.
            BlockPos stand = standOf(world, foot);
            double fx = foot.getX() + 0.5;
            double fz = foot.getZ() + 0.5;
            double dx = fx - mob.getX();
            double dz = fz - mob.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 <= 25.0 && !myTurn(world, foot, id, now)) {
                // Somebody else is getting on: wait a few blocks out from the wall.
                Long since = waitSince.putIfAbsent(id, now);
                if (since == null) {
                    stats.waited++;
                } else if (now - since > WAIT_MAX) {
                    bar(id, foot, now);   // waited long enough: another way (another ladder, or the raid's)
                    goals.remove(id);
                    goalSince.remove(id);
                    waitSince.remove(id);
                    holding.remove(id);
                    continue;
                }
                goalSince.put(id, now);   // waiting is no giving up
                BlockPos wait = waitSpot(world, foot, stand);
                double wx = wait.getX() + 0.5 - mob.getX();
                double wz = wait.getZ() + 0.5 - mob.getZ();
                if (wx * wx + wz * wz > 2.25) {
                    if (!wait.equals(mob.getNavigation().getTargetPos()) || mob.getNavigation().isDone()) {
                        mob.getNavigation().moveTo(wait.getX() + 0.5, wait.getY(), wait.getZ() + 0.5, 1.0);
                    }
                } else {
                    mob.getNavigation().stop();
                }
                mob.getLookControl().setLookAt(fx, foot.getY() + 1.5, fz);
                continue;
            }
            waitSince.remove(id);
            if (d2 <= 6.25 && Math.abs(mob.getY() - stand.getY()) < 1.5 && clearWalk(world, mob, stand)) {
                // Close to the foot: walk straight into the ladder (the path finder
                // stops in front of it), the climb takes over from there.
                mob.getNavigation().stop();
                double d = Math.sqrt(d2);
                if (d > 0.05) {
                    double step = Math.min(0.24, d);
                    mob.move(MoverType.SELF, new Vec3(dx / d * step, 0.0, dz / d * step));
                }
                if (!stand.equals(foot) && d < 0.3 && mob.onGround()) {
                    // Right under a ladder that ends above the ground: up to its first rung.
                    Direction toWall = wallSide(world.getBlockState(foot));
                    double px = toWall == null ? 0.0 : toWall.getStepX() * 0.05;
                    double pz = toWall == null ? 0.0 : toWall.getStepZ() * 0.05;
                    mob.setDeltaMovement(px, 0.5, pz);
                }
                mob.getMoveControl().setWantedPosition(fx, stand.getY(), fz, 1.0);
                mob.getLookControl().setLookAt(fx, foot.getY() + 1.5, fz);
            } else if (!stand.equals(mob.getNavigation().getTargetPos()) || mob.getNavigation().isDone()) {
                // The monster's own AI keeps steering to its target, so steer back.
                mob.getNavigation().moveTo(fx, stand.getY(), fz, 1.0);
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

    /**
     * Inside a ladder: up it goes, and at the top over onto the wall; or, when the goal lies on
     * this side of the ladder's wall, down it goes.
     */
    private static void climb(ServerLevel world, Mob mob, BlockPos anchor, boolean up) {
        BlockPos at = mob.blockPosition();
        BlockState here = world.getBlockState(at);
        Vec3 v = mob.getDeltaMovement();
        Direction toWall = wallSide(here);
        if (toWall == null) {
            // Vines and scaffolding: up, leaning towards the core.
            boolean top = !isClimbable(world.getBlockState(at.above()));
            double push = top ? 0.18 : 0.05;
            double dx = anchor.getX() + 0.5 - mob.getX();
            double dz = anchor.getZ() + 0.5 - mob.getZ();
            double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
            mob.setDeltaMovement(v.x * 0.5 + dx / len * push, Math.max(v.y, 0.22), v.z * 0.5 + dz / len * push);
            return;
        }
        Direction across = toWall.getClockWise();
        double ox = mob.getX() - (at.getX() + 0.5);
        double oz = mob.getZ() - (at.getZ() + 0.5);
        // Keep to the middle of the ladder: half beside it, a monster bumps its head on a block
        // next to the ladder, or at the top into the battlements beside the way over the wall.
        double side = ox * across.getStepX() + oz * across.getStepZ();
        double sideways = clamp(-side * 0.45, 0.1);
        double towards;
        double vy;
        if (up) {
            boolean top = !isClimbable(world.getBlockState(at.above()));
            double off = ox * toWall.getStepX() + oz * toWall.getStepZ();
            if (!top && (Math.abs(side) > 0.2 || off < -0.2)) {
                // Others below push a climber off the rungs (Simon 30.10., 16:12): back onto them.
                mob.setPos(at.getX() + 0.5 + toWall.getStepX() * 0.1, mob.getY(), at.getZ() + 0.5 + toWall.getStepZ() * 0.1);
                v = new Vec3(0.0, v.y, 0.0);
                sideways = 0.0;
            }
            double vn = v.x * toWall.getStepX() + v.z * toWall.getStepZ();
            towards = vn * 0.5 + (top ? 0.18 : 0.05);
            vy = Math.max(v.y, 0.22);
        } else {
            // Down: away from the wall to the middle (pressing against the wall or a block beside
            // the ladder makes a monster climb up) and no steering of its own until the bottom.
            double off = ox * toWall.getStepX() + oz * toWall.getStepZ();
            towards = clamp(-off * 0.45, 0.1);
            vy = Math.min(v.y, -0.15);
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0);
        }
        mob.setDeltaMovement(toWall.getStepX() * towards + across.getStepX() * sideways, vy,
                toWall.getStepZ() * towards + across.getStepZ() * sideways);
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    /** The direction from a ladder to the wall it hangs on, or null for vines and scaffolding. */
    private static Direction wallSide(BlockState state) {
        return state.getBlock() instanceof LadderBlock ? state.getValue(LadderBlock.FACING).getOpposite() : null;
    }

    /**
     * True when the monster on a ladder should go up: its goal lies behind the wall the ladder
     * hangs on (or straight above). On a ladder inside the base the goal lies on the monster's
     * own side: down. Vines and scaffolding always go up.
     */
    private static boolean upward(ServerLevel world, Mob mob, BlockPos goal) {
        Direction toWall = wallSide(world.getBlockState(mob.blockPosition()));
        if (toWall == null) {
            return true;
        }
        double along = (goal.getX() + 0.5 - mob.getX()) * toWall.getStepX() + (goal.getZ() + 0.5 - mob.getZ()) * toWall.getStepZ();
        if (along > 0.5) {
            return true;
        }
        if (along < -0.5) {
            return false;
        }
        return goal.getY() > mob.getY() + 1.0;   // in line with the ladder: up only to a goal clearly above
    }

    /** True when the monster made no headway on its ladder for a while (a block above it, the top closed off). */
    private boolean stalled(UUID id, Mob mob, boolean up, long now) {
        double h = up ? mob.getY() : -mob.getY();
        double dir = up ? 1 : 0;
        double[] p = progress.get(id);
        if (p == null || p[2] != dir || h > p[0] + 0.4) {
            progress.put(id, new double[] {h, now, dir});
            return false;
        }
        return now - (long) p[1] > STALL;
    }

    /** Off the ladder it is stuck on: away from the wall, that ladder not again straight away. */
    private void letGo(ServerLevel world, Mob mob, BlockPos anchor, long now) {
        UUID id = mob.getUUID();
        stats.letGo++;
        BlockPos foot = mob.blockPosition();
        for (int i = 0; i < 32 && isClimbable(world.getBlockState(foot.below())); i++) {
            foot = foot.below();
        }
        bar(id, foot, now);
        endTurn(foot, id);
        progress.remove(id);
        climbedAt.remove(id);
        goals.remove(id);
        goalSince.remove(id);
        released.put(id, now + RELEASE);
        Direction toWall = wallSide(world.getBlockState(mob.blockPosition()));
        double ax;
        double az;
        if (toWall != null) {
            ax = -toWall.getStepX();
            az = -toWall.getStepZ();
        } else {
            double dx = mob.getX() - (anchor.getX() + 0.5);
            double dz = mob.getZ() - (anchor.getZ() + 0.5);
            double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
            ax = dx / len;
            az = dz / len;
        }
        mob.getNavigation().stop();
        mob.setDeltaMovement(ax * 0.3, 0.1, az * 0.3);
    }

    /** Bars a ladder for a monster for a while. */
    private void bar(UUID id, BlockPos foot, long now) {
        gaveUp.put(id, foot);
        gaveUpUntil.put(id, now + BARRED);
    }

    /**
     * True when it is this monster's turn at the ladder: nobody else has it, or the one who had it
     * is gone, has taken too long, or is no longer heading for it.
     */
    private boolean myTurn(ServerLevel world, BlockPos foot, UUID id, long now) {
        UUID other = turn.get(foot);
        if (other != null && !other.equals(id)) {
            Entity e = world.getEntity(other);
            boolean still = e != null && e.isAlive() && foot.equals(goals.get(other))
                    && now - turnSince.getOrDefault(foot, now) <= TURN_MAX;
            if (still) {
                return false;
            }
            if (e != null && e.isAlive() && foot.equals(goals.get(other))) {
                bar(other, foot, now);   // took too long: its turn is over, that ladder barred for it
                goals.remove(other);
                goalSince.remove(other);
            }
            other = null;
        }
        if (other == null) {
            turn.put(foot, id);
            turnSince.put(foot, now);
        }
        return true;
    }

    private void endTurn(BlockPos foot, UUID id) {
        if (foot != null && id.equals(turn.get(foot))) {
            turn.remove(foot);
            turnSince.remove(foot);
        }
    }

    /** Where the others wait for their turn: a few blocks out from the wall in front of the ladder. */
    private static BlockPos waitSpot(ServerLevel world, BlockPos foot, BlockPos stand) {
        BlockState state = world.getBlockState(foot);
        if (!(state.getBlock() instanceof LadderBlock)) {
            return stand;
        }
        Direction out = state.getValue(LadderBlock.FACING);
        BlockPos p = stand.relative(out, WAIT_OUT);
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos q = p.above(dy);
            if (world.getBlockState(q.below()).blocksMotion()
                    && world.getBlockState(q).getCollisionShape(world, q).isEmpty()
                    && world.getBlockState(q.above()).getCollisionShape(world, q.above()).isEmpty()) {
                return q;
            }
        }
        return stand;
    }

    /** The lowest rung of the climbable column at {@code at}. */
    private static BlockPos footOf(ServerLevel world, BlockPos at) {
        BlockPos foot = at;
        for (int i = 0; i < 32 && isClimbable(world.getBlockState(foot.below())); i++) {
            foot = foot.below();
        }
        return foot;
    }

    static boolean isClimbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE);
    }

    /**
     * Where a monster stands to get onto the ladder with this foot: the foot itself, or the
     * ground under it when the ladder ends one block above the ground.
     */
    private static BlockPos standOf(ServerLevel world, BlockPos foot) {
        return world.getBlockState(foot.below()).blocksMotion() ? foot : foot.below();
    }

    /** True when a monster can get onto a ladder with this foot from below (it ends one block above the ground). */
    private static boolean reachableFromBelow(ServerLevel world, BlockPos foot) {
        BlockPos gap = foot.below();
        BlockState state = world.getBlockState(gap);
        return state.getCollisionShape(world, gap).isEmpty() && state.getFluidState().isEmpty()
                && world.getBlockState(gap.below()).blocksMotion();
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
                    if (!world.getBlockState(pos.below()).blocksMotion() && !reachableFromBelow(world, pos)) {
                        continue;   // a ladder hanging in the air cannot be reached (one block above the ground can)
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
            if (canWalkTo(mob, standOf(world, foot))) {
                return foot;
            }
        }
        return null;
    }
}
