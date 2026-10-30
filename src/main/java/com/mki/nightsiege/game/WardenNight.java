package com.mki.nightsiege.game;

import com.mki.nightsiege.core.ExpeditionKind;
import com.mki.nightsiege.core.NightFragment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The dungeon wardens in the nights (Simon, 29 October 2026): in a normal night (no boss night)
 * one of the eight dungeon champions comes with a chance of 20 % at the end of the night, after
 * the last wave, in the look of his dungeon. From then on he is the night's goal: it is won when
 * he falls and lost when he still stands at dawn. His fall gives crates as good as a fragment
 * night's and two ability points to everyone still standing.
 */
public final class WardenNight {

    /** Tag of a dungeon champion that wears its dungeon's look. */
    public static final String TAG = "nightsiege_warden";

    private ExpeditionKind kind;
    private UUID wardenId;
    private int networkId = -1;
    private boolean spawned;
    private boolean fallen;
    private UUID killer;
    private ServerBossEvent bar;
    private double maxHealth = 1.0;

    /** One of the eight with this chance, else null. */
    public static ExpeditionKind roll(Random random, double chance) {
        if (chance <= 0.0 || random.nextDouble() >= chance) {
            return null;
        }
        ExpeditionKind[] all = ExpeditionKind.values();
        return all[random.nextInt(all.length)];
    }

    public void plan(ExpeditionKind kind) {
        this.kind = kind;
    }

    public boolean planned() {
        return kind != null;
    }

    public ExpeditionKind kind() {
        return kind;
    }

    public boolean spawned() {
        return spawned;
    }

    public boolean fallen() {
        return fallen;
    }

    public UUID killer() {
        return killer;
    }

    /** The warden standing now, or null. */
    public Mob warden(ServerLevel level) {
        Entity e = wardenId == null || level == null ? null : level.getEntity(wardenId);
        return e instanceof Mob mob && mob.isAlive() ? mob : null;
    }

    /** His name in the colour of his fragment. */
    public static MutableComponent name(ExpeditionKind kind) {
        return Component.translatable(kind.championKey())
                .withStyle(s -> s.withColor(TextColor.fromRgb(kind.fragment().color() & 0xFFFFFF)));
    }

    public static Component epithet(ExpeditionKind kind) {
        return Component.translatable("nightsiege.expedition." + kind.id() + ".epithet");
    }

