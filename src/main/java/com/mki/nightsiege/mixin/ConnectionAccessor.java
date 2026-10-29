package com.mki.nightsiege.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The balance tool's team bots (29 October 2026) are real server players on a connection
 * without a network: an embedded channel that swallows every packet. The channel is set here.
 */
@Mixin(Connection.class)
public interface ConnectionAccessor {

    @Accessor("channel")
    void nightsiege$setChannel(Channel channel);
}
