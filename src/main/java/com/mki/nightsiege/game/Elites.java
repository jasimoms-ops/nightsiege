package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Elite monsters (Simon 05.11.: "Stärkere Monster statt nur mehr"). From level 10 on, one in eight of
 * the night's monsters (rising to one in five at level 20) is an elite: more life, a name over its
 * head and a glow in the colour of its affix. From level 15 on some carry both affixes.
 * <ul>
 *   <li><b>Schnell</b> (yellow): runs 40 % faster and strikes again 30 % sooner.</li>
 *   <li><b>Heilungshemmend</b> (dark green): every hit leaves gaping wounds (half healing for 4 s).</li>
 * </ul>
 * Not the bosses, champions, guards or the summoner's dead; only the waves and the refills of a night.
 */
public final class Elites {

    /** Every elite carries this tag and the tag of each of its affixes (they live in the entity's data). */
    public static final String TAG = "nightsiege_affix_elite";

    public enum Affix {
        FAST("fast", ChatFormatting.YELLOW, 0xFFE040),
        WOUNDS("wounds", ChatFormatting.DARK_GREEN, 0x2F7A2A);

        private final String id;
        private final ChatFormatting color;
        private final int rgb;

        Affix(String id, ChatFormatting color, int rgb) {
            this.id = id;
            this.color = color;
            this.rgb = rgb;
        }

        public String id() {
            return id;
        }

        public String tag() {
            return TAG + "_" + id;
        }

        public ChatFormatting color() {
            return color;
        }

        public int rgb() {
            return rgb;
        }
    }

    /** The glow colours: one affix its own, both purple. */
    public static final String TEAM_FAST = "nightsiege_elite_fast";
    public static final String TEAM_WOUNDS = "nightsiege_elite_wounds";
    public static final String TEAM_BOTH = "nightsiege_elite_both";

    private static final Identifier LIFE = Identifier.fromNamespaceAndPath("nightsiege", "elite_life");
    private static final Identifier SPEED = Identifier.fromNamespaceAndPath("nightsiege", "elite_speed");

    /** Elites made this night (for the tests and the statistics). */
    private static int made;

    private Elites() {
    }

    // ------------------------------------------------------------------ who becomes one

    /** The chance that a monster of this level is an elite (0 below the start level). */
    public static double chance(SiegeConfig cfg, int level) {
        if (cfg == null || !cfg.eliteMobs || level < cfg.eliteMobFromLevel) {
            return 0.0;
        }
        if (cfg.eliteMobMaxLevel <= cfg.eliteMobFromLevel || level >= cfg.eliteMobMaxLevel) {
            return level >= cfg.eliteMobMaxLevel ? cfg.eliteMobChanceMax : cfg.eliteMobChance;
        }
        double t = (level - cfg.eliteMobFromLevel) / (double) (cfg.eliteMobMaxLevel - cfg.eliteMobFromLevel);
        return cfg.eliteMobChance + (cfg.eliteMobChanceMax - cfg.eliteMobChance) * t;
    }

    /** A new monster of the night: rolls whether it is an elite, and makes it one. */
    public static boolean maybe(ServerLevel level, Mob mob, int siegeLevel, SiegeConfig cfg, Random random) {
        if (mob == null || level == null || !eligible(mob)) {
            return false;
        }
        double chance = chance(cfg, siegeLevel);
        if (chance <= 0 || random.nextDouble() >= chance) {
            return false;
        }
        Set<Affix> affixes = EnumSet.noneOf(Affix.class);
        if (siegeLevel >= cfg.eliteMobTwoFromLevel && random.nextDouble() < cfg.eliteMobTwoChance) {
            affixes.addAll(List.of(Affix.values()));
        } else {
            affixes.add(Affix.values()[random.nextInt(Affix.values().length)]);
        }
        make(level, mob, affixes, cfg);
        return true;
    }

