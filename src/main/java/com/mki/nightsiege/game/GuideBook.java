package com.mki.nightsiege.game;

import net.minecraft.world.item.ItemStack;

/** The handbook every player receives the first time they join. */
public final class GuideBook {

    private GuideBook() {
    }

    public static ItemStack create() {
        return new ItemStack(ModContent.GUIDE);
    }
}
