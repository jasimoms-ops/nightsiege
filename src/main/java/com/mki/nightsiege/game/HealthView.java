package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The medic and the warden see how much life the other defenders have (Simon, 26 October
 * 2026): "♥ 14/20" under the name of each defender, only in a siege night. The server tells
 * each healer whom to show; the client draws it (HealthViewClient).
 */
public final class HealthView {

    /** What each player was told last (the defenders' ids, "" for nothing). */
    private static final Map<UUID, String> SENT = new HashMap<>();

    private HealthView() {
    }

    /** True for the guardians whose way is blessing: medic and warden. */
    public static boolean healer(SiegeManager m, ServerPlayer player) {
        PlayerSkills skills = m.save().skillsOf(player.getUUID().toString());
        if (skills.clazz() != SkillClass.GUARDIAN) {
            return false;
        }
        String sub = m.skillTree().subclassOf(skills);
        return SkillTree.SUB_MEDIC.equals(sub) || SkillTree.SUB_WARDEN.equals(sub);
    }

    /** Once a second: who should see which defenders' life. */
    public static void tick(MinecraftServer server, SiegeManager m) {
        StringBuilder ids = new StringBuilder();
        if (m.isActive()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (m.isDefending(p)) {
                    if (!ids.isEmpty()) {
                        ids.append(',');
                    }
                    ids.append(p.getUUID());
                }
            }
        }
        boolean refresh = server.overworld().getGameTime() % 200 == 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String show = m.isActive() && m.isDefending(p) && healer(m, p) ? ids.toString() : "";
            String last = SENT.getOrDefault(p.getUUID(), "");
            if (!show.equals(last) || (refresh && !show.isEmpty())) {
                SENT.put(p.getUUID(), show);
                ServerPlayNetworking.send(p, new CoreNetworking.ScreenPayload("healthview", show));
            }
        }
    }

    /** A player joined: tell them again from scratch. */
    public static void forget(UUID player) {
        SENT.remove(player);
    }
}
