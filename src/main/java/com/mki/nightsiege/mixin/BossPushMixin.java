package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.BossService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bosses and dungeon champions take no knockback (Simon 05.11.: "Bosse dürfen kein Knockback
 * bekommen"). Their knockback resistance stops the vanilla knockback; this stops every other push:
 * the shield bash, the bulwark, traps, arrow rain, explosions and being shoved by other monsters.
 * The brains still move them with their own setDeltaMovement (leaps, charges).
 */
@Mixin(Entity.class)
public abstract class BossPushMixin {

    @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
    private void nightsiege$bossStands(double x, double y, double z, CallbackInfo ci) {
        if (BossService.immovable((Entity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void nightsiege$bossStandsVec(Vec3 by, CallbackInfo ci) {
        if (BossService.immovable((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
