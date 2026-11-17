package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.CoreScreen;
import com.mki.nightsiege.client.GatherClientState;
import com.mki.nightsiege.game.CoreNetworking;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Tor 12: the card "Next siege" of the open core window (tab STATS) in English and German at GUI 2 and 3, one picture
 * each, checked: no line over the base line, every participant of the gathering by name or in "+N", the footer whole.
 * Usable from any scene with the window open (kapitel; sammeln with the real states from the server).
 */
final class CoreCardCheck {

    private CoreCardCheck() {
    }

    /** The four looks of the open window with the card checks. {@code gathering}: the list must account for everyone. */
    static int matrix(ClientGameTestContext context, String name, boolean gathering, BiConsumer<Boolean, String> check) {
        return LayoutCheck.matrix(context, name, true, check, tag -> {
            CoreScreen.testNextSiegeGap = -1000;
            CoreScreen.testGatherListed = -1;
            context.waitTicks(3);
            int gap = CoreScreen.testNextSiegeGap;
            check.accept(gap >= 0, tag + ": no line of the next-siege card over the base line (gap " + gap + " px)");
            if (gathering) {
                int listed = CoreScreen.testGatherListed;
                int all = context.computeOnClient(client -> GatherClientState.entries.size());
                check.accept(listed == all, tag + ": every participant listed by name or in +N (" + listed + " of " + all + ")");
            }
        });
    }

    /**
     * Client only (no server packet): a gathering with the own player and {@code others} more, the first {@code ready}
     * of all ready. For pictures of a state the server does not show by itself; {@link #clear} afterwards.
     */
    static void fake(ClientGameTestContext context, int phase, int seconds, int others, int ready) {
        context.runOnClient(client -> {
            List<CoreNetworking.GatherEntry> list = new ArrayList<>();
            list.add(new CoreNetworking.GatherEntry(client.player.getName().getString(), client.player.getUUID(), ready > 0));
            String[] names = {"Brunhild_der_Lange", "Kai", "Mira", "Totenwaechter99", "Olaf", "Ysolde", "Jasper", "Hagen", "Lin"};
            for (int i = 0; i < others; i++) {
                list.add(new CoreNetworking.GatherEntry(names[i % names.length], UUID.nameUUIDFromBytes(("t12-" + i).getBytes()),
                        i + 1 < ready));
            }
            GatherClientState.entries = List.copyOf(list);
            GatherClientState.seconds = seconds;
            GatherClientState.selfReady = ready > 0;
            GatherClientState.receivedAt = client.level == null ? 0 : client.level.getGameTime();
            GatherClientState.phase = phase;
        });
        context.waitTicks(3);
    }

    static void clear(ClientGameTestContext context) {
        context.runOnClient(client -> GatherClientState.reset());
        context.waitTicks(3);
    }
}
