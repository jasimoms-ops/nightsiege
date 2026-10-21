package com.mki.nightsiege.game;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.AmbientParticle;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.BooleanModifier;
import net.minecraft.world.attribute.modifier.ColorModifier;
import net.minecraft.world.attribute.modifier.FloatModifier;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The look and feel of an event: sky, fog, light, stars and ambient particles,
 * plus the few gameplay values that go with it (no burning undead during an
 * eclipse, no zombifying piglins at a rift).
 *
 * <p>Minecraft builds sky and light from layered environment attributes. Every
 * level gets one extra layer here that blends the active event in, fading over
 * a few seconds. The client keeps its own state (set by the server through a
 * message); the server keeps one per dimension.</p>
 */
public final class EventAtmosphere {

    public static final String BLOOD_MOON = "BLOOD_MOON";
    public static final String ECLIPSE = "ECLIPSE";
    public static final String FOG = "FOG";
    public static final String THUNDER = "THUNDER";
    public static final String NETHER_RIFT = "NETHER_RIFT";
    public static final String FROST = "FROST";
    public static final String AURORA = "AURORA";

    /** What one side currently shows, with a fade between 0 and 1. */
    public static final class State {
        volatile String event = "";
        volatile String shown = "";
        volatile float strength;
        volatile float speed = 0.01f;

        public String shown() {
            return shown;
        }

        public float strength() {
            return strength;
        }

        /** Moves the fade one tick on; a new event first fades the old one out. */
        void tick() {
            String wanted = event;
            if (!wanted.equals(shown)) {
                if (strength > 0f && !shown.isEmpty()) {
                    strength = Math.max(0f, strength - 0.05f);
                    return;
                }
                shown = wanted;
                strength = 0f;
            }
            float target = shown.isEmpty() ? 0f : 1f;
            if (strength < target) {
                strength = Math.min(target, strength + speed);
            } else if (strength > target) {
                strength = Math.max(target, strength - 0.005f);
            }
        }

        void set(String wanted, int fadeTicks) {
            event = wanted == null ? "" : wanted;
            speed = 1f / Math.max(1, fadeTicks);
        }
    }

    private static final State CLIENT = new State();
    private static final Map<String, State> SERVER = new ConcurrentHashMap<>();
    private static final Map<String, EnvironmentAttributeMap> LOOKS = new HashMap<>();
    private static final Set<EnvironmentAttribute<?>> TOUCHED = new HashSet<>();

