package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.PlayerBuilt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player set a block down: its chunk is written down as built on (update 30.10.), so the war
 * camp and the dungeons never rise in a player's building.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemBuiltMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void nightsiege$markBuilt(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = cir.getReturnValue();
        if (result != null && result.consumesAction() && context.getPlayer() instanceof ServerPlayer
                && context.getLevel() instanceof ServerLevel level) {
            PlayerBuilt.mark(level, context.getClickedPos());
        }
    }
}
