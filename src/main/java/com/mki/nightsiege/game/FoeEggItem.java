package com.mki.nightsiege.game;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A spawn egg for one of the new attackers of update 1 (27 October 2026), in creative only,
 * like the eggs of the siege machines. In a running (test) night the monster joins it and
 * does its job; outside a night it only shows how it looks.
 */
public class FoeEggItem extends Item {

    private final String entryId;

    public FoeEggItem(String entryId, Item.Properties properties) {
        super(properties);
        this.entryId = entryId;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        if (!player.getAbilities().instabuild) {
            player.sendSystemMessage(Component.translatable("nightsiege.foe.egg_creative").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        SiegeManager m = SiegeManager.get();
        if (m == null) {
            return InteractionResult.FAIL;
        }
        BlockPos spot = context.getClickedPos().relative(context.getClickedFace());
        Mob mob = SiegeCommands.spawnFoeAt(m, level, entryId, spot);
        if (mob == null) {
            player.sendSystemMessage(Component.translatable("nightsiege.cmd.foe_failed").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        // What it does, and when (Simon 27.10.: "the tunneler does not dig, the grappler does not pull").
        Foes.Role role = Foes.roleOf(entryId);
        String hint = !m.isActive() ? "nightsiege.foe.egg_look"
                : role == Foes.Role.TUNNELER ? "nightsiege.foe.egg_tunneler"
                : role == Foes.Role.GRAPPLER ? "nightsiege.foe.egg_grappler" : "";
        if (!hint.isEmpty()) {
            player.sendSystemMessage(Component.translatable(hint).withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }
}
