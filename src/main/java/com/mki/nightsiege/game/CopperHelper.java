package com.mki.nightsiege.game;

import com.mki.nightsiege.core.TrapKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The engineer's copper golem (mechanics way, epic crate gain): a small helper that
 * picks up arrows lying around the base and carries them to the engineer's turrets.
 * It has no AI of its own (so it never sorts anyone's chests); this class walks it.
 */
public final class CopperHelper {

    private static final String TAG = "nightsiege_copper_helper";
    private static final int CARRY_MAX = 16;

    /** Engineer → their golem. */
    private static final Map<UUID, UUID> GOLEMS = new HashMap<>();
    /** Golem → arrows carried. */
    private static final Map<UUID, Integer> CARRY = new HashMap<>();
    /** Golem → game time of its last free arrows. */
    private static final Map<UUID, Long> FOUND = new HashMap<>();

    private CopperHelper() {
    }

    /** Engineers who should have a golem (checked once a second). */
    private static final Set<UUID> WANTED = new HashSet<>();

    /** Every 5 ticks: once a second the golems are counted, every time each golem does one step of its job. */
    public static void tick(ServerLevel level, List<ServerPlayer> upright) {
        SiegeManager m = SiegeManager.get();
        if (m == null) {
            return;
        }
        if (level.getGameTime() % 20 == 0) {
            WANTED.clear();
            for (ServerPlayer player : upright) {
                if (player.level() == level && CrateEffects.gainLevel(player, "kupfergolem") > 0) {
                    WANTED.add(player.getUUID());
                    if (golemOf(level, player.getUUID()) == null) {
                        spawn(level, player);
                    }
                }
            }
        }
        Set<UUID> wanted = WANTED;
        Iterator<Map.Entry<UUID, UUID>> it = GOLEMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            Entity golem = level.getEntity(entry.getValue());
            if (!wanted.contains(entry.getKey())) {
                if (golem != null) {
                    golem.discard();
                }
                it.remove();
                continue;
            }
            if (golem instanceof Mob mob) {
                work(level, mob, level.getServer().getPlayerList().getPlayer(entry.getKey()));
            }
        }
    }

    private static Entity golemOf(ServerLevel level, UUID owner) {
        UUID id = GOLEMS.get(owner);
        Entity golem = id == null ? null : level.getEntity(id);
        return golem != null && golem.isAlive() ? golem : null;
    }

    private static void spawn(ServerLevel level, ServerPlayer owner) {
        Mob golem = net.minecraft.world.entity.EntityTypes.COPPER_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (golem == null) {
            return;
        }
        golem.setPos(owner.getX() + 1, owner.getY(), owner.getZ() + 1);
        golem.setNoAi(true);
        golem.setInvulnerable(true);
        golem.setPersistenceRequired();
        golem.setSilent(true);
        golem.addTag(TAG);
        golem.setCustomName(Component.translatable("nightsiege.gadget.kupfergolem.name", owner.getDisplayName()));
        level.addFreshEntity(golem);
        GOLEMS.put(owner.getUUID(), golem.getUUID());
        CARRY.put(golem.getUUID(), 0);
        FOUND.put(golem.getUUID(), level.getGameTime());
        level.playSound(null, golem.blockPosition(), SoundEvents.COPPER_GOLEM_SPAWN, SoundSource.NEUTRAL, 1f, 1.2f);
    }

    /** One step: pick up arrows, or bring them to the emptiest turret, or follow the engineer. */
    private static void work(ServerLevel level, Mob golem, ServerPlayer owner) {
        UUID id = golem.getUUID();
        int carry = CARRY.getOrDefault(id, 0);
        long now = level.getGameTime();
        // Every 10 s the golem finds a few arrows of its own, so the turrets never run quite dry.
        if (now - FOUND.getOrDefault(id, now) >= 200 && carry < CARRY_MAX) {
            carry = Math.min(CARRY_MAX, carry + 2);
            FOUND.put(id, now);
        }
        Vec3 goal = null;
        if (carry < CARRY_MAX) {
            Entity arrow = nearestArrow(level, golem.position(), 12.0);
            if (arrow != null) {
                if (arrow.distanceToSqr(golem) < 2.5) {
                    carry += arrow instanceof ItemEntity item ? item.getItem().getCount() : 1;
                    carry = Math.min(CARRY_MAX, carry);
                    arrow.discard();
                    level.playSound(null, golem.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4f, 1.5f);
                } else {
                    goal = arrow.position();
                }
            }
        }
        if (goal == null && carry > 0 && owner != null) {
            BlockPos turret = SiegeManager.get().kits().traps().emptiestTurret(level, owner.getUUID().toString(), golem.position(), 24.0);
            if (turret != null) {
                if (golem.position().distanceToSqr(Vec3.atBottomCenterOf(turret)) < 4.0) {
                    int loaded = SiegeManager.get().kits().traps().addAmmo(level, turret, carry);
                    carry -= loaded;
                    if (loaded > 0) {
                        level.sendParticles(ParticleTypes.WAX_ON, turret.getX() + 0.5, turret.getY() + 1.1, turret.getZ() + 0.5,
                                8, 0.3, 0.2, 0.3, 0);
                        level.playSound(null, turret, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.3f);
                    }
                } else {
                    goal = Vec3.atBottomCenterOf(turret);
                }
            }
        }
        if (goal == null && owner != null && golem.distanceToSqr(owner) > 9.0) {
            goal = owner.position();
        }
        CARRY.put(id, carry);
        if (goal != null) {
            walk(level, golem, goal, owner);
        }
    }

    private static Entity nearestArrow(ServerLevel level, Vec3 at, double radius) {
        AABB box = new AABB(at, at).inflate(radius, 4.0, radius);
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> e instanceof AbstractArrow a
                && a.pickup == AbstractArrow.Pickup.ALLOWED && a.getDeltaMovement().lengthSqr() < 0.01
                || e instanceof ItemEntity item && item.getItem().is(Items.ARROW) && !KitService.isKit(item.getItem()))) {
            double d = e.distanceToSqr(at);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** A quarter second of walking (one block), a block up or down at a time; far away it hops over. */
    private static void walk(ServerLevel level, Mob golem, Vec3 goal, ServerPlayer owner) {
        Vec3 from = golem.position();
        Vec3 flat = new Vec3(goal.x - from.x, 0, goal.z - from.z);
        double dist = flat.length();
        if (dist > 30 && owner != null) {
            golem.teleportTo(owner.getX() + 1, owner.getY(), owner.getZ() + 1);
            return;
        }
        double step = Math.min(dist, 1.0);
        if (step < 0.2) {
            return;
        }
        Vec3 dir = flat.scale(1.0 / dist);
        Vec3 next = from.add(dir.scale(step));
        Double y = groundAt(level, next, from.y);
        if (y == null) {
            // Something in the way: try half the step.
            next = from.add(dir.scale(step / 2));
            y = groundAt(level, next, from.y);
            if (y == null) {
                if (owner != null && golem.distanceToSqr(owner) > 36) {
                    golem.teleportTo(owner.getX() + 1, owner.getY(), owner.getZ() + 1);
                }
                return;
            }
        }
        golem.setYRot((float) (Math.atan2(-dir.x, dir.z) * 180.0 / Math.PI));
        golem.setYHeadRot(golem.getYRot());
        golem.teleportTo(next.x, y, next.z);
    }

    /** The standing height near {@code y} at a spot: a free 2-block gap over solid ground, up to 2 up or 3 down. */
    private static Double groundAt(ServerLevel level, Vec3 at, double y) {
        int base = (int) Math.floor(y + 0.01);
        int[] tries = {0, 1, -1, 2, -2, -3};
        for (int dy : tries) {
            BlockPos feet = BlockPos.containing(at.x, base + dy, at.z);
            BlockState below = level.getBlockState(feet.below());
            if (free(level, feet) && free(level, feet.above()) && below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) {
                return (double) feet.getY();
            }
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty()
                && ModContent.trapOf(state) != TrapKind.TURRET;
    }

    /** End of the night (and server stop): all golems go. */
    public static void removeAll(MinecraftServer server) {
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                for (UUID id : GOLEMS.values()) {
                    Entity golem = level.getEntity(id);
                    if (golem != null) {
                        golem.discard();
                    }
                }
            }
        }
        GOLEMS.clear();
        CARRY.clear();
        FOUND.clear();
        WANTED.clear();
    }

    /** Golems left over from a crash are taken away when they load. */
    public static void onLoad(Entity entity) {
        if (entity.entityTags().contains(TAG) && !GOLEMS.containsValue(entity.getUUID())) {
            entity.discard();
        }
    }

    public static int count() {
        return GOLEMS.size();
    }
}
