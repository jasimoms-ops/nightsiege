package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The raid's plan (Simon, 27 October 2026: "the raid has to keep to the base better"; "the melee
 * monsters break no blocks, they wait for a ladder, a creeper or the trebuchet").
 *
 * <ul>
 *   <li>Every wave comes from one direction, from level {@code raidFlankFromLevel} on with a flank
 *   from a second one; the bar and a raid horn from that side tell the defenders where.</li>
 *   <li>The monsters spawn on open ground that walks to the wall (see {@link BaseScan}).</li>
 *   <li>Melee monsters gather in front of the wall and wait. As soon as a way in is open on
 *   their side (a ladder at the wall, a crater, a hole of the trebuchet, an open gate or an open
 *   gateway) they storm through it; after a while of waiting they also take one on another side.</li>
 *   <li>Archers and casters stand in a line in front of the wall and shoot at whoever they see.</li>
 *   <li>Spiders climb anyway; in an open base everybody goes straight for the defenders.</li>
 *   <li>Outside the walls a monster only goes for a defender who comes out to it
 *   ({@code raidLeash}); nobody runs off after a player far away and stays there.</li>
 *   <li>A monster that makes no headway takes another way; one stuck for long (in a hole, in
 *   water) comes back from the spawn ground, out of everybody's sight.</li>
 * </ul>
 */
public final class RaidPlan {

    public enum Kind { MELEE, RANGED, CLIMBER }

    public enum State { APPROACH, HOLD, ASSAULT, HUNT }

    /** How a wave was planned (for the bar and the gametest). */
    public record Wave(int number, double main, double flank, boolean gate) {
        public boolean hasFlank() {
            return !Double.isNaN(flank);
        }
    }

    /** A way into the base: a gate, an open gateway, a hole in the wall or a ladder. */
    public static final class Entry {
        public final String kind;
        public final BlockPos at;
        public final BlockPos front;
        public final double angle;
        boolean open;
        final long since;

        Entry(String kind, BlockPos at, BlockPos front, double angle, long since) {
            this.kind = kind;
            this.at = at;
            this.front = front;
            this.angle = angle;
            this.since = since;
        }

        public boolean isOpen() {
            return open;
        }
    }

    private static final class Order {
        final Kind kind;
        final double angle;
        BlockPos rally;
        State state;
        Entry entry;
        long holdSince = -1;
        double lastX;
        double lastZ;
        long movedAt;
        long nextNav;
        BlockPos navGoal;
        long nextMill;

        Order(Kind kind, double angle, State state, long now) {
            this.kind = kind;
            this.angle = angle;
            this.state = state;
            this.movedAt = now;
        }
    }

    private static RaidPlan current;

    private ServerLevel world;
    private BlockPos anchor;
    private BaseScan base;
    private SiegeConfig cfg;
    private Random random = new Random();
    private Consumer<Component> notice = c -> { };
    private final Map<UUID, Order> orders = new HashMap<>();
    private final List<Entry> gates = new ArrayList<>();
    private final List<Entry> holes = new ArrayList<>();
    private final List<Entry> ladders = new ArrayList<>();
    private final LongArrayList pendingHoles = new LongArrayList();
    private Wave lastWave;
    private long lastNoticeAt = Long.MIN_VALUE / 2;

    /** Counters for the gametest and the log. */
    public int rerouted;
    public int returned;
    public int stormed;

    public RaidPlan() {
        current = this;
    }

    // ------------------------------------------------------------------ the night

    public void begin(ServerLevel world, BlockPos anchor, BaseScan base, SiegeConfig cfg, Random random,
                      Consumer<Component> notice) {
        reset();
        current = this;
        if (world == null || anchor == null || base == null || cfg == null || !cfg.raidPlan) {
            return;
        }
        this.world = world;
        this.anchor = anchor;
        this.base = base;
        this.cfg = cfg;
        this.random = random;
        this.notice = notice;
        long now = world.getGameTime();
        for (BaseScan.Gate g : base.gates) {
            Entry e = new Entry(g.arch() ? "arch" : "gate", g.pos(), g.front(), base.angleOf(g.pos().getX() + 0.5, g.pos().getZ() + 0.5), now);
            e.open = g.arch() || gateOpen(world, g.pos());
            gates.add(e);
        }
    }

    public void reset() {
        world = null;
        anchor = null;
        base = null;
        orders.clear();
        gates.clear();
        holes.clear();
        ladders.clear();
        pendingHoles.clear();
        lastWave = null;
        rerouted = 0;
        returned = 0;
        stormed = 0;
    }

