package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.AbilityFxClient;
import com.mki.nightsiege.client.FxSettings;
import com.mki.nightsiege.core.AbilityKeys;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.game.AbilityFx;
import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Z4 (Simon 12.11.: "alle Animationen aussagekräftiger und auffälliger, größer"): every subclass fires
 * one ability and its ultimate, once with "Effekte: reduziert" (= the old look, "vorher") and once with
 * "voll" ("nachher"). Pictures effekte_&lt;sub&gt;_{faehigkeit|ult}_{vorher|nachher}; checks: the full
 * effects draw particles, the reduced ones none, the own ultimate shakes the camera, ability hits flash.
 */
final class EffekteScenes {

    private static final String HUSK = "ns_fx_husk";
    private static final double[] HOME = new double[3];
    private static int checks;
    /** Looking a little down: the crosshair meets the near husk at 2.5 blocks (melee and execute reach). */
    private static final float PITCH = 10.0f;
    /** Chef 12.11. 18:10: effects cover at most ~15 % of the caster's own view. */
    private static final double MAX_COVER = 0.15;
    /** ... and every subclass looks visibly new: at least this share of the picture differs before/after. */
    private static final double MIN_APART = 0.01;
    /** Presses per case for the median. */
    private static final int REPEAT = 3;
    /** A pixel counts as changed when a colour channel moved by more than this. */
    private static final int STRONG = 48;
    /** Lines sub;view;what;cover before;cover after;apart for the summary. */
    private static final List<String> COVER = new ArrayList<>();
    private static final List<String> FAILS = new ArrayList<>();

    private EffekteScenes() {
    }

    private static void log(String line) {
        System.out.println("[Night Siege test] effekte " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            FAILS.add(what);
            log("FAIL " + what);
        }
    }

    private static void server(ClientGameTestContext context, String step, EventLookTest.OnServer action) {
        EventLookTest.onServer(context, (player, manager) -> {
            try {
                action.run(player, manager);
            } catch (Throwable t) {
                checks++;
                FAILS.add(step + ": " + t);
                log("FAIL " + step + ": " + t);
                t.printStackTrace(System.out);
            }
        });
    }

