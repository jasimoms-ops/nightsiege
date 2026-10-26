package com.mki.nightsiege.client;

import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.TowerBlock;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * The client side of a manned tower (Simon, 26 October 2026): sitting in one, the attack
 * button fires it. The server is told when the button goes down and up; meanwhile the
 * attack itself is held back (MinecraftAttackMixin), so nothing in front gets hit or mined.
 */
public final class MannedClient {

    private static boolean seated;
    private static boolean sentDown;

    private MannedClient() {
    }

    /** True while the player sits in an arrow turret or tesla coil. */
    public static boolean seated() {
        return seated;
    }

    /** Start of every client tick. */
    public static void tick(Minecraft client) {
        seated = client.player != null && client.level != null && inTower(client);
        boolean down = seated && client.gui.screen() == null && client.options.keyAttack.isDown();
        if (down != sentDown && ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
            ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload(down ? "man:1" : "man:0"));
            sentDown = down;
        }
    }

    /** A new connection: nothing held. */
    public static void reset() {
        seated = false;
        sentDown = false;
    }

    private static boolean inTower(Minecraft client) {
        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof ArmorStand stand) || !stand.isMarker()) {
            return false;
        }
        BlockPos below = BlockPos.containing(stand.getX(), stand.getY() - 0.5, stand.getZ());
        return client.level.getBlockState(below).getBlock() instanceof TowerBlock;
    }
}
