package com.mki.nightsiege.game;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The handbook item: using it opens the handbook window on the client. */
public class GuideItem extends Item {

    /** Set by the client entrypoint; does nothing on a dedicated server. */
    public static Runnable opener = () -> {
    };

    public GuideItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            opener.run();
        }
        return InteractionResult.SUCCESS;
    }
}
