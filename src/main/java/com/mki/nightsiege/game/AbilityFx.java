package com.mki.nightsiege.game;

import com.mki.nightsiege.core.FxColors;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Z4 (Simon 12.11.: "alle Animationen aussagekräftiger und auffälliger, größer"): the one place the
 * abilities' new effects start. The server only says what happened (cast, ultimate, ring with the real
 * radius, monsters struck) with the class colours; each client draws it in {@code AbilityFxClient} -
 * fully, or not at all with "Effekte: reduziert" (= the old look, which stays as it was). So the client
 * option, the particle budget and the camera shake for the caster alone live where the picture is made.
 * Only looks: no damage, no pauses, no values.
 */
public final class AbilityFx {

    /** Clients this close to the place get the effect (blocks). */
    static final double VIEW = 64.0;

    /** Player melee swings running now (Player.attack): their hits are no ability hits. */
    private static int melee;
    /** Monsters struck by abilities this tick, per player: entity ids. */
    private static final Map<ServerPlayer, List<Integer>> HITS = new LinkedHashMap<>();
    /** Sent payloads by kind, for the tests. */
    private static final int[] SENT = new int[4];

    private AbilityFx() {
    }

    /** A key of the player fired (its pause started): arm, burst, and for an ultimate the big package. */
    public static void cast(ServerPlayer player, boolean ult) {
        send(player, ult ? CoreNetworking.FxPayload.ULT : CoreNetworking.FxPayload.CAST, player.position(), 0f, List.of());
    }

    /** An area ability went off: its real radius as a ring on the ground. */
    public static void area(ServerPlayer player, Vec3 center, double radius) {
        if (player == null || center == null || !(radius > 0.3)) {
            return;
        }
        send(player, CoreNetworking.FxPayload.RING, center, (float) Math.min(48.0, radius), List.of());
    }

    /** Player.attack starts / ends (PlayerSweepMixin): hits in between are melee, not abilities. */
    public static void meleeStart(Player player) {
        if (!player.level().isClientSide()) {
            melee++;
        }
    }

    public static void meleeEnd(Player player) {
        if (!player.level().isClientSide() && melee > 0) {
            melee--;
        }
    }

    /**
     * After damage (AFTER_DAMAGE): a monster hit by a player's ability flashes. Melee swings and arrows
     * are left out (they have their own look); collected and sent once a tick.
     */
    public static void afterDamage(LivingEntity entity, DamageSource source) {
        if (melee > 0 || entity instanceof Player || !(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile) {
            return;
        }
        List<Integer> ids = HITS.computeIfAbsent(player, p -> new ArrayList<>());
        if (ids.size() < 64) {
            ids.add(entity.getId());
        }
    }

    /** End of every server tick: the hits of the tick go out. */
    public static void tick(MinecraftServer server) {
        melee = 0;   // no swing runs between ticks; a swing that threw cannot block the flashes for good
        if (HITS.isEmpty()) {
            return;
        }
        List<Map.Entry<ServerPlayer, List<Integer>>> all = new ArrayList<>(HITS.entrySet());
        HITS.clear();
        for (Map.Entry<ServerPlayer, List<Integer>> e : all) {
            if (!e.getKey().isRemoved()) {
                send(e.getKey(), CoreNetworking.FxPayload.HITS, e.getKey().position(), 0f, e.getValue());
            }
        }
    }

    private static void send(ServerPlayer player, int kind, Vec3 at, float radius, List<Integer> ids) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int[] colors = colors(player);
        CoreNetworking.FxPayload payload = new CoreNetworking.FxPayload(kind, player.getId(), at, radius, colors[0], colors[1], ids);
        double view = VIEW + radius;
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(at) <= view * view && ServerPlayNetworking.canSend(viewer, CoreNetworking.FxPayload.TYPE)) {
                ServerPlayNetworking.send(viewer, payload);
                SENT[kind]++;
            }
        }
    }

    /** Class colour and subclass accent of the player. */
    static int[] colors(ServerPlayer player) {
        SiegeManager m = SiegeManager.get();
        SkillClass clazz = null;
        String sub = null;
        if (m != null) {
            PlayerSkills own = m.effectiveSkills(player);
            if (own != null) {
                clazz = own.clazz();
                sub = m.skillTree().subclassOf(own);
            }
        }
        return new int[]{FxColors.base(clazz), FxColors.accent(clazz, sub)};
    }

    /** Tests: payloads sent so far of a kind (0 cast, 1 ult, 2 ring, 3 hits). */
    public static int sentForTests(int kind) {
        return SENT[kind];
    }

    public static void reset() {
        HITS.clear();
        melee = 0;
    }
}
