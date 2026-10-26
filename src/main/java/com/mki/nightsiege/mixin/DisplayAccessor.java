package com.mki.nightsiege.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The shape of a display entity (the boss fight's soul crystals and egg sacs, the manned
 * towers' rigs, the drone) and how smoothly it moves between updates.
 */
@Mixin(Display.class)
public interface DisplayAccessor {

    @Invoker("setTransformation")
    void nightsiege$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void nightsiege$setTransformationInterpolationDuration(int ticks);

    @Invoker("setTransformationInterpolationDelay")
    void nightsiege$setTransformationInterpolationDelay(int ticks);

    @Invoker("setPosRotInterpolationDuration")
    void nightsiege$setPosRotInterpolationDuration(int ticks);
}
