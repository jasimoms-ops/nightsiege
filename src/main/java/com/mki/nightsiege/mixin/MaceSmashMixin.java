package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.KitService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The tanks' kit mace (Simon 05.11.) has no smash attack: a hit after a fall deals no extra damage, throws
 * nothing around and does not spare the fall damage, else wind charges and jumps off the wall would make it
 * far too strong. Every part of the vanilla smash asks this one method (the damage bonus, the mace damage
 * type, the blast and the reset of the fall), so a kit mace hits like any weapon. Other maces stay vanilla.
 */
@Mixin(MaceItem.class)
public abstract class MaceSmashMixin {

    @Inject(method = "canSmashAttack", at = @At("HEAD"), cancellable = true)
    private static void nightsiege$noKitSmash(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity != null && KitService.isKitMace(entity.getMainHandItem())) {
            cir.setReturnValue(false);
        }
    }
}
