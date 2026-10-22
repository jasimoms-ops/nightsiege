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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /** Gunner ultimate: this engineer's turrets fire three times as fast until the given time. */
    public void overcharge(ServerPlayer owner, long until) {
        overchargedUntil.put(owner.getUUID().toString(), until);
    }

    /** Hands an engineer their free traps for the night. */
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
                case MINE -> tickMine(level, pos, record, tier);
                case TURRET -> tickTurret(level, pos, record, tier, now);
                case FLAME -> tickFlame(level, pos, record, tier, now);
                case POISON -> tickPoison(level, pos, record, tier, now);
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
            victim.hurtServer(level, source, (float) (TrapKind.SPIKE.damage(tier) * power));
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

    private void tickMine(ServerLevel level, BlockPos pos, TrapRecord record, int tier) {
        if (enemies(level, new AABB(pos.getX() - 0.2, pos.getY() + 1, pos.getZ() - 0.2,
                pos.getX() + 1.2, pos.getY() + 2.2, pos.getZ() + 1.2)).isEmpty()) {
            return;
        }
        blowMine(level, pos, record, tier, 1.0, 1.0, 0);
    }

    /** The mine goes off: hurts every monster around, then is gone. Neighbours may follow. */
    private void blowMine(ServerLevel level, BlockPos pos, TrapRecord record, int tier,
                          double damageScale, double radiusScale, int depth) {
        long now = level.getGameTime();
        Vec3 center = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        double shrapnel = bonus(record, SkillEffect.SHRAPNEL_MINE);
        double r = TrapKind.MINE.radius(tier) * (shrapnel > 0 ? 1.5 : 1.0) * radiusScale;
        double damage = (TrapKind.MINE.damage(tier) + shrapnel) * power(record, now) * blastScale(record) * damageScale;
        DamageSource source = sourceFor(level, record);

        level.removeBlock(pos, false);
        traps().remove(record);
        boostedUntil.remove(key(record));
        dirty = true;

        for (LivingEntity victim : enemies(level, new AABB(center, center).inflate(r))) {
            double distance = Math.sqrt(victim.distanceToSqr(center));
            if (distance > r) {
                continue;
            }
            // Full force at the centre, half at the edge.
            double falloff = 1.0 - 0.5 * (distance / r);
            victim.hurtServer(level, source, (float) (damage * falloff));
            Vec3 away = victim.position().subtract(center);
            Vec3 push = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0.6, 0)
                    : away.normalize().scale(0.9 * falloff).add(0, 0.35, 0);
            victim.push(push.x, push.y, push.z);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 40, r * 0.4, 0.4, r * 0.4, 0.05);
        if (shrapnel > 0) {
            level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 30, r * 0.4, 0.3, r * 0.4, 0.4);
        }
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2f, 1.1f);
        // Chain reaction: the engineer's other mines close by go off too.
        if (depth < 6 && has(record, "e_chain_reaction")) {
            for (TrapRecord other : new ArrayList<>(traps())) {
                if (TrapKind.MINE.id().equals(other.kind) && record.owner.equals(other.owner)
                        && record.dimension.equals(other.dimension)
                        && Vec3.atCenterOf(new BlockPos(other.x, other.y, other.z)).distanceToSqr(center) <= 25.0) {
                    BlockPos at = new BlockPos(other.x, other.y, other.z);
                    if (ModContent.trapOf(level.getBlockState(at)) == TrapKind.MINE) {
                        blowMine(level, at, other, tierOf(other), damageScale, radiusScale, depth + 1);
                    }
                }
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
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(TrapKind.TURRET.damage(tier) * power(record, now) * piercing(record, now));
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
        double damage = TrapKind.FLAME.damage(tier) * power(record, now) * blastScale(record) * damageScale;
        for (LivingEntity victim : around(level, center, r)) {
            victim.igniteForSeconds(seconds);
            boolean poisoned = victim.hasEffect(MobEffects.POISON) || victim.hasEffect(MobEffects.WITHER);
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

    private void tickPoison(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        if (onTop(level, pos).isEmpty()) {
            return;
        }
        releasePoison(level, pos, record, tier, now, 1.0);
    }

    private void releasePoison(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now, double radiusScale) {
        Vec3 center = Vec3.atCenterOf(pos).add(0, 0.8, 0);
        double r = TrapKind.POISON.radius(tier) * radiusScale;
        int ticks = (int) Math.round(TrapKind.POISON.holdTicks(tier) * poisonScale(record));
        int amp = TrapKind.POISON.amplifier(tier) + (boosted(record, now) ? 1 : 0);
        double acid = bonus(record, SkillEffect.ACID);
        for (LivingEntity victim : around(level, center, r)) {
            // Poison heals the undead, so they wither instead.
            boolean undead = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .wrapAsHolder(victim.getType()).is(net.minecraft.tags.EntityTypeTags.UNDEAD);
            victim.addEffect(new MobEffectInstance(undead ? MobEffects.WITHER : MobEffects.POISON, ticks, amp));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks / 2, acid > 0 ? 1 : 0));
            if (acid > 0) {
                marker.mark(victim, acid, now + ticks, false);
            }
        }
        level.sendParticles(POISON_DUST, center.x, center.y, center.z, 60, r * 0.5, 0.5, r * 0.5, 0.0);
        playAt(level, pos, SoundEvents.BREWING_STAND_BREW, 1.0f, 0.6f);
        record.uses--;
        record.readyAt = now + quicker(record, now, TrapKind.POISON.cooldownTicks(tier));
        if (record.uses <= 0) {
            wearOut(level, pos, record);
        }
        dirty = true;
    }

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
                * piercing(record, now));
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
            if (sneak && (kind == TrapKind.MINE || kind == TrapKind.FLAME || kind == TrapKind.POISON)) {
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
            } else if (pay(player, skills, cost(skills, COST_OVERCHARGE))) {
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
            case MINE -> blowMine(level, pos, record, tier, damage, radius, 0);
            case FLAME -> fireFlame(level, pos, record, tier, now, damage, radius);
            case POISON -> {
                releasePoison(level, pos, record, tier, now, radius);
                double plague = tree.totalBonus(SkillEffect.PLAGUE_MINE, skills);
                if (plague > 0) {
                    Vec3 c = Vec3.atCenterOf(pos).add(0, 0.6, 0);
                    DamageSource source = sourceFor(level, record);
                    double r = TrapKind.POISON.radius(tier) * radius;
                    for (LivingEntity victim : around(level, c, r)) {
                        victim.hurtServer(level, source, (float) (plague * damage));
                    }
                    level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.5, 0.2, 0.5, 0.0);
                    playAt(level, pos, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.4f);
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
        return kind != TrapKind.TESLA && kind != TrapKind.MINE && record.uses < fullUses(kind, tierOf(record), record);
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
        return energy.computeIfAbsent(player.getUUID(), k -> maxEnergy(skills));
    }

    private double maxEnergy(PlayerSkills skills) {
        return REMOTE_ENERGY + (skills == null ? 0 : tree.totalBonus(SkillEffect.REMOTE_ENERGY, skills));
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
        if (have < cost) {
            actionBar(player, Component.translatable("nightsiege.msg.remote_low",
                    (int) Math.floor(have), (int) Math.ceil(cost)));
            return false;
        }
        energy.put(player.getUUID(), have - cost);
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
        if (!boosted(record, now)) {
            return 1.0;
        }
        return bonus(record, SkillEffect.OVERVOLT) > 0 ? 3.0 : 2.0;
    }

    /** Cooldowns halve while overcharged. */
    private int quicker(TrapRecord record, long now, int ticks) {
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
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive());
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
