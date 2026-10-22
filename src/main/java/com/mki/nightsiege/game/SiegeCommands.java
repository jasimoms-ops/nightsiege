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
                .then(Commands.literal("cores").executes(SiegeCommands::cores))
                .then(Commands.literal("skills").executes(SiegeCommands::skills))
                .then(Commands.literal("skill")
                        .then(Commands.argument("node", StringArgumentType.word())
                                .executes(SiegeCommands::buySkill)))
                .then(Commands.literal("mobs").executes(SiegeCommands::mobs))
                .then(Commands.literal("start").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SiegeCommands::start))
                .then(Commands.literal("test").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> test(ctx, null))
                                .then(Commands.literal("normal").executes(ctx -> test(ctx, SiegeEvent.NONE)))
                                .then(Commands.literal("blutmond").executes(ctx -> test(ctx, SiegeEvent.BLOOD_MOON)))
                                .then(Commands.literal("champion").executes(ctx -> test(ctx, SiegeEvent.ELITE)))
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
                .then(Commands.literal("base").executes(SiegeCommands::baseInfo)
                        .then(Commands.literal("radius").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    reply(ctx.getSource(), Component.translatable("nightsiege.cmd.base_radius",
                                            SiegeManager.get().config().baseRadius));
                                    return 1;
                                })
                                .then(Commands.argument("bloecke", IntegerArgumentType.integer(16, 192))
                                        .executes(SiegeCommands::baseRadius))))
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
    private static int test(CommandContext<CommandSourceStack> ctx, SiegeEvent event) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager manager = SiegeManager.get();
        if (manager.isActive()) {
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
        if (!manager.startTest(server, player.level(), player.blockPosition(), level, event)) {
            fail(source, Component.translatable("nightsiege.cmd.no_target"));
            return 0;
        }
        reply(source, Component.translatable("nightsiege.cmd.test_started", manager.currentLevel()));
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

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager manager = SiegeManager.get();
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
        } else if (scan.gap != null) {
            reply(source, Component.translatable("nightsiege.cmd.base_gap",
                    scan.gap.getX() + " " + scan.gap.getY() + " " + scan.gap.getZ(), scan.width(), scan.depth(), radius));
            BaseDefense.highlight(player, scan.gap);
        } else {
            reply(source, Component.translatable("nightsiege.cmd.base_open", scan.width(), scan.depth(), radius));
        }
        return 1;
    }

    private static int baseRadius(CommandContext<CommandSourceStack> ctx) {
        int radius = IntegerArgumentType.getInteger(ctx, "bloecke");
        SiegeManager.get().setBaseRadius(radius);
        reply(ctx.getSource(), Component.translatable("nightsiege.cmd.base_radius_set", radius));
        return 1;
    }

    private static void reply(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static void fail(CommandSourceStack source, Component message) {
        source.sendFailure(message);
    }
}
