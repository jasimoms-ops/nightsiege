package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spiders under an overhang (Simon, 27 October 2026: the spider queen hung under the wall, and
 * the night's spiders do the same). A climbing spider presses on against a block jutting out
 * above it and stays there for good. When it has climbed no higher for 1.5 s with a block over
 * its head, it swings out past the lip, up and in onto the top (a short guided hop); with no
 * spot on top it lets go of the wall and finds another way.
 */
public final class SpiderOverhang {

    /** A spider's climb: the highest point reached and since when it got no higher. */
    private static final class Track {
        double bestY;
        long since;
        long nextTry;
        long seen;
    }

    /** A guided hop: out past the lip, up, in onto the top. */
    private static final class Hop {
        final UUID id;
        final Vec3[] points;
        final long until;
        final boolean hadNoGravity;
        int index;

        Hop(UUID id, Vec3[] points, long until, boolean hadNoGravity) {
            this.id = id;
            this.points = points;
            this.until = until;
            this.hadNoGravity = hadNoGravity;
        }
    }

    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static final List<Hop> HOPS = new ArrayList<>();
    /** Spiders watched outside a night (the tests). */
    private static final Set<UUID> WATCHED = new HashSet<>();
    /** Hops so far (for the tests and the log). */
    private static int hops;
    private static int letGo;

    private SpiderOverhang() {
    }

    /** Every tick of the night: the hops move on; every fifth tick the climbers are checked. */
    public static void tick(ServerLevel level, Collection<UUID> mobs, Mob boss, Vec3 goal, long now) {
        tickHops(level, now);
        if (now % 5 != 0) {
            return;
        }
        for (UUID id : mobs) {
            if (level.getEntity(id) instanceof Spider spider) {
                check(level, spider, goal, now);
            }
        }
        if (boss instanceof Spider queen) {
            check(level, queen, goal, now);
        }
        TRACKS.values().removeIf(t -> now - t.seen > 100);
    }

    /** Outside a night: only the spiders the tests watch. */
    public static void tickWatched(MinecraftServer server) {
        if (WATCHED.isEmpty()) {
            return;   // in a night the night ticks the hops
        }
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        tickHops(level, now);
        if (now % 5 != 0) {
            return;
        }
        for (UUID id : WATCHED) {
            if (level.getEntity(id) instanceof Spider spider) {
                LivingEntity target = spider.getTarget();
                check(level, spider, target == null ? spider.position() : target.position(), now);
            }
        }
    }

    /** Tests: watch a spider outside a night (or stop watching all). */
    public static void watch(UUID id) {
        if (id == null) {
            WATCHED.clear();
        } else {
            WATCHED.add(id);
        }
    }

    /** Tests: hops so far and how often a spider let go of the wall instead. */
    public static String testCounts() {
        return "hops=" + hops + " letgo=" + letGo;
    }

    private static void check(ServerLevel level, Mob spider, Vec3 core, long now) {
        if (!spider.isAlive() || hopping(spider.getUUID())) {
            return;
        }
        LivingEntity target = spider.getTarget();
        Vec3 goal = target != null && target.isAlive() ? target.position() : core;
        Track t = TRACKS.computeIfAbsent(spider.getUUID(), k -> new Track());
        t.seen = now;
        if (!spider.onClimbable()) {
            t.bestY = spider.getY();
            t.since = now;
            return;
        }
        if (spider.getY() > t.bestY + 0.25) {
            t.bestY = spider.getY();
            t.since = now;
            return;
        }
        if (now - t.since < 30 || now < t.nextTry || !roofAbove(level, spider)) {
            return;
        }
        t.nextTry = now + 60;
        t.since = now;
        Direction wall = wallSide(level, spider, goal);
        Vec3[] path = wall == null ? null : path(level, spider, wall, goal);
        if (path != null) {
            HOPS.add(new Hop(spider.getUUID(), path, now + 50, spider.isNoGravity()));
            spider.setNoGravity(true);
            spider.getNavigation().stop();
            hops++;
            level.playSound(null, spider.getX(), spider.getY(), spider.getZ(), SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 1.0f, 0.7f);
            return;
        }
        // Nowhere to go on top: let go of the wall, away from it.
        Vec3 away = wall == null ? spider.position().subtract(goal).multiply(1, 0, 1)
                : new Vec3(-wall.getStepX(), 0, -wall.getStepZ());
        if (away.lengthSqr() > 1.0e-4) {
            away = away.normalize();
            spider.setDeltaMovement(away.x * 0.5, 0.15, away.z * 0.5);
            spider.hurtMarked = true;
            letGo++;
        }
    }

    private static boolean hopping(UUID id) {
        for (Hop hop : HOPS) {
            if (hop.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void tickHops(ServerLevel level, long now) {
        Iterator<Hop> it = HOPS.iterator();
        while (it.hasNext()) {
            Hop hop = it.next();
            Entity e = level.getEntity(hop.id);
            if (!(e instanceof Mob mob) || !mob.isAlive() || now > hop.until) {
                if (e instanceof Mob mob) {
                    mob.setNoGravity(hop.hadNoGravity);
                }
                it.remove();
                continue;
            }
            Vec3 to = hop.points[hop.index];
            Vec3 step = to.subtract(mob.position());
            double len = step.length();
            if (len < 0.3) {
                hop.index++;
                if (hop.index >= hop.points.length) {
                    mob.setNoGravity(hop.hadNoGravity);
                    mob.setDeltaMovement(Vec3.ZERO);
                    mob.hurtMarked = true;
                    it.remove();
                }
                continue;
            }
            mob.setDeltaMovement(step.scale(Math.min(0.45, len) / len));
            mob.hurtMarked = true;
            mob.getNavigation().stop();
            if (now % 3 == 0) {
                level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.2, mob.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    /** A solid block right over the spider's back. */
    private static boolean roofAbove(ServerLevel level, Mob spider) {
        AABB box = spider.getBoundingBox();
        double y = box.maxY + 0.15;
        double[][] spots = {
                {box.minX + 0.05, box.minZ + 0.05}, {box.maxX - 0.05, box.minZ + 0.05},
                {box.minX + 0.05, box.maxZ - 0.05}, {box.maxX - 0.05, box.maxZ - 0.05},
                {(box.minX + box.maxX) * 0.5, (box.minZ + box.maxZ) * 0.5}};
        for (double[] s : spots) {
            if (solid(level, BlockPos.containing(s[0], y, s[1]))) {
                return true;
            }
        }
        return false;
    }

    /** The side the spider clings to: a solid block beside it (the one most towards its goal). */
    private static Direction wallSide(ServerLevel level, Mob spider, Vec3 goal) {
        AABB box = spider.getBoundingBox();
        double midY = (box.minY + box.maxY) * 0.5;
        Vec3 c = spider.position();
        Vec3 toGoal = goal.subtract(c).multiply(1, 0, 1);
        Direction best = null;
        double bestDot = -2.0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            double half = (box.maxX - box.minX) * 0.5;
            BlockPos beside = BlockPos.containing(c.x + d.getStepX() * (half + 0.3), midY, c.z + d.getStepZ() * (half + 0.3));
            if (!solid(level, beside)) {
                continue;
            }
            double dot = toGoal.lengthSqr() < 1.0e-4 ? 0.0 : toGoal.normalize().dot(new Vec3(d.getStepX(), 0, d.getStepZ()));
            if (dot > bestDot) {
                bestDot = dot;
                best = d;
            }
        }
        return best;
    }

    /**
     * The way over the lip: out from the wall past the overhang, up above it, in onto the
     * nearest spot on top. Null when there is no such spot within reach.
     */
    private static Vec3[] path(ServerLevel level, Mob spider, Direction wall, Vec3 goal) {
        AABB box = spider.getBoundingBox();
        double half = (box.maxX - box.minX) * 0.5;
        int roof = (int) Math.floor(box.maxY + 0.15);
        Vec3 c = spider.position();
        Vec3 out = new Vec3(-wall.getStepX(), 0, -wall.getStepZ());
        // How far the overhang reaches out from the spider's back.
        int depth = 0;
        for (int k = 0; k <= 4; k++) {
            BlockPos p = BlockPos.containing(c.x + out.x * k, roof, c.z + out.z * k);
            if (solid(level, p)) {
                depth = k + 1;
            } else if (k > 0) {
                break;
            }
        }
        if (depth > 4) {
            return null;
        }
        // The spot on top: above the roof, standable, within a few blocks, nearest the goal.
        int need = Math.max(1, (int) Math.ceil(spider.getBbHeight()));
        BlockPos base = spider.blockPosition();
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = roof + 1; y <= roof + 6; y++) {
                    BlockPos feet = new BlockPos(base.getX() + dx, y, base.getZ() + dz);
                    if (!solid(level, feet.below())) {
                        continue;
                    }
                    if (!free(level, feet, need)) {
                        break;
                    }
                    Vec3 spot = Vec3.atBottomCenterOf(feet);
                    double score = spot.distanceToSqr(goal) + 4.0 * spot.distanceToSqr(c);
                    if (score < bestScore) {
                        bestScore = score;
                        best = spot;
                    }
                    break;
                }
            }
        }
        if (best == null) {
            return null;
        }
        double reach = depth + half + 0.6;
        double topY = Math.max(best.y, roof + 1.0) + 0.6;
        Vec3 swingOut = new Vec3(c.x + out.x * reach, c.y, c.z + out.z * reach);
        Vec3 swingUp = new Vec3(swingOut.x, topY, swingOut.z);
        Vec3 over = new Vec3(best.x, topY, best.z);
        return new Vec3[]{swingOut, swingUp, over, best.add(0, 0.05, 0)};
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean free(ServerLevel level, BlockPos feet, int height) {
        for (int k = 0; k < height; k++) {
            if (solid(level, feet.above(k))) {
                return false;
            }
        }
        return true;
    }
}
