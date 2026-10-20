package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.AnchorPicker;
import com.mki.nightsiege.core.EventPicker;
import com.mki.nightsiege.core.MobPool;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SiegeScheduler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Runs the whole mod: scheduling, the active siege, and persistence. */
public final class SiegeManager {

    private static final SiegeManager INSTANCE = new SiegeManager();
    private static final String CONFIG_FILE = "nightsiege.json";
    private static final String SAVE_FILE = "nightsiege-state.json";
    /** The digger runs in blocks of this many ticks instead of every tick. */
    private static final int DIG_INTERVAL = 10;

    public static SiegeManager get() {
        return INSTANCE;
    }

    private SiegeConfig config = new SiegeConfig();
    private SiegeSave save = new SiegeSave();
    private final SiegeScheduler scheduler = new SiegeScheduler();
    private final SleepWatch sleepWatch = new SleepWatch();
    private final Digger digger = new Digger();
    private final Random random = new Random();
    private MobPool pool = new MobPool(SiegeConfig.defaultMobs());
    private final Set<String> unavailableMobs = new HashSet<>();

    // --- state of the running siege ---
    private boolean active;
    private ServerLevel world;
    private BlockPos anchor;
    private int siegeLevel;
    private int ticksLeft;
    private int totalTicks;
    private int batchCooldown;
    private int digAccumulator;
    /** Updated every tick so the level can be shown without a server reference. */
    private long lastKnownDay;
    private ServerBossEvent bossBar;
    private SiegeEvent currentEvent = SiegeEvent.NONE;
    /** During a theme night every mob is of this type. */
    private String themeMobId;
    /** The champion of an elite night, while it lives. */
    private UUID eliteId;
    private boolean elitePenaltyApplied;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> fallen = new HashSet<>();
    private final Set<UUID> liveMobs = new HashSet<>();

    private SiegeManager() {
    }

    public SiegeConfig config() {
        return config;
    }

    public SiegeSave save() {
        return save;
    }

    public boolean isActive() {
        return active;
    }

    public int currentLevel() {
        return active ? siegeLevel : SiegeMath.level(save.siegesWon, lastKnownDay, config);
    }

    public int secondsLeft() {
        return active ? ticksLeft / 20 : 0;
    }

    // ------------------------------------------------------------------
    // setup
    // ------------------------------------------------------------------

