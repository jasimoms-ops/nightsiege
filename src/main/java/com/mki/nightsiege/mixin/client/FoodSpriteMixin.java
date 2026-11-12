package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.KitHud;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Z3b (Simon 12.11. 20:11: "die Hungerbalken bitte auch Vanilla, aber im eigenen Design"): with the kit, vanilla's
 * hunger row keeps its form and behaviour (10 symbols, halves, green with the hunger effect, the shaking) and only
 * takes the mod's symbols.
 */
@Mixin(Hud.class)
public abstract class FoodSpriteMixin {

    @ModifyArg(method = "extractFood", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
            index = 1)
    private Identifier nightsiege$kitFood(Identifier sprite) {
        return KitHud.foodSprite(sprite);
    }
}
