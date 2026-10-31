package com.mki.nightsiege.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Writes one player's own data (inventory, stats, advancements) to disk now (Simon 31.10.: survival
 * safety): the kit swap and the reward crates are in the mod's save at once, the vanilla player data
 * only at the next autosave, so a crash in between lost or doubled the own things.
 */
@Mixin(PlayerList.class)
public interface PlayerListInvoker {

    @Invoker("save")
    void nightsiege$save(ServerPlayer player);
}
