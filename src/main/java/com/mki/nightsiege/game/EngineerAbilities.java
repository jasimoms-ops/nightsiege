package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
import com.mki.nightsiege.core.StingLock;
import com.mki.nightsiege.core.TrapKind;
import com.mki.nightsiege.core.UltState;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tor 4b, Variante B "Turm als Waffe" (Chef 09.11. 01:20): every engineer subclass has its own fighting
 * abilities on places 2 and 3 instead of the remote. On foot they go out from the hand; from a tower's
 * seat the tower fires them, with more reach, area or targets. The keys come from
 * {@link com.mki.nightsiege.core.AbilityKeys} alone; place 1 is the blast jump (in the seat the
 * Abwehrring, {@link EngineerEscape}), the alchemist keeps his potions on 3 and the acid on 4.
 *
 * <p>Every number is a {@link SkillValues} entry under {@code t_<id>} (server tweaks work as usual).
 */
public final class EngineerAbilities {

    /** Schütze: place 2 Durchschlag, place 3 Sperrfeuer. */
    public static final String PIERCE = "pierce";
    public static final String SUPPRESS = "suppress";
    /** Elektriker: place 2 Kettenblitz, place 3 Statikfeld. */
    public static final String CHAIN = "chain";
    public static final String STATIC = "static";
    /** Sprengmeister: place 2 Haftgranate, place 3 Zielpunkt. */
    public static final String GRENADE = "grenade";
    public static final String TARGET = "target";
    /** Alchemist: place 2 Siedepunkt (place 3 the potions, 4 the acid flask). */
    public static final String BOIL = "boil";

    /** All of them, in the order of the subclasses. */
    public static final List<String> ALL = List.of(PIERCE, SUPPRESS, CHAIN, STATIC, GRENADE, TARGET, BOIL);

    /** Player → ability id → game time it is ready again. */
    private static final Map<UUID, Map<String, Long>> READY = new HashMap<>();
    /** What runs on over several ticks: Sperrfeuer bursts, Statikfelder, Haftgranaten. */
    private static final List<Running> RUNNING = new ArrayList<>();
    /** Ticks between two stings of a Statikfeld. */
    private static final long STING_EVERY = 20;
    /** Shared hit lock of a player's Statikfelder: no stacking of the Netzwerk fields. */
    private static final StingLock STUNG = new StingLock();

    private EngineerAbilities() {
    }

    /** The abilities a subclass has on places 2 and 3 (the alchemist only place 2). */
    public static List<String> of(String sub) {
        if (sub == null) {
            return List.of();
        }
        return switch (sub) {
            case SkillTree.SUB_GUNNER -> List.of(PIERCE, SUPPRESS);
            case SkillTree.SUB_ELECTRICIAN -> List.of(CHAIN, STATIC);
            case SkillTree.SUB_DEMOLITION -> List.of(GRENADE, TARGET);
            case SkillTree.SUB_ALCHEMIST -> List.of(BOIL);
            default -> List.of();
        };
    }

    /** True for one of the ids here. */
    public static boolean isOne(String id) {
        return id != null && ALL.contains(id);
    }

    /** The node the ability's numbers sit under. */
    public static String node(String id) {
        return "t_" + id;
    }

    private static double val(String id, String key, double fallback) {
        return SkillValues.get(node(id), key, fallback);
    }

    // ------------------------------------------------------------------ pauses

    /** The base pause of an ability (ticks), before the skills. */
    public static long basePause(String id) {
        return Math.round(val(id, "pause", 200));
    }

    /**
     * Share of the pause after the skills (qa/tor-4b/skillbaeume.md): Akku and Schnellladung shorten it
     * per level, Feldtechniker shortens it, Festungsbauer lengthens it; with Notstrom under its share of
     * health the pause runs faster.
     */
    public static double pauseFactor(SkillTree tree, PlayerSkills skills, double healthShare) {
        if (skills == null || tree == null) {
            return 1.0;
        }
        double f = 1.0 - tree.totalBonus(SkillEffect.REMOTE_ENERGY, skills) - tree.totalBonus(SkillEffect.RECHARGE, skills);
        if (SkillTree.has(skills, "e_fieldtech")) {
            f -= SkillValues.get("e_fieldtech", "cost", 0.25);
        }
        if (SkillTree.has(skills, "e_fortress")) {
            f += SkillValues.get("e_fortress", "cost", 0.2);
        }
        if (tree.totalBonus(SkillEffect.BACKUP_POWER, skills) > 0 && healthShare < SkillValues.get("e_backup", "below", 0.3)) {
            f /= Math.max(1.0, SkillValues.get("e_backup", "speed", 2.0));
        }
        return Math.max(0.3, Math.min(2.0, f));
    }

