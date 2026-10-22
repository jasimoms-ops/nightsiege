package com.mki.nightsiege.game;

import com.mki.nightsiege.core.DungeonMath;
import com.mki.nightsiege.core.DungeonTheme;
import com.mki.nightsiege.core.DungeonTier;
import com.mki.nightsiege.core.DungeonType;
import com.mki.nightsiege.core.NightFragment;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Dungeon cores hidden in the structures of other mods.
 *
 * <p>Nothing is touched while the world generates. When a player first stands
 * inside a matching structure, the mod decides (the same way every time) whether
 * it hides a core and, if so, sets the core into the structure. A player coming
 * near wakes it; a right click starts one to three waves with a guardian boss at
 * the end. Every defender gets a fragment of the structure's kind, one of
 * vigilance for the boss, and a dungeon map.</p>
 */
public final class DungeonService {

    private static final String TAG = "nightsiege_dungeon";
    /** The guards set into a dungeon's rooms; they stay until killed or the fight starts. */
    private static final String GUARD_TAG = "nightsiege_dungeon_guard";
    /** First time the placement of a structure's core was tried, to give up waiting for chunks after a while. */
    private final Map<String, Long> firstTry = new HashMap<>();

    private final Supplier<SiegeSave> save;
    private final Supplier<SiegeConfig> config;
    private final Random random = new Random();
    private final Map<String, Fight> fights = new HashMap<>();
    private int scanCounter;
    private boolean dirty;

    /** A dungeon fight in progress. Test fights have no record and pay out nothing. */
    private static final class Fight {
        String id;
        DungeonRecord record;
        DungeonType type;
        DungeonTheme theme;
        ServerLevel level;
        BlockPos anchor;
        int waves;
        int wave;
        int mobLevel;
        boolean boss;
        boolean test;
        long startedAt;
        long nextWaveAt;
        int waveSize;
        final Set<UUID> participants = new HashSet<>();
        final Set<UUID> fallen = new HashSet<>();
        final Set<UUID> mobs = new HashSet<>();
        ServerBossEvent bar;
    }

    public DungeonService(Supplier<SiegeSave> save, Supplier<SiegeConfig> config) {
        this.save = save;
        this.config = config;
    }

    // ------------------------------------------------------------------
    // finding and setting cores
    // ------------------------------------------------------------------