    public void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> persist());
        ServerTickEvents.END_SERVER_TICK.register(server -> onEndTick(server));
        // Object parameters keep these handlers compiling even if the event
        // signatures shift between Fabric API versions.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onDeath(entity));
        PlayerBlockBreakEvents.AFTER.register(
                (level, player, pos, state, blockEntity) -> onBlockBroken(level, pos));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> SiegeCommands.register(dispatcher));
    }

    private void onServerStarted(MinecraftServer server) {
        reload();
        NightSiegeMod.LOGGER.info("[Night Siege] {} Betten bekannt, {} Belagerungen bestanden",
                save.beds.size(), save.siegesWon);
    }

    /** Reads config and saved state from disk. Also used by the reload command. */
    public void reload() {
        Path configPath = Json.configFile(CONFIG_FILE);
        SiegeConfig loaded = Json.read(configPath, SiegeConfig.class);
        config = loaded == null ? new SiegeConfig() : loaded;
        config.sanitize();
        Json.write(configPath, config);

        SiegeSave loadedSave = Json.read(Json.configFile(SAVE_FILE), SiegeSave.class);
        save = loadedSave == null ? new SiegeSave() : loadedSave;
        if (save.beds == null) {
            save.beds = new ArrayList<>();
        }
        scheduler.setLastSiegeDay(save.lastSiegeDay < 0 ? Long.MIN_VALUE : save.lastSiegeDay);

        pool = new MobPool(config.mobs);
        unavailableMobs.clear();
    }

    public void persist() {
        Json.write(Json.configFile(SAVE_FILE), save);
    }

    // ------------------------------------------------------------------
    // main loop
    // ------------------------------------------------------------------

    private void onEndTick(MinecraftServer server) {
        sleepWatch.poll(server, this::onPlayerSlept);

        long gameTime = server.overworld().getGameTime();
        lastKnownDay = SiegeMath.dayOf(gameTime);
        if (active) {
            tickSiege(server);
            return;
        }
        SiegeScheduler.Decision decision = scheduler.evaluate(gameTime, false, config);
        if (decision == SiegeScheduler.Decision.WARN) {
            announceWarning(server);
        } else if (decision == SiegeScheduler.Decision.START) {
            tryStart(server);
        }
    }

    private void announceWarning(MinecraftServer server) {
        if (findTarget(server) == null) {
            return;
        }
        int level = SiegeMath.level(save.siegesWon, SiegeMath.dayOf(server.overworld().getGameTime()), config);
        broadcast(server, Component.translatable("nightsiege.msg.warning", level)
                .withStyle(ChatFormatting.GOLD));
    }

    private void tryStart(MinecraftServer server) {
        AnchorPicker.Result target = findTarget(server);
        if (target == null) {
            return;
        }
        ServerLevel targetWorld = levelByName(server, target.anchor().dimension());
        if (targetWorld == null) {
            return;
        }
        BlockPos pos = new BlockPos((int) target.anchor().x(), (int) target.anchor().y(), (int) target.anchor().z());
        start(server, targetWorld, pos);
    }

    /** Starts a siege at the given bed. Also used by the start command. */
    public boolean start(MinecraftServer server, ServerLevel targetWorld, BlockPos targetAnchor) {
        if (active) {
            return false;
        }
        List<ServerPlayer> nearby = playersNear(server, targetWorld, targetAnchor, config.participantRadius);
        if (nearby.isEmpty()) {
            return false;
        }
        world = targetWorld;
        anchor = targetAnchor;
        siegeLevel = SiegeMath.level(save.siegesWon, SiegeMath.dayOf(server.overworld().getGameTime()), config);
        totalTicks = SiegeMath.durationSeconds(siegeLevel, config) * 20;
        ticksLeft = totalTicks;
        batchCooldown = 0;
        digAccumulator = 0;
        participants.clear();
        fallen.clear();
        liveMobs.clear();
        digger.reset();
        for (ServerPlayer player : nearby) {
            participants.add(player.getUUID());
        }
        currentEvent = EventPicker.pick(siegeLevel, random, config);
        themeMobId = null;
        eliteId = null;
        elitePenaltyApplied = false;
        if (currentEvent == SiegeEvent.THEME_WAVE) {
            themeMobId = pickAvailableMob();
            if (themeMobId == null) {
                // Nothing usable to build a theme night from, so stay plain.
                currentEvent = SiegeEvent.NONE;
            }
        }

        // The event is never named. A blood moon only shows as a red bar.
        bossBar = new ServerBossEvent(UUID.randomUUID(),
                bossBarTitle(),
                currentEvent == SiegeEvent.BLOOD_MOON
                        ? BossEvent.BossBarColor.RED
                        : BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setProgress(1.0f);
        active = true;

        broadcast(server, Component.translatable("nightsiege.msg.start", siegeLevel)
                .withStyle(ChatFormatting.RED));
        if (currentEvent == SiegeEvent.ELITE) {
            spawnElite(server);
        }
        return true;
    }

    /** An entity id from the pool that is actually installed, or null. */
    private String pickAvailableMob() {
        for (int attempt = 0; attempt < 12; attempt++) {
            String candidate = pool.pick(siegeLevel, random, unavailableMobs);
            if (candidate == null) {
                return null;
            }
            if (MobFactory.isUnavailable(candidate)) {
                unavailableMobs.add(candidate);
                continue;
            }
            return candidate;
        }
        return null;
    }

    private void spawnElite(MinecraftServer server) {
        String entityId = pickAvailableMob();
        if (entityId == null) {
            return;
        }
        Mob champion = MobFactory.spawn(world, anchor, entityId, siegeLevel,
                config, random, currentEvent, true);
        if (champion != null) {
            eliteId = champion.getUUID();
            liveMobs.add(eliteId);
            aimAtDefender(server, champion);
        }
    }

    private Component bossBarTitle() {
        return Component.translatable("nightsiege.bar.title", siegeLevel, secondsLeft());
    }

    private void tickSiege(MinecraftServer server) {
        if (world == null || anchor == null) {
            finish(server, false);
            return;
        }
        pruneMobs();
        refreshBossBar(server);

        // Players who walk into the defended area join the defence.
        for (ServerPlayer player : playersNear(server, world, anchor, config.participantRadius)) {
            if (!fallen.contains(player.getUUID())) {
                participants.add(player.getUUID());
            }
        }

        int standing = 0;
        int fallenCount = 0;
        for (UUID id : participants) {
            if (fallen.contains(id)) {
                fallenCount++;
            } else if (server.getPlayerList().getPlayer(id) != null) {
                standing++;
            }
        }
        if (standing == 0) {
            if (fallenCount > 0) {
                // Every defender died: this is a real defeat.
                finish(server, false);
            } else {
                // Everyone logged out or crashed. Not a defeat, so no punishment.
                abort(server);
            }
            return;
        }

        if (batchCooldown <= 0) {
            spawnBatch(server);
            batchCooldown = config.batchIntervalSeconds * 20;
        } else {
            batchCooldown--;
        }

        digAccumulator++;
        if (digAccumulator >= DIG_INTERVAL) {
            digger.tick(world, new ArrayList<>(liveMobs), anchor, config, digAccumulator);
            digAccumulator = 0;
        }

        ticksLeft--;
        if (ticksLeft <= 0) {
            finish(server, true);
        }
    }

    private void refreshBossBar(MinecraftServer server) {
        if (bossBar == null) {
            return;
        }
        bossBar.setProgress(totalTicks <= 0 ? 0.0f : Math.max(0.0f, (float) ticksLeft / totalTicks));
        if (ticksLeft % 20 == 0) {
            bossBar.setName(bossBarTitle());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                bossBar.addPlayer(player);
            }
        }
    }

    private void spawnBatch(MinecraftServer server) {
        int wanted = (int) Math.round(SiegeMath.mobsPerBatch(siegeLevel, participants.size(), config)
                * currentEvent.mobCountMultiplier(config));
        int room = config.maxLiveMobs - liveMobs.size();
        int count = Math.min(wanted, Math.max(0, room));
        for (int i = 0; i < count; i++) {
            String entityId = themeMobId != null
                    ? themeMobId
                    : pool.pick(siegeLevel, random, unavailableMobs);
            if (entityId == null) {
                return;
            }
            if (MobFactory.isUnavailable(entityId)) {
                // Mod not installed: remember it and try something else next time.
                if (unavailableMobs.add(entityId)) {
                    NightSiegeMod.LOGGER.warn("[Night Siege] Mob {} nicht gefunden, wird uebersprungen", entityId);
                }
                continue;
            }
            Mob mob = MobFactory.spawn(world, anchor, entityId, siegeLevel,
                    config, random, currentEvent, false);
            if (mob != null) {
                liveMobs.add(mob.getUUID());
                aimAtDefender(server, mob);
            }
        }
    }

    /** Sends the mob after the closest living defender. */
    private void aimAtDefender(MinecraftServer server, Mob mob) {
        ServerPlayer closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (UUID id : participants) {
            if (fallen.contains(id)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.level() != world) {
                continue;
            }
            double distance = mob.distanceToSqr(player.getX(), player.getY(), player.getZ());
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = player;
            }
        }
        if (closest != null) {
            mob.setTarget(closest);
        }
    }

    private void pruneMobs() {
        Iterator<UUID> it = liveMobs.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity entity = world.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                digger.forget(id);
                it.remove();
            }
        }
    }

    /** Ends the siege with no winner and no punishment. */
    public void abort(MinecraftServer server) {
        if (!active) {
            return;
        }
        active = false;
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        clearRemainingMobs();
        digger.reset();
        broadcast(server, Component.translatable("nightsiege.msg.aborted").withStyle(ChatFormatting.GRAY));
        world = null;
        anchor = null;
        currentEvent = SiegeEvent.NONE;
        themeMobId = null;
        eliteId = null;
        elitePenaltyApplied = false;
        participants.clear();
        fallen.clear();
        liveMobs.clear();
    }

    // ------------------------------------------------------------------
    // ending
    // ------------------------------------------------------------------

    public void finish(MinecraftServer server, boolean won) {
        if (!active) {
            return;
        }
        active = false;
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        clearRemainingMobs();
        digger.reset();

        long gameTime = server.overworld().getGameTime();
        scheduler.markSiegeFinished(gameTime);
        save.lastSiegeDay = SiegeMath.dayOf(gameTime);

        if (won) {
            save.siegesWon++;
            int next = SiegeMath.level(save.siegesWon, save.lastSiegeDay, config);
            broadcast(server, Component.translatable("nightsiege.msg.won", next)
                    .withStyle(ChatFormatting.GREEN));
        } else {
            save.siegesLost++;
            int destroyed = 0;
            if (world != null && anchor != null) {
                destroyed = Demolition.blast(world, anchor, config);
                save.removeBed(dimensionName(world), anchor.getX(), anchor.getY(), anchor.getZ());
            }
            broadcast(server, Component.translatable("nightsiege.msg.lost", destroyed)
                    .withStyle(ChatFormatting.DARK_RED));
        }
        persist();

        world = null;
        anchor = null;
        currentEvent = SiegeEvent.NONE;
        themeMobId = null;
        eliteId = null;
        elitePenaltyApplied = false;
        participants.clear();
        fallen.clear();
        liveMobs.clear();
    }

    private void clearRemainingMobs() {
        if (world == null) {
            return;
        }
        for (UUID id : liveMobs) {
            Entity entity = world.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        liveMobs.clear();
    }

    // ------------------------------------------------------------------
    // event handlers
    // ------------------------------------------------------------------

    private void onPlayerSlept(ServerPlayer player, BlockPos bedPos) {
        ServerLevel playerWorld = player.level();
        String dimension = dimensionName(playerWorld);
        BedRecord bed = save.findBed(dimension, bedPos.getX(), bedPos.getY(), bedPos.getZ());
        if (bed == null) {
            bed = new BedRecord(dimension, bedPos.getX(), bedPos.getY(), bedPos.getZ());
            save.beds.add(bed);
        }
        if (bed.isRegistered(config.sleepsToRegisterBase)) {
            return;
        }
        bed.sleeps++;
        if (bed.isRegistered(config.sleepsToRegisterBase)) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.base_registered")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.sleep_progress",
                    bed.sleeps, config.sleepsToRegisterBase).withStyle(ChatFormatting.GRAY));
        }
        persist();
    }

    /** Takes Object so the handler keeps compiling across Fabric API versions. */
    private void onDeath(Object entity) {
        if (active && entity instanceof Mob mob && mob.getUUID().equals(eliteId)) {
            onEliteKilled(mob);
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        sleepWatch.forget(id);
        if (!active || !participants.contains(id)) {
            return;
        }
        fallen.add(id);
        MinecraftServer server = player.level().getServer();
        broadcast(server, Component.translatable("nightsiege.msg.defender_down",
                player.getScoreboardName(), countStanding()).withStyle(ChatFormatting.RED));
    }

    /** The wave loses its edge once the champion is down. */
    private void onEliteKilled(Mob champion) {
        if (elitePenaltyApplied) {
            return;
        }
        elitePenaltyApplied = true;
        eliteId = null;
        double keep = 1.0 - config.eliteDeathDamagePenalty;
        int weakened = 0;
        for (UUID id : liveMobs) {
            Entity other = world == null ? null : world.getEntity(id);
            if (!(other instanceof Mob otherMob) || !otherMob.isAlive()) {
                continue;
            }
            AttributeInstance damage = otherMob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) {
                damage.setBaseValue(damage.getBaseValue() * keep);
                weakened++;
            }
        }
        MinecraftServer server = champion.level().getServer();
        broadcast(server, Component.translatable("nightsiege.msg.elite_down", weakened)
                .withStyle(ChatFormatting.GREEN));
    }

    private int countStanding() {
        int standing = 0;
        for (UUID id : participants) {
            if (!fallen.contains(id)) {
                standing++;
            }
        }
        return standing;
    }

    /** Takes Object so the handler keeps compiling across Fabric API versions. */
    private void onBlockBroken(Object levelObject, BlockPos pos) {
        if (!(levelObject instanceof ServerLevel brokenWorld)) {
            return;
        }
        String dimension = dimensionName(brokenWorld);

        if (active && world == brokenWorld && anchor != null && isSameBed(anchor, pos)) {
            // Breaking the anchor bed mid-siege would be a free escape, so it loses.
            MinecraftServer server = brokenWorld.getServer();
            broadcast(server, Component.translatable("nightsiege.msg.anchor_broken")
                    .withStyle(ChatFormatting.DARK_RED));
            finish(server, false);
            return;
        }
        if (save.findBed(dimension, pos.getX(), pos.getY(), pos.getZ()) != null) {
            save.removeBed(dimension, pos.getX(), pos.getY(), pos.getZ());
            persist();
        }
    }

    /**
     * A bed covers two blocks and the game may report either half, so positions
     * one block apart are treated as the same bed.
     */
    private boolean isSameBed(BlockPos a, BlockPos b) {
        return a.getY() == b.getY()
                && Math.abs(a.getX() - b.getX()) <= 1
                && Math.abs(a.getZ() - b.getZ()) <= 1;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** The registered bed with the most players within the anchor radius. */
    public AnchorPicker.Result findTarget(MinecraftServer server) {
        List<BedRecord> beds = save.registeredBeds(config.sleepsToRegisterBase);
        if (beds.isEmpty()) {
            return null;
        }
        List<AnchorPicker.Point> bedPoints = new ArrayList<>();
        for (BedRecord bed : beds) {
            bedPoints.add(new AnchorPicker.Point(bed.dimension, bed.x, bed.y, bed.z));
        }
        List<AnchorPicker.Point> playerPoints = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            playerPoints.add(new AnchorPicker.Point(dimensionName(player.level()),
                    player.getX(), player.getY(), player.getZ()));
        }
        return AnchorPicker.pick(bedPoints, playerPoints, config.anchorRadius);
    }

    private List<ServerPlayer> playersNear(MinecraftServer server, ServerLevel targetWorld,
                                           BlockPos center, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        double radiusSq = radius * radius;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != targetWorld) {
                continue;
            }
            if (player.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) <= radiusSq) {
                out.add(player);
            }
        }
        return out;
    }

    public static String dimensionName(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    public static ServerLevel levelByName(MinecraftServer server, String name) {
        for (ServerLevel candidate : server.getAllLevels()) {
            if (dimensionName(candidate).equals(name)) {
                return candidate;
            }
        }
        return null;
    }

    public void broadcast(MinecraftServer server, Component message) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }
}
