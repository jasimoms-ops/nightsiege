package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.mixin.BlockDisplayAccessor;
import com.mki.nightsiege.mixin.DisplayAccessor;
import com.mki.nightsiege.mixin.MobGoalsAccessor;
import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The new attackers of update 1 (Simon, 27 October 2026; concept in the project doc
 * night-siege-update1-gegner-konzept.md). Every one is a vanilla monster with a job, so the
 * event textures and the glowing eyes fit it without anything new on the client:
 * <ul>
 *   <li><b>Revenant</b> (zombie in grave clothes): its corpse lies 5 s and then it stands up
 *   again with half its life, once. Fire, a blow on the grave or a summoner taking the corpse
 *   stop it.</li>
 *   <li><b>Tunneler</b> (zombie miner with pickaxe and lantern): digs in 20–30 blocks before
 *   the wall and comes up inside it near the core; 3 s of dust and knocking warn where. It
 *   changes no blocks.</li>
 *   <li><b>Standard bearer</b> (pillager with a grey illager banner): monsters within 8 blocks
 *   are 20 % faster and hit 20 % harder; it keeps behind the others.</li>
 *   <li><b>Grappler</b> (pillager with hook and rope instead of the crossbow): every 15 s it
 *   pulls a defender standing high (a wall, a tower) down in front of the wall, after a
 *   second's warning line. A shield blocks the hook, a hit on the thrower stops it, the pull
 *   ends without fall damage. Between hooks it fights hand to hand.</li>
 *   <li><b>Iron knight</b> (zombie in full iron): 25 % slower, arrows and bolts keep only a
 *   fifth of their damage.</li>
 *   <li><b>Treasure raider</b> (golden pillager, outside the normal list): in 20 % of the
 *   nights it shows up once, flees for 45 s and is gone; who brings it down gets a crate for
 *   every defender with a chance of 50 %, else experience.</li>
 * </ul>
 */
public final class Foes {

    /** Every new attacker carries this tag and the tag of its kind. */
    public static final String TAG = "nightsiege_foe";
    /** A revenant that has stood up once already. */
    public static final String TAG_RISEN = "nightsiege_risen";
    /** The hit box and the mound of a revenant's grave. */
    public static final String TAG_GRAVE = "nightsiege_grave";

    private static final Identifier KNIGHT_SLOW = Identifier.fromNamespaceAndPath("nightsiege", "iron_knight_slow");
    private static final Identifier KNIGHT_ARMOUR = Identifier.fromNamespaceAndPath("nightsiege", "iron_knight_armour");
    private static final String NAMESPACE = "nightsiege:";

    /** The kinds, their entry id ("nightsiege:revenant") and the vanilla monster under them. */
    public enum Role {
        REVENANT("revenant", "minecraft:zombie"),
        TUNNELER("tunneler", "minecraft:zombie"),
        STANDARD_BEARER("standard_bearer", "minecraft:pillager"),
        GRAPPLER("grappler", "minecraft:pillager"),
        IRON_KNIGHT("iron_knight", "minecraft:zombie"),
        TREASURE_RAIDER("treasure_raider", "minecraft:pillager");

        private final String id;
        private final String base;

        Role(String id, String base) {
            this.id = id;
            this.base = base;
        }

        public String id() {
            return id;
        }

        /** The vanilla monster it is made from. */
        public String base() {
            return base;
        }

        /** Its id in the mob list of the config. */
        public String entryId() {
            return NAMESPACE + id;
        }

        public String tag() {
            return TAG + "_" + id;
        }

        public String translationKey() {
            return "nightsiege.foe." + id;
        }

        public static Role byId(String id) {
            if (id == null) {
                return null;
            }
            String key = id.trim().toLowerCase(Locale.ROOT);
            if (key.startsWith(NAMESPACE)) {
                key = key.substring(NAMESPACE.length());
            }
            for (Role role : values()) {
                if (role.id.equals(key)) {
                    return role;
                }
            }
            return null;
        }
    }

    /** What a night lends the attackers. */
    public interface Host {
        ServerLevel world();

        BlockPos anchor();

        /** The walls around the core (null without a closed base). */
        BaseScan base();

        int level();

        SiegeConfig config();

        Random random();

        SiegeEvent event();

        /** The defenders standing tonight, in this world. */
        List<ServerPlayer> defenders();

        /** Everyone who defends tonight, the fallen too. */
        Set<UUID> nightPlayers();

        /** Spawns a monster of tonight (level, event, scaling) on a spot; it does not join the night yet. */
        Mob spawn(String entityId, BlockPos spot);

        /** The monster joins the night's monsters (it counts for the kill goal). */
        void addMob(Mob mob);

        /** A short line in the boss bar. */
        void notice(Component text);

        /** One crate for every defender of the night (the treasure raider). */
        void awardCrates();
    }

    /** The running instance (for the static hooks of MobFactory, the damage and the summoner). */
    private static Foes current;
    /** The living attackers of the new kinds and their kind, noted when they are made. */
    private static final Map<UUID, Role> ROLES = new HashMap<>();
    /** Tunnelers that came up again (from then on they fight like the others). */
    private static final Set<UUID> EMERGED = new HashSet<>();
    /** Grapplers walking to a spot from where they see a defender on the wall (they pick no fight meanwhile). */
    private static final Set<UUID> POSITIONING = new HashSet<>();

    private final List<Grave> graves = new ArrayList<>();
    private final Map<UUID, Dig> digs = new HashMap<>();
    private final Map<UUID, Hook> hooks = new HashMap<>();
    private final Map<UUID, Long> buffedUntil = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    private UUID treasure;
    private long treasureGone;
    private int treasureWave = -1;
    private boolean treasureCame;

