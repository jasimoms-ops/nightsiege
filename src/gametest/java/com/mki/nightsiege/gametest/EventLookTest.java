package com.mki.nightsiege.gametest;

import com.mki.nightsiege.game.EventAtmosphere;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Starts the real client, loads a world and photographs every event look:
 * sky, light and the event textures of the four common monsters.
 */
public class EventLookTest implements FabricClientGameTest {

    private static final String[] EVENTS = {
            EventAtmosphere.ECLIPSE, EventAtmosphere.FROST, EventAtmosphere.NETHER_RIFT, EventAtmosphere.FOG,
            EventAtmosphere.THUNDER, EventAtmosphere.BLOOD_MOON, EventAtmosphere.AURORA};

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder()
                .adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            game.getConnection().waitForChunksRender();
            // Every mixin of the mod (and all others) must apply cleanly.
            MixinEnvironment.getCurrentEnvironment().audit();

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

            for (int i = 0; i < EVENTS.length; i++) {
                String event = EVENTS[i];
                boolean night = EventAtmosphere.BLOOD_MOON.equals(event) || EventAtmosphere.AURORA.equals(event);
                if (night) {
                    game.getServer().runCommand("time set 14600");
                    context.getInput().lookAt(EventAtmosphere.AURORA.equals(event) ? -150.0f : -90.0f, -22.0f);
                }
                context.runOnClient(client -> EventAtmosphere.setClient(event, 1));
                context.waitTicks(30);
                context.takeScreenshot("event_%02d_%s".formatted(i + 1, event.toLowerCase(java.util.Locale.ROOT)));
            }
            context.runOnClient(client -> EventAtmosphere.setClient("", 1));
            context.waitTicks(30);
            context.takeScreenshot("event_99_night_normal");

            // The same once more with the layer meant for shader packs.
            System.setProperty("nightsiege.overlay", "true");
            game.getServer().runCommand("time set 1500");
            context.getInput().lookAt(-90.0f, -12.0f);
            for (int i = 0; i < EVENTS.length; i++) {
                String event = EVENTS[i];
                boolean night = EventAtmosphere.BLOOD_MOON.equals(event) || EventAtmosphere.AURORA.equals(event);
                if (night) {
                    game.getServer().runCommand("time set 14600");
                    context.getInput().lookAt(EventAtmosphere.AURORA.equals(event) ? -150.0f : -90.0f, -22.0f);
                }
                context.runOnClient(client -> EventAtmosphere.setClient(event, 1));
                context.waitTicks(30);
                context.takeScreenshot("overlay_%02d_%s".formatted(i + 1, event.toLowerCase(java.util.Locale.ROOT)));
            }
            System.setProperty("nightsiege.overlay", "false");
        }
    }
}
