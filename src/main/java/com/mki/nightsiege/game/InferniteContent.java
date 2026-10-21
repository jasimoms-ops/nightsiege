package com.mki.nightsiege.game;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Infernite: the tier after netherite.
 *
 * <p>The ore hides deep in the Nether, about as rare as ancient debris. Smelted
 * it gives scrap; four scrap and a netherite ingot make an ingot. The upgrade
 * template, earned by maxing a class's ultimate, turns netherite gear (and a
 * bow, crossbow, shield or mace) into infernite gear with slightly better
 * stats. The one piece that matches the player's own path also keeps their
 * skills and ultimate working outside a siege.</p>
 */
public final class InferniteContent {

    public static Block ORE;
    public static Block BLOCK;
    public static Item ORE_ITEM;
    public static Item BLOCK_ITEM;
    public static Item SCRAP;
    public static Item INGOT;
    public static Item TEMPLATE;

    /** Upgraded gear by its own id, in creative-tab order. */
    public static final Map<String, Item> GEAR = new LinkedHashMap<>();

    public static final TagKey<Item> TOOL_MATERIALS =
            TagKey.create(Registries.ITEM, ModContent.id("infernite_tool_materials"));
    public static final TagKey<Item> REPAIRS_ARMOR =
            TagKey.create(Registries.ITEM, ModContent.id("repairs_infernite_armor"));

    /** A step above netherite: more durable, faster, one point more damage. */
    public static final ToolMaterial TOOLS = new ToolMaterial(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2531, 10.0f, 5.0f, 18, TOOL_MATERIALS);

