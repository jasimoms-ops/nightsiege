package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.EventAtmosphere;
import com.mki.nightsiege.game.SiegeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During a thunder night only the mod's own lightning strikes, and only
 * monsters: natural bolts could set the base on fire. During a siege no
 * phantoms or patrols come.
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

    /** No phantoms, patrols and the like while natural monster spawns are held back for a siege. */
    @Inject(method = "tickCustomSpawners", at = @At("HEAD"), cancellable = true, require = 0)
    private void nightsiege$noCustomSpawnsInSiege(boolean spawnEnemies, CallbackInfo ci) {
        if (SiegeManager.holdsCustomSpawns((ServerLevel) (Object) this)) {
            ci.cancel();
        }
    }

    /**
     * Spawn protection must not lock players without operator rights out of the core
     * (playtest 25 October 2026: the core stood near the world spawn, and only operators
     * could open it). Building and breaking there stays protected.
     */
    @Inject(method = "mayInteract", at = @At("RETURN"), cancellable = true, require = 0)
    private void nightsiege$coreBeatsSpawnProtection(net.minecraft.world.entity.Entity entity,
                                                     net.minecraft.core.BlockPos pos,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && entity instanceof net.minecraft.server.level.ServerPlayer
                && com.mki.nightsiege.game.ModContent.SIEGE_CORE != null
                && ((ServerLevel) (Object) this).getBlockState(pos).is(com.mki.nightsiege.game.ModContent.SIEGE_CORE)
                && ((ServerLevel) (Object) this).getWorldBorder().isWithinBounds(pos)) {
            cir.setReturnValue(true);
        }
    }
}
