package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Temporary: permissions, world path, font and tooltip API. */
class MiscProbeTest {

    private static Class<?> load(String n) {
        try {
            return Class.forName(n, false, MiscProbeTest.class.getClassLoader());
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
        for (Method m : c.getMethods()) {
            boolean hit = parts.length == 0;
            for (String x : parts) hit |= m.getName().toLowerCase().contains(x.toLowerCase());
            if (hit && m.getDeclaringClass() != Object.class) sb.append(Modifier.isStatic(m.getModifiers()) ? "s:" : "").append(sig(m)).append(m.getReturnType().getSimpleName()).append(' ');
        }
        System.out.println("PROBE: " + n.substring(n.lastIndexOf('.') + 1) + " " + sb);
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
            for (String x : parts) hit |= f.getName().toLowerCase().contains(x.toLowerCase());
            if (hit) sb.append(f.getName()).append(':').append(f.getType().getSimpleName()).append(' ');
        }
        System.out.println("PROBE: F " + c.getSimpleName() + " " + sb);
    }

    @Test
    void probe() {
        methods("net.minecraft.commands.CommandSourceStack", "permission");
        methods("net.minecraft.commands.Commands", "permission", "hasPermission");
        fields("net.minecraft.commands.Commands", "LEVEL", "PERMISSION");
        methods("net.minecraft.server.permissions.PermissionSet");
        methods("net.minecraft.server.permissions.Permissions");
        fields("net.minecraft.server.permissions.Permissions");
        fields("net.minecraft.server.permissions.PermissionLevel");
        methods("net.minecraft.server.MinecraftServer", "getWorldPath", "getServerDirectory");
        fields("net.minecraft.world.level.storage.LevelResource", "ROOT", "DATA");
        methods("net.minecraft.client.gui.Font", "split", "wordWrapHeight", "lineHeight");
        fields("net.minecraft.client.gui.Font", "lineHeight");
        methods("net.minecraft.client.gui.screens.Screen", "mouseScrolled", "keyPressed");
        methods("net.minecraft.world.level.Level", "isClientSide");
        methods("net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback", "getTooltip");
        fields("net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback");
        methods("net.minecraft.client.gui.components.Button", "setAlpha");
    }
}
