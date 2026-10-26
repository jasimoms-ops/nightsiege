package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The engineer's remote: right click on an own trap overcharges it, on a
 * monster marks it; the X key (second order) sets traps off, fires the towers'
 * salvo or big throw, reloads, and aims a mortar at the block looked at. The bar
 * on the item is its energy.
 */
public class RemoteItem extends Item {

    /** Set by the siege manager: what to do with a use, pointing at a block or (null) at whatever is in sight. */
    public interface Handler {
        void use(ServerPlayer player, BlockPos clicked);
    }

    public static Handler handler = (player, clicked) -> {
    };

    public RemoteItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            press(server, player.getItemInHand(hand), null);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide() && context.getPlayer() instanceof ServerPlayer server) {
            press(server, context.getItemInHand(), context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    private static void press(ServerPlayer player, ItemStack stack, BlockPos clicked) {
        if (player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        player.getCooldowns().addCooldown(stack, 6);
        handler.use(player, clicked);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x4FC3F7;
    }
}
