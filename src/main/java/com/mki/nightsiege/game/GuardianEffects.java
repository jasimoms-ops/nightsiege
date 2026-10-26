package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

/**
 * The guardian, the tank of the team.
 *
 * <p>Every guardian draws the anger of monsters that go for a teammate, blocks
 * perfectly when the shield comes up just in time (the attacker is stunned) and
 * earns skill points for damage caught and healing given. The way of the shield
 * adds the shield's tricks, the way of blessing healing and barriers. The shield
 * action is a left click while blocking.</p>
 */
public final class GuardianEffects {

    private static GuardianEffects instance;

    /** Ticks after the shield is up in which a block counts as perfect. */
    static final int PERFECT_WINDOW = 10;
    /** How long a guardian with the riposte may block at a stretch, and the pause after. */
    static final int BLOCK_LIMIT = 60;
    static final int BLOCK_PAUSE = 40;
    /** Radius in which monsters that attack a teammate turn on the guardian. */
    static final double AGGRO_RADIUS = 6.0;
    private static final int THROW_COOLDOWN = 160;
    private static final int DOME_COOLDOWN = 500;
    private static final int HAND_COOLDOWN = 120;
    private static final Identifier ANCHOR_KB = ModContent.id("guardian_anchor");
    private static final Identifier BARRIER_CAP = ModContent.id("guardian_barrier");

    /** Hands caught damage and healing to the siege, which turns it into skill points. */
    public interface Credit {
        void accept(ServerPlayer player, double amount, Entity from);
    }

    private final SkillTree tree;
    private final Function<ServerPlayer, PlayerSkills> skillsFor;
    private final CombatEffects combat;
    private Credit credit = (player, amount, from) -> {
    };
    private final Random random = new Random();

    private final Map<UUID, Long> blockStart = new HashMap<>();
    private final Map<UUID, Long> actionReadyAt = new HashMap<>();
    private final Map<UUID, Long> handReadyAt = new HashMap<>();
    private final Map<UUID, Long> counterUntil = new HashMap<>();
    private final Map<UUID, Double> revenge = new HashMap<>();
    private final Map<UUID, Long> lastStandReadyAt = new HashMap<>();
    private final Map<UUID, Long> tauntUntil = new HashMap<>();
    /** Who gave a player the barrier they carry: the life bond heals when it breaks. */
    private final Map<UUID, UUID> barrierFrom = new HashMap<>();
    /** A player's absorption just before a hit, to see a barrier break. */
    private final Map<UUID, Float> absorbBefore = new HashMap<>();
    private final List<Zone> zones = new ArrayList<>();
    private int counter;

    private enum ZoneKind { DOME, SANCTUARY, LIFESPRING }

    private record Zone(ZoneKind kind, UUID owner, ServerLevel level, Vec3 center, double radius, long until) {
    }

    public GuardianEffects(SkillTree tree, Function<ServerPlayer, PlayerSkills> skillsFor, CombatEffects combat) {
        this.tree = tree;
        this.skillsFor = skillsFor;
        this.combat = combat;
        instance = this;
    }

    public void setCredit(Credit credit) {
        this.credit = credit == null ? (player, amount, from) -> {
        } : credit;
    }

    // ------------------------------------------------------------------
    // incoming damage (called by the mixin before the hit lands)
    // ------------------------------------------------------------------

    /** Scales a hit: the bastion, the anchor, ward signs, the sanctuary and the saint's own blows. */
    public static float scale(LivingEntity victim, ServerLevel level, DamageSource source, float amount) {
        GuardianEffects self = instance;
        if (self == null || amount <= 0 || level == null) {
            return amount;
        }
        try {
            return self.scaleDamage(victim, level, source, amount);
        } catch (RuntimeException e) {
            return amount;
        }
    }

