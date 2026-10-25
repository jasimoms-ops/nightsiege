package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.TrapKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs the engineer's traps: who may place them, what they do each tick,
 * reloading the turret and the free traps at the start of a siege.
 *
 * <p>Traps only ever hurt monsters ({@link Enemy}), never players or animals,
 * and they work by day as well as by night. A trap is as strong as its owner's
 * current skill tier, so upgrades reach traps that are already placed.</p>
 */
public final class TrapService {

    /** Extra Tesla damage, e.g. in a thunder night. */
    private java.util.function.DoubleSupplier teslaBoost = () -> 1.0;

    public void setTeslaBoost(java.util.function.DoubleSupplier boost) {
        this.teslaBoost = boost;
    }


    /** Traps are checked every this many ticks. */
    private static final int TICK_INTERVAL = 5;

    /** The manager swaps its save object when a world loads, so always ask for the current one. */
    private final Supplier<SiegeSave> save;
    private final SkillTree tree;
    private int tickCounter;
    /** Engineers whose turrets are overcharged: owner uuid to the game time it ends. */
    private final java.util.Map<String, Long> overchargedUntil = new java.util.HashMap<>();
    /** Single traps overcharged with the remote: trap key to the game time it ends. */
    private final java.util.Map<String, Long> boostedUntil = new java.util.HashMap<>();
    /** Monsters marked with a remote: monster uuid to (owner, end). */
    private final java.util.Map<UUID, RemoteMark> remoteMarks = new java.util.HashMap<>();
    /** Energy of every engineer's remote. */
    private final java.util.Map<UUID, Double> energy = new java.util.HashMap<>();
    /** Lends the combat marks (extra damage taken, glowing) to the remote and the acid. */
    private Marker marker = (victim, bonus, until, glow) -> {
    };
    private boolean dirty;

    private record RemoteMark(String owner, long until) {
    }

    public interface Marker {
        void mark(LivingEntity victim, double bonus, long until, boolean glow);
    }

    public void setMarker(Marker marker) {
        this.marker = marker;
    }

    /** Base energy of the remote and what each order costs. */
    public static final double REMOTE_ENERGY = 100.0;
    public static final double COST_OVERCHARGE = 30.0;
    /** Overcharging a tesla coil takes the whole remote, an arrow turret 70 % (shares of the maximum). */
    public static final double OVERCHARGE_TESLA_SHARE = 1.0;
    public static final double OVERCHARGE_TURRET_SHARE = 0.7;
    public static final double COST_MARK = 10.0;
    public static final double COST_DETONATE = 20.0;
    public static final double COST_RELOAD = 40.0;
    public static final double REMOTE_RANGE = 24.0;

    public TrapService(Supplier<SiegeSave> save, SkillTree tree) {
        this.save = save;
        this.tree = tree;
    }

    // ------------------------------------------------------------------
    // placing, breaking, reloading
    // ------------------------------------------------------------------

    /** True when this player may set down this trap: the engineer who unlocked it, or anyone in creative. */
    /**
     * Why this player may not set down this trap right now, or null when they may.
     * Creative players may always, so traps can be tried out freely.
     */
    public Component placeBlocked(ServerPlayer player, TrapKind kind) {
        if (player.getAbilities().instabuild) {
            return null;
        }
        int tier = tree.trapTier(kind, save.get().skillsOf(player.getUUID().toString()));
        if (tier <= 0) {
            return Component.translatable("nightsiege.msg.trap_locked",
                    Component.translatable("nightsiege.skill." + kind.id()));
        }
        int max = maxFor(kind, tier, save.get().skillsOf(player.getUUID().toString()));
        if (placedBy(player.getUUID().toString(), kind) >= max) {
            return Component.translatable("nightsiege.msg.trap_cap", max,
                    Component.translatable("block.nightsiege." + kind.id()));
        }
        return null;
    }

    /** Traps of this kind an engineer currently has in the world. */
    public int placedBy(String owner, TrapKind kind) {
        int count = 0;
        for (TrapRecord record : traps()) {
            if (kind.id().equals(record.kind) && owner.equals(record.owner)) {
                count++;
            }
        }
        return count;
    }

