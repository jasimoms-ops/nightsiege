package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.KitHud;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Z3: the mount's hearts in the mod's design too while the kit is worn. */
@Mixin(Hud.class)
public abstract class VehicleHeartMixin {

    @ModifyArg(method = "extractVehicleHealth", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
            index = 1)
    private Identifier nightsiege$kitMountHeart(Identifier sprite) {
        return KitHud.mountHeartSprite(sprite);
    }
}
