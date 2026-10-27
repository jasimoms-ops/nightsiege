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
 * core, guarded by vindicators: 8 and 3 more for every further signed-up player. Nothing is
 * said in the chat; only the smoke of its fires shows it. Whoever of the signed-up players
 * steps inside the palisade starts the fight: the war horn, the class cards as before a
 * night, the kit (the own things go into the kit store) and 10 seconds of grace. The own
 * things come back when the workshop falls, when the player falls or leaves the palisade.</p>
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
    /** Until when a fighter is spared (game time). */
    private final Map<UUID, Long> grace = new HashMap<>();
    private ServerBossEvent bar;
    /** What happened in a damage event, handled on the next tick. */
    private Outcome outcome;
    private UUID outcomeBy;
    /** The camp burns down at this time after its workshop fell (game time), or -1. */
    private long burnUntil = -1L;

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
        if (now % 20 == 0) {
            roll(server);
        }
        SiegeSave.CampRecord camp = camp();
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
            checkEntries(level, camp, center, now);
        }
        if (now % 20 == 0) {
            tickFighters(server, level, camp, center, now);
        }
    }

    /** On the day before a siege day: one roll whether a camp comes. */
    private void roll(MinecraftServer server) {
        SiegeSave save = m.save();
        SiegeConfig cfg = m.config();
        if (!cfg.machinesEnabled || cfg.campChance <= 0 || m.isActive() || m.testPending() || m.mainCore() == null
                || save.camp != null) {
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
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = cfg.campMinDistance + random.nextDouble() * Math.max(0, cfg.campMaxDistance - cfg.campMinDistance);
            int x = core.x + (int) Math.round(Math.cos(angle) * distance);
            int z = core.z + (int) Math.round(Math.sin(angle) * distance);
            Integer floor = site(level, x, z, true);
            if (floor == null) {
                continue;
            }
            int facing = CampLayout.facingTowards(core.x - x, core.z - z);
            build(level, camp, new BlockPos(x, floor, z), facing);
            m.persist();
            return;
        }
    }

    /**
     * The height one walks on for a camp in the middle of (x, z), or null if it cannot stand
     * there: ground not loaded, water, too steep, built on, players close by.
     */
    Integer site(ServerLevel level, int x, int z, boolean awayFromPlayers) {
        int r = CampLayout.GROUND + 1;
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return null;
                }
            }
        }
        if (awayFromPlayers) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(x + 0.5, p.getY(), z + 0.5) < 40.0 * 40.0) {
                    return null;   // nobody sees it pop up
                }
            }
        }
        List<Integer> heights = new ArrayList<>();
        for (int dx = -CampLayout.GROUND; dx <= CampLayout.GROUND; dx += 2) {
            for (int dz = -CampLayout.GROUND; dz <= CampLayout.GROUND; dz += 2) {
                if (dx * dx + dz * dz > CampLayout.GROUND * CampLayout.GROUND) {
                    continue;
                }
                int g = ground(level, x + dx, z + dz);
                if (g == Integer.MIN_VALUE) {
                    return null;   // water, lava or something built
                }
                heights.add(g);
            }
        }
        if (heights.isEmpty()) {
            return null;
        }
        Collections.sort(heights);
        int low = heights.get(0);
        int high = heights.get(heights.size() - 1);
        if (high - low > 7) {
            return null;
        }
        int floor = heights.get(heights.size() / 2) + 1;
        // Nothing but nature in the space the camp takes.
        for (int dx = -CampLayout.GROUND; dx <= CampLayout.GROUND; dx += 3) {
            for (int dz = -CampLayout.GROUND; dz <= CampLayout.GROUND; dz += 3) {
                if (dx * dx + dz * dz > CampLayout.GROUND * CampLayout.GROUND) {
                    continue;
                }
                for (int y = floor - 2; y <= floor + CampLayout.CLEAR; y++) {
                    if (!natural(level.getBlockState(new BlockPos(x + dx, y, z + dz)))) {
                        return null;
                    }
                }
            }
        }
        return floor;
    }

    /** The top ground block of a column (trees and plants skipped), or MIN_VALUE for water or a build. */
    private static int ground(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int y = top; y > top - 40 && y > level.getMinY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (state.isAir() || state.canBeReplaced() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
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

    /** Ground, rock, trees and plants: what a camp may be set onto (no player's building). */
    static boolean natural(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.canBeReplaced() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SNOW)
                || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.ICE) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.COAL_ORE)
                || state.is(Blocks.IRON_ORE) || state.is(Blocks.COPPER_ORE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.MELON) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.CALCITE) || state.is(Blocks.SMOOTH_BASALT)
                || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS) || state.is(Blocks.SWEET_BERRY_BUSH);
    }

    /** Levels the ground and sets the camp's blocks; the guards come when someone is near. */
    void build(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, int facing) {
        camp.x = center.getX();
        camp.y = center.getY();
        camp.z = center.getZ();
        camp.facing = facing;
        camp.dimension = SiegeManager.dimensionName(level);
        int floorY = center.getY();
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
            for (int y = floorY - 2; y > floorY - 9; y--) {
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
        NightSiegeMod.LOGGER.info("[Night Siege] Heerlager steht bei {} {} {} (Tor nach {}, Belagerung an Tag {})",
                center.getX(), center.getY(), center.getZ(), facing, camp.day);
    }

    /** The guards and the workbench's hit box (once the camp's ground is entity-loaded). */
    void man(ServerLevel level, SiegeSave.CampRecord camp) {
        SiegeConfig cfg = m.config();
        BlockPos center = new BlockPos(camp.x, camp.y, camp.z);
        int signedUp = m.save().registered == null ? 1 : m.save().registered.size();
        int count = cfg.campGuards + cfg.campGuardsPerPlayer * Math.max(0, signedUp - 1);
        List<BlockPos> spots = CampLayout.guardSpots(center, camp.facing);
        Collections.shuffle(spots, random);
        camp.guards.clear();
        for (int i = 0; i < count; i++) {
            BlockPos base = spots.get(i % spots.size());
            BlockPos spot = BossService.groundNear(level, base.getX() + 0.5 + random.nextInt(3) - 1, base.getY() + 1,
                    base.getZ() + 0.5 + random.nextInt(3) - 1);
            if (spot == null) {
                spot = BossService.groundNear(level, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
            }
            if (spot == null) {
                continue;
            }
            Mob guard = MobFactory.spawnAt(level, spot, "minecraft:vindicator", m.currentLevel(), cfg, random, SiegeEvent.NONE, false);
            if (guard == null) {
                continue;
            }
            guard.removeTag(MobFactory.TAG);   // not a monster of the night: it guards the camp
            guard.addTag(TAG);
            guard.addTag(TAG + "_" + camp.id);
            guard.setHomeTo(center, 13);
            guard.setPersistenceRequired();
            camp.guards.add(guard.getUUID().toString());
        }
        Zombie bench = workbench(level, camp);
        camp.workshop = bench == null ? "" : bench.getUUID().toString();
        camp.manned = true;
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Heerlager bemannt: {} Wachen", camp.guards.size());
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

    // ------------------------------------------------------------------ the fight

    private void checkEntries(ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, long now) {
        if (m.isActive() || m.testPending()) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (fighters.contains(p.getUUID()) || !m.isRegistered(p) || p.isSpectator() || p.getAbilities().instabuild
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
        boolean first = fighters.isEmpty();
        UUID id = player.getUUID();
        fighters.add(id);
        int seconds = m.config().campGraceSeconds;
        grace.put(id, now + seconds * 20L);
        if (first) {
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
        m.campClassPick(player, seconds);
        SkillService.apply(player, m.save().skillsOf(id.toString()), m.skillTree());
        showBar(level, camp, player);
        NightSiegeMod.LOGGER.info("[Night Siege] {} greift das Heerlager an", player.getScoreboardName());
    }

    private void tickFighters(MinecraftServer server, ServerLevel level, SiegeSave.CampRecord camp, BlockPos center, long now) {
        for (UUID id : new ArrayList<>(fighters)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                // Logged out: the own things come back when they log in again.
                fighters.remove(id);
                grace.remove(id);
                continue;
            }
            double dx = p.getX() - (center.getX() + 0.5);
            double dz = p.getZ() - (center.getZ() + 0.5);
            double limit = CampLayout.PALISADE + 2.5;
            if (p.level() != level || dx * dx + dz * dz > limit * limit || Math.abs(p.getY() - center.getY()) > 14) {
                leave(p, "nightsiege.camp.left");
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
        }
    }

    private void showBar(ServerLevel level, SiegeSave.CampRecord camp, ServerPlayer player) {
        if (bar == null) {
            bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("nightsiege.camp.workbench"),
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
        for (UUID id : new ArrayList<>(fighters)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                leave(p, null);
            }
        }
        fighters.clear();
        grace.clear();
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
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
            return !(attacker instanceof Player p) || fighters.contains(p.getUUID());
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
            if (outcome == null) {
                outcome = Outcome.WON;
                outcomeBy = player.getUUID();
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

    private void handleOutcome(MinecraftServer server, Outcome o, UUID by) {
        SiegeSave.CampRecord camp = camp();
        if (camp == null) {
            return;
        }
        ServerPlayer player = by == null ? null : server.getPlayerList().getPlayer(by);
        ServerLevel level = SiegeManager.levelByName(server, camp.dimension);
        if (o == Outcome.WON) {
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
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(center).inflate(28, 16, 28), SiegeCamp::isCampMob)) {
            e.discard();
        }
        Map<BlockPos, BlockState> blocks = CampLayout.blocks(center, camp.facing, camp.id);
        List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(blocks.entrySet());
        list.sort((a, b) -> Integer.compare(b.getKey().getY(), a.getKey().getY()));   // from the top down
        int taken = 0;
        for (Map.Entry<BlockPos, BlockState> e : list) {
            BlockState now = level.getBlockState(e.getKey());
            if (!now.is(e.getValue().getBlock())) {
                continue;   // someone changed it: leave it
            }
            BlockEntity be = level.getBlockEntity(e.getKey());
            if (be instanceof Container container) {
                container.clearContent();
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

    /** True for a block of the standing camp (players cannot break it). */
    public boolean protects(net.minecraft.world.level.Level level, BlockPos pos) {
        SiegeSave.CampRecord camp = camp();
        if (camp == null || !camp.built || !(level instanceof ServerLevel server)
                || !SiegeManager.dimensionName(server).equals(camp.dimension)) {
            return false;
        }
        int dx = pos.getX() - camp.x;
        int dz = pos.getZ() - camp.z;
        int dy = pos.getY() - camp.y;
        return dy >= -1 && dy <= 7 && dx * dx + dz * dz <= (CampLayout.PALISADE + 1.5) * (CampLayout.PALISADE + 1.5);
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
        for (int attempt = 0; attempt < 80; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = cfg.campMinDistance + random.nextDouble() * Math.max(0, cfg.campMaxDistance - cfg.campMinDistance);
            int x = core.x + (int) Math.round(Math.cos(angle) * distance);
            int z = core.z + (int) Math.round(Math.sin(angle) * distance);
            Integer floor = site(level, x, z, false);
            if (floor != null) {
                build(level, camp, new BlockPos(x, floor, z), CampLayout.facingTowards(core.x - x, core.z - z));
                m.persist();
                return new BlockPos(x, floor, z);
            }
        }
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

    /** Tests: the player steps into the fight now (as when walking through the gate). */
    public void testJoin(ServerPlayer player) {
        SiegeSave.CampRecord camp = camp();
        if (camp != null && camp.built && player.level() instanceof ServerLevel level) {
            join(level, camp, new BlockPos(camp.x, camp.y, camp.z), player, level.getGameTime());
        }
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
