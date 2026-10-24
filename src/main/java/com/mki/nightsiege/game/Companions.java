package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
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
 * The summoner's companions (way of the wild, 25 October 2026): wolves, iron golems, snow
 * golems, a raised skeleton archer, and for the ultimates spirit wolves and the titan.
 *
 * <p>They belong to one player, follow and fight for them, never hurt a player and vanish
 * at the end of the night (or when their summoner falls or leaves). Their kills count for
 * the night and give the summoner points. They grow with the night like the monsters do.</p>
 *
 * <p>The vanilla mobs keep their own fighting (bite, punch, snowballs, bow); this class
 * only picks their targets, keeps them near their summoner and adds the skills.</p>
 */
public final class Companions {

    public static final String TAG = "nightsiege_companion";

    /** What a companion is (decides its slot, its stats and its skills). */
    enum Kind { WOLF, GOLEM, SNOW, SKELETON, SPIRIT, TITAN, MINION, GHOST }

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
        /** Raised by the army of the dead (no place taken). */
        boolean army;
        /** Factor on every hit it deals (the raised dead and ghosts carry their strength here). */
        double damageMul = 1.0;

        Companion(UUID id, UUID owner, Kind kind, long born) {
            this.id = id;
            this.owner = owner;
            this.kind = kind;
            this.born = born;
        }
    }

    /** Every living companion by its entity id (static: the damage and target hooks ask without an instance). */
    private static final Map<UUID, Companion> ALL = new HashMap<>();
    private static Companions instance;

    /** Outside a night (creative tests) companions go after this many ticks. */
    private static final int TEST_LIFETIME = 3600;
    private static final double FOLLOW = 5.0;
    private static final double FIGHT = 14.0;
    private static final double TELEPORT = 30.0;

    private final SkillTree tree;
    private final Function<ServerPlayer, PlayerSkills> skillsFor;
    private final Supplier<SiegeConfig> config;
    /** Owner → game time the staff can call again (also shortened by Seelenernte). */
    private final Map<UUID, Long> nextCall = new HashMap<>();
    /** Owner → game time the skeleton guard may rise again after it fell. */
    private final Map<UUID, Long> nextBones = new HashMap<>();
    /** Owner → end of the current howl (wolves faster and stronger). */
    private final Map<UUID, Long> howlUntil = new HashMap<>();
    private final Map<UUID, Long> nextHowl = new HashMap<>();

    /** A monster that fell tonight: it can be raised for half a minute. */
    record Corpse(String typeId, ServerLevel level, Vec3 pos, long time, boolean elite) {
    }

    /** Way of the dead: souls per summoner (3 at the start of a night). */
    private final Map<UUID, Integer> souls = new HashMap<>();
    private final java.util.ArrayDeque<Corpse> corpses = new java.util.ArrayDeque<>();
    /** Owner → game time the wolf of the cross talent may come again. */
    private final Map<UUID, Long> nextWolf = new HashMap<>();
    /** Owner → game time the staff may raise again (a short pause against double clicks). */
    private final Map<UUID, Long> nextRaise = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();

    private record Pending(long at, Runnable action) {
    }

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

    // ------------------------------------------------------------------
    // questions from everywhere
    // ------------------------------------------------------------------

    public static boolean isCompanion(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG);
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

    /** A companion picks no players and no other companions; nothing else changes. */
    public static boolean mayTarget(Mob mob, LivingEntity target) {
        if (!isCompanion(mob) || target == null) {
            return true;
        }
        return !(target instanceof Player) && !isCompanion(target);
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
            if (c != null && c.kind == Kind.SPIRIT) {
                return false;   // spirits cannot be hurt
            }
        }
        if (isCompanion(attacker) && (victim instanceof Player || isCompanion(victim))) {
            return false;
        }
        if (source.getDirectEntity() instanceof Snowball && isCompanion(attacker) && victim instanceof Enemy) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1));
        }
        return true;
    }

    /** Factor on a hit, for or against a companion (called from the damage scaling). */
    public static float damageFactor(LivingEntity victim, DamageSource source) {
        Companions self = instance;
        if (self == null || ALL.isEmpty()) {
            return 1f;
        }
        float factor = 1f;
        Companion hurt = ALL.get(victim.getUUID());
        if (hurt != null) {
            PlayerSkills skills = self.skillsOf(hurt.owner, victim);
            factor *= (float) Math.max(0.2, 1.0 - self.bonus(SkillEffect.SUMMON_TOUGHNESS, skills));
        }
        Entity attacker = source.getEntity();
        Companion hitter = attacker == null ? null : ALL.get(attacker.getUUID());
        if (hitter != null && attacker instanceof LivingEntity living) {
            PlayerSkills skills = self.skillsOf(hitter.owner, attacker);
            // Wildheit: wounded companions bite harder.
            if (SkillTree.has(skills, "s_wildness") && living.getHealth() < living.getMaxHealth() * 0.5f) {
                factor *= 1.25f;
            }
            if (hitter.kind == Kind.WOLF || hitter.kind == Kind.SPIRIT) {
                // Rudeljagd: wolves on the same prey.
                if (SkillTree.has(skills, "s_packhunt") && self.huntersOn(victim, hitter.owner, true) >= 2) {
                    factor *= 1.2f;
                }
                Long howl = self.howlUntil.get(hitter.owner);
                if (howl != null && victim.level().getGameTime() < howl) {
                    factor *= 1.3f;
                }
            }
            // Mischrudel: wolves and the skeleton together.
            if (SkillTree.has(skills, "s_mixedpack") && (hitter.kind == Kind.WOLF || hitter.kind == Kind.SKELETON)
                    && self.mixedOn(victim, hitter.owner)) {
                factor *= 1.15f;
            }
            if (hitter.kind == Kind.SKELETON) {
                factor *= (float) self.damageScale(skills, hitter.kind, false);   // arrows: no attribute to carry it
            }
            if (hitter.kind == Kind.MINION || hitter.kind == Kind.GHOST) {
                factor *= (float) hitter.damageMul;
                // Skelettschützen: the raised archers shoot harder.
                if (hitter.kind == Kind.MINION && source.getDirectEntity() != attacker && SkillTree.has(skills, "d_archers")) {
                    factor *= 1.5f;
                }
            }
        }
        // Unheilige Kraft: the summoner hits harder for every raised dead.
        if (attacker instanceof ServerPlayer summoner && victim instanceof Enemy && !isCompanion(victim)) {
            PlayerSkills skills = self.skillsFor.apply(summoner);
            if (SkillTree.has(skills, "d_unholy") && summoner.level() instanceof ServerLevel level) {
                int raised = count(level, summoner.getUUID(), k -> k == Kind.MINION);
                factor *= (float) (1.0 + self.bonus(SkillEffect.UNHOLY_MIGHT, skills) * raised);
            }
        }
        return factor;
    }

    // ------------------------------------------------------------------
    // the staff
    // ------------------------------------------------------------------

    /**
     * Right click with the staff: call (wolf, or golem for the golemsmith; the skeleton guard
     * and the snow golems come along). Sneaking at a monster sends every companion at it.
     */
    public void useStaff(ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != com.mki.nightsiege.core.SkillClass.SUMMONER) {
            CombatEffects.actionBar(player, Component.translatable(skills == null
                    ? "nightsiege.msg.staff_night_only" : "nightsiege.msg.staff_summoner_only"));
            return;
        }
        long now = level.getGameTime();
        boolean dead = SkillTree.WAY_DEAD.equals(skills.pathTaken(tree));
        if (player.isShiftKeyDown()) {
            LivingEntity prey = CombatEffects.lookTarget(level, player, 32.0);
            if (prey == null && dead) {
                Mob own = lookOwn(level, player, 16.0);
                if (own != null && SkillTree.has(skills, "d_corpse") && ALL.get(own.getUUID()) != null
                        && ALL.get(own.getUUID()).kind == Kind.MINION) {
                    corpseBlast(level, player, skills, own);
                    return;
                }
                if (SkillTree.has(skills, "d_soulshield")) {
                    soulShield(player, skills);
                    return;
                }
            }
            if (prey == null) {
                CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_no_prey"));
                return;
            }
            int sent = 0;
            for (Mob mob : mobsOf(level, player.getUUID())) {
                mob.setTarget(prey);
                sent++;
            }
            prey.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_attack", sent));
            level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.7f, 1.4f);
            return;
        }
        if (dead) {
            raiseWithStaff(level, player, skills, stack, now);
            return;
        }
        boolean golems = SkillTree.has(skills, "s_golemsmith");
        if (!golems && !SkillTree.has(skills, "s_packcall")) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_nothing"));
            return;
        }
        long ready = nextCall.getOrDefault(player.getUUID(), 0L);
        if (now < ready && !player.getAbilities().instabuild) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_wait", (ready - now + 19) / 20));
            return;
        }
        int slots = slots(skills);
        int used = count(level, player.getUUID(), k -> k == Kind.WOLF || k == Kind.GOLEM);
        if (used >= slots) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_full", used, slots));
            return;
        }
        Mob called = golems ? spawn(level, player, skills, Kind.GOLEM) : spawn(level, player, skills, Kind.WOLF);
        if (called == null) {
            return;
        }
        if (golems && SkillTree.has(skills, "s_snowguard")) {
            int snow = count(level, player.getUUID(), k -> k == Kind.SNOW);
            for (int i = snow; i < 2; i++) {
                spawn(level, player, skills, Kind.SNOW);
            }
        }
        if (SkillTree.has(skills, "l_cross_bones") && count(level, player.getUUID(), k -> k == Kind.SKELETON) == 0
                && now >= nextBones.getOrDefault(player.getUUID(), 0L)) {
            spawn(level, player, skills, Kind.SKELETON);
        }
        int pause = golems ? 60 * 20 : (int) Math.round((23.0 - bonus(SkillEffect.PACK_CALL, skills)) * 20.0);
        if (player.getAbilities().instabuild) {
            pause = 20;
        }
        nextCall.put(player.getUUID(), now + pause);
        player.getCooldowns().addCooldown(stack, pause);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.8f, golems ? 0.7f : 1.3f);
        CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_called",
                Component.translatable(golems ? "nightsiege.companion.golem" : "nightsiege.companion.wolf"), used + 1, slots));
    }

    /** For the tests: the staff may call again at once. */
    public void resetPause(UUID owner) {
        nextCall.remove(owner);
    }

    /** For the tests: calls one companion of a kind (wolf, golem, snow, skeleton) without the staff. */
    public Mob testCall(ServerPlayer owner, String kind) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return null;
        }
        return spawn(level, owner, skillsFor.apply(owner), Kind.valueOf(kind.toUpperCase(java.util.Locale.ROOT)));
    }

    /** Wolves and golems a summoner may have at once. */
    int slots(PlayerSkills skills) {
        if (SkillTree.has(skills, "s_shepherd")) {
            return 1;   // Einsamer Hirte: one, twice as strong
        }
        int slots = 2 + (int) Math.round(bonus(SkillEffect.PACK_SLOTS, skills));
        if (SkillTree.has(skills, "s_beastlord")) {
            slots++;
        }
        return slots;
    }

    // ------------------------------------------------------------------
    // ultimates
    // ------------------------------------------------------------------

    /** Wilde Jagd: spirit wolves hunt for a while (6 / 7 / 8, ten seconds, Endlose Jagd five more). */
    public boolean wildHunt(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        int count = 5 + rank;
        long until = level.getGameTime() + 200 + (SkillTree.has(skills, "s_endless_hunt") ? 100 : 0);
        for (int i = 0; i < count; i++) {
            Mob wolf = spawn(level, player, skills, Kind.SPIRIT);
            Companion c = wolf == null ? null : ALL.get(wolf.getUUID());
            if (c != null) {
                c.until = until;
                c.rank = rank;
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
        c.until = level.getGameTime() + 300;
        c.rank = rank;
        c.nextSlam = level.getGameTime() + 40;
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
            case MINION -> null;   // the raised dead come from raise()
        };
        if (mob == null) {
            return null;
        }
        Vec3 at = spot(level, owner);
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
            // A skeleton runs from wolves; this one hunts with them.
            mob.getGoalSelector().removeAllGoals(g -> g instanceof net.minecraft.world.entity.ai.goal.AvoidEntityGoal<?>);
        }
        if (mob instanceof Wolf wolf) {
            wolf.setTame(true, true);
            wolf.setOwner(owner);
            wolf.setOrderedToSit(false);
        }
        if (mob instanceof IronGolem golem) {
            golem.setPlayerCreated(true);
        }
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        Companion c = new Companion(mob.getUUID(), owner.getUUID(), kind, level.getGameTime());
        if (kind == Kind.WOLF && SkillTree.has(skills, "s_alpha")
                && count(level, owner.getUUID(), k -> k == Kind.WOLF) == 0) {
            c.alpha = true;   // the first wolf of the pack leads it
        }
        if (kind == Kind.SKELETON || kind == Kind.SNOW || kind == Kind.SPIRIT || kind == Kind.TITAN || kind == Kind.GHOST || c.alpha) {
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
        level.sendParticles(kind == Kind.SPIRIT ? ParticleTypes.SOUL : ParticleTypes.POOF,
                at.x, at.y + 0.6, at.z, 12, 0.4, 0.4, 0.4, 0.02);
        return mob;
    }

    /** A free spot beside the summoner. */
    private static Vec3 spot(ServerLevel level, ServerPlayer owner) {
        for (int i = 0; i < 8; i++) {
            double a = owner.getRandom().nextDouble() * Math.PI * 2;
            double r = 1.5 + owner.getRandom().nextDouble() * 1.5;
            BlockPos pos = BlockPos.containing(owner.getX() + Math.cos(a) * r, owner.getY(), owner.getZ() + Math.sin(a) * r);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(pos);
            }
        }
        return owner.position();
    }

    /** Health, damage, speed and size from the skills and the night. */
    private void applyStats(Mob mob, Companion c, PlayerSkills skills) {
        double health = switch (c.kind) {
            case WOLF, SPIRIT -> 40.0;
            case GOLEM -> 100.0;
            case SNOW -> 20.0;
            case SKELETON -> 30.0;
            case TITAN -> 300.0;
            case MINION, GHOST -> 14.0;
        };
        double damage = switch (c.kind) {
            case WOLF -> 4.0;
            case SPIRIT -> 4.0 * (1.5 + 0.25 * c.rank);
            case GOLEM -> 15.0;
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
            double factor = 1.0 + bonus(SkillEffect.SUMMON_SPEED, skills) + (c.kind == Kind.SPIRIT ? 0.3 : 0.0);
            speed.setBaseValue(speed.getBaseValue() * factor);
        }
        double size = c.alpha ? 1.35 : c.kind == Kind.TITAN ? 2.0 : 1.0;
        if (size != 1.0) {
            set(mob, Attributes.SCALE, size);
        }
        if (c.kind == Kind.TITAN || c.kind == Kind.GOLEM) {
            set(mob, Attributes.KNOCKBACK_RESISTANCE, 1.0);
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
                scale *= 1.5;
            }
        }
        if (alpha) {
            scale *= 2.0;
        }
        if (SkillTree.has(skills, "s_shepherd") && (kind == Kind.WOLF || kind == Kind.GOLEM)) {
            scale *= 2.0;
        }
        return scale;
    }

    double damageScale(PlayerSkills skills, Kind kind, boolean alpha) {
        double scale = (1.0 + bonus(SkillEffect.SUMMON_DAMAGE, skills)) * nightScale(true);
        if (kind == Kind.GOLEM || kind == Kind.TITAN) {
            scale *= 1.0 + bonus(SkillEffect.GOLEM_CALL, skills);
        }
        if (alpha) {
            scale *= 2.0;
        }
        if (SkillTree.has(skills, "s_shepherd") && (kind == Kind.WOLF || kind == Kind.GOLEM)) {
            scale *= 2.0;
        }
        return scale;
    }

    /** Companions grow with the night like the monsters (a share of their growth, config companionScaling). */
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

    private PlayerSkills skillsOf(UUID owner, Entity near) {
        if (!(near.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        return player == null ? null : skillsFor.apply(player);
    }

    // ------------------------------------------------------------------
    // ticking
    // ------------------------------------------------------------------

    /**
     * Every tick (the work is spread over five ticks): companions of summoners who may no
     * longer have them go, the others pick targets, follow and use their skills.
     */
    public void tick(MinecraftServer server, Predicate<ServerPlayer> mayKeep) {
        if (ALL.isEmpty() && pending.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        if (!pending.isEmpty()) {
            for (Pending p : new ArrayList<>(pending)) {
                if (now >= p.at()) {
                    pending.remove(p);
                    p.action().run();
                }
            }
        }
        if (now % 5 != 0) {
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
        // Heulen: every 20 s the pack howls (5 s faster and stronger).
        for (ServerPlayer owner : owners.values()) {
            if (owner == null) {
                continue;
            }
            PlayerSkills skills = skillsFor.apply(owner);
            if (SkillTree.has(skills, "s_howl") && now >= nextHowl.getOrDefault(owner.getUUID(), 0L)
                    && count((ServerLevel) owner.level(), owner.getUUID(), k -> k == Kind.WOLF) > 0) {
                nextHowl.put(owner.getUUID(), now + 400);
                howlUntil.put(owner.getUUID(), now + 100);
                for (Mob wolf : mobsOf((ServerLevel) owner.level(), owner.getUUID())) {
                    Companion c = ALL.get(wolf.getUUID());
                    if (c != null && c.kind == Kind.WOLF) {
                        wolf.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 1, false, true));
                        owner.level().playSound(null, wolf.blockPosition(), sound("entity.wolf.growl"), SoundSource.NEUTRAL, 1.0f, 0.7f);
                        if (c.alpha && SkillTree.has(skills, "s_packleader")) {
                            healPack((ServerLevel) owner.level(), owner, wolf, 4.0f);   // Rudelführer: the alpha heals the pack
                        }
                    }
                }
            }
        }
    }

    private void work(ServerLevel level, ServerPlayer owner, PlayerSkills skills, Companion c, Mob mob, long now) {
        double range = FIGHT + bonus(SkillEffect.CALL_RANGE, skills);
        double toOwner = Math.sqrt(mob.distanceToSqr(owner));
        if (toOwner > TELEPORT || mob.level() != owner.level()) {
            Vec3 at = spot(level, owner);
            mob.teleportTo(at.x, at.y, at.z);
            mob.getNavigation().stop();
            mob.setTarget(null);
            return;
        }
        if (mob instanceof Wolf wolf && wolf.isOrderedToSit()) {
            wolf.setOrderedToSit(false);   // a right click made it sit: in a siege the pack stays on its feet
            wolf.setInSittingPose(false);
        }
        LivingEntity target = mob.getTarget();
        boolean keepTarget = target != null && target.isAlive() && !(target instanceof Player) && !isCompanion(target)
                && target.distanceToSqr(owner) <= (range + 4) * (range + 4);
        if (!keepTarget) {
            target = nearestFoe(level, owner, mob, range);
            mob.setTarget(target);
        }
        if (target == null && toOwner > FOLLOW && !(mob instanceof Wolf)) {
            mob.getNavigation().moveTo(owner, c.kind == Kind.TITAN ? 1.0 : 1.15);
        }
        if (c.kind == Kind.SPIRIT && now % 10 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 0.5, mob.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        }
        if (c.kind == Kind.TITAN && now >= c.nextSlam) {
            c.nextSlam = now + 60;
            slam(level, owner, skills, c, mob);
        }
        // Pflege: every five seconds a heart back.
        if (now % 100 == 0 && SkillTree.has(skills, "s_care") && mob.getHealth() < mob.getMaxHealth()) {
            mob.heal((float) bonus(SkillEffect.CARE, skills));
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /** The nearest monster around the summoner (creepers are left alone, like wolves do). */
    private static LivingEntity nearestFoe(ServerLevel level, ServerPlayer owner, Mob self, double range) {
        AABB box = owner.getBoundingBox().inflate(range, 6.0, range);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof Enemy && e.isAlive() && !isCompanion(e) && !(e instanceof Creeper))) {
            double d = e.distanceToSqr(self);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private void slam(ServerLevel level, ServerPlayer owner, PlayerSkills skills, Companion c, Mob titan) {
        double r = 4.5;
        float damage = (float) ((6.0 + 3.0 * c.rank) * damageScale(skills, Kind.TITAN, false) / 1.5);
        DamageSource source = level.damageSources().mobAttack(titan);
        boolean fist = SkillTree.has(skills, "s_titanfist");
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, titan.getBoundingBox().inflate(r, 2.0, r),
                e -> e instanceof Enemy && e.isAlive() && !isCompanion(e))) {
            e.invulnerableTime = 0;
            e.hurtServer(level, source, damage);
            Vec3 away = e.position().subtract(titan.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 0) : away.normalize();
            // Titanenfaust: the slam flings the monsters away.
            double push = fist ? 1.6 : 0.5;
            e.push(away.x * push, fist ? 0.9 : 0.4, away.z * push);
            e.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, titan.getX(), titan.getY() + 0.2, titan.getZ(), 5, r * 0.4, 0.1, r * 0.4, 0.0);
        level.playSound(null, titan.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    private void healPack(ServerLevel level, ServerPlayer owner, Mob alpha, float amount) {
        for (Mob mob : mobsOf(level, owner.getUUID())) {
            Companion c = ALL.get(mob.getUUID());
            if (c != null && c.kind == Kind.WOLF && mob.distanceToSqr(alpha) <= 64.0) {
                mob.heal(amount);
                level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 0.8, mob.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            }
        }
    }

    /** The time of a spirit or the titan is up; with Erzkern the titan leaves a plain golem behind. */
    private void expire(ServerLevel level, ServerPlayer owner, Companion c, Mob mob) {
        Vec3 at = mob.position();
        remove(level.getServer(), c, mob);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.6, at.z, 10, 0.4, 0.4, 0.4, 0.02);
        if (c.kind == Kind.TITAN && SkillTree.has(skillsFor.apply(owner), "s_orecore")) {
            spawn(level, owner, skillsFor.apply(owner), Kind.GOLEM);
        }
    }

    private void remove(MinecraftServer server, Companion c, Entity entity) {
        ALL.remove(c.id);
        if (entity != null && entity.isAlive()) {
            entity.discard();
        }
        if (c.kind == Kind.SKELETON && server != null) {
            nextBones.put(c.owner, server.overworld().getGameTime() + 600);
        }
        if (c.kind == Kind.WOLF && server != null) {
            nextWolf.put(c.owner, server.overworld().getGameTime() + 1200);
        }
    }

    // ------------------------------------------------------------------
    // events
    // ------------------------------------------------------------------

    /**
     * After a hit: the summoner is credited with what the companions deal; with
     * Hüterbindung whoever hits the summoner is attacked by all of them; spirit wolves of
     * the moon pack heal their summoner.
     */
    public void afterDamage(LivingEntity victim, DamageSource source, float taken) {
        Entity attacker = source.getEntity();
        Companion hitter = attacker == null ? null : ALL.get(attacker.getUUID());
        if (hitter != null && victim instanceof Enemy && taken > 0 && victim.level() instanceof ServerLevel deadLevel
                && (hitter.kind == Kind.MINION || hitter.kind == Kind.GHOST || hitter.kind == Kind.WOLF)) {
            ServerPlayer owner = deadLevel.getServer().getPlayerList().getPlayer(hitter.owner);
            PlayerSkills skills = owner == null ? null : skillsFor.apply(owner);
            boolean melee = source.getDirectEntity() == attacker;
            if (hitter.kind != Kind.WOLF) {
                // Fäulnis (and with the bone legion their arrows too): the hit monster grows weak.
                if (SkillTree.has(skills, "d_rot") && (melee || SkillTree.has(skills, "d_legion"))) {
                    victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                }
                // Ghulhunger: the raised dead feed on their prey.
                if (SkillTree.has(skills, "d_ghoul") && melee && attacker instanceof LivingEntity living) {
                    living.heal(taken * (float) bonus(SkillEffect.GHOUL_HUNGER, skills));
                    if (SkillTree.has(skills, "d_dance")) {
                        healKind(deadLevel, hitter.owner, Kind.WOLF, (float) bonus(SkillEffect.DEATH_DANCE, skills));
                    }
                }
            } else if (SkillTree.has(skills, "d_dance")) {
                // Totentanz: the wolf's bites mend the raised dead.
                healKind(deadLevel, hitter.owner, Kind.MINION, (float) bonus(SkillEffect.DEATH_DANCE, skills));
            }
        }
        if (hitter != null && hitter.kind == Kind.SPIRIT && victim instanceof Enemy && taken > 0
                && victim.level() instanceof ServerLevel level) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(hitter.owner);
            if (owner != null && SkillTree.has(skillsFor.apply(owner), "s_moonpack")) {
                owner.heal(1.0f);
            }
        }
        // Golemwerk: a golem's punch throws twice as far.
        if (hitter != null && (hitter.kind == Kind.GOLEM || hitter.kind == Kind.TITAN) && victim instanceof Enemy && taken > 0
                && victim.level() instanceof ServerLevel golemLevel) {
            ServerPlayer owner = golemLevel.getServer().getPlayerList().getPlayer(hitter.owner);
            if (owner != null && SkillTree.has(skillsFor.apply(owner), "s_golemwork")) {
                Vec3 away = victim.position().subtract(attacker.position()).multiply(1, 0, 1);
                if (away.lengthSqr() > 1.0e-4) {
                    away = away.normalize();
                    victim.push(away.x * 0.9, 0.35, away.z * 0.9);
                    victim.hurtMarked = true;
                }
            }
        }
        if (victim instanceof ServerPlayer player && attacker instanceof Mob foe && attacker instanceof Enemy
                && !isCompanion(attacker) && victim.level() instanceof ServerLevel level) {
            PlayerSkills skills = skillsFor.apply(player);
            if (SkillTree.has(skills, "s_wardbond")) {
                for (Mob mob : mobsOf(level, player.getUUID())) {
                    mob.setTarget(foe);
                }
            }
        }
    }

    /** A companion killed a monster: Seelenernte heals it and shortens the staff's pause. */
    public void onKill(Entity killer, Entity killed) {
        Companion c = killer == null ? null : ALL.get(killer.getUUID());
        if (c == null || !(killer.level() instanceof ServerLevel level) || !(killed instanceof Enemy)) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(c.owner);
        PlayerSkills skills = owner == null ? null : skillsFor.apply(owner);
        if (owner != null && SkillTree.has(skills, "d_pact")) {
            owner.heal((float) bonus(SkillEffect.SOUL_PACT, skills));   // Seelenpakt
        }
        if (SkillTree.has(skills, "l_cross_harvest") && killer instanceof LivingEntity living) {
            living.heal(living.getMaxHealth() * 0.2f);
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

    /** End of the night: every companion goes. */
    public void dismissAll(MinecraftServer server) {
        for (Companion c : new ArrayList<>(ALL.values())) {
            Entity entity = find(server, c.id);
            if (entity != null && entity.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.5, entity.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            }
            remove(server, c, entity);
        }
        ALL.clear();
        nextCall.clear();
        nextBones.clear();
        howlUntil.clear();
        nextHowl.clear();
        souls.clear();
        corpses.clear();
        nextWolf.clear();
        nextRaise.clear();
        pending.clear();
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
    }

    // ------------------------------------------------------------------
    // way of the dead: souls, raising, ghosts
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
        while (!corpses.isEmpty() && (now - corpses.peekFirst().time() > 600 || corpses.size() > 80)) {
            corpses.pollFirst();
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(mob) > 16.0 * 16.0) {
                continue;
            }
            PlayerSkills skills = skillsFor.apply(player);
            if (skills == null || !SkillTree.WAY_DEAD.equals(skills.pathTaken(tree))) {
                continue;
            }
            int gain = 1 + (player.getRandom().nextDouble() < bonus(SkillEffect.SOUL_GREED, skills) ? 1 : 0);
            addSouls(player, skills, gain);
        }
    }

    /** Souls of a summoner of the dead (3 at the start of the night). */
    public int soulsOf(UUID owner) {
        return souls.getOrDefault(owner, 3);
    }

    int soulCap(PlayerSkills skills) {
        return SkillTree.has(skills, "d_soulkeeper") ? 20 : 10;
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

    /** Places for the raised dead (two; the necromancer gets a third, the soul keeper loses one). */
    int minionSlots(PlayerSkills skills) {
        int slots = 2 + (skills != null && skills.levelOf("d_necro") >= 3 ? 1 : 0);
        if (SkillTree.has(skills, "d_soulkeeper")) {
            slots--;
        }
        return Math.max(1, slots);
    }

    /** Totenruf: the last monster fallen within 16 blocks rises (3 souls); ghosts and the wolf come along. */
    private void raiseWithStaff(ServerLevel level, ServerPlayer player, PlayerSkills skills, ItemStack stack, long now) {
        if (!SkillTree.has(skills, "d_raise")) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_nothing_dead"));
            return;
        }
        if (now < nextRaise.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        int have = soulsOf(player.getUUID());
        if (have < 3 && !player.getAbilities().instabuild) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.souls_short", have, 3));
            return;
        }
        int slots = minionSlots(skills);
        int used = count(level, player.getUUID(), k -> k == Kind.MINION, c -> !c.army);
        if (used >= slots) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_full", used, slots));
            return;
        }
        Corpse corpse = takeCorpse(level, player.position(), 16.0, SkillTree.has(skills, "d_champion"));
        if (corpse == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.no_corpse"));
            return;
        }
        long life = Math.round((20.0 + bonus(SkillEffect.RAISE_DEAD, skills)) * 20.0 * (1.0 + bonus(SkillEffect.BIND_RUNE, skills)));
        Mob raised = raise(level, player, skills, corpse, 0.4, now + life, false);
        if (raised == null) {
            return;
        }
        souls.put(player.getUUID(), have - 3);
        showSouls(player, Math.max(0, have - 3), soulCap(skills));
        nextRaise.put(player.getUUID(), now + 20);
        player.getCooldowns().addCooldown(stack, 20);
        // Seelenfänger: a ghost (with the swarm two) comes along for fifteen seconds.
        if (SkillTree.has(skills, "d_soulcatcher")) {
            int ghosts = SkillTree.has(skills, "d_swarm") ? 2 : 1;
            for (int i = 0; i < ghosts; i++) {
                ghost(level, player, skills, now + 300);
            }
        }
        // ↔ Rudelruf: one wolf runs with the dead.
        if (SkillTree.has(skills, "d_cross_wolf") && count(level, player.getUUID(), k -> k == Kind.WOLF) == 0
                && now >= nextWolf.getOrDefault(player.getUUID(), 0L)) {
            spawn(level, player, skills, Kind.WOLF);
        }
        CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.raised", raised.getType().getDescription(),
                used + 1, slots));
    }

    /** The newest corpse near a spot (the champion only with the mastery), taken off the list. */
    private Corpse takeCorpse(ServerLevel level, Vec3 near, double range, boolean champions) {
        long now = level.getGameTime();
        java.util.Iterator<Corpse> it = corpses.descendingIterator();
        while (it.hasNext()) {
            Corpse c = it.next();
            if (c.level() == level && now - c.time() <= 600 && c.pos().distanceToSqr(near) <= range * range
                    && (!c.elite() || champions) && !NOT_RAISED.contains(c.typeId())) {
                it.remove();
                return c;
            }
        }
        return null;
    }

    /** A fallen monster rises again for its summoner, weakened (strength: share of tonight's monster). */
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
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            mob.setDropChance(slot, 0f);
        }
        mob.getGoalSelector().removeAllGoals(g -> g instanceof net.minecraft.world.entity.ai.goal.AvoidEntityGoal<?>);
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        Companion c = new Companion(mob.getUUID(), owner.getUUID(), Kind.MINION, level.getGameTime());
        c.until = until;
        c.army = army;
        SiegeManager m = SiegeManager.get();
        double night = m == null ? 1.0 : m.mobHealthFactor();
        double nightDamage = m == null ? 1.0 : m.mobDamageFactor();
        double own = (1.0 + bonus(SkillEffect.NECRO_POWER, skills)) * (SkillTree.has(skills, "d_lich") ? 1.5 : 1.0);
        double life = mob.getMaxHealth() * night * strength * (1.0 + bonus(SkillEffect.SUMMON_HEALTH, skills)) * own;
        set(mob, Attributes.MAX_HEALTH, Math.max(4.0, life));
        mob.setHealth(mob.getMaxHealth());
        c.damageMul = nightDamage * strength * (1.0 + bonus(SkillEffect.SUMMON_DAMAGE, skills)) * own;
        if (SkillTree.has(skills, "d_archers")) {
            set(mob, Attributes.FOLLOW_RANGE, 32.0);
        }
        mob.setCustomName(Component.translatable("nightsiege.companion.minion", owner.getDisplayName())
                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        mob.setCustomNameVisible(true);
        ALL.put(mob.getUUID(), c);
        if (!level.addFreshEntity(mob)) {
            ALL.remove(mob.getUUID());
            return null;
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, corpse.pos().x, corpse.pos().y + 0.5, corpse.pos().z, 14, 0.4, 0.6, 0.4, 0.03);
        level.sendParticles(ParticleTypes.SOUL, corpse.pos().x, corpse.pos().y + 0.2, corpse.pos().z, 10, 0.5, 0.1, 0.5, 0.01);
        level.playSound(null, mob.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.5f, 1.6f);
        return mob;
    }

    /** A ghost of the soulcatcher: flies through walls, fights for a while. */
    private void ghost(ServerLevel level, ServerPlayer owner, PlayerSkills skills, long until) {
        Mob ghost = spawn(level, owner, skills, Kind.GHOST);
        Companion c = ghost == null ? null : ALL.get(ghost.getUUID());
        if (c == null) {
            return;
        }
        c.until = until;
        SiegeManager m = SiegeManager.get();
        double nightDamage = m == null ? 1.0 : m.mobDamageFactor();
        c.damageMul = nightDamage * 0.6 * (1.0 + bonus(SkillEffect.SOUL_CATCHER, skills)) * (1.0 + bonus(SkillEffect.SUMMON_DAMAGE, skills))
                * (SkillTree.has(skills, "d_lich") ? 1.5 : 1.0);
        if (ghost instanceof net.minecraft.world.entity.monster.Vex vex) {
            vex.setLimitedLife((int) Math.max(20, until - level.getGameTime() + 40));
        }
    }

    /** Leichensprengung: one of the raised dead bursts; with Seelenbrand souls come back. */
    private void corpseBlast(ServerLevel level, ServerPlayer owner, PlayerSkills skills, Mob minion) {
        Vec3 at = minion.position();
        float damage = (float) (bonus(SkillEffect.CORPSE_BLAST, skills) + minion.getMaxHealth() * 0.5);
        DamageSource source = level.damageSources().mobAttack(owner);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, minion.getBoundingBox().inflate(3.5, 2.0, 3.5),
                e -> e instanceof Enemy && e.isAlive() && !isCompanion(e))) {
            e.invulnerableTime = 0;
            e.hurtServer(level, source, damage);
        }
        Companion c = ALL.get(minion.getUUID());
        if (c != null) {
            remove(level.getServer(), c, minion);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.6, at.z, 3, 0.6, 0.4, 0.6, 0.0);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.6, at.z, 16, 0.8, 0.5, 0.8, 0.05);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.3f);
        if (SkillTree.has(skills, "d_soulburn")) {
            addSouls(owner, skills, (int) Math.round(bonus(SkillEffect.SOUL_BURN, skills)));
        }
    }

    /** Seelenschild: five souls become four hearts of absorption. */
    private void soulShield(ServerPlayer player, PlayerSkills skills) {
        int have = soulsOf(player.getUUID());
        if (have < 5) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.souls_short", have, 5));
            return;
        }
        souls.put(player.getUUID(), have - 5);
        showSouls(player, have - 5, soulCap(skills));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.6, 0.5, 0.03);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** Armee der Toten: every monster fallen in the last half minute within 20 blocks rises for a while. */
    public boolean armyOfTheDead(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        long now = level.getGameTime();
        long until = now + 200 + (SkillTree.has(skills, "d_eternal") ? 100 : 0);
        int raised = 0;
        Corpse corpse;
        while (raised < 24 && (corpse = takeCorpse(level, player.position(), 20.0, SkillTree.has(skills, "d_champion"))) != null) {
            if (raise(level, player, skills, corpse, 0.4 + 0.1 * rank, until, true) != null) {
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
        float damage = have * (1f + rank);
        storm(level, player, damage);
        if (SkillTree.has(skills, "d_echo")) {
            pending.add(new Pending(level.getGameTime() + 20, () -> {
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
        double r = 10.0;
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 3.0, r),
                e -> e instanceof Enemy && e.isAlive() && !isCompanion(e))) {
            if (e.distanceToSqr(player) > r * r) {
                continue;
            }
            e.invulnerableTime = 0;
            e.hurtServer(level, source, damage);
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
    private static Mob lookOwn(ServerLevel level, ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Mob best = null;
        double bestDot = 0.95;
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

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static List<Mob> mobsOf(ServerLevel level, UUID owner) {
        List<Mob> out = new ArrayList<>();
        for (Companion c : ALL.values()) {
            if (c.owner.equals(owner) && level.getEntity(c.id) instanceof Mob mob && mob.isAlive()) {
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

    /** Companions of one summoner by kind (wolf, golem, snow, skeleton, spirit, titan), for the tests. */
    public static int countOf(UUID owner, String kind) {
        int n = 0;
        for (Companion c : ALL.values()) {
            if (c.owner.equals(owner) && c.kind.name().equalsIgnoreCase(kind)) {
                n++;
            }
        }
        return n;
    }

    private static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path));
    }
}
