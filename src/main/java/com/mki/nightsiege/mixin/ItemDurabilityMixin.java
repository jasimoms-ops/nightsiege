package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.KitService;
import com.mki.nightsiege.game.SiegeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Nothing wears out in a siege night: kit items never, and whatever a defender of the
 * running night carries (armour, weapons, tools) neither, even when the kit could not
 * be handed out.
 */
@Mixin(ItemStack.class)
public abstract class ItemDurabilityMixin {

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void nightsiege$noWearInTheNight(int amount, ServerLevel level, ServerPlayer player, Consumer<Item> onBreak,
                                             CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (KitService.isKit(self)) {
            ci.cancel();
            return;
        }
        SiegeManager manager = SiegeManager.get();
        if (player != null && manager != null && manager.isDefending(player)) {
            ci.cancel();
        }
    }
}
