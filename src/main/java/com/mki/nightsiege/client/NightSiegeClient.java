package com.mki.nightsiege.client;

import com.mki.nightsiege.core.CoreStats;
import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.Json;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client side: receives the core's state and opens the window. */
public class NightSiegeClient implements ClientModInitializer {

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
    }
}