    /** Ticks of the pause this player gets after the ability. */
    static long pause(ServerPlayer player, SkillTree tree, PlayerSkills skills, String id) {
        double share = player.getMaxHealth() <= 0 ? 1.0 : player.getHealth() / player.getMaxHealth();
        double crate = PIERCE.equals(id) || CHAIN.equals(id) || GRENADE.equals(id) || BOIL.equals(id)
                ? (CrateEffects.gainLevel(player, "werkzeugtasche") > 0 ? 1.0 - SkillValues.get("t_tech", "toolbag", 0.1) : 1.0)
                : 1.0;
        return Math.max(20, Math.round(basePause(id) * pauseFactor(tree, skills, share) * crate));
    }

    /** Ticks until the player may use it again; 0 when ready. */
    public static int cooldownLeft(ServerPlayer player, String id, long now) {
        Map<String, Long> own = player == null ? null : READY.get(player.getUUID());
        Long at = own == null ? null : own.get(id);
        return at == null || at <= now ? 0 : (int) (at - now);
    }

    /** Places 2 and 3 ready at once (the Notschalter gain, the superconductor). */
    public static void ready(ServerPlayer player) {
        if (player != null) {
            READY.remove(player.getUUID());
        }
    }

    /** End or start of a night: every ability ready, nothing runs on. */
    public static void reset() {
        READY.clear();
        RUNNING.clear();
        STUNG.clear();
    }

    // ------------------------------------------------------------------ the bar

    /** Its box on the ability bar. */
    public static UltState.Slot slot(ServerPlayer player, SkillTree tree, PlayerSkills skills, String id, long now) {
        UltState.Slot slot = new UltState.Slot();
        slot.id = id;
        slot.icon = icon(id);
        slot.name = "nightsiege.ability." + id;
        slot.sub = "nightsiege.ability." + id + ".sub";
        if (PIERCE.equals(id)) {
            slot.subArgs.add(String.valueOf(Math.round(val(PIERCE, "targets", 3))));   // Tor 4c.6: the text reads the value
        }
        slot.cooldownTicks = cooldownLeft(player, id, now);
        slot.totalTicks = (int) pause(player, tree, skills, id);
        return slot;
    }

    static String icon(String id) {
        return switch (id) {
            case PIERCE -> "minecraft:spectral_arrow";
            case SUPPRESS -> "minecraft:dispenser";
            case CHAIN -> "minecraft:lightning_rod";
            case STATIC -> "minecraft:copper_bulb";
            case GRENADE -> "minecraft:slime_ball";
            case TARGET -> "minecraft:target";
            case BOIL -> "minecraft:blaze_powder";
            default -> "minecraft:barrier";
        };
    }

    // ------------------------------------------------------------------ firing

