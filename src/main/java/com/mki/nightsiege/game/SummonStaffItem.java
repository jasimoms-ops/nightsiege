package com.mki.nightsiege.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The summoner's staff: right click calls a companion (a wolf, or a golem for the
 * golemsmith), sneaking and right clicking at a monster sends every companion at it.
 * The pause until the next call shows on the item.
 */
public class SummonStaffItem extends Item {

    /** Set by the siege manager. */
    public interface Handler {
        void use(ServerPlayer player, ItemStack stack);
    }

    public static Handler handler = (player, stack) -> {
    };

    public SummonStaffItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            ItemStack stack = player.getItemInHand(hand);
            // Commands (sneaking) work even while the call is cooling down.
            if (!player.isShiftKeyDown() && player.getCooldowns().isOnCooldown(stack)) {
                return InteractionResult.FAIL;
            }
            handler.use(server, stack);
        }
        return InteractionResult.SUCCESS;
    }
}
