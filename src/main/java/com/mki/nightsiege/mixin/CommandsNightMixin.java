package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mki.nightsiege.game.NightDamage;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;

/** What a command (/fill, /setblock, a command block) changes in a night stays: the night's mending leaves it. */
@Mixin(Commands.class)
public abstract class CommandsNightMixin {

    @WrapMethod(method = "performCommand")
    private void nightsiege$command(ParseResults<CommandSourceStack> parse, String command, Operation<Void> original) {
        NightDamage.playerBegin();
        try {
            original.call(parse, command);
        } finally {
            NightDamage.playerEnd();
        }
    }
}
