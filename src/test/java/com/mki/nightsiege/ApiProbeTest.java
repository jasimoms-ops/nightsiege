package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reports which sound, particle and team APIs this Minecraft version offers. */
class ApiProbeTest {

    /** Loads without running static initialisers, which need a booted game. */
    private static Class<?> load(String className) throws ClassNotFoundException {
        return Class.forName(className, false, ApiProbeTest.class.getClassLoader());
    }

    private static void probe(String label, List<String> items) {
        if (items.isEmpty()) {
            System.out.println("PROBE: " + label + " -> keine");
            return;
        }
        String joined = String.join(" | ", items);
        if (joined.length() > 2500) {
            joined = joined.substring(0, 2500) + " ...";
        }
        System.out.println("PROBE: " + label + " -> " + joined);
    }

    private static List<String> methods(String className, String... nameParts) {
        List<String> out = new ArrayList<>();
        try {
            Class<?> c = load(className);
            for (Method m : c.getMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                boolean wanted = nameParts.length == 0;
                for (String part : nameParts) {
                    if (m.getName().toLowerCase().contains(part.toLowerCase())) {
                        wanted = true;
                    }
                }
                if (!wanted) {
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
                out.add(sb.append(')').toString());
            }
        } catch (Throwable t) {
            out.add("KLASSE FEHLT: " + t.getClass().getSimpleName());
        }
        java.util.Collections.sort(out);
        return out;
    }

    private static List<String> fields(String className, String... nameParts) {
        List<String> out = new ArrayList<>();
        try {
            Class<?> c = load(className);
            for (Field f : c.getFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                boolean wanted = nameParts.length == 0;
                for (String part : nameParts) {
                    if (f.getName().toLowerCase().contains(part.toLowerCase())) {
                        wanted = true;
                    }
                }
                if (wanted) {
                    out.add(f.getName() + ":" + f.getType().getSimpleName());
                }
            }
        } catch (Throwable t) {
            out.add("KLASSE FEHLT: " + t.getClass().getSimpleName());
        }
        java.util.Collections.sort(out);
        return out;
    }

    @Test
    void reportSoundAndParticleApi() {
        probe("ServerLevel.sound/particles",
                methods("net.minecraft.server.level.ServerLevel", "playSound", "sendParticles", "playSeededSound", "levelEvent"));
        probe("ServerPlayer.sound",
                methods("net.minecraft.server.level.ServerPlayer", "playNotifySound", "playSound"));
        probe("SoundSource", fields("net.minecraft.sounds.SoundSource"));
        probe("SoundEvents.horn/raid",
                fields("net.minecraft.sounds.SoundEvents", "HORN", "RAID", "GOAT"));
        probe("SoundEvents.ominous/thunder/wither",
                fields("net.minecraft.sounds.SoundEvents", "THUNDER", "WITHER", "OMINOUS", "TRIAL"));
        probe("SoundEvents.bell/beacon/portal/roar",
                fields("net.minecraft.sounds.SoundEvents", "BELL_", "BEACON", "PORTAL", "ROAR", "GROWL"));
        probe("ParticleTypes", fields("net.minecraft.core.particles.ParticleTypes"));
        probe("DustParticleOptions",
                methods("net.minecraft.core.particles.DustParticleOptions"));
        probe("DustParticleOptions ctors", List.of(
                Arrays.toString(safeCtors("net.minecraft.core.particles.DustParticleOptions"))));
        probe("Scoreboard/PlayerTeam",
                methods("net.minecraft.world.scores.Scoreboard", "Team", "addPlayer"));
        probe("PlayerTeam.color",
                methods("net.minecraft.world.scores.PlayerTeam", "setColor", "setPlayerPrefix"));
    }

    private static Object[] safeCtors(String className) {
        try {
            Class<?> c = load(className);
            List<String> out = new ArrayList<>();
            for (var ctor : c.getConstructors()) {
                StringBuilder sb = new StringBuilder("<init>(");
                Class<?>[] ps = ctor.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(ps[i].getSimpleName());
                }
                out.add(sb.append(')').toString());
            }
            return out.toArray();
        } catch (Throwable t) {
            return new Object[]{"FEHLT"};
        }
    }
}