    /** A bar key of an engineer on one of these abilities. True when it was one of them (fired or not). */
    static boolean fire(ServerPlayer player, TrapService traps, String id) {
        if (!isOne(id)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        SkillTree tree = traps.tree();
        PlayerSkills skills = traps.skillsOf(player);
        boolean creative = player.getAbilities().instabuild;
        if (!creative && (skills.clazz() != SkillClass.ENGINEER || !of(tree.subclassOf(skills)).contains(id))) {
            return true;
        }
        SiegeManager m = SiegeManager.get();
        if (!creative && (m == null || !m.isDefending(player))) {
            bar(player, Component.translatable("nightsiege.msg.escape_fight_only"));
            return true;
        }
        long now = level.getGameTime();
        int left = cooldownLeft(player, id, now);
        if (left > 0 && !creative) {
            bar(player, Component.translatable("nightsiege.msg.tech_wait",
                    Component.translatable("nightsiege.ability." + id), (left + 19) / 20));
            return true;
        }
        Vec3 seat = traps.seatTop(player);
        boolean done = switch (id) {
            case PIERCE -> pierce(level, player, traps, tree, skills, seat, now);
            case SUPPRESS -> suppress(level, player, traps, tree, skills, seat, now);
            case CHAIN -> chain(level, player, traps, tree, skills, seat, now);
            case STATIC -> staticField(level, player, traps, tree, skills, seat, now);
            case GRENADE -> grenade(level, player, traps, tree, skills, seat, now);
            case TARGET -> target(level, player, traps, tree, skills, seat, now);
            case BOIL -> boil(level, player, traps, tree, skills, seat, now);
            default -> false;
        };
        if (done) {
            READY.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(id, now + pause(player, tree, skills, id));
        }
        return true;
    }

    /** The seat's bonus on reach (+50 %). */
    private static double seatReach(Vec3 seat) {
        return seat == null ? 1.0 : SkillValues.get("t_tech", "seatreach", 1.5);
    }

    /**
     * Chef 09.11. 05:35 (no mortar seat): sitting in an arrow turret or a tesla coil, the demolition
     * expert's grenade and the alchemist's potion fly +50 % further. seatKind from TrapService.seatKind.
     */
    public static double seatThrow(String seatKind) {
        return TrapKind.TURRET.id().equals(seatKind) || TrapKind.TESLA.id().equals(seatKind)
                ? SkillValues.get("t_tech", "seatreach", 1.5) : 1.0;
    }

    /** Fernsteuerung: more blocks for every throw and beam. */
    private static double extraReach(SkillTree tree, PlayerSkills skills) {
        return tree.totalBonus(SkillEffect.REMOTE_RANGE, skills);
    }

    /** Überspannung: Durchschlag and Kettenblitz hit harder. */
    private static double overvolt(PlayerSkills skills) {
        return SkillTree.has(skills, "e_overvolt") ? 1.0 + SkillValues.get("e_overvolt", "dmg", 0.4) : 1.0;
    }

    /** Zielsucher: what an ability hits stays marked (more damage, the towers take it first). */
    private static void seek(TrapService traps, ServerPlayer player, SkillTree tree, PlayerSkills skills, LivingEntity hit, long now) {
        double bonus = tree.totalBonus(SkillEffect.SEEKER, skills);
        if (bonus > 0) {
            traps.markFor(player, hit, bonus, now + Math.round(SkillValues.get("e_seeker", "time", 160)));
        }
    }

    private static Vec3 origin(ServerPlayer player, Vec3 seat) {
        return seat != null ? seat : player.getEyePosition().subtract(0, 0.2, 0);
    }

    private static DamageSource source(ServerLevel level, ServerPlayer player) {
        return level.damageSources().playerAttack(player);
    }

    private static void hurt(ServerLevel level, ServerPlayer player, LivingEntity victim, double damage) {
        victim.invulnerableTime = 0;
        victim.hurtServer(level, source(level, player), (float) damage);
    }

    // ----- Schütze

    /** Durchschlag: one bolt through up to 5 monsters in the line of sight, 2.5× a turret arrow. */
    private static boolean pierce(ServerLevel level, ServerPlayer player, TrapService traps, SkillTree tree,
                                  PlayerSkills skills, Vec3 seat, long now) {
        double range = (val(PIERCE, "range", 32) + extraReach(tree, skills)) * seatReach(seat);
        Vec3 from = origin(player, seat);
        Vec3 to = player.getEyePosition().add(player.getLookAngle().scale(range));
        if (seat != null) {
            to = from.add(to.subtract(from).normalize().scale(range));
        }
        // Prüfer-Fund 4b: the bolt stops at the first solid block, not through walls.
        HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() == HitResult.Type.BLOCK) {
            to = wall.getLocation();
        }
        final Vec3 a = from;
        final Vec3 b = to;
        List<LivingEntity> line = new ArrayList<>();
        for (LivingEntity e : TrapService.enemies(level, new AABB(a, b).inflate(1.0))) {
            if (e.getBoundingBox().inflate(0.4).clip(a, b).isPresent()) {
                line.add(e);
            }
        }
        line.sort(Comparator.comparingDouble(e -> e.distanceToSqr(a)));
        int most = (int) Math.round(val(PIERCE, "targets", 3));
        double damage = traps.turretArrow(player) * val(PIERCE, "dmg", 2.5) * overvolt(skills);
        int hit = 0;
        for (LivingEntity e : line) {
            if (hit >= most) {
                break;
            }
            hurt(level, player, e, damage);
            seek(traps, player, tree, skills, e, now);
            hit++;
        }
        trail(level, a, b, ParticleTypes.CRIT, 0.6);
        level.playSound(null, BlockPos.containing(a), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.7f);
        bar(player, Component.translatable("nightsiege.msg.tech_pierce", hit));
        return true;
    }

