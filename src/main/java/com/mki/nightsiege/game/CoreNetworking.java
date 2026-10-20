package com.mki.nightsiege.game;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The two messages the core's window needs.
 *
 * <p>Both carry a single string. The window's whole state travels as JSON, so
 * adding a number to the screen later never means touching the network code.</p>
 */
public final class CoreNetworking {

    /** Server to client: everything the window shows. */
    public record CoreUiPayload(String json) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<CoreUiPayload> TYPE =
                new CustomPacketPayload.Type<>(ModContent.id("core_ui"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CoreUiPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CoreUiPayload::json, CoreUiPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: buy one skill. */
    public record BuySkillPayload(String nodeId) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<BuySkillPayload> TYPE =
                new CustomPacketPayload.Type<>(ModContent.id("buy_skill"));

        public static final StreamCodec<RegistryFriendlyByteBuf, BuySkillPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, BuySkillPayload::nodeId, BuySkillPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CoreNetworking() {
    }

    /** Registers both message types. Must run on both sides. */
    public static void registerTypes() {
        PayloadTypeRegistry.clientboundPlay().register(CoreUiPayload.TYPE, CoreUiPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BuySkillPayload.TYPE, BuySkillPayload.CODEC);
    }

    /** Server side handler for skill purchases. */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(BuySkillPayload.TYPE,
                (payload, context) -> {
                    // Network threads must not touch world state directly.
                    context.server().execute(() ->
                            SiegeManager.get().onBuySkill(context.player(), payload.nodeId()));
                });
    }
}
