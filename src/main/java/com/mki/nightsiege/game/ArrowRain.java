package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The archers' arrow rain (Simon 30.10., 20:11: "die Bogenschützen sind weiterhin sehr schlecht …
 * vielleicht brauchen die alle so eine AoE-Schaden-Fähigkeit, sowas wie das Knochenlich-Pfeilregen";
 * 20:12: on F, "je Unterklasse eigen"; 20:17: "zwischen jedem Regen 5 s Zeit"). F rains arrows onto the
 * spot looked at (a monster looked at, else the block, at most {@code range} blocks away) for
 * {@code dur} ticks; every few ticks each monster inside the circle takes a hit. The next rain is
 * ready {@code pause} ticks after the last one ended. Each subclass has its own note:
 * <ul>
 *   <li>Schnellschütze (swift): a hail – many small hits.</li>
 *   <li>Wuchtschütze (heavy): few heavy hits that push the monsters back.</li>
 *   <li>Bolzenschütze (bolter): bolts on a tighter circle, harder hits.</li>
 *   <li>Fallensteller (trapper): a wide circle, light hits, and everything in it is slowed.</li>
 * </ul>
 * The hits grow with the night's level like the monsters' life and count as the archer's own
 * damage (class and subclass factors of the config apply), in the statistics as "faehigkeit:pfeilregen".
 * All numbers are skill values of "arrow_rain" (server skill tweaks can change them).
 */
public final class ArrowRain {

    /** Its id (ability field F, statistics). */
    public static final String ID = "arrow_rain";
    static final String NODE = "arrow_rain";

    private static final Map<UUID, Long> READY = new HashMap<>();
    private static final Map<UUID, Integer> TOTAL = new HashMap<>();
    private static final List<Rain> RAINS = new ArrayList<>();
    private static CombatEffects combat;
    /** True while a rain's hit lands (the statistics name its source). */
    private static boolean hitting;
    /** For the tests: rains fired and hits landed. */
    private static int fired;
    private static int hits;

    private ArrowRain() {
    }

    /** One rain in the air. */
    private static final class Rain {
        final UUID owner;
        final ServerLevel level;
        final Vec3 center;
        final String sub;
        final long end;
        long nextHit;

        Rain(UUID owner, ServerLevel level, Vec3 center, String sub, long start, long end) {
            this.owner = owner;
            this.level = level;
            this.center = center;
            this.sub = sub;
            this.end = end;
            this.nextHit = start;
        }
    }

    /** The combat effects the hits go through (no on-hit procs, like every bonus hit). */
    static void use(CombatEffects effects) {
        combat = effects;
    }

    public static boolean hitting() {
        return hitting;
    }

    public static int fired() {
        return fired;
    }

    public static int hits() {
        return hits;
    }

    /** A new night, or the end of one: every rain ends, every F is ready. */
    public static void reset() {
        READY.clear();
        TOTAL.clear();
        RAINS.clear();
    }

    /** Whether this player's F is the arrow rain: every archer subclass, from the class on. */
    public static boolean has(PlayerSkills skills) {
        return skills != null && skills.hasClass() && skills.clazz() == SkillClass.ARCHER;
    }

    /** Ticks until F is ready again (the rain itself and the pause after it). */
    public static int cooldownLeft(ServerPlayer player, long now) {
        Long ready = player == null ? null : READY.get(player.getUUID());
        return ready == null || ready <= now ? 0 : (int) (ready - now);
    }

    /** The whole wait of the last rain (rain plus pause), for the ring on the HUD. */
    public static int cooldownTotal(ServerPlayer player) {
        return player == null ? 0 : TOTAL.getOrDefault(player.getUUID(), duration() + pause());
    }

    static int duration() {
        return (int) Math.max(1, SkillValues.get(NODE, "dur", 60));
    }

    static int pause() {
        return (int) Math.max(0, SkillValues.get(NODE, "pause", 160));
    }

    static double range() {
        return SkillValues.get(NODE, "range", 32.0);
    }

    /** The subclass's own numbers: radius, ticks between hits, damage of a hit (level 1), slow ticks, push. */
    static double radius(String sub) {
        return SkillValues.get(NODE, "radius_" + key(sub), 4.0);
    }

    static int every(String sub) {
        return (int) Math.max(1, SkillValues.get(NODE, "every_" + key(sub), 10));
    }

    static double damage(String sub) {
        return SkillValues.get(NODE, "dmg_" + key(sub), 0.5) * SkillValues.get(NODE, "mult", 1.0);
    }

    private static String key(String sub) {
        return sub == null || sub.isEmpty() ? "swift" : sub;
    }

