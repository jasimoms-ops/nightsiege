package com.mki.nightsiege.game;

import com.mki.nightsiege.core.TrapKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Arrow turret and tesla coil (Simon, 26 October 2026): towers a player can sit in. While
 * someone sits in one it shows its manned look (seat, shield plates, banner or twin coils).
 */
public class TowerBlock extends TrapBlock {

    public static final BooleanProperty MANNED = BooleanProperty.create("manned");

    public TowerBlock(TrapKind kind, BlockBehaviour.Properties properties) {
        super(kind, properties);
        registerDefaultState(stateDefinition.any().setValue(MANNED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MANNED);
    }

    /** True for the towers a player can sit in. */
    public static boolean mannable(TrapKind kind) {
        return kind == TrapKind.TURRET || kind == TrapKind.TESLA;
    }
}
