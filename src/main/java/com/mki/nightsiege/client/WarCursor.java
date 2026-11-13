package com.mki.nightsiege.client;

import com.mki.nightsiege.core.SiegeMachineKind;
import com.mki.nightsiege.core.WarLeaderData;
import com.mki.nightsiege.game.SiegeMachines;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tor 10 / K2 (plan-kriegsfuehrer-3): the war leader's ground cross in the bird's-eye view. It lies on
 * what the mouse points at (ground, wall, roof), snaps onto a unit within 1.5 blocks and tells by colour
 * and sign what a click does: white nothing picked, green with an arrow move, red with a sword attack,
 * yellow a special order, grey with an X not possible (with the reason). With a machine picked it shows
 * the spread round the cross, the reach round the machine and the distance.
 * <p>
 * The state is worked out every frame by {@link #update}; the order code (K1) may set the intent, the
 * machine and a preview from outside ({@link #setIntent}, {@link #setMachine}, {@link #setPreview}), those
 * hold until cleared. The click sends its order to {@link #target()} so what is shown is what is ordered.
 * Drawn by {@link WarCursorRender}.
 */
public final class WarCursor {

    /** What a click does here. */
    public enum Intent { NONE, MOVE, ATTACK, SPECIAL, INVALID }

    /** What lies under the cross. */
    public enum Kind { GROUND, WALL, FLAG, SQUAD, MACHINE, DEFENDER, GATE }

    /** A special order's preview: where the ladder goes, where the blast hole opens. */
    public enum Preview { NONE, LADDER, BLAST }

    /** The cross snaps onto a unit up to this far (blocks). */
    public static final double SNAP = 1.5;

    /** A unit under the cross: what it is, its name and life (life share -1: none known). */
    public record Hover(Kind kind, String name, String life, float share, double x, double y, double z, int id) {
    }

    // ------------------------------------------------------------------ worked out each frame
    private static boolean shown;
    private static double screenX;
    private static double screenY;
    private static double worldX;
    private static double worldY;
    private static double worldZ;
    private static boolean onGround;
    private static BlockPos target;
    private static Kind under = Kind.GROUND;
    private static Hover hover;
    private static Intent intent = Intent.NONE;
    private static String reason = "";
    private static String action = "";
    private static int width = 1;
    private static int height = 1;
    private static float partial;

    // ------------------------------------------------------------------ the machine picked (auto or from outside)
    private static SiegeMachineKind machine;
    private static double machineX;
    private static double machineY;
    private static double machineZ;
    private static double distance;

    // ------------------------------------------------------------------ set from outside (K1)
    private static Intent setIntent;
    private static String setReason = "";
    private static boolean setMachine;
    private static SiegeMachineKind outMachine;
    private static double outX;
    private static double outY;
    private static double outZ;
    private static Preview preview = Preview.NONE;
    private static double previewX;
    private static double previewY;
    private static double previewZ;
    private static double previewR;

    /** Tests: the cross over this world spot instead of the mouse (null: the mouse). */
    private static double[] testAt;

    private WarCursor() {
    }

    // ------------------------------------------------------------------ the API for the orders (K1)

    /** The order code decides the intent (and a hint why); holds until {@link #clearIntent()}. */
    public static void setIntent(Intent value, String why) {
        setIntent = value;
        setReason = why == null ? "" : why;
    }

    public static void clearIntent() {
        setIntent = null;
        setReason = "";
    }

    /** The machine whose spread, reach and distance are shown (null: none); holds until {@link #clearMachine()}. */
    public static void setMachine(SiegeMachineKind kind, double x, double y, double z) {
        setMachine = true;
        outMachine = kind;
        outX = x;
        outY = y;
        outZ = z;
    }

    /** Back to the machine of the picked squad. */
    public static void clearMachine() {
        setMachine = false;
        outMachine = null;
    }

    /** A special order's preview at a world spot (radius: the blast hole); holds until {@link #clearPreview()}. */
    public static void setPreview(Preview kind, double x, double y, double z, double radius) {
        preview = kind == null ? Preview.NONE : kind;
        previewX = x;
        previewY = y;
        previewZ = z;
        previewR = radius;
    }

    public static void clearPreview() {
        preview = Preview.NONE;
    }

    /** Everything set from outside off (the view closes). */
    public static void reset() {
        clearIntent();
        clearMachine();
        clearPreview();
        testAt = null;
        shown = false;
    }

    // ------------------------------------------------------------------ what is shown

    public static boolean shown() {
        return shown;
    }

    public static Intent intent() {
        return intent;
    }

    /** Why the intent is so (grey: why not), may be empty. */
    public static String reason() {
        return reason;
    }

    /** The word for what a click does ("Bewegen", "Angreifen", ...), may be empty. */
    public static String action() {
        return action;
    }

    /** The block the click orders to (snapped onto the unit), or null. */
    public static BlockPos target() {
        return target;
    }

    /** The unit under the cross, or null. */
    public static Hover hover() {
        return hover;
    }

    public static Kind under() {
        return under;
    }

    public static SiegeMachineKind machine() {
        return machine;
    }

    /** Blocks from the machine to the cross (0 without a machine). */
    public static double distance() {
        return distance;
    }

    public static double spread() {
        return machine == null ? 0 : SiegeMachines.aimSpread(machine);
    }

    public static double reach() {
        return SiegeMachines.AIM_REACH;
    }

    public static Preview preview() {
        return preview;
    }

    static double previewX() {
        return previewX;
    }

    static double previewY() {
        return previewY;
    }

    static double previewZ() {
        return previewZ;
    }

    static double previewR() {
        return previewR;
    }

    static double screenX() {
        return screenX;
    }

    static double screenY() {
        return screenY;
    }

    static double worldX() {
        return worldX;
    }

    static double worldY() {
        return worldY;
    }

    static double worldZ() {
        return worldZ;
    }

    static boolean onGround() {
        return onGround;
    }

    static double machineX() {
        return machineX;
    }

    static double machineY() {
        return machineY;
    }

    static double machineZ() {
        return machineZ;
    }

    /** Tests: put the cross over a world spot (null: back to the mouse). */
    public static void testAimAt(double[] xz) {
        testAt = xz;
    }

    /** Tests: where the cross is on the screen (GUI pixels). */
    public static double[] testScreen() {
        return new double[]{screenX, screenY};
    }

    /** A one-line state for the test log. */
    public static String testState() {
        return String.format(Locale.ROOT, "shown=%s intent=%s under=%s hover=%s target=%s action=%s reason=%s machine=%s dist=%.1f spread=%.1f preview=%s at=%.0f,%.0f",
                shown, intent, under, hover == null ? "-" : hover.kind() + ":" + hover.name(), target == null ? "-" : target.toShortString(),
                action, reason, machine, distance, spread(), preview, screenX, screenY);
    }

    // ------------------------------------------------------------------ the camera's picture

    /** Screen spot of a world spot, also off the screen (null behind or under the camera). */
    static double[] project(double wx, double wy, double wz) {
        double[] cam = WarLeaderScreen.cameraAt(partial);
        if (cam == null) {
            return null;
        }
        double dy = cam[1] - wy;
        if (dy < 0.5) {
            return null;
        }
        double tanY = tanY();
        double tanX = tanY * width / (double) height;
        double nx = (wx - cam[0]) / (tanX * dy);
        double ny = -(wz - cam[2]) / (tanY * dy);
        return new double[]{(nx + 1.0) / 2.0 * width, (1.0 - ny) / 2.0 * height};
    }

    /** Screen pixels per block at a world height. */
    static double pixelsPerBlock(double wy) {
        double[] cam = WarLeaderScreen.cameraAt(partial);
        if (cam == null) {
            return 8;
        }
        double dy = Math.max(1.0, cam[1] - wy);
        return height / (2.0 * tanY() * dy);
    }

    private static double tanY() {
        return Math.tan(Math.toRadians(Minecraft.getInstance().gameRenderer.mainCamera().getFov()) / 2.0);
    }

    static int width() {
        return width;
    }

    static int height() {
        return height;
    }

    // ------------------------------------------------------------------ each frame

    /**
     * Works out the cross for this frame. {@code hidden}: not shown (a panel or the menu under the mouse).
     * Returns the screen spot the cross is at (the mouse, or the test spot).
     */
    public static double[] update(WarLeaderData data, int pickedSquad, double mouseX, double mouseY, int w, int h, float delta,
                                  boolean hidden) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        partial = delta;
        Minecraft client = Minecraft.getInstance();
        if (testAt != null && client.level != null) {
            double gy = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(testAt[0]), (int) Math.floor(testAt[1]));
            double[] s = project(testAt[0], gy, testAt[1]);
            if (s != null) {
                mouseX = s[0];
                mouseY = s[1];
            }
        }
        screenX = mouseX;
        screenY = mouseY;
        hover = null;
        target = null;
        under = Kind.GROUND;
        onGround = false;
        distance = 0;
        shown = (!hidden || testAt != null) && data != null && WarLeaderScreen.cameraAt(delta) != null && client.level != null && client.player != null;
        if (!shown) {
            return new double[]{mouseX, mouseY};
        }
        // The ray from the camera through the mouse, as the click picks.
        Vec3 from = client.gameRenderer.mainCamera().position();
        double tanY = tanY();
        double tanX = tanY * width / (double) height;
        double nx = mouseX / width * 2.0 - 1.0;
        double ny = 1.0 - mouseY / height * 2.0;
        Vec3 dir = new Vec3(nx * tanX, -1.0, -ny * tanY).normalize();
        BlockHitResult hit = client.level.clip(new ClipContext(from, from.add(dir.scale(400.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, client.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            onGround = true;
            Vec3 at = hit.getLocation();
            worldX = at.x;
            worldY = at.y;
            worldZ = at.z;
            target = hit.getBlockPos();
            under = isWall(client, hit) ? Kind.WALL : Kind.GROUND;
            snap(data, mouseX, mouseY);
        }
        WarLeaderData.Squad picked = null;
        for (WarLeaderData.Squad s : data.squads) {
            if (s.id == pickedSquad) {
                picked = s;
            }
        }
        machineFor(picked);
        decide(data, picked);
        if (setIntent != null) {
            intent = setIntent;
            reason = setReason;
            if (setIntent == Intent.SPECIAL && reason.isEmpty()) {
                action = tr("nightsiege.war.cursor.special");
            }
        }
        return new double[]{screenX, screenY};
    }

    /** Raised above the ground round it (a wall piece, a roof), or its side is hit. */
    private static boolean isWall(Minecraft client, BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP) {
            return true;
        }
        BlockPos p = hit.getBlockPos();
        int top = p.getY() + 1;
        int low = top;
        int[][] around = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] o : around) {
            low = Math.min(low, client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX() + o[0], p.getZ() + o[1]));
        }
        return top - low >= 2;
    }

    /** Onto an own squad within {@link #SNAP} blocks, else a flag, else the nearest other unit (as the click picks). */
    private static void snap(WarLeaderData data, double mouseX, double mouseY) {
        Hover flag = null;
        for (WarLeaderData.Point p : data.points) {
            double[] s = project(p.x + 0.5, p.y, p.z + 0.5);
            if (s != null && sq(s[0] - mouseX) + sq(s[1] - 6 - mouseY) < 100) {
                flag = new Hover(Kind.FLAG, tr("nightsiege.war.cursor.flag", p.index + 1), "", -1f, p.x + 0.5, p.y, p.z + 0.5, p.index);
                break;
            }
        }
        double best = SNAP * SNAP;
        Minecraft client = Minecraft.getInstance();
        // The squad whose monster (or middle) is nearest on the screen, within 9 pixels: the one the click picks
        // (WarLeaderScreen.nearestSquad) - two squads on one flag go by their monsters, not by their middles.
        WarLeaderData.Squad mine = null;
        double minePx = 9 * 9;
        for (WarLeaderData.Squad q : data.squads) {
            if ("coming".equals(q.order)) {
                continue;
            }
            List<double[]> spots = new ArrayList<>();
            spots.add(new double[]{q.x, client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(q.x), (int) Math.floor(q.z)), q.z});
            for (WarLeaderData.Unit u : q.units) {
                spots.add(new double[]{u.x, u.y, u.z});
            }
            for (double[] w : spots) {
                double[] s = project(w[0], w[1], w[2]);
                if (s != null && sq(s[0] - mouseX) + sq(s[1] - mouseY) < minePx) {
                    minePx = sq(s[0] - mouseX) + sq(s[1] - mouseY);
                    mine = q;
                }
            }
        }
        Hover squad = null;
        if (mine != null) {
            WarLeaderData.Squad q = mine;
            double gy = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(q.x), (int) Math.floor(q.z));
            String name = itemName(q.item);
            squad = q.machine
                    ? new Hover(Kind.MACHINE, name, q.maxHealth <= 0 ? "" : tr("nightsiege.war.cursor.life", Math.round(q.health),
                    Math.round(q.maxHealth)), q.maxHealth <= 0 ? -1f : Math.max(0f, Math.min(1f, q.health / q.maxHealth)), q.x, gy, q.z, q.id)
                    : new Hover(Kind.SQUAD, name, tr("nightsiege.war.cursor.alive", q.alive, Math.max(q.alive, q.total)),
                    q.total <= 0 ? -1f : q.alive / (float) q.total, q.x, gy, q.z, q.id);
        }
        for (WarLeaderData.Mate m : data.defenders) {
            if (m.fallen || (m.x == 0 && m.z == 0)) {
                continue;
            }
            double d = sq(m.x - worldX) + sq(m.z - worldZ);
            if (d <= best) {
                best = d;
                hover = new Hover(Kind.DEFENDER, m.name, tr("nightsiege.war.cursor.life", Math.round(m.health), Math.round(m.max)),
                        m.max <= 0 ? -1f : Math.max(0f, Math.min(1f, m.health / m.max)), m.x, m.y, m.z, -1);
            }
        }
        for (WarLeaderData.Target t : data.targets) {
            double d = sq(t.x + 0.5 - worldX) + sq(t.z + 0.5 - worldZ);
            if (d <= best) {
                best = d;
                hover = new Hover(Kind.GATE, tr("arch".equals(t.kind) ? "nightsiege.war.arch" : "nightsiege.war.gate"),
                        t.state == null || t.state.isEmpty() ? "" : tr("nightsiege.war.cursor.gate_" + t.state),
                        "closed".equals(t.state) ? 1f : "broken".equals(t.state) ? 0f : -1f,
                        t.x + 0.5, t.y + 1, t.z + 0.5, -1);
            }
        }
        if (squad != null) {
            // The squad goes before a defender or gate unless that one is nearer on the screen.
            double[] hs = hover == null ? null : project(hover.x(), hover.y(), hover.z());
            if (hs == null || sq(hs[0] - mouseX) + sq(hs[1] - mouseY) >= minePx) {
                hover = squad;
            }
        }
        if (flag != null && (hover == null || (hover.kind() != Kind.SQUAD && hover.kind() != Kind.MACHINE))) {
            hover = flag;   // a squad on its flag goes first (the flag also by F1-F8)
            under = Kind.FLAG;
            return;
        }
        if (hover != null) {
            under = hover.kind();
            worldX = hover.x();
            worldY = hover.y();
            worldZ = hover.z();
            target = BlockPos.containing(worldX, worldY - 0.5, worldZ);
            double[] s = project(worldX, worldY, worldZ);
            if (s != null) {
                screenX = s[0];
                screenY = s[1];
            }
        }
    }

    private static void machineFor(WarLeaderData.Squad picked) {
        machine = null;
        if (setMachine) {
            machine = outMachine;
            machineX = outX;
            machineY = outY;
            machineZ = outZ;
        } else if (picked != null && picked.machine && picked.item.startsWith("machine.")) {
            machine = SiegeMachineKind.byId(picked.item.substring("machine.".length()));
            Minecraft client = Minecraft.getInstance();
            machineX = picked.x;
            machineZ = picked.z;
            machineY = client.level == null ? 0 : client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) Math.floor(picked.x), (int) Math.floor(picked.z));
        }
        if (machine != null && target != null) {
            // As the server measures: from the machine to the block the click sends.
            distance = Math.sqrt(sq(target.getX() - machineX) + sq(target.getZ() - machineZ));
        }
    }

    /** The intent from today's orders (without K1): the picked squad and what lies under the cross. */
    private static void decide(WarLeaderData data, WarLeaderData.Squad picked) {
        reason = "";
        action = "";
        boolean selects = hover != null && (hover.kind() == Kind.FLAG || hover.kind() == Kind.SQUAD || hover.kind() == Kind.MACHINE);
        if (picked == null || selects) {
            intent = Intent.NONE;
            return;
        }
        if (picked.ownWill) {
            intent = Intent.INVALID;
            reason = tr("nightsiege.war.cursor.own");
            return;
        }
        if (target == null) {
            intent = Intent.INVALID;
            reason = tr("nightsiege.war.cursor.nothing");
            return;
        }
        double dx = target.getX() - (data.centerX + 0.5);
        double dz = target.getZ() - (data.centerZ + 0.5);
        if (dx * dx + dz * dz > sq(data.radius + 8.0)) {   // as WarLeader.inView on the server
            intent = Intent.INVALID;
            reason = tr("nightsiege.war.cursor.field");
            return;
        }
        if (picked.machine) {
            if ("coming".equals(picked.order)) {
                intent = Intent.INVALID;
                reason = tr("nightsiege.war.machine_coming");
            } else if (machine != null && distance > SiegeMachines.AIM_REACH) {
                intent = Intent.INVALID;
                reason = tr("nightsiege.war.cursor.far", Math.round(distance), Math.round(SiegeMachines.AIM_REACH));
            } else {
                intent = Intent.ATTACK;
                action = tr("nightsiege.war.fire_here");
            }
            return;
        }
        if (under == Kind.DEFENDER || under == Kind.GATE || under == Kind.WALL) {
            intent = Intent.ATTACK;
            action = tr("nightsiege.war.cursor.attack");
        } else {
            intent = Intent.MOVE;
            action = tr("nightsiege.war.cursor.move");
        }
    }

    // ------------------------------------------------------------------ small helpers

    private static double sq(double v) {
        return v * v;
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String itemName(String id) {
        if (id == null) {
            return "";
        }
        if (id.startsWith("machine.")) {
            return tr("nightsiege.machine." + id.substring("machine.".length()));
        }
        if (id.startsWith("boss.")) {
            return tr("nightsiege.boss." + id.substring("boss.".length()));
        }
        return tr("nightsiege.war.squad." + id);   // as WarLeaderScreen.itemName
    }

    /** A number for the player: "1,5" in German, "1.5" in English, whole numbers without a point. */
    static String number(double v) {
        boolean german = false;
        try {
            german = Minecraft.getInstance().options.languageCode.startsWith("de");
        } catch (RuntimeException ignored) {
            // no options (tests)
        }
        if (Math.abs(v - Math.rint(v)) < 0.05) {
            return String.valueOf(Math.round(v));
        }
        return String.format(german ? Locale.GERMANY : Locale.ROOT, "%.1f", v);
    }
}
