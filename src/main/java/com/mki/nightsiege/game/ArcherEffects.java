package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Applies the archer's skills to the arrows they loose from a bow or crossbow.
 *
 * <p>Every fresh arrow is looked at exactly once: a tag marks it as handled so
 * it is not boosted again when its chunk loads later, and arrows the mod
 * spawns itself carry the same tag so extra arrows never multiply.</p>
 */
public final class ArcherEffects {

    public static final String HANDLED = "nightsiege_arrow";
    /** Speed of a fully drawn vanilla bow shot, in blocks per tick. */
    private static final double FULL_SPEED = 3.0;
    /** Vanilla base damage of an arrow. */
    private static final double BASE_DAMAGE = 2.0;
    /** The heavy path trades speed for weight: a short pause after every bow shot. */
    private static final int HEAVY_COOLDOWN_TICKS = 12;

    /** Power shot armed by the ultimate key: game time it runs out, and its rank. */
    private final Map<UUID, long[]> powerArmed = new HashMap<>();
    /** Shots fired, for the volley's every fourth. */
    private final Map<UUID, Integer> volleyCount = new HashMap<>();
    /** Power shot of the sniper: goes through everything in its way. */
    public static final String SNIPER_TAG = "nightsiege_sniper";
    /** Arrow storm running: game time it ends. */
    private final Map<UUID, Long> stormUntil = new HashMap<>();

    /** Ultimate of the heavy path: the next shot within ten seconds is a power shot. */
    public void armPowerShot(ServerPlayer player, long now, int rank) {
        powerArmed.put(player.getUUID(), new long[]{now + 200, rank});
    }

    /** Ultimate of the swift path: for a few seconds every shot fans out into three. */
    public void startStorm(ServerPlayer player, long until) {
        stormUntil.put(player.getUUID(), until);
    }

