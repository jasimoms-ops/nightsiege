package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PerkKind;
import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The main core's perks. Fragments from dungeons are set into the core and
 * level up one perk each, shared by everyone. A perk works on every player who
 * stands within {@link SiegeConfig#perkRadius} of a main core.
 */
public final class PerkService {

    private static final Identifier VITALITY_ID = ModContent.id("perk_vitality");
    private static final Identifier BULWARK_ID = ModContent.id("perk_bulwark");
    private static final Identifier FLAME_ID = ModContent.id("perk_flame");

    private final Supplier<SiegeSave> save;
    private final Supplier<SiegeConfig> config;
    private final Random random = new Random();
    /** Who is in range of a core right now, refreshed every two seconds. */
    private final Map<java.util.UUID, Boolean> inRange = new HashMap<>();

    public PerkService(Supplier<SiegeSave> save, Supplier<SiegeConfig> config) {
        this.save = save;
        this.config = config;
    }

    public int fragments(PerkKind kind) {
        Map<String, Integer> map = save.get().perkFragments;
        return map == null ? 0 : Math.max(0, map.getOrDefault(kind.id(), 0));
    }

    public int stage(PerkKind kind) {
        return PerkKind.stageFor(fragments(kind));
    }

    public Map<PerkKind, Integer> stages() {
        Map<PerkKind, Integer> out = new EnumMap<>(PerkKind.class);
        for (PerkKind kind : PerkKind.values()) {
            out.put(kind, stage(kind));
        }
        return out;
    }

    /**
     * A fragment in hand on the main core: it is set in. Sneaking sets in the
     * whole stack. Returns true when the click is handled.
     */
    public boolean insert(ServerPlayer player, ItemStack held, BlockPos core) {
        PerkKind kind = held.isEmpty() ? null : DungeonContent.fragmentKind(held.getItem());
        if (kind == null) {
            return false;
        }
        int have = fragments(kind);
        int cap = PerkKind.fragmentsFor(PerkKind.MAX_STAGE);
        if (have >= cap) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(
                    "nightsiege.perk.full", perkName(kind)).withStyle(ChatFormatting.YELLOW)));
            return true;
        }
        int amount = player.isShiftKeyDown() ? Math.min(held.getCount(), cap - have) : 1;
        int before = PerkKind.stageFor(have);
        if (save.get().perkFragments == null) {
            save.get().perkFragments = new HashMap<>();
        }
        save.get().perkFragments.put(kind.id(), have + amount);
        if (!player.getAbilities().instabuild) {
            held.shrink(amount);
        }
        int after = PerkKind.stageFor(have + amount);
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.ENCHANT, core.getX() + 0.5, core.getY() + 1.2, core.getZ() + 0.5,
                30, 0.4, 0.5, 0.4, 0.6);
        if (after > before) {
            level.playSound(null, core, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.0f);
            Component text = Component.translatable("nightsiege.perk.stage_up", perkName(kind), roman(after))
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(text);
            }
        } else {
            level.playSound(null, core, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
            int next = PerkKind.fragmentsFor(after + 1) - (have + amount);
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(
                    "nightsiege.perk.inserted", perkName(kind), roman(after), next)));
        }
        return true;
    }

    /** Every two seconds: perks on for everyone near a core, off for everyone else. */
    public void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 40 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean near = player.isAlive() && !player.isSpectator() && nearCore(player);
            inRange.put(player.getUUID(), near);
            apply(player, near);
        }
    }

    private boolean nearCore(ServerPlayer player) {
        if (save.get().cores == null) {
            return false;
        }
        String dim = SiegeManager.dimensionName(player.level());
        double r = config.get().perkRadius;
        for (CoreRecord core : save.get().cores) {
            if (dim.equals(core.dimension)) {
                double dx = player.getX() - (core.x + 0.5);
                double dy = player.getY() - (core.y + 0.5);
                double dz = player.getZ() - (core.z + 0.5);
                if (dx * dx + dy * dy + dz * dz <= r * r) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean inRange(ServerPlayer player) {
        return nearCore(player);
    }

    private boolean active(ServerPlayer player) {
        return inRange.getOrDefault(player.getUUID(), false);
    }

    private void apply(ServerPlayer player, boolean near) {
        int vitality = near ? stage(PerkKind.VITALITY) : 0;
        int bulwark = near ? stage(PerkKind.BULWARK) : 0;
        int flame = near ? stage(PerkKind.FLAME) : 0;
        int tides = near ? stage(PerkKind.TIDES) : 0;
        modifier(player, Attributes.MAX_HEALTH, VITALITY_ID, 4.0 * vitality, AttributeModifier.Operation.ADD_VALUE);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        modifier(player, Attributes.ARMOR, BULWARK_ID, bulwark, AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.ATTACK_DAMAGE, FLAME_ID, 0.1 * flame, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (tides > 0) {
            effect(player, MobEffects.REGENERATION, tides >= 3 ? 1 : 0);
        }
        if (flame >= 3) {
            effect(player, MobEffects.FIRE_RESISTANCE, 0);
        }
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount,
                                 AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount <= 0) {
            instance.removeModifier(id);
        } else {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    /** Tops an effect up before it runs out, so its own timing stays natural. */
    private static void effect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() < 60) {
            player.addEffect(new MobEffectInstance(effect, 160, amplifier, true, false, true));
        }
    }

    /** Bollwerk: part of the damage a player takes near a core is given back right away. */
    public void afterDamage(LivingEntity victim, float taken) {
        if (!(victim instanceof ServerPlayer player) || taken <= 0 || !player.isAlive() || !active(player)) {
            return;
        }
        int bulwark = stage(PerkKind.BULWARK);
        if (bulwark > 0) {
            player.heal(taken * 0.05f * bulwark);
        }
    }

    /** Flamme: extra share of arrow damage. */
    public double arrowBonus(ServerPlayer player) {
        return active(player) ? 0.1 * stage(PerkKind.FLAME) : 0.0;
    }

    /** Wachsamkeit: shorter ultimate cooldown. */
    public double cooldownScale(ServerPlayer player) {
        return active(player) ? 1.0 - 0.1 * stage(PerkKind.VIGILANCE) : 1.0;
    }

    /** Wachsamkeit: experience for a kill, with 10 % more per stage (the remainder rolled). */
    public int experience(ServerPlayer player, int amount) {
        int stage = active(player) ? stage(PerkKind.VIGILANCE) : 0;
        if (stage <= 0 || amount <= 0) {
            return amount;
        }
        double total = amount * (1.0 + 0.1 * stage);
        int whole = (int) Math.floor(total);
        return whole + (random.nextDouble() < total - whole ? 1 : 0);
    }

    public void forget(ServerPlayer player) {
        inRange.remove(player.getUUID());
    }

    public static Component perkName(PerkKind kind) {
        return Component.translatable("nightsiege.perk." + kind.id());
    }

    public static String roman(int stage) {
        return switch (stage) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "0";
        };
    }
}
