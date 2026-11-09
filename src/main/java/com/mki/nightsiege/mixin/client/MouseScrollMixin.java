package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.GainClient;
import com.mki.nightsiege.client.NightKeys;
import com.mki.nightsiege.core.KitArrange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With the arrow or potion key held, the mouse wheel goes through the choices and not through the
 * hotbar (Simon 30.10.: "pfeile und die tränke auf eine taste … dann kann man scrollen um zu ändern").
 */
@Mixin(MouseHandler.class)
public abstract class MouseScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nightsiege$scrollChoice(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (GainClient.onScroll(yOffset)) {
            ci.cancel();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        nightsiege$before = mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot();
    }

    @Unique
    private int nightsiege$before = -1;

    /** Tor 3.2b: at night the wheel only goes through places 4-9 (1-3 are the gain keys). */
    @Inject(method = "onScroll", at = @At("TAIL"))
    private void nightsiege$skipGainPlaces(long window, double xOffset, double yOffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !NightKeys.owned() || nightsiege$before < 0) {
            return;
        }
        int now = mc.player.getInventory().getSelectedSlot();
        int held = KitArrange.nightHeld(now, nightsiege$before);
        if (held != now) {
            mc.player.getInventory().setSelectedSlot(held);
        }
    }
}
