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

    /** What each healer was told last for the party list (Tor 2), "" for nothing. */
    private static final Map<UUID, String> PARTY_SENT = new HashMap<>();

    /**
     * Tor 2 (Simon's play test 08.11., the medic: "Anzeige aller Mitspieler mit Leben"): every half second
     * each healer of the night (medic, warden, anyone with a healing ability) gets the other defenders'
     * names and life for a compact list at the edge of the screen; nobody else gets anything.
     */
    public static void tickParty(MinecraftServer server, SiegeManager m) {
        java.util.List<com.mki.nightsiege.core.HudSync.Mate> all = new java.util.ArrayList<>();
        if (m.isActive()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (m.isDefending(p)) {
                    all.add(new com.mki.nightsiege.core.HudSync.Mate(p.getUUID().toString(), p.getName().getString(),
                            p.isAlive() ? p.getHealth() : 0f, p.getMaxHealth(), p.getAbsorptionAmount()));
                }
            }
        }
        boolean refresh = server.overworld().getGameTime() % com.mki.nightsiege.core.HudSync.PARTY_REFRESH == 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String show = "";
            if (m.isActive() && m.isDefending(p) && (healer(m, p) || m.combatEffects().healsOthers(p))) {
                com.mki.nightsiege.core.HudSync.Party party = new com.mki.nightsiege.core.HudSync.Party();
                party.mates = com.mki.nightsiege.core.HudSync.party(p.getUUID().toString(), all,
                        com.mki.nightsiege.core.HudSync.PARTY_MAX);
                show = party.mates.isEmpty() ? "" : Json.toJson(party);
            }
            String last = PARTY_SENT.getOrDefault(p.getUUID(), "");
            if (!show.equals(last) || (refresh && !show.isEmpty())) {
                PARTY_SENT.put(p.getUUID(), show);
                if (ServerPlayNetworking.canSend(p, CoreNetworking.ScreenPayload.TYPE)) {
                    ServerPlayNetworking.send(p, new CoreNetworking.ScreenPayload("partyhealth", show));
                }
            }
        }
    }

    /** A player joined: tell them again from scratch. */
    public static void forget(UUID player) {
        SENT.remove(player);
        PARTY_SENT.remove(player);
    }
}
