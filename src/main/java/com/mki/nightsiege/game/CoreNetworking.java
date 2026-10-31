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
                StreamCodec.composite(ByteBufCodecs.stringUtf8(262144), CoreUiPayload::json, CoreUiPayload::new);

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
     * Client to server: one action from the window.
     *
     * <p>The action is a short string such as {@code buy:double_shot},
     * {@code class:ARCHER}, {@code donate:10} or {@code dev:grant}. One message
     * type covers every button, so a new button never means new network code.</p>
     */
    /** Server to client: which event the sky of the player's dimension shows ("" for none). */
    public record EventSkyPayload(String event, int fade) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<EventSkyPayload> TYPE =
                new CustomPacketPayload.Type<>(ModContent.id("event_sky"));

        public static final StreamCodec<RegistryFriendlyByteBuf, EventSkyPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, EventSkyPayload::event,
                        ByteBufCodecs.VAR_INT, EventSkyPayload::fade, EventSkyPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: opens a window by kind ("classpick", "crate") with its state as JSON. */
    public record ScreenPayload(String kind, String json) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ScreenPayload> TYPE =
                new CustomPacketPayload.Type<>(ModContent.id("screen"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ScreenPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ScreenPayload::kind,
                        ByteBufCodecs.stringUtf8(262144), ScreenPayload::json, ScreenPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

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
        PayloadTypeRegistry.clientboundPlay().register(EventSkyPayload.TYPE, EventSkyPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScreenPayload.TYPE, ScreenPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CoreActionPayload.TYPE, CoreActionPayload.CODEC);
    }

    /**
     * (Simon 31.10.: survival safety 2) A modified client could send thousands of window actions a
     * second, and every one rebuilt the whole core window or wrote the save. Each player now has a small
     * bucket: up to {@link #BURST} actions at once, refilled by one per server tick (20 a second); the rest
     * is dropped here, before it reaches the server's task queue. A normal client never comes near it.
     */
    static final int BURST = 20;
    /** Per player: {tokens, tick of the last refill}. */
    private static final java.util.Map<java.util.UUID, long[]> BUCKETS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger ADMITTED = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger DROPPED = new java.util.concurrent.atomic.AtomicInteger();

    /** True when this action of the player may go on (one token taken), false when it is dropped. */
    static boolean admit(java.util.UUID player, long tick) {
        long[] bucket = BUCKETS.computeIfAbsent(player, id -> new long[]{BURST, tick});
        synchronized (bucket) {
            if (tick > bucket[1]) {
                bucket[0] = Math.min(BURST, bucket[0] + (tick - bucket[1]));
                bucket[1] = tick;
            }
            if (bucket[0] <= 0) {
                DROPPED.incrementAndGet();
                return false;
            }
            bucket[0]--;
            ADMITTED.incrementAndGet();
            return true;
        }
    }

    /** For the test: window actions let through since the start. */
    public static int admitted() {
        return ADMITTED.get();
    }

    /** For the test: window actions dropped by the bucket since the start. */
    public static int dropped() {
        return DROPPED.get();
    }

    /** Server side handler for skill purchases. */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CoreActionPayload.TYPE,
                (payload, context) -> {
                    if (!admit(context.player().getUUID(), context.server().getTickCount())) {
                        return;   // too many at once: dropped (survival safety 2)
                    }
                    // Network threads must not touch world state directly.
                    context.server().execute(() ->
                            SiegeManager.get().onCoreAction(context.player(), payload.action()));
                });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> BUCKETS.remove(handler.player.getUUID()));
    }
}
