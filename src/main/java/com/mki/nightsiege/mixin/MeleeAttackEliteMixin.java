package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.Elites;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Elite monsters with the affix "Schnell" (Simon 05.11.) strike again sooner. */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackEliteMixin {

    @Shadow
    @Final
    protected PathfinderMob mob;

    @Shadow
    private int ticksUntilNextAttack;

    @Inject(method = "resetAttackCooldown", at = @At("TAIL"))
    private void nightsiege$fastElite(CallbackInfo ci) {
        double cut = Elites.attackCut(mob);
        if (cut > 0) {
            ticksUntilNextAttack = Math.max(1, (int) Math.round(ticksUntilNextAttack * (1.0 - cut)));
        }
    }
}
