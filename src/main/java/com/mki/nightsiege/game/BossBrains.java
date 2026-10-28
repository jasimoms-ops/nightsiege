package com.mki.nightsiege.game;

import com.mki.nightsiege.core.BossKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnifferEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The abilities of the four bosses (Simon, 25 October 2026). Each brain runs every tick of
 * the fight; the boss's own vanilla AI still walks and strikes, the brain adds the rest.
 * Cooldowns are in ticks; every ability is announced (particles, sound) before it lands.
 */
final class BossBrains {

    private BossBrains() {
    }

    static Brain create(BossKind kind, BossService service) {
        return switch (kind) {
            case GRAVE_KING -> new GraveKing(service, kind);
            case SPIDER_QUEEN -> new SpiderQueen(service, kind);
            case BONE_LICH -> new Lich(service, kind);
            case WARLORD -> new Warlord(service, kind);
        };
    }

    /** One boss's abilities. */
    abstract static class Brain {
        final BossService service;
        final BossKind kind;
        long spawnedAt;

        Brain(BossService service, BossKind kind) {
            this.service = service;
            this.kind = kind;
        }

        void spawned(BossService.Host host, Mob boss) {
            spawnedAt = host.world().getGameTime();
        }

        abstract void tick(BossService.Host host, Mob boss, long now);

        /** A translation key shown after the boss's name on the bar, or null. */
        String phase(ServerLevel level) {
            return null;
        }

        boolean allowDamage(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
            return true;
        }

        void hitPlayer(ServerPlayer player, Entity boss) {
        }

        double auraFactor(ServerLevel level, Entity attacker) {
            return 1.0;
        }

        void secondPhase(ServerLevel level, Mob rider) {
        }

        void partBroken(ServerLevel level, BossService.Part part, Mob hitbox) {
        }

        void partDue(BossService.Host host, BossService.Part part, long now) {
        }

        void cleanUp(ServerLevel level) {
        }

        static boolean below(Mob boss, double share) {
            return boss.getHealth() < boss.getMaxHealth() * share;
        }

        static void tell(BossService.Host host, String key, ChatFormatting color) {
            for (ServerPlayer p : host.watchers()) {
                p.sendSystemMessage(Component.translatable(key).withStyle(color));
            }
        }
    }

    // ================================================================== Grabkönig

    /**
     * The grave king: a giant zombie with a crown. Stomps (a shock wave after a wind-up),
     * raises zombies out of the ground around a defender, leaps over walls when he cannot
     * get through, and rages below half his health.
     */
    static final class GraveKing extends Brain {
        private static final Identifier RAGE = Identifier.fromNamespaceAndPath("nightsiege", "boss_rage");
        /** Share of the max health of the stomp at its centre (Simon, 28 October 2026). */
        static final double STOMP_SHARE = 30.0;
        long nextStomp;
        long windupUntil;
        long nextRaise;
        long nextLeap;
        boolean leaping;
        long leapStart;
        boolean enraged;
        Vec3 lastPos;
        int stuckTicks;

        GraveKing(BossService service, BossKind kind) {
            super(service, kind);
        }

        @Override
        void spawned(BossService.Host host, Mob boss) {
            super.spawned(host, boss);
            nextStomp = spawnedAt + 100;
            nextRaise = spawnedAt + 160;
            nextLeap = spawnedAt + 100;
            BossService.set(boss, Attributes.ATTACK_KNOCKBACK, 1.5);
            BossService.set(boss, Attributes.MOVEMENT_SPEED, 0.27);
        }

        @Override
        String phase(ServerLevel level) {
            return enraged ? "nightsiege.boss.phase.rage" : null;
        }

        @Override
        void tick(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            List<ServerPlayer> defenders = host.defenders();
            if (!enraged && below(boss, 0.5)) {
                enraged = true;
                AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null && !speed.hasModifier(RAGE)) {
                    speed.addTransientModifier(new AttributeModifier(RAGE, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                AttributeInstance attack = boss.getAttribute(Attributes.ATTACK_DAMAGE);
                if (attack != null && !attack.hasModifier(RAGE)) {
                    attack.addTransientModifier(new AttributeModifier(RAGE, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                BossService.sound(level, boss.position(), SoundEvents.RAVAGER_ROAR, 2.0f, 0.5f);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(),
                        12, 1.0, 0.5, 1.0, 0.0);
                tell(host, "nightsiege.boss.grave_king.rage", ChatFormatting.RED);
            }
            if (enraged && now % 6 == 0) {
                level.sendParticles(new DustParticleOptions(0xB01010, 1.6f), boss.getX(), boss.getY() + boss.getBbHeight() * 0.6,
                        boss.getZ(), 3, 0.6, 0.8, 0.6, 0.0);
            }
            // The leap: in the air until he lands, then the stomp without wind-up.
            if (leaping) {
                if (boss.onGround() && now > leapStart + 6) {
                    leaping = false;
                    stomp(host, boss, 6.0);
                } else if (now > leapStart + 80) {
                    leaping = false;
                }
                return;
            }
            if (windupUntil > 0) {
                boss.getNavigation().stop();
                if (now % 2 == 0) {
                    BossService.ring(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, boss.position(), 1.5 + (20 - (windupUntil - now)) * 0.25, 18);
                }
                if (now >= windupUntil) {
                    windupUntil = 0;
                    stomp(host, boss, 7.0);
                    nextStomp = now + (enraged ? 100 : 160);
                }
                return;
            }
            if (now >= nextStomp && near(defenders, boss, 7.0)) {
                windupUntil = now + 20;
                BossService.sound(level, boss.position(), SoundEvents.RAVAGER_ROAR, 1.6f, 0.45f);
                return;
            }
            if (now >= nextRaise && !defenders.isEmpty()) {
                raise(host, defenders.get(host.random().nextInt(defenders.size())));
                nextRaise = now + (enraged ? 280 : 400);
            }
            // Stuck at a wall (or far off with everyone inside): over the wall in one leap.
            if (now % 20 == 0) {
                Vec3 pos = boss.position();
                LivingEntity target = boss.getTarget();
                boolean far = target != null && target.distanceToSqr(boss) > 16.0;
                stuckTicks = lastPos != null && lastPos.distanceToSqr(pos) < 1.0 && far ? stuckTicks + 20 : 0;
                lastPos = pos;
                if (target instanceof ServerPlayer player && now >= nextLeap && boss.onGround()
                        && (stuckTicks >= 60 || (far && host.insideBase(player.getX(), player.getZ())
                        && !host.insideBase(boss.getX(), boss.getZ()) && player.distanceToSqr(boss) < 26.0 * 26.0))) {
                    leap(level, boss, player.position(), 7.0);
                    leaping = true;
                    leapStart = now;
                    nextLeap = now + (enraged ? 160 : 240);
                    stuckTicks = 0;
                }
            }
        }

        private void stomp(BossService.Host host, Mob boss, double radius) {
            ServerLevel level = host.world();
            Vec3 c = boss.position();
            float damage = BossService.abilityDamage(host, kind, 8.0);
            for (ServerPlayer p : host.defenders()) {
                double dx = p.getX() - c.x;
                double dz = p.getZ() - c.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius || Math.abs(p.getY() - c.y) > 4.0) {
                    continue;
                }
                double f = 1.0 - 0.5 * (d / radius);
                // 30 % of the max health at the centre on top (28 October 2026), less towards the edge.
                BossService.strike(host, level, boss, p, (float) (damage * f), STOMP_SHARE * f);
                BossService.knock(p, c, 0.4 + 1.2 * (1.0 - d / radius), 0.6);
            }
            BlockState ground = level.getBlockState(boss.blockPosition().below());
            if (ground.isAir()) {
                ground = Blocks.DIRT.defaultBlockState();
            }
            for (double r = 1.5; r <= radius; r += 1.5) {
                BossService.ring(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), c, r, (int) (r * 6));
            }
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 6, radius * 0.3, 0.1, radius * 0.3, 0.0);
            BossService.sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0f, 0.55f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 0.6f);
        }

