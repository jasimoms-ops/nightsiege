package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.BlockGuard;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SiegeMachineKind;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.mixin.BlockDisplayAccessor;
import com.mki.nightsiege.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The siege machines of a night after a war camp (concept 27 October 2026). They are set up
 * 40–60 blocks in front of the wall a little after the night begins and stay where they are.
 * Each has its own life (an invisible hit box under a block model), two pillagers who work it
 * and three pillagers who guard it (Simon, 27 October 2026: "sollten während der Nacht von
 * Pillagern bewacht werden"). With both workers dead it stands still until new ones come.
 * What they break in the wall is written down and comes back after the night.
 */
public final class SiegeMachines {

    /** Tag of a machine's hit box. */
    public static final String TAG_MACHINE = "nightsiege_machine";
    /** Tag of a machine's workers and guards. */
    public static final String TAG_CREW = "nightsiege_machinecrew";

    /** Everything of the machines that is in the world now (models, shots, crews); the rest goes when it loads. */
    private static final Set<UUID> LIVE = new HashSet<>();

    /** What a machine sees of the night (or of a try-out in creative). */
    interface Field {
        ServerLevel world();

        /** What is besieged: the core (or, for a try-out, where the machine aims). */
        Vec3 anchor();

        List<ServerPlayer> defenders();

        int level();

        boolean insideBase(double x, double z);

        double baseEdge(double x, double z);
    }

    static final class Machine {
        final SiegeMachineKind kind;
        final ServerLevel level;
        final Vec3 base;
        final float yaw;
        final boolean trial;
        final Vec3 trialTarget;
        Rigs.Rig rig;
        UUID hitbox;
        double maxHealth = 1;
        final List<UUID> workers = new ArrayList<>();
        final List<UUID> guards = new ArrayList<>();
        long nextShot;
        long crewDue = -1L;
        /** The throwing arm: when it last swung (game time), or -1. */
        long swung = -1L;
        /** Sabotage (Simon, 27 October 2026): ticks worked on it, and when someone last did. */
        int sabotage;
        long sabotageAt = -1L;
        float pitch;
        final List<Rope> ropes = new ArrayList<>();

        Machine(SiegeMachineKind kind, ServerLevel level, Vec3 base, float yaw, boolean trial, Vec3 trialTarget) {
            this.kind = kind;
            this.level = level;
            this.base = base;
            this.yaw = yaw;
            this.trial = trial;
            this.trialTarget = trialTarget;
        }

        Vec3 at(double x, double y, double z) {
            return Rigs.world(base, yaw, x, y, z);
        }
    }

    /** A boulder, shell, pot or carcass in the air. */
    private static final class Shot {
        final Machine machine;
        final Vec3 from;
        final Vec3 to;
        final long start;
        final int flight;
        final double arc;
        final UUID display;
        final Wall wall;

        Shot(Machine machine, Vec3 from, Vec3 to, long start, int flight, double arc, UUID display, Wall wall) {
            this.machine = machine;
            this.from = from;
            this.to = to;
            this.start = start;
            this.flight = flight;
            this.arc = arc;
            this.display = display;
            this.wall = wall;
        }
    }

    /** Fire or plague lying on the ground for a while. */
    private static final class Zone {
        final Machine machine;
        final boolean fire;
        final Vec3 center;
        final double radius;
        final long until;
        final UUID display;

        Zone(Machine machine, boolean fire, Vec3 center, double radius, long until, UUID display) {
            this.machine = machine;
            this.fire = fire;
            this.center = center;
            this.radius = radius;
            this.until = until;
            this.display = display;
        }
    }

    /** The rope of a grappling hook: ladders up the wall and the hook on top. */
    private static final class Rope {
        final List<BlockPos> rungs = new ArrayList<>();
        UUID hook;
    }

    /** A wall in front of a machine: its column, the ground before it, its top and outer face. */
    record Wall(int x, int z, int ground, int top, Direction face) {
        BlockPos bottom() {
            return new BlockPos(x, ground, z);
        }
    }

    private final List<Machine> machines = new ArrayList<>();
    private final List<Shot> shots = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final Map<UUID, Machine> owners = new HashMap<>();
    private final Random random = new Random();
    /** Machines planned for tonight, set up at {@link #setUpAt}. */
    private final List<SiegeMachineKind> planned = new ArrayList<>();
    private long setUpAt = -1L;
    private ServerBossEvent bar;
    /** Machines of tonight (for the bar). */
    private int nightTotal;
    /** Who works on which machine right now (a hit interrupts it). */
    private final Map<UUID, Machine> saboteurs = new HashMap<>();

    // ------------------------------------------------------------------ the night

    /** Tonight comes with machines: how many follows from the level. */
    public void plan(int level) {
        planned.clear();
        planned.addAll(SiegeMachineKind.pick(SiegeMachineKind.countFor(level), random));
        setUpAt = -1L;
        NightSiegeMod.LOGGER.info("[Night Siege] Belagerungsmaschinen heute Nacht: {}", planned);
    }

    /** Admin: machines into the running night at once. */
    public void planNow(int level) {
        plan(level);
        setUpAt = 0L;
    }

    public boolean planned() {
        return !planned.isEmpty();
    }

    /** The machines planned for tonight (for the tests and the log). */
    public List<SiegeMachineKind> plannedKinds() {
        return new ArrayList<>(planned);
    }

    /** Machines standing now (night and try-outs). */
    public int standing() {
        return machines.size();
    }

    /** Every tick of the night. */
    void tick(BossService.Host host, SiegeConfig cfg, long now) {
        if (!planned.isEmpty()) {
            if (setUpAt < 0) {
                setUpAt = now + cfg.machineStartSeconds * 20L;
            }
            if (now >= setUpAt) {
                setUp(host, cfg, now);
            }
        }
        Field night = field(host);
        tickAll(night, cfg, now);
        if (bar != null && now % 20 == 0) {
            refreshBar(host.watchers());
        }
    }

    /** Every tick outside the nights: only machines set up from a spawn egg. */
    void tickTrials(MinecraftServer server, SiegeConfig cfg, long now) {
        if (machines.isEmpty() && shots.isEmpty() && zones.isEmpty()) {
            return;
        }
        tickAll(null, cfg, now);
    }

    private void tickAll(Field night, SiegeConfig cfg, long now) {
        for (Machine machine : new ArrayList<>(machines)) {
            Field f = machine.trial ? trialField(machine) : night;
            if (f == null) {
                continue;
            }
            try {
                tickMachine(f, cfg, machine, now);
            } catch (RuntimeException e) {
                NightSiegeMod.LOGGER.warn("[Night Siege] Maschine {}: {}", machine.kind.id(), e.toString());
            }
        }
        tickShots(night, cfg, now);
        tickZones(night, cfg, now);
    }

    /** The machines go up in front of the walls. */
    private void setUp(BossService.Host host, SiegeConfig cfg, long now) {
        List<SiegeMachineKind> kinds = new ArrayList<>(planned);
        planned.clear();
        ServerLevel level = host.world();
        BlockPos anchor = host.anchor();
        if (level == null || anchor == null) {
            return;
        }
        double start = random.nextDouble() * Math.PI * 2.0;
        int placed = 0;
        for (int i = 0; i < kinds.size(); i++) {
            Vec3 spot = null;
            for (int attempt = 0; attempt < 14 && spot == null; attempt++) {
                double a = start + i * Math.PI * 2.0 / kinds.size() + (attempt == 0 ? 0 : (random.nextDouble() - 0.5) * 1.4);
                double dx = Math.cos(a);
                double dz = Math.sin(a);
                double edge = host.baseEdge(anchor.getX() + 0.5 + dx * 30, anchor.getZ() + 0.5 + dz * 30);
                double far = Math.max(edge, 10.0) + 40.0 + random.nextDouble() * 20.0 - (attempt > 9 ? 15 : 0);
                spot = groundSpot(level, anchor.getX() + 0.5 + dx * far, anchor.getZ() + 0.5 + dz * far);
            }
            if (spot == null) {
                continue;
            }
            float yaw = yawTowards(spot, Vec3.atBottomCenterOf(anchor));
            Machine machine = spawn(level, kinds.get(i), spot, yaw, false, null, host.level(), host.defenders().size(), cfg);
            if (machine != null) {
                machine.nextShot = now + 60 + random.nextInt(80);
                placed++;
            }
        }
        if (placed == 0) {
            return;
        }
        nightTotal = placed;
        bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("nightsiege.machine.bar", placed),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        for (ServerPlayer p : host.watchers()) {
            bar.addPlayer(p);
            p.sendSystemMessage(Component.translatable("nightsiege.machine.arrive", placed).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            p.sendSystemMessage(Component.translatable("nightsiege.machine.how", cfg.machineSabotageSeconds).withStyle(ChatFormatting.YELLOW));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2f, 0.7f);
        }
        NightSiegeMod.LOGGER.info("[Night Siege] {} Belagerungsmaschinen aufgestellt", placed);
    }

    /** A free spot on the ground (no water), or null when the ground is not loaded. */
    private static Vec3 groundSpot(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        BlockPos below = new BlockPos(bx, y - 1, bz);
        if (!level.getFluidState(below).isEmpty() || !level.getFluidState(below.above()).isEmpty()) {
            return null;
        }
        return new Vec3(bx + 0.5, y, bz + 0.5);
    }

    static float yawTowards(Vec3 from, Vec3 to) {
        return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
    }

    /** Builds one machine: model, hit box, two workers and the guards. */
    Machine spawn(ServerLevel level, SiegeMachineKind kind, Vec3 base, float yaw, boolean trial, Vec3 trialTarget,
                  int siegeLevel, int defenders, SiegeConfig cfg) {
        Machine machine = new Machine(kind, level, base, yaw, trial, trialTarget);
        MachineModels.Model model = MachineModels.of(kind);
        machine.rig = Rigs.spawn(level, base, base.add(Rigs.world(Vec3.ZERO, yaw, model.pivot().x, model.pivot().y, model.pivot().z)),
                model.pieces(), yaw, null);
        machine.pitch = model.rest();
        Rigs.turn(level, machine.rig, yaw, machine.pitch);
        for (UUID id : machine.rig.all()) {
            LIVE.add(id);
            Entity e = level.getEntity(id);
            if (e != null) {
                e.addTag(TAG_MACHINE);
            }
        }
        Zombie box = EntityTypes.HUSK.create(level, EntitySpawnReason.EVENT);   // a husk: no burning by day
        if (box == null) {
            Rigs.remove(level, machine.rig);
            return null;
        }
        box.snapTo(base.x, base.y, base.z, yaw, 0f);
        box.setNoAi(true);
        box.setInvisible(true);
        box.setSilent(true);
        box.setPersistenceRequired();
        double health = kind.health(siegeLevel) * cfg.machineHealthMultiplier * (1.0 + 0.15 * Math.max(0, defenders - 1));
        BossService.set(box, Attributes.MAX_HEALTH, health);
        box.setHealth((float) health);
        BossService.set(box, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        BossService.set(box, Attributes.SCALE, model.hitboxScale());
        box.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        // Invisible by an effect: any effect added later (poison, slowness) would otherwise make it seen again.
        box.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            box.setItemSlot(slot, ItemStack.EMPTY);
            box.setDropChance(slot, 0f);
        }
        box.setCustomName(Component.translatable(kind.translationKey()).withStyle(ChatFormatting.GOLD));
        box.setCustomNameVisible(true);
        box.addTag(MobFactory.TAG);   // the defenders' weapons and towers take it for an enemy
        box.addTag(TAG_MACHINE);
        LIVE.add(box.getUUID());   // before it enters the world: loading checks for strays
        if (!level.addFreshEntity(box)) {
            LIVE.remove(box.getUUID());
            Rigs.remove(level, machine.rig);
            return null;
        }
        machine.hitbox = box.getUUID();
        machine.maxHealth = health;
        owners.put(box.getUUID(), machine);
        machines.add(machine);
        if (!trial) {
            crew(machine, cfg, siegeLevel, true);
        }
        return machine;
    }

    /** Workers at the machine and, the first time, the guards around it. */
    private void crew(Machine machine, SiegeConfig cfg, int siegeLevel, boolean withGuards) {
        ServerLevel level = machine.level;
        for (int i = 0; i < cfg.machineCrew; i++) {
            double side = i % 2 == 0 ? 1.9 : -1.9;
            Vec3 at = machine.at(side, 0, -1.2 - (i / 2) * 1.2);
            Vec3 from = machine.workers.isEmpty() && !withGuards ? machine.at(side * 2, 0, -14) : at;
            Mob mob = pillager(level, from, siegeLevel, cfg, machine);
            if (mob != null) {
                machine.workers.add(mob.getUUID());
                mob.setHomeTo(BlockPos.containing(machine.base), 4);
                if (from != at) {
                    mob.getNavigation().moveTo(at.x, at.y, at.z, 1.1);
                }
            }
        }
        if (!withGuards) {
            return;
        }
        for (int i = 0; i < cfg.machineGuards; i++) {
            double a = Math.toRadians(machine.yaw) + Math.PI + (random.nextDouble() - 0.5) * Math.PI * 1.6;
            double r = 4.5 + random.nextDouble() * 2.5;
            Vec3 at = machine.base.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Mob mob = pillager(level, at, siegeLevel, cfg, machine);
            if (mob != null) {
                machine.guards.add(mob.getUUID());
                mob.setHomeTo(BlockPos.containing(machine.base), 9);
            }
        }
    }

    private Mob pillager(ServerLevel level, Vec3 at, int siegeLevel, SiegeConfig cfg, Machine machine) {
        BlockPos spot = BossService.groundNear(level, at.x, at.y + 2, at.z);
        if (spot == null) {
            spot = BlockPos.containing(at.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) Math.floor(at.x), (int) Math.floor(at.z)), at.z);
        }
        Mob mob = MobFactory.spawnAt(level, spot, "minecraft:pillager", siegeLevel, cfg, random, SiegeEvent.NONE, false);
        if (mob == null) {
            return null;
        }
        mob.addTag(TAG_CREW);
        LIVE.add(mob.getUUID());
        owners.put(mob.getUUID(), machine);
        return mob;
    }

    // ------------------------------------------------------------------ working the machines

    private void tickMachine(Field f, SiegeConfig cfg, Machine machine, long now) {
        ServerLevel level = machine.level;
        Entity box = level.getEntity(machine.hitbox);
        if (box == null || !box.isAlive()) {
            if (level.isLoaded(BlockPos.containing(machine.base))) {
                destroy(machine, null, false);
            }
            return;
        }
        animate(machine, now);
        if (now % 20 == 0) {
            leash(machine);
            tickRopes(machine);
        }
        int working = machine.trial ? 2 : workersAt(machine);
        if (working == 0) {
            if (!machine.trial && livingWorkers(machine) == 0) {
                if (machine.crewDue < 0) {
                    machine.crewDue = now + cfg.machineCrewSeconds * 20L;
                } else if (now >= machine.crewDue) {
                    machine.crewDue = -1L;
                    machine.workers.clear();
                    crew(machine, cfg, f.level(), false);   // new workers walk up from behind
                }
            }
            return;
        }
        if (now < machine.nextShot) {
            return;
        }
        boolean fired = fire(f, cfg, machine, now);
        double seconds = machine.kind.seconds() * (working >= 2 ? 1.0 : 1.6);
        machine.nextShot = now + (fired ? (long) Math.round(seconds * 20) : 20L);
    }

    private int workersAt(Machine machine) {
        int n = 0;
        for (UUID id : machine.workers) {
            Entity e = machine.level.getEntity(id);
            if (e != null && e.isAlive() && e.distanceToSqr(machine.base) < 6.0 * 6.0) {
                n++;
            }
        }
        return n;
    }

    private int livingWorkers(Machine machine) {
        int n = 0;
        for (UUID id : machine.workers) {
            Entity e = machine.level.getEntity(id);
            if (e != null && e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    /** Workers stay at the machine and guards close to it. */
    private void leash(Machine machine) {
        for (UUID id : machine.workers) {
            if (machine.level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                double d = mob.distanceToSqr(machine.base);
                if (d > 3.5 * 3.5 && (mob.getTarget() == null || d > 10 * 10)) {
                    mob.setTarget(null);
                    mob.getNavigation().moveTo(machine.base.x + 1.5, machine.base.y, machine.base.z, 1.1);
                }
            }
        }
        for (UUID id : machine.guards) {
            if (machine.level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                double d = mob.distanceToSqr(machine.base);
                if (d > 9 * 9 && (mob.getTarget() == null || d > 24 * 24)) {
                    mob.setTarget(null);
                    mob.getNavigation().moveTo(machine.base.x, machine.base.y, machine.base.z, 1.15);
                }
            }
        }
    }

    /** Whether a worker or guard may pick this target: only defenders close to its machine. */
    public boolean mayTarget(Mob mob, LivingEntity target) {
        Machine machine = owners.get(mob.getUUID());
        if (machine == null || target == null) {
            return true;
        }
        double reach = machine.guards.contains(mob.getUUID()) ? 24 : 14;
        return target.distanceToSqr(machine.base) < reach * reach;
    }

    /** One shot of a machine; false when it found nothing to shoot at. */
    private boolean fire(Field f, SiegeConfig cfg, Machine machine, long now) {
        ServerLevel level = machine.level;
        Vec3 anchor = f.anchor();
        switch (machine.kind) {
            case TREBUCHET -> {
                Wall wall = findWall(level, machine.base, anchor, 110, random);
                Vec3 to = wall != null
                        ? new Vec3(wall.x() + 0.5 + wall.face().getStepX() * 0.6, wall.ground() + Math.max(1.0, (wall.top() - wall.ground()) * 0.55),
                                wall.z() + 0.5 + wall.face().getStepZ() * 0.6)
                        : groundAt(level, anchor.add(random.nextGaussian() * 4, 0, random.nextGaussian() * 4));
                swing(machine, now);
                launch(machine, machine.at(0, 8.0, -1.5), to, now + 4, 50, 14, Blocks.COBBLESTONE.defaultBlockState(), 1.1f, wall);
                sound(level, machine.base, SoundEvents.ANVIL_PLACE, 1.4f, 0.5f);
                return true;
            }
            case MORTAR -> {
                Vec3 to = insideTarget(f, machine);
                swing(machine, now);
                launch(machine, machine.at(0, 2.0, 1.2), to, now, 60, 24, Blocks.COAL_BLOCK.defaultBlockState(), 0.55f, null);
                sound(level, machine.base, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 1.6f);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, machine.at(0, 2.2, 1.4).x, machine.at(0, 2.2, 1.4).y,
                        machine.at(0, 2.2, 1.4).z, 12, 0.3, 0.3, 0.3, 0.05);
                return true;
            }
            case FIRE, PLAGUE -> {
                Vec3 to = insideTarget(f, machine);
                swing(machine, now);
                BlockState load = machine.kind == SiegeMachineKind.FIRE ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : Blocks.BONE_BLOCK.defaultBlockState();
                launch(machine, machine.at(0, 3.5, 0.5), to, now + 3, 40, 13, load, 0.6f, null);
                sound(level, machine.base, SoundEvents.ANVIL_PLACE, 0.9f, 0.8f);
                return true;
            }
            case BALLISTA -> {
                return shootBolt(f, cfg, machine, now);
            }
            case HOOK -> {
                if (machine.ropes.size() >= 3) {
                    return false;
                }
                Wall wall = findWall(level, machine.base, anchor, 90, random);
                if (wall == null) {
                    return false;
                }
                Wall climb = climbable(level, wall);
                Wall aim = climb != null ? climb : wall;
                Vec3 to = new Vec3(aim.x() + 0.5 + aim.face().getStepX() * 0.6, aim.top() - 0.5, aim.z() + 0.5 + aim.face().getStepZ() * 0.6);
                turnTo(machine, to);
                launch(machine, machine.at(0, 1.8, 2.0), to, now, 18, 3, Blocks.IRON_BLOCK.defaultBlockState(), 0.3f, climb);
                sound(level, machine.base, SoundEvents.CROSSBOW_SHOOT, 1.6f, 0.5f);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Where a shell lands: mostly by a defender inside the walls, else somewhere within them. */
    private Vec3 insideTarget(Field f, Machine machine) {
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : f.defenders()) {
            if (p.level() == machine.level && p.distanceToSqr(machine.base) < 110 * 110) {
                near.add(p);
            }
        }
        Vec3 anchor = f.anchor();
        if (!near.isEmpty() && random.nextDouble() < 0.75) {
            ServerPlayer p = near.get(random.nextInt(near.size()));
            return groundAt(machine.level, p.position().add(random.nextGaussian() * 2.0, 0, random.nextGaussian() * 2.0));
        }
        double a = random.nextDouble() * Math.PI * 2.0;
        double edge = f.baseEdge(anchor.x + Math.cos(a) * 20, anchor.z + Math.sin(a) * 20);
        double r = Math.sqrt(random.nextDouble()) * Math.max(4.0, edge - 3.0);
        return groundAt(machine.level, anchor.add(Math.cos(a) * r, 0, Math.sin(a) * r));
    }

    private static Vec3 groundAt(ServerLevel level, Vec3 at) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(at.x), (int) Math.floor(at.z));
        return new Vec3(at.x, y, at.z);
    }

    /**
     * The wall in front of a machine on the way to the core: where the ground first rises by
     * two blocks or more. Null when there is none within reach (an open base).
     */
    static Wall findWall(ServerLevel level, Vec3 from, Vec3 towards, double reach, Random random) {
        double dx = towards.x - from.x;
        double dz = towards.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0) {
            return null;
        }
        // A little to the side each time, so the holes do not all land in one spot.
        double side = random == null ? 0 : (random.nextDouble() - 0.5) * 0.12;
        double cos = Math.cos(side);
        double sin = Math.sin(side);
        double ux = (dx * cos - dz * sin) / len;
        double uz = (dx * sin + dz * cos) / len;
        int prev = Integer.MIN_VALUE;
        for (double t = 2.0; t <= Math.min(reach, len); t += 0.5) {
            int x = (int) Math.floor(from.x + ux * t);
            int z = (int) Math.floor(from.z + uz * t);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                return null;
            }
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (prev != Integer.MIN_VALUE && h - prev >= 2) {
                return new Wall(x, z, prev, h, Direction.getApproximateNearest(-ux, 0, -uz));
            }
            prev = h;
        }
        return null;
    }

    /** A column of the wall (a few blocks to either side) a rope can go up, or null. */
    private static Wall climbable(ServerLevel level, Wall wall) {
        Direction across = wall.face().getClockWise();
        for (int o : new int[] {0, 1, -1, 2, -2, 3, -3}) {
            int x = wall.x() + across.getStepX() * o;
            int z = wall.z() + across.getStepZ() * o;
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos bottom = new BlockPos(x, wall.ground() + dy, z);
                BlockPos column = bottom.relative(wall.face());
                BlockState below = level.getBlockState(column.below());
                if (below.getCollisionShape(level, column.below()).isEmpty()) {
                    continue;
                }
                int h = Besiegers.climbHeight(level, bottom, wall.face());
                if (h > 1) {
                    return new Wall(x, z, wall.ground() + dy, wall.ground() + dy + h, wall.face());
                }
            }
        }
        return null;
    }

    private boolean shootBolt(Field f, SiegeConfig cfg, Machine machine, long now) {
        ServerLevel level = machine.level;
        Vec3 muzzle = machine.at(0, 1.75, 2.2);
        double groundY = f.anchor().y;
        ServerPlayer best = null;
        double bestScore = Double.MAX_VALUE;
        for (ServerPlayer p : f.defenders()) {
            if (p.level() != level || p.isSpectator()) {
                continue;
            }
            double d = p.distanceToSqr(muzzle);
            if (d > 72 * 72 || d < 9) {
                continue;
            }
            Vec3 eye = p.getEyePosition();
            BlockHitResult hit = level.clip(new ClipContext(muzzle, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) {
                continue;   // in cover
            }
            boolean high = p.getY() >= groundY + 3.0 || p.isPassenger();
            double score = (high ? 0 : 1_000_000) + d;
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (best == null) {
            return false;
        }
        Vec3 aim = best.getEyePosition().add(best.getDeltaMovement().scale(6)).subtract(0, 0.35, 0);
        turnTo(machine, aim);
        Vec3 dir = aim.subtract(muzzle);
        Arrow bolt = new Arrow(level, muzzle.x, muzzle.y, muzzle.z, new ItemStack(Items.ARROW), null);
        Entity box = level.getEntity(machine.hitbox);
        if (box != null) {
            bolt.setOwner(box);
        }
        bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
        bolt.setNoGravity(true);
        float speed = 3.4f;
        bolt.setBaseDamage(damage(f, cfg, 7.0) / speed);
        bolt.setCritArrow(true);
        bolt.shoot(dir.x, dir.y, dir.z, speed, 0.4f);
        level.addFreshEntity(bolt);
        sound(level, machine.base, SoundEvents.CROSSBOW_SHOOT, 2.0f, 0.45f);
        return true;
    }

    private void turnTo(Machine machine, Vec3 target) {
        Vec3 pivot = machine.at(0, 1.6, 0);
        Vec3 d = target.subtract(pivot);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        machine.pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.max(0.1, flat)));
        Rigs.turn(machine.level, machine.rig, machine.yaw, machine.pitch);
    }

    private void swing(Machine machine, long now) {
        machine.swung = now;
    }

    /** The throwing arm: it swings forward when a shot goes and is wound back slowly. */
    private void animate(Machine machine, long now) {
        if (machine.swung < 0) {
            return;
        }
        MachineModels.Model model = MachineModels.of(machine.kind);
        long t = now - machine.swung;
        float pitch;
        if (t <= 6) {
            pitch = model.rest() + (model.thrown() - model.rest()) * (t / 6f);
        } else if (t <= 16) {
            pitch = model.thrown();
        } else if (t <= 56) {
            pitch = model.thrown() + (model.rest() - model.thrown()) * ((t - 16) / 40f);
        } else {
            pitch = model.rest();
            machine.swung = -1L;
        }
        machine.pitch = pitch;
        Rigs.turn(machine.level, machine.rig, machine.yaw, pitch);
    }

    // ------------------------------------------------------------------ shots in the air

    private void launch(Machine machine, Vec3 from, Vec3 to, long start, int flight, double arc, BlockState shown, float size, Wall wall) {
        Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, machine.level);
        ((BlockDisplayAccessor) display).nightsiege$setBlockState(shown);
        ((DisplayAccessor) display).nightsiege$setTransformation(new Transformation(
                new Vector3f(-size / 2f, -size / 2f, -size / 2f), new Quaternionf(), new Vector3f(size, size, size), new Quaternionf()));
        ((DisplayAccessor) display).nightsiege$setPosRotInterpolationDuration(2);
        display.setPos(from.x, from.y, from.z);
        display.addTag(TAG_MACHINE);
        LIVE.add(display.getUUID());
        machine.level.addFreshEntity(display);
        shots.add(new Shot(machine, from, to, start, flight, arc, display.getUUID(), wall));
    }

    private void tickShots(Field night, SiegeConfig cfg, long now) {
        Iterator<Shot> it = shots.iterator();
        List<Shot> landed = new ArrayList<>();
        while (it.hasNext()) {
            Shot shot = it.next();
            ServerLevel level = shot.machine.level;
            Entity display = level.getEntity(shot.display);
            double t = (now - shot.start) / (double) shot.flight;
            if (t < 0) {
                continue;
            }
            if (t >= 1.0) {
                if (display != null) {
                    display.discard();
                }
                LIVE.remove(shot.display);
                it.remove();
                landed.add(shot);
                continue;
            }
            Vec3 p = shot.from.lerp(shot.to, t).add(0, shot.arc * 4.0 * t * (1.0 - t), 0);
            if (display != null) {
                display.setPos(p.x, p.y, p.z);
            }
            ParticleOptions trail = switch (shot.machine.kind) {
                case FIRE -> ParticleTypes.FLAME;
                case PLAGUE -> new DustParticleOptions(0x5A7A1E, 1.4f);
                case HOOK -> ParticleTypes.CRIT;
                default -> ParticleTypes.SMOKE;
            };
            level.sendParticles(trail, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.01);
            if (shot.machine.kind == SiegeMachineKind.MORTAR && now % 5 == 0) {
                BossService.ring(level, new DustParticleOptions(0xE01010, 1.6f), shot.to.add(0, 0.1, 0), 3.5, 22);
            }
        }
        for (Shot shot : landed) {
            Field f = shot.machine.trial ? trialField(shot.machine) : night;
            if (f != null) {
                try {
                    land(f, cfg, shot, now);
                } catch (RuntimeException e) {
                    NightSiegeMod.LOGGER.warn("[Night Siege] Einschlag {}: {}", shot.machine.kind.id(), e.toString());
                }
            }
        }
    }

    private void land(Field f, SiegeConfig cfg, Shot shot, long now) {
        Machine machine = shot.machine;
        ServerLevel level = machine.level;
        Vec3 at = shot.to;
        switch (machine.kind) {
            case TREBUCHET -> {
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.6, 0.6, 0.6, 0);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()),
                        at.x, at.y, at.z, 40, 0.8, 0.8, 0.8, 0.2);
                sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2.2f, 0.6f);
                sound(level, at, SoundEvents.ANVIL_LAND, 1.4f, 0.5f);
                if (!machine.trial) {
                    crater(level, BlockPos.containing(at.x - (shot.wall == null ? 0 : shot.wall.face().getStepX() * 0.8), at.y,
                            at.z - (shot.wall == null ? 0 : shot.wall.face().getStepZ() * 0.8)), 2.2, cfg);
                }
                blast(f, cfg, machine, at, 3.5, 11.0, 1.0);
            }
            case MORTAR -> {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
                sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2.4f, 0.8f);
                blast(f, cfg, machine, at, 4.0, 12.0, 1.2);
            }
            case FIRE -> {
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 60, 1.6, 0.4, 1.6, 0.05);
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 10, 1.0, 0.2, 1.0, 0);
                sound(level, at, SoundEvents.FIRECHARGE_USE, 1.6f, 0.7f);
                sound(level, at, SoundEvents.GLASS_BREAK, 1.0f, 0.6f);
                blast(f, cfg, machine, at, 3.0, 4.0, 0.3);
                zone(machine, true, at, 3.0, now + 200L, Blocks.MAGMA_BLOCK.defaultBlockState());
            }
            case PLAGUE -> {
                level.sendParticles(new DustParticleOptions(0x6B8E23, 2.0f), at.x, at.y + 0.5, at.z, 50, 1.8, 0.6, 1.8, 0);
                sound(level, at, SoundEvents.SLIME_SQUISH, 1.6f, 0.5f);
                sound(level, at, SoundEvents.ZOMBIE_INFECT, 1.0f, 0.6f);
                zone(machine, false, at, 3.5, now + 240L, Blocks.BONE_BLOCK.defaultBlockState());
            }
            case HOOK -> {
                sound(level, at, SoundEvents.CHAIN_PLACE, 1.6f, 0.6f);
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.2);
                if (shot.wall != null && !machine.trial) {
                    rope(machine, shot.wall);
                }
            }
            default -> {
            }
        }
    }

    /** Damage around an impact for the defenders there (through armour and shields like a blow). */
    private void blast(Field f, SiegeConfig cfg, Machine machine, Vec3 at, double radius, double base, double push) {
        ServerLevel level = machine.level;
        Entity box = level.getEntity(machine.hitbox);
        DamageSource source = box instanceof LivingEntity l ? level.damageSources().mobAttack(l) : level.damageSources().generic();
        for (ServerPlayer p : f.defenders()) {
            if (p.level() != level) {
                continue;
            }
            double d = p.position().add(0, 0.9, 0).distanceTo(at);
            if (d > radius) {
                continue;
            }
            float dmg = (float) (damage(f, cfg, base) * (1.0 - 0.6 * d / radius));
            p.invulnerableTime = 0;
            p.hurtServer(level, source, dmg);
            if (push > 0) {
                BossService.knock(p, at, push, 0.35);
            }
        }
    }

    private static double damage(Field f, SiegeConfig cfg, double base) {
        return base * SiegeMath.damageMultiplier(f.level(), cfg) * cfg.machineDamageMultiplier;
    }

    /** A hole in the wall, written down so the night's end puts it back. */
    private static void crater(ServerLevel level, BlockPos middle, double radius, SiegeConfig cfg) {
        if (!NightDamage.recording(level)) {
            return;   // only in a night (a try-out breaks nothing)
        }
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = dx * dx + dy * dy * 1.2 + dz * dz;
                    if (d2 > radius * radius || d2 > (radius - 0.7) * (radius - 0.7) && level.getRandom().nextFloat() < 0.45f) {
                        continue;
                    }
                    BlockPos pos = middle.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.is(Blocks.BEDROCK) || state.hasBlockEntity()
                            || ModContent.isCore(state) || ModContent.trapOf(state) != null
                            || BlockGuard.isProtected(Demolition.blockId(state), cfg)) {
                        continue;
                    }
                    NightDamage.record(level, pos, state);
                    level.destroyBlock(pos, false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ fire, plague, ropes

    private void zone(Machine machine, boolean fire, Vec3 at, double radius, long until, BlockState shown) {
        Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, machine.level);
        ((BlockDisplayAccessor) display).nightsiege$setBlockState(shown);
        float size = 0.7f;
        ((DisplayAccessor) display).nightsiege$setTransformation(new Transformation(
                new Vector3f(-size / 2f, 0f, -size / 2f), new Quaternionf(), new Vector3f(size, size * 0.6f, size), new Quaternionf()));
        display.setPos(at.x, at.y, at.z);
        display.addTag(TAG_MACHINE);
        LIVE.add(display.getUUID());
        machine.level.addFreshEntity(display);
        zones.add(new Zone(machine, fire, at, radius, until, display.getUUID()));
    }

    private void tickZones(Field night, SiegeConfig cfg, long now) {
        if (now % 10 != 0) {
            return;
        }
        Iterator<Zone> it = zones.iterator();
        while (it.hasNext()) {
            Zone zone = it.next();
            ServerLevel level = zone.machine.level;
            boolean doused = zone.fire && water(level, zone.center, zone.radius);
            if (now >= zone.until || doused) {
                Entity e = level.getEntity(zone.display);
                if (e != null) {
                    e.discard();
                }
                LIVE.remove(zone.display);
                if (doused) {
                    sound(level, zone.center, SoundEvents.FIRE_EXTINGUISH, 1.2f, 1.0f);
                    level.sendParticles(ParticleTypes.CLOUD, zone.center.x, zone.center.y + 0.4, zone.center.z, 20, 1.2, 0.3, 1.2, 0.02);
                }
                it.remove();
                continue;
            }
            Vec3 c = zone.center;
            if (zone.fire) {
                level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.2, c.z, 18, zone.radius * 0.5, 0.2, zone.radius * 0.5, 0.01);
                level.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.8, c.z, 6, zone.radius * 0.4, 0.3, zone.radius * 0.4, 0.01);
            } else {
                level.sendParticles(new DustParticleOptions(0x7BA03A, 1.8f), c.x, c.y + 0.6, c.z, 22,
                        zone.radius * 0.5, 0.5, zone.radius * 0.5, 0);
                level.sendParticles(ParticleTypes.SNEEZE, c.x, c.y + 0.4, c.z, 4, zone.radius * 0.4, 0.2, zone.radius * 0.4, 0.01);
            }
            Field f = zone.machine.trial ? trialField(zone.machine) : night;
            if (f == null) {
                continue;
            }
            for (ServerPlayer p : f.defenders()) {
                if (p.level() != level || Math.abs(p.getY() - c.y) > 2.5
                        || p.position().subtract(c).horizontalDistanceSqr() > zone.radius * zone.radius) {
                    continue;
                }
                if (zone.fire) {
                    if (!p.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                        p.igniteForSeconds(3f);
                    }
                } else {
                    p.addEffect(new MobEffectInstance(MobEffects.POISON, 70, 1));
                }
            }
        }
    }

    /** Water in the fire (a bucket put out puts it out). */
    private static boolean water(ServerLevel level, Vec3 c, double radius) {
        int r = (int) Math.ceil(radius);
        BlockPos mid = BlockPos.containing(c);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (level.getFluidState(mid.offset(dx, dy, dz)).is(net.minecraft.tags.FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The grappling hook caught: a rope of ladders up the outside of the wall. */
    private void rope(Machine machine, Wall wall) {
        ServerLevel level = machine.level;
        BlockPos bottom = wall.bottom();
        BlockPos column = bottom.relative(wall.face());
        int height = Besiegers.climbHeight(level, bottom, wall.face());
        if (height < 2) {
            return;
        }
        Rope rope = new Rope();
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, wall.face());
        for (int i = 0; i < height; i++) {
            BlockPos pos = column.above(i);
            if (!ladder.canSurvive(level, pos) || !TempBlocks.place(level, pos, ladder)) {
                break;
            }
            rope.rungs.add(pos);
        }
        if (rope.rungs.isEmpty()) {
            return;
        }
        Display.BlockDisplay hook = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        ((BlockDisplayAccessor) hook).nightsiege$setBlockState(Blocks.TRIPWIRE_HOOK.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, wall.face()));
        BlockPos top = bottom.above(height);
        hook.setPos(top.getX() + wall.face().getStepX() * 0.2, top.getY(), top.getZ() + wall.face().getStepZ() * 0.2);
        hook.addTag(TAG_MACHINE);
        LIVE.add(hook.getUUID());
        level.addFreshEntity(hook);
        rope.hook = hook.getUUID();
        machine.ropes.add(rope);
        sound(level, Vec3.atCenterOf(top), SoundEvents.LADDER_PLACE, 1.4f, 0.6f);
    }

    /** A rope one rung of which was knocked off falls as a whole (the hook was struck off). */
    private void tickRopes(Machine machine) {
        Iterator<Rope> it = machine.ropes.iterator();
        while (it.hasNext()) {
            Rope rope = it.next();
            boolean whole = true;
            for (BlockPos pos : rope.rungs) {
                if (!machine.level.getBlockState(pos).is(Blocks.LADDER)) {
                    whole = false;
                    break;
                }
            }
            if (whole) {
                continue;
            }
            dropRope(machine.level, rope);
            it.remove();
        }
    }

    private static void dropRope(ServerLevel level, Rope rope) {
        for (BlockPos pos : rope.rungs) {
            if (TempBlocks.isTemp(level, pos)) {
                TempBlocks.remove(level, pos);
            }
        }
        if (rope.hook != null) {
            Entity e = level.getEntity(rope.hook);
            if (e != null) {
                sound(level, e.position(), SoundEvents.CHAIN_BREAK, 1.2f, 0.8f);
                e.discard();
            }
            LIVE.remove(rope.hook);
        }
    }

    // ------------------------------------------------------------------ damage and the end

    /** Before a death: a machine's hit box does not die but takes its machine with it. */
    public boolean allowDeath(LivingEntity entity, DamageSource source) {
        Machine machine = entity.entityTags().contains(TAG_MACHINE) ? owners.get(entity.getUUID()) : null;
        if (machine == null || !machine.hitbox.equals(entity.getUUID())) {
            return true;
        }
        ServerPlayer by = source != null && source.getEntity() instanceof ServerPlayer p ? p : null;
        if (by == null && source != null && Companions.isCompanion(source.getEntity())) {
            by = Companions.ownerPlayer(source.getEntity()) instanceof ServerPlayer p ? p : null;
        }
        destroy(machine, by, true);
        return false;
    }

    /**
     * Hits on a hit box do nothing (Simon, 27 October 2026: arrows from afar would be too easy):
     * a machine is sabotaged by hand. Only a player in creative can strike one down (to try out).
     */
    public boolean allowDamage(LivingEntity victim, DamageSource source) {
        if (!victim.entityTags().contains(TAG_MACHINE)) {
            return true;
        }
        if (source.getEntity() instanceof ServerPlayer p) {
            if (p.getAbilities().instabuild) {
                return true;
            }
            actionBar(p, Component.translatable("nightsiege.machine.no_damage").withStyle(ChatFormatting.YELLOW));
        }
        return false;
    }

    /**
     * Sabotage: a defender holds right-click on a machine (the client repeats it every few
     * ticks while the button is held). After {@code machineSabotageSeconds} of work it falls
     * apart; letting go starts over, a hit taken interrupts it.
     */
    public boolean onUse(ServerPlayer player, Entity entity, SiegeConfig cfg) {
        Machine machine = entity == null ? null : owners.get(entity.getUUID());
        if (machine == null || !machine.hitbox.equals(entity.getUUID()) || player.isSpectator() || Drones.isDrone(player)) {
            return false;
        }
        SiegeManager m = SiegeManager.get();
        boolean creative = player.getAbilities().instabuild;
        if (!creative && !machine.trial && (m == null || !m.isDefending(player))) {
            return false;
        }
        if (player.distanceToSqr(entity) > 4.5 * 4.5) {
            actionBar(player, Component.translatable("nightsiege.machine.sabotage_far").withStyle(ChatFormatting.GRAY));
            return true;
        }
        long now = machine.level.getGameTime();
        long gap = machine.sabotageAt < 0 ? 0 : now - machine.sabotageAt;
        if (gap > 20) {
            machine.sabotage = 0;   // let go in between: from the start
        } else {
            machine.sabotage += (int) Math.min(gap, 8);   // a lag spike gives no free work
        }
        machine.sabotageAt = now;
        saboteurs.put(player.getUUID(), machine);
        int need = creative ? 1 : Math.max(1, cfg.machineSabotageSeconds) * 20;
        double done = Math.min(1.0, machine.sabotage / (double) need);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < Math.round(done * 10) ? '█' : '░');
        }
        actionBar(player, Component.translatable("nightsiege.machine.sabotage",
                Component.translatable(machine.kind.translationKey()), bar.toString(), (int) Math.round(done * 100))
                .withStyle(ChatFormatting.GOLD));
        if (gap > 0) {
            Vec3 at = entity.position().add(0, 1.0, 0);
            machine.level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.5, 0.5, 0.5, 0.1);
            sound(machine.level, at, now % 8 < 4 ? SoundEvents.CHAIN_HIT : SoundEvents.WOOD_HIT, 0.8f, 0.7f + (float) done * 0.6f);
        }
        if (machine.sabotage >= need) {
            saboteurs.remove(player.getUUID());
            destroy(machine, player, true);
        }
        return true;
    }

    /**
     * On the client: a machine's hit box (an invisible husk named after a machine). Right-click
     * on it must count as handled there too, else a bow or shield in hand starts being used and
     * the client stops repeating the click.
     */
    public static boolean looksLikeMachine(Entity entity) {
        return entity instanceof net.minecraft.world.entity.monster.zombie.Husk && entity.isInvisible()
                && entity.getCustomName() != null
                && entity.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().startsWith("nightsiege.machine.");
    }

    /** A hit taken interrupts a sabotage. */
    public void afterDamage(LivingEntity victim, float taken) {
        if (taken <= 0 || saboteurs.isEmpty() || !(victim instanceof ServerPlayer player)) {
            return;
        }
        Machine machine = saboteurs.remove(player.getUUID());
        if (machine != null && machine.sabotage > 0 && machine.level.getGameTime() - machine.sabotageAt <= 20) {
            machine.sabotage = 0;
            machine.sabotageAt = -1L;
            actionBar(player, Component.translatable("nightsiege.machine.sabotage_hit").withStyle(ChatFormatting.RED));
        }
    }

    private static void actionBar(ServerPlayer player, Component text) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(text));
    }

    private void destroy(Machine machine, ServerPlayer by, boolean announce) {
        ServerLevel level = machine.level;
        machines.remove(machine);
        Rigs.remove(level, machine.rig);
        Entity box = level.getEntity(machine.hitbox);
        if (box != null) {
            box.discard();
        }
        LIVE.remove(machine.hitbox);
        owners.remove(machine.hitbox);
        for (Rope rope : machine.ropes) {
            dropRope(level, rope);
        }
        machine.ropes.clear();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, machine.base.x, machine.base.y + 1.5, machine.base.z, 1, 0, 0, 0, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_PLANKS.defaultBlockState()),
                machine.base.x, machine.base.y + 1.5, machine.base.z, 60, 1.2, 1.2, 1.2, 0.2);
        sound(level, machine.base, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 2.0f, 0.6f);
        if (announce && !machine.trial) {
            Component name = Component.translatable(machine.kind.translationKey());
            Component msg = by == null ? Component.translatable("nightsiege.machine.destroyed", name)
                    : Component.translatable("nightsiege.machine.destroyed_by", name, by.getDisplayName());
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                if (bar != null && bar.getPlayers().contains(p)) {
                    p.sendSystemMessage(msg.copy().withStyle(ChatFormatting.GREEN));
                }
            }
            if (by != null && SiegeManager.get() != null) {
                SiegeManager.get().machineDestroyed(by);
            }
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Maschine {} zerstört{}", machine.kind.id(), by == null ? "" : " (sabotiert von " + by.getScoreboardName() + ")");
        if (bar != null) {
            long left = machines.stream().filter(m -> !m.trial).count();
            if (left == 0) {
                bar.removeAllPlayers();
                bar = null;
            }
        }
    }

    private void refreshBar(List<ServerPlayer> watchers) {
        int count = 0;
        for (Machine machine : machines) {
            if (!machine.trial) {
                count++;
            }
        }
        if (count == 0) {
            bar.removeAllPlayers();
            bar = null;
            return;
        }
        bar.setName(Component.translatable("nightsiege.machine.bar", count));
        bar.setProgress((float) Math.max(0.0, Math.min(1.0, count / (double) Math.max(count, nightTotal))));
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
            if (!watchers.contains(p)) {
                bar.removePlayer(p);
            }
        }
        for (ServerPlayer p : watchers) {
            bar.addPlayer(p);
        }
    }

    /** The night is over: every machine of the night with its crew goes (try-outs stay). */
    void reset(MinecraftServer server) {
        planned.clear();
        setUpAt = -1L;
        nightTotal = 0;
        saboteurs.clear();
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        for (Machine machine : new ArrayList<>(machines)) {
            if (!machine.trial) {
                remove(machine);
            }
        }
        // The crews of machines struck down earlier in the night go too.
        for (Map.Entry<UUID, Machine> e : new ArrayList<>(owners.entrySet())) {
            if (!e.getValue().trial) {
                discard(e.getValue().level, e.getKey());
                owners.remove(e.getKey());
            }
        }
        shots.removeIf(shot -> {
            if (shot.machine.trial) {
                return false;
            }
            discard(shot.machine.level, shot.display);
            return true;
        });
        zones.removeIf(zone -> {
            if (zone.machine.trial) {
                return false;
            }
            discard(zone.machine.level, zone.display);
            return true;
        });
    }

    /** Every machine, try-outs too (admin). Returns how many went. */
    public int removeAll() {
        int n = machines.size();
        for (Machine machine : new ArrayList<>(machines)) {
            remove(machine);
        }
        for (Map.Entry<UUID, Machine> e : new ArrayList<>(owners.entrySet())) {
            discard(e.getValue().level, e.getKey());
        }
        owners.clear();
        for (Shot shot : shots) {
            discard(shot.machine.level, shot.display);
        }
        shots.clear();
        for (Zone zone : zones) {
            discard(zone.machine.level, zone.display);
        }
        zones.clear();
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        return n;
    }

    private void remove(Machine machine) {
        ServerLevel level = machine.level;
        machines.remove(machine);
        Rigs.remove(level, machine.rig);
        discard(level, machine.hitbox);
        owners.remove(machine.hitbox);
        for (UUID id : machine.workers) {
            discard(level, id);
            owners.remove(id);
        }
        for (UUID id : machine.guards) {
            discard(level, id);
            owners.remove(id);
        }
        for (Rope rope : machine.ropes) {
            dropRope(level, rope);
        }
        machine.ropes.clear();
    }

    private static void discard(ServerLevel level, UUID id) {
        LIVE.remove(id);
        Entity e = id == null ? null : level.getEntity(id);
        if (e != null) {
            e.discard();
        }
    }

    /** A machine part, shot or crew member from before a restart: it goes when it loads. */
    public static boolean stray(Entity entity) {
        return (entity.entityTags().contains(TAG_MACHINE) || entity.entityTags().contains(TAG_CREW))
                && !LIVE.contains(entity.getUUID());
    }

    // ------------------------------------------------------------------ fields

    private static Field field(BossService.Host host) {
        return new Field() {
            @Override
            public ServerLevel world() {
                return host.world();
            }

            @Override
            public Vec3 anchor() {
                BlockPos a = host.anchor();
                return a == null ? Vec3.ZERO : Vec3.atBottomCenterOf(a);
            }

            @Override
            public List<ServerPlayer> defenders() {
                return host.defenders();
            }

            @Override
            public int level() {
                return host.level();
            }

            @Override
            public boolean insideBase(double x, double z) {
                return host.insideBase(x, z);
            }

            @Override
            public double baseEdge(double x, double z) {
                return host.baseEdge(x, z);
            }
        };
    }

    /** A try-out from a spawn egg aims at the core (or the spot it was set up towards) and at players near. */
    private static Field trialField(Machine machine) {
        return new Field() {
            @Override
            public ServerLevel world() {
                return machine.level;
            }

            @Override
            public Vec3 anchor() {
                return machine.trialTarget;
            }

            @Override
            public List<ServerPlayer> defenders() {
                List<ServerPlayer> list = new ArrayList<>();
                for (ServerPlayer p : machine.level.players()) {
                    if (!p.isSpectator() && p.distanceToSqr(machine.base) < 90 * 90) {
                        list.add(p);
                    }
                }
                return list;
            }

            @Override
            public int level() {
                return SiegeManager.get() == null ? 1 : SiegeManager.get().currentLevel();
            }

            @Override
            public boolean insideBase(double x, double z) {
                return machine.trialTarget.distanceToSqr(x, machine.trialTarget.y, z) < 12 * 12;
            }

            @Override
            public double baseEdge(double x, double z) {
                return 12;
            }
        };
    }

    // ------------------------------------------------------------------ spawn eggs and tests

    /**
     * A try-out machine from a spawn egg (creative): it stands where it is put, faces away from
     * the player and aims at the core if it is near, else 40 blocks ahead.
     */
    public void trial(ServerLevel level, SiegeMachineKind kind, Vec3 at, float playerYaw) {
        SiegeManager m = SiegeManager.get();
        Vec3 target = null;
        CoreRecord core = m == null ? null : m.mainCore();
        if (core != null && SiegeManager.dimensionName(level).equals(core.dimension)
                && at.distanceToSqr(core.x + 0.5, at.y, core.z + 0.5) < 150 * 150) {
            target = new Vec3(core.x + 0.5, core.y, core.z + 0.5);
        }
        if (target == null) {
            double r = Math.toRadians(playerYaw);
            target = groundAt(level, at.add(-Math.sin(r) * 40, 0, Math.cos(r) * 40));
        }
        float yaw = yawTowards(at, target);
        SiegeConfig cfg = m == null ? new SiegeConfig() : m.config();
        Machine machine = spawn(level, kind, at, yaw, true, target, m == null ? 1 : m.currentLevel(), 1, cfg);
        if (machine != null) {
            machine.nextShot = level.getGameTime() + 40;
        }
    }

    /** Tests: one machine of the night set up now at a spot, aiming at the core. */
    public boolean testSpawn(BossService.Host host, SiegeConfig cfg, SiegeMachineKind kind, Vec3 at) {
        BlockPos anchor = host.anchor();
        if (anchor == null) {
            return false;
        }
        Machine machine = spawn(host.world(), kind, at, yawTowards(at, Vec3.atBottomCenterOf(anchor)), false, null,
                host.level(), host.defenders().size(), cfg);
        if (machine == null) {
            return false;
        }
        machine.nextShot = host.world().getGameTime() + 20;
        nightTotal++;
        if (bar == null) {
            bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("nightsiege.machine.bar", 1),
                    BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        }
        return true;
    }

    /** Tests: the hit boxes of the machines standing. */
    public List<UUID> testHitboxes() {
        List<UUID> list = new ArrayList<>();
        for (Machine machine : machines) {
            list.add(machine.hitbox);
        }
        return list;
    }

    /** Tests: ropes hanging on the walls. */
    public int testRopes() {
        int n = 0;
        for (Machine machine : machines) {
            n += machine.ropes.size();
        }
        return n;
    }

    /** Tests: workers and guards of all machines alive. */
    public int testCrew() {
        int n = 0;
        for (Machine machine : machines) {
            for (UUID id : machine.workers) {
                Entity e = machine.level.getEntity(id);
                n += e != null && e.isAlive() ? 1 : 0;
            }
            for (UUID id : machine.guards) {
                Entity e = machine.level.getEntity(id);
                n += e != null && e.isAlive() ? 1 : 0;
            }
        }
        return n;
    }

    /** Tests: every machine shoots at its next tick. */
    public void testFireNow(long now) {
        for (Machine machine : machines) {
            machine.nextShot = now;
        }
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    private static void sound(ServerLevel level, Vec3 at, net.minecraft.core.Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }
}
