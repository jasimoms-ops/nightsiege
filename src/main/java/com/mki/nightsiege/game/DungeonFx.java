package com.mki.nightsiege.game;

import com.mki.nightsiege.core.ExpeditionKind;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * The dungeon reacts to the group (content update, Simon 29 October 2026: "mit Effekten
 * arbeiten, um es epischer zu machen"). Only vanilla sounds and particles; everything goes to
 * the players in the dungeon and its gatehouse, never to the whole world.
 *
 * <ul>
 *   <li>Atmosphere: fog and its colour, ambient particles (a look of {@link EventAtmosphere}
 *       sent to the player), now and then a sound of the place.</li>
 *   <li>Moments: the runes round the seal stone, the hall's title, the vault's sound when a hall
 *       is clear, the arena's gate slamming down, the music, the champion rising from his pit,
 *       phase 2 at half life, the bells of the win and the dust of the collapse.</li>
 * </ul>
 */
final class DungeonFx {

    private static final Random RANDOM = new Random();

    private DungeonFx() {
    }

    // ------------------------------------------------------------------ atmosphere

    /** The dungeon's fog and particles for one player (phase 2: closer, redder). */
    static void look(ServerPlayer player, ExpeditionKind kind, boolean phase2) {
        if (ServerPlayNetworking.canSend(player, CoreNetworking.EventSkyPayload.TYPE)) {
            ServerPlayNetworking.send(player, new CoreNetworking.EventSkyPayload(EventAtmosphere.dungeonLook(kind.id(), phase2), 60));
        }
    }

    /** Back to the sky of the world (the next sync sends it) and the music stops. */
    static void leave(ServerPlayer player, ExpeditionKind kind) {
        SiegeManager.get().events().forget(player.getUUID());
        Holder<SoundEvent> music = music(kind);
        if (music != null) {
            music.unwrapKey().ifPresent(key -> player.connection.send(new ClientboundStopSoundPacket(key.identifier(), SoundSource.RECORDS)));
        }
    }

    /** Now and then a sound of the place, close to each player. */
    static void ambient(ServerLevel level, Collection<ServerPlayer> players, ExpeditionKind kind, long now) {
        if (kind.layout() == ExpeditionKind.Layout.FORGE && now % 40 == 0) {
            for (ServerPlayer p : players) {
                play(p, SoundEvents.ANVIL_USE, 0.25f, kind == ExpeditionKind.BASALT_FORGE ? 0.45f : 0.6f, 8);   // the hammer every 2 s
            }
        }
        if (now % 20 != 0) {
            return;
        }
        for (ServerPlayer p : players) {
            if (RANDOM.nextInt(6) != 0) {
                continue;
            }
            List<SoundEvent> sounds = sounds(kind);
            play(p, sounds.get(RANDOM.nextInt(sounds.size())), 0.5f, 0.7f + RANDOM.nextFloat() * 0.3f, 10);
        }
    }

    private static List<SoundEvent> sounds(ExpeditionKind kind) {
        return switch (kind) {
            case STONE_CRYPT -> List.of(SoundEvents.AMBIENT_CAVE.value(), SoundEvents.BELL_RESONATE, SoundEvents.CHAIN_PLACE);
            case DESERT_CRYPT -> List.of(SoundEvents.AMBIENT_CAVE.value(), SoundEvents.HUSK_AMBIENT, SoundEvents.SAND_BREAK);
            case ILLAGER_KEEP -> List.of(SoundEvents.WEATHER_RAIN_ABOVE, SoundEvents.ANVIL_USE, SoundEvents.PILLAGER_AMBIENT);
            case FROST_KEEP -> List.of(SoundEvents.POWDER_SNOW_STEP, SoundEvents.GLASS_BREAK, SoundEvents.AMBIENT_CAVE.value());
            case POISON_SWAMP -> List.of(SoundEvents.FROG_AMBIENT, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundEvents.WITCH_AMBIENT);
            case DEEP_CELLAR -> List.of(SoundEvents.WARDEN_HEARTBEAT, SoundEvents.SCULK_CLICKING, SoundEvents.CHAIN_PLACE);
            case NETHER_FORGE -> List.of(SoundEvents.FURNACE_FIRE_CRACKLE, SoundEvents.LAVA_POP, SoundEvents.ANVIL_USE);
            case BASALT_FORGE -> List.of(SoundEvents.SOUL_ESCAPE.value(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundEvents.ANVIL_USE);
        };
    }

    /** A sound for one player only, a few blocks off in some direction. */
    static void play(ServerPlayer p, SoundEvent sound, float volume, float pitch, double spread) {
        double a = RANDOM.nextDouble() * Math.PI * 2;
        double d = spread * RANDOM.nextDouble();
        p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.AMBIENT,
                p.getX() + Math.cos(a) * d, p.getY() + 1, p.getZ() + Math.sin(a) * d, volume, pitch, RANDOM.nextLong()));
    }

    // ------------------------------------------------------------------ moments

    /** The runes round the seal stone: one part lights for each player ready. */
    static void runes(ServerLevel level, BlockPos seal, int ready, int members) {
        int parts = 12;
        int lit = members <= 0 ? 0 : Math.round(parts * ready / (float) members);
        for (int i = 0; i < parts; i++) {
            double a = Math.PI * 2 * i / parts;
            double x = seal.getX() + 0.5 + Math.cos(a) * 2.2;
            double z = seal.getZ() + 0.5 + Math.sin(a) * 2.2;
            level.sendParticles(i < lit ? ParticleTypes.END_ROD : ParticleTypes.ENCHANT, x, seal.getY() + 0.15, z, 1, 0.05, 0.0, 0.05, 0.0);
        }
    }

    /** How loud the arena's music plays (was a disc at full volume before 29 October 2026). */
    static final float MUSIC_VOLUME = 0.35f;

    /** The music of the building, for the group (vanilla discs over the record slider). */
    static Holder<SoundEvent> music(ExpeditionKind kind) {
        String id = switch (kind.layout()) {
            case CRYPT -> "music_disc.creator";
            case KEEP -> "music_disc.precipice";
            case CELLAR -> "music_disc.tears";
            case FORGE -> "music_disc.pigstep";
        };
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));
        return sound == null ? null : BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
    }

    /** The arena's gate slams down: dust, a thud, the music begins. */
    static void arenaClosed(ServerLevel level, List<BlockPos> gate, Collection<ServerPlayer> players, ExpeditionKind kind) {
        BlockPos at = gate.get(gate.size() / 2);
        level.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 2.5f, 0.5f);
        level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 40, 2.5, 0.3, 1.0, 0.02);
        Holder<SoundEvent> music = music(kind);
        for (ServerPlayer p : players) {
            p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 40, 0, false, false));
            if (music != null) {
                // Quieter and with the player (Simon 29.10.: "die Musik des Dungeons muss leiser"): bound to
                // him, so it neither swells nor fades as he walks; the record slider still sets it.
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(music, SoundSource.RECORDS, p,
                        MUSIC_VOLUME, 1f, RANDOM.nextLong()));
            }
        }
    }

    /** The ring of light round the dome: a flash runs once round (share 0..1 of the way). */
    static void ring(ServerLevel level, BlockPos top, double share) {
        double a = share * Math.PI * 2 - Math.PI / 2;
        for (int k = 0; k < 3; k++) {
            double b = a - k * 0.05;
            level.sendParticles(ParticleTypes.END_ROD, top.getX() + 0.5 + Math.cos(b) * 19.5, top.getY(), top.getZ() + 0.5 + Math.sin(b) * 19.5,
                    4, 0.2, 0.2, 0.2, 0.01);
        }
    }

    /** The champion's pit glows, a column of his fire rises. */
    static void pit(ServerLevel level, BlockPos pit, ExpeditionKind kind, int step) {
        SimpleParticleType fire = kind.fragment() == com.mki.nightsiege.core.NightFragment.EMBER ? ParticleTypes.FLAME
                : kind.layout() == ExpeditionKind.Layout.CELLAR || kind == ExpeditionKind.STONE_CRYPT
                || kind == ExpeditionKind.BASALT_FORGE ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        double x = pit.getX() + 0.5;
        double z = pit.getZ() + 0.5;
        for (int y = 0; y < 8; y++) {
            level.sendParticles(fire, x, pit.getY() + y, z, 6, 0.6, 0.3, 0.6, 0.01);
        }
        if (step == 0) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, pit.getY() + 1, z, 1, 0, 0, 0, 0);
        }
    }

    /** Phase 2: a roar, the fog closer, the dungeon's own sign. */
    static void phase2(ServerLevel level, ServerPlayer[] players, ExpeditionKind kind, Vec3 champion) {
        level.playSound(null, BlockPos.containing(champion), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.7f);
        for (ServerPlayer p : players) {
            look(p, kind, true);
        }
        double x = champion.x;
        double y = champion.y;
        double z = champion.z;
        switch (kind) {
            case STONE_CRYPT -> level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y + 2, z, 200, 10, 3, 10, 0.02);
            case DESERT_CRYPT -> level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 8, z, 400, 14, 4, 14, 0.05);
            case ILLAGER_KEEP -> {
                level.playSound(null, BlockPos.containing(champion), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 0.8f);
                for (ServerPlayer p : players) {
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 10, 0, false, false));   // the flash
                }
            }
            case FROST_KEEP -> level.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 6, z, 500, 14, 5, 14, 0.05);
            case POISON_SWAMP -> level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 3, z, 300, 12, 3, 12, 0.02);
            case DEEP_CELLAR -> {
                level.playSound(null, BlockPos.containing(champion), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3f, 0.8f);
                for (ServerPlayer p : players) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                }
            }
            case NETHER_FORGE -> level.sendParticles(ParticleTypes.LAVA, x, y + 6, z, 120, 12, 3, 12, 0.1);
            case BASALT_FORGE -> level.sendParticles(ParticleTypes.SOUL, x, y + 1, z, 200, 10, 2, 10, 0.05);
        }
    }

    /** The win: three bells, a column of light over the way out. */
    static void win(ServerLevel level, Vec3 at) {
        for (int i = 0; i < 3; i++) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3f, 0.6f + i * 0.2f);
        }
        for (int y = 0; y < 16; y++) {
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + y, at.z, 6, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** The collapse: dust and pieces from the roof over a player. */
    static void debris(ServerLevel level, ServerPlayer p, BlockState roof) {
        level.sendParticles(p, new BlockParticleOption(ParticleTypes.FALLING_DUST, roof), false, false,
                p.getX(), p.getY() + 5, p.getZ(), 40, 5, 0.5, 5, 0.0);
    }
}
