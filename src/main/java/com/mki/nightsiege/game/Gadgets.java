package com.mki.nightsiege.game;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Optional;

/**
 * The items crate gains put into a night kit: a vanilla item with a name, a line of
 * help and a tag saying which gain it belongs to. The special arrows are tipped
 * arrows in their own colour.
 */
public final class Gadgets {

    /** Custom data key: which gain an item belongs to. */
    public static final String TAG = "nightsiege_gadget";
    /** Custom data key on arrows: light, climb or storm. */
    public static final String ARROW_TAG = "nightsiege_arrow_kind";
    /** Custom data flags on kit gear. */
    public static final String SCOPE = "nightsiege_scope";
    public static final String DOUBLE_JUMP = "nightsiege_doublejump";

    private Gadgets() {
    }

    /** The gain an item belongs to, or "". */
    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(TAG, "");
    }

    /** The kind of a special arrow (light, climb, storm), or "". */
    public static String arrowKind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(ARROW_TAG, "");
    }

    public static boolean flag(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(key, false);
    }

    public static void setFlag(ItemStack stack, String key) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(key, true));
    }

    /** A gadget: the base item, named after the gain, with a help line. */
    public static ItemStack make(Item base, String id, int count) {
        ItemStack stack = new ItemStack(base);
        if (count > 1 && stack.getMaxStackSize() < count) {
            // Throwing axes and spears stack: they never wear out in the kit anyway.
            stack.remove(DataComponents.MAX_DAMAGE);
            stack.remove(DataComponents.DAMAGE);
            stack.set(DataComponents.MAX_STACK_SIZE, Math.min(64, Math.max(count, 16)));
        }
        stack.setCount(Math.max(1, count));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG, id));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("nightsiege.gadget." + id));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("nightsiege.gadget." + id + ".hint").withStyle(ChatFormatting.GRAY))));
        return stack;
    }

    /** A stack of special arrows: tipped arrows in their own colour, without effects. */
    public static ItemStack arrows(String kind, int color, int count) {
        ItemStack stack = new ItemStack(Items.TIPPED_ARROW, count);
        stack.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.of(color), List.of(), Optional.empty()));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, (CompoundTag tag) -> tag.putString(ARROW_TAG, kind));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("nightsiege.arrow." + kind));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("nightsiege.arrow." + kind + ".hint").withStyle(ChatFormatting.GRAY))));
        return stack;
    }
}