    /** For the tests and the protocol: what happened tonight. */
    public int risenTonight;
    public int gravesBroken;
    public int gravesTaken;
    public int tunnelsDone;
    public int hooksLanded;
    public int hooksBlocked;
    public int fallsSaved;
    public String treasureOutcome = "";

    private static final class Grave {
        final UUID hitbox;
        final UUID mound;
        final Vec3 spot;
        final long riseAt;

        Grave(UUID hitbox, UUID mound, Vec3 spot, long riseAt) {
            this.hitbox = hitbox;
            this.mound = mound;
            this.spot = spot;
            this.riseAt = riseAt;
        }
    }

    private static final class Dig {
        /** 0 walks to its spot, 1 digs in, 2 is under the ground. */
        int phase;
        long since;
        BlockPos exit;
    }

    private static final class Hook {
        long next;
        UUID target;
        long fireAt;
    }

    public Foes() {
        current = this;
    }

    // ------------------------------------------------------------------ kinds

    public static boolean isRole(String entryId) {
        return entryId != null && entryId.trim().toLowerCase(Locale.ROOT).startsWith(NAMESPACE) && Role.byId(entryId) != null;
    }

    /** The kind of a mob-list entry ("nightsiege:tunneler"), or null for any other monster. */
    public static Role roleOf(String entryId) {
        return isRole(entryId) ? Role.byId(entryId) : null;
    }

