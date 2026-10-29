package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.BossKind;
import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.PlaytestData;
import com.mki.nightsiege.core.SiegeConfig;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
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
        List<ServerPlayer> players = new ArrayList<>();
        if (alone != null) {
            if (!m.isRegistered(alone)) {
                return "nightsiege.playtest.not_registered";
            }
            players.add(alone);
        } else {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (m.isRegistered(p) && !p.isSpectator()) {
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
        }
        SiegeConfig cfg = m.config();
        SiegeSave.PlaytestState s = new SiegeSave.PlaytestState();
        s.target = target;
        s.levels = levelsFor(target);
        s.interludes = new ArrayList<>(List.of("camp", "dungeon", "dungeon", "dungeon"));
        Collections.shuffle(s.interludes, random);
        if (cfg.bossesEnabled) {
            s.bossNight = 1 + random.nextInt(NIGHTS - 1);   // not the first night
            BossKind[] kinds = BossKind.values();
            s.boss = kinds[random.nextInt(kinds.length)].id();
        }
        // Inventories first: whatever the save cannot hold stops the playtest before anything changed.
        for (ServerPlayer p : players) {
            SiegeSave.PlaytestPlayer pp = new SiegeSave.PlaytestPlayer();
            pp.name = p.getScoreboardName();
            HolderLookup.Provider regs = p.level().registryAccess();
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                String json = KitService.encode(stack, regs);
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
            fresh.award(PLAN_POINTS);
            m.save().skills.put(p.getUUID().toString(), fresh);
            p.getInventory().clearContent();
            SkillService.clear(p);
            ClassTags.update(server, p, fresh, null);
        }
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Playtest bis Stufe {} mit {} Spielern: Stufen {}, vorher {}, Boss in Nacht {} ({})",
                target, players.size(), s.levels, s.interludes, s.bossNight + 1, s.boss);
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
            case "crates" -> claimCrates(player, pp);
            default -> send(player, true);
        }
    }

    private SiegeSave.PlaytestPlayer player(ServerPlayer player) {
        SiegeSave.PlaytestState s = state();
        return s == null || s.finished || player == null ? null : s.players.get(player.getUUID().toString());
    }

    private void toggleReady(ServerPlayer player, SiegeSave.PlaytestPlayer pp) {
        if (!planning()) {
            send(player, true);
            return;
        }
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
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

    /** Hands out the crates waiting for this player. */
    private void claimCrates(ServerPlayer player, SiegeSave.PlaytestPlayer pp) {
        SiegeSave.PlaytestState s = state();
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        SkillClass clazz = own.clazz();
        if (pp.cratesDue <= 0 || clazz == null || s == null) {
            player.sendSystemMessage(Component.translatable("nightsiege.playtest.no_crates").withStyle(ChatFormatting.GRAY));
            send(player, true);
            return;
        }
        SiegeConfig cfg = m.config();
        int level = level();
        int normal = 0;
        int event = 0;
        int fragment = 0;
        for (int i = 0; i < pp.cratesDue; i++) {
            int index = pp.cratesGiven + i + 1;
            // Every boss night (one in bossEvery) brought a fragment crate, event nights an event crate.
            if (cfg.bossesEnabled && cfg.bossEvery > 0 && index % Math.max(1, (int) Math.round(cfg.bossEvery * (1.0 + cfg.bonusCrateChance))) == 0) {
                fragment++;
            } else if (random.nextDouble() < cfg.eventChance) {
                event++;
            } else {
                normal++;
            }
        }
        give(player, clazz, CrateKind.NORMAL, level, normal);
        give(player, clazz, CrateKind.EVENT, level, event);
        give(player, clazz, CrateKind.FRAGMENT, level, fragment);
        int count = pp.cratesDue;
        pp.cratesGiven += count;
        pp.cratesDue = 0;
        m.persist();
        player.sendSystemMessage(Component.translatable("nightsiege.playtest.crates_given", count, level)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        send(player, false);
    }

    private static void give(ServerPlayer player, SkillClass clazz, CrateKind kind, int level, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack stack = CrateService.playtestCrate(clazz, kind, level);
        stack.setCount(count);
        player.getInventory().placeItemBackInInventory(stack);
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
        if (now % 100 == 0 && !"night".equals(s.phase)) {
            clearWildMonsters(server, s);   // between the parts nothing natural hunts the players (Simon 29.10.)
        }
        switch (s.phase) {
            case "plan" -> {
                if (allReady(server, s)) {
                    beginNights(server, s);
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
            case "pause_interlude" -> startInterlude(server, s);
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
                    // Straight into the inventory (Simon 29.10.: one only got them from the chat link);
                    // they stay with the player through every kit.
                    claimCrates(p, pp);
                }
            }
        }
        s.phase = "pause_interlude";
        s.phaseEnds = server.overworld().getGameTime() + PAUSE_INTERLUDE_SECONDS * 20L;
        s.campWon = false;
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
        java.util.LinkedHashMap<String, Integer> planned = new java.util.LinkedHashMap<>();
        for (String id : path) {
            if (tree.get(id) != null) {
                planned.merge(id, 1, Integer::sum);
            }
        }
        return learnLikeAPlayer(tree, own, planned);
    }

    /**
     * Spends the points of {@code own} on the planned levels (node → top level, in the planned order),
     * one level at a time, always the first that fits of:
     * <ol>
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
            for (String id : order) {
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

    private void startInterlude(MinecraftServer server, SiegeSave.PlaytestState s) {
        String kind = s.interludes.get(s.step);
        long now = server.overworld().getGameTime();
        boolean ok = "camp".equals(kind) ? startCamp(server, s) : startDungeon(server, s);
        if (!ok) {
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

    private boolean startDungeon(MinecraftServer server, SiegeSave.PlaytestState s) {
        Expeditions exp = m.expeditions();
        if (exp.record() != null) {
            exp.removeAll(server);
        }
        SiegeSave.ExpeditionRecord rec = exp.report(server, null);
        if (rec == null) {
            return false;
        }
        BlockPos door = exp.buildNow(server);
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (door == null || level == null) {
            exp.removeAll(server);
            return false;
        }
        s.lostAttempts = exp.lostAttempts();
        exp.openEntry(true);   // the playtest goes straight down (no ready round at the seal stone)
        exp.noRetry(true);   // one attempt: lost is lost, it goes on (no second round at once)
        bringAll(server, s, level, door, false);
        broadcast(server, Component.translatable("nightsiege.playtest.dungeon_go",
                Component.translatable(com.mki.nightsiege.core.ExpeditionKind.byId(rec.kind) == null ? "nightsiege.playtest.dungeon"
                        : com.mki.nightsiege.core.ExpeditionKind.byId(rec.kind).translationKey()), DUNGEON_MINUTES)
                .withStyle(ChatFormatting.GOLD));
        return true;
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
            p.setHealth(p.getMaxHealth());
            p.clearFire();
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
            }
            m.ensureNight(server, com.mki.nightsiege.core.SiegeEvent.NONE);
            m.setTestMachines(s.campWon);
            // No fixed event: the night rolls one like a real night (a boss night has none).
            ok = m.prepareTest(server, level, new BlockPos(core.x, core.y, core.z), lvl,
                    boss != null ? com.mki.nightsiege.core.SiegeEvent.NONE : null, boss);
        }
        s.campWon = false;
        if (!ok) {
            m.setTestMachines(false);
            broadcast(server, Component.translatable("nightsiege.playtest.night_failed").withStyle(ChatFormatting.RED));
            s.phase = "after_night";
            s.phaseEnds = server.overworld().getGameTime() + 40L;
        } else {
            s.phase = "night";
            s.phaseEnds = -1L;
            NightSiegeMod.LOGGER.info("[Night Siege] Playtest: Nacht {} auf Stufe {}{}", s.step + 1, lvl,
                    boss == null ? "" : " mit " + boss.id());
        }
        m.persist();
        refreshAll(server);
    }

    private void nextStep(MinecraftServer server, SiegeSave.PlaytestState s) {
        if (s.step + 1 >= NIGHTS) {
            finish(server, "nightsiege.playtest.done");
            return;
        }
        s.step++;
        startStep(server, s);
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
        if (m.save().expedition != null) {
            m.expeditions().removeAll(server);
        }
        m.save().expedition = s.realExpedition;
        m.save().camp = s.realCamp;
        m.save().machinesDay = s.realMachinesDay;
        m.persist();
        for (Map.Entry<String, SiegeSave.PlaytestPlayer> e : s.players.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(e.getKey()));
            if (p != null) {
                restore(p, e.getValue());
                p.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GOLD));
            } else {
                e.getValue().restorePending = true;
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
        SiegeSave.PlaytestState s = state();
        if (s == null) {
            return;
        }
        SiegeSave.PlaytestPlayer pp = s.players.get(player.getUUID().toString());
        if (pp == null) {
            return;
        }
        if (s.finished && pp.restorePending) {
            restore(player, pp);
            player.sendSystemMessage(Component.translatable("nightsiege.playtest.reset_join").withStyle(ChatFormatting.GOLD));
            cleanUp();
        } else if (!s.finished) {
            send(player, "plan".equals(s.phase));
        }
    }

    private void restore(ServerPlayer player, SiegeSave.PlaytestPlayer pp) {
        MinecraftServer server = player.level().getServer();
        if (m.kits().hasStash(player)) {
            m.kits().restore(player);   // the kit goes first, then the inventory of before
        }
        Inventory inv = player.getInventory();
        inv.clearContent();
        HolderLookup.Provider regs = player.level().registryAccess();
        for (int i = 0; i < pp.items.size() && i < inv.getContainerSize(); i++) {
            ItemStack stack = KitService.decode(pp.items.get(i), regs);
            if (!stack.isEmpty()) {
                inv.setItem(i, stack);
            }
        }
        PlayerSkills before = pp.skills == null || pp.skills.isEmpty() ? null : Json.fromJson(pp.skills, PlayerSkills.class);
        if (before == null) {
            before = new PlayerSkills();
        }
        m.save().skills.put(player.getUUID().toString(), before);
        SkillService.clear(player);
        if (server != null) {
            ClassTags.update(server, player, before, m.skillTree().subclassOfProfile(before, before.chosenClass));
        }
        pp.restorePending = false;
        pp.items = new ArrayList<>();
        pp.skills = "";
        m.persist();
        if (ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE)) {
            PlaytestData data = new PlaytestData();
            data.phase = "done";
            ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("playtest_update", Json.toJson(data)));
        }
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
            p.setHealth(p.getMaxHealth());
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
        data.step = s.step;
        data.target = s.target;
        for (int i = 0; i < NIGHTS && i < s.levels.size(); i++) {
            PlaytestData.Night night = new PlaytestData.Night();
            night.level = s.levels.get(i);
            night.interlude = i < s.interludes.size() ? s.interludes.get(i) : "";
            night.boss = i == s.bossNight ? s.boss : "";
            night.done = i < s.step || (i == s.step && "after_night".equals(s.phase));
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
        return "phase=" + s.phase + " step=" + s.step + " levels=" + s.levels + " interludes=" + s.interludes
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
}
