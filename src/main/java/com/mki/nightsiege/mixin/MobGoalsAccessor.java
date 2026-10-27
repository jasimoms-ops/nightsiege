package com.mki.nightsiege.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the grappler (a pillager without a crossbow) learn to fight hand to hand (update 1, 27 October 2026). */
@Mixin(Mob.class)
public interface MobGoalsAccessor {

    @Accessor("goalSelector")
    GoalSelector nightsiege$goals();
}
