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

    /** The curse altar and the wanted posters (update 2 "Fluch & Rache"). */
    private static void describeCurses(CoreStats stats, SiegeManager manager, SiegeConfig cfg, SiegeSave save) {
        stats.cursesEnabled = cfg.cursesEnabled;
        stats.curseMax = cfg.curseMax;
        java.util.List<com.mki.nightsiege.core.CurseKind> chosen = Curses.chosen(save);
        for (com.mki.nightsiege.core.CurseKind k : chosen) {
            stats.curses.add(k.id());
        }
        stats.altarSealed = manager.isActive();
        for (com.mki.nightsiege.core.CurseKind k : Curses.active()) {
            stats.cursesTonight.add(k.id());
        }
        stats.curseValues.put("zaeh", cfg.curseHealth);
        stats.curseValues.put("blutdurst", cfg.curseDamage);
        stats.curseValues.put("uebermacht", cfg.curseMobs);
        stats.curseValues.put("eile", cfg.curseSpeed);
        stats.curseValues.put("siechtum", cfg.curseHeal);
        stats.curseValues.put("champions", 1.0);
        stats.curseValues.put("sturmlauf", cfg.curseWaves);
        stats.curseValues.put("stumpf", cfg.curseTraps);
        stats.curseCrateChance = cfg.curseCrateChance;
        stats.bonusCrateChance = cfg.bonusCrateChance;
        stats.cursePoints = cfg.cursePoints;
        stats.oddsPlain = cfg.crateOdds(com.mki.nightsiege.core.CrateKind.NORMAL).clone();
        stats.oddsCursed = chosen.isEmpty() ? stats.oddsPlain.clone()
                : CrateService.cursedOdds(cfg, com.mki.nightsiege.core.CrateKind.NORMAL, chosen.size());
        stats.nemesisEnabled = cfg.nemesisEnabled;
        java.util.List<SiegeSave.NemesisRecord> all = new java.util.ArrayList<>(Nemesis.records(save, false));
        all.addAll(Nemesis.records(save, true));
        for (SiegeSave.NemesisRecord rec : all) {
            CoreStats.NemesisLine line = new CoreStats.NemesisLine();
            line.id = rec.id;
            line.name = rec.name;
            line.type = rec.type;
            line.level = rec.level;
            line.traits = new java.util.ArrayList<>(rec.traits);
            line.victims = new java.util.ArrayList<>(rec.victims);
            line.kills = rec.kills;
            line.nightsAway = rec.nightsAway;
            line.status = rec.status;
            line.slainBy = rec.slainBy;
            line.test = rec.test;
            line.here = manager.isActive() && manager.nemesis().here(rec);
            stats.nemeses.add(line);
        }
        // Lurking first (on the field before them), real before test, then the slain, newest first.
        stats.nemeses.sort(java.util.Comparator.comparingInt((CoreStats.NemesisLine l) -> l.here ? 0 : "lauert".equals(l.status) ? 1 : 2)
                .thenComparing(l -> l.test)
                .thenComparing(java.util.Comparator.comparingInt((CoreStats.NemesisLine l) -> l.level).reversed()));
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
        stats.extraSiegeOn = cfg.extraSiegePerDay > 0;
        stats.extraSiegeFromLevel = cfg.extraSiegeFromLevel;
        stats.extraSiegeBlock = manager.extraSiegeBlock(server).name();
        stats.extraSiegeToday = manager.extraSiegeToday(server);
        stats.extraSiegeBy = stats.extraSiegeToday && save.extraCalledBy != null ? save.extraCalledBy : "";
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

        // Profiles (Simon, 26 October 2026): points can go into every own profile.
        stats.myClass = skills.className();
        stats.myProfile = skills.chosenClass == null ? "" : skills.chosenClass;
        stats.playClass = stats.myClass;
        for (String key : skills.profiles()) {
            com.mki.nightsiege.core.SkillClass c = PlayerSkills.classOfKey(key);
            if (c == null) {
                continue;
            }
            if (!stats.unlocked.contains(c.name())) {
                stats.unlocked.add(c.name());
            }
            CoreStats.ProfileLine line = new CoreStats.ProfileLine();
            line.key = key;
            line.clazz = c.name();
            line.subclass = tree.subclassOfProfile(skills, key);
            line.path = skills.asProfile(key, () -> skills.pathTaken(tree));
            SkillNode ult = tree.ultimateOfProfile(skills, key);
            line.ult = ult == null ? 0 : skills.levelInProfile(key, ult.id());
            line.ultMax = ult == null ? 3 : ult.maxLevel();
            line.playing = key.equals(skills.chosenClass);
            line.complete = tree.isCompleteProfile(skills, key);
            line.spent = tree.pointsSpentProfile(skills, key);
            if (line.complete && !stats.completeClasses.contains(c.name())) {
                stats.completeClasses.add(c.name());
            }
            stats.profiles.add(line);
            CoreStats.ProfileNodes nodes = new CoreStats.ProfileNodes();
            for (SkillNode node : tree.ofClass(c)) {
                int lvl = skills.levelInProfile(key, node.id());
                if (lvl > 0) {
                    nodes.levels.put(node.id(), lvl);
                }
                nodes.costs.put(node.id(), node.costFor(lvl));
                String why = skills.asProfile(key, () -> tree.blockedReason(node.id(), skills));
                if (why != null) {
                    nodes.blocked.put(node.id(), why);
                }
            }
            stats.profileNodes.put(key, nodes);
        }
        stats.unlocksFree = skills.unlocksFree;
        if (skills.unlocksFree > 0) {
            for (com.mki.nightsiege.core.SkillClass c : manager.unlockClasses(viewer, skills)) {
                stats.unlockClasses.add(c.name());
            }
            stats.unlockSubclasses.addAll(manager.unlockSubclasses(viewer, skills));
        }
        stats.nextClassOpen = manager.mayUnlock(viewer, skills);
        for (com.mki.nightsiege.core.SkillClass c : com.mki.nightsiege.core.SkillClass.values()) {
            if (c.isShared() || !manager.mayChoose(viewer, c)) {
                continue;
            }
            CoreStats.ClassLine line = new CoreStats.ClassLine();
            line.id = c.name();
            line.unlocked = skills.hasUnlocked(c);
            line.leveling = c == skills.clazz();   // points are one account: the played profile levels
            line.played = c == skills.clazz();
            line.complete = line.unlocked && tree.isComplete(skills, c);
            line.ultDone = line.unlocked && tree.ultimateMaxed(skills, c);
            line.subclass = tree.chosenSubclass(skills, c);
            if (line.unlocked) {
                String fixedWay = "";
                com.mki.nightsiege.core.SkillTree.SubclassInfo fixed =
                        com.mki.nightsiege.core.SkillTree.subclassByNode(skills.fixedSubOf(c));
                if (fixed != null) {
                    fixedWay = fixed.way();
                }
                String taken = skills.as(c, () -> skills.pathTaken(tree));
                line.way = taken.isEmpty() ? fixedWay : taken;
                line.spent = skills.as(c, () -> tree.pointsSpent(skills, c));
                line.total = tree.pointsToComplete(skills, c);
            }
            stats.classLines.add(line);
        }
        stats.summoner = manager.maySummon(viewer);
        if (skills.gains != null) {
            for (Map.Entry<String, Map<String, Integer>> entry : skills.gains.entrySet()) {
                stats.gains.put(entry.getKey(), new java.util.HashMap<>(entry.getValue()));
            }
        }
        stats.points = skills.points();
        stats.earned = skills.earned;
        stats.donated = skills.donated;
        stats.pathTaken = skills.pathTaken(tree);
        stats.subclass = tree.subclassOfProfile(skills, skills.chosenClass);
        // Test buttons (+50, reset, crates): creative, or admins while the playtest switch is on.
        stats.creative = viewer.getAbilities().instabuild || (manager.config().testToolsForEveryone && manager.isAdmin(viewer));
        stats.admin = manager.isAdmin(viewer);
        stats.classResetUnlimited = manager.config().testToolsForEveryone;
        stats.classResetAvailable = manager.classResetAvailable(skills);
        stats.nightFragment = save.nightFragment;
        stats.scout = manager.expeditions().scoutLine(viewer);
        describeCurses(stats, manager, cfg, save);
        stats.perkRadius = (int) Math.round(manager.config().perkRadius);
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
            com.mki.nightsiege.core.KeyHint hint = com.mki.nightsiege.core.KeyHint.forNode(tree, node);
            line.key = hint == com.mki.nightsiege.core.KeyHint.NONE ? "" : hint.name();
            boolean shared = node.clazz().isShared();
            line.level = shared ? save.coreSkills().levelOf(node.id()) : skills.levelIn(node.clazz(), node.id());
            line.maxLevel = node.maxLevel();
            line.cost = node.costFor(line.level);
            line.requires = node.requires();
            line.requiresLevel = node.requiresLevel();
            line.freeLevels = node.freeLevels();
            line.upgradeRequires = node.upgradeRequires();
            line.blocked = shared ? tree.blockedReason(node.id(), save.coreSkills())
                    : skills.hasUnlocked(node.clazz())
                    ? skills.as(node.clazz(), () -> tree.blockedReason(node.id(), skills))
                    : tree.blockedReason(node.id(), skills);
            line.live = node.effect().isLive();
            line.group = node.group() == null ? "" : node.group();
            line.col = node.col();
            line.locks = new java.util.ArrayList<>(node.locksList());
            line.lane = tree.laneOf(node.id());
            line.amount = node.amountPerLevel();
            line.values = com.mki.nightsiege.core.SkillValues.of(node.id());
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
