package com.mki.nightsiege.game;

import com.mki.nightsiege.mixin.ItemDisplayAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * The season trophy "Krone der Belagerung" (design update, Simon 29 October 2026; the season finale
 * itself comes with the content update): the item every defender gets, and the crown that stands on
 * the core from then on (an item display with the 3D model "crown on a cushion").
 */
public final class SiegeCrown {

    public static final String TAG = "nightsiege_crown";

    private SiegeCrown() {
    }

    /** The trophy item: season, date and the names of the winners in its lore. */
    public static ItemStack trophy(int season, String date, List<String> names) {
        // On an echo shard like the nemesis trophy: not placeable, no use of its own.
        ItemStack stack = new ItemStack(Items.ECHO_SHARD);
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("nightsiege", "trophy/siege_crown"));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("nightsiege.crown.name").withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.RARITY, Rarity.EPIC);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("nightsiege.crown.season", season, date).withStyle(ChatFormatting.GRAY));
        lore.add(Component.translatable("nightsiege.crown.names", String.join(", ", names)).withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG, season));
        return stack;
    }

    /** The crown on top of the core (a display that stays; tagged so it can be found again). */
    public static Display.ItemDisplay showOnCore(ServerLevel level, BlockPos core) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        ItemStack model = new ItemStack(Items.ECHO_SHARD);
        model.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("nightsiege", "trophy/siege_crown_3d"));
        ((ItemDisplayAccessor) display).nightsiege$setItemStack(model);
        display.setPos(core.getX() + 0.5, core.getY() + 1.25, core.getZ() + 0.5);
        display.addTag(TAG);
        level.addFreshEntity(display);
        return display;
    }
}
