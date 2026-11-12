package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.AbilityFxClient;
import com.mki.nightsiege.client.FxSettings;
import com.mki.nightsiege.game.CoreNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Z4 load test (Chef 12.11.: 6 players and 120 monsters may cost at most 10 % FPS). Six stand-in casters
 * (husks; the client draws their effects exactly as for other players) fire casts, ultimates, rings, hit
 * flashes and boosted old particles in a loop in front of the camera, with 120 more husks in sight; 30 s
 * "reduziert" then 30 s "voll". Per tick: wall time (tick and frame run in step in the client gametest),
 * particles alive, particles the effects started. Output: FXLAST;modus;fps_mittel;frame_p95_ms;partikel_tick_max.
 */
final class FxLastScenes {

    private static final String TAG = "ns_fxlast";
    private static final int WARM = 60;
    private static final int TICKS = 600;
    private static final int[] COLORS = {0xE03030, 0x3070F0, 0x30C040, 0xE0C020, 0xA030D0, 0x30D0D0};

    private static boolean hooked;
    private static boolean running;
    private static long tickNo;
    private static int[] casters = new int[0];
    private static int[] targets = new int[0];

    private FxLastScenes() {
    }

    private static void log(String line) {
        System.out.println("[Night Siege test] fxlast " + line);
    }

    private static void fire(int kind, int src, Vec3 at, float radius, int color, List<Integer> ids) {
        AbilityFxClient.receive(new CoreNetworking.FxPayload(kind, src, at, radius, color, color ^ 0x808080, ids));
    }

