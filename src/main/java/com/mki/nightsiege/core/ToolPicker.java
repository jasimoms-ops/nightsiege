package com.mki.nightsiege.core;

import java.util.Random;

/**
 * Decides which tool a freshly spawned siege mob carries.
 *
 * <p>The material of the tool does not matter for what it can break: any
 * pickaxe opens any stone. Only the kind of tool counts.</p>
 */
public final class ToolPicker {

    private ToolPicker() {
    }

    public static ToolKind pick(Random random, SiegeConfig cfg) {
        if (!cfg.toolGatedDigging || cfg.toolEquipChance <= 0.0) {
            return ToolKind.NONE;
        }
        if (random.nextDouble() >= Math.min(1.0, cfg.toolEquipChance)) {
            return ToolKind.NONE;
        }
        int pick = Math.max(0, cfg.toolPickaxeWeight);
        int axe = Math.max(0, cfg.toolAxeWeight);
        int shovel = Math.max(0, cfg.toolShovelWeight);
        int total = pick + axe + shovel;
        if (total <= 0) {
            return ToolKind.NONE;
        }
        int roll = random.nextInt(total);
        if (roll < pick) {
            return ToolKind.PICKAXE;
        }
        if (roll < pick + axe) {
            return ToolKind.AXE;
        }
        return ToolKind.SHOVEL;
    }

    /**
     * Works out which tool an item id represents. This reads the mob's actual
     * held item, so a zombie that spawned with a pickaxe on its own counts just
     * like one the mod equipped, and tools from other mods work too.
     *
     * <p>Order matters: a pickaxe id also ends in "axe", so it is tested first.</p>
     */
    public static ToolKind fromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return ToolKind.NONE;
        }
        String id = itemId.toLowerCase(java.util.Locale.ROOT);
        int slash = id.indexOf(':');
        String path = slash >= 0 ? id.substring(slash + 1) : id;
        if (path.contains("pickaxe") || path.contains("pick_axe")) {
            return ToolKind.PICKAXE;
        }
        if (path.equals("axe") || path.endsWith("_axe") || path.contains("hatchet")) {
            return ToolKind.AXE;
        }
        if (path.contains("shovel") || path.contains("spade")) {
            return ToolKind.SHOVEL;
        }
        return ToolKind.NONE;
    }

    /** The item id given to a mob carrying this tool. */
    public static String itemId(ToolKind kind, SiegeConfig cfg) {
        return switch (kind) {
            case PICKAXE -> cfg.pickaxeItem;
            case AXE -> cfg.axeItem;
            case SHOVEL -> cfg.shovelItem;
            case NONE -> null;
        };
    }
}
