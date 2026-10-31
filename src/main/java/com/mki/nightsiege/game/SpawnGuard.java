package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The server's spawn protection and Night Siege (Simon 31.10.: survival safety; in December about ten
 * players, not all of them operators). In 26.2 the packet handler asks
 * {@code MinecraftServer.isUnderSpawnProtection} itself before a right click on a block or the start
 * of breaking one (SpawnUseMixin, SpawnBreakMixin), so without this a defender without operator
 * rights could not set a trap, a tower or a turret, nor a barricade or the war banner, near a core
 * that stands by the world spawn (and not even open the core). Let through, only for signed-up
 * players and only within the base radius of the core:
 * <ul>
 *   <li>a right click with a trap item, a kit item or a crate gadget in that hand,</li>
 *   <li>a right click on a trap or tower (man it, its menu) with an empty hand, arrows or such an item,</li>
 *   <li>breaking a trap, a tower or one of the night's blocks.</li>
 * </ul>
 * The core itself is open to everyone (sign-up, its window). Anything else (building, breaking the
 * spawn) stays protected as in vanilla.
 */
public final class SpawnGuard {

    private SpawnGuard() {
    }

    /** A right click on this block may go on although the spot is under the spawn protection. */
    public static boolean useAllowed(Player player, ServerLevel level, BlockPos pos, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || level == null || pos == null) {
            return false;
        }
        BlockState clicked = level.getBlockState(pos);
        if (ModContent.isCore(clicked)) {
            return true;   // the core's window (its click never builds anything)
        }
        SiegeManager m = SiegeManager.get();
        if (m == null || !m.isRegistered(sp) || !nearCore(m, level, pos)) {
            return false;
        }
        ItemStack held = sp.getItemInHand(hand == null ? InteractionHand.MAIN_HAND : hand);
        if (ownItem(held)) {
            return true;
        }
        return (ModContent.trapOf(clicked) != null || TempBlocks.isTemp(level, pos))
                && (held.isEmpty() || held.is(net.minecraft.world.item.Items.ARROW));
    }

    /** Breaking this block may start although the spot is under the spawn protection. */
    public static boolean breakAllowed(Player player, ServerLevel level, BlockPos pos) {
        if (!(player instanceof ServerPlayer sp) || level == null || pos == null) {
            return false;
        }
        SiegeManager m = SiegeManager.get();
        if (m == null || !m.isRegistered(sp) || !nearCore(m, level, pos)) {
            return false;
        }
        return ModContent.trapOf(level.getBlockState(pos)) != null || TempBlocks.isTemp(level, pos);
    }

    /** A trap, a kit item or a crate gadget: Night Siege's own things, placed or used at the base. */
    private static boolean ownItem(ItemStack held) {
        return held != null && !held.isEmpty()
                && (ModContent.trapOf(held) != null || KitService.isKit(held) || !Gadgets.of(held).isEmpty());
    }

    /** Within the base radius of the core, in its world. */
    private static boolean nearCore(SiegeManager m, ServerLevel level, BlockPos pos) {
        CoreRecord core = m.mainCore();
        if (core == null || m.config() == null || !SiegeManager.dimensionName(level).equals(core.dimension)) {
            return false;
        }
        double radius = m.config().baseRadius;
        double dx = pos.getX() - core.x;
        double dz = pos.getZ() - core.z;
        return dx * dx + dz * dz <= radius * radius;
    }
}
