package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.ToolKind;
import com.mki.nightsiege.core.ToolPicker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/** Creates the siege mobs, scales them, and hands out the digging tools. */
public final class MobFactory {

    /** Entity tag every siege mob carries, so they can be told apart. */
    public static final String TAG = "nightsiege";
    /** Entity tag of the champion during an elite night. */
    public static final String TAG_ELITE = "nightsiege_elite";

    private MobFactory() {
    }

    /** True when this entity id is not registered, usually because the mod is missing. */
    public static boolean isUnavailable(String entityId) {
        if (entityId == null) {
            return true;
        }
        for (String part : entityId.split("\\+")) {
            Identifier id = Identifier.tryParse(part.trim());
            if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads the tool a mob is actually holding. This covers mobs the mod armed
     * as well as mobs that happened to spawn with a tool of their own.
     */
    public static ToolKind toolOf(Mob mob) {
        ItemStack held = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if (held == null || held.isEmpty()) {
            return ToolKind.NONE;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
        return ToolPicker.fromItemId(id == null ? null : id.toString());
    }

    /**
     * Spawns one mob near the anchor.
     *
     * @param elite          true for the champion of an elite night
     * @param distanceBonus  extra blocks from the Basis branch of the skill tree
     * @return the mob, or null when nothing could be placed
     */
    public static Mob spawn(ServerLevel world, BlockPos anchor, String entityId, int siegeLevel,
                            SiegeConfig cfg, Random random, SiegeEvent event, boolean elite,
                            double distanceBonus) {
        if (isUnavailable(entityId)) {
            return null;
        }
        BlockPos spot = findSpawnSpot(world, anchor, cfg, random, distanceBonus);
        if (spot == null) {
            return null;
        }
        return spawnAt(world, spot, entityId, siegeLevel, cfg, random, event, elite);
    }

    /** Spawns one mob (and its rider, if any) on a spot already chosen. */
    public static Mob spawnAt(ServerLevel world, BlockPos spot, String entityId, int siegeLevel,
                              SiegeConfig cfg, Random random, SiegeEvent event, boolean elite) {
        if (isUnavailable(entityId)) {
            return null;
        }
        String[] parts = entityId.split("\\+");
        Mob mob = create(world, parts[0].trim(), spot, random);
        if (mob == null) {
            return null;
        }
        applyScaling(mob, siegeLevel, cfg, event, elite);
        equipTool(mob, cfg, random, elite);
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        // A rider from the entry, unless vanilla already seated one.
        if (parts.length > 1 && mob.getPassengers().isEmpty()) {
            Mob rider = create(world, parts[1].trim(), spot, random);
            if (rider != null && !rider.startRiding(mob)) {
                rider.discard();
            }
        }
        for (Entity passenger : mob.getPassengers()) {
            if (passenger instanceof Mob riding) {
                applyScaling(riding, siegeLevel, cfg, event, false);
                riding.setPersistenceRequired();
                riding.addTag(TAG);
            }
        }
        if (elite) {
            mob.addTag(TAG_ELITE);
            mob.setCustomName(Component.translatable("nightsiege.elite.name"));
            mob.setCustomNameVisible(true);
            mob.setGlowingTag(true);
        }
        world.addFreshEntityWithPassengers(mob);
        if (mob.isRemoved()) {
            return null;
        }
        return mob;
    }

    /**
     * Creates a mob the way the world itself would: with its weapons, armour and
     * variants, so skeletons carry bows and pillagers crossbows.
     */
    private static Mob create(ServerLevel world, String entityId, BlockPos spot, Random random) {
        Identifier id = Identifier.tryParse(entityId);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        Entity entity = type == null ? null : type.create(world, EntitySpawnReason.EVENT);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
        try {
            mob.finalizeSpawn(world, world.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
        } catch (RuntimeException e) {
            // A mob from another mod that cannot set itself up here still spawns bare.
        }
        return mob;
    }

    private static void applyScaling(Mob mob, int siegeLevel, SiegeConfig cfg,
                                     SiegeEvent event, boolean elite) {
        double healthMul = SiegeMath.healthMultiplier(siegeLevel, cfg) * event.healthMultiplier(cfg);
        double damageMul = SiegeMath.damageMultiplier(siegeLevel, cfg) * event.damageMultiplier(cfg);
        if (elite) {
            healthMul *= cfg.eliteHealthMultiplier;
            damageMul *= cfg.eliteDamageMultiplier;
        }

        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * healthMul);
            mob.setHealth((float) health.getBaseValue());
        }
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * damageMul);
        }
        // Without a long follow range the mobs lose interest halfway to the base.
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(Math.max(follow.getBaseValue(), 72.0));
        }
    }

    /**
     * Gives some mobs a tool. Only an armed mob can break blocks later, and the
     * kind of tool decides which blocks. The champion always carries a pickaxe
     * so it can always reach the base.
     */
    private static void equipTool(Mob mob, SiegeConfig cfg, Random random, boolean elite) {
        if (!cfg.toolGatedDigging) {
            return;
        }
        // A mob that already holds something keeps it; its own tool counts too.
        ItemStack held = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if (held != null && !held.isEmpty()) {
            return;
        }
        ToolKind kind = elite ? ToolKind.PICKAXE : ToolPicker.pick(random, cfg);
        if (kind == ToolKind.NONE) {
            return;
        }
        String itemId = ToolPicker.itemId(kind, cfg);
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null) {
            return;
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        // Never drop it, otherwise sieges turn into a tool farm.
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    /** Looks for open ground in a ring around the anchor. */
    public static BlockPos findSpawnSpot(ServerLevel world, BlockPos anchor, SiegeConfig cfg,
                                          Random random, double distanceBonus) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = cfg.spawnMinDistance + Math.max(0.0, distanceBonus)
                    + random.nextDouble() * (cfg.spawnMaxDistance - cfg.spawnMinDistance);
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!world.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spot = new BlockPos(x, y, z);
            // Needs two free blocks of room and no water or lava underfoot.
            if (!world.getBlockState(spot).isAir() || !world.getBlockState(spot.above()).isAir()) {
                continue;
            }
            if (!world.getBlockState(spot.below()).getFluidState().isEmpty()) {
                continue;
            }
            return spot;
        }
        NightSiegeMod.LOGGER.debug("[Night Siege] kein Spawnplatz gefunden");
        return null;
    }
}
