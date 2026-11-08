package com.mki.nightsiege.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The vanilla bar, for every bar that is not the mod's. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayInvoker {

    @Invoker("extractBar")
    void nightsiege$extractBar(GuiGraphicsExtractor gui, int x, int y, BossEvent event);

    /** The bars the client shows, in their order (for the compact boss bars, Tor 2.3). */
    @Accessor("events")
    java.util.Map<java.util.UUID, net.minecraft.client.gui.components.LerpingBossEvent> nightsiege$events();
}
