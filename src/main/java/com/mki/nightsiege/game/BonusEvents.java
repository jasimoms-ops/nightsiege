package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Good things between the sieges: the aurora on a quiet night. (The trader caravan is gone,
 * Simon 31.10.: not needed any more.)
 */
public final class BonusEvents {

    private final Supplier<SiegeSave> save;
    private final Supplier<SiegeConfig> config;
    private final Random random = new Random();
    private long rolledAuroraDay = Long.MIN_VALUE;
    private boolean aurora;
    /** Started by command: lasts until a siege starts or the command ends it. */
    private boolean forcedAurora;
    private int auroraTicks;

    public BonusEvents(Supplier<SiegeSave> save, Supplier<SiegeConfig> config) {
        this.save = save;
        this.config = config;
    }

    /** Near a core (the aurora's reach, the radius of the old core perks). */
    private boolean nearCore(ServerPlayer player) {
        if (save.get().cores == null) {
            return false;
        }
        String dim = SiegeManager.dimensionName(player.level());
        double r = config.get().perkRadius;
        for (CoreRecord core : save.get().cores) {
            if (core != null && dim.equals(core.dimension)) {
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

    public boolean auroraActive() {
        return aurora;
    }

    /**
     * @param siegeActive a siege is running right now
     * @param siegeDue    the countdown is done, a siege comes next
     */
    public void tick(MinecraftServer server, boolean siegeActive, boolean siegeDue) {
        boolean quiet = !siegeActive && !siegeDue;
        ServerLevel overworld = server.overworld();
        long clock = overworld.getOverworldClockTime();
        long day = Math.floorDiv(clock, 24000L);
        int time = (int) Math.floorMod(clock, 24000L);

        if (aurora) {
            boolean over = forcedAurora ? siegeActive : !quiet || time < 13000 || time >= 23000;
            if (over) {
                endAurora(server);
            } else {
                tickAurora(server);
            }
        } else if (time >= 13000 && time < 13200 && rolledAuroraDay != day) {
            rolledAuroraDay = day;
            if (quiet && random.nextDouble() < config.get().auroraChance) {
                startAurora(server, false);
            }
        }

    }

    // ------------------------------------------------------------------
    // aurora
    // ------------------------------------------------------------------

    public void startAurora(MinecraftServer server, boolean forced) {
        aurora = true;
        forcedAurora = forced;
        auroraTicks = 0;
        ServerLevel overworld = server.overworld();
        EventAtmosphere.setServer(SiegeManager.dimensionName(overworld), EventAtmosphere.AURORA, 400);
        for (ServerPlayer player : overworld.players()) {
            if (!SiegeManager.get().isRegistered(player)) {
                continue;
            }
            player.sendSystemMessage(Component.translatable("nightsiege.bonus.aurora_start")
                    .withStyle(ChatFormatting.GREEN));
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.AMBIENT, 0.8f, 0.6f);
        }
    }

    public void endAurora(MinecraftServer server) {
        if (!aurora) {
            return;
        }
        aurora = false;
        ServerLevel overworld = server.overworld();
        String dim = SiegeManager.dimensionName(overworld);
        if (EventAtmosphere.AURORA.equals(EventAtmosphere.serverEvent(dim))) {
            EventAtmosphere.setServer(dim, "", 400);
        }
    }

    /** Near a core the aurora heals and brings a point of experience every minute. */
    private void tickAurora(MinecraftServer server) {
        auroraTicks++;
        boolean point = auroraTicks % (config.get().auroraPointSeconds * 20) == 0;
        if (auroraTicks % 40 != 0 && !point) {
            return;
        }
        for (ServerPlayer player : server.overworld().players()) {
            if (!SiegeManager.get().isRegistered(player)) {
                continue;
            }
            if (!player.isAlive() || player.isSpectator() || !nearCore(player)) {
                continue;
            }
            if (auroraTicks % 40 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false, true));
            }
            if (point && config.get().skillsEnabled) {
                save.get().skillsOf(player.getUUID().toString()).award(1);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                        Component.translatable("nightsiege.bonus.aurora_point").withStyle(ChatFormatting.GREEN)));
            }
        }
    }
}
