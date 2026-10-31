package com.mki.nightsiege.mixin.client;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The press counter of a key mapping, so a night ability key can count a press itself. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("clickCount")
    int nightsiege$clickCount();

    @Accessor("clickCount")
    void nightsiege$setClickCount(int count);

    /** Every mapping by its key, as KeyMapping.click and set go through them (31.10.: other mods' keys). */
    @Accessor("MAP")
    static java.util.Map<com.mojang.blaze3d.platform.InputConstants.Key, java.util.List<KeyMapping>> nightsiege$map() {
        throw new AssertionError();
    }
}
