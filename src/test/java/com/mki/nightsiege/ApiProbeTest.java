package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Last sweep: the Screen base class and how to disconnect a player. */
class ApiProbeTest {

    private static Class<?> load(String n) throws ClassNotFoundException {
        return Class.forName(n, false, ApiProbeTest.class.getClassLoader());
    }

    private static void probe(String cls, String label, String... filter) {
        List<String> o = new ArrayList<>();
        try {
            Class<?> c = load(cls);
            for (Method m : c.getMethods()) {
                if (m.getDeclaringClass() == Object.class) {
                    continue;
                }
                boolean want = false;
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
            for (Method m : c.getDeclaredMethods()) {
                String n = m.getName();
                if (!n.equals("addRenderableWidget") && !n.equals("addWidget") && !n.equals("init")) {
                    continue;
                }
                StringBuilder sb = new StringBuilder("[decl]").append(n).append('(');
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(ps[i].getSimpleName());
                }
                o.add(sb.append(')').toString());
            }
            List<String> fs = new ArrayList<>();
            for (Field f : c.getFields()) {
                fs.add(f.getName() + ":" + f.getType().getSimpleName());
            }
            if (!fs.isEmpty()) {
                o.add("FELDER{" + String.join(",", fs) + "}");
            }
        } catch (Throwable t) {
            o.add("FEHLT " + t.getClass().getSimpleName());
        }
        java.util.Collections.sort(o);
        String j = String.join(" | ", o);
        System.out.println("PROBE: " + label + " -> " + (j.length() > 1800 ? j.substring(0, 1800) : j));
    }

    @Test
    void probeScreen() {
        probe("net.minecraft.client.gui.screens.Screen", "Screen",
                "addRenderable", "addWidget", "mouseClick", "mouseScroll", "onClose", "isPause", "init");
        probe("net.minecraft.server.level.ServerPlayer", "ServerPlayer", "connection", "disconnect");
        probe("net.minecraft.server.network.ServerGamePacketListenerImpl", "Listener", "disconnect");
    }
}
