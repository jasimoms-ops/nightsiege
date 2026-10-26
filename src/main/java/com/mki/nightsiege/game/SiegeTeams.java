package com.mki.nightsiege.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.util.Optional;

/**
 * Colours the glow of siege mobs.
 *
 * <p>Minecraft takes the glow colour from the entity's scoreboard team, so the
 * mod keeps throwaway teams: deep red for blood moon nights, gold for the
 * champion and purple for the summoner's raised dead. Both are recreated on demand and never touch teams the
 * players made themselves.</p>
 */
public final class SiegeTeams {

    public static final String BLOOD_MOON = "nightsiege_blood";
    public static final String CHAMPION = "nightsiege_champ";
    /** The summoner's raised dead with the grave candles (crate gain): purple. */
    public static final String GRAVE = "nightsiege_grave";
    /** The soulcatcher's ghosts: aqua, so they stand out from the evokers' vexes. */
    public static final String GHOST = "nightsiege_ghost";

    private SiegeTeams() {
    }

    private static PlayerTeam ensure(MinecraftServer server, String name, TeamColor color) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
        }
        team.setColor(Optional.of(color));
        return team;
    }

    /** Puts the entity on a coloured team and makes it glow. */
    public static void mark(MinecraftServer server, Entity entity, String teamName, TeamColor color) {
        if (server == null || entity == null) {
            return;
        }
        try {
            PlayerTeam team = ensure(server, teamName, color);
            server.getScoreboard().addPlayerToTeam(entity.getScoreboardName(), team);
            entity.setGlowingTag(true);
        } catch (RuntimeException e) {
            // A glow is decoration; never let it break a siege.
            NightSiegeModLog.warn("Leuchtmarkierung fehlgeschlagen: " + e);
        }
    }

    /** Removes both teams again, so nothing is left behind after the night. */
    public static void cleanUp(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            Scoreboard scoreboard = server.getScoreboard();
            for (String name : new String[]{BLOOD_MOON, CHAMPION, GRAVE, GHOST}) {
                PlayerTeam team = scoreboard.getPlayerTeam(name);
                if (team != null) {
                    scoreboard.removePlayerTeam(team);
                }
            }
        } catch (RuntimeException e) {
            NightSiegeModLog.warn("Teams aufraeumen fehlgeschlagen: " + e);
        }
    }
}
