package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The base around the core during a night, and what the core tree does with it:
 * monsters spawn outside the walls, masonry slows the diggers, the battlements
 * help the defenders up on the wall, the beacon fire lights up the monsters
 * outside, and the rebuilding mends the base when the night is over.
 */
public final class BaseDefense {

    /** Blocks in front of and behind the wall that still count as "on the wall". */
    private static final double WALL_INSIDE = 4.0;
    private static final double WALL_OUTSIDE = 2.5;
    private static final int MAX_SNAPSHOT = 600_000;
    private static final int MAX_CHECKS = 2_500_000;
    private static final double[] REBUILD_SHARE = {0.0, 0.4, 0.7, 1.0};

    private static BaseDefense activeDefense;

    private ServerLevel world;
    private BlockPos anchor;
    private BaseScan base;
    private double battlements;
    private boolean beacon;
    private int rebuild;
    private final LongArrayList savedPos = new LongArrayList();
    private final List<BlockState> savedState = new ArrayList<>();

    /** Scans the base and reads the core tree at the start of a night. */
    public void begin(ServerLevel world, BlockPos anchor, SiegeConfig cfg, PlayerSkills core, SkillTree tree,
                      Digger digger) {
        begin(world, anchor, cfg, core, tree, digger, false);
    }

    /** As above; {@code keepSnapshot} also remembers the base when only the trowel (a crate gain) needs it. */
    public void begin(ServerLevel world, BlockPos anchor, SiegeConfig cfg, PlayerSkills core, SkillTree tree,
                      Digger digger, boolean keepSnapshot) {
        this.world = world;
        this.anchor = anchor;
        this.base = scanFresh(world, anchor, cfg);
        // The core tree is gone (Simon, 27 October 2026): no masonry, battlements or beacon any
        // more, and everything the night broke comes back after it.
        this.battlements = 0;
        this.beacon = false;
        this.rebuild = REBUILD_SHARE.length - 1;
        digger.setSlowdown(1.0);
        savedPos.clear();
        savedState.clear();
        snapshot();
        NightDamage.begin(world);
        activeDefense = this;
    }

    public void end() {
        if (activeDefense == this) {
            activeDefense = null;
        }
        NightDamage.end();
        world = null;
        anchor = null;
        base = null;
        battlements = 0;
        beacon = false;
        rebuild = 0;
        savedPos.clear();
        savedState.clear();
    }

    public BaseScan base() {
        return base;
    }

    // ------------------------------------------------------------------ spawning

