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
 * Applies the archer's skills to the arrows they loose from a bow.
 *
 * <p>Every fresh arrow is looked at exactly once: a tag marks it as handled so
 * it is not boosted again when its chunk loads later, and arrows the mod
 * spawns itself carry the same tag so a double shot never multiplies.</p>
 */
public final class ArcherEffects {

    private static final String HANDLED = "nightsiege_arrow";
    /** Speed of a fully drawn vanilla bow shot, in blocks per tick. */
    private static final double FULL_SPEED = 3.0;
    /** Vanilla base damage of an arrow. */
    private static final double BASE_DAMAGE = 2.0;
    /** The heavy path trades speed for weight: a short pause after every shot. */
    private static final int HEAVY_COOLDOWN_TICKS = 12;
    /** Spread of the second arrow, in degrees to either side. */
    private static final float DOUBLE_SPREAD = 4.0f;

    private final Map<UUID, Long> powerReadyAt = new HashMap<>();

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
        if (weapon == null || !weapon.is(Items.BOW)) {
            return;   // Crossbows, dispensers and thrown arrows stay vanilla.
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != SkillClass.ARCHER) {
            return;
        }

        // Swift path: a short draw is pulled towards full force.
        double assist = Math.min(1.0, tree.totalBonus(SkillEffect.DRAW_ASSIST, skills));
        if (assist > 0 && !arrow.isCritArrow()) {
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

        // Both paths and the trunk: extra damage on bow arrows only.
        double damage = BASE_DAMAGE + tree.totalBonus(SkillEffect.ARROW_DAMAGE, skills);
        arrow.setBaseDamage(damage);

        if (SkillTree.PATH_HEAVY.equals(skills.pathTaken(tree))) {
            player.getCooldowns().addCooldown(weapon, HEAVY_COOLDOWN_TICKS);
            tryPowerShot(arrow, level, player, skills, damage);
        }

        if (tree.totalBonus(SkillEffect.DOUBLE_SHOT, skills) >= 1.0) {
            spawnTwin(arrow, level, player, weapon, damage);
        }
    }

    /** Heavy path finale: a full draw off cooldown hits several times harder. */
    private void tryPowerShot(AbstractArrow arrow, ServerLevel level, ServerPlayer player,
                              PlayerSkills skills, double damage) {
        int rank = skills.levelOf("power_shot");
        if (rank <= 0 || !arrow.isCritArrow()) {
            return;
        }
        long now = level.getGameTime();
        long readyAt = powerReadyAt.getOrDefault(player.getUUID(), 0L);
        if (now < readyAt) {
            long seconds = (readyAt - now + 19) / 20;
            actionBar(player, Component.translatable("nightsiege.msg.power_wait", seconds));
            return;
        }
        // Rank 1: x3, rank 3: x4.5. Cooldown 30 s, 25 s, 20 s.
        double multiplier = 2.25 + 0.75 * rank;
        arrow.setBaseDamage(damage * multiplier);
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.25));
        arrow.entityTags().add("nightsiege_power");
        powerReadyAt.put(player.getUUID(), now + 20L * (35 - 5 * rank));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.9f);
        level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(),
                14, 0.25, 0.25, 0.25, 0.08);
        actionBar(player, Component.translatable("nightsiege.msg.power_fired"));
    }

    /** Swift path finale: a second arrow beside the first. It cannot be picked up. */
    private static void spawnTwin(AbstractArrow original, ServerLevel level, ServerPlayer player,
                                  ItemStack weapon, double damage) {
        Arrow twin = new Arrow(level, player, new ItemStack(Items.ARROW), weapon.copy());
        twin.entityTags().add(HANDLED);
        twin.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        twin.setBaseDamage(damage);
        twin.setCritArrow(original.isCritArrow());
        float side = level.getRandom().nextBoolean() ? 1f : -1f;
        Vec3 motion = original.getDeltaMovement().yRot((float) Math.toRadians(DOUBLE_SPREAD * side));
        twin.setPos(original.getX(), original.getY(), original.getZ());
        twin.setDeltaMovement(motion);
        level.addFreshEntity(twin);
    }

    /** Called when a player's class is reset or the server stops. */
    public void forget(UUID player) {
        powerReadyAt.remove(player);
    }

    private static void actionBar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
}
