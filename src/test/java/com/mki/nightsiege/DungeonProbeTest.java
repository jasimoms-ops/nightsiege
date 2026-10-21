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
        methods("net.fabricmc.fabric.api.loot.v3.LootTableEvents$Modify");
        fields("net.fabricmc.fabric.api.loot.v3.LootTableEvents");
        ctors("net.minecraft.world.entity.ai.attributes.AttributeModifier");
        methods("net.minecraft.world.entity.ai.attributes.AttributeInstance", "addOrUpdateTransientModifier", "removeModifier", "addTransientModifier", "hasModifier");
        fields("net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation");
        methods("net.minecraft.world.entity.Entity", "teleportTo");
        ctors("net.minecraft.server.level.ServerBossEvent");
        fields("net.minecraft.world.level.storage.loot.BuiltInLootTables", "SIMPLE_DUNGEON", "PILLAGER_OUTPOST", "SHIPWRECK_MAP", "BASTION_TREASURE", "UNDERWATER_RUIN_BIG", "RUINED_PORTAL");
        methods("net.minecraft.world.level.storage.loot.LootPool", "lootPool");
        methods("net.minecraft.world.level.storage.loot.LootTable$Builder", "withPool");
        methods("net.minecraft.world.level.storage.loot.entries.LootItem", "lootTableItem");
        methods("net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition", "randomChance");
        methods("net.minecraft.world.level.storage.loot.LootPool$Builder", "add", "when", "setRolls");
        methods("net.minecraft.world.level.ServerLevelAccessor", "getLevel");
        methods("net.minecraft.world.level.Level", "getChunk");
        methods("net.minecraft.core.component.DataComponentMap", "get");
    }
}
