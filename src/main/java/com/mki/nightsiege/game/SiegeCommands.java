package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeEvent;

import com.mki.nightsiege.core.AnchorPicker;
import com.mki.nightsiege.core.MobEntry;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.DungeonType;
import com.mki.nightsiege.core.PerkKind;
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
                        .then(Commands.literal("list").executes(SiegeCommands::dungeonList))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("art", StringArgumentType.word()).suggests(SiegeCommands::suggestDungeons)
                                        .executes(SiegeCommands::dungeonTp)))
                        .then(Commands.literal("start")
                                .then(Commands.argument("art", StringArgumentType.word()).suggests(SiegeCommands::suggestDungeons)
                                        .executes(ctx -> dungeonStart(ctx, -1, null))
                                        .then(Commands.argument("wellen", IntegerArgumentType.integer(1, 9))
                                                .executes(ctx -> dungeonStart(ctx, IntegerArgumentType.getInteger(ctx, "wellen"), null))
                                                .then(Commands.argument("stufe", StringArgumentType.word())
                                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                                java.util.List.of("easy", "medium", "hard", "nether"), b))
                                                        .executes(ctx -> dungeonStart(ctx, IntegerArgumentType.getInteger(ctx, "wellen"),
                                                                StringArgumentType.getString(ctx, "stufe")))))))
                        .then(Commands.literal("reset").executes(ctx -> dungeonReset(ctx, null))
                                .then(Commands.argument("art", StringArgumentType.word()).suggests(SiegeCommands::suggestDungeons)
                                        .executes(ctx -> dungeonReset(ctx, StringArgumentType.getString(ctx, "art")))))
                        .then(Commands.literal("perk")
                                .then(Commands.argument("perk", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            java.util.List<String> ids = new java.util.ArrayList<>();
                                            for (PerkKind kind : PerkKind.values()) {
                                                ids.add(kind.id());
                                            }
                                            return net.minecraft.commands.SharedSuggestionProvider.suggest(ids, b);
                                        })
                                        .then(Commands.argument("stufe", IntegerArgumentType.integer(0, PerkKind.MAX_STAGE))
                                                .executes(SiegeCommands::perkSet)))))
                .then(Commands.literal("sim").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> sim(ctx, 10, 150))
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
    private static final String[] BOSS_NAMES = {"grabkoenig", "spinnenkoenigin", "knochenlich", "kriegsherr"};

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

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestDungeons(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (DungeonType type : SiegeManager.get().config().dungeons) {
            keys.add(type.key);
        }
        return net.minecraft.commands.SharedSuggestionProvider.suggest(keys, builder);
    }

    private static DungeonType dungeonArg(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "art");
        DungeonType type = DungeonType.byKey(SiegeManager.get().config().dungeons, key);
        if (type == null) {
            fail(ctx.getSource(), Component.translatable("nightsiege.cmd.dungeon_unknown", key));
        }
        return type;
    }

    private static int dungeonList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager manager = SiegeManager.get();
        DungeonToolState state = manager.dungeons().toolState(source.getLevel());
        for (DungeonToolState.Entry entry : state.entries) {
            Component line = Component.literal(entry.installed ? "● " : "○ ")
                    .withStyle(entry.installed ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.DARK_GRAY)
                    .append(Component.literal(entry.key + " ").withStyle(net.minecraft.ChatFormatting.WHITE))
                    .append(Component.literal("[" + entry.tier.toLowerCase(java.util.Locale.ROOT) + ", "
                            + entry.theme.toLowerCase(java.util.Locale.ROOT) + "] ").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(Component.translatable("nightsiege.cmd.dungeon_line", entry.found, entry.beaten)
                            .withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
            reply(source, line);
        }
        StringBuilder perks = new StringBuilder();
        for (PerkKind kind : PerkKind.values()) {
            perks.append(kind.id()).append(' ').append(PerkService.roman(manager.perks().stage(kind)))
                    .append(" (").append(manager.perks().fragments(kind)).append(")  ");
        }
        reply(source, Component.literal(perks.toString().trim()).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        return 1;
    }

    private static int dungeonTp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        DungeonType type = dungeonArg(ctx);
        if (player == null || type == null) {
            if (player == null) {
                fail(source, Component.translatable("nightsiege.cmd.player_only"));
            }
            return 0;
        }
        reply(source, SiegeManager.get().dungeons().travel(player, type));
        SiegeManager.get().persist();
        return 1;
    }

    private static int dungeonStart(CommandContext<CommandSourceStack> ctx, int waves, String tierName) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        DungeonType type = dungeonArg(ctx);
        if (player == null || type == null) {
            if (player == null) {
                fail(source, Component.translatable("nightsiege.cmd.player_only"));
            }
            return 0;
        }
        com.mki.nightsiege.core.DungeonTier tier = tierName == null ? type.tier()
                : com.mki.nightsiege.core.DungeonTier.byName(tierName);
        if (tier == null) {
            tier = type.tier();
        }
        SiegeManager.get().dungeons().startTest(player, type, waves < 1 ? tier.waves() : waves, tier, 0, true,
                type.theme(), null);
        return 1;
    }

    private static int dungeonReset(CommandContext<CommandSourceStack> ctx, String key) {
        CommandSourceStack source = ctx.getSource();
        DungeonType type = null;
        if (key != null) {
            type = DungeonType.byKey(SiegeManager.get().config().dungeons, key);
            if (type == null) {
                fail(source, Component.translatable("nightsiege.cmd.dungeon_unknown", key));
                return 0;
            }
        }
        int count = SiegeManager.get().dungeons().reset(source.getServer(), type);
        SiegeManager.get().persist();
        reply(source, Component.translatable("nightsiege.dungeon.reset_done", count));
        return 1;
    }

    private static int perkSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        PerkKind kind = PerkKind.byId(StringArgumentType.getString(ctx, "perk"));
        if (kind == null) {
            fail(source, Component.translatable("nightsiege.cmd.perk_unknown"));
            return 0;
        }
        int stage = IntegerArgumentType.getInteger(ctx, "stufe");
        SiegeSave save = SiegeManager.get().save();
        if (save.perkFragments == null) {
            save.perkFragments = new java.util.HashMap<>();
        }
        save.perkFragments.put(kind.id(), PerkKind.fragmentsFor(stage));
        SiegeManager.get().persist();
        reply(source, Component.translatable("nightsiege.cmd.perk_set", PerkService.perkName(kind), PerkService.roman(stage)));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager.get().reload();
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
