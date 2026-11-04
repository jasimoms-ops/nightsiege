package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.EventAtmosphere;
import com.mki.nightsiege.game.SiegeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Simon 04.11. 23:54 ("Gewitter in der ganzen Welt … können keine blöcke entzünden"): the thunder
 * night's storm is world-wide weather, so ordinary lightning strikes everywhere while it lasts. Those
 * strikes set no block on fire; they still flash, thunder and hit what stands there.
 */
@Mixin(LightningBolt.class)
public abstract class LightningFireMixin {

    @Inject(method = "spawnFire", at = @At("HEAD"), cancellable = true, require = 1)
    private void nightsiege$noFireInTheThunderNight(int extraIgnitions, CallbackInfo ci) {
        if (((LightningBolt) (Object) this).level() instanceof ServerLevel level
                && EventAtmosphere.THUNDER.equals(EventAtmosphere.serverEvent(SiegeManager.dimensionName(level)))) {
            ci.cancel();
        }
    }
}
