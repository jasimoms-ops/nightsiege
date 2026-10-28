package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.NemesisLore;
import com.mki.nightsiege.core.NemesisLore.Trait;
import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The nemesis (update 2 "Fluch & Rache", Simon 28 October 2026): a monster that kills a defender
 * flees in a cloud of smoke and comes back in a later night, with a name, a title, a bar of its own
 * and more power, and hunts its victim. If it kills again it rises in rank; whoever slays it gets
 * its trophy.
 */
public final class Nemesis {

    /** Every nemesis back in a night carries this tag (and {@code nightsiege_nem_<id>}). */
    public static final String TAG = "nightsiege_nemesis";
    private static final String TAG_ID = "nightsiege_nem_";
    private static final String STATUS_LURKING = "lauert";
    private static final String STATUS_SLAIN = "erschlagen";
    /** Slain nemeses kept for the wanted posters. */
    private static final int SLAIN_KEPT = 6;

    /** What the nemesis needs of the night. */
    public interface Host {
        ServerLevel world();

        BlockPos anchor();

        SiegeConfig config();

        Random random();

        SiegeSave save();

        boolean testNight();

        /** True for a monster of this night (not a boss, not a camp guard, no companion). */
        boolean isNightMob(UUID id);

        /** The monster fled: out of the night's list. */
        void forget(UUID id);

        /** Spawns a monster of this type on the side of this angle (the raid plan steers it). */
        Mob spawn(String type, double angle);

        /** The defenders standing now. */
        List<ServerPlayer> defenders();

        Set<UUID> nightPlayers();

        /** The class a defender plays (SkillClass name), or empty. */
        String classOf(UUID player);

        void broadcast(Component text);

        void notice(Component text);

        void persist();
    }

    /** A nemesis on the field tonight. */
    private static final class Live {
        final SiegeSave.NemesisRecord rec;
        final ServerBossEvent bar;
        final UUID mob;

        Live(SiegeSave.NemesisRecord rec, ServerBossEvent bar, UUID mob) {
            this.rec = rec;
            this.bar = bar;
            this.mob = mob;
        }
    }

    private static Nemesis current;

    private final Map<UUID, Live> live = new HashMap<>();
    private Host host;
    private boolean called;
    /** For the tests: nemeses made, promoted, back, slain this night. */
    public int made;
    public int promoted;
    public int returned;
    public int slain;

    public Nemesis() {
        current = this;
    }

    // ------------------------------------------------------------------ the night

    /** A night begins: every lurking nemesis of this kind of night has been away one more night. */
    public void beginNight(Host h) {
        endBars();
        live.clear();
        host = h;
        called = false;
        made = promoted = returned = slain = 0;
        current = this;
        for (SiegeSave.NemesisRecord rec : records(h.save(), h.testNight())) {
            if (rec.lurking()) {
                rec.nightsAway++;
            }
        }
    }

    /** The night is over: the bars go, a nemesis still standing slinks off (it lurks on). */
    public void endNight(Host h) {
        if (h != null && h.world() != null) {
            for (Live l : live.values()) {
                Entity e = h.world().getEntity(l.mob);
                if (e != null && e.isAlive()) {
                    h.broadcast(Component.translatable("nightsiege.nemesis.escaped", fullName(l.rec)).withStyle(ChatFormatting.DARK_RED));
                }
            }
        }
        endBars();
        live.clear();
        called = false;
    }

    private void endBars() {
        for (Live l : live.values()) {
            l.bar.removeAllPlayers();
        }
    }

    /** The second wave: the nemeses that come back tonight come with it. */
    public void onWave(Host h, int wave) {
        if (wave >= 2 && !called) {
            called = true;
            callIn(h, false);
        }
    }

