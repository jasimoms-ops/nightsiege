package com.mki.nightsiege.game;

import com.mki.nightsiege.core.AnchorPicker;
import com.mki.nightsiege.core.MobEntry;
import com.mki.nightsiege.core.SiegeMath;
import com.mojang.brigadier.CommandDispatcher;
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
                .then(Commands.literal("bases").executes(SiegeCommands::bases))
                .then(Commands.literal("mobs").executes(SiegeCommands::mobs))
                .then(Commands.literal("start").executes(SiegeCommands::start))
                .then(Commands.literal("stop").executes(SiegeCommands::stop))
                .then(Commands.literal("reload").executes(SiegeCommands::reload)));
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

    private static int bases(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager manager = SiegeManager.get();
        var beds = manager.save().registeredBeds(manager.config().sleepsToRegisterBase);
        if (beds.isEmpty()) {
            reply(source, Component.translatable("nightsiege.cmd.no_bases"));
            return 1;
        }
        reply(source, Component.translatable("nightsiege.cmd.bases_header", beds.size()));
        for (BedRecord bed : beds) {
            reply(source, Component.literal(" - " + bed.dimension + " " + bed.x + " " + bed.y + " " + bed.z));
        }
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

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SiegeManager.get().reload();
        reply(source, Component.translatable("nightsiege.cmd.reloaded"));
        return 1;
    }

    private static void reply(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static void fail(CommandSourceStack source, Component message) {
        source.sendFailure(message);
    }
}