    /**
     * Health, size and look of a dungeon champion, in a dungeon or in a night: his armour comes off
     * (the look is his armour; a little real armour makes up for it) and every client draws him in
     * the look of his dungeon.
     */
    public static void dress(Mob mob, ExpeditionKind kind, double health) {
        // A grown warden alone: no baby (its texture has no look), no jockey on him or under him.
        mob.setBaby(false);
        mob.ejectPassengers();
        mob.stopRiding();
        BossService.set(mob, Attributes.MAX_HEALTH, health);
        mob.setHealth((float) health);
        BossService.set(mob, Attributes.SCALE, 1.35);
        BossService.set(mob, Attributes.KNOCKBACK_RESISTANCE, 0.8);
        AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) {
            range.setBaseValue(Math.max(range.getBaseValue(), 64.0));
        }
        boolean hadArmour = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!mob.getItemBySlot(slot).isEmpty()) {
                hadArmour = true;
                mob.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        if (hadArmour) {
            AttributeInstance armour = mob.getAttribute(Attributes.ARMOR);
            if (armour != null) {
                armour.setBaseValue(armour.getBaseValue() + 4.0);
            }
            // The clients saw the armour when he was added; the change alone would not reach them
            // (the game compares with the last tick, and he has not ticked yet): tell them.
            if (mob.level() instanceof ServerLevel level) {
                List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> bare = new ArrayList<>();
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    bare.add(com.mojang.datafixers.util.Pair.of(slot, ItemStack.EMPTY));
                }
                level.getChunkSource().sendToTrackingPlayers(mob,
                        new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(mob.getId(), bare));
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0f);
        }
        // Like the bosses: no sunburn when a night runs into the morning.
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        mob.clearFire();
        mob.setCustomName(name(kind));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        mob.addTag(TAG + "_" + kind.id());
        BossService.foeLook(mob, "champion_" + kind.id());
    }

    /** The kind whose look a champion wears (from its tag), or null. */
    public static ExpeditionKind kindOf(Entity entity) {
        if (entity == null || !entity.entityTags().contains(TAG)) {
            return null;
        }
        for (ExpeditionKind k : ExpeditionKind.values()) {
            if (entity.entityTags().contains(TAG + "_" + k.id())) {
                return k;
            }
        }
        return null;
    }

    /** The warden comes: where the monsters come from, with his bar, his title and his look. */
    public boolean spawn(BossService.Host host) {
        if (kind == null || spawned) {
            return false;
        }
        ServerLevel level = host.world();
        BlockPos spot = host.spawnSpot();
        if (level == null || spot == null) {
            return false;
        }
        Mob mob = host.spawnAdd(kind.champion(), spot);
        if (mob == null) {
            mob = host.spawnAdd("minecraft:wither_skeleton", spot);
        }
        if (mob == null) {
            return false;   // the field is full: the next second
        }
        int defenders = Math.max(1, host.defenders().size());
        SiegeManager m = SiegeManager.get();
        double perPlayer = host.config().perPlayer(m != null && m.inPlaytest(), host.config().bossHealthPerExtraPlayer);
        double health = mob.getMaxHealth() * ExpeditionKind.championLife(host.level()) * kind.championLifeShare() * (1.0 + perPlayer * (defenders - 1));
        dress(mob, kind, health);
        wardenId = mob.getUUID();
        networkId = mob.getId();
        maxHealth = health;
        spawned = true;
        ServerPlayer target = BossService.nearest(host.defenders(), mob.position(), 96.0);
        if (target != null) {
            mob.setTarget(target);
        }
        bar = new ServerBossEvent(com.mki.nightsiege.core.NightBarIds.make(com.mki.nightsiege.core.NightBarIds.Kind.BOSS), name(kind),
                color(kind.fragment()), BossEvent.BossBarOverlay.NOTCHED_10);   // the boss bars' look (design update)
        bar.setDarkenScreen(true);
        bar.setProgress(1f);
        for (ServerPlayer p : host.watchers()) {
            bar.addPlayer(p);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(name(kind).withStyle(ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(epithet(kind).copy().withStyle(ChatFormatting.GRAY)));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.15f);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mob.getX(), mob.getY() + 1.0, mob.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1.0, mob.getZ(), 30, 1.0, 1.2, 1.0, 0.04);
        return true;
    }

    /** Every tick once he is there: the bar, and back towards the base when he wandered off. */
    public void tick(BossService.Host host, long now) {
        if (!spawned || fallen) {
            return;
        }
        ServerLevel level = host.world();
        Mob mob = warden(level);
        if (mob == null) {
            return;
        }
        if (now % 20 == 0) {
            BlockPos anchor = host.anchor();
            if (mob.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5) > 64.0 * 64.0) {
                BlockPos spot = host.spawnSpot();
                if (spot != null) {
                    mob.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                }
            }
            if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                ServerPlayer target = BossService.nearest(host.defenders(), mob.position(), 96.0);
                if (target != null) {
                    mob.setTarget(target);
                }
            }
        }
        if (bar != null) {
            bar.setProgress((float) Math.max(0.0, Math.min(1.0, mob.getHealth() / maxHealth)));
            if (now % 20 == 0) {
                List<ServerPlayer> watchers = host.watchers();
                for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
                    if (!watchers.contains(p)) {
                        bar.removePlayer(p);
                    }
                }
                for (ServerPlayer p : watchers) {
                    bar.addPlayer(p);
                }
            }
        }
    }

    /** A monster died: true when it was the warden (the night is then won). */
    public boolean onDeath(Entity entity, DamageSource source) {
        if (!spawned || fallen || !(entity instanceof LivingEntity dead) || !dead.getUUID().equals(wardenId)) {
            return false;
        }
        fallen = true;
        killer = source != null && source.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        ServerLevel level = (ServerLevel) dead.level();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, dead.getX(), dead.getY() + 1.0, dead.getZ(), 1, 0.3, 0.3, 0.3, 0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, dead.getX(), dead.getY() + 1.5, dead.getZ(), 60, 0.8, 1.2, 0.8, 0.35);
        level.playSound(null, dead.getX(), dead.getY(), dead.getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.2f, 0.7f);
        BossService.dropFoeLook(level.getServer(), networkId);
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        return true;
    }

    /** End of the night: bar, look and (if still standing) the warden himself go. */
    public void reset(MinecraftServer server) {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        if (server != null && wardenId != null) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(wardenId);
                if (e != null) {
                    e.discard();
                }
            }
        }
        if (networkId >= 0) {
            BossService.dropFoeLook(server, networkId);
        }
        kind = null;
        wardenId = null;
        networkId = -1;
        spawned = false;
        fallen = false;
        killer = null;
        maxHealth = 1.0;
    }

    private static BossEvent.BossBarColor color(NightFragment fragment) {
        return switch (fragment) {
            case BLOOD, EMBER -> BossEvent.BossBarColor.RED;
            case MIST -> BossEvent.BossBarColor.WHITE;
            case FROST -> BossEvent.BossBarColor.BLUE;
            case STORM -> BossEvent.BossBarColor.YELLOW;
            case SHADOW -> BossEvent.BossBarColor.PURPLE;
        };
    }
}
