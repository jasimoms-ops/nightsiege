package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mki.nightsiege.game.NightDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Survival safety (Simon 31.10.): the fire the night's attackers lit burns the base, and what it burns
 * comes back after the night – also where it spread. A fire somebody else lit (lava, a player) is not
 * the night's.
 */
@Mixin(FireBlock.class)
public abstract class FireNightMixin {

    @WrapMethod(method = "tick")
    private void nightsiege$nightFire(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                      Operation<Void> original) {
        if (!NightDamage.nightFire(level, pos)) {
            original.call(state, level, pos, random);
            return;
        }
        int before = NightDamage.fireBegin();
        try {
            original.call(state, level, pos, random);
        } finally {
            NightDamage.causeEnd(before);
        }
    }
}