    /** Who may become an elite: plain monsters of the night, no boss, champion, guard or special one. */
    static boolean eligible(Mob mob) {
        if (mob.entityTags().contains(TAG) || mob.entityTags().contains(MobFactory.TAG_ELITE)
                || BossService.immovable(mob) || Companions.isCompanion(mob) || Nemesis.isNemesis(mob)) {
            return false;
        }
        Foes.Role role = Foes.roleOf(mob);
        // The treasure raider runs away and the stunner already counters the fighters.
        return role != Foes.Role.TREASURE_RAIDER && role != Foes.Role.STUNNER
                && !(mob instanceof net.minecraft.world.entity.monster.Phantom);
    }

    /** Makes the monster an elite with these affixes (also for the tests). */
    public static void make(ServerLevel level, Mob mob, Set<Affix> affixes, SiegeConfig cfg) {
        if (affixes.isEmpty()) {
            return;
        }
        mob.addTag(TAG);
        for (Affix a : affixes) {
            mob.addTag(a.tag());
        }
        AttributeInstance life = mob.getAttribute(Attributes.MAX_HEALTH);
        if (life != null && !life.hasModifier(LIFE) && cfg.eliteMobLife > 1.0) {
            life.addPermanentModifier(new AttributeModifier(LIFE, cfg.eliteMobLife - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
        if (affixes.contains(Affix.FAST)) {
            AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(SPEED) && cfg.eliteFastSpeed > 0) {
                speed.addPermanentModifier(new AttributeModifier(SPEED, cfg.eliteFastSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
        mob.setCustomName(name(affixes));
        mob.setCustomNameVisible(true);
        boolean both = affixes.size() > 1;
        Affix one = affixes.iterator().next();
        SiegeTeams.mark(level.getServer(), mob, both ? TEAM_BOTH : one == Affix.FAST ? TEAM_FAST : TEAM_WOUNDS,
                both ? TeamColor.LIGHT_PURPLE : one == Affix.FAST ? TeamColor.YELLOW : TeamColor.DARK_GREEN);
        made++;
    }

    /** "Elite · Schnell · Heilungshemmend" in the affixes' colours. */
    static MutableComponent name(Set<Affix> affixes) {
        MutableComponent name = Component.translatable("nightsiege.affix_elite.name").withStyle(ChatFormatting.GOLD);
        for (Affix a : affixes) {
            name.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("nightsiege.affix_elite." + a.id()).withStyle(a.color()));
        }
        return name;
    }

    // ------------------------------------------------------------------ what they do

    /** True when the entity is an elite with this affix. */
    public static boolean has(Entity entity, Affix affix) {
        return entity != null && entity.entityTags().contains(affix.tag());
    }

    /** True for any elite of this kind. */
    public static boolean isElite(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG);
    }

    /** The affixes of an elite (empty for others). */
    public static List<Affix> affixes(Entity entity) {
        List<Affix> out = new ArrayList<>();
        for (Affix a : Affix.values()) {
            if (has(entity, a)) {
                out.add(a);
            }
        }
        return out;
    }

    /** Its hits leave gaping wounds (Wounds asks). */
    public static boolean wounds(Entity attacker) {
        return has(attacker, Affix.WOUNDS);
    }

    /** How much shorter the pause between its melee attacks is (the melee goal mixin asks). */
    public static double attackCut(Mob mob) {
        if (!has(mob, Affix.FAST)) {
            return 0.0;
        }
        SiegeManager m = SiegeManager.get();
        return m == null ? 0.3 : m.config().eliteFastAttack;
    }

    /** A few sparks in the affix's colour around the living elites (once a second from the night's tick). */
    public static void sparkle(ServerLevel level, Iterable<java.util.UUID> mobs) {
        for (java.util.UUID id : mobs) {
            Entity e = level.getEntity(id);
            if (e == null || !isElite(e)) {
                continue;
            }
            for (Affix a : affixes(e)) {
                level.sendParticles(new DustParticleOptions(a.rgb(), 1.1f), e.getX(), e.getY(0.6), e.getZ(), 3, 0.3, 0.4, 0.3, 0.0);
            }
        }
    }

    /** Elites made since the last night began. */
    public static int made() {
        return made;
    }

    /** The night ended: the count starts again (the teams go with the other glow teams). */
    public static void clear() {
        made = 0;
    }
}
