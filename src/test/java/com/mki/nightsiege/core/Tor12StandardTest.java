package com.mki.nightsiege.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tor 12 Teil B (Simon 17.11.: Nacht 1 allein nur ca. 3 Monster): the mod's own defaults are the server's balance
 * (v30); migration v31 moves values still on the old default, own values stay.
 */
class Tor12StandardTest {

    /** Not balance: the spawn side belongs to the server's map, the test tools and the help text to the server. */
    private static final List<String> NOT_BALANCE = List.of("spawnArcFrom", "spawnArcTo", "testToolsForEveryone", "tuningHelp");

    private static SiegeConfig read(String path) throws Exception {
        SiegeConfig c = new Gson().fromJson(Files.readString(Path.of(path), StandardCharsets.UTF_8), SiegeConfig.class);
        c.sanitize();
        return c;
    }

    /** The balance values as JSON; the mob list by id (its order does not matter). */
    private static JsonObject balance(SiegeConfig c) {
        JsonObject o = new Gson().toJsonTree(c).getAsJsonObject();
        NOT_BALANCE.forEach(o::remove);
        TreeMap<String, JsonElement> mobs = new TreeMap<>();
        for (JsonElement e : o.remove("mobs").getAsJsonArray()) {
            mobs.put(e.getAsJsonObject().get("id").getAsString(), e);
        }
        JsonObject byId = new JsonObject();
        mobs.forEach(byId::add);
        o.add("mobs", byId);
        return o;
    }

    private static void assertSameBalance(JsonObject want, JsonObject have) {
        for (String key : want.keySet()) {
            assertEquals(want.get(key), have.get(key), "Wert " + key);
        }
        assertEquals(want.keySet(), have.keySet());
    }

    @Test
    void freshConfigIsTheServers() throws Exception {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        SiegeConfig server = read("tools/sim/server-config-v30.json");
        assertEquals(SiegeConfig.SETTINGS_VERSION, fresh.settingsVersion);
        assertEquals(31, server.settingsVersion);
        assertSameBalance(balance(server), balance(fresh));
        // the numbers from Simon's night 1 (local world on the old defaults)
        assertEquals(1.8, fresh.mobsPerLevel, 1e-9);
        assertEquals(0.924, fresh.monsterDamage, 1e-9);
        assertTrue(fresh.refillSpawns);
        assertEquals(3, fresh.wardenFromLevel);
        assertEquals(3, server.wardenFromLevel);   // missing key: the field's default
    }

    @Test
    void oldStandardBecomesTheServers() throws Exception {
        SiegeConfig old = read("src/test/resources/tor12/standard-v30.json");
        SiegeConfig server = read("tools/sim/server-config-v30.json");
        assertEquals(SiegeConfig.SETTINGS_VERSION, old.settingsVersion);
        assertSameBalance(balance(server), balance(old));
    }

    @Test
    void ownValuesStay() throws Exception {
        JsonObject json = new Gson().fromJson(Files.readString(Path.of("src/test/resources/tor12/standard-v30.json"),
                StandardCharsets.UTF_8), JsonObject.class);
        json.addProperty("mobsPerLevel", 1.3);
        json.addProperty("monsterDamage", 1.1);
        json.getAsJsonObject("subclassDamage").addProperty("axe", 1.4);
        JsonArray caps = new JsonArray();
        caps.add(10);
        caps.add(20);
        json.add("liveCapByPlayers", caps);
        json.getAsJsonObject("skillTweaks").getAsJsonObject("potion_tower").getAsJsonObject("values").addProperty("hit", 7.0);
        JsonObject bloodlust = new JsonObject();
        JsonObject values = new JsonObject();
        values.addProperty("steal", 0.2);
        bloodlust.add("values", values);
        json.getAsJsonObject("skillTweaks").add("bloodlust", bloodlust);
        for (JsonElement e : json.getAsJsonArray("mobs")) {
            if (e.getAsJsonObject().get("id").getAsString().equals("minecraft:vex")) {
                e.getAsJsonObject().addProperty("weight", 5);
            }
        }
        SiegeConfig c = new Gson().fromJson(json, SiegeConfig.class);
        c.sanitize();
        assertEquals(1.3, c.mobsPerLevel, 1e-9);
        assertEquals(1.1, c.monsterDamage, 1e-9);
        assertEquals(1.4, c.subclassDamage.get("axe"), 1e-9);
        assertEquals(List.of(10, 20), c.liveCapByPlayers);
        assertEquals(7.0, c.skillTweaks.get("potion_tower").values.get("hit"), 1e-9);
        assertEquals(0.2, c.skillTweaks.get("bloodlust").values.get("steal"), 1e-9);
        assertEquals(1.01, c.skillTweaks.get("bloodlust").values.get("below"), 1e-9);   // missing key comes in
        assertTrue(c.mobs.stream().anyMatch(m -> m.id.equals("minecraft:vex") && m.weight == 5));
        assertTrue(c.mobs.stream().noneMatch(m -> m.id.equals("minecraft:phantom")));
        // the rest moved
        assertEquals(0.1, c.healthPerLevel, 1e-9);
        assertEquals(0.9, c.subclassDamage.get("blademaster"), 1e-9);
    }

    @Test
    void noWardenBeforeLevelThree() {
        SiegeConfig c = new Gson().fromJson("{\"settingsVersion\":31,\"wardenFromLevel\":0}", SiegeConfig.class);
        c.sanitize();
        assertEquals(1, c.wardenFromLevel);
    }
}
