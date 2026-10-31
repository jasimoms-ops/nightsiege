package com.mki.nightsiege.game;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The siege core. Placing it starts the countdown to the first siege; right
 * clicking it opens the status and the skill tree.
 */
public class SiegeCoreBlock extends Block {

    public SiegeCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * (Simon 31.10.: survival safety 2) Only operators set a core down, and only in the overworld. A
     * refused placement places nothing and keeps the item in the hand; the server says why. The client
     * runs the same check, so it does not show a core that is not there.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        String refusal = SiegeManager.coreRefusal(context.getPlayer(), context.getLevel());
        if (refusal != null) {
            if (context.getLevel() instanceof ServerLevel && context.getPlayer() instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable(refusal).withStyle(ChatFormatting.RED)));
                if (!(player instanceof net.fabricmc.fabric.api.entity.FakePlayer)) {
                    player.inventoryMenu.sendAllDataToRemote();   // the hand shows the core again
                }
            }
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            SiegeManager.get().onCorePlaced(serverLevel, pos, placer);
        }
    }
}
