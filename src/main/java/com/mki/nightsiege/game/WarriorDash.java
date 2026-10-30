package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The warriors' dash (Simon 30.10., 03:19: "gib den kriegern einen 3 block dash alle 10s", 03:21:
 * "in die richtung in die geschaut wird"): the first key of the ability bar (R) throws him three
 * blocks the way he looks, level with the ground (looking up or down does not lift him), every
 * ten seconds. Every warrior has it, from the class on.
 */
public final class WarriorDash {

    /** Its id on the ability bar. */
    public static final String ID = "dash";
    /** Ticks between two dashes (Simon: every 10 s). */
    static final int PAUSE = 200;
    /** About three blocks: on the ground the friction takes 45 % a tick, in the air the drag 9 %. */
    static final double GROUND_SPEED = 1.35;
    static final double AIR_SPEED = 0.28;

    private static final Map<UUID, Long> READY = new HashMap<>();
    /** For the tests: how often one dashed. */
    private static int dashes;

    private WarriorDash() {
    }

    /** Its box on the ability bar. */
    public static com.mki.nightsiege.core.UltState.Slot slot(ServerPlayer player, long now) {
        com.mki.nightsiege.core.UltState.Slot dash = new com.mki.nightsiege.core.UltState.Slot();
        dash.id = ID;
        dash.icon = "minecraft:feather";
        dash.name = "nightsiege.ability.dash";
        dash.sub = "nightsiege.ability.dash.sub";
        dash.cooldownTicks = cooldownLeft(player, now);
        dash.totalTicks = PAUSE;
        return dash;
    }

    public static int cooldownLeft(ServerPlayer player, long now) {
        Long ready = player == null ? null : READY.get(player.getUUID());
        return ready == null || ready <= now ? 0 : (int) (ready - now);
    }

    /** A new night, or the end of one: every dash is ready. */
    public static void reset() {
        READY.clear();
    }

    public static int dashes() {
        return dashes;
    }

    /** The key was pressed: off he goes, if he may. */
    static void fire(ServerPlayer player, TrapService traps) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        SiegeManager m = SiegeManager.get();
        if (!creative && (m == null || !m.isDefending(player))) {
            bar(player, Component.translatable("nightsiege.msg.dash_fight_only"));
            return;
        }
        if (player.isPassenger() || Drones.isDrone(player) || traps.isSeated(player)) {
            return;
        }
        long now = level.getGameTime();
        int left = cooldownLeft(player, now);
        if (left > 0 && !creative) {
            bar(player, Component.translatable("nightsiege.msg.dash_wait", (left + 19) / 20));
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        double speed = player.onGround() ? GROUND_SPEED : AIR_SPEED;
        double up = player.onGround() ? 0.0 : Math.min(player.getDeltaMovement().y, 0.0);
        Vec3 at = player.position();
        player.setDeltaMovement(forward.x * speed, up, forward.z * speed);
        player.hurtMarked = true;
        player.resetFallDistance();
        READY.put(player.getUUID(), now + PAUSE);
        dashes++;
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 10, 0.3, 0.05, 0.3, 0.04);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x + forward.x, at.y + 1.0, at.z + forward.z, 1, 0, 0, 0, 0);
        level.playSound(null, BlockPos.containing(at), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7f, 1.5f);
    }

    private static void bar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
}
