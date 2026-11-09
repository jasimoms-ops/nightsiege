package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mki.nightsiege.core.WarriorSpiritMath;
import com.mki.nightsiege.game.Combo;
import com.mki.nightsiege.game.Companions;
import com.mki.nightsiege.game.GuardianEffects;
import com.mki.nightsiege.game.StealPool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Lets the guardian's skills scale a hit before it lands: the bastion, the
 * anchor, ward signs and the sanctuary take some of it away. Since 05.11. it also
 * measures the life a player really loses (for the life-steal pool and the statistics).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

    @Shadow
    protected float lastHurt;

    @WrapMethod(method = "hurtServer")
    private boolean nightsiege$scaleHit(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        float scaled = GuardianEffects.scale(self, level, source, damage);
        boolean began = StealPool.begin(self);
        String diag = Companions.diagHurtBefore(self, source, damage, scaled, lastHurt);   // GOLEMDIAG 3 (bench only)
        try {
            boolean hit = original.call(level, source, scaled);
            if (diag != null) {
                Companions.diagHurtAfter(self, source, diag, hit, lastHurt);
            }
            return hit;
        } finally {
            StealPool.end(self, source, began);
        }
    }

    /** Tor 4.10: the blademaster's sword hits in the Kombo push only w_combo.knock (0.3) as far. */
    @WrapMethod(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V")
    private void nightsiege$softComboKnock(double strength, double x, double z, DamageSource source, float f, boolean b,
                                           Operation<Void> original) {
        if (source != null && source.getEntity() instanceof Player p && source.getDirectEntity() == p && Combo.active(p)) {
            strength *= WarriorSpiritMath.comboKnockback();
        }
        original.call(strength, x, z, source, f, b);
    }
}
