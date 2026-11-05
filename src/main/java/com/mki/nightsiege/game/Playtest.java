package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.BossKind;
import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.ExpeditionKind;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.PlaytestData;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The playtest (Simon, 28 October 2026): "every player picks a class and plans the whole skill
 * tree; then four nights of rising level are played, in each one the tree is as far along the
 * planned path as the points of that night reach; before every night something happens – once a
 * war camp, three times a dungeon one is taken to; afterwards everything is reset".
 *
 * <ol>
 *   <li>{@code /nightsiege playtest <target level>}: every signed-up player online takes part. Their
 *   skills and inventory are put away; they start with no class and plenty of planning points.</li>
 *   <li>Planning: class and subclass at the armour stands, then the whole tree in the order they want
 *   to learn it (the order is the path). Everyone presses "Ready" – without it nothing starts.</li>
 *   <li>Four steps: the points of the night's level (as many as one would have by then) are spent along
 *   the path; the crates one would have by then wait behind a button; a war camp or a dungeon (players
 *   are taken there); back to the core; a test night of that level (random event, one boss night).
 *   Lost or won, it goes on.</li>
 *   <li>The end (or /nightsiege playtest stop, or a restart): skills and inventory back as before, the
 *   world's own dungeon and camp back too.</li>
 * </ol>
 *
 * <p>"Playtest 2" (Simon 04.11.): its nights scale like real nights; the three dungeons are three
 * different kinds and every night without a boss has its own event, both picked at the start (what
 * the last playtests had comes less often); with nobody of it online it pauses (the running part ends)
 * and goes on with that part once everyone online is ready again; between the fights nobody is hurt;
 * the crates are opened straight from the menu, at any time.
 */
public final class Playtest {

    public static final int NIGHTS = 4;
    /** Points for planning: enough to learn any whole tree. */
    static final int PLAN_POINTS = 9999;
    private static final int PAUSE_INTERLUDE_SECONDS = 20;
    private static final int PAUSE_NIGHT_SECONDS = 10;
    private static final int AFTER_NIGHT_SECONDS = 25;
    private static final int CAMP_MINUTES = 10;
    private static final int DUNGEON_MINUTES = 15;

    private final SiegeManager m;
    private final Random random = new Random();

    Playtest(SiegeManager manager) {
        this.m = manager;
    }

    // ------------------------------------------------------------------ queries

    private SiegeSave.PlaytestState state() {
        return m.save() == null ? null : m.save().playtest;
    }

    /** A playtest runs (from the planning to its end). */
    public boolean running() {
        SiegeSave.PlaytestState s = state();
        return s != null && !s.finished;
    }

    /** The paths are being planned. */
    public boolean planning() {
        SiegeSave.PlaytestState s = state();
        return s != null && !s.finished && "plan".equals(s.phase);
    }

    /** The players of the playtest who are online now. */
    public List<ServerPlayer> onlinePlayers(MinecraftServer server) {
        SiegeSave.PlaytestState s = state();
        return s == null || s.finished ? List.of() : online(server, s);
    }

