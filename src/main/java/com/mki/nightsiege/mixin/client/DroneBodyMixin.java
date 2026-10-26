package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.DroneClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The body of a player flying as a drone is not drawn, nor what they hold or wear: the drone is all. */
@Mixin(EntityRenderDispatcher.class)
public abstract class DroneBodyMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void nightsiege$hideDroneBody(Entity entity, Frustum frustum, double x, double y, double z,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && DroneClient.isDrone(player.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
