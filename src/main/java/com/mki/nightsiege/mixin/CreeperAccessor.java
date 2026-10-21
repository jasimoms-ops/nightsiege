package com.mki.nightsiege.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a lightning strike of the thunder night charge a creeper without setting it on fire. */
@Mixin(Creeper.class)
public interface CreeperAccessor {

    @Accessor("DATA_IS_POWERED")
    static EntityDataAccessor<Boolean> nightsiege$powered() {
        throw new AssertionError();
    }
}
