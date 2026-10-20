package com.mki.nightsiege.core;

/**
 * The tool a siege mob carries. It decides which blocks that mob may break.
 * A mob without a tool cannot dig at all.
 */
public enum ToolKind {
    NONE,
    PICKAXE,
    AXE,
    SHOVEL;

    public boolean canDig() {
        return this != NONE;
    }
}
