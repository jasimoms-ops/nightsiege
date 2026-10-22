package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.SiegeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A defender who dies in a siege drops nothing: monsters would put on the armour
 * and walk off with it. The inventory comes back on respawn (see SiegeManager).
 */
@Mixin(Player.class)
public abstract class PlayerKeepInventoryMixin {

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void nightsiege$keepInSiege(ServerLevel level, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && SiegeManager.get().keepsInventory(player)) {
            ci.cancel();
        }
    }
}
