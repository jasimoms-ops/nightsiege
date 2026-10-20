package com.mki.nightsiege.game;

import com.mki.nightsiege.core.BlockGuard;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.ToolKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lets armed siege mobs chew through walls.
 *
 * <p>A mob can only break what its tool is made for: a pickaxe opens stone, an
 * axe opens wood, a shovel opens earth. The material of the tool does not
 * matter, only its kind. A mob with empty hands cannot dig at all.</p>
 *
 * <p>Runs from the server tick rather than a custom AI goal, so no mixin into
 * Mob is needed and the behaviour only ever applies to siege mobs.</p>
 */
public final class Digger {

    /** How far from the anchor a mob stops digging, because it has arrived. */
    private static final double ARRIVED_DISTANCE_SQ = 9.0;

    private static final class Progress {
        BlockPos target;
        int ticks;
    }

    private final Map<UUID, Progress> progress = new HashMap<>();

    public void reset() {
        progress.clear();
    }

    public void forget(UUID mobId) {
        progress.remove(mobId);
    }

    /**
     * @param elapsedTicks ticks since the last call, added to the dig progress
     */
    public void tick(ServerLevel world, Collection<UUID> mobIds, BlockPos anchor,
                     SiegeConfig cfg, int elapsedTicks) {
        if (!cfg.diggingEnabled) {
            return;
        }
        for (UUID mobId : mobIds) {
            Entity entity = world.getEntity(mobId);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                progress.remove(mobId);
                continue;
            }
            ToolKind tool = cfg.toolGatedDigging ? MobFactory.toolOf(mob) : ToolKind.PICKAXE;
            if (cfg.toolGatedDigging && !tool.canDig()) {
                progress.remove(mobId);
                continue;
            }
            if (mob.distanceToSqr(anchor.getX() + 0.5, mob.getY(), anchor.getZ() + 0.5) <= ARRIVED_DISTANCE_SQ) {
                progress.remove(mobId);
                continue;
            }
            // A finished navigation means the mob found no way through. That is
            // exactly when it should start breaking the wall in front of it.
            if (!mob.getNavigation().isDone()) {
                progress.remove(mobId);
                continue;
            }
            BlockPos target = blockInFront(world, mob, anchor, cfg, tool);
            if (target == null) {
                progress.remove(mobId);
                continue;
            }
            Progress state = progress.computeIfAbsent(mobId, key -> new Progress());
            if (!target.equals(state.target)) {
                state.target = target;
                state.ticks = 0;
            }
            state.ticks += elapsedTicks;
            if (state.ticks >= cfg.digTicksPerBlock) {
                world.destroyBlock(target, false);
                state.target = null;
                state.ticks = 0;
            }
        }
    }

    /** The first block between the mob and the anchor that its tool can open. */
    private BlockPos blockInFront(ServerLevel world, Mob mob, BlockPos anchor,
                                  SiegeConfig cfg, ToolKind tool) {
        double dx = anchor.getX() + 0.5 - mob.getX();
        double dz = anchor.getZ() + 0.5 - mob.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) {
            return null;
        }
        int stepX = (int) Math.round(dx / length);
        int stepZ = (int) Math.round(dz / length);
        if (stepX == 0 && stepZ == 0) {
            return null;
        }
        BlockPos feet = mob.blockPosition().offset(stepX, 0, stepZ);
        if (isBreakable(world, feet, cfg, tool)) {
            return feet;
        }
        BlockPos head = feet.above();
        if (isBreakable(world, head, cfg, tool)) {
            return head;
        }
        return null;
    }

    private boolean isBreakable(ServerLevel world, BlockPos pos, SiegeConfig cfg, ToolKind tool) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (BlockGuard.isProtected(Demolition.blockId(state), cfg)) {
            return false;
        }
        return matchesTool(state, cfg, tool);
    }

    /**
     * Uses Minecraft's own mineable tags, so blocks from other mods are judged
     * by the same rule the game itself uses for player tools.
     */
    private boolean matchesTool(BlockState state, SiegeConfig cfg, ToolKind tool) {
        if (!cfg.toolGatedDigging) {
            return true;
        }
        return switch (tool) {
            case PICKAXE -> state.is(BlockTags.MINEABLE_WITH_PICKAXE);
            case AXE -> state.is(BlockTags.MINEABLE_WITH_AXE);
            // Earth digging is off by default: only stone and wood give way.
            case SHOVEL -> cfg.allowShovelDigging && state.is(BlockTags.MINEABLE_WITH_SHOVEL);
            case NONE -> false;
        };
    }
}
