package com.mki.nightsiege.game;

import com.mki.nightsiege.core.TrapKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** One of the engineer's traps. The block itself is plain; {@link TrapService} does the work. */
public class TrapBlock extends Block {

    private final TrapKind kind;

    public TrapBlock(TrapKind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public TrapKind kind() {
        return kind;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            SiegeManager.get().onTrapPlaced(serverLevel, pos, placer, kind);
        }
    }
}
