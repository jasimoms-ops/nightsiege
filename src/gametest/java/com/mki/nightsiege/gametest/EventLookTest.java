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
                context.runOnClient(client -> EventAtmosphere.setClient("", 1));
                context.waitTicks(30);
                context.takeScreenshot("event_99_night_normal");
                warriorWindow(context, game);
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
        context.waitTicks(40);
        context.takeScreenshot("ui_archer_crossbow");
        context.waitTicks(5);
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
