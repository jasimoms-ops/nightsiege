package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
import com.mki.nightsiege.core.WarriorHudData;
import com.mki.nightsiege.core.WarriorSpiritMath;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kampfgeist and the combo finishers of the warriors (Simon 05.11.: the warrior "feels very vanilla
 * compared to all other classes"; "ja alles super bau das so").
 *
 * <ul>
 *   <li><b>Kampfgeist</b>: a meter of 0–100 per warrior. A melee hit on a monster, a kill and a hit
 *       taken from a monster fill it; after a few seconds without fighting it falls. It starts at 0
 *       every night and whenever the kit is put on. From 50 on F can be used ({@link WarriorArts}).</li>
 *   <li><b>Chain</b>: every 4th melee hit in a row is a <b>finisher</b>; the chain breaks after 2 s
 *       without a hit. At a full meter the finisher is <b>empowered</b>: double effect for 30
 *       Kampfgeist. Klingenwirbel (blademaster), Blutiger Hieb (berserker), Spalter (axe fighter),
 *       Lichtschlag (paladin).</li>
 * </ul>
 *
 * A swing counts once (the sweep's other monsters do not add to it) and only when it was charged
 * (attack strength at least {@code w_spirit.charge}), so fast clicking fills nothing. The finishers'
 * hits count in the statistics as "finisher:&lt;id&gt;". All numbers are skill values
 * ({@link WarriorSpiritMath}).
 */
public final class WarriorSpirit {

    private static final Identifier CLEAVE_ARMOUR = ModContent.id("spalter_armour");
    private static final DustParticleOptions BLOOD = new DustParticleOptions(0x9A0A0A, 1.2f);

    private static final Map<UUID, Meter> METERS = new HashMap<>();
    private static final Map<UUID, Swing> SWINGS = new HashMap<>();
    private static final List<Bleed> BLEEDS = new ArrayList<>();
    private static final Map<UUID, Crack> CRACKS = new HashMap<>();
    private static CombatEffects combat;
    /** The statistics' name of the hit that lands right now (null: none of ours). */
    private static String source;

    // For the tests and the class simulation.
    private static int finishers;
    private static int empoweredFinishers;
    private static String lastFinisher = "";
    private static boolean lastEmpowered;
    private static double finisherHeal;
    private static double finisherDamage;

    private WarriorSpirit() {
    }

    /** One warrior's meter and chain. */
    private static final class Meter {
        double value;
        long lastFight = Long.MIN_VALUE / 4;
        int chain;
        long lastHit = Long.MIN_VALUE / 4;
        int fin;
        boolean finEmp;
        long sentAt = Long.MIN_VALUE / 4;
        boolean dirty;
    }

    /** The last swing of a player (Player.attack): when, how charged, and whether it was counted. */
    private static final class Swing {
        long tick = Long.MIN_VALUE;
        float charge;
        boolean counted;
    }

    /** Blutiger Hieb's bleeding: a share of the hit, once a second. */
    private static final class Bleed {
        final ServerLevel level;
        final UUID victim;
        final UUID owner;
        final float perSecond;
        final long end;
        long next;

        Bleed(ServerLevel level, UUID victim, UUID owner, float perSecond, long next, long end) {
            this.level = level;
            this.victim = victim;
            this.owner = owner;
            this.perSecond = perSecond;
            this.next = next;
            this.end = end;
        }
    }

    /** Spalter's broken armour on a monster, until it comes back. */
    private record Crack(ServerLevel level, long until) {
    }

    /** The combat effects the finishers' hits go through (no on-hit procs, like every bonus hit). */
    static void use(CombatEffects effects) {
        combat = effects;
    }

    static CombatEffects combat() {
        return combat;
    }

    // ------------------------------------------------------------------ the statistics' names

    /** "finisher:…" or "faehigkeit:…" while one of these hits lands, else null (StatsRecorder, MeleeSim). */
    public static String statsSource() {
        return source;
    }

    /**
     * Deals one hit of a finisher or an F ability under its statistics name; returns the life it took.
     * {@code followUp}: the amount comes from a hit that already had the class factor (finishers); else
     * the factor applies (the F abilities' own hits).
     */
    static float strike(ServerLevel level, LivingEntity victim, DamageSource src, float amount, String label, boolean followUp) {
        if (victim == null || !victim.isAlive() || amount <= 0f) {
            return 0f;
        }
        float before = victim.getHealth();
        String outer = source;
        source = label;
        try {
            if (combat == null) {
                victim.invulnerableTime = 0;
                victim.hurtServer(level, src, amount);
            } else if (followUp) {
                combat.followUpHit(level, victim, src, amount);
            } else {
                combat.bonusHit(level, victim, src, amount);
            }
        } finally {
            source = outer;
        }
        return Math.max(0f, before - (victim.isAlive() ? victim.getHealth() : 0f));
    }

    // ------------------------------------------------------------------ the meter

    private static Meter meter(ServerPlayer player) {
        return METERS.computeIfAbsent(player.getUUID(), k -> new Meter());
    }

    /** This warrior's Kampfgeist now (0 without a meter). */
    public static double value(ServerPlayer player) {
        Meter m = player == null ? null : METERS.get(player.getUUID());
        return m == null ? 0.0 : m.value;
    }

    /** Hits of the running chain (0 when none or broken). */
    public static int chain(ServerPlayer player) {
        Meter m = player == null ? null : METERS.get(player.getUUID());
        return m == null ? 0 : WarriorSpiritMath.chainNow(m.chain, m.lastHit, player.level().getGameTime());
    }

    /** Sets the meter (the tests, the bots' check). */
    public static void set(ServerPlayer player, double value) {
        Meter m = meter(player);
        m.value = WarriorSpiritMath.add(0.0, value);
        m.lastFight = player.level().getGameTime();
        send(player, m);
    }

    /** A new night (or its end): every meter and chain back to 0, bleeding and broken armour gone. */
    public static void reset() {
        for (Meter m : METERS.values()) {
            m.value = 0.0;
            m.chain = 0;
            m.dirty = true;
        }
        SWINGS.clear();
        BLEEDS.clear();
        for (Map.Entry<UUID, Crack> e : new ArrayList<>(CRACKS.entrySet())) {
            uncrack(e.getValue().level(), e.getKey());
        }
        CRACKS.clear();
    }

    /** The kit was put on: this warrior starts at 0. */
    public static void reset(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Meter m = METERS.get(player.getUUID());
        if (m != null) {
            m.value = 0.0;
            m.chain = 0;
            send(player, m);
        }
    }

    static void forget(UUID player) {
        METERS.remove(player);
        SWINGS.remove(player);
    }

    /** F was used: its price. */
    static void spend(ServerPlayer player, double cost, long now) {
        Meter m = meter(player);
        m.value = Math.max(0.0, m.value - cost);
        m.lastFight = now;
        send(player, m);
    }

    /** Player.attack began (mixin): when and how charged, for {@link #onSwing}. */
    public static void noteSwing(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        Swing s = SWINGS.computeIfAbsent(sp.getUUID(), k -> new Swing());
        s.tick = sp.level().getGameTime();
        s.charge = sp.getAttackStrengthScale(0.5f);
        s.counted = false;
    }

    /**
     * A melee hit of a warrior on a monster with the weapon of his way (CombatEffects; a killing blow
     * too). {@code hit} is the damage of the hit. Fills the meter, counts the chain and fires the finisher.
     */
    static void onSwing(CombatEffects effects, ServerLevel level, ServerPlayer player, PlayerSkills skills, String sub,
                        LivingEntity victim, float hit, long now) {
        if (combat == null) {
            combat = effects;
        }
        if (skills == null || skills.clazz() != SkillClass.WARRIOR || !WarriorSpiritMath.isWarriorSub(sub)) {
            return;
        }
        Swing s = SWINGS.get(player.getUUID());
        if (s == null || s.tick != now || s.counted || s.charge + 1.0e-4 < WarriorSpiritMath.minCharge()) {
            return;   // not a swing of this tick, its other monsters (the sweep), or an uncharged click
        }
        s.counted = true;
        Meter m = meter(player);
        m.value = WarriorSpiritMath.add(m.value, WarriorSpiritMath.gainHit());
        m.lastFight = now;
        m.chain = WarriorSpiritMath.chainAfterHit(m.chain, m.lastHit, now);
        m.lastHit = now;
        if (WarriorSpiritMath.isFinisher(m.chain)) {
            finisher(level, player, sub, victim, hit, m, now);
        }
        send(player, m);
    }

    /** A warrior killed a monster. */
    static void onKill(ServerPlayer player, long now) {
        Meter m = meter(player);
        m.value = WarriorSpiritMath.add(m.value, WarriorSpiritMath.gainKill());
        m.lastFight = now;
        send(player, m);
    }

    /** A monster hit a warrior. */
    static void onHurt(ServerPlayer player, long now) {
        Meter m = meter(player);
        m.value = WarriorSpiritMath.add(m.value, WarriorSpiritMath.gainHurt());
        m.lastFight = now;
        send(player, m);
    }

    // ------------------------------------------------------------------ the finishers

    private static void finisher(ServerLevel level, ServerPlayer player, String sub, LivingEntity victim, float hit,
                                 Meter m, long now) {
        boolean emp = WarriorSpiritMath.empowers(m.value);
        double factor = emp ? WarriorSpiritMath.empowerFactor() : 1.0;
        if (emp) {
            m.value = Math.max(0.0, m.value - WarriorSpiritMath.empowerCost());
        }
        String id = WarriorSpiritMath.finisherId(sub);
        String label = "finisher:" + id;
        switch (sub) {
            case SkillTree.SUB_BERSERKER -> bloodyStrike(level, player, victim, hit, factor, label, now);
            case SkillTree.SUB_AXE -> {
                if (victim.isAlive()) {
                    cleave(level, victim, factor, now);
                }
            }
            case SkillTree.SUB_PALADIN -> lightStrike(level, player, factor);
            default -> bladeWhirl(level, player, victim, hit, factor, label);
        }
        finishers++;
        if (emp) {
            empoweredFinishers++;
        }
        lastFinisher = id;
        lastEmpowered = emp;
        m.fin++;
        m.finEmp = emp;
        level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), emp ? 26 : 12, 0.4, 0.4, 0.4, 0.3);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                1.0f, emp ? 0.6f : 0.85f);
        if (emp) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, victim.getX(), victim.getY(0.6), victim.getZ(), 20, 0.5, 0.5, 0.5, 0.2);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.6f);
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.finisher_empowered",
                    Component.translatable("nightsiege.finisher." + id)));
        }
    }

    /** Klingenwirbel: a short spin, a share of the hit on every monster around the warrior. */
    private static void bladeWhirl(ServerLevel level, ServerPlayer player, LivingEntity victim, float hit, double factor, String label) {
        double r = SkillValues.get("w_combo", "fin_radius", 2.5);
        float dmg = (float) (hit * SkillValues.get("w_combo", "fin_share", 0.6) * factor);
        DamageSource src = level.damageSources().playerAttack(player);
        if (dmg > 0.05f) {
            for (LivingEntity e : CombatEffects.enemies(level, player.position(), r, 1.5)) {
                if (e != victim && e.isAlive()) {
                    finisherDamage += strike(level, e, src, dmg, label, true);
                }
            }
        }
        Vec3 c = player.position();
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Math.cos(a) * r * 0.7, c.y + 1.0, c.z + Math.sin(a) * r * 0.7,
                    1, 0, 0, 0, 0);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.25f);
    }

    /** Blutiger Hieb: more damage the less life the berserker has, and the target bleeds. */
    private static void bloodyStrike(ServerLevel level, ServerPlayer player, LivingEntity victim, float hit, double factor,
                                     String label, long now) {
        double life = player.getHealth() / Math.max(1f, player.getMaxHealth());
        float extra = (float) (hit * WarriorSpiritMath.bloodyBonus(life) * factor);
        DamageSource src = level.damageSources().playerAttack(player);
        if (victim.isAlive() && extra > 0.05f) {
            finisherDamage += strike(level, victim, src, extra, label, true);
        }
        int time = (int) Math.max(20, Math.round(SkillValues.get("bloodlust", "fin_bleedtime", 60)));
        double total = hit * SkillValues.get("bloodlust", "fin_bleed", 0.3) * factor;
        if (victim.isAlive() && total > 0.05) {
            UUID id = victim.getUUID();
            BLEEDS.removeIf(b -> b.victim.equals(id));   // a new bleeding takes the old one's place
            BLEEDS.add(new Bleed(level, id, player.getUUID(), (float) (total / Math.max(1, time / 20)), now + 20, now + time));
        }
        level.sendParticles(BLOOD, victim.getX(), victim.getY(0.6), victim.getZ(), 18, 0.35, 0.45, 0.35, 0.0);
    }

    /**
     * Spalter (the axe fighter's finisher, and every hit of his Axtwurf): the target's armour drops by a
     * share for a few seconds and it is knocked down (strongly slowed for a moment).
     */
    static void cleave(ServerLevel level, LivingEntity victim, double factor, long now) {
        long until = now + Math.max(1L, Math.round(SkillValues.get("heavy_blow", "fin_time", 80)));
        double share = WarriorSpiritMath.cleaveArmour(factor);
        AttributeInstance armour = victim.getAttribute(Attributes.ARMOR);
        if (armour != null && share > 0.0) {
            armour.addOrUpdateTransientModifier(new AttributeModifier(CLEAVE_ARMOUR, -share,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            CRACKS.put(victim.getUUID(), new Crack(level, until));
        }
        int slow = (int) Math.round(SkillValues.get("heavy_blow", "fin_slow", 20) * factor);
        if (slow > 0) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, 3));
        }
        double crack = SkillValues.get("heavy_blow", "fin_crack", 0.0) * factor;
        if (crack > 0.0 && combat != null) {
            combat.remoteMark(victim, crack, until, false);   // optional: it takes more damage from everyone
        }
        level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.5), victim.getZ(), 14, 0.35, 0.3, 0.35, 0.15);
        level.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + 0.1, victim.getZ(), 10, 0.4, 0.05, 0.4, 0.02);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.7f, 1.3f);
    }

    /** Lichtschlag: the players around are healed, the undead around burn. */
    private static void lightStrike(ServerLevel level, ServerPlayer player, double factor) {
        double r = SkillValues.get("smite", "fin_radius", 6);
        float heal = (float) (SkillValues.get("smite", "fin_heal", 2) * factor);
        float burn = (float) (SkillValues.get("smite", "fin_burn", 60) * factor / 20.0);
        if (heal > 0f) {
            for (ServerPlayer mate : GuardianEffects.mates(level)) {
                if (!mate.isAlive() || mate.distanceToSqr(player) > r * r) {
                    continue;
                }
                float real;
                if (combat != null) {
                    real = combat.guardian().heal(player, mate, heal);
                } else {
                    real = Math.min(heal, Math.max(0f, mate.getMaxHealth() - mate.getHealth()));
                    mate.heal(real);
                }
                finisherHeal += real;
                if (real > 0f) {
                    level.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 2.0, mate.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
                }
            }
        }
        if (burn > 0f) {
            for (LivingEntity e : CombatEffects.enemies(level, player.position(), r, 3.0)) {
                if (CombatEffects.isUndead(e)) {
                    e.igniteForSeconds(burn);
                }
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 30, r * 0.3, 0.6, r * 0.3, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7f, 1.8f);
    }

    /** True while Spalter's broken armour is on this monster (for the tests). */
    public static boolean cracked(LivingEntity victim) {
        AttributeInstance armour = victim == null ? null : victim.getAttribute(Attributes.ARMOR);
        return armour != null && armour.getModifier(CLEAVE_ARMOUR) != null;
    }

    /** True while this monster bleeds from a Blutiger Hieb (for the tests). */
    public static boolean bleeding(LivingEntity victim) {
        for (Bleed b : BLEEDS) {
            if (victim != null && b.victim.equals(victim.getUUID())) {
                return true;
            }
        }
        return false;
    }

    private static void uncrack(ServerLevel level, UUID id) {
        Entity e = level == null ? null : level.getEntity(id);
        if (e instanceof LivingEntity living) {
            AttributeInstance armour = living.getAttribute(Attributes.ARMOR);
            if (armour != null && armour.getModifier(CLEAVE_ARMOUR) != null) {
                armour.removeModifier(CLEAVE_ARMOUR);
            }
        }
    }

    // ------------------------------------------------------------------ every tick

    /** The meters fall, chains break, the bleeding bleeds, broken armour comes back, the F abilities run. */
    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (!METERS.isEmpty()) {
            for (Map.Entry<UUID, Meter> e : METERS.entrySet()) {
                Meter m = e.getValue();
                double before = m.value;
                m.value = WarriorSpiritMath.decayTick(m.value, now - m.lastFight);
                int chain = WarriorSpiritMath.chainNow(m.chain, m.lastHit, now);
                if (chain != m.chain) {
                    m.chain = chain;
                    m.dirty = true;
                }
                if (m.value != before) {
                    m.dirty = true;
                }
                boolean crossed = WarriorSpiritMath.glows(before) != WarriorSpiritMath.glows(m.value)
                        || WarriorSpiritMath.canUseAbility(before) != WarriorSpiritMath.canUseAbility(m.value);
                if (m.dirty && (now - m.sentAt >= 10 || crossed || m.value <= 0.0)) {
                    ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                    if (p != null) {
                        send(p, m);
                    } else {
                        m.dirty = false;
                    }
                }
            }
        }
        if (!BLEEDS.isEmpty()) {
            List<Bleed> done = new ArrayList<>();
            for (Bleed b : new ArrayList<>(BLEEDS)) {
                if (now < b.next) {
                    continue;
                }
                Entity e = b.level.getEntity(b.victim);
                ServerPlayer owner = server.getPlayerList().getPlayer(b.owner);
                if (!(e instanceof LivingEntity victim) || !victim.isAlive() || owner == null || now > b.end) {
                    done.add(b);
                    continue;
                }
                b.next = now + 20;
                finisherDamage += strike(b.level, victim, b.level.damageSources().playerAttack(owner), b.perSecond,
                        "finisher:" + WarriorSpiritMath.finisherId(SkillTree.SUB_BERSERKER), true);
                b.level.sendParticles(BLOOD, victim.getX(), victim.getY(0.6), victim.getZ(), 6, 0.25, 0.35, 0.25, 0.0);
                if (b.next > b.end) {
                    done.add(b);
                }
            }
            BLEEDS.removeAll(done);
        }
        if (!CRACKS.isEmpty()) {
            Iterator<Map.Entry<UUID, Crack>> it = CRACKS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Crack> e = it.next();
                if (now >= e.getValue().until()) {
                    uncrack(e.getValue().level(), e.getKey());
                    it.remove();
                }
            }
        }
        WarriorArts.tick(server, now);
    }

    // ------------------------------------------------------------------ the client

    /** The meter and the chain to the warrior's client (the bar, the F field, the dots under the crosshair). */
    private static void send(ServerPlayer player, Meter m) {
        m.sentAt = player.level().getGameTime();
        m.dirty = false;
        if (player.connection == null || !ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE)) {
            return;   // a bot of the simulation
        }
        WarriorHudData d = new WarriorHudData();
        d.v = (float) m.value;
        d.max = (float) WarriorSpiritMath.max();
        d.glow = (float) WarriorSpiritMath.glowAt();
        d.cost = (float) WarriorSpiritMath.abilityCost();
        d.chain = WarriorSpiritMath.chainNow(m.chain, m.lastHit, m.sentAt);
        d.hits = WarriorSpiritMath.chainHits();
        d.window = (int) WarriorSpiritMath.chainWindow();
        d.fin = m.fin;
        d.finEmp = m.finEmp;
        d.emp = WarriorSpiritMath.empowers(m.value);
        d.dim = !WarriorSpiritMath.canUseAbility(m.value);
        ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("warrior", Json.toJson(d)));
    }

    // ------------------------------------------------------------------ tests and the simulation

    public static int finishers() {
        return finishers;
    }

    public static int empoweredFinishers() {
        return empoweredFinishers;
    }

    public static String lastFinisher() {
        return lastFinisher;
    }

    public static boolean lastEmpowered() {
        return lastEmpowered;
    }

    public static double finisherHeal() {
        return finisherHeal;
    }

    public static double finisherDamage() {
        return finisherDamage;
    }

    /** A new run of the simulation: the counters from 0. */
    public static void resetCounters() {
        finishers = 0;
        empoweredFinishers = 0;
        lastFinisher = "";
        lastEmpowered = false;
        finisherHeal = 0.0;
        finisherDamage = 0.0;
        WarriorArts.resetCounters();
    }

    /** One line for the simulation's log. */
    public static String counters() {
        return String.format(java.util.Locale.ROOT, "Finisher %d (verstärkt %d, Schaden %.0f, Heilung %.0f), %s",
                finishers, empoweredFinishers, finisherDamage, finisherHeal, WarriorArts.counters());
    }
}