    public boolean active() {
        return world != null && base != null && cfg != null && cfg.raidPlan;
    }

    public Wave lastWave() {
        return lastWave;
    }

    public List<Entry> entries() {
        List<Entry> all = new ArrayList<>(gates);
        all.addAll(holes);
        all.addAll(ladders);
        return all;
    }

    // ------------------------------------------------------------------ a wave

    /**
     * Picks where the next wave comes from and tells the defenders. Null when the base has no
     * ground to spawn on (then the old way of spawning stays).
     */
    public Wave planWave(int number, int level) {
        if (!active() || base.spawnSpotCount() == 0) {
            return null;
        }
        double main = Double.NaN;
        boolean atGate = false;
        // Half of the waves go for a gate (if a gate has ground in front of it).
        List<Entry> doors = new ArrayList<>(gates);
        if (!doors.isEmpty() && random.nextBoolean()) {
            Entry g = doors.get(random.nextInt(doors.size()));
            if (base.spawnNear(g.angle, 3, random) != null) {
                main = g.angle;
                atGate = true;
            }
        }
        if (Double.isNaN(main)) {
            for (int tries = 0; tries < 24; tries++) {
                int s = random.nextInt(BaseScan.SLICES);
                if (base.spawnSpots(s).isEmpty()) {
                    continue;
                }
                double a = BaseScan.angleOfSlice(s);
                if (lastWave != null && tries < 16 && angleBetween(a, lastWave.main()) < Math.PI / 3) {
                    continue;   // not twice from the same side
                }
                main = a;
                break;
            }
        }
        if (Double.isNaN(main)) {
            return null;
        }
        double flank = Double.NaN;
        if (level >= cfg.raidFlankFromLevel && cfg.raidFlankShare > 0) {
            for (int tries = 0; tries < 12; tries++) {
                double a = main + (random.nextBoolean() ? 1 : -1) * (Math.PI / 2 + random.nextDouble() * Math.PI / 2);
                if (base.spawnNear(a, 2, random) != null) {
                    flank = a;
                    break;
                }
            }
        }
        lastWave = new Wave(number, main, flank, atGate);
        Component where = Component.translatable("nightsiege.dir." + compass(main));
        Component text = !Double.isNaN(flank)
                ? Component.translatable("nightsiege.raid.wave_flank", number, where,
                        Component.translatable("nightsiege.dir." + compass(flank)))
                : atGate ? Component.translatable("nightsiege.raid.wave_gate", number, where)
                : Component.translatable("nightsiege.raid.wave", number, where);
        notice.accept(text);
        horn(main);
        return lastWave;
    }

    /** The direction of the n-th of {@code count} monsters of the wave (the last ones go to the flank). */
    public double angleFor(Wave wave, int index, int count) {
        if (wave == null) {
            return Double.NaN;
        }
        if (wave.hasFlank() && index >= Math.round(count * (1.0 - cfg.raidFlankShare))) {
            return wave.flank();
        }
        return wave.main();
    }

    /** Ground to spawn on towards an angle, checked that it is still free. */
    public BlockPos spawnSpot(double angle) {
        if (!active() || Double.isNaN(angle)) {
            return null;
        }
        for (int tries = 0; tries < 8; tries++) {
            BlockPos spot = base.spawnNear(angle + (random.nextDouble() - 0.5) * 0.35, tries < 4 ? 3 : 10, random);
            if (spot == null) {
                return null;
            }
            if (world.hasChunk(spot.getX() >> 4, spot.getZ() >> 4) && free(spot)) {
                return spot;
            }
        }
        return null;
    }

    private boolean free(BlockPos spot) {
        BlockState feet = world.getBlockState(spot);
        BlockState head = world.getBlockState(spot.above());
        return feet.getCollisionShape(world, spot).isEmpty() && head.getCollisionShape(world, spot.above()).isEmpty()
                && feet.getFluidState().isEmpty() && !world.getBlockState(spot.below()).getCollisionShape(world, spot.below()).isEmpty();
    }

