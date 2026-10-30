package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.BossKind;
import com.mki.nightsiege.core.BossPlan;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.mixin.BlockDisplayAccessor;
import com.mki.nightsiege.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The boss of a boss night (Simon, 25 October 2026): every 4th siege (6th until the content
 * update of 29 October 2026) ends with one of eight bosses (see {@link BossKind}). It comes as the finale with a last big wave; the night is
 * won when it falls and lost when it still stands at dawn. The abilities live in
 * {@link BossBrains}; this class spawns the boss, keeps its bar, its parts (soul crystals,
 * egg sacs) and tells the clients which monster wears which boss look.
 *
 * <p>The first four change no blocks; the frost jarl raises walls of ice for 15 s and the ram
 * titan breaks through walls and leaves craters, both written down for the repair after the
 * night (content update). Bosses are not in the night's list of monsters, so ladders,
 * diggers and the kill goal leave them alone; towers and players hit them like any monster.</p>
 */
public final class BossService {

    /** Tag of a boss (and the warlord's ravager). */
    public static final String TAG_BOSS = "nightsiege_boss";
    /** Tag of a part of a boss fight: the hitbox of a soul crystal or egg sac, and its look. */
    public static final String TAG_PART = "nightsiege_bosspart";
    /** Tag of the storm witch's phantom: it cannot be hurt, so towers and monsters leave it alone. */
    public static final String TAG_MOUNT = "nightsiege_bossmount";
    /** Tags of the lich's arrows: the soul arrow (10 % of max health, slows) and the rain (6 %). */
    public static final String TAG_SOUL_ARROW = "nightsiege_soularrow";
    public static final String TAG_RAIN_ARROW = "nightsiege_rainarrow";

    /**
     * Shares of the max health on top of the boss's blows (Simon, 28 October 2026), at level 10;
     * the abilities pass their own share to {@link #strike}.
     */
    static final double MELEE_SHARE = 12.0;
    static final double QUEEN_BITE_SHARE = 15.0;
    static final double SOUL_ARROW_SHARE = 10.0;
    static final double RAIN_ARROW_SHARE = 6.0;

    /** Set while an ability's own blow lands: its share comes from {@link #strike}, not as a melee share. */
    private static final ThreadLocal<Boolean> ABILITY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** What the night lends the boss. */
    public interface Host {
        ServerLevel world();

        BlockPos anchor();

        int level();

        SiegeConfig config();

        Random random();

        /** The defenders still standing, in the boss's world. */
        List<ServerPlayer> defenders();

        /** Everyone who sees the boss bar (the defenders, the fallen watching). */
        List<ServerPlayer> watchers();

        /** A monster of the night at a spot (counted, aimed), or null when the field is full. */
        Mob spawnAdd(String entityId, BlockPos spot);

        /** A spot outside the walls where monsters come from. */
        BlockPos spawnSpot();

        /** Distance from the core to the wall in the direction of a point, or 0 without a base. */
        double baseEdge(double x, double z);

        /** Whether a point lies within the walls. */
        boolean insideBase(double x, double z);
    }

    /** A soul crystal or an egg sac: an invisible hitbox monster and the block shown on it. */
    static final class Part {
        final UUID hitbox;
        final UUID display;
        final String kind;
        final long born;
        long due;

        Part(UUID hitbox, UUID display, String kind, long born, long due) {
            this.hitbox = hitbox;
            this.display = display;
            this.kind = kind;
            this.born = born;
            this.due = due;
        }
    }

    private BossKind kind;
    private BossBrains.Brain brain;
    /** The night the boss fights in (level and config for the shares of the max health). */
    private Host host;
    private UUID bossId;
    /** The warlord on the ravager's back (the boss of the second phase), or the ember colossus's second half. */
    private UUID riderId;
    /** The storm witch's phantom (it cannot be hurt; it goes when she falls). */
    private UUID mountId;
    private ServerBossEvent bar;
    private boolean spawned;
    private boolean fallen;
    private double maxTotal = 1.0;
    /** Who dealt the killing blow (for the champion's points), or null. */
    private UUID killer;
    final List<Part> parts = new ArrayList<>();
    /** Network ids of the boss monsters and their look, for the clients. */
    private final Map<Integer, String> looks = new HashMap<>();
    /**
     * The looks of every boss service of the night (the season finale runs several at once), so
     * that one service's update does not wipe the others' looks on the clients.
     */
    private static final Map<Integer, String> ALL_LOOKS = new HashMap<>();
    /** Share of the boss night's health this boss gets (the season finale: about 40 %). */
    private double healthShare = 1.0;