    /** F pressed: the rain onto the spot looked at, if it is ready. */
    static void fire(ServerPlayer player, SiegeManager m) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 spot = aim(level, player);
        if (spot == null) {
            bar(player, Component.translatable("nightsiege.msg.rain_no_spot"));
            return;
        }
        fireAt(player, m, spot);
    }

    /** The rain onto a given spot (the bots pick theirs). Returns whether it started. */
    static boolean fireAt(ServerPlayer player, SiegeManager m, Vec3 spot) {
        if (!(player.level() instanceof ServerLevel level) || spot == null) {
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && (m == null || !m.isDefending(player))) {
            bar(player, Component.translatable("nightsiege.msg.rain_fight_only"));
            return false;
        }
        if (Drones.isDrone(player)) {
            return false;
        }
        long now = level.getGameTime();
        int left = cooldownLeft(player, now);
        if (left > 0 && !creative) {
            bar(player, Component.translatable("nightsiege.msg.rain_wait", (left + 19) / 20));
            return false;
        }
        PlayerSkills skills = m == null ? null : m.save().skillsOf(player.getUUID().toString());
        String sub = skills == null || m.skillTree() == null ? SkillTree.SUB_SWIFT : m.skillTree().subclassOf(skills);
        if (sub == null || sub.isEmpty()) {
            sub = SkillTree.SUB_SWIFT;
        }
        int dur = duration();
        int wait = dur + pause();
        READY.put(player.getUUID(), now + wait);
        TOTAL.put(player.getUUID(), wait);
        RAINS.add(new Rain(player.getUUID(), level, spot, sub, now + 4, now + dur));
        fired++;
        double r = radius(sub);
        // The circle where it will come down, and the volley going up.
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            level.sendParticles(ParticleTypes.CRIT, spot.x + Math.cos(a) * r, spot.y + 0.2, spot.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.8f, 0.8f);
        return true;
    }

    /** Where the player looks: a monster in reach, else the block hit, else the ground under the end of the reach. */
    static Vec3 aim(ServerLevel level, ServerPlayer player) {
        double range = range();
        LivingEntity target = CombatEffects.lookTarget(level, player, range);
        if (target != null) {
            return target.position();
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(end.x), (int) Math.floor(end.z));
        return top > level.getMinY() ? new Vec3(end.x, top, end.z) : null;
    }

    /** Every tick: the rains in the air hit what is under them. */
    public static void tick(MinecraftServer server) {
        if (RAINS.isEmpty()) {
            return;
        }
        Iterator<Rain> it = RAINS.iterator();
        while (it.hasNext()) {
            Rain rain = it.next();
            long now = rain.level.getGameTime();
            if (now > rain.end) {
                it.remove();
                continue;
            }
            streaks(rain);
            if (now < rain.nextHit) {
                continue;
            }
            rain.nextHit = now + every(rain.sub);
            ServerPlayer owner = server.getPlayerList().getPlayer(rain.owner);
            if (owner == null || !owner.isAlive()) {
                it.remove();
                continue;
            }
            hit(rain, owner);
        }
    }

    private static void hit(Rain rain, ServerPlayer owner) {
        ServerLevel level = rain.level;
        SiegeManager m = SiegeManager.get();
        int nightLevel = m == null ? 1 : Math.max(1, m.currentLevel());
        double perHit = damage(rain.sub) * (m == null ? 1.0 : SiegeMath.healthMultiplier(nightLevel, m.config()));
        double r = radius(rain.sub);
        int slow = (int) SkillValues.get(NODE, "slow_" + key(rain.sub), 0);
        double push = SkillValues.get(NODE, "push_" + key(rain.sub), 0.0);
        DamageSource source = level.damageSources().playerAttack(owner);
        List<LivingEntity> under = CombatEffects.enemies(level, rain.center, r, 4.0);
        hitting = true;
        try {
            for (LivingEntity victim : under) {
                if (combat != null) {
                    combat.bonusHit(level, victim, source, (float) perHit);
                } else {
                    victim.invulnerableTime = 0;
                    victim.hurtServer(level, source, (float) perHit);
                }
                hits++;
                if (slow > 0) {
                    victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, 1));
                }
                if (push > 0) {
                    Vec3 away = victim.position().subtract(rain.center);
                    double len = Math.sqrt(away.x * away.x + away.z * away.z);
                    if (len > 1.0e-3) {
                        victim.push(away.x / len * push, 0.1, away.z / len * push);
                        victim.hurtMarked = true;
                    }
                }
                level.sendParticles(particle(rain.sub), victim.getX(), victim.getY(0.8), victim.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
            }
        } finally {
            hitting = false;
        }
        if (!under.isEmpty()) {
            level.playSound(null, BlockPos.containing(rain.center), SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    /** The arrows coming down: streaks from above onto the circle. */
    private static void streaks(Rain rain) {
        ServerLevel level = rain.level;
        double r = radius(rain.sub);
        int count = SkillTree.SUB_SWIFT.equals(rain.sub) ? 4 : 2;
        for (int i = 0; i < count; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double d = Math.sqrt(level.getRandom().nextDouble()) * r;
            double x = rain.center.x + Math.cos(a) * d;
            double z = rain.center.z + Math.sin(a) * d;
            // count 0: the particle flies along (dx, dy, dz) at the given speed – straight down
            level.sendParticles(ParticleTypes.CRIT, x, rain.center.y + 6.0, z, 0, 0.0, -1.0, 0.0, 1.6);
        }
    }

    private static ParticleOptions particle(String sub) {
        if (SkillTree.SUB_TRAPPER.equals(sub)) {
            return ParticleTypes.ITEM_COBWEB;
        }
        if (SkillTree.SUB_BOLTER.equals(sub)) {
            return ParticleTypes.ENCHANTED_HIT;
        }
        if (SkillTree.SUB_HEAVY.equals(sub)) {
            return ParticleTypes.SWEEP_ATTACK;
        }
        return ParticleTypes.CRIT;
    }

    private static void bar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
}
