package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The warrior's and guardian's skills that act in a fight rather than on an attribute.
 *
 * <p>Ultimates are fired the same way for every class: sneak and hit a monster.
 * Each one has its own cooldown; firing it again too early only shows how long
 * is left. The guardian angel is the exception: it fires by itself, once a night.</p>
 */
public final class CombatEffects {

    /** Heal auras pulse this often. */
    private static final int HEAL_INTERVAL = 60;
    private static final double HEAL_RADIUS = 8.0;

    private final SkillTree tree;
    private final Function<ServerPlayer, PlayerSkills> skillsFor;
    private final Predicate<Entity> isChampion;
    private final Random random = new Random();

    private final Map<UUID, Long> ultReadyAt = new HashMap<>();
    /** Guardians whose angel has already saved someone this night. */
    private final Set<UUID> angelUsed = new HashSet<>();
    /** Set while the mod deals its own bonus damage, so that hit is not boosted again. */
    private boolean dealingBonus;
    private int healCounter;

    public CombatEffects(SkillTree tree, Function<ServerPlayer, PlayerSkills> skillsFor,
                         Predicate<Entity> isChampion) {
        this.tree = tree;
        this.skillsFor = skillsFor;
        this.isChampion = isChampion;
    }

    /** A new night: every guardian angel is ready again. */
    public void newSiege() {
        angelUsed.clear();
    }

    // ------------------------------------------------------------------
    // ultimates: sneak + hit
    // ------------------------------------------------------------------

    public void onAttack(Object playerObject, Object targetObject) {
        if (!(playerObject instanceof ServerPlayer player) || !player.isShiftKeyDown()
                || !(targetObject instanceof LivingEntity target) || !(target instanceof Enemy)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null) {
            return;
        }
        SkillEffect ult = null;
        int rank = 0;
        for (SkillEffect candidate : new SkillEffect[]{SkillEffect.WHIRLWIND, SkillEffect.EXECUTE,
                SkillEffect.SHIELD_CHARGE, SkillEffect.TAUNT}) {
            int r = (int) Math.round(tree.totalBonus(candidate, skills));
            if (r > 0) {
                ult = candidate;
                rank = r;
                break;
            }
        }
        if (ult == null) {
            return;
        }
        long now = level.getGameTime();
        long readyAt = ultReadyAt.getOrDefault(player.getUUID(), 0L);
        if (now < readyAt) {
            actionBar(player, Component.translatable("nightsiege.msg.ult_wait", (readyAt - now + 19) / 20));
            return;
        }
        boolean fired = switch (ult) {
            case WHIRLWIND -> whirlwind(level, player, rank);
            case EXECUTE -> execute(level, player, target, rank);
            case SHIELD_CHARGE -> shieldCharge(level, player, rank);
            case TAUNT -> taunt(level, player, rank);
            default -> false;
        };
        if (fired) {
            ultReadyAt.put(player.getUUID(), now + 600L - 100L * rank);   // 25 s / 20 s / 15 s
            actionBar(player, Component.translatable("nightsiege.skill." + ultNode(ult)));
        }
    }

    private static String ultNode(SkillEffect ult) {
        return switch (ult) {
            case WHIRLWIND -> "whirlwind";
            case EXECUTE -> "execute";
            case SHIELD_CHARGE -> "shield_charge";
            default -> "taunt";
        };
    }