    static {
        LOOKS.put(ECLIPSE, EnvironmentAttributeMap.builder()
                .modify(EnvironmentAttributes.SKY_COLOR, ColorModifier.MULTIPLY_RGB, rgb(0x120C1E))
                .modify(EnvironmentAttributes.FOG_COLOR, ColorModifier.MULTIPLY_RGB, rgb(0x1C1628))
                .modify(EnvironmentAttributes.CLOUD_COLOR, ColorModifier.MULTIPLY_ARGB, ARGB.color(255, 0x34, 0x30, 0x44))
                .modify(EnvironmentAttributes.SKY_LIGHT_LEVEL, FloatModifier.MULTIPLY, 0.27f)
                .modify(EnvironmentAttributes.SKY_LIGHT_FACTOR, FloatModifier.MULTIPLY, 0.22f)
                .modify(EnvironmentAttributes.SKY_LIGHT_COLOR, ColorModifier.MULTIPLY_RGB, rgb(0x9A80FF))
                .modify(EnvironmentAttributes.STAR_BRIGHTNESS, FloatModifier.MAXIMUM, 0.6f)
                .modify(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, ColorModifier.MULTIPLY_ARGB, 0)
                .modify(EnvironmentAttributes.MONSTERS_BURN, BooleanModifier.AND, false)
                .build());
        LOOKS.put(BLOOD_MOON, EnvironmentAttributeMap.builder()
                .modify(EnvironmentAttributes.SKY_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(150, 0x48, 0x04, 0x08))
                .modify(EnvironmentAttributes.FOG_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(170, 0x52, 0x0C, 0x10))
                .modify(EnvironmentAttributes.CLOUD_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(150, 0x60, 0x18, 0x18))
                .modify(EnvironmentAttributes.SKY_LIGHT_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(140, 0xFF, 0x58, 0x58))
                .modify(EnvironmentAttributes.STAR_BRIGHTNESS, FloatModifier.MULTIPLY, 0.3f)
                .build());
        LOOKS.put(FOG, EnvironmentAttributeMap.builder()
                .modify(EnvironmentAttributes.FOG_START_DISTANCE, FloatModifier.MINIMUM, 1.0f)
                .modify(EnvironmentAttributes.FOG_END_DISTANCE, FloatModifier.MINIMUM, 22.0f)
                .modify(EnvironmentAttributes.SKY_FOG_END_DISTANCE, FloatModifier.MINIMUM, 26.0f)
                .modify(EnvironmentAttributes.CLOUD_FOG_END_DISTANCE, FloatModifier.MINIMUM, 40.0f)
                .modify(EnvironmentAttributes.FOG_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(180, 0x2C, 0x30, 0x34))
                .modify(EnvironmentAttributes.SKY_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(190, 0x28, 0x2C, 0x30))
                .modify(EnvironmentAttributes.STAR_BRIGHTNESS, FloatModifier.MULTIPLY, 0.0f)
                .build());
        LOOKS.put(THUNDER, EnvironmentAttributeMap.builder()
                .modify(EnvironmentAttributes.SKY_LIGHT_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(70, 0x90, 0xB8, 0xFF))
                .modify(EnvironmentAttributes.CLOUD_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(120, 0x20, 0x24, 0x30))
                .build());
        LOOKS.put(NETHER_RIFT, EnvironmentAttributeMap.builder()
                .modify(EnvironmentAttributes.SKY_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(190, 0x40, 0x0E, 0x22))
                .modify(EnvironmentAttributes.FOG_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(190, 0x4C, 0x12, 0x16))
                .modify(EnvironmentAttributes.FOG_END_DISTANCE, FloatModifier.MINIMUM, 96.0f)
                .modify(EnvironmentAttributes.CLOUD_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(170, 0x60, 0x1C, 0x20))
                .modify(EnvironmentAttributes.SKY_LIGHT_COLOR, ColorModifier.MULTIPLY_RGB, rgb(0xFF9070))
                .modify(EnvironmentAttributes.STAR_BRIGHTNESS, FloatModifier.MULTIPLY, 0.0f)
                .set(EnvironmentAttributes.AMBIENT_PARTICLES, List.of(
                        new AmbientParticle(ParticleTypes.ASH, 0.12f),
                        new AmbientParticle(ParticleTypes.WHITE_ASH, 0.03f)))
                .modify(EnvironmentAttributes.PIGLINS_ZOMBIFY, BooleanModifier.AND, false)
                .build());
        LOOKS.put(FROST, EnvironmentAttributeMap.builder()
                .modify(EnvironmentAttributes.FOG_START_DISTANCE, FloatModifier.MINIMUM, 4.0f)
                .modify(EnvironmentAttributes.FOG_END_DISTANCE, FloatModifier.MINIMUM, 52.0f)
                .modify(EnvironmentAttributes.FOG_COLOR, ColorModifier.MULTIPLY_RGB, rgb(0xA4B4CC))
                .modify(EnvironmentAttributes.SKY_COLOR, ColorModifier.MULTIPLY_RGB, rgb(0x94A4BC))
                .modify(EnvironmentAttributes.CLOUD_COLOR, ColorModifier.MULTIPLY_ARGB, ARGB.color(255, 0xC4, 0xD0, 0xE4))
                .modify(EnvironmentAttributes.SKY_LIGHT_COLOR, ColorModifier.MULTIPLY_RGB, rgb(0xB4C8F0))
                .modify(EnvironmentAttributes.STAR_BRIGHTNESS, FloatModifier.MULTIPLY, 0.0f)
                .set(EnvironmentAttributes.AMBIENT_PARTICLES, List.of(
                        new AmbientParticle(ParticleTypes.SNOWFLAKE, 0.22f)))
                .build());
        LOOKS.put(AURORA, EnvironmentAttributeMap.builder()
                .modify(EnvironmentAttributes.SKY_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(60, 0x10, 0x40, 0x30))
                .modify(EnvironmentAttributes.STAR_BRIGHTNESS, FloatModifier.MAXIMUM, 0.85f)
                .modify(EnvironmentAttributes.SKY_LIGHT_COLOR, ColorModifier.ALPHA_BLEND, ARGB.color(50, 0x60, 0xFF, 0xB0))
                .build());
        for (EnvironmentAttributeMap map : LOOKS.values()) {
            TOUCHED.addAll(map.keySet());
        }
        TOUCHED.add(EnvironmentAttributes.SUN_ANGLE);
        TOUCHED.add(EnvironmentAttributes.MOON_ANGLE);
        TOUCHED.add(EnvironmentAttributes.MOON_PHASE);
    }

    private EventAtmosphere() {
    }

    private static int rgb(int value) {
        return 0xFF000000 | value;
    }