    private float scaleDamage(LivingEntity victim, ServerLevel level, DamageSource source, float amount) {
        float factor = 1f;
        SiegeManager tuning = SiegeManager.get();
        // Monster strength per type: outside the skill floor, so it also reaches the bulwark.
        float monster = tuning != null && victim instanceof ServerPlayer && source.getEntity() instanceof Enemy
                ? tuning.monsterDamageFactor(source.getEntity()) : 1f;
        if (tuning != null && source.getEntity() instanceof ServerPlayer hitter && victim instanceof Enemy
                && !CombatEffects.inFollowUp()) {   // a follow-up hit already carries the factor
            factor *= tuning.damageFactor(hitter);   // server balancing per class and subclass
        }
        if (tuning != null && victim instanceof ServerPlayer hurt && source.getEntity() instanceof Enemy) {
            factor *= tuning.damageTakenFactor(hurt);
            if ((source.getEntity() instanceof net.minecraft.world.entity.monster.zombie.Zombie
                    || source.getEntity() instanceof net.minecraft.world.entity.monster.spider.Spider)
                    && !BossService.isBoss(source.getEntity())) {   // the grave king and the queen hit everyone alike
                factor *= tuning.zombieSpiderFactor(hurt);   // zombies and spiders hit the melee classes harder
            }
        }
        boolean followUp = CombatEffects.inFollowUp() && victim instanceof Enemy;   // its hit already had these
        if (!followUp) {
            factor *= CrateEffects.factor(victim, source);   // crate gains of the night kit
        }
        factor *= Companions.damageFactor(victim, source);   // the summoner's companions
        if (!followUp && source.getEntity() instanceof ServerPlayer attacker && victim instanceof Enemy
                && SkillTree.has(skillsFor.apply(attacker), "g_saint")) {
            factor *= 0.7f;   // the saint heals, but hits softer
        }
        if (!(victim instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount * factor;
        }
        absorbBefore.put(player.getUUID(), player.getAbsorptionAmount());
        boolean fromMonster = source.getEntity() instanceof Enemy;
        for (Zone zone : zones) {
            if (zone.kind() == ZoneKind.SANCTUARY && zone.level() == level && inside(zone, player)) {
                factor *= 0.4f;
                break;
            }
        }
        if (fromMonster) {
            factor *= BaseDefense.guardFactor(player);   // battlements of the core
            double sign = 0.0;
            for (ServerPlayer guardian : level.players()) {
                if (guardian.isAlive() && guardian.distanceToSqr(player) <= 36.0) {
                    PlayerSkills gs = skillsFor.apply(guardian);
                    if (isGuardian(gs)) {
                        sign = Math.max(sign, tree.totalBonus(SkillEffect.WARD_SIGN, gs));
                    }
                }
            }
            factor *= (float) (1.0 - sign);
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (isGuardian(skills) && shieldWay(player, skills)) {
            double bastion = tree.totalBonus(SkillEffect.BASTION, skills);
            if (bastion > 0 && fromMonster) {
                long now = level.getGameTime();
                int count = now <= tauntUntil.getOrDefault(player.getUUID(), 0L)
                        && SkillTree.has(skills, "g_iron_will") ? 4 : Math.min(4, attackers(level, player));
                factor *= (float) (1.0 - bastion * count);
            }
            double anchor = tree.totalBonus(SkillEffect.ANCHOR, skills);
            if (anchor > 0 && anchored(player)) {
                factor *= (float) (1.0 - anchor);
            }
        }
        // Skills can take away at most maxDamageReduction together (the bulwark was nearly immortal).
        float floor = tuning == null ? 0.30f : (float) (1.0 - tuning.config().maxDamageReduction);
        return amount * monster * Math.max(floor, factor);
    }

    // ------------------------------------------------------------------
    // after a hit
    // ------------------------------------------------------------------

    public void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
        if (!(victim instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        UUID id = player.getUUID();
        Float before = absorbBefore.remove(id);
        PlayerSkills skills = skillsFor.apply(player);
        if (before != null && before > 0.01f && player.getAbsorptionAmount() <= 0.01f) {
            barrierBroken(level, player, skills);
        }
        if (!isGuardian(skills) || !player.isAlive()) {
            return;
        }
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living instanceof Enemy
                ? living : null;
        if (attacker == null) {
            return;
        }
        credit.accept(player, base, attacker);
        boolean active = shieldWay(player, skills);
        double rev = tree.totalBonus(SkillEffect.REVENGE, skills);
        if (active && rev > 0) {
            revenge.merge(id, base * rev, (a, b) -> Math.min(8.0, a + b));
        }
        if (blocked) {
            onBlocked(level, player, skills, attacker, now);
        }
        if (active && source.getDirectEntity() == attacker) {
            double thorns = tree.totalBonus(SkillEffect.THORNS, skills);
            boolean anchored = anchored(player) && tree.totalBonus(SkillEffect.ANCHOR, skills) > 0;
            if (thorns > 0) {
                float hurt = (float) (thorns * (SkillTree.has(skills, "g_bladeshield") ? 2 : 1));
                DamageSource own = level.damageSources().playerAttack(player);
                if (anchored && SkillTree.has(skills, "g_hedgehog")) {
                    for (LivingEntity near : CombatEffects.enemies(level, player.position(), 2.5, 1.5)) {
                        combat.bonusHit(level, near, own, hurt * 3f);
                    }
                    level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY(0.5), player.getZ(),
                            16, 1.0, 0.4, 1.0, 0.2);
                } else {
                    combat.bonusHit(level, attacker, own, hurt);
                }
            }
            if (anchored) {
                CombatEffects.pushAway(attacker, player.position(), 0.6, 0.15);
            }
        }
        if (active && tree.totalBonus(SkillEffect.LAST_STAND, skills) > 0
                && player.getHealth() < player.getMaxHealth() * 0.3f
                && now >= lastStandReadyAt.getOrDefault(id, 0L)) {
            lastStandReadyAt.put(id, now + 600);
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 1));
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY(1.0), player.getZ(),
                    20, 0.4, 0.6, 0.4, 0.2);
            play(level, player, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.7f);
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.last_stand"));
        }
    }

    private void onBlocked(ServerLevel level, ServerPlayer player, PlayerSkills skills, LivingEntity attacker, long now) {
        UUID id = player.getUUID();
        ItemStack shield = player.getUseItem();
        BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
        int delay = blocks == null ? 5 : blocks.blockDelayTicks();
        boolean perfect = player.isUsingItem() && player.getTicksUsingItem() <= delay + PERFECT_WINDOW;
        double wall = tree.totalBonus(SkillEffect.SHIELD_WALL, skills);
        if (wall > 0) {
            addAbsorption(player, (float) (0.5 * wall), 4f);
        }
        if (perfect) {
            // Stunned: the monster stands still for a second.
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 9));
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1));
            blockStart.put(id, now);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, attacker.getX(), attacker.getY(0.7), attacker.getZ(),
                    12, 0.3, 0.3, 0.3, 0.2);
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY(0.8), player.getZ(), 10, 0.4, 0.3, 0.4, 0.3);
            play(level, player, SoundEvents.ANVIL_LAND, 0.35f, 1.9f);
            if (tree.totalBonus(SkillEffect.COUNTERSTRIKE, skills) > 0) {
                counterUntil.put(id, now + 60);
            }
            if (tree.totalBonus(SkillEffect.SHIELD_BASH, skills) > 0) {
                bash(level, player, skills);
            }
            if (SkillTree.has(skills, "g_vengeance") && SkillTree.has(skills, "g_shieldbash")) {
                shockwave(level, player, (float) tree.totalBonus(SkillEffect.VENGEANCE, skills));
            }
        }
        double counter = tree.totalBonus(SkillEffect.COUNTER, skills);
        if (counter > 0 && shieldWay(player, skills)) {
            float hurt = (float) (counter * (perfect ? 2 : 1) * (SkillTree.has(skills, "g_bladeshield") ? 2 : 1));
            combat.bonusHit(level, attacker, level.damageSources().playerAttack(player), hurt);
            CombatEffects.pushAway(attacker, player.position(), 0.9, 0.3);
            play(level, player, SoundEvents.SHIELD_BLOCK.value(), 0.8f, 1.4f);
        }
    }

    /** Shield bash: a perfect block throws back everything in front. */
    private void bash(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        Vec3 flat = CombatEffects.flatLook(player);
        if (flat == null) {
            return;
        }
        float hurt = (float) tree.totalBonus(SkillEffect.SHIELD_BASH, skills);
        DamageSource own = level.damageSources().playerAttack(player);
        for (LivingEntity victim : CombatEffects.enemies(level, player.position(), 4.0, 2.0)) {
            Vec3 to = victim.position().subtract(player.position());
            Vec3 dir = new Vec3(to.x, 0, to.z);
            if (dir.lengthSqr() > 1.0e-4 && dir.normalize().dot(flat) < 0.2) {
                continue;
            }
            combat.bonusHit(level, victim, own, hurt);
            victim.push(flat.x * 1.2, 0.3, flat.z * 1.2);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
        }
        Vec3 p = player.position().add(flat.scale(1.5)).add(0, 1.0, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 3, 0.6, 0.3, 0.6, 0.0);
        play(level, player, SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 1.2f);
    }

    /** Vengeance: a ring of force around the guardian. */
    private void shockwave(ServerLevel level, ServerPlayer player, float hurt) {
        DamageSource own = level.damageSources().playerAttack(player);
        for (LivingEntity victim : CombatEffects.enemies(level, player.position(), 3.5, 1.5)) {
            combat.bonusHit(level, victim, own, hurt);
            CombatEffects.pushAway(victim, player.position(), 0.8, 0.35);
        }
        ring(level, player.position().add(0, 0.2, 0), 3.0, ParticleTypes.CLOUD, 24);
        play(level, player, SoundEvents.MACE_SMASH_GROUND, 0.9f, 1.3f);
    }

    private void barrierBroken(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        UUID from = barrierFrom.remove(player.getUUID());
        if (from != null) {
            ServerPlayer warden = level.getServer().getPlayerList().getPlayer(from);
            PlayerSkills ws = warden == null ? null : skillsFor.apply(warden);
            if (warden != null && SkillTree.has(ws, "g_lifebond")) {
                heal(warden, player, (float) tree.totalBonus(SkillEffect.LIFEBOND, ws));
                level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.8, player.getZ(),
                        3, 0.3, 0.2, 0.3, 0.0);
            }
        }
        if (isGuardian(skills) && SkillTree.has(skills, "g_vengeance") && SkillTree.has(skills, "g_ironhide")) {
            shockwave(level, player, (float) tree.totalBonus(SkillEffect.VENGEANCE, skills));
        }
    }

    /** Extra damage of a guardian's melee hit: the counterstrike after a perfect block, revenge. */
    public double meleeExtra(ServerLevel level, ServerPlayer player, PlayerSkills skills, LivingEntity victim,
                             float taken, long now) {
        if (!isGuardian(skills) || !shieldWay(player, skills)) {
            return 0.0;
        }
        UUID id = player.getUUID();
        double extra = 0.0;
        Long until = counterUntil.remove(id);
        if (until != null && now <= until) {
            extra += taken * tree.totalBonus(SkillEffect.COUNTERSTRIKE, skills);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 14, 0.3, 0.4, 0.3, 0.3);
            play(level, player, SoundEvents.PLAYER_ATTACK_CRIT, 0.9f, 0.8f);
        }
        Double charge = revenge.remove(id);
        if (charge != null && charge >= 0.5) {
            extra += charge;
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, victim.getX(), victim.getY(0.8), victim.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        }
        return extra;
    }

    // ------------------------------------------------------------------
    // every tick
    // ------------------------------------------------------------------

    public void tick(MinecraftServer server) {
        counter++;
        long now = server.overworld().getGameTime();
        tickZones(now);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            PlayerSkills skills = skillsFor.apply(player);
            boolean guardian = isGuardian(skills) && player.isAlive() && !player.isSpectator();
            anchorModifier(player, guardian && tree.totalBonus(SkillEffect.ANCHOR, skills) > 0
                    && shieldWay(player, skills) && anchored(player));
            if (counter % 20 == 0) {
                dropBarrierCap(player);
            }
            if (!guardian || !(player.level() instanceof ServerLevel level)) {
                blockStart.remove(id);
                continue;
            }
            blockLimit(level, player, skills, now);
            if (counter % 20 == 0) {
                aggro(level, player, skills);
            }
            if (counter % 100 == 0) {
                ironhide(level, player, skills);
            }
            if (counter % 160 == 0) {
                ward(level, player, skills);
            }
        }
    }

    /** With the riposte a guardian may block three seconds at a stretch, then the shield rests two. */
    private void blockLimit(ServerLevel level, ServerPlayer player, PlayerSkills skills, long now) {
        UUID id = player.getUUID();
        if (tree.totalBonus(SkillEffect.COUNTER, skills) <= 0 || !player.isUsingItem()
                || !player.getUseItem().has(DataComponents.BLOCKS_ATTACKS)) {
            blockStart.remove(id);
            return;
        }
        long start = blockStart.computeIfAbsent(id, k -> now);
        int limit = BLOCK_LIMIT + (int) Math.round(10 * tree.totalBonus(SkillEffect.SHIELD_WALL, skills));
        if (now - start >= limit) {
            ItemStack stack = player.getUseItem();
            player.stopUsingItem();
            player.getCooldowns().addCooldown(stack, BLOCK_PAUSE);
            blockStart.remove(id);
            play(level, player, SoundEvents.SHIELD_BREAK.value(), 0.5f, 1.5f);
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.shield_tired"));
        }
    }

    /** Monsters close by that go for a teammate turn on the guardian. */
    private void aggro(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        if (player.isCreative()) {
            return;
        }
        double extra = tree.totalBonus(SkillEffect.AGGRO, skills);
        double radius = AGGRO_RADIUS + extra;
        for (LivingEntity e : CombatEffects.enemies(level, player.position(), radius, 3.0)) {
            if (!(e instanceof Mob mob) || !(mob.getTarget() instanceof Player other) || other == player) {
                continue;
            }
            if (other instanceof ServerPlayer mate && isGuardian(skillsFor.apply(mate))) {
                continue;   // another tank already holds it
            }
            if (extra > 0 || random.nextDouble() < 0.6) {
                mob.setTarget(player);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3,
                        mob.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    private void ironhide(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        double per = tree.totalBonus(SkillEffect.IRONHIDE, skills);
        if (per <= 0 || !shieldWay(player, skills)) {
            return;
        }
        int near = Math.min(4, CombatEffects.enemies(level, player.position(), 5.0, 2.0).size());
        if (near > 0) {
            addAbsorption(player, (float) (per * near), 8f);
            level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY(0.6), player.getZ(), 6, 0.4, 0.5, 0.4, 0.0);
        }
    }

    /** The ward: the weakest player close by (the warden too) gets a barrier. */
    private void ward(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        double ward = tree.totalBonus(SkillEffect.WARD, skills);
        if (ward <= 0) {
            return;
        }
        ServerPlayer weakest = null;
        double lowest = Double.MAX_VALUE;
        for (ServerPlayer mate : level.players()) {
            if (!mate.isAlive() || mate.isSpectator() || mate.distanceToSqr(player) > 144.0) {
                continue;
            }
            double max = Math.max(1f, mate.getMaxHealth());
            double share = (mate.getHealth() + mate.getAbsorptionAmount()) / max;
            if (share < lowest) {
                lowest = share;
                weakest = mate;
            }
        }
        if (weakest == null) {
            return;
        }
        float amount = (float) ((2.0 + ward) * (SkillTree.has(skills, "g_patron") ? 1.5 : 1.0));
        if (weakest.getAbsorptionAmount() < amount) {
            float gained = amount - Math.max(0f, weakest.getAbsorptionAmount());
            addAbsorption(weakest, amount, amount);
            barrierFrom.put(weakest.getUUID(), player.getUUID());
            if (weakest != player) {
                credit.accept(player, gained, null);
            }
            ring(level, weakest.position().add(0, 1.0, 0), 0.8, ParticleTypes.END_ROD, 12);
            play(level, weakest, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        }
        if (SkillTree.has(skills, "g_cleanse")) {
            cleanse(weakest);
        }
    }

    private static void cleanse(LivingEntity e) {
        e.removeEffect(MobEffects.POISON);
        e.removeEffect(MobEffects.WITHER);
        e.removeEffect(MobEffects.SLOWNESS);
        e.removeEffect(MobEffects.WEAKNESS);
    }

    private void anchorModifier(ServerPlayer player, boolean on) {
        AttributeInstance kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb == null) {
            return;
        }
        if (on) {
            kb.addOrUpdateTransientModifier(new AttributeModifier(ANCHOR_KB, 1.0, AttributeModifier.Operation.ADD_VALUE));
        } else if (kb.getModifier(ANCHOR_KB) != null) {
            kb.removeModifier(ANCHOR_KB);
        }
    }

    // ------------------------------------------------------------------
    // the shield action (left click while blocking) and the healing hand
    // ------------------------------------------------------------------

    public void onShieldAction(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return;
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (!isGuardian(skills) || !player.isUsingItem() || !player.getUseItem().has(DataComponents.BLOCKS_ATTACKS)) {
            return;
        }
        long now = level.getGameTime();
        boolean toss = tree.totalBonus(SkillEffect.SHIELD_THROW, skills) > 0;
        boolean dome = tree.totalBonus(SkillEffect.DOME, skills) > 0;
        boolean hand = tree.totalBonus(SkillEffect.HEALING_HAND, skills) > 0;
        if (!toss && !dome) {
            if (hand) {
                ServerPlayer mate = lookMate(level, player, 16.0);
                handHeal(level, player, skills, mate == null ? player : mate, now);
            } else {
                CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.shield_action_none"));
            }
            return;
        }
        long ready = actionReadyAt.getOrDefault(player.getUUID(), 0L);
        if (now < ready) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.shield_action_wait", (ready - now + 19) / 20));
            return;
        }
        boolean done = toss ? throwShield(level, player, skills) : raiseDome(level, player, now);
        if (done) {
            int cooldown = player.getAbilities().instabuild ? 20 : toss ? THROW_COOLDOWN : DOME_COOLDOWN;
            actionReadyAt.put(player.getUUID(), now + cooldown);
        }
    }

    /** Right click on a teammate: the healing hand. Returns true when it was used. */
    public boolean onUseEntity(ServerPlayer player, Entity target, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(target instanceof ServerPlayer mate) || mate == player
                || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (!isGuardian(skills) || tree.totalBonus(SkillEffect.HEALING_HAND, skills) <= 0) {
            return false;
        }
        handHeal(level, player, skills, mate, level.getGameTime());
        return true;
    }

    private void handHeal(ServerLevel level, ServerPlayer player, PlayerSkills skills, ServerPlayer target, long now) {
        long ready = handReadyAt.getOrDefault(player.getUUID(), 0L);
        if (now < ready) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.healing_hand_wait", (ready - now + 19) / 20));
            return;
        }
        boolean self = target == player;
        float amount = (float) (2.0 + tree.totalBonus(SkillEffect.HEALING_HAND, skills)) * (self ? 0.5f : 1f);
        heal(player, target, amount);
        if (SkillTree.has(skills, "g_miracle")) {
            float barrier = (float) tree.totalBonus(SkillEffect.MIRACLE, skills);
            addAbsorption(target, barrier, barrier);
            barrierFrom.put(target.getUUID(), player.getUUID());
        }
        if (!self) {
            trail(level, player.getEyePosition().subtract(0, 0.3, 0), target.position().add(0, 1.0, 0),
                    ParticleTypes.HAPPY_VILLAGER, 10);
        }
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 1.9, target.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        play(level, target, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
        handReadyAt.put(player.getUUID(), now + (player.getAbilities().instabuild ? 10 : HAND_COOLDOWN));
    }

    /** The shield flies, bounces from monster to monster and comes back. */
    private boolean throwShield(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        LivingEntity first = CombatEffects.lookTarget(level, player, 14.0);
        if (first == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.ult_no_target"));
            return false;
        }
        boolean boomerang = SkillTree.has(skills, "g_boomerang");
        int count = boomerang ? 4 : 3;
        List<LivingEntity> chain = new ArrayList<>();
        chain.add(first);
        LivingEntity last = first;
        while (chain.size() < count) {
            LivingEntity next = null;
            double best = 36.0;
            for (LivingEntity e : CombatEffects.enemies(level, last.position(), 6.0, 2.0)) {
                double d = e.distanceToSqr(last);
                if (!chain.contains(e) && d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) {
                break;
            }
            chain.add(next);
            last = next;
        }
        float hurt = (float) (tree.totalBonus(SkillEffect.SHIELD_THROW, skills)
                * (SkillTree.has(skills, "g_bladeshield") ? 2 : 1));
        ItemStack stack = player.getUseItem();
        Item item = stack.getItem();
        player.stopUsingItem();
        player.getCooldowns().addCooldown(stack, 3 * chain.size() + 6);
        play(level, player, SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.8f);
        DamageSource own = level.damageSources().playerAttack(player);
        ParticleOptions look = new ItemParticleOption(ParticleTypes.ITEM, item);
        Vec3 prev = player.getEyePosition().subtract(0, 0.3, 0);
        for (int i = 0; i < chain.size(); i++) {
            LivingEntity target = chain.get(i);
            Vec3 from = prev;
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
            combat.schedule(2 + 3 * i, () -> {
                trail(level, from, to, look, 8);
                if (target.isAlive()) {
                    combat.bonusHit(level, target, own, hurt);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2));
                    if (boomerang && player.isAlive()) {
                        Vec3 pull = player.position().subtract(target.position());
                        Vec3 flat = new Vec3(pull.x, 0, pull.z);
                        if (flat.lengthSqr() > 1.0e-4) {
                            flat = flat.normalize().scale(0.9);
                            target.push(flat.x, 0.25, flat.z);
                        }
                        if (target instanceof Mob mob) {
                            mob.setTarget(player);
                        }
                    }
                    play(level, target, SoundEvents.SHIELD_BLOCK.value(), 0.8f, 1.6f);
                }
            });
            prev = to;
        }
        Vec3 back = prev;
        combat.schedule(2 + 3 * chain.size(), () -> {
            trail(level, back, player.getEyePosition().subtract(0, 0.3, 0), look, 8);
            play(level, player, SoundEvents.TRIDENT_RETURN, 0.9f, 1.2f);
        });
        return true;
    }

    private boolean raiseDome(ServerLevel level, ServerPlayer player, long now) {
        zones.add(new Zone(ZoneKind.DOME, player.getUUID(), level, player.position(), 4.0, now + 100));
        play(level, player, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.4f);
        play(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------
    // healing
    // ------------------------------------------------------------------

    /**
     * Heals a player as this healer: more with prayer and the saint, double on the
     * badly hurt with triage; overflow turns into absorption. Healing others earns
     * a guardian skill points. Returns the health actually restored.
     */
    public float heal(ServerPlayer healer, ServerPlayer target, float base) {
        if (target == null || !target.isAlive() || base <= 0) {
            return 0f;
        }
        PlayerSkills skills = healer == null ? null : skillsFor.apply(healer);
        boolean guardian = isGuardian(skills);
        double power = 1.0;
        if (guardian) {
            power += tree.totalBonus(SkillEffect.HEAL_POWER, skills);
            if (SkillTree.has(skills, "g_saint")) {
                power += tree.totalBonus(SkillEffect.SAINT, skills);
            }
            if (tree.totalBonus(SkillEffect.TRIAGE, skills) > 0 && target.getHealth() < target.getMaxHealth() * 0.4f) {
                power *= 2.0;
            }
        }
        float amount = (float) (base * power);
        float real = Math.min(amount, Math.max(0f, target.getMaxHealth() - target.getHealth()));
        if (real > 0) {
            target.heal(real);
        }
        if (!guardian) {
            return real;
        }
        double overflow = tree.totalBonus(SkillEffect.OVERFLOW, skills);
        if (overflow > 0 && amount > real + 0.01f) {
            addAbsorption(target, amount - real, (float) overflow);
        }
        if (real > 0 && tree.totalBonus(SkillEffect.GRACE, skills) > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, true));
        }
        if (target != healer && real > 0) {
            double selfless = tree.totalBonus(SkillEffect.SELFLESS, skills);
            if (selfless > 0) {
                healer.heal((float) (real * selfless));
            }
            credit.accept(healer, real, null);
        }
        return real;
    }

    /** Extra radius of a guardian's heal aura. */
    public double auraRange(PlayerSkills skills) {
        return isGuardian(skills) ? tree.totalBonus(SkillEffect.AURA_RANGE, skills) : 0.0;
    }

    // ------------------------------------------------------------------
    // ultimates
    // ------------------------------------------------------------------

    boolean taunt(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        long now = level.getGameTime();
        double r = 7.0 + 3.0 * rank;   // 10 / 13 / 16
        for (LivingEntity victim : CombatEffects.enemies(level, player.position(), r, 4.0)) {
            if (victim instanceof Mob mob) {
                mob.setTarget(player);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, victim.getX(), victim.getY() + victim.getBbHeight() + 0.3,
                        victim.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
            }
        }
        int length = 60 + 20 * rank;
        tauntUntil.put(player.getUUID(), now + length);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, length, 1));
        boolean ironWill = SkillTree.has(skills, "g_iron_will");
        boolean shared = SkillTree.has(skills, "g_shared_burden");
        // The guardian holds the line: a shield of extra hearts for the team around.
        for (ServerPlayer mate : level.players()) {
            if (!mate.isAlive() || mate.distanceToSqr(player) > r * r) {
                continue;
            }
            int amp = mate == player && ironWill ? rank * 2 + 1 : rank;
            mate.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, amp));
            if (shared && mate != player) {
                mate.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 0));
                addAbsorption(mate, 4f, 4f);
            }
        }
        play(level, player, SoundEvents.RAVAGER_ROAR, 1.0f, 1.2f);
        return true;
    }

    boolean shieldCharge(ServerLevel level, ServerPlayer player, int rank) {
        Vec3 flat = CombatEffects.flatLook(player);
        if (flat == null) {
            return false;
        }
        PlayerSkills skills = skillsFor.apply(player);
        boolean rampart = SkillTree.has(skills, "g_rampart");
        boolean debris = SkillTree.has(skills, "g_debris");
        float damage = 3f + 3f * rank;   // 6 / 9 / 12
        Vec3 start = player.position();
        DamageSource own = level.damageSources().playerAttack(player);
        for (LivingEntity victim : CombatEffects.enemies(level, start, 6.0, 2.0)) {
            Vec3 to = victim.position().subtract(start);
            Vec3 dir = new Vec3(to.x, 0, to.z);
            if (dir.lengthSqr() < 1.0e-4 || dir.normalize().dot(flat) < 0.5) {
                continue;   // only a cone of about 60 degrees to each side
            }
            combat.bonusHit(level, victim, own, damage);
            double force = rampart ? 2.6 : 1.4;
            victim.push(flat.x * force, rampart ? 0.6 : 0.35, flat.z * force);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, rampart ? 9 : 2));
        }
        // The guardian goes with the charge.
        player.push(flat.x * 1.2, 0.1, flat.z * 1.2);
        player.hurtMarked = true;
        for (int i = 1; i <= 6; i++) {
            Vec3 p = start.add(flat.scale(i)).add(0, 1.0, 0);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0.02);
        }
        if (debris) {
            for (int i = 1; i <= 3; i++) {
                Vec3 at = start.add(flat.scale(2.0 * i));
                combat.schedule(3 * i, () -> {
                    for (LivingEntity victim : CombatEffects.enemies(level, at, 2.0, 1.5)) {
                        combat.bonusHit(level, victim, own, 4f);
                        victim.push(0, 0.4, 0);
                    }
                    level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4f, 1.5f);
                });
            }
        }
        play(level, player, SoundEvents.SHIELD_BLOCK.value(), 1.0f, 0.6f);
        play(level, player, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.8f);
        return true;
    }

    boolean massHeal(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        boolean rebirth = SkillTree.has(skills, "g_rebirth");
        for (ServerPlayer mate : level.players()) {
            if (mate.isAlive() && mate.distanceToSqr(player) <= 144.0) {
                heal(player, mate, 6f + 4f * rank);
                mate.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                if (rebirth) {
                    removeHarmful(mate);
                }
                level.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 1.8, mate.getZ(),
                        6, 0.4, 0.3, 0.4, 0.0);
            }
        }
        if (rebirth) {
            combat.readyAngel(player.getUUID());
        }
        if (SkillTree.has(skills, "g_lifespring")) {
            zones.add(new Zone(ZoneKind.LIFESPRING, player.getUUID(), level, player.position(), 6.0,
                    level.getGameTime() + 120));
        }
        play(level, player, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.4f);
        return true;
    }

    boolean sanctuary(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        long length = 100 + 20L * rank + (SkillTree.has(skills, "g_eternal_watch") ? 60 : 0);
        zones.add(new Zone(ZoneKind.SANCTUARY, player.getUUID(), level, player.position(), 8.0,
                level.getGameTime() + length));
        ring(level, player.position().add(0, 0.2, 0), 8.0, ParticleTypes.END_ROD, 60);
        play(level, player, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.0f);
        play(level, player, SoundEvents.BELL_RESONATE, 1.0f, 1.2f);
        return true;
    }

    private static void removeHarmful(LivingEntity e) {
        List<Holder<MobEffect>> bad = new ArrayList<>();
        for (MobEffectInstance effect : e.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                bad.add(effect.getEffect());
            }
        }
        for (Holder<MobEffect> effect : bad) {
            e.removeEffect(effect);
        }
    }

    // ------------------------------------------------------------------
    // zones: the dome, the sanctuary, the spring of life
    // ------------------------------------------------------------------

    private void tickZones(long now) {
        if (zones.isEmpty()) {
            return;
        }
        List<Zone> ended = new ArrayList<>();
        zones.removeIf(zone -> {
            if (now >= zone.until()) {
                ended.add(zone);
                return true;
            }
            return false;
        });
        for (Zone zone : ended) {
            zoneEnded(zone);
        }
        for (Zone zone : new ArrayList<>(zones)) {
            ServerLevel level = zone.level();
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(zone.owner());
            PlayerSkills skills = owner == null ? null : skillsFor.apply(owner);
            if (zone.kind() != ZoneKind.LIFESPRING) {
                holdOut(level, zone);
            }
            boolean second = (zone.until() - now) % 20 == 0;
            if (second && owner != null) {
                boolean heals = zone.kind() == ZoneKind.LIFESPRING
                        || (zone.kind() == ZoneKind.DOME && SkillTree.has(skills, "g_wellspring"))
                        || (zone.kind() == ZoneKind.SANCTUARY && SkillTree.has(skills, "g_lightcircle"));
                if (heals) {
                    for (ServerPlayer mate : level.players()) {
                        if (mate.isAlive() && inside(zone, mate) && heal(owner, mate, 1.5f) > 0) {
                            level.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 1.9, mate.getZ(),
                                    1, 0.2, 0.1, 0.2, 0.0);
                        }
                    }
                }
                if (zone.kind() == ZoneKind.SANCTUARY && SkillTree.has(skills, "g_lightcircle")) {
                    DamageSource own = level.damageSources().playerAttack(owner);
                    for (LivingEntity e : CombatEffects.enemies(level, zone.center(), zone.radius() + 1.5, 3.0)) {
                        e.igniteForSeconds(3f);
                        if (CombatEffects.isUndead(e)) {
                            combat.bonusHit(level, e, own, 3f);
                        }
                    }
                }
            }
            if (now % 4 == 0) {
                drawZone(level, zone, now);
            }
        }
    }

    /** Monsters are pushed out, monster shots inside fall apart. */
    private void holdOut(ServerLevel level, Zone zone) {
        Vec3 c = zone.center();
        double r = zone.radius();
        for (LivingEntity e : CombatEffects.enemies(level, c, r + 0.6, 3.0)) {
            Vec3 away = e.position().subtract(c);
            Vec3 flat = new Vec3(away.x, 0, away.z);
            if (flat.lengthSqr() < 1.0e-3) {
                flat = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            }
            double depth = r + 0.6 - flat.length();
            if (depth > 0) {
                Vec3 push = flat.normalize().scale(0.35 + 0.12 * depth);
                e.setDeltaMovement(push.x, Math.max(0.0, e.getDeltaMovement().y), push.z);
                e.hurtMarked = true;
            }
        }
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r, 4.0, r))) {
            if (shot.getOwner() instanceof Player) {
                continue;
            }
            Vec3 at = shot.position().subtract(c);
            if (at.x * at.x + at.z * at.z <= r * r) {
                level.sendParticles(ParticleTypes.POOF, shot.getX(), shot.getY(), shot.getZ(), 3, 0.1, 0.1, 0.1, 0.0);
                shot.discard();
            }
        }
    }

    private void zoneEnded(Zone zone) {
        ServerLevel level = zone.level();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(zone.owner());
        if (zone.kind() == ZoneKind.SANCTUARY && owner != null && SkillTree.has(skillsFor.apply(owner), "g_eternal_watch")) {
            for (ServerPlayer mate : level.players()) {
                if (mate.isAlive() && inside(zone, mate)) {
                    addAbsorption(mate, 4f, 4f);
                }
            }
        }
    }

    private void drawZone(ServerLevel level, Zone zone, long now) {
        Vec3 c = zone.center();
        switch (zone.kind()) {
            case DOME -> {
                ring(level, c.add(0, 0.15, 0), zone.radius(), ParticleTypes.END_ROD, 20);
                for (int i = 0; i < 30; i++) {
                    double yaw = random.nextDouble() * Math.PI * 2;
                    double pitch = random.nextDouble() * Math.PI * 0.5;
                    double x = Math.cos(yaw) * Math.cos(pitch) * zone.radius();
                    double z = Math.sin(yaw) * Math.cos(pitch) * zone.radius();
                    double y = Math.sin(pitch) * zone.radius();
                    level.sendParticles(ParticleTypes.END_ROD, c.x + x, c.y + y, c.z + z, 1, 0, 0, 0, 0.0);
                }
            }
            case SANCTUARY -> {
                double turn = now * 0.05;
                for (int i = 0; i < 24; i++) {
                    double a = turn + i * Math.PI * 2 / 24;
                    level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * zone.radius(), c.y + 0.2,
                            c.z + Math.sin(a) * zone.radius(), 1, 0, 0.05, 0, 0.0);
                }
                level.sendParticles(ParticleTypes.GLOW, c.x, c.y + 1.0, c.z, 6, zone.radius() * 0.5, 0.5, zone.radius() * 0.5, 0.0);
            }
            case LIFESPRING -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 0.4, c.z, 8,
                    zone.radius() * 0.5, 0.2, zone.radius() * 0.5, 0.0);
        }
    }

    private static boolean inside(Zone zone, Entity e) {
        double dx = e.getX() - zone.center().x;
        double dz = e.getZ() - zone.center().z;
        double dy = e.getY() - zone.center().y;
        return dx * dx + dz * dz <= zone.radius() * zone.radius() && dy > -2.0 && dy < 4.0;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    public static boolean isGuardian(PlayerSkills skills) {
        return skills != null && skills.clazz() == SkillClass.GUARDIAN;
    }

    /** The boni of the way of the shield need a shield at hand; the other way has no such rule. */
    private boolean shieldWay(ServerPlayer player, PlayerSkills skills) {
        if (!SkillTree.WAY_SHIELD.equals(skills.pathTaken(tree))) {
            return true;
        }
        return player.getMainHandItem().has(DataComponents.BLOCKS_ATTACKS)
                || player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
    }

    private static boolean anchored(ServerPlayer player) {
        return player.isShiftKeyDown() && player.isBlocking();
    }

    /** Monsters close by that are after this player. */
    private static int attackers(ServerLevel level, ServerPlayer player) {
        int n = 0;
        for (LivingEntity e : CombatEffects.enemies(level, player.position(), 8.0, 3.0)) {
            if (e instanceof Mob mob && mob.getTarget() == player) {
                n++;
            }
        }
        return n;
    }

    /** The teammate the player is looking at, within reach. */
    private static ServerPlayer lookMate(ServerLevel level, ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        ServerPlayer best = null;
        double bestDot = 0.95;
        for (ServerPlayer mate : level.players()) {
            if (mate == player || !mate.isAlive() || mate.isSpectator()) {
                continue;
            }
            Vec3 to = mate.position().add(0, 1.0, 0).subtract(eye);
            double dist = to.length();
            if (dist > reach || dist < 1.0e-4) {
                continue;
            }
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = mate;
            }
        }
        return best;
    }

    /**
     * Adds absorption up to a cap (never takes any away). The attribute that caps
     * absorption is raised while the player carries a barrier.
     */
    static void addAbsorption(LivingEntity e, float amount, float cap) {
        AttributeInstance max = e.getAttribute(Attributes.MAX_ABSORPTION);
        if (max == null || amount <= 0) {
            return;
        }
        if (max.getModifier(BARRIER_CAP) == null) {
            max.addOrUpdateTransientModifier(new AttributeModifier(BARRIER_CAP, 40.0, AttributeModifier.Operation.ADD_VALUE));
        }
        float now = Math.max(0f, e.getAbsorptionAmount());
        float target = Math.max(now, Math.min(now + amount, cap));
        e.setAbsorptionAmount(target);
    }

    private static void dropBarrierCap(ServerPlayer player) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (max != null && max.getModifier(BARRIER_CAP) != null && player.getAbsorptionAmount() <= 0.01f) {
            max.removeModifier(BARRIER_CAP);
        }
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, int steps) {
        int n = Math.max(steps, (int) (from.distanceTo(to) * 3));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.lerp(to, i / (double) n);
            level.sendParticles(particle, p.x, p.y, p.z, 2, 0.04, 0.04, 0.04, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private static void ring(ServerLevel level, Vec3 c, double r, ParticleOptions particle, int count) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            level.sendParticles(particle, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0.02, 0, 0.0);
        }
    }

    private static void play(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public void forget(UUID player) {
        blockStart.remove(player);
        actionReadyAt.remove(player);
        handReadyAt.remove(player);
        counterUntil.remove(player);
        revenge.remove(player);
        lastStandReadyAt.remove(player);
        tauntUntil.remove(player);
        barrierFrom.remove(player);
        absorbBefore.remove(player);
    }
}
