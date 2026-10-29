package com.mki.nightsiege.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The skill tree's backgrounds v2 (Simon, 29 October 2026, 01:25: a pixel landscape per subclass with
 * its crest, parallax and a small animation, muted). Three layers from tools/ui/tree_bg2.py – sky
 * (480 wide), scene (544) and front (608) – that move further the nearer they are when the tree is
 * dragged, and in front of the scene a few particles of the subclass's motif: embers, souls, mist ...
 */
public final class TreeScene {

    static final int SKY_W = 480;
    static final int MID_W = 544;
    static final int FRONT_W = 608;
    static final int TEX_H = 270;

    /** What moves in front of a scene. */
    enum Kind { EMBER, SPARK, BUBBLE, MOTE, SOUL, LEAF, SNOW, RAIN, WIND, FIREFLY, DUST, GHOST }

    private record Motion(Kind kind, int color, int count) {
    }

    private static final Map<String, Motion> MOTION = new HashMap<>();
    private static final Map<String, Boolean> PRESENT = new HashMap<>();

    static {
        MOTION.put("blademaster", new Motion(Kind.LEAF, 0xC87890, 18));
        MOTION.put("berserker", new Motion(Kind.EMBER, 0xFF7828, 30));
        MOTION.put("axe", new Motion(Kind.LEAF, 0xB0723A, 18));
        MOTION.put("paladin", new Motion(Kind.MOTE, 0xF0D278, 22));
        MOTION.put("swift", new Motion(Kind.WIND, 0xD2EBDC, 12));
        MOTION.put("heavy", new Motion(Kind.FIREFLY, 0xDCC85A, 16));
        MOTION.put("bolter", new Motion(Kind.DUST, 0xB4AA96, 20));
        MOTION.put("trapper", new Motion(Kind.FIREFLY, 0xAAE65A, 22));
        MOTION.put("shieldfighter", new Motion(Kind.RAIN, 0x8CA0C8, 60));
        MOTION.put("bulwark", new Motion(Kind.SNOW, 0xDCDCE6, 40));
        MOTION.put("medic", new Motion(Kind.MOTE, 0x8CE68C, 22));
        MOTION.put("warden", new Motion(Kind.MOTE, 0x64F0E1, 24));
        MOTION.put("gunner", new Motion(Kind.SPARK, 0xFFC864, 16));
        MOTION.put("electrician", new Motion(Kind.SPARK, 0xFFE664, 22));
        MOTION.put("demolition", new Motion(Kind.EMBER, 0xFFA03C, 26));
        MOTION.put("alchemist", new Motion(Kind.BUBBLE, 0x78F064, 20));
        MOTION.put("tamer", new Motion(Kind.FIREFLY, 0xC8DCC8, 16));
        MOTION.put("golemsmith", new Motion(Kind.EMBER, 0xF08C3C, 24));
        MOTION.put("necromancer", new Motion(Kind.GHOST, 0xB478F0, 10));
        MOTION.put("soulcatcher", new Motion(Kind.SOUL, 0x64F5EB, 22));
    }

    private TreeScene() {
    }

    private static Identifier layer(String sub, String name) {
        return Identifier.fromNamespaceAndPath("nightsiege", "textures/gui/skills/sub2/" + sub + "_" + name + ".png");
    }

    /** True when the subclass has its three layers (else the caller draws the old picture). */
    static boolean has(String sub) {
        if (sub == null || sub.isEmpty()) {
            return false;
        }
        return PRESENT.computeIfAbsent(sub, s -> Minecraft.getInstance().getResourceManager()
                .getResource(layer(s, "sky")).isPresent());
    }

    /**
     * Draws the scene into the view. driftX and driftY go from -1 to 1 with the camera over the tree.
     */
    static void draw(GuiGraphicsExtractor gui, String sub, int vx, int vy, int vw, int vh, double driftX, double driftY) {
        int regionH = TEX_H;
        int regionW = (int) Math.round(TEX_H * vw / (double) vh);
        if (regionW > SKY_W) {
            regionW = SKY_W;
            regionH = (int) Math.round(SKY_W * vh / (double) vw);
        }
        float v = (float) Math.max(0, Math.min(TEX_H - regionH, (TEX_H - regionH) / 2.0 * (1 + driftY * 0.5)));
        float uSky = offset(SKY_W, regionW, driftX, 0.35);
        float uMid = offset(MID_W, regionW, driftX, 0.7);
        float uFront = offset(FRONT_W, regionW, driftX, 1.0);
        gui.blit(RenderPipelines.GUI_TEXTURED, layer(sub, "sky"), vx, vy, uSky, v, vw, vh, regionW, regionH, SKY_W, TEX_H);
        gui.blit(RenderPipelines.GUI_TEXTURED, layer(sub, "mid"), vx, vy, uMid, v, vw, vh, regionW, regionH, MID_W, TEX_H);
        Motion motion = MOTION.get(sub);
        if (motion != null) {
            particles(gui, motion, vx, vy, vw, vh, uMid, v, vw / (double) regionW);
        }
        if ("electrician".equals(sub)) {
            // Now and then the storm flashes.
            long ms = System.currentTimeMillis() % 7000L;
            if (ms < 90 || (ms > 180 && ms < 240)) {
                gui.fill(vx, vy, vx + vw, vy + vh, 0x22FFF6C8);
            }
        }
        gui.blit(RenderPipelines.GUI_TEXTURED, layer(sub, "front"), vx, vy, uFront, v, vw, vh, regionW, regionH, FRONT_W, TEX_H);
    }

