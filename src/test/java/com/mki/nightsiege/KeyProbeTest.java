package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Temporary: key mapping, spear and reach API. */
class KeyProbeTest {

    private static Class<?> load(String n) {
        try {
            return Class.forName(n, false, KeyProbeTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sig(java.lang.reflect.Executable m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        for (Class<?> p : m.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
        return sb.append(')').toString();
    }

    private static void all(String n) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (var ct : c.getConstructors()) sb.append("<init>").append(sig(ct)).append(' ');
        for (Method m : c.getDeclaredMethods()) if (Modifier.isPublic(m.getModifiers())) sb.append(sig(m)).append(m.getReturnType().getSimpleName()).append(' ');
        System.out.println("PROBE: " + n.substring(n.lastIndexOf('.') + 1) + " " + sb);
    }

    private static void fields(String n, String... names) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            try {
                var f = c.getField(name);
                sb.append(name).append(':').append(f.getType().getSimpleName()).append(' ');
            } catch (Throwable t) {
                sb.append(name).append(":NONE ");
            }
        }
        System.out.println("PROBE: F " + c.getSimpleName() + " " + sb);
    }

    @Test
    void probe() {
        for (String n : new String[]{
                "net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper",
                "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper",
                "net.minecraft.client.KeyMapping",
                "net.minecraft.client.KeyMapping$Category",
                "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents"}) {
            all(n);
        }
        fields("net.minecraft.world.item.Items", "IRON_SPEAR", "WOODEN_SPEAR", "DIAMOND_SPEAR", "NETHERITE_SPEAR", "CROSSBOW", "SPYGLASS");
        fields("net.minecraft.tags.ItemTags", "SPEARS", "AXES", "SWORDS");
        fields("net.minecraft.world.entity.ai.attributes.Attributes", "ENTITY_INTERACTION_RANGE", "BLOCK_INTERACTION_RANGE");
        fields("net.minecraft.world.entity.Entity", "hurtMarked");
        fields("org.lwjgl.glfw.GLFW", "GLFW_KEY_R", "GLFW_KEY_V");
        fields("com.mojang.blaze3d.platform.InputConstants$Type", "KEYSYM");
        fields("net.minecraft.core.particles.ParticleTypes", "END_ROD", "CLOUD", "FLASH", "SONIC_BOOM", "WAX_OFF", "ENCHANTED_HIT", "EXPLOSION");
        fields("net.minecraft.sounds.SoundEvents", "CROSSBOW_SHOOT", "TRIDENT_THROW", "BELL_BLOCK", "MACE_SMASH_GROUND_HEAVY", "BEACON_ACTIVATE", "EVOKER_CAST_SPELL", "WITCH_THROW", "ILLUSIONER_CAST_SPELL");
    }
}
