package com.mki.nightsiege.client;

import com.mki.nightsiege.game.EventAtmosphere;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * The event look for players with a shader pack.
 *
 * <p>Shader packs draw their own sky, sun, moon and fog, so the colours the mod
 * puts into Minecraft's sky never reach the screen. This layer is drawn on top
 * of the finished picture instead, where no shader can touch it: a colour filter
 * with a vignette, the event's moon or the eclipsed sun at their place in the
 * sky (hidden when terrain is in the way) and the aurora.</p>
 */
public final class EventOverlay {

    private static final Identifier VIGNETTE = tex("vignette");
    private static final Identifier AURORA = tex("aurora");

    /** Colour filter, vignette colour and the body in the sky (moon or sun). */
    private record Look(int tint, int vignette, Identifier body, boolean sun, float size) {
    }

    private static final Map<String, Look> LOOKS = new HashMap<>();

    static {
        // Dark, see-through tints: they colour the picture without lifting it.
        // Only the blood moon and the eclipse keep a thin dark edge.
        LOOKS.put(EventAtmosphere.BLOOD_MOON, new Look(0x40700810, 0x70300004, tex("blood_moon"), false, 0.24f));
        LOOKS.put(EventAtmosphere.ECLIPSE, new Look(0x30100818, 0x80000000, tex("eclipse_sun"), true, 0.30f));
        LOOKS.put(EventAtmosphere.FOG, new Look(0x30303438, 0, tex("fog_moon"), false, 0.24f));
        LOOKS.put(EventAtmosphere.FROST, new Look(0x1E10203A, 0, tex("frost_moon"), false, 0.24f));
        LOOKS.put(EventAtmosphere.NETHER_RIFT, new Look(0x34501008, 0, tex("rift_moon"), false, 0.24f));
        LOOKS.put(EventAtmosphere.THUNDER, new Look(0x24182030, 0, null, false, 0f));
        LOOKS.put(EventAtmosphere.AURORA, new Look(0x0C103828, 0, null, false, 0f));
    }

    private static boolean irisChecked;
    private static Object irisApi;
    private static Method irisInUse;
    private static Method irisSunPath;

    private static int frame;
    private static boolean bodyVisible = true;
    private static final boolean[] auroraVisibleStrips = new boolean[96];

    private EventOverlay() {
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("nightsiege", "textures/gui/event/" + name + ".png");
    }

