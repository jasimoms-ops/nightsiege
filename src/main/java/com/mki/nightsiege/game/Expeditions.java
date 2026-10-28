package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.CoreStats;
import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.ExpeditionKind;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Night Siege's own dungeons (dungeon update, Simon 27 October 2026). They replace the dungeon
 * cores in the structures of Dungeons and Taverns.
 *
 * <ul>
 *   <li><b>Report:</b> on the morning after a siege night, with a chance of 50 %, the scouts
 *       report a band of besiegers 250–600 blocks from the core. The core window shows where
 *       (tab "Kundschaft"), with a map to take along. Only one is open at a time; it stays until
 *       it is won.</li>
 *   <li><b>Building:</b> the gatehouse is set when its ground is loaded and nobody stands close;
 *       the rooms below follow chunk by chunk as their ground loads.</li>
 *   <li><b>The fight:</b> a signed-up player going down the stairs fights with class, kit and
 *       skills like at the war camp. Guard rooms one after the other (the gate opens when the
 *       room's monsters are dead), then the arena: waves and the champion. Who falls is out until
 *       the fight ends; when the last one falls, the attempt is lost (cleared rooms stay open).</li>
 *   <li><b>Reward:</b> a class crate and skill points for every fighter, a chest with a night
 *       fragment for the group. The dungeon stays as a ruin whose blocks may be taken.</li>
 * </ul>
 */
public final class Expeditions {

    /** On every monster of a dungeon. */
    public static final String TAG = "nightsiege_exp";
    /** On the arena's champion: the engineer's towers reach it further and take it first (28 October 2026). */
    public static final String CHAMPION_TAG = "nightsiege_exp_champion";
    /** Tag of the scouts' map (its value is the dungeon's id). */
    private static final String MAP_TAG = "nightsiege_scout";
    /** Monsters of the dungeon of now; anything else with the tag goes when it loads. */
    private static final Set<UUID> LIVE = ConcurrentHashMap.newKeySet();

    private final SiegeManager m;
    private final Random random = new Random();

    private ExpeditionLayout.Plan plan;
    private int planId = -1;
    private Map<Long, List<Map.Entry<BlockPos, BlockState>>> byChunk = new HashMap<>();

    /** Players fighting now, everyone of this attempt (for the reward), the fallen of it. */
    private final Set<UUID> fighters = new HashSet<>();
    private final Set<UUID> joined = new LinkedHashSet<>();
    private final Set<UUID> out = new HashSet<>();
    private final Map<UUID, Long> grace = new HashMap<>();
    /** Fighters who fell in a damage event, handled on the next tick. */
    private final List<UUID> falling = new ArrayList<>();
    /** The monsters of a guard room (by room), of the arena's waves. */
    private final Map<Integer, Set<UUID>> roomMobs = new HashMap<>();
    private final Set<UUID> arenaMobs = new HashSet<>();
    private boolean arenaClosed;
    private int wave;
    private long nextWaveAt = -1L;
    private UUID champion;
    private ServerBossEvent bar;
    private int buildRounds;
    /** Attempts lost (every fighter fell) since the server started: the playtest goes on after one. */
    private int lostAttempts;

    /** When each player last took a map (game time). */
    private final Map<UUID, Long> mapTaken = new HashMap<>();

    Expeditions(SiegeManager manager) {
        this.m = manager;
    }

    // ------------------------------------------------------------------ queries

    public static boolean isExpeditionMob(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG);
    }

    public boolean isFighter(Player player) {
        return player != null && !fighters.isEmpty() && fighters.contains(player.getUUID());
    }

    public SiegeSave.ExpeditionRecord record() {
        return m.save().expedition;
    }

    /** A dungeon is reported and not won yet. */
    public boolean open() {
        SiegeSave.ExpeditionRecord rec = record();
        return rec != null && !rec.won;
    }

    private static ExpeditionKind kind(SiegeSave.ExpeditionRecord rec) {
        ExpeditionKind kind = ExpeditionKind.byId(rec.kind);
        return kind == null ? ExpeditionKind.STONE_CRYPT : kind;
    }

    private ExpeditionLayout.Plan plan(SiegeSave.ExpeditionRecord rec) {
        if (plan == null || planId != rec.id) {
            plan = ExpeditionLayout.plan(new BlockPos(rec.x, rec.y, rec.z), rec.facing, kind(rec), rec.rooms, rec.id);
            planId = rec.id;
            byChunk = new HashMap<>();
            for (Map.Entry<BlockPos, BlockState> e : plan.blocks.entrySet()) {
                byChunk.computeIfAbsent(chunkKey(e.getKey().getX() >> 4, e.getKey().getZ() >> 4), k -> new ArrayList<>()).add(e);
            }
        }
        return plan;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ the report

    /** After a siege night (won or lost): the scouts may report a dungeon. */
    void afterNight(MinecraftServer server) {
        SiegeConfig cfg = m.config();
        if (!cfg.dungeonsEnabled || open() || random.nextDouble() >= cfg.expeditionChance) {
            return;
        }
        report(server, null);
    }

    /**
     * The scouts report a dungeon: a spot on land 250–600 blocks from the core whose ground
     * can carry the way down. Null when no spot was found (or no core).
     */
    public SiegeSave.ExpeditionRecord report(MinecraftServer server, ExpeditionKind forced) {
        CoreRecord core = m.mainCore();
        if (core == null) {
            return null;
        }
        ServerLevel level = SiegeManager.levelByName(server, core.dimension);
        if (level == null) {
            return null;
        }
        SiegeConfig cfg = m.config();
        int rooms = ExpeditionKind.rooms(m.currentLevel());
        int length = ExpeditionLayout.length(rooms);
        for (int attempt = 0; attempt < 80; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = cfg.expeditionMinDistance
                    + random.nextDouble() * Math.max(0, cfg.expeditionMaxDistance - cfg.expeditionMinDistance);
            int x = core.x + (int) Math.round(Math.cos(angle) * distance);
            int z = core.z + (int) Math.round(Math.sin(angle) * distance);
            int facing = CampLayout.facingTowards(x - core.x, z - core.z);   // the way leads away from the core
            String biome = groundFor(level, x, z, facing, length);
            if (biome == null) {
                continue;
            }
            SiegeSave.ExpeditionRecord rec = new SiegeSave.ExpeditionRecord();
            rec.id = random.nextInt(1_000_000) + 1;
            rec.kind = (forced != null ? forced : ExpeditionKind.forBiome(biome, random.nextInt(10))).id();
            rec.dimension = core.dimension;
            rec.x = x;
            rec.y = Integer.MIN_VALUE;
            rec.z = z;
            rec.facing = facing;
            rec.rooms = rooms;
            rec.reportedDay = m.dayNow(server);
            drawMap(level, rec, core);
            if (fighters.size() > 0) {
                endAll(server, null);
            }
            m.save().expedition = rec;
            plan = null;
            buildRounds = 0;
            m.persist();
            announce(server, rec, core);
            NightSiegeMod.LOGGER.info("[Night Siege] Kundschafter melden {} bei {} {} ({} Blöcke vom Core, Biom {})",
                    rec.kind, x, z, (int) distance, biome);
            return rec;
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Kundschafter: kein Platz für einen Dungeon gefunden");
        return null;
    }

    /**
     * The biome at a planned spot if the ground there carries a dungeon: land (no sea, no river)
     * and, along the whole way down, ground above the vaults – from the world's generator, so it
     * works for ground nobody has seen yet. Null when not.
     */
    private static String groundFor(ServerLevel level, int x, int z, int facing, int length) {
        int sea = level.getSeaLevel();
        String biome = biomePath(level, x, sea + 4, z);
        if (wet(biome)) {
            return null;   // the cheap look first: sea and rivers are out
        }
        int y0 = baseHeight(level, x, z);
        if (y0 <= sea + 1) {
            return null;
        }
        biome = biomePath(level, x, y0, z);
        BlockPos origin = new BlockPos(x, y0, z);
        for (int lz = -8; lz <= length; lz += 8) {
            for (int lx = -10; lx <= 10; lx += 10) {
                BlockPos at = CampLayout.world(origin, facing, lx, 0, lz);
                int h = baseHeight(level, at.getX(), at.getZ());
                // The vaults' roofs lie 6 below the gatehouse floor; water may not stand above them.
                if (h < y0 - 4 || h <= sea) {
                    return null;
                }
                if (lz % 24 == 0 && wet(biomePath(level, at.getX(), h, at.getZ()))) {
                    return null;
                }
            }
        }
        return biome;
    }

    private static boolean wet(String biome) {
        return biome.contains("ocean") || biome.contains("river") || biome.contains("beach") || biome.contains("shore");
    }

    /** The ground as the world's generator makes it (no trees, no buildings; works for ground nobody has seen). */
    private static int baseHeight(ServerLevel level, int x, int z) {
        return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level,
                level.getChunkSource().randomState());
    }

    private static String biomePath(ServerLevel level, int x, int y, int z) {
        Holder<Biome> biome = level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2);
        return biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
    }

    /** The scouts' map: a square around the core and the dungeon, one terrain letter per cell. */
    private static void drawMap(ServerLevel level, SiegeSave.ExpeditionRecord rec, CoreRecord core) {
        int size = 40;
        int cx = (core.x + rec.x) / 2;
        int cz = (core.z + rec.z) / 2;
        int span = Math.max(Math.abs(rec.x - core.x), Math.abs(rec.z - core.z)) + 160;
        int cell = Math.max(8, (span + size - 1) / size);
        rec.mapSize = size;
        rec.mapCell = cell;
        rec.mapX = cx - size * cell / 2;
        rec.mapZ = cz - size * cell / 2;
        int y = level.getSeaLevel() + 6;
        StringBuilder out = new StringBuilder(size * size);
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                int x = rec.mapX + col * cell + cell / 2;
                int z = rec.mapZ + row * cell + cell / 2;
                out.append(terrain(biomePath(level, x, y, z)));
            }
        }
        rec.map = out.toString();
    }

    /** One letter for the map's colour (the client knows the colours). */
    static char terrain(String b) {
        if (b.contains("deep") && b.contains("ocean")) {
            return 'o';
        }
        if (b.contains("ocean") || b.contains("river")) {
            return 'w';
        }
        if (b.contains("beach") || b.contains("shore")) {
            return 'b';
        }
        if (b.contains("frozen") || b.contains("snow") || b.contains("ice") || b.contains("grove")) {
            return 'n';
        }
        if (b.contains("peaks") || b.contains("slopes") || b.contains("stony") || b.contains("windswept")) {
            return 'm';
        }
        if (b.contains("badlands")) {
            return 'r';
        }
        if (b.contains("desert")) {
            return 'd';
        }
        if (b.contains("savanna")) {
            return 'a';
        }
        if (b.contains("swamp") || b.contains("mangrove")) {
            return 's';
        }
        if (b.contains("jungle") || b.contains("bamboo")) {
            return 'j';
        }
        if (b.contains("taiga")) {
            return 't';
        }
        if (b.contains("forest") || b.contains("cherry") || b.contains("pale_garden")) {
            return 'f';
        }
        if (b.contains("mushroom")) {
            return 'u';
        }
        return 'p';
    }

    private void announce(MinecraftServer server, SiegeSave.ExpeditionRecord rec, CoreRecord core) {
        ExpeditionKind kind = kind(rec);
        int dx = rec.x - core.x;
        int dz = rec.z - core.z;
        Component text = Component.translatable("nightsiege.expedition.report",
                Component.translatable(kind.translationKey() + ".at").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                (int) Math.round(Math.sqrt(dx * (double) dx + dz * (double) dz)), direction(dx, dz))
                .withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (m.isRegistered(p)) {
                p.sendSystemMessage(text);
                p.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.AMBIENT,
                        p.getX(), p.getY(), p.getZ(), 0.6f, 1.4f, random.nextLong()));
            }
        }
    }

    /** "Nordosten" and the like for a way from (0, 0) to (dx, dz). */
    static Component direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int index = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return Component.translatable("nightsiege.expedition.dir." + index);
    }

    // ------------------------------------------------------------------ every tick

    /** Every tick outside the nights. */
    void tick(MinecraftServer server) {
        if (!falling.isEmpty()) {
            for (UUID id : new ArrayList<>(falling)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    fallOut(server, p);
                }
            }
            falling.clear();
        }
        SiegeSave.ExpeditionRecord rec = record();
        long now = server.overworld().getGameTime();
        if (now % 10 == 0) {
            mapHints(server, rec);
        }
        if (rec == null || rec.won) {
            if (!fighters.isEmpty()) {
                endAll(server, null);
            }
            return;
        }
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level == null) {
            return;
        }
        if (m.isActive() || m.testPending() || m.nightComing(server)) {
            if (!fighters.isEmpty()) {
                endAll(server, "nightsiege.expedition.night");   // the night calls everyone home
            }
            return;
        }
        if (!rec.built) {
            if (now % 100 == 0) {
                tryBuild(level, rec);
            }
            return;
        }
        if (now % 40 == 0) {
            plan(rec);
            if (rec.chunksDone.size() < byChunk.size()) {
                buildChunks(level, rec);
            }
        }
        if (now % 5 == 0) {
            checkEntries(level, rec, now);
        }
        if (now % 200 == 0 && fighters.isEmpty() && !arenaClosed && rec.cleared >= rec.rooms) {
            // A gate left shut by a crash in the arena fight opens again.
            BlockPos gate = plan(rec).arenaGate.get(0);
            if (level.hasChunk(gate.getX() >> 4, gate.getZ() >> 4) && level.getBlockState(gate).is(Blocks.IRON_BARS)) {
                setGate(level, plan(rec).arenaGate, false);
            }
        }
        if (now % 10 == 0 && (!fighters.isEmpty() || !joined.isEmpty())) {
            tickFight(server, level, rec, now);
        }
    }

    // ------------------------------------------------------------------ building

    private void tryBuild(ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        if (!level.hasChunk(rec.x >> 4, rec.z >> 4)) {
            return;   // nobody near yet
        }
        SiegeCamp.Reasons why = new SiegeCamp.Reasons();
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = rec.x + (attempt == 0 ? 0 : random.nextInt(41) - 20);
            int z = rec.z + (attempt == 0 ? 0 : random.nextInt(41) - 20);
            Integer floor = SiegeCamp.site(level, x, z, 9, 12, false, false, why);
            if (floor == null) {
                continue;
            }
            boolean someone = false;
            for (ServerPlayer p : level.players()) {
                // Nobody close sees it pop up; nobody at all stands where its walls go.
                double limit = buildRounds < 3 ? 32.0 : 12.0;
                if (p.distanceToSqr(x + 0.5, p.getY(), z + 0.5) < limit * limit) {
                    someone = true;
                    break;
                }
            }
            if (someone) {
                buildRounds++;
                return;
            }
            build(level, rec, new BlockPos(x, floor, z));
            return;
        }
        if (++buildRounds % 12 == 0) {
            // A minute with the ground loaded and no place: a little further off.
            rec.x += random.nextInt(97) - 48;
            rec.z += random.nextInt(97) - 48;
            m.persist();
            NightSiegeMod.LOGGER.info("[Night Siege] Dungeon wartet auf einen Platz ({}), neu bei {} {}", why, rec.x, rec.z);
        }
    }

    /** Levels the ground of the gatehouse and sets everything whose ground is loaded. */
    void build(ServerLevel level, SiegeSave.ExpeditionRecord rec, BlockPos origin) {
        int lowest = level.getMinY() + 20;   // the vaults reach 18 below the gatehouse floor (flat worlds)
        if (origin.getY() < lowest) {
            origin = new BlockPos(origin.getX(), lowest, origin.getZ());
        }
        rec.x = origin.getX();
        rec.y = origin.getY();
        rec.z = origin.getZ();
        rec.built = true;
        rec.chunksDone.clear();
        plan = null;
        int y = origin.getY();
        for (int lx = -8; lx <= 8; lx++) {
            for (int lz = -10; lz <= 7; lz++) {
                BlockPos column = CampLayout.world(origin, rec.facing, lx, 0, lz);
                for (int up = 0; up <= 14; up++) {
                    BlockPos pos = column.above(up);
                    BlockState there = level.getBlockState(pos);
                    if (!there.isAir() && !there.hasBlockEntity() && SiegeCamp.natural(there)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                for (int down = 2; down <= 12; down++) {
                    BlockPos pos = column.below(down);
                    BlockState there = level.getBlockState(pos);
                    if (!there.isAir() && !there.canBeReplaced() && there.getFluidState().isEmpty()) {
                        break;
                    }
                    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                BlockPos under = column.below();
                boolean path = Math.abs(lx) <= 1 && lz <= -6;
                if (path || level.getBlockState(under).isAir() || level.getBlockState(under).canBeReplaced()
                        || !level.getBlockState(under).getFluidState().isEmpty()) {
                    level.setBlock(under, path ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
        buildChunks(level, rec);
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon {} steht bei {} {} {} (Weg nach {}, {} Räume)", rec.kind,
                rec.x, rec.y, rec.z, rec.facing, rec.rooms);
    }

    /** Sets the dungeon's blocks in every loaded chunk not done yet. */
    private void buildChunks(ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        ExpeditionLayout.Plan p = plan(rec);
        Set<BlockPos> openGates = new HashSet<>();
        for (int i = 0; i < Math.min(rec.cleared, p.roomGates.size()); i++) {
            openGates.addAll(p.roomGates.get(i));
        }
        boolean changed = false;
        for (Map.Entry<Long, List<Map.Entry<BlockPos, BlockState>>> chunk : byChunk.entrySet()) {
            if (rec.chunksDone.contains(chunk.getKey())) {
                continue;
            }
            int cx = (int) (chunk.getKey() >> 32);
            int cz = (int) (long) chunk.getKey();
            if (!level.hasChunk(cx, cz)) {
                continue;
            }
            for (Map.Entry<BlockPos, BlockState> e : chunk.getValue()) {
                BlockPos pos = e.getKey();
                BlockState now = level.getBlockState(pos);
                if (now.hasBlockEntity() && !now.is(Blocks.SPAWNER)) {
                    continue;   // somebody's chest stays where it is
                }
                BlockState want = openGates.contains(pos) ? Blocks.AIR.defaultBlockState() : e.getValue();
                if (now != want) {
                    level.setBlock(pos, want, Block.UPDATE_CLIENTS);
                }
            }
            rec.chunksDone.add(chunk.getKey());
            changed = true;
        }
        if (changed) {
            m.persist();
        }
    }

    // ------------------------------------------------------------------ fighters

    private boolean inside(ExpeditionLayout.Plan p, Entity e, double margin) {
        Vec3 at = e.position();
        for (AABB box : p.inside) {
            if (box.inflate(margin).contains(at)) {
                return true;
            }
        }
        return false;
    }

    private void checkEntries(ServerLevel level, SiegeSave.ExpeditionRecord rec, long now) {
        ExpeditionLayout.Plan p = plan(rec);
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            if (fighters.contains(id) || out.contains(id) || !m.isRegistered(player) || player.isSpectator()
                    || player.getAbilities().instabuild || !player.isAlive() || Drones.isDrone(player)) {
                continue;
            }
            if (inside(p, player, 0.0)) {
                join(level, rec, player, now);
            }
        }
    }

    /** A signed-up player goes down the stairs: horn, class cards, kit, skills, grace. */
    void join(ServerLevel level, SiegeSave.ExpeditionRecord rec, ServerPlayer player, long now) {
        boolean first = fighters.isEmpty();
        UUID id = player.getUUID();
        if (joined.isEmpty()) {
            rec.attempts++;   // a new attempt
        }
        fighters.add(id);
        joined.add(id);
        m.stats().dungeonJoin(player, rec.kind, m.currentLevel(), rec.rooms, rec.attempts, now);
        int seconds = m.config().expeditionGraceSeconds;
        grace.put(id, now + seconds * 20L);
        ExpeditionKind kind = kind(rec);
        if (first) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(rec.x, rec.y, rec.z) < 160.0 * 160.0) {
                    p.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.HOSTILE,
                            rec.x, rec.y, rec.z, 64f, 0.8f, random.nextLong()));
                }
            }
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(kind.translationKey())
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("nightsiege.expedition.subtitle", seconds)
                .withStyle(ChatFormatting.GOLD)));
        player.sendSystemMessage(Component.translatable("nightsiege.expedition.enter", seconds).withStyle(ChatFormatting.GOLD));
        KitService kits = m.kits();
        if (m.config().nightKits && !kits.hasStash(player)) {
            kits.equip(player, m.kitLevel());
            if (kits.hasStash(player)) {
                List<String> list = m.save().expeditionKits;
                if (list == null) {
                    m.save().expeditionKits = list = new ArrayList<>();
                }
                if (!list.contains(id.toString())) {
                    list.add(id.toString());
                }
                m.persist();
            }
        }
        m.campClassPick(player, seconds);
        SkillService.apply(player, m.save().skillsOf(id.toString()), m.skillTree());
        showBar(rec, player);
        NightSiegeMod.LOGGER.info("[Night Siege] {} betritt den Dungeon {}", player.getScoreboardName(), rec.kind);
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
        if (player.level() instanceof ServerLevel level) {
            for (UUID mobId : allMobs()) {
                if (level.getEntity(mobId) instanceof Mob mob && mob.getTarget() == player) {
                    mob.setTarget(null);
                }
            }
        }
    }

    private void giveBack(ServerPlayer player) {
        String id = player.getUUID().toString();
        List<String> list = m.save().expeditionKits;
        if (list != null && list.remove(id) && m.kits().hasStash(player)) {
            m.kits().restore(player);   // persists
        }
        SkillService.clear(player);
    }

    /** A logged-in player: a dungeon kit from before goes back. */
    void onJoin(ServerPlayer player) {
        List<String> list = m.save().expeditionKits;
        if (list != null && list.contains(player.getUUID().toString()) && !fighters.contains(player.getUUID())) {
            giveBack(player);
        }
    }

    /** Everyone out of the fight; the monsters of the open room and the arena go. */
    void endAll(MinecraftServer server, String messageKey) {
        for (UUID id : new ArrayList<>(fighters)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                leave(p, messageKey);
            }
        }
        fighters.clear();
        resetAttempt(server, false);
    }

    /** The fallen fighter: whole again, own things back, put down before the gate. */
    private void fallOut(MinecraftServer server, ServerPlayer player) {
        SiegeSave.ExpeditionRecord rec = record();
        out.add(player.getUUID());
        m.stats().dungeonFell();
        leave(player, null);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        if (rec != null && rec.built) {
            ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
            BlockPos door = plan(rec).door;
            if (level != null) {
                player.teleportTo(level, door.getX() + 0.5, door.getY(), door.getZ() + 0.5, java.util.Set.of(),
                        player.getYRot(), player.getXRot(), true);
            }
        }
        player.sendSystemMessage(Component.translatable("nightsiege.expedition.fallen").withStyle(ChatFormatting.RED));
        if (fighters.isEmpty()) {
            for (UUID id : joined) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    p.sendSystemMessage(Component.translatable("nightsiege.expedition.lost").withStyle(ChatFormatting.DARK_RED));
                }
            }
            resetAttempt(server, true);
        }
    }

    // ------------------------------------------------------------------ the fight

    private void tickFight(MinecraftServer server, ServerLevel level, SiegeSave.ExpeditionRecord rec, long now) {
        ExpeditionLayout.Plan p = plan(rec);
        for (UUID id : new ArrayList<>(fighters)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                fighters.remove(id);   // logged out: the own things come back when they log in again
                grace.remove(id);
                continue;
            }
            if (player.level() != level || !inside(p, player, 1.5)) {
                leave(player, "nightsiege.expedition.left");
            }
        }
        if (fighters.isEmpty()) {
            m.stats().dungeonEnd("verlassen", null);
            resetAttempt(server, false);
            return;
        }
        ExpeditionKind kind = kind(rec);
        int lvl = m.currentLevel();
        if (rec.cleared < rec.rooms) {
            int room = rec.cleared;
            Set<UUID> mobs = roomMobs.get(room);
            if (mobs == null) {
                AABB zone = p.roomZones.get(room);
                for (UUID id : fighters) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null && zone.contains(player.position())) {
                        spawnRoom(level, rec, p, kind, room, lvl);
                        break;
                    }
                }
            } else {
                mobs.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity l) || !l.isAlive());
                if (mobs.isEmpty()) {
                    openRoom(level, rec, p, room);
                }
            }
        } else if (!arenaClosed) {
            boolean all = true;
            for (UUID id : fighters) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null || !p.arena.deflate(0.5, 0, 0.5).contains(player.position())) {
                    all = false;
                    break;
                }
            }
            if (all) {
                setGate(level, p.arenaGate, true);
                arenaClosed = true;
                wave = 0;
                nextWaveAt = now + 60;
                level.playSound(null, p.arenaGate.get(0), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.6f);
                for (UUID id : fighters) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) {
                        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
                        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("nightsiege.expedition.arena")
                                .withStyle(ChatFormatting.DARK_RED)));
                        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
                                "nightsiege.expedition.arena_sub", rec.rooms).withStyle(ChatFormatting.GOLD)));
                    }
                }
            }
        } else if (champion == null) {
            arenaMobs.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity l) || !l.isAlive());
            if (arenaMobs.isEmpty()) {
                if (nextWaveAt < 0) {
                    nextWaveAt = now + 80;   // a breath between the waves
                } else if (now >= nextWaveAt) {
                    nextWaveAt = -1L;
                    if (wave < rec.rooms) {
                        spawnWave(level, rec, p, kind, lvl);
                    } else {
                        spawnChampion(level, rec, p, kind, lvl);
                    }
                }
            }
        } else {
            Entity c = level.getEntity(champion);
            if (!(c instanceof LivingEntity l) || !l.isAlive()) {
                win(server, level, rec);
                return;
            }
        }
        updateBar(level, rec);
    }

    private void spawnRoom(ServerLevel level, SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p, ExpeditionKind kind,
                           int room, int lvl) {
        Set<UUID> mobs = new HashSet<>();
        int count = ExpeditionKind.guards(lvl, fighters.size());
        List<BlockPos> spots = p.roomSpots.get(room);
        for (int i = 0; i < count; i++) {
            BlockPos base = spots.get(i % spots.size());
            BlockPos spot = i < spots.size() ? base : base.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
            Mob mob = spawn(level, rec, kind.mobs().get(random.nextInt(kind.mobs().size())), spot);
            if (mob != null) {
                mobs.add(mob.getUUID());
            }
        }
        roomMobs.put(room, mobs);
        level.playSound(null, p.roomCenters.get(room), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.6f, 0.8f);
    }

    private void openRoom(ServerLevel level, SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p, int room) {
        setGate(level, p.roomGates.get(room), false);
        rec.cleared = room + 1;
        roomMobs.remove(room);
        m.stats().dungeonProgress(rec.cleared, 0, false);
        m.persist();
        level.playSound(null, p.roomGates.get(room).get(0), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2f, 0.7f);
        Component text = Component.translatable(rec.cleared >= rec.rooms ? "nightsiege.expedition.room_last"
                : "nightsiege.expedition.room_open", rec.cleared, rec.rooms).withStyle(ChatFormatting.GREEN);
        for (UUID id : fighters) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(text));
            }
        }
    }

    private void spawnWave(ServerLevel level, SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p, ExpeditionKind kind,
                           int lvl) {
        wave++;
        m.stats().dungeonProgress(rec.cleared, wave, false);
        int count = ExpeditionKind.wave(lvl, fighters.size());
        for (int i = 0; i < count; i++) {
            BlockPos base = p.waveSpots.get(i % p.waveSpots.size());
            BlockPos spot = base.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
            Mob mob = spawn(level, rec, kind.mobs().get(random.nextInt(kind.mobs().size())), spot);
            if (mob != null) {
                arenaMobs.add(mob.getUUID());
            }
        }
        level.playSound(null, p.arenaCenter, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 1.2f);
        Component text = Component.translatable("nightsiege.expedition.wave", wave, rec.rooms).withStyle(ChatFormatting.RED);
        for (UUID id : fighters) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(text));
            }
        }
    }

    private void spawnChampion(ServerLevel level, SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p, ExpeditionKind kind,
                               int lvl) {
        Mob mob = spawn(level, rec, kind.champion(), p.arenaCenter);
        if (mob == null) {
            win(level.getServer(), level, rec);   // nothing to fight (a missing monster): the way is free
            return;
        }
        double factor = ExpeditionKind.championLife(lvl) * (1.0 + 0.5 * Math.max(0, fighters.size() - 1));
        double health = mob.getMaxHealth() * factor;
        BossService.set(mob, Attributes.MAX_HEALTH, health);
        mob.setHealth((float) health);
        BossService.set(mob, Attributes.SCALE, 1.35);
        BossService.set(mob, Attributes.KNOCKBACK_RESISTANCE, 0.8);
        mob.setCustomName(Component.translatable(kind.championKey()).withStyle(ChatFormatting.GOLD));
        mob.setCustomNameVisible(true);
        mob.addTag(CHAMPION_TAG);
        champion = mob.getUUID();
        m.stats().dungeonProgress(rec.cleared, wave, true);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.arenaCenter.getX() + 0.5, p.arenaCenter.getY() + 1,
                p.arenaCenter.getZ() + 0.5, 1, 0, 0, 0, 0);
        level.playSound(null, p.arenaCenter, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 1.2f);
        Component text = Component.translatable("nightsiege.expedition.champion",
                Component.translatable(kind.championKey())).withStyle(ChatFormatting.GOLD);
        for (UUID id : fighters) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(text));
            }
        }
    }

    /** A monster of the dungeon: no siege monster (no night counting, its own drops), only for the fighters. */
    private Mob spawn(ServerLevel level, SiegeSave.ExpeditionRecord rec, String entityId, BlockPos spot) {
        SiegeConfig cfg = m.config();
        int lvl = m.currentLevel();
        Mob mob = MobFactory.spawnAt(level, spot, cfg.withoutEffects(entityId), lvl, cfg, random, SiegeEvent.NONE, false);
        if (mob == null) {
            mob = MobFactory.spawnAt(level, spot, "minecraft:zombie", lvl, cfg, random, SiegeEvent.NONE, false);
        }
        if (mob == null) {
            return null;
        }
        mark(mob, rec);
        for (Entity passenger : mob.getPassengers()) {
            if (passenger instanceof Mob rider) {
                mark(rider, rec);
            }
        }
        return mob;
    }

    private static void mark(Mob mob, SiegeSave.ExpeditionRecord rec) {
        LIVE.add(mob.getUUID());
        mob.removeTag(MobFactory.TAG);   // not a monster of the night: drops as ever, no night counting
        mob.addTag(TAG);
        mob.addTag(TAG + "_" + rec.id);
        mob.setPersistenceRequired();
    }

    private Set<UUID> allMobs() {
        Set<UUID> all = new HashSet<>(arenaMobs);
        for (Set<UUID> s : roomMobs.values()) {
            all.addAll(s);
        }
        if (champion != null) {
            all.add(champion);
        }
        return all;
    }

    private static void setGate(ServerLevel level, List<BlockPos> gate, boolean closed) {
        for (BlockPos pos : gate) {
            level.setBlock(pos, closed ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** The attempt is over without a win: its monsters go, the arena opens, the fallen may come again. */
    private void resetAttempt(MinecraftServer server, boolean lost) {
        SiegeSave.ExpeditionRecord rec = record();
        ServerLevel level = rec == null ? null : SiegeManager.levelByName(server, rec.dimension);
        if (level != null) {
            for (UUID id : allMobs()) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
                LIVE.remove(id);
            }
            if (arenaClosed && rec.built) {
                setGate(level, plan(rec).arenaGate, false);
            }
        }
        roomMobs.clear();
        arenaMobs.clear();
        champion = null;
        arenaClosed = false;
        wave = 0;
        nextWaveAt = -1L;
        out.clear();
        joined.clear();
        grace.clear();
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        m.stats().dungeonEnd(lost ? "verloren" : "abgebrochen", null);   // nothing if the attempt is written already
        if (lost) {
            lostAttempts++;
            NightSiegeMod.LOGGER.info("[Night Siege] Dungeon-Versuch verloren ({} von {} Räumen offen)",
                    rec == null ? 0 : rec.cleared, rec == null ? 0 : rec.rooms);
        }
    }

    /** The champion fell: rewards, the chest with the fragment, the ruin. */
    private void win(MinecraftServer server, ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        ExpeditionLayout.Plan p = plan(rec);
        ExpeditionKind kind = kind(rec);
        rec.won = true;
        setGate(level, p.arenaGate, false);
        // The group's chest with the night fragment, facing the way in.
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .rotate(CampLayout.rotation(rec.facing));
        level.setBlock(p.chest, chest, Block.UPDATE_ALL);
        BlockEntity be = level.getBlockEntity(p.chest);
        Item fragment = DungeonContent.NIGHT_FRAGMENTS.get(kind.fragment());
        if (be instanceof Container box && fragment != null) {
            box.setItem(13, new ItemStack(fragment));
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.chest.getX() + 0.5, p.chest.getY() + 1, p.chest.getZ() + 0.5,
                40, 0.6, 0.8, 0.6, 0.3);
        // The signal fire goes out: nobody gathers here any more.
        BlockState fire = level.getBlockState(p.fire);
        if (fire.hasProperty(BlockStateProperties.LIT)) {
            level.setBlock(p.fire, fire.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
        }
        Set<UUID> who = new LinkedHashSet<>(joined);
        int lvl = m.currentLevel();
        SiegeConfig cfg = m.config();
        m.crates().awardEach(server, who, CrateKind.EVENT, lvl);
        for (UUID id : who) {
            if (cfg.expeditionSkillPoints > 0) {
                m.save().skillsOf(id.toString()).award(cfg.expeditionSkillPoints);
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("nightsiege.expedition.reward", cfg.expeditionSkillPoints,
                        Component.translatable("item.nightsiege.night_fragment_" + kind.fragment().id())).withStyle(ChatFormatting.AQUA));
                level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            }
        }
        List<String> names = new ArrayList<>();
        for (UUID id : who) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            names.add(player == null ? "?" : player.getScoreboardName());
        }
        m.broadcastRegistered(server, Component.translatable("nightsiege.expedition.won", String.join(", ", names),
                Component.translatable(kind.translationKey())).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon {} bezwungen von {}", rec.kind, names);
        m.stats().dungeonEnd("gewonnen", List.of("klassenkiste_je_spieler", "punkte_je_spieler:" + cfg.expeditionSkillPoints,
                "fragment:" + kind.fragment().id()));
        endAll(server, null);
        m.persist();
    }

    private void showBar(SiegeSave.ExpeditionRecord rec, ServerPlayer player) {
        if (bar == null) {
            bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable(kind(rec).translationKey()),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        }
        bar.addPlayer(player);
    }

    private void updateBar(ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        if (bar == null) {
            return;
        }
        ExpeditionKind kind = kind(rec);
        if (champion != null && level.getEntity(champion) instanceof LivingEntity c) {
            bar.setName(Component.translatable(kind.championKey()));
            bar.setColor(BossEvent.BossBarColor.PURPLE);
            bar.setProgress((float) Math.max(0.0, Math.min(1.0, c.getHealth() / c.getMaxHealth())));
        } else if (arenaClosed) {
            bar.setName(Component.translatable("nightsiege.expedition.bar_wave", Component.translatable(kind.translationKey()),
                    Math.max(1, wave), rec.rooms));
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setProgress(1f);
        } else {
            bar.setName(Component.translatable("nightsiege.expedition.bar_room", Component.translatable(kind.translationKey()),
                    Math.min(rec.cleared + 1, rec.rooms), rec.rooms));
            bar.setColor(BossEvent.BossBarColor.YELLOW);
            bar.setProgress((float) Math.min(1.0, rec.cleared / (double) Math.max(1, rec.rooms)));
        }
    }

    // ------------------------------------------------------------------ damage and death

    /** Damage in a dungeon fight, for the statistics. */
    void afterDamage(LivingEntity victim, DamageSource source, float taken) {
        if (fighters.isEmpty() || taken <= 0) {
            return;
        }
        Entity raw = source.getEntity();
        Entity hitter = Companions.isCompanion(raw) ? Companions.ownerPlayer(raw) : raw;
        if (isExpeditionMob(victim) && hitter instanceof ServerPlayer p && fighters.contains(p.getUUID())) {
            m.stats().dungeonDamage(true, taken);
        } else if (victim instanceof ServerPlayer p && fighters.contains(p.getUUID()) && isExpeditionMob(raw)) {
            m.stats().dungeonDamage(false, taken);
        }
    }

    /** Whether a dungeon monster may pick this target: fighters past their grace and their companions. */
    public boolean mayTarget(Mob mob, LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            if (!fighters.contains(player.getUUID())) {
                return false;
            }
            Long until = grace.get(player.getUUID());
            return until == null || mob.level().getGameTime() >= until;
        }
        UUID owner = Companions.isCompanion(target) ? Companions.ownerOf(target) : null;
        return owner != null && fighters.contains(owner);
    }

    /** Whether a hit lands: the dungeon's monsters spare the fighters in their grace. */
    public boolean allowDamage(LivingEntity victim, DamageSource source) {
        if (victim instanceof ServerPlayer player && isExpeditionMob(source.getEntity())) {
            Long until = grace.get(player.getUUID());
            return fighters.contains(player.getUUID()) && (until == null || victim.level().getGameTime() >= until);
        }
        return true;
    }

    /** Before a death: a fighter does not die, they are out until the fight ends. False when called off. */
    public boolean allowDeath(LivingEntity entity) {
        if (entity instanceof ServerPlayer player && fighters.contains(player.getUUID())) {
            player.setHealth(player.getMaxHealth());
            player.removeAllEffects();
            player.clearFire();
            grace.put(player.getUUID(), Long.MAX_VALUE);   // spared until it is handled
            if (!falling.contains(player.getUUID())) {
                falling.add(player.getUUID());
            }
            return false;
        }
        return true;
    }

    /** A dungeon monster loaded from the world that belongs to no fight of now goes. */
    static boolean stray(Entity entity) {
        return isExpeditionMob(entity) && !LIVE.contains(entity.getUUID());
    }

    /** True for a block of a dungeon not won yet (players cannot break it). */
    public boolean protects(net.minecraft.world.level.Level level, BlockPos pos) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null || rec.won || !rec.built || !(level instanceof ServerLevel server)
                || !SiegeManager.dimensionName(server).equals(rec.dimension)) {
            return false;
        }
        if (ModContent.trapOf(level.getBlockState(pos)) != null) {
            return false;   // the engineer takes his field towers along to the next room (28 October 2026)
        }
        int dx = pos.getX() - rec.x;
        int dz = pos.getZ() - rec.z;
        int reach = ExpeditionLayout.length(rec.rooms) + 16;
        if (dx * dx + dz * dz > reach * reach) {
            return false;
        }
        return plan(rec).blocks.containsKey(pos);
    }

    // ------------------------------------------------------------------ the map and the core window

    /** The core window's page "Kundschaft". */
    public CoreStats.ScoutLine scoutLine(ServerPlayer viewer) {
        SiegeSave.ExpeditionRecord rec = record();
        CoreRecord core = m.mainCore();
        if (rec == null || core == null) {
            return null;
        }
        ExpeditionKind kind = kind(rec);
        CoreStats.ScoutLine line = new CoreStats.ScoutLine();
        line.kind = kind.id();
        line.fragment = kind.fragment().id();
        for (String mob : new LinkedHashSet<>(kind.mobs())) {
            line.mobs.add(mob);
        }
        line.coreX = core.x;
        line.coreZ = core.z;
        line.targetX = rec.x;
        line.targetZ = rec.z;
        line.built = rec.built;
        line.won = rec.won;
        line.rooms = rec.rooms;
        line.cleared = rec.cleared;
        line.championLife = ExpeditionKind.championLife(m.currentLevel());
        line.points = m.config().expeditionSkillPoints;
        line.fighters = fighters.size();
        line.sameWorld = SiegeManager.dimensionName(viewer.level()).equals(rec.dimension);
        line.viewerX = (int) Math.floor(viewer.getX());
        line.viewerZ = (int) Math.floor(viewer.getZ());
        line.map = rec.map == null ? "" : rec.map;
        line.mapSize = rec.mapSize;
        line.mapCell = rec.mapCell;
        line.mapX = rec.mapX;
        line.mapZ = rec.mapZ;
        return line;
    }

    /** The button "Karte mitnehmen": a map with a cross on the dungeon. */
    public void giveMap(ServerPlayer player) {
        SiegeSave.ExpeditionRecord rec = record();
        MinecraftServer server = player.level().getServer();
        if (rec == null || server == null) {
            player.sendSystemMessage(Component.translatable("nightsiege.expedition.none").withStyle(ChatFormatting.GRAY));
            return;
        }
        long now = server.overworld().getGameTime();
        Long last = mapTaken.get(player.getUUID());
        if (last != null && now - last < 100) {
            return;   // one click, one map
        }
        mapTaken.put(player.getUUID(), now);
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level == null) {
            return;
        }
        ItemStack map = scoutMap(level, rec);
        if (!player.getInventory().add(map)) {
            player.drop(map, false);
        }
        player.sendSystemMessage(Component.translatable("nightsiege.expedition.map_taken").withStyle(ChatFormatting.GOLD));
        level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1f);
    }

    static ItemStack scoutMap(ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        BlockPos target = new BlockPos(rec.x, level.getSeaLevel(), rec.z);
        ItemStack map = MapItem.create(level, rec.x, rec.z, (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, target, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("nightsiege.expedition.map_name",
                Component.translatable(kind(rec).translationKey())));
        CustomData.update(DataComponents.CUSTOM_DATA, map, tag -> tag.putInt(MAP_TAG, rec.id));
        return map;
    }

    private static int mapId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        CompoundTag tag = data.copyTag();
        return tag.getIntOr(MAP_TAG, 0);
    }

    /** With the scouts' map in hand: how far and which way (action bar). */
    private void mapHints(MinecraftServer server, SiegeSave.ExpeditionRecord rec) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int id = mapId(player.getMainHandItem());
            if (id == 0) {
                id = mapId(player.getOffhandItem());
            }
            if (id == 0) {
                continue;
            }
            Component text;
            if (rec == null || rec.id != id) {
                text = Component.translatable("nightsiege.expedition.map_old").withStyle(ChatFormatting.GRAY);
            } else if (!SiegeManager.dimensionName(player.level()).equals(rec.dimension)) {
                text = Component.translatable("nightsiege.expedition.map_world").withStyle(ChatFormatting.GRAY);
            } else {
                double dx = rec.x + 0.5 - player.getX();
                double dz = rec.z + 0.5 - player.getZ();
                int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                Component name = Component.translatable(kind(rec).translationKey()).withStyle(ChatFormatting.GOLD);
                if (rec.won) {
                    text = Component.translatable("nightsiege.expedition.map_ruin", name, distance);
                } else if (distance < 12) {
                    text = Component.translatable("nightsiege.expedition.map_here", name);
                } else {
                    text = Component.translatable("nightsiege.expedition.map_hint", name, distance, direction(dx, dz));
                }
            }
            player.connection.send(new ClientboundSetActionBarTextPacket(text));
        }
    }

    // ------------------------------------------------------------------ admin and tests

    /** Admin: the dungeon goes (a standing one is taken away block by block, the fight ends). */
    /** Lost attempts since the start (the playtest compares it). */
    public int lostAttempts() {
        return lostAttempts;
    }

    /**
     * The playtest's dungeon goes when its part is over: fight ended, monsters gone, the gatehouse
     * taken down even when it was won (no ruins of playtests), the record forgotten.
     */
    public boolean removeAll(MinecraftServer server) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec != null && rec.won) {
            rec.won = false;   // so the gatehouse goes like that of an open dungeon
        }
        return remove(server);
    }

    public boolean remove(MinecraftServer server) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null) {
            return false;
        }
        endAll(server, null);
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level != null && rec.built && !rec.won) {
            ExpeditionLayout.Plan p = plan(rec);
            for (Entity e : level.getEntitiesOfClass(Entity.class, p.bounds.inflate(4), Expeditions::isExpeditionMob)) {
                e.discard();
            }
            for (Map.Entry<BlockPos, BlockState> e : p.blocks.entrySet()) {
                BlockPos pos = e.getKey();
                if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && pos.getY() >= rec.y - 1
                        && level.getBlockState(pos).is(e.getValue().getBlock()) && !e.getValue().isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);   // the gatehouse; the vaults stay hidden
                }
            }
        }
        m.save().expedition = null;
        plan = null;
        m.persist();
        return true;
    }

    /** Admin and tests: the reported dungeon is built now at its spot (chunks are loaded on the way). */
    public BlockPos buildNow(MinecraftServer server) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null || rec.won && !rec.built) {
            return null;
        }
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level == null) {
            return null;
        }
        if (!rec.built) {
            loadAround(level, rec.x, rec.z, 12);
            Integer floor = SiegeCamp.site(level, rec.x, rec.z, 9, 12, false, true, null);
            if (floor == null) {
                floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rec.x, rec.z);
            }
            build(level, rec, new BlockPos(rec.x, floor, rec.z));
        }
        // Everything else too: its chunks are loaded (generated) here.
        ExpeditionLayout.Plan p = plan(rec);
        if (rec.won) {
            return p.door;   // a ruin is not built again
        }
        for (long key : byChunk.keySet()) {
            level.getChunk((int) (key >> 32), (int) key);
        }
        buildChunks(level, rec);
        return p.door;
    }

    private static void loadAround(ServerLevel level, int x, int z, int r) {
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /** Tests: a dungeon of this kind at a given spot, built at once (no checks). */
    public SiegeSave.ExpeditionRecord testBuild(ServerLevel level, BlockPos origin, int facing, ExpeditionKind kind, int rooms) {
        MinecraftServer server = level.getServer();
        if (record() != null) {
            endAll(server, null);
        }
        SiegeSave.ExpeditionRecord rec = new SiegeSave.ExpeditionRecord();
        rec.id = random.nextInt(1_000_000) + 1;
        rec.kind = kind.id();
        rec.dimension = SiegeManager.dimensionName(level);
        rec.x = origin.getX();
        rec.y = origin.getY();
        rec.z = origin.getZ();
        rec.facing = facing;
        rec.rooms = rooms;
        rec.reportedDay = m.dayNow(server);
        CoreRecord core = m.mainCore();
        if (core != null) {
            drawMap(level, rec, core);
        }
        m.save().expedition = rec;
        plan = null;
        loadAround(level, rec.x, rec.z, 12);
        build(level, rec, origin);
        plan(rec);
        for (long key : byChunk.keySet()) {
            level.getChunk((int) (key >> 32), (int) key);
        }
        buildChunks(level, rec);
        return rec;
    }

    /** Tests: the plan of the dungeon of now. */
    public ExpeditionLayout.Plan testPlan() {
        SiegeSave.ExpeditionRecord rec = record();
        return rec == null || !rec.built ? null : plan(rec);
    }

    /** Tests: the player goes down now. */
    public void testJoin(ServerPlayer player) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec != null && rec.built && player.level() instanceof ServerLevel level && !fighters.contains(player.getUUID())) {
            join(level, rec, player, level.getGameTime());
        }
    }

    /** Tests: one step of the fight now (as every half second). */
    public void testTick(MinecraftServer server) {
        SiegeSave.ExpeditionRecord rec = record();
        ServerLevel level = rec == null ? null : SiegeManager.levelByName(server, rec.dimension);
        if (level != null && rec.built && !rec.won) {
            tickFight(server, level, rec, level.getGameTime());
        }
    }

    /** Tests: the monsters standing now (rooms, waves, champion). */
    public List<Mob> testMobs(ServerLevel level) {
        List<Mob> out = new ArrayList<>();
        for (UUID id : allMobs()) {
            if (level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                out.add(mob);
            }
        }
        return out;
    }

    /** Tests: a short state line. */
    public String testState() {
        SiegeSave.ExpeditionRecord rec = record();
        return rec == null ? "keiner" : String.format(Locale.ROOT, "%s räume %d/%d arena %s welle %d champion %s gewonnen %s kämpfer %d",
                rec.kind, rec.cleared, rec.rooms, arenaClosed, wave, champion != null, rec.won, fighters.size());
    }
}
