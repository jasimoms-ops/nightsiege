package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The second ultimates from the legendary crate gains, fired with their own key
 * (default X): battle trance (warrior), lure arrow (archer), mirror shield
 * (guardian) and magnetic field (engineer). Each one has a 90 second pause.
 */
public final class SecondUlts {

    public static final int COOLDOWN_TICKS = 90 * 20;

    /** When each player may fire again. */
    private static final Map<UUID, Long> READY = new HashMap<>();
    /** Guardians whose mirror shield is up, until when. */
    private static final Map<UUID, Long> MIRROR = new HashMap<>();
    /** Magnetic fields of tonight. */
    private static final List<Magnet> MAGNETS = new ArrayList<>();

    private record Magnet(Vec3 point, long until) {
    }

    private SecondUlts() {
    }

    public static void reset() {
        READY.clear();
        MIRROR.clear();
        MAGNETS.clear();
    }

    /** The legendary gain that gives a class its second ultimate. */
    public static String gainFor(SkillClass clazz) {
        if (clazz == null) {
            return null;
        }
        return switch (clazz) {
            case WARRIOR -> "schlachtentrance";
            case ARCHER -> "lockpfeil";
            case GUARDIAN -> "spiegelschild";
            case ENGINEER -> "magnetfeld";
            case SUMMONER -> "seelentausch";
            default -> null;
        };
    }

    /** The second-ult key was pressed. */
    public static void fire(ServerPlayer player) {
        SiegeManager m = SiegeManager.get();
        if (m == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        String gain = gainFor(own.clazz());
        if (gain == null || own.gainStage(own.clazz(), gain) <= 0) {
            GainGear.bar(player, Component.translatable("nightsiege.ult2.none"));
            return;
        }
        if (!m.isDefending(player)) {
            GainGear.bar(player, Component.translatable("nightsiege.ult2.night_only"));
            return;
        }
        long now = level.getGameTime();
        Long ready = READY.get(player.getUUID());
        if (ready != null && now < ready) {
            GainGear.bar(player, Component.translatable("nightsiege.ult2.wait", (int) Math.ceil((ready - now) / 20.0)));
            return;
        }
        if (gain.equals("seelentausch") && (Companions.get() == null || !Companions.get().soulSwap(level, player))) {
            return;   // nobody to swap with: the pause does not start
        }
        READY.put(player.getUUID(), now + COOLDOWN_TICKS);
        switch (gain) {
            case "schlachtentrance" -> trance(player, level);
            case "lockpfeil" -> lureArrow(player, level);
            case "spiegelschild" -> mirror(player, level, now);
            case "magnetfeld" -> magnet(player, level, now);
            default -> {
            }
        }
        GainGear.bar(player, Component.translatable("nightsiege.ult2.fired",
                Component.translatable("nightsiege.gain." + gain)).withStyle(ChatFormatting.GOLD));
    }

    /** Seconds until the second ult is ready again (0 = ready), for the HUD line. */
    public static int secondsLeft(ServerPlayer player) {
        Long ready = READY.get(player.getUUID());
        if (ready == null) {
            return 0;
        }
        return (int) Math.max(0, Math.ceil((ready - player.level().getGameTime()) / 20.0));
    }

    /** Battle trance: monsters within 10 blocks much slower for 5 s, the warrior faster. */
    private static void trance(ServerPlayer player, ServerLevel level) {
        for (Mob mob : GainGear.enemies(level, player.position(), 10.0)) {
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3, false, true));
        }
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 1, false, false, true));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1, player.getZ(), 16, 3, 0.5, 3, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 0.6f);
    }

    /** Lure arrow: flies straight where the archer looks. */
    private static void lureArrow(ServerPlayer player, ServerLevel level) {
        KitProjectile arrow = new KitProjectile(level, player, new ItemStack(Items.ARROW), KitProjectile.LURE);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 2.6f, 0.2f);
        level.addFreshEntity(arrow);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f, 0.7f);
    }

    /** Mirror shield: for 5 s projectiles turn back towards whoever shot them. */
    private static void mirror(ServerPlayer player, ServerLevel level, long now) {
        MIRROR.put(player.getUUID(), now + 100);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 30, 0.7, 0.9, 0.7, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.6f);
    }

    /** Magnetic field: pulls monsters within 10 blocks of the spot looked at for 3 s. */
    private static void magnet(ServerPlayer player, ServerLevel level, long now) {
        HitResult hit = player.pick(24.0, 0f, false);
        Vec3 point = hit.getType() == HitResult.Type.MISS
                ? player.getEyePosition().add(player.getLookAngle().scale(12)) : hit.getLocation();
        MAGNETS.add(new Magnet(point, now + 60));
        level.playSound(null, BlockPos.containing(point), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6f, 1.8f);
    }

    /** Projectiles never hurt a guardian behind the mirror. */
    public static boolean allowDamage(LivingEntity victim, DamageSource source) {
        if (MIRROR.isEmpty() || !(victim instanceof ServerPlayer player)) {
            return true;
        }
        Long until = MIRROR.get(player.getUUID());
        return until == null || victim.level().getGameTime() >= until || !source.is(DamageTypeTags.IS_PROJECTILE);
    }

    /** Every tick of the night: the mirror turns projectiles, the magnets pull. */
    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (!MIRROR.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = MIRROR.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player == null || now >= entry.getValue()) {
                    it.remove();
                    continue;
                }
                if (player.level() != level) {
                    continue;
                }
                AABB box = player.getBoundingBox().inflate(4.0);
                for (Projectile p : level.getEntitiesOfClass(Projectile.class, box, e -> e.getOwner() != player)) {
                    Entity owner = p.getOwner();
                    if (owner instanceof ServerPlayer) {
                        continue;   // allies' arrows fly on
                    }
                    Vec3 v = p.getDeltaMovement();
                    Vec3 toPlayer = player.position().add(0, 1, 0).subtract(p.position());
                    if (v.lengthSqr() < 1e-3 || v.dot(toPlayer) <= 0) {
                        continue;
                    }
                    Vec3 back = owner != null ? owner.position().add(0, owner.getBbHeight() * 0.6, 0).subtract(p.position())
                            .normalize().scale(Math.max(1.2, v.length())) : v.scale(-1.0);
                    p.setDeltaMovement(back);
                    p.setOwner(player);
                    p.hurtMarked = true;
                    level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
                }
                if (now % 10 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 6, 0.6, 0.8, 0.6, 0.01);
                }
            }
        }
        if (!MAGNETS.isEmpty()) {
            Iterator<Magnet> it = MAGNETS.iterator();
            while (it.hasNext()) {
                Magnet magnet = it.next();
                if (now >= magnet.until()) {
                    it.remove();
                    continue;
                }
                for (Mob mob : GainGear.enemies(level, magnet.point(), 10.0)) {
                    Vec3 to = magnet.point().subtract(mob.position());
                    double d = to.length();
                    if (d < 0.8) {
                        continue;
                    }
                    Vec3 pull = to.scale(1.0 / d).scale(Math.min(0.45, 0.12 + d * 0.04));
                    mob.setDeltaMovement(mob.getDeltaMovement().scale(0.3).add(pull.x, Math.max(0.0, pull.y) + 0.02, pull.z));
                    mob.hurtMarked = true;
                }
                if (now % 4 == 0) {
                    Vec3 p = magnet.point();
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 0.5, p.z, 30, 2.5, 1.0, 2.5, 0.2);
                }
            }
        }
    }

    /** For the gametest. */
    public static boolean mirrorUp(ServerPlayer player) {
        Long until = MIRROR.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }
}
