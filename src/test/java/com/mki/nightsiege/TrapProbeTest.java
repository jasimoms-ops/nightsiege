package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Temporary: reports the damage, effect and entity API of this Minecraft version. */
class TrapProbeTest {

    private static Class<?> load(String n) {
        try {
            return Class.forName(n, false, TrapProbeTest.class.getClassLoader());
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
            if (Modifier.isStatic(m.getModifiers()) && parts.length > 0 && parts[0].equals("!")) continue;
            boolean hit = false;
            for (String p : parts) hit |= m.getName().equals(p);
            if (!hit) continue;
            sb.append(m.getName()).append('(');
            for (Class<?> p : m.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
            sb.append(")").append(m.getReturnType().getSimpleName()).append(' ');
        }
        System.out.println("PROBE: " + c.getSimpleName() + " " + sb);
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
                sb.append(name).append(':').append(f.getGenericType().getTypeName().replaceAll("[a-z]+\\.", "")).append(' ');
            } catch (Throwable t) {
                sb.append(name).append(":NONE ");
            }
        }
        System.out.println("PROBE: F " + c.getSimpleName() + " " + sb);
    }

    @Test
    void probe() {
        dump("net.minecraft.world.entity.LivingEntity", "hurtServer", "hurt", "addEffect", "knockback", "push", "hurtOrSimulate");
        dump("net.minecraft.world.entity.Entity", "hurtServer", "push", "setDeltaMovement", "level", "hasLineOfSight");
        dump("net.minecraft.world.entity.Mob", "getNavigation", "getTarget", "hasLineOfSight");
        dump("net.minecraft.world.damagesource.DamageSources", "playerAttack", "magic", "generic", "explosion", "thorns", "arrow", "trident", "cactus", "sweetBerryBush", "mobAttack");
        dump("net.minecraft.world.level.Level", "getEntitiesOfClass", "isLoaded", "hasChunkAt", "removeBlock", "getBlockState", "explode", "isLoaded");
        dump("net.minecraft.world.entity.player.Inventory", "add", "placeItemBackInInventory");
        dump("net.minecraft.world.effect.MobEffectInstance", "getDuration");
        Class<?> ei = load("net.minecraft.world.effect.MobEffectInstance");
        if (ei != null) for (var ct : ei.getConstructors()) {
            StringBuilder sb = new StringBuilder("PROBE: EI <init>(");
            for (Class<?> p : ct.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
            System.out.println(sb + ")");
        }
        System.out.println("PROBE: Enemy " + (load("net.minecraft.world.entity.monster.Enemy") != null));
        fields("net.minecraft.world.effect.MobEffects", "SLOWNESS", "MOVEMENT_SLOWDOWN", "GLOWING", "WEAKNESS");
        fields("net.minecraft.sounds.SoundEvents", "GENERIC_EXPLODE", "ANVIL_LAND", "DISPENSER_LAUNCH", "ARROW_SHOOT",
                "COBWEB_PLACE", "CHAIN_PLACE", "TRIPWIRE_CLICK_ON", "PISTON_EXTEND", "IRON_TRAPDOOR_CLOSE", "SWEET_BERRY_BUSH_BREAK", "ITEM_PICKUP", "DISPENSER_DISPENSE");
        fields("net.minecraft.core.particles.ParticleTypes", "CRIT", "EXPLOSION", "EXPLOSION_EMITTER", "ITEM_COBWEB", "SMOKE", "DAMAGE_INDICATOR", "SNOWFLAKE", "POOF");
        dump("net.minecraft.world.entity.projectile.arrow.AbstractArrow", "shoot", "setOwner");
    }
}
