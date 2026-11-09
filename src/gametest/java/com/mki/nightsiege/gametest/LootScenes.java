package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.CrateGains;
import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.CrateRoll;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.game.GainGear;
import com.mki.nightsiege.game.GainKeys;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tor 5.4, scene "loot": for every class the crate is rolled on the server (the same roll as a crate in
 * the hand) until a gain with a key comes; it is on the keys 1-3 of that class; no retired gain is ever
 * drawn; the archer's fire arrows fill the quiver. Every line starts with "[loot]"; "OK/FAIL"; the summary
 * "x von y" (0 checks = FAIL).
 */
final class LootScenes {

    private LootScenes() {
    }

    private static final Set<String> RETIRED = Set.of("fackeln", "spektralpfeile", "fester_griff", "leuchtschild",
            "zweite_ladung", "recycling", "koecher", "blendgranaten", "wurfspeere", "wurfbeile");
    private static int ok;
    private static int all;

    private static void log(String line) {
        System.out.println("[loot] " + line);
    }

    private static void check(boolean good, String what) {
        all++;
        ok += good ? 1 : 0;
        log((good ? "OK   " : "FAIL ") + what);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        ok = 0;
        all = 0;
        EventLookTest.ensureCore(context, game);
        game.getServer().runCommand("time set 18000");
        context.waitTicks(10);
        for (SkillClass clazz : new SkillClass[]{SkillClass.WARRIOR, SkillClass.ARCHER, SkillClass.GUARDIAN,
                SkillClass.ENGINEER, SkillClass.SUMMONER}) {
            EventLookTest.onServer(context, (player, m) -> {
                if (!m.isRegistered(player)) {
                    m.register(player);
                }
                PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
                String oldClass = own.chosenClass;
                Map<String, Integer> oldGains = own.gains.get(clazz.name()) == null ? null
                        : new HashMap<>(own.gains.get(clazz.name()));
                int oldPoints = own.points;
                own.chosenClass = clazz.name();
                own.gains.put(clazz.name(), new HashMap<>());
                if (own.gainKeyChoice != null) {
                    own.gainKeyChoice.remove(clazz.name());
                }
                String won = null;
                String retired = null;
                int rolls = 0;
                for (; rolls < 400 && won == null; rolls++) {
                    CrateRoll roll = m.crates().roll(own, clazz, CrateKind.values()[0], 7);
                    if (roll == null || roll.gain == null) {
                        break;
                    }
                    if (RETIRED.contains(roll.gain) && retired == null) {
                        retired = roll.gain;
                    }
                    if (!roll.duplicate && CrateGains.keyAbility(roll.gain)) {
                        won = roll.gain;
                    }
                }
                check(retired == null, clazz + ": no retired gain in " + rolls + " crates" + (retired == null ? "" : " (" + retired + ")"));
                check(won != null, clazz + ": a key gain came out of the crate after " + rolls + ": " + won);
                List<String> keys = GainKeys.assigned(m, own, clazz);
                String key = com.mki.nightsiege.core.GainCharges.FIRST_AID.equals(won)
                        ? com.mki.nightsiege.core.GainCharges.POTION : won;   // Tor 5.7: the first-aid kit is in the field "Heiltrank"
                check(won != null && keys.contains(key), clazz + ": new gain on the keys 1-3: " + keys);
                if (clazz == SkillClass.ARCHER) {
                    own.setGainStage(clazz, "brandpfeile", 1);
                    own.setGainStage(clazz, "lichtpfeile", 1);
                    m.kits().equip(player, 7);
                    var q = GainGear.quiverOf(player);
                    check(q != null && q.shots.getOrDefault("fire", 0) == 16 && q.shots.getOrDefault("light", 0) == 16,
                            "ARCHER: fire and light arrows in the quiver " + (q == null ? "-" : q.shots));
                    if (m.kits().hasStash(player)) {
                        m.kits().restore(player);
                    }
                }
                // everything back for the next scenes
                if (oldGains == null) {
                    own.gains.remove(clazz.name());
                } else {
                    own.gains.put(clazz.name(), oldGains);
                }
                if (own.gainKeyChoice != null) {
                    own.gainKeyChoice.remove(clazz.name());
                }
                own.points = oldPoints;
                own.chosenClass = oldClass;
            });
            context.waitTicks(5);   // onServer only queues the step on the server thread
        }
        game.getServer().runCommand("time set 1000");
        context.waitTicks(5);
        log((all > 0 && ok == all ? "OK   " : "FAIL ") + ok + " von " + all);
        log("done");
    }
}
