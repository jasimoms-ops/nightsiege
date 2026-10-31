package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mki.nightsiege.game.KitBlocks;
import com.mki.nightsiege.game.KitService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Survival safety 2 (Simon 31.10.): a block set down from a kit item (cobblestone, torch, ladder) is a
 * night block: mined it gives nothing, and it goes when the night (or the dungeon or camp fight) ends.
 * The kit mark is read before the placing, since the last item of a stack is gone afterwards.
 */
@Mixin(BlockItem.class)
public abstract class KitBlockMixin {

    @WrapMethod(method = "place")
    private InteractionResult nightsiege$kitBlock(BlockPlaceContext context, Operation<InteractionResult> original) {
        boolean server = context.getPlayer() instanceof ServerPlayer && context.getLevel() instanceof ServerLevel;
        boolean kit = server && KitService.isKit(context.getItemInHand());
        BlockPos pos = context.getClickedPos();
        InteractionResult result = original.call(context);
        if (server && result != null && result.consumesAction()) {
            ServerLevel level = (ServerLevel) context.getLevel();
            if (kit) {
                KitBlocks.placed((ServerPlayer) context.getPlayer(), level, pos);
            } else {
                KitBlocks.placedOwn(level, pos);
            }
        }
        return result;
    }
}
