package com.mki.nightsiege.client;

import com.mki.nightsiege.core.WheelData;
import com.mki.nightsiege.game.CoreNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Fähigkeit 3 (Simon, 27 October 2026): a short press picks the next choice as before; held
 * for a moment it asks the server for the wheel (arrow kinds, spells, potions, healing
 * abilities), which opens while the key is still down. Letting go over a choice picks it.
 */
public final class WheelClient {

    /** Ticks the key must be held before the wheel is asked for. */
    private static final int HOLD = 5;
    private static boolean wasDown;
    private static long pressedAt;
    private static boolean asked;
    /** For the client test: Fähigkeit 3 counts as held. */
    public static volatile boolean testHeld;

    private WheelClient() {
    }

    /** Whether the key bound to this mapping is physically held (keyboard or mouse). */
    static boolean physicallyDown(Minecraft client, KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) {
            return false;
        }
        if (testHeld && mapping == GainClient.quiverKey()) {
            return true;
        }
        try {
            InputConstants.Key key = InputConstants.getKey(mapping.saveString());
            long window = client.getWindow().handle();
            if (key.getType() == InputConstants.Type.MOUSE) {
                return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            }
            return key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0
                    && InputConstants.isKeyDown(client.getWindow(), key.getValue());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void tick(Minecraft client, KeyMapping key) {
        int clicks = 0;
        while (key.consumeClick()) {
            clicks++;
        }
        if (client.player == null || client.level == null) {
            wasDown = false;
            asked = false;
            return;
        }
        if (client.gui.screen() != null) {
            // Chat, inventory or the wheel itself: no presses counted here.
            wasDown = client.gui.screen() instanceof WheelScreen && wasDown;
            return;
        }
        boolean down = physicallyDown(client, key);
        long now = client.level.getGameTime();
        if (down && !wasDown) {
            pressedAt = now;
            asked = false;
        }
        if (down && !asked && now - pressedAt >= HOLD) {
            asked = true;
            send("wheel:open");
        }
        if (!down && wasDown && !asked) {
            send("quiver");   // a short press
        } else if (!down && !wasDown && clicks > 0) {
            send("quiver");   // pressed and let go within one tick
        }
        wasDown = down;
    }

    /** The server sent the wheel: it opens if the key is still held (otherwise it came too late). */
    public static void open(WheelData data) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || data == null || data.options.isEmpty() || client.gui.screen() != null
                || !physicallyDown(client, GainClient.quiverKey())) {
            return;
        }
        client.setScreenAndShow(new WheelScreen(data));
    }

    static void send(String action) {
        if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
            ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload(action));
        }
    }
}
