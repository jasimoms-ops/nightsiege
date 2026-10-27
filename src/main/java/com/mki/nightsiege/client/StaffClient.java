package com.mki.nightsiege.client;

import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.SummonStaffItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * The summoner's staff (Simon, 27 October 2026): a left click is the attack (soul bolt on the
 * way of the dead, call strike on the way of the light), held it repeats; the right click
 * casts the spell picked with Fähigkeit 3. The staff does not hit or mine like a stick.
 */
public final class StaffClient {

    /** Ticks between two attacks sent while the button is held (the server keeps its own pause). */
    private static final int REPEAT = 10;
    private static long nextSend;

    private StaffClient() {
    }

    public static boolean holdsStaff(Minecraft client) {
        return client.player != null && client.player.getMainHandItem().getItem() instanceof SummonStaffItem;
    }

    /** The attack button went down with the staff in hand: the staff attacks instead of a hit. */
    public static boolean onAttack(Minecraft client) {
        if (!holdsStaff(client)) {
            return false;
        }
        send(client);
        return true;
    }

    /** Held: the attack again every half second. */
    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.gui.screen() != null || !holdsStaff(client)
                || !client.options.keyAttack.isDown()) {
            return;
        }
        if (client.level.getGameTime() >= nextSend) {
            send(client);
        }
    }

    private static void send(Minecraft client) {
        if (client.level != null) {
            nextSend = client.level.getGameTime() + REPEAT;
        }
        if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
            ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("staffhit"));
        }
    }
}
