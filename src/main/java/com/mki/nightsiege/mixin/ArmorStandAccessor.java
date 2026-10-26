package com.mki.nightsiege.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The seat of a manned tower: a small marker armour stand (both setters are private). */
@Mixin(ArmorStand.class)
public interface ArmorStandAccessor {

    @Invoker("setSmall")
    void nightsiege$setSmall(boolean small);

    @Invoker("setMarker")
    void nightsiege$setMarker(boolean marker);
}
