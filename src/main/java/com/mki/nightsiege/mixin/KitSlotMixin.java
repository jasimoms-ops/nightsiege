package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.KitService;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Night kit items stay with the player: they cannot be put into chests,
 * barrels, ender chests or any other container.
 */
@Mixin(Slot.class)
public abstract class KitSlotMixin {

    @Shadow
    @Final
    public Container container;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void nightsiege$keepKitWithPlayer(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(container instanceof Inventory) && KitService.isKit(stack)) {
            cir.setReturnValue(false);
        }
    }
}
