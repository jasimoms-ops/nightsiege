package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PerkKind;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Blocks and items of the dungeons: the cores, the fragments, the map and the test compass. */
public final class DungeonContent {

    /** A dungeon core that has not been beaten yet. Cannot be broken. */
    public static Block CORE;
    /** What is left of a core once its dungeon is beaten. */
    public static Block SPENT_CORE;
    public static Item CORE_ITEM;
    public static Item SPENT_CORE_ITEM;
    public static Item MAP;
    public static Item COMPASS;
    /** The old perk fragments of the first dungeons; still registered so old worlds keep them. */
    public static final Map<PerkKind, Item> FRAGMENTS = new EnumMap<>(PerkKind.class);
    /** Night fragments: set into the main core, they make the next siege a special night. */
    public static final Map<com.mki.nightsiege.core.NightFragment, Item> NIGHT_FRAGMENTS =
            new EnumMap<>(com.mki.nightsiege.core.NightFragment.class);

    private DungeonContent() {
    }

    public static void register() {
        CORE = block("dungeon_core", BlockBehaviour.Properties.of()
                .strength(-1.0f, 3600000.0f).sound(SoundType.VAULT).lightLevel(state -> 10));
        SPENT_CORE = block("dungeon_core_spent", BlockBehaviour.Properties.of()
                .strength(5.0f, 6.0f).sound(SoundType.VAULT));
        CORE_ITEM = item("dungeon_core", p -> new BlockItem(CORE, p.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));
        SPENT_CORE_ITEM = item("dungeon_core_spent", p -> new BlockItem(SPENT_CORE, p.useBlockDescriptionPrefix()));
        for (PerkKind kind : PerkKind.values()) {
            FRAGMENTS.put(kind, item("core_fragment_" + kind.id(),
                    p -> new Item(p.stacksTo(16).rarity(kind == PerkKind.VIGILANCE ? Rarity.EPIC : Rarity.RARE))));
        }
        for (com.mki.nightsiege.core.NightFragment kind : com.mki.nightsiege.core.NightFragment.values()) {
            NIGHT_FRAGMENTS.put(kind, item("night_fragment_" + kind.id(), p -> new Item(p.stacksTo(16).rarity(Rarity.EPIC))));
        }
        MAP = item("dungeon_map", p -> new Item(p.stacksTo(16).rarity(Rarity.UNCOMMON)));   // the old dungeons are gone (29 October 2026)
        COMPASS = item("dungeon_compass", p -> new Item(p.stacksTo(1).rarity(Rarity.EPIC)));
        registerLoot();
        net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions.register(DUNGEONS_ON);
    }

    /** Blank dungeon maps now and then in the chests of vanilla structures. */
    private static final Map<String, Float> MAP_LOOT = Map.of(
            "chests/simple_dungeon", 0.25f,
            "chests/pillager_outpost", 0.3f,
            "chests/shipwreck_map", 0.3f,
            "chests/underwater_ruin_big", 0.25f,
            "chests/ruined_portal", 0.15f,
            "chests/bastion_treasure", 0.4f);

    /** Whether the dungeons are on; set from the config when the siege loads it. */
    public static volatile boolean active;

    /** Loot is built before the siege reads its config, so the file is read here directly. */
    private static boolean enabledInFile() {
        com.mki.nightsiege.core.SiegeConfig config = Json.read(Json.configFile("nightsiege.json"),
                com.mki.nightsiege.core.SiegeConfig.class);
        return config != null && config.settingsVersion >= 1 && config.dungeonsEnabled && config.legacyDungeons;
    }

    /** Data files with this load condition only load while the dungeons are on (the cartographer's map trade). */
    private static final net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType<DungeonsOn> DUNGEONS_ON =
            net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType.create(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("nightsiege", "dungeons_enabled"),
                    com.mojang.serialization.MapCodec.unit(new DungeonsOn()));

    private record DungeonsOn() implements net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition {
        @Override
        public net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType<?> getType() {
            return DUNGEONS_ON;
        }

        @Override
        public boolean test(net.minecraft.resources.RegistryOps.RegistryInfoLookup registryInfo) {
            return enabledInFile();
        }
    }

    /** Dungeon maps in chests (off: since October 2026 only the trader caravan sells them). */
    private static final boolean MAPS_IN_CHESTS = false;

    private static void registerLoot() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin() || !"minecraft".equals(key.identifier().getNamespace())) {
                return;
            }
            if (!enabledInFile() || !MAPS_IN_CHESTS) {
                return;   // maps only come from the trader caravan now
            }
            Float chance = MAP_LOOT.get(key.identifier().getPath());
            if (chance == null) {
                return;
            }
            tableBuilder.withPool(LootPool.lootPool()
                    .add(LootItem.lootTableItem(MAP))
                    .when(LootItemRandomChanceCondition.randomChance(chance)));
        });
    }

    public static List<Item> creativeItems() {
        List<Item> out = new ArrayList<>();
        out.add(COMPASS);
        out.add(MAP);
        out.addAll(NIGHT_FRAGMENTS.values());
        out.add(CORE_ITEM);
        out.add(SPENT_CORE_ITEM);
        return out;
    }

    /** The night fragment an item is, or null. */
    public static com.mki.nightsiege.core.NightFragment nightFragmentKind(Item item) {
        for (Map.Entry<com.mki.nightsiege.core.NightFragment, Item> entry : NIGHT_FRAGMENTS.entrySet()) {
            if (entry.getValue() == item) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** The perk a fragment item feeds, or null. */
    public static PerkKind fragmentKind(Item item) {
        for (Map.Entry<PerkKind, Item> entry : FRAGMENTS.entrySet()) {
            if (entry.getValue() == item) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Block block(String name, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ModContent.id(name));
        Block block = new Block(properties.setId(key));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        return block;
    }

    private static Item item(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ModContent.id(name));
        Item item = factory.apply(new Item.Properties().setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        return item;
    }
}
