package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Rüstungsbruch (Simon's idea, balance update 26 October 2026): a zombie's hit or a
 * creeper's blast lowers the player's armour for a few seconds – since 31.10. only in a night or a
 * fight and only from the mod's own monsters.
 *
 * <p>A real status effect, so the player sees its icon and time. Each amplifier step takes
 * 10 % of the armour, so the config share (default 30 %) becomes the amplifier. Another
 * hit only starts the time again; it never stacks.</p>
 */
public final class ArmorBreak {

    /** Each amplifier step: −10 % armour (vanilla scales a modifier by amplifier + 1). */
    private static final double STEP = -0.1;
    public static Holder<MobEffect> EFFECT;

    private ArmorBreak() {
    }

    public static void register() {
        MobEffect effect = new MobEffect(MobEffectCategory.HARMFUL, 0x8A6A4A) {
        }.addAttributeModifier(Attributes.ARMOR, ModContent.id("armor_break"), STEP,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ModContent.id("armor_break"), effect);
    }

    /** Called after a player took damage; breaks his armour when a zombie or a creeper did it. */
    public static void afterHit(ServerPlayer player, DamageSource source, float taken, SiegeConfig config) {
        if (EFFECT == null || config == null || taken <= 0 || config.armorBreakShare <= 0 || config.armorBreakSeconds <= 0) {
            return;
        }
        var attacker = source.getEntity();
        boolean zombie = attacker instanceof Zombie && source.getDirectEntity() == attacker;
        boolean creeper = attacker instanceof Creeper && source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
        if (!(zombie || creeper) || BossService.isBoss(attacker)) {
            return;
        }
        // Survival safety 2 (Simon 31.10.): only in a fight (a defender of the night, a fighter in a dungeon
        // or at the war camp) and only from Night Siege's own monsters; a zombie of everyday play leaves the
        // armour alone, also in the Nether or for a player who never signed up.
        SiegeManager m = SiegeManager.get();
        if (m == null || !m.isDefending(player) || !OwnMobs.is(attacker)) {
            return;
        }
        int amplifier = Math.max(0, (int) Math.round(config.armorBreakShare * 10.0) - 1);
        int ticks = (int) Math.round(config.armorBreakSeconds * 20.0);
        player.addEffect(new MobEffectInstance(EFFECT, ticks, amplifier, false, true, true));
    }
}
