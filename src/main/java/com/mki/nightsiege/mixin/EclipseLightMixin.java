package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.EventAtmosphere;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Survival safety 2 (Simon 31.10.): the eclipse no longer darkens the server's whole dimension (that
 * brought monsters by day everywhere and switched daylight sensors off). Monsters in the defended
 * area around the core still see its dark: spiders stay angry, endermen stay, the undead do not burn.
 */
@Mixin(Entity.class)
public abstract class EclipseLightMixin {

    @Inject(method = "getLightLevelDependentMagicValue", at = @At("RETURN"), cancellable = true)
    private void nightsiege$eclipseDark(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Enemy) || self.level().isClientSide()) {
            return;
        }
        float light = cir.getReturnValueF();
        float seen = EventAtmosphere.monsterLight(self.level(), self.getX(), self.getZ(), light);
        if (seen != light) {
            cir.setReturnValue(seen);
        }
    }
}
