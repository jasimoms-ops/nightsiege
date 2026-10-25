package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.SiegeManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Siege and dungeon monsters never take aim at players who are not part of
 * the night: those players notice nothing of the mod.
 */
@Mixin(Mob.class)
public abstract class MobTargetMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void nightsiege$onlyDefenders(LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (target != null && !self.level().isClientSide() && !SiegeManager.mayTarget(self, target)) {
            ci.cancel();
            return;
        }
        if (target != null && !self.level().isClientSide()) {
            LivingEntity other = SiegeManager.retarget(self, target);
            if (other != target) {
                ci.cancel();
                self.setTarget(other);   // archers are focused less: another defender close by instead
            }
        }
    }
}
