package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mki.nightsiege.game.GuardianEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets the guardian's skills scale a hit before it lands: the bastion, the
 * anchor, ward signs and the sanctuary take some of it away.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

    @WrapMethod(method = "hurtServer")
    private boolean nightsiege$scaleHit(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
        float scaled = GuardianEffects.scale((LivingEntity) (Object) this, level, source, damage);
        return original.call(level, source, scaled);
    }
}
