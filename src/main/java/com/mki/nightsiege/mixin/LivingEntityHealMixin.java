package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.Wounds;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Klaffende Wunden: every heal of a wounded player is cut (see {@link Wounds}). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float nightsiege$wounds(float amount) {
        return Wounds.heal((LivingEntity) (Object) this, amount);
    }
}
