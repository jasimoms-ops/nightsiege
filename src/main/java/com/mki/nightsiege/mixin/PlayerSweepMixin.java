package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.Combo;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The blademaster's combo fights like Minecraft's old combat: no sweep attack. */
@Mixin(Player.class)
public abstract class PlayerSweepMixin {

    @Inject(method = "isSweepAttack", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noSweepInCombo(boolean a, boolean b, boolean c, CallbackInfoReturnable<Boolean> cir) {
        if (Combo.active((Player) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
