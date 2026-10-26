package com.mki.nightsiege.game;

import com.mki.nightsiege.core.CrateGains;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
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
    private static final double TELEPORT = 30.0;

    /** A named skill number ({@link SkillValues}); the fallback is the built-in value. */
    private static double v(String node, String key, double fallback) {
        return SkillValues.get(node, key, fallback);
    }

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
    /** Owner → game time the pack leader heals the pack again. */
    private final Map<UUID, Long> nextLead = new HashMap<>();

    /** A monster that fell tonight: it can be raised for half a minute. */
    record Corpse(String typeId, ServerLevel level, Vec3 pos, long time, boolean elite) {
    }

    /** Way of the dead: souls per summoner (3 at the start of a night). */
    private final Map<UUID, Integer> souls = new HashMap<>();
    private final java.util.ArrayDeque<Corpse> corpses = new java.util.ArrayDeque<>();
    /** How many dead each summoner raised tonight (every 4th is a champion with the mastery). */
    private final Map<UUID, Integer> raisedCount = new HashMap<>();
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
            // Heulen: the wolves, and for the golemsmith the golems too (the howl did nothing for him).
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
                // Skelettschützen: the raised archers shoot harder.
                if (hitter.kind == Kind.MINION && source.getDirectEntity() != attacker && SkillTree.has(skills, "d_archers")) {
                    factor *= (float) (1.0 + self.bonus(SkillEffect.SKELETON_ARCHERS, skills));
                }
            }
        }
        // Unheilige Kraft: the summoner hits harder for every raised dead.
        if (attacker instanceof ServerPlayer summoner && victim instanceof Enemy && !isCompanion(victim)) {
            PlayerSkills skills = self.skillsFor.apply(summoner);
            if (SkillTree.has(skills, "d_unholy") && summoner.level() instanceof ServerLevel level) {
                // The army counts too, but only up to a cap (with 24 raised it was +120 %).
                int raised = Math.min(count(level, summoner.getUUID(), k -> k == Kind.MINION),
                        (int) Math.round(v("d_unholy", "max", 6)));
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
     * and the snow golems come along). The X key at a monster sends every companion at it.
     */
    public void useStaff(ServerPlayer player, ItemStack stack) {
        useStaff(player, stack, false);
    }

    /**
     * The staff. {@code command}: the X key while the staff is in hand (it was sneak + right
     * click until 25 October 2026): all companions at the monster looked at, or corpse blast
     * and soul shield on the way of the dead.
     */
    public void useStaff(ServerPlayer player, ItemStack stack, boolean command) {
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
        if (command) {
            LivingEntity prey = CombatEffects.lookTarget(level, player, 32.0);
            if (prey == null && dead) {
                Mob own = lookOwn(level, player, 16.0);
                if (own != null && SkillTree.has(skills, "d_corpse") && ALL.get(own.getUUID()) != null
                        && ALL.get(own.getUUID()).kind == Kind.MINION) {
                    corpseBlast(level, player, skills, own);
                    return;
                }
                // The soul shield moved to sneak + X (26 October 2026): X into the air cost five souls by accident.
                if (SkillTree.has(skills, "d_soulshield")) {
                    CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_soulshield_hint",
                            Component.keybind("key.nightsiege.second_ult")));
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
        int pause = callPause(player, skills);
        // Doppelstab: the staff holds two calls; nextCall is the time both are back.
        int charges = gain(skills, "doppelstab") > 0 ? 2 : 1;
        long ready = nextCall.getOrDefault(player.getUUID(), 0L);
        long blocked = ready - (long) (charges - 1) * pause;
        if (now < blocked && !player.getAbilities().instabuild) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_wait", (blocked - now + 19) / 20));
            return;
        }
        // Schneewacht and ↔ Knochenwache answer every call, also when the places for wolves
        // and golems are full (balance update, 26 October 2026: they only came with a call that worked).
        int extras = callExtras(level, player, skills, now, true);
        int slots = slots(skills);
        int used = count(level, player.getUUID(), k -> k == Kind.WOLF || k == Kind.GOLEM);
        Mob called = null;
        if (used < slots) {
            called = golems ? spawn(level, player, skills, Kind.GOLEM) : spawn(level, player, skills, Kind.WOLF);
        }
        if (called == null && extras == 0) {
            if (used >= slots) {
                CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_full", used, slots));
            }
            return;
        }
        // A call that brought anybody uses the staff's pause.
        long full = Math.max(ready, now) + pause;
        nextCall.put(player.getUUID(), full);
        long wait = full - (long) (charges - 1) * pause - now;
        if (wait > 0) {
            player.getCooldowns().addCooldown(stack, (int) wait);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.8f, golems ? 0.7f : 1.3f);
        if (called == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_full_extras", used, slots));
            return;
        }
        CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_called",
                Component.translatable(golems ? "nightsiege.companion.golem" : "nightsiege.companion.wolf"), used + 1, slots));
    }

    /**
     * The pause of a call in ticks. Wolves: shorter with every level of Rudelruf. Golems: a
     * minute, and the same levels shorten it too (it was a fixed minute, so Rudelruf 2 and 3
     * did nothing for the golemsmith; balance update, 26 October 2026).
     */
    int callPause(ServerPlayer player, PlayerSkills skills) {
        if (player.getAbilities().instabuild) {
            return 20;
        }
        int level = Math.max(1, skills == null ? 1 : skills.levelOf("s_packcall"));
        if (SkillTree.has(skills, "s_golemsmith")) {
            return (int) Math.max(20, Math.round(v("s_golemsmith", "pause", 1200) - v("s_golemsmith", "step", 180) * (level - 1)));
        }
        // 20 s on level 1, each further level the amount (3 s) less: 20 / 17 / 14 s as before.
        double step = skills == null ? 0.0 : bonus(SkillEffect.PACK_CALL, skills) / Math.max(1, level);
        return (int) Math.max(20, Math.round(v("s_packcall", "pause", 400) - step * 20.0 * (level - 1)));
    }

    /** Snow golems (Schneewacht) and the skeleton guard (↔ Knochenwache) fill up; how many came. */
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

    /** The staff's item cooldown follows nextCall (Seelenernte shortens the pause). */
    private void syncStaffCooldown(ServerPlayer player, PlayerSkills skills, long now) {
        int pause = callPause(player, skills);
        int charges = gain(skills, "doppelstab") > 0 ? 2 : 1;
        long wait = nextCall.getOrDefault(player.getUUID(), 0L) - (long) (charges - 1) * pause - now;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ModContent.STAFF != null && stack.is(ModContent.STAFF) && player.getCooldowns().isOnCooldown(stack)) {
                // A new cooldown replaces the old one; one tick is as good as none.
                player.getCooldowns().addCooldown(stack, (int) Math.max(1, wait));
            }
        }
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
            return (int) Math.round(v("s_shepherd", "slots", 1));   // Einsamer Hirte: one, twice as strong
        }
        int slots = (int) Math.round(v("s_packcall", "slots", 2)) + (int) Math.round(bonus(SkillEffect.PACK_SLOTS, skills));
        if (SkillTree.has(skills, "s_beastlord")) {
            slots += (int) Math.round(bonus(SkillEffect.BEAST_LORD, skills));
        }
        return slots;
    }

    // ------------------------------------------------------------------
    // ultimates
    // ------------------------------------------------------------------

    /** Wilde Jagd: spirit wolves hunt for a while (6 / 7 / 8, ten seconds, Endlose Jagd five more). */
    public boolean wildHunt(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        int count = (int) Math.round(v("s_wild_hunt", "count" + rank, 5 + rank));
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
            // A skeleton runs from wolves; this one hunts with them.
            mob.getGoalSelector().removeAllGoals(g -> g instanceof net.minecraft.world.entity.ai.goal.AvoidEntityGoal<?>);
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
            case WOLF, SPIRIT -> 40.0;
            case GOLEM -> 80.0;   // was 100: the golem way was far too strong (Simon, 25 October 2026)
            case SNOW -> 20.0;
            case SKELETON -> v("l_cross_bones", "health", 30.0);
            case TITAN -> v("s_titan", "health", 300.0);
            case MINION -> 14.0;
            case GHOST -> v("d_soulcatcher", "health", 30.0);   // was 14 (Simon, 25 October 2026)
            case MOUNT, HAWK -> 20.0;
        };
        double damage = switch (c.kind) {
            case WOLF -> 3.0;   // was 4 (wolves in armour too strong, Simon 25 October 2026)
            case SPIRIT -> v("s_wild_hunt", "dmg" + Math.max(1, c.rank), 4.0 * (1.5 + 0.25 * c.rank));
            case GOLEM -> 10.0;   // was 15
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
        // Ghosts a little bigger, so they are seen at all (Simon never saw them).
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
        // Alphawolf and Einsamer Hirte do not multiply (the lone wolf is always the alpha, it
        // was ×4): the bigger of the two counts (balance update, 26 October 2026).
        double lead = alpha ? v("s_alpha", "health", 2.0) : 1.0;
        if (SkillTree.has(skills, "s_shepherd") && (kind == Kind.WOLF || kind == Kind.GOLEM)) {
            lead = Math.max(lead, v("s_shepherd", "health", 2.0));
        }
        scale *= lead;
        if (kind == Kind.GHOST && SkillTree.has(skills, "d_lich")) {
            scale *= 1.0 + bonus(SkillEffect.LICH, skills);   // Lich: the ghosts' health too, as the text says
        }
        return scale;
    }

    double damageScale(PlayerSkills skills, Kind kind, boolean alpha) {
        double scale = (1.0 + bonus(SkillEffect.SUMMON_DAMAGE, skills)) * nightScale(true);
        if (kind == Kind.GOLEM || kind == Kind.TITAN) {
            scale *= 1.0 + bonus(SkillEffect.GOLEM_CALL, skills);
        }
        // Alphawolf and Einsamer Hirte do not multiply (the lone wolf is always the alpha, it
        // was ×4): the bigger of the two counts (balance update, 26 October 2026).
        double lead = alpha ? v("s_alpha", "damage", 2.0) : 1.0;
        if (SkillTree.has(skills, "s_shepherd") && (kind == Kind.WOLF || kind == Kind.GOLEM)) {
            lead = Math.max(lead, v("s_shepherd", "damage", 2.0));
        }
        scale *= lead;
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
        // Heulen: every 20 s the pack howls (5 s faster and stronger); the golems roar along.
        for (ServerPlayer owner : owners.values()) {
            if (owner == null) {
                continue;
            }
            taunt(owner);
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
            // Rudelführer: on its own clock the leader (the alpha, else the strongest wolf) heals the
            // pack (it needed Alphawolf and Heulen together, balance update 26 October 2026).
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
     * Monsters near the summoner that go for him turn to one of his companions instead
     * (config companionTauntRadius), so the companions take the blows, not the summoner.
     */
    private void taunt(ServerPlayer owner) {
        SiegeConfig cfg = config.get();
        double radius = cfg == null ? 0.0 : cfg.companionTauntRadius;
        if (radius <= 0 || !(owner.level() instanceof ServerLevel level) || !owner.isAlive()) {
            return;
        }
        List<Mob> guards = new ArrayList<>();
        for (Mob mob : mobsOf(level, owner.getUUID())) {
            Companion c = ALL.get(mob.getUUID());
            if (c != null && c.kind != Kind.SPIRIT && c.kind != Kind.HAWK && c.kind != Kind.MOUNT && mob.isAlive()
                    && mob.distanceToSqr(owner) < 24.0 * 24.0) {
                guards.add(mob);
            }
        }
        if (guards.isEmpty()) {
            return;
        }
        for (Mob foe : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(radius),
                e -> e instanceof Enemy && e.isAlive() && !isCompanion(e) && e.getTarget() == owner)) {
            Mob best = null;
            double bestD = Double.MAX_VALUE;
            for (Mob guard : guards) {
                double d = guard.distanceToSqr(foe);
                if (d < bestD) {
                    bestD = d;
                    best = guard;
                }
            }
            if (best != null) {
                foe.setTarget(best);
                if (best.getTarget() == null || !best.getTarget().isAlive()) {
                    best.setTarget(foe);
                }
            }
        }
    }

    private void work(ServerLevel level, ServerPlayer owner, PlayerSkills skills, Companion c, Mob mob, long now) {
        if (c.kind == Kind.HAWK) {
            return;   // it flies its circle in nightTick
        }
        if (c.kind == Kind.MOUNT) {
            if (now % 10 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 0.2, mob.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
            }
            if (!mob.isVehicle() && (mob.distanceToSqr(owner) > TELEPORT * TELEPORT || mob.level() != owner.level())) {
                Vec3 at = spot(level, owner);
                mob.teleportTo(at.x, at.y, at.z);
            }
            return;
        }
        double range = v("s_callrange", "base", 14.0) + bonus(SkillEffect.CALL_RANGE, skills);
        // Skelettschützen: raised archers pick their targets further out.
        if (c.kind == Kind.MINION && SkillTree.has(skills, "d_archers")
                && (mob.isHolding(net.minecraft.world.item.Items.BOW) || mob.isHolding(net.minecraft.world.item.Items.CROSSBOW))) {
            range += v("d_archers", "range", 8.0);
        }
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
            c.nextSlam = now + Math.round(v("s_titan", "slam", 60));
            slam(level, owner, skills, c, mob);
        }
        // Pflege: every five seconds a heart back.
        if (now % Math.max(5, Math.round(v("s_care", "every", 100))) == 0 && SkillTree.has(skills, "s_care")
                && mob.getHealth() < mob.getMaxHealth()) {
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

    /** The time of a spirit or the titan is up; with Erzkern the titan leaves a plain golem behind. */
    private void expire(ServerLevel level, ServerPlayer owner, Companion c, Mob mob) {
        Vec3 at = mob.position();
        remove(level.getServer(), c, mob);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.6, at.z, 10, 0.4, 0.4, 0.4, 0.02);
        if (c.kind == Kind.TITAN) {
            oreCore(level, owner);
        }
    }

    /** A companion was killed in the fight: the titan's ore core stays, the pack gets a new alpha. */
    private void fell(ServerLevel level, ServerPlayer owner, Companion c) {
        if (c.kind == Kind.TITAN) {
            oreCore(level, owner);
        } else if (c.kind == Kind.WOLF && c.alpha) {
            promoteAlpha(level, owner);
        }
    }

    /**
     * Erzkern: when the titan ends or falls, a plain iron golem stays if a place is free;
     * with every place taken the titan's iron heals the golems instead (balance update,
     * 26 October 2026: it came without a place check, and not at all when the titan was killed).
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

    /** Alphawolf: the alpha fell, so the strongest wolf left takes the lead (it was leaderless until all died). */
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
                    victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) Math.round(v("d_rot", "duration", 60)), 0));
                }
                // Ghulhunger: the raised dead feed on their prey.
                if (SkillTree.has(skills, "d_ghoul") && melee && attacker instanceof LivingEntity living) {
                    living.heal(taken * (float) bonus(SkillEffect.GHOUL_HUNGER, skills));
                }
                // Totentanz: their melee hits mend the wolf (also without Ghulhunger).
                if (SkillTree.has(skills, "d_dance") && melee) {
                    healKind(deadLevel, hitter.owner, Kind.WOLF, (float) bonus(SkillEffect.DEATH_DANCE, skills));
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
                owner.heal((float) v("s_moonpack", "heal", 1.0));
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
                    double push = v("s_golemwork", "push", 0.9);
                    victim.push(away.x * push, v("s_golemwork", "lift", 0.35), away.z * push);
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
            // The staff's own cooldown has to shrink too, or the shorter pause is never felt.
            syncStaffCooldown(owner, skills, level.getGameTime());
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
        keepSouls(server);
        nextCall.clear();
        nextBones.clear();
        howlUntil.clear();
        nextHowl.clear();
        nextLead.clear();
        souls.clear();
        corpses.clear();
        raisedCount.clear();
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
            if (skills == null || !SkillTree.WAY_DEAD.equals(skills.pathTaken(tree))) {
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
     * The night begins: the summoner's first companions are already there (Simon, 25 October
     * 2026) – two wolves with Rudelruf, one golem for the golemsmith (with Schneewacht its snow
     * golems), two ghosts (60 s) with Totenruf on the way of the dead.
     */
    public void startNight(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != com.mki.nightsiege.core.SkillClass.SUMMONER) {
            return;
        }
        if (SkillTree.WAY_DEAD.equals(skills.pathTaken(tree))) {
            if (SkillTree.has(skills, "d_raise")) {
                long until = level.getGameTime() + Math.round(v("d_raise", "startlife", 1200));   // the start ghosts fly for a minute
                int ghosts = (int) Math.round(v("d_raise", "startghosts", 2));
                for (int i = count(level, player.getUUID(), k -> k == Kind.GHOST); i < ghosts; i++) {
                    ghost(level, player, skills, until);
                }
            }
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
        // The start golem comes with its snow golems (Schneewacht).
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

    /** Places for the raised dead (two; the necromancer gets a third, the soul keeper loses one). */
    int minionSlots(PlayerSkills skills) {
        int slots = (int) Math.round(v("d_raise", "slots", 2))
                + (skills != null && skills.levelOf("d_necro") >= (int) Math.round(v("d_necro", "slotlevel", 3)) ? 1 : 0);
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
        // ↔ Rudelruf: the wolf answers every use of the staff, also when no dead can rise
        // (it only came with a raising that worked).
        crossWolf(level, player, skills, now);
        int have = soulsOf(player.getUUID());
        int cost = (int) Math.round(v("d_raise", "souls", 3)) - (gain(skills, "doppelstab") > 0 ? 1 : 0);   // Doppelstab: one soul less
        if (have < cost && !player.getAbilities().instabuild) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.souls_short", have, cost));
            return;
        }
        int slots = minionSlots(skills);
        int used = count(level, player.getUUID(), k -> k == Kind.MINION, c -> !c.army);
        if (used >= slots) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.staff_full", used, slots));
            return;
        }
        Corpse corpse = takeCorpse(level, player.position(), v("d_raise", "range", 16.0), SkillTree.has(skills, "d_champion"));
        if (corpse == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.no_corpse"));
            return;
        }
        long life = Math.round((v("d_raise", "base", 30.0) + bonus(SkillEffect.RAISE_DEAD, skills)) * 20.0
                * (1.0 + bonus(SkillEffect.BIND_RUNE, skills)));
        // Full strength of tonight's monster (was 40 %: "they do next to nothing", Simon 25 October 2026).
        Mob raised = raise(level, player, skills, corpse, 1.0, now + life, false);
        if (raised == null) {
            return;
        }
        souls.put(player.getUUID(), Math.max(0, have - cost));
        showSouls(player, Math.max(0, have - cost), soulCap(skills));
        nextRaise.put(player.getUUID(), now + 20);
        player.getCooldowns().addCooldown(stack, 20);
        // Seelenfänger: a ghost (with the swarm two) comes along for fifteen seconds.
        if (SkillTree.has(skills, "d_soulcatcher")) {
            int ghosts = SkillTree.has(skills, "d_swarm") ? (int) Math.round(v("d_swarm", "ghosts", 2)) : 1;
            for (int i = 0; i < ghosts; i++) {
                ghost(level, player, skills, now + Math.round(v("d_soulcatcher", "life", 300)));
            }
        }
        CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.raised", raised.getType().getDescription(),
                used + 1, slots));
    }

    /** ↔ Rudelruf: one wolf runs with the dead (a pause after it fell). */
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
        if (gain(skills, "grabruestung") > 0) {
            graveArmour(mob);
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
        double own = (1.0 + bonus(SkillEffect.NECRO_POWER, skills)) * (1.0 + bonus(SkillEffect.LICH, skills));
        // At least 30 health before the night and the skills (Simon, 25 October 2026): the raised
        // dead were gone at the first hit.
        double life = Math.max(v("d_raise", "minhealth", 30.0), mob.getMaxHealth() * strength) * night * (1.0 + bonus(SkillEffect.SUMMON_HEALTH, skills)) * own;
        // Champion der Toten (since the champion night is gone, 25 October 2026): every 4th of
        // the raised dead rises as a champion, twice as strong and bigger.
        boolean champion = false;
        if (SkillTree.has(skills, "d_champion")) {
            int n = raisedCount.merge(owner.getUUID(), 1, Integer::sum);
            champion = n % Math.max(1, (int) Math.round(v("d_champion", "every", 4))) == 0;
        }
        if (champion) {
            life *= v("d_champion", "health", 2.0);
            set(mob, Attributes.SCALE, v("d_champion", "size", 1.3));
        }
        set(mob, Attributes.MAX_HEALTH, Math.max(4.0, life));
        mob.setHealth(mob.getMaxHealth());
        c.damageMul = nightDamage * strength * (1.0 + bonus(SkillEffect.SUMMON_DAMAGE, skills)) * own * (champion ? v("d_champion", "damage", 2.0) : 1.0);
        if (SkillTree.has(skills, "d_archers")) {
            set(mob, Attributes.FOLLOW_RANGE, 32.0);
        }
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
        c.damageMul = nightDamage * v("d_soulcatcher", "strength", 0.6) * (1.0 + bonus(SkillEffect.SOUL_CATCHER, skills))
                * (1.0 + bonus(SkillEffect.SUMMON_DAMAGE, skills)) * (1.0 + bonus(SkillEffect.LICH, skills));
        if (ghost instanceof net.minecraft.world.entity.monster.Vex vex) {
            vex.setLimitedLife((int) Math.max(20, until - level.getGameTime() + 40));
        }
    }

    /** Leichensprengung: one of the raised dead bursts; with Seelenbrand souls come back. */
    private void corpseBlast(ServerLevel level, ServerPlayer owner, PlayerSkills skills, Mob minion) {
        Vec3 at = minion.position();
        float damage = (float) (bonus(SkillEffect.CORPSE_BLAST, skills) + minion.getMaxHealth() * v("d_corpse", "share", 0.5));
        double r = v("d_corpse", "radius", 3.5);
        DamageSource source = level.damageSources().mobAttack(owner);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, minion.getBoundingBox().inflate(r, 2.0, r),
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
    /**
     * Sneak + X with the staff in hand (26 October 2026). Looking at an own companion with
     * the soul swap won: false, so the second ult swaps. Otherwise the soul shield on the way
     * of the dead (true). False when neither applies: the second ult fires as without the staff.
     */
    public boolean sneakStaff(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != com.mki.nightsiege.core.SkillClass.SUMMONER) {
            return false;
        }
        if (gain(skills, "seelentausch") > 0 && lookOwn(level, player, 48.0, 0.9) != null) {
            return false;
        }
        if (SkillTree.WAY_DEAD.equals(skills.pathTaken(tree)) && SkillTree.has(skills, "d_soulshield")) {
            soulShield(player, skills);
            return true;
        }
        return false;
    }

    private void soulShield(ServerPlayer player, PlayerSkills skills) {
        int have = soulsOf(player.getUUID());
        int cost = (int) Math.round(v("d_soulshield", "souls", 5));
        if (have < cost) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.souls_short", have, cost));
            return;
        }
        souls.put(player.getUUID(), have - cost);
        showSouls(player, have - cost, soulCap(skills));
        // Absorption: 4 health per effect level (amount 8 = Absorption II).
        int amp = Math.max(0, (int) Math.round(bonus(SkillEffect.SOUL_SHIELD, skills) / 4.0) - 1);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, (int) Math.round(v("d_soulshield", "duration", 600)), amp));
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.6, 0.5, 0.03);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** Armee der Toten: every monster fallen in the last half minute within 20 blocks rises for a while. */
    public boolean armyOfTheDead(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        long now = level.getGameTime();
        long until = now + Math.round(v("d_army", "duration", 200)
                + (SkillTree.has(skills, "d_eternal") ? bonus(SkillEffect.ETERNAL_LEGION, skills) * 20.0 : 0.0));
        int raised = 0;
        int most = (int) Math.round(v("d_army", "max", 24));
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
        return lookOwn(level, player, reach, 0.95);
    }

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
            case "totenglocke" -> deathBell(level, player, stack);
            default -> {
            }
        }
    }

    /** Rückrufpfeife: every companion is at the summoner's side at once. 10 s pause. */
    private void recall(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int came = 0;
        for (Companion c : new ArrayList<>(ALL.values())) {
            if (!c.owner.equals(player.getUUID()) || c.kind == Kind.HAWK || !(level.getEntity(c.id) instanceof Mob mob)
                    || !mob.isAlive() || mob.isVehicle()) {
                continue;
            }
            Vec3 at = spot(level, player);
            mob.teleportTo(at.x, at.y, at.z);
            mob.getNavigation().stop();
            mob.setTarget(null);
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

    /** Totenglocke: the raised dead and the ghosts come to the summoner and heal a quarter. 60 s pause. */
    private void deathBell(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int came = 0;
        for (Companion c : new ArrayList<>(ALL.values())) {
            if (!c.owner.equals(player.getUUID()) || (c.kind != Kind.MINION && c.kind != Kind.GHOST)
                    || !(level.getEntity(c.id) instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            Vec3 at = spot(level, player);
            mob.teleportTo(at.x, at.y, at.z);
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.heal(mob.getMaxHealth() * 0.25f);
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.6, at.z, 8, 0.3, 0.4, 0.3, 0.02);
            came++;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.6f, 0.6f);
        player.getCooldowns().addCooldown(stack, 1200);
        CombatEffects.actionBar(player, Component.translatable("nightsiege.gadget.totenglocke.done", came));
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
     * summoner with the Spähfalke gets theirs and the monsters within 30 blocks light up.
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
            if (skills == null || skills.clazz() != com.mki.nightsiege.core.SkillClass.SUMMONER
                    || gain(skills, "spaehfalke") <= 0 || SkillTree.WAY_DEAD.equals(skills.pathTaken(tree))) {
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
