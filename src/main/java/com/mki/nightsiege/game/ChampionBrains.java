package com.mki.nightsiege.game;

import com.mki.nightsiege.core.ExpeditionKind;
import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The two skills of every dungeon champion (content update, Simon 28/29 October 2026), in the
 * style of his fragment. One of them aims at the galleries or the niches. Every 12–20 s one
 * comes, the other next time; a second before, it shows (particles over him, a sound, a line in
 * the action bar). The share hits go through {@link PercentDamage}.
 */
final class ChampionBrains {

    /** What the dungeon gives the brains. */
    interface Host {
        List<ServerPlayer> fighters();

        /** Brings a monster of the dungeon into the arena fight (it counts as a wave monster). */
        Mob spawnAdd(String entityId, BlockPos at);

        List<Mob> arenaMobs();

        /** The arena floor's height (y one walks on). */
        int floorY();
    }

    private final Mob champion;
    private final ExpeditionKind kind;
    private final Random random = new Random();
    private long nextAt;
    private int skill;
    /** A skill announced and when it comes (game time), -1 for none. */
    private long castAt = -1L;
    private int pending;
    /** The swamp mother hides until she is struck. */
    private boolean hidden;
    private float lastHealth;
    /** The ember lord's lava trail: points and until when they burn. */
    private final List<Vec3> trail = new ArrayList<>();
    private long trailUntil;

    ChampionBrains(Mob champion, ExpeditionKind kind, long now) {
        this.champion = champion;
        this.kind = kind;
        this.nextAt = now + 160;   // the first after 8 s
        this.lastHealth = champion.getHealth();
    }

    static String skillKey(ExpeditionKind kind, int skill) {
        return "nightsiege.champion." + kind.id() + "." + (skill == 0 ? "a" : "b");
    }

