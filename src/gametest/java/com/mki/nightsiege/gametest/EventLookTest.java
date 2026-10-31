package com.mki.nightsiege.gametest;

import com.mki.nightsiege.game.EventAtmosphere;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Starts the real client, loads a world and photographs every event look:
 * sky, light and the event textures of the four common monsters. When the CI
 * hands over shader packs (with Iris and Sodium on the class path), the same
 * is photographed under each pack.
 */
public class EventLookTest implements FabricClientGameTest {

    /** Where the boss scene's test blast went (night damage comes back after the night). */
    private static BlockPos BLAST_SPOT;


    private static final String[] EVENTS = {
            EventAtmosphere.ECLIPSE, EventAtmosphere.FROST, EventAtmosphere.NETHER_RIFT, EventAtmosphere.FOG,
            EventAtmosphere.THUNDER, EventAtmosphere.BLOOD_MOON, EventAtmosphere.AURORA};

    @Override
    public void runTest(ClientGameTestContext context) {
        startWatchdog();
        String packs = System.getenv("NIGHTSIEGE_SHADERPACKS");
        boolean shaderRun = packs != null && !packs.isBlank();
        try (TestSingleplayerContext game = context.worldBuilder()
                .adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            if (shaderRun) {
                game.getConnection().waitForChunksDownload();
                context.waitTicks(100);
            } else {
                game.getConnection().waitForChunksRender();
                // Every mixin of the mod (and all others) must apply cleanly.
                MixinEnvironment.getCurrentEnvironment().audit();
            }
            setUpScene(context, game);
            registrationScene(context);
            String only = System.getenv("NIGHTSIEGE_SCENES");
            if (only != null && !only.isBlank() && !shaderRun) {
                // A dispatch with "scenes": only these, for a quick look (27 October 2026).
                java.util.List<String> want = java.util.Arrays.asList(only.toLowerCase(Locale.ROOT).replace(" ", "").split(","));
                if (want.contains("camp")) {
                    campScene(context, game);
                }
                if (want.contains("base")) {
                    baseScene(context, game);   // the base, a test night and the mending after it
                }
                if (want.contains("machines")) {
                    machineScene(context, game);
                }
                if (want.contains("bar")) {
                    alchemistScene(context, game);   // the ability bar of the alchemist and the healer
                    summonerScene(context, game);    // ... and of the summoners
                }
                if (want.contains("bar") || want.contains("seele")) {
                    soulcatcherScene(context, game);   // souls in the dungeon, the spells without G (28 October 2026)
                }
                if (want.contains("tech")) {
                    techScene(context, game);   // the engineer after the playtest of 28 October 2026
                }
                if (want.contains("foes")) {
                    foeScene(context, game);
                }
                if (want.contains("raid")) {
                    raidScene(context, game);
                }
                if (want.contains("curse")) {
                    curseScene(context, game);   // update 2: the curse altar and the nemesis
                }
                if (want.contains("ruhmeshalle")) {
                    hallScene(context, game);   // the Ruhmeshalle and the points command (31 October 2026)
                }
                if (want.contains("dungeons")) {
                    dungeonScene(context, game);
                }
                if (want.contains("dungeondump")) {
                    dungeonDumpScene(context, game);   // every block of the eight dungeons as a file (29 October 2026)
                }
                if (want.contains("warden")) {
                    wardenScene(context, game);   // the eight dungeon wardens (29 October 2026)
                }
                if (want.contains("guards")) {
                    guardScene(context, game);   // the dungeon guards in their warden's look (29 October 2026, 22:06)
                }
                if (want.contains("mobs")) {
                    mobLineup(context, game);   // the event monsters of every event
                }
                if (want.contains("stats")) {
                    statsScene(context, game);
                    nightStatsScene(context);
                }
                if (want.contains("percent")) {
                    percentScene(context, game);   // shares of the max health (28 October 2026)
                }
                if (want.contains("weapons")) {
                    kitWeaponScene(context, game);   // the kit weapons, 3D since 29 October 2026
                }
                if (want.contains("bosses")) {
                    bossScene(context, game);   // all eight bosses (four more since the content update, 29 October 2026)
                }
                if (want.contains("campground")) {
                    campGroundScene(context, game);   // the camp's ground comes back (30 October 2026)
                }
                if (want.contains("bossai")) {
                    bossAiScene(context, game);   // the spider queen and the bone lich behind walls (30 October 2026)
                }
                if (want.contains("subtrees")) {
                    subTreeShots(context);   // one tree per subclass (content update, 29 October 2026)
                }
                if (want.contains("treebg")) {
                    treeSceneShots(context);   // the skill tree backgrounds v2 (29 October 2026)
                }
                if (want.contains("kithud")) {
                    kitHudScene(context, game);   // the kit HUD (28 October 2026)
                }
                if (want.contains("overhang")) {
                    overhangScene(context, game);   // spiders under an overhang
                }
                if (want.contains("playtest")) {
                    playtestScene(context, game);   // the playtest (28 October 2026)
                }
                if (want.contains("ui")) {
                    // The windows at the end of the full run (the full run outgrew its 40 minutes, 29 October 2026).
                    ensureCore(context, game);   // alone, no scene before it placed a core (30 October 2026)
                    versionBScene(context, game);
                    versionB2Scene(context, game);
                    classUiScene(context, game);
                }
                if (want.contains("bolzen")) {
                    bolterAimScene(context, game);   // where the bolter's bolt flies (Simon 30.10., 11:50)
                }
                if (want.contains("leiterburg")) {
                    ladderCastleScene(context, game);   // the same on the copy of Simon's castle (30.10.)
                }
                if (want.contains("leitersturm")) {
                    ladderCrowdScene(context, game);   // many monsters at one ladder in a real night (Simon 30.10., 16:12)
                }
                if (want.contains("survival")) {
                    survivalScene(context, game);   // a real night in survival, start to end (Simon 30.10., 23:51)
                }
                if (want.contains("ss-speichern")) {
                    SsSpeichernScenes.run(context, game);   // survival safety: save, keys, infighting (31.10.)
                }
                if (want.contains("ss-inventar")) {
                    SsInventarScenes.run(context, game);   // survival safety: kit swap, crates, crashes, death (31.10.)
                }
                if (want.contains("ss-welt")) {
                    SsWeltScenes.run(context, game);   // survival safety: the mending after the night (Simon 31.10.)
                }
                if (want.contains("ss-nacht")) {
                    SsNachtScenes.run(context, game);   // Survival-Sicherheit: accepts, sleeping, missed night, stop, /time set (31.10.)
                }
                if (want.contains("ladders")) {
                    // The climbing scenes, then the ladder cases of 28 October 2026 (block beside, ladder above the ground).
                    ladderScene(context, game);
                    ladderTargetScene(context, game, true);
                    innerLadderScene(context, game);
                    ladderCasesScene(context, game);
                }
                return;
            }
            if (System.getenv("NIGHTSIEGE_SIM") != null) {
                simScene(context, game);
                return;
            }

            if (!shaderRun) {
                bossScene(context, game);
                wardenScene(context, game);   // the eight dungeon wardens in their looks, and a warden night
                guardScene(context, game);   // the dungeon guards in their warden's look
                series(context, game, "event");
                mobLineup(context, game);
                context.runOnClient(client -> EventAtmosphere.setClient("", 1));
                context.waitTicks(30);
                context.takeScreenshot("event_99_night_normal");
                warriorWindow(context, game);
                doomScene(context);
                baseScene(context, game);
                inviteScene(context, game);
                coreTreeScene(context);
                armorScene(context, game);
                kitWeaponScene(context, game);
                guardianScene(context);
                engineerScene(context);
                ladderScene(context, game);
                ladderTargetScene(context, game, true);
                innerLadderScene(context, game);
                ladderOverhangScene(context, game);
                ladderCasesScene(context, game);   // block beside a ladder, ladder above the ground (28 October 2026)
                nightHoldScene(context, game);
                besiegerScene(context, game);   // again since 27 October 2026: more sappers, craters, free ladder ways
                campScene(context, game);
                machineScene(context, game);
                foeScene(context, game);   // the new attackers of update 1 (27 October 2026)
                raidScene(context, game);   // the raid's plan (27 October 2026)
                curseScene(context, game);   // update 2 "Fluch & Rache" (28 October 2026)
                dungeonScene(context, game);   // the own dungeons (dungeon update, 27 October 2026)
                statsScene(context, game);   // the statistics logger (28 October 2026), after the camp and the dungeons
                percentScene(context, game);   // bosses and machines take a share of the max health (28 October 2026)
                overhangScene(context, game);   // spiders swing over an overhang
                com.mki.nightsiege.game.TrapService.mortarsAnyTime = true;   // the scenes below test by day
                mortarScene(context, game);
                mannedScene(context, game);
                alchemistScene(context, game);
                techScene(context, game);   // grenade, blast jump, towers vs champions, three potions, field towers
                spotterScene(context, game);
                droneScene(context, game);
                nightStatsScene(context);
                // ladderTargetScene and towerScene are done (ladders fine, towers 2 volleys); kept for later checks.
                // besiegerScene is done (ladders up, one hole per side); kept for later checks.
                summonerScene(context, game);
                soulcatcherScene(context, game);   // souls in the dungeon, the spells without G (28 October 2026)
                kitHudScene(context, game);   // the kit HUD (28 October 2026)
                playtestScene(context, game);   // the playtest (28 October 2026): plan, four steps, all back
                versionBScene(context, game);
                versionB2Scene(context, game);
                classUiScene(context, game);
                return;
            }

            int n = 0;
            for (String pack : packs.split(";")) {
                if (pack.isBlank()) {
                    continue;
                }
                n++;
                if (!activate(context, Path.of(pack.trim()))) {
                    continue;
                }
                context.waitTicks(200);
                context.runOnClient(client -> EventAtmosphere.setClient("", 1));
                game.getServer().runCommand("time set 1500");
                context.getInput().lookAt(-90.0f, -12.0f);
                context.waitTicks(30);
                context.takeScreenshot("shader_%d_00_normal".formatted(n));
                series(context, game, "shader_" + n);
            }
        }
    }

    /**
     * A hang (30.10.: the client test ran into its 40 minutes without a word): when the server has
     * not ticked for a minute, the stacks of the server, render and test threads go into the log
     * and the game stops, so the log shows where it stood.
     */
    private static void startWatchdog() {
        Thread watch = new Thread(() -> {
            int last = -1;
            int still = 0;
            while (true) {
                try {
                    Thread.sleep(10_000L);
                } catch (InterruptedException e) {
                    return;
                }
                net.minecraft.server.MinecraftServer server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
                if (server == null) {
                    still = 0;
                    continue;
                }
                int ticks = server.getTickCount();
                still = ticks == last ? still + 1 : 0;
                last = ticks;
                if (still < 6) {
                    continue;
                }
                for (java.util.Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                    String name = e.getKey().getName();
                    if (!name.contains("Server thread") && !name.contains("Render thread") && !name.toLowerCase(Locale.ROOT).contains("test")) {
                        continue;
                    }
                    System.out.println("[Night Siege test] HANG " + name + " (" + e.getKey().getState() + ")");
                    int n = 0;
                    for (StackTraceElement el : e.getValue()) {
                        if (n++ >= 60) {
                            break;
                        }
                        System.out.println("[Night Siege test] HANG   at " + el);
                    }
                }
                System.out.flush();
                try {
                    Thread.sleep(3_000L);
                } catch (InterruptedException e) {
                    return;
                }
                Runtime.getRuntime().halt(3);
            }
        }, "Night Siege watchdog");
        watch.setDaemon(true);
        watch.start();
    }

    private static void setUpScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("weather clear");
        String[] mobs = {"zombie", "skeleton", "creeper", "spider"};
        for (int i = 0; i < mobs.length; i++) {
            game.getServer().runCommand("summon minecraft:%s %d %d %d {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Rotation:[90f,0f]}"
                    .formatted(mobs[i], pos.getX() + 7, pos.getY() + 1, pos.getZ() - 4 + i * 2 + 1));
        }
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block"
                .formatted(pos.getX() + 5, pos.getY() - 1, pos.getZ() - 5, pos.getX() + 9, pos.getY() - 1, pos.getZ() + 5));
        context.waitTicks(60);
        context.getInput().lookAt(-90.0f, -12.0f);
        context.waitTicks(10);
        context.takeScreenshot("event_00_normal_day");
    }

    /** Every event in turn: the eclipse by day facing the sun, the others at night facing the moon. */
    private static void series(ClientGameTestContext context, TestSingleplayerContext game, String prefix) {
        game.getServer().runCommand("time set 1500");
        context.getInput().lookAt(-90.0f, -12.0f);
        for (int i = 0; i < EVENTS.length; i++) {
            String event = EVENTS[i];
            // Sieges come at night; only the eclipse is shot by day.
            boolean night = !EventAtmosphere.ECLIPSE.equals(event);
            if (night) {
                game.getServer().runCommand("time set 14600");
                context.getInput().lookAt(EventAtmosphere.AURORA.equals(event) ? -150.0f : -90.0f,
                        EventAtmosphere.AURORA.equals(event) ? -40.0f : -22.0f);
            }
            context.runOnClient(client -> EventAtmosphere.setClient(event, 1));
            // The mist wisps need a few seconds to drift in; shader packs
            // build clouds and sky over many frames (few frames per second here).
            boolean misty = EventAtmosphere.FOG.equals(event) || EventAtmosphere.FROST.equals(event)
                    || EventAtmosphere.NETHER_RIFT.equals(event);
            context.waitTicks(prefix.startsWith("shader") ? 200 : misty ? 140 : 30);
            context.takeScreenshot("%s_%02d_%s".formatted(prefix, i + 1, event.toLowerCase(Locale.ROOT)));
        }
    }

    /** The event monsters up close, so their textures, clothes and eyes can be checked. */
    private static void mobLineup(ClientGameTestContext context, TestSingleplayerContext game) {
        closeScreen(context);   // the statistics window of the test nights before must not cover the photos
        BlockPos home = context.computeOnClient(client -> client.player.blockPosition());
        // At night (undead burn by day), seen with night vision so the textures show; no command echo in the chat.
        game.getServer().runCommand("gamerule log_admin_commands false");
        game.getServer().runCommand("gamerule send_command_feedback false");
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("effect give @a minecraft:night_vision 600 0 true");
        game.getServer().runCommand("tp @a %d.5 %d %d.5 -90 6".formatted(home.getX() + 2, home.getY(), home.getZ()));
        // The other kinds with event looks, in a second row to the side.
        String[] more = {"husk", "drowned", "stray", "bogged", "wither_skeleton", "parched", "cave_spider"};
        int vz = home.getZ() + 16;
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block"
                .formatted(home.getX() + 4, home.getY() - 1, vz - 8, home.getX() + 10, home.getY() - 1, vz + 8));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air"
                .formatted(home.getX() + 4, home.getY(), vz - 8, home.getX() + 10, home.getY() + 4, vz + 8));
        for (int i = 0; i < more.length; i++) {
            game.getServer().runCommand("summon minecraft:%s %d %d %d {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Rotation:[90f,0f]}"
                    .formatted(more[i], home.getX() + 8, home.getY(), vz - 6 + i * 2));
        }
        // Illagers, the witch, ravager and breeze; and the rift's nether monsters and the eclipse's phantom.
        String[] third = {"pillager", "vindicator", "evoker", "witch", "ravager", "breeze"};
        String[] fourth = {"blaze", "piglin_brute", "zombified_piglin", "hoglin", "magma_cube", "phantom"};
        int[] rows = {home.getZ() + 34, home.getZ() + 52};
        String[][] rowMobs = {third, fourth};
        for (int r = 0; r < 2; r++) {
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block"
                    .formatted(home.getX() + 4, home.getY() - 1, rows[r] - 9, home.getX() + 12, home.getY() - 1, rows[r] + 9));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air"
                    .formatted(home.getX() + 4, home.getY(), rows[r] - 9, home.getX() + 12, home.getY() + 5, rows[r] + 9));
            for (int i = 0; i < rowMobs[r].length; i++) {
                game.getServer().runCommand("summon minecraft:%s %d %d %d {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Rotation:[90f,0f],Size:1}"
                        .formatted(rowMobs[r][i], home.getX() + 9, home.getY() + (rowMobs[r][i].equals("phantom") ? 1 : 0), rows[r] - 7 + i * 3));
            }
        }
        context.waitTicks(20);
        for (String event : EVENTS) {
            if (EventAtmosphere.AURORA.equals(event)) {
                continue;
            }
            context.runOnClient(client -> EventAtmosphere.setClient(event, 1));
            context.waitTicks(30);
            closeScreen(context);   // a statistics window that came late must not cover the photos
            context.takeScreenshot("mobs_" + event.toLowerCase(Locale.ROOT));
            game.getServer().runCommand("tp @a %d.5 %d %d.5 -90 8".formatted(home.getX() + 3, home.getY(), vz));
            context.waitTicks(10);
            context.takeScreenshot("mobs2_" + event.toLowerCase(Locale.ROOT));
            // Without night vision: what glows in the dark.
            game.getServer().runCommand("effect clear @a minecraft:night_vision");
            context.waitTicks(10);
            context.takeScreenshot("mobs2_" + event.toLowerCase(Locale.ROOT) + "_dark");
            game.getServer().runCommand("effect give @a minecraft:night_vision 600 0 true");
            game.getServer().runCommand("tp @a %d.5 %d %d.5 -90 8".formatted(home.getX() + 2, home.getY(), rows[0]));
            context.waitTicks(10);
            context.takeScreenshot("mobs3_" + event.toLowerCase(Locale.ROOT));
            // The phantom wears every event's look since 29 October 2026 (the nether monsters only the rift's).
            game.getServer().runCommand("tp @a %d.5 %d %d.5 -90 8".formatted(home.getX() + 2, home.getY(), rows[1]));
            context.waitTicks(10);
            context.takeScreenshot("mobs4_" + event.toLowerCase(Locale.ROOT));
            // The phantom up close (it is the last of the fourth row, one block up).
            game.getServer().runCommand("tp @a %d.5 %d %d.5 facing %d.5 %d %d.5".formatted(home.getX() + 5, home.getY() + 1,
                    rows[1] + 8, home.getX() + 9, home.getY() + 1, rows[1] + 8));
            context.waitTicks(10);
            context.takeScreenshot("mobs5_phantom_" + event.toLowerCase(Locale.ROOT));
            game.getServer().runCommand("tp @a %d.5 %d %d.5 -90 6".formatted(home.getX() + 2, home.getY(), home.getZ()));
            context.waitTicks(5);
        }
        for (int z : rows) {
            game.getServer().runCommand("kill @e[type=!player,x=%d,y=%d,z=%d,distance=..14]".formatted(home.getX() + 9, home.getY(), z));
        }
        game.getServer().runCommand("kill @e[type=!player,x=%d,y=%d,z=%d,distance=..12]".formatted(home.getX() + 8, home.getY(), vz));
        game.getServer().runCommand("effect clear @a minecraft:night_vision");
        game.getServer().runCommand("gamerule log_admin_commands true");
        game.getServer().runCommand("gamerule send_command_feedback true");
        game.getServer().runCommand("tp @a %d.5 %d %d.5 -90 -12".formatted(home.getX(), home.getY(), home.getZ()));
        game.getServer().runCommand("time set 14600");
        context.waitTicks(20);
    }

    /** Selects a node in the open core window (as a click would) and waits a moment. */
    private static void selectNode(ClientGameTestContext context, String id) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("selectedId");
                field.setAccessible(true);
                field.set(null, id);
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not select " + id + ": " + e);
            }
        });
        context.waitTicks(10);
    }

    /** The new warrior tree: picks a few nodes and photographs the window. */
    private static void warriorWindow(ClientGameTestContext context, TestSingleplayerContext game) {
        // The window opens on the class overview now: show the warrior's tree directly.
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field tab = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                tab.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object value = Enum.valueOf((Class) tab.getType(), "WARRIOR");
                tab.set(null, value);
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not set the tab: " + e);
            }
        });
        context.runOnClient(client -> {
            net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
            server.execute(() -> {
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                com.mki.nightsiege.game.SiegeManager manager = com.mki.nightsiege.game.SiegeManager.get();
                manager.onCoreAction(player, "class:WARRIOR");
                manager.onCoreAction(player, "dev:400");
                for (String id : subclassFirst("warrior_drill", "w_endurance", "champion_bane", "w_combo", "w_combo",
                        "keen_edge", "w_bladefrenzy", "w_frenzy", "w_evasion", "execute", "w_chain_execute")) {
                    manager.onBuySkill(player, id);
                }
                manager.onCoreAction(player, "open");
            });
        });
        context.waitTicks(40);
        context.takeScreenshot("ui_warrior_blade");
        zoomShots(context, "WARRIOR", "warrior");
        selectNode(context, "w_bladefrenzy");
        context.takeScreenshot("ui_warrior_selected");
        // The key line in the info panel (26 October 2026): an ult with a target, an ult upgrade.
        selectNode(context, "execute");
        context.takeScreenshot("ui_key_execute");
        selectNode(context, "w_chain_execute");
        context.takeScreenshot("ui_key_mastery");
        context.runOnClient(client -> {
            net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
            server.execute(() -> {
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                com.mki.nightsiege.game.SiegeManager manager = com.mki.nightsiege.game.SiegeManager.get();
                manager.onCoreAction(player, "dev:reset");
                manager.onCoreAction(player, "class:ARCHER");
                manager.onCoreAction(player, "dev:400");
                for (String id : subclassFirst("archer_focus", "a_quiver", "heavy_bolts", "a_netbolt", "a_netbolt",
                        "a_shackle", "x_mark", "a_hunternet", "a_reloadrush", "x_hunt", "x_pack")) {
                    manager.onBuySkill(player, id);
                }
                manager.onCoreAction(player, "open");
            });
        });
        context.waitTicks(20);
        context.runOnClient(client -> {
            // Show the player's own (new) class tab when the window refreshes.
            try {
                java.lang.reflect.Field tab = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                tab.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object archer = Enum.valueOf((Class) tab.getType(), "ARCHER");
                tab.set(null, archer);
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not reset the tab: " + e);
            }
        });
        context.runOnClient(client -> {
            net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
            server.execute(() -> com.mki.nightsiege.game.SiegeManager.get().onCoreAction(
                    server.getPlayerList().getPlayers().get(0), "open"));
        });
        context.waitTicks(40);
        selectNode(context, "a_hunternet");
        context.takeScreenshot("ui_archer_crossbow");
        zoomShots(context, "ARCHER", "archer");
        context.waitTicks(5);
    }

    /** Hovering a node marks with a red cross what learning it would shut. */
    private static void doomScene(ClientGameTestContext context) {
        openTree(context, "WARRIOR", "warrior_drill");
        selectNode(context, "w_bladedance");
        context.takeScreenshot("ui_doom_way");
        openTree(context, "WARRIOR", "warrior_drill", "champion_bane");
        zoomTo(context, "WARRIOR", -150.0, -40.0, 0.6);
        selectNode(context, "w_combo");
        context.takeScreenshot("ui_doom_subclass");
        selectNode(context, null);
        closeScreen(context);
    }

    /**
     * A small walled yard with a door and a core: the scan finds the door as the
     * gap; with the door bricked up the yard is closed and the monsters of a test
     * night spawn outside the walls.
     */
    private static void baseScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 22;
        int cy = pos.getY();
        int cz = pos.getZ();
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block"
                .formatted(cx - 12, cy - 1, cz - 12, cx + 12, cy - 1, cz + 12));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air"
                .formatted(cx - 12, cy, cz - 12, cx + 12, cy + 8, cz + 12));
        int r = 6;
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx + r, cy + 2, cz - r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz + r, cx + r, cy + 2, cz + r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx - r, cy + 2, cz + r));
        game.getServer().runCommand(wall.formatted(cx + r, cy, cz - r, cx + r, cy + 2, cz + r));
        game.getServer().runCommand("setblock %d %d %d minecraft:oak_door[half=lower,facing=east]".formatted(cx + r, cy, cz));
        game.getServer().runCommand("setblock %d %d %d minecraft:oak_door[half=upper,facing=east]".formatted(cx + r, cy + 1, cz));
        context.waitTicks(5);
        net.minecraft.core.BlockPos core = new net.minecraft.core.BlockPos(cx, cy, cz);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.setBlock(core, com.mki.nightsiege.game.ModContent.SIEGE_CORE.defaultBlockState(), 3);
            manager.onCorePlaced(level, core, player);
            com.mki.nightsiege.game.BaseDefense.forgetScans();
            com.mki.nightsiege.game.BaseScan scan = com.mki.nightsiege.game.BaseDefense.scanFor(level, core, manager.config());
            System.out.println("[Night Siege test] base with door: closed=" + scan.closed + " gap=" + scan.gap
                    + " size=" + scan.width() + "x" + scan.depth());
        });
        context.waitTicks(5);
        showTab(context, "CORE");
        context.takeScreenshot("ui_core_base_door");
        closeScreen(context);
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone_bricks"
                .formatted(cx + r, cy, cz, cx + r, cy + 1, cz));
        context.waitTicks(5);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.BaseDefense.forgetScans();
            com.mki.nightsiege.game.BaseScan scan = com.mki.nightsiege.game.BaseDefense.scanFor(level, core, manager.config());
            System.out.println("[Night Siege test] base closed up: closed=" + scan.closed + " gap=" + scan.gap
                    + " size=" + scan.width() + "x" + scan.depth());
            manager.startTest(level.getServer(), level, core, 3, com.mki.nightsiege.core.SiegeEvent.NONE);
        });
        context.waitTicks(30);
        showTab(context, "CORE");
        context.takeScreenshot("ui_core_base_closed");
        closeScreen(context);
        // The siege bar with the kill goal.
        context.waitTicks(25);
        context.takeScreenshot("siege_bar_kills");
        // Everything a night breaks comes back after it (Simon, 27 October 2026): a blast with a torch and
        // a sign in it, a burnt plank, a trampled field. The chest outlasts the blast; what a player breaks stays.
        BlockPos fix = new BlockPos(cx, cy, cz + 30);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.level.block.state.BlockState planks = net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState();
            level.setBlock(fix.offset(0, 0, 1), planks, 3);
            level.setBlock(fix.offset(0, 1, 1), net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState(), 3);
            level.setBlock(fix.offset(1, 0, 0), net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState(), 3);
            if (level.getBlockEntity(fix.offset(1, 0, 0)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                sign.setText(sign.getFrontText().setMessage(0, net.minecraft.network.chat.Component.literal("Halt")), true);
            }
            level.setBlock(fix.offset(-1, 0, 0), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(fix.offset(-1, 0, 0)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
            }
            level.setBlock(fix.offset(-4, 0, 0), planks, 3);
            level.setBlock(fix.offset(4, -1, 0), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState(), 3);
            level.setBlock(fix.offset(0, 0, -8), planks, 3);
            int before = com.mki.nightsiege.game.NightDamage.lostCount();
            // As the night's attackers (since 31.10. only their damage is written down).
            com.mki.nightsiege.game.NightDamage.asAttacker(() -> {
                level.explode(null, fix.getX() + 0.5, fix.getY() + 0.5, fix.getZ() + 0.5, 3.0f,
                        net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                level.setBlock(fix.offset(-4, 1, 0), net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
                level.setBlock(fix.offset(-4, 0, 0), net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);   // burnt
                level.setBlock(fix.offset(4, -1, 0), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);   // trampled
            });
            com.mki.nightsiege.game.NightDamage.playerBegin();
            level.destroyBlock(fix.offset(0, 0, -8), false);   // a player's own (out of the blast)
            com.mki.nightsiege.game.NightDamage.playerEnd();
            System.out.println("[Night Siege test] repair: recording=" + com.mki.nightsiege.game.NightDamage.recording(level)
                    + " lost " + before + " -> " + com.mki.nightsiege.game.NightDamage.lostCount()
                    + " chest after blast=" + level.getBlockState(fix.offset(-1, 0, 0)).getBlock());
        });
        context.waitTicks(5);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.BaseScan scan = com.mki.nightsiege.game.BaseDefense.scanFor(level, core, manager.config());
            int inside = 0;
            int outside = 0;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e.entityTags().contains(com.mki.nightsiege.game.MobFactory.TAG)) {
                    if (scan.contains(e.getBlockX(), e.getBlockZ())) {
                        inside++;
                    } else {
                        outside++;
                    }
                }
            }
            System.out.println("[Night Siege test] siege mobs inside=" + inside + " outside=" + outside);
            manager.abort(level.getServer());
            try {
                for (String file : new String[]{com.mki.nightsiege.game.NightLog.NIGHT_FILE, com.mki.nightsiege.game.NightLog.PLAYER_FILE}) {
                    for (String l : java.nio.file.Files.readAllLines(com.mki.nightsiege.game.Json.configFile(file))) {
                        System.out.println("[Night Siege test] " + file + ": " + l);
                    }
                }
                for (net.minecraft.network.chat.Component c : com.mki.nightsiege.game.BalanceReport.build(manager.config(), true)) {
                    System.out.println("[Night Siege test] balance: " + c.getString());
                }
            } catch (java.io.IOException e) {
                System.out.println("[Night Siege test] no protocol: " + e);
            }
            for (int lv : new int[]{3, 7, 12}) {
                manager.giveKits(level.getServer(), level, player.blockPosition(), lv, true);
                StringBuilder worn = new StringBuilder();
                for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                        net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                        net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                    net.minecraft.world.item.ItemStack st = player.getItemBySlot(slot);
                    worn.append(st.getItem()).append(st.isEnchanted() ? "*" : "").append(st.hasFoil() ? "(glint)" : "").append(' ');
                }
                System.out.println("[Night Siege test] kit " + lv + ": " + worn + "points="
                        + manager.save().skillsOf(player.getUUID().toString()).earned);
            }
        });
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            StringBuilder bad = new StringBuilder();
            java.util.function.BiConsumer<String, Boolean> want = (what, ok) -> {
                if (!ok) {
                    bad.append(what).append("; ");
                }
            };
            want.accept("planks", level.getBlockState(fix.offset(0, 0, 1)).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS));
            want.accept("torch", level.getBlockState(fix.offset(0, 1, 1)).is(net.minecraft.world.level.block.Blocks.TORCH));
            want.accept("ground", !level.getBlockState(fix.below()).isAir());
            want.accept("sign", level.getBlockEntity(fix.offset(1, 0, 0)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
                    && "Halt".equals(sign.getFrontText().getMessage(0, false).getString()));
            want.accept("chest", level.getBlockEntity(fix.offset(-1, 0, 0)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
                    && chest.getItem(0).is(net.minecraft.world.item.Items.DIAMOND));
            want.accept("burnt plank", level.getBlockState(fix.offset(-4, 0, 0)).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS));
            want.accept("fire out", !level.getBlockState(fix.offset(-4, 1, 0)).is(net.minecraft.world.level.block.Blocks.FIRE));
            want.accept("field", level.getBlockState(fix.offset(4, -1, 0)).is(net.minecraft.world.level.block.Blocks.FARMLAND));
            want.accept("player's own stays broken", level.getBlockState(fix.offset(0, 0, -8)).isAir());
            System.out.println("[Night Siege test] repair after the night: " + (bad.length() == 0 ? "all back" : "missing " + bad));
        });
        context.waitTicks(10);
        // A test night starts after its preparation: traps first, then the first wave.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            manager.config().testPrepSeconds = 3;
            boolean ok = manager.prepareTest(level.getServer(), level, core, 3, com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] prep: ok=" + ok + " preparing=" + manager.isPreparing()
                    + " active=" + manager.isActive());
        });
        context.waitTicks(20);
        context.takeScreenshot("test_prep_bar");
        context.waitTicks(60);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] prep done: preparing=" + manager.isPreparing()
                    + " active=" + manager.isActive());
            manager.abort(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
            manager.config().testPrepSeconds = 60;
        });
        context.waitTicks(10);
    }

    /** The sixteen class outfits on armour stands named after the subclasses, then on the player. */
    private static void armorScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        // Command echoes would cover the pictures.
        game.getServer().runCommand("gamerule sendCommandFeedback false");
        game.getServer().runCommand("gamerule send_command_feedback false");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(x - 12, y - 1, z - 7, x + 1, y - 1, z + 7));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(x - 12, y, z - 7, x + 1, y + 4, z + 7));
        String[][] rows = {
                {"berserker", "blademaster", "axe", "paladin", "swift", "heavy", "bolter", "trapper"},
                {"shieldfighter", "bulwark", "medic", "warden", "gunner", "electrician", "demolition", "alchemist"}};
        game.getServer().runCommand("tp @p %d %d %d 90 8".formatted(x - 1, y, z));
        for (int r = 0; r < rows.length; r++) {
            game.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
            for (int i = 0; i < rows[r].length; i++) {
                game.getServer().runCommand(String.format(java.util.Locale.ROOT,
                        "summon minecraft:armor_stand %d %d %.1f {CustomName:\"%s\",ShowArms:1b,Rotation:[-90f,0f],"
                        + "equipment:{head:{id:\"minecraft:iron_helmet\"},chest:{id:\"minecraft:iron_chestplate\"},"
                        + "legs:{id:\"minecraft:iron_leggings\"},feet:{id:\"minecraft:iron_boots\"}}}",
                        x - 6, y, z - 3.5 + i, rows[r][i]));
            }
            context.waitTicks(20);
            context.getInput().lookAt(90.0f, 8.0f);
                context.waitTicks(10);
            context.takeScreenshot("armor_row_" + (r + 1));
        }
        // Every outfit up close, four at a time, from the front and from the back.
        String[] all = {"berserker", "blademaster", "axe", "paladin", "swift", "heavy", "bolter", "trapper",
                "shieldfighter", "bulwark", "medic", "warden", "gunner", "electrician", "demolition", "alchemist",
                "tamer", "golemsmith", "necromancer", "soulcatcher"};
        // Chat lines and toasts fade after ten seconds; wait so the pictures stay clear.
        game.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
        context.waitTicks(230);
        for (int g = 0; g < 5; g++) {
            for (int side = 0; side < 2; side++) {
                game.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
                for (int i = 0; i < 4; i++) {
                    // Since 27 October 2026 each stand also holds its subclass's kit weapon.
                    game.getServer().runCommand(String.format(java.util.Locale.ROOT,
                            "summon minecraft:armor_stand %d %d %.1f {CustomName:\"%s\",ShowArms:1b,Rotation:[%df,0f],"
                            + "equipment:{head:{id:\"minecraft:iron_helmet\"},chest:{id:\"minecraft:iron_chestplate\"},"
                            + "legs:{id:\"minecraft:iron_leggings\"},feet:{id:\"minecraft:iron_boots\"},%s}}",
                            x - 5, y, z - 2.25 + i * 1.5, all[g * 4 + i], side == 0 ? -90 : 90, kitHand(all[g * 4 + i])));
                }
                context.waitTicks(15);
                context.getInput().lookAt(90.0f, 12.0f);
                context.waitTicks(5);
                context.takeScreenshot("armor_close_%d_%s".formatted(g + 1, side == 0 ? "front" : "back"));
            }
            // The same four with the head turned and bent: collars must not cut into the head.
            game.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
            for (int i = 0; i < 4; i++) {
                game.getServer().runCommand(String.format(java.util.Locale.ROOT,
                        "summon minecraft:armor_stand %d %d %.1f {CustomName:\"%s\",ShowArms:1b,Rotation:[-90f,0f],"
                        + "Pose:{Head:[%df,%df,0f]},"
                        + "equipment:{head:{id:\"minecraft:iron_helmet\"},chest:{id:\"minecraft:iron_chestplate\"},"
                        + "legs:{id:\"minecraft:iron_leggings\"},feet:{id:\"minecraft:iron_boots\"}}}",
                        x - 5, y, z - 2.25 + i * 1.5, all[g * 4 + i], i % 2 == 0 ? 30 : -20, i < 2 ? 45 : -45));
            }
            context.waitTicks(15);
            context.getInput().lookAt(90.0f, 12.0f);
            context.waitTicks(5);
            context.takeScreenshot("armor_close_%d_turned".formatted(g + 1));
        }
        game.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
        // The player as a berserker, seen from the front.
        onServer(context, (player, manager) -> {
            manager.onCoreAction(player, "dev:reset");
            manager.onCoreAction(player, "class:WARRIOR");
            manager.onCoreAction(player, "dev:600");
            for (String id : subclassFirst("warrior_drill", "champion_bane", "bloodlust")) {
                manager.onBuySkill(player, id);
            }
            manager.onCoreAction(player, "open");
        });
        context.waitTicks(10);
        closeScreen(context);
        for (String slot : new String[]{"head:iron_helmet", "chest:iron_chestplate", "legs:iron_leggings", "feet:iron_boots"}) {
            String[] p = slot.split(":");
            // Enchanted on purpose: the outfit must not show the glint.
            game.getServer().runCommand("item replace entity @p armor.%s with minecraft:%s[minecraft:enchantments={\"minecraft:protection\":2}]"
                    .formatted(p[0], p[1]));
        }
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(20);
        context.waitTicks(2);
        context.takeScreenshot("armor_player_berserker");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        for (String slot : new String[]{"head", "chest", "legs", "feet"}) {
            game.getServer().runCommand("item replace entity @p armor.%s with minecraft:air".formatted(slot));
        }
        game.getServer().runCommand("tp @p %d %d %d -90 0".formatted(x, y, z));
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("gamerule sendCommandFeedback true");
        game.getServer().runCommand("gamerule send_command_feedback true");
        context.waitTicks(10);
    }

    /**
     * Not signed up, the mod leaves the player alone: the core only offers the sign-up.
     * Then the player signs up (straight in the save: there is no core yet).
     */
    private static void registrationScene(ClientGameTestContext context) {
        onServer(context, (player, manager) -> manager.onCoreAction(player, "open"));
        context.waitTicks(10);
        context.takeScreenshot("chat_register_offer");
        onServer(context, (player, manager) -> {
            manager.save().register(player.getUUID().toString());
            System.out.println("[Night Siege test] registered=" + manager.isRegistered(player));
        });
        context.waitTicks(5);
    }

    /** The morning invitation with its buttons, an accept, and the situation tab during the vote. */
    private static void inviteScene(ClientGameTestContext context, TestSingleplayerContext game) {
        game.getServer().runCommand("time set 2000");
        context.waitTicks(5);
        onServer(context, (player, manager) -> {
            long day = com.mki.nightsiege.core.SiegeMath.dayOf(
                    player.level().getServer().overworld().getOverworldClockTime());
            manager.save().nextSiegeDay = day;
            manager.save().voteDay = day;
            manager.save().clearVotes();
            manager.sendInvitation(player);
        });
        context.waitTicks(10);
        context.takeScreenshot("chat_invitation");
        onServer(context, (player, manager) -> manager.vote(player, true));
        context.waitTicks(10);
        context.takeScreenshot("chat_vote_yes");
        showTab(context, "STATS");
        context.takeScreenshot("ui_rhythm_vote");
        closeScreen(context);
        onServer(context, (player, manager) -> {
            long day = com.mki.nightsiege.core.SiegeMath.dayOf(
                    player.level().getServer().overworld().getOverworldClockTime());
            System.out.println("[Night Siege test] vote: open=" + manager.voteOpen(player.level().getServer())
                    + " accepted=" + manager.acceptedCount());
            manager.save().nextSiegeDay = day + 3;
            manager.save().voteDay = -1;
            manager.save().clearVotes();
        });
        context.waitTicks(5);
        showTab(context, "STATS");
        context.takeScreenshot("ui_rhythm_days");
        closeScreen(context);
    }

    /** The shared core tree as a star map, with a few nodes bought from the pot. */
    private static void coreTreeScene(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("VIEWS");
                field.setAccessible(true);
                ((java.util.Map<?, ?>) field.get(null)).remove("CORE");
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not reset the view: " + e);
            }
        });
        onServer(context, (player, manager) -> {
            manager.onCoreAction(player, "dev:80");
            for (String id : new String[]{"masonry", "masonry", "battlements", "fortified", "fortified"}) {
                manager.onBuySkill(player, id);
            }
        });
        context.waitTicks(10);
        selectNode(context, "rebuild");
        showTab(context, "CORE");
        context.takeScreenshot("ui_core_tree");
        selectNode(context, null);
        context.waitTicks(10);
        context.takeScreenshot("ui_core_tree_overview");
        closeScreen(context);
    }

    /**
     * A closed yard with a ladder on the outside of its wall: a husk outside wants to the
     * middle of the yard, finds the ladder, climbs it and drops in on the other side.
     */
    private static void ladderScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int gx = pos.getX() + 4;
        int gy = pos.getY();
        int gz = pos.getZ() + 26;
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(gx - 14, gy - 1, gz - 8, gx + 6, gy - 1, gz + 8));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(gx - 14, gy, gz - 8, gx + 6, gy + 8, gz + 8));
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        int r = 3;
        game.getServer().runCommand(wall.formatted(gx - r, gy, gz - r, gx + r, gy + 3, gz - r));
        game.getServer().runCommand(wall.formatted(gx - r, gy, gz + r, gx + r, gy + 3, gz + r));
        game.getServer().runCommand(wall.formatted(gx - r, gy, gz - r, gx - r, gy + 3, gz + r));
        game.getServer().runCommand(wall.formatted(gx + r, gy, gz - r, gx + r, gy + 3, gz + r));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:ladder[facing=west]"
                .formatted(gx - r - 1, gy, gz, gx - r - 1, gy + 3, gz));
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {PersistenceRequired:1b,Tags:[\"ns_ladder\"],"
                + "CustomName:\"Kletterer\",CustomNameVisible:1b}").formatted(gx - 10, gy, gz + 2));
        // Watch from the side, a little above.
        game.getServer().runCommand("setblock %d %d %d minecraft:barrier".formatted(gx - 4, gy + 3, gz - 12));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 22".formatted(gx - 4, gy + 4, gz - 12));
        context.waitTicks(20);
        final net.minecraft.core.BlockPos goal = new net.minecraft.core.BlockPos(gx, gy, gz);
        final com.mki.nightsiege.game.LadderClimb ladders = new com.mki.nightsiege.game.LadderClimb();
        for (int i = 0; i < 160; i++) {
            final int step = i;
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (e.entityTags().contains("ns_ladder")) {
                        ladders.tick(level, java.util.List.of(e.getUUID()), goal, level.getGameTime());
                        if (step % 40 == 0) {
                            System.out.println("[Night Siege test] ladder " + step + ": at " + e.blockPosition()
                                    + " climbing=" + ((net.minecraft.world.entity.LivingEntity) e).onClimbable());
                        }
                    }
                }
            });
            context.waitTicks(2);
            if (i == 30 || i == 60 || i == 100) {
                context.takeScreenshot("ladder_climb_" + (i / 30));
            }
        }
        context.takeScreenshot("ladder_climb_end");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e.entityTags().contains("ns_ladder")) {
                    boolean inside = Math.abs(e.getBlockX() - goal.getX()) < 3 && Math.abs(e.getBlockZ() - goal.getZ()) < 3;
                    System.out.println("[Night Siege test] ladder result: at " + e.blockPosition() + " inside=" + inside);
                    e.discard();
                }
            }
        });
        context.waitTicks(5);
    }

    /** The kit weapon of a subclass as an armour stand's main hand (NBT). */
    private static String kitHand(String subclass) {
        com.mki.nightsiege.core.KitWeapons.Look look = com.mki.nightsiege.core.KitWeapons.of(subclass);
        if (look == null) {
            return "mainhand:{id:\"minecraft:iron_sword\",count:1}";
        }
        String hand = "mainhand:{id:\"" + kitBaseItem(look) + "\",count:1,components:{\"minecraft:item_model\":\"" + look.itemModel() + "\"}}";
        String shield = com.mki.nightsiege.game.ClassShields.componentsSnbt(subclass);
        if (!shield.isEmpty()) {
            hand += ",offhand:{id:\"minecraft:shield\",count:1,components:{" + shield + "}}";   // the subclass's shield (27 Oct 2026)
        }
        return hand;
    }

    private static String kitBaseItem(com.mki.nightsiege.core.KitWeapons.Look look) {
        return switch (look.kind()) {
            case SWORD -> "minecraft:iron_sword";
            case AXE -> "minecraft:iron_axe";
            case BOW -> "minecraft:bow";
            case CROSSBOW -> "minecraft:crossbow";
            case STAFF -> "nightsiege:summoning_staff";
        };
    }

    /** All 20 kit weapons in the inventory, and a few in the hand (Simon, 27 October 2026). */
    private static void kitWeaponScene(ClientGameTestContext context, TestSingleplayerContext game) {
        game.getServer().runCommand("clear @p");
        game.getServer().runCommand("time set 6000");
        int slot = 0;
        for (com.mki.nightsiege.core.KitWeapons.Look look : com.mki.nightsiege.core.KitWeapons.all()) {
            game.getServer().runCommand("item replace entity @p %s with %s[minecraft:item_model=\"%s\",minecraft:item_name=%s]"
                    .formatted(slot < 9 ? "hotbar." + slot : "inventory." + (slot - 9), kitBaseItem(look), look.itemModel(),
                            "{translate:\"" + look.nameKey() + "\"}"));
            slot++;
        }
        context.waitTicks(10);
        context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
        context.waitTicks(10);
        context.takeScreenshot("kit_weapons_inventory");
        closeScreen(context);
        context.waitTicks(5);
        for (int i : new int[] {0, 4, 6}) {
            final int hotbar = i;
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(hotbar));
            context.getInput().lookAt(90.0f, 20.0f);
            context.waitTicks(10);
            context.takeScreenshot("kit_weapons_hand_" + i);
        }
        boolean[] named = {false};
        context.runOnClient(client -> named[0] = client.player.getInventory().getItem(0).getHoverName().getString().equals(
                net.minecraft.network.chat.Component.translatable("nightsiege.kitweapon.blademaster_saber").getString()));
        System.out.println("[Night Siege test] kit weapons: named=" + named[0]);
        game.getServer().runCommand("clear @p");
        balanceFightScene(context, game);   // balance update 2 (29 October 2026): life steal, champions, kit storage
    }

    /** The slot of a menu that shows this slot of the player's inventory, or -1. */
    static int menuSlotOf(net.minecraft.world.inventory.AbstractContainerMenu menu,
                                  net.minecraft.world.entity.player.Inventory inv, int invSlot) {
        for (int i = 0; i < menu.slots.size(); i++) {
            net.minecraft.world.inventory.Slot s = menu.slots.get(i);
            if (s.container == inv && s.getContainerSlot() == invSlot) {
                return i;
            }
        }
        return -1;
    }

    /** Kit items in a container, counted by stack size. */
    static int kitCount(net.minecraft.world.Container box) {
        int n = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            if (com.mki.nightsiege.game.KitService.isKit(box.getItem(i))) {
                n += box.getItem(i).getCount();
            }
        }
        return n;
    }

    /**
     * Balance update 2 (Simon, 29 October 2026): the hit life steal on level 1 against level 15 and its
     * floor of half a heart, a dungeon champion's blow and shot with the share of the max health (half
     * through a shield), and kit items that go into no chest, no vase, no bundle and vanish when dropped.
     */
    private static void balanceFightScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 180;
        int cz = pos.getZ() - 120;
        int cy = yard(context, game, cx, cz, 16, 8);
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("clear @p");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 0".formatted(cx, cy, cz));
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"ns_fight\",\"ns_fight_dummy\"],"
                + "attributes:[{id:\"minecraft:max_health\",base:1000.0d}],Health:1000.0f}").formatted(cx + 4, cy, cz + 4));
        context.waitTicks(20);
        java.util.List<String> fails = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        int[] savedWon = {0};

        // 1. Life steal of the axe fighter: level 1 (×2.5) against level 15 (×1), then the floor of 1 life point.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.switchTo(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.levels.clear();
            own.levels.put("heavy_blow", 1);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
            net.minecraft.world.entity.LivingEntity dummy = null;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e.entityTags().contains("ns_fight_dummy") && e instanceof net.minecraft.world.entity.LivingEntity l) {
                    dummy = l;
                }
            }
            if (dummy == null) {
                fails.add("no training dummy");
                return;
            }
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            String sub = manager.skillTree().subclassOf(own);
            savedWon[0] = manager.save().siegesWon;
            int[] levels = {1, 15};
            double[] ratio = new double[2];
            for (int i = 0; i < levels.length; i++) {
                manager.save().siegesWon = levels[i] - 1;
                player.setHealth(2f);
                float before = dummy.getHealth();
                dummy.invulnerableTime = 0;
                dummy.hurtServer(level, level.damageSources().playerAttack(player), 30f);
                float dealt = before - dummy.getHealth();
                float healed = player.getHealth() - 2f;
                ratio[i] = dealt > 0 ? healed / dealt : 0;
                System.out.println(String.format(java.util.Locale.ROOT,
                        "[Night Siege test] fight lifesteal level %d (now %d) sub=%s dealt %.2f healed %.2f share %.3f expect %.3f",
                        levels[i], manager.currentLevel(), sub, dealt, healed, ratio[i],
                        cfg.hitLifesteal(sub) * cfg.hitLifestealLevelFactor(levels[i])));
            }
            if (!"axe".equals(sub)) {
                fails.add("lifesteal test is no axe fighter: " + sub);
            }
            if (ratio[1] < 0.045 || ratio[1] > 0.075) {
                fails.add(String.format(java.util.Locale.ROOT, "life steal on level 15 is %.3f of the hit, not 0.06", ratio[1]));
            }
            if (ratio[1] <= 0 || ratio[0] / ratio[1] < 2.2 || ratio[0] / ratio[1] > 2.8) {
                fails.add(String.format(java.util.Locale.ROOT, "life steal level 1 / level 15 = %.2f, not 2.5",
                        ratio[1] <= 0 ? 0 : ratio[0] / ratio[1]));
            }
            // Two small hits at once: the first heals the floor (1 life point), the second only its share.
            float[] small = new float[2];
            for (int i = 0; i < 2; i++) {
                player.setHealth(2f);
                dummy.invulnerableTime = 0;
                dummy.hurtServer(level, level.damageSources().playerAttack(player), 2f);
                small[i] = player.getHealth() - 2f;
            }
            System.out.println(String.format(java.util.Locale.ROOT,
                    "[Night Siege test] fight lifesteal floor: first small hit healed %.2f, second %.2f (floor %.1f every %d ticks)",
                    small[0], small[1], cfg.hitLifestealMinHeal, cfg.hitLifestealMinTicks));
            if (Math.abs(small[0] - cfg.hitLifestealMinHeal) > 0.05) {
                fails.add(String.format(java.util.Locale.ROOT, "the floor heal was %.2f, not %.1f", small[0], cfg.hitLifestealMinHeal));
            }
            if (small[1] >= cfg.hitLifestealMinHeal - 0.05 || small[1] <= 0f) {
                fails.add(String.format(java.util.Locale.ROOT, "the second small hit healed %.2f (the floor must wait)", small[1]));
            }
            // The formula alone: what the config says for a hit of 10 on level 1 and 15.
            double h1 = com.mki.nightsiege.game.CombatEffects.hitHeal(cfg, "axe", 10, 0, 1, false);
            double h15 = com.mki.nightsiege.game.CombatEffects.hitHeal(cfg, "axe", 10, 4, 15, false);
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] fight lifesteal formula: hit 10 on level 1 heals %.2f,"
                    + " on level 15 with 4 cleaved %.2f", h1, h15));
            if (Math.abs(h1 - 1.5) > 1e-6 || Math.abs(h15 - (0.6 + 4 * 0.06 * 0.5)) > 1e-6) {
                fails.add("life steal formula " + h1 + " / " + h15);
            }
            manager.save().siegesWon = savedWon[0];
            own.levels.clear();
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(10);
        game.getServer().runCommand("kill @e[tag=ns_fight]");

        // 2. A dungeon champion's blow and arrow take the share on top; a plain husk does not.
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"ns_fight\",\"ns_fight_champion\",\"nightsiege_warden\"]}")
                .formatted(cx, cy, cz + 2));
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"ns_fight\",\"ns_fight_plain\"]}")
                .formatted(cx + 3, cy, cz + 2));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 facing %d.5 %d %d.5".formatted(cx, cy, cz, cx, cy + 1, cz + 2));
        game.getServer().runCommand("gamemode survival @p");
        context.waitTicks(20);
        float[] share = new float[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob champion = null;
            net.minecraft.world.entity.Mob plain = null;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof net.minecraft.world.entity.Mob mob && e.entityTags().contains("ns_fight_champion")) {
                    champion = mob;
                } else if (e instanceof net.minecraft.world.entity.Mob mob && e.entityTags().contains("ns_fight_plain")) {
                    plain = mob;
                }
            }
            if (champion == null || plain == null) {
                fails.add("champion or plain husk missing");
                return;
            }
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            float max = player.getMaxHealth();
            share[0] = (float) (max * cfg.championPercent(manager.currentLevel()) * manager.damageTakenFactor(player));
            float[] took = new float[4];
            net.minecraft.world.entity.Mob[] by = {plain, champion};
            for (int i = 0; i < 2; i++) {
                player.setHealth(max);
                player.invulnerableTime = 0;
                by[i].doHurtTarget(level, player);
                took[i] = max - player.getHealth();
            }
            for (int i = 0; i < 2; i++) {
                player.setHealth(max);
                player.invulnerableTime = 0;
                net.minecraft.world.entity.projectile.arrow.Arrow arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level, by[i],
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
                player.hurtServer(level, level.damageSources().arrow(arrow, by[i]), 2f);
                took[2 + i] = max - player.getHealth();
                arrow.discard();
            }
            System.out.println(String.format(java.util.Locale.ROOT,
                    "[Night Siege test] fight champion level %d share %.2f (%.0f %%): blow plain %.2f champion %.2f, arrow plain %.2f champion %.2f",
                    manager.currentLevel(), share[0], cfg.championPercent(manager.currentLevel()) * 100, took[0], took[1], took[2], took[3]));
            if (took[1] - took[0] < share[0] * 0.9f) {
                fails.add(String.format(java.util.Locale.ROOT, "the champion's blow took %.2f, a plain one %.2f (share %.2f)", took[1], took[0], share[0]));
            }
            if (took[3] - took[2] < share[0] * 0.9f) {
                fails.add(String.format(java.util.Locale.ROOT, "the champion's arrow took %.2f, a plain one %.2f (share %.2f)", took[3], took[2], share[0]));
            }
            player.setHealth(max);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
            player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
        });
        context.waitTicks(12);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob champion = null;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof net.minecraft.world.entity.Mob mob && e.entityTags().contains("ns_fight_champion")) {
                    champion = mob;
                }
            }
            float max = player.getMaxHealth();
            player.setHealth(max);
            player.invulnerableTime = 0;
            boolean blocking = player.isBlocking();
            if (champion != null) {
                champion.doHurtTarget(level, player);
            }
            float took = max - player.getHealth();
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] fight champion on a shield: blocking=%s took %.2f (half share %.2f)",
                    blocking, took, share[0] * manager.config().championPercentShield));
            if (blocking && Math.abs(took - share[0] * manager.config().championPercentShield) > 0.15f + share[0] * 0.1f) {
                fails.add(String.format(java.util.Locale.ROOT, "on a shield the champion took %.2f, not half the share %.2f", took,
                        share[0] * manager.config().championPercentShield));
            }
            player.stopUsingItem();
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
            player.setHealth(max);
        });
        context.waitTicks(5);
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("kill @e[tag=ns_fight]");

        // 3. Kit items: not into a chest (click, shift-click, number key), not into a vase, not into a bundle,
        //    free inside the own inventory, gone when dropped; left-overs go when the kit ends.
        BlockPos chestPos = new BlockPos(cx + 2, cy, cz + 3);
        BlockPos potPos = new BlockPos(cx - 2, cy, cz + 3);
        game.getServer().runCommand("setblock %d %d %d minecraft:chest".formatted(chestPos.getX(), chestPos.getY(), chestPos.getZ()));
        game.getServer().runCommand("setblock %d %d %d minecraft:decorated_pot".formatted(potPos.getX(), potPos.getY(), potPos.getZ()));
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            inv.clearContent();
            net.minecraft.world.item.ItemStack kit = com.mki.nightsiege.game.KitService.mark(
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 3));
            inv.setItem(0, kit.copy());
            inv.setSelectedSlot(0);
            if (!(level.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)) {
                fails.add("no chest for the kit test");
                return;
            }
            player.openMenu(chest);
            net.minecraft.world.inventory.AbstractContainerMenu menu = player.containerMenu;
            int hot0 = menuSlotOf(menu, inv, 0);
            menu.clicked(hot0, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
            int afterShift = kitCount(chest);
            menu.clicked(hot0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            menu.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            int afterPlace = kitCount(chest);
            menu.clicked(hot0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            menu.clicked(1, 0, net.minecraft.world.inventory.ContainerInput.SWAP, player);
            int afterSwap = kitCount(chest);
            player.closeContainer();
            boolean back = com.mki.nightsiege.game.KitService.isKit(inv.getItem(0)) && inv.getItem(0).getCount() == 3;
            System.out.println("[Night Siege test] fight kit chest: menu slot " + hot0 + " shift=" + afterShift + " place=" + afterPlace
                    + " swap=" + afterSwap + " kept=" + back);
            if (hot0 < 0 || afterShift + afterPlace + afterSwap > 0 || !back) {
                fails.add("a kit item went into the chest (shift " + afterShift + ", place " + afterPlace + ", swap " + afterSwap + ", kept " + back + ")");
            }
            // The vase: a right-click with the kit bread in hand.
            net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(potPos), net.minecraft.core.Direction.UP, potPos, false);
            net.minecraft.world.InteractionResult used = player.gameMode.useItemOn(player, level, player.getMainHandItem(),
                    net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            int inPot = level.getBlockEntity(potPos) instanceof net.minecraft.world.Container pot ? kitCount(pot) : -1;
            System.out.println("[Night Siege test] fight kit vase: " + used + " in the vase " + inPot + " in hand " + player.getMainHandItem().getCount());
            if (inPot != 0) {
                fails.add("the vase took the kit item (" + inPot + ")");
            }
            // A bundle takes no kit item; inside the own inventory the kit item moves freely.
            inv.clearContent();
            inv.setItem(9, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUNDLE));
            inv.setItem(10, kit.copy());
            net.minecraft.world.inventory.AbstractContainerMenu own = player.inventoryMenu;
            int s9 = menuSlotOf(own, inv, 9);
            int s10 = menuSlotOf(own, inv, 10);
            int s20 = menuSlotOf(own, inv, 20);
            own.clicked(s10, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            own.clicked(s9, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            own.clicked(s20, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            if (!own.getCarried().isEmpty()) {
                inv.placeItemBackInInventory(own.getCarried());
                own.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            }
            int kitLeft = kitCount(inv);
            boolean moved = com.mki.nightsiege.game.KitService.isKit(inv.getItem(9));
            System.out.println("[Night Siege test] fight kit bundle: kit items " + kitLeft + "/3, moved to slot 9=" + moved
                    + " bundle at 20=" + inv.getItem(20).is(net.minecraft.world.item.Items.BUNDLE));
            if (kitLeft != 3) {
                fails.add("the bundle took kit items (" + kitLeft + "/3 left)");
            }
            if (!moved) {
                fails.add("the kit item did not move inside the own inventory");
            }
            // Dropped: gone.
            inv.clearContent();
            net.minecraft.world.entity.item.ItemEntity drop = player.drop(kit.copy(), false, true);
            System.out.println("[Night Siege test] fight kit drop: entity=" + (drop != null));
            if (drop != null) {
                fails.add("a dropped kit item lies on the ground");
            }
            // Left-overs: a kit item in the chest and in an item frame go when the kit ends.
            chest.setItem(4, kit.copy());
            net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(level,
                    new BlockPos(cx - 3, cy, cz - 3), net.minecraft.core.Direction.UP);
            level.addFreshEntity(frame);
            frame.setItem(kit.copy());
            boolean frameRefused = com.mki.nightsiege.game.KitGuard.refusesEntity(player, net.minecraft.world.InteractionHand.MAIN_HAND, frame);
            inv.setItem(0, kit.copy());
            boolean frameRefusedWithKit = com.mki.nightsiege.game.KitGuard.refusesEntity(player, net.minecraft.world.InteractionHand.MAIN_HAND, frame);
            inv.clearContent();
            int removed = com.mki.nightsiege.game.KitGuard.sweepAround(player);
            System.out.println("[Night Siege test] fight kit sweep: removed " + removed + " chest " + kitCount(chest) + " frame "
                    + frame.getItem().getCount() + " frame refuses empty hand=" + frameRefused + " kit hand=" + frameRefusedWithKit);
            if (kitCount(chest) != 0 || !frame.getItem().isEmpty()) {
                fails.add("the sweep left kit items (chest " + kitCount(chest) + ", frame " + frame.getItem().getCount() + ")");
            }
            if (frameRefused || !frameRefusedWithKit) {
                fails.add("item frame guard empty=" + frameRefused + " kit=" + frameRefusedWithKit);
            }
            frame.discard();
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            int lying = 0;
            for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    player.getBoundingBox().inflate(12.0))) {
                if (com.mki.nightsiege.game.KitService.isKit(e.getItem())) {
                    lying++;
                }
            }
            System.out.println("[Night Siege test] fight kit items on the ground: " + lying);
            if (lying > 0) {
                fails.add(lying + " kit items lie on the ground");
            }
        });
        context.waitTicks(5);
        game.getServer().runCommand("setblock %d %d %d minecraft:air".formatted(chestPos.getX(), chestPos.getY(), chestPos.getZ()));
        game.getServer().runCommand("setblock %d %d %d minecraft:air".formatted(potPos.getX(), potPos.getY(), potPos.getZ()));
        game.getServer().runCommand("clear @p");
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(pos.getX(), pos.getY(), pos.getZ()));
        context.waitTicks(20);
        System.out.println("[Night Siege test] fight scene: " + (fails.isEmpty() ? "ok" : String.join("; ", fails)));
        if (!fails.isEmpty()) {
            throw new AssertionError("fight scene: " + String.join("; ", fails));
        }
    }

    /**
     * Simon (27 October 2026): daybreak in the middle of a siege. A test night by day sets dusk on
     * the same day; during the siege the clock stops at 21500; after it the clock runs again.
     */
    private static void nightHoldScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        // The test world stands still (no daylight cycle); here the clock must run.
        game.getServer().runCommand("gamerule advance_time true");
        game.getServer().runCommand("gamerule doDaylightCycle true");
        game.getServer().runCommand("time set 30000");   // day 1, 6000
        context.waitTicks(5);
        final long[] t = new long[8];
        onServer(context, (player, manager) -> {
            net.minecraft.server.MinecraftServer server = ((net.minecraft.server.level.ServerLevel) player.level()).getServer();
            t[0] = server.overworld().getOverworldClockTime();
            manager.ensureNight(server, com.mki.nightsiege.core.SiegeEvent.NONE);
            t[1] = server.overworld().getOverworldClockTime();
            manager.config().testPrepSeconds = 1;
            manager.prepareTest(server, (net.minecraft.server.level.ServerLevel) player.level(),
                    pos, 2, com.mki.nightsiege.core.SiegeEvent.NONE);
        });
        context.waitTicks(50);
        closeScreen(context);
        game.getServer().runCommand("time add 8600");   // past 21500, same day
        context.waitTicks(60);
        onServer(context, (player, manager) -> {
            t[2] = player.level().getServer().overworld().getOverworldClockTime();
            t[5] = manager.isActive() ? 1 : 0;
        });
        context.waitTicks(60);
        onServer(context, (player, manager) -> {
            net.minecraft.server.MinecraftServer server = ((net.minecraft.server.level.ServerLevel) player.level()).getServer();
            t[3] = server.overworld().getOverworldClockTime();
            manager.abort(server);
            manager.config().testPrepSeconds = 60;
        });
        context.waitTicks(60);
        onServer(context, (player, manager) -> {
            t[4] = player.level().getServer().overworld().getOverworldClockTime();
            t[6] = manager.save().clockHeld ? 1 : 0;
        });
        context.waitTicks(5);
        System.out.println("[Night Siege test] night hold: day " + t[0] + " -> test " + t[1] + ", held " + t[2] + "/" + t[3]
                + " (active " + t[5] + "), after " + t[4]);
        game.getServer().runCommand("gamerule advance_time false");
        game.getServer().runCommand("gamerule doDaylightCycle false");
        if (t[1] != 24000 + 13000 || t[5] != 1 || t[2] != 24000 + 21500 || t[3] != t[2] || t[4] <= t[3] || t[6] != 0) {
            throw new AssertionError("night hold: " + java.util.Arrays.toString(t));
        }
        game.getServer().runCommand("time set 6000");
    }

    /**
     * Simon (27 October 2026): a ladder carrier may only build where the way up is free. A wall
     * 5 high with an overhang over one column: there no ladder, next to it the full height.
     */
    private static void ladderOverhangScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int gx = pos.getX() + 200;
        int gz = pos.getZ();
        int gy = groundAt(context, gx, gz);
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(gx - 6, gy - 1, gz - 6, gx + 6, gy - 1, gz + 6));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(gx - 6, gy, gz - 6, gx + 6, gy + 9, gz + 6));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone_bricks".formatted(gx, gy, gz - 4, gx, gy + 4, gz + 4));
        // The overhang: one block sticking out to the west at z = gz, two below the top.
        game.getServer().runCommand("setblock %d %d %d minecraft:stone_bricks".formatted(gx - 1, gy + 3, gz));
        // A roof over z = gz + 3 closes the top there.
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone_bricks".formatted(gx - 1, gy + 6, gz + 3, gx + 1, gy + 6, gz + 3));
        context.waitTicks(10);
        final int[] got = {-9, -9, -9};
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            got[0] = com.mki.nightsiege.game.Besiegers.climbHeight(level, new BlockPos(gx, gy, gz), net.minecraft.core.Direction.WEST);
            got[1] = com.mki.nightsiege.game.Besiegers.climbHeight(level, new BlockPos(gx, gy, gz - 2), net.minecraft.core.Direction.WEST);
            got[2] = com.mki.nightsiege.game.Besiegers.climbHeight(level, new BlockPos(gx, gy, gz + 3), net.minecraft.core.Direction.WEST);
        });
        context.waitTicks(5);
        System.out.println("[Night Siege test] ladder overhang: blocked=" + got[0] + " free=" + got[1] + " roof=" + got[2]);
        if (got[0] != -1 || got[1] != 5 || got[2] != -1) {
            throw new AssertionError("ladder way up: overhang " + got[0] + " (want -1), free " + got[1] + " (want 5), roof " + got[2] + " (want -1)");
        }
    }

    /**
     * Simon (28 October 2026, 22:38): monsters got stuck at ladders with a block beside them and
     * at ladders that end one block above the ground. Four small walled yards side by side, one
     * husk outside each that wants to the middle of its yard:
     * <ul>
     *   <li>side: the outer ladder has blocks beside it (at the ground, half way up, battlements at the top);</li>
     *   <li>hanging: the lowest rung of the outer ladder is one block above the ground;</li>
     *   <li>down: over the outer ladder, and right behind it a ladder inside with blocks beside it;</li>
     *   <li>downhang: the same, the inner ladder ending one block above the floor of the yard.</li>
     * </ul>
     * Each husk must stand on the floor of its yard within the time limit: one PASS/FAIL line each.
     */
    private static void ladderCasesScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        final String[] names = {"side", "hanging", "down", "downhang"};
        final int n = names.length;
        final int r = 3;
        final int limit = 600;   // ticks
        final int[] gx = new int[n];
        final int[] gy = new int[n];
        final int[] gz = new int[n];
        game.getServer().runCommand("time set 6000");
        for (int c = 0; c < n; c++) {
            gx[c] = pos.getX() + 240 + c * 30;
            gz[c] = pos.getZ();
            gy[c] = groundAt(context, gx[c], gz[c]);
            int x = gx[c];
            int y = gy[c];
            int z = gz[c];
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(x - 14, y - 1, z - 8, x + 6, y - 1, z + 8));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(x - 14, y, z - 8, x + 6, y + 8, z + 8));
            String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
            game.getServer().runCommand(wall.formatted(x - r, y, z - r, x + r, y + 3, z - r));
            game.getServer().runCommand(wall.formatted(x - r, y, z + r, x + r, y + 3, z + r));
            game.getServer().runCommand(wall.formatted(x - r, y, z - r, x - r, y + 3, z + r));
            game.getServer().runCommand(wall.formatted(x + r, y, z - r, x + r, y + 3, z + r));
            String block = "setblock %d %d %d minecraft:stone_bricks";
            String outer = "fill %d %d %d %d %d %d minecraft:ladder[facing=west]";
            String inner = "fill %d %d %d %d %d %d minecraft:ladder[facing=east]";
            switch (names[c]) {
                case "side" -> {
                    game.getServer().runCommand(outer.formatted(x - r - 1, y, z, x - r - 1, y + 3, z));
                    game.getServer().runCommand(block.formatted(x - r - 1, y, z + 1));       // right of it at the ground
                    game.getServer().runCommand(block.formatted(x - r - 1, y + 2, z - 1));   // left of it half way up
                    game.getServer().runCommand(block.formatted(x - r, y + 4, z - 1));       // battlements beside the top
                    game.getServer().runCommand(block.formatted(x - r, y + 4, z + 1));
                }
                case "hanging" -> game.getServer().runCommand(outer.formatted(x - r - 1, y + 1, z, x - r - 1, y + 3, z));
                case "down" -> {
                    game.getServer().runCommand(outer.formatted(x - r - 1, y, z, x - r - 1, y + 3, z));
                    game.getServer().runCommand(inner.formatted(x - r + 1, y, z, x - r + 1, y + 3, z));
                    game.getServer().runCommand(block.formatted(x - r + 1, y + 2, z - 1));   // beside the inner ladder
                    game.getServer().runCommand(block.formatted(x - r + 1, y + 3, z - 1));
                    game.getServer().runCommand(block.formatted(x - r + 1, y + 2, z + 1));
                    game.getServer().runCommand(block.formatted(x - r + 1, y + 3, z + 1));
                }
                default -> {
                    game.getServer().runCommand(outer.formatted(x - r - 1, y, z, x - r - 1, y + 3, z));
                    game.getServer().runCommand(inner.formatted(x - r + 1, y + 1, z, x - r + 1, y + 3, z));
                }
            }
            game.getServer().runCommand(("summon minecraft:husk %d %d %d {PersistenceRequired:1b,Tags:[\"ns_lc_%s\"],"
                    + "CustomName:\"%s\",CustomNameVisible:1b}").formatted(x - 10, y, z + (c % 2 == 0 ? -2 : 2), names[c], names[c]));
        }
        // Watch the first yard (block beside the ladder) from the side, a little above.
        game.getServer().runCommand("setblock %d %d %d minecraft:barrier".formatted(gx[0] - 4, gy[0] + 3, gz[0] - 12));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 22".formatted(gx[0] - 4, gy[0] + 4, gz[0] - 12));
        context.waitTicks(20);
        final com.mki.nightsiege.game.LadderClimb[] ladders = new com.mki.nightsiege.game.LadderClimb[n];
        final int[] passAt = new int[n];
        final String[] where = new String[n];
        for (int c = 0; c < n; c++) {
            ladders[c] = new com.mki.nightsiege.game.LadderClimb();
            passAt[c] = -1;
            where[c] = "gone";
        }
        for (int i = 0; i < limit / 2; i++) {
            final int step = i;
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    for (int c = 0; c < n; c++) {
                        if (!e.entityTags().contains("ns_lc_" + names[c]) || !(e instanceof net.minecraft.world.entity.Mob mob)) {
                            continue;
                        }
                        ladders[c].tick(level, java.util.List.of(e.getUUID()), new BlockPos(gx[c], gy[c], gz[c]), level.getGameTime());
                        where[c] = String.format(Locale.ROOT, "%.1f %.1f %.1f climbing=%s busy=%s", e.getX() - gx[c], e.getY() - gy[c],
                                e.getZ() - gz[c], mob.onClimbable(), ladders[c].busy(e.getUUID()));
                        boolean inside = Math.abs(e.getBlockX() - gx[c]) < r && Math.abs(e.getBlockZ() - gz[c]) < r;
                        if (passAt[c] < 0 && inside && e.getY() < gy[c] + 0.5 && e.onGround()) {
                            passAt[c] = step * 2;
                        }
                        if (step % 50 == 0) {
                            System.out.println("[Night Siege test] ladder case " + names[c] + " t=" + step * 2 + ": " + where[c]);
                        }
                    }
                }
            });
            context.waitTicks(2);
            if (i == 25 || i == 45 || i == 100) {
                context.takeScreenshot("ladder_cases_" + i * 2);
            }
        }
        context.waitTicks(5);
        int passed = 0;
        StringBuilder failed = new StringBuilder();
        for (int c = 0; c < n; c++) {
            if (passAt[c] >= 0) {
                passed++;
                System.out.println("[Night Siege test] ladder case " + names[c] + ": PASS after " + passAt[c] + " ticks");
            } else {
                failed.append(' ').append(names[c]);
                System.out.println("[Night Siege test] ladder case " + names[c] + ": FAIL, not down in the yard after " + limit
                        + " ticks (at " + where[c] + ")");
            }
        }
        System.out.println("[Night Siege test] ladder cases: " + passed + "/" + n + " PASS");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                for (String name : names) {
                    if (e.entityTags().contains("ns_lc_" + name)) {
                        e.discard();
                    }
                }
            }
        });
        context.waitTicks(5);
        if (passed < n) {
            throw new AssertionError("ladder cases failed:" + failed);
        }
    }

    /**
     * Simon's playtest (25 October 2026): monsters were pulled to the ladders on the inner
     * side of the walls and pressed against the wall in front of them. A husk outside a
     * yard, a ladder only inside: it must not hang at the wall in front of that ladder.
     */
    private static void innerLadderScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int gx = pos.getX() + 160;
        int gz = pos.getZ();
        int gy = groundAt(context, gx, gz);
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(gx - 20, gy - 1, gz - 12, gx + 8, gy - 1, gz + 12));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(gx - 20, gy, gz - 12, gx + 8, gy + 8, gz + 12));
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        int r = 5;
        game.getServer().runCommand(wall.formatted(gx - r, gy, gz - r, gx + r, gy + 3, gz - r));
        game.getServer().runCommand(wall.formatted(gx - r, gy, gz + r, gx + r, gy + 3, gz + r));
        game.getServer().runCommand(wall.formatted(gx - r, gy, gz - r, gx - r, gy + 3, gz + r));
        game.getServer().runCommand(wall.formatted(gx + r, gy, gz - r, gx + r, gy + 3, gz + r));
        // The ladder hangs on the inner side of the west wall.
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:ladder[facing=east]"
                .formatted(gx - r + 1, gy, gz, gx - r + 1, gy + 3, gz));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 60 255 true");
        game.getServer().runCommand("tp @p %d.5 %d %d.5 90 -15".formatted(gx + 3, gy, gz));
        game.getServer().runCommand("summon minecraft:husk %d %d %d {PersistenceRequired:1b,Tags:[\"ns_inner\"]}"
                .formatted(gx - 12, gy, gz + 1));
        context.waitTicks(40);
        final com.mki.nightsiege.game.LadderClimb ladders = new com.mki.nightsiege.game.LadderClimb();
        final net.minecraft.core.BlockPos anchor = new net.minecraft.core.BlockPos(gx, gy, gz);
        final net.minecraft.core.BlockPos inner = new net.minecraft.core.BlockPos(gx - r + 1, gy, gz);
        final int[] busy = {0};
        final int[] atWall = {0};
        for (int i = 0; i < 200; i++) {
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (e.entityTags().contains("ns_inner") && e instanceof net.minecraft.world.entity.Mob mob) {
                        if (mob.getTarget() == null) {
                            mob.setTarget(player);
                        }
                        ids.add(e.getUUID());
                        if (ladders.busy(e.getUUID())) {
                            busy[0]++;
                        }
                        double dx = e.getX() - (inner.getX() - 1.5);
                        double dz = e.getZ() - (inner.getZ() + 0.5);
                        if (dx * dx + dz * dz < 2.25) {
                            atWall[0]++;   // right in front of the wall where the inner ladder hangs
                        }
                    }
                }
                ladders.tick(level, ids, anchor, level.getGameTime());
            });
            context.waitTicks(2);
        }
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e.entityTags().contains("ns_inner")) {
                    e.discard();
                }
            }
        });
        System.out.println("[Night Siege test] inner ladder: steered " + busy[0] + " of 200 steps, at the wall " + atWall[0]
                + " (both should be 0)");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(5);
    }

    /**
     * A real night situation: a player inside a walled yard is the target of two husks
     * outside, a ladder hangs on the outside of the wall, away from where the husks
     * bump into it. Once with the old behaviour (the attack goal steers too) and once
     * with the hold goal.
     */
    private static void ladderTargetScene(ClientGameTestContext context, TestSingleplayerContext game, boolean hold) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int gx = pos.getX() + 40 + (hold ? 40 : 0);
        int gz = pos.getZ();
        int gy = context.computeOnClient(client -> client.level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz));
        com.mki.nightsiege.game.LadderClimb.holdGoals = hold;
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(gx - 20, gy - 1, gz - 12, gx + 8, gy - 1, gz + 12));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(gx - 20, gy, gz - 12, gx + 8, gy + 8, gz + 12));
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        int r = 5;
        game.getServer().runCommand(wall.formatted(gx - r, gy, gz - r, gx + r, gy + 3, gz - r));
        game.getServer().runCommand(wall.formatted(gx - r, gy, gz + r, gx + r, gy + 3, gz + r));
        game.getServer().runCommand(wall.formatted(gx - r, gy, gz - r, gx - r, gy + 3, gz + r));
        game.getServer().runCommand(wall.formatted(gx + r, gy, gz - r, gx + r, gy + 3, gz + r));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:ladder[facing=west]"
                .formatted(gx - r - 1, gy, gz + 3, gx - r - 1, gy + 3, gz + 3));
        // The player stands inside, in survival, so the husks really want him.
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 60 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 60 5 true");
        game.getServer().runCommand("tp @p %d.5 %d %d.5 90 -15".formatted(gx + 3, gy, gz));
        String tag = hold ? "ns_ladder_hold" : "ns_ladder_old";
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {PersistenceRequired:1b,Tags:[\"" + tag + "\"]}")
                .formatted(gx - 12, gy, gz - 3));
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {PersistenceRequired:1b,Tags:[\"" + tag + "\"]}")
                .formatted(gx - 11, gy, gz - 7));
        context.waitTicks(20);
        final com.mki.nightsiege.game.LadderClimb ladders = new com.mki.nightsiege.game.LadderClimb();
        final net.minecraft.core.BlockPos anchor = new net.minecraft.core.BlockPos(gx, gy, gz);
        for (int i = 0; i < 300; i++) {
            final int step = i;
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (e.entityTags().contains(tag) && e instanceof net.minecraft.world.entity.Mob mob) {
                        if (mob.getTarget() == null) {
                            mob.setTarget(player);
                        }
                        ids.add(e.getUUID());
                        if (step % 50 == 0) {
                            System.out.println("[Night Siege test] ladder target " + (hold ? "neu" : "alt") + " " + step
                                    + ": at " + e.blockPosition() + " climbing=" + mob.onClimbable()
                                    + " busy=" + ladders.busy(e.getUUID()) + " target=" + (mob.getTarget() != null));
                        }
                    }
                }
                ladders.tick(level, ids, anchor, level.getGameTime());
            });
            context.waitTicks(2);
            if (i == 60 || i == 150) {
                context.takeScreenshot("ladder_target_" + (hold ? "neu_" : "alt_") + (i / 60));
            }
        }
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            int inside = 0;
            int total = 0;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e.entityTags().contains(tag)) {
                    total++;
                    if (Math.abs(e.getBlockX() - anchor.getX()) < r && Math.abs(e.getBlockZ() - anchor.getZ()) < r) {
                        inside++;
                    }
                    e.discard();
                }
            }
            System.out.println("[Night Siege test] ladder target " + (hold ? "neu" : "alt") + " result: " + inside + "/" + total + " inside");
        });
        com.mki.nightsiege.game.LadderClimb.holdGoals = true;
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(5);
    }

    /**
     * The chemistry towers: a mortar shells a group of husks, a potion thrower poisons another.
     * Logs their health, so the rule "two hits per zombie" can be checked.
     */
    private static void towerScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int gx = pos.getX() + 60;
        int gz = pos.getZ() - 40;
        int gy = context.computeOnClient(client -> client.level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz));
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(gx - 4, gy - 1, gz - 6, gx + 22, gy - 1, gz + 38));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(gx - 4, gy, gz - 6, gx + 22, gy + 10, gz + 38));
        // A wall between the mortar and its targets: the shells go over it.
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone_bricks".formatted(gx + 8, gy, gz - 3, gx + 8, gy + 4, gz + 3));
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"ns_tower_a\"]}").formatted(gx + 16, gy, gz));
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"ns_tower_a\"]}").formatted(gx + 16, gy, gz + 1));
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"ns_tower_b\"]}").formatted(gx + 10, gy, gz + 32));
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"ns_tower_b\"]}").formatted(gx + 10, gy, gz + 33));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 -60 25".formatted(gx + 2, gy + 6, gz - 5));
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.levels.put("mortar_tower", 1);
            own.levels.put("potion_tower", 1);
            place(level, manager, player, new BlockPos(gx, gy, gz), com.mki.nightsiege.core.TrapKind.MORTAR);
            place(level, manager, player, new BlockPos(gx, gy, gz + 32), com.mki.nightsiege.core.TrapKind.BREWER);
            System.out.println("[Night Siege test] tower hit=" + com.mki.nightsiege.game.SiegeManager.get().mobHealthFactor() * 10.0);
        });
        for (int i = 0; i < 24; i++) {
            final int step = i;
            context.waitTicks(10);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                StringBuilder line = new StringBuilder("[Night Siege test] towers t=" + (step + 1) * 10 + ":");
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (e instanceof net.minecraft.world.entity.LivingEntity l
                            && (e.entityTags().contains("ns_tower_a") || e.entityTags().contains("ns_tower_b"))) {
                        line.append(' ').append(e.entityTags().contains("ns_tower_a") ? "A" : "B")
                                .append(String.format(java.util.Locale.ROOT, "%.1f", l.getHealth()));
                    }
                }
                System.out.println(line);
            });
            if (i == 4) {
                context.takeScreenshot("tower_mortar");
            }
        }
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.setBlock(new BlockPos(gx, gy, gz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(new BlockPos(gx, gy, gz + 32), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e.entityTags().contains("ns_tower_a") || e.entityTags().contains("ns_tower_b")) {
                    e.discard();
                }
            }
        });
        context.waitTicks(5);
    }

    /**
     * Simon's playtest (25 October 2026): the mortars fired all the time at monsters they
     * could not see and hit nothing. First a husk shut in a stone box (no shell may fly),
     * then three husks walking across in front of the tower (the shells must hit).
     */
    private static void mortarScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int gx = pos.getX() + 60;
        int gz = pos.getZ() + 60;
        int gy = groundAt(context, gx, gz);
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(gx - 4, gy - 1, gz - 16, gx + 24, gy - 1, gz + 16));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(gx - 4, gy, gz - 16, gx + 24, gy + 10, gz + 16));
        // The hidden husk: in a closed stone box 12 blocks away.
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(gx + 11, gy, gz - 1, gx + 13, gy + 3, gz + 1));
        game.getServer().runCommand("setblock %d %d %d minecraft:air".formatted(gx + 12, gy + 1, gz));
        game.getServer().runCommand("setblock %d %d %d minecraft:air".formatted(gx + 12, gy + 2, gz));
        String husk = "summon minecraft:husk %d %d %d {PersistenceRequired:1b,Tags:[\"%s\"],Health:200f,"
                + "attributes:[{id:\"minecraft:max_health\",base:200d}]}";
        game.getServer().runCommand(husk.formatted(gx + 12, gy + 1, gz, "ns_mortar_hidden"));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 -90 25".formatted(gx - 3, gy + 8, gz));
        game.getServer().runCommand("setblock %d %d %d minecraft:barrier".formatted(gx - 3, gy + 7, gz));
        context.waitTicks(40);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.levels.put("mortar_tower", 1);
            place(level, manager, player, new BlockPos(gx, gy, gz), com.mki.nightsiege.core.TrapKind.MORTAR);
        });
        final java.util.Set<java.util.UUID> shells = new java.util.HashSet<>();
        for (int i = 0; i < 60; i++) {
            context.waitTicks(2);
            onServer(context, (player, manager) -> countShells(player, shells));
        }
        int hiddenShells = shells.size();
        // The box goes; now three husks walk across in front of the tower, 14 blocks away.
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(gx + 11, gy, gz - 1, gx + 13, gy + 3, gz + 1));
        com.mki.nightsiege.game.TrapService.shotLog = line -> System.out.println("[Night Siege test] " + line);
        // The engineer sets the spot (sneak + right click with the remote): the middle of the husks' path.
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] mortar aimed="
                + manager.kits().traps().setAim((net.minecraft.server.level.ServerLevel) player.level(),
                new BlockPos(gx, gy, gz), new BlockPos(gx + 15, gy - 1, gz))));
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e != null && e.entityTags().contains("ns_mortar_hidden")) {
                    e.discard();
                }
            }
        });
        for (int k = 0; k < 3; k++) {
            game.getServer().runCommand(husk.formatted(gx + 14 + k, gy, gz - 12 + k, "ns_mortar_walk"));
        }
        context.waitTicks(5);
        shells.clear();
        final float[] lost = {0f};
        for (int i = 0; i < 120; i++) {
            final int step = i;
            context.waitTicks(2);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                countShells(player, shells);
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (e.entityTags().contains("ns_mortar_walk") && e instanceof net.minecraft.world.entity.Mob mob) {
                        mob.setTarget(null);
                        // Back and forth across the field (12 blocks each way).
                        boolean north = (step / 40) % 2 == 0;
                        if (step % 10 == 0) {
                            mob.getNavigation().moveTo(mob.getX(), gy, gz + (north ? 12 : -12), 1.0);
                        }
                        if (step == 119) {
                            lost[0] += mob.getMaxHealth() - mob.getHealth();
                        }
                    }
                }
            });
            if (i == 40) {
                context.takeScreenshot("mortar_moving");
            }
        }
        com.mki.nightsiege.game.TrapService.shotLog = null;
        System.out.println("[Night Siege test] mortar hidden: " + hiddenShells + " shells (should be 0); walking: "
                + shells.size() + " shells, " + String.format(java.util.Locale.ROOT, "%.1f", lost[0]) + " damage on 3 husks");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.setBlock(new BlockPos(gx, gy, gz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e.entityTags().contains("ns_mortar_walk") || e.entityTags().contains("ns_mortar_hidden")) {
                    e.discard();
                }
            }
        });
        context.waitTicks(5);
    }

    /**
     * The ground height far from the player: the chunks there are loaded on the server first
     * (the client does not know them, its height there would be the bottom of the world).
     */
    private static int groundAt(ClientGameTestContext context, int x, int z) {
        final int[] ground = {Integer.MIN_VALUE};
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (int cx = (x - 32) >> 4; cx <= (x + 32) >> 4; cx++) {
                for (int cz = (z - 32) >> 4; cz <= (z + 32) >> 4; cz++) {
                    level.setChunkForced(cx, cz, true);
                    level.getChunk(cx, cz);
                }
            }
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        });
        context.waitTicks(20);
        System.out.println("[Night Siege test] ground at " + x + "," + z + " = " + ground[0]);
        return ground[0];
    }

    private static void countShells(net.minecraft.server.level.ServerPlayer player, java.util.Set<java.util.UUID> shells) {
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof com.mki.nightsiege.game.KitProjectile shot
                    && com.mki.nightsiege.game.KitProjectile.SHELL.equals(shot.kind())) {
                shells.add(e.getUUID());
            }
        }
    }

    /** The statistics window after a night, with four defenders. */
    private static void nightStatsScene(ClientGameTestContext context) {
        context.runOnClient(client -> {
            com.mki.nightsiege.core.NightStatsData data = new com.mki.nightsiege.core.NightStatsData();
            data.level = 10;
            data.event = "blood_moon";
            data.test = true;
            data.result = "test_gehalten";
            data.kills = 131;
            data.required = 125;
            data.waves = 9;
            data.seconds = 265;
            String[][] rows = {{"OpaSimoms", "warrior", "berserker", "412", "180", "0", "52", "0"},
                    {"Quxo", "engineer", "alchemist", "355", "64", "0", "41", "1"},
                    {"peachkosderechte", "guardian", "medic", "120", "142", "260", "17", "0"},
                    {client.player.getScoreboardName(), "archer", "heavy", "290", "96", "0", "21", "2"}};
            for (String[] r : rows) {
                com.mki.nightsiege.core.NightStatsData.Row row = new com.mki.nightsiege.core.NightStatsData.Row();
                row.name = r[0];
                row.clazz = r[1];
                row.subclass = r[2];
                row.dealt = Double.parseDouble(r[3]);
                row.taken = Double.parseDouble(r[4]);
                row.healed = Double.parseDouble(r[5]);
                row.kills = Integer.parseInt(r[6]);
                row.deaths = Integer.parseInt(r[7]);
                data.rows.add(row);
            }
            data.mvp = com.mki.nightsiege.core.NightStatsData.mvpOf(data.rows);
            client.setScreenAndShow(new com.mki.nightsiege.client.NightStatsScreen(data));
        });
        context.waitTicks(10);
        context.takeScreenshot("night_stats");
        // A real night with records: the vote row over the page tabs (29 October 2026).
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof com.mki.nightsiege.client.NightStatsScreen open) {
                com.mki.nightsiege.core.NightStatsData data = com.mki.nightsiege.game.Json.fromJson(
                        com.mki.nightsiege.game.Json.toJson(open.dataForTest()), com.mki.nightsiege.core.NightStatsData.class);
                data.test = false;
                data.result = "gewonnen";
                data.records = new com.mki.nightsiege.core.NightRecords.View();
                client.setScreenAndShow(new com.mki.nightsiege.client.NightStatsScreen(data));
            }
        });
        context.waitTicks(10);
        context.takeScreenshot("night_stats_vote");
        context.runOnClient(client -> client.setScreenAndShow(null));
        context.waitTicks(5);
    }

    /**
     * A closed yard in a test night (level 6): every zombie carries a ladder, sapper creepers come
     * from the start. Logs ladders built and holes blown (at most one per side).
     */
    /** Puts the camera at a spot (on a barrier, so it does not fall) looking at a point. */
    private static void camera(TestSingleplayerContext game, double x, double y, double z, double fx, double fy, double fz) {
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d minecraft:barrier",
                (int) Math.floor(x), (int) Math.floor(y) - 1, (int) Math.floor(z)));
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f facing %.2f %.2f %.2f",
                x, y, z, fx, fy, fz));
    }

    /** Clears a square yard on the server (grass floor, air above) around a middle and returns its ground height. */
    static int yard(ClientGameTestContext context, TestSingleplayerContext game, int cx, int cz, int out, int high) {
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - out - 8, cz - out - 8, cx + out + 8, cz + out + 8));
        context.waitTicks(40);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);
        int cy = ground[0];
        for (int part = -out; part <= out; part += 16) {
            int z1 = cz + part;
            int z2 = Math.min(cz + out, z1 + 15);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - out, cy - 1, z1, cx + out, cy - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - out, cy, z1, cx + out, cy + high, z2));
        }
        return cy;
    }

    /**
     * Where a camp may stand (Simon 27.10., 22:49: "the camp never spawns"): a meadow with
     * flowers, mushrooms, a sapling, a puddle and a small hill must be taken; the same meadow
     * with a few planks on it must not.
     */
    private static void campSiteCheck(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int sx = pos.getX() - 260;
        int sz = pos.getZ() + 180;
        int sy = yard(context, game, sx, sz, 22, 16);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            java.util.Random r = new java.util.Random(7);
            net.minecraft.world.level.block.state.BlockState[] plants = {
                    net.minecraft.world.level.block.Blocks.POPPY.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.DANDELION.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.CORNFLOWER.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.OAK_SAPLING.defaultBlockState()};
            for (int dx = -20; dx <= 20; dx++) {
                for (int dz = -20; dz <= 20; dz++) {
                    if (r.nextInt(4) == 0) {
                        level.setBlock(new BlockPos(sx + dx, sy, sz + dz), plants[r.nextInt(plants.length)], 3);
                    }
                }
            }
            level.setBlock(new BlockPos(sx + 5, sy - 1, sz - 4), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
            for (int dx = 8; dx <= 12; dx++) {
                for (int dy = 0; dy < 4; dy++) {
                    level.setBlock(new BlockPos(sx + dx, sy + dy, sz + 6), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                }
            }
            String meadow = manager.camp().siteForTest(level, sx, sz);
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {   // a small floor of planks: the sparse sampling must hit it
                    level.setBlock(new BlockPos(sx - 2 + i, sy - 1, sz - 2 + j), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }
            }
            String built = manager.camp().siteForTest(level, sx, sz);
            System.out.println("[Night Siege test] camp site meadow=" + meadow + " (ground " + sy + ") with planks=" + built);
        });
        context.waitTicks(5);
    }

    /**
     * The war camp (27 October 2026, rebuilt 29 October): built, looked at by day and by night,
     * attacked through the war drum's ready round (class cards, kit, grace, the gates shut), the
     * crossbowmen on the towers, the workbench shut away until the tent guards fall, the inner
     * ring opening, reinforcement from the tents, the workbench destroyed (it burns down and goes),
     * then a second camp where the defender falls (the camp wins, the machines come for sure).
     */
    private static void campScene(ClientGameTestContext context, TestSingleplayerContext game) {
        campSiteCheck(context, game);
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 220;
        int cz = pos.getZ() - 60;
        int cy = yard(context, game, cx, cz, 36, 18);
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode creative @p");
        // Update 30.10.: the ground under the camp comes back (a tree and a pile of stone stand there now),
        // and a chunk a player built in takes no camp.
        final long[] groundBefore = new long[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (int dy = 0; dy < 5; dy++) {
                level.setBlock(new BlockPos(cx + 10, cy + dy, cz + 3), net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(), 2);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(new BlockPos(cx + 10 + dx, cy + 5, cz + 3 + dz), net.minecraft.world.level.block.Blocks.OAK_LEAVES
                            .defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
                }
            }
            for (int dy = 0; dy < 3; dy++) {
                level.setBlock(new BlockPos(cx - 8, cy + dy, cz - 8), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            }
            groundBefore[0] = campGround(level, cx, cy, cz);
            BlockPos far = new BlockPos(cx + 400, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    cx + 400, cz), cz);
            level.getChunk(far.getX() >> 4, far.getZ() >> 4);
            int fy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far.getX(), far.getZ());
            BlockPos placed = new BlockPos(far.getX(), fy, far.getZ());
            level.setBlock(placed, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
            String freeBefore = manager.camp().siteForTest(level, far.getX() + 3, far.getZ() + 3);
            com.mki.nightsiege.game.PlayerBuilt.mark(level, placed);
            String freeAfter = manager.camp().siteForTest(level, far.getX() + 3, far.getZ() + 3);
            System.out.println("[Night Siege test] camp player built: before=" + freeBefore + " after=" + freeAfter);
        });
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            manager.camp().testCamp(level, new BlockPos(cx, cy, cz), 0, 999_999L);
            System.out.println("[Night Siege test] camp built at " + cx + " " + cy + " " + cz + " guards="
                    + manager.camp().testGuards(level) + " bench=" + (manager.camp().testBench(level) != null)
                    + " " + manager.camp().testRound(level));
        });
        context.waitTicks(40);
        // From outside the gate (south), a little above.
        camera(game, cx + 8.5, cy + 16, cz + 42.5, cx + 0.5, cy + 1, cz + 0.5);
        context.runOnClient(client -> hud(client, false));
        context.waitTicks(60);
        context.takeScreenshot("camp_gate");
        camera(game, cx - 30.5, cy + 30, cz - 26.5, cx + 0.5, cy, cz + 0.5);
        context.waitTicks(40);
        context.takeScreenshot("camp_above");
        camera(game, cx + 0.5, cy + 34, cz + 6.5, cx + 0.5, cy, cz + 4.5);
        context.waitTicks(40);
        context.takeScreenshot("camp_top");
        camera(game, cx + 3.5, cy + 4, cz + 7.5, cx + 0.5, cy + 2, cz + 0.5);
        context.waitTicks(30);
        context.takeScreenshot("camp_workshop");
        camera(game, cx + 0.5, cy + 9, cz - 3.5, cx - 10.5, cy + 6.5, cz - 10.5);
        context.waitTicks(30);
        context.takeScreenshot("camp_tower");
        game.getServer().runCommand("time set 18000");
        camera(game, cx + 8.5, cy + 16, cz + 42.5, cx + 0.5, cy + 1, cz + 0.5);
        context.waitTicks(40);
        context.takeScreenshot("camp_night");

        // The forecourt (content update): the war drum, the fire, the benches, the gate behind.
        game.getServer().runCommand("time set 1500");
        camera(game, cx + 0.5, cy + 9, cz + 38.5, cx + 0.5, cy, cz + 22.5);
        context.waitTicks(40);
        context.takeScreenshot("camp_forecourt");
        context.runOnClient(client -> hud(client, true));

        // Walking in without the drum: nothing happens (the gate is no way in any more).
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 120 255 true");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5", cx, cy, cz + 14));
        context.waitTicks(20);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] camp walk-in without drum: fighter="
                + manager.camp().isFighter(player)));

        // The attack: the war drum struck, the class cards of the ready round, ready, inside, the gates shut.
        onServer(context, (player, manager) -> {
            BlockPos path = manager.camp().testForecourtPath();
            player.teleportTo(path.getX() + 0.5, path.getY(), path.getZ() + 0.5);
            manager.config().campReinforceSeconds = 10;   // the reinforcement comes within the test
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            if (!own.hasClass()) {
                own.unlockClass(com.mki.nightsiege.core.SkillClass.WARRIOR);
                String key = own.profileFor(com.mki.nightsiege.core.SkillClass.WARRIOR);
                if (key != null) {
                    own.switchToProfile(key);
                }
            }
            BlockPos drum = manager.camp().testDrum();
            boolean struck = manager.camp().onDrum(player, drum);
            System.out.println("[Night Siege test] camp drum " + drum + " struck=" + struck + " inRound="
                    + manager.camp().inRound(player) + " starter=" + manager.camp().roundStarter(player) + " "
                    + manager.camp().testRound(level));
        });
        context.waitTicks(20);
        context.takeScreenshot("camp_round_cards");
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            manager.onPlayClass(player, own.chosenClass);   // the "Bereit" of the class window
        });
        context.waitTicks(25);
        closeScreen(context);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            System.out.println("[Night Siege test] camp fight: fighter=" + manager.camp().isFighter(player)
                    + " kit=" + manager.kits().hasStash(player) + " defending=" + manager.isDefending(player)
                    + " guards=" + manager.camp().testGuards(level) + " at=" + player.blockPosition()
                    + " " + manager.camp().testRound(level));
        });
        camera(game, cx + 0.5, cy + 3, cz + 12.5, cx + 0.5, cy + 1.5, cz + 18.5);
        context.waitTicks(20);
        context.takeScreenshot("camp_gate_shut");
        // Out over the palisade: put back inside.
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5", cx, cy, cz + 24));
        context.waitTicks(30);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] camp out over the wall: fighter="
                + manager.camp().isFighter(player) + " back inside=" + (player.getZ() < cz + 18) + " at=" + player.blockPosition()));
        // In the open before the towers: the crossbowmen shoot from up there.
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                cx, cy, cz - 15, cx - 10, cy + 6, cz - 10));
        context.takeScreenshot("camp_fight");
        context.waitTicks(220);   // past the grace: the guards come, the towers shoot
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            int targeting = 0;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (com.mki.nightsiege.game.SiegeCamp.isCampMob(e) && e instanceof net.minecraft.world.entity.Mob mob
                        && mob.getTarget() == player) {
                    targeting++;
                }
            }
            int bolts = 0;
            for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null,
                    new net.minecraft.world.phys.AABB(new BlockPos(cx, cy, cz)).inflate(40, 20, 40),
                    e -> e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow a
                            && a.getOwner() != null && a.getOwner().entityTags().contains(com.mki.nightsiege.game.SiegeCamp.TAG_TOWER))) {
                bolts++;
            }
            System.out.println("[Night Siege test] camp after grace: guards on the player=" + targeting
                    + " tower bolts=" + bolts + " hp=" + Math.round(player.getHealth()) + " " + manager.camp().testRound(level));
            net.minecraft.world.entity.LivingEntity bench = manager.camp().testBench(level);
            if (bench != null) {
                bench.invulnerableTime = 0;
                bench.hurtServer(level, level.damageSources().playerAttack(player), 120f);
                System.out.println("[Night Siege test] camp bench before the ring is open: " + Math.round(bench.getHealth()) + "/"
                        + Math.round(bench.getMaxHealth()));
            }
            // The tent guards fall: the inner ring opens.
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (com.mki.nightsiege.game.SiegeCamp.isCampMob(e) && e instanceof net.minecraft.world.entity.Mob mob
                        && mob.getType() == net.minecraft.world.entity.EntityTypes.VINDICATOR) {
                    mob.invulnerableTime = 0;
                    mob.hurtServer(level, level.damageSources().playerAttack(player), 10000f);
                }
            }
        });
        context.waitTicks(30);
        camera(game, cx + 0.5, cy + 3, cz + 14.5, cx + 0.5, cy + 1, cz + 8.5);
        context.waitTicks(20);
        context.takeScreenshot("camp_ring_open");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            System.out.println("[Night Siege test] camp tents down: " + manager.camp().testRound(level));
            net.minecraft.world.entity.LivingEntity bench = manager.camp().testBench(level);
            if (bench != null) {
                bench.invulnerableTime = 0;
                bench.hurtServer(level, level.damageSources().playerAttack(player), 120f);
                System.out.println("[Night Siege test] camp bench after a blow: " + Math.round(bench.getHealth()) + "/"
                        + Math.round(bench.getMaxHealth()));
            }
        });
        context.waitTicks(200);   // a reinforcement comes out of the tents (every 10 s in this test)
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            System.out.println("[Night Siege test] camp reinforcement: " + manager.camp().testRound(level));
            manager.config().campReinforceSeconds = 60;
            net.minecraft.world.entity.LivingEntity bench = manager.camp().testBench(level);
            if (bench != null) {
                bench.invulnerableTime = 0;
                bench.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
            }
        });
        context.waitTicks(30);
        context.takeScreenshot("camp_destroyed");
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.SiegeSave.CampRecord camp = manager.save().camp;
            System.out.println("[Night Siege test] camp destroyed=" + (camp != null && camp.destroyed)
                    + " machinesDay=" + manager.save().machinesDay + " fighter=" + manager.camp().isFighter(player)
                    + " kit=" + manager.kits().hasStash(player) + " "
                    + manager.camp().testRound((net.minecraft.server.level.ServerLevel) player.level()));
        });
        context.waitTicks(100);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            System.out.println("[Night Siege test] camp gone=" + (manager.save().camp == null) + " palisade="
                    + level.getBlockState(new BlockPos(cx + 18, cy + 1, cz)).getBlock()
                    + " guards left=" + manager.camp().testGuards(level));
        });
        context.waitTicks(40);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            long after = campGround(level, cx, cy, cz);
            System.out.println("[Night Siege test] camp ground back: same=" + (after == groundBefore[0])
                    + " log=" + level.getBlockState(new BlockPos(cx + 10, cy + 2, cz + 3)).getBlock()
                    + " stone=" + level.getBlockState(new BlockPos(cx - 8, cy + 2, cz - 8)).getBlock()
                    + " " + manager.camp().groundState());
        });

        // A second camp, two attackers' worth: the defender falls there, the camp wins.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            manager.camp().testCamp(level, new BlockPos(cx, cy, cz), 1, 999_998L);
        });
        context.waitTicks(20);
        game.getServer().runCommand("effect clear @p");
        onServer(context, (player, manager) -> {
            BlockPos path = manager.camp().testForecourtPath();   // this camp's gate looks another way
            player.teleportTo(path.getX() + 0.5, path.getY(), path.getZ() + 0.5);
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            manager.camp().onDrum(player, manager.camp().testDrum());
            manager.onPlayClass(player, manager.save().skillsOf(player.getUUID().toString()).chosenClass);
        });
        context.waitTicks(25);
        closeScreen(context);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] camp second fight: fighter=" + manager.camp().isFighter(player) + " "
                    + manager.camp().testRound((net.minecraft.server.level.ServerLevel) player.level()));
            player.invulnerableTime = 0;
            player.hurtServer((net.minecraft.server.level.ServerLevel) player.level(),
                    player.level().damageSources().generic(), 500f);
        });
        context.waitTicks(20);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] camp won: alive=" + player.isAlive() + " hp=" + Math.round(player.getHealth())
                    + " machinesDay=" + manager.save().machinesDay + " camp gone=" + (manager.save().camp == null)
                    + " kit=" + manager.kits().hasStash(player) + " at=" + player.blockPosition());
            manager.save().machinesDay = -1L;
        });
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("time set 6000");
        context.waitTicks(10);
    }

    /**
     * Update 30.10. (Simon: "das heer lager muss komplett verschwinden … da bleibt dann immer eine gerade
     * grass fläche über"): a camp on uneven ground with a tree and a pile of stone; after it goes, the
     * ground is the same block for block.
     */
    private static void campGroundScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 260;
        int cz = pos.getZ() + 90;
        int cy = yard(context, game, cx, cz, 36, 18);
        game.getServer().runCommand("gamemode creative @p");
        final long[] before = new long[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (int dy = 0; dy < 5; dy++) {
                level.setBlock(new BlockPos(cx + 10, cy + dy, cz + 3), net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(), 2);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(new BlockPos(cx + 10 + dx, cy + 5, cz + 3 + dz), net.minecraft.world.level.block.Blocks.OAK_LEAVES
                            .defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
                }
            }
            for (int dx = -2; dx <= 2; dx++) {   // a little hill
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(new BlockPos(cx - 8 + dx, cy, cz - 8 + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
            }
            for (int dy = 1; dy < 3; dy++) {
                level.setBlock(new BlockPos(cx - 8, cy + dy, cz - 8), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            }
            level.setBlock(new BlockPos(cx + 4, cy - 1, cz - 12), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);   // a dip
            before[0] = campGround(level, cx, cy, cz);
            CAMP_BEFORE.clear();
            for (int bx = cx - 30; bx <= cx + 30; bx++) {
                for (int bz = cz - 30; bz <= cz + 30; bz++) {
                    for (int by = cy - 12; by <= cy + 16; by++) {
                        BlockPos p = new BlockPos(bx, by, bz);
                        CAMP_BEFORE.put(p, level.getBlockState(p));
                    }
                }
            }
            manager.camp().testCamp(level, new BlockPos(cx, cy, cz), 0, 999_997L);
            System.out.println("[Night Siege test] campground built: log=" + level.getBlockState(new BlockPos(cx + 10, cy + 2, cz + 3)).getBlock()
                    + " " + manager.camp().groundState());
        });
        context.waitTicks(20);
        game.getServer().runCommand("tp @p %d.5 %d %d.5 180 20".formatted(cx, cy + 12, cz + 30));
        context.waitTicks(10);
        context.takeScreenshot("campground_standing");
        onServer(context, (player, manager) -> manager.camp().testRemove(player.level().getServer()));
        context.waitTicks(40);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            long after = campGround(level, cx, cy, cz);
            int diffs = 0;
            StringBuilder first = new StringBuilder();
            for (java.util.Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState> e : CAMP_BEFORE.entrySet()) {
                net.minecraft.world.level.block.state.BlockState now = level.getBlockState(e.getKey());
                if (now != e.getValue()) {
                    if (diffs++ < 12) {
                        first.append(' ').append(e.getKey().getX() - cx).append('/').append(e.getKey().getY() - cy).append('/')
                                .append(e.getKey().getZ() - cz).append(' ').append(e.getValue()).append("->").append(now);
                    }
                }
            }
            System.out.println("[Night Siege test] campground diff: " + diffs + first);
            System.out.println("[Night Siege test] campground back: same=" + (after == before[0])
                    + " log=" + level.getBlockState(new BlockPos(cx + 10, cy + 2, cz + 3)).getBlock()
                    + " stone=" + level.getBlockState(new BlockPos(cx - 8, cy + 2, cz - 8)).getBlock()
                    + " dip=" + level.getBlockState(new BlockPos(cx + 4, cy - 1, cz - 12)).getBlock()
                    + " " + manager.camp().groundState());
        });
        context.takeScreenshot("campground_gone");
        game.getServer().runCommand("forceload remove all");
    }

    private static final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> CAMP_BEFORE = new java.util.HashMap<>();

    /** A sum over the ground a test camp takes (the same before it stood and after it is gone). */
    private static long campGround(net.minecraft.server.level.ServerLevel level, int x, int y, int z) {
        long sum = 0;
        for (int bx = x - 30; bx <= x + 30; bx++) {
            for (int bz = z - 30; bz <= z + 30; bz++) {
                for (int by = y - 12; by <= y + 16; by++) {
                    BlockPos p = new BlockPos(bx, by, bz);
                    sum = sum * 31 + net.minecraft.world.level.block.Block.getId(level.getBlockState(p)) + 7L * (bx + by * 3 + bz * 5);
                }
            }
        }
        return sum;
    }

    /** Sets the core window's tab by its name (the next opening shows it). */
    private static void coreTab(ClientGameTestContext context, String name) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field tab = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                tab.setAccessible(true);
                for (Object t : tab.getType().getEnumConstants()) {
                    if (t.toString().equals(name)) {
                        tab.set(null, t);
                    }
                }
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] tab not set: " + e);
            }
        });
    }

    /**
     * Ground for a dungeon of the content update (its vaults go 29 deep, too deep for the flat
     * test world): the chunks loaded, a pillar of stone under the gatehouse; the halls and the
     * arena stand in the air below. Returns the height one walks on.
     */
    private static int dungeonGround(ClientGameTestContext context, TestSingleplayerContext game, int cx, int cz, int rooms) {
        int top = -6;
        int cz2 = 20 + 43 * rooms + 38;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 34, cz - 14, cx + 34, cz + cz2 + 32));
        context.waitTicks(30);
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(cx - 8, -60, cz - 10, cx + 8, top - 2, cz + 7));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - 8, top - 1, cz - 10, cx + 8, top - 1, cz + 7));
        context.waitTicks(5);
        return top;
    }

    /**
     * A hill of stone for a dungeon (the test world is flat, its ground lies too low for the
     * vaults): 27 wide, from the gatehouse to past the arena, grass on top, air above. Returns
     * the height one walks on.
     */
    private static int hill(ClientGameTestContext context, TestSingleplayerContext game, int cx, int cz) {
        int top = -36;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 16, cz - 16, cx + 16, cz + 88));
        context.waitTicks(30);
        for (int z1 = cz - 14; z1 <= cz + 84; z1 += 40) {
            int z2 = Math.min(cz + 84, z1 + 39);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(cx - 13, -63, z1, cx + 13, top - 2, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - 13, top - 1, z1, cx + 13, top - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - 13, top, z1, cx + 13, top + 18, z2));
        }
        context.waitTicks(5);
        return top;
    }

    /**
     * The statistics logger (28 October 2026): a short test night with two waves, half of them
     * struck down by the player, a crate opened; then the files in world/nightsiege/stats.
     */
    private static void statsScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 140;
        int cz = pos.getZ() + 140;
        int cy = yard(context, game, cx, cz, 26, 10);
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 300 255 true");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5", cx, cy, cz + 3));
        context.waitTicks(20);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            manager.config().testPrepSeconds = 1;
            boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 3,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] stats night prepared=" + ok);
        });
        context.waitTicks(60);
        closeScreen(context);
        for (int round = 0; round < 2; round++) {
            onServer(context, (player, manager) -> manager.testWave(player.level().getServer()));
            context.waitTicks(80);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                int struck = 0;
                java.util.List<net.minecraft.world.entity.Mob> mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        player.getBoundingBox().inflate(80), m -> m.entityTags().contains(com.mki.nightsiege.game.MobFactory.TAG)
                                && m.isAlive());
                for (int i = 0; i < mobs.size(); i += 2) {
                    net.minecraft.world.entity.Mob mob = mobs.get(i);
                    mob.invulnerableTime = 0;
                    mob.hurtServer(level, level.damageSources().playerAttack(player), 6f);
                    mob.invulnerableTime = 0;
                    mob.hurtServer(level, level.damageSources().playerAttack(player), 100000f);
                    struck++;
                }
                System.out.println("[Night Siege test] stats wave: monsters " + mobs.size() + ", struck down " + struck);
            });
            context.waitTicks(30);
        }
        onServer(context, (player, manager) -> {
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), com.mki.nightsiege.game.CrateService.crate(
                    com.mki.nightsiege.core.SkillClass.WARRIOR, com.mki.nightsiege.core.CrateKind.NORMAL, 3));
            manager.crates().open(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            com.mki.nightsiege.game.StatsRecorder.NightFile night = manager.stats().testNight();
            System.out.println("[Night Siege test] stats running: " + (night == null ? "none"
                    : "waves " + night.wellen.size() + " spawned " + night.gespawnt + " players " + night.spieler.keySet()));
            manager.abort(player.level().getServer());
            manager.config().testPrepSeconds = 60;
        });
        context.waitTicks(40);
        closeScreen(context);
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.StatsRecorder.testFlush();
            java.nio.file.Path dir = manager.stats().testFolder();
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
                java.util.List<java.nio.file.Path> list = files.sorted().toList();
                StringBuilder names = new StringBuilder();
                for (java.nio.file.Path f : list) {
                    names.append(' ').append(f.getFileName()).append('(').append(java.nio.file.Files.size(f)).append(')');
                }
                System.out.println("[Night Siege test] stats files:" + names);
                java.nio.file.Path last = null;
                for (java.nio.file.Path f : list) {
                    if (f.getFileName().toString().startsWith("testnacht_")) {
                        last = f;
                    }
                }
                if (last != null) {
                    com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(
                            java.nio.file.Files.readString(last)).getAsJsonObject();
                    StringBuilder sb = new StringBuilder();
                    sb.append("version=").append(o.get("version")).append(" build=").append(o.get("build"))
                            .append(" ergebnis=").append(o.get("ergebnis")).append(" abgeschlossen=").append(o.get("abgeschlossen"))
                            .append(" gespawnt=").append(o.get("gespawnt")).append(" wellen=").append(o.getAsJsonArray("wellen").size())
                            .append(" gegner=").append(o.getAsJsonObject("gegner").keySet())
                            .append(" spieler=").append(o.getAsJsonObject("spieler").keySet());
                    for (com.google.gson.JsonElement w : o.getAsJsonArray("wellen")) {
                        com.google.gson.JsonObject wave = w.getAsJsonObject();
                        sb.append(" | welle ").append(wave.get("nummer")).append(": gespawnt ").append(wave.get("gespawnt"))
                                .append(" spieler ").append(wave.getAsJsonObject("spieler"));
                    }
                    System.out.println("[Night Siege test] stats night file " + last.getFileName() + ": " + sb);
                }
                java.nio.file.Path crates = dir.resolve("kisten.jsonl");
                if (java.nio.file.Files.exists(crates)) {
                    java.util.List<String> lines = java.nio.file.Files.readAllLines(crates);
                    System.out.println("[Night Siege test] stats crates: " + lines.size() + " last " + lines.get(lines.size() - 1));
                }
            } catch (java.io.IOException | RuntimeException e) {
                System.out.println("[Night Siege test] stats files not read: " + e);
            }
        });
        context.waitTicks(10);
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("forceload remove all");
        context.waitTicks(10);
    }

    /** Hits every dungeon monster standing now with a deadly player blow. */
    private static void killDungeonMobs(ClientGameTestContext context, String label) {
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            java.util.List<net.minecraft.world.entity.Mob> mobs = manager.expeditions().testMobs(level);
            int dead = 0;
            for (net.minecraft.world.entity.Mob mob : mobs) {
                mob.invulnerableTime = 0;
                mob.hurtServer(level, level.damageSources().playerAttack(player), 100000f);
                if (!mob.isAlive()) {
                    dead++;
                }
            }
            System.out.println("[Night Siege test] dungeon " + label + ": " + mobs.size() + " monsters, struck down " + dead
                    + " | " + manager.expeditions().testState());
        });
    }

    /** A sum over every block of the ground a test dungeon takes (the same before and after it stood). */
    private static long groundSum(net.minecraft.server.level.ServerLevel level, int x, int z, int cz) {
        long sum = 0;
        for (int bx = x - 33; bx <= x + 33; bx++) {
            for (int bz = z - 12; bz <= z + cz + 30; bz++) {
                for (int by = -60; by <= 12; by++) {
                    BlockPos p = new BlockPos(bx, by, bz);
                    sum = sum * 31 + net.minecraft.world.level.block.Block.getId(level.getBlockState(p)) + 7L * (bx + by * 3 + bz * 5);
                }
            }
        }
        return sum;
    }

    /**
     * Every block of the eight dungeons, built side by side with four halls each, written to
     * {@code nightsiege-dungeon-dump.txt.gz} in the game folder (Simon 29.10.: blocked doors and
     * stairs, blocks in the air): per block the state, whether it can stand where it stands, and
     * per state its collision boxes; plus the plan's spots and boxes. Checked outside the game.
     */
    private static void dungeonDumpScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        game.getServer().runCommand("gamemode creative @p");
        com.mki.nightsiege.core.ExpeditionKind[] kinds = com.mki.nightsiege.core.ExpeditionKind.values();
        int bx = pos.getX() - 300;
        int bz = pos.getZ() + 260;
        int top = -6;
        int rooms = 4;
        int cz = 20 + 43 * rooms + 38;
        Path file = FabricLoader.getInstance().getGameDir().resolve("nightsiege-dungeon-dump.txt.gz");
        final java.io.Writer[] out = new java.io.Writer[1];
        try {
            out[0] = new java.io.BufferedWriter(new java.io.OutputStreamWriter(
                    new java.util.zip.GZIPOutputStream(Files.newOutputStream(file)), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            System.out.println("[Night Siege test] dungeon dump not opened: " + e);
            return;
        }
        for (int i = 0; i < kinds.length; i++) {
            final int index = i;
            final int cx = bx + i * 72;
            game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 36, bz - 14, cx + 36, bz + cz + 34));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(cx - 8, -60, bz - 10, cx + 8, top - 2, bz + 7));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - 8, top - 1, bz - 10, cx + 8, top - 1, bz + 7));
            context.waitTicks(20);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                BlockPos origin = new BlockPos(cx, top, bz);
                manager.expeditions().testBuild(level, origin, 0, kinds[index], rooms);
                com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
                try {
                    int n = dumpDungeon(level, plan, origin, kinds[index].id(), rooms, out[0]);
                    System.out.println("[Night Siege test] dungeon dump " + kinds[index].id() + ": " + n + " blocks");
                } catch (java.io.IOException | RuntimeException e) {
                    System.out.println("[Night Siege test] dungeon dump " + kinds[index].id() + " failed: " + e);
                }
            });
            context.waitTicks(20);
            game.getServer().runCommand("forceload remove all");
        }
        onServer(context, (player, manager) -> {
            try {
                out[0].close();
                System.out.println("[Night Siege test] dungeon dump written: " + file + " " + Files.size(file));
            } catch (java.io.IOException e) {
                System.out.println("[Night Siege test] dungeon dump not closed: " + e);
            }
        });
        context.waitTicks(10);
    }

    private static void dumpSpot(java.io.Writer out, String name, BlockPos p, BlockPos o) throws java.io.IOException {
        out.write("M " + name + " " + (p.getX() - o.getX()) + " " + (p.getY() - o.getY()) + " " + (p.getZ() - o.getZ()) + "\n");
    }

    private static void dumpBox(java.io.Writer out, String name, net.minecraft.world.phys.AABB b, BlockPos o) throws java.io.IOException {
        out.write("B " + name + " " + (int) Math.floor(b.minX - o.getX()) + " " + (int) Math.floor(b.minY - o.getY()) + " "
                + (int) Math.floor(b.minZ - o.getZ()) + " " + (int) Math.ceil(b.maxX - o.getX()) + " "
                + (int) Math.ceil(b.maxY - o.getY()) + " " + (int) Math.ceil(b.maxZ - o.getZ()) + "\n");
    }

    private static int dumpDungeon(net.minecraft.server.level.ServerLevel level, com.mki.nightsiege.game.ExpeditionLayout.Plan plan,
                                   BlockPos o, String id, int rooms, java.io.Writer out) throws java.io.IOException {
        out.write("D " + id + " " + rooms + " " + o.getX() + " " + o.getY() + " " + o.getZ() + "\n");
        dumpSpot(out, "door", plan.door, o);
        dumpSpot(out, "seal", plan.seal, o);
        dumpSpot(out, "fire", plan.fire, o);
        dumpSpot(out, "arenaCenter", plan.arenaCenter, o);
        for (BlockPos p : plan.stairGate) {
            dumpSpot(out, "stairGate", p, o);
        }
        for (BlockPos p : plan.arenaGate) {
            dumpSpot(out, "arenaGate", p, o);
        }
        for (BlockPos p : plan.frontGate) {
            dumpSpot(out, "frontGate", p, o);
        }
        for (BlockPos p : plan.waveGates) {
            dumpSpot(out, "waveGate", p, o);
        }
        for (BlockPos p : plan.waveSpots) {
            dumpSpot(out, "waveSpot", p, o);
        }
        for (BlockPos p : plan.waiting) {
            dumpSpot(out, "waiting", p, o);
        }
        for (BlockPos p : plan.walkways) {
            dumpSpot(out, "walk", p, o);
        }
        for (int i = 0; i < plan.roomGates.size(); i++) {
            for (BlockPos p : plan.roomGates.get(i)) {
                dumpSpot(out, "roomGate" + i, p, o);
            }
            for (BlockPos p : plan.roomSpots.get(i)) {
                dumpSpot(out, "roomSpot" + i, p, o);
            }
            dumpSpot(out, "roomCenter" + i, plan.roomCenters.get(i), o);
            dumpBox(out, "hall" + i, plan.roomHalls.get(i), o);
            dumpBox(out, "zone" + i, plan.roomZones.get(i), o);
            out.write("H " + i + " " + plan.roomModules.get(i) + "\n");
        }
        for (net.minecraft.world.phys.AABB b : plan.inside) {
            dumpBox(out, "inside", b, o);
        }
        dumpBox(out, "arena", plan.arena, o);
        dumpBox(out, "bounds", plan.bounds, o);
        java.util.Map<net.minecraft.world.level.block.state.BlockState, Integer> pal = new java.util.LinkedHashMap<>();
        net.minecraft.world.phys.AABB b = plan.bounds.inflate(2);
        int n = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        StringBuilder sb = new StringBuilder();
        for (int x = (int) b.minX; x < (int) b.maxX; x++) {
            for (int y = (int) b.minY; y < (int) b.maxY; y++) {
                for (int z = (int) b.minZ; z < (int) b.maxZ; z++) {
                    at.set(x, y, z);
                    net.minecraft.world.level.block.state.BlockState st = level.getBlockState(at);
                    if (st.isAir()) {
                        continue;
                    }
                    Integer idx = pal.get(st);
                    if (idx == null) {
                        idx = pal.size();
                        pal.put(st, idx);
                    }
                    sb.setLength(0);
                    sb.append(x - o.getX()).append(' ').append(y - o.getY()).append(' ').append(z - o.getZ()).append(' ').append(idx);
                    if (!st.canSurvive(level, at)) {
                        sb.append(" s");
                    }
                    sb.append('\n');
                    out.write(sb.toString());
                    n++;
                }
            }
        }
        for (java.util.Map.Entry<net.minecraft.world.level.block.state.BlockState, Integer> e : pal.entrySet()) {
            net.minecraft.world.level.block.state.BlockState st = e.getKey();
            sb.setLength(0);
            sb.append("P ").append(e.getValue()).append(' ').append(st.toString().replace(' ', '_')).append(" |");
            for (net.minecraft.world.phys.AABB box : st.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                    BlockPos.ZERO).toAabbs()) {
                sb.append(String.format(Locale.ROOT, " %.3f,%.3f,%.3f,%.3f,%.3f,%.3f", box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
            }
            if (!st.getFluidState().isEmpty()) {
                sb.append(" fluid");
            }
            sb.append('\n');
            out.write(sb.toString());
        }
        out.flush();
        return n;
    }

    /** Block light where one walks in the dungeon of now (palette rule 7: at least 7 on the ways). */
    private static void lightCheck(net.minecraft.server.level.ServerLevel level, com.mki.nightsiege.game.SiegeManager manager, String id) {
        com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
        if (plan == null) {
            return;
        }
        int cells = 0;
        int dark = 0;
        int black = 0;
        int min = 15;
        long total = 0;
        java.util.Map<String, int[]> parts = new java.util.LinkedHashMap<>();
        for (BlockPos p : plan.walkways) {
            if (!level.getBlockState(p).isAir()) {
                continue;
            }
            int light = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p);
            cells++;
            total += light;
            min = Math.min(min, light);
            if (light < 7) {
                dark++;
            }
            if (light == 0) {
                black++;
            }
            String part = "gang";
            for (int i = 0; i < plan.roomHalls.size(); i++) {
                if (plan.roomHalls.get(i).contains(net.minecraft.world.phys.Vec3.atCenterOf(p))) {
                    part = "saal" + (i + 1);
                }
            }
            if (plan.arena.inflate(2).contains(net.minecraft.world.phys.Vec3.atCenterOf(p))) {
                part = "arena";
            }
            int[] c = parts.computeIfAbsent(part, k -> new int[2]);
            c[0]++;
            if (light < 7) {
                c[1]++;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, int[]> e : parts.entrySet()) {
            sb.append(' ').append(e.getKey()).append(' ').append(e.getValue()[1]).append('/').append(e.getValue()[0]);
        }
        System.out.println("[Night Siege test] dungeon light " + id + ": cells=" + cells + " under7=" + dark + " zero=" + black
                + " min=" + min + " mean=" + (cells == 0 ? 0 : total / cells) + " |" + sb);
    }

    /**
     * The own dungeons (dungeon update, 27 October 2026): the scouts' report with the core
     * window's page and the map in hand; all eight built side by side and photographed (gatehouse,
     * a guard room, the arena); one fought through (rooms, gates, waves, champion, reward, ruin);
     * one attempt lost (the last fighter falls).
     */
    private static void dungeonScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode creative @p");
        final boolean[] fakeCore = new boolean[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            if (manager.mainCore() == null) {
                manager.save().cores.add(new com.mki.nightsiege.game.CoreRecord(
                        com.mki.nightsiege.game.SiegeManager.dimensionName(level), pos.getX(), pos.getY(), pos.getZ()));
                fakeCore[0] = true;
            }
            com.mki.nightsiege.game.SiegeSave.ExpeditionRecord rec = manager.expeditions().report(level.getServer(), null);
            System.out.println("[Night Siege test] dungeon report: " + (rec == null ? "none"
                    : rec.kind + " at " + rec.x + " " + rec.z + " facing " + rec.facing + " rooms " + rec.rooms
                    + " map " + rec.map.length() + " cells of " + rec.mapCell));
        });
        context.waitTicks(20);
        coreTab(context, "SCOUT");
        onServer(context, (player, manager) -> manager.openCoreWindow(player));
        context.waitTicks(30);
        context.takeScreenshot("dungeon_scout_page");
        closeScreen(context);
        onServer(context, (player, manager) -> {
            manager.expeditions().giveMap(player);
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(net.minecraft.world.item.Items.FILLED_MAP)) {
                    net.minecraft.world.item.ItemStack map = inv.removeItemNoUpdate(i);
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, map);
                    break;
                }
            }
        });
        context.getInput().lookAt(0.0f, 40.0f);
        context.waitTicks(30);
        context.takeScreenshot("dungeon_map_hand");
        onServer(context, (player, manager) -> player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.item.ItemStack.EMPTY));

        // All eight side by side: the way leads south (+z). Built in the air over the flat world
        // (its ground lies too low for vaults 29 deep), the gatehouse on a pillar of stone.
        com.mki.nightsiege.core.ExpeditionKind[] kinds = com.mki.nightsiege.core.ExpeditionKind.values();
        int bx = pos.getX() - 300;
        int bz = pos.getZ() + 260;
        int top = -6;
        int rooms = 2;
        int cz = 20 + 43 * rooms + 38;   // the arena's middle (ExpeditionLayout.arenaCenter)
        for (com.mki.nightsiege.core.ExpeditionKind k : kinds) {
            java.util.List<String> missing = new java.util.ArrayList<>();
            for (String id : k.allBlocks()) {
                if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.Identifier.withDefaultNamespace(id))) {
                    missing.add(id);
                }
            }
            System.out.println("[Night Siege test] dungeon palette " + k.id() + ": " + k.allBlocks().size() + " blocks, missing " + missing);
        }
        int[] floors = new int[kinds.length + 1];
        for (int i = 0; i <= kinds.length; i++) {
            int cx = bx + i * 72;
            floors[i] = top;
            game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 34, bz - 14, cx + 34, bz + cz + 32));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(cx - 8, -60, bz - 10, cx + 8, top - 2, bz + 7));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - 8, top - 1, bz - 10, cx + 8, top - 1, bz + 7));
        }
        context.waitTicks(40);
        // The ground of the fight dungeon before it stands (it must be the same after it is gone).
        int fx = bx + kinds.length * 72;
        int fy = floors[kinds.length];
        final long[] sumBefore = new long[1];
        final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new java.util.HashMap<>();
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            sumBefore[0] = groundSum(level, fx, bz, cz);
            for (int x = fx - 33; x <= fx + 33; x++) {
                for (int z = bz - 12; z <= bz + cz + 30; z++) {
                    for (int y = -60; y <= 12; y++) {
                        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(new BlockPos(x, y, z));
                        if (!st.isAir()) {
                            before.put(new BlockPos(x, y, z), st);
                        }
                    }
                }
            }
        });
        for (int i = 0; i < kinds.length; i++) {
            final int index = i;
            final int cx = bx + i * 72;
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                long t0 = System.nanoTime();
                manager.expeditions().testBuild(level, new BlockPos(cx, floors[index], bz), 0, kinds[index], rooms);
                com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
                System.out.println("[Night Siege test] dungeon built " + kinds[index].id() + " at " + cx + " " + floors[index] + " " + bz
                        + " blocks=" + plan.blocks.size() + " fill=" + plan.fill.size() + " ms=" + (System.nanoTime() - t0) / 1_000_000
                        + " halls=" + plan.roomModules + " spots=" + plan.roomSpots.stream().map(java.util.List::size).toList()
                        + " waveSpots=" + plan.waveSpots.size()
                        + " fire=" + level.getBlockState(plan.fire).getBlock()
                        + " door=" + level.getBlockState(new BlockPos(cx, floors[index], bz - 5)).getBlock());
            });
            context.waitTicks(10);
            onServer(context, (player, manager) -> lightCheck((net.minecraft.server.level.ServerLevel) player.level(), manager,
                    kinds[index].id()));
        }
        context.runOnClient(client -> hud(client, false));
        game.getServer().runCommand("time set 1500");
        for (int i = 0; i < kinds.length; i++) {
            int cx = bx + i * 72;
            int cy = floors[i];
            int hy = cy - 20;
            int ay = cy - 24;
            String id = kinds[i].id();
            camera(game, cx + 9.5, cy + 6, bz - 15.5, cx + 0.5, cy + 3, bz + 0.5);
            context.waitTicks(i == 0 ? 80 : 45);
            context.takeScreenshot("dungeon_" + id + "_gate");
            // Hall 1 from the left tower niche, then from the back over the whole hall.
            camera(game, cx - 11.5, hy + 4.5, bz + 33.5, cx + 2.5, hy + 1, bz + 56.5);
            context.waitTicks(35);
            context.takeScreenshot("dungeon_" + id + "_room");
            camera(game, cx + 6.5, hy + 9.5, bz + 32 + 27.5, cx + 0.5, hy, bz + 32 + 4.5);
            context.waitTicks(30);
            context.takeScreenshot("dungeon_" + id + "_room_back");
            // Hall 2 (offset to the left): its own module.
            camera(game, cx - 14 + 0.5, hy + 8.5, bz + 75 + 1.5, cx - 14 + 0.5, hy + 1, bz + 75 + 20.5);
            context.waitTicks(30);
            context.takeScreenshot("dungeon_" + id + "_room2");
            // The arena from the gallery by the way in, and from above the pit.
            camera(game, cx + 0.5, ay + 8.5, bz + cz - 19.5, cx + 0.5, ay - 1, bz + cz + 4.5);
            context.waitTicks(35);
            context.takeScreenshot("dungeon_" + id + "_arena");
            camera(game, cx + 15.5, ay + 12.5, bz + cz + 12.5, cx - 2.5, ay - 2, bz + cz - 3.5);
            context.waitTicks(30);
            context.takeScreenshot("dungeon_" + id + "_arena_side");
        }
        context.runOnClient(client -> hud(client, true));

        // One fought through on fresh ground: the stone crypt, two halls; then it collapses.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            manager.expeditions().testBuild(level, new BlockPos(fx, fy, bz), 0, com.mki.nightsiege.core.ExpeditionKind.STONE_CRYPT, rooms);
        });
        context.waitTicks(10);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 900 255 true");
        // The ready round at the seal stone: the window, the class chosen, the gate goes up.
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                fx + 2, fy, bz - 4, fx, fy, bz + 2));
        context.waitTicks(20);
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
            if (!manager.save().skillsOf(player.getUUID().toString()).hasClass()) {
                manager.onChooseClass(player, "WARRIOR");
            }
            System.out.println("[Night Siege test] dungeon class: " + manager.save().skillsOf(player.getUUID().toString()).chosenClass);
            boolean used = manager.expeditions().onSeal(player, plan.seal);
            System.out.println("[Night Siege test] dungeon seal: used=" + used + " inRound=" + manager.expeditions().inRound(player)
                    + " stone=" + player.level().getBlockState(plan.seal).getBlock()
                    + " gate=" + player.level().getBlockState(plan.stairGate.get(0)).getBlock());
        });
        context.waitTicks(30);
        context.takeScreenshot("dungeon_round_pick");
        onServer(context, (player, manager) -> manager.onPlayClass(player, manager.save().skillsOf(player.getUUID().toString()).chosenClass));
        context.waitTicks(10);
        closeScreen(context);
        context.waitTicks(50);
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
            System.out.println("[Night Siege test] dungeon round started: inRound=" + manager.expeditions().inRound(player)
                    + " gate=" + player.level().getBlockState(plan.stairGate.get(0)).getBlock()
                    + "/" + player.level().getBlockState(plan.stairGate.get(3)).getBlock());
        });
        context.takeScreenshot("dungeon_round_gate");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                fx, fy - 12, bz + 12, fx, fy - 16, bz + 20));
        context.waitTicks(30);
        context.takeScreenshot("dungeon_fight_cards");
        closeScreen(context);
        context.waitTicks(20);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] dungeon gate behind: "
                + player.level().getBlockState(manager.expeditions().testPlan().stairGate.get(0)).getBlock()));
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] dungeon enter: fighter="
                + manager.expeditions().isFighter(player) + " kit=" + manager.kits().hasStash(player)
                + " defending=" + manager.isDefending(player) + " | " + manager.expeditions().testState()));
        int[] offsets = {0, -14};
        for (int room = 0; room < rooms; room++) {
            int z0 = bz + 32 + 43 * room;
            int ox = fx + offsets[room];
            game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                    ox, fy - 20, z0 + 2, ox, fy - 19, z0 + 14));
            // The first hall: its gate goes up once everyone stands before it (29.10.), then its guards come.
            context.waitTicks(room == 0 ? 90 : 30);
            if (room == 0) {
                onServer(context, (player, manager) -> System.out.println("[Night Siege test] dungeon front gate: "
                        + player.level().getBlockState(manager.expeditions().testPlan().frontGate.get(0)).getBlock()
                        + " | " + manager.expeditions().testState()));
                context.takeScreenshot("dungeon_fight_room");
            }
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                int targeting = 0;
                StringBuilder where = new StringBuilder();
                for (net.minecraft.world.entity.Mob mob : manager.expeditions().testMobs(level)) {
                    if (mob.getTarget() == player) {
                        targeting++;
                    }
                    where.append(' ').append(mob.blockPosition().getX() - player.getBlockX()).append('/')
                            .append(mob.blockPosition().getY() - player.getBlockY()).append('/')
                            .append(mob.blockPosition().getZ() - player.getBlockZ());
                }
                System.out.println("[Night Siege test] dungeon room: monsters=" + manager.expeditions().testMobs(level).size()
                        + " on the player=" + targeting + " at" + where);
            });
            if (room == 0) {
                // Update 30.10.: the stair keeps the monsters in (one put on it is pushed back down).
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
                    java.util.List<net.minecraft.world.entity.Mob> mobs = manager.expeditions().testMobs(level);
                    net.minecraft.world.phys.AABB stair = plan.inside.get(0);
                    int sx = (int) Math.floor(stair.getCenter().x);
                    int sz = (int) Math.floor(stair.getCenter().z);
                    BlockPos step = null;
                    for (int y = (int) stair.maxY; y >= (int) stair.minY; y--) {
                        BlockPos at = new BlockPos(sx, y, sz);
                        if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).isAir()) {
                            step = at;
                            break;
                        }
                    }
                    if (!mobs.isEmpty() && step != null) {
                        net.minecraft.world.entity.Mob mob = mobs.get(0);
                        mob.setNoAi(false);
                        mob.teleportTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5);
                        System.out.println("[Night Siege test] dungeon stair: put " + mob.getType().getDescriptionId() + " on " + step
                                + " inZone=" + plan.stairZone.contains(mob.position()));
                    } else {
                        System.out.println("[Night Siege test] dungeon stair: no monster or no step (" + mobs.size() + ", " + step + ")");
                    }
                });
                context.waitTicks(12);
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
                    int onStair = 0;
                    for (net.minecraft.world.entity.Mob mob : manager.expeditions().testMobs(level)) {
                        if (plan.stairZone.contains(mob.position())) {
                            onStair++;
                        }
                    }
                    System.out.println("[Night Siege test] dungeon stair after: on the stair=" + onStair
                            + " pushes=" + manager.expeditions().stairPushes());
                });
            }
            killDungeonMobs(context, "room " + (room + 1));
            if (room == 0) {
                context.waitTicks(3);
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    net.minecraft.world.phys.AABB around = player.getBoundingBox().inflate(48);
                    int items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around).size();
                    int orbs = level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, around).size();
                    System.out.println("[Night Siege test] dungeon drops after room 1: items=" + items + " orbs=" + orbs);
                });
            }
            context.waitTicks(25);
            final int gateZ = z0 + 30;
            final int gateX = ox;
            onServer(context, (player, manager) -> System.out.println("[Night Siege test] dungeon gate " + gateZ + ": "
                    + player.level().getBlockState(new BlockPos(gateX, fy - 20, gateZ)).getBlock()
                    + " | " + manager.expeditions().testState()));
        }
        // The arena: in, the gate closes, two waves from the monster gates, the champion from the pit.
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                fx, fy - 24, bz + cz - 14, fx, fy - 24, bz + cz));
        context.waitTicks(20);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] dungeon arena gate: "
                + player.level().getBlockState(new BlockPos(fx, fy - 24, bz + cz - 23)).getBlock()
                + " | " + manager.expeditions().testState()));
        for (int wave = 1; wave <= rooms; wave++) {
            context.waitTicks(wave == 1 ? 80 : 110);
            if (wave == 1) {
                context.takeScreenshot("dungeon_fight_wave");
                onServer(context, (player, manager) -> System.out.println("[Night Siege test] dungeon monster gate: "
                        + player.level().getBlockState(new BlockPos(fx + 23, fy - 24, bz + cz)).getBlock()));
            }
            killDungeonMobs(context, "wave " + wave);
            context.waitTicks(10);
        }
        context.waitTicks(110);
        context.takeScreenshot("dungeon_fight_champion");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Mob mob : manager.expeditions().testMobs(level)) {
                System.out.println("[Night Siege test] dungeon champion: " + mob.getName().getString() + " hp="
                        + Math.round(mob.getHealth()) + "/" + Math.round(mob.getMaxHealth()) + " at " + mob.blockPosition());
            }
        });
        context.waitTicks(90);   // his entrance (3 s, untouchable) is over
        context.takeScreenshot("dungeon_fight_champion2");
        // Update 30.10.: 2.5 times the life, and he leaps at a fighter who keeps away (no teleport).
        final double[] leapFrom = new double[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Mob mob : manager.expeditions().testMobs(level)) {
                if (mob.entityTags().contains(com.mki.nightsiege.game.Expeditions.CHAMPION_TAG)) {
                    // On the arena floor 14 blocks off (he may still stand in his pit, three lower).
                    int floor = manager.expeditions().testPlan().arenaCenter.getY() + 3;
                    player.teleportTo(level, mob.getX() + 14, floor, mob.getZ(), java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                    leapFrom[0] = Math.sqrt(player.distanceToSqr(mob));
                    System.out.println("[Night Siege test] dungeon champion life: " + Math.round(mob.getMaxHealth())
                            + " speed=" + String.format(java.util.Locale.ROOT, "%.3f", mob.getAttributeValue(
                                    net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED))
                            + " distance=" + Math.round(leapFrom[0]));
                }
            }
        });
        context.waitTicks(40);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Mob mob : manager.expeditions().testMobs(level)) {
                if (mob.entityTags().contains(com.mki.nightsiege.game.Expeditions.CHAMPION_TAG)) {
                    System.out.println("[Night Siege test] dungeon champion leap: leaps=" + manager.expeditions().championLeaps()
                            + " sees=" + mob.hasLineOfSight(player) + " ground=" + mob.onGround() + " noAi=" + mob.isNoAi()
                            + " distance " + Math.round(leapFrom[0]) + " -> " + Math.round(Math.sqrt(player.distanceToSqr(mob))));
                }
            }
        });
        final int[] pointsBefore = new int[1];
        onServer(context, (player, manager) -> pointsBefore[0] = manager.save().skillsOf(player.getUUID().toString()).points());
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Mob mob : manager.expeditions().testMobs(level)) {
                if (mob.entityTags().contains(com.mki.nightsiege.game.Expeditions.CHAMPION_TAG)) {
                    mob.invulnerableTime = 0;
                    mob.hurtServer(level, level.damageSources().playerAttack(player), mob.getMaxHealth() * 0.6f);   // phase 2
                }
            }
        });
        context.waitTicks(30);
        context.takeScreenshot("dungeon_fight_phase2");
        killDungeonMobs(context, "champion");
        context.waitTicks(25);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.SiegeSave.ExpeditionRecord rec = manager.save().expedition;
            int fragments = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = player.getInventory().getItem(i);
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().startsWith("night_fragment")) {
                    fragments += st.getCount();
                }
            }
            System.out.println("[Night Siege test] dungeon won=" + (rec != null && rec.won) + " fighter="
                    + manager.expeditions().isFighter(player) + " kit=" + manager.kits().hasStash(player)
                    + " points " + pointsBefore[0] + "->" + manager.save().skillsOf(player.getUUID().toString()).points()
                    + " fragments=" + fragments
                    + " protected=" + manager.expeditions().protects(level, new BlockPos(fx + 6, fy - 21, bz + 40))
                    + " | " + manager.expeditions().testState());
        });
        context.waitTicks(200);
        context.takeScreenshot("dungeon_fight_collapse");
        context.waitTicks(500);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] dungeon after the collapse: player at "
                + player.blockPosition() + " | " + manager.expeditions().testState()));
        context.waitTicks(200);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel lv = (net.minecraft.server.level.ServerLevel) player.level();
            long after = groundSum(lv, fx, bz, cz);
            int diff = 0;
            StringBuilder sb = new StringBuilder();
            for (int x = fx - 33; x <= fx + 33; x++) {
                for (int z = bz - 12; z <= bz + cz + 30; z++) {
                    for (int y = -60; y <= 12; y++) {
                        BlockPos at = new BlockPos(x, y, z);
                        net.minecraft.world.level.block.state.BlockState was = before.getOrDefault(at,
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        net.minecraft.world.level.block.state.BlockState now = lv.getBlockState(at);
                        if (was != now) {
                            if (diff < 12) {
                                sb.append(' ').append(x - fx).append('/').append(y).append('/').append(z - bz).append(':')
                                        .append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(was.getBlock()).getPath()).append("->")
                                        .append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath());
                            }
                            diff++;
                        }
                    }
                }
            }
            System.out.println("[Night Siege test] dungeon ground diff: " + diff + sb);
            System.out.println("[Night Siege test] dungeon gone: record=" + (manager.save().expedition != null)
                    + " ground before=" + sumBefore[0] + " after=" + after + " same=" + (sumBefore[0] == after)
                    + " | " + manager.expeditions().testState());
        });
        camera(game, fx + 9.5, fy + 6, bz - 15.5, fx + 0.5, fy + 3, bz + 0.5);
        context.waitTicks(30);
        context.takeScreenshot("dungeon_fight_gone");
        game.getServer().runCommand("gamemode creative @p");

        // A lost attempt: the illager keep, the only fighter falls in the first hall.
        int lx = bx + 2 * 72;
        int ly = floors[2];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            manager.expeditions().testBuild(level, new BlockPos(lx, ly, bz), 0, com.mki.nightsiege.core.ExpeditionKind.ILLAGER_KEEP, rooms);
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> manager.expeditions().openEntry(true));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5", lx, ly - 20, bz + 34));
        context.waitTicks(30);
        closeScreen(context);
        context.waitTicks(10);
        game.getServer().runCommand("effect clear @p");
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] dungeon before the fall: " + manager.expeditions().testState()
                    + " monsters=" + manager.expeditions().testMobs((net.minecraft.server.level.ServerLevel) player.level()).size());
            player.invulnerableTime = 0;
            player.hurtServer((net.minecraft.server.level.ServerLevel) player.level(), player.level().damageSources().generic(), 500f);
        });
        context.waitTicks(20);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] dungeon fallen: alive=" + player.isAlive()
                + " hp=" + Math.round(player.getHealth()) + " at=" + player.blockPosition() + " kit=" + manager.kits().hasStash(player)
                + " monsters=" + manager.expeditions().testMobs((net.minecraft.server.level.ServerLevel) player.level()).size()
                + " | " + manager.expeditions().testState()));
        onServer(context, (player, manager) -> {
            manager.expeditions().openEntry(false);
            manager.expeditions().openEntry(false);
            manager.expeditions().remove(player.level().getServer());
        });

        // The scouts' dungeon builds by itself once its ground is loaded and nobody is close (update 30.10.:
        // its plan worked out in the background, its blocks over many ticks): on loaded flat ground far off.
        int sx = bx + (kinds.length + 3) * 72;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(sx - 70, bz - 40, sx + 70, bz + 300));
        context.waitTicks(60);
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.SiegeSave.ExpeditionRecord rec = manager.expeditions().report(player.level().getServer(),
                    com.mki.nightsiege.core.ExpeditionKind.STONE_CRYPT);
            if (rec != null) {
                rec.x = sx;
                rec.z = bz;
                rec.facing = 0;
            }
            System.out.println("[Night Siege test] dungeon by itself: reported=" + (rec != null) + " at " + sx + " " + bz);
        });
        boolean[] stands = {false};
        int[] waitedTicks = {0};
        for (int i = 0; i < 60 && !stands[0]; i++) {
            context.waitTicks(20);
            waitedTicks[0] += 20;
            onServer(context, (player, manager) -> {
                com.mki.nightsiege.game.SiegeSave.ExpeditionRecord rec = manager.save().expedition;
                stands[0] = rec != null && rec.built && rec.complete;
            });
        }
        context.waitTicks(5);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
            System.out.println("[Night Siege test] dungeon by itself: stands=" + stands[0] + " after ~" + waitedTicks[0] + " ticks"
                    + " seal=" + (plan == null ? "?" : level.getBlockState(plan.seal).getBlock())
                    + " fire=" + (plan == null ? "?" : level.getBlockState(plan.fire).getBlock())
                    + " | " + manager.expeditions().testState());
            manager.expeditions().remove(level.getServer());
        });
        context.waitTicks(100);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] dungeon by itself gone: teardowns left=" + manager.expeditions().teardownsLeft()
                    + " | " + manager.expeditions().testState());
            if (fakeCore[0]) {
                manager.save().cores.removeIf(c -> c.x == pos.getX() && c.y == pos.getY() && c.z == pos.getZ());
            }
        });
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("time set 6000");
        context.waitTicks(10);
    }

    /**
     * The six siege machines (27 October 2026): set up around a walled yard in a test night,
     * photographed by day and by night, left to shoot for a while (holes, ropes, fire and
     * plague logged), one struck down.
     */
    /**
     * Shares of the max health (Simon, 28 October 2026): on top of the usual hit, armour does not
     * lessen them, a shield blocks them. Measured on a player in full diamond armour against the
     * grave king's blow, machine impacts and the lich (soul arrow, worn out after the shield).
     */
    private static void percentScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 240;
        int cz = pos.getZ() - 240;
        int cy = yard(context, game, cx, cz, 40, 14);
        int r = 9;
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx + r, cy + 3, cz - r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz + r, cx + r, cy + 3, cz + r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx - r, cy + 3, cz + r));
        game.getServer().runCommand(wall.formatted(cx + r, cy, cz - r, cx + r, cy + 3, cz + r));
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy, cz));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("effect give @p minecraft:saturation 600 10 true");
        context.waitTicks(20);
        final int[] saved = new int[2];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            saved[0] = manager.config().testPrepSeconds;
            saved[1] = manager.config().batchIntervalSeconds;
            manager.config().testPrepSeconds = 1;
            manager.config().batchIntervalSeconds = 600;   // no waves
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] percent level factor 6=%.2f 10=%.2f 20=%.2f 30=%.2f",
                    com.mki.nightsiege.game.PercentDamage.levelFactor(6, cfg), com.mki.nightsiege.game.PercentDamage.levelFactor(10, cfg),
                    com.mki.nightsiege.game.PercentDamage.levelFactor(20, cfg), com.mki.nightsiege.game.PercentDamage.levelFactor(30, cfg)));
            boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 10,
                    com.mki.nightsiege.core.SiegeEvent.NONE, com.mki.nightsiege.core.BossKind.GRAVE_KING);
            System.out.println("[Night Siege test] percent prep ok=" + ok);
        });
        context.waitTicks(60);
        closeScreen(context);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean now = manager.bossNow(level.getServer());
            net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
            if (boss != null) {
                boss.setNoAi(true);
                boss.teleportTo(cx + 0.5, cy, cz + 2.5);   // in front of the player (he looks south)
            }
            dressDiamond(player);
            System.out.println("[Night Siege test] percent boss=" + now + " armor=" + player.getArmorValue() + " max=" + player.getMaxHealth());
        });
        context.waitTicks(5);
        // A: the share alone, 30 % (armour does not count).
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
            player.setHealth(player.getMaxHealth());
            float aimed = com.mki.nightsiege.game.PercentDamage.hit(level, player, boss, boss, 30.0, 10, manager.config(), 1.0);
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] percent share 30%% aimed=%.1f took=%.1f (armour %d)",
                    aimed, player.getMaxHealth() - player.getHealth(), player.getArmorValue()));
        });
        context.waitTicks(15);
        // B: the grave king's blow: his damage through the armour plus 12 %.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            boolean hit = boss != null && boss.doHurtTarget(level, player);
            float share = com.mki.nightsiege.game.PercentDamage.amount(player, 12.0, 10, manager.config(), manager.config().bossPercentDamage);
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] percent king blow hit=%s took=%.1f share=%.1f",
                    hit, player.getMaxHealth() - player.getHealth(), share));
        });
        context.waitTicks(15);
        // C: the same blow on a raised shield: nothing.
        onServer(context, (player, manager) -> {
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
            player.setHealth(player.getMaxHealth());
            player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
        });
        context.waitTicks(12);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
            player.invulnerableTime = 0;
            boolean blocking = player.isBlocking();
            if (boss != null) {
                boss.doHurtTarget(level, player);
            }
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] percent shield blocking=%s took=%.1f",
                    blocking, player.getMaxHealth() - player.getHealth()));
            player.stopUsingItem();
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
        });
        context.waitTicks(10);
        // D: machine impacts right at the player.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
            if (boss != null) {
                boss.teleportTo(cx + 0.5, cy, cz + 30.5);   // out of the way
            }
            com.mki.nightsiege.core.SiegeMachineKind[] kinds = {com.mki.nightsiege.core.SiegeMachineKind.TREBUCHET,
                    com.mki.nightsiege.core.SiegeMachineKind.MORTAR, com.mki.nightsiege.core.SiegeMachineKind.FIRE};
            for (int i = 0; i < kinds.length; i++) {
                double a = Math.PI * 2.0 * i / kinds.length;
                double x = cx + 0.5 + Math.cos(a) * 26;
                double z = cz + 0.5 + Math.sin(a) * 26;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        (int) Math.floor(x), (int) Math.floor(z));
                manager.testMachine(kinds[i], new net.minecraft.world.phys.Vec3(Math.floor(x) + 0.5, y, Math.floor(z) + 0.5));
            }
            manager.machines().testHoldFire(level.getGameTime() + 4000);
            for (com.mki.nightsiege.core.SiegeMachineKind kind : kinds) {
                player.setHealth(player.getMaxHealth());
                player.clearFire();
                boolean ok = manager.testMachineImpact(kind, player);
                float share = com.mki.nightsiege.game.PercentDamage.amount(player,
                        kind == com.mki.nightsiege.core.SiegeMachineKind.MORTAR ? 30.0 : kind == com.mki.nightsiege.core.SiegeMachineKind.TREBUCHET ? 25.0 : 10.0,
                        10, manager.config(), manager.config().machinePercentDamage);
                System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] percent machine %s ok=%s took=%.1f share=%.1f",
                        kind.id(), ok, player.getMaxHealth() - player.getHealth(), share));
            }
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            manager.machines().removeAll();
            manager.abort(player.level().getServer());
        });
        context.waitTicks(40);
        // E: the lich – soul arrows, then his shield broken: worn out on the ground.
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy, cz));
        game.getServer().runCommand("effect give @p minecraft:regeneration 600 3 true");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            player.setHealth(player.getMaxHealth());
            boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 10,
                    com.mki.nightsiege.core.SiegeEvent.NONE, com.mki.nightsiege.core.BossKind.BONE_LICH);
            System.out.println("[Night Siege test] percent lich prep ok=" + ok);
        });
        context.waitTicks(60);
        closeScreen(context);
        onServer(context, (player, manager) -> manager.bossNow(player.level().getServer()));
        final float[] low = {99f};
        final boolean[] slowed = {false};
        for (int t = 0; t < 10; t++) {
            context.waitTicks(10);
            onServer(context, (player, manager) -> {
                low[0] = Math.min(low[0], player.getHealth());
                slowed[0] |= player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
            });
        }
        onServer(context, (player, manager) -> System.out.println(String.format(java.util.Locale.ROOT,
                "[Night Siege test] percent lich arrows: lowest hp=%.1f of %.0f slowed=%s", low[0], player.getMaxHealth(), slowed[0])));
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (java.util.UUID part : manager.bosses().partHitboxes()) {
                net.minecraft.world.entity.Entity e = level.getEntity(part);
                if (e instanceof net.minecraft.world.entity.LivingEntity crystal) {
                    crystal.invulnerableTime = 0;
                    crystal.hurtServer(level, level.damageSources().playerAttack(player), 5000f);
                }
            }
        });
        final double[] start = new double[1];
        for (int t = 0; t < 9; t++) {
            context.waitTicks(20);
            final int sec = t + 1;
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
                if (boss == null) {
                    return;
                }
                if (sec == 1) {
                    start[0] = boss.getY();
                }
                int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        boss.getBlockX(), boss.getBlockZ());
                if (sec == 1 || sec == 3 || sec == 6 || sec == 9) {
                    System.out.println(String.format(java.util.Locale.ROOT,
                            "[Night Siege test] percent lich t=%ds exhausted=%s above ground=%.1f parts=%d",
                            sec, manager.bosses().testLichExhausted(level.getGameTime()), boss.getY() - ground,
                            manager.bosses().partHitboxes().size()));
                }
            });
        }
        onServer(context, (player, manager) -> {
            manager.abort(player.level().getServer());
            manager.config().testPrepSeconds = saved[0];
            manager.config().batchIntervalSeconds = saved[1];
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.item.ItemStack.EMPTY);
        });
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(20);
    }

    private static void dressDiamond(net.minecraft.server.level.ServerPlayer player) {
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS));
    }

    /**
     * A spider under an overhang: a wall six high with a ledge jutting out two blocks at the
     * top, a player behind it. Six seconds without the fix (it hangs under the ledge), then
     * with it (it swings out and over).
     */
    private static void overhangScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 240;
        int cz = pos.getZ() + 240;
        int cy = yard(context, game, cx, cz, 24, 12);
        String fill = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        game.getServer().runCommand(fill.formatted(cx - 6, cy, cz, cx + 6, cy + 5, cz + 2));          // the wall
        game.getServer().runCommand(fill.formatted(cx - 6, cy + 5, cz - 2, cx + 6, cy + 5, cz - 1));  // the ledge
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 180 20", cx, cy, cz + 7));
        context.waitTicks(20);
        final java.util.UUID[] id = new java.util.UUID[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.monster.spider.Spider spider = net.minecraft.world.entity.EntityTypes.SPIDER.create(level,
                    net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (spider != null) {
                spider.snapTo(cx + 0.5, cy, cz - 0.5, 0f, 0f);
                spider.setPersistenceRequired();
                level.addFreshEntity(spider);
                spider.setTarget(player);
                id[0] = spider.getUUID();
            }
        });
        for (int t = 0; t < 2; t++) {
            context.waitTicks(60);
            final int sec = (t + 1) * 3;
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.entity.Entity e = id[0] == null ? null : level.getEntity(id[0]);
                if (e instanceof net.minecraft.world.entity.Mob m) {
                    m.setTarget(player);
                }
                System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] overhang without fix t=%ds spider y=%s climbing=%s",
                        sec, e == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f", e.getY() - cy),
                        e instanceof net.minecraft.world.entity.LivingEntity l && l.onClimbable()));
            });
        }
        context.takeScreenshot("overhang_stuck");
        onServer(context, (player, manager) -> com.mki.nightsiege.game.SpiderOverhang.watch(id[0]));
        for (int t = 0; t < 4; t++) {
            context.waitTicks(40);
            final int sec = (t + 1) * 2;
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.entity.Entity e = id[0] == null ? null : level.getEntity(id[0]);
                if (e instanceof net.minecraft.world.entity.Mob m) {
                    m.setTarget(player);
                }
                System.out.println(String.format(java.util.Locale.ROOT,
                        "[Night Siege test] overhang with fix t=%ds spider y=%s z=%s (wall z 0..2, top y 6, inside z>2) %s",
                        sec, e == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f", e.getY() - cy),
                        e == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f", e.getZ() - cz),
                        com.mki.nightsiege.game.SpiderOverhang.testCounts()));
            });
        }
        context.takeScreenshot("overhang_over");
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.SpiderOverhang.watch(null);
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Entity e = id[0] == null ? null : level.getEntity(id[0]);
            if (e != null) {
                e.discard();
            }
        });
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);
    }

    private static void machineScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 200;
        int cz = pos.getZ() - 200;
        int cy = yard(context, game, cx, cz, 40, 14);
        int r = 9;
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx + r, cy + 4, cz - r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz + r, cx + r, cy + 4, cz + r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx - r, cy + 4, cz + r));
        game.getServer().runCommand(wall.formatted(cx + r, cy, cz - r, cx + r, cy + 4, cz + r));
        game.getServer().runCommand("time set 13000");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5", cx, cy, cz));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 600 10 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 600 10 true");
        context.waitTicks(20);
        final int[] saved = new int[2];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            saved[0] = manager.config().testPrepSeconds;
            saved[1] = manager.config().batchIntervalSeconds;
            manager.config().testPrepSeconds = 1;
            manager.config().batchIntervalSeconds = 600;   // no waves: only the machines
            boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 12,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] machines prep ok=" + ok);
        });
        context.waitTicks(60);
        closeScreen(context);
        com.mki.nightsiege.core.SiegeMachineKind[] kinds = com.mki.nightsiege.core.SiegeMachineKind.values();
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (int i = 0; i < kinds.length; i++) {
                double a = Math.PI * 2.0 * i / kinds.length + Math.PI / 6;
                double x = cx + 0.5 + Math.cos(a) * 24;
                double z = cz + 0.5 + Math.sin(a) * 24;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        (int) Math.floor(x), (int) Math.floor(z));
                boolean ok = manager.testMachine(kinds[i], new net.minecraft.world.phys.Vec3(Math.floor(x) + 0.5, y, Math.floor(z) + 0.5));
                System.out.println("[Night Siege test] machine " + kinds[i].id() + " ok=" + ok);
            }
            System.out.println("[Night Siege test] machines standing=" + manager.machines().standing()
                    + " crew=" + manager.machines().testCrew());
        });
        context.waitTicks(20);
        // Each machine up close by day, then all of them from above.
        game.getServer().runCommand("time set 6000");
        context.runOnClient(client -> hud(client, false));
        for (int i = 0; i < kinds.length; i++) {
            double a = Math.PI * 2.0 * i / kinds.length + Math.PI / 6;
            double x = cx + 0.5 + Math.cos(a) * 24;
            double z = cz + 0.5 + Math.sin(a) * 24;
            // From the side, a little above: the crew stands behind the machine.
            double sx = x + Math.cos(a + Math.PI / 2) * 8.5 + Math.cos(a) * 2.0;
            double sz = z + Math.sin(a + Math.PI / 2) * 8.5 + Math.sin(a) * 2.0;
            camera(game, sx, cy + 4, sz, x, cy + 1.8, z);
            context.waitTicks(30);
            context.takeScreenshot("machine_" + kinds[i].id());
        }
        camera(game, cx + 34.5, cy + 26, cz + 34.5, cx + 0.5, cy, cz + 0.5);
        context.waitTicks(40);
        context.takeScreenshot("machines_above");
        context.runOnClient(client -> hud(client, true));
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5", cx, cy + 5, cz));
        game.getServer().runCommand("setblock %d %d %d minecraft:barrier".formatted(cx, cy + 4, cz));
        game.getServer().runCommand("time set 18000");
        // Let them shoot.
        for (int i = 0; i < 40; i++) {
            final int step = i;
            if (i % 10 == 0) {
                onServer(context, (player, manager) -> manager.machines().testFireNow(player.level().getGameTime()));
            }
            context.waitTicks(20);
            if (i == 3 || i == 12) {
                closeScreen(context);
                context.takeScreenshot("machines_fire_" + i);
            }
            if (step % 10 == 9) {
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    int holes = 0;
                    for (int x = cx - r; x <= cx + r; x++) {
                        for (int z = cz - r; z <= cz + r; z++) {
                            if (Math.abs(x - cx) != r && Math.abs(z - cz) != r) {
                                continue;
                            }
                            for (int y = cy; y <= cy + 4; y++) {
                                if (level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                                    holes++;
                                }
                            }
                        }
                    }
                    System.out.println(String.format(java.util.Locale.ROOT,
                            "[Night Siege test] machines t=%ds active=%s standing=%d crew=%d ropes=%d wallgaps=%d hp=%d fire=%s poison=%s",
                            step + 1, manager.isActive(), manager.machines().standing(), manager.machines().testCrew(),
                            manager.machines().testRopes(), holes, Math.round(player.getHealth()), player.isOnFire(),
                            player.hasEffect(net.minecraft.world.effect.MobEffects.POISON)));
                });
            }
        }
        // Weapons do nothing; one is sabotaged by hand (hold right-click, 4 s).
        final double[] at = new double[3];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            java.util.List<java.util.UUID> boxes = manager.machines().testHitboxes();
            if (!boxes.isEmpty() && level.getEntity(boxes.get(0)) instanceof net.minecraft.world.entity.LivingEntity box) {
                box.invulnerableTime = 0;
                box.hurtServer(level, level.damageSources().playerAttack(player), 5000f);
                at[0] = box.getX();
                at[1] = box.getY();
                at[2] = box.getZ();
            }
            System.out.println("[Night Siege test] machines after a 5000 blow: standing=" + manager.machines().standing());
        });
        context.waitTicks(5);
        // A hit interrupts the work (checked above in the log): first the guards and workers go,
        // the machines hold their fire, then the sabotage runs through.
        game.getServer().runCommand("kill @e[tag=nightsiege_machinecrew]");
        game.getServer().runCommand("kill @e[type=!minecraft:player,tag=nightsiege,tag=!nightsiege_machine]");
        onServer(context, (player, manager) -> manager.machines().testHoldFire(player.level().getGameTime() + 4000));
        context.waitTicks(5);
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f facing %.2f %.2f %.2f",
                at[0] + 2.0, at[1], at[2] + 1.0, at[0], at[1] + 1.0, at[2]));
        context.waitTicks(5);
        for (int i = 0; i < 90; i++) {   // the photo stalls the client: the work starts over after it
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                java.util.List<java.util.UUID> boxes = manager.machines().testHitboxes();
                net.minecraft.world.entity.Entity box = boxes.isEmpty() ? null : level.getEntity(boxes.get(0));
                if (player.distanceToSqr(at[0], at[1], at[2]) > 9) {
                    player.teleportTo(at[0] + 2.0, at[1], at[2] + 1.0);   // shells knock the tester away
                }
                boolean used = manager.machines().onUse(player, box, manager.config());
                if (level.getGameTime() % 20 < 3) {
                    System.out.println("[Night Siege test] sabotage used=" + used + " d=" + (box == null ? -1
                            : Math.round(Math.sqrt(player.distanceToSqr(box)) * 10) / 10.0) + " standing=" + manager.machines().standing()
                            + " work=" + manager.machines().testSabotage() + " hp=" + player.getHealth());
                }
            });
            context.waitTicks(3);
            if (i == 14) {
                context.takeScreenshot("machines_sabotage");
            }
        }
        onServer(context, (player, manager) ->
                System.out.println("[Night Siege test] machines after sabotage: standing=" + manager.machines().standing()));
        context.waitTicks(20);
        onServer(context, (player, manager) -> {
            manager.abort(player.level().getServer());
            manager.config().testPrepSeconds = saved[0];
            manager.config().batchIntervalSeconds = saved[1];
            System.out.println("[Night Siege test] machines after the night: standing=" + manager.machines().standing());
        });
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);
    }

    /**
     * Simon 30.10., 16:12: "mehrere Monster sammeln sich unten an der Leiter und keins kommt hoch".
     * A real test night at a closed yard (walls 5 high, no gate, no sappers): the ladder carriers
     * put up ladders, the raid sends the melee monsters to them. Every 5 s: how many monsters are
     * over the wall, how many stand at a ladder's foot and what the ladder climbing does with them.
     */
    /**
     * The ladder storm on the copy of Simon's castle (tools/sim/burg.json.gz, 29.10. 17:40): a test
     * night of level 10 with ladder carriers and two camel troops, the defender at the core. Every
     * 5 s: who stands at a ladder's foot and what the ladders did.
     */
    private static void ladderCastleScene(ClientGameTestContext context, TestSingleplayerContext game) {
        java.nio.file.Path copy = null;
        for (java.nio.file.Path d = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath(); d != null; d = d.getParent()) {
            java.nio.file.Path f = d.resolve("tools/sim/burg.json.gz");
            if (java.nio.file.Files.isRegularFile(f)) {
                copy = f;
                break;
            }
        }
        System.out.println("[Night Siege test] leiterburg Kopie: " + copy);
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int nx = pos.getX() - 300;
        int nz = pos.getZ() + 300;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(nx - 80, nz - 80, nx + 80, nz + 80));
        context.waitTicks(60);
        final BlockPos[] core = new BlockPos[1];
        final java.nio.file.Path copyFile = copy;
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.SimCastle.Ground g = com.mki.nightsiege.game.SimCastle.build(level, new BlockPos(nx, 70, nz), copyFile);
            core[0] = g.core();
            System.out.println("[Night Siege test] leiterburg Boden " + g.kind() + " " + g.blocks() + " Blöcke, Core " + g.core().toShortString());
        });
        context.waitTicks(40);
        int cx = core[0].getX();
        int cy = core[0].getY();
        int cz = core[0].getZ();
        game.getServer().runCommand("time set 13000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("kill @e[type=!player,x=%d,y=%d,z=%d,distance=..90]".formatted(cx, cy, cz));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 0".formatted(cx, cy + 1, cz + 2));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 200 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 200 10 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 200 10 true");
        game.getServer().runCommand("effect give @p minecraft:night_vision 200 0 true");
        context.waitTicks(20);
        final double[] savedD = new double[1];
        final int[] savedI = new int[2];
        final boolean[] savedB = new boolean[3];
        final java.util.List<Integer> savedCap = new java.util.ArrayList<>();
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            savedD[0] = cfg.ladderCarrierShare;
            savedI[0] = cfg.batchIntervalSeconds;
            savedI[1] = cfg.testPrepSeconds;
            savedB[0] = cfg.machinesEnabled;
            savedB[1] = cfg.cursesEnabled;
            savedB[2] = cfg.nemesisEnabled;
            savedCap.addAll(cfg.liveCapByPlayers);
            cfg.ladderCarrierShare = 0.15;
            cfg.batchIntervalSeconds = 10;
            cfg.testPrepSeconds = 2;
            cfg.machinesEnabled = false;
            cfg.cursesEnabled = false;
            cfg.nemesisEnabled = false;
            cfg.liveCapByPlayers = new java.util.ArrayList<>(java.util.List.of(30));
            boolean ok = manager.prepareTest(level.getServer(), level, core[0], 10, com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] leiterburg prep ok=" + ok);
        });
        context.waitTicks(10);
        closeScreen(context);
        final boolean[] camels = {false};
        final int[] best = new int[1];
        for (int i = 0; i < 24; i++) {
            final int step = i;
            context.waitTicks(100);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                long now = level.getGameTime();
                java.util.Map<Long, BlockPos> feet = new java.util.HashMap<>();
                for (BlockPos p : com.mki.nightsiege.game.TempBlocks.all(level, net.minecraft.world.level.block.Blocks.LADDER)) {
                    if (!level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.LADDER)) {
                        continue;
                    }
                    long col = ((long) p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL);
                    BlockPos f = feet.get(col);
                    if (f == null || p.getY() < f.getY()) {
                        feet.put(col, p);
                    }
                }
                if (!camels[0] && !feet.isEmpty()) {
                    BlockPos p = feet.values().iterator().next();
                    net.minecraft.core.Direction outDir = level.getBlockState(p).getValue(net.minecraft.world.level.block.LadderBlock.FACING);
                    for (int k = 0; k < 2; k++) {
                        int sx = p.getX() + outDir.getStepX() * (7 + k * 3);
                        int sz = p.getZ() + outDir.getStepZ() * (7 + k * 3);
                        BlockPos spot = new BlockPos(sx, level.getHeight(
                                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz), sz);
                        net.minecraft.world.entity.Mob troop = spot == null ? null
                                : com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level, com.mki.nightsiege.core.SiegeConfig.CAMEL_TROOP, spot);
                        System.out.println("[Night Siege test] leiterburg Kamel-Trupp: " + (troop == null ? "keiner" : "Reiter " + troop.getPassengers().size()));
                    }
                    camels[0] = true;
                }
                int total = 0;
                int atFoot = 0;
                int stuckAtFoot = 0;
                java.util.Map<String, Integer> states = new java.util.TreeMap<>();
                java.util.List<String> footDetail = new java.util.ArrayList<>();
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (!(e instanceof net.minecraft.world.entity.Mob mob) || !e.isAlive()
                            || !e.entityTags().contains(com.mki.nightsiege.game.MobFactory.TAG)) {
                        continue;
                    }
                    total++;
                    com.mki.nightsiege.game.RaidPlan.State st = manager.raid().stateOf(e.getUUID());
                    states.merge(String.valueOf(st), 1, Integer::sum);
                    for (BlockPos f : feet.values()) {
                        double dx = e.getX() - (f.getX() + 0.5);
                        double dz = e.getZ() - (f.getZ() + 0.5);
                        if (dx * dx + dz * dz < 9.0 && Math.abs(e.getY() - f.getY()) < 1.5) {
                            atFoot++;
                            if (!mob.onClimbable()) {
                                stuckAtFoot++;
                            }
                            footDetail.add(String.format(java.util.Locale.ROOT, "%s@%.1f,%.1f,%.1f fuss=%s plan=%s ziel=%s klettert=%s %s",
                                    e.getType().toShortString(), e.getX() - cx, e.getY() - cy, e.getZ() - cz,
                                    f.offset(-cx, -cy, -cz).toShortString(), st,
                                    mob.getTarget() == null ? "-" : mob.getTarget().getName().getString(), mob.onClimbable(),
                                    manager.ladders().describe(e.getUUID(), now)));
                            break;
                        }
                    }
                }
                best[0] = Math.max(best[0], stuckAtFoot);
                System.out.println(String.format(java.util.Locale.ROOT,
                        "[Night Siege test] leiterburg t=%ds aktiv=%s leitern=%d monster=%d am_fuss=%d plan=%s abgesessen=%d | %s",
                        (step + 1) * 5, manager.isActive(), feet.size(), total, atFoot, states, manager.raid().dismounted,
                        manager.ladders().stats()));
                if (step % 4 == 3) {
                    for (String d : footDetail) {
                        System.out.println("[Night Siege test] leiterburg   am Fuß: " + d);
                    }
                }
            });
            if (i == 11 || i == 23) {
                closeScreen(context);
                context.takeScreenshot("leiterburg_" + (i + 1) * 5);
            }
        }
        System.out.println("[Night Siege test] leiterburg ERGEBNIS: höchstens " + best[0] + " Monster gleichzeitig am Fuß, ohne zu klettern");
        onServer(context, (player, manager) -> {
            manager.abort(player.level().getServer());
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            cfg.ladderCarrierShare = savedD[0];
            cfg.batchIntervalSeconds = savedI[0];
            cfg.testPrepSeconds = savedI[1];
            cfg.machinesEnabled = savedB[0];
            cfg.cursesEnabled = savedB[1];
            cfg.nemesisEnabled = savedB[2];
            cfg.liveCapByPlayers = new java.util.ArrayList<>(savedCap);
        });
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);
    }

    private static void ladderCrowdScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 120;
        int cz = pos.getZ() + 120;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 48, cz - 48, cx + 48, cz + 48));
        context.waitTicks(40);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);
        int cy = ground[0];
        int r = 12;
        int out = 44;
        game.getServer().runCommand("time set 13000");
        game.getServer().runCommand("weather clear");
        for (int part = -out; part <= out; part += 22) {
            int z1 = cz + part;
            int z2 = Math.min(cz + out, z1 + 21);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - out, cy - 1, z1, cx + out, cy - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - out, cy, z1, cx + out, cy + 12, z2));
        }
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx + r, cy + 4, cz - r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz + r, cx + r, cy + 4, cz + r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx - r, cy + 4, cz + r));
        game.getServer().runCommand(wall.formatted(cx + r, cy, cz - r, cx + r, cy + 4, cz + r));
        game.getServer().runCommand("kill @e[type=!player,x=%d,y=%d,z=%d,distance=..70]".formatted(cx, cy, cz));
        // The defender in the middle of the yard, looking north over the wall.
        game.getServer().runCommand("tp @p %d.5 %d %d.5 180 -12".formatted(cx, cy, cz + 4));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 200 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 200 10 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 200 10 true");
        game.getServer().runCommand("effect give @p minecraft:night_vision 200 0 true");
        context.waitTicks(20);
        final double[] savedD = new double[1];
        final int[] savedI = new int[3];
        final boolean[] savedB = new boolean[3];
        final java.util.List<Integer> savedCapCrowd = new java.util.ArrayList<>();
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            savedD[0] = cfg.ladderCarrierShare;
            savedI[0] = cfg.sapperFromLevel;
            savedI[1] = cfg.batchIntervalSeconds;
            savedI[2] = cfg.testPrepSeconds;
            savedB[0] = cfg.machinesEnabled;
            savedB[1] = cfg.cursesEnabled;
            savedB[2] = cfg.nemesisEnabled;
            cfg.ladderCarrierShare = 0.0;   // the two ladders below, the same in every run
            cfg.sapperFromLevel = 999;   // no craters: the ladders are the only way in
            cfg.batchIntervalSeconds = 10;
            cfg.testPrepSeconds = 2;
            cfg.machinesEnabled = false;
            cfg.cursesEnabled = false;
            cfg.nemesisEnabled = false;
            savedCapCrowd.clear();
            savedCapCrowd.addAll(cfg.liveCapByPlayers);
            cfg.liveCapByPlayers = new java.util.ArrayList<>(java.util.List.of(30));
            boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 8,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            // Two night ladders like the carriers', on the outside of the north and the east wall.
            for (int y = cy; y <= cy + 4; y++) {
                com.mki.nightsiege.game.TempBlocks.place(level, new BlockPos(cx + 3, y, cz - r - 1),
                        net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.NORTH));
                com.mki.nightsiege.game.TempBlocks.place(level, new BlockPos(cx + r + 1, y, cz - 2),
                        net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST));
            }
            System.out.println("[Night Siege test] leitersturm yard " + cx + " " + cy + " " + cz + " prep ok=" + ok);
        });
        context.waitTicks(10);
        closeScreen(context);
        final int[] best = new int[2];
        final boolean[] camels = {false};
        for (int i = 0; i < 24; i++) {
            final int step = i;
            context.waitTicks(100);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                long now = level.getGameTime();
                if (!camels[0]) {
                    // Simon's crowd: camel troops in front of the first ladder (they cannot climb it).
                    for (BlockPos p : com.mki.nightsiege.game.TempBlocks.all(level, net.minecraft.world.level.block.Blocks.LADDER)) {
                        net.minecraft.world.level.block.state.BlockState ls = level.getBlockState(p);
                        if (!ls.is(net.minecraft.world.level.block.Blocks.LADDER)) {
                            continue;
                        }
                        net.minecraft.core.Direction outDir = ls.getValue(net.minecraft.world.level.block.LadderBlock.FACING);
                        for (int k = 0; k < 2; k++) {
                            BlockPos spot = new BlockPos(p.getX(), cy, p.getZ()).relative(outDir, 7 + k * 3);
                            net.minecraft.world.entity.Mob troop = com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level,
                                    com.mki.nightsiege.core.SiegeConfig.CAMEL_TROOP, spot);
                            System.out.println("[Night Siege test] leitersturm Kamel-Trupp bei " + spot.offset(-cx, -cy, -cz).toShortString()
                                    + ": " + (troop == null ? "keiner" : troop.getType().toShortString() + " Reiter " + troop.getPassengers().size()));
                        }
                        camels[0] = true;
                        break;
                    }
                }
                // The feet of the night's ladders outside the wall.
                java.util.Map<Long, BlockPos> feet = new java.util.HashMap<>();
                for (BlockPos p : com.mki.nightsiege.game.TempBlocks.all(level, net.minecraft.world.level.block.Blocks.LADDER)) {
                    if (!level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.LADDER)) {
                        continue;
                    }
                    long col = ((long) p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL);
                    BlockPos f = feet.get(col);
                    if (f == null || p.getY() < f.getY()) {
                        feet.put(col, p);
                    }
                }
                int total = 0;
                int inside = 0;
                int onWall = 0;
                int atFoot = 0;
                java.util.Map<String, Integer> states = new java.util.TreeMap<>();
                java.util.List<String> footDetail = new java.util.ArrayList<>();
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (!(e instanceof net.minecraft.world.entity.Mob mob) || !e.isAlive()
                            || !e.entityTags().contains(com.mki.nightsiege.game.MobFactory.TAG)) {
                        continue;
                    }
                    total++;
                    int ax = Math.abs(e.getBlockX() - cx);
                    int az = Math.abs(e.getBlockZ() - cz);
                    if (ax < r && az < r) {
                        inside++;
                    } else if (Math.max(ax, az) <= r && e.getY() >= cy + 4.5) {
                        onWall++;
                    }
                    com.mki.nightsiege.game.RaidPlan.State st = manager.raid().stateOf(e.getUUID());
                    states.merge(String.valueOf(st), 1, Integer::sum);
                    for (BlockPos f : feet.values()) {
                        double dx = e.getX() - (f.getX() + 0.5);
                        double dz = e.getZ() - (f.getZ() + 0.5);
                        if (dx * dx + dz * dz < 9.0 && e.getY() < f.getY() + 1.5) {
                            atFoot++;
                            footDetail.add(String.format(java.util.Locale.ROOT, "%s@%.1f,%.1f,%.1f fuss=%s plan=%s ziel=%s klettert=%s %s",
                                    e.getType().toShortString(), e.getX() - cx, e.getY() - cy, e.getZ() - cz,
                                    f.offset(-cx, -cy, -cz).toShortString(), st,
                                    mob.getTarget() == null ? "-" : mob.getTarget().getName().getString(), mob.onClimbable(),
                                    manager.ladders().describe(e.getUUID(), now)));
                            break;
                        }
                    }
                }
                best[0] = Math.max(best[0], inside + onWall);
                best[1] = Math.max(best[1], atFoot);
                System.out.println(String.format(java.util.Locale.ROOT,
                        "[Night Siege test] leitersturm t=%ds aktiv=%s leitern=%d monster=%d drin=%d mauer=%d am_fuss=%d plan=%s abgesessen=%d | %s",
                        (step + 1) * 5, manager.isActive(), feet.size(), total, inside, onWall, atFoot, states,
                        manager.raid().dismounted, manager.ladders().stats()));
                if (step % 4 == 3) {
                    for (String d : footDetail) {
                        System.out.println("[Night Siege test] leitersturm   am Fuß: " + d);
                    }
                }
            });
            if (i == 9 || i == 17 || i == 23) {
                closeScreen(context);
                context.takeScreenshot("leitersturm_" + (i + 1) * 5);
            }
        }
        System.out.println("[Night Siege test] leitersturm ERGEBNIS: höchstens " + best[0] + " Monster drin/auf der Mauer, "
                + "höchstens " + best[1] + " am Fuß einer Leiter");
        onServer(context, (player, manager) -> {
            manager.abort(player.level().getServer());
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            cfg.ladderCarrierShare = savedD[0];
            cfg.sapperFromLevel = savedI[0];
            cfg.batchIntervalSeconds = savedI[1];
            cfg.testPrepSeconds = savedI[2];
            cfg.machinesEnabled = savedB[0];
            cfg.cursesEnabled = savedB[1];
            cfg.nemesisEnabled = savedB[2];
            cfg.liveCapByPlayers = new java.util.ArrayList<>(savedCapCrowd);
        });
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);
    }

    private static void besiegerScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 120;
        int cz = pos.getZ() + 120;
        // On the server, with the chunk loaded: the client does not know chunks this far away
        // (its height there was the bottom of the world, so the yard was buried).
        // Fill and setblock only work in loaded chunks: keep the whole yard loaded for the scene.
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 40, cz - 40, cx + 40, cz + 40));
        context.waitTicks(40);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);   // onServer runs on the next server tick
        int cy = ground[0];
        System.out.println("[Night Siege test] besiegers yard at " + cx + " " + cy + " " + cz);
        int r = 10;
        int out = 38;
        game.getServer().runCommand("time set 13000");
        for (int part = -out; part <= out; part += 19) {
            int z1 = cz + part;
            int z2 = Math.min(cz + out, z1 + 18);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - out, cy - 1, z1, cx + out, cy - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - out, cy, z1, cx + out, cy + 9, z2));
        }
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx + r, cy + 4, cz - r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz + r, cx + r, cy + 4, cz + r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx - r, cy + 4, cz + r));
        game.getServer().runCommand(wall.formatted(cx + r, cy, cz - r, cx + r, cy + 4, cz + r));
        // Watch from above the north side, outside the wall (ladders lean on the outside).
        game.getServer().runCommand("setblock %d %d %d minecraft:barrier".formatted(cx, cy + 12, cz - 20));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 35".formatted(cx, cy + 13, cz - 20));
        // A real defender (survival), who cannot fall.
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 120 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 120 10 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 120 10 true");
        context.waitTicks(20);
        final double[] before = new double[2];
        final int[] beforeInt = new int[2];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            before[0] = manager.config().ladderCarrierShare;
            beforeInt[0] = manager.config().sapperFromLevel;
            beforeInt[1] = manager.config().batchIntervalSeconds;
            manager.config().ladderCarrierShare = 1.0;
            manager.config().sapperFromLevel = 1;
            manager.config().batchIntervalSeconds = 12;
            manager.config().testPrepSeconds = 2;
            boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 6,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] besiegers prep ok=" + ok);
        });
        // The class window opens with every night; it would cover the pictures.
        context.waitTicks(10);
        closeScreen(context);
        for (int i = 0; i < 45; i++) {
            final int step = i;
            context.waitTicks(20);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                int ladders = com.mki.nightsiege.game.TempBlocks.all(level, net.minecraft.world.level.block.Blocks.LADDER).size();
                int carriers = 0;
                int sappers = 0;
                double carrierNear = 999;
                double sapperNear = 999;
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    double d = Math.sqrt(e.distanceToSqr(cx + 0.5, e.getY(), cz + 0.5));
                    if (e.entityTags().contains(com.mki.nightsiege.game.Besiegers.CARRIER)) {
                        carriers++;
                        carrierNear = Math.min(carrierNear, d);
                    }
                    if (e.entityTags().contains(com.mki.nightsiege.game.Besiegers.SAPPER)) {
                        sappers++;
                        sapperNear = Math.min(sapperNear, d);
                    }
                }
                if (step == 0) {
                    System.out.println("[Night Siege test] besiegers wall=" + level.getBlockState(new BlockPos(cx + r, cy + 2, cz)).getBlock()
                            + " player=" + player.blockPosition());
                }
                if (step % 5 == 4) {
                    System.out.println(String.format(java.util.Locale.ROOT,
                            "[Night Siege test] besiegers t=%ds active=%s ladders=%d carriers=%d (nearest %.1f) sappers=%d (nearest %.1f) holes=%d",
                            step + 1, manager.isActive(), ladders, carriers, carrierNear, sappers, sapperNear,
                            manager.besiegerBreaches()));
                }
            });
            if (i == 20 || i == 40) {
                closeScreen(context);
                context.takeScreenshot("besiegers_" + i);
            }
        }
        onServer(context, (player, manager) -> {
            manager.abort(player.level().getServer());
            manager.config().ladderCarrierShare = before[0];
            manager.config().sapperFromLevel = beforeInt[0];
            manager.config().batchIntervalSeconds = beforeInt[1];
        });
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);
    }

    /**
     * The dungeon wardens (Simon, 29 October 2026): all eight in the look of their dungeon,
     * from the front and the back, by day and by night; then a test night with a warden at
     * its end, who must fall for the night to be won.
     */
    /**
     * The dungeon guards (Simon, 29 October 2026, 22:06: "Wachen, die auf den Wächtern basieren"):
     * for every dungeon its warden and one guard of every kind of its monsters, in a row at night
     * with night vision, then without it.
     */
    private static void guardScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 160;
        int cz = pos.getZ() - 160;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 48, cz - 48, cx + 48, cz + 48));
        context.waitTicks(40);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);
        int cy = ground[0];
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - 20, cy - 1, cz - 12, cx + 20, cy - 1, cz + 12));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - 20, cy, cz - 12, cx + 20, cy + 10, cz + 12));
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %.1f facing %d.5 %.1f %.1f",
                cx, cy, cz + 7.5, cx, cy + 1.3, cz - 3.5));
        game.getServer().runCommand("effect give @p minecraft:night_vision 600 0 true");
        context.runOnClient(client -> hud(client, false));
        for (com.mki.nightsiege.core.ExpeditionKind kind : com.mki.nightsiege.core.ExpeditionKind.values()) {
            game.getServer().runCommand("kill @e[type=!player,x=%d,y=%d,z=%d,distance=..30]".formatted(cx, cy, cz));
            context.waitTicks(5);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                java.util.List<String> ids = new java.util.ArrayList<>();
                ids.add(kind.champion());
                for (String id : kind.mobs()) {
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < ids.size(); k++) {
                    double x = cx + 0.5 + (k - (ids.size() - 1) / 2.0) * 2.4;
                    BlockPos spot = new BlockPos((int) Math.floor(x), cy, cz - 4);
                    net.minecraft.world.entity.Mob mob = com.mki.nightsiege.game.MobFactory.spawnAt(level, spot, ids.get(k), 8,
                            manager.config(), new java.util.Random(k), com.mki.nightsiege.core.SiegeEvent.NONE, false);
                    if (mob == null) {
                        sb.append(' ').append(ids.get(k)).append("=none");
                        continue;
                    }
                    if (k == 0) {
                        com.mki.nightsiege.game.WardenNight.dress(mob, kind, 60.0);
                    } else if (!com.mki.nightsiege.game.Foes.isRole(ids.get(k))) {
                        com.mki.nightsiege.game.BossService.foeLook(mob, "guard_" + kind.id());
                    }
                    mob.setNoAi(true);
                    mob.setInvulnerable(true);
                    mob.removeTag(com.mki.nightsiege.game.MobFactory.TAG);
                    mob.addTag("nightsiege_test_guard");
                    mob.teleportTo(x, cy, cz - 3.5);
                    mob.setYRot(0f);
                    mob.setYHeadRot(0f);
                    mob.yBodyRot = 0f;
                    sb.append(' ').append(mob.getType().toShortString()).append('=')
                            .append(com.mki.nightsiege.game.BossService.foeLookOf(mob.getId()));
                }
                System.out.println("[Night Siege test] guards " + kind.id() + ":" + sb);
            });
            context.waitTicks(40);
            context.takeScreenshot("guards_" + kind.id());
        }
        game.getServer().runCommand("effect clear @p minecraft:night_vision");
        context.waitTicks(20);
        context.takeScreenshot("guards_basalt_forge_dark");
        context.runOnClient(client -> hud(client, true));
        game.getServer().runCommand("kill @e[type=!player,x=%d,y=%d,z=%d,distance=..30]".formatted(cx, cy, cz));
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("time set 6000");
        context.waitTicks(10);
    }

    private static void wardenScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 160;
        int cz = pos.getZ() - 160;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 48, cz - 48, cx + 48, cz + 48));
        context.waitTicks(40);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);
        int cy = ground[0];
        int out = 40;
        for (int part = -out; part <= out; part += 20) {
            int z1 = cz + part;
            int z2 = Math.min(cz + out, z1 + 19);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - out, cy - 1, z1, cx + out, cy - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - out, cy, z1, cx + out, cy + 12, z2));
        }
        game.getServer().runCommand("time set 18000");   // the undead would burn by day
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 10".formatted(cx, cy, cz + 6));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 600 10 true");
        game.getServer().runCommand("kill @e[type=!player,x=%d,y=%d,z=%d,distance=..40]".formatted(cx, cy, cz));
        context.waitTicks(20);
        // All eight in a row, facing the camera.
        final java.util.List<java.util.UUID> row = new java.util.ArrayList<>();
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.core.ExpeditionKind[] all = com.mki.nightsiege.core.ExpeditionKind.values();
            for (int k = 0; k < all.length; k++) {
                com.mki.nightsiege.core.ExpeditionKind kind = all[k];
                double x = cx + 0.5 + (k - (all.length - 1) / 2.0) * 2.2;
                BlockPos spot = new BlockPos((int) Math.floor(x), cy, cz - 4);
                net.minecraft.world.entity.Mob mob = com.mki.nightsiege.game.MobFactory.spawnAt(level, spot, kind.champion(), 8,
                        manager.config(), new java.util.Random(k), com.mki.nightsiege.core.SiegeEvent.NONE, false);
                if (mob == null) {
                    System.out.println("[Night Siege test] warden " + kind.id() + " not spawned");
                    continue;
                }
                com.mki.nightsiege.game.WardenNight.dress(mob, kind, 60.0);
                mob.setNoAi(true);
                mob.removeTag(com.mki.nightsiege.game.MobFactory.TAG);
                mob.teleportTo(x, cy, cz - 3.5);
                mob.setYRot(0f);
                mob.setYHeadRot(0f);
                mob.yBodyRot = 0f;
                row.add(mob.getUUID());
                System.out.println("[Night Siege test] warden " + kind.id() + " type=" + mob.getType().toShortString()
                        + " look=" + com.mki.nightsiege.game.BossService.foeLookOf(mob.getId())
                        + " armour=" + Math.round(mob.getArmorValue()) + " head=" + mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()
                        + " name=" + (mob.getCustomName() == null ? "-" : mob.getCustomName().getString()));
            }
        });
        context.waitTicks(10);
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %.1f facing %d.5 %.1f %.1f",
                cx, cy, cz + 7.5, cx, cy + 1.7, cz - 3.5));
        // At night (the undead would burn by day), lit by night vision; then without it: what glows.
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("effect give @p minecraft:night_vision 600 0 true");
        game.getServer().runCommand("kill @e[type=!player,tag=!nightsiege_warden,x=%d,y=%d,z=%d,distance=..40]".formatted(cx, cy, cz));
        closeScreen(context);
        context.runOnClient(client -> hud(client, false));
        context.waitTicks(40);
        context.takeScreenshot("warden_row_day");
        game.getServer().runCommand("effect clear @p minecraft:night_vision");
        context.waitTicks(20);
        context.takeScreenshot("warden_row_night");
        game.getServer().runCommand("effect give @p minecraft:night_vision 600 0 true");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (java.util.UUID id : row) {
                if (level.getEntity(id) instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setYRot(180f);
                    mob.setYHeadRot(180f);
                    mob.yBodyRot = 180f;
                }
            }
        });
        context.waitTicks(20);
        context.takeScreenshot("warden_row_back");
        // Closer, two at a time, for the details.
        for (int k = 0; k < 4; k++) {
            final int pair = k;
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                for (java.util.UUID id : row) {
                    if (level.getEntity(id) instanceof net.minecraft.world.entity.Mob mob) {
                        mob.setYRot(0f);
                        mob.setYHeadRot(0f);
                        mob.yBodyRot = 0f;
                    }
                }
            });
            double x = cx + 0.5 + (2 * pair + 0.5 - 3.5) * 2.2;
            game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.1f %d %.1f facing %.1f %.1f %.1f",
                    x, cy, cz + 0.8, x, cy + 1.6, cz - 3.5));
            context.waitTicks(15);
            context.takeScreenshot("warden_pair_" + pair);
        }
        context.runOnClient(client -> hud(client, true));
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (java.util.UUID id : row) {
                net.minecraft.world.entity.Entity e = level.getEntity(id);
                if (e != null) {
                    com.mki.nightsiege.game.BossService.dropFoeLook(level.getServer(), e.getId());
                    e.discard();
                }
            }
        });
        // A test night with the grave warden at its end: he must fall for the night to be won.
        final int[] prep = new int[1];
        onServer(context, (player, manager) -> {
            prep[0] = manager.config().testPrepSeconds;
            manager.config().testPrepSeconds = 1;
            manager.planTestWarden(com.mki.nightsiege.core.ExpeditionKind.STONE_CRYPT);
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 8,
                    com.mki.nightsiege.core.SiegeEvent.NONE, null);
            System.out.println("[Night Siege test] warden night prep ok=" + ok);
        });
        context.waitTicks(40);
        closeScreen(context);
        onServer(context, (player, manager) -> {
            boolean now = manager.wardenNow(player.level().getServer());
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob w = manager.warden().warden(level);
            System.out.println("[Night Siege test] warden night active=" + manager.isActive() + " planned=" + manager.warden().kind()
                    + " spawned=" + now + " mob=" + (w == null ? "-" : w.getType().toShortString() + " hp=" + Math.round(w.getHealth())
                    + " scale=" + w.getScale() + " look=" + com.mki.nightsiege.game.BossService.foeLookOf(w.getId()))
                    + " bar=" + manager.bossBarText());
            if (w != null) {
                w.setNoAi(true);
                w.teleportTo(cx + 0.5, cy, cz - 3.5);
                w.setYRot(0f);
                w.setYHeadRot(0f);
                w.yBodyRot = 0f;
            }
        });
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %.1f facing %d.5 %.1f %.1f",
                cx, cy, cz + 3.0, cx, cy + 1.8, cz - 3.5));
        context.waitTicks(30);
        closeScreen(context);
        context.takeScreenshot("warden_night");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob w = manager.warden().warden(level);
            if (w != null) {
                w.invulnerableTime = 0;
                w.hurtServer(level, level.damageSources().playerAttack(player), 100000f);
            }
            System.out.println("[Night Siege test] warden hit: fallen=" + manager.warden().fallen() + " bar=" + manager.bossBarText());
        });
        context.waitTicks(80);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] warden after the fall: night active=" + manager.isActive());
            if (manager.isActive()) {
                manager.abort(player.level().getServer());
            }
            manager.config().testPrepSeconds = prep[0];
        });
        context.waitTicks(60);
        closeScreen(context);
        game.getServer().runCommand("effect clear @p minecraft:night_vision");
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
    }

    /**
     * The boss nights (Simon, 25 October 2026): each of the four bosses in a walled yard,
     * photographed, left to fight for a while (its abilities logged), then brought down.
     * The lich's soul shield must hold until its crystals are broken; the warlord's beast
     * falls first, then the warlord. The boss's fall wins the night.
     */
    /**
     * Update 30.10. (Simon: the spider queen and the bone lich "teleportieren sich oder greifen garnicht
     * richtig an sondern bleiben irgendwo hängen"): each in a test night against a defender behind a
     * walled square (5 high, a 1-wide gate); every tick a jump of more than 3 blocks counts as a
     * teleport. The queen must come to the defender, the lich must shoot over the wall.
     */
    private static void bossAiScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 160;
        int cz = pos.getZ() - 160;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 48, cz - 48, cx + 48, cz + 48));
        context.waitTicks(40);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);
        int cy = ground[0];
        int r = 9;
        int out = 40;
        for (int part = -out; part <= out; part += 20) {
            int z1 = cz + part;
            int z2 = Math.min(cz + out, z1 + 19);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - out, cy - 1, z1, cx + out, cy - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - out, cy, z1, cx + out, cy + 14, z2));
        }
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx + r, cy + 4, cz - r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz + r, cx + r, cy + 4, cz + r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx - r, cy + 4, cz + r));
        game.getServer().runCommand(wall.formatted(cx + r, cy, cz - r, cx + r, cy + 4, cz + r));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx, cy, cz + r, cx, cy + 1, cz + r));   // the gate
        for (int t = -r + 2; t <= r - 2; t += 4) {   // the base is lit (a spider lets go of its target in the light)
            game.getServer().runCommand("setblock %d %d %d minecraft:torch".formatted(cx + t, cy, cz - r + 1));
            game.getServer().runCommand("setblock %d %d %d minecraft:torch".formatted(cx + t, cy, cz + r - 1));
        }
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 600 10 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 600 10 true");
        final int[] prep = new int[1];
        onServer(context, (player, manager) -> {
            prep[0] = manager.config().testPrepSeconds;
            manager.config().testPrepSeconds = 1;
        });
        for (com.mki.nightsiege.core.BossKind kind : new com.mki.nightsiege.core.BossKind[] {
                com.mki.nightsiege.core.BossKind.SPIDER_QUEEN, com.mki.nightsiege.core.BossKind.BONE_LICH}) {
            String id = kind.id();
            game.getServer().runCommand("tp @p %d.5 %d %d.5 0 10".formatted(cx, cy, cz - 4));
            context.waitTicks(5);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 8,
                        com.mki.nightsiege.core.SiegeEvent.NONE, kind);
                System.out.println("[Night Siege test] bossai " + id + " prep ok=" + ok);
            });
            context.waitTicks(40);
            closeScreen(context);
            onServer(context, (player, manager) -> {
                manager.bossNow(player.level().getServer());
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
                if (boss != null && kind == com.mki.nightsiege.core.BossKind.SPIDER_QUEEN) {
                    boss.teleportTo(cx + 0.5, cy, cz + r + 14.5);   // outside, in front of the gate side
                }
                System.out.println("[Night Siege test] bossai " + id + " boss=" + (boss == null ? "-" : boss.blockPosition().toShortString()));
            });
            final double[] last = {Double.NaN, 0, 0};
            final int[] jumps = new int[1];
            final double[] closest = {1e9};
            final float[] lost = new float[1];
            for (int tick = 0; tick < 500; tick += 2) {
                context.waitTicks(2);
                final int now = tick;
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
                    if (boss == null) {
                        return;
                    }
                    if (!Double.isNaN(last[0])) {
                        double dx = boss.getX() - last[0];
                        double dy = boss.getY() - last[1];
                        double dz = boss.getZ() - last[2];
                        if (dx * dx + dy * dy + dz * dz > 3.0 * 3.0) {
                            jumps[0]++;
                            System.out.println("[Night Siege test] bossai " + id + " jump at t=" + now + ": "
                                    + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + " blocks");
                        }
                    }
                    last[0] = boss.getX();
                    last[1] = boss.getY();
                    last[2] = boss.getZ();
                    closest[0] = Math.min(closest[0], Math.sqrt(boss.distanceToSqr(player)));
                    lost[0] = Math.max(lost[0], player.getMaxHealth() - player.getHealth());
                    if (now % 100 == 0) {
                        System.out.println(String.format(java.util.Locale.ROOT,
                                "[Night Siege test] bossai %s t=%d pos=%s distance=%.1f target=%s %s",
                                id, now, boss.blockPosition().toShortString(), Math.sqrt(boss.distanceToSqr(player)),
                                boss.getTarget() == player ? "player" : boss.getTarget() == null ? "-" : boss.getTarget().getType().toShortString(),
                                manager.bosses().testBrainStats()));
                    }
                });
                if (tick == 200) {
                    context.takeScreenshot("bossai_" + id);
                }
            }
            onServer(context, (player, manager) -> {
                System.out.println(String.format(java.util.Locale.ROOT,
                        "[Night Siege test] bossai %s result: teleports=%d closest=%.1f %s",
                        id, jumps[0], closest[0], manager.bosses().testBrainStats()));
                if (manager.isActive()) {
                    manager.abort(player.level().getServer());
                }
            });
            context.waitTicks(40);
        }
        // The fire after the night (update 30.10.): a fire lit after the night's end goes out within two seconds.
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:oak_planks".formatted(cx + 2, cy, cz + 2, cx + 4, cy, cz + 4));
        game.getServer().runCommand("setblock %d %d %d minecraft:fire".formatted(cx + 3, cy + 1, cz + 3));
        game.getServer().runCommand("setblock %d %d %d minecraft:netherrack".formatted(cx - 4, cy, cz - 4));
        game.getServer().runCommand("setblock %d %d %d minecraft:fire".formatted(cx - 4, cy + 1, cz - 4));
        context.waitTicks(60);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] bossai fire after the night: planks fire="
                + player.level().getBlockState(new BlockPos(cx + 3, cy + 1, cz + 3)).getBlock()
                + " fireplace=" + player.level().getBlockState(new BlockPos(cx - 4, cy + 1, cz - 4)).getBlock()
                + " swept=" + com.mki.nightsiege.game.NightDamage.sweptFires()));
        onServer(context, (player, manager) -> manager.config().testPrepSeconds = prep[0]);
    }

    private static void bossScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 160;
        int cz = pos.getZ() + 160;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 48, cz - 48, cx + 48, cz + 48));
        context.waitTicks(40);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);
        int cy = ground[0];
        int r = 9;
        int out = 40;
        for (int part = -out; part <= out; part += 20) {
            int z1 = cz + part;
            int z2 = Math.min(cz + out, z1 + 19);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - out, cy - 1, z1, cx + out, cy - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - out, cy, z1, cx + out, cy + 12, z2));
        }
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx + r, cy + 3, cz - r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz + r, cx + r, cy + 3, cz + r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx - r, cy + 3, cz + r));
        game.getServer().runCommand(wall.formatted(cx + r, cy, cz - r, cx + r, cy + 3, cz + r));
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 10".formatted(cx, cy, cz));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 600 10 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 600 10 true");
        context.waitTicks(20);
        final int[] prep = new int[1];
        onServer(context, (player, manager) -> {
            prep[0] = manager.config().testPrepSeconds;
            manager.config().testPrepSeconds = 1;
        });
        context.waitTicks(5);
        for (com.mki.nightsiege.core.BossKind kind : com.mki.nightsiege.core.BossKind.values()) {
            String id = kind.id();
            game.getServer().runCommand("tp @p %d.5 %d %d.5 0 10".formatted(cx, cy, cz));
            context.waitTicks(5);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 8,
                        com.mki.nightsiege.core.SiegeEvent.NONE, kind);
                System.out.println("[Night Siege test] boss " + id + " prep ok=" + ok);
            });
            context.waitTicks(40);
            closeScreen(context);
            onServer(context, (player, manager) -> {
                boolean now = manager.bossNow(player.level().getServer());
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
                System.out.println("[Night Siege test] boss " + id + " night=" + manager.isActive() + " planned="
                        + manager.bosses().kind() + " spawned=" + now + " boss=" + (boss == null ? "-" : boss.getType().toShortString()
                        + " hp=" + Math.round(boss.getHealth()) + " scale=" + boss.getScale()));
                if (boss != null) {
                    // Hold the boss still for the photo, close to the camera (its abilities wait too).
                    com.mki.nightsiege.game.BossService.testHold = true;
                    boss.setNoAi(true);
                    net.minecraft.world.entity.Mob mount = manager.bosses().mountOf(level);
                    if (mount != null) {
                        mount.teleportTo(cx + 0.5, cy + 1.0, cz - 4.5);   // the storm witch on her phantom, low
                        mount.setYRot(0f);
                        mount.setYHeadRot(0f);
                        mount.yBodyRot = 0f;
                    } else {
                        boss.teleportTo(cx + 0.5, cy, cz - 4.5);
                    }
                    boss.setYRot(0f);
                    boss.setYHeadRot(0f);
                    boss.yBodyRot = 0f;
                    net.minecraft.world.entity.Mob rider = manager.bosses().riderOf(level);
                    if (rider != null) {
                        rider.setNoAi(true);
                    }
                }
            });
            context.waitTicks(10);
            // Photos without the HUD: by day (to see the look) and by night.
            double back = kind == com.mki.nightsiege.core.BossKind.SPIDER_QUEEN ? 8.0 : 9.5;
            double eyeAt = kind == com.mki.nightsiege.core.BossKind.SPIDER_QUEEN ? 1.0 : 2.4;
            game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %.1f facing %d.5 %.1f %.1f",
                    cx - 3, cy, cz - 4.5 + back, cx, cy + eyeAt, cz - 4.5));
            game.getServer().runCommand("time set 6000");
            closeScreen(context);
            context.runOnClient(client -> hud(client, false));
            context.waitTicks(40);
            context.takeScreenshot("boss_" + id + "_day");
            game.getServer().runCommand("time set 18000");
            context.waitTicks(20);
            context.takeScreenshot("boss_" + id);
            context.runOnClient(client -> hud(client, true));
            if (kind == com.mki.nightsiege.core.BossKind.BONE_LICH) {
                // A soul crystal of the shield, up close.
                final double[] at = new double[3];
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    for (java.util.UUID part : manager.bosses().partHitboxes()) {
                        net.minecraft.world.entity.Entity e = level.getEntity(part);
                        if (e != null) {
                            at[0] = e.getX();
                            at[1] = e.getY();
                            at[2] = e.getZ();
                            break;
                        }
                    }
                    System.out.println("[Night Siege test] boss bone_lich crystal at " + Math.round(at[0]) + " " + Math.round(at[1]) + " " + Math.round(at[2]));
                });
                context.waitTicks(5);
                game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.1f %.1f %.1f facing %.1f %.1f %.1f",
                        at[0] + 3.0, at[1], at[2] + 3.0, at[0], at[1] + 1.0, at[2]));
                context.runOnClient(client -> hud(client, false));
                context.waitTicks(20);
                context.takeScreenshot("boss_bone_lich_crystal");
                context.runOnClient(client -> hud(client, true));
            }
            // Everything a night destroys comes back (Simon, 27 October 2026): a blast far out in the field.
            if (kind == com.mki.nightsiege.core.BossKind.GRAVE_KING) {
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    BlockPos at = player.blockPosition().offset(40, 0, 0);
                    for (int dy = 0; dy < 3; dy++) {
                        level.setBlock(at.above(dy), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    }
                    BLAST_SPOT = at;
                    level.explode(null, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 3.0f,
                            net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                    System.out.println("[Night Siege test] night damage blast at " + at.toShortString() + " block now="
                            + level.getBlockState(at.above()).getBlock().getDescriptionId());
                });
            }
            // Let it fight for a while, then look at what it did.
            onServer(context, (player, manager) -> {
                com.mki.nightsiege.game.BossService.testHold = false;
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
                if (boss != null) {
                    boss.setNoAi(false);
                }
                net.minecraft.world.entity.Mob rider = manager.bosses().riderOf(level);
                if (rider != null) {
                    rider.setNoAi(false);
                }
            });
            game.getServer().runCommand("tp @p %d.5 %d %d.5 180 10".formatted(cx, cy, cz + 3));
            for (int t = 0; t < 12; t++) {
                context.waitTicks(20);
                final int sec = t + 1;
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
                    int mobs = 0;
                    for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                        if (e != null && e.entityTags().contains(com.mki.nightsiege.game.MobFactory.TAG)
                                && !com.mki.nightsiege.game.BossService.isBossOrPart(e)) {
                            mobs++;
                        }
                    }
                    if (sec % 3 == 0) {
                        System.out.println(String.format(java.util.Locale.ROOT,
                                "[Night Siege test] boss %s t=%ds hp=%s pos=%s parts=%d monsters=%d player=%.1f/%.1f speed=%.2f",
                                id, sec, boss == null ? "-" : String.valueOf(Math.round(boss.getHealth())),
                                boss == null ? "-" : boss.blockPosition().toShortString(), manager.bosses().partHitboxes().size(), mobs,
                                player.getHealth(), player.getMaxHealth(),
                                player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
                    }
                });
                if (t == 6) {
                    closeScreen(context);
                    context.takeScreenshot("boss_" + id + "_fight");
                }
                if (t == 10 && kind == com.mki.nightsiege.core.BossKind.SPIDER_QUEEN) {
                    final double[] at = new double[3];
                    onServer(context, (player, manager) -> {
                        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                        for (java.util.UUID part : manager.bosses().partHitboxes()) {
                            net.minecraft.world.entity.Entity e = level.getEntity(part);
                            if (e != null) {
                                at[0] = e.getX();
                                at[1] = e.getY();
                                at[2] = e.getZ();
                                break;
                            }
                        }
                    });
                    context.waitTicks(5);
                    if (at[0] != 0 || at[2] != 0) {
                        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.1f %.1f %.1f facing %.1f %.1f %.1f",
                                at[0] + 2.5, at[1] + 0.5, at[2] + 2.5, at[0], at[1] + 0.5, at[2]));
                        context.runOnClient(client -> hud(client, false));
                        context.waitTicks(10);
                        context.takeScreenshot("boss_spider_queen_egg");
                        context.runOnClient(client -> hud(client, true));
                    }
                }
            }
            if (kind == com.mki.nightsiege.core.BossKind.EMBER_COLOSSUS) {
                // Below half the colossus breaks in two (content update).
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
                    if (boss != null) {
                        boss.setHealth(boss.getMaxHealth() * 0.45f);
                    }
                });
                context.waitTicks(10);
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
                    net.minecraft.world.entity.Mob half = manager.bosses().riderOf(level);
                    System.out.println("[Night Siege test] boss ember_colossus split: main=" + (boss == null ? "-" : Math.round(boss.getHealth())
                            + "/" + Math.round(boss.getMaxHealth())) + " half=" + (half == null ? "-" : Math.round(half.getHealth())
                            + "/" + Math.round(half.getMaxHealth())));
                });
                closeScreen(context);
                context.takeScreenshot("boss_ember_colossus_split");
            }
            // Down with it: the lich's shield must hold until its crystals are gone.
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
                if (boss == null) {
                    System.out.println("[Night Siege test] boss " + id + " gone before the end");
                    return;
                }
                float before = boss.getHealth();
                boss.invulnerableTime = 0;
                boss.hurtServer(level, level.damageSources().playerAttack(player), 20f);
                System.out.println("[Night Siege test] boss " + id + " hit 20: " + Math.round(before) + " -> " + Math.round(boss.getHealth())
                        + " parts=" + manager.bosses().partHitboxes().size());
                for (java.util.UUID part : manager.bosses().partHitboxes()) {
                    net.minecraft.world.entity.Entity e = level.getEntity(part);
                    if (e instanceof net.minecraft.world.entity.LivingEntity l) {
                        l.invulnerableTime = 0;
                        l.hurtServer(level, level.damageSources().playerAttack(player), 10000f);
                    }
                }
            });
            context.waitTicks(10);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                for (int k = 0; k < 2; k++) {
                    net.minecraft.world.entity.Mob boss = manager.bosses().boss(level);
                    if (boss == null) {
                        break;
                    }
                    float before = boss.getHealth();
                    boss.invulnerableTime = 0;
                    boss.hurtServer(level, level.damageSources().playerAttack(player), 100000f);
                    System.out.println("[Night Siege test] boss " + id + " big hit " + k + ": " + Math.round(before) + " -> "
                            + (boss.isAlive() ? String.valueOf(Math.round(boss.getHealth())) : "dead") + " fallen=" + manager.bosses().fallen());
                }
            });
            context.waitTicks(80);
            onServer(context, (player, manager) -> {
                System.out.println("[Night Siege test] boss " + id + " after the fall: night active=" + manager.isActive()
                        + " speed=" + player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
                if (BLAST_SPOT != null) {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    System.out.println("[Night Siege test] night damage after the night block="
                            + level.getBlockState(BLAST_SPOT.above()).getBlock().getDescriptionId());
                    BLAST_SPOT = null;
                }
                if (manager.isActive()) {
                    manager.abort(player.level().getServer());
                }
            });
            context.waitTicks(20);
            closeScreen(context);
        }
        // The boss rhythm (Simon 29.10.): the Vierer-Nacht (four bosses, two at a time) and the double
        // boss night after night 40 (two bosses at once, 15 s into a test night).
        for (String mode : new String[] {"vierer", "doppel"}) {
            final int[] pause = new int[1];
            final boolean[] running = {true};
            final boolean group = mode.equals("vierer");
            game.getServer().runCommand("tp @p %d.5 %d %d.5 0 10".formatted(cx, cy, cz));
            onServer(context, (player, manager) -> {
                pause[0] = manager.config().bossGroupPauseSeconds;
                manager.config().bossGroupPauseSeconds = 1;
                if (group) {
                    manager.markTestFinale(true);
                } else {
                    manager.markTestLate(true);
                }
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 25,
                        com.mki.nightsiege.core.SiegeEvent.NONE);
                System.out.println("[Night Siege test] " + mode + " prep ok=" + ok);
            });
            context.waitTicks(40 + 120);
            closeScreen(context);
            for (int step = 0; step < 30 && running[0]; step++) {
                final int at = step;
                final boolean strike = at > 0 && (group || at >= 8);
                onServer(context, (player, manager) -> {
                    net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                    java.util.List<String> alive = new java.util.ArrayList<>();
                    if (strike) {
                        // The lich's crystals first (its shield holds until they are gone), as in the boss scenes.
                        for (com.mki.nightsiege.game.BossService b : manager.bossesForTest()) {
                            for (java.util.UUID part : b.partHitboxes()) {
                                if (level.getEntity(part) instanceof net.minecraft.world.entity.LivingEntity l && l.isAlive()) {
                                    l.invulnerableTime = 0;
                                    l.hurtServer(level, level.damageSources().playerAttack(player), 10000f);
                                }
                            }
                        }
                    }
                    for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                        if (e instanceof net.minecraft.world.entity.Mob m && m.isAlive()
                                && m.entityTags().contains(com.mki.nightsiege.game.BossService.TAG_BOSS)) {
                            alive.add(m.getType().toShortString() + " " + Math.round(m.getHealth()) + "/" + Math.round(m.getMaxHealth()));
                            if (strike) {
                                m.invulnerableTime = 0;
                                m.hurtServer(level, level.damageSources().playerAttack(player), 100000f);
                            }
                        }
                    }
                    running[0] = manager.isActive();
                    System.out.println("[Night Siege test] " + mode + " step " + at + ": active=" + manager.isActive() + " state="
                            + manager.finaleState() + " bosses=" + alive);
                });
                if (step == (group ? 0 : 7)) {
                    context.takeScreenshot(mode + "_bar");
                }
                context.waitTicks(60);
            }
            onServer(context, (player, manager) -> {
                System.out.println("[Night Siege test] " + mode + " over: active=" + manager.isActive() + " state=" + manager.finaleState()
                        + " crowns=" + manager.save().season);
                if (manager.isActive()) {
                    manager.abort(player.level().getServer());
                }
                manager.config().bossGroupPauseSeconds = pause[0];
            });
            context.waitTicks(40);
        }
        context.waitTicks(20);
        onServer(context, (player, manager) -> manager.config().testPrepSeconds = prep[0]);
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);
    }

    /**
     * The summoner (admins only for now): the staff calls a pack, the alpha leads it, the
     * wild hunt and the titan come with the ultimate; every companion goes with the night.
     */
    private static void summonerScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 120;
        int cz = pos.getZ() - 120;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 40, cz - 40, cx + 40, cz + 40));
        context.waitTicks(40);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);
        int cy = ground[0];
        int out = 30;
        for (int part = -out; part <= out; part += 20) {
            int z1 = cz + part;
            int z2 = Math.min(cz + out, z1 + 19);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - out, cy - 1, z1, cx + out, cy - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - out, cy, z1, cx + out, cy + 8, z2));
        }
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("tp @p %d.5 %d %d.5 180 15".formatted(cx, cy, cz + 4));
        // The player becomes a summoner of the tamer's way for the scene (the old progress comes back after).
        final String[] backup = new String[1];
        final boolean[] openBefore = new boolean[1];
        onServer(context, (player, manager) -> {
            var own = manager.save().skillsOf(player.getUUID().toString());
            backup[0] = com.mki.nightsiege.game.Json.toJson(own);
            openBefore[0] = manager.config().summonerForEveryone;
            manager.config().summonerForEveryone = true;
            own.reset();
            own.chosenClass = com.mki.nightsiege.core.SkillClass.SUMMONER.name();
            own.unlockClass(com.mki.nightsiege.core.SkillClass.SUMMONER);
            own.award(600);
            for (String id : subclassFirst("s_soulbond", "s_command", "s_packcall", "s_tamer", "s_tamer", "s_alpha", "s_howl",
                    "s_packleader", "s_wildness", "s_wild_hunt")) {
                manager.onBuySkill(player, id);
            }
            System.out.println("[Night Siege test] summoner class=" + own.chosenClass + " tamer=" + own.levelOf("s_tamer")
                    + " hunt=" + own.levelOf("s_wild_hunt") + " admin=" + manager.maySummon(player));
            // In creative the staff also calls outside a night (for trying it out).
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            net.minecraft.world.item.ItemStack staff = new net.minecraft.world.item.ItemStack(com.mki.nightsiege.game.ModContent.STAFF);
            for (int i = 0; i < 5; i++) {
                c.resetPause(player.getUUID());
                c.callWithStaff(player);   // the call (sneak + right click into the air, or G) since 26 October 2026
            }
            System.out.println("[Night Siege test] summoner test wolves=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "wolf")
                    + " (3 places: tamer 2)");
        });
        closeScreen(context);
        context.waitTicks(30);
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(10);
        context.takeScreenshot("summoner_pack");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.Companions.get().wildHunt(level, player, 3);
            System.out.println("[Night Siege test] summoner hunt spirits="
                    + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "spirit"));
        });
        context.waitTicks(12);
        context.takeScreenshot("summoner_wild_hunt");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            c.dismissAll(level.getServer());
            c.titan(level, player, 2);
            c.testCall(player, "golem");
            c.testCall(player, "snow");
            c.testCall(player, "snow");
            c.testCall(player, "skeleton");
            System.out.println("[Night Siege test] summoner titan=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "titan")
                    + " golem=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "golem")
                    + " snow=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "snow")
                    + " skeleton=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "skeleton"));
        });
        context.waitTicks(20);
        context.takeScreenshot("summoner_titan");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        // Now a test night: the kit brings the staff, the pack fights.
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.Companions.get().dismissAll(player.level().getServer());
            // The summoner's crate gains of the way of the wild (and both ways).
            var own = manager.save().skillsOf(player.getUUID().toString());
            for (String gain : new String[]{"futterbeutel", "rueckrufpfeife", "wolfspanzer", "golemplatten", "geisterross",
                    "futternapf", "doppelstab", "spaehfalke", "seelentausch"}) {
                own.setGainStage(com.mki.nightsiege.core.SkillClass.SUMMONER, gain, 1);
            }
            manager.config().testPrepSeconds = 2;
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz - 8), 4,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] summoner prep ok=" + ok);
        });
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 200 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 200 10 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 200 10 true");
        context.waitTicks(80);
        closeScreen(context);
        onServer(context, (player, manager) -> {
            net.minecraft.world.item.ItemStack staff = net.minecraft.world.item.ItemStack.EMPTY;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(com.mki.nightsiege.game.ModContent.STAFF)) {
                    staff = player.getInventory().getItem(i);
                }
            }
            System.out.println("[Night Siege test] summoner night active=" + manager.isActive() + " staff in kit=" + !staff.isEmpty());
            StringBuilder kit = new StringBuilder();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                String g = com.mki.nightsiege.game.Gadgets.of(player.getInventory().getItem(i));
                if (!g.isEmpty()) {
                    kit.append(g).append(' ');
                }
                if (player.getInventory().getItem(i).is(net.minecraft.world.item.Items.BEEF)) {
                    kit.append("beef=").append(player.getInventory().getItem(i).getCount()).append(' ');
                }
            }
            System.out.println("[Night Siege test] summoner gain kit: " + kit);
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            // The call waits for its pause (Doppelstab gives the staff's attack two charges since 26 October 2026).
            c.resetPause(player.getUUID());
            c.callWithStaff(player);
            int one = com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "wolf");
            c.callWithStaff(player);
            int two = com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "wolf");
            System.out.println("[Night Siege test] summoner call wolves " + one + " -> " + two + " (the second call waits)");
            for (int i = 0; i < 5; i++) {
                c.resetPause(player.getUUID());
                c.callWithStaff(player);
            }
            System.out.println("[Night Siege test] summoner night wolves=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "wolf"));
        });
        for (int i = 1; i <= 4; i++) {
            context.waitTicks(100);
            final int step = i;
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                int fighting = 0;
                double hp = 0;
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (com.mki.nightsiege.game.Companions.isCompanion(e) && e instanceof net.minecraft.world.entity.Mob m) {
                        hp = Math.max(hp, m.getMaxHealth());
                        if (m.getTarget() != null) {
                            fighting++;
                        }
                    }
                }
                System.out.println(String.format(java.util.Locale.ROOT,
                        "[Night Siege test] summoner night t=%ds wolves=%d fighting=%d maxhp=%.0f kills=%d", step * 5,
                        com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "wolf"), fighting, hp, manager.nightKills()));
            });
            if (i == 2) {
                context.takeScreenshot("summoner_night");
            }
        }
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            String armour = "";
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (com.mki.nightsiege.game.Companions.isCompanion(e) && e instanceof net.minecraft.world.entity.animal.wolf.Wolf w) {
                    armour = w.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY).toString();
                }
            }
            c.useGadget(player, level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEAD), "rueckrufpfeife");
            c.useGadget(player, level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE), "geisterross");
            c.useGadget(player, level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL), "futternapf");
            System.out.println("[Night Siege test] summoner gains hawk=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "hawk")
                    + " mount=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "mount") + " wolf armour=" + armour);
            // Seelentausch: look at a wolf, fire the second ult.
            net.minecraft.world.entity.Entity wolf = null;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (com.mki.nightsiege.game.Companions.isCompanion(e) && e instanceof net.minecraft.world.entity.animal.wolf.Wolf
                        && e.distanceTo(player) > 2.0) {
                    wolf = e;
                }
            }
            if (wolf != null) {
                net.minecraft.world.phys.Vec3 before = player.position();
                net.minecraft.world.phys.Vec3 target = wolf.position();
                player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                        wolf.position().add(0, wolf.getBbHeight() * 0.5, 0));
                com.mki.nightsiege.game.SecondUlts.fire(player);
                System.out.println(String.format(java.util.Locale.ROOT,
                        "[Night Siege test] summoner soul swap moved %.1f blocks (wolf was %.1f away)",
                        player.position().distanceTo(before), target.distanceTo(before)));
            }
        });
        context.waitTicks(20);
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(10);
        context.takeScreenshot("summoner_gains");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        // The way of the dead in the same night: souls from the fallen, the soulcatcher raises with ghosts.
        onServer(context, (player, manager) -> {
            // Only this summoner's pack goes: the fallen of the night stay to be raised.
            com.mki.nightsiege.game.Companions.get().dismiss(player);
            var own = manager.save().skillsOf(player.getUUID().toString());
            own.reset();
            own.chosenClass = com.mki.nightsiege.core.SkillClass.SUMMONER.name();
            own.unlockClass(com.mki.nightsiege.core.SkillClass.SUMMONER);
            own.award(800);
            for (String id : subclassFirst("s_soulbond", "d_raise", "d_soulcatcher", "d_soulcatcher", "d_swarm", "d_shards",
                    "d_soulburn", "d_unholy", "d_army")) {
                manager.onBuySkill(player, id);
            }
            for (String gain : new String[]{"knochenstaub", "grabkerzen", "grabruestung", "totenglocke", "seelengefaess"}) {
                own.setGainStage(com.mki.nightsiege.core.SkillClass.SUMMONER, gain, 1);
            }
            System.out.println("[Night Siege test] summoner dead start souls=" + com.mki.nightsiege.game.Companions.startSouls(own)
                    + " (want 6)");
            System.out.println("[Night Siege test] summoner dead way=" + own.pathTaken(new com.mki.nightsiege.core.SkillTree())
                    + " catcher=" + own.levelOf("d_soulcatcher") + " army=" + own.levelOf("d_army"));
            // The crate gains lift the tower caps (Simon, 26 October 2026).
            com.mki.nightsiege.game.TrapService ts = manager.trapService();
            com.mki.nightsiege.core.SkillClass eng = com.mki.nightsiege.core.SkillClass.ENGINEER;
            com.mki.nightsiege.core.TrapKind mortar = com.mki.nightsiege.core.TrapKind.MORTAR;
            com.mki.nightsiege.core.TrapKind tesla = com.mki.nightsiege.core.TrapKind.TESLA;
            int plain = ts.maxFor(mortar, 5, own);
            own.setGainStage(eng, "notfallmine", 1);
            int mine = ts.maxFor(mortar, 5, own);
            int teslaMine = ts.maxFor(tesla, 5, own);
            own.setGainStage(eng, "ueberkapazitaet", 1);
            int both = ts.maxFor(mortar, 5, own);
            int teslaOver = ts.maxFor(tesla, 5, own);
            own.setGainStage(eng, "notfallmine", 0);
            own.setGainStage(eng, "ueberkapazitaet", 0);
            System.out.println("[Night Siege test] tower caps mortar=" + plain + " +notfallmine=" + mine + " +both=" + both
                    + " tesla +notfallmine=" + teslaMine + " +ueberkapazitaet=" + teslaOver + " (want 2, 3, 4, 2, 3)");
        });
        closeScreen(context);   // buying skills opened the class window
        // A fresh wave falls right in front of the summoner: souls and corpses.
        onServer(context, (player, manager) -> manager.testWave(player.level().getServer()));
        context.waitTicks(40);
        game.getServer().runCommand("tp @e[tag=nightsiege] %d.5 %d %d.5".formatted(cx, cy, cz - 2));
        context.waitTicks(5);
        game.getServer().runCommand("kill @e[tag=nightsiege]");   // the server's own position is far away: no distance
        context.waitTicks(25);
        // The ability bar of the soulcatcher: his two spells on R and G (since 28 October 2026); the swarm
        // twice: the second cast must wait for its 6 s.
        context.runOnClient(client -> {
            com.mki.nightsiege.core.UltState st = com.mki.nightsiege.client.UltHud.state();
            System.out.println("[Night Siege test] soulcatcher bar slots=" + (st == null ? -1 : st.slots.size()));
        });
        context.takeScreenshot("summoner_bar");
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            c.soulsForTest(player, 12);
            long now = player.level().getGameTime();
            boolean first = c.castSpell(player, "swarm");
            boolean second = c.castSpell(player, "swarm");
            System.out.println("[Night Siege test] swarm cooldown: first=" + first + " second=" + second
                    + " left=" + c.spellCooldownLeft(player, "swarm", now) + " (a cast: second false, left 120)");
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            int before = c.soulsOf(player.getUUID());
            boolean shield = c.castSpell(player, "shield");   // gone since 28 October 2026: no cast, no souls
            System.out.println("[Night Siege test] summoner dead souls before=" + before + " after=" + c.soulsOf(player.getUUID())
                    + " (no soul shield any more: the same) cast=" + shield + " absorption=" + player.getAbsorptionAmount()
                    + " minions=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "minion"));
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (com.mki.nightsiege.game.Companions.isCompanion(e) && e instanceof net.minecraft.world.entity.Mob m
                        && !(e instanceof net.minecraft.world.entity.monster.Vex) && !(e instanceof net.minecraft.world.entity.animal.wolf.Wolf)) {
                    System.out.println("[Night Siege test] summoner minion " + m.getType().getDescriptionId() + " glowing=" + m.hasGlowingTag()
                            + " team=" + (m.getTeam() == null ? "-" : m.getTeam().getName())
                            + " chest=" + m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
                }
            }
            System.out.println("[Night Siege test] summoner ghosts t=0s" + com.mki.nightsiege.game.Companions.ghostReport(level, player));
            c.useGadget(player, level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BELL), "totenglocke");
        });
        // The ghosts as players see them (no window in front: an earlier step left the core window open).
        closeScreen(context);
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(20);
        closeScreen(context);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] summoner ghosts t=2s"
                + com.mki.nightsiege.game.Companions.ghostReport((net.minecraft.server.level.ServerLevel) player.level(), player)));
        context.runOnClient(client -> {
            int vexes = 0;
            int aqua = 0;
            int named = 0;
            if (client.level != null && client.player != null) {
                for (net.minecraft.world.entity.monster.Vex e : client.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Vex.class,
                        client.player.getBoundingBox().inflate(48.0))) {
                    vexes++;
                    if (e.getTeam() != null && "nightsiege_ghost".equals(e.getTeam().getName())) {
                        aqua++;
                    }
                    if (e.isCustomNameVisible()) {
                        named++;
                    }
                }
            }
            System.out.println("[Night Siege test] summoner ghosts on the client vex=" + vexes + " aqua team=" + aqua + " name shown=" + named);
        });
        context.takeScreenshot("summoner_dead");
        context.waitTicks(60);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] summoner ghosts t=5s"
                + com.mki.nightsiege.game.Companions.ghostReport((net.minecraft.server.level.ServerLevel) player.level(), player)));
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean fired = com.mki.nightsiege.game.Companions.get().armyOfTheDead(level, player, 2);
            System.out.println("[Night Siege test] summoner army fired=" + fired + " minions="
                    + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "minion"));
        });
        context.waitTicks(20);
        context.takeScreenshot("summoner_army");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            int before = c.soulsOf(player.getUUID());
            boolean fired = c.soulStorm(level, player, 2);
            System.out.println("[Night Siege test] summoner storm fired=" + fired + " souls " + before + " -> " + c.soulsOf(player.getUUID()));
        });
        context.waitTicks(100);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] summoner dead after 5s minions="
                + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "minion")
                + " ghosts=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "ghost") + " kills=" + manager.nightKills()));
        // The kill goal drops when a wave cannot come (field full): the bar says why (Simon, 26 October 2026).
        onServer(context, (player, manager) -> {
            String before = manager.bossBarText();
            int keep = manager.config().maxLiveMobs;
            manager.config().maxLiveMobs = 0;
            manager.testWave(player.level().getServer());
            String after = manager.bossBarText();
            manager.config().maxLiveMobs = keep;
            System.out.println("[Night Siege test] summoner goal bar before=" + before + " | after=" + after);
        });
        // Souls again from a second wave, for the soul vessel at the end of the night.
        onServer(context, (player, manager) -> manager.testWave(player.level().getServer()));
        context.waitTicks(40);
        game.getServer().runCommand("tp @e[tag=nightsiege] %d.5 %d %d.5".formatted(cx, cy, cz - 2));
        context.waitTicks(5);
        game.getServer().runCommand("kill @e[tag=nightsiege]");
        context.waitTicks(5);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] summoner souls before the end="
                + com.mki.nightsiege.game.Companions.get().soulsOf(player.getUUID())));
        onServer(context, (player, manager) -> manager.abort(player.level().getServer()));
        context.waitTicks(20);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] summoner soul vessel kept="
                + manager.save().skillsOf(player.getUUID().toString()).keptSouls));
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(5);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] summoner after the night companions=" + com.mki.nightsiege.game.Companions.countOf(player.getUUID()));
            manager.config().summonerForEveryone = openBefore[0];
            // The old progress back (field by field, the save keeps its object).
            var own = manager.save().skillsOf(player.getUUID().toString());
            var old = com.mki.nightsiege.game.Json.fromJson(backup[0], com.mki.nightsiege.core.PlayerSkills.class);
            for (java.lang.reflect.Field f : com.mki.nightsiege.core.PlayerSkills.class.getFields()) {
                try {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.set(own, f.get(old));
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println("[Night Siege test] summoner restored class=" + own.chosenClass + " unlocked=" + own.unlockedClasses());
        });
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(10);
    }

    /**
     * The soulcatcher after Simon's playtest of 28 October 2026: a kill in a dungeon fight gives a
     * soul; his two spells sit on R and G of the bar and are cast without picking (no soul shield,
     * no soul chain, the old choice key steps nothing); the necromancer raises a dungeon corpse; a
     * guard of the war camp gives a soul too.
     */
    private static void soulcatcherScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode creative @p");
        final boolean[] fakeCore = new boolean[1];
        final String[] backup = new String[1];
        final boolean[] openBefore = new boolean[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            if (manager.mainCore() == null) {
                manager.save().cores.add(new com.mki.nightsiege.game.CoreRecord(
                        com.mki.nightsiege.game.SiegeManager.dimensionName(level), pos.getX(), pos.getY(), pos.getZ()));
                fakeCore[0] = true;
            }
            var own = manager.save().skillsOf(player.getUUID().toString());
            backup[0] = com.mki.nightsiege.game.Json.toJson(own);
            openBefore[0] = manager.config().summonerForEveryone;
            manager.config().summonerForEveryone = true;
            own.reset();
            own.chosenClass = com.mki.nightsiege.core.SkillClass.SUMMONER.name();
            own.unlockClass(com.mki.nightsiege.core.SkillClass.SUMMONER);
            own.award(900);
            for (String id : subclassFirst("s_soulbond", "d_raise", "d_soulcatcher", "d_soulcatcher", "d_haste", "d_shards",
                    "d_soulcatcher", "d_host")) {
                manager.onBuySkill(player, id);
            }
            com.mki.nightsiege.core.SkillTree tree = manager.skillTree();
            System.out.println("[Night Siege test] summoner soulcatcher skills catcher=" + own.levelOf("d_soulcatcher")
                    + " haste=" + own.levelOf("d_haste") + " host=" + own.levelOf("d_host")
                    + " old nodes gone=" + (tree.get("d_fetter") == null && tree.get("d_soulshield") == null)
                    + " (want 3, 1, 1, true)");
        });
        closeScreen(context);
        // A stone crypt with one guard room, far from the other scenes.
        int fx = pos.getX() + 220;
        int bz = pos.getZ() - 340;
        int fy = dungeonGround(context, game, fx, bz, 1);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            manager.expeditions().testBuild(level, new BlockPos(fx, fy, bz), 0, com.mki.nightsiege.core.ExpeditionKind.STONE_CRYPT, 1);
            manager.expeditions().openEntry(true);   // straight in, no ready round
        });
        context.waitTicks(10);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                fx, fy - 12, bz + 12, fx, fy - 16, bz + 20));
        context.waitTicks(30);
        closeScreen(context);   // the class cards of the dungeon
        int rz = bz + 32;
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                fx, fy - 20, rz + 2, fx, fy - 19, rz + 14));
        context.waitTicks(40);
        closeScreen(context);
        // One monster of the dungeon falls next to the soulcatcher: one soul (no Seelengier learned).
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            java.util.List<net.minecraft.world.entity.Mob> mobs = manager.expeditions().testMobs(level);
            int before = c.soulsOf(player.getUUID());
            String fell = "-";
            if (!mobs.isEmpty()) {
                net.minecraft.world.entity.Mob mob = mobs.get(0);
                mob.teleportTo(player.getX() + 1.5, player.getY(), player.getZ() + 3.0);
                mob.invulnerableTime = 0;
                mob.hurtServer(level, level.damageSources().playerAttack(player), 100000f);
                fell = mob.getType().getDescriptionId() + " dead=" + !mob.isAlive();
            }
            System.out.println("[Night Siege test] summoner soulcatcher dungeon fighter=" + manager.expeditions().isFighter(player)
                    + " kit=" + manager.kits().hasStash(player) + " monsters=" + mobs.size() + " fell=" + fell
                    + " souls " + before + " -> " + c.soulsOf(player.getUUID()) + " (want +1)");
        });
        context.waitTicks(30);   // the bar comes once a second
        context.runOnClient(client -> {
            com.mki.nightsiege.core.UltState st = com.mki.nightsiege.client.UltHud.state();
            StringBuilder ids = new StringBuilder();
            if (st != null && st.slots != null) {
                for (com.mki.nightsiege.core.UltState.Slot slot : st.slots) {
                    ids.append(slot.id).append(' ');
                }
            }
            System.out.println("[Night Siege test] summoner soulcatcher bar on the client: " + ids + "(want swarm fear)");
        });
        // The spells without picking: R the swarm, G the fright, the right click the last one again; the
        // old choice key steps nothing any more; shield and chain are no spells of his.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            java.util.List<net.minecraft.world.entity.Mob> mobs = manager.expeditions().testMobs(level);
            for (int i = 0; i < mobs.size(); i++) {
                mobs.get(i).teleportTo(player.getX() + (i - 1) * 1.2, player.getY(), player.getZ() + 4.0);
            }
            if (!mobs.isEmpty()) {
                player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                        mobs.get(0).position().add(0, mobs.get(0).getBbHeight() * 0.5, 0));
            }
            net.minecraft.world.item.ItemStack staff = new net.minecraft.world.item.ItemStack(com.mki.nightsiege.game.ModContent.STAFF);
            c.soulsForTest(player, 12);
            c.resetPause(player.getUUID());
            long now = level.getGameTime();
            StringBuilder line = new StringBuilder("souls 12");
            manager.onCoreAction(player, "slot:0");   // R
            line.append(" R(swarm)->").append(c.soulsOf(player.getUUID()));
            int swarmWait = c.spellCooldownLeft(player, "swarm", now);
            c.resetPause(player.getUUID());
            manager.onCoreAction(player, "slot:1");   // G
            line.append(" G(fear)->").append(c.soulsOf(player.getUUID()));
            c.resetPause(player.getUUID());
            c.useStaff(player, staff);   // right click: the last spell again
            line.append(" right->").append(c.soulsOf(player.getUUID()));
            c.resetPause(player.getUUID());
            manager.onCoreAction(player, "quiver");   // the old choice key
            c.useStaff(player, staff);
            line.append(" oldkey+right->").append(c.soulsOf(player.getUUID()));
            boolean chain = c.castSpell(player, "chain");
            boolean shield = c.castSpell(player, "shield");
            System.out.println("[Night Siege test] summoner soulcatcher casts " + line + " swarm pause=" + swarmWait
                    + " chain=" + chain + " shield=" + shield + " barslot chain=" + manager.abilitySlotOf(player, "chain")
                    + " shield=" + manager.abilitySlotOf(player, "shield") + " fear=" + manager.abilitySlotOf(player, "fear")
                    + " (want 8, 6, 4, 2, pause 80, false, false, -1, -1, 1)");
        });
        context.waitTicks(8);
        context.takeScreenshot("summoner_soulcatcher_dungeon");
        context.waitTicks(40);
        // The necromancer (same way) raises a corpse of the dungeon.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            var own = manager.save().skillsOf(player.getUUID().toString());
            own.reset();
            own.chosenClass = com.mki.nightsiege.core.SkillClass.SUMMONER.name();
            own.unlockClass(com.mki.nightsiege.core.SkillClass.SUMMONER);
            own.award(400);
            for (String id : subclassFirst("s_soulbond", "d_raise", "d_necro", "d_necro")) {
                manager.onBuySkill(player, id);
            }
            c.soulsForTest(player, 6);
            java.util.List<net.minecraft.world.entity.Mob> mobs = manager.expeditions().testMobs(level);
            int before = c.soulsOf(player.getUUID());
            if (!mobs.isEmpty()) {
                net.minecraft.world.entity.Mob mob = mobs.get(0);
                mob.teleportTo(player.getX(), player.getY(), player.getZ() + 2.5);
                mob.invulnerableTime = 0;
                mob.hurtServer(level, level.damageSources().playerAttack(player), 100000f);
            }
            int afterKill = c.soulsOf(player.getUUID());
            c.resetPause(player.getUUID());
            boolean raised = c.castSpell(player, "raise");
            System.out.println("[Night Siege test] summoner soulcatcher necromancer in the dungeon: monsters left=" + mobs.size()
                    + " souls " + before + " -> " + afterKill + " raise=" + raised + " minions="
                    + com.mki.nightsiege.game.Companions.countOf(player.getUUID(), "minion") + " (want +1 if one was left, true, 1)");
        });
        context.waitTicks(20);
        context.takeScreenshot("summoner_soulcatcher_necro");
        // Out of the dungeon: the kit goes, so do its souls.
        onServer(context, (player, manager) -> {
            manager.expeditions().openEntry(false);
            manager.expeditions().remove(player.level().getServer());
            System.out.println("[Night Siege test] summoner soulcatcher after the dungeon kit=" + manager.kits().hasStash(player)
                    + " souls=" + com.mki.nightsiege.game.Companions.get().soulsOf(player.getUUID()) + " (want false, 4: a fresh start)");
        });
        context.waitTicks(10);
        // A guard of the war camp falls near the summoner of the dead (in creative his skills count): a soul as well.
        game.getServer().runCommand("gamemode creative @p");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.Companions c = com.mki.nightsiege.game.Companions.get();
            int before = c.soulsOf(player.getUUID());
            net.minecraft.world.entity.Mob guard = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level,
                    net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            String result = "no guard";
            if (guard != null) {
                guard.snapTo(player.getX() + 2, player.getY(), player.getZ() + 2, 0f, 0f);
                level.addFreshEntity(guard);
                guard.addTag(com.mki.nightsiege.game.SiegeCamp.TAG);   // after the load check (no camp stands now)
                guard.invulnerableTime = 0;
                guard.hurtServer(level, level.damageSources().playerAttack(player), 100000f);
                result = "dead=" + !guard.isAlive();
            }
            System.out.println("[Night Siege test] summoner soulcatcher camp guard " + result + " souls " + before + " -> "
                    + c.soulsOf(player.getUUID()) + " (want +1)");
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            manager.expeditions().openEntry(false);
            manager.expeditions().remove(player.level().getServer());
            com.mki.nightsiege.game.Companions.get().dismiss(player);
            if (fakeCore[0]) {
                manager.save().cores.removeIf(c -> c.x == pos.getX() && c.y == pos.getY() && c.z == pos.getZ());
            }
            manager.config().summonerForEveryone = openBefore[0];
            var own = manager.save().skillsOf(player.getUUID().toString());
            var old = com.mki.nightsiege.game.Json.fromJson(backup[0], com.mki.nightsiege.core.PlayerSkills.class);
            for (java.lang.reflect.Field f : com.mki.nightsiege.core.PlayerSkills.class.getFields()) {
                try {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.set(own, f.get(old));
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        closeScreen(context);
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("time set 6000");
        context.waitTicks(10);
    }

    /** A core on flat ground near the player, when none is known yet (the scenes that need one, run alone). */
    static void ensureCore(ClientGameTestContext context, TestSingleplayerContext game) {
        boolean none = context.computeOnClient(client -> com.mki.nightsiege.game.SiegeManager.get().save().cores.isEmpty());
        if (!none) {
            return;
        }
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 40;
        int cz = pos.getZ() + 40;
        int cy = yard(context, game, cx, cz, 16, 10);
        net.minecraft.core.BlockPos core = new net.minecraft.core.BlockPos(cx, cy, cz);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.setBlock(core, com.mki.nightsiege.game.ModContent.SIEGE_CORE.defaultBlockState(), 3);
            manager.onCorePlaced(level, core, player);
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            System.out.println("[Night Siege test] ensureCore: placed at " + core + " cores=" + manager.save().cores.size());
        });
        context.waitTicks(10);
        closeScreen(context);
    }

    /** The player's inventory slot by slot as text (item, count, every component), for the before/after check. */
    static java.util.List<String> survivalSnapshot(net.minecraft.server.level.ServerPlayer player) {
        java.util.List<String> out = new java.util.ArrayList<>();
        net.minecraft.core.HolderLookup.Provider regs = player.level().registryAccess();
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack st = inv.getItem(i);
            out.add(st.isEmpty() ? "" : com.mki.nightsiege.game.KitService.encode(st, regs));
        }
        return out;
    }

    static String survivalShort(String json) {
        if (json == null || json.isEmpty()) {
            return "-";
        }
        return json.length() > 140 ? json.substring(0, 140) + "…" : json;
    }

    /**
     * Survival spot check (Simon 30.10., 23:51: "die Mod wurde noch nie im Survival probiert"):
     * a REAL night, not a test night – the rhythm invites in the morning, the player accepts, the
     * horn sounds, the night starts at the core – with a full survival inventory (enchanted, named,
     * damaged items, a filled shulker box, potions, totems, pearls, emeralds, worn armour, a shield).
     * In the night: what the kit did with the inventory, items picked up, a try to sleep the night
     * away, a lethal hit. After the night: the inventory slot by slot against the one before, the
     * clock, game mode, abilities, the save.
     */
    private static void survivalScene(ClientGameTestContext context, TestSingleplayerContext game) {
        ensureCore(context, game);
        final java.util.List<String> before = new java.util.ArrayList<>();
        final int[] core = new int[3];
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("difficulty normal");
        game.getServer().runCommand("gamerule advance_time true");
        game.getServer().runCommand("gamerule doDaylightCycle true");
        game.getServer().runCommand("time set 500");
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.CoreRecord c = manager.mainCore();
            core[0] = c.x;
            core[1] = c.y;
            core[2] = c.z;
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.switchTo(com.mki.nightsiege.core.SkillClass.WARRIOR);
            // A real survival inventory: every slot full, armour on, a shield in the off hand.
            net.minecraft.core.HolderLookup.RegistryLookup<net.minecraft.world.item.enchantment.Enchantment> ench =
                    player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            inv.clearContent();
            net.minecraft.world.item.ItemStack sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            ench.get(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS).ifPresent(h -> sword.enchant(h, 5));
            sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Simons Schwert"));
            sword.set(net.minecraft.core.component.DataComponents.DAMAGE, 120);
            net.minecraft.world.item.ItemStack shulker = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHULKER_BOX);
            shulker.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BLOCK, 9),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 4),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, 2))));
            net.minecraft.world.item.ItemStack potion = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
            potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.HEALING));
            net.minecraft.world.item.ItemStack[] hot = {
                    sword, shulker,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 16),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING, 1),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 30),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 10),
                    potion,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 5),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH, 64)};
            for (int i = 0; i < hot.length; i++) {
                inv.setItem(i, hot[i]);
            }
            net.minecraft.world.item.Item[] fill = {net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.OAK_LOG,
                    net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.BREAD, net.minecraft.world.item.Items.DIRT,
                    net.minecraft.world.item.Items.ARROW, net.minecraft.world.item.Items.STONE_BRICKS, net.minecraft.world.item.Items.REDSTONE};
            for (int i = 9; i < 36; i++) {
                net.minecraft.world.item.Item item = fill[i % fill.length];
                inv.setItem(i, new net.minecraft.world.item.ItemStack(item, Math.min(64, 10 + i)));
            }
            net.minecraft.world.item.ItemStack chest = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
            ench.get(net.minecraft.world.item.enchantment.Enchantments.PROTECTION).ifPresent(h -> chest.enchant(h, 4));
            net.minecraft.world.item.ItemStack helmet = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET);
            helmet.set(net.minecraft.core.component.DataComponents.DAMAGE, 50);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
            player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(), core[0] + 2.5, core[1], core[2] + 0.5,
                    java.util.Set.of(), 0f, 0f, true);
            before.addAll(survivalSnapshot(player));
            // Survival safety (31.10.): no override any more – one invited player, one accept must be enough.
            System.out.println("[Night Siege test] survival requiredAccepts config=" + manager.config().requiredAccepts + " (not overridden)");
            // Today is a siege day; nothing voted yet.
            manager.save().nextSiegeDay = manager.clockDayFor(player.level().getServer());
            manager.save().clearVotes();
            System.out.println("[Night Siege test] survival setup: core=" + c.x + " " + c.y + " " + c.z + " slots=" + before.size()
                    + " filled=" + before.stream().filter(x -> !x.isEmpty()).count() + " day=" + manager.save().nextSiegeDay
                    + " level=" + manager.currentLevel() + " mode=" + player.gameMode());
        });
        context.waitTicks(20);
        // Morning: the invitation.
        game.getServer().runCommand("time set 1100");
        context.waitTicks(60);
        onServer(context, (player, manager) -> {
            MinecraftServerAccess.log(manager, player, "after invite time");
            manager.vote(player, true);
            System.out.println("[Night Siege test] survival vote: open=" + manager.voteOpen(player.level().getServer())
                    + " accepted=" + manager.acceptedCount());
        });
        context.waitTicks(20);   // the queued vote runs before the clock jumps to the evening
        // Evening: the horn.
        game.getServer().runCommand("time set 12100");
        context.waitTicks(60);
        onServer(context, (player, manager) -> MinecraftServerAccess.log(manager, player, "after horn time"));
        context.waitTicks(20);
        closeScreen(context);
        // Between horn and start: everyone goes to bed (the base is free of monsters then).
        game.getServer().runCommand("time set 13000");
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            Object result = player.startSleepInBed(survivalBed(player));
            System.out.println("[Night Siege test] survival sleep after horn: result=" + result + " sleeping=" + player.isSleeping());
        });
        context.waitTicks(160);
        onServer(context, (player, manager) -> {
            if (player.isSleeping()) {
                player.stopSleeping();
            }
            MinecraftServerAccess.log(manager, player, "after sleeping past the horn");
        });
        context.waitTicks(20);
        closeScreen(context);
        // The clock where the night should begin (if sleeping moved it to the morning, back to the evening of the same day).
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] survival before start: day="
                + manager.clockDayFor(player.level().getServer()) + " time=" + manager.clockTimeFor(player.level().getServer())));
        context.waitTicks(10);
        // Night: the start at the core.
        game.getServer().runCommand("time set 15100");
        context.waitTicks(80);
        closeScreen(context);
        boolean natural = context.computeOnClient(client -> com.mki.nightsiege.game.SiegeManager.get().isActive());
        if (!natural) {
            // The rhythm did not start the night: a real night by command instead (not a test night).
            onServer(context, (player, manager) -> MinecraftServerAccess.log(manager, player, "no natural night, starting by command"));
            context.waitTicks(10);
            game.getServer().runCommand("execute as @p at @p run nightsiege start");
            context.waitTicks(80);
            closeScreen(context);
        }
        System.out.println("[Night Siege test] survival night came naturally=" + natural);
        onServer(context, (player, manager) -> {
            MinecraftServerAccess.log(manager, player, "after start time");
            java.util.List<String> now = survivalSnapshot(player);
            System.out.println("[Night Siege test] survival in night: kit=" + manager.kits().hasStash(player)
                    + " defending=" + manager.isDefending(player) + " slots=" + now.stream().filter(x -> !x.isEmpty()).count()
                    + " mainhand=" + player.getMainHandItem().getItem() + " mode=" + player.gameMode());
            // Things picked up in the night (survival safety 31.10.: everything that is not kit stays with the player).
            player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 5));
            player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 3));
            player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 2));
            // Taken from the own chest while the kit is on: an elytra and a potion (the own inventory is full,
            // so after the night they must come back beside it or lie at the player's feet).
            net.minecraft.world.item.ItemStack swift = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
            swift.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.SWIFTNESS));
            player.getInventory().add(swift);
            player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA));
            System.out.println("[Night Siege test] survival taken in the night: emerald 5, dirt 3, diamond 2, swiftness potion, elytra");
        });
        context.waitTicks(20);
        context.takeScreenshot("survival_night");
        // Sleep the siege night away? A bed beside the player, then into it.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            Object result = player.startSleepInBed(survivalBed(player));
            System.out.println("[Night Siege test] survival sleep: result=" + result + " sleeping=" + player.isSleeping()
                    + " time=" + manager.clockTimeFor(level.getServer()));
        });
        context.waitTicks(140);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] survival after sleep: sleeping=" + player.isSleeping() + " active=" + manager.isActive()
                    + " time=" + manager.clockTimeFor(player.level().getServer()));
            if (player.isSleeping()) {
                player.stopSleeping();
            }
        });
        // A lethal hit in the night.
        onServer(context, (player, manager) -> {
            float hp = player.getHealth();
            player.hurtServer((net.minecraft.server.level.ServerLevel) player.level(), player.damageSources().generic(), 500f);
            System.out.println("[Night Siege test] survival lethal hit: hp " + hp + " -> " + player.getHealth() + " alive=" + player.isAlive()
                    + " drone=" + com.mki.nightsiege.game.Drones.isDrone(player) + " active=" + manager.isActive());
        });
        context.waitTicks(100);
        closeScreen(context);
        for (int i = 0; i < 12; i++) {
            boolean active = context.computeOnClient(client -> com.mki.nightsiege.game.SiegeManager.get().isActive());
            if (!active) {
                break;
            }
            context.waitTicks(100);
        }
        context.waitTicks(40);
        closeScreen(context);
        onServer(context, (player, manager) -> {
            if (!player.isAlive()) {
                System.out.println("[Night Siege test] survival: player dead after the night, respawning");
                player.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                        net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            }
        });
        context.waitTicks(40);
        onServer(context, (player, manager) -> {
            MinecraftServerAccess.log(manager, player, "after the night");
            java.util.List<String> after = survivalSnapshot(player);
            int same = 0;
            java.util.List<String> diff = new java.util.ArrayList<>();
            for (int i = 0; i < Math.max(before.size(), after.size()); i++) {
                String b = i < before.size() ? before.get(i) : "";
                String a = i < after.size() ? after.get(i) : "";
                if (b.equals(a)) {
                    same++;
                } else {
                    diff.add("slot " + i + ": vorher " + survivalShort(b) + " | nachher " + survivalShort(a));
                }
            }
            System.out.println("[Night Siege test] survival RESULT: same=" + same + " differ=" + diff.size()
                    + " kitLeft=" + manager.kits().hasStash(player) + " drone=" + com.mki.nightsiege.game.Drones.isDrone(player)
                    + " mode=" + player.gameMode() + " flying=" + player.getAbilities().flying + " mayfly=" + player.getAbilities().mayfly
                    + " invulnerable=" + player.getAbilities().invulnerable + " hp=" + player.getHealth()
                    + " won=" + manager.save().siegesWon + " lost=" + manager.save().siegesLost
                    + " nextSiegeDay=" + manager.save().nextSiegeDay);
            for (String d : diff) {
                System.out.println("[Night Siege test] survival DIFF " + d);
            }
            java.util.List<net.minecraft.world.entity.item.ItemEntity> dropped = player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(24));
            int elytra = 0;
            int swift = 0;
            for (net.minecraft.world.entity.item.ItemEntity e : dropped) {
                System.out.println("[Night Siege test] survival dropped near player: " + e.getItem().getItem() + " x" + e.getItem().getCount());
                elytra += e.getItem().is(net.minecraft.world.item.Items.ELYTRA) ? 1 : 0;
                swift += survivalSwift(e.getItem()) ? 1 : 0;
            }
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            int kitItems = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                elytra += inv.getItem(i).is(net.minecraft.world.item.Items.ELYTRA) ? 1 : 0;
                swift += survivalSwift(inv.getItem(i)) ? 1 : 0;
                kitItems += com.mki.nightsiege.game.KitService.isKit(inv.getItem(i)) ? 1 : 0;
            }
            System.out.println("[Night Siege test] survival KEPT: elytra=" + elytra + " swiftness=" + swift + " kitItemsLeft=" + kitItems
                    + " (expected 1 1 0)");
        });
        // The clock goes on after the night (no frozen night).
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] survival clock A: "
                + manager.clockTimeFor(player.level().getServer())));
        context.waitTicks(100);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] survival clock B: "
                + manager.clockTimeFor(player.level().getServer())));
        context.takeScreenshot("survival_after");
        game.getServer().runCommand("gamemode creative @p");
    }

    private static boolean survivalSwift(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.item.alchemy.PotionContents pc = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return stack.is(net.minecraft.world.item.Items.POTION) && pc != null && pc.is(net.minecraft.world.item.alchemy.Potions.SWIFTNESS);
    }

    /** A bed two blocks south of the player; returns its head. */
    static BlockPos survivalBed(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
        BlockPos bed = player.blockPosition().offset(0, 0, 2);
        net.minecraft.world.level.block.state.BlockState foot = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("red_bed")).defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
        level.setBlock(bed, foot, 3);
        level.setBlock(bed.offset(0, 0, 1), foot.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
        return bed.offset(0, 0, 1);
    }

    /** Log lines for the survival scene. */
    private static final class MinecraftServerAccess {
        static void log(com.mki.nightsiege.game.SiegeManager manager, net.minecraft.server.level.ServerPlayer player, String when) {
            net.minecraft.server.MinecraftServer server = player.level().getServer();
            System.out.println("[Night Siege test] survival " + when + ": day=" + manager.clockDayFor(server)
                    + " time=" + manager.clockTimeFor(server) + " nextSiegeDay=" + manager.save().nextSiegeDay
                    + " voteDay=" + manager.save().voteDay + " voteOpen=" + manager.voteOpen(server)
                    + " nightComing=" + manager.nightComing(server) + " active=" + manager.isActive()
                    + " accepted=" + manager.acceptedCount() + " defending=" + manager.isDefending(player)
                    + " kit=" + manager.kits().hasStash(player) + " mode=" + player.gameMode());
        }
    }

    /**
     * Version B: the class window before a test night, the night kit in the
     * inventory, a defender who falls and watches, the own inventory coming back,
     * a reward crate spinning and the collection tab.
     */
    private static void versionBScene(ClientGameTestContext context, TestSingleplayerContext game) {
        int[] core = context.computeOnClient(client -> {
            com.mki.nightsiege.game.CoreRecord c = com.mki.nightsiege.game.SiegeManager.get().save().cores.get(0);
            return new int[]{c.x, c.y, c.z};
        });
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("tp @p %d.5 %d %d.5 -90 10".formatted(core[0] + 2, core[1], core[2] + 2));
        context.waitTicks(10);
        // With only one class the window comes too, and the summoner (open for everyone) is on it.
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            manager.config().testPrepSeconds = 30;
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean ok = manager.prepareTest(level.getServer(), level, new net.minecraft.core.BlockPos(core[0], core[1], core[2]), 5,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] one-class pick: prep=" + ok + " unlocked=" + own.unlockedClasses()
                    + " summonerForEveryone=" + manager.config().summonerForEveryone
                    + " mayChoose=" + manager.mayChoose(player, com.mki.nightsiege.core.SkillClass.SUMMONER));
        });
        context.waitTicks(20);
        String oneClass = context.computeOnClient(client -> {
            if (!(client.gui.screen() instanceof com.mki.nightsiege.client.ClassPickScreen)) {
                return "screen=" + (client.gui.screen() == null ? "none" : client.gui.screen().getClass().getSimpleName());
            }
            return "screen=ClassPickScreen";
        });
        System.out.println("[Night Siege test] one-class pick " + oneClass);
        context.takeScreenshot("b_class_pick_one_class");
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] one-class pick cancelled="
                + manager.cancelPreparation() + " stash=" + manager.kits().hasStash(player)));
        closeScreen(context);
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.unlockClass(com.mki.nightsiege.core.SkillClass.GUARDIAN);
            player.getInventory().clearContent();
            player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 32));
            player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_SWORD));
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            manager.config().testPrepSeconds = 8;
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean ok = manager.prepareTest(level.getServer(), level, new net.minecraft.core.BlockPos(core[0], core[1], core[2]), 14,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] B prep: ok=" + ok + " stash=" + manager.kits().hasStash(player)
                    + " unlocked=" + own.unlockedClasses());
        });
        context.waitTicks(20);
        context.takeScreenshot("b_class_pick");
        // The same window at GUI scale 1 (much more room: the kit with its labels).
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        context.runOnClient(client -> {
            client.options.guiScale().set(1);
            client.resizeGui();
        });
        context.waitTicks(3);
        context.takeScreenshot("b_class_pick_gui2");
        context.runOnClient(client -> {
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        context.waitTicks(3);
        onServer(context, (player, manager) -> manager.onPlayClass(player, "GUARDIAN"));
        closeScreen(context);
        context.runOnClient(client -> client.setScreenAndShow(
                new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
        context.waitTicks(15);
        context.takeScreenshot("b_kit_inventory");
        closeScreen(context);
        onServer(context, (player, manager) -> {
            StringBuilder items = new StringBuilder();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = player.getInventory().getItem(i);
                if (!st.isEmpty()) {
                    items.append(st.getCount()).append('x').append(st.getItem()).append(com.mki.nightsiege.game.KitService.isKit(st) ? "*" : "").append(' ');
                }
            }
            System.out.println("[Night Siege test] B kit: " + items);
        });
        context.waitTicks(150);   // the night starts after the preparation
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] B night: active=" + manager.isActive());
            player.hurtServer((net.minecraft.server.level.ServerLevel) player.level(),
                    player.damageSources().genericKill(), 1000f);
            System.out.println("[Night Siege test] B fallen: spectator=" + player.isSpectator() + " alive=" + player.isAlive());
        });
        context.waitTicks(20);
        context.takeScreenshot("b_spectator");
        onServer(context, (player, manager) -> {
            manager.abort(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
            StringBuilder items = new StringBuilder();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = player.getInventory().getItem(i);
                if (!st.isEmpty()) {
                    items.append(st.getCount()).append('x').append(st.getItem()).append(' ');
                }
            }
            System.out.println("[Night Siege test] B restored: mode=" + player.gameMode() + " stash="
                    + manager.kits().hasStash(player) + " items=" + items);
            manager.config().testPrepSeconds = 60;
        });
        context.waitTicks(10);
        context.runOnClient(client -> client.setScreenAndShow(
                new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
        context.waitTicks(15);
        context.takeScreenshot("b_kit_restored");
        closeScreen(context);
        game.getServer().runCommand("gamemode creative @p");
        // Reward crates: one spins, the next ones are opened to fill the collection.
        for (int i = 0; i < 8; i++) {
            final int n = i;
            onServer(context, (player, manager) -> {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        com.mki.nightsiege.game.CrateService.crate(com.mki.nightsiege.core.SkillClass.GUARDIAN,
                                n == 0 ? com.mki.nightsiege.core.CrateKind.FRAGMENT : com.mki.nightsiege.core.CrateKind.EVENT, 20));
                manager.crates().open(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            });
            context.waitTicks(n == 0 ? 30 : 10);
            if (n == 0) {
                context.takeScreenshot("b_crate_spin");
            }
            context.runOnClient(client -> {
                if (client.gui.screen() instanceof com.mki.nightsiege.client.CrateScreen crate) {
                    crate.finishNow();
                }
            });
            if (n == 0) {
                context.waitTicks(3);
                context.takeScreenshot("b_crate_glory");
                context.waitTicks(7);
                context.takeScreenshot("b_crate_result");
            } else {
                context.waitTicks(10);
            }
            closeScreen(context);
        }
        // The world effect of a legendary gain: ring, spiral, three bursts above the player.
        onServer(context, (player, manager) -> {
            player.setXRot(-35f);
            com.mki.nightsiege.game.CrateFx.schedule(player, com.mki.nightsiege.core.CrateRarity.LEGENDARY, "kriegsbanner", 0);
        });
        game.getServer().runCommand("tp @p ~ ~ ~ ~ -35");
        context.waitTicks(16);
        context.takeScreenshot("b_crate_fx_world");
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("collectionClass");
                field.setAccessible(true);
                field.set(null, "GUARDIAN");
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not pick the collection class: " + e);
            }
        });
        showTab(context, "COLLECTION");
        context.takeScreenshot("b_collection");
        closeScreen(context);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] B gains: " + manager.save().skillsOf(player.getUUID().toString()).gains);
            for (com.mki.nightsiege.core.CrateGains.Gain g : com.mki.nightsiege.core.CrateGains.all()) {
                net.minecraft.resources.Identifier icon = net.minecraft.resources.Identifier.tryParse(
                        com.mki.nightsiege.core.CrateGains.icon(g.id()));
                if (icon == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(icon)) {
                    System.out.println("[Night Siege test] B missing icon: " + g.id());
                }
            }
        });
        context.waitTicks(5);
    }

    /**
     * Melee balance simulation: an open field with a core, then /nightsiege sim runs every
     * melee subclass through test nights (old and new values). The server sprints meanwhile.
     */
    private static void simScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 30;
        int cy = pos.getY();
        int cz = pos.getZ();
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block"
                .formatted(cx - 24, cy - 1, cz - 24, cx + 24, cy - 1, cz + 24));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air"
                .formatted(cx - 24, cy, cz - 24, cx + 24, cy + 12, cz + 24));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 -90 10".formatted(cx + 2, cy, cz));
        game.getServer().runCommand("gamerule doDaylightCycle false");
        game.getServer().runCommand("time set 18000");
        context.waitTicks(20);
        // Less work for the (software-rendered) client, so the server has the machine.
        context.runOnClient(client -> {
            client.options.renderDistance().set(2);
            client.options.simulationDistance().set(5);
            client.options.framerateLimit().set(10);
        });
        // NIGHTSIEGE_SIM: classes or subclasses to simulate and the seconds per night, e.g. "GUARDIAN 240".
        // "L5 L10" picks the levels (default 10).
        java.util.List<String> only = new java.util.ArrayList<>();
        java.util.List<Integer> simLevels = new java.util.ArrayList<>();
        int seconds = 150;
        boolean benchRun = false;
        for (String token : System.getenv("NIGHTSIEGE_SIM").trim().split("\\s+")) {
            if (token.matches("\\d+")) {
                if (!token.equals("1")) {
                    seconds = Integer.parseInt(token);
                }
            } else if (token.matches("[Ll]\\d+")) {
                simLevels.add(Integer.parseInt(token.substring(1)));
            } else if (token.equals("bench")) {
                benchRun = true;
            } else if (token.equals("punkte")) {
                // Only the points one has by that level, spent like the playtest (28 October 2026).
                com.mki.nightsiege.game.MeleeSim.budget = true;
                System.out.println("[Night Siege sim] Punkte wie bis zu dieser Stufe (statt voll geskillt)");
            } else if (token.matches("[A-Za-z]+\\.[A-Za-z:_]+=[0-9.]+")) {
                // "classDamageTaken.engineer=1.5": one entry of a map of the config.
                String[] kv = token.split("=");
                String[] path = kv[0].split("\\.", 2);
                onServer(context, (player, manager) -> {
                    try {
                        java.lang.reflect.Field f = manager.config().getClass().getField(path[0]);
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Double> map = (java.util.Map<String, Double>) f.get(manager.config());
                        map.put(path[1], Double.parseDouble(kv[1]));
                    } catch (ReflectiveOperationException | ClassCastException e) {
                        System.out.println("[Night Siege sim] unbekannter Wert " + kv[0]);
                    }
                });
                System.out.println("[Night Siege sim] Einstellung " + kv[0] + " = " + kv[1]);
            } else if (token.matches("[A-Za-z]+=[0-9.a-z]+")) {
                // "engineer=0.7": a class damage factor; "trapDamage=0.6": any number or switch of the config.
                String[] kv = token.split("=");
                onServer(context, (player, manager) -> {
                    var cfg = manager.config();
                    if (java.util.List.of("warrior", "archer", "guardian", "engineer", "summoner").contains(kv[0])) {
                        cfg.classDamage.put(kv[0], Double.parseDouble(kv[1]));
                        return;
                    }
                    try {
                        java.lang.reflect.Field f = cfg.getClass().getField(kv[0]);
                        if (f.getType() == double.class) {
                            f.setDouble(cfg, Double.parseDouble(kv[1]));
                        } else if (f.getType() == int.class) {
                            f.setInt(cfg, (int) Double.parseDouble(kv[1]));
                        } else if (f.getType() == boolean.class) {
                            f.setBoolean(cfg, Boolean.parseBoolean(kv[1]));
                        }
                    } catch (ReflectiveOperationException e) {
                        System.out.println("[Night Siege sim] unbekannter Wert " + kv[0]);
                    }
                });
                System.out.println("[Night Siege sim] Einstellung " + kv[0] + " = " + kv[1]);
            } else if (!token.isBlank()) {
                only.add(token);
            }
        }
        final int simSeconds = seconds;
        final boolean simBench = benchRun;
        net.minecraft.core.BlockPos core = new net.minecraft.core.BlockPos(cx, cy, cz);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.setBlock(core, com.mki.nightsiege.game.ModContent.SIEGE_CORE.defaultBlockState(), 3);
            manager.onCorePlaced(level, core, player);
            manager.save().register(player.getUUID().toString());
            String problem = com.mki.nightsiege.game.MeleeSim.start(player, simLevels, simSeconds, only, simBench);
            System.out.println("[Night Siege sim] start: " + (problem == null ? "ok" : problem));
        });
        context.waitTicks(40);
        game.getServer().runCommand("tick sprint 2000000");
        boolean shot = false;
        for (int i = 0; i < 6000; i++) {
            context.waitTicks(60);
            boolean done = context.computeOnClient(client -> com.mki.nightsiege.game.MeleeSim.done());
            if (!shot && i == 3) {
                context.takeScreenshot("sim_fight");
                shot = true;
            }
            if (done) {
                break;
            }
            if (i % 10 == 0) {
                int n = context.computeOnClient(client -> com.mki.nightsiege.game.MeleeSim.results().size());
                System.out.println("[Night Siege sim] running, finished runs: " + n);
            }
        }
        game.getServer().runCommand("tick sprint stop");
        context.waitTicks(20);
        for (String line : context.computeOnClient(client -> com.mki.nightsiege.game.MeleeSim.report())) {
            System.out.println(line);
        }
    }

    /** Gives every B2 gain to every class, so the kits and gadgets can be tried. */
    private static void giveB2Gains(com.mki.nightsiege.core.PlayerSkills own) {
        String[][] byClass = {
                {"WARRIOR", "wurfbeile:3", "kampfhorn:1", "schlachtentrance:1", "wurfspeere:1", "kletterhaken:1",
                        "kriegsbanner:1", "sprungstiefel:1"},
                {"ARCHER", "lichtpfeile:1", "kletterpfeile:1", "sturmpfeile:1", "tarnumhang:1",
                        "zielfernrohr:1", "lenkpfeile:1", "lockpfeil:1", "spektralpfeile:1"},
                {"GUARDIAN", "barrikaden:1", "signalhorn:1", "mauerkelle:1", "leibwaechter:1", "turmschild:1",
                        "leichtschild:1", "schildlaterne:1", "spiegelschild:1"},
                {"ENGINEER", "ersatzbatterie:1", "oelfass:1", "kupfergolem:1", "magnetfeld:1",
                        "werkzeugtasche:1", "zweite_ladung:1", "stahlturm:1", "recycling:1", "tarnfallen:1"}};
        for (String[] row : byClass) {
            com.mki.nightsiege.core.SkillClass c = com.mki.nightsiege.core.SkillClass.valueOf(row[0]);
            own.unlockClass(c);
            for (int i = 1; i < row.length; i++) {
                String[] kv = row[i].split(":");
                own.setGainStage(c, kv[0], Integer.parseInt(kv[1]));
            }
        }
    }

    /** Starts a short test night with one class and waits until it runs. */
    private static void b2Night(ClientGameTestContext context, int[] core, String clazz) {
        onServer(context, (player, manager) -> {
            manager.onPlayClass(player, clazz);
            manager.config().testPrepSeconds = 3;
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean ok = manager.prepareTest(level.getServer(), level, new net.minecraft.core.BlockPos(core[0], core[1], core[2]), 14,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] B2 " + clazz + " prep ok=" + ok);
        });
        context.waitTicks(20);
        closeScreen(context);
        context.waitTicks(60);
        onServer(context, (player, manager) -> {
            StringBuilder items = new StringBuilder();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = player.getInventory().getItem(i);
                String g = com.mki.nightsiege.game.Gadgets.of(st);
                String a = com.mki.nightsiege.game.Gadgets.arrowKind(st);
                if (!g.isEmpty() || !a.isEmpty()) {
                    items.append(st.getCount()).append('x').append(g.isEmpty() ? a : g).append(' ');
                }
            }
            System.out.println("[Night Siege test] B2 " + clazz + " night active=" + manager.isActive()
                    + " defending=" + manager.isDefending(player) + " gadgets=" + items);
        });
    }

    private static void inventoryShot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> client.setScreenAndShow(
                new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
        context.waitTicks(15);
        context.takeScreenshot(name);
        closeScreen(context);
    }

    /** Version B2: the gadgets of every class in a short test night each. */
    /**
     * The class overview in the core window, the subclass window with its armour stands and the
     * class window before a night with the stands (Simon's design of 26 October 2026). The player's
     * own progress comes back afterwards.
     */
    private static void classUiScene(ClientGameTestContext context, TestSingleplayerContext game) {
        closeScreen(context);
        final String[] backup = new String[1];
        onServer(context, (player, manager) -> {
            var own = manager.save().skillsOf(player.getUUID().toString());
            backup[0] = com.mki.nightsiege.game.Json.toJson(own);
            own.reset();
            own.chosenClass = com.mki.nightsiege.core.SkillClass.WARRIOR.name();
            own.unlockClass(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.fixSub(com.mki.nightsiege.core.SkillClass.WARRIOR, "w_combo");
            own.award(2000);
            var tree = manager.skillTree();
            // The way of the blade with its ultimate at level 3: the next class opens.
            for (int round = 0; round < 40; round++) {
                for (com.mki.nightsiege.core.SkillNode n : tree.ofClass(com.mki.nightsiege.core.SkillClass.WARRIOR)) {
                    if (tree.blockedReason(n.id(), own) == null) {
                        tree.unlock(n.id(), own);
                    }
                }
            }
            System.out.println("[Night Siege test] classes ult3=" + tree.ultimateMaxed(own, com.mki.nightsiege.core.SkillClass.WARRIOR)
                    + " way=" + own.pathTaken(tree) + " sub=" + tree.chosenSubclass(own, com.mki.nightsiege.core.SkillClass.WARRIOR)
                    + " axe blocked=" + tree.blockedReason("heavy_blow", own) + " berserker blocked=" + tree.blockedReason("bloodlust", own)
                    + " nextOpen=" + manager.mayOpenNextClass(player, own));
            manager.onChooseClass(player, "GUARDIAN:g_bastion");
            System.out.println("[Night Siege test] classes unlocked=" + own.unlockedClasses()
                    + " guardian sub=" + tree.chosenSubclass(own, com.mki.nightsiege.core.SkillClass.GUARDIAN)
                    + " medic blocked=" + own.as(com.mki.nightsiege.core.SkillClass.GUARDIAN, () -> tree.blockedReason("g_healing_hand", own)));
        });
        context.waitTicks(10);
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field tab = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                tab.setAccessible(true);
                for (Object t : tab.getType().getEnumConstants()) {
                    if (t.toString().equals("CLASSES")) {
                        tab.set(null, t);
                    }
                }
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] classes tab not set: " + e);
            }
        });
        // One unlock free (Simon, 27 October 2026): the own class card gets "Freischalten" too.
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlocksFree = 1;
            manager.openCoreWindow(player);
        });
        context.waitTicks(20);
        context.takeScreenshot("ui_classes");
        context.runOnClient(client -> {
            StringBuilder labels = new StringBuilder("[Night Siege test] classes buttons:");
            for (net.minecraft.client.gui.components.events.GuiEventListener l : client.gui.screen().children()) {
                if (l instanceof net.minecraft.client.gui.components.Button b && !b.getMessage().getString().isEmpty()) {
                    labels.append(" [").append(b.getMessage().getString()).append("]");
                }
            }
            System.out.println(labels);
        });
        context.runOnClient(client -> client.setScreenAndShow(new com.mki.nightsiege.client.SubclassPickScreen(
                com.mki.nightsiege.core.SkillClass.WARRIOR, com.mki.nightsiege.client.SubclassPickScreen.Mode.NEXT,
                client.gui.screen(), "", java.util.List.of("blademaster"))));
        context.waitTicks(10);
        context.takeScreenshot("ui_subpick_taken");
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field tab = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                tab.setAccessible(true);
                for (Object t : tab.getType().getEnumConstants()) {
                    if (t.toString().equals("STATS")) {
                        tab.set(null, t);
                    }
                }
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] stats tab not set: " + e);
            }
        });
        onServer(context, (player, manager) -> manager.openCoreWindow(player));
        context.waitTicks(20);
        context.takeScreenshot("ui_profiles");
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field tab = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                tab.setAccessible(true);
                for (Object t : tab.getType().getEnumConstants()) {
                    if (t.toString().equals("CLASSES")) {
                        tab.set(null, t);
                    }
                }
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] classes tab not set: " + e);
            }
        });
        onServer(context, (player, manager) -> manager.openCoreWindow(player));
        context.waitTicks(10);
        // The same at GUI scale 4 (a small screen): tabs and buttons must stay on it.
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        context.runOnClient(client -> {
            client.options.guiScale().set(4);
            client.resizeGui();
        });
        context.waitTicks(5);
        context.takeScreenshot("ui_classes_gui4");
        context.runOnClient(client -> {
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        context.waitTicks(5);
        for (com.mki.nightsiege.core.SkillClass c : new com.mki.nightsiege.core.SkillClass[]{
                com.mki.nightsiege.core.SkillClass.ARCHER, com.mki.nightsiege.core.SkillClass.SUMMONER}) {
            context.runOnClient(client -> client.setScreenAndShow(new com.mki.nightsiege.client.SubclassPickScreen(
                    c, com.mki.nightsiege.client.SubclassPickScreen.Mode.NEXT, client.gui.screen(), "")));
            context.waitTicks(10);
            context.takeScreenshot("ui_subpick_" + c.name().toLowerCase(java.util.Locale.ROOT));
        }
        context.runOnClient(client -> {
            client.options.guiScale().set(1);
            client.resizeGui();
        });
        context.waitTicks(5);
        context.takeScreenshot("ui_subpick_gui1");
        context.runOnClient(client -> {
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        closeScreen(context);
        // The class window before a test night: stands on the cards.
        onServer(context, (player, manager) -> {
            manager.config().testPrepSeconds = 20;
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.CoreRecord c = manager.save().cores.get(0);
            boolean ok = manager.prepareTest(level.getServer(), level, new net.minecraft.core.BlockPos(c.x, c.y, c.z), 12,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] classes prep=" + ok);
        });
        context.waitTicks(20);
        context.takeScreenshot("ui_class_pick_stands");
        onServer(context, (player, manager) -> {
            manager.cancelPreparation();
            var own = manager.save().skillsOf(player.getUUID().toString());
            var old = com.mki.nightsiege.game.Json.fromJson(backup[0], com.mki.nightsiege.core.PlayerSkills.class);
            for (java.lang.reflect.Field f : com.mki.nightsiege.core.PlayerSkills.class.getFields()) {
                try {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.set(own, f.get(old));
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        closeScreen(context);
    }

    private static void versionB2Scene(ClientGameTestContext context, TestSingleplayerContext game) {
        java.util.List<String> b2Fails = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        int[] core = context.computeOnClient(client -> {
            com.mki.nightsiege.game.CoreRecord c = com.mki.nightsiege.game.SiegeManager.get().save().cores.get(0);
            return new int[]{c.x, c.y, c.z};
        });
        // A right click on the core signs up and opens the menu at once (hotfix 24.10.).
        onServer(context, (player, manager) -> {
            manager.save().unregister(player.getUUID().toString());
            com.mki.nightsiege.game.CoreRecord c = manager.save().cores.get(0);
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(c.x, c.y, c.z);
            try {
                java.lang.reflect.Method use = com.mki.nightsiege.game.SiegeManager.class.getDeclaredMethod(
                        "onUseBlock", Object.class, Object.class, Object.class, Object.class);
                use.setAccessible(true);
                Object result = use.invoke(manager, player, player.level(), net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                                net.minecraft.core.Direction.UP, pos, false));
                System.out.println("[Night Siege test] core right click: " + result + " registered=" + manager.isRegistered(player));
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] core right click failed: " + e);
            }
        });
        context.waitTicks(15);
        context.takeScreenshot("core_rightclick_signup");
        closeScreen(context);
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("effect give @p minecraft:night_vision 900 0 true");
        int px = core[0] + 3;
        int py = core[1];
        int pz = core[2] + 3;
        game.getServer().runCommand("tp @p %d.5 %d %d.5 -90 15".formatted(px, py, pz));
        // A wall to shoot at, eight blocks to the east, and a floor in front of it.
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone_bricks".formatted(px + 8, py, pz - 3, px + 8, py + 6, pz + 3));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(px + 1, py - 1, pz - 3, px + 7, py - 1, pz + 3));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(px + 1, py, pz - 3, px + 7, py + 5, pz + 3));
        onServer(context, (player, manager) -> {
            giveB2Gains(manager.save().skillsOf(player.getUUID().toString()));
            manager.abort(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
        });
        context.waitTicks(10);

        // ---- warrior: thrown axes, the war horn, banner and battle trance ----
        b2Night(context, core, "WARRIOR");
        inventoryShot(context, "b2_kit_warrior");
        for (int i = 0; i < 4; i++) {
            game.getServer().runCommand("summon minecraft:zombie %d %d %d {NoAI:1b,PersistenceRequired:1b,Rotation:[90f,0f]}"
                    .formatted(px + 5, py, pz - 2 + i));
        }
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            player.setYRot(-90f);
            player.setXRot(10f);
            boolean axe = com.mki.nightsiege.game.GainGear.testUse(player, "wurfbeil");
            boolean banner = com.mki.nightsiege.game.GainGear.testPlace(player, "kriegsbanner",
                    new net.minecraft.core.BlockPos(px + 2, py - 1, pz + 2));
            boolean barricade = com.mki.nightsiege.game.GainGear.testPlace(player, "barrikade",
                    new net.minecraft.core.BlockPos(px + 3, py - 1, pz));
            com.mki.nightsiege.game.SecondUlts.fire(player);
            // The horn is on a gain key now (balance update 2), not in the kit.
            var own = manager.save().skillsOf(player.getUUID().toString());
            java.util.List<String> keys = com.mki.nightsiege.game.GainKeys.assigned(manager, own, own.clazz());
            manager.onCoreAction(player, "gain:" + keys.indexOf("kampfhorn"));
            long horn = com.mki.nightsiege.game.GainKeys.pauseLeft(player, "kampfhorn");
            System.out.println("[Night Siege test] B2 warrior: axe=" + axe + " banner=" + banner + " barricade=" + barricade
                    + " keys=" + keys + " hornPause=" + horn + " state=" + com.mki.nightsiege.game.GainGear.debugState());
            if (!keys.contains("kampfhorn") || !keys.contains("kletterhaken") || horn <= 0) {
                b2Fails.add("warrior gain keys " + keys + " horn pause " + horn);
            }
            if (kitHas(player, "kampfhorn") || kitHas(player, "kletterhaken")) {
                b2Fails.add("the warrior's kit still holds the horn or the hook");
            }
        });
        context.waitTicks(25);
        context.takeScreenshot("b2_warrior_gadgets");
        onServer(context, (player, manager) -> {
            StringBuilder hp = new StringBuilder();
            for (net.minecraft.world.entity.monster.zombie.Zombie z : player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.zombie.Zombie.class, player.getBoundingBox().inflate(12))) {
                hp.append((int) z.getHealth()).append(z.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) ? "s " : " ");
            }
            System.out.println("[Night Siege test] B2 zombies: " + hp);
            manager.abort(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
            System.out.println("[Night Siege test] B2 after night: temp=" + manager.save().tempBlocks.size()
                    + " fence=" + player.level().getBlockState(new net.minecraft.core.BlockPos(px + 3, py, pz)));
        });
        game.getServer().runCommand("kill @e[type=minecraft:zombie]");
        context.waitTicks(10);

        // ---- archer: light, climbing and storm arrows, the quiver ----
        b2Night(context, core, "ARCHER");
        inventoryShot(context, "b2_kit_archer");
        // The quiver (balance update 2, Simon 29.10.): no arrow stacks of their own, the kinds are shots
        // in the quiver, picked on the bar; a normal arrow shot after the pick flies as that kind.
        onServer(context, (player, manager) -> {
            StringBuilder stacks = new StringBuilder();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = player.getInventory().getItem(i);
                if (!com.mki.nightsiege.game.Gadgets.arrowKind(st).isEmpty() || st.is(net.minecraft.world.item.Items.SPECTRAL_ARROW)) {
                    stacks.append(st.getCount()).append('x').append(st.getHoverName().getString()).append(' ');
                }
            }
            var q = com.mki.nightsiege.game.GainGear.quiverOf(player);
            java.util.List<String> bar = new java.util.ArrayList<>();
            for (var slot : manager.abilitySlots(player)) {
                bar.add(slot.id.isEmpty() ? "normal" : slot.id);
            }
            System.out.println("[Night Siege test] B2 quiver: stacks=" + stacks + " shots=" + (q == null ? "-" : q.shots) + " bar=" + bar);
            if (stacks.length() > 0) {
                b2Fails.add("special arrow stacks in the kit: " + stacks);
            }
            // Update 30.10.: one arrow key (held + wheel), no key per kind.
            if (q == null || q.shots.getOrDefault("light", 0) != 16 || q.shots.getOrDefault("spectral", 0) != 16
                    || !bar.contains("quiver") || bar.contains("light") || bar.contains("storm")) {
                b2Fails.add("quiver " + (q == null ? "-" : q.shots) + " bar " + bar);
            }
        });
        String[] kinds = {"light", "climb", "storm"};
        for (int i = 0; i < kinds.length; i++) {
            final int k = i;
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                int key = manager.abilitySlotOf(player, "quiver");
                var qq = com.mki.nightsiege.game.GainGear.quiverOf(player);
                for (int turn = 0; turn < 6 && !kinds[k].equals(qq.current); turn++) {
                    manager.onCoreAction(player, "cycle:" + key + ":1");   // the wheel, the key held
                }
                net.minecraft.world.entity.projectile.arrow.Arrow arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(
                        level, player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
                arrow.setPos(px + 1.5, py + 3.5, pz - 1.5 + k * 1.5);
                arrow.shoot(1, 0.0, 0, 2.5f, 0f);
                level.addFreshEntity(arrow);
                String info = com.mki.nightsiege.game.GainGear.debugArrow(arrow);
                System.out.println("[Night Siege test] B2 arrow " + kinds[k] + ": " + info
                        + " shots=" + com.mki.nightsiege.game.GainGear.quiverOf(player).shots);
                if (!arrow.entityTags().contains("ns_arrow_" + kinds[k])) {
                    b2Fails.add("a shot after picking " + kinds[k] + " flew as " + info);
                }
            });
            context.waitTicks(3);
        }
        onServer(context, (player, manager) -> {
            // Climbing arrows: 4 shots, one gone; a pick of the current kind again goes back to the normal arrows.
            var q = com.mki.nightsiege.game.GainGear.quiverOf(player);
            String before = q.current;
            manager.onCoreAction(player, "slot:" + manager.abilitySlotOf(player, "quiver"));   // a tap: normal arrows
            System.out.println("[Night Siege test] B2 quiver after: shots=" + q.shots + " current " + before + " -> '" + q.current + "'");
            if (q.shots.getOrDefault("climb", 0) != 3 || !q.current.isEmpty()) {
                b2Fails.add("quiver after the shots " + q.shots + " current '" + q.current + "'");
            }
        });
        context.waitTicks(30);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] B2 archer: temp=" + manager.save().tempBlocks);
            for (net.minecraft.world.entity.projectile.arrow.AbstractArrow a : player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, player.getBoundingBox().inflate(16))) {
                System.out.println("[Night Siege test] B2 arrow later: " + a.position() + " " + com.mki.nightsiege.game.GainGear.debugArrow(a));
            }
        });
        game.getServer().runCommand("tp @p %d.5 %d %d.5 -90 -5".formatted(px, py, pz));
        context.waitTicks(10);
        context.takeScreenshot("b2_archer_arrows");
        onServer(context, (player, manager) -> manager.abort(((net.minecraft.server.level.ServerLevel) player.level()).getServer()));
        context.waitTicks(10);

        // ---- engineer: copper golem, oil, magnetic field ----
        b2Night(context, core, "ENGINEER");
        inventoryShot(context, "b2_kit_engineer");
        context.waitTicks(30);
        for (int i = 0; i < 3; i++) {
            game.getServer().runCommand("summon minecraft:zombie %d %d %d {PersistenceRequired:1b}"
                    .formatted(px + 6, py, pz - 2 + 2 * i));
        }
        onServer(context, (player, manager) -> {
            player.setYRot(-90f);
            player.setXRot(20f);
            com.mki.nightsiege.game.SecondUlts.fire(player);
            var own = manager.save().skillsOf(player.getUUID().toString());
            java.util.List<String> keys = com.mki.nightsiege.game.GainKeys.assigned(manager, own, own.clazz());
            manager.onCoreAction(player, "gain:" + keys.indexOf("oelfass"));
            boolean flash = com.mki.nightsiege.game.GainGear.testUse(player, "alchemie_blende");
            net.minecraft.world.item.ItemStack grenade = net.minecraft.world.item.ItemStack.EMPTY;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if ("alchemie_blende".equals(com.mki.nightsiege.game.Gadgets.of(player.getInventory().getItem(i)))) {
                    grenade = player.getInventory().getItem(i);
                }
            }
            float flashCd = grenade.isEmpty() ? -1f : player.getCooldowns().getCooldownPercent(grenade, 0f);
            System.out.println("[Night Siege test] B2 engineer: golems=" + com.mki.nightsiege.game.CopperHelper.count()
                    + " keys=" + keys + " oil=" + com.mki.nightsiege.game.GainGear.debugState() + " flash=" + flash + " flashCd=" + flashCd);
            if (!keys.contains("oelfass") || !keys.contains("ersatzbatterie") || !flash || kitHas(player, "oelfass")
                    || kitHas(player, "blendgranate")) {
                b2Fails.add("engineer keys " + keys + " flash " + flash);
            }
        });
        context.waitTicks(30);
        context.takeScreenshot("b2_engineer");
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] B2 engineer later: " + com.mki.nightsiege.game.GainGear.debugState());
            if (!com.mki.nightsiege.game.GainGear.debugState().contains("oil=1")) {
                b2Fails.add("the oil barrel from its key made no puddle: " + com.mki.nightsiege.game.GainGear.debugState());
            }
            // The flash grenade is back after 7 s (Simon 29.10.): 100 ticks in, it is still down; at 140 it is up.
            manager.abort(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
        });
        game.getServer().runCommand("kill @e[type=minecraft:zombie]");
        context.waitTicks(10);

        // ---- guardian: tower shield, mirror, chain ----
        b2Night(context, core, "GUARDIAN");
        inventoryShot(context, "b2_kit_guardian");
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.SecondUlts.fire(player);
            // Three gain abilities, two keys: the rarest first (chain, trowel); a click in the collection
            // puts the signal horn on the first key; "Dritte Hand" brings the third key.
            var own = manager.save().skillsOf(player.getUUID().toString());
            java.util.List<String> first = com.mki.nightsiege.game.GainKeys.assigned(manager, own, own.clazz());
            manager.onCoreAction(player, "gainkey:GUARDIAN:signalhorn");
            java.util.List<String> chosen = com.mki.nightsiege.game.GainKeys.assigned(manager, own, own.clazz());
            manager.onCoreAction(player, "gain:0");
            boolean signal = player.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING);
            own.setGainStage(com.mki.nightsiege.core.SkillClass.GUARDIAN, "dritte_hand", 1);
            java.util.List<String> three = com.mki.nightsiege.game.GainKeys.assigned(manager, own, own.clazz());
            manager.onCoreAction(player, "gain:" + three.indexOf("mauerkelle"));
            boolean trowel = com.mki.nightsiege.game.GainGear.usedTonight(player, "mauerkelle");
            var hud = com.mki.nightsiege.game.GainKeys.slots(manager, player, player.level().getGameTime());
            StringBuilder boxes = new StringBuilder();
            for (var b : hud) {
                boxes.append(b.id).append(b.current ? "(spent)" : "").append(b.cooldownTicks > 0 ? "(" + b.cooldownTicks + ")" : "").append(' ');
            }
            System.out.println("[Night Siege test] B2 guardian keys: first=" + first + " chosen=" + chosen + " three=" + three
                    + " hud=" + boxes);
            if (!first.equals(java.util.List.of("leibwaechter", "mauerkelle")) || !chosen.get(0).equals("signalhorn")
                    || three.size() != 3 || !signal || !trowel) {
                b2Fails.add("guardian keys first=" + first + " chosen=" + chosen + " three=" + three + " signal=" + signal
                        + " trowel=" + trowel);
            }
            net.minecraft.world.item.ItemStack shield = player.getOffhandItem();
            System.out.println("[Night Siege test] B2 guardian: mirror=" + com.mki.nightsiege.game.SecondUlts.mirrorUp(player)
                    + " signal=" + signal + " trowel=" + trowel
                    + " shield=" + shield.get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)
                    + " use=" + shield.get(net.minecraft.core.component.DataComponents.USE_EFFECTS));
        });
        context.waitTicks(20);
        onServer(context, (player, manager) -> {
            manager.abort(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
            manager.config().testPrepSeconds = 60;
            System.out.println("[Night Siege test] B2 done: temp=" + manager.save().tempBlocks.size()
                    + " golems=" + com.mki.nightsiege.game.CopperHelper.count());
        });
        context.waitTicks(10);
        if (!b2Fails.isEmpty()) {
            throw new AssertionError("B2 gadgets: " + String.join("; ", b2Fails));
        }
    }

    /** True when the kit holds a gadget with this id. */
    private static boolean kitHas(net.minecraft.server.level.ServerPlayer player, String gadget) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (gadget.equals(com.mki.nightsiege.game.Gadgets.of(player.getInventory().getItem(i)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The raid's plan (27 October 2026): a castle with a gate, an archway and a keep, two houses
     * outside. The scan finds the castle (gate and archway), the houses are ground in front; the
     * wave comes from one side, the melee monsters gather in front of the wall and wait; a hole,
     * a ladder and the opened gate let them in; one stuck in a pit comes back from the spawn ground.
     */
    /**
     * Update 2 "Fluch & Rache" (Simon, 28 October 2026): three stones on the curse altar (one refused,
     * one taken off), the window with the altar and the wanted posters, a test night under the curses,
     * a nemesis called back, promoted, a new one made, one slain and its trophy.
     */
    /**
     * The Ruhmeshalle (31 October 2026): a hall with the player and five made-up defenders, the
     * badge thresholds, the points command, then every page of the tab as a picture.
     */
    private static void hallScene(ClientGameTestContext context, TestSingleplayerContext game) {
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.HallOfFame hall = manager.save().hall;
            hall.heroes.clear();
            hall.bestiary.clear();
            hall.curioBests.clear();
            String[][] people = {
                    {player.getUUID().toString(), player.getScoreboardName(), "blademaster", "warrior"},
                    {"6b1b6c47-1d8a-4c1e-9a0a-000000000001", "Ritter_Kai", "bulwark", "guardian"},
                    {"6b1b6c47-1d8a-4c1e-9a0a-000000000002", "Mia_Bogen", "swift", "archer"},
                    {"6b1b6c47-1d8a-4c1e-9a0a-000000000003", "Heilerin", "medic", "guardian"},
                    {"6b1b6c47-1d8a-4c1e-9a0a-000000000004", "Tuefftler", "gunner", "engineer"},
                    {"6b1b6c47-1d8a-4c1e-9a0a-000000000005", "Lukas", "necromancer", "summoner"}};
            com.mki.nightsiege.core.HallOfFame.LastNight last = new com.mki.nightsiege.core.HallOfFame.LastNight();
            last.date = "2026-10-30";
            last.night = 14;
            last.level = 9;
            last.result = "gewonnen";
            for (int i = 0; i < people.length; i++) {
                String[] p = people[i];
                com.mki.nightsiege.core.HallOfFame.Hero h = hall.hero(p[0], p[1]);
                h.nights = 14 - i;
                h.held = 11 - i;
                h.lost = 3;
                h.kills = 1200 - i * 170;
                h.assists = 300 - i * 30;
                h.deaths = 2 + i;
                h.bosses = 9 - i;
                h.dealt = 48000 - i * 6000;
                h.taken = 9000 + i * 500;
                h.intercepted = i == 1 ? 31000 : 2000;
                h.healed = i == 3 ? 6500 : 300;
                h.towerKills = i == 4 ? 640 : 0;
                h.nightsNoDeath = 9 - i;
                h.streak = 4;
                h.bestStreak = 8 - i;
                h.dungeons = 6;
                h.dungeonsWon = 5 - Math.min(4, i);
                h.camps = 3;
                h.campsDestroyed = 2;
                h.crates = 120 - i * 15;
                h.rareCrates = 9;
                h.maxCurses = Math.max(0, 3 - i);
                h.dealtBy.put("nah", 30000.0 - i * 3000);
                h.dealtBy.put("fern", 9000.0);
                h.dealtBy.put("faehigkeit", 6000.0);
                h.dealtBy.put("turm", i == 4 ? 15000.0 : 500.0);
                com.mki.nightsiege.core.HallOfFame.SubLine sub = new com.mki.nightsiege.core.HallOfFame.SubLine();
                sub.clazz = p[3];
                sub.nights = h.nights - 2;
                sub.held = h.held - 2;
                sub.kills = h.kills - 200;
                sub.dealt = h.dealt - 5000;
                h.subs.put(p[2], sub);
                com.mki.nightsiege.core.HallOfFame.SubLine other = new com.mki.nightsiege.core.HallOfFame.SubLine();
                other.clazz = "archer";
                other.nights = 2;
                other.held = 2;
                other.kills = 200;
                other.dealt = 5000;
                h.subs.put("heavy", other);
                h.victims.put("minecraft:zombie", 400 - i * 20);
                h.victims.put("nightsiege:revenant", 90);
                h.killedBy.put("nightsiege:executioner", 2);
                h.takenFrom.put("minecraft:skeleton", 3000.0);
                for (int n = 0; n < 10; n++) {
                    com.mki.nightsiege.core.HallOfFame.NightMini mini = new com.mki.nightsiege.core.HallOfFame.NightMini();
                    mini.date = "2026-10-" + (21 + n);
                    mini.night = 5 + n;
                    mini.level = 1 + n;
                    mini.held = n % 4 != 2;
                    mini.dealt = 2000 + n * 600 - i * 200;
                    mini.kills = 50 + n * 8;
                    mini.deaths = n % 3;
                    mini.sub = p[2];
                    h.recent.add(mini);
                }
                hall.curio(p[0], h, "weitesterTreffer", 52.4 - i * 6, "2026-10-29");
                hall.curio(p[0], h, "blutrausch", 13 - i, "2026-10-29");
                hall.curio(p[0], h, "mehrfach", 6 - Math.min(5, i), "2026-10-29");
                hall.curio(p[0], h, "creeper", 30 + i, "2026-10-29");
                hall.curio(p[0], h, "ohneTreffer", 240 - i * 20, "2026-10-29");
                hall.curio(p[0], h, "retter", i == 3 ? 40 : 2, "2026-10-29");
                hall.curio(p[0], h, "geschosse", i == 2 ? 3800 : 100, "2026-10-29");
                com.mki.nightsiege.core.HallOfFame.Entry e = new com.mki.nightsiege.core.HallOfFame.Entry();
                e.uuid = p[0];
                e.name = p[1];
                e.clazz = p[3];
                e.sub = p[2];
                e.kills = 140 - i * 17;
                e.dealt = 5200 - i * 700;
                e.healed = i == 3 ? 900 : 40;
                e.intercepted = i == 1 ? 3100 : 200;
                e.bosses = i < 2 ? 1 : 0;
                e.deaths = i % 3;
                e.held = true;
                last.entries.add(e);
                java.util.List<String> up = com.mki.nightsiege.core.HallBadges.update(h, manager.config().hallBadgeTiers);
                System.out.println("[Night Siege test] hall badges " + p[1] + " " + up);
            }
            hall.lastNight = last;
            String[] beasts = {"minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "nightsiege:revenant", "nightsiege:executioner",
                    "minecraft:zombie+elite", "boss:minecraft:wither_skeleton", "maschine"};
            for (int i = 0; i < beasts.length; i++) {
                com.mki.nightsiege.core.HallOfFame.Beast b = hall.bestiary.computeIfAbsent(beasts[i], k -> new com.mki.nightsiege.core.HallOfFame.Beast());
                b.seen = 900 - i * 100;
                b.killed = 880 - i * 100;
                b.killedPlayers = i % 3;
                b.damageToPlayers = 4000 - i * 300;
            }
            // Badge thresholds: 1000 kills is silver, 5000 gold.
            double[] t = com.mki.nightsiege.core.HallBadges.tiers(com.mki.nightsiege.core.HallBadges.byId("schlaechter"), java.util.Map.of());
            System.out.println("[Night Siege test] hall tiers schlaechter=" + java.util.Arrays.toString(t)
                    + " 999=" + com.mki.nightsiege.core.HallBadges.tier(999, t) + " 1000=" + com.mki.nightsiege.core.HallBadges.tier(1000, t)
                    + " 5000=" + com.mki.nightsiege.core.HallBadges.tier(5000, t)
                    + " override=" + java.util.Arrays.toString(com.mki.nightsiege.core.HallBadges.tiers(
                    com.mki.nightsiege.core.HallBadges.byId("schlaechter"), java.util.Map.of("schlaechter", "10/20,5/x"))));
            com.mki.nightsiege.core.HallOfFame.Hero mine = hall.heroes.get(player.getUUID().toString());
            mine.badges.put("bollwerk", 3);
            manager.onCoreAction(player, "hall:title:bollwerk");
            System.out.println("[Night Siege test] hall title=" + mine.title + " gold=" + mine.badges);
        });
        context.waitTicks(10);
        // The points command: a fixed amount, taken away again, then catching up.
        game.getServer().runCommand("nightsiege punkte @p 5");
        game.getServer().runCommand("nightsiege punkte @p -2");
        game.getServer().runCommand("nightsiege punkte @p aufholen");
        context.waitTicks(10);
        String[][] pages = {{"PODIUM", "hall_podium"}, {"CHRONICLE", "hall_chronicle"}, {"BADGES", "hall_badges"},
                {"CURIOS", "hall_curios"}, {"BESTIARY", "hall_bestiary"}};
        showTab(context, "HALL");
        context.waitTicks(20);
        for (String[] page : pages) {
            context.runOnClient(client -> {
                try {
                    Class<?> pagesClass = Class.forName("com.mki.nightsiege.client.HallPages");
                    java.lang.reflect.Field field = pagesClass.getDeclaredField("page");
                    field.setAccessible(true);
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object value = Enum.valueOf((Class) field.getType(), page[0]);
                    field.set(null, value);
                } catch (ReflectiveOperationException e) {
                    System.out.println("[Night Siege test] could not set the hall page: " + e);
                }
            });
            context.waitTicks(10);
            context.takeScreenshot(page[1]);
        }
        // The podium of all time, by damage.
        context.runOnClient(client -> {
            try {
                Class<?> pagesClass = Class.forName("com.mki.nightsiege.client.HallPages");
                java.lang.reflect.Field page = pagesClass.getDeclaredField("page");
                page.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object podium = Enum.valueOf((Class) page.getType(), "PODIUM");
                page.set(null, podium);
                java.lang.reflect.Field last = pagesClass.getDeclaredField("lastNight");
                last.setAccessible(true);
                last.set(null, false);
                java.lang.reflect.Field cat = pagesClass.getDeclaredField("category");
                cat.setAccessible(true);
                cat.set(null, "dealt");
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not set the podium: " + e);
            }
        });
        context.waitTicks(10);
        context.takeScreenshot("hall_podium_total");
        closeScreen(context);
    }

    private static void curseScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 300;
        int cz = pos.getZ() - 300;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 64, cz - 64, cx + 64, cz + 64));
        context.waitTicks(60);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);
        int cy = ground[0];
        int out = 40;
        for (int part = -out; part <= out; part += 16) {
            int z1 = cz + part;
            int z2 = Math.min(cz + out, z1 + 15);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - out, cy - 1, z1, cx + out, cy - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - out, cy, z1, cx + out, cy + 9, z2));
        }
        BlockPos core = new BlockPos(cx, cy, cz);
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(cx, cy, cz - 3));
        context.waitTicks(10);
        // The altar: three stones, a fourth refused, one taken off and another laid; two posters.
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.SiegeSave save = manager.save();
            save.curses.clear();
            com.mki.nightsiege.game.Nemesis.clear(save, true);
            manager.onCoreAction(player, "curse:zaeh");
            manager.onCoreAction(player, "curse:champions");
            manager.onCoreAction(player, "curse:siechtum");
            manager.onCoreAction(player, "curse:blutdurst");   // the altar is full: refused
            String full = String.valueOf(save.curses);
            manager.onCoreAction(player, "curse:siechtum");    // taken off again
            manager.onCoreAction(player, "curse:uebermacht");
            com.mki.nightsiege.game.SiegeSave.NemesisRecord lurk = manager.nemesis().make(manager.nemesisHost(), "minecraft:zombie", player, true, 2);
            com.mki.nightsiege.game.SiegeSave.NemesisRecord dead = manager.nemesis().make(manager.nemesisHost(), "minecraft:spider", player, true, 1);
            dead.status = "erschlagen";
            dead.slainBy = player.getScoreboardName();
            System.out.println("[Night Siege test] curse altar full=" + full + " now=" + save.curses + " posters=" + save.nemeses.size()
                    + " lurk=" + lurk.name + "/" + lurk.level + lurk.traits + " dead=" + dead.name);
        });
        context.waitTicks(10);
        showTab(context, "CURSE");
        context.takeScreenshot("curse_altar");
        closeScreen(context);

        final int[] keep = new int[6];
        final double[] keepD = new double[3];
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            keep[0] = cfg.baseMobsPerBatch;
            keep[1] = cfg.batchIntervalSeconds;
            keep[2] = cfg.nightPhantoms;
            keep[3] = cfg.raidMinLadders;
            keep[4] = cfg.testPrepSeconds;
            keep[5] = cfg.sapperFromLevel;
            keepD[0] = cfg.mobsPerLevel;
            keepD[1] = cfg.ladderCarrierShare;
            keepD[2] = cfg.treasureRaiderChance;
            cfg.baseMobsPerBatch = 6;
            cfg.mobsPerLevel = 0.0;
            cfg.batchIntervalSeconds = 600;
            cfg.nightPhantoms = 0;
            cfg.raidMinLadders = 0;
            cfg.testPrepSeconds = 2;
            cfg.sapperFromLevel = 999;
            cfg.ladderCarrierShare = 0.0;
            cfg.treasureRaiderChance = 0.0;
            manager.testPool(java.util.List.of(new com.mki.nightsiege.core.MobEntry("minecraft:zombie", 1, 10)));
        });
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand("effect give @p minecraft:night_vision 600 0 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 600 10 true");
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean ok = manager.prepareTest(level.getServer(), level, core, 5, com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] curse prep ok=" + ok);
        });
        context.waitTicks(60);
        closeScreen(context);
        context.waitTicks(60);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            java.util.List<net.minecraft.world.entity.Mob> mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    new net.minecraft.world.phys.AABB(core).inflate(90), m -> m.entityTags().contains(com.mki.nightsiege.game.MobFactory.TAG));
            int champions = 0;
            StringBuilder hp = new StringBuilder();
            for (net.minecraft.world.entity.Mob m : mobs) {
                if (m.entityTags().contains(com.mki.nightsiege.game.MobFactory.TAG_ELITE)) {
                    champions++;
                } else if (hp.length() < 40) {
                    hp.append(Math.round(m.getMaxHealth())).append(' ');
                }
            }
            System.out.println("[Night Siege test] curse night active=" + manager.isActive() + " curses=" + com.mki.nightsiege.game.Curses.active()
                    + " count=" + manager.nightCurses() + " life=" + com.mki.nightsiege.game.Curses.healthFactor()
                    + " mobsFactor=" + com.mki.nightsiege.game.Curses.mobFactor() + " mobs=" + mobs.size() + " champions=" + champions
                    + " zombieHp=[" + hp.toString().trim() + "] expectHp=" + Math.round(20.0 * manager.mobHealthFactor() * 1.3)
                    + " bar=" + manager.bossBarText());
        });
        context.waitTicks(10);
        // The nemesis comes back (at once, as with the second wave).
        final double[] at = new double[3];
        onServer(context, (player, manager) -> {
            int n = manager.nemesis().callIn(manager.nemesisHost(), true);
            StringBuilder line = new StringBuilder();
            for (net.minecraft.world.entity.Mob m : manager.nemesis().onFieldMobs()) {
                line.append(m.getCustomName() == null ? "?" : m.getCustomName().getString()).append(" hp=").append(Math.round(m.getMaxHealth()))
                        .append(" target=").append(m.getTarget() == null ? "-" : m.getTarget().getScoreboardName())
                        .append(" glow=").append(m.isCurrentlyGlowing()).append(" d=").append(Math.round(m.distanceTo(player))).append("; ");
                at[0] = m.getX();
                at[1] = m.getY();
                at[2] = m.getZ();
            }
            System.out.println("[Night Siege test] curse nemesis called=" + n + " field=[" + line + "] bar=" + manager.bossBarText());
        });
        context.waitTicks(30);
        game.getServer().runCommand("tp @p %d.5 %d %d.5 facing %.1f %.1f %.1f".formatted(cx, cy, cz - 3, at[0], at[1] + 1.0, at[2]));
        context.waitTicks(40);
        context.takeScreenshot("curse_nemesis");
        // It slays the player (as it would): it rises in rank and flees again.
        onServer(context, (player, manager) -> {
            java.util.List<net.minecraft.world.entity.Mob> field = manager.nemesis().onFieldMobs();
            if (field.isEmpty()) {
                System.out.println("[Night Siege test] curse promote: no nemesis on the field");
                return;
            }
            net.minecraft.world.entity.Mob nem = field.get(0);
            manager.nemesis().onDefenderKilled(manager.nemesisHost(), player, nem.damageSources().mobAttack(nem));
            StringBuilder recs = new StringBuilder();
            for (com.mki.nightsiege.game.SiegeSave.NemesisRecord r : com.mki.nightsiege.game.Nemesis.records(manager.save(), true)) {
                recs.append(r.name).append('/').append(r.level).append(r.traits).append('/').append(r.status).append("; ");
            }
            System.out.println("[Night Siege test] curse promote promoted=" + manager.nemesis().promoted + " removed=" + nem.isRemoved()
                    + " field=" + manager.nemesis().onFieldMobs().size() + " records=[" + recs + "]");
        });
        context.waitTicks(10);
        // An ordinary zombie of the night slays the player: it becomes a nemesis.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            java.util.List<net.minecraft.world.entity.monster.zombie.Zombie> zs = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                    new net.minecraft.world.phys.AABB(core).inflate(90), z -> z.entityTags().contains(com.mki.nightsiege.game.MobFactory.TAG)
                            && !z.entityTags().contains(com.mki.nightsiege.game.Nemesis.TAG));
            if (zs.isEmpty()) {
                System.out.println("[Night Siege test] curse made: no zombie");
                return;
            }
            net.minecraft.world.entity.monster.zombie.Zombie z = zs.get(0);
            int before = com.mki.nightsiege.game.Nemesis.records(manager.save(), true).size();
            manager.nemesis().onDefenderKilled(manager.nemesisHost(), player, z.damageSources().mobAttack(z));
            int after = com.mki.nightsiege.game.Nemesis.records(manager.save(), true).size();
            System.out.println("[Night Siege test] curse made made=" + manager.nemesis().made + " records=" + before + "->" + after
                    + " fled=" + z.isRemoved());
        });
        context.waitTicks(10);
        // Both lurking ones called back; the first is slain by the player: its trophy.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            int n = manager.nemesis().callIn(manager.nemesisHost(), true);
            java.util.List<net.minecraft.world.entity.Mob> field = manager.nemesis().onFieldMobs();
            if (field.isEmpty()) {
                System.out.println("[Night Siege test] curse slay: nobody came (" + n + ")");
                return;
            }
            net.minecraft.world.entity.Mob victim = field.get(0);
            victim.hurtServer(level, player.damageSources().playerAttack(player), 100000f);
            System.out.println("[Night Siege test] curse slay called=" + n + " dead=" + !victim.isAlive());
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.world.item.ItemStack trophy = net.minecraft.world.item.ItemStack.EMPTY;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = player.getInventory().getItem(i);
                net.minecraft.world.item.component.CustomData data = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (data != null && data.copyTag().contains("nemesis_trophy")) {
                    trophy = st;
                }
            }
            StringBuilder recs = new StringBuilder();
            com.mki.nightsiege.game.SiegeSave.NemesisRecord slainRec = null;
            for (com.mki.nightsiege.game.SiegeSave.NemesisRecord r : com.mki.nightsiege.game.Nemesis.records(manager.save(), true)) {
                recs.append(r.name).append('/').append(r.status).append("; ");
                if ("erschlagen".equals(r.status) && r.type.equals("minecraft:spider")) {
                    slainRec = r;
                }
            }
            System.out.println("[Night Siege test] curse trophy slain=" + manager.nemesis().slain + " item="
                    + (trophy.isEmpty() ? "none" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(trophy.getItem()) + " " + trophy.getHoverName().getString())
                    + " records=[" + recs + "]");
            if (!trophy.isEmpty()) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, trophy.copy());
            }
            if (slainRec != null) {
                // The plaque of a monster without a head of its own, in the other hand.
                player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, com.mki.nightsiege.game.Nemesis.trophy(slainRec));
            }
        });
        context.waitTicks(20);
        game.getServer().runCommand("tp @p %d.5 %d %d.5 -180 10".formatted(cx, cy, cz - 3));
        context.waitTicks(20);
        context.takeScreenshot("curse_trophy");
        showTab(context, "CURSE");
        context.takeScreenshot("curse_window_night");
        closeScreen(context);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            manager.abort(level.getServer());
            System.out.println("[Night Siege test] curse after altar=" + manager.save().curses + " active=" + com.mki.nightsiege.game.Curses.count()
                    + " life=" + com.mki.nightsiege.game.Curses.healthFactor());
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            cfg.baseMobsPerBatch = keep[0];
            cfg.batchIntervalSeconds = keep[1];
            cfg.nightPhantoms = keep[2];
            cfg.raidMinLadders = keep[3];
            cfg.testPrepSeconds = keep[4];
            cfg.sapperFromLevel = keep[5];
            cfg.mobsPerLevel = keepD[0];
            cfg.ladderCarrierShare = keepD[1];
            cfg.treasureRaiderChance = keepD[2];
            manager.testPool(null);
            manager.save().curses.clear();
            com.mki.nightsiege.game.Nemesis.clear(manager.save(), true);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        });
        context.waitTicks(20);
        game.getServer().runCommand("forceload remove %d %d %d %d".formatted(cx - 64, cz - 64, cx + 64, cz + 64));
    }

    private static void raidScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 300;
        int cz = pos.getZ() - 300;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 80, cz - 80, cx + 80, cz + 80));
        context.waitTicks(60);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);
        int cy = ground[0];
        int out = 48;
        for (int part = -out; part <= out; part += 16) {
            int z1 = cz + part;
            int z2 = Math.min(cz + out, z1 + 15);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - out, cy - 1, z1, cx + out, cy - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - out, cy, z1, cx + out, cy + 9, z2));
        }
        int r = 12;
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx + r, cy + 5, cz - r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz + r, cx + r, cy + 5, cz + r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx - r, cy + 5, cz + r));
        game.getServer().runCommand(wall.formatted(cx + r, cy, cz - r, cx + r, cy + 5, cz + r));
        // The gate (north) and an archway (east).
        game.getServer().runCommand("setblock %d %d %d minecraft:oak_door[half=lower,facing=north]".formatted(cx, cy, cz - r));
        game.getServer().runCommand("setblock %d %d %d minecraft:oak_door[half=upper,facing=north]".formatted(cx, cy + 1, cz - r));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx + r, cy, cz - 1, cx + r, cy + 2, cz + 1));
        // The keep with the core, its own door.
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:oak_planks hollow".formatted(cx - 3, cy, cz + 2, cx + 3, cy + 4, cz + 8));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - 2, cy, cz + 3, cx + 2, cy, cz + 7));   // floor at ground level
        game.getServer().runCommand("setblock %d %d %d minecraft:oak_door[half=lower,facing=north]".formatted(cx, cy, cz + 2));
        game.getServer().runCommand("setblock %d %d %d minecraft:oak_door[half=upper,facing=north]".formatted(cx, cy + 1, cz + 2));
        // Two houses outside.
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:spruce_planks hollow".formatted(cx - 2, cy, cz + 20, cx + 2, cy + 3, cz + 24));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - 1, cy, cz + 21, cx + 1, cy, cz + 23));
        game.getServer().runCommand("setblock %d %d %d minecraft:spruce_door[half=lower,facing=north]".formatted(cx, cy, cz + 20));
        game.getServer().runCommand("setblock %d %d %d minecraft:spruce_door[half=upper,facing=north]".formatted(cx, cy + 1, cz + 20));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:spruce_planks hollow".formatted(cx - 24, cy, cz - 2, cx - 20, cy + 3, cz + 2));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - 23, cy, cz - 1, cx - 21, cy, cz + 1));
        game.getServer().runCommand("setblock %d %d %d minecraft:spruce_door[half=lower,facing=east]".formatted(cx - 20, cy, cz));
        game.getServer().runCommand("setblock %d %d %d minecraft:spruce_door[half=upper,facing=east]".formatted(cx - 20, cy + 1, cz));
        context.waitTicks(10);
        BlockPos core = new BlockPos(cx, cy, cz + 5);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.BaseDefense.forgetScans();
            long t0 = System.nanoTime();
            com.mki.nightsiege.game.BaseScan scan = com.mki.nightsiege.game.BaseScan.scan(level, core, manager.config().baseRadius);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            StringBuilder gates = new StringBuilder();
            for (com.mki.nightsiege.game.BaseScan.Gate g : scan.gates) {
                gates.append(g.arch() ? "arch@" : "door@").append(g.pos().getX() - cx).append(',').append(g.pos().getZ() - cz).append(' ');
            }
            System.out.println("[Night Siege test] raid scan closed=" + scan.closed + " gates=[" + gates + "] size=" + scan.width() + "x" + scan.depth()
                    + " spawn=" + scan.spawnSpotCount() + " ms=" + ms
                    + " court=" + scan.contains(cx, cz - 5) + " keep=" + scan.contains(cx, cz + 5)
                    + " houseS=" + scan.contains(cx, cz + 22) + " houseW=" + scan.contains(cx - 22, cz)
                    + " field=" + scan.contains(cx, cz - 30));
        });
        context.waitTicks(5);
        // The archway walled up: only the gate is left.
        game.getServer().runCommand(wall.formatted(cx + r, cy, cz - 1, cx + r, cy + 2, cz + 1));
        context.waitTicks(5);
        final int[] keep = new int[8];
        final double[] keepD = new double[4];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.game.BaseScan scan = com.mki.nightsiege.game.BaseScan.scan(level, core, manager.config().baseRadius);
            int near = 0;
            int under = 0;
            int min = Integer.MAX_VALUE;
            for (int s = 0; s < com.mki.nightsiege.game.BaseScan.SLICES; s++) {
                for (BlockPos p : scan.spawnSpots(s)) {
                    double d = Math.sqrt(p.distSqr(new BlockPos(cx, p.getY(), cz)));
                    min = Math.min(min, (int) d);
                    if (scan.contains(p.getX(), p.getZ())) {
                        near++;
                    }
                    if (!level.canSeeSky(p)) {
                        under++;
                    }
                }
            }
            System.out.println("[Night Siege test] raid scan walled closed=" + scan.closed + " gates=" + scan.gates.size()
                    + " spawn=" + scan.spawnSpotCount() + " spawnInside=" + near + " spawnRoofed=" + under + " spawnMinDist=" + min);
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            keep[0] = cfg.baseMobsPerBatch;
            keep[1] = cfg.batchIntervalSeconds;
            keep[2] = cfg.sapperFromLevel;
            keep[3] = cfg.nightPhantoms;
            keep[4] = cfg.raidMinLadders;
            keep[5] = cfg.raidStuckSeconds;
            keep[6] = cfg.raidRegroupSeconds;
            keep[7] = cfg.testPrepSeconds;
            keepD[0] = cfg.mobsPerLevel;
            keepD[1] = cfg.ladderCarrierShare;
            keepD[2] = cfg.treasureRaiderChance;
            keepD[3] = cfg.nightPhantomsPerLevel;
            cfg.baseMobsPerBatch = 12;
            cfg.mobsPerLevel = 0.0;
            cfg.batchIntervalSeconds = 600;
            cfg.sapperFromLevel = 0;
            cfg.nightPhantoms = 0;
            cfg.nightPhantomsPerLevel = 0.0;
            cfg.ladderCarrierShare = 0.0;
            cfg.raidMinLadders = 0;
            cfg.treasureRaiderChance = 0.0;
            cfg.raidStuckSeconds = 5;
            cfg.raidRegroupSeconds = 12;
            cfg.testPrepSeconds = 2;
            manager.testPool(java.util.List.of(new com.mki.nightsiege.core.MobEntry("minecraft:zombie", 1, 10),
                    new com.mki.nightsiege.core.MobEntry("minecraft:husk", 1, 4),
                    new com.mki.nightsiege.core.MobEntry("minecraft:skeleton", 1, 4)));
        });
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 600 10 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 600 10 true");
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(cx, cy, cz - 4));
        // The test player does not move: the arrows must not knock it off the wall into the field.
        game.getServer().runCommand("attribute @p minecraft:knockback_resistance base set 1");
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean ok = manager.prepareTest(level.getServer(), level, core, 8, com.mki.nightsiege.core.SiegeEvent.NONE);
            System.out.println("[Night Siege test] raid prep ok=" + ok);
        });
        context.waitTicks(60);
        closeScreen(context);
        // On a lookout above the yard (inside the base): melee monsters outside cannot go for it, the
        // camera only turns from here, so the counts are not disturbed by moving the player.
        game.getServer().runCommand("setblock %d %d %d minecraft:barrier".formatted(cx, cy + 9, cz - 4));
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(cx, cy + 10, cz - 4));
        context.waitTicks(80);
        final double[] main = new double[] {Double.NaN};
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.RaidPlan plan = manager.raid();
            com.mki.nightsiege.game.RaidPlan.Wave w = plan.lastWave();
            if (w != null) {
                main[0] = w.main();
            }
            System.out.println("[Night Siege test] raid wave " + (w == null ? "none" : w.number() + " main="
                    + com.mki.nightsiege.game.RaidPlan.compass(w.main()) + " flank=" + (w.hasFlank() ? com.mki.nightsiege.game.RaidPlan.compass(w.flank()) : "-")
                    + " gate=" + w.gate()) + " active=" + plan.active() + " states=" + plan.states());
            raidCensus("spawned", (net.minecraft.server.level.ServerLevel) player.level(), manager, cx, cz);
        });
        context.waitTicks(400);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] raid waiting states=" + manager.raid().states() + " rerouted=" + manager.raid().rerouted
                    + " returned=" + manager.raid().returned + " stormed=" + manager.raid().stormed);
            raidCensus("waiting", (net.minecraft.server.level.ServerLevel) player.level(), manager, cx, cz);
        });
        // A look at them from above the wall, towards where they came from.
        game.getServer().runCommand("effect give @p minecraft:night_vision 600 0 true");
        if (!Double.isNaN(main[0])) {
            int lx = cx + (int) Math.round(Math.cos(main[0]) * (r + 10));
            int lz = cz + 5 + (int) Math.round(Math.sin(main[0]) * (r + 10));
            game.getServer().runCommand("tp @p %d.5 %d %d.5 facing %d.5 %d %d.5".formatted(cx, cy + 10, cz - 4, lx, cy, lz));
        }
        context.runOnClient(client -> hud(client, false));
        context.waitTicks(40);
        context.takeScreenshot("raid_waiting");
        context.runOnClient(client -> hud(client, true));


        // A hole on their side: they storm it.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            double a = Double.isNaN(main[0]) ? Math.PI : main[0];
            BlockPos hole = wallTowards(level, cx, cy, cz, a, r);
            for (int dy = 0; dy <= 2; dy++) {
                for (int side = -1; side <= 1; side++) {
                    BlockPos p = holePart(hole, cx, r, side, dy);
                    com.mki.nightsiege.game.NightDamage.record(level, p, level.getBlockState(p));
                    level.destroyBlock(p, false);
                }
            }
            System.out.println("[Night Siege test] raid hole at " + (hole.getX() - cx) + "," + (hole.getZ() - cz));
        });
        context.waitTicks(300);
        onServer(context, (player, manager) -> {
            StringBuilder ways = new StringBuilder();
            for (com.mki.nightsiege.game.RaidPlan.Entry e : manager.raid().entries()) {
                ways.append(e.kind).append(e.isOpen() ? "+" : "-").append('@').append(com.mki.nightsiege.game.RaidPlan.compass(e.angle)).append(' ');
            }
            System.out.println("[Night Siege test] raid hole ways=[" + ways + "] states=" + manager.raid().states() + " stormed=" + manager.raid().stormed);
            raidCensus("hole", (net.minecraft.server.level.ServerLevel) player.level(), manager, cx, cz);
        });
        if (!Double.isNaN(main[0])) {
            int ex = cx + (int) Math.round(Math.cos(main[0]) * (r - 2));
            int ez = cz + 5 + (int) Math.round(Math.sin(main[0]) * (r - 2));
            game.getServer().runCommand("tp @p %d.5 %d %d.5 facing %d.5 %d %d.5".formatted(cx, cy + 10, cz - 4, ex, cy, ez));
        }
        context.runOnClient(client -> hud(client, false));
        context.waitTicks(30);
        context.takeScreenshot("raid_storm");
        context.runOnClient(client -> hud(client, true));


        // A new wave; the hole walled up again; a ladder on the west wall, then the gate opened.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e != null && e.entityTags().contains(com.mki.nightsiege.game.MobFactory.TAG)
                        && e instanceof net.minecraft.world.entity.Mob m && manager.raid().stateOf(m.getUUID()) == com.mki.nightsiege.game.RaidPlan.State.HUNT) {
                    m.discard();   // the ones inside are done
                }
            }
            double a = Double.isNaN(main[0]) ? Math.PI : main[0];
            BlockPos hole = wallTowards(level, cx, cy, cz, a, r);
            for (int dy = 0; dy <= 2; dy++) {
                for (int side = -1; side <= 1; side++) {
                    BlockPos p = holePart(hole, cx, r, side, dy);
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
            net.minecraft.world.level.block.state.BlockState ladder = net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.WEST);
            for (int dy = 0; dy <= 5; dy++) {
                com.mki.nightsiege.game.TempBlocks.place(level, new BlockPos(cx - r - 1, cy + dy, cz - 6), ladder);
            }
        });
        context.waitTicks(120);
        onServer(context, (player, manager) -> {
            StringBuilder ways = new StringBuilder();
            for (com.mki.nightsiege.game.RaidPlan.Entry e : manager.raid().entries()) {
                ways.append(e.kind).append(e.isOpen() ? "+" : "-").append('@').append(com.mki.nightsiege.game.RaidPlan.compass(e.angle)).append(' ');
            }
            System.out.println("[Night Siege test] raid ladder ways=[" + ways + "] states=" + manager.raid().states() + " stormed=" + manager.raid().stormed);
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (com.mki.nightsiege.game.RaidPlan.Entry e : manager.raid().entries()) {
                if (e.kind.equals("hole")) {
                    System.out.println("[Night Siege test] raid hole entry at=" + (e.at.getX() - cx) + "," + (e.at.getY() - cy) + "," + (e.at.getZ() - cz)
                            + " front=" + (e.front.getX() - cx) + "," + (e.front.getY() - cy) + "," + (e.front.getZ() - cz)
                            + " open=" + e.isOpen() + " walk=" + manager.defense().base().walkInto(level, e.front, e.at, 12, 3000));
                }
            }
        });
        context.waitTicks(400);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] raid ladder after states=" + manager.raid().states() + " stormed=" + manager.raid().stormed
                    + " rerouted=" + manager.raid().rerouted + " returned=" + manager.raid().returned);
            raidCensus("ladder", (net.minecraft.server.level.ServerLevel) player.level(), manager, cx, cz);
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (BlockPos p : new BlockPos[] {new BlockPos(cx, cy, cz - r), new BlockPos(cx, cy + 1, cz - r)}) {
                net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
                if (st.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                    level.setBlock(p, st.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true), 10);
                }
            }
            // One stuck in a pit far from everybody: it comes back from the spawn ground.
            BlockPos pit = new BlockPos(cx + 2, cy - 3, cz + 40);
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlock(pit.above(dy), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            net.minecraft.world.entity.Mob z = com.mki.nightsiege.game.MobFactory.spawnAt(level, pit, "minecraft:zombie", 8, manager.config(),
                    new java.util.Random(3), com.mki.nightsiege.core.SiegeEvent.NONE, false);
            if (z != null) {
                z.addTag("nightsiege_test_pit");
                manager.foesHost().addMob(z);
            }
            System.out.println("[Night Siege test] raid pit zombie=" + (z != null) + " enlisted=" + (z != null && manager.raid().enlisted(z.getUUID())));
        });
        context.waitTicks(400);
        onServer(context, (player, manager) -> {
            StringBuilder ways = new StringBuilder();
            for (com.mki.nightsiege.game.RaidPlan.Entry e : manager.raid().entries()) {
                ways.append(e.kind).append(e.isOpen() ? "+" : "-").append('@').append(com.mki.nightsiege.game.RaidPlan.compass(e.angle)).append(' ');
            }
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            String pit = "gone";
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e != null && e.entityTags().contains("nightsiege_test_pit")) {
                    pit = String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", e.getX() - cx, e.getY() - cy, e.getZ() - cz);
                }
            }
            System.out.println("[Night Siege test] raid gate ways=[" + ways + "] states=" + manager.raid().states() + " stormed=" + manager.raid().stormed
                    + " returned=" + manager.raid().returned + " pit=" + pit);
            raidCensus("gate", level, manager, cx, cz);
            manager.abort(level.getServer());
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            cfg.baseMobsPerBatch = keep[0];
            cfg.batchIntervalSeconds = keep[1];
            cfg.sapperFromLevel = keep[2];
            cfg.nightPhantoms = keep[3];
            cfg.raidMinLadders = keep[4];
            cfg.raidStuckSeconds = keep[5];
            cfg.raidRegroupSeconds = keep[6];
            cfg.testPrepSeconds = keep[7];
            cfg.mobsPerLevel = keepD[0];
            cfg.ladderCarrierShare = keepD[1];
            cfg.treasureRaiderChance = keepD[2];
            cfg.nightPhantomsPerLevel = keepD[3];
            manager.testPool(null);
        });
        context.waitTicks(20);
        game.getServer().runCommand("attribute @p minecraft:knockback_resistance base set 0");
        game.getServer().runCommand("forceload remove %d %d %d %d".formatted(cx - 80, cz - 80, cx + 80, cz + 80));
    }

    /** The castle wall (|x| or |z| = r around cx/cz) where a ray from the core (cx, cz + 5) towards an angle meets it, at foot height, not at a corner. */
    private static BlockPos wallTowards(net.minecraft.server.level.ServerLevel level, int cx, int cy, int cz, double a, int r) {
        double ox = cx + 0.5;
        double oz = cz + 5.5;
        for (double d = 1; d <= 3 * r; d += 0.25) {
            int x = (int) Math.floor(ox + Math.cos(a) * d);
            int z = (int) Math.floor(oz + Math.sin(a) * d);
            if (Math.abs(x - cx) >= r || Math.abs(z - cz) >= r) {
                x = Math.max(cx - r, Math.min(cx + r, x));
                z = Math.max(cz - r, Math.min(cz + r, z));
                if (Math.abs(x - cx) == r) {
                    z = Math.max(cz - r + 3, Math.min(cz + r - 3, z));
                } else {
                    x = Math.max(cx - r + 3, Math.min(cx + r - 3, x));
                }
                return new BlockPos(x, cy, z);
            }
        }
        return new BlockPos(cx - r, cy, cz);
    }

    /** The three columns of a hole across a wall block: along the wall it stands in. */
    private static BlockPos holePart(BlockPos wall, int cx, int r, int side, int dy) {
        return Math.abs(wall.getX() - cx) == r ? wall.offset(0, dy, side) : wall.offset(side, dy, 0);
    }

    /** Where the night's monsters are: inside, at the wall, on the ground in front, far off; and what they aim at. */
    private static void raidCensus(String when, net.minecraft.server.level.ServerLevel level, com.mki.nightsiege.game.SiegeManager manager,
                                   int cx, int cz) {
        com.mki.nightsiege.game.BaseScan scan = manager.defense() == null ? null : manager.defense().base();
        int total = 0;
        int inside = 0;
        int near = 0;
        int field = 0;
        int far = 0;
        int targets = 0;
        int enlisted = 0;
        double maxTarget = 0;
        double maxDist = 0;
        int details = 0;
        int assaultDetails = 0;
        net.minecraft.server.level.ServerPlayer p0 = level.getServer().getPlayerList().getPlayers().get(0);
        System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] raid player %s at %.1f,%.1f,%.1f hp=%.1f mode=%s drone=%s",
                when, p0.getX() - cx, p0.getY(), p0.getZ() - cz, p0.getHealth(), p0.isSpectator() ? "spectator" : p0.isCreative() ? "creative" : "survival",
                com.mki.nightsiege.game.Drones.isDrone(p0)));
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e == null || !e.entityTags().contains(com.mki.nightsiege.game.MobFactory.TAG) || !(e instanceof net.minecraft.world.entity.Mob m)
                    || !m.isAlive()) {
                continue;
            }
            total++;
            if (manager.raid().enlisted(m.getUUID())) {
                enlisted++;
            }
            double d = Math.sqrt((m.getX() - cx) * (m.getX() - cx) + (m.getZ() - cz) * (m.getZ() - cz));
            maxDist = Math.max(maxDist, d);
            if (scan != null && scan.contains(m.getBlockX(), m.getBlockZ())) {
                inside++;
            } else if (d <= 26) {
                near++;
            } else if (d <= 48) {
                field++;
            } else {
                far++;
            }
            if (m.getTarget() != null) {
                targets++;
                maxTarget = Math.max(maxTarget, m.distanceTo(m.getTarget()));
            }
            if (manager.raid().stateOf(m.getUUID()) == com.mki.nightsiege.game.RaidPlan.State.ASSAULT && assaultDetails < 3) {
                assaultDetails++;
                net.minecraft.world.entity.LivingEntity t = m.getTarget();
                net.minecraft.world.level.pathfinder.Path path = m.getNavigation().getPath();
                System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] raid assault %s %s at %.1f,%.1f,%.1f plan=%s nav=%s target=%s",
                        when, net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath(), m.getX() - cx, m.getY(), m.getZ() - cz,
                        manager.raid().describe(m.getUUID()),
                        path == null ? "none" : (path.getTarget().getX() - cx) + "," + (path.getTarget().getZ() - cz) + (path.canReach() ? " reach" : " partial")
                                + (m.getNavigation().isDone() ? " done" : ""),
                        t == null ? "-" : (t == level.getServer().getPlayerList().getPlayers().get(0) ? "Player0" : t.getStringUUID().substring(0, 8))
                                + String.format(java.util.Locale.ROOT, "@%.0f,%.0f,%.0f", t.getX() - cx, t.getY(), t.getZ() - cz)));
            }
            if (d > 40 && details < 4) {
                details++;
                net.minecraft.world.entity.LivingEntity t = m.getTarget();
                System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] raid far %s %s at %.0f,%.0f plan=%s target=%s",
                        when, net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath(), m.getX() - cx, m.getZ() - cz,
                        manager.raid().describe(m.getUUID()),
                        t == null ? "-" : (t == p0 ? "Player0" : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t.getType()).getPath()
                                + ":" + t.getName().getString())
                                + String.format(java.util.Locale.ROOT, "@%.0f,%.0f", t.getX() - cx, t.getZ() - cz)));
            }
        }
        System.out.println(String.format(java.util.Locale.ROOT,
                "[Night Siege test] raid census %s total=%d enlisted=%d inside=%d atWall=%d field=%d far=%d maxDist=%.1f targets=%d maxTarget=%.1f",
                when, total, enlisted, inside, near, field, far, maxDist, targets, maxTarget));
    }

    /**
     * The new attackers of update 1 (27 October 2026) in a walled yard in a test night at level
     * 10: a line-up by night and in the blood moon look, the revenant (rises, burns, its grave
     * struck), the tunneler (comes up inside), the standard bearer's aura, the grappler pulling the
     * defender off the wall, the iron knight against arrows, the treasure raider (caught and
     * escaped), the camel troop, the limits per wave and the friendly fire. Logs "foes ..." lines.
     */
    private static void foeScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 160;
        int cz = pos.getZ() + 160;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 48, cz - 48, cx + 48, cz + 48));
        context.waitTicks(40);
        final int[] ground = new int[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.getChunk(cx >> 4, cz >> 4);
            ground[0] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        });
        context.waitTicks(10);
        int cy = ground[0];
        int r = 10;
        int out = 44;
        for (int part = -out; part <= out; part += 16) {
            int z1 = cz + part;
            int z2 = Math.min(cz + out, z1 + 15);
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx - out, cy - 1, z1, cx + out, cy - 1, z2));
            game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx - out, cy, z1, cx + out, cy + 10, z2));
        }
        String wall = "fill %d %d %d %d %d %d minecraft:stone_bricks";
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx + r, cy + 4, cz - r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz + r, cx + r, cy + 4, cz + r));
        game.getServer().runCommand(wall.formatted(cx - r, cy, cz - r, cx - r, cy + 4, cz + r));
        game.getServer().runCommand(wall.formatted(cx + r, cy, cz - r, cx + r, cy + 4, cz + r));
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand("effect give @p minecraft:regeneration 600 10 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 600 10 true");
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(cx, cy, cz));
        context.waitTicks(20);
        final int[] keep = new int[4];
        final double[] keepD = new double[2];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            keep[0] = cfg.baseMobsPerBatch;
            keep[1] = cfg.batchIntervalSeconds;
            keep[2] = cfg.sapperFromLevel;
            keepD[0] = cfg.mobsPerLevel;
            keepD[1] = cfg.treasureRaiderChance;
            cfg.baseMobsPerBatch = 1;
            cfg.mobsPerLevel = 0.0;
            cfg.batchIntervalSeconds = 600;
            cfg.sapperFromLevel = 0;
            cfg.treasureRaiderChance = 0.0;
            cfg.testPrepSeconds = 2;
            boolean ok = manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 10,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            boolean eggs = net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("nightsiege", "foe_revenant"));
            System.out.println("[Night Siege test] foes prep ok=" + ok + " eggs=" + eggs + " list="
                    + cfg.mobs.stream().filter(e -> e.id.startsWith("nightsiege:") || e.id.contains("camel")).map(e -> e.id).toList());
        });
        context.waitTicks(60);
        closeScreen(context);
        context.waitTicks(20);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] foes active=" + manager.isActive()
                + " defending=" + manager.isDefending(player)));

        // The line-up, outside the north wall, looked at from above the wall.
        final java.util.List<java.util.UUID> lineup = new java.util.ArrayList<>();
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            String[] ids = {"nightsiege:revenant", "nightsiege:tunneler", "nightsiege:standard_bearer", "nightsiege:grappler",
                    "nightsiege:iron_knight", "nightsiege:treasure_raider", com.mki.nightsiege.core.SiegeConfig.CAMEL_TROOP};
            for (int i = 0; i < ids.length; i++) {
                net.minecraft.world.entity.Mob mob = com.mki.nightsiege.game.MobFactory.spawnAt(level,
                        new BlockPos(cx - 9 + i * 3, cy, cz - r - 5), ids[i], 10, manager.config(), new java.util.Random(1),
                        com.mki.nightsiege.core.SiegeEvent.NONE, false);
                if (mob == null) {
                    System.out.println("[Night Siege test] foes lineup FAILED " + ids[i]);
                    continue;
                }
                mob.setNoAi(true);
                mob.setYRot(0f);
                mob.setYHeadRot(0f);
                mob.setYBodyRot(0f);
                lineup.add(mob.getUUID());
                StringBuilder gear = new StringBuilder();
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    net.minecraft.world.item.ItemStack stack = mob.getItemBySlot(slot);
                    if (!stack.isEmpty()) {
                        gear.append(slot.getName()).append('=').append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()).append(' ');
                    }
                }
                StringBuilder riders = new StringBuilder();
                for (net.minecraft.world.entity.Entity rider : mob.getPassengers()) {
                    riders.append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(rider.getType()).getPath());
                    if (rider instanceof net.minecraft.world.entity.Mob rm && !rm.getMainHandItem().isEmpty()) {
                        riders.append('(').append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(rm.getMainHandItem().getItem()).getPath()).append(')');
                    }
                    riders.append(' ');
                    lineup.add(rider.getUUID());
                    if (rider instanceof net.minecraft.world.entity.Mob rm) {
                        rm.setNoAi(true);
                    }
                }
                System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] foes lineup %s type=%s name=%s hp=%.0f speed=%.3f gear=[%s] riders=[%s]",
                        ids[i], net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath(),
                        mob.getCustomName() == null ? "-" : mob.getCustomName().getString(), mob.getMaxHealth(),
                        mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED), gear, riders));
            }
        });
        context.waitTicks(10);
        game.getServer().runCommand("setblock %d %d %d minecraft:barrier".formatted(cx, cy + 6, cz - r + 2));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 facing %d.5 %d %d.5".formatted(cx, cy + 7, cz - r + 2, cx, cy + 1, cz - r - 5));
        context.runOnClient(client -> hud(client, false));
        context.waitTicks(40);
        context.takeScreenshot("foes_lineup_night");
        context.runOnClient(client -> EventAtmosphere.setClient(EventAtmosphere.BLOOD_MOON, 1));
        context.waitTicks(30);
        context.takeScreenshot("foes_lineup_blood_moon");
        context.runOnClient(client -> EventAtmosphere.setClient("", 1));
        game.getServer().runCommand("time set 6000");
        context.waitTicks(30);
        context.takeScreenshot("foes_lineup_day");
        game.getServer().runCommand("time set 18000");
        context.runOnClient(client -> hud(client, true));
        onServer(context, (player, manager) -> {
            for (java.util.UUID id : lineup) {
                net.minecraft.world.entity.Entity e = ((net.minecraft.server.level.ServerLevel) player.level()).getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
        });
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(cx, cy, cz));
        context.waitTicks(10);

        // The revenant: struck down (rises), burnt (stays down), its grave struck (stays down).
        final java.util.UUID[] holder = new java.util.UUID[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob rev = com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level, "nightsiege:revenant", new BlockPos(cx + 4, cy, cz));
            rev.setNoAi(true);
            rev.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
            System.out.println("[Night Siege test] foes revenant down graves=" + manager.foes().graveCount());
        });
        game.getServer().runCommand("tp @p %d.5 %d %d.5 facing %d.5 %d %d.5".formatted(cx + 1, cy + 2, cz - 3, cx + 4, cy, cz));
        context.runOnClient(client -> hud(client, false));
        context.waitTicks(40);
        context.takeScreenshot("foes_revenant_grave");
        context.runOnClient(client -> hud(client, true));
        context.waitTicks(100);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            System.out.println("[Night Siege test] foes revenant risen=" + manager.foes().risenTonight + " graves=" + manager.foes().graveCount());
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e != null && e.entityTags().contains(com.mki.nightsiege.game.Foes.TAG_RISEN) && e instanceof net.minecraft.world.entity.LivingEntity le) {
                    System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] foes revenant risen hp=%.1f/%.1f", le.getHealth(), le.getMaxHealth()));
                    le.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
                }
            }
            net.minecraft.world.entity.Mob burnt = com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level, "nightsiege:revenant", new BlockPos(cx - 4, cy, cz));
            burnt.setNoAi(true);
            burnt.igniteForSeconds(5f);
            burnt.hurtServer(level, level.damageSources().onFire(), 1000f);
            net.minecraft.world.entity.Mob struck = com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level, "nightsiege:revenant", new BlockPos(cx, cy, cz + 4));
            struck.setNoAi(true);
            struck.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
            System.out.println("[Night Siege test] foes revenant after second death graves=" + manager.foes().graveCount() + " (risen dies for good, burnt leaves none)");
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e != null && e.entityTags().contains(com.mki.nightsiege.game.Foes.TAG_GRAVE) && e instanceof net.minecraft.world.entity.LivingEntity box) {
                    box.hurtServer(level, level.damageSources().playerAttack(player), 4f);
                }
            }
        });
        context.waitTicks(140);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] foes revenant total risen=" + manager.foes().risenTonight
                + " broken=" + manager.foes().gravesBroken + " graves=" + manager.foes().graveCount()));

        // The tunneler: from 34 blocks south it digs in and comes up inside the walls.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob t = com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level, "nightsiege:tunneler", new BlockPos(cx, cy, cz + 34));
            holder[0] = t == null ? null : t.getUUID();
        });
        for (int i = 0; i < 30; i++) {
            final int step = i;
            context.waitTicks(20);
            onServer(context, (player, manager) -> {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.entity.Entity t = holder[0] == null ? null : level.getEntity(holder[0]);
                if (t != null && step % 3 == 2) {
                    boolean inside = Math.abs(t.getX() - cx - 0.5) < r && Math.abs(t.getZ() - cz - 0.5) < r;
                    System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] foes tunneler t=%ds pos=%.1f %.1f %.1f invisible=%s emerged=%s inside=%s",
                            step + 1, t.getX() - cx, t.getY() - cy, t.getZ() - cz, t.isInvisible(),
                            com.mki.nightsiege.game.Foes.emerged(t.getUUID()), inside));
                }
            });
            if (i == 14) {
                context.takeScreenshot("foes_tunneler_warning");
            }
        }
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Entity t = holder[0] == null ? null : level.getEntity(holder[0]);
            System.out.println("[Night Siege test] foes tunneler done=" + manager.foes().tunnelsDone + " alive=" + (t != null && t.isAlive()));
            if (t != null) {
                t.discard();
            }
        });

        // The standard bearer: a zombie in its aura is faster and hits harder.
        final java.util.UUID[] pair = new java.util.UUID[2];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob b = com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level, "nightsiege:standard_bearer", new BlockPos(cx + 20, cy, cz));
            net.minecraft.world.entity.Mob z = com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level, "minecraft:zombie", new BlockPos(cx + 22, cy, cz));
            b.setNoAi(true);
            z.setNoAi(true);
            pair[0] = b.getUUID();
            pair[1] = z.getUUID();
        });
        context.waitTicks(50);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Entity z = level.getEntity(pair[1]);
            boolean fast = z instanceof net.minecraft.world.entity.LivingEntity le && le.hasEffect(net.minecraft.world.effect.MobEffects.SPEED);
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] foes banner speed=%s factor=%.2f damageFactor=%.2f",
                    fast, com.mki.nightsiege.game.Foes.bannerFactor(z), manager.monsterDamageFactor(z)));
            for (java.util.UUID id : pair) {
                net.minecraft.world.entity.Entity e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
        });

        // The grappler: the defender stands on the north wall; it pulls him down outside.
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(cx, cy + 5, cz - r));
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob g = com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level, "nightsiege:grappler", new BlockPos(cx, cy, cz - r - 7));
            holder[0] = g == null ? null : g.getUUID();
            System.out.println("[Night Siege test] foes grappler spawned=" + (g != null) + " hand="
                    + (g == null ? "-" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(g.getMainHandItem().getItem()).getPath()));
        });
        final double[] lowest = {999};
        for (int i = 0; i < 20; i++) {
            final int step = i;
            context.waitTicks(20);
            onServer(context, (player, manager) -> {
                lowest[0] = Math.min(lowest[0], player.getY() - cy);
                if (step % 2 == 1) {
                    net.minecraft.world.entity.Entity g = holder[0] == null ? null : ((net.minecraft.server.level.ServerLevel) player.level()).getEntity(holder[0]);
                    System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] foes grappler t=%ds player=%.1f %.1f %.1f grappler=%s hooks=%d",
                            step + 1, player.getX() - cx, player.getY() - cy, player.getZ() - cz,
                            g == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f sees=%s", g.getX() - cx, g.getY() - cy, g.getZ() - cz,
                                    g instanceof net.minecraft.world.entity.LivingEntity gl && gl.hasLineOfSight(player)),
                            manager.foes().hooksLanded));
                }
            });
            if (i == 6) {
                context.takeScreenshot("foes_grappler");
            }
        }
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            System.out.println("[Night Siege test] foes grappler hooks=" + manager.foes().hooksLanded + " falls_saved=" + manager.foes().fallsSaved + " lowest=" + lowest[0]
                    + " outside=" + (player.getZ() < cz - r) + " health=" + player.getHealth());
            net.minecraft.world.entity.Entity g = holder[0] == null ? null : level.getEntity(holder[0]);
            if (g != null) {
                g.discard();
            }
        });
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(cx, cy, cz));
        context.waitTicks(10);

        // The iron knight against a shot, beside a plain zombie.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Mob k = com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level, "nightsiege:iron_knight", new BlockPos(cx + 4, cy, cz + 4));
            net.minecraft.world.entity.Mob z = com.mki.nightsiege.game.SiegeCommands.spawnFoeAt(manager, level, "minecraft:zombie", new BlockPos(cx - 4, cy, cz + 4));
            k.setNoAi(true);
            z.setNoAi(true);
            float kh = k.getHealth();
            float zh = z.getHealth();
            k.hurtServer(level, level.damageSources().mobProjectile(player, player), 6f);
            z.hurtServer(level, level.damageSources().mobProjectile(player, player), 6f);
            float kShot = kh - k.getHealth();
            float zShot = zh - z.getHealth();
            k.invulnerableTime = 0;
            float kh2 = k.getHealth();
            k.hurtServer(level, level.damageSources().playerAttack(player), 6f);
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] foes knight shot=%.2f zombie shot=%.2f knight melee=%.2f speed=%.3f",
                    kShot, zShot, kh2 - k.getHealth(), k.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
            k.discard();
            z.discard();
        });
        context.waitTicks(10);

        // The treasure raider: caught once, escaped once.
        onServer(context, (player, manager) -> {
            manager.config().treasureRaiderSeconds = 20;
            boolean ok = manager.foes().treasureNow(manager.foesHost());
            System.out.println("[Night Siege test] foes treasure spawned=" + ok + " bar=" + manager.bossBarText());
        });
        context.waitTicks(60);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.Entity t = manager.foes().treasure() == null ? null : level.getEntity(manager.foes().treasure());
            if (t instanceof net.minecraft.world.entity.LivingEntity raider) {
                System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] foes treasure glow=%s nameShown=%s dist=%.1f target=%s",
                        raider.isCurrentlyGlowing(), raider.isCustomNameVisible(), Math.sqrt(raider.distanceToSqr(cx, cy, cz)),
                        raider instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null));
                player.teleportTo(raider.getX(), raider.getY() + 3, raider.getZ() - 4);
            }
        });
        context.waitTicks(20);
        context.takeScreenshot("foes_treasure");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            int crates = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(com.mki.nightsiege.game.CrateService.CRATE)) {
                    crates += player.getInventory().getItem(i).getCount();
                }
            }
            keep[3] = crates;
            net.minecraft.world.entity.Entity t = manager.foes().treasure() == null ? null : level.getEntity(manager.foes().treasure());
            if (t instanceof net.minecraft.world.entity.LivingEntity raider) {
                raider.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
            }
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            int crates = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(com.mki.nightsiege.game.CrateService.CRATE)) {
                    crates += player.getInventory().getItem(i).getCount();
                }
            }
            System.out.println("[Night Siege test] foes treasure caught outcome=" + manager.foes().treasureOutcome + " crates "
                    + keep[3] + "->" + crates + " xp=" + player.totalExperience);
            manager.foes().treasureNow(manager.foesHost());
            manager.foes().treasureTimeUp(player.level().getGameTime() + 20);
        });
        context.waitTicks(40);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] foes treasure second outcome=" + manager.foes().treasureOutcome
                + " gone=" + (manager.foes().treasure() == null)));
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(cx, cy, cz));
        context.waitTicks(10);

        // The limits per wave, the camel troop in a wave and the friendly fire.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            java.util.Map<String, Integer> wave = new java.util.HashMap<>();
            int tunnelers = 0;
            for (int i = 0; i < 6; i++) {
                String id = manager.foes().limit("nightsiege:tunneler", wave, 10, manager.config(), () -> "nightsiege:tunneler");
                if (id.equals("nightsiege:tunneler")) {
                    tunnelers++;
                }
            }
            java.util.Map<String, Integer> wave2 = new java.util.HashMap<>();
            int bearers10 = 0;
            int bearers15 = 0;
            for (int i = 0; i < 6; i++) {
                if (manager.foes().limit("nightsiege:standard_bearer", wave2, 10, manager.config(), () -> "nightsiege:standard_bearer").equals("nightsiege:standard_bearer")) {
                    bearers10++;
                }
            }
            java.util.Map<String, Integer> wave3 = new java.util.HashMap<>();
            for (int i = 0; i < 6; i++) {
                if (manager.foes().limit("nightsiege:standard_bearer", wave3, 15, manager.config(), () -> "nightsiege:standard_bearer").equals("nightsiege:standard_bearer")) {
                    bearers15++;
                }
            }
            System.out.println("[Night Siege test] foes limits tunnelers=" + tunnelers + "/6 bearers10=" + bearers10 + " bearers15=" + bearers15);
            net.fabricmc.fabric.api.entity.FakePlayer friend = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
            manager.testJoin(friend.getUUID(), true);
            boolean blocked = !com.mki.nightsiege.game.FriendlyFire.allow(player, level.damageSources().playerAttack(friend));
            boolean arrowBlocked = !com.mki.nightsiege.game.FriendlyFire.allow(player, level.damageSources().mobProjectile(friend, friend));
            net.minecraft.world.entity.monster.zombie.Zombie z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            boolean monsterHits = com.mki.nightsiege.game.FriendlyFire.allow(player, level.damageSources().mobAttack(z));
            manager.testJoin(friend.getUUID(), false);
            boolean strangerHits = com.mki.nightsiege.game.FriendlyFire.allow(player, level.damageSources().playerAttack(friend));
            System.out.println("[Night Siege test] foes friendlyfire blow=" + blocked + " arrow=" + arrowBlocked
                    + " monster_hits=" + monsterHits + " stranger_hits=" + strangerHits);
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            manager.abort(player.level().getServer());
            com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
            cfg.baseMobsPerBatch = keep[0];
            cfg.batchIntervalSeconds = keep[1];
            cfg.sapperFromLevel = keep[2];
            cfg.mobsPerLevel = keepD[0];
            cfg.treasureRaiderChance = keepD[1];
            System.out.println("[Night Siege test] foes after night ff=" + com.mki.nightsiege.game.FriendlyFire.allow(player,
                    ((net.minecraft.server.level.ServerLevel) player.level()).damageSources().playerAttack(
                            net.fabricmc.fabric.api.entity.FakePlayer.get((net.minecraft.server.level.ServerLevel) player.level())))
                    + " known=" + com.mki.nightsiege.game.Foes.known());
        });
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);
    }

    /** Something to do on the integrated server with the test player and the siege manager. */
    interface OnServer {
        void run(net.minecraft.server.level.ServerPlayer player, com.mki.nightsiege.game.SiegeManager manager);
    }

    /** Shows or hides the HUD (for clean photos). */
    private static void hud(net.minecraft.client.Minecraft client, boolean shown) {
        if (client.gui.hud.isHidden() == shown) {
            client.gui.hud.toggle();
        }
    }

    static void onServer(ClientGameTestContext context, OnServer action) {
        context.runOnClient(client -> {
            net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
            server.execute(() -> {
                if (!server.getPlayerList().getPlayers().isEmpty()) {
                    action.run(server.getPlayerList().getPlayers().get(0), com.mki.nightsiege.game.SiegeManager.get());
                }
            });
        });
    }

    /**
     * A learning order written the old way (trunk, way, then the subclass) in the order of today's
     * trees (Simon, 29 October 2026: the subclass node first, its level 1 free): the first step on a
     * subclass node moves to the front, every later one stays where it was (its level 2 and up).
     */
    private static String[] subclassFirst(String... ids) {
        java.util.List<String> out = new java.util.ArrayList<>(java.util.Arrays.asList(ids));
        for (int i = 0; i < out.size(); i++) {
            if (com.mki.nightsiege.core.SkillTree.isSubclassNode(out.get(i))) {
                out.add(0, out.remove(i));
                break;
            }
        }
        return out.toArray(new String[0]);
    }

    /** Picks a class and some nodes, then opens the window on that class's tab. */
    private static void openTree(ClientGameTestContext context, String clazz, String... nodes) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("VIEWS");
                field.setAccessible(true);
                ((java.util.Map<?, ?>) field.get(null)).remove(clazz);   // start at the overview
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not reset the view: " + e);
            }
        });
        onServer(context, (player, manager) -> {
            manager.onCoreAction(player, "dev:reset");
            manager.onCoreAction(player, "class:" + clazz);
            manager.onCoreAction(player, "dev:600");
            for (String id : subclassFirst(nodes)) {
                manager.onBuySkill(player, id);
            }
            manager.onCoreAction(player, "open");
        });
        context.waitTicks(20);
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field tab = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                tab.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object value = Enum.valueOf((Class) tab.getType(), clazz);
                tab.set(null, value);
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not set the tab: " + e);
            }
        });
        onServer(context, (player, manager) -> manager.onCoreAction(player, "open"));
        context.waitTicks(40);
    }

    /** One skill tree per subclass (Simon, 29 October 2026): every class, every subclass tab. */
    private static void subTreeShots(ClientGameTestContext context) {
        String[][] classes = {
                {"WARRIOR", "blademaster", "berserker", "axe", "paladin"},
                {"GUARDIAN", "shieldfighter", "bulwark", "medic", "warden"},
                {"ARCHER", "swift", "heavy", "bolter", "trapper"},
                {"ENGINEER", "gunner", "electrician", "demolition", "alchemist"},
                {"SUMMONER", "tamer", "golemsmith", "necromancer", "soulcatcher"}};
        for (String[] c : classes) {
            openTree(context, c[0]);
            for (int i = 1; i < c.length; i++) {
                String sub = c[i];
                pickSub(context, c[0], sub);
                context.takeScreenshot("subtree_" + sub);
                // Klare Stufen (Simon, 29 October 2026): the middle tiers and the top, readable.
                zoomTo(context, c[0] + "/" + sub, 0.0, -5.5 * 50, 1.0);
                context.takeScreenshot("subtree_" + sub + "_mid");
                zoomTo(context, c[0] + "/" + sub, 0.0, -9.0 * 50, 1.0);
                context.takeScreenshot("subtree_" + sub + "_top");
                zoomTo(context, c[0] + "/" + sub, 0.0, 0.0, 0.0);
            }
        }
        // The berserker with a way learned: lit links, the view on the next tier, a talent chosen.
        openTree(context, "WARRIOR", "warrior_drill", "w_endurance", "champion_bane", "bloodlust", "bloodlust",
                "blood_drinker", "w_bloodblade");
        pickSub(context, "WARRIOR", "berserker");
        context.takeScreenshot("subtree_berserker_learned");
        selectNode(context, "w_frenzy");
        context.takeScreenshot("subtree_berserker_choice");
        selectNode(context, null);
        // The whole tree (the button in the corner).
        zoomTo(context, "WARRIOR/berserker", 0.0, -5.0 * 50, 0.01);
        context.takeScreenshot("subtree_berserker_whole");
        zoomTo(context, "WARRIOR/berserker", 0.0, 0.0, 0.0);
        // The subclass first (Simon, 29 October 2026): picked with the class at the armour stands, its node
        // is the root of the tree with level 1 for free; the trunk opens at once, nothing was paid.
        openTree(context, "ARCHER");
        onServer(context, (player, manager) -> {
            manager.onCoreAction(player, "dev:reset");
            manager.onCoreAction(player, "class:ARCHER:swift_draw");
            manager.onCoreAction(player, "dev:600");
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            System.out.println("[Night Siege test] subclass first: swift_draw=" + own.levelOf("swift_draw")
                    + " points=" + own.points() + " history=" + own.history
                    + " trunk=" + manager.skillTree().blockedReason("archer_focus", own)
                    + " level2=" + manager.skillTree().blockedReason("swift_draw", own)
                    + " other way=" + manager.skillTree().blockedReason("heavy_bolts", own));
            manager.onCoreAction(player, "open");
        });
        context.waitTicks(20);
        pickSub(context, "ARCHER", "swift");
        context.takeScreenshot("subtree_swift_gift");
        selectNode(context, "swift_draw");
        context.takeScreenshot("subtree_swift_gift_panel");
        selectNode(context, "double_shot");
        context.takeScreenshot("subtree_swift_spine");
        selectNode(context, null);
        closeScreen(context);
    }

    /** Shows one subclass's tree in the open class tab. */
    private static void pickSub(ClientGameTestContext context, String clazz, String sub) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("VIEW_SUB");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> views = (java.util.Map<String, String>) field.get(null);
                views.put(clazz, sub);
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not pick the subclass: " + e);
            }
        });
        onServer(context, (player, manager) -> manager.onCoreAction(player, "open"));
        context.waitTicks(15);
    }

    /**
     * The skill tree backgrounds v2 (29 October 2026): all 20 behind the warrior's tree, two of them
     * also dragged to both sides (parallax) and a second later (the particles move).
     */
    private static void treeSceneShots(ClientGameTestContext context) {
        String[] subs = {"blademaster", "berserker", "axe", "paladin", "swift", "heavy", "bolter", "trapper",
                "shieldfighter", "bulwark", "medic", "warden", "gunner", "electrician", "demolition", "alchemist",
                "tamer", "golemsmith", "necromancer", "soulcatcher"};
        openTree(context, "WARRIOR", "warrior_drill", "bloodlust");
        for (String sub : subs) {
            context.runOnClient(client -> com.mki.nightsiege.client.CoreScreen.testSceneSub = sub);
            context.waitTicks(4);
            context.takeScreenshot("treebg_" + sub);
        }
        for (String sub : new String[] {"berserker", "soulcatcher"}) {
            context.runOnClient(client -> com.mki.nightsiege.client.CoreScreen.testSceneSub = sub);
            zoomTo(context, "WARRIOR", -400.0, 0.0, 0.5);
            context.takeScreenshot("treebg_" + sub + "_left");
            zoomTo(context, "WARRIOR", 400.0, 0.0, 0.5);
            context.takeScreenshot("treebg_" + sub + "_right");
            context.waitTicks(20);
            context.takeScreenshot("treebg_" + sub + "_right_later");
        }
        zoomTo(context, "WARRIOR", 0.0, 0.0, 0.0);
        context.runOnClient(client -> com.mki.nightsiege.client.CoreScreen.testSceneSub = null);
        closeScreen(context);
    }

    /** Sets where the window looks (as the mouse wheel would) and redraws it. */
    /** Close-ups of a class tree so the icons can be seen: both ways, upper and lower half. */
    private static void zoomShots(ClientGameTestContext context, String clazz, String name) {
        double[][] spots = {{-200, -60}, {-200, 110}, {200, -60}, {200, 110}};
        String[] names = {"left_top", "left_bottom", "right_top", "right_bottom"};
        for (int i = 0; i < spots.length; i++) {
            zoomTo(context, clazz, spots[i][0], spots[i][1], 0.6);
            context.takeScreenshot("tree_" + name + "_" + names[i]);
        }
        zoomTo(context, clazz, 0.0, 0.0, 0.0);
    }

    private static void zoomTo(ClientGameTestContext context, String clazz, double x, double y, double zoom) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("VIEWS");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, double[]> views = (java.util.Map<String, double[]>) field.get(null);
                if (zoom <= 0) {
                    views.remove(clazz);   // back to the whole tree
                } else {
                    views.put(clazz, new double[]{x, y, zoom});
                }
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not zoom: " + e);
            }
        });
        onServer(context, (player, manager) -> manager.onCoreAction(player, "open"));
        context.waitTicks(30);
    }

    /** Switches the core window to a tab (by its enum name) and opens it again. */
    private static void showTab(ClientGameTestContext context, String tab) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                field.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object value = Enum.valueOf((Class) field.getType(), tab);
                field.set(null, value);
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not set the tab: " + e);
            }
        });
        onServer(context, (player, manager) -> manager.onCoreAction(player, "open"));
        context.waitTicks(30);
    }

    /** Opens (or closes) the "change class once" question in the open window. */
    private static void askClassReset(ClientGameTestContext context, boolean open) {
        context.runOnClient(client -> {
            Object screen = client.gui.screen();
            if (!(screen instanceof com.mki.nightsiege.client.CoreScreen)) {
                return;
            }
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("confirmingReset");
                field.setAccessible(true);
                field.set(screen, open);
                java.lang.reflect.Method rebuild = net.minecraft.client.gui.screens.Screen.class.getDeclaredMethod("rebuildWidgets");
                rebuild.setAccessible(true);
                rebuild.invoke(screen);
            } catch (ReflectiveOperationException e) {
                System.out.println("[Night Siege test] could not open the question: " + e);
            }
        });
        context.waitTicks(10);
    }

    static void closeScreen(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (client.gui.screen() != null) {
                client.gui.setScreen(null);
            }
        });
        context.waitTicks(10);
    }

    /** Raises a shield on the server and fires the shield action (as a left click while blocking would). */
    private static void shieldAction(ClientGameTestContext context) {
        onServer(context, (player, manager) -> {
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
            player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
            manager.onCoreAction(player, "shield");
        });
    }

    /** The guardian: both ways in the window, then the shield throw, the dome and the sanctuary in the world. */
    private static void guardianScene(ClientGameTestContext context) {
        openTree(context, "GUARDIAN", "vitality", "g_challenger", "g_shieldwall", "riposte", "riposte",
                "g_shieldthrow", "g_boomerang", "g_thorns", "g_laststand", "shield_charge", "g_debris");
        selectNode(context, "g_boomerang");
        context.takeScreenshot("ui_guardian_shield");
        zoomTo(context, "GUARDIAN", -200.0, -60.0, 0.55);
        context.takeScreenshot("ui_guardian_mid");
        zoomTo(context, "GUARDIAN", -230.0, -40.0, 1.0);
        context.takeScreenshot("ui_guardian_zoom");
        askClassReset(context, true);
        context.takeScreenshot("ui_class_reset_dialog");
        askClassReset(context, false);
        // The night fragments tab, with a blood fragment set into the core.
        onServer(context, (player, manager) -> manager.save().nightFragment = "BLOOD");
        showTab(context, "PERKS");
        context.takeScreenshot("ui_fragments");
        onServer(context, (player, manager) -> manager.save().nightFragment = null);
        closeScreen(context);
        context.getInput().lookAt(-90.0f, 4.0f);
        context.waitTicks(10);
        shieldAction(context);
        context.waitTicks(3);
        context.takeScreenshot("guardian_throw");
        context.waitTicks(20);
        onServer(context, (player, manager) -> manager.onCoreAction(player, "ult"));
        context.waitTicks(8);
        context.takeScreenshot("guardian_charge");
        context.waitTicks(20);

        openTree(context, "GUARDIAN", "vitality", "toughness", "field_dressing", "g_ward", "g_ward", "g_dome",
                "g_wellspring", "g_lightbringer", "g_grace", "g_sanctuary", "g_eternal_watch");
        selectNode(context, "g_wellspring");
        context.takeScreenshot("ui_guardian_blessing");
        zoomShots(context, "GUARDIAN", "guardian");
        closeScreen(context);
        context.getInput().lookAt(-90.0f, 20.0f);
        shieldAction(context);
        context.waitTicks(12);
        context.takeScreenshot("guardian_dome");
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            player.stopUsingItem();
            manager.onCoreAction(player, "ult");
        });
        context.waitTicks(30);
        context.takeScreenshot("guardian_sanctuary");
        context.waitTicks(5);
    }

    /** The engineer: both ways in the window, then the remote on a turret, a monster and a mine. */
    private static void engineerScene(ClientGameTestContext context) {
        openTree(context, "ENGINEER", "spike_trap", "spike_trap", "e_battery", "snare_trap", "e_circuit",
                "arrow_turret", "arrow_turret", "e_ammobelt", "e_overvolt", "e_charged", "e_watchtower",
                "overcharge", "e_sustained");
        selectNode(context, "e_charged");
        context.takeScreenshot("ui_engineer_mech");
        openTree(context, "ENGINEER", "spike_trap", "spike_trap", "e_finemech", "snare_trap", "e_detonator",
                "mortar_tower", "mortar_tower", "e_shrapnel_mine", "e_chain_reaction", "e_napalm", "e_blastpower", "bombardment",
                "e_carpet");
        selectNode(context, "e_chain_reaction");
        context.takeScreenshot("ui_engineer_chem");
        zoomShots(context, "ENGINEER", "engineer");
        closeScreen(context);
        context.getInput().lookAt(-90.0f, 8.0f);
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.core.BlockPos at = player.blockPosition();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(com.mki.nightsiege.game.ModContent.REMOTE));
            net.minecraft.core.BlockPos turret = at.offset(2, 0, 3);
            net.minecraft.core.BlockPos mine = at.offset(4, 0, 0);
            net.minecraft.core.BlockPos mine2 = at.offset(4, 0, 2);
            place(level, manager, player, turret, com.mki.nightsiege.core.TrapKind.TURRET);
            place(level, manager, player, mine, com.mki.nightsiege.core.TrapKind.MORTAR);
            place(level, manager, player, mine2, com.mki.nightsiege.core.TrapKind.MORTAR);
            manager.kits().traps().useRemote(player, turret, true);   // Fähigkeit 2: overcharge
            manager.kits().traps().useRemote(player, null, true);     // Fähigkeit 2: mark what is in sight
        });
        context.waitTicks(6);
        context.takeScreenshot("engineer_remote");
        onServer(context, (player, manager) -> manager.kits().traps().useRemote(player, player.blockPosition().offset(4, 0, 0), true));
        context.waitTicks(4);
        context.takeScreenshot("engineer_detonate");
        towerMenuScene(context);
        context.waitTicks(20);
        onServer(context, (player, manager) -> manager.onCoreAction(player, "ult"));
        context.waitTicks(6);
        context.takeScreenshot("engineer_bombardment");
        context.waitTicks(5);
    }

    /**
     * The tower menu (Simon, 27 October 2026): the arrow turret and a mortar are strengthened
     * (19:15/19:51: nothing breaks or runs empty any more – an old broken turret works again);
     * the right click with the remote shows them, the codes are typed and 3 s later they work.
     */
    private static void towerMenuScene(ClientGameTestContext context) {
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.core.BlockPos at = player.blockPosition();
            com.mki.nightsiege.game.TrapRecord turret = manager.kits().traps().recordAt(level, at.offset(2, 0, 3));
            com.mki.nightsiege.game.TrapRecord mortar = manager.kits().traps().recordAt(level, at.offset(4, 0, 0));
            if (turret != null) {
                turret.broken = true;   // as in an old save: the next tick clears it
            }
            com.mki.nightsiege.game.RemoteItem.handler.use(player, null);   // right click: the tower menu
        });
        context.waitTicks(10);
        context.runOnClient(client -> {
            boolean open = client.gui.screen() instanceof com.mki.nightsiege.client.TowerMenuScreen;
            System.out.println("[Night Siege test] towers menu=" + open + " rows="
                    + (open ? ((com.mki.nightsiege.client.TowerMenuScreen) client.gui.screen()).rows().size() : -1));
        });
        context.takeScreenshot("tower_menu");
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof com.mki.nightsiege.client.TowerMenuScreen menu) {
                menu.testShowCode(menu.rowOf(com.mki.nightsiege.core.TrapKind.TURRET.id()));
            }
        });
        context.waitTicks(3);
        context.takeScreenshot("tower_code");
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof com.mki.nightsiege.client.TowerMenuScreen menu) {
                menu.testBuff(menu.rowOf(com.mki.nightsiege.core.TrapKind.TURRET.id()));
                menu.testBuff(menu.rowOf(com.mki.nightsiege.core.TrapKind.MORTAR.id()));
            }
        });
        context.waitTicks(20);
        context.takeScreenshot("tower_working");
        context.waitTicks(60);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.core.BlockPos at = player.blockPosition();
            com.mki.nightsiege.game.TrapRecord turret = manager.kits().traps().recordAt(level, at.offset(2, 0, 3));
            com.mki.nightsiege.game.TrapRecord mortar = manager.kits().traps().recordAt(level, at.offset(4, 0, 0));
            System.out.println("[Night Siege test] towers after fix turret broken=" + (turret == null ? "?" : turret.broken)
                    + " strengthened=" + (turret != null && manager.kits().traps().strengthenedForTest(turret))
                    + " mortar strengthened=" + (mortar != null && manager.kits().traps().strengthenedForTest(mortar)));
            com.mki.nightsiege.game.RemoteItem.handler.use(player, null);   // the menu again: the turret shows strengthened
        });
        context.waitTicks(6);
        context.takeScreenshot("tower_buffed");
        closeScreen(context);
    }

    /**
     * Manned towers (Simon, 26 October 2026): the player sits in an arrow turret and a tesla
     * coil, holds the button and the husks in front lose life; climbing out restores the look.
     */
    private static void mannedScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int gx = pos.getX() - 50;
        int gz = pos.getZ() + 50;
        int gy = groundAt(context, gx, gz);
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(gx - 6, gy - 1, gz - 6, gx + 20, gy - 1, gz + 12));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(gx - 6, gy, gz - 6, gx + 20, gy + 8, gz + 12));
        for (int i = 0; i < 3; i++) {
            game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"ns_manned\"]}")
                    .formatted(gx + 8, gy, gz + i * 2));
        }
        game.getServer().runCommand("tp @p %d.5 %d %d.5 -90 10".formatted(gx, gy + 1, gz - 2));
        context.waitTicks(220);   // the chat lines of the commands fade, so the towers can be seen
        BlockPos turret = new BlockPos(gx, gy, gz);
        BlockPos tesla = new BlockPos(gx, gy, gz + 4);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.levels.put("arrow_turret", 1);
            own.levels.put("tesla_coil", 1);
            place(level, manager, player, turret, com.mki.nightsiege.core.TrapKind.TURRET);
            place(level, manager, player, tesla, com.mki.nightsiege.core.TrapKind.TESLA);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            boolean ok = manager.kits().traps().tryMount(level, turret, player, net.minecraft.world.item.ItemStack.EMPTY);
            System.out.println("[Night Siege test] tower mount=" + ok + " seated=" + manager.kits().traps().isSeated(player)
                    + " look=" + level.getBlockState(turret).getValue(com.mki.nightsiege.game.TowerBlock.MANNED));
        });
        context.waitTicks(10);
        context.getInput().lookAt(-90.0f, 4.0f);
        context.waitTicks(6);
        context.runOnClient(client -> System.out.println("[Night Siege test] tower client seated="
                + com.mki.nightsiege.client.MannedClient.seated()));
        context.takeScreenshot("manned_turret_first");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        context.waitTicks(6);
        context.takeScreenshot("manned_turret_back");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(6);
        context.takeScreenshot("manned_turret_front");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        logManned(context, "turret before");
        onServer(context, (player, manager) -> manager.kits().traps().onManAction(player, true));
        context.waitTicks(40);
        context.takeScreenshot("manned_turret_fire");
        onServer(context, (player, manager) -> manager.kits().traps().onManAction(player, false));
        logManned(context, "turret after 2s");
        // The tower takes the hits of its gunner (Simon, 27 October 2026): his health stays.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.LivingEntity husk = null;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof net.minecraft.world.entity.LivingEntity l && e.entityTags().contains("ns_manned") && l.isAlive()) {
                    husk = l;
                }
            }
            net.minecraft.world.level.GameType mode = player.gameMode();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);   // creative players take no hits
            float hp = player.getHealth();
            float towerBefore = manager.kits().traps().seatHealthLeft(player);
            player.invulnerableTime = 0;
            player.hurtServer(level, husk != null ? level.damageSources().mobAttack(husk) : level.damageSources().generic(), 6f);
            player.setGameMode(mode);
            System.out.println(String.format(java.util.Locale.ROOT,
                    "[Night Siege test] tower seat health player %.1f -> %.1f tower %.1f -> %.1f seated=%s",
                    hp, player.getHealth(), towerBefore, manager.kits().traps().seatHealthLeft(player),
                    manager.kits().traps().isSeated(player)));
        });
        // Climb out: the look goes back to the plain turret.
        onServer(context, (player, manager) -> player.stopRiding());
        context.waitTicks(4);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            System.out.println("[Night Siege test] tower out seated=" + manager.kits().traps().isSeated(player)
                    + " look=" + level.getBlockState(turret).getValue(com.mki.nightsiege.game.TowerBlock.MANNED));
            boolean ok = manager.kits().traps().tryMount(level, tesla, player, net.minecraft.world.item.ItemStack.EMPTY);
            System.out.println("[Night Siege test] tower tesla mount=" + ok);
        });
        context.waitTicks(8);
        context.getInput().lookAt(-90.0f, 6.0f);
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        onServer(context, (player, manager) -> manager.kits().traps().onManAction(player, true));
        context.waitTicks(12);
        context.takeScreenshot("manned_tesla_beam");
        context.waitTicks(28);
        onServer(context, (player, manager) -> manager.kits().traps().onManAction(player, false));
        logManned(context, "tesla after 2s");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(6);
        context.takeScreenshot("manned_tesla_front");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        onServer(context, (player, manager) -> {
            player.stopRiding();
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.setBlock(turret, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(tesla, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e.entityTags().contains("ns_manned")) {
                    e.discard();
                }
            }
        });
        context.waitTicks(5);
        nightKeys(context);
    }

    /**
     * Simon, 27 October 2026: in the night Q, F and Y are Night Siege's ability keys alone –
     * Q drops nothing, F swaps nothing.
     */
    private static void nightKeys(ClientGameTestContext context) {
        try {
            onServer(context, (player, manager) -> {
                player.getInventory().setSelectedSlot(8);
                player.getInventory().setItem(8, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 5));
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
            });
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(8));
            context.waitTicks(5);
            // The test player is no defender of a real night: the server's "the keys are taken" is set by hand.
            context.runOnClient(client -> {
                com.mki.nightsiege.client.NightKeys.setOwned(true);
                System.out.println("[Night Siege test] keys owned=" + com.mki.nightsiege.client.NightKeys.owned()
                        + " ability1=" + com.mki.nightsiege.client.NightSiegeClient.ultimateKey().saveString()
                        + " ability2=" + com.mki.nightsiege.client.GainClient.orderKey().saveString()
                        + " ability3=" + com.mki.nightsiege.client.GainClient.quiverKey().saveString()
                        + " ult2=" + com.mki.nightsiege.client.GainClient.secondUltKey().saveString());
            });
            net.minecraft.client.KeyMapping drop = context.computeOnClient(client -> client.options.keyDrop);
            net.minecraft.client.KeyMapping swap = context.computeOnClient(client -> client.options.keySwapOffhand);
            context.getInput().pressKey(drop);
            context.waitTicks(3);
            context.getInput().pressKey(swap);
            context.waitTicks(10);
            onServer(context, (player, manager) -> System.out.println("[Night Siege test] keys hand=" + player.getMainHandItem().getCount()
                    + " offhand=" + player.getOffhandItem().getCount() + " (want 5 and 0)"));
            context.takeScreenshot("night_keys");
        } catch (Throwable t) {
            System.out.println("[Night Siege test] keys error " + t);
            for (StackTraceElement e : t.getStackTrace()) {
                System.out.println("[Night Siege test] keys at " + e);
            }
        } finally {
            context.runOnClient(client -> com.mki.nightsiege.client.NightKeys.setOwned(false));
            onServer(context, (player, manager) -> player.getInventory().setItem(8, net.minecraft.world.item.ItemStack.EMPTY));
            context.waitTicks(2);
        }
    }

    private static void logManned(ClientGameTestContext context, String label) {
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            StringBuilder line = new StringBuilder("[Night Siege test] tower " + label + ":");
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof net.minecraft.world.entity.LivingEntity l && e.entityTags().contains("ns_manned")) {
                    line.append(String.format(java.util.Locale.ROOT, " %.1f", l.getHealth()));
                }
            }
            System.out.println(line);
        });
        context.waitTicks(2);
    }

    /**
     * The playtest (Simon, 28 October 2026): a player with a class, skills and diamonds starts it,
     * plans a whole path, is ready; each of the four steps gives the night's points along the path and
     * the crates up to then, a camp or a dungeon (taken there), a test night – each skipped with
     * "weiter". At the end the class, the skills and the diamonds are back.
     */
    private static void playtestScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        BlockPos core = new BlockPos(pos.getX() + 3, pos.getY(), pos.getZ() + 3);
        String[] before = new String[2];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            if (manager.mainCore() == null) {
                level.setBlock(core, com.mki.nightsiege.game.ModContent.SIEGE_CORE.defaultBlockState(), 3);
                manager.onCorePlaced(level, core, player);
            }
            String id = player.getUUID().toString();
            manager.save().register(id);
            if (manager.kits().hasStash(player)) {
                manager.kits().restore(player);   // a kit from the scene before
            }
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(id);
            own.reset();
            own.award(40);
            manager.onChooseClass(player, "ARCHER");
            for (int i = 0; i < 3; i++) {
                for (com.mki.nightsiege.core.SkillNode n : manager.skillTree().ofClass(com.mki.nightsiege.core.SkillClass.ARCHER)) {
                    if (manager.skillTree().canUnlock(n.id(), own)) {
                        manager.skillTree().unlock(n.id(), own);
                        break;
                    }
                }
            }
            player.getInventory().setItem(5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
            before[0] = own.chosenClass + " " + new java.util.TreeMap<>(own.levels) + " points=" + own.points;
            String problem = manager.playtest().start(level.getServer(), 8, player);   // alone (Simon 11:56)
            com.mki.nightsiege.core.PlayerSkills fresh = manager.save().skillsOf(id);
            System.out.println("[Night Siege test] playtest start: problem=" + problem + " before=" + before[0]
                    + " fresh class=" + fresh.hasClass() + " points=" + fresh.points()
                    + " inventory empty=" + player.getInventory().isEmpty() + " " + manager.playtest().status());
        });
        context.waitTicks(30);
        context.takeScreenshot("playtest_plan");
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.SkillTree tree = manager.skillTree();
            String sub = com.mki.nightsiege.core.SkillTree.subclassInfosOf(com.mki.nightsiege.core.SkillClass.WARRIOR).get(0).node();
            manager.onChooseClass(player, "WARRIOR:" + sub);
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            for (int round = 0; round < 600; round++) {
                boolean any = false;
                for (com.mki.nightsiege.core.SkillNode n : tree.ofClass(com.mki.nightsiege.core.SkillClass.WARRIOR)) {
                    if (tree.canUnlock(n.id(), own)) {
                        tree.unlock(n.id(), own);
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    break;
                }
            }
            System.out.println("[Night Siege test] playtest planned: " + own.className() + " path=" + own.history.size()
                    + " points left=" + own.points());
            manager.playtest().send(player, true);
        });
        context.waitTicks(20);
        context.takeScreenshot("playtest_plan_done");
        onServer(context, (player, manager) -> manager.playtest().onAction(player, "ready"));
        context.waitTicks(45);
        for (int step = 0; step < com.mki.nightsiege.game.Playtest.NIGHTS; step++) {
            int shot = step;
            onServer(context, (player, manager) -> {
                com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
                int due = manager.playtest().cratesDue(player.getUUID());
                manager.playtest().onAction(player, "crates");
                int crates = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (player.getInventory().getItem(i).is(com.mki.nightsiege.game.CrateService.CRATE)) {
                        crates += player.getInventory().getItem(i).getCount();
                    }
                }
                System.out.println("[Night Siege test] playtest step " + (shot + 1) + ": " + manager.playtest().status()
                        + " level=" + manager.currentLevel() + " points=" + own.points() + " learned=" + own.history.size()
                        + "/" + manager.playtest().pathOf(player.getUUID()).size() + " crates due=" + due + " in inventory=" + crates);
                manager.playtest().send(player, true);
            });
            context.waitTicks(20);
            if (shot == 0) {
                context.takeScreenshot("playtest_step1");
            }
            onServer(context, (player, manager) -> manager.playtest().skip(player.level().getServer()));   // to the camp or dungeon
            context.waitTicks(80);
            // A dungeon is searched and built during the pause before it (update 30.10.: no longer in one
            // tick): the part begins once it stands complete.
            String[] waitPhase = {""};
            int waited = 0;
            for (int wait = 0; wait < 120; wait++) {
                onServer(context, (player, manager) -> waitPhase[0] = manager.playtest().phase());
                context.waitTicks(20);
                if (!"pause_interlude".equals(waitPhase[0])) {
                    break;
                }
                waited++;
            }
            int waitedSeconds = waited;
            onServer(context, (player, manager) -> System.out.println("[Night Siege test] playtest waited " + waitedSeconds
                    + " s for the part: " + manager.expeditions().testState()));
            onServer(context, (player, manager) -> {
                String kind = manager.playtest().interlude();
                BlockPos at = player.blockPosition();
                String what = "camp".equals(kind)
                        ? "camp=" + (manager.save().camp != null && manager.save().camp.built)
                        : "dungeon=" + (manager.save().expedition != null && manager.save().expedition.built)
                        + " " + (manager.save().expedition == null ? "" : manager.save().expedition.kind);
                System.out.println("[Night Siege test] playtest interlude " + (shot + 1) + ": " + kind + " " + what
                        + " phase=" + manager.playtest().phase() + " player at " + at.getX() + " " + at.getY() + " " + at.getZ()
                        + " (" + (int) Math.sqrt(at.distSqr(core)) + " from the core) level=" + manager.currentLevel());
            });
            context.waitTicks(10);
            context.takeScreenshot("playtest_interlude" + (shot + 1));
            onServer(context, (player, manager) -> manager.playtest().skip(player.level().getServer()));   // the interlude ends
            context.waitTicks(20);
            onServer(context, (player, manager) -> {
                BlockPos at = player.blockPosition();
                System.out.println("[Night Siege test] playtest back: phase=" + manager.playtest().phase() + " camp="
                        + (manager.save().camp != null) + " dungeon=" + (manager.save().expedition != null)
                        + " player " + (int) Math.sqrt(at.distSqr(core)) + " from the core");
                manager.playtest().skip(player.level().getServer());   // the night
            });
            context.waitTicks(60);
            onServer(context, (player, manager) -> {
                int crates = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (player.getInventory().getItem(i).is(com.mki.nightsiege.game.CrateService.CRATE)) {
                        crates += player.getInventory().getItem(i).getCount();
                    }
                }
                com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
                System.out.println("[Night Siege test] playtest night " + (shot + 1) + ": phase=" + manager.playtest().phase()
                        + " preparing=" + manager.isPreparing() + " active=" + manager.isActive()
                        + " kit=" + manager.kits().hasStash(player) + " crates with the kit=" + crates
                        + " levels=" + new java.util.TreeMap<>(own.levels));
                manager.playtest().skip(player.level().getServer());   // the night ends
            });
            context.waitTicks(20);
            onServer(context, (player, manager) -> manager.playtest().skip(player.level().getServer()));   // the next step
            context.waitTicks(30);
        }
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            String after = own.chosenClass + " " + new java.util.TreeMap<>(own.levels) + " points=" + own.points;
            net.minecraft.world.item.ItemStack slot = player.getInventory().getItem(5);
            boolean ok = after.equals(before[0]) && slot.is(net.minecraft.world.item.Items.DIAMOND) && slot.getCount() == 7
                    && manager.save().playtest == null;
            System.out.println("[Night Siege test] playtest restored: " + (ok ? "all back" : "MISSING") + " skills=" + after
                    + " slot5=" + slot + " save=" + (manager.save().playtest == null ? "clean" : manager.playtest().status()));
        });
        context.waitTicks(10);
        game.getServer().runCommand("time set 1500");
        context.waitTicks(10);
    }


    /** The alchemist (Simon, 26 October 2026): the potion menu, then a healing zone on the ground. */
    /**
     * The kit HUD (Simon, 28 October 2026): the panel with one, with many and with no abilities,
     * hurt, under water, and the vanilla HUD back once the kit is gone.
     */
    private static void kitHudScene(ClientGameTestContext context, TestSingleplayerContext game) {
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect clear @p");
        context.getInput().lookAt(90.0f, 10.0f);
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.switchTo(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.levels.clear();
            own.levels.put("whirlwind", 1);
            own.levels.put("w_combo", 1);   // the blademaster: his own crest
            manager.kits().equip(player, 1);
        });
        game.getServer().runCommand("say Kit-HUD: so sieht der Chat neben der Leiste aus");
        game.getServer().runCommand("say zweite Zeile");
        context.waitTicks(30);
        context.runOnClient(client -> {
            com.mki.nightsiege.core.UltState st = com.mki.nightsiege.client.UltHud.state();
            System.out.println("[Night Siege test] kit hud: worn=" + com.mki.nightsiege.client.KitHud.worn()
                    + " active=" + com.mki.nightsiege.client.KitHud.active()
                    + " class=" + (st == null ? "?" : st.clazz) + " kitsOn=" + (st != null && st.kitsOn)
                    + " ult=" + (st != null && st.has) + " slots=" + (st == null ? -1 : st.slots.size()));
        });
        context.takeScreenshot("kithud_warrior");
        // The bars at the top (design update): a night bar, a boss bar and the camp's workbench.
        net.minecraft.server.level.ServerBossEvent[] bars = new net.minecraft.server.level.ServerBossEvent[3];
        onServer(context, (player, manager) -> {
            bars[0] = new net.minecraft.server.level.ServerBossEvent(com.mki.nightsiege.core.NightBarIds.make(com.mki.nightsiege.core.NightBarIds.Kind.NIGHT),
                    net.minecraft.network.chat.Component.literal("Belagerung – Stufe 3 – noch 204 s · Kills 11/39"),
                    net.minecraft.world.BossEvent.BossBarColor.PURPLE, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
            bars[1] = new net.minecraft.server.level.ServerBossEvent(com.mki.nightsiege.core.NightBarIds.make(com.mki.nightsiege.core.NightBarIds.Kind.BOSS),
                    net.minecraft.network.chat.Component.literal("Knochenlich"),
                    net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);
            bars[2] = new net.minecraft.server.level.ServerBossEvent(com.mki.nightsiege.core.NightBarIds.make(com.mki.nightsiege.core.NightBarIds.Kind.CAMP),
                    net.minecraft.network.chat.Component.literal("Belagerungswerkbank"),
                    net.minecraft.world.BossEvent.BossBarColor.YELLOW, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
            bars[0].setProgress(0.55f);
            bars[1].setProgress(0.8f);
            bars[2].setProgress(0.4f);
            for (net.minecraft.server.level.ServerBossEvent b : bars) {
                b.addPlayer(player);
            }
        });
        context.waitTicks(5);
        onServer(context, (player, manager) -> bars[1].setProgress(0.62f));
        context.waitTicks(4);
        context.takeScreenshot("kithud_bars");
        onServer(context, (player, manager) -> {
            for (net.minecraft.server.level.ServerBossEvent b : bars) {
                b.removeAllPlayers();
            }
        });
        // The kit inventory (variant B): crest, values and abilities where the crafting grid was.
        context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
        context.waitTicks(10);
        boolean[] hidden = {false};
        context.runOnClient(client -> hidden[0] = com.mki.nightsiege.core.KitView.hideCrafting
                && !client.player.inventoryMenu.getSlot(1).isActive());
        System.out.println("[Night Siege test] kit inventory: crafting hidden=" + hidden[0]);
        context.takeScreenshot("kithud_inventory");
        closeScreen(context);
        context.waitTicks(5);
        if (!hidden[0]) {
            throw new AssertionError("kit inventory: the crafting grid is still there");
        }
        // Many abilities: the warden with his ult, F and three healing abilities.
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.GUARDIAN);
            own.switchTo(com.mki.nightsiege.core.SkillClass.GUARDIAN);
            own.levels.clear();
            own.levels.put("vitality", 3);
            own.levels.put("field_dressing", 3);
            own.levels.put("g_ward", 2);
            own.levels.put("g_dome", 1);
            own.levels.put("holy_light", 1);
            manager.kits().refit(player, 1);
        });
        context.waitTicks(30);
        context.runOnClient(client -> {
            com.mki.nightsiege.core.UltState st = com.mki.nightsiege.client.UltHud.state();
            System.out.println("[Night Siege test] kit hud healer: class=" + (st == null ? "?" : st.clazz)
                    + " ult=" + (st != null && st.has) + " f=" + (st != null && st.hasAb2)
                    + " slots=" + (st == null ? -1 : st.slots.size()));
        });
        context.takeScreenshot("kithud_healer");
        // Hurt, with a shield of absorption, hungry and short of air.
        onServer(context, (player, manager) -> {
            player.setHealth(9f);
            player.setAbsorptionAmount(4f);
            player.getFoodData().setFoodLevel(13);
            player.setAirSupply(120);
        });
        context.waitTicks(3);
        context.takeScreenshot("kithud_hurt");
        // The alchemist: potions not learned yet get no box.
        onServer(context, (player, manager) -> {
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.levels.clear();
            own.levels.put("potion_tower", 3);
            own.levels.put("e_acid", 1);
            own.levels.put("brew_heal", 1);   // potions are nodes since 28 October 2026
            own.levels.put("brew_slow", 1);
            manager.kits().refit(player, 1);
            com.mki.nightsiege.core.WheelData wheel = manager.kits().traps().brewWheel(player);
            int open = 0;
            for (com.mki.nightsiege.core.WheelData.Option o : wheel == null ? java.util.List.<com.mki.nightsiege.core.WheelData.Option>of() : wheel.options) {
                open += o.open ? 1 : 0;
            }
            int shown = manager.abilitySlots(player).size();
            System.out.println("[Night Siege test] kit hud alchemist: potions=" + (wheel == null ? 0 : wheel.options.size())
                    + " learned=" + open + " boxes=" + shown);
            // The blast jump first (28 October 2026), then one key for all potions (30 October 2026).
            if (shown != (open > 0 ? 2 : 1)) {
                throw new AssertionError("kit hud: " + shown + " boxes for " + open + " learned potions (one key) and the blast jump");
            }
        });
        context.waitTicks(30);
        // v2 (29 October 2026): effects in the right wing, the action bar's text in the middle arch.
        game.getServer().runCommand("effect give @p minecraft:speed 30 0");
        game.getServer().runCommand("effect give @p minecraft:regeneration 8 0");
        game.getServer().runCommand("title @p actionbar \"Kein Platz für die Falle hier\"");
        context.waitTicks(5);
        context.takeScreenshot("kithud_alchemist");
        // Nothing learned: no ability section at all.
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.switchTo(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.levels.clear();
            manager.kits().refit(player, 1);
        });
        context.waitTicks(30);
        context.takeScreenshot("kithud_bare");
        // The season trophy (design update): the crown on a block in front and in the hand.
        net.minecraft.world.entity.Display.ItemDisplay[] crown = new net.minecraft.world.entity.Display.ItemDisplay[1];
        onServer(context, (player, manager) -> {
            net.minecraft.core.BlockPos at = player.blockPosition().relative(player.getDirection(), 3);
            ((net.minecraft.server.level.ServerLevel) player.level()).setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            crown[0] = com.mki.nightsiege.game.SiegeCrown.showOnCore((net.minecraft.server.level.ServerLevel) player.level(), at);
            player.getInventory().setItem(8, com.mki.nightsiege.game.SiegeCrown.trophy(1, "29.10.2026", java.util.List.of("Simon", "Quxo")));
            player.getInventory().setSelectedSlot(8);
        });
        context.waitTicks(10);
        context.takeScreenshot("kithud_crown");
        onServer(context, (player, manager) -> {
            if (crown[0] != null) {
                crown[0].discard();
            }
        });
        // The kit gone: the vanilla HUD is back.
        onServer(context, (player, manager) -> manager.kits().restore(player));
        context.waitTicks(10);
        boolean[] after = {true};
        context.runOnClient(client -> after[0] = com.mki.nightsiege.client.KitHud.active());
        System.out.println("[Night Siege test] kit hud after restore: active=" + after[0]);
        context.takeScreenshot("kithud_off");
        game.getServer().runCommand("gamemode creative @p");
        if (after[0]) {
            throw new AssertionError("kit hud still active without the kit");
        }
    }

    private static void alchemistScene(ClientGameTestContext context, TestSingleplayerContext game) {
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.levels.clear();
            own.levels.put("potion_tower", 3);
            own.levels.put("e_acid", 1);
            for (String brew : new String[] {"brew_heal", "brew_poison", "brew_slow"}) {
                own.levels.put(brew, 1);   // three of the seven potions (28 October 2026)
            }
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(com.mki.nightsiege.game.ModContent.REMOTE));
            System.out.println("[Night Siege test] alchemist sub=" + manager.skillTree().subclassOf(own));
        });
        // The ability bar (Simon, 27 October 2026, instead of the wheel): every potion on a key of its own.
        context.waitTicks(25);
        context.runOnClient(client -> {
            com.mki.nightsiege.core.UltState st = com.mki.nightsiege.client.UltHud.state();
            System.out.println("[Night Siege test] alchemist bar slots=" + (st == null ? -1 : st.slots.size())
                    + " ab2=" + (st != null && st.hasAb2));
        });
        context.takeScreenshot("alchemist_bar");
        context.getInput().lookAt(-90.0f, 45.0f);
        context.waitTicks(4);
        onServer(context, (player, manager) -> {
            // Update 30.10.: one potion key; held, the wheel goes to the slowness potion; a tap throws it.
            int slot = manager.abilitySlotOf(player, "brew");
            StringBuilder seen = new StringBuilder();
            for (int turn = 0; turn < 4 && slot >= 0; turn++) {
                String name = manager.abilitySlots(player).get(slot).name;
                seen.append(name.substring(name.lastIndexOf('.') + 1)).append(' ');
                if (name.endsWith(".slow")) {
                    break;
                }
                manager.onCoreAction(player, "cycle:" + slot + ":1");
            }
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (var one : manager.abilitySlots(player)) {
                ids.add(one.id);
            }
            System.out.println("[Night Siege test] alchemist potion key " + slot + " went " + seen + "bar=" + ids);
            manager.onCoreAction(player, "slot:" + Math.max(0, slot));   // a tap throws it at once
        });
        context.waitTicks(30);
        context.runOnClient(client -> System.out.println("[Night Siege test] alchemist zones="
                + com.mki.nightsiege.game.AlchemyZones.count()));
        context.takeScreenshot("alchemist_zone");
        context.waitTicks(5);
        // The warden (27 October 2026): his healing abilities on the bar, the dome's key casts it.
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.GUARDIAN);
            own.switchTo(com.mki.nightsiege.core.SkillClass.GUARDIAN);
            own.levels.clear();
            own.levels.put("vitality", 3);
            own.levels.put("field_dressing", 3);
            own.levels.put("g_ward", 2);
            own.levels.put("g_dome", 1);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        });
        context.waitTicks(25);
        context.runOnClient(client -> {
            com.mki.nightsiege.core.UltState st = com.mki.nightsiege.client.UltHud.state();
            System.out.println("[Night Siege test] healer bar slots=" + (st == null ? -1 : st.slots.size()));
        });
        context.takeScreenshot("healer_bar");
        onServer(context, (player, manager) -> manager.onCoreAction(player, "slot:" + Math.max(0, manager.abilitySlotOf(player, "dome"))));
        context.waitTicks(10);
        context.takeScreenshot("healer_dome");
        // Update 30.10.: the medic's self heal on a key of its own (the hand on himself, whoever he looks at).
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.levels.clear();
            own.levels.put("field_dressing", 1);
            own.levels.put("g_healing_hand", 2);   // the medic's subclass node
        });
        context.waitTicks(25);
        final float[] hpBefore = new float[1];
        onServer(context, (player, manager) -> {
            player.setHealth(player.getMaxHealth() - 8f);
            hpBefore[0] = player.getHealth();
            int key = manager.abilitySlotOf(player, "selfheal");
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (var one : manager.abilitySlots(player)) {
                ids.add(one.id);
            }
            System.out.println("[Night Siege test] medic bar=" + ids + " selfheal key=" + key
                    + " sub=" + manager.skillTree().subclassOf(manager.save().skillsOf(player.getUUID().toString())));
            manager.onCoreAction(player, "slot:" + Math.max(0, key));
        });
        context.waitTicks(5);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] medic self heal: hp "
                + hpBefore[0] + " -> " + player.getHealth()));
        context.takeScreenshot("medic_bar");
        // Update 30.10.: every warrior's storm step on R, 3 blocks the way he looks.
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.switchTo(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.levels.clear();
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
        context.getInput().lookAt(-90.0f, 10.0f);
        context.waitTicks(25);
        final double[] dashFrom = new double[3];
        onServer(context, (player, manager) -> {
            dashFrom[0] = player.getX();
            dashFrom[1] = player.getY();
            dashFrom[2] = player.getZ();
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (var one : manager.abilitySlots(player)) {
                ids.add(one.id);
            }
            System.out.println("[Night Siege test] warrior bar=" + ids + " onGround=" + player.onGround());
            manager.onCoreAction(player, "slot:" + Math.max(0, manager.abilitySlotOf(player, "dash")));
        });
        context.waitTicks(20);
        context.takeScreenshot("warrior_bar");
        onServer(context, (player, manager) -> {
            double dx = player.getX() - dashFrom[0];
            double dz = player.getZ() - dashFrom[2];
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] warrior dash: moved %.2f blocks (dx %.2f dz %.2f dy %.2f) dashes=%d",
                    Math.sqrt(dx * dx + dz * dz), dx, dz, player.getY() - dashFrom[1], com.mki.nightsiege.game.WarriorDash.dashes()));
        });
        // Update 30.10.: the quick shot's tap: a bow let go one tick after the press still shoots.
        final int[] arrowsBefore = new int[1];
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ARCHER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ARCHER);
            own.levels.clear();
            own.levels.put("swift_draw", 1);
            net.minecraft.world.item.ItemStack bow = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bow);
            player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW, 16));
            arrowsBefore[0] = player.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
                    player.getBoundingBox().inflate(64)).size();
            player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        context.waitTicks(1);
        onServer(context, (player, manager) -> player.releaseUsingItem());
        context.waitTicks(2);
        onServer(context, (player, manager) -> {
            int now = player.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
                    player.getBoundingBox().inflate(64)).size();
            System.out.println("[Night Siege test] quick shot tap: arrows " + arrowsBefore[0] + " -> " + now
                    + " saved taps=" + com.mki.nightsiege.game.QuickDraw.savedTaps()
                    + " sub=" + manager.skillTree().subclassOf(manager.save().skillsOf(player.getUUID().toString())));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.switchTo(com.mki.nightsiege.core.SkillClass.ENGINEER);
        });
        context.waitTicks(3);
    }

    /**
     * The engineer after Simon's playtest of 28 October 2026: every engineer's kit holds the flash
     * grenade and his ability bar starts with the blast jump, which throws him back; an arrow turret
     * reaches a champion beyond its range while a plain husk as far stays untouched; the alchemist
     * learns three of the seven potions, each with its own pause, and the acid flask; in a dungeon
     * the engineer sets up field towers although three turrets stand at home, and they go with the fight.
     */
    private static void techScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 140;
        int cz = pos.getZ() + 140;
        int cy = yard(context, game, cx, cz, 26, 10);
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 10".formatted(cx, cy, cz - 6));
        context.waitTicks(40);
        java.util.List<String> fails = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        int[] layoutMove = {-1, -1};   // the cobblestone's slot before and after the player moved it
        java.util.List<BlockPos> homeTurrets = java.util.List.of(new BlockPos(cx, cy, cz), new BlockPos(cx, cy, cz - 12),
                new BlockPos(cx + 2, cy, cz - 12));

        // 1. The kit: the flash grenade for every engineer, the blast jump first on the bar.
        onServer(context, (player, manager) -> {
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.levels.clear();
            for (String id : new String[] {"spike_trap", "snare_trap", "e_circuit", "arrow_turret"}) {
                own.levels.put(id, 3);
            }
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            manager.kits().equip(player, 1);
            int flash = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
                if ("alchemie_blende".equals(com.mki.nightsiege.game.Gadgets.of(stack))) {
                    flash += stack.getCount();
                }
            }
            java.util.List<com.mki.nightsiege.core.UltState.Slot> slots = manager.abilitySlots(player);
            String first = slots.isEmpty() ? "-" : slots.get(0).id;
            System.out.println("[Night Siege test] tech kit: sub=" + manager.skillTree().subclassOf(own) + " flash=" + flash
                    + " bar=" + slots.size() + " first=" + first);
            if (flash < 1) {
                fails.add("no flash grenade in the gunner's kit");
            }
            if (!"escape".equals(first)) {
                fails.add("the gunner's bar starts with " + first);
            }
            // Wooden tools in every kit, and the kit comes back sorted as the player left it (Simon 29.10.).
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            int cobble = -1;
            boolean pick = false;
            for (int i = 0; i < 36; i++) {
                if (inv.getItem(i).is(net.minecraft.world.item.Items.COBBLESTONE)) {
                    cobble = i;
                }
                pick |= inv.getItem(i).is(net.minecraft.world.item.Items.WOODEN_PICKAXE);
            }
            if (!pick) {
                fails.add("no wooden pickaxe in the kit");
            }
            // Moved into an empty slot, like Simon did (29.10.: "beim nächsten Kit dupliziert").
            int free = -1;
            for (int i = 35; i >= 9 && free < 0; i--) {
                if (inv.getItem(i).isEmpty()) {
                    free = i;
                }
            }
            if (cobble >= 0 && free >= 0) {
                layoutMove[0] = cobble;
                layoutMove[1] = free;
                inv.setItem(free, inv.getItem(cobble));
                inv.setItem(cobble, net.minecraft.world.item.ItemStack.EMPTY);
            }
        });
        context.waitTicks(5);
        onServer(context, (player, manager) -> manager.kits().restore(player));
        context.waitTicks(5);
        String[] serverSlots = new String[36];
        onServer(context, (player, manager) -> {
            manager.kits().equip(player, 1);
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            if (layoutMove[1] >= 0) {
                boolean back = inv.getItem(layoutMove[1]).is(net.minecraft.world.item.Items.COBBLESTONE);
                System.out.println("[Night Siege test] tech kit layout: cobblestone " + layoutMove[0] + " -> " + layoutMove[1]
                        + " kept=" + back);
                if (!back) {
                    fails.add("the kit forgot where the cobblestone was");
                }
            }
            for (int i = 0; i < 36; i++) {
                net.minecraft.world.item.ItemStack s = inv.getItem(i);
                serverSlots[i] = s.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()) + "x" + s.getCount();
            }
        });
        context.waitTicks(10);
        // What the client shows must be what the server holds (ghost copies looked like duplicated items).
        String ghost = context.computeOnClient(client -> {
            StringBuilder diff = new StringBuilder();
            net.minecraft.world.entity.player.Inventory inv = client.player.getInventory();
            for (int i = 0; i < 36; i++) {
                net.minecraft.world.item.ItemStack s = inv.getItem(i);
                String c = s.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()) + "x" + s.getCount();
                String sv = serverSlots[i] == null ? "" : serverSlots[i];
                if (!c.equals(sv)) {
                    diff.append(' ').append(i).append(":client=").append(c.isEmpty() ? "-" : c).append("/server=")
                            .append(sv.isEmpty() ? "-" : sv);
                }
            }
            return diff.toString();
        });
        System.out.println("[Night Siege test] tech kit client sync:" + (ghost.isEmpty() ? " ok" : ghost));
        if (!ghost.isEmpty()) {
            fails.add("the client shows other kit slots than the server:" + ghost);
        }
        context.waitTicks(20);
        context.takeScreenshot("tech_kit_bar");

        // 2. The blast jump: thrown back (looking south, so north), the husk beside him crawls.
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 0".formatted(cx - 8, cy, cz + 6));
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {PersistenceRequired:1b,Tags:[\"ns_tech\",\"ns_tech_push\"]}")
                .formatted(cx - 8, cy, cz + 8));
        context.waitTicks(30);
        double[] start = new double[3];
        onServer(context, (player, manager) -> {
            start[0] = player.getX();
            start[1] = player.getY();
            start[2] = player.getZ();
            System.out.println("[Night Siege test] tech escape before: ground=" + player.onGround()
                    + " slot=" + manager.abilitySlotOf(player, "escape"));
            manager.onCoreAction(player, "slot:" + Math.max(0, manager.abilitySlotOf(player, "escape")));
        });
        context.waitTicks(4);
        context.takeScreenshot("tech_escape_air");
        context.waitTicks(6);
        onServer(context, (player, manager) -> System.out.println(String.format(java.util.Locale.ROOT,
                "[Night Siege test] tech escape after 10 ticks: dx %.1f dz %.1f up %.1f", player.getX() - start[0],
                player.getZ() - start[2], player.getY() - start[1])));
        context.waitTicks(20);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            double dx = player.getX() - start[0];
            double dz = player.getZ() - start[2];
            double moved = Math.sqrt(dx * dx + dz * dz);
            boolean slowed = false;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e.entityTags().contains("ns_tech_push") && e instanceof net.minecraft.world.entity.LivingEntity l) {
                    slowed = l.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
                }
            }
            java.util.List<com.mki.nightsiege.core.UltState.Slot> slots = manager.abilitySlots(player);
            int wait = slots.isEmpty() ? -1 : slots.get(0).cooldownTicks;
            System.out.println(String.format(java.util.Locale.ROOT,
                    "[Night Siege test] tech escape: moved %.1f (dz %.1f, up %.1f) husk slowed=%s pause=%d",
                    moved, dz, player.getY() - start[1], slowed, wait));
            if (moved < 3.0 || dz > -2.0) {
                fails.add(String.format(java.util.Locale.ROOT, "blast jump moved only %.1f blocks (dz %.1f)", moved, dz));
            }
            if (!slowed) {
                fails.add("the blast jump did not slow the husk beside it");
            }
            if (wait <= 0) {
                fails.add("the blast jump has no pause");
            }
        });
        context.waitTicks(5);   // the commands below must not overtake the look above
        game.getServer().runCommand("kill @e[tag=ns_tech]");

        // 3. Towers and the champion: 21 blocks off (range 16, ×1.5 for bosses and champions); a plain husk 20 off stays whole.
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"ns_tech\",\"nightsiege_exp_champion\"],"
                + "attributes:[{id:\"minecraft:max_health\",base:200.0d}],Health:200.0f}").formatted(cx + 21, cy, cz));
        game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"ns_tech\",\"ns_tech_plain\"],"
                + "attributes:[{id:\"minecraft:max_health\",base:200.0d}],Health:200.0f}").formatted(cx - 20, cy, cz));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 facing %d.5 %d %d.5".formatted(cx + 6, cy, cz - 7, cx + 21, cy + 1, cz));
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (BlockPos at : homeTurrets) {
                place(level, manager, player, at, com.mki.nightsiege.core.TrapKind.TURRET);
            }
            net.minecraft.network.chat.Component blocked = manager.kits().traps().placeBlocked(player, com.mki.nightsiege.core.TrapKind.TURRET);
            System.out.println("[Night Siege test] tech home turrets: " + manager.kits().traps().placedBy(
                    player.getUUID().toString(), com.mki.nightsiege.core.TrapKind.TURRET, false) + " fourth blocked="
                    + (blocked == null ? "no (creative)" : blocked.getString()));
        });
        context.waitTicks(170);
        context.takeScreenshot("tech_turret_champion");
        onServer(context, (player, manager) -> {
            float champion = health(player, "nightsiege_exp_champion");
            float plain = health(player, "ns_tech_plain");
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] tech towers: champion %.1f/200 plain %.1f/200",
                    champion, plain));
            if (champion >= 200f) {
                fails.add("no tower reached the champion 21 blocks off");
            }
            if (plain < 200f) {
                fails.add("a tower reached the plain husk 20 blocks off");
            }
        });
        context.waitTicks(5);

        // 4. The alchemist: three of seven potions, each with its own pause, and the acid flask.
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            com.mki.nightsiege.core.SkillTree tree = manager.skillTree();
            own.levels.clear();
            own.points = 1000;
            for (String id : new String[] {"spike_trap", "snare_trap", "e_detonator", "potion_tower"}) {
                own.levels.put(id, 3);
            }
            own.levels.put("e_acid", 1);
            if (own.planned != null) {
                own.planned.remove(own.chosenClass);   // an earlier scene may have fixed another subclass
            }
            for (String id : new String[] {"brew_heal", "brew_slow", "brew_weak", "e_acid_flask"}) {
                if (!tree.unlock(id, own)) {
                    fails.add("could not learn " + id + ": " + tree.blockedReason(id, own));
                }
            }
            String fourth = tree.blockedReason("brew_poison", own);
            manager.kits().refit(player, 1);
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (com.mki.nightsiege.core.UltState.Slot s : manager.abilitySlots(player)) {
                ids.add(s.id);
            }
            System.out.println("[Night Siege test] tech alchemist: sub=" + tree.subclassOf(own) + " fourth potion=" + fourth
                    + " bar=" + ids);
            if (!"choice".equals(fourth)) {
                fails.add("a fourth potion is not shut: " + fourth);
            }
            if (!ids.equals(java.util.List.of("escape", "heal", "slow", "weak", "acid"))) {
                fails.add("alchemist bar " + ids);
            }
        });
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 45".formatted(cx - 6, cy, cz + 10));
        context.waitTicks(30);
        context.takeScreenshot("tech_alchemist_bar");
        int zonesBefore = context.computeOnClient(client -> com.mki.nightsiege.game.AlchemyZones.count());
        onServer(context, (player, manager) -> manager.onCoreAction(player, "slot:" + Math.max(0, manager.abilitySlotOf(player, "slow"))));
        context.waitTicks(25);
        onServer(context, (player, manager) -> {
            manager.onCoreAction(player, "slot:" + Math.max(0, manager.abilitySlotOf(player, "weak")));
            long now = player.level().getGameTime();
            int slow = manager.kits().traps().brewCooldownLeft(player, com.mki.nightsiege.core.BrewKind.SLOW, now);
            int heal = manager.kits().traps().brewCooldownLeft(player, com.mki.nightsiege.core.BrewKind.HEAL, now);
            System.out.println("[Night Siege test] tech alchemist pauses: slow=" + slow + " heal=" + heal);
            if (slow <= 0 || heal != 0) {
                fails.add("potion pauses slow=" + slow + " heal=" + heal);
            }
        });
        context.waitTicks(30);
        context.takeScreenshot("tech_alchemist_zones");
        int zonesAfter = context.computeOnClient(client -> com.mki.nightsiege.game.AlchemyZones.count());
        System.out.println("[Night Siege test] tech alchemist zones " + zonesBefore + " -> " + zonesAfter);
        if (zonesAfter - zonesBefore < 2) {
            fails.add("two potions in a row made " + (zonesAfter - zonesBefore) + " clouds");
        }
        // The acid flask onto the plain husk (out of the turrets' reach).
        game.getServer().runCommand("tp @p %d.5 %d %d.5 facing %d.5 %d %d.5".formatted(cx - 14, cy, cz, cx - 20, cy + 1, cz));
        context.waitTicks(10);
        onServer(context, (player, manager) -> manager.onCoreAction(player, "slot:" + Math.max(0, manager.abilitySlotOf(player, "acid"))));
        context.waitTicks(30);
        context.takeScreenshot("tech_acid_flask");
        onServer(context, (player, manager) -> {
            float plain = health(player, "ns_tech_plain");
            System.out.println(String.format(java.util.Locale.ROOT, "[Night Siege test] tech acid: plain husk %.1f/200", plain));
            if (plain >= 200f) {
                fails.add("the acid flask did not hurt the husk");
            }
        });
        context.waitTicks(5);
        game.getServer().runCommand("kill @e[tag=ns_tech]");

        // 5. A dungeon: field towers although three turrets stand at home; they go with the fight.
        onServer(context, (player, manager) -> {
            manager.kits().restore(player);
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.levels.clear();
            for (String id : new String[] {"spike_trap", "snare_trap", "e_circuit", "arrow_turret"}) {
                own.levels.put(id, 3);
            }
        });
        int bx = cx + 90;
        int bz = cz - 40;
        int fy = dungeonGround(context, game, bx, bz, 2);
        onServer(context, (player, manager) -> {
            manager.expeditions().testBuild((net.minecraft.server.level.ServerLevel) player.level(),
                    new BlockPos(bx, fy, bz), 0, com.mki.nightsiege.core.ExpeditionKind.STONE_CRYPT, 2);
            manager.expeditions().openEntry(true);   // straight in, no ready round
        });
        context.waitTicks(10);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                bx, fy - 20, bz + 34, bx, fy - 19, bz + 44));
        context.waitTicks(30);
        closeScreen(context);
        BlockPos[] field = new BlockPos[1];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            int items = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(com.mki.nightsiege.game.ModContent.TRAP_ITEMS.get(com.mki.nightsiege.core.TrapKind.TURRET))) {
                    items += stack.getCount();
                }
            }
            net.minecraft.network.chat.Component blocked = manager.kits().traps().placeBlocked(player, com.mki.nightsiege.core.TrapKind.TURRET);
            field[0] = player.blockPosition().offset(0, 0, 1);
            place(level, manager, player, field[0], com.mki.nightsiege.core.TrapKind.TURRET);
            com.mki.nightsiege.game.TrapRecord rec = manager.kits().traps().recordAt(level, field[0]);
            System.out.println("[Night Siege test] tech dungeon: fighter=" + manager.expeditions().isFighter(player)
                    + " field=" + manager.fieldFighter(player) + " turret items=" + items
                    + " blocked=" + (blocked == null ? "-" : blocked.getString()) + " placed field=" + (rec != null && rec.field)
                    + " home=" + manager.kits().traps().placedBy(player.getUUID().toString(), com.mki.nightsiege.core.TrapKind.TURRET, false));
            if (!manager.fieldFighter(player)) {
                fails.add("not a field fighter in the dungeon");
            }
            if (items < 1) {
                fails.add("no free turrets in the dungeon kit");
            }
            if (blocked != null) {
                fails.add("turret blocked in the dungeon: " + blocked.getString());
            }
            if (rec == null || !rec.field) {
                fails.add("the dungeon turret is no field tower");
            }
        });
        context.waitTicks(10);
        context.takeScreenshot("tech_dungeon_field_tower");
        onServer(context, (player, manager) -> {
            manager.expeditions().openEntry(false);
            manager.expeditions().remove(player.level().getServer());
        });
        context.waitTicks(20);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean gone = manager.kits().traps().recordAt(level, field[0]) == null
                    && com.mki.nightsiege.game.ModContent.trapOf(level.getBlockState(field[0])) == null;
            int home = manager.kits().traps().placedBy(player.getUUID().toString(), com.mki.nightsiege.core.TrapKind.TURRET, false);
            System.out.println("[Night Siege test] tech dungeon over: field tower gone=" + gone + " home=" + home);
            if (!gone) {
                fails.add("the field tower stayed after the fight");
            }
            if (home != homeTurrets.size()) {
                fails.add("home turrets " + home);
            }
            for (BlockPos at : homeTurrets) {
                level.removeBlock(at, false);
                manager.kits().traps().onBroken(level, at);
            }
        });
        context.waitTicks(5);
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(pos.getX(), pos.getY(), pos.getZ()));
        context.waitTicks(20);
        // The heavy archer has the blast jump first on his bar too (Simon 29.10., 00:09), and it gives 2 hearts back.
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ARCHER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ARCHER);
            own.levels.clear();
            own.levels.put("heavy_draw", 1);
            java.util.List<com.mki.nightsiege.core.UltState.Slot> slots = manager.abilitySlots(player);
            String first = slots.isEmpty() ? "-" : slots.get(0).id;
            player.setHealth(10f);
            com.mki.nightsiege.game.EngineerEscape.reset();
            manager.onCoreAction(player, "slot:" + Math.max(0, manager.abilitySlotOf(player, "escape")));
            System.out.println("[Night Siege test] tech heavy archer: sub=" + manager.skillTree().subclassOf(own)
                    + " first=" + first + " hp after the jump=" + Math.round(player.getHealth()));
            if (!"escape".equals(first)) {
                fails.add("the heavy archer's bar starts with " + first);
            }
            if (player.getHealth() < 13.5f && manager.isDefending(player)) {
                fails.add("the blast jump gave no hearts back (" + player.getHealth() + ")");
            }
        });
        context.waitTicks(20);
        if (!fails.isEmpty()) {
            throw new AssertionError("tech scene: " + String.join("; ", fails));
        }
    }

    /** Health of the first living entity with this tag, or -1. */
    private static float health(net.minecraft.server.level.ServerPlayer player, String tag) {
        for (net.minecraft.world.entity.Entity e : ((net.minecraft.server.level.ServerLevel) player.level()).getAllEntities()) {
            if (e.entityTags().contains(tag) && e instanceof net.minecraft.world.entity.LivingEntity l && l.isAlive()) {
                return l.getHealth();
            }
        }
        return -1f;
    }

    /**
     * The demolition expert's bird's-eye view (Simon, 26 October 2026): the view opens above
     * the base, point fire and a barrage line are called from it.
     */
    private static void spotterScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int gx = pos.getX() + 50;
        int gz = pos.getZ() - 60;
        int gy = groundAt(context, gx, gz);
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(gx - 20, gy - 1, gz - 20, gx + 20, gy - 1, gz + 20));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(gx - 20, gy, gz - 20, gx + 20, gy + 10, gz + 20));
        for (int i = 0; i < 4; i++) {
            game.getServer().runCommand(("summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"ns_spot\"]}")
                    .formatted(gx + 8, gy, gz - 3 + i * 2));
        }
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 20".formatted(gx - 6, gy, gz));
        // A normal view distance here: the view must show chunks around the camera (27 October 2026).
        int oldDistance = context.computeOnClient(client -> client.options.renderDistance().get());
        context.runOnClient(client -> client.options.renderDistance().set(6));
        context.waitTicks(220);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.levels.clear();
            own.levels.put("mortar_tower", 3);
            own.levels.put("e_barrage", 1);
            own.levels.put("e_smoke_salvo", 1);
            place(level, manager, player, new BlockPos(gx - 4, gy, gz - 2), com.mki.nightsiege.core.TrapKind.MORTAR);
            place(level, manager, player, new BlockPos(gx - 4, gy, gz + 2), com.mki.nightsiege.core.TrapKind.MORTAR);
            System.out.println("[Night Siege test] spotter sub=" + manager.skillTree().subclassOf(own));
            com.mki.nightsiege.core.SiegeConfig.SkillTweak wide = new com.mki.nightsiege.core.SiegeConfig.SkillTweak();
            wide.values = new java.util.HashMap<>(java.util.Map.of("spotradius", 5000.0));
            java.util.Map<String, com.mki.nightsiege.core.SiegeConfig.SkillTweak> tweaks = new java.util.HashMap<>();
            if (manager.config().skillTweaks != null) {
                tweaks.putAll(manager.config().skillTweaks);
            }
            tweaks.put("mortar_tower", wide);
            com.mki.nightsiege.core.SkillValues.apply(tweaks);
            manager.kits().traps().openSpotter(player);
        });
        context.waitTicks(20);
        context.runOnClient(client -> {
            boolean open = client.gui.screen() instanceof com.mki.nightsiege.client.SpotterScreen;
            System.out.println("[Night Siege test] spotter open=" + open);
            if (client.gui.screen() instanceof com.mki.nightsiege.client.SpotterScreen spot) {
                spot.testMove(gx + 2, gz, 24);
            }
        });
        context.waitTicks(10);
        context.takeScreenshot("spotter_view");
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof com.mki.nightsiege.client.SpotterScreen spot) {
                spot.testFire(0, new BlockPos(gx + 8, gy - 1, gz));
            }
        });
        context.waitTicks(50);
        context.takeScreenshot("spotter_point");
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof com.mki.nightsiege.client.SpotterScreen spot) {
                spot.testFire(1, new BlockPos(gx + 6, gy - 1, gz - 6));
                spot.testFire(1, new BlockPos(gx + 6, gy - 1, gz + 6));
            }
        });
        context.waitTicks(30);
        context.takeScreenshot("spotter_barrage");
        // Strengthening from the binoculars (Simon, 27 October 2026): a mortar is strengthened with its code.
        context.waitTicks(30);
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof com.mki.nightsiege.client.SpotterScreen spot) {
                System.out.println("[Night Siege test] spotter service rows=" + spot.testServiceRows()
                        + " started=" + spot.testServiceStart());
            }
        });
        context.waitTicks(5);
        context.takeScreenshot("spotter_service_code");
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof com.mki.nightsiege.client.SpotterScreen spot) {
                spot.testServiceType();
            }
        });
        context.waitTicks(100);
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof com.mki.nightsiege.client.SpotterScreen spot) {
                System.out.println("[Night Siege test] spotter service after rows=" + spot.testServiceRows());
            }
        });
        // Far from the body: the chunks follow the camera (27 October 2026).
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof com.mki.nightsiege.client.SpotterScreen spot) {
                spot.testPan(-70, -40);
            }
        });
        context.waitTicks(120);
        onServer(context, (player, manager) -> System.out.println("[Night Siege test] spotter chunks body="
                + player.chunkPosition() + " sent around=" + com.mki.nightsiege.game.SpotterChunks.centerFor(player)));
        context.takeScreenshot("spotter_far");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            StringBuilder line = new StringBuilder("[Night Siege test] spotter husks:");
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof net.minecraft.world.entity.LivingEntity l && e.entityTags().contains("ns_spot")) {
                    line.append(String.format(java.util.Locale.ROOT, " %.1f", l.getHealth()));
                }
            }
            System.out.println(line);
        });
        closeScreen(context);
        context.runOnClient(client -> System.out.println("[Night Siege test] spotter closed camera="
                + (com.mki.nightsiege.client.SpotterScreen.cameraAt(0f) == null) + " smartCull=" + client.smartCull));
        context.runOnClient(client -> client.options.renderDistance().set(oldDistance));
        onServer(context, (player, manager) -> com.mki.nightsiege.core.SkillValues.apply(manager.config().skillTweaks));
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            level.setBlock(new BlockPos(gx - 4, gy, gz - 2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(new BlockPos(gx - 4, gy, gz + 2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e != null && e.entityTags().contains("ns_spot")) {
                    e.discard();
                }
            }
        });
        context.waitTicks(5);
    }

    /** Prints every player arrow when it comes into the world (only while the bolter scene measures). */
    private static boolean BOLT_WATCH;
    private static boolean BOLT_HOOKED;

    /**
     * The bolter's aim (Simon 30.10., 11:50: "die Armbrust schießt nicht aufs Fadenkreuz, der Schuss
     * kommt von weiter unten"): the crossbow loaded and shot by a real right click at a white wall
     * 15.5 blocks ahead. For every shot: where the bolt starts and how it flies next to the eye and
     * the look on the server and the client, where it sticks against the point under the crosshair,
     * and two pictures. Kit crossbow standing, sneaking, sneaking at once, a plain crossbow.
     */
    private static void bolterAimScene(ClientGameTestContext context, TestSingleplayerContext game) {
        if (!BOLT_HOOKED) {
            BOLT_HOOKED = true;
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                if (BOLT_WATCH && entity instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow a
                        && a.getOwner() instanceof net.minecraft.server.level.ServerPlayer p) {
                    net.minecraft.world.phys.Vec3 eye = p.getEyePosition();
                    net.minecraft.world.phys.Vec3 look = p.getLookAngle();
                    net.minecraft.world.phys.Vec3 m = a.getDeltaMovement();
                    double speed = m.length();
                    double angle = speed < 1e-6 ? -1 : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, m.scale(1 / speed).dot(look)))));
                    System.out.println(String.format(Locale.ROOT,
                            "[Night Siege test] bolt spawn pos=%.3f/%.3f/%.3f eye=%.3f/%.3f/%.3f below-eye=%.3f motion=%.3f/%.3f/%.3f speed=%.2f "
                                    + "look=%.3f/%.3f/%.3f off-look=%.2fdeg pitch=%.1f yaw=%.1f crouch=%s pose=%s eyeH=%.2f feetY=%.3f weapon=%s model=%s tags=%s",
                            a.getX(), a.getY(), a.getZ(), eye.x, eye.y, eye.z, eye.y - a.getY(), m.x, m.y, m.z, speed,
                            look.x, look.y, look.z, angle, p.getXRot(), p.getYRot(), p.isCrouching(), p.getPose(), p.getEyeHeight(), p.getY(),
                            a.getWeaponItem() == null ? "-" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(a.getWeaponItem().getItem()),
                            a.getWeaponItem() == null ? "-" : a.getWeaponItem().get(net.minecraft.core.component.DataComponents.ITEM_MODEL),
                            a.entityTags()));
                }
            });
        }
        final int[] o = new int[3];
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            BlockPos b = player.blockPosition().above(30);
            o[0] = b.getX();
            o[1] = b.getY();
            o[2] = b.getZ();
            for (int dx = -18; dx <= 3; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    for (int dy = -1; dy <= 8; dy++) {
                        net.minecraft.world.level.block.state.BlockState state = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                        if (dy == -1 && dx >= -2) {
                            state = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
                        }
                        if (dx == -16) {
                            state = (dy == 1 && dz == 0 ? net.minecraft.world.level.block.Blocks.COAL_BLOCK
                                    : net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK).defaultBlockState();
                        }
                        level.setBlock(b.offset(dx, dy, dz), state, 2);
                    }
                }
            }
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, java.util.Set.of(), 90f, 0f, true);
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ARCHER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ARCHER);
            own.levels.clear();
            own.levels.put("archer_focus", 1);
            own.levels.put("heavy_bolts", 3);
            own.levels.put("shrapnel", 1);
            manager.kits().equip(player, 10);
            net.minecraft.world.item.ItemStack xbow = net.minecraft.world.item.ItemStack.EMPTY;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
                if (s.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                    xbow = s.copy();
                    break;
                }
            }
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, xbow);
            System.out.println("[Night Siege test] bolter sub=" + manager.skillTree().subclassOf(own)
                    + " way=" + own.pathTaken(manager.skillTree()) + " kit=" + manager.kits().hasStash(player)
                    + " hand=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(xbow.getItem())
                    + " model=" + xbow.get(net.minecraft.core.component.DataComponents.ITEM_MODEL)
                    + " enchants=" + xbow.getEnchantments());
        });
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        context.getInput().lookAt(90.0f, 0.0f);
        context.waitTicks(20);
        bolterShot(context, o, "kit_stand", 0);
        context.runOnClient(client -> client.options.keyShift.setDown(true));
        context.waitTicks(15);
        bolterShot(context, o, "kit_sneak", 0);
        context.runOnClient(client -> client.options.keyShift.setDown(false));
        context.waitTicks(15);
        // Sneak pressed only just before the shot: the camera still comes down while the bolt leaves.
        bolterShot(context, o, "kit_sneak_now", 2);
        context.runOnClient(client -> client.options.keyShift.setDown(false));
        context.waitTicks(15);
        // A plain crossbow in a warrior's hand: no kit look, no archer skills (Minecraft alone).
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.switchTo(com.mki.nightsiege.core.SkillClass.WARRIOR);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CROSSBOW));
        });
        context.waitTicks(10);
        bolterShot(context, o, "plain_stand", 0);
        context.getInput().lookAt(90.0f, -10.0f);
        context.waitTicks(5);
        bolterShot(context, o, "plain_up10", 0);
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.switchTo(com.mki.nightsiege.core.SkillClass.ARCHER);
            manager.kits().equip(player, 10);
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
                if (s.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, s.copy());
                    break;
                }
            }
        });
        context.waitTicks(10);
        bolterShot(context, o, "kit_up10", 0);
        context.getInput().lookAt(90.0f, 0.0f);
        context.waitTicks(5);

        // Simon 30.10., 12:39: "es war nur im Dungeon so" – the same shots in a dungeon fight.
        int bx = o[0] + 140;
        int bz = o[2] - 60;
        int fy = dungeonGround(context, game, bx, bz, 2);
        onServer(context, (player, manager) -> {
            manager.kits().restore(player);
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ARCHER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ARCHER);
            own.levels.clear();
            // Simon's bolter on the server (30.10.).
            own.levels.put("heavy_bolts", 1);
            own.levels.put("archer_focus", 2);
            own.levels.put("shrapnel", 2);
            own.levels.put("a_trapmaster", 1);
            own.levels.put("a_quiver", 1);
            own.levels.put("a_explosive", 1);
            own.levels.put("a_lightxbow", 1);
            own.levels.put("a_tripwire", 1);
            manager.expeditions().testBuild((net.minecraft.server.level.ServerLevel) player.level(),
                    new BlockPos(bx, fy, bz), 0, com.mki.nightsiege.core.ExpeditionKind.DEEP_CELLAR, 2);
            manager.expeditions().openEntry(true);   // straight in, no ready round
        });
        context.waitTicks(10);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                bx, fy - 20, bz + 34, bx, fy - 19, bz + 44));
        context.waitTicks(40);
        closeScreen(context);
        onServer(context, (player, manager) -> {
            if (!manager.expeditions().isFighter(player)) {
                manager.expeditions().testJoin(player);
            }
            for (net.minecraft.world.entity.Mob mob : manager.expeditions().testMobs((net.minecraft.server.level.ServerLevel) player.level())) {
                mob.setNoAi(true);   // no monster in the way of the measurement
            }
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = player.getInventory().getItem(i);
                if (st.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                    net.minecraft.world.item.ItemStack held = player.getMainHandItem().copy();
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, st.copy());
                    player.getInventory().setItem(i, held);
                    break;
                }
            }
            System.out.println("[Night Siege test] bolt dungeon: " + manager.expeditions().testState()
                    + " fighter=" + manager.expeditions().isFighter(player) + " kit=" + manager.kits().hasStash(player)
                    + " hand=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem())
                    + " model=" + player.getMainHandItem().get(net.minecraft.core.component.DataComponents.ITEM_MODEL)
                    + " at=" + player.blockPosition() + " pose=" + player.getPose());
        });
        closeScreen(context);
        context.waitTicks(10);
        context.takeScreenshot("bolt_dungeon_view");
        int[] d = {bx, fy - 20, bz + 34};
        bolterShot(context, d, "dungeon_stand", 0);
        context.getInput().lookAt(0.0f, 10.0f);
        context.waitTicks(5);
        bolterShot(context, d, "dungeon_down10", 0);
        context.getInput().lookAt(0.0f, -10.0f);
        context.waitTicks(5);
        bolterShot(context, d, "dungeon_up10", 0);
        // What the standing player collides with down there, on the server and on the client, and
        // where in the halls the server would make him crawl (eye 0.4) while the client stands.
        onServer(context, (player, manager) -> {
            net.minecraft.world.level.Level level = player.level();
            net.minecraft.world.phys.AABB box = player.getDimensions(net.minecraft.world.entity.Pose.STANDING)
                    .makeBoundingBox(player.position()).deflate(1.0E-7);
            StringBuilder line = new StringBuilder("[Night Siege test] bolt collide server at " + player.blockPosition() + ":");
            for (net.minecraft.world.phys.shapes.VoxelShape shape : level.getBlockCollisions(player, box)) {
                net.minecraft.world.phys.AABB bb = shape.bounds();
                BlockPos at = BlockPos.containing(bb.getCenter());
                line.append(String.format(Locale.ROOT, " block %s %s [%.2f..%.2f]", at, level.getBlockState(at), bb.minY, bb.maxY));
            }
            for (net.minecraft.world.phys.shapes.VoxelShape shape : level.getEntityCollisions(player, box)) {
                line.append(String.format(Locale.ROOT, " entityShape %s", shape.bounds()));
            }
            for (net.minecraft.world.entity.Entity e : level.getEntities(player, box.inflate(0.5))) {
                line.append(" entity ").append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))
                        .append(e.canBeCollidedWith(player) ? "(solid)" : "").append(e.entityTags());
            }
            line.append(" border=").append(level.getWorldBorder().isWithinBounds(box));
            System.out.println(line);
            // The halls: each block column at the player's floor height, 30 to each side and 70 ahead.
            int crawl = 0;
            int blocked = 0;
            int free = 0;
            StringBuilder spots = new StringBuilder();
            for (int x = player.getBlockX() - 14; x <= player.getBlockX() + 14; x++) {
                for (int z = player.getBlockZ() - 30; z <= player.getBlockZ() + 70; z++) {
                    BlockPos foot = new BlockPos(x, player.getBlockY(), z);
                    if (!level.getBlockState(foot.below()).isFaceSturdy(level, foot.below(), net.minecraft.core.Direction.UP)
                            || !level.getBlockState(foot).getCollisionShape(level, foot).isEmpty()) {
                        continue;   // no floor to stand on here
                    }
                    net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(foot);
                    boolean stand = level.noCollision(player, player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(at).deflate(1.0E-7));
                    boolean swim = level.noCollision(player, player.getDimensions(net.minecraft.world.entity.Pose.SWIMMING).makeBoundingBox(at).deflate(1.0E-7));
                    boolean airAbove = level.getBlockState(foot.above()).getCollisionShape(level, foot.above()).isEmpty();
                    if (stand) {
                        free++;
                    } else if (swim) {
                        crawl++;
                        if (airAbove && spots.length() < 1500) {
                            spots.append(' ').append(x).append('/').append(z);
                        }
                    } else {
                        blocked++;
                    }
                }
            }
            System.out.println("[Night Siege test] bolt collide scan: free=" + free + " crawl=" + crawl + " blocked=" + blocked
                    + " crawl-with-air-above:" + spots);
        });
        context.runOnClient(client -> {
            net.minecraft.world.phys.AABB box = client.player.getDimensions(net.minecraft.world.entity.Pose.STANDING)
                    .makeBoundingBox(client.player.position()).deflate(1.0E-7);
            StringBuilder line = new StringBuilder("[Night Siege test] bolt collide client fits=" + client.level.noCollision(client.player, box) + ":");
            for (net.minecraft.world.phys.shapes.VoxelShape shape : client.level.getBlockCollisions(client.player, box)) {
                line.append(" block ").append(shape.bounds());
            }
            for (net.minecraft.world.phys.shapes.VoxelShape shape : client.level.getEntityCollisions(client.player, box)) {
                line.append(" entityShape ").append(shape.bounds());
            }
            line.append(" border=").append(client.level.getWorldBorder().isWithinBounds(box));
            System.out.println(line);
        });
        onServer(context, (player, manager) -> {
            manager.expeditions().openEntry(false);
            manager.expeditions().remove(player.level().getServer());
        });
        context.waitTicks(20);
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
    }

    /**
     * One crossbow shot: load by holding the use key, then (after {@code sneakFirst} ticks of fresh
     * sneaking, when not 0) a click fires. Prints the flight on server and client and where it stuck.
     */
    private static void bolterShot(ClientGameTestContext context, int[] o, String label, int sneakFirst) {
        onServer(context, (player, manager) -> {
            for (net.minecraft.world.entity.Entity e : player.level().getEntities((net.minecraft.world.entity.Entity) null,
                    player.getBoundingBox().inflate(40), e -> e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow)) {
                e.discard();
            }
        });
        context.runOnClient(client -> client.options.keyUse.setDown(true));
        context.waitTicks(36);
        context.runOnClient(client -> client.options.keyUse.setDown(false));
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            net.minecraft.world.item.component.ChargedProjectiles loaded = player.getMainHandItem()
                    .get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
            System.out.println("[Night Siege test] bolt " + label + " loaded=" + (loaded != null && !loaded.isEmpty()));
        });
        if (sneakFirst > 0) {
            context.runOnClient(client -> client.options.keyShift.setDown(true));
            context.waitTicks(sneakFirst);
        }
        context.runOnClient(client -> {
            net.minecraft.world.phys.Vec3 eye = client.player.getEyePosition();
            System.out.println(String.format(Locale.ROOT,
                    "[Night Siege test] bolt %s client before eye=%.3f/%.3f/%.3f feetY=%.3f pitch=%.1f yaw=%.1f crouch=%s pose=%s eyeH=%.2f",
                    label, eye.x, eye.y, eye.z, client.player.getY(), client.player.getXRot(), client.player.getYRot(),
                    client.player.isCrouching(), client.player.getPose(), client.player.getEyeHeight()));
        });
        onServer(context, (player, manager) -> {
            net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
            System.out.println(String.format(Locale.ROOT,
                    "[Night Siege test] bolt %s server before eye=%.3f/%.3f/%.3f feetY=%.3f pose=%s eyeH=%.2f fighter=%s fitsStanding=%s",
                    label, eye.x, eye.y, eye.z, player.getY(), player.getPose(), player.getEyeHeight(),
                    manager.expeditions().isFighter(player),
                    player.level().noCollision(player, player.getDimensions(net.minecraft.world.entity.Pose.STANDING)
                            .makeBoundingBox(player.position()).deflate(1.0E-7))));
        });
        BOLT_WATCH = true;
        net.minecraft.client.KeyMapping use = context.computeOnClient(client -> client.options.keyUse);
        context.getInput().pressKey(use);
        for (int t = 1; t <= 7; t++) {
            context.waitTicks(1);
            final int tick = t;
            onServer(context, (player, manager) -> {
                StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "[Night Siege test] bolt %s t=%d server:", label, tick));
                for (net.minecraft.world.entity.Entity e : player.level().getEntities((net.minecraft.world.entity.Entity) null,
                        player.getBoundingBox().inflate(40), e -> e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow)) {
                    line.append(String.format(Locale.ROOT, " %.2f/%.3f/%.2f v=%.2f/%.3f/%.2f%s", e.getX(), e.getY(), e.getZ(),
                            e.getDeltaMovement().x, e.getDeltaMovement().y, e.getDeltaMovement().z,
                            ((com.mki.nightsiege.mixin.ArrowAccessor) e).nightsiege$isInGround() ? " stuck" : ""));
                }
                System.out.println(line);
            });
            context.runOnClient(client -> {
                StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "[Night Siege test] bolt %s t=%d client:", label, tick));
                for (net.minecraft.world.entity.Entity e : client.level.getEntities((net.minecraft.world.entity.Entity) null,
                        client.player.getBoundingBox().inflate(40), e -> e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow)) {
                    line.append(String.format(Locale.ROOT, " %.2f/%.3f/%.2f", e.getX(), e.getY(), e.getZ()));
                }
                System.out.println(line);
            });
            if (t == 1) {
                context.takeScreenshot("bolt_" + label + "_flight");
            }
        }
        BOLT_WATCH = false;
        context.waitTicks(8);
        onServer(context, (player, manager) -> {
            net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            double wall = o[0] - 15;   // the wall's face towards the player (the open-air wall; in the dungeon look.x is 0)
            double t = Math.abs(look.x) > 0.5 ? (wall - eye.x) / look.x : 0;
            double wantY = eye.y + look.y * t;
            double wantZ = eye.z + look.z * t;
            for (net.minecraft.world.entity.Entity e : player.level().getEntities((net.minecraft.world.entity.Entity) null,
                    player.getBoundingBox().inflate(40), e -> e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow)) {
                System.out.println(String.format(Locale.ROOT,
                        "[Night Siege test] bolt %s result: stuck=%s at=%.2f/%.3f/%.3f crosshair=%.3f/%.3f below-crosshair=%.3f side=%.3f",
                        label, ((com.mki.nightsiege.mixin.ArrowAccessor) e).nightsiege$isInGround(), e.getX(), e.getY(), e.getZ(),
                        wantY, wantZ, wantY - e.getY(), e.getZ() - wantZ));
            }
        });
        context.takeScreenshot("bolt_" + label + "_hit");
    }

    /**
     * The drone of a fallen defender (Simon, 26 October 2026): it flies forward with W, turns
     * with D, climbs with Shift, and its crossbow hits a husk in front of it.
     */
    private static void droneScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int gx = pos.getX() - 50;
        int gz = pos.getZ() - 50;
        int gy = groundAt(context, gx, gz);
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(gx - 12, gy - 1, gz - 12, gx + 12, gy - 1, gz + 12));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(gx - 12, gy, gz - 12, gx + 12, gy + 10, gz + 12));
        game.getServer().runCommand(("summon minecraft:husk %d.5 %d %d.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"ns_drone\"]}")
                .formatted(gx, gy, gz + 6));
        game.getServer().runCommand("tp @p %d.5 %d %d.5 0 10".formatted(gx, gy + 2, gz));
        context.waitTicks(220);
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.Drones.start(player);
            System.out.println("[Night Siege test] drone start drone=" + com.mki.nightsiege.game.Drones.isDrone(player)
                    + " flying=" + player.getAbilities().flying + " height=" + String.format(java.util.Locale.ROOT, "%.2f", player.getBbHeight()));
        });
        context.waitTicks(20);
        context.getInput().lookAt(0.0f, 8.0f);
        context.waitTicks(5);
        context.runOnClient(client -> System.out.println("[Night Siege test] drone client=" + com.mki.nightsiege.client.DroneClient.localDrone()
                + " rig following=" + com.mki.nightsiege.client.DroneClient.rigFollowing()));
        context.takeScreenshot("drone_first");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(6);
        context.takeScreenshot("drone_front");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        context.waitTicks(6);
        context.takeScreenshot("drone_back");
        context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        // The crossbow: two seconds of fire at the husk ahead (the drone hovers above it, so it aims down).
        context.getInput().lookAt(0.0f, 6.0f);
        context.waitTicks(3);
        onServer(context, (player, manager) -> com.mki.nightsiege.game.Drones.onFireButton(player, true));
        context.waitTicks(30);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            StringBuilder line = new StringBuilder(String.format(java.util.Locale.ROOT,
                    "[Night Siege test] drone shots=%d eye=%.2f/%.2f/%.2f look=%.2f/%.2f/%.2f arrows:",
                    com.mki.nightsiege.game.Drones.shots, player.getEyePosition().x, player.getEyePosition().y, player.getEyePosition().z,
                    player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z));
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow a && a.getOwner() == player) {
                    line.append(String.format(java.util.Locale.ROOT, " %.1f/%.1f/%.1f", e.getX(), e.getY(), e.getZ()));
                }
                if (e.entityTags().contains("ns_drone")) {
                    line.append(String.format(java.util.Locale.ROOT, " husk@%.1f/%.1f/%.1f", e.getX(), e.getY(), e.getZ()));
                }
            }
            System.out.println(line);
        });
        context.waitTicks(10);
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.Drones.onFireButton(player, false);
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof net.minecraft.world.entity.LivingEntity l && e.entityTags().contains("ns_drone")) {
                    System.out.println("[Night Siege test] drone husk=" + String.format(java.util.Locale.ROOT, "%.1f", l.getHealth()));
                }
            }
        });
        // Steering: W forward, D turns, Shift climbs (the keys as if held).
        final double[] start = new double[4];
        context.runOnClient(client -> {
            start[0] = client.player.getX();
            start[1] = client.player.getY();
            start[2] = client.player.getZ();
            start[3] = client.player.getYRot();
            client.options.keyUp.setDown(true);
        });
        context.waitTicks(20);
        context.runOnClient(client -> {
            client.options.keyUp.setDown(false);
            client.options.keyRight.setDown(true);
        });
        context.waitTicks(10);
        context.runOnClient(client -> {
            client.options.keyRight.setDown(false);
            client.options.keyShift.setDown(true);
        });
        context.waitTicks(10);
        context.runOnClient(client -> {
            client.options.keyShift.setDown(false);
            System.out.println(String.format(java.util.Locale.ROOT,
                    "[Night Siege test] drone moved dz=%.2f dy=%.2f turned=%.1f",
                    client.player.getZ() - start[2], client.player.getY() - start[1], client.player.getYRot() - start[3]));
        });
        context.takeScreenshot("drone_after");
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.Drones.stop(player);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            System.out.println("[Night Siege test] drone stop drone=" + com.mki.nightsiege.game.Drones.isDrone(player)
                    + " height=" + String.format(java.util.Locale.ROOT, "%.2f", player.getBbHeight()));
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e.entityTags().contains("ns_drone")) {
                    e.discard();
                }
            }
        });
        context.waitTicks(5);
    }

    private static void place(net.minecraft.server.level.ServerLevel level, com.mki.nightsiege.game.SiegeManager manager,
                              net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos,
                              com.mki.nightsiege.core.TrapKind kind) {
        level.setBlock(pos, com.mki.nightsiege.game.ModContent.TRAP_BLOCKS.get(kind).defaultBlockState(), 3);
        manager.onTrapPlaced(level, pos, player, kind);
    }

    /**
     * Switches Iris to a shader pack. A bare file name means a pack that is
     * already in the shaderpacks folder (the mod puts its own pack there);
     * a path is copied there first.
     */
    private static boolean activate(ClientGameTestContext context, Path pack) {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
            Files.createDirectories(dir);
            Path target = dir.resolve(pack.getFileName().toString());
            if (pack.getParent() != null || !Files.exists(target)) {
                if (!Files.exists(pack)) {
                    System.out.println("[Night Siege test] shader pack missing: " + pack);
                    return false;
                }
                Files.copy(pack, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String name = target.getFileName().toString();
            context.<Exception>runOnClient(client -> {
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                Object config = iris.getMethod("getIrisConfig").invoke(null);
                config.getClass().getMethod("setShaderPackName", String.class).invoke(config, name);
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
                config.getClass().getMethod("save").invoke(config);
                iris.getMethod("reload").invoke(null);
            });
            System.out.println("[Night Siege test] shader pack active: " + name + " mode "
                    + context.computeOnClient(client -> com.mki.nightsiege.client.ShaderSupport.mode()));
            return true;
        } catch (Exception e) {
            System.out.println("[Night Siege test] shader pack failed: " + pack + " " + e);
            e.printStackTrace(System.out);
            return false;
        }
    }
}
