package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.DroneClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A drone steers its own way (DroneClient): Minecraft's walking and flying keys do nothing then. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void nightsiege$droneSteers(CallbackInfo ci) {
        if (DroneClient.localDrone()) {
            ClientInput self = (ClientInput) (Object) this;
            self.keyPresses = Input.EMPTY;
            ((ClientInputAccessor) self).nightsiege$setMoveVector(Vec2.ZERO);
        }
    }
}