    /**
     * Natural monsters near the players go (between the parts it is night, and they were killed by
     * zombies and creepers while waiting): not those of the night, the camp, the dungeon or a summoner.
     */
    private void clearWildMonsters(MinecraftServer server, SiegeSave.PlaytestState s) {
        for (ServerPlayer p : online(server, s)) {
            if (!(p.level() instanceof ServerLevel level)) {
                continue;
            }
            for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    p.getBoundingBox().inflate(48.0), mob -> mob instanceof net.minecraft.world.entity.monster.Enemy
                            && mob.isAlive() && wild(mob))) {
                mob.discard();
            }
        }
    }

    private static boolean wild(net.minecraft.world.entity.Mob mob) {
        java.util.Set<String> tags = mob.entityTags();
        return !tags.contains(MobFactory.TAG) && !Expeditions.isExpeditionMob(mob) && !SiegeCamp.isCampMob(mob)
                && !Companions.isCompanion(mob) && !BossService.isBossOrPart(mob) && !mob.isPersistenceRequired()
                && !mob.hasCustomName() && !mob.isPassenger();
    }

    /** How many players take part. */
    public int playerCount() {
        SiegeSave.PlaytestState s = state();
        return s == null || s.finished ? 0 : s.players.size();
    }

    public boolean takesPart(UUID id) {
        SiegeSave.PlaytestState s = state();
        return s != null && !s.finished && id != null && s.players.containsKey(id.toString());
    }

    /** True for the skills of a player who plans their path now (they may change the class freely). */
    public boolean plansWith(PlayerSkills own) {
        SiegeSave.PlaytestState s = state();
        if (own == null || s == null || s.finished || !"plan".equals(s.phase)) {
            return false;
        }
        for (String id : s.players.keySet()) {
            if (m.save().skills != null && m.save().skills.get(id) == own) {
                return true;
            }
        }
        return false;
    }

    /** The level of the playtest outside its nights (camp, dungeon, kits), or -1. */
    public int level() {
        SiegeSave.PlaytestState s = state();
        if (s == null || s.finished || s.levels.isEmpty()) {
            return -1;
        }
        return s.levels.get(Math.max(0, Math.min(s.levels.size() - 1, s.step)));
    }

    /** The four levels for a target: evenly up to it (20 → 5, 10, 15, 20). */
    public static List<Integer> levelsFor(int target) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i <= NIGHTS; i++) {
            out.add(Math.max(1, (int) Math.round(target * i / (double) NIGHTS)));
        }
        return out;
    }

    /** Crates a defender would have by a night of this level: one per night before, plus the chance of a second. */
    public static int cratesUntil(int level, SiegeConfig cfg) {
        double perNight = 1.0 + Math.max(0.0, cfg.bonusCrateChance);
        return (int) Math.round(Math.max(0, level - 1) * perNight);
    }

    // ------------------------------------------------------------------ start

    /** Starts a playtest for every signed-up player online; returns null or the translation key of why not. */
    public String start(MinecraftServer server, int target) {
        return start(server, target, null);
    }

    /**
     * As above; with {@code alone} only that player takes part (Simon, 28 October 2026: "is the
     * playtest possible alone"), the others online go on as they were.
     */
    public String start(MinecraftServer server, int target, ServerPlayer alone) {
        if (running()) {
            return "nightsiege.playtest.already";
        }
        if (m.isActive() || m.isPreparing() || m.nightComing(server)) {
            return "nightsiege.cmd.already_active";
        }
        if (m.mainCore() == null) {
            return "nightsiege.playtest.no_core";
        }
        // Restores of an earlier playtest that still wait for offline players are kept aside, never
        // overwritten by this one (Simon 31.10.: survival safety; they would keep the planning skills).
        keepWaitingRestores();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            giveBackWaiting(p);   // online again but not given back yet: before anything is taken
        }
        List<ServerPlayer> players = new ArrayList<>();
        if (alone != null) {
            if (!m.isRegistered(alone)) {
                return "nightsiege.playtest.not_registered";
            }
            players.add(alone);
        } else {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (m.isRegistered(p) && !p.isSpectator() && !p.isDeadOrDying()) {
                    players.add(p);
                }
            }
        }
        if (players.isEmpty()) {
            return "nightsiege.playtest.nobody";
        }
        for (ServerPlayer p : players) {
            if (m.kits().hasStash(p)) {
                return "nightsiege.playtest.kit_worn";
            }
            if (m.save().playtestRestores != null && m.save().playtestRestores.containsKey(p.getUUID().toString())) {
                return "nightsiege.playtest.restore_waiting";   // their real things are not back yet (dead)
            }
        }
        SiegeConfig cfg = m.config();
        SiegeSave.PlaytestState s = new SiegeSave.PlaytestState();
        s.target = target;
        s.levels = levelsFor(target);
        s.interludes = new ArrayList<>(List.of("camp", "dungeon", "dungeon", "dungeon"));
        Collections.shuffle(s.interludes, random);
        SiegeSave.PlaytestRecent recent = recent();
        if (cfg.bossesEnabled) {
            s.bossNight = 1 + random.nextInt(NIGHTS - 1);   // not the first night
            // Playtest 2 (Simon 04.11.): not a boss of the last two playtests, if there is another.
            List<String> bosses = new ArrayList<>();
            for (BossKind k : BossKind.values()) {
                if (!last(recent.bosses, 2).contains(k.id())) {
                    bosses.add(k.id());
                }
            }
            if (bosses.isEmpty()) {
                for (BossKind k : BossKind.values()) {
                    bosses.add(k.id());
                }
            }
            s.boss = bosses.get(random.nextInt(bosses.size()));
        }
        planVariety(s, cfg, recent);
        // Inventories first: whatever the save cannot hold stops the playtest before anything changed.
        for (ServerPlayer p : players) {
            SiegeSave.PlaytestPlayer pp = new SiegeSave.PlaytestPlayer();
            pp.name = p.getScoreboardName();
            HolderLookup.Provider regs = p.level().registryAccess();
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                String json = KitService.store(stack, regs);   // SNBT: the numbers stay as they are (31.10.)
                if (!stack.isEmpty() && json.isEmpty()) {
                    NightSiegeMod.LOGGER.warn("[Night Siege] Playtest: Gegenstand von {} lässt sich nicht speichern", pp.name);
                    return "nightsiege.playtest.item_unsaved";
                }
                pp.items.add(json);
            }
            pp.skills = Json.toJson(m.save().skillsOf(p.getUUID().toString()));
            s.players.put(p.getUUID().toString(), pp);
        }
        // The world's own dungeon fight and camp fight end; their records wait until the playtest is over.
        m.expeditions().endAll(server, null);
        m.camp().endAll(server);
        m.camp().openEntry(false);
        if (m.save().camp != null) {
            // A camp of the world goes (its guards could not wait unseen); the next siege may roll a new one.
            NightSiegeMod.LOGGER.info("[Night Siege] Playtest: das Heerlager der Welt wird abgebaut");
            m.camp().remove(server);
            m.save().campRolledDay = -1L;
        }
        s.realExpedition = m.save().expedition;
        s.realCamp = null;
        s.realMachinesDay = m.save().machinesDay;
        m.save().expedition = null;
        m.save().camp = null;
        m.save().machinesDay = -1L;
        m.save().playtest = s;
        m.persist();   // written before anything of the players is touched
        for (ServerPlayer p : players) {
            PlayerSkills fresh = new PlayerSkills();
            fresh.profileVersion = PlayerSkills.PROFILE_VERSION;
            fresh.freeSubclassVersion = PlayerSkills.FREE_SUBCLASS_VERSION;
            fresh.award(PLAN_POINTS);
            m.save().skills.put(p.getUUID().toString(), fresh);
            p.getInventory().clearContent();
            SkillService.clear(p);
            ClassTags.update(server, p, fresh, null);
        }
        m.persist();
        remember(recent, s);
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Playtest bis Stufe {} mit {} Spielern: Stufen {}, vorher {}, Dungeons {}, Ereignisse {}, Boss in Nacht {} ({})",
                target, players.size(), s.levels, s.interludes, s.dungeons, s.events, s.bossNight + 1, s.boss);
        for (ServerPlayer p : players) {
            p.sendSystemMessage(Component.translatable("nightsiege.playtest.started", target).withStyle(ChatFormatting.GOLD));
            p.sendSystemMessage(Component.translatable("nightsiege.playtest.plan_hint").withStyle(ChatFormatting.YELLOW));
            p.sendSystemMessage(menuLink());
        }
        for (ServerPlayer p : players) {
            send(p, true);
        }
        return null;
    }

    // ------------------------------------------------------------------ the window's buttons

    /** A button of the playtest window ("tree", "ready", "crates", "menu"). */
    public void onAction(ServerPlayer player, String action) {
        SiegeSave.PlaytestPlayer pp = player(player);
        if (pp == null) {
            player.sendSystemMessage(Component.translatable("nightsiege.playtest.not_in").withStyle(ChatFormatting.GRAY));
            return;
        }
        switch (action) {
            case "tree" -> m.openCoreWindow(player);
            case "ready" -> toggleReady(player, pp);
            case "crates" -> openCrate(player, pp);
            default -> send(player, true);
        }
    }

    private SiegeSave.PlaytestPlayer player(ServerPlayer player) {
        SiegeSave.PlaytestState s = state();
        return s == null || s.finished || player == null ? null : s.players.get(player.getUUID().toString());
    }

    private void toggleReady(ServerPlayer player, SiegeSave.PlaytestPlayer pp) {
        SiegeSave.PlaytestState s = state();
        boolean paused = s != null && "paused".equals(s.phase);
        if (!planning() && !paused) {
            send(player, true);
            return;
        }
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        if (paused) {
            // Back after the pause (Playtest 2): the path is fixed, everyone online ready goes on.
            pp.ready = !pp.ready;
            m.persist();
            MinecraftServer server = player.level().getServer();
            if (pp.ready) {
                broadcast(server, Component.translatable("nightsiege.playtest.is_ready", player.getDisplayName(), pp.path.size())
                        .withStyle(ChatFormatting.GREEN));
            }
            refreshAll(server);
            return;
        }
        if (!pp.ready && !own.hasClass()) {
            player.sendSystemMessage(Component.translatable("nightsiege.playtest.need_class").withStyle(ChatFormatting.RED));
            send(player, true);
            return;
        }
        if (!pp.ready && (own.history == null || own.history.isEmpty())) {
            // Ready with an empty path would mean four nights without a single skill (playtest 28.10.).
            player.sendSystemMessage(Component.translatable("nightsiege.playtest.need_path").withStyle(ChatFormatting.RED));
            send(player, true);
            return;
        }
        pp.ready = !pp.ready;
        m.persist();
        MinecraftServer server = player.level().getServer();
        if (pp.ready) {
            int path = own.history == null ? 0 : own.history.size();
            broadcast(server, Component.translatable("nightsiege.playtest.is_ready", player.getDisplayName(), path)
                    .withStyle(ChatFormatting.GREEN));
        }
        refreshAll(server);
    }

    /**
     * Opens the next crate waiting for this player, at once (Simon 04.11.: "Playtest 2" – as items they
     * went into the kit's stash in a dungeon or a night and could not be opened): the same roll and
     * band as a playtest crate in the hand, in every phase, kit or not. Its gain works from the next
     * part (the kit worn now is not swapped in the middle of a fight).
     */
    private void openCrate(ServerPlayer player, SiegeSave.PlaytestPlayer pp) {
        SiegeSave.PlaytestState s = state();
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        SkillClass clazz = own.clazz();
        if (pp.cratesDue <= 0 || clazz == null || s == null) {
            player.sendSystemMessage(Component.translatable("nightsiege.playtest.no_crates").withStyle(ChatFormatting.GRAY));
            send(player, true);
            return;
        }
        CrateKind kind = crateKind(pp.cratesGiven + 1, m.config());
        if (!m.crates().openOwed(player, clazz, kind, level())) {
            // The band of the last one still runs: one at a time.
            player.sendSystemMessage(Component.translatable("nightsiege.playtest.crate_spinning").withStyle(ChatFormatting.GRAY));
            send(player, false);
            return;
        }
        pp.cratesGiven++;
        pp.cratesDue = Math.max(0, pp.cratesDue - 1);
        m.persist();
        if (pp.cratesDue > 0) {
            player.sendSystemMessage(crateLink(pp.cratesDue));
        }
        send(player, false);
    }

    /** The kind of a player's n-th crate: every boss night (one in bossEvery) brought a fragment crate, event nights an event crate. */
    private CrateKind crateKind(int index, SiegeConfig cfg) {
        if (cfg.bossesEnabled && cfg.bossEvery > 0
                && index % Math.max(1, (int) Math.round(cfg.bossEvery * (1.0 + cfg.bonusCrateChance))) == 0) {
            return CrateKind.FRAGMENT;
        }
        return random.nextDouble() < cfg.eventChance ? CrateKind.EVENT : CrateKind.NORMAL;
    }

    /** "N crates wait – click: the next one opens" (runs /nightsiege playtest kisten). */
    private static Component crateLink(int count) {
        return Component.translatable("nightsiege.playtest.crates_waiting", count).withStyle(style -> style
                .withColor(ChatFormatting.LIGHT_PURPLE).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/nightsiege playtest kisten")));
    }

    // ------------------------------------------------------------------ the flow

    /** Every tick (the checks once a second). */
    void tick(MinecraftServer server) {
        SiegeSave.PlaytestState s = state();
        if (s == null || s.finished) {
            return;
        }
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) {
            return;
        }
        if (!"plan".equals(s.phase) && !"paused".equals(s.phase) && nobodyOnline(server, s) && pause(server, s)) {
            return;   // nobody of it online: the running part ends, the step waits (Playtest 2, 04.11.)
        }
        if (now % 100 == 0 && !"night".equals(s.phase)) {
            clearWildMonsters(server, s);   // between the parts nothing natural hunts the players (Simon 29.10.)
        }
        keepFed(server, s);
        switch (s.phase) {
            case "plan" -> {
                if (allReady(server, s)) {
                    beginNights(server, s);
                }
            }
            case "paused" -> {
                if (allReady(server, s)) {
                    resume(server, s);   // everyone online is back and ready: that part again
                }
            }
            case "pause_interlude" -> {
                if (now >= s.phaseEnds) {
                    startInterlude(server, s);
                } else if ((s.phaseEnds - now) % 100 == 0) {
                    refreshAll(server);
                }
            }
            case "interlude" -> {
                if (interludeOver(server, s) || now >= s.phaseEnds) {
                    endInterlude(server, s, now >= s.phaseEnds);
                } else if (now % 100 == 0) {
                    bringLate(server, s);
                }
            }
            case "pause_night" -> {
                if (now >= s.phaseEnds) {
                    startNight(server, s);
                }
            }
            case "night" -> {
                nightBegins(server, s);
                if (!m.isActive() && !m.isPreparing()) {
                    s.phase = "after_night";
                    s.phaseEnds = now + AFTER_NIGHT_SECONDS * 20L;
                    m.persist();
                    broadcast(server, Component.translatable(s.step + 1 < NIGHTS ? "nightsiege.playtest.night_over"
                            : "nightsiege.playtest.last_night_over", s.step + 1).withStyle(ChatFormatting.GOLD));
                    openAll(server, s);   // after the night the menu comes back (Simon 14:12)
                }
            }
            case "after_night" -> {
                if (now >= s.phaseEnds) {
                    nextStep(server, s);
                }
            }
            default -> {
            }
        }
    }

    private boolean allReady(MinecraftServer server, SiegeSave.PlaytestState s) {
        int online = 0;
        for (Map.Entry<String, SiegeSave.PlaytestPlayer> e : s.players.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(e.getKey()));
            if (p == null) {
                continue;   // away: does not hold the others up
            }
            online++;
            if (!e.getValue().ready) {
                return false;
            }
        }
        return online > 0;
    }

    /** /nightsiege playtest weiter: the current part ends now (planning: without the ones not ready). */
    public boolean skip(MinecraftServer server) {
        SiegeSave.PlaytestState s = state();
        if (s == null || s.finished) {
            return false;
        }
        long now = server.overworld().getGameTime();
        switch (s.phase) {
            case "plan" -> beginNights(server, s);
            case "pause_interlude" -> {
                s.phaseEnds = Math.min(s.phaseEnds, now);   // the dungeon may still be building: as soon as it stands
                startInterlude(server, s);
            }
            case "interlude" -> endInterlude(server, s, true);
            case "pause_night" -> startNight(server, s);
            case "night" -> {
                if (m.isPreparing()) {
                    m.cancelPreparation();
                }
                if (m.isActive()) {
                    m.abort(server);
                }
                s.phase = "after_night";
                s.phaseEnds = now + AFTER_NIGHT_SECONDS * 20L;   // the usual pause follows ("weiter" again skips it)
            }
            case "after_night" -> nextStep(server, s);
            case "paused" -> resume(server, s);
            default -> {
                return false;
            }
        }
        m.persist();
        return true;
    }

    /** The planning is over: every path is fixed, the first step begins. */
    private void beginNights(MinecraftServer server, SiegeSave.PlaytestState s) {
        for (Map.Entry<String, SiegeSave.PlaytestPlayer> e : s.players.entrySet()) {
            PlayerSkills own = m.save().skillsOf(e.getKey());
            e.getValue().path = own.history == null ? new ArrayList<>() : new ArrayList<>(own.history);
            e.getValue().ready = true;
        }
        s.step = 0;
        broadcast(server, Component.translatable("nightsiege.playtest.go").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        NightSiegeMod.LOGGER.info("[Night Siege] Playtest: Planung vorbei, Pfade {}", pathSummary(s));
        startStep(server, s);
    }

    private String pathSummary(SiegeSave.PlaytestState s) {
        StringBuilder out = new StringBuilder();
        for (SiegeSave.PlaytestPlayer pp : s.players.values()) {
            out.append(pp.name).append('=').append(pp.path.size()).append(' ');
        }
        return out.toString().trim();
    }

    /** A step begins: the night's points along the path, its crates, then the pause before the camp or dungeon. */
    private void startStep(MinecraftServer server, SiegeSave.PlaytestState s) {
        int level = s.levels.get(s.step);
        SiegeConfig cfg = m.config();
        int budget = TestKit.pointsBefore(level, cfg);
        int crates = cratesUntil(level, cfg);
        for (Map.Entry<String, SiegeSave.PlaytestPlayer> e : s.players.entrySet()) {
            SiegeSave.PlaytestPlayer pp = e.getValue();
            PlayerSkills own = m.save().skillsOf(e.getKey());
            int learned = replay(own, pp.path, budget, s.step == 0 ? 0 : pp.pointsGiven);
            pp.pointsGiven = Math.max(pp.pointsGiven, budget);
            pp.cratesDue = Math.max(0, crates - pp.cratesGiven);
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(e.getKey()));
            if (p != null) {
                SkillService.clear(p);
                ClassTags.update(server, p, own, m.skillTree().subclassOfProfile(own, own.chosenClass));
                p.sendSystemMessage(Component.translatable("nightsiege.playtest.step", s.step + 1, NIGHTS, level, budget,
                        learned, pp.path.size()).withStyle(ChatFormatting.AQUA));
                if (pp.cratesDue > 0) {
                    // They wait behind "Kiste öffnen" in the menu and this link (Playtest 2, 04.11.: as
                    // items they went into the kit's stash in a dungeon or a night and could not be opened).
                    p.sendSystemMessage(crateLink(pp.cratesDue));
                }
            }
        }
        toInterludePause(server, s);
    }

    /** The pause before the camp or the dungeon of this step (after a step began, or after a pause). */
    private void toInterludePause(MinecraftServer server, SiegeSave.PlaytestState s) {
        s.phase = "pause_interlude";
        s.phaseEnds = server.overworld().getGameTime() + PAUSE_INTERLUDE_SECONDS * 20L;
        s.campWon = false;
        waitSince = -1L;
        if ("dungeon".equals(s.interludes.get(s.step))) {
            // The dungeon is searched and built during the pause (update 30.10.: in one tick it held
            // the server up for seconds); the players are taken there once it stands. Its kind was
            // picked at the start (Playtest 2: three different ones, not by the ground).
            m.expeditions().prepare(server, dungeonOf(s));
        }
        m.persist();
        broadcast(server, Component.translatable("nightsiege.playtest.next_" + s.interludes.get(s.step), PAUSE_INTERLUDE_SECONDS)
                .withStyle(ChatFormatting.YELLOW));
        // Between the parts everyone looks at the own skill tree: what the night brings (Simon 28.10.).
        for (ServerPlayer p : online(server, s)) {
            send(p, false);
            if (ServerPlayNetworking.canSend(p, CoreNetworking.ScreenPayload.TYPE)) {
                ServerPlayNetworking.send(p, new CoreNetworking.ScreenPayload("playtest_tree", ""));
            }
            m.openCoreWindow(p);
        }
    }

    /**
     * The tree of a night: everything unlearned, then the planned path is learned again as far as the
     * points reach, the way a sensible player would (Simon 29.10.: "nobody puts the first skill to 4
     * before picking the subclass"): see {@link #learnLikeAPlayer}. Returns the levels learned.
     */
    int replay(PlayerSkills own, List<String> path, int budget, int given) {
        SkillTree tree = m.skillTree();
        if (given > 0 && own.hasClass() && own.levels != null && !own.levels.isEmpty()) {
            // Night after night only the new points are spent, on top of what the night before had
            // (a real player keeps his levels; playtest 29.10.: a level fell from 3 back to 1).
            own.award(Math.max(0, budget - given));
        } else {
            if (own.levels != null) {
                own.levels.clear();
            }
            if (own.history != null) {
                own.history.clear();
            }
            own.points = Math.max(0, budget);
            own.earned = own.points;
            own.lost = 0;
        }
        if (!own.hasClass()) {
            return 0;
        }
        // The subclass comes with its node at level 1, for free and outside the path (29 October 2026).
        own.grantFreeSubclasses();
        java.util.LinkedHashMap<String, Integer> planned = new java.util.LinkedHashMap<>();
        for (String id : path) {
            if (tree.get(id) != null) {
                planned.merge(id, 1, Integer::sum);
            }
        }
        // The path holds the bought levels only: the free ones come on top.
        for (Map.Entry<String, Integer> e : planned.entrySet()) {
            SkillNode node = tree.get(e.getKey());
            if (node.freeLevels() > 0 && own.levelOf(node.id()) >= node.freeLevels()) {
                e.setValue(e.getValue() + node.freeLevels());
            }
        }
        return learnLikeAPlayer(tree, own, planned);
    }

    /**
     * Spends the points of {@code own} on the planned levels (node → top level, in the planned order),
     * one level at a time, always the first that fits of:
     * <ol>
     *   <li>a free level (the subclass node's level 1, the root of the tree: it costs nothing);</li>
     *   <li>a planned node not learned yet that can be learned now (new abilities, the way, the
     *   subclass, the ult come as soon as they are open);</li>
     *   <li>a level that opens a planned node still shut (e.g. the subclass node at level 2 for the
     *   next ring);</li>
     *   <li>the planned node with the lowest level (ties: the planned order), so the levels grow evenly.</li>
     * </ol>
     * Returns the levels learned. Also used by the simulation ("punkte").
     */
    public static int learnLikeAPlayer(SkillTree tree, PlayerSkills own, java.util.LinkedHashMap<String, Integer> planned) {
        List<String> order = new ArrayList<>(planned.keySet());
        int learned = 0;
        for (int guard = 0; guard < 500; guard++) {
            String pick = null;
            // A free level first (the subclass node, the root of the tree since 29 October 2026).
            for (String id : order) {
                SkillNode node = tree.get(id);
                int lv = own.levelOf(id);
                if (node != null && node.isFreeAt(lv) && lv < planned.get(id) && tree.blockedReason(id, own) == null) {
                    pick = id;
                    break;
                }
            }
            for (String id : order) {
                if (pick != null) {
                    break;
                }
                if (own.levelOf(id) == 0 && tree.blockedReason(id, own) == null) {
                    pick = id;
                    break;
                }
            }
            if (pick == null) {
                List<String> shut = new ArrayList<>();
                for (String id : order) {
                    if (own.levelOf(id) == 0 && "locked".equals(tree.blockedReason(id, own))) {
                        shut.add(id);
                    }
                }
                if (!shut.isEmpty()) {
                    for (String id : order) {
                        if (own.levelOf(id) == 0 || own.levelOf(id) >= planned.get(id) || tree.blockedReason(id, own) != null) {
                            continue;
                        }
                        PlayerSkills test = Json.fromJson(Json.toJson(own), PlayerSkills.class);
                        if (test == null || !tree.unlock(id, test)) {
                            continue;
                        }
                        boolean opens = false;
                        for (String c : shut) {
                            String why = tree.blockedReason(c, test);
                            if (why == null || "points".equals(why)) {
                                opens = true;
                                break;
                            }
                        }
                        if (opens) {
                            pick = id;
                            break;
                        }
                    }
                }
            }
            if (pick == null) {
                int best = Integer.MAX_VALUE;
                for (String id : order) {
                    int lv = own.levelOf(id);
                    if (lv > 0 && lv < planned.get(id) && lv < best && tree.blockedReason(id, own) == null) {
                        best = lv;
                        pick = id;
                    }
                }
            }
            if (pick == null || !tree.unlock(pick, own)) {
                break;
            }
            learned++;
        }
        return learned;
    }

    /** Since when the players wait for a dungeon still being built (game time), or -1. */
    private long waitSince = -1L;
    /** How long they wait at most (then it goes on with the night). */
    private static final long MAX_WAIT_TICKS = 3 * 60 * 20L;
    private static final int STARTED = 0;
    private static final int FAILED = 1;
    private static final int WAITING = 2;

    private void startInterlude(MinecraftServer server, SiegeSave.PlaytestState s) {
        String kind = s.interludes.get(s.step);
        long now = server.overworld().getGameTime();
        int result = "camp".equals(kind) ? (startCamp(server, s) ? STARTED : FAILED) : startDungeon(server, s);
        if (result == WAITING) {
            if (waitSince < 0) {
                waitSince = now;
                broadcast(server, Component.translatable("nightsiege.playtest.dungeon_wait").withStyle(ChatFormatting.GRAY));
            }
            if (now - waitSince < MAX_WAIT_TICKS) {
                return;   // again next second (phaseEnds has passed)
            }
            NightSiegeMod.LOGGER.warn("[Night Siege] Playtest: Dungeon nach {} s noch nicht fertig, es geht ohne weiter",
                    (now - waitSince) / 20);
            m.expeditions().removeAll(server);
            result = FAILED;
        }
        waitSince = -1L;
        if (result == FAILED) {
            broadcast(server, Component.translatable("nightsiege.playtest.no_place_" + kind).withStyle(ChatFormatting.GRAY));
            toNightPause(server, s);
            return;
        }
        s.phase = "interlude";
        s.phaseEnds = now + ("camp".equals(kind) ? CAMP_MINUTES : DUNGEON_MINUTES) * 60L * 20L;
        m.persist();
        refreshAll(server);
    }

    private boolean startCamp(MinecraftServer server, SiegeSave.PlaytestState s) {
        BlockPos center = m.camp().forceCamp(server);
        SiegeSave.CampRecord camp = m.save().camp;
        if (center == null || camp == null) {
            return false;
        }
        s.campDay = camp.day;
        ServerLevel level = SiegeManager.levelByName(server, camp.dimension);
        if (level == null) {
            return false;
        }
        BlockPos gate = CampLayout.outsideGate(center, camp.facing);
        m.camp().openEntry(true);   // the playtest walks in as before (no war drum round)
        bringAll(server, s, level, gate, true);
        broadcast(server, Component.translatable("nightsiege.playtest.camp_go", CAMP_MINUTES).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** The dungeon prepared during the pause: the players go there once it stands complete. */
    private int startDungeon(MinecraftServer server, SiegeSave.PlaytestState s) {
        Expeditions exp = m.expeditions();
        String prep = exp.preparation(server);
        if (prep.isEmpty()) {
            exp.prepare(server, dungeonOf(s));   // not prepared (a restart in the pause): now
            prep = exp.preparation(server);
        }
        if ("failed".equals(prep)) {
            exp.removeAll(server);
            return FAILED;
        }
        if (!"ready".equals(prep)) {
            return WAITING;
        }
        exp.takePrepared();
        SiegeSave.ExpeditionRecord rec = exp.record();
        BlockPos door = exp.buildNow(server);
        ServerLevel level = rec == null ? null : SiegeManager.levelByName(server, rec.dimension);
        if (door == null || level == null) {
            exp.removeAll(server);
            return FAILED;
        }
        s.lostAttempts = exp.lostAttempts();
        exp.openEntry(true);   // the playtest goes straight down (no ready round at the seal stone)
        exp.noRetry(true);   // one attempt: lost is lost, it goes on (no second round at once)
        bringAll(server, s, level, door, false);
        broadcast(server, Component.translatable("nightsiege.playtest.dungeon_go",
                Component.translatable(com.mki.nightsiege.core.ExpeditionKind.byId(rec.kind) == null ? "nightsiege.playtest.dungeon"
                        : com.mki.nightsiege.core.ExpeditionKind.byId(rec.kind).translationKey()), DUNGEON_MINUTES)
                .withStyle(ChatFormatting.GOLD));
        return STARTED;
    }

    /**
     * Once the fight has begun, whoever is still outside (opening crates, lost on the way) is
     * brought to a fighter, so nobody misses the part (playtest 28.10.: one player never went in).
     * The fallen are left outside.
     */
    private void bringLate(MinecraftServer server, SiegeSave.PlaytestState s) {
        boolean camp = "camp".equals(s.interludes.get(s.step));
        List<UUID> fighters = camp ? m.camp().fighterIds() : m.expeditions().fighterIds();
        ServerPlayer lead = null;
        for (UUID id : fighters) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.isAlive()) {
                lead = p;
                break;
            }
        }
        if (lead == null || !(lead.level() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer p : online(server, s)) {
            UUID id = p.getUUID();
            boolean fallen = camp ? m.camp().hasFallen(id) : m.expeditions().hasFallen(id);
            if (fighters.contains(id) || fallen || !p.isAlive() || Drones.isDrone(p)) {
                continue;
            }
            if (p.isSpectator()) {
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
            p.teleportTo(level, lead.getX(), lead.getY(), lead.getZ(), java.util.Set.of(), lead.getYRot(), lead.getXRot(), true);
            p.sendSystemMessage(Component.translatable("nightsiege.playtest.brought_late", lead.getDisplayName())
                    .withStyle(ChatFormatting.YELLOW));
            NightSiegeMod.LOGGER.info("[Night Siege] Playtest: {} zu {} gebracht ({})", p.getScoreboardName(),
                    lead.getScoreboardName(), camp ? "Heerlager" : "Dungeon");
        }
    }

    /** The camp is gone or burns, or the dungeon is won or lost. */
    private boolean interludeOver(MinecraftServer server, SiegeSave.PlaytestState s) {
        if ("camp".equals(s.interludes.get(s.step))) {
            SiegeSave.CampRecord camp = m.save().camp;
            return camp == null || camp.destroyed;
        }
        SiegeSave.ExpeditionRecord rec = m.save().expedition;
        return rec == null || rec.won || m.expeditions().lostAttempts() > s.lostAttempts;
    }

    private void endInterlude(MinecraftServer server, SiegeSave.PlaytestState s, boolean timedOut) {
        if ("camp".equals(s.interludes.get(s.step))) {
            SiegeSave.CampRecord camp = m.save().camp;
            // The camp won (a defender fell) or nobody broke its workbench: its machines come tonight.
            boolean won = m.save().machinesDay >= 0 && m.save().machinesDay == s.campDay;
            boolean standing = camp != null && !camp.destroyed;
            s.campWon = won || standing;
            if (camp != null) {
                m.camp().endAll(server);
                m.camp().openEntry(false);
                if (standing) {
                    m.camp().remove(server);
                }
            }
            m.save().machinesDay = -1L;
            broadcast(server, Component.translatable(s.campWon ? "nightsiege.playtest.camp_machines"
                    : "nightsiege.playtest.camp_broken").withStyle(s.campWon ? ChatFormatting.RED : ChatFormatting.GREEN));
        } else {
            SiegeSave.ExpeditionRecord rec = m.save().expedition;
            boolean won = rec != null && rec.won;
            m.expeditions().endAll(server, null);
            m.expeditions().removeAll(server);
            m.expeditions().openEntry(false);
            broadcast(server, Component.translatable(won ? "nightsiege.playtest.dungeon_won"
                    : timedOut ? "nightsiege.playtest.dungeon_time" : "nightsiege.playtest.dungeon_lost")
                    .withStyle(won ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        toNightPause(server, s);
    }

    private void toNightPause(MinecraftServer server, SiegeSave.PlaytestState s) {
        for (ServerPlayer p : online(server, s)) {
            refresh(p);
            m.sendToCore(p);
        }
        s.phase = "pause_night";
        s.phaseEnds = server.overworld().getGameTime() + PAUSE_NIGHT_SECONDS * 20L;
        m.persist();
        BossKind boss = s.step == s.bossNight ? BossKind.byId(s.boss) : null;
        broadcast(server, Component.translatable(boss != null ? "nightsiege.playtest.night_boss_soon" : "nightsiege.playtest.night_soon",
                s.step + 1, s.levels.get(s.step), PAUSE_NIGHT_SECONDS,
                boss == null ? Component.empty() : Component.translatable("nightsiege.boss." + boss.id()))
                .withStyle(ChatFormatting.YELLOW));
        openAll(server, s);   // back at the core after the camp or dungeon: the menu again (Simon 14:12)
    }

    private void openAll(MinecraftServer server, SiegeSave.PlaytestState s) {
        for (ServerPlayer p : online(server, s)) {
            send(p, true);
        }
    }

    private void startNight(MinecraftServer server, SiegeSave.PlaytestState s) {
        CoreRecord core = m.mainCore();
        ServerLevel level = core == null ? null : SiegeManager.levelByName(server, core.dimension);
        int lvl = s.levels.get(s.step);
        BossKind boss = s.step == s.bossNight ? BossKind.byId(s.boss) : null;
        boolean ok = false;
        if (level != null) {
            for (ServerPlayer p : online(server, s)) {
                m.sendToCore(p);
                refresh(p);   // the start of a part: full life and food (Playtest 2)
            }
            m.ensureNight(server, SiegeEvent.NONE);
            m.setTestMachines(s.campWon);
            // Playtest 2 (Simon 04.11.): the event picked for this night at the start, never none (a boss
            // night has none); a playtest of before rolls one like a real night.
            ok = m.prepareTest(server, level, new BlockPos(core.x, core.y, core.z), lvl,
                    boss != null ? SiegeEvent.NONE : eventOf(s), boss);
        }
        // s.campWon stays until the next step: a night broken off by a pause comes again with the machines.
        if (!ok) {
            m.setTestMachines(false);
            broadcast(server, Component.translatable("nightsiege.playtest.night_failed").withStyle(ChatFormatting.RED));
            s.phase = "after_night";
            s.phaseEnds = server.overworld().getGameTime() + 40L;
        } else {
            s.phase = "night";
            s.phaseEnds = -1L;
            nightBegun = false;
            nightBegins(server, s);   // without a preparation time it runs already
            NightSiegeMod.LOGGER.info("[Night Siege] Playtest: Nacht {} auf Stufe {}{}", s.step + 1, lvl,
                    boss == null ? ", Ereignis " + (eventOf(s) == null ? "gewürfelt" : eventOf(s).name()) : " mit " + boss.id());
        }
        m.persist();
        refreshAll(server);
    }

    /** The test night of this step has begun (its preparation is over) and its defenders were refreshed. */
    private boolean nightBegun;

    /**
     * The test night begins (Playtest 2): its defenders start with full life and food, no fire and
     * nothing harmful – after the night's skills and kit, which raise the max life.
     */
    private void nightBegins(MinecraftServer server, SiegeSave.PlaytestState s) {
        if (nightBegun || !m.isActive()) {
            return;
        }
        nightBegun = true;
        for (ServerPlayer p : online(server, s)) {
            if (m.isDefending(p)) {
                refresh(p);
            }
        }
    }

    private void nextStep(MinecraftServer server, SiegeSave.PlaytestState s) {
        if (s.step + 1 >= NIGHTS) {
            finish(server, "nightsiege.playtest.done");
            return;
        }
        s.step++;
        startStep(server, s);
    }

    // ------------------------------------------------------------------ the pause (Playtest 2)

    /**
     * Tests: the playtest acts as if none of its players were online (a real logout would end the
     * client test). Only the pause looks at it.
     */
    public static boolean testAway;

    private boolean nobodyOnline(MinecraftServer server, SiegeSave.PlaytestState s) {
        return testAway || online(server, s).isEmpty();
    }

    /**
     * Nobody of the playtest is online (Simon 04.11.: "Playtest 2" – it went on alone and built dungeon
     * after dungeon until the restart): the running part ends without a result – a test night is broken
     * off (monsters gone, the base mended, the night's blocks gone, kits off), a dungeon or camp fight
     * ended and its building taken down – and the playtest waits. The part comes again once everyone
     * online is ready: the camp or dungeon when it paused during or before it, else the night; after a
     * night the next step. False when there is nothing to pause (planning waits by itself, and after the
     * last night the end comes as it would).
     */
    private boolean pause(MinecraftServer server, SiegeSave.PlaytestState s) {
        String part;
        switch (s.phase) {
            case "pause_interlude", "interlude" -> part = "interlude";
            case "pause_night", "night" -> part = "night";
            case "after_night" -> {
                if (s.step + 1 >= NIGHTS) {
                    return false;
                }
                part = "next";
            }
            default -> {
                return false;
            }
        }
        String was = s.phase;
        // Paused first: whatever the ending below sets off never sees the old phase.
        s.phase = "paused";
        s.pausedPart = part;
        s.phaseEnds = -1L;
        waitSince = -1L;
        if ("interlude".equals(part)) {
            dropInterlude(server, s);
        } else if ("night".equals(part)) {
            if (m.isPreparing()) {
                m.cancelPreparation();
            }
            if (m.isActive()) {
                m.abort(server);   // no result, no reward, nothing lost
            }
            m.setTestMachines(false);
        }
        for (SiegeSave.PlaytestPlayer pp : s.players.values()) {
            pp.ready = false;
        }
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Playtest pausiert (niemand online) in {} von Schritt {}: danach {}",
                was, s.step + 1, part);
        return true;
    }

    /** The camp or the dungeon of this step goes, built or still being searched and built. */
    private void dropInterlude(MinecraftServer server, SiegeSave.PlaytestState s) {
        if ("camp".equals(s.interludes.get(s.step))) {
            m.camp().endAll(server);
            m.camp().openEntry(false);
            if (m.save().camp != null) {
                m.camp().remove(server);
            }
            m.save().machinesDay = -1L;
        } else {
            m.expeditions().endAll(server, null);
            m.expeditions().removeAll(server);   // also a search or a build still going on
            m.expeditions().openEntry(false);
        }
        s.campWon = false;
    }

    /** Everyone online is ready again after a pause: the part that was left, with the usual short pause before it. */
    private void resume(MinecraftServer server, SiegeSave.PlaytestState s) {
        String part = s.pausedPart == null ? "" : s.pausedPart;
        s.pausedPart = "";
        for (SiegeSave.PlaytestPlayer pp : s.players.values()) {
            pp.ready = true;
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Playtest geht weiter: Schritt {}, {}", s.step + 1, part);
        broadcast(server, Component.translatable("nightsiege.playtest.resumed", partName(s, part)).withStyle(ChatFormatting.GREEN));
        switch (part) {
            case "night" -> toNightPause(server, s);
            case "next" -> nextStep(server, s);
            default -> toInterludePause(server, s);
        }
        m.persist();
    }

    /** What comes after the pause, for the chat: the camp, the dungeon (its kind), or the night. */
    private Component partName(SiegeSave.PlaytestState s) {
        return partName(s, s.pausedPart);
    }

    private Component partName(SiegeSave.PlaytestState s, String part) {
        int step = "next".equals(part) ? s.step + 1 : s.step;
        if (step < 0 || step >= s.levels.size()) {
            return Component.empty();
        }
        if ("night".equals(part)) {
            return Component.translatable("nightsiege.playtest.t_night", step + 1);
        }
        if ("camp".equals(s.interludes.get(step))) {
            return Component.translatable("nightsiege.playtest.i_camp");
        }
        String kind = s.dungeons == null || step >= s.dungeons.size() ? "" : s.dungeons.get(step);
        ExpeditionKind k = ExpeditionKind.byId(kind);
        return k == null ? Component.translatable("nightsiege.playtest.i_dungeon") : Component.translatable(k.translationKey());
    }

    // ------------------------------------------------------------------ no harm between the rounds (Playtest 2)

    /**
     * Simon 04.11. ("Playtest 2": "kein Tod zwischen den Runden"): a player of a running playtest takes
     * damage only as a defender of the running night or a fighter of the dungeon or camp fight (its
     * ready round over) – not while planning, in the pauses, on the way or before the fight starts. The
     * void and /kill still go through.
     */
    boolean allowDamage(Entity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player) || source == null || !takesPart(player.getUUID())) {
            return true;
        }
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || m.isDefending(player);
    }

    /** Once a second: outside the fights nobody of the playtest goes hungry. */
    private void keepFed(MinecraftServer server, SiegeSave.PlaytestState s) {
        for (ServerPlayer p : online(server, s)) {
            if (!m.isDefending(p) && p.isAlive()) {
                if (p.getFoodData().getFoodLevel() < 20) {
                    p.getFoodData().setFoodLevel(20);
                }
                if (p.getFoodData().getSaturationLevel() < 5f) {
                    p.getFoodData().setSaturation(5f);
                }
            }
        }
    }

    /** The start of a part (camp, dungeon, night): full life and food, no fire, no harmful effect. */
    private static void refresh(ServerPlayer p) {
        if (!p.isAlive()) {
            return;
        }
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5f);
        p.clearFire();
        p.resetFallDistance();
        List<net.minecraft.core.Holder<MobEffect>> bad = new ArrayList<>();
        for (MobEffectInstance effect : p.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                bad.add(effect.getEffect());
            }
        }
        for (net.minecraft.core.Holder<MobEffect> effect : bad) {
            p.removeEffect(effect);
        }
    }

    // ------------------------------------------------------------------ variety (Playtest 2)

    /** Events a playtest night can have: all but the eclipse, a day event (a test night is always made night). */
    private static final SiegeEvent[] NIGHT_EVENTS = {SiegeEvent.BLOOD_MOON, SiegeEvent.FOG,
            SiegeEvent.THUNDER, SiegeEvent.NETHER_RIFT, SiegeEvent.FROST};   // no theme wave (Simon 5 November 2026)
    /** Entries kept of the last playtests (per list), and how many of the newest count as recent. */
    private static final int RECENT_KEPT = 24;
    private static final int RECENT_DUNGEONS = 6;
    private static final int RECENT_EVENTS = 6;

    /**
     * Simon 04.11. ("Playtest 2": "mehr Abwechslung"): the dungeons of the playtest are different kinds out
     * of all eight (not by the ground: around the base it was nearly always the crypt or the keep), every
     * night without a boss has an event, none twice and never none; what the last playtests had comes last.
     */
    private void planVariety(SiegeSave.PlaytestState s, SiegeConfig cfg, SiegeSave.PlaytestRecent recent) {
        int dungeons = 0;
        for (String i : s.interludes) {
            if ("dungeon".equals(i)) {
                dungeons++;
            }
        }
        List<String> kinds = new ArrayList<>();
        for (ExpeditionKind k : ExpeditionKind.values()) {
            kinds.add(k.id());
        }
        List<String> pickedKinds = pickVaried(kinds, recent.dungeons, dungeons, RECENT_DUNGEONS, random);
        s.dungeons = new ArrayList<>();
        int k = 0;
        for (String i : s.interludes) {
            s.dungeons.add("dungeon".equals(i) && k < pickedKinds.size() ? pickedKinds.get(k++) : "");
        }
        int nights = 0;
        for (int i = 0; i < NIGHTS; i++) {
            if (i != s.bossNight) {
                nights++;
            }
        }
        // The events the config lets come first; one it has turned off only if there are too few.
        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        for (SiegeEvent e : NIGHT_EVENTS) {
            (eventWeight(e, cfg) > 0 ? on : off).add(e.name());
        }
        List<String> pickedEvents = pickVaried(on, recent.events, nights, RECENT_EVENTS, random);
        if (pickedEvents.size() < nights) {
            pickedEvents.addAll(pickVaried(off, recent.events, nights - pickedEvents.size(), RECENT_EVENTS, random));
        }
        s.events = new ArrayList<>();
        int e = 0;
        for (int i = 0; i < NIGHTS; i++) {
            s.events.add(i != s.bossNight && e < pickedEvents.size() ? pickedEvents.get(e++) : "");
        }
    }

    /**
     * {@code count} different ids of {@code all}: first those not among the newest {@code window} of
     * {@code recent} (at random), then the recent ones, the one used longest ago first.
     */
    static List<String> pickVaried(List<String> all, List<String> recent, int count, int window, Random random) {
        List<String> newest = last(recent, window);
        List<String> fresh = new ArrayList<>();
        List<String> used = new ArrayList<>();
        for (String id : all) {
            (newest.contains(id) ? used : fresh).add(id);
        }
        Collections.shuffle(fresh, random);
        used.sort(Comparator.comparingInt(newest::lastIndexOf));
        List<String> out = new ArrayList<>(fresh);
        out.addAll(used);
        return new ArrayList<>(out.subList(0, Math.max(0, Math.min(count, out.size()))));
    }

    private static List<String> last(List<String> list, int n) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(list.subList(Math.max(0, list.size() - n), list.size()));
    }

    private static int eventWeight(SiegeEvent e, SiegeConfig cfg) {
        return switch (e) {
            case BLOOD_MOON -> cfg.bloodMoonWeight;
            case FOG -> cfg.fogWeight;
            case THUNDER -> cfg.thunderWeight;
            case NETHER_RIFT -> cfg.netherRiftWeight;
            case FROST -> cfg.frostWeight;
            default -> 0;
        };
    }

    /** The save's history of the last playtests (made whole if an old save lacks it). */
    private SiegeSave.PlaytestRecent recent() {
        SiegeSave.PlaytestRecent r = m.save().playtestRecent;
        if (r == null) {
            r = m.save().playtestRecent = new SiegeSave.PlaytestRecent();
        }
        if (r.dungeons == null) {
            r.dungeons = new ArrayList<>();
        }
        if (r.events == null) {
            r.events = new ArrayList<>();
        }
        if (r.bosses == null) {
            r.bosses = new ArrayList<>();
        }
        return r;
    }

    /** This playtest's dungeon kinds, events and boss go into the history (the oldest drop out). */
    private static void remember(SiegeSave.PlaytestRecent r, SiegeSave.PlaytestState s) {
        for (String id : s.dungeons) {
            if (!id.isEmpty()) {
                r.dungeons.add(id);
            }
        }
        for (String id : s.events) {
            if (!id.isEmpty()) {
                r.events.add(id);
            }
        }
        if (s.boss != null && !s.boss.isEmpty()) {
            r.bosses.add(s.boss);
        }
        for (List<String> list : List.of(r.dungeons, r.events, r.bosses)) {
            while (list.size() > RECENT_KEPT) {
                list.remove(0);
            }
        }
    }

    /** The dungeon kind picked for this step, or null (a playtest of before: by the ground). */
    private static ExpeditionKind dungeonOf(SiegeSave.PlaytestState s) {
        return s.dungeons == null || s.step < 0 || s.step >= s.dungeons.size() ? null : ExpeditionKind.byId(s.dungeons.get(s.step));
    }

    /** The event picked for this step's night, or null (a playtest of before: rolled like a real night). */
    private static SiegeEvent eventOf(SiegeSave.PlaytestState s) {
        if (s.events == null || s.step < 0 || s.step >= s.events.size() || s.events.get(s.step).isEmpty()) {
            return null;
        }
        try {
            return SiegeEvent.valueOf(s.events.get(s.step));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ the end

    /** /nightsiege playtest stop, the last night's end or a restart: everything back as it was. */
    public void finish(MinecraftServer server, String messageKey) {
        SiegeSave.PlaytestState s = state();
        if (s == null || s.finished) {
            return;
        }
        s.finished = true;
        s.phase = "done";
        if (m.isPreparing()) {
            m.cancelPreparation();
        }
        if (m.isActive()) {
            m.abort(server);
        }
        m.setTestMachines(false);
        // The playtest's camp and dungeon go; the world gets its own back.
        m.camp().endAll(server);
        m.camp().openEntry(false);
        if (m.save().camp != null) {
            m.camp().remove(server);
        }
        m.expeditions().endAll(server, null);
        m.expeditions().openEntry(false);
        m.expeditions().removeAll(server);   // also a search or a build of its dungeon still going on
        m.save().expedition = s.realExpedition;
        m.save().camp = s.realCamp;
        m.save().machinesDay = s.realMachinesDay;
        m.persist();
        for (Map.Entry<String, SiegeSave.PlaytestPlayer> e : s.players.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(e.getKey()));
            if (p != null && restore(p, e.getValue())) {
                p.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GOLD));
            } else {
                e.getValue().restorePending = true;   // away or dead: when they log in or respawn
            }
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Playtest beendet ({}), alles zurückgesetzt", messageKey);
        cleanUp();
    }

    /** After a restart: a playtest that was running is undone (players come back as they log in). */
    void afterRestart(MinecraftServer server) {
        SiegeSave.PlaytestState s = state();
        if (s != null && !s.finished) {
            NightSiegeMod.LOGGER.info("[Night Siege] Playtest lief beim Neustart: wird zurückgesetzt");
            finish(server, "nightsiege.playtest.reset_restart");
        }
    }

    /** A player logs in: what the playtest took comes back if it ended while they were away. */
    void onJoin(ServerPlayer player) {
        if (giveBackWaiting(player)) {
            return;
        }
        SiegeSave.PlaytestState s = state();
        if (s == null) {
            return;
        }
        SiegeSave.PlaytestPlayer pp = s.players.get(player.getUUID().toString());
        if (pp != null && !s.finished) {
            boolean paused = "paused".equals(s.phase);
            if (paused) {
                // Back to a paused playtest (Playtest 2): the part they left is gone; at the core the menu
                // asks for "Bereit", and with everyone online ready that part comes again.
                m.sendToCore(player);
                player.sendSystemMessage(Component.translatable("nightsiege.playtest.paused_join", partName(s))
                        .withStyle(ChatFormatting.YELLOW));
                player.sendSystemMessage(menuLink());
            }
            send(player, "plan".equals(s.phase) || paused);
        }
    }

    /** Back from the dead (Simon 31.10.: survival safety): a restore that waited for the respawn. */
    void afterRespawn(ServerPlayer player) {
        giveBackWaiting(player);
    }

    /**
     * Gives a player back what a finished playtest took, if that still waits: the restore kept aside
     * from an earlier playtest first (the oldest holds the real things), else the one of the last.
     * Returns true when something came back.
     */
    private boolean giveBackWaiting(ServerPlayer player) {
        String id = player.getUUID().toString();
        Map<String, SiegeSave.PlaytestPlayer> kept = m.save().playtestRestores;
        SiegeSave.PlaytestPlayer old = kept == null ? null : kept.get(id);
        SiegeSave.PlaytestState s = state();
        SiegeSave.PlaytestPlayer pp = s == null || !s.finished ? null : s.players.get(id);
        if (old != null) {
            if (!restore(player, old)) {
                return false;   // dead: after the respawn
            }
            kept.remove(id);
            if (pp != null && pp.restorePending) {
                pp.restorePending = false;   // a newer one would only bring the playtest's things back
                pp.items = new ArrayList<>();
                pp.skills = "";
            }
            m.persist();
        } else if (pp != null && pp.restorePending) {
            if (!restore(player, pp)) {
                return false;
            }
        } else {
            return false;
        }
        player.sendSystemMessage(Component.translatable("nightsiege.playtest.reset_join").withStyle(ChatFormatting.GOLD));
        cleanUp();
        return true;
    }

    /** A new playtest starts: the restores of the last one that still wait move aside (the oldest stays). */
    private void keepWaitingRestores() {
        SiegeSave.PlaytestState s = state();
        if (s == null || !s.finished || s.players == null) {
            return;
        }
        for (Map.Entry<String, SiegeSave.PlaytestPlayer> e : s.players.entrySet()) {
            if (e.getValue() != null && e.getValue().restorePending) {
                if (m.save().playtestRestores == null) {
                    m.save().playtestRestores = new java.util.HashMap<>();
                }
                if (m.save().playtestRestores.putIfAbsent(e.getKey(), e.getValue()) == null) {
                    NightSiegeMod.LOGGER.info("[Night Siege] Playtest: Rückgabe für {} wartet weiter (beiseitegelegt)", e.getValue().name);
                }
                e.getValue().restorePending = false;
            }
        }
        m.persist();
    }

    /** Puts back what the playtest took. False (nothing done) while the player is dead: after the respawn. */
    private boolean restore(ServerPlayer player, SiegeSave.PlaytestPlayer pp) {
        if (player.isDeadOrDying()) {
            return false;   // the body would take it along and lose it on respawn (Simon 31.10.)
        }
        MinecraftServer server = player.level().getServer();
        if (m.kits().hasStash(player)) {
            m.kits().restore(player, false);   // the kit goes first, then the inventory of before; crates after it
        }
        Inventory inv = player.getInventory();
        inv.clearContent();
        HolderLookup.Provider regs = player.level().registryAccess();
        List<String> unreadable = new ArrayList<>();
        List<ItemStack> loose = new ArrayList<>();
        for (int i = 0; i < pp.items.size(); i++) {
            String raw = pp.items.get(i);
            ItemStack stack = KitService.load(raw, regs);
            if (stack == null) {
                unreadable.add(raw);   // kept in the quarantine, the rest comes back
            } else if (!stack.isEmpty() && i < inv.getContainerSize()) {
                inv.setItem(i, stack);
            } else if (!stack.isEmpty()) {
                loose.add(stack);
            }
        }
        for (ItemStack stack : loose) {
            inv.placeItemBackInInventory(stack);
        }
        if (!unreadable.isEmpty()) {
            m.kits().quarantine(player, unreadable);
        }
        PlayerSkills before = pp.skills == null || pp.skills.isEmpty() ? null : Json.fromJson(pp.skills, PlayerSkills.class);
        if (before == null) {
            before = new PlayerSkills();
        }
        m.skillTree().migrateFreeSubclass(before);   // kept from before the subclass came first: once, as on loading
        m.save().skills.put(player.getUUID().toString(), before);
        SkillService.clear(player);
        if (server != null) {
            ClassTags.update(server, player, before, m.skillTree().subclassOfProfile(before, before.chosenClass));
        }
        pp.restorePending = false;
        pp.items = new ArrayList<>();
        pp.skills = "";
        player.inventoryMenu.sendAllDataToRemote();
        KitService.savePlayer(player);   // the real things on disk before the save forgets them (Simon 31.10.)
        m.persist();
        if (ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE)) {
            PlaytestData data = new PlaytestData();
            data.phase = "done";
            ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("playtest_update", Json.toJson(data)));
        }
        m.crates().deliverPending(player);   // real crates that waited for the playtest to end
        ClassBanners.deliverPending(m, player);   // class banners too (update 6)
        return true;
    }

    /** The save forgets the playtest once nobody waits for their things any more. */
    private void cleanUp() {
        SiegeSave.PlaytestState s = state();
        if (s == null || !s.finished) {
            return;
        }
        for (SiegeSave.PlaytestPlayer pp : s.players.values()) {
            if (pp.restorePending) {
                m.persist();
                return;
            }
        }
        m.save().playtest = null;
        m.persist();
    }

    // ------------------------------------------------------------------ helpers

    private List<ServerPlayer> online(MinecraftServer server, SiegeSave.PlaytestState s) {
        List<ServerPlayer> out = new ArrayList<>();
        for (String id : s.players.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(id));
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    /** Takes every player there, side by side on the ground (a little off the spot). */
    private void bringAll(MinecraftServer server, SiegeSave.PlaytestState s, ServerLevel level, BlockPos at, boolean ground) {
        int i = 0;
        for (ServerPlayer p : online(server, s)) {
            if (p.isSpectator()) {
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
            int dx = (i % 3) - 1;
            int dz = (i / 3) % 3 - 1;
            int x = at.getX() + dx;
            int z = at.getZ() + dz;
            level.getChunk(x >> 4, z >> 4);
            int y = ground ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : at.getY();
            p.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
            refresh(p);   // the start of a part: full life and food, no fire, nothing harmful (Playtest 2)
            i++;
        }
    }

    private void broadcast(MinecraftServer server, Component message) {
        SiegeSave.PlaytestState s = state();
        if (s == null) {
            return;
        }
        for (ServerPlayer p : online(server, s)) {
            p.sendSystemMessage(message);
        }
    }

    private static Component menuLink() {
        MutableComponent link = Component.translatable("nightsiege.playtest.menu_link");
        return link.withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/nightsiege playtest menue")));
    }

    /** Refreshes the window of everyone who has it open. */
    private void refreshAll(MinecraftServer server) {
        SiegeSave.PlaytestState s = state();
        if (s == null) {
            return;
        }
        for (ServerPlayer p : online(server, s)) {
            send(p, false);
        }
    }

    /** The window: {@code open} opens it, else only an open one is refreshed. */
    public void send(ServerPlayer player, boolean open) {
        SiegeSave.PlaytestState s = state();
        if (s == null || player == null || !ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE)) {
            return;
        }
        SiegeSave.PlaytestPlayer me = s.players.get(player.getUUID().toString());
        if (me == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        PlaytestData data = new PlaytestData();
        data.phase = s.finished ? "done" : s.phase;
        data.pausedPart = s.pausedPart == null ? "" : s.pausedPart;
        data.step = s.step;
        data.target = s.target;
        for (int i = 0; i < NIGHTS && i < s.levels.size(); i++) {
            PlaytestData.Night night = new PlaytestData.Night();
            night.level = s.levels.get(i);
            night.interlude = i < s.interludes.size() ? s.interludes.get(i) : "";
            night.boss = i == s.bossNight ? s.boss : "";
            night.dungeon = s.dungeons != null && i < s.dungeons.size() ? s.dungeons.get(i) : "";
            night.event = s.events != null && i < s.events.size() && i != s.bossNight ? s.events.get(i) : "";
            night.done = i < s.step || (i == s.step && ("after_night".equals(s.phase)
                    || "paused".equals(s.phase) && "next".equals(s.pausedPart)));
            data.nights.add(night);
        }
        long now = server == null ? 0L : server.overworld().getGameTime();
        data.seconds = s.phaseEnds < 0 || "night".equals(s.phase) || "plan".equals(s.phase) ? -1
                : (int) Math.max(0, (s.phaseEnds - now) / 20);
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        data.ready = me.ready;
        data.hasClass = own.hasClass();
        data.clazz = own.hasClass() ? own.className() : "";
        data.subclass = own.hasClass() ? m.skillTree().subclassOfProfile(own, own.chosenClass) : "";
        data.planned = "plan".equals(s.phase) ? (own.history == null ? 0 : own.history.size()) : me.path.size();
        data.learned = own.history == null ? 0 : own.history.size();
        data.points = own.points();
        data.crates = me.cratesDue;
        for (Map.Entry<String, SiegeSave.PlaytestPlayer> e : s.players.entrySet()) {
            PlaytestData.Mate mate = new PlaytestData.Mate();
            PlayerSkills os = m.save().skillsOf(e.getKey());
            mate.name = e.getValue().name;
            mate.clazz = os.hasClass() ? os.className() : "";
            mate.subclass = os.hasClass() ? m.skillTree().subclassOfProfile(os, os.chosenClass) : "";
            mate.planned = "plan".equals(s.phase) ? (os.history == null ? 0 : os.history.size()) : e.getValue().path.size();
            mate.ready = e.getValue().ready;
            mate.online = server != null && server.getPlayerList().getPlayer(UUID.fromString(e.getKey())) != null;
            data.team.add(mate);
        }
        ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload(open ? "playtest" : "playtest_update", Json.toJson(data)));
    }

    /** One line for /nightsiege playtest status and the tests. */
    public String status() {
        SiegeSave.PlaytestState s = state();
        if (s == null) {
            return "kein Playtest";
        }
        return "phase=" + s.phase + ("paused".equals(s.phase) ? ":" + s.pausedPart : "") + " step=" + s.step
                + " levels=" + s.levels + " interludes=" + s.interludes + " dungeons=" + s.dungeons + " events=" + s.events
                + " boss=" + (s.bossNight + 1) + ":" + s.boss + " players=" + pathSummary(s) + " finished=" + s.finished;
    }

    /** The planned path of a player (for the tests). */
    public List<String> pathOf(UUID id) {
        SiegeSave.PlaytestState s = state();
        SiegeSave.PlaytestPlayer pp = s == null || id == null ? null : s.players.get(id.toString());
        return pp == null ? List.of() : pp.path;
    }

    /** Crates waiting for a player (for the tests). */
    public int cratesDue(UUID id) {
        SiegeSave.PlaytestState s = state();
        SiegeSave.PlaytestPlayer pp = s == null || id == null ? null : s.players.get(id.toString());
        return pp == null ? 0 : pp.cratesDue;
    }

    /** The current phase, or "" (for the tests). */
    public String phase() {
        SiegeSave.PlaytestState s = state();
        return s == null ? "" : s.phase;
    }

    /** The interlude before the current night, or "" (for the tests). */
    public String interlude() {
        SiegeSave.PlaytestState s = state();
        return s == null || s.step < 0 || s.step >= s.interludes.size() ? "" : s.interludes.get(s.step);
    }

    /** The dungeon kinds of the steps ("" before the camp), for the tests. */
    public List<String> dungeonPlan() {
        SiegeSave.PlaytestState s = state();
        return s == null || s.dungeons == null ? List.of() : List.copyOf(s.dungeons);
    }

    /** The events of the nights ("" for the boss night), for the tests. */
    public List<String> eventPlan() {
        SiegeSave.PlaytestState s = state();
        return s == null || s.events == null ? List.of() : List.copyOf(s.events);
    }

    /** The part a paused playtest plays again ("" when not paused), for the tests. */
    public String pausedPart() {
        SiegeSave.PlaytestState s = state();
        return s == null || !"paused".equals(s.phase) ? "" : s.pausedPart;
    }

    /** Tests: a player of the playtest logs in again (as the join event would call it). */
    public void testJoin(ServerPlayer player) {
        onJoin(player);
    }
}
