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

            if (!shaderRun) {
                series(context, game, "event");
                mobLineup(context, game);
                context.runOnClient(client -> EventAtmosphere.setClient("", 1));
                context.waitTicks(30);
                context.takeScreenshot("event_99_night_normal");
                warriorWindow(context, game);
                doomScene(context);
                baseScene(context, game);
                armorScene(context, game);
                guardianScene(context);
                engineerScene(context);
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
        // At night (undead burn by day), seen with night vision so the textures show.
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("effect give @a minecraft:night_vision 600 0 true");
        game.getServer().runCommand("tp @a %d.5 %d %d.5 -90 6".formatted(home.getX() + 2, home.getY(), home.getZ()));
        context.waitTicks(20);
        for (String event : EVENTS) {
            if (EventAtmosphere.AURORA.equals(event)) {
                continue;
            }
            context.runOnClient(client -> EventAtmosphere.setClient(event, 1));
            context.waitTicks(30);
            context.takeScreenshot("mobs_" + event.toLowerCase(Locale.ROOT));
        }
        game.getServer().runCommand("effect clear @a minecraft:night_vision");
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
        context.runOnClient(client -> {
            net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
            server.execute(() -> {
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                com.mki.nightsiege.game.SiegeManager manager = com.mki.nightsiege.game.SiegeManager.get();
                manager.onCoreAction(player, "class:WARRIOR");
                manager.onCoreAction(player, "dev:400");
                for (String id : new String[]{"warrior_drill", "w_endurance", "champion_bane", "keen_edge", "keen_edge",
                        "w_combo", "w_frenzy", "w_bladefrenzy", "w_evasion", "execute", "w_chain_execute"}) {
                    manager.onBuySkill(player, id);
                }
                manager.onCoreAction(player, "open");
            });
        });
        context.waitTicks(40);
        context.takeScreenshot("ui_warrior_blade");
        selectNode(context, "w_bladefrenzy");
        context.takeScreenshot("ui_warrior_selected");
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
        context.waitTicks(5);
    }

    /** Hovering a node marks with a red cross what learning it would shut. */
    private static void doomScene(ClientGameTestContext context) {
        openTree(context, "WARRIOR", "warrior_drill");
        selectNode(context, "w_bladedance");
        context.takeScreenshot("ui_doom_way");
        openTree(context, "WARRIOR", "warrior_drill", "champion_bane");
        zoomTo(context, "WARRIOR", -150.0, -40.0, 0.6);
        selectNode(context, "keen_edge");
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
                    worn.append(st.getItem()).append(st.isEnchanted() ? "*" : "").append(' ');
                }
                System.out.println("[Night Siege test] kit " + lv + ": " + worn + "points="
                        + manager.save().skillsOf(player.getUUID().toString()).earned);
            }
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
        // A closer look at the first four.
        game.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
        for (int i = 0; i < 4; i++) {
            game.getServer().runCommand(String.format(java.util.Locale.ROOT,
                    "summon minecraft:armor_stand %d %d %.1f {CustomName:\"%s\",ShowArms:1b,Rotation:[-90f,0f],"
                    + "equipment:{head:{id:\"minecraft:diamond_helmet\"},chest:{id:\"minecraft:leather_chestplate\"},"
                    + "legs:{id:\"minecraft:golden_leggings\"},feet:{id:\"minecraft:netherite_boots\"}}}",
                    x - 4, y, z - 1.5 + i, rows[0][i]));
        }
        context.waitTicks(20);
        context.getInput().lookAt(90.0f, 14.0f);
        context.waitTicks(10);
        context.takeScreenshot("armor_warriors_close");
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
            game.getServer().runCommand("item replace entity @p armor.%s with minecraft:%s".formatted(p[0], p[1]));
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

    /** Something to do on the integrated server with the test player and the siege manager. */
    private interface OnServer {
        void run(net.minecraft.server.level.ServerPlayer player, com.mki.nightsiege.game.SiegeManager manager);
    }

    private static void onServer(ClientGameTestContext context, OnServer action) {
        context.runOnClient(client -> {
            net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
            server.execute(() -> action.run(server.getPlayerList().getPlayers().get(0),
                    com.mki.nightsiege.game.SiegeManager.get()));
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
    private static void zoomTo(ClientGameTestContext context, String clazz, double x, double y, double zoom) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("VIEWS");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, double[]> views = (java.util.Map<String, double[]>) field.get(null);
                views.put(clazz, new double[]{x, y, zoom});
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
                "blast_mine", "blast_mine", "e_shrapnel_mine", "e_chain_reaction", "e_blastpower", "bombardment",
                "e_carpet");
        selectNode(context, "e_chain_reaction");
        context.takeScreenshot("ui_engineer_chem");
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
            place(level, manager, player, mine, com.mki.nightsiege.core.TrapKind.MINE);
            place(level, manager, player, mine2, com.mki.nightsiege.core.TrapKind.MINE);
            com.mki.nightsiege.game.RemoteItem.handler.use(player, turret);   // overcharge
            com.mki.nightsiege.game.RemoteItem.handler.use(player, null);     // mark what is in sight
        });
        context.waitTicks(6);
        context.takeScreenshot("engineer_remote");
        onServer(context, (player, manager) -> {
            player.setShiftKeyDown(true);
            com.mki.nightsiege.game.RemoteItem.handler.use(player, player.blockPosition().offset(4, 0, 0));
            player.setShiftKeyDown(false);
        });
        context.waitTicks(4);
        context.takeScreenshot("engineer_detonate");
        context.waitTicks(20);
        onServer(context, (player, manager) -> manager.onCoreAction(player, "ult"));
        context.waitTicks(6);
        context.takeScreenshot("engineer_bombardment");
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
