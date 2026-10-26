package com.mki.nightsiege.client;

import com.mki.nightsiege.core.MortarMode;
import com.mki.nightsiege.core.SpotterData;
import com.mki.nightsiege.game.CoreNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * The demolition expert's bird's-eye view (Simon, 26 October 2026), in the spirit of
 * Ravenswatch: the camera hangs above the base and always looks straight down. WASD moves
 * it, the mouse wheel zooms, a click calls the picked fire onto the block under the cursor.
 * The player's body stays where it stood, unguarded. Sneak or Esc leaves the view.
 *
 * <p>The camera is a client-side armour stand that is never added to the world; while the
 * screen is open, Minecraft looks through it.</p>
 */
public class SpotterScreen extends Screen {

    private static final int GOLD = 0xFFFFD86B;
    private static final int GREY = 0xFF8F879F;
    private static final int LIGHT = 0xFFCFC7DD;
    private static final int RED = 0xFFE05A5A;
    private static final int PANEL = 0xD0121019;
    private static final int LINE = 0xFF2C2740;
    private static final double MIN_HEIGHT = 10;
    private static final double MAX_HEIGHT = 50;

    private final SpotterData data;
    private final long[] readyAt;
    private ArmorStand eye;
    private double camX;
    private double camZ;
    private double zoomH = 26;
    private int selected;
    /** First spot of a barrage line, null when none is set. */
    private BlockPos lineStart;
    /** The block under the cursor (null: none). */
    private BlockPos hover;
    private long ticks;

    public SpotterScreen(SpotterData data) {
        super(Component.translatable("nightsiege.spotter.title"));
        this.data = data;
        this.readyAt = new long[data.modes.size()];
        for (int i = 0; i < data.modes.size(); i++) {
            readyAt[i] = data.modes.get(i).wait * 20L;
        }
        this.camX = data.startX;
        this.camZ = data.startZ;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        Minecraft client = Minecraft.getInstance();
        if (eye == null && client.level != null) {
            eye = new ArmorStand(client.level, camX, camY(), camZ);
            eye.setInvisible(true);
            eye.setYRot(180f);
            eye.setXRot(90f);
            eye.setOldPosAndRot();
            client.setCameraEntity(eye);
        }
    }

