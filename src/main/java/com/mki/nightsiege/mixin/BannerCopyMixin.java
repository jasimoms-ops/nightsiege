package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.ClassBanners;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BannerDuplicateRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Class banners cannot be copied at the crafting table (update 6, Simon 4 November 2026, 23:15:
 * "Kopieren gesperrt, damit nur Spieler der Klasse ihr Banner haben"). The loom cannot add their
 * patterns either: they have no pattern item.
 */
@Mixin(BannerDuplicateRecipe.class)
public abstract class BannerCopyMixin {

    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), cancellable = true)
    private void nightsiege$noClassBannerCopy(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (ClassBanners.isClassBanner(stack)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
