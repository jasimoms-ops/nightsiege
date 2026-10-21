package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Temporary: structure, map, trade, loot and attribute API. */
class DungeonProbeTest {

    private static Class<?> load(String n) {
        try {
            return Class.forName(n, false, DungeonProbeTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sig(java.lang.reflect.Executable m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        for (Class<?> p : m.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
        return sb.append(')').toString();
    }

    private static void methods(String n, String... parts) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (parts.length == 0) for (var ct : c.getConstructors()) sb.append("<init>").append(sig(ct)).append(' ');
        for (Method m : c.getMethods()) {
            boolean hit = parts.length == 0;
            for (String x : parts) hit |= m.getName().equals(x) || (x.endsWith("*") && m.getName().startsWith(x.substring(0, x.length() - 1)));
            if (hit && m.getDeclaringClass() != Object.class) sb.append(Modifier.isStatic(m.getModifiers()) ? "s:" : "").append(sig(m)).append(m.getReturnType().getSimpleName()).append(' ');
        }
        System.out.println("PROBE: " + n.substring(n.lastIndexOf('.') + 1) + " " + sb);
    }

    private static void ctors(String n) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (var ct : c.getConstructors()) sb.append("<init>").append(sig(ct)).append(' ');
        System.out.println("PROBE: C " + n.substring(n.lastIndexOf('.') + 1) + " " + sb);
    }

    private static void fields(String n, String... parts) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (var f : c.getFields()) {
            boolean hit = parts.length == 0;
            for (String x : parts) hit |= f.getName().contains(x);
            if (hit) sb.append(f.getName()).append(':').append(f.getType().getSimpleName()).append(' ');
        }
        System.out.println("PROBE: F " + c.getSimpleName() + " " + sb);
    }

    @Test
    void probe() {
        methods("net.minecraft.world.level.StructureManager", "getAllStructuresAt", "getStructureAt", "getStructureWithPieceAt", "startsForStructure", "getStructureWithPieceAt");
        methods("net.minecraft.server.level.ServerLevel", "structureManager", "findNearestMapStructure", "getChunkSource", "getSeed", "getHeight", "registryAccess", "setBlockAndUpdate", "dimension");
        methods("net.minecraft.world.level.chunk.ChunkGenerator", "findNearestMapStructure");
        methods("net.minecraft.world.level.levelgen.structure.StructureStart", "getBoundingBox", "getChunkPos", "isValid", "getStructure");
        methods("net.minecraft.world.level.levelgen.structure.BoundingBox", "minX", "maxX", "minY", "maxY", "minZ", "maxZ", "getCenter", "isInside", "inflatedBy");
        methods("net.minecraft.core.RegistryAccess", "lookupOrThrow", "lookup");
        methods("net.minecraft.core.Registry", "getKey", "listElements", "entrySet", "getValue", "get", "wrapAsHolder", "keySet");
        methods("net.minecraft.core.HolderSet", "direct");
        methods("net.minecraft.world.item.MapItem", "create", "renderBiomePreviewMap");
        methods("net.minecraft.world.level.saveddata.maps.MapItemSavedData", "addTargetDecoration");
        fields("net.minecraft.world.level.saveddata.maps.MapDecorationTypes");
        methods("net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper");
        methods("net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper$WanderingTraderOffersBuilder");
        ctors("net.minecraft.world.item.trading.MerchantOffer");
        ctors("net.minecraft.world.item.trading.ItemCost");
        fields("net.minecraft.world.entity.npc.villager.VillagerProfession", "CARTOGRAPHER");
        fields("net.minecraft.world.entity.npc.VillagerProfession", "CARTOGRAPHER");
        methods("net.minecraft.world.entity.npc.villager.VillagerTrades$ItemListing");
        methods("net.minecraft.world.entity.npc.VillagerTrades$ItemListing");
        methods("net.fabricmc.fabric.api.loot.v3.LootTableEvents$Modify");
        fields("net.fabricmc.fabric.api.loot.v3.LootTableEvents");
        ctors("net.minecraft.world.entity.ai.attributes.AttributeModifier");
        methods("net.minecraft.world.entity.ai.attributes.AttributeInstance", "addOrUpdateTransientModifier", "removeModifier", "addTransientModifier", "hasModifier");
        fields("net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation");
        methods("net.minecraft.world.entity.Entity", "teleportTo");
        methods("net.minecraft.server.level.ServerPlayer", "teleportTo");
        ctors("net.minecraft.server.level.ServerBossEvent");
        fields("net.minecraft.world.level.storage.loot.BuiltInLootTables", "SIMPLE_DUNGEON", "PILLAGER_OUTPOST", "SHIPWRECK_MAP", "BASTION_TREASURE", "UNDERWATER_RUIN_BIG", "RUINED_PORTAL", "WOODLAND_MANSION");
        methods("net.minecraft.world.level.storage.loot.LootPool", "lootPool");
        methods("net.minecraft.world.level.storage.loot.LootTable$Builder", "withPool", "pool");
        methods("net.minecraft.world.level.storage.loot.entries.LootItem", "lootTableItem");
        methods("net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition", "randomChance");
        methods("net.minecraft.world.level.block.Block", "getDrops");
    }
}