    // ------------------------------------------------------------------ the night

    /** A boss night begins: this boss comes in the finale. */
    public void plan(BossKind kind) {
        plan(kind, 1.0);
    }

    /** As above with a share of its usual health (the season finale's bosses: about 40 %). */
    public void plan(BossKind kind, double healthShare) {
        reset(null);
        this.kind = kind;
        this.healthShare = Math.max(0.05, healthShare);
    }

    /** True for this service's boss, its rider or second half, its mount or one of its parts. */
    boolean owns(Entity entity) {
        if (entity == null) {
            return false;
        }
        UUID id = entity.getUUID();
        if (id.equals(bossId) || id.equals(riderId) || id.equals(mountId)) {
            return true;
        }
        for (Part part : parts) {
            if (part.hitbox.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The boss planned for tonight, or null in a night without boss. */
    public BossKind kind() {
        return kind;
    }

    public boolean planned() {
        return kind != null;
    }

    public boolean spawned() {
        return spawned;
    }

    /** True once the boss has fallen tonight. */
    public boolean fallen() {
        return fallen;
    }

    /** The boss monster of now (the ravager or, in the second phase, the warlord), or null. */
    public Mob boss(ServerLevel level) {
        Entity e = bossId == null || level == null ? null : level.getEntity(bossId);
        return e instanceof Mob mob && mob.isAlive() ? mob : null;
    }

    Mob rider(ServerLevel level) {
        Entity e = riderId == null || level == null ? null : level.getEntity(riderId);
        return e instanceof Mob mob && mob.isAlive() ? mob : null;
    }

    /** The storm witch's phantom, or null. */
    Mob mount(ServerLevel level) {
        Entity e = mountId == null || level == null ? null : level.getEntity(mountId);
        return e instanceof Mob mob && mob.isAlive() ? mob : null;
    }

    /** The boss comes: spawned outside the walls, its bar shown, everyone told. */
    public boolean spawn(Host host) {
        if (kind == null || spawned) {
            return false;
        }
        ServerLevel level = host.world();
        BlockPos spot = host.spawnSpot();
        if (spot == null) {
            return false;
        }
        int defenders = Math.max(1, host.defenders().size());
        this.host = host;
        brain = BossBrains.create(kind, this);
        Mob boss = create(level, kind.entityId(), spot, host.random());
        if (boss == null) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Boss {} nicht erschaffen", kind.id());
            return false;
        }
        double perPlayer = host.config().perPlayer(SiegeManager.get() != null && SiegeManager.get().inPlaytest(),
                host.config().bossHealthPerExtraPlayer);
        double health = BossPlan.health(kind.baseHealth(), host.level(), defenders, host.config(), perPlayer) * healthShare;
        // The golem and the ravager hit hard as they are: the new bosses start from a lighter blow.
        switch (kind) {
            case FROST_JARL -> set(boss, Attributes.ATTACK_DAMAGE, 6.0);
            case RAM_TITAN -> set(boss, Attributes.ATTACK_DAMAGE, 8.0);
            case EMBER_COLOSSUS -> set(boss, Attributes.ATTACK_DAMAGE, 5.0);
            default -> {
            }
        }
        // The ravager hits hard as it is; the warlord himself takes the boss factor.
        prepare(boss, host, health, kind.scale(), kind == BossKind.WARLORD ? 0.85 : kind.damage());
        boss.setCustomName(Component.translatable("nightsiege.boss." + kind.id()));
        if (!level.addFreshEntity(boss)) {
            return false;
        }
        bossId = boss.getUUID();
        forgetLooks();
        look(boss.getId(), kind.id());
        maxTotal = boss.getMaxHealth();
        if (kind == BossKind.WARLORD) {
            Mob rider = create(level, BossKind.WARLORD_RIDER, spot, host.random());
            if (rider != null) {
                double riderHealth = BossPlan.health(BossKind.WARLORD_RIDER_HEALTH, host.level(), defenders, host.config(), perPlayer)
                        * healthShare;
                prepare(rider, host, riderHealth, 1.35, kind.damage());
                rider.setCustomName(Component.translatable("nightsiege.boss.warlord_rider"));
                rider.snapTo(boss.getX(), boss.getY(), boss.getZ(), boss.getYRot(), 0f);
                if (level.addFreshEntity(rider) && rider.startRiding(boss)) {
                    riderId = rider.getUUID();
                    look(rider.getId(), "warlord_rider");
                    maxTotal += rider.getMaxHealth();
                } else {
                    rider.discard();
                }
            }
        }
        if (kind == BossKind.STORM_WITCH) {
            // Her phantom: it cannot be hurt and flies where her brain steers it.
            BlockPos high = spot.above(10);
            Mob phantom = create(level, "minecraft:phantom", high, host.random());
            if (phantom != null) {
                phantom.setNoAi(true);
                phantom.setNoGravity(true);
                phantom.setInvulnerable(true);
                phantom.setPersistenceRequired();
                set(phantom, Attributes.SCALE, 2.2);
                phantom.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
                phantom.addTag(MobFactory.TAG);
                phantom.addTag(TAG_MOUNT);
                if (level.addFreshEntity(phantom)) {
                    boss.snapTo(high.getX() + 0.5, high.getY(), high.getZ() + 0.5, boss.getYRot(), 0f);
                    if (boss.startRiding(phantom)) {
                        mountId = phantom.getUUID();
                        look(phantom.getId(), kind.id());
                    } else {
                        phantom.discard();
                    }
                }
            }
        }
        spawned = true;
        brain.spawned(host, boss);
        aim(host, boss);
        bar = new ServerBossEvent(com.mki.nightsiege.core.NightBarIds.make(com.mki.nightsiege.core.NightBarIds.Kind.BOSS), barName(level), color(kind), BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setDarkenScreen(true);
        bar.setProgress(1f);
        for (ServerPlayer p : host.watchers()) {
            bar.addPlayer(p);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("nightsiege.boss." + kind.id())
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("nightsiege.boss." + kind.id() + ".sub")
                    .withStyle(ChatFormatting.GOLD)));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                    SoundSource.HOSTILE, 0.9f, 0.55f);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SOUL, boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 1.2, 1.2, 1.2, 0.05);
        syncAll(level.getServer());
        return true;
    }

