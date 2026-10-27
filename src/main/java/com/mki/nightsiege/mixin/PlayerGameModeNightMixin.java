package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mki.nightsiege.game.NightDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/** What a player breaks or builds in a night is theirs: the night's mending leaves it as it is. */
@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerGameModeNightMixin {

    @WrapMethod(method = "destroyBlock")
    private boolean nightsiege$playerBreaks(BlockPos pos, Operation<Boolean> original) {
        NightDamage.playerBegin();
        try {
            return original.call(pos);
        } finally {
            NightDamage.playerEnd();
        }
    }

    @WrapMethod(method = "useItemOn")
    private InteractionResult nightsiege$playerUsesOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                                      BlockHitResult hit, Operation<InteractionResult> original) {
        NightDamage.playerBegin();
        try {
            return original.call(player, level, stack, hand, hit);
        } finally {
            NightDamage.playerEnd();
        }
    }

    @WrapMethod(method = "useItem")
    private InteractionResult nightsiege$playerUses(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                                    Operation<InteractionResult> original) {
        NightDamage.playerBegin();
        try {
            return original.call(player, level, stack, hand);
        } finally {
            NightDamage.playerEnd();
        }
    }
}