    public static final ResourceKey<EquipmentAsset> ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, ModContent.id("infernite"));

    public static ArmorMaterial ARMOR;

    private InferniteContent() {
    }

    public static void register() {
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        defense.put(ArmorType.BOOTS, 3);
        defense.put(ArmorType.LEGGINGS, 7);
        defense.put(ArmorType.CHESTPLATE, 9);
        defense.put(ArmorType.HELMET, 3);
        defense.put(ArmorType.BODY, 13);
        ARMOR = new ArmorMaterial(42, defense, 18, SoundEvents.ARMOR_EQUIP_NETHERITE,
                3.5f, 0.15f, REPAIRS_ARMOR, ASSET);

        ORE = block("infernite_ore", BlockBehaviour.Properties.of()
                .strength(30.0f, 1200.0f).requiresCorrectToolForDrops().sound(SoundType.ANCIENT_DEBRIS));
        BLOCK = block("infernite_block", BlockBehaviour.Properties.of()
                .strength(50.0f, 1200.0f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK));
        ORE_ITEM = blockItem("infernite_ore", ORE);
        BLOCK_ITEM = blockItem("infernite_block", BLOCK);
        SCRAP = item("infernite_scrap", new Item.Properties().fireResistant(), Item::new);
        INGOT = item("infernite_ingot", new Item.Properties().fireResistant(), Item::new);
        TEMPLATE = item("infernite_upgrade_smithing_template", new Item.Properties().rarity(Rarity.EPIC),
                InferniteContent::template);

        gear("infernite_sword", p -> new Item(p.sword(TOOLS, 3.0f, -2.4f)));
        gear("infernite_pickaxe", p -> new Item(p.pickaxe(TOOLS, 1.0f, -2.8f)));
        gear("infernite_axe", p -> new AxeItem(TOOLS, 5.0f, -3.0f, p));
        gear("infernite_shovel", p -> new ShovelItem(TOOLS, 1.5f, -3.0f, p));
        gear("infernite_hoe", p -> new HoeItem(TOOLS, -4.0f, 0.0f, p));
        // Built exactly like vanilla's netherite spear, on the infernite material.
        gear("infernite_spear", p -> new Item(p.spear(TOOLS, 1.15f, 1.2f, 0.4f, 2.5f, 9.0f, 5.5f, 5.1f, 8.75f, 4.6f)
                .repairable(TOOL_MATERIALS)));
        gear("infernite_helmet", p -> new Item(p.humanoidArmor(ARMOR, ArmorType.HELMET)));
        gear("infernite_chestplate", p -> new Item(p.humanoidArmor(ARMOR, ArmorType.CHESTPLATE)));
        gear("infernite_leggings", p -> new Item(p.humanoidArmor(ARMOR, ArmorType.LEGGINGS)));
        gear("infernite_boots", p -> new Item(p.humanoidArmor(ARMOR, ArmorType.BOOTS)));
        // Bow, crossbow, shield and mace: vanilla's own recipe, sturdier.
        gear("infernite_bow", p -> new BowItem(p.durability(640).enchantable(1).repairable(TOOL_MATERIALS)));
        gear("infernite_crossbow", p -> new CrossbowItem(p.stacksTo(1).durability(720)
                .component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)
                .enchantable(1).repairable(TOOL_MATERIALS)));
        gear("infernite_shield", p -> new ShieldItem(p.durability(560)
                .component(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
                .repairable(TOOL_MATERIALS)
                .equippableUnswappable(EquipmentSlot.OFFHAND)
                .delayedComponent(DataComponents.BLOCKS_ATTACKS, registries -> new BlocksAttacks(0.25f, 1.0f,
                        List.of(new BlocksAttacks.DamageReduction(90.0f, Optional.empty(), 0.0f, 1.0f)),
                        new BlocksAttacks.ItemDamageFunction(3.0f, 1.0f, 1.0f),
                        Optional.of(registries.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                        Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
                .component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK)));
        gear("infernite_mace", p -> new MaceItem(p.durability(750)
                .component(DataComponents.TOOL, MaceItem.createToolProperties())
                .repairable(TOOL_MATERIALS)
                .attributes(MaceItem.createAttributes())
                .enchantable(18)
                .component(DataComponents.WEAPON, new Weapon(1))));

        // As rare as ancient debris: one hidden vein attempt per Nether chunk.
        BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(),
                GenerationStep.Decoration.UNDERGROUND_DECORATION,
                ResourceKey.create(Registries.PLACED_FEATURE, ModContent.id("infernite_ore")));
    }

    /** Everything for the creative tab, in a sensible order. */
    public static List<Item> creativeItems() {
        List<Item> out = new ArrayList<>(List.of(ORE_ITEM, SCRAP, INGOT, BLOCK_ITEM, TEMPLATE));
        out.addAll(GEAR.values());
        return out;
    }

    public static Item gear(String id) {
        return GEAR.get(id);
    }

    // ------------------------------------------------------------------

    private static Block block(String name, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ModContent.id(name));
        Block block = new Block(properties.setId(key));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        return block;
    }

    private static Item blockItem(String name, Block block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ModContent.id(name));
        Item item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix().fireResistant());
        Registry.register(BuiltInRegistries.ITEM, key, item);
        return item;
    }

    private static Item item(String name, Item.Properties properties,
                             java.util.function.Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ModContent.id(name));
        Item item = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        return item;
    }

    private static void gear(String name, java.util.function.Function<Item.Properties, Item> factory) {
        Item.Properties properties = new Item.Properties().fireResistant().rarity(Rarity.EPIC);
        GEAR.put(name, item(name, properties, factory));
    }

    private static Item template(Item.Properties properties) {
        Component appliesTo = Component.translatable("item.nightsiege.smithing_template.infernite.applies_to")
                .withStyle(ChatFormatting.BLUE);
        Component ingredients = Component.translatable("item.nightsiege.smithing_template.infernite.ingredients")
                .withStyle(ChatFormatting.BLUE);
        Component base = Component.translatable("item.nightsiege.smithing_template.infernite.base_slot");
        Component addition = Component.translatable("item.nightsiege.smithing_template.infernite.additions_slot");
        List<Identifier> baseIcons = List.of(
                slot("helmet"), slot("sword"), slot("chestplate"), slot("pickaxe"),
                slot("leggings"), slot("axe"), slot("boots"), slot("hoe"), slot("shovel"));
        List<Identifier> additionIcons = List.of(slot("ingot"));
        return new SmithingTemplateItem(appliesTo, ingredients, base, addition, baseIcons, additionIcons, properties);
    }

    private static Identifier slot(String name) {
        return Identifier.withDefaultNamespace("container/slot/" + name);
    }
}
