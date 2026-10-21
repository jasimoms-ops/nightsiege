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

    public static CoreStats build(MinecraftServer server, SiegeManager manager, ServerPlayer viewer) {
        SiegeConfig cfg = manager.config();
        SiegeSave save = manager.save();
        SkillTree tree = manager.skillTree();
        PlayerSkills skills = save.skillsOf(viewer.getUUID().toString());

        CoreStats stats = new CoreStats();
        int level = manager.currentLevel();

        stats.siegeActive = manager.isActive();
        stats.level = level;
        stats.secondsLeft = manager.secondsLeft();
        stats.eventKey = manager.currentEventKey();

        stats.countdownPercent = (int) Math.round(manager.scheduler().progress() * 100.0);
        stats.countdownMinutesLeft = (int) (manager.scheduler().remainingTicks() / 20L / 60L);
        stats.playersNear = manager.playersAtCore(server);
        stats.playersRequired = cfg.requiredPlayers;
        stats.coreRadius = (int) cfg.coreRadius;
        stats.clockRunning = stats.playersNear >= cfg.requiredPlayers;

        stats.mobsPerWave = SiegeMath.mobsPerBatch(level, Math.max(1, stats.playersNear), cfg);
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

        stats.myClass = skills.chosenClass == null ? "" : skills.chosenClass;
        stats.points = skills.points();
        stats.earned = skills.earned;
        stats.donated = skills.donated;
        stats.pathTaken = skills.pathTaken(tree);
        stats.subclass = tree.subclassOf(skills);
        stats.creative = viewer.getAbilities().instabuild;
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
        stats.perkRadius = (int) Math.round(manager.config().perkRadius);
        stats.perkInRange = manager.perks().inRange(viewer);
        stats.skillsEnabled = cfg.skillsEnabled;
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
            line.level = shared ? save.coreSkills().levelOf(node.id()) : skills.levelOf(node.id());
            line.maxLevel = node.maxLevel();
            line.cost = node.costFor(line.level);
            line.requires = node.requires();
            line.requiresLevel = node.requiresLevel();
            line.blocked = tree.blockedReason(node.id(), shared ? save.coreSkills() : skills);
            line.live = node.effect().isLive();
            line.group = node.group() == null ? "" : node.group();
            line.col = node.col();
            line.locks = new java.util.ArrayList<>(node.locksList());
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
