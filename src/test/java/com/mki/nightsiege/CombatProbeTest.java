package com.mki.nightsiege;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Temporary: reports combat event and entity API of this version. */
class CombatProbeTest {

    private static Class<?> load(String n) {
        try {
            return Class.forName(n, false, CombatProbeTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sig(Method m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        for (Class<?> p : m.getParameterTypes()) sb.append(p.getSimpleName()).append(',');
        return sb.append(')').append(m.getReturnType().getSimpleName()).toString();
    }

    private static void dump(String n, String... names) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Method m : c.getMethods()) {
            for (String p : names) if (m.getName().equals(p)) sb.append(sig(m)).append(' ');
        }
        System.out.println("PROBE: " + c.getSimpleName() + " " + sb);
    }

    /** Static fields of an events class, plus the single method of each field's callback type. */
    private static void events(String n) {
        Class<?> c = load(n);
        if (c == null) {
            System.out.println("PROBE: missing " + n);
            return;
        }
        for (var f : c.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            String gen = f.getGenericType().getTypeName();
            String cb = gen.replaceAll(".*<(.*)>.*", "$1");
            Class<?> cbc = load(cb);
            String m = "";
            if (cbc != null) for (Method x : cbc.getMethods()) if (Modifier.isAbstract(x.getModifiers())) m = sig(x);
            System.out.println("PROBE: EV " + c.getSimpleName() + "." + f.getName() + " " + m);
        }
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
        events("net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents");
        events("net.fabricmc.fabric.api.event.player.AttackEntityCallback");
        dump("net.fabricmc.fabric.api.event.player.AttackEntityCallback", "interact");
        dump("net.minecraft.world.entity.Entity", "igniteForSeconds", "setRemainingFireTicks", "isShiftKeyDown", "getLookAngle", "getType", "isInvertedHealAndHarm");
        dump("net.minecraft.world.entity.LivingEntity", "heal", "getMaxHealth", "isBlocking", "knockback", "setHealth");
        dump("net.minecraft.world.entity.Mob", "setTarget");
        fields("net.minecraft.world.entity.Entity", "invulnerableTime");
        fields("net.minecraft.world.entity.ai.attributes.Attributes", "KNOCKBACK_RESISTANCE", "ARMOR_TOUGHNESS");
        fields("net.minecraft.world.effect.MobEffects", "POISON", "WITHER", "WEAKNESS", "RESISTANCE", "REGENERATION", "SLOWNESS");
        fields("net.minecraft.tags.EntityTypeTags", "UNDEAD");
        fields("net.minecraft.core.particles.ParticleTypes", "ELECTRIC_SPARK", "FLAME", "HEART", "SWEEP_ATTACK", "ANGRY_VILLAGER", "TOTEM_OF_UNDYING", "LAVA", "SMALL_FLAME", "GLOW");
        fields("net.minecraft.sounds.SoundEvents", "PLAYER_ATTACK_SWEEP", "PLAYER_ATTACK_CRIT", "SHIELD_BLOCK", "TOTEM_USE", "FIRECHARGE_USE", "BREWING_STAND_BREW",
                "BEACON_POWER_SELECT", "TRIDENT_THUNDER", "LIGHTNING_BOLT_IMPACT", "EVOKER_PREPARE_ATTACK", "RAVAGER_ROAR", "GOAT_HORN_SOUND_VARIANTS", "PLAYER_LEVELUP", "BLAZE_SHOOT", "GENERIC_EXTINGUISH_FIRE", "AMETHYST_BLOCK_CHIME", "WARDEN_SONIC_BOOM", "ANVIL_LAND", "IRON_GOLEM_ATTACK");
    }
}
