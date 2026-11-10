package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.CasterCurbs;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tor 9b: blindness and slowness from illager casters on players are cut (see {@link CasterCurbs}). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void nightsiege$casterCurb(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        MobEffectInstance cut = CasterCurbs.curb(self, effect, source);
        if (cut == effect) {
            return;
        }
        cir.setReturnValue(cut != null && CasterCurbs.apply(self, cut, source));
    }
}
