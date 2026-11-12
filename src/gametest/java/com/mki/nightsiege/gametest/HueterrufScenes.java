package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.AbilityKeys;
import com.mki.nightsiege.core.GuardCall;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.Companions;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.WardenNight;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Paket 11.11. Z1 "hueterruf" (Simon 12.11. 14:56, Chef 15:00): the tamer and the golemsmith have Hüterruf on C
 * without a skill point. In a test night: two husks are pushed about 4 blocks and slowed 2 s, a guard (warden tag)
 * only slowed 1 s and not pushed, a wolf 20 blocks away comes back to at most 3 blocks and goes for the husk that
 * hit, a hit in the 4 s takes 60 % of one before, a second press waits (18 s). Log lines start with "[hueterruf]".
 */
final class HueterrufScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static final String PLAIN = "ns_hr_plain";
    private static final String GUARD = "ns_hr_guard";

    private HueterrufScenes() {
    }

    private static void log(String line) {
        System.out.println("[hueterruf] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    private static void server(ClientGameTestContext context, String step, EventLookTest.OnServer action) {
        EventLookTest.onServer(context, (player, manager) -> {
            try {
                action.run(player, manager);
            } catch (Throwable t) {
                FAILS.add(step + ": " + t);
                log("ERROR in " + step + ": " + t);
                t.printStackTrace(System.out);
            }
        });
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static void kit(ServerPlayer player, SiegeManager m, String node) {
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        own.unlockClass(SkillClass.SUMMONER);
        own.switchTo(SkillClass.SUMMONER);
        own.levels.clear();
        own.levels.put(node, 1);
        m.kits().equip(player, 6);
    }

    private static List<Mob> tagged(ServerLevel level, String tag) {
        List<Mob> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob mob && mob.isAlive() && e.entityTags().contains(tag)) {
                out.add(mob);
            }
        }
        return out;
    }

    private static UltState.Slot slotOf(SiegeManager m, ServerPlayer player) {
        for (UltState.Slot s : m.abilitySlots(player)) {
            if (GuardCall.ID.equals(s.id)) {
                return s;
            }
        }
        return null;
    }

    private static int slowTicks(Mob mob) {
        MobEffectInstance e = mob.getEffect(MobEffects.SLOWNESS);
        return e == null ? 0 : e.getDuration();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("kill @e[tag=" + PLAIN + "]");
        game.getServer().runCommand("kill @e[tag=" + GUARD + "]");
        context.getInput().lookAt(-90.0f, 0.0f);
        context.waitTicks(10);
        final int[] saved = new int[2];
        final boolean[] refill = new boolean[1];
        server(context, "night", (player, m) -> {
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
            log("night prepared=" + m.prepareTest(level.getServer(), level, player.blockPosition(), 1,
                    com.mki.nightsiege.core.SiegeEvent.NONE));
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "kit", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(160),
                    e -> e instanceof net.minecraft.world.entity.monster.Enemy).forEach(Mob::discard);
            Companions.get().dismissAll(level.getServer());
            Companions.resetGuardCall();
            kit(player, m, "s_golemsmith");
            UltState.Slot g = slotOf(m, player);
            check(g != null && AbilityKeys.Key.ABILITY_4.name().equals(g.key),
                    "golemsmith: Hüterruf on the bar on C (" + (g == null ? "none" : g.key) + ")");
            kit(player, m, "s_tamer");
            UltState.Slot t = slotOf(m, player);
            check(t != null && AbilityKeys.Key.ABILITY_4.name().equals(t.key) && t.cooldownTicks == 0 && t.totalTicks == 360,
                    "tamer: Hüterruf on C, ready, pause 360 (" + (t == null ? "none" : t.key + " " + t.totalTicks) + ")");
            check(m.isDefending(player), "the player defends the test night");
        });
        game.getServer().runCommand("gamemode survival @p");
        String husk = "execute as @p at @p run summon minecraft:husk ^%s ^ ^2 {PersistenceRequired:1b,Tags:[\"nightsiege\",\"%s\"]}";
        game.getServer().runCommand(husk.formatted("1", PLAIN));
        game.getServer().runCommand(husk.formatted("-1", PLAIN));
        game.getServer().runCommand("execute as @p at @p run summon minecraft:husk ^ ^ ^-2 {PersistenceRequired:1b,Tags:[\"nightsiege\",\""
                + GUARD + "\",\"" + WardenNight.TAG + "\"]}");
        context.waitTicks(5);
        final List<Vec3> before = new ArrayList<>();
        final Mob[] wolf = new Mob[1];
        final Vec3[] guardAt = new Vec3[1];
        server(context, "press", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            player.setHealth(player.getMaxHealth());
            wolf[0] = Companions.get().testCall(player, "wolf");
            if (wolf[0] != null) {
                wolf[0].teleportTo(player.getX() + 20, player.getY(), player.getZ());
            }
            List<Mob> plain = tagged(level, PLAIN);
            List<Mob> guard = tagged(level, GUARD);
            check(plain.size() == 2 && guard.size() == 1 && wolf[0] != null,
                    "two husks, a guard and a wolf (" + plain.size() + "/" + guard.size() + "/" + (wolf[0] != null) + ")");
            if (plain.size() != 2 || guard.size() != 1 || wolf[0] == null) {
                return;
            }
            Mob hitter = plain.get(0);
            player.invulnerableTime = 0;
            float h0 = player.getHealth();
            player.hurtServer(level, level.damageSources().mobAttack(hitter), 6f);
            float lost1 = h0 - player.getHealth();
            plain.forEach(p -> before.add(p.position()));
            guardAt[0] = guard.get(0).position();
            int slot = m.abilitySlotOf(player, GuardCall.ID);
            m.onCoreAction(player, "slot:" + slot);   // the key's own path
            check(Companions.guardCooldownLeft(player, level.getGameTime()) > 300, "the press fired: pause started");
            for (Mob p : plain) {
                int slow = slowTicks(p);
                MobEffectInstance e = p.getEffect(MobEffects.SLOWNESS);
                check(e != null && e.getAmplifier() == 1 && slow >= 35 && slow <= 40, "husk: Slowness II for 2 s (" + slow + ")");
                check(Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z) > 0.3, "husk: thrown back (speed "
                        + f(Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z)) + ")");
            }
            Mob gu = guard.get(0);
            int gslow = slowTicks(gu);
            check(gslow > 0 && gslow <= 20, "guard: slowed 1 s only (" + gslow + ")");
            check(Math.hypot(gu.getDeltaMovement().x, gu.getDeltaMovement().z) < 0.1, "guard: no push");
            double d = wolf[0].distanceTo(player);
            check(d <= 3.5, "the wolf from 20 blocks is beside him (" + f(d) + ")");
            check(wolf[0].getTarget() == hitter, "the wolf goes for the husk that hit him");
            player.invulnerableTime = 0;
            float h1 = player.getHealth();
            player.hurtServer(level, level.damageSources().mobAttack(hitter), 6f);
            float lost2 = h1 - player.getHealth();
            log("hit before " + f(lost1) + ", guarded " + f(lost2));
            check(lost1 > 0.5f && Math.abs(lost2 / lost1 - 0.6f) < 0.03f, "guarded hit = 60 % (" + f(lost2 / Math.max(0.01f, lost1)) + ")");
            float h2 = player.getHealth();
            m.onCoreAction(player, "slot:" + slot);   // again at once: it waits
            check(player.getHealth() == h2 && slowTicks(gu) <= gslow, "pressed again at once: nothing (the pause)");
        });
        context.waitTicks(12);
        server(context, "after", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            List<Mob> plain = tagged(level, PLAIN);
            for (int i = 0; i < Math.min(plain.size(), before.size()); i++) {
                double moved = Math.hypot(plain.get(i).getX() - before.get(i).x, plain.get(i).getZ() - before.get(i).z);
                check(moved >= 2.5, "husk flew back " + f(moved) + " blocks (want about " + f(GuardCall.pushBlocks()) + ")");
            }
            for (Mob g : tagged(level, GUARD)) {
                double moved = Math.hypot(g.getX() - guardAt[0].x, g.getZ() - guardAt[0].z);
                check(moved < 1.5, "the guard stayed (" + f(moved) + ")");
            }
        });
        game.getServer().runCommand("kill @e[tag=" + PLAIN + "]");
        game.getServer().runCommand("kill @e[tag=" + GUARD + "]");
        server(context, "end", (player, m) -> {
            Companions.get().dismissAll(player.level().getServer());
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            m.config().refillSpawns = refill[0];
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            m.save().skillsOf(player.getUUID().toString()).reset();
            Companions.resetGuardCall();
            player.setHealth(player.getMaxHealth());
        });
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);
        if (checks == 0) {
            check(false, "no check ran");
        }
        log("summary: " + (checks - FAILS.size()) + " of " + checks + " OK" + (FAILS.isEmpty() ? "" : ", FAIL: " + FAILS));
    }
}
