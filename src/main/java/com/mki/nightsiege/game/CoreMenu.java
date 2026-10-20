package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SkillState;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillBranch;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The core's screen, drawn as clickable chat lines.
 *
 * <p>A proper window would need client side code and its own network messages.
 * For a first version this keeps everything on the server, works in multiplayer
 * without a client mod, and is quick to extend.</p>
 */
public final class CoreMenu {

    private CoreMenu() {
    }

    public static void show(ServerPlayer player, SiegeManager manager) {
        SiegeConfig cfg = manager.config();
        SkillState skills = manager.save().skills();
        SkillTree tree = manager.skillTree();

        line(player, Component.translatable("nightsiege.menu.header").withStyle(ChatFormatting.GOLD));

        // --- status
        if (manager.isActive()) {
            line(player, Component.translatable("nightsiege.menu.active",
                    manager.currentLevel(), manager.secondsLeft()).withStyle(ChatFormatting.RED));
        } else {
            int here = manager.playersAtCore(player.level().getServer());
            boolean running = here >= cfg.requiredPlayers;
            line(player, Component.translatable("nightsiege.menu.countdown",
                    percent(manager.scheduler().progress()),
                    minutes(manager.scheduler().remainingTicks()))
                    .withStyle(running ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            line(player, Component.translatable(
                    running ? "nightsiege.menu.players_ok" : "nightsiege.menu.players_missing",
                    here, cfg.requiredPlayers, (int) cfg.coreRadius)
                    .withStyle(running ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        line(player, Component.translatable("nightsiege.menu.record",
                manager.currentLevel(), manager.save().siegesWon, manager.save().siegesLost)
                .withStyle(ChatFormatting.GRAY));

        // --- skills
        if (!cfg.skillsEnabled) {
            return;
        }
        line(player, Component.translatable("nightsiege.menu.skills", skills.points())
                .withStyle(ChatFormatting.AQUA));

        for (SkillBranch branch : SkillBranch.values()) {
            List<SkillNode> branchNodes = tree.ofBranch(branch);
            if (branchNodes.isEmpty()) {
                continue;
            }
            line(player, Component.translatable(branch.translationKey())
                    .withStyle(ChatFormatting.YELLOW));
            for (SkillNode node : branchNodes) {
                int level = skills.levelOf(node.id());
                String reason = tree.blockedReason(node.id(), skills);

                MutableComponent row = Component.literal("  ")
                        .append(Component.translatable(node.translationKey())
                                .withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("  " + level + "/" + node.maxLevel() + " ")
                                .withStyle(ChatFormatting.DARK_GRAY));

                if (reason == null) {
                    row.append(button(Component.translatable("nightsiege.menu.buy", node.costFor(level)),
                            ChatFormatting.GREEN, "/nightsiege skill " + node.id()));
                } else {
                    row.append(Component.translatable("nightsiege.menu.blocked_" + reason,
                            node.costFor(level)).withStyle(ChatFormatting.DARK_GRAY));
                }
                if (!node.effect().isLive()) {
                    row.append(Component.translatable("nightsiege.menu.preview")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                line(player, row);
            }
        }
    }

    private static MutableComponent button(Component label, ChatFormatting color, String command) {
        return Component.literal("[").append(label).append(Component.literal("]"))
                .withStyle(style -> style
                        .withColor(color)
                        .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static void line(ServerPlayer player, Component component) {
        player.sendSystemMessage(component);
    }

    private static int percent(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0);
    }

    /** Remaining qualifying time, rounded to whole minutes of real time. */
    private static int minutes(long ticks) {
        return (int) Math.max(0L, ticks / 20L / 60L);
    }
}
