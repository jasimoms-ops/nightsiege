package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.HudSync;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tor 2 "hud2" (Simon's play test 08.11.: "kein sichtbarer Cooldown außer G und Ult", the medic: "Anzeige aller
 * Mitspieler mit Leben"): the healers' boxes carry their pauses, the guardian's shield action and last stand
 * show as timers, the party list draws. Screenshots hud2_01_medic, hud2_02_shield. Log lines "[hud2]".
 */
final class Hud2Scenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private Hud2Scenes() {
    }

    private static void log(String line) {
        System.out.println("[hud2] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    private static void server(ClientGameTestContext context, String step, EventLookTest.OnServer action) {
        EventLookTest.onServer(context, (player, manager) -> {
            try {
                action.run(player, manager);
            } catch (Throwable t) {
                FAILS.add(step + ": " + t);
                log("ERROR in " + step + ": " + t);
            }
        });
    }

    private static void guardian(ServerPlayer player, SiegeManager m, String... nodes) {
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        own.unlockClass(SkillClass.GUARDIAN);
        own.switchTo(SkillClass.GUARDIAN);
        own.levels.clear();
        for (String id : nodes) {
            own.levels.put(id, 1);
        }
        m.kits().equip(player, 10);
        m.sendUltHud(player);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);

        // 1: the medic - every healing box has its pause, F too, the party list draws
        server(context, "medic", (player, m) -> {
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            guardian(player, m, "g_healing_hand", "g_dome");
            List<UltState.Slot> slots = m.abilitySlots(player);
            check(!slots.isEmpty(), "the medic has boxes on the bar (" + slots.size() + ")");
            for (UltState.Slot s : slots) {
                check(s.totalTicks > 0, "box " + s.id + " knows its pause (" + s.totalTicks + " ticks)");
            }
            check(com.mki.nightsiege.game.HealthView.healer(m, player), "the medic sees the party list (Tor 3.5)");
        });
        context.runOnClient(client -> {
            HudSync.Party p = new HudSync.Party();
            p.mates.add(new HudSync.Mate("a", "Anna", 5, 20, 0));
            p.mates.add(new HudSync.Mate("b", "Bernhard", 14, 20, 4));
            p.mates.add(new HudSync.Mate("c", "Clara", 0, 20, 0));
            com.mki.nightsiege.client.PartyHealthHud.set(new com.google.gson.Gson().toJson(p));
        });
        context.waitTicks(10);
        context.takeScreenshot("hud2_01_medic");
        context.runOnClient(client -> com.mki.nightsiege.client.PartyHealthHud.clear());

        // 2: the shield fighter - shield action and last stand as timers
        server(context, "shield", (player, m) -> {
            guardian(player, m, "riposte", "g_shieldthrow", "g_laststand");
            List<UltState.Slot> timers = m.combatEffects().timers(player, player.level().getGameTime());
            // Tor 3.1: the shield throw is a box of its own on Q (no longer block + left click in the timer row)
            check(m.abilitySlots(player).stream().anyMatch(t -> t.id.equals(com.mki.nightsiege.core.AbilityKeys.SHIELD)
                    && com.mki.nightsiege.core.AbilityKeys.Key.ABILITY_1.name().equals(t.key) && t.totalTicks > 0)
                    && timers.stream().noneMatch(t -> t.id.equals("shield_action")), "shield action timer");
            check(timers.stream().anyMatch(t -> t.id.equals("laststand") && t.totalTicks > 0), "last stand timer");
            check(!com.mki.nightsiege.game.HealthView.healer(m, player), "the shield fighter sees no party list");
        });
        context.waitTicks(10);
        context.takeScreenshot("hud2_02_shield");
        server(context, "end", (player, m) -> {
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
        });
        context.waitTicks(5);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
