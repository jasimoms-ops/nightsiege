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

    private static void shot(ClientGameTestContext context, String name) {
        context.getInput().setCursorPos(1, 1);   // lessons 12.11.: the cursor in an empty corner
        context.takeScreenshot(name);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
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
            for (boolean full : new boolean[]{false, true}) {
                String when = full ? "nachher" : "vorher";
                context.runOnClient(client -> FxSettings.setFull(full));
                final int[] drawn = {AbilityFxClient.drawnForTests()};
                final int boostBefore = AbilityFxClient.boostedForTests();
                // the ability: the same moment in both looks, 4 ticks after it went off; no picture when it did not
                boolean castFired = fire(context, game, sub + " ability " + when, press[0], CoreNetworking.FxPayload.CAST);
                if (castFired) {
                    context.waitTicks(4);
                    shot(context, "effekte_" + sub + "_faehigkeit_" + when);
                }
                check(castFired, sub + " " + when + ": the ability (" + press[0] + ") went off");
                context.waitTicks(15);
                final int castDrawn = AbilityFxClient.drawnForTests() - drawn[0];
                drawn[0] = AbilityFxClient.drawnForTests();
                boolean ultFired = fire(context, game, sub + " ult " + when, "ult", CoreNetworking.FxPayload.ULT);
                boolean shaking = AbilityFxClient.shakingForTests();
                if (ultFired) {
                    context.waitTicks(4);
                    shot(context, "effekte_" + sub + "_ult_" + when);
                }
                check(ultFired, sub + " " + when + ": the ultimate went off");
                context.waitTicks(30);
                int ultDrawn = AbilityFxClient.drawnForTests() - drawn[0];
                log(sub + " " + when + ": ability fired=" + castFired + " particles=" + castDrawn + " ult fired=" + ultFired
                        + " ult particles=" + ultDrawn + " shake=" + shaking);
                if (full) {
                    check(!ultFired || ultDrawn > 0, sub + ": full ultimate draws particles");
                    if (ultFired) {
                        ultsFull++;
                        shakes += shaking ? 1 : 0;
                    }
                } else {
                    check(castDrawn == 0 && ultDrawn == 0 && AbilityFxClient.boostedForTests() == boostBefore,
                            sub + ": reduced draws none of the new effects and keeps the old particles");
                }
            }
        }
        context.runOnClient(client -> {
            FxSettings.setFull(true);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.chatVisibility().set((net.minecraft.world.entity.player.ChatVisiblity) before[0]);
            client.options.fov().set((Integer) before[1]);
        });
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        int hits = AbilityFxClient.receivedForTests(CoreNetworking.FxPayload.HITS) - hitsBefore;
        log("ultimates fired (full)=" + ultsFull + " shakes=" + shakes + " hit messages=" + hits
                + " rings=" + AbilityFx.sentForTests(CoreNetworking.FxPayload.RING) + " old particles bigger=" + AbilityFxClient.boostedForTests());
        check(AbilityFxClient.boostedForTests() > 0, "the abilities' own particles came bigger with full effects");
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
