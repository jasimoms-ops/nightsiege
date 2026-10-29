package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.KitGuard;
import com.mki.nightsiege.game.KitService;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kit items never go into a container (Simon 29.10.: kit items put away were lost). {@code KitSlotMixin}
 * stops plain slots; many slots have their own rule (shulker box, furnace fuel, brewing stand ...), so
 * here every click that would put a kit item into a slot outside the player's inventory is refused,
 * a shift-click finds no room outside it, bundles take no kit items, and whatever still got in goes
 * back to the player after the click. Inside the own inventory everything moves as before.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class KitMenuMixin {

    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void nightsiege$keepKitOut(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (KitGuard.refusesClick((AbstractContainerMenu) (Object) this, slotId, button, input, player)) {
            ci.cancel();
        }
    }

    @Inject(method = "clicked", at = @At("TAIL"))
    private void nightsiege$sweepKit(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        KitGuard.sweepMenu((AbstractContainerMenu) (Object) this, player);
    }

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noKitShiftClick(ItemStack stack, int start, int end, boolean reverse, CallbackInfoReturnable<Boolean> cir) {
        if (KitService.isKit(stack) && KitGuard.outsidePlayer(slots, start, end)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tryItemClickBehaviourOverride", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noKitInBundle(Player player, ClickAction action, Slot slot, ItemStack clicked, ItemStack carried,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (KitGuard.refusesStacking(clicked, carried)) {
            cir.setReturnValue(false);
        }
    }
}