    /** The kind of a living monster, or null. */
    public static Role roleOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        Role known = ROLES.get(entity.getUUID());
        if (known != null) {
            return known;
        }
        if (!entity.entityTags().contains(TAG)) {
            return null;
        }
        for (Role role : Role.values()) {
            if (entity.entityTags().contains(role.tag())) {
                return role;
            }
        }
        return null;
    }

    public static boolean hasRole(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG);
    }

    /** True for kinds that come only a few at a time (never as a theme wave). */
    public static boolean isCapped(String entryId) {
        Role role = roleOf(entryId);
        return role == Role.TUNNELER || role == Role.STANDARD_BEARER || role == Role.GRAPPLER || role == Role.TREASURE_RAIDER;
    }

    // ------------------------------------------------------------------ making one

    /** Dresses a fresh vanilla monster as one of the new attackers (before the night's scaling). */
    public static void setUp(Mob mob, Role role, SiegeConfig cfg) {
        mob.addTag(TAG);
        mob.addTag(role.tag());
        ROLES.put(mob.getUUID(), role);
        mob.setCustomName(Component.translatable(role.translationKey()));
        BossService.foeLook(mob, "foe_" + role.id());   // its own look (design update, 29 October 2026)
        if (mob instanceof Zombie zombie) {
            zombie.setBaby(false);
        }
        switch (role) {
            case REVENANT -> {
                bare(mob);
                mob.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, 0x3B2F24));
                mob.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, 0x4A3B2C));
            }
            case TUNNELER -> {
                bare(mob);
                mob.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, 0xC9A227));
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.LANTERN));
            }
            case STANDARD_BEARER -> mob.setItemSlot(EquipmentSlot.HEAD, banner(mob));
            case GRAPPLER -> {
                mob.setItemSlot(EquipmentSlot.MAINHAND, grapplingHook());
                mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.LEAD));
                if (mob instanceof PathfinderMob walker) {
                    // Without a crossbow a pillager would only stand there: between hooks it fights by hand.
                    ((MobGoalsAccessor) mob).nightsiege$goals().addGoal(2, new MeleeAttackGoal(walker, 1.0, true));
                }
            }
            case IRON_KNIGHT -> {
                bare(mob);
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null && !speed.hasModifier(KNIGHT_SLOW)) {
                    speed.addTransientModifier(new AttributeModifier(KNIGHT_SLOW, -cfg.ironKnightSlow,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }
                // Melee hits it fully (concept): the iron is for the look and against shots only.
                for (var attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS)) {
                    AttributeInstance armour = mob.getAttribute(attribute);
                    if (armour != null && !armour.hasModifier(KNIGHT_ARMOUR)) {
                        armour.addTransientModifier(new AttributeModifier(KNIGHT_ARMOUR, -1.0,
                                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    }
                }
            }
            case TREASURE_RAIDER -> {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLD_INGOT));
                mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                times(mob, Attributes.MAX_HEALTH, 2.0);
                times(mob, Attributes.MOVEMENT_SPEED, 1.25);
                mob.setHealth(mob.getMaxHealth());
                mob.setCustomNameVisible(true);
                mob.setGlowingTag(true);
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0.0f);
        }
    }

    /** The husk in front of a camel husk carries a spear, like the vanilla troop. */
    public static void armTroop(Mob mob) {
        if (!(mob instanceof CamelHusk) || mob.getPassengers().isEmpty()) {
            return;
        }
        if (mob.getPassengers().get(0) instanceof Mob front && front.getMainHandItem().isEmpty()) {
            front.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
            front.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        }
    }

    private static void bare(Mob mob) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.BODY && slot != EquipmentSlot.SADDLE) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /** The grappler's hook (Simon 27.10.: designed for it, tools/weapons/grapnel.py): a tripwire hook with its own look. */
    static ItemStack grapplingHook() {
        ItemStack hook = new ItemStack(Items.TRIPWIRE_HOOK);
        hook.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
                Identifier.fromNamespaceAndPath("nightsiege", "foe/grappling_hook"));
        hook.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                net.minecraft.network.chat.Component.translatable("item.nightsiege.grappling_hook"));
        return hook;
    }

    private static ItemStack dyed(Item item, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        return stack;
    }

    /** A grey banner with the illager face, its colours in the blood moon style. */
    private static ItemStack banner(Mob mob) {
        // 26.2 keeps the coloured banners in a colour collection: the grey one by its id.
        ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                Identifier.withDefaultNamespace("gray_banner")));
        try {
            HolderGetter<BannerPattern> patterns = mob.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
            stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
                    .addIfRegistered(patterns, BannerPatterns.RHOMBUS_MIDDLE, DyeColor.RED)
                    .addIfRegistered(patterns, BannerPatterns.STRIPE_BOTTOM, DyeColor.LIGHT_GRAY)
                    .addIfRegistered(patterns, BannerPatterns.STRIPE_CENTER, DyeColor.BLACK)
                    .addIfRegistered(patterns, BannerPatterns.BORDER, DyeColor.LIGHT_GRAY)
                    .addIfRegistered(patterns, BannerPatterns.STRIPE_MIDDLE, DyeColor.BLACK)
                    .addIfRegistered(patterns, BannerPatterns.HALF_HORIZONTAL, DyeColor.LIGHT_GRAY)
                    .addIfRegistered(patterns, BannerPatterns.CIRCLE_MIDDLE, DyeColor.LIGHT_GRAY)
                    .addIfRegistered(patterns, BannerPatterns.BORDER, DyeColor.BLACK)
                    .build());
        } catch (RuntimeException e) {
            // a plain grey banner then
        }
        return stack;
    }

    private static void times(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double factor) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * factor);
        }
    }

    // ------------------------------------------------------------------ the night

    /** A new night: the old one's leftovers go, and whether (and with which wave) the treasure raider comes. */
    public void startNight(Host host, int plannedWaves, boolean bossNight) {
        reset(host.world());
        SiegeConfig cfg = host.config();
        if (!bossNight && host.level() >= cfg.treasureRaiderFromLevel && plannedWaves >= 3
                && host.random().nextDouble() < cfg.treasureRaiderChance) {
            int lo = 2;
            int hi = Math.max(lo, plannedWaves - 1);
            treasureWave = lo + host.random().nextInt(hi - lo + 1);
        }
    }

    /** The night is over: graves, the treasure raider and every note go. */
    public void reset(ServerLevel level) {
        if (level != null) {
            for (Grave g : graves) {
                discard(level, g.hitbox);
                discard(level, g.mound);
            }
            if (treasure != null) {
                discard(level, treasure);
            }
        }
        graves.clear();
        digs.clear();
        hooks.clear();
        buffedUntil.clear();
        noFallUntil.clear();
        ROLES.clear();
        EMERGED.clear();
        POSITIONING.clear();
        treasure = null;
        treasureWave = -1;
        treasureCame = false;
        risenTonight = 0;
        gravesBroken = 0;
        gravesTaken = 0;
        tunnelsDone = 0;
        hooksLanded = 0;
        hooksBlocked = 0;
        fallsSaved = 0;
        treasureOutcome = "";
    }

    /** The wave planned for the treasure raider (-1: none tonight). */
    public int treasureWave() {
        return treasureWave;
    }

    /** A wave has come: maybe the treasure raider with it. */
    public void afterWave(Host host, int wave) {
        if (wave == treasureWave && !treasureCame) {
            spawnTreasure(host);
        }
    }

    /**
     * Keeps the kinds that come only a few at a time in their numbers: a picked entry over its
     * limit is picked again (at most a few times, then a plain zombie comes).
     */
    public String limit(String picked, Map<String, Integer> wave, int level, SiegeConfig cfg, Supplier<String> again) {
        if (picked == null) {
            return null;   // nothing to pick at all: the wave ends there, as before
        }
        for (int attempt = 0; attempt < 6 && picked != null; attempt++) {
            Role role = roleOf(picked);
            if (role == null) {
                return picked;
            }
            int cap = switch (role) {
                case TUNNELER -> cfg.tunnelersPerWave;
                case STANDARD_BEARER -> level >= cfg.standardBearerTwoFromLevel ? cfg.standardBearersPerWave * 2 : cfg.standardBearersPerWave;
                case GRAPPLER -> cfg.grapplersAtOnce;
                case TREASURE_RAIDER -> 0;
                default -> Integer.MAX_VALUE;
            };
            int used = role == Role.GRAPPLER ? living(Role.GRAPPLER) : wave.getOrDefault(role.entryId(), 0);
            if (used < cap) {
                wave.merge(role.entryId(), 1, Integer::sum);
                return picked;
            }
            picked = again.get();
        }
        return picked == null || isCapped(picked) ? "minecraft:zombie" : picked;
    }

    private static int living(Role role) {
        int n = 0;
        for (Role r : ROLES.values()) {
            if (r == role) {
                n++;
            }
        }
        return n;
    }

    /** Every other tick of the night. */
    public void tick(Host host, Collection<UUID> live, long now) {
        ServerLevel level = host.world();
        if (level == null || host.anchor() == null) {
            return;
        }
        List<ServerPlayer> defenders = host.defenders();
        for (UUID id : new ArrayList<>(live)) {
            Role role = ROLES.get(id);
            if (role == null) {
                continue;
            }
            if (!(level.getEntity(id) instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            switch (role) {
                case TUNNELER -> tickTunneler(host, mob, now);
                case STANDARD_BEARER -> tickBearer(host, mob, defenders, now);
                case GRAPPLER -> tickGrappler(host, mob, defenders, now);
                default -> {
                }
            }
        }
        tickGraves(host, now);
        tickTreasure(host, defenders, now);
        if (now % 100 == 0) {
            buffedUntil.values().removeIf(until -> until < now);
            noFallUntil.values().removeIf(until -> until < now);
            ROLES.keySet().removeIf(id -> !(level.getEntity(id) instanceof Mob m) || !m.isAlive());
        }
    }

    // ------------------------------------------------------------------ revenant

    private void makeGrave(Host host, LivingEntity dead) {
        ServerLevel level = host.world();
        long now = level.getGameTime();
        BlockPos ground = BossService.groundNear(level, dead.getX(), dead.getY(), dead.getZ());
        Vec3 spot = ground == null ? dead.position() : Vec3.atBottomCenterOf(ground);
        Husk hitbox = EntityTypes.HUSK.create(level, EntitySpawnReason.EVENT);
        if (hitbox == null) {
            return;
        }
        hitbox.snapTo(spot.x, spot.y, spot.z, dead.getYRot(), 0f);
        hitbox.setNoAi(true);
        hitbox.setSilent(true);
        hitbox.setPersistenceRequired();
        // The invisibility effect: a plain invisible flag goes again with the next effect.
        hitbox.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        hitbox.setInvisible(true);
        set(hitbox, Attributes.MAX_HEALTH, 2.0);
        hitbox.setHealth(2.0f);
        set(hitbox, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        set(hitbox, Attributes.SCALE, 0.55);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            hitbox.setItemSlot(slot, ItemStack.EMPTY);
            hitbox.setDropChance(slot, 0.0f);
        }
        hitbox.addTag(MobFactory.TAG);   // defenders and towers may strike it
        hitbox.addTag(TAG_GRAVE);
        Display.BlockDisplay mound = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        ((BlockDisplayAccessor) mound).nightsiege$setBlockState(Blocks.COARSE_DIRT.defaultBlockState());
        ((DisplayAccessor) mound).nightsiege$setTransformation(new Transformation(
                new Vector3f(-0.45f, -0.08f, -0.75f), new Quaternionf(), new Vector3f(0.9f, 0.3f, 1.5f), new Quaternionf()));
        mound.setPos(spot.x, spot.y, spot.z);
        mound.addTag(TAG_GRAVE);
        // Noted before they enter the world, else the check for leftovers of a restart takes them away.
        long riseAt = now + Math.round(host.config().revenantRiseSeconds * 20.0);
        Grave grave = new Grave(hitbox.getUUID(), mound.getUUID(), spot, riseAt);
        graves.add(grave);
        if (!level.addFreshEntity(hitbox)) {
            graves.remove(grave);
            return;
        }
        level.addFreshEntity(mound);
        BlockState soil = level.getBlockState(BlockPos.containing(spot).below());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil.isAir() ? Blocks.DIRT.defaultBlockState() : soil),
                spot.x, spot.y + 0.2, spot.z, 20, 0.4, 0.15, 0.6, 0.05);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.HOSTILE, 1.0f, 0.6f);
    }

    private void tickGraves(Host host, long now) {
        ServerLevel level = host.world();
        Iterator<Grave> it = graves.iterator();
        List<Grave> rising = new ArrayList<>();
        while (it.hasNext()) {
            Grave g = it.next();
            Entity hitbox = level.getEntity(g.hitbox);
            if (!(hitbox instanceof LivingEntity box) || !box.isAlive()) {
                discard(level, g.mound);
                it.remove();
                continue;
            }
            BlockPos at = BlockPos.containing(g.spot);
            if (box.isOnFire() || level.getBlockState(at).is(BlockTags.FIRE) || level.getBlockState(at).is(Blocks.LAVA)) {
                breakGrave(level, g, true);
                it.remove();
                continue;
            }
            if (now % 10 == 0) {
                level.sendParticles(ParticleTypes.SOUL, g.spot.x, g.spot.y + 0.3, g.spot.z, 1, 0.3, 0.1, 0.5, 0.01);
            }
            if (now >= g.riseAt) {
                rising.add(g);
                it.remove();
            }
        }
        for (Grave g : rising) {
            rise(host, g);
        }
    }

    private void rise(Host host, Grave g) {
        ServerLevel level = host.world();
        discard(level, g.hitbox);
        discard(level, g.mound);
        Mob mob = host.spawn(Role.REVENANT.entryId(), BlockPos.containing(g.spot));
        if (mob == null) {
            return;
        }
        mob.addTag(TAG_RISEN);
        mob.setHealth((float) Math.max(1.0, mob.getMaxHealth() * host.config().revenantRiseHealth));
        host.addMob(mob);
        aim(mob, host.defenders());
        risenTonight++;
        BlockState soil = level.getBlockState(BlockPos.containing(g.spot).below());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil.isAir() ? Blocks.DIRT.defaultBlockState() : soil),
                g.spot.x, g.spot.y + 0.3, g.spot.z, 30, 0.4, 0.3, 0.4, 0.1);
        level.sendParticles(ParticleTypes.SCULK_SOUL, g.spot.x, g.spot.y + 0.6, g.spot.z, 8, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, g.spot.x, g.spot.y, g.spot.z, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.0f, 0.7f);
    }

    /** A grave is struck down or burns: no revenant from it. */
    private void breakGrave(ServerLevel level, Grave g, boolean kill) {
        if (kill && level.getEntity(g.hitbox) instanceof LivingEntity box && box.isAlive()) {
            box.discard();
        }
        discard(level, g.mound);
        gravesBroken++;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()),
                g.spot.x, g.spot.y + 0.2, g.spot.z, 24, 0.4, 0.15, 0.6, 0.1);
        level.sendParticles(ParticleTypes.POOF, g.spot.x, g.spot.y + 0.3, g.spot.z, 6, 0.3, 0.2, 0.3, 0.02);
        level.playSound(null, g.spot.x, g.spot.y, g.spot.z, SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 0.8f, 0.6f);
    }

    /** A summoner of the dead took a corpse: a grave there stays empty. */
    public static void corpseTaken(ServerLevel level, Vec3 at) {
        Foes f = current;
        if (f == null || level == null || at == null) {
            return;
        }
        Iterator<Grave> it = f.graves.iterator();
        while (it.hasNext()) {
            Grave g = it.next();
            if (g.spot.distanceToSqr(at.x, g.spot.y, at.z) <= 2.25) {
                discard(level, g.hitbox);
                discard(level, g.mound);
                level.sendParticles(ParticleTypes.SOUL, g.spot.x, g.spot.y + 0.4, g.spot.z, 8, 0.3, 0.3, 0.3, 0.02);
                f.gravesTaken++;
                it.remove();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ tunneler

    private void tickTunneler(Host host, Mob mob, long now) {
        if (EMERGED.contains(mob.getUUID())) {
            return;
        }
        ServerLevel level = host.world();
        SiegeConfig cfg = host.config();
        BlockPos anchor = host.anchor();
        double cx = anchor.getX() + 0.5;
        double cz = anchor.getZ() + 0.5;
        Dig dig = digs.get(mob.getUUID());
        if (dig == null) {
            dig = new Dig();
            dig.since = now;
            digs.put(mob.getUUID(), dig);
        }
        switch (dig.phase) {
            case 0 -> {
                // To 20-30 blocks before the wall (or at once when it is nearer already).
                mob.setTarget(null);
                BaseScan base = host.base();
                double edge = base == null ? 6.0 : base.edgeTowards(mob.getX(), mob.getZ());
                double dist = Math.sqrt((mob.getX() - cx) * (mob.getX() - cx) + (mob.getZ() - cz) * (mob.getZ() - cz));
                double want = edge + 20.0 + (Math.abs(mob.getUUID().getLeastSignificantBits()) % 11);
                if (dist <= want || now - dig.since > 300) {
                    dig.phase = 1;
                    dig.since = now;
                    mob.getNavigation().stop();
                    level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 1.0f, 0.7f);
                } else if (now % 20 == 0) {
                    mob.getNavigation().moveTo(cx, anchor.getY(), cz, 1.0);
                }
            }
            case 1 -> {
                mob.setTarget(null);
                mob.getNavigation().stop();
                if (now % 4 == 0) {
                    dust(level, mob.blockPosition(), 8);
                    level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GRAVEL_HIT, SoundSource.HOSTILE, 0.8f, 0.8f);
                }
                if (now - dig.since >= 20) {
                    dig.exit = exitSpot(host);
                    hide(mob);
                    dig.phase = 2;
                    dig.since = now;
                }
            }
            default -> {
                long total = Math.round(cfg.tunnelSeconds * 20.0);
                long warn = Math.round(cfg.tunnelWarnSeconds * 20.0);
                long t = now - dig.since;
                if (dig.exit != null && t >= total - warn && now % 4 == 0) {
                    dust(level, dig.exit, 6);
                    if (now % 12 == 0) {
                        // Knocking from below.
                        level.playSound(null, dig.exit.getX() + 0.5, dig.exit.getY(), dig.exit.getZ() + 0.5,
                                SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.5f, 0.5f);
                    }
                }
                if (t >= total) {
                    emerge(host, mob, dig);
                    digs.remove(mob.getUUID());
                }
            }
        }
    }

    /** Under the ground: unseen, untouchable, still. */
    private static void hide(Mob mob) {
        mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        mob.setInvisible(true);
        mob.setInvulnerable(true);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setSilent(true);
        mob.teleportTo(mob.getX(), mob.getY() - 3.0, mob.getZ());
    }

    private void emerge(Host host, Mob mob, Dig dig) {
        ServerLevel level = host.world();
        BlockPos exit = dig.exit != null ? dig.exit : exitSpot(host);
        if (exit == null) {
            exit = host.anchor().above();
        }
        mob.removeEffect(MobEffects.INVISIBILITY);
        mob.setInvisible(false);
        mob.setInvulnerable(false);
        mob.setNoAi(false);
        mob.setNoGravity(false);
        mob.setSilent(host.event() == SiegeEvent.FOG);
        mob.teleportTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5);
        EMERGED.add(mob.getUUID());
        tunnelsDone++;
        dust(level, exit, 30);
        level.playSound(null, exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
        aim(mob, host.defenders());
    }

    /** Somewhere inside the walls, a few blocks from the core. */
    private static BlockPos exitSpot(Host host) {
        ServerLevel level = host.world();
        BlockPos anchor = host.anchor();
        BaseScan base = host.base();
        Random random = host.random();
        double cx = anchor.getX() + 0.5;
        double cz = anchor.getZ() + 0.5;
        for (int attempt = 0; attempt < 16; attempt++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double edge = base == null ? 8.0 : base.edgeTowards(cx + Math.cos(a) * 8, cz + Math.sin(a) * 8);
            double r = 2.5 + random.nextDouble() * Math.max(0.5, Math.min(edge - 3.0, 6.0));
            BlockPos spot = BossService.groundNear(level, cx + Math.cos(a) * r, anchor.getY(), cz + Math.sin(a) * r);
            if (spot != null && (base == null || base.contains(spot.getX(), spot.getZ())) && !spot.equals(anchor)) {
                return spot;
            }
        }
        return BossService.groundNear(level, cx + 2.0, anchor.getY(), cz);
    }

    private static void dust(ServerLevel level, BlockPos at, int count) {
        BlockState soil = level.getBlockState(at.below());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil.isAir() ? Blocks.DIRT.defaultBlockState() : soil),
                at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, count, 0.35, 0.05, 0.35, 0.05);
    }

    // ------------------------------------------------------------------ standard bearer

    private void tickBearer(Host host, Mob bearer, List<ServerPlayer> defenders, long now) {
        if (now % 20 != 0) {
            return;
        }
        ServerLevel level = host.world();
        double r = host.config().standardBearerRadius;
        AABB box = bearer.getBoundingBox().inflate(r, 4.0, r);
        for (Mob other : level.getEntitiesOfClass(Mob.class, box, m -> m != bearer && m.isAlive() && m instanceof Enemy
                && m.entityTags().contains(MobFactory.TAG) && !m.entityTags().contains(TAG_GRAVE)
                && !Companions.isCompanion(m) && !BossService.isBossOrPart(m))) {
            if (other.distanceToSqr(bearer) > r * r) {
                continue;
            }
            other.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0, true, true));
            buffedUntil.put(other.getUUID(), now + 30);
        }
        BossService.ring(level, new DustParticleOptions(0x8A1A1A, 1.1f), bearer.position().add(0, 0.15, 0), r, 28);
        // It stays behind the others: too close to a defender, it backs off.
        ServerPlayer near = nearest(defenders, bearer);
        if (near != null && bearer.distanceToSqr(near) < 100.0) {
            Vec3 away = bearer.position().subtract(near.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0e-4) {
                Vec3 to = bearer.position().add(away.normalize().scale(6.0));
                bearer.getNavigation().moveTo(to.x, to.y, to.z, 1.1);
            }
        }
    }

    /** Monsters in a banner's aura hit harder (also with arrows: the shooter counts). */
    public static float bannerFactor(Entity attacker) {
        Foes f = current;
        if (f == null || attacker == null) {
            return 1f;
        }
        Long until = f.buffedUntil.get(attacker.getUUID());
        if (until == null || until < attacker.level().getGameTime()) {
            return 1f;
        }
        SiegeManager m = SiegeManager.get();
        return (float) (1.0 + (m == null ? 0.2 : m.config().standardBearerBonus));
    }

    // ------------------------------------------------------------------ grappler

    private void tickGrappler(Host host, Mob mob, List<ServerPlayer> defenders, long now) {
        ServerLevel level = host.world();
        SiegeConfig cfg = host.config();
        Hook hook = hooks.get(mob.getUUID());
        if (hook == null) {
            hook = new Hook();
            hook.next = now + 40 + host.random().nextInt(60);   // the first throw after 2-5 s
            hooks.put(mob.getUUID(), hook);
        }
        if (hook.target != null) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(hook.target);
            if (target == null || !target.isAlive() || target.level() != level) {
                hook.target = null;
                hook.next = now + 60;
                return;
            }
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(target, 30f, 30f);
            if (now % 4 == 0) {
                Vec3 from = mob.position().add(0, mob.getBbHeight() * 0.7, 0);
                Vec3 to = target.position().add(0, 1.0, 0);
                BossService.line(level, ParticleTypes.CRIT, from, to, (int) Math.max(6, from.distanceTo(to) * 1.5));
            }
            if (now >= hook.fireAt) {
                fire(level, mob, target, cfg, now);
                hook.target = null;
                hook.next = now + Math.round(cfg.grappleSeconds * 20.0);
            }
            return;
        }
        // A defender standing high within reach (a little more: it walks into position).
        ServerPlayer best = null;
        double reach = cfg.grappleRange + 8.0;
        double bestDistance = reach * reach;
        for (ServerPlayer p : defenders) {
            if (p.isPassenger() || p.isSpectator() || Drones.isDrone(p)) {
                continue;   // a gunner in his tower stays where he is
            }
            double d = mob.distanceToSqr(p);
            if (d <= bestDistance && standsHigh(level, p, cfg.skyHunterHeight)) {
                bestDistance = d;
                best = p;
            }
        }
        if (best == null) {
            POSITIONING.remove(mob.getUUID());
            return;   // nobody up there: it fights hand to hand like the others
        }
        // Right under the wall it cannot see him: it keeps 6-9 blocks off, in sight, while the hook is ready.
        // It takes its place 4 s before the throw (so it does not run under the wall first and lose sight).
        boolean ready = now >= hook.next - 80;
        boolean seen = mob.hasLineOfSight(best);
        double dx = mob.getX() - best.getX();
        double dz = mob.getZ() - best.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        boolean placed = seen && flat >= 5.0 && flat <= cfg.grappleRange - 2.0;
        if (ready && !placed) {
            POSITIONING.add(mob.getUUID());
            mob.setTarget(null);   // its melee goal would walk it to the wall again
        } else {
            POSITIONING.remove(mob.getUUID());
        }
        if (ready && !placed && now % 10 == 0) {
            double want = 7.5;
            double ux = flat < 1.0e-3 ? 1.0 : dx / flat;
            double uz = flat < 1.0e-3 ? 0.0 : dz / flat;
            BlockPos spot = BossService.groundNear(level, best.getX() + ux * want, mob.getY(), best.getZ() + uz * want);
            if (spot != null) {
                mob.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.15);
            }
        }
        if (now < hook.next || !seen || mob.distanceToSqr(best) > cfg.grappleRange * cfg.grappleRange) {
            return;
        }
        POSITIONING.remove(mob.getUUID());
        hook.target = best.getUUID();
        hook.fireAt = now + 20;
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.HOSTILE, 1.2f, 0.6f);
    }

    private void fire(ServerLevel level, Mob mob, ServerPlayer target, SiegeConfig cfg, long now) {
        double reach = cfg.grappleRange + 2.0;
        if (mob.distanceToSqr(target) > reach * reach || !mob.hasLineOfSight(target)) {
            return;
        }
        if (target.isBlocking()) {
            hooksBlocked++;
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.8f);
            return;
        }
        Vec3 pull = mob.position().subtract(target.position());
        double flat = Math.sqrt(pull.x * pull.x + pull.z * pull.z);
        double speed = Math.min(2.0, 0.4 + flat * 0.12);
        double dx = flat < 1.0e-3 ? 0.0 : pull.x / flat * speed;
        double dz = flat < 1.0e-3 ? 0.0 : pull.z / flat * speed;
        target.setDeltaMovement(dx, 0.42, dz);
        target.hurtMarked = true;
        noFallUntil.put(target.getUUID(), now + 100);
        hooksLanded++;
        mob.setTarget(target);   // now it fights the one it pulled down
        Vec3 from = mob.position().add(0, mob.getBbHeight() * 0.7, 0);
        BossService.line(level, ParticleTypes.ENCHANTED_HIT, from, target.position().add(0, 1.0, 0), 16);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.2f, 0.6f);
        target.sendSystemMessage(Component.translatable("nightsiege.foe.grappler.hooked").withStyle(ChatFormatting.GRAY), true);
    }

    /** At least {@code height} blocks above the ground around him (as for the vexes and phantoms). */
    static boolean standsHigh(ServerLevel world, ServerPlayer player, int height) {
        int feet = player.getBlockY();
        int lowest = feet;
        BlockPos base = player.blockPosition();
        int[][] probes = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
        for (int[] p : probes) {
            int x = base.getX() + p[0];
            int z = base.getZ() + p[1];
            int y = feet;
            while (y > feet - 12 && world.getBlockState(new BlockPos(x, y - 1, z)).getCollisionShape(world, new BlockPos(x, y - 1, z)).isEmpty()) {
                y--;
            }
            lowest = Math.min(lowest, y);
        }
        return feet - lowest >= Math.max(1, height);
    }

    // ------------------------------------------------------------------ treasure raider

    private void spawnTreasure(Host host) {
        treasureCame = true;
        BlockPos spot = outsideSpot(host);
        if (spot == null) {
            return;
        }
        Mob mob = host.spawn(Role.TREASURE_RAIDER.entryId(), spot);
        if (mob == null) {
            return;
        }
        ServerLevel level = host.world();
        treasure = mob.getUUID();
        treasureGone = level.getGameTime() + Math.round(host.config().treasureRaiderSeconds * 20.0);
        SiegeTeams.mark(level.getServer(), mob, SiegeTeams.CHAMPION, net.minecraft.world.scores.TeamColor.GOLD);
        host.notice(Component.translatable("nightsiege.foe.treasure_raider.here").withStyle(ChatFormatting.GOLD));
        for (ServerPlayer p : host.defenders()) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.0f, 1.4f);
        }
    }

    /** For the tests and the command: the treasure raider comes now. */
    public boolean treasureNow(Host host) {
        if (treasure != null) {
            return false;
        }
        spawnTreasure(host);
        return treasure != null;
    }

    private void tickTreasure(Host host, List<ServerPlayer> defenders, long now) {
        if (treasure == null) {
            return;
        }
        ServerLevel level = host.world();
        if (!(level.getEntity(treasure) instanceof Mob mob) || !mob.isAlive()) {
            treasure = null;
            return;
        }
        mob.setTarget(null);
        mob.setAggressive(true);   // shows the gold in its raised hand
        if (now % 4 == 0) {
            level.sendParticles(new DustParticleOptions(0xFFC21A, 1.0f), mob.getX(), mob.getY() + 1.0, mob.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
        }
        if (now >= treasureGone) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.8, mob.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0f, 1.0f);
            mob.discard();
            treasure = null;
            treasureOutcome = "entkommen";
            host.notice(Component.translatable("nightsiege.foe.treasure_raider.gone").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (now % 10 != 0) {
            return;
        }
        ServerPlayer near = nearest(defenders, mob);
        BlockPos anchor = host.anchor();
        Vec3 away;
        if (near != null && mob.distanceToSqr(near) < 28.0 * 28.0) {
            away = mob.position().subtract(near.position());
        } else {
            away = mob.position().subtract(Vec3.atCenterOf(anchor));
        }
        away = away.multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0e-4) {
            away = new Vec3(1, 0, 0);
        }
        Vec3 to = mob.position().add(away.normalize().scale(10.0));
        BlockPos ground = BossService.groundNear(level, to.x, mob.getY(), to.z);
        if (ground != null) {
            mob.getNavigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 1.3);
        }
    }

    private void treasureDown(Host host, LivingEntity dead) {
        treasure = null;
        ServerLevel level = host.world();
        SiegeConfig cfg = host.config();
        level.sendParticles(new DustParticleOptions(0xFFC21A, 1.6f), dead.getX(), dead.getY() + 1.0, dead.getZ(), 40, 0.6, 0.8, 0.6, 0.0);
        level.playSound(null, dead.getX(), dead.getY(), dead.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE, 1.0f, 1.2f);
        if (host.random().nextDouble() < cfg.treasureCrateChance) {
            treasureOutcome = "kisten";
            host.awardCrates();
            host.notice(Component.translatable("nightsiege.foe.treasure_raider.crates").withStyle(ChatFormatting.GOLD));
        } else {
            treasureOutcome = "erfahrung";
            for (UUID id : host.nightPlayers()) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) {
                    p.giveExperiencePoints(cfg.treasureXp);
                }
            }
            host.notice(Component.translatable("nightsiege.foe.treasure_raider.xp", cfg.treasureXp).withStyle(ChatFormatting.GOLD));
        }
    }

    /** Just outside the walls, 8-16 blocks beyond them on a random side (in the old ring without walls). */
    private static BlockPos outsideSpot(Host host) {
        ServerLevel level = host.world();
        BlockPos anchor = host.anchor();
        BaseScan base = host.base();
        Random random = host.random();
        for (int attempt = 0; attempt < 24; attempt++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double edge = base == null ? 10.0 : base.edgeTowards(anchor.getX() + 0.5 + Math.cos(a) * 8, anchor.getZ() + 0.5 + Math.sin(a) * 8);
            double d = edge + 8.0 + random.nextDouble() * 8.0;
            int x = anchor.getX() + (int) Math.round(Math.cos(a) * d);
            int z = anchor.getZ() + (int) Math.round(Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4) || (base != null && base.contains(x, z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spot = new BlockPos(x, y, z);
            if (level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir()
                    && level.getBlockState(spot.below()).getFluidState().isEmpty()) {
                return spot;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ damage and death

    /** A monster or a grave fell. */
    public void onDeath(Host host, LivingEntity dead, DamageSource source) {
        if (dead == null || host.world() == null) {
            return;
        }
        if (dead.entityTags().contains(TAG_GRAVE)) {
            Iterator<Grave> it = graves.iterator();
            while (it.hasNext()) {
                Grave g = it.next();
                if (g.hitbox.equals(dead.getUUID())) {
                    breakGrave(host.world(), g, false);
                    it.remove();
                    break;
                }
            }
            return;
        }
        Role role = roleOf(dead);
        if (role == null) {
            return;
        }
        ROLES.remove(dead.getUUID());
        digs.remove(dead.getUUID());
        hooks.remove(dead.getUUID());
        if (role == Role.REVENANT && !dead.entityTags().contains(TAG_RISEN) && !burning(dead, source)) {
            makeGrave(host, dead);
        } else if (role == Role.TREASURE_RAIDER && dead.getUUID().equals(treasure)) {
            treasureDown(host, dead);
        }
    }

    private static boolean burning(LivingEntity dead, DamageSource source) {
        return (source != null && source.is(DamageTypeTags.IS_FIRE)) || dead.isOnFire() || dead.getRemainingFireTicks() > 0;
    }

    /** After a hit: a struck grappler drops its hook, a struck treasure raider slows down. */
    public void afterDamage(LivingEntity victim, DamageSource source) {
        Role role = victim == null ? null : ROLES.get(victim.getUUID());
        if (role == Role.GRAPPLER) {
            Hook hook = hooks.get(victim.getUUID());
            if (hook != null && hook.target != null) {
                hook.target = null;
                hook.next = victim.level().getGameTime() + 100;
                victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.TRIPWIRE_CLICK_ON,
                        SoundSource.HOSTILE, 1.0f, 0.8f);
            }
        } else if (role == Role.TREASURE_RAIDER) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, true));
        }
    }

    /** Whether a hit lands: a pulled defender takes no fall damage. */
    public boolean allowDamage(LivingEntity victim, DamageSource source) {
        if (victim instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL)) {
            Long until = noFallUntil.get(player.getUUID());
            if (until != null && until >= player.level().getGameTime()) {
                noFallUntil.remove(player.getUUID());
                fallsSaved++;
                return false;
            }
        }
        return true;
    }

    /** The iron knight: arrows and bolts glance off its armour. */
    public static float hitFactor(LivingEntity victim, DamageSource source) {
        if (victim == null || source == null || !victim.entityTags().contains(Role.IRON_KNIGHT.tag())
                || !source.is(DamageTypeTags.IS_PROJECTILE)) {
            return 1f;
        }
        if (victim.level() instanceof ServerLevel level) {
            level.playSound(null, victim.getX(), victim.getY() + 1.0, victim.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.7f, 1.5f);
        }
        SiegeManager m = SiegeManager.get();
        return (float) (m == null ? 0.2 : m.config().ironKnightRangedShare);
    }

    /** Whom the new attackers may pick: the treasure raider nobody, a tunneler nobody until it is up. */
    public static boolean mayTarget(Mob mob, LivingEntity target) {
        Role role = ROLES.get(mob.getUUID());
        if (role == Role.TREASURE_RAIDER) {
            return false;
        }
        if (role == Role.GRAPPLER && POSITIONING.contains(mob.getUUID())) {
            return false;
        }
        return role != Role.TUNNELER || EMERGED.contains(mob.getUUID());
    }

    /** A grave or a treasure raider left from before a restart. */
    public static boolean isStale(Entity entity) {
        Foes f = current;
        if (entity.entityTags().contains(TAG_GRAVE)) {
            if (f == null) {
                return true;
            }
            for (Grave g : f.graves) {
                if (g.hitbox.equals(entity.getUUID()) || g.mound.equals(entity.getUUID())) {
                    return false;
                }
            }
            return true;
        }
        if (entity.entityTags().contains(Role.TREASURE_RAIDER.tag())) {
            return !ROLES.containsKey(entity.getUUID());   // noted when it is made, gone after a restart
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    private static ServerPlayer nearest(List<ServerPlayer> defenders, Entity from) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer p : defenders) {
            if (p.isSpectator() || Drones.isDrone(p) || p.level() != from.level()) {
                continue;
            }
            double d = from.distanceToSqr(p);
            if (d < bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        return best;
    }

    private static void aim(Mob mob, List<ServerPlayer> defenders) {
        ServerPlayer near = nearest(defenders, mob);
        if (near != null) {
            mob.setTarget(near);
        }
    }

    private static void set(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void discard(ServerLevel level, UUID id) {
        if (id == null) {
            return;
        }
        Entity e = level.getEntity(id);
        if (e != null) {
            e.discard();
        }
    }

    // ------------------------------------------------------------------ for the tests

    public int graveCount() {
        return graves.size();
    }

    public int digging() {
        return digs.size();
    }

    public UUID treasure() {
        return treasure;
    }

    /** The treasure raider's time runs out now (tests). */
    public void treasureTimeUp(long now) {
        treasureGone = now;
    }

    public static boolean emerged(UUID id) {
        return EMERGED.contains(id);
    }

    public static int known() {
        return ROLES.size();
    }
}