    /** Returns true when the save changed. */
    public boolean tick(MinecraftServer server) {
        if (!config.get().dungeonsEnabled) {
            return false;
        }
        tickFights(server);
        if (++scanCounter >= 20) {
            scanCounter = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isSpectator()) {
                    scan(player);
                }
            }
        }
        boolean was = dirty;
        dirty = false;
        return was;
    }

    private void scan(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Structure structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
            Identifier id = registry.getKey(structure);
            if (id == null) {
                continue;
            }
            DungeonType type = DungeonType.find(config.get().dungeons, id.toString());
            if (type == null) {
                continue;
            }
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start == null || !start.isValid()) {
                continue;
            }
            DungeonRecord record = recordFor(level, start, type, id.toString());
            if (record != null) {
                watch(level, player, record);
            }
        }
    }

    /** The record of this structure; creates it (and sets the core) the first time. */
    private DungeonRecord recordFor(ServerLevel level, StructureStart start, DungeonType type, String structureId) {
        String dim = SiegeManager.dimensionName(level);
        ChunkPos chunk = start.getChunkPos();
        for (DungeonRecord record : records()) {
            if (record.sameStructure(dim, chunk.x(), chunk.z())) {
                return record;
            }
        }
        boolean forced = forced().contains(dim + "|" + chunk.x() + "|" + chunk.z());
        if (!forced && !DungeonMath.isDungeon(level.getSeed(), structureId, chunk.x(), chunk.z(), type.chance)) {
            return null;
        }
        // The whole structure has to be there before its rooms can be judged; after
        // half a minute of waiting the loaded part has to do.
        List<BoundingBox> boxes = DungeonPlaces.boxes(start);
        String mark = dim + "|" + chunk.x() + "|" + chunk.z();
        long now = level.getGameTime();
        long first = firstTry.computeIfAbsent(mark, k -> now);
        if (!DungeonPlaces.loaded(level, boxes)) {
            if (now - first < 600) {
                return null;
            }
            boxes.removeIf(b -> !DungeonPlaces.loaded(level, List.of(b)));
            if (boxes.isEmpty()) {
                return null;
            }
        }
        firstTry.remove(mark);
        BlockPos spot = ruleSpot(level, start, type);
        if (spot == null) {
            spot = DungeonPlaces.coreSpot(level, boxes);
        }
        if (spot == null) {
            spot = coreSpot(level, start.getBoundingBox());
        }
        if (spot == null || !level.isLoaded(spot)) {
            return null;
        }
        DungeonRecord record = new DungeonRecord();
        record.type = type.key;
        record.structure = structureId;
        record.dimension = dim;
        record.chunkX = chunk.x();
        record.chunkZ = chunk.z();
        record.x = spot.getX();
        record.y = spot.getY();
        record.z = spot.getZ();
        BoundingBox all = start.getBoundingBox();
        record.minX = all.minX();
        record.minY = all.minY();
        record.minZ = all.minZ();
        record.maxX = all.maxX();
        record.maxY = all.maxY();
        record.maxZ = all.maxZ();
        level.setBlockAndUpdate(spot, DungeonContent.CORE.defaultBlockState());
        records().add(record);
        dirty = true;
        return record;
    }

    /**
     * Somewhere near the middle of the structure with a floor below: the first
     * open space above solid ground, searched in a widening square around the
     * centre column. Water counts as open, so sunken ruins work too.
     */
    private static BlockPos coreSpot(ServerLevel level, BoundingBox box) {
        BlockPos center = box.getCenter();
        for (int r = 0; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    for (int y = box.minY() + 1; y <= box.maxY(); y++) {
                        BlockPos at = new BlockPos(x, y, z);
                        if (!level.isLoaded(at)) {
                            return null;
                        }
                        if (open(level, at) && open(level, at.above()) && solid(level, at.below())) {
                            return at;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.material.Fluid fluid = state.getFluidState().getType();
        return state.getCollisionShape(level, pos).isEmpty() && fluid != Fluids.LAVA && fluid != Fluids.FLOWING_LAVA;
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    // ------------------------------------------------------------------
    // core places set by hand: kept inside the start piece, before it is turned
    // ------------------------------------------------------------------

    private static BlockPos origin(net.minecraft.world.level.levelgen.structure.StructurePiece piece) {
        if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool) {
            return pool.getPosition();
        }
        BoundingBox box = piece.getBoundingBox();
        return new BlockPos(box.minX(), box.minY(), box.minZ());
    }

    /** A template's x/z turned the way the piece was placed (pivot at the piece's origin). */
    private static int[] turn(int x, int z, net.minecraft.world.level.block.Rotation rotation) {
        if (rotation == null) {
            return new int[]{x, z};
        }
        return switch (rotation) {
            case CLOCKWISE_90 -> new int[]{-z, x};
            case CLOCKWISE_180 -> new int[]{-x, -z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    private static int[] unturn(int x, int z, net.minecraft.world.level.block.Rotation rotation) {
        if (rotation == null) {
            return new int[]{x, z};
        }
        return switch (rotation) {
            case CLOCKWISE_90 -> new int[]{z, -x};
            case CLOCKWISE_180 -> new int[]{-x, -z};
            case COUNTERCLOCKWISE_90 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    /** The template a piece was built from, or "" for pieces that are not from a template pool. */
    private static String template(net.minecraft.world.level.levelgen.structure.StructurePiece piece) {
        if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool
                && pool.getElement() instanceof net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement single) {
            return String.valueOf(single.getTemplateLocation());
        }
        return "";
    }

    /** The hand-set place of the kind in this structure, if it has one and there is room. */
    private static BlockPos ruleSpot(ServerLevel level, StructureStart start, DungeonType type) {
        if (type.core == null || type.core.size() != 3 || start.getPieces().isEmpty()) {
            return null;
        }
        BlockPos best = null;
        for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
            boolean match = type.coreTemplate == null || type.coreTemplate.isBlank()
                    ? piece == start.getPieces().get(0)
                    : type.coreTemplate.equals(template(piece));
            if (!match) {
                continue;
            }
            int[] xz = turn(type.core.get(0), type.core.get(2), piece.getRotation());
            BlockPos at = origin(piece).offset(xz[0], type.core.get(1), xz[1]);
            if (level.isLoaded(at) && DungeonPlaces.standable(level, at, 2) && (best == null || at.getY() < best.getY())) {
                best = at;
            }
        }
        return best;
    }

    /** Creative tool: the core of the dungeon the player stands in moves to the player; the place counts for its kind. */
    public void moveCoreHere(ServerPlayer player) {
        ServerLevel level = player.level();
        String dim = SiegeManager.dimensionName(level);
        BlockPos here = player.blockPosition();
        DungeonRecord record = null;
        double best = Double.MAX_VALUE;
        for (DungeonRecord r : records()) {
            if (!dim.equals(r.dimension)) {
                continue;
            }
            boolean inside = r.hasBox() && here.getX() >= r.minX - 2 && here.getX() <= r.maxX + 2
                    && here.getZ() >= r.minZ - 2 && here.getZ() <= r.maxZ + 2
                    && here.getY() >= r.minY - 2 && here.getY() <= r.maxY + 2;
            double d = here.distSqr(new BlockPos(r.x, r.y, r.z));
            if ((inside || d < 48 * 48) && (inside ? d - 1e9 : d) < best) {
                best = inside ? d - 1e9 : d;
                record = r;
            }
        }
        if (record == null) {
            player.sendSystemMessage(Component.translatable("nightsiege.dungeon.core_not_here").withStyle(ChatFormatting.RED));
            return;
        }
        if (record.beaten()) {
            player.sendSystemMessage(Component.translatable("nightsiege.dungeon.core_beaten").withStyle(ChatFormatting.RED));
            return;
        }
        if (!DungeonPlaces.standable(level, here, 2)) {
            player.sendSystemMessage(Component.translatable("nightsiege.dungeon.core_blocked").withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos old = new BlockPos(record.x, record.y, record.z);
        if (level.getBlockState(old).getBlock() == DungeonContent.CORE) {
            level.setBlockAndUpdate(old, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(here, DungeonContent.CORE.defaultBlockState());
        record.x = here.getX();
        record.y = here.getY();
        record.z = here.getZ();
        dirty = true;
        // The place, kept inside the start piece, counts for every dungeon of the kind.
        DungeonType type = DungeonType.byKey(config.get().dungeons, record.type);
        StructureStart start = startOf(level, record);
        if (type != null && start != null && !start.getPieces().isEmpty()) {
            // The smallest piece around the player: the room the core now stands in.
            net.minecraft.world.level.levelgen.structure.StructurePiece piece = start.getPieces().get(0);
            long smallest = Long.MAX_VALUE;
            for (net.minecraft.world.level.levelgen.structure.StructurePiece p : start.getPieces()) {
                BoundingBox b = p.getBoundingBox();
                long size = (long) b.getXSpan() * b.getYSpan() * b.getZSpan();
                if (b.isInside(here) && !template(p).isEmpty() && size < smallest) {
                    smallest = size;
                    piece = p;
                }
            }
            BlockPos origin = origin(piece);
            int[] xz = unturn(here.getX() - origin.getX(), here.getZ() - origin.getZ(), piece.getRotation());
            type.core = new ArrayList<>(List.of(xz[0], here.getY() - origin.getY(), xz[1]));
            type.coreTemplate = template(piece);
            Json.write(Json.configFile("nightsiege.json"), config.get());
            com.mki.nightsiege.NightSiegeMod.LOGGER.info("[Night Siege] core place of {}: {} in {}", type.key, type.core,
                    type.coreTemplate);
        }
        player.sendSystemMessage(Component.translatable("nightsiege.dungeon.core_moved", here.getX(), here.getY(), here.getZ(),
                typeName(record.type)).withStyle(ChatFormatting.GREEN));
    }

    /** Creative tool: to the core of the nearest known dungeon of a kind (or to the nearest such structure first). */
    public Component toCore(ServerPlayer player, DungeonType type) {
        ServerLevel level = player.level();
        String dim = SiegeManager.dimensionName(level);
        DungeonRecord nearest = null;
        double best = Double.MAX_VALUE;
        for (DungeonRecord r : records()) {
            if (r.type.equals(type.key) && dim.equals(r.dimension)) {
                double d = player.blockPosition().distSqr(new BlockPos(r.x, r.y, r.z));
                if (d < best) {
                    best = d;
                    nearest = r;
                }
            }
        }
        if (nearest == null) {
            travel(player, type);
            return Component.translatable("nightsiege.dungeon.core_none", typeName(type.key));
        }
        BlockPos core = new BlockPos(nearest.x, nearest.y, nearest.z);
        level.getChunk(core.getX() >> 4, core.getZ() >> 4);
        BlockPos land = core.above();
        int[][] around = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}};
        for (int[] d : around) {
            BlockPos at = core.offset(d[0], 0, d[1]);
            if (DungeonPlaces.standable(level, at, 2)) {
                land = at;
                break;
            }
        }
        player.teleportTo(land.getX() + 0.5, land.getY(), land.getZ() + 0.5);
        return Component.translatable("nightsiege.dungeon.core_tp", typeName(type.key), core.getX(), core.getY(), core.getZ());
    }

    /**
     * Test aid for CI: finds the nearest dungeon of some kinds, generates it and
     * logs where its core would stand and what that place is like.
     */
    public void probe(MinecraftServer server) {
        String[][] kinds = {{"undead_crypt", "o"}, {"creeping_crypt", "o"}, {"catacomb", "o"}, {"illager_manor", "o"},
                {"toxic_lair", "o"}, {"witch_villa", "o"}, {"combat_shrine_3", "o"}, {"stray_fort", "o"},
                {"jungle_ruins", "o"}, {"desert_ruins", "o"}, {"illager_hideout", "o"}, {"nether_keep", "n"}};
        for (String[] kind : kinds) {
            DungeonType type = DungeonType.byKey(config.get().dungeons, kind[0]);
            ServerLevel level = "n".equals(kind[1]) ? server.getLevel(net.minecraft.world.level.Level.NETHER) : server.overworld();
            if (type == null || level == null) {
                continue;
            }
            long t0 = System.nanoTime();
            try {
                List<Holder<Structure>> list = holders(level, type);
                Pair<BlockPos, Holder<Structure>> found = list.isEmpty() ? null : level.getChunkSource().getGenerator()
                        .findNearestMapStructure(level, HolderSet.direct(list), BlockPos.ZERO, 40, false);
                if (found == null) {
                    com.mki.nightsiege.NightSiegeMod.LOGGER.info("[Night Siege] PROBE {}: none found", kind[0]);
                    continue;
                }
                ChunkPos chunk = ChunkPos.containing(found.getFirst());
                level.getChunk(chunk.x(), chunk.z());
                StructureStart start = null;
                for (StructureStart s : level.structureManager().startsForStructure(chunk, st -> true)) {
                    if (s.isValid()) {
                        start = s;
                        break;
                    }
                }
                if (start == null) {
                    com.mki.nightsiege.NightSiegeMod.LOGGER.info("[Night Siege] PROBE {}: no start at {}", kind[0], chunk);
                    continue;
                }
                List<BoundingBox> boxes = DungeonPlaces.boxes(start);
                for (BoundingBox box : boxes) {
                    for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                        for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                            level.getChunk(cx, cz);
                        }
                    }
                }
                long t1 = System.nanoTime();
                BlockPos spot = DungeonPlaces.coreSpot(level, boxes);
                long t2 = System.nanoTime();
                BoundingBox all = start.getBoundingBox();
                String where = spot == null ? "NONE" : spot.toShortString() + " (" + (spot.getY() - all.minY()) + " over the bottom, "
                        + (all.maxY() - spot.getY()) + " under the top, sky " + level.canSeeSky(spot.above(2)) + ")";
                com.mki.nightsiege.NightSiegeMod.LOGGER.info("[Night Siege] PROBE {}: {} pieces, box {}, core {} - gen {} ms, search {} ms",
                        kind[0], start.getPieces().size(), all, where, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000);
            } catch (RuntimeException e) {
                com.mki.nightsiege.NightSiegeMod.LOGGER.info("[Night Siege] PROBE {}: failed {}", kind[0], e.toString());
            }
        }
    }

    /** The structure a record belongs to, if it is still there. */
    private StructureStart startOf(ServerLevel level, DungeonRecord record) {
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(record.chunkX, record.chunkZ),
                s -> record.structure.equals(String.valueOf(registry.getKey(s))))) {
            if (start.isValid()) {
                return start;
            }
        }
        return null;
    }

    /** Monsters of the dungeon's theme in its rooms: on the way to the core they have to be fought. */
    private void setGuards(ServerLevel level, DungeonRecord record, ServerPlayer waker) {
        DungeonType type = DungeonType.byKey(config.get().dungeons, record.type);
        StructureStart start = startOf(level, record);
        if (type == null || start == null) {
            return;
        }
        List<BoundingBox> boxes = DungeonPlaces.boxes(start);
        boxes.removeIf(b -> !DungeonPlaces.loaded(level, List.of(b)));
        if (boxes.isEmpty()) {
            return;
        }
        int players = 0;
        int spent = 0;
        for (ServerPlayer near : level.players()) {
            if (!near.isSpectator() && near.distanceToSqr(waker) <= DungeonMath.FIGHT_RADIUS * DungeonMath.FIGHT_RADIUS) {
                players++;
                PlayerSkills skills = save.get().skillsOf(near.getUUID().toString());
                spent += Math.max(0, skills.earned - skills.points() - skills.donated);
            }
        }
        players = Math.max(1, players);
        int mobLevel = DungeonMath.level(type.tier(), spent / players);
        int count = DungeonMath.guards(type.tier(), players, start.getPieces().size());
        List<String> pool = new ArrayList<>();
        for (String id : type.theme().mobs()) {
            if (!MobFactory.isUnavailable(id)) {
                pool.add(id);
            }
        }
        if (pool.isEmpty()) {
            pool.add("minecraft:zombie");
        }
        for (BlockPos spot : DungeonPlaces.guardSpots(level, boxes, count, waker.blockPosition(), random)) {
            Mob mob = MobFactory.spawnAt(level, spot, pool.get(random.nextInt(pool.size())), mobLevel,
                    config.get(), random, SiegeEvent.NONE, false);
            if (mob != null) {
                mob.addTag(GUARD_TAG);
                mob.setPersistenceRequired();
            }
        }
    }

    /**
     * Before the fight: everything that would join in on its own is sent away. Iron
     * and snow golems (some structures have dozens), the guards and every other
     * monster in or near the structure. Villagers and animals stay.
     */
    private void clearHelpers(ServerLevel level, DungeonRecord record, BlockPos anchor) {
        net.minecraft.world.phys.AABB area = record != null && record.hasBox()
                ? new net.minecraft.world.phys.AABB(record.minX, record.minY, record.minZ,
                        record.maxX + 1, record.maxY + 1, record.maxZ + 1).inflate(16)
                : new net.minecraft.world.phys.AABB(anchor).inflate(48);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> true)) {
            String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
            boolean golem = id.endsWith("_golem");
            boolean monster = mob instanceof net.minecraft.world.entity.monster.Enemy;
            if ((golem || monster) && !mob.entityTags().contains(TAG)) {
                level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY(0.5), mob.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
                mob.discard();
            }
        }
    }

    /** Wakes a dormant core when a player comes close, and keeps an awake one glowing. */
    private void watch(ServerLevel level, ServerPlayer player, DungeonRecord record) {
        BlockPos core = new BlockPos(record.x, record.y, record.z);
        if (!level.isLoaded(core)) {
            return;
        }
        // A core that went missing (and was not beaten) comes back.
        if (!record.beaten() && level.getBlockState(core).getBlock() != DungeonContent.CORE && !fights.containsKey(key(record))) {
            level.setBlockAndUpdate(core, DungeonContent.CORE.defaultBlockState());
        }
        if (record.beaten()) {
            return;
        }
        if (!record.guarded) {
            record.guarded = true;
            dirty = true;
            setGuards(level, record, player);
        }
        double distance = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(core)));
        if (distance > DungeonMath.WAKE_RADIUS) {
            return;
        }
        if (DungeonRecord.DORMANT.equals(record.state)) {
            record.state = DungeonRecord.AWAKE;
            dirty = true;
            level.playSound(null, core, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2f, 0.6f);
            level.playSound(null, core, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 0.5f);
            for (ServerPlayer near : level.players()) {
                if (near.distanceToSqr(Vec3.atCenterOf(core)) <= DungeonMath.FIGHT_RADIUS * DungeonMath.FIGHT_RADIUS) {
                    near.sendSystemMessage(Component.translatable("nightsiege.dungeon.awake",
                            typeName(record.type)).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
        }
        if (!fights.containsKey(key(record))) {
            // A beam of light shows where the core waits.
            for (int i = 0; i < 6; i++) {
                level.sendParticles(ParticleTypes.END_ROD, core.getX() + 0.5, core.getY() + 1.2 + i * 0.8,
                        core.getZ() + 0.5, 1, 0.05, 0.1, 0.05, 0.0);
            }
            player.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.translatable("nightsiege.dungeon.hint", typeName(record.type))));
        }
    }

    // ------------------------------------------------------------------
    // starting a fight
    // ------------------------------------------------------------------

    /** Right click on a block. Returns true when it was a dungeon core and the click is handled. */
    public boolean onUse(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() == DungeonContent.SPENT_CORE) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("nightsiege.dungeon.spent")));
            return true;
        }
        if (state.getBlock() != DungeonContent.CORE) {
            return false;
        }
        if (!config.get().dungeonsEnabled) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("nightsiege.dungeon.off")));
            return true;
        }
        String dim = SiegeManager.dimensionName(level);
        DungeonRecord record = null;
        for (DungeonRecord r : records()) {
            if (r.at(dim, pos.getX(), pos.getY(), pos.getZ())) {
                record = r;
                break;
            }
        }
        if (record == null) {
            // A core set by hand in creative: a test fight of a random kind.
            if (player.getAbilities().instabuild) {
                DungeonType type = config.get().dungeons.get(random.nextInt(config.get().dungeons.size()));
                startTest(player, type, type.tier().waves(), type.tier(), 0, true, type.theme(), pos.above());
            }
            return true;
        }
        if (record.beaten()) {
            return true;
        }
        if (fights.containsKey(key(record))) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("nightsiege.dungeon.running")));
            return true;
        }
        long now = level.getGameTime();
        if (now < record.retryAt) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(
                    "nightsiege.dungeon.retry", (record.retryAt - now + 1199) / 1200)));
            return true;
        }
        DungeonType type = DungeonType.byKey(config.get().dungeons, record.type);
        if (type == null) {
            return true;
        }
        Fight fight = new Fight();
        fight.id = key(record);
        fight.record = record;
        fight.type = type;
        fight.theme = type.theme();
        fight.level = level;
        fight.anchor = pos;
        fight.waves = type.tier().waves();
        fight.boss = true;
        clearHelpers(level, record, pos);
        begin(fight, type.tier(), -1);
        return true;
    }

    /** Creative test: a fight right where the player stands, with free settings. Counts for nothing. */
    public void startTest(ServerPlayer player, DungeonType type, int waves, DungeonTier tier, int progress,
                          boolean boss, DungeonTheme theme, BlockPos at) {
        Fight fight = new Fight();
        fight.id = "test:" + player.getUUID();
        Fight old = fights.remove(fight.id);
        if (old != null) {
            end(old, false, true);
        }
        fight.type = type;
        fight.theme = theme == null ? type.theme() : theme;
        fight.level = player.level();
        fight.anchor = at == null ? player.blockPosition() : at;
        fight.waves = Math.max(1, Math.min(9, waves));
        fight.boss = boss;
        fight.test = true;
        begin(fight, tier == null ? type.tier() : tier, Math.max(0, progress));
    }

    private void begin(Fight fight, DungeonTier tier, int forcedProgress) {
        ServerLevel level = fight.level;
        Vec3 center = Vec3.atCenterOf(fight.anchor);
        int spent = 0;
        for (ServerPlayer near : level.players()) {
            if (!near.isSpectator() && near.distanceToSqr(center) <= DungeonMath.FIGHT_RADIUS * DungeonMath.FIGHT_RADIUS) {
                fight.participants.add(near.getUUID());
                PlayerSkills skills = save.get().skillsOf(near.getUUID().toString());
                spent += Math.max(0, skills.earned - skills.points() - skills.donated);
            }
        }
        if (fight.participants.isEmpty()) {
            return;
        }
        int progress = forcedProgress >= 0 ? forcedProgress : spent / fight.participants.size();
        fight.mobLevel = DungeonMath.level(tier, progress);
        fight.waveSize = DungeonMath.mobsPerWave(tier, fight.participants.size());
        fight.startedAt = level.getGameTime();
        fight.nextWaveAt = fight.startedAt + 40;
        fight.wave = 0;
        fight.bar = new ServerBossEvent(UUID.randomUUID(), barTitle(fight),
                tier == DungeonTier.NETHER || tier == DungeonTier.HARD ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.NOTCHED_10);
        for (UUID id : fight.participants) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                fight.bar.addPlayer(p);
                p.sendSystemMessage(Component.translatable(fight.test ? "nightsiege.dungeon.test_start" : "nightsiege.dungeon.start",
                        typeName(fight.type.key), fight.waves, fight.mobLevel).withStyle(ChatFormatting.GOLD));
            }
        }
        level.playSound(null, fight.anchor, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 0.8f);
        fights.put(fight.id, fight);
    }

    private Component barTitle(Fight fight) {
        return Component.translatable("nightsiege.dungeon.bar", typeName(fight.type.key),
                Math.max(1, fight.wave), fight.waves);
    }

    // ------------------------------------------------------------------
    // running fights
    // ------------------------------------------------------------------

    private void tickFights(MinecraftServer server) {
        if (fights.isEmpty()) {
            return;
        }
        for (Fight fight : new ArrayList<>(fights.values())) {
            ServerLevel level = fight.level;
            long now = level.getGameTime();
            if (now % 10 != 0) {
                continue;
            }
            fight.mobs.removeIf(id -> {
                Entity e = level.getEntity(id);
                return e == null || !e.isAlive();
            });
            // Defenders who are gone, dead or too far away no longer hold the line.
            int standing = 0;
            Vec3 center = Vec3.atCenterOf(fight.anchor);
            for (UUID id : fight.participants) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && p.isAlive() && !fight.fallen.contains(id) && p.level() == level
                        && p.distanceToSqr(center) <= DungeonMath.LEAVE_RADIUS * DungeonMath.LEAVE_RADIUS) {
                    standing++;
                }
            }
            if (standing == 0 || now - fight.startedAt > DungeonMath.MAX_FIGHT_TICKS) {
                end(fight, false, false);
                continue;
            }
            if (fight.mobs.isEmpty()) {
                if (fight.wave >= fight.waves) {
                    end(fight, true, false);
                    continue;
                }
                if (fight.nextWaveAt == 0) {
                    fight.nextWaveAt = now + 60;
                }
                if (now >= fight.nextWaveAt) {
                    spawnWave(fight);
                    fight.nextWaveAt = 0;
                }
            } else {
                aim(fight, server);
                // Mobs that have been around for long glow, so stragglers are easy to find.
                if ((now / 10) % 30 == 0) {
                    for (UUID id : fight.mobs) {
                        if (level.getEntity(id) instanceof Mob mob) {
                            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.GLOWING, 100, 0));
                        }
                    }
                }
            }
            if (fight.bar != null) {
                fight.bar.setName(barTitle(fight));
                float progress = fight.waveSize <= 0 ? 0f : Math.min(1f, fight.mobs.size() / (float) fight.waveSize);
                fight.bar.setProgress(fight.mobs.isEmpty() ? 0f : progress);
            }
        }
    }

    private void spawnWave(Fight fight) {
        fight.wave++;
        ServerLevel level = fight.level;
        List<String> pool = new ArrayList<>();
        for (String id : fight.theme.mobs()) {
            if (!MobFactory.isUnavailable(id)) {
                pool.add(id);
            }
        }
        if (pool.isEmpty()) {
            pool.add("minecraft:zombie");
        }
        int count = fight.waveSize;
        for (int i = 0; i < count; i++) {
            BlockPos spot = spawnSpot(level, fight.anchor);
            if (spot == null) {
                continue;
            }
            Mob mob = MobFactory.spawnAt(level, spot, pool.get(random.nextInt(pool.size())), fight.mobLevel,
                    config.get(), random, SiegeEvent.NONE, false);
            track(fight, mob);
        }
        boolean last = fight.wave >= fight.waves;
        if (last && fight.boss) {
            String bossId = null;
            for (String candidate : fight.theme.bosses()) {
                if (!MobFactory.isUnavailable(candidate)) {
                    bossId = candidate;
                    break;
                }
            }
            BlockPos spot = spawnSpot(level, fight.anchor);
            if (bossId != null && spot != null) {
                Mob boss = MobFactory.spawnAt(level, spot, bossId, fight.mobLevel, config.get(), random, SiegeEvent.NONE, true);
                if (boss != null) {
                    boss.setCustomName(Component.translatable("nightsiege.dungeon.boss." + fight.theme.key()));
                    boss.addTag(TAG + "_boss");
                    level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, boss.getX(), boss.getY(1.0), boss.getZ(),
                            40, 0.5, 0.8, 0.5, 0.3);
                    level.playSound(null, spot, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7f, 1.3f);
                }
                track(fight, boss);
            }
        }
        level.playSound(null, fight.anchor, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0f, 0.8f);
        for (UUID id : fight.participants) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(
                        last && fight.boss ? "nightsiege.dungeon.wave_boss" : "nightsiege.dungeon.wave",
                        fight.wave, fight.waves)));
            }
        }
    }

    private void track(Fight fight, Mob mob) {
        if (mob == null) {
            return;
        }
        mob.addTag(TAG);
        fight.mobs.add(mob.getUUID());
        for (Entity rider : mob.getPassengers()) {
            rider.addTag(TAG);
            fight.mobs.add(rider.getUUID());
        }
    }

    /** A free spot 6 to 14 blocks from the core, at about its height. */
    private BlockPos spawnSpot(ServerLevel level, BlockPos anchor) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 6 + random.nextDouble() * 8;
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * distance);
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos at = new BlockPos(x, anchor.getY() + dy, z);
                if (level.isLoaded(at) && open(level, at) && open(level, at.above()) && solid(level, at.below())) {
                    return at;
                }
            }
        }
        return null;
    }

    private void aim(Fight fight, MinecraftServer server) {
        for (UUID id : fight.mobs) {
            if (!(fight.level.getEntity(id) instanceof Mob mob) || mob.getTarget() != null) {
                continue;
            }
            ServerPlayer best = null;
            double bestDistance = Double.MAX_VALUE;
            for (UUID pid : fight.participants) {
                ServerPlayer p = server.getPlayerList().getPlayer(pid);
                if (p == null || p.level() != fight.level || !p.isAlive() || p.isCreative() || p.isSpectator()) {
                    continue;
                }
                double d = mob.distanceToSqr(p);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = p;
                }
            }
            if (best != null) {
                mob.setTarget(best);
            }
        }
    }

    /** A defender died: they no longer hold the line in their fight. */
    public void onPlayerDeath(ServerPlayer player) {
        for (Fight fight : fights.values()) {
            if (fight.participants.contains(player.getUUID())) {
                fight.fallen.add(player.getUUID());
            }
        }
    }

    private void end(Fight fight, boolean won, boolean silent) {
        fights.remove(fight.id);
        ServerLevel level = fight.level;
        for (UUID id : fight.mobs) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        if (fight.bar != null) {
            fight.bar.removeAllPlayers();
        }
        if (silent) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (!won) {
            if (fight.record != null) {
                fight.record.retryAt = level.getGameTime() + DungeonMath.RETRY_TICKS;
                dirty = true;
            }
            message(server, fight, Component.translatable(fight.test ? "nightsiege.dungeon.test_lost" : "nightsiege.dungeon.lost")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        level.playSound(null, fight.anchor, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, fight.anchor.getX() + 0.5, fight.anchor.getY() + 1.0,
                fight.anchor.getZ() + 0.5, 80, 0.8, 1.2, 0.8, 0.4);
        if (fight.test) {
            message(server, fight, Component.translatable("nightsiege.dungeon.test_won").withStyle(ChatFormatting.GREEN));
            return;
        }
        if (fight.record != null) {
            fight.record.state = DungeonRecord.BEATEN;
            BlockPos core = new BlockPos(fight.record.x, fight.record.y, fight.record.z);
            if (level.isLoaded(core)) {
                level.setBlockAndUpdate(core, DungeonContent.SPENT_CORE.defaultBlockState());
            }
            dirty = true;
        }
        NightFragment kind = fight.type.fragment();
        for (UUID id : fight.participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                continue;
            }
            give(p, new ItemStack(DungeonContent.NIGHT_FRAGMENTS.get(kind)));
            give(p, new ItemStack(DungeonContent.MAP));
            p.sendSystemMessage(Component.translatable("nightsiege.dungeon.won", typeName(fight.type.key),
                    Component.translatable("item.nightsiege.night_fragment_" + kind.id())).withStyle(ChatFormatting.GREEN));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack);
    }

    private static void message(MinecraftServer server, Fight fight, Component text) {
        for (UUID id : fight.participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(text);
            }
        }
    }

    // ------------------------------------------------------------------
    // maps
    // ------------------------------------------------------------------

    /** Structures of every dungeon kind that exists in this game, for map searches. */
    private List<Holder<Structure>> holders(ServerLevel level, DungeonType only) {
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> out = new ArrayList<>();
        for (Identifier id : registry.keySet()) {
            DungeonType type = DungeonType.find(config.get().dungeons, id.toString());
            if (type != null && (only == null || only.key.equals(type.key))) {
                registry.get(id).ifPresent(out::add);
            }
        }
        return out;
    }

    /** Nearest dungeon structure; with {@code fresh} only in land nobody has seen yet. */
    private Pair<BlockPos, Holder<Structure>> nearest(ServerLevel level, BlockPos from, DungeonType only, boolean fresh) {
        List<Holder<Structure>> list = holders(level, only);
        if (list.isEmpty()) {
            return null;
        }
        return level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(list), from, 100, fresh);
    }

    /** A blank dungeon map was used: it becomes a map to the nearest dungeon in unexplored land. */
    public void useMap(ServerLevel level, ServerPlayer player, InteractionHand hand) {
        if (!config.get().dungeonsEnabled) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("nightsiege.dungeon.off")));
            return;
        }
        Pair<BlockPos, Holder<Structure>> found = nearest(level, player.blockPosition(), null, true);
        if (found == null) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("nightsiege.dungeon.map_none")));
            return;
        }
        BlockPos target = found.getFirst();
        ChunkPos chunk = ChunkPos.containing(target);
        String mark = SiegeManager.dimensionName(level) + "|" + chunk.x() + "|" + chunk.z();
        if (!forced().contains(mark)) {
            forced().add(mark);
            dirty = true;
        }
        String typeKey = found.getSecond().unwrapKey()
                .map(k -> DungeonType.find(config.get().dungeons, k.identifier().toString()))
                .map(t -> t.key).orElse("");
        ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, target, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("nightsiege.dungeon.map_name", typeName(typeKey)));
        ItemStack held = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        if (held.isEmpty()) {
            player.setItemInHand(hand, map);
        } else {
            give(player, map);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------
    // creative tool and commands
    // ------------------------------------------------------------------

    /** Sends the list of dungeon kinds to the player's test tool window. */
    public void openTool(ServerPlayer player) {
        if (!player.getAbilities().instabuild) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("nightsiege.msg.creative_only")));
            return;
        }
        if (!ServerPlayNetworking.canSend(player, CoreNetworking.DungeonUiPayload.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(player, new CoreNetworking.DungeonUiPayload(Json.toJson(toolState(player.level()))));
    }

    public DungeonToolState toolState(ServerLevel level) {
        DungeonToolState state = new DungeonToolState();
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (DungeonType type : config.get().dungeons) {
            DungeonToolState.Entry entry = new DungeonToolState.Entry();
            entry.key = type.key;
            entry.tier = type.tier().name();
            entry.theme = type.theme().name();
            entry.mod = type.namespace();
            entry.fragment = type.fragment().id();
            entry.coreSet = type.core != null;
            for (Identifier id : registry.keySet()) {
                if (type.matches(id.toString())) {
                    entry.installed = true;
                    break;
                }
            }
            for (DungeonRecord record : records()) {
                if (record.type.equals(type.key)) {
                    entry.found++;
                    if (record.beaten()) {
                        entry.beaten++;
                    }
                }
            }
            state.entries.add(entry);
        }
        return state;
    }

    /** Teleports to the nearest structure of this kind in the player's dimension and makes sure it is a dungeon. */
    public Component travel(ServerPlayer player, DungeonType type) {
        ServerLevel level = player.level();
        Pair<BlockPos, Holder<Structure>> found = nearest(level, player.blockPosition(), type, false);
        if (found == null) {
            return Component.translatable("nightsiege.dungeon.tp_none", typeName(type.key));
        }
        BlockPos target = found.getFirst();
        ChunkPos chunk = ChunkPos.containing(target);
        String mark = SiegeManager.dimensionName(level) + "|" + chunk.x() + "|" + chunk.z();
        if (!forced().contains(mark)) {
            forced().add(mark);
            dirty = true;
        }
        level.getChunk(chunk.x(), chunk.z());
        BlockPos land = target;
        BoundingBox box = null;
        for (StructureStart start : level.structureManager().startsForStructure(chunk, s -> true)) {
            if (start.isValid()) {
                box = start.getBoundingBox();
                break;
            }
        }
        if (box != null) {
            BlockPos center = box.getCenter();
            level.getChunk(center.getX() >> 4, center.getZ() >> 4);
            land = standingSpot(level, center.getX(), center.getZ(), box.minY(), box.maxY() + 8);
        }
        if (land == null || box == null) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
            land = new BlockPos(target.getX(), y + 1, target.getZ());
        }
        player.teleportTo(land.getX() + 0.5, land.getY(), land.getZ() + 0.5);
        return Component.translatable("nightsiege.dungeon.tp_done", typeName(type.key), land.getX(), land.getY(), land.getZ());
    }

    /** Highest spot with two free blocks over solid ground in a column, scanning down from the top. */
    private static BlockPos standingSpot(ServerLevel level, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            BlockPos at = new BlockPos(x, y, z);
            if (open(level, at) && open(level, at.above()) && solid(level, at.below())) {
                return at;
            }
        }
        return null;
    }

    /** Makes every beaten (or found) dungeon of a kind playable again. A null kind resets all. */
    public int reset(MinecraftServer server, DungeonType type) {
        int count = 0;
        for (DungeonRecord record : records()) {
            if (type != null && !record.type.equals(type.key)) {
                continue;
            }
            record.state = DungeonRecord.DORMANT;
            record.retryAt = 0;
            ServerLevel level = SiegeManager.levelByName(server, record.dimension);
            BlockPos core = new BlockPos(record.x, record.y, record.z);
            if (level != null && level.isLoaded(core)) {
                level.setBlockAndUpdate(core, DungeonContent.CORE.defaultBlockState());
            }
            count++;
        }
        dirty = true;
        return count;
    }

    // ------------------------------------------------------------------

    public static Component typeName(String key) {
        return Component.translatable("nightsiege.dungeon.type." + key);
    }

    private static String key(DungeonRecord record) {
        return record.dimension + "|" + record.chunkX + "|" + record.chunkZ;
    }

    private List<DungeonRecord> records() {
        if (save.get().dungeons == null) {
            save.get().dungeons = new ArrayList<>();
        }
        return save.get().dungeons;
    }

    private List<String> forced() {
        if (save.get().forcedDungeons == null) {
            save.get().forcedDungeons = new ArrayList<>();
        }
        return save.get().forcedDungeons;
    }

    /** A button in the creative tool window. */
    public void onToolAction(ServerPlayer player, String argument) {
        if (!player.getAbilities().instabuild) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.creative_only").withStyle(ChatFormatting.RED));
            return;
        }
        String[] parts = argument.split(":");
        String verb = parts[0];
        DungeonType type = parts.length > 1 ? DungeonType.byKey(config.get().dungeons, parts[1]) : null;
        switch (verb) {
            case "tp" -> {
                if (type != null) {
                    player.sendSystemMessage(travel(player, type));
                }
            }
            case "core" -> {
                if (type != null) {
                    player.sendSystemMessage(toCore(player, type));
                }
            }
            case "setcore" -> moveCoreHere(player);
            case "test" -> {
                if (type == null) {
                    return;
                }
                int waves = parts.length > 2 ? parse(parts[2], type.tier().waves()) : type.tier().waves();
                DungeonTier tier = parts.length > 3 ? DungeonTier.byName(parts[3]) : null;
                int progress = parts.length > 4 ? parse(parts[4], 0) : 0;
                boolean boss = parts.length <= 5 || !"0".equals(parts[5]);
                DungeonTheme theme = parts.length > 6 ? DungeonTheme.byName(parts[6]) : null;
                startTest(player, type, waves, tier == null ? type.tier() : tier, progress, boss,
                        theme == null ? type.theme() : theme, null);
            }
            case "reset" -> {
                int count = reset(player.level().getServer(), type);
                player.sendSystemMessage(Component.translatable("nightsiege.dungeon.reset_done", count)
                        .withStyle(ChatFormatting.YELLOW));
                openTool(player);
            }
            case "stop" -> {
                Fight fight = fights.remove("test:" + player.getUUID());
                if (fight != null) {
                    end(fight, false, true);
                }
            }
            default -> openTool(player);
        }
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Mobs of a fight that no longer runs (the server was restarted) are cleared away when they load. */
    public void onEntityLoad(Entity entity) {
        if (!entity.entityTags().contains(TAG) || entity.entityTags().contains(GUARD_TAG)) {
            return;
        }
        for (Fight fight : fights.values()) {
            if (fight.mobs.contains(entity.getUUID())) {
                return;
            }
        }
        entity.discard();
    }

    /** All running fights end without a winner, e.g. when the server stops. */
    public void abortAll() {
        for (Fight fight : new ArrayList<>(fights.values())) {
            end(fight, false, true);
        }
    }
}