        private void raise(BossService.Host host, ServerPlayer around) {
            ServerLevel level = host.world();
            int raised = 0;
            for (int i = 0; i < 12 && raised < 4; i++) {
                double a = host.random().nextDouble() * Math.PI * 2.0;
                double r = 3.0 + host.random().nextDouble() * 3.0;
                BlockPos spot = BossService.groundNear(level, around.getX() + Math.cos(a) * r, around.getY(), around.getZ() + Math.sin(a) * r);
                if (spot == null) {
                    continue;
                }
                Mob zombie = host.spawnAdd("minecraft:zombie", spot);
                if (zombie == null) {
                    break;
                }
                raised++;
                BlockState soil = level.getBlockState(spot.below());
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil.isAir() ? Blocks.DIRT.defaultBlockState() : soil),
                        spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5, 30, 0.4, 0.2, 0.4, 0.1);
                level.sendParticles(ParticleTypes.SOUL, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 6, 0.3, 0.4, 0.3, 0.02);
            }
            if (raised > 0) {
                BossService.sound(level, around.position(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 1.2f, 0.6f);
                BossService.sound(level, around.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.0f, 0.7f);
            }
        }

        private static boolean near(List<ServerPlayer> defenders, Mob boss, double radius) {
            for (ServerPlayer p : defenders) {
                double dx = p.getX() - boss.getX();
                double dz = p.getZ() - boss.getZ();
                if (dx * dx + dz * dz <= radius * radius && Math.abs(p.getY() - boss.getY()) < 4.0) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Throws a monster towards a point in a high arc (over walls), {@code apex} blocks above the higher end. */
    static void leap(ServerLevel level, Mob mob, Vec3 to, double apex) {
        Vec3 from = mob.position();
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double dy = to.y - from.y;
        double g = 0.08;
        double h = Math.max(0.0, dy) + apex;
        double vy = Math.sqrt(2.0 * g * h);
        double up = vy / g;
        double down = Math.sqrt(2.0 * Math.max(0.5, h - dy) / g);
        int ticks = (int) Math.ceil(up + down);
        // Air drag takes 9 % of the sideways speed every tick.
        double sum = (1.0 - Math.pow(0.91, ticks)) / 0.09;
        mob.getNavigation().stop();
        mob.setDeltaMovement(dx / sum, vy * 1.04, dz / sum);
        mob.hurtMarked = true;
        level.sendParticles(ParticleTypes.CLOUD, from.x, from.y + 0.2, from.z, 20, 0.8, 0.1, 0.8, 0.05);
        BossService.sound(level, from, SoundEvents.BREEZE_JUMP, 1.6f, 0.5f);
    }

    // ================================================================== Spinnenkönigin

    /**
     * The spider queen: climbs any wall like her kind; shoots webs that hold a defender in
     * place (a raised shield catches them), lays egg sacs that hatch into cave spiders unless
     * broken, poisons with her bite, and calls her brood below half.
     */
    static final class SpiderQueen extends Brain {
        private static final Identifier WEB = Identifier.fromNamespaceAndPath("nightsiege", "boss_web");
        long nextWeb;
        long webAt;
        UUID webTarget;
        long nextEggs;
        boolean brood;
        final Map<UUID, Long> rooted = new HashMap<>();
        /** How far each egg has cracked (0 to 2). */
        final Map<UUID, Integer> cracked = new HashMap<>();

        SpiderQueen(BossService service, BossKind kind) {
            super(service, kind);
        }

        @Override
        void spawned(BossService.Host host, Mob boss) {
            super.spawned(host, boss);
            nextWeb = spawnedAt + 80;
            nextEggs = spawnedAt + 200;
            BossService.set(boss, Attributes.MOVEMENT_SPEED, 0.33);
        }

        @Override
        String phase(ServerLevel level) {
            return brood ? "nightsiege.boss.phase.brood" : null;
        }

        @Override
        void tick(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            tickRoots(host, now);
            if (!brood && below(boss, 0.5)) {
                brood = true;
                int called = 0;
                for (int i = 0; i < 10 && called < 4; i++) {
                    double a = host.random().nextDouble() * Math.PI * 2.0;
                    BlockPos spot = BossService.groundNear(level, boss.getX() + Math.cos(a) * 3, boss.getY(), boss.getZ() + Math.sin(a) * 3);
                    if (spot != null && host.spawnAdd("minecraft:spider", spot) != null) {
                        called++;
                    }
                }
                BossService.sound(level, boss.position(), SoundEvents.SPIDER_AMBIENT, 2.0f, 0.4f);
                tell(host, "nightsiege.boss.spider_queen.brood", ChatFormatting.RED);
            }
            // The web: a hiss, then the shot along her line of sight.
            if (webTarget != null && now >= webAt) {
                ServerPlayer target = level.getServer().getPlayerList().getPlayer(webTarget);
                webTarget = null;
                if (target != null && target.isAlive() && boss.hasLineOfSight(target)) {
                    shoot(level, boss, target, now);
                }
                nextWeb = now + (brood ? 80 : 140);
            } else if (webTarget == null && now >= nextWeb) {
                ServerPlayer target = webTarget(host, boss);
                if (target != null) {
                    webTarget = target.getUUID();
                    webAt = now + 10;
                    BossService.sound(level, boss.position(), SoundEvents.SPIDER_AMBIENT, 1.6f, 0.5f);
                } else {
                    nextWeb = now + 20;
                }
            }
            if (now % 5 == 0) {
                crack(level, now);
            }
            if (now >= nextEggs) {
                layEggs(host, boss, now);
                nextEggs = now + 300;
            }
        }

        private ServerPlayer webTarget(BossService.Host host, Mob boss) {
            ServerPlayer best = null;
            double bestDistance = 22.0 * 22.0;
            for (ServerPlayer p : host.defenders()) {
                double d = p.distanceToSqr(boss);
                if (d < bestDistance && !rooted.containsKey(p.getUUID()) && boss.hasLineOfSight(p)) {
                    bestDistance = d;
                    best = p;
                }
            }
            return best;
        }

        private void shoot(ServerLevel level, Mob boss, ServerPlayer target, long now) {
            Vec3 from = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
            Vec3 to = target.position().add(0, 1.0, 0);
            BossService.line(level, ParticleTypes.ITEM_COBWEB, from, to, (int) Math.max(6, from.distanceTo(to) * 2));
            if (target.isBlocking()) {
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
                return;
            }
            rooted.put(target.getUUID(), now + 50);
            root(target, true);
            BossService.sound(level, target.position(), SoundEvents.COBWEB_PLACE, 1.5f, 0.6f);
            target.sendSystemMessage(Component.translatable("nightsiege.boss.spider_queen.webbed").withStyle(ChatFormatting.GRAY), true);
        }

        private static void root(ServerPlayer player, boolean on) {
            for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                instance.removeModifier(WEB);
                if (on) {
                    instance.addTransientModifier(new AttributeModifier(WEB, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
        }

        private void tickRoots(BossService.Host host, long now) {
            ServerLevel level = host.world();
            Iterator<Map.Entry<UUID, Long>> it = rooted.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
                if (p == null) {
                    it.remove();
                    continue;
                }
                if (now >= e.getValue() || !p.isAlive() || p.isSpectator() || Drones.isDrone(p)) {
                    root(p, false);
                    it.remove();
                } else if (now % 4 == 0) {
                    level.sendParticles(ParticleTypes.ITEM_COBWEB, p.getX(), p.getY() + 0.8, p.getZ(), 3, 0.35, 0.5, 0.35, 0.0);
                }
            }
        }

        private void layEggs(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            if (service.partsOf("egg").size() >= 4) {
                return;
            }
            int laid = 0;
            for (int i = 0; i < 8 && laid < 2; i++) {
                double a = host.random().nextDouble() * Math.PI * 2.0;
                BlockPos spot = BossService.groundNear(level, boss.getX() + Math.cos(a) * 2.5, boss.getY(), boss.getZ() + Math.sin(a) * 2.5);
                if (spot == null) {
                    continue;
                }
                double health = 16.0 * com.mki.nightsiege.core.SiegeMath.healthMultiplier(host.level(), host.config());
                Vec3 at = new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                if (service.addPart(level, at, "egg", Blocks.SNIFFER_EGG.defaultBlockState(), 1.2f, health, now, now + 160) != null) {
                    laid++;
                    level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y + 0.5, at.z, 10, 0.4, 0.3, 0.4, 0.02);
                }
            }
            if (laid > 0) {
                BossService.sound(level, boss.position(), SoundEvents.SLIME_BLOCK_PLACE, 1.4f, 0.5f);
                tell(host, "nightsiege.boss.spider_queen.eggs", ChatFormatting.YELLOW);
            }
        }

        @Override
        void partDue(BossService.Host host, BossService.Part part, long now) {
            ServerLevel level = host.world();
            Entity hitbox = level.getEntity(part.hitbox);
            if (hitbox == null) {
                service.removePart(level, part);
                return;
            }
            Vec3 at = hitbox.position();
            int hatched = 0;
            for (int i = 0; i < 3; i++) {
                if (host.spawnAdd("minecraft:cave_spider", BlockPos.containing(at)) != null) {
                    hatched++;
                }
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNIFFER_EGG.defaultBlockState()),
                    at.x, at.y + 0.5, at.z, 25, 0.4, 0.4, 0.4, 0.1);
            BossService.sound(level, at, SoundEvents.SNIFFER_EGG_HATCH, 1.4f, 0.8f);
            service.removePart(level, part);
        }

        @Override
        void cleanUp(ServerLevel level) {
            for (UUID id : rooted.keySet()) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) {
                    root(p, false);
                }
            }
            rooted.clear();
        }

        /** The egg cracks as it nears hatching. */
        private void crack(ServerLevel level, long now) {
            for (BossService.Part part : service.partsOf("egg")) {
                long left = part.due - now;
                int hatch = left < 55 ? 2 : left < 110 ? 1 : 0;
                Integer before = cracked.put(part.hitbox, hatch);
                if (before == null || before != hatch) {
                    BossService.showBlock(level, part, Blocks.SNIFFER_EGG.defaultBlockState().setValue(SnifferEggBlock.HATCH, hatch));
                    Entity e = level.getEntity(part.hitbox);
                    if (e != null && before != null) {
                        BossService.sound(level, e.position(), SoundEvents.SNIFFER_EGG_CRACK, 1.0f, 0.9f);
                    }
                }
            }
        }

        @Override
        void partBroken(ServerLevel level, BossService.Part part, Mob hitbox) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNIFFER_EGG.defaultBlockState()),
                    hitbox.getX(), hitbox.getY() + 0.5, hitbox.getZ(), 30, 0.4, 0.4, 0.4, 0.1);
            level.sendParticles(ParticleTypes.ITEM_COBWEB, hitbox.getX(), hitbox.getY() + 0.5, hitbox.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
            BossService.sound(level, hitbox.position(), SoundEvents.TURTLE_EGG_BREAK, 1.2f, 0.7f);
            service.removePart(level, part);
        }

        @Override
        void hitPlayer(ServerPlayer player, Entity boss) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), boss);
        }
    }

    // ================================================================== Knochenlich

    /**
     * The bone lich: hovers round the base outside the walls and shoots; calls down arrow
     * rain on a defender (a warning ring first; a roof keeps it off); a soul shield makes him
     * immune until the three soul crystals within the walls are broken (at the start and once
     * more below half); blinks away from fighters who come close; calls skeleton guards.
     */
    static final class Lich extends Brain {
        /** Base damage of the soul arrow before level and boss factors (about 10 on level 10, Simon 28 October 2026). */
        static final double SOUL_ARROW_DAMAGE = 4.0;
        /** The rain falls over three seconds, one arrow every third tick (it came in half a second). */
        static final int RAIN_TICKS = 60;
        /**
         * With his shield broken (the soul lanterns smashed) he sinks down, worn out, for fifteen
         * seconds: the fighters' chance (was seven; Simon 28.10.: "stunned longer once the lanterns are broken").
         */
        static final int EXHAUSTED_TICKS = 300;
        /** He blinks away after three seconds with a fighter close (was 1.5 s), then waits twelve (was 7). */
        static final int BLINK_AFTER = 60;
        static final int BLINK_PAUSE = 240;
        double angle;
        long exhaustedUntil;
        /** His soul arrows in flight (they fly straight and go after three seconds). */
        final Map<UUID, Long> soulArrows = new HashMap<>();
        long nextShot;
        long nextRain;
        Vec3 rainAt;
        long rainFall;
        long nextBlink;
        int closeTicks;
        long nextGuards;
        boolean shield;
        boolean secondShield;

        Lich(BossService service, BossKind kind) {
            super(service, kind);
        }

        @Override
        void spawned(BossService.Host host, Mob boss) {
            super.spawned(host, boss);
            BlockPos a = host.anchor();
            angle = Math.atan2(boss.getZ() - (a.getZ() + 0.5), boss.getX() - (a.getX() + 0.5));
            boss.setNoGravity(true);
            // The bow first: putting it in his hand gives the skeleton its own bow goal back.
            boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            boss.getGoalSelector().removeAllGoals(g -> true);
            nextShot = spawnedAt + 40;
            nextRain = spawnedAt + 120;
            nextGuards = spawnedAt + 300;
            raiseShield(host, spawnedAt);
        }

        @Override
        String phase(ServerLevel level) {
            return shield ? "nightsiege.boss.phase.shield" : null;
        }

        @Override
        void tick(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            if (!secondShield && below(boss, 0.5)) {
                secondShield = true;
                exhaustedUntil = 0;   // the second shield wakes him
                raiseShield(host, now);
            }
            tickSoulArrows(level, now);
            if (shield) {
                if (service.partsOf("crystal").isEmpty()) {
                    shield = false;
                    BossService.sound(level, boss.position(), SoundEvents.TOTEM_USE, 1.4f, 0.6f);
                    level.sendParticles(ParticleTypes.SOUL, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(),
                            40, 1.0, 1.5, 1.0, 0.1);
                    tell(host, "nightsiege.boss.bone_lich.shield_down", ChatFormatting.GREEN);
                    exhaust(host, boss, now);
                } else if (now % 5 == 0) {
                    Vec3 c = boss.position().add(0, boss.getBbHeight() * 0.5, 0);
                    BossService.ring(level, ParticleTypes.SOUL_FIRE_FLAME, c.add(0, -0.15, 0), 1.6, 14);
                    if (now % 10 == 0) {
                        for (BossService.Part part : service.partsOf("crystal")) {
                            Entity e = level.getEntity(part.hitbox);
                            if (e != null) {
                                BossService.line(level, ParticleTypes.SOUL, e.position().add(0, 1.2, 0), c, 14);
                            }
                        }
                    }
                }
            }
            if (now < exhaustedUntil) {
                sink(host, boss, now);
                return;
            }
            if (exhaustedUntil > 0) {
                // Up again: back to his round outside the walls.
                exhaustedUntil = 0;
                nextShot = now + 20;
                nextBlink = Math.max(nextBlink, now + 60);
                boss.removeEffect(MobEffects.GLOWING);
                BossService.sound(level, boss.position(), SoundEvents.WITHER_SKELETON_AMBIENT, 1.6f, 0.6f);
                tell(host, "nightsiege.boss.bone_lich.rises", ChatFormatting.RED);
            }
            hover(host, boss, now);
            List<ServerPlayer> defenders = host.defenders();
            if (now >= nextShot) {
                ServerPlayer target = sighted(defenders, boss, 32.0);
                if (target != null) {
                    face(boss, target.position());
                    soulArrow(host, boss, target, now);
                }
                nextShot = now + (secondShield ? 30 : 40);
            }
            if (rainAt != null) {
                if (now < rainFall) {
                    if (now % 4 == 0) {
                        BossService.ring(level, ParticleTypes.SOUL_FIRE_FLAME, rainAt, 3.5, 28);
                        BossService.ring(level, new DustParticleOptions(0x3FD0FF, 1.2f), rainAt, 2.0, 12);
                    }
                } else if (now < rainFall + RAIN_TICKS) {
                    // Spread over three seconds, so more than one arrow can find its mark (28 October 2026).
                    if ((now - rainFall) % 3 == 0) {
                        rainArrow(host, boss);
                    }
                    if (now % 6 == 0) {
                        BossService.ring(level, ParticleTypes.SOUL_FIRE_FLAME, rainAt, 3.5, 20);
                    }
                } else {
                    rainAt = null;
                }
            } else if (now >= nextRain && !defenders.isEmpty()) {
                ServerPlayer target = defenders.get(host.random().nextInt(defenders.size()));
                rainAt = new Vec3(target.getX(), target.getY(), target.getZ());
                rainFall = now + 40;
                nextRain = now + (secondShield ? 120 : 180);
                BossService.sound(level, rainAt, SoundEvents.EVOKER_CAST_SPELL, 1.4f, 0.6f);
            }
            if (now >= nextGuards) {
                int called = 0;
                for (int i = 0; i < 8 && called < 2; i++) {
                    double a = host.random().nextDouble() * Math.PI * 2.0;
                    BlockPos spot = BossService.groundNear(level, boss.getX() + Math.cos(a) * 3,
                            BossService.surface(level, boss.getX(), boss.getZ()), boss.getZ() + Math.sin(a) * 3);
                    if (spot != null && host.spawnAdd("minecraft:skeleton", spot) != null) {
                        called++;
                        level.sendParticles(ParticleTypes.SOUL, spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5, 10, 0.3, 0.6, 0.3, 0.02);
                    }
                }
                nextGuards = now + 400;
            }
            // Fighters close by: he blinks away.
            boolean close = false;
            for (ServerPlayer p : defenders) {
                if (p.distanceToSqr(boss) < 5.0 * 5.0) {
                    close = true;
                    break;
                }
            }
            closeTicks = close ? closeTicks + 1 : 0;
            if (closeTicks >= BLINK_AFTER && now >= nextBlink) {
                Vec3 before = boss.position();
                angle += Math.PI * (0.5 + host.random().nextDouble() * 0.5) * (host.random().nextBoolean() ? 1 : -1);
                Vec3 spot = hoverPoint(host, boss);
                boss.teleportTo(spot.x, spot.y, spot.z);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, before.x, before.y + 2, before.z, 40, 0.6, 1.5, 0.6, 0.1);
                level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 2, spot.z, 40, 0.6, 1.5, 0.6, 0.1);
                BossService.sound(level, before, SoundEvents.ENDERMAN_TELEPORT, 1.4f, 0.6f);
                nextBlink = now + BLINK_PAUSE;
                closeTicks = 0;
            }
        }

        /** Floats round the base, a little above the ground, outside the walls. */
        private void hover(BossService.Host host, Mob boss, long now) {
            angle += 0.004;
            Vec3 goal = hoverPoint(host, boss);
            Vec3 pos = boss.position();
            Vec3 step = goal.subtract(pos);
            double len = step.length();
            Vec3 move = len < 1.0e-3 ? Vec3.ZERO : step.scale(Math.min(0.16, len) / len);
            boss.setDeltaMovement(move);
            boss.hurtMarked = true;
            LivingEntity target = boss.getTarget();
            if (target == null || !target.isAlive() || now % 40 == 0) {
                ServerPlayer near = BossService.nearest(host.defenders(), pos, 40.0);
                if (near != null) {
                    boss.setTarget(near);
                    target = near;
                }
            }
            if (target != null) {
                face(boss, target.position());
            }
            if (now % 3 == 0) {
                host.world().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y - 0.2, pos.z, 2, 0.4, 0.1, 0.4, 0.01);
            }
        }

        private Vec3 hoverPoint(BossService.Host host, Mob boss) {
            BlockPos a = host.anchor();
            double cx = a.getX() + 0.5;
            double cz = a.getZ() + 0.5;
            double x0 = cx + Math.cos(angle) * 20;
            double z0 = cz + Math.sin(angle) * 20;
            double edge = host.baseEdge(x0, z0);
            double radius = Math.max(9.0, Math.min(30.0, (edge > 0 ? edge : 8.0) + 5.0));
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            double y = Math.max(BossService.surface(host.world(), x, z), a.getY() - 2) + 1.5;
            return new Vec3(x, y, z);
        }

        private static void face(Mob boss, Vec3 at) {
            double dx = at.x - boss.getX();
            double dz = at.z - boss.getZ();
            float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
            boss.setYRot(yaw);
            boss.setYHeadRot(yaw);
            boss.yBodyRot = yaw;
        }

        private static ServerPlayer sighted(List<ServerPlayer> defenders, Mob boss, double range) {
            ServerPlayer best = null;
            double bestDistance = range * range;
            for (ServerPlayer p : defenders) {
                double d = p.distanceToSqr(boss);
                if (d < bestDistance && boss.hasLineOfSight(p)) {
                    bestDistance = d;
                    best = p;
                }
            }
            return best;
        }

        private void rainArrow(BossService.Host host, Mob boss) {
            ServerLevel level = host.world();
            double a = host.random().nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(host.random().nextDouble()) * 3.5;
            double x = rainAt.x + Math.cos(a) * r;
            double z = rainAt.z + Math.sin(a) * r;
            double y = rainAt.y + 14.0;
            Arrow arrow = new Arrow(level, x, y, z, new ItemStack(Items.ARROW), null);
            arrow.setOwner(boss);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setBaseDamage(BossService.abilityDamage(host, kind, 1.6));
            arrow.setCritArrow(true);
            arrow.addTag(BossService.TAG_RAIN_ARROW);   // 6 % of the max health on top
            arrow.setDeltaMovement((host.random().nextDouble() - 0.5) * 0.1, -2.2, (host.random().nextDouble() - 0.5) * 0.1);
            level.addFreshEntity(arrow);
            if (host.random().nextInt(3) == 0) {
                BossService.sound(level, new Vec3(x, rainAt.y + 4, z), SoundEvents.SKELETON_SHOOT, 0.8f, 0.6f);
            }
        }

        /**
         * The soul arrow (instead of the plain skeleton shot, which grew with nothing): a straight
         * bolt with the boss's damage, 10 % of the max health on top and a short slowness.
         */
        private void soulArrow(BossService.Host host, Mob boss, ServerPlayer target, long now) {
            ServerLevel level = host.world();
            Vec3 from = boss.position().add(0, boss.getBbHeight() * 0.75, 0);
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0).add(target.getDeltaMovement().scale(5));
            Vec3 dir = aim.subtract(from);
            if (dir.lengthSqr() < 1.0e-4) {
                return;
            }
            Arrow arrow = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            arrow.setOwner(boss);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setNoGravity(true);
            float speed = 2.4f;
            arrow.setBaseDamage(BossService.abilityDamage(host, kind, SOUL_ARROW_DAMAGE) / speed);
            arrow.addTag(BossService.TAG_SOUL_ARROW);
            arrow.shoot(dir.x, dir.y, dir.z, speed, 1.0f);
            if (level.addFreshEntity(arrow)) {
                soulArrows.put(arrow.getUUID(), now);
            }
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 8, 0.3, 0.3, 0.3, 0.02);
            BossService.sound(level, from, SoundEvents.SKELETON_SHOOT, 1.4f, 0.5f);
            BossService.sound(level, from, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 1.2f);
        }

        /** Soul arrows trail soul fire and go after three seconds (they fly without falling). */
        private void tickSoulArrows(ServerLevel level, long now) {
            Iterator<Map.Entry<UUID, Long>> it = soulArrows.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                Entity arrow = level.getEntity(e.getKey());
                if (arrow == null || !arrow.isAlive()) {
                    it.remove();
                } else if (now - e.getValue() > 60) {
                    arrow.discard();
                    it.remove();
                } else {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
        }

        /** The shield broke: he sinks down worn out, the fighters' chance to strike (28 October 2026). */
        private void exhaust(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            exhaustedUntil = now + EXHAUSTED_TICKS;
            rainAt = null;
            closeTicks = 0;
            boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, EXHAUSTED_TICKS, 0, false, false));
            BossService.sound(level, boss.position(), SoundEvents.SKELETON_HURT, 1.8f, 0.5f);
            tell(host, "nightsiege.boss.bone_lich.exhausted", ChatFormatting.GOLD);
        }

        /** Worn out he sinks to the ground where he is and does nothing. */
        private void sink(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            double ground = BossService.surface(level, boss.getX(), boss.getZ());
            double dy = ground - boss.getY();
            boss.setDeltaMovement(0, Math.max(-0.25, Math.min(0.05, dy * 0.3)), 0);
            boss.hurtMarked = true;
            boss.getNavigation().stop();
            if (now % 4 == 0) {
                level.sendParticles(ParticleTypes.SOUL, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 3, 0.5, 0.6, 0.5, 0.01);
                level.sendParticles(ParticleTypes.SMOKE, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
            }
        }

        /** For the tests: whether he lies worn out now. */
        boolean exhausted(long now) {
            return now < exhaustedUntil;
        }

        @Override
        void cleanUp(ServerLevel level) {
            for (UUID id : soulArrows.keySet()) {
                Entity arrow = level.getEntity(id);
                if (arrow != null) {
                    arrow.discard();
                }
            }
            soulArrows.clear();
        }

        /** Three soul crystals within the walls hold the shield up. */
        private void raiseShield(BossService.Host host, long now) {
            ServerLevel level = host.world();
            BlockPos a = host.anchor();
            double cx = a.getX() + 0.5;
            double cz = a.getZ() + 0.5;
            double start = host.random().nextDouble() * Math.PI * 2.0;
            int placed = 0;
            for (int k = 0; k < 3; k++) {
                double ang = start + k * Math.PI * 2.0 / 3.0;
                double far = cx + Math.cos(ang) * 20;
                double farZ = cz + Math.sin(ang) * 20;
                double edge = host.baseEdge(far, farZ);
                double radius = edge > 0 ? Math.max(3.0, edge - 3.0) : 7.0;
                BlockPos spot = BossService.groundNear(level, cx + Math.cos(ang) * radius, a.getY() + 1, cz + Math.sin(ang) * radius);
                if (spot == null) {
                    spot = BossService.groundNear(level, cx + Math.cos(ang) * 3, a.getY() + 1, cz + Math.sin(ang) * 3);
                }
                if (spot == null) {
                    continue;
                }
                double health = 30.0 * com.mki.nightsiege.core.SiegeMath.healthMultiplier(host.level(), host.config())
                        * (1.0 + 0.5 * Math.max(0, host.defenders().size() - 1));
                Vec3 at = new Vec3(spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() + 0.5);
                if (service.addPart(level, at, "crystal", Blocks.SOUL_LANTERN.defaultBlockState(), 1.6f, health, now, 0) != null) {
                    placed++;
                    level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 20, 0.4, 0.6, 0.4, 0.05);
                }
            }
            if (placed > 0) {
                shield = true;
                for (ServerPlayer p : host.watchers()) {
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SOUL_ESCAPE, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.5f);
                }
                tell(host, "nightsiege.boss.bone_lich.shield", ChatFormatting.AQUA);
            }
        }

        @Override
        boolean allowDamage(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
            if (shield && victim.getUUID().equals(service.boss(level) == null ? null : service.boss(level).getUUID())) {
                if (level.getGameTime() % 4 == 0) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                            victim.getZ(), 8, 0.8, 1.0, 0.8, 0.02);
                    level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK,
                            net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 0.5f);
                }
                if (source.getEntity() instanceof ServerPlayer p) {
                    p.sendSystemMessage(Component.translatable("nightsiege.boss.bone_lich.immune").withStyle(ChatFormatting.AQUA), true);
                }
                return false;
            }
            return true;
        }

        @Override
        void partBroken(ServerLevel level, BossService.Part part, Mob hitbox) {
            level.sendParticles(ParticleTypes.SOUL, hitbox.getX(), hitbox.getY() + 1, hitbox.getZ(), 30, 0.5, 0.6, 0.5, 0.1);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SOUL_LANTERN.defaultBlockState()),
                    hitbox.getX(), hitbox.getY() + 1, hitbox.getZ(), 20, 0.4, 0.4, 0.4, 0.1);
            BossService.sound(level, hitbox.position(), SoundEvents.LANTERN_BREAK, 1.5f, 0.6f);
            BossService.sound(level, hitbox.position(), SoundEvents.GLASS_BREAK, 1.2f, 0.7f);
            service.removePart(level, part);
        }
    }

    // ================================================================== Illager-Kriegsherr

    /**
     * The warlord on his ravager. First the beast takes every hit (the warlord sits covered):
     * it charges in a line after a roar and is dazed when it runs into a wall; his war banner
     * makes the illagers near him hit harder, and squads of pillagers come. When the ravager
     * falls he fights on foot: leaps at the defenders, a war cry drives the illagers on.
     */
    static final class Warlord extends Brain {
        private static final Identifier FOOT = Identifier.fromNamespaceAndPath("nightsiege", "boss_on_foot");
        /** Shares of the max health of the charge and of the leap's landing (Simon, 28 October 2026). */
        static final double CHARGE_SHARE = 35.0;
        static final double LEAP_SHARE = 25.0;
        boolean onFoot;
        long nextCharge;
        long windupUntil;
        long chargeUntil;
        Vec3 chargeDir;
        final Set<UUID> hitThisCharge = new HashSet<>();
        long nextSquad;
        long nextCry;
        long nextLeap;
        boolean leaping;
        long leapStart;
        Vec3 lastPos;
        int stuckTicks;
        boolean redirecting;

        Warlord(BossService service, BossKind kind) {
            super(service, kind);
        }

        @Override
        void spawned(BossService.Host host, Mob boss) {
            super.spawned(host, boss);
            nextCharge = spawnedAt + 80;
            nextSquad = spawnedAt + 200;
            ServerLevel level = host.world();
            Mob rider = service.rider(level);
            if (rider != null) {
                // His war banner is part of his look (on his back); the helmet stays free.
                rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                rider.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
        }

        @Override
        String phase(ServerLevel level) {
            return onFoot ? "nightsiege.boss.phase.on_foot" : "nightsiege.boss.phase.mounted";
        }

        @Override
        void secondPhase(ServerLevel level, Mob rider) {
            onFoot = true;
            rider.stopRiding();
            AttributeInstance speed = rider.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(FOOT)) {
                speed.addTransientModifier(new AttributeModifier(FOOT, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            long now = level.getGameTime();
            nextCry = now + 60;
            nextSquad = now + 200;
            nextLeap = now + 60;
            BossService.sound(level, rider.position(), SoundEvents.VINDICATOR_CELEBRATE, 2.0f, 0.6f);
            BossService.sound(level, rider.position(), SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 1.3f);
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                if (p.level() == level && p.distanceToSqr(rider) < 96 * 96) {
                    p.sendSystemMessage(Component.translatable("nightsiege.boss.warlord.on_foot").withStyle(ChatFormatting.RED));
                }
            }
        }

        @Override
        void tick(BossService.Host host, Mob boss, long now) {
            ServerLevel level = host.world();
            if (now >= nextSquad) {
                squad(host, boss);
                nextSquad = now + (onFoot ? 400 : 500);
            }
            if (onFoot) {
                tickOnFoot(host, boss, now);
            } else {
                tickCharge(host, boss, now);
            }
            Mob rider = service.rider(level);
            if (rider != null && now % 10 == 0) {
                level.sendParticles(new DustParticleOptions(0x6A1B9A, 1.4f), rider.getX(), rider.getY() + rider.getBbHeight() + 0.4,
                        rider.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            }
        }

        private void tickCharge(BossService.Host host, Mob beast, long now) {
            ServerLevel level = host.world();
            if (chargeUntil > 0) {
                beast.getNavigation().stop();
                Vec3 v = beast.getDeltaMovement();
                beast.setDeltaMovement(chargeDir.x * 0.75, v.y, chargeDir.z * 0.75);
                beast.hurtMarked = true;
                if (now % 2 == 0) {
                    level.sendParticles(ParticleTypes.CLOUD, beast.getX(), beast.getY() + 0.3, beast.getZ(), 3, 0.6, 0.1, 0.6, 0.02);
                }
                float damage = BossService.abilityDamage(host, kind, 10.0);
                AABB box = beast.getBoundingBox().inflate(1.0);
                for (ServerPlayer p : host.defenders()) {
                    if (box.intersects(p.getBoundingBox()) && hitThisCharge.add(p.getUUID())) {
                        BossService.strike(host, level, beast, p, damage, CHARGE_SHARE);
                        p.push(chargeDir.x * 1.8, 0.6, chargeDir.z * 1.8);
                        p.hurtMarked = true;
                        BossService.sound(level, p.position(), SoundEvents.IRON_GOLEM_ATTACK, 1.4f, 0.6f);
                    }
                }
                if (beast.horizontalCollision && now > chargeUntil - 26) {
                    // Into the wall: dazed for two seconds.
                    chargeUntil = 0;
                    beast.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 5, false, true));
                    level.sendParticles(ParticleTypes.CRIT, beast.getX(), beast.getY() + beast.getBbHeight(), beast.getZ(), 20, 0.8, 0.4, 0.8, 0.2);
                    BossService.sound(level, beast.position(), SoundEvents.RAVAGER_STUNNED, 1.6f, 0.7f);
                    BossService.sound(level, beast.position(), SoundEvents.ANVIL_LAND, 0.8f, 0.5f);
                } else if (now >= chargeUntil) {
                    chargeUntil = 0;
                }
                return;
            }
            if (windupUntil > 0) {
                beast.getNavigation().stop();
                beast.setDeltaMovement(0, beast.getDeltaMovement().y, 0);
                if (now % 3 == 0) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, beast.getX(), beast.getY() + 0.2, beast.getZ(), 4, 0.8, 0.1, 0.8, 0.02);
                }
                if (now >= windupUntil) {
                    windupUntil = 0;
                    chargeUntil = now + 30;
                    hitThisCharge.clear();
                    BossService.sound(level, beast.position(), SoundEvents.RAVAGER_STEP, 2.0f, 0.6f);
                }
                return;
            }
            if (now < nextCharge || !beast.onGround()) {
                return;
            }
            ServerPlayer target = null;
            double best = 22.0 * 22.0;
            for (ServerPlayer p : host.defenders()) {
                double d = p.distanceToSqr(beast);
                if (d > 6.0 * 6.0 && d < best && beast.hasLineOfSight(p)) {
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
            windupUntil = now + 25;
            nextCharge = now + 200;
            BossService.sound(level, beast.position(), SoundEvents.RAVAGER_ROAR, 2.0f, 0.8f);
        }

        private void tickOnFoot(BossService.Host host, Mob warlord, long now) {
            ServerLevel level = host.world();
            if (leaping) {
                if (warlord.onGround() && now > leapStart + 6) {
                    leaping = false;
                    Vec3 c = warlord.position();
                    float damage = BossService.abilityDamage(host, kind, 6.0);
                    for (ServerPlayer p : host.defenders()) {
                        if (p.distanceToSqr(c) < 4.0 * 4.0) {
                            BossService.strike(host, level, warlord, p, damage, LEAP_SHARE);
                            BossService.knock(p, c, 1.0, 0.5);
                        }
                    }
                    level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 3, 1.0, 0.1, 1.0, 0.0);
                    BossService.sound(level, c, SoundEvents.MACE_SMASH_GROUND, 1.6f, 0.7f);
                } else if (now > leapStart + 80) {
                    leaping = false;
                }
                return;
            }
            if (now >= nextCry) {
                int driven = 0;
                for (Mob m : level.getEntitiesOfClass(Mob.class, warlord.getBoundingBox().inflate(16.0),
                        m -> m instanceof AbstractIllager && m.isAlive() && m != warlord)) {
                    m.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
                    m.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
                    driven++;
                }
                level.playSound(null, warlord.getX(), warlord.getY(), warlord.getZ(), SoundEvents.RAID_HORN,
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.2f, 1.0f);
                BossService.sound(level, warlord.position(), SoundEvents.EVOKER_CAST_SPELL, 1.4f, 0.7f);
                nextCry = now + 300;
            }
            if (now % 20 == 0) {
                Vec3 pos = warlord.position();
                LivingEntity target = warlord.getTarget();
                boolean far = target != null && target.distanceToSqr(warlord) > 16.0;
                stuckTicks = lastPos != null && lastPos.distanceToSqr(pos) < 1.0 && far ? stuckTicks + 20 : 0;
                lastPos = pos;
                if (target instanceof ServerPlayer player && now >= nextLeap && warlord.onGround()
                        && (stuckTicks >= 60 || (far && host.insideBase(player.getX(), player.getZ())
                        && !host.insideBase(warlord.getX(), warlord.getZ()) && player.distanceToSqr(warlord) < 24.0 * 24.0))) {
                    leap(level, warlord, player.position(), 6.0);
                    leaping = true;
                    leapStart = now;
                    nextLeap = now + 200;
                    stuckTicks = 0;
                }
            }
        }

        private void squad(BossService.Host host, Mob around) {
            ServerLevel level = host.world();
            String[] squad = {"minecraft:pillager", "minecraft:pillager", "minecraft:pillager", "minecraft:vindicator"};
            int called = 0;
            for (String id : squad) {
                for (int i = 0; i < 6; i++) {
                    double a = host.random().nextDouble() * Math.PI * 2.0;
                    BlockPos spot = BossService.groundNear(level, around.getX() + Math.cos(a) * 4, around.getY(), around.getZ() + Math.sin(a) * 4);
                    if (spot != null && !host.insideBase(spot.getX() + 0.5, spot.getZ() + 0.5)) {
                        if (host.spawnAdd(id, spot) != null) {
                            called++;
                        }
                        break;
                    }
                }
            }
            if (called > 0) {
                level.playSound(null, around.getX(), around.getY(), around.getZ(), SoundEvents.RAID_HORN,
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.6f, 1.0f);
                tell(host, "nightsiege.boss.warlord.squad", ChatFormatting.GOLD);
            }
        }

        @Override
        boolean allowDamage(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
            if (onFoot || redirecting) {
                return true;
            }
            Mob rider = service.rider(level);
            Mob beast = service.boss(level);
            if (rider != null && beast != null && victim == rider) {
                // In the saddle the warlord is covered: the beast takes the hit.
                redirecting = true;
                try {
                    beast.invulnerableTime = 0;
                    beast.hurtServer(level, source, amount);
                } finally {
                    redirecting = false;
                }
                return false;
            }
            return true;
        }

        @Override
        double auraFactor(ServerLevel level, Entity attacker) {
            Mob rider = service.rider(level);
            if (rider == null || attacker == rider || !(attacker instanceof AbstractIllager)) {
                return 1.0;
            }
            return attacker.distanceToSqr(rider) <= 14.0 * 14.0 ? 1.3 : 1.0;
        }
    }
}
