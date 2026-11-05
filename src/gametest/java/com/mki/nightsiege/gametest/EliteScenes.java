package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.HallOfFame;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.game.CombatEffects;
import com.mki.nightsiege.game.Elites;
import com.mki.nightsiege.game.HallService;
import com.mki.nightsiege.game.MobFactory;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.StatsRecorder;
import com.mki.nightsiege.game.StealPool;
import com.mki.nightsiege.game.Wounds;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Simon 05.11. 15:0x: life steal only against the damage taken (100 % of the last 5 s), the revenant's
 * gaping wounds, elite monsters (from level 10, one in eight; Schnell and Heilungshemmend), the life a
 * defender really lost in the statistics, and the playtest's nights in the Ruhmeshalle. Log lines start
 * with "[elite]".
 */
final class EliteScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private EliteScenes() {
    }

    private static void log(String line) {
        System.out.println("[elite] " + line);
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

    private static Mob mob(ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z) {
        Mob mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            return null;
        }
        mob.setPos(x, y, z);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        return mob;
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** A zombie's hit of this much on the player, as in the night (armour and all). */
    private static float hit(ServerLevel level, ServerPlayer player, Mob by, float amount) {
        float before = player.getHealth();
        player.invulnerableTime = 0;
        player.hurtServer(level, level.damageSources().mobAttack(by), amount);
        return before - player.getHealth();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 300;
        int cz = pos.getZ() - 300;
        int cy = EventLookTest.yard(context, game, cx, cz, 30, 12);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy, cz));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(20);
        final int[] saved = new int[2];
        final boolean[] refill = new boolean[1];
        // A test night at level 15 without waves (the elites may have both affixes there).
        server(context, "night", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            m.config().eliteMobs = true;          // off for the other scenes (EventLookTest)
            m.config().lifestealCoupled = true;
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            refill[0] = m.config().refillSpawns;
            m.config().testPrepSeconds = 1;
            m.config().batchIntervalSeconds = 600;
            m.config().refillSpawns = false;   // no monsters of the night: only the scene's own hit the player
            boolean ok = m.prepareTest(level.getServer(), level, new BlockPos(cx + 8, cy, cz + 8), 15,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            log("night prepared=" + ok);
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        // The first wave goes (at level 15 it killed the player in the first run of this scene).
        server(context, "clear", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            List<Mob> monsters = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(160),
                    e -> e instanceof net.minecraft.world.entity.monster.Enemy);
            monsters.forEach(Mob::discard);
            log("the night's first monsters gone: " + monsters.size());
        });
        context.waitTicks(10);

        // ---------------------------------------------------------------- life steal against the damage taken
        server(context, "steal", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            check(m.isActive(), "the test night runs (level " + m.currentLevel() + ")");
            check(m.config().lifestealCoupled && Math.abs(m.config().lifestealTakenShare - 1.0) < 1e-9
                    && Math.abs(m.config().lifestealTakenSeconds - 5.0) < 1e-9, "config: coupled, 100 %, 5 s");
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            Wounds.cure(player);
            StealPool.forget(player.getUUID());
            Mob zombie = mob(level, EntityTypes.ZOMBIE, player.getX() + 2, player.getY(), player.getZ());
            // Not hit: no life steal at all.
            player.setHealth(10f);
            CombatEffects.steal(player, 6f);
            check(Math.abs(player.getHealth() - 10f) < 0.01f, "nobody hit him: life steal heals nothing (" + f(player.getHealth()) + ")");
            // Hit for 4: life steal heals those 4 back, no more.
            player.setHealth(16f);
            float lost = hit(level, player, zombie, 4f);
            double pool = StealPool.available(player, level.getGameTime());
            log("hit 4: lost=" + f(lost) + " pool=" + f(pool) + " health=" + f(player.getHealth()));
            check(lost > 0.2f && Math.abs(pool - lost) < 0.05, "the pool holds what was lost (" + f(pool) + " of " + f(lost) + ")");
            float h0 = player.getHealth();
            CombatEffects.steal(player, 10f);
            check(Math.abs(player.getHealth() - (h0 + lost)) < 0.05f, "a steal of 10 heals the lost " + f(lost) + " only ("
                    + f(player.getHealth() - h0) + ")");
            float h1 = player.getHealth();
            CombatEffects.steal(player, 5f);
            check(Math.abs(player.getHealth() - h1) < 0.01f, "the pool is used up: no more heal");
            // Many small steals take it bit by bit.
            player.setHealth(16f);
            float lost2 = hit(level, player, zombie, 3f);
            float h2 = player.getHealth();
            for (int i = 0; i < 10; i++) {
                CombatEffects.steal(player, 0.5f);
            }
            check(Math.abs(player.getHealth() - (h2 + lost2)) < 0.05f, "ten small steals heal no more than the " + f(lost2) + " lost ("
                    + f(player.getHealth() - h2) + ")");
            // Wounded: half of it.
            player.setHealth(16f);
            float lost3 = hit(level, player, zombie, 4f);
            Wounds.afterDamage(player, level.damageSources().mobAttack(mob(level, EntityTypes.SPIDER, player.getX(), player.getY(), player.getZ() - 2)), 1f);
            float h3 = player.getHealth();
            CombatEffects.steal(player, 10f);
            check(Math.abs(player.getHealth() - (h3 + lost3 * 0.5f)) < 0.05f, "wounded: half of the lost comes back ("
                    + f(player.getHealth() - h3) + " of " + f(lost3) + ")");
            Wounds.cure(player);
            // Off: as before.
            m.config().lifestealCoupled = false;
            player.setHealth(10f);
            CombatEffects.steal(player, 4f);
            check(Math.abs(player.getHealth() - 14f) < 0.01f, "lifestealCoupled false: the steal heals as before");
            m.config().lifestealCoupled = true;
            zombie.discard();
            // An old loss runs out after 5 s.
            player.setHealth(16f);
            Mob z2 = mob(level, EntityTypes.ZOMBIE, player.getX() + 2, player.getY(), player.getZ());
            hit(level, player, z2, 3f);
            z2.discard();
        });
        context.waitTicks(110);
        server(context, "steal expiry", (player, m) -> {
            check(m.isActive() && player.isAlive(), "the night still runs, the player stands");
            float h = player.getHealth();
            double pool = StealPool.available(player, player.level().getGameTime());
            CombatEffects.steal(player, 5f);
            check(pool < 1e-6 && Math.abs(player.getHealth() - h) < 0.01f, "after 5.5 s the loss is gone from the pool (" + f(pool) + ")");
        });

        // ---------------------------------------------------------------- the statistics: life really lost
        server(context, "armour", (player, m) -> {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        });
        context.waitTicks(5);   // the armour's attributes come with the player's next tick
        server(context, "statistics", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            double[] t0 = m.stats().testTotals(player);
            check(t0 != null, "the night's statistics run");
            if (t0 == null) {
                return;
            }
            log("armour " + f(player.getArmorValue()));
            Mob zombie = mob(level, EntityTypes.ZOMBIE, player.getX() + 2, player.getY(), player.getZ());
            player.setHealth(20f);
            float lost = hit(level, player, zombie, 8f);
            double[] t1 = m.stats().testTotals(player);
            double taken = t1[0] - t0[0];
            double really = t1[1] - t0[1];
            log("8 on diamond: erhalten=" + f(taken) + " verloren=" + f(really) + " health lost=" + f(lost));
            check(Math.abs(really - lost) < 0.05, "verloren is the life really lost (" + f(really) + " = " + f(lost) + ")");
            check(really < taken - 1.0, "verloren is below erhalten (the hit before armour): " + f(really) + " < " + f(taken));
            // A steal over the pool is counted as cut, a refill without heal() as heilungSonst.
            player.setHealth(10f);
            float h = player.getHealth();
            CombatEffects.steal(player, 10f);
            double[] t2 = m.stats().testTotals(player);
            check(Math.abs((t2[4] - t1[4]) - (player.getHealth() - h)) < 0.05, "lebensraub counts what landed (" + f(t2[4] - t1[4]) + ")");
            check(t2[5] - t1[5] > 0.5, "lebensraubGekappt counts what the pool held back (" + f(t2[5] - t1[5]) + ")");
            player.setHealth(10f);
            player.setHealth(15f);
            double[] t3 = m.stats().testTotals(player);
            check(Math.abs((t3[6] - t2[6]) - 5.0) < 0.05, "a refill by setHealth is heilungSonst (" + f(t3[6] - t2[6]) + ")");
            player.heal(2f);
            double[] t4 = m.stats().testTotals(player);
            check(Math.abs(t4[6] - t3[6]) < 1e-6 && Math.abs((t4[3] - t3[3]) - 2.0) < 0.05, "a heal() is heilungErhalten, not heilungSonst");
            zombie.discard();
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            player.setHealth(player.getMaxHealth());
        });

        // ---------------------------------------------------------------- the revenant's wounds
        server(context, "revenant", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            check(m.config().antiHealMobs.contains("nightsiege:revenant"), "antiHealMobs has the revenant " + m.config().antiHealMobs);
            Mob revenant = MobFactory.spawnAt(level, player.blockPosition().offset(3, 0, 0), "nightsiege:revenant", 15, m.config(),
                    new Random(5), com.mki.nightsiege.core.SiegeEvent.NONE, false);
            Mob zombie = mob(level, EntityTypes.ZOMBIE, player.getX() - 2, player.getY(), player.getZ());
            check(revenant != null, "a revenant spawns");
            Wounds.cure(player);
            Wounds.afterDamage(player, level.damageSources().mobAttack(zombie), 2f);
            check(!Wounds.wounded(player), "a plain zombie leaves no wounds");
            if (revenant != null) {
                revenant.setNoAi(true);
                Wounds.afterDamage(player, level.damageSources().mobAttack(revenant), 2f);
                check(Wounds.wounded(player), "a revenant's hit leaves gaping wounds");
                revenant.discard();
            }
            Wounds.cure(player);
            zombie.discard();
        });

        // ---------------------------------------------------------------- elites
        server(context, "elites", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            SiegeConfig cfg = m.config();
            check(Elites.chance(cfg, 9) == 0.0, "no elites below level 10");
            check(Math.abs(Elites.chance(cfg, 10) - 0.125) < 1e-9, "level 10: one in eight");
            check(Math.abs(Elites.chance(cfg, 15) - 0.1625) < 1e-9, "level 15: " + f(Elites.chance(cfg, 15)));
            check(Math.abs(Elites.chance(cfg, 20) - 0.2) < 1e-9 && Math.abs(Elites.chance(cfg, 30) - 0.2) < 1e-9, "level 20 and up: one in five");
            // The rolls: 600 zombies at level 15 and at level 12.
            Random random = new Random(42);
            int elites = 0;
            int both = 0;
            int single12 = 0;
            int elites12 = 0;
            for (int i = 0; i < 600; i++) {
                Mob z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                if (z != null && Elites.maybe(level, z, 15, cfg, random)) {
                    elites++;
                    both += Elites.affixes(z).size() > 1 ? 1 : 0;
                }
                Mob y = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                if (y != null && Elites.maybe(level, y, 12, cfg, random)) {
                    elites12++;
                    single12 += Elites.affixes(y).size() == 1 ? 1 : 0;
                }
            }
            log("rolls: level 15 " + elites + "/600 (both " + both + "), level 12 " + elites12 + "/600");
            check(elites > 600 * 0.1625 * 0.6 && elites < 600 * 0.1625 * 1.4, "about one in six at level 15 (" + elites + "/600)");
            check(both > elites * 0.15 && both < elites * 0.6, "level 15: some with both affixes (" + both + " of " + elites + ")");
            check(single12 == elites12, "below level 15 only one affix");

            // A fast one.
            Mob plain = mob(level, EntityTypes.ZOMBIE, player.getX() + 4, player.getY(), player.getZ() + 4);
            Mob fast = mob(level, EntityTypes.ZOMBIE, player.getX() - 4, player.getY(), player.getZ() + 4);
            Elites.make(level, fast, EnumSet.of(Elites.Affix.FAST), cfg);
            double sp = fast.getAttributeValue(Attributes.MOVEMENT_SPEED) / plain.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double life = fast.getMaxHealth() / plain.getMaxHealth();
            log("fast: speed x" + f(sp) + " life x" + f(life) + " health " + f(fast.getHealth()) + " name "
                    + (fast.getCustomName() == null ? "-" : fast.getCustomName().getString()));
            check(Math.abs(sp - 1.4) < 0.01, "Schnell: 40 % faster");
            check(Math.abs(life - 1.75) < 0.01 && Math.abs(fast.getHealth() - fast.getMaxHealth()) < 0.01, "an elite has 75 % more life, full");
            check(fast.getCustomName() != null && fast.isCustomNameVisible()
                    && fast.getCustomName().getString().contains("Elite"), "its name over the head says Elite");
            PlayerTeam team = level.getServer().getScoreboard().getPlayersTeam(fast.getScoreboardName());
            check(team != null && Elites.TEAM_FAST.equals(team.getName()) && fast.hasGlowingTag(), "it glows yellow (team "
                    + (team == null ? "-" : team.getName()) + ")");
            check(Elites.isElite(fast) && Elites.has(fast, Elites.Affix.FAST) && !Elites.has(fast, Elites.Affix.WOUNDS), "its tags");
            check(!Elites.wounds(fast), "a fast one leaves no wounds");
            // Strikes sooner: the melee goal's pause after an attack.
            int[] pause = {meleePause(plain), meleePause(fast)};
            log("melee pause: plain " + pause[0] + " fast " + pause[1]);
            check(pause[0] > 0 && pause[1] > 0 && pause[1] <= Math.round(pause[0] * 0.75), "Schnell strikes again sooner ("
                    + pause[1] + " vs " + pause[0] + " ticks)");

            // A grievous one: wounds with every hit.
            Mob grievous = mob(level, EntityTypes.SKELETON, player.getX(), player.getY(), player.getZ() + 5);
            Elites.make(level, grievous, EnumSet.of(Elites.Affix.WOUNDS), cfg);
            Wounds.cure(player);
            Wounds.afterDamage(player, level.damageSources().mobAttack(grievous), 1f);
            check(Wounds.wounded(player), "Heilungshemmend: its hit leaves gaping wounds");
            PlayerTeam team2 = level.getServer().getScoreboard().getPlayersTeam(grievous.getScoreboardName());
            check(team2 != null && Elites.TEAM_WOUNDS.equals(team2.getName()), "it glows dark green");
            Wounds.cure(player);
            Mob two = mob(level, EntityTypes.HUSK, player.getX() + 2, player.getY(), player.getZ() + 6);
            Elites.make(level, two, EnumSet.allOf(Elites.Affix.class), cfg);
            PlayerTeam team3 = level.getServer().getScoreboard().getPlayersTeam(two.getScoreboardName());
            check(team3 != null && Elites.TEAM_BOTH.equals(team3.getName()) && Elites.affixes(two).size() == 2, "both: purple, two affixes");
            // Not for a champion or a nemesis-like special one.
            Mob champ = mob(level, EntityTypes.ZOMBIE, player.getX() - 2, player.getY(), player.getZ() + 6);
            champ.addTag(MobFactory.TAG_ELITE);
            boolean any = false;
            Random r2 = new Random(1);
            for (int i = 0; i < 100; i++) {
                any |= Elites.maybe(level, champ, 20, cfg, r2);
            }
            check(!any && !Elites.isElite(champ), "a champion never becomes an elite");
            plain.discard();
            fast.discard();
            grievous.discard();
            two.discard();
            champ.discard();
        });

        // ---------------------------------------------------------------- the playtest's nights in the hall
        server(context, "hall", (player, m) -> {
            String uuid = player.getUUID().toString();
            HallOfFame.Hero h0 = m.save().hall.heroes.get(uuid);
            int nights0 = h0 == null ? 0 : h0.nights;
            StatsRecorder.NightFile n = night(player, true, true, "test_gehalten");
            HallService.addNight(m, player.level().getServer(), n, true);
            HallOfFame.Hero h1 = m.save().hall.heroes.get(uuid);
            check(h1 != null && h1.nights == nights0 + 1, "a playtest night counts in the hall (" + nights0 + " -> "
                    + (h1 == null ? "-" : h1.nights) + ")");
            HallOfFame.LastNight last = m.save().hall.lastNight;
            check(last != null && last.playtest && "gewonnen".equals(last.result), "marked as playtest, held");
            check(h1 != null && !h1.recent.isEmpty() && h1.recent.get(h1.recent.size() - 1).playtest, "the hero's last night is a playtest");
            HallService.addNight(m, player.level().getServer(), night(player, true, false, "test_gehalten"), true);
            check(m.save().hall.heroes.get(uuid).nights == nights0 + 1, "another test night (not the playtest's) stays out");
            HallService.addNight(m, player.level().getServer(), night(player, true, true, "test_verloren"), true);
            HallOfFame.Hero h2 = m.save().hall.heroes.get(uuid);
            check(h2.nights == nights0 + 2 && h2.lost >= 1, "a lost playtest night counts as lost");
        });

        server(context, "end", (player, m) -> {
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            m.config().refillSpawns = refill[0];
            m.config().eliteMobs = false;
            m.config().lifestealCoupled = false;
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(20);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }

    /** A finished night file with the player in one wave. */
    private static StatsRecorder.NightFile night(ServerPlayer player, boolean test, boolean playtest, String result) {
        StatsRecorder.NightFile n = new StatsRecorder.NightFile();
        n.test = test;
        n.playtest = playtest;
        n.abgeschlossen = true;
        n.ergebnis = result;
        n.nacht = 7;
        n.stufe = 15;
        n.ende = java.time.LocalDateTime.now().toString();
        StatsRecorder.Spieler s = new StatsRecorder.Spieler();
        s.name = player.getScoreboardName();
        s.uuid = player.getUUID().toString();
        s.klasse = "warrior";
        n.spieler.put(s.name, s);
        StatsRecorder.Welle w = new StatsRecorder.Welle();
        StatsRecorder.SpielerWelle pw = new StatsRecorder.SpielerWelle();
        pw.schaden = 500;
        pw.kills = 12;
        w.spieler.put(s.name, pw);
        n.wellen.add(w);
        return n;
    }

    /** The melee goal's pause after an attack (its cooldown set anew), read from the goal. */
    private static int meleePause(Mob mob) {
        try {
            var goals = ((com.mki.nightsiege.mixin.MobGoalsAccessor) mob).nightsiege$goals().getAvailableGoals();
            for (var wrapped : goals) {
                if (wrapped.getGoal() instanceof MeleeAttackGoal goal) {
                    java.lang.reflect.Method reset = MeleeAttackGoal.class.getDeclaredMethod("resetAttackCooldown");
                    reset.setAccessible(true);
                    reset.invoke(goal);
                    java.lang.reflect.Field left = MeleeAttackGoal.class.getDeclaredField("ticksUntilNextAttack");
                    left.setAccessible(true);
                    return left.getInt(goal);
                }
            }
            log("no melee goal on " + mob.getType());
        } catch (ReflectiveOperationException | RuntimeException e) {
            log("melee pause not readable: " + e);
        }
        return -1;
    }
}
