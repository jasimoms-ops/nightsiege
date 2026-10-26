package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.MannedClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In a manned tower the attack button fires the tower (Simon, 26 October 2026): it must not
 * hit, mine or break anything in front of the player at the same time.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noAttackInTower(CallbackInfoReturnable<Boolean> cir) {
        if (MannedClient.seated()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noMiningInTower(boolean down, CallbackInfo ci) {
        if (MannedClient.seated()) {
            ci.cancel();
        }
    }
}
