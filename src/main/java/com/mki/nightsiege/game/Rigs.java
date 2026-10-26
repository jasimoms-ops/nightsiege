package com.mki.nightsiege.game;

import com.mki.nightsiege.mixin.BlockDisplayAccessor;
import com.mki.nightsiege.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Models built from block displays (Simon, 26 October 2026): the rigs on manned towers and
 * the steampunk drone of a fallen defender. Each piece is a block scaled into a beam or a
 * plate; the whole rig turns with the yaw (and the aiming pieces with the pitch) of whoever
 * steers it. Spinning pieces (rotors) turn about their own centre.
 */
public final class Rigs {

    /** Tag of every piece; pieces nobody owns any more (a crash) go when they load. */
    public static final String TAG = "nightsiege_rig";
    private static final Set<UUID> LIVE = new HashSet<>();

    private Rigs() {
    }

    /** How a piece moves: with the yaw only, with yaw and pitch, or spinning about its centre. */
    public enum Mode { YAW, AIM, SPIN }

    /** One block of a rig: its block, lower corner and size in blocks (rig space: +Z is forward). */
    public record Piece(String block, float x, float y, float z, float sx, float sy, float sz, Mode mode) {
        public static Piece yaw(String block, double x, double y, double z, double sx, double sy, double sz) {
            return new Piece(block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, Mode.YAW);
        }

        public static Piece aim(String block, double x, double y, double z, double sx, double sy, double sz) {
            return new Piece(block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, Mode.AIM);
        }

        public static Piece spin(String block, double x, double y, double z, double sx, double sy, double sz) {
            return new Piece(block, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, Mode.SPIN);
        }
    }

    /** A rig in the world: its pieces by how they move. */
    public static final class Rig {
        final List<UUID> yaw = new ArrayList<>();
        final List<UUID> aim = new ArrayList<>();
        final List<UUID> spin = new ArrayList<>();
        final List<Piece> spinPieces = new ArrayList<>();
        float lastYaw = Float.NaN;
        float lastPitch = Float.NaN;

        public List<UUID> all() {
            List<UUID> out = new ArrayList<>(yaw);
            out.addAll(aim);
            out.addAll(spin);
            return out;
        }
    }

    /** True for a piece of a rig that still belongs to something. */
    public static boolean isLive(Entity entity) {
        return LIVE.contains(entity.getUUID());
    }

    /**
     * Builds a rig. The yaw and spin pieces stand at {@code base}, the aiming pieces at
     * {@code aimPivot}; with {@code rideOn} set, every piece rides that entity instead.
     */
    public static Rig spawn(ServerLevel level, Vec3 base, Vec3 aimPivot, List<Piece> pieces, float yaw, Entity rideOn) {
        Rig rig = new Rig();
        for (Piece p : pieces) {
            Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
            ((BlockDisplayAccessor) display).nightsiege$setBlockState(block(p.block()));
            ((DisplayAccessor) display).nightsiege$setTransformation(p.mode() == Mode.SPIN ? spinShape(p, 0f) : shape(p));
            ((DisplayAccessor) display).nightsiege$setPosRotInterpolationDuration(2);
            ((DisplayAccessor) display).nightsiege$setTransformationInterpolationDuration(1);
            Vec3 at = p.mode() == Mode.AIM ? aimPivot : base;
            display.setPos(at.x, at.y, at.z);
            display.setYRot(yaw);
            display.addTag(TAG);
            LIVE.add(display.getUUID());
            level.addFreshEntity(display);
            if (rideOn != null) {
                display.startRiding(rideOn, true, true);
            }
            switch (p.mode()) {
                case YAW -> rig.yaw.add(display.getUUID());
                case AIM -> rig.aim.add(display.getUUID());
                case SPIN -> {
                    rig.spin.add(display.getUUID());
                    rig.spinPieces.add(p);
                }
            }
        }
        rig.lastYaw = yaw;
        return rig;
    }

