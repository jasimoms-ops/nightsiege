package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SkillValues;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A defender who falls in the night flies on as a small steampunk drone (Simon, 26 October
 * 2026) instead of watching as a spectator: slow, never hurt, bumping into walls, within
 * reach of the core. It shoots crossbow bolts at a crossbow's pace with half a crossbow's
 * damage (growing with the night). The player is shrunk, invisible and flying in adventure
 * mode; the drone is a rig of block displays riding him. The client steers (DroneClient):
 * W/S forward and back, A/D turn, the mouse looks, Ctrl down, Shift up.
 */
public final class Drones {

    private static final Identifier SCALE_ID = ModContent.id("drone_scale");

    private static final class Drone {
        final UUID player;
        Rigs.Rig rig;
        boolean holding;
        long readyAt;
        float spin;

        Drone(UUID player) {
            this.player = player;
        }
    }

    private static final Map<UUID, Drone> DRONES = new HashMap<>();
    /** The ids last sent to the clients (who is a drone). */
    private static String sent = "";

    /**
     * The drone: a copper body with a brass band and a glass dome, a lantern at the front, a
     * little chimney, four rotors on dark oak arms and a crossbow underneath. Rig space: the
     * origin is just above the eye, +Z is where the drone looks; the body hangs below the eye.
     */
    private static final List<Rigs.Piece> RIG = List.of(
            Rigs.Piece.yaw("copper_block", -0.17, -0.47, -0.21, 0.34, 0.2, 0.42),
            Rigs.Piece.yaw("gold_block", -0.19, -0.38, -0.23, 0.38, 0.05, 0.46),
            Rigs.Piece.yaw("light_blue_stained_glass", -0.1, -0.27, -0.1, 0.2, 0.1, 0.2),
            Rigs.Piece.yaw("sea_lantern", -0.05, -0.42, 0.2, 0.1, 0.08, 0.06),
            Rigs.Piece.yaw("polished_blackstone", -0.035, -0.3, -0.2, 0.07, 0.14, 0.07),
            Rigs.Piece.yaw("dark_oak_planks", -0.45, -0.33, -0.025, 0.9, 0.04, 0.05),
            Rigs.Piece.yaw("dark_oak_planks", -0.025, -0.33, -0.45, 0.05, 0.04, 0.9),
            Rigs.Piece.yaw("iron_block", -0.48, -0.31, -0.03, 0.06, 0.06, 0.06),
            Rigs.Piece.yaw("iron_block", 0.42, -0.31, -0.03, 0.06, 0.06, 0.06),
            Rigs.Piece.yaw("iron_block", -0.03, -0.31, -0.48, 0.06, 0.06, 0.06),
            Rigs.Piece.yaw("iron_block", -0.03, -0.31, 0.42, 0.06, 0.06, 0.06),
            Rigs.Piece.spin("dark_oak_planks", -0.62, -0.255, -0.025, 0.34, 0.015, 0.05),
            Rigs.Piece.spin("dark_oak_planks", 0.28, -0.255, -0.025, 0.34, 0.015, 0.05),
            Rigs.Piece.spin("dark_oak_planks", -0.17, -0.255, -0.475, 0.34, 0.015, 0.05),
            Rigs.Piece.spin("dark_oak_planks", -0.17, -0.255, 0.425, 0.34, 0.015, 0.05),
            Rigs.Piece.yaw("dark_oak_planks", -0.025, -0.55, -0.06, 0.05, 0.05, 0.36),
            Rigs.Piece.yaw("spruce_planks", -0.18, -0.54, 0.24, 0.36, 0.035, 0.035),
            Rigs.Piece.yaw("iron_block", -0.0125, -0.515, 0.27, 0.025, 0.025, 0.18));

    private Drones() {
    }

    public static boolean isDrone(Player player) {
        return player != null && DRONES.containsKey(player.getUUID());
    }

    public static boolean isDrone(UUID id) {
        return id != null && DRONES.containsKey(id);
    }

