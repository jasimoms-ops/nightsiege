package com.mki.nightsiege.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Creative tool: opens the list of every dungeon kind to travel to, test or reset. */
public class DungeonCompassItem extends Item {

    public DungeonCompassItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            SiegeManager.get().dungeons().openTool(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
