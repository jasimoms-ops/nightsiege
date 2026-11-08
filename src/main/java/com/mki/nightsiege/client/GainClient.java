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
 * the six keys of the ability bar, the gain wheel (R) and the double jump of the jump boots. The scope lives in a mixin.
 */
public final class GainClient {

    /**
     * The ability keys (Tor 3.1, Simon 08.11.: "Nur Tasten"): Q, F, Y (sneak + Y: a fourth ability
     * beyond three), in the order of {@link com.mki.nightsiege.core.AbilityKeys.Key}. New names, so the
     * new defaults reach players whose options still hold the old R, G, Y, C, V, B bar.
     */
    public static final int SLOTS = 3;
    /** The index {@link #keyIndex} gives a box on sneak + Y (the fourth ability, Tor 3.1). */
    public static final int SNEAK_Y = 3;
    private static final KeyMapping[] SLOT_KEYS = new KeyMapping[SLOTS];
    private static final String[] SLOT_NAMES = {"key.nightsiege.skill_q", "key.nightsiege.skill_f", "key.nightsiege.skill_y"};
    /** Q, F, Y (Z on QWERTY); the fourth ability of the alchemist and the necromancer is sneak + Y (Tor 3.1). */
    private static final int[] SLOT_DEFAULTS = {GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_Z};
    /**
     * The gains with an ability of their own (balance update 2): at most three places ("Third Hand").
     * Tor 3.2 (Simon 08.11.): one key with a wheel instead of N, M, J, which the left hand could not
     * reach and which Simple Voice Chat also used. R: free in vanilla, in Simple Voice Chat (N, M, V, G,
     * H, Caps Lock) and in Crawl on Demand (Z), next to the ability keys Q, F, Y and X.
     */
    public static final int GAIN_KEYS = 3;
    private static KeyMapping gainWheelKey;
    /** The place used last (a tap uses it again); -1 before the first. */
    private static int lastGain = -1;
    /** Ticks the gain key has been held since its press, -1 while it is up. */
    private static int gainHeld = -1;
    /** The arrow or potion key being held (its bar index), -1 for none; whether the wheel turned meanwhile. */
    private static int heldSlot = -1;
    /** The key (Q, F, Y, extra) that holds it. */
    private static int heldKey;
    private static boolean scrolled;
    private static double scrollSum;
    private static boolean jumpWasDown;
    private static boolean doubleJumpUsed;
    /** Ticks in the air since the feet left the ground. */
    private static int airTicks;

    private GainClient() {
    }

    /** The F key (the class's F field: arrow rain, warrior art, remote, staff order). */
    public static KeyMapping orderKey() {
        return SLOT_KEYS[1];
    }

    /** The second ultimate of a crate: sneak + the ult key (Tor 3.1), so this is the ult key. */
    public static KeyMapping secondUltKey() {
        return NightSiegeClient.ultimateKey();
    }

    /** The arrow kinds' key (Y, Tor 3.1). */
    public static KeyMapping quiverKey() {
        return SLOT_KEYS[2];
    }

    /** Q, F, Y for 0, 1, 2; for {@link #SNEAK_Y} the Y key (shown with sneak in front). */
    public static KeyMapping slotKey(int index) {
        if (index == SNEAK_Y) {
            return SLOT_KEYS[2];
        }
        return index >= 0 && index < SLOTS ? SLOT_KEYS[index] : null;
    }

    /** The key of gain place {@code index}: since Tor 3.2 the one wheel key for every place. */
    public static KeyMapping gainKey(int index) {
        return index >= 0 && index < GAIN_KEYS ? gainWheelKey : null;
    }

    /** The gain key (hold: wheel, tap: the gain used last). */
    public static KeyMapping wheelKey() {
        return gainWheelKey;
    }

    /** Uses gain place {@code index} (the wheel, a tap) and keeps it for the next tap. */
    public static void useGain(int index) {
        lastGain = index;
        send("gain:" + index);
    }

    private static int gainCount() {
        com.mki.nightsiege.core.UltState s = UltHud.state();
        return s == null || s.gains == null ? 0 : Math.min(s.gains.size(), GAIN_KEYS);
    }

    /** True when bar key i lies on the same key as the gain wheel: then the wheel has it alone. */
    private static boolean underWheel(int i) {
        return gainWheelKey != null && !gainWheelKey.isUnbound()
                && SLOT_KEYS[i].saveString().equals(gainWheelKey.saveString());
    }

