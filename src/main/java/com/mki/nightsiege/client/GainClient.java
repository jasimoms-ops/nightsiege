package com.mki.nightsiege.client;

import com.mki.nightsiege.core.AbilityKeys.Key;
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

/**
 * The client side of the crate gains and abilities: the ability places 1-4, the second ult (R), the
 * gain places 1-3, the inventory key of the night (I) and the double jump of the jump boots. The
 * scope lives in a mixin. Every default key comes from {@link com.mki.nightsiege.core.AbilityKeys}.
 */
public final class GainClient {

    /**
     * The ability places 1-4 (Tor 3.2b, Chef 09.11. 01:38): Q, E, F, C by default, in the order of
     * {@link com.mki.nightsiege.core.AbilityKeys#ABILITY_PLACES}. New names, so the new defaults reach
     * players whose options still hold the old Q, F, Y.
     */
    public static final int SLOTS = 4;
    private static final KeyMapping[] SLOT_KEYS = new KeyMapping[SLOTS];
    /**
     * The gain places (Tor 3.2b, Simon 09.11.: "Gewinne auf die Tasten 1-3" instead of the wheel):
     * in the night 1, 2, 3 fire the gains, the hotbar choice on 1-3 waits until the morning.
     */
    public static final int GAIN_KEYS = 3;
    private static final KeyMapping[] GAIN_KEY = new KeyMapping[GAIN_KEYS];
    /** The second ultimate of a crate (R, Tor 3.2b: no sneak + X any more). */
    private static KeyMapping secondUltKey;
    /** The inventory in the night (I): E is an ability then (Tor 3.2b). */
    private static KeyMapping inventoryKey;
    /** The arrow or potion key being held (its bar index), -1 for none; whether the wheel turned meanwhile. */
    private static int heldSlot = -1;
    /** The key (place 1-4) that holds it. */
    private static int heldKey;
    private static boolean scrolled;
    private static double scrollSum;
    private static boolean jumpWasDown;
    private static boolean doubleJumpUsed;
    /** Ticks in the air since the feet left the ground. */
    private static int airTicks;

    private GainClient() {
    }

    /** Place 2 (E): the class's own field (arrow rain, warrior art, remote, staff order). */
    public static KeyMapping orderKey() {
        return SLOT_KEYS[1];
    }

    /** The second ultimate of a crate (R). */
    public static KeyMapping secondUltKey() {
        return secondUltKey;
    }

    /** The arrow kinds' key (place 3, F; the tanks have them on place 4). */
    public static KeyMapping quiverKey() {
        return SLOT_KEYS[2];
    }

    /** The inventory key of the night (I). */
    public static KeyMapping inventoryKey() {
        return inventoryKey;
    }

    /** The key of ability place {@code index} (0 = place 1), or null. */
    public static KeyMapping slotKey(int index) {
        return index >= 0 && index < SLOTS ? SLOT_KEYS[index] : null;
    }

    /** The key of gain place {@code index} (0 = key 1), or null. */
    public static KeyMapping gainKey(int index) {
        return index >= 0 && index < GAIN_KEYS ? GAIN_KEY[index] : null;
    }

    /** Uses gain place {@code index}. */
    public static void useGain(int index) {
        send("gain:" + index);
    }

    private static KeyMapping register(String name, Key key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, key.glfw(), category));
    }

    public static void init(KeyMapping.Category category) {
        for (int i = 0; i < SLOTS; i++) {
            SLOT_KEYS[i] = register("key.nightsiege.ability_" + (i + 1),
                    com.mki.nightsiege.core.AbilityKeys.ABILITY_PLACES.get(i), category);
        }
        secondUltKey = register("key.nightsiege.ult2", Key.ULT2, category);
        // The gain wheel (R) and the old gain1..3 are no longer registered: their lines in options.txt
        // are simply ignored, nothing to move over.
        for (int i = 0; i < GAIN_KEYS; i++) {
            GAIN_KEY[i] = register("key.nightsiege.gain_" + (i + 1),
                    com.mki.nightsiege.core.AbilityKeys.GAIN_PLACES.get(i), category);
        }
        inventoryKey = register("key.nightsiege.inventory", Key.INVENTORY, category);
        ClientTickEvents.END_CLIENT_TICK.register(GainClient::tick);
        // Tor 3.2b: in the kit inventory a click on a gain's field puts the next free gain on that place.
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
                net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((shown, event) -> {
                    int place = KitInventory.gainAt(event.x(), event.y());
                    if (place < 0) {
                        return true;
                    }
                    send("gainplace:" + place);
                    return false;
                });
            }
        });
        // The gain cooldowns as their own small element at the right edge (does not touch the kit HUD).
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR,
                com.mki.nightsiege.game.ModContent.id("gain_cooldowns"), (gui, delta) -> GainCooldownHud.draw(gui));
    }

    /** The ability place (0-3) a box of the bar is on, by its key from the server; -1 for none. */
    public static int keyIndex(com.mki.nightsiege.core.UltState.Slot slot) {
        if (slot == null || slot.key == null || slot.key.isEmpty()) {
            return -1;
        }
        try {
            return Key.valueOf(slot.key).place();
        } catch (IllegalArgumentException e) {
            return -1;   // a key name of an older server
        }
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

    /**
     * True when a press of these keys is an ability: only while the server gives the keys to Night
     * Siege (night, kit, fight). By day E opens the inventory and 1-3 pick the hotbar, nothing else.
     */
    private static boolean acting(Minecraft client) {
        return NightKeys.owned() && client.gui.screen() == null;
    }

    private static void tick(Minecraft client) {
        // Tor 3.2b: at night the hand never rests on places 1-3 (empty, they are the gain keys).
        if (NightKeys.owned() && client.player != null) {
            net.minecraft.world.entity.player.Inventory inv = client.player.getInventory();
            int held = com.mki.nightsiege.core.KitArrange.nightHeld(inv.getSelectedSlot(), inv.getSelectedSlot());
            if (held != inv.getSelectedSlot()) {
                inv.setSelectedSlot(held);
            }
        }
        // Places 1-4 (Tor 3.2b): each fires the box of the bar the server put on it; place 2 without
        // a box is the class's own field. The arrow and the potion key (Simon 30.10.): held, the mouse
        // wheel changes the choice; let go without turning it, a tap.
        for (int k = 0; k < SLOTS; k++) {
            while (SLOT_KEYS[k].consumeClick()) {
                if (!acting(client)) {
                    continue;
                }
                int i = slotOn(k);
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
        // R: the second ultimate of a crate (Tor 3.2b).
        while (secondUltKey.consumeClick()) {
            com.mki.nightsiege.core.UltState s = UltHud.state();
            if (acting(client) && s != null && s.has2) {
                send("ult2x");
            }
        }
        // 1, 2, 3: the gains on their places (Tor 3.2b); without a gain the server says so.
        for (int g = 0; g < GAIN_KEYS; g++) {
            while (GAIN_KEY[g].consumeClick()) {
                if (acting(client)) {
                    useGain(g);
                }
            }
        }
        // I: the inventory in the night, where E is an ability.
        while (inventoryKey.consumeClick()) {
            if (acting(client) && client.player != null) {
                client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
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
