package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.SiegeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No dropping items during the night (Simon, 25 October 2026): a defender who throws an
 * item (Q key, out of the inventory screen) gets it back into the inventory. Dying still
 * works as before, and an item that no longer fits anywhere falls as usual.
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerDropMixin {

    @Unique
    private static boolean nightsiege$returning;

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void nightsiege$noDropAtNight(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (nightsiege$returning || stack.isEmpty() || player.isDeadOrDying() || SiegeManager.get() == null
                || !SiegeManager.get().isDefending(player)) {
            return;
        }
        nightsiege$returning = true;
        try {
            player.getInventory().placeItemBackInInventory(stack);
        } finally {
            nightsiege$returning = false;
        }
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("nightsiege.msg.no_drop_night")));
        cir.setReturnValue(null);
    }
}
