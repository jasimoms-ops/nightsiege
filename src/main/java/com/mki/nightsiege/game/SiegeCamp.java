package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The war camp of the besiegers (concept 27 October 2026, Simon: "übernehmen wir alles
 * genauso"; built that evening: "Werkbank mit Lebensleiste", "Schon am Vortag", "Diener").
 *
 * <p>On the day before a siege a camp stands with a chance of 20 %, 80–150 blocks from the
 * core. Nothing is said in the chat; only the smoke of its fires shows it.</p>
 *
 * <p>Camp rebuilt (Simon 29.10.: "etwa wie heute", the workbench grows with the attackers): alone 6
 * tent guards (vindicators, two before each tent) and 2 crossbowmen (pillagers) on the two
 * watchtowers, for each further attacker 2 tent guards and 1 crossbowman more (at most 4). The
 * inner ring round the workshop opens when the tent guards have fallen; until then the workbench
 * takes no hits. While it stands, reinforcement comes from the tents every 60 s: 1 and 1 per
 * fighter, up to 8 guards alive. The workbench has 300 life and 30 % more per further attacker.</p>
 *
 * <p>Content update (Simon 28/29 October 2026): the fight starts at the war drum in the forecourt.
 * A strike opens the ready round ({@link StartRound}): everyone signed up within 20 blocks chooses
 * a class; when all are ready they stand inside the gate, iron bars shut it, the war horn sounds,
 * the kits come and 10 seconds of grace. Nobody gets out (who does is put back) and nobody else
 * gets in; who logs out counts as fallen. The fallen wait by the fire in the forecourt with the
 * fight's bar until it is over. The own things come back when the fight ends or the player falls.</p>
 *
 * <ul>
 *   <li>The siege workbench destroyed: no siege machines tonight (the rest of the night as ever).</li>
 *   <li>A defender falls at the camp: the camp has won, the machines come for sure, the camp is
 *       gone (no second try).</li>
 *   <li>Left alone: the machines come at night.</li>
 * </ul>
 */
public final class SiegeCamp {

    /** Tag of the camp's guards and of the workbench's hit box. */
    public static final String TAG = "nightsiege_camp";
    /** Tag of the workbench's hit box. */
    public static final String TAG_WORKSHOP = "nightsiege_campwork";

    private enum Outcome { WON, DESTROYED }

    private final SiegeManager m;
    private final Random random = new Random();
    /** Players fighting in the camp now. */
    private final Set<UUID> fighters = new HashSet<>();
    /**
     * Fighters who fell (Simon, 28 October 2026: the camp won as soon as one fell, three times
     * within 20 s in the playtest): they wait outside until the fight is over; the camp only wins
     * when the last one falls.
     */
    private final Set<UUID> out = new HashSet<>();
    /** Fighters who fell in a damage event, handled on the next tick. */
    private final List<UUID> fallen = new ArrayList<>();
    /** Until when a fighter is spared (game time). */
    private final Map<UUID, Long> grace = new HashMap<>();
    private ServerBossEvent bar;
    /** What happened in a damage event, handled on the next tick. */
    private Outcome outcome;
    private UUID outcomeBy;
    /** The camp burns down at this time after its workshop fell (game time), or -1. */
    private long burnUntil = -1L;
    /** The ready round at the war drum, or null. */
    private StartRound round;
    /** Tests and the playtest: whoever steps inside joins (no ready round). */
    private boolean openEntry;
    /** Where each fighter last stood inside (who gets out is put back there). */
    private final Map<UUID, Vec3> lastInside = new HashMap<>();
    /** The iron bars in the gate while a fight is on. */
    private final List<BlockPos> gateBars = new ArrayList<>();
    /** Tag of the crossbowmen on the watchtowers. */
    public static final String TAG_TOWER = "nightsiege_camptower";
    /** When the next reinforcement comes (game time), or -1. */
    private long nextReinforce = -1L;
    /** When each crossbowman may shoot again (game time). */
    private final Map<UUID, Long> nextShot = new HashMap<>();
    /** When a fighter was last told that the inner ring is shut (game time). */
    private final Map<UUID, Long> toldShut = new HashMap<>();

    SiegeCamp(SiegeManager manager) {
        this.m = manager;
    }

    // ------------------------------------------------------------------ queries

    /** True for a guard or the workbench of a camp. */
    public static boolean isCampMob(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG);
    }

    public boolean isFighter(Player player) {
        return player != null && !fighters.isEmpty() && fighters.contains(player.getUUID());
    }

    /** True while someone fights in the camp. */
    public boolean fighting() {
        return !fighters.isEmpty();
    }

    /** The fighters of now (the playtest brings the late ones to them). */
    public List<UUID> fighterIds() {
        return new ArrayList<>(fighters);
    }

    /** True for a fighter who fell and waits outside until the fight is over. */
    public boolean hasFallen(UUID id) {
        return id != null && out.contains(id);
    }

    /** The camp of now, or null. */
    public SiegeSave.CampRecord camp() {
        return m.save().camp;
    }

    /** The middle of the standing camp, or null. */
    public BlockPos center() {
        SiegeSave.CampRecord camp = camp();
        return camp == null || !camp.built ? null : new BlockPos(camp.x, camp.y, camp.z);
    }

    // ------------------------------------------------------------------ the day

    /** Every tick, outside the nights. */
    void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (outcome != null) {
            Outcome o = outcome;
            UUID by = outcomeBy;
            outcome = null;
            outcomeBy = null;
            handleOutcome(server, o, by);
            return;
        }
        if (!fallen.isEmpty()) {
            handleFallen(server);
        }
        restoreGround(server);
        if (now % 20 == 0) {
            roll(server);
        }
        SiegeSave.CampRecord camp = camp();
        if (camp == null || burnUntil > 0) {
            endRound(server, null);   // no camp to fight (any more): the round goes
        }
        if (camp == null) {
            if (!fighters.isEmpty()) {
                endAll(server);
            }
            return;
        }
        ServerLevel level = SiegeManager.levelByName(server, camp.dimension);
        if (level == null) {
            return;
        }
        if (burnUntil > 0) {
            burning(level, camp, now);
            if (now >= burnUntil) {
                burnUntil = -1L;
                remove(server);
            }
            return;
        }
        if (now % 20 == 0 && camp.day < m.dayNow(server)) {
            NightSiegeMod.LOGGER.info("[Night Siege] Heerlager von Tag {} abgebaut (die Nacht ist vorbei)", camp.day);
            remove(server);   // its night went by without a horn (missed, server off)
            return;
        }
        if (!camp.built) {
            if (now % 100 == 0) {
                tryBuild(server, level, camp);
            }
            return;
        }
        BlockPos center = new BlockPos(camp.x, camp.y, camp.z);
        if (!camp.manned) {
            if (now % 20 == 0 && level.isPositionEntityTicking(center)) {
                man(level, camp);
            }
            return;
        }
        if (now % 5 == 0) {
            if (openEntry) {
                checkEntries(level, camp, center, now);
            } else if (!fighters.isEmpty()) {
                keepOut(level, camp, center);
            }
        }
        if (round != null && now % 10 == 0) {
            tickRound(level, camp, center, now);
        }
        if (now % 20 == 0) {
            tickFighters(server, level, camp, center, now);
        }
        if (!fighters.isEmpty() && now % 5 == 0) {
            shootFromTowers(level, camp, now);
        }
    }

    /** On the day before a siege day: one roll whether a camp comes. */
    private void roll(MinecraftServer server) {
        SiegeSave save = m.save();
        SiegeConfig cfg = m.config();
        if (!cfg.machinesEnabled || cfg.campChance <= 0 || m.isActive() || m.testPending() || m.mainCore() == null
                || save.camp != null || m.playtest().running()) {
            return;
        }
        long day = m.dayNow(server);
        if (save.nextSiegeDay != day + 1 || save.campRolledDay == day) {
            return;
        }
        save.campRolledDay = day;
        if (random.nextDouble() < cfg.campChance) {
            plan(day + 1);
            NightSiegeMod.LOGGER.info("[Night Siege] Heerlager für die Belagerung an Tag {} gewürfelt", day + 1);
        }
        m.persist();
    }

    /** A camp for this siege day; it is built as soon as a place for it is loaded. */
    private SiegeSave.CampRecord plan(long siegeDay) {
        CoreRecord core = m.mainCore();
        SiegeSave.CampRecord camp = new SiegeSave.CampRecord();
        camp.id = Math.abs(random.nextInt(1_000_000)) + 1;
        camp.dimension = core == null ? "" : core.dimension;
        camp.day = siegeDay;
        m.save().camp = camp;
        return camp;
    }

    // ------------------------------------------------------------------ building

    private void tryBuild(MinecraftServer server, ServerLevel level, SiegeSave.CampRecord camp) {
        CoreRecord core = m.mainCore();
        if (core == null) {
            return;
        }
        SiegeConfig cfg = m.config();
        Reasons why = new Reasons();
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = cfg.campMinDistance + random.nextDouble() * Math.max(0, cfg.campMaxDistance - cfg.campMinDistance);
            int x = core.x + (int) Math.round(Math.cos(angle) * distance);
            int z = core.z + (int) Math.round(Math.sin(angle) * distance);
            Integer floor = site(level, x, z, true, false, why);
            if (floor == null) {
                continue;
            }
            int facing = CampLayout.facingTowards(core.x - x, core.z - z);
            if (!forecourtFits(level, new BlockPos(x, floor, z), facing)
                    || !untouched(level, new BlockPos(x, floor, z), facing, camp.id, why)) {
                why.built++;
                continue;
            }
            build(level, camp, new BlockPos(x, floor, z), facing);
            NightSiegeMod.LOGGER.info("[Night Siege] Heerlager aufgebaut bei {} {} {} (für Tag {})", x, floor, z, camp.day);
            buildFails = 0;
            m.persist();
            return;
        }
        // Every few minutes a line why it is still waiting (Simon 27.10.: it never came).
        if (buildFails++ % 60 == 0) {
            NightSiegeMod.LOGGER.info("[Night Siege] Heerlager für Tag {} wartet auf einen Platz ({})", camp.day, why);
        }
    }

    /** Build tries without a place since the last camp (for the log line). */
    private int buildFails;

    /**
     * The height one walks on for a camp in the middle of (x, z), or null if it cannot stand
     * there: ground not loaded, water, too steep, built on, players close by.
     */
    Integer site(ServerLevel level, int x, int z, boolean awayFromPlayers) {
        return site(level, x, z, awayFromPlayers, false, null);
    }

    /** Why sites were turned down (for the admin command's log line). */
    static final class Reasons {
        /** The first block that counted as built, for the tests. */
        String detail = "";
        int unloaded;
        int players;
        int water;
        int steep;
        int built;

        @Override
        public String toString() {
            return "nicht geladen " + unloaded + ", Spieler nah " + players + ", Wasser " + water + ", zu steil " + steep
                    + ", Bauten " + built + (detail.isEmpty() ? "" : "; zuletzt " + detail);
        }
    }

    /**
     * The site check (Simon 27.10., 22:49: "the camp never spawns"): flowers, mushrooms,
     * saplings and every other plant count as nature now – before, one poppy anywhere on the
     * 35 × 35 blocks turned a meadow down as "built on". A little water (a puddle, a brook)
     * and hills the levelling can handle (up to 8 down, 12 up from the floor) are fine too.
     * {@code load}: the admin command loads the chunks it needs.
     */
    Integer site(ServerLevel level, int x, int z, boolean awayFromPlayers, boolean load, Reasons why) {
        return site(level, x, z, CampLayout.GROUND, CampLayout.CLEAR, awayFromPlayers, load, why);
    }

    /** The same check for a round ground of any size (the dungeons' gatehouse uses it too). */
    static Integer site(ServerLevel level, int x, int z, int radius, int clear, boolean awayFromPlayers, boolean load,
                        Reasons why) {
        int r = radius + 1;
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    if (!load) {
                        if (why != null) {
                            why.unloaded++;
                        }
                        return null;
                    }
                    level.getChunk(cx, cz);   // the admin command: load (or generate) it
                }
            }
        }
        if (awayFromPlayers) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(x + 0.5, p.getY(), z + 0.5) < 40.0 * 40.0) {
                    if (why != null) {
                        why.players++;
                    }
                    return null;   // nobody sees it pop up
                }
            }
        }
        List<Integer> heights = new ArrayList<>();
        int samples = 0;
        int wet = 0;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                samples++;
                int g = ground(level, x + dx, z + dz);
                if (g == WATER) {
                    wet++;
                    continue;
                }
                if (g == Integer.MIN_VALUE) {
                    if (why != null) {
                        why.built++;
                        why.detail = "Boden bei " + (x + dx) + " " + (z + dz) + ": "
                                + level.getBlockState(new BlockPos(x + dx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz), z + dz));
                    }
                    return null;   // something built
                }
                heights.add(g);
            }
        }
        if (heights.isEmpty() || wet > samples * 0.08) {
            if (why != null) {
                why.water++;
                why.detail = "Wasser " + wet + "/" + samples;
            }
            return null;   // a lake or a river, not a puddle
        }
        Collections.sort(heights);
        int low = heights.get(0);
        int high = heights.get(heights.size() - 1);
        int floor = heights.get(heights.size() / 2) + 1;
        if (floor - low > 9 || high - floor > 12) {
            if (why != null) {
                why.steep++;
                why.detail = "Boden " + low + " bis " + high + ", Mitte " + floor;
            }
            return null;
        }
        // Nothing a player built (update 30.10., Simon: "auf keinen fall in irgendwelchen von spieler
        // platzierten blöcken"): no chunk where players set nature-like blocks down ...
        if (PlayerBuilt.anyIn(level, x - radius - 2, z - radius - 2, x + radius + 2, z + radius + 2)) {
            if (why != null) {
                why.built++;
                why.detail = "Spieler haben dort gebaut";
            }
            return null;
        }
        // ... and nothing but nature in the space it takes: first every third column (quick, most
        // places fail here), then every block of it (a fence or a hut between those was missed).
        for (int step = 3; step >= 1; step -= 2) {
            for (int dx = -radius - 1; dx <= radius + 1; dx += step) {
                for (int dz = -radius - 1; dz <= radius + 1; dz += step) {
                    if (dx * dx + dz * dz > (radius + 1) * (radius + 1)) {
                        continue;
                    }
                    for (int y = floor - 2; y <= floor + clear; y++) {
                        BlockState state = level.getBlockState(new BlockPos(x + dx, y, z + dz));
                        if (!natural(state) && state.getFluidState().isEmpty()) {
                            if (why != null) {
                                why.built++;
                                why.detail = (x + dx) + " " + y + " " + (z + dz) + ": " + state;
                            }
                            return null;
                        }
                    }
                }
            }
        }
        return floor;
    }

    /** {@link #ground} for a column of water. */
    static final int WATER = Integer.MIN_VALUE + 1;

    /**
     * The top ground block of a column (trees and plants skipped), {@link #WATER} for water,
     * or MIN_VALUE for a build.
     */
    static int ground(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int y = top; y > top - 40 && y > level.getMinY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return WATER;
            }
            if (state.isAir() || plant(state) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                    || state.is(Blocks.SNOW) || state.is(Blocks.BAMBOO) || state.is(Blocks.SUGAR_CANE)
                    || state.is(Blocks.CACTUS)) {
                continue;
            }
            if (!natural(state)) {
                return Integer.MIN_VALUE;
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Grass, flowers, mushrooms, saplings, petals, vines and any other plant: no collision box,
     * breaks at once, no block entity – but not torches, rails, redstone, buttons and the like.
     */
    static boolean plant(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        if (state.is(tag("flowers")) || state.is(tag("small_flowers")) || state.is(tag("saplings"))
                || state.is(tag("replaceable_by_trees")) || state.is(tag("sword_efficient"))
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.SWEET_BERRY_BUSH)) {
            return true;
        }
        if (!state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
            return false;
        }
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        for (String made : MADE) {
            if (path.contains(made)) {
                return false;
            }
        }
        return state.getBlock().defaultDestroyTime() <= 0.2f;
    }

    /** A vanilla block tag by its name (a missing one simply matches nothing). */
    private static net.minecraft.tags.TagKey<Block> tag(String name) {
        return TAGS.computeIfAbsent(name, n -> net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.Identifier.withDefaultNamespace(n)));
    }

    private static final Map<String, net.minecraft.tags.TagKey<Block>> TAGS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Natural ground, by block (see {@link #natural}). */
    private static final Set<Block> GROUND_BLOCKS = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT,
            Blocks.PODZOL, Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.MUD, Blocks.MOSS_BLOCK, Blocks.SAND, Blocks.RED_SAND,
            Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE,
            Blocks.GRAVEL, Blocks.CLAY, Blocks.SNOW_BLOCK, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.TERRACOTTA,
            Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.DRIPSTONE_BLOCK, Blocks.SMOOTH_BASALT, Blocks.MOSSY_COBBLESTONE);

    /** Ids of things without a collision box that people put down. */
    private static final String[] MADE = {"torch", "rail", "redstone", "repeater", "comparator", "button", "lever",
            "tripwire", "pressure_plate", "candle", "carpet", "sign", "banner", "head", "skull", "string", "scaffolding",
            "ladder", "frame", "pot"};

    /** Ground, rock, trees and plants: what a camp may be set onto (no player's building). */
    static boolean natural(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (plant(state)) {
            return true;
        }
        // The plain ground blocks by name: in 26.2 the grass block is no longer in the dirt tag
        // (the test of 27.10. turned down a flat grass meadow), so they no longer hang on tags.
        if (GROUND_BLOCKS.contains(state.getBlock()) || state.is(tag("dirt")) || state.is(tag("grass_blocks"))
                || state.is(tag("substrate_overworld")) || state.is(tag("sand")) || state.is(tag("base_stone_overworld"))) {
            return true;
        }
        return state.canBeReplaced() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SNOW)
                || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.ICE) || state.is(tag("overworld_carver_replaceable"))
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.COAL_ORE)
                || state.is(Blocks.IRON_ORE) || state.is(Blocks.COPPER_ORE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.MELON) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.CALCITE) || state.is(Blocks.SMOOTH_BASALT)
                || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.BEE_NEST) || state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.PACKED_MUD) || state.is(Blocks.MUD);
    }

    /** For the tests: the floor height of a camp there and why not ("null (…)"); chunks are loaded on the way. */
    public String siteForTest(ServerLevel level, int x, int z) {
        Reasons why = new Reasons();
        Integer floor = site(level, x, z, false, true, why);
        return floor == null ? "null (" + why + ")" : String.valueOf(floor);
    }

    /** Levels the ground and sets the camp's blocks; the guards come when someone is near. */
    void build(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, int facing) {
        NightDamage.quietly(() -> buildNow(level, camp, center, facing));   // the camp's own building, not the night's
    }

    private void buildNow(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, int facing) {
        camp.x = center.getX();
        camp.y = center.getY();
        camp.z = center.getZ();
        camp.facing = facing;
        camp.dimension = SiegeManager.dimensionName(level);
        int floorY = center.getY();
        // What stands there now is written down first (update 30.10., Simon: "das heer lager muss komplett
        // verschwinden … da bleibt dann immer eine gerade grass fläche über"): it all goes back after.
        ExpeditionSnapshot snap = ExpeditionSnapshot.loadCamp(level.getServer(), camp.id);
        Map<Long, List<BlockPos>> byChunk = new java.util.LinkedHashMap<>();
        for (BlockPos pos : spots(center, facing, camp.id)) {
            byChunk.computeIfAbsent(PlayerBuilt.key(pos.getX() >> 4, pos.getZ() >> 4),
                    k -> new ArrayList<>()).add(pos);
        }
        for (Map.Entry<Long, List<BlockPos>> e : byChunk.entrySet()) {
            snap.recordQuietly(e.getKey(), level, e.getValue());
        }
        snap.flush();
        String key = camp.id + "|" + camp.dimension;
        if (m.save().campGrounds == null) {
            m.save().campGrounds = new ArrayList<>();
        }
        if (!m.save().campGrounds.contains(key)) {
            m.save().campGrounds.add(key);
        }
        Map<BlockPos, BlockState> floor = CampLayout.floor(center, facing, camp.id);
        for (Map.Entry<BlockPos, BlockState> e : floor.entrySet()) {
            BlockPos under = e.getKey();
            int x = under.getX();
            int z = under.getZ();
            // Away with trees and hills above the floor ...
            for (int y = floorY; y <= floorY + CampLayout.CLEAR; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            // ... earth into dips under it ...
            for (int y = floorY - 2; y > floorY - 12; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState there = level.getBlockState(pos);
                if (!there.isAir() && !there.canBeReplaced() && there.getFluidState().isEmpty()) {
                    break;
                }
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            // ... and the camp's floor.
            level.setBlock(under, e.getValue(), Block.UPDATE_CLIENTS);
        }
        for (Map.Entry<BlockPos, BlockState> e : CampLayout.blocks(center, facing, camp.id).entrySet()) {
            level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
        }
        camp.built = true;
        m.stats().campBuilt(camp.day);
        NightSiegeMod.LOGGER.info("[Night Siege] Heerlager steht bei {} {} {} (Tor nach {}, Belagerung an Tag {})",
                center.getX(), center.getY(), center.getZ(), facing, camp.day);
    }

    /** The guards and the workbench's hit box (once the camp's ground is entity-loaded). */
    void man(ServerLevel level, SiegeSave.CampRecord camp) {
        SiegeConfig cfg = m.config();
        int players = 1;
        int tents = cfg.campTentGuards;
        if (m.inPlaytest()) {
            // The playtest goes by its own players, each as many guards as one alone (Simon 28.10.).
            players = Math.max(1, m.playtest().playerCount());
            tents = (int) Math.min(20, Math.round(cfg.campTentGuards * (1.0 + cfg.playtestPlayerScaling * (players - 1))));
        }
        camp.guards.clear();
        camp.tentGuards.clear();
        camp.crossbows.clear();
        camp.ringOpen = false;
        spawnTentGuards(level, camp, tents);
        spawnCrossbows(level, camp, crossbowsFor(players));
        Zombie bench = workbench(level, camp);
        camp.workshop = bench == null ? "" : bench.getUUID().toString();
        if (bench != null) {
            setBenchLife(bench, players);
        }
        camp.manned = true;
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Heerlager bemannt: {} Zeltwachen, {} Armbrustschützen",
                camp.tentGuards.size(), camp.crossbows.size());
    }

    /** Crossbowmen on the towers for so many attackers. */
    private int crossbowsFor(int players) {
        SiegeConfig cfg = m.config();
        return Math.max(0, Math.min(cfg.campCrossbowsMax, cfg.campCrossbows + cfg.campCrossbowsPerPlayer * Math.max(0, players - 1)));
    }

    /** Tent guards for so many attackers. */
    private int tentGuardsFor(int players) {
        SiegeConfig cfg = m.config();
        return cfg.campTentGuards + cfg.campTentGuardsPerPlayer * Math.max(0, players - 1);
    }

    /** The workbench's life for so many attackers (Simon 29.10.: 30 % more per further one). */
    private void setBenchLife(LivingEntity bench, int players) {
        SiegeConfig cfg = m.config();
        double health = cfg.campWorkshopHealth * (1.0 + cfg.campWorkshopHealthPerPlayer * Math.max(0, players - 1));
        BossService.set(bench, Attributes.MAX_HEALTH, health);
        bench.setHealth((float) health);
        if (bar != null) {
            bar.setProgress(1f);
        }
    }

    /** One guard of the camp (tagged, at home in the camp), or null. */
    private Mob spawnGuard(ServerLevel level, SiegeSave.CampRecord camp, BlockPos base, String type, boolean jitter) {
        BlockPos spot = base;
        if (jitter) {
            spot = BossService.groundNear(level, base.getX() + 0.5 + random.nextInt(3) - 1, base.getY() + 1,
                    base.getZ() + 0.5 + random.nextInt(3) - 1);
            if (spot == null) {
                spot = BossService.groundNear(level, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
            }
            if (spot == null) {
                return null;
            }
        }
        Mob guard = MobFactory.spawnAt(level, spot, type, m.currentLevel(), m.config(), random, SiegeEvent.NONE, false);
        if (guard == null) {
            return null;
        }
        guard.removeTag(MobFactory.TAG);   // not a monster of the night: it guards the camp
        guard.addTag(TAG);
        guard.addTag(TAG + "_" + camp.id);
        guard.setHomeTo(new BlockPos(camp.x, camp.y, camp.z), 17);
        guard.setPersistenceRequired();
        camp.guards.add(guard.getUUID().toString());
        return guard;
    }

    /** Guards before the tents, two at each, round and round. */
    private void spawnTentGuards(ServerLevel level, SiegeSave.CampRecord camp, int count) {
        List<BlockPos> spots = CampLayout.tentSpots(new BlockPos(camp.x, camp.y, camp.z), camp.facing);
        int start = camp.tentGuards.size();
        for (int i = 0; i < count; i++) {
            Mob guard = spawnGuard(level, camp, spots.get((start + i) % spots.size()), "minecraft:vindicator", true);
            if (guard != null) {
                camp.tentGuards.add(guard.getUUID().toString());
            }
        }
    }

    /** Crossbowmen on the towers' platforms: they stay up there (no walking) and shoot. */
    private void spawnCrossbows(ServerLevel level, SiegeSave.CampRecord camp, int count) {
        List<BlockPos> spots = CampLayout.towerSpots(new BlockPos(camp.x, camp.y, camp.z), camp.facing);
        for (int i = camp.crossbows.size(); i < Math.min(count, spots.size()); i++) {
            BlockPos spot = spots.get(i);
            Mob bow = spawnGuard(level, camp, spot, "minecraft:pillager", false);
            if (bow == null) {
                continue;
            }
            BossService.set(bow, Attributes.MOVEMENT_SPEED, 0.0);
            BossService.set(bow, Attributes.KNOCKBACK_RESISTANCE, 1.0);
            bow.setHomeTo(spot, 1);
            bow.addTag(TAG_TOWER);
            if (!(bow.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem)) {
                bow.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.CROSSBOW));
            }
            bow.setDropChance(EquipmentSlot.MAINHAND, 0f);
            camp.crossbows.add(bow.getUUID().toString());
        }
    }

    /** Living guards of the camp (all of them, or of one list). */
    private static int alive(ServerLevel level, List<String> ids) {
        int n = 0;
        for (String g : ids) {
            Entity e = level.getEntity(UUID.fromString(g));
            if (e != null && e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    /** The siege workbench's hit box: an invisible, still monster with the workbench's life. */
    private Zombie workbench(ServerLevel level, SiegeSave.CampRecord camp) {
        Zombie box = EntityTypes.HUSK.create(level, EntitySpawnReason.EVENT);   // a husk: no burning by day
        if (box == null) {
            return null;
        }
        BlockPos at = CampLayout.workbench(new BlockPos(camp.x, camp.y, camp.z));
        box.snapTo(at.getX() + 0.5, at.getY() - 0.4, at.getZ() + 0.5, 0f, 0f);
        box.setNoAi(true);
        box.setInvisible(true);
        box.setSilent(true);
        box.setNoGravity(true);
        box.setPersistenceRequired();
        double health = m.config().campWorkshopHealth;
        BossService.set(box, Attributes.MAX_HEALTH, health);
        box.setHealth((float) health);
        BossService.set(box, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        BossService.set(box, Attributes.SCALE, 1.9);
        box.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        // Invisible by an effect: any effect added later (poison, slowness) would otherwise make it seen again.
        box.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            box.setItemSlot(slot, ItemStack.EMPTY);
            box.setDropChance(slot, 0f);
        }
        box.setCustomName(Component.translatable("nightsiege.camp.workbench"));
        box.addTag(TAG);
        box.addTag(TAG + "_" + camp.id);
        box.addTag(TAG_WORKSHOP);
        return level.addFreshEntity(box) ? box : null;
    }

    private LivingEntity bench(ServerLevel level, SiegeSave.CampRecord camp) {
        if (camp.workshop == null || camp.workshop.isEmpty()) {
            return null;
        }
        Entity e = level.getEntity(UUID.fromString(camp.workshop));
        return e instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    /** The forecourt's ground towards the core: natural and not too steep for the levelling. */
    private static boolean forecourtFits(ServerLevel level, BlockPos center, int facing) {
        for (int lx = -CampLayout.FORE_HALF; lx <= CampLayout.FORE_HALF; lx += 2) {
            for (int lz = CampLayout.FORE_NEAR + 2; lz <= CampLayout.FORE_FAR; lz += 2) {
                BlockPos at = CampLayout.world(center, facing, lx, 0, lz);
                if (!level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
                    return false;
                }
                int g = ground(level, at.getX(), at.getZ());
                if (g == WATER || g == Integer.MIN_VALUE || center.getY() - g > 9 || g - center.getY() > 12) {
                    return false;
                }
                for (int y = center.getY(); y <= center.getY() + 4; y++) {
                    if (!natural(level.getBlockState(new BlockPos(at.getX(), y, at.getZ())))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the ready round (the war drum)

    /** Tests and the playtest: whoever steps inside joins, without the ready round. */
    public void openEntry(boolean value) {
        openEntry = value;
    }

    /** True for a member of the ready round (for the class window). */
    public boolean inRound(ServerPlayer player) {
        return round != null && player != null && round.has(player.getUUID());
    }

    public boolean roundStarter(ServerPlayer player) {
        return round != null && player != null && round.isStarter(player.getUUID());
    }

    /** Seconds until the starter may go without the others (0 when he may now). */
    public int roundForceIn(long now) {
        return round == null ? 0 : round.forceIn(now);
    }

    public List<UUID> roundMembers() {
        return round == null ? List.of() : round.members();
    }

    public boolean roundReady(UUID id) {
        return round != null && round.isReady(id);
    }

    /** A right click on the war drum: the ready round begins, or ends again (its starter). */
    public boolean onDrum(ServerPlayer player, BlockPos pos) {
        SiegeSave.CampRecord camp = camp();
        if (camp == null || !camp.built || camp.destroyed || burnUntil > 0 || !(player.level() instanceof ServerLevel level)
                || !SiegeManager.dimensionName(level).equals(camp.dimension)) {
            return false;
        }
        BlockPos center = new BlockPos(camp.x, camp.y, camp.z);
        BlockPos drum = CampLayout.drum(center, camp.facing);
        if (!pos.equals(drum)) {
            return false;
        }
        beat(level, drum, 0.9f);
        UUID id = player.getUUID();
        if (!m.isRegistered(player)) {
            player.sendSystemMessage(Component.translatable("nightsiege.camp.round_register").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (!fighters.isEmpty()) {
            player.sendSystemMessage(Component.translatable(out.contains(id) ? "nightsiege.camp.round_fallen"
                    : "nightsiege.camp.round_running").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (m.isActive() || m.testPending() || m.nightComing(level.getServer())) {
            player.sendSystemMessage(Component.translatable("nightsiege.camp.round_night").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (!camp.manned) {
            return true;   // the guards are not there yet (they come when the camp's ground ticks)
        }
        if (round != null) {
            if (round.isStarter(id)) {
                endRound(level.getServer(), "nightsiege.camp.round_cancelled");
            } else if (round.has(id)) {
                m.campClassPick(player, 0);
            } else {
                player.sendSystemMessage(Component.translatable("nightsiege.camp.round_other").withStyle(ChatFormatting.GRAY));
            }
            return true;
        }
        if (!m.save().skillsOf(id.toString()).hasClass()) {
            player.sendSystemMessage(Component.translatable("nightsiege.camp.round_noclass").withStyle(ChatFormatting.GRAY));
            return true;
        }
        StartRound r = new StartRound(id, drum, level.getGameTime());
        for (ServerPlayer other : level.players()) {
            if (other != player && m.isRegistered(other) && !other.isSpectator() && !Drones.isDrone(other)
                    && !other.getAbilities().instabuild && m.save().skillsOf(other.getUUID().toString()).hasClass()
                    && r.near(other, level)) {
                r.add(other.getUUID());
            }
        }
        round = r;
        Component text = Component.translatable("nightsiege.camp.round_open", player.getDisplayName()).withStyle(ChatFormatting.GOLD);
        for (UUID member : r.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(member);
            if (p != null) {
                p.sendSystemMessage(text);
                m.campClassPick(p, 0);
            }
        }
        beat(level, drum, 0.7f);
        NightSiegeMod.LOGGER.info("[Night Siege] Bereit-Runde an der Kriegstrommel von {} mit {} Spielern",
                player.getScoreboardName(), r.size());
        return true;
    }

    /** A class chosen in the window: ready in the round. */
    public void onRoundReady(ServerPlayer player) {
        if (round == null || player == null || !round.markReady(player.getUUID()) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        beat(level, round.at, 1.0f + 0.1f * round.readyCount());
        Component text = Component.translatable("nightsiege.camp.round_ready", player.getDisplayName(),
                round.readyCount(), round.size()).withStyle(ChatFormatting.YELLOW);
        for (UUID id : round.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(text));
            }
        }
    }

    /** The starter's button: go without those not ready (after 30 s). */
    public void forceRound(ServerPlayer player) {
        if (round == null || player == null || !round.isStarter(player.getUUID()) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<UUID> dropped = round.force(level.getGameTime());
        for (UUID id : dropped) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(Component.translatable("nightsiege.camp.round_dropped").withStyle(ChatFormatting.GRAY));
                m.closeClassPick(p);
            }
        }
        if (!dropped.isEmpty()) {
            m.refreshClassPicksNow(level.getServer());
        }
    }

    private void endRound(MinecraftServer server, String messageKey) {
        if (round == null) {
            return;
        }
        List<UUID> members = round.members();
        round = null;
        for (UUID id : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                if (messageKey != null) {
                    p.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GRAY));
                }
                m.closeClassPick(p);
            }
        }
    }

    /** The round: who walks off is out (the starter ends it); when all are ready, the fight begins. */
    private void tickRound(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, long now) {
        if (m.isActive() || m.testPending() || m.nightComing(level.getServer())) {
            endRound(level.getServer(), "nightsiege.camp.round_night");   // the night comes first
            return;
        }
        List<UUID> gone = round.dropGone(level);
        for (UUID id : gone) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(Component.translatable("nightsiege.camp.round_left").withStyle(ChatFormatting.GRAY));
                m.closeClassPick(p);
            }
            if (round.isStarter(id)) {
                endRound(level.getServer(), "nightsiege.camp.round_cancelled");
                return;
            }
        }
        if (!gone.isEmpty()) {
            m.refreshClassPicksNow(level.getServer());
        }
        if (round.size() == 0) {
            round = null;
        } else if (round.allReady()) {
            startFight(level, camp, center, now);
        }
    }

    /** All ready: they stand inside the gate, the bars shut it, the horn, kits and grace. */
    private void startFight(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, long now) {
        List<UUID> members = round.members();
        round = null;
        // As many guards and as much life as this group needs (Simon 29.10.: "etwa wie heute").
        int players = Math.max(1, members.size());
        int more = tentGuardsFor(players) - camp.tentGuards.size();
        if (more > 0) {
            spawnTentGuards(level, camp, more);
        }
        spawnCrossbows(level, camp, crossbowsFor(players));
        LivingEntity bench = bench(level, camp);
        if (bench != null) {
            setBenchLife(bench, players);
        }
        m.persist();
        BlockPos in = CampLayout.world(center, camp.facing, 0, 0, -1);
        float yaw = (float) Math.toDegrees(Math.atan2(-(in.getX() - center.getX()), in.getZ() - center.getZ()));
        int i = 0;
        for (UUID id : members) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                continue;
            }
            BlockPos spot = CampLayout.insideGate(center, camp.facing, i++);
            m.closeClassPick(p);
            p.closeContainer();
            p.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, java.util.Set.of(), yaw, 0f, true);
            join(level, camp, center, p, now, false);
        }
        shutGate(level, camp, center);
        NightSiegeMod.LOGGER.info("[Night Siege] Heerlager-Kampf beginnt mit {} Spielern, das Tor ist zu ({} Zeltwachen, {} Armbrustschützen, Werkbank {})",
                i, alive(level, camp.tentGuards), alive(level, camp.crossbows), bench == null ? "-" : Math.round(bench.getMaxHealth()));
    }

    /** Iron bars into the gate (temporary blocks: they never stay). */
    private void shutGate(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center) {
        List<BlockPos> bars = new ArrayList<>(CampLayout.gate(center, camp.facing));
        bars.addAll(CampLayout.forecourtGate(center, camp.facing));   // the second portcullis: the forecourt's way in
        for (BlockPos pos : bars) {
            if (TempBlocks.place(level, pos, Blocks.IRON_BARS.defaultBlockState())) {
                gateBars.add(pos);
            }
        }
        BlockPos mid = CampLayout.world(center, camp.facing, 0, 1, CampLayout.GATE_Z);
        level.playSound(null, mid, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
        level.playSound(null, mid, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.5f, 0.6f);
    }

    private void openGate(ServerLevel level) {
        if (gateBars.isEmpty()) {
            return;
        }
        for (BlockPos pos : gateBars) {
            if (TempBlocks.isTemp(level, pos)) {
                TempBlocks.remove(level, pos);
            }
        }
        level.playSound(null, gateBars.get(0), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2f, 0.6f);
        gateBars.clear();
    }

    /**
     * While a fight is on, nobody else is in the camp or its forecourt (both are shut): the late
     * are put before the forecourt. The fallen wait in the forecourt.
     */
    private void keepOut(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center) {
        BlockPos before = CampLayout.beforeForecourt(center, camp.facing);
        for (ServerPlayer p : level.players()) {
            UUID id = p.getUUID();
            if (fighters.contains(id) || p.isSpectator() || p.getAbilities().instabuild || Drones.isDrone(p)) {
                continue;
            }
            if (Math.abs(p.getY() - center.getY()) > 10) {
                continue;
            }
            double dx = p.getX() - (center.getX() + 0.5);
            double dz = p.getZ() - (center.getZ() + 0.5);
            boolean inCamp = dx * dx + dz * dz <= CampLayout.INSIDE * CampLayout.INSIDE;
            int[] l = CampLayout.local(center, camp.facing, p.blockPosition());
            boolean inForecourt = CampLayout.insideForecourt(l[0], l[1]);
            if (inCamp || inForecourt && !out.contains(id)) {
                p.teleportTo(level, before.getX() + 0.5, before.getY(), before.getZ() + 0.5, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
                p.sendSystemMessage(Component.translatable("nightsiege.camp.round_late").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // ------------------------------------------------------------------ the inner ring, reinforcement, the towers

    /** The tent guards have fallen: the inner ring's gate opens (the workbench may be struck now). */
    private void checkRing(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center) {
        if (camp.ringOpen || alive(level, camp.tentGuards) > 0) {
            return;
        }
        camp.ringOpen = true;
        NightDamage.quietly(() -> {
            for (BlockPos pos : CampLayout.innerGate(center, camp.facing)) {
                if (level.getBlockState(pos).is(Blocks.IRON_BARS)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        });
        BlockPos gate = CampLayout.world(center, camp.facing, 0, 1, CampLayout.INNER_GATE_Z);
        level.playSound(null, gate, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2f, 0.6f);
        level.playSound(null, gate, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 2f, 0.5f);
        level.sendParticles(ParticleTypes.CLOUD, gate.getX() + 0.5, gate.getY() + 0.5, gate.getZ() + 0.5, 20, 1.2, 0.8, 0.3, 0.02);
        for (UUID id : fighters) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("nightsiege.camp.ring_open")
                        .withStyle(ChatFormatting.GOLD)));
                p.sendSystemMessage(Component.translatable("nightsiege.camp.ring_open_chat").withStyle(ChatFormatting.GOLD));
            }
        }
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Heerlager: Zeltwachen gefallen, der innere Ring ist offen");
    }

    /** Every 60 s while the workbench stands: guards come out of the tents (up to the cap alive). */
    private void reinforce(ServerLevel level, SiegeSave.CampRecord camp, long now) {
        SiegeConfig cfg = m.config();
        int room = cfg.campReinforceCap - alive(level, camp.guards);
        int count = Math.min(room, cfg.campReinforceBase + cfg.campReinforcePerPlayer * fighters.size());
        if (count <= 0) {
            return;
        }
        List<BlockPos> spots = CampLayout.tentSpots(new BlockPos(camp.x, camp.y, camp.z), camp.facing);
        int came = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spot = spots.get(random.nextInt(spots.size()));
            Mob guard = spawnGuard(level, camp, spot, "minecraft:vindicator", true);
            if (guard == null) {
                continue;
            }
            came++;
            ServerPlayer target = nearestFighter(level, guard, 48);
            if (target != null) {
                guard.setTarget(target);
            }
            if (i == 0) {
                level.playSound(null, guard.blockPosition(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 2f, 0.8f);
            }
        }
        if (came == 0) {
            return;
        }
        Component text = Component.translatable("nightsiege.camp.reinforce", came).withStyle(ChatFormatting.RED);
        for (UUID id : fighters) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(text));
            }
        }
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Heerlager: Verstärkung {} (lebend jetzt {})", came, alive(level, camp.guards));
    }

    /** The nearest fighter this guard may fight (past the grace), within so many blocks, or null. */
    private ServerPlayer nearestFighter(ServerLevel level, Mob mob, double range) {
        ServerPlayer best = null;
        double bestDist = range * range;
        for (UUID id : fighters) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null || p.level() != level || !p.isAlive() || !mayTarget(mob, p)) {
                continue;
            }
            double d = p.distanceToSqr(mob);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * The crossbowmen on the towers shoot at the nearest fighter they see, far beyond a
     * pillager's own reach (they cannot walk up to anyone up there).
     */
    private void shootFromTowers(ServerLevel level, SiegeSave.CampRecord camp, long now) {
        SiegeConfig cfg = m.config();
        for (String g : camp.crossbows) {
            Entity e = level.getEntity(UUID.fromString(g));
            if (!(e instanceof Mob bow) || !bow.isAlive()) {
                continue;
            }
            Long next = nextShot.get(bow.getUUID());
            if (next != null && now < next) {
                continue;
            }
            ServerPlayer target = nearestFighter(level, bow, cfg.campCrossbowRange);
            if (target == null) {
                continue;
            }
            // The bolt leaves over the parapet (a step forward and up), and only when its way is free.
            Vec3 eye = bow.getEyePosition();
            Vec3 aim = target.getEyePosition().subtract(0, 0.45, 0).add(target.getDeltaMovement().scale(4));
            Vec3 flat = new Vec3(aim.x - eye.x, 0, aim.z - eye.z);
            if (flat.lengthSqr() < 1.0) {
                continue;
            }
            Vec3 from = eye.add(flat.normalize().scale(1.8)).add(0, 0.3, 0);
            net.minecraft.world.phys.BlockHitResult way = level.clip(new net.minecraft.world.level.ClipContext(from,
                    target.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, bow));
            if (way.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                continue;
            }
            nextShot.put(bow.getUUID(), now + Math.round(cfg.campCrossbowSeconds * 20) + random.nextInt(8));
            bow.setTarget(target);
            bow.getLookControl().setLookAt(target, 60f, 60f);
            Vec3 dir = aim.subtract(from);
            float speed = 2.6f;
            double flight = dir.length() / speed;
            dir = dir.add(0, 0.025 * flight * flight, 0);   // the arrow falls on its way
            net.minecraft.world.entity.projectile.arrow.Arrow bolt = new net.minecraft.world.entity.projectile.arrow.Arrow(
                    level, from.x, from.y, from.z, new ItemStack(net.minecraft.world.item.Items.ARROW), new ItemStack(net.minecraft.world.item.Items.CROSSBOW));
            bolt.setOwner(bow);
            bolt.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
            double damage = 4.0 * com.mki.nightsiege.core.SiegeMath.damageMultiplier(m.currentLevel(), cfg);
            bolt.setBaseDamage(damage / speed);
            bolt.shoot(dir.x, dir.y, dir.z, speed, 1.0f);
            level.addFreshEntity(bolt);
            level.playSound(null, bow.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.5f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    /** The drum's beat. */
    private static void beat(ServerLevel level, BlockPos drum, float pitch) {
        level.playSound(null, drum, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 3f, pitch);
        level.sendParticles(ParticleTypes.NOTE, drum.getX() + 0.5, drum.getY() + 1.2, drum.getZ() + 0.5, 1, 0, 0, 0, 0.5);
    }

    // ------------------------------------------------------------------ the fight

    private void checkEntries(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, long now) {
        if (m.isActive() || m.testPending()) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (fighters.contains(p.getUUID()) || out.contains(p.getUUID()) || !m.isRegistered(p) || p.isSpectator() || p.getAbilities().instabuild
                    || !p.isAlive() || Drones.isDrone(p)) {
                continue;
            }
            double dx = p.getX() - (center.getX() + 0.5);
            double dz = p.getZ() - (center.getZ() + 0.5);
            if (dx * dx + dz * dz > CampLayout.INSIDE * CampLayout.INSIDE || Math.abs(p.getY() - center.getY()) > 8) {
                continue;
            }
            join(level, camp, center, p, now);
        }
    }

    /** A signed-up player stepped inside the palisade: horn, class cards, kit, grace. */
    void join(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, ServerPlayer player, long now) {
        join(level, camp, center, player, now, true);
    }

    /** As above; {@code pick}: the class window (not after the ready round, the class is chosen). */
    void join(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, ServerPlayer player, long now, boolean pick) {
        boolean first = fighters.isEmpty();
        lastInside.put(player.getUUID(), player.position());
        UUID id = player.getUUID();
        fighters.add(id);
        m.stats().campJoin(player, m.currentLevel(), now);
        int seconds = m.config().campGraceSeconds;
        grace.put(id, now + seconds * 20L);
        if (first) {
            nextReinforce = now + (seconds + m.config().campReinforceSeconds) * 20L;
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(center.getX(), center.getY(), center.getZ()) < 160.0 * 160.0) {
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                            SoundEvents.RAID_HORN, SoundSource.HOSTILE, center.getX(), center.getY(), center.getZ(),
                            64f, 1f, random.nextLong()));
                }
            }
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("nightsiege.camp.title")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("nightsiege.camp.subtitle", seconds)
                .withStyle(ChatFormatting.GOLD)));
        player.sendSystemMessage(Component.translatable("nightsiege.camp.enter", seconds).withStyle(ChatFormatting.GOLD));
        KitService kits = m.kits();
        if (m.config().nightKits && !kits.hasStash(player)) {
            kits.equip(player, m.kitLevel());
            if (kits.hasStash(player)) {
                List<String> list = m.save().campKits;
                if (list == null) {
                    m.save().campKits = list = new ArrayList<>();
                }
                if (!list.contains(id.toString())) {
                    list.add(id.toString());
                }
                m.persist();
            }
        }
        if (pick) {
            m.campClassPick(player, seconds);
        }
        SkillService.apply(player, m.save().skillsOf(id.toString()), m.skillTree());
        showBar(level, camp, player);
        NightSiegeMod.LOGGER.info("[Night Siege] {} greift das Heerlager an", player.getScoreboardName());
    }

    private void tickFighters(MinecraftServer server, ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, long now) {
        for (UUID id : new ArrayList<>(fighters)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                // Logged out: counts as fallen (content update); the own things come back at the next login.
                if (!fallen.contains(id)) {
                    fallen.add(id);
                }
                continue;
            }
            double dx = p.getX() - (center.getX() + 0.5);
            double dz = p.getZ() - (center.getZ() + 0.5);
            double limit = CampLayout.PALISADE + 0.5;
            boolean inside = p.level() == level && dx * dx + dz * dz <= limit * limit && Math.abs(p.getY() - center.getY()) <= 14;
            if (inside) {
                lastInside.put(id, p.position());
            } else if (openEntry) {
                leave(p, "nightsiege.camp.left");
            } else {
                // Out over the palisade (a pearl, the hook, a climbing arrow): back where he stood inside.
                Vec3 back = lastInside.getOrDefault(id, Vec3.atBottomCenterOf(CampLayout.insideGate(center, camp.facing, 2)));
                p.teleportTo(level, back.x, back.y, back.z, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
                p.sendSystemMessage(Component.translatable("nightsiege.camp.put_back").withStyle(ChatFormatting.GRAY));
            }
        }
        if (!fighters.isEmpty() && outcome == null) {
            checkRing(level, camp, center);
            if (nextReinforce > 0 && now >= nextReinforce) {
                nextReinforce = now + m.config().campReinforceSeconds * 20L;
                reinforce(level, camp, now);
            }
        }
        LivingEntity bench = bench(level, camp);
        if (bar != null) {
            if (bench != null) {
                bar.setProgress((float) Math.max(0.0, Math.min(1.0, bench.getHealth() / bench.getMaxHealth())));
            }
            if (fighters.isEmpty()) {
                bar.removeAllPlayers();
                bar = null;
            }
        }
        if (fighters.isEmpty()) {
            calmGuards(level, camp);
            m.stats().campAttemptEnd("verlassen");   // everyone left the palisade (nothing if no attempt is open)
            out.clear();   // nobody fights any more: the fallen may try again
            openGate(level);
        }
    }

    private void showBar(ServerLevel level, SiegeSave.CampRecord camp, ServerPlayer player) {
        if (bar == null) {
            bar = new ServerBossEvent(com.mki.nightsiege.core.NightBarIds.make(com.mki.nightsiege.core.NightBarIds.Kind.CAMP), Component.translatable("nightsiege.camp.workbench"),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            LivingEntity bench = bench(level, camp);
            bar.setProgress(bench == null ? 1f : (float) Math.max(0.0, Math.min(1.0, bench.getHealth() / bench.getMaxHealth())));
        }
        bar.addPlayer(player);
    }

    /** A fighter is out of the fight: own things back, skills off. */
    void leave(ServerPlayer player, String messageKey) {
        UUID id = player.getUUID();
        fighters.remove(id);
        grace.remove(id);
        if (bar != null) {
            bar.removePlayer(player);
        }
        giveBack(player);
        if (messageKey != null) {
            player.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GRAY));
        }
        ServerLevel level = player.level() instanceof ServerLevel l ? l : null;
        SiegeSave.CampRecord camp = camp();
        if (level != null && camp != null) {
            for (String g : camp.guards) {
                Entity e = level.getEntity(UUID.fromString(g));
                if (e instanceof Mob mob && mob.getTarget() == player) {
                    mob.setTarget(null);
                }
            }
        }
    }

    /** The kit goes, the own inventory comes back (only for a kit from the camp), skills off. */
    private void giveBack(ServerPlayer player) {
        String id = player.getUUID().toString();
        List<String> list = m.save().campKits;
        if (list != null && list.remove(id) && m.kits().hasStash(player)) {
            m.kits().restore(player);   // persists
        }
        SkillService.clear(player);
    }

    /** Everyone out of the fight (the camp goes or the night comes). */
    void endAll(MinecraftServer server) {
        if (!fighters.isEmpty()) {
            m.stats().campAttemptEnd("abgebrochen");
        }
        for (UUID id : new ArrayList<>(fighters)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                leave(p, null);
            }
        }
        fighters.clear();
        grace.clear();
        out.clear();
        fallen.clear();
        lastInside.clear();
        nextShot.clear();
        toldShut.clear();
        nextReinforce = -1L;
        round = null;
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        SiegeSave.CampRecord camp = camp();
        ServerLevel level = camp == null ? null : SiegeManager.levelByName(server, camp.dimension);
        if (level != null) {
            openGate(level);
        }
    }

    private void calmGuards(ServerLevel level, SiegeSave.CampRecord camp) {
        for (String g : camp.guards) {
            Entity e = level.getEntity(UUID.fromString(g));
            if (e instanceof Mob mob && mob.getTarget() != null) {
                mob.setTarget(null);
            }
        }
    }

    /** A logged-in player: a camp kit from before goes back if it is still on (handled by the night's join too). */
    void onJoin(ServerPlayer player) {
        List<String> list = m.save().campKits;
        if (list != null && list.contains(player.getUUID().toString()) && !fighters.contains(player.getUUID())) {
            giveBack(player);
        }
    }

    // ------------------------------------------------------------------ damage and death

    /** Whether a camp monster may pick this target: only fighters past their grace. */
    public boolean mayTarget(Mob mob, LivingEntity target) {
        if (!(target instanceof ServerPlayer player) || !fighters.contains(player.getUUID())) {
            return false;
        }
        Long until = grace.get(player.getUUID());
        return until == null || mob.level().getGameTime() >= until;
    }

    /** Whether a hit lands: the workbench only takes the fighters' blows, the grace holds. */
    public boolean allowDamage(LivingEntity victim, DamageSource source) {
        Entity attacker = source.getEntity();
        if (victim.entityTags().contains(TAG_WORKSHOP)) {
            if (fighters.isEmpty() || attacker == null || isCampMob(attacker)) {
                return false;   // no falling, no suffocating in its own blocks, nothing from outside the fight
            }
            if (attacker instanceof Player p && !fighters.contains(p.getUUID())) {
                return false;
            }
            SiegeSave.CampRecord camp = camp();
            if (camp != null && !camp.ringOpen) {
                // The inner ring is shut until the tent guards have fallen (also against arrows over it).
                if (attacker instanceof ServerPlayer p && victim.level() instanceof ServerLevel level) {
                    long now = level.getGameTime();
                    Long told = toldShut.get(p.getUUID());
                    if (told == null || now - told > 60) {
                        toldShut.put(p.getUUID(), now);
                        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                                Component.translatable("nightsiege.camp.ring_shut", alive(level, camp.tentGuards))
                                        .withStyle(ChatFormatting.RED)));
                    }
                }
                return false;
            }
            return true;
        }
        if (victim instanceof ServerPlayer player && isCampMob(attacker)) {
            Long until = grace.get(player.getUUID());
            return fighters.contains(player.getUUID()) && (until == null || victim.level().getGameTime() >= until);
        }
        return true;
    }

    /**
     * Before a death: a fighter does not die (the camp has won), the workbench does not die
     * either (it falls with the camp). False when the death is called off.
     */
    public boolean allowDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayer player && fighters.contains(player.getUUID())) {
            player.setHealth(player.getMaxHealth());
            player.removeAllEffects();
            player.clearFire();
            grace.put(player.getUUID(), Long.MAX_VALUE);   // spared until it is handled
            if (!fallen.contains(player.getUUID())) {
                fallen.add(player.getUUID());
            }
            return false;
        }
        if (entity.entityTags().contains(TAG_WORKSHOP)) {
            entity.setHealth(1f);
            entity.invulnerableTime = 40;
            if (outcome == null && burnUntil < 0) {
                outcome = Outcome.DESTROYED;
                outcomeBy = source != null && source.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
            }
            return false;
        }
        return true;
    }

    /**
     * The fallen of the last tick: each one waits outside the gate (own things back) until the fight
     * is over; when nobody is left inside, the camp has won.
     */
    private void handleFallen(MinecraftServer server) {
        SiegeSave.CampRecord camp = camp();
        List<UUID> list = new ArrayList<>(fallen);
        fallen.clear();
        if (camp == null || outcome != null) {
            return;
        }
        for (UUID id : list) {
            if (!fighters.contains(id)) {
                continue;
            }
            if (fighters.size() <= 1) {
                outcome = Outcome.WON;   // the last one fell
                outcomeBy = id;
                return;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            out.add(id);
            m.stats().campFell();
            if (player == null) {
                fighters.remove(id);
                grace.remove(id);
                continue;
            }
            leave(player, null);
            player.setHealth(player.getMaxHealth());
            player.clearFire();
            if (bar != null) {
                bar.addPlayer(player);   // the fight's bar stays with the waiting
            }
            ServerLevel level = SiegeManager.levelByName(server, camp.dimension);
            if (level != null) {
                BlockPos seat = CampLayout.waiting(new BlockPos(camp.x, camp.y, camp.z), camp.facing);
                player.teleportTo(level, seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, java.util.Set.of(),
                        player.getYRot(), player.getXRot(), true);
            }
            player.sendSystemMessage(Component.translatable("nightsiege.camp.fallen").withStyle(ChatFormatting.RED));
            for (UUID f : fighters) {
                ServerPlayer mate = server.getPlayerList().getPlayer(f);
                if (mate != null) {
                    mate.sendSystemMessage(Component.translatable("nightsiege.camp.mate_fallen", player.getDisplayName(),
                            fighters.size()).withStyle(ChatFormatting.GOLD));
                }
            }
            NightSiegeMod.LOGGER.info("[Night Siege] {} ist im Heerlager gefallen, {} kämpfen weiter",
                    player.getScoreboardName(), fighters.size());
        }
    }

    private void handleOutcome(MinecraftServer server, Outcome o, UUID by) {
        SiegeSave.CampRecord camp = camp();
        if (camp == null) {
            return;
        }
        ServerPlayer player = by == null ? null : server.getPlayerList().getPlayer(by);
        ServerLevel level = SiegeManager.levelByName(server, camp.dimension);
        if (o == Outcome.WON) {
            m.stats().campFell();
            m.stats().campWon(camp.day);
            m.save().machinesDay = camp.day;
            NightSiegeMod.LOGGER.info("[Night Siege] Das Heerlager hat gesiegt: Belagerungsmaschinen in der Nacht von Tag {}", camp.day);
            m.broadcastRegistered(server, Component.translatable("nightsiege.camp.won",
                    player == null ? Component.literal("?") : player.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
            endAll(server);
            if (player != null) {
                m.sendToCore(player);
            }
            remove(server);
            return;
        }
        camp.destroyed = true;
        m.stats().campDestroyed(camp.day);
        NightSiegeMod.LOGGER.info("[Night Siege] Belagerungswerkstatt zerstört: keine Maschinen in der Nacht von Tag {}", camp.day);
        m.broadcastRegistered(server, Component.translatable("nightsiege.camp.destroyed",
                player == null ? Component.literal("?") : player.getDisplayName()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        if (level != null) {
            BlockPos at = CampLayout.workbench(new BlockPos(camp.x, camp.y, camp.z));
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0, 0, 0, 0);
            level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2f, 0.7f);
            level.playSound(null, at, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 8f, 0.6f);
        }
        endAll(server);
        m.persist();
        burnUntil = server.overworld().getGameTime() + 100;   // it burns down, then it is gone
    }

    private void burning(ServerLevel level, SiegeSave.CampRecord camp, long now) {
        if (now % 4 != 0) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * CampLayout.PALISADE;
            double x = camp.x + 0.5 + Math.cos(a) * r;
            double z = camp.z + 0.5 + Math.sin(a) * r;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, camp.y + 1.5, z, 3, 0.4, 0.6, 0.4, 0.02);
            level.sendParticles(ParticleTypes.FLAME, x, camp.y + 0.8, z, 4, 0.4, 0.4, 0.4, 0.01);
        }
    }

    // ------------------------------------------------------------------ going away

    /** The horn sounds: an untouched camp sends its machines tonight; the camp goes. */
    void onHorn(MinecraftServer server) {
        SiegeSave.CampRecord camp = camp();
        if (camp == null) {
            return;
        }
        if (camp.built && !camp.destroyed) {
            m.save().machinesDay = m.dayNow(server);
            NightSiegeMod.LOGGER.info("[Night Siege] Das Heerlager steht noch: Belagerungsmaschinen heute Nacht");
        }
        remove(server);
    }

    /** The night is called off: the camp goes without consequences. */
    void onCancel(MinecraftServer server) {
        if (camp() != null) {
            remove(server);
        }
        m.save().machinesDay = -1L;
    }

    /** Takes the camp away: guards, the workbench, its blocks; the floor turns to grass. */
    void remove(MinecraftServer server) {
        NightDamage.quietly(() -> removeNow(server));   // a night does not bring the camp back
    }

    /** True for the spots of the standing camp (it builds and burns on its own; the night's mending leaves it). */
    public boolean covers(Level level, BlockPos pos) {
        SiegeSave.CampRecord camp = camp();
        if (camp == null || !camp.built || level == null || !SiegeManager.dimensionName(level).equals(camp.dimension)) {
            return false;
        }
        int reach = Math.max(CampLayout.GROUND + 6, CampLayout.FORE_FAR + 2);   // the forecourt too
        return Math.abs(pos.getX() - camp.x) <= reach && Math.abs(pos.getZ() - camp.z) <= reach
                && pos.getY() >= camp.y - 12 && pos.getY() <= camp.y + CampLayout.CLEAR + 6;
    }

    private void removeNow(MinecraftServer server) {
        endAll(server);
        burnUntil = -1L;
        SiegeSave.CampRecord camp = camp();
        m.save().camp = null;
        m.persist();
        if (camp == null || !camp.built) {
            return;
        }
        ServerLevel level = SiegeManager.levelByName(server, camp.dimension);
        if (level == null) {
            return;
        }
        BlockPos center = new BlockPos(camp.x, camp.y, camp.z);
        for (String g : camp.guards) {
            Entity e = level.getEntity(UUID.fromString(g));
            if (e != null) {
                e.discard();
            }
        }
        if (camp.workshop != null && !camp.workshop.isEmpty()) {
            Entity e = level.getEntity(UUID.fromString(camp.workshop));
            if (e != null) {
                e.discard();
            }
        }
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(center).inflate(34, 18, 34), SiegeCamp::isCampMob)) {
            e.discard();
        }
        Map<BlockPos, BlockState> blocks = CampLayout.blocks(center, camp.facing, camp.id);
        if (m.save().campGrounds != null && m.save().campGrounds.contains(camp.id + "|" + camp.dimension)) {
            // Written down when it was built: the old ground comes back chunk by chunk (restoreGround),
            // trees, hills and all. Its barrels are not emptied any more (Simon 31.10.: survival safety):
            // what lies in one is a player's, and a barrel with something in it stays standing.
            for (int i = 0; i < 20; i++) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.getX() + random.nextGaussian() * 8, center.getY() + 1,
                        center.getZ() + random.nextGaussian() * 8, 1, 0, 0.05, 0, 0.01);
            }
            NightSiegeMod.LOGGER.info("[Night Siege] Heerlager abgebaut, das alte Gelände kommt zurück");
            return;
        }
        List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(blocks.entrySet());
        list.sort((a, b) -> Integer.compare(b.getKey().getY(), a.getKey().getY()));   // from the top down
        int taken = 0;
        for (Map.Entry<BlockPos, BlockState> e : list) {
            BlockState now = level.getBlockState(e.getKey());
            if (!now.is(e.getValue().getBlock())) {
                continue;   // someone changed it: leave it
            }
            BlockEntity be = level.getBlockEntity(e.getKey());
            if (be instanceof Container container && !container.isEmpty()) {
                continue;   // something lies in it, a player's: it stays (31.10.)
            }
            level.setBlock(e.getKey(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            taken++;
        }
        Random traces = new Random(camp.id);
        for (Map.Entry<BlockPos, BlockState> e : CampLayout.floor(center, camp.facing, camp.id).entrySet()) {
            BlockState now = level.getBlockState(e.getKey());
            if (!now.is(e.getValue().getBlock()) || now.is(Blocks.GRASS_BLOCK)) {
                continue;
            }
            // Mostly grass again; trampled spots stay for a while.
            level.setBlock(e.getKey(), traces.nextInt(100) < 12 ? Blocks.COARSE_DIRT.defaultBlockState()
                    : Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int i = 0; i < 20; i++) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.getX() + random.nextGaussian() * 8, center.getY() + 1,
                    center.getZ() + random.nextGaussian() * 8, 1, 0, 0.05, 0, 0.01);
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Heerlager abgebaut ({} Blöcke)", taken);
    }

    /**
     * Every spot the camp changes when it is built: its blocks, and per floor column the earth
     * filled into dips below, the floor and the space cleared above it.
     */
    static java.util.Set<BlockPos> spots(BlockPos center, int facing, long id) {
        java.util.Set<BlockPos> out = new java.util.LinkedHashSet<>();
        int floorY = center.getY();
        for (BlockPos under : CampLayout.floor(center, facing, id).keySet()) {
            for (int y = floorY - 11; y <= floorY + CampLayout.CLEAR; y++) {
                out.add(new BlockPos(under.getX(), y, under.getZ()));
            }
        }
        out.addAll(CampLayout.blocks(center, facing, id).keySet());
        return out;
    }

    /**
     * Update 30.10. (Simon: "auf keinen fall in irgendwelchen von spieler platzierten blöcken"): every
     * spot the camp would change is nature (or water), and no chunk it touches holds a player's building.
     */
    static boolean untouched(ServerLevel level, BlockPos center, int facing, long id, Reasons why) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (BlockPos pos : spots(center, facing, id)) {
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            if (seen.add(PlayerBuilt.key(cx, cz))) {
                if (!level.hasChunk(cx, cz) || PlayerBuilt.marked(level, cx, cz)) {
                    if (why != null) {
                        why.detail = level.hasChunk(cx, cz) ? "Spieler haben dort gebaut" : "Chunk nicht geladen";
                    }
                    return false;
                }
            }
            BlockState state = level.getBlockState(pos);
            if (!natural(state) && (state.getFluidState().isEmpty() || state.hasBlockEntity())) {
                if (why != null) {
                    why.detail = pos.getX() + " " + pos.getY() + " " + pos.getZ() + ": " + state;
                }
                return false;
            }
        }
        return true;
    }

    /** The camps whose old ground is coming back: a few chunks every tick, once their chunks are loaded. */
    private final Map<String, ExpeditionSnapshot> groundSnaps = new java.util.HashMap<>();
    /** Per waiting camp the blocks it set itself (its layout's), so a player's blocks there stay (31.10.). */
    private final Map<String, java.util.Set<Block>> groundBlocks = new java.util.HashMap<>();
    /** For the tests: blocks of old ground put back so far. */
    private int groundPut;

    /**
     * Survival safety (Simon 31.10.): every waiting camp in turn, each with the chunks of it that are
     * loaded now (the first one waiting for a chunk nobody visits no longer holds up the others).
     * A spot keeps a player's thing (a chest with something in it, a block of their own, a portal).
     */
    private void restoreGround(MinecraftServer server) {
        List<String> pending = m.save().campGrounds;
        if (pending == null || pending.isEmpty()) {
            return;
        }
        SiegeSave.CampRecord standing = camp();
        String standingKey = standing == null ? null : standing.id + "|" + standing.dimension;
        int[] budget = {6000};
        boolean changed = false;
        for (String key : new ArrayList<>(pending)) {
            if (budget[0] <= 0) {
                break;
            }
            if (key.equals(standingKey)) {
                continue;   // only the standing camp's ground is written down
            }
            String[] parts = key.split("\\|", 2);
            int id;
            try {
                id = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                pending.remove(key);
                changed = true;
                continue;
            }
            ServerLevel level = parts.length > 1 ? SiegeManager.levelByName(server, parts[1]) : null;
            if (level == null) {
                pending.remove(key);
                groundSnaps.remove(key);
                changed = true;
                continue;
            }
            final int campId = id;
            ExpeditionSnapshot snap = groundSnaps.computeIfAbsent(key, k -> ExpeditionSnapshot.loadCamp(server, campId));
            java.util.Set<Block> own = groundBlocks.computeIfAbsent(key, k -> campBlocks(campId));
            int[] put = {0};
            for (long chunk : snap.keys()) {
                if (budget[0] <= 0) {
                    break;
                }
                int cx = (int) chunk;
                int cz = (int) (chunk >>> 32);
                if (!level.hasChunk(cx, cz)) {
                    continue;   // back once someone comes by
                }
                List<BlockPos> where = snap.places(chunk);
                if (where == null) {
                    snap.forget(chunk);
                    continue;
                }
                List<Integer> order = new ArrayList<>();
                for (int i = 0; i < where.size(); i++) {
                    order.add(i);
                }
                order.sort((a, b) -> Integer.compare(where.get(a).getY(), where.get(b).getY()));   // from the bottom up
                NightDamage.quietly(() -> {
                    for (int i : order) {
                        BlockState old = snap.old(chunk, i);
                        BlockPos pos = where.get(i);
                        if (old == null || level.getBlockState(pos) == old
                                || !Teardown.mayReset(level, pos, old, now -> own.contains(now.getBlock()))) {
                            continue;
                        }
                        level.setBlock(pos, old, Block.UPDATE_CLIENTS);
                        put[0]++;
                    }
                });
                budget[0] -= where.size();
                snap.forget(chunk);
            }
            groundPut += put[0];
            if (snap.chunkCount() == 0) {
                snap.delete();
                groundSnaps.remove(key);
                groundBlocks.remove(key);
                pending.remove(key);
                changed = true;
                NightSiegeMod.LOGGER.info("[Night Siege] Gelände des Heerlagers {} wiederhergestellt", id);
            }
        }
        if (changed) {
            m.persist();
        }
    }

    /** The blocks a camp of this id sets (its layout, its floor, the earth under it). */
    private static java.util.Set<Block> campBlocks(long id) {
        java.util.Set<Block> out = new java.util.HashSet<>();
        for (BlockState s : CampLayout.blocks(BlockPos.ZERO, 0, id).values()) {
            out.add(s.getBlock());
        }
        for (BlockState s : CampLayout.floor(BlockPos.ZERO, 0, id).values()) {
            out.add(s.getBlock());
        }
        out.add(Blocks.DIRT);
        out.add(Blocks.GRASS_BLOCK);
        out.add(Blocks.COARSE_DIRT);
        return out;
    }

    /** For the tests: blocks of old ground put back, and camps still waiting for theirs. */
    public String groundState() {
        return "zurück=" + groundPut + " wartend=" + (m.save().campGrounds == null ? 0 : m.save().campGrounds.size());
    }

    /** True for a block of the standing camp (players cannot break it). */
    public boolean protects(net.minecraft.world.level.Level level, BlockPos pos) {
        SiegeSave.CampRecord camp = camp();
        if (camp == null || !camp.built || !(level instanceof ServerLevel server)
                || !SiegeManager.dimensionName(server).equals(camp.dimension)) {
            return false;
        }
        if (ModContent.trapOf(level.getBlockState(pos)) != null) {
            return false;   // the engineer's field towers may be picked up again (28 October 2026)
        }
        int dx = pos.getX() - camp.x;
        int dz = pos.getZ() - camp.z;
        int dy = pos.getY() - camp.y;
        if (dy >= -1 && dy <= 4) {
            int[] l = CampLayout.local(new BlockPos(camp.x, camp.y, camp.z), camp.facing, pos);
            if (CampLayout.inForecourt(l[0], l[1])) {
                return true;   // the forecourt, its drum and benches (content update)
            }
        }
        return dy >= -1 && dy <= 11 && dx * dx + dz * dz <= (CampLayout.PALISADE + 1.5) * (CampLayout.PALISADE + 1.5);
    }

    /** A camp monster loaded from the world that belongs to no camp of now goes. */
    boolean stray(Entity entity) {
        if (!isCampMob(entity)) {
            return false;
        }
        SiegeSave.CampRecord camp = camp();
        return camp == null || !entity.entityTags().contains(TAG + "_" + camp.id);
    }

    // ------------------------------------------------------------------ admin and tests

    /**
     * Admin: a camp now for the coming siege (or tomorrow's), built at once near the core if a
     * place is loaded. Returns its middle, or null.
     */
    public BlockPos forceCamp(MinecraftServer server) {
        CoreRecord core = m.mainCore();
        if (core == null) {
            return null;
        }
        if (camp() != null) {
            remove(server);
        }
        ServerLevel level = SiegeManager.levelByName(server, core.dimension);
        if (level == null) {
            return null;
        }
        long today = m.dayNow(server);
        long day = m.save().nextSiegeDay >= today ? m.save().nextSiegeDay : today + 1;
        SiegeSave.CampRecord camp = plan(day);
        SiegeConfig cfg = m.config();
        Reasons why = new Reasons();
        for (int attempt = 0; attempt < 120; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = cfg.campMinDistance + random.nextDouble() * Math.max(0, cfg.campMaxDistance - cfg.campMinDistance);
            int x = core.x + (int) Math.round(Math.cos(angle) * distance);
            int z = core.z + (int) Math.round(Math.sin(angle) * distance);
            Integer floor = site(level, x, z, false, true, why);
            if (floor != null && untouched(level, new BlockPos(x, floor, z), CampLayout.facingTowards(core.x - x, core.z - z),
                    camp.id, why)) {
                build(level, camp, new BlockPos(x, floor, z), CampLayout.facingTowards(core.x - x, core.z - z));
                m.persist();
                NightSiegeMod.LOGGER.info("[Night Siege] Heerlager per Befehl bei {} {} {} (vorher verworfen: {})", x, floor, z, why);
                return new BlockPos(x, floor, z);
            }
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Heerlager per Befehl: kein Platz ({})", why);
        m.save().camp = null;
        m.persist();
        return null;
    }

    /** Tests: a camp at a given place (no checks), built and manned at once. */
    public void testCamp(ServerLevel level, BlockPos center, int facing, long siegeDay) {
        if (camp() != null) {
            remove(level.getServer());
        }
        SiegeSave.CampRecord camp = plan(siegeDay);
        build(level, camp, center, facing);
        man(level, camp);
    }

    /** Tests: the camp goes now (as when its night is called off). */
    public void testRemove(MinecraftServer server) {
        remove(server);
    }

    /** Tests: the player steps into the fight now (as when walking through the gate). */
    public void testJoin(ServerPlayer player) {
        SiegeSave.CampRecord camp = camp();
        if (camp != null && camp.built && player.level() instanceof ServerLevel level) {
            join(level, camp, new BlockPos(camp.x, camp.y, camp.z), player, level.getGameTime());
        }
    }

    /** Tests: the war drum, or null. */
    public BlockPos testDrum() {
        SiegeSave.CampRecord camp = camp();
        return camp == null ? null : CampLayout.drum(new BlockPos(camp.x, camp.y, camp.z), camp.facing);
    }

    /** Tests: a free spot on the forecourt's path (near the drum). */
    public BlockPos testForecourtPath() {
        SiegeSave.CampRecord camp = camp();
        return camp == null ? null : CampLayout.outsideGate(new BlockPos(camp.x, camp.y, camp.z), camp.facing);
    }

    /** Tests: the round and the gate in one line. */
    public String testRound(ServerLevel level) {
        SiegeSave.CampRecord camp = camp();
        int bars = 0;
        int gate = 0;
        if (camp != null) {
            for (BlockPos pos : CampLayout.gate(new BlockPos(camp.x, camp.y, camp.z), camp.facing)) {
                gate++;
                if (level.getBlockState(pos).is(Blocks.IRON_BARS)) {
                    bars++;
                }
            }
        }
        LivingEntity bench = camp == null ? null : bench(level, camp);
        return "round=" + (round == null ? "-" : round.readyCount() + "/" + round.size()) + " gate=" + bars + "/" + gate
                + " fighters=" + fighters.size() + " fallen=" + out.size()
                + (camp == null ? "" : " ring=" + (camp.ringOpen ? "open" : "shut") + " tents=" + alive(level, camp.tentGuards) + "/"
                + camp.tentGuards.size() + " bows=" + alive(level, camp.crossbows) + "/" + camp.crossbows.size()
                + " guards=" + alive(level, camp.guards) + " bench=" + (bench == null ? "-" : Math.round(bench.getHealth()) + "/"
                + Math.round(bench.getMaxHealth())));
    }

    /** Tests: true when the point lies in the forecourt (the waiting place of the fallen). */
    public boolean testInForecourt(BlockPos pos) {
        SiegeSave.CampRecord camp = camp();
        if (camp == null) {
            return false;
        }
        int[] l = CampLayout.local(new BlockPos(camp.x, camp.y, camp.z), camp.facing, pos);
        return CampLayout.inForecourt(l[0], l[1]);
    }

    /** Tests: the workbench's hit box, or null. */
    public LivingEntity testBench(ServerLevel level) {
        SiegeSave.CampRecord camp = camp();
        return camp == null ? null : bench(level, camp);
    }

    /** Tests: the guards still standing. */
    public int testGuards(ServerLevel level) {
        SiegeSave.CampRecord camp = camp();
        int n = 0;
        if (camp != null) {
            for (String g : camp.guards) {
                Entity e = level.getEntity(UUID.fromString(g));
                if (e != null && e.isAlive()) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Tests: handle a pending outcome now. */
    public void testSettle(MinecraftServer server) {
        if (outcome != null) {
            Outcome o = outcome;
            UUID by = outcomeBy;
            outcome = null;
            outcomeBy = null;
            handleOutcome(server, o, by);
        }
        if (burnUntil > 0) {
            burnUntil = -1L;
            remove(server);
        }
    }

    static Vec3 middle(SiegeSave.CampRecord camp) {
        return new Vec3(camp.x + 0.5, camp.y, camp.z + 0.5);
    }
}
