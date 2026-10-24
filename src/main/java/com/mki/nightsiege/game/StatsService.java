package com.mki.nightsiege.game;

import com.mki.nightsiege.core.CoreStats;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.Map;

/** Collects everything the core's window shows into one object. */
public final class StatsService {

    private StatsService() {
    }

    /** The base around the core next to the viewer: closed or open, how big, where the gap is. */
    private static void describeBase(CoreStats stats, SiegeSave save, SiegeConfig cfg, ServerPlayer viewer) {
        stats.baseRadius = cfg.baseRadius;
        if (!(viewer.level() instanceof net.minecraft.server.level.ServerLevel here) || save.cores == null) {
            return;
        }
        String dimension = SiegeManager.dimensionName(here);
        CoreRecord best = null;
        double bestDistance = 48.0 * 48.0;
        for (CoreRecord core : save.cores) {
            if (core == null || !dimension.equals(core.dimension)) {
                continue;
            }
            double d = viewer.distanceToSqr(core.x + 0.5, core.y + 0.5, core.z + 0.5);
            if (d < bestDistance) {
                bestDistance = d;
                best = core;
            }
        }
        if (best == null) {
            return;
        }
        BaseScan scan = BaseDefense.scanFor(here, new net.minecraft.core.BlockPos(best.x, best.y, best.z), cfg);
        stats.baseKnown = true;
        stats.baseClosed = scan.closed;
        stats.baseWidth = scan.width();
        stats.baseDepth = scan.depth();
        if (!scan.closed && scan.gap != null) {
            stats.baseGap = scan.gap.getX() + " " + scan.gap.getY() + " " + scan.gap.getZ();
            BaseDefense.highlight(viewer, scan.gap);
        }
    }

