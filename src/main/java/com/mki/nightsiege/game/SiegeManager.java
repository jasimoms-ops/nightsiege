package com.mki.nightsiege.game;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.AnchorPicker;
import com.mki.nightsiege.core.EventPicker;
import com.mki.nightsiege.core.MobPool;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SiegeScheduler;
import com.mki.nightsiege.core.SkillTree;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Runs the whole mod: the countdown, the siege itself, skills and persistence. */
public final class SiegeManager {

    private static final SiegeManager INSTANCE = new SiegeManager();
    private final ArcherEffects archer = new ArcherEffects();

    /**
     * The skills that count for this player right now: during a siege for every
     * defender still standing, and always in creative so classes can be tried.
     */
    PlayerSkills liveSkillsFor(ServerPlayer player) {
        boolean defending = active && participants.contains(player.getUUID())
                && !fallen.contains(player.getUUID());
        if (!defending && !player.getAbilities().instabuild) {
            return null;
        }
        return save.skillsOf(player.getUUID().toString());
    }

    private static final String CONFIG_FILE = "nightsiege.json";
    private static final String SAVE_FILE = "nightsiege-state.json";
    private static final int DIG_INTERVAL = 10;
    /** Write the countdown to disk once a minute rather than every tick. */
    private static final int PERSIST_INTERVAL = 1200;

    public static SiegeManager get() {
        return INSTANCE;
    }

    private SiegeConfig config = new SiegeConfig();
    private SiegeSave save = new SiegeSave();
    private final SiegeScheduler scheduler = new SiegeScheduler();
    private final SkillTree skillTree = new SkillTree();
    private final Digger digger = new Digger();
    private final Random random = new Random();
    private MobPool pool = new MobPool(SiegeConfig.defaultMobs());
    private final Set<String> unavailableMobs = new HashSet<>();
    private long lastKnownDay;
    private int persistCounter;

    // --- state of the running siege ---
    private boolean active;
    private ServerLevel world;
    private BlockPos anchor;
    private int siegeLevel;
    private int ticksLeft;
    private int totalTicks;
    private int batchCooldown;
    private int digAccumulator;
    /** Players waiting for the client-mod check, with the tick it is due. */
    private final Map<UUID, Integer> pendingModCheck = new HashMap<>();
    private ServerBossEvent bossBar;
    private SiegeEvent currentEvent = SiegeEvent.NONE;
    private String themeMobId;
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

    public SkillTree skillTree() {
        return skillTree;
    }

