package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Registers the siege core block and its item. */
public final class ModContent {

    public static final String CORE_NAME = "siege_core";

    public static Block SIEGE_CORE;
    public static Item SIEGE_CORE_ITEM;
    public static CreativeModeTab TAB;

    private ModContent() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NightSiegeMod.MOD_ID, path);
    }

    public static void register() {
        Identifier id = id(CORE_NAME);

        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        SIEGE_CORE = new SiegeCoreBlock(BlockBehaviour.Properties.of()
                .setId(blockKey)
                // Tough enough that it is a deliberate build, not a throwaway.
                .strength(25.0f, 1200.0f)
                .sound(SoundType.METAL));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, SIEGE_CORE);

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        SIEGE_CORE_ITEM = new BlockItem(SIEGE_CORE, new Item.Properties()
                .setId(itemKey)
                .useBlockDescriptionPrefix());
        Registry.register(BuiltInRegistries.ITEM, itemKey, SIEGE_CORE_ITEM);

        // Everything the mod adds lives on its own creative page.
        ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("general"));
        TAB = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.nightsiege.general"))
                .icon(() -> new ItemStack(SIEGE_CORE_ITEM))
                .displayItems((params, output) -> {
                    output.accept(SIEGE_CORE_ITEM);
                    output.accept(GuideBook.create());
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, TAB);
    }

    public static boolean isCore(net.minecraft.world.level.block.state.BlockState state) {
        return state != null && SIEGE_CORE != null && state.getBlock() == SIEGE_CORE;
    }
}
