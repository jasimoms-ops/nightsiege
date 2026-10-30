package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
    // Every other number of a guardian node sits in SkillValuesGuardian (shown in the skill texts).
    private static final Identifier ANCHOR_KB = ModContent.id("guardian_anchor");
    private static final Identifier IRONWARD_ARMOR = ModContent.id("guardian_ironward");
    private static final Identifier IRONWARD_TOUGHNESS = ModContent.id("guardian_ironward_toughness");
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
    /** The healing ability each guardian picked on the Fähigkeit-3 wheel (27 October 2026). */
    private final Map<UUID, String> healChoice = new HashMap<>();
    /** The warden's own ward was given by hand: the timed one waits until then. */
    private final Map<UUID, Long> wardSkipUntil = new HashMap<>();
    private final Map<UUID, Long> counterUntil = new HashMap<>();
    private final Map<UUID, Double> revenge = new HashMap<>();
    private final Map<UUID, Long> lastStandReadyAt = new HashMap<>();
    private final Map<UUID, Long> tauntUntil = new HashMap<>();
    /**
     * A monster's hit on a player as the monster dealt it (its strength included), before every cut on
     * the defender's side: subclass damage taken, bastion, anchor, Wächter-Schutz, ward sign, sanctuary.
     * The logger's "abgefangen" (30.10.: it was counted after those, so the bulwark's own cuts made it
     * look as if it held off less than the shield fighter).
     */
    private static final Map<UUID, Float> RAW_HIT = new HashMap<>();
    /** Who gave a player the barrier they carry: the life bond heals when it breaks. */
    private final Map<UUID, UUID> barrierFrom = new HashMap<>();
    /** A player's absorption just before a hit, to see a barrier break. */
    private final Map<UUID, Float> absorbBefore = new HashMap<>();
    /** Guardians who carry absorption of their own iron hide: vengeance fires when it breaks. */
    private final Set<UUID> ironhideUp = new HashSet<>();
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
            float out = self.scaleDamage(victim, level, source, amount);
            // An engineer in a tower: the tower takes the hit (Simon, 27 October 2026).
            if (out > 0 && victim instanceof ServerPlayer seated
                    && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.IS_FALL)) {
                out = TrapService.seatAbsorb(seated, out);
            }
            Nemesis.dealt(victim, source, out);   // the blood drinker heals from it
            return out;
        } catch (RuntimeException e) {
            return amount;
        }
    }

    /**
     * The monster's hit that just landed on this player as the monster dealt it, before the defender's
     * cuts ({@link #RAW_HIT}); {@code fallback} when none was noted. Taken once, by the logger.
     */
    public static float rawHit(UUID player, float fallback) {
        Float raw = RAW_HIT.remove(player);
        return raw == null ? fallback : raw;
    }

    /**
     * The heavy archer against a dungeon champion (Simon 30.10.: "wuchtschütze maximal 8% damage des
     * lebens der wächter"): one hit takes at most this share of the champion's max health.
     */
    private float heavyCap(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0f || !ChampionStrikes.isChampion(victim) || !(source.getEntity() instanceof ServerPlayer hitter)) {
            return amount;
        }
        SiegeManager m = SiegeManager.get();
        double share = m == null || m.config() == null ? 0.08 : m.config().heavyChampionCap;
        if (share <= 0 || !SkillTree.SUB_HEAVY.equals(tree.subclassOf(skillsFor.apply(hitter)))) {
            return amount;
        }
        return (float) Math.min(amount, victim.getMaxHealth() * share);
    }

    private float scaleDamage(LivingEntity victim, ServerLevel level, DamageSource source, float amount) {
        float factor = 1f;
        // Server balancing per class and subclass (and zombies/spiders against the melee classes) is
        // no skill: it sits outside the skill floor below, like the monster strength.
        float outside = 1f;
        SiegeManager tuning = SiegeManager.get();
        // Monster strength per type: outside the skill floor, so it also reaches the bulwark.
        // The share of the max health (bosses, machines) stays the share it says (28 October 2026).
        boolean share = PercentDamage.is(source);
        float monster = tuning != null && victim instanceof ServerPlayer && source.getEntity() instanceof Enemy && !share
                ? tuning.monsterDamageFactor(source.getEntity()) : 1f;
        if (victim instanceof ServerPlayer hit) {
            if (source.getEntity() instanceof Enemy) {
                RAW_HIT.put(hit.getUUID(), amount * monster);   // for the logger, before the defender's cuts
            } else {
                RAW_HIT.remove(hit.getUUID());
            }
        }
        if (tuning != null && source.getEntity() instanceof ServerPlayer hitter && victim instanceof Enemy
                && !CombatEffects.inFollowUp()) {   // a follow-up hit already carries the factor
            outside *= tuning.damageFactor(hitter);   // server balancing per class and subclass
        }
        if (tuning != null && victim instanceof ServerPlayer hurt && source.getEntity() instanceof Enemy) {
            outside *= tuning.damageTakenFactor(hurt);
            outside *= TankGuard.damageFactor(hurt);   // a bulwark or shield fighter near (Simon 30.10.)
            Companions.ownerHurt(hurt, source.getEntity());   // the golem draws the golemsmith's attackers
            if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                if (TrapService.shieldBlocks(hurt)) {
                    return 0f;   // the tower shield (crate gain) takes the shot whole
                }
                outside *= TrapService.seatShotFactor(hurt);   // a gunner in his tower (27 October 2026)
            }
            if ((source.getEntity() instanceof net.minecraft.world.entity.monster.zombie.Zombie
                    || source.getEntity() instanceof net.minecraft.world.entity.monster.spider.Spider)
                    && !BossService.isBoss(source.getEntity()) && !share) {   // the grave king and the queen hit everyone alike
                outside *= tuning.zombieSpiderFactor(hurt);   // zombies and spiders hit the melee classes harder
            }
        }
        boolean followUp = CombatEffects.inFollowUp() && victim instanceof Enemy;   // its hit already had these
        if (!followUp) {
            factor *= CrateEffects.factor(victim, source);   // crate gains of the night kit
        }
        factor *= Companions.damageFactor(victim, source);   // the summoner's companions
        factor *= Foes.hitFactor(victim, source);   // arrows glance off the iron knight (update 1)
        factor *= Nemesis.hitFactor(victim, source);   // a nemesis's armour, fireproofing and grudge (update 2)
        if (!followUp && source.getEntity() instanceof ServerPlayer attacker && victim instanceof Enemy
                && SkillTree.has(skillsFor.apply(attacker), "g_saint")) {
            factor *= (float) SkillValues.get("g_saint", "dmg", 0.7);   // the saint heals, but hits softer
        }
        if (!(victim instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return heavyCap(victim, source, amount * outside * factor);
        }
        absorbBefore.put(player.getUUID(), player.getAbsorptionAmount());
        boolean fromMonster = source.getEntity() instanceof Enemy;
        for (Zone zone : zones) {
            if (zone.kind() == ZoneKind.SANCTUARY && zone.level() == level && inside(zone, player)) {
                factor *= (float) SkillValues.get("g_sanctuary", "factor", 0.4);
                break;
            }
        }
        if (fromMonster) {
            factor *= BaseDefense.guardFactor(player);   // battlements of the core
            double sign = 0.0;
            for (ServerPlayer guardian : level.players()) {
                double signRadius = SkillValues.get("g_wardsign", "radius", 6.0);
                if (guardian.isAlive() && guardian.distanceToSqr(player) <= signRadius * signRadius) {
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
                String node = bastionNode(skills);
                int most = (int) Math.round(SkillValues.get(node, "count", 4.0));
                int count = now <= tauntUntil.getOrDefault(player.getUUID(), 0L) && SkillTree.has(skills, "g_iron_will")
                        ? (int) Math.round(SkillValues.get("g_iron_will", "count", 4.0))
                        : Math.min(most, attackers(level, player, SkillValues.get(node, "radius", 8.0)));
                factor *= (float) (1.0 - bastion * count);
            }
            double anchor = tree.totalBonus(SkillEffect.ANCHOR, skills);
            if (anchor > 0 && anchored(player)) {
                factor *= (float) (1.0 - anchor);
            }
        }
        // Skills can take away at most maxDamageReduction together (the bulwark was nearly immortal).
        float floor = tuning == null ? 0.30f : (float) (1.0 - tuning.config().maxDamageReduction);
        return amount * monster * outside * Math.max(floor, factor);
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
            double cap = SkillValues.get("g_revenge", "cap", 8.0);
            revenge.merge(id, base * rev, (a, b) -> Math.min(cap, a + b));
        }
        if (blocked) {
            onBlocked(level, player, skills, attacker, now);
        }
        if (active && source.getDirectEntity() == attacker) {
            double thorns = tree.totalBonus(SkillEffect.THORNS, skills);
            boolean anchored = anchored(player) && tree.totalBonus(SkillEffect.ANCHOR, skills) > 0;
            boolean hedgehog = anchored && tree.totalBonus(SkillEffect.HEDGEHOG, skills) > 0;
            if (hedgehog && thorns <= 0) {
                thorns = SkillValues.get("g_hedgehog", "dmg", 4.0);   // the hedgehog brings its own spines
            }
            if (thorns > 0) {
                float hurt = (float) (thorns * bladeshield(skills));
                DamageSource own = level.damageSources().playerAttack(player);
                if (hedgehog) {
                    float mult = (float) SkillValues.get("g_hedgehog", "mult", 3.0);
                    double reach = SkillValues.get("g_hedgehog", "radius", 2.5);
                    for (LivingEntity near : CombatEffects.enemies(level, player.position(), reach, 1.5)) {
                        combat.bonusHit(level, near, own, (float) (hurt * mult * group(skills)));
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
                && player.getHealth() < player.getMaxHealth() * (float) SkillValues.get("g_laststand", "below", 0.3)
                && now >= lastStandReadyAt.getOrDefault(id, 0L)) {
            lastStandReadyAt.put(id, now + Math.round(SkillValues.get("g_laststand", "cd", 600.0)));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,
                    (int) Math.round(SkillValues.get("g_laststand", "time", 80.0)), 1));
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
            addAbsorption(player, (float) (SkillValues.get("g_shieldwall", "absorb", 0.5) * wall),
                    (float) SkillValues.get("g_shieldwall", "cap", 4.0));
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
                counterUntil.put(id, now + Math.round(SkillValues.get("g_counterstrike", "window", 60.0)));
            }
            if (tree.totalBonus(SkillEffect.SHIELD_BASH, skills) > 0) {
                bash(level, player, skills);
            }
            if (SkillTree.has(skills, "g_vengeance") && SkillTree.has(skills, "g_shieldbash")
                    && shieldWay(player, skills)) {
                shockwave(level, player, (float) tree.totalBonus(SkillEffect.VENGEANCE, skills));
            }
        }
        double counter = tree.totalBonus(SkillEffect.COUNTER, skills);
        if (counter > 0 && shieldWay(player, skills)) {
            String node = skills.levelOf("riposte") > 0 ? "riposte" : "b_cross_riposte";
            float hurt = (float) (counter * (perfect ? SkillValues.get(node, "perfect", 2.0) : 1.0) * bladeshield(skills));
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
        for (LivingEntity victim : CombatEffects.enemies(level, player.position(), SkillValues.get("g_shieldbash", "range", 4.0), 2.0)) {
            Vec3 to = victim.position().subtract(player.position());
            Vec3 dir = new Vec3(to.x, 0, to.z);
            if (dir.lengthSqr() > 1.0e-4 && dir.normalize().dot(flat) < 0.2) {
                continue;
            }
            combat.bonusHit(level, victim, own, hurt);
            victim.push(flat.x * 1.2, 0.3, flat.z * 1.2);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) Math.round(SkillValues.get("g_shieldbash", "slow", 40.0)), 2));
        }
        Vec3 p = player.position().add(flat.scale(1.5)).add(0, 1.0, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 3, 0.6, 0.3, 0.6, 0.0);
        play(level, player, SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 1.2f);
    }

    /** Vengeance: a ring of force around the guardian. */
    private void shockwave(ServerLevel level, ServerPlayer player, float hurt) {
        hurt = (float) (hurt * group(skillsFor.apply(player)));
        DamageSource own = level.damageSources().playerAttack(player);
        for (LivingEntity victim : CombatEffects.enemies(level, player.position(), SkillValues.get("g_vengeance", "radius", 3.5), 1.5)) {
            combat.bonusHit(level, victim, own, hurt);
            CombatEffects.pushAway(victim, player.position(), 0.8, 0.35);
        }
        ring(level, player.position().add(0, 0.2, 0), 3.0, ParticleTypes.CLOUD, 24);
        play(level, player, SoundEvents.MACE_SMASH_GROUND, 0.9f, 1.3f);
    }

    private void barrierBroken(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        boolean ownIronhide = ironhideUp.remove(player.getUUID());
        UUID from = barrierFrom.remove(player.getUUID());
        if (from != null) {
            ServerPlayer warden = level.getServer().getPlayerList().getPlayer(from);
            PlayerSkills ws = warden == null ? null : skillsFor.apply(warden);
            if (warden != null && SkillTree.has(ws, "g_lifebond")) {
                heal(warden, player, (float) (tree.totalBonus(SkillEffect.LIFEBOND, ws) * patron(ws)));
                level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.8, player.getZ(),
                        3, 0.3, 0.2, 0.3, 0.0);
            }
        }
        // Vengeance only when the guardian's own iron hide breaks, not any absorption (shield wall, a ward, taunt).
        if (ownIronhide && isGuardian(skills) && SkillTree.has(skills, "g_vengeance") && SkillTree.has(skills, "g_ironhide")
                && shieldWay(player, skills)) {
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
            ironwardModifier(player, guardian ? skills : null);
            if (counter % 20 == 0) {
                dropBarrierCap(player);
                if (player.getAbsorptionAmount() <= 0.01f) {
                    ironhideUp.remove(id);
                }
            }
            shieldEnergy(player, skills, counter);   // every player with a shield in the night, not only guardians
            if (!guardian || !(player.level() instanceof ServerLevel level)) {
                blockStart.remove(id);
                ironhideUp.remove(id);
                continue;
            }
            if (counter % 20 == 0) {
                aggro(level, player, skills);
            }
            if (counter % every("g_ironhide", "interval", 100.0) == 0) {
                ironhide(level, player, skills);
            }
            if (counter % every(wardNode(skills), "interval", 160.0) == 0
                    && level.getGameTime() >= wardSkipUntil.getOrDefault(id, 0L)) {
                ward(level, player, skills);
            }
            if (counter % every(auraNode(skills), "interval", 60.0) == 0) {
                aura(level, player, skills);
            }
        }
    }

    /** Shield energy of each player in ticks of blocking left (Simon 26.10.). */
    private final Map<UUID, Double> shieldEnergy = new HashMap<>();
    /** Players whose shield ran dry: locked until one second is charged again (the firm grip lifts cooldowns). */
    private final Set<UUID> shieldLocked = new HashSet<>();

    /** A new night: every shield is full again. */
    public void newNight() {
        shieldEnergy.clear();
        shieldLocked.clear();
    }

    /** The shield energy's share (1 = full) for the kit HUD's gauge; -1 outside the night. */
    public double shieldShare(ServerPlayer player, PlayerSkills skills) {
        Double left = shieldEnergy.get(player.getUUID());
        return left == null ? -1.0 : Math.max(0.0, Math.min(1.0, left / shieldCapacity(skills)));
    }

    /** Ticks of blocking a full shield holds: config shieldEnergySeconds, more with the shield wall. */
    private double shieldCapacity(PlayerSkills skills) {
        SiegeManager m = SiegeManager.get();
        double seconds = m == null ? 6.0 : m.config().shieldEnergySeconds;
        double wall = skills == null ? 0.0 : tree.totalBonus(SkillEffect.SHIELD_WALL, skills);
        return seconds * 20.0 + SkillValues.get("g_shieldwall", "block", 10.0) * wall;
    }

    /**
     * Shield energy (Simon 26.10., replaces the riposte's block limit): in the night every shield
     * holds at most shieldEnergySeconds of blocking at a stretch. Blocking drains one second per
     * second, lowering the shield charges shieldRechargePerSecond per second. When it runs dry the
     * block ends and the shield is locked until one second is charged again. The bar on the kit
     * shield shows the energy while it is not full.
     */
    private void shieldEnergy(ServerPlayer player, PlayerSkills skills, int tick) {
        SiegeManager m = SiegeManager.get();
        UUID id = player.getUUID();
        if (m == null || !m.isDefending(player) || !player.isAlive()) {
            shieldLocked.remove(id);
            if (shieldEnergy.remove(id) != null) {
                showShieldEnergy(player, 1.0);   // the bar goes away with the night
            }
            return;
        }
        double max = shieldCapacity(skills);
        double recharge = Math.max(0.05, m.config().shieldRechargePerSecond);
        double left = Math.min(max, shieldEnergy.getOrDefault(id, max));
        boolean blocking = player.isUsingItem() && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
        if (shieldLocked.contains(id)) {
            if (left >= 20.0) {
                shieldLocked.remove(id);
            } else if (blocking) {
                player.stopUsingItem();
                blocking = false;
            }
        }
        if (blocking) {
            left -= 1.0;
            if (left <= 0.0) {
                left = 0.0;
                shieldLocked.add(id);
                ItemStack stack = player.getUseItem();
                player.stopUsingItem();
                // Locked until one second of blocking is charged again.
                player.getCooldowns().addCooldown(stack, (int) Math.ceil(20.0 / recharge));
                if (player.level() instanceof ServerLevel level) {
                    play(level, player, SoundEvents.SHIELD_BREAK.value(), 0.5f, 1.5f);
                }
                CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.shield_tired"));
            }
        } else {
            left = Math.min(max, left + recharge);
        }
        shieldEnergy.put(id, left);
        if (tick % 5 == 0) {
            showShieldEnergy(player, left / max);
        }
    }

    /** The energy as the bar of the kit shields: hidden when full, like durability. */
    private static void showShieldEnergy(ServerPlayer player, double share) {
        for (ItemStack stack : new ItemStack[]{player.getOffhandItem(), player.getMainHandItem()}) {
            if (stack.isEmpty() || !stack.has(DataComponents.BLOCKS_ATTACKS) || !KitService.isKit(stack)
                    || !stack.has(DataComponents.MAX_DAMAGE)) {
                continue;
            }
            if (stack.has(DataComponents.UNBREAKABLE)) {
                // Kit items never wear out anyway (ItemDurabilityMixin); without the flag the bar shows.
                stack.remove(DataComponents.UNBREAKABLE);
            }
            int maxDamage = stack.getMaxDamage();
            // In 20 steps, so the slot is not sent to the client every tick.
            double step = Math.ceil(Math.max(0.0, Math.min(1.0, share)) * 20.0) / 20.0;
            int damage = Math.min(maxDamage - 1, (int) Math.round(maxDamage * (1.0 - step)));
            if (stack.getDamageValue() != damage) {
                stack.setDamageValue(damage);
            }
        }
    }

    /**
     * Monsters close by that go for a teammate turn on the guardian. The bulwark reaches further and is
     * always sure (Simon 30.10.: "the bulwark is the worst tank of its class": every guardian drew alike).
     * A tank (way of the shield) also takes monsters off the healers (config tanksPeelHealers); before,
     * a monster on any guardian was left alone, so the medic and the warden kept theirs.
     */
    private void aggro(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        if (player.isCreative()) {
            return;
        }
        SiegeManager sm = SiegeManager.get();
        if (!isTank(skills) && !(sm != null && sm.config() != null && sm.config().healersChallenge)) {
            return;   // Simon 31.10.: the healers do not pull monsters off the damage dealers
        }
        double extra = tree.totalBonus(SkillEffect.AGGRO, skills);
        double call = SkillTree.SUB_BULWARK.equals(tree.subclassOf(skills))
                ? Math.max(0.0, SkillValues.get("g_challenger", "bulwark", 4.0)) : 0.0;
        SiegeManager m = SiegeManager.get();
        boolean peel = isTank(skills) && (m == null || m.config() == null || m.config().tanksPeelHealers);
        double radius = SkillValues.get("g_challenger", "radius", 6.0) + extra + call;
        for (LivingEntity e : CombatEffects.enemies(level, player.position(), radius, 3.0)) {
            if (!(e instanceof Mob mob) || !(mob.getTarget() instanceof Player other) || other == player) {
                continue;
            }
            if (other instanceof ServerPlayer mate) {
                PlayerSkills held = skillsFor.apply(mate);
                if (isGuardian(held) && (!peel || isTank(held))) {
                    continue;   // another tank already holds it (a healer's monster only a tank takes)
                }
            }
            if (extra > 0 || call > 0 || random.nextDouble() < SkillValues.get("g_challenger", "chance", 0.6)) {
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
        int most = (int) Math.round(SkillValues.get("g_ironhide", "count", 4.0));
        int near = Math.min(most, CombatEffects.enemies(level, player.position(), SkillValues.get("g_ironhide", "radius", 5.0), 2.0).size());
        if (near > 0) {
            addAbsorption(player, (float) (per * near), (float) SkillValues.get("g_ironhide", "cap", 8.0));
            if (player.getAbsorptionAmount() > 0.01f) {
                ironhideUp.add(player.getUUID());
            }
            level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY(0.6), player.getZ(), 6, 0.4, 0.5, 0.4, 0.0);
        }
    }

    /** The ward: the weakest player close by (the warden too) gets a barrier. */
    private void ward(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        double ward = tree.totalBonus(SkillEffect.WARD, skills);
        if (ward <= 0) {
            return;
        }
        String node = wardNode(skills);
        double reach = SkillValues.get(node, "radius", 12.0);
        ServerPlayer weakest = null;
        double lowest = Double.MAX_VALUE;
        for (ServerPlayer mate : mates(level)) {
            if (!mate.isAlive() || mate.isSpectator() || Drones.isDrone(mate) || mate.distanceToSqr(player) > reach * reach) {
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
        float amount = (float) ((SkillValues.get(node, "base", 2.0) + ward) * barrierPower(skills));
        if (weakest.getAbsorptionAmount() < amount) {
            giveBarrier(player, weakest, amount, amount);
            ring(level, weakest.position().add(0, 1.0, 0), 0.8, ParticleTypes.END_ROD, 12);
            play(level, weakest, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        }
        if (SkillTree.has(skills, "g_cleanse")) {
            cleanse(weakest);
        }
    }

    /** The heal aura (field dressing): every few seconds the players close by heal a little. */
    private void aura(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        double amount = tree.totalBonus(SkillEffect.HEAL_AURA, skills);
        if (amount <= 0) {
            return;
        }
        double radius = SkillValues.get(auraNode(skills), "radius", 8.0) + auraRange(skills);
        for (ServerPlayer mate : mates(level)) {
            if (!mate.isAlive() || mate.distanceToSqr(player) > radius * radius) {
                continue;
            }
            if (heal(player, mate, (float) amount) > 0) {
                level.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 2.0, mate.getZ(),
                        1, 0.2, 0.1, 0.2, 0.0);
            }
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
                ServerPlayer mate = lookMate(level, player, SkillValues.get("g_healing_hand", "range", 16.0));
                if (mate == null) {
                    mate = MeleeSim.benchMate(player);   // the test bench's wounded teammate
                }
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
            int cooldown = player.getAbilities().instabuild ? 20
                    : (int) Math.round(toss ? SkillValues.get("g_shieldthrow", "cd", 160.0) : SkillValues.get("g_dome", "cd", 500.0));
            actionReadyAt.put(player.getUUID(), now + cooldown);
        }
    }

    // ------------------------------------------------------------------
    // the healers' keys (Simon, 27 October 2026): Fähigkeit 3 picks on the wheel, Fähigkeit 2 casts
    // ------------------------------------------------------------------

    /** The healing abilities this guardian has, in wheel order: healing hand, ward, dome. */
    private List<String> heals(PlayerSkills skills) {
        List<String> out = new ArrayList<>();
        if (!isGuardian(skills)) {
            return out;
        }
        if (tree.totalBonus(SkillEffect.HEALING_HAND, skills) > 0) {
            out.add("hand");
        }
        if (tree.totalBonus(SkillEffect.WARD, skills) > 0) {
            out.add("ward");
        }
        if (tree.totalBonus(SkillEffect.DOME, skills) > 0) {
            out.add("dome");
        }
        return out;
    }

    private String healOf(ServerPlayer player, List<String> heals) {
        String picked = healChoice.get(player.getUUID());
        return picked != null && heals.contains(picked) ? picked : heals.isEmpty() ? null : heals.get(0);
    }

    /** The healer's wheel, or null when the guardian has no healing ability to pick. */
    public com.mki.nightsiege.core.WheelData healWheel(ServerPlayer player) {
        PlayerSkills skills = skillsFor.apply(player);
        List<String> heals = heals(skills);
        if (heals.isEmpty()) {
            return null;
        }
        com.mki.nightsiege.core.WheelData wheel = new com.mki.nightsiege.core.WheelData();
        wheel.title = "nightsiege.wheel.heals";
        wheel.hint = "nightsiege.wheel.heals.hint";
        String current = healOf(player, heals);
        long now = player.level().getGameTime();
        for (String id : heals) {
            com.mki.nightsiege.core.WheelData.Option o = new com.mki.nightsiege.core.WheelData.Option();
            o.id = id;
            o.icon = switch (id) {
                case "hand" -> "minecraft:honey_bottle";
                case "ward" -> "minecraft:amethyst_shard";
                default -> "minecraft:glass";
            };
            o.name = "nightsiege.heal." + id;
            long ready = switch (id) {
                case "hand" -> handReadyAt.getOrDefault(player.getUUID(), 0L);
                case "ward" -> wardSkipUntil.getOrDefault(player.getUUID(), 0L);
                default -> actionReadyAt.getOrDefault(player.getUUID(), 0L);
            };
            if (ready > now) {
                o.sub = "nightsiege.wheel.wait";
                o.subArgs.add(String.valueOf((ready - now + 19) / 20));
            } else {
                o.sub = "nightsiege.wheel.ready";
            }
            o.current = id.equals(current);
            wheel.options.add(o);
        }
        return wheel;
    }

    public void pickHeal(ServerPlayer player, String id) {
        if (heals(skillsFor.apply(player)).contains(id)) {
            healChoice.put(player.getUUID(), id);
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.heal_chosen",
                    Component.translatable("nightsiege.heal." + id)));
        }
    }

    /** A short press of Fähigkeit 3: the next healing ability. False when the player has none. */
    public boolean nextHeal(ServerPlayer player) {
        List<String> heals = heals(skillsFor.apply(player));
        if (heals.isEmpty()) {
            return false;
        }
        String current = healOf(player, heals);
        pickHeal(player, heals.get((heals.indexOf(current) + 1) % heals.size()));
        return true;
    }

    /** The id of the medic's own healing key on the bar (Simon 30.10.). */
    public static final String SELF_HEAL = "selfheal";

    /**
     * The medic's key to heal himself (Simon 30.10.: "der sanitäter braucht eine extra taste um sich
     * selbst zu heilen"): his healing hand on himself, whoever he looks at (it shares the hand's pause).
     */
    public com.mki.nightsiege.core.UltState.Slot selfHealSlot(ServerPlayer player) {
        PlayerSkills skills = skillsFor.apply(player);
        if (!heals(skills).contains("hand")) {
            return null;
        }
        long now = player.level().getGameTime();
        com.mki.nightsiege.core.UltState.Slot slot = new com.mki.nightsiege.core.UltState.Slot();
        slot.id = SELF_HEAL;
        slot.icon = "minecraft:glistering_melon_slice";
        slot.name = "nightsiege.heal.self";
        slot.sub = "nightsiege.heal.self.sub";
        long ready = handReadyAt.getOrDefault(player.getUUID(), 0L);
        slot.cooldownTicks = ready > now ? (int) (ready - now) : 0;
        slot.totalTicks = (int) Math.round(SkillValues.get("g_healing_hand", "cd", 120.0));
        return slot;
    }

    /** The self-heal key: the healing hand on himself. */
    public boolean castSelfHeal(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (!heals(skills).contains("hand")) {
            return false;
        }
        handHeal(level, player, skills, player, level.getGameTime());
        return true;
    }

    /**
     * Fähigkeit 2: the picked healing ability, on the teammate looked at (else on oneself) – no
     * shield needed. False when the player has none, so the key may do something else.
     */
    public boolean castHeal(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        PlayerSkills skills = skillsFor.apply(player);
        List<String> heals = heals(skills);
        String id = healOf(player, heals);
        if (id == null) {
            return false;
        }
        long now = level.getGameTime();
        ServerPlayer mate = lookMate(level, player, SkillValues.get("g_healing_hand", "range", 16.0));
        switch (id) {
            case "hand" -> {
                if (mate == null) {
                    mate = MeleeSim.benchMate(player);
                }
                handHeal(level, player, skills, mate == null ? player : mate, now);
            }
            case "ward" -> wardByHand(level, player, skills, mate == null ? player : mate, now);
            default -> {
                long ready = actionReadyAt.getOrDefault(player.getUUID(), 0L);
                if (now < ready) {
                    CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.shield_action_wait", (ready - now + 19) / 20));
                } else if (raiseDome(level, player, now)) {
                    actionReadyAt.put(player.getUUID(), now + (player.getAbilities().instabuild ? 20
                            : Math.round(SkillValues.get("g_dome", "cd", 500.0))));
                }
            }
        }
        return true;
    }

    /**
     * The warden's ward by hand: the barrier of his next timed ward goes now to the player
     * looked at (else to himself); the timed one waits one interval, so it is not more ward.
     */
    private void wardByHand(ServerLevel level, ServerPlayer player, PlayerSkills skills, ServerPlayer target, long now) {
        long ready = wardSkipUntil.getOrDefault(player.getUUID(), 0L);
        if (now < ready) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.healing_hand_wait", (ready - now + 19) / 20));
            return;
        }
        String node = wardNode(skills);
        float amount = (float) ((SkillValues.get(node, "base", 2.0) + tree.totalBonus(SkillEffect.WARD, skills)) * barrierPower(skills));
        giveBarrier(player, target, amount, amount);
        ring(level, target.position().add(0, 1.0, 0), 0.8, ParticleTypes.END_ROD, 12);
        play(level, target, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        if (target != player) {
            trail(level, player.getEyePosition().subtract(0, 0.3, 0), target.position().add(0, 1.0, 0), ParticleTypes.END_ROD, 10);
        }
        if (SkillTree.has(skills, "g_cleanse")) {
            cleanse(target);
        }
        salve(player, target);
        wardSkipUntil.put(player.getUUID(), now + (player.getAbilities().instabuild ? 20 : every(node, "interval", 160.0)));
    }

    /** Wundsalbe (crate gain, 27 October 2026): the healed one loses gaping wounds and broken armour. */
    private static void salve(ServerPlayer healer, ServerPlayer target) {
        if (CrateEffects.gainLevel(healer, "wundsalbe") <= 0) {
            return;
        }
        Wounds.cure(target);
        if (ArmorBreak.EFFECT != null) {
            target.removeEffect(ArmorBreak.EFFECT);
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
        float amount = (float) ((SkillValues.get("g_healing_hand", "base", 2.0) + tree.totalBonus(SkillEffect.HEALING_HAND, skills))
                * (self ? SkillValues.get("g_healing_hand", "self", 0.5) : 1.0));
        heal(player, target, amount);
        salve(player, target);
        if (SkillTree.has(skills, "g_miracle")) {
            float barrier = (float) (tree.totalBonus(SkillEffect.MIRACLE, skills) * barrierPower(skills));
            giveBarrier(player, target, barrier, barrier);
        }
        if (!self) {
            trail(level, player.getEyePosition().subtract(0, 0.3, 0), target.position().add(0, 1.0, 0),
                    ParticleTypes.HAPPY_VILLAGER, 10);
        }
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 1.9, target.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        play(level, target, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
        handReadyAt.put(player.getUUID(), now + (player.getAbilities().instabuild ? 10
                : Math.round(SkillValues.get("g_healing_hand", "cd", 120.0))));
    }

    /** The shield flies, bounces from monster to monster and comes back. */
    private boolean throwShield(ServerLevel level, ServerPlayer player, PlayerSkills skills) {
        LivingEntity first = CombatEffects.lookTarget(level, player, SkillValues.get("g_shieldthrow", "range", 14.0));
        if (first == null) {
            CombatEffects.actionBar(player, Component.translatable("nightsiege.msg.ult_no_target"));
            return false;
        }
        boolean boomerang = SkillTree.has(skills, "g_boomerang");
        int count = (int) Math.round(boomerang ? SkillValues.get("g_boomerang", "targets", 4.0)
                : SkillValues.get("g_shieldthrow", "targets", 3.0));
        double hop = SkillValues.get("g_shieldthrow", "chain", 6.0);
        List<LivingEntity> chain = new ArrayList<>();
        chain.add(first);
        LivingEntity last = first;
        while (chain.size() < count) {
            LivingEntity next = null;
            double best = hop * hop;
            for (LivingEntity e : CombatEffects.enemies(level, last.position(), hop, 2.0)) {
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
        float hurt = (float) (tree.totalBonus(SkillEffect.SHIELD_THROW, skills) * bladeshield(skills));
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
        zones.add(new Zone(ZoneKind.DOME, player.getUUID(), level, player.position(), SkillValues.get("g_dome", "radius", 4.0),
                now + Math.round(SkillValues.get("g_dome", "time", 100.0))));
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
            if (tree.totalBonus(SkillEffect.TRIAGE, skills) > 0
                    && target.getHealth() < target.getMaxHealth() * (float) SkillValues.get("g_triage", "below", 0.4)) {
                power *= SkillValues.get("g_triage", "mult", 2.0);
            }
            power *= subclassHealing(skills);
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
            float cap = (float) (overflow * patron(skills));
            giveBarrier(healer, target, (float) ((amount - real) * patron(skills)), cap);
        }
        if (real > 0 && tree.totalBonus(SkillEffect.GRACE, skills) > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.SPEED,
                    (int) Math.round(SkillValues.get("g_grace", "time", 60.0)), 0, true, false, true));
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
        double r = SkillValues.get("taunt", "radius" + rank, 7.0 + 3.0 * rank);   // 10 / 13 / 16
        for (LivingEntity victim : CombatEffects.enemies(level, player.position(), r, 4.0)) {
            if (victim instanceof Mob mob) {
                mob.setTarget(player);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, victim.getX(), victim.getY() + victim.getBbHeight() + 0.3,
                        victim.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
            }
        }
        int length = (int) Math.round(SkillValues.get("taunt", "res" + rank, 60.0 + 20.0 * rank));
        tauntUntil.put(player.getUUID(), now + length);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, length, 1));
        int absorbTime = (int) Math.round(SkillValues.get("taunt", "absorbtime", 200.0));
        boolean ironWill = SkillTree.has(skills, "g_iron_will");
        boolean shared = SkillTree.has(skills, "g_shared_burden");
        // The guardian holds the line: a shield of extra hearts for the team around.
        for (ServerPlayer mate : mates(level)) {
            if (!mate.isAlive() || mate.distanceToSqr(player) > r * r) {
                continue;
            }
            // Absorption II/III/IV (4 health per amplifier step); iron will doubles the guardian's own.
            float wall = (float) (SkillValues.get("taunt", "absorb" + rank, 4.0 * (rank + 1))
                    * (mate == player && ironWill ? 2.0 : 1.0));
            int amp = Math.max(0, Math.round(wall / 4f) - 1);
            float before = Math.max(0f, mate.getAbsorptionAmount());
            mate.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, absorbTime, amp));
            if (mate != player) {
                credit.accept(player, Math.max(0f, mate.getAbsorptionAmount() - before), null);
            }
            if (shared && mate != player) {
                mate.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,
                        (int) Math.round(SkillValues.get("g_shared_burden", "res", 200.0)), 0));
                // On top of the taunt's absorption (with the cap at 4 it was swallowed by it and did nothing).
                float barrier = (float) SkillValues.get("g_shared_burden", "barrier", 4.0);
                giveBarrier(player, mate, barrier, Math.max(0f, mate.getAbsorptionAmount()) + barrier);
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
        float damage = (float) (SkillValues.get("shield_charge", "dmg" + rank, 3.0 + 3.0 * rank)   // 6 / 9 / 12
                * group(skills));
        Vec3 start = player.position();
        DamageSource own = level.damageSources().playerAttack(player);
        double reach = SkillValues.get("shield_charge", "range", 6.0);
        int slow = (int) Math.round(rampart ? SkillValues.get("g_rampart", "slow", 40.0) : SkillValues.get("shield_charge", "slow", 40.0));
        for (LivingEntity victim : CombatEffects.enemies(level, start, reach, 2.0)) {
            Vec3 to = victim.position().subtract(start);
            Vec3 dir = new Vec3(to.x, 0, to.z);
            if (dir.lengthSqr() < 1.0e-4 || dir.normalize().dot(flat) < 0.5) {
                continue;   // only a cone of about 60 degrees to each side
            }
            combat.bonusHit(level, victim, own, damage);
            double force = rampart ? 2.6 : 1.4;
            victim.push(flat.x * force, rampart ? 0.6 : 0.35, flat.z * force);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, rampart ? 9 : 2));
        }
        // The guardian goes with the charge.
        player.push(flat.x * 1.2, 0.1, flat.z * 1.2);
        player.hurtMarked = true;
        for (int i = 1; i <= 6; i++) {
            Vec3 p = start.add(flat.scale(i)).add(0, 1.0, 0);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0.02);
        }
        if (debris) {
            int hits = (int) Math.round(SkillValues.get("g_debris", "hits", 3.0));
            float hit = (float) (SkillValues.get("g_debris", "dmg", 4.0) * group(skills));
            double blast = SkillValues.get("g_debris", "radius", 2.0);
            for (int i = 1; i <= hits; i++) {
                Vec3 at = start.add(flat.scale(2.0 * i));
                combat.schedule(3 * i, () -> {
                    for (LivingEntity victim : CombatEffects.enemies(level, at, blast, 1.5)) {
                        combat.bonusHit(level, victim, own, hit);
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
        double reach = SkillValues.get("mass_heal", "radius", 12.0);
        float amount = (float) SkillValues.get("mass_heal", "heal" + rank, 6.0 + 4.0 * rank);
        int regen = (int) Math.round(SkillValues.get("mass_heal", "regen", 100.0));
        for (ServerPlayer mate : mates(level)) {
            if (mate.isAlive() && mate.distanceToSqr(player) <= reach * reach) {
                heal(player, mate, amount);
                mate.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 1));
                if (rebirth) {
                    removeHarmful(mate);
                }
                level.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 1.8, mate.getZ(),
                        6, 0.4, 0.3, 0.4, 0.0);
            }
        }
        if (rebirth) {
            combat.readyAngel(player.getUUID());
            handReadyAt.remove(player.getUUID());   // the healing hand is ready again at once
        }
        if (SkillTree.has(skills, "g_lifespring")) {
            zones.add(new Zone(ZoneKind.LIFESPRING, player.getUUID(), level, player.position(),
                    SkillValues.get("g_lifespring", "radius", 6.0),
                    level.getGameTime() + Math.round(SkillValues.get("g_lifespring", "time", 120.0))));
        }
        play(level, player, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.4f);
        return true;
    }

    boolean sanctuary(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        long length = Math.round(SkillValues.get("g_sanctuary", "time" + rank, 100.0 + 20.0 * rank)
                + (SkillTree.has(skills, "g_eternal_watch") ? SkillValues.get("g_eternal_watch", "time", 60.0) : 0.0));
        double radius = SkillValues.get("g_sanctuary", "radius", 8.0);
        zones.add(new Zone(ZoneKind.SANCTUARY, player.getUUID(), level, player.position(), radius,
                level.getGameTime() + length));
        ring(level, player.position().add(0, 0.2, 0), radius, ParticleTypes.END_ROD, 60);
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
                double perSecond = switch (zone.kind()) {
                    case LIFESPRING -> SkillValues.get("g_lifespring", "heal", 1.5);
                    case DOME -> SkillTree.has(skills, "g_wellspring") ? SkillValues.get("g_wellspring", "heal", 1.5) : 0.0;
                    case SANCTUARY -> SkillTree.has(skills, "g_lightcircle") ? SkillValues.get("g_lightcircle", "heal", 1.5) : 0.0;
                };
                if (perSecond > 0) {
                    for (ServerPlayer mate : mates(level)) {
                        if (mate.isAlive() && inside(zone, mate) && heal(owner, mate, (float) perSecond) > 0) {
                            level.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 1.9, mate.getZ(),
                                    1, 0.2, 0.1, 0.2, 0.0);
                        }
                    }
                }
                if (zone.kind() == ZoneKind.SANCTUARY && SkillTree.has(skills, "g_lightcircle")) {
                    DamageSource own = level.damageSources().playerAttack(owner);
                    for (LivingEntity e : CombatEffects.enemies(level, zone.center(), zone.radius() + 1.5, 3.0)) {
                        e.igniteForSeconds((float) SkillValues.get("g_lightcircle", "burn", 3.0));
                        if (CombatEffects.isUndead(e)) {
                            combat.bonusHit(level, e, own, (float) SkillValues.get("g_lightcircle", "undead", 3.0));
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
        PlayerSkills skills = owner == null ? null : skillsFor.apply(owner);
        if (zone.kind() == ZoneKind.SANCTUARY && owner != null && SkillTree.has(skills, "g_eternal_watch")) {
            float barrier = (float) (SkillValues.get("g_eternal_watch", "barrier", 4.0) * barrierPower(skills));
            for (ServerPlayer mate : mates(level)) {
                if (mate.isAlive() && inside(zone, mate)) {
                    giveBarrier(owner, mate, barrier, barrier);
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

    /** The players a heal or barrier can reach: everyone in the level, and on the test bench its wounded teammate. */
    static List<ServerPlayer> mates(ServerLevel level) {
        List<ServerPlayer> extra = MeleeSim.benchMates(level);
        if (extra.isEmpty()) {
            return level.players();
        }
        List<ServerPlayer> all = new ArrayList<>(level.players());
        all.addAll(extra);
        return all;
    }

    public static boolean isGuardian(PlayerSkills skills) {
        return skills != null && skills.clazz() == SkillClass.GUARDIAN;
    }

    /** A tank: a guardian on the way of the shield (shield fighter, bulwark), not a healer. */
    private boolean isTank(PlayerSkills skills) {
        return isGuardian(skills) && SkillTree.WAY_SHIELD.equals(skills.pathTaken(tree));
    }

    /** The boni of the way of the shield need a shield at hand; the other way has no such rule. */
    private boolean shieldWay(ServerPlayer player, PlayerSkills skills) {
        if (!SkillTree.WAY_SHIELD.equals(skills.pathTaken(tree))) {
            return true;
        }
        return player.getMainHandItem().has(DataComponents.BLOCKS_ATTACKS)
                || player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
    }

    /** Which node's numbers the bastion uses: the shield's own or the blessing's cross node. */
    private static String bastionNode(PlayerSkills skills) {
        return skills.levelOf("g_bastion") > 0 ? "g_bastion" : "b_cross_bastion";
    }

    private static String wardNode(PlayerSkills skills) {
        return skills != null && skills.levelOf("g_ward") > 0 ? "g_ward" : "s_cross_ward";
    }

    private static String auraNode(PlayerSkills skills) {
        return skills != null && skills.levelOf("field_dressing") > 0 ? "field_dressing" : "s_cross_dressing";
    }

    /** A pulse every so many ticks, read from the skill values (at least every tick). */
    private static int every(String node, String key, double fallback) {
        return Math.max(1, (int) Math.round(SkillValues.get(node, key, fallback)));
    }

    /**
     * Simon 26.10.: the bulwark's damage on groups (hedgehog, vengeance, shield charge, debris)
     * is 25 % lower; one target stays as it was.
     */
    private double group(PlayerSkills skills) {
        return SkillTree.SUB_BULWARK.equals(tree.subclassOf(skills)) ? SkillValues.get("g_bastion", "group", 0.75) : 1.0;
    }

    /** The blade shield doubles riposte, thorns and the shield throw. */
    private static double bladeshield(PlayerSkills skills) {
        return SkillTree.has(skills, "g_bladeshield") ? SkillValues.get("g_bladeshield", "mult", 2.0) : 1.0;
    }

    /** The patron makes every barrier of the guardian stronger. */
    private double patron(PlayerSkills skills) {
        double extra = SkillTree.has(skills, "g_patron") ? tree.totalBonus(SkillEffect.PATRON, skills) : 0.0;
        return 1.0 + Math.max(0.0, extra);
    }

    /** Strength of a new barrier: prayer and the saint work on barriers as on healing, the patron on top. */
    private double barrierPower(PlayerSkills skills) {
        if (!isGuardian(skills)) {
            return 1.0;
        }
        double power = 1.0 + tree.totalBonus(SkillEffect.HEAL_POWER, skills);
        if (SkillTree.has(skills, "g_saint")) {
            power += tree.totalBonus(SkillEffect.SAINT, skills);
        }
        return power * patron(skills) * subclassHealing(skills);
    }

    /** Server balancing per subclass (Simon 27.10.: the medic heals 20 % more, like the warden 3.8/s). */
    private double subclassHealing(PlayerSkills skills) {
        SiegeManager m = SiegeManager.get();
        return m == null || m.config() == null ? 1.0 : m.config().healingFactor(tree.subclassOf(skills));
    }

    /**
     * A barrier from this guardian: the life bond knows who gave it, and what it really
     * adds on a teammate counts as healing (night stats and skill points).
     */
    private void giveBarrier(ServerPlayer giver, ServerPlayer target, float amount, float cap) {
        float before = Math.max(0f, target.getAbsorptionAmount());
        addAbsorption(target, amount, cap);
        float gained = Math.max(0f, target.getAbsorptionAmount()) - before;
        if (gained <= 0.01f) {
            return;
        }
        if (giver != null) {
            barrierFrom.put(target.getUUID(), giver.getUUID());
            if (target != giver) {
                credit.accept(giver, gained, null);
            }
        }
    }

    /**
     * Iron ward: with a shield at hand the guardian gets armour and armour toughness
     * (only then, unlike toughness from the trunk, which always holds).
     */
    private void ironwardModifier(ServerPlayer player, PlayerSkills skills) {
        double armor = skills == null ? 0.0 : tree.totalBonus(SkillEffect.IRONWARD, skills);
        boolean shield = player.getMainHandItem().has(DataComponents.BLOCKS_ATTACKS)
                || player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
        double steps = skills == null ? 0.0 : skills.levelOf("g_ironward");
        boolean on = armor > 0 && shield;
        modifier(player, Attributes.ARMOR, IRONWARD_ARMOR, on ? armor : 0.0);
        modifier(player, Attributes.ARMOR_TOUGHNESS, IRONWARD_TOUGHNESS,
                on ? steps * SkillValues.get("g_ironward", "toughness", 1.0) : 0.0);
    }

    private static void modifier(ServerPlayer player, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                 Identifier id, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (value > 0) {
            AttributeModifier have = instance.getModifier(id);
            if (have == null || Math.abs(have.amount() - value) > 1.0e-6) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
            }
        } else if (instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }

    private static boolean anchored(ServerPlayer player) {
        return player.isShiftKeyDown() && player.isBlocking();
    }

    /** Monsters close by that are after this player. */
    private static int attackers(ServerLevel level, ServerPlayer player, double radius) {
        int n = 0;
        for (LivingEntity e : CombatEffects.enemies(level, player.position(), radius, 3.0)) {
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
        for (ServerPlayer mate : mates(level)) {
            if (mate == player || !mate.isAlive() || mate.isSpectator() || Drones.isDrone(mate)) {
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
        ironhideUp.remove(player);
    }
}
