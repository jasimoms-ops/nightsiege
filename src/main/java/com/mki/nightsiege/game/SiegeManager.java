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
import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.SiegeRhythm;
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
import net.minecraft.network.chat.MutableComponent;
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
                && !fallen.contains(player.getUUID()) || camp.isFighter(player) || expeditions.isFighter(player);
        PlayerSkills own = effectiveSkills(player);
        if (!defending && !player.getAbilities().instabuild) {   // the infernite class items are gone (29 October 2026)
            return null;
        }
        return own;
    }

    /** The skills that count for this player: their own (the creative path trial went with the class items). */
    PlayerSkills effectiveSkills(ServerPlayer player) {
        return save.skillsOf(player.getUUID().toString());
    }

    /** Ultimate key: fires the ultimate. */
    private void onUltimateKey(ServerPlayer player) {
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
    private final KitService kits = new KitService(() -> save, () -> config, skillTree, traps, this::persist);
    private final CrateService crates = new CrateService(() -> save, () -> config, skillTree, this::persist);
    /** The summoner's wolves, golems and spirits. */
    private final Companions companions = new Companions(skillTree, this::liveSkillsFor, () -> config);
    private final CombatEffects combat = new CombatEffects(skillTree, this::liveSkillsFor,
            BossService::isBoss, archer, traps);
    /** The boss of a boss night (every 6th siege, Simon 25 October 2026; the champion night is gone). */
    private final BossService bosses = new BossService();
    /**
     * The boss rhythm (Simon 29 October 2026): the "Vierer-Nacht" on nights 20 and 40 brings the four
     * bosses of the boss nights before it, two at a time, with no waves and no clock; won, every
     * defender gets the crown. From night 41 every 2nd night two bosses come at once, five minutes in.
     */
    private boolean finaleNight;
    /** A late boss night (after night 40): its bosses come five minutes in, waves before. */
    private boolean lateNight;
    /** What tonight is in the boss rhythm (counted at its end). */
    private com.mki.nightsiege.core.BossPlan.NightKind tonightKind = com.mki.nightsiege.core.BossPlan.NightKind.NORMAL;
    /** The bosses of the Vierer-Nacht or the late night that came so far (one service each). */
    private final List<BossService> finaleBosses = new ArrayList<>();
    /** Their bosses still to come, in their order. */
    private final List<com.mki.nightsiege.core.BossKind> finaleQueue = new ArrayList<>();
    /** When the next pair of the Vierer-Nacht comes (game time), or -1 before the first is timed. */
    private long finaleNextAt = -1L;
    private int finaleDown;
    /** What the next test night is (/nightsiege test <level> vierer | doppel), or null. */
    private com.mki.nightsiege.core.BossPlan.NightKind testKind;
    /** A dungeon warden at the end of a normal night (Simon, 29 October 2026: 20 %). */
    private final WardenNight warden = new WardenNight();
    /** The warden a test night brings (set by /nightsiege test <level> waechter, taken at its start). */
    private com.mki.nightsiege.core.ExpeditionKind testWarden;
    /** The war camp before a siege and the siege machines of the night after it (27 October 2026). */
    private final SiegeCamp camp = new SiegeCamp(this);
    private final SiegeMachines machines = new SiegeMachines();
    /** Night Siege's own dungeons, reported by the scouts (dungeon update, 27 October 2026). */
    private final Expeditions expeditions = new Expeditions(this);
    /** The playtest (Simon, 28 October 2026): four nights of rising level along planned paths. */
    private final Playtest playtest = new Playtest(this);
    /** The statistics logger for the December playtest (28 October 2026): files on the server only. */
    private final StatsRecorder stats = new StatsRecorder(this);
    private final Digger digger = new Digger();
    private final LadderClimb ladders = new LadderClimb();
    /** Ladder carriers and sapper creepers. */
    private final Besiegers besiegers = new Besiegers();
    /** The new attackers of update 1 (Simon, 27 October 2026). */
    private final Foes foes = new Foes();
    private final BaseDefense defense = new BaseDefense();
    /** The raid's plan: directions, gathering in front of the wall, ways in (27 October 2026). */
    private final RaidPlan raid = new RaidPlan();
    /** Update 2 "Fluch & Rache": the nemeses, and the curse stones laid on tonight's altar. */
    private final Nemesis nemesis = new Nemesis();
    private int nightCurses;
    /** The night's protocol for balancing (config/nightsiege-stats*.csv). */
    private final NightLog nightLog = new NightLog();
    private final EventEffects events = new EventEffects();

    /** The event looks sent to the players (the dungeons send their own and hand back). */
    EventEffects events() {
        return events;
    }
    private final BonusEvents bonus = new BonusEvents(() -> save, () -> config);
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
    private BlockPos spawnBlockCenter;
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
        com.mki.nightsiege.core.BossKind boss;

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
    /** Tick at which a boss night won by the boss's fall ends (a few seconds of triumph), or -1. */
    private long bossVictoryAt = -1L;
    /** "boss_<id>" for the night's protocol and statistics window of a boss night, else null. */
    private String bossLabel;
    /** The boss a test night brings (set by /nightsiege test <level> boss, taken at its start). */
    private com.mki.nightsiege.core.BossKind testBoss;
    /** The next test night brings the siege machines (a playtest's camp won), taken at its start. */
    private boolean testMachines;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> fallen = new HashSet<>();
    private final Set<UUID> liveMobs = new HashSet<>();
    /** Vexes and phantoms: high-standing defenders first (Simon, 26 October 2026). */
    private final SkyHunters skyHunters = new SkyHunters();

    private SiegeManager() {
    }

    public SiegeConfig config() {
        return config;
    }

    public SiegeSave save() {
        return save;
    }

    public Playtest playtest() {
        return playtest;
    }

    public Expeditions expeditions() {
        return expeditions;
    }

    public StatsRecorder stats() {
        return stats;
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
        if (!active) {
            int playtestLevel = playtest.level();
            if (playtestLevel > 0) {
                return playtestLevel;   // camp, dungeon and kits of a playtest go by its level
            }
        }
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
        combat.setArrowBonus(defense::arrowBonus);   // the core perks are gone (content update, 29 October 2026)
        traps.setTeslaBoost(() -> active && currentEvent == SiegeEvent.THUNDER ? config.thunderTeslaMultiplier : 1.0);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TeamSim.stop(server);
            SimBots.removeAll(server);
            safe("Dungeons", () -> expeditions.endAll(server, null));   // kits back, the arena's gate open
            traps.releaseAll(server);   // gunners out of the towers, the seats go
            Drones.stopAll(server);
            if (active) {
                events.end(server, world, currentEvent);
            }
            EventAtmosphere.resetServer();
            syncSchedulerToSave();
            persist();
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            onEndTick(server);
            noteTickLoad();
        });
        NightDamage.exclude(camp::covers);   // the war camp is not the night's to mend
        // Object parameters keep these handlers compiling across Fabric versions.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onDeath(entity, source));
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
            CrateEffects.afterDamage(entity, source);
            Wounds.afterDamage(entity, source, taken);
            safe("Abstand", () -> RangedGuard.afterDamage(this, entity, source, taken));
            safe("Boss-Treffer", () -> {
                for (BossService b : allBosses()) {
                    b.afterHit(entity, source, blocked);
                }
            });
            GainGear.afterDamage(entity, source, taken);
            MeleeSim.onDamage(entity, source, taken);
            safe("Begleiter-Treffer", () -> companions.afterDamage(entity, source, taken));
            safe("Gegner-Treffer", () -> foes.afterDamage(entity, source));
            safe("Tank-Brecher", () -> foes.afterHit(foesHost, entity, source, blocked));
            machines.afterDamage(entity, source, taken);
            expeditions.afterDamage(entity, source, taken);
            safe("Ballista-Treffer", () -> machines.afterBoltHit(entity, source, blocked));
            safe("Wächter-Treffer", () -> ChampionStrikes.afterHit(this, entity, source, blocked));   // balance update 2 (29.10.)
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!camp.allowDeath(entity, source) || !machines.allowDeath(entity, source) || !expeditions.allowDeath(entity)) {
                return false;   // a fighter at the camp or in a dungeon, the camp's workbench or a machine's hit box
            }
            boolean dies = combat.allowDeath(entity) && allowNightDeath(entity);
            if (dies && !config.siegeMobDrops) {
                NightDrops.onDeath(entity);   // siege monsters drop nothing (Simon, 26 October 2026)
            }
            return dies;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !infighting(source.getEntity(), entity) && allowModDamage(entity, source.getEntity()) && combat.allowDamage(entity, source, amount)
                        && CrateEffects.allowDamage(entity, source) && Companions.allowDamage(entity, source)
                        && bossesAllowDamage(entity, source, amount)
                        && camp.allowDamage(entity, source) && machines.allowDamage(entity, source)
                        && expeditions.allowDamage(entity, source)
                        && foes.allowDamage(entity, source)
                        && FriendlyFire.allow(entity, source));   // no friendly fire in the night (27 October 2026)
        combat.guardian().setCredit(this::creditTank);
        traps.setMarker(combat::remoteMark);
        Digger.hardness = traps::digFactor;
        RemoteItem.handler = traps::useRemote;
        SummonStaffItem.handler = companions::useStaff;
        // A drone uses nothing, places nothing and hits nothing; its crossbow is the attack button.
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) ->
                Drones.isDrone(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                Drones.isDrone(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                Drones.isDrone(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                Drones.isDrone(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
                Drones.isDrone(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        // Crate gadgets (B2): thrown, blown, placed or tied to an ally.
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(GainGear::onUseItem);
        KitGuard.register();   // kit items go into no vase, frame, stand or bundle (balance update 2, 29.10.)
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(GainGear::onUseBlock);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                GainGear.onUseEntity(player, world, hand, entity));
        // Siege machines are sabotaged by hand: hold right-click at one (Simon, 27 October 2026).
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()) {
                return SiegeMachines.looksLikeMachine(entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return player instanceof ServerPlayer server && hand == net.minecraft.world.InteractionHand.MAIN_HAND
                    && machines.onUse(server, entity, config) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                !world.isClientSide() && player instanceof ServerPlayer server
                        && combat.guardian().onUseEntity(server, entity, hand)
                        ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS);
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.entityTags().contains(Rigs.TAG) && !Rigs.isLive(entity)) {
                entity.discard();   // a piece of a tower rig or a drone from before a restart
                return;
            }
            if (entity.entityTags().contains(TrapService.SEAT_TAG) && !traps.isLiveSeat(entity)) {
                entity.discard();   // the seat of a manned tower from before a restart
                return;
            }
            if (RetiredItems.strayDrop(entity)) {
                entity.discard();   // infernite or an old dungeon's thing on the ground
                return;
            }
            if (NightDrops.isLoot(entity)) {
                entity.discard();   // loot of a siege monster
                return;
            }
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity drop && KitService.isKit(drop.getItem())) {
                drop.discard();   // a dropped kit item is gone; the kit comes again next night
                return;
            }
            if (camp.stray(entity) || SiegeMachines.stray(entity) || Expeditions.stray(entity)) {
                entity.discard();   // a guard, workbench, machine part or crew from a camp or night that is over, a dungeon monster
                return;
            }
            if (Foes.isStale(entity)) {
                entity.discard();   // a grave or a treasure raider from before a restart
                return;
            }
            if (BossService.isBossOrPart(entity) && !bosses.planned() && !finaleNight && !lateNight) {
                entity.discard();   // left from a night that ended without us (a crash)
                return;
            }
            archer.onEntityLoad(entity, world, skillTree, this::liveSkillsFor);
            CrateEffects.onArrow(entity);
            GainGear.onArrowLoad(entity);
            CopperHelper.onLoad(entity);
            Companions.onEntityLoad(entity);
        });
        PlayerBlockBreakEvents.BEFORE.register(
                (level, player, pos, state, blockEntity) -> allowBreak(player, state));
        // The war camp is not a quarry: its blocks stay (they go with the camp).
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                player.getAbilities().instabuild || !camp.protects(level, pos) && !expeditions.protects(level, pos));
        // Night-only blocks (barricades, the war banner, ladders, light) go without drops.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel && TempBlocks.isTemp(serverLevel, pos)) {
                TempBlocks.remove(serverLevel, pos);
                return false;
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register(
                (level, player, pos, state, blockEntity) -> {
                    if (ModContent.isCore(state) && player instanceof ServerPlayer breaker) {
                        // The core goes into the admin's hand with all its progress (it lives in the save).
                        breaker.getInventory().placeItemBackInInventory(new ItemStack(ModContent.SIEGE_CORE_ITEM));
                        breaker.sendSystemMessage(Component.translatable("nightsiege.msg.core_taken")
                                .withStyle(ChatFormatting.GOLD));
                    }
                    onBlockBroken(level, pos);
                });
        RetiredItems.register();   // infernite and the old dungeons' things go (content update, 29 October 2026)
        UseBlockCallback.EVENT.register(
                (player, level, hand, hitResult) -> onUseBlock(player, level, hand, hitResult));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> SiegeCommands.register(dispatcher));
    }

    private void onServerStarted(MinecraftServer server) {
        EventAtmosphere.resetServer();
        stats.attach(server);
        savePath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve(SAVE_FILE).normalize();
        migrateSharedSave();
        reload();
        TempBlocks.init(() -> save, this::persist);
        if (!active) {
            releaseNight(server);   // the clock stood still when the server went down in a siege
            int left = TempBlocks.clearAll(server);
            if (left > 0) {
                NightSiegeMod.LOGGER.info("[Night Siege] {} Nacht-Blöcke von einem Absturz aufgeräumt", left);
            }
        }
        playtest.afterRestart(server);   // a playtest that ran when the server went down is undone
        NightSiegeMod.LOGGER.info("[Night Siege] {} Cores bekannt, {} Belagerungen bestanden",
                save.cores == null ? 0 : save.cores.size(), save.siegesWon);
        // CI only: the balance tool's team simulation (29 October 2026).
        if (System.getenv("NIGHTSIEGE_TEAMSIM") != null) {
            TeamSim.startFromEnv(server);
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
        com.mki.nightsiege.core.SkillValues.apply(config.skillTweaks);
        if (tweaked > 0) {
            NightSiegeMod.LOGGER.info("[Night Siege] {} Skills mit Server-Werten", tweaked);
        }
        Json.write(configPath, config);
        DungeonContent.active = config.dungeonsEnabled && config.legacyDungeons;   // the caravan's old maps

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
        int retired = save.moveRetiredNodes();
        if (retired > 0) {
            NightSiegeMod.LOGGER.info("[Night Siege] Seelenfänger umgebaut: {} Spieler behalten ihre Punkte im Nachfolger-Talent", retired);
            persist();
        }
        boolean oldBalance = save.balanceVersion < SiegeSave.BALANCE_VERSION;
        int restarted = save.migrateBalance();
        if (oldBalance) {
            NightSiegeMod.LOGGER.info("[Night Siege] Neues Balancing: {} Spieler starten mit 0 Punkten neu", restarted);
            persist();
        }
        // Profiles (26 October 2026): an ult at level 3 from before earns its unlock now.
        int caughtUp = 0;
        boolean movedToProfiles = false;
        if (save.skills != null) {
            for (PlayerSkills own : save.skills.values()) {
                if (own != null && own.profileVersion < PlayerSkills.PROFILE_VERSION) {
                    int before = own.unlocksFree;
                    skillTree.migrateProfiles(own);
                    caughtUp += Math.max(0, own.unlocksFree - before);
                    movedToProfiles = true;
                }
            }
        }
        if (movedToProfiles) {
            NightSiegeMod.LOGGER.info("[Night Siege] Profile eingeführt: {} Freischaltungen nachgereicht", caughtUp);
            persist();
        }
        // Balance update 2 (29 October 2026): crate gains that left the crates pay their duplicate points back once.
        if (!save.retiredGainsPaid) {
            int paid = save.payRetiredGains();
            save.retiredGainsPaid = true;
            NightSiegeMod.LOGGER.info("[Night Siege] Entfernte Kisten-Gewinne zurückgezahlt: {} Punkte", paid);
            persist();
        }
        // The alchemist's potions became nodes, three of seven (28 October 2026): once per save.
        if (!save.brewsMigrated) {
            int brewProfiles = 0;
            if (save.skills != null) {
                for (PlayerSkills own : save.skills.values()) {
                    brewProfiles += skillTree.migrateBrews(own);
                }
            }
            save.brewsMigrated = true;
            NightSiegeMod.LOGGER.info("[Night Siege] Alchemisten-Tränke als Knoten: {} Profile angepasst", brewProfiles);
            persist();
        }
        // The subclass first, its level 1 free (Simon, 29 October 2026): once per player, see migrateFreeSubclasses.
        migrateFreeSubclasses();
        scheduler.setReadyTicks(save.readyTicks);
        scheduler.setRequiredTicks(save.requiredTicks);
        if (scheduler.requiredTicks() <= 0L && !save.cores.isEmpty()) {
            scheduler.rollInterval(random, config);
        }

        pool = new MobPool(config.mobs);
        unavailableMobs.clear();
        TempBlocks.rebuildIndex();
    }

    /**
     * Level 1 of the subclass node is free since 29 October 2026: every player who bought it gets its
     * points back once, every profile with a fixed subclass that never learned it gets it for free
     * (see SkillTree#migrateFreeSubclass), also the skills a running playtest keeps for afterwards.
     */
    private void migrateFreeSubclasses() {
        int players = 0;
        int points = 0;
        if (save.skills != null) {
            for (PlayerSkills own : save.skills.values()) {
                if (own != null && own.freeSubclassVersion < PlayerSkills.FREE_SUBCLASS_VERSION) {
                    points += skillTree.migrateFreeSubclass(own);
                    players++;
                }
            }
        }
        if (save.playtest != null && save.playtest.players != null) {
            for (SiegeSave.PlaytestPlayer pp : save.playtest.players.values()) {
                PlayerSkills kept = pp == null || pp.skills == null || pp.skills.isEmpty() ? null
                        : Json.fromJson(pp.skills, PlayerSkills.class);
                if (kept != null && kept.freeSubclassVersion < PlayerSkills.FREE_SUBCLASS_VERSION) {
                    points += skillTree.migrateFreeSubclass(kept);
                    pp.skills = Json.toJson(kept);
                    players++;
                }
            }
        }
        if (players > 0) {
            NightSiegeMod.LOGGER.info("[Night Siege] Unterklasse zuerst, Stufe 1 geschenkt: {} Spieler, {} Punkte zurück",
                    players, points);
            persist();
        }
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

    /** When the running server tick began (for the load in the logger, 30.10.). */
    private long tickStartNanos;
    private int loadTicks;

    /** The night's load for the logger: the tick's time, the monsters alive, once a second all mobs. */
    private void noteTickLoad() {
        if (!active || world == null || tickStartNanos == 0L) {
            return;
        }
        double ms = (System.nanoTime() - tickStartNanos) / 1_000_000.0;
        int all = -1;
        if (++loadTicks % 20 == 0) {
            all = 0;
            for (Entity e : world.getAllEntities()) {
                if (e instanceof Mob) {
                    all++;
                }
            }
        }
        int mobsAll = all;
        safe("Statistik", () -> stats.tickLoad(ms, liveMobs.size(), mobsAll));
    }

    private void onEndTick(MinecraftServer server) {
        tickModCheck(server);
        safe("Wächter-Schutz", () -> TankGuard.tick(server, config,
                p -> skillTree.subclassOf(save.skillsOf(p.getUUID().toString()))));
        safe("Reparatur", () -> NightDamage.tick(server));
        MeleeSim.tick(server);
        TeamSim.tick(server);
        TrapService.clearHitting();
        trapsDirty |= traps.tick(server);
        TrapService.clearHitting();
        safe("Drohnen", () -> Drones.tick(server));
        safe("Spinnen-Test", () -> SpiderOverhang.tickWatched(server));
        combat.tick(server);
        // Companions stay with summoners who defend the night (or try them out in creative).
        safe("Begleiter", () -> companions.tick(server, p -> isDefending(p) || p.getAbilities().instabuild));
        safe("Kisten-Effekt", () -> CrateFx.tick(server));
        EventAtmosphere.tickServer();
        BaseDefense.tickHighlights(server);
        if (server.overworld().getGameTime() % 100 == 0) {
            refreshClassTags(server);
        }
        if (server.overworld().getGameTime() % 20 == 0) {
            events.syncClients(server);
            bonus.tick(server, active, siegeDueToday(server));
        }
        if (server.overworld().getGameTime() % 20 == 0) {
            sendUltHud(server);
            safe("Lebensanzeige", () -> HealthView.tick(server, this));
        }
        if (trapsDirty && ++trapSaveCounter >= 200) {
            trapSaveCounter = 0;
            trapsDirty = false;
            persist();
        }

        long gameTime = server.overworld().getGameTime();
        lastKnownDay = SiegeMath.dayOf(gameTime);
        safe("Playtest", () -> playtest.tick(server));

        if (!active) {
            safe("Heerlager", () -> camp.tick(server));
            safe("Dungeons", () -> expeditions.tick(server));
            safe("Maschinen-Probe", () -> machines.tickTrials(server, config, gameTime));
        }
        if (active) {
            tickSiege(server);
            return;
        }
        if (save.clockHeld) {
            releaseNight(server);   // no siege, no held night
        }
        if (pendingTest != null) {
            tickPreparation(server);
            return;
        }
        if (playtest.running()) {
            return;   // the real nights wait while a playtest runs
        }

        tickRhythm(server);
        if (server.overworld().getGameTime() % 20 == 0) {
            tickComingNight(server);
        }
    }

    /**
     * Between the horn and the start: defenders who accepted and come to the core
     * get their kit and the class window.
     */
    private void tickComingNight(MinecraftServer server) {
        if (!nightComing(server)) {
            return;
        }
        CoreRecord core = mainCore();
        ServerLevel level = core == null ? null : levelByName(server, core.dimension);
        if (level == null) {
            return;
        }
        for (ServerPlayer player : defendersNear(server, level, new BlockPos(core.x, core.y, core.z), false)) {
            if (config.nightKits && !kits.hasStash(player)) {
                equipKit(player);
                sendClassPick(player, secondsToStart(server));
            }
        }
    }

    /** Seconds until tonight's siege starts (0 when it is not coming). */
    private int secondsToStart(MinecraftServer server) {
        if (pendingTest != null) {
            return Math.max(0, pendingTest.ticksLeft / 20);
        }
        if (!nightComing(server)) {
            return 0;
        }
        return Math.max(0, (startTime() - clockTime(server)) / 20);
    }

    /** At the horn: everyone who accepted gets the class window (wherever they are). */
    private void offerClassPick(MinecraftServer server) {
        int seconds = secondsToStart(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String id = player.getUUID().toString();
            boolean coming = isRegistered(player) && (openNight || (save.accepted != null && save.accepted.contains(id)));
            if (coming) {
                sendClassPick(player, seconds);
            }
        }
    }

    /**
     * Opens the class window for one defender: always, also with only one class (then it
     * shows the kit, the team and what the other classes still need).
     */
    private void sendClassPick(ServerPlayer player, int seconds) {
        sendClassPick(player, seconds, "classpick");
    }

    /** Sends the window ("classpick" opens it, "classpick_update" only refreshes an open one). */
    private void sendClassPick(ServerPlayer player, int seconds, String kind) {
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        if (!own.hasClass() || !ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE)) {
            return;
        }
        com.mki.nightsiege.core.ClassPickData data = new com.mki.nightsiege.core.ClassPickData();
        data.play = own.chosenClass == null ? "" : own.chosenClass;
        data.seconds = seconds;
        data.level = levelForKit();
        data.test = pendingTest != null;
        SiegeEvent tonight = pendingTest != null ? pendingTest.event : pendingEvent();
        data.event = tonight == null || tonight == SiegeEvent.NONE ? "" : tonight.name();
        data.accepts = acceptedCount();
        data.required = config.requiredAccepts;
        data.unlocksFree = own.unlocksFree;
        data.nextOpen = mayUnlock(player, own);
        // One card per profile (Simon, 26 October 2026): "Krieger – Berserker", "Krieger – Klingenmeister" ...
        for (String key : own.profiles()) {
            SkillClass c = PlayerSkills.classOfKey(key);
            if (c == null || c.isShared() || !mayChoose(player, c)) {
                continue;
            }
            com.mki.nightsiege.core.ClassPickData.Entry entry = new com.mki.nightsiege.core.ClassPickData.Entry();
            entry.id = key;
            entry.clazz = c.name();
            entry.unlocked = true;
            entry.leveling = key.equals(own.chosenClass);
            entry.complete = skillTree.isCompleteProfile(own, key);
            entry.way = own.asProfile(key, () -> own.pathTaken(skillTree));
            entry.subclass = skillTree.subclassOfProfile(own, key);
            entry.spent = skillTree.pointsSpentProfile(own, key);
            entry.total = skillTree.pointsToCompleteProfile(own, key);
            com.mki.nightsiege.core.SkillNode ult = skillTree.ultimateOfProfile(own, key);
            entry.ult = ult == null ? 0 : own.levelInProfile(key, ult.id());
            entry.ultMax = ult == null ? 3 : ult.maxLevel();
            entry.ultDone = ult != null && entry.ult >= entry.ultMax;
            if (config.nightKits) {
                try {
                    entry.kit = own.asProfile(key, () -> kits.preview(player, own, data.level));
                } catch (RuntimeException e) {
                    NightSiegeMod.LOGGER.warn("[Night Siege] Kit-Vorschau {} fehlgeschlagen: {}", key, e.toString());
                }
            }
            data.classes.add(entry);
        }
        int level = data.level;
        data.armor = com.mki.nightsiege.core.NightKit.armor(level, 1).name();
        data.protection = com.mki.nightsiege.core.NightKit.protection(level);
        data.sharpness = com.mki.nightsiege.core.NightKit.sharpness(level);
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            String oid = other.getUUID().toString();
            if (isRegistered(other) && (openNight || pendingTest != null
                    || (save.accepted != null && save.accepted.contains(oid)))) {
                PlayerSkills os = save.skillsOf(oid);
                com.mki.nightsiege.core.ClassPickData.Mate mate = new com.mki.nightsiege.core.ClassPickData.Mate(
                        other.getScoreboardName(), os.className());
                mate.subclass = skillTree.subclassOfProfile(os, os.chosenClass);
                mate.self = other == player;
                mate.ready = !classPicking.contains(other.getUUID()) && !mate.self;
                data.team.add(mate);
            }
        }
        if (camp.inRound(player) || expeditions.inRound(player)) {
            // A ready round (content update): the war drum's or the dungeon's seal stone's, its members,
            // who is ready, the starter's button.
            boolean atCamp = camp.inRound(player);
            data.round = true;
            data.starter = atCamp ? camp.roundStarter(player) : expeditions.roundStarter(player);
            data.forceIn = atCamp ? camp.roundForceIn(player.level().getGameTime()) : expeditions.roundForceIn(player.level().getGameTime());
            data.team.clear();
            for (UUID id : atCamp ? camp.roundMembers() : expeditions.roundMembers()) {
                ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(id);
                if (other == null) {
                    continue;
                }
                PlayerSkills os = save.skillsOf(id.toString());
                com.mki.nightsiege.core.ClassPickData.Mate mate = new com.mki.nightsiege.core.ClassPickData.Mate(
                        other.getScoreboardName(), os.className());
                mate.subclass = skillTree.subclassOfProfile(os, os.chosenClass);
                mate.self = other == player;
                mate.ready = atCamp ? camp.roundReady(id) : expeditions.roundReady(id);
                data.team.add(mate);
            }
        }
        if ("classpick".equals(kind)) {
            classPicking.add(player.getUUID());
        }
        ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload(kind, Json.toJson(data)));
    }

    /** The class window shut from the server (the war drum's round ended or left it). */
    void closeClassPick(ServerPlayer player) {
        if (player != null && classPicking.remove(player.getUUID())) {
            ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("classpick_close", "{}"));
        }
    }

    /** The open class windows anew (the war drum's round changed). */
    void refreshClassPicksNow(MinecraftServer server) {
        refreshClassPicks(server);
    }

    /** Someone confirmed a class: the others with the window open see it at once. */
    private void refreshClassPicks(MinecraftServer server) {
        if (server == null) {
            return;
        }
        int seconds = secondsToStart(server);
        for (UUID id : new ArrayList<>(classPicking)) {
            ServerPlayer other = server.getPlayerList().getPlayer(id);
            if (other != null) {
                sendClassPick(other, seconds, "classpick_update");
            }
        }
    }

    // ------------------------------------------------------------------
    // rhythm: invitation in the morning, vote, horn, night
    // ------------------------------------------------------------------

    /** Day the horn last sounded and a siege last started (memory only). */
    private long warnedDay = -1L;
    private long startedDay = -1L;
    /** Day of the running (or last) real siege, for the next siege day. */
    private long siegeDay = -1L;

    /** The one core of the server, or null. */
    public CoreRecord mainCore() {
        if (save.cores == null) {
            return null;
        }
        for (CoreRecord core : save.cores) {
            if (core != null) {
                return core;
            }
        }
        return null;
    }

    private long clockDay(MinecraftServer server) {
        return SiegeMath.dayOf(server.overworld().getOverworldClockTime());
    }

    private int clockTime(MinecraftServer server) {
        return SiegeMath.timeOfDay(server.overworld().getOverworldClockTime());
    }

    /**
     * Latest clock time a siege night shows (Simon, 27 October 2026: daybreak came in the middle of
     * long sieges, from level 12 on a siege outlasts the night). There the overworld clock stops
     * until the siege ends; the sun rises after it. Only the time of day moves, never the day.
     */
    public static final int NIGHT_HOLD = 21500;
    /** Where a test night started by day sets the clock (dusk; dark when the preparation ends). */
    public static final int NIGHT_START = 13000;

    private static net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> overworldClock(MinecraftServer server) {
        try {
            return server.overworld().dimensionTypeRegistration().value().defaultClock().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Moves the overworld clock to this time of day on the same day (forwards or back). */
    private static void setTimeOfDay(MinecraftServer server, int timeOfDay) {
        net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> clock = overworldClock(server);
        if (clock == null) {
            return;
        }
        net.minecraft.world.clock.ServerClockManager clocks = server.clockManager();
        long total = clocks.getTotalTicks(clock);
        clocks.setTotalTicks(clock, total - Math.floorMod(total, 24000L) + timeOfDay);
    }

    /** Once a second during a siege: the clock stops at {@link #NIGHT_HOLD} (not in the eclipse, a day event). */
    private void holdNight(MinecraftServer server) {
        if (save.clockHeld || world == null || world.getGameTime() % 20 != 0 || currentEvent == SiegeEvent.ECLIPSE) {
            return;
        }
        int time = clockTime(server);
        if (time < NIGHT_HOLD) {
            return;
        }
        net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> clock = overworldClock(server);
        if (clock == null) {
            return;
        }
        try {
            setTimeOfDay(server, NIGHT_HOLD);
            server.clockManager().setPaused(clock, true);
            save.clockHeld = true;
            persist();
            NightSiegeMod.LOGGER.info("[Night Siege] Nacht hält bis zum Ende der Belagerung");
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Uhr anhalten fehlgeschlagen: {}", e.toString());
        }
    }

    /** After the siege (and after a crash in the middle of one): the clock runs again. */
    private void releaseNight(MinecraftServer server) {
        if (!save.clockHeld || server == null) {
            return;
        }
        save.clockHeld = false;
        net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> clock = overworldClock(server);
        if (clock != null) {
            try {
                server.clockManager().setPaused(clock, false);
            } catch (RuntimeException e) {
                NightSiegeMod.LOGGER.warn("[Night Siege] Uhr freigeben fehlgeschlagen: {}", e.toString());
            }
        }
    }

    /** For a test night by day: night first, same day (not for the eclipse, which comes by day). */
    public void ensureNight(MinecraftServer server, SiegeEvent event) {
        if (event == SiegeEvent.ECLIPSE) {
            return;
        }
        int time = clockTime(server);
        if (time < 12500 || time >= NIGHT_HOLD) {
            try {
                setTimeOfDay(server, NIGHT_START);
            } catch (RuntimeException e) {
                NightSiegeMod.LOGGER.warn("[Night Siege] Uhrzeit setzen fehlgeschlagen: {}", e.toString());
            }
        }
    }

    /** True on the day of a siege night, from the invitation on (the aurora stays away then). */
    public boolean siegeDueToday(MinecraftServer server) {
        return mainCore() != null && save.nextSiegeDay >= 0 && save.nextSiegeDay <= clockDay(server);
    }

    /** True while the vote of today runs: invited, not yet decided. */
    public boolean voteOpen(MinecraftServer server) {
        long day = clockDay(server);
        return !active && save.voteDay == day && save.nextSiegeDay == day && warnedDay != day
                && clockTime(server) < closeTime();
    }

    private int closeTime() {
        return pendingEvent() == SiegeEvent.ECLIPSE ? config.eclipseWarningDayTime : config.warningDayTime;
    }

    private int startTime() {
        return pendingEvent() == SiegeEvent.ECLIPSE ? config.eclipseStartDayTime : config.startDayTime;
    }

    /** The horn has sounded today and the night has not begun yet. */
    public boolean nightComing(MinecraftServer server) {
        long day = clockDay(server);
        return !active && warnedDay == day && startedDay != day;
    }

    /** Accepted invitations of players still signed up. */
    public int acceptedCount() {
        int count = 0;
        if (save.accepted != null) {
            for (String id : save.accepted) {
                if (save.isRegistered(id)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void tickRhythm(MinecraftServer server) {
        CoreRecord core = mainCore();
        if (core == null) {
            return;
        }
        long day = clockDay(server);
        int time = clockTime(server);
        SiegeRhythm.Step step = SiegeRhythm.decide(day, time, save.nextSiegeDay, save.voteDay, warnedDay, startedDay,
                acceptedCount(), config, closeTime(), startTime());
        switch (step) {
            case SCHEDULE -> {
                save.nextSiegeDay = SiegeRhythm.nextAfter(day, config);
                persist();
            }
            case MISSED -> {
                save.nextSiegeDay = extraSiegeRuns() ? endExtraSiege(day, time) : SiegeRhythm.afterMissed(day, time, config);
                save.clearVotes();
                persist();
            }
            case INVITE -> sendInvitations(server, day);
            case CANCEL -> cancelNight(server, day, "nightsiege.msg.vote_failed");
            case WARN -> soundHorn(server, day, core);
            case START -> startNight(server, day, core);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // extra siege: called at the core for tonight (Simon, 26 October 2026)
    // ------------------------------------------------------------------

    /** True while an extra siege is invited or played: the regular siege day waits in the save. */
    private boolean extraSiegeRuns() {
        return save.extraSiegeDay >= 0;
    }

    /** True when today's night is an extra siege (for the window). */
    public boolean extraSiegeToday(MinecraftServer server) {
        return extraSiegeRuns() && save.extraSiegeDay == clockDay(server);
    }

    /** The extra night is over (played, called off or missed): the regular plan goes on as before. */
    private long endExtraSiege(long day, int time) {
        long next = SiegeRhythm.afterExtra(save.extraRegularDay, day, time, config);
        save.extraSiegeDay = -1L;
        save.extraRegularDay = -1L;
        return next;
    }

    /** The next siege day after a night (or a called-off night) of this day. */
    private long nextSiegeDayAfter(MinecraftServer server, long day) {
        return extraSiegeRuns() ? endExtraSiege(clockDay(server), clockTime(server)) : SiegeRhythm.nextAfter(day, config);
    }

    /** Why the viewer cannot call an extra siege now (NONE: they can). */
    public SiegeRhythm.ExtraBlock extraSiegeBlock(MinecraftServer server) {
        long day = clockDay(server);
        int calls = save.extraCalledDay == day ? save.extraCallsToday : 0;
        boolean held = warnedDay == day || startedDay == day;
        boolean busy = active || pendingTest != null || nightComing(server);
        return SiegeRhythm.extraSiegeBlock(day, clockTime(server), currentLevel(), save.nextSiegeDay, save.voteDay,
                held, calls, busy, config, closeTime());
    }

    /** The button at the core: invites everyone to an extra siege tonight, as on a regular siege day. */
    private void onCallExtraSiege(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || mainCore() == null) {
            return;
        }
        SiegeRhythm.ExtraBlock block = extraSiegeBlock(server);
        if (block != SiegeRhythm.ExtraBlock.NONE) {
            player.sendSystemMessage(Component.translatable("nightsiege.ui.extra_block." + block.name().toLowerCase(Locale.ROOT),
                    config.extraSiegeFromLevel).withStyle(ChatFormatting.GRAY));
            openCoreWindow(player);
            return;
        }
        long day = clockDay(server);
        save.extraCallsToday = save.extraCalledDay == day ? save.extraCallsToday + 1 : 1;
        save.extraCalledDay = day;
        save.extraCalledBy = player.getScoreboardName();
        save.extraRegularDay = save.nextSiegeDay;
        save.extraSiegeDay = day;
        save.nextSiegeDay = day;
        NightSiegeMod.LOGGER.info("[Night Siege] {} ruft eine Extra-Belagerung für Tag {} aus (reguläre Belagerung an Tag {})",
                player.getScoreboardName(), day, save.extraRegularDay);
        broadcast(server, Component.translatable("nightsiege.msg.extra_called", player.getDisplayName())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sendInvitations(server, day);   // persists
        openCoreWindow(player);
    }

    /** Morning of a siege day: every signed-up player is asked whether they take part. */
    private void sendInvitations(MinecraftServer server, long day) {
        save.voteDay = day;
        save.clearVotes();
        // The event is rolled now: an eclipse comes at noon, so the vote must know it.
        NightFragment fragment = NightFragment.byName(save.nightFragment);
        SiegeEvent pending = fragment != null ? fragment.event() : EventPicker.pick(currentLevel(), random, config);
        if (comingKind() != com.mki.nightsiege.core.BossPlan.NightKind.NORMAL) {
            pending = SiegeEvent.NONE;   // a boss night comes by night and has no event
        }
        if (pending == SiegeEvent.ECLIPSE && extraSiegeRuns()
                && clockTime(server) + 1200 > config.eclipseWarningDayTime) {
            pending = SiegeEvent.NONE;   // an extra siege called late in the day: no time left to vote before noon
        }
        save.pendingEvent = pending.name();
        persist();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isRegistered(player)) {
                sendInvitation(player);
            }
        }
    }

    /** The invitation with its two buttons. */
    public void sendInvitation(ServerPlayer player) {
        boolean eclipse = pendingEvent() == SiegeEvent.ECLIPSE;
        MutableComponent text = Component.empty()
                .append(Component.translatable(eclipse ? "nightsiege.msg.invite_noon" : "nightsiege.msg.invite",
                        currentLevel()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" "))
                .append(button("nightsiege.msg.vote_yes", "/nightsiege zusagen", ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(button("nightsiege.msg.vote_no", "/nightsiege absagen", ChatFormatting.RED));
        player.sendSystemMessage(text);
        NightFragment fragment = NightFragment.byName(save.nightFragment);
        com.mki.nightsiege.core.BossKind boss = upcomingBoss();
        com.mki.nightsiege.core.BossPlan.NightKind coming = comingKind();
        if (boss != null) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.boss_warning",
                    Component.translatable("nightsiege.boss." + boss.id())).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else if (coming == com.mki.nightsiege.core.BossPlan.NightKind.GROUP) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.group_warning").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else if (coming == com.mki.nightsiege.core.BossPlan.NightKind.LATE) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.late_warning", config.bossLateCount,
                    Math.max(1, config.bossLateSeconds / 60)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else if (fragment != null) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.special_warning",
                    Component.translatable("nightsiege.event." + fragment.event().name().toLowerCase(Locale.ROOT)),
                    config.specialNightXpMultiplier).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        player.sendSystemMessage(Component.translatable("nightsiege.msg.vote_need", acceptedCount(),
                config.requiredAccepts).withStyle(ChatFormatting.GRAY));
    }

    /** A clickable chat button that runs a command. */
    public static MutableComponent button(String key, String command, ChatFormatting color) {
        return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(command))));
    }

    /** A signed-up player answers today's invitation. */
    public void vote(ServerPlayer player, boolean yes) {
        MinecraftServer server = player.level().getServer();
        if (!isRegistered(player)) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.not_registered").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (server == null || !voteOpen(server)) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.vote_closed").withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean wasEnough = acceptedCount() >= config.requiredAccepts;
        if (!save.vote(player.getUUID().toString(), yes)) {
            player.sendSystemMessage(Component.translatable(yes ? "nightsiege.msg.vote_already_yes" : "nightsiege.msg.vote_already_no")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        persist();
        int accepts = acceptedCount();
        broadcast(server, Component.translatable(yes ? "nightsiege.msg.voted_yes" : "nightsiege.msg.voted_no",
                player.getDisplayName(), accepts, config.requiredAccepts)
                .withStyle(yes ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (!wasEnough && accepts >= config.requiredAccepts) {
            broadcast(server, Component.translatable(pendingEvent() == SiegeEvent.ECLIPSE
                    ? "nightsiege.msg.vote_enough_noon" : "nightsiege.msg.vote_enough").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Too few accepts or nobody at the core: the night is skipped, the next invitation comes a few days later. */
    private void cancelNight(MinecraftServer server, long day, String key) {
        safe("Heerlager", () -> camp.onCancel(server));
        long next = nextSiegeDayAfter(server, day);
        int days = (int) (next - day);
        if (save.voteDay == day) {
            broadcast(server, Component.translatable(key, acceptedCount(), config.requiredAccepts, days)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (pendingEvent() == SiegeEvent.ECLIPSE) {
            CoreRecord core = mainCore();
            if (core != null) {
                EventAtmosphere.setServer(core.dimension, "", 200);
                events.syncClients(server);
            }
        }
        save.nextSiegeDay = next;
        save.pendingEvent = null;
        save.clearVotes();
        warnedDay = day;
        startedDay = day;
        endKits(server);
        persist();
    }

    /** Sunset (or noon for an eclipse): the horn sounds for the night that was agreed on. */
    private void soundHorn(MinecraftServer server, long day, CoreRecord core) {
        warnedDay = day;
        safe("Heerlager", () -> camp.onHorn(server));   // the camp goes; untouched, its machines come tonight
        safe("Dungeons", () -> expeditions.endAll(server, "nightsiege.expedition.night"));
        boolean eclipse = pendingEvent() == SiegeEvent.ECLIPSE;
        broadcast(server, Component.translatable(eclipse ? "nightsiege.msg.eclipse_warning" : "nightsiege.msg.warning",
                currentLevel()).withStyle(eclipse ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        Fx.warning(server, config);
        handOutAtWarning(server, core);
        if (eclipse) {
            EventAtmosphere.setServer(core.dimension, EventAtmosphere.ECLIPSE,
                    EventAtmosphere.fadeTicks(EventAtmosphere.ECLIPSE));
            events.syncClients(server);
        }
    }

    /** Start time: the siege begins with the players who accepted and stand at the core. */
    private void startNight(MinecraftServer server, long day, CoreRecord core) {
        startedDay = day;
        ServerLevel targetWorld = levelByName(server, core.dimension);
        boolean started = targetWorld != null
                && start(server, targetWorld, new BlockPos(core.x, core.y, core.z), 0, pendingEvent());
        if (started) {
            siegeDay = day;
            save.pendingEvent = null;
            persist();
        } else {
            cancelNight(server, day, "nightsiege.msg.nobody_at_core");
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
        // Started by an admin without a vote: every signed-up player nearby defends.
        openNight = true;
        boolean started = start(server, targetWorld, targetAnchor, 0, null);
        if (started) {
            siegeDay = clockDay(server);
        } else {
            openNight = false;
        }
        return started;
    }

    /** A real night started by command: not only those who accepted defend, but every signed-up player. */
    private boolean openNight;

    /** Waves started in the running siege. */
    private int wavesStarted;
    /** Siege monsters killed this night, spawned this night, and the kills the night asks for. */
    private int nightKills;
    private int spawnedTotal;
    /** Monsters of the night gone without being slain (quotaSkipsVanished, 29.10.). */
    private int vanishedTotal;
    private final Set<UUID> slainNightMobs = new HashSet<>();
    private int killQuota;
    /**
     * The kill goal moves with what can still come (Simon, 26 October 2026: keep the sum, but
     * show why it changes): the goal the bar showed last, the reasons noted since, and the note
     * the bar carries for five seconds after a change.
     */
    private int shownRequired = -1;
    private Component goalDown;
    private Component goalUp;
    private Component goalNote;
    private int goalNoteUntil;
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
        return prepareTest(server, targetWorld, targetAnchor, level, event, null);
    }

    /** /nightsiege test <level> boss <name>: a test night that ends with this boss (it comes 15 s in). */
    public boolean prepareTest(MinecraftServer server, ServerLevel targetWorld, BlockPos targetAnchor,
                               int level, SiegeEvent event, com.mki.nightsiege.core.BossKind boss) {
        if (active || pendingTest != null) {
            return false;
        }
        testBoss = null;
        List<ServerPlayer> nearby = defendersNear(server, targetWorld, targetAnchor, true);
        if (nearby.isEmpty()) {
            return false;
        }
        if (config.testPrepSeconds <= 0) {
            testBoss = boss;
            return startTest(server, targetWorld, targetAnchor, level, event);
        }
        trapsHanded.clear();
        int shownLevel = Math.min(config.maxLevel, level);
        pendingTest = new PendingTest(targetWorld, targetAnchor, level, event, config.testPrepSeconds * 20);
        pendingTest.boss = boss;
        for (ServerPlayer player : nearby) {
            equipKit(player);
            sendClassPick(player, config.testPrepSeconds);
        }
        prepBar = new ServerBossEvent(com.mki.nightsiege.core.NightBarIds.make(com.mki.nightsiege.core.NightBarIds.Kind.PREP),
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
    public static boolean blocksNaturalSpawns(ServerLevel level, double x, double z) {
        return INSTANCE.blocksSpawnsIn(level, x, z);
    }

    /** Phantoms and patrols wait while a siege night runs in this world (only for those few minutes). */
    public static boolean holdsCustomSpawns(ServerLevel level) {
        SiegeManager m = INSTANCE;
        if (m.config == null || !m.config.blockNaturalSpawnsInSiege) {
            return false;
        }
        return (m.active && m.world == level) || (m.pendingTest != null && m.pendingTest.world == level)
                || m.playtest.running();
    }

    /** Natural monsters are held back only around the core, so players elsewhere notice nothing. */
    private boolean blocksSpawnsIn(ServerLevel level, double x, double z) {
        if (config == null || !config.blockNaturalSpawnsInSiege || level == null) {
            return false;
        }
        BlockPos center = null;
        if (active && world == level) {
            center = anchor;
        }
        PendingTest prep = pendingTest;
        if (center == null && prep != null && prep.world == level) {
            center = prep.anchor;
        }
        String blocked = spawnBlockWorld;
        if (center == null && blocked != null && blocked.equals(dimensionName(level)) && level.getGameTime() < spawnBlockUntil) {
            center = spawnBlockCenter;
        }
        double r = config.naturalSpawnBlockRadius;
        if (center == null && playtest.running() && level.getServer() != null) {
            // The playtest (Simon 29.10.): no natural monsters around its players, at any time.
            for (ServerPlayer p : playtest.onlinePlayers(level.getServer())) {
                if (p.level() == level) {
                    double px = x - p.getX();
                    double pz = z - p.getZ();
                    if (px * px + pz * pz <= r * r) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (center == null) {
            return false;
        }
        double dx = x - (center.getX() + 0.5);
        double dz = z - (center.getZ() + 0.5);
        return dx * dx + dz * dz <= r * r;
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
        PendingTest prep = pendingTest;
        pendingTest = null;
        trapsHanded.clear();
        if (prepBar != null) {
            prepBar.removeAllPlayers();
            prepBar = null;
        }
        if (prep != null && prep.world != null) {
            endKits(prep.world.getServer());
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
                for (ServerPlayer player : defendersNear(server, prep.world, prep.anchor, true)) {
                    prepBar.addPlayer(player);
                    // Whoever comes along in the preparation gets their kit too.
                    equipKit(player);
                }
            }
            return;
        }
        pendingTest = null;
        if (prepBar != null) {
            prepBar.removeAllPlayers();
            prepBar = null;
        }
        testBoss = prep.boss;
        if (!startTest(server, prep.world, prep.anchor, prep.level, prep.event)) {
            trapsHanded.clear();
            broadcast(server, Component.translatable("nightsiege.cmd.no_target").withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * The night kit (with traps for engineers) for a defender: given once, at the
     * horn, in a test's preparation or when joining the night. Without kits only
     * the free traps are handed out, as before.
     */
    private void equipKit(ServerPlayer player) {
        if (!config.nightKits) {
            handOutTraps(player);
            return;
        }
        if (!kits.hasStash(player)) {
            kits.equip(player, levelForKit());
        }
    }

    /** The level the kit is built for: the running night, the test in preparation or the coming night. */
    private int levelForKit() {
        if (active) {
            return siegeLevel;
        }
        PendingTest prep = pendingTest;
        if (prep != null) {
            return Math.min(config.maxLevel, prep.level);
        }
        return currentLevel();
    }

    public KitService kits() {
        return kits;
    }

    public CrateService crates() {
        return crates;
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
    private void handOutAtWarning(MinecraftServer server, CoreRecord core) {
        ServerLevel targetWorld = levelByName(server, core.dimension);
        if (targetWorld == null) {
            return;
        }
        // Natural monsters stop with the warning; if the night does not come, they start again a minute after its time.
        spawnBlockWorld = dimensionName(targetWorld);
        long gap = Math.max(config.startDayTime - config.warningDayTime, config.eclipseStartDayTime - config.eclipseWarningDayTime);
        spawnBlockUntil = targetWorld.getGameTime() + Math.max(0L, gap) + 1200L;
        BlockPos pos = new BlockPos(core.x, core.y, core.z);
        spawnBlockCenter = pos;
        trapsHanded.clear();
        for (ServerPlayer player : defendersNear(server, targetWorld, pos, false)) {
            equipKit(player);
        }
        offerClassPick(server);
    }

    private boolean start(MinecraftServer server, ServerLevel targetWorld, BlockPos targetAnchor,
                          int forcedLevel, SiegeEvent forcedEvent) {
        if (active) {
            return false;
        }
        boolean test = forcedLevel > 0;
        List<ServerPlayer> nearby = defendersNear(server, targetWorld, targetAnchor, test);
        if (nearby.isEmpty()) {
            return false;
        }
        world = targetWorld;
        anchor = targetAnchor;
        testRun = test;
        // The curse stones on the altar act from the first wave (a test night tries them, no reward).
        Curses.begin(config.cursesEnabled ? Curses.chosen(save) : List.of());
        nightCurses = Curses.count();
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
        ladders.reset();
        besiegers.reset();
        for (ServerPlayer player : nearby) {
            participants.add(player.getUUID());
        }
        wavesStarted = 0;
        waveWeight.clear();
        CrateEffects.reset();
        GainGear.reset();
        SecondUlts.reset();
        nightNumber = testRun ? -1 : save.nightsHeld++;
        for (ServerPlayer player : nearby) {
            joinNight(player);
        }

        // The boss rhythm (Simon 29.10.): boss nights 4, 8, 12, 16, the Vierer-Nacht on 20 and 40, from 41
        // every 2nd night two bosses after five minutes. A boss night has no event; a fragment waits.
        resetFinale(server);
        com.mki.nightsiege.core.BossPlan.NightKind kind = testRun ? (testBoss != null ? com.mki.nightsiege.core.BossPlan.NightKind.BOSS : testKind != null ? testKind : com.mki.nightsiege.core.BossPlan.NightKind.NORMAL)
                : comingKind();
        testKind = null;
        tonightKind = kind;
        boolean finale = kind == com.mki.nightsiege.core.BossPlan.NightKind.GROUP;
        com.mki.nightsiege.core.BossKind tonightBoss = kind != com.mki.nightsiege.core.BossPlan.NightKind.BOSS ? null : testRun ? testBoss : upcomingBoss();
        testBoss = null;
        bossVictoryAt = -1L;
        bosses.reset(server);
        if (tonightBoss != null) {
            bosses.plan(tonightBoss);
        }
        if (finale) {
            finaleNight = true;
            finaleQueue.addAll(com.mki.nightsiege.core.BossPlan.groupBosses(save.groupBosses, 4, random));
        } else if (kind == com.mki.nightsiege.core.BossPlan.NightKind.LATE) {
            lateNight = true;
            finaleQueue.addAll(com.mki.nightsiege.core.BossPlan.lateBosses(config.bossLateCount, random));
        }
        // A normal night may end with a dungeon warden (Simon, 29 October 2026: 20 %, one of the eight).
        warden.reset(server);
        com.mki.nightsiege.core.ExpeditionKind tonightWarden = tonightBoss != null || kind != com.mki.nightsiege.core.BossPlan.NightKind.NORMAL ? null
                : testRun ? testWarden : WardenNight.roll(random, config.wardenChance);
        testWarden = null;
        if (tonightWarden != null) {
            warden.plan(tonightWarden);
            NightSiegeMod.LOGGER.info("[Night Siege] Wächter der Nacht: {}", tonightWarden.id());
        }
        safe("Dungeons", () -> expeditions.endAll(server, "nightsiege.expedition.night"));
        // The machines of a war camp left standing (or that won) come tonight.
        machines.reset(server);
        boolean machinesTonight = testRun ? testMachines
                : save.machinesDay >= 0 && save.machinesDay == clockDay(server);
        testMachines = false;
        if (config.machinesEnabled && machinesTonight) {
            machines.plan(siegeLevel);
        }
        if (!testRun) {
            save.machinesDay = -1L;
        }
        currentEvent = tonightBoss != null || finaleNight || lateNight ? SiegeEvent.NONE : forcedEvent != null ? forcedEvent : pickEventNow(server);
        save.specialNight = false;
        usedFragment = null;
        NightFragment fragment = testRun || tonightBoss != null || finaleNight || lateNight ? null : NightFragment.byName(save.nightFragment);
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
        if (currentEvent == SiegeEvent.THEME_WAVE) {
            themeMobId = pickAvailableMob();
            if (themeMobId == null) {
                currentEvent = SiegeEvent.NONE;
            }
        }

        // The event is never named. A blood moon only shows as a red bar.
        bossBar = new ServerBossEvent(com.mki.nightsiege.core.NightBarIds.make(com.mki.nightsiege.core.NightBarIds.Kind.NIGHT),
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
        long scanStart = System.nanoTime();
        defense.begin(world, anchor, config, save.coreSkills(), skillTree, digger, anyoneHas("mauerkelle"));
        long scanMs = (System.nanoTime() - scanStart) / 1_000_000L;
        safe("Angriffsplan", () -> {
            raid.begin(world, anchor, defense.base(), config, random, this::barNotice);
            BaseScan b = defense.base();
            if (b != null) {
                NightSiegeMod.LOGGER.info("[Night Siege] Basis: {} {}x{}, Tore {}, Spawnplätze {} ({} ms mit Schnappschuss)",
                        b.closed ? "geschlossen" : "offen", b.width(), b.depth(), b.gates.size(), b.spawnSpotCount(), scanMs);
            }
        });
        nightKills = 0;
        spawnedTotal = 0;
        vanishedTotal = 0;
        slainNightMobs.clear();
        shownRequired = -1;
        goalDown = null;
        goalUp = null;
        goalNote = null;
        nightLog.begin(world.getGameTime());
        safe("Statistik", () -> stats.beginNight(world.getGameTime(), nightNumber, siegeLevel, testRun,
                currentEvent.name().toLowerCase(java.util.Locale.ROOT), save.specialNight, usedFragment,
                finaleNight ? "vierer" : lateNight ? "doppel" : bosses.kind() == null ? null : bosses.kind().id(), defense.base(), List.of(), clockDay(server)));
        digger.takeBroken();
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                logPlayer(player);
            }
        }
        killQuota = SiegeMath.killQuota(config.killQuotaShare, plannedWaves(), wantedPerWave());
        safe("Gegner", () -> foes.startNight(foesHost, plannedWaves(), bosses.planned() || finaleNight || lateNight));
        safe("Nemesis", () -> nemesis.beginNight(nemesisHost));
        traps.refillAll();   // every tower full and whole at nightfall (27 October 2026)
        bonus.endAurora(server);
        events.begin(server, world, anchor, currentEvent, config);
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                equipKit(player);
                SkillService.apply(player, save.skillsOf(player.getUUID().toString()), skillTree);
                safe("Startbegleiter", () -> companions.startNight(player));
            }
        }

        broadcast(server, Component.translatable("nightsiege.msg.start", siegeLevel)
                .withStyle(ChatFormatting.RED));
        if (nightCurses > 0) {
            broadcast(server, Component.translatable("nightsiege.curse.night", Curses.names(Curses.active()), nightCurses)
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        Fx.siegeStart(server, config, currentEvent == SiegeEvent.BLOOD_MOON);
        if (tonightBoss != null) {
            broadcast(server, Component.translatable("nightsiege.msg.boss_night",
                    Component.translatable("nightsiege.boss." + tonightBoss.id())).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
        if (finaleNight) {
            broadcast(server, Component.translatable("nightsiege.msg.group_night", bossNames(finaleQueue))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else if (lateNight) {
            broadcast(server, Component.translatable("nightsiege.msg.late_night", finaleQueue.size(), lateTicks() / 20 / 60)
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
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
            if (Foes.isCapped(candidate)) {
                continue;   // a night of only tunnelers or banners would make no sense
            }
            return candidate;
        }
        return null;
    }

    /** Blood moon haze, on a slow beat so it stays cheap. */
    private void tickAtmosphere(MinecraftServer server) {
        if (!config.particlesEnabled || world == null) {
            return;
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
        if (Curses.count() > 0) {
            title = Component.empty().append(title).append(Curses.barMark());   // a cursed night: ☠ and the number of curses
        }
        if (finaleNight) {
            // The Vierer-Nacht: no clock, the bosses are the goal.
            int total = finaleDown + finaleQueue.size() + (int) finaleBosses.stream().filter(b -> !b.fallen()).count();
            return Component.translatable("nightsiege.bar.group", siegeLevel, finaleDown, total)
                    .withStyle(ChatFormatting.GOLD);
        }
        if (lateNight) {
            // A late boss night: the clock and the bosses (coming, or how many still stand).
            int standing = (int) finaleBosses.stream().filter(b -> !b.fallen()).count();
            Component state = finaleBosses.isEmpty()
                    ? Component.translatable("nightsiege.bar.late_coming", finaleQueue.size(),
                            Math.max(0, (lateTicks() - (totalTicks - ticksLeft)) / 20))
                    : Component.translatable("nightsiege.bar.late_here", standing);
            return Component.empty().append(title).append(Component.literal("  ·  "))
                    .append(state.copy().withStyle(ChatFormatting.RED));
        }
        if (bosses.planned()) {
            // A boss night: no kill goal, the boss is the goal.
            return Component.empty().append(title).append(Component.literal("  ·  "))
                    .append(Component.translatable(bosses.spawned() ? "nightsiege.bar.boss_here" : "nightsiege.bar.boss_coming",
                            Component.translatable("nightsiege.boss." + bosses.kind().id())).withStyle(ChatFormatting.RED));
        }
        if (warden.spawned()) {
            // The warden came: he is the goal now, the kill goal is gone.
            return Component.empty().append(title).append(Component.literal("  ·  "))
                    .append(Component.translatable("nightsiege.bar.warden_here", WardenNight.name(warden.kind())));
        }
        int required = killsRequired();
        noteGoalChange(required);
        if (required <= 0) {
            return title;
        }
        MutableComponent bar = Component.empty().append(title).append(Component.literal("  ·  "))
                .append(Component.translatable("nightsiege.bar.kills", nightKills, required)
                        .withStyle(nightKills >= required ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        if (goalNote != null && ticksLeft > goalNoteUntil) {
            bar.append(Component.literal(" ")).append(goalNote);
        }
        return bar;
    }

    /** The goal moved since the bar last showed it: the bar says why for five seconds. */
    private void noteGoalChange(int required) {
        if (shownRequired >= 0 && required != shownRequired) {
            boolean down = required < shownRequired;
            Component why = down ? goalDown : goalUp;
            if (why == null) {
                why = Component.translatable(down ? "nightsiege.bar.goal_fewer" : "nightsiege.bar.goal_more");
            }
            goalNote = Component.translatable(down ? "nightsiege.bar.goal_down" : "nightsiege.bar.goal_up", why)
                    .withStyle(down ? ChatFormatting.AQUA : ChatFormatting.GOLD);
            goalNoteUntil = ticksLeft - 100;
            goalDown = null;
            goalUp = null;
        }
        shownRequired = required;
    }

    /** For the tests: the boss bar's text right now. */
    public String bossBarText() {
        return bossBar == null ? "" : bossBarTitle().getString();
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
        holdNight(server);

        for (ServerPlayer player : defendersNear(server, world, anchor, testRun)) {
            if (!fallen.contains(player.getUUID())) {
                if (participants.add(player.getUUID())) {
                    // More defenders, bigger waves: the goal can rise (up to where it started).
                    goalUp = Component.translatable("nightsiege.bar.goal_joined", player.getScoreboardName());
                    logPlayer(player);
                    equipKit(player);
                    SkillService.apply(player, save.skillsOf(player.getUUID().toString()), skillTree);
                    joinNight(player);
                    safe("Startbegleiter", () -> companions.startNight(player));
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
            if (bossVictoryAt > 0) {
                finish(server, true);   // the boss fell first
                return;
            }
            if (fallenCount > 0) {
                finish(server, false);
            } else {
                abort(server);
            }
            return;
        }

        // The boss night's finale: the boss and a last big wave; after that no more waves.
        if (bosses.planned() && !bosses.spawned() && ticksLeft <= bossFinaleTicks() && world.getGameTime() % 20 == 0) {
            if (bosses.spawn(bossHost)) {
                spawnBatch(server, config.bossFinaleWave);
            }
        }
        if (finaleNight) {
            tickFinale(server);
        } else if (lateNight) {
            tickLate(server);
        } else if (warden.planned() && !warden.spawned() && ticksLeft <= wardenFinaleTicks() && world.getGameTime() % 20 == 0) {
            // The warden comes at the end of the night, after the last wave; then no more waves.
            if (warden.spawn(bossHost)) {
                broadcast(server, Component.translatable("nightsiege.warden.comes", WardenNight.name(warden.kind()))
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        if (!finaleNight && !(lateNight && !finaleBosses.isEmpty()) && !bosses.spawned() && !warden.spawned()) {
            if (batchCooldown <= 0) {
                spawnBatch(server);
                double extraDelay = 0.0 /* the core tree is gone (27 October 2026) */;
                batchCooldown = (int) Math.round((config.batchIntervalSeconds + extraDelay) * 20.0 * Curses.waveFactor());
            } else {
                batchCooldown--;
            }
        }
        safe("Boss", () -> {
            for (BossService b : allBosses()) {
                b.tick(bossHost, world.getGameTime());
            }
        });
        safe("Wächter", () -> warden.tick(bossHost, world.getGameTime()));
        safe("Maschinen", () -> machines.tick(bossHost, config, world.getGameTime()));
        if (bossVictoryAt > 0 && world.getGameTime() >= bossVictoryAt) {
            finish(server, true);
            return;
        }

        tickAtmosphere(server);
        List<ServerPlayer> upright = new ArrayList<>();
        for (UUID id : participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && !fallen.contains(id)) {
                upright.add(p);
            }
        }
        GainGear.tick(world, upright);
        SecondUlts.tick(world);
        safe("Begleiter-Gewinne", () -> companions.nightTick(world, upright));
        if (world.getGameTime() % 5 == 0) {
            for (ServerPlayer p : upright) {
                Combo.tick(p, SkillTree.has(save.skillsOf(p.getUUID().toString()), "w_combo"));
            }
        }
        if (world.getGameTime() % 20 == 0) {
            tickSpectators(server);
            CrateEffects.tick(world, upright);
            safe("Statistik", () -> stats.tickSecond(world.getGameTime(), liveMobs, participants, world, traps));
        }
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

        if (world.getGameTime() % 20 == 0) {
            java.util.List<ServerPlayer> skyTargets = new java.util.ArrayList<>();
            for (UUID id : participants) {
                ServerPlayer p = fallen.contains(id) ? null : server.getPlayerList().getPlayer(id);
                if (p != null) {
                    skyTargets.add(p);
                }
            }
            skyHunters.tick(world, liveMobs, skyTargets, config, world.getGameTime());   // vexes and phantoms go for the walls
        }
        if (world.getGameTime() % 2 == 0) {
            ladders.tick(world, liveMobs, anchor, world.getGameTime());
            besiegers.tick(world, liveMobs, anchor, world.getGameTime());
            safe("Gegner", () -> foes.tick(foesHost, liveMobs, world.getGameTime()));
            safe("Angriffsplan", () -> raid.tick(world.getGameTime(), liveMobs, upright, ladders));
        }
        safe("Nemesis", () -> nemesis.tick(nemesisHost, world.getGameTime()));
        // Spiders under an overhang swing out and over the lip (Simon, 27 October 2026).
        safe("Spinnen", () -> SpiderOverhang.tick(world, liveMobs, bosses.boss(world),
                net.minecraft.world.phys.Vec3.atCenterOf(anchor), world.getGameTime()));

        // Monsters no longer dig through walls (Simon, 27 October 2026): no pickaxe and axe monsters.

        if (bossVictoryAt > 0 || finaleNight) {
            return;   // the boss is down: the night ends in a moment, the clock stands (the finale has none)
        }
        ticksLeft--;
        if (ticksLeft <= 0) {
            if (bosses.spawned() && !bosses.fallen()) {
                // Dawn and the boss still stands: the night is lost.
                broadcast(server, Component.translatable("nightsiege.msg.boss_survived",
                        Component.translatable("nightsiege.boss." + bosses.kind().id())).withStyle(ChatFormatting.DARK_RED));
                finish(server, false);
            } else if (lateNight && (!finaleQueue.isEmpty() || finaleBosses.stream().anyMatch(b -> !b.fallen()))) {
                // Dawn and a boss of the late night still stands: the night is lost.
                broadcast(server, Component.translatable("nightsiege.msg.late_survived").withStyle(ChatFormatting.DARK_RED));
                finish(server, false);
            } else if (warden.spawned() && !warden.fallen()) {
                // Dawn and the warden still stands: the night is lost.
                broadcast(server, Component.translatable("nightsiege.warden.survived", WardenNight.name(warden.kind()))
                        .withStyle(ChatFormatting.DARK_RED));
                finish(server, false);
            } else {
                finish(server, true);
            }
        }
    }

    /** Tests: every boss of the night (the finale's included). */
    public List<BossService> bossesForTest() {
        return allBosses();
    }

    /** The main boss service and the finale's, for the hooks every boss needs. */
    private List<BossService> allBosses() {
        if (finaleBosses.isEmpty()) {
            return List.of(bosses);
        }
        List<BossService> all = new ArrayList<>(finaleBosses.size() + 1);
        all.add(bosses);
        all.addAll(finaleBosses);
        return all;
    }

    private boolean bossesAllowDamage(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
        for (BossService b : allBosses()) {
            if (!b.allowDamage(victim, source, amount)) {
                return false;
            }
        }
        return true;
    }

    private double bossDamageFactor(Entity attacker) {
        double f = 1.0;
        for (BossService b : allBosses()) {
            f *= b.damageFactor(attacker);
        }
        return f;
    }

    /** True while one of the night's bosses takes no damage at all (the lich behind his soul shield). */
    public boolean bossShielded(Entity entity) {
        for (BossService b : allBosses()) {
            if (b.shielded(entity)) {
                return true;
            }
        }
        return false;
    }

    /** The night count of the boss rhythm (an old save starts from the nights held so far). */
    private int bossNights() {
        if (save.bossNights < 0) {
            save.bossNights = Math.max(0, save.nightsHeld);
        }
        if (save.season <= 0) {
            save.season = 1;
        }
        return save.bossNights;
    }

    /** What the coming real night is in the boss rhythm. */
    private com.mki.nightsiege.core.BossPlan.NightKind comingKind() {
        if (inPlaytest()) {
            return com.mki.nightsiege.core.BossPlan.NightKind.NORMAL;   // the playtest brings its own boss
        }
        return com.mki.nightsiege.core.BossPlan.nightKind(bossNights() + 1, config);
    }

    /** Ticks into a late boss night at which its bosses come (a test night: 15 s; at most half the night). */
    private int lateTicks() {
        if (testRun) {
            return 300;
        }
        return Math.max(20, Math.min(config.bossLateSeconds * 20, totalTicks / 2));
    }

    /** "A, B, C und D" for the chat. */
    private Component bossNames(List<com.mki.nightsiege.core.BossKind> kinds) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < kinds.size(); i++) {
            if (i > 0) {
                out.append(Component.translatable(i == kinds.size() - 1 ? "nightsiege.msg.and" : "nightsiege.msg.comma"));
            }
            out.append(Component.translatable("nightsiege.boss." + kinds.get(i).id()));
        }
        return out;
    }

    /** The Vierer-Nacht: two bosses at a time; the next pair when the last fell (after a pause), at the latest after a while. */
    private void tickFinale(MinecraftServer server) {
        long now = world.getGameTime();
        if (finaleNextAt < 0) {
            finaleNextAt = now + (testRun ? 100L : config.bossGroupPauseSeconds * 20L);
            return;
        }
        if (finaleQueue.isEmpty() || now < finaleNextAt || now % 20 != 0) {
            return;
        }
        List<com.mki.nightsiege.core.BossKind> came = new ArrayList<>();
        int atOnce = Math.max(1, config.bossGroupAtOnce);
        while (came.size() < atOnce && !finaleQueue.isEmpty()) {
            com.mki.nightsiege.core.BossKind kind = finaleQueue.get(0);
            BossService next = new BossService();
            next.plan(kind, config.bossGroupHealth);
            if (!next.spawn(bossHost)) {
                break;
            }
            finaleQueue.remove(0);
            finaleBosses.add(next);
            came.add(kind);
        }
        if (came.isEmpty()) {
            finaleNextAt = now + 40L;
            return;
        }
        finaleNextAt = now + config.bossGroupPairSeconds * 20L;
        traps.refillAll();   // the towers full and whole again for every pair
        broadcast(server, Component.translatable("nightsiege.msg.group_pair", bossNames(came),
                finaleBosses.size(), finaleBosses.size() + finaleQueue.size()).withStyle(ChatFormatting.GOLD));
    }

    /** A late boss night: its bosses all at once, five minutes in, with a last big wave; then no more waves. */
    private void tickLate(MinecraftServer server) {
        if (finaleQueue.isEmpty() || world.getGameTime() % 20 != 0 || totalTicks - ticksLeft < lateTicks()) {
            return;
        }
        List<com.mki.nightsiege.core.BossKind> came = new ArrayList<>();
        for (com.mki.nightsiege.core.BossKind kind : new ArrayList<>(finaleQueue)) {
            BossService next = new BossService();
            next.plan(kind, config.bossLateHealth);
            if (next.spawn(bossHost)) {
                finaleQueue.remove(kind);
                finaleBosses.add(next);
                came.add(kind);
            }
        }
        if (came.isEmpty()) {
            return;
        }
        spawnBatch(server, config.bossFinaleWave);
        broadcast(server, Component.translatable("nightsiege.msg.late_comes", bossNames(came)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    /** A boss of the Vierer-Nacht or the late night fell: the next pair after the pause, or the night is won. */
    private void onFinaleBossDown(MinecraftServer server, BossService fallenBoss) {
        finaleDown++;
        UUID killer = fallenBoss.killer();
        ServerPlayer hero = killer == null ? null : server.getPlayerList().getPlayer(killer);
        com.mki.nightsiege.core.BossKind kind = fallenBoss.kind();
        broadcast(server, Component.translatable(hero != null ? "nightsiege.msg.boss_down_by" : "nightsiege.msg.boss_down",
                Component.translatable("nightsiege.boss." + (kind == null ? "grave_king" : kind.id())),
                hero == null ? "" : hero.getScoreboardName()).withStyle(ChatFormatting.GOLD));
        long now = world.getGameTime();
        boolean allDown = finaleQueue.isEmpty() && finaleBosses.stream().allMatch(BossService::fallen);
        if (allDown && bossVictoryAt < 0) {
            routMonsters();
            bossVictoryAt = now + 60L;
        } else if (finaleNight && !finaleQueue.isEmpty() && finaleBosses.stream().allMatch(BossService::fallen)) {
            finaleNextAt = Math.min(finaleNextAt, now + config.bossGroupPauseSeconds * 20L);   // the pair is down
        }
    }

    /** The Vierer-Nacht is won: a crown for every defender, one on the core; the next four come anew. */
    private void endSeason(MinecraftServer server) {
        int season = save.season;
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        List<String> names = new ArrayList<>();
        for (UUID id : participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            String name = p != null ? p.getScoreboardName() : save.statsOf(id.toString(), null).name;
            names.add(name == null || name.isBlank() ? "?" : name);
        }
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                net.minecraft.world.item.ItemStack crown = SiegeCrown.trophy(season, date, names);
                if (!player.getInventory().add(crown)) {
                    player.drop(crown, false);
                }
            }
        }
        if (world != null && anchor != null) {
            SiegeCrown.crownCore(world, anchor);
        }
        broadcast(server, Component.translatable("nightsiege.msg.season_won", season).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        save.season = season + 1;
        save.groupBosses.clear();
    }

    private void resetFinale(MinecraftServer server) {
        for (BossService b : finaleBosses) {
            b.reset(server);
        }
        finaleBosses.clear();
        finaleQueue.clear();
        finaleNextAt = -1L;
        finaleDown = 0;
        finaleNight = false;
        lateNight = false;
    }

    /** Test nights: the next one is a Vierer-Nacht (or not). */
    public void markTestFinale(boolean on) {
        testKind = on ? com.mki.nightsiege.core.BossPlan.NightKind.GROUP : null;
    }

    /** Test nights: the next one is a late boss night (or not). */
    public void markTestLate(boolean on) {
        testKind = on ? com.mki.nightsiege.core.BossPlan.NightKind.LATE : null;
    }

    /** For the tests: the Vierer-Nacht's or late night's state ("bosses down / bosses in all"). */
    public String finaleState() {
        return finaleNight || lateNight ? finaleDown + "/" + (finaleDown + finaleQueue.size() + (int) finaleBosses.stream().filter(b -> !b.fallen()).count())
                : "-";
    }

    /** Ticks before the end of the night at which the warden comes (a test night: 15 s after the start). */
    private int wardenFinaleTicks() {
        if (testRun) {
            return Math.max(20, totalTicks - 300);
        }
        return Math.max(20, Math.min(config.wardenFinaleSeconds * 20, totalTicks / 2));
    }

    /** Ticks before the end of the night at which the boss comes (a test night: 15 s after the start). */
    private int bossFinaleTicks() {
        if (testRun) {
            return Math.max(20, totalTicks - 300);
        }
        return com.mki.nightsiege.core.BossPlan.finaleTicks(totalTicks, config);
    }

    /** The boss of the coming siege, or null when it is no boss night (drawn once and kept until it falls). */
    private com.mki.nightsiege.core.BossKind upcomingBoss() {
        if (comingKind() != com.mki.nightsiege.core.BossPlan.NightKind.BOSS) {
            return null;
        }
        com.mki.nightsiege.core.BossKind kind = com.mki.nightsiege.core.BossKind.byId(save.nextBoss);
        if (kind == null) {
            if (save.bossBag == null) {
                save.bossBag = new ArrayList<>();
            }
            kind = com.mki.nightsiege.core.BossPlan.draw(save.bossBag, save.lastBoss, random);
            save.nextBoss = kind == null ? null : kind.id();
        }
        return kind;
    }

    public BossService bosses() {
        return bosses;
    }

    public WardenNight warden() {
        return warden;
    }

    /** /nightsiege test <level> waechter <name>: the next test night ends with this warden. */
    public void planTestWarden(com.mki.nightsiege.core.ExpeditionKind kind) {
        testWarden = kind;
    }

    /** Admin and tests: the warden planned for this night comes now. */
    public boolean wardenNow(MinecraftServer server) {
        if (!active || !warden.planned() || warden.spawned()) {
            return false;
        }
        boolean ok = warden.spawn(bossHost);
        if (ok) {
            broadcast(server, Component.translatable("nightsiege.warden.comes", WardenNight.name(warden.kind()))
                    .withStyle(ChatFormatting.GOLD));
        }
        return ok;
    }

    /** The base of the running night (for the gametest). */
    public BaseDefense defense() {
        return defense;
    }

    /** The raid's plan of the running night (for the gametest). */
    public RaidPlan raid() {
        return raid;
    }

    public Nemesis nemesis() {
        return nemesis;
    }

    public Nemesis.Host nemesisHost() {
        return nemesisHost;
    }

    /** Curse stones on the running night (0 outside one). */
    public int nightCurses() {
        return active ? nightCurses : 0;
    }

    private final Nemesis.Host nemesisHost = new Nemesis.Host() {
        @Override
        public ServerLevel world() {
            return world;
        }

        @Override
        public BlockPos anchor() {
            return anchor;
        }

        @Override
        public com.mki.nightsiege.core.SiegeConfig config() {
            return config;
        }

        @Override
        public Random random() {
            return random;
        }

        @Override
        public SiegeSave save() {
            return save;
        }

        @Override
        public boolean testNight() {
            return testRun;
        }

        @Override
        public boolean isNightMob(UUID id) {
            return liveMobs.contains(id);
        }

        @Override
        public void forget(UUID id) {
            if (liveMobs.remove(id)) {
                noteVanished(id);
            }
            digger.forget(id);
            ladders.forget(id);
        }

        @Override
        public Mob spawn(String type, double angle) {
            if (world == null || MobFactory.isUnavailable(type)) {
                return null;
            }
            Mob mob = spawnMob(type, false, angle);
            if (mob == null) {
                return null;
            }
            if (mob instanceof net.minecraft.world.entity.monster.Phantom) {
                mob.setPos(mob.getX(), mob.getY() + 14, mob.getZ());
            }
            events.onSpawned(currentEvent, mob, world);
            liveMobs.add(mob.getUUID());
            spawnedTotal++;
            for (Entity rider : mob.getPassengers()) {
                liveMobs.add(rider.getUUID());
                spawnedTotal++;
            }
            goalUp = Component.translatable("nightsiege.bar.goal_extra");
            if (raid.active() && RaidPlan.eligible(mob)) {
                safe("Angriffsplan", () -> raid.enlist(mob, angle));
            }
            return mob;
        }

        @Override
        public List<ServerPlayer> defenders() {
            return bossHost.defenders();
        }

        @Override
        public Set<UUID> nightPlayers() {
            return new HashSet<>(participants);
        }

        @Override
        public String classOf(UUID player) {
            String name = save.skillsOf(player.toString()).className();
            return name == null ? "" : name;
        }

        @Override
        public void broadcast(Component text) {
            if (world != null) {
                SiegeManager.this.broadcast(world.getServer(), text);
            }
        }

        @Override
        public void notice(Component text) {
            barNotice(text);
        }

        @Override
        public void persist() {
            SiegeManager.this.persist();
        }
    };

    /** Tests: the monsters of the next waves from this list (null: the config's list again). */
    public void testPool(java.util.List<com.mki.nightsiege.core.MobEntry> entries) {
        pool = new MobPool(entries != null ? entries : config.mobs);
    }

    public SiegeCamp camp() {
        return camp;
    }

    public SiegeMachines machines() {
        return machines;
    }

    /** Admin: the machines of a war camp come into this night now. */
    public boolean machinesNow(MinecraftServer server) {
        if (!active || !config.machinesEnabled) {
            return false;
        }
        machines.planNow(siegeLevel);
        return true;
    }

    /** Tests: one machine of the running night at a spot, aiming at the core. */
    public boolean testMachine(com.mki.nightsiege.core.SiegeMachineKind kind, net.minecraft.world.phys.Vec3 at) {
        return active && machines.testSpawn(bossHost, config, kind, at);
    }

    /** Tests: one impact of a standing machine of this kind right at the player. */
    public boolean testMachineImpact(com.mki.nightsiege.core.SiegeMachineKind kind, ServerPlayer player) {
        return active && machines.testImpact(bossHost, config, kind, player);
    }

    /** The day of the server's clock (for the camp). */
    long dayNow(MinecraftServer server) {
        return clockDay(server);
    }

    boolean testPending() {
        return pendingTest != null;
    }

    /** The level a kit is made for now (the camp hands out the coming night's kit). */
    int kitLevel() {
        return levelForKit();
    }

    /** The next test night brings siege machines (the playtest's camp won or stood). */
    void setTestMachines(boolean machines) {
        testMachines = machines;
    }

    /** The class cards as before a night (the camp fight opens them). */
    void campClassPick(ServerPlayer player, int seconds) {
        sendClassPick(player, seconds);
    }

    void sendToCore(ServerPlayer player) {
        toCore(player);
    }

    /** A message for every signed-up player online. */
    void broadcastRegistered(MinecraftServer server, Component message) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isRegistered(p)) {
                p.sendSystemMessage(message);
            }
        }
    }

    /** A defender struck a siege machine down: it counts like a champion's kill. */
    void machineDestroyed(ServerPlayer player) {
        if (!active || testRun || player == null) {
            return;
        }
        nightLog.of(player.getUUID(), player.getScoreboardName()).kills++;
        if (config.skillsEnabled && config.skillPointsPerKill > 0) {
            save.skillsOf(player.getUUID().toString()).award(nightPoints(
                    config.skillPointsPerKill * config.championPointMultiplier));
        }
    }

    /** Admin and tests: the boss planned for this night comes now, with its last wave. */
    public boolean bossNow(MinecraftServer server) {
        if (!active || !bosses.planned() || bosses.spawned()) {
            return false;
        }
        if (bosses.spawn(bossHost)) {
            spawnBatch(server, config.bossFinaleWave);
            return true;
        }
        return false;
    }

    /** What the night lends its boss. */
    private final BossService.Host bossHost = new BossService.Host() {
        @Override
        public ServerLevel world() {
            return world;
        }

        @Override
        public BlockPos anchor() {
            return anchor;
        }

        @Override
        public int level() {
            return siegeLevel;
        }

        @Override
        public com.mki.nightsiege.core.SiegeConfig config() {
            return config;
        }

        @Override
        public Random random() {
            return random;
        }

        @Override
        public List<ServerPlayer> defenders() {
            List<ServerPlayer> list = new ArrayList<>();
            if (world == null) {
                return list;
            }
            for (UUID id : participants) {
                ServerPlayer p = world.getServer().getPlayerList().getPlayer(id);
                if (p != null && !fallen.contains(id) && p.isAlive() && !p.isSpectator() && p.level() == world) {
                    list.add(p);
                }
            }
            return list;
        }

        @Override
        public List<ServerPlayer> watchers() {
            List<ServerPlayer> list = new ArrayList<>();
            if (world == null) {
                return list;
            }
            for (UUID id : participants) {
                ServerPlayer p = world.getServer().getPlayerList().getPlayer(id);
                if (p != null) {
                    list.add(p);
                }
            }
            return list;
        }

        @Override
        public Mob spawnAdd(String entityId, BlockPos spot) {
            if (world == null || liveMobs.size() >= maxLive() || MobFactory.isUnavailable(entityId)) {
                return null;
            }
            Mob mob = MobFactory.spawnAt(world, spot, entityId, siegeLevel, config, random, currentEvent, false);
            if (mob == null) {
                return null;
            }
            liveMobs.add(mob.getUUID());
            spawnedTotal++;
            goalUp = Component.translatable("nightsiege.bar.goal_extra");
            aimAtDefender(world.getServer(), mob);
            return mob;
        }

        @Override
        public BlockPos spawnSpot() {
            if (world == null || anchor == null) {
                return null;
            }
            BlockPos spot = defense.spawnSpot(config, random);
            return spot != null ? spot : MobFactory.findSpawnSpot(world, anchor, config, random, 0.0);
        }

        @Override
        public double baseEdge(double x, double z) {
            BaseScan base = defense.base();
            return base == null ? 0.0 : base.edgeTowards(x, z);
        }

        @Override
        public boolean insideBase(double x, double z) {
            BaseScan base = defense.base();
            return base != null && base.contains((int) Math.floor(x), (int) Math.floor(z));
        }
    };

    /** What the night lends the new attackers (update 1, 27 October 2026). */
    private final Foes.Host foesHost = new Foes.Host() {
        @Override
        public ServerLevel world() {
            return world;
        }

        @Override
        public BlockPos anchor() {
            return anchor;
        }

        @Override
        public BaseScan base() {
            return defense.base();
        }

        @Override
        public int level() {
            return siegeLevel;
        }

        @Override
        public com.mki.nightsiege.core.SiegeConfig config() {
            return config;
        }

        @Override
        public Random random() {
            return random;
        }

        @Override
        public SiegeEvent event() {
            return currentEvent == null ? SiegeEvent.NONE : currentEvent;
        }

        @Override
        public List<ServerPlayer> defenders() {
            return bossHost.defenders();
        }

        @Override
        public Set<UUID> nightPlayers() {
            return new HashSet<>(participants);
        }

        @Override
        public Mob spawn(String entityId, BlockPos spot) {
            if (world == null || spot == null || MobFactory.isUnavailable(entityId)) {
                return null;
            }
            Mob mob = MobFactory.spawnAt(world, spot, entityId, siegeLevel, config, random, currentEvent, false);
            if (mob != null) {
                events.onSpawned(currentEvent, mob, world);
            }
            return mob;
        }

        @Override
        public void addMob(Mob mob) {
            if (mob != null) {
                liveMobs.add(mob.getUUID());
                spawnedTotal++;
                goalUp = Component.translatable("nightsiege.bar.goal_extra");
                if (RaidPlan.eligible(mob)) {
                    safe("Angriffsplan", () -> raid.enlist(mob, Double.NaN));   // a risen revenant goes on with the others
                }
            }
        }

        @Override
        public void notice(Component text) {
            barNotice(text);
        }

        @Override
        public void awardCrates() {
            if (world != null) {
                crates.awardEach(world.getServer(), participants, CrateKind.NORMAL, siegeLevel);
            }
        }

        @Override
        public boolean isTank(ServerPlayer player) {
            String sub = skillTree.subclassOf(save.skillsOf(player.getUUID().toString()));
            return com.mki.nightsiege.core.SkillTree.SUB_SHIELDFIGHTER.equals(sub)
                    || com.mki.nightsiege.core.SkillTree.SUB_BULWARK.equals(sub);
        }

        @Override
        public boolean firmGrip(ServerPlayer player) {
            PlayerSkills own = save.skillsOf(player.getUUID().toString());
            return own.hasClass() && kits.gainLevel(own, own.clazz(), "fester_griff") > 0;
        }
    };

    public Foes foes() {
        return foes;
    }

    public Foes.Host foesHost() {
        return foesHost;
    }

    /** A short line in the boss bar (five seconds), e.g. the treasure raider. */
    public void barNotice(Component text) {
        goalNote = text;
        goalNoteUntil = ticksLeft - 100;
    }

    /** Everyone who defends tonight, the fallen and the drones too. */
    public boolean isNightPlayer(net.minecraft.world.entity.player.Player player) {
        return player != null && active && participants.contains(player.getUUID());
    }

    /** For the tests: a (fake) player joins or leaves tonight's defenders. */
    public void testJoin(UUID id, boolean join) {
        if (join) {
            participants.add(id);
        } else {
            participants.remove(id);
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
                if (participants.contains(player.getUUID())) {
                    bossBar.addPlayer(player);
                } else {
                    bossBar.removePlayer(player);
                }
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
                    config.pointGainMultiplier * CrateEffects.pointFactor(player));
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
        int players = Math.max(1, participants.size());
        double perPlayer = config.perPlayer(playtest.running(), config.playerScaling);
        // What the cap leaves out comes as life (per-player scaling, 29 October 2026).
        overflowHealth = SiegeMath.overflowHealthFactor(SiegeMath.mobsWanted(siegeLevel, players, config, perPlayer), maxLive(), config);
        return (int) Math.round(SiegeMath.mobsPerBatch(siegeLevel, players, config, perPlayer, maxLive())
                * currentEvent.mobCountMultiplier(config) * (save.specialNight ? config.specialNightMobMultiplier : 1.0)
                * Curses.mobFactor());
    }

    /** Waves the whole night brings: one at the start, then one per interval. */
    private int plannedWaves() {
        double extraDelay = 0.0 /* the core tree is gone (27 October 2026) */;
        // A wave starts the tick after its cooldown has run out, so waves lie one tick more apart.
        int interval = Math.max(20, (int) Math.round((config.batchIntervalSeconds + extraDelay) * 20.0 * Curses.waveFactor())) + 1;
        return Math.max(1, (totalTicks - 1) / interval + 1);
    }

    /**
     * Kills the night asks for: the share of its monsters set in the config, never
     * more than that share of what can still come (a full field of monsters keeps
     * waves small, and then fewer kills are needed).
     */
    public int killsRequired() {
        int counted = config.quotaSkipsVanished ? spawnedTotal - vanishedTotal : spawnedTotal;
        return SiegeMath.killsRequired(config.killQuotaShare, killQuota, Math.max(0, counted),
                plannedWaves() - wavesStarted, wantedPerWave());
    }

    public int nightKills() {
        return nightKills;
    }

    /** For the tests: the next wave of the running night comes at once. */
    public void testWave(MinecraftServer server) {
        if (active && world != null) {
            spawnBatch(server);
        }
    }

    private void spawnBatch(MinecraftServer server) {
        spawnBatch(server, 1.0);
    }

    /** One wave, {@code size} times the normal count (the boss's last wave is bigger). */
    private void spawnBatch(MinecraftServer server, double size) {
        if (wavesStarted > 0) {
            waveSurvived(server);
        }
        wavesStarted++;
        safe("Statistik", () -> stats.waveStart(world.getGameTime(), wavesStarted, liveMobs.size()));
        int wanted = (int) Math.round(wantedPerWave() * size);
        int room = maxLive() - liveMobs.size();
        int count = Math.min(wanted, Math.max(0, room));
        if (count > 0) {
            Fx.waveIncoming(server, config, participants);
        }
        int spawned = 0;
        List<Mob> wave = new ArrayList<>();
        Map<String, Integer> waveFoes = new HashMap<>();
        // The raid's plan: where this wave comes from (told on the bar, a horn from that side).
        RaidPlan.Wave plan = null;
        if (count > 0 && raid.active()) {
            try {
                plan = raid.planWave(wavesStarted, siegeLevel);
            } catch (RuntimeException e) {
                NightSiegeMod.LOGGER.error("[Night Siege] Fehler beim Angriffsplan: {}", e.toString(), e);
            }
        }
        besiegers.aimAt(plan == null ? Double.NaN : plan.main());
        for (int i = 0; i < count; i++) {
            String entityId = themeMobId != null
                    ? themeMobId
                    : foes.limit(pool.pick(siegeLevel, random, unavailableMobs), waveFoes, siegeLevel, config,
                            () -> pool.pick(siegeLevel, random, unavailableMobs));   // tunnelers, banners, grapplers only a few
            if (entityId == null) {
                break;
            }
            entityId = config.withoutEffects(events.substitute(currentEvent, entityId, config), siegeLevel, random);
            BlockPos rift = events.riftForLast();
            if (MobFactory.isUnavailable(entityId)) {
                if (unavailableMobs.add(entityId)) {
                    NightSiegeMod.LOGGER.warn("[Night Siege] Mob {} nicht gefunden, wird uebersprungen", entityId);
                }
                continue;
            }
            double angle = raid.angleFor(plan, i, count);
            boolean champion = i < Curses.championsPerWave();   // the curse "Champions": one leads every wave
            Mob mob = rift != null
                    ? MobFactory.spawnAt(world, rift, entityId, siegeLevel, config, random, currentEvent, champion)
                    : spawnMob(entityId, champion, angle);
            if (mob != null) {
                events.onSpawned(currentEvent, mob, world);
                liveMobs.add(mob.getUUID());
                spawnedTotal++;
                for (Entity rider : mob.getPassengers()) {
                    liveMobs.add(rider.getUUID());
                    spawnedTotal++;
                    goalUp = Component.translatable("nightsiege.bar.goal_extra");
                }
                if (plan != null && RaidPlan.eligible(mob)) {
                    raid.enlist(mob, rift != null ? Double.NaN : angle);   // the plan steers it (and picks its fights)
                } else {
                    aimAtDefender(server, mob);
                }
                Fx.mobSpawned(world, config, mob, spawned < config.audibleSpawnsPerBatch);
                if (currentEvent == SiegeEvent.BLOOD_MOON && config.bloodMoonGlow) {
                    SiegeTeams.mark(server, mob, SiegeTeams.BLOOD_MOON, TeamColor.DARK_RED);
                }
                wave.add(mob);
                spawned++;
            }
        }
        if (count < wanted) {
            goalDown = Component.translatable("nightsiege.bar.goal_full");   // the field is full
        } else if (spawned < count) {
            goalDown = Component.translatable("nightsiege.bar.goal_no_room");   // no spot for some of them
        }
        // Phantoms in every night (Simon, 27 October 2026): a few come with the second wave.
        if (wavesStarted == 2 && themeMobId == null) {
            int phantoms = Math.max(0, config.nightPhantoms) + (int) Math.floor(Math.max(0.0, config.nightPhantomsPerLevel) * siegeLevel);
            for (int i = 0; i < Math.min(phantoms, 12); i++) {
                Mob phantom = spawnMob("minecraft:phantom", false);
                if (phantom == null) {
                    continue;
                }
                phantom.setPos(phantom.getX(), phantom.getY() + 14, phantom.getZ());
                liveMobs.add(phantom.getUUID());
                spawnedTotal++;
                aimAtDefender(server, phantom);
                if (currentEvent == SiegeEvent.BLOOD_MOON && config.bloodMoonGlow) {
                    SiegeTeams.mark(server, phantom, SiegeTeams.BLOOD_MOON, TeamColor.DARK_RED);
                }
            }
        }
        // Some of them carry ladders; a sapper creeper may come along (it does not count for the kill goal).
        besiegers.afterWave(world, anchor, defense.base(), wave, siegeLevel, config, random, currentEvent,
                sapper -> liveMobs.add(sapper.getUUID()));
        if (themeMobId == null) {
            safe("Schatz-Pluenderer", () -> foes.afterWave(foesHost, wavesStarted));
        }
        safe("Nemesis", () -> nemesis.onWave(nemesisHost, wavesStarted));   // the nemeses come with the second wave
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

    /** A monster of the night left the field: slain, or gone without a kill (blown up, fled …). */
    private void noteVanished(UUID id) {
        if (!slainNightMobs.remove(id)) {
            vanishedTotal++;
            if (config.quotaSkipsVanished) {
                goalDown = Component.translatable("nightsiege.bar.goal_vanished");
            }
        }
    }

    private void pruneMobs() {
        Iterator<UUID> it = liveMobs.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity entity = world.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                digger.forget(id);
                ladders.forget(id);
                it.remove();
                noteVanished(id);
            }
        }
    }

    // ------------------------------------------------------------------
    // ending
    // ------------------------------------------------------------------

    /** Spawns a monster outside the walls of the base, or in the old ring if none was found. */
    private Mob spawnMob(String entityId, boolean elite) {
        return spawnMob(entityId, elite, Double.NaN);
    }

    /** As above, on the ground in front of the walls towards an angle (the raid's plan), if there is some. */
    private Mob spawnMob(String entityId, boolean elite, double angle) {
        if (MobFactory.isUnavailable(entityId)) {
            return null;
        }
        BlockPos spot = Double.isNaN(angle) ? null : raid.spawnSpot(angle);
        if (spot == null) {
            spot = defense.spawnSpot(config, random);
        }
        if (spot == null) {
            spot = MobFactory.findSpawnSpot(world, anchor, config, random, 0.0);
        }
        return spot == null ? null
                : MobFactory.spawnAt(world, spot, entityId, siegeLevel, config, random, currentEvent, elite);
    }

    /** Playtest kits for every player around a spot. Returns how many got one. */
    public int giveKits(MinecraftServer server, ServerLevel level, BlockPos center, int siegeLevelWanted, boolean nightStarts) {
        int count = 0;
        for (ServerPlayer player : defendersNear(server, level, center, true)) {
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
        double factor = config.damageFactor(skills.className(), skillTree.subclassOf(skills));
        // Bollwerk-Stand (Simon, 25 October 2026): the shield fighter on the shield-throw way
        // hits 5 % harder for every monster within 4 blocks, at most 6 (+30 %).
        if (SkillTree.SUB_SHIELDFIGHTER.equals(skillTree.subclassOf(skills)) && SkillTree.has(skills, "g_shieldthrow")) {
            double reach = com.mki.nightsiege.core.SkillValues.get("g_shieldthrow", "near", 4.0);
            int near = player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(reach),
                    m -> m instanceof net.minecraft.world.entity.monster.Enemy && m.isAlive() && !Companions.isCompanion(m)).size();
            factor *= 1.0 + Math.min(com.mki.nightsiege.core.SkillValues.get("g_shieldthrow", "bonuscap", 0.3),
                    com.mki.nightsiege.core.SkillValues.get("g_shieldthrow", "bonus", 0.05) * near);
        }
        factor *= config.lowLevelDealt(currentLevel());   // the low levels hit a little harder (28.10.)
        return (float) factor;
    }

    /** Server balancing: factor on the damage this player takes from monsters. */
    /** How much tougher tonight's monsters are than plain ones (level and event); 1 outside a night uses the kit level. */
    public double mobHealthFactor() {
        int level = active ? siegeLevel : levelForKit();
        SiegeEvent event = active && currentEvent != null ? currentEvent : SiegeEvent.NONE;
        return com.mki.nightsiege.core.SiegeMath.healthMultiplier(level, config) * event.healthMultiplier(config);
    }

    /** Messages already logged by {@link #safe}: one line per kind of failure, not one per tick. */
    private final java.util.Set<String> failures = new java.util.HashSet<>();

    /** Runs a part of the night that must never take the server down (the summoner's companions are new). */
    private void safe(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            if (failures.add(what + e)) {
                NightSiegeMod.LOGGER.error("[Night Siege] Fehler bei {}: {}", what, e.toString(), e);
            }
        }
    }

    /** How much harder tonight's monsters hit than plain ones (level and event). */
    public double mobDamageFactor() {
        int level = active ? siegeLevel : levelForKit();
        SiegeEvent event = active && currentEvent != null ? currentEvent : SiegeEvent.NONE;
        return com.mki.nightsiege.core.SiegeMath.damageMultiplier(level, config) * event.damageMultiplier(config);
    }

    /** Factor on the damage this monster deals to players (config monsterDamage / monsterDamageByType). */
    public float monsterDamageFactor(Entity attacker) {
        if (attacker == null) {
            return 1f;
        }
        return (float) (config.monsterDamageFactor(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString())
                * bossDamageFactor(attacker)   // the warlord's banner
                * Foes.bannerFactor(attacker)   // a standard bearer's aura (update 1)
                * Curses.damageFactor()   // the curse "Blutdurst" (update 2)
                * Nemesis.damageFactor(attacker));   // a nemesis hits harder
    }

    /** Zombies and spiders against warriors and guardians (config zombieSpiderVsMelee, Simon 25 October 2026). */
    public float zombieSpiderFactor(ServerPlayer player) {
        PlayerSkills skills = save.skillsOf(player.getUUID().toString());
        com.mki.nightsiege.core.SkillClass clazz = skills.clazz();
        return clazz == com.mki.nightsiege.core.SkillClass.WARRIOR || clazz == com.mki.nightsiege.core.SkillClass.GUARDIAN
                ? (float) config.zombieSpiderVsMelee : 1f;
    }

    public float damageTakenFactor(ServerPlayer player) {
        PlayerSkills skills = save.skillsOf(player.getUUID().toString());
        // A lone defender of a night takes soloDamageTaken of it (per-player scaling, 29 October 2026).
        double solo = active && participants.contains(player.getUUID())
                ? SiegeMath.damageTakenForGroup(participants.size(), config) : 1.0;   // by group size (29.10.)
        return (float) (config.damageTakenFactor(skills.className(), skillTree.subclassOf(skills))
                * config.lowLevelTaken(currentLevel()) * solo);   // the low levels are a little kinder (28.10.)
    }

    /** Life factor of tonight's monsters for the ones the cap left out (overflowHealth); 1 outside a night. */
    private double overflowHealth = 1.0;

    public double overflowHealthFactor() {
        // and the life by group size (mobHealthByPlayers, 29.10.)
        return active ? overflowHealth * SiegeMath.healthForGroup(Math.max(1, participants.size()), config) : 1.0;
    }

    /** True while a playtest runs (its nights, camps and dungeons scale per player, Simon 28.10.). */
    public boolean inPlaytest() {
        return playtest.running();
    }

    /** Monsters alive at once at most: in a playtest the cap grows with the players. */
    public int maxLive() {
        if (!playtest.running()) {
            return SiegeMath.liveCap(Math.max(1, participants.size()), config);   // livePerPlayer, 29.10.
        }
        int players = Math.max(1, participants.size());
        return (int) Math.min(120, Math.round(config.maxLiveMobs * (1.0 + config.playtestLivePerPlayer * (players - 1))));
    }

    /** Notes a defender in the night's protocol, with class and subclass. */
    private void logPlayer(ServerPlayer player) {
        safe("Statistik", () -> stats.addPlayer(player));
        NightLog.Line line = nightLog.of(player.getUUID(), player.getScoreboardName());
        PlayerSkills skills = save.skillsOf(player.getUUID().toString());
        line.clazz = skills.className().toLowerCase(java.util.Locale.ROOT);
        line.subclass = skillTree.subclassOf(skills);
    }

    /** Writes the night's protocol lines. */
    private void logNight(String result, int blasted, int required) {
        safe("Statistik", () -> stats.endNight(world == null ? 0L : world.getGameTime(), result, required, nightKills, liveMobs));
        try {
            nightLog.dug += digger.takeBroken();
            String eventName = bossLabel != null ? bossLabel : currentEvent.name().toLowerCase(java.util.Locale.ROOT);
            bossLabel = null;
            NightLog.Night night = new NightLog.Night(nightNumber, siegeLevel, testRun, eventName,
                    save.specialNight, wavesStarted, spawnedTotal, nightKills, required, fallen.size(), result,
                    world == null ? 0L : world.getGameTime(), blasted);
            nightLog.write(night);
            if (!"abgebrochen".equals(result) && world != null) {
                com.mki.nightsiege.core.NightStatsData summary = nightLog.summary(night);
                if (!testRun && !inPlaytest() && nightNumber >= 0) {
                    safe("Rekorde", () -> takeRecords(world.getServer(), summary.held()));
                }
                showNightStats(world.getServer(), summary);
            }
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Nacht-Protokoll fehlgeschlagen: {}", e.toString());
        }
    }

    /** Records broken tonight, per player (shown with the statistics). */
    private void takeRecords(MinecraftServer server, boolean held) {
        if (save.records == null) {
            save.records = new com.mki.nightsiege.core.NightRecords();
        }
        String date = java.time.LocalDate.now().toString();
        List<com.mki.nightsiege.core.NightRecords.Broken> broken =
                save.records.offer(nightLog.recordLines(), held, nightNumber, date);
        persist();
        Map<String, Integer> shown = new HashMap<>();
        for (com.mki.nightsiege.core.NightRecords.Broken b : broken) {
            ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(b.uuid()));
            int n = shown.merge(b.uuid(), 1, Integer::sum);
            if (player == null || n > 3) {
                continue;   // three messages at most (Simon 28.10.)
            }
            Component what = recordText(b);
            if (n == 1) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 120, 10));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        Component.translatable("nightsiege.records.new").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(what));
                player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.1f);
            }
            player.sendSystemMessage(Component.translatable("nightsiege.records.chat", what).withStyle(ChatFormatting.GOLD));
        }
    }

    /** "Schaden als Berserker: 312 ❤" and the like. */
    private static Component recordText(com.mki.nightsiege.core.NightRecords.Broken b) {
        Component who = b.subclass() != null && !b.subclass().isBlank() && !"class".equals(b.kind())
                ? Component.translatable("nightsiege.subclass." + b.subclass())
                : Component.translatable("nightsiege.class." + b.clazz().toLowerCase(java.util.Locale.ROOT));
        if ("streak".equals(b.stat())) {
            return Component.translatable("nightsiege.records.streak_line", (int) b.value());
        }
        String value = "kills".equals(b.stat()) ? String.valueOf((int) b.value())
                : String.format(java.util.Locale.GERMANY, "%.1f ❤", b.value() / 2.0);
        return Component.translatable("nightsiege.records.line." + b.kind(),
                Component.translatable("nightsiege.records.stat." + b.stat()), who, value);
    }

    /** The statistics of the last night, for /nightsiege statistik. */
    private com.mki.nightsiege.core.NightStatsData lastNightStats;
    private final Set<UUID> lastNightDefenders = new HashSet<>();

    /** Opens the statistics window for everyone who defended tonight. */
    private void showNightStats(MinecraftServer server, com.mki.nightsiege.core.NightStatsData data) {
        if (server == null || data == null || data.rows.isEmpty()) {
            return;
        }
        lastNightStats = data;
        lastNightDefenders.clear();
        lastNightDefenders.addAll(nightLog.ids());
        lastNightDefenders.addAll(participants);
        for (UUID id : lastNightDefenders) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            if (ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE)) {
                ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("nightstats",
                        Json.toJson(withRecords(data, player, false))));
            }
            player.sendSystemMessage(Component.translatable("nightsiege.stats.chat")
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/nightsiege statistik"))));
        }
    }

    /** /nightsiege statistik: the window of the last night again. Returns false when there is none. */
    public boolean resendNightStats(ServerPlayer player) {
        if (lastNightStats == null || player == null
                || !ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE)) {
            return false;
        }
        ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("nightstats_now",
                Json.toJson(withRecords(lastNightStats, player, false))));
        return true;
    }

    /** /nightsiege rekorde: the statistics window on its records page (works without a night too). */
    public boolean sendRecords(ServerPlayer player) {
        if (player == null || !ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE)) {
            return false;
        }
        com.mki.nightsiege.core.NightStatsData data = lastNightStats;
        if (data == null) {
            data = new com.mki.nightsiege.core.NightStatsData();
            data.recordsOnly = true;
        }
        ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("nightstats_now",
                Json.toJson(withRecords(data, player, true))));
        return true;
    }

    /** Admin: every record goes (the progress reset keeps them; this is the only way to clear them). */
    public void clearRecords() {
        save.records = new com.mki.nightsiege.core.NightRecords();
        persist();
    }

    /** A copy of the night's data with this player's view of the records. */
    private com.mki.nightsiege.core.NightStatsData withRecords(com.mki.nightsiege.core.NightStatsData data,
                                                                ServerPlayer player, boolean open) {
        com.mki.nightsiege.core.NightStatsData copy = Json.fromJson(Json.toJson(data), com.mki.nightsiege.core.NightStatsData.class);
        if (copy == null) {
            copy = data;
        }
        if (save.records != null) {
            copy.records = save.records.view(player.getUUID().toString());
        }
        copy.openRecords = open;
        return copy;
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
        boolean finale = finaleNight;
        boolean late = lateNight;
        com.mki.nightsiege.core.BossPlan.NightKind kindTonight = tonightKind;
        tonightKind = com.mki.nightsiege.core.BossPlan.NightKind.NORMAL;
        boolean manyDown = (finale || late) && finaleQueue.isEmpty() && !finaleBosses.isEmpty()
                && finaleBosses.stream().allMatch(BossService::fallen);
        boolean bossNight = bosses.planned() || finale || late;
        boolean bossDown = finale || late ? manyDown : bosses.fallen();
        com.mki.nightsiege.core.BossKind bossKind = bosses.kind();
        bossLabel = finale ? "vierer" : late ? "doppel" : bossKind == null ? null : "boss_" + bossKind.id();
        bosses.reset(server);
        resetFinale(server);
        boolean wardenNight = warden.spawned();
        boolean wardenDown = warden.fallen();
        com.mki.nightsiege.core.ExpeditionKind wardenKind = warden.kind();
        if (bossLabel == null && wardenNight && wardenKind != null) {
            bossLabel = "waechter_" + wardenKind.id();
        }
        warden.reset(server);
        machines.reset(server);
        bossVictoryAt = -1L;
        safe("Nemesis", () -> nemesis.endNight(nemesisHost));
        int curses = nightCurses;
        Curses.end();
        nightCurses = 0;
        clearRemainingMobs();
        digger.reset();
        ladders.reset();
        besiegers.reset();
        raid.reset();
        foes.reset(world);
        companions.dismissAll(server);
        mendBase(server);
        endKits(server);

        // A boss night is won by the boss's fall alone, the kill goal does not count; so is a night whose warden came.
        int required = bossNight || wardenNight ? 0 : killsRequired();
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
        if (finale) {
            // The Vierer-Nacht: won, a crown for every defender.
            if (won && bossDown) {
                endSeason(server);
            }
        } else if (bosses.planned() && bossDown && won) {
            save.lastBoss = bossKind == null ? null : bossKind.id();
            save.nextBoss = null;
            if (bossKind != null) {
                save.groupBosses.add(bossKind.id());   // one of the four of the next Vierer-Nacht
            }
        }
        // The rhythm's count: a lost boss night or Vierer-Nacht comes again.
        bossNights();
        save.bossNights = com.mki.nightsiege.core.BossPlan.countNight(save.bossNights, kindTonight, won && bossDown);
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
            // The reward of a won night: a crate for every defender (better odds in event and fragment nights).
            // The boss's crate is as good as a fragment night's (Simon, 25 October 2026).
            // So is the warden's (Simon, 29 October 2026), with ability points for those still standing.
            CrateKind kind = save.specialNight || (bossNight && bossDown) || wardenDown ? CrateKind.FRAGMENT
                    : currentEvent != SiegeEvent.NONE ? CrateKind.EVENT : CrateKind.NORMAL;
            crates.award(server, participants, kind, siegeLevel, curses);
            if (curses > 0) {
                awardCursePoints(server, curses);
            }
            if (wardenDown) {
                awardWardenPoints(server, wardenKind);
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
            if (config.failureBlast && world != null && anchor != null) {
                destroyed = Demolition.blast(world, anchor, config);
                broadcast(server, Component.translatable("nightsiege.msg.lost", destroyed)
                        .withStyle(ChatFormatting.DARK_RED));
            } else {
                broadcast(server, Component.translatable("nightsiege.msg.lost_no_penalty")
                        .withStyle(ChatFormatting.DARK_RED));
            }
            blasted = destroyed;
            if (config.defeatSkillLoss > 0) {
                loseSkills(server);
            }
            broadcast(server, Component.translatable("nightsiege.msg.night_again", siegeLevel)
                    .withStyle(ChatFormatting.RED));
        }

        logNight(result, blasted, required);
        save.specialNight = false;
        if (curses > 0 && save.curses != null) {
            save.curses.clear();   // the stones lay for this night: the altar is empty again
        }
        scheduleNextAfterNight(server);
        safe("Kundschafter", () -> expeditions.afterNight(server));   // the morning after: maybe a dungeon
        persist();
        clearSiegeState();
    }

    /** After a real night (won, lost or broken off) the next invitation comes a few days later. */
    private void scheduleNextAfterNight(MinecraftServer server) {
        long day = siegeDay >= 0 ? siegeDay : clockDay(server);
        save.nextSiegeDay = nextSiegeDayAfter(server, day);
        save.clearVotes();
        siegeDay = -1L;
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
        bossLabel = finaleNight ? "vierer" : lateNight ? "doppel" : bosses.kind() == null ? null : "boss_" + bosses.kind().id();
        tonightKind = com.mki.nightsiege.core.BossPlan.NightKind.NORMAL;
        bosses.reset(server);
        resetFinale(server);
        warden.reset(server);
        machines.reset(server);
        bossVictoryAt = -1L;
        safe("Nemesis", () -> nemesis.endNight(nemesisHost));
        Curses.end();   // broken off: the stones stay on the altar for the next night
        nightCurses = 0;
        clearRemainingMobs();
        digger.reset();
        ladders.reset();
        besiegers.reset();
        raid.reset();
        foes.reset(world);
        companions.dismissAll(server);
        mendBase(server);
        endKits(server);
        logNight("abgebrochen", 0, killsRequired());
        if (!testRun) {
            scheduleNextAfterNight(server);
            persist();
        }
        testRun = false;
        broadcast(server, Component.translatable("nightsiege.msg.aborted").withStyle(ChatFormatting.GRAY));
        clearSiegeState();
    }

    /** The night is over (or did not come): every kit goes back, fallen defenders return to the core. */
    private void endKits(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            endNightFor(player);
        }
        Drones.stopAll(server);
        secondChance.clear();
        secondChanceUsed.clear();
        traps.resetZones();
        traps.resetSpotter();
        traps.refillAll();
        GainGear.endNight(server);
        traps.recycle(server);
    }

    /** True when some signed-up player owns this crate gain (in any class): the night then prepares for it. */
    private boolean anyoneHas(String gainId) {
        if (save.skills == null) {
            return false;
        }
        for (com.mki.nightsiege.core.PlayerSkills skills : save.skills.values()) {
            if (skills != null && skills.gains != null) {
                for (java.util.Map<String, Integer> byClass : skills.gains.values()) {
                    if (byClass != null && byClass.getOrDefault(gainId, 0) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The trowel: broken base blocks near a spot come back now. */
    public int restoreBaseNear(BlockPos center, double radius) {
        return defense.restoreNear(center, radius);
    }

    /** A message to every defender of the running night. */
    public void broadcastDefenders(Component message) {
        MinecraftServer server = world == null ? null : world.getServer();
        if (server == null) {
            return;
        }
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** One defender after the night: own inventory back; a spectator stands at the core again. */
    private void endNightFor(ServerPlayer player) {
        boolean drone = Drones.isDrone(player);
        boolean watching = (player.isSpectator() || drone) && fallen.contains(player.getUUID());
        if (drone) {
            Drones.stop(player);
            if (player.gameMode() == net.minecraft.world.level.GameType.ADVENTURE) {
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
        }
        if (kits.hasStash(player)) {
            kits.restore(player);
        }
        if (watching || player.isSpectator() && fallen.contains(player.getUUID())) {
            if (player.isSpectator() || drone && player.gameMode() == net.minecraft.world.level.GameType.ADVENTURE) {
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
            toCore(player);
        }
        if (watching) {
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
        }
    }

    /** Puts a player on top of the core (or leaves them where they are without one). */
    private void toCore(ServerPlayer player) {
        ServerLevel level = world;
        BlockPos at = anchor;
        if (level == null || at == null) {
            CoreRecord core = mainCore();
            MinecraftServer server = player.level().getServer();
            if (core == null || server == null) {
                return;
            }
            level = levelByName(server, core.dimension);
            at = new BlockPos(core.x, core.y, core.z);
        }
        if (level == null) {
            return;
        }
        player.teleportTo(level, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, java.util.Set.of(),
                player.getYRot(), player.getXRot(), true);
    }

    /**
     * A defender takes fatal damage in the night: instead of dying they watch the
     * rest of it as a spectator near the core. Returns false to cancel the death.
     */
    private boolean allowNightDeath(Object entity) {
        if (!(entity instanceof ServerPlayer player) || !active || !config.nightKits
                || !participants.contains(player.getUUID()) || fallen.contains(player.getUUID())) {
            return true;
        }
        kits.rememberGameMode(player);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.clearFire();
        SkillService.clear(player);
        // A small steampunk drone instead of a spectator (Simon, 26 October 2026).
        Drones.start(player);
        markFallen(player);
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        if (kits.gainLevel(own, own.clazz(), "zweite_chance") > 0 && secondChanceUsed.add(player.getUUID())) {
            // The second chance: back at the core after 20 seconds, once a night.
            secondChance.put(player.getUUID(), world == null ? 0L : world.getGameTime() + 400L);
            player.sendSystemMessage(Component.translatable("nightsiege.msg.second_chance").withStyle(ChatFormatting.GOLD));
        } else {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.drone_start").withStyle(ChatFormatting.GRAY));
        }
        return false;
    }

    /** Defenders waiting for their second chance, with the tick it comes. */
    private final Map<UUID, Long> secondChance = new HashMap<>();
    /** Defenders who used their second chance tonight. */
    private final Set<UUID> secondChanceUsed = new HashSet<>();

    /** Once a second: spectators stay near the core; a second chance brings a defender back. */
    private void tickSpectators(MinecraftServer server) {
        if (world == null || anchor == null) {
            return;
        }
        double r = config.spectatorRadius;
        long now = world.getGameTime();
        for (UUID id : new ArrayList<>(fallen)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isSpectator() && !Drones.isDrone(player)) {
                continue;
            }
            Long back = secondChance.get(id);
            if (back != null && now >= back) {
                secondChance.remove(id);
                fallen.remove(id);
                Drones.stop(player);
                SiegeSave.KitStash stash = save.kitStashes == null ? null : save.kitStashes.get(id.toString());
                String mode = stash == null || stash.gameMode == null || stash.gameMode.isEmpty() ? "survival" : stash.gameMode;
                player.setGameMode(net.minecraft.world.level.GameType.byName(mode, net.minecraft.world.level.GameType.SURVIVAL));
                if (stash != null) {
                    stash.gameMode = "";
                }
                toCore(player);
                player.setHealth(player.getMaxHealth());
                SkillService.apply(player, save.skillsOf(id.toString()), skillTree);
                broadcast(server, Component.translatable("nightsiege.msg.second_chance_back",
                        player.getScoreboardName()).withStyle(ChatFormatting.GREEN));
                continue;
            }
            if (player.level() != world || player.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5) > r * r) {
                toCore(player);
                player.sendSystemMessage(Component.translatable("nightsiege.msg.spectator_range", (int) r)
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private void clearSiegeState() {
        Wounds.clear();
        openNight = false;
        trapsHanded.clear();
        spawnBlockWorld = null;
        if (world != null) {
            events.end(world.getServer(), world, currentEvent);
            releaseNight(world.getServer());
        }
        world = null;
        anchor = null;
        currentEvent = SiegeEvent.NONE;
        themeMobId = null;
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

    /** A night won under curse stones: skill points for everyone still standing (update 2). */
    /** The warden fell: ability points for every defender still standing. */
    private void awardWardenPoints(MinecraftServer server, com.mki.nightsiege.core.ExpeditionKind kind) {
        int amount = config.skillsEnabled ? config.wardenPoints : 0;
        if (amount > 0) {
            for (UUID id : participants) {
                if (!fallen.contains(id)) {
                    save.skillsOf(id.toString()).award(amount);
                }
            }
        }
        broadcast(server, Component.translatable("nightsiege.warden.paid",
                kind == null ? Component.empty() : WardenNight.name(kind), amount).withStyle(ChatFormatting.GOLD));
    }

    private void awardCursePoints(MinecraftServer server, int curses) {
        int amount = config.skillsEnabled ? Curses.points(config, curses) : 0;
        if (amount > 0) {
            for (UUID id : participants) {
                if (!fallen.contains(id)) {
                    save.skillsOf(id.toString()).award(amount);
                }
            }
        }
        broadcast(server, Component.translatable("nightsiege.curse.paid", curses, amount,
                Math.round(Curses.extraCrateChance(config, curses) * 100)).withStyle(ChatFormatting.LIGHT_PURPLE));
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

    /** A monster of a fight by day: a guard of the war camp (not its workshop), of a dungeon or of an old dungeon core. */
    static boolean foughtOutsideTheNight(Mob mob) {
        return SiegeCamp.isCampMob(mob) && !mob.entityTags().contains(SiegeCamp.TAG_WORKSHOP)
                || Expeditions.isExpeditionMob(mob);
    }

    /** Takes Object so the handler keeps compiling across Fabric API versions. */
    private void onDeath(Object entity, net.minecraft.world.damagesource.DamageSource source) {
        if (entity instanceof LivingEntity dead) {
            safe("Todestreffer", () -> combat.afterKillingHit(dead, source));
        }
        if (active && entity instanceof LivingEntity fell && world != null) {
            safe("Gegner", () -> foes.onDeath(foesHost, fell, source));   // revenants' graves, the treasure
        }
        if (active && entity instanceof Mob slainMob && world != null) {
            safe("Nemesis", () -> nemesis.onDeath(nemesisHost, slainMob, source));   // a nemesis slain: its trophy
        }
        if (active && entity instanceof Mob killed && liveMobs.contains(killed.getUUID())) {
            nightKills++;
            slainNightMobs.add(killed.getUUID());
            safe("Statistik", () -> stats.mobDied(killed, source));
            // A monster of the night can be raised by a summoner of the dead; souls for those near.
            safe("Seelen", () -> companions.onMonsterDeath(killed, false));
        } else if (entity instanceof Mob fought && foughtOutsideTheNight(fought)) {
            // The war camp's guards and the dungeons' monsters give souls and corpses too (Simon's
            // playtest, 28 October 2026: "the soulcatcher collects no souls in dungeons").
            safe("Seelen", () -> companions.onMonsterDeath(fought, false));
        }
        if (active && entity instanceof Mob wardenMob && world != null && warden.onDeath(wardenMob, source) && bossVictoryAt < 0) {
            onWardenDown(world.getServer());
        }
        if (active && entity instanceof Mob mob && BossService.isBossOrPart(mob)) {
            if (bosses.onDeath(mob, source) && world != null && bossVictoryAt < 0) {
                onBossDown(world.getServer(), mob);
            }
            for (BossService b : new ArrayList<>(finaleBosses)) {
                if (b.onDeath(mob, source) && world != null) {
                    onFinaleBossDown(world.getServer(), b);
                }
            }
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (!active || !participants.contains(id)) {
            return;
        }
        if (!fallen.contains(id)) {
            safe("Nemesis", () -> nemesis.onDefenderKilled(nemesisHost, player, source));   // the killer flees and comes back
        }
        markFallen(player);
    }

    /** A defender is out for the night (dead, or watching as a spectator). */
    private void markFallen(ServerPlayer player) {
        UUID id = player.getUUID();
        fallen.add(id);
        companions.dismiss(player);
        nightLog.of(id, player.getScoreboardName()).deaths++;
        safe("Statistik", () -> stats.playerFell(player));
        save.statsOf(id.toString(), player.getScoreboardName()).fallen++;
        MinecraftServer server = player.level().getServer();
        broadcast(server, Component.translatable("nightsiege.msg.defender_down",
                player.getScoreboardName(), countStanding()).withStyle(ChatFormatting.RED));
        Fx.defenderDown(server, config, participants);
    }

    /** The boss has fallen: the night is won; the monsters left flee, the night ends in three seconds. */
    private void onBossDown(MinecraftServer server, Mob boss) {
        bossVictoryAt = world.getGameTime() + 60L;
        routMonsters();
        UUID killer = bosses.killer();
        ServerPlayer hero = killer == null ? null : server.getPlayerList().getPlayer(killer);
        broadcast(server, Component.translatable(hero != null ? "nightsiege.msg.boss_down_by" : "nightsiege.msg.boss_down",
                Component.translatable("nightsiege.boss." + bosses.kind().id()),
                hero == null ? "" : hero.getScoreboardName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    /** The warden has fallen: the night is won; the monsters left flee, the night ends in three seconds. */
    private void onWardenDown(MinecraftServer server) {
        bossVictoryAt = world.getGameTime() + 60L;
        routMonsters();
        UUID killer = warden.killer();
        ServerPlayer hero = killer == null ? null : server.getPlayerList().getPlayer(killer);
        broadcast(server, Component.translatable(hero != null ? "nightsiege.warden.down_by" : "nightsiege.warden.down",
                WardenNight.name(warden.kind()), hero == null ? "" : hero.getScoreboardName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    /** The monsters still standing flee in a puff of smoke. */
    private void routMonsters() {
        for (UUID id : new ArrayList<>(liveMobs)) {
            Entity other = world.getEntity(id);
            if (other instanceof Mob mob && mob.isAlive()) {
                world.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, mob.getX(), mob.getY() + 0.8, mob.getZ(),
                        8, 0.3, 0.5, 0.3, 0.02);
                mob.discard();
            }
        }
        liveMobs.clear();
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
        RetiredItems.purgePlayer(player);
        events.forget(player.getUUID());
        HealthView.forget(player.getUUID());
        Drones.cleanup(player);
        Drones.sendTo(player);
        if (bosses.spawned()) {
            bosses.syncTo(player);
        }
        MinecraftServer joinServer = player.level().getServer();
        boolean stillTonight = (active && participants.contains(player.getUUID())) || pendingTest != null
                || (joinServer != null && nightComing(joinServer));
        if (kits.hasStash(player) && !stillTonight) {
            // The night ended while they were away: their own things come back now.
            boolean watching = player.isSpectator();
            kits.restore(player);
            if (watching) {
                if (player.isSpectator()) {
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                }
                toCore(player);
            }
        }
        camp.onJoin(player);   // a kit from a camp fight before logging out goes back
        expeditions.onJoin(player);   // the same for a dungeon fight
        if (active && config.nightKits && participants.contains(player.getUUID()) && fallen.contains(player.getUUID())
                && !player.isSpectator()) {
            Drones.start(player);   // fallen before they left: back up as a drone
        }
        crates.deliverPending(player);
        playtest.onJoin(player);   // things back after a playtest that ended while away
        // Skills gelten nur waehrend einer Belagerung. Beim Betreten werden
        // daher Reste entfernt, etwa nach einem Absturz mitten in der Nacht.
        if (active && participants.contains(player.getUUID()) && !fallen.contains(player.getUUID())) {
            SkillService.apply(player, save.skillsOf(player.getUUID().toString()), skillTree);
        } else {
            SkillService.clear(player);
        }
        giveStarterCore(player);
        if (isRegistered(player)) {
            giveGuideBook(player);
            MinecraftServer server = player.level().getServer();
            String id = player.getUUID().toString();
            boolean answered = (save.accepted != null && save.accepted.contains(id))
                    || (save.declined != null && save.declined.contains(id));
            if (server != null && voteOpen(server) && !answered) {
                sendInvitation(player);
            }
        }
        if (config.requireClientMod && !SimBots.isBot(player)) {
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
    /** Tells every player with the mod what their ultimate slot shows. */
    private void sendUltHud(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendUltHud(player);
        }
    }

    /** The ult slots and the ability bar of one player. */
    private void sendUltHud(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        {
            if (!ServerPlayNetworking.canSend(player, CoreNetworking.UltHudPayload.TYPE)) {
                return;
            }
            PlayerSkills own = effectiveSkills(player);
            com.mki.nightsiege.core.SkillNode node = isRegistered(player) ? skillTree.ultimateOf(own) : null;
            com.mki.nightsiege.core.UltState state = new com.mki.nightsiege.core.UltState();
            // The ability keys are Night Siege's alone in the night, in every fight (camp, dungeon) and
            // whenever a kit is on (Simon 28.10.: in the dungeon Q/F dropped and swapped instead).
            state.keys = (active && participants.contains(player.getUUID())) || kits.hasStash(player)
                    || camp.isFighter(player) || expeditions.isFighter(player);
            state.kitsOn = config.nightKits;
            state.clazz = own.hasClass() ? own.clazz().name().toLowerCase(java.util.Locale.ROOT) : "";
            state.sub = own.hasClass() ? skillTree.subclassOf(own) : "";
            String second = SecondUlts.gainFor(own.clazz());
            if (second != null && own.hasClass() && own.gainStage(own.clazz(), second) > 0) {
                state.has2 = true;
                state.icon2 = com.mki.nightsiege.core.CrateGains.icon(second);
                state.name2 = "nightsiege.gain." + second;
                state.cooldown2Ticks = SecondUlts.secondsLeft(player) * 20;
                state.total2Ticks = SecondUlts.COOLDOWN_TICKS;
                state.live = liveSkillsFor(player) != null;
            }
            if (node != null) {
                int rank = Math.max(1, own.levelOf(node.id()));
                state.has = true;
                state.node = node.id();
                state.icon = node.icon();
                state.totalTicks = combat.cooldownTotal(player.getUUID(), CombatEffects.cooldownFor(rank));
                state.cooldownTicks = combat.cooldownLeft(player.getUUID(), now);
                state.live = liveSkillsFor(player) != null;
            }
            if (isRegistered(player) && own.hasClass()) {
                state.live = state.live || liveSkillsFor(player) != null;
                state.slots = safeSlots(player);
                try {
                    state.gains = GainKeys.slots(this, player, now);
                } catch (RuntimeException e) {
                    NightSiegeMod.LOGGER.warn("[Night Siege] Gewinn-Tasten: {}", e.toString());
                }
                // Fähigkeit 2 (F): what it does for this class. The soul catcher has no companions
                // to send (Simon 27.10.: "F does nothing for him"), so he gets no F field.
                String sub = skillTree.subclassOf(own);
                if (own.clazz() == com.mki.nightsiege.core.SkillClass.ENGINEER) {
                    state.hasAb2 = true;
                    state.iconAb2 = "nightsiege:remote_control";
                    state.nameAb2 = "nightsiege.abilitybar.f_remote";
                } else if (own.clazz() == com.mki.nightsiege.core.SkillClass.SUMMONER
                        && !SkillTree.SUB_SOULCATCHER.equals(sub)) {
                    state.hasAb2 = true;
                    state.iconAb2 = "minecraft:lead";
                    state.nameAb2 = "nightsiege.abilitybar.f_staff";
                } else if (combat.guardian().healWheel(player) != null) {
                    state.hasAb2 = true;
                    state.iconAb2 = "minecraft:glistering_melon_slice";
                    state.nameAb2 = "nightsiege.abilitybar.f_heal";
                }
            }
            // By day no abilities on screen (Simon 28.10.): only in a fight (night, test, camp, dungeon),
            // with a kit on, or in creative for trying things out.
            boolean fighting = liveSkillsFor(player) != null || kits.hasStash(player) || player.getAbilities().instabuild
                    || pendingTest != null && participants.contains(player.getUUID());
            if (!fighting) {
                state = new com.mki.nightsiege.core.UltState();
                state.kitsOn = config.nightKits;
            }
            final PlayerSkills hudOwn = own;
            final com.mki.nightsiege.core.UltState hudState = state;
            safe("Kit-Leiste", () -> kitHudValues(player, hudOwn, hudState));
            ServerPlayNetworking.send(player, new CoreNetworking.UltHudPayload(Json.toJson(state)));
        }
    }

    /** Kit HUD v2 (29 October 2026): the class's gauge and the left wing (companions, towers). */
    private void kitHudValues(ServerPlayer player, PlayerSkills own, com.mki.nightsiege.core.UltState state) {
        if (!isRegistered(player) || !own.hasClass()) {
            return;
        }
        switch (own.clazz()) {
            case SUMMONER -> {
                if (SkillTree.WAY_DEAD.equals(own.pathTaken(skillTree))) {
                    state.gauge = true;
                    state.gaugeValue = companions.soulsOf(player.getUUID());
                    state.gaugeMax = Companions.soulCapOf(own);
                    state.gaugeName = "nightsiege.kithud.souls";
                    state.gaugeColor = 0xFF8E5BD6;
                }
                if (player.level() instanceof ServerLevel level) {
                    state.units = Companions.unitsOf(level, player.getUUID());
                }
            }
            case ENGINEER -> {
                state.gauge = true;
                state.gaugeValue = (float) traps.energyOf(player);
                state.gaugeMax = (float) traps.maxEnergyOf(player);
                state.gaugeName = "nightsiege.kithud.energy";
                state.gaugeColor = 0xFF3FA0E0;
                state.units = traps.unitsOf(player);
            }
            case GUARDIAN -> {
                double share = combat.guardian().shieldShare(player, own);
                if (share >= 0.0) {
                    state.gauge = true;
                    state.gaugeValue = (float) (share * 100.0);
                    state.gaugeMax = 100f;
                    state.gaugeName = "nightsiege.kithud.shield";
                    state.gaugeColor = 0xFFC8B070;
                }
            }
            default -> {
            }
        }
    }

    /** Called by {@link TrapBlock} once a trap is set down. */
    /** The traps and towers (for the class simulation). */
    public TrapService trapService() {
        return traps;
    }

    public void onTrapPlaced(ServerLevel level, BlockPos pos, LivingEntity placer, TrapKind kind) {
        traps.onPlaced(level, pos, placer, kind);
        persist();
    }

    public void onCorePlaced(ServerLevel level, BlockPos pos, LivingEntity placer) {
        String dimension = dimensionName(level);
        if (save.findCore(dimension, pos.getX(), pos.getY(), pos.getZ()) != null) {
            return;
        }
        CoreRecord existing = mainCore();
        if (existing != null) {
            // There is only one core: a second one goes back into the hand.
            level.removeBlock(pos, false);
            if (placer instanceof ServerPlayer player) {
                if (!player.getAbilities().instabuild) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(ModContent.SIEGE_CORE_ITEM));
                }
                player.sendSystemMessage(Component.translatable("nightsiege.msg.one_core",
                        existing.x, existing.y, existing.z).withStyle(ChatFormatting.RED));
            }
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

    /** Only admins may take the core away, and never while a night is announced or running. */
    private boolean allowBreak(Object playerObject, Object stateObject) {
        if (!(stateObject instanceof net.minecraft.world.level.block.state.BlockState state) || !ModContent.isCore(state)
                || !(playerObject instanceof ServerPlayer player)) {
            return true;
        }
        if (!net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.core_admin_only").withStyle(ChatFormatting.RED));
            return false;
        }
        MinecraftServer server = player.level().getServer();
        boolean night = active || pendingTest != null
                || (server != null && warnedDay == clockDay(server) && startedDay != clockDay(server));
        if (night) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.core_not_now").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
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
        // The dungeon's seal stone (content update): the ready round.
        if (handObject == InteractionHand.MAIN_HAND && expeditions.onSeal(player, pos)) {
            return InteractionResult.SUCCESS;
        }
        // Manned towers (Simon, 26 October 2026): a right click on an arrow turret or tesla coil sits down in it.
        if (handObject == InteractionHand.MAIN_HAND && traps.tryMount(serverLevel, pos, player, held)) {
            return InteractionResult.SUCCESS;
        }
        if (traps.onUse(serverLevel, pos, player, held)) {
            trapsDirty = true;
            return InteractionResult.SUCCESS;
        }
        // The war drum before the war camp (content update): the ready round.
        if (handObject == InteractionHand.MAIN_HAND && camp.onDrum(player, pos)) {
            return InteractionResult.SUCCESS;
        }
        TrapKind placing = ModContent.trapOf(held);
        Component blocked = placing == null ? null : traps.placeBlocked(player, placing);
        if (blocked != null) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(blocked));
            return InteractionResult.FAIL;
        }
        if (!ModContent.isCore(serverLevel.getBlockState(pos))) {
            return InteractionResult.PASS;
        }
        if (!isRegistered(player)) {
            // A right click on the core signs the player up and opens the menu at once
            // (the chat button alone was easy to miss, and then the core seemed dead).
            if (handObject == InteractionHand.MAIN_HAND) {
                signUp(player);
                openCoreWindow(player);
            }
            return InteractionResult.SUCCESS;
        }
        if (insertNightFragment(player, held, pos)) {
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
            if (player == null || SimBots.isBot(player)) {
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
            ClassTags.update(here.getServer(), player, own, skillTree.subclassOfProfile(own, own.chosenClass));
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
            if (!isRegistered(player)) {
                ClassTags.update(server, player, null, null);
                continue;
            }
            PlayerSkills own = save.skillsOf(player.getUUID().toString());
            ClassTags.update(server, player, own, skillTree.subclassOfProfile(own, own.chosenClass));
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
        if (entity instanceof LivingEntity living) {
            safe("Statistik", () -> stats.damage(living, source, taken, entity.entityTags().contains(MobFactory.TAG),
                    entity instanceof ServerPlayer p && participants.contains(p.getUUID())));
        }
        boolean companion = Companions.isCompanion(source.getEntity());
        Entity hitter = companion ? Companions.ownerPlayer(source.getEntity()) : source.getEntity();
        if (hitter instanceof ServerPlayer attacker && entity.entityTags().contains(MobFactory.TAG)) {
            NightLog.Line line = nightLog.of(attacker.getUUID(), attacker.getScoreboardName());
            line.dealt += taken;
            if (companion || Companions.spellHit()) {
                line.dealtCompanions += taken;   // the summoner's companions and spells (Simon 26.10.: the 50/50 split)
            }
        }
        if (entity instanceof ServerPlayer victim && participants.contains(victim.getUUID())) {
            nightLog.of(victim.getUUID(), victim.getScoreboardName()).taken += taken;
        }
    }

    private void creditTank(ServerPlayer player, double amount, Entity from) {
        if (active && from == null && amount > 0) {
            nightLog.of(player.getUUID(), player.getScoreboardName()).healed += amount;
            stats.healGiven(player, amount);
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
            save.skillsOf(player.getUUID().toString()).award(nightPoints(points * config.skillPointsPerKill));
        }
    }

    /** Counts a kill and turns it into skill experience. */
    private void onKill(Object killerObject, Object killedObject) {
        if (killerObject instanceof Entity killerEntity && Companions.isCompanion(killerEntity)
                && killedObject instanceof Entity victim) {
            // A companion's kill is its summoner's.
            safe("Begleiter-Kill", () -> companions.onKill(killerEntity, victim));
            killerObject = Companions.ownerPlayer(killerEntity);
        }
        if (!(killerObject instanceof ServerPlayer player) || !(killedObject instanceof Entity killed)) {
            return;
        }
        combat.onKill(player, killed);
        MeleeSim.onKill(player);
        if (killed instanceof LivingEntity livingKilled) {
            CrateEffects.onKill(player, livingKilled);
        }
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
        if (killed.entityTags().contains(BossService.TAG_PART)) {
            return;   // a soul crystal or an egg sac is no monster
        }
        // The killing blow on a boss counts like the champion's did (the warlord's beast does not).
        boolean champion = BossService.isBoss(killed) && !(killed instanceof net.minecraft.world.entity.monster.Ravager);
        PlayerStats stats = save.statsOf(player.getUUID().toString(), player.getScoreboardName());
        stats.kills++;
        save.totalKills++;
        if (champion) {
            stats.championKills++;
            save.totalChampionKills++;
        }
        if (config.skillsEnabled && config.skillPointsPerKill > 0) {
            save.skillsOf(player.getUUID().toString()).award(nightPoints(champion
                    ? config.skillPointsPerKill * config.championPointMultiplier
                    : config.skillPointsPerKill));
        }
    }

    /** Routes one button press from the window. */
    /** The name of an ability key or menu for the statistics, or null for anything else. */
    private String abilityName(ServerPlayer player, String verb, String argument) {
        return switch (verb) {
            case "ult" -> "ult";
            case "ult2" -> "faehigkeit_f";
            case "ult2x" -> "zweit_ult";
            case "quiver" -> "koecher";
            case "shield" -> "schild";
            case "staffhit" -> "stab";
            case "spot" -> "vogelperspektive";
            case "tower" -> "turm_menue";
            case "jump" -> "doppelsprung";
            case "slot" -> {
                int index = parseAmount(argument);
                List<com.mki.nightsiege.core.UltState.Slot> slots = abilitySlots(player);
                yield index >= 0 && index < slots.size() ? "wahl:" + slots.get(index).id : "wahl";
            }
            case "gain" -> {
                int index = parseAmount(argument);
                PlayerSkills own = save.skillsOf(player.getUUID().toString());
                List<String> keys = own.hasClass() ? GainKeys.assigned(this, own, own.clazz()) : List.of();
                yield index >= 0 && index < keys.size() ? "gewinn:" + keys.get(index) : "gewinn";
            }
            default -> null;
        };
    }

    public void onCoreAction(ServerPlayer player, String action) {
        if (player == null || action == null || action.isBlank()) {
            return;
        }
        String[] parts = action.split(":", 2);
        String verb = parts[0];
        String argument = parts.length > 1 ? parts[1] : "";
        if (!isRegistered(player) && !verb.equals("dungeon")) {
            // Not signed up: the mod stays out of the way (keys do nothing, the core only offers the sign-up).
            if (!verb.equals("ult") && !verb.equals("shield") && !verb.equals("ult2") && !verb.equals("quiver")
                    && !verb.equals("jump") && !verb.equals("man") && !verb.equals("drone")
                    && !verb.equals("ult2x") && !verb.equals("staffhit") && !verb.equals("wheel")
                    && !verb.equals("slot") && !verb.equals("gain") && !verb.equals("cycle")) {
                offerRegistration(player);
            }
            return;
        }
        if (active) {
            String ability = abilityName(player, verb, argument);
            if (ability != null) {
                safe("Statistik", () -> stats.ability(player, ability));
            }
        }
        switch (verb) {
            case "roster" -> unregister(player);
            case "extrasiege" -> onCallExtraSiege(player);
            case "vote" -> {
                vote(player, argument.equals("yes"));
                openCoreWindow(player);
            }
            case "buy" -> onBuySkill(player, argument);
            case "class" -> onChooseClass(player, argument);
            case "sub" -> onFixSubclass(player, argument);
            case "play" -> onPlayClass(player, argument);
            case "classreset" -> onClassReset(player);
            case "donate" -> onDonate(player, parseAmount(argument));
            case "dev" -> onDevAction(player, argument);
            case "wipegains" -> wipeAllGains(player);
            case "ult" -> onUltimateKey(player);
            case "ult2" -> onSecondKey(player);
            case "ult2x" -> SecondUlts.fire(player);   // X: the second ultimate alone (27 October 2026)
            case "staffhit" -> companions.staffAttack(player);   // left click with the staff (27 October 2026)
            case "quiver" -> abilityThreeTap(player);
            case "slot" -> onSlotKey(player, parseAmount(argument));   // the ability bar (27 October 2026)
            case "cycle" -> onCycle(player, argument);   // arrows and potions: the key held, the wheel turned (30.10.)
            case "gain" -> {
                GainKeys.onKey(this, player, parseAmount(argument));   // the gain keys N, M, J (balance update 2)
                sendUltHud(player);
            }
            case "gainkey" -> {
                // The collection: a gain ability onto the first gain key ("gainkey:CLASS:id").
                String[] bits = argument.split(":", 2);
                if (bits.length == 2 && GainKeys.choose(this, player, bits[0], bits[1])) {
                    persist();
                    sendUltHud(player);
                    StatsService.sendTo(player, this);
                }
            }
            case "wheel" -> onWheel(player, argument);
            case "jump" -> GainGear.onDoubleJump(player);
            // A manned tower: the attack button pressed (1) or let go (0).
            case "man" -> traps.onManAction(player, "1".equals(argument));
            case "tower" -> traps.onTowerAction(player, argument);   // the tower menu (27 October 2026)
            // A drone's crossbow: the attack button pressed (1) or let go (0).
            case "drone" -> Drones.onFireButton(player, "1".equals(argument));
            // The alchemist's potion menu: the potion picked.
            case "brew" -> traps.onBrewChoice(player, argument);
            // The demolition expert's bird's-eye view: a fire called on a spot.
            case "spot" -> traps.onSpotAction(player, argument);
            case "shield" -> combat.guardian().onShieldAction(player);
            case "scout" -> expeditions.giveMap(player);   // the core window's "Karte mitnehmen"
            case "round_force" -> {   // a ready round (war drum or seal stone): go without the others
                camp.forceRound(player);
                expeditions.forceRound(player);
            }
            case "playtest" -> playtest.onAction(player, argument);   // the playtest window (28 October 2026)
            case "feedback" -> safe("Bewertung", () -> stats.feedback(player, argument));   // 1-click feedback (29 October 2026)
            case "curse" -> {   // the curse altar (update 2): a stone laid on or taken off
                Curses.toggle(this, player, argument);
                openCoreWindow(player);
            }
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
        if (!player.getAbilities().instabuild && !(config.testToolsForEveryone && isAdmin(player))) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.creative_only")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (argument.startsWith("reset")) {
            devResetClass(player);
        } else if (argument.startsWith("gains")) {
            devResetGains(player, argument.length() > 6 ? argument.substring(6) : "");
        } else if (argument.startsWith("crate")) {
            devCrate(player, argument.length() > 6 ? argument.substring(6) : "");
        } else {
            devGrant(player, Math.max(1, parseAmount(argument)));
        }
    }

    /** Test tool: a reward crate of the shown class (or the own one), as after a won night. */
    private void devCrate(ServerPlayer player, String className) {
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        com.mki.nightsiege.core.SkillClass clazz = com.mki.nightsiege.core.SkillClass.byName(className);
        if (clazz == null || clazz.isShared()) {
            clazz = own.clazz();
        }
        if (clazz == null) {
            return;
        }
        ItemStack crate = CrateService.crate(clazz, com.mki.nightsiege.core.CrateKind.NORMAL, levelForKit());
        if (!player.getInventory().add(crate)) {
            player.drop(crate, false);
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Test-Kiste für {}: {}", player.getScoreboardName(), clazz.name());
    }

    /** Operator button in the collection tab: every player's crate gains start over (Simon, 26 October 2026). */
    private void wipeAllGains(ServerPlayer player) {
        if (!isAdmin(player)) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.admin_only").withStyle(ChatFormatting.RED));
            return;
        }
        int players = 0;
        int removed = 0;
        if (save.skills != null) {
            for (PlayerSkills own : save.skills.values()) {
                if (own == null || own.gains == null || own.gains.isEmpty()) {
                    continue;
                }
                for (java.util.Map<String, Integer> byClass : own.gains.values()) {
                    removed += byClass == null ? 0 : byClass.size();
                }
                own.gains.clear();
                players++;
            }
        }
        persist();
        NightSiegeMod.LOGGER.info("[Night Siege] {} setzt alle Kisten-Gewinne zurueck ({} Spieler, {} Gewinne)",
                player.getScoreboardName(), players, removed);
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.translatable("nightsiege.msg.gains_wiped_all", player.getDisplayName())
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        openCoreWindow(player);
    }

    /** Creative: the crate gains of one class (or of every class) start over, for testing the crates. */
    private void devResetGains(ServerPlayer player, String className) {
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        com.mki.nightsiege.core.SkillClass clazz = com.mki.nightsiege.core.SkillClass.byName(className);
        int removed = 0;
        if (own.gains != null) {
            if (clazz == null) {
                for (java.util.Map<String, Integer> byClass : own.gains.values()) {
                    removed += byClass == null ? 0 : byClass.size();
                }
                own.gains.clear();
            } else {
                java.util.Map<String, Integer> byClass = own.gains.remove(clazz.name());
                removed = byClass == null ? 0 : byClass.size();
            }
        }
        persist();
        player.sendSystemMessage(Component.translatable("nightsiege.msg.dev_gains_reset", removed,
                clazz == null ? Component.translatable("nightsiege.ui.all_classes") : Component.translatable(clazz.translationKey()))
                .withStyle(ChatFormatting.YELLOW));
        openCoreWindow(player);
    }

    /** A player clicked a node in the window ("node" or "node@PROFILE"). */
    public void onBuySkill(ServerPlayer player, String nodeId) {
        if (nodeId != null && nodeId.contains("@")) {
            int at = nodeId.indexOf('@');
            onBuySkill(player, nodeId.substring(0, at), nodeId.substring(at + 1));
        } else {
            onBuySkill(player, nodeId, null);
        }
    }

    /**
     * Buys a node in one of the player's profiles. Points are one account
     * (Simon, 26 October 2026): they can be spent in every own profile. Without a
     * profile key the played profile (or the oldest one of the node's class) is meant.
     */
    public void onBuySkill(ServerPlayer player, String nodeId, String profileKey) {
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
            openCoreWindow(player);
            return;
        }
        String key = profileKey != null && !profileKey.isBlank() ? profileKey : own.profileFor(node.clazz());
        if (key == null || !own.hasProfile(key) || PlayerSkills.classOfKey(key) != node.clazz()) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.class_not_leveling")
                    .withStyle(ChatFormatting.RED));
            openCoreWindow(player);
            return;
        }
        boolean bought = own.asProfile(key, () -> skillTree.unlock(nodeId, own));
        if (bought) {
            rewardMaxedUltimate(player, own, node, key);
            persist();
            // Skills only hold during a siege, so apply at once only if one runs.
            if (key.equals(own.chosenClass) && active && participants.contains(player.getUUID())
                    && !fallen.contains(player.getUUID())) {
                SkillService.apply(player, own, skillTree);
            }
        }
        openCoreWindow(player);
    }

    /** The new classes this player could open with an unlock. */
    public List<SkillClass> unlockClasses(ServerPlayer player, PlayerSkills own) {
        List<SkillClass> out = new ArrayList<>();
        for (SkillClass c : SkillClass.values()) {
            if (!c.isShared() && !own.hasUnlocked(c) && mayChoose(player, c)) {
                out.add(c);
            }
        }
        return out;
    }

    /** The further subclasses of own classes this player could open with an unlock ("CLASS:subclass"). */
    public List<String> unlockSubclasses(ServerPlayer player, PlayerSkills own) {
        List<String> out = new ArrayList<>();
        for (SkillClass c : SkillClass.values()) {
            if (c.isShared() || !own.hasUnlocked(c) || !mayChoose(player, c) || own.nextProfileKey(c) == null) {
                continue;
            }
            for (String sub : skillTree.freeSubclasses(own, c)) {
                out.add(c.name() + ":" + sub);
            }
        }
        return out;
    }

    /** True when the player holds an unlock and there is something left to open with it. */
    public boolean mayUnlock(ServerPlayer player, PlayerSkills own) {
        return own.hasClass() && own.unlocksFree > 0
                && (!unlockClasses(player, own).isEmpty() || !unlockSubclasses(player, own).isEmpty());
    }

    /** Old name, kept for callers: an unlock may be used now. */
    public boolean mayOpenNextClass(ServerPlayer player, PlayerSkills own) {
        return mayUnlock(player, own);
    }

    /** The summoner is for admins only until the config opens it for everyone (Simon, 25 October 2026). */
    public boolean maySummon(ServerPlayer player) {
        return config.summonerForEveryone || (player != null
                && net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions()));
    }

    /** Whether this player may see and choose a class. */
    public boolean mayChoose(ServerPlayer player, SkillClass c) {
        return c != null && !c.isShared() && (!c.adminOnly() || maySummon(player));
    }

    /**
     * The reset at the core is there until the first unlock is earned (an ultimate at
     * its top level, Simon 26 October 2026). In a playtest
     * (testToolsForEveryone) every player may change the class as often as they like.
     */
    public boolean classResetAvailable(PlayerSkills own) {
        if (config.testToolsForEveryone || playtest.plansWith(own)) {
            return own.hasClass();   // the playtest's planning: change the class as often as one likes
        }
        return own.hasClass() && !own.classResetUsed && own.profiles().size() <= 1 && !own.earnedUnlock();
    }

    /** Server operators (permission level 2). */
    public boolean isAdmin(ServerPlayer player) {
        return player != null && net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    /**
     * Everyone back to nothing: points, levels, classes, crate gains and crates still waiting
     * are gone, the class change is free again, and the shared core tree starts empty too.
     * The night statistics and the crates in inventories stay. Not during a night.
     */
    public int wipePlayers(MinecraftServer server) {
        if (active || save.skills == null) {
            return -1;
        }
        int wiped = save.skills.size();
        save.skills.clear();
        if (save.pendingCrates != null) {
            save.pendingCrates.clear();
        }
        save.coreSkills = new PlayerSkills();
        persist();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SkillService.clear(player);
            player.sendSystemMessage(Component.translatable("nightsiege.msg.progress_wiped").withStyle(ChatFormatting.GOLD));
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Fortschritt aller Spieler zurueckgesetzt ({} Eintraege)", wiped);
        return wiped;
    }

    /**
     * Maxing an ultimate: every profile's first time gives one unlock (a new class or a
     * further subclass).
     */
    private void rewardMaxedUltimate(ServerPlayer player, PlayerSkills own, com.mki.nightsiege.core.SkillNode node,
                                     String key) {
        if (node.effect().target() != SkillEffect.Target.ULTIMATE
                || own.levelInProfile(key, node.id()) < node.maxLevel()) {
            return;
        }
        if (playtest != null && playtest.takesPart(player.getUUID())) {
            return;   // the playtest's skills are thrown away afterwards: no unlocks, no template (Simon 28.10.)
        }
        if (skillTree.grantUltUnlocks(own) > 0) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.unlock_earned",
                    profileName(own, key)).withStyle(ChatFormatting.GOLD));
            Fx.pointsEarned(player.level().getServer(), config);
        }
        // No infernite template any more (content update, 29 October 2026).
    }

    /** "Krieger – Berserker" (or just "Krieger" while the profile has no subclass), translated on the client. */
    private Component profileName(PlayerSkills own, String key) {
        SkillClass c = PlayerSkills.classOfKey(key);
        if (c == null) {
            return Component.literal("?");
        }
        String sub = skillTree.subclassOfProfile(own, key);
        MutableComponent name = Component.translatable(c.translationKey());
        return sub.isEmpty() ? name : name.append(" – ").append(Component.translatable("nightsiege.subclass." + sub));
    }

    /**
     * Commits a player to a class ("CLASS"), or uses an unlock: a new class ("CLASS") or
     * a further subclass of an own class ("CLASS:subclass"), which opens a new profile.
     */
    public void onChooseClass(ServerPlayer player, String argument) {
        String className = argument == null ? "" : argument;
        String subclass = "";
        int colon = className.indexOf(':');
        if (colon >= 0) {
            subclass = className.substring(colon + 1).trim().toLowerCase(java.util.Locale.ROOT);
            className = className.substring(0, colon);
        }
        SkillClass chosen = SkillClass.byName(className);
        if (player == null || chosen == null || chosen.isShared()) {
            return;
        }
        // The armour stand window sends the subclass node ("CLASS:w_combo"), the unlock dialog its key.
        SkillTree.SubclassInfo byNode = SkillTree.subclassByNode(subclass);
        if (byNode != null) {
            subclass = byNode.clazz() == chosen ? byNode.key() : "";
        }
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        if (!mayChoose(player, chosen)) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.class_admin_only")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!own.hasClass()) {
            // The first class: its profile is opened for the subclass picked at the armour stands.
            // The profile first, then the class (Simon, 27 October 2026): with the class set first
            // the list of profiles already held it, the pick opened a second profile "CLASS#2" for
            // the subclass and the played one had no subclass, so both ways stood open.
            // The profile holds level 1 of its subclass node from the start (29 October 2026): played, it stays.
            String first = own.addProfile(chosen, skillTree.subclassesOf(chosen).contains(subclass) ? subclass : "");
            own.playOpened(first != null ? first : chosen.name());
            persist();
            ClassTags.update(player.level().getServer(), player, own, skillTree.chosenSubclass(own, own.clazz()));
            traps.giveRemote(player, own);
            player.sendSystemMessage(Component.translatable("nightsiege.msg.class_chosen",
                    Component.translatable(chosen.translationKey())).withStyle(ChatFormatting.GREEN));
            openCoreWindow(player);
            return;
        }
        if (own.unlocksFree <= 0) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.unlock_none")
                    .withStyle(ChatFormatting.RED));
            openCoreWindow(player);
            return;
        }
        String key = null;
        if (!own.hasUnlocked(chosen)) {
            // A new class: its first profile, the subclass is chosen in the tree as usual.
            if (!subclass.isEmpty() && !skillTree.subclassesOf(chosen).contains(subclass)) {
                subclass = "";
            }
            key = own.addProfile(chosen, subclass);
        } else if (!subclass.isEmpty() && skillTree.freeSubclasses(own, chosen).contains(subclass)) {
            // A further subclass of an own class: a new profile that starts at 0.
            key = own.addProfile(chosen, subclass);
        }
        if (key == null) {
            player.sendSystemMessage(Component.translatable(own.hasUnlocked(chosen) ? "nightsiege.msg.subclass_taken"
                    : "nightsiege.msg.class_locked").withStyle(ChatFormatting.RED));
            openCoreWindow(player);
            return;
        }
        own.unlocksFree--;
        persist();
        player.sendSystemMessage(Component.translatable("nightsiege.msg.profile_new",
                profileName(own, key)).withStyle(ChatFormatting.GREEN));
        if (!(active && participants.contains(player.getUUID()))) {
            onPlayClass(player, key);   // the new profile is played from now on (Simon, 27 October 2026)
            return;
        }
        openCoreWindow(player);
    }

    /**
     * Fixes the subclass of an unlocked class that has none yet (classes chosen before the
     * armour stand window): only while nothing of the other way or subclass is learned.
     */
    public void onFixSubclass(ServerPlayer player, String argument) {
        String[] parts = argument == null ? new String[0] : argument.split(":", 2);
        SkillClass clazz = parts.length == 0 ? null : SkillClass.byName(parts[0]);
        SkillTree.SubclassInfo sub = parts.length > 1 ? SkillTree.subclassByNode(parts[1]) : null;
        if (player == null || clazz == null || sub == null || sub.clazz() != clazz) {
            return;
        }
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        if (!own.hasUnlocked(clazz) || !own.fixedSubOf(clazz).isEmpty()) {
            openCoreWindow(player);
            return;
        }
        String learned = skillTree.chosenSubclass(own, clazz);
        String way = own.as(clazz, () -> own.pathTaken(skillTree));
        if ((!learned.isEmpty() && !learned.equals(sub.key())) || (!way.isEmpty() && !way.equals(sub.way()))) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.sub_conflict")
                    .withStyle(ChatFormatting.RED));
            openCoreWindow(player);
            return;
        }
        own.fixSub(clazz, sub.node());
        persist();
        if (clazz == own.clazz()) {
            ClassTags.update(player.level().getServer(), player, own, skillTree.chosenSubclass(own, own.clazz()));
        }
        player.sendSystemMessage(Component.translatable("nightsiege.msg.sub_fixed",
                Component.translatable("nightsiege.subclass." + sub.key())).withStyle(ChatFormatting.GREEN));
        openCoreWindow(player);
    }

    /** Picks which profile the player plays (the next night, or from now on): a profile key or a class name. */
    public void onPlayClass(ServerPlayer player, String profileKey) {
        SkillClass chosen = PlayerSkills.classOfKey(profileKey);
        if (player == null || chosen == null || chosen.isShared()) {
            return;
        }
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        String key = own.hasProfile(profileKey) ? profileKey : own.profileFor(chosen);
        if (active && participants.contains(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.play_not_now").withStyle(ChatFormatting.RED));
            return;
        }
        if (key == null || !own.hasProfile(key)) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.class_locked").withStyle(ChatFormatting.RED));
            return;
        }
        if (!key.equals(own.chosenClass)) {
            own.switchToProfile(key);
            persist();
            SkillService.clear(player);
            if (kits.hasStash(player)) {
                kits.refit(player, levelForKit());   // the kit of the new class for tonight
            } else {
                traps.giveRemote(player, own);
            }
        }
        player.sendSystemMessage(Component.translatable("nightsiege.msg.play_class",
                profileName(own, key)).withStyle(ChatFormatting.GREEN));
        camp.onRoundReady(player);   // the war drum's ready round (content update)
        expeditions.onRoundReady(player);   // the dungeon's ready round (content update)
        if (classPicking.remove(player.getUUID())) {
            // Chosen in the window before the night: no core window on top, just the new name tag.
            ClassTags.update(player.level().getServer(), player, own, skillTree.subclassOfProfile(own, own.chosenClass));

            refreshClassPicks(player.level().getServer());
            return;
        }
        openCoreWindow(player);
    }

    /** Holes the sapper creepers blew tonight (for the test). */
    public int besiegerBreaches() {
        return besiegers.breaches();
    }

    /** Defenders who still have the class window of tonight open. */
    private final Set<UUID> classPicking = new HashSet<>();

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
        if (!classResetAvailable(own)) {
            player.sendSystemMessage(Component.translatable(own.classResetUsed ? "nightsiege.msg.class_reset_used"
                    : "nightsiege.msg.class_reset_closed").withStyle(ChatFormatting.RED));
            openCoreWindow(player);
            return;
        }
        boolean wasUsed = own.classResetUsed;
        own.changeClass();
        if (config.testToolsForEveryone) {
            own.classResetUsed = wasUsed;   // the playtest change does not use up the real one
        }
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

    // ------------------------------------------------------------------
    // roster: who signed up at the core
    // ------------------------------------------------------------------

    /** True for players who signed up at the core. The mod leaves everyone else alone. */
    public boolean isRegistered(net.minecraft.world.entity.player.Player player) {
        return player != null && save.isRegistered(player.getUUID().toString());
    }

    /**
     * The mod's monsters and players who are not part of it leave each other
     * alone: siege monsters only fight the night's defenders, dungeon monsters
     * only signed-up players.
     */
    public boolean allowModDamage(Entity victim, Entity attacker) {
        if (victim instanceof net.minecraft.world.entity.player.Player player && attacker != null) {
            return mayFight(attacker, player);
        }
        if (attacker instanceof net.minecraft.world.entity.player.Player player && victim != null) {
            return mayFight(victim, player);
        }
        return true;
    }

    /** Whether this monster of the mod and this player may fight; any other monster always may. */
    public boolean mayFight(Entity mob, net.minecraft.world.entity.player.Player player) {
        if (SiegeCamp.isCampMob(mob)) {
            return camp.isFighter(player) || player.getAbilities().instabuild;   // the camp ignores everyone else
        }
        if (mob.entityTags().contains(SiegeMachines.TAG_MACHINE) && player.getAbilities().instabuild) {
            return true;   // a try-out machine can be struck down in creative
        }
        if (Expeditions.isExpeditionMob(mob)) {
            return expeditions.isFighter(player) || player.getAbilities().instabuild;   // only who fights in the dungeon
        }
        if (mob.entityTags().contains(MobFactory.TAG)) {
            return isDefending(player) || Drones.isDrone(player);   // a drone's bolts count too
        }
        return true;
    }

    /** Called before a monster picks a target: the mod's monsters never pick players outside it. */
    public static boolean mayTarget(Mob mob, LivingEntity target) {
        if (target instanceof net.minecraft.world.entity.player.Player p && Drones.isDrone(p)) {
            return false;   // the drones of the fallen are left alone
        }
        if (BossService.isBoss(mob) && !(target instanceof net.minecraft.world.entity.player.Player)) {
            return false;   // a boss fights the defenders only (the frost jarl is an iron golem at heart)
        }
        if (BossService.isMount(target) || (BossService.isBossOrPart(target) && !Companions.isCompanion(mob))) {
            return false;   // and no monster turns on a boss (zombies hate iron golems); the witch's phantom cannot be hurt
        }
        if (!Companions.mayTarget(mob, target)) {
            return false;   // companions pick no players and no other companions
        }
        if (!GainGear.mayTarget(mob, target)) {
            return false;
        }
        if (Besiegers.busy(mob)) {
            return false;   // a ladder carrier or sapper at its job picks no fights
        }
        if (SiegeCamp.isCampMob(mob)) {
            return INSTANCE.camp.mayTarget(mob, target);   // the camp's guards only fight who attacks it
        }
        if (Expeditions.isExpeditionMob(mob)) {
            return INSTANCE.expeditions.mayTarget(mob, target);   // a dungeon's monsters only fight its fighters
        }
        if (mob.entityTags().contains(SiegeMachines.TAG_CREW) && !INSTANCE.machines.mayTarget(mob, target)) {
            return false;   // the machines' pillagers stay by their machine
        }
        if (!Foes.mayTarget(mob, target)) {
            return false;   // the treasure raider flees, a tunneler is busy digging
        }
        if (infighting(mob, target)) {
            return false;   // monsters do not turn on each other (Simon, 25 October 2026)
        }
        if (!RaidPlan.mayTarget(mob, target)) {
            return false;   // outside the walls only who comes out (the raid's plan)
        }
        return !(target instanceof net.minecraft.world.entity.player.Player player) || INSTANCE.mayFight(mob, player);
    }

    /**
     * True for a monster against another monster, the summoner's companions aside: a zombie
     * hit by a skeleton's arrow does not go for the skeleton, and the arrow does it no harm.
     */
    public static boolean infighting(Entity attacker, Entity victim) {
        return attacker instanceof net.minecraft.world.entity.monster.Enemy && victim instanceof net.minecraft.world.entity.monster.Enemy
                && attacker != victim && !Companions.isCompanion(attacker) && !Companions.isCompanion(victim);
    }

    /**
     * The X key: with the remote in hand its second order, with the summoner's staff its
     * order (attack the monster or hold the block looked at), otherwise the second ultimate.
     */
    private void onSecondKey(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        // Fähigkeit 2 (F, Simon 27 October 2026): the order of the remote or the staff in hand.
        // The second ultimate has its own key X now, so the two never get in each other's way.
        if (ModContent.REMOTE != null && held.is(ModContent.REMOTE)) {
            traps.useRemote(player, null, true);
            return;
        }
        if (held.getItem() instanceof SummonStaffItem) {
            companions.command(player);
            return;
        }
        // The healers: the ability picked on the wheel, on the teammate looked at.
        combat.guardian().castHeal(player);
    }

    /**
     * A short press of Fähigkeit 3 (Simon, 27 October 2026): the next choice without the wheel –
     * the summoner's spell (or his call on the way of the light), the alchemist's potion, the
     * healer's ability, the archer's arrow kind.
     */
    private void abilityThreeTap(ServerPlayer player) {
        if (companions.selectKey(player) || traps.nextBrew(player) || combat.guardian().nextHeal(player)) {
            return;
        }
        GainGear.cycleQuiver(player);
    }

    /**
     * The choices of the ability bar (Simon, 27 October 2026: the wheel is gone, every ability has
     * a key of its own): the summoner's spells (or his call on the way of the light), the
     * alchemist's potions, the healer's abilities, the archer's arrow kinds.
     */
    public List<com.mki.nightsiege.core.UltState.Slot> abilitySlots(ServerPlayer player) {
        List<com.mki.nightsiege.core.UltState.Slot> out = new ArrayList<>();
        PlayerSkills own = effectiveSkills(player);
        long now = player.level().getGameTime();
        boolean spells = companions.spellWheel(player) != null;
        // The engineer (28 October 2026): the escape first, the alchemist's potions with their own pauses, his acid flask.
        List<com.mki.nightsiege.core.UltState.Slot> engineer = traps.engineerSlots(player, own, now);
        com.mki.nightsiege.core.WheelData wheel = engineer != null ? null : wheelOf(player);
        if (engineer != null) {
            out.addAll(engineer);
        } else if (wheel != null && "nightsiege.wheel.quiver".equals(wheel.title)) {
            com.mki.nightsiege.core.UltState.Slot quiver = GainGear.quiverSlot(player);
            if (quiver != null) {
                out.add(quiver);   // one key for the arrows (Simon 30.10.)
            }
        } else if (wheel != null) {
            for (com.mki.nightsiege.core.WheelData.Option o : wheel.options) {
                if (!o.open) {
                    continue;   // not learned yet: no box and no key (Simon, 28 October 2026)
                }
                com.mki.nightsiege.core.UltState.Slot slot = new com.mki.nightsiege.core.UltState.Slot();
                slot.id = o.id;
                slot.icon = o.icon;
                slot.invSlot = o.slot;
                slot.color = o.color;
                slot.name = o.name;
                slot.sub = o.sub;
                slot.subArgs.addAll(o.subArgs);
                slot.current = o.current && !spells;
                if (spells) {
                    slot.cooldownTicks = companions.spellCooldownLeft(player, o.id, now);
                    slot.totalTicks = companions.spellCooldownTotal(player, o.id);
                }
                out.add(slot);
            }
        } else if (own.clazz() == com.mki.nightsiege.core.SkillClass.SUMMONER && own.hasClass()) {
            // The way of the light: the call (wolves or the golem back to you).
            com.mki.nightsiege.core.UltState.Slot slot = new com.mki.nightsiege.core.UltState.Slot();
            slot.id = "call";
            slot.icon = "minecraft:goat_horn";
            slot.name = "nightsiege.ability.call";
            out.add(slot);
        }
        if (own.hasClass() && SkillTree.SUB_HEAVY.equals(skillTree.subclassOf(own))) {
            out.add(0, EngineerEscape.slot(player, now));   // the heavy archer gets the blast jump too (Simon 29.10.)
        }
        if (own.hasClass() && own.clazz() == com.mki.nightsiege.core.SkillClass.WARRIOR) {
            out.add(0, WarriorDash.slot(player, now));   // every warrior: the dash on R (Simon 30.10.)
        }
        if (own.hasClass() && SkillTree.SUB_MEDIC.equals(skillTree.subclassOf(own))) {
            com.mki.nightsiege.core.UltState.Slot self = combat.guardian().selfHealSlot(player);
            if (self != null) {
                out.add(self);   // the medic heals himself on a key of his own (Simon 30.10.)
            }
        }
        while (out.size() > 6) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private List<com.mki.nightsiege.core.UltState.Slot> safeSlots(ServerPlayer player) {
        try {
            return abilitySlots(player);
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Fähigkeitenleiste: {}", e.toString());
            return new ArrayList<>();
        }
    }

    /** The index of a choice on the bar, or -1 (for the tests). */
    public int abilitySlotOf(ServerPlayer player, String id) {
        List<com.mki.nightsiege.core.UltState.Slot> slots = abilitySlots(player);
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** A bar key (R, G, Y, C, V, B): the ability on it, at once. */
    private void onSlotKey(ServerPlayer player, int index) {
        List<com.mki.nightsiege.core.UltState.Slot> slots = abilitySlots(player);
        if (index < 0 || index >= slots.size()) {
            return;
        }
        String id = slots.get(index).id;
        if (WarriorDash.ID.equals(id)) {
            WarriorDash.fire(player, traps);   // the warriors' dash (Simon 30.10.)
        } else if (GainGear.QUIVER_SLOT.equals(id)) {
            GainGear.normalArrows(player);   // a tap on the arrow key: the normal arrows again
        } else if (GuardianEffects.SELF_HEAL.equals(id)) {
            combat.guardian().castSelfHeal(player);
        } else if (EngineerEscape.ID.equals(id)) {
            EngineerEscape.fire(player, traps);   // the engineers' and the heavy archer's blast jump
        } else if (traps.onEngineerSlot(player, id)) {
            // the engineer's escape, the alchemist's potion or acid flask (28 October 2026)
        } else if ("call".equals(id)) {
            companions.selectKey(player);   // the way of the light: the call
        } else if (companions.spellWheel(player) != null) {
            companions.castSpell(player, id);   // the spell, on what you look at
        } else if (traps.brewWheel(player) != null) {
            traps.onBrewChoice(player, id);
            traps.throwBrew(player);   // the potion, thrown
        } else if (combat.guardian().healWheel(player) != null) {
            combat.guardian().pickHeal(player, id);
            combat.guardian().castHeal(player);   // the healing, on the teammate looked at (or you)
        } else {
            GainGear.pickQuiver(player, id);   // the arrow kind for the next shots
        }
        sendUltHud(player);
    }

    /** "cycle:index:direction": a key for a set of choices held and the mouse wheel turned (Simon 30.10.). */
    private void onCycle(ServerPlayer player, String argument) {
        String[] bits = argument.split(":", 2);
        int index = parseAmount(bits[0]);
        int direction = bits.length > 1 && bits[1].startsWith("-") ? -1 : 1;
        List<com.mki.nightsiege.core.UltState.Slot> slots = abilitySlots(player);
        if (index < 0 || index >= slots.size()) {
            return;
        }
        String id = slots.get(index).id;
        if (GainGear.QUIVER_SLOT.equals(id)) {
            GainGear.cycleQuiver(player, direction);
        } else if (TrapService.BREW_SLOT.equals(id)) {
            traps.cycleBrew(player, direction);
        }
        sendUltHud(player);
    }

    /** The wheel of Fähigkeit 3, the one that fits the class. Null when there is nothing to pick. */
    private com.mki.nightsiege.core.WheelData wheelOf(ServerPlayer player) {
        com.mki.nightsiege.core.WheelData wheel = companions.spellWheel(player);
        if (wheel == null) {
            wheel = traps.brewWheel(player);
        }
        if (wheel == null) {
            wheel = combat.guardian().healWheel(player);
        }
        if (wheel == null) {
            wheel = GainGear.quiverWheel(player);
        }
        return wheel;
    }

    /** "wheel:open" (the key held) sends the wheel; "wheel:pick:id" (let go over a choice) takes it. */
    private void onWheel(ServerPlayer player, String argument) {
        if (argument.startsWith("pick:")) {
            String id = argument.substring(5);
            if (companions.spellWheel(player) != null) {
                companions.pickSpell(player, id);
            } else if (traps.brewWheel(player) != null) {
                traps.onBrewChoice(player, id);
            } else if (combat.guardian().healWheel(player) != null) {
                combat.guardian().pickHeal(player, id);
            } else {
                GainGear.pickQuiver(player, id);
            }
            return;
        }
        com.mki.nightsiege.core.WheelData wheel = wheelOf(player);
        if (wheel == null || wheel.options.isEmpty()) {
            abilityThreeTap(player);   // nothing to pick (the way of the light calls): as a short press
            return;
        }
        ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("wheel", Json.toJson(wheel)));
    }

    /**
     * Monsters go less for bow and crossbow archers (Simon, 25 October 2026): a monster that
     * takes aim at an archer turns to another defender who is not much further away.
     */
    public static net.minecraft.world.entity.LivingEntity retarget(Mob mob, net.minecraft.world.entity.LivingEntity target) {
        SiegeManager m = INSTANCE;
        if (m == null || !m.active || !(target instanceof ServerPlayer archer) || !m.isDefending(archer)) {
            return target;
        }
        PlayerSkills skills = m.save.skillsOf(archer.getUUID().toString());
        if (skills.clazz() != com.mki.nightsiege.core.SkillClass.ARCHER || archer.level().getServer() == null) {
            return target;
        }
        double limit = Math.sqrt(mob.distanceToSqr(archer)) + 8.0;
        ServerPlayer best = null;
        double bestDistance = limit * limit;
        for (UUID id : m.participants) {
            ServerPlayer other = archer.level().getServer().getPlayerList().getPlayer(id);
            if (other == null || other == archer || other.level() != mob.level() || !m.isDefending(other)
                    || m.save.skillsOf(id.toString()).clazz() == com.mki.nightsiege.core.SkillClass.ARCHER) {
                continue;
            }
            double d = mob.distanceToSqr(other);
            if (d < bestDistance) {
                bestDistance = d;
                best = other;
            }
        }
        return best == null ? target : best;
    }

    /**
     * True while this player fights away from the base, in a dungeon or at the war camp, and
     * field towers are on (28 October 2026): his towers and traps set up there are field ones.
     */
    public boolean fieldFighter(net.minecraft.world.entity.player.Player player) {
        return player != null && config.fieldTowers && (camp.isFighter(player) || expeditions.isFighter(player));
    }

    /** True while this player defends the running night. */
    public boolean isDefending(net.minecraft.world.entity.player.Player player) {
        return player != null && (active && participants.contains(player.getUUID()) && !fallen.contains(player.getUUID())
                || camp.isFighter(player) || expeditions.isFighter(player));
    }

    /**
     * Players near a spot who may defend: in a real night those who accepted
     * today's invitation, in a test the signed-up players (or everyone near, if
     * none of them is there, so an admin can test alone).
     */
    private List<ServerPlayer> defendersNear(MinecraftServer server, ServerLevel targetWorld, BlockPos center, boolean test) {
        List<ServerPlayer> near = playersNear(server, targetWorld, center, config.participantRadius);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : near) {
            String id = player.getUUID().toString();
            boolean ok = test || openNight ? save.isRegistered(id)
                    : save.isRegistered(id) && save.accepted != null && save.accepted.contains(id);
            if (ok) {
                out.add(player);
            }
        }
        if (test && out.isEmpty()) {
            return near;
        }
        return out;
    }

    /** Right click on the core without being signed up: the offer to sign up. */
    private void offerRegistration(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("nightsiege.msg.register_offer").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.empty()
                .append(button("nightsiege.msg.register_button", "/nightsiege eintragen", ChatFormatting.GREEN))
                .append(Component.literal("  "))
                .append(Component.translatable("nightsiege.msg.register_hint").withStyle(ChatFormatting.GRAY)));
    }

    /** Signs a player up (they must stand at the core). */
    public void register(ServerPlayer player) {
        CoreRecord core = mainCore();
        if (core == null) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.no_core").withStyle(ChatFormatting.RED));
            return;
        }
        if (isRegistered(player)) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.already_registered").withStyle(ChatFormatting.GRAY));
            return;
        }
        double r = 16.0;
        if (!dimensionName(player.level()).equals(core.dimension)
                || player.distanceToSqr(core.x + 0.5, core.y + 0.5, core.z + 0.5) > r * r) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.register_at_core").withStyle(ChatFormatting.RED));
            return;
        }
        signUp(player);
    }

    /** Puts the player on the roster (no checks: the caller made sure they stand at the core). */
    private void signUp(ServerPlayer player) {
        if (isRegistered(player)) {
            return;
        }
        save.register(player.getUUID().toString());
        persist();
        player.sendSystemMessage(Component.translatable("nightsiege.msg.registered", config.daysBetweenSieges,
                config.requiredAccepts).withStyle(ChatFormatting.GREEN));
        giveGuideBook(player);
        events.forget(player.getUUID());
        PlayerSkills own = save.skillsOf(player.getUUID().toString());
        ClassTags.update(player.level().getServer(), player, own, skillTree.subclassOfProfile(own, own.chosenClass));
        MinecraftServer server = player.level().getServer();
        if (server != null && voteOpen(server)) {
            sendInvitation(player);
        }
    }

    /** Signs a player off: from now on the mod leaves them alone (their progress stays saved). */
    public void unregister(ServerPlayer player) {
        if (!isRegistered(player)) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.not_registered").withStyle(ChatFormatting.GRAY));
            return;
        }
        MinecraftServer server = player.level().getServer();
        String id = player.getUUID().toString();
        boolean tonight = server != null && warnedDay == clockDay(server) && save.accepted != null && save.accepted.contains(id);
        if (isDefending(player) || (pendingTest != null && isRegistered(player)) || tonight) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.unregister_blocked").withStyle(ChatFormatting.RED));
            return;
        }
        save.unregister(id);
        persist();
        SkillService.clear(player);
        ClassTags.update(server, player, null, null);
        events.forget(player.getUUID());
        player.sendSystemMessage(Component.translatable("nightsiege.msg.unregistered").withStyle(ChatFormatting.GRAY));
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
            if (isRegistered(player)) {
                player.sendSystemMessage(message);
            }
        }
    }
}
