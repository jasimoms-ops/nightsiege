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

    /** Server to client, once a second: the ultimate slot next to the hotbar. */
    public record UltHudPayload(String json) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<UltHudPayload> TYPE =
                new CustomPacketPayload.Type<>(ModContent.id("ult_hud"));

        public static final StreamCodec<RegistryFriendlyByteBuf, UltHudPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, UltHudPayload::json, UltHudPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to client, every two seconds while a siege runs: whether this player
     * is under a blood moon. The client fades the red sky in and out on its own
     * and lets it set if the messages stop.
     */
    public record BloodMoonPayload(boolean active) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<BloodMoonPayload> TYPE =
                new CustomPacketPayload.Type<>(ModContent.id("blood_moon"));

        public static final StreamCodec<RegistryFriendlyByteBuf, BloodMoonPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, BloodMoonPayload::active, BloodMoonPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client to server: one action from the window.
     *
     * <p>The action is a short string such as {@code buy:double_shot},
     * {@code class:ARCHER}, {@code donate:10} or {@code dev:grant}. One message
     * type covers every button, so a new button never means new network code.</p>
     */
    public record CoreActionPayload(String action) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<CoreActionPayload> TYPE =
                new CustomPacketPayload.Type<>(ModContent.id("core_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CoreActionPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
                        CoreActionPayload::action, CoreActionPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CoreNetworking() {
    }

    private static boolean typesRegistered;

    /**
     * Registers both message types. The main entrypoint calls this, and it runs on
     * client and server alike; a second call is ignored, since Fabric refuses to
     * register a type twice and would stop the game from starting.
     */
    public static synchronized void registerTypes() {
        if (typesRegistered) {
            return;
        }
        typesRegistered = true;
        PayloadTypeRegistry.clientboundPlay().register(CoreUiPayload.TYPE, CoreUiPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UltHudPayload.TYPE, UltHudPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BloodMoonPayload.TYPE, BloodMoonPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CoreActionPayload.TYPE, CoreActionPayload.CODEC);
    }

    /** Server side handler for skill purchases. */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CoreActionPayload.TYPE,
                (payload, context) -> {
                    // Network threads must not touch world state directly.
                    context.server().execute(() ->
                            SiegeManager.get().onCoreAction(context.player(), payload.action()));
                });
    }
}
