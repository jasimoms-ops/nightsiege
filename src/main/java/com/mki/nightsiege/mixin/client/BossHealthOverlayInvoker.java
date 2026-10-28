package com.mki.nightsiege.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The vanilla bar, for every bar that is not the mod's. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayInvoker {

    @Invoker("extractBar")
    void nightsiege$extractBar(GuiGraphicsExtractor gui, int x, int y, BossEvent event);
}
