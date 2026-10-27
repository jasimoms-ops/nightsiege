package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeMachineKind;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The block models of the siege machines (Simon, 27 October 2026: heavy, thick beams, like the
 * sketches; not filigree like the arrow tower's crossbow). Rig space: +Z points at the wall,
 * +X is the machine's left, units are blocks. The moving part (throwing arm, barrel, bow)
 * turns about the pivot.
 */
final class MachineModels {

    /** A model: its pieces, the pivot of the moving part, the arm's pitch at rest and thrown, the hit box size. */
    record Model(List<Rigs.Piece> pieces, Vec3 pivot, float rest, float thrown, double hitboxScale) {
    }

    private static final Map<SiegeMachineKind, Model> MODELS = new EnumMap<>(SiegeMachineKind.class);

    static {
        MODELS.put(SiegeMachineKind.TREBUCHET, trebuchet());
        MODELS.put(SiegeMachineKind.MORTAR, mortar());
        MODELS.put(SiegeMachineKind.BALLISTA, ballista(false));
        MODELS.put(SiegeMachineKind.HOOK, ballista(true));
        MODELS.put(SiegeMachineKind.FIRE, catapult(true));
        MODELS.put(SiegeMachineKind.PLAGUE, catapult(false));
    }

    private MachineModels() {
    }

    static Model of(SiegeMachineKind kind) {
        return MODELS.get(kind);
    }

    private static final String WOOD = "dark_oak_log";
    private static final String BARK = "stripped_dark_oak_log";
    private static final String PLANK = "dark_oak_planks";
    private static final String IRON = "iron_block";

    private static Rigs.Piece y(String block, double x, double y, double z, double sx, double sy, double sz) {
        return Rigs.Piece.yaw(block, x, y, z, sx, sy, sz);
    }

    private static Rigs.Piece a(String block, double x, double y, double z, double sx, double sy, double sz) {
        return Rigs.Piece.aim(block, x, y, z, sx, sy, sz);
    }

    /** A grey war banner on a pole at the back. */
    private static void banner(List<Rigs.Piece> p, double x, double z, double height, String cloth) {
        p.add(y(BARK, x, 0, z, 0.16, height, 0.16));
        p.add(y(cloth, x + 0.03, height - 1.25, z + 0.16, 0.06, 1.1, 0.75));
        p.add(y(BARK, x - 0.02, height - 0.2, z + 0.1, 0.2, 0.1, 0.85));
    }

    /** Four heavy wheels. */
    private static void wheels(List<Rigs.Piece> p, double halfWidth, double front, double back, double size) {
        for (double x : new double[] {-halfWidth - 0.26, halfWidth}) {
            for (double z : new double[] {front, back}) {
                p.add(y(PLANK, x, 0, z - size / 2, 0.26, size, size));
                p.add(y(IRON, x - 0.04, size / 2 - 0.12, z - 0.12, 0.34, 0.24, 0.24));
            }
        }
    }

    /** The trebuchet: an A-frame of thick beams, a long arm with a sling and a box of stones as counterweight. */
    private static Model trebuchet() {
        List<Rigs.Piece> p = new ArrayList<>();
        // The base frame.
        p.add(y(WOOD, -1.75, 0.2, -2.7, 0.5, 0.5, 5.4));
        p.add(y(WOOD, 1.25, 0.2, -2.7, 0.5, 0.5, 5.4));
        p.add(y(WOOD, -1.75, 0.25, 2.2, 3.5, 0.4, 0.5));
        p.add(y(WOOD, -1.75, 0.25, -2.7, 3.5, 0.4, 0.5));
        p.add(y(PLANK, -1.25, 0.3, -0.6, 2.5, 0.25, 1.2));
        wheels(p, 1.75, 1.7, -2.0, 1.3);
        // The A-frame: two uprights, braces before and behind, the axle on top.
        p.add(y(WOOD, -1.65, 0.7, -0.25, 0.45, 3.9, 0.5));
        p.add(y(WOOD, 1.2, 0.7, -0.25, 0.45, 3.9, 0.5));
        p.add(y(BARK, -1.62, 0.7, 1.1, 0.36, 2.5, 0.36));
        p.add(y(BARK, -1.62, 0.7, -1.6, 0.36, 2.5, 0.36));
        p.add(y(BARK, 1.25, 0.7, 1.1, 0.36, 2.5, 0.36));
        p.add(y(BARK, 1.25, 0.7, -1.6, 0.36, 2.5, 0.36));
        p.add(y(BARK, -1.62, 2.9, -1.6, 0.36, 0.3, 3.06));
        p.add(y(BARK, 1.25, 2.9, -1.6, 0.36, 0.3, 3.06));
        p.add(y(IRON, -1.8, 4.42, -0.12, 3.6, 0.28, 0.28));
        p.add(y(IRON, -1.7, 4.3, -0.3, 0.55, 0.55, 0.62));
        p.add(y(IRON, 1.15, 4.3, -0.3, 0.55, 0.55, 0.62));
        // A pile of stones and the banner at the back.
        p.add(y("cobblestone", 0.3, 0.55, -2.55, 0.7, 0.6, 0.7));
        p.add(y("cobblestone", -0.6, 0.55, -2.45, 0.55, 0.5, 0.55));
        p.add(y("mossy_cobblestone", -0.1, 1.1, -2.4, 0.5, 0.45, 0.5));
        banner(p, -1.68, -2.65, 5.2, "gray_wool");
        // The arm (about the axle): long behind with the sling, short in front with the counterweight.
        p.add(a(WOOD, -0.24, -0.24, -5.2, 0.48, 0.48, 6.9));
        p.add(a(IRON, -0.3, -0.3, -0.3, 0.6, 0.6, 0.6));
        p.add(a(IRON, -0.27, -0.27, -2.6, 0.54, 0.54, 0.2));
        p.add(a(PLANK, -0.75, -1.75, 0.95, 1.5, 1.4, 1.15));
        p.add(a(IRON, -0.8, -0.5, 0.9, 1.6, 0.18, 1.25));
        p.add(a("cobblestone", -0.6, -0.42, 1.05, 1.2, 0.3, 0.95));
        p.add(a(PLANK, -0.4, -0.55, -5.5, 0.8, 0.45, 0.8));
        p.add(a("cobblestone", -0.3, -0.12, -5.4, 0.6, 0.5, 0.6));
        return new Model(p, new Vec3(0, 4.56, 0.02), -38f, 78f, 2.6);
    }

    /** The enemy mortar: a squat black barrel on a heavy timber bed behind sandbags. */
    private static Model mortar() {
        List<Rigs.Piece> p = new ArrayList<>();
        p.add(y(PLANK, -1.35, 0, -1.35, 2.7, 0.35, 2.7));
        p.add(y(WOOD, -1.45, 0, -1.45, 0.4, 0.45, 2.9));
        p.add(y(WOOD, 1.05, 0, -1.45, 0.4, 0.45, 2.9));
        // The cheeks holding the barrel, with iron straps.
        p.add(y(WOOD, -1.0, 0.35, -0.75, 0.32, 1.0, 1.5));
        p.add(y(WOOD, 0.68, 0.35, -0.75, 0.32, 1.0, 1.5));
        p.add(y(IRON, -1.04, 1.05, -0.22, 0.4, 0.22, 0.44));
        p.add(y(IRON, 0.64, 1.05, -0.22, 0.4, 0.22, 0.44));
        // Sandbags round the back and sides.
        p.add(y("brown_wool", -1.6, 0, -1.9, 3.2, 0.55, 0.45));
        p.add(y("brown_wool", -1.4, 0.55, -1.85, 2.8, 0.4, 0.4));
        p.add(y("brown_wool", -2.0, 0, -1.4, 0.45, 0.5, 2.0));
        p.add(y("brown_wool", 1.55, 0, -1.4, 0.45, 0.5, 2.0));
        // Shells waiting.
        p.add(y("coal_block", 1.1, 0.35, 0.9, 0.4, 0.4, 0.4));
        p.add(y("coal_block", 0.62, 0.35, 1.05, 0.4, 0.4, 0.4));
        p.add(y("coal_block", 0.88, 0.72, 0.98, 0.36, 0.36, 0.36));
        banner(p, -1.5, -1.6, 3.4, "gray_wool");
        // The barrel (about the trunnions), pointing up over the wall.
        p.add(a("polished_blackstone", -0.52, -0.52, -0.8, 1.04, 1.04, 2.0));
        p.add(a(IRON, -0.58, -0.58, 1.0, 1.16, 1.16, 0.32));
        p.add(a(IRON, -0.56, -0.56, -0.55, 1.12, 1.12, 0.22));
        p.add(a("black_concrete", -0.34, -0.34, 1.31, 0.68, 0.68, 0.03));
        p.add(a(IRON, -0.62, -0.12, -0.12, 1.24, 0.24, 0.24));
        return new Model(p, new Vec3(0, 1.2, 0), -55f, -48f, 1.7);
    }

    /** The ballista behind its wooden shield; with the grappling hook a rope coil and a hook on the bolt. */
    private static Model ballista(boolean hook) {
        List<Rigs.Piece> p = new ArrayList<>();
        // A cross of beams with a post.
        p.add(y(WOOD, -1.1, 0, -0.22, 2.2, 0.38, 0.44));
        p.add(y(WOOD, -0.22, 0, -1.1, 0.44, 0.38, 2.2));
        p.add(y(WOOD, -0.27, 0.38, -0.27, 0.54, 1.0, 0.54));
        p.add(y(IRON, -0.32, 1.25, -0.32, 0.64, 0.14, 0.64));
        // The shield wall in front, with iron studs and its two posts.
        p.add(y("spruce_planks", -1.7, 0, 1.65, 3.4, 1.4, 0.28));
        p.add(y(WOOD, -1.85, 0, 1.6, 0.34, 1.65, 0.4));
        p.add(y(WOOD, 1.51, 0, 1.6, 0.34, 1.65, 0.4));
        p.add(y(IRON, -1.15, 0.55, 1.92, 0.26, 0.26, 0.06));
        p.add(y(IRON, -0.13, 0.85, 1.92, 0.26, 0.26, 0.06));
        p.add(y(IRON, 0.89, 0.55, 1.92, 0.26, 0.26, 0.06));
        p.add(y(BARK, -1.7, 1.0, 1.6, 3.4, 0.16, 0.36));
        if (hook) {
            // The rope coil beside it.
            p.add(y("brown_wool", 0.55, 0.38, -1.0, 0.7, 0.5, 0.7));
            p.add(y("brown_wool", 0.68, 0.88, -0.87, 0.44, 0.22, 0.44));
            p.add(y("iron_chain", 0.86, 0.38, -0.2, 0.08, 1.2, 0.08));
        } else {
            // A bundle of bolts.
            p.add(y("spruce_planks", 0.55, 0.38, -1.1, 0.12, 0.12, 1.4));
            p.add(y("spruce_planks", 0.72, 0.38, -1.1, 0.12, 0.12, 1.4));
            p.add(y("spruce_planks", 0.63, 0.52, -1.1, 0.12, 0.12, 1.4));
        }
        banner(p, -1.0, -1.05, 3.0, "gray_wool");
        // The bow: stock, thick limbs with iron tips, the string drawn back, the bolt.
        p.add(a(WOOD, -0.22, -0.16, -1.55, 0.44, 0.32, 2.9));
        p.add(a(WOOD, -1.8, -0.13, 0.95, 3.6, 0.26, 0.26));
        p.add(a(IRON, -1.88, -0.16, 0.9, 0.24, 0.32, 0.34));
        p.add(a(IRON, 1.64, -0.16, 0.9, 0.24, 0.32, 0.34));
        p.add(a("white_wool", -1.72, -0.03, -0.42, 3.44, 0.05, 0.05));
        p.add(a(IRON, -0.34, -0.34, -1.7, 0.68, 0.28, 0.28));
        p.add(a("spruce_planks", -0.07, 0.16, -0.45, 0.14, 0.14, 2.4));
        if (hook) {
            p.add(a(IRON, -0.45, 0.13, 1.95, 0.9, 0.12, 0.12));
            p.add(a(IRON, -0.45, 0.25, 1.75, 0.12, 0.32, 0.12));
            p.add(a(IRON, 0.33, 0.25, 1.75, 0.12, 0.32, 0.12));
            p.add(a(IRON, -0.08, 0.13, 1.95, 0.16, 0.12, 0.35));
        } else {
            p.add(a(IRON, -0.11, 0.12, 1.92, 0.22, 0.22, 0.34));
        }
        return new Model(p, new Vec3(0, 1.6, 0), 0f, 0f, 1.8);
    }

    /** The fire or plague sling: a low frame with a torsion bundle, an arm with a bucket and a stop beam. */
    private static Model catapult(boolean fire) {
        List<Rigs.Piece> p = new ArrayList<>();
        p.add(y(WOOD, -1.15, 0.15, -2.1, 0.42, 0.45, 3.8));
        p.add(y(WOOD, 0.73, 0.15, -2.1, 0.42, 0.45, 3.8));
        p.add(y(WOOD, -1.15, 0.2, 1.3, 2.3, 0.4, 0.42));
        p.add(y(WOOD, -1.15, 0.2, -2.1, 2.3, 0.4, 0.42));
        wheels(p, 1.15, 1.0, -1.5, 1.0);
        // Uprights at the pivot, the stop beam the arm strikes and the rope bundle.
        p.add(y(WOOD, -1.05, 0.6, 0.55, 0.34, 2.0, 0.38));
        p.add(y(WOOD, 0.71, 0.6, 0.55, 0.34, 2.0, 0.38));
        p.add(y(WOOD, -1.05, 2.45, 0.5, 2.1, 0.32, 0.48));
        p.add(y("brown_wool", -0.78, 0.88, 0.58, 1.56, 0.36, 0.36));
        p.add(y(BARK, -1.05, 1.5, -0.4, 0.3, 0.3, 1.0));
        p.add(y(BARK, 0.75, 1.5, -0.4, 0.3, 0.3, 1.0));
        String load = fire ? "magma_block" : "bone_block";
        if (fire) {
            p.add(y("magma_block", 0.2, 0.6, -1.85, 0.42, 0.42, 0.42));
            p.add(y("magma_block", -0.35, 0.6, -1.7, 0.42, 0.42, 0.42));
            p.add(y("terracotta", -0.1, 1.0, -1.8, 0.36, 0.36, 0.36));
            banner(p, -1.12, -2.05, 3.6, "red_wool");
        } else {
            p.add(y("bone_block", 0.1, 0.6, -1.9, 0.6, 0.45, 0.65));
            p.add(y("moss_block", -0.5, 0.6, -1.8, 0.5, 0.35, 0.55));
            p.add(y("skeleton_skull", -0.45, 0.95, -1.75, 0.45, 0.45, 0.45));
            banner(p, -1.12, -2.05, 3.6, "green_wool");
        }
        // The arm, pointing back from the pivot, with its bucket and load.
        p.add(a(WOOD, -0.17, -0.17, -2.75, 0.34, 0.34, 3.1));
        p.add(a(IRON, -0.2, -0.2, -0.25, 0.4, 0.4, 0.4));
        p.add(a(PLANK, -0.4, -0.08, -3.05, 0.8, 0.36, 0.66));
        p.add(a(load, -0.28, 0.2, -2.95, 0.56, 0.42, 0.48));
        return new Model(p, new Vec3(0, 1.05, 0.75), -10f, 72f, 2.0);
    }
}