    private static void night(ClientGameTestContext context, String tag) {
        final boolean[] soon = new boolean[1];
        server(context, tag + " night soon", (player, m) -> soon[0] = m.isActive() && m.secondsLeft() < 40);
        context.waitTicks(2);
        for (int w = 0; w < 50 && soon[0]; w++) {
            context.waitTicks(20);
            server(context, tag + " night wait", (player, m) -> soon[0] = m.isActive());
            context.waitTicks(2);
        }
        final boolean[] renewed = new boolean[1];
        server(context, tag + " night", (player, m) -> {
            if (!m.isActive()) {
                ServerLevel level = (ServerLevel) player.level();
                renewed[0] = m.prepareTest(level.getServer(), level, player.blockPosition(), 1, com.mki.nightsiege.core.SiegeEvent.NONE);
            }
        });
        context.waitTicks(2);
        if (renewed[0]) {
            context.waitTicks(80);
            EventLookTest.closeScreen(context);
            server(context, tag + " clear", (player, m) -> ((ServerLevel) player.level()).getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(160), e -> e instanceof net.minecraft.world.entity.monster.Enemy
                            && !e.entityTags().contains(HUSK)).forEach(Mob::discard));
            context.waitTicks(5);
        }
    }

    private static void home(net.minecraft.server.level.ServerPlayer player) {
        player.teleportTo(HOME[0], HOME[1], HOME[2]);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.resetFallDistance();
    }

    /** Husks at 20 % life (the execution needs under 25-35 %), the player a little hurt (heals need a wound). */
    private static void weaken(net.minecraft.server.level.ServerPlayer player) {
        for (Mob h : ((ServerLevel) player.level()).getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20),
                e -> e.entityTags().contains(HUSK))) {
            h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
            h.setHealth(h.getMaxHealth() * 0.2f);
            if (com.mki.nightsiege.game.Companions.get() != null) {
                // CI b344cb0b6: the army of the dead raises fallen monsters - a corpse per husk (and a soul)
                com.mki.nightsiege.game.Companions.get().onMonsterDeath(h, false);
            }
        }
        player.setHealth(player.getMaxHealth() * 0.6f);
    }

    /**
     * Presses a key from home with every pause and resource ready, the husks weak; up to three tries
     * (a target can be out of the crosshair for a tick). True when its effect message went out.
     */
    /**
     * Fresh husks before every press (CI b344cb0b6: the execution and the berserker's ult killed the near one in
     * "vorher", so "nachher" found no target): one in melee reach straight ahead, two 7 blocks out.
     */
    private static void respawn(TestSingleplayerContext game) {
        game.getServer().runCommand("tp @e[tag=" + HUSK + "] ~ -200 ~");   // gone from the picture, no death animation
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        String husk = "summon minecraft:husk %s %s %s {NoAI:1b,PersistenceRequired:1b,Tags:[\"nightsiege\",\"" + HUSK + "\"]}";
        double[][] spots = {{2.5, 0}, {7.0, 1.5}, {7.0, -1.5}};
        for (double[] at : spots) {
            game.getServer().runCommand(String.format(java.util.Locale.ROOT, husk, HOME[0] + at[0], HOME[1], HOME[2] + at[1]));
        }
    }

    private static boolean fire(ClientGameTestContext context, TestSingleplayerContext game, String step, String press, int kind) {
        for (int attempt = 0; attempt < 3; attempt++) {
            final int sentBefore = AbilityFx.sentForTests(kind);
            respawn(game);
            context.waitTicks(2);
            context.getInput().lookAt(-90.0f, PITCH);
            context.waitTicks(1);
            server(context, step, (player, m) -> {
                home(player);
                KlassencheckScenes.ready(player, m);
                weaken(player);
                m.onCoreAction(player, press);
            });
            context.waitTicks(2);   // server(...) only queues the step
            if (AbilityFx.sentForTests(kind) > sentBefore) {
                return true;
            }
            context.waitTicks(10);
        }
        return false;
    }

    private static java.nio.file.Path shot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> {   // no window over the picture (CI 550877110: the night's end window); no wait
            if (client.gui.screen() != null) {
                client.gui.setScreen(null);
            }
        });
        context.getInput().setCursorPos(1, 1);   // lessons 12.11.: the cursor in an empty corner
        return context.takeScreenshot(name);
    }

    /**
     * One measured press: husks fresh, everything ready, a base picture, then the key on the same spot and
     * its picture 6 ticks later. {base, picture}, or null when the key did not go off in three tries.
     */
    private static java.nio.file.Path[] measured(ClientGameTestContext context, TestSingleplayerContext game, String step,
                                                 String press, int kind, String name) {
        for (int attempt = 0; attempt < 3; attempt++) {
            final int sentBefore = AbilityFx.sentForTests(kind);
            calm(context, game, step + " calm");
            java.nio.file.Path base = shot(context, name + "_basis");
            server(context, step, (player, m) -> m.onCoreAction(player, press));
            context.waitTicks(2);   // server(...) only queues the step
            if (AbilityFx.sentForTests(kind) > sentBefore) {
                context.waitTicks(4);
                return new java.nio.file.Path[]{base, shot(context, name)};
            }
            try {
                java.nio.file.Files.deleteIfExists(base);
            } catch (Exception ignored) {
                // a picture more in the folder
            }
            context.waitTicks(10);
        }
        return null;
    }

    /**
     * Median over the repetitions: cover = changed(base, picture) of {@code a}; apart = changed(picture a,
     * picture b) of the same repetition. -1 when nothing could be measured.
     */
    private static double median(java.nio.file.Path[][] a, java.nio.file.Path[][] b, boolean cover) {
        List<Double> values = new ArrayList<>();
        for (int rep = 0; rep < a.length; rep++) {
            if (a[rep] == null || !cover && (b == null || b[rep] == null)) {
                continue;
            }
            double v = cover ? changed(a[rep][0], a[rep][1]) : changed(a[rep][1], b[rep][1]);
            if (v >= 0) {
                values.add(v);
            }
        }
        if (values.isEmpty()) {
            return -1;
        }
        java.util.Collections.sort(values);
        return values.get(values.size() / 2);
    }

    /** The same set-up as {@link #fire} without a key: the picture without any effect, at the same moment. */
    private static void calm(ClientGameTestContext context, TestSingleplayerContext game, String step) {
        respawn(game);
        context.waitTicks(2);
        context.getInput().lookAt(-90.0f, PITCH);
        context.waitTicks(1);
        server(context, step, (player, m) -> {
            home(player);
            KlassencheckScenes.ready(player, m);
            weaken(player);
        });
        context.waitTicks(4);
    }

    /**
     * Share of pixels that changed strongly between two pictures (any colour channel by more than
     * {@link #STRONG}); every second pixel in both directions. -1 when one cannot be read.
     */
    static double changed(java.nio.file.Path a, java.nio.file.Path b) {
        try {
            java.awt.image.BufferedImage x = javax.imageio.ImageIO.read(a.toFile());
            java.awt.image.BufferedImage y = javax.imageio.ImageIO.read(b.toFile());
            if (x == null || y == null || x.getWidth() != y.getWidth() || x.getHeight() != y.getHeight()) {
                return -1;
            }
            long all = 0;
            long moved = 0;
            for (int j = 0; j < x.getHeight(); j += 2) {
                for (int i = 0; i < x.getWidth(); i += 2) {
                    int p = x.getRGB(i, j);
                    int q = y.getRGB(i, j);
                    int d = Math.max(Math.abs((p >> 16 & 0xFF) - (q >> 16 & 0xFF)),
                            Math.max(Math.abs((p >> 8 & 0xFF) - (q >> 8 & 0xFF)), Math.abs((p & 0xFF) - (q & 0xFF))));
                    all++;
                    if (d > STRONG) {
                        moved++;
                    }
                }
            }
            return all == 0 ? -1 : (double) moved / all;
        } catch (Exception e) {
            log("picture not read: " + e);
            return -1;
        }
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        COVER.clear();
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 1200 4 true");
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        // pictures: third person from behind, a wide lens, no chat (CI 5ca7535db: the chat covered the middle)
        final Object[] before = new Object[2];
        context.runOnClient(client -> {
            before[0] = client.options.chatVisibility().get();
            before[1] = client.options.fov().get();
            client.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            client.options.fov().set(100);
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        context.getInput().lookAt(-90.0f, PITCH);
        context.waitTicks(10);
        final int[] saved = new int[2];
        final boolean[] refill = new boolean[1];
        server(context, "night", (player, m) -> {
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            player.getInventory().clearContent();
            ServerLevel level = (ServerLevel) player.level();
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            refill[0] = m.config().refillSpawns;
            m.config().testPrepSeconds = 1;
            m.config().batchIntervalSeconds = 600;
            m.config().refillSpawns = false;
            m.prepareTest(level.getServer(), level, player.blockPosition(), 1, com.mki.nightsiege.core.SiegeEvent.NONE);
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "home", (player, m) -> {
            ((ServerLevel) player.level()).getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(160),
                    e -> e instanceof net.minecraft.world.entity.monster.Enemy).forEach(Mob::discard);
            HOME[0] = player.getX();
            HOME[1] = player.getY();
            HOME[2] = player.getZ();
        });
        context.waitTicks(5);

        final Set<String> subNodes = new HashSet<>();
        SkillTree.SUBCLASSES.forEach(i -> subNodes.add(i.node()));
        final List<String> learned = new ArrayList<>();
        final int hitsBefore = AbilityFxClient.receivedForTests(CoreNetworking.FxPayload.HITS);
        int shakes = 0;
        int ultsFull = 0;
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            String sub = info.key();
            night(context, sub);
            game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
            server(context, sub + " home", (player, m) -> home(player));
            context.waitTicks(3);
            respawn(game);
            context.waitTicks(5);
            final String[] press = new String[1];
            server(context, sub + " setup", (player, m) -> {
                for (Mob h : ((ServerLevel) player.level()).getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20),
                        e -> e.entityTags().contains(HUSK))) {
                    h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
                }
                PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
                own.unlockClass(info.clazz());
                own.switchTo(info.clazz());
                own.chosenClass = info.clazz().name();
                for (String id : learned) {
                    own.setLevel(id, 0);
                }
                learned.clear();
                own.setLevel(info.node(), 1);
                learned.add(info.node());
                boolean ult = false;
                for (SkillNode n : m.skillTree().all()) {
                    if (n.clazz() != info.clazz() || subNodes.contains(n.id())) {
                        continue;
                    }
                    if (!n.onTrunk() && !n.clazz().isShared() && !info.way().equals(n.path())) {
                        continue;
                    }
                    if (n.effect().target() == SkillEffect.Target.ULTIMATE) {
                        if (ult || !info.way().equals(n.path())) {
                            continue;
                        }
                        ult = true;
                    }
                    own.setLevel(n.id(), n.maxLevel());
                    learned.add(n.id());
                }
                m.kits().equip(player, 20);
                KlassencheckScenes.ready(player, m);
                m.sendUltHud(player);
                int slots = m.abilitySlots(player).size();
                press[0] = AbilityKeys.keyOf(sub, AbilityKeys.F_ACTION) != null ? "ult2" : slots > 1 ? "slot:1" : "slot:0";
            });
            context.waitTicks(5);
            // Chef 12.11. 18:10: first person and F5, each before/after, measured against a picture without effect.
            // The shake is off for the pictures (it moves the whole picture) and checked once on its own below.
            context.runOnClient(client -> FxSettings.setShake(false));
            // CI 1b70abcc3: single pictures against one early base picture jumped between runs (random particles,
            // lingering clouds). Now every press gets its own base picture right before it, same place, same husks,
            // three presses per case; the median counts (one outlier drops out). Only the first set of pictures is kept.
            for (boolean firstPerson : new boolean[]{true, false}) {
                String view = firstPerson ? "ich" : "f5";
                night(context, sub + " " + view);   // CI 550877110: the night must not end during a subclass
                context.runOnClient(client -> client.options.setCameraType(firstPerson ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK));
                context.waitTicks(5);
                for (int k = 0; k < 2; k++) {
                    String what = k == 0 ? "faehigkeit" : "ult";
                    String key = k == 0 ? press[0] : "ult";
                    int kind = k == 0 ? CoreNetworking.FxPayload.CAST : CoreNetworking.FxPayload.ULT;
                    java.nio.file.Path[][][] pics = new java.nio.file.Path[2][REPEAT][];   // [vorher|nachher][rep] = {base, picture}
                    for (boolean full : new boolean[]{false, true}) {
                        String when = full ? "nachher" : "vorher";
                        context.runOnClient(client -> FxSettings.setFull(full));
                        int drawnBefore = AbilityFxClient.drawnForTests();
                        int boostBefore = AbilityFxClient.boostedForTests();
                        int fired = 0;
                        for (int rep = 0; rep < REPEAT; rep++) {
                            String name = "effekte_" + sub + "_" + view + "_" + what + "_" + when + (rep == 0 ? "" : "_m" + rep);
                            pics[full ? 1 : 0][rep] = measured(context, game, sub + " " + view + " " + what + " " + when, key, kind, name);
                            fired += pics[full ? 1 : 0][rep] != null ? 1 : 0;
                            context.waitTicks(30);
                        }
                        check(fired == REPEAT, sub + " " + view + " " + when + ": " + what + " (" + key + ") went off " + fired + " of " + REPEAT);
                        int drawn = AbilityFxClient.drawnForTests() - drawnBefore;
                        if (full) {
                            check(fired == 0 || drawn > 0, sub + " " + view + " " + what + ": full effects draw particles");
                        } else {
                            check(drawn == 0 && AbilityFxClient.boostedForTests() == boostBefore,
                                    sub + " " + view + " " + what + ": reduced draws none of the new effects and keeps the old particles");
                        }
                    }
                    double coverOld = median(pics[0], null, true);
                    double cover = median(pics[1], null, true);
                    double apart = median(pics[0], pics[1], false);
                    log(String.format(java.util.Locale.ROOT, "%s %s %s: Deckung vorher %.1f %%, nachher %.1f %%, Unterschied vorher/nachher %.1f %% (Median aus %d)",
                            sub, view, what, coverOld * 100, cover * 100, apart * 100, REPEAT));
                    COVER.add(String.format(java.util.Locale.ROOT, "%s;%s;%s;%.1f;%.1f;%.1f", sub, view, what, coverOld * 100, cover * 100, apart * 100));
                    // the new look may not cover more than 15 % of the own view; where the old one alone covers more
                    // (the alchemist's cloud, the berserker's vignette), the new one may add at most 2 points
                    if (firstPerson && cover >= 0) {
                        check(cover <= MAX_COVER || cover <= coverOld + 0.02, sub + " " + what + ": effects cover "
                                + Math.round(cover * 100) + " % of the own view (max 15 %, old look " + Math.round(coverOld * 100) + " %)");
                    }
                    check(apart >= MIN_APART, sub + " " + view + " " + what + ": before and after differ only "
                            + String.format(java.util.Locale.ROOT, "%.1f", apart * 100) + " % of the picture (min "
                            + Math.round(MIN_APART * 100) + " %)");
                    // only the first set of pictures stays (base and picture of before and after)
                    for (java.nio.file.Path[][] mode : pics) {
                        for (int rep = 1; rep < REPEAT; rep++) {
                            if (mode[rep] != null) {
                                for (java.nio.file.Path f : mode[rep]) {
                                    try {
                                        java.nio.file.Files.deleteIfExists(f);
                                    } catch (Exception ignored) {
                                        // a picture more in the folder
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // the shake of one's own ultimate, once, without a picture
            context.runOnClient(client -> {
                FxSettings.setFull(true);
                FxSettings.setShake(true);
                client.options.setCameraType(CameraType.FIRST_PERSON);
            });
            boolean ultFired = fire(context, game, sub + " ult shake", "ult", CoreNetworking.FxPayload.ULT);
            boolean shaking = AbilityFxClient.shakingForTests();
            if (ultFired) {
                ultsFull++;
                shakes += shaking ? 1 : 0;
            }
            context.waitTicks(30);
        }
        context.runOnClient(client -> {
            FxSettings.setFull(false);   // the default since Chef 12.11. 18:10
            FxSettings.setShake(true);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.chatVisibility().set((net.minecraft.world.entity.player.ChatVisiblity) before[0]);
            client.options.fov().set((Integer) before[1]);
        });
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        int hits = AbilityFxClient.receivedForTests(CoreNetworking.FxPayload.HITS) - hitsBefore;
        log("ultimates fired (full)=" + ultsFull + " shakes=" + shakes + " hit messages=" + hits
                + " rings=" + AbilityFx.sentForTests(CoreNetworking.FxPayload.RING) + " old particles bigger=" + AbilityFxClient.boostedForTests());
        check(AbilityFxClient.boostedForTests() > 0, "the abilities' own particles came bigger with full effects");
        log("Deckung (sub;sicht;was;vorher %;nachher %;unterschied %): " + String.join(" | ", COVER));
        check(shakes == ultsFull, "every own ultimate shook the camera (" + shakes + "/" + ultsFull + ")");
        check(hits > 0, "ability hits flashed (" + hits + ")");
        check(AbilityFx.sentForTests(CoreNetworking.FxPayload.RING) > 0, "area abilities drew their ring");
        server(context, "restore", (player, m) -> {
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            m.config().refillSpawns = refill[0];
        });
        context.waitTicks(2);
        log("RESULT " + (checks - FAILS.size()) + " of " + checks + " ok" + (FAILS.isEmpty() ? "" : " FAILS " + FAILS));
    }
}
