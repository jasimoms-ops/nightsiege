package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.StatsRecorder;
import com.mki.nightsiege.game.Wounds;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Klaffende Wunden: every heal of a wounded player is cut (see {@link Wounds}). Since 05.11. the
 * statistics also see life given back without a heal (setHealth: the night totem, the second
 * chance, refills), so what a defender lost and got back adds up.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float nightsiege$wounds(float amount) {
        StatsRecorder.HEALING.get()[0]++;
        float healed = Wounds.heal((LivingEntity) (Object) this, amount);
        com.mki.nightsiege.game.MeleeSim.onHeal((LivingEntity) (Object) this, healed);
        StatsRecorder.onHeal((LivingEntity) (Object) this, healed);
        return healed;
    }

    @Inject(method = "heal", at = @At("RETURN"))
    private void nightsiege$healDone(float amount, CallbackInfo ci) {
        int[] depth = StatsRecorder.HEALING.get();
        depth[0] = Math.max(0, depth[0] - 1);
    }

    @Inject(method = "setHealth", at = @At("HEAD"))
    private void nightsiege$directHeal(float health, CallbackInfo ci) {
        if (StatsRecorder.HEALING.get()[0] > 0) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.tickCount <= 0 || self.level() == null || self.level().isClientSide()) {
            return;
        }
        float now = self.getHealth();
        if (now <= 0f) {
            return;   // a defender who fell and becomes a drone is not healed
        }
        float to = Math.min(health, self.getMaxHealth());
        if (to > now) {
            StatsRecorder.onDirectHeal(self, to - now);
        }
    }
}
