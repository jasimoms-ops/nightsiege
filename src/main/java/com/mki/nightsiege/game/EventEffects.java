package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.mixin.CreeperAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * What the events do in the world, beyond the sky: who attacks, the
 * lightning, the rifts and the cold. Nothing here ever changes a block.
 */
public final class EventEffects {

    private static final String[] NETHER_MOBS = {
            "minecraft:blaze", "minecraft:wither_skeleton", "minecraft:piglin_brute", "minecraft:magma_cube",
            "minecraft:zombified_piglin", "minecraft:hoglin", "minecraft:blaze", "minecraft:wither_skeleton"};
    private static final String[] SNEAKERS = {"minecraft:spider", "minecraft:creeper", "minecraft:spider",
            "minecraft:creeper", "minecraft:cave_spider"};

    private final Random random = new Random();
    private final List<BlockPos> rifts = new ArrayList<>();
    private final Map<UUID, String> sentToClient = new HashMap<>();
    private int nextStrike;
    private int nextFarBolt;

    /** Where a rift attacker should appear, set by {@link #substitute}. */
    private BlockPos lastRift;

    // ------------------------------------------------------------------
    // start and end
    // ------------------------------------------------------------------

    public void begin(MinecraftServer server, ServerLevel world, BlockPos anchor, SiegeEvent event, SiegeConfig cfg) {
        String look = event.atmosphere();
        if (!look.isEmpty()) {
            EventAtmosphere.setServer(SiegeManager.dimensionName(world), look, EventAtmosphere.fadeTicks(look));
        }
        rifts.clear();
        nextStrike = 40;
        nextFarBolt = 20;
        if (event == SiegeEvent.THUNDER) {
            server.setWeatherParameters(0, 20 * 60 * 15, true, true);
        }
        if (event == SiegeEvent.NETHER_RIFT) {
            for (int i = 0; i < cfg.netherRiftCount * 4 && rifts.size() < cfg.netherRiftCount; i++) {
                BlockPos spot = MobFactory.findSpawnSpot(world, anchor, cfg, random, 0.0);
                if (spot != null) {
                    rifts.add(spot);
                }
            }
            for (BlockPos rift : rifts) {
                world.playSound(null, rift, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 0.6f, 0.6f);
            }
        }
        syncClients(server);
    }

    public void end(MinecraftServer server, ServerLevel world, SiegeEvent event) {
        if (world != null) {
            EventAtmosphere.setServer(SiegeManager.dimensionName(world), "", 200);
        }
        if (event == SiegeEvent.THUNDER && server != null) {
            server.setWeatherParameters(12000, 0, false, false);
        }
        rifts.clear();
        if (server != null) {
            syncClients(server);
        }
    }

    // ------------------------------------------------------------------
    // attackers
    // ------------------------------------------------------------------

    /** The mob that really comes instead of the picked one, or the picked one itself. */
    public String substitute(SiegeEvent event, String picked, SiegeConfig cfg) {
        lastRift = null;
        return switch (event) {
            case ECLIPSE -> random.nextDouble() < cfg.eclipsePhantomShare ? "minecraft:phantom" : picked;
            case FOG -> random.nextDouble() < cfg.fogSneakerShare ? SNEAKERS[random.nextInt(SNEAKERS.length)] : picked;
            case FROST -> picked.startsWith("minecraft:skeleton") ? picked.replace("minecraft:skeleton", "minecraft:stray")
                    : random.nextDouble() < 0.15 ? "minecraft:stray" : picked;
            case NETHER_RIFT -> {
                if (!rifts.isEmpty() && random.nextDouble() < cfg.netherRiftShare) {
                    lastRift = rifts.get(random.nextInt(rifts.size()));
                    yield NETHER_MOBS[random.nextInt(NETHER_MOBS.length)];
                }
                yield picked;
            }
            default -> picked;
        };
    }

    /** The rift the last substituted mob should come out of, if any. */
    public BlockPos riftForLast() {
        return lastRift;
    }

    /** Last touches on a fresh attacker. */
    public void onSpawned(SiegeEvent event, Mob mob, ServerLevel world) {
        if (event == SiegeEvent.FOG) {
            mob.setSilent(true);
        }
        if (event == SiegeEvent.NETHER_RIFT && lastRift != null) {
            world.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY(1.0), mob.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
        }
    }

    // ------------------------------------------------------------------
    // every tick
    // ------------------------------------------------------------------

    public void tick(MinecraftServer server, ServerLevel world, BlockPos anchor, SiegeEvent event,
                     Set<UUID> participants, Set<UUID> fallen, Set<UUID> liveMobs, SiegeConfig cfg, long now) {
        switch (event) {
            case THUNDER -> {
                tickThunder(world, liveMobs, cfg);
                tickFarLightning(world, anchor);
            }
            case NETHER_RIFT -> tickRifts(world, now);
            case FROST -> {
                if (now % 10 == 0) {
                    tickCold(server, world, participants, fallen);
                }
            }
            default -> {
            }
        }
    }

