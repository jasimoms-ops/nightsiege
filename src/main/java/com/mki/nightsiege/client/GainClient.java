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
 * The client side of the crate gains and abilities: Fähigkeit 2 (F), the second-ult key (X),
 * the six keys of the ability bar and the double jump of the jump boots. The scope lives in a mixin.
 */
public final class GainClient {

    private static KeyMapping orderKey;
    /** The second ultimate from the crate stays on X of its own (Simon, 27 October 2026). */
    private static KeyMapping secondUltKey;
    /** The keys of the ability bar (27 October 2026, instead of Fähigkeit 3 and its wheel). */
    public static final int SLOTS = 6;
    private static final KeyMapping[] SLOT_KEYS = new KeyMapping[SLOTS];
    /** Around WASD: R, G, Y (Z on QWERTY), C, V, B. */
    private static final int[] SLOT_DEFAULTS = {GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_C,
            GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B};
    /** The gain keys (balance update 2, Simon 29.10.: N, M, J): the crate gains with an ability of their own. */
    public static final int GAIN_KEYS = 3;
    private static final KeyMapping[] GAIN_KEY_MAPPINGS = new KeyMapping[GAIN_KEYS];
    private static final int[] GAIN_DEFAULTS = {GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_J};
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

    /** The first bar key (the texts that still speak of "the choice key"). */
    public static KeyMapping quiverKey() {
        return SLOT_KEYS[0];
    }

    public static KeyMapping slotKey(int index) {
        return index >= 0 && index < SLOTS ? SLOT_KEYS[index] : null;
    }

    public static KeyMapping gainKey(int index) {
        return index >= 0 && index < GAIN_KEYS ? GAIN_KEY_MAPPINGS[index] : null;
    }

    public static void init(KeyMapping.Category category) {
        orderKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.ability2", GLFW.GLFW_KEY_F, category));   // Fähigkeit 2, the orders formerly on X (27 Oct 2026)
        secondUltKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.second_ult", GLFW.GLFW_KEY_X, category));
        for (int i = 0; i < SLOTS; i++) {
            SLOT_KEYS[i] = KeyMappingHelper.registerKeyMapping(
                    new KeyMapping("key.nightsiege.slot" + (i + 1), SLOT_DEFAULTS[i], category));
        }
        for (int i = 0; i < GAIN_KEYS; i++) {
            GAIN_KEY_MAPPINGS[i] = KeyMappingHelper.registerKeyMapping(
                    new KeyMapping("key.nightsiege.gain" + (i + 1), GAIN_DEFAULTS[i], category));
        }
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
        // The ability bar: each key its ability, at once (27 October 2026).
        for (int i = 0; i < SLOTS; i++) {
            while (SLOT_KEYS[i].consumeClick()) {
                if (client.gui.screen() == null) {
                    send("slot:" + i);
                }
            }
        }
        for (int i = 0; i < GAIN_KEYS; i++) {
            while (GAIN_KEY_MAPPINGS[i].consumeClick()) {
                if (client.gui.screen() == null) {
                    send("gain:" + i);
                }
            }
        }
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
