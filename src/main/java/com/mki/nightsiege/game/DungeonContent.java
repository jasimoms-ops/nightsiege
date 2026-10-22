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
    public static final Map<PerkKind, Item> FRAGMENTS = new EnumMap<>(PerkKind.class);

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
        MAP = item("dungeon_map", p -> new DungeonMapItem(p.stacksTo(16).rarity(Rarity.UNCOMMON)));
        COMPASS = item("dungeon_compass", p -> new DungeonCompassItem(p.stacksTo(1).rarity(Rarity.EPIC)));
        registerLoot();
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
        return config != null && config.settingsVersion >= 1 && config.dungeonsEnabled;
    }

    private static void registerLoot() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin() || !"minecraft".equals(key.identifier().getNamespace())) {
                return;
            }
            if (!enabledInFile()) {
                return;   // dungeons are off: no maps in chests
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
        out.addAll(FRAGMENTS.values());
        out.add(CORE_ITEM);
        out.add(SPENT_CORE_ITEM);
        return out;
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
