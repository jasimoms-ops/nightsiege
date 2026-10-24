package com.mki.nightsiege.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the homing arrows see whether an arrow is already stuck. */
@Mixin(AbstractArrow.class)
public interface ArrowAccessor {

    @Invoker("isInGround")
    boolean nightsiege$isInGround();
}
