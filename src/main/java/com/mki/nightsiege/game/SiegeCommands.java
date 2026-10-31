package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeEvent;

import com.mki.nightsiege.core.AnchorPicker;
import com.mki.nightsiege.core.MobEntry;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillNode;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.UUID;

/**
 * Admin commands.
 *
 * <p>These are open to every player. To restrict them to operators, add
 * {@code .requires(source -> source.hasPermission(2))} right after each
 * {@code Commands.literal("nightsiege")} below.</p>
 */
public final class SiegeCommands {

    private SiegeCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nightsiege")
                .then(Commands.literal("info").executes(SiegeCommands::info))
                .then(Commands.literal("statistik").executes(SiegeCommands::nightStats))
                // The records (content update, 29 October 2026); clearing them is for admins only.
                .then(Commands.literal("rekorde").executes(ctx -> {
                            if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                                    && SiegeManager.get().sendRecords(player)) {
                                return 1;
                            }
                            reply(ctx.getSource(), Component.translatable("nightsiege.records.none"));
                            return 0;
                        })
                        .then(Commands.literal("loeschen")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("bestaetigen").executes(ctx -> {
                                    SiegeManager.get().clearRecords();
                                    reply(ctx.getSource(), Component.translatable("nightsiege.records.cleared"));
                                    return 1;
                                }))))
                // The playtest (Simon, 28 October 2026): four nights of rising level along planned paths.
                .then(Commands.literal("playtest")
                        .then(Commands.argument("zielstufe", IntegerArgumentType.integer(4, 1000))
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> playtestStart(ctx, false))
                                .then(Commands.literal("allein").executes(ctx -> playtestStart(ctx, true))))
                        .then(Commands.literal("stop").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    if (!SiegeManager.get().playtest().running()) {
                                        fail(ctx.getSource(), Component.translatable("nightsiege.playtest.none"));
                                        return 0;
                                    }
                                    boolean inIt = ctx.getSource().getPlayer() != null
                                            && SiegeManager.get().playtest().takesPart(ctx.getSource().getPlayer().getUUID());
                                    SiegeManager.get().playtest().finish(ctx.getSource().getServer(), "nightsiege.playtest.stopped");
                                    if (!inIt) {
                                        reply(ctx.getSource(), Component.translatable("nightsiege.playtest.stopped"));   // a player in it was told already
                                    }
                                    return 1;
                                }))
                        .then(Commands.literal("weiter").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    boolean ok = SiegeManager.get().playtest().skip(ctx.getSource().getServer());
                                    if (!ok) {
                                        fail(ctx.getSource(), Component.translatable("nightsiege.playtest.none"));
                                        return 0;
                                    }
                                    reply(ctx.getSource(), Component.translatable("nightsiege.playtest.skipped"));
                                    return 1;
                                }))
                        .then(Commands.literal("status").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    reply(ctx.getSource(), Component.literal(SiegeManager.get().playtest().status()));
                                    return 1;
                                }))
                        .then(Commands.literal("bereit").executes(ctx -> asPlayer(ctx,
                                p -> SiegeManager.get().playtest().onAction(p, "ready"))))
                        .then(Commands.literal("kisten").executes(ctx -> asPlayer(ctx,
                                p -> SiegeManager.get().playtest().onAction(p, "crates"))))
                        .then(Commands.literal("menue").executes(ctx -> asPlayer(ctx,
                                p -> SiegeManager.get().playtest().onAction(p, "menu")))))
                .then(Commands.literal("cores").executes(SiegeCommands::cores))
                .then(Commands.literal("skills").executes(SiegeCommands::skills))
                .then(Commands.literal("skill")
                        .then(Commands.argument("node", StringArgumentType.word())
                                .executes(SiegeCommands::buySkill)))
                .then(Commands.literal("mobs").executes(SiegeCommands::mobs))
                .then(Commands.literal("start").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SiegeCommands::start))
                // Try the drone of a fallen defender any time, e.g. in creative (Simon, 27 October 2026).
                .then(Commands.literal("drohne").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean up = Drones.toggleTest(player);
                            reply(ctx.getSource(), Component.translatable(up ? "nightsiege.cmd.drone_on" : "nightsiege.cmd.drone_off"));
                            return 1;
                        }))
                .then(Commands.literal("heerlager").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            net.minecraft.core.BlockPos at = SiegeManager.get().camp().forceCamp(ctx.getSource().getServer());
                            if (at == null) {
                                fail(ctx.getSource(), Component.translatable("nightsiege.cmd.camp_none"));
                                return 0;
                            }
                            reply(ctx.getSource(), Component.translatable("nightsiege.cmd.camp_built", at.getX(), at.getY(), at.getZ()));
                            return 1;
                        })
                        .then(Commands.literal("weg").executes(ctx -> {
                            SiegeManager.get().camp().remove(ctx.getSource().getServer());
                            reply(ctx.getSource(), Component.translatable("nightsiege.cmd.camp_removed"));
                            return 1;
                        })))
                .then(Commands.literal("maschinen").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            boolean ok = SiegeManager.get().machinesNow(ctx.getSource().getServer());
                            if (ok) {
                                reply(ctx.getSource(), Component.translatable("nightsiege.cmd.machines_now"));
                            } else {
                                fail(ctx.getSource(), Component.translatable("nightsiege.cmd.machines_not_now"));
                            }
                            return ok ? 1 : 0;
                        })
                        .then(Commands.literal("weg").executes(ctx -> {
                            int n = SiegeManager.get().machines().removeAll();
                            reply(ctx.getSource(), Component.translatable("nightsiege.cmd.machines_removed", n));
                            return n;
                        })))
                // The new attackers of update 1 to try out (27 October 2026); in a running night they join it.
                .then(Commands.literal("gegner").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("art", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(FOE_NAMES.keySet(), b))
                                .executes(SiegeCommands::foe)))
                // Update 2 "Fluch & Rache" (28 October 2026): the curse altar and the nemeses by hand.
                .then(Commands.literal("fluch").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SiegeCommands::curseList)
                        .then(Commands.literal("leeren").executes(SiegeCommands::curseClear))
                        .then(Commands.argument("art", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(com.mki.nightsiege.core.CurseKind.values()).map(com.mki.nightsiege.core.CurseKind::id), b))
                                .executes(SiegeCommands::curseToggle)))
                .then(Commands.literal("nemesis").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SiegeCommands::nemesisList)
                        .then(Commands.literal("rufen").executes(SiegeCommands::nemesisCall))
                        .then(Commands.literal("machen")
                                .then(Commands.argument("typ", StringArgumentType.word())
                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                List.of("zombie", "skeleton", "spider", "husk", "stray", "vindicator", "pillager", "witch", "wither_skeleton", "piglin_brute"), b))
                                        .executes(ctx -> nemesisMake(ctx, 1))
                                        .then(Commands.argument("stufe", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> nemesisMake(ctx, IntegerArgumentType.getInteger(ctx, "stufe"))))))
                        .then(Commands.literal("vergessen").executes(ctx -> nemesisForget(ctx, false))
                                .then(Commands.literal("test").executes(ctx -> nemesisForget(ctx, true)))))
                .then(Commands.literal("bossjetzt").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            boolean ok = SiegeManager.get().bossNow(ctx.getSource().getServer());
                            if (ok) {
                                reply(ctx.getSource(), Component.translatable("nightsiege.cmd.boss_now"));
                            } else {
                                fail(ctx.getSource(), Component.translatable("nightsiege.cmd.no_boss_now"));
                            }
                            return ok ? 1 : 0;
                        }))
                .then(Commands.literal("waechterjetzt").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            boolean ok = SiegeManager.get().wardenNow(ctx.getSource().getServer());
                            if (ok) {
                                reply(ctx.getSource(), Component.translatable("nightsiege.cmd.warden_now"));
                            } else {
                                fail(ctx.getSource(), Component.translatable("nightsiege.cmd.no_warden_now"));
                            }
                            return ok ? 1 : 0;
                        }))
                .then(Commands.literal("test").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> test(ctx, null))
                                .then(Commands.literal("normal").executes(ctx -> test(ctx, SiegeEvent.NONE)))
                                .then(Commands.literal("blutmond").executes(ctx -> test(ctx, SiegeEvent.BLOOD_MOON)))
                                .then(Commands.literal("boss").executes(ctx -> testBoss(ctx, null))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    for (String n : BOSS_NAMES) {
                                                        b.suggest(n);
                                                    }
                                                    return b.buildFuture();
                                                })
                                                .executes(ctx -> testBoss(ctx, StringArgumentType.getString(ctx, "name")))))
                                .then(Commands.literal("vierer").executes(SiegeCommands::testFinale))
                                .then(Commands.literal("doppel").executes(SiegeCommands::testLate))
                                .then(Commands.literal("waechter").executes(ctx -> testWarden(ctx, null))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                        java.util.Arrays.stream(com.mki.nightsiege.core.ExpeditionKind.values())
                                                                .map(com.mki.nightsiege.core.ExpeditionKind::id), b))
                                                .executes(ctx -> testWarden(ctx, StringArgumentType.getString(ctx, "name")))))
                                .then(Commands.literal("thema").executes(ctx -> test(ctx, SiegeEvent.THEME_WAVE)))
                                .then(Commands.literal("finsternis").executes(ctx -> test(ctx, SiegeEvent.ECLIPSE)))
                                .then(Commands.literal("nebel").executes(ctx -> test(ctx, SiegeEvent.FOG)))
                                .then(Commands.literal("gewitter").executes(ctx -> test(ctx, SiegeEvent.THUNDER)))
                                .then(Commands.literal("netherriss").executes(ctx -> test(ctx, SiegeEvent.NETHER_RIFT)))
                                .then(Commands.literal("frost").executes(ctx -> test(ctx, SiegeEvent.FROST)))))
                .then(Commands.literal("ereignis").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("polarlicht").executes(ctx -> {
                            SiegeManager.get().bonus().startAurora(ctx.getSource().getServer(), true);
                            return 1;
                        }))
                        .then(Commands.literal("polarlicht_ende").executes(ctx -> {
                            SiegeManager.get().bonus().endAurora(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("karawane").executes(ctx -> {
                            if (SiegeManager.get().bonus().spawnCaravan(ctx.getSource().getServer()) == null) {
                                fail(ctx.getSource(), Component.translatable("nightsiege.cmd.caravan_failed"));
                                return 0;
                            }
                            return 1;
                        })))
                .then(Commands.literal("stop").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SiegeCommands::stop))
                .then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SiegeCommands::reload))
                .then(Commands.literal("dungeon").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        // The own dungeons (dungeon update, 27 October 2026).
                        .then(Commands.literal("melden").executes(ctx -> expeditionReport(ctx, null))
                                .then(Commands.argument("art", StringArgumentType.word()).suggests(SiegeCommands::suggestExpeditions)
                                        .executes(ctx -> expeditionReport(ctx, StringArgumentType.getString(ctx, "art")))))
                        .then(Commands.literal("bauen").executes(SiegeCommands::expeditionBuild))
                        .then(Commands.literal("hin").executes(SiegeCommands::expeditionGo))
                        .then(Commands.literal("hier")
                                .then(Commands.argument("art", StringArgumentType.word()).suggests(SiegeCommands::suggestExpeditions)
                                        .executes(SiegeCommands::expeditionHere)))
                        .then(Commands.literal("status").executes(ctx -> {
                            reply(ctx.getSource(), Component.literal(SiegeManager.get().expeditions().testState()));
                            return 1;
                        }))
                        .then(Commands.literal("weg").executes(ctx -> {
                            boolean ok = SiegeManager.get().expeditions().remove(ctx.getSource().getServer());
                            reply(ctx.getSource(), Component.translatable(ok ? "nightsiege.cmd.expedition_removed"
                                    : "nightsiege.cmd.expedition_none"));
                            return ok ? 1 : 0;
                        }))
)
                .then(Commands.literal("sim").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> sim(ctx, 10, 150))
                        // The balance tool's team simulation (29 October 2026): only on a test world.
                        .then(Commands.literal("team")
                                .executes(ctx -> teamSim(ctx, ""))
                                .then(Commands.argument("einstellungen", StringArgumentType.greedyString())
                                        .executes(ctx -> teamSim(ctx, StringArgumentType.getString(ctx, "einstellungen")))))
                        .then(Commands.argument("stufe", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> sim(ctx, IntegerArgumentType.getInteger(ctx, "stufe"), 150))
                                .then(Commands.argument("sekunden", IntegerArgumentType.integer(20, 900))
                                        .executes(ctx -> sim(ctx, IntegerArgumentType.getInteger(ctx, "stufe"),
                                                IntegerArgumentType.getInteger(ctx, "sekunden"))))))
                .then(Commands.literal("eintragen").executes(ctx -> asPlayer(ctx, p -> SiegeManager.get().register(p))))
                .then(Commands.literal("austragen").executes(ctx -> asPlayer(ctx, p -> SiegeManager.get().unregister(p))))
                .then(Commands.literal("zusagen").executes(ctx -> asPlayer(ctx, p -> SiegeManager.get().vote(p, true))))
                .then(Commands.literal("absagen").executes(ctx -> asPlayer(ctx, p -> SiegeManager.get().vote(p, false))))
                .then(Commands.literal("balance").executes(ctx -> balance(ctx, false))
                        .then(Commands.literal("test").executes(ctx -> balance(ctx, true))))
                .then(Commands.literal("session").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("start").executes(ctx -> {
                            int moved = BalanceReport.newSession();
                            reply(ctx.getSource(), Component.translatable("nightsiege.cmd.session_start", moved));
                            return 1;
                        })))
                .then(Commands.literal("kit").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("stufe", IntegerArgumentType.integer(1, 1000)).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            if (player == null) {
                                fail(ctx.getSource(), Component.translatable("nightsiege.cmd.player_only"));
                                return 0;
                            }
                            int n = SiegeManager.get().giveKits(ctx.getSource().getServer(), player.level(), player.blockPosition(),
                                    IntegerArgumentType.getInteger(ctx, "stufe"), false);
                            reply(ctx.getSource(), Component.translatable("nightsiege.cmd.kits", n));
                            return 1;
                        })))
                .then(Commands.literal("base").executes(SiegeCommands::baseInfo)
                        .then(Commands.literal("radius").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    reply(ctx.getSource(), Component.translatable("nightsiege.cmd.base_radius",
                                            SiegeManager.get().config().baseRadius));
                                    return 1;
                                })
                                .then(Commands.argument("bloecke", IntegerArgumentType.integer(16, 192))
                                        .executes(SiegeCommands::baseRadius))))
                .then(Commands.literal("wipe").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("spieler")
                                .executes(ctx -> {
                                    reply(ctx.getSource(), Component.translatable("nightsiege.cmd.wipe_warn"));
                                    return 1;
                                })
                                .then(Commands.literal("bestaetigen").executes(SiegeCommands::wipePlayers))))
                // Skill points for latecomers (Simon 30.10., 23:44: "falls an Tag 5 oder so noch Personen hinzukommen").
                .then(Commands.literal("punkte").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("spieler", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.literal("aufholen").executes(SiegeCommands::pointsCatchUp))
                                .then(Commands.argument("anzahl", IntegerArgumentType.integer(-100000, 100000))
                                        .executes(SiegeCommands::pointsGive))))
                // The Ruhmeshalle (31.10.): empty it, or read every logger file in again (titles stay).
                .then(Commands.literal("ruhmeshalle").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("leeren").then(Commands.literal("bestaetigen").executes(ctx -> hallReset(ctx, false))))
                        .then(Commands.literal("neu_einlesen").executes(ctx -> hallReset(ctx, true))))
                .then(Commands.literal("dev")
                        .then(Commands.literal("reset").executes(SiegeCommands::devReset))
                        .then(Commands.argument("punkte", IntegerArgumentType.integer(1, 100000))
                                .executes(SiegeCommands::devGrant))));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager manager = SiegeManager.get();
        if (manager.isActive()) {
            reply(source, Component.translatable("nightsiege.cmd.info_active",
                    manager.currentLevel(), manager.secondsLeft()));
        } else {
            reply(source, Component.translatable("nightsiege.cmd.info_idle",
                    manager.currentLevel(),
                    manager.save().siegesWon,
                    manager.save().siegesLost));
        }
        return 1;
    }

    /** The statistics window of the last night again. */
    private static int nightStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !SiegeManager.get().resendNightStats(player)) {
            reply(source, Component.translatable("nightsiege.stats.none"));
            return 0;
        }
        return 1;
    }

    private static int cores(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager manager = SiegeManager.get();
        var cores = manager.save().cores;
        if (cores == null || cores.isEmpty()) {
            reply(source, Component.translatable("nightsiege.cmd.no_cores"));
            return 1;
        }
        reply(source, Component.translatable("nightsiege.cmd.cores_header", cores.size()));
        for (CoreRecord core : cores) {
            reply(source, Component.literal(" - " + core.dimension + " "
                    + core.x + " " + core.y + " " + core.z));
        }
        return 1;
    }

    private static int skills(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            fail(source, Component.translatable("nightsiege.cmd.players_only"));
            return 0;
        }
        SiegeManager.get().openCoreWindow(player);
        return 1;
    }

    private static int buySkill(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            fail(source, Component.translatable("nightsiege.cmd.players_only"));
            return 0;
        }
        SiegeManager manager = SiegeManager.get();
        String nodeId = StringArgumentType.getString(ctx, "node");
        PlayerSkills skills = manager.save().skillsOf(player.getUUID().toString());
        SkillNode node = manager.skillTree().get(nodeId);
        if (node == null) {
            fail(source, Component.translatable("nightsiege.cmd.unknown_skill", nodeId));
            return 0;
        }
        if (!manager.skillTree().unlock(nodeId, skills)) {
            fail(source, Component.translatable(
                    "nightsiege.menu.blocked_" + manager.skillTree().blockedReason(nodeId, skills),
                    node.costFor(skills.levelOf(nodeId))));
            return 0;
        }
        // An ultimate at its top level earns one unlock (a new class or subclass), as in the window.
        manager.skillTree().grantUltUnlocks(skills);
        manager.persist();
        // Skills gelten nur waehrend einer Belagerung. Laeuft gerade eine, wird
        // der Kauf sofort wirksam, sonst erst in der naechsten Nacht.
        if (manager.isActive()) {
            SkillService.apply(player, skills, manager.skillTree());
        }
        manager.persist();
        reply(source, Component.translatable("nightsiege.cmd.skill_bought",
                Component.translatable(node.translationKey()), skills.levelOf(nodeId)));
        manager.openCoreWindow(player);
        return 1;
    }

    private static int mobs(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager manager = SiegeManager.get();
        int level = manager.currentLevel();
        reply(source, Component.translatable("nightsiege.cmd.mobs_header", level));
        for (MobEntry entry : manager.config().mobs) {
            if (!entry.appliesTo(level)) {
                continue;
            }
            String state = MobFactory.isUnavailable(entry.id) ? " (nicht installiert)" : "";
            reply(source, Component.literal(" - " + entry.id + "  x" + entry.weight + state));
        }
        return 1;
    }

    /**
     * A test siege at any level, around the caller: shows what that level looks
     * like without touching the record, the points or the base.
     */
    /** German names of the bosses for the test command (the ids work too). */
    private static final String[] BOSS_NAMES = {"grabkoenig", "spinnenkoenigin", "knochenlich", "kriegsherr", "frostjarl",
            "glutkoloss", "sturmhexe", "rammbock"};

    private static com.mki.nightsiege.core.BossKind bossByName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase(java.util.Locale.ROOT).replace("ö", "oe");
        return switch (n) {
            case "grabkoenig" -> com.mki.nightsiege.core.BossKind.GRAVE_KING;
            case "spinnenkoenigin", "koenigin" -> com.mki.nightsiege.core.BossKind.SPIDER_QUEEN;
            case "knochenlich", "lich" -> com.mki.nightsiege.core.BossKind.BONE_LICH;
            case "kriegsherr", "illager" -> com.mki.nightsiege.core.BossKind.WARLORD;
            case "frostjarl", "jarl" -> com.mki.nightsiege.core.BossKind.FROST_JARL;
            case "glutkoloss", "koloss" -> com.mki.nightsiege.core.BossKind.EMBER_COLOSSUS;
            case "sturmhexe", "hexe" -> com.mki.nightsiege.core.BossKind.STORM_WITCH;
            case "rammbock", "titan", "rammbock-titan" -> com.mki.nightsiege.core.BossKind.RAM_TITAN;
            default -> com.mki.nightsiege.core.BossKind.byId(n);
        };
    }

    /** /nightsiege test <level> boss [name]: a test night that ends with a boss (it comes 15 s in). */
    private static int testBoss(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        com.mki.nightsiege.core.BossKind kind = name == null
                ? com.mki.nightsiege.core.BossKind.values()[new java.util.Random().nextInt(com.mki.nightsiege.core.BossKind.values().length)]
                : bossByName(name);
        if (kind == null) {
            fail(source, Component.translatable("nightsiege.cmd.no_boss", String.join(", ", BOSS_NAMES)));
            return 0;
        }
        return test(ctx, SiegeEvent.NONE, kind);
    }

    private static int test(CommandContext<CommandSourceStack> ctx, SiegeEvent event) {
        return test(ctx, event, null);
    }

    /** /nightsiege test <level> vierer: a test night as the Vierer-Nacht (four bosses, two at a time, the first after 5 s). */
    private static int testFinale(CommandContext<CommandSourceStack> ctx) {
        SiegeManager.get().markTestFinale(true);
        int result = test(ctx, SiegeEvent.NONE);
        if (result == 0) {
            SiegeManager.get().markTestFinale(false);
        }
        return result;
    }

    /** /nightsiege test <level> doppel: a test night as a late boss night (two bosses, 15 s in). */
    private static int testLate(CommandContext<CommandSourceStack> ctx) {
        SiegeManager.get().markTestLate(true);
        int result = test(ctx, SiegeEvent.NONE);
        if (result == 0) {
            SiegeManager.get().markTestLate(false);
        }
        return result;
    }

    /** /nightsiege test <level> waechter [dungeon]: a test night that ends with this dungeon warden (15 s in). */
    private static int testWarden(CommandContext<CommandSourceStack> ctx, String name) {
        com.mki.nightsiege.core.ExpeditionKind[] all = com.mki.nightsiege.core.ExpeditionKind.values();
        com.mki.nightsiege.core.ExpeditionKind kind = name == null ? all[new java.util.Random().nextInt(all.length)] : null;
        for (com.mki.nightsiege.core.ExpeditionKind k : all) {
            if (k.id().equals(name)) {
                kind = k;
            }
        }
        if (kind == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.no_warden",
                    String.join(", ", java.util.Arrays.stream(all).map(com.mki.nightsiege.core.ExpeditionKind::id).toList())));
            return 0;
        }
        SiegeManager.get().planTestWarden(kind);
        int result = test(ctx, SiegeEvent.NONE);
        if (result == 0) {
            SiegeManager.get().planTestWarden(null);
        }
        return result;
    }

    private static int test(CommandContext<CommandSourceStack> ctx, SiegeEvent event, com.mki.nightsiege.core.BossKind boss) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager manager = SiegeManager.get();
        if (manager.isActive() || manager.isPreparing()) {
            fail(source, Component.translatable("nightsiege.cmd.already_active"));
            return 0;
        }
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            fail(source, Component.translatable("nightsiege.cmd.player_only"));
            return 0;
        }
        int level = IntegerArgumentType.getInteger(ctx, "level");
        MinecraftServer server = source.getLevel().getServer();
        // A test night by day becomes night first (Simon, 27 October 2026: no more /time set night).
        manager.ensureNight(server, boss != null ? SiegeEvent.NONE : event);
        if (manager.config().testKits) {
            manager.giveKits(server, player.level(), player.blockPosition(), level, true);
        }
        // Weapons and traps now, the first wave after the preparation time.
        if (!manager.prepareTest(server, player.level(), player.blockPosition(), level, event, boss)) {
            fail(source, Component.translatable("nightsiege.cmd.no_target"));
            return 0;
        }
        if (manager.isPreparing()) {
            reply(source, Component.translatable("nightsiege.cmd.test_prepared",
                    Math.min(manager.config().maxLevel, level), manager.config().testPrepSeconds));
        } else {
            reply(source, Component.translatable("nightsiege.cmd.test_started", manager.currentLevel()));
        }
        return 1;
    }

    /** Class simulation: every subclass defends a test night alone, fully skilled. */

    /** /nightsiege sim team [Stufen Gruppen …] or "stop": the team simulation at the existing core. */
    private static int teamSim(CommandContext<CommandSourceStack> ctx, String args) {
        MinecraftServer server = ctx.getSource().getServer();
        if (args.trim().equals("stop")) {
            TeamSim.stop(server);
            reply(ctx.getSource(), Component.literal("Team-Sim gestoppt"));
            return 1;
        }
        String problem = TeamSim.start(server, args);
        if (problem != null) {
            fail(ctx.getSource(), Component.literal("Team-Sim: " + problem));
            return 0;
        }
        reply(ctx.getSource(), Component.literal("Team-Sim läuft – Ergebnis in config/nightsiege-teamsim.txt"));
        return 1;
    }

    private static int sim(CommandContext<CommandSourceStack> ctx, int level, int seconds) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            fail(source, Component.translatable("nightsiege.cmd.player_only"));
            return 0;
        }
        String problem = MeleeSim.start(player, level, seconds);
        if (problem != null) {
            fail(source, Component.translatable(problem));
            return 0;
        }
        reply(source, Component.translatable("nightsiege.sim.started", MeleeSim.SUBCLASSES.length, level, seconds));
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager manager = SiegeManager.get();
        if (manager.isActive()) {
            fail(source, Component.translatable("nightsiege.cmd.already_active"));
            return 0;
        }
        MinecraftServer server = source.getLevel().getServer();
        AnchorPicker.Result target = manager.findTarget(server);
        ServerLevel world;
        BlockPos anchor;
        if (target != null) {
            world = SiegeManager.levelByName(server, target.anchor().dimension());
            anchor = new BlockPos((int) target.anchor().x(), (int) target.anchor().y(), (int) target.anchor().z());
        } else {
            // No registered base with players nearby: fall back to where the caller stands.
            ServerPlayer player;
            try {
                player = source.getPlayerOrException();
            } catch (Exception e) {
                fail(source, Component.translatable("nightsiege.cmd.no_target"));
                return 0;
            }
            world = player.level();
            anchor = player.blockPosition();
        }
        if (world == null || !manager.start(server, world, anchor)) {
            fail(source, Component.translatable("nightsiege.cmd.no_target"));
            return 0;
        }
        reply(source, Component.translatable("nightsiege.cmd.started"));
        return 1;
    }

    /** Runs something for the player who typed (or clicked) the command. */
    /** /nightsiege playtest <target level>: every signed-up player online plans a path, then four nights. */
    private static int playtestStart(CommandContext<CommandSourceStack> ctx, boolean alone) {
        int target = IntegerArgumentType.getInteger(ctx, "zielstufe");
        ServerPlayer self = ctx.getSource().getPlayer();
        if (alone && self == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.player_only"));
            return 0;
        }
        String problem = SiegeManager.get().playtest().start(ctx.getSource().getServer(), target, alone ? self : null);
        if (problem != null) {
            fail(ctx.getSource(), Component.translatable(problem));
            return 0;
        }
        java.util.List<Integer> levels = Playtest.levelsFor(target);
        reply(ctx.getSource(), Component.translatable("nightsiege.playtest.started_admin", target,
                levels.get(0) + " / " + levels.get(1) + " / " + levels.get(2) + " / " + levels.get(3)));
        return 1;
    }

    private static int asPlayer(CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<ServerPlayer> action) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.player_only"));
            return 0;
        }
        action.accept(player);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager manager = SiegeManager.get();
        if (manager.cancelPreparation()) {
            reply(source, Component.translatable("nightsiege.cmd.prep_cancelled"));
            return 1;
        }
        if (!manager.isActive()) {
            fail(source, Component.translatable("nightsiege.cmd.not_active"));
            return 0;
        }
        // Counts as survived, so stopping never triggers the punishment.
        manager.finish(source.getLevel().getServer(), true);
        reply(source, Component.translatable("nightsiege.cmd.stopped"));
        return 1;
    }

    /** /nightsiege punkte <spieler> <anzahl>: gives (or, negative, takes) skill points. */
    private static int pointsGive(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "spieler");
        int amount = IntegerArgumentType.getInteger(ctx, "anzahl");
        int now = SiegeManager.get().grantPoints(target, amount, ctx.getSource().getTextName());
        reply(ctx.getSource(), Component.translatable(amount >= 0 ? "nightsiege.cmd.points_given" : "nightsiege.cmd.points_taken",
                target.getScoreboardName(), Math.abs(amount), now));
        return 1;
    }

    /** /nightsiege punkte <spieler> aufholen: up to the points one would have by the world's level. */
    private static int pointsCatchUp(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "spieler");
        SiegeManager m = SiegeManager.get();
        int level = m.currentLevel();
        int target0 = TestKit.pointsBefore(level, m.config());
        int added = m.catchUpPoints(target, ctx.getSource().getTextName());
        int now = m.save().skillsOf(target.getUUID().toString()).points();
        if (added <= 0) {
            reply(ctx.getSource(), Component.translatable("nightsiege.cmd.points_up_to_date", target.getScoreboardName(),
                    m.save().skillsOf(target.getUUID().toString()).earned, target0, level));
            return 0;
        }
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.points_caught_up", target.getScoreboardName(), level, added, now));
        return 1;
    }

    private static int hallReset(CommandContext<CommandSourceStack> ctx, boolean rebuild) {
        SiegeManager m = SiegeManager.get();
        int heroes = HallService.reset(m, ctx.getSource().getServer(), rebuild, ctx.getSource().getTextName());
        reply(ctx.getSource(), Component.translatable(rebuild ? "nightsiege.cmd.hall_rebuilt" : "nightsiege.cmd.hall_cleared", heroes));
        return 1;
    }

    /** Creative only: hands out experience so a class can be tried out. */
    private static int devGrant(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            fail(source, Component.translatable("nightsiege.cmd.player_only"));
            return 0;
        }
        if (!player.getAbilities().instabuild) {
            fail(source, Component.translatable("nightsiege.msg.creative_only"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "punkte");
        SiegeManager.get().devGrant(player, amount);
        reply(source, Component.translatable("nightsiege.cmd.dev_granted", amount));
        return 1;
    }

    /** Creative only: frees the player to pick a different class. */
    private static int devReset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            fail(source, Component.translatable("nightsiege.cmd.player_only"));
            return 0;
        }
        if (!player.getAbilities().instabuild) {
            fail(source, Component.translatable("nightsiege.msg.creative_only"));
            return 0;
        }
        SiegeManager.get().devResetClass(player);
        reply(source, Component.translatable("nightsiege.cmd.dev_reset"));
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestExpeditions(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (com.mki.nightsiege.core.ExpeditionKind kind : com.mki.nightsiege.core.ExpeditionKind.values()) {
            ids.add(kind.id());
        }
        return net.minecraft.commands.SharedSuggestionProvider.suggest(ids, builder);
    }

    /** The scouts report a dungeon now (of a given kind, or as the ground decides). */
    private static int expeditionReport(CommandContext<CommandSourceStack> ctx, String kindId) {
        com.mki.nightsiege.core.ExpeditionKind kind = kindId == null ? null : com.mki.nightsiege.core.ExpeditionKind.byId(kindId);
        if (kindId != null && kind == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.expedition_unknown", kindId));
            return 0;
        }
        Expeditions exp = SiegeManager.get().expeditions();
        if (exp.record() != null) {
            exp.remove(ctx.getSource().getServer());
        }
        // The scouts search over the next seconds (update 30.10.: it held the server up); the
        // result comes as a chat line to whoever sent them.
        ServerPlayer asker = ctx.getSource().getPlayer();
        if (!exp.scout(ctx.getSource().getServer(), kind, false, asker == null ? null : asker.getUUID())) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.expedition_no_spot"));
            return 0;
        }
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.expedition_searching"));
        return 1;
    }

    /** The reported dungeon is built now (its ground is loaded for it, over the next seconds). */
    private static int expeditionBuild(CommandContext<CommandSourceStack> ctx) {
        Expeditions exp = SiegeManager.get().expeditions();
        net.minecraft.core.BlockPos door = exp.buildNow(ctx.getSource().getServer());
        if (door == null) {
            boolean busy = exp.building();
            if (busy) {
                reply(ctx.getSource(), Component.translatable("nightsiege.cmd.expedition_building"));
                return 1;
            }
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.expedition_none"));
            return 0;
        }
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.expedition_built", door.getX(), door.getY(), door.getZ()));
        return 1;
    }

    /** To the gate of the reported dungeon (once it stands: else it is built first, then again). */
    private static int expeditionGo(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.player_only"));
            return 0;
        }
        Expeditions exp = SiegeManager.get().expeditions();
        net.minecraft.core.BlockPos door = exp.buildNow(ctx.getSource().getServer());
        SiegeSave.ExpeditionRecord rec = exp.record();
        if (door == null && rec != null && exp.building()) {
            reply(ctx.getSource(), Component.translatable("nightsiege.cmd.expedition_building"));
            return 1;
        }
        if (door == null || rec == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.expedition_none"));
            return 0;
        }
        net.minecraft.server.level.ServerLevel level = SiegeManager.levelByName(ctx.getSource().getServer(), rec.dimension);
        if (level == null) {
            return 0;
        }
        player.teleportTo(level, door.getX() + 0.5, door.getY(), door.getZ() + 0.5, java.util.Set.of(),
                player.getYRot(), player.getXRot(), true);
        return 1;
    }

    /** A dungeon of this kind right here, the way down in the direction one looks (to look at it). */
    private static int expeditionHere(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        com.mki.nightsiege.core.ExpeditionKind kind = com.mki.nightsiege.core.ExpeditionKind.byId(StringArgumentType.getString(ctx, "art"));
        if (player == null || kind == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.player_only"));
            return 0;
        }
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
        int facing = CampLayout.facingTowards(player.getLookAngle().x, player.getLookAngle().z);
        net.minecraft.core.BlockPos origin = CampLayout.world(player.blockPosition(), facing, 0, 0, 7);
        int rooms = com.mki.nightsiege.core.ExpeditionKind.rooms(SiegeManager.get().currentLevel());
        SiegeManager.get().expeditions().buildHere(level, origin, facing, kind, rooms);   // over the next seconds
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.expedition_building"));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager.get().reloadKeepingState();   // the state in memory is written first (31.10.)
        reply(source, Component.translatable("nightsiege.cmd.reloaded"));
        return 1;
    }

    /** Scans the base around the nearest core and tells what it found. */
    private static int baseInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            fail(source, Component.translatable("nightsiege.cmd.player_only"));
            return 0;
        }
        SiegeManager manager = SiegeManager.get();
        com.mki.nightsiege.core.CoreStats stats = new com.mki.nightsiege.core.CoreStats();
        BaseDefense.forgetScans();
        CoreRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        String dimension = SiegeManager.dimensionName(level);
        for (CoreRecord core : manager.save().cores) {
            if (core != null && dimension.equals(core.dimension)) {
                double d = player.distanceToSqr(core.x + 0.5, core.y + 0.5, core.z + 0.5);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = core;
                }
            }
        }
        if (best == null) {
            fail(source, Component.translatable("nightsiege.cmd.base_none"));
            return 0;
        }
        BaseScan scan = BaseDefense.scanFor(level, new BlockPos(best.x, best.y, best.z), manager.config());
        int radius = manager.config().baseRadius;
        if (scan.closed) {
            reply(source, Component.translatable("nightsiege.cmd.base_closed", scan.width(), scan.depth(), radius));
            StringBuilder where = new StringBuilder();
            for (int i = 0; i < scan.gates.size() && i < 4; i++) {
                BaseScan.Gate g = scan.gates.get(i);
                where.append(i > 0 ? ", " : "").append(g.pos().getX()).append(' ').append(g.pos().getY()).append(' ')
                        .append(g.pos().getZ()).append(g.arch() ? " (Torbogen)" : "");
                BaseDefense.highlight(player, g.pos());
            }
            reply(source, Component.translatable("nightsiege.cmd.base_gates", scan.gates.size(),
                    where.length() == 0 ? "-" : where.toString(), scan.spawnSpotCount()));
        } else if (scan.gap != null) {
            reply(source, Component.translatable("nightsiege.cmd.base_gap",
                    scan.gap.getX() + " " + scan.gap.getY() + " " + scan.gap.getZ(), scan.width(), scan.depth(), radius));
            BaseDefense.highlight(player, scan.gap);
        } else {
            reply(source, Component.translatable("nightsiege.cmd.base_open", scan.width(), scan.depth(), radius));
        }
        return 1;
    }

    /** Shows the evaluation and writes it to config/nightsiege-auswertung.txt. */
    private static int balance(CommandContext<CommandSourceStack> ctx, boolean tests) {
        List<Component> lines = BalanceReport.build(SiegeManager.get().config(), tests);
        for (Component line : lines) {
            reply(ctx.getSource(), line);
        }
        String file = BalanceReport.writeFile(lines);
        if (file != null) {
            reply(ctx.getSource(), Component.translatable("nightsiege.cmd.balance_file", file));
        }
        return 1;
    }

    private static int baseRadius(CommandContext<CommandSourceStack> ctx) {
        int radius = IntegerArgumentType.getInteger(ctx, "bloecke");
        SiegeManager.get().setBaseRadius(radius);
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.base_radius_set", radius));
        return 1;
    }

    private static int wipePlayers(CommandContext<CommandSourceStack> ctx) {
        int wiped = SiegeManager.get().wipePlayers(ctx.getSource().getServer());
        if (wiped < 0) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.wipe_night"));
            return 0;
        }
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.wipe_done", wiped));
        return 1;
    }

    /** The names of /nightsiege gegner and what they make. */
    private static final java.util.Map<String, String> FOE_NAMES = new java.util.LinkedHashMap<>();

    static {
        FOE_NAMES.put("wiedergaenger", Foes.Role.REVENANT.entryId());
        FOE_NAMES.put("tunnelgraeber", Foes.Role.TUNNELER.entryId());
        FOE_NAMES.put("standartentraeger", Foes.Role.STANDARD_BEARER.entryId());
        FOE_NAMES.put("enterhaken", Foes.Role.GRAPPLER.entryId());
        FOE_NAMES.put("eisenritter", Foes.Role.IRON_KNIGHT.entryId());
        FOE_NAMES.put("schatz", Foes.Role.TREASURE_RAIDER.entryId());
        FOE_NAMES.put("kamel", com.mki.nightsiege.core.SiegeConfig.CAMEL_TROOP);
    }

    private static int foe(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "art").toLowerCase(java.util.Locale.ROOT);
        String id = FOE_NAMES.get(name);
        if (id == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.foe_unknown", String.join(", ", FOE_NAMES.keySet())));
            return 0;
        }
        SiegeManager m = SiegeManager.get();
        Mob mob = spawnFoe(m, (ServerLevel) player.level(), id, player);
        if (mob == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.foe_failed"));
            return 0;
        }
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.foe_spawned", mob.getDisplayName()));
        return 1;
    }

    // ------------------------------------------------------------------ update 2: curses and nemeses

    private static int curseList(CommandContext<CommandSourceStack> ctx) {
        SiegeManager m = SiegeManager.get();
        List<com.mki.nightsiege.core.CurseKind> chosen = Curses.chosen(m.save());
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.curse_list", chosen.size(), m.config().curseMax,
                chosen.isEmpty() ? Component.literal("-") : Curses.names(chosen),
                Curses.count() == 0 ? Component.literal("-") : Curses.names(Curses.active())));
        return chosen.size();
    }

    private static int curseClear(CommandContext<CommandSourceStack> ctx) {
        SiegeManager m = SiegeManager.get();
        if (m.save().curses != null) {
            m.save().curses.clear();
        }
        m.persist();
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.curse_cleared"));
        return 1;
    }

    private static int curseToggle(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "art");
        if (com.mki.nightsiege.core.CurseKind.byId(id) == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.curse_unknown"));
            return 0;
        }
        return Curses.toggle(SiegeManager.get(), player, id) ? 1 : 0;
    }

    private static int nemesisList(CommandContext<CommandSourceStack> ctx) {
        SiegeManager m = SiegeManager.get();
        List<SiegeSave.NemesisRecord> all = new java.util.ArrayList<>(Nemesis.records(m.save(), false));
        all.addAll(Nemesis.records(m.save(), true));
        if (all.isEmpty()) {
            reply(ctx.getSource(), Component.translatable("nightsiege.cmd.nemesis_none"));
            return 0;
        }
        for (SiegeSave.NemesisRecord rec : all) {
            reply(ctx.getSource(), Component.translatable("nightsiege.cmd.nemesis_line", Nemesis.fullName(rec), rec.type, rec.level,
                    String.join(", ", rec.traits), String.join(", ", rec.victims), rec.status, rec.nightsAway,
                    rec.test ? " (Test)" : ""));
        }
        return all.size();
    }

    private static int nemesisCall(CommandContext<CommandSourceStack> ctx) {
        SiegeManager m = SiegeManager.get();
        if (!m.isActive()) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.nemesis_no_night"));
            return 0;
        }
        int n = m.nemesis().callIn(m.nemesisHost(), true);
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.nemesis_called", n));
        return n;
    }

    /** A test nemesis, as if this monster had slain the player who types it. */
    private static int nemesisMake(CommandContext<CommandSourceStack> ctx, int level) {
        SiegeManager m = SiegeManager.get();
        String type = StringArgumentType.getString(ctx, "typ").toLowerCase(java.util.Locale.ROOT);
        if (!type.contains(":")) {
            type = "minecraft:" + type;
        }
        if (MobFactory.isUnavailable(type)) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.nemesis_bad_type", type));
            return 0;
        }
        ServerPlayer victim = ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        SiegeSave.NemesisRecord rec = m.nemesis().make(m.nemesisHost(), type, victim, true, level);
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.nemesis_made", Nemesis.fullName(rec), type));
        return 1;
    }

    private static int nemesisForget(CommandContext<CommandSourceStack> ctx, boolean test) {
        SiegeManager m = SiegeManager.get();
        int n = Nemesis.clear(m.save(), test);
        m.persist();
        reply(ctx.getSource(), Component.translatable(test ? "nightsiege.cmd.nemesis_forgot_test" : "nightsiege.cmd.nemesis_forgot", n));
        return n;
    }

    /** Spawns one of the new attackers a few blocks in front of a player (the command and the spawn eggs). */
    public static Mob spawnFoe(SiegeManager m, ServerLevel level, String id, ServerPlayer player) {
        net.minecraft.world.phys.Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        net.minecraft.world.phys.Vec3 at = player.position().add(look.lengthSqr() < 1.0e-4 ? net.minecraft.world.phys.Vec3.ZERO : look.normalize().scale(4.0));
        BlockPos spot = BossService.groundNear(level, at.x, player.getY(), at.z);
        return spawnFoeAt(m, level, id, spot != null ? spot : player.blockPosition());
    }

    /** Spawns one of the new attackers on a spot; in a running night it joins it. */
    public static Mob spawnFoeAt(SiegeManager m, ServerLevel level, String id, BlockPos spot) {
        Foes.Role role = Foes.roleOf(id);
        if (m.isActive() && role == Foes.Role.TREASURE_RAIDER) {
            m.foes().treasureNow(m.foesHost());
            UUID t = m.foes().treasure();
            return t == null ? null : level.getEntity(t) instanceof Mob raider ? raider : null;
        }
        if (m.isActive()) {
            Mob mob = m.foesHost().spawn(id, spot);
            if (mob != null) {
                m.foesHost().addMob(mob);
                for (net.minecraft.world.entity.Entity rider : mob.getPassengers()) {
                    if (rider instanceof Mob r) {
                        m.foesHost().addMob(r);
                    }
                }
            }
            return mob;
        }
        Mob mob = MobFactory.spawnAt(level, spot, id, m.currentLevel(), m.config(), new java.util.Random(), SiegeEvent.NONE, false);
        if (mob != null) {
            // Outside a night only a look: without the siege tag it can be struck down like any monster.
            mob.removeTag(MobFactory.TAG);
            for (net.minecraft.world.entity.Entity rider : mob.getPassengers()) {
                rider.removeTag(MobFactory.TAG);
            }
        }
        return mob;
    }

    private static void reply(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static void fail(CommandSourceStack source, Component message) {
        source.sendFailure(message);
    }
}