    /** Turns the rig to a yaw; the aiming pieces also take the pitch. Small changes are skipped. */
    public static void turn(ServerLevel level, Rig rig, float yaw, float pitch) {
        boolean yawMoved = Float.isNaN(rig.lastYaw) || Math.abs(wrap(yaw - rig.lastYaw)) > 0.8f;
        boolean pitchMoved = Float.isNaN(rig.lastPitch) || Math.abs(pitch - rig.lastPitch) > 0.8f;
        if (!yawMoved && !pitchMoved) {
            return;
        }
        rig.lastYaw = yaw;
        rig.lastPitch = pitch;
        if (yawMoved) {
            for (UUID id : rig.yaw) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    e.setYRot(yaw);
                }
            }
            for (UUID id : rig.spin) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    e.setYRot(yaw);
                }
            }
        }
        for (UUID id : rig.aim) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.setYRot(yaw);
                e.setXRot(pitch);
            }
        }
    }

    /** Turns the spinning pieces to an angle (degrees) about their own centres. */
    public static void spin(ServerLevel level, Rig rig, float angle) {
        for (int i = 0; i < rig.spin.size(); i++) {
            Entity e = level.getEntity(rig.spin.get(i));
            if (e instanceof Display display) {
                // Alternate rotors turn the other way, like a real quadcopter.
                ((DisplayAccessor) display).nightsiege$setTransformationInterpolationDelay(0);
                ((DisplayAccessor) display).nightsiege$setTransformation(spinShape(rig.spinPieces.get(i), i % 2 == 0 ? angle : -angle));
            }
        }
    }

    /** Puts every piece back on the entity it rides (a teleport can throw them off). */
    public static void remount(ServerLevel level, Rig rig, Entity vehicle) {
        for (UUID id : rig.all()) {
            Entity e = level.getEntity(id);
            if (e != null && e.getVehicle() != vehicle) {
                e.stopRiding();
                e.setPos(vehicle.getX(), vehicle.getY(), vehicle.getZ());
                e.startRiding(vehicle, true, true);
            }
        }
    }

    /** True while every piece of the rig is still in the world. */
    public static boolean complete(ServerLevel level, Rig rig) {
        for (UUID id : rig.all()) {
            if (level.getEntity(id) == null) {
                return false;
            }
        }
        return true;
    }

    public static void remove(ServerLevel level, Rig rig) {
        if (rig == null) {
            return;
        }
        for (UUID id : rig.all()) {
            LIVE.remove(id);
            Entity e = level == null ? null : level.getEntity(id);
            if (e != null) {
                e.stopRiding();
                e.discard();
            }
        }
        rig.yaw.clear();
        rig.aim.clear();
        rig.spin.clear();
        rig.spinPieces.clear();
    }

    private static Transformation shape(Piece p) {
        return new Transformation(new Vector3f(p.x(), p.y(), p.z()), new Quaternionf(),
                new Vector3f(p.sx(), p.sy(), p.sz()), new Quaternionf());
    }

    /** A spinning piece: turned about the vertical axis through its centre. */
    private static Transformation spinShape(Piece p, float degrees) {
        float cx = p.x() + p.sx() / 2f;
        float cz = p.z() + p.sz() / 2f;
        Quaternionf rot = new Quaternionf().rotationY((float) Math.toRadians(degrees));
        Vector3f half = new Vector3f(p.sx() / 2f, 0f, p.sz() / 2f);
        rot.transform(half);
        return new Transformation(new Vector3f(cx - half.x, p.y(), cz - half.z), rot,
                new Vector3f(p.sx(), p.sy(), p.sz()), new Quaternionf());
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /** The block of a piece by its id; lamps and bulbs shine. */
    static BlockState block(String id) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
        BlockState state = (block == null ? Blocks.STONE : block).defaultBlockState();
        if (state.hasProperty(BlockStateProperties.LIT)) {
            state = state.setValue(BlockStateProperties.LIT, true);
        }
        return state;
    }

    /** Where a point of rig space lands in the world for a rig at {@code base} turned to {@code yaw}. */
    public static Vec3 world(Vec3 base, float yaw, double x, double y, double z) {
        double r = Math.toRadians(yaw);
        double sin = Math.sin(r);
        double cos = Math.cos(r);
        // Rig +Z is the facing direction (-sin yaw, 0, cos yaw); rig +X is its left-hand side.
        return base.add(x * cos - z * sin, y, x * sin + z * cos);
    }
}
