package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mki.nightsiege.game.NightDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Survival safety (Simon 31.10.): who changes a block in a night. While a siege attacker (a monster
 * of the night, a boss, a machine, what one of them shot) takes its tick, what it breaks is the
 * night's and comes back after it; what anything else does in its tick (players' TNT, falling sand,
 * animals on the fields) stays as it is.
 */
@Mixin(ServerLevel.class)
public abstract class NightCauseMixin {

    @WrapMethod(method = "tickNonPassenger")
    private void nightsiege$whoActs(Entity entity, Operation<Void> original) {
        if (!NightDamage.recording((ServerLevel) (Object) this)) {
            original.call(entity);
            return;
        }
        int before = NightDamage.actorBegin(entity);
        try {
            original.call(entity);
        } finally {
            NightDamage.causeEnd(before);
        }
    }

    @WrapMethod(method = "tickPassenger")
    private void nightsiege$whoRides(Entity vehicle, Entity passenger, Operation<Void> original) {
        if (!NightDamage.recording((ServerLevel) (Object) this)) {
            original.call(vehicle, passenger);
            return;
        }
        int before = NightDamage.actorBegin(passenger);
        try {
            original.call(vehicle, passenger);
        } finally {
            NightDamage.causeEnd(before);
        }
    }
}
