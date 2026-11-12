package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.Combo;
import com.mki.nightsiege.game.WarriorSpirit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The blademaster's combo fights like Minecraft's old combat: no sweep attack. And every swing tells
 * Kampfgeist how charged it was (Simon 05.11.: a click without waiting fills nothing).
 */
@Mixin(Player.class)
public abstract class PlayerSweepMixin {

    @Inject(method = "isSweepAttack", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noSweepInCombo(boolean a, boolean b, boolean c, CallbackInfoReturnable<Boolean> cir) {
        if (Combo.active((Player) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void nightsiege$noteSwing(Entity target, CallbackInfo ci) {
        WarriorSpirit.noteSwing((Player) (Object) this);
        com.mki.nightsiege.game.AbilityFx.meleeStart((Player) (Object) this);   // Z4: melee hits do not flash as ability hits
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void nightsiege$swingDone(Entity target, CallbackInfo ci) {
        com.mki.nightsiege.game.AbilityFx.meleeEnd((Player) (Object) this);
    }
}