    public static void init(KeyMapping.Category category) {
        for (int i = 0; i < SLOTS; i++) {
            SLOT_KEYS[i] = KeyMappingHelper.registerKeyMapping(
                    new KeyMapping(SLOT_NAMES[i], SLOT_DEFAULTS[i], category));
        }
        // The old gain1..3 (N, M, J) are no longer registered: their lines in options.txt are simply
        // ignored, nothing to move over (the wheel key is new).
        gainWheelKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.gain_wheel", GLFW.GLFW_KEY_R, category));
        ClientTickEvents.END_CLIENT_TICK.register(GainClient::tick);
        // The gain cooldowns as their own small element at the right edge (does not touch the kit HUD).
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR,
                com.mki.nightsiege.game.ModContent.id("gain_cooldowns"), (gui, delta) -> GainCooldownHud.draw(gui));
    }

    /** The key index (Q, F, Y, extra) a box of the bar is on, by its key from the server. */
    public static int keyIndex(com.mki.nightsiege.core.UltState.Slot slot) {
        if (slot == null || slot.key == null) {
            return -1;
        }
        com.mki.nightsiege.core.AbilityKeys.Key[] keys = com.mki.nightsiege.core.AbilityKeys.Key.values();
        for (int k = 0, at = 0; k < keys.length; k++) {
            if (keys[k] == com.mki.nightsiege.core.AbilityKeys.Key.X) {
                continue;
            }
            if (keys[k].name().equals(slot.key)) {
                return at;
            }
            at++;
        }
        return -1;
    }

    /** The mapping a box of the bar fires on (Tor 3.1), or null. */
    public static KeyMapping keyOf(com.mki.nightsiege.core.UltState.Slot slot) {
        return slotKey(keyIndex(slot));
    }

    /** The bar index of the box on key k, -1 for none. */
    private static int slotOn(int k) {
        com.mki.nightsiege.core.UltState s = UltHud.state();
        if (s == null || s.slots == null) {
            return -1;
        }
        for (int i = 0; i < s.slots.size(); i++) {
            if (keyIndex(s.slots.get(i)) == k) {
                return i;
            }
        }
        return -1;
    }

    /** True when bar key i holds a set of choices (arrows, potions) that the mouse wheel goes through. */
    private static boolean scrollSlot(int i) {
        com.mki.nightsiege.core.UltState s = UltHud.state();
        return s != null && s.slots != null && i >= 0 && i < s.slots.size() && s.slots.get(i).scroll;
    }

    /** For the HUD: the bar key held for the wheel now, -1 for none. */
    public static int heldSlot() {
        return heldSlot;
    }

    /**
     * The mouse wheel (MouseScrollMixin): with the arrow or potion key held it changes the choice and
     * the hotbar stays where it is. True when it was taken.
     */
    public static boolean onScroll(double amount) {
        Minecraft client = Minecraft.getInstance();
        if (heldSlot < 0 || client.gui.screen() != null || amount == 0.0) {
            return false;
        }
        scrollSum += amount;
        while (Math.abs(scrollSum) >= 1.0) {
            int step = scrollSum > 0 ? -1 : 1;   // up: the one before
            send("cycle:" + heldSlot + ":" + step);
            scrollSum += step;
            scrolled = true;
        }
        if (!scrolled && Math.abs(scrollSum) > 0.0) {
            scrolled = true;   // a touchpad's small steps still count as turning
        }
        return true;
    }

    private static void send(String action) {
        if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
            ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload(action));
        }
    }

    private static void tick(Minecraft client) {
        // Q, F and Y (Tor 3.1): each fires the box of the bar the server put on it; sneak + Y the fourth
        // ability (alchemist, necromancer) when there is one; F without a box is the class's F field. The arrow and the potion key (Simon 30.10.):
        // held, the mouse wheel changes the choice; let go without turning it, a tap.
        for (int k = 0; k < SLOTS; k++) {
            while (SLOT_KEYS[k].consumeClick()) {
                if (client.gui.screen() != null) {
                    continue;
                }
                if (underWheel(k)) {
                    continue;   // the same key as the gain wheel: the wheel has it alone (Tor 3.2)
                }
                int i = k == 2 && client.options.keyShift.isDown() && slotOn(SNEAK_Y) >= 0 ? slotOn(SNEAK_Y) : slotOn(k);
                if (i < 0) {
                    com.mki.nightsiege.core.UltState s = UltHud.state();
                    if (k == 1 && s != null && s.hasAb2) {
                        send("ult2");
                    }
                } else if (scrollSlot(i)) {
                    heldSlot = i;
                    heldKey = k;
                    scrolled = false;
                    scrollSum = 0.0;
                } else {
                    send("slot:" + i);
                }
            }
        }
        if (heldSlot >= 0 && !SLOT_KEYS[heldKey].isDown()) {
            if (!scrolled) {
                send("slot:" + heldSlot);
            }
            heldSlot = -1;
        }
        // The gain wheel (Tor 3.2): held a quarter second the wheel opens, a tap uses the last gain.
        while (gainWheelKey.consumeClick()) {
            if (client.gui.screen() == null && gainHeld < 0) {
                gainHeld = 0;
            }
        }
        if (gainHeld >= 0) {
            int n = gainCount();
            if (client.gui.screen() != null) {
                gainHeld = -1;   // something else opened meanwhile
            } else if (!gainWheelKey.isDown()) {
                if (com.mki.nightsiege.core.GainWheelLogic.isTap(gainHeld)) {
                    // without gains the server says so ("No ability for the gain keys yet")
                    useGain(Math.max(0, com.mki.nightsiege.core.GainWheelLogic.tapIndex(lastGain, n)));
                }
                gainHeld = -1;
            } else if (++gainHeld >= com.mki.nightsiege.core.GainWheelLogic.HOLD_TICKS) {
                gainHeld = -1;
                if (n > 0) {
                    client.gui.setScreen(new GainWheelScreen(com.mki.nightsiege.core.GainWheelLogic.tapIndex(lastGain, n)));
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