    /** Berserker: hits everything around and throws it back. */
    private boolean whirlwind(ServerLevel level, ServerPlayer player, int rank) {
        double r = 3.5 + 0.5 * rank;
        float damage = 4f + 3f * rank;   // 7 / 10 / 13
        Vec3 center = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : enemies(level, new AABB(center, center).inflate(r, 1.5, r))) {
            if (victim.distanceToSqr(center) > r * r) {
                continue;
            }
            bonusHit(level, victim, source, damage);
            Vec3 away = victim.position().subtract(center);
            if (away.lengthSqr() > 1.0e-4) {
                away = away.normalize().scale(0.8);
                victim.push(away.x, 0.3, away.z);
            }
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x + Math.cos(a) * r * 0.6, center.y + 1.0,
                    center.z + Math.sin(a) * r * 0.6, 1, 0, 0, 0, 0);
        }
        play(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
        return true;
    }

    /** Blademaster: a monster below the threshold dies on the spot. */
    private boolean execute(ServerLevel level, ServerPlayer player, LivingEntity target, int rank) {
        double threshold = 0.2 + 0.05 * rank;   // 25 % / 30 % / 35 % health
        if (target.getHealth() > target.getMaxHealth() * threshold) {
            actionBar(player, Component.translatable("nightsiege.msg.execute_too_healthy",
                    Math.round(threshold * 100)));
            return false;
        }
        bonusHit(level, target, level.damageSources().playerAttack(player), target.getHealth() + 1000f);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(),
                30, 0.4, 0.5, 0.4, 0.3);
        play(level, player, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
        play(level, player, SoundEvents.ANVIL_LAND, 0.4f, 0.5f);
        return true;
    }

    /** Shieldbearer: everything in front takes the shield and flies back. */
    private boolean shieldCharge(ServerLevel level, ServerPlayer player, int rank) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0e-4) {
            return false;
        }
        flat = flat.normalize();
        float damage = 3f + 3f * rank;   // 6 / 9 / 12
        Vec3 eye = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : enemies(level, new AABB(eye, eye).inflate(6.0, 2.0, 6.0))) {
            Vec3 to = victim.position().subtract(eye);
            double dist = to.length();
            if (dist > 6.0 || dist < 1.0e-4) {
                continue;
            }
            Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
            if (dir.dot(flat) < 0.5) {
                continue;   // only a cone of about 60 degrees to each side
            }
            bonusHit(level, victim, source, damage);
            victim.push(flat.x * 1.4, 0.35, flat.z * 1.4);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
        }
        for (int i = 1; i <= 6; i++) {
            Vec3 p = eye.add(flat.scale(i)).add(0, 1.0, 0);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0.02);
        }
        play(level, player, SoundEvents.SHIELD_BLOCK.value(), 1.0f, 0.6f);
        play(level, player, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.8f);
        return true;
    }

    /** Bulwark: every monster around turns on the guardian, who hardens. */
    private boolean taunt(ServerLevel level, ServerPlayer player, int rank) {
        double r = 7.0 + 3.0 * rank;   // 10 / 13 / 16
        Vec3 center = player.position();
        for (LivingEntity victim : enemies(level, new AABB(center, center).inflate(r, 4.0, r))) {
            if (victim instanceof Mob mob && victim.distanceToSqr(center) <= r * r) {
                mob.setTarget(player);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, victim.getX(), victim.getY() + victim.getBbHeight() + 0.3,
                        victim.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60 + 20 * rank, 1));
        play(level, player, SoundEvents.RAVAGER_ROAR, 1.0f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------
    // passive combat skills
    // ------------------------------------------------------------------

    public void afterDamage(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
        if (dealingBonus || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        // A warrior's melee hit on a monster.
        if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player
                && victim instanceof Enemy && taken > 0) {
            PlayerSkills skills = skillsFor.apply(player);
            if (skills != null && skills.clazz() == SkillClass.WARRIOR) {
                warriorHit(level, player, skills, victim, taken);
            }
        }
        // A shield catching a monster's blow.
        if (blocked && victim instanceof ServerPlayer player
                && source.getEntity() instanceof LivingEntity attacker && attacker instanceof Enemy) {
            PlayerSkills skills = skillsFor.apply(player);
            double counter = skills == null ? 0 : tree.totalBonus(SkillEffect.COUNTER, skills);
            if (counter > 0) {
                bonusHit(level, attacker, level.damageSources().playerAttack(player), (float) counter);
                Vec3 away = attacker.position().subtract(player.position());
                if (away.lengthSqr() > 1.0e-4) {
                    away = away.normalize().scale(0.9);
                    attacker.push(away.x, 0.3, away.z);
                }
                play(level, player, SoundEvents.SHIELD_BLOCK.value(), 0.8f, 1.4f);
            }
        }
    }

    private void warriorHit(ServerLevel level, ServerPlayer player, PlayerSkills skills,
                            LivingEntity victim, float taken) {
        double bonus = 0.0;
        double rage = tree.totalBonus(SkillEffect.RAGE, skills);
        if (rage > 0) {
            double missing = 1.0 - player.getHealth() / Math.max(1f, player.getMaxHealth());
            bonus += rage * missing;
        }
        boolean crit = random.nextDouble() < tree.totalBonus(SkillEffect.CRIT_CHANCE, skills);
        if (crit) {
            bonus += 0.5;
        }
        if (isChampion.test(victim)) {
            bonus += tree.totalBonus(SkillEffect.CHAMPION_BANE, skills);
        }
        float extra = (float) (taken * bonus);
        if (extra > 0.05f && victim.isAlive()) {
            bonusHit(level, victim, level.damageSources().playerAttack(player), extra);
        }
        if (crit) {
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(),
                    12, 0.3, 0.4, 0.3, 0.2);
            play(level, player, SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
        }
        double steal = tree.totalBonus(SkillEffect.LIFESTEAL, skills);
        if (steal > 0) {
            player.heal((float) ((taken + extra) * steal));
        }
    }

    /** Deals damage the mod adds on top, through the hit cooldown, without boosting it again. */
    private void bonusHit(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
        dealingBonus = true;
        try {
            victim.invulnerableTime = 0;
            victim.hurtServer(level, source, amount);
        } finally {
            dealingBonus = false;
        }
    }

    // ------------------------------------------------------------------
    // guardian: angel and healing
    // ------------------------------------------------------------------

    /** Returns false when a guardian angel catches this player. */
    public boolean allowDeath(LivingEntity dying) {
        if (!(dying instanceof ServerPlayer victim) || !(victim.level() instanceof ServerLevel level)) {
            return true;
        }
        for (ServerPlayer guardian : level.players()) {
            if (angelUsed.contains(guardian.getUUID())) {
                continue;
            }
            PlayerSkills skills = skillsFor.apply(guardian);
            int rank = skills == null ? 0 : (int) Math.round(tree.totalBonus(SkillEffect.GUARDIAN_ANGEL, skills));
            if (rank <= 0) {
                continue;
            }
            double reach = 8.0 + 8.0 * rank;   // 16 / 24 / 32 blocks
            if (guardian != victim && guardian.distanceToSqr(victim) > reach * reach) {
                continue;
            }
            angelUsed.add(guardian.getUUID());
            victim.setHealth(4f + 2f * rank);
            victim.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            victim.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1));
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, victim.getX(), victim.getY(1.0), victim.getZ(),
                    40, 0.4, 0.6, 0.4, 0.4);
            play(level, victim, SoundEvents.TOTEM_USE, 0.8f, 1.2f);
            victim.sendSystemMessage(Component.translatable("nightsiege.msg.angel_saved", guardian.getDisplayName()));
            if (guardian != victim) {
                guardian.sendSystemMessage(Component.translatable("nightsiege.msg.angel_used", victim.getDisplayName()));
            }
            return false;
        }
        return true;
    }

    public void tick(MinecraftServer server) {
        if (++healCounter < HEAL_INTERVAL) {
            return;
        }
        healCounter = 0;
        for (ServerPlayer medic : server.getPlayerList().getPlayers()) {
            PlayerSkills skills = skillsFor.apply(medic);
            double heal = skills == null ? 0 : tree.totalBonus(SkillEffect.HEAL_AURA, skills);
            if (heal <= 0 || !(medic.level() instanceof ServerLevel level)) {
                continue;
            }
            for (ServerPlayer mate : level.players()) {
                if (mate.isAlive() && mate.distanceToSqr(medic) <= HEAL_RADIUS * HEAL_RADIUS
                        && mate.getHealth() < mate.getMaxHealth()) {
                    mate.heal((float) heal);
                    level.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 2.0, mate.getZ(),
                            1, 0.2, 0.1, 0.2, 0.0);
                }
            }
        }
    }

    public void forget(UUID player) {
        ultReadyAt.remove(player);
    }

    // ------------------------------------------------------------------

    private static List<LivingEntity> enemies(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive());
    }

    private static void play(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void actionBar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
}
