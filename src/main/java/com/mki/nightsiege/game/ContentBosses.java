package com.mki.nightsiege.game;

import com.mki.nightsiege.core.BlockGuard;
import com.mki.nightsiege.core.BossKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The four bosses of the content update (Simon 28/29 October 2026), each against another way of
 * defending: the frost jarl stomps over shields and walls defenders apart with ice, the ember
 * colossus leaves burning cracks and breaks in two, the storm witch strikes from the air and
 * sweeps the walls, the ram titan runs through gate and tank. Every blow takes a share of the
 * max health (as with the first four); the blocks they change come back after the night.
 */
final class ContentBosses {

    private ContentBosses() {
    }

    /** Defenders within a radius on the same level (±4 blocks up or down). */
    static List<ServerPlayer> around(BossService.Host host, Vec3 c, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : host.defenders()) {
            double dx = p.getX() - c.x;
            double dz = p.getZ() - c.z;
            if (dx * dx + dz * dz <= radius * radius && Math.abs(p.getY() - c.y) < 4.0) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Over a wall when it cannot get through (as the grave king does): stuck for three seconds,
     * or outside while its target stands inside the walls.
     */
    static final class Stuck {
        Vec3 lastPos;
        int ticks;
        long next;

        boolean leap(BossService.Host host, Mob boss, long now, double apex) {
            if (now % 20 != 0) {
                return false;
            }
            Vec3 pos = boss.position();
            LivingEntity target = boss.getTarget();
            boolean far = target != null && target.distanceToSqr(boss) > 16.0;
            ticks = lastPos != null && lastPos.distanceToSqr(pos) < 1.0 && far ? ticks + 20 : 0;
            lastPos = pos;
            if (target instanceof ServerPlayer player && now >= next && boss.onGround()
                    && (ticks >= 60 || (far && host.insideBase(player.getX(), player.getZ())
                    && !host.insideBase(boss.getX(), boss.getZ()) && player.distanceToSqr(boss) < 26.0 * 26.0))) {
                BossBrains.leap(host.world(), boss, player.position(), apex);
                next = now + 240;
                ticks = 0;
                return true;
            }
            return false;
        }
    }

    // ================================================================== Frostjarl

    /**
     * The frost jarl: a giant of pack ice. His stomp goes over shields; he raises walls of real ice
     * for 15 s that cut a defender off from the others, and throws blocks of ice at whoever keeps
     * away from him. Below half an avalanche comes down on every defender, and now and then he
     * wraps himself in an armour of ice that has to be broken (then he stands dazed for 3 s).
     *
     * <p>Bosse 2 (Simon 05.11.: "die vier neuen bosse müssen nochmal verbessert werden"): stomp 8 → 20 %,
     * the ice block (15 %), the avalanche from 50 % (was 30 %), the frost armour.</p>
     */
    static final class FrostJarl extends BossBrains.Brain {
        static final double STOMP_SHARE = 20.0;
        static final double AVALANCHE_SHARE = 12.0;
        static final double BOULDER_SHARE = 15.0;
        static final int BOULDER_EVERY = 8 * 20;
        static final int BOULDER_FLIGHT = 20;
        static final double BOULDER_RADIUS = 2.5;
        /** The armour swallows this share of his max health before it breaks. */
        static final double ARMOR_SHARE = 0.15;
        static final int ARMOR_EVERY = 25 * 20;
        static final int STUN_TICKS = 3 * 20;
        static final int WALL_TICKS = 15 * 20;
        long nextStomp;
        long windupUntil;
        long nextWall;
        long nextAvalanche;
        long avalancheAt;
        boolean avalanche;
        final List<Vec3> marks = new ArrayList<>();
        /** The ice of the walls standing now, and when it melts. */
        final List<BlockPos> ice = new ArrayList<>();
        long melt;
        final Stuck stuck = new Stuck();
        long nextBoulder;
        long boulderLands;
        Vec3 boulderFrom;
        Vec3 boulderAt;
        int boulders;
        /** What is left of the frost armour (0 = none). */
        double armor;
        long nextArmor;
        long stunUntil;
        boolean stunned;
        int armorsBroken;
        BossService.Host lastHost;

        FrostJarl(BossService service, BossKind kind) {
            super(service, kind);
        }

        @Override
        String stats() {
            return String.format(java.util.Locale.ROOT, "boulders=%d armors=%d armorLeft=%.0f avalanche=%s dazed=%s",
                    boulders, armorsBroken, armor, avalanche, stunned);
        }

        @Override
        void spawned(BossService.Host host, Mob boss) {
            super.spawned(host, boss);
            nextStomp = spawnedAt + 100;
            nextWall = spawnedAt + 220;
            nextBoulder = spawnedAt + 120;
            BossService.set(boss, Attributes.MOVEMENT_SPEED, 0.26);
        }

        @Override
        String phase(ServerLevel level) {
            if (armor > 0) {
                return "nightsiege.boss.phase.frost_armor";
            }
            if (stunned) {
                return "nightsiege.boss.phase.dazed";
            }
            return avalanche ? "nightsiege.boss.phase.avalanche" : null;
        }

        @Override
        void tick(BossService.Host host, Mob boss, long now) {
            lastHost = host;
            ServerLevel level = host.world();
            if (now % 5 == 0) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, boss.getX(), boss.getY() + boss.getBbHeight() * 0.7, boss.getZ(),
                        2, boss.getBbWidth() * 0.4, boss.getBbHeight() * 0.3, boss.getBbWidth() * 0.4, 0.01);
            }
            if (melt > 0 && now >= melt) {
                meltWalls(level);
            }
            tickBoulder(host, boss, now);
            if (stunned) {
                // The armour broke: he stands dazed, the fighters' chance.
                boss.getNavigation().stop();
                if (now % 4 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                            boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(), 4, 0.6, 0.2, 0.6, 0.0);
                }
                if (now < stunUntil) {
                    return;
                }
                stunned = false;
                boss.setNoAi(false);
            }
            if (!avalanche && below(boss, 0.5)) {
                avalanche = true;
                nextAvalanche = now + 20;
                nextArmor = now + 100;
                tell(host, "nightsiege.boss.frost_jarl.avalanche", ChatFormatting.AQUA);
                BossService.sound(level, boss.position(), SoundEvents.RAVAGER_ROAR, 2.0f, 0.4f);
            }
            if (avalanche && armor <= 0 && now >= nextArmor) {
                armor = boss.getMaxHealth() * ARMOR_SHARE;
                nextArmor = Long.MAX_VALUE;   // the next comes once this one is broken
                tell(host, "nightsiege.boss.frost_jarl.armor", ChatFormatting.AQUA);
                BossService.sound(level, boss.position(), SoundEvents.GLASS_PLACE, 2.0f, 0.4f);
                BossService.sound(level, boss.position(), SoundEvents.POWDER_SNOW_PLACE, 2.0f, 0.5f);
            }
            if (armor > 0 && now % 4 == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                        boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(), 6,
                        boss.getBbWidth() * 0.5, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.5, 0.0);
            }
            tickAvalanche(host, boss, now);
            if (windupUntil > 0) {
                boss.getNavigation().stop();
                if (now % 2 == 0) {
                    BossService.ring(level, ParticleTypes.SNOWFLAKE, boss.position(), 1.5 + (20 - (windupUntil - now)) * 0.25, 18);
                }
                if (now >= windupUntil) {
                    windupUntil = 0;
                    stomp(host, boss);
                    nextStomp = now + (avalanche ? 100 : 140);
                }
                return;
            }
            if (now >= nextStomp && !around(host, boss.position(), 6.5).isEmpty()) {
                windupUntil = now + 20;
                BossService.sound(level, boss.position(), SoundEvents.IRON_GOLEM_HURT, 1.8f, 0.4f);
                return;
            }
            if (now >= nextWall && ice.isEmpty()) {
                if (raiseWall(host, boss)) {
                    nextWall = now + 300;
                    melt = now + WALL_TICKS;
                } else {
                    nextWall = now + 40;
                }
            }
            if (boulderLands == 0 && now >= nextBoulder) {
                throwBoulder(host, boss, now);
            }
            stuck.leap(host, boss, now, 6.0);
        }

        /** The frost armour swallows the blows until it breaks; then he stands dazed. */
        @Override
        boolean allowDamage(ServerLevel level, LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
            if (armor <= 0 || !(victim instanceof Mob boss)) {
                return true;
            }
            armor -= amount;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 12, 0.6, 0.6, 0.6, 0.1);
            BossService.sound(level, boss.position(), SoundEvents.GLASS_HIT, 1.4f, 0.6f);
            if (armor <= 0) {
                armor = 0;
                long now = level.getGameTime();
                stunned = true;
                stunUntil = now + STUN_TICKS;
                nextArmor = now + ARMOR_EVERY;
                windupUntil = 0;
                armorsBroken++;
                boss.setNoAi(true);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                        boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(), 80, 1.0, 1.2, 1.0, 0.3);
                BossService.sound(level, boss.position(), SoundEvents.GLASS_BREAK, 2.0f, 0.5f);
                if (lastHost != null) {
                    tell(lastHost, "nightsiege.boss.frost_jarl.armor_broken", ChatFormatting.GREEN);
                }
            }
            return false;
        }

        /** The block of ice: at the defender farthest from him (8 to 32 blocks), a shadow first. */
        private void throwBoulder(BossService.Host host, Mob boss, long now) {
            ServerPlayer far = null;
            double best = 8.0 * 8.0;
            for (ServerPlayer p : host.defenders()) {
                double d = p.distanceToSqr(boss);
                if (d > best && d < 32.0 * 32.0) {
                    best = d;
                    far = p;
                }
            }
            if (far == null) {
                nextBoulder = now + 20;
                return;
            }
            boulderFrom = boss.position().add(0, boss.getBbHeight() * 0.9, 0);
            boulderAt = far.position();
            boulderLands = now + BOULDER_FLIGHT;
            nextBoulder = now + BOULDER_EVERY;
            BossService.sound(host.world(), boss.position(), SoundEvents.IRON_GOLEM_ATTACK, 1.8f, 0.5f);
        }

        private void tickBoulder(BossService.Host host, Mob boss, long now) {
            if (boulderLands == 0) {
                return;
            }
            ServerLevel level = host.world();
            double t = 1.0 - (boulderLands - now) / (double) BOULDER_FLIGHT;
            Vec3 at = boulderFrom.lerp(boulderAt, t).add(0, Math.sin(Math.PI * t) * 6.0, 0);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.0);
            level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.0);
            if (now % 3 == 0) {
                BossService.ring(level, new DustParticleOptions(0x9FD8FF, 1.2f), boulderAt, BOULDER_RADIUS, 16);
            }
            if (now < boulderLands) {
                return;
            }
            boulderLands = 0;
            boulders++;
            float damage = BossService.abilityDamage(host, kind, 6.0);
            for (ServerPlayer p : around(host, boulderAt, BOULDER_RADIUS)) {
                BossService.strike(host, level, boss, p, damage, BOULDER_SHARE);
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), boss);
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    boulderAt.x, boulderAt.y + 0.5, boulderAt.z, 60, 1.2, 0.5, 1.2, 0.2);
            BossService.sound(level, boulderAt, SoundEvents.GLASS_BREAK, 2.0f, 0.4f);
            BossService.sound(level, boulderAt, SoundEvents.MACE_SMASH_GROUND, 1.6f, 0.6f);
        }

        /** Stomp: 20 % through the shield and a frost that slows, within 6.5 blocks. */
        private void stomp(BossService.Host host, Mob boss) {
            ServerLevel level = host.world();
            Vec3 c = boss.position();
            float damage = BossService.abilityDamage(host, kind, 6.0);
            for (ServerPlayer p : around(host, c, 6.5)) {
                BossService.strikeThrough(host, level, boss, p, damage, STOMP_SHARE);
                BossService.knock(p, c, 0.8, 0.5);
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), boss);
            }
            for (double r = 1.5; r <= 6.5; r += 1.5) {
                BossService.ring(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), c, r, (int) (r * 6));
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 0.5, c.z, 60, 3.0, 0.5, 3.0, 0.05);
            BossService.sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0f, 0.5f);
            BossService.sound(level, c, SoundEvents.GLASS_BREAK, 1.4f, 0.5f);
        }

        /**
         * A wall of pack ice, 7 wide and 3 high: between his target and the defender nearest to it
         * (the healer is cut off), or behind the target when it stands alone.
         */
        private boolean raiseWall(BossService.Host host, Mob boss) {
            ServerLevel level = host.world();
            if (!(boss.getTarget() instanceof ServerPlayer target) || target.distanceToSqr(boss) > 24.0 * 24.0) {
                return false;
            }
            ServerPlayer mate = null;
            double best = 14.0 * 14.0;
            for (ServerPlayer p : host.defenders()) {
                double d = p.distanceToSqr(target);
                if (p != target && d > 9.0 && d < best) {
                    best = d;
                    mate = p;
                }
            }
            Vec3 from = target.position();
            Vec3 to = mate != null ? mate.position() : from.add(from.subtract(boss.position()).multiply(1, 0, 1));
            Vec3 dir = to.subtract(from).multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0e-4) {
                return false;
            }
            dir = dir.normalize();
            Vec3 middle = mate != null ? from.add(dir.scale(Math.sqrt(best) / 2.0)) : from.add(dir.scale(3.0));
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            BlockPos ground = BossService.groundNear(level, middle.x, from.y, middle.z);
            if (ground == null) {
                return false;
            }
            Set<BlockPos> placed = new HashSet<>();
            for (int i = -3; i <= 3; i++) {
                BlockPos column = BlockPos.containing(middle.x + side.x * i, ground.getY(), middle.z + side.z * i);
                BlockPos foot = BossService.groundNear(level, column.getX() + 0.5, ground.getY(), column.getZ() + 0.5);
                if (foot == null) {
                    continue;
                }
                for (int dy = 0; dy < 3; dy++) {
                    BlockPos at = foot.above(dy);
                    if (!placed.contains(at) && level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()
                            && TempBlocks.place(level, at, Blocks.PACKED_ICE.defaultBlockState())) {
                        placed.add(at);
                        ice.add(at);
                    }
                }
            }
            if (placed.isEmpty()) {
                return false;
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, middle.x, ground.getY() + 1.5, middle.z, 50, 3.0, 1.0, 3.0, 0.05);
            BossService.sound(level, middle, SoundEvents.GLASS_PLACE, 1.6f, 0.5f);
            BossService.sound(level, middle, SoundEvents.POWDER_SNOW_PLACE, 1.6f, 0.6f);
            return true;
        }

        private void meltWalls(ServerLevel level) {
            for (BlockPos at : ice) {
                if (TempBlocks.isTemp(level, at)) {
                    level.sendParticles(ParticleTypes.FALLING_WATER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
                    TempBlocks.remove(level, at);
                }
            }
            ice.clear();
            melt = 0;
        }

        /** Below half: snow gathers over every defender for two seconds, then the avalanche falls. */
        private void tickAvalanche(BossService.Host host, Mob boss, long now) {
            if (!avalanche) {
                return;
            }
            ServerLevel level = host.world();
            if (avalancheAt == 0 && now >= nextAvalanche) {
                marks.clear();
                for (ServerPlayer p : host.defenders()) {
                    if (p.distanceToSqr(boss) < 30.0 * 30.0) {
                        marks.add(p.position());
                    }
                }
                avalancheAt = marks.isEmpty() ? 0 : now + 40;
                nextAvalanche = now + 240;
            }
            if (avalancheAt == 0) {
                return;
            }
            if (now % 4 == 0) {
                for (Vec3 m : marks) {
                    BossService.ring(level, ParticleTypes.SNOWFLAKE, m, 2.5, 16);
                    level.sendParticles(ParticleTypes.SNOWFLAKE, m.x, m.y + 6.0, m.z, 6, 1.5, 0.5, 1.5, 0.0);
                }
            }
            if (now < avalancheAt) {
                return;
            }
            avalancheAt = 0;
            float damage = BossService.abilityDamage(host, kind, 6.0);
            for (Vec3 m : marks) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                        m.x, m.y + 1.0, m.z, 80, 1.5, 1.0, 1.5, 0.2);
                BossService.sound(level, m, SoundEvents.SNOW_BREAK, 2.0f, 0.5f);
                for (ServerPlayer p : around(host, m, 2.5)) {
                    BossService.strike(host, level, boss, p, damage, AVALANCHE_SHARE);
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), boss);
                }
            }
            marks.clear();
        }

        @Override
        void died(ServerLevel level) {
            meltWalls(level);
            boulderLands = 0;
        }

        @Override
        void cleanUp(ServerLevel level) {
            meltWalls(level);
        }
    }

    // ================================================================== Glutkoloss

    /**
     * The ember colossus: a giant magma cube that leaves cracks of lava behind (3 % of the max
     * health per second to whoever stands in them, through shields), and at half its health
     * breaks into two halves with 40 % each.
     *
     * <p>Simon 05.11.: "der Magma-Cube-Boss ist viel zu leicht, da er nie an einen rankommt". Every
     * {@link #POUNCE_EVERY} ticks each half leaps at its target (3 to 24 blocks away, over walls too);
     * where it lands, every defender within {@link #POUNCE_RADIUS} blocks takes {@link #POUNCE_SHARE} %
     * of the max health (a raised shield takes the blow), and a crack opens.</p>
     *
     * <p>Bosse 2 (Simon 05.11.): cracks 3 → 5 %/s; the eruption every 15 s (he sinks in, 1.5 s later six
     * lumps of magma fall on warning rings round the defenders, 15 % each); when one half falls, the
     * other goes white-hot (faster, a leap every 3 s, an eruption every 10 s).</p>
     */
    static final class EmberColossus extends BossBrains.Brain {
        static final double CRACK_SHARE = 5.0;
        static final double ERUPTION_SHARE = 15.0;
        static final int ERUPTION_EVERY = 15 * 20;
        static final int WHITE_ERUPTION_EVERY = 10 * 20;
        static final int ERUPTION_WINDUP = 30;
        static final int ERUPTION_MARKS = 6;
        static final double ERUPTION_RADIUS = 2.0;
        static final int WHITE_POUNCE_EVERY = 3 * 20;
        static final double HALF_SHARE = 0.4;
        static final int CRACK_TICKS = 12 * 20;
        static final double POUNCE_SHARE = 12.0;
        static final double POUNCE_RADIUS = 3.5;
        static final int POUNCE_EVERY = 5 * 20;

        /** One half's leap at its target: when the next may come, and whether it is in the air. */
        static final class Pounce {
            long next;
            long since;
            boolean inAir;
            int landed;
        }

        final Pounce pounceA = new Pounce();
        final Pounce pounceB = new Pounce();
        record Crack(Vec3 at, long until) {
        }

        final List<Crack> cracks = new ArrayList<>();
        boolean split;
        final Stuck stuckA = new Stuck();
        final Stuck stuckB = new Stuck();
        /** Both halves stood once; when one is down the other goes white-hot. */
        boolean halves;
        boolean whiteHot;
        long nextEruption;
        long eruptionAt;
        final List<Vec3> eruptionMarks = new ArrayList<>();
        int eruptions;
        boolean eruptionTold;

        EmberColossus(BossService service, BossKind kind) {
            super(service, kind);
        }

        @Override
        void spawned(BossService.Host host, Mob boss) {
            super.spawned(host, boss);
            body(boss);
            nextEruption = spawnedAt + 200;
        }

        private static void body(Mob boss) {
            BossService.set(boss, Attributes.MOVEMENT_SPEED, 0.5);
            BossService.set(boss, Attributes.JUMP_STRENGTH, 0.62);
        }

        @Override
        String phase(ServerLevel level) {
            if (whiteHot) {
                return "nightsiege.boss.phase.white_hot";
            }
            return split ? "nightsiege.boss.phase.split" : null;
        }

        @Override
        void tick(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            if (!split && below(boss, 0.5)) {
                split = true;
                if (service.splitColossus(host, boss, HALF_SHARE)) {
                    Mob other = service.rider(level);
                    if (other != null) {
                        body(other);
                        halves = true;
                    }
                    level.sendParticles(ParticleTypes.LAVA, boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 1.5, 1.0, 1.5, 0.2);
                    level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 1.0, boss.getZ(), 4, 1.0, 0.5, 1.0, 0.0);
                    BossService.sound(level, boss.position(), SoundEvents.MAGMA_CUBE_SQUISH, 2.0f, 0.4f);
                    level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.GENERIC_EXPLODE,
                            net.minecraft.sounds.SoundSource.HOSTILE, 1.2f, 0.6f);
                    tell(host, "nightsiege.boss.ember_colossus.split", ChatFormatting.GOLD);
                }
            }
            Mob other = service.rider(level);
            if (now % 20 == 0) {
                service.emberStage(level);   // brighter at 66 % and 33 % (Bosse 2)
            }
            if (halves && other == null && !whiteHot) {
                // One half is down: the other burns white-hot.
                whiteHot = true;
                BossService.set(boss, Attributes.MOVEMENT_SPEED, 0.65);
                pounceA.next = Math.min(pounceA.next, now + 20);
                nextEruption = Math.min(nextEruption, now + 60);
                level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 1.0, boss.getZ(), 60, 1.2, 1.0, 1.2, 0.1);
                BossService.sound(level, boss.position(), SoundEvents.BLAZE_SHOOT, 2.0f, 0.5f);
                tell(host, "nightsiege.boss.ember_colossus.white_hot", ChatFormatting.YELLOW);
            }
            if (whiteHot && now % 3 == 0) {
                level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(),
                        2, boss.getBbWidth() * 0.4, boss.getBbHeight() * 0.3, boss.getBbWidth() * 0.4, 0.01);
            }
            if (tickEruption(host, boss, now)) {
                tickCracks(host, boss, now);
                return;   // sunk in: he waits for the eruption
            }
            if (now % 10 == 0) {
                crack(boss, now);
                if (other != null) {
                    crack(other, now);
                }
            }
            stuckA.leap(host, boss, now, 5.0);
            pounce(host, boss, pounceA, now);
            if (other != null) {
                stuckB.leap(host, other, now, 5.0);
                pounce(host, other, pounceB, now);
                if (now % 20 == 0 && (other.getTarget() == null || !other.getTarget().isAlive())) {
                    service.aim(host, other);
                }
            }
            tickCracks(host, boss, now);
        }

        /** Leaps of the halves so far (for the test). */
        int pounces() {
            return pounceA.landed + pounceB.landed;
        }

        /** The leap at the target and its landing (Simon 05.11.). */
        private void pounce(BossService.Host host, Mob body, Pounce p, long now) {
            ServerLevel level = host.world();
            if (p.inAir) {
                if (body.onGround() && now - p.since > 6) {
                    p.inAir = false;
                    p.landed++;
                    Vec3 at = body.position();
                    level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 30, POUNCE_RADIUS * 0.5, 0.2, POUNCE_RADIUS * 0.5, 0.2);
                    level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.8, 0.2, 0.8, 0.0);
                    BossService.sound(level, at, SoundEvents.MAGMA_CUBE_SQUISH, 2.0f, 0.5f);
                    for (ServerPlayer player : host.defenders()) {
                        if (player.distanceToSqr(at) < POUNCE_RADIUS * POUNCE_RADIUS && Math.abs(player.getY() - at.y) < 2.5) {
                            PercentDamage.hit(level, player, body, body, POUNCE_SHARE, host.level(), host.config(),
                                    host.config().bossPercentDamage);
                        }
                    }
                    cracks.add(new Crack(at, now + CRACK_TICKS));
                } else if (now - p.since > 80) {
                    p.inAir = false;   // never came down on the ground (water, a ledge): try again later
                }
                return;
            }
            if (now < p.next || !body.onGround() || !(body.getTarget() instanceof ServerPlayer target) || !target.isAlive()) {
                return;
            }
            double d2 = target.distanceToSqr(body);
            if (d2 < 3.0 * 3.0 || d2 > 24.0 * 24.0) {
                return;
            }
            BossBrains.leap(level, body, target.position(), 2.5);
            p.inAir = true;
            p.since = now;
            p.next = now + (whiteHot ? WHITE_POUNCE_EVERY : POUNCE_EVERY);
        }

        /** Eruptions so far (for the test). */
        int eruptions() {
            return eruptions;
        }

        @Override
        String stats() {
            return "pounces=" + pounces() + " eruptions=" + eruptions + " split=" + split + " whiteHot=" + whiteHot;
        }

        /**
         * The eruption (Bosse 2): he sinks in, warning rings glow round the defenders, and 1.5 s later
         * a lump of magma falls on each (15 %, it sets on fire and leaves a crack). True while he sinks.
         */
        private boolean tickEruption(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            if (eruptionAt == 0) {
                if (now < nextEruption || !boss.onGround() || pounceA.inAir) {
                    return false;
                }
                eruptionMarks.clear();
                List<ServerPlayer> near = new ArrayList<>();
                for (ServerPlayer p : host.defenders()) {
                    if (p.distanceToSqr(boss) < 32.0 * 32.0) {
                        near.add(p);
                        if (eruptionMarks.size() < ERUPTION_MARKS) {
                            eruptionMarks.add(p.position());
                        }
                    }
                }
                if (near.isEmpty()) {
                    nextEruption = now + 40;
                    return false;
                }
                for (int i = 0; i < 20 && eruptionMarks.size() < ERUPTION_MARKS; i++) {
                    ServerPlayer p = near.get(host.random().nextInt(near.size()));
                    double a = host.random().nextDouble() * Math.PI * 2.0;
                    double r = 2.5 + host.random().nextDouble() * 2.5;
                    BlockPos spot = BossService.groundNear(level, p.getX() + Math.cos(a) * r, p.getY(), p.getZ() + Math.sin(a) * r);
                    if (spot != null) {
                        eruptionMarks.add(Vec3.atBottomCenterOf(spot));
                    }
                }
                eruptionAt = now + ERUPTION_WINDUP;
                nextEruption = now + (whiteHot ? WHITE_ERUPTION_EVERY : ERUPTION_EVERY);
                BossService.sound(level, boss.position(), SoundEvents.LAVA_AMBIENT, 2.0f, 0.5f);
                BossService.sound(level, boss.position(), SoundEvents.BASALT_BREAK, 2.0f, 0.4f);
                if (!eruptionTold) {
                    eruptionTold = true;
                    tell(host, "nightsiege.boss.ember_colossus.eruption", ChatFormatting.GOLD);
                }
            }
            boss.getNavigation().stop();
            boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
            if (now % 3 == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, boss.getX(), boss.getY() + 0.3, boss.getZ(), 4,
                        boss.getBbWidth() * 0.4, 0.1, boss.getBbWidth() * 0.4, 0.02);
                level.sendParticles(ParticleTypes.LAVA, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(), 2, 0.5, 0.1, 0.5, 0.0);
                for (Vec3 m : eruptionMarks) {
                    BossService.ring(level, new DustParticleOptions(0xFF5A0A, 1.4f), m, ERUPTION_RADIUS, 14);
                    level.sendParticles(ParticleTypes.SMALL_FLAME, m.x, m.y + 0.1, m.z, 2, 0.6, 0.0, 0.6, 0.0);
                }
            }
            if (now < eruptionAt) {
                return true;
            }
            eruptionAt = 0;
            eruptions++;
            float damage = BossService.abilityDamage(host, kind, 5.0);
            Set<UUID> hit = new HashSet<>();
            for (Vec3 m : eruptionMarks) {
                level.sendParticles(ParticleTypes.LAVA, m.x, m.y + 0.3, m.z, 20, 0.8, 0.3, 0.8, 0.2);
                level.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 0.3, m.z, 1, 0.2, 0.1, 0.2, 0.0);
                BossService.sound(level, m, SoundEvents.FIRECHARGE_USE, 1.4f, 0.6f);
                for (ServerPlayer p : around(host, m, ERUPTION_RADIUS)) {
                    if (hit.add(p.getUUID())) {
                        BossService.strike(host, level, boss, p, damage, ERUPTION_SHARE);
                        p.igniteForSeconds(3f);
                    }
                }
                cracks.add(new Crack(m, now + CRACK_TICKS));
            }
            eruptionMarks.clear();
            return false;
        }

        /** A new crack where a half stands on the ground (not right beside the last one). */
        private void crack(Mob body, long now) {
            if (!body.onGround()) {
                return;
            }
            Vec3 at = body.position();
            for (Crack c : cracks) {
                if (c.at().distanceToSqr(at) < 1.6 * 1.6) {
                    return;
                }
            }
            cracks.add(new Crack(at, now + CRACK_TICKS));
            while (cracks.size() > 48) {
                cracks.remove(0);
            }
        }

        private void tickCracks(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            Iterator<Crack> it = cracks.iterator();
            while (it.hasNext()) {
                if (now >= it.next().until()) {
                    it.remove();
                }
            }
            if (now % 4 == 0) {
                for (Crack c : cracks) {
                    level.sendParticles(new DustParticleOptions(0xFF6A10, 1.3f), c.at().x, c.at().y + 0.1, c.at().z, 3, 0.6, 0.02, 0.6, 0.0);
                    if (host.random().nextInt(4) == 0) {
                        level.sendParticles(ParticleTypes.LAVA, c.at().x, c.at().y + 0.1, c.at().z, 1, 0.4, 0.0, 0.4, 0.0);
                    }
                }
            }
            if (now % 20 != 0) {
                return;
            }
            for (ServerPlayer p : host.defenders()) {
                for (Crack c : cracks) {
                    double dx = p.getX() - c.at().x;
                    double dz = p.getZ() - c.at().z;
                    if (dx * dx + dz * dz < 1.4 * 1.4 && Math.abs(p.getY() - c.at().y) < 1.5) {
                        PercentDamage.hitThrough(level, p, boss, null, CRACK_SHARE, host.level(), host.config(),
                                host.config().bossPercentDamage);
                        p.igniteForSeconds(2f);
                        break;
                    }
                }
            }
        }

        @Override
        void cleanUp(ServerLevel level) {
            cracks.clear();
            eruptionMarks.clear();
            eruptionAt = 0;
        }
    }

    // ================================================================== Sturmhexe

    /**
     * The storm witch: she circles the base on her phantom, out of reach of swords. Chain
     * lightning jumps from defender to defender (6 % of the max health each), a storm sweeps
     * the defenders off the walls, and every 30 s she lands for 8 s – then she can be reached.
     *
     * <p>Bosse 2 (Simon 05.11.): chain lightning 6 → 10 %; a lightning strike on a warning ring (20 %);
     * on the ground she calls two vexes; below half the eye of the storm pulls everyone in the base
     * towards its middle for 6 s (where the chain lightning finds them all).</p>
     */
    static final class StormWitch extends BossBrains.Brain {
        static final double BOLT_SHARE = 10.0;
        static final double STRIKE_SHARE = 20.0;
        static final int STRIKE_EVERY = 7 * 20;
        static final int STRIKE_WARN = 30;
        static final double STRIKE_RADIUS = 2.5;
        static final int EYE_EVERY = 20 * 20;
        static final int EYE_TICKS = 6 * 20;
        static final double EYE_RADIUS = 16.0;
        static final int VEXES = 2;
        static final int FLY_TICKS = 30 * 20;
        static final int LAND_TICKS = 8 * 20;
        enum Flight { CIRCLE, DOWN, LANDED, UP }

        Flight flight = Flight.CIRCLE;
        long landAt;
        long liftAt;
        double angle;
        Vec3 landing;
        long nextBolt;
        long nextStorm;
        long stormAt;
        long nextStrike;
        long strikeAt;
        final List<Vec3> strikeMarks = new ArrayList<>();
        int strikes;
        boolean eyePhase;
        long nextEye;
        long eyeUntil;
        Vec3 eyeAt;
        int eyes;
        int vexes;

        StormWitch(BossService service, BossKind kind) {
            super(service, kind);
        }

        @Override
        void spawned(BossService.Host host, Mob boss) {
            super.spawned(host, boss);
            BlockPos a = host.anchor();
            angle = Math.atan2(boss.getZ() - a.getZ() - 0.5, boss.getX() - a.getX() - 0.5);
            landAt = spawnedAt + FLY_TICKS;
            nextBolt = spawnedAt + 80;
            nextStorm = spawnedAt + 200;
            nextStrike = spawnedAt + 120;
        }

        @Override
        String phase(ServerLevel level) {
            if (eyeUntil > 0) {
                return "nightsiege.boss.phase.storm_eye";
            }
            return flight == Flight.LANDED ? "nightsiege.boss.phase.landed" : "nightsiege.boss.phase.flying";
        }

        @Override
        void tick(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            Mob mount = service.mount(level);
            if (mount != null) {
                fly(host, boss, mount, now);
                if (now % 3 == 0) {
                    level.sendParticles(ParticleTypes.CLOUD, mount.getX(), mount.getY() - 0.2, mount.getZ(), 2, 0.8, 0.1, 0.8, 0.0);
                }
            }
            if (now >= nextBolt) {
                chain(host, boss);
                nextBolt = now + (below(boss, 0.5) ? 80 : 110);
            }
            tickStorm(host, boss, now);
            tickStrike(host, boss, now);
            if (!eyePhase && below(boss, 0.5)) {
                eyePhase = true;
                nextEye = now + 20;
            }
            tickEye(host, boss, now);
        }

        /** For the tests: lightning strikes, storm eyes and vexes so far. */
        int strikes() {
            return strikes;
        }

        int eyes() {
            return eyes;
        }

        int vexes() {
            return vexes;
        }

        @Override
        void testNudge(long now) {
            if (flight == Flight.CIRCLE) {
                landAt = now;
            }
        }

        @Override
        String stats() {
            return "strikes=" + strikes + " eyes=" + eyes + " vexes=" + vexes + " eyeNow=" + (eyeUntil > 0) + " flight=" + flight;
        }

        /**
         * The lightning strike (Bosse 2): a ring of sparks under a defender (two below half), and
         * after 1.5 s the bolt comes down on it: 20 % to whoever still stands there.
         */
        private void tickStrike(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            if (strikeAt == 0) {
                if (now < nextStrike) {
                    return;
                }
                nextStrike = now + STRIKE_EVERY;
                List<ServerPlayer> near = new ArrayList<>();
                for (ServerPlayer p : host.defenders()) {
                    if (p.distanceToSqr(boss) < 40.0 * 40.0) {
                        near.add(p);
                    }
                }
                java.util.Collections.shuffle(near, host.random());
                strikeMarks.clear();
                for (int i = 0; i < near.size() && i < (below(boss, 0.5) ? 2 : 1); i++) {
                    strikeMarks.add(near.get(i).position());
                }
                if (strikeMarks.isEmpty()) {
                    return;
                }
                strikeAt = now + STRIKE_WARN;
                BossService.sound(level, boss.position(), SoundEvents.EVOKER_PREPARE_ATTACK, 1.6f, 1.4f);
            }
            if (now % 3 == 0) {
                for (Vec3 m : strikeMarks) {
                    BossService.ring(level, ParticleTypes.ELECTRIC_SPARK, m, STRIKE_RADIUS, 16);
                    BossService.ring(level, new DustParticleOptions(0xFFE34A, 1.2f), m, STRIKE_RADIUS * 0.6, 10);
                }
            }
            if (now < strikeAt) {
                return;
            }
            strikeAt = 0;
            strikes++;
            float damage = BossService.abilityDamage(host, kind, 6.0);
            for (Vec3 m : strikeMarks) {
                bolt(level, m);
                for (ServerPlayer p : around(host, m, STRIKE_RADIUS)) {
                    BossService.strike(host, level, boss, p, damage, STRIKE_SHARE);
                }
            }
            strikeMarks.clear();
        }

        /**
         * The eye of the storm (Bosse 2, below half, every 20 s): for 6 s a whirl at the core pulls
         * every defender within 16 blocks towards its middle, harder the closer he is.
         */
        private void tickEye(BossService.Host host, Mob boss, long now) {
            if (!eyePhase) {
                return;
            }
            ServerLevel level = host.world();
            if (eyeUntil == 0) {
                if (now < nextEye) {
                    return;
                }
                BlockPos a = host.anchor();
                double x = a.getX() + 0.5;
                double z = a.getZ() + 0.5;
                eyeAt = new Vec3(x, BossService.surface(level, x, z), z);
                eyeUntil = now + EYE_TICKS;
                nextEye = now + EYE_EVERY;
                eyes++;
                tell(host, "nightsiege.boss.storm_witch.eye", ChatFormatting.YELLOW);
                BossService.sound(level, eyeAt, SoundEvents.BREEZE_JUMP, 2.0f, 0.3f);
            }
            if (now % 2 == 0) {
                double turn = now * 0.35;
                for (int k = 0; k < 3; k++) {
                    double a = turn + k * Math.PI * 2.0 / 3.0;
                    for (double r = 2.0; r <= EYE_RADIUS; r += 3.5) {
                        level.sendParticles(ParticleTypes.CLOUD, eyeAt.x + Math.cos(a + r * 0.25) * r, eyeAt.y + 0.4 + r * 0.15,
                                eyeAt.z + Math.sin(a + r * 0.25) * r, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
            for (ServerPlayer p : host.defenders()) {
                Vec3 to = eyeAt.subtract(p.position()).multiply(1, 0, 1);
                double d = to.length();
                if (d > EYE_RADIUS || d < 1.5 || Math.abs(p.getY() - eyeAt.y) > 8.0) {
                    continue;
                }
                Vec3 pull = to.scale(1.0 / d);
                Vec3 swirl = new Vec3(-pull.z, 0, pull.x);
                double strength = 0.03 + 0.07 * (1.0 - d / EYE_RADIUS);
                p.push(pull.x * strength + swirl.x * 0.02, 0.0, pull.z * strength + swirl.z * 0.02);
                p.hurtMarked = true;
            }
            if (now >= eyeUntil) {
                eyeUntil = 0;
            }
        }

        /** Steers the phantom: round the base high up, down to land, a while on the ground, up again. */
        private void fly(BossService.Host host, Mob boss, Mob mount, long now) {
            ServerLevel level = host.world();
            BlockPos a = host.anchor();
            double cx = a.getX() + 0.5;
            double cz = a.getZ() + 0.5;
            double edge = Math.max(10.0, host.baseEdge(mount.getX(), mount.getZ()));
            Vec3 goal;
            switch (flight) {
                case CIRCLE -> {
                    angle += 0.012;
                    double r = edge + 8.0;
                    double x = cx + Math.cos(angle) * r;
                    double z = cz + Math.sin(angle) * r;
                    goal = new Vec3(x, BossService.surface(level, x, z) + 13.0, z);
                    if (now >= landAt) {
                        double lx = cx + Math.cos(angle) * (edge + 4.0);
                        double lz = cz + Math.sin(angle) * (edge + 4.0);
                        BlockPos spot = BossService.groundNear(level, lx, BossService.surface(level, lx, lz), lz);
                        if (spot != null) {
                            landing = Vec3.atBottomCenterOf(spot);
                            flight = Flight.DOWN;
                        } else {
                            landAt = now + 60;
                        }
                    }
                }
                case DOWN -> {
                    goal = landing;
                    if (mount.position().distanceToSqr(landing) < 0.6 * 0.6) {
                        flight = Flight.LANDED;
                        liftAt = now + LAND_TICKS;
                        tell(host, "nightsiege.boss.storm_witch.landed", ChatFormatting.GREEN);
                        BossService.sound(level, landing, SoundEvents.PHANTOM_FLAP, 2.0f, 0.5f);
                        // On the ground she calls two vexes (Bosse 2).
                        for (int i = 0; i < VEXES; i++) {
                            double turn = host.random().nextDouble() * Math.PI * 2.0;
                            BlockPos spot = BlockPos.containing(landing.x + Math.cos(turn) * 2.0, landing.y + 1.5, landing.z + Math.sin(turn) * 2.0);
                            if (host.spawnAdd("minecraft:vex", spot) != null) {
                                vexes++;
                            }
                        }
                        BossService.sound(level, landing, SoundEvents.EVOKER_PREPARE_SUMMON, 1.6f, 1.2f);
                    }
                }
                case LANDED -> {
                    goal = landing;
                    if (now >= liftAt) {
                        flight = Flight.UP;
                    }
                }
                default -> {
                    double x = cx + Math.cos(angle) * (edge + 8.0);
                    double z = cz + Math.sin(angle) * (edge + 8.0);
                    goal = new Vec3(x, BossService.surface(level, x, z) + 13.0, z);
                    if (mount.position().distanceToSqr(goal) < 2.0 * 2.0) {
                        flight = Flight.CIRCLE;
                        landAt = now + FLY_TICKS;
                    }
                }
            }
            Vec3 pos = mount.position();
            Vec3 step = goal.subtract(pos);
            double len = step.length();
            double max = flight == Flight.CIRCLE ? 0.45 : 0.35;
            if (len > max) {
                step = step.scale(max / len);
            }
            Vec3 next = pos.add(step);
            float yaw = len > 1.0e-3 ? (float) (Math.atan2(step.z, step.x) * 180.0 / Math.PI) - 90f : mount.getYRot();
            mount.snapTo(next.x, next.y, next.z, yaw, 0f);
            mount.setYHeadRot(yaw);
            mount.yBodyRot = yaw;
            if (boss.getVehicle() != mount && flight != Flight.LANDED) {
                boss.startRiding(mount);   // knocked off: back into the saddle
            }
        }

        /** Chain lightning: the nearest defender in sight, then up to three more within 7 blocks of the last. */
        private void chain(BossService.Host host, Mob boss) {
            ServerLevel level = host.world();
            ServerPlayer first = null;
            double best = 30.0 * 30.0;
            for (ServerPlayer p : host.defenders()) {
                double d = p.distanceToSqr(boss);
                if (d < best && boss.hasLineOfSight(p)) {
                    best = d;
                    first = p;
                }
            }
            if (first == null) {
                return;
            }
            Set<UUID> hit = new HashSet<>();
            Vec3 from = boss.position().add(0, boss.getBbHeight() * 0.8, 0);
            ServerPlayer p = first;
            float damage = BossService.abilityDamage(host, kind, 3.0);
            for (int jump = 0; jump < 4 && p != null; jump++) {
                hit.add(p.getUUID());
                Vec3 to = p.position().add(0, 1.0, 0);
                BossService.line(level, ParticleTypes.ELECTRIC_SPARK, from, to, (int) Math.max(6, from.distanceTo(to) * 2));
                bolt(level, p.position());
                BossService.strike(host, level, boss, p, damage, BOLT_SHARE);
                from = to;
                ServerPlayer next = null;
                double near = 7.0 * 7.0;
                for (ServerPlayer o : host.defenders()) {
                    double d = o.position().distanceToSqr(p.position());
                    if (!hit.contains(o.getUUID()) && d < near) {
                        near = d;
                        next = o;
                    }
                }
                p = next;
            }
            BossService.sound(level, boss.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 1.4f);
        }

        private static void bolt(ServerLevel level, Vec3 at) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("lightning_bolt"));
            Entity e = type == null ? null : type.create(level, EntitySpawnReason.EVENT);
            if (e instanceof LightningBolt bolt) {
                bolt.setVisualOnly(true);
                bolt.snapTo(at.x, at.y, at.z);
                level.addFreshEntity(bolt);
            }
        }

        /**
         * The storm: a second and a half of wind round her, then a gust that throws the defenders on
         * the walls (two blocks or more above the ground of the base) away from her; the others stagger.
         */
        private void tickStorm(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            if (stormAt == 0 && now >= nextStorm) {
                stormAt = now + 30;
                nextStorm = now + 260;
                BossService.sound(level, boss.position(), SoundEvents.BREEZE_JUMP, 2.0f, 0.4f);
            }
            if (stormAt == 0) {
                return;
            }
            if (now < stormAt) {
                if (now % 3 == 0) {
                    BossService.ring(level, ParticleTypes.CLOUD, boss.position(), 3.0 + (30 - (stormAt - now)) * 0.3, 20);
                }
                return;
            }
            stormAt = 0;
            int ground = host.anchor().getY();
            for (ServerPlayer p : host.defenders()) {
                double d = p.distanceToSqr(boss);
                if (d > 28.0 * 28.0) {
                    continue;
                }
                boolean onWall = p.getY() >= ground + 2.0;
                Vec3 away = p.position().subtract(boss.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
                double push = onWall ? 1.7 : 0.6;
                p.push(away.x * push, onWall ? 0.45 : 0.2, away.z * push);
                p.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
            }
            BossService.sound(level, boss.position(), SoundEvents.BREEZE_JUMP, 2.0f, 0.7f);
            BossService.sound(level, boss.position(), SoundEvents.ELYTRA_FLYING, 1.0f, 1.6f);
        }

        @Override
        void died(ServerLevel level) {
            eyeUntil = 0;
            strikeAt = 0;
            strikeMarks.clear();
            Mob mount = service.mount(level);
            if (mount != null) {
                level.sendParticles(ParticleTypes.CLOUD, mount.getX(), mount.getY(), mount.getZ(), 30, 1.0, 0.5, 1.0, 0.1);
                mount.discard();
            }
        }

        @Override
        void cleanUp(ServerLevel level) {
            died(level);
        }
    }

    // ================================================================== Rammbock-Titan

    /**
     * The ram titan: a giant armoured ravager. Its charge runs through gates and walls and
     * through the tank (no shield stops it); where it ends, the ground caves in (a 3 × 3 crater).
     * Blocks it breaks fly on as debris. Whoever stands behind it is trampled. Broken blocks come
     * back after the night. Below half it rages and charges twice in a row.
     *
     * <p>Bosse 2 (Simon 05.11.): charge 12 → 30 %, crater 10 → 15 %, the trample (20 %), the debris
     * (10 %), the double charge below half.</p>
     */
    static final class RamTitan extends BossBrains.Brain {
        private static final Identifier RAGE = Identifier.fromNamespaceAndPath("nightsiege", "boss_ram_rage");
        static final double CHARGE_SHARE = 30.0;
        static final double CRATER_SHARE = 15.0;
        static final double TRAMPLE_SHARE = 20.0;
        static final double DEBRIS_SHARE = 10.0;
        /** Most blocks one charge breaks. */
        static final int MAX_BREAK = 30;
        /** Most pieces of debris one charge throws. */
        static final int MAX_DEBRIS = 5;
        /** How long a defender has to stand behind it before it tramples (ticks). */
        static final int TRAMPLE_AFTER = 40;
        static final double TRAMPLE_RANGE = 4.5;

        /** A block the titan broke, flying on. */
        static final class Debris {
            Vec3 pos;
            Vec3 vel;
            final BlockState state;
            int life;
            final Set<UUID> hit = new HashSet<>();

            Debris(Vec3 pos, Vec3 vel, BlockState state) {
                this.pos = pos;
                this.vel = vel;
                this.state = state;
            }
        }

        long nextCharge;
        long windupUntil;
        long chargeUntil;
        Vec3 chargeDir;
        int broken;
        int thrown;
        final Set<UUID> hitThisCharge = new HashSet<>();
        boolean enraged;
        boolean secondCharge;
        int behindTicks;
        long nextTrample;
        final List<Debris> debris = new ArrayList<>();
        int charges;
        int tramples;
        int debrisHits;

        RamTitan(BossService service, BossKind kind) {
            super(service, kind);
        }

        @Override
        void spawned(BossService.Host host, Mob boss) {
            super.spawned(host, boss);
            nextCharge = spawnedAt + 80;
            BossService.set(boss, Attributes.MOVEMENT_SPEED, 0.3);
        }

        @Override
        String phase(ServerLevel level) {
            return enraged ? "nightsiege.boss.phase.rage" : null;
        }

        /** For the tests: charges, tramples and debris hits so far. */
        int charges() {
            return charges;
        }

        int tramples() {
            return tramples;
        }

        int debrisHits() {
            return debrisHits;
        }

        @Override
        String stats() {
            return "charges=" + charges + " tramples=" + tramples + " debrisHits=" + debrisHits + " enraged=" + enraged;
        }

        @Override
        void tick(BossService.Host host, Mob beast, long now) {
            ServerLevel level = host.world();
            tickDebris(host, beast);
            if (!enraged && below(beast, 0.5)) {
                enraged = true;
                AttributeInstance speed = beast.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null && !speed.hasModifier(RAGE)) {
                    speed.addTransientModifier(new AttributeModifier(RAGE, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                tell(host, "nightsiege.boss.ram_titan.rage", ChatFormatting.RED);
                BossService.sound(level, beast.position(), SoundEvents.RAVAGER_ROAR, 2.0f, 0.4f);
            }
            if (chargeUntil > 0) {
                tickCharge(host, beast, now);
                return;
            }
            if (windupUntil > 0) {
                beast.getNavigation().stop();
                beast.setDeltaMovement(0, beast.getDeltaMovement().y, 0);
                if (now % 3 == 0) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, beast.getX(), beast.getY() + 0.2, beast.getZ(), 5, 1.2, 0.1, 1.2, 0.02);
                }
                if (now >= windupUntil) {
                    windupUntil = 0;
                    chargeUntil = now + 40;
                    broken = 0;
                    thrown = 0;
                    hitThisCharge.clear();
                    charges++;
                    BossService.sound(level, beast.position(), SoundEvents.RAVAGER_STEP, 2.0f, 0.5f);
                }
                return;
            }
            if (tickTrample(host, beast, now)) {
                return;
            }
            if (now < nextCharge || !beast.onGround()) {
                return;
            }
            if (startCharge(host, beast, now, 30)) {
                nextCharge = now + (enraged ? 150 : 220);
            } else {
                nextCharge = now + 20;
            }
        }

        /** Turns towards the defender to run at (in reach, walls or not) and winds up. False when none is there. */
        private boolean startCharge(BossService.Host host, Mob beast, long now, int windup) {
            ServerPlayer target = null;
            double best = 26.0 * 26.0;
            for (ServerPlayer p : host.defenders()) {
                double d = p.distanceToSqr(beast);
                if (d > 5.0 * 5.0 && d < best) {
                    best = d;
                    target = p;
                }
            }
            if (target == null) {
                return false;
            }
            Vec3 dir = target.position().subtract(beast.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0e-4) {
                return false;
            }
            chargeDir = dir.normalize();
            float yaw = (float) (Math.atan2(chargeDir.z, chargeDir.x) * 180.0 / Math.PI) - 90f;
            beast.setYRot(yaw);
            beast.yBodyRot = yaw;
            beast.setYHeadRot(yaw);
            windupUntil = now + windup;
            BossService.sound(host.world(), beast.position(), SoundEvents.RAVAGER_ROAR, 2.0f, 0.7f);
            return true;
        }

        /**
         * The trample (Bosse 2): whoever stands behind it for two seconds is kicked (20 %, far away).
         * True when it kicked this tick.
         */
        private boolean tickTrample(BossService.Host host, Mob beast, long now) {
            if (now % 5 != 0) {
                return false;
            }
            double yaw = Math.toRadians(beast.yBodyRot);
            Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            List<ServerPlayer> behind = new ArrayList<>();
            for (ServerPlayer p : around(host, beast.position(), TRAMPLE_RANGE + beast.getBbWidth() * 0.5)) {
                Vec3 to = p.position().subtract(beast.position()).multiply(1, 0, 1);
                if (to.lengthSqr() > 1.0e-4 && to.normalize().dot(forward) < -0.2) {
                    behind.add(p);
                }
            }
            behindTicks = behind.isEmpty() ? 0 : behindTicks + 5;
            if (behindTicks < TRAMPLE_AFTER || now < nextTrample) {
                return false;
            }
            ServerLevel level = host.world();
            behindTicks = 0;
            nextTrample = now + 80;
            tramples++;
            float damage = BossService.abilityDamage(host, kind, 6.0);
            for (ServerPlayer p : behind) {
                BossService.strike(host, level, beast, p, damage, TRAMPLE_SHARE);
                BossService.knock(p, beast.position(), 1.6, 0.6);
            }
            Vec3 back = beast.position().subtract(forward.scale(beast.getBbWidth() * 0.6));
            level.sendParticles(ParticleTypes.CLOUD, back.x, back.y + 0.3, back.z, 20, 1.0, 0.2, 1.0, 0.05);
            BossService.sound(level, back, SoundEvents.RAVAGER_ATTACK, 2.0f, 0.5f);
            BossService.sound(level, back, SoundEvents.MACE_SMASH_GROUND, 1.4f, 0.7f);
            return true;
        }

        private void tickCharge(BossService.Host host, Mob beast, long now) {
            ServerLevel level = host.world();
            beast.getNavigation().stop();
            Vec3 v = beast.getDeltaMovement();
            beast.setDeltaMovement(chargeDir.x * 0.8, v.y, chargeDir.z * 0.8);
            beast.hurtMarked = true;
            if (now % 2 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, beast.getX(), beast.getY() + 0.3, beast.getZ(), 4, 0.9, 0.1, 0.9, 0.02);
            }
            // Through the wall: what stands right in front of its head breaks.
            if (broken < MAX_BREAK) {
                ram(host, beast);
            }
            float damage = BossService.abilityDamage(host, kind, 10.0);
            AABB box = beast.getBoundingBox().inflate(1.0);
            for (ServerPlayer p : host.defenders()) {
                if (box.intersects(p.getBoundingBox()) && hitThisCharge.add(p.getUUID())) {
                    BossService.strikeThrough(host, level, beast, p, damage, CHARGE_SHARE);
                    p.push(chargeDir.x * 2.0, 0.7, chargeDir.z * 2.0);
                    p.hurtMarked = true;
                    BossService.sound(level, p.position(), SoundEvents.IRON_GOLEM_ATTACK, 1.6f, 0.5f);
                }
            }
            if (now >= chargeUntil) {
                chargeUntil = 0;
                crater(host, beast);
                // Raging: straight into a second charge (Bosse 2).
                if (enraged && !secondCharge && startCharge(host, beast, now, 10)) {
                    secondCharge = true;
                } else {
                    secondCharge = false;
                }
            }
        }

        /** Breaks the blocks of the wall right in front of the titan (3 wide, up to its shoulders); some fly on. */
        private void ram(BossService.Host host, Mob beast) {
            ServerLevel level = host.world();
            if (!NightDamage.recording(level)) {
                return;   // a try-out breaks nothing
            }
            double reach = beast.getBbWidth() / 2.0 + 0.8;
            Vec3 front = beast.position().add(chargeDir.scale(reach));
            Vec3 side = new Vec3(-chargeDir.z, 0, chargeDir.x);
            int height = (int) Math.ceil(Math.min(4.0, beast.getBbHeight()));
            boolean any = false;
            for (int i = -1; i <= 1 && broken < MAX_BREAK; i++) {
                for (int dy = 0; dy < height && broken < MAX_BREAK; dy++) {
                    BlockPos at = BlockPos.containing(front.x + side.x * i, beast.getY() + dy + 0.1, front.z + side.z * i);
                    if (breakable(level, at, host)) {
                        BlockState state = level.getBlockState(at);
                        NightDamage.asAttacker(() -> level.destroyBlock(at, false));   // the night's: back after it (31.10.)
                        broken++;
                        any = true;
                        if (thrown < MAX_DEBRIS && debris.size() < 12 && (i == 0 || host.random().nextInt(3) == 0)) {
                            thrown++;
                            Vec3 spread = side.scale((host.random().nextDouble() - 0.5) * 0.3);
                            debris.add(new Debris(Vec3.atCenterOf(at), chargeDir.scale(0.9).add(spread).add(0, 0.3, 0), state));
                        }
                    }
                }
            }
            if (any) {
                BossService.sound(level, front, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.6f, 0.6f);
            }
        }

        /** The debris flies on, falls, and hits whoever is in its way (10 %). */
        private void tickDebris(BossService.Host host, Mob beast) {
            if (debris.isEmpty()) {
                return;
            }
            ServerLevel level = host.world();
            float damage = BossService.abilityDamage(host, kind, 4.0);
            Iterator<Debris> it = debris.iterator();
            while (it.hasNext()) {
                Debris d = it.next();
                d.pos = d.pos.add(d.vel);
                d.vel = d.vel.add(0, -0.06, 0);
                d.life++;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, d.state), d.pos.x, d.pos.y, d.pos.z, 4, 0.2, 0.2, 0.2, 0.0);
                AABB box = new AABB(d.pos.x - 0.7, d.pos.y - 0.7, d.pos.z - 0.7, d.pos.x + 0.7, d.pos.y + 0.7, d.pos.z + 0.7);
                for (ServerPlayer p : host.defenders()) {
                    if (box.intersects(p.getBoundingBox()) && d.hit.add(p.getUUID())) {
                        BossService.strike(host, level, beast, p, damage, DEBRIS_SHARE);
                        p.push(d.vel.x * 0.8, 0.3, d.vel.z * 0.8);
                        p.hurtMarked = true;
                        debrisHits++;
                    }
                }
                BlockPos at = BlockPos.containing(d.pos);
                boolean solid = !level.getBlockState(at).getCollisionShape(level, at).isEmpty();
                if (d.life > 25 || (solid && d.life > 2)) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, d.state), d.pos.x, d.pos.y, d.pos.z, 12, 0.3, 0.3, 0.3, 0.1);
                    it.remove();
                }
            }
        }

        private static boolean breakable(ServerLevel level, BlockPos at, BossService.Host host) {
            BlockState state = level.getBlockState(at);
            return !state.isAir() && state.getFluidState().isEmpty() && !state.is(Blocks.BEDROCK) && !state.hasBlockEntity()
                    && state.getDestroySpeed(level, at) >= 0 && !ModContent.isCore(state) && ModContent.trapOf(state) == null
                    && !TempBlocks.isTemp(level, at)
                    && !BlockGuard.isProtected(Demolition.blockId(state), host.config());
        }

        /** Where the charge ends the ground caves in: a 3 × 3 crater, and a blow round about. */
        private void crater(BossService.Host host, Mob beast) {
            ServerLevel level = host.world();
            Vec3 c = beast.position();
            float damage = BossService.abilityDamage(host, kind, 6.0);
            for (ServerPlayer p : around(host, c, 3.5)) {
                BossService.strike(host, level, beast, p, damage, CRATER_SHARE);
                BossService.knock(p, c, 1.0, 0.6);
            }
            BlockState ground = level.getBlockState(beast.blockPosition().below());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground),
                    c.x, c.y + 0.3, c.z, 60, 1.5, 0.3, 1.5, 0.2);
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 3, 1.0, 0.1, 1.0, 0.0);
            BossService.sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0f, 0.5f);
            if (!NightDamage.recording(level)) {
                return;
            }
            BlockPos middle = beast.blockPosition().below();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int depth = dx == 0 && dz == 0 ? 2 : 1;
                    for (int dy = 0; dy < depth; dy++) {
                        BlockPos at = middle.offset(dx, -dy, dz);
                        if (breakable(level, at, host)) {
                            NightDamage.asAttacker(() -> level.destroyBlock(at, false));   // the night's: back after it (31.10.)
                        }
                    }
                }
            }
        }

        @Override
        void died(ServerLevel level) {
            debris.clear();
        }

        @Override
        void cleanUp(ServerLevel level) {
            debris.clear();
        }
    }
}
