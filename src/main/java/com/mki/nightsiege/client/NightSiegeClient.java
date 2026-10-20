package com.mki.nightsiege.client;

import com.mki.nightsiege.core.CoreStats;
import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.Json;
import com.mki.nightsiege.game.ModContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Client side: the core window and the key for the ultimate. */
public class NightSiegeClient implements ClientModInitializer {

    /** Fires the ultimate of the player's path. R by default; rebindable under Controls. */
    private static KeyMapping ultimateKey;

    @Override
    public void onInitializeClient() {
        // The message types are registered by the main entrypoint, which also runs here.
        ClientPlayNetworking.registerGlobalReceiver(CoreNetworking.CoreUiPayload.TYPE,
                (payload, context) -> {
                    CoreStats stats = Json.fromJson(payload.json(), CoreStats.class);
                    if (stats == null) {
                        return;
                    }
                    // Network threads must not touch the screen directly.
                    context.client().execute(() -> {
                        Minecraft client = Minecraft.getInstance();
                        CoreScreen open = CoreScreen.openInstance();
                        if (open != null) {
                            open.update(stats);
                        } else {
                            client.setScreenAndShow(new CoreScreen(stats));
                        }
                    });
                });

        KeyMapping.Category category = KeyMapping.Category.register(ModContent.id("main"));
        ultimateKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.ultimate", GLFW.GLFW_KEY_R, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ultimateKey.consumeClick()) {
                if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
                    ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("ult"));
                }
            }
        });
    }
}
