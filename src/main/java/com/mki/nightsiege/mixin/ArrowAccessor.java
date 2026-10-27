package com.mki.nightsiege.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the homing arrows see whether an arrow is already stuck; the base damage for killing hits. */
@Mixin(AbstractArrow.class)
public interface ArrowAccessor {

    @Invoker("isInGround")
    boolean nightsiege$isInGround();

    /** The arrow's base damage (26.2 has no getter for it any more). */
    @Accessor("baseDamage")
    double nightsiege$baseDamage();
}
