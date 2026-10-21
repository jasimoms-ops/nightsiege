package com.mki.nightsiege.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A blank dungeon map. Used, it looks for the nearest dungeon in unexplored land
 * and turns into a real map with a red cross on it.
 */
public class DungeonMapItem extends Item {

    public DungeonMapItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            SiegeManager.get().dungeons().useMap(serverLevel, serverPlayer, hand);
        }
        return InteractionResult.SUCCESS;
    }
}
