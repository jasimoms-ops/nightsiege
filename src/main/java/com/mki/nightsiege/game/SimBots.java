package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.mixin.ConnectionAccessor;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The balance tool's team bots (Simon, 29 October 2026: "Team-Sim mit 1–10 Bots"): real server
 * players without a client. They join through the player list like anyone else, so the night
 * sees them as defenders, the monsters hunt them, kits, skills and the logger treat them as
 * players. Their connection is an embedded channel that swallows every packet (the way the
 * Carpet mod's fake players work); nothing ever reaches a network.
 *
 * <p>A real player's game loop runs from his connection; a bot's runs from the level's entity
 * tick, see {@link Bot#tick()}.</p>
 */
public final class SimBots {

    private static final Map<UUID, Bot> BOTS = new LinkedHashMap<>();

    private SimBots() {
    }

    /** True for a team bot of the balance tool. */
    public static boolean isBot(Player player) {
        return player != null && BOTS.containsKey(player.getUUID());
    }

    public static boolean isBot(UUID id) {
        return id != null && BOTS.containsKey(id);
    }

    /** The bots there now, in the order they came. */
    public static List<ServerPlayer> all() {
        List<ServerPlayer> out = new ArrayList<>();
        for (Bot bot : BOTS.values()) {
            if (!bot.isRemoved()) {
                out.add(bot);
            }
        }
        return out;
    }

    /**
     * Every bot this tool brought in, the dead ones too. A bot that really died (outside the night no
     * drone catches the fall) has no client to press "respawn": after a second its body leaves the level
     * ({@link #all()} no longer lists it), but it stays in the server's player list until it is taken out.
     */
    public static List<ServerPlayer> known() {
        return new ArrayList<>(BOTS.values());
    }

    /** True when this bot can play a run: alive, in the level, and the one the player list knows by its id. */
    static boolean standing(MinecraftServer server, ServerPlayer bot) {
        return bot != null && !bot.isRemoved() && bot.isAlive() && server.getPlayerList().getPlayer(bot.getUUID()) == bot;
    }

    /** The same name always gives the same id, so a bot's save entries stay its own. */
    public static UUID idOf(String name) {
        return UUID.nameUUIDFromBytes(("nightsiege-bot:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Brings a bot into the world at this spot (survival, full health). Null when it failed. A bot of
     * this name that still stands is given back as it is; one that died (or is gone from the player list)
     * is taken out first and comes back fresh, so no dead body with the last run's skills stays behind
     * as a "defender" of the next night (31.10., one bot missing in 10–25 % of the test nights).
     */
    public static ServerPlayer spawn(MinecraftServer server, ServerLevel level, String name, double x, double y, double z) {
        UUID id = idOf(name);
        Bot old = BOTS.get(id);
        if (old != null && standing(server, old)) {
            return old;
        }
        if (old != null) {
            System.out.println("[Night Siege sim] BOTNEU;" + name + ";" + (old.isAlive() ? "fort" : "tot"));
            remove(server, old);
        }
        ServerPlayer stale = server.getPlayerList().getPlayer(id);
        if (stale != null) {
            // Something else under the bot's id (a copy the player list made of it): out as well.
            try {
                server.getPlayerList().remove(stale);
            } catch (RuntimeException e) {
                NightSiegeMod.LOGGER.warn("[Night Siege sim] Alter Eintrag von Bot {} ließ sich nicht entfernen: {}", name, e.toString());
            }
        }
        GameProfile profile = new GameProfile(id, name);
        Bot bot = new Bot(server, level, profile);
        EmbeddedChannel channel = new EmbeddedChannel();
        BotConnection connection = new BotConnection(channel);
        bot.channel = channel;
        try {
            server.getPlayerList().placeNewPlayer(connection, bot, CommonListenerCookie.createInitial(profile, false));
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege sim] Bot {} konnte nicht beitreten: {}", name, e.toString());
            return null;
        }
        BOTS.put(id, bot);
        bot.setGameMode(GameType.SURVIVAL);
        bot.teleportTo(level, x, y, z, java.util.Set.of(), 0f, 0f, true);
        // Its save file may hold the body of a bot that died: health, fire and effects start over.
        bot.setHealth(bot.getMaxHealth());
        bot.clearFire();
        bot.removeAllEffects();
        bot.getFoodData().setFoodLevel(20);
        return bot;
    }

    /** Takes one bot out of the world again. */
    public static void remove(MinecraftServer server, ServerPlayer player) {
        if (!(player instanceof Bot bot)) {
            return;
        }
        BOTS.remove(bot.getUUID(), bot);
        try {
            server.getPlayerList().remove(bot);
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege sim] Bot {} ließ sich nicht entfernen: {}", bot.getScoreboardName(), e.toString());
        }
        bot.channel.close();
    }

    /** Takes every bot out (end of the simulation, server stopping), the dead ones too. */
    public static void removeAll(MinecraftServer server) {
        for (ServerPlayer bot : known()) {
            remove(server, bot);
        }
        BOTS.clear();
    }

    /** A server player whose game loop runs from the level, with nothing on the other end of the line. */
    static final class Bot extends ServerPlayer {
        EmbeddedChannel channel;

        Bot(MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile, ClientInformation.createDefault());
        }

        @Override
        public void tick() {
            super.tick();
            try {
                // What the connection does for a real player each tick: the player's own update
                // (attack strength, item use, health and hunger, potion effects).
                doTick();
            } catch (RuntimeException e) {
                NightSiegeMod.LOGGER.warn("[Night Siege sim] Bot-Tick {}: {}", getScoreboardName(), e.toString());
            }
            if (channel != null) {
                channel.releaseOutbound();   // the packets for a client that is not there
            }
        }
    }

    /** A connection on an embedded channel: packets go nowhere, protocol changes do nothing. */
    static final class BotConnection extends Connection {

        BotConnection(EmbeddedChannel channel) {
            super(PacketFlow.SERVERBOUND);
            ((ConnectionAccessor) (Object) this).nightsiege$setChannel(channel);
        }

        @Override
        public void setReadOnly() {
        }

        @Override
        public void handleDisconnection() {
        }

        @Override
        public void setListenerForServerboundHandshake(PacketListener listener) {
        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> info, T listener) {
        }
    }
}
