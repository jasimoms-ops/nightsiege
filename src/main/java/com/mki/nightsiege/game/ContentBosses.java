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
     * The frost jarl: a giant of pack ice. His stomp goes over shields (8 % of the max health
     * through them); he raises walls of real ice for 15 s that cut a defender off from the
     * others; below 30 % an avalanche comes down on every defender.
     */
    static final class FrostJarl extends BossBrains.Brain {
        static final double STOMP_SHARE = 8.0;
        static final double AVALANCHE_SHARE = 12.0;
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

        FrostJarl(BossService service, BossKind kind) {
            super(service, kind);
        }

        @Override
        void spawned(BossService.Host host, Mob boss) {
            super.spawned(host, boss);
            nextStomp = spawnedAt + 100;
            nextWall = spawnedAt + 220;
            BossService.set(boss, Attributes.MOVEMENT_SPEED, 0.26);
        }

        @Override
        String phase(ServerLevel level) {
            return avalanche ? "nightsiege.boss.phase.avalanche" : null;
        }

        @Override
        void tick(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            if (now % 5 == 0) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, boss.getX(), boss.getY() + boss.getBbHeight() * 0.7, boss.getZ(),
                        2, boss.getBbWidth() * 0.4, boss.getBbHeight() * 0.3, boss.getBbWidth() * 0.4, 0.01);
            }
            if (melt > 0 && now >= melt) {
                meltWalls(level);
            }
            if (!avalanche && below(boss, 0.3)) {
                avalanche = true;
                nextAvalanche = now + 20;
                tell(host, "nightsiege.boss.frost_jarl.avalanche", ChatFormatting.AQUA);
                BossService.sound(level, boss.position(), SoundEvents.RAVAGER_ROAR, 2.0f, 0.4f);
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
            stuck.leap(host, boss, now, 6.0);
        }

        /** 8 % through the shield and a frost that slows, within 6.5 blocks. */
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

        /** Below 30 %: snow gathers over every defender for two seconds, then the avalanche falls. */
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
     */
    static final class EmberColossus extends BossBrains.Brain {
        static final double CRACK_SHARE = 3.0;
        static final double HALF_SHARE = 0.4;
        static final int CRACK_TICKS = 12 * 20;
        record Crack(Vec3 at, long until) {
        }

        final List<Crack> cracks = new ArrayList<>();
        boolean split;
        final Stuck stuckA = new Stuck();
        final Stuck stuckB = new Stuck();

        EmberColossus(BossService service, BossKind kind) {
            super(service, kind);
        }

        @Override
        void spawned(BossService.Host host, Mob boss) {
            super.spawned(host, boss);
            body(boss);
        }

        private static void body(Mob boss) {
            BossService.set(boss, Attributes.MOVEMENT_SPEED, 0.5);
            BossService.set(boss, Attributes.JUMP_STRENGTH, 0.62);
        }

        @Override
        String phase(ServerLevel level) {
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
            if (now % 10 == 0) {
                crack(boss, now);
                if (other != null) {
                    crack(other, now);
                }
            }
            stuckA.leap(host, boss, now, 5.0);
            if (other != null) {
                stuckB.leap(host, other, now, 5.0);
                if (now % 20 == 0 && (other.getTarget() == null || !other.getTarget().isAlive())) {
                    service.aim(host, other);
                }
            }
            tickCracks(host, boss, now);
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
        }
    }

    // ================================================================== Sturmhexe

    /**
     * The storm witch: she circles the base on her phantom, out of reach of swords. Chain
     * lightning jumps from defender to defender (6 % of the max health each), a storm sweeps
     * the defenders off the walls, and every 30 s she lands for 8 s – then she can be reached.
     */
    static final class StormWitch extends BossBrains.Brain {
        static final double BOLT_SHARE = 6.0;
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
        }

        @Override
        String phase(ServerLevel level) {
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
     * through the tank (12 % of the max health, no shield stops it); where it ends, the ground
     * caves in (a 3 × 3 crater). Broken blocks come back after the night. Below half it rages.
     */
    static final class RamTitan extends BossBrains.Brain {
        private static final Identifier RAGE = Identifier.fromNamespaceAndPath("nightsiege", "boss_ram_rage");
        static final double CHARGE_SHARE = 12.0;
        static final double CRATER_SHARE = 10.0;
        /** Most blocks one charge breaks. */
        static final int MAX_BREAK = 30;
        long nextCharge;
        long windupUntil;
        long chargeUntil;
        Vec3 chargeDir;
        int broken;
        final Set<UUID> hitThisCharge = new HashSet<>();
        boolean enraged;

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

        @Override
        void tick(BossService.Host host, Mob beast, long now) {
            ServerLevel level = host.world();
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
                    hitThisCharge.clear();
                    BossService.sound(level, beast.position(), SoundEvents.RAVAGER_STEP, 2.0f, 0.5f);
                }
                return;
            }
            if (now < nextCharge || !beast.onGround()) {
                return;
            }
            // The defender to run at: in reach, walls or not (it runs through them).
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
                nextCharge = now + 20;
                return;
            }
            Vec3 dir = target.position().subtract(beast.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0e-4) {
                return;
            }
            chargeDir = dir.normalize();
            float yaw = (float) (Math.atan2(chargeDir.z, chargeDir.x) * 180.0 / Math.PI) - 90f;
            beast.setYRot(yaw);
            beast.yBodyRot = yaw;
            beast.setYHeadRot(yaw);
            windupUntil = now + 30;
            nextCharge = now + (enraged ? 150 : 220);
            BossService.sound(level, beast.position(), SoundEvents.RAVAGER_ROAR, 2.0f, 0.7f);
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
            }
        }

        /** Breaks the blocks of the wall right in front of the titan (3 wide, up to its shoulders). */
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
                        level.destroyBlock(at, false);
                        broken++;
                        any = true;
                    }
                }
            }
            if (any) {
                BossService.sound(level, front, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.6f, 0.6f);
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
                            level.destroyBlock(at, false);
                        }
                    }
                }
            }
        }
    }
}