    /** The defender fell: up as a drone. */
    public static void start(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (DRONES.containsKey(player.getUUID())) {
            return;
        }
        player.setGameMode(GameType.ADVENTURE);
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.invulnerable = true;
        player.onUpdateAbilities();
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.addOrUpdateTransientModifier(new AttributeModifier(SCALE_ID,
                    SkillValues.get("drone", "scale", 0.3) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        Drone drone = new Drone(player.getUUID());
        drone.rig = Rigs.spawn(level, player.position(), player.position(), RIG, player.getYRot(), player);
        drone.readyAt = level.getGameTime() + 20;
        DRONES.put(player.getUUID(), drone);
        player.setDeltaMovement(0, 0.35, 0);
        player.hurtMarked = true;
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(), 20, 0.3, 0.2, 0.3, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8f, 1.6f);
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("nightsiege.msg.drone_controls")));
        sync(level.getServer());
    }

    /** Back on the ground: the drone goes, the player is themselves again (game mode is set by the caller). */
    public static void stop(ServerPlayer player) {
        Drone drone = DRONES.remove(player.getUUID());
        if (drone == null) {
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            Rigs.remove(level, drone.rig);
            sync(level.getServer());
        }
        restoreBody(player);
    }

    /** Takes the drone's traces off a player (also after a crash, when they join). */
    public static void restoreBody(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.removeModifier(SCALE_ID);
        }
        player.removeEffect(MobEffects.INVISIBILITY);
        Abilities abilities = player.getAbilities();
        abilities.flying = false;
        abilities.mayfly = player.isCreative() || player.isSpectator();
        abilities.invulnerable = player.isCreative() || player.isSpectator();
        player.onUpdateAbilities();
    }

    /** A player joined who is no drone: drone traces left from before a restart go. */
    public static void cleanup(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (!isDrone(player) && scale != null && scale.hasModifier(SCALE_ID)) {
            restoreBody(player);
            if (player.gameMode() == GameType.ADVENTURE) {
                player.setGameMode(GameType.SURVIVAL);
            }
        }
    }

    /** The attack button went down (true) or up (false) on a drone's client. */
    public static void onFireButton(ServerPlayer player, boolean down) {
        Drone drone = DRONES.get(player.getUUID());
        if (drone != null) {
            drone.holding = down;
        }
    }

    /** Everybody down again (end of the night, server stop): the caller resets game modes. */
    public static void stopAll(MinecraftServer server) {
        for (UUID id : new ArrayList<>(DRONES.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                stop(p);
            } else {
                Drone d = DRONES.remove(id);
                for (ServerLevel level : server.getAllLevels()) {
                    Rigs.remove(level, d.rig);
                }
            }
        }
        sync(server);
    }

    /** Every tick: the rig turns with the drone, the rotors spin, the crossbow fires. */
    public static void tick(MinecraftServer server) {
        if (DRONES.isEmpty()) {
            return;
        }
        for (Drone drone : new ArrayList<>(DRONES.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(drone.player);
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                // Gone offline: the rig goes, they come back as a drone when they join in the night.
                DRONES.remove(drone.player);
                for (ServerLevel l : server.getAllLevels()) {
                    Rigs.remove(l, drone.rig);
                }
                sync(server);
                continue;
            }
            long now = level.getGameTime();
            if (now % 20 == 0) {
                if (!Rigs.complete(level, drone.rig)) {
                    Rigs.remove(level, drone.rig);
                    drone.rig = Rigs.spawn(level, player.position(), player.position(), RIG, player.getYRot(), player);
                } else {
                    Rigs.remount(level, drone.rig, player);
                }
                if (!player.getAbilities().flying) {
                    player.getAbilities().flying = true;
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
            }
            Rigs.turn(level, drone.rig, player.getYRot(), 0f);
            drone.spin = (drone.spin + 47f) % 360f;
            Rigs.spin(level, drone.rig, drone.spin);
            if (now % 12 == 0) {
                Vec3 chimney = Rigs.world(player.position().add(0, player.getBbHeight(), 0), player.getYRot(), 0, -0.12, -0.17);
                level.sendParticles(ParticleTypes.SMOKE, chimney.x, chimney.y, chimney.z, 1, 0.01, 0.02, 0.01, 0.005);
            }
            if (drone.holding && now >= drone.readyAt) {
                fire(level, player, drone, now);
            }
        }
    }

    /** One crossbow bolt where the drone looks: half a crossbow's damage, growing with the night. */
    private static void fire(ServerLevel level, ServerPlayer player, Drone drone, long now) {
        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition();
        Vec3 out = eye.add(look.scale(0.55)).subtract(0, 0.12, 0);
        Arrow arrow = new Arrow(level, out.x, out.y, out.z, new ItemStack(Items.ARROW), null);
        arrow.entityTags().add("nightsiege_arrow");   // no archer skills, no crate gains
        arrow.entityTags().add("nightsiege_turret");
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        SiegeManager m = SiegeManager.get();
        double night = m == null ? 1.0 : m.mobHealthFactor();
        arrow.setBaseDamage(SkillValues.get("drone", "dmg", 1.0) * night);
        arrow.setOwner(player);
        arrow.shoot(look.x, look.y, look.z, 3.15f, 1.0f);
        level.addFreshEntity(arrow);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.8f, 1.3f);
        level.sendParticles(ParticleTypes.SMOKE, out.x, out.y, out.z, 3, 0.03, 0.03, 0.03, 0.01);
        drone.readyAt = now + Math.round(SkillValues.get("drone", "pause", 25));
    }

    /** Tells every client who flies as a drone (their bodies are not drawn). */
    public static void sync(MinecraftServer server) {
        if (server == null) {
            return;
        }
        String ids = String.join(",", DRONES.keySet().stream().map(UUID::toString).toList());
        if (ids.equals(sent)) {
            return;
        }
        sent = ids;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new CoreNetworking.ScreenPayload("drones", ids));
        }
    }

    /** A player joined: they learn who is a drone right now. */
    public static void sendTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("drones", sent));
    }
}