    /** True while Iris runs a shader pack. Without one, the sky itself shows the event. */
    public static boolean shadersActive() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
                try {
                    irisSunPath = api.getMethod("getSunPathRotation");
                } catch (NoSuchMethodException e) {
                    irisSunPath = null;
                }
            } catch (Throwable t) {
                irisApi = null;
                irisInUse = null;
            }
        }
        if (irisApi == null || irisInUse == null) {
            return false;
        }
        try {
            return (Boolean) irisInUse.invoke(irisApi);
        } catch (Throwable t) {
            return false;
        }
    }

    /** How far the shader tilts the path of sun and moon (0 without a shader). */
    private static float sunPathRotation() {
        if (irisSunPath == null || irisApi == null || forced()) {
            return 0f;
        }
        try {
            return (Float) irisSunPath.invoke(irisApi);
        } catch (Throwable t) {
            return 0f;
        }
    }

    /**
     * Direction of the sun (or moon) for an angle, the same way Iris places it:
     * turned by -90 degrees, tilted by the shader's sun path, then along the day.
     */
    private static Vec3 celestialDirection(float angle) {
        org.joml.Vector4f v = new org.joml.Matrix4f()
                .rotate(com.mojang.math.Axis.YP.rotationDegrees(-90.0F))
                .rotate(com.mojang.math.Axis.ZP.rotationDegrees(sunPathRotation()))
                .rotate(com.mojang.math.Axis.XP.rotationDegrees(angle))
                .transform(new org.joml.Vector4f(0f, 1f, 0f, 0f));
        return new Vec3(v.x(), v.y(), v.z()).normalize();
    }

    /** For the automatic client test: draws the layer even without a shader pack. */
    private static boolean forced() {
        return Boolean.getBoolean("nightsiege.overlay");
    }

    public static void draw(GuiGraphicsExtractor gui, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        EventAtmosphere.State state = EventAtmosphere.client();
        float s = Math.min(1f, state.strength());
        Look look = LOOKS.get(state.shown());
        if (s <= 0.01f || look == null || !(forced() || shadersActive())) {
            return;
        }
        frame++;
        int w = gui.guiWidth();
        int h = gui.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Camera camera = mc.gameRenderer.mainCamera();

        if (look.tint() != 0) {
            gui.fill(0, 0, w, h, scaleAlpha(look.tint(), s));
        }
        if (look.body() != null) {
            // Read the angles first (that refreshes them), then use the untouched ones.
            camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partial);
            camera.attributeProbe().getValue(EnvironmentAttributes.MOON_ANGLE, partial);
            float angle = look.sun() ? EventAtmosphere.realSunAngle : EventAtmosphere.realMoonAngle;
            Vec3 dir = celestialDirection(angle);
            if (frame % 4 == 0) {
                bodyVisible = clearSky(mc, camera, dir);
            }
            if (bodyVisible && dir.y > -0.08) {
                drawBody(gui, mc, camera, dir, look, w, h, s);
            }
        }
        if (EventAtmosphere.AURORA.equals(state.shown())) {
            drawAurora(gui, mc, camera, w, h, s);
        }
        if (look.vignette() != 0) {
            gui.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, w, h, 256, 256, 256, 256,
                    scaleAlpha(look.vignette(), s));
        }
    }

    private static void drawBody(GuiGraphicsExtractor gui, Minecraft mc, Camera camera, Vec3 dir, Look look,
                                 int w, int h, float s) {
        float[] p = project(dir, camera, mc, w, h);
        if (p == null) {
            return;
        }
        // The sprite covers the body and its glow, like the vanilla sky quads.
        int half = Math.max(4, Math.round(p[2] * look.size()));
        int size = half * 2;
        int alpha = Math.round(255 * s);
        gui.blit(RenderPipelines.GUI_TEXTURED, look.body(), Math.round(p[0]) - half, Math.round(p[1]) - half,
                0f, 0f, size, size, 64, 64, 64, 64, (alpha << 24) | 0xFFFFFF);
    }

    private static final int AURORA_STRIPS = 96;
    private static final boolean[] auroraVisible2 = new boolean[AURORA_STRIPS];

    /**
     * The aurora as upright strips across the northern sky. Two curtains, each
     * rippling: the hem rises and falls in slow waves, bright bands run along it
     * and the rays drift sideways.
     */
    private static void drawAurora(GuiGraphicsExtractor gui, Minecraft mc, Camera camera, int w, int h, float s) {
        double t = (System.currentTimeMillis() % 3600000L) / 1000.0;
        curtain(gui, mc, camera, w, h, s, t, 0.0, 1.0f, 0);
        curtain(gui, mc, camera, w, h, s * 0.65f, t + 40.0, 12.0, 1.12f, 1);
    }

    private static void curtain(GuiGraphicsExtractor gui, Minecraft mc, Camera camera, int w, int h, float s,
                                double t, double turn, float tall, int layer) {
        for (int i = 0; i < AURORA_STRIPS; i++) {
            double f0 = i / (double) AURORA_STRIPS;
            double f1 = (i + 1) / (double) AURORA_STRIPS;
            double a0 = Math.toRadians(-80 + 160.0 * f0 + turn + Math.sin(t * 0.04 + layer) * 4.0);
            double a1 = Math.toRadians(-80 + 160.0 * f1 + turn + Math.sin(t * 0.04 + layer) * 4.0);
            double r0 = 92.0 + Math.sin(f0 * 9.0 + t * 0.11) * 10.0 + Math.sin(f0 * 23.0 - t * 0.07) * 3.0;
            double r1 = 92.0 + Math.sin(f1 * 9.0 + t * 0.11) * 10.0 + Math.sin(f1 * 23.0 - t * 0.07) * 3.0;
            double low0 = 22.0 + Math.sin(f0 * 7.0 + t * 0.35) * 5.0 + Math.sin(f0 * 17.0 - t * 0.6) * 2.0;
            double low1 = 22.0 + Math.sin(f1 * 7.0 + t * 0.35) * 5.0 + Math.sin(f1 * 17.0 - t * 0.6) * 2.0;
            double high0 = low0 + (44.0 + Math.sin(f0 * 11.0 - t * 0.25) * 10.0) * tall;
            double high1 = low1 + (44.0 + Math.sin(f1 * 11.0 - t * 0.25) * 10.0) * tall;
            Vec3 b0 = new Vec3(Math.sin(a0) * r0, low0, -Math.cos(a0) * r0).normalize();
            Vec3 b1 = new Vec3(Math.sin(a1) * r1, low1, -Math.cos(a1) * r1).normalize();
            Vec3 t0 = new Vec3(Math.sin(a0) * r0, high0, -Math.cos(a0) * r0).normalize();
            Vec3 t1 = new Vec3(Math.sin(a1) * r1, high1, -Math.cos(a1) * r1).normalize();
            boolean[] visible = layer == 0 ? auroraVisibleStrips : auroraVisible2;
            if ((frame + i) % 12 == 0) {
                visible[i] = clearSky(mc, camera, b0.add(b1).normalize());
            }
            if (!visible[i]) {
                continue;
            }
            float[] pb0 = project(b0, camera, mc, w, h);
            float[] pb1 = project(b1, camera, mc, w, h);
            float[] pt0 = project(t0, camera, mc, w, h);
            float[] pt1 = project(t1, camera, mc, w, h);
            if (pb0 == null || pb1 == null || pt0 == null || pt1 == null) {
                continue;
            }
            int x0 = Math.round(Math.min(pb0[0], pb1[0]));
            int x1 = Math.round(Math.max(pb0[0], pb1[0]));
            int top = Math.round((pt0[1] + pt1[1]) / 2f);
            int bottom = Math.round((pb0[1] + pb1[1]) / 2f);
            if (x1 - x0 < 1 || bottom - top < 2 || x1 < 0 || x0 > w || bottom < 0 || top > h) {
                continue;
            }
            // Bright bands travel along the curtain; the rays drift sideways.
            double band = 0.5 + 0.5 * Math.sin(f0 * 14.0 - t * 0.9 + layer * 2.0);
            double flicker = 0.85 + 0.15 * Math.sin(t * 3.1 + i * 1.7);
            float alpha = (float) (s * (0.35 + 0.65 * band) * flicker);
            float u = (float) ((i * 0.75 + t * 2.5) % 31.0);
            gui.blit(RenderPipelines.GUI_TEXTURED, AURORA, x0, top, u, 0f, x1 - x0 + 1, bottom - top,
                    1, 64, 32, 64, (Math.round(255 * alpha) << 24) | (layer == 0 ? 0xFFFFFF : 0xE8D8FF));
        }
    }

    /** Nothing solid between the eye and the sky in this direction. */
    private static boolean clearSky(Minecraft mc, Camera camera, Vec3 dir) {
        Vec3 from = camera.position();
        Vec3 to = from.add(dir.scale(256.0));
        HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * Where a direction lands on the screen: x, y and the pixels per unit of
     * tangent at the centre (for sizes). Null if it is behind the camera.
     */
    private static float[] project(Vec3 dir, Camera camera, Minecraft mc, int w, int h) {
        double yaw = Math.toRadians(camera.yRot());
        double pitch = Math.toRadians(camera.xRot());
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;
        double d = dir.x * fx + dir.y * fy + dir.z * fz;
        if (d <= 0.05) {
            return null;
        }
        double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = w / (double) h;
        double sx = (dir.x * rx + dir.z * rz) / d / (tan * aspect);
        double sy = (dir.x * ux + dir.y * uy + dir.z * uz) / d / tan;
        return new float[]{(float) ((sx * 0.5 + 0.5) * w), (float) ((0.5 - sy * 0.5) * h), (float) (h / 2.0 / tan)};
    }

    private static int scaleAlpha(int argb, float s) {
        int a = Math.round(((argb >>> 24) & 0xFF) * s);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
