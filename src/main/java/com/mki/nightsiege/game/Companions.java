package com.mki.nightsiege.game;

import com.mki.nightsiege.core.CrateGains;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The summoner (rebuilt after the playtest of 26 October 2026, Simon's concept): a few
 * companions with one shared mind, and the summoner fights along with the staff.
 *
 * <p><b>One mind for every companion</b> (wolf, iron and snow golem, skeleton guard, raised
 * dead, spirits; only the looks differ): they stay within 10 blocks of their summoner, go
 * first for monsters that attack him or a teammate, otherwise for the nearest monster near
 * him; stuck or too far away they come back to his side. The vanilla minds of the mobs pick
 * nothing any more (their target goals are stopped in {@link #mayTarget}, their wandering
 * goals removed); this class sets every target. Monsters turn on a companion only within a
 * few blocks and only for a few seconds (config companionTauntRadius / companionTauntSeconds).</p>
 *
 * <p><b>The staff</b>: right click is the summoner's own attack (the call strike on the way of
 * the light, the soul bolt on the way of the dead). Way of the light: sneak + right click at a
 * monster sends all companions (the golem taunts and slams), at a block they hold the spot,
 * into the air (or the G key) it calls a wolf or the golem. Way of the dead: sneak + right
 * click casts the chosen spell, G picks the next. The X key with the staff gives the order
 * (attack / hold) on both ways; sneak + X is the second ult.</p>
 */
public final class Companions {

    public static final String TAG = "nightsiege_companion";

    /** What a companion is (decides its slot, its stats and its skills). */
    enum Kind { WOLF, GOLEM, SNOW, SKELETON, SPIRIT, TITAN, MINION, GHOST, MOUNT, HAWK }

    static final class Companion {
        final UUID id;
        final UUID owner;
        final Kind kind;
        final long born;
        /** Game time it vanishes, or -1 for the whole night. */
        long until = -1;
        boolean alpha;
        int rank;
        long nextSlam;
        /** The golem's taunt and slam wait for it to reach the ordered target (until this time). */
        long slamDeadline = -1;
        /** Raised by the army of the dead (no place taken). */
        boolean army;
        /** A corpse of the necromancer's spell: it bursts at its end. */
        boolean burst;
        /** Factor on every hit it deals (the raised dead carry their strength here). */
        double damageMul = 1.0;
        /** For the stuck check: where it stood and when it last got anywhere. */
        Vec3 lastPos;
        long lastMoved;
        ServerLevel lastLevel;

        Companion(UUID id, UUID owner, Kind kind, long born) {
            this.id = id;
            this.owner = owner;
            this.kind = kind;
            this.born = born;
            this.lastMoved = born;
        }
    }

    /** Every living companion by its entity id (static: the damage and target hooks ask without an instance). */
    private static final Map<UUID, Companion> ALL = new HashMap<>();
    private static Companions instance;

    /** A monster that turned on a companion: whom it had before, and until when. */
    private record Taunt(ServerLevel level, UUID before, long until) {
    }

    /** Monsters on a companion right now (they go back to their old target afterwards). */
    private static final Map<UUID, Taunt> TAUNTS = new HashMap<>();
    /** Monsters that just let go of a companion: no new companion target until then. */
    private static final Map<UUID, Long> TAUNT_PAUSE = new HashMap<>();
    /** Monsters in a curse zone (they take more damage) until this game time. */
    private static final Map<UUID, Long> CURSED = new HashMap<>();
    /** Above zero while this class sets a target itself (every other target change of a companion is refused). */
    private static int steering;
    /** Above zero while a spell deals its damage (the "companions and spells" share of the bench and the log). */
    private static int spell;

    /** Outside a night (creative tests) companions go after this many ticks. */
    private static final int TEST_LIFETIME = 3600;

    /** A named skill number ({@link SkillValues}); the fallback is the built-in value. */
    private static double v(String node, String key, double fallback) {
        return SkillValues.get(node, key, fallback);
    }

    private final SkillTree tree;
    private final Function<ServerPlayer, PlayerSkills> skillsFor;
    private final Supplier<SiegeConfig> config;
    private GuardianEffects.Credit credit = (player, amount, from) -> {
    };
    /** Owner → game time the staff can call again (also shortened by Seelenernte). */
    private final Map<UUID, Long> nextCall = new HashMap<>();
    /** Owner → game time both charges of the staff's attack are back (Doppelstab: two charges). */
    private final Map<UUID, Long> attackReady = new HashMap<>();
    /** Owner → end of the call strike's boost on the companions. */
    private final Map<UUID, Long> boostUntil = new HashMap<>();
    /** Owner → the order of the staff (attack this monster, or hold this spot). */
    private final Map<UUID, Order> orders = new HashMap<>();
    /** Owner → the spell the G key picked (index into the subclass's spells). */
    private final Map<UUID, Integer> spellIndex = new HashMap<>();
    /** Owner → game time the next spell may go (a short pause against double clicks). */
    private final Map<UUID, Long> nextSpell = new HashMap<>();
    /** Owner → game time the skeleton guard may rise again after it fell. */
    private final Map<UUID, Long> nextBones = new HashMap<>();
    /** Owner → end of the current howl (wolves faster and stronger). */
    private final Map<UUID, Long> howlUntil = new HashMap<>();
    private final Map<UUID, Long> nextHowl = new HashMap<>();
    /** Owner → game time the pack leader heals the pack again. */
    private final Map<UUID, Long> nextLead = new HashMap<>();

    /** The order of the staff: a monster to attack or a spot to hold, until a time. */
    private record Order(UUID target, Vec3 hold, long until) {
    }

    /** A monster that fell tonight: it can be raised for half a minute. */
    record Corpse(String typeId, ServerLevel level, Vec3 pos, long time, boolean elite) {
    }

    /** A homing shot of the staff: the soul bolt (the summoner's own) or a ghost of the swarm (a spell). */
    private static final class Missile {
        final UUID owner;
        final ServerLevel level;
        Vec3 pos;
        UUID target;
        final double speed;
        final float damage;
        final boolean ghost;
        /** Seelenbrand: the first ghost of a swarm gives the soul back. */
        final boolean refund;
        int life = 80;

        Missile(UUID owner, ServerLevel level, Vec3 pos, UUID target, double speed, float damage, boolean ghost, boolean refund) {
            this.owner = owner;
            this.level = level;
            this.pos = pos;
            this.target = target;
            this.speed = speed;
            this.damage = damage;
            this.ghost = ghost;
            this.refund = refund;
        }
    }

    /** A curse zone of the necromancer. */
    private record Zone(UUID owner, ServerLevel level, Vec3 center, double radius, long until) {
    }

    /** A block of a bone wall (taken away when its time is up, or at the end of the night). */
    private record WallBlock(ServerLevel level, BlockPos pos, long until) {
    }

    /** Way of the dead: souls per summoner (3 at the start of a night). */
    private final Map<UUID, Integer> souls = new HashMap<>();
    private final java.util.ArrayDeque<Corpse> corpses = new java.util.ArrayDeque<>();
    /** How many dead each summoner raised tonight (every 4th of the army is a champion with the mastery). */
    private final Map<UUID, Integer> raisedCount = new HashMap<>();
    /** Owner → game time the wolf of the cross talent may come again. */
    private final Map<UUID, Long> nextWolf = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private final List<Missile> missiles = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final List<WallBlock> walls = new ArrayList<>();

    private record Pending(long at, Runnable action) {
    }

    /** The spells of the way of the dead: three for each subclass, the G key steps through them. */
    static final List<String> NECRO_SPELLS = List.of("raise", "wall", "curse");
    static final List<String> CATCHER_SPELLS = List.of("swarm", "shield", "chain");

    /** Monsters that do not come back: they would blow up, poison or fly off. */
    private static final java.util.Set<String> NOT_RAISED = java.util.Set.of(
            "minecraft:creeper", "minecraft:witch", "minecraft:evoker", "minecraft:illusioner", "minecraft:phantom",
            "minecraft:ghast", "minecraft:vex", "minecraft:slime", "minecraft:magma_cube", "minecraft:warden",
            "minecraft:wither", "minecraft:ender_dragon", "minecraft:elder_guardian", "minecraft:breeze");

    public Companions(SkillTree tree, Function<ServerPlayer, PlayerSkills> skillsFor, Supplier<SiegeConfig> config) {
        this.tree = tree;
        this.skillsFor = skillsFor;
        this.config = config;
        instance = this;
    }

    public static Companions get() {
        return instance;
    }

    /** The soul shield counts as healing (night protocol and skill points), like the guardian's barriers. */
    public void setCredit(GuardianEffects.Credit credit) {
        this.credit = credit == null ? (player, amount, from) -> {
        } : credit;
    }

    // ------------------------------------------------------------------
    // questions from everywhere
    // ------------------------------------------------------------------

    public static boolean isCompanion(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG);
    }

    /** True while a spell of the way of the dead (or the soul storm) deals its damage. */
    public static boolean spellHit() {
        return spell > 0;
    }

    /** The summoner a companion belongs to, or null. */
    public static UUID ownerOf(Entity entity) {
        Companion c = entity == null ? null : ALL.get(entity.getUUID());
        return c == null ? null : c.owner;
    }

    public static ServerPlayer ownerPlayer(Entity entity) {
        UUID owner = ownerOf(entity);
        if (owner == null || !(entity.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(owner);
    }

    /**
     * Called before any mob takes a target (mixin on setTarget). A companion takes only what
     * this class gives it, never a player or another companion. Another mob turns on a
     * companion only within the taunt radius, for the taunt seconds, and not right after
     * the last time (Simon 26.10.: the companions drew all the anger).
     */
    public static boolean mayTarget(Mob mob, LivingEntity target) {
        if (target == null) {
            return true;
        }
        if (isCompanion(mob)) {
            if (target instanceof Player || isCompanion(target)) {
                return false;
            }
            return steering > 0;   // the vanilla target goals of the companions pick nothing any more
        }
        if (isCompanion(target)) {
            return allowTaunt(mob, target);
        }
        return true;
    }

    /** A companion's target is only cleared by this class (the vanilla goals would drop the one it was given). */
    public static boolean mayClearTarget(Mob mob) {
        return steering > 0 || !isCompanion(mob);
    }

    private static boolean allowTaunt(Mob foe, LivingEntity guard) {
        if (steering > 0) {
            return true;   // our own taunts are recorded where they are made
        }
        Companions self = instance;
        SiegeConfig cfg = self == null ? null : self.config.get();
        double radius = cfg == null ? 3.0 : cfg.companionTauntRadius;
        long now = foe.level().getGameTime();
        Long pause = TAUNT_PAUSE.get(foe.getUUID());
        if (radius <= 0 || (pause != null && now < pause) || foe.distanceToSqr(guard) > radius * radius) {
            return false;
        }
        Taunt old = TAUNTS.get(foe.getUUID());
        if (old == null && foe.level() instanceof ServerLevel level) {
            LivingEntity before = foe.getTarget();
            TAUNTS.put(foe.getUUID(), new Taunt(level, before == null || isCompanion(before) ? null : before.getUUID(),
                    now + tauntTicks(cfg)));
        }
        return true;
    }

    private static long tauntTicks(SiegeConfig cfg) {
        return Math.max(1L, Math.round((cfg == null ? 3.0 : cfg.companionTauntSeconds) * 20.0));
    }

    /** Sets a target on a companion (or on a monster for a taunt): the only way past {@link #mayTarget}. */
    private static void steer(Mob mob, LivingEntity target) {
        steering++;
        try {
            mob.setTarget(target);
        } finally {
            steering--;
        }
    }

    /** A monster turns on a companion for a while (the taunt and the golem's mockery). */
    private static void tauntTo(ServerLevel level, Mob foe, Mob guard, long ticks) {
        if (foe.getTarget() == guard) {
            return;
        }
        LivingEntity before = foe.getTarget();
        TAUNTS.put(foe.getUUID(), new Taunt(level, before == null || isCompanion(before) ? null : before.getUUID(),
                level.getGameTime() + ticks));
        steer(foe, guard);
    }

    /** Players and companions never hurt companions; companions never hurt players. Snowballs of the snow guard slow. */
    public static boolean allowDamage(LivingEntity victim, DamageSource source) {
        try {
            return allowDamageUnsafe(victim, source);
        } catch (RuntimeException e) {
            return true;   // never let the companions break a hit
        }
    }

    private static boolean allowDamageUnsafe(LivingEntity victim, DamageSource source) {
        Entity attacker = source.getEntity();
        if (isCompanion(victim)) {
            if (attacker instanceof Player || isCompanion(attacker)) {
                return false;
            }
            Companion c = ALL.get(victim.getUUID());
            if (c != null && (c.kind == Kind.SPIRIT || c.kind == Kind.HAWK)) {
                return false;   // spirits and the scouting hawk cannot be hurt
            }
        }
        if (isCompanion(attacker) && (victim instanceof Player || isCompanion(victim))) {
            return false;
        }
        if (source.getDirectEntity() instanceof Snowball && isCompanion(attacker) && victim instanceof Enemy) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) Math.round(v("s_snowguard", "slow", 50)), 1));
        }
        return true;
    }

    /** Factor on a hit, for or against a companion, in a curse zone, or by a summoner (called from the damage scaling). */
    public static float damageFactor(LivingEntity victim, DamageSource source) {
        Companions self = instance;
        if (self == null) {
            return 1f;
        }
        float factor = 1f;
        if (!CURSED.isEmpty() && victim instanceof Enemy) {
            Long cursed = CURSED.get(victim.getUUID());
            if (cursed != null && victim.level().getGameTime() <= cursed) {
                factor *= (float) (1.0 + v("d_necro", "curse", 0.25));   // Fluchzone
            }
        }
        Entity attacker = source.getEntity();
        if (ALL.isEmpty() && !(attacker instanceof ServerPlayer)) {
            return factor;
        }
        Companion hurt = ALL.get(victim.getUUID());
        if (hurt != null) {
            PlayerSkills skills = self.skillsOf(hurt.owner, victim);
            factor *= (float) Math.max(0.2, 1.0 - self.bonus(SkillEffect.SUMMON_TOUGHNESS, skills));
        }
        Companion hitter = attacker == null ? null : ALL.get(attacker.getUUID());
        // A splash of the dead (see afterDamage) comes from a hit that already had these factors.
        if (hitter != null && attacker instanceof LivingEntity living && !splashing) {
            PlayerSkills skills = self.skillsOf(hitter.owner, attacker);
            SiegeConfig cfg = self.config.get();
            if (cfg != null && victim instanceof Enemy) {
                factor *= (float) Math.max(0.1, cfg.companionDamage);
            }
            // Wildheit: wounded companions bite harder.
            if (SkillTree.has(skills, "s_wildness")
                    && living.getHealth() < living.getMaxHealth() * (float) v("s_wildness", "below", 0.5)) {
                factor *= (float) (1.0 + self.bonus(SkillEffect.WILDNESS, skills));
            }
            if (hitter.kind == Kind.WOLF || hitter.kind == Kind.SPIRIT) {
                // Rudeljagd: wolves on the same prey.
                if (SkillTree.has(skills, "s_packhunt") && self.huntersOn(victim, hitter.owner, true) >= 2) {
                    factor *= (float) (1.0 + self.bonus(SkillEffect.PACK_HUNT, skills));
                }
            }
            // Heulen: the wolves, and for the golemsmith the golem too.
            if (hitter.kind == Kind.WOLF || hitter.kind == Kind.SPIRIT || hitter.kind == Kind.GOLEM) {
                Long howl = self.howlUntil.get(hitter.owner);
                if (howl != null && victim.level().getGameTime() < howl) {
                    factor *= (float) (1.0 + self.bonus(SkillEffect.HOWL, skills));
                }
            }
            // Mischrudel: wolves and the skeleton together.
            if (SkillTree.has(skills, "s_mixedpack") && (hitter.kind == Kind.WOLF || hitter.kind == Kind.SKELETON)
                    && self.mixedOn(victim, hitter.owner)) {
                factor *= (float) (1.0 + self.bonus(SkillEffect.MIXED_PACK, skills));
            }
            if (hitter.kind == Kind.SKELETON) {
                factor *= (float) self.damageScale(skills, hitter.kind, false);   // arrows: no attribute to carry it
            }
            if (hitter.kind == Kind.MINION || hitter.kind == Kind.GHOST) {
                factor *= (float) hitter.damageMul;
            }
            // Ruf-Hieb: a hit of the summoner's staff spurs the companions on for a few seconds.
            Long boost = self.boostUntil.get(hitter.owner);
            if (boost != null && victim.level().getGameTime() < boost) {
                factor *= (float) (1.0 + v("s_packcall", "strikeboost", 0.2));
            }
            // Herr der Tiere: more damage instead of a place more (Simon 26.10.: at most four wolves).
            if (SkillTree.has(skills, "s_beastlord")) {
                factor *= (float) (1.0 + self.bonus(SkillEffect.BEAST_LORD, skills));
            }
            // Hüterbindung: monsters that go for a player take more from the companions.
            if (SkillTree.has(skills, "s_wardbond") && victim instanceof Mob foe && foe.getTarget() instanceof Player) {
                factor *= (float) (1.0 + self.bonus(SkillEffect.WARD_BOND, skills));
            }
        }
        // Unheilige Kraft: the summoner of the dead hits harder for every soul he carries.
        if (attacker instanceof ServerPlayer summoner && victim instanceof Enemy && !isCompanion(victim)) {
            PlayerSkills skills = self.skillsFor.apply(summoner);
            if (SkillTree.has(skills, "d_unholy")) {
                int carried = Math.min(self.soulsOf(summoner.getUUID()), (int) Math.round(v("d_unholy", "max", 10)));
                factor *= (float) (1.0 + self.bonus(SkillEffect.UNHOLY_MIGHT, skills) * carried);
            }
        }
        return factor;
    }

    // ------------------------------------------------------------------
    // the staff
    // ------------------------------------------------------------------

    /**
     * Right click with the staff. Without sneaking: the summoner's own attack (Ruf-Hieb on the
     * way of the light, Seelengeschoss on the way of the dead). Sneaking: on the way of the
     * light the order (at a monster or a block) or the call (into the air), on the way of
     * the dead the chosen spell.
     */
    public void useStaff(ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = summonerSkills(player);
        if (skills == null) {
            return;
        }
        long now = level.getGameTime();
        boolean dead = SkillTree.WAY_DEAD.equals(skills.pathTaken(tree));
        if (player.isShiftKeyDown()) {
            if (dead) {
                castSpell(level, player, skills, now);
            } else if (!order(level, player, skills, now, false)) {
                call(level, player, skills, now);
            }
            return;
        }
        if (dead) {
            soulBolt(level, player, skills, now);
        } else {
            callStrike(level, player, skills, now);
        }
    }

    /** The X key with the staff in hand (both ways): the order at the monster or the block looked at. */
    public void command(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = summonerSkills(player);
        if (skills != null) {
            order(level, player, skills, level.getGameTime(), true);
        }
    }

    /**
     * The G key (the archer's quiver key, free for the summoner): the way of the dead picks the
     * next spell, the way of the light calls. False for everyone else (the quiver then).
     */
    public boolean selectKey(ServerPlayer player) {
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != com.mki.nightsiege.core.SkillClass.SUMMONER
                || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (SkillTree.WAY_DEAD.equals(skills.pathTaken(tree))) {
            List<String> spells = spellsOf(skills);
            if (spells.isEmpty()) {
                CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.spell_none"));
                return true;
            }
            int next = (spellIndex.getOrDefault(player.getUUID(), 0) + 1) % spells.size();
            spellIndex.put(player.getUUID(), next);
            showSpell(player, skills);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.3f);
            return true;
        }
        call(level, player, skills, level.getGameTime());
        return true;
    }

    /** The summoner's skills, or null with a note when the staff is not his to use. */
    private PlayerSkills summonerSkills(ServerPlayer player) {
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != com.mki.nightsiege.core.SkillClass.SUMMONER) {
            CombatEffects.actionBar(player, Component.translatable(skills == null
                    ? "nightsiege.msg.staff_night_only" : "nightsiege.msg.staff_summoner_only"));
            return null;
        }
        return skills;
    }

    /** How far the staff reaches (its attack, the order, the spells). */
    private double staffRange(PlayerSkills skills) {
        return v("s_callrange", "base", 16.0) + bonus(SkillEffect.CALL_RANGE, skills);
    }

    /**
     * One charge of the staff's own attack (Doppelstab: two). False while both are used up.
     * The item shows no cooldown: a vanilla cooldown would block the sneak actions too.
     */
    private boolean takeCharge(ServerPlayer player, PlayerSkills skills, long now, int pause) {
        if (player.getAbilities().instabuild) {
            pause = Math.min(pause, 10);
        }
        int charges = gain(skills, "doppelstab") > 0 ? 2 : 1;
        long ready = attackReady.getOrDefault(player.getUUID(), 0L);
        long blocked = ready - (long) (charges - 1) * pause;
        if (now < blocked) {
            return false;
        }
        attackReady.put(player.getUUID(), Math.max(ready, now) + pause);
        return true;
    }

    /**
     * Ruf-Hieb (way of the light): a short blow of light at the monster looked at (16 blocks);
     * a hit spurs the own companions on (+20 % damage for 4 s).
     */
    private void callStrike(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now) {
        LivingEntity prey = CombatEffects.lookTarget(level, player, staffRange(skills));
        if (prey == null) {
            if (now >= attackReady.getOrDefault(player.getUUID(), 0L)) {
                CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_no_prey"));
            }
            return;
        }
        if (!takeCharge(player, skills, now, (int) Math.round(v("s_packcall", "strikepause", 20)))) {
            return;
        }
        float damage = (float) (v("s_packcall", "strike", 5.0) * nightScale(true));
        trail(level, player.getEyePosition().add(0, -0.3, 0), prey.position().add(0, prey.getBbHeight() * 0.6, 0),
                ParticleTypes.ENCHANTED_HIT, 10);
        prey.invulnerableTime = 0;
        prey.hurtServer(level, level.damageSources().playerAttack(player), damage);
        boostUntil.put(player.getUUID(), now + Math.round(v("s_packcall", "strikeboosttime", 80)));
        level.playSound(null, prey.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.5f);
    }

    /**
     * The order (sneak + right click on the way of the light, X on both ways): at a monster all
     * companions attack it (the golem taunts and slams when it gets there), at a block they hold
     * that spot (until the next order or 20 s). False when neither is looked at.
     */
    private boolean order(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now, boolean say) {
        double range = staffRange(skills) + v("s_callrange", "order", 16.0);
        LivingEntity prey = CombatEffects.lookTarget(level, player, range);
        if (prey != null) {
            orders.put(player.getUUID(), new Order(prey.getUUID(), null, now + Math.round(v("s_callrange", "attacktime", 200))));
            int sent = 0;
            for (Mob mob : mobsOf(level, player.getUUID())) {
                Companion c = ALL.get(mob.getUUID());
                if (c == null) {
                    continue;
                }
                steer(mob, prey);
                if (c.kind == Kind.GOLEM && now >= c.nextSlam) {
                    c.slamDeadline = now + 100;   // taunt and slam once it reaches the target (at most 5 s)
                }
                sent++;
            }
            prey.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_attack", sent));
            level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.7f, 1.4f);
            return true;
        }
        HitResult hit = player.pick(range, 0f, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            Vec3 spot = Vec3.atBottomCenterOf(block.getBlockPos().above());
            long hold = Math.round(v("s_callrange", "holdtime", 400));
            orders.put(player.getUUID(), new Order(null, spot, now + hold));
            for (Mob mob : mobsOf(level, player.getUUID())) {
                steer(mob, null);
                mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.2);
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.x, spot.y + 0.2, spot.z, 12, 0.6, 0.1, 0.6, 0.0);
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_hold", hold / 20));
            level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.6f, 1.0f);
            return true;
        }
        if (say) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_no_prey"));
        }
        return false;
    }

    /** For the tests and the G key: the call of the way of the light, without the staff in hand. */
    public void callWithStaff(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            PlayerSkills skills = summonerSkills(player);
            if (skills != null) {
                call(level, player, skills, level.getGameTime());
            }
        }
    }

    /** The call: a wolf, or the golem for the golemsmith; the snow golem and the skeleton guard come along. */
    private void call(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now) {
        if (SkillTree.WAY_DEAD.equals(skills.pathTaken(tree))) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_call_dead"));
            return;
        }
        boolean golems = SkillTree.has(skills, "s_golemsmith");
        if (!golems && !SkillTree.has(skills, "s_packcall")) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_nothing"));
            return;
        }
        int pause = callPause(player, skills);
        long ready = nextCall.getOrDefault(player.getUUID(), 0L);
        if (now < ready && !player.getAbilities().instabuild) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_wait", (ready - now + 19) / 20));
            return;
        }
        int extras = callExtras(level, player, skills, now, true);
        int slots = slots(skills);
        int used = count(level, player.getUUID(), k -> k == Kind.WOLF || k == Kind.GOLEM);
        Mob called = null;
        if (used < slots) {
            called = spawn(level, player, skills, golems ? Kind.GOLEM : Kind.WOLF);
        }
        if (called == null && extras == 0) {
            if (used >= slots) {
                CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_full", used, slots));
            }
            return;
        }
        nextCall.put(player.getUUID(), now + pause);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.8f, golems ? 0.7f : 1.3f);
        if (called == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_full_extras", used, slots));
            return;
        }
        CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_called",
                Component.translatable(golems ? "nightsiege.companion.golem" : "nightsiege.companion.wolf"), used + 1, slots));
    }

    /**
     * The pause of a call in ticks. Wolves: shorter with every level of Rudelruf. Golem: a
     * minute, and the same levels shorten it too.
     */
    int callPause(ServerPlayer player, PlayerSkills skills) {
        if (player.getAbilities().instabuild) {
            return 20;
        }
        int level = Math.max(1, skills == null ? 1 : skills.levelOf("s_packcall"));
        if (SkillTree.has(skills, "s_golemsmith")) {
            return (int) Math.max(20, Math.round(v("s_golemsmith", "pause", 1200) - v("s_golemsmith", "step", 180) * (level - 1)));
        }
        double step = skills == null ? 0.0 : bonus(SkillEffect.PACK_CALL, skills) / Math.max(1, level);
        return (int) Math.max(20, Math.round(v("s_packcall", "pause", 400) - step * 20.0 * (level - 1)));
    }

    /** The snow golem (Schneewacht) and the skeleton guard (↔ Knochenwache) come along; how many came. */
    private int callExtras(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now, boolean bones) {
        int came = 0;
        if (SkillTree.has(skills, "s_golemsmith") && SkillTree.has(skills, "s_snowguard")) {
            int want = (int) Math.round(bonus(SkillEffect.SNOW_GUARD, skills));
            for (int i = count(level, player.getUUID(), k -> k == Kind.SNOW); i < want; i++) {
                if (spawn(level, player, skills, Kind.SNOW) != null) {
                    came++;
                }
            }
        }
        if (bones && SkillTree.has(skills, "l_cross_bones") && count(level, player.getUUID(), k -> k == Kind.SKELETON) == 0
                && now >= nextBones.getOrDefault(player.getUUID(), 0L)) {
            if (spawn(level, player, skills, Kind.SKELETON) != null) {
                came++;
            }
        }
        return came;
    }

    /** For the tests: the staff may call and attack again at once. */
    public void resetPause(UUID owner) {
        nextCall.remove(owner);
        attackReady.remove(owner);
        nextSpell.remove(owner);
    }

    /** For the tests: calls one companion of a kind (wolf, golem, snow, skeleton) without the staff. */
    public Mob testCall(ServerPlayer owner, String kind) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return null;
        }
        return spawn(level, owner, skillsFor.apply(owner), Kind.valueOf(kind.toUpperCase(java.util.Locale.ROOT)));
    }

    /**
     * Wolves or golems a summoner may have at once (Simon 26.10.): the golemsmith exactly one
     * golem, the tamer at most four wolves (also with every skill), the lone shepherd one.
     */
    int slots(PlayerSkills skills) {
        if (SkillTree.has(skills, "s_golemsmith")) {
            return 1;
        }
        if (SkillTree.has(skills, "s_shepherd")) {
            return (int) Math.round(v("s_shepherd", "slots", 1));   // Einsamer Hirte: one, twice as strong
        }
        int slots = (int) Math.round(v("s_packcall", "slots", 2)) + (int) Math.floor(bonus(SkillEffect.PACK_SLOTS, skills) + 1.0e-9);
        return Math.max(1, Math.min((int) Math.round(v("s_tamer", "max", 4)), slots));
    }

    // ------------------------------------------------------------------
    // the way of the dead: soul bolt and spells
    // ------------------------------------------------------------------

    /** Seelengeschoss: free, a second's pause, a homing bolt at the monster looked at. */
    private void soulBolt(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now) {
        if (!SkillTree.has(skills, "d_raise")) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_nothing_dead"));
            return;
        }
        LivingEntity prey = CombatEffects.lookTarget(level, player, staffRange(skills));
        if (prey == null) {
            if (now >= attackReady.getOrDefault(player.getUUID(), 0L)) {
                CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_no_prey"));
            }
            return;
        }
        if (!takeCharge(player, skills, now, (int) Math.round(v("d_raise", "boltpause", 20)))) {
            return;
        }
        float damage = (float) ((v("d_raise", "bolt", 5.0) + bonus(SkillEffect.RAISE_DEAD, skills)) * nightScale(true));
        missiles.add(new Missile(player.getUUID(), level, player.getEyePosition().add(player.getLookAngle().scale(0.8)),
                prey.getUUID(), v("d_raise", "boltspeed", 1.3), damage, false, false));
        level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    /** The spells of the summoner's subclass (empty before the subclass). */
    static List<String> spellsOf(PlayerSkills skills) {
        if (SkillTree.has(skills, "d_necro")) {
            return NECRO_SPELLS;
        }
        if (SkillTree.has(skills, "d_soulcatcher")) {
            return CATCHER_SPELLS;
        }
        return List.of();
    }

    /** The spell the G key picked. */
    String selectedSpell(UUID owner, PlayerSkills skills) {
        List<String> spells = spellsOf(skills);
        return spells.isEmpty() ? null : spells.get(Math.floorMod(spellIndex.getOrDefault(owner, 0), spells.size()));
    }

    /** Souls a spell costs. */
    static int spellCost(String spellId) {
        return switch (spellId) {
            case "raise" -> (int) Math.round(v("d_necro", "raisecost", 2));
            case "wall" -> (int) Math.round(v("d_necro", "wallcost", 2));
            case "curse" -> (int) Math.round(v("d_necro", "cursecost", 3));
            case "swarm" -> (int) Math.round(v("d_soulcatcher", "swarmcost", 2));
            case "shield" -> (int) Math.round(v("d_soulcatcher", "shieldcost", 2));
            case "chain" -> (int) Math.round(v("d_soulcatcher", "chaincost", 3));
            default -> 0;
        };
    }

    private void showSpell(ServerPlayer player, PlayerSkills skills) {
        String id = selectedSpell(player.getUUID(), skills);
        if (id == null) {
            return;
        }
        CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.spell_hud",
                Component.translatable("nightsiege.spell." + id), spellCost(id), soulsOf(player.getUUID()), soulCap(skills)));
    }

    /** For the tests and the bench: casts one spell by its id (the souls are paid as with the staff). */
    public boolean castSpell(ServerPlayer player, String spellId) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        PlayerSkills skills = skillsFor.apply(player);
        List<String> spells = spellsOf(skills);
        int at = spells.indexOf(spellId);
        if (at < 0) {
            return false;
        }
        spellIndex.put(player.getUUID(), at);
        nextSpell.remove(player.getUUID());
        return castSpell(level, player, skills, level.getGameTime());
    }

    /** Sneak + right click on the way of the dead: the chosen spell, paid with souls. */
    private boolean castSpell(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now) {
        String id = selectedSpell(player.getUUID(), skills);
        if (id == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.spell_none"));
            return false;
        }
        if (now < nextSpell.getOrDefault(player.getUUID(), 0L)) {
            return false;
        }
        int cost = spellCost(id);
        int have = soulsOf(player.getUUID());
        boolean free = player.getAbilities().instabuild;
        if (have < cost && !free) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.souls_short", have, cost));
            return false;
        }
        boolean done = switch (id) {
            case "raise" -> raiseCorpse(level, player, skills, now);
            case "wall" -> boneWall(level, player, skills, now);
            case "curse" -> curseZone(level, player, skills, now);
            case "swarm" -> ghostSwarm(level, player, skills);
            case "shield" -> soulShield(level, player, skills);
            case "chain" -> soulChain(level, player, skills);
            default -> false;
        };
        if (!done) {
            return false;
        }
        nextSpell.put(player.getUUID(), now + Math.round(v("d_raise", "spellpause", 20)));
        if (!free) {
            souls.put(player.getUUID(), Math.max(0, have - cost));
        }
        showSouls(player, soulsOf(player.getUUID()), soulCap(skills));
        return true;
    }

    /** Spell durations grow with the bind rune; the bone legion adds seconds to wall, zone and chain. */
    private long spellTicks(PlayerSkills skills, double base, boolean legion) {
        double ticks = base * (1.0 + bonus(SkillEffect.BIND_RUNE, skills));
        if (legion && SkillTree.has(skills, "d_legion")) {
            ticks += bonus(SkillEffect.BONE_LEGION, skills) * 20.0;
        }
        return Math.max(20L, Math.round(ticks));
    }

    /** Spell damage: the night, the commander and the necromancer's or soulcatcher's levels, the lich. */
    private double spellScale(PlayerSkills skills, SkillEffect subclass) {
        return nightScale(true) * (1.0 + bonus(SkillEffect.SUMMON_DAMAGE, skills)) * (1.0 + bonus(subclass, skills))
                * (1.0 + (SkillTree.has(skills, "d_lich") ? bonus(SkillEffect.LICH, skills) : 0.0));
    }

    /** Places for the raised dead of the spell (two; the soul keeper one); the army does not count. */
    int minionSlots(PlayerSkills skills) {
        int slots = (int) Math.round(v("d_raise", "slots", 2));
        if (SkillTree.has(skills, "d_soulkeeper")) {
            slots--;
        }
        return Math.max(1, slots);
    }

    /** Leiche erwecken: the newest corpse near you stands up for 8 s, fights, then bursts. */
    private boolean raiseCorpse(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now) {
        int slots = minionSlots(skills);
        int used = count(level, player.getUUID(), k -> k == Kind.MINION, c -> !c.army);
        if (used >= slots) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_full", used, slots));
            return false;
        }
        Corpse corpse = takeCorpse(level, player.position(), v("d_raise", "range", 16.0), false);
        if (corpse == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.no_corpse"));
            return false;
        }
        Mob raised = raise(level, player, skills, corpse, 1.0, now + spellTicks(skills, v("d_necro", "raiselife", 160), false), false);
        if (raised == null) {
            return false;
        }
        Companion c = ALL.get(raised.getUUID());
        if (c != null) {
            c.burst = true;
        }
        CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.raised", raised.getType().getDescription(),
                used + 1, slots));
        return true;
    }

    /** A raised corpse of the spell bursts (at its end or when it falls): damage around it, Seelenbrand gives a soul. */
    private void corpseBurst(ServerLevel level, ServerPlayer owner, Vec3 at) {
        PlayerSkills skills = skillsFor.apply(owner);
        boolean blast = SkillTree.has(skills, "d_corpse");
        float damage = (float) ((v("d_necro", "burst", 6.0) + (blast ? bonus(SkillEffect.CORPSE_BLAST, skills) : 0.0))
                * spellScale(skills, SkillEffect.NECRO_POWER));
        double r = v("d_necro", "burstradius", 3.0) + (blast ? v("d_corpse", "radius", 1.0) : 0.0);
        DamageSource source = level.damageSources().playerAttack(owner);
        for (LivingEntity e : CombatEffects.enemies(level, at, r, 2.0)) {
            spellHurt(level, e, source, damage);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.6, at.z, 3, 0.6, 0.4, 0.6, 0.0);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.6, at.z, 16, 0.8, 0.5, 0.8, 0.05);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.3f);
        if (SkillTree.has(skills, "d_soulburn")) {
            addSouls(owner, skills, (int) Math.round(bonus(SkillEffect.SOUL_BURN, skills)));
        }
    }

    /** Knochenwall: for 6 s a wall of bone blocks in front of you, 5 wide and 2 high (no drops). */
    private boolean boneWall(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now) {
        Vec3 flat = CombatEffects.flatLook(player);
        if (flat == null) {
            return false;
        }
        double ahead = v("d_necro", "walldistance", 3.0);
        int half = (int) Math.round(v("d_necro", "wallwidth", 5)) / 2;
        int height = (int) Math.round(v("d_necro", "wallheight", 2));
        Vec3 center = player.position().add(flat.scale(ahead));
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        long until = now + spellTicks(skills, v("d_necro", "walllife", 120), true);
        int placed = 0;
        for (int i = -half; i <= half; i++) {
            Vec3 at = center.add(side.scale(i));
            BlockPos base = BlockPos.containing(at.x, player.getY(), at.z);
            // Onto the ground: up out of a block, down over a step (two at most).
            for (int up = 0; up < 2 && !level.getBlockState(base).canBeReplaced(); up++) {
                base = base.above();
            }
            for (int down = 0; down < 2 && level.getBlockState(base.below()).canBeReplaced(); down++) {
                base = base.below();
            }
            for (int y = 0; y < height; y++) {
                BlockPos pos = base.above(y);
                if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
                    continue;   // nobody is walled in (a monster would choke in the bone)
                }
                if (TempBlocks.place(level, pos, Blocks.BONE_BLOCK.defaultBlockState())) {
                    walls.add(new WallBlock(level, pos.immutable(), until));
                    placed++;
                }
            }
        }
        if (placed == 0) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.wall_blocked"));
            return false;
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 1, center.z, 16, 1.5, 0.6, 1.5, 0.02);
        level.playSound(null, BlockPos.containing(center), sound("block.bone_block.place"), SoundSource.PLAYERS, 1.2f, 0.7f);
        return true;
    }

    /** The spot a spell is aimed at: the monster looked at, else the block looked at. Null when neither. */
    private LivingEntity spellTarget(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        return CombatEffects.lookTarget(level, player, staffRange(skills) + v("d_raise", "spellrange", 8.0));
    }

    /** Fluchzone: for 6 s a circle of 4 blocks around the target; monsters in it take more damage and are slower. */
    private boolean curseZone(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now) {
        LivingEntity target = spellTarget(level, player, skills);
        Vec3 center;
        if (target != null) {
            center = target.position();
        } else {
            HitResult hit = player.pick(staffRange(skills), 0f, false);
            if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
                CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_no_prey"));
                return false;
            }
            center = Vec3.atBottomCenterOf(block.getBlockPos().above());
        }
        double r = v("d_necro", "curseradius", 4.0) + (SkillTree.has(skills, "d_rot") ? bonus(SkillEffect.ROT, skills) : 0.0);
        zones.add(new Zone(player.getUUID(), level, center, r, now + spellTicks(skills, v("d_necro", "curselife", 120), true)));
        level.playSound(null, BlockPos.containing(center), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }

    /** Geisterschwarm: ghosts fly as homing shots at your target and its neighbours. */
    private boolean ghostSwarm(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        LivingEntity target = spellTarget(level, player, skills);
        if (target == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_no_prey"));
            return false;
        }
        int count = (int) Math.round(v("d_soulcatcher", "ghosts", 3)
                + (SkillTree.has(skills, "d_swarm") ? bonus(SkillEffect.GHOST_SWARM, skills) : 0.0));
        float damage = (float) (v("d_soulcatcher", "ghost", 5.0) * spellScale(skills, SkillEffect.SOUL_CATCHER));
        List<LivingEntity> near = new ArrayList<>();
        for (LivingEntity e : CombatEffects.enemies(level, target.position(), v("d_soulcatcher", "spread", 6.0), 2.0)) {
            if (e != target) {
                near.add(e);
            }
        }
        near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(target)));
        for (int i = 0; i < count; i++) {
            LivingEntity aim = i == 0 || near.isEmpty() ? target : near.get((i - 1) % near.size());
            double a = i / (double) Math.max(1, count) * Math.PI * 2;
            Vec3 from = player.getEyePosition().add(Math.cos(a) * 0.8, 0.3, Math.sin(a) * 0.8);
            missiles.add(new Missile(player.getUUID(), level, from, aim.getUUID(), v("d_soulcatcher", "ghostspeed", 0.7),
                    damage, true, i == 0));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    /** Seelenschild: a barrier on the teammate looked at, else on you; it counts as healing. */
    private boolean soulShield(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        ServerPlayer target = lookMate(level, player, v("d_soulcatcher", "shieldrange", 16.0));
        if (target == null) {
            target = player;
        }
        float amount = (float) ((v("d_soulcatcher", "shield", 8.0)
                + (SkillTree.has(skills, "d_soulshield") ? bonus(SkillEffect.SOUL_SHIELD, skills) : 0.0))
                * (1.0 + bonus(SkillEffect.SOUL_CATCHER, skills)));
        float before = Math.max(0f, target.getAbsorptionAmount());
        GuardianEffects.addAbsorption(target, amount, amount);
        float gained = Math.max(0f, target.getAbsorptionAmount()) - before;
        if (gained <= 0.01f) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.shield_full", target.getDisplayName()));
            return false;
        }
        credit.accept(player, gained, null);
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1, target.getZ(), 20, 0.5, 0.6, 0.5, 0.03);
        level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
        if (target != player) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.shield_given", target.getDisplayName()));
        }
        return true;
    }

    /** The teammate the summoner looks at (the bench's wounded teammate too), or null. */
    private static ServerPlayer lookMate(ServerLevel level, ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        ServerPlayer best = null;
        double bestDot = 0.95;
        for (ServerPlayer mate : GuardianEffects.mates(level)) {
            if (mate == player || !mate.isAlive() || mate.isSpectator() || Drones.isDrone(mate)) {
                continue;
            }
            Vec3 to = mate.position().add(0, 1.0, 0).subtract(eye);
            double dist = to.length();
            if (dist > reach || dist < 1.0e-4) {
                continue;
            }
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = mate;
            }
        }
        return best;
    }

    /** Seelenkette: every monster within 5 blocks of the target is slowed hard for 4 s. */
    private boolean soulChain(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        LivingEntity target = spellTarget(level, player, skills);
        if (target == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_no_prey"));
            return false;
        }
        double r = v("d_soulcatcher", "chainradius", 5.0) + (SkillTree.has(skills, "d_rot") ? bonus(SkillEffect.ROT, skills) : 0.0);
        int ticks = (int) spellTicks(skills, v("d_soulcatcher", "chainlife", 80), true);
        int amp = (int) Math.round(v("d_soulcatcher", "chainslow", 3));
        Vec3 from = target.position().add(0, target.getBbHeight() * 0.5, 0);
        for (LivingEntity e : CombatEffects.enemies(level, target.position(), r, 2.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amp));
            rot(e, skills);
            if (e != target) {
                trail(level, from, e.position().add(0, e.getBbHeight() * 0.5, 0), ParticleTypes.SOUL_FIRE_FLAME, 6);
            }
        }
        level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2f, 0.6f);
        return true;
    }

    /** Fäulnis: bolts and spells weaken the monsters they reach. */
    private void rot(LivingEntity e, PlayerSkills skills) {
        if (SkillTree.has(skills, "d_rot")) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) Math.round(v("d_rot", "duration", 60)), 0));
        }
    }

    /** Damage of a spell: counted as "companions and spells" (bench and night protocol). */
    private static void spellHurt(ServerLevel level, LivingEntity victim, DamageSource source, float damage) {
        spell++;
        try {
            victim.invulnerableTime = 0;
            victim.hurtServer(level, source, damage);
        } finally {
            spell--;
        }
    }

    /** Every tick: the homing shots fly; a lost target gives way to the next monster close by. */
    private void tickMissiles(MinecraftServer server) {
        if (missiles.isEmpty()) {
            return;
        }
        for (Missile m : new ArrayList<>(missiles)) {
            if (--m.life <= 0) {
                missiles.remove(m);
                continue;
            }
            Entity e = m.level.getEntity(m.target);
            LivingEntity target = e instanceof LivingEntity l && l.isAlive() ? l : null;
            if (target == null) {
                LivingEntity next = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity near : CombatEffects.enemies(m.level, m.pos, 6.0, 3.0)) {
                    double d = near.distanceToSqr(m.pos);
                    if (d < best) {
                        best = d;
                        next = near;
                    }
                }
                if (next == null) {
                    m.level.sendParticles(ParticleTypes.POOF, m.pos.x, m.pos.y, m.pos.z, 3, 0.1, 0.1, 0.1, 0.01);
                    missiles.remove(m);
                    continue;
                }
                m.target = next.getUUID();
                target = next;
            }
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
            Vec3 to = aim.subtract(m.pos);
            double dist = to.length();
            if (dist <= m.speed + 0.6) {
                missiles.remove(m);
                ServerPlayer owner = server.getPlayerList().getPlayer(m.owner);
                if (owner != null) {
                    if (m.ghost) {
                        ghostHit(m, owner, target);
                    } else {
                        boltHit(m, owner, target);
                    }
                }
                continue;
            }
            m.pos = m.pos.add(to.scale(m.speed / dist));
            m.level.sendParticles(m.ghost ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME, m.pos.x, m.pos.y, m.pos.z,
                    m.ghost ? 3 : 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** The soul bolt lands (the summoner's own damage): Skelettschützen, Ghulhunger, Fäulnis, Totentanz, Seelenpakt. */
    private void boltHit(Missile m, ServerPlayer owner, LivingEntity target) {
        PlayerSkills skills = skillsFor.apply(owner);
        ServerLevel level = m.level;
        DamageSource source = level.damageSources().playerAttack(owner);
        float before = target.getHealth();
        target.invulnerableTime = 0;
        target.hurtServer(level, source, m.damage);
        float dealt = Math.max(0f, before - Math.max(0f, target.getHealth()));
        rot(target, skills);
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                6, 0.2, 0.2, 0.2, 0.02);
        // Skelettschützen (now): the bolt jumps on to a second monster.
        if (SkillTree.has(skills, "d_archers")) {
            LivingEntity second = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity near : CombatEffects.enemies(level, target.position(), v("d_archers", "range", 6.0), 2.0)) {
                double d = near.distanceToSqr(target);
                if (near != target && d < best) {
                    best = d;
                    second = near;
                }
            }
            if (second != null) {
                trail(level, target.position().add(0, target.getBbHeight() * 0.6, 0),
                        second.position().add(0, second.getBbHeight() * 0.6, 0), ParticleTypes.SOUL_FIRE_FLAME, 5);
                float b2 = second.getHealth();
                second.invulnerableTime = 0;
                second.hurtServer(level, source, (float) (m.damage * bonus(SkillEffect.SKELETON_ARCHERS, skills)));
                dealt += Math.max(0f, b2 - Math.max(0f, second.getHealth()));
                rot(second, skills);
                pact(owner, skills, second);
            }
        }
        if (SkillTree.has(skills, "d_ghoul") && dealt > 0) {
            owner.heal(dealt * (float) bonus(SkillEffect.GHOUL_HUNGER, skills));   // Ghulhunger
        }
        if (SkillTree.has(skills, "d_dance")) {
            healKind(level, owner.getUUID(), Kind.WOLF, (float) bonus(SkillEffect.DEATH_DANCE, skills));   // Totentanz
        }
        pact(owner, skills, target);
    }

    /** Seelenpakt: a monster killed by the bolt or a spell heals the summoner. */
    private void pact(ServerPlayer owner, PlayerSkills skills, LivingEntity victim) {
        if (!victim.isAlive() && SkillTree.has(skills, "d_pact")) {
            owner.heal((float) bonus(SkillEffect.SOUL_PACT, skills));
        }
    }

    /** A ghost of the swarm lands (a spell): Leichensprengung splashes, Seelenbrand gives a soul back once. */
    private void ghostHit(Missile m, ServerPlayer owner, LivingEntity target) {
        PlayerSkills skills = skillsFor.apply(owner);
        ServerLevel level = m.level;
        DamageSource source = level.damageSources().playerAttack(owner);
        spellHurt(level, target, source, m.damage);
        rot(target, skills);
        pact(owner, skills, target);
        if (SkillTree.has(skills, "d_corpse")) {
            float splash = (float) (bonus(SkillEffect.CORPSE_BLAST, skills) * spellScale(skills, SkillEffect.SOUL_CATCHER)
                    * v("d_corpse", "ghostshare", 0.5));
            for (LivingEntity e : CombatEffects.enemies(level, target.position(), v("d_corpse", "ghostradius", 2.5), 2.0)) {
                if (e != target) {
                    spellHurt(level, e, source, splash);
                }
            }
            if (m.refund && SkillTree.has(skills, "d_soulburn")) {
                addSouls(owner, skills, (int) Math.round(bonus(SkillEffect.SOUL_BURN, skills)));
            }
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                8, 0.3, 0.3, 0.3, 0.03);
    }

    /** Every five ticks: the curse zones mark and slow the monsters inside; old marks are forgotten. */
    private void tickZones(long now) {
        if (!CURSED.isEmpty()) {
            CURSED.values().removeIf(until -> until < now);
        }
        if (zones.isEmpty()) {
            return;
        }
        for (Zone z : new ArrayList<>(zones)) {
            if (now >= z.until()) {
                zones.remove(z);
                continue;
            }
            ServerPlayer owner = z.level().getServer().getPlayerList().getPlayer(z.owner());
            PlayerSkills skills = owner == null ? null : skillsFor.apply(owner);
            for (LivingEntity e : CombatEffects.enemies(z.level(), z.center(), z.radius(), 2.5)) {
                CURSED.put(e.getUUID(), now + 10);
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, (int) Math.round(v("d_necro", "curseslow", 0))));
                rot(e, skills);
            }
            if (now % 10 == 0) {
                int points = (int) Math.max(12, z.radius() * 6);
                for (int i = 0; i < points; i++) {
                    double a = i / (double) points * Math.PI * 2;
                    z.level().sendParticles(ParticleTypes.SOUL, z.center().x + Math.cos(a) * z.radius(), z.center().y + 0.15,
                            z.center().z + Math.sin(a) * z.radius(), 1, 0.0, 0.02, 0.0, 0.0);
                }
            }
        }
    }

    /** Bone walls whose time is up go (no drops). */
    private void tickWalls(long now) {
        if (walls.isEmpty()) {
            return;
        }
        for (WallBlock w : new ArrayList<>(walls)) {
            if (now >= w.until()) {
                walls.remove(w);
                TempBlocks.remove(w.level(), w.pos());
            }
        }
    }

    // ------------------------------------------------------------------
    // ultimates
    // ------------------------------------------------------------------

    /** Wilde Jagd: spirit wolves hunt for a while (Endlose Jagd five seconds more). */
    public boolean wildHunt(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        int count = (int) Math.round(v("s_wild_hunt", "count" + rank, 3 + rank));
        long until = level.getGameTime() + Math.round(v("s_wild_hunt", "duration", 200)
                + (SkillTree.has(skills, "s_endless_hunt") ? bonus(SkillEffect.ENDLESS_HUNT, skills) * 20.0 : 0.0));
        for (int i = 0; i < count; i++) {
            Mob wolf = spawn(level, player, skills, Kind.SPIRIT);
            Companion c = wolf == null ? null : ALL.get(wolf.getUUID());
            if (c != null) {
                c.until = until;
                c.rank = rank;
                // The bite by rank (the stats were set before the rank was known: always rank 0).
                set(wolf, Attributes.ATTACK_DAMAGE, v("s_wild_hunt", "dmg" + Math.max(1, rank), 4.0 * (1.5 + 0.25 * rank))
                        * damageScale(skills, Kind.SPIRIT, false));
            }
        }
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 40, 2.0, 0.6, 2.0, 0.05);
        level.playSound(null, player.blockPosition(), sound("entity.wolf.growl"), SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    /** Titan: a giant golem for fifteen seconds that slams the ground. */
    public boolean titan(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        Mob titan = spawn(level, player, skills, Kind.TITAN);
        Companion c = titan == null ? null : ALL.get(titan.getUUID());
        if (c == null) {
            return false;
        }
        c.until = level.getGameTime() + Math.round(v("s_titan", "duration", 300));
        c.rank = rank;
        c.nextSlam = level.getGameTime() + Math.round(v("s_titan", "firstslam", 40));
        level.sendParticles(ParticleTypes.EXPLOSION, titan.getX(), titan.getY() + 1, titan.getZ(), 6, 1.0, 0.5, 1.0, 0.0);
        level.playSound(null, titan.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.5f, 0.5f);
        return true;
    }

    // ------------------------------------------------------------------
    // spawning
    // ------------------------------------------------------------------

    private Mob spawn(ServerLevel level, ServerPlayer owner, PlayerSkills skills, Kind kind) {
        Mob mob = switch (kind) {
            case WOLF, SPIRIT -> EntityTypes.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
            case GOLEM, TITAN -> EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
            case SNOW -> EntityTypes.SNOW_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
            case SKELETON -> EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
            case GHOST -> EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
            case MOUNT -> EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
            case HAWK -> EntityTypes.PARROT.create(level, EntitySpawnReason.MOB_SUMMONED);
            case MINION -> null;   // the raised dead come from raise()
        };
        if (mob == null) {
            return null;
        }
        Vec3 at = kind == Kind.HAWK ? owner.position().add(0, 4.5, 0) : spot(level, owner);
        mob.snapTo(at.x, at.y, at.z, owner.getYRot(), 0f);
        if (kind == Kind.SKELETON) {
            try {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
            } catch (RuntimeException e) {
                // a skeleton without its bow still stands guard
            }
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.BOW));
            // No burning in the morning sun and nothing to drop.
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.SKELETON_SKULL));
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                mob.setDropChance(slot, 0f);
            }
        }
        if (mob instanceof Wolf wolf) {
            wolf.setTame(true, true);
            wolf.setOwner(owner);
            wolf.setOrderedToSit(false);
            if (kind == Kind.WOLF && gain(skills, "wolfspanzer") > 0) {
                // Wolfspanzer: the pack runs in armour (it takes the hits until it breaks).
                wolf.setItemSlot(net.minecraft.world.entity.EquipmentSlot.BODY, new ItemStack(net.minecraft.world.item.Items.WOLF_ARMOR));
                wolf.setDropChance(net.minecraft.world.entity.EquipmentSlot.BODY, 0f);
            }
        }
        if (mob instanceof net.minecraft.world.entity.animal.equine.AbstractHorse horse) {
            // Geisterross: tame and saddled, for the summoner to ride.
            horse.setTamed(true);
            horse.setOwner(owner);
            horse.setItemSlot(net.minecraft.world.entity.EquipmentSlot.SADDLE, new ItemStack(net.minecraft.world.item.Items.SADDLE));
            horse.setDropChance(net.minecraft.world.entity.EquipmentSlot.SADDLE, 0f);
        }
        if (kind == Kind.HAWK) {
            // Spähfalke: no mind of its own; it circles above its summoner (see nightTick).
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setSilent(true);
            mob.setInvulnerable(true);
        }
        if (mob instanceof IronGolem golem) {
            golem.setPlayerCreated(true);
        }
        if (kind != Kind.HAWK && kind != Kind.MOUNT) {
            oneMind(mob);
        }
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        Companion c = new Companion(mob.getUUID(), owner.getUUID(), kind, level.getGameTime());
        if (kind == Kind.WOLF && SkillTree.has(skills, "s_alpha")
                && count(level, owner.getUUID(), k -> k == Kind.WOLF, o -> o.alpha) == 0) {
            c.alpha = true;   // the pack has no leader (yet, or any more): this wolf leads it
        }
        if (kind == Kind.SKELETON || kind == Kind.SNOW || kind == Kind.SPIRIT || kind == Kind.TITAN || kind == Kind.GHOST
                || kind == Kind.MOUNT || kind == Kind.HAWK || c.alpha) {
            mob.setCustomName(Component.translatable("nightsiege.companion." + (c.alpha ? "alpha" : kind.name().toLowerCase(java.util.Locale.ROOT)),
                    owner.getDisplayName()));
        }
        if (kind == Kind.SPIRIT) {
            mob.setGlowingTag(true);
            mob.setSilent(true);
        }
        applyStats(mob, c, skills);
        // Known before it enters the world: the load hook throws out companions nobody owns.
        ALL.put(mob.getUUID(), c);
        if (!level.addFreshEntity(mob)) {
            ALL.remove(mob.getUUID());
            return null;
        }
        level.sendParticles(kind == Kind.SPIRIT || kind == Kind.MOUNT ? ParticleTypes.SOUL : ParticleTypes.POOF,
                at.x, at.y + 0.6, at.z, 12, 0.4, 0.4, 0.4, 0.02);
        return mob;
    }

    /**
     * The shared mind: the mob's own wandering, following and fleeing goes (this class moves
     * it); its fighting goals stay (bite, punch, snowballs, bow). Its target goals still run,
     * but every target they pick is refused in {@link #mayTarget}.
     */
    private static void oneMind(Mob mob) {
        mob.getGoalSelector().removeAllGoals(g -> g instanceof net.minecraft.world.entity.ai.goal.AvoidEntityGoal<?>
                || g instanceof net.minecraft.world.entity.ai.goal.RandomStrollGoal
                || g instanceof net.minecraft.world.entity.ai.goal.FollowOwnerGoal);
    }

    /** A free spot beside the summoner. */
    private static Vec3 spot(ServerLevel level, ServerPlayer owner) {
        return spot(level, owner.position(), owner);
    }

    private static Vec3 spot(ServerLevel level, Vec3 near, Entity random) {
        for (int i = 0; i < 8; i++) {
            double a = random.getRandom().nextDouble() * Math.PI * 2;
            double r = 1.5 + random.getRandom().nextDouble() * 1.5;
            BlockPos pos = BlockPos.containing(near.x + Math.cos(a) * r, near.y, near.z + Math.sin(a) * r);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(pos);
            }
        }
        return near;
    }

    /** Health, damage, speed and size from the skills and the night. */
    private void applyStats(Mob mob, Companion c, PlayerSkills skills) {
        if (c.kind == Kind.MOUNT) {
            set(mob, Attributes.MAX_HEALTH, 40.0 * nightScale(false));
            mob.setHealth(mob.getMaxHealth());
            set(mob, Attributes.MOVEMENT_SPEED, 0.32);
            set(mob, Attributes.JUMP_STRENGTH, 0.8);
            return;
        }
        if (c.kind == Kind.HAWK) {
            return;
        }
        double health = switch (c.kind) {
            case WOLF -> v("s_packcall", "health", 40.0);
            case SPIRIT -> 40.0;
            // Simon 26.10.: one golem only, weaker than vanilla (the golemsmith about as strong as the tamer).
            case GOLEM -> v("s_golemsmith", "health", 60.0);
            case SNOW -> 20.0;
            case SKELETON -> v("l_cross_bones", "health", 30.0);
            case TITAN -> v("s_titan", "health", 300.0);
            case MINION -> 14.0;
            case GHOST -> v("d_soulcatcher", "health", 30.0);
            case MOUNT, HAWK -> 20.0;
        };
        double damage = switch (c.kind) {
            case WOLF -> v("s_packcall", "damage", 3.0);
            case SPIRIT -> v("s_wild_hunt", "dmg" + Math.max(1, c.rank), 4.0 * (1.5 + 0.25 * c.rank));
            case GOLEM -> v("s_golemsmith", "damage", 9.0);
            case TITAN -> 22.0;
            default -> -1.0;
        };
        double healthScale = healthScale(skills, c.kind, c.alpha);
        set(mob, Attributes.MAX_HEALTH, health * healthScale);
        mob.setHealth(mob.getMaxHealth());
        if (damage > 0) {
            set(mob, Attributes.ATTACK_DAMAGE, damage * damageScale(skills, c.kind, c.alpha));
        }
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double factor = 1.0 + bonus(SkillEffect.SUMMON_SPEED, skills) + (c.kind == Kind.SPIRIT ? v("s_wild_hunt", "speed", 0.3) : 0.0);
            speed.setBaseValue(speed.getBaseValue() * factor);
        }
        double size = c.alpha ? v("s_alpha", "size", 1.35) : c.kind == Kind.TITAN ? 2.0 : c.kind == Kind.GHOST ? 1.6 : 1.0;
        if (size != 1.0) {
            set(mob, Attributes.SCALE, size);
        }
        if (c.kind == Kind.TITAN || c.kind == Kind.GOLEM) {
            set(mob, Attributes.KNOCKBACK_RESISTANCE, 1.0);
            if (gain(skills, "golemplatten") > 0) {
                AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
                if (armor != null) {
                    armor.setBaseValue(armor.getBaseValue() + 4.0);   // Golemplatten
                }
            }
        }
    }

    private static void set(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    double healthScale(PlayerSkills skills, Kind kind, boolean alpha) {
        double scale = (1.0 + bonus(SkillEffect.SUMMON_HEALTH, skills)) * nightScale(false);
        if (kind == Kind.GOLEM || kind == Kind.TITAN) {
            scale *= 1.0 + bonus(SkillEffect.GOLEM_CALL, skills);
            if (SkillTree.has(skills, "s_hardgolem")) {
                scale *= 1.0 + bonus(SkillEffect.HARD_GOLEM, skills);
            }
        }
        // Alphawolf and Einsamer Hirte do not multiply: the bigger of the two counts.
        double lead = alpha ? v("s_alpha", "health", 2.0) : 1.0;
        if (SkillTree.has(skills, "s_shepherd") && (kind == Kind.WOLF || kind == Kind.GOLEM)) {
            lead = Math.max(lead, v("s_shepherd", "health", 2.0));
        }
        scale *= lead;
        return scale;
    }

    double damageScale(PlayerSkills skills, Kind kind, boolean alpha) {
        double scale = (1.0 + bonus(SkillEffect.SUMMON_DAMAGE, skills)) * nightScale(true);
        if (kind == Kind.GOLEM || kind == Kind.TITAN) {
            scale *= 1.0 + bonus(SkillEffect.GOLEM_CALL, skills);
        }
        double lead = alpha ? v("s_alpha", "damage", 2.0) : 1.0;
        if (SkillTree.has(skills, "s_shepherd") && (kind == Kind.WOLF || kind == Kind.GOLEM)) {
            lead = Math.max(lead, v("s_shepherd", "damage", 2.0));
        }
        scale *= lead;
        return scale;
    }

    /** Companions (and the staff) grow with the night like the monsters (a share of their growth, config companionScaling). */
    private double nightScale(boolean damage) {
        SiegeManager m = SiegeManager.get();
        SiegeConfig cfg = config.get();
        if (m == null || cfg == null) {
            return 1.0;
        }
        double monsters = damage ? m.mobDamageFactor() : m.mobHealthFactor();
        return 1.0 + Math.max(0.0, monsters - 1.0) * cfg.companionScaling;
    }

    private double bonus(SkillEffect effect, PlayerSkills skills) {
        return skills == null ? 0.0 : tree.totalBonus(effect, skills);
    }

    /** The working level of one of the summoner's crate gains. */
    static int gain(PlayerSkills skills, String id) {
        CrateGains.Gain g = CrateGains.get(id);
        return g == null || skills == null ? 0 : g.level(skills.gainStage(com.mki.nightsiege.core.SkillClass.SUMMONER, id));
    }

    private PlayerSkills skillsOf(UUID owner, Entity near) {
        if (!(near.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        return player == null ? null : skillsFor.apply(player);
    }

    // ------------------------------------------------------------------
    // ticking: the shared mind
    // ------------------------------------------------------------------

    /**
     * Every tick the shots fly; every five ticks companions of summoners who may no longer
     * have them go, the others pick targets, follow and use their skills.
     */
    public void tick(MinecraftServer server, Predicate<ServerPlayer> mayKeep) {
        long now = server.overworld().getGameTime();
        if (!pending.isEmpty()) {
            for (Pending p : new ArrayList<>(pending)) {
                if (now >= p.at()) {
                    pending.remove(p);
                    p.action().run();
                }
            }
        }
        tickMissiles(server);
        if (now % 5 != 0) {
            return;
        }
        tickTaunts(now);
        tickZones(now);
        tickWalls(now);
        if (ALL.isEmpty()) {
            return;
        }
        Map<UUID, ServerPlayer> owners = new HashMap<>();
        for (Companion c : new ArrayList<>(ALL.values())) {
            ServerPlayer owner = owners.computeIfAbsent(c.owner, id -> server.getPlayerList().getPlayer(id));
            Entity entity = owner == null ? find(server, c.id) : ((ServerLevel) owner.level()).getEntity(c.id);
            if (entity == null && owner != null) {
                entity = find(server, c.id);   // left behind in another dimension
            }
            // In a night while the summoner defends; outside it only for a creative test, and not for long.
            boolean defending = owner != null && SiegeManager.get() != null && SiegeManager.get().isDefending(owner);
            boolean keep = owner != null && owner.isAlive() && mayKeep.test(owner)
                    && (defending || now - c.born < TEST_LIFETIME);
            if (!(entity instanceof Mob mob) || !mob.isAlive() || !keep) {
                remove(server, c, entity);
                if (keep && owner.level() instanceof ServerLevel at) {
                    fell(at, owner, c);   // killed in the fight (or already gone), not sent away
                }
                continue;
            }
            ServerLevel level = (ServerLevel) mob.level();
            if (c.until >= 0 && now >= c.until) {
                expire(level, owner, c, mob);
                continue;
            }
            PlayerSkills skills = skillsFor.apply(owner);
            work(level, owner, skills, c, mob, now);
        }
        // Heulen: every 20 s the pack howls (5 s faster and stronger); the golem roars along.
        for (ServerPlayer owner : owners.values()) {
            if (owner == null) {
                continue;
            }
            Order order = orders.get(owner.getUUID());
            if (order != null && now >= order.until()) {
                orders.remove(owner.getUUID());
            }
            guard(owner, now);
            PlayerSkills skills = skillsFor.apply(owner);
            ServerLevel ownLevel = (ServerLevel) owner.level();
            if (SkillTree.has(skills, "s_howl") && now >= nextHowl.getOrDefault(owner.getUUID(), 0L)
                    && count(ownLevel, owner.getUUID(), k -> k == Kind.WOLF || k == Kind.GOLEM) > 0) {
                nextHowl.put(owner.getUUID(), now + Math.round(v("s_howl", "every", 400)));
                int length = (int) Math.round(v("s_howl", "length", 100));
                howlUntil.put(owner.getUUID(), now + length);
                for (Mob pack : mobsOf(ownLevel, owner.getUUID())) {
                    Companion c = ALL.get(pack.getUUID());
                    if (c != null && (c.kind == Kind.WOLF || c.kind == Kind.GOLEM)) {
                        pack.addEffect(new MobEffectInstance(MobEffects.SPEED, length, 1, false, true));
                        ownLevel.playSound(null, pack.blockPosition(), c.kind == Kind.WOLF ? sound("entity.wolf.growl")
                                : SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 1.0f, 0.7f);
                    }
                }
            }
            // Rudelführer: on its own clock the leader (the alpha, else the strongest wolf) heals the pack.
            if (SkillTree.has(skills, "s_packleader") && now >= nextLead.getOrDefault(owner.getUUID(), 0L)) {
                Mob leader = null;
                boolean alphaFound = false;
                for (Mob wolf : mobsOf(ownLevel, owner.getUUID())) {
                    Companion c = ALL.get(wolf.getUUID());
                    if (c == null || c.kind != Kind.WOLF || alphaFound) {
                        continue;
                    }
                    if (c.alpha || leader == null || wolf.getHealth() > leader.getHealth()) {
                        leader = wolf;
                        alphaFound = c.alpha;
                    }
                }
                if (leader != null) {
                    nextLead.put(owner.getUUID(), now + Math.round(v("s_packleader", "every", 400)));
                    healPack(ownLevel, owner, leader, (float) (bonus(SkillEffect.PACK_LEADER, skills) * 2.0));
                }
            }
        }
    }

    /**
     * Taunts whose time is up end: the monster goes back to whom it had before (or picks anew),
     * and for a moment it turns on no companion again.
     */
    private void tickTaunts(long now) {
        if (!TAUNT_PAUSE.isEmpty()) {
            TAUNT_PAUSE.values().removeIf(until -> until < now);
        }
        if (TAUNTS.isEmpty()) {
            return;
        }
        long pause = Math.max(0L, Math.round(v("s_soulbond", "tauntpause", 40)));
        for (Map.Entry<UUID, Taunt> e : new ArrayList<>(TAUNTS.entrySet())) {
            Taunt t = e.getValue();
            if (now < t.until()) {
                continue;
            }
            TAUNTS.remove(e.getKey());
            if (!(t.level().getEntity(e.getKey()) instanceof Mob foe) || !foe.isAlive()) {
                continue;
            }
            TAUNT_PAUSE.put(foe.getUUID(), now + pause);
            if (!isCompanion(foe.getTarget())) {
                continue;   // it went for someone else by itself
            }
            Entity before = t.before() == null ? null : t.level().getEntity(t.before());
            LivingEntity back = before instanceof LivingEntity l && l.isAlive() && !isCompanion(l) ? l : null;
            foe.setTarget(back);   // null: its own goals pick a defender again
        }
        if (TAUNTS.size() > 2000) {
            TAUNTS.clear();   // never a leak: monsters that vanished without their taunt ending
        }
    }

    /**
     * Monsters right next to a companion that go for its summoner turn to that companion
     * for a few seconds (config companionTauntRadius, companionTauntSeconds): the companions
     * catch a few blows, but they no longer draw the whole night's anger.
     */
    private void guard(ServerPlayer owner, long now) {
        SiegeConfig cfg = config.get();
        double radius = cfg == null ? 0.0 : cfg.companionTauntRadius;
        if (radius <= 0 || !(owner.level() instanceof ServerLevel level) || !owner.isAlive()) {
            return;
        }
        long ticks = tauntTicks(cfg);
        for (Mob guard : mobsOf(level, owner.getUUID())) {
            Companion c = ALL.get(guard.getUUID());
            if (c == null || c.kind == Kind.SPIRIT || !guard.isAlive()) {
                continue;
            }
            for (Mob foe : level.getEntitiesOfClass(Mob.class, guard.getBoundingBox().inflate(radius),
                    e -> e instanceof Enemy && e.isAlive() && !isCompanion(e) && e.getTarget() == owner)) {
                Long paused = TAUNT_PAUSE.get(foe.getUUID());
                if ((paused == null || now >= paused) && !TAUNTS.containsKey(foe.getUUID())
                        && foe.distanceToSqr(guard) <= radius * radius) {
                    tauntTo(level, foe, guard, ticks);
                }
            }
        }
    }

    private void work(ServerLevel level, ServerPlayer owner, PlayerSkills skills, Companion c, Mob mob, long now) {
        if (c.kind == Kind.HAWK) {
            return;   // it flies its circle in nightTick
        }
        double teleport = v("s_soulbond", "teleport", 16.0);
        if (c.kind == Kind.MOUNT) {
            if (now % 10 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 0.2, mob.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
            }
            if (!mob.isVehicle() && (mob.distanceToSqr(owner) > 30.0 * 30.0 || mob.level() != owner.level())) {
                Vec3 at = spot(level, owner);
                mob.teleportTo(at.x, at.y, at.z);
            }
            return;
        }
        Order order = orders.get(owner.getUUID());
        Vec3 anchor = order != null && order.hold() != null ? order.hold() : owner.position();
        double leash = v("s_soulbond", "leash", 10.0);
        double reach = leash + v("s_soulbond", "buffer", 3.0);
        // Too far away (or in another world): back beside the summoner at once.
        if (mob.level() != owner.level() || mob.position().distanceToSqr(anchor) > teleport * teleport
                || mob.distanceToSqr(owner) > (teleport + 16.0) * (teleport + 16.0)) {
            backTo(owner, anchor, c, mob, now);
            return;
        }
        if (mob instanceof Wolf wolf && wolf.isOrderedToSit()) {
            wolf.setOrderedToSit(false);   // a right click made it sit: in a siege the pack stays on its feet
            wolf.setInSittingPose(false);
        }
        LivingEntity target = pickTarget(level, owner, mob, order, anchor, leash, reach);
        if (target != mob.getTarget()) {
            steer(mob, target);
        }
        // Stuck: about three seconds without getting anywhere while it should move.
        double toAnchor = mob.position().distanceTo(anchor);
        boolean shouldMove = target != null ? mob.distanceTo(target) > 3.0 : toAnchor > 4.0;
        if (c.lastPos == null || c.lastLevel != level || mob.position().distanceToSqr(c.lastPos) > 0.6 * 0.6 || !shouldMove) {
            c.lastPos = mob.position();
            c.lastLevel = level;
            c.lastMoved = now;
        } else if (now - c.lastMoved >= Math.round(v("s_soulbond", "stuck", 60)) && c.kind != Kind.TITAN) {
            backTo(owner, anchor, c, mob, now);
            return;
        }
        if (target == null) {
            double near = order != null && order.hold() != null ? 1.5 : 4.0;
            if (toAnchor > near && (mob.getNavigation().isDone() || now % 20 == 0)) {
                mob.getNavigation().moveTo(anchor.x, anchor.y, anchor.z, c.kind == Kind.TITAN ? 1.0 : 1.2);
            }
        }
        if (c.kind == Kind.SPIRIT && now % 10 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 0.5, mob.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        }
        if (c.kind == Kind.GHOST && now % 10 == 0) {
            // A trail of soul fire, so the ghost is seen among the monsters.
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                    3, 0.2, 0.3, 0.2, 0.0);
        }
        if (c.kind == Kind.TITAN && now >= c.nextSlam) {
            c.nextSlam = now + Math.round(v("s_titan", "slam", 60));
            slam(level, owner, skills, c, mob);
        }
        // The golem on order: when it reaches the monster, it taunts all around and slams the ground (every 10 s).
        if (c.kind == Kind.GOLEM && c.slamDeadline >= 0) {
            if (now > c.slamDeadline) {
                c.slamDeadline = -1;
            } else if (target != null && mob.distanceTo(target) <= v("s_golemsmith", "slamreach", 3.5)) {
                c.slamDeadline = -1;
                c.nextSlam = now + Math.round(v("s_golemsmith", "slamevery", 200));
                golemSlam(level, owner, skills, mob);
            }
        }
        // Pflege: every five seconds a heart back.
        if (now % Math.max(5, Math.round(v("s_care", "every", 100))) == 0 && SkillTree.has(skills, "s_care")
                && mob.getHealth() < mob.getMaxHealth()) {
            mob.heal((float) bonus(SkillEffect.CARE, skills));
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /** Stuck or too far away: the companion is beside its summoner (or at the spot it holds) at once. */
    private static void backTo(ServerPlayer owner, Vec3 anchor, Companion c, Mob mob, long now) {
        ServerLevel at = (ServerLevel) owner.level();
        Vec3 spot = spot(at, anchor, owner);
        mob.teleportTo(spot.x, spot.y, spot.z);
        mob.getNavigation().stop();
        steer(mob, null);
        c.lastPos = spot;
        c.lastLevel = at;
        c.lastMoved = now;
        at.sendParticles(ParticleTypes.POOF, spot.x, spot.y + 0.5, spot.z, 4, 0.2, 0.2, 0.2, 0.01);
    }

    /**
     * The shared mind's choice: the ordered monster; else monsters that go for the summoner
     * or a teammate (within 10 blocks and a little more); else the nearest monster near him.
     */
    private static LivingEntity pickTarget(ServerLevel level, ServerPlayer owner, Mob mob, Order order, Vec3 anchor,
                                           double leash, double reach) {
        if (order != null && order.target() != null) {
            Entity e = level.getEntity(order.target());
            if (e instanceof LivingEntity ordered && ordered.isAlive() && ordered.distanceToSqr(owner) <= 32.0 * 32.0) {
                return ordered;
            }
        }
        LivingEntity current = mob.getTarget();
        boolean currentOk = current != null && current.isAlive() && !(current instanceof Player) && !isCompanion(current)
                && current.position().distanceToSqr(anchor) <= reach * reach;
        boolean currentThreat = currentOk && current instanceof Mob cm && cm.getTarget() instanceof Player;
        if (currentThreat) {
            return current;
        }
        LivingEntity threat = nearest(level, anchor, reach, mob, e -> e instanceof Mob m && m.getTarget() instanceof Player);
        if (threat != null) {
            return threat;
        }
        if (currentOk) {
            return current;
        }
        return nearest(level, anchor, leash, mob, e -> !(e instanceof Creeper));
    }

    /** The monster nearest to a companion within a radius of a spot. */
    private static LivingEntity nearest(ServerLevel level, Vec3 center, double radius, Mob self, Predicate<LivingEntity> also) {
        AABB box = new AABB(center, center).inflate(radius, 6.0, radius);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof Enemy && e.isAlive() && !isCompanion(e))) {
            if (e.position().distanceToSqr(center) > radius * radius || !also.test(e)) {
                continue;
            }
            double d = e.distanceToSqr(self);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** The golem's order: monsters within 4 blocks go for it for 3 s, and the ground slam hits them. */
    private void golemSlam(ServerLevel level, ServerPlayer owner, PlayerSkills skills, Mob golem) {
        double tauntRadius = v("s_golemsmith", "tauntradius", 4.0);
        long tauntTicks = Math.round(v("s_golemsmith", "taunttime", 60));
        for (Mob foe : level.getEntitiesOfClass(Mob.class, golem.getBoundingBox().inflate(tauntRadius, 2.0, tauntRadius),
                e -> e instanceof Enemy && e.isAlive() && !isCompanion(e))) {
            tauntTo(level, foe, golem, tauntTicks);
        }
        double r = v("s_golemsmith", "slamradius", 3.5);
        float damage = (float) (v("s_golemsmith", "slam", 6.0) * damageScale(skills, Kind.GOLEM, false));
        DamageSource source = level.damageSources().mobAttack(golem);
        for (LivingEntity e : CombatEffects.enemies(level, golem.position(), r, 2.0)) {
            e.invulnerableTime = 0;
            e.hurtServer(level, source, damage);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, golem.getX(), golem.getY() + 0.2, golem.getZ(), 3, r * 0.4, 0.1, r * 0.4, 0.0);
        level.playSound(null, golem.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    private void slam(ServerLevel level, ServerPlayer owner, PlayerSkills skills, Companion c, Mob titan) {
        double r = v("s_titan", "radius", 4.5);
        float damage = (float) (v("s_titan", "dmg" + Math.max(1, c.rank), 6.0 + 3.0 * c.rank)
                * damageScale(skills, Kind.TITAN, false) / 1.5);
        DamageSource source = level.damageSources().mobAttack(titan);
        boolean fist = SkillTree.has(skills, "s_titanfist");
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, titan.getBoundingBox().inflate(r, 2.0, r),
                e -> e instanceof Enemy && e.isAlive() && !isCompanion(e))) {
            e.invulnerableTime = 0;
            e.hurtServer(level, source, damage);
            Vec3 away = e.position().subtract(titan.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 0) : away.normalize();
            // Titanenfaust: the slam flings the monsters away.
            double push = fist ? v("s_titanfist", "push", 1.6) : v("s_titan", "push", 0.5);
            e.push(away.x * push, fist ? v("s_titanfist", "lift", 0.9) : v("s_titan", "lift", 0.4), away.z * push);
            e.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, titan.getX(), titan.getY() + 0.2, titan.getZ(), 5, r * 0.4, 0.1, r * 0.4, 0.0);
        level.playSound(null, titan.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    private void healPack(ServerLevel level, ServerPlayer owner, Mob alpha, float amount) {
        for (Mob mob : mobsOf(level, owner.getUUID())) {
            Companion c = ALL.get(mob.getUUID());
            double r = v("s_packleader", "radius", 8.0);
            if (c != null && c.kind == Kind.WOLF && mob.distanceToSqr(alpha) <= r * r) {
                mob.heal(amount);
                level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 0.8, mob.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            }
        }
    }

    /** The time of a spirit, the titan or a raised corpse is up; the corpse bursts, the titan leaves its ore core. */
    private void expire(ServerLevel level, ServerPlayer owner, Companion c, Mob mob) {
        Vec3 at = mob.position();
        remove(level.getServer(), c, mob);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.6, at.z, 10, 0.4, 0.4, 0.4, 0.02);
        if (c.kind == Kind.TITAN) {
            oreCore(level, owner);
        }
        if (c.burst && owner != null) {
            corpseBurst(level, owner, at);
        }
    }

    /** A companion was killed in the fight: the titan's ore core stays, the pack gets a new alpha, a corpse bursts. */
    private void fell(ServerLevel level, ServerPlayer owner, Companion c) {
        if (c.kind == Kind.TITAN) {
            oreCore(level, owner);
        } else if (c.kind == Kind.WOLF && c.alpha) {
            promoteAlpha(level, owner);
        }
        if (c.burst && c.lastPos != null && c.lastLevel != null) {
            corpseBurst(c.lastLevel, owner, c.lastPos);
        }
    }

    /**
     * Erzkern: when the titan ends or falls, the golem comes back if it is gone; else the
     * titan's iron heals the golem in full (Simon 26.10.: no second golem).
     */
    private void oreCore(ServerLevel level, ServerPlayer owner) {
        PlayerSkills skills = owner == null ? null : skillsFor.apply(owner);
        if (!SkillTree.has(skills, "s_orecore")) {
            return;
        }
        int used = count(level, owner.getUUID(), k -> k == Kind.WOLF || k == Kind.GOLEM);
        if (used < slots(skills)) {
            spawn(level, owner, skills, Kind.GOLEM);
            return;
        }
        for (Mob mob : mobsOf(level, owner.getUUID())) {
            Companion g = ALL.get(mob.getUUID());
            if (g != null && g.kind == Kind.GOLEM) {
                mob.heal(mob.getMaxHealth() * (float) v("s_orecore", "heal", 1.0));
                level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
            }
        }
    }

    /** Alphawolf: the alpha fell, so the strongest wolf left takes the lead. */
    private void promoteAlpha(ServerLevel level, ServerPlayer owner) {
        PlayerSkills skills = owner == null ? null : skillsFor.apply(owner);
        if (!SkillTree.has(skills, "s_alpha")
                || count(level, owner.getUUID(), k -> k == Kind.WOLF, o -> o.alpha) > 0) {
            return;
        }
        Mob best = null;
        for (Mob mob : mobsOf(level, owner.getUUID())) {
            Companion w = ALL.get(mob.getUUID());
            if (w != null && w.kind == Kind.WOLF && (best == null || mob.getHealth() > best.getHealth())) {
                best = mob;
            }
        }
        Companion lead = best == null ? null : ALL.get(best.getUUID());
        if (lead == null) {
            return;
        }
        float share = best.getHealth() / Math.max(1f, best.getMaxHealth());
        lead.alpha = true;
        // Only health, damage and size grow (applyStats again would add the speed bonus twice).
        double health = healthScale(skills, Kind.WOLF, true) / healthScale(skills, Kind.WOLF, false);
        double damage = damageScale(skills, Kind.WOLF, true) / damageScale(skills, Kind.WOLF, false);
        AttributeInstance maxHealth = best.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() * health);
        }
        AttributeInstance attack = best.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(attack.getBaseValue() * damage);
        }
        set(best, Attributes.SCALE, v("s_alpha", "size", 1.35));
        best.setHealth(best.getMaxHealth() * share);
        best.setCustomName(Component.translatable("nightsiege.companion.alpha", owner.getDisplayName()));
        level.playSound(null, best.blockPosition(), sound("entity.wolf.growl"), SoundSource.NEUTRAL, 1.0f, 0.6f);
    }

    private void remove(MinecraftServer server, Companion c, Entity entity) {
        ALL.remove(c.id);
        if (c.kind == Kind.MINION && server != null) {
            leaveCandles(server, c.id);
        }
        if (entity != null && entity.isAlive()) {
            entity.discard();
        }
        if (c.kind == Kind.SKELETON && server != null) {
            nextBones.put(c.owner, server.overworld().getGameTime() + Math.round(v("l_cross_bones", "pause", 600)));
        }
        if (c.kind == Kind.WOLF && server != null) {
            nextWolf.put(c.owner, server.overworld().getGameTime() + Math.round(v("d_cross_wolf", "pause", 1200)));
        }
    }

    // ------------------------------------------------------------------
    // events
    // ------------------------------------------------------------------

    /** True while the splash of a raised dead's hit is dealt (no second splash, no second factor). */
    private static boolean splashing;

    /**
     * After a hit: the melee hits of the raised dead splash a little, Totentanz heals, the
     * moon pack heals the summoner, Golemwerk throws.
     */
    public void afterDamage(LivingEntity victim, DamageSource source, float taken) {
        Entity attacker = source.getEntity();
        Companion hitter = attacker == null ? null : ALL.get(attacker.getUUID());
        if (hitter == null || !(victim instanceof Enemy) || taken <= 0 || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(hitter.owner);
        PlayerSkills skills = owner == null ? null : skillsFor.apply(owner);
        boolean melee = source.getDirectEntity() == attacker;
        if (hitter.kind == Kind.MINION && melee && !splashing && attacker instanceof LivingEntity dead) {
            // The melee hits of the raised dead splash onto the monsters around the target.
            float share = (float) (taken * v("d_raise", "splash", 0.3));
            double reach = v("d_raise", "splashradius", 2.0);
            if (share > 0.05f) {
                splashing = true;
                try {
                    DamageSource splash = level.damageSources().mobAttack(dead);
                    for (LivingEntity near : CombatEffects.enemies(level, victim.position(), reach, 1.5)) {
                        if (near != victim && near != attacker && !isCompanion(near)) {
                            near.invulnerableTime = 0;
                            near.hurtServer(level, splash, share);
                        }
                    }
                } finally {
                    splashing = false;
                }
            }
        }
        // Totentanz: the wolf's bites mend the raised dead.
        if (hitter.kind == Kind.WOLF && SkillTree.has(skills, "d_dance")) {
            healKind(level, hitter.owner, Kind.MINION, (float) bonus(SkillEffect.DEATH_DANCE, skills));
        }
        if (hitter.kind == Kind.SPIRIT && owner != null && SkillTree.has(skills, "s_moonpack")) {
            owner.heal((float) v("s_moonpack", "heal", 1.0));
        }
        // Golemwerk: a golem's punch throws the monster away.
        if ((hitter.kind == Kind.GOLEM || hitter.kind == Kind.TITAN) && owner != null && SkillTree.has(skills, "s_golemwork")) {
            Vec3 away = victim.position().subtract(attacker.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0e-4) {
                away = away.normalize();
                double push = v("s_golemwork", "push", 0.9);
                victim.push(away.x * push, v("s_golemwork", "lift", 0.35), away.z * push);
                victim.hurtMarked = true;
            }
        }
    }

    /** A companion killed a monster: Seelenpakt heals the summoner, Seelenernte the companion and the staff's pause. */
    public void onKill(Entity killer, Entity killed) {
        Companion c = killer == null ? null : ALL.get(killer.getUUID());
        if (c == null || !(killer.level() instanceof ServerLevel level) || !(killed instanceof Enemy)) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(c.owner);
        PlayerSkills skills = owner == null ? null : skillsFor.apply(owner);
        if (owner == null) {
            return;
        }
        if (SkillTree.has(skills, "d_pact")) {
            owner.heal((float) bonus(SkillEffect.SOUL_PACT, skills));   // Seelenpakt
        }
        if (SkillTree.has(skills, "l_cross_harvest") && killer instanceof LivingEntity living) {
            living.heal(living.getMaxHealth() * (float) v("l_cross_harvest", "heal", 0.2));
            long ready = nextCall.getOrDefault(c.owner, 0L);
            nextCall.put(c.owner, ready - Math.round(bonus(SkillEffect.SOUL_HARVEST, skills) * 20.0));
            level.sendParticles(ParticleTypes.SOUL, killed.getX(), killed.getY() + 0.8, killed.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** A companion from before a restart (or another night) has no summoner any more. */
    public static void onEntityLoad(Entity entity) {
        if (isCompanion(entity) && !ALL.containsKey(entity.getUUID())) {
            entity.discard();
        }
    }

    /** End of the night: every companion goes, the bone walls fall. */
    public void dismissAll(MinecraftServer server) {
        for (Companion c : new ArrayList<>(ALL.values())) {
            Entity entity = find(server, c.id);
            if (entity != null && entity.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.5, entity.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            }
            remove(server, c, entity);
        }
        ALL.clear();
        for (WallBlock w : walls) {
            TempBlocks.remove(w.level(), w.pos());
        }
        walls.clear();
        keepSouls(server);
        nextCall.clear();
        attackReady.clear();
        boostUntil.clear();
        orders.clear();
        nextSpell.clear();
        nextBones.clear();
        howlUntil.clear();
        nextHowl.clear();
        nextLead.clear();
        souls.clear();
        corpses.clear();
        raisedCount.clear();
        nextWolf.clear();
        pending.clear();
        missiles.clear();
        zones.clear();
        TAUNTS.clear();
        TAUNT_PAUSE.clear();
        CURSED.clear();
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    /** One summoner's companions go (they fell or left the night). */
    public void dismiss(ServerPlayer owner) {
        if (owner == null || !(owner.level() instanceof ServerLevel level)) {
            return;
        }
        for (Companion c : new ArrayList<>(ALL.values())) {
            if (c.owner.equals(owner.getUUID())) {
                remove(level.getServer(), c, find(level.getServer(), c.id));
            }
        }
        orders.remove(owner.getUUID());
    }

    // ------------------------------------------------------------------
    // way of the dead: souls, corpses, the army
    // ------------------------------------------------------------------

    /**
     * A monster of the night fell: it may be raised for half a minute, and every summoner of
     * the dead within 16 blocks gets a soul (Seelengier: sometimes two).
     */
    public void onMonsterDeath(Mob mob, boolean elite) {
        if (!(mob.level() instanceof ServerLevel level) || isCompanion(mob)) {
            return;
        }
        long now = level.getGameTime();
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        corpses.addLast(new Corpse(id, level, mob.position(), now, elite));
        long age = Math.round(v("d_army", "age", 600));
        while (!corpses.isEmpty() && (now - corpses.peekFirst().time() > age || corpses.size() > 80)) {
            corpses.pollFirst();
        }
        for (ServerPlayer player : level.players()) {
            double range = v("d_raise", "range", 16.0);
            if (player.distanceToSqr(mob) > range * range) {
                continue;
            }
            PlayerSkills skills = skillsFor.apply(player);
            if (skills == null || !SkillTree.WAY_DEAD.equals(skills.pathTaken(tree)) || !SkillTree.has(skills, "d_raise")) {
                continue;
            }
            int gain = 1 + (player.getRandom().nextDouble() < bonus(SkillEffect.SOUL_GREED, skills) ? 1 : 0);
            addSouls(player, skills, gain);
        }
    }

    /** Souls of a summoner of the dead (3 at the start of the night). */
    public int soulsOf(UUID owner) {
        return souls.getOrDefault(owner, (int) Math.round(v("d_raise", "start", 3)));
    }

    int soulCap(PlayerSkills skills) {
        return soulCapOf(skills);
    }

    /** Most souls a summoner of the dead can hold (the soul keeper twice as many). */
    public static int soulCapOf(PlayerSkills skills) {
        return (int) Math.round(SkillTree.has(skills, "d_soulkeeper") ? v("d_soulkeeper", "cap", 20) : v("d_raise", "cap", 10));
    }

    /** Souls a night starts with: three, two more per stage of bone dust, and what the soul vessel kept. */
    public static int startSouls(PlayerSkills skills) {
        int kept = skills != null && gain(skills, "seelengefaess") > 0 ? Math.max(0, Math.min(5, skills.keptSouls)) : 0;
        return Math.min(soulCapOf(skills), (int) Math.round(v("d_raise", "start", 3)) + 2 * gain(skills, "knochenstaub") + kept);
    }

    /** The kit of the night was handed out: the souls start where the staff shows them. */
    public void beginNight(ServerPlayer player, PlayerSkills skills) {
        if (player != null && skills != null) {
            souls.putIfAbsent(player.getUUID(), startSouls(skills));
        }
    }

    /**
     * The night begins: the summoner's first companions are already there – the wolves of
     * Rudelruf (at most the places), the golemsmith's golem with its snow golem. The way of
     * the dead starts with its souls only (its wolf of the cross talent comes in nightTick).
     */
    public void startNight(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != com.mki.nightsiege.core.SkillClass.SUMMONER
                || SkillTree.WAY_DEAD.equals(skills.pathTaken(tree))) {
            return;
        }
        int used = count(level, player.getUUID(), k -> k == Kind.WOLF || k == Kind.GOLEM);
        int room = slots(skills) - used;
        if (SkillTree.has(skills, "s_golemsmith")) {
            if (room > 0 && count(level, player.getUUID(), k -> k == Kind.GOLEM) == 0) {
                spawn(level, player, skills, Kind.GOLEM);
            }
        } else if (SkillTree.has(skills, "s_packcall")) {
            int wolves = (int) Math.round(v("s_packcall", "startwolves", 2));
            for (int i = 0; i < Math.min(wolves, room); i++) {
                spawn(level, player, skills, Kind.WOLF);
            }
        }
        callExtras(level, player, skills, level.getGameTime(), false);
    }

    /** Seelengefäß: at the end of the night up to five souls stay for the next one. */
    private void keepSouls(MinecraftServer server) {
        SiegeManager m = SiegeManager.get();
        if (m == null || server == null) {
            return;
        }
        for (Map.Entry<UUID, Integer> e : souls.entrySet()) {
            PlayerSkills saved = m.save().skillsOf(e.getKey().toString());
            if (saved != null && gain(saved, "seelengefaess") > 0 && SkillTree.WAY_DEAD.equals(saved.pathTaken(tree))) {
                saved.keptSouls = Math.max(0, Math.min(5, e.getValue()));
            }
        }
    }

    private void addSouls(ServerPlayer player, PlayerSkills skills, int amount) {
        int cap = soulCap(skills);
        int now = Math.max(0, Math.min(cap, soulsOf(player.getUUID()) + amount));
        souls.put(player.getUUID(), now);
        showSouls(player, now, cap);
    }

    /** The staff shows the souls as its bar; the action bar says the number. */
    private static void showSouls(ServerPlayer player, int count, int cap) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ModContent.STAFF != null && stack.is(ModContent.STAFF)) {
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack,
                        tag -> {
                            tag.putInt(SummonStaffItem.SOULS, count);
                            tag.putInt(SummonStaffItem.SOUL_CAP, cap);
                        });
            }
        }
        CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.souls", count, cap));
    }

    /** ↔ Rudelruf: one wolf runs with the summoner of the dead (a pause after it fell). */
    private void crossWolf(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now) {
        if (SkillTree.has(skills, "d_cross_wolf") && count(level, player.getUUID(), k -> k == Kind.WOLF) == 0
                && now >= nextWolf.getOrDefault(player.getUUID(), 0L)) {
            spawn(level, player, skills, Kind.WOLF);
        }
    }

    /** The newest corpse near a spot (the champion only with the mastery), taken off the list. */
    private Corpse takeCorpse(ServerLevel level, Vec3 near, double range, boolean champions) {
        long now = level.getGameTime();
        java.util.Iterator<Corpse> it = corpses.descendingIterator();
        while (it.hasNext()) {
            Corpse c = it.next();
            if (c.level() == level && now - c.time() <= Math.round(v("d_army", "age", 600)) && c.pos().distanceToSqr(near) <= range * range
                    && (!c.elite() || champions) && !NOT_RAISED.contains(c.typeId())) {
                it.remove();
                return c;
            }
        }
        return null;
    }

    /** A fallen monster rises again for its summoner (strength: share of tonight's monster), with the shared mind. */
    private Mob raise(ServerLevel level, ServerPlayer owner, PlayerSkills skills, Corpse corpse, double strength, long until,
                      boolean army) {
        Identifier id = Identifier.tryParse(corpse.typeId());
        net.minecraft.world.entity.EntityType<?> type = id == null ? null
                : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(id);
        Entity entity = type == null ? null : type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        mob.snapTo(corpse.pos().x, corpse.pos().y, corpse.pos().z, owner.getRandom().nextFloat() * 360f, 0f);
        try {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
        } catch (RuntimeException e) {
            // a monster from another mod that cannot set itself up here still rises bare
        }
        if (gain(skills, "grabruestung") > 0) {
            graveArmour(mob);
        }
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            mob.setDropChance(slot, 0f);
        }
        oneMind(mob);
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        Companion c = new Companion(mob.getUUID(), owner.getUUID(), Kind.MINION, level.getGameTime());
        c.until = until;
        c.army = army;
        SiegeManager m = SiegeManager.get();
        double night = m == null ? 1.0 : m.mobHealthFactor();
        double nightDamage = m == null ? 1.0 : m.mobDamageFactor();
        double own = (1.0 + bonus(SkillEffect.NECRO_POWER, skills))
                * (1.0 + (SkillTree.has(skills, "d_lich") ? bonus(SkillEffect.LICH, skills) : 0.0));
        double life = Math.max(v("d_raise", "minhealth", 30.0), mob.getMaxHealth() * strength) * night * (1.0 + bonus(SkillEffect.SUMMON_HEALTH, skills)) * own;
        // Champion der Toten: every 4th of the army rises as a champion, twice as strong and bigger.
        boolean champion = false;
        if (army && SkillTree.has(skills, "d_champion")) {
            int n = raisedCount.merge(owner.getUUID(), 1, Integer::sum);
            champion = n % Math.max(1, (int) Math.round(v("d_champion", "every", 4))) == 0;
        }
        if (champion) {
            life *= v("d_champion", "health", 2.0);
            set(mob, Attributes.SCALE, v("d_champion", "size", 1.3));
        }
        set(mob, Attributes.MAX_HEALTH, Math.max(4.0, life));
        mob.setHealth(mob.getMaxHealth());
        c.damageMul = nightDamage * strength * (1.0 + bonus(SkillEffect.SUMMON_DAMAGE, skills)) * own
                * (champion ? v("d_champion", "damage", 2.0) : 1.0) * v("d_raise", "power", 1.5);
        mob.setCustomName(Component.translatable("nightsiege.companion.minion", owner.getDisplayName())
                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        mob.setCustomNameVisible(true);
        if (gain(skills, "grabkerzen") > 0) {
            // Grabkerzen: the raised dead shine purple through walls.
            SiegeTeams.mark(level.getServer(), mob, SiegeTeams.GRAVE, net.minecraft.world.scores.TeamColor.DARK_PURPLE);
        }
        ALL.put(mob.getUUID(), c);
        if (!level.addFreshEntity(mob)) {
            ALL.remove(mob.getUUID());
            return null;
        }
        c.lastPos = mob.position();
        c.lastLevel = level;
        level.sendParticles(ParticleTypes.SCULK_SOUL, corpse.pos().x, corpse.pos().y + 0.5, corpse.pos().z, 14, 0.4, 0.6, 0.4, 0.03);
        level.sendParticles(ParticleTypes.SOUL, corpse.pos().x, corpse.pos().y + 0.2, corpse.pos().z, 10, 0.5, 0.1, 0.5, 0.01);
        level.playSound(null, mob.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.5f, 1.6f);
        return mob;
    }

    /** Armee der Toten: up to four monsters fallen in the last half minute within 20 blocks rise for 8 s. */
    public boolean armyOfTheDead(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        long now = level.getGameTime();
        long until = now + Math.round(v("d_army", "duration", 160)
                + (SkillTree.has(skills, "d_eternal") ? bonus(SkillEffect.ETERNAL_LEGION, skills) * 20.0 : 0.0));
        int raised = 0;
        int most = (int) Math.round(v("d_army", "max", 4));
        double strength = v("d_army", "str" + rank, 0.8 + 0.1 * rank);
        Corpse corpse;
        while (raised < most && (corpse = takeCorpse(level, player.position(), v("d_army", "radius", 20.0),
                SkillTree.has(skills, "d_champion"))) != null) {
            if (raise(level, player, skills, corpse, strength, until, true) != null) {
                raised++;
            }
        }
        if (raised == 0) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.army_none"));
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 40, 4.0, 0.5, 4.0, 0.05);
        return true;
    }

    /** Seelensturm: every soul at once as a wave of ghosts; damage for each soul. */
    public boolean soulStorm(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        int have = soulsOf(player.getUUID());
        if (have <= 0) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.souls_short", 0, 1));
            return false;
        }
        float damage = have * (float) v("d_soulstorm", "dmg" + rank, 1.0 + rank);
        storm(level, player, damage);
        if (SkillTree.has(skills, "d_echo")) {
            pending.add(new Pending(level.getGameTime() + Math.round(v("d_echo", "delay", 20)), () -> {
                if (player.isAlive()) {
                    storm(level, player, damage * (float) bonus(SkillEffect.ECHO, skills));
                }
            }));
        }
        int left = SkillTree.has(skills, "d_soulflood") ? (int) Math.floor(have * bonus(SkillEffect.SOUL_FLOOD, skills)) : 0;
        souls.put(player.getUUID(), left);
        showSouls(player, left, soulCap(skills));
        return true;
    }

    private void storm(ServerLevel level, ServerPlayer player, float damage) {
        double r = v("d_soulstorm", "radius", 10.0);
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 3.0, r),
                e -> e instanceof Enemy && e.isAlive() && !isCompanion(e))) {
            if (e.distanceToSqr(player) > r * r) {
                continue;
            }
            spellHurt(level, e, source, damage);
            level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 1, e.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
        }
        for (int i = 0; i < 48; i++) {
            double a = i / 48.0 * Math.PI * 2;
            level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX() + Math.cos(a) * 3, player.getY() + 1, player.getZ() + Math.sin(a) * 3,
                    1, Math.cos(a) * 0.4, 0.05, Math.sin(a) * 0.4, 0.4);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** The own companion the summoner looks at, or null. */
    private static Mob lookOwn(ServerLevel level, ServerPlayer player, double reach, double cone) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Mob best = null;
        double bestDot = cone;
        for (Mob mob : mobsOf(level, player.getUUID())) {
            Vec3 to = mob.position().add(0, mob.getBbHeight() * 0.5, 0).subtract(eye);
            double dist = to.length();
            if (dist > reach || dist < 1.0e-4) {
                continue;
            }
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = mob;
            }
        }
        return best;
    }

    private void healKind(ServerLevel level, UUID owner, Kind kind, float amount) {
        for (Companion c : ALL.values()) {
            if (c.owner.equals(owner) && c.kind == kind && level.getEntity(c.id) instanceof Mob mob && mob.isAlive()) {
                mob.heal(amount);
            }
        }
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, int steps) {
        int n = Math.max(steps, (int) (from.distanceTo(to) * 2));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.lerp(to, i / (double) n);
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // ------------------------------------------------------------------
    // crate gains: gadgets, the steed, the hawk, grave candles, soul swap
    // ------------------------------------------------------------------

    /** A right click with one of the summoner's kit gadgets (whistle, steed, bowl, bell). */
    public void useGadget(ServerPlayer player, ServerLevel level, ItemStack stack, String id) {
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != com.mki.nightsiege.core.SkillClass.SUMMONER) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_summoner_only"));
            return;
        }
        switch (id) {
            case "rueckrufpfeife" -> recall(level, player, stack);
            case "geisterross" -> steed(level, player, skills, stack);
            case "futternapf" -> feedingBowl(level, player, stack);
            case "totenglocke" -> deathBell(level, player, skills, stack);
            default -> {
            }
        }
    }

    /** Rückrufpfeife: every companion is at the summoner's side at once. 10 s pause. */
    private void recall(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int came = 0;
        orders.remove(player.getUUID());
        for (Companion c : new ArrayList<>(ALL.values())) {
            if (!c.owner.equals(player.getUUID()) || c.kind == Kind.HAWK || !(level.getEntity(c.id) instanceof Mob mob)
                    || !mob.isAlive() || mob.isVehicle()) {
                continue;
            }
            Vec3 at = spot(level, player);
            mob.teleportTo(at.x, at.y, at.z);
            mob.getNavigation().stop();
            steer(mob, null);
            level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 6, 0.3, 0.3, 0.3, 0.02);
            came++;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.2f, 1.8f);
        player.getCooldowns().addCooldown(stack, 200);
        CombatEffects.actionBar(player, Component.translatable("nightsiege.gadget.rueckrufpfeife.done", came));
    }

    /** Geisterross: a saddled skeleton horse for the night; if it stands already, it comes. */
    private void steed(ServerLevel level, ServerPlayer player, PlayerSkills skills, ItemStack stack) {
        for (Companion c : ALL.values()) {
            if (c.owner.equals(player.getUUID()) && c.kind == Kind.MOUNT && level.getEntity(c.id) instanceof Mob horse && horse.isAlive()) {
                Vec3 at = spot(level, player);
                horse.teleportTo(at.x, at.y, at.z);
                level.playSound(null, horse.blockPosition(), SoundEvents.SKELETON_HORSE_AMBIENT, SoundSource.NEUTRAL, 1f, 1f);
                player.getCooldowns().addCooldown(stack, 60);
                CombatEffects.actionBar(player, Component.translatable("nightsiege.gadget.geisterross.comes"));
                return;
            }
        }
        Mob horse = spawn(level, player, skills, Kind.MOUNT);
        if (horse == null) {
            return;
        }
        level.playSound(null, horse.blockPosition(), SoundEvents.SKELETON_HORSE_AMBIENT, SoundSource.NEUTRAL, 1.2f, 0.8f);
        player.getCooldowns().addCooldown(stack, 100);
        CombatEffects.actionBar(player, Component.translatable("nightsiege.gadget.geisterross.called"));
    }

    /** Futternapf: companions within five blocks heal for thirty seconds. 90 s pause. */
    private void feedingBowl(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int fed = 0;
        for (Mob mob : mobsOf(level, player.getUUID())) {
            if (mob.distanceToSqr(player) <= 25.0) {
                mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1, false, true));
                level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
                fed++;
            }
        }
        if (fed == 0) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.gadget.futternapf.none"));
            return;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1f, 0.9f);
        player.getCooldowns().addCooldown(stack, 1800);
        CombatEffects.actionBar(player, Component.translatable("nightsiege.gadget.futternapf.done", fed));
    }

    /**
     * Totenglocke (since the spells, 26 October 2026): the bell calls souls – three at once
     * (up to the most you can hold). 60 s pause.
     */
    private void deathBell(ServerLevel level, ServerPlayer player, PlayerSkills skills, ItemStack stack) {
        int before = soulsOf(player.getUUID());
        addSouls(player, skills, 3);
        int got = soulsOf(player.getUUID()) - before;
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 12, 0.5, 0.6, 0.5, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.6f, 0.6f);
        player.getCooldowns().addCooldown(stack, 1200);
        CombatEffects.actionBar(player, Component.translatable("nightsiege.gadget.totenglocke.done", got));
    }

    /** Grabrüstung: the raised dead wear iron where they wear nothing. */
    private static void graveArmour(Mob mob) {
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET};
        net.minecraft.world.item.Item[] iron = {net.minecraft.world.item.Items.IRON_HELMET, net.minecraft.world.item.Items.IRON_CHESTPLATE,
                net.minecraft.world.item.Items.IRON_LEGGINGS, net.minecraft.world.item.Items.IRON_BOOTS};
        for (int i = 0; i < slots.length; i++) {
            if (mob.getItemBySlot(slots[i]).isEmpty()) {
                mob.setItemSlot(slots[i], new ItemStack(iron[i]));
            }
        }
    }

    private static void leaveCandles(MinecraftServer server, UUID id) {
        try {
            net.minecraft.world.scores.Scoreboard board = server.getScoreboard();
            net.minecraft.world.scores.PlayerTeam team = board.getPlayerTeam(SiegeTeams.GRAVE);
            if (team != null && team.getPlayers().contains(id.toString())) {
                board.removePlayerFromTeam(id.toString(), team);
            }
        } catch (RuntimeException e) {
            // the team is only colour
        }
    }

    /**
     * Every tick of a running night: the scouting hawks fly their circle; once a second every
     * summoner with the Spähfalke gets theirs and the monsters within 30 blocks light up, the
     * wolf of ↔ Rudelruf comes to the summoner of the dead, and the chosen spell shows while
     * the staff is in hand.
     */
    public void nightTick(ServerLevel level, List<ServerPlayer> upright) {
        long now = level.getGameTime();
        for (Companion c : ALL.values()) {
            if (c.kind != Kind.HAWK || !(level.getEntity(c.id) instanceof Mob hawk)) {
                continue;
            }
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(c.owner);
            if (owner == null || owner.level() != level) {
                continue;
            }
            double a = (now % 120) / 120.0 * Math.PI * 2;
            double x = owner.getX() + Math.cos(a) * 3.5;
            double z = owner.getZ() + Math.sin(a) * 3.5;
            float yaw = (float) Math.toDegrees(a);   // flying along the circle
            hawk.setPos(x, owner.getY() + 4.5, z);
            hawk.setYRot(yaw);
            hawk.setYHeadRot(yaw);
            hawk.setYBodyRot(yaw);
        }
        if (now % 20 != 0) {
            return;
        }
        for (ServerPlayer player : upright) {
            if (player.level() != level) {
                continue;
            }
            PlayerSkills skills = skillsFor.apply(player);
            if (skills == null || skills.clazz() != com.mki.nightsiege.core.SkillClass.SUMMONER) {
                continue;
            }
            boolean dead = SkillTree.WAY_DEAD.equals(skills.pathTaken(tree));
            if (dead) {
                crossWolf(level, player, skills, now);
                if (now % 40 == 0 && ModContent.STAFF != null && player.getMainHandItem().is(ModContent.STAFF)) {
                    showSpell(player, skills);
                }
                continue;
            }
            if (gain(skills, "spaehfalke") <= 0) {
                continue;
            }
            if (count(level, player.getUUID(), k -> k == Kind.HAWK) == 0) {
                spawn(level, player, skills, Kind.HAWK);
            }
            for (Mob mob : GainGear.enemies(level, player.position(), 30.0)) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
            }
        }
    }

    /** Seelentausch (second ult): the summoner and the companion looked at swap places. */
    public boolean soulSwap(ServerLevel level, ServerPlayer player) {
        Mob partner = lookOwn(level, player, 48.0, 0.9);
        if (partner == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.swap_none"));
            return false;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        Vec3 me = player.position();
        Vec3 it = partner.position();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, me.x, me.y + 1, me.z, 30, 0.4, 0.8, 0.4, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, it.x, it.y + 1, it.z, 30, 0.4, 0.8, 0.4, 0.05);
        partner.teleportTo(me.x, me.y, me.z);
        partner.getNavigation().stop();
        player.teleportTo(it.x, it.y, it.z);
        player.resetFallDistance();
        level.playSound(null, BlockPos.containing(me), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.8f);
        level.playSound(null, BlockPos.containing(it), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** A summoner's fighting companions (the steed and the hawk do not fight). */
    private static List<Mob> mobsOf(ServerLevel level, UUID owner) {
        List<Mob> out = new ArrayList<>();
        for (Companion c : ALL.values()) {
            if (c.owner.equals(owner) && c.kind != Kind.MOUNT && c.kind != Kind.HAWK
                    && level.getEntity(c.id) instanceof Mob mob && mob.isAlive()) {
                out.add(mob);
            }
        }
        return out;
    }

    private static int count(ServerLevel level, UUID owner, Predicate<Kind> kinds) {
        return count(level, owner, kinds, c -> true);
    }

    private static int count(ServerLevel level, UUID owner, Predicate<Kind> kinds, Predicate<Companion> also) {
        int n = 0;
        for (Companion c : ALL.values()) {
            if (c.owner.equals(owner) && kinds.test(c.kind) && also.test(c) && level.getEntity(c.id) instanceof Mob mob && mob.isAlive()) {
                n++;
            }
        }
        return n;
    }

    /** How many companions of one summoner go for this victim (wolves only, or all). */
    private int huntersOn(LivingEntity victim, UUID owner, boolean wolvesOnly) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return 0;
        }
        int n = 0;
        for (Companion c : ALL.values()) {
            if (!c.owner.equals(owner) || (wolvesOnly && c.kind != Kind.WOLF && c.kind != Kind.SPIRIT)) {
                continue;
            }
            if (level.getEntity(c.id) instanceof Mob mob && mob.getTarget() == victim) {
                n++;
            }
        }
        return n;
    }

    /** True when a wolf and the skeleton of one summoner are both on this victim. */
    private boolean mixedOn(LivingEntity victim, UUID owner) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return false;
        }
        boolean wolf = false;
        boolean bones = false;
        for (Companion c : ALL.values()) {
            if (c.owner.equals(owner) && level.getEntity(c.id) instanceof Mob mob && mob.getTarget() == victim) {
                wolf |= c.kind == Kind.WOLF;
                bones |= c.kind == Kind.SKELETON;
            }
        }
        return wolf && bones;
    }

    /** Companions of one summoner, for the window and the tests. */
    public static int countOf(UUID owner) {
        int n = 0;
        for (Companion c : ALL.values()) {
            if (c.owner.equals(owner)) {
                n++;
            }
        }
        return n;
    }

    /** Companions of one summoner by kind (wolf, golem, snow, skeleton, spirit, titan, minion), for the tests. */
    public static int countOf(UUID owner, String kind) {
        int n = 0;
        for (Companion c : ALL.values()) {
            if (c.owner.equals(owner) && c.kind.name().equalsIgnoreCase(kind)) {
                n++;
            }
        }
        return n;
    }

    /** Where a summoner's ghosts are and how they fare, for the tests. */
    public static String ghostReport(ServerLevel level, ServerPlayer owner) {
        StringBuilder out = new StringBuilder();
        for (Companion c : ALL.values()) {
            if (!c.owner.equals(owner.getUUID()) || c.kind != Kind.GHOST) {
                continue;
            }
            Entity e = level.getEntity(c.id);
            if (!(e instanceof Mob mob)) {
                out.append(" [gone]");
                continue;
            }
            LivingEntity target = mob.getTarget();
            out.append(String.format(java.util.Locale.ROOT, " [hp=%.0f/%.0f d=%.1f dy=%.1f glow=%s name=%s target=%s left=%ds]",
                    mob.getHealth(), mob.getMaxHealth(), Math.sqrt(mob.distanceToSqr(owner)), mob.getY() - owner.getY(),
                    mob.hasGlowingTag(), mob.isCustomNameVisible(),
                    target == null ? "-" : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath(),
                    c.until < 0 ? -1 : (c.until - level.getGameTime()) / 20));
        }
        return out.length() == 0 ? " none" : out.toString();
    }

    private static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path));
    }
}
