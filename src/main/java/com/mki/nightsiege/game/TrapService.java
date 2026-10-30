package com.mki.nightsiege.game;

import com.mki.nightsiege.core.BrewKind;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
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
import net.minecraft.world.entity.decoration.ArmorStand;
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
    /** Towers strengthened in the tower menu (Simon, 27 October 2026): trap key to the game time it ends. */
    private final java.util.Map<String, Long> strengthenedUntil = new java.util.HashMap<>();
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
        current = this;
    }

    /** The service of the running server (for the damage hook of a seated gunner). */
    private static TrapService current;

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
        // In a dungeon or at the war camp only the field towers count (28 October 2026): the ones
        // standing at home used to fill the caps, so the engineer had nothing to set up there.
        if (placedBy(player.getUUID().toString(), kind, fieldFight(player)) >= max) {
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

    /** Traps of this kind an engineer has standing, only the field ones or only the ones at home. */
    public int placedBy(String owner, TrapKind kind, boolean field) {
        int count = 0;
        for (TrapRecord record : traps()) {
            if (kind.id().equals(record.kind) && owner.equals(record.owner) && record.field == field) {
                count++;
            }
        }
        return count;
    }

    /** True while the player fights in a dungeon or at the war camp (his traps there are field traps). */
    static boolean fieldFight(ServerPlayer player) {
        SiegeManager m = SiegeManager.get();
        return m != null && player != null && m.fieldFighter(player);
    }

    public void onPlaced(ServerLevel level, BlockPos pos, LivingEntity placer, TrapKind kind) {
        String owner = placer == null ? "" : placer.getUUID().toString();
        String dimension = SiegeManager.dimensionName(level);
        traps().removeIf(t -> t.matches(dimension, pos.getX(), pos.getY(), pos.getZ()));
        TrapRecord record = new TrapRecord(kind.id(), dimension, pos.getX(), pos.getY(), pos.getZ(), owner);
        record.field = placer instanceof ServerPlayer player && fieldFight(player);
        int tier = tierOf(record);
        record.uses = fullUses(kind, tier, record);
        traps().add(record);
        record.ammo = magazine(record);
        record.shield = shieldMax(record);
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
        actionBar(player, Component.translatable("nightsiege.msg.turret_endless"));
        return true;
    }

    /** Traps destroyed tonight whose engineer has trap recycling. */
    private final List<TrapRecord> recycled = new ArrayList<>();

    /** Owners whose spare part was used tonight (Ersatzteil, once a night). */
    private final java.util.Set<String> spareUsed = new java.util.HashSet<>();

    /** Ersatzteil: the lost trap goes back into its owner's inventory, once a night. */
    private boolean spareBack(MinecraftServer server, TrapRecord record, TrapKind kind) {
        if (server == null || spareUsed.contains(record.owner) || ModContent.TRAP_ITEMS.get(kind) == null) {
            return false;
        }
        ServerPlayer owner;
        try {
            owner = server.getPlayerList().getPlayer(java.util.UUID.fromString(record.owner));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (owner == null) {
            return false;
        }
        spareUsed.add(record.owner);
        owner.getInventory().placeItemBackInInventory(new ItemStack(ModContent.TRAP_ITEMS.get(kind), 1));
        actionBar(owner, Component.translatable("nightsiege.msg.spare_back"));
        return true;
    }

    /** After the night: recycled traps stand where they stood (if the spot is free). */
    public void recycle(MinecraftServer server) {
        spareUsed.clear();
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
            traps().add(record);
            record.ammo = magazine(record);
            record.shield = shieldMax(record);
            level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0);
        }
        recycled.clear();
        dirty = true;
    }

    /** The owner's turret nearest a spot that still has room for arrows (the copper golem's errand). */
    public BlockPos emptiestTurret(ServerLevel level, String owner, Vec3 near, double radius) {
        if (true) {
            return null;   // endless arrows since 26 October 2026; the copper golem repairs floor traps now
        }
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

    /** The owner's most worn floor trap near a spot, or null (the copper golem's errand). */
    public BlockPos mostWornTrap(ServerLevel level, String owner, Vec3 near, double radius) {
        String dimension = SiegeManager.dimensionName(level);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (TrapRecord record : traps()) {
            TrapKind kind = TrapKind.byId(record.kind);
            if (kind == null || !owner.equals(record.owner) || !dimension.equals(record.dimension) || !canReload(record, kind)) {
                continue;
            }
            BlockPos pos = new BlockPos(record.x, record.y, record.z);
            double d = near.distanceToSqr(Vec3.atCenterOf(pos));
            if (d > radius * radius) {
                continue;
            }
            double left = record.broken ? -1.0 : record.uses / (double) Math.max(1, fullUses(kind, tierOf(record), record));
            double score = left * 100.0 + Math.sqrt(d);
            if (score < bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return best;
    }

    /** The copper golem repairs a floor trap fully; true when it was worn. */
    public boolean repairByGolem(ServerLevel level, BlockPos pos) {
        TrapRecord record = find(level, pos);
        TrapKind kind = record == null ? null : TrapKind.byId(record.kind);
        if (kind == null || !canReload(record, kind)) {
            return false;
        }
        record.uses = fullUses(kind, tierOf(record), record);
        record.broken = false;
        dirty = true;
        return true;
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

    /**
     * How much longer monsters need to dig through a block: every tower of an engineer with
     * steel turrets (crate gain) holds twice as long, and the skill e_durable adds its share
     * on top (balance update, 26 October 2026: it was a copy of fine mechanics).
     */
    public double digFactor(ServerLevel level, BlockPos pos) {
        TrapKind here = ModContent.trapOf(level.getBlockState(pos));
        if (here == null || !here.isTower()) {
            return 1.0;
        }
        TrapRecord record = find(level, pos);
        if (record == null) {
            return 1.0;
        }
        double factor = gainOf(record, "stahlturm") > 0 ? 2.0 : 1.0;
        return factor * (1.0 + bonus(record, SkillEffect.TOWER_TOUGHNESS));
    }

    /** Ultimate overcharge: this engineer's arrow turrets and tesla coils fire three times as fast until the given time. */
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
                int have = placedBy(player.getUUID().toString(), kind, fieldFight(player));
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
    /**
     * The frost lord's breath (dungeon update): towers within {@code radius} of a point do not
     * shoot for {@code ticks}. Returns how many were struck.
     */
    public int freezeNear(ServerLevel level, net.minecraft.world.phys.Vec3 at, double radius, int ticks) {
        int count = 0;
        long until = level.getGameTime() + ticks;
        String dim = SiegeManager.dimensionName(level);
        for (TrapRecord record : traps()) {
            if (!dim.equals(record.dimension)) {
                continue;
            }
            double dx = record.x + 0.5 - at.x;
            double dy = record.y + 0.5 - at.y;
            double dz = record.z + 0.5 - at.z;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                record.readyAt = Math.max(record.readyAt, until);
                level.sendParticles(ParticleTypes.SNOWFLAKE, record.x + 0.5, record.y + 1.0, record.z + 0.5, 20, 0.4, 0.5, 0.4, 0.02);
                count++;
            }
        }
        return count;
    }

    public boolean tick(MinecraftServer server) {
        // The manned towers and the alchemist's clouds want every tick (aim and pulses).
        tickSeats(server);
        tickFixes(server);
        AlchemyZones.tick(server);
        ArrowRain.tick(server);   // the archers' arrow rain (Simon 30.10.)
        if (++tickCounter < TICK_INTERVAL) {
            return takeDirty();
        }
        SpotterChunks.tick(server);
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
            if (record.field && !fieldFight(ownerOf(level, record))) {
                // The fight in the dungeon or at the camp is over: its field towers go, without a drop.
                list.remove(record);
                dirty = true;
                if (level.isLoaded(pos) && ModContent.trapOf(level.getBlockState(pos)) == kind) {
                    level.removeBlock(pos, false);
                    level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                            12, 0.3, 0.3, 0.3, 0.02);
                }
                continue;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (ModContent.trapOf(state) != kind) {
                // Gone by some way the break event did not see, e.g. an explosion.
                list.remove(record);
                dirty = true;
                if (record.field) {
                    // A field tower lost in the dungeon or at the camp stays lost (no spare, no rebuild at home).
                } else if (gainOf(record, "recycling") > 0 && spareBack(server, record, kind)) {
                    // Ersatzteil (id recycling, 27 October 2026): back in the owner's inventory at once.
                } else if (NightDamage.recording(level) || gainOf(record, "recycling") > 0) {
                    recycled.add(record);   // every trap lost in the night stands again after it (27 October 2026)
                }
                continue;
            }
            long now = level.getGameTime();
            if (state.getBlock() instanceof TowerBlock && state.getValue(TowerBlock.MANNED) && !mannedTower(record)) {
                setLook(level, pos, false);   // a manned look left over from a crash or a restart
            }
            if (record.broken) {
                // Traps no longer break (Simon, 27 October 2026): one broken in an old save works again.
                record.broken = false;
                dirty = true;
            }
            if (fixes.containsKey(key(record))) {
                continue;   // being strengthened: it waits the 3 s
            }
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

    /**
     * One direct hit of a chemistry tower: 10 life points of tonight's level times trapDamage,
     * so 37.5 % of a zombie with the default trapDamage 0.75 (it was meant as half a zombie).
     */
    static double towerHit(TrapKind kind) {
        return SkillValues.get(kind.id(), "hit", 10.0) * levelScale();
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
        return level * Math.max(0.05, m.config().trapDamage) * Curses.trapFactor();   // the curse "Stumpfe Fallen"
    }

    /**
     * Crate gains that make a chemistry tower's shot stronger, and the remote's overcharge
     * (double, triple with overvolt) like on every other trap (balance update, 26 October 2026:
     * mortar and potion thrower only got faster before).
     */
    private double towerPower(TrapRecord record, long now) {
        double gain = 1.0 + 0.1 * gainOf(record, "verstaerkt");
        if (record.ambushNight != GainGear.nightId() && gainOf(record, "tarnfallen") > 0) {
            record.ambushNight = GainGear.nightId();
            gain *= 1.5;
        }
        return gain * overcharged(record, now);
    }

    /**
     * The monster with the most others around it within reach (marked ones first), or null.
     * Only monsters the tower can see count: none behind walls, in caves or below hills
     * (Simon's playtest: the mortars kept firing at monsters they could not reach).
     */
    private LivingEntity bestGroup(ServerLevel level, TrapRecord record, Vec3 from, double range, double splash, long now,
                                   java.util.Set<LivingEntity> skip) {
        double search = range * searchFactor();
        List<LivingEntity> all = enemies(level, new AABB(from, from).inflate(search, 14.0, search));
        BlockPos self = BlockPos.containing(from.x, from.y - 1.0, from.z);
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : all) {
            if (skip != null && skip.contains(e) || shielded(e)) {
                continue;
            }
            double dx = e.getX() - from.x;
            double dz = e.getZ() - from.z;
            double flat = dx * dx + dz * dz;
            double reach = reachOf(record, e, range, now);
            if (flat > reach * reach || flat < 4.0) {
                continue;   // too far, or right at the tower's foot
            }
            int around = 0;
            for (LivingEntity o : all) {
                if (o.distanceToSqr(e) <= splash * splash) {
                    around++;
                }
            }
            // Bosses and champions before any group (28 October 2026), a marked monster on top.
            double score = around * 10.0 + (markedBy(record, e, now) ? 25.0 : 0.0) + (bigTarget(e) ? 30.0 : 0.0)
                    - Math.sqrt(flat) * 0.1;
            if (score > bestScore && sees(level, from, e, self)) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    /**
     * The mortar fires steadily onto the spot its engineer set with the remote (X key while
     * looking at a block); without a spot it stays quiet (Simon's playtest, 25 October 2026).
     */
    /** For the client test: mortars fire outside a siege night too. */
    public static volatile boolean mortarsAnyTime;

    private void tickMortar(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        if (!record.aimed) {
            record.readyAt = now + 20;
            return;
        }
        // Only in the night (Simon's list, 27 October 2026): by day they kept shelling their spot.
        // A field mortar fires in its dungeon or camp fight too (28 October 2026): it only stands while that fight runs.
        SiegeManager m = SiegeManager.get();
        if (!mortarsAnyTime && !record.field && (m == null || !m.isActive())) {
            record.readyAt = now + 20;
            return;
        }
        Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.9, 0);
        Vec3 aim = aimSpot(level, record);
        if (shotLog != null) {
            shotLog.accept(String.format(java.util.Locale.ROOT, "mortar fires at %.1f %.1f %.1f", aim.x, aim.y, aim.z));
        }
        lob(level, record, muzzle, aim, KitProjectile.SHELL, tier, 1.0, 1.0);
        afterShot(level, pos, record);
        level.sendParticles(ParticleTypes.SMOKE, aim.x, aim.y + 0.1, aim.z, 6, 0.4, 0.02, 0.4, 0.0);
        record.readyAt = now + quicker(record, now, slowed(TrapKind.MORTAR, TrapKind.MORTAR.cooldownTicks(tier)));
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
        afterShot(level, pos, record);
        record.readyAt = now + quicker(record, now, TrapKind.BREWER.cooldownTicks(tier));
        dirty = true;
    }

    /** Fires a shell or a potion in a high arc (over walls) onto a spot. */
    private void lob(ServerLevel level, TrapRecord record, Vec3 from, Vec3 to, String kind, int tier,
                     double damageScale, double radiusScale) {
        lob(level, record, from, to, kind, tier, damageScale, radiusScale, "");
    }

    /** A lobbed shot with a special fire from the bird's-eye view ("fire", "smoke", or ""). */
    private void lob(ServerLevel level, TrapRecord record, Vec3 from, Vec3 to, String kind, int tier,
                     double damageScale, double radiusScale, String mode) {
        boolean shell = KitProjectile.SHELL.equals(kind);
        ItemStack look = shell ? new ItemStack("smoke".equals(mode) ? Items.FIREWORK_STAR : Items.FIRE_CHARGE)
                : PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON);
        KitProjectile shot = new KitProjectile(level, from.x, from.y, from.z, look, kind);
        shot.setData(String.join("|", record.kind, record.dimension, String.valueOf(record.x), String.valueOf(record.y),
                String.valueOf(record.z), record.owner == null ? "" : record.owner, String.valueOf(tier),
                String.valueOf(damageScale), String.valueOf(radiusScale), mode == null ? "" : mode));
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
            String mode = d.length > 9 ? d[9] : "";
            if (KitProjectile.SHELL.equals(shot.kind()) && "smoke".equals(mode)) {
                smokeBurst(level, at);
            } else if (KitProjectile.SHELL.equals(shot.kind())) {
                shellBurst(level, record, at, direct, tier, damageScale, radiusScale, false);
                if ("fire".equals(mode)) {
                    GainGear.burnPatch(level, at, Math.round(SkillValues.get("e_fire_salvo", "burn", 100)));
                }
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
        double r = TrapKind.MORTAR.splash(tier) * (1.0 + shrapnel) * radiusScale
                * (small ? SkillValues.get("e_chain_reaction", "radius", 0.6) : 1.0);
        double damage = towerHit(TrapKind.MORTAR) * towerPower(record, level.getGameTime()) * blastScale(record) * damageScale
                * (small ? SkillValues.get("e_chain_reaction", "dmg", 1.0 / 3.0) : 1.0);
        DamageSource source = sourceFor(level, record);
        boolean napalm = has(record, "e_napalm");
        double accelerant = bonus(record, SkillEffect.ACCELERANT);
        if (shotLog != null) {
            List<LivingEntity> hit = around(level, at, r);
            shotLog.accept(String.format(java.util.Locale.ROOT, "shell burst at %.1f %.1f %.1f r=%.2f dmg=%.1f direct=%s victims=%d",
                    at.x, at.y, at.z, r, damage, direct == null ? "-" : direct.getType().toShortString(), hit.size()));
        }
        for (LivingEntity victim : around(level, at, r)) {
            double f = falloff(victim, at, direct, r);
            victim.invulnerableTime = 0;
            // The accelerant: blasts and flames hit poisoned monsters harder.
            double fire = accelerant > 0 && isPoisoned(victim) ? 1.0 + accelerant : 1.0;
            victim.hurtServer(level, source, (float) (damage * f * fire));
            if (napalm) {
                victim.igniteForSeconds((float) (SkillValues.get("e_napalm", "shellburn", 60) / 20.0));
            }
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
            int count = (int) Math.round(SkillValues.get("e_chain_reaction", "count", 3));
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2 * i / count + level.getRandom().nextDouble();
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
        double total = towerHit(TrapKind.BREWER) * towerPower(record, now) * damageScale * poisonScale(record);
        double acid = bonus(record, SkillEffect.ACID);
        int amp = TrapKind.BREWER.amplifier(tier) + (acid > 0 ? (int) Math.round(SkillValues.get("e_acid", "slow", 1)) : 0);
        boolean accelerant = bonus(record, SkillEffect.ACCELERANT) > 0;
        for (LivingEntity victim : around(level, at, r)) {
            if (accelerant) {
                // The accelerant: the potion sets them alight, and the poison eats burning monsters faster.
                victim.igniteForSeconds((float) (SkillValues.get("e_accelerant", "burn", 40) / 20.0));
            }
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
                victim.hurtServer(level, source, (float) (plague * damageScale * overcharged(record, now)));
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
            double accelerant = bonus(who, SkillEffect.ACCELERANT);
            if (accelerant > 0 && victim.isOnFire()) {
                sum *= 1.0 + accelerant;
            }
            victim.invulnerableTime = 0;
            victim.hurtServer(level, sourceFor(level, who), (float) sum);
            level.sendParticles(POISON_DUST, victim.getX(), victim.getY(0.6), victim.getZ(), 4, 0.25, 0.3, 0.25, 0.0);
            if (list.isEmpty()) {
                it.remove();
            }
        }
    }

    private void tickTurret(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        if (mannedTower(record)) {
            record.readyAt = now + 5;   // its gunner fires it (Simon, 26 October 2026)
            return;
        }
        // Endless arrows since 26 October 2026 (Simon): no ammo check, nothing to reload.
        Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.75, 0);
        double range = TrapKind.TURRET.radius(tier) + bonus(record, SkillEffect.TOWER_RANGE);
        // Bosses and champions reach further and come first (28 October 2026), after a marked monster.
        LivingEntity target = enemies(level, new AABB(muzzle, muzzle).inflate(range * searchFactor())).stream()
                .filter(e -> !shielded(e) && inReach(record, e, muzzle, range, now))
                .filter(e -> clearShot(level, port(muzzle, e), e.getEyePosition(), pos)
                        || clearShot(level, port(muzzle, e), e.position().add(0, e.getBbHeight() * 0.5, 0), pos))
                .min(Comparator.comparingDouble(e -> priority(record, e, muzzle, now)))
                .orElse(null);
        if (target == null) {
            return;
        }
        // The arrow leaves at the side facing the target, so it also hits monsters right below
        // a turret on a wall (Simon's playtest, 25 October 2026: it could not shoot downwards).
        Vec3 out = port(muzzle, target);
        Arrow arrow = new Arrow(level, out.x, out.y, out.z, new ItemStack(Items.ARROW), null);
        arrow.entityTags().add("nightsiege_arrow");   // keeps the archer skills off it
        arrow.entityTags().add("nightsiege_turret");  // and the crate gains (the archer tag is on every arrow)
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        // Alone it shoots at 60 % (Simon, 26 October 2026): a gunner in the seat does better.
        arrow.setBaseDamage(turretDamage(record, tier, now) * unmanned(TrapKind.TURRET));
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
        dx = target.getX() - out.x;
        dz = target.getZ() - out.z;
        double dy = target.getY(0.5) - out.y;
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.12, dz, 2.4f, 1.5f);
        level.addFreshEntity(arrow);
        afterShot(level, pos, record);
        playAt(level, pos, SoundEvents.DISPENSER_LAUNCH, 0.8f, 1.3f);
        if (ultOvercharged(record, now)) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 3, 0.2, 0.2, 0.2, 0.05);
        }
        record.readyAt = now + turretCooldown(record, tier, now);
        dirty = true;
    }

    /** Damage of one turret arrow, before the manned or unmanned factor. */
    private double turretDamage(TrapRecord record, int tier, long now) {
        return TrapKind.TURRET.damage(tier) * power(record, now) * piercing(record, now) * levelScale();
    }

    /** Ticks between two turret arrows, manned or not. */
    private int turretCooldown(TrapRecord record, int tier, long now) {
        int cooldown = quicker(record, now, slowed(TrapKind.TURRET, TrapKind.TURRET.cooldownTicks(tier)));
        if (bonus(record, SkillEffect.AMMO_BELT) > 0) {
            // Munitionsband: the turret shoots faster (it held more arrows until 26 October 2026).
            cooldown = Math.max(3, (int) Math.round(cooldown * (1.0 - SkillValues.get("e_ammobelt", "speed", 0.2))));
        }
        if (ultOvercharged(record, now)) {
            cooldown = Math.max(3, (int) (cooldown / ultSpeed()));
        }
        return cooldown;
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
        float seconds = TrapKind.FLAME.holdTicks(tier) / 20f * (napalm ? (float) SkillValues.get("e_napalm", "burn", 2.0) : 1f);
        double r = (TrapKind.FLAME.radius(tier) + (napalm ? SkillValues.get("e_napalm", "radius", 1.0) : 0.0)) * radiusScale;
        double damage = TrapKind.FLAME.damage(tier) * power(record, now) * blastScale(record) * damageScale * levelScale();
        for (LivingEntity victim : around(level, center, r)) {
            victim.igniteForSeconds(seconds);
            victim.hurtServer(level, source, (float) (damage * (isPoisoned(victim) ? 1.0 + accelerant : 1.0)));
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

    /** Poisoned by anything: a potion effect or the brewer's own poison. */
    private boolean isPoisoned(LivingEntity victim) {
        return victim.hasEffect(MobEffects.POISON) || victim.hasEffect(MobEffects.WITHER) || poisoned(victim);
    }

    static final net.minecraft.core.particles.DustParticleOptions POISON_DUST =
            new net.minecraft.core.particles.DustParticleOptions(0x6FB33A, 1.5f);

    private void tickTesla(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        if (mannedTower(record)) {
            record.readyAt = now + 5;   // its gunner holds the beam (Simon, 26 October 2026)
            return;
        }
        Vec3 from = Vec3.atCenterOf(pos).add(0, 0.8, 0);
        double range = TrapKind.TESLA.radius(tier) + bonus(record, SkillEffect.TOWER_RANGE);
        LivingEntity first = enemies(level, new AABB(from, from).inflate(range * searchFactor())).stream()
                .filter(e -> !shielded(e) && inReach(record, e, from, range, now))
                .filter(e -> clearShot(level, from, e.getEyePosition(), pos))
                .min(Comparator.comparingDouble(e -> priority(record, e, from, now)))
                .orElse(null);
        if (first == null) {
            return;
        }
        // Alone it strikes at 60 % (Simon, 26 October 2026): a gunner in the seat does better.
        float damage = (float) (teslaDamage(record, tier, now) * unmanned(TrapKind.TESLA));
        discharge(level, record, tier, now, first, from, damage);
        afterShot(level, pos, record);
        playAt(level, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
        record.readyAt = now + teslaCooldown(level, pos, record, tier, now);
    }

    /** Damage of one discharge on each monster it reaches, before the manned or unmanned factor. */
    private double teslaDamage(TrapRecord record, int tier, long now) {
        return TrapKind.TESLA.damage(tier) * teslaBoost.getAsDouble() * power(record, now)
                * piercing(record, now) * levelScale();
    }

    /**
     * One discharge: the monster given first, then jumps to the nearest monsters not hit yet.
     * Returns the monsters it struck.
     */
    private List<LivingEntity> discharge(ServerLevel level, TrapRecord record, int tier, long now, LivingEntity first,
                                         Vec3 from, float damage) {
        DamageSource source = sourceFor(level, record);
        boolean beam = bonus(record, SkillEffect.GUIDING_BEAM) > 0;
        // At most four monsters per discharge (Simon, 25 October 2026); the chain bolt lifts that
        // limit by its own jumps, or it would do nothing at tier 3 (balance update, 26 October 2026).
        int chain = (int) Math.round(bonus(record, SkillEffect.CHAIN_LIGHTNING));
        int most = (int) Math.round(SkillValues.get("tesla_coil", "maxtargets", 4)) + chain;
        int jumps = Math.min(most, TrapKind.TESLA.chainTargets(tier) + chain);
        double jumpRange = SkillValues.get("tesla_coil", "jump", 4.0);
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
            current = enemies(level, new AABB(at, at).inflate(jumpRange)).stream()
                    .filter(e -> !hit.contains(e))
                    .min(Comparator.comparingDouble(e -> (beam && markedBy(record, e, now) ? 0.0 : 100.0) + e.distanceToSqr(at)))
                    .orElse(null);
        }
        return hit;
    }

    /** Ticks between two discharges of this coil (its engineer close by, the ultimate, the tool bag). */
    private int teslaCooldown(ServerLevel level, BlockPos pos, TrapRecord record, int tier, long now) {
        // Five times slower than at first; with its engineer within six blocks only three times.
        int cooldown = TrapKind.TESLA.cooldownTicks(tier);
        ServerPlayer owner = ownerOf(level, record);
        double near = SkillValues.get("tesla_coil", "nearrange", 6.0);
        if (owner != null && owner.distanceToSqr(Vec3.atCenterOf(pos)) <= near * near) {
            cooldown = (int) Math.round(cooldown * SkillValues.get("tesla_coil", "near", 0.6));
        }
        // The ultimate overcharge drives the tesla coils too (balance update, 26 October 2026).
        if (ultOvercharged(record, now)) {
            cooldown = Math.max(3, (int) (cooldown / ultSpeed()));
        }
        return quicker(record, now, cooldown);
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
    // manned towers (Simon, 26 October 2026)
    // ------------------------------------------------------------------

    /*
     * An engineer climbs into his arrow turret or tesla coil with a right click (not with the
     * remote, a block or a gadget in hand) and sneaks to climb out. Nobody in it: the tower
     * fights on its own at 60 %. Someone in it: the tower only fires when he does, where he
     * looks, at 140 %. The turret alternates its two crossbows and a head hit counts double;
     * the tesla coil sends one discharge at its own pace where he looks (27 October 2026).
     */

    /** Tag of the invisible armour stand a gunner sits on. */
    public static final String SEAT_TAG = "nightsiege_seat";
    /** Tag of a manned turret's arrows (a head hit counts double). */
    static final String TAG_MANNED = "nightsiege_manned";
    /** Base damage of the manned turret's arrows in flight, for the head hit. */
    private static final Map<UUID, Double> MANNED_ARROWS = new HashMap<>();

    /** Someone sitting in a tower. */
    private static final class Seat {
        final String trap;
        final String dimension;
        final BlockPos pos;
        final TrapKind kind;
        final UUID player;
        final UUID stand;
        /** The attack button is held (the client says when it changes). */
        boolean holding;
        long readyAt;
        /** Which crossbow fires next. */
        boolean left;
        long lowNoticeAt;
        float lastPitch = Float.NaN;
        /** The tower's rig: platform, shields and the ballista or the coils and emitter. */
        Rigs.Rig rig;

        Seat(String trap, String dimension, BlockPos pos, TrapKind kind, UUID player, UUID stand) {
            this.trap = trap;
            this.dimension = dimension;
            this.pos = pos;
            this.kind = kind;
            this.player = player;
            this.stand = stand;
        }
    }

    /**
     * The manned arrow turret (Simon, 26 October 2026): a swivel platform with shields and a
     * banner, and in front of the gunner a ballista that follows his aim. Rig space: +Z is
     * where the gunner looks; the platform stands on the block, the ballista turns about a
     * point at his chest.
     */
    private static final List<Rigs.Piece> TURRET_RIG = List.of(
            Rigs.Piece.yaw("spruce_planks", -0.65, 0.0, -0.65, 1.3, 0.1, 1.3),
            Rigs.Piece.yaw("spruce_planks", -0.66, 0.1, -0.2, 0.08, 0.42, 0.75),
            Rigs.Piece.yaw("spruce_planks", 0.58, 0.1, -0.2, 0.08, 0.42, 0.75),
            Rigs.Piece.yaw("iron_block", -0.67, 0.5, -0.21, 0.1, 0.05, 0.77),
            Rigs.Piece.yaw("iron_block", 0.57, 0.5, -0.21, 0.1, 0.05, 0.77),
            Rigs.Piece.yaw("dark_oak_log", -0.6, 0.1, -0.62, 0.07, 1.25, 0.07),
            Rigs.Piece.yaw("red_wool", -0.585, 0.88, -1.0, 0.03, 0.42, 0.38),
            Rigs.Piece.yaw("gold_block", -0.59, 0.84, -1.0, 0.04, 0.05, 0.38),
            Rigs.Piece.aim("stripped_dark_oak_log", -0.081, -0.108, 0.27, 0.162, 0.162, 1.552),
            Rigs.Piece.aim("dark_oak_planks", -0.675, -0.081, 1.512, 0.594, 0.108, 0.108),
            Rigs.Piece.aim("dark_oak_planks", 0.081, -0.081, 1.512, 0.594, 0.108, 0.108),
            Rigs.Piece.aim("dark_oak_planks", -1.053, -0.074, 1.323, 0.405, 0.095, 0.095),
            Rigs.Piece.aim("dark_oak_planks", 0.648, -0.074, 1.323, 0.405, 0.095, 0.095),
            Rigs.Piece.aim("white_wool", -1.053, -0.041, 1.31, 2.106, 0.02, 0.02),
            Rigs.Piece.aim("iron_block", -0.034, 0.054, 0.743, 0.068, 0.068, 1.013),
            Rigs.Piece.aim("iron_block", -0.068, 0.02, 1.728, 0.135, 0.135, 0.135),
            Rigs.Piece.aim("iron_block", -0.405, -0.513, 0.837, 0.81, 0.351, 0.054),
            Rigs.Piece.aim("iron_block", -0.108, -0.405, 0.27, 0.216, 0.297, 0.162));

    /**
     * The manned tesla coil: a copper platform, two coils behind the gunner with glowing tips
     * that crackle at each other, and a copper emitter in front that follows his aim.
     */
    private static final List<Rigs.Piece> TESLA_RIG = List.of(
            Rigs.Piece.yaw("cut_copper", -0.6, 0.0, -0.6, 1.2, 0.1, 1.2),
            Rigs.Piece.yaw("copper_block", -0.58, 0.1, -0.55, 0.18, 0.95, 0.18),
            Rigs.Piece.yaw("copper_block", 0.40, 0.1, -0.55, 0.18, 0.95, 0.18),
            Rigs.Piece.yaw("gold_block", -0.62, 0.3, -0.59, 0.26, 0.04, 0.26),
            Rigs.Piece.yaw("gold_block", -0.62, 0.55, -0.59, 0.26, 0.04, 0.26),
            Rigs.Piece.yaw("gold_block", -0.62, 0.8, -0.59, 0.26, 0.04, 0.26),
            Rigs.Piece.yaw("gold_block", 0.36, 0.3, -0.59, 0.26, 0.04, 0.26),
            Rigs.Piece.yaw("gold_block", 0.36, 0.55, -0.59, 0.26, 0.04, 0.26),
            Rigs.Piece.yaw("gold_block", 0.36, 0.8, -0.59, 0.26, 0.04, 0.26),
            Rigs.Piece.yaw("sea_lantern", -0.61, 1.05, -0.58, 0.24, 0.24, 0.24),
            Rigs.Piece.yaw("sea_lantern", 0.37, 1.05, -0.58, 0.24, 0.24, 0.24),
            Rigs.Piece.aim("cut_copper", -0.07, -0.14, 0.3, 0.14, 0.14, 0.65),
            Rigs.Piece.aim("gold_block", -0.1, -0.17, 0.5, 0.2, 0.2, 0.05),
            Rigs.Piece.aim("gold_block", -0.1, -0.17, 0.72, 0.2, 0.2, 0.05),
            Rigs.Piece.aim("sea_lantern", -0.09, -0.16, 0.95, 0.18, 0.18, 0.18),
            Rigs.Piece.aim("iron_block", -0.08, -0.3, 0.2, 0.16, 0.22, 0.12));

    /** Height of the ballista's or emitter's pivot above the tower's top. */
    private static final double AIM_HEIGHT = 0.55;

    /** Trap key → the seat on that tower. */
    private final Map<String, Seat> seats = new HashMap<>();
    /** Player → the seat they are in. */
    private final Map<UUID, Seat> seatOf = new HashMap<>();

    /** True while someone sits in this tower (then it does not fire on its own). */
    private boolean mannedTower(TrapRecord record) {
        return !seats.isEmpty() && seats.containsKey(key(record));
    }

    /** Damage factor of a tower nobody sits in. */
    static double unmanned(TrapKind kind) {
        return SkillValues.get(kind.id(), "unmanned", 0.6);
    }

    /** Damage factor of a tower fired by its gunner. */
    static double manned(TrapKind kind) {
        return SkillValues.get(kind.id(), "manned", 1.4);
    }

    /** True while the player sits in a tower. */
    public boolean isSeated(ServerPlayer player) {
        return seatOf.containsKey(player.getUUID());
    }

    /**
     * Damage factor for a shot at a player (Simon, 27 October 2026: skeletons sniped gunners off
     * their towers in three hits): in the seat arrows and other shots take 70 % less, melee as usual.
     */
    public static float seatShotFactor(ServerPlayer player) {
        TrapService service = current;
        Seat seat = service == null || player == null ? null : service.seatOf.get(player.getUUID());
        if (seat == null) {
            return 1f;
        }
        double guard = Math.max(0.0, Math.min(1.0, SkillValues.get(seat.kind.id(), "seatguard", 0.7)));
        return (float) (1.0 - guard);
    }

    /** A tower's health for the one sitting in it: three times the player's own (`seathealth` 3). */
    private static float towerHealth(ServerPlayer player, TrapRecord record) {
        double k = SkillValues.get(record.kind, "seathealth", 3.0);
        return (float) (player.getMaxHealth() * k);
    }

    /**
     * Damage to an engineer in a tower (Simon, 27 October 2026): the tower takes it, with three
     * times the player's health. When it runs out the tower breaks and throws its gunner off;
     * what is left of the hit then lands on the player. Returns the part the player takes.
     */
    public static float seatAbsorb(ServerPlayer player, float amount) {
        TrapService service = current;
        Seat seat = service == null || player == null || amount <= 0 ? null : service.seatOf.get(player.getUUID());
        if (seat == null || !(player.level() instanceof ServerLevel level)) {
            return amount;
        }
        TrapRecord record = service.find(level, seat.pos);
        if (record == null || record.broken) {
            return amount;
        }
        float max = towerHealth(player, record);
        float left = Math.max(0f, max - record.hpLost);
        float take = Math.min(amount, left);
        record.hpLost += take;
        service.dirty = true;
        Vec3 c = Vec3.atCenterOf(seat.pos);
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.9, c.z, 4, 0.3, 0.2, 0.3, 0.05);
        playAt(level, seat.pos, SoundEvents.ANVIL_LAND, 0.25f, 1.8f);
        float now = Math.max(0f, max - record.hpLost);
        if (now <= 0.01f) {
            // Overrun (Simon, 27 October 2026: nothing breaks any more): the gunner falls out,
            // the tower fights on by itself and takes nobody for 20 s, then it is whole again.
            long lock = Math.round(SkillValues.get("e_longrange", "seatlock", 400));
            record.hpLost = 0f;
            record.seatLockedUntil = level.getGameTime() + lock;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.9, c.z, 14, 0.3, 0.2, 0.3, 0.02);
            playAt(level, seat.pos, SoundEvents.ANVIL_BREAK, 0.8f, 1.2f);
            service.release(level.getServer(), seat);
            actionBar(player, Component.translatable("nightsiege.msg.tower_wrecked",
                    Component.translatable("block.nightsiege." + record.kind), lock / 20));
        } else {
            actionBar(player, Component.translatable("nightsiege.msg.tower_health",
                    String.format(java.util.Locale.ROOT, "%.0f", now), String.format(java.util.Locale.ROOT, "%.0f", max)));
        }
        return amount - take;
    }

    /** For the client test: what is left of the seated tower's health, or -1. */
    public float seatHealthLeft(ServerPlayer player) {
        Seat seat = seatOf.get(player.getUUID());
        if (seat == null || !(player.level() instanceof ServerLevel level)) {
            return -1f;
        }
        TrapRecord record = find(level, seat.pos);
        return record == null ? -1f : Math.max(0f, towerHealth(player, record) - record.hpLost);
    }

    /** True for a seat stand that belongs to a gunner right now (stray ones are removed). */
    public boolean isLiveSeat(Entity entity) {
        for (Seat seat : seats.values()) {
            if (seat.stand.equals(entity.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A right click on an arrow turret or tesla coil: the engineer sits down in it. Returns
     * true when the click was about manning (seated, or told why not).
     */
    public boolean tryMount(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack held) {
        TrapKind kind = ModContent.trapOf(level.getBlockState(pos));
        if (!TowerBlock.mannable(kind) || player.isShiftKeyDown()) {
            return false;
        }
        if (held != null && !held.isEmpty() && (held.is(ModContent.REMOTE)
                || held.getItem() instanceof net.minecraft.world.item.BlockItem
                || !Gadgets.of(held).isEmpty() || (ModContent.STAFF != null && held.is(ModContent.STAFF)))) {
            return false;   // the remote overcharges, blocks build on it, gadgets work as always
        }
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        if (skills.clazz() != SkillClass.ENGINEER && !player.getAbilities().instabuild) {
            actionBar(player, Component.translatable("nightsiege.msg.tower_engineer_only"));
            return true;
        }
        TrapRecord record = find(level, pos);
        if (record == null) {
            // Placed before the mod tracked it, or the save was lost: adopt it.
            onPlaced(level, pos, player, kind);
            record = find(level, pos);
            if (record == null) {
                return false;
            }
        }
        Seat taken = seats.get(key(record));
        if (taken != null) {
            if (!taken.player.equals(player.getUUID())) {
                actionBar(player, Component.translatable("nightsiege.msg.tower_taken"));
            }
            return true;
        }
        long lockLeft = record.seatLockedUntil - level.getGameTime();
        if (lockLeft > 0) {
            actionBar(player, Component.translatable("nightsiege.msg.tower_recovering",
                    Component.translatable("block.nightsiege." + record.kind), (lockLeft + 19) / 20));
            return true;
        }
        BlockPos above = pos.above();
        if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()
                || !level.getBlockState(above.above()).getCollisionShape(level, above.above()).isEmpty()) {
            actionBar(player, Component.translatable("nightsiege.msg.tower_no_room"));
            return true;
        }
        Seat old = seatOf.get(player.getUUID());
        if (old != null) {
            release(level.getServer(), old);
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        dressSeat(stand, kind);
        stand.setYRot(player.getYRot());
        stand.setYBodyRot(player.getYRot());
        level.addFreshEntity(stand);
        // Tagged only now: the load event removes every tagged seat it does not know (strays).
        stand.addTag(SEAT_TAG);
        if (!player.startRiding(stand, true, true)) {
            stand.discard();
            actionBar(player, Component.translatable("nightsiege.msg.tower_no_room"));
            return true;
        }
        Seat seat = new Seat(key(record), record.dimension, pos.immutable(), kind, player.getUUID(), stand.getUUID());
        seat.readyAt = level.getGameTime() + 5;
        Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        seat.rig = Rigs.spawn(level, top, top.add(0, AIM_HEIGHT, 0), kind == TrapKind.TESLA ? TESLA_RIG : TURRET_RIG,
                player.getYRot(), null);
        seats.put(seat.trap, seat);
        seatOf.put(seat.player, seat);
        setLook(level, pos, true);
        playAt(level, pos, SoundEvents.CHAIN_PLACE, 1.0f, 1.2f);
        actionBar(player, Component.translatable(kind == TrapKind.TESLA
                ? "nightsiege.msg.tower_manned_tesla" : "nightsiege.msg.tower_manned_turret"));
        return true;
    }

    /** The seat: a small invisible marker armour stand with the tower's two weapons in its hands. */
    private static void dressSeat(ArmorStand stand, TrapKind kind) {
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setShowArms(true);
        stand.setNoBasePlate(true);
        ((com.mki.nightsiege.mixin.ArmorStandAccessor) stand).nightsiege$setSmall(true);
        ((com.mki.nightsiege.mixin.ArmorStandAccessor) stand).nightsiege$setMarker(true);
        // The weapons are the tower's rig (block displays), the stand is only the seat.
    }

    /** Shows or hides a tower's manned look (seat, plates, banner or twin coils). */
    private static void setLook(ServerLevel level, BlockPos pos, boolean manned) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TowerBlock && state.getValue(TowerBlock.MANNED) != manned) {
            level.setBlock(pos, state.setValue(TowerBlock.MANNED, manned), 3);
        }
    }

    /** The player pressed or let go of the attack button while sitting in a tower. */
    public void onManAction(ServerPlayer player, boolean hold) {
        Seat seat = seatOf.get(player.getUUID());
        if (seat != null) {
            seat.holding = hold;
        }
    }

    /** Everybody out of the towers (end of the night, server stop). */
    public void releaseAll(MinecraftServer server) {
        for (Seat seat : new ArrayList<>(seats.values())) {
            release(server, seat);
        }
        MANNED_ARROWS.clear();
    }

    private void release(MinecraftServer server, Seat seat) {
        seats.remove(seat.trap);
        seatOf.remove(seat.player);
        ServerLevel level = server == null ? null : SiegeManager.levelByName(server, seat.dimension);
        if (level == null) {
            return;
        }
        Entity stand = level.getEntity(seat.stand);
        ServerPlayer player = server.getPlayerList().getPlayer(seat.player);
        if (player != null && stand != null && player.getVehicle() == stand) {
            player.stopRiding();
        }
        if (stand != null) {
            stand.discard();
        }
        Rigs.remove(level, seat.rig);
        setLook(level, seat.pos, false);
        playAt(level, seat.pos, SoundEvents.CHAIN_PLACE, 0.7f, 0.7f);
    }

    /** Every tick: who still sits where, the weapons turn with the gunner, and the shots. */
    private void tickSeats(MinecraftServer server) {
        if (seats.isEmpty()) {
            if (MANNED_ARROWS.size() > 256) {
                MANNED_ARROWS.clear();   // arrows that stuck in walls never reported back
            }
            return;
        }
        for (Seat seat : new ArrayList<>(seats.values())) {
            ServerLevel level = SiegeManager.levelByName(server, seat.dimension);
            ServerPlayer player = server.getPlayerList().getPlayer(seat.player);
            Entity stand = level == null ? null : level.getEntity(seat.stand);
            TrapRecord record = level == null ? null : find(level, seat.pos);
            if (level == null || player == null || stand == null || !player.isAlive() || player.getVehicle() != stand
                    || record == null || ModContent.trapOf(level.getBlockState(seat.pos)) != seat.kind) {
                release(server, seat);
                continue;
            }
            float yaw = player.getYRot();
            float pitch = Math.max(-45f, Math.min(45f, player.getXRot()));
            stand.setYRot(yaw);
            long now = level.getGameTime();
            if (seat.rig != null) {
                Rigs.turn(level, seat.rig, yaw, pitch);
                if (seat.kind == TrapKind.TESLA && now % 8 == 0) {
                    // The coil tips crackle at each other while someone sits in the coil.
                    Vec3 top = new Vec3(seat.pos.getX() + 0.5, seat.pos.getY() + 1.0, seat.pos.getZ() + 0.5);
                    spark(level, Rigs.world(top, yaw, -0.49, 1.17, -0.46), Rigs.world(top, yaw, 0.49, 1.17, -0.46));
                }
            }
            if (seat.holding && now >= seat.readyAt && !canFire(record)) {
                seat.readyAt = now + 20;
                actionBar(player, Component.translatable("nightsiege.msg.tower_busy"));
            } else if (seat.holding && now >= seat.readyAt) {
                int tier = tierOf(record);
                if (seat.kind == TrapKind.TURRET) {
                    fireManned(level, seat, record, player, tier, now);
                } else {
                    beamPulse(level, seat, record, player, tier, now);
                }
            }
        }
    }

    /** The manned turret: one arrow where the gunner looks, the two crossbows in turn. */
    private void fireManned(ServerLevel level, Seat seat, TrapRecord record, ServerPlayer gunner, int tier, long now) {
        Vec3 look = gunner.getLookAngle();
        Vec3 eye = gunner.getEyePosition();
        // The bolt leaves the ballista's head and meets the gunner's line of sight 32 blocks out.
        Vec3 pivot = new Vec3(seat.pos.getX() + 0.5, seat.pos.getY() + 1.0 + AIM_HEIGHT, seat.pos.getZ() + 0.5);
        Vec3 out = pivot.add(look.scale(1.45 * 1.35));   // the ballista is a third bigger since 27 October 2026
        Vec3 aim = eye.add(look.scale(32.0)).subtract(out);
        Arrow arrow = new Arrow(level, out.x, out.y, out.z, new ItemStack(Items.ARROW), null);
        arrow.entityTags().add("nightsiege_arrow");   // keeps the archer skills off it
        arrow.entityTags().add("nightsiege_turret");  // and the crate gains
        arrow.entityTags().add(TAG_MANNED);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        double base = turretDamage(record, tier, now) * manned(TrapKind.TURRET);
        arrow.setBaseDamage(base);
        if (bonus(record, SkillEffect.CHARGED_ARROWS) > 0) {
            arrow.entityTags().add(TAG_CHARGED);
        }
        if (bonus(record, SkillEffect.TARGET_BLAST) > 0) {
            arrow.entityTags().add(TAG_BLAST);
        }
        arrow.setOwner(gunner);
        arrow.shoot(aim.x, aim.y, aim.z, 3.2f, 0.5f);
        level.addFreshEntity(arrow);
        afterShot(level, seat.pos, record);
        MANNED_ARROWS.put(arrow.getUUID(), base);
        level.playSound(null, seat.pos, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.9f, 0.7f);
        level.sendParticles(ParticleTypes.CRIT, out.x, out.y, out.z, 3, 0.05, 0.05, 0.05, 0.05);
        if (ultOvercharged(record, now)) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, out.x, out.y, out.z, 3, 0.2, 0.2, 0.2, 0.05);
        }
        seat.readyAt = now + turretCooldown(record, tier, now);
        dirty = true;
    }

    /**
     * The manned tesla coil (Simon, 27 October 2026: slow again and harder): while the button is
     * held, one discharge leaves the emitter at the coil's own pace as soon as a monster is on
     * the gunner's line of sight, with the manned factor on every hit, and jumps on like the
     * coil's own discharge. Nothing in sight: it only crackles and keeps its charge.
     */
    private void beamPulse(ServerLevel level, Seat seat, TrapRecord record, ServerPlayer gunner, int tier, long now) {
        Vec3 look = gunner.getLookAngle();
        Vec3 eye = gunner.getEyePosition();
        double range = TrapKind.TESLA.radius(tier) + bonus(record, SkillEffect.TOWER_RANGE)
                + SkillValues.get("tesla_coil", "beamrange", 4.0);
        // Bosses and champions further away still (28 October 2026): the beam looks as far as that.
        Vec3 end = eye.add(look.scale(range * searchFactor()));
        // The bolt leaves the emitter's glowing tip.
        Vec3 from = new Vec3(seat.pos.getX() + 0.5, seat.pos.getY() + 1.0 + AIM_HEIGHT, seat.pos.getZ() + 0.5).add(look.scale(1.05));
        double grace = SkillValues.get("tesla_coil", "aimgrace", 0.5);
        LivingEntity first = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : enemies(level, new AABB(eye, end).inflate(1.0 + grace))) {
            java.util.Optional<Vec3> at = e.getBoundingBox().inflate(grace).clip(eye, end);
            if (at.isEmpty() || shielded(e)) {
                continue;
            }
            double d = at.get().distanceToSqr(eye);
            double reach = reachOf(record, e, range, now);
            if (d < best && d <= reach * reach && clearShot(level, eye, at.get(), seat.pos)) {
                best = d;
                first = e;
            }
        }
        if (first == null) {
            if (now % 8 == 0) {
                spark(level, from, eye.add(look.scale(3.0)));   // it crackles into the air, the charge stays
            }
            return;
        }
        PlayerSkills skills = save.get().skillsOf(gunner.getUUID().toString());
        double price = SkillValues.get("tesla_coil", "boltcost", 0.0);
        if (price > 0 && !payQuiet(gunner, cost(skills, price))) {
            if (now >= seat.lowNoticeAt) {
                seat.lowNoticeAt = now + 20;
                actionBar(gunner, Component.translatable("nightsiege.msg.beam_empty"));
            }
            return;
        }
        float damage = (float) (teslaDamage(record, tier, now) * manned(TrapKind.TESLA));
        discharge(level, record, tier, now, first, from, damage);
        afterShot(level, seat.pos, record);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, first.getX(), first.getY(0.5), first.getZ(), 14, 0.3, 0.5, 0.3, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 6, 0.1, 0.1, 0.1, 0.05);
        playAt(level, seat.pos, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.6f);
        seat.readyAt = now + teslaCooldown(level, seat.pos, record, tier, now);
        dirty = true;
    }

    /** Takes energy without the "too little" line (the beam says it its own way). */
    private boolean payQuiet(ServerPlayer player, double cost) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        double have = energyOf(player);
        if (have + 1e-6 < cost) {
            return false;
        }
        energy.put(player.getUUID(), Math.max(0.0, have - cost));
        showEnergy(player);
        return true;
    }

    /** An arrow of a manned turret struck a creature: on the head it counts double. */
    public static void onMannedArrowHit(AbstractArrow arrow, net.minecraft.world.phys.EntityHitResult hit) {
        if (!arrow.entityTags().contains(TAG_MANNED)) {
            return;
        }
        Double base = MANNED_ARROWS.remove(arrow.getUUID());
        if (base == null || !(hit.getEntity() instanceof LivingEntity target) || !(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        double head = target.getY() + target.getBbHeight() * SkillValues.get("arrow_turret", "headline", 0.75);
        if (hit.getLocation().y < head) {
            return;
        }
        arrow.setBaseDamage(base * SkillValues.get("arrow_turret", "headshot", 2.0));
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 12, 0.25, 0.2, 0.25, 0.3);
        if (arrow.getOwner() instanceof ServerPlayer gunner) {
            level.playSound(null, gunner.blockPosition(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.6f, 1.4f);
            actionBar(gunner, Component.translatable("nightsiege.msg.headshot"));
        }
    }

    // ------------------------------------------------------------------
    // the alchemist's potion zones (Simon, 26 October 2026)
    // ------------------------------------------------------------------

    /** The potion each alchemist picked in the menu. */
    private final Map<UUID, BrewKind> brewChoice = new HashMap<>();
    /**
     * When each alchemist may throw each potion again (Simon, 28 October 2026: the pause holds
     * only the potion just thrown, not all of them).
     */
    private final Map<UUID, Map<BrewKind, Long>> zoneReady = new HashMap<>();
    /** The short lock after any throw, so three potions never leave in the same moment. */
    private final Map<UUID, Long> zoneLock = new HashMap<>();
    /** When each alchemist may throw the next acid flask. */
    private final Map<UUID, Long> acidReady = new HashMap<>();

    /** The potion the alchemist throws: his pick while he has it, else his first one. */
    private BrewKind brewOf(ServerPlayer player, PlayerSkills skills) {
        BrewKind picked = brewChoice.get(player.getUUID());
        return picked != null && picked.usable(skills) ? picked : BrewKind.firstUnlocked(skills);
    }

    /** Ticks a potion pauses after its throw: its node's value (the old shared one as fallback). */
    private static long brewPause(BrewKind kind) {
        return Math.round(SkillValues.get(kind.nodeId(), "pause", SkillValues.get("potion_tower", "zonepause", 400)));
    }

    /** Ticks until this alchemist may throw this potion again; 0 when ready. */
    public int brewCooldownLeft(ServerPlayer player, BrewKind kind, long now) {
        Map<BrewKind, Long> own = zoneReady.get(player.getUUID());
        Long ready = own == null || kind == null ? null : own.get(kind);
        return ready == null || ready <= now ? 0 : (int) (ready - now);
    }

    /** Sneak + right click with the remote: the alchemist's potion menu. */
    public void openBrewMenu(ServerPlayer player, PlayerSkills skills) {
        com.mki.nightsiege.core.BrewMenuData data = new com.mki.nightsiege.core.BrewMenuData();
        BrewKind current = brewOf(player, skills);
        data.selected = current == null ? "" : current.id();
        long now = player.level().getGameTime();
        data.cooldown = current == null ? 0 : (brewCooldownLeft(player, current, now) + 19) / 20;
        data.zoneSeconds = (int) Math.round(SkillValues.get("potion_tower", "zonetime", 160) / 20.0);
        data.pauseSeconds = (int) Math.round(SkillValues.get("potion_tower", "zonepause", 400) / 20.0);
        data.reach = (int) Math.round(SkillValues.get("potion_tower", "zonerange", 10));
        for (BrewKind kind : BrewKind.values()) {
            com.mki.nightsiege.core.BrewMenuData.Entry entry = new com.mki.nightsiege.core.BrewMenuData.Entry();
            entry.id = kind.id();
            entry.open = kind.usable(skills);
            entry.need = kind.nodeId();   // its own node since 28 October 2026
            data.brews.add(entry);
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new CoreNetworking.ScreenPayload("brewmenu", Json.toJson(data)));
    }

    /**
     * The alchemist's wheel (Simon, 27 October 2026): the seven potions, the learned ones
     * pickable; Fähigkeit 2 throws the one picked. Null for everyone else.
     */
    public com.mki.nightsiege.core.WheelData brewWheel(ServerPlayer player) {
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        if (skills.clazz() != SkillClass.ENGINEER || !SkillTree.SUB_ALCHEMIST.equals(tree.subclassOf(skills))) {
            return null;
        }
        com.mki.nightsiege.core.WheelData wheel = new com.mki.nightsiege.core.WheelData();
        wheel.title = "nightsiege.wheel.brews";
        wheel.hint = "nightsiege.wheel.brews.hint";
        BrewKind current = brewOf(player, skills);
        for (BrewKind kind : BrewKind.values()) {
            com.mki.nightsiege.core.WheelData.Option o = new com.mki.nightsiege.core.WheelData.Option();
            o.id = kind.id();
            o.color = kind.color();
            o.name = "nightsiege.brew." + kind.id();
            o.open = kind.usable(skills);
            o.sub = !o.open ? "nightsiege.wheel.locked"
                    : kind.forPlayers() ? "nightsiege.wheel.brew_players" : "nightsiege.wheel.brew_monsters";
            o.current = kind == current;
            wheel.options.add(o);
        }
        return wheel;
    }

    /** A short press of Fähigkeit 3 (alchemist): the next learned potion. False for everyone else. */
    public boolean nextBrew(ServerPlayer player) {
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        if (skills.clazz() != SkillClass.ENGINEER || !SkillTree.SUB_ALCHEMIST.equals(tree.subclassOf(skills))) {
            return false;
        }
        BrewKind current = brewOf(player, skills);
        BrewKind[] all = BrewKind.values();
        int start = current == null ? -1 : current.ordinal();
        for (int i = 1; i <= all.length; i++) {
            BrewKind next = all[Math.floorMod(start + i, all.length)];
            if (next.usable(skills)) {
                onBrewChoice(player, next.id());
                return true;
            }
        }
        actionBar(player, Component.translatable("nightsiege.msg.brew_locked"));
        return true;
    }

    /** A pick in the potion menu. */
    public void onBrewChoice(ServerPlayer player, String id) {
        BrewKind kind = BrewKind.byId(id);
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        if (kind == null || !kind.usable(skills)) {
            actionBar(player, Component.translatable("nightsiege.msg.brew_locked"));
            return;
        }
        brewChoice.put(player.getUUID(), kind);
        actionBar(player, Component.translatable("nightsiege.msg.brew_chosen",
                Component.translatable("nightsiege.brew." + kind.id())));
    }

    /** The bar key of a potion (27 October 2026): the alchemist throws it at once. */
    public void throwBrew(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        if (SkillTree.SUB_ALCHEMIST.equals(tree.subclassOf(skills)) || player.getAbilities().instabuild) {
            throwZone(level, player, skills, level.getGameTime());
        }
    }

    /** The block or monster looked at within reach, whichever is nearer; null when nothing is. */
    private static Vec3 throwSpot(ServerLevel level, ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 spot = null;
        net.minecraft.world.phys.HitResult hit = player.pick(reach, 0f, false);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            spot = hit.getLocation();
        }
        LivingEntity target = CombatEffects.lookTarget(level, player, reach);
        if (target != null && (spot == null || target.distanceToSqr(eye) < spot.distanceToSqr(eye))) {
            spot = target.position();
        }
        return spot;
    }

    /** Sends a thrown flask in a low arc onto the spot. */
    private static void lobFlask(ServerLevel level, KitProjectile shot, Vec3 spot) {
        Vec3 from = shot.position();
        double dx = spot.x - from.x;
        double dy = spot.y - from.y;
        double dz = spot.z - from.z;
        int flight = Math.max(6, flightTicks(Math.sqrt(dx * dx + dz * dz)) / 2);
        shot.setDeltaMovement(ballistic(dx, dy, dz, flight, 0.03));
        level.addFreshEntity(shot);
    }

    private void throwZone(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now) {
        boolean creative = player.getAbilities().instabuild;
        SiegeManager m = SiegeManager.get();
        if (!creative && (m == null || !m.isDefending(player))) {
            actionBar(player, Component.translatable("nightsiege.msg.zone_night_only"));
            return;
        }
        BrewKind brew = brewOf(player, skills);
        if (brew == null) {
            actionBar(player, Component.translatable("nightsiege.msg.zone_none"));
            return;
        }
        // Only this potion waits out its pause (Simon, 28 October 2026); the others are ready
        // after the short lock of a throw.
        int left = brewCooldownLeft(player, brew, now);
        if (left > 0 && !creative) {
            actionBar(player, Component.translatable("nightsiege.msg.zone_wait_brew",
                    Component.translatable("nightsiege.brew." + brew.id()), (left + 19) / 20));
            return;
        }
        Long lock = zoneLock.get(player.getUUID());
        if (lock != null && now < lock && !creative) {
            return;
        }
        double reach = SkillValues.get("potion_tower", "zonerange", 10);
        Vec3 spot = throwSpot(level, player, reach);
        if (spot == null) {
            actionBar(player, Component.translatable("nightsiege.msg.zone_far", (int) Math.round(reach)));
            return;
        }
        ItemStack look = new ItemStack(Items.LINGERING_POTION);
        look.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new PotionContents(
                java.util.Optional.empty(), java.util.Optional.of(brew.color()), List.of(), java.util.Optional.empty()));
        KitProjectile shot = new KitProjectile(level, player, look, KitProjectile.ZONE);
        shot.setData(brew.id());
        lobFlask(level, shot, spot);
        zoneReady.computeIfAbsent(player.getUUID(), k -> new java.util.EnumMap<>(BrewKind.class)).put(brew, now + brewPause(brew));
        zoneLock.put(player.getUUID(), now + Math.round(SkillValues.get("potion_tower", "zonelock", 20)));
        level.playSound(null, player.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 1.0f, 0.8f);
        actionBar(player, Component.translatable("nightsiege.msg.zone_thrown",
                Component.translatable("nightsiege.brew." + brew.id())));
    }

    // ------------------------------------------------------------------
    // the alchemist's acid flask (Simon, 28 October 2026: one more ability for the potions he gave up)
    // ------------------------------------------------------------------

    /** Ticks until this alchemist may throw the next acid flask; 0 when ready. */
    public int acidCooldownLeft(ServerPlayer player, long now) {
        Long ready = acidReady.get(player.getUUID());
        return ready == null || ready <= now ? 0 : (int) (ready - now);
    }

    /** The bar key of the acid flask: it flies onto the block or monster looked at and bursts there. */
    public void throwAcid(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        boolean creative = player.getAbilities().instabuild;
        if (!SkillTree.has(skills, "e_acid_flask") && !creative) {
            actionBar(player, Component.translatable("nightsiege.msg.acid_locked"));
            return;
        }
        SiegeManager m = SiegeManager.get();
        if (!creative && (m == null || !m.isDefending(player))) {
            actionBar(player, Component.translatable("nightsiege.msg.zone_night_only"));
            return;
        }
        long now = level.getGameTime();
        int left = acidCooldownLeft(player, now);
        if (left > 0 && !creative) {
            actionBar(player, Component.translatable("nightsiege.msg.acid_wait", (left + 19) / 20));
            return;
        }
        double reach = SkillValues.get("e_acid_flask", "range", 14);
        Vec3 spot = throwSpot(level, player, reach);
        if (spot == null) {
            actionBar(player, Component.translatable("nightsiege.msg.zone_far", (int) Math.round(reach)));
            return;
        }
        ItemStack look = new ItemStack(Items.SPLASH_POTION);
        look.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new PotionContents(
                java.util.Optional.empty(), java.util.Optional.of(ACID_COLOR), List.of(), java.util.Optional.empty()));
        KitProjectile shot = new KitProjectile(level, player, look, KitProjectile.ACID);
        lobFlask(level, shot, spot);
        acidReady.put(player.getUUID(), now + Math.round(SkillValues.get("e_acid_flask", "pause", 240)));
        level.playSound(null, player.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** Colour of the acid and its flask. */
    static final int ACID_COLOR = 0xB5E61D;

    /**
     * The acid flask burst: every monster around takes a potion thrower's hit times the flask's
     * factor, crawls for a moment, and takes more damage from everything while the acid eats.
     */
    public void onAcidLanded(ServerLevel level, KitProjectile shot, Vec3 at) {
        Entity owner = shot.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        long now = level.getGameTime();
        double r = SkillValues.get("e_acid_flask", "radius", 3.0);
        double damage = towerHit(TrapKind.BREWER) * SkillValues.get("e_acid_flask", "dmg", 1.2);
        double acid = SkillValues.get("e_acid_flask", "acid", 0.25);
        int ticks = (int) Math.round(SkillValues.get("e_acid_flask", "time", 120));
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : around(level, at, r)) {
            victim.invulnerableTime = 0;
            victim.hurtServer(level, source, (float) damage);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Math.min(ticks, 60), 0));
            marker.mark(victim, acid, now + ticks, false);
            level.sendParticles(ACID_DUST, victim.getX(), victim.getY(0.6), victim.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
        }
        level.sendParticles(ACID_DUST, at.x, at.y + 0.3, at.z, 60, r * 0.45, 0.3, r * 0.45, 0.0);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.3, at.z, 24, r * 0.3, 0.1, r * 0.3, 0.1);
        level.playSound(null, BlockPos.containing(at), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    private static final net.minecraft.core.particles.DustParticleOptions ACID_DUST =
            new net.minecraft.core.particles.DustParticleOptions(ACID_COLOR, 1.4f);

    // ------------------------------------------------------------------
    // the engineer's ability bar (28 October 2026): the escape for all, potions and acid for the alchemist
    // ------------------------------------------------------------------

    /**
     * The engineer's boxes on the ability bar, null for everyone else: the escape first (R for
     * every engineer), then the alchemist's learned potions with their own pauses, then his acid flask.
     */
    public List<com.mki.nightsiege.core.UltState.Slot> engineerSlots(ServerPlayer player, PlayerSkills skills, long now) {
        if (skills == null || skills.clazz() != SkillClass.ENGINEER || !skills.hasClass()) {
            return null;
        }
        List<com.mki.nightsiege.core.UltState.Slot> out = new ArrayList<>();
        out.add(EngineerEscape.slot(player, now));
        if (!SkillTree.SUB_ALCHEMIST.equals(tree.subclassOf(skills))) {
            return out;
        }
        // One key for the potions (Simon 30.10.: "pfeile und die tränke auf eine taste … dann kann man
        // scrollen um zu ändern"): it shows the chosen one; held, the wheel changes it; a tap throws it.
        BrewKind current = brewOf(player, skills);
        if (current != null && !BrewKind.learned(skills).isEmpty()) {
            com.mki.nightsiege.core.UltState.Slot slot = new com.mki.nightsiege.core.UltState.Slot();
            slot.id = BREW_SLOT;
            slot.color = current.color();
            slot.name = "nightsiege.brew." + current.id();
            slot.sub = "nightsiege.wheel.scroll_hint";
            slot.cooldownTicks = brewCooldownLeft(player, current, now);
            slot.totalTicks = (int) brewPause(current);
            slot.scroll = true;
            out.add(slot);
        }
        if (SkillTree.has(skills, "e_acid_flask")) {
            com.mki.nightsiege.core.UltState.Slot acid = new com.mki.nightsiege.core.UltState.Slot();
            acid.id = "acid";
            acid.icon = "minecraft:experience_bottle";
            acid.name = "nightsiege.ability.acid";
            acid.sub = "nightsiege.ability.acid.sub";
            acid.cooldownTicks = acidCooldownLeft(player, now);
            acid.totalTicks = (int) Math.round(SkillValues.get("e_acid_flask", "pause", 240));
            out.add(acid);
        }
        return out;
    }

    /** The id of the alchemist's one potion key on the bar. */
    public static final String BREW_SLOT = "brew";

    /** The potion key held and the wheel turned: the next (or the one before) of his learned potions. */
    public void cycleBrew(ServerPlayer player, int direction) {
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        List<BrewKind> learned = BrewKind.learned(skills);
        if (learned.isEmpty()) {
            return;
        }
        BrewKind current = brewOf(player, skills);
        int at = Math.max(0, learned.indexOf(current));
        BrewKind next = learned.get(Math.floorMod(at + (direction < 0 ? -1 : 1), learned.size()));
        onBrewChoice(player, next.id());
    }

    /** A bar key of an engineer: the escape, the acid flask or a potion. False when it is none of his. */
    public boolean onEngineerSlot(ServerPlayer player, String id) {
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        if (skills.clazz() != SkillClass.ENGINEER || id == null) {
            return false;
        }
        if (EngineerEscape.ID.equals(id)) {
            EngineerEscape.fire(player, this);
            return true;
        }
        if (BREW_SLOT.equals(id)) {
            throwBrew(player);   // a tap: the chosen potion
            return true;
        }
        if ("acid".equals(id)) {
            throwAcid(player);
            return true;
        }
        BrewKind kind = BrewKind.byId(id);
        if (kind != null) {
            onBrewChoice(player, id);
            throwBrew(player);
            return true;
        }
        return false;
    }

    /** End of the night: the zone pauses, the acid flask and the escape start fresh. */
    public void resetZones() {
        zoneReady.clear();
        zoneLock.clear();
        acidReady.clear();
        EngineerEscape.reset();
        WarriorDash.reset();
        ArrowRain.reset();
    }

    // ------------------------------------------------------------------
    // the demolition expert's bird's-eye view (Simon, 26 October 2026)
    // ------------------------------------------------------------------

    /** When each player may use each fire again: player → mode id → game time. */
    private final Map<UUID, Map<String, Long>> spotReady = new HashMap<>();

    /** The spyglass of the kit: the bird's-eye view opens on the client. */
    public void openSpotter(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        boolean creative = player.getAbilities().instabuild;
        if (!creative && !SkillTree.SUB_DEMOLITION.equals(tree.subclassOf(skills))) {
            actionBar(player, Component.translatable("nightsiege.msg.spotter_demolition_only"));
            return;
        }
        SiegeManager m = SiegeManager.get();
        if (!creative && (m == null || !m.isDefending(player))) {
            actionBar(player, Component.translatable("nightsiege.msg.spotter_night_only"));
            return;
        }
        com.mki.nightsiege.core.SpotterData data = new com.mki.nightsiege.core.SpotterData();
        BlockPos center = spotCenter(level, player);
        data.centerX = center.getX();
        data.centerY = center.getY();
        data.centerZ = center.getZ();
        data.radius = (int) Math.round(SkillValues.get(TrapKind.MORTAR.id(), "spotradius", 48));
        double dx = player.getX() - center.getX();
        double dz = player.getZ() - center.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double scale = d > data.radius ? data.radius / d : 1.0;
        data.startX = center.getX() + dx * scale;
        data.startZ = center.getZ() + dz * scale;
        data.mortars = ownMortars(level, player).size();
        long now = level.getGameTime();
        Map<String, Long> ready = spotReady.getOrDefault(player.getUUID(), Map.of());
        for (com.mki.nightsiege.core.MortarMode mode : com.mki.nightsiege.core.MortarMode.values()) {
            com.mki.nightsiege.core.SpotterData.Mode entry = new com.mki.nightsiege.core.SpotterData.Mode();
            entry.id = mode.id();
            entry.open = mode.unlocked(skills) || creative;
            long at = ready.getOrDefault(mode.id(), 0L);
            entry.wait = at > now ? (int) ((at - now + 19) / 20) : 0;
            entry.pause = mode.pauseTicks() / 20;
            entry.spread = switch (mode) {
                case POINT -> TrapKind.MORTAR.splash(1);
                case BARRAGE -> 1.5;
                case AREA -> SkillValues.get("e_area_fire", "radius", 5);
                case SMOKE -> SkillValues.get("e_smoke_salvo", "radius", 5);
                case FIRE -> 2.5;
            };
            data.modes.add(entry);
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new CoreNetworking.ScreenPayload("spotter", Json.toJson(data)));
    }

    /** The middle of the view: the core in the player's world, else where the player stands. */
    private static BlockPos spotCenter(ServerLevel level, ServerPlayer player) {
        SiegeManager m = SiegeManager.get();
        if (fieldFight(player)) {
            return player.blockPosition();   // in a dungeon or at the camp: the view opens over the fight (28 October 2026)
        }
        CoreRecord core = m == null ? null : m.mainCore();
        if (core != null && SiegeManager.dimensionName(level).equals(core.dimension)) {
            return new BlockPos(core.x, core.y, core.z);
        }
        return player.blockPosition();
    }

    private List<TrapRecord> ownMortars(ServerLevel level, ServerPlayer player) {
        String owner = player.getUUID().toString();
        String dim = SiegeManager.dimensionName(level);
        List<TrapRecord> out = new ArrayList<>();
        for (TrapRecord r : traps()) {
            if (TrapKind.MORTAR.id().equals(r.kind) && r.dimension.equals(dim) && !r.broken
                    && (r.owner.equals(owner) || player.getAbilities().instabuild)) {
                out.add(r);
            }
        }
        return out;
    }

    /** True when this mortar's shells reach the spot. */
    private boolean reaches(TrapRecord r, Vec3 spot) {
        Vec3 at = Vec3.atCenterOf(new BlockPos(r.x, r.y, r.z));
        double reach = TrapKind.MORTAR.radius(tierOf(r)) + bonus(r, SkillEffect.TOWER_RANGE);
        double dx = at.x - spot.x;
        double dz = at.z - spot.z;
        return dx * dx + dz * dz <= reach * reach;
    }

    /**
     * A click in the bird's-eye view: "mode:x:y:z", for the barrage "barrage:x:y:z:x2:y2:z2"
     * (the spots are the blocks clicked; the shells land on top of them).
     */
    public void onSpotAction(ServerPlayer player, String argument) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        String[] parts = argument.split(":");
        // The camera of the view (27 October 2026): the chunks sent follow it while it is open.
        if (parts.length >= 1 && parts[0].equals("close")) {
            SpotterChunks.report(player, null, null);
            return;
        }
        if (parts.length >= 3 && parts[0].equals("cam")) {
            try {
                SpotterChunks.report(player, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            } catch (NumberFormatException ignored) {
                // a broken report changes nothing
            }
            return;
        }
        com.mki.nightsiege.core.MortarMode mode = parts.length == 0 ? null : com.mki.nightsiege.core.MortarMode.byId(parts[0]);
        if (mode == null || parts.length < 4 || (mode == com.mki.nightsiege.core.MortarMode.BARRAGE && parts.length < 7)) {
            return;
        }
        Vec3 a;
        Vec3 b;
        try {
            a = new Vec3(Integer.parseInt(parts[1]) + 0.5, Integer.parseInt(parts[2]) + 1.0, Integer.parseInt(parts[3]) + 0.5);
            b = parts.length >= 7
                    ? new Vec3(Integer.parseInt(parts[4]) + 0.5, Integer.parseInt(parts[5]) + 1.0, Integer.parseInt(parts[6]) + 0.5)
                    : a;
        } catch (NumberFormatException e) {
            return;
        }
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        boolean creative = player.getAbilities().instabuild;
        if (!creative && !SkillTree.SUB_DEMOLITION.equals(tree.subclassOf(skills))) {
            return;
        }
        SiegeManager m = SiegeManager.get();
        if (!creative && (m == null || !m.isDefending(player))) {
            actionBar(player, Component.translatable("nightsiege.msg.spotter_night_only"));
            return;
        }
        if (!creative && !mode.unlocked(skills)) {
            actionBar(player, Component.translatable("nightsiege.msg.spotter_locked"));
            return;
        }
        BlockPos center = spotCenter(level, player);
        double radius = SkillValues.get(TrapKind.MORTAR.id(), "spotradius", 48) + 2.0;
        for (Vec3 p : new Vec3[] {a, b}) {
            double dx = p.x - center.getX();
            double dz = p.z - center.getZ();
            if (dx * dx + dz * dz > radius * radius) {
                actionBar(player, Component.translatable("nightsiege.msg.spotter_far"));
                return;
            }
        }
        long now = level.getGameTime();
        Map<String, Long> ready = spotReady.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        long at = ready.getOrDefault(mode.id(), 0L);
        if (mode.pauseTicks() > 0 && now < at && !creative) {
            actionBar(player, Component.translatable("nightsiege.msg.spotter_wait", (at - now + 19) / 20));
            return;
        }
        List<TrapRecord> mortars = ownMortars(level, player);
        if (mortars.isEmpty()) {
            actionBar(player, Component.translatable("nightsiege.msg.spotter_no_mortar"));
            return;
        }
        int fired = switch (mode) {
            case POINT -> pointFire(level, mortars, a, now);
            case BARRAGE -> barrage(level, mortars, a, b);
            case AREA -> scatter(level, mortars, a, (int) Math.round(SkillValues.get("e_area_fire", "shells", 8)),
                    SkillValues.get("e_area_fire", "radius", 5), SkillValues.get("e_area_fire", "dmg", 0.8), "");
            case SMOKE -> scatter(level, mortars, a, (int) Math.round(SkillValues.get("e_smoke_salvo", "shells", 2)),
                    1.5, 0.0, "smoke");
            case FIRE -> scatter(level, mortars, a, (int) Math.round(SkillValues.get("e_fire_salvo", "shells", 3)),
                    1.5, SkillValues.get("e_fire_salvo", "dmg", 0.6), "fire");
        };
        if (fired <= 0) {
            actionBar(player, Component.translatable("nightsiege.msg.mortar_aim_far"));
            return;
        }
        if (mode.pauseTicks() > 0) {
            ready.put(mode.id(), now + mode.pauseTicks());
        }
        dirty = true;
        level.playSound(null, BlockPos.containing(a), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
        actionBar(player, Component.translatable("nightsiege.msg.spotter_fired",
                Component.translatable("nightsiege.mortarmode." + mode.id()), fired));
    }

    /** Point fire: every own mortar that reaches the spot fires there from now on (no 10 s wait). */
    private int pointFire(ServerLevel level, List<TrapRecord> mortars, Vec3 spot, long now) {
        int n = 0;
        BlockPos block = BlockPos.containing(spot.x, spot.y - 1.0, spot.z);
        for (TrapRecord r : mortars) {
            if (!reaches(r, spot)) {
                continue;
            }
            r.aimed = true;
            r.aimX = block.getX();
            r.aimY = block.getY();
            r.aimZ = block.getZ();
            r.aimedAt = now;
            r.readyAt = Math.min(r.readyAt, now + 20);
            spark(level, Vec3.atCenterOf(new BlockPos(r.x, r.y, r.z)).add(0, 0.9, 0), spot);
            n++;
        }
        if (n > 0) {
            level.sendParticles(ParticleTypes.FLAME, spot.x, spot.y + 0.1, spot.z, 24, 1.2, 0.05, 1.2, 0.01);
        }
        return n;
    }

    /** Sperrfeuer: shells evenly along the line, the mortars that reach a spot taking turns. */
    private int barrage(ServerLevel level, List<TrapRecord> mortars, Vec3 a, Vec3 b) {
        int shells = (int) Math.round(SkillValues.get("e_barrage", "shells", 6));
        double longest = SkillValues.get("e_barrage", "length", 16);
        Vec3 line = b.subtract(a);
        if (line.length() > longest) {
            b = a.add(line.normalize().scale(longest));
        }
        int fired = 0;
        for (int i = 0; i < shells; i++) {
            Vec3 spot = shells == 1 ? a : a.add(b.subtract(a).scale(i / (double) (shells - 1)));
            if (fireOne(level, mortars, spot, fired, 1.0, "")) {
                fired++;
            }
        }
        return fired;
    }

    /** Shells scattered in a circle around the spot (area, smoke and fire salvo). */
    private int scatter(ServerLevel level, List<TrapRecord> mortars, Vec3 center, int shells, double radius,
                        double damage, String mode) {
        int fired = 0;
        for (int i = 0; i < shells; i++) {
            Vec3 spot = center;
            if (i > 0 || shells > 1) {
                double ang = level.getRandom().nextDouble() * Math.PI * 2;
                double dist = Math.sqrt(level.getRandom().nextDouble()) * radius;
                spot = center.add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
            }
            if (fireOne(level, mortars, spot, fired, damage, mode)) {
                fired++;
            }
        }
        return fired;
    }

    /** One shell from the next mortar (in turn) that reaches the spot. */
    private boolean fireOne(ServerLevel level, List<TrapRecord> mortars, Vec3 spot, int turn, double damage, String mode) {
        List<TrapRecord> able = new ArrayList<>();
        for (TrapRecord r : mortars) {
            if (reaches(r, spot) && canFire(r)) {
                able.add(r);
            }
        }
        if (able.isEmpty()) {
            return false;
        }
        TrapRecord r = able.get(turn % able.size());
        Vec3 muzzle = Vec3.atCenterOf(new BlockPos(r.x, r.y, r.z)).add(0, 0.9, 0);
        int tier = tierOf(r);
        lob(level, r, muzzle, spot, KitProjectile.SHELL, tier, damage, 1.0, mode);
        afterShot(level, new BlockPos(r.x, r.y, r.z), r);
        // A salvo shell is one of the mortar's own shots (Simon, 27 October 2026): it reloads
        // afterwards, so a salvo steers the fire instead of adding to it.
        double share = SkillValues.get(TrapKind.MORTAR.id(), "salvoreload", 1.0);
        if (share > 0) {
            long now = level.getGameTime();
            long reload = Math.round(quicker(r, now, slowed(TrapKind.MORTAR, TrapKind.MORTAR.cooldownTicks(tier))) * share);
            r.readyAt = Math.max(r.readyAt, now) + reload;
        }
        return true;
    }

    /** A smoke shell: no blast, monsters in the cloud lose their target for a while. */
    private void smokeBurst(ServerLevel level, Vec3 at) {
        double radius = SkillValues.get("e_smoke_salvo", "radius", 5);
        int ticks = (int) Math.round(SkillValues.get("e_smoke_salvo", "time", 80));
        GainGear.blind(level, at, radius, ticks, false);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, 40, radius * 0.4, 0.6, radius * 0.4, 0.01);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 30, radius * 0.4, 0.4, radius * 0.4, 0.02);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.2f, 0.6f);
    }

    /** End of the night: the fire pauses start fresh. */
    public void resetSpotter() {
        spotReady.clear();
    }

    // ------------------------------------------------------------------
    // the remote
    // ------------------------------------------------------------------

    static final String TAG_CHARGED = "nightsiege_charged";
    static final String TAG_BLAST = "nightsiege_blast";

    /** The engineer's remote was used, pointing at a block (null: at whatever is in sight). */
    public void useRemote(ServerPlayer player, BlockPos clicked) {
        useRemote(player, clicked, false);
    }

    /**
     * The remote's orders. {@code second}: the second order, given with the X key while the
     * remote is in hand (it was sneak + right click until 25 October 2026): detonate, the
     * mortar's salvo, reload, and aiming a mortar at a block.
     */
    public void useRemote(ServerPlayer player, BlockPos clicked, boolean second) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        boolean creative = player.getAbilities().instabuild;
        if (skills.clazz() != SkillClass.ENGINEER && !creative) {
            actionBar(player, Component.translatable("nightsiege.msg.remote_engineer_only"));
            return;
        }
        // The right click (Simon, 27 October 2026): always the tower menu – the state of every
        // tower and trap, strengthen the towers with a code there.
        if (!second) {
            openTowerMenu(player, true);
            return;
        }
        long now = level.getGameTime();
        // Fähigkeit 2: overcharge a trap (mortar, flame trap and potion thrower also fire at once),
        // mark a monster, aim the mortars at a block; the alchemist throws his potion zone.
        boolean alchemist = SkillTree.SUB_ALCHEMIST.equals(tree.subclassOf(skills));
        double range = remoteRange(skills);
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
        if (monster == null && trapPos == null && !alchemist) {
            // A boss or champion is marked from further away (28 October 2026), so the towers can be set on it.
            LivingEntity far = CombatEffects.lookTarget(level, player, range * bossRange());
            if (bigTarget(far)) {
                monster = far;
            }
        }
        if (trapPos != null && (monster == null
                || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(trapPos)) <= monster.distanceToSqr(player))) {
            TrapRecord record = find(level, trapPos);
            if (record == null || (!record.owner.equals(player.getUUID().toString()) && !creative)) {
                actionBar(player, Component.translatable("nightsiege.msg.remote_not_yours"));
                return;
            }
            TrapKind kind = TrapKind.byId(record.kind);
            if (!pay(player, skills, overchargeCost(skills, kind))) {
                return;
            }
            boost(level, record, skills, now, 1.0);
            if (tree.totalBonus(SkillEffect.NETWORK, skills) > 0) {
                Vec3 c = Vec3.atCenterOf(trapPos);
                double reach = SkillValues.get("e_network", "radius", 6.0);
                double share = SkillValues.get("e_network", "share", 0.5);
                for (TrapRecord other : traps()) {
                    if (other != record && other.owner.equals(record.owner) && other.dimension.equals(record.dimension)
                            && Vec3.atCenterOf(new BlockPos(other.x, other.y, other.z)).distanceToSqr(c) <= reach * reach) {
                        boost(level, other, skills, now, share);
                    }
                }
            }
            // The old "detonate" is part of the overcharge now: mortar salvo, big throw, flame burst.
            if ((kind == TrapKind.MORTAR || kind == TrapKind.FLAME || kind == TrapKind.BREWER)
                    && detonateBlocked(level, trapPos, record, kind) == null) {
                detonate(level, trapPos, record, kind, skills);
                if (kind.isTower()) {
                    afterShot(level, trapPos, record);
                }
                report(player, skills, "nightsiege.msg.remote_detonated");
            } else {
                report(player, skills, "nightsiege.msg.remote_overcharged");
            }
            return;
        }
        if (alchemist) {
            throwZone(level, player, skills, now);
            return;
        }
        if (monster != null) {
            if (pay(player, skills, cost(skills, SkillValues.get("e_seeker", "mark", COST_MARK)))) {
                double bonus = SkillValues.get("e_seeker", "base", 0.15) + tree.totalBonus(SkillEffect.SEEKER, skills);
                long until = now + Math.round(SkillValues.get("e_seeker", "time", 160));
                remoteMarks.put(monster.getUUID(), new RemoteMark(player.getUUID().toString(), until));
                marker.mark(monster, bonus, until, true);
                spark(level, player.getEyePosition().subtract(0, 0.2, 0), monster.position().add(0, monster.getBbHeight() * 0.6, 0));
                level.playSound(null, monster.getX(), monster.getY(), monster.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(),
                        SoundSource.PLAYERS, 0.8f, 1.8f);
                report(player, skills, "nightsiege.msg.remote_marked");
            }
            return;
        }
        if (aimMortar(level, player, range, now)) {
            return;
        }
        actionBar(player, Component.translatable("nightsiege.msg.remote_no_target"));
    }

    /** How far the remote reaches (also the tower menu's reach for repairs). */
    double remoteRange(PlayerSkills skills) {
        return SkillValues.get("e_longrange", "base", REMOTE_RANGE) + tree.totalBonus(SkillEffect.REMOTE_RANGE, skills);
    }

    // ------------------------------------------------------------------
    // the tower menu (Simon, 27 October 2026): no magazines, no wear, no repairs any more
    // (19:15 and 19:51) – the engineer strengthens his towers there with a code
    // ------------------------------------------------------------------

    /** Towers have endless shots again (Simon, 27 October 2026, 19:51: "reloading goes too"). */
    public static boolean usesAmmo(TrapKind kind) {
        return false;
    }

    /** Shots in a full tower: none to count any more (endless). */
    int magazine(TrapRecord record) {
        return 0;
    }

    /** True when the tower may fire: not in the 3 s of its strengthening. */
    boolean canFire(TrapRecord record) {
        TrapKind kind = TrapKind.byId(record.kind);
        return kind != null && !fixes.containsKey(key(record));
    }

    /** One shot of a tower: nothing to count any more (endless shots, no wear since 27 October 2026). */
    void afterShot(ServerLevel level, BlockPos pos, TrapRecord record) {
    }

    /** Every tower whole: at the start and at the end of each night. */
    public void refillAll() {
        for (TrapRecord record : traps()) {
            TrapKind kind = TrapKind.byId(record.kind);
            if (kind == null) {
                continue;
            }
            if (kind.isTower()) {
                record.broken = false;
                record.hpLost = 0f;
                record.shield = shieldMax(record);
            }
        }
        fixes.clear();
        strengthenedUntil.clear();
        dirty = true;
    }

    /** A strengthening under way: who does it (a player or the copper golem) and when it is done. */
    private record Fix(UUID player, long doneAt, boolean golem) {
    }

    /** Trap key → its strengthening under way. */
    private final Map<String, Fix> fixes = new HashMap<>();

    /** Ticks a strengthening takes once the code is in (3 s). */
    private static long fixTicks() {
        return Math.round(SkillValues.get("e_longrange", "fixtime", 60));
    }

    /**
     * The demolition expert's bird's-eye view reaches every own tower within its radius
     * (Simon, 27 October 2026: strengthen from the binoculars too).
     */
    private boolean spotterReaches(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!SpotterChunks.isOpen(player)) {
            return false;
        }
        BlockPos center = spotCenter(level, player);
        double radius = SkillValues.get(TrapKind.MORTAR.id(), "spotradius", 48) + 2.0;
        double dx = pos.getX() + 0.5 - center.getX();
        double dz = pos.getZ() + 0.5 - center.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    /** The right click with the remote: the state of every own trap in this world. */
    public void openTowerMenu(ServerPlayer player, boolean open) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        com.mki.nightsiege.core.TowerMenuData data = new com.mki.nightsiege.core.TowerMenuData();
        data.open = open;
        data.range = (int) Math.round(remoteRange(skills));
        data.fixSeconds = (int) Math.round(fixTicks() / 20.0);
        data.buffArrows = (int) Math.round(SkillValues.get("e_longrange", "buffcode", 5));
        data.buffPercent = (int) Math.round((SkillValues.get("e_longrange", "buffpower", 1.5) - 1.0) * 100.0);
        data.buffSeconds = (int) Math.round(SkillValues.get("e_longrange", "bufftime", 400) / 20.0);
        data.golem = CrateEffects.gainLevel(player, "kupfergolem") > 0;
        data.golemMax = (int) Math.round(SkillValues.get("e_longrange", "golemtowers", 2));
        String owner = player.getUUID().toString();
        String dim = SiegeManager.dimensionName(level);
        long now = level.getGameTime();
        double reach = remoteRange(skills);
        for (TrapRecord record : traps()) {
            TrapKind kind = TrapKind.byId(record.kind);
            if (kind == null || !owner.equals(record.owner) || !dim.equals(record.dimension)) {
                continue;
            }
            com.mki.nightsiege.core.TowerMenuData.Row row = new com.mki.nightsiege.core.TowerMenuData.Row();
            row.kind = kind.id();
            row.x = record.x;
            row.y = record.y;
            row.z = record.z;
            double d = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(new BlockPos(record.x, record.y, record.z))));
            row.dist = (int) Math.round(d);
            row.inRange = d <= reach + 0.5 || player.getAbilities().instabuild
                    || spotterReaches(level, player, new BlockPos(record.x, record.y, record.z));
            if (kind.isFloorPlate()) {
                row.fullUses = fullUses(kind, tierOf(record), record);
                row.uses = Math.min(record.uses, row.fullUses);
            }
            Fix fix = fixes.get(key(record));
            row.working = fix == null ? 0 : (int) Math.max(1, fix.doneAt() - now);
            row.golem = record.golem;
            row.tower = kind.isTower();
            Long strong = strengthenedUntil.get(key(record));
            row.buffed = strong == null ? 0 : (int) Math.max(0, strong - now);
            data.rows.add(row);
        }
        data.rows.sort(Comparator.comparingInt((com.mki.nightsiege.core.TowerMenuData.Row r) -> r.tower ? 0 : 1)
                .thenComparingInt(r -> r.dist));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new CoreNetworking.ScreenPayload("towers", Json.toJson(data)));
    }

    /** From the tower menu: "buff:x:y:z" (strengthen a tower, the code was right) or "golem:x:y:z" (the golem's two towers). */
    public void onTowerAction(ServerPlayer player, String argument) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        String[] parts = argument.split(":");
        if (parts.length >= 1 && "list".equals(parts[0])) {
            openTowerMenu(player, false);   // the bird's-eye view asks for the state of the towers
            return;
        }
        if (parts.length < 4) {
            return;
        }
        BlockPos pos;
        try {
            pos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return;
        }
        TrapRecord record = find(level, pos);
        boolean creative = player.getAbilities().instabuild;
        if (record == null || (!record.owner.equals(player.getUUID().toString()) && !creative)) {
            return;
        }
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        if ("golem".equals(parts[0])) {
            if (CrateEffects.gainLevel(player, "kupfergolem") <= 0 || !TrapKind.byId(record.kind).isTower()) {
                return;
            }
            if (!record.golem) {
                int most = (int) Math.round(SkillValues.get("e_longrange", "golemtowers", 2));
                long have = traps().stream().filter(t -> t.golem && t.owner.equals(record.owner)).count();
                if (have >= most) {
                    actionBar(player, Component.translatable("nightsiege.msg.golem_full", most));
                    openTowerMenu(player, false);
                    return;
                }
            }
            record.golem = !record.golem;
            dirty = true;
            openTowerMenu(player, false);
            return;
        }
        if (!"buff".equals(parts[0])) {
            return;
        }
        long now = level.getGameTime();
        double d = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos)));
        boolean reached = d <= remoteRange(skills) + 0.5 || creative || spotterReaches(level, player, pos);
        if (!canStrengthen(record, now) || fixes.containsKey(key(record)) || !reached) {
            openTowerMenu(player, false);
            return;
        }
        long ticks = gainOf(record, "werkzeugtasche") > 0 ? Math.round(fixTicks() * 0.6) : fixTicks();
        fixes.put(key(record), new Fix(player.getUUID(), now + (creative ? 10 : ticks), false));
        playAt(level, pos, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.2f);
        actionBar(player, Component.translatable("nightsiege.msg.tower_buffing",
                Component.translatable("block.nightsiege." + record.kind), Math.max(1, ticks / 20)));
        openTowerMenu(player, false);
    }

    /**
     * A tower can be strengthened (Simon, 27 October 2026, instead of repairs and reloads) when
     * it is not strengthened already; in the last 5 s of a strengthening it can be renewed.
     */
    private boolean canStrengthen(TrapRecord record, long now) {
        TrapKind kind = TrapKind.byId(record.kind);
        if (kind == null || !kind.isTower()) {
            return false;
        }
        Long until = strengthenedUntil.get(key(record));
        return until == null || until - now <= 100;
    }

    /** For the tests: true while the tower is strengthened from the menu. */
    public boolean strengthenedForTest(TrapRecord record) {
        Long until = strengthenedUntil.get(key(record));
        return until != null;
    }

    /** Damage factor of a tower strengthened in the menu (×1.5); 1 otherwise. */
    private double strengthened(TrapRecord record, long now) {
        Long until = strengthenedUntil.get(key(record));
        if (until == null) {
            return 1.0;
        }
        if (now > until) {
            strengthenedUntil.remove(key(record));
            return 1.0;
        }
        return SkillValues.get("e_longrange", "buffpower", 1.5);
    }

    /**
     * Ticks a strengthening lasts: 20 s, longer with fine mechanics (the same share as the floor
     * traps) and with the amplifier crate (crate gain "munitionskiste", 50 % longer).
     */
    private long strengthenTicks(TrapRecord record) {
        double base = SkillValues.get("e_longrange", "bufftime", 400);
        double crate = gainOf(record, "munitionskiste") > 0 ? 1.5 : 1.0;
        return Math.round(base * crate * (1.0 + Math.max(0.0, bonus(record, SkillEffect.TRAP_DURABILITY))));
    }

    /** The copper golem starts strengthening a tower (it stands there for the same 3 s). True when it did. */
    public boolean startGolemFix(ServerLevel level, BlockPos pos, UUID golem) {
        TrapRecord record = find(level, pos);
        if (record == null || !canStrengthen(record, level.getGameTime()) || fixes.containsKey(key(record))) {
            return false;
        }
        fixes.put(key(record), new Fix(golem, level.getGameTime() + fixTicks(), true));
        playAt(level, pos, SoundEvents.SMITHING_TABLE_USE, 0.8f, 1.4f);
        return true;
    }

    /** The golem's towers that can be strengthened (again), nearest first. */
    public List<BlockPos> golemWork(ServerLevel level, String owner, Vec3 near) {
        String dim = SiegeManager.dimensionName(level);
        List<BlockPos> out = new ArrayList<>();
        for (TrapRecord record : traps()) {
            if (record.golem && owner.equals(record.owner) && dim.equals(record.dimension)
                    && canStrengthen(record, level.getGameTime()) && !fixes.containsKey(key(record))) {
                out.add(new BlockPos(record.x, record.y, record.z));
            }
        }
        out.sort(Comparator.comparingDouble(p -> near.distanceToSqr(Vec3.atCenterOf(p))));
        return out;
    }

    /** True while a fix runs on the tower at this spot. */
    public boolean fixing(ServerLevel level, BlockPos pos) {
        TrapRecord record = find(level, pos);
        return record != null && fixes.containsKey(key(record));
    }

    /** Every tick: fixes that are done; sparks on the towers under work. */
    private void tickFixes(MinecraftServer server) {
        if (!strengthenedUntil.isEmpty() && server.overworld().getGameTime() % 10 == 0) {
            // Strengthened towers glow with sparks while it lasts.
            for (TrapRecord record : traps()) {
                Long until = strengthenedUntil.get(key(record));
                ServerLevel level = until == null ? null : SiegeManager.levelByName(server, record.dimension);
                if (level == null) {
                    continue;
                }
                if (level.getGameTime() > until) {
                    strengthenedUntil.remove(key(record));
                    continue;
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, record.x + 0.5, record.y + 1.2, record.z + 0.5, 3, 0.35, 0.4, 0.35, 0.02);
            }
        }
        if (fixes.isEmpty()) {
            return;
        }
        for (TrapRecord record : new ArrayList<>(traps())) {
            Fix fix = fixes.get(key(record));
            if (fix == null) {
                continue;
            }
            ServerLevel level = SiegeManager.levelByName(server, record.dimension);
            if (level == null) {
                continue;
            }
            long now = level.getGameTime();
            BlockPos pos = new BlockPos(record.x, record.y, record.z);
            if (now % 5 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        3, 0.3, 0.3, 0.3, 0.0);
                if (now % 15 == 0) {
                    playAt(level, pos, SoundEvents.ANVIL_USE, 0.35f, 1.6f);
                }
            }
            if (now < fix.doneAt()) {
                continue;
            }
            fixes.remove(key(record));
            TrapKind kind = TrapKind.byId(record.kind);
            if (kind == null) {
                continue;
            }
            // Strengthened (Simon, 27 October 2026, instead of repairs and reloads): +50 % damage
            // for 20 s; it also mends the tower's health for its gunner and charges the tower shield.
            record.broken = false;
            strengthenedUntil.put(key(record), now + strengthenTicks(record));
            record.hpLost = 0f;
            record.shield = shieldMax(record);
            record.readyAt = now;
            dirty = true;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 24, 0.3, 0.3, 0.3, 0.0);
            playAt(level, pos, SoundEvents.BEACON_ACTIVATE, 0.9f, 1.4f);
            ServerPlayer worker = server.getPlayerList().getPlayer(fix.player());
            if (worker != null) {
                actionBar(worker, Component.translatable("nightsiege.msg.tower_buffed", Component.translatable("block.nightsiege." + kind.id()),
                        (int) Math.round((SkillValues.get("e_longrange", "buffpower", 1.5) - 1.0) * 100.0),
                        strengthenTicks(record) / 20));
            }
            ServerPlayer owner = ownerOf(level, record);
            if (owner != null && fix.golem()) {
                actionBar(owner, Component.translatable("nightsiege.msg.golem_fixed", Component.translatable("block.nightsiege." + kind.id())));
            }
        }
    }

    // ------------------------------------------------------------------
    // the turret shield (crate gain "geschuetzschild", 27 October 2026)
    // ------------------------------------------------------------------

    /** Shots the tower shield blocks before it is spent (0 without the gain or for other towers). */
    int shieldMax(TrapRecord record) {
        TrapKind kind = TrapKind.byId(record.kind);
        if (kind != TrapKind.TURRET && kind != TrapKind.TESLA) {
            return 0;
        }
        return gainOf(record, "geschuetzschild") > 0 ? (int) Math.round(SkillValues.get("e_longrange", "shield", 20)) : 0;
    }

    /**
     * A shot at a seated gunner: the tower shield takes it whole while it has charges.
     * True when it was blocked.
     */
    public static boolean shieldBlocks(ServerPlayer player) {
        TrapService service = current;
        Seat seat = service == null || player == null ? null : service.seatOf.get(player.getUUID());
        if (seat == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        TrapRecord record = service.find(level, seat.pos);
        if (record == null || record.shield <= 0) {
            return false;
        }
        record.shield--;
        service.dirty = true;
        Vec3 at = player.getEyePosition().subtract(0, 0.3, 0);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.35, 0.3, 0.35, 0.1);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 3, 0.4, 0.3, 0.4, 0.01);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8f, 1.3f);
        actionBar(player, Component.translatable("nightsiege.msg.tower_shield", record.shield, service.shieldMax(record)));
        return true;
    }

    /** Ticks between two changes of a mortar's target spot (built-in; the skill value mortar_tower.aimpause wins). */
    public static final int AIM_PAUSE_TICKS = 200;

    private static long aimPause() {
        return Math.round(SkillValues.get(TrapKind.MORTAR.id(), "aimpause", AIM_PAUSE_TICKS));
    }

    /** Sets a mortar's target spot directly (for the gametest). */
    public boolean setAim(ServerLevel level, BlockPos tower, BlockPos spot) {
        TrapRecord record = find(level, tower);
        if (record == null) {
            return false;
        }
        record.aimed = true;
        record.aimX = spot.getX();
        record.aimY = spot.getY();
        record.aimZ = spot.getZ();
        record.aimedAt = level.getGameTime();
        record.readyAt = level.getGameTime();
        return true;
    }

    /** Where a set mortar's shells land: on top of the chosen block. */
    private static Vec3 aimSpot(ServerLevel level, TrapRecord record) {
        return new Vec3(record.aimX + 0.5, record.aimY + 1.0, record.aimZ + 0.5);
    }

    /**
     * The X key with the remote, looking at a block (not a trap): the nearest own mortar that
     * reaches it fires there from now on. Each mortar can be set again only after 10 s.
     * Returns true when the click was about aiming (set, too early or nothing in reach).
     */
    private boolean aimMortar(ServerLevel level, ServerPlayer player, double range, long now) {
        net.minecraft.world.phys.HitResult hit = player.pick(Math.max(range, 64.0), 0f, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult block)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos spot = block.getBlockPos();
        Vec3 target = Vec3.atBottomCenterOf(spot.above());
        String owner = player.getUUID().toString();
        String dim = SiegeManager.dimensionName(level);
        // Every own mortar that reaches the spot aims there (Simon, 26 October 2026: with several
        // mortars it was unclear which one the nearest-mortar rule picked).
        List<TrapRecord> aiming = new ArrayList<>();
        TrapRecord waiting = null;
        boolean any = false;
        for (TrapRecord r : traps()) {
            if (!TrapKind.MORTAR.id().equals(r.kind) || !r.dimension.equals(dim)
                    || (!r.owner.equals(owner) && !player.getAbilities().instabuild)) {
                continue;
            }
            any = true;
            Vec3 at = Vec3.atCenterOf(new BlockPos(r.x, r.y, r.z));
            double reach = TrapKind.MORTAR.radius(tierOf(r)) + bonus(r, SkillEffect.TOWER_RANGE);
            double dx = at.x - target.x;
            double dz = at.z - target.z;
            if (dx * dx + dz * dz > reach * reach) {
                continue;
            }
            if (now - r.aimedAt < aimPause()) {
                if (waiting == null || r.aimedAt < waiting.aimedAt) {
                    waiting = r;
                }
                continue;
            }
            aiming.add(r);
        }
        if (!any) {
            return false;   // no mortar: the remote does what it did before
        }
        if (aiming.isEmpty()) {
            if (waiting != null) {
                long left = (aimPause() - (now - waiting.aimedAt) + 19) / 20;
                actionBar(player, Component.translatable("nightsiege.msg.mortar_aim_wait", left));
            } else {
                actionBar(player, Component.translatable("nightsiege.msg.mortar_aim_far"));
            }
            return true;
        }
        for (TrapRecord r : aiming) {
            r.aimed = true;
            r.aimX = spot.getX();
            r.aimY = spot.getY();
            r.aimZ = spot.getZ();
            r.aimedAt = now;
            r.readyAt = Math.min(r.readyAt, now + 20);
            // A line of sparks from each mortar to the spot shows which ones took the order.
            spark(level, Vec3.atCenterOf(new BlockPos(r.x, r.y, r.z)).add(0, 0.9, 0), target);
        }
        dirty = true;
        level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 0.1, target.z, 24, 1.2, 0.05, 1.2, 0.01);
        level.playSound(null, spot, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 0.9f, 0.8f);
        actionBar(player, Component.translatable("nightsiege.msg.mortar_aimed_n", aiming.size(), spot.getX(), spot.getY(), spot.getZ()));
        return true;
    }

    private void boost(ServerLevel level, TrapRecord record, PlayerSkills skills, long now, double share) {
        long length = Math.round((SkillValues.get("e_circuit", "base", 100) + tree.totalBonus(SkillEffect.OVERCHARGE_TIME, skills)) * share);
        boostedUntil.merge(key(record), now + length, Math::max);
        record.readyAt = Math.min(record.readyAt, now);
        BlockPos pos = new BlockPos(record.x, record.y, record.z);
        Vec3 c = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 0.4, 0.4, 0.4, 0.1);
        playAt(level, pos, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.8f);
    }

    /** Why an X order on this trap would fire at nothing (a language key), or null when it can go. */
    private String detonateBlocked(ServerLevel level, BlockPos pos, TrapRecord record, TrapKind kind) {
        if (kind == TrapKind.MORTAR && !record.aimed) {
            return "nightsiege.msg.remote_no_aim";
        }
        if (kind == TrapKind.BREWER && brewerTarget(level, pos, record) == null) {
            return "nightsiege.msg.remote_no_group";
        }
        return null;
    }

    /** The group the potion thrower's big throw would go for, or null. */
    private LivingEntity brewerTarget(ServerLevel level, BlockPos pos, TrapRecord record) {
        int tier = tierOf(record);
        Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.9, 0);
        double range = TrapKind.BREWER.radius(tier) + bonus(record, SkillEffect.TOWER_RANGE);
        return bestGroup(level, record, muzzle, range, TrapKind.BREWER.splash(tier), level.getGameTime(), null);
    }

    /** Sets a floor trap off from afar, stronger with the detonator. */
    private void detonate(ServerLevel level, BlockPos pos, TrapRecord record, TrapKind kind, PlayerSkills skills) {
        int tier = tierOf(record);
        long now = level.getGameTime();
        double damage = 1.0 + SkillValues.get("e_detonator", "dmg", 0.15) * tree.totalBonus(SkillEffect.DETONATOR, skills);
        double radius = 1.0 + tree.totalBonus(SkillEffect.BLAST_POWER, skills);
        switch (kind) {
            case MORTAR -> {
                // The salvo: three shells at once onto the set spot, a little spread.
                if (!record.aimed) {
                    break;
                }
                Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.9, 0);
                Vec3 aim = aimSpot(level, record);
                int salvo = (int) Math.round(SkillValues.get(TrapKind.MORTAR.id(), "salvo", 3));
                for (int i = 0; i < salvo; i++) {
                    double a = Math.PI * 2 * i / salvo;
                    Vec3 spot = i == 0 ? aim : aim.add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8);
                    lob(level, record, muzzle, spot, KitProjectile.SHELL, tier, damage, radius);
                }
            }
            case FLAME -> fireFlame(level, pos, record, tier, now, damage, radius);
            case BREWER -> {
                // The big throw: half as wide again (and with the plague skill it bursts).
                Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.9, 0);
                LivingEntity target = brewerTarget(level, pos, record);
                if (target != null) {
                    lob(level, record, muzzle, lead(muzzle, target), KitProjectile.BREW, tier, damage,
                            radius * SkillValues.get(TrapKind.BREWER.id(), "big", 1.5));
                }
            }
            default -> {
            }
        }
    }

    private boolean canReload(TrapRecord record, TrapKind kind) {
        if (record.broken) {
            return true;
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
        record.broken = false;
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
            LivingEntity next = enemies(level, new AABB(at, at).inflate(SkillValues.get("e_charged", "jump", 4.0))).stream()
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
            double r = SkillValues.get("e_targetblast", "radius", 2.0);
            for (LivingEntity near : enemies(level, new AABB(at, at).inflate(r, 1.5, r))) {
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

    /** The remote's full charge, for the kit HUD's gauge. */
    public double maxEnergyOf(ServerPlayer player) {
        return maxEnergy(save.get().skillsOf(player.getUUID().toString()));
    }

    /** The kit HUD's left wing (29 October 2026): the engineer's traps and towers by kind, with their limit. */
    public java.util.List<com.mki.nightsiege.core.UltState.Unit> unitsOf(ServerPlayer player) {
        java.util.List<com.mki.nightsiege.core.UltState.Unit> out = new java.util.ArrayList<>();
        String owner = player.getUUID().toString();
        PlayerSkills skills = save.get().skillsOf(owner);
        for (TrapKind kind : TrapKind.values()) {
            int count = placedBy(owner, kind);
            net.minecraft.world.item.Item item = ModContent.TRAP_ITEMS.get(kind);
            if (count <= 0 || item == null) {
                continue;
            }
            com.mki.nightsiege.core.UltState.Unit u = new com.mki.nightsiege.core.UltState.Unit();
            u.icon = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
            u.count = count;
            u.max = maxFor(kind, Math.max(1, tree.trapTier(kind, skills)), skills);
            out.add(u);
        }
        return out;
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
        double max = SkillValues.get("e_battery", "base", REMOTE_ENERGY)
                + (skills == null ? 0 : tree.totalBonus(SkillEffect.REMOTE_ENERGY, skills));
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
        // Every shooting tower costs a share of the full remote (Simon, 26 October 2026: 90 %).
        double share = kind != null && kind.isTower() ? SkillValues.get(kind.id(), "overcharge", 0.9) : 0.0;
        if (share <= 0) {
            return cost(skills, kind == null ? COST_OVERCHARGE : SkillValues.get(kind.id(), "overcharge", COST_OVERCHARGE));
        }
        double max = maxEnergy(skills);
        return Math.min(max, cost(skills, share * max));
    }

    private double cost(PlayerSkills skills, double base) {
        double scale = 1.0;
        if (SkillTree.has(skills, "e_fortress")) {
            scale *= 1.0 + SkillValues.get("e_fortress", "cost", 0.3);
        }
        if (SkillTree.has(skills, "e_fieldtech")) {
            scale *= 1.0 - SkillValues.get("e_fieldtech", "cost", 0.4);
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
                // Energy per second, handed out every five ticks.
                double rate = SkillValues.get("e_fastcharge", "rate", 5.0) / 4.0 * (1.0 + tree.totalBonus(SkillEffect.RECHARGE, skills));
                if (now < max * SkillValues.get("e_backup", "below", 0.3) && tree.totalBonus(SkillEffect.BACKUP_POWER, skills) > 0) {
                    rate *= SkillValues.get("e_backup", "speed", 2.0);
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
        return overcharged(record, now) * gain;
    }

    /**
     * The remote's overcharge on one trap: ×2, with overvolt ×3; 1 when it is not overcharged.
     * The strengthening from the tower menu (×1.5) comes on top.
     */
    private double overcharged(TrapRecord record, long now) {
        double menu = strengthened(record, now);
        if (!boosted(record, now)) {
            return menu;
        }
        return menu * (bonus(record, SkillEffect.OVERVOLT) > 0 ? SkillValues.get("e_overvolt", "power", 3.0)
                : SkillValues.get("e_overvolt", "base", 2.0));
    }

    /** True while the owner's ultimate overcharge runs (arrow turrets and tesla coils fire faster). */
    private boolean ultOvercharged(TrapRecord record, long now) {
        Long until = overchargedUntil.get(record.owner);
        return until != null && now <= until;
    }

    private static double ultSpeed() {
        return Math.max(1.0, SkillValues.get("overcharge", "speed", 3.0));
    }

    /** A crate gain of the trap's owner, only while a night runs (or the field fight of a field tower). */
    private int gainOf(TrapRecord record, String gainId) {
        SiegeManager m = SiegeManager.get();
        if (m == null || !m.isActive() && !record.field) {
            return 0;
        }
        PlayerSkills skills = ownerSkills(record);
        return skills == null ? 0 : m.kits().gainLevel(skills, com.mki.nightsiege.core.SkillClass.ENGINEER, gainId);
    }

    /** Cooldowns halve while overcharged. */
    /**
     * Simon 26.10.: the arrow turret shoots half as fast up to world level 14 and 30 % slower from
     * level 15; the mortar half as fast on every level. The night's level counts.
     */
    static int slowed(TrapKind kind, int ticks) {
        return (int) Math.round(ticks * slowFactor(kind, nightLevel()));
    }

    /** Factor on a tower's cooldown on this world level (1 for every other trap). */
    public static double slowFactor(TrapKind kind, int level) {
        if (kind == TrapKind.TURRET) {
            return level < (int) Math.round(SkillValues.get(kind.id(), "slowuntil", 15))
                    ? SkillValues.get(kind.id(), "slowlow", 2.0) : SkillValues.get(kind.id(), "slowhigh", 1.0 / 0.7);
        }
        if (kind == TrapKind.MORTAR) {
            return SkillValues.get(kind.id(), "slow", 2.0);
        }
        return 1.0;
    }

    /** The level of the running night (or the coming one). */
    private static int nightLevel() {
        SiegeManager m = SiegeManager.get();
        return m == null ? 1 : m.currentLevel();
    }

    private int quicker(TrapRecord record, long now, int ticks) {
        if (gainOf(record, "werkzeugtasche") > 0) {
            ticks = Math.max(3, (int) Math.round(ticks * 0.7));   // tool bag: traps re-arm sooner
        }
        return boosted(record, now) ? Math.max(3, ticks / 2) : ticks;
    }

    /** The armour piercer: turret and tesla hit harder while the ultimate overcharge runs. */
    private double piercing(TrapRecord record, long now) {
        return ultOvercharged(record, now) ? 1.0 + bonus(record, SkillEffect.ARMOR_PIERCER) : 1.0;
    }

    /** Fire and blasts: stronger for the pyromaniac, weaker for the plague doctor. */
    private double blastScale(TrapRecord record) {
        double scale = 1.0;
        if (has(record, "e_pyro")) {
            scale *= 1.0 + bonus(record, SkillEffect.PYROMANIAC);
        }
        if (has(record, "e_plague_doctor")) {
            scale *= 1.0 - SkillValues.get("e_plague_doctor", "blast", 0.3);
        }
        return scale;
    }

    private double poisonScale(TrapRecord record) {
        return (1.0 + bonus(record, SkillEffect.SLOW_POISON))
                * (has(record, "e_plague_doctor") ? SkillValues.get("e_plague_doctor", "poison", 2.0) : 1.0);
    }

    /** Tag of a dungeon's champion (Expeditions) and of the boss of a dungeon core (DungeonService). */
    static final String[] CHAMPION_TAGS = {Expeditions.CHAMPION_TAG, "nightsiege_dungeon_boss"};

    /**
     * Bosses, their soul crystals and egg sacs, and the dungeons' champions (Simon's playtest,
     * 28 October 2026: the towers never reached them): towers reach them further and go for them first.
     */
    static boolean bigTarget(LivingEntity e) {
        if (e == null) {
            return false;
        }
        if (BossService.isBossOrPart(e)) {
            return true;
        }
        for (String tag : CHAMPION_TAGS) {
            if (e.entityTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** True while a boss takes no damage at all (the lich behind his soul shield): no tower wastes a shot on it. */
    static boolean shielded(LivingEntity e) {
        if (BossService.isMount(e)) {
            return true;   // the storm witch's phantom cannot be hurt
        }
        SiegeManager m = SiegeManager.get();
        return m != null && BossService.isBoss(e) && m.bossShielded(e);
    }

    private static double bossRange() {
        SiegeManager m = SiegeManager.get();
        return m == null || m.config() == null ? 1.5 : m.config().towerBossRange;
    }

    private static double markRange() {
        SiegeManager m = SiegeManager.get();
        return m == null || m.config() == null ? 1.25 : m.config().towerMarkRange;
    }

    /** The widest a tower looks: its range times the larger of the two factors. */
    static double searchFactor() {
        return Math.max(1.0, Math.max(bossRange(), markRange()));
    }

    /** How far this tower reaches this monster: further for bosses and champions and for a marked one. */
    private double reachOf(TrapRecord record, LivingEntity e, double range, long now) {
        double f = 1.0;
        if (bigTarget(e)) {
            f = Math.max(f, bossRange());
        }
        if (markedBy(record, e, now)) {
            f = Math.max(f, markRange());
        }
        return range * f;
    }

    private boolean inReach(TrapRecord record, LivingEntity e, Vec3 from, double range, long now) {
        double reach = reachOf(record, e, range, now);
        return e.distanceToSqr(from) <= reach * reach;
    }

    /** Which monster a turret or coil takes: a marked one, then bosses and champions, then the nearest. */
    private double priority(TrapRecord record, LivingEntity e, Vec3 from, long now) {
        double d = e.distanceToSqr(from);
        if (markedBy(record, e, now)) {
            return d;
        }
        return (bigTarget(e) ? 5000.0 : 10000.0) + d;
    }

    private boolean markedBy(TrapRecord record, LivingEntity e, long now) {
        RemoteMark mark = remoteMarks.get(e.getUUID());
        if (mark != null && now <= mark.until() && mark.owner().equals(record.owner)) {
            return true;
        }
        // Sappeur-Alarm (crate gain, 27 October 2026): sappers and ladder carriers count as marked.
        return gainOf(record, "sappeuralarm") > 0
                && (e.entityTags().contains(Besiegers.SAPPER) || e.entityTags().contains(Besiegers.CARRIER));
    }

    private int fullUses(TrapKind kind, int tier, TrapRecord record) {
        int base = kind.uses(tier);
        if (base == Integer.MAX_VALUE || base <= 1) {
            return base;
        }
        double scale = 1.0 + bonus(record, SkillEffect.TRAP_DURABILITY);
        if (has(record, "e_pyro")) {
            scale *= 1.0 - SkillValues.get("e_pyro", "wear", 0.3);
        }
        return Math.max(1, (int) Math.round(base * scale));
    }

    private int maxAmmo(TrapRecord record) {
        return magazine(record);
    }

    /**
     * Most traps of a kind. The towers' hard caps (Simon, 25 October 2026: mortar and tesla
     * two, arrow turret and potion thrower three) are the base; the fortress builder and the
     * crate gain overcapacity put one more on top, the field technician takes one away
     * (balance update, 26 October 2026: the caps used to swallow all three).
     */
    public int maxFor(TrapKind kind, int tier, PlayerSkills skills) {
        int ueber = 0;
        SiegeManager m = SiegeManager.get();
        if (m != null && skills != null
                && m.kits().gainLevel(skills, com.mki.nightsiege.core.SkillClass.ENGINEER, "ueberkapazitaet") > 0) {
            ueber = 1;   // crate gain: one more of every kind
        }
        if ((kind == TrapKind.TURRET || kind == TrapKind.TESLA) && m != null && skills != null
                && m.kits().gainLevel(skills, com.mki.nightsiege.core.SkillClass.ENGINEER, "pfeilkiste") > 0) {
            ueber += 1;   // Zusatzturm (id pfeilkiste): one more arrow turret or tesla coil (27 October 2026: the tesla too)
        }
        if (kind == TrapKind.MORTAR && m != null && skills != null
                && m.kits().gainLevel(skills, com.mki.nightsiege.core.SkillClass.ENGINEER, "notfallmine") > 0) {
            ueber += 1;   // Notfall-Mörser: the extra mortar of the kit has its place (26 October 2026)
        }
        return maxFor(kind, tier, SkillTree.has(skills, "e_fortress"), SkillTree.has(skills, "e_fieldtech"), ueber);
    }

    /** The cap without the save: for tests and the window. */
    public static int maxFor(TrapKind kind, int tier, boolean fortress, boolean fieldtech, int extra) {
        int max = kind.maxPlaced(tier);
        if (kind.isTower()) {
            max = Math.min(max, (int) Math.round(SkillValues.get(kind.id(), "cap", kind == TrapKind.MORTAR || kind == TrapKind.TESLA ? 2 : 3)));
        }
        max += extra;
        if (kind == TrapKind.TURRET || kind == TrapKind.TESLA) {
            if (fortress) {
                max += (int) Math.round(SkillValues.get("e_fortress", "slots", 1));
            }
            if (fieldtech) {
                max -= (int) Math.round(SkillValues.get("e_fieldtech", "slots", 1));
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

    /** For the tests: the record of the trap at a spot, or null. */
    public TrapRecord recordAt(ServerLevel level, BlockPos pos) {
        return find(level, pos);
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

    /** The trap whose blow lands right now and when (for the statistics: a tower's hit is its owner's attack). */
    private static TrapRecord hitting;
    private static long hittingAt = -1L;

    /** Forgets the trap of the last blow (before and after the traps' tick, so no player's own blow is taken for it). */
    static void clearHitting() {
        hitting = null;
    }

    /** The kind of the trap whose blow this player's damage is ("arrow_turret", "tesla_coil_bemannt" …), or null. */
    static String hittingKey(ServerPlayer owner) {
        TrapRecord record = hitting;
        if (record == null || owner == null || hittingAt != owner.level().getGameTime()
                || !owner.getUUID().toString().equals(record.owner)) {
            return null;
        }
        TrapService service = current;
        boolean manned = service != null && service.mannedTower(record);
        return record.kind + (manned ? "_bemannt" : "");
    }

    /** The tower this player sits in ("tesla_coil" …), or null. */
    public String seatKind(ServerPlayer player) {
        Seat seat = seatOf.get(player.getUUID());
        return seat == null ? null : seat.kind.id();
    }

    /** Damage counts as the owner's own when they are online, so kills reward them. */
    private static DamageSource sourceFor(ServerLevel level, TrapRecord record) {
        hitting = record;
        hittingAt = level.getGameTime();
        ServerPlayer owner = ownerOf(level, record);
        return owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().cactus();
    }

    static List<LivingEntity> enemies(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive()
                && !Companions.isCompanion(e));   // the summoner's skeleton is no foe
    }

    /** The turret's opening towards a monster: just outside its block, on the side facing it. */
    private static Vec3 port(Vec3 muzzle, LivingEntity e) {
        double dx = e.getX() - muzzle.x;
        double dz = e.getZ() - muzzle.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-3) {
            return muzzle;
        }
        double k = 0.75 / Math.max(Math.abs(dx), Math.abs(dz)) ;   // to the edge of the block (square), a bit beyond
        return new Vec3(muzzle.x + dx * Math.min(k, 0.75 / len * 1.42), muzzle.y - 0.25, muzzle.z + dz * Math.min(k, 0.75 / len * 1.42));
    }

    /** True when the tower sees the monster: its eyes or the middle of its body. */
    private static boolean sees(ServerLevel level, Vec3 from, LivingEntity e, BlockPos self) {
        return clearShot(level, from, e.getEyePosition(), self)
                || clearShot(level, from, e.position().add(0, e.getBbHeight() * 0.5, 0), self);
    }

    /** For the gametest: every shell burst is written here when set. */
    public static java.util.function.Consumer<String> shotLog;

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
