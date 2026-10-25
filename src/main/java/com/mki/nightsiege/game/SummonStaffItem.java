package com.mki.nightsiege.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The summoner's staff: right click calls a companion (a wolf, or a golem for the
 * golemsmith), sneaking and right clicking at a monster sends every companion at it.
 * The pause until the next call shows on the item.
 */
public class SummonStaffItem extends Item {

    /** Set by the siege manager. */
    public interface Handler {
        void use(ServerPlayer player, ItemStack stack);
    }

    public static Handler handler = (player, stack) -> {
    };

    /** Custom data keys: the souls of the way of the dead, shown as the bar of the staff. */
    public static final String SOULS = "ns_souls";
    public static final String SOUL_CAP = "ns_soulcap";

    public SummonStaffItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            ItemStack stack = player.getItemInHand(hand);
            // Commands (X key) go elsewhere; the call waits for its cooldown.
            if (player.getCooldowns().isOnCooldown(stack)) {
                return InteractionResult.FAIL;
            }
            handler.use(server, stack);
        }
        return InteractionResult.SUCCESS;
    }

    private static int[] souls(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        net.minecraft.nbt.CompoundTag tag = data.copyTag();
        int cap = tag.getIntOr(SOUL_CAP, 0);
        return cap <= 0 ? null : new int[]{tag.getIntOr(SOULS, 0), cap};
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return souls(stack) != null;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int[] s = souls(stack);
        return s == null ? 0 : Math.round(13f * Math.min(s[0], s[1]) / s[1]);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x6FE0D0;
    }
}
