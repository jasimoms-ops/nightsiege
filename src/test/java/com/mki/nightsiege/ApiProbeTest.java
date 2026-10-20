package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** One sweep over everything the core interface needs. Temporary. */
class ApiProbeTest {

    private static Class<?> load(String n) throws ClassNotFoundException {
        return Class.forName(n, false, ApiProbeTest.class.getClassLoader());
    }

    private static void out(String label, List<String> items) {
        String joined = items.isEmpty() ? "keine" : String.join(" | ", items);
        if (joined.length() > 2200) {
            joined = joined.substring(0, 2200) + " ...";
        }
        System.out.println("PROBE: " + label + " -> " + joined);
    }

    private static void fields(String cls, String label) {
        List<String> o = new ArrayList<>();
        try {
            for (Field f : load(cls).getFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    o.add(f.getName());
                }
            }
        } catch (Throwable t) {
            o.add("FEHLT " + t.getClass().getSimpleName());
        }
        java.util.Collections.sort(o);
        out(label, o);
    }

    private static void methods(String cls, String label, boolean staticOnly, String... filter) {
        List<String> o = new ArrayList<>();
        try {
            for (Method m : load(cls).getMethods()) {
                if (staticOnly && !Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                if (m.getDeclaringClass() == Object.class) {
                    continue;
                }
                boolean want = filter.length == 0;
                for (String f : filter) {
                    if (m.getName().toLowerCase().contains(f.toLowerCase())) {
                        want = true;
                    }
                }
                if (!want) {
                    continue;
                }
                StringBuilder sb = new StringBuilder(m.getName()).append('(');
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(ps[i].getSimpleName());
                }
                o.add(sb.append("):").append(m.getReturnType().getSimpleName()).toString());
            }
        } catch (Throwable t) {
            o.add("FEHLT " + t.getClass().getSimpleName());
        }
        java.util.Collections.sort(o);
        out(label, o);
    }

    @Test
    void probeEverything() {
        fields("net.minecraft.network.codec.ByteBufCodecs", "ByteBufCodecs");
        methods("net.minecraft.network.codec.StreamCodec", "StreamCodec static", true);
        methods("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking", "ServerPlayNetworking", true);
        methods("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking", "ClientPlayNetworking", true);
        methods("net.minecraft.server.network.ServerGamePacketListenerImpl", "Connection", false, "disconnect");
        methods("net.minecraft.client.gui.screens.Screen", "Screen", false,
                "init", "render", "mouseClick", "mouseScroll", "mouseDrag", "onClose", "isPause", "addRender", "width", "height");
        methods("net.minecraft.client.gui.GuiGraphicsExtractor", "GuiGraphicsExtractor", false);
        methods("net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents$AfterKilledOtherEntity",
                "AfterKilledOtherEntity", false);
        methods("net.minecraft.client.Minecraft", "Minecraft", false, "setScreen", "getWindow", "getInstance");
    }
}
