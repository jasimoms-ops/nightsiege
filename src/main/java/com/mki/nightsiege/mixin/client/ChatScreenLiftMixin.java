package com.mki.nightsiege.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mki.nightsiege.client.KitHud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Z3 (Chef 12.11.: the chat ends above the hearts, also when open): the open chat goes up as far as the closed one;
 * the hover and the clicks on its lines go up with it.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenLiftMixin {

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"))
    private void nightsiege$liftOpenChat(ChatComponent chat, GuiGraphicsExtractor gui, Font font, int ticks, int mouseX,
                                         int mouseY, ChatComponent.DisplayMode mode, boolean insertion, Operation<Void> original) {
        int up = KitHud.chatLift();
        if (up <= 0) {
            original.call(chat, gui, font, ticks, mouseX, mouseY, mode, insertion);
            return;
        }
        gui.pose().pushMatrix();
        gui.pose().translate(0, -up);
        original.call(chat, gui, font, ticks, mouseX, mouseY + up, mode, insertion);
        gui.pose().popMatrix();
    }

    @ModifyArg(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;captureClickableText(Lnet/minecraft/client/gui/ActiveTextCollector;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"),
            index = 1)
    private int nightsiege$liftClicks(int height) {
        return height - KitHud.chatLift();
    }
}