    /** One client tick of the six casters' loop. */
    private static void drive(Minecraft mc) {
        if (!running || mc.level == null || casters.length == 0) {
            return;
        }
        tickNo++;
        for (int s = 0; s < casters.length; s++) {
            net.minecraft.world.entity.Entity e = mc.level.getEntity(casters[s]);
            if (e == null) {
                continue;
            }
            Vec3 at = e.position();
            int color = COLORS[s];
            long t = tickNo + s * 7L;
            if (t % 100 == 0) {
                fire(CoreNetworking.FxPayload.ULT, casters[s], at, 6f, color, List.of());
            } else if (t % 20 == 0) {
                fire(CoreNetworking.FxPayload.CAST, casters[s], at, 0f, color, List.of());
            }
            if (t % 40 == 10) {
                fire(CoreNetworking.FxPayload.RING, casters[s], at, 5f, color, List.of());
            }
            if (t % 20 == 5 && targets.length > 0) {
                List<Integer> ids = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    ids.add(targets[(int) ((t / 20 * 20 + i + s * 20L) % targets.length)]);
                }
                fire(CoreNetworking.FxPayload.HITS, casters[s], at, 0f, color, ids);
            }
            if (t % 10 == 3) {   // the abilities' own old particles: 30 flames, drawn bigger when full
                fire(CoreNetworking.FxPayload.OPEN, casters[s], at, 0f, 0, List.of());
                ClientboundLevelParticlesPacket p = new ClientboundLevelParticlesPacket(ParticleTypes.FLAME, true, true,
                        at.x, at.y + 1, at.z, 0.6f, 0.6f, 0.6f, 0.05f, 30);
                if (!AbilityFxClient.boost(p)) {
                    for (int i = 0; i < 30; i++) {   // what Minecraft draws for the packet
                        mc.level.addParticle(ParticleTypes.FLAME, true, true, at.x + mc.level.getRandom().nextGaussian() * 0.6,
                                at.y + 1 + mc.level.getRandom().nextGaussian() * 0.6, at.z + mc.level.getRandom().nextGaussian() * 0.6, 0, 0.03, 0);
                    }
                }
                fire(CoreNetworking.FxPayload.CLOSE, casters[s], at, 0f, 0, List.of());
            }
        }
    }

    private static int alive(Minecraft mc) {
        String s = mc.particleEngine.countParticles();
        int n = 0;
        for (int i = 0; i < s.length() && Character.isDigit(s.charAt(i)); i++) {
            n = n * 10 + (s.charAt(i) - '0');
        }
        return n;
    }

    /** {fps mean, frame p95 ms, particles alive max, started per tick, mean ms}. */
    private static double[] measure(ClientGameTestContext context, boolean full) {
        context.runOnClient(client -> {
            FxSettings.setFull(full);
            client.particleEngine.clearParticles();
        });
        running = true;
        context.waitTicks(WARM);
        long[] ms = new long[TICKS];
        final int[] peak = new int[1];
        final int startedBefore = AbilityFxClient.drawnForTests();
        for (int i = 0; i < TICKS; i++) {
            long t0 = System.nanoTime();
            context.waitTicks(1);
            ms[i] = System.nanoTime() - t0;
            if (i % 5 == 0) {
                context.runOnClient(client -> peak[0] = Math.max(peak[0], alive(client)));
            }
        }
        running = false;
        int started = AbilityFxClient.drawnForTests() - startedBefore;
        long sum = 0;
        for (long v : ms) {
            sum += v;
        }
        long[] sorted = ms.clone();
        Arrays.sort(sorted);
        double mean = sum / 1e6 / TICKS;
        double p95 = sorted[(int) (TICKS * 0.95)] / 1e6;
        context.waitTicks(40);
        return new double[]{1000.0 / mean, p95, peak[0], started / (double) TICKS, mean};
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("kill @e[tag=" + TAG + "]");
        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            if (!hooked) {
                hooked = true;
                ClientTickEvents.END_CLIENT_TICK.register(FxLastScenes::drive);
            }
        });
        context.getInput().lookAt(-90.0f, 15.0f);
        context.waitTicks(10);
        final double[][] pos = new double[1][];
        EventLookTest.onServer(context, (player, m) -> pos[0] = new double[]{player.getX(), player.getY(), player.getZ()});
        context.waitTicks(2);
        double px = pos[0][0];
        double py = pos[0][1];
        double pz = pos[0][2];
        String husk = "summon minecraft:husk %s %s %s {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"" + TAG + "\",\"%s\"]}";
        // six casters on a line 6 blocks in front (yaw -90 looks to +x), 120 monsters in rows behind them
        for (int s = 0; s < 6; s++) {
            game.getServer().runCommand(String.format(java.util.Locale.ROOT, husk, px + 6, py, pz - 7.5 + s * 3.0, "caster"));
        }
        for (int i = 0; i < 120; i++) {
            game.getServer().runCommand(String.format(java.util.Locale.ROOT, husk, px + 10 + (i / 20) * 2.0, py, pz - 9.5 + (i % 20) * 1.0, "target"));
        }
        context.waitTicks(10);
        final List<Integer> c = new ArrayList<>();
        final List<Integer> t = new ArrayList<>();
        EventLookTest.onServer(context, (player, m) -> {
            for (Mob h : ((ServerLevel) player.level()).getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(60),
                    e -> e.entityTags().contains(TAG))) {
                (h.entityTags().contains("caster") ? c : t).add(h.getId());
            }
        });
        context.waitTicks(2);
        casters = c.stream().mapToInt(Integer::intValue).toArray();
        targets = t.stream().mapToInt(Integer::intValue).toArray();
        log("casters=" + casters.length + " monsters=" + targets.length);
        double[] red = measure(context, false);
        double[] full = measure(context, true);
        for (boolean f : new boolean[]{false, true}) {
            double[] r = f ? full : red;
            System.out.println(String.format(java.util.Locale.ROOT, "FXLAST;%s;%.1f;%.1f;%d", f ? "voll" : "reduziert", r[0], r[1], (int) r[2]));
        }
        double loss = (red[0] - full[0]) / red[0] * 100.0;
        log(String.format(java.util.Locale.ROOT, "started per tick: reduziert %.1f voll %.1f; mean ms %.2f / %.2f; fps loss %.1f %% (limit 10) %s",
                red[3], full[3], red[4], full[4], loss, loss <= 10.0 ? "OK" : "OVER"));
        log("RESULT " + (casters.length == 6 && targets.length == 120 && loss <= 10.0 ? "1 of 1 ok" : "FAIL casters/monsters/loss"));
        game.getServer().runCommand("kill @e[tag=" + TAG + "]");
        context.runOnClient(client -> FxSettings.setFull(true));
        context.waitTicks(5);
    }
}