    /**
     * Brings lurking nemeses back. {@code force} (the command) brings every lurking one of this
     * kind of night; otherwise a test night brings those away a night or more, a real night draws.
     */
    public int callIn(Host h, boolean force) {
        SiegeConfig cfg = h.config();
        if (!cfg.nemesisEnabled || h.world() == null || h.anchor() == null) {
            return 0;
        }
        List<SiegeSave.NemesisRecord> candidates = new ArrayList<>();
        for (SiegeSave.NemesisRecord rec : records(h.save(), h.testNight())) {
            if (rec.lurking() && !onField(rec)) {
                candidates.add(rec);
            }
        }
        Collections.shuffle(candidates, h.random());
        int cap = force || h.testNight() ? Math.max(1, cfg.nemesisMax) : cfg.nemesisPerNight;
        int count = 0;
        for (SiegeSave.NemesisRecord rec : candidates) {
            if (count >= cap) {
                break;
            }
            boolean comes = force
                    || (h.testNight() ? rec.nightsAway >= 1 : NemesisLore.returns(rec.nightsAway, cfg.nemesisReturnChance, h.random().nextDouble()));
            if (comes && bringBack(h, rec)) {
                count++;
            }
        }
        if (count > 0) {
            h.persist();
        }
        return count;
    }

    private boolean onField(SiegeSave.NemesisRecord rec) {
        for (Live l : live.values()) {
            if (l.rec == rec) {
                return true;
            }
        }
        return false;
    }

