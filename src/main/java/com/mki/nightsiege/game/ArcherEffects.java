package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
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

    /** Power shot armed by the ultimate key: game time it runs out, and its rank. */
    private final Map<UUID, long[]> powerArmed = new HashMap<>();
    /** Shots fired, for the volley's every fourth. */
    private final Map<UUID, Integer> volleyCount = new HashMap<>();
    /** Power shot of the sniper: goes through everything in its way. */
    public static final String SNIPER_TAG = "nightsiege_sniper";
    /** Standfest: the crossbow bolt was shot while sneaking (checked at the shot, not at the hit). */
    public static final String BRACED_TAG = "nightsiege_braced";
    /** Arrow storm running: game time it ends. */
    private final Map<UUID, Long> stormUntil = new HashMap<>();

    /** Ultimate of the heavy path: the next shot within ten seconds is a power shot. */
    public void armPowerShot(ServerPlayer player, long now, int rank) {
        powerArmed.put(player.getUUID(), new long[]{now + (long) SkillValues.get("power_shot", "armed", 200), rank});
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
        // The quick shot draws his bow full at once (Simon 30.10.: short taps fell to the ground): his
        // arrows always fly at full force, and they hit as hard as the draw and the assist give, so the
        // damage stays as it was (an arrow's damage is its speed times its base damage).
        boolean quick = bow && SkillTree.SUB_SWIFT.equals(tree.subclassOf(skills));
        double drawScale = 1.0;
        if (bow && (quick || assist > 0) && !arrow.isCritArrow()) {
            Vec3 motion = arrow.getDeltaMovement();
            double speed = motion.length();
            if (speed > 1.0e-4) {
                double drawn = Math.min(1.0, speed / FULL_SPEED);
                double boosted = drawn + assist * (1.0 - drawn);
                if (quick) {
                    arrow.setDeltaMovement(motion.scale(Math.max(1.0, FULL_SPEED / speed)));
                    drawScale = boosted;
                } else {
                    arrow.setDeltaMovement(motion.scale(boosted / drawn));
                }
                if (boosted >= 0.98) {
                    arrow.setCritArrow(true);
                }
            }
        }

        double damage = BASE_DAMAGE + tree.totalBonus(SkillEffect.ARROW_DAMAGE, skills);
        if (crossbow) {
            damage += tree.totalBonus(SkillEffect.CROSSBOW_DAMAGE, skills);
        }
        // Steady hand (balance update, 26 October 2026): a fully drawn bow shot hits a little harder,
        // so the node still counts next to the quick draw, which already pulls every shot to full.
        int steady = skills.levelOf("a_steady");
        if (bow && steady > 0 && arrow.isCritArrow()) {
            damage *= 1.0 + steady * SkillValues.get("a_steady", "full", 0.05);
        }
        // Köcherfüllung (crate gain): +4 % arrow damage per stage (was +16 arrows until 26 October 2026,
        // when every kit bow and crossbow got Infinity).
        com.mki.nightsiege.core.CrateGains.Gain fill = com.mki.nightsiege.core.CrateGains.get("koecherfuellung");
        int fillStage = fill == null ? 0 : fill.level(skills.gainStage(SkillClass.ARCHER, "koecherfuellung"));
        if (fillStage > 0) {
            damage *= 1.0 + fillStage * SkillValues.get("koecherfuellung", "dmg", 0.04);
        }
        // Stärke (crate gain) gives the quick shot's bow no Power (it has none): +5 % arrow damage per stage instead (27.10.).
        if (bow && SkillTree.SUB_SWIFT.equals(tree.subclassOf(skills))) {
            com.mki.nightsiege.core.CrateGains.Gain power = com.mki.nightsiege.core.CrateGains.get("staerke");
            int powerStage = power == null ? 0 : power.level(skills.gainStage(SkillClass.ARCHER, "staerke"));
            if (powerStage > 0) {
                damage *= 1.0 + powerStage * 0.05;
            }
        }
        if (SkillTree.has(skills, "a_deadeye")) {
            damage *= SkillValues.get("a_deadeye", "mult", 1.35);
        }
        if (SkillTree.has(skills, "a_skirmisher")) {
            damage *= SkillValues.get("a_skirmisher", "mult", 0.9);
        }
        if (crossbow && SkillTree.has(skills, "a_siegeengine")) {
            damage *= SkillValues.get("a_siegeengine", "mult", 1.4);
        }
        damage *= drawScale;   // the quick shot: flies full, hits like the draw (extra arrows too)
        arrow.setBaseDamage(damage);
        // Standfest counts when the bolt leaves the crossbow while sneaking (26 October 2026: it used to
        // check the sneak key when the bolt landed).
        if (crossbow && player.isShiftKeyDown()) {
            arrow.entityTags().add(BRACED_TAG);
        }
        if (tree.totalBonus(SkillEffect.FIRE_ARROWS, skills) > 0) {
            arrow.igniteForSeconds(100f);
        }

        // The heavy bow trades speed for weight: a short pause after every shot.
        if (bow && SkillTree.has(skills, "heavy_draw")) {
            player.getCooldowns().addCooldown(weapon, (int) SkillValues.get("heavy_draw", "pause", 12));
        }

        // Quiver: now and then the arrow comes back. A bow with Infinity uses up no arrows,
        // so there the quiver hands over a free extra arrow instead (balance update, 26 October 2026).
        double quiver = tree.totalBonus(SkillEffect.QUIVER, skills);
        if (quiver > 0 && !player.getAbilities().instabuild && level.getRandom().nextDouble() < quiver) {
            if (arrow.pickup == AbstractArrow.Pickup.ALLOWED) {
                ItemStack back = arrow.getPickupItemStackOrigin().copyWithCount(1);
                if (!back.isEmpty() && player.getInventory().add(back)) {
                    arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }
            } else if (bow && arrow.pickup == AbstractArrow.Pickup.CREATIVE_ONLY) {
                spawnTwin(arrow, level, player, weapon, damage * SkillValues.get("a_quiver", "extra", 0.5), 2.0f, false);
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
        // Simon 26.10.: the quick shot's extra arrows (double shot, volley, arrow storm) hit softer,
        // about 10 % less on groups; the main arrow stays as it is.
        double extraScale = SkillTree.SUB_SWIFT.equals(tree.subclassOf(skills))
                ? SkillValues.get("swift_draw", "extra", 0.85) : 1.0;
        double extraDamage = (fireVolley ? damage * SkillValues.get("a_firevolley", "mult", 1.5) : damage) * extraScale;
        if (tree.totalBonus(SkillEffect.DOUBLE_SHOT, skills) >= 1.0) {
            String twinNode = crossbow ? "x_cross_double" : "double_shot";
            float side = (float) SkillValues.get(twinNode, "angle", 4.0);
            spawnTwin(arrow, level, player, weapon, extraDamage, level.getRandom().nextBoolean() ? side : -side, fireVolley);
        }
        if (crossbow && tree.totalBonus(SkillEffect.MULTI_BOLT, skills) > 0) {
            spawnFan(arrow, level, player, weapon, damage, (int) SkillValues.get("a_multiload", "arrows", 2),
                    (float) SkillValues.get("a_multiload", "angle", 7.0), false);
        }
        if (tree.totalBonus(SkillEffect.VOLLEY, skills) > 0) {
            int shots = volleyCount.merge(player.getUUID(), 1, Integer::sum);
            if (shots % Math.max(1, (int) SkillValues.get("a_volley", "every", 4)) == 0) {
                spawnFan(arrow, level, player, weapon, extraDamage, (int) SkillValues.get("a_volley", "arrows", 2),
                        (float) SkillValues.get("a_volley", "angle", 8.0), fireVolley);
            }
        }
        Long storm = stormUntil.get(player.getUUID());
        if (storm != null) {
            if (now <= storm) {
                boolean burning = SkillTree.has(skills, "a_firestorm");
                spawnFan(arrow, level, player, weapon, damage * extraScale, (int) SkillValues.get("arrow_storm", "arrows", 2),
                        (float) SkillValues.get("arrow_storm", "angle", 8.0), burning);
                if (SkillTree.has(skills, "a_tempest")) {
                    spawnFan(arrow, level, player, weapon, damage * extraScale, (int) SkillValues.get("a_tempest", "arrows", 2),
                            (float) SkillValues.get("a_tempest", "angle", 16.0), burning);
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
        double mult = SkillValues.get("power_shot", "mult" + rank, 2.25 + 0.75 * rank);
        arrow.setBaseDamage(damage * mult * (sniper ? SkillValues.get("a_sniper", "mult", 1.5) : 1.0));
        if (sniper) {
            arrow.entityTags().add(SNIPER_TAG);
        }
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(SkillValues.get("power_shot", "speed", 1.25)));
        arrow.setCritArrow(true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.9f);
        level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(),
                14, 0.25, 0.25, 0.25, 0.08);
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("nightsiege.msg.power_fired")));
    }

    /** {@code count} extra arrows in pairs to the right and left, each pair one {@code step} further out. */
    private static void spawnFan(AbstractArrow original, ServerLevel level, ServerPlayer player,
                                 ItemStack weapon, double damage, int count, float step, boolean burning) {
        for (int i = 0; i < count; i++) {
            float angle = step * (i / 2 + 1) * (i % 2 == 0 ? 1f : -1f);
            spawnTwin(original, level, player, weapon, damage, angle, burning);
        }
    }

    /** A second arrow beside the first, turned by the given angle. It cannot be picked up. */
    private static void spawnTwin(AbstractArrow original, ServerLevel level, ServerPlayer player,
                                  ItemStack weapon, double damage, float degrees, boolean burning) {
        Arrow twin = new Arrow(level, player, new ItemStack(Items.ARROW), weapon.copy());
        twin.entityTags().add(HANDLED);
        twin.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        twin.setBaseDamage(damage);
        twin.setCritArrow(original.isCritArrow());
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
