package com.mki.nightsiege.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/**
 * Temporary, CI only: creates a world, photographs the night sky, starts a
 * blood moon test siege, photographs it again and once more after it ends.
 */
public class SkyScreenshotTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            TestServerContext server = world.getServer();
            for (String command : new String[]{
                    "gamerule advance_time false",
                    "gamerule advance_weather false",
                    "weather clear",
                    "time set 14800",
                    "gamemode spectator @a",
                    "execute as @a at @s run tp @s ~ ~20 ~"}) {
                server.runCommand(command);
            }
            context.waitTicks(60);
            world.getConnection().waitForChunksRender();
            context.getInput().pressKey(GLFW.GLFW_KEY_F1);

            shots(context, "1_normal");

            server.runCommand("execute as @a at @s run nightsiege test 1 blutmond");
            context.waitTicks(220);
            shots(context, "2_blutmond");

            server.runCommand("nightsiege stop");
            context.waitTicks(220);
            context.getInput().lookAt(-90f, -30f);
            context.waitTicks(3);
            context.takeScreenshot("3_danach_mond");

            context.getInput().pressKey(GLFW.GLFW_KEY_F1);
        }
    }

    private static void shots(ClientGameTestContext context, String prefix) {
        float[][] views = {{-90f, -30f}, {-90f, -12f}, {90f, -8f}, {0f, -70f}};
        String[] names = {"mond", "mond_horizont", "westen", "oben"};
        for (int i = 0; i < views.length; i++) {
            context.getInput().lookAt(views[i][0], views[i][1]);
            context.waitTicks(3);
            context.takeScreenshot(prefix + "_" + names[i]);
        }
    }
}
