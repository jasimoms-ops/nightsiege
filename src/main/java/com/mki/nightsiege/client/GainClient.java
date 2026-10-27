package com.mki.nightsiege.client;

import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.Gadgets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * The client side of the crate gains: the second-ult key (X), the quiver key (G)
 * and the double jump of the jump boots. The scope lives in a mixin.
 */
public final class GainClient {

    private static KeyMapping orderKey;
    /** The second ultimate from the crate stays on X of its own (Simon, 27 October 2026). */
    private static KeyMapping secondUltKey;
    private static KeyMapping quiverKey;
    private static boolean jumpWasDown;
    private static boolean doubleJumpUsed;
    /** Ticks in the air since the feet left the ground. */
    private static int airTicks;

    private GainClient() {
    }

    public static KeyMapping orderKey() {
        return orderKey;
    }

    public static KeyMapping secondUltKey() {
        return secondUltKey;
    }

    public static KeyMapping quiverKey() {
        return quiverKey;
    }

    public static void init(KeyMapping.Category category) {
        orderKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.ability2", GLFW.GLFW_KEY_F, category));   // Fähigkeit 2, the orders formerly on X (27 Oct 2026)
        secondUltKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.second_ult", GLFW.GLFW_KEY_X, category));
        quiverKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.ability3", GLFW.GLFW_KEY_Z, category));   // Fähigkeit 3, formerly G: the German Y key
        ClientTickEvents.END_CLIENT_TICK.register(GainClient::tick);
    }

    private static void send(String action) {
        if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
            ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload(action));
        }
    }

    private static void tick(Minecraft client) {
        while (orderKey.consumeClick()) {
            send("ult2");
        }
        while (secondUltKey.consumeClick()) {
            send("ult2x");
        }
        // Fähigkeit 3: a short press or the wheel (held), see WheelClient (27 October 2026).
        WheelClient.tick(client, quiverKey);
        LocalPlayer player = client.player;
        if (player == null) {
            jumpWasDown = false;
            return;
        }
        boolean down = client.options.keyJump.isDown();
        if (player.onGround() || player.isInWater() || player.onClimbable() || player.getAbilities().flying
                || player.isPassenger()) {
            doubleJumpUsed = false;
            airTicks = 0;
        } else if (++airTicks >= 2 && down && !jumpWasDown && !doubleJumpUsed && client.gui.screen() == null
                && Gadgets.flag(player.getItemBySlot(EquipmentSlot.FEET), Gadgets.DOUBLE_JUMP)) {
            // Not in the tick of the normal jump: at the end of that tick the feet have already
            // left the ground, so the press of the first jump used up the second one at once
            // (Simon's playtest, 25 October 2026: "the jump boots do not work").
            doubleJumpUsed = true;
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x * 1.1, 0.55, v.z * 1.1);
            player.resetFallDistance();
            for (int i = 0; i < 8; i++) {
                player.level().addParticle(ParticleTypes.CLOUD, player.getX() + (Math.random() - 0.5) * 0.6,
                        player.getY(), player.getZ() + (Math.random() - 0.5) * 0.6, 0, -0.05, 0);
            }
            send("jump");
        }
        jumpWasDown = down;
    }
}
