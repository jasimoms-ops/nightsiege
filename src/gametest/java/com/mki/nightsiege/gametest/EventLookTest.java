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
                if (want.contains("machines")) {
                    machineScene(context, game);
                }
                return;
            }
            if (System.getenv("NIGHTSIEGE_SIM") != null) {
                simScene(context, game);
                return;
            }

            if (!shaderRun) {
                bossScene(context, game);
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
                nightHoldScene(context, game);
                besiegerScene(context, game);   // again since 27 October 2026: more sappers, craters, free ladder ways
                campScene(context, game);
                machineScene(context, game);
                com.mki.nightsiege.game.TrapService.mortarsAnyTime = true;   // the scenes below test by day
                mortarScene(context, game);
                mannedScene(context, game);
                alchemistScene(context, game);
                spotterScene(context, game);
                droneScene(context, game);
                nightStatsScene(context);
                // ladderTargetScene and towerScene are done (ladders fine, towers 2 volleys); kept for later checks.
                // besiegerScene is done (ladders up, one hole per side); kept for later checks.
                summonerScene(context, game);
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
            if (EventAtmosphere.NETHER_RIFT.equals(event) || EventAtmosphere.ECLIPSE.equals(event)) {
                game.getServer().runCommand("tp @a %d.5 %d %d.5 -90 8".formatted(home.getX() + 2, home.getY(), rows[1]));
                context.waitTicks(10);
                context.takeScreenshot("mobs4_" + event.toLowerCase(Locale.ROOT));
            }
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
                for (String id : new String[]{"warrior_drill", "w_endurance", "champion_bane", "w_combo", "w_combo",
                        "keen_edge", "w_frenzy", "w_bladefrenzy", "w_evasion", "execute", "w_chain_execute"}) {
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
                for (String id : new String[]{"archer_focus", "a_quiver", "heavy_bolts", "a_netbolt", "a_netbolt",
                        "a_shackle", "x_mark", "a_hunternet", "a_reloadrush", "x_hunt", "x_pack"}) {
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
            for (String id : new String[]{"warrior_drill", "champion_bane", "bloodlust"}) {
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
    private static int yard(ClientGameTestContext context, TestSingleplayerContext game, int cx, int cz, int out, int high) {
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
     * The war camp (27 October 2026): built, looked at by day and by night, attacked (class
     * cards, kit, grace), its workbench destroyed (it burns down and goes), then a second camp
     * where the defender falls (the camp wins, the machines come for sure).
     */
    private static void campScene(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 220;
        int cz = pos.getZ() - 60;
        int cy = yard(context, game, cx, cz, 30, 16);
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode creative @p");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            manager.camp().testCamp(level, new BlockPos(cx, cy, cz), 0, 999_999L);
            System.out.println("[Night Siege test] camp built at " + cx + " " + cy + " " + cz + " guards="
                    + manager.camp().testGuards(level) + " bench=" + (manager.camp().testBench(level) != null));
        });
        context.waitTicks(40);
        // From outside the gate (south), a little above.
        camera(game, cx + 6.5, cy + 13, cz + 30.5, cx + 0.5, cy + 1, cz + 0.5);
        context.runOnClient(client -> hud(client, false));
        context.waitTicks(60);
        context.takeScreenshot("camp_gate");
        camera(game, cx - 25.5, cy + 22, cz - 21.5, cx + 0.5, cy, cz + 0.5);
        context.waitTicks(40);
        context.takeScreenshot("camp_above");
        camera(game, cx + 2.5, cy + 3, cz + 9.5, cx + 0.5, cy + 1.5, cz + 0.5);
        context.waitTicks(30);
        context.takeScreenshot("camp_workshop");
        game.getServer().runCommand("time set 18000");
        camera(game, cx + 6.5, cy + 13, cz + 30.5, cx + 0.5, cy + 1, cz + 0.5);
        context.waitTicks(40);
        context.takeScreenshot("camp_night");
        context.runOnClient(client -> hud(client, true));

        // The attack: a signed-up defender walks through the gate.
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 60 255 true");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                cx, cy, cz + 9, cx, cy + 1, cz));
        context.waitTicks(30);
        context.takeScreenshot("camp_fight_cards");
        closeScreen(context);
        context.waitTicks(20);
        context.takeScreenshot("camp_fight");
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            System.out.println("[Night Siege test] camp fight: fighter=" + manager.camp().isFighter(player)
                    + " kit=" + manager.kits().hasStash(player) + " defending=" + manager.isDefending(player)
                    + " guards=" + manager.camp().testGuards(level));
        });
        context.waitTicks(220);   // past the grace: the guards come
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            int targeting = 0;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (com.mki.nightsiege.game.SiegeCamp.isCampMob(e) && e instanceof net.minecraft.world.entity.Mob mob
                        && mob.getTarget() == player) {
                    targeting++;
                }
            }
            System.out.println("[Night Siege test] camp after grace: guards on the player=" + targeting
                    + " hp=" + Math.round(player.getHealth()));
            net.minecraft.world.entity.LivingEntity bench = manager.camp().testBench(level);
            if (bench != null) {
                bench.invulnerableTime = 0;
                bench.hurtServer(level, level.damageSources().playerAttack(player), 120f);
                System.out.println("[Night Siege test] camp bench after a blow: " + Math.round(bench.getHealth()) + "/"
                        + Math.round(bench.getMaxHealth()));
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
                    + " kit=" + manager.kits().hasStash(player));
        });
        context.waitTicks(100);
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            System.out.println("[Night Siege test] camp gone=" + (manager.save().camp == null) + " palisade="
                    + level.getBlockState(new BlockPos(cx + 15, cy + 1, cz)).getBlock()
                    + " guards left=" + manager.camp().testGuards(level));
        });

        // A second camp: the defender falls there, the camp wins.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            manager.camp().testCamp(level, new BlockPos(cx, cy, cz), 1, 999_998L);
        });
        context.waitTicks(20);
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5", cx, cy, cz + 7));
        context.waitTicks(30);
        closeScreen(context);
        onServer(context, (player, manager) -> {
            player.invulnerableTime = 0;
            player.hurtServer((net.minecraft.server.level.ServerLevel) player.level(),
                    player.level().damageSources().generic(), 500f);
        });
        context.waitTicks(20);
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] camp won: alive=" + player.isAlive() + " hp=" + Math.round(player.getHealth())
                    + " machinesDay=" + manager.save().machinesDay + " camp gone=" + (manager.save().camp == null)
                    + " kit=" + manager.kits().hasStash(player));
            manager.save().machinesDay = -1L;
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
        // One struck down.
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            java.util.List<java.util.UUID> boxes = manager.machines().testHitboxes();
            if (!boxes.isEmpty() && level.getEntity(boxes.get(0)) instanceof net.minecraft.world.entity.LivingEntity box) {
                box.invulnerableTime = 0;
                box.hurtServer(level, level.damageSources().playerAttack(player), 5000f);
            }
            System.out.println("[Night Siege test] machines after one struck down: standing=" + manager.machines().standing());
        });
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
     * The boss nights (Simon, 25 October 2026): each of the four bosses in a walled yard,
     * photographed, left to fight for a while (its abilities logged), then brought down.
     * The lich's soul shield must hold until its crystals are broken; the warlord's beast
     * falls first, then the warlord. The boss's fall wins the night.
     */
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
                    // Hold the boss still for the photo, close to the camera.
                    boss.setNoAi(true);
                    boss.teleportTo(cx + 0.5, cy, cz - 4.5);
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
            for (String id : new String[]{"s_soulbond", "s_command", "s_packcall", "s_tamer", "s_tamer", "s_alpha", "s_howl",
                    "s_wildness", "s_wild_hunt"}) {
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
            for (String id : new String[]{"s_soulbond", "d_raise", "d_soulcatcher", "d_soulcatcher", "d_swarm", "d_shards",
                    "d_soulburn", "d_unholy", "d_army"}) {
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
        // The ability bar of the soulcatcher (27 October 2026): his four spells on R, G, Y, C; the swarm twice:
        // the second cast must wait for its 6 s.
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
            boolean shield = c.castSpell(player, "shield");   // the soulcatcher's spells since 26 October 2026
            System.out.println("[Night Siege test] summoner dead souls before=" + before + " after=" + c.soulsOf(player.getUUID())
                    + " (soul shield: 2) cast=" + shield + " absorption=" + player.getAbsorptionAmount()
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
                {"ARCHER", "lichtpfeile:1", "kletterpfeile:1", "sturmpfeile:1", "tarnumhang:1", "koecher:1",
                        "zielfernrohr:1", "lenkpfeile:1", "lockpfeil:1", "spektralpfeile:1"},
                {"GUARDIAN", "barrikaden:1", "signalhorn:1", "mauerkelle:1", "leibwaechter:1", "turmschild:1",
                        "leichtschild:1", "schildlaterne:1", "spiegelschild:1"},
                {"ENGINEER", "blendgranaten:3", "ersatzbatterie:1", "oelfass:1", "kupfergolem:1", "magnetfeld:1",
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
            boolean horn = com.mki.nightsiege.game.GainGear.testUse(player, "kampfhorn");
            System.out.println("[Night Siege test] B2 warrior: axe=" + axe + " banner=" + banner + " barricade=" + barricade
                    + " horn=" + horn + " state=" + com.mki.nightsiege.game.GainGear.debugState());
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
        onServer(context, (player, manager) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            String[] kinds = {"light", "climb", "storm"};
            for (int i = 0; i < kinds.length; i++) {
                net.minecraft.world.item.ItemStack ammo = com.mki.nightsiege.game.Gadgets.arrows(kinds[i], 0xFFFFFF, 1);
                net.minecraft.world.entity.projectile.arrow.Arrow arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(
                        level, player, ammo, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
                arrow.setPos(px + 1.5, py + 3.5, pz - 1.5 + i * 1.5);
                arrow.shoot(1, 0.0, 0, 2.5f, 0f);
                level.addFreshEntity(arrow);
                System.out.println("[Night Siege test] B2 arrow " + kinds[i] + ": " + com.mki.nightsiege.game.GainGear.debugArrow(arrow));
            }
            com.mki.nightsiege.game.GainGear.cycleQuiver(player);
            System.out.println("[Night Siege test] B2 quiver: offhand=" + player.getOffhandItem().getHoverName().getString());
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
            boolean oil = com.mki.nightsiege.game.GainGear.testUse(player, "oelfass");
            boolean flash = com.mki.nightsiege.game.GainGear.testUse(player, "blendgranate");
            System.out.println("[Night Siege test] B2 engineer: golems=" + com.mki.nightsiege.game.CopperHelper.count()
                    + " oil=" + oil + " flash=" + flash);
        });
        context.waitTicks(30);
        context.takeScreenshot("b2_engineer");
        onServer(context, (player, manager) -> {
            System.out.println("[Night Siege test] B2 engineer later: " + com.mki.nightsiege.game.GainGear.debugState());
            manager.abort(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
        });
        game.getServer().runCommand("kill @e[type=minecraft:zombie]");
        context.waitTicks(10);

        // ---- guardian: tower shield, mirror, chain ----
        b2Night(context, core, "GUARDIAN");
        inventoryShot(context, "b2_kit_guardian");
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.SecondUlts.fire(player);
            boolean signal = com.mki.nightsiege.game.GainGear.testUse(player, "signalhorn");
            boolean trowel = com.mki.nightsiege.game.GainGear.testUse(player, "mauerkelle");
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
    }

    /** Something to do on the integrated server with the test player and the siege manager. */
    private interface OnServer {
        void run(net.minecraft.server.level.ServerPlayer player, com.mki.nightsiege.game.SiegeManager manager);
    }

    /** Shows or hides the HUD (for clean photos). */
    private static void hud(net.minecraft.client.Minecraft client, boolean shown) {
        if (client.gui.hud.isHidden() == shown) {
            client.gui.hud.toggle();
        }
    }

    private static void onServer(ClientGameTestContext context, OnServer action) {
        context.runOnClient(client -> {
            net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
            server.execute(() -> {
                if (!server.getPlayerList().getPlayers().isEmpty()) {
                    action.run(server.getPlayerList().getPlayers().get(0), com.mki.nightsiege.game.SiegeManager.get());
                }
            });
        });
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
            for (String id : nodes) {
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

    private static void closeScreen(ClientGameTestContext context) {
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
                "g_shieldthrow", "g_thorns", "g_boomerang", "g_laststand", "shield_charge", "g_debris");
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
                "g_lightbringer", "g_wellspring", "g_grace", "g_sanctuary", "g_lightcircle");
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
                "mortar_tower", "mortar_tower", "e_shrapnel_mine", "e_chain_reaction", "e_blastpower", "bombardment",
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

    /** The alchemist (Simon, 26 October 2026): the potion menu, then a healing zone on the ground. */
    private static void alchemistScene(ClientGameTestContext context, TestSingleplayerContext game) {
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.switchTo(com.mki.nightsiege.core.SkillClass.ENGINEER);
            own.levels.clear();
            own.levels.put("potion_tower", 3);
            own.levels.put("e_acid", 1);
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
            int slot = manager.abilitySlotOf(player, "slow");
            System.out.println("[Night Siege test] alchemist slow on bar slot " + slot);
            manager.onCoreAction(player, "slot:" + Math.max(0, slot));   // its key throws it at once
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
        onServer(context, (player, manager) -> {
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.switchTo(com.mki.nightsiege.core.SkillClass.ENGINEER);
        });
        context.waitTicks(3);
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
