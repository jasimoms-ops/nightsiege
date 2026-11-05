package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
import com.mki.nightsiege.core.WarriorSpiritMath;
import com.mki.nightsiege.mixin.DisplayAccessor;
import com.mki.nightsiege.mixin.ItemDisplayAccessor;
import com.mojang.math.Transformation;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The warriors' F abilities (Simon 05.11.: the warrior was the only class with nothing on F). Each
 * costs {@code w_spirit.cost} Kampfgeist ({@link WarriorSpirit}) and has a short pause; F works only
 * while defending in a fight (night, dungeon, war camp), like the archers' arrow rain.
 *
 * <ul>
 *   <li>Klingenmeister, <b>Klingentanz</b>: three quick jumps to monsters within 6 blocks, one hit
 *       each; no damage while dancing.</li>
 *   <li>Berserker, <b>Raserei</b>: 6 s of +30 % attack speed and double life steal, but 15 % more
 *       damage taken; every kill in it adds a second.</li>
 *   <li>Axtkämpfer, <b>Axtwurf</b>: the axe flies straight ahead (up to 12 blocks) and back and hits
 *       everything on its way, with Spalter's broken armour.</li>
 *   <li>Paladin, <b>Weihegrund</b>: a consecrated circle of 6 blocks for 8 s: players inside regenerate,
 *       the undead burn, monsters are slowed.</li>
 * </ul>
 *
 * Their hits grow with the night's level like the monsters' life (as the arrow rain's do), count as
 * the warrior's own damage (class factors apply) and in the statistics as "faehigkeit:&lt;id&gt;".
 * The numbers are skill values ({@code f_*}) of the subclass node, see {@link WarriorSpiritMath}.
 */
public final class WarriorArts {

    /** The thrown axe's display (gone after a restart). */
    static final String TAG = "nightsiege_axe_throw";
    private static final Identifier FRENZY_SPEED = ModContent.id("frenzy_speed");
    private static final DustParticleOptions RAGE = new DustParticleOptions(0xB01010, 1.3f);

    private static final Map<UUID, Long> READY = new HashMap<>();
    private static final Map<UUID, Integer> TOTAL = new HashMap<>();
    private static final Map<UUID, Dance> DANCES = new HashMap<>();
    private static final Map<UUID, Frenzy> FRENZIES = new HashMap<>();
    private static final List<Throw> THROWS = new ArrayList<>();
    private static final List<Ground> GROUNDS = new ArrayList<>();
    /** The thrown axes' displays alive right now. */
    private static final Set<UUID> LIVE = new HashSet<>();

    // For the tests and the class simulation.
    private static int used;
    private static int danceHits;
    private static int frenzies;
    private static int frenzyKills;
    private static int throwHits;
    private static int throwsDone;
    private static int groundPulses;
    private static double damage;

    private WarriorArts() {
    }

    private static final class Dance {
        final ServerLevel level;
        final List<UUID> order;
        final int gap;
        final long until;
        int index;
        long next;

        Dance(ServerLevel level, List<UUID> order, int gap, long first, long until) {
            this.level = level;
            this.order = order;
            this.gap = gap;
            this.next = first;
            this.until = until;
        }
    }

    private static final class Frenzy {
        long end;
        final long cap;

        Frenzy(long end, long cap) {
            this.end = end;
            this.cap = cap;
        }
    }

    private static final class Throw {
        final ServerLevel level;
        final UUID owner;
        final Vec3 dir;
        final UUID display;
        final long start;
        final Set<UUID> hitOut = new HashSet<>();
        final Set<UUID> hitBack = new HashSet<>();
        Vec3 pos;
        double travelled;
        boolean back;
        float spin;

        Throw(ServerLevel level, UUID owner, Vec3 pos, Vec3 dir, UUID display, long start) {
            this.level = level;
            this.owner = owner;
            this.pos = pos;
            this.dir = dir;
            this.display = display;
            this.start = start;
        }
    }

    private static final class Ground {
        final ServerLevel level;
        final UUID owner;
        final Vec3 center;
        final long end;
        long next;

        Ground(ServerLevel level, UUID owner, Vec3 center, long next, long end) {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.next = next;
            this.end = end;
        }
    }

    // ------------------------------------------------------------------ who has what

    /** Whether this player's F is a warrior's ability: every warrior subclass. */
    public static boolean has(PlayerSkills skills, SkillTree tree) {
        return skills != null && tree != null && skills.hasClass() && skills.clazz() == SkillClass.WARRIOR
                && WarriorSpiritMath.isWarriorSub(tree.subclassOf(skills));
    }

    /** Ticks until F is ready again. */
    public static int cooldownLeft(ServerPlayer player, long now) {
        Long ready = player == null ? null : READY.get(player.getUUID());
        return ready == null || ready <= now ? 0 : (int) (ready - now);
    }

    /** The whole pause of the last use, for the ring on the HUD. */
    public static int cooldownTotal(ServerPlayer player, String sub) {
        return player == null ? 0 : TOTAL.getOrDefault(player.getUUID(), WarriorSpiritMath.abilityCooldown(sub));
    }

    /** True when F could go now: enough Kampfgeist and no pause (the bots ask). */
    public static boolean ready(ServerPlayer player, long now) {
        return WarriorSpiritMath.canUseAbility(WarriorSpirit.value(player)) && cooldownLeft(player, now) == 0;
    }

    /** How close a monster must be before a bot uses F (the throw flies far, the others work close by). */
    public static double botReach(String sub) {
        if (SkillTree.SUB_AXE.equals(sub)) {
            return SkillValues.get("heavy_blow", "f_range", 12) * 0.75;
        }
        if (SkillTree.SUB_BLADEMASTER.equals(sub)) {
            return SkillValues.get("w_combo", "f_radius", 6) - 0.5;
        }
        return 4.0;
    }

    /** No damage while the blade dances (ALLOW_DAMAGE). */
    public static boolean allowDamage(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return !dancing(player);
    }

    /** True while this player dances (for the tests too). */
    public static boolean dancing(ServerPlayer player) {
        Dance d = player == null ? null : DANCES.get(player.getUUID());
        return d != null && player.level().getGameTime() <= d.until;
    }

    /** True while this berserker is in his frenzy. */
    public static boolean frenzied(ServerPlayer player) {
        Frenzy f = player == null ? null : FRENZIES.get(player.getUUID());
        return f != null && player.level().getGameTime() <= f.end;
    }

    /** Ticks of frenzy left (for the tests). */
    public static long frenzyLeft(ServerPlayer player) {
        Frenzy f = player == null ? null : FRENZIES.get(player.getUUID());
        return f == null ? 0L : Math.max(0L, f.end - player.level().getGameTime());
    }

    /** The factor on the berserker's life steal (double in the frenzy). */
    static double stealFactor(ServerPlayer player) {
        return frenzied(player) ? SkillValues.get("bloodlust", "f_steal", 2.0) : 1.0;
    }

    /** The factor on the damage a player takes from monsters (more in the frenzy). */
    public static float takenFactor(ServerPlayer player) {
        return frenzied(player) ? (float) (1.0 + SkillValues.get("bloodlust", "f_taken", 0.15)) : 1f;
    }

    /** A kill in the frenzy makes it last longer. */
    static void onKill(ServerPlayer player, long now) {
        Frenzy f = FRENZIES.get(player.getUUID());
        if (f != null && now <= f.end) {
            f.end = Math.min(f.cap, f.end + Math.max(0L, Math.round(SkillValues.get("bloodlust", "f_extend", 20))));
            frenzyKills++;
        }
    }

    /** A thrown axe's display from before a restart (ENTITY_LOAD). */
    public static boolean stray(Entity entity) {
        return entity.entityTags().contains(TAG) && !LIVE.contains(entity.getUUID());
    }

    /** A new night or the end of one: everything stops, every F is ready. */
    public static void reset() {
        READY.clear();
        TOTAL.clear();
        DANCES.clear();
        for (Frenzy f : FRENZIES.values()) {
            f.end = Long.MIN_VALUE / 4;   // the next tick takes the attack speed away
        }
        for (Throw t : THROWS) {
            removeDisplay(t);
        }
        THROWS.clear();
        GROUNDS.clear();
    }

    static void forget(UUID player) {
        READY.remove(player);
        TOTAL.remove(player);
        DANCES.remove(player);
    }

    // ------------------------------------------------------------------ F

    /** F pressed by a warrior. Returns whether an ability went off. */
    static boolean fire(ServerPlayer player, SiegeManager m) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && (m == null || !m.isDefending(player))) {
            bar(player, Component.translatable("nightsiege.msg.spirit_fight_only"));
            return false;
        }
        if (Drones.isDrone(player) || player.isPassenger()) {
            return false;
        }
        PlayerSkills skills = m == null ? null : m.save().skillsOf(player.getUUID().toString());
        String sub = skills == null || m.skillTree() == null ? "" : m.skillTree().subclassOf(skills);
        if (!WarriorSpiritMath.isWarriorSub(sub)) {
            return false;
        }
        String id = WarriorSpiritMath.abilityId(sub);
        Component name = Component.translatable("nightsiege.warrior_f." + id);
        long now = level.getGameTime();
        int left = cooldownLeft(player, now);
        if (left > 0 && !creative) {
            bar(player, Component.translatable("nightsiege.msg.spirit_wait", name, (left + 19) / 20));
            return false;
        }
        double have = WarriorSpirit.value(player);
        double cost = WarriorSpiritMath.abilityCost();
        if (!WarriorSpiritMath.canUseAbility(have)) {
            bar(player, Component.translatable("nightsiege.msg.spirit_low", name, (int) Math.floor(have), (int) Math.round(cost)));
            return false;
        }
        boolean fired = switch (sub) {
            case SkillTree.SUB_BERSERKER -> frenzy(level, player, now);
            case SkillTree.SUB_AXE -> axeThrow(level, player, now);
            case SkillTree.SUB_PALADIN -> hallow(level, player, now);
            default -> dance(level, player, now);
        };
        if (!fired) {
            return false;
        }
        WarriorSpirit.spend(player, cost, now);
        int pause = WarriorSpiritMath.abilityCooldown(sub);
        READY.put(player.getUUID(), now + pause);
        TOTAL.put(player.getUUID(), pause);
        used++;
        bar(player, Component.translatable("nightsiege.msg.spirit_used", name));
        return true;
    }

    /** The damage of an ability's hit: its number on level 1, grown like the monsters' life. */
    private static float scaled(double base) {
        SiegeManager m = SiegeManager.get();
        int level = m == null ? 1 : Math.max(1, m.currentLevel());
        return (float) (base * (m == null ? 1.0 : SiegeMath.healthMultiplier(level, m.config())));
    }

    // --- Klingentanz

    private static boolean dance(ServerLevel level, ServerPlayer player, long now) {
        double r = SkillValues.get("w_combo", "f_radius", 6);
        List<LivingEntity> near = new ArrayList<>(CombatEffects.enemies(level, player.position(), r, 3.0));
        near.removeIf(e -> !e.isAlive() || !player.hasLineOfSight(e));
        if (near.isEmpty()) {
            bar(player, Component.translatable("nightsiege.msg.spirit_no_target"));
            return false;
        }
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        int jumps = (int) Math.max(1, Math.round(SkillValues.get("w_combo", "f_jumps", 3)));
        List<UUID> order = new ArrayList<>();
        for (int i = 0; i < jumps; i++) {
            order.add(near.get(i % near.size()).getUUID());   // fewer monsters than jumps: round again
        }
        int gap = (int) Math.max(1, Math.round(SkillValues.get("w_combo", "f_gap", 5)));
        DANCES.put(player.getUUID(), new Dance(level, order, gap, now, now + (long) gap * jumps + 6));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8f, 1.6f);
        return true;
    }

    private static void danceStep(MinecraftServer server, UUID id, Dance d, long now) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null || !player.isAlive() || player.level() != d.level) {
            d.index = d.order.size();
            return;
        }
        ServerLevel level = d.level;
        Entity planned = level.getEntity(d.order.get(d.index));
        LivingEntity target = planned instanceof LivingEntity l && l.isAlive() ? l : null;
        if (target == null) {
            // The planned one fell already: the nearest one still standing.
            double r = SkillValues.get("w_combo", "f_radius", 6);
            double best = Double.MAX_VALUE;
            for (LivingEntity e : CombatEffects.enemies(level, player.position(), r, 3.0)) {
                double dd = e.distanceToSqr(player);
                if (e.isAlive() && dd < best) {
                    best = dd;
                    target = e;
                }
            }
        }
        d.index++;
        d.next = now + d.gap;
        if (target == null) {
            return;
        }
        Vec3 from = player.position();
        Vec3 to = target.position();
        Vec3 flat = new Vec3(from.x - to.x, 0, from.z - to.z);
        double off = target.getBbWidth() / 2.0 + 0.7;
        Vec3 spot = flat.lengthSqr() < 1.0e-4 ? to : to.add(flat.normalize().scale(off));
        if (!free(level, player, spot)) {
            spot = to;   // the monster's own feet are free of blocks
        }
        if (free(level, player, spot)) {
            level.sendParticles(ParticleTypes.CLOUD, from.x, from.y + 0.2, from.z, 6, 0.25, 0.05, 0.25, 0.03);
            player.teleportTo(spot.x, spot.y, spot.z);
            player.resetFallDistance();
        }
        DamageSource src = level.damageSources().playerAttack(player);
        damage += WarriorSpirit.strike(level, target, src, scaled(SkillValues.get("w_combo", "f_dmg", 6)),
                "faehigkeit:" + WarriorSpiritMath.abilityId(SkillTree.SUB_BLADEMASTER), false);
        danceHits++;
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    /** True when the player's body fits at this spot (blocks only). */
    private static boolean free(ServerLevel level, ServerPlayer player, Vec3 spot) {
        double half = player.getBbWidth() / 2.0;
        AABB body = new AABB(spot.x - half, spot.y + 0.01, spot.z - half, spot.x + half, spot.y + player.getBbHeight(), spot.z + half);
        return level.noCollision(player, body);
    }

    // --- Raserei

    private static boolean frenzy(ServerLevel level, ServerPlayer player, long now) {
        long time = Math.max(1L, Math.round(SkillValues.get("bloodlust", "f_time", 120)));
        long cap = Math.max(time, Math.round(SkillValues.get("bloodlust", "f_max", 240)));
        FRENZIES.put(player.getUUID(), new Frenzy(now + time, now + cap));
        frenzies++;
        speed(player, true);
        level.sendParticles(RAGE, player.getX(), player.getY(1.0), player.getZ(), 24, 0.4, 0.5, 0.4, 0.0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6f, 1.3f);
        return true;
    }

    private static void speed(ServerPlayer player, boolean on) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attack == null) {
            return;
        }
        if (on) {
            attack.addOrUpdateTransientModifier(new AttributeModifier(FRENZY_SPEED, SkillValues.get("bloodlust", "f_speed", 0.3),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (attack.getModifier(FRENZY_SPEED) != null) {
            attack.removeModifier(FRENZY_SPEED);
        }
    }

    /** True while the frenzy's attack speed is on this player (for the tests). */
    public static boolean frenzySpeedOn(ServerPlayer player) {
        AttributeInstance attack = player == null ? null : player.getAttribute(Attributes.ATTACK_SPEED);
        return attack != null && attack.getModifier(FRENZY_SPEED) != null;
    }

    // --- Axtwurf

    private static boolean axeThrow(ServerLevel level, ServerPlayer player, long now) {
        Vec3 dir = CombatEffects.flatLook(player);
        if (dir == null) {
            double yaw = Math.toRadians(player.getYRot());
            dir = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        Vec3 start = player.position().add(0, player.getBbHeight() * 0.55, 0).add(dir.scale(0.6));
        ItemStack held = player.getMainHandItem();
        ItemStack shown = held.isEmpty() ? new ItemStack(Items.IRON_AXE) : held.copyWithCount(1);
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        ((ItemDisplayAccessor) display).nightsiege$setItemStack(shown);
        ((DisplayAccessor) display).nightsiege$setPosRotInterpolationDuration(1);
        ((DisplayAccessor) display).nightsiege$setTransformationInterpolationDuration(1);
        display.setPos(start.x, start.y, start.z);
        display.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        display.addTag(TAG);
        LIVE.add(display.getUUID());
        level.addFreshEntity(display);
        THROWS.add(new Throw(level, player.getUUID(), start, dir, display.getUUID(), now));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
        return true;
    }

    /** One tick of a thrown axe; true when it is back (or lost). */
    private static boolean flyAxe(MinecraftServer server, Throw t, long now) {
        ServerPlayer owner = server.getPlayerList().getPlayer(t.owner);
        if (owner == null || !owner.isAlive() || owner.level() != t.level || now - t.start > 80) {
            return true;
        }
        double speed = Math.max(0.2, SkillValues.get("heavy_blow", "f_speed", 1.5));
        double range = SkillValues.get("heavy_blow", "f_range", 12);
        Vec3 next;
        boolean home = false;
        if (!t.back) {
            next = t.pos.add(t.dir.scale(speed));
            BlockHitResult wall = t.level.clip(new ClipContext(t.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (wall.getType() == HitResult.Type.BLOCK) {
                next = wall.getLocation().subtract(t.dir.scale(0.2));
                t.back = true;   // it struck a wall: back it comes
            }
            t.travelled += speed;
            if (t.travelled >= range) {
                t.back = true;
            }
            strikeAlong(t.level, owner, t.pos, next, t.hitOut, now);
        } else {
            Vec3 hand = owner.position().add(0, owner.getBbHeight() * 0.55, 0);
            Vec3 to = hand.subtract(t.pos);
            double d = to.length();
            next = d <= speed ? hand : t.pos.add(to.scale(speed / d));
            home = d <= speed + 0.3;
            strikeAlong(t.level, owner, t.pos, next, t.hitBack, now);
        }
        t.pos = next;
        t.spin += 0.9f;
        Entity e = t.level.getEntity(t.display);
        if (e instanceof Display display) {
            display.setPos(next.x, next.y, next.z);
            ((DisplayAccessor) display).nightsiege$setTransformationInterpolationDelay(0);
            ((DisplayAccessor) display).nightsiege$setTransformation(new Transformation(new Vector3f(),
                    new Quaternionf().rotationX(t.spin).rotateY((float) (Math.PI / 2)), new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
        }
        t.level.sendParticles(ParticleTypes.CRIT, next.x, next.y, next.z, 2, 0.1, 0.1, 0.1, 0.02);
        if (now % 4 == 0) {
            t.level.playSound(null, next.x, next.y, next.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4f, 1.8f);
        }
        return home;
    }

    /** Every monster along the axe's way this tick, once on the way out and once on the way back. */
    private static void strikeAlong(ServerLevel level, ServerPlayer owner, Vec3 a, Vec3 b, Set<UUID> done, long now) {
        double width = SkillValues.get("heavy_blow", "f_width", 1.3);
        AABB box = new AABB(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z),
                Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z)).inflate(width + 1.0, 2.5, width + 1.0);
        DamageSource src = level.damageSources().playerAttack(owner);
        float dmg = scaled(SkillValues.get("heavy_blow", "f_dmg", 5));
        for (LivingEntity e : TrapService.enemies(level, box)) {
            if (done.contains(e.getUUID())) {
                continue;
            }
            double reach = width + e.getBbWidth() / 2.0;
            double y = (a.y + b.y) / 2.0;
            if (flatDistance(e.position(), a, b) > reach || y < e.getY() - 0.6 || y > e.getY() + e.getBbHeight() + 0.6) {
                continue;
            }
            done.add(e.getUUID());
            damage += WarriorSpirit.strike(level, e, src, dmg, "faehigkeit:" + WarriorSpiritMath.abilityId(SkillTree.SUB_AXE), false);
            throwHits++;
            if (e.isAlive()) {
                WarriorSpirit.cleave(level, e, 1.0, now);
            }
        }
    }

    /** Sideways distance of a point from the segment a–b (heights left out). */
    static double flatDistance(Vec3 p, Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double len = dx * dx + dz * dz;
        double t = len < 1.0e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((p.x - a.x) * dx + (p.z - a.z) * dz) / len));
        double cx = a.x + dx * t - p.x;
        double cz = a.z + dz * t - p.z;
        return Math.sqrt(cx * cx + cz * cz);
    }

    private static void removeDisplay(Throw t) {
        Entity e = t.level.getEntity(t.display);
        if (e != null) {
            e.discard();
        }
        LIVE.remove(t.display);
    }

    /** Thrown axes in the air right now (for the tests). */
    public static int axesInFlight() {
        return THROWS.size();
    }

    // --- Weihegrund

    private static boolean hallow(ServerLevel level, ServerPlayer player, long now) {
        long time = Math.max(20L, Math.round(SkillValues.get("smite", "f_time", 160)));
        GROUNDS.add(new Ground(level, player.getUUID(), player.position(), now, now + time));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    /** Once a second: the players inside regenerate, the monsters are slowed, the undead burn. */
    private static void pulse(MinecraftServer server, Ground g) {
        ServerLevel level = g.level;
        double r = SkillValues.get("smite", "f_radius", 6);
        int regen = (int) Math.max(0, Math.round(SkillValues.get("smite", "f_regen", 50)));
        int slow = (int) Math.max(0, Math.round(SkillValues.get("smite", "f_slow", 30)));
        groundPulses++;
        for (ServerPlayer mate : GuardianEffects.mates(level)) {
            double dx = mate.getX() - g.center.x;
            double dz = mate.getZ() - g.center.z;
            if (mate.isAlive() && regen > 0 && dx * dx + dz * dz <= r * r && Math.abs(mate.getY() - g.center.y) <= 3.0) {
                mate.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 0));
            }
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(g.owner);
        float holy = scaled(SkillValues.get("smite", "f_holy", 1.0));
        for (LivingEntity e : CombatEffects.enemies(level, g.center, r, 3.0)) {
            if (slow > 0) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, 0));
            }
            if (CombatEffects.isUndead(e)) {
                e.igniteForSeconds(2f);
                if (owner != null && holy > 0f) {
                    damage += WarriorSpirit.strike(level, e, level.damageSources().playerAttack(owner), holy,
                            "faehigkeit:" + WarriorSpiritMath.abilityId(SkillTree.SUB_PALADIN), false);
                }
            }
        }
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2 * i / 28;
            level.sendParticles(ParticleTypes.END_ROD, g.center.x + Math.cos(a) * r, g.center.y + 0.2, g.center.z + Math.sin(a) * r,
                    1, 0, 0.05, 0, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, g.center.x, g.center.y + 0.3, g.center.z, 12, r * 0.4, 0.1, r * 0.4, 0.01);
    }

    /** Weihegründe on the ground right now (for the tests). */
    public static int grounds() {
        return GROUNDS.size();
    }

    // ------------------------------------------------------------------ every tick

    static void tick(MinecraftServer server, long now) {
        if (!DANCES.isEmpty()) {
            Iterator<Map.Entry<UUID, Dance>> it = DANCES.entrySet().iterator();
            List<Map.Entry<UUID, Dance>> due = new ArrayList<>();
            while (it.hasNext()) {
                Map.Entry<UUID, Dance> e = it.next();
                Dance d = e.getValue();
                if (d.index >= d.order.size() && now > d.until) {
                    it.remove();
                } else if (d.index < d.order.size() && now >= d.next) {
                    due.add(e);
                }
            }
            for (Map.Entry<UUID, Dance> e : due) {
                danceStep(server, e.getKey(), e.getValue(), now);
            }
        }
        if (!FRENZIES.isEmpty()) {
            Iterator<Map.Entry<UUID, Frenzy>> it = FRENZIES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Frenzy> e = it.next();
                ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
                if (player == null || !player.isAlive() || now > e.getValue().end) {
                    if (player != null) {
                        speed(player, false);
                    }
                    it.remove();
                } else if (now % 10 == 0 && player.level() instanceof ServerLevel level) {
                    speed(player, true);
                    level.sendParticles(RAGE, player.getX(), player.getY(0.8), player.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
                }
            }
        }
        if (!THROWS.isEmpty()) {
            for (Throw t : new ArrayList<>(THROWS)) {
                if (flyAxe(server, t, now)) {
                    removeDisplay(t);
                    THROWS.remove(t);
                    throwsDone++;
                }
            }
        }
        if (!GROUNDS.isEmpty()) {
            for (Ground g : new ArrayList<>(GROUNDS)) {
                if (now > g.end) {
                    GROUNDS.remove(g);
                    continue;
                }
                if (now >= g.next) {
                    g.next = now + 20;
                    pulse(server, g);
                }
            }
        }
    }

    private static void bar(ServerPlayer player, Component text) {
        if (player.connection != null) {
            player.connection.send(new ClientboundSetActionBarTextPacket(text));
        }
    }

    // ------------------------------------------------------------------ tests and the simulation

    public static int used() {
        return used;
    }

    public static int danceHits() {
        return danceHits;
    }

    public static int frenzies() {
        return frenzies;
    }

    public static int frenzyKills() {
        return frenzyKills;
    }

    public static int throwHits() {
        return throwHits;
    }

    public static int throwsDone() {
        return throwsDone;
    }

    public static int groundPulses() {
        return groundPulses;
    }

    static void resetCounters() {
        used = 0;
        danceHits = 0;
        frenzies = 0;
        frenzyKills = 0;
        throwHits = 0;
        throwsDone = 0;
        groundPulses = 0;
        damage = 0.0;
    }

    static String counters() {
        return String.format(java.util.Locale.ROOT,
                "F %d (Schaden %.0f; Tanz-Treffer %d, Raserei %d/+%d Kills, Axt-Treffer %d, Weihegrund-Pulse %d)",
                used, damage, danceHits, frenzies, frenzyKills, throwHits, groundPulses);
    }
}