    public SiegeScheduler scheduler() {
        return scheduler;
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
        ModContent.register();
        CoreNetworking.registerTypes();
        CoreNetworking.registerServerReceivers();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            syncSchedulerToSave();
            persist();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> onEndTick(server));
        // Object parameters keep these handlers compiling across Fabric versions.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onDeath(entity));
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(
                (killWorld, killer, killed, killSource) -> onKill(killer, killed));
        ServerPlayerEvents.JOIN.register(player -> onJoin(player));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) ->
                archer.onEntityLoad(entity, world, skillTree, this::liveSkillsFor));
        PlayerBlockBreakEvents.AFTER.register(
                (level, player, pos, state, blockEntity) -> onBlockBroken(level, pos));
        UseBlockCallback.EVENT.register(
                (player, level, hand, hitResult) -> onUseBlock(player, level, hitResult));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> SiegeCommands.register(dispatcher));
    }

    private void onServerStarted(MinecraftServer server) {
        reload();
        NightSiegeMod.LOGGER.info("[Night Siege] {} Cores bekannt, {} Belagerungen bestanden",
                save.cores == null ? 0 : save.cores.size(), save.siegesWon);
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
        if (save.cores == null) {
            save.cores = new ArrayList<>();
        }
        scheduler.setReadyTicks(save.readyTicks);
        scheduler.setRequiredTicks(save.requiredTicks);
        if (scheduler.requiredTicks() <= 0L && !save.cores.isEmpty()) {
            scheduler.rollInterval(random, config);
        }

        pool = new MobPool(config.mobs);
        unavailableMobs.clear();
    }

    private void syncSchedulerToSave() {
        save.readyTicks = scheduler.readyTicks();
        save.requiredTicks = scheduler.requiredTicks();
    }

    public void persist() {
        Json.write(Json.configFile(SAVE_FILE), save);
    }

    // ------------------------------------------------------------------
    // main loop
    // ------------------------------------------------------------------

    private void onEndTick(MinecraftServer server) {
        tickModCheck(server);

        long gameTime = server.overworld().getGameTime();
        lastKnownDay = SiegeMath.dayOf(gameTime);

        if (active) {
            tickSiege(server);
            return;
        }

        // The countdown only moves while enough defenders are gathered.
        AnchorPicker.Result target = findTarget(server);
        if (target != null && target.playerCount() >= config.requiredPlayers) {
            scheduler.addQualifyingTicks(1L);
            if (++persistCounter >= PERSIST_INTERVAL) {
                persistCounter = 0;
                syncSchedulerToSave();
                persist();
            }
        }

        SiegeScheduler.Decision decision = scheduler.evaluate(gameTime, false, config);
        if (decision == SiegeScheduler.Decision.WARN) {
            if (target != null) {
                broadcast(server, Component.translatable("nightsiege.msg.warning", currentLevel())
                        .withStyle(ChatFormatting.GOLD));
                Fx.warning(server, config);
            }
        } else if (decision == SiegeScheduler.Decision.START) {
            tryStart(server, target);
        }
    }

    private void tryStart(MinecraftServer server, AnchorPicker.Result target) {
        if (target == null) {
            return;
        }
        ServerLevel targetWorld = levelByName(server, target.anchor().dimension());
        if (targetWorld == null) {
            return;
        }
        BlockPos pos = new BlockPos((int) target.anchor().x(), (int) target.anchor().y(),
                (int) target.anchor().z());
        start(server, targetWorld, pos);
    }

    /** Starts a siege at the given core. Also used by the start command. */
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
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                SkillService.apply(player, save.skillsOf(player.getUUID().toString()), skillTree);
            }
        }

        broadcast(server, Component.translatable("nightsiege.msg.start", siegeLevel)
                .withStyle(ChatFormatting.RED));
        Fx.siegeStart(server, config, currentEvent == SiegeEvent.BLOOD_MOON);
        if (currentEvent == SiegeEvent.ELITE) {
            spawnElite(server);
        }
        return true;
    }

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
                config, random, currentEvent, true, spawnDistanceBonus());
        if (champion != null) {
            eliteId = champion.getUUID();
            liveMobs.add(eliteId);
            aimAtDefender(server, champion);
            Fx.championSpawned(world, config, champion);
            SiegeTeams.mark(server, champion, SiegeTeams.CHAMPION, TeamColor.GOLD);
        }
    }

    /** Champion aura and blood moon haze, on a slow beat so they stay cheap. */
    private void tickAtmosphere(MinecraftServer server) {
        if (!config.particlesEnabled || world == null) {
            return;
        }
        if (ticksLeft % 10 == 0 && eliteId != null) {
            Entity champion = world.getEntity(eliteId);
            if (champion instanceof Mob championMob && championMob.isAlive()) {
                Fx.championAura(world, config, championMob);
            }
        }
        if (ticksLeft % 40 == 0 && currentEvent == SiegeEvent.BLOOD_MOON) {
            for (UUID id : participants) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null && player.level() == world) {
                    Fx.bloodMoonHaze(world, config, player);
                }
            }
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

        for (ServerPlayer player : playersNear(server, world, anchor, config.participantRadius)) {
            if (!fallen.contains(player.getUUID())) {
                if (participants.add(player.getUUID())) {
                    SkillService.apply(player, save.skillsOf(player.getUUID().toString()), skillTree);
                }
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
                finish(server, false);
            } else {
                abort(server);
            }
            return;
        }

        if (batchCooldown <= 0) {
            spawnBatch(server);
            double extraDelay = skillTree.totalBonus(SkillEffect.WAVE_DELAY, save.coreSkills());
            batchCooldown = (int) Math.round((config.batchIntervalSeconds + extraDelay) * 20.0);
        } else {
            batchCooldown--;
        }

        tickAtmosphere(server);

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
        if (count > 0) {
            Fx.waveIncoming(server, config, participants);
        }
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            String entityId = themeMobId != null
                    ? themeMobId
                    : pool.pick(siegeLevel, random, unavailableMobs);
            if (entityId == null) {
                return;
            }
            if (MobFactory.isUnavailable(entityId)) {
                if (unavailableMobs.add(entityId)) {
                    NightSiegeMod.LOGGER.warn("[Night Siege] Mob {} nicht gefunden, wird uebersprungen", entityId);
                }
                continue;
            }
            Mob mob = MobFactory.spawn(world, anchor, entityId, siegeLevel,
                    config, random, currentEvent, false, spawnDistanceBonus());
            if (mob != null) {
                liveMobs.add(mob.getUUID());
                aimAtDefender(server, mob);
                Fx.mobSpawned(world, config, mob, spawned < config.audibleSpawnsPerBatch);
                if (currentEvent == SiegeEvent.BLOOD_MOON && config.bloodMoonGlow) {
                    SiegeTeams.mark(server, mob, SiegeTeams.BLOOD_MOON, TeamColor.DARK_RED);
                }
                spawned++;
            }
        }
    }

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

    // ------------------------------------------------------------------
    // ending
    // ------------------------------------------------------------------

    /** Extra blocks the Basis branch pushes the attackers back by. */
    private double spawnDistanceBonus() {
        return skillTree.totalBonus(SkillEffect.SPAWN_DISTANCE, save.coreSkills());
    }

    /** Skills only hold for the night, so everyone goes back to normal values. */
    private void clearSkillsFromParticipants(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                SkillService.clear(player);
            }
        }
    }

    public void finish(MinecraftServer server, boolean won) {
        if (!active) {
            return;
        }
        active = false;
        clearSkillsFromParticipants(server);
        SiegeTeams.cleanUp(server);
        if (world != null && anchor != null) {
            Fx.coreFlare(world, config, anchor, won);
        }
        if (won) {
            Fx.victory(server, config);
        } else {
            Fx.defeat(server, config);
        }
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        clearRemainingMobs();
        digger.reset();

        save.highestLevel = Math.max(save.highestLevel, siegeLevel);
        if (won) {
            save.siegesWon++;
            save.currentStreak++;
            save.bestStreak = Math.max(save.bestStreak, save.currentStreak);
            awardSkillPoints(server);
            int next = SiegeMath.level(save.siegesWon, lastKnownDay, config);
            broadcast(server, Component.translatable("nightsiege.msg.won", next)
                    .withStyle(ChatFormatting.GREEN));
        } else {
            save.siegesLost++;
            save.currentStreak = 0;
            int destroyed = 0;
            if (world != null && anchor != null) {
                destroyed = Demolition.blast(world, anchor, config);
            }
            resetProgress(server);
            broadcast(server, Component.translatable("nightsiege.msg.lost", destroyed)
                    .withStyle(ChatFormatting.DARK_RED));
            broadcast(server, Component.translatable("nightsiege.msg.core_reset")
                    .withStyle(ChatFormatting.DARK_RED));
        }

        scheduler.markSiegeFinished(random, config);
        syncSchedulerToSave();
        persist();
        clearSiegeState();
    }

    /** Ends the siege with no winner and no punishment. */
    public void abort(MinecraftServer server) {
        if (!active) {
            return;
        }
        active = false;
        clearSkillsFromParticipants(server);
        SiegeTeams.cleanUp(server);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        clearRemainingMobs();
        digger.reset();
        broadcast(server, Component.translatable("nightsiege.msg.aborted").withStyle(ChatFormatting.GRAY));
        clearSiegeState();
    }

    private void clearSiegeState() {
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

    /** A lost siege sets the core back to the start: progress and skills are gone. */
    private void resetProgress(MinecraftServer server) {
        save.siegesWon = 0;
        save.resetAllSkills();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SkillService.clear(player);
        }
    }

    private void awardSkillPoints(MinecraftServer server) {
        if (!config.skillsEnabled || config.skillPointsPerSiege <= 0) {
            return;
        }
        int amount = config.skillPointsPerSiege + siegeLevel / Math.max(1, config.skillPointsLevelStep);
        // Everyone who was still standing earns it for themselves.
        for (UUID id : participants) {
            if (fallen.contains(id)) {
                continue;
            }
            save.skillsOf(id.toString()).award(amount);
        }
        Fx.pointsEarned(server, config);
        broadcast(server, Component.translatable("nightsiege.msg.points_earned", amount)
                .withStyle(ChatFormatting.AQUA));
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
        if (!active || !participants.contains(id)) {
            return;
        }
        fallen.add(id);
        save.statsOf(id.toString(), player.getScoreboardName()).fallen++;
        MinecraftServer server = player.level().getServer();
        broadcast(server, Component.translatable("nightsiege.msg.defender_down",
                player.getScoreboardName(), countStanding()).withStyle(ChatFormatting.RED));
        Fx.defenderDown(server, config, participants);
    }

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
        if (world != null) {
            Fx.championDown(world, config, champion);
        }
        broadcast(champion.level().getServer(),
                Component.translatable("nightsiege.msg.elite_down", weakened)
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
    private void onJoin(Object entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        // Skills gelten nur waehrend einer Belagerung. Beim Betreten werden
        // daher Reste entfernt, etwa nach einem Absturz mitten in der Nacht.
        if (active && participants.contains(player.getUUID()) && !fallen.contains(player.getUUID())) {
            SkillService.apply(player, save.skillsOf(player.getUUID().toString()), skillTree);
        } else {
            SkillService.clear(player);
        }
        giveStarterCore(player);
        giveGuideBook(player);
        if (config.requireClientMod) {
            // The client registers its channels a moment after joining, so the
            // check waits three seconds rather than kicking an honest player.
            pendingModCheck.put(player.getUUID(), 60);
        }
    }

    /** Every player gets the written guide once, the first time they join. */
    private void giveGuideBook(ServerPlayer player) {
        String id = player.getUUID().toString();
        if (!config.giveGuideBook || !save.needsGuide(id)) {
            return;
        }
        save.markGuideGiven(id);
        persist();
        if (!player.addItem(GuideBook.create())) {
            player.drop(GuideBook.create(), false);
        }
    }

    /** The very first player of a world is handed one core, once ever. */
    private void giveStarterCore(ServerPlayer player) {
        if (!config.giveStarterCore || save.starterCoreGiven || ModContent.SIEGE_CORE_ITEM == null) {
            return;
        }
        save.starterCoreGiven = true;
        ItemStack stack = new ItemStack(ModContent.SIEGE_CORE_ITEM);
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
        player.sendSystemMessage(Component.translatable("nightsiege.msg.starter_core")
                .withStyle(ChatFormatting.GOLD));
        persist();
    }

    /** Called from the block itself when a core is put down. */
    public void onCorePlaced(ServerLevel level, BlockPos pos, LivingEntity placer) {
        String dimension = dimensionName(level);
        if (save.findCore(dimension, pos.getX(), pos.getY(), pos.getZ()) != null) {
            return;
        }
        save.cores.add(new CoreRecord(dimension, pos.getX(), pos.getY(), pos.getZ()));
        if (scheduler.requiredTicks() <= 0L) {
            scheduler.rollInterval(random, config);
        }
        syncSchedulerToSave();
        persist();
        if (placer instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.core_placed",
                    config.requiredPlayers, (int) config.coreRadius).withStyle(ChatFormatting.GOLD));
        }
    }

    /** Takes Object so the handler keeps compiling across Fabric API versions. */
    private void onBlockBroken(Object levelObject, BlockPos pos) {
        if (!(levelObject instanceof ServerLevel brokenWorld)) {
            return;
        }
        String dimension = dimensionName(brokenWorld);
        if (save.findCore(dimension, pos.getX(), pos.getY(), pos.getZ()) == null) {
            return;
        }
        save.removeCore(dimension, pos.getX(), pos.getY(), pos.getZ());
        syncSchedulerToSave();
        persist();

        MinecraftServer server = brokenWorld.getServer();
        if (active && world == brokenWorld && pos.equals(anchor)) {
            broadcast(server, Component.translatable("nightsiege.msg.anchor_broken")
                    .withStyle(ChatFormatting.DARK_RED));
            finish(server, false);
        }
    }

    /** Right clicking the core opens the status and skill menu. */
    private InteractionResult onUseBlock(Object playerObject, Object levelObject, Object hitObject) {
        if (!(levelObject instanceof ServerLevel serverLevel)
                || !(playerObject instanceof ServerPlayer player)
                || !(hitObject instanceof BlockHitResult hit)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hit.getBlockPos();
        if (!ModContent.isCore(serverLevel.getBlockState(pos))) {
            return InteractionResult.PASS;
        }
        openCoreWindow(player);
        return InteractionResult.SUCCESS;
    }

    /** Disconnects players whose client does not have the mod. */
    private void tickModCheck(MinecraftServer server) {
        if (pendingModCheck.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> it = pendingModCheck.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            if (!ServerPlayNetworking.canSend(player, CoreNetworking.CoreUiPayload.TYPE)) {
                NightSiegeMod.LOGGER.info("[Night Siege] {} ohne Mod abgewiesen", player.getScoreboardName());
                player.connection.disconnect(Component.translatable("nightsiege.msg.mod_required"));
            }
        }
    }

    /** Sends the window state, or falls back to chat for a client without it. */
    public void openCoreWindow(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, CoreNetworking.CoreUiPayload.TYPE)) {
            StatsService.sendTo(player, this);
        } else {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.mod_required"));
        }
    }

    /** Counts a kill and turns it into skill experience. */
    private void onKill(Object killerObject, Object killedObject) {
        if (!(killerObject instanceof ServerPlayer player) || !(killedObject instanceof Entity killed)) {
            return;
        }
        if (!killed.entityTags().contains(MobFactory.TAG)) {
            return;
        }
        boolean champion = killed.entityTags().contains(MobFactory.TAG_ELITE);
        PlayerStats stats = save.statsOf(player.getUUID().toString(), player.getScoreboardName());
        stats.kills++;
        save.totalKills++;
        if (champion) {
            stats.championKills++;
            save.totalChampionKills++;
        }
        if (config.skillsEnabled && config.skillPointsPerKill > 0) {
            save.skillsOf(player.getUUID().toString()).award(champion
                    ? config.skillPointsPerKill * config.championPointMultiplier
                    : config.skillPointsPerKill);
        }
    }

    /** Routes one button press from the window. */
    public void onCoreAction(ServerPlayer player, String action) {
        if (player == null || action == null || action.isBlank()) {
            return;
        }
        String[] parts = action.split(":", 2);
        String verb = parts[0];
        String argument = parts.length > 1 ? parts[1] : "";
        switch (verb) {
            case "buy" -> onBuySkill(player, argument);
            case "class" -> onChooseClass(player, argument);
            case "donate" -> onDonate(player, parseAmount(argument));
            case "dev" -> onDevAction(player, argument);
            default -> openCoreWindow(player);
        }
    }

    private static int parseAmount(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Creative shortcuts, so classes can be tried out without grinding. */
    private void onDevAction(ServerPlayer player, String argument) {
        if (!player.getAbilities().instabuild) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.creative_only")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (argument.startsWith("reset")) {
            devResetClass(player);
        } else {
            devGrant(player, Math.max(1, parseAmount(argument)));
        }
    }

    /** A player clicked a node in the window. */
    public void onBuySkill(ServerPlayer player, String nodeId) {
        if (player == null || nodeId == null || !config.skillsEnabled) {
            return;
        }
        var node = skillTree.get(nodeId);
        if (node == null) {
            return;
        }
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        if (node.clazz().isShared()) {
            // The core tree is paid from the shared pot that donations fill.
            if (skillTree.unlock(nodeId, save.coreSkills())) {
                persist();
            }
        } else if (skillTree.unlock(nodeId, own)) {
            persist();
            // Skills only hold during a siege, so apply at once only if one runs.
            if (active && participants.contains(player.getUUID()) && !fallen.contains(player.getUUID())) {
                SkillService.apply(player, own, skillTree);
            }
        }
        openCoreWindow(player);
    }

    /** Commits a player to a class. Only possible while they have none. */
    public void onChooseClass(ServerPlayer player, String className) {
        SkillClass chosen = SkillClass.byName(className);
        if (player == null || chosen == null || chosen.isShared()) {
            return;
        }
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        if (own.hasClass()) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.class_locked")
                    .withStyle(ChatFormatting.RED));
        } else {
            own.chosenClass = chosen.name();
            persist();
            player.sendSystemMessage(Component.translatable("nightsiege.msg.class_chosen",
                    Component.translatable(chosen.translationKey())).withStyle(ChatFormatting.GREEN));
        }
        openCoreWindow(player);
    }

    /** Moves experience from a player into the shared core pot. */
    public void onDonate(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        int moved = Math.min(amount, own.points());
        if (moved > 0) {
            own.spend(moved);
            own.donated += moved;
            save.coreSkills().award(moved);
            persist();
            broadcast(player.level().getServer(), Component.translatable("nightsiege.msg.donated",
                    player.getScoreboardName(), moved).withStyle(ChatFormatting.AQUA));
        }
        openCoreWindow(player);
    }

    /** Creative only: hands a player experience so a class can be tried out. */
    public void devGrant(ServerPlayer player, int amount) {
        save.skillsOf(player.getUUID().toString()).award(Math.max(0, amount));
        save.coreSkills().award(Math.max(0, amount));
        persist();
        openCoreWindow(player);
    }

    /** Creative only: frees a player to pick a different class. */
    public void devResetClass(ServerPlayer player) {
        save.skillsOf(player.getUUID().toString()).reset();
        SkillService.clear(player);
        persist();
        openCoreWindow(player);
    }

    /** The running event, or an empty string outside a siege. */
    public String currentEventKey() {
        return active && currentEvent != SiegeEvent.NONE ? currentEvent.name() : "";
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** The core with the most players within the core radius. */
    public AnchorPicker.Result findTarget(MinecraftServer server) {
        if (save.cores == null || save.cores.isEmpty()) {
            return null;
        }
        List<AnchorPicker.Point> corePoints = new ArrayList<>();
        for (CoreRecord core : save.cores) {
            if (core != null) {
                corePoints.add(new AnchorPicker.Point(core.dimension, core.x, core.y, core.z));
            }
        }
        List<AnchorPicker.Point> playerPoints = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            playerPoints.add(new AnchorPicker.Point(dimensionName(player.level()),
                    player.getX(), player.getY(), player.getZ()));
        }
        return AnchorPicker.pick(corePoints, playerPoints, config.coreRadius);
    }

    /** How many players are gathered at the best core right now. */
    public int playersAtCore(MinecraftServer server) {
        AnchorPicker.Result target = findTarget(server);
        return target == null ? 0 : target.playerCount();
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

    public static String dimensionName(Level level) {
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
