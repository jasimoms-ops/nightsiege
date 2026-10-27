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
 * <p>While the screen is open, CameraMixin puts Minecraft's camera at {@link #cameraAt}
 * (detached, so the player's body shows and the hand does not).</p>
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
    /**
     * A narrow lens from higher up (Simon, 27 October 2026: walls leaned and tall buildings cut
     * into the picture): the view looks almost like a map, and the camera keeps above the roofs.
     */
    private static final float VIEW_FOV = 40f;
    /** Height per zoom step, so the narrow lens sees about as much as the old wide one. */
    private static final double HEIGHT_K = 1.9;
    /** Blocks the camera stays above the highest roof below it. */
    private static final double ROOF_GAP = 10;

    private final SpotterData data;
    private final long[] readyAt;
    /** The view open right now, for the camera. */
    private static SpotterScreen active;
    private double camX;
    private double camZ;
    private double oldX;
    private double oldY;
    private double oldZ;
    private double zoomH = 26;
    /** The camera's height right now: it glides to its target instead of jumping over towers. */
    private double camYv = Double.NaN;
    private boolean hudWasHidden;
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
        if (active != this) {
            camYv = targetY();
            oldX = camX;
            oldY = camYv;
            oldZ = camZ;
            // No hotbar, hearts or other overlays in the view (Simon, 27 October 2026); our own
            // panel shows the health instead.
            Minecraft client = Minecraft.getInstance();
            hudWasHidden = client.options.hideGui;
            client.options.hideGui = true;
        }
        active = this;
        cursor(false);   // the red X is the pointer (Simon, 26 October 2026)
    }

    @Override
    public void removed() {
        if (active == this) {
            active = null;
            Minecraft.getInstance().options.hideGui = hudWasHidden;
        }
        cursor(true);
        super.removed();
    }

    /** The lens of the view while it is open, or a negative number. */
    public static float viewFov() {
        return active == null ? -1f : VIEW_FOV;
    }

    /** True while the bird's-eye view is open (the HUD leaves out its own crosshair then). */
    public static boolean isOpen() {
        return active != null;
    }

    /** Shows or hides the system's mouse pointer over the game window. */
    private static void cursor(boolean visible) {
        try {
            GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_CURSOR,
                    visible ? GLFW.GLFW_CURSOR_NORMAL : GLFW.GLFW_CURSOR_HIDDEN);
        } catch (RuntimeException ignored) {
            // no window (tests without a display): nothing to hide
        }
    }

    /** Where the camera hangs this frame (between the last tick and this one), or null. */
    public static double[] cameraAt(float partialTick) {
        SpotterScreen view = active;
        if (view == null) {
            return null;
        }
        double t = Math.max(0.0, Math.min(1.0, partialTick));
        return new double[]{view.oldX + (view.camX - view.oldX) * t, view.oldY + (view.camY() - view.oldY) * t,
                view.oldZ + (view.camZ - view.oldZ) * t};
    }

    /** No blur and no dark backdrop: the world is the view. */
    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
    }

    private double camY() {
        return Double.isNaN(camYv) ? targetY() : camYv;
    }

    /** Where the camera wants to hang: the zoom over the core's height, but always above the roofs below. */
    private double targetY() {
        double y = data.centerY + zoomH * HEIGHT_K;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return y;
        }
        int top = Integer.MIN_VALUE;
        int reach = (int) Math.round(Math.max(6.0, zoomH * 0.5));
        for (int ox = -reach; ox <= reach; ox += 3) {
            for (int oz = -reach; oz <= reach; oz += 3) {
                int bx = (int) Math.floor(camX) + ox;
                int bz = (int) Math.floor(camZ) + oz;
                top = Math.max(top, client.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bx, bz));
            }
        }
        return top == Integer.MIN_VALUE ? y : Math.max(y, top + ROOF_GAP);
    }

    // ------------------------------------------------------------------
    // input
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            onClose();
            return;
        }
        ticks++;
        oldX = camX;
        oldY = camY();
        oldZ = camZ;
        camYv = camY() + (targetY() - camY()) * 0.3;
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

    private static final int SLOT_H = 26;
    private static final int SLOT_GAP = 3;
    private static final int SLOT_MIN = 64;

    /** Width of one fire slot: wide enough for the longest name at its small scale. */
    private int slotW() {
        Font font = Minecraft.getInstance().font;
        int widest = 0;
        for (int i = 0; i < data.modes.size(); i++) {
            String name = (i + 1) + " " + Component.translatable("nightsiege.mortarmode." + data.modes.get(i).id).getString();
            widest = Math.max(widest, (int) Math.ceil(font.width(name) * 0.7f));
        }
        int w = Math.max(SLOT_MIN, widest + 26);
        int fit = (this.width - 12 - (data.modes.size() - 1) * SLOT_GAP) / Math.max(1, data.modes.size());
        return Math.min(w, Math.max(40, fit));
    }

    private int barLeft() {
        int total = data.modes.size() * slotW() + (data.modes.size() - 1) * SLOT_GAP;
        return (this.width - total) / 2;
    }

    private int barTop() {
        return this.height - SLOT_H - 8;   // the hotbar is hidden in the view
    }

    private int barIndexAt(double x, double y) {
        int top = barTop();
        if (y < top || y >= top + SLOT_H) {
            return -1;
        }
        int left = barLeft();
        int w = slotW();
        for (int i = 0; i < data.modes.size(); i++) {
            int sx = left + i * (w + SLOT_GAP);
            if (x >= sx && x < sx + w) {
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
        boolean hurt = client.player != null && client.player.hurtTime > 0;
        if (hurt) {
            int a = 0x60 + client.player.hurtTime * 12;
            int c = (Math.min(255, a) << 24) | 0xC01010;
            gui.fill(0, 0, this.width, 4, c);
            gui.fill(0, this.height - 4, this.width, this.height, c);
            gui.fill(0, 0, 4, this.height, c);
            gui.fill(this.width - 4, 0, this.width, this.height, c);
        }

        // Top left: title, controls in two short lines.
        String title = Component.translatable("nightsiege.spotter.title").getString();
        String help1 = Component.translatable("nightsiege.spotter.help1").getString();
        String help2 = Component.translatable("nightsiege.spotter.help2").getString();
        int hw = Math.max(font.width(title), (int) Math.ceil(Math.max(font.width(help1), font.width(help2)) * 0.65f));
        panel(gui, 6, 6, 6 + hw + 10, 36);
        gui.text(font, title, 11, 10, GOLD, true);
        small(gui, font, help1, 11, 21, LIGHT, 0.65f);
        small(gui, font, help2, 11, 28, LIGHT, 0.65f);

        // Top right: mortars, distance, the body's health.
        String info = Component.translatable("nightsiege.spotter.info", data.mortars,
                (int) Math.round(Math.sqrt(Math.pow(camX - data.centerX, 2) + Math.pow(camZ - data.centerZ, 2))), data.radius).getString();
        String life = client.player == null ? "" : Component.translatable("nightsiege.spotter.life",
                (int) Math.ceil(client.player.getHealth()), (int) Math.ceil(client.player.getMaxHealth())).getString();
        int iw = (int) Math.ceil(Math.max(font.width(info), font.width(life)) * 0.7f);
        panel(gui, this.width - iw - 16, 6, this.width - 6, 29);
        small(gui, font, info, this.width - iw - 11, 10, LIGHT, 0.7f);
        float share = client.player == null ? 1f : client.player.getHealth() / Math.max(1f, client.player.getMaxHealth());
        int lifeColor = hurt ? RED : share > 0.6f ? 0xFF7CD67C : share > 0.3f ? GOLD : RED;
        small(gui, font, life, this.width - iw - 11, 19, lifeColor, 0.7f);
        if (hurt) {
            String warn = Component.translatable("nightsiege.spotter.hurt").getString();
            gui.text(font, warn, (this.width - font.width(warn)) / 2, 10, RED, true);
        }

        // The red X on the cursor, and the spot under it.
        redX(gui, mouseX, mouseY, readyAt[selected] > ticks);
        if (hover != null) {
            String at = hover.getX() + " / " + hover.getY() + " / " + hover.getZ();
            small(gui, font, at, mouseX + 9, mouseY + 7, LIGHT, 0.6f);
        }

        // Above the bar: what the picked fire does, or the second click of a barrage line.
        int left = barLeft();
        int top = barTop();
        int w = slotW();
        SpotterData.Mode picked = data.modes.get(selected);
        String line = lineStart != null ? Component.translatable("nightsiege.spotter.line_end").getString()
                : Component.translatable("nightsiege.mortarmode." + picked.id).getString() + " – "
                + Component.translatable("nightsiege.spotter.hint." + picked.id).getString() + " · "
                + (picked.pause > 0 ? Component.translatable("nightsiege.spotter.pause", picked.pause).getString()
                : Component.translatable("nightsiege.spotter.nopause").getString());
        int lw = (int) Math.ceil(font.width(line) * 0.7f);
        panel(gui, (this.width - lw) / 2 - 5, top - 14, (this.width + lw) / 2 + 5, top - 3);
        small(gui, font, line, (this.width - lw) / 2, top - 11, lineStart != null ? GOLD : LIGHT, 0.7f);

        // The fire bar.
        for (int i = 0; i < data.modes.size(); i++) {
            SpotterData.Mode mode = data.modes.get(i);
            int sx = left + i * (w + SLOT_GAP);
            boolean sel = i == selected;
            gui.fill(sx - 1, top - 1, sx + w + 1, top + SLOT_H + 1, sel ? GOLD : LINE);
            gui.fill(sx, top, sx + w, top + SLOT_H, mode.open ? 0xE0181424 : 0xE0101014);
            gui.pose().pushMatrix();
            gui.pose().translate(sx + 3, top + 5);
            gui.pose().scale(0.95f, 0.95f);
            gui.item(iconOf(mode.id), 0, 0);
            gui.pose().popMatrix();
            String name = (i + 1) + " " + Component.translatable("nightsiege.mortarmode." + mode.id).getString();
            small(gui, font, font.plainSubstrByWidth(name, (int) ((w - 24) / 0.7f)), sx + 22, top + 5,
                    mode.open ? (sel ? GOLD : 0xFFFFFFFF) : GREY, 0.7f);
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
                gui.fill(sx, top + SLOT_H - h, sx + w, top + SLOT_H, 0x70000000);
            } else {
                sub = Component.translatable("nightsiege.spotter.ready").getString();
                color = 0xFF7CD67C;
            }
            small(gui, font, font.plainSubstrByWidth(sub, (int) ((w - 24) / 0.6f)), sx + 22, top + 16, color, 0.6f);
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    /** A dark panel with a thin frame. */
    private static void panel(GuiGraphicsExtractor gui, int x0, int y0, int x1, int y1) {
        gui.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, LINE);
        gui.fill(x0, y0, x1, y1, PANEL);
    }

    /** A red X of 2-pixel strokes; dimmed while the picked fire still waits. */
    private static void redX(GuiGraphicsExtractor gui, int x, int y, boolean waiting) {
        int color = waiting ? 0x90E05050 : 0xFFFF2A2A;
        int shadow = 0x80000000;
        for (int i = -6; i <= 6; i++) {
            gui.fill(x + i - 1 + 1, y + i - 1 + 1, x + i + 1 + 1, y + i + 1 + 1, shadow);
            gui.fill(x + i - 1 + 1, y - i - 1 + 1, x + i + 1 + 1, y - i + 1 + 1, shadow);
        }
        for (int i = -6; i <= 6; i++) {
            gui.fill(x + i - 1, y + i - 1, x + i + 1, y + i + 1, color);
            gui.fill(x + i - 1, y - i - 1, x + i + 1, y - i + 1, color);
        }
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
        if (selected != modeIndex) {
            pick(modeIndex);
        }
        fire(spot);
    }

    /** For the test: move the camera. */
    public void testMove(double x, double z, double h) {
        camX = x;
        camZ = z;
        zoomH = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, h));
        camYv = targetY();
        oldX = camX;
        oldY = camYv;
        oldZ = camZ;
    }
}
