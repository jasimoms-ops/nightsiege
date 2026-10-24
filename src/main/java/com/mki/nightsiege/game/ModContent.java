package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.TrapKind;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
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

import java.util.EnumMap;
import java.util.Map;

/** Registers the siege core, the engineer's traps, their items and the creative tab. */
public final class ModContent {

    public static final String CORE_NAME = "siege_core";

    public static Block SIEGE_CORE;
    public static Item SIEGE_CORE_ITEM;
    public static CreativeModeTab TAB;
    public static Item GUIDE;
    /** The engineer's remote. */
    public static Item REMOTE;
    public static final Map<TrapKind, Block> TRAP_BLOCKS = new EnumMap<>(TrapKind.class);
    public static final Map<TrapKind, Item> TRAP_ITEMS = new EnumMap<>(TrapKind.class);

    private ModContent() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NightSiegeMod.MOD_ID, path);
    }

    /** Big slow mist wisps for the fog, frost and rift nights; drawn by the client. */
    public static final net.minecraft.core.particles.SimpleParticleType FOG_WISP =
            net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true);

    public static void register() {
        Identifier id = id(CORE_NAME);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("fog_wisp"), FOG_WISP);

        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        SIEGE_CORE = new SiegeCoreBlock(BlockBehaviour.Properties.of()
                .setId(blockKey)
                // Tough enough that it is a deliberate build, not a throwaway.
                .strength(25.0f, 3600000.0f)
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                .sound(SoundType.METAL));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, SIEGE_CORE);

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        SIEGE_CORE_ITEM = new BlockItem(SIEGE_CORE, new Item.Properties()
                .setId(itemKey)
                .useBlockDescriptionPrefix());
        Registry.register(BuiltInRegistries.ITEM, itemKey, SIEGE_CORE_ITEM);

        for (TrapKind kind : TrapKind.values()) {
            registerTrap(kind);
        }

        ResourceKey<Item> guideKey = ResourceKey.create(Registries.ITEM, id("siege_guide"));
        GUIDE = new GuideItem(new Item.Properties().setId(guideKey).stacksTo(1)
                .rarity(net.minecraft.world.item.Rarity.UNCOMMON));
        Registry.register(BuiltInRegistries.ITEM, guideKey, GUIDE);

        ResourceKey<Item> remoteKey = ResourceKey.create(Registries.ITEM, id("remote_control"));
        REMOTE = new RemoteItem(new Item.Properties().setId(remoteKey).durability(100)
                .rarity(net.minecraft.world.item.Rarity.UNCOMMON));
        Registry.register(BuiltInRegistries.ITEM, remoteKey, REMOTE);

        InferniteContent.register();
        DungeonContent.register();
        CrateService.registerItem();

        // Everything the mod adds lives on its own creative page.
        ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("general"));
        TAB = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.nightsiege.general"))
                .icon(() -> new ItemStack(SIEGE_CORE_ITEM))
                .displayItems((params, output) -> {
                    output.accept(SIEGE_CORE_ITEM);
                    output.accept(GuideBook.create());
                    output.accept(REMOTE);
                    for (TrapKind kind : TrapKind.values()) {
                        output.accept(TRAP_ITEMS.get(kind));
                    }
                    for (Item item : InferniteContent.creativeItems()) {
                        output.accept(item);
                    }
                    for (Item item : DungeonContent.creativeItems()) {
                        output.accept(item);
                    }
                    for (com.mki.nightsiege.core.SkillClass c : com.mki.nightsiege.core.SkillClass.values()) {
                        if (!c.isShared()) {
                            output.accept(CrateService.crate(c, com.mki.nightsiege.core.CrateKind.NORMAL, 10));
                        }
                    }
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, TAB);
    }

    public static boolean isCore(net.minecraft.world.level.block.state.BlockState state) {
        return state != null && SIEGE_CORE != null && state.getBlock() == SIEGE_CORE;
    }

    private static void registerTrap(TrapKind kind) {
        Identifier id = id(kind.id());
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        boolean turret = !kind.isFloorPlate();
        Block block = new TrapBlock(kind, BlockBehaviour.Properties.of()
                .setId(blockKey)
                .strength(turret ? 3.5f : 2.0f, 6.0f)
                .sound(SoundType.METAL));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Item item = new BlockItem(block, new Item.Properties()
                .setId(itemKey)
                .useBlockDescriptionPrefix());
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);

        TRAP_BLOCKS.put(kind, block);
        TRAP_ITEMS.put(kind, item);
    }

    /** The trap a block state belongs to, or null. */
    public static TrapKind trapOf(net.minecraft.world.level.block.state.BlockState state) {
        return state != null && state.getBlock() instanceof TrapBlock trap ? trap.kind() : null;
    }

    /** The trap an item places, or null. */
    public static TrapKind trapOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (Map.Entry<TrapKind, Item> entry : TRAP_ITEMS.entrySet()) {
            if (stack.is(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
