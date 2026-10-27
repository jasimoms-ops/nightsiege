package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeMachineKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * A spawn egg for one siege machine (concept 27 October 2026: "Kreativmodus: Spawn-Ei je
 * Maschine zum Ausprobieren"). In creative it sets the machine up where it is used; the
 * machine shoots at the core when it is near, else straight ahead. A try-out breaks no blocks
 * and goes when its hit box is struck down or with "/nightsiege maschinen weg".
 */
public class MachineEggItem extends Item {

    private final SiegeMachineKind kind;

    public MachineEggItem(SiegeMachineKind kind, Item.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public SiegeMachineKind kind() {
        return kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        if (!player.getAbilities().instabuild) {
            player.sendSystemMessage(Component.translatable("nightsiege.machine.egg_creative").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        SiegeManager m = SiegeManager.get();
        if (m == null) {
            return InteractionResult.FAIL;
        }
        Vec3 at = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        m.machines().trial(level, kind, at, player.getYRot());
        player.sendSystemMessage(Component.translatable("nightsiege.machine.egg_placed",
                Component.translatable(kind.translationKey())).withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
