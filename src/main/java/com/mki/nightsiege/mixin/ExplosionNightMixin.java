package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mki.nightsiege.game.NightDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * What an attacker's blast breaks during a night is written down and comes back after it (27 October
 * 2026). Survival safety (Simon 31.10.): only a blast a siege attacker set off (a creeper, a ghast's
 * fireball, a boss) – not a player's TNT, crystal, bed or anchor; and what will come back drops nothing.
 */
@Mixin(ServerExplosion.class)
public abstract class ExplosionNightMixin {

    @Shadow
    @Final
    private ServerLevel level;

    /** Who set the blast off decides whether its damage (blocks, fire) is the night's. */
    @WrapMethod(method = "explode")
    private int nightsiege$byWhom(Operation<Integer> original) {
        if (!NightDamage.recording(this.level)) {
            return original.call();
        }
        ServerExplosion self = (ServerExplosion) (Object) this;
        int before = NightDamage.blastBegin(self.getDirectSourceEntity(), self.getIndirectSourceEntity());
        try {
            return original.call();
        } finally {
            NightDamage.causeEnd(before);
        }
    }

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void nightsiege$spareContainers(List<BlockPos> blocks, CallbackInfo ci) {
        if (blocks == null || !NightDamage.attacking(this.level)) {
            return;
        }
        try {
            // Chests and other containers outlast a night's blast (their contents would fall out).
            blocks.removeIf(pos -> NightDamage.spared(this.level, pos));
        } catch (UnsupportedOperationException ignored) {
            // a fixed list: the level's own guard keeps them anyway
        }
    }

    /** A block that comes back after the night (or a trap that stands again) drops nothing now. */
    @WrapOperation(method = "interactWithBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;onExplosionHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;Ljava/util/function/BiConsumer;)V"))
    private void nightsiege$noDoubleDrops(BlockState state, ServerLevel world, BlockPos pos, Explosion explosion,
                                          BiConsumer<ItemStack, BlockPos> drops, Operation<Void> original) {
        original.call(state, world, pos, explosion, NightDamage.keepsDrops(world, pos) ? NightDamage.NO_DROPS : drops);
    }
}