    /** Called for every level when its attribute layers are built: one more layer on top. */
    public static void addLayers(EnvironmentAttributeSystem.Builder builder, Level level) {
        for (EnvironmentAttribute<?> attribute : TOUCHED) {
            addLayer(builder, level, attribute);
        }
    }

    /**
     * Shader state on the client: 0 none, 1 another pack, 2 the Night Siege pack
     * (see ShaderSupport). The Night Siege pack draws every event itself. Other
     * packs keep their own sky; only the eclipse is shown there, by moving the
     * pack's sun below the horizon and its new moon in front of where it was.
     */
    public static volatile int clientShaderMode;
    /** Sun and moon angle before any event changed them. */
    public static volatile float realSunAngle;
    public static volatile float realMoonAngle;

    @SuppressWarnings("unchecked")
    private static <V> V celestial(EnvironmentAttribute<V> attribute, V result, String shown, float strength) {
        if (attribute == EnvironmentAttributes.SUN_ANGLE) {
            realSunAngle = (Float) result;
        } else if (attribute == EnvironmentAttributes.MOON_ANGLE) {
            realMoonAngle = (Float) result;
        }
        if (clientShaderMode != 1 || strength < 0.5f || !ECLIPSE.equals(shown)) {
            return result;
        }
        if (attribute == EnvironmentAttributes.SUN_ANGLE) {
            return (V) Float.valueOf((((Float) result) + 180f) % 360f);
        }
        if (attribute == EnvironmentAttributes.MOON_ANGLE) {
            return (V) Float.valueOf(realSunAngle);
        }
        if (attribute == EnvironmentAttributes.MOON_PHASE) {
            return (V) net.minecraft.world.level.MoonPhase.NEW_MOON;
        }
        return result;
    }

    private static boolean isCelestial(EnvironmentAttribute<?> attribute) {
        return attribute == EnvironmentAttributes.SUN_ANGLE || attribute == EnvironmentAttributes.MOON_ANGLE
                || attribute == EnvironmentAttributes.MOON_PHASE;
    }

    private static <V> void addLayer(EnvironmentAttributeSystem.Builder builder, Level level, EnvironmentAttribute<V> attribute) {
        boolean celestial = isCelestial(attribute);
        builder.addTimeBasedLayer(attribute, (result, cacheTickId) -> {
            State state = stateFor(level);
            if (state == null) {
                return result;
            }
            float strength = state.strength;
            String shown = state.shown;
            if (celestial) {
                return level.isClientSide() ? celestial(attribute, result, shown, strength) : result;
            }
            if (strength <= 0.001f || shown.isEmpty()) {
                return result;
            }
            EnvironmentAttributeMap look = LOOKS.get(shown);
            if (look == null || !look.contains(attribute)) {
                return result;
            }
            V changed = look.applyModifier(attribute, result);
            return attribute.type().stateChangeLerp().apply(Math.min(1f, strength), result, changed);
        });
    }

    private static State stateFor(Level level) {
        if (level.isClientSide()) {
            return CLIENT;
        }
        return SERVER.get(SiegeManager.dimensionName(level));
    }

    // --- server

    /** Starts (or, with an empty event, ends) the look of an event in one dimension. */
    public static void setServer(String dimension, String event, int fadeTicks) {
        SERVER.computeIfAbsent(dimension, d -> new State()).set(event, fadeTicks);
    }

    /** The event a dimension shows, or an empty string. */
    public static String serverEvent(String dimension) {
        State state = SERVER.get(dimension);
        return state == null ? "" : state.event;
    }

    public static float serverStrength(String dimension, String event) {
        State state = SERVER.get(dimension);
        return state == null || !state.shown.equals(event) ? 0f : state.strength;
    }

    public static void tickServer() {
        for (State state : SERVER.values()) {
            state.tick();
        }
    }

    public static void resetServer() {
        SERVER.clear();
    }

    // --- client

    public static void setClient(String event, int fadeTicks) {
        CLIENT.set(event, fadeTicks);
    }

    public static void tickClient() {
        CLIENT.tick();
    }

    public static void resetClient() {
        CLIENT.event = "";
        CLIENT.shown = "";
        CLIENT.strength = 0f;
    }

    public static State client() {
        return CLIENT;
    }

    /** Strength of an event on the client right now, 0 if another one is shown. */
    public static float clientStrength(String event) {
        return CLIENT.shown.equals(event) ? CLIENT.strength : 0f;
    }

    /** How many ticks an event takes to fade in. The eclipse creeps in slowly. */
    public static int fadeTicks(String event) {
        return ECLIPSE.equals(event) ? 600 : 160;
    }
}