    /** For the tests: the boss's abilities wait (while it is photographed). */
    public static boolean testHold;

    /** Every tick of the night once the boss is there. */
    public void tick(Host host, long now) {
        if (!spawned || fallen || brain == null || testHold) {
            return;
        }
        this.host = host;
        ServerLevel level = host.world();
        Mob boss = boss(level);
        if (boss == null) {
            return;
        }
        // Lost far away (thrown, fled): back towards the base on its own feet (Simon 30.10.: the bosses
        // "teleportieren sich"). Only one lost very far, with no defender near it, is put back.
        BlockPos anchor = host.anchor();
        if (now % 20 == 0 && !(brain instanceof BossBrains.Lich)) {
            double away = boss.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            boolean watched = nearest(host.defenders(), boss.position(), 40.0) != null;
            if (away > 160.0 * 160.0 && !watched) {
                BlockPos spot = host.spawnSpot();
                if (spot != null) {
                    boss.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                }
            } else if (away > 64.0 * 64.0 && !watched) {
                boss.getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 1.2);
            }
        }
        if (now % 20 == 0 && (boss.getTarget() == null || !boss.getTarget().isAlive()) && !(brain instanceof BossBrains.Lich)) {
            aim(host, boss);
        }
        brain.tick(host, boss, now);
        tickParts(host, now);
        if (bar != null) {
            bar.setProgress((float) Math.max(0.0, Math.min(1.0, currentTotal(level) / maxTotal)));
            if (now % 20 == 0) {
                bar.setName(barName(level));
                List<ServerPlayer> watchers = host.watchers();
                for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
                    if (!watchers.contains(p)) {
                        bar.removePlayer(p);
                    }
                }
                for (ServerPlayer p : watchers) {
                    bar.addPlayer(p);
                }
            }
        }
    }

    private double currentTotal(ServerLevel level) {
        double total = 0;
        Entity main = bossId == null ? null : level.getEntity(bossId);
        if (main instanceof LivingEntity l && l.isAlive()) {
            total += l.getHealth();
        }
        Entity r = riderId == null ? null : level.getEntity(riderId);
        if (r instanceof LivingEntity l && l.isAlive() && r != main) {
            total += l.getHealth();
        }
        return total;
    }

    private Component barName(ServerLevel level) {
        Component name = Component.translatable("nightsiege.boss." + kind.id());
        String phase = brain == null ? null : brain.phase(level);
        return phase == null ? name : Component.empty().append(name).append(Component.literal(" – "))
                .append(Component.translatable(phase));
    }

    private static BossEvent.BossBarColor color(BossKind kind) {
        return switch (kind) {
            case GRAVE_KING -> BossEvent.BossBarColor.YELLOW;
            case SPIDER_QUEEN -> BossEvent.BossBarColor.RED;
            case BONE_LICH -> BossEvent.BossBarColor.BLUE;
            case WARLORD -> BossEvent.BossBarColor.PURPLE;
            case FROST_JARL -> BossEvent.BossBarColor.WHITE;
            case EMBER_COLOSSUS -> BossEvent.BossBarColor.RED;
            case STORM_WITCH -> BossEvent.BossBarColor.GREEN;
            case RAM_TITAN -> BossEvent.BossBarColor.PINK;
        };
    }

    /**
     * A monster died. True when it was the boss itself (the night is then won). The
     * ravager's death starts the warlord's second phase.
     */
    public boolean onDeath(Entity entity, DamageSource source) {
        if (!spawned || fallen || !(entity instanceof Mob mob)) {
            return false;
        }
        UUID id = mob.getUUID();
        if (id.equals(bossId) && riderId != null && kind == BossKind.WARLORD) {
            Mob rider = rider((ServerLevel) mob.level());
            if (rider != null) {
                // The ravager is down: the warlord fights on foot.
                bossId = riderId;
                brain.secondPhase((ServerLevel) mob.level(), rider);
                return false;
            }
        }
        if (kind == BossKind.EMBER_COLOSSUS && riderId != null && (id.equals(bossId) || id.equals(riderId))) {
            // One half of the colossus is down; the other burns on.
            ServerLevel level = (ServerLevel) mob.level();
            Mob other = id.equals(bossId) ? rider(level) : boss(level);
            if (other != null) {
                bossId = other.getUUID();
                riderId = null;
                level.sendParticles(ParticleTypes.LAVA, mob.getX(), mob.getY() + 0.5, mob.getZ(), 30, 1.0, 0.6, 1.0, 0.1);
                return false;
            }
        }
        if (id.equals(bossId) || (kind == BossKind.WARLORD && id.equals(riderId))) {
            fallen = true;
            if (brain != null) {
                brain.died((ServerLevel) mob.level());
            }
            killer = source != null && source.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
            ServerLevel level = (ServerLevel) mob.level();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mob.getX(), mob.getY() + 1.0, mob.getZ(), 2, 0.5, 0.5, 0.5, 0);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mob.getX(), mob.getY() + 1.5, mob.getZ(), 80, 1.0, 1.5, 1.0, 0.4);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), net.minecraft.sounds.SoundEvents.WITHER_DEATH,
                    SoundSource.HOSTILE, 0.7f, 1.2f);
            return true;
        }
        Part broken = null;
        for (Part part : parts) {
            if (part.hitbox.equals(id)) {
                broken = part;
                break;
            }
        }
        if (broken != null && brain != null) {
            brain.partBroken((ServerLevel) mob.level(), broken, mob);   // removes it from the list
        }
        return false;
    }

    /** The player who dealt the killing blow, or null. */
    public UUID killer() {
        return killer;
    }

    /**
     * Whether a hit lands: the lich's soul shield holds, the warlord in the saddle is covered
     * by his beast (the hit goes to the ravager), and no boss takes damage from falling or
     * from walls around it.
     */
    public boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (!spawned || fallen || brain == null || victim == null) {
            return true;
        }
        if (!victim.entityTags().contains(TAG_BOSS) || !owns(victim)) {
            return true;   // not a boss, or another service's (the season finale)
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)) {
            return false;
        }
        return brain.allowDamage((ServerLevel) victim.level(), victim, source, amount);
    }

    /**
     * A boss's blow landed on a player: the queen poisons, and every blow and every arrow of a
     * boss takes a share of the max health on top (Simon, 28 October 2026). A blocked hit takes
     * nothing; the share itself and an ability's blow (it brings its own share) add none.
     */
    public void afterHit(LivingEntity victim, DamageSource source, boolean blocked) {
        if (!spawned || fallen || brain == null || !(victim instanceof ServerPlayer player)) {
            return;
        }
        Entity attacker = source.getEntity();
        if (attacker == null || !attacker.entityTags().contains(TAG_BOSS) || !owns(attacker) || PercentDamage.inHit()
                || PercentDamage.is(source)) {
            return;
        }
        brain.hitPlayer(player, attacker);
        if (blocked || ABILITY.get() || host == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity direct = source.getDirectEntity();
        double share = 0.0;
        if (direct != null && direct.entityTags().contains(TAG_SOUL_ARROW)) {
            share = SOUL_ARROW_SHARE;
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), attacker);
        } else if (direct != null && direct.entityTags().contains(TAG_RAIN_ARROW)) {
            share = RAIN_ARROW_SHARE;
        } else if (direct == attacker) {
            share = kind == BossKind.SPIDER_QUEEN ? QUEEN_BITE_SHARE : MELEE_SHARE;
        }
        if (share > 0) {
            PercentDamage.hit(level, player, attacker, direct, share, host.level(), host.config(), host.config().bossPercentDamage);
        }
    }

    /** Factor on a monster's damage from the boss's presence (the war banner). */
    public double damageFactor(Entity attacker) {
        if (!spawned || fallen || brain == null || attacker == null || !(attacker.level() instanceof ServerLevel level)) {
            return 1.0;
        }
        return brain.auraFactor(level, attacker);   // the warlord's banner (his own illagers; another boss is no illager)
    }

    /** The night ends: bar, parts and whatever is left of the boss go. */
    public void reset(MinecraftServer server) {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        if (brain != null && server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                brain.cleanUp(level);
                for (Part part : parts) {
                    discard(level, part.hitbox);
                    discard(level, part.display);
                }
                discard(level, bossId);
                discard(level, riderId);
                discard(level, mountId);
            }
        }
        parts.clear();
        boolean hadLooks = !looks.isEmpty();
        forgetLooks();
        if (hadLooks && server != null) {
            syncAll(server);
        }
        kind = null;
        brain = null;
        bossId = null;
        riderId = null;
        mountId = null;
        spawned = false;
        fallen = false;
        killer = null;
        maxTotal = 1.0;
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

    /** For the tests: the hitboxes of the soul crystals and egg sacs now. */
    public List<UUID> partHitboxes() {
        List<UUID> list = new ArrayList<>();
        for (Part part : parts) {
            list.add(part.hitbox);
        }
        return list;
    }

    /** For the tests (update 30.10.): the queen's straight runs and leaps, the lich's sweeps and shots. */
    public String testBrainStats() {
        if (brain instanceof BossBrains.SpiderQueen queen) {
            return "direct=" + queen.directTicks + " leaps=" + queen.leaps;
        }
        if (brain instanceof BossBrains.Lich lich) {
            return "dashes=" + lich.dashes() + " shots=" + lich.shots() + " lift=" + Math.round(lich.lift);
        }
        return "-";
    }

    /** For the tests: whether the lich lies worn out after his shield broke. */
    public boolean testLichExhausted(long now) {
        return brain instanceof BossBrains.Lich lich && lich.exhausted(now);
    }

    /** For the tests: the warlord on his ravager, the colossus's second half (null for the other bosses). */
    public Mob riderOf(ServerLevel level) {
        return rider(level);
    }

    /** For the tests: the storm witch's phantom, or null. */
    public Mob mountOf(ServerLevel level) {
        return mount(level);
    }

    /**
     * True while this boss takes no damage at all: the lich behind his soul shield. The
     * engineer's towers skip him then and go for the crystals (28 October 2026).
     */
    public boolean shielded(Entity entity) {
        return spawned && !fallen && isBoss(entity) && owns(entity) && brain instanceof BossBrains.Lich lich && lich.shield;
    }

    /** True for a boss or one of its parts (they never count as monsters of the night). */
    public static boolean isBossOrPart(Entity entity) {
        return entity != null && (entity.entityTags().contains(TAG_BOSS) || entity.entityTags().contains(TAG_PART)
                || entity.entityTags().contains(TAG_MOUNT));
    }

    /** True for the storm witch's phantom. */
    public static boolean isMount(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG_MOUNT);
    }

    public static boolean isBoss(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG_BOSS);
    }

    // ------------------------------------------------------------------ the clients' looks

    /** Tells one player which monsters wear a boss look (sent on join and whenever it changes). */
    public void syncTo(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE)) {
            return;
        }
        StringBuilder json = new StringBuilder("{\"looks\":{");
        boolean first = true;
        Map<Integer, String> all = new HashMap<>(FOE_LOOKS);
        all.putAll(ALL_LOOKS);
        for (Map.Entry<Integer, String> e : all.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
        }
        json.append("}}");
        ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("bosses", json.toString()));
    }

    /** The update-1 foes' own looks (design update, 29 October 2026): network id → folder ("foe_revenant"). */
    private static final Map<Integer, String> FOE_LOOKS = new HashMap<>();
    private static BossService last;

    {
        last = this;
    }

    /** Marks a foe as wearing its own look on the clients; drops the looks of foes that are gone. */
    public static void foeLook(net.minecraft.world.entity.Mob mob, String folder) {
        if (mob.level() instanceof ServerLevel level) {
            FOE_LOOKS.keySet().removeIf(id -> {
                net.minecraft.world.entity.Entity e = level.getEntity(id);
                return e == null || !e.isAlive();
            });
        }
        FOE_LOOKS.put(mob.getId(), folder);
        if (last != null) {
            last.syncAll(mob.level().getServer());
        }
    }

    /** As {@link #foeLook}, without telling the clients yet (many at once: then {@link #syncFoeLooks}). */
    public static void foeLookQuiet(net.minecraft.world.entity.Mob mob, String folder) {
        FOE_LOOKS.put(mob.getId(), folder);
    }

    /** Tells every client the looks now, dropping those of monsters that are gone. */
    public static void syncFoeLooks(ServerLevel level) {
        FOE_LOOKS.keySet().removeIf(id -> {
            net.minecraft.world.entity.Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        if (last != null) {
            last.syncAll(level.getServer());
        }
    }

    /** The foe or champion with this network id is gone (discarded at the end of a fight, 29 October 2026). */
    public static void dropFoeLook(MinecraftServer server, int entityId) {
        if (FOE_LOOKS.remove(entityId) != null && last != null) {
            last.syncAll(server);
        }
    }

    /** The look of the monster with this network id ("champion_stone_crypt" …), or null. */
    public static String foeLookOf(int entityId) {
        return FOE_LOOKS.get(entityId);
    }

    void syncAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            syncTo(p);
        }
    }

    // ------------------------------------------------------------------ building blocks

    static Mob create(ServerLevel level, String entityId, BlockPos spot, Random random) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(entityId);
        EntityType<?> type = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(id);
        Entity entity = type == null ? null : type.create(level, EntitySpawnReason.EVENT);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        return mob;
    }

    /** Health, size, damage, tags and protection of a boss monster. */
    static void prepare(Mob mob, Host host, double health, double scale, double damage) {
        set(mob, Attributes.MAX_HEALTH, health);
        mob.setHealth((float) health);
        AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(attack.getBaseValue() * SiegeMath.damageMultiplier(host.level(), host.config())
                    * damage * host.config().bossDamageMultiplier);
        }
        set(mob, Attributes.SCALE, scale);
        set(mob, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        set(mob, Attributes.FOLLOW_RANGE, 96.0);
        AttributeInstance step = mob.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) {
            step.setBaseValue(Math.max(step.getBaseValue(), 1.25));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0f);
        }
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        mob.setPersistenceRequired();
        mob.addTag(MobFactory.TAG);
        mob.addTag(TAG_BOSS);
    }

    static void set(LivingEntity mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    void aim(Host host, Mob boss) {
        ServerPlayer best = nearest(host.defenders(), boss.position(), 96.0);
        if (best != null) {
            boss.setTarget(best);
        }
    }

    static ServerPlayer nearest(List<ServerPlayer> players, Vec3 from, double range) {
        ServerPlayer best = null;
        double bestDistance = range * range;
        for (ServerPlayer p : players) {
            double d = p.position().distanceToSqr(from);
            if (d < bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        return best;
    }

    /** The damage of a boss ability: its base times the night's level and the boss factors. */
    static float abilityDamage(Host host, BossKind kind, double base) {
        return (float) (base * SiegeMath.damageMultiplier(host.level(), host.config()) * kind.damage()
                * host.config().bossDamageMultiplier);
    }

    /** Hurts a player as a blow of the boss (so shields, armour and the class factors count). */
    static void hurt(ServerLevel level, Mob boss, ServerPlayer player, float damage) {
        ABILITY.set(Boolean.TRUE);
        try {
            player.invulnerableTime = 0;
            player.hurtServer(level, level.damageSources().mobAttack(boss), damage);
        } finally {
            ABILITY.set(Boolean.FALSE);
        }
    }

    /**
     * An ability's blow: the usual damage (armour counts), then {@code share} % of the player's
     * max health that armour does not lessen (28 October 2026). A shield facing the boss blocks both.
     */
    static void strike(Host host, ServerLevel level, Mob boss, ServerPlayer player, float damage, double share) {
        hurt(level, boss, player, damage);
        if (share > 0 && player.isAlive()) {
            PercentDamage.hit(level, player, boss, boss, share, host.level(), host.config(), host.config().bossPercentDamage);
        }
    }

    /** As {@link #strike}, but the share goes through a raised shield (the jarl's stomp, the titan's charge). */
    static void strikeThrough(Host host, ServerLevel level, Mob boss, ServerPlayer player, float damage, double share) {
        hurt(level, boss, player, damage);
        if (share > 0 && player.isAlive()) {
            PercentDamage.hitThrough(level, player, boss, boss, share, host.level(), host.config(), host.config().bossPercentDamage);
        }
    }

    /**
     * The ember colossus breaks in two (content update): both halves keep {@code share} of the
     * whole's max health each and shrink a little; the bar goes on from where it stood.
     */
    boolean splitColossus(Host host, Mob whole, double share) {
        ServerLevel level = host.world();
        if (riderId != null || whole == null) {
            return false;
        }
        double max = whole.getMaxHealth();
        double half = max * share;
        double scale = whole.getScale() * 0.78;
        Mob other = create(level, kind.entityId(), whole.blockPosition(), host.random());
        if (other == null) {
            return false;
        }
        set(other, Attributes.ATTACK_DAMAGE, 5.0);
        prepare(other, host, half, scale, kind.damage());
        other.setCustomName(whole.getCustomName());
        double a = host.random().nextDouble() * Math.PI * 2.0;
        other.snapTo(whole.getX() + Math.cos(a) * 2.5, whole.getY() + 0.5, whole.getZ() + Math.sin(a) * 2.5, whole.getYRot(), 0f);
        if (!level.addFreshEntity(other)) {
            return false;
        }
        set(whole, Attributes.MAX_HEALTH, half);
        whole.setHealth((float) half);
        set(whole, Attributes.SCALE, scale);
        riderId = other.getUUID();
        look(other.getId(), kind.id());
        syncAll(level.getServer());
        // The bar stands where it stood: the two halves together are what was left.
        double shown = bar == null ? 0.5 : Math.max(0.05, bar.getProgress());
        maxTotal = 2.0 * half / shown;
        aim(host, other);
        return true;
    }

    /** Throws a player away from a point. */
    static void knock(ServerPlayer player, Vec3 from, double strength, double up) {
        Vec3 away = player.position().subtract(from).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
        player.push(away.x * strength, up, away.z * strength);
        player.hurtMarked = true;
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    static void ring(ServerLevel level, ParticleOptions type, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2.0 * i / points;
            level.sendParticles(type, center.x + Math.cos(a) * radius, center.y + 0.15, center.z + Math.sin(a) * radius,
                    1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    static void line(ServerLevel level, ParticleOptions type, Vec3 from, Vec3 to, int points) {
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.lerp(to, i / (double) points);
            level.sendParticles(type, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Free ground near a spot: a place where a monster can stand, within a few blocks up or down. */
    static BlockPos groundNear(ServerLevel level, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y, z);
        for (int dy = 3; dy >= -5; dy--) {
            BlockPos p = base.above(dy);
            if (!level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
                    && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && level.getFluidState(p).isEmpty()) {
                return p;
            }
        }
        return null;
    }

    /** Height of the ground (top of the blocks) at a column. */
    static int surface(ServerLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    /**
     * A part of the fight: an invisible hitbox monster that players and towers can hit,
     * with a block shown on it (a soul lantern, an egg). Gone with the night.
     */
    Part addPart(ServerLevel level, Vec3 at, String kind, BlockState shown, float size, double health, long now, long due) {
        Zombie hitbox = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (hitbox == null) {
            return null;
        }
        hitbox.snapTo(at.x, at.y, at.z, 0f, 0f);
        hitbox.setNoAi(true);
        hitbox.setInvisible(true);
        hitbox.setSilent(true);
        hitbox.setNoGravity(true);
        hitbox.setPersistenceRequired();
        set(hitbox, Attributes.MAX_HEALTH, health);
        hitbox.setHealth((float) health);
        set(hitbox, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        set(hitbox, Attributes.SCALE, size / 1.6);
        hitbox.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        // Invisible by an effect: adding any effect resets the plain invisible flag.
        hitbox.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            hitbox.setItemSlot(slot, ItemStack.EMPTY);
            hitbox.setDropChance(slot, 0f);
        }
        hitbox.addTag(MobFactory.TAG);
        hitbox.addTag(TAG_PART);
        if (!level.addFreshEntity(hitbox)) {
            return null;
        }
        Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        ((BlockDisplayAccessor) display).nightsiege$setBlockState(shown);
        ((DisplayAccessor) display).nightsiege$setTransformation(new Transformation(
                new Vector3f(-size / 2f, 0f, -size / 2f), new Quaternionf(), new Vector3f(size, size, size), new Quaternionf()));
        display.setPos(at.x, at.y, at.z);
        display.addTag(TAG_PART);
        level.addFreshEntity(display);
        Part part = new Part(hitbox.getUUID(), display.getUUID(), kind, now, due);
        parts.add(part);
        return part;
    }

    /** Changes the block a part shows (the egg cracks). */
    static void showBlock(ServerLevel level, Part part, BlockState state) {
        Entity e = level.getEntity(part.display);
        if (e instanceof Display.BlockDisplay display) {
            ((BlockDisplayAccessor) display).nightsiege$setBlockState(state);
        }
    }

    void removePart(ServerLevel level, Part part) {
        discard(level, part.hitbox);
        discard(level, part.display);
        parts.remove(part);
    }

    private void tickParts(Host host, long now) {
        ServerLevel level = host.world();
        Iterator<Part> it = parts.iterator();
        List<Part> due = new ArrayList<>();
        while (it.hasNext()) {
            Part part = it.next();
            Entity hitbox = level.getEntity(part.hitbox);
            if (hitbox == null || !hitbox.isAlive()) {
                discard(level, part.display);
                it.remove();
                continue;
            }
            if (part.due > 0 && now >= part.due) {
                due.add(part);
            }
        }
        for (Part part : due) {
            brain.partDue(host, part, now);
        }
    }

    /** Living parts of one kind. */
    List<Part> partsOf(String kind) {
        List<Part> list = new ArrayList<>();
        for (Part part : parts) {
            if (part.kind.equals(kind)) {
                list.add(part);
            }
        }
        return list;
    }

    /** Marks a monster as wearing a boss look on the clients (the guards of a boss do not). */
    void look(Mob mob, String look) {
        look(mob.getId(), look);
        syncAll(mob.level().getServer());
    }

    private void look(int networkId, String look) {
        looks.put(networkId, look);
        ALL_LOOKS.put(networkId, look);
    }

    /** This service's looks go (the others' stay). */
    private void forgetLooks() {
        for (Integer id : looks.keySet()) {
            ALL_LOOKS.remove(id);
        }
        looks.clear();
    }
}
