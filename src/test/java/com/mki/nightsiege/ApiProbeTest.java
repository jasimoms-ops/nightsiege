package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/** Reports the exact generic signature of the team colour API. */
class ApiProbeTest {

    @Test
    void reportTeamColourApi() {
        for (String className : new String[]{
                "net.minecraft.world.scores.PlayerTeam",
                "net.minecraft.world.scores.Team"}) {
            try {
                Class<?> c = Class.forName(className, false, getClass().getClassLoader());
                for (Method m : c.getMethods()) {
                    String n = m.getName().toLowerCase();
                    if (!n.contains("color") && !n.contains("colour")) {
                        continue;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(m.getGenericReturnType().getTypeName()).append(' ')
                      .append(m.getName()).append('(');
                    Type[] ps = m.getGenericParameterTypes();
                    for (int i = 0; i < ps.length; i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(ps[i].getTypeName());
                    }
                    sb.append(')');
                    System.out.println("PROBE: " + className.substring(className.lastIndexOf('.') + 1)
                            + " :: " + sb);
                }
            } catch (Throwable t) {
                System.out.println("PROBE: " + className + " -> FEHLT " + t);
            }
        }
    }
}
