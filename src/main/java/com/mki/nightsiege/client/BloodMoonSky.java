package com.mki.nightsiege.client;

import com.mki.nightsiege.game.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector4f;

/**
 * The look of a blood moon night on this client.
 *
 * <p>The server only says whether a blood moon is up. Everything visible is
 * decided here: a large glowing red moon instead of the normal one, a crimson
 * sky with reddish stars and a red haze towards the horizon. All of it fades in
 * and out over a few seconds, so the switch never pops.</p>
 *
 * <p>The values are read by two mixins: the sky renderer asks for the moon and
 * the star colour, and the camera's environment probe runs its sky colour, fog
 * colour, fog distances and star brightness through {@link #adjust}.</p>
 */
public final class BloodMoonSky {

    /** The moon picture in the celestial atlas: textures/environment/celestial/blood_moon.png. */
    public static final Identifier MOON_SPRITE = ModContent.id("blood_moon");

    /** The blood moon quad is this much larger than the normal moon; the picture includes its glow. */
    private static final float MOON_SCALE = 2.1f;
    /** Seconds to fade in or out, in ticks. */
    private static final int FADE_TICKS = 160;
    /** The server repeats its message every two seconds; after six in game time without one, the moon sets. */
    private static final long STALE_TICKS = 120L;

    // Night colours: a dark crimson overhead that glows redder towards the
    // horizon. The tint keeps the brightness of the vanilla colour, so a blood
    // moon running into dawn turns the sky orange-red instead of black.
    private static final float[] SKY_BASE = {0.10f, 0.006f, 0.012f};
    private static final float[] FOG_BASE = {0.27f, 0.022f, 0.028f};
    private static final float TINT_GAIN = 1.4f;

    /** Fog starts and ends this close at full strength: a red haze, the view stays usable. */
    private static final float FOG_START = 18f;
    private static final float FOG_END = 176f;

    private static boolean announced;
    private static long heardAt = Long.MIN_VALUE;
    private static float level;

    private BloodMoonSky() {
    }

    /** Called with every message from the server. */
    public static void onServerState(boolean active) {
        announced = active;
        Minecraft mc = Minecraft.getInstance();
        heardAt = mc.level != null ? mc.level.getGameTime() : Long.MIN_VALUE;
    }

    /** Once per client tick: moves the fade towards on or off. */
    public static void tick(Minecraft mc) {
        boolean show = false;
        if (announced && mc.level != null) {
            long now = mc.level.getGameTime();
            // Game time stands still while the game is paused, so pausing never sets the moon.
            show = heardAt != Long.MIN_VALUE && now - heardAt <= STALE_TICKS && now >= heardAt;
        }
        float step = 1f / FADE_TICKS;
        level = show ? Math.min(1f, level + step) : Math.max(0f, level - step);
    }

    /** Leaving a world drops everything at once. */
    public static void reset() {
        announced = false;
        heardAt = Long.MIN_VALUE;
        level = 0f;
    }

    /** 0 for a normal night, 1 for a full blood moon, eased. */
    public static float intensity() {
        float t = level;
        return t * t * (3f - 2f * t);
    }

    private static MoonPhase firstPhase;

    /**
     * The phase drawn from the start of a moon buffer. The blood moon buffer holds
     * a single quad, so it is always drawn as this phase.
     */
    public static MoonPhase firstPhase() {
        MoonPhase found = firstPhase;
        if (found == null) {
            found = MoonPhase.FULL_MOON;
            for (MoonPhase phase : MoonPhase.values()) {
                if (phase.index() == 0) {
                    found = phase;
                    break;
                }
            }
            firstPhase = found;
        }
        return found;
    }

    /** Size of the blood moon quad relative to the normal moon; it swells while fading in. */
    public static float moonScale(float intensity) {
        return 1f + (MOON_SCALE - 1f) * intensity;
    }

    /** Stars burn red; the colour arrives as brightness in all four channels. */
    public static void tintStars(Vector4f color) {
        float i = intensity();
        if (i <= 0f) {
            return;
        }
        color.y *= 1f - 0.58f * i;
        color.z *= 1f - 0.62f * i;
    }

    /** Runs one environment value of the camera through the blood moon look. */
    public static Object adjust(EnvironmentAttribute<?> attribute, Object value) {
        float i = intensity();
        if (i <= 0f || value == null) {
            return value;
        }
        if (attribute == EnvironmentAttributes.SKY_COLOR && value instanceof Integer c) {
            return tint(c, SKY_BASE, i);
        }
        if (attribute == EnvironmentAttributes.FOG_COLOR && value instanceof Integer c) {
            return tint(c, FOG_BASE, i);
        }
        if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS && value instanceof Float f) {
            return Math.min(1f, f * (1f + 0.3f * i));
        }
        if (attribute == EnvironmentAttributes.FOG_START_DISTANCE && value instanceof Float f) {
            return Math.min(f, lerp(f, FOG_START, i));
        }
        if (attribute == EnvironmentAttributes.FOG_END_DISTANCE && value instanceof Float f) {
            return Math.min(f, lerp(f, FOG_END, i));
        }
        return value;
    }

    private static int tint(int argb, float[] base, float i) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        float tr = Math.min(1f, base[0] + lum * TINT_GAIN);
        float tg = Math.min(1f, base[1] + lum * TINT_GAIN * 0.22f);
        float tb = Math.min(1f, base[2] + lum * TINT_GAIN * 0.20f);
        int nr = Math.round(lerp(r, tr, i) * 255f);
        int ng = Math.round(lerp(g, tg, i) * 255f);
        int nb = Math.round(lerp(b, tb, i) * 255f);
        return (argb & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
