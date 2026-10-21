package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.EventAtmosphere;
import com.mki.nightsiege.game.SiegeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During a thunder night only the mod's own lightning strikes, and only
 * monsters: natural bolts could set the base on fire.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "tickThunder", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noNaturalLightning(LevelChunk chunk, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (EventAtmosphere.THUNDER.equals(EventAtmosphere.serverEvent(SiegeManager.dimensionName(self)))) {
            ci.cancel();
        }
    }
}