    /**
     * @param skillsFor returns the skills that count for this player right now,
     *                  or null when their skills are not live
     */
    public void onEntityLoad(Entity entity, ServerLevel level, SkillTree tree,
                             Function<ServerPlayer, PlayerSkills> skillsFor) {
        if (!(entity instanceof AbstractArrow arrow) || arrow.entityTags().contains(HANDLED)) {
            return;
        }
        arrow.entityTags().add(HANDLED);
        if (!(arrow.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack weapon = arrow.getWeaponItem();
        // Any bow or crossbow counts, the infernite ones included.
        boolean bow = weapon != null && weapon.getItem() instanceof net.minecraft.world.item.BowItem;
        boolean crossbow = weapon != null && weapon.getItem() instanceof net.minecraft.world.item.CrossbowItem;
        if (!bow && !crossbow) {
            return;   // Dispensers and thrown arrows stay vanilla.
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != SkillClass.ARCHER) {
            return;
        }
        // The skills of a way only work with its weapon.
        String way = skills.pathTaken(tree);
        if ((SkillTree.WAY_BOW.equals(way) && !bow) || (SkillTree.WAY_CROSSBOW.equals(way) && !crossbow)) {
            return;
        }
        long now = level.getGameTime();

        // A short bow draw is pulled towards full force.
        double assist = Math.min(1.0, tree.totalBonus(SkillEffect.DRAW_ASSIST, skills));
        if (bow && assist > 0 && !arrow.isCritArrow()) {
            Vec3 motion = arrow.getDeltaMovement();
            double speed = motion.length();
            if (speed > 1.0e-4) {
                double drawn = Math.min(1.0, speed / FULL_SPEED);
                double boosted = drawn + assist * (1.0 - drawn);
                arrow.setDeltaMovement(motion.scale(boosted / drawn));
                if (boosted >= 0.98) {
                    arrow.setCritArrow(true);
                }
            }
        }

        double damage = BASE_DAMAGE + tree.totalBonus(SkillEffect.ARROW_DAMAGE, skills);
        if (crossbow) {
            damage += tree.totalBonus(SkillEffect.CROSSBOW_DAMAGE, skills);
        }
        if (SkillTree.has(skills, "a_deadeye")) {
            damage *= 1.35;
        }
        if (SkillTree.has(skills, "a_skirmisher")) {
            damage *= 0.9;
        }
        if (crossbow && SkillTree.has(skills, "a_siegeengine")) {
            damage *= 1.4;
        }
        arrow.setBaseDamage(damage);
        if (tree.totalBonus(SkillEffect.FIRE_ARROWS, skills) > 0) {
            arrow.igniteForSeconds(100f);
        }

        // The heavy bow trades speed for weight: a short pause after every shot.
        if (bow && SkillTree.has(skills, "heavy_draw")) {
            player.getCooldowns().addCooldown(weapon, HEAVY_COOLDOWN_TICKS);
        }

        // Quiver: now and then the arrow comes back.
        double quiver = tree.totalBonus(SkillEffect.QUIVER, skills);
        if (quiver > 0 && !player.getAbilities().instabuild && arrow.pickup == AbstractArrow.Pickup.ALLOWED
                && level.getRandom().nextDouble() < quiver) {
            ItemStack back = arrow.getPickupItemStackOrigin().copyWithCount(1);
            if (!back.isEmpty() && player.getInventory().add(back)) {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
        }

        long[] armed = powerArmed.get(player.getUUID());
        if (armed != null) {
            powerArmed.remove(player.getUUID());
            if (now <= armed[0]) {
                firePowerShot(arrow, level, player, damage, (int) armed[1], SkillTree.has(skills, "a_sniper"));
            }
        }

        boolean fireVolley = tree.totalBonus(SkillEffect.FIRE_VOLLEY, skills) > 0;
        double extraDamage = fireVolley ? damage * 1.5 : damage;
        if (tree.totalBonus(SkillEffect.DOUBLE_SHOT, skills) >= 1.0) {
            spawnTwin(arrow, level, player, weapon, extraDamage, 4.0f, fireVolley);
        }
        if (crossbow && tree.totalBonus(SkillEffect.MULTI_BOLT, skills) > 0) {
            spawnTwin(arrow, level, player, weapon, damage, 7.0f, false);
            spawnTwin(arrow, level, player, weapon, damage, -7.0f, false);
        }
        if (tree.totalBonus(SkillEffect.VOLLEY, skills) > 0) {
            int shots = volleyCount.merge(player.getUUID(), 1, Integer::sum);
            if (shots % 4 == 0) {
                spawnTwin(arrow, level, player, weapon, extraDamage, 8.0f, fireVolley);
                spawnTwin(arrow, level, player, weapon, extraDamage, -8.0f, fireVolley);
            }
        }
        Long storm = stormUntil.get(player.getUUID());
        if (storm != null) {
            if (now <= storm) {
                boolean burning = SkillTree.has(skills, "a_firestorm");
                spawnTwin(arrow, level, player, weapon, damage, 8.0f, burning);
                spawnTwin(arrow, level, player, weapon, damage, -8.0f, burning);
                if (SkillTree.has(skills, "a_tempest")) {
                    spawnTwin(arrow, level, player, weapon, damage, 16.0f, burning);
                    spawnTwin(arrow, level, player, weapon, damage, -16.0f, burning);
                }
                if (burning) {
                    arrow.igniteForSeconds(100f);
                }
                level.sendParticles(ParticleTypes.FLAME, arrow.getX(), arrow.getY(), arrow.getZ(),
                        6, 0.2, 0.2, 0.2, 0.02);
            } else {
                stormUntil.remove(player.getUUID());
            }
        }
    }

    /** Rank 1: x3, rank 3: x4.5 damage, and a faster flight. The sniper's shot hits half again as hard and pierces. */
    private static void firePowerShot(AbstractArrow arrow, ServerLevel level, ServerPlayer player,
                                      double damage, int rank, boolean sniper) {
        arrow.setBaseDamage(damage * (2.25 + 0.75 * rank) * (sniper ? 1.5 : 1.0));
        if (sniper) {
            arrow.entityTags().add(SNIPER_TAG);
        }
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.25));
        arrow.setCritArrow(true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.9f);
        level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(),
                14, 0.25, 0.25, 0.25, 0.08);
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("nightsiege.msg.power_fired")));
    }

    /** A second arrow beside the first, turned by the given angle. It cannot be picked up. */
    private static void spawnTwin(AbstractArrow original, ServerLevel level, ServerPlayer player,
                                  ItemStack weapon, double damage, float degrees, boolean burning) {
        Arrow twin = new Arrow(level, player, new ItemStack(Items.ARROW), weapon.copy());
        twin.entityTags().add(HANDLED);
        twin.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        twin.setBaseDamage(damage);
        twin.setCritArrow(original.isCritArrow());
        if (degrees == 4.0f && level.getRandom().nextBoolean()) {
            degrees = -degrees;
        }
        Vec3 motion = original.getDeltaMovement().yRot((float) Math.toRadians(degrees));
        twin.setPos(original.getX(), original.getY(), original.getZ());
        twin.setDeltaMovement(motion);
        if (burning) {
            twin.igniteForSeconds(100f);
        }
        level.addFreshEntity(twin);
    }

    /** Called when a player's class is reset. */
    public void forget(UUID player) {
        powerArmed.remove(player);
        stormUntil.remove(player);
        volleyCount.remove(player);
    }
}
