package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Temporary: tool, armour, smithing and worldgen API. */
class GearProbeTest {

    private static Class<?> load(String n) {
        try {
            return Class.forName(n, false, GearProbeTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sig(java.lang.reflect.Executable m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        for (Class<?> p : m.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
        return sb.append(')').toString();
    }

    private static void all(String n, String... names) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (var ct : c.getConstructors()) sb.append("<init>").append(sig(ct)).append(' ');
        for (Method m : c.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            boolean hit = names.length == 0;
            for (String x : names) hit |= m.getName().equals(x);
            if (hit) sb.append(Modifier.isStatic(m.getModifiers()) ? "static " : "").append(sig(m)).append(m.getReturnType().getSimpleName()).append(' ');
        }
        System.out.println("PROBE: " + n.substring(n.lastIndexOf('.') + 1) + " " + sb);
    }

    private static void fields(String n) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (var f : c.getFields()) sb.append(f.getName()).append(':').append(f.getType().getSimpleName()).append(' ');
        System.out.println("PROBE: F " + c.getSimpleName() + " " + sb);
    }

    @Test
    void probe() {
        all("net.minecraft.world.item.Item$Properties", "sword", "pickaxe", "axe", "shovel", "hoe", "spear", "humanoidArmor", "fireResistant", "durability", "stacksTo", "rarity", "repairable", "enchantable", "tool", "component");
        all("net.minecraft.world.item.ToolMaterial");
        fields("net.minecraft.world.item.ToolMaterial");
        all("net.minecraft.world.item.equipment.ArmorMaterial");
        fields("net.minecraft.world.item.equipment.ArmorMaterials");
        fields("net.minecraft.world.item.equipment.ArmorType");
        all("net.minecraft.world.item.equipment.EquipmentAssets", "createId");
        fields("net.minecraft.world.item.equipment.EquipmentAssets");
        all("net.minecraft.world.item.SmithingTemplateItem");
        all("net.minecraft.world.item.AxeItem");
        all("net.minecraft.world.item.ShovelItem");
        all("net.minecraft.world.item.HoeItem");
        all("net.minecraft.world.item.MaceItem", "createAttributes");
        all("net.minecraft.world.item.BowItem");
        all("net.minecraft.world.item.CrossbowItem");
        all("net.minecraft.world.item.ShieldItem");
        all("net.fabricmc.fabric.api.biome.v1.BiomeModifications", "addFeature");
        all("net.fabricmc.fabric.api.biome.v1.BiomeSelectors", "foundInTheNether");
        fields("net.minecraft.world.level.levelgen.GenerationStep$Decoration");
        fields("net.minecraft.core.registries.Registries");
        fields("net.minecraft.tags.ItemTags");
        fields("net.minecraft.tags.BlockTags");
        all("net.minecraft.world.level.block.state.BlockBehaviour$Properties", "requiresCorrectToolForDrops", "strength", "sound", "explosionResistance");
        fields("net.minecraft.world.level.block.SoundType");
        all("net.minecraft.world.item.Item$TooltipContext");
        all("net.minecraft.world.item.Item", "appendHoverText", "isFoil");
    }
}