    /** A place outside the walls, six to twenty blocks beyond the edge of the base. */
    public BlockPos spawnSpot(SiegeConfig cfg, Random random) {
        if (base == null || world == null) {
            return null;
        }
        // The ground the scan found in front of the walls: from there a monster walks to the base.
        if (base.spawnSpotCount() > 0) {
            for (int attempt = 0; attempt < 8; attempt++) {
                BlockPos spot = base.spawnNear(random.nextDouble() * Math.PI * 2.0, BaseScan.SLICES / 2, random);
                if (spot != null && world.hasChunk(spot.getX() >> 4, spot.getZ() >> 4)
                        && world.getBlockState(spot).getCollisionShape(world, spot).isEmpty()
                        && world.getBlockState(spot.above()).getCollisionShape(world, spot.above()).isEmpty()
                        && world.getBlockState(spot).getFluidState().isEmpty()) {
                    return spot;
                }
            }
        }
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double edge = base.edgeTowards(anchor.getX() + 0.5 + cos * 8, anchor.getZ() + 0.5 + sin * 8);
            double distance = edge + 6.0 + random.nextDouble() * 14.0;
            int x = anchor.getX() + (int) Math.round(cos * distance);
            int z = anchor.getZ() + (int) Math.round(sin * distance);
            if (!world.hasChunk(x >> 4, z >> 4) || base.contains(x, z)) {
                continue;
            }
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y > anchor.getY() + 40) {
                continue;   // not on a mountain top far above the base
            }
            BlockPos spot = new BlockPos(x, y, z);
            if (!world.getBlockState(spot).isAir() || !world.getBlockState(spot.above()).isAir()) {
                continue;
            }
            if (!world.getBlockState(spot.below()).getFluidState().isEmpty()) {
                continue;
            }
            return spot;
        }
        return null;
    }

    // ------------------------------------------------------------------ during the night

    /** The beacon fire: monsters outside the walls glow. */
    public void tick(Collection<UUID> mobs, long gameTime) {
        if (!beacon || base == null || world == null || gameTime % 40 != 0) {
            return;
        }
        for (UUID id : mobs) {
            Entity entity = world.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive()
                    && !base.contains((int) Math.floor(mob.getX()), (int) Math.floor(mob.getZ()))) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            }
        }
    }

    /** Whether a defender stands up on the wall of the base. */
    public boolean onWall(ServerPlayer player) {
        if (base == null || world == null || player.level() != world) {
            return false;
        }
        if (player.getY() < anchor.getY() + 2) {
            return false;
        }
        double dx = player.getX() - (anchor.getX() + 0.5);
        double dz = player.getZ() - (anchor.getZ() + 0.5);
        double distance = Math.sqrt(dx * dx + dz * dz);
        double edge = base.edgeTowards(player.getX(), player.getZ());
        return distance >= edge - WALL_INSIDE && distance <= edge + WALL_OUTSIDE;
    }

    /** Share of extra arrow damage for a defender on the wall. */
    public double arrowBonus(ServerPlayer player) {
        return battlements > 0 && onWall(player) ? battlements : 0.0;
    }

    /** Factor on the damage a defender on the wall takes. */
    public static float guardFactor(ServerPlayer player) {
        BaseDefense self = activeDefense;
        if (self == null || self.battlements <= 0 || !self.onWall(player)) {
            return 1f;
        }
        return (float) Math.max(0.3, 1.0 - self.battlements);
    }

    // ------------------------------------------------------------------ rebuilding

    /** Remembers every block of the base (except containers and fluids) before the night. */
    private void snapshot() {
        if (base == null) {
            return;
        }
        double margin = 2.5;
        int r = (int) Math.ceil(base.reach() + margin);
        int checks = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = anchor.getX() - r; x <= anchor.getX() + r; x++) {
            for (int z = anchor.getZ() - r; z <= anchor.getZ() + r; z++) {
                if (!base.near(x, z, margin) || !world.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = anchor.getY() - 8; y <= anchor.getY() + 24; y++) {
                    if (++checks > MAX_CHECKS || savedPos.size() >= MAX_SNAPSHOT) {
                        return;
                    }
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || state.hasBlockEntity() || state.getBlock() instanceof LiquidBlock
                            || ModContent.trapOf(state) != null || ModContent.isCore(state)) {
                        continue;   // traps come back with their record (TrapService.recycle), the core never breaks
                    }
                    savedPos.add(pos.asLong());
                    savedState.add(state);
                }
            }
        }
    }

    /**
     * Mends what the night broke: a share of the missing blocks comes back, solid
     * blocks first and from the bottom up. Damage done after this call (the blast
     * of a lost night) stays. Returns the number of blocks put back.
     */
    public int restore(Random random) {
        if (rebuild <= 0 || world == null) {
            return 0;
        }
        List<Integer> missing = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < savedPos.size(); i++) {
            pos.set(BlockPos.of(savedPos.getLong(i)));
            if (!world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            BlockState now = world.getBlockState(pos);
            if (now != savedState.get(i) && (now.isAir() || now.canBeReplaced())) {
                missing.add(i);
            }
        }
        double share = REBUILD_SHARE[Math.min(REBUILD_SHARE.length - 1, rebuild)];
        if (share < 1.0) {
            Collections.shuffle(missing, random);
            missing = new ArrayList<>(missing.subList(0, (int) Math.round(missing.size() * share)));
        }
        missing.sort((a, b) -> {
            boolean fullA = isFull(savedState.get(a), savedPos.getLong(a));
            boolean fullB = isFull(savedState.get(b), savedPos.getLong(b));
            if (fullA != fullB) {
                return fullA ? -1 : 1;
            }
            return Integer.compare(BlockPos.getY(savedPos.getLong(a)), BlockPos.getY(savedPos.getLong(b)));
        });
        int put = 0;
        for (int i : missing) {
            if (world.setBlock(BlockPos.of(savedPos.getLong(i)), savedState.get(i), Block.UPDATE_CLIENTS)) {
                put++;
            }
        }
        // Everything else monsters broke in the night, also outside the base's snapshot.
        put += NightDamage.restore(world);
        if (put > 0 && anchor != null) {
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, anchor.getX() + 0.5, anchor.getY() + 1.5,
                    anchor.getZ() + 0.5, 30, 1.5, 1.0, 1.5, 0.0);
        }
        return put;
    }

    /**
     * The trowel (a guardian's crate gain): every block of the base that the night broke
     * within {@code radius} of a spot comes back at once. Returns the number put back.
     */
    public int restoreNear(BlockPos center, double radius) {
        if (world == null || savedPos.isEmpty() || center == null) {
            return 0;
        }
        double r2 = radius * radius;
        List<Integer> missing = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < savedPos.size(); i++) {
            pos.set(BlockPos.of(savedPos.getLong(i)));
            if (pos.distSqr(center) > r2 || !world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            BlockState now = world.getBlockState(pos);
            if (now != savedState.get(i) && (now.isAir() || now.canBeReplaced())) {
                missing.add(i);
            }
        }
        missing.sort((a, b) -> Integer.compare(BlockPos.getY(savedPos.getLong(a)), BlockPos.getY(savedPos.getLong(b))));
        int put = 0;
        for (int i : missing) {
            BlockPos at = BlockPos.of(savedPos.getLong(i));
            if (world.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(at)).isEmpty()
                    && world.setBlock(at, savedState.get(i), Block.UPDATE_CLIENTS)) {
                put++;
                world.sendParticles(ParticleTypes.WAX_ON, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
            }
        }
        return put;
    }

    private boolean isFull(BlockState state, long packed) {
        return state.isCollisionShapeFullBlock(world, BlockPos.of(packed));
    }

    // ------------------------------------------------------------------ the core window

    private record Cached(long time, BaseScan scan) {
    }

    private static final Map<Long, Cached> CACHE = new HashMap<>();
    private static final List<Highlight> HIGHLIGHTS = new ArrayList<>();

    private record Highlight(UUID player, BlockPos gap, long until) {
    }

    /** A fresh scan; the window reuses it for thirty seconds. */
    private static BaseScan scanFresh(ServerLevel level, BlockPos core, SiegeConfig cfg) {
        BaseScan scan = BaseScan.scan(level, core, cfg.baseRadius);
        CACHE.put(core.asLong(), new Cached(level.getGameTime(), scan));
        return scan;
    }

    /** The base around a core as the window shows it: the scan of the night, or a recent one. */
    public static BaseScan scanFor(ServerLevel level, BlockPos core, SiegeConfig cfg) {
        BaseDefense self = activeDefense;
        if (self != null && self.base != null && core.equals(self.anchor) && level == self.world) {
            return self.base;
        }
        Cached cached = CACHE.get(core.asLong());
        if (cached != null && level.getGameTime() - cached.time() < 600 && cached.time() <= level.getGameTime()) {
            return cached.scan();
        }
        return scanFresh(level, core, cfg);
    }

    /** Forgets the scans, so the next look sees the base as it is now. */
    public static void forgetScans() {
        CACHE.clear();
    }

    /** Lets one player see the gap in the wall for ten seconds. */
    public static void highlight(ServerPlayer player, BlockPos gap) {
        HIGHLIGHTS.removeIf(h -> h.player().equals(player.getUUID()));
        HIGHLIGHTS.add(new Highlight(player.getUUID(), gap, player.level().getGameTime() + 200));
    }

    public static void tickHighlights(MinecraftServer server) {
        if (HIGHLIGHTS.isEmpty()) {
            return;
        }
        Iterator<Highlight> it = HIGHLIGHTS.iterator();
        while (it.hasNext()) {
            Highlight h = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(h.player());
            if (player == null || !(player.level() instanceof ServerLevel level)
                    || level.getGameTime() > h.until()) {
                it.remove();
                continue;
            }
            if (level.getGameTime() % 10 != 0) {
                continue;
            }
            BlockPos g = h.gap();
            level.sendParticles(player, ParticleTypes.FLAME, true, true,
                    g.getX() + 0.5, g.getY() + 1.0, g.getZ() + 0.5, 12, 0.3, 0.8, 0.3, 0.01);
            level.sendParticles(player, ParticleTypes.END_ROD, true, true,
                    g.getX() + 0.5, g.getY() + 2.5, g.getZ() + 0.5, 4, 0.1, 1.0, 0.1, 0.02);
        }
    }
}
