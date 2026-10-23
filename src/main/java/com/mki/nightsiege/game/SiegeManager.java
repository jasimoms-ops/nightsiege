package com.mki.nightsiege.game;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.AnchorPicker;
import com.mki.nightsiege.core.EventPicker;
import com.mki.nightsiege.core.MobPool;
import com.mki.nightsiege.core.NightFragment;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.TrapKind;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SiegeScheduler;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.WaveXp;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
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
import java.util.Locale;
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
        PlayerSkills own = effectiveSkills(player);
        if (!defending && !player.getAbilities().instabuild && !ClassGear.carries(player, own, skillTree)) {
            return null;
        }
        return own;
    }

    /** Creative only: which of a class item's paths is being tried out, per player. */
    private final Map<UUID, Integer> testPathIndex = new HashMap<>();
    /** Fully levelled stand-in skill sets, one per path, for trying paths out. */
    private final Map<String, PlayerSkills> testProfiles = new HashMap<>();

    /**
     * The skills that count for this player. Normally their own; in creative,
     * holding an infernite class item of another path lends them that path,
     * fully levelled, so every ultimate can be tried out.
     */
    PlayerSkills effectiveSkills(ServerPlayer player) {
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        String path = testPath(player, own);
        return path == null ? own : testProfile(path);
    }

    private String testPath(ServerPlayer player, PlayerSkills own) {
        if (!player.getAbilities().instabuild) {
            return null;
        }
        List<String> paths = ClassGear.heldPaths(player);
        if (paths.isEmpty() || paths.contains(own.pathTaken(skillTree))) {
            return null;
        }
        int index = testPathIndex.getOrDefault(player.getUUID(), 0);
        return paths.get(Math.floorMod(index, paths.size()));
    }

    private PlayerSkills testProfile(String path) {
        return testProfiles.computeIfAbsent(path, p -> {
            PlayerSkills profile = new PlayerSkills();
            for (com.mki.nightsiege.core.SkillNode node : skillTree.all()) {
                if (p.equals(node.path())) {
                    profile.chosenClass = node.clazz().name();
                }
            }
            // Everything of the path at full level; of each choice only the first that fits.
            java.util.Set<String> groups = new java.util.HashSet<>();
            for (com.mki.nightsiege.core.SkillNode node : skillTree.all()) {
                if (!node.clazz().name().equals(profile.chosenClass) || !(node.onTrunk() || p.equals(node.path()))) {
                    continue;
                }
                if ((node.inGroup() && groups.contains(node.group())) || !skillTree.needsMet(node, profile)
                        || skillTree.lockedOut(node, profile)) {
                    continue;
                }
                if (node.inGroup()) {
                    groups.add(node.group());
                }
                profile.setLevel(node.id(), node.maxLevel());
            }
            return profile;
        });
    }

    /** Ultimate key: fires the ultimate; in creative, sneaking switches the path being tried. */
    private void onUltimateKey(ServerPlayer player) {
        List<String> paths = ClassGear.heldPaths(player);
        if (player.getAbilities().instabuild && player.isShiftKeyDown() && paths.size() > 1) {
            int next = testPathIndex.getOrDefault(player.getUUID(), 0) + 1;
            testPathIndex.put(player.getUUID(), next);
            String path = paths.get(Math.floorMod(next, paths.size()));
            com.mki.nightsiege.core.SkillNode ult = skillTree.ultimateOf(testProfile(path));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                    Component.translatable("nightsiege.msg.test_path",
                            Component.translatable("nightsiege.ui.path_" + path),
                            ult == null ? Component.literal("-") : Component.translatable("nightsiege.skill." + ult.id()))));
            return;
        }
        combat.triggerUltimate(player);
    }

    private static final String CONFIG_FILE = "nightsiege.json";
    private static final String SAVE_FILE = "nightsiege-state.json";
    /** Where this world keeps its siege state: inside the world folder, so every world has its own. */
    private Path savePath;

    /**
     * Older versions kept the state in the config folder, shared by every world.
     * The first world started with this version takes it over; the old file is
     * renamed so no other world inherits it.
     */
    private void migrateSharedSave() {
        Path legacy = Json.configFile(SAVE_FILE);
        try {
            if (savePath != null && !java.nio.file.Files.exists(savePath) && java.nio.file.Files.exists(legacy)) {
                java.nio.file.Files.copy(legacy, savePath);
                java.nio.file.Files.move(legacy, legacy.resolveSibling("nightsiege-state.migrated.json"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                NightSiegeMod.LOGGER.info("[Night Siege] Alter gemeinsamer Spielstand in diese Welt uebernommen");
            }
        } catch (java.io.IOException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Spielstand konnte nicht uebernommen werden: {}", e.toString());
        }
    }
    private static final int DIG_INTERVAL = 10;
    /** Write the countdown to disk once a minute rather than every tick. */
    private static final int PERSIST_INTERVAL = 1200;

    public static SiegeManager get() {
        return INSTANCE;
    }

    private SiegeConfig config = new SiegeConfig();
    private SiegeSave save = new SiegeSave();
    /** Trap wear is saved in batches, not on every spike hit. */
    private boolean trapsDirty;
    private int trapSaveCounter;
    private final SiegeScheduler scheduler = new SiegeScheduler();
    private final SkillTree skillTree = new SkillTree();
    private final TrapService traps = new TrapService(() -> save, skillTree);
    private final CombatEffects combat = new CombatEffects(skillTree, this::liveSkillsFor,
            entity -> this.eliteId != null && this.eliteId.equals(entity.getUUID()), archer, traps);
    private final Digger digger = new Digger();
    private final DungeonService dungeons = new DungeonService(() -> save, () -> config);
    private final PerkService perks = new PerkService(() -> save, () -> config);
    private final BaseDefense defense = new BaseDefense();
    /** The night's protocol for balancing (config/nightsiege-stats*.csv). */
    private final NightLog nightLog = new NightLog();
    private final EventEffects events = new EventEffects();
    private final BonusEvents bonus = new BonusEvents(() -> save, () -> config, perks);
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
    /** When each defender last got the free traps (game time): at the warning, in a test's preparation or at the start. */
    private final Map<UUID, Long> trapsHanded = new HashMap<>();
    /** From the warning until the night is over no natural monsters spawn in this world (dimension name). */
    private String spawnBlockWorld;
    private long spawnBlockUntil;
    /** A test night that waits for its preparation time to run out, and its bar. */
    private PendingTest pendingTest;
    private ServerBossEvent prepBar;

    private static final class PendingTest {
        final ServerLevel world;
        final BlockPos anchor;
        final int level;
        final SiegeEvent event;
        final int totalTicks;
        int ticksLeft;

        PendingTest(ServerLevel world, BlockPos anchor, int level, SiegeEvent event, int ticks) {
            this.world = world;
            this.anchor = anchor;
            this.level = level;
            this.event = event;
            this.totalTicks = ticks;
            this.ticksLeft = ticks;
        }
    }
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

    public DungeonService dungeons() {
        return dungeons;
    }

    public PerkService perks() {
        return perks;
    }

    public BonusEvents bonus() {
        return bonus;
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
        combat.setCooldownScale(perks::cooldownScale);
        combat.setArrowBonus(p -> perks.arrowBonus(p) + defense.arrowBonus(p));
        traps.setTeslaBoost(() -> active && currentEvent == SiegeEvent.THUNDER ? config.thunderTeslaMultiplier : 1.0);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            dungeons.abortAll();
            if (active) {
                events.end(server, world, currentEvent);
            }
            EventAtmosphere.resetServer();
            syncSchedulerToSave();
            persist();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> onEndTick(server));
        // Object parameters keep these handlers compiling across Fabric versions.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onDeath(entity));
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(
                (killWorld, killer, killed, killSource) -> onKill(killer, killed));
        ServerPlayerEvents.JOIN.register(player -> onJoin(player));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && keptInventory.remove(oldPlayer.getUUID())) {
                net.minecraft.world.entity.player.Inventory from = oldPlayer.getInventory();
                net.minecraft.world.entity.player.Inventory to = newPlayer.getInventory();
                for (int i = 0; i < from.getContainerSize(); i++) {
                    to.setItem(i, from.getItem(i).copy());
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
            logDamage(entity, source, taken);
            combat.afterDamage(entity, source, baseDamage, taken, blocked);
            perks.afterDamage(entity, taken);
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> combat.allowDeath(entity));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> combat.allowDamage(entity, source, amount));
        combat.guardian().setCredit(this::creditTank);
        traps.setMarker(combat::remoteMark);
        RemoteItem.handler = traps::useRemote;
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                !world.isClientSide() && player instanceof ServerPlayer server
                        && combat.guardian().onUseEntity(server, entity, hand)
                        ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS);
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            archer.onEntityLoad(entity, world, skillTree, this::liveSkillsFor);
            dungeons.onEntityLoad(entity);
        });
        PlayerBlockBreakEvents.AFTER.register(
                (level, player, pos, state, blockEntity) -> onBlockBroken(level, pos));
        UseBlockCallback.EVENT.register(
                (player, level, hand, hitResult) -> onUseBlock(player, level, hand, hitResult));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> SiegeCommands.register(dispatcher));
    }

    private void onServerStarted(MinecraftServer server) {
        EventAtmosphere.resetServer();
        savePath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve(SAVE_FILE).normalize();
        migrateSharedSave();
        reload();
        NightSiegeMod.LOGGER.info("[Night Siege] {} Cores bekannt, {} Belagerungen bestanden",
                save.cores == null ? 0 : save.cores.size(), save.siegesWon);
        // CI only: where the cores of real dungeons would stand.
        if (System.getenv("NIGHTSIEGE_DUNGEON_PROBE") != null) {
            dungeons.probe(server);
        }
    }

    /** How far the base scan looks for walls; kept in the config file. */
    public void setBaseRadius(int radius) {
        config.baseRadius = Math.max(16, Math.min(192, radius));
        Json.write(Json.configFile(CONFIG_FILE), config);
        BaseDefense.forgetScans();
    }

    /** Reads config and saved state from disk. Also used by the reload command. */
    public void reload() {
        Path configPath = Json.configFile(CONFIG_FILE);
        SiegeConfig loaded = Json.read(configPath, SiegeConfig.class);
        config = loaded == null ? new SiegeConfig() : loaded;
        config.sanitize();
        int tweaked = skillTree.applyTweaks(config.skillTweaks);
        if (tweaked > 0) {
            NightSiegeMod.LOGGER.info("[Night Siege] {} Skills mit Server-Werten", tweaked);
        }
        Json.write(configPath, config);
        DungeonContent.active = config.dungeonsEnabled;

        SiegeSave loadedSave = savePath == null ? null : Json.read(savePath, SiegeSave.class);
        save = loadedSave == null ? new SiegeSave() : loadedSave;
        if (save.cores == null) {
            save.cores = new ArrayList<>();
        }
        int respec = save.migrateSkills();
        if (respec > 0) {
            NightSiegeMod.LOGGER.info("[Night Siege] Neue Klassen-Bäume: {} Spieler bekommen ihre Punkte zurück", respec);
            persist();
        }
        boolean oldBalance = save.balanceVersion < SiegeSave.BALANCE_VERSION;
        int restarted = save.migrateBalance();
        if (oldBalance) {
            NightSiegeMod.LOGGER.info("[Night Siege] Neues Balancing: {} Spieler starten mit 0 Punkten neu", restarted);
            persist();
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
        if (savePath != null) {
            Json.write(savePath, save);
        }
    }

    // ------------------------------------------------------------------
    // main loop
    // ------------------------------------------------------------------

    private void onEndTick(MinecraftServer server) {
        tickModCheck(server);
        trapsDirty |= traps.tick(server);
        combat.tick(server);
        perks.tick(server);
        EventAtmosphere.tickServer();
        BaseDefense.tickHighlights(server);
        if (dungeons.tick(server)) {
            persist();
        }
        if (server.overworld().getGameTime() % 100 == 0) {
            refreshClassTags(server);
        }
        if (server.overworld().getGameTime() % 20 == 0) {
            events.syncClients(server);
            bonus.tick(server, active, scheduler.armed());
        }
        if (server.overworld().getGameTime() % 20 == 0) {
            sendUltHud(server);
            tickClassGear(server);
        }
        if (trapsDirty && ++trapSaveCounter >= 200) {
            trapSaveCounter = 0;
            trapsDirty = false;
            persist();
        }

        long gameTime = server.overworld().getGameTime();
        lastKnownDay = SiegeMath.dayOf(gameTime);

        if (active) {
            tickSiege(server);
            return;
        }
        if (pendingTest != null) {
            tickPreparation(server);
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

        // The event of the coming siege is rolled as soon as the countdown is done:
        // an eclipse comes at noon instead of at night.
        long clock = server.overworld().getOverworldClockTime();
        SiegeEvent pending = pendingEvent();
        if (scheduler.armed() && pending == null) {
            NightFragment fragment = NightFragment.byName(save.nightFragment);
            pending = fragment != null ? fragment.event() : EventPicker.pick(currentLevel(), random, config);
            save.pendingEvent = pending.name();
            persist();
        }
        boolean eclipse = pending == SiegeEvent.ECLIPSE;
        SiegeScheduler.Decision decision = eclipse
                ? scheduler.evaluate(clock, false, config.eclipseWarningDayTime, config.eclipseStartDayTime)
                : scheduler.evaluate(clock, false, config);
        if (decision == SiegeScheduler.Decision.WARN) {
            if (target != null) {
                broadcast(server, Component.translatable(eclipse ? "nightsiege.msg.eclipse_warning" : "nightsiege.msg.warning",
                        currentLevel()).withStyle(eclipse ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
                NightFragment fragment = NightFragment.byName(save.nightFragment);
                if (fragment != null) {
                    broadcast(server, Component.translatable("nightsiege.msg.special_warning",
                            Component.translatable("nightsiege.event." + fragment.event().name().toLowerCase(Locale.ROOT)),
                            config.specialNightXpMultiplier).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                Fx.warning(server, config);
                handOutAtWarning(server, target);
                if (eclipse) {
                    EventAtmosphere.setServer(target.anchor().dimension(), EventAtmosphere.ECLIPSE,
                            EventAtmosphere.fadeTicks(EventAtmosphere.ECLIPSE));
                    events.syncClients(server);
                }
            }
        } else if (decision == SiegeScheduler.Decision.START) {
            boolean started = tryStart(server, target, pending);
            if (started) {
                save.pendingEvent = null;
                persist();
            } else if (eclipse && target != null) {
                EventAtmosphere.setServer(target.anchor().dimension(), "", 200);
                events.syncClients(server);
            }
        }
    }

    private SiegeEvent pendingEvent() {
        if (save.pendingEvent == null || save.pendingEvent.isBlank()) {
            return null;
        }
        try {
            return SiegeEvent.valueOf(save.pendingEvent);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean tryStart(MinecraftServer server, AnchorPicker.Result target, SiegeEvent event) {
        if (target == null) {
            return false;
        }
        ServerLevel targetWorld = levelByName(server, target.anchor().dimension());
        if (targetWorld == null) {
            return false;
        }
        BlockPos pos = new BlockPos((int) target.anchor().x(), (int) target.anchor().y(),
                (int) target.anchor().z());
        return start(server, targetWorld, pos, 0, event);
    }

    /** Picks an event for a siege started now; an eclipse only by day. */
    private SiegeEvent pickEventNow(MinecraftServer server) {
        int time = (int) Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
        boolean day = time < 12000;
        SiegeEvent event = EventPicker.pick(siegeLevel, random, config);
        for (int i = 0; i < 8 && event == SiegeEvent.ECLIPSE && !day; i++) {
            event = EventPicker.pick(siegeLevel, random, config);
        }
        return !day && event == SiegeEvent.ECLIPSE ? SiegeEvent.NONE : event;
    }

    /** Starts a siege at the given core. Also used by the start command. */
    public boolean start(MinecraftServer server, ServerLevel targetWorld, BlockPos targetAnchor) {
        return start(server, targetWorld, targetAnchor, 0, null);
    }

    /** Waves started in the running siege. */
    private int wavesStarted;
    /** Siege monsters killed this night, spawned this night, and the kills the night asks for. */
    private int nightKills;
    private int spawnedTotal;
    private int killQuota;
    /** The night fragment this night used up (given back when the night has to be played again). */
    private String usedFragment;
    /** How often a survived wave counts for each defender this night (catching up on missed nights). */
    private final Map<UUID, Integer> waveWeight = new HashMap<>();
    /** Number of the running siege night, for catching up; -1 in tests. */
    private int nightNumber = -1;
    /** Defenders who died this night with their inventory kept: it comes back when they respawn. */
    private final Set<UUID> keptInventory = new HashSet<>();

    /** True while a test siege runs: nothing it does is counted, won or lost. */
    private boolean testRun;
    private int levelOverride;
    private SiegeEvent eventOverride;

    /**
     * Starts a test siege at a chosen level and, optionally, with a chosen event.
     * It plays out like a real one but leaves no trace: no record, no points,
     * no blast and no reset when it is lost.
     */
    public boolean startTest(MinecraftServer server, ServerLevel targetWorld, BlockPos targetAnchor,
                             int level, SiegeEvent event) {
        return start(server, targetWorld, targetAnchor, level, event);
    }

    /**
     * /nightsiege test: the defenders get their free traps at once, the night
     * itself starts after the preparation time (config testPrepSeconds), so
     * traps can be set and positions taken before the first wave.
     */
    public boolean prepareTest(MinecraftServer server, ServerLevel targetWorld, BlockPos targetAnchor,
                               int level, SiegeEvent event) {
        if (active || pendingTest != null) {
            return false;
        }
        List<ServerPlayer> nearby = playersNear(server, targetWorld, targetAnchor, config.participantRadius);
        if (nearby.isEmpty()) {
            return false;
        }
        if (config.testPrepSeconds <= 0) {
            return startTest(server, targetWorld, targetAnchor, level, event);
        }
        trapsHanded.clear();
        for (ServerPlayer player : nearby) {
            handOutTraps(player);
        }
        int shownLevel = Math.min(config.maxLevel, level);
        pendingTest = new PendingTest(targetWorld, targetAnchor, level, event, config.testPrepSeconds * 20);
        prepBar = new ServerBossEvent(UUID.randomUUID(),
                Component.translatable("nightsiege.bar.prep", shownLevel, config.testPrepSeconds),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        prepBar.setProgress(1.0f);
        for (ServerPlayer player : nearby) {
            prepBar.addPlayer(player);
        }
        broadcast(server, Component.translatable("nightsiege.msg.prep_start", shownLevel, config.testPrepSeconds)
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    /**
     * Whether natural monster spawns (and phantoms, patrols) are held back in this
     * world: during a siege, a test's preparation and from the warning on.
     */
    public static boolean blocksNaturalSpawns(ServerLevel level) {
        return INSTANCE.blocksSpawnsIn(level);
    }

    private boolean blocksSpawnsIn(ServerLevel level) {
        if (config == null || !config.blockNaturalSpawnsInSiege || level == null) {
            return false;
        }
        if (active && world == level) {
            return true;
        }
        PendingTest prep = pendingTest;
        if (prep != null && prep.world == level) {
            return true;
        }
        String blocked = spawnBlockWorld;
        return blocked != null && blocked.equals(dimensionName(level)) && level.getGameTime() < spawnBlockUntil;
    }

    /** True while a test night waits for its preparation time. */
    public boolean isPreparing() {
        return pendingTest != null;
    }

    /** Ends a test's preparation without a night. Returns false when none was running. */
    public boolean cancelPreparation() {
        if (pendingTest == null) {
            return false;
        }
        pendingTest = null;
        trapsHanded.clear();
        if (prepBar != null) {
            prepBar.removeAllPlayers();
            prepBar = null;
        }
        return true;
    }

    private void tickPreparation(MinecraftServer server) {
        PendingTest prep = pendingTest;
        prep.ticksLeft--;
        if (prep.ticksLeft > 0) {
            if (prep.ticksLeft % 20 == 0 && prepBar != null) {
                int seconds = prep.ticksLeft / 20;
                prepBar.setName(Component.translatable("nightsiege.bar.prep", Math.min(config.maxLevel, prep.level), seconds));
                prepBar.setProgress(Math.max(0f, Math.min(1f, prep.ticksLeft / (float) prep.totalTicks)));
                prepBar.setColor(seconds <= 10 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
                for (ServerPlayer player : playersNear(server, prep.world, prep.anchor, config.participantRadius)) {
                    prepBar.addPlayer(player);
                    // Whoever comes along in the preparation gets their traps too.
                    handOutTraps(player);
                }
            }
            return;
        }
        pendingTest = null;
        if (prepBar != null) {
            prepBar.removeAllPlayers();
            prepBar = null;
        }
        if (!startTest(server, prep.world, prep.anchor, prep.level, prep.event)) {
            trapsHanded.clear();
            broadcast(server, Component.translatable("nightsiege.cmd.no_target").withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Free traps once per night: whoever got them at the warning or in a test's
     * preparation does not get them again when the night starts.
     */
    private void handOutTraps(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = trapsHanded.get(player.getUUID());
        long window = Math.max(config.startDayTime - config.warningDayTime, config.testPrepSeconds * 20L) + 2400L;
        if (last != null && now >= last && now - last < window) {
            return;
        }
        trapsHanded.put(player.getUUID(), now);
        traps.giveFreeTraps(player);
    }

    /** At the warning the defenders near the core get their free traps, to set them before the first wave. */
    private void handOutAtWarning(MinecraftServer server, AnchorPicker.Result target) {
        ServerLevel targetWorld = levelByName(server, target.anchor().dimension());
        if (targetWorld == null) {
            return;
        }
        // Natural monsters stop with the warning; if the night does not come, they start again a minute after its time.
        spawnBlockWorld = dimensionName(targetWorld);
        long gap = Math.max(config.startDayTime - config.warningDayTime, config.eclipseStartDayTime - config.eclipseWarningDayTime);
        spawnBlockUntil = targetWorld.getGameTime() + Math.max(0L, gap) + 1200L;
        BlockPos pos = new BlockPos((int) target.anchor().x(), (int) target.anchor().y(), (int) target.anchor().z());
        trapsHanded.clear();
        for (ServerPlayer player : playersNear(server, targetWorld, pos, config.participantRadius)) {
            handOutTraps(player);
        }
    }

    private boolean start(MinecraftServer server, ServerLevel targetWorld, BlockPos targetAnchor,
                          int forcedLevel, SiegeEvent forcedEvent) {
        if (active) {
            return false;
        }
        List<ServerPlayer> nearby = playersNear(server, targetWorld, targetAnchor, config.participantRadius);
        if (nearby.isEmpty()) {
            return false;
        }
        world = targetWorld;
        anchor = targetAnchor;
        testRun = forcedLevel > 0;
        siegeLevel = testRun ? Math.min(config.maxLevel, forcedLevel)
                : SiegeMath.level(save.siegesWon, SiegeMath.dayOf(server.overworld().getGameTime()), config);
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
        wavesStarted = 0;
        waveWeight.clear();
        nightNumber = testRun ? -1 : save.nightsHeld++;
        for (ServerPlayer player : nearby) {
            joinNight(player);
        }

        currentEvent = forcedEvent != null ? forcedEvent : pickEventNow(server);
        save.specialNight = false;
        usedFragment = null;
        NightFragment fragment = testRun ? null : NightFragment.byName(save.nightFragment);
        if (fragment != null) {
            int time = (int) Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
            // A shadow fragment waits for a siege by day: an eclipse needs the sun.
            if (fragment.event() != SiegeEvent.ECLIPSE || time < 12000) {
                currentEvent = fragment.event();
                save.specialNight = true;
                usedFragment = save.nightFragment;
                save.nightFragment = null;
            }
        }
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
                currentEvent == SiegeEvent.BLOOD_MOON || currentEvent == SiegeEvent.NETHER_RIFT
                        ? BossEvent.BossBarColor.RED
                        : currentEvent == SiegeEvent.FROST ? BossEvent.BossBarColor.BLUE
                        : currentEvent == SiegeEvent.ECLIPSE ? BossEvent.BossBarColor.WHITE
                        : BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setProgress(1.0f);
        active = true;
        combat.newSiege();
        defense.begin(world, anchor, config, save.coreSkills(), skillTree, digger);
        nightKills = 0;
        spawnedTotal = 0;
        nightLog.begin(world.getGameTime());
        digger.takeBroken();
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                logPlayer(player);
            }
        }
        killQuota = SiegeMath.killQuota(config.killQuotaShare, plannedWaves(), wantedPerWave());
        bonus.endAurora(server);
        events.begin(server, world, anchor, currentEvent, config);
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                SkillService.apply(player, save.skillsOf(player.getUUID().toString()), skillTree);
                handOutTraps(player);
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
        Mob champion = spawnMob(entityId, true);
        if (champion != null) {
            eliteId = champion.getUUID();
            liveMobs.add(eliteId);
            spawnedTotal++;
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
        Component title = save.specialNight
                ? Component.translatable("nightsiege.bar.title_special", siegeLevel, secondsLeft())
                : Component.translatable("nightsiege.bar.title", siegeLevel, secondsLeft());
        int required = killsRequired();
        if (required <= 0) {
            return title;
        }
        return Component.empty().append(title).append(Component.literal("  ·  "))
                .append(Component.translatable("nightsiege.bar.kills", nightKills, required)
                        .withStyle(nightKills >= required ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    /** Experience is worth more in a special night. */
    private int nightPoints(int amount) {
        return save.specialNight ? amount * config.specialNightXpMultiplier : amount;
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
                    logPlayer(player);
                    SkillService.apply(player, save.skillsOf(player.getUUID().toString()), skillTree);
                    handOutTraps(player);
                    joinNight(player);
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
        defense.tick(liveMobs, world.getGameTime());
        if (world.getGameTime() % 20 == 0) {
            for (UUID id : liveMobs) {
                Entity mob = world.getEntity(id);
                if (mob != null) {
                    nightLog.near(Math.sqrt(mob.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5)));
                }
            }
        }
        events.tick(server, world, anchor, currentEvent, participants, fallen, liveMobs, config, world.getGameTime());

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

    /** A defender joins this night: missed nights make every wave count more for them. */
    private void joinNight(ServerPlayer player) {
        if (testRun || nightNumber < 0) {
            return;
        }
        PlayerSkills skills = save.skillsOf(player.getUUID().toString());
        int weight = WaveXp.catchUp(nightNumber, skills.lastNight, config.catchUpMax);
        skills.lastNight = nightNumber;
        waveWeight.put(player.getUUID(), weight);
        if (weight > 1) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.catch_up", weight - 1, weight)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /** A wave is over: everyone still standing near the core has survived it. */
    private void waveSurvived(MinecraftServer server) {
        if (testRun || !config.skillsEnabled || world == null || anchor == null) {
            return;
        }
        double reach = config.participantRadius * 1.5;
        for (UUID id : participants) {
            if (fallen.contains(id)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.level() != world
                    || player.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5) > reach * reach) {
                continue;
            }
            int weight = waveWeight.getOrDefault(id, 1) * (save.specialNight ? config.specialNightXpMultiplier : 1);
            int points = WaveXp.survive(save.skillsOf(id.toString()), weight, config.wavesPerPoint,
                    config.pointGainMultiplier);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(points > 0
                    ? Component.translatable("nightsiege.msg.wave_point", wavesStarted, points).withStyle(ChatFormatting.AQUA)
                    : Component.translatable("nightsiege.msg.wave_held", wavesStarted).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Whether a defender dying now keeps the inventory (and is noted to get it back on respawn). */
    public boolean keepsInventory(ServerPlayer player) {
        if (!active || !config.keepInventoryInSiege || !participants.contains(player.getUUID())) {
            return false;
        }
        keptInventory.add(player.getUUID());
        return true;
    }

    /** Monsters one wave brings with the defenders there now. */
    private int wantedPerWave() {
        return (int) Math.round(SiegeMath.mobsPerBatch(siegeLevel, Math.max(1, participants.size()), config)
                * currentEvent.mobCountMultiplier(config) * (save.specialNight ? config.specialNightMobMultiplier : 1.0));
    }

    /** Waves the whole night brings: one at the start, then one per interval. */
    private int plannedWaves() {
        double extraDelay = skillTree.totalBonus(SkillEffect.WAVE_DELAY, save.coreSkills());
        // A wave starts the tick after its cooldown has run out, so waves lie one tick more apart.
        int interval = Math.max(20, (int) Math.round((config.batchIntervalSeconds + extraDelay) * 20.0)) + 1;
        return Math.max(1, (totalTicks - 1) / interval + 1);
    }

    /**
     * Kills the night asks for: the share of its monsters set in the config, never
     * more than that share of what can still come (a full field of monsters keeps
     * waves small, and then fewer kills are needed).
     */
    public int killsRequired() {
        return SiegeMath.killsRequired(config.killQuotaShare, killQuota, spawnedTotal,
                plannedWaves() - wavesStarted, wantedPerWave());
    }

    public int nightKills() {
        return nightKills;
    }

    private void spawnBatch(MinecraftServer server) {
        if (wavesStarted > 0) {
            waveSurvived(server);
        }
        wavesStarted++;
        int wanted = wantedPerWave();
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
            entityId = config.withoutEffects(events.substitute(currentEvent, entityId, config));
            BlockPos rift = events.riftForLast();
            if (MobFactory.isUnavailable(entityId)) {
                if (unavailableMobs.add(entityId)) {
                    NightSiegeMod.LOGGER.warn("[Night Siege] Mob {} nicht gefunden, wird uebersprungen", entityId);
                }
                continue;
            }
            Mob mob = rift != null
                    ? MobFactory.spawnAt(world, rift, entityId, siegeLevel, config, random, currentEvent, false)
                    : spawnMob(entityId, false);
            if (mob != null) {
                events.onSpawned(currentEvent, mob, world);
                liveMobs.add(mob.getUUID());
                spawnedTotal++;
                for (Entity rider : mob.getPassengers()) {
                    liveMobs.add(rider.getUUID());
                    spawnedTotal++;
                }
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

    /** Spawns a monster outside the walls of the base, or in the old ring if none was found. */
    private Mob spawnMob(String entityId, boolean elite) {
        entityId = config.withoutEffects(entityId);
        if (MobFactory.isUnavailable(entityId)) {
            return null;
        }
        BlockPos spot = defense.spawnSpot(config, random);
        if (spot == null) {
            spot = MobFactory.findSpawnSpot(world, anchor, config, random, 0.0);
        }
        return spot == null ? null
                : MobFactory.spawnAt(world, spot, entityId, siegeLevel, config, random, currentEvent, elite);
    }

    /** Playtest kits for every player around a spot. Returns how many got one. */
    public int giveKits(MinecraftServer server, ServerLevel level, BlockPos center, int siegeLevelWanted, boolean nightStarts) {
        int count = 0;
        for (ServerPlayer player : playersNear(server, level, center, config.participantRadius)) {
            TestKit.give(server, player, siegeLevelWanted, save.skillsOf(player.getUUID().toString()), skillTree, config, random,
                    nightStarts);
            count++;
        }
        persist();
        return count;
    }

    /** Server balancing: factor on the damage this player deals to monsters. */
    public float damageFactor(ServerPlayer player) {
        PlayerSkills skills = save.skillsOf(player.getUUID().toString());
        return (float) config.damageFactor(skills.chosenClass, skillTree.subclassOf(skills));
    }

    /** Server balancing: factor on the damage this player takes from monsters. */
    public float damageTakenFactor(ServerPlayer player) {
        PlayerSkills skills = save.skillsOf(player.getUUID().toString());
        return (float) config.damageTakenFactor(skills.chosenClass, skillTree.subclassOf(skills));
    }

    /** Notes a defender in the night's protocol, with class and subclass. */
    private void logPlayer(ServerPlayer player) {
        NightLog.Line line = nightLog.of(player.getUUID(), player.getScoreboardName());
        PlayerSkills skills = save.skillsOf(player.getUUID().toString());
        line.clazz = skills.chosenClass == null ? "" : skills.chosenClass.toLowerCase(java.util.Locale.ROOT);
        line.subclass = skillTree.subclassOf(skills);
    }

    /** Writes the night's protocol lines. */
    private void logNight(String result, int blasted, int required) {
        try {
            nightLog.dug += digger.takeBroken();
            nightLog.write(new NightLog.Night(nightNumber, siegeLevel, testRun, currentEvent.name().toLowerCase(java.util.Locale.ROOT),
                    save.specialNight, wavesStarted, spawnedTotal, nightKills, required, fallen.size(), result,
                    world == null ? 0L : world.getGameTime(), blasted));
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Nacht-Protokoll fehlgeschlagen: {}", e.toString());
        }
    }

    /** The rebuilding of the core tree mends the base; then the night's base is forgotten. */
    private void mendBase(MinecraftServer server) {
        int repaired = defense.restore(random);
        if (repaired > 0) {
            broadcast(server, Component.translatable("nightsiege.msg.rebuilt", repaired)
                    .withStyle(ChatFormatting.GREEN));
        }
        defense.end();
        BaseDefense.forgetScans();
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
        mendBase(server);

        int required = killsRequired();
        boolean quotaMet = nightKills >= required;
        if (testRun) {
            logNight(won ? "test_gehalten" : "test_verloren", 0, required);
            testRun = false;
            broadcast(server, Component.translatable(won ? "nightsiege.msg.test_won" : "nightsiege.msg.test_lost")
                    .withStyle(ChatFormatting.GRAY));
            if (won && required > 0) {
                broadcast(server, Component.translatable(quotaMet ? "nightsiege.msg.quota_met" : "nightsiege.msg.quota_missed_test",
                        nightKills, required).withStyle(ChatFormatting.GRAY));
            }
            clearSiegeState();
            return;
        }

        save.highestLevel = Math.max(save.highestLevel, siegeLevel);
        String result = won ? (quotaMet ? "gewonnen" : "ziel_verfehlt") : "verloren";
        int blasted = 0;
        if (won && !quotaMet) {
            // Held, but too few monsters fell: the night comes again at the same level.
            save.currentStreak = 0;
            waveSurvived(server);
            if (save.specialNight && usedFragment != null) {
                save.nightFragment = usedFragment;   // the special night comes again too
            }
            broadcast(server, Component.translatable("nightsiege.msg.quota_missed", nightKills, required, siegeLevel)
                    .withStyle(ChatFormatting.RED));
        } else if (won) {
            save.siegesWon++;
            save.currentStreak++;
            save.bestStreak = Math.max(save.bestStreak, save.currentStreak);
            waveSurvived(server);
            awardSkillPoints(server);
            if (config.dungeonsEnabled) {
                rewardDungeonMaps(server);
            }
            if (save.specialNight && world != null && anchor != null) {
                SpecialNight.lootChest(world, anchor, random);
                broadcast(server, Component.translatable("nightsiege.msg.special_loot").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            int next = SiegeMath.level(save.siegesWon, lastKnownDay, config);
            if (required > 0) {
                broadcast(server, Component.translatable("nightsiege.msg.quota_met", nightKills, required)
                        .withStyle(ChatFormatting.GREEN));
            }
            broadcast(server, Component.translatable("nightsiege.msg.won", next)
                    .withStyle(ChatFormatting.GREEN));
        } else {
            save.siegesLost++;
            save.currentStreak = 0;
            int destroyed = 0;
            if (world != null && anchor != null) {
                destroyed = Demolition.blast(world, anchor, config);
            }
            blasted = destroyed;
            broadcast(server, Component.translatable("nightsiege.msg.lost", destroyed)
                    .withStyle(ChatFormatting.DARK_RED));
            loseSkills(server);
            broadcast(server, Component.translatable("nightsiege.msg.night_again", siegeLevel)
                    .withStyle(ChatFormatting.RED));
        }

        logNight(result, blasted, required);
        save.specialNight = false;
        scheduler.markSiegeFinished(random, config);
        syncSchedulerToSave();
        persist();
        clearSiegeState();
    }

    /** After a won siege every defender has a one in three chance of a dungeon map. */
    private void rewardDungeonMaps(MinecraftServer server) {
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && random.nextInt(3) == 0) {
                player.getInventory().placeItemBackInInventory(new ItemStack(DungeonContent.MAP));
                player.sendSystemMessage(Component.translatable("nightsiege.dungeon.map_reward")
                        .withStyle(ChatFormatting.AQUA));
            }
        }
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
        mendBase(server);
        logNight("abgebrochen", 0, killsRequired());
        testRun = false;
        broadcast(server, Component.translatable("nightsiege.msg.aborted").withStyle(ChatFormatting.GRAY));
        clearSiegeState();
    }

    private void clearSiegeState() {
        trapsHanded.clear();
        spawnBlockWorld = null;
        if (world != null) {
            events.end(world.getServer(), world, currentEvent);
        }
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

    /** A lost night: every defender loses the newest learned skills, the points spent on them with them. */
    private void loseSkills(MinecraftServer server) {
        if (config.defeatSkillLoss <= 0) {
            return;
        }
        for (UUID id : participants) {
            PlayerSkills skills = save.skillsOf(id.toString());
            List<String> gone = skillTree.loseLast(skills, config.defeatSkillLoss);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || gone.isEmpty()) {
                continue;
            }
            net.minecraft.network.chat.MutableComponent names = Component.empty();
            for (int i = 0; i < gone.size(); i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append(Component.translatable("nightsiege.skill." + gone.get(i)));
            }
            player.sendSystemMessage(Component.translatable("nightsiege.msg.skills_lost", names)
                    .withStyle(ChatFormatting.RED));
        }
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
        int amount = nightPoints(config.skillPointsPerSiege + siegeLevel / Math.max(1, config.skillPointsLevelStep));
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
        if (active && entity instanceof Mob killed && liveMobs.contains(killed.getUUID())) {
            nightKills++;
        }
        if (active && entity instanceof Mob mob && mob.getUUID().equals(eliteId)) {
            onEliteKilled(mob);
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        dungeons.onPlayerDeath(player);
        UUID id = player.getUUID();
        if (!active || !participants.contains(id)) {
            return;
        }
        fallen.add(id);
        nightLog.of(id, player.getScoreboardName()).deaths++;
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
        events.forget(player.getUUID());
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
    /** Players whose skills hold outside a siege because they carry their class item. */
    private final Set<UUID> gearBoosted = new HashSet<>();

    /** Applies or lifts the attribute skills of players who carry their class item. */
    private void tickClassGear(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            boolean defending = active && participants.contains(id) && !fallen.contains(id);
            if (defending) {
                gearBoosted.remove(id);   // the siege looks after these
                continue;
            }
            PlayerSkills own = effectiveSkills(player);
            if (ClassGear.carries(player, own, skillTree)) {
                SkillService.apply(player, own, skillTree);
                gearBoosted.add(id);
            } else if (gearBoosted.remove(id)) {
                SkillService.clear(player);
            }
        }
    }

    /** Tells every player with the mod what their ultimate slot shows. */
    private void sendUltHud(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, CoreNetworking.UltHudPayload.TYPE)) {
                continue;
            }
            PlayerSkills own = effectiveSkills(player);
            com.mki.nightsiege.core.SkillNode node = skillTree.ultimateOf(own);
            com.mki.nightsiege.core.UltState state = new com.mki.nightsiege.core.UltState();
            if (node != null) {
                int rank = Math.max(1, own.levelOf(node.id()));
                state.has = true;
                state.node = node.id();
                state.icon = node.icon();
                state.totalTicks = combat.cooldownTotal(player.getUUID(), CombatEffects.cooldownFor(rank));
                state.cooldownTicks = combat.cooldownLeft(player.getUUID(), now);
                state.live = liveSkillsFor(player) != null;
            }
            ServerPlayNetworking.send(player, new CoreNetworking.UltHudPayload(Json.toJson(state)));
        }
    }

    /** Called by {@link TrapBlock} once a trap is set down. */
    public void onTrapPlaced(ServerLevel level, BlockPos pos, LivingEntity placer, TrapKind kind) {
        traps.onPlaced(level, pos, placer, kind);
        persist();
    }

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
        if (traps.onBroken(brokenWorld, pos)) {
            persist();
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
    private InteractionResult onUseBlock(Object playerObject, Object levelObject, Object handObject, Object hitObject) {
        if (!(levelObject instanceof ServerLevel serverLevel)
                || !(playerObject instanceof ServerPlayer player)
                || !(hitObject instanceof BlockHitResult hit)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hit.getBlockPos();
        ItemStack held = handObject instanceof InteractionHand hand ? player.getItemInHand(hand) : ItemStack.EMPTY;
        if (traps.onUse(serverLevel, pos, player, held)) {
            trapsDirty = true;
            return InteractionResult.SUCCESS;
        }
        TrapKind placing = ModContent.trapOf(held);
        Component blocked = placing == null ? null : traps.placeBlocked(player, placing);
        if (blocked != null) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(blocked));
            return InteractionResult.FAIL;
        }
        if (handObject == InteractionHand.MAIN_HAND && dungeons.onUse(serverLevel, pos, player)) {
            return InteractionResult.SUCCESS;
        }
        if (!ModContent.isCore(serverLevel.getBlockState(pos))) {
            return InteractionResult.PASS;
        }
        if (insertNightFragment(player, held, pos)) {
            persist();
            return InteractionResult.SUCCESS;
        }
        if (perks.insert(player, held, pos)) {
            persist();
            return InteractionResult.SUCCESS;
        }
        openCoreWindow(player);
        return InteractionResult.SUCCESS;
    }

    /**
     * A night fragment in hand on the main core: the next siege becomes a special
     * night of its event. One at a time; returns true when the click is handled.
     */
    private boolean insertNightFragment(ServerPlayer player, ItemStack held, BlockPos pos) {
        NightFragment kind = held.isEmpty() ? null : DungeonContent.nightFragmentKind(held.getItem());
        if (kind == null) {
            return false;
        }
        NightFragment set = NightFragment.byName(save.nightFragment);
        if (active) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                    Component.translatable("nightsiege.fragment.busy").withStyle(ChatFormatting.YELLOW)));
            return true;
        }
        if (set != null) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                    Component.translatable("nightsiege.fragment.already",
                            Component.translatable("item.nightsiege.night_fragment_" + set.id())).withStyle(ChatFormatting.YELLOW)));
            return true;
        }
        save.nightFragment = kind.name();
        // An event already rolled for the coming siege gives way to the fragment.
        if (save.pendingEvent != null) {
            save.pendingEvent = kind.event().name();
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.08);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.5, 0.5, 0.5, 0.8);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 0.7f);
        Component text = Component.translatable("nightsiege.fragment.set", player.getDisplayName(),
                Component.translatable("item.nightsiege.night_fragment_" + kind.id()),
                Component.translatable("nightsiege.event." + kind.event().name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(text);
        }
        return true;
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
        if (player.level() instanceof ServerLevel here) {
            PlayerSkills own = save.skillsOf(player.getUUID().toString());
            ClassTags.update(here.getServer(), player, own, skillTree.subclassOf(own));
        }
        if (ServerPlayNetworking.canSend(player, CoreNetworking.CoreUiPayload.TYPE)) {
            StatsService.sendTo(player, this);
        } else {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.mod_required"));
        }
    }

    /** Puts every player's subclass (or class) after their name. */
    private void refreshClassTags(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerSkills own = save.skillsOf(player.getUUID().toString());
            ClassTags.update(server, player, own, skillTree.subclassOf(own));
        }
    }

    /** Damage a guardian caught and healing given, until it adds up to a point. */
    private final Map<UUID, Double> tankPool = new HashMap<>();

    /**
     * A guardian caught damage from an attacker (or healed a teammate, from null):
     * every so much of it is worth one kill. Only during a real siege, and only
     * the siege's own monsters count.
     */
    /** Damage for the night's protocol: dealt to siege monsters, taken by defenders. */
    private void logDamage(Entity entity, net.minecraft.world.damagesource.DamageSource source, float taken) {
        if (!active || taken <= 0) {
            return;
        }
        if (source.getEntity() instanceof ServerPlayer attacker && entity.entityTags().contains(MobFactory.TAG)) {
            nightLog.of(attacker.getUUID(), attacker.getScoreboardName()).dealt += taken;
        }
        if (entity instanceof ServerPlayer victim && participants.contains(victim.getUUID())) {
            nightLog.of(victim.getUUID(), victim.getScoreboardName()).taken += taken;
        }
    }

    private void creditTank(ServerPlayer player, double amount, Entity from) {
        if (active && from == null && amount > 0) {
            nightLog.of(player.getUUID(), player.getScoreboardName()).healed += amount;
        }
        if (!active || testRun || !config.skillsEnabled || config.skillPointsPerKill <= 0 || amount <= 0) {
            return;
        }
        if (from != null && !from.entityTags().contains(MobFactory.TAG)) {
            return;
        }
        double pool = tankPool.merge(player.getUUID(), amount, Double::sum);
        int per = Math.max(1, config.tankDamagePerPoint);
        if (pool >= per) {
            int points = (int) (pool / per);
            tankPool.put(player.getUUID(), pool - points * per);
            save.skillsOf(player.getUUID().toString()).award(nightPoints(perks.experience(player, points * config.skillPointsPerKill)));
        }
    }

    /** Counts a kill and turns it into skill experience. */
    private void onKill(Object killerObject, Object killedObject) {
        if (!(killerObject instanceof ServerPlayer player) || !(killedObject instanceof Entity killed)) {
            return;
        }
        combat.onKill(player, killed);
        if (!killed.entityTags().contains(MobFactory.TAG)) {
            return;
        }
        if (active) {
            nightLog.playerKills++;
            nightLog.of(player.getUUID(), player.getScoreboardName()).kills++;
        }
        if (testRun) {
            return;   // Test sieges count for nothing.
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
            save.skillsOf(player.getUUID().toString()).award(nightPoints(perks.experience(player, champion
                    ? config.skillPointsPerKill * config.championPointMultiplier
                    : config.skillPointsPerKill)));
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
            case "classreset" -> onClassReset(player);
            case "donate" -> onDonate(player, parseAmount(argument));
            case "dev" -> onDevAction(player, argument);
            case "ult" -> onUltimateKey(player);
            case "shield" -> combat.guardian().onShieldAction(player);
            case "dungeon" -> dungeons.onToolAction(player, argument);
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
            rewardMaxedUltimate(player, own, node);
            persist();
            // Skills only hold during a siege, so apply at once only if one runs.
            if (active && participants.contains(player.getUUID()) && !fallen.contains(player.getUUID())) {
                SkillService.apply(player, own, skillTree);
            }
        }
        openCoreWindow(player);
    }

    /** Maxing the ultimate of one's path pays out the infernite upgrade template, once. */
    private void rewardMaxedUltimate(ServerPlayer player, PlayerSkills own, com.mki.nightsiege.core.SkillNode node) {
        if (node.effect().target() != SkillEffect.Target.ULTIMATE || own.templateGiven
                || own.levelOf(node.id()) < node.maxLevel()) {
            return;
        }
        own.templateGiven = true;
        player.getInventory().placeItemBackInInventory(new ItemStack(InferniteContent.TEMPLATE));
        player.sendSystemMessage(Component.translatable("nightsiege.msg.template_earned")
                .withStyle(ChatFormatting.GOLD));
        Fx.pointsEarned(player.level().getServer(), config);
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
            traps.giveRemote(player, own);
            player.sendSystemMessage(Component.translatable("nightsiege.msg.class_chosen",
                    Component.translatable(chosen.translationKey())).withStyle(ChatFormatting.GREEN));
        }
        openCoreWindow(player);
    }

    /** The one free class change at the core: class open again, all points back. */
    public void onClassReset(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        if (!own.hasClass()) {
            openCoreWindow(player);
            return;
        }
        if (own.classResetUsed) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.class_reset_used")
                    .withStyle(ChatFormatting.RED));
            openCoreWindow(player);
            return;
        }
        own.changeClass();
        SkillService.clear(player);
        persist();
        player.sendSystemMessage(Component.translatable("nightsiege.msg.class_reset_done", own.points())
                .withStyle(ChatFormatting.GREEN));
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