    /** Sperrfeuer: the own arrow turrets around fire faster, and the gunner fires a stream of bolts. */
    private static boolean suppress(ServerLevel level, ServerPlayer player, TrapService traps, SkillTree tree,
                                    PlayerSkills skills, Vec3 seat, long now) {
        long time = Math.round((val(SUPPRESS, "time", 80) + tree.totalBonus(SkillEffect.OVERCHARGE_TIME, skills))
                * (CrateEffects.gainLevel(player, "munitionskiste") > 0 ? 1.5 : 1.0));
        double radius = val(SUPPRESS, "radius", 16) * seatReach(seat)
                * (SkillTree.has(skills, "e_network") ? 1.0 + SkillValues.get("e_network", "share", 0.5) : 1.0);
        int towers = traps.rush(level, player, seat != null ? seat : player.position(), radius, now + time);
        RUNNING.add(new Running(SUPPRESS, player.getUUID(), SiegeManager.dimensionName(level), null, now + time, now, 0));
        level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 1.0f, 0.6f);
        bar(player, Component.translatable("nightsiege.msg.tech_suppress", towers, (time + 19) / 20));
        return true;
    }

    // ----- Elektriker

    /** Kettenblitz: the monster looked at, then from monster to monster (4, in the seat 6, Leitstrahl +2). */
    private static boolean chain(ServerLevel level, ServerPlayer player, TrapService traps, SkillTree tree,
                                 PlayerSkills skills, Vec3 seat, long now) {
        double range = (val(CHAIN, "range", 16) + extraReach(tree, skills)) * seatReach(seat);
        LivingEntity first = CombatEffects.lookTarget(level, player, range, true);   // not through walls (Prüfer-Fund 4b)
        if (first == null) {
            bar(player, Component.translatable("nightsiege.msg.tech_no_target"));
            return false;
        }
        int most = (int) Math.round(seat != null ? val(CHAIN, "seattargets", 6) : val(CHAIN, "targets", 4));
        if (SkillTree.has(skills, "e_beacon")) {
            most += (int) Math.round(SkillValues.get("e_beacon", "targets", 2));
        }
        double jump = val(CHAIN, "jump", 6);
        double damage = val(CHAIN, "dmg", 6) * TrapService.levelScale() * overvolt(skills);
        List<LivingEntity> hit = new ArrayList<>();
        Vec3 from = origin(player, seat);
        LivingEntity next = first;
        while (next != null && hit.size() < most) {
            Vec3 at = next.position().add(0, next.getBbHeight() * 0.6, 0);
            trail(level, from, at, ParticleTypes.ELECTRIC_SPARK, 0.4);
            hurt(level, player, next, damage);
            seek(traps, player, tree, skills, next, now);
            hit.add(next);
            from = at;
            LivingEntity near = null;
            for (LivingEntity e : TrapService.enemies(level, next.getBoundingBox().inflate(jump))) {
                if (!hit.contains(e) && e.distanceTo(next) <= jump && (near == null || e.distanceToSqr(next) < near.distanceToSqr(next))
                        && next.hasLineOfSight(e)) {
                    near = e;
                }
            }
            next = near;
        }
        level.playSound(null, first.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.7f, 1.7f);
        bar(player, Component.translatable("nightsiege.msg.tech_chain", hit.size()));
        return true;
    }

    /** Statikfeld: a zone on the spot looked at, slows and stings every second (Netzwerk: also at own traps near it). */
    private static boolean staticField(ServerLevel level, ServerPlayer player, TrapService traps, SkillTree tree,
                                       PlayerSkills skills, Vec3 seat, long now) {
        double reach = (val(STATIC, "range", 14) + extraReach(tree, skills)) * seatReach(seat);
        Vec3 spot = TrapService.throwSpot(level, player, reach);
        if (spot == null) {
            bar(player, Component.translatable("nightsiege.msg.zone_far", (int) Math.round(reach)));
            return false;
        }
        double radius = seat != null ? val(STATIC, "seatradius", 6) : val(STATIC, "radius", 4);
        long time = Math.round(val(STATIC, "time", 120) + tree.totalBonus(SkillEffect.OVERCHARGE_TIME, skills));
        String dim = SiegeManager.dimensionName(level);
        RUNNING.add(new Running(STATIC, player.getUUID(), dim, spot, now + time, now, radius));
        int more = 0;
        if (tree.totalBonus(SkillEffect.NETWORK, skills) > 0) {
            long half = Math.round(time * SkillValues.get("e_network", "share", 0.5));
            for (Vec3 trap : traps.ownTrapsNear(level, player, spot, SkillValues.get("e_network", "radius", 6))) {
                RUNNING.add(new Running(STATIC, player.getUUID(), dim, trap, now + half, now, radius * 0.75));
                more++;
            }
        }
        level.playSound(null, BlockPos.containing(spot), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.9f);
        bar(player, Component.translatable("nightsiege.msg.tech_static", (time + 19) / 20, more));
        return true;
    }

    // ----- Sprengmeister

    /** Haftgranate: sticks where it lands and goes off after a moment; from a seat it flies +50 % further. */
    private static boolean grenade(ServerLevel level, ServerPlayer player, TrapService traps, SkillTree tree,
                                   PlayerSkills skills, Vec3 seat, long now) {
        double reach = val(GRENADE, "range", 14) * seatThrow(traps.seatKind(player)) + extraReach(tree, skills);
        Vec3 spot = TrapService.throwSpot(level, player, reach);
        if (spot == null) {
            bar(player, Component.translatable("nightsiege.msg.zone_far", (int) Math.round(reach)));
            return false;
        }
        long fuse = Math.round(val(GRENADE, "fuse", 20));
        RUNNING.add(new Running(GRENADE, player.getUUID(), SiegeManager.dimensionName(level), spot, now + fuse, now, 0));
        trail(level, origin(player, seat), spot, ParticleTypes.SMOKE, 0.8);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    /** Zielpunkt: the own mortars that reach the spot looked at fire there, plus one small salvo. */
    private static boolean target(ServerLevel level, ServerPlayer player, TrapService traps, SkillTree tree,
                                  PlayerSkills skills, Vec3 seat, long now) {
        double reach = val(TARGET, "range", 48) * seatReach(seat) + extraReach(tree, skills);
        HitResult hit = player.pick(reach, 0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            bar(player, Component.translatable("nightsiege.msg.zone_far", (int) Math.round(reach)));
            return false;
        }
        Vec3 spot = hit.getLocation();
        int shells = (int) Math.round(val(TARGET, "shells", 2));
        if (CrateEffects.gainLevel(player, "munitionskiste") > 0) {
            shells += 1;
        }
        double damage = 1.0 + SkillValues.get("e_detonator", "dmg", 0.15) * tree.totalBonus(SkillEffect.DETONATOR, skills);
        int[] done = traps.targetPoint(level, player, spot, shells, damage);
        if (done[0] <= 0) {
            bar(player, Component.translatable("nightsiege.msg.mortar_aim_far"));
            return false;
        }
        bar(player, Component.translatable("nightsiege.msg.tech_target", done[0], done[1]));
        return true;
    }

    // ----- Alchemist

    /** Siedepunkt: the next potion flies at once and covers twice the area; own potion throwers around throw big now. */
    private static boolean boil(ServerLevel level, ServerPlayer player, TrapService traps, SkillTree tree,
                                PlayerSkills skills, Vec3 seat, long now) {
        double area = val(BOIL, "area", 2.0) * (1.0 + SkillValues.get("e_detonator", "area", 0.1)
                * tree.totalBonus(SkillEffect.DETONATOR, skills));
        traps.boilNext(player, now + Math.round(val(BOIL, "time", 200)), area);
        int towers = traps.boilBrewers(level, player, seat != null ? seat : player.position(),
                val(BOIL, "radius", 16) * seatReach(seat), area);
        level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 1.3f);
        bar(player, Component.translatable("nightsiege.msg.tech_boil", towers));
        return true;
    }

    // ------------------------------------------------------------------ what runs on

    private static final class Running {
        final String id;
        final UUID player;
        final String dimension;
        final Vec3 at;
        final long until;
        long next;
        final double radius;

        Running(String id, UUID player, String dimension, Vec3 at, long until, long next, double radius) {
            this.id = id;
            this.player = player;
            this.dimension = dimension;
            this.at = at;
            this.until = until;
            this.next = next;
            this.radius = radius;
        }
    }

    /** Every server tick (from the trap service): fields sting, bursts fire, grenades go off. */
    static void tick(MinecraftServer server, TrapService traps) {
        if (RUNNING.isEmpty()) {
            return;
        }
        Map<ServerPlayer, List<Running>> due = new HashMap<>();
        for (Iterator<Running> it = RUNNING.iterator(); it.hasNext(); ) {
            Running r = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(r.player);
            ServerLevel level = player != null && player.level() instanceof ServerLevel l
                    && SiegeManager.dimensionName(l).equals(r.dimension) ? l : null;
            if (level == null || !player.isAlive()) {
                it.remove();   // logged out, died, or left the world: it ends (night end: reset())
                continue;
            }
            long now = level.getGameTime();
            switch (r.id) {
                case GRENADE -> {
                    if (now >= r.until) {
                        blast(level, player, traps, r.at, now);
                        it.remove();
                    } else if (now % 5 == 0) {
                        level.sendParticles(ParticleTypes.SMALL_FLAME, r.at.x, r.at.y + 0.2, r.at.z, 3, 0.1, 0.1, 0.1, 0.01);
                    }
                }
                case STATIC -> {
                    if (now > r.until) {
                        it.remove();
                    } else if (now >= r.next) {
                        r.next = now + STING_EVERY;
                        due.computeIfAbsent(player, k -> new ArrayList<>()).add(r);   // stung together below
                    }
                }
                case SUPPRESS -> {
                    if (now > r.until) {
                        it.remove();
                    } else if (now >= r.next) {
                        r.next = now + Math.max(2, Math.round(val(SUPPRESS, "every", 10)));
                        bolt(level, player, traps);
                    }
                }
                default -> it.remove();
            }
        }
        for (Map.Entry<ServerPlayer, List<Running>> e : due.entrySet()) {
            if (e.getKey().level() instanceof ServerLevel level) {
                sting(level, e.getKey(), e.getValue());
            }
        }
    }

    private static void blast(ServerLevel level, ServerPlayer player, TrapService traps, Vec3 at, long now) {
        PlayerSkills skills = traps.skillsOf(player);
        double damage = val(GRENADE, "dmg", 6) * TrapService.levelScale()
                * (1.0 + SkillValues.get("e_detonator", "dmg", 0.15) * traps.tree().totalBonus(SkillEffect.DETONATOR, skills));
        double radius = val(GRENADE, "radius", 3) * traps.tree().blastScale(skills);   // Tor 4c.6: Sprengkraft as its text says
        long mark = now + Math.round(val(GRENADE, "mark", 160));
        for (LivingEntity e : TrapService.around(level, at, radius)) {
            hurt(level, player, e, damage);
            traps.markFor(player, e, 0.0, mark);   // the towers take it first (and Zielsprengung goes off on it)
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.5, 0.2, 0.5, 0);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    /**
     * One pulse of all due Statikfelder of a player (Prüfer-Fund 4b): one entity query over all of them,
     * each monster stung at most once per pulse ({@link #STUNG}, also across casts), the sparks shared out.
     */
    private static void sting(ServerLevel level, ServerPlayer player, List<Running> fields) {
        double damage = val(STATIC, "dmg", 1) * TrapService.levelScale();
        AABB box = null;
        for (Running r : fields) {
            AABB one = new AABB(r.at, r.at).inflate(r.radius, 2.0, r.radius);
            box = box == null ? one : box.minmax(one);
        }
        long now = level.getGameTime();
        for (LivingEntity e : TrapService.enemies(level, box)) {
            boolean inside = false;
            for (Running r : fields) {
                if (e.distanceToSqr(r.at) <= r.radius * r.radius + 4.0) {   // as TrapService.around
                    inside = true;
                    break;
                }
            }
            if (!inside || !STUNG.tryHit(player.getUUID(), e.getId(), now, STING_EVERY)) {
                continue;
            }
            hurt(level, player, e, damage);
            if (!BossService.isBossOrPart(e)) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
            }
        }
        int sparks = Math.max(6, 24 / fields.size());
        for (Running r : fields) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, r.at.x, r.at.y + 0.3, r.at.z, sparks, r.radius * 0.45, 0.2, r.radius * 0.45, 0.05);
        }
    }

    /** One bolt of the Sperrfeuer: at the monster looked at, else the nearest in reach. */
    private static void bolt(ServerLevel level, ServerPlayer player, TrapService traps) {
        Vec3 seat = traps.seatTop(player);
        double reach = (val(SUPPRESS, "radius", 16) + extraReach(traps.tree(), traps.skillsOf(player))) * seatReach(seat);
        LivingEntity target = CombatEffects.lookTarget(level, player, reach);
        Vec3 from = origin(player, seat);
        if (target == null) {
            for (LivingEntity e : TrapService.around(level, player.position(), reach)) {
                if (target == null || e.distanceToSqr(from) < target.distanceToSqr(from)) {
                    target = e;
                }
            }
        }
        if (target == null) {
            return;
        }
        hurt(level, player, target, traps.turretArrow(player) * val(SUPPRESS, "dmg", 0.6));
        trail(level, from, target.position().add(0, target.getBbHeight() * 0.6, 0), ParticleTypes.CRIT, 0.8);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5f, 1.4f);
    }

    // ------------------------------------------------------------------ helpers

    private static void trail(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions particle, double step) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.min(64, Math.max(2, d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static void bar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }

    /**
     * For the bots (bench, team sim), like an average player: with an own arrow turret or tesla coil
     * close by he sits in it (and holds fire); each ability fires when it is ready and the target is in
     * its reach, aimed at the body (Durchschlag, Kettenblitz) or at the ground under it (fields, Zielpunkt).
     */
    static void botUse(SiegeManager m, ServerPlayer player, LivingEntity aim) {
        if (aim == null || !aim.isAlive() || !(player.level() instanceof ServerLevel level)) {
            return;   // no target: an average player keeps his abilities
        }
        TrapService traps = m.trapService();
        botSeat(level, traps, player, aim);
        Vec3 seat = traps.seatTop(player);
        double dist = origin(player, seat).distanceTo(aim.position().add(0, aim.getBbHeight() * 0.5, 0));
        List<UltState.Slot> slots = m.abilitySlots(player);
        for (int i = 0; i < slots.size(); i++) {
            String id = slots.get(i).id;
            if (!isOne(id) || slots.get(i).cooldownTicks > 0 || dist > botReach(id) * seatReach(seat)) {
                continue;
            }
            boolean ground = STATIC.equals(id) || GRENADE.equals(id) || TARGET.equals(id);
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    ground ? aim.position().add(0, -0.1, 0) : aim.position().add(0, aim.getBbHeight() * 0.5, 0));
            m.onCoreAction(player, "slot:" + i);
        }
    }

    /** The reach a bot waits for before it fires (before the seat; Fernsteuerung left out). */
    private static double botReach(String id) {
        return switch (id) {
            case PIERCE -> val(PIERCE, "range", 32);
            case CHAIN -> val(CHAIN, "range", 16);
            case STATIC -> val(STATIC, "range", 14);
            case GRENADE -> val(GRENADE, "range", 14);
            case TARGET -> val(TARGET, "range", 48);
            case SUPPRESS -> val(SUPPRESS, "radius", 16);
            case BOIL -> val(BOIL, "radius", 16);
            default -> 0;
        };
    }

    /** A bot sits in an own arrow turret or tesla coil within 5 blocks, when the target is within 24 of it. */
    private static void botSeat(ServerLevel level, TrapService traps, ServerPlayer player, LivingEntity aim) {
        if (traps.seatTop(player) == null) {
            for (Vec3 at : traps.ownTrapsNear(level, player, player.position(), 5.0)) {
                BlockPos pos = BlockPos.containing(at);
                if (TowerBlock.mannable(ModContent.trapOf(level.getBlockState(pos))) && at.distanceTo(aim.position()) <= 24.0) {
                    traps.tryMount(level, pos, player, net.minecraft.world.item.ItemStack.EMPTY);
                    if (traps.seatTop(player) != null) {
                        break;
                    }
                }
            }
        }
        if (traps.seatTop(player) != null) {
            traps.onManAction(player, true);   // a seated player holds fire
        }
    }
}
