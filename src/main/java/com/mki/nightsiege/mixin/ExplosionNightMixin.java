package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.NightDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** What an explosion breaks during a night is written down and comes back after it (27 October 2026). */
@Mixin(ServerExplosion.class)
public abstract class ExplosionNightMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void nightsiege$rememberBlocks(List<BlockPos> blocks, CallbackInfo ci) {
        if (blocks == null || !NightDamage.recording(this.level)) {
            return;
        }
        for (BlockPos pos : blocks) {
            NightDamage.record(this.level, pos, this.level.getBlockState(pos));
        }
    }
}
