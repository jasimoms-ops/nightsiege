package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mki.nightsiege.game.GuardianEffects;
import com.mki.nightsiege.game.StealPool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets the guardian's skills scale a hit before it lands: the bastion, the
 * anchor, ward signs and the sanctuary take some of it away. Since 05.11. it also
 * measures the life a player really loses (for the life-steal pool and the statistics).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

    @WrapMethod(method = "hurtServer")
    private boolean nightsiege$scaleHit(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        float scaled = GuardianEffects.scale(self, level, source, damage);
        boolean began = StealPool.begin(self);
        try {
            return original.call(level, source, scaled);
        } finally {
            StealPool.end(self, source, began);
        }
    }
}
