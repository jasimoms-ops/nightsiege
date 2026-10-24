package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Locale;
import java.util.Map;

/**
 * Shows a player's subclass (or, before one is chosen, the class) after the
 * name: above the head, in the tab list and in chat. Done with scoreboard
 * teams, which Minecraft shows in all three places; a player who is already in
 * a team someone else made keeps it and gets no tag.
 */
public final class ClassTags {

    private static final String PREFIX = "ns_c_";

    private static final Map<SkillClass, Integer> COLORS = Map.of(
            SkillClass.ARCHER, 0x8BD45C,
            SkillClass.WARRIOR, 0xFF6B4A,
            SkillClass.GUARDIAN, 0x6FA8FF,
            SkillClass.ENGINEER, 0xF0A040,
            SkillClass.SUMMONER, 0x5FD8B8);

    private ClassTags() {
    }

    public static void update(MinecraftServer server, ServerPlayer player, PlayerSkills skills, String subclass) {
        if (server == null || player == null) {
            return;
        }
        try {
            Scoreboard scoreboard = server.getScoreboard();
            String name = player.getScoreboardName();
            PlayerTeam current = scoreboard.getPlayersTeam(name);
            if (current != null && !current.getName().startsWith(PREFIX)) {
                return;   // a team of the players' own: leave it alone
            }
            SkillClass clazz = skills == null || !skills.hasClass() ? null : skills.clazz();
            if (clazz == null || clazz.isShared()) {
                if (current != null) {
                    scoreboard.removePlayerFromTeam(name, current);
                }
                return;
            }
            boolean sub = subclass != null && !subclass.isBlank();
            String key = sub ? subclass : clazz.name().toLowerCase(Locale.ROOT);
            String teamName = PREFIX + key;
            if (current != null && current.getName().equals(teamName)) {
                return;
            }
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
                Component label = Component.translatable(sub ? "nightsiege.subclass." + key : clazz.translationKey())
                        .withStyle(style -> style.withColor(TextColor.fromRgb(COLORS.getOrDefault(clazz, 0xC0C0C0))));
                team.setPlayerSuffix(Component.literal(" ").append(label));
            }
            scoreboard.addPlayerToTeam(name, team);
        } catch (RuntimeException e) {
            // A name tag is decoration; never let it break the game.
            NightSiegeModLog.warn("Klassen-Namensschild fehlgeschlagen: " + e);
        }
    }
}