    private void tickThunder(ServerLevel world, Set<UUID> liveMobs, SiegeConfig cfg) {
        if (--nextStrike > 0 || liveMobs.isEmpty()) {
            return;
        }
        int base = cfg.thunderStrikeSeconds * 20;
        nextStrike = base + random.nextInt(base + 1);
        List<Mob> mobs = new ArrayList<>();
        for (UUID id : liveMobs) {
            if (world.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                mobs.add(mob);
            }
        }
        if (mobs.isEmpty()) {
            return;
        }
        Mob target = mobs.get(random.nextInt(mobs.size()));
        Entity bolt = lightning(world);
        if (bolt instanceof LightningBolt lightning) {
            lightning.setVisualOnly(true);
            lightning.snapTo(target.getX(), target.getY(), target.getZ());
            world.addFreshEntity(lightning);
        }
        float damage = (float) cfg.thunderStrikeDamage;
        for (Mob mob : mobs) {
            double d = mob.distanceToSqr(target);
            if (d > 9.0) {
                continue;
            }
            mob.hurtServer(world, world.damageSources().lightningBolt(), mob == target ? damage : damage / 2f);
            if (mob instanceof Creeper creeper && creeper.isAlive()) {
                creeper.getEntityData().set(CreeperAccessor.nightsiege$powered(), true);
            }
        }
    }

    /** Lightning in the distance all around the base, just for the storm's look and sound. */
    private void tickFarLightning(ServerLevel world, BlockPos anchor) {
        if (--nextFarBolt > 0 || anchor == null) {
            return;
        }
        nextFarBolt = 50 + random.nextInt(110);
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 40.0 + random.nextDouble() * 60.0;
        int x = anchor.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * distance);
        if (!world.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        Entity bolt = lightning(world);
        if (bolt instanceof LightningBolt lightning) {
            lightning.setVisualOnly(true);
            lightning.snapTo(x + 0.5, y, z + 0.5);
            world.addFreshEntity(lightning);
        }
    }

    private static Entity lightning(ServerLevel world) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("lightning_bolt"));
        return type == null ? null : type.create(world, EntitySpawnReason.EVENT);
    }

    private void tickRifts(ServerLevel world, long now) {
        if (now % 4 != 0) {
            return;
        }
        for (BlockPos rift : rifts) {
            double x = rift.getX() + 0.5;
            double y = rift.getY() + 1.4;
            double z = rift.getZ() + 0.5;
            world.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 10, 0.25, 1.2, 0.25, 0.02);
            world.sendParticles(ParticleTypes.PORTAL, x, y, z, 12, 0.3, 1.0, 0.3, 0.6);
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, rift.getY() + 0.2, z, 2, 0.4, 0.05, 0.4, 0.01);
            world.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.2, z, 1, 0.2, 0.3, 0.2, 0.01);
            if (now % 80 == 0) {
                world.playSound(null, rift, SoundEvents.PORTAL_AMBIENT, SoundSource.HOSTILE, 0.5f, 0.6f);
            }
        }
    }

    /** Out in the open the blizzard bites: slowness and frost on the screen, but no frost damage. */
    private void tickCold(MinecraftServer server, ServerLevel world, Set<UUID> participants, Set<UUID> fallen) {
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.level() != world || fallen.contains(id) || player.isCreative()
                    || player.isSpectator() || Drones.isDrone(player) || !player.canFreeze()) {
                continue;
            }
            BlockPos pos = player.blockPosition();
            boolean open = world.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY() + 1;
            if (!open) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false, true));
            int frozen = Math.min(player.getTicksRequiredToFreeze() - 40, 50);
            if (player.getTicksFrozen() < frozen) {
                player.setTicksFrozen(frozen);
            }
        }
    }

    // ------------------------------------------------------------------
    // clients
    // ------------------------------------------------------------------

    /** Tells every player which event their dimension shows, when it changed. */
    public void syncClients(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Players outside Night Siege keep their normal sky.
            String event = SiegeManager.get().isRegistered(player)
                    ? EventAtmosphere.serverEvent(SiegeManager.dimensionName(player.level())) : "";
            String sent = sentToClient.get(player.getUUID());
            if (event.equals(sent)) {
                continue;
            }
            if (!ServerPlayNetworking.canSend(player, CoreNetworking.EventSkyPayload.TYPE)) {
                continue;
            }
            sentToClient.put(player.getUUID(), event);
            ServerPlayNetworking.send(player, new CoreNetworking.EventSkyPayload(event,
                    event.isEmpty() ? 200 : EventAtmosphere.fadeTicks(event)));
        }
    }

    public void forget(UUID player) {
        sentToClient.remove(player);
    }
}
