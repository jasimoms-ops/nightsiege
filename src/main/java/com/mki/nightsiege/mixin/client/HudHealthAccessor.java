package com.mki.nightsiege.mixin.client;

import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Z3: vanilla counts its heart rows with the health it still shows after a loss (displayHealth), not only the max. */
@Mixin(Hud.class)
public interface HudHealthAccessor {

    @Accessor("displayHealth")
    int nightsiege$displayHealth();
}
