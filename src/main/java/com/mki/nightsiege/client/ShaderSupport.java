package com.mki.nightsiege.client;

import com.mki.nightsiege.game.EventAtmosphere;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Knows whether Iris runs a shader pack, and whether it is the Night Siege
 * pack, which draws every event itself (fog, sky, moons, eclipse, aurora).
 * Other packs only get the world effects (particles, lightning, monster looks).
 */
public final class ShaderSupport {

    public static final int NONE = 0;
    public static final int FOREIGN = 1;
    public static final int NIGHT_SIEGE = 2;

    private static boolean checked;
    private static Object irisApi;
    private static Method inUse;
    private static Method packName;

    private ShaderSupport() {
    }

    private static void init() {
        if (checked) {
            return;
        }
        checked = true;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApi = api.getMethod("getInstance").invoke(null);
            inUse = api.getMethod("isShaderPackInUse");
        } catch (Throwable t) {
            irisApi = null;
            inUse = null;
        }
        try {
            packName = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName");
        } catch (Throwable t) {
            packName = null;
        }
    }

    /** NONE, FOREIGN or NIGHT_SIEGE. */
    public static int mode() {
        init();
        if (irisApi == null || inUse == null) {
            return NONE;
        }
        try {
            if (!(Boolean) inUse.invoke(irisApi)) {
                return NONE;
            }
        } catch (Throwable t) {
            return NONE;
        }
        return isNightSiegePack(currentPack()) ? NIGHT_SIEGE : FOREIGN;
    }

    public static String currentPack() {
        init();
        if (packName == null) {
            return "";
        }
        try {
            Object name = packName.invoke(null);
            return name == null ? "" : name.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public static boolean isNightSiegePack(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
        return n.contains("nightsiege");
    }

    // ------------------------------------------------------------------
    // values for the uniforms ns_event and ns_strength
    // ------------------------------------------------------------------

    /** Number of the shown event, the same numbers as in the pack's nightsiege.glsl. */
    public static int eventId() {
        return switch (EventAtmosphere.client().shown()) {
            case EventAtmosphere.BLOOD_MOON -> 1;
            case EventAtmosphere.ECLIPSE -> 2;
            case EventAtmosphere.FOG -> 3;
            case EventAtmosphere.THUNDER -> 4;
            case EventAtmosphere.NETHER_RIFT -> 5;
            case EventAtmosphere.FROST -> 6;
            case EventAtmosphere.AURORA -> 7;
            default -> 0;
        };
    }

    public static float strength() {
        return Math.max(0f, Math.min(1f, EventAtmosphere.client().strength()));
    }
}