    /** Takes a monster of a wave into the plan. Phantoms, vexes and the attackers with a job of their own stay out. */
    public void enlist(Mob mob, double angle) {
        if (!active() || mob == null || mob.isPassenger() || !eligible(mob)) {
            return;
        }
        long now = world.getGameTime();
        if (Double.isNaN(angle)) {
            angle = base.angleOf(mob.getX(), mob.getZ());
        }
        Kind kind = kindOf(mob);   // a mount goes with the melee monsters, its archer shoots from it
        State state = !base.closed || kind == Kind.CLIMBER || base.contains(mob.getBlockX(), mob.getBlockZ())
                ? State.HUNT : State.APPROACH;
        Order order = new Order(kind, angle, state, now);
        order.rally = kind == Kind.RANGED ? base.fireNear(angle, 4, random) : base.rallyNear(angle, 4, random);
        if (order.rally == null && state == State.APPROACH) {
            order.state = State.HUNT;   // no ground in front of the wall here: straight on
        }
        order.lastX = mob.getX();
        order.lastZ = mob.getZ();
        orders.put(mob.getUUID(), order);
        mob.setTarget(null);
    }

    /** True for the monsters the plan steers (a wave's ground monsters). */
    public static boolean eligible(Mob mob) {
        if (mob.isNoGravity()) {
            return false;   // phantoms and vexes fly to the walls on their own
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        if (id.equals("phantom") || id.equals("vex") || id.contains("ghast")) {
            return false;
        }
        Foes.Role role = Foes.roleOf(mob);
        return role == null || role == Foes.Role.REVENANT || role == Foes.Role.IRON_KNIGHT;
    }

    public static Kind kindOf(Mob mob) {
        if (mob.getNavigation() instanceof WallClimberNavigation) {
            return Kind.CLIMBER;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        if (mob instanceof RangedAttackMob || mob instanceof SpellcasterIllager || id.contains("breeze")
                || id.contains("blaze") || id.contains("wildfire") || id.contains("archer")) {
            return Kind.RANGED;
        }
        return Kind.MELEE;
    }

    // ------------------------------------------------------------------ targets

    /**
     * Whether a monster of the plan may go for this target: outside the walls only who comes out
     * to it, archers whoever they can shoot, the stormers and the hunters whoever is near.
     */
    public static boolean mayTarget(Mob mob, LivingEntity target) {
        RaidPlan plan = current;
        if (plan == null || !plan.active() || target == null || mob == null) {
            return true;
        }
        Entity root = mob.getRootVehicle();
        Order order = plan.orders.get(root.getUUID());
        if (order == null) {
            return true;
        }
        return plan.allowed(order, kindOf(mob), mob, target);
    }

    private boolean allowed(Order order, Kind kind, Mob mob, LivingEntity target) {
        double d = mob.distanceTo(target);
        if (order.state == State.HUNT) {
            return d <= 32.0;
        }
        // On the way in (and waiting) only who comes out to them: no chasing a defender up on the wall.
        if (kind == Kind.RANGED) {
            return d <= 28.0;
        }
        if (base.closed) {
            if (base.contains(target.getBlockX(), target.getBlockZ())) {
                return false;   // behind the wall: no way there yet
            }
            if (base.near(target.getBlockX(), target.getBlockZ(), 2.5) && target.getY() > mob.getY() + 1.5) {
                return false;   // up on the wall
            }
        }
        return d <= cfg.raidLeash;
    }

    // ------------------------------------------------------------------ the ways in

    /** A block of the night broke (a sapper, the trebuchet, an explosion): maybe the wall is open there. */
    public static void blockBroken(net.minecraft.world.level.Level level, BlockPos pos) {
        RaidPlan plan = current;
        if (plan == null || !plan.active() || level != plan.world || !plan.base.closed) {
            return;
        }
        if (!plan.base.near(pos.getX(), pos.getZ(), 4.0)) {
            return;   // far from the wall
        }
        long cell = BlockPos.asLong(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        if (!plan.pendingHoles.contains(cell) && plan.pendingHoles.size() < 256) {
            plan.pendingHoles.add(cell);
        }
    }

    private void updateEntries(long now) {
        // Gates: open while a defender has opened them (or something broke them).
        for (Entry g : gates) {
            boolean was = g.open;
            g.open = g.kind.equals("arch") || gateOpen(world, g.at);
            if (g.open && !was) {
                tell("nightsiege.raid.breach_gate", g.angle, now);
                log("Weg offen:", g);
            } else if (was && !g.open) {
                log("Weg zu:", g);
            }
        }
        // Holes: each broken spot near the wall once, then every few seconds again (a defender may close it).
        if (!pendingHoles.isEmpty()) {
            LongArrayList cells = new LongArrayList(pendingHoles);
            pendingHoles.clear();
            for (int i = 0; i < cells.size(); i++) {
                long c = cells.getLong(i);
                BlockPos at = new BlockPos(BlockPos.getX(c) * 4 + 2, BlockPos.getY(c) * 4 + 2, BlockPos.getZ(c) * 4 + 2);
                boolean known = false;
                for (Entry h : holes) {
                    if (h.at.distSqr(at) < 25) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    checkHole(at, now);
                }
            }
        }
        if (now % 100 == 0) {
            Iterator<Entry> it = holes.iterator();
            while (it.hasNext()) {
                Entry h = it.next();
                boolean was = h.open;
                h.open = base.walkInto(world, h.front, h.at, 12, 3000);
                if (was != h.open) {
                    log(h.open ? "Weg wieder offen:" : "Weg zu:", h);
                }
            }
        }
        // Ladders of the night at the wall (the carriers' and the hook ballista's ropes).
        Map<Long, BlockPos> feet = new HashMap<>();
        Map<Long, Integer> rungs = new HashMap<>();
        for (BlockPos p : TempBlocks.all(world, Blocks.LADDER)) {
            if (!world.getBlockState(p).is(Blocks.LADDER) || base.contains(p.getX(), p.getZ()) || !base.near(p.getX(), p.getZ(), 3.0)) {
                continue;
            }
            long col = ((long) p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL);
            BlockPos foot = feet.get(col);
            if (foot == null || p.getY() < foot.getY()) {
                feet.put(col, p);
            }
            rungs.merge(col, 1, Integer::sum);
        }
        List<Entry> now2 = new ArrayList<>();
        for (Map.Entry<Long, BlockPos> f : feet.entrySet()) {
            if (rungs.getOrDefault(f.getKey(), 0) < 2) {
                continue;
            }
            BlockPos foot = f.getValue();
            Entry known = null;
            for (Entry l : ladders) {
                if (l.at.getX() == foot.getX() && l.at.getZ() == foot.getZ()) {
                    known = l;
                    break;
                }
            }
            if (known == null) {
                known = new Entry("ladder", foot, foot, base.angleOf(foot.getX() + 0.5, foot.getZ() + 0.5), now);
                tell("nightsiege.raid.breach_ladder", known.angle, now);
                log("Weg offen:", known);
            }
            known.open = true;
            now2.add(known);
        }
        ladders.clear();
        ladders.addAll(now2);
    }

    private void checkHole(BlockPos at, long now) {
        BlockPos from = groundOutside(at);
        if (from == null) {
            return;
        }
        if (base.walkInto(world, from, at, 12, 3000)) {
            Entry e = new Entry("hole", at, from, base.angleOf(at.getX() + 0.5, at.getZ() + 0.5), now);
            e.open = true;
            holes.add(e);
            tell("nightsiege.raid.breach_hole", e.angle, now);
            log("Weg offen:", e);
        }
    }

    /** Ground outside the wall near a spot, the nearest a monster can stand on. */
    private BlockPos groundOutside(BlockPos at) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int x = at.getX() + dx;
                int z = at.getZ() + dz;
                if (base.contains(x, z)) {
                    continue;
                }
                for (int dy = -4; dy <= 3; dy++) {
                    BlockPos p = new BlockPos(x, at.getY() + dy, z);
                    if (!free(p)) {
                        continue;
                    }
                    double d = p.distSqr(at);
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    static boolean gateOpen(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!BaseScan.isDoor(state)) {
            return state.getCollisionShape(level, pos).isEmpty();   // broken: open for good
        }
        return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
    }

    private void log(String what, Entry e) {
        com.mki.nightsiege.NightSiegeMod.LOGGER.info("[Night Siege] Angriffsplan: {} {} im {} bei {} {} {}", what, e.kind, compass(e.angle),
                e.at.getX(), e.at.getY(), e.at.getZ());
    }

    private void tell(String key, double angle, long now) {
        if (now - lastNoticeAt < 80) {
            return;
        }
        lastNoticeAt = now;
        notice.accept(Component.translatable(key, Component.translatable("nightsiege.dir." + compass(angle))));
    }

    /** The open way in a monster on that side takes; after a long wait also one on another side. */
    private Entry entryFor(Order order, Mob mob, long now) {
        Entry best = null;
        double bestScore = Double.MAX_VALUE;
        boolean longWait = order.holdSince >= 0 && now - order.holdSince > cfg.raidRegroupSeconds * 20L;
        for (List<Entry> list : List.of(gates, holes, ladders)) {
            for (Entry e : list) {
                if (!e.open) {
                    continue;
                }
                double turn = angleBetween(e.angle, order.angle);
                if (turn > Math.PI * 0.4 && !longWait) {
                    continue;   // another side: only after a long wait
                }
                double score = turn * 30.0 + Math.sqrt(mob.distanceToSqr(Vec3.atCenterOf(e.front)));
                if (score < bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ every few ticks

    /** Steers the monsters of the plan (called every other tick). */
    public void tick(long now, Collection<UUID> live, List<ServerPlayer> defenders, LadderClimb climbing) {
        if (!active()) {
            return;
        }
        if (now % 20 == 0) {
            updateEntries(now);
        }
        if (now % 10 != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Order>> it = orders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Order> e = it.next();
            UUID id = e.getKey();
            Order order = e.getValue();
            Entity entity = world.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive() || !live.contains(id)) {
                it.remove();
                continue;
            }
            try {
                steer(mob, order, now, defenders, climbing);
            } catch (RuntimeException ex) {
                it.remove();   // never take the night down for one monster
            }
        }
    }

    private void steer(Mob mob, Order order, long now, List<ServerPlayer> defenders, LadderClimb climbing) {
        if (Besiegers.busy(mob) || climbing.busy(mob.getUUID())) {
            order.movedAt = now;   // carrying a ladder or on one: their own way
            return;
        }
        boolean inside = base.closed && base.contains(mob.getBlockX(), mob.getBlockZ());
        if (inside && order.state != State.HUNT) {
            order.state = State.HUNT;   // over the wall: now for the defenders
            stormed++;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && (!target.isAlive() || !allowed(order, order.kind, mob, target))) {
            mob.setTarget(null);
            target = null;
        }
        double mx = mob.getX() - order.lastX;
        double mz = mob.getZ() - order.lastZ;
        if (mx * mx + mz * mz > 2.25) {
            order.lastX = mob.getX();
            order.lastZ = mob.getZ();
            order.movedAt = now;
        }

        switch (order.state) {
            case HUNT -> {
                if (target == null) {
                    target = nearest(mob, defenders, order);
                    if (target != null) {
                        mob.setTarget(target);
                    }
                }
                if (target == null) {
                    go(mob, order, anchor, now, 1.0);
                }
            }
            case APPROACH, HOLD -> {
                Entry entry = entryFor(order, mob, now);
                if (entry != null && order.kind != Kind.RANGED) {
                    order.entry = entry;
                    order.state = State.ASSAULT;
                    order.navGoal = null;
                    break;
                }
                if (order.kind == Kind.RANGED && target == null) {
                    ServerPlayer seen = nearestSeen(mob, defenders, 28.0);
                    if (seen != null) {
                        mob.setTarget(seen);
                        target = seen;
                    }
                }
                if (target != null) {
                    break;   // a defender came out (or an archer has one in sight): its own fight
                }
                if (order.rally == null) {
                    order.state = State.HUNT;
                    break;
                }
                double d2 = mob.distanceToSqr(Vec3.atBottomCenterOf(order.rally));
                if (order.state == State.APPROACH) {
                    if (d2 <= 9.0) {
                        order.state = State.HOLD;
                        order.holdSince = now;
                        order.nextMill = now + 40 + random.nextInt(60);
                    } else {
                        go(mob, order, order.rally, now, 1.0);
                    }
                } else if (now >= order.nextMill) {
                    // Waiting: a few steps here and there, facing the wall.
                    order.nextMill = now + 60 + random.nextInt(80);
                    BlockPos step = order.rally.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                    mob.getNavigation().moveTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 0.8);
                    order.movedAt = now;
                } else if (d2 > 49.0) {
                    order.state = State.APPROACH;   // pushed away: back to the gathering place
                }
                if (order.state == State.HOLD) {
                    order.movedAt = now;   // waiting is no being stuck
                }
            }
            case ASSAULT -> {
                Entry entry = order.entry;
                if (entry == null || !entry.open || !entries().contains(entry)) {
                    order.entry = null;
                    order.state = State.APPROACH;   // the way closed again: back to the others
                    order.navGoal = null;
                    break;
                }
                if (target != null) {
                    break;
                }
                double d2 = mob.distanceToSqr(Vec3.atBottomCenterOf(entry.front));
                if (d2 > 6.25) {
                    go(mob, order, entry.front, now, 1.15);
                } else {
                    go(mob, order, anchor, now, 1.15);   // through it (a ladder: the climb takes over)
                }
            }
        }

        // Making no headway: another way, and after a long while back from the spawn ground.
        long still = now - order.movedAt;
        if (still > cfg.raidStuckSeconds * 20L && target == null) {
            if (still > cfg.raidStuckSeconds * 60L && nobodyNear(mob, defenders, 24.0)) {
                BlockPos spot = spawnSpot(order.angle);
                if (spot != null) {
                    world.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.8, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                    mob.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                    mob.getNavigation().stop();
                    order.state = base.closed ? State.APPROACH : State.HUNT;
                    order.entry = null;
                    order.navGoal = null;
                    order.lastX = mob.getX();
                    order.lastZ = mob.getZ();
                    order.movedAt = now;
                    returned++;
                    return;
                }
            }
            if (still % (cfg.raidStuckSeconds * 20L) < 10) {
                rerouted++;
                BlockPos other = order.kind == Kind.RANGED ? base.fireNear(order.angle + (random.nextDouble() - 0.5), 6, random)
                        : base.rallyNear(order.angle + (random.nextDouble() - 0.5), 6, random);
                if (other != null) {
                    order.rally = other;
                }
                if (order.state == State.ASSAULT) {
                    order.state = State.APPROACH;   // that way did not work out: wait for another
                    order.entry = null;
                }
                order.navGoal = null;
                mob.getNavigation().stop();
            }
        }
    }

    /** Sends a monster somewhere, without asking the path finder again every time. */
    private void go(Mob mob, Order order, BlockPos goal, long now, double speed) {
        if (goal == null) {
            return;
        }
        if (goal.equals(order.navGoal) && now < order.nextNav && !mob.getNavigation().isDone()) {
            return;
        }
        order.navGoal = goal;
        order.nextNav = now + 40;
        mob.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, speed);
    }

    private LivingEntity nearest(Mob mob, List<ServerPlayer> defenders, Order order) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : defenders) {
            if (p.level() != world || !p.isAlive() || p.isSpectator() || !SiegeManager.mayTarget(mob, p)) {
                continue;
            }
            double d = mob.distanceToSqr(p);
            if (d < bestD && allowed(order, order.kind, mob, p)) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private ServerPlayer nearestSeen(Mob mob, List<ServerPlayer> defenders, double reach) {
        ServerPlayer best = null;
        double bestD = reach * reach;
        for (ServerPlayer p : defenders) {
            if (p.level() != world || !p.isAlive() || p.isSpectator()) {
                continue;
            }
            double d = mob.distanceToSqr(p);
            if (d < bestD && mob.hasLineOfSight(p) && SiegeManager.mayTarget(mob, p)) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static boolean nobodyNear(Mob mob, List<ServerPlayer> defenders, double reach) {
        for (ServerPlayer p : defenders) {
            if (p.level() == mob.level() && p.distanceToSqr(mob) < reach * reach) {
                return false;
            }
        }
        return true;
    }

    /** Counts for the gametest: monsters per state. */
    public Map<State, Integer> states() {
        Map<State, Integer> out = new HashMap<>();
        for (Order o : orders.values()) {
            out.merge(o.state, 1, Integer::sum);
        }
        return out;
    }

    public boolean enlisted(UUID id) {
        return orders.containsKey(id);
    }

    /** For the gametest: what a monster of the plan is up to. */
    public String describe(UUID id) {
        Order o = orders.get(id);
        if (o == null) {
            return "-";
        }
        return o.kind + "/" + o.state + (o.entry != null ? "->" + o.entry.kind + "@" + compass(o.entry.angle) : "")
                + " side=" + compass(o.angle) + (o.rally != null ? " rally=" + o.rally.getX() + "," + o.rally.getZ() : "");
    }

    public State stateOf(UUID id) {
        Order o = orders.get(id);
        return o == null ? null : o.state;
    }

    // ------------------------------------------------------------------ helpers

    private void horn(double angle) {
        if (world == null || anchor == null) {
            return;
        }
        double x = anchor.getX() + 0.5 + Math.cos(angle) * 28;
        double z = anchor.getZ() + 0.5 + Math.sin(angle) * 28;
        world.playSound(null, x, anchor.getY() + 4, z, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0f, 1.0f);
    }

    static double angleBetween(double a, double b) {
        double d = Math.abs(a - b) % (2 * Math.PI);
        return d > Math.PI ? 2 * Math.PI - d : d;
    }

    /** One of eight directions; north is towards negative z. */
    public static String compass(double angle) {
        double deg = Math.toDegrees(angle);   // 0 east, 90 south
        int i = (int) Math.floorMod(Math.round(deg / 45.0), 8L);
        return new String[] {"e", "se", "s", "sw", "w", "nw", "n", "ne"}[i];
    }
}
