package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Reports what TeamColor looks like, so the glow colour can be set cleanly. */
class ApiProbeTest {

    @Test
    void reportTeamColour() {
        String name = "net.minecraft.world.scores.TeamColor";
        try {
            Class<?> c = Class.forName(name, false, getClass().getClassLoader());
            System.out.println("PROBE: TeamColor isEnum=" + c.isEnum()
                    + " super=" + (c.getSuperclass() == null ? "-" : c.getSuperclass().getSimpleName()));

            List<String> fields = new ArrayList<>();
            for (Field f : c.getFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    fields.add(f.getName() + ":" + f.getType().getSimpleName());
                }
            }
            System.out.println("PROBE: TeamColor Felder -> " + String.join(" | ", fields));

            List<String> methods = new ArrayList<>();
            for (Method m : c.getMethods()) {
                if (m.getDeclaringClass() == Object.class) {
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
                methods.add(sb.append("):").append(m.getReturnType().getSimpleName()).toString());
            }
            java.util.Collections.sort(methods);
            System.out.println("PROBE: TeamColor Methoden -> " + String.join(" | ", methods));
        } catch (Throwable t) {
            System.out.println("PROBE: TeamColor -> FEHLT " + t);
        }
    }
}
