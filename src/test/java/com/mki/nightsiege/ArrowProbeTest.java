package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/** Temporary: reports the arrow, bow and cooldown API of this Minecraft version. */
class ArrowProbeTest {

    private static Class<?> load(String n) {
        try {
            return Class.forName(n, false, ArrowProbeTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static void dump(String n, String... parts) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Method m : c.getMethods()) {
            boolean hit = parts.length == 0;
            for (String p : parts) {
                hit |= m.getName().toLowerCase().contains(p.toLowerCase());
            }
            if (!hit) continue;
            sb.append(m.getName()).append('(');
            for (Class<?> p : m.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
            sb.append(")").append(m.getReturnType().getSimpleName()).append(' ');
        }
        for (var ct : c.getConstructors()) {
            sb.append("<init>(");
            for (Class<?> p : ct.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
            sb.append(") ");
        }
        System.out.println("PROBE: " + c.getSimpleName() + " " + sb);
    }

    @Test
    void probe() {
        for (String n : new String[]{
                "net.minecraft.world.entity.projectile.AbstractArrow",
                "net.minecraft.world.entity.projectile.arrow.AbstractArrow",
                "net.minecraft.world.entity.projectile.Arrow",
                "net.minecraft.world.entity.projectile.arrow.Arrow"}) {
            dump(n, "damage", "crit", "owner", "pickup", "weapon", "pierce", "knockback", "shoot");
        }
        dump("net.minecraft.world.item.ItemCooldowns", "cooldown");
        dump("net.minecraft.world.item.BowItem", "power", "release", "duration", "shoot");
        dump("net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents");
        dump("net.minecraft.world.entity.projectile.Projectile", "owner", "shoot");
        Class<?> a = load("net.minecraft.world.entity.projectile.arrow.AbstractArrow");
        if (a == null) a = load("net.minecraft.world.entity.projectile.AbstractArrow");
        if (a != null) {
            for (var f : a.getFields()) System.out.println("PROBE: field " + f.getName() + ":" + f.getType().getSimpleName());
            for (var inner : a.getClasses()) System.out.println("PROBE: inner " + inner.getName());
        }
    }
}
