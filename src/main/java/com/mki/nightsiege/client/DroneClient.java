package com.mki.nightsiege.client;

import com.mki.nightsiege.game.CoreNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The client side of the drone (Simon, 26 October 2026): who flies as one (their bodies are
 * not drawn), and the steering of one's own drone. W/S fly forward and back, A/D turn, the
 * mouse looks around, Ctrl sinks, Shift climbs; the attack button fires the crossbow.
 * Minecraft's own movement keys are held back while flying (KeyboardInputMixin).
 */
public final class DroneClient {

    private static final Set<UUID> DRONES = new HashSet<>();
    private static boolean sentDown;
    /** Ticks since the crossbow last fired (for the reload bar; the server decides). */
    private static int sinceShot = 100;
    /** Ticks of take-off left (the drone rises a little when it starts). */
    private static int lift;
    private static boolean wasDrone;

    /** Blocks per tick: slow, it is a little machine. */
    private static final double SPEED = 0.17;
    private static final double CLIMB = 0.13;
    private static final float TURN = 3.5f;
    private static final int RELOAD = 25;

    private DroneClient() {
    }

    public static void set(String ids) {
        DRONES.clear();
        if (ids == null || ids.isBlank()) {
            return;
        }
        for (String id : ids.split(",")) {
            try {
                DRONES.add(UUID.fromString(id.trim()));
            } catch (IllegalArgumentException ignored) {
                // a broken id is skipped
            }
        }
    }

    public static void clear() {
        DRONES.clear();
        sentDown = false;
    }

    public static boolean isDrone(UUID id) {
        return !DRONES.isEmpty() && DRONES.contains(id);
    }

    /** True while this client's own player flies as a drone. */
    public static boolean localDrone() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && isDrone(p.getUUID());
    }

    /** Start of every client tick: steer and tell the server about the attack button. */
    public static void tick(Minecraft client) {
        LocalPlayer p = client.player;
        if (p == null || !isDrone(p.getUUID())) {
            wasDrone = false;
            if (sentDown && ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
                ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("drone:0"));
            }
            sentDown = false;
            return;
        }
        sinceShot++;
        if (!wasDrone) {
            wasDrone = true;
            lift = 10;   // take-off: up from where the defender fell
        }
        boolean free = client.gui.screen() == null;
        boolean forward = free && client.options.keyUp.isDown();
        boolean back = free && client.options.keyDown.isDown();
        boolean left = free && client.options.keyLeft.isDown();
        boolean right = free && client.options.keyRight.isDown();
        boolean up = free && client.options.keyShift.isDown();
        boolean down = free && client.options.keySprint.isDown();
        if (left != right) {
            p.setYRot(p.getYRot() + (right ? TURN : -TURN));
        }
        double yaw = Math.toRadians(p.getYRot());
        double move = (forward ? SPEED : 0) - (back ? SPEED : 0);
        double rise = (up ? CLIMB : 0) - (down ? CLIMB : 0);
        if (lift > 0) {
            lift--;
            rise = Math.max(rise, CLIMB);
        }
        p.setDeltaMovement(new Vec3(-Math.sin(yaw) * move, rise, Math.cos(yaw) * move));

        boolean fire = free && client.options.keyAttack.isDown();
        if (fire && sinceShot >= RELOAD) {
            sinceShot = 0;
        }
        if (fire != sentDown && ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
            ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload(fire ? "drone:1" : "drone:0"));
            sentDown = fire;
        }
    }

    /** The entity ids of one's own drone rig (sent by the server). */
    private static int[] rig = new int[0];

    public static void setRig(String ids) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (String id : ids == null ? new String[0] : ids.split(",")) {
            try {
                out.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        rig = out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** How many pieces of the own rig this client moves itself (client test). */
    public static int rigFollowing() {
        Minecraft client = Minecraft.getInstance();
        int n = 0;
        for (int id : rig) {
            if (client.level != null && client.level.getEntity(id) instanceof net.minecraft.world.entity.Display) {
                n++;
            }
        }
        return n;
    }

    /**
     * End of every client tick: one's own drone rig sits on the player and turns with it right
     * away (27 October 2026: placed by the server alone, it trailed one ping behind).
     */
    public static void followRig(Minecraft client) {
        LocalPlayer p = client.player;
        if (p == null || client.level == null || !isDrone(p.getUUID()) || rig.length == 0) {
            return;
        }
        double h = p.getBbHeight();
        for (int id : rig) {
            if (client.level.getEntity(id) instanceof net.minecraft.world.entity.Display e) {
                e.setPos(p.getX(), p.getY() + h, p.getZ());
                e.xo = p.xo;
                e.yo = p.yo + h;
                e.zo = p.zo;
                e.xOld = p.xOld;
                e.yOld = p.yOld + h;
                e.zOld = p.zOld;
                e.yRotO = p.yRotO;
                e.setYRot(p.getYRot());
            }
        }
    }

    /** A brass frame and the crossbow's reload bar while flying. */
    public static void draw(GuiGraphicsExtractor gui) {
        if (!localDrone()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int brass = 0xC0B5893A;
        int dark = 0x90301E0E;
        int len = Math.min(w, h) / 8;
        int t = 3;
        // Four brass corners.
        gui.fill(6, 6, 6 + len, 6 + t, brass);
        gui.fill(6, 6, 6 + t, 6 + len, brass);
        gui.fill(w - 6 - len, 6, w - 6, 6 + t, brass);
        gui.fill(w - 6 - t, 6, w - 6, 6 + len, brass);
        gui.fill(6, h - 6 - t, 6 + len, h - 6, brass);
        gui.fill(6, h - 6 - len, 6 + t, h - 6, brass);
        gui.fill(w - 6 - len, h - 6 - t, w - 6, h - 6, brass);
        gui.fill(w - 6 - t, h - 6 - len, w - 6, h - 6, brass);
        String title = Component.translatable("nightsiege.drone.title").getString();
        gui.fill(w / 2 - font.width(title) / 2 - 6, 8, w / 2 + font.width(title) / 2 + 6, 20, dark);
        gui.text(font, title, w / 2 - font.width(title) / 2, 10, 0xFFE8C56B, true);
        String help = Component.translatable("nightsiege.drone.help").getString();
        int hw = (int) (font.width(help) * 0.7f);
        gui.pose().pushMatrix();
        gui.pose().translate(w / 2f - hw / 2f, 23);
        gui.pose().scale(0.7f, 0.7f);
        gui.text(font, help, 0, 0, 0xFFD8C9A8, true);
        gui.pose().popMatrix();
        // The crossbow's reload under the crosshair.
        int bw = 30;
        int bx = w / 2 - bw / 2;
        int by = h / 2 + 10;
        float ready = Math.min(1f, sinceShot / (float) RELOAD);
        gui.fill(bx - 1, by - 1, bx + bw + 1, by + 3, dark);
        gui.fill(bx, by, bx + (int) (bw * ready), by + 2, ready >= 1f ? 0xFFE8C56B : 0xFF8A6A2A);
    }
}