    public void onPlaced(ServerLevel level, BlockPos pos, LivingEntity placer, TrapKind kind) {
        String owner = placer == null ? "" : placer.getUUID().toString();
        String dimension = SiegeManager.dimensionName(level);
        traps().removeIf(t -> t.matches(dimension, pos.getX(), pos.getY(), pos.getZ()));
        TrapRecord record = new TrapRecord(kind.id(), dimension, pos.getX(), pos.getY(), pos.getZ(), owner);
        int tier = tierOf(record);
        record.uses = fullUses(kind, tier, record);
        record.ammo = kind.startAmmo(tier);
        traps().add(record);
        dirty = true;
        level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f, 1.6f);
    }

    /** Forgets a trap whose block was broken. Returns true when there was one. */
    public boolean onBroken(ServerLevel level, BlockPos pos) {
        String dimension = SiegeManager.dimensionName(level);
        boolean removed = traps().removeIf(t -> t.matches(dimension, pos.getX(), pos.getY(), pos.getZ()));
        dirty |= removed;
        return removed;
    }

    /**
     * Right click on a turret: arrows in hand are loaded, an empty hand shows the count.
     * Returns true when the click was handled.
     */
    public boolean onUse(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack held) {
        if (ModContent.trapOf(level.getBlockState(pos)) != TrapKind.TURRET) {
            return false;
        }
        // Only arrows or an empty hand talk to the turret; anything else builds as usual.
        if (held != null && !held.isEmpty() && !held.is(Items.ARROW)) {
            return false;
        }
        TrapRecord record = find(level, pos);
        if (record == null) {
            // Placed before the mod tracked it, or the save was lost: adopt it.
            onPlaced(level, pos, player, TrapKind.TURRET);
            record = find(level, pos);
            if (record == null) {
                return false;
            }
        }
        int max = maxAmmo(record);
        if (held != null && held.is(Items.ARROW) && record.ammo < max) {
            int load = Math.min(held.getCount(), max - record.ammo);
            record.ammo += load;
            if (!player.getAbilities().instabuild) {
                held.shrink(load);
            }
            dirty = true;
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
        actionBar(player, Component.translatable("nightsiege.msg.turret_ammo", record.ammo, max));
        return true;
    }

    /** Traps destroyed tonight whose engineer has trap recycling. */
    private final List<TrapRecord> recycled = new ArrayList<>();

    /** After the night: recycled traps stand where they stood (if the spot is free). */
    public void recycle(MinecraftServer server) {
        if (server == null || recycled.isEmpty()) {
            recycled.clear();
            return;
        }
        for (TrapRecord old : recycled) {
            TrapKind kind = TrapKind.byId(old.kind);
            ServerLevel level = SiegeManager.levelByName(server, old.dimension);
            net.minecraft.world.level.block.Block block = kind == null ? null : ModContent.TRAP_BLOCKS.get(kind);
            if (level == null || block == null) {
                continue;
            }
            BlockPos pos = new BlockPos(old.x, old.y, old.z);
            BlockState there = level.getBlockState(pos);
            if (!(there.isAir() || there.canBeReplaced())) {
                continue;
            }
            level.setBlock(pos, block.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
            String dimension = SiegeManager.dimensionName(level);
            traps().removeIf(t -> t.matches(dimension, pos.getX(), pos.getY(), pos.getZ()));
            TrapRecord record = new TrapRecord(kind.id(), dimension, pos.getX(), pos.getY(), pos.getZ(), old.owner);
            int tier = tierOf(record);
            record.uses = fullUses(kind, tier, record);
            record.ammo = kind.startAmmo(tier);
            traps().add(record);
            level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0);
        }
        recycled.clear();
        dirty = true;
    }

    /** The owner's turret nearest a spot that still has room for arrows (the copper golem's errand). */
    public BlockPos emptiestTurret(ServerLevel level, String owner, Vec3 near, double radius) {
        String dimension = SiegeManager.dimensionName(level);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (TrapRecord record : traps()) {
            if (!TrapKind.TURRET.id().equals(record.kind) || !owner.equals(record.owner) || !dimension.equals(record.dimension)) {
                continue;
            }
            int max = maxAmmo(record);
            if (record.ammo >= max) {
                continue;
            }
            BlockPos pos = new BlockPos(record.x, record.y, record.z);
            double d = near.distanceToSqr(Vec3.atCenterOf(pos));
            if (d > radius * radius) {
                continue;
            }
            double score = record.ammo * 4.0 + Math.sqrt(d);
            if (score < bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return best;
    }

    /** Loads arrows into a turret; returns how many went in. */
    public int addAmmo(ServerLevel level, BlockPos pos, int arrows) {
        TrapRecord record = find(level, pos);
        if (record == null || !TrapKind.TURRET.id().equals(record.kind)) {
            return 0;
        }
        int load = Math.max(0, Math.min(arrows, maxAmmo(record) - record.ammo));
        record.ammo += load;
        dirty |= load > 0;
        return load;
    }

    /** How much longer monsters need to dig through a block: steel turrets (crate gain) hold twice as long. */
    public double digFactor(ServerLevel level, BlockPos pos) {
        TrapKind here = ModContent.trapOf(level.getBlockState(pos));
        if (here == null || !here.isTower()) {
            return 1.0;
        }
        TrapRecord record = find(level, pos);
        return record != null && gainOf(record, "stahlturm") > 0 ? 2.0 : 1.0;
    }

    /** Gunner ultimate: this engineer's turrets fire three times as fast until the given time. */
    public void overcharge(ServerPlayer owner, long until) {
        overchargedUntil.put(owner.getUUID().toString(), until);
    }

    /** Hands an engineer their free traps for the night. */
    /** The free traps of one night for these skills (without looking at what the player already has). */
    public java.util.Map<TrapKind, Integer> freeTrapCounts(PlayerSkills skills) {
        java.util.Map<TrapKind, Integer> out = new java.util.EnumMap<>(TrapKind.class);
        for (TrapKind kind : TrapKind.values()) {
            int tier = tree.trapTier(kind, skills);
            int count = kind.freePerSiege(tier);
            if (count > 0) {
                count += (int) Math.round(tree.totalBonus(SkillEffect.FREE_TRAPS, skills));
                out.put(kind, Math.min(count, maxFor(kind, tier, skills)));
            }
        }
        return out;
    }

    public void giveFreeTraps(ServerPlayer player) {
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        int total = 0;
        for (TrapKind kind : TrapKind.values()) {
            int tier = tree.trapTier(kind, skills);
            int count = kind.freePerSiege(tier);
            if (count > 0) {
                count += (int) Math.round(tree.totalBonus(SkillEffect.FREE_TRAPS, skills));
                // Only top up to the cap: placed traps and those in the pack both count.
                net.minecraft.world.item.Item item = ModContent.TRAP_ITEMS.get(kind);
                int have = placedBy(player.getUUID().toString(), kind);
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.is(item)) {
                        have += stack.getCount();
                    }
                }
                count = Math.min(count, Math.max(0, maxFor(kind, tier, skills) - have));
            }
            if (count > 0) {
                player.getInventory().placeItemBackInInventory(new ItemStack(ModContent.TRAP_ITEMS.get(kind), count));
                total += count;
            }
        }
        if (total > 0) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.free_traps", total));
        }
        giveRemote(player, skills);
    }

    // ------------------------------------------------------------------
    // ticking
    // ------------------------------------------------------------------

    /** Returns true when the save changed and should be written. */
    public boolean tick(MinecraftServer server) {
        if (++tickCounter < TICK_INTERVAL) {
            return takeDirty();
        }
        tickCounter = 0;
        rechargeRemotes(server);
        if (++dotTick % 2 == 0) {
            tickDots(server);
        }
        List<TrapRecord> list = traps();
        if (list.isEmpty()) {
            return takeDirty();
        }
        for (TrapRecord record : new ArrayList<>(list)) {
            TrapKind kind = TrapKind.byId(record.kind);
            ServerLevel level = SiegeManager.levelByName(server, record.dimension);
            if (kind == null || level == null) {
                list.remove(record);
                dirty = true;
                continue;
            }
            BlockPos pos = new BlockPos(record.x, record.y, record.z);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (ModContent.trapOf(state) != kind) {
                // Gone by some way the break event did not see, e.g. an explosion.
                list.remove(record);
                dirty = true;
                if (gainOf(record, "recycling") > 0) {
                    recycled.add(record);   // trap recycling: it stands again after the night
                }
                continue;
            }
            long now = level.getGameTime();
            if (now < record.readyAt) {
                continue;
            }
            int tier = tierOf(record);
            switch (kind) {
                case SPIKE -> tickSpike(level, pos, record, tier, now);
                case SNARE -> tickSnare(level, pos, record, tier, now);
                case MORTAR -> tickMortar(level, pos, record, tier, now);
                case TURRET -> tickTurret(level, pos, record, tier, now);
                case FLAME -> tickFlame(level, pos, record, tier, now);
                case BREWER -> tickBrewer(level, pos, record, tier, now);
                case TESLA -> tickTesla(level, pos, record, tier, now);
            }
        }
        return takeDirty();
    }

    private void tickSpike(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        List<LivingEntity> victims = enemies(level, new AABB(pos.getX(), pos.getY() + 1, pos.getZ(),
                pos.getX() + 1, pos.getY() + 2.2, pos.getZ() + 1));
        if (victims.isEmpty()) {
            return;
        }
        DamageSource source = sourceFor(level, record);
        double power = power(record, now);
        for (LivingEntity victim : victims) {
            victim.hurtServer(level, source, (float) (TrapKind.SPIKE.damage(tier) * power * levelScale()));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TrapKind.SPIKE.holdTicks(tier), 1));
            record.uses--;
        }
        level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                8, 0.3, 0.1, 0.3, 0.1);
        playAt(level, pos, SoundEvents.SWEET_BERRY_BUSH_BREAK, 0.7f, 0.6f);
        record.readyAt = now + quicker(record, now, TrapKind.SPIKE.cooldownTicks(tier));
        if (record.uses <= 0) {
            wearOut(level, pos, record);
        }
        dirty = true;
    }

    private void tickSnare(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        double r = TrapKind.SNARE.radius(tier);
        Vec3 center = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        // It springs when a monster steps right onto it, then catches everything around.
        if (enemies(level, new AABB(pos.getX(), pos.getY() + 1, pos.getZ(),
                pos.getX() + 1, pos.getY() + 2.2, pos.getZ() + 1)).isEmpty()) {
            return;
        }
        int hold = (int) Math.round(TrapKind.SNARE.holdTicks(tier) * power(record, now));
        for (LivingEntity victim : enemies(level, new AABB(center, center).inflate(r, 2.0, r))) {
            if (victim.distanceToSqr(center) > r * r + 4.0) {
                continue;
            }
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, hold, 9));
            victim.setDeltaMovement(0.0, Math.min(0.0, victim.getDeltaMovement().y), 0.0);
            if (victim instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            level.sendParticles(ParticleTypes.ITEM_COBWEB, victim.getX(), victim.getY() + 0.6, victim.getZ(),
                    10, 0.3, 0.4, 0.3, 0.02);
        }
        level.sendParticles(ParticleTypes.ITEM_COBWEB, center.x, center.y, center.z, 30, r * 0.5, 0.3, r * 0.5, 0.02);
        playAt(level, pos, SoundEvents.COBWEB_PLACE, 1.0f, 0.7f);
        playAt(level, pos, SoundEvents.TRIPWIRE_CLICK_ON, 0.8f, 0.8f);
        record.uses--;
        record.readyAt = now + quicker(record, now, TrapKind.SNARE.cooldownTicks(tier));
        if (record.uses <= 0) {
            wearOut(level, pos, record);
        }
        dirty = true;
    }

    // ------------------------------------------------------------------
    // the chemistry towers: mortar and brewer (24 October 2026, instead of mine and poison trap)
    // ------------------------------------------------------------------

    /** One direct hit of a chemistry tower: half a normal zombie of tonight (Simon: two shots per zombie). */
    static double towerHit() {
        return 10.0 * levelScale();
    }

    /**
     * Traps and towers grow with the level like the monsters' health (config
     * trapDamageFollowsLevel), times the server's factor trapDamage.
     */
    static double levelScale() {
        SiegeManager m = SiegeManager.get();
        if (m == null || m.config() == null) {
            return 1.0;
        }
        double level = m.config().trapDamageFollowsLevel ? m.mobHealthFactor() : 1.0;
        return level * Math.max(0.05, m.config().trapDamage);
    }

    /** Crate gains that make traps stronger; the overcharge makes the towers faster instead. */
    private double towerPower(TrapRecord record) {
        double gain = 1.0 + 0.1 * gainOf(record, "verstaerkt");
        if (record.ambushNight != GainGear.nightId() && gainOf(record, "tarnfallen") > 0) {
            record.ambushNight = GainGear.nightId();
            gain *= 1.5;
        }
        return gain;
    }

    /**
     * The monster with the most others around it within reach (marked ones first), or null.
     * Only monsters the tower can see count: none behind walls, in caves or below hills
     * (Simon's playtest: the mortars kept firing at monsters they could not reach).
     */
    private LivingEntity bestGroup(ServerLevel level, TrapRecord record, Vec3 from, double range, double splash, long now,
                                   java.util.Set<LivingEntity> skip) {
        List<LivingEntity> all = enemies(level, new AABB(from, from).inflate(range, 14.0, range));
        BlockPos self = BlockPos.containing(from.x, from.y - 1.0, from.z);
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : all) {
            if (skip != null && skip.contains(e)) {
                continue;
            }
            double dx = e.getX() - from.x;
            double dz = e.getZ() - from.z;
            double flat = dx * dx + dz * dz;
            if (flat > range * range || flat < 4.0) {
                continue;   // too far, or right at the tower's foot
            }
            int around = 0;
            for (LivingEntity o : all) {
                if (o.distanceToSqr(e) <= splash * splash) {
                    around++;
                }
            }
            double score = around * 10.0 + (markedBy(record, e, now) ? 25.0 : 0.0) - Math.sqrt(flat) * 0.1;
            if (score > bestScore && sees(level, from, e, self)) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private void tickMortar(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.9, 0);
        double range = TrapKind.MORTAR.radius(tier) + bonus(record, SkillEffect.TOWER_RANGE);
        LivingEntity target = bestGroup(level, record, muzzle, range, TrapKind.MORTAR.splash(tier), now, null);
        if (target == null) {
            record.readyAt = now + 10;   // nothing in sight: look again in half a second
            return;
        }
        lob(level, record, muzzle, lead(muzzle, target), KitProjectile.SHELL, tier, 1.0, 1.0);
        record.readyAt = now + quicker(record, now, TrapKind.MORTAR.cooldownTicks(tier));
        dirty = true;
    }

    private void tickBrewer(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.9, 0);
        double range = TrapKind.BREWER.radius(tier) + bonus(record, SkillEffect.TOWER_RANGE);
        LivingEntity target = bestGroup(level, record, muzzle, range, TrapKind.BREWER.splash(tier), now, null);
        if (target == null) {
            record.readyAt = now + 10;   // nothing in sight: look again in half a second
            return;
        }
        lob(level, record, muzzle, lead(muzzle, target), KitProjectile.BREW, tier, 1.0, 1.0);
        record.readyAt = now + quicker(record, now, TrapKind.BREWER.cooldownTicks(tier));
        dirty = true;
    }

    /** Fires a shell or a potion in a high arc (over walls) onto a spot. */
    private void lob(ServerLevel level, TrapRecord record, Vec3 from, Vec3 to, String kind, int tier,
                     double damageScale, double radiusScale) {
        boolean shell = KitProjectile.SHELL.equals(kind);
        ItemStack look = shell ? new ItemStack(Items.FIRE_CHARGE)
                : PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON);
        KitProjectile shot = new KitProjectile(level, from.x, from.y, from.z, look, kind);
        shot.setData(String.join("|", record.kind, record.dimension, String.valueOf(record.x), String.valueOf(record.y),
                String.valueOf(record.z), record.owner == null ? "" : record.owner, String.valueOf(tier),
                String.valueOf(damageScale), String.valueOf(radiusScale)));
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        int flight = flightTicks(Math.sqrt(dx * dx + dz * dz));
        shot.setDeltaMovement(ballistic(dx, dy, dz, flight, 0.03));
        level.addFreshEntity(shot);
        BlockPos pos = new BlockPos(record.x, record.y, record.z);
        if (shell) {
            playAt(level, pos, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.8f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + 0.2, from.z, 6, 0.15, 0.1, 0.15, 0.03);
        } else {
            playAt(level, pos, SoundEvents.SPLASH_POTION_THROW, 0.9f, 0.8f);
            level.sendParticles(POISON_DUST, from.x, from.y + 0.2, from.z, 8, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /**
     * Start velocity that lands a thrown projectile {@code t} ticks later at the offset, with
     * the per-tick rule of Minecraft's throwables (gravity first, then 1 % drag, then the move).
     */
    static Vec3 ballistic(double dx, double dy, double dz, int t, double g) {
        double sum = 0;
        double f = 0.99;
        for (int k = 0; k < t; k++) {
            sum += f;
            f *= 0.99;
        }
        double lo = -3;
        double hi = 6;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (rise(mid, t, g) < dy) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return new Vec3(dx / sum, (lo + hi) / 2, dz / sum);
    }

    static double rise(double vy, int t, double g) {
        double y = 0;
        for (int k = 0; k < t; k++) {
            vy = (vy - g) * 0.99;
            y += vy;
        }
        return y;
    }

    /** A tower shot landed (called by the projectile). */
    public void onTowerShot(ServerLevel level, KitProjectile shot, Entity direct, Vec3 at) {
        String[] d = shot.data().split("\\|", -1);
        if (d.length < 9) {
            return;
        }
        TrapRecord record = null;
        try {
            int x = Integer.parseInt(d[2]);
            int y = Integer.parseInt(d[3]);
            int z = Integer.parseInt(d[4]);
            for (TrapRecord r : traps()) {
                if (r.matches(d[1], x, y, z)) {
                    record = r;
                    break;
                }
            }
            if (record == null) {
                record = new TrapRecord(d[0], d[1], x, y, z, d[5]);   // the tower is gone, its shot still counts
            }
            int tier = Integer.parseInt(d[6]);
            double damageScale = Double.parseDouble(d[7]);
            double radiusScale = Double.parseDouble(d[8]);
            if (KitProjectile.SHELL.equals(shot.kind())) {
                shellBurst(level, record, at, direct, tier, damageScale, radiusScale, false);
            } else {
                brewBurst(level, record, at, direct, tier, damageScale, radiusScale, damageScale > 1.0 || radiusScale > 1.0);
            }
        } catch (NumberFormatException e) {
            // a broken shot does nothing
        }
    }

    /** Full damage within a block of the impact (and on the monster it hit), half at the edge. */
    private static double falloff(LivingEntity victim, Vec3 at, Entity direct, double r) {
        if (victim == direct) {
            return 1.0;
        }
        double dx = victim.getX() - at.x;
        double dz = victim.getZ() - at.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        return d <= 1.0 ? 1.0 : 1.0 - 0.5 * Math.min(1.0, (d - 1.0) / Math.max(0.5, r - 1.0));
    }

    private void shellBurst(ServerLevel level, TrapRecord record, Vec3 at, Entity direct, int tier,
                            double damageScale, double radiusScale, boolean small) {
        double shrapnel = bonus(record, SkillEffect.SHRAPNEL_MINE);
        double r = TrapKind.MORTAR.splash(tier) * (1.0 + shrapnel) * radiusScale * (small ? 0.6 : 1.0);
        double damage = towerHit() * towerPower(record) * blastScale(record) * damageScale * (small ? 1.0 / 3.0 : 1.0);
        DamageSource source = sourceFor(level, record);
        for (LivingEntity victim : around(level, at, r)) {
            double f = falloff(victim, at, direct, r);
            victim.invulnerableTime = 0;
            victim.hurtServer(level, source, (float) (damage * f));
            Vec3 away = victim.position().subtract(at);
            Vec3 push = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0.3, 0) : away.normalize().scale(0.4 * f).add(0, 0.2, 0);
            victim.push(push.x, push.y, push.z);
        }
        level.sendParticles(small ? ParticleTypes.SMOKE : ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, small ? 8 : 2,
                r * 0.3, 0.2, r * 0.3, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, small ? 4 : 14, r * 0.35, 0.3, r * 0.35, 0.03);
        if (shrapnel > 0 && !small) {
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.3, at.z, 24, r * 0.4, 0.3, r * 0.4, 0.4);
        }
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                small ? 0.5f : 0.9f, small ? 1.6f : 1.2f);
        // Scatter fire: the shell bursts into three small ones around the impact.
        if (!small && has(record, "e_chain_reaction")) {
            for (int i = 0; i < 3; i++) {
                double angle = Math.PI * 2 * i / 3 + level.getRandom().nextDouble();
                Vec3 spot = at.add(Math.cos(angle) * 2.2, 0, Math.sin(angle) * 2.2);
                shellBurst(level, record, spot, null, tier, damageScale, radiusScale, true);
            }
        }
    }

    /** Poison over time of the brewer, per monster: what is still to come, in hits of half a second. */
    private static final class Dot {
        final String owner;
        final String dimension;
        final double perHit;
        int hitsLeft;

        Dot(String owner, String dimension, double perHit, int hitsLeft) {
            this.owner = owner;
            this.dimension = dimension;
            this.perHit = perHit;
            this.hitsLeft = hitsLeft;
        }
    }

    private final Map<UUID, List<Dot>> dots = new HashMap<>();
    private int dotTick;

    /** True while the brewer's poison eats at this monster. */
    public boolean poisoned(LivingEntity e) {
        List<Dot> list = dots.get(e.getUUID());
        return list != null && !list.isEmpty();
    }

    private void brewBurst(ServerLevel level, TrapRecord record, Vec3 at, Entity direct, int tier,
                           double damageScale, double radiusScale, boolean big) {
        long now = level.getGameTime();
        double r = TrapKind.BREWER.splash(tier) * radiusScale;
        int ticks = (int) Math.round(TrapKind.BREWER.holdTicks(tier) * poisonScale(record));
        int hits = Math.max(1, ticks / 10);
        double total = towerHit() * towerPower(record) * damageScale * poisonScale(record);
        double acid = bonus(record, SkillEffect.ACID);
        int amp = TrapKind.BREWER.amplifier(tier) + (acid > 0 ? 1 : 0);
        for (LivingEntity victim : around(level, at, r)) {
            double f = falloff(victim, at, direct, r);
            dots.computeIfAbsent(victim.getUUID(), k -> new ArrayList<>())
                    .add(new Dot(record.owner, record.dimension, total * f / hits, hits));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amp));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0));
            if (acid > 0) {
                marker.mark(victim, acid, now + ticks, false);
            }
        }
        level.sendParticles(POISON_DUST, at.x, at.y + 0.4, at.z, 50, r * 0.45, 0.4, r * 0.45, 0.0);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.3, at.z, 20, r * 0.3, 0.1, r * 0.3, 0.1);
        level.playSound(null, BlockPos.containing(at), SoundEvents.SPLASH_POTION_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        // The plague throw (remote, with the skill): the big potion also bursts.
        double plague = bonus(record, SkillEffect.PLAGUE_MINE);
        if (big && plague > 0) {
            DamageSource source = sourceFor(level, record);
            for (LivingEntity victim : around(level, at, r)) {
                victim.invulnerableTime = 0;
                victim.hurtServer(level, source, (float) (plague * damageScale));
            }
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.5, 0.2, 0.5, 0.0);
        }
    }

    /** Every half second: the brewer's poison bites. */
    private void tickDots(MinecraftServer server) {
        if (dots.isEmpty()) {
            return;
        }
        java.util.Iterator<Map.Entry<UUID, List<Dot>>> it = dots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<Dot>> entry = it.next();
            List<Dot> list = entry.getValue();
            Dot first = list.isEmpty() ? null : list.get(0);
            ServerLevel level = first == null ? null : SiegeManager.levelByName(server, first.dimension);
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity victim) || !victim.isAlive()) {
                it.remove();
                continue;
            }
            double sum = 0;
            String owner = first.owner;
            for (Dot dot : list) {
                sum += dot.perHit;
                dot.hitsLeft--;
            }
            list.removeIf(dot -> dot.hitsLeft <= 0);
            TrapRecord who = new TrapRecord(TrapKind.BREWER.id(), first.dimension, 0, 0, 0, owner);
            victim.invulnerableTime = 0;
            victim.hurtServer(level, sourceFor(level, who), (float) sum);
            level.sendParticles(POISON_DUST, victim.getX(), victim.getY(0.6), victim.getZ(), 4, 0.25, 0.3, 0.25, 0.0);
            if (list.isEmpty()) {
                it.remove();
            }
        }
    }

    private void tickTurret(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        if (record.ammo <= 0) {
            return;
        }
        Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.75, 0);
        double range = TrapKind.TURRET.radius(tier) + bonus(record, SkillEffect.TOWER_RANGE);
        LivingEntity target = enemies(level, new AABB(muzzle, muzzle).inflate(range)).stream()
                .filter(e -> e.distanceToSqr(muzzle) <= range * range)
                .filter(e -> clearShot(level, muzzle, e.getEyePosition(), pos))
                .min(Comparator.comparingDouble(e -> (markedBy(record, e, now) ? 0.0 : 10000.0) + e.distanceToSqr(muzzle)))
                .orElse(null);
        if (target == null) {
            return;
        }
        Arrow arrow = new Arrow(level, muzzle.x, muzzle.y, muzzle.z, new ItemStack(Items.ARROW), null);
        arrow.entityTags().add("nightsiege_arrow");   // keeps the archer skills off it
        arrow.entityTags().add("nightsiege_turret");  // and the crate gains (the archer tag is on every arrow)
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(TrapKind.TURRET.damage(tier) * power(record, now) * piercing(record, now) * levelScale());
        if (bonus(record, SkillEffect.CHARGED_ARROWS) > 0) {
            arrow.entityTags().add(TAG_CHARGED);
        }
        if (bonus(record, SkillEffect.TARGET_BLAST) > 0) {
            arrow.entityTags().add(TAG_BLAST);
        }
        ServerPlayer owner = ownerOf(level, record);
        if (owner != null) {
            arrow.setOwner(owner);   // so kills earn the engineer their points
        }
        double dx = target.getX() - muzzle.x;
        double dz = target.getZ() - muzzle.z;
        double dy = target.getY(0.5) - muzzle.y;
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.12, dz, 2.4f, 1.5f);
        level.addFreshEntity(arrow);
        playAt(level, pos, SoundEvents.DISPENSER_LAUNCH, 0.8f, 1.3f);

        record.ammo--;
        int cooldown = quicker(record, now, TrapKind.TURRET.cooldownTicks(tier));
        Long boosted = overchargedUntil.get(record.owner);
        if (boosted != null && now <= boosted) {
            cooldown = Math.max(3, cooldown / 3);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 3, 0.2, 0.2, 0.2, 0.05);
        }
        record.readyAt = now + cooldown;
        dirty = true;
    }

    /** Monsters standing right on top of a floor plate. */
    private static List<LivingEntity> onTop(ServerLevel level, BlockPos pos) {
        return enemies(level, new AABB(pos.getX(), pos.getY() + 1, pos.getZ(),
                pos.getX() + 1, pos.getY() + 2.2, pos.getZ() + 1));
    }

    private static List<LivingEntity> around(ServerLevel level, Vec3 center, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : enemies(level, new AABB(center, center).inflate(r, 2.0, r))) {
            if (e.distanceToSqr(center) <= r * r + 4.0) {
                out.add(e);
            }
        }
        return out;
    }

    private void tickFlame(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        if (onTop(level, pos).isEmpty()) {
            return;
        }
        fireFlame(level, pos, record, tier, now, 1.0, 1.0);
    }

    private void fireFlame(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now,
                           double damageScale, double radiusScale) {
        Vec3 center = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        DamageSource source = sourceFor(level, record);
        boolean napalm = has(record, "e_napalm");
        double accelerant = bonus(record, SkillEffect.ACCELERANT);
        float seconds = TrapKind.FLAME.holdTicks(tier) / 20f * (napalm ? 2f : 1f);
        double r = (TrapKind.FLAME.radius(tier) + (napalm ? 1.0 : 0.0)) * radiusScale;
        double damage = TrapKind.FLAME.damage(tier) * power(record, now) * blastScale(record) * damageScale * levelScale();
        for (LivingEntity victim : around(level, center, r)) {
            victim.igniteForSeconds(seconds);
            boolean poisoned = victim.hasEffect(MobEffects.POISON) || victim.hasEffect(MobEffects.WITHER) || poisoned(victim);
            victim.hurtServer(level, source, (float) (damage * (poisoned ? 1.0 + accelerant : 1.0)));
        }
        // Only particles: the trap never places fire blocks.
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 40, r * 0.5, 0.4, r * 0.5, 0.03);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 6, 0.4, 0.2, 0.4, 0.0);
        if (napalm) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z, 20, r * 0.5, 0.3, r * 0.5, 0.02);
        }
        playAt(level, pos, SoundEvents.FIRECHARGE_USE, 0.9f, 0.8f);
        record.uses--;
        record.readyAt = now + quicker(record, now, TrapKind.FLAME.cooldownTicks(tier));
        if (record.uses <= 0) {
            wearOut(level, pos, record);
        }
        dirty = true;
    }

    static final net.minecraft.core.particles.DustParticleOptions POISON_DUST =
            new net.minecraft.core.particles.DustParticleOptions(0x6FB33A, 1.5f);

    private void tickTesla(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        Vec3 from = Vec3.atCenterOf(pos).add(0, 0.8, 0);
        double range = TrapKind.TESLA.radius(tier) + bonus(record, SkillEffect.TOWER_RANGE);
        boolean beam = bonus(record, SkillEffect.GUIDING_BEAM) > 0;
        LivingEntity first = enemies(level, new AABB(from, from).inflate(range)).stream()
                .filter(e -> e.distanceToSqr(from) <= range * range)
                .filter(e -> clearShot(level, from, e.getEyePosition(), pos))
                .min(Comparator.comparingDouble(e -> (markedBy(record, e, now) ? 0.0 : 10000.0) + e.distanceToSqr(from)))
                .orElse(null);
        if (first == null) {
            return;
        }
        DamageSource source = sourceFor(level, record);
        float damage = (float) (TrapKind.TESLA.damage(tier) * teslaBoost.getAsDouble() * power(record, now)
                * piercing(record, now) * levelScale());
        int jumps = TrapKind.TESLA.chainTargets(tier) + (int) Math.round(bonus(record, SkillEffect.CHAIN_LIGHTNING));
        List<LivingEntity> hit = new ArrayList<>();
        LivingEntity current = first;
        Vec3 start = from;
        while (current != null && hit.size() < jumps) {
            hit.add(current);
            Vec3 end = current.position().add(0, current.getBbHeight() * 0.5, 0);
            spark(level, start, end);
            current.hurtServer(level, source, damage);
            start = end;
            // Jump to the nearest monster within four blocks not hit yet.
            Vec3 at = end;
            current = enemies(level, new AABB(at, at).inflate(4.0)).stream()
                    .filter(e -> !hit.contains(e))
                    .min(Comparator.comparingDouble(e -> (beam && markedBy(record, e, now) ? 0.0 : 100.0) + e.distanceToSqr(at)))
                    .orElse(null);
        }
        playAt(level, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
        record.readyAt = now + quicker(record, now, TrapKind.TESLA.cooldownTicks(tier));
    }

    /** A crackling line of sparks between two points. */
    static void spark(ServerLevel level, Vec3 a, Vec3 b) {
        Vec3 step = b.subtract(a);
        int n = Math.max(2, (int) (step.length() * 3));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(step.scale(i / (double) n));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    // ------------------------------------------------------------------
    // the remote
    // ------------------------------------------------------------------

    static final String TAG_CHARGED = "nightsiege_charged";
    static final String TAG_BLAST = "nightsiege_blast";

    /** The engineer's remote was used, pointing at a block (null: at whatever is in sight). */
    public void useRemote(ServerPlayer player, BlockPos clicked) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        boolean creative = player.getAbilities().instabuild;
        if (skills.clazz() != SkillClass.ENGINEER && !creative) {
            actionBar(player, Component.translatable("nightsiege.msg.remote_engineer_only"));
            return;
        }
        long now = level.getGameTime();
        double range = REMOTE_RANGE + tree.totalBonus(SkillEffect.REMOTE_RANGE, skills);
        BlockPos trapPos = clicked;
        if (trapPos == null || ModContent.trapOf(level.getBlockState(trapPos)) == null) {
            trapPos = null;
            net.minecraft.world.phys.HitResult hit = player.pick(range, 0f, false);
            if (hit instanceof net.minecraft.world.phys.BlockHitResult block
                    && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                BlockPos at = block.getBlockPos();
                if (ModContent.trapOf(level.getBlockState(at)) != null) {
                    trapPos = at;
                } else if (ModContent.trapOf(level.getBlockState(at.above())) != null) {
                    trapPos = at.above();
                }
            }
        }
        LivingEntity monster = CombatEffects.lookTarget(level, player, range);
        boolean sneak = player.isShiftKeyDown();
        if (trapPos != null && (monster == null
                || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(trapPos)) <= monster.distanceToSqr(player))) {
            TrapRecord record = find(level, trapPos);
            if (record == null || (!record.owner.equals(player.getUUID().toString()) && !creative)) {
                actionBar(player, Component.translatable("nightsiege.msg.remote_not_yours"));
                return;
            }
            TrapKind kind = TrapKind.byId(record.kind);
            if (sneak && (kind == TrapKind.MORTAR || kind == TrapKind.FLAME || kind == TrapKind.BREWER)) {
                double cost = cost(skills, COST_DETONATE - 4.0 * tree.totalBonus(SkillEffect.DETONATOR, skills));
                if (pay(player, skills, cost)) {
                    detonate(level, trapPos, record, kind, skills);
                    report(player, skills, "nightsiege.msg.remote_detonated");
                }
            } else if (sneak) {
                double cost = cost(skills, kind == TrapKind.TURRET && tree.totalBonus(SkillEffect.AMMO_BELT, skills) > 0
                        ? COST_RELOAD / 2 : COST_RELOAD);
                if (!canReload(record, kind)) {
                    actionBar(player, Component.translatable("nightsiege.msg.remote_full"));
                } else if (pay(player, skills, cost)) {
                    reload(level, trapPos, record, kind, player);
                    report(player, skills, "nightsiege.msg.remote_reloaded");
                }
            } else if (pay(player, skills, overchargeCost(skills, kind))) {
                boost(level, record, skills, now, 1.0);
                if (tree.totalBonus(SkillEffect.NETWORK, skills) > 0) {
                    Vec3 c = Vec3.atCenterOf(trapPos);
                    for (TrapRecord other : traps()) {
                        if (other != record && other.owner.equals(record.owner) && other.dimension.equals(record.dimension)
                                && Vec3.atCenterOf(new BlockPos(other.x, other.y, other.z)).distanceToSqr(c) <= 36.0) {
                            boost(level, other, skills, now, 0.5);
                        }
                    }
                }
                report(player, skills, "nightsiege.msg.remote_overcharged");
            }
            return;
        }
        if (monster != null && !sneak) {
            if (pay(player, skills, cost(skills, COST_MARK))) {
                double bonus = 0.15 + tree.totalBonus(SkillEffect.SEEKER, skills);
                long until = now + 160;
                remoteMarks.put(monster.getUUID(), new RemoteMark(player.getUUID().toString(), until));
                marker.mark(monster, bonus, until, true);
                spark(level, player.getEyePosition().subtract(0, 0.2, 0), monster.position().add(0, monster.getBbHeight() * 0.6, 0));
                level.playSound(null, monster.getX(), monster.getY(), monster.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(),
                        SoundSource.PLAYERS, 0.8f, 1.8f);
                report(player, skills, "nightsiege.msg.remote_marked");
            }
            return;
        }
        actionBar(player, Component.translatable("nightsiege.msg.remote_no_target"));
    }

    private void boost(ServerLevel level, TrapRecord record, PlayerSkills skills, long now, double share) {
        long length = Math.round((100 + tree.totalBonus(SkillEffect.OVERCHARGE_TIME, skills)) * share);
        boostedUntil.merge(key(record), now + length, Math::max);
        record.readyAt = Math.min(record.readyAt, now);
        BlockPos pos = new BlockPos(record.x, record.y, record.z);
        Vec3 c = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 0.4, 0.4, 0.4, 0.1);
        playAt(level, pos, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.8f);
    }

    /** Sets a floor trap off from afar, stronger with the detonator. */
    private void detonate(ServerLevel level, BlockPos pos, TrapRecord record, TrapKind kind, PlayerSkills skills) {
        int tier = tierOf(record);
        long now = level.getGameTime();
        double damage = 1.0 + 0.15 * tree.totalBonus(SkillEffect.DETONATOR, skills);
        double radius = 1.0 + tree.totalBonus(SkillEffect.BLAST_POWER, skills);
        switch (kind) {
            case MORTAR -> {
                // The salvo: three shells at once onto the three thickest groups.
                Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.9, 0);
                double range = TrapKind.MORTAR.radius(tier) + bonus(record, SkillEffect.TOWER_RANGE);
                java.util.Set<LivingEntity> taken = new java.util.HashSet<>();
                for (int i = 0; i < 3; i++) {
                    LivingEntity target = bestGroup(level, record, muzzle, range, TrapKind.MORTAR.splash(tier), now, taken);
                    if (target == null) {
                        break;
                    }
                    taken.add(target);
                    lob(level, record, muzzle, lead(muzzle, target), KitProjectile.SHELL, tier, damage, radius);
                }
            }
            case FLAME -> fireFlame(level, pos, record, tier, now, damage, radius);
            case BREWER -> {
                // The big throw: half as wide again (and with the plague skill it bursts).
                Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.9, 0);
                double range = TrapKind.BREWER.radius(tier) + bonus(record, SkillEffect.TOWER_RANGE);
                LivingEntity target = bestGroup(level, record, muzzle, range, TrapKind.BREWER.splash(tier), now, null);
                if (target != null) {
                    lob(level, record, muzzle, lead(muzzle, target), KitProjectile.BREW, tier, damage, radius * 1.5);
                }
            }
            default -> {
            }
        }
    }

    private boolean canReload(TrapRecord record, TrapKind kind) {
        if (kind == TrapKind.TURRET) {
            return record.ammo < maxAmmo(record);
        }
        return kind.isFloorPlate() && record.uses < fullUses(kind, tierOf(record), record);
    }

    /** Fills a trap up again; a turret takes the arrows from the engineer's pack. */
    private void reload(ServerLevel level, BlockPos pos, TrapRecord record, TrapKind kind, ServerPlayer player) {
        if (kind == TrapKind.TURRET) {
            int max = maxAmmo(record);
            if (player.getAbilities().instabuild) {
                record.ammo = max;
            } else {
                for (int i = 0; i < player.getInventory().getContainerSize() && record.ammo < max; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.is(Items.ARROW)) {
                        int load = Math.min(stack.getCount(), max - record.ammo);
                        record.ammo += load;
                        stack.shrink(load);
                    }
                }
                record.ammo = Math.max(record.ammo, Math.min(max, record.ammo + 16));   // a few spare bolts of its own
            }
        } else {
            record.uses = fullUses(kind, tierOf(record), record);
        }
        dirty = true;
        Vec3 c = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        level.sendParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, 12, 0.4, 0.3, 0.4, 0.0);
        playAt(level, pos, SoundEvents.CHAIN_PLACE, 0.9f, 1.4f);
    }

    /** A charged or blasting turret arrow hit a monster. */
    public void onTurretArrowHit(ServerLevel level, LivingEntity victim, AbstractArrow arrow, ServerPlayer owner, float taken) {
        DamageSource source = level.damageSources().playerAttack(owner);
        PlayerSkills skills = save.get().skillsOf(owner.getUUID().toString());
        if (arrow.entityTags().contains(TAG_CHARGED)) {
            Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
            LivingEntity next = enemies(level, new AABB(at, at).inflate(4.0)).stream()
                    .filter(e -> e != victim)
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(at)))
                    .orElse(null);
            if (next != null) {
                spark(level, at, next.position().add(0, next.getBbHeight() * 0.5, 0));
                next.invulnerableTime = 0;
                next.hurtServer(level, source, (float) Math.max(1.0, taken * tree.totalBonus(SkillEffect.CHARGED_ARROWS, skills)));
            }
        }
        RemoteMark mark = remoteMarks.get(victim.getUUID());
        if (arrow.entityTags().contains(TAG_BLAST) && mark != null && level.getGameTime() <= mark.until()) {
            Vec3 at = victim.position();
            float damage = (float) tree.totalBonus(SkillEffect.TARGET_BLAST, skills);
            for (LivingEntity near : enemies(level, new AABB(at, at).inflate(2.0, 1.5, 2.0))) {
                near.invulnerableTime = 0;
                near.hurtServer(level, source, damage);
            }
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.6f);
        }
    }

    /** Fills a remote to the top (the superconductor). */
    public void fillRemote(ServerPlayer player) {
        energy.put(player.getUUID(), maxEnergy(save.get().skillsOf(player.getUUID().toString())));
        showEnergy(player);
    }

    public double energyOf(ServerPlayer player) {
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        double max = maxEnergy(skills);
        double now = energy.computeIfAbsent(player.getUUID(), k -> max);
        if (now > max) {
            // The battery was unlearned (a reset): never more than the remote holds.
            energy.put(player.getUUID(), max);
            return max;
        }
        return now;
    }

    private double maxEnergy(PlayerSkills skills) {
        double max = REMOTE_ENERGY + (skills == null ? 0 : tree.totalBonus(SkillEffect.REMOTE_ENERGY, skills));
        SiegeManager m = SiegeManager.get();
        if (skills != null && m != null
                && m.kits().gainLevel(skills, com.mki.nightsiege.core.SkillClass.ENGINEER, "zweite_ladung") > 0) {
            max *= 1.5;   // crate gain: a second charge in the remote
        }
        return max;
    }

    /**
     * What overcharging this trap costs: a share of the full remote for the tesla coil and
     * the arrow turret (field technician and fortress builder still count, never more than
     * the remote holds), the old flat price for every other trap.
     */
    public double overchargeCost(PlayerSkills skills, TrapKind kind) {
        double share = kind == TrapKind.TESLA ? OVERCHARGE_TESLA_SHARE
                : kind == TrapKind.TURRET ? OVERCHARGE_TURRET_SHARE : 0.0;
        if (share <= 0) {
            return cost(skills, COST_OVERCHARGE);
        }
        double max = maxEnergy(skills);
        return Math.min(max, cost(skills, share * max));
    }

    private double cost(PlayerSkills skills, double base) {
        double scale = 1.0;
        if (SkillTree.has(skills, "e_fortress")) {
            scale *= 1.3;
        }
        if (SkillTree.has(skills, "e_fieldtech")) {
            scale *= 0.6;
        }
        return Math.max(1.0, base * scale);
    }

    private boolean pay(ServerPlayer player, PlayerSkills skills, double cost) {
        double have = energyOf(player);
        if (player.getAbilities().instabuild) {
            return true;
        }
        if (have + 1e-6 < cost) {
            actionBar(player, Component.translatable("nightsiege.msg.remote_low",
                    (int) Math.floor(have), (int) Math.ceil(cost)));
            return false;
        }
        energy.put(player.getUUID(), Math.max(0.0, have - cost));
        showEnergy(player);
        return true;
    }

    private void report(ServerPlayer player, PlayerSkills skills, String key) {
        actionBar(player, Component.translatable(key).append(Component.literal("  ⚡ ")).append(Component.literal(
                (int) Math.floor(energyOf(player)) + "/" + (int) Math.round(maxEnergy(skills)))));
    }

    /** Every remote fills up again by itself, faster with skills; the bar on the item shows how full. */
    private void rechargeRemotes(MinecraftServer server) {
        remoteMarks.values().removeIf(m -> m.until() < server.overworld().getGameTime());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
            if (skills.clazz() != SkillClass.ENGINEER) {
                continue;
            }
            double max = maxEnergy(skills);
            double now = energyOf(player);
            if (now < max) {
                double rate = 1.25 * (1.0 + tree.totalBonus(SkillEffect.RECHARGE, skills));
                if (now < max * 0.3 && tree.totalBonus(SkillEffect.BACKUP_POWER, skills) > 0) {
                    rate *= 2.0;
                }
                energy.put(player.getUUID(), Math.min(max, now + rate));
            }
            if (++barCounter % 4 == 0) {
                showEnergy(player);
            }
        }
    }

    private int barCounter;

    private void showEnergy(ServerPlayer player) {
        if (ModContent.REMOTE == null) {
            return;
        }
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        double max = maxEnergy(skills);
        int damage = (int) Math.round(100.0 * (1.0 - Math.min(max, energyOf(player)) / max));
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModContent.REMOTE) && stack.getDamageValue() != damage) {
                stack.setDamageValue(damage);
            }
        }
    }

    /** Every engineer carries a remote; a lost one comes back with the free traps. */
    public void giveRemote(ServerPlayer player, PlayerSkills skills) {
        if (ModContent.REMOTE == null || skills == null || skills.clazz() != SkillClass.ENGINEER) {
            return;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModContent.REMOTE)) {
                return;
            }
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(ModContent.REMOTE));
        player.sendSystemMessage(Component.translatable("nightsiege.msg.remote_given"));
    }

    // ------------------------------------------------------------------
    // trap strength from the owner's skills
    // ------------------------------------------------------------------

    private PlayerSkills ownerSkills(TrapRecord record) {
        return record.owner == null || record.owner.isEmpty() ? null : save.get().skillsOf(record.owner);
    }

    private double bonus(TrapRecord record, SkillEffect effect) {
        PlayerSkills skills = ownerSkills(record);
        return skills == null ? 0.0 : tree.totalBonus(effect, skills);
    }

    private boolean has(TrapRecord record, String id) {
        return SkillTree.has(ownerSkills(record), id);
    }

    private static String key(TrapRecord record) {
        return record.dimension + ":" + record.x + ":" + record.y + ":" + record.z;
    }

    private boolean boosted(TrapRecord record, long now) {
        Long until = boostedUntil.get(key(record));
        if (until == null) {
            return false;
        }
        if (now > until) {
            boostedUntil.remove(key(record));
            return false;
        }
        return true;
    }

    /** Damage factor: doubled while overcharged by the remote, tripled with overvolt. */
    private double power(TrapRecord record, long now) {
        double gain = 1.0 + 0.1 * gainOf(record, "verstaerkt");   // crate gain: stronger traps in the night
        if (record.ambushNight != GainGear.nightId() && gainOf(record, "tarnfallen") > 0) {
            record.ambushNight = GainGear.nightId();   // hidden traps: the first strike of the night hits harder
            gain *= 1.5;
        }
        if (!boosted(record, now)) {
            return gain;
        }
        return (bonus(record, SkillEffect.OVERVOLT) > 0 ? 3.0 : 2.0) * gain;
    }

    /** A crate gain of the trap's owner, only while a night runs. */
    private int gainOf(TrapRecord record, String gainId) {
        SiegeManager m = SiegeManager.get();
        if (m == null || !m.isActive()) {
            return 0;
        }
        PlayerSkills skills = ownerSkills(record);
        return skills == null ? 0 : m.kits().gainLevel(skills, com.mki.nightsiege.core.SkillClass.ENGINEER, gainId);
    }

    /** Cooldowns halve while overcharged. */
    private int quicker(TrapRecord record, long now, int ticks) {
        if (gainOf(record, "werkzeugtasche") > 0) {
            ticks = Math.max(3, (int) Math.round(ticks * 0.7));   // tool bag: traps re-arm sooner
        }
        return boosted(record, now) ? Math.max(3, ticks / 2) : ticks;
    }

    /** The armour piercer: turret and tesla hit harder while the ultimate overcharge runs. */
    private double piercing(TrapRecord record, long now) {
        Long until = overchargedUntil.get(record.owner);
        return until != null && now <= until ? 1.0 + bonus(record, SkillEffect.ARMOR_PIERCER) : 1.0;
    }

    /** Fire and blasts: stronger for the pyromaniac, weaker for the plague doctor. */
    private double blastScale(TrapRecord record) {
        double scale = 1.0;
        if (has(record, "e_pyro")) {
            scale *= 1.0 + bonus(record, SkillEffect.PYROMANIAC);
        }
        if (has(record, "e_plague_doctor")) {
            scale *= 0.7;
        }
        return scale;
    }

    private double poisonScale(TrapRecord record) {
        return (1.0 + bonus(record, SkillEffect.SLOW_POISON)) * (has(record, "e_plague_doctor") ? 2.0 : 1.0);
    }

    private boolean markedBy(TrapRecord record, LivingEntity e, long now) {
        RemoteMark mark = remoteMarks.get(e.getUUID());
        return mark != null && now <= mark.until() && mark.owner().equals(record.owner);
    }

    private int fullUses(TrapKind kind, int tier, TrapRecord record) {
        int base = kind.uses(tier);
        if (base == Integer.MAX_VALUE || base <= 1) {
            return base;
        }
        double scale = 1.0 + bonus(record, SkillEffect.TRAP_DURABILITY);
        if (has(record, "e_pyro")) {
            scale *= 0.7;
        }
        return Math.max(1, (int) Math.round(base * scale));
    }

    private int maxAmmo(TrapRecord record) {
        int max = TrapKind.TURRET.maxAmmo(tierOf(record));
        return bonus(record, SkillEffect.AMMO_BELT) > 0 ? max * 2 : max;
    }

    /** Most traps of a kind: the fortress builder has one tower more, the field technician one fewer. */
    public int maxFor(TrapKind kind, int tier, PlayerSkills skills) {
        int max = kind.maxPlaced(tier);
        SiegeManager m = SiegeManager.get();
        if (m != null && skills != null
                && m.kits().gainLevel(skills, com.mki.nightsiege.core.SkillClass.ENGINEER, "ueberkapazitaet") > 0) {
            max += 1;   // crate gain: one more of every kind
        }
        if (kind == TrapKind.TURRET || kind == TrapKind.TESLA) {
            if (SkillTree.has(skills, "e_fortress")) {
                max += 1;
            }
            if (SkillTree.has(skills, "e_fieldtech")) {
                max -= 1;
            }
        }
        return Math.max(1, max);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private List<TrapRecord> traps() {
        if (save.get().traps == null) {
            save.get().traps = new ArrayList<>();
        }
        return save.get().traps;
    }

    private TrapRecord find(ServerLevel level, BlockPos pos) {
        String dimension = SiegeManager.dimensionName(level);
        for (TrapRecord record : traps()) {
            if (record.matches(dimension, pos.getX(), pos.getY(), pos.getZ())) {
                return record;
            }
        }
        return null;
    }

    /** The owner's current tier. Traps from a player who lost the skill keep working at tier 1. */
    private int tierOf(TrapRecord record) {
        TrapKind kind = TrapKind.byId(record.kind);
        if (record.owner == null || record.owner.isEmpty() || kind == null) {
            return 1;
        }
        return Math.max(1, tree.trapTier(kind, save.get().skillsOf(record.owner)));
    }

    private static ServerPlayer ownerOf(ServerLevel level, TrapRecord record) {
        if (record.owner == null || record.owner.isEmpty()) {
            return null;
        }
        try {
            return level.getServer().getPlayerList().getPlayer(UUID.fromString(record.owner));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Damage counts as the owner's own when they are online, so kills reward them. */
    private static DamageSource sourceFor(ServerLevel level, TrapRecord record) {
        ServerPlayer owner = ownerOf(level, record);
        return owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().cactus();
    }

    static List<LivingEntity> enemies(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive()
                && !Companions.isCompanion(e));   // the summoner's skeleton is no foe
    }

    /** True when the tower sees the monster: its eyes or the middle of its body. */
    private static boolean sees(ServerLevel level, Vec3 from, LivingEntity e, BlockPos self) {
        return clearShot(level, from, e.getEyePosition(), self)
                || clearShot(level, from, e.position().add(0, e.getBbHeight() * 0.5, 0), self);
    }

    /** Ticks a lobbed shell or potion flies over this flat distance. */
    static int flightTicks(double flat) {
        return (int) Math.round(14 + 0.8 * flat);
    }

    /**
     * Where a lobbed shot must land to meet the monster: where it walks to during the
     * flight (its last step per tick, a little short because monsters turn and stop).
     */
    static Vec3 lead(Vec3 from, LivingEntity target) {
        double vx = Math.max(-0.4, Math.min(0.4, target.getX() - target.xo));
        double vz = Math.max(-0.4, Math.min(0.4, target.getZ() - target.zo));
        Vec3 at = target.position();
        for (int i = 0; i < 2; i++) {
            double dx = at.x - from.x;
            double dz = at.z - from.z;
            int t = flightTicks(Math.sqrt(dx * dx + dz * dz));
            at = target.position().add(vx * t * 0.85, 0, vz * t * 0.85);
        }
        return at;
    }

    /** Walks the line in half block steps; anything solid in between blocks the shot. */
    private static boolean clearShot(ServerLevel level, Vec3 from, Vec3 to, BlockPos self) {
        Vec3 step = to.subtract(from);
        int steps = (int) Math.ceil(step.length() * 2.0);
        for (int i = 1; i < steps; i++) {
            BlockPos at = BlockPos.containing(from.add(step.scale(i / (double) steps)));
            if (at.equals(self)) {
                continue;
            }
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void wearOut(ServerLevel level, BlockPos pos, TrapRecord record) {
        level.removeBlock(pos, false);
        traps().remove(record);
        level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                12, 0.3, 0.2, 0.3, 0.02);
        playAt(level, pos, SoundEvents.ANVIL_LAND, 0.4f, 1.6f);
        dirty = true;
    }

    private static void playAt(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void actionBar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }

    private boolean takeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }
}
