package com.mki.nightsiege.client;

import com.mki.nightsiege.core.WarLeaderData;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The war leader's screen (Kriegsführer, Simon 29 October 2026; concept in
 * claude/night-siege-kriegsfuehrer-konzept.md).
 *
 * <ul>
 *   <li>Ready round: who is in it and ready, "Los" (all ready, or after the wait with one ready).</li>
 *   <li>The round: the bird's-eye view of the demolition expert's binoculars (WASD, wheel), reaching
 *   the base's edge plus 70 blocks. At the top the gold, the wave and its time; on the left the shop
 *   (squads, machines, the boss, cards), on the right the own squads with their orders and the
 *   defenders. In the picture: the gathering points as red flags, the gates, the squads and the
 *   defenders with their life.</li>
 *   <li>A click on a flag picks the gathering point the shop buys for; a click on a squad picks it,
 *   then a click on the field (a gate, a piece of wall) is its target. G: the picked squad gathers
 *   again, Space: every gathering squad storms. Esc: the menu (give up).</li>
 *   <li>The end: who won, the records.</li>
 * </ul>
 */
public class WarLeaderScreen extends Screen {

    private static final int GOLD = PurpleUi.GOLD;
    private static final int GREY = PurpleUi.GREY;
    private static final int DIM = PurpleUi.DIM;
    private static final int LIGHT = PurpleUi.LIGHT;
    private static final int RED = PurpleUi.RED;
    private static final int GREEN = PurpleUi.GREEN;
    private static final int ORANGE = 0xFFFFB347;
    private static final int VIOLET = 0xFFB48CFF;
    private static final int PANEL = 0xD8121019;
    private static final int LINE = PurpleUi.LINE;
    private static final float VIEW_FOV = 40f;
    private static final double HEIGHT_K = 1.9;
    private static final double MIN_HEIGHT = 12;
    private static final double ROOF_GAP = 10;
    private static final String[] TABS = {"squad", "machine", "boss", "card"};
    private static final int SHOP_W = 138;
    private static final int SIDE_W = 146;
    private static final int ROW_H = 13;

    /** The open screen (the camera follows it). */
    private static WarLeaderScreen active;
    /** The last data while the screen is shut (another screen open), and when it may open again by itself. */
    private static WarLeaderData pending;
    private static long reopenAfter;

    private WarLeaderData data;
    private double camX;
    private double camZ;
    private double oldX;
    private double oldY;
    private double oldZ;
    private double zoomH = 40;
    private double camYv = Double.NaN;
    private long ticks;
    private int reportedX = Integer.MIN_VALUE;
    private int reportedZ = Integer.MIN_VALUE;
    private long reportedAt;
    private int point;
    private int squad = -1;
    private String tab = "squad";
    /** The first row of the shop shown (the list scrolls when the screen is low). */
    private int shopFirst;
    /** The first own squad shown on the right. */
    private int squadFirst;
    private boolean menu;
    private boolean confirmGiveUp;
    private String notice = "";
    private long noticeUntil;
    private BlockPos hover;
    private boolean placed;
    /** The frame's partial tick (the icons sit where the camera is this frame). */
    private float partial;

    public WarLeaderScreen(WarLeaderData data) {
        super(Component.translatable("nightsiege.war.title"));
        this.data = data;
        this.camX = data.startX != 0 ? data.startX : data.centerX + 0.5;
        this.camZ = data.startZ != 0 ? data.startZ : data.centerZ + 0.5;
    }

    // ------------------------------------------------------------------ from the server

    /** "warleader" (open) and "warleader_update" (refresh). */
    public static void receive(WarLeaderData data, boolean open) {
        if (data == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof WarLeaderScreen screen) {
            screen.update(data);
            return;
        }
        boolean round = "prep".equals(data.phase) || "wave".equals(data.phase);
        boolean free = client.gui.screen() == null && System.currentTimeMillis() >= reopenAfter;
        if (open || (round && free) || "end".equals(data.phase)) {
            pending = null;
            client.setScreenAndShow(new WarLeaderScreen(data));
        } else {
            pending = round ? data : null;   // the round's view comes back by itself, the ready round's window does not
        }
    }

