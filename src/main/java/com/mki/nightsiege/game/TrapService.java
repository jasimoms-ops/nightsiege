package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
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

    /** Traps are checked every this many ticks. */
    private static final int TICK_INTERVAL = 5;

    /** The manager swaps its save object when a world loads, so always ask for the current one. */
    private final Supplier<SiegeSave> save;
    private final SkillTree tree;
    private int tickCounter;
    private boolean dirty;

    public TrapService(Supplier<SiegeSave> save, SkillTree tree) {
        this.save = save;
        this.tree = tree;
    }

    // ------------------------------------------------------------------
    // placing, breaking, reloading
    // ------------------------------------------------------------------

    /** True when this player may set down this trap: the engineer who unlocked it, or anyone in creative. */
    public boolean mayPlace(ServerPlayer player, TrapKind kind) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        return tree.trapTier(kind, save.get().skillsOf(player.getUUID().toString())) > 0;
    }

    public void onPlaced(ServerLevel level, BlockPos pos, LivingEntity placer, TrapKind kind) {
        String owner = placer == null ? "" : placer.getUUID().toString();
        String dimension = SiegeManager.dimensionName(level);
        traps().removeIf(t -> t.matches(dimension, pos.getX(), pos.getY(), pos.getZ()));
        TrapRecord record = new TrapRecord(kind.id(), dimension, pos.getX(), pos.getY(), pos.getZ(), owner);
        int tier = tierOf(record);
        record.uses = kind.uses(tier);
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
        int max = TrapKind.TURRET.maxAmmo(tierOf(record));
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

    /** Hands an engineer their free traps for the night. */
    public void giveFreeTraps(ServerPlayer player) {
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        int total = 0;
        for (TrapKind kind : TrapKind.values()) {
            int count = kind.freePerSiege(tree.trapTier(kind, skills));
            if (count > 0) {
                player.getInventory().placeItemBackInInventory(new ItemStack(ModContent.TRAP_ITEMS.get(kind), count));
                total += count;
            }
        }
        if (total > 0) {
            player.sendSystemMessage(Component.translatable("nightsiege.msg.free_traps", total));
        }
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
        for (LivingEntity victim : victims) {
            victim.hurtServer(level, source, (float) TrapKind.SPIKE.damage(tier));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TrapKind.SPIKE.holdTicks(tier), 1));
            record.uses--;
        }
        level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                8, 0.3, 0.1, 0.3, 0.1);
        playAt(level, pos, SoundEvents.SWEET_BERRY_BUSH_BREAK, 0.7f, 0.6f);
        record.readyAt = now + TrapKind.SPIKE.cooldownTicks(tier);
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
        int hold = TrapKind.SNARE.holdTicks(tier);
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
        record.readyAt = now + TrapKind.SNARE.cooldownTicks(tier);
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
        Vec3 center = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        double r = TrapKind.MINE.radius(tier);
        double damage = TrapKind.MINE.damage(tier);
        DamageSource source = sourceFor(level, record);

        level.removeBlock(pos, false);
        traps().remove(record);
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
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2f, 1.1f);
    }

    private void tickTurret(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        if (record.ammo <= 0) {
            return;
        }
        Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.75, 0);
        double range = TrapKind.TURRET.radius(tier);
        LivingEntity target = enemies(level, new AABB(muzzle, muzzle).inflate(range)).stream()
                .filter(e -> e.distanceToSqr(muzzle) <= range * range)
                .filter(e -> clearShot(level, muzzle, e.getEyePosition(), pos))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(muzzle)))
                .orElse(null);
        if (target == null) {
            return;
        }
        Arrow arrow = new Arrow(level, muzzle.x, muzzle.y, muzzle.z, new ItemStack(Items.ARROW), null);
        arrow.entityTags().add("nightsiege_arrow");   // keeps the archer skills off it
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(TrapKind.TURRET.damage(tier));
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
        record.readyAt = now + TrapKind.TURRET.cooldownTicks(tier);
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
        Vec3 center = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        DamageSource source = sourceFor(level, record);
        float seconds = TrapKind.FLAME.holdTicks(tier) / 20f;
        for (LivingEntity victim : around(level, center, TrapKind.FLAME.radius(tier))) {
            victim.igniteForSeconds(seconds);
            victim.hurtServer(level, source, (float) TrapKind.FLAME.damage(tier));
        }
        // Only particles: the trap never places fire blocks.
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 40,
                TrapKind.FLAME.radius(tier) * 0.5, 0.4, TrapKind.FLAME.radius(tier) * 0.5, 0.03);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 6, 0.4, 0.2, 0.4, 0.0);
        playAt(level, pos, SoundEvents.FIRECHARGE_USE, 0.9f, 0.8f);
        record.uses--;
        record.readyAt = now + TrapKind.FLAME.cooldownTicks(tier);
        if (record.uses <= 0) {
            wearOut(level, pos, record);
        }
        dirty = true;
    }

    private static final net.minecraft.core.particles.DustParticleOptions POISON_DUST =
            new net.minecraft.core.particles.DustParticleOptions(0x6FB33A, 1.5f);

    private void tickPoison(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        if (onTop(level, pos).isEmpty()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos).add(0, 0.8, 0);
        double r = TrapKind.POISON.radius(tier);
        int ticks = TrapKind.POISON.holdTicks(tier);
        int amp = TrapKind.POISON.amplifier(tier);
        for (LivingEntity victim : around(level, center, r)) {
            // Poison heals the undead, so they wither instead.
            boolean undead = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .wrapAsHolder(victim.getType()).is(net.minecraft.tags.EntityTypeTags.UNDEAD);
            victim.addEffect(new MobEffectInstance(undead ? MobEffects.WITHER : MobEffects.POISON, ticks, amp));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks / 2, 0));
        }
        level.sendParticles(POISON_DUST, center.x, center.y, center.z, 60, r * 0.5, 0.5, r * 0.5, 0.0);
        playAt(level, pos, SoundEvents.BREWING_STAND_BREW, 1.0f, 0.6f);
        record.uses--;
        record.readyAt = now + TrapKind.POISON.cooldownTicks(tier);
        if (record.uses <= 0) {
            wearOut(level, pos, record);
        }
        dirty = true;
    }

    private void tickTesla(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        Vec3 from = Vec3.atCenterOf(pos).add(0, 0.8, 0);
        double range = TrapKind.TESLA.radius(tier);
        LivingEntity first = enemies(level, new AABB(from, from).inflate(range)).stream()
                .filter(e -> e.distanceToSqr(from) <= range * range)
                .filter(e -> clearShot(level, from, e.getEyePosition(), pos))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(from)))
                .orElse(null);
        if (first == null) {
            return;
        }
        DamageSource source = sourceFor(level, record);
        float damage = (float) TrapKind.TESLA.damage(tier);
        List<LivingEntity> hit = new ArrayList<>();
        LivingEntity current = first;
        Vec3 start = from;
        while (current != null && hit.size() < TrapKind.TESLA.chainTargets(tier)) {
            hit.add(current);
            Vec3 end = current.position().add(0, current.getBbHeight() * 0.5, 0);
            spark(level, start, end);
            current.hurtServer(level, source, damage);
            start = end;
            // Jump to the nearest monster within four blocks not hit yet.
            Vec3 at = end;
            current = enemies(level, new AABB(at, at).inflate(4.0)).stream()
                    .filter(e -> !hit.contains(e))
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(at)))
                    .orElse(null);
        }
        playAt(level, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
        record.readyAt = now + TrapKind.TESLA.cooldownTicks(tier);
    }

    /** A crackling line of sparks between two points. */
    private static void spark(ServerLevel level, Vec3 a, Vec3 b) {
        Vec3 step = b.subtract(a);
        int n = Math.max(2, (int) (step.length() * 3));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(step.scale(i / (double) n));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
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

    private static List<LivingEntity> enemies(ServerLevel level, AABB box) {
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
