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
        // Since 09.11. (Simon: in the dungeon the night's rules) also in a dungeon and in the camp: isDefending covers them.
        StringBuilder ids = new StringBuilder();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (m.isDefending(p)) {
                if (!ids.isEmpty()) {
                    ids.append(',');
                }
                ids.append(p.getUUID());
            }
        }
        boolean refresh = server.overworld().getGameTime() % 200 == 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String show = m.isDefending(p) && healer(m, p) ? ids.toString() : "";
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
        // Tor 3.5 (Simon 08.11.): the list for medics and wardens, in the siege, a war camp or a dungeon
        // (the playtest runs through these). The fallen of a camp or dungeon count as down.
        java.util.List<String> siege = new java.util.ArrayList<>();
        if (m.isActive()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (m.isDefending(p)) {
                    siege.add(p.getUUID().toString());
                }
            }
        }
        java.util.List<String> camp = ids(m.camp().fighterIds());
        java.util.List<String> dungeon = ids(m.expeditions().fighterIds());
        java.util.Map<java.util.List<String>, java.util.List<com.mki.nightsiege.core.HudSync.Mate>> mates = new java.util.IdentityHashMap<>();
        boolean refresh = server.overworld().getGameTime() % com.mki.nightsiege.core.HudSync.PARTY_REFRESH == 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String show = "";
            String self = p.getUUID().toString();
            java.util.List<String> group = healer(m, p)
                    ? com.mki.nightsiege.core.HudSync.groupOf(self, siege, camp, dungeon) : null;
            if (group != null) {
                com.mki.nightsiege.core.HudSync.Party party = new com.mki.nightsiege.core.HudSync.Party();
                java.util.List<com.mki.nightsiege.core.HudSync.Mate> all = mates.computeIfAbsent(group,
                        g -> matesOf(server, m, g, g == siege ? null : g == camp ? m.camp()::hasFallen : m.expeditions()::hasFallen));
                party.mates = com.mki.nightsiege.core.HudSync.party(self, all,
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

    private static java.util.List<String> ids(java.util.List<UUID> fighters) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (fighters != null) {
            for (UUID id : fighters) {
                out.add(id.toString());
            }
        }
        return out;
    }

    /** The online members of a group with their life; fallen ones (dead, or out of a camp or dungeon) at 0. */
    private static java.util.List<com.mki.nightsiege.core.HudSync.Mate> matesOf(MinecraftServer server, SiegeManager m,
            java.util.List<String> group, java.util.function.Predicate<UUID> fallen) {
        java.util.List<com.mki.nightsiege.core.HudSync.Mate> out = new java.util.ArrayList<>();
        for (String id : group) {
            UUID uuid = UUID.fromString(id);
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null) {
                continue;
            }
            boolean down = !p.isAlive() || p.isSpectator() || (fallen != null && fallen.test(uuid));
            out.add(new com.mki.nightsiege.core.HudSync.Mate(id, p.getName().getString(),
                    down ? 0f : p.getHealth(), p.getMaxHealth(), down ? 0f : p.getAbsorptionAmount()));
        }
        return out;
    }

    /** A player joined: tell them again from scratch. */
    public static void forget(UUID player) {
        SENT.remove(player);
        PARTY_SENT.remove(player);
    }
}