    /** "warleader_close": the ready round went. */
    public static void closeAll() {
        pending = null;
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof WarLeaderScreen) {
            client.setScreenAndShow(null);
        }
    }

    /** Every client tick: a round shut by another screen opens again once the game is free. */
    public static void tickClosed(Minecraft client) {
        if (pending != null && client.gui.screen() == null && client.player != null && System.currentTimeMillis() >= reopenAfter) {
            WarLeaderData d = pending;
            pending = null;
            client.setScreenAndShow(new WarLeaderScreen(d));
        }
        if (client.player == null) {
            pending = null;
        }
    }

    public void update(WarLeaderData fresh) {
        boolean wasView = inView();
        this.data = fresh;
        if (!wasView && inView()) {
            camX = fresh.startX != 0 ? fresh.startX : fresh.centerX + 0.5;
            camZ = fresh.startZ != 0 ? fresh.startZ : fresh.centerZ + 0.5;
            camYv = targetY();
            oldX = camX;
            oldY = camYv;
            oldZ = camZ;
            cursor(false);
        }
        if (!inView()) {
            cursor(true);
        }
        if (point >= fresh.points.size()) {
            point = 0;
        }
        if (squad >= 0 && fresh.squads.stream().noneMatch(s -> s.id == squad)) {
            squad = -1;
        }
    }

    /** The bird's-eye view is on: the build-up and the waves. */
    private boolean inView() {
        return data != null && ("prep".equals(data.phase) || "wave".equals(data.phase));
    }

    // ------------------------------------------------------------------ the camera (CameraMixin through SpotterScreen)

    public static double[] cameraAt(float partialTick) {
        WarLeaderScreen view = active;
        if (view == null || !view.inView()) {
            return null;
        }
        double t = Math.max(0.0, Math.min(1.0, partialTick));
        return new double[]{view.oldX + (view.camX - view.oldX) * t, view.oldY + (view.camY() - view.oldY) * t,
                view.oldZ + (view.camZ - view.oldZ) * t};
    }

    public static float viewFov() {
        return active == null || !active.inView() ? -1f : VIEW_FOV;
    }

    public static boolean isViewOpen() {
        return active != null && active.inView();
    }

    private double camY() {
        return Double.isNaN(camYv) ? targetY() : camYv;
    }

    /** As high as the zoom asks, but never into the fog of the render distance and always above the roofs. */
    private double maxZoom() {
        int chunks = 12;
        try {
            chunks = Minecraft.getInstance().options.renderDistance().get();
        } catch (RuntimeException ignored) {
            // the default
        }
        return Math.max(30.0, Math.min(110.0, chunks * 16 * 0.72 / HEIGHT_K));
    }

    private double targetY() {
        double y = data.centerY + zoomH * HEIGHT_K;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return y;
        }
        int top = Integer.MIN_VALUE;
        int reach = (int) Math.round(Math.max(6.0, zoomH * 0.4));
        for (int ox = -reach; ox <= reach; ox += 4) {
            for (int oz = -reach; oz <= reach; oz += 4) {
                top = Math.max(top, client.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        (int) Math.floor(camX) + ox, (int) Math.floor(camZ) + oz));
            }
        }
        return top == Integer.MIN_VALUE ? y : Math.max(y, top + ROOF_GAP);
    }

    // ------------------------------------------------------------------ open and shut

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
        }
        active = this;
        if (inView()) {
            cursor(false);
            Minecraft.getInstance().smartCull = false;
            reportCamera(true);
        }
    }

    @Override
    public void removed() {
        if (active == this) {
            active = null;
        }
        cursor(true);
        Minecraft.getInstance().smartCull = true;
        super.removed();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (!inView()) {
            super.extractBackground(gui, mouseX, mouseY, delta);
        }
    }

    private static void cursor(boolean visible) {
        try {
            GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_CURSOR,
                    visible ? GLFW.GLFW_CURSOR_NORMAL : GLFW.GLFW_CURSOR_HIDDEN);
        } catch (RuntimeException ignored) {
            // no window (tests)
        }
    }

    private static void send(String action) {
        if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
            ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload(action));
        }
    }

    private void reportCamera(boolean now) {
        int cx = (int) Math.floor(camX) >> 4;
        int cz = (int) Math.floor(camZ) >> 4;
        if (now || cx != reportedX || cz != reportedZ || ticks - reportedAt >= 20) {
            reportedX = cx;
            reportedZ = cz;
            reportedAt = ticks;
            send(String.format(Locale.ROOT, "war:cam:%.1f:%.1f", camX, camZ));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            onClose();
            return;
        }
        ticks++;
        if (!inView()) {
            return;
        }
        oldX = camX;
        oldY = camY();
        oldZ = camZ;
        zoomH = Math.min(zoomH, maxZoom());
        camYv = camY() + (targetY() - camY()) * 0.3;
        if (!menu) {
            double step = 0.03 * zoomH + 0.3;
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
        }
        double dx = camX - (data.centerX + 0.5);
        double dz = camZ - (data.centerZ + 0.5);
        double d = Math.sqrt(dx * dx + dz * dz);
        double r = Math.max(20, data.radius);
        if (d > r) {
            camX = data.centerX + 0.5 + dx * r / d;
            camZ = data.centerZ + 0.5 + dz * r / d;
        }
        reportCamera(false);
        double mx = client.mouseHandler.getScaledXPos(client.getWindow());
        double my = client.mouseHandler.getScaledYPos(client.getWindow());
        hover = overPanel(mx, my) ? null : pickBlock(mx, my);
    }

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
        if (inView() && mouseX >= sideX() && mouseX < sideX() + SIDE_W && mouseY >= shopTop() && mouseY < squadsBottom()) {
            squadFirst = Math.max(0, Math.min(Math.max(0, data.squads.size() - squadRows()), squadFirst - (int) Math.signum(scrollY)));
            return true;
        }
        if (inView() && mouseX >= shopX() && mouseX < shopX() + SHOP_W && mouseY >= shopTop() && mouseY < shopBottom()) {
            shopFirst = Math.max(0, Math.min(Math.max(0, shown().size() - shopRows()), shopFirst - (int) Math.signum(scrollY)));
            return true;
        }
        if (inView()) {
            zoomH = Math.max(MIN_HEIGHT, Math.min(maxZoom(), zoomH - scrollY * 3.0));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (!inView()) {
            if ("lobby".equals(data.phase) && k == GLFW.GLFW_KEY_ENTER && data.canGo) {
                send("war:go");
                return true;
            }
            return super.keyPressed(event);
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            menu = !menu;
            confirmGiveUp = false;
            return true;
        }
        if (menu) {
            return true;
        }
        if (k == GLFW.GLFW_KEY_SPACE) {
            send("war:storm");
            click(0.8f);
            return true;
        }
        if (k == GLFW.GLFW_KEY_G && squad >= 0) {
            send("war:order:" + squad + ":gather");
            click(1.0f);
            return true;
        }
        if (k == GLFW.GLFW_KEY_TAB) {
            int i = List.of(TABS).indexOf(tab);
            tab = TABS[(i + 1) % TABS.length];
            shopFirst = 0;
            return true;
        }
        if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_9 && k - GLFW.GLFW_KEY_1 < data.points.size()) {
            point = k - GLFW.GLFW_KEY_1;
            WarLeaderData.Point p = data.points.get(point);
            camX = p.x + 0.5;
            camZ = p.z + 0.5;
            return true;
        }
        if (k == GLFW.GLFW_KEY_H) {
            camX = data.centerX + 0.5;
            camZ = data.centerZ + 0.5;
            return true;
        }
        return true;   // the movement keys are read in tick()
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        boolean left = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        if (!inView()) {
            return clickPanel(x, y) || super.mouseClicked(event, doubleClick);
        }
        if (menu) {
            menuClick(x, y);
            return true;
        }
        if (!left) {
            squad = -1;   // right click: no squad picked
            return true;
        }
        if (shopClick(x, y) || sideClick(x, y)) {
            return true;
        }
        if (overPanel(x, y)) {
            return true;
        }
        // In the picture: a flag, a squad, else the target of the picked squad.
        int[] flag = nearestFlag(x, y);
        if (flag != null) {
            point = flag[0];
            click(1.3f);
            return true;
        }
        int picked = nearestSquad(x, y);
        if (picked >= 0) {
            squad = picked;
            click(1.2f);
            return true;
        }
        if (squad >= 0) {
            BlockPos at = pickBlock(x, y);
            WarLeaderData.Squad s = squadById(squad);
            if (at != null && s != null && !s.ownWill) {
                send("war:order:" + squad + ":target:" + at.getX() + ":" + at.getZ());
                click(0.9f);
            }
        }
        return true;
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.25f));
    }

    private WarLeaderData.Squad squadById(int id) {
        for (WarLeaderData.Squad s : data.squads) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the picture

    /** Screen spot of a world spot (null behind the camera or far off the screen). */
    private int[] project(double wx, double wy, double wz) {
        double[] cam = cameraAt(partial);
        if (cam == null || this.width <= 0 || this.height <= 0) {
            return null;
        }
        double dy = cam[1] - wy;
        if (dy < 1.0) {
            return null;
        }
        double fov = Math.toRadians(Minecraft.getInstance().gameRenderer.mainCamera().getFov());
        double tanY = Math.tan(fov / 2.0);
        double tanX = tanY * this.width / (double) this.height;
        double nx = (wx - cam[0]) / (tanX * dy);
        double ny = -(wz - cam[2]) / (tanY * dy);
        if (Math.abs(nx) > 1.05 || Math.abs(ny) > 1.05) {
            return null;
        }
        return new int[]{(int) Math.round((nx + 1.0) / 2.0 * this.width), (int) Math.round((1.0 - ny) / 2.0 * this.height)};
    }

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
        Vec3 dir = new Vec3(nx * tanX, -1.0, -ny * tanY).normalize();
        BlockHitResult hit = client.level.clip(new ClipContext(from, from.add(dir.scale(400.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, client.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private int[] nearestFlag(double x, double y) {
        int best = -1;
        double bestD = 10 * 10;
        for (WarLeaderData.Point p : data.points) {
            int[] s = project(p.x + 0.5, p.y, p.z + 0.5);
            if (s == null) {
                continue;
            }
            double dx = s[0] - x;
            double dy = s[1] - 6 - y;
            double d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = p.index;
            }
        }
        return best < 0 ? null : new int[]{best};
    }

    private int nearestSquad(double x, double y) {
        int best = -1;
        double bestD = 9 * 9;
        for (WarLeaderData.Squad s : data.squads) {
            if ("coming".equals(s.order)) {
                continue;
            }
            int[] p = project(s.x, groundY(s.x, s.z), s.z);
            if (p == null) {
                continue;
            }
            double d = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y);
            if (d < bestD) {
                bestD = d;
                best = s.id;
            }
        }
        return best;
    }

    private double groundY(double x, double z) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return data.centerY;
        }
        return client.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(x), (int) Math.floor(z));
    }

    // ------------------------------------------------------------------ layout

    private int shopX() {
        return 6;
    }

    private int shopTop() {
        return 34;
    }

    private int shopBottom() {
        return this.height - 26;
    }

    private int sideX() {
        return this.width - SIDE_W - 6;
    }

    private boolean overPanel(double x, double y) {
        if (x >= shopX() - 1 && x < shopX() + SHOP_W + 1 && y >= shopTop() - 1 && y < shopBottom() + 1) {
            return true;
        }
        if (x >= sideX() - 1 && x < sideX() + SIDE_W + 1 && y >= shopTop() - 1 && y < shopBottom() + 1) {
            return true;
        }
        int bw = topW();
        return y < 30 && x >= (this.width - bw) / 2.0 - 2 && x < (this.width + bw) / 2.0 + 2;
    }

    private int topW() {
        return Math.min(this.width - 2 * (SHOP_W + 14), 260);
    }

    /** Rows of the shop that fit. */
    private int shopRows() {
        return Math.max(3, (shopBottom() - 24 - (shopTop() + 30)) / ROW_H);
    }

    private List<WarLeaderData.Item> shown() {
        List<WarLeaderData.Item> out = new ArrayList<>();
        for (WarLeaderData.Item it : data.shop) {
            if (tab.equals(it.group)) {
                out.add(it);
            }
        }
        return out;
    }

    private boolean shopClick(double x, double y) {
        int x0 = shopX();
        if (x < x0 || x >= x0 + SHOP_W || y < shopTop() || y >= shopBottom()) {
            return false;
        }
        int tabW = SHOP_W / TABS.length;
        if (y < shopTop() + 13) {
            int i = (int) ((x - x0) / tabW);
            if (i >= 0 && i < TABS.length) {
                tab = TABS[i];
                shopFirst = 0;
                click(1.1f);
            }
            return true;
        }
        int rowTop = shopTop() + 30;
        int i = (int) ((y - rowTop) / ROW_H);
        List<WarLeaderData.Item> items = shown();
        if (y >= rowTop && i >= 0 && i < shopRows() && shopFirst + i < items.size()) {
            WarLeaderData.Item it = items.get(shopFirst + i);
            if ("ok".equals(it.state)) {
                send("war:buy:" + it.id + ":" + point);
                click(1.5f);
            } else {
                note(stateText(it));
                click(0.5f);
            }
        }
        return true;
    }

    private boolean sideClick(double x, double y) {
        int x0 = sideX();
        if (x < x0 || x >= x0 + SIDE_W || y < shopTop() || y >= shopBottom()) {
            return false;
        }
        // The two buttons under the squads.
        int by = squadsBottom() + 3;
        if (y >= by && y < by + 14) {
            int half = (SIDE_W - 12) / 2;
            if (x < x0 + 4 + half) {
                if (squad >= 0) {
                    send("war:order:" + squad + ":gather");
                    click(1.0f);
                }
            } else {
                send("war:storm");
                click(0.8f);
            }
            return true;
        }
        int rowTop = shopTop() + 16;
        int i = (int) ((y - rowTop) / 12);
        if (y >= rowTop && i >= 0 && i < squadRows() && squadFirst + i < data.squads.size()) {
            WarLeaderData.Squad s = data.squads.get(squadFirst + i);
            squad = s.id;
            if (!"coming".equals(s.order)) {
                camX = s.x;
                camZ = s.z;
            }
            click(1.2f);
        }
        return true;
    }

    private int squadRows() {
        int space = (shopBottom() - shopTop()) / 2 - 36;
        return Math.max(3, space / 12);
    }

    private int squadsBottom() {
        return shopTop() + 16 + squadRows() * 12;
    }

    private void note(String text) {
        notice = text;
        noticeUntil = ticks + 60;
    }

    private String stateText(WarLeaderData.Item it) {
        return switch (it.state) {
            case "poor" -> tr("nightsiege.war.state.poor", it.price - data.gold);
            case "level" -> tr("nightsiege.war.state.level", it.from);
            case "wave" -> tr("nightsiege.war.state.wave", it.from);
            case "used" -> tr("nightsiege.war.state.used");
            case "busy" -> tr("nightsiege.war.state.busy");
            case "full" -> tr("squad".equals(it.group) ? "nightsiege.war.state.full" : "nightsiege.war.state.machines", data.machinesMax);
            default -> "";
        };
    }

    // ------------------------------------------------------------------ the menu (Esc)

    private int[] menuBox() {
        int w = 170;
        int h = 86;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        return new int[]{x, y, w, h};
    }

    private void menuClick(double x, double y) {
        int[] b = menuBox();
        int bx = b[0] + 10;
        int bw = b[2] - 20;
        int y0 = b[1] + 20;
        if (x < bx || x >= bx + bw) {
            return;
        }
        if (y >= y0 && y < y0 + 16) {
            menu = false;
            confirmGiveUp = false;
        } else if (y >= y0 + 20 && y < y0 + 36) {
            // A moment without the screen (the game's own menu with Esc), then it comes back.
            menu = false;
            reopenAfter = System.currentTimeMillis() + 15000L;
            pending = data;
            onClose();
        } else if (y >= y0 + 40 && y < y0 + 56) {
            if (confirmGiveUp) {
                send("war:giveup");
                menu = false;
            }
            confirmGiveUp = !confirmGiveUp;
        }
    }

    // ------------------------------------------------------------------ the lobby and the end

    private int[] panelBox() {
        int w = Math.min(this.width - 20, 280);
        int rows = "end".equals(data.phase) ? data.lines.size() + 2 : Math.max(1, data.lobby.size()) + 3;
        int h = Math.min(this.height - 20, 54 + rows * 12);
        return new int[]{(this.width - w) / 2, (this.height - h) / 2, w, h};
    }

    private boolean clickPanel(double x, double y) {
        int[] b = panelBox();
        int by = b[1] + b[3] - 22;
        if (y < by || y >= by + 16) {
            return false;
        }
        if ("end".equals(data.phase)) {
            int bw = 90;
            int bx = (this.width - bw) / 2;
            if (x >= bx && x < bx + bw) {
                onClose();
                return true;
            }
            return false;
        }
        int bw = (b[2] - 30) / 2;
        int goX = b[0] + 10;
        int cancelX = goX + bw + 10;
        if (x >= goX && x < goX + bw && data.canGo) {
            send("war:go");
            click(1.4f);
            return true;
        }
        if (x >= cancelX && x < cancelX + bw) {
            send("war:cancel");
            onClose();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        partial = delta;
        if (!inView()) {
            drawPanel(gui, font, mouseX, mouseY);
            super.extractRenderState(gui, mouseX, mouseY, delta);
            return;
        }
        if (!placed) {
            placed = true;
            camYv = targetY();
        }
        drawWorld(gui, font);
        drawTop(gui, font);
        drawShop(gui, font, mouseX, mouseY);
        drawSide(gui, font, mouseX, mouseY);
        drawHint(gui, font);
        if (menu) {
            drawMenu(gui, font, mouseX, mouseY);
        } else {
            cross(gui, mouseX, mouseY, squad >= 0 ? RED : GOLD);
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void drawPanel(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        int[] b = panelBox();
        PurpleUi.window(gui, b[0], b[1], b[0] + b[2], b[1] + b[3]);
        int x = b[0] + 10;
        int y = b[1] + 8;
        if ("end".equals(data.phase)) {
            String head = tr("won".equals(data.result) ? "nightsiege.war.end.title_won"
                    : "lost".equals(data.result) ? "nightsiege.war.end.title_lost" : "nightsiege.war.end.title_aborted");
            gui.text(font, head, (this.width - font.width(head)) / 2, y, "won".equals(data.result) ? GOLD : "lost".equals(data.result) ? RED : GREY, true);
            y += 18;
            for (String line : data.lines) {
                String text = lineText(line);
                for (net.minecraft.util.FormattedCharSequence part : font.split(net.minecraft.network.chat.FormattedText.of(text), b[2] - 20)) {
                    gui.text(font, part, x, y, LIGHT, false);
                    y += 10;
                }
                y += 2;
            }
            int bw = 90;
            int bx = (this.width - bw) / 2;
            int by = b[1] + b[3] - 22;
            boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 16;
            PurpleUi.button(gui, font, bx, by, bw, 16, tr("nightsiege.war.close"), true, over, true);
            return;
        }
        // The ready round.
        String head = tr("nightsiege.war.lobby_title");
        gui.text(font, head, (this.width - font.width(head)) / 2, y, GOLD, true);
        y += 13;
        small(gui, font, tr("nightsiege.war.lobby_sub", data.radius), x, y, GREY, 0.75f);
        y += 12;
        if (data.lobby.isEmpty()) {
            small(gui, font, tr("nightsiege.war.lobby_nobody", data.radius), x, y, ORANGE, 0.8f);
            y += 12;
        }
        for (WarLeaderData.Mate mate : data.lobby) {
            gui.fill(x, y, b[0] + b[2] - 10, y + 11, PurpleUi.BOX);
            gui.text(font, mate.name, x + 4, y + 2, LIGHT, false);
            String cls = mate.clazz == null || mate.clazz.isEmpty() ? "" : tr("nightsiege.class." + mate.clazz.toLowerCase(Locale.ROOT));
            small(gui, font, cls, x + 90, y + 3, GREY, 0.75f);
            String st = tr(mate.ready ? "nightsiege.war.ready" : "nightsiege.war.choosing");
            gui.text(font, st, b[0] + b[2] - 14 - font.width(st), y + 2, mate.ready ? GREEN : ORANGE, false);
            y += 12;
        }
        y += 2;
        if (!data.canGo && data.forceIn > 0 && !data.lobby.isEmpty()) {
            small(gui, font, tr("nightsiege.war.lobby_wait", data.forceIn), x, y, GREY, 0.75f);
        }
        int bw = (b[2] - 30) / 2;
        int by = b[1] + b[3] - 22;
        int goX = b[0] + 10;
        int cancelX = goX + bw + 10;
        boolean overGo = mouseX >= goX && mouseX < goX + bw && mouseY >= by && mouseY < by + 16;
        boolean overCancel = mouseX >= cancelX && mouseX < cancelX + bw && mouseY >= by && mouseY < by + 16;
        PurpleUi.button(gui, font, goX, by, bw, 16, tr("nightsiege.war.go"), data.canGo, overGo, true);
        PurpleUi.button(gui, font, cancelX, by, bw, 16, tr("nightsiege.war.cancel"), true, overCancel, false);
    }

    /** "key\u0001arg…" from the server, translated here. */
    static String lineText(String line) {
        if (line == null) {
            return "";
        }
        String[] parts = line.split("\u0001");
        Object[] args = new Object[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return Component.translatable(parts[0], args).getString();
    }

    private void drawWorld(GuiGraphicsExtractor gui, Font font) {
        // The gates of the base.
        for (WarLeaderData.Target t : data.targets) {
            int[] s = project(t.x + 0.5, t.y + 1, t.z + 0.5);
            if (s == null) {
                continue;
            }
            frame(gui, s[0] - 4, s[1] - 4, s[0] + 4, s[1] + 4, 0xC0FFD86B);
            small(gui, font, tr("arch".equals(t.kind) ? "nightsiege.war.arch" : "nightsiege.war.gate"), s[0] + 6, s[1] - 3, 0xE0FFD86B, 0.6f);
        }
        // The gathering points: red flags with their number.
        for (WarLeaderData.Point p : data.points) {
            int[] s = project(p.x + 0.5, p.y, p.z + 0.5);
            if (s == null) {
                continue;
            }
            boolean sel = p.index == point;
            gui.fill(s[0], s[1] - 14, s[0] + 1, s[1], 0xFF2A2020);
            gui.fill(s[0] + 1, s[1] - 14, s[0] + 9, s[1] - 9, sel ? GOLD : 0xFFE03030);
            gui.fill(s[0] + 1, s[1] - 9, s[0] + 6, s[1] - 7, sel ? GOLD : 0xFFE03030);
            if (sel) {
                frame(gui, s[0] - 4, s[1] - 17, s[0] + 12, s[1] + 2, GOLD);
            }
            String n = String.valueOf(p.index + 1);
            gui.text(font, n, s[0] + 3, s[1] - 22, sel ? GOLD : 0xFFFFFFFF, true);
        }
        // The squads.
        for (WarLeaderData.Squad q : data.squads) {
            if ("coming".equals(q.order)) {
                continue;
            }
            double gy = groundY(q.x, q.z);
            int[] s = project(q.x, gy, q.z);
            if (s == null) {
                continue;
            }
            int color = orderColor(q.order);
            boolean sel = q.id == squad;
            gui.fill(s[0] - 3, s[1] - 3, s[0] + 3, s[1] + 3, 0xFF000000);
            gui.fill(s[0] - 2, s[1] - 2, s[0] + 2, s[1] + 2, color);
            if (sel) {
                frame(gui, s[0] - 6, s[1] - 6, s[0] + 6, s[1] + 6, GOLD);
            }
            String label = shortName(q.item) + " " + q.alive;
            small(gui, font, label, s[0] + 5, s[1] - 3, sel ? GOLD : color, 0.6f);
        }
        // The defenders: a dot, their name and life.
        for (WarLeaderData.Mate m : data.defenders) {
            if (m.fallen || (m.x == 0 && m.z == 0)) {
                continue;
            }
            int[] s = project(m.x, m.y, m.z);
            if (s == null) {
                continue;
            }
            float share = m.max <= 0 ? 0 : m.health / m.max;
            int c = share > 0.6f ? GREEN : share > 0.3f ? GOLD : RED;
            gui.fill(s[0] - 3, s[1] - 3, s[0] + 3, s[1] + 3, 0xFF0A1A30);
            gui.fill(s[0] - 2, s[1] - 2, s[0] + 2, s[1] + 2, 0xFF5AB4FF);
            gui.fill(s[0] - 8, s[1] + 5, s[0] + 8, s[1] + 7, 0xC0000000);
            gui.fill(s[0] - 8, s[1] + 5, s[0] - 8 + Math.round(16 * Math.max(0f, Math.min(1f, share))), s[1] + 7, c);
            small(gui, font, m.name, s[0] - (int) (font.width(m.name) * 0.3f), s[1] - 10, 0xFFBFE0FF, 0.6f);
        }
        // Under the cursor: the target spot of the picked squad.
        if (hover != null && squad >= 0 && !menu) {
            int[] s = project(hover.getX() + 0.5, hover.getY() + 1, hover.getZ() + 0.5);
            if (s != null) {
                small(gui, font, tr("nightsiege.war.target_here"), s[0] + 9, s[1] + 6, RED, 0.6f);
            }
        }
    }

    private static int orderColor(String order) {
        return switch (order == null ? "" : order) {
            case "gather" -> ORANGE;
            case "target" -> RED;
            case "storm" -> 0xFFFF3030;
            case "free" -> VIOLET;
            default -> GREY;
        };
    }

    private void drawTop(GuiGraphicsExtractor gui, Font font) {
        int w = topW();
        int x0 = (this.width - w) / 2;
        panel(gui, x0, 4, x0 + w, 28);
        String phase = "prep".equals(data.phase) ? tr("nightsiege.war.top_prep", clock(data.seconds))
                : tr("nightsiege.war.top_wave", data.wave, data.waves, clock(data.seconds));
        gui.text(font, phase, x0 + 6, 8, "prep".equals(data.phase) ? LIGHT : 0xFFFF8A6A, true);
        String gold = "◆ " + data.gold;
        gui.text(font, gold, x0 + w - 6 - font.width(gold), 8, GOLD, true);
        String sub = data.nextGold > 0 ? tr("nightsiege.war.top_next", data.nextGold) : tr("nightsiege.war.top_last");
        if (!data.card.isEmpty()) {
            sub = tr("nightsiege.war.top_card", tr("nightsiege.war.card." + data.card)) + "  ·  " + sub;
        }
        small(gui, font, sub, x0 + 6, 19, data.card.isEmpty() ? GREY : RED, 0.7f);
        if (ticks < noticeUntil && !notice.isEmpty()) {
            int nw = (int) Math.ceil(font.width(notice) * 0.8f);
            panel(gui, (this.width - nw) / 2 - 5, 31, (this.width + nw) / 2 + 5, 42);
            small(gui, font, notice, (this.width - nw) / 2, 33, ORANGE, 0.8f);
        }
    }

    private void drawShop(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        int x0 = shopX();
        int top = shopTop();
        int bottom = shopBottom();
        panel(gui, x0, top, x0 + SHOP_W, bottom);
        int tabW = SHOP_W / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            boolean sel = TABS[i].equals(tab);
            int tx = x0 + i * tabW;
            gui.fill(tx + 1, top + 1, tx + tabW - 1, top + 12, sel ? 0xFF3A2F12 : 0xFF1A1622);
            String name = tr("nightsiege.war.tab." + TABS[i]);
            small(gui, font, font.plainSubstrByWidth(name, (int) ((tabW - 4) / 0.65f)), tx + 3, top + 4, sel ? GOLD : GREY, 0.65f);
        }
        String at = tr("nightsiege.war.shop_at", point + 1);
        small(gui, font, at, x0 + 4, top + 17, LIGHT, 0.7f);
        int y = top + 30;
        WarLeaderData.Item over = null;
        List<WarLeaderData.Item> list = shown();
        shopFirst = Math.max(0, Math.min(shopFirst, Math.max(0, list.size() - shopRows())));
        if (shopFirst > 0) {
            small(gui, font, "▲", x0 + SHOP_W - 10, top + 17, GREY, 0.7f);
        }
        if (shopFirst + shopRows() < list.size()) {
            small(gui, font, "▼ " + (list.size() - shopFirst - shopRows()), x0 + SHOP_W - 22, bottom - 30, GREY, 0.7f);
        }
        for (int n = shopFirst; n < list.size() && n < shopFirst + shopRows(); n++) {
            WarLeaderData.Item it = list.get(n);
            boolean ok = "ok".equals(it.state);
            boolean hot = mouseX >= x0 && mouseX < x0 + SHOP_W && mouseY >= y && mouseY < y + ROW_H;
            if (hot) {
                over = it;
                gui.fill(x0 + 1, y, x0 + SHOP_W - 1, y + ROW_H - 1, ok ? 0xC03A2F12 : 0xC0241E34);
            }
            gui.pose().pushMatrix();
            gui.pose().translate(x0 + 3, y + 1);
            gui.pose().scale(0.7f, 0.7f);
            gui.item(icon(it.id), 0, 0);
            gui.pose().popMatrix();
            String name = itemName(it.id) + (it.count > 1 ? " ×" + it.count : "");
            small(gui, font, font.plainSubstrByWidth(name, (int) ((SHOP_W - 44) / 0.7f)), x0 + 17, y + 3, ok ? 0xFFFFFFFF : DIM, 0.7f);
            String price = String.valueOf(it.price);
            int pc = ok ? GOLD : "poor".equals(it.state) ? RED : DIM;
            small(gui, font, "◆" + price, x0 + SHOP_W - 5 - (int) Math.ceil(font.width("◆" + price) * 0.7f), y + 3, pc, 0.7f);
            y += ROW_H;
        }
        // What the hovered thing is, or the hint.
        String about = over != null ? describe(over) : tr("nightsiege.war.shop_hint");
        int ly = bottom - 22;
        for (net.minecraft.util.FormattedCharSequence part : font.split(net.minecraft.network.chat.FormattedText.of(about), (int) ((SHOP_W - 8) / 0.6f))) {
            if (ly > bottom - 6) {
                break;
            }
            gui.pose().pushMatrix();
            gui.pose().translate(x0 + 4, ly);
            gui.pose().scale(0.6f, 0.6f);
            gui.text(font, part, 0, 0, over != null && !"ok".equals(over.state) ? ORANGE : GREY, false);
            gui.pose().popMatrix();
            ly += 6;
        }
    }

    private String describe(WarLeaderData.Item it) {
        String state = "ok".equals(it.state) ? "" : " – " + stateText(it);
        return switch (it.group) {
            case "squad" -> tr("leitertraeger".equals(it.id) ? "nightsiege.war.about_ladder"
                    : "sprengmeister".equals(it.id) ? "nightsiege.war.about_sapper"
                    : it.ownWill ? "nightsiege.war.about_own" : "nightsiege.war.about_squad", it.count, it.from) + state;
            case "machine" -> tr("nightsiege.war.about_machine", it.from, data.machinesMax) + state;
            case "boss" -> tr("nightsiege.war.about_boss", it.from) + state;
            default -> tr("nightsiege.war.card_about." + it.id.substring(it.id.indexOf('.') + 1)) + state;
        };
    }

    private void drawSide(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        int x0 = sideX();
        int top = shopTop();
        int bottom = shopBottom();
        panel(gui, x0, top, x0 + SIDE_W, bottom);
        small(gui, font, tr("nightsiege.war.squads", data.squads.size()), x0 + 4, top + 4, GOLD, 0.75f);
        int y = top + 16;
        int rows = squadRows();
        squadFirst = Math.max(0, Math.min(squadFirst, Math.max(0, data.squads.size() - rows)));
        for (int i = squadFirst; i < Math.min(squadFirst + rows, data.squads.size()); i++) {
            WarLeaderData.Squad s = data.squads.get(i);
            boolean sel = s.id == squad;
            boolean hot = mouseX >= x0 && mouseX < x0 + SIDE_W && mouseY >= y && mouseY < y + 12;
            if (sel || hot) {
                gui.fill(x0 + 1, y, x0 + SIDE_W - 1, y + 11, sel ? 0xC03A2F12 : 0xC0241E34);
            }
            int color = orderColor(s.order);
            gui.fill(x0 + 3, y + 3, x0 + 7, y + 7, color);
            String name = shortName(s.item) + " " + s.alive + "/" + s.total;
            small(gui, font, font.plainSubstrByWidth(name, (int) (70 / 0.65f)), x0 + 10, y + 2, sel ? GOLD : LIGHT, 0.65f);
            String state = orderText(s);
            small(gui, font, state, x0 + SIDE_W - 4 - (int) Math.ceil(font.width(state) * 0.6f), y + 3, color, 0.6f);
            y += 12;
        }
        if (data.squads.size() > rows) {
            // More than fit: the wheel scrolls, the header shows where.
            String more = (squadFirst > 0 ? "▲ " : "") + (squadFirst + rows < data.squads.size() ? "▼ " + (data.squads.size() - squadFirst - rows) : "");
            small(gui, font, more, x0 + SIDE_W - 4 - (int) Math.ceil(font.width(more) * 0.65f), top + 5, GREY, 0.65f);
        }
        if (data.squads.isEmpty()) {
            small(gui, font, tr("nightsiege.war.squads_none"), x0 + 4, y, GREY, 0.65f);
        }
        int by = squadsBottom() + 3;
        int half = (SIDE_W - 12) / 2;
        boolean overG = mouseX >= x0 + 4 && mouseX < x0 + 4 + half && mouseY >= by && mouseY < by + 14;
        boolean overS = mouseX >= x0 + 8 + half && mouseX < x0 + 8 + 2 * half && mouseY >= by && mouseY < by + 14;
        PurpleUi.button(gui, font, x0 + 4, by, half, 14, tr("nightsiege.war.btn_gather"), squad >= 0, overG, false);
        PurpleUi.button(gui, font, x0 + 8 + half, by, half, 14, tr("nightsiege.war.btn_storm"), true, overS, true);
        // The defenders.
        int dy = by + 20;
        gui.fill(x0 + 3, dy - 3, x0 + SIDE_W - 3, dy - 2, LINE);
        small(gui, font, tr("nightsiege.war.defenders"), x0 + 4, dy, GOLD, 0.75f);
        dy += 11;
        for (WarLeaderData.Mate m : data.defenders) {
            if (dy + 10 > bottom - 2) {
                break;
            }
            float share = m.max <= 0 ? 0 : m.health / m.max;
            int c = m.fallen ? DIM : share > 0.6f ? GREEN : share > 0.3f ? GOLD : RED;
            String name = (m.fallen ? "✝ " : "") + m.name;
            small(gui, font, font.plainSubstrByWidth(name, (int) (80 / 0.65f)), x0 + 4, dy + 1, m.fallen ? DIM : LIGHT, 0.65f);
            if (!m.fallen) {
                int bx = x0 + SIDE_W - 46;
                gui.fill(bx, dy + 2, bx + 40, dy + 5, 0xC0000000);
                gui.fill(bx, dy + 2, bx + Math.round(40 * Math.max(0f, Math.min(1f, share))), dy + 5, c);
            }
            dy += 10;
        }
    }

    private String orderText(WarLeaderData.Squad s) {
        return switch (s.order == null ? "" : s.order) {
            case "coming" -> tr("nightsiege.war.order.coming", s.seconds);
            case "gather" -> tr("nightsiege.war.order.gather", s.seconds);
            case "target" -> tr("nightsiege.war.order.target");
            case "storm" -> tr("nightsiege.war.order.storm");
            default -> tr("nightsiege.war.order.free");
        };
    }

    private void drawHint(GuiGraphicsExtractor gui, Font font) {
        String text;
        WarLeaderData.Squad s = squad >= 0 ? squadById(squad) : null;
        if (s != null) {
            text = s.ownWill ? tr("nightsiege.war.hint_own", shortName(s.item)) : tr("nightsiege.war.hint_squad", shortName(s.item));
        } else {
            text = tr("nightsiege.war.hint", point + 1);
        }
        int w = (int) Math.ceil(font.width(text) * 0.7f);
        int y = this.height - 18;
        panel(gui, (this.width - w) / 2 - 5, y - 2, (this.width + w) / 2 + 5, y + 9);
        small(gui, font, text, (this.width - w) / 2, y, s != null ? GOLD : LIGHT, 0.7f);
    }

    private void drawMenu(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        gui.fill(0, 0, this.width, this.height, 0x80000000);
        int[] b = menuBox();
        PurpleUi.window(gui, b[0], b[1], b[0] + b[2], b[1] + b[3]);
        String head = tr("nightsiege.war.menu");
        gui.text(font, head, (this.width - font.width(head)) / 2, b[1] + 7, GOLD, true);
        int bx = b[0] + 10;
        int bw = b[2] - 20;
        int y0 = b[1] + 20;
        String[] labels = {tr("nightsiege.war.menu_back"), tr("nightsiege.war.menu_game"),
                tr(confirmGiveUp ? "nightsiege.war.menu_giveup_sure" : "nightsiege.war.menu_giveup")};
        for (int i = 0; i < 3; i++) {
            int y = y0 + i * 20;
            boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + 16;
            PurpleUi.button(gui, font, bx, y, bw, 16, labels[i], true, over, i == 0);
        }
    }

    // ------------------------------------------------------------------ small helpers

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    static String clock(int seconds) {
        seconds = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String itemName(String id) {
        if (id.startsWith("machine.")) {
            return tr("nightsiege.machine." + id.substring(8));
        }
        if (id.startsWith("boss.")) {
            return tr("nightsiege.boss." + id.substring(5));
        }
        if (id.startsWith("card.")) {
            return tr("nightsiege.war.card." + id.substring(5));
        }
        return tr("nightsiege.war.squad." + id);
    }

    private static String shortName(String id) {
        return itemName(id);
    }

    private static ItemStack icon(String id) {
        return switch (id) {
            case "zombies" -> new ItemStack(Items.ZOMBIE_HEAD);
            case "skelette" -> new ItemStack(Items.SKELETON_SKULL);
            case "spinnen" -> new ItemStack(Items.SPIDER_EYE);
            case "creeper" -> new ItemStack(Items.CREEPER_HEAD);
            case "wiedergaenger" -> new ItemStack(Items.ROTTEN_FLESH);
            case "axtillager" -> new ItemStack(Items.IRON_AXE);
            case "enterhaken" -> new ItemStack(Items.TRIPWIRE_HOOK);
            case "tunnelgraeber" -> new ItemStack(Items.IRON_SHOVEL);
            case "standarte" -> new ItemStack(Items.BANNER.red());
            case "eisenritter" -> new ItemStack(Items.IRON_CHESTPLATE);
            case "kameltrupp" -> new ItemStack(Items.SAND);
            case "leitertraeger" -> new ItemStack(Items.LADDER);
            case "sprengmeister" -> new ItemStack(Items.GUNPOWDER);
            case "machine.trebuchet" -> new ItemStack(Items.OAK_LOG);
            case "machine.mortar" -> new ItemStack(Items.TNT);
            case "machine.ballista" -> new ItemStack(Items.CROSSBOW);
            case "machine.fire" -> new ItemStack(Items.FIRE_CHARGE);
            case "machine.plague" -> new ItemStack(Items.SLIME_BALL);
            case "machine.hook" -> new ItemStack(Items.LEAD);
            case "card.blutmond" -> new ItemStack(Items.REDSTONE);
            case "card.nebel" -> new ItemStack(Items.COBWEB);
            case "card.frost" -> new ItemStack(Items.SNOWBALL);
            case "card.gewitter" -> new ItemStack(Items.TRIDENT);
            default -> new ItemStack(id.startsWith("boss.") ? Items.WITHER_SKELETON_SKULL : Items.PAPER);
        };
    }

    private static void panel(GuiGraphicsExtractor gui, int x0, int y0, int x1, int y1) {
        gui.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, LINE);
        gui.fill(x0, y0, x1, y1, PANEL);
    }

    private static void frame(GuiGraphicsExtractor gui, int x0, int y0, int x1, int y1, int color) {
        gui.fill(x0, y0, x1, y0 + 1, color);
        gui.fill(x0, y1 - 1, x1, y1, color);
        gui.fill(x0, y0, x0 + 1, y1, color);
        gui.fill(x1 - 1, y0, x1, y1, color);
    }

    /** The pointer: a small cross (gold; red while a squad is picked and a click gives its target). */
    private static void cross(GuiGraphicsExtractor gui, int x, int y, int color) {
        gui.fill(x - 6, y, x + 7, y + 1, 0x80000000);
        gui.fill(x, y - 6, x + 1, y + 7, 0x80000000);
        gui.fill(x - 5, y - 1, x + 6, y, color);
        gui.fill(x - 1, y - 5, x, y + 6, color);
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, false);
        gui.pose().popMatrix();
    }

    // ------------------------------------------------------------------ tests

    /** Tests: the camera over a spot at a zoom. */
    public void testMove(double x, double z, double h) {
        camX = x;
        camZ = z;
        zoomH = Math.max(MIN_HEIGHT, Math.min(maxZoom(), h));
        camYv = targetY();
        oldX = camX;
        oldY = camYv;
        oldZ = camZ;
    }

    public void testPick(int pointIndex, int squadId, String shopTab) {
        point = pointIndex;
        squad = squadId;
        if (shopTab != null) {
            tab = shopTab;
        }
    }

    public void testMenu(boolean open) {
        menu = open;
    }

    public String testPhase() {
        return data == null ? "" : data.phase;
    }
}