    @Override
    public void removed() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.setCameraEntity(client.player);
        }
        eye = null;
        super.removed();
    }

    /** No blur and no dark backdrop: the world is the view. */
    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
    }

    private double camY() {
        return data.centerY + zoomH;
    }

    // ------------------------------------------------------------------
    // input
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || eye == null) {
            onClose();
            return;
        }
        ticks++;
        if (down(client, client.options.keyShift)) {
            onClose();
            return;
        }
        double step = 0.035 * zoomH + 0.25;
        // The camera looks north: W is towards -Z, D towards +X.
        if (down(client, client.options.keyUp) || key(client, GLFW.GLFW_KEY_UP)) {
            camZ -= step;
        }
        if (down(client, client.options.keyDown) || key(client, GLFW.GLFW_KEY_DOWN)) {
            camZ += step;
        }
        if (down(client, client.options.keyLeft) || key(client, GLFW.GLFW_KEY_LEFT)) {
            camX -= step;
        }
        if (down(client, client.options.keyRight) || key(client, GLFW.GLFW_KEY_RIGHT)) {
            camX += step;
        }
        double dx = camX - data.centerX;
        double dz = camZ - data.centerZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > data.radius) {
            camX = data.centerX + dx * data.radius / d;
            camZ = data.centerZ + dz * data.radius / d;
        }
        eye.setOldPosAndRot();
        eye.setPos(camX, camY(), camZ);
        eye.setYRot(180f);
        eye.setXRot(90f);
        preview(client);
    }

    /** Whether the key bound to this mapping is held (key mappings get no events while a screen is open). */
    private static boolean down(Minecraft client, KeyMapping mapping) {
        try {
            InputConstants.Key bound = InputConstants.getKey(mapping.saveString());
            return bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() >= 0
                    && InputConstants.isKeyDown(client.getWindow(), bound.getValue());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean key(Minecraft client, int code) {
        return InputConstants.isKeyDown(client.getWindow(), code);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        zoomH = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, zoomH - scrollY * 2.5));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_9 && k - GLFW.GLFW_KEY_1 < data.modes.size()) {
            pick(k - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int bar = barIndexAt(event.x(), event.y());
        if (bar >= 0) {
            pick(bar);
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            lineStart = null;   // right click drops a half-drawn barrage line
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        BlockPos spot = pickBlock(event.x(), event.y());
        if (spot == null) {
            return true;
        }
        fire(spot);
        return true;
    }

    private void pick(int index) {
        if (index < 0 || index >= data.modes.size() || !data.modes.get(index).open) {
            return;
        }
        selected = index;
        lineStart = null;
    }

    private void fire(BlockPos spot) {
        SpotterData.Mode mode = data.modes.get(selected);
        if (readyAt[selected] > ticks) {
            return;
        }
        String action;
        if (MortarMode.BARRAGE.id().equals(mode.id)) {
            if (lineStart == null) {
                lineStart = spot;   // the first click sets the start, the second one fires
                return;
            }
            action = "spot:" + mode.id + ":" + lineStart.getX() + ":" + lineStart.getY() + ":" + lineStart.getZ()
                    + ":" + spot.getX() + ":" + spot.getY() + ":" + spot.getZ();
            lineStart = null;
        } else {
            action = "spot:" + mode.id + ":" + spot.getX() + ":" + spot.getY() + ":" + spot.getZ();
        }
        if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
            ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload(action));
        }
        if (mode.pause > 0) {
            readyAt[selected] = ticks + mode.pause * 20L;
        }
    }

    /** The block under the cursor: a ray from the camera through the mouse. */
    private BlockPos pickBlock(double mouseX, double mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || this.width <= 0 || this.height <= 0) {
            return null;
        }
        Vec3 from = client.gameRenderer.mainCamera().position();
        double fov = Math.toRadians(client.gameRenderer.mainCamera().getFov());
        double tanY = Math.tan(fov / 2.0);
        double tanX = tanY * this.width / (double) this.height;
        double nx = mouseX / this.width * 2.0 - 1.0;
        double ny = 1.0 - mouseY / this.height * 2.0;
        // Straight down, screen top towards north (-Z), screen right towards east (+X).
        Vec3 dir = new Vec3(nx * tanX, -1.0, -ny * tanY).normalize();
        BlockHitResult hit = client.level.clip(new ClipContext(from, from.add(dir.scale(200.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos();
    }

    /** Dust where the shells would land: a ring for the picked fire, a line for the barrage. */
    private void preview(Minecraft client) {
        if (ticks % 3 != 0) {
            return;
        }
        double mx = client.mouseHandler.getScaledXPos(client.getWindow());
        double my = client.mouseHandler.getScaledYPos(client.getWindow());
        hover = barIndexAt(mx, my) >= 0 ? null : pickBlock(mx, my);
        if (hover == null) {
            return;
        }
        SpotterData.Mode mode = data.modes.get(selected);
        int color = colorOf(mode.id);
        DustParticleOptions dust = new DustParticleOptions(color, 2.2f);
        double y = hover.getY() + 1.15;
        if (MortarMode.BARRAGE.id().equals(mode.id) && lineStart != null) {
            Vec3 a = Vec3.atCenterOf(lineStart);
            Vec3 b = Vec3.atCenterOf(hover);
            int n = (int) Math.max(2, a.distanceTo(b) * 1.5);
            for (int i = 0; i <= n; i++) {
                Vec3 p = a.add(b.subtract(a).scale(i / (double) n));
                client.level.addParticle(dust, p.x, y, p.z, 0, 0, 0);
            }
            return;
        }
        double r = Math.max(1.0, mode.spread);
        int n = (int) Math.max(10, r * 6);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            client.level.addParticle(dust, hover.getX() + 0.5 + Math.cos(a) * r, y, hover.getZ() + 0.5 + Math.sin(a) * r, 0, 0, 0);
        }
        client.level.addParticle(dust, hover.getX() + 0.5, y, hover.getZ() + 0.5, 0, 0, 0);
    }

    private static int colorOf(String mode) {
        return switch (mode) {
            case "barrage" -> 0xFF8A3A;
            case "area" -> 0xFFC44A;
            case "smoke" -> 0xC8C8C8;
            case "fire" -> 0xFF4A1A;
            default -> 0xFF2A2A;
        };
    }

    private static ItemStack iconOf(String mode) {
        return new ItemStack(switch (mode) {
            case "barrage" -> Items.IRON_BARS;
            case "area" -> Items.TNT;
            case "smoke" -> Items.CAMPFIRE;
            case "fire" -> Items.FIRE_CHARGE;
            default -> Items.TARGET;
        });
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    private static final int SLOT_W = 74;
    private static final int SLOT_H = 34;
    private static final int SLOT_GAP = 4;

    private int barLeft() {
        int total = data.modes.size() * SLOT_W + (data.modes.size() - 1) * SLOT_GAP;
        return (this.width - total) / 2;
    }

    private int barTop() {
        return this.height - SLOT_H - 30;
    }

    private int barIndexAt(double x, double y) {
        int top = barTop();
        if (y < top || y >= top + SLOT_H) {
            return -1;
        }
        int left = barLeft();
        for (int i = 0; i < data.modes.size(); i++) {
            int sx = left + i * (SLOT_W + SLOT_GAP);
            if (x >= sx && x < sx + SLOT_W) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        Minecraft client = Minecraft.getInstance();
        // Hurt: the screen edge flashes red, the body is standing unguarded.
        if (client.player != null && client.player.hurtTime > 0) {
            int a = 0x60 + client.player.hurtTime * 12;
            int c = (Math.min(255, a) << 24) | 0xC01010;
            gui.fill(0, 0, this.width, 4, c);
            gui.fill(0, this.height - 4, this.width, this.height, c);
            gui.fill(0, 0, 4, this.height, c);
            gui.fill(this.width - 4, 0, this.width, this.height, c);
            String warn = Component.translatable("nightsiege.spotter.hurt").getString();
            gui.text(font, warn, (this.width - font.width(warn)) / 2, 30, RED, true);
        }
        // Title and help.
        gui.fill(6, 6, 6 + Math.max(170, font.width(Component.translatable("nightsiege.spotter.help").getString()) / 4 * 3 + 12), 30, PANEL);
        gui.text(font, Component.translatable("nightsiege.spotter.title").getString(), 10, 9, GOLD, true);
        small(gui, font, Component.translatable("nightsiege.spotter.help").getString(), 10, 20, LIGHT, 0.75f);
        String info = Component.translatable("nightsiege.spotter.info", data.mortars,
                (int) Math.round(Math.sqrt(Math.pow(camX - data.centerX, 2) + Math.pow(camZ - data.centerZ, 2))), data.radius).getString();
        int iw = (int) (font.width(info) * 0.75f);
        gui.fill(this.width - iw - 14, 6, this.width - 6, 18, PANEL);
        small(gui, font, info, this.width - iw - 10, 9, LIGHT, 0.75f);

        // Crosshair on the cursor and the spot under it.
        if (hover != null) {
            String at = hover.getX() + " / " + hover.getY() + " / " + hover.getZ();
            small(gui, font, at, mouseX + 8, mouseY + 8, LIGHT, 0.7f);
        }
        if (lineStart != null) {
            String hint = Component.translatable("nightsiege.spotter.line_end").getString();
            gui.text(font, hint, (this.width - font.width(hint)) / 2, barTop() - 14, GOLD, true);
        }

        // The fire bar.
        int left = barLeft();
        int top = barTop();
        for (int i = 0; i < data.modes.size(); i++) {
            SpotterData.Mode mode = data.modes.get(i);
            int sx = left + i * (SLOT_W + SLOT_GAP);
            boolean sel = i == selected;
            gui.fill(sx - 1, top - 1, sx + SLOT_W + 1, top + SLOT_H + 1, sel ? GOLD : LINE);
            gui.fill(sx, top, sx + SLOT_W, top + SLOT_H, mode.open ? 0xE0181424 : 0xE0101014);
            gui.item(iconOf(mode.id), sx + 4, top + 4);
            String name = Component.translatable("nightsiege.mortarmode." + mode.id).getString();
            small(gui, font, (i + 1) + "  " + name, sx + 23, top + 6, mode.open ? (sel ? GOLD : 0xFFFFFFFF) : GREY, 0.7f);
            String sub;
            int color;
            long left2 = readyAt[i] - ticks;
            if (!mode.open) {
                sub = Component.translatable("nightsiege.spotter.locked").getString();
                color = GREY;
            } else if (left2 > 0) {
                sub = Component.translatable("nightsiege.spotter.wait", (left2 + 19) / 20).getString();
                color = RED;
                int h = (int) (SLOT_H * Math.min(1.0, left2 / Math.max(1.0, mode.pause * 20.0)));
                gui.fill(sx, top + SLOT_H - h, sx + SLOT_W, top + SLOT_H, 0x70000000);
            } else {
                sub = mode.pause > 0 ? Component.translatable("nightsiege.spotter.pause", mode.pause).getString()
                        : Component.translatable("nightsiege.spotter.nopause").getString();
                color = 0xFF7CD67C;
            }
            small(gui, font, sub, sx + 23, top + 18, color, 0.6f);
        }
        String foot = Component.translatable("nightsiege.spotter.foot").getString();
        int fw = (int) (font.width(foot) * 0.7f);
        small(gui, font, foot, (this.width - fw) / 2, top + SLOT_H + 6, GREY, 0.7f);
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, false);
        gui.pose().popMatrix();
    }

    /** For the test: aim the view and click a fire there, as a player would. */
    public void testFire(int modeIndex, BlockPos spot) {
        pick(modeIndex);
        fire(spot);
    }

    /** For the test: move the camera. */
    public void testMove(double x, double z, double h) {
        camX = x;
        camZ = z;
        zoomH = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, h));
    }
}
