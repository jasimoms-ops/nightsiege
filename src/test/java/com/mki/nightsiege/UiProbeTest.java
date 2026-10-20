package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Temporary: spawn, HUD and GUI drawing API. */
class UiProbeTest {

    private static Class<?> load(String n) {
        try {
            return Class.forName(n, false, UiProbeTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sig(java.lang.reflect.Executable m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        for (Class<?> p : m.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
        return sb.append(')').toString();
    }

    private static void methods(String n, String... names) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Method m : c.getMethods()) {
            boolean hit = names.length == 0;
            for (String x : names) hit |= m.getName().equals(x);
            if (hit && m.getDeclaringClass() != Object.class) sb.append(sig(m)).append(m.getReturnType().getSimpleName()).append(' ');
        }
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
        methods("net.minecraft.world.entity.Mob", "finalizeSpawn");
        methods("net.minecraft.server.level.ServerLevel", "getCurrentDifficultyAt", "addFreshEntityWithPassengers");
        methods("net.minecraft.world.entity.Entity", "startRiding", "getPassengers");
        fields("net.minecraft.world.entity.EntityType", "CAMEL_HUSK", "PARCHED", "BOGGED", "BREEZE", "ZOMBIE_HORSE", "ILLUSIONER", "ZOMBIE_NAUTILUS", "SKELETON_HORSE");
        methods("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry");
        methods("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement");
        Class<?> v = load("net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements");
        if (v != null) {
            StringBuilder sb = new StringBuilder();
            for (var f : v.getFields()) sb.append(f.getName()).append(' ');
            System.out.println("PROBE: VHE " + sb);
        }
        methods("net.minecraft.client.gui.GuiGraphicsExtractor", "blit", "blitSprite", "item", "fill", "text", "pose", "renderItemDecorations", "itemDecorations", "nextStratum", "enableScissor", "disableScissor");
        fields("net.minecraft.client.renderer.RenderPipelines", "GUI_TEXTURED");
        methods("net.minecraft.client.Minecraft", "getWindow", "getInstance");
        methods("com.mojang.blaze3d.platform.Window", "getGuiScaledWidth", "getGuiScaledHeight");
        methods("net.minecraft.client.gui.screens.Screen", "renderBackground", "renderTransparentBackground", "renderBlurredBackground", "renderMenuBackground", "extractBackground", "extractRenderState");
    }
}
