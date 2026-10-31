package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Survival safety (Simon 31.10., review round 2, D2/D5/D6): nobody is buried or walled in where a
 * dungeon or the war camp is built or taken down, and no animal or villager suffocates in their walls.
 *
 * <ul>
 *   <li>Where a dungeon or a camp is taken down, the ground that comes back may close round someone:
 *       every tick while it comes back, whoever stands in a block there is put on top of the ground
 *       (right after the blocks are set, before the player's next tick).</li>
 *   <li>The place is kept in the save (a "gone site"): who logs in later in a block there (logged out
 *       during the fight, on the waiting bench, in a crash) is put on top as well.</li>
 *   <li>A login is looked at once the player's chunk is loaded; the dungeon checks first whether he
 *       stands in its sealed part without fighting (see {@link Expeditions#arrive}).</li>
 * </ul>
 */
final class SafeGround {

    /** At most this many places are remembered (the oldest finished ones go first). */
    static final int MAX_SITES = 24;
    /** A login is looked at for this long (game ticks) at most, waiting for the player's chunk. */
    private static final long ARRIVAL_TICKS = 600L;

    /** Players who logged in and are not looked at yet: until when (game time; MIN_VALUE: not known yet). */
    private static final Map<UUID, Long> ARRIVALS = new HashMap<>();
    /** For the tests: players put on top of the ground so far. */
    private static int lifted;

    private SafeGround() {
    }

    // ------------------------------------------------------------------ in a block, on top

    /** True when the entity's body stands in a block that stops it (it would suffocate or is shut in). */
    static boolean stuck(ServerLevel level, Entity e) {
        if (e.isInWall()) {
            return true;
        }
        AABB box = e.getBoundingBox().deflate(0.05);
        int x0 = (int) Math.floor(box.minX);
        int y0 = (int) Math.floor(box.minY);
        int z0 = (int) Math.floor(box.minZ);
        int x1 = (int) Math.floor(box.maxX);
        int y1 = (int) Math.floor(box.maxY);
        int z1 = (int) Math.floor(box.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty() || !shape.bounds().move(x, y, z).intersects(box)) {
                        continue;
                    }
                    for (AABB part : shape.toAabbs()) {
                        if (part.move(x, y, z).intersects(box)) {
                            return true;   // (by its parts: inside a scaffolding or by a fence one is not stuck)
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Where one stands on top of the ground of this column; null when its chunk is not loaded. */
    static BlockPos surface(ServerLevel level, int x, int z) {
        if (!Expeditions.loaded(level, x >> 4, z >> 4)) {
            return null;
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    /** Puts the entity on top of the ground of its own column. True when it was moved. */
    static boolean toSurface(ServerLevel level, Entity e) {
        BlockPos top = surface(level, e.getBlockX(), e.getBlockZ());
        if (top == null || top.getY() <= e.getY() && !stuck(level, e)) {
            return false;
        }
        moveTo(level, e, new Vec3(e.getX(), top.getY(), e.getZ()));
        return true;
    }

    /** Puts the entity at this spot (players with their look kept, the fall forgotten). */
    static void moveTo(ServerLevel level, Entity e, Vec3 to) {
        if (e instanceof ServerPlayer player) {
            player.teleportTo(level, to.x, to.y, to.z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        } else {
            e.teleportTo(to.x, to.y, to.z);
        }
        e.resetFallDistance();
    }

    /**
     * Every player (not spectating) in the area who stands in a block now goes on top of the ground
     * of his column, with a line why. Returns how many.
     */
    static int liftStuck(ServerLevel level, AABB area) {
        int n = 0;
        for (ServerPlayer player : new ArrayList<>(level.players())) {
            if (player.isSpectator() || !area.contains(player.position()) || !stuck(level, player)) {
                continue;
            }
            if (toSurface(level, player)) {
                n++;
                lifted++;
                player.sendSystemMessage(Component.translatable("nightsiege.safe.lifted").withStyle(ChatFormatting.GRAY));
                NightSiegeMod.LOGGER.info("[Night Siege] {} stand im Boden bei {} und wurde nach oben gebracht",
                        player.getScoreboardName(), player.blockPosition());
            }
        }
        return n;
    }

    /** A thing a player owns or set up: someone's animal (tamed, named, on a lead), a villager, an armor stand, a frame. */
    static boolean someones(Entity e) {
        if (e instanceof Enemy) {
            return e instanceof Mob mob && (mob.hasCustomName() || mob.isLeashed());   // a named monster is kept by someone
        }
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        if (path.contains("villager") || path.contains("trader") || path.equals("armor_stand") || path.contains("item_frame")
                || path.equals("painting") || path.contains("golem") || path.equals("allay")) {
            return true;
        }
        if (e instanceof Mob mob) {
            if (mob.hasCustomName() || mob.isLeashed()) {
                return true;
            }
            if (mob instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwnerReference() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Before blocks are set in the area (Simon 31.10., D6): monsters there go quietly (as in the dungeon's
     * halls), every other living thing (animals, villagers, pets, armor stands) is put at {@code to}, or on
     * top of its own column when {@code to} is null. {@code skip}: the builder's own (its monsters).
     * Returns how many were moved.
     */
    static int clearCreatures(ServerLevel level, AABB area, Vec3 to, Predicate<Entity> skip) {
        int moved = 0;
        for (Entity e : level.getEntitiesOfClass(Entity.class, area, e -> e instanceof LivingEntity && !(e instanceof Player)
                && e.isAlive() && (skip == null || !skip.test(e)))) {
            if (e instanceof Enemy && !someones(e)) {
                e.discard();   // a cave monster: it would only suffocate there and drop its loot
                continue;
            }
            if (e.isPassenger()) {
                e.stopRiding();
            }
            if (to != null) {
                moveTo(level, e, to);
            } else {
                BlockPos top = surface(level, e.getBlockX(), e.getBlockZ());
                if (top != null) {
                    moveTo(level, e, new Vec3(e.getX(), top.getY(), e.getZ()));
                }
            }
            moved++;
        }
        return moved;
    }

    // ------------------------------------------------------------------ places taken down

    /** The area of a gone site. */
    static AABB area(SiegeSave.GoneSite site) {
        return new AABB(site.minX, site.minY, site.minZ, site.maxX + 1, site.maxY + 1, site.maxZ + 1);
    }

    /** A dungeon or a camp is taken down here: its ground comes back now ({@code restoring}). */
    static void remember(SiegeSave save, String kind, int id, String dimension, AABB area, long day) {
        if (save.goneSites == null) {
            save.goneSites = new ArrayList<>();
        }
        SiegeSave.GoneSite site = find(save, kind, id);
        if (site == null) {
            site = new SiegeSave.GoneSite();
            site.kind = kind;
            site.id = id;
            save.goneSites.add(site);
        }
        site.dimension = dimension;
        site.minX = (int) Math.floor(area.minX);
        site.minY = (int) Math.floor(area.minY);
        site.minZ = (int) Math.floor(area.minZ);
        site.maxX = (int) Math.ceil(area.maxX) - 1;
        site.maxY = (int) Math.ceil(area.maxY) - 1;
        site.maxZ = (int) Math.ceil(area.maxZ) - 1;
        site.restoring = true;
        site.day = day;
        while (save.goneSites.size() > MAX_SITES) {
            SiegeSave.GoneSite oldest = null;
            for (SiegeSave.GoneSite s : save.goneSites) {
                if (!s.restoring && (oldest == null || s.day < oldest.day)) {
                    oldest = s;
                }
            }
            if (oldest == null) {
                break;
            }
            save.goneSites.remove(oldest);
        }
    }

    /** Its ground is all back: whoever stands in a block there now goes up; the place stays for later logins. */
    static void done(MinecraftServer server, SiegeSave save, String kind, int id) {
        SiegeSave.GoneSite site = find(save, kind, id);
        if (site == null) {
            return;
        }
        ServerLevel level = SiegeManager.levelByName(server, site.dimension);
        if (level != null) {
            liftStuck(level, area(site));
        }
        site.restoring = false;
    }

    private static SiegeSave.GoneSite find(SiegeSave save, String kind, int id) {
        if (save.goneSites == null) {
            return null;
        }
        for (SiegeSave.GoneSite s : save.goneSites) {
            if (s.id == id && kind.equals(s.kind)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ every tick, logins

    /** A player logged in: looked at once his chunk is there (see {@link #tick}). */
    static void expect(UUID id) {
        ARRIVALS.put(id, Long.MIN_VALUE);
    }

    /**
     * Every tick, at night too (after the dungeons and the camp have set their blocks for this tick):
     * whoever stands in a block where ground comes back goes up; the players who logged in are looked at.
     */
    static void tick(MinecraftServer server, SiegeManager m) {
        SiegeSave save = m.save();
        if (save.goneSites != null) {
            for (SiegeSave.GoneSite site : save.goneSites) {
                if (!site.restoring) {
                    continue;
                }
                ServerLevel level = SiegeManager.levelByName(server, site.dimension);
                if (level != null && !level.players().isEmpty()) {
                    liftStuck(level, area(site));
                }
            }
        }
        if (ARRIVALS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (UUID id : new ArrayList<>(ARRIVALS.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            long until = ARRIVALS.get(id);
            if (until == Long.MIN_VALUE) {
                until = now + ARRIVAL_TICKS;
                ARRIVALS.put(id, until);
            }
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                ARRIVALS.remove(id);
                continue;
            }
            if (!Expeditions.loaded(level, player.getBlockX() >> 4, player.getBlockZ() >> 4)) {
                if (now > until) {
                    ARRIVALS.remove(id);   // its chunk never came: nothing to look at
                }
                continue;
            }
            ARRIVALS.remove(id);
            arrive(m, level, player);
        }
    }

    /** One login, its chunk loaded: out of a dungeon's sealed part, out of ground that came back. */
    static void arrive(SiegeManager m, ServerLevel level, ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        if (m.expeditions().arrive(level, player)) {
            return;   // put before the dungeon's gate
        }
        SiegeSave save = m.save();
        if (save.goneSites == null) {
            return;
        }
        String dim = SiegeManager.dimensionName(level);
        for (SiegeSave.GoneSite site : save.goneSites) {
            if (dim.equals(site.dimension) && area(site).contains(player.position()) && stuck(level, player)) {
                if (toSurface(level, player)) {
                    lifted++;
                    player.sendSystemMessage(Component.translatable("nightsiege.safe.lifted").withStyle(ChatFormatting.GRAY));
                    NightSiegeMod.LOGGER.info("[Night Siege] {} kam im Boden von {} {} an und wurde nach oben gebracht",
                            player.getScoreboardName(), site.kind, site.id);
                }
                return;
            }
        }
    }

    /** For the tests: players put on top so far, and the places remembered (restoring ones marked). */
    static String testState(SiegeSave save) {
        StringBuilder sb = new StringBuilder("gehoben=" + lifted + " orte=");
        if (save.goneSites != null) {
            for (SiegeSave.GoneSite s : save.goneSites) {
                sb.append(s.kind).append(':').append(s.id).append(s.restoring ? "*" : "").append(' ');
            }
        }
        return sb.toString().trim();
    }
}
