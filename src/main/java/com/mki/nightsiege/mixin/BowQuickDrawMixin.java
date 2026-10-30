package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mki.nightsiege.game.QuickDraw;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** A quick-shot archer's tap shoots, however short (update 30.10., see {@link QuickDraw}). */
@Mixin(BowItem.class)
public abstract class BowQuickDrawMixin {

    @WrapMethod(method = "releaseUsing")
    private boolean nightsiege$quickTap(ItemStack stack, Level level, LivingEntity entity, int timeLeft,
                                        Operation<Boolean> original) {
        if (!level.isClientSide() && QuickDraw.applies(entity)) {
            int duration = ((BowItem) (Object) this).getUseDuration(stack, entity);
            if (duration - timeLeft < QuickDraw.MIN_TICKS) {
                timeLeft = duration - QuickDraw.MIN_TICKS;
                QuickDraw.saved();
            }
        }
        return original.call(stack, level, entity, timeLeft);
    }
}
