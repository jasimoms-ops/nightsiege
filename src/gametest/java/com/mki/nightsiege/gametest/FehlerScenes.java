package com.mki.nightsiege.gametest;

import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.SiegeSave;
import com.mki.nightsiege.game.StatsRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Vex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fehler-Stichprobe 05.11. abends: (1) a defender killed in the night becomes a drone, and the killer still
 * becomes a nemesis and the arch-enemy in the statistics; (3) the rest of his life counts as lost; (2) a
 * magician's vexes go with the night. Log lines start with "[fehler]".
 */
final class FehlerScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private FehlerScenes() {
    }

    private static void log(String line) {
        System.out.println("[fehler] " + line);
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

    private static int clear(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        List<Mob> monsters = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(200),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy);
        monsters.forEach(Mob::discard);
        return monsters.size();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 300;
        int cz = pos.getZ() + 300;
        int cy = EventLookTest.yard(context, game, cx, cz, 30, 12);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy, cz));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(20);
        final int[] saved = new int[2];
        final boolean[] savedB = new boolean[1];
        final BlockPos core = new BlockPos(cx + 8, cy, cz + 8);

        // ---------------------------------------------------------------- 1 and 3: a real lethal blow in the night
        server(context, "night", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            savedB[0] = m.config().refillSpawns;
            m.config().testPrepSeconds = 1;
            m.config().batchIntervalSeconds = 600;
            m.config().refillSpawns = false;
            log("night prepared=" + m.prepareTest(level.getServer(), level, core, 6, com.mki.nightsiege.core.SiegeEvent.NONE));
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "clear", (player, m) -> log("first monsters gone: " + clear(player)));
        context.waitTicks(5);
        final int[] nemesesBefore = new int[1];
        final Mob[] killer = new Mob[1];
        server(context, "kill", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            check(m.isActive() && m.isDefending(player), "the test night runs and the player defends");
            nemesesBefore[0] = m.save().nemeses.size();
            killer[0] = m.foesHost().spawn("minecraft:zombie", player.blockPosition().east(2));
            check(killer[0] != null, "a zombie of the night");
            if (killer[0] == null) {
                return;
            }
            killer[0].setNoAi(true);
            m.foesHost().addMob(killer[0]);
            player.setHealth(3.0f);
            player.invulnerableTime = 0;
            player.hurtServer(level, level.damageSources().mobAttack(killer[0]), 60f);
            log("after the blow: alive=" + player.isAlive() + " hp=" + player.getHealth() + " defending=" + m.isDefending(player)
                    + " nemeses " + nemesesBefore[0] + " -> " + m.save().nemeses.size());
            check(player.isAlive(), "the player does not really die (a drone instead)");
            check(!m.isDefending(player), "he is out for the night");
            SiegeSave.NemesisRecord made = null;
            for (SiegeSave.NemesisRecord r : m.save().nemeses) {
                if (r.victimIds.contains(player.getUUID().toString())) {
                    made = r;
                }
            }
            check(m.save().nemeses.size() == nemesesBefore[0] + 1 && made != null,
                    "the killer becomes a nemesis (" + (made == null ? "-" : made.name + " " + made.type + " test=" + made.test) + ")");
            check(made != null && made.test, "as a test night's nemesis");
            check(killer[0].isRemoved(), "and flees from the field");
        });
        // The night ends by itself (nobody stands), the file is written.
        for (int i = 0; i < 10; i++) {
            context.waitTicks(20);
            final boolean[] done = new boolean[1];
            server(context, "wait " + i, (player, m) -> done[0] = !m.isActive());
            if (done[0]) {
                break;
            }
        }
        context.waitTicks(20);
        server(context, "stats", (player, m) -> {
            if (m.isActive()) {
                m.finish(player.level().getServer(), false);
            }
            StatsRecorder.NightFile n = StatsRecorder.lastFinished;
            Map<String, Integer> by = n == null || n.todDurch == null ? null : n.todDurch.get(player.getScoreboardName());
            log("file: tode=" + (n == null ? "-" : n.tode) + " todDurch=" + (n == null ? "-" : n.todDurch));
            check(by != null && by.keySet().stream().anyMatch(k -> k.contains("zombie")), "the night's file names the killer (" + by + ")");
            double lost = 0;
            if (n != null) {
                for (StatsRecorder.Welle w : n.wellen) {
                    StatsRecorder.SpielerWelle pw = w.spieler.get(player.getScoreboardName());
                    if (pw != null) {
                        lost += pw.verloren;
                    }
                }
            }
            log("lost in the file: " + lost);
            check(lost >= 2.9, "the rest of the life (3) counts as lost (" + lost + ")");
            // The test's nemesis goes again, so the scenes after see the world as before.
            m.save().nemeses.removeIf(r -> r.victimIds.contains(player.getUUID().toString()) && r.test);
        });
        context.waitTicks(40);
        EventLookTest.alive(context);

        // ---------------------------------------------------------------- 2: a magician's vexes go with the night
        final boolean[] smartWas = new boolean[1];
        server(context, "night 2", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            // Fehlerprüfung 06.11.: a night broken off writes the smart night too (this one is aborted below).
            smartWas[0] = m.config().smartNight;
            m.config().smartNight = true;
            log("night 2 prepared=" + m.prepareTest(level.getServer(), level, core, 12, com.mki.nightsiege.core.SiegeEvent.NONE));
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "clear 2", (player, m) -> log("first monsters gone: " + clear(player)));
        context.waitTicks(5);
        final Vex[] vexes = new Vex[2];
        server(context, "vex", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            check(m.isActive(), "night 2 runs");
            Mob evoker = m.foesHost().spawn("minecraft:evoker", player.blockPosition().south(6));
            check(evoker != null, "a magician of the night");
            if (evoker == null) {
                return;
            }
            evoker.setNoAi(true);
            m.foesHost().addMob(evoker);
            for (int i = 0; i < 2; i++) {
                Vex v = EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (v == null) {
                    continue;
                }
                if (i == 0) {
                    v.setOwner(evoker);   // called up by the magician
                }
                v.setPos(player.getX() + 3 + i, player.getY() + 4, player.getZ() - 3);
                v.setNoAi(true);
                level.addFreshEntity(v);
                vexes[i] = v;
            }
            check(vexes[0] != null && vexes[0].entityTags().contains(SiegeManager.NIGHT_MOB_TAG),
                    "the magician's vex belongs to the night");
            check(vexes[1] != null && !vexes[1].entityTags().contains(SiegeManager.NIGHT_MOB_TAG),
                    "a vex without a master is not tagged (it goes by the core's reach)");
            check(m.smartNight().on(), "the smart night runs in night 2");
            m.setSmartStep(2);
            StatsRecorder.lastFinished = null;
            m.abort(player.level().getServer());
            StatsRecorder.NightFile n = StatsRecorder.lastFinished;
            StatsRecorder.SchlaueNacht s = n == null ? null : n.schlaueNacht;
            log("aborted night: result=" + (n == null ? "-" : n.ergebnis) + " schlaueNacht an=" + (s == null ? "-" : s.an)
                    + " hoechste=" + (s == null ? "-" : s.hoechste) + " schritte=" + (s == null ? "-" : s.schritte.size()));
            check(n != null && "abgebrochen".equals(n.ergebnis), "the broken-off night writes its file");
            check(s != null && s.an && s.hoechste == 2 && !s.schritte.isEmpty(),
                    "the broken-off night's file has the smart night (an, highest step 2)");
            m.config().smartNight = smartWas[0];
        });
        context.waitTicks(10);
        server(context, "vex after", (player, m) -> {
            check(!m.isActive(), "night 2 is over");
            check(vexes[0] == null || vexes[0].isRemoved(), "the magician's vex is gone with the night");
            check(vexes[1] == null || vexes[1].isRemoved(), "so is the vex near the core");
            clear(player);
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            m.config().refillSpawns = savedB[0];
        });
        context.waitTicks(10);
        EventLookTest.alive(context);

        // ---------------------------------------------------------------- Fehlerprüfung 06.11.: an old stash (peach, 25.10.)
        server(context, "old stash", (player, m) -> {
            net.minecraft.core.HolderLookup.Provider regs = player.level().registryAccess();
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            inv.clearContent();
            // In the inventory: a marked kit sword, own golden apples and unmarked kit armour of back then.
            inv.setItem(0, com.mki.nightsiege.game.KitService.mark(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD)));
            inv.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 5));
            net.minecraft.world.item.ItemStack oldChest = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
            oldChest.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
            oldChest.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            inv.setItem(38, oldChest);
            // The stash as peach's of 25.10.: the old JSON form, own things and two pieces of old kit armour.
            SiegeSave.KitStash stash = new SiegeSave.KitStash();
            for (int i = 0; i < 41; i++) {
                stash.items.add("");
            }
            stash.items.set(0, "{\"id\":\"minecraft:diamond_sword\",\"count\":1}");
            stash.items.set(1, "{\"id\":\"minecraft:bread\",\"count\":29}");
            stash.items.set(12, "{\"id\":\"minecraft:diamond_boots\",\"count\":1,\"components\":{\"minecraft:enchantments\":{\"minecraft:protection\":3,\"minecraft:feather_falling\":3},\"minecraft:enchantment_glint_override\":false,\"minecraft:unbreakable\":{}}}");
            stash.items.set(39, "{\"id\":\"minecraft:diamond_helmet\",\"count\":1,\"components\":{\"minecraft:enchantments\":{\"minecraft:protection\":3,\"minecraft:respiration\":2},\"minecraft:enchantment_glint_override\":false,\"minecraft:unbreakable\":{}}}");
            stash.items.set(40, "{\"id\":\"minecraft:totem_of_undying\",\"count\":1}");
            stash.gameMode = "survival";
            stash.state = "";
            if (m.save().kitStashes == null) {
                m.save().kitStashes = new java.util.HashMap<>();
            }
            m.save().kitStashes.put(player.getUUID().toString(), stash);
            m.kits().restore(player);
            int unbreakableGlintless = 0;
            boolean sword = false;
            boolean bread = false;
            boolean totem = false;
            boolean apples = false;
            boolean kitLeft = false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = inv.getItem(i);
                if (st.isEmpty()) {
                    continue;
                }
                if (st.has(net.minecraft.core.component.DataComponents.UNBREAKABLE)
                        && Boolean.FALSE.equals(st.get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) {
                    unbreakableGlintless++;
                }
                sword |= st.is(net.minecraft.world.item.Items.DIAMOND_SWORD);
                bread |= st.is(net.minecraft.world.item.Items.BREAD) && st.getCount() == 29;
                totem |= st.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
                apples |= st.is(net.minecraft.world.item.Items.GOLDEN_APPLE) && st.getCount() == 5;
                kitLeft |= com.mki.nightsiege.game.KitService.isKit(st);
            }
            log("old stash back: sword=" + sword + " bread=" + bread + " totem=" + totem + " apples=" + apples
                    + " old kit gear left=" + unbreakableGlintless + " kit left=" + kitLeft + " stash=" + m.kits().hasStash(player));
            check(sword && bread && totem, "the own things of an old stash come back");
            check(apples, "what was picked up in the night stays");
            check(unbreakableGlintless == 0, "the old unmarked kit armour goes with the kit (" + unbreakableGlintless + " left)");
            check(!kitLeft && !m.kits().hasStash(player), "no kit item and no stash left");

            // The same kind of thing in a stash of today ("snbt:") is the player's own and comes back.
            inv.clearContent();
            net.minecraft.world.item.ItemStack ownBoots = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS);
            ownBoots.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
            ownBoots.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            SiegeSave.KitStash today = new SiegeSave.KitStash();
            for (int i = 0; i < 41; i++) {
                today.items.add("");
            }
            today.items.set(3, com.mki.nightsiege.game.KitService.store(ownBoots, regs));
            today.state = "applied";
            m.save().kitStashes.put(player.getUUID().toString(), today);
            m.kits().restore(player);
            check(inv.getItem(3).is(net.minecraft.world.item.Items.DIAMOND_BOOTS), "in a stash of today such boots stay the player's own");
            inv.clearContent();
            m.persist();
        });
        context.waitTicks(10);
        EventLookTest.alive(context);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
