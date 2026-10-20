package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports which creative tab API this Minecraft version actually offers.
 * Temporary: remove once the tab is implemented.
 */
class ApiProbeTest {

    private static void probe(String line) {
        System.out.println("PROBE: " + line);
    }

    @Test
    void reportCreativeTabApi() {
        for (String name : List.of(
                "net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup",
                "net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab",
                "net.minecraft.world.item.CreativeModeTab",
                "net.minecraft.world.item.CreativeModeTab$Builder")) {
            try {
                Class<?> c = Class.forName(name);
                List<String> methods = new ArrayList<>();
                for (Method m : c.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
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
                    methods.add(sb.append(')').toString());
                }
                java.util.Collections.sort(methods);
                probe(name + " -> " + String.join(" | ", methods));
            } catch (Throwable t) {
                probe(name + " -> FEHLT (" + t.getClass().getSimpleName() + ")");
            }
        }
    }
}
