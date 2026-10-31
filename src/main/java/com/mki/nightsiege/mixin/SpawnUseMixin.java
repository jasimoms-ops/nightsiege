package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mki.nightsiege.game.SpawnGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A right click on a block under the server's spawn protection (31.10., survival safety): Night
 * Siege's own placements and uses at the base go through for signed-up players (SpawnGuard).
 * 26.2 asks the spawn protection twice here: directly, then in ServerLevel.mayInteract.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SpawnUseMixin {

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;isUnderSpawnProtection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean nightsiege$siegeUseAtSpawn(MinecraftServer server, ServerLevel level, BlockPos pos, Player player,
                                               Operation<Boolean> original,
                                               @Local(argsOnly = true) ServerboundUseItemOnPacket packet) {
        return original.call(server, level, pos, player)
                && !SpawnGuard.useAllowed(player, level, pos, packet.getHand());
    }

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean nightsiege$siegeMayUse(ServerLevel level, Entity entity, BlockPos pos, Operation<Boolean> original,
                                           @Local(argsOnly = true) ServerboundUseItemOnPacket packet) {
        if (original.call(level, entity, pos)) {
            return true;
        }
        return entity instanceof Player player && level.getWorldBorder().isWithinBounds(pos)
                && SpawnGuard.useAllowed(player, level, pos, packet.getHand());
    }
}
