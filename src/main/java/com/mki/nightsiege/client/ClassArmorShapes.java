package com.mki.nightsiege.client;

import java.util.List;
import java.util.Map;

/**
 * The 3D parts of the class outfits. Written by tools/armor/build.py; do not edit by hand.
 */
public final class ClassArmorShapes {

    /** One box: slot (head, chest, legs, feet), model part, place, size, texture spot, pivot and turn. */
    public record Box(String slot, String part, String name, float x, float y, float z, int w, int h, int d,
                      float grow, int u, int v, boolean mirror, float px, float py, float pz,
                      float rx, float ry, float rz, boolean cape) {
    }

    public static final int TEXTURE_WIDTH = 128;
    public static final int TEXTURE_HEIGHT = 64;

    private ClassArmorShapes() {
    }

    public static final Map<String, List<Box>> BOXES = Map.ofEntries(
            Map.entry("berserker", List.of(
                    new Box("head", "head", "skull", -5.0f, -11.500f, -5.500f, 10, 3, 10, 0.0f, 64, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "snout", -2.500f, -9.500f, -8.500f, 5, 3, 4, 0.0f, 104, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "horn1", -8.0f, -11.500f, -1.500f, 3, 2, 3, 0.0f, 104, 7, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "horn1_m", 5.0f, -11.500f, -1.500f, 3, 2, 3, 0.0f, 104, 7, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "horn2", -9.500f, -14.500f, -1.500f, 2, 4, 3, 0.0f, 116, 7, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "horn2_m", 7.500f, -14.500f, -1.500f, 2, 4, 3, 0.0f, 116, 7, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "horn3", -9.0f, -16.500f, -1.0f, 1, 2, 2, 0.0f, 122, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "horn3_m", 8.0f, -16.500f, -1.0f, 1, 2, 2, 0.0f, 122, 0, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "pelt", -5.500f, -10.0f, 4.200f, 11, 12, 2, 0.0f, 64, 13, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "mantle", -5.500f, -2.500f, -3.500f, 11, 4, 7, 0.0f, 90, 14, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "right_arm", "pad", -4.600f, -3.600f, -3.500f, 6, 4, 7, 0.0f, 90, 25, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "left_arm", "pad_l", -1.400f, -3.600f, -3.500f, 6, 4, 7, 0.0f, 90, 25, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "cloak", -5.0f, 0.0f, 0.0f, 10, 15, 1, 0.0f, 64, 27, false, 0.0f, -1.0f, 3.300f, 0.0f, 0.0f, 0.0f, true),
                    new Box("chest", "body", "belt", -5.0f, 8.500f, -3.0f, 10, 2, 6, 0.300f, 0, 32, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "buckle", -1.500f, 8.0f, -3.900f, 3, 3, 1, 0.0f, 116, 25, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "right_arm", "bracer", -4.0f, 6.0f, -3.0f, 6, 4, 6, 0.300f, 32, 32, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "left_arm", "bracer_l", -2.0f, 6.0f, -3.0f, 6, 4, 6, 0.300f, 32, 32, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("legs", "body", "loin", -3.0f, 11.0f, -3.300f, 6, 6, 1, 0.0f, 64, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("legs", "body", "loinb", -3.0f, 11.0f, 2.300f, 6, 5, 1, 0.0f, 78, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("feet", "right_leg", "wrap", -3.500f, 6.0f, -3.500f, 7, 4, 7, 0.0f, 86, 36, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("feet", "left_leg", "wrap_l", -3.500f, 6.0f, -3.500f, 7, 4, 7, 0.0f, 86, 36, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false))),
            Map.entry("blademaster", List.of(
                    new Box("head", "head", "beret", -6.0f, -2.200f, -6.0f, 12, 2, 12, 0.0f, 64, 0, false, 0.0f, -9.0f, 0.0f, 0.050f, 0.0f, -0.140f, false),
                    new Box("head", "head", "plume", -0.500f, -9.0f, -1.0f, 1, 9, 3, 0.0f, 112, 0, false, 5.200f, -10.0f, 1.0f, 0.950f, 0.0f, 0.300f, false),
                    new Box("head", "head", "brooch", 2.500f, -11.600f, -6.600f, 2, 2, 1, 0.0f, 120, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "ruff", -5.0f, -2.800f, -3.600f, 10, 2, 7, 0.0f, 64, 14, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "right_arm", "puff", -4.600f, -2.200f, -3.600f, 7, 5, 7, 0.0f, 98, 14, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "left_arm", "puff_l", -2.400f, -2.200f, -3.600f, 7, 5, 7, 0.0f, 98, 14, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "cape", -5.0f, 0.0f, 0.0f, 10, 9, 1, 0.0f, 64, 23, false, 0.0f, -1.500f, 3.300f, 0.0f, 0.0f, 0.0f, true),
                    new Box("chest", "body", "knot", 2.600f, 8.400f, -3.900f, 2, 5, 1, 0.0f, 120, 3, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("legs", "right_leg", "hose", -3.0f, -0.600f, -3.0f, 6, 5, 6, 0.0f, 64, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("legs", "left_leg", "hose_l", -3.0f, -0.600f, -3.0f, 6, 5, 6, 0.0f, 64, 0, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("feet", "right_leg", "cuff", -3.600f, 4.600f, -3.600f, 7, 2, 7, 0.0f, 86, 26, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("feet", "left_leg", "cuff_l", -3.400f, 4.600f, -3.600f, 7, 2, 7, 0.0f, 86, 26, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false))),
            Map.entry("axe", List.of(
                    new Box("head", "head", "helm0", -5.500f, -9.600f, -5.500f, 11, 2, 11, 0.0f, 64, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "helm1", -4.500f, -11.200f, -4.500f, 9, 2, 9, 0.0f, 64, 13, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "helm2", -3.0f, -12.600f, -3.0f, 6, 2, 6, 0.0f, 100, 13, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "knob", -1.0f, -13.600f, -1.0f, 2, 1, 2, 0.0f, 108, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "nasal", -0.500f, -8.500f, -6.300f, 1, 5, 1, 0.0f, 116, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "right_arm", "pauldron", -4.800f, -3.200f, -3.600f, 6, 5, 7, 0.0f, 100, 21, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.180f, false),
                    new Box("chest", "left_arm", "pauldron_l", -1.200f, -3.200f, -3.600f, 6, 5, 7, 0.0f, 100, 21, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.180f, false),
                    new Box("chest", "body", "vest", -5.0f, -0.600f, -3.0f, 10, 9, 6, 0.350f, 64, 24, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "skirt", -5.0f, 10.400f, -3.0f, 10, 4, 6, 0.300f, 0, 32, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "belt", -5.0f, 8.800f, -3.0f, 10, 2, 6, 0.500f, 32, 32, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "buckle", -1.0f, 8.400f, -4.0f, 2, 2, 1, 0.0f, 120, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "haft", 5.400f, 8.0f, -0.500f, 1, 8, 1, 0.0f, 108, 3, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "blade", 5.300f, 13.0f, -2.600f, 1, 3, 3, 0.0f, 120, 3, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false))),
            Map.entry("paladin", List.of(
                    new Box("head", "head", "circlet", -5.500f, -8.600f, -5.500f, 11, 1, 11, 0.0f, 64, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("head", "head", "gem", -1.0f, -9.200f, -6.300f, 2, 2, 1, 0.0f, 108, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "tabard", -4.0f, 0.400f, -3.600f, 8, 16, 1, 0.0f, 108, 3, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "right_arm", "pauldron", -4.700f, -3.300f, -3.600f, 6, 3, 7, 0.0f, 64, 12, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.150f, false),
                    new Box("chest", "left_arm", "pauldron_l", -1.300f, -3.300f, -3.600f, 6, 3, 7, 0.0f, 64, 12, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.150f, false),
                    new Box("chest", "body", "cloak", -5.500f, 0.0f, 0.0f, 11, 20, 1, 0.0f, 90, 20, false, 0.0f, -1.0f, 3.400f, 0.0f, 0.0f, 0.0f, true),
                    new Box("chest", "body", "belt", -5.0f, 8.0f, -3.0f, 10, 1, 6, 0.800f, 0, 32, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("chest", "body", "buckle", -1.0f, 7.500f, -4.500f, 2, 2, 1, 0.0f, 114, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("legs", "right_leg", "knee", -2.500f, 4.0f, -3.400f, 5, 2, 1, 0.0f, 64, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("legs", "left_leg", "knee_l", -2.500f, 4.0f, -3.400f, 5, 2, 1, 0.0f, 64, 0, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("feet", "right_leg", "toe", -2.500f, 10.600f, -4.700f, 5, 2, 2, 0.0f, 90, 12, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false),
                    new Box("feet", "left_leg", "toe_l", -2.500f, 10.600f, -4.700f, 5, 2, 2, 0.0f, 90, 12, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false))),
            Map.entry("swift", List.of()),
            Map.entry("heavy", List.of()),
            Map.entry("bolter", List.of()),
            Map.entry("trapper", List.of()),
            Map.entry("shieldfighter", List.of()),
            Map.entry("bulwark", List.of()),
            Map.entry("medic", List.of()),
            Map.entry("warden", List.of()),
            Map.entry("gunner", List.of()),
            Map.entry("electrician", List.of()),
            Map.entry("demolition", List.of()),
            Map.entry("alchemist", List.of()));
}
