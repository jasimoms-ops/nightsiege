package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mki.nightsiege.game.SpawnGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Breaking a block under the server's spawn protection (31.10., survival safety): signed-up players
 * may take down traps, towers and the night's blocks at the base (SpawnGuard); everything else
 * stays protected.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class SpawnBreakMixin {

    @WrapOperation(method = "handleBlockBreakAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;isUnderSpawnProtection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean nightsiege$siegeBreakAtSpawn(MinecraftServer server, ServerLevel level, BlockPos pos, Player player,
                                                 Operation<Boolean> original) {
        return original.call(server, level, pos, player) && !SpawnGuard.breakAllowed(player, level, pos);
    }

    @WrapOperation(method = "handleBlockBreakAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean nightsiege$siegeMayBreak(ServerLevel level, Entity entity, BlockPos pos, Operation<Boolean> original) {
        if (original.call(level, entity, pos)) {
            return true;
        }
        return entity instanceof Player player && level.getWorldBorder().isWithinBounds(pos)
                && SpawnGuard.breakAllowed(player, level, pos);
    }
}