    private boolean bringBack(Host h, SiegeSave.NemesisRecord rec) {
        ServerPlayer prey = prey(h, rec);
        BlockPos a = h.anchor();
        double angle = prey == null ? h.random().nextDouble() * Math.PI * 2
                : Math.atan2(prey.getZ() - (a.getZ() + 0.5), prey.getX() - (a.getX() + 0.5));
        Mob mob = h.spawn(rec.type, angle);
        if (mob == null) {
            NightSiegeMod.LOGGER.info("[Night Siege] Nemesis {} ({}) fand keinen Platz", rec.name, rec.type);
            return false;
        }
        SiegeConfig cfg = h.config();
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * NemesisLore.healthFactor(cfg, rec.level, rec.traits));
            mob.setHealth((float) health.getBaseValue());
        }
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * NemesisLore.speedFactor(rec.traits));
        }
        if (NemesisLore.has(rec.traits, Trait.FIREPROOF)) {
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        }
        mob.addTag(TAG);
        mob.addTag(TAG_ID + rec.id);
        mob.setCustomName(fullName(rec).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        mob.setCustomNameVisible(true);
        SiegeTeams.mark(h.world().getServer(), mob, SiegeTeams.NEMESIS, TeamColor.RED);
        if (prey != null) {
            mob.setTarget(prey);
        }
        ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), barName(rec), BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_6);
        bar.setProgress(1.0f);
        live.put(mob.getUUID(), new Live(rec, bar, mob.getUUID()));
        showBar(h, bar);
        rec.nightsAway = 0;
        returned++;
        ServerLevel level = h.world();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 1.0, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
        level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1.0, mob.getZ(), 12, 0.5, 0.6, 0.5, 0.02);
        for (ServerPlayer p : h.defenders()) {
            level.playSound(null, p.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.5f, 0.7f);
        }
        h.broadcast(Component.translatable(prey == null ? "nightsiege.nemesis.back" : "nightsiege.nemesis.back_hunt",
                fullName(rec), prey == null ? "" : prey.getScoreboardName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        h.notice(Component.translatable("nightsiege.nemesis.bar_back", rec.name).withStyle(ChatFormatting.RED));
        NightSiegeMod.LOGGER.info("[Night Siege] Nemesis zurück: {} ({}, Stufe {}, {})", rec.name, rec.type, rec.level, rec.traits);
        return true;
    }

    /** Whom it hunts: its newest victim standing tonight, else the one nearest the core. */
    private static ServerPlayer prey(Host h, SiegeSave.NemesisRecord rec) {
        List<ServerPlayer> standing = h.defenders();
        for (int i = rec.victimIds.size() - 1; i >= 0; i--) {
            String id = rec.victimIds.get(i);
            for (ServerPlayer p : standing) {
                if (p.getUUID().toString().equals(id)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static void showBar(Host h, ServerBossEvent bar) {
        if (h.world() == null) {
            return;
        }
        Set<UUID> night = h.nightPlayers();
        for (ServerPlayer p : h.world().getServer().getPlayerList().getPlayers()) {
            if (night.contains(p.getUUID())) {
                bar.addPlayer(p);
            } else {
                bar.removePlayer(p);
            }
        }
    }

    /** Every second: the bars follow the life, the nemesis keeps after its prey. */
    public void tick(Host h, long now) {
        if (live.isEmpty() || h.world() == null || now % 10 != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Live>> it = live.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Live> entry = it.next();
            Live l = entry.getValue();
            Entity e = h.world().getEntity(entry.getKey());
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                l.bar.removeAllPlayers();
                it.remove();
                continue;
            }
            l.bar.setProgress(Math.max(0f, Math.min(1f, mob.getHealth() / Math.max(1f, mob.getMaxHealth()))));
            if (now % 20 == 0) {
                showBar(h, l.bar);
                ServerPlayer prey = prey(h, l.rec);
                if (prey != null && mob.getTarget() != prey && mob.distanceToSqr(prey) < 48 * 48
                        && SiegeManager.mayTarget(mob, prey)) {
                    mob.setTarget(prey);
                }
                h.world().sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 0.3, mob.getZ(), 3, 0.25, 0.1, 0.25, 0.0);
            }
        }
    }

    // ------------------------------------------------------------------ a defender falls

    /** A defender of the night died: was it a monster? Then it flees, and is (or rises as) a nemesis. */
    public void onDefenderKilled(Host h, ServerPlayer victim, DamageSource source) {
        SiegeConfig cfg = h.config();
        if (!cfg.nemesisEnabled || source == null || h.world() == null) {
            return;
        }
        Entity killer = source.getEntity();
        if (!(killer instanceof Mob mob) || !mob.isAlive() || mob.level() != h.world()) {
            return;
        }
        Live known = live.get(mob.getUUID());
        if (known != null) {
            promote(h, known, victim, mob);
            return;
        }
        if (!h.isNightMob(mob.getUUID()) || !mayBecome(mob)) {
            return;
        }
        int lurking = 0;
        for (SiegeSave.NemesisRecord rec : records(h.save(), h.testNight())) {
            if (rec.lurking()) {
                lurking++;
            }
        }
        if (lurking >= cfg.nemesisMax) {
            return;   // the lurking ones are enough: no new one until one falls
        }
        SiegeSave.NemesisRecord rec = new SiegeSave.NemesisRecord();
        SiegeSave save = h.save();
        save.nemesisCounter++;
        rec.id = "n" + save.nemesisCounter;
        Set<String> taken = new HashSet<>();
        for (SiegeSave.NemesisRecord other : save.nemeses) {
            taken.add(other.name);
        }
        rec.name = NemesisLore.name(h.random(), taken);
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        rec.type = type == null ? "minecraft:zombie" : type.toString();
        rec.level = 1;
        Trait trait = NemesisLore.newTrait(rec.traits, h.random());
        if (trait != null) {
            rec.traits.add(trait.id());
        }
        rec.victims.add(victim.getScoreboardName());
        rec.victimIds.add(victim.getUUID().toString());
        rec.foeClass = h.classOf(victim.getUUID());
        rec.kills = 1;
        rec.nightsAway = 0;
        rec.test = h.testNight();
        save.nemeses.add(rec);
        made++;
        flee(h, mob);
        h.broadcast(Component.translatable("nightsiege.nemesis.made", Component.translatable(mob.getType().getDescriptionId()),
                victim.getScoreboardName(), rec.name).withStyle(ChatFormatting.DARK_RED));
        NightSiegeMod.LOGGER.info("[Night Siege] Nemesis entstanden: {} ({}) nach {} ({})", rec.name, rec.type,
                victim.getScoreboardName(), rec.traits);
        h.persist();
    }

    /** It killed again: one rank up, one trait more, and off into the smoke once more. */
    private void promote(Host h, Live l, ServerPlayer victim, Mob mob) {
        SiegeSave.NemesisRecord rec = l.rec;
        int before = rec.level;
        rec.level = Math.min(h.config().nemesisMaxLevel, rec.level + 1);
        Trait trait = NemesisLore.newTrait(rec.traits, h.random());
        if (trait != null) {
            rec.traits.add(trait.id());
        }
        rec.kills++;
        if (!rec.victimIds.contains(victim.getUUID().toString())) {
            rec.victims.add(victim.getScoreboardName());
            rec.victimIds.add(victim.getUUID().toString());
        }
        promoted++;
        l.bar.removeAllPlayers();
        live.remove(mob.getUUID());
        flee(h, mob);
        h.broadcast(Component.translatable(rec.level > before ? "nightsiege.nemesis.promoted" : "nightsiege.nemesis.again",
                rec.name, victim.getScoreboardName(), fullName(rec),
                trait == null ? Component.literal("-") : Component.translatable(trait.translationKey()))
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        h.persist();
    }

    /** No boss, no machine crew, no creeper (it falls in its own blast), no vex. */
    static boolean mayBecome(Mob mob) {
        if (mob instanceof Creeper || mob instanceof net.minecraft.world.entity.monster.Vex) {
            return false;
        }
        Set<String> tags = mob.entityTags();
        return !BossService.isBossOrPart(mob) && !tags.contains(SiegeMachines.TAG_CREW) && !tags.contains(SiegeMachines.TAG_MACHINE)
                && !Companions.isCompanion(mob) && !SiegeCamp.isCampMob(mob);
    }

    /** Off in a cloud of smoke, with a laugh. */
    private static void flee(Host h, Mob mob) {
        ServerLevel level = h.world();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 1.0, mob.getZ(), 40, 0.5, 0.9, 0.5, 0.03);
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.8, mob.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.0f, 0.6f);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.5f);
        h.forget(mob.getUUID());
        mob.discard();
    }

    // ------------------------------------------------------------------ a nemesis falls

    /** A monster died: if it was a nemesis it is slain for good, and the one who did it gets its trophy. */
    public boolean onDeath(Host h, Mob mob, DamageSource source) {
        Live l = live.remove(mob.getUUID());
        if (l == null) {
            return false;
        }
        l.bar.removeAllPlayers();
        SiegeSave.NemesisRecord rec = l.rec;
        ServerPlayer hero = source != null && source.getEntity() instanceof ServerPlayer p ? p : null;
        if (hero == null) {
            double best = 32.0 * 32.0;
            for (ServerPlayer p : h.defenders()) {
                double d = p.distanceToSqr(mob);
                if (d < best) {
                    best = d;
                    hero = p;
                }
            }
        }
        rec.status = STATUS_SLAIN;
        rec.slainBy = hero == null ? "" : hero.getScoreboardName();
        rec.slainAt = System.currentTimeMillis();
        slain++;
        if (hero != null) {
            hero.getInventory().placeItemBackInInventory(trophy(rec));
            hero.level().playSound(null, hero.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        ServerLevel level = h.world();
        if (level != null) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mob.getX(), mob.getY() + 1.0, mob.getZ(), 40, 0.5, 0.8, 0.5, 0.25);
        }
        String victims = String.join(", ", rec.victims);
        h.broadcast(Component.translatable(hero == null ? "nightsiege.nemesis.slain" : "nightsiege.nemesis.slain_by",
                fullName(rec), hero == null ? "" : hero.getScoreboardName(), victims).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        NightSiegeMod.LOGGER.info("[Night Siege] Nemesis erschlagen: {} von {}", rec.name, rec.slainBy);
        pruneSlain(h.save(), rec.test);
        h.persist();
        return true;
    }

    private static void pruneSlain(SiegeSave save, boolean test) {
        List<SiegeSave.NemesisRecord> dead = new ArrayList<>();
        for (SiegeSave.NemesisRecord rec : save.nemeses) {
            if (!rec.lurking() && rec.test == test) {
                dead.add(rec);
            }
        }
        dead.sort((a, b) -> Long.compare(a.slainAt, b.slainAt));
        while (dead.size() > SLAIN_KEPT) {
            save.nemeses.remove(dead.remove(0));
        }
    }

    /** The trophy: the head of a monster that has one, otherwise a plaque with its skull. */
    public static ItemStack trophy(SiegeSave.NemesisRecord rec) {
        Item head = headOf(rec.type);
        ItemStack stack = new ItemStack(head == null ? Items.PAPER : head);
        if (head == null) {
            stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("nightsiege", "trophy/nemesis"));
        }
        stack.set(DataComponents.ITEM_NAME, Component.translatable("nightsiege.nemesis.trophy", fullName(rec)).withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.RARITY, Rarity.EPIC);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("nightsiege.nemesis.trophy_level", rec.level, rec.kills).withStyle(ChatFormatting.GRAY));
        lore.add(Component.translatable("nightsiege.nemesis.trophy_victims", String.join(", ", rec.victims)).withStyle(ChatFormatting.DARK_RED));
        if (!rec.slainBy.isEmpty()) {
            lore.add(Component.translatable("nightsiege.nemesis.trophy_slain", rec.slainBy).withStyle(ChatFormatting.GOLD));
        }
        if (rec.test) {
            lore.add(Component.translatable("nightsiege.nemesis.trophy_test").withStyle(ChatFormatting.DARK_GRAY));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("nemesis_trophy", rec.id));
        return stack;
    }

    private static Item headOf(String type) {
        String t = type == null ? "" : type.replace("minecraft:", "");
        return switch (t) {
            case "zombie", "husk", "drowned", "zombie_villager" -> Items.ZOMBIE_HEAD;
            case "skeleton", "stray", "bogged", "parched" -> Items.SKELETON_SKULL;
            case "wither_skeleton" -> Items.WITHER_SKELETON_SKULL;
            case "piglin", "piglin_brute", "zombified_piglin" -> Items.PIGLIN_HEAD;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ its blows and the blows it takes

    private static Live liveOf(Entity e) {
        Nemesis n = current;
        return n == null || e == null || n.live.isEmpty() ? null : n.live.get(e.getUUID());
    }

    /** Its blows against the defenders (SiegeManager.monsterDamageFactor). */
    public static float damageFactor(Entity attacker) {
        Live l = liveOf(attacker);
        Nemesis n = current;
        if (l == null || n.host == null) {
            return 1f;
        }
        return (float) NemesisLore.damageFactor(n.host.config(), l.rec.level, l.rec.traits);
    }

    /** The blows it takes: armour against shots, fire does nothing, its first victim's class less. */
    public static float hitFactor(LivingEntity victim, DamageSource source) {
        Live l = liveOf(victim);
        if (l == null || source == null) {
            return 1f;
        }
        float f = 1f;
        if (NemesisLore.has(l.rec.traits, Trait.ARMORED) && source.is(DamageTypeTags.IS_PROJECTILE)) {
            f *= (float) NemesisLore.ARMORED_SHOTS;
        }
        if (NemesisLore.has(l.rec.traits, Trait.FIREPROOF) && source.is(DamageTypeTags.IS_FIRE)) {
            return 0f;
        }
        Nemesis n = current;
        if (NemesisLore.has(l.rec.traits, Trait.CLASS_FOE) && n.host != null && source.getEntity() instanceof ServerPlayer p
                && !l.rec.foeClass.isEmpty() && l.rec.foeClass.equals(n.host.classOf(p.getUUID()))) {
            f *= (float) NemesisLore.CLASS_FOE_SHARE;
        }
        return f;
    }

    /** The blood drinker heals from what it deals. */
    public static void dealt(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0 || source == null || !(victim instanceof ServerPlayer)) {
            return;
        }
        Live l = liveOf(source.getEntity());
        if (l != null && NemesisLore.has(l.rec.traits, Trait.LEECH) && source.getEntity() instanceof LivingEntity self && self.isAlive()) {
            self.heal((float) (amount * NemesisLore.LEECH_SHARE));
        }
    }

    public static boolean isNemesis(Entity e) {
        return e != null && e.entityTags().contains(TAG);
    }

    // ------------------------------------------------------------------ names and records

    /** "Moderklaue der Schlächter". */
    public static MutableComponent fullName(SiegeSave.NemesisRecord rec) {
        return Component.literal(rec.name + " ").append(Component.translatable(NemesisLore.titleKey(rec.level)));
    }

    private static Component barName(SiegeSave.NemesisRecord rec) {
        return Component.literal("☠ ").append(fullName(rec)).append(Component.literal(" · "))
                .append(Component.translatable("nightsiege.nemesis.bar_level", rec.level));
    }

    /** The records of real nights, or of test nights. */
    public static List<SiegeSave.NemesisRecord> records(SiegeSave save, boolean test) {
        List<SiegeSave.NemesisRecord> out = new ArrayList<>();
        if (save == null || save.nemeses == null) {
            return out;
        }
        for (SiegeSave.NemesisRecord rec : save.nemeses) {
            if (rec != null && rec.test == test) {
                out.add(rec);
            }
        }
        return out;
    }

    /** True while this record's monster is on the field. */
    public boolean here(SiegeSave.NemesisRecord rec) {
        return onField(rec);
    }

    /** The nemeses on the field (for the tests). */
    public List<Mob> onFieldMobs() {
        List<Mob> out = new ArrayList<>();
        if (host == null || host.world() == null) {
            return out;
        }
        for (UUID id : live.keySet()) {
            if (host.world().getEntity(id) instanceof Mob m) {
                out.add(m);
            }
        }
        return out;
    }

    /** For the command and the tests: a nemesis made by hand (as if this monster type had slain the player). */
    public SiegeSave.NemesisRecord make(Host h, String type, ServerPlayer victim, boolean test, int level) {
        SiegeSave save = h.save();
        SiegeSave.NemesisRecord rec = new SiegeSave.NemesisRecord();
        save.nemesisCounter++;
        rec.id = "n" + save.nemesisCounter;
        Set<String> taken = new HashSet<>();
        for (SiegeSave.NemesisRecord other : save.nemeses) {
            taken.add(other.name);
        }
        rec.name = NemesisLore.name(h.random(), taken);
        rec.type = type;
        rec.level = Math.max(1, Math.min(h.config().nemesisMaxLevel, level));
        for (int i = 0; i < rec.level; i++) {
            Trait trait = NemesisLore.newTrait(rec.traits, h.random());
            if (trait != null) {
                rec.traits.add(trait.id());
            }
        }
        if (victim != null) {
            rec.victims.add(victim.getScoreboardName());
            rec.victimIds.add(victim.getUUID().toString());
            rec.foeClass = h.classOf(victim.getUUID());
        }
        rec.kills = Math.max(1, rec.level);
        rec.nightsAway = 1;
        rec.test = test;
        save.nemeses.add(rec);
        h.persist();
        return rec;
    }

    /** For the command: every nemesis of this kind is forgotten. */
    public static int clear(SiegeSave save, boolean test) {
        int before = save.nemeses.size();
        save.nemeses.removeIf(rec -> rec == null || rec.test == test);
        return before - save.nemeses.size();
    }
}