    public static CoreStats build(MinecraftServer server, SiegeManager manager, ServerPlayer viewer) {
        SiegeConfig cfg = manager.config();
        SiegeSave save = manager.save();
        SkillTree tree = manager.skillTree();
        PlayerSkills skills = save.skillsOf(viewer.getUUID().toString());

        CoreStats stats = new CoreStats();
        int level = manager.currentLevel();

        stats.siegeActive = manager.isActive();
        if (stats.siegeActive) {
            stats.nightKills = manager.nightKills();
            stats.killsRequired = manager.killsRequired();
        }
        stats.level = level;
        stats.secondsLeft = manager.secondsLeft();
        stats.eventKey = manager.currentEventKey();

        long clock = server.overworld().getOverworldClockTime();
        long day = SiegeMath.dayOf(clock);
        int time = SiegeMath.timeOfDay(clock);
        long siegeDay = save.nextSiegeDay;
        stats.countdownPercent = (int) Math.round(com.mki.nightsiege.core.SiegeRhythm.progress(day, time, siegeDay, cfg) * 100.0);
        stats.countdownMinutesLeft = (int) (com.mki.nightsiege.core.SiegeRhythm.ticksUntilWarning(day, time, siegeDay, cfg) / 20L / 60L);
        stats.daysUntilSiege = siegeDay < 0 ? -1 : (int) Math.max(0, siegeDay - day);
        stats.voteOpen = manager.voteOpen(server);
        stats.accepted = manager.acceptedCount();
        stats.acceptsRequired = cfg.requiredAccepts;
        stats.registeredCount = save.registered == null ? 0 : save.registered.size();
        String me = viewer.getUUID().toString();
        stats.myVote = save.accepted != null && save.accepted.contains(me) ? "yes"
                : save.declined != null && save.declined.contains(me) ? "no" : "";
        stats.nightComing = manager.nightComing(server);
        stats.playersNear = stats.accepted;
        stats.playersRequired = cfg.requiredAccepts;
        stats.coreRadius = (int) cfg.coreRadius;
        stats.clockRunning = stats.voteOpen || stats.nightComing;

        stats.mobsPerWave = SiegeMath.mobsPerBatch(level, Math.max(1, Math.max(stats.accepted, manager.playersAtCore(server))), cfg);
        stats.healthMultiplier = SiegeMath.healthMultiplier(level, cfg);
        stats.damageMultiplier = SiegeMath.damageMultiplier(level, cfg);
        stats.maxLiveMobs = cfg.maxLiveMobs;

        stats.siegesWon = save.siegesWon;
        stats.siegesLost = save.siegesLost;
        stats.bestStreak = save.bestStreak;
        stats.currentStreak = save.currentStreak;
        stats.totalKills = save.totalKills;
        stats.totalChampionKills = save.totalChampionKills;
        stats.highestLevelReached = Math.max(save.highestLevel, level);

        // The window's own class is the one being levelled: that is where points go.
        com.mki.nightsiege.core.SkillClass leveling = skills.levelingClass();
        stats.myClass = leveling == null ? "" : leveling.name();
        stats.playClass = skills.chosenClass == null ? "" : skills.chosenClass;
        stats.unlocked = new java.util.ArrayList<>(skills.unlockedClasses());
        for (String name : stats.unlocked) {
            if (tree.isComplete(skills, com.mki.nightsiege.core.SkillClass.byName(name))) {
                stats.completeClasses.add(name);
            }
        }
        stats.nextClassOpen = manager.mayOpenNextClass(skills);
        if (skills.gains != null) {
            for (Map.Entry<String, Map<String, Integer>> entry : skills.gains.entrySet()) {
                stats.gains.put(entry.getKey(), new java.util.HashMap<>(entry.getValue()));
            }
        }
        stats.points = skills.points();
        stats.earned = skills.earned;
        stats.donated = skills.donated;
        stats.pathTaken = skills.as(leveling, () -> skills.pathTaken(tree));
        stats.subclass = skills.as(leveling, () -> tree.subclassOf(skills));
        stats.creative = viewer.getAbilities().instabuild;
        stats.classResetAvailable = manager.classResetAvailable(skills);
        for (com.mki.nightsiege.core.PerkKind kind : com.mki.nightsiege.core.PerkKind.values()) {
            CoreStats.PerkLine line = new CoreStats.PerkLine();
            line.id = kind.id();
            line.fragments = manager.perks().fragments(kind);
            line.stage = com.mki.nightsiege.core.PerkKind.stageFor(line.fragments);
            line.stageAt = com.mki.nightsiege.core.PerkKind.fragmentsFor(line.stage);
            line.nextAt = line.stage >= com.mki.nightsiege.core.PerkKind.MAX_STAGE ? 0
                    : com.mki.nightsiege.core.PerkKind.fragmentsFor(line.stage + 1);
            stats.perks.add(line);
        }
        stats.nightFragment = save.nightFragment;
        stats.perkRadius = (int) Math.round(manager.config().perkRadius);
        stats.perkInRange = manager.perks().inRange(viewer);
        stats.skillsEnabled = cfg.skillsEnabled;
        describeBase(stats, save, cfg, viewer);
        stats.corePoints = save.coreSkills().points();
        stats.coreEarned = save.coreSkills().earned;

        if (save.playerStats != null) {
            for (Map.Entry<String, PlayerStats> entry : save.playerStats.entrySet()) {
                PlayerStats p = entry.getValue();
                boolean online = false;
                try {
                    online = server.getPlayerList().getPlayer(java.util.UUID.fromString(entry.getKey())) != null;
                } catch (IllegalArgumentException ignored) {
                    // A malformed uuid in a hand-edited save must not break the window.
                }
                stats.players.add(new CoreStats.PlayerLine(
                        p.name == null || p.name.isBlank() ? "?" : p.name,
                        p.kills, p.championKills, p.fallen, online));
            }
            stats.players.sort(Comparator.comparingInt((CoreStats.PlayerLine l) -> l.kills).reversed());
        }

        for (SkillNode node : tree.all()) {
            CoreStats.NodeLine line = new CoreStats.NodeLine();
            line.id = node.id();
            line.clazz = node.clazz().name();
            line.path = node.path() == null ? "" : node.path();
            line.icon = node.icon();
            line.row = node.row();
            boolean shared = node.clazz().isShared();
            line.level = shared ? save.coreSkills().levelOf(node.id()) : skills.levelIn(node.clazz(), node.id());
            line.maxLevel = node.maxLevel();
            line.cost = node.costFor(line.level);
            line.requires = node.requires();
            line.requiresLevel = node.requiresLevel();
            line.blocked = shared ? tree.blockedReason(node.id(), save.coreSkills())
                    : skills.hasUnlocked(node.clazz())
                    ? skills.as(node.clazz(), () -> tree.blockedReason(node.id(), skills))
                    : tree.blockedReason(node.id(), skills);
            line.live = node.effect().isLive();
            line.group = node.group() == null ? "" : node.group();
            line.col = node.col();
            line.locks = new java.util.ArrayList<>(node.locksList());
            line.lane = tree.laneOf(node.id());
            stats.nodes.add(line);
        }
        return stats;
    }

    /** Builds the window state and sends it to one player. */
    public static void sendTo(ServerPlayer player, SiegeManager manager) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String json = Json.toJson(build(server, manager, player));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new CoreNetworking.CoreUiPayload(json));
    }
}