    void tick(ServerLevel level, Host host, int siegeLevel, SiegeConfig cfg, long now) {
        if (!champion.isAlive()) {
            return;
        }
        if (hidden && champion.getHealth() < lastHealth) {
            hidden = false;
            champion.removeEffect(MobEffects.INVISIBILITY);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, champion.getX(), champion.getY() + 1, champion.getZ(), 40, 0.6, 0.8, 0.6, 0.02);
        }
        lastHealth = champion.getHealth();
        if (now < trailUntil) {
            if (now % 4 == 0) {
                trail.add(champion.position());
            }
            if (now % 10 == 0) {
                for (Vec3 p : trail) {
                    level.sendParticles(ParticleTypes.LAVA, p.x, p.y + 0.1, p.z, 1, 0.3, 0.0, 0.3, 0.0);
                    level.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.1, p.z, 2, 0.3, 0.05, 0.3, 0.01);
                }
                for (ServerPlayer player : host.fighters()) {
                    for (Vec3 p : trail) {
                        if (player.position().distanceToSqr(p) < 1.4) {
                            PercentDamage.hit(level, player, champion, champion, 3.0, siegeLevel, cfg, 1.0);
                            player.igniteForSeconds(2f);
                            break;
                        }
                    }
                }
            }
        } else if (!trail.isEmpty()) {
            trail.clear();
        }
        if (castAt >= 0) {
            if (now >= castAt) {
                castAt = -1L;
                cast(level, host, pending, siegeLevel, cfg, now);
            }
            return;
        }
        if (now < nextAt || host.fighters().isEmpty()) {
            return;
        }
        // Announce: a second of warning.
        pending = skill;
        skill = 1 - skill;
        castAt = now + 20;
        nextAt = now + 240 + random.nextInt(161);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, champion.getX(), champion.getY() + champion.getBbHeight() + 0.5, champion.getZ(),
                30, 0.5, 0.3, 0.5, 0.2);
        level.playSound(null, champion.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2f, 0.7f);
        Component text = Component.translatable("nightsiege.champion.warn", Component.translatable(skillKey(kind, pending)))
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : host.fighters()) {
            player.connection.send(new ClientboundSetActionBarTextPacket(text));
        }
    }

    private void cast(ServerLevel level, Host host, int which, int siegeLevel, SiegeConfig cfg, long now) {
        List<ServerPlayer> fighters = host.fighters();
        if (fighters.isEmpty()) {
            return;
        }
        ServerPlayer target = champion.getTarget() instanceof ServerPlayer p && fighters.contains(p) ? p
                : fighters.stream().min(Comparator.comparingDouble(champion::distanceToSqr)).orElse(fighters.get(0));
        Vec3 at = champion.position();
        switch (kind) {
            case STONE_CRYPT -> {
                if (which == 0) {
                    // Grave breath: 6 % to everyone within 8, he heals by what it took.
                    float healed = 0;
                    for (ServerPlayer p : fighters) {
                        if (p.distanceToSqr(champion) <= 64) {
                            healed += PercentDamage.hit(level, p, champion, champion, 6.0, siegeLevel, cfg, 1.0);
                        }
                    }
                    champion.heal(Math.max(champion.getMaxHealth() * 0.02f, healed));
                    ring(level, at, 8, ParticleTypes.SOUL);
                    level.playSound(null, champion.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 2f, 0.6f);
                } else {
                    for (int i = 0; i < 3; i++) {
                        double a = Math.PI * 2 * i / 3;
                        host.spawnAdd("nightsiege:revenant", BlockPos.containing(at.x + Math.cos(a) * 3, host.floorY(), at.z + Math.sin(a) * 3));
                    }
                    level.playSound(null, champion.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2f, 0.8f);
                }
            }
            case DESERT_CRYPT -> {
                if (which == 0) {
                    for (ServerPlayer p : fighters) {
                        if (p.distanceToSqr(champion) <= 144) {
                            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                        }
                    }
                    ring(level, at, 12, ParticleTypes.WHITE_ASH);
                    level.playSound(null, champion.blockPosition(), SoundEvents.HUSK_AMBIENT, SoundSource.HOSTILE, 2f, 0.5f);
                } else {
                    // Bound: the target cannot move for 2 s.
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 9));
                    target.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 40, 200));
                    target.setDeltaMovement(Vec3.ZERO);
                    target.hurtMarked = true;
                    level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1, target.getZ(), 60, 0.4, 0.8, 0.4, 0.5);
                    level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2f, 0.6f);
                }
            }
            case ILLAGER_KEEP -> {
                if (which == 0) {
                    // Lightning through the shield: 8 % that no shield stops.
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                    if (bolt != null) {
                        bolt.snapTo(target.getX(), target.getY(), target.getZ());
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    float amount = PercentDamage.amount(target, 8.0, siegeLevel, cfg, 1.0);
                    target.invulnerableTime = 0;
                    target.hurtServer(level, level.damageSources().magic(), amount);
                } else {
                    for (Mob mob : host.arenaMobs()) {
                        if (mob != champion && mob.isAlive()) {
                            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
                            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
                            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + 2, mob.getZ(), 3, 0.3, 0.2, 0.3, 0);
                        }
                    }
                    level.playSound(null, champion.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3f, 1.4f);
                }
            }
            case FROST_KEEP -> {
                if (which == 0) {
                    int frozen = SiegeManager.get().trapService().freezeNear(level, at, 32, 60);
                    ring(level, at, 10, ParticleTypes.SNOWFLAKE);
                    level.playSound(null, champion.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
                    if (frozen == 0) {
                        for (ServerPlayer p : fighters) {
                            if (p.distanceToSqr(champion) <= 100) {
                                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                            }
                        }
                    }
                } else {
                    // Splinters on the galleries: whoever stands high is struck.
                    boolean any = false;
                    for (ServerPlayer p : fighters) {
                        if (p.getY() >= host.floorY() + 2.5) {
                            splinter(level, p, siegeLevel, cfg);
                            any = true;
                        }
                    }
                    if (!any) {
                        splinter(level, target, siegeLevel, cfg);
                    }
                }
            }
            case POISON_SWAMP -> {
                if (which == 0) {
                    hidden = true;
                    champion.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 400, 0, false, false));
                    level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 1, at.z, 50, 0.6, 0.9, 0.6, 0.05);
                    level.playSound(null, champion.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 2f, 0.8f);
                } else {
                    AreaEffectCloud cloud = new AreaEffectCloud(level, target.getX(), target.getY(), target.getZ());
                    cloud.setRadius(3.0f);
                    cloud.setDuration(120);
                    cloud.setRadiusPerTick(-0.01f);
                    cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                    level.addFreshEntity(cloud);
                    for (int i = 0; i < 2; i++) {
                        host.spawnAdd("minecraft:cave_spider", BlockPos.containing(target.getX() + (i == 0 ? 2 : -2), host.floorY(), target.getZ()));
                    }
                    level.playSound(null, target.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 2f, 0.9f);
                }
            }
            case DEEP_CELLAR -> {
                if (which == 0) {
                    // Behind the weakest.
                    ServerPlayer weak = fighters.stream().min(Comparator.comparingDouble(p -> p.getHealth() / p.getMaxHealth())).orElse(target);
                    Vec3 back = weak.position().subtract(weak.getLookAngle().multiply(2, 0, 2));
                    level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 60, 0.5, 1, 0.5, 0.5);
                    champion.teleportTo(back.x, weak.getY(), back.z);
                    champion.setTarget(weak);
                    weak.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
                    level.playSound(null, weak.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2f, 0.6f);
                } else {
                    // Two players pulled together.
                    ServerPlayer a = null;
                    ServerPlayer b = null;
                    double best = 0;
                    for (ServerPlayer p : fighters) {
                        for (ServerPlayer q : fighters) {
                            double d = p.distanceToSqr(q);
                            if (p != q && d > best && d < 24 * 24) {
                                best = d;
                                a = p;
                                b = q;
                            }
                        }
                    }
                    if (a == null) {
                        a = target;
                        b = null;
                    }
                    Vec3 mid = b == null ? champion.position() : a.position().add(b.position()).scale(0.5);
                    for (ServerPlayer p : b == null ? List.of(a) : List.of(a, b)) {
                        Vec3 pull = mid.subtract(p.position());
                        double len = Math.max(1.0, pull.length());
                        p.setDeltaMovement(pull.scale(1.4 / len).add(0, 0.35, 0));
                        p.hurtMarked = true;
                        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
                        level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
                    }
                    level.playSound(null, champion.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5f, 1.2f);
                }
            }
            case NETHER_FORGE -> {
                if (which == 0) {
                    // The hammer: 10 % round him, up into the air.
                    for (ServerPlayer p : fighters) {
                        if (p.distanceToSqr(champion) <= 16) {
                            PercentDamage.hit(level, p, champion, champion, 10.0, siegeLevel, cfg, 1.0);
                            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.6, 0));
                            p.hurtMarked = true;
                        }
                    }
                    level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 6, 1.5, 0.1, 1.5, 0);
                    level.playSound(null, champion.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2f, 0.5f);
                } else {
                    trailUntil = now + 100;
                    trail.clear();
                    level.playSound(null, champion.blockPosition(), SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 2f, 0.7f);
                }
            }
            case BASALT_FORGE -> {
                if (which == 0) {
                    champion.setAbsorptionAmount(champion.getMaxHealth() * 0.15f);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 1, at.z, 50, 0.5, 1, 0.5, 0.03);
                    level.playSound(null, champion.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.HOSTILE, 2f, 0.6f);
                } else {
                    // Sparks on the high places (niches, platforms, the gallery).
                    boolean any = false;
                    for (ServerPlayer p : fighters) {
                        if (p.getY() >= host.floorY() + 1.5) {
                            spark(level, p, siegeLevel, cfg);
                            any = true;
                        }
                    }
                    if (!any) {
                        spark(level, target, siegeLevel, cfg);
                    }
                }
            }
        }
    }

    private void splinter(ServerLevel level, ServerPlayer p, int siegeLevel, SiegeConfig cfg) {
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, p.getX(), p.getY() + 3, p.getZ(), 40, 0.8, 0.4, 0.8, 0.1);
        level.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 1.4f);
        PercentDamage.hit(level, p, champion, champion, 5.0, siegeLevel, cfg, 1.0);
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
    }

    private void spark(ServerLevel level, ServerPlayer p, int siegeLevel, SiegeConfig cfg) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 2.5, p.getZ(), 30, 0.6, 0.6, 0.6, 0.05);
        level.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.5f, 0.8f);
        PercentDamage.hit(level, p, champion, champion, 6.0, siegeLevel, cfg, 1.0);
        p.igniteForSeconds(3f);
    }

    private static void ring(ServerLevel level, Vec3 at, double r, net.minecraft.core.particles.SimpleParticleType type) {
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32;
            level.sendParticles(type, at.x + Math.cos(a) * r, at.y + 0.3, at.z + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.01);
        }
    }
}