    private static float offset(int width, int regionW, double drift, double factor) {
        double extra = Math.max(0, width - regionW);
        return (float) Math.max(0, Math.min(extra, extra / 2.0 + drift * extra / 2.0 * factor));
    }

    private static double hash(int i, int salt) {
        long h = (i * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }

    /** The particles in the scene layer's pixels (544 x 270), shown through the view's window on it. */
    private static void particles(GuiGraphicsExtractor gui, Motion m, int vx, int vy, int vw, int vh,
                                  float u, float v, double scale) {
        double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        int px = Math.max(1, (int) Math.round(scale * 2));   // one doubled pixel of the scene
        for (int i = 0; i < m.count(); i++) {
            double h1 = hash(i, 1);
            double h2 = hash(i, 2);
            double h3 = hash(i, 3);
            double x;
            double y;
            double alpha;
            int w = px;
            int hgt = px;
            switch (m.kind()) {
                case EMBER, SPARK, BUBBLE, MOTE, SOUL -> {
                    double period = switch (m.kind()) {
                        case SPARK -> 1.6 + h3 * 1.4;
                        case EMBER -> 5 + h3 * 4;
                        case BUBBLE -> 7 + h3 * 3;
                        default -> 9 + h3 * 6;
                    };
                    double phase = (t / period + h1) % 1.0;
                    double rise = m.kind() == Kind.SPARK ? 50 : 200;
                    double base = m.kind() == Kind.SPARK ? 140 + h3 * 80 : 236;
                    y = base - phase * rise;
                    double sway = m.kind() == Kind.SOUL ? 8 : m.kind() == Kind.MOTE ? 5 : 3;
                    x = h2 * MID_W + Math.sin(t * 0.9 + h1 * 20) * sway + (m.kind() == Kind.EMBER ? phase * 18 : 0);
                    alpha = Math.sin(Math.PI * phase);
                    if (m.kind() == Kind.SPARK) {
                        alpha *= (Math.sin(t * 25 + i) > -0.2) ? 1 : 0.2;
                    }
                }
                case LEAF, SNOW -> {
                    double period = m.kind() == Kind.SNOW ? 10 + h3 * 6 : 12 + h3 * 8;
                    double phase = (t / period + h1) % 1.0;
                    y = phase * TEX_H;
                    x = h2 * MID_W + phase * (m.kind() == Kind.LEAF ? 60 : 20) + Math.sin(t * 1.3 + h1 * 30) * 6;
                    alpha = Math.min(1, Math.min(phase * 6, (1 - phase) * 6));
                }
                case RAIN -> {
                    double phase = (t / (0.7 + h3 * 0.3) + h1) % 1.0;
                    y = phase * TEX_H;
                    x = h2 * MID_W - phase * 12;
                    alpha = 0.55;
                    hgt = px * 4;
                    w = Math.max(1, px / 2);
                }
                case WIND -> {
                    double phase = (t / (3 + h3 * 2) + h1) % 1.0;
                    x = phase * (MID_W + 120) - 60;
                    y = 30 + h2 * 180;
                    alpha = Math.sin(Math.PI * phase) * 0.6;
                    w = px * 10;
                    hgt = Math.max(1, px / 2);
                }
                case FIREFLY -> {
                    x = h2 * MID_W + Math.sin(t * 0.45 + h1 * 40) * 22;
                    y = 150 + h3 * 80 + Math.cos(t * 0.33 + h2 * 40) * 10;
                    alpha = Math.max(0, Math.sin(t * 1.7 + h1 * 50));
                }
                case DUST -> {
                    double phase = (t / (18 + h3 * 10) + h1) % 1.0;
                    x = phase * MID_W;
                    y = 120 + h2 * 110 + Math.sin(t * 0.6 + h1 * 9) * 4;
                    alpha = Math.sin(Math.PI * phase) * 0.6;
                }
                default -> {   // GHOST: a pale wisp drifting up and aside
                    double phase = (t / (14 + h3 * 8) + h1) % 1.0;
                    x = h2 * MID_W + phase * 40;
                    y = 230 - phase * 150;
                    alpha = Math.sin(Math.PI * phase) * 0.5;
                    w = px * 3;
                    hgt = px * 4;
                }
            }
            int sx = vx + (int) Math.round((x - u) * scale);
            int sy = vy + (int) Math.round((y - v) * scale);
            if (sx < vx || sy < vy || sx + w > vx + vw || sy + hgt > vy + vh || alpha <= 0.02) {
                continue;
            }
            int a = (int) Math.round(Math.min(1, alpha) * 200);
            gui.fill(sx, sy, sx + w, sy + hgt, (a << 24) | m.color());
            if (m.kind() == Kind.GHOST) {
                gui.fill(sx + px, sy - px, sx + w - px, sy, ((a / 2) << 24) | m.color());
            } else if (m.kind() == Kind.FIREFLY || m.kind() == Kind.SOUL || m.kind() == Kind.EMBER) {
                // a faint halo
                gui.fill(sx - px, sy - px, sx + w + px, sy + hgt + px, ((a / 6) << 24) | m.color());
            }
        }
    }
}
