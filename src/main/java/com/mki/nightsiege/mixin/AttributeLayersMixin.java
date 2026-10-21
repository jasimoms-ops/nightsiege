package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.EventAtmosphere;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the event layer on top of every level's sky, light and fog. */
@Mixin(EnvironmentAttributeSystem.Builder.class)
public abstract class AttributeLayersMixin {

    @Inject(method = "addDefaultLayers", at = @At("RETURN"))
    private void nightsiege$addEventLayer(Level level, CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
        EventAtmosphere.addLayers((EnvironmentAttributeSystem.Builder) (Object) this, level);
    }
}
