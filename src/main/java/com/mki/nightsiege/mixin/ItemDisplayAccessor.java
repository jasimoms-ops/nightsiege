package com.mki.nightsiege.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The item an item display shows (the season crown on the core). */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {

    @Invoker("setItemStack")
    void nightsiege$setItemStack(ItemStack stack);
}
