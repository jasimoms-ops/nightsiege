package com.mki.nightsiege.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Survival-Sicherheit, package "Nacht" (Simon 31.10.): a real night with one player and one accept
 * (no requiredAccepts override), the horn waking a sleeper, sleeping refused after the horn, the
 * natural start, a missing defender's grace minute and the scaling by who is here, a clean server
 * stop in the middle of the night (fragment and machines back, monsters gone, kit off), a crash
 * leftover and the drone's invisibility, a missed night and a coming night stopped by an admin
 * (after a lethal hit) giving the own inventory back, and /time set back in time.
 * Log lines start with "[ss-nacht]".
 */
final class SsNachtScenes {

    private SsNachtScenes() {
    }

    private static void log(String line) {
        System.out.println("[ss-nacht] " + line);
    }

    private static void state(String when, net.minecraft.server.level.ServerPlayer player, com.mki.nightsiege.game.SiegeManager m) {
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        log(when + ": day=" + m.clockDayFor(server) + " time=" + m.clockTimeFor(server)
                + " nextSiegeDay=" + m.save().nextSiegeDay + " voteDay=" + m.save().voteDay
                + " voteOpen=" + m.voteOpen(server) + " nightComing=" + m.nightComing(server) + " active=" + m.isActive()
                + " accepted=" + m.acceptedCount() + " invited=" + m.save().invitedCount() + " required=" + m.requiredAccepts()
                + " kit=" + m.kits().hasStash(player) + " sleepBlocked=" + m.sleepBlocked(server)
                + " mode=" + player.gameMode() + " alive=" + player.isAlive());
    }

    /** Slots that differ from the snapshot taken before (0 = the own inventory is whole). */
    private static int diff(List<String> before, net.minecraft.server.level.ServerPlayer player, String label) {
        List<String> now = EventLookTest.survivalSnapshot(player);
        int differ = 0;
        for (int i = 0; i < Math.max(before.size(), now.size()); i++) {
            String b = i < before.size() ? before.get(i) : "";
            String a = i < now.size() ? now.get(i) : "";
            if (!b.equals(a)) {
                differ++;
                if (differ <= 4) {
                    log(label + " DIFF slot " + i + ": vorher " + EventLookTest.survivalShort(b) + " | nachher " + EventLookTest.survivalShort(a));
                }
            }
        }
        return differ;
    }

    private static int nightMobs(net.minecraft.server.level.ServerLevel level) {
        int n = 0;
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e.entityTags().contains(com.mki.nightsiege.game.SiegeManager.NIGHT_MOB_TAG)) {
                n++;
            }
        }
        return n;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.ensureCore(context, game);
        final List<String> before = new ArrayList<>();
        final long[] day = new long[1];
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("difficulty normal");
        game.getServer().runCommand("gamerule advance_time true");
        game.getServer().runCommand("gamerule doDaylightCycle true");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("time set 500");
        context.waitTicks(10);
        EventLookTest.onServer(context, (player, m) -> {
            com.mki.nightsiege.game.CoreRecord c = m.mainCore();
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            com.mki.nightsiege.core.PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            own.unlockClass(com.mki.nightsiege.core.SkillClass.WARRIOR);
            own.switchTo(com.mki.nightsiege.core.SkillClass.WARRIOR);
            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
            inv.clearContent();
            net.minecraft.world.item.ItemStack sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Simons Schwert"));
            inv.setItem(0, sword);
            inv.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 16));
            inv.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 30));
            inv.setItem(9, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 40));
            inv.setItem(20, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 12));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
            player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(), c.x + 2.5, c.y, c.z + 0.5,
                    java.util.Set.of(), 0f, 0f, true);
            before.addAll(EventLookTest.survivalSnapshot(player));
            // Today is a siege day with a night fragment and the war camp's machines; nothing voted yet.
            long today = m.clockDayFor(player.level().getServer());
            m.save().nextSiegeDay = today;
            m.save().clearVotes();
            m.save().nightFragment = "MIST";
            m.save().machinesDay = today;
            log("setup: requiredAccepts(config)=" + m.config().requiredAccepts + " day=" + today + " core=" + c.x + " " + c.y + " " + c.z
                    + " registered=" + m.save().registered.size());
        });
        context.waitTicks(20);

        // --- 1. The invitation: one player invited, one accept is enough.
        game.getServer().runCommand("time set 1100");
        context.waitTicks(60);
        EventLookTest.onServer(context, (player, m) -> {
            state("invite", player, m);
            m.vote(player, true);
        });
        context.waitTicks(20);
        EventLookTest.onServer(context, (player, m) -> state("voted", player, m));
        context.waitTicks(10);

        // --- 2. In bed before the horn (as in a thunderstorm by day): the horn wakes the sleeper.
        game.getServer().runCommand("time set 11700");
        context.waitTicks(20);
        EventLookTest.onServer(context, (player, m) -> {
            player.startSleeping(EventLookTest.survivalBed(player));   // straight into bed (a storm's rain takes a while)
            log("in bed before horn: sleeping=" + player.isSleeping() + " blocked=" + m.sleepBlocked(player.level().getServer()));
        });
        context.waitTicks(15);
        game.getServer().runCommand("time set 12100");
        context.waitTicks(40);
        EventLookTest.onServer(context, (player, m) -> {
            log("after horn: sleeping=" + player.isSleeping() + " (expect false)");
            state("horn", player, m);
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);

        // --- 3. Sleeping after the horn is refused.
        game.getServer().runCommand("time set 13000");
        context.waitTicks(10);
        EventLookTest.onServer(context, (player, m) -> {
            com.mojang.datafixers.util.Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, net.minecraft.util.Unit> r =
                    player.startSleepInBed(EventLookTest.survivalBed(player));
            log("sleep after horn: refused=" + r.left().isPresent() + " sleeping=" + player.isSleeping()
                    + " blocked=" + m.sleepBlocked(player.level().getServer()));
        });
        context.waitTicks(120);
        EventLookTest.onServer(context, (player, m) -> state("after the bed try", player, m));
        context.waitTicks(10);

        // --- 4. The night starts by itself with 1 accept.
        game.getServer().runCommand("time set 15100");
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("effect give @p resistance 300 4 true");
        game.getServer().runCommand("effect give @p regeneration 300 2 true");
        game.getServer().runCommand("effect give @p saturation 300 1 true");
        final java.util.UUID ghost = java.util.UUID.randomUUID();
        EventLookTest.onServer(context, (player, m) -> {
            state("start", player, m);
            log("REAL NIGHT started naturally=" + m.isActive() + " level=" + m.currentLevel() + " present=" + m.presentCount()
                    + " fragment=" + m.save().nightFragment + " special=" + m.save().specialNight);
            // A second defender who is not online: inside the grace minute, then fallen.
            m.testJoin(ghost, true);
        });
        context.waitTicks(40);
        EventLookTest.onServer(context, (player, m) -> log("grace: present=" + m.presentCount() + " (expect 2) active=" + m.isActive()));
        context.waitTicks(1240);
        EventLookTest.closeScreen(context);
        EventLookTest.onServer(context, (player, m) -> log("after grace: present=" + m.presentCount() + " (expect 1) active=" + m.isActive()
                + " defending=" + m.isDefending(player) + " killsRequired=" + m.killsRequired()));
        context.waitTicks(10);

        // --- 5. The server stops in the middle of the night (the SERVER_STOPPING path).
        EventLookTest.onServer(context, (player, m) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            int mobs = nightMobs(level);
            long today = m.clockDayFor(level.getServer());
            m.endNightForStop(level.getServer());
            log("STOP: nightMobs before=" + mobs + " after=" + nightMobs(level) + " active=" + m.isActive()
                    + " kit=" + m.kits().hasStash(player) + " drone=" + com.mki.nightsiege.game.Drones.isDrone(player)
                    + " fragment=" + m.save().nightFragment + " machinesDay=" + m.save().machinesDay
                    + " nextSiegeDay=" + m.save().nextSiegeDay + " today=" + today
                    + " won=" + m.save().siegesWon + " lost=" + m.save().siegesLost + " mode=" + player.gameMode()
                    + " flying=" + player.getAbilities().flying + " invulnerable=" + player.getAbilities().invulnerable);
            log("STOP inventory differ=" + diff(before, player, "stop") + " (expect 0)");
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);

        // --- 6. A crash leftover: a monster of a night loaded with no night running goes.
        EventLookTest.onServer(context, (player, m) -> {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.monster.zombie.Zombie z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level,
                    net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (z != null) {
                z.setPos(player.getX() + 6, player.getY(), player.getZ());
                z.addTag(com.mki.nightsiege.game.MobFactory.TAG);
                z.addTag(com.mki.nightsiege.game.SiegeManager.NIGHT_MOB_TAG);
                z.setPersistenceRequired();
                level.addFreshEntity(z);
                log("crash leftover: removed=" + z.isRemoved() + " (expect true)");
            }
            // The drone's invisibility after a logout: only the drone's goes, a potion's stays.
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,
                    net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0, false, false));
            player.getAbilities().invulnerable = true;
            com.mki.nightsiege.game.Drones.cleanup(player);
            boolean droneGone = !player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 600, 0));
            com.mki.nightsiege.game.Drones.cleanup(player);
            boolean potionStays = player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            log("drone invisibility: droneGone=" + droneGone + " potionStays=" + potionStays
                    + " invulnerable=" + player.getAbilities().invulnerable + " (expect true true false)");
            day[0] = m.clockDayFor(level.getServer());
            m.save().nextSiegeDay = day[0] + 1;
            m.save().clearVotes();
        });
        context.waitTicks(20);

        // --- 7. A missed night: horn, kit on, then the next morning without a night: own things back.
        game.getServer().runCommand("time set " + ((day[0] + 1) * 24000L + 1100L));
        context.waitTicks(60);
        EventLookTest.onServer(context, (player, m) -> m.vote(player, true));
        context.waitTicks(20);
        game.getServer().runCommand("time set " + ((day[0] + 1) * 24000L + 12100L));
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        EventLookTest.onServer(context, (player, m) -> {
            state("missed: horn", player, m);
            log("missed: kit on, inventory differ=" + diff(before, player, "kit-on") + " (expect >0)");
        });
        context.waitTicks(10);
        game.getServer().runCommand("time set " + ((day[0] + 2) * 24000L + 500L));
        context.waitTicks(60);
        EventLookTest.onServer(context, (player, m) -> {
            state("missed: next morning", player, m);
            log("MISSED inventory differ=" + diff(before, player, "missed") + " kit=" + m.kits().hasStash(player) + " (expect 0 false)");
        });
        context.waitTicks(10);

        // --- 8. A coming night: horn, a lethal hit before the start, then /nightsiege stop: own things back.
        game.getServer().runCommand("time set " + ((day[0] + 2) * 24000L + 1100L));
        context.waitTicks(60);
        EventLookTest.onServer(context, (player, m) -> m.vote(player, true));
        context.waitTicks(20);
        game.getServer().runCommand("time set " + ((day[0] + 2) * 24000L + 12100L));
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("effect clear @p");
        EventLookTest.onServer(context, (player, m) -> {
            state("coming: horn", player, m);
            float hp = player.getHealth();
            player.hurtServer((net.minecraft.server.level.ServerLevel) player.level(), player.damageSources().generic(), 500f);
            log("lethal hit between horn and start: hp " + hp + " -> " + player.getHealth() + " alive=" + player.isAlive()
                    + " kit=" + m.kits().hasStash(player));
        });
        context.waitTicks(40);
        EventLookTest.onServer(context, (player, m) -> {
            if (!player.isAlive()) {
                player.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                        net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            }
        });
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        EventLookTest.onServer(context, (player, m) -> {
            state("coming: respawned", player, m);
            log("coming: after respawn inventory differ=" + diff(before, player, "respawn"));
        });
        context.waitTicks(10);
        game.getServer().runCommand("nightsiege stop");
        context.waitTicks(30);
        EventLookTest.onServer(context, (player, m) -> {
            state("coming: stopped", player, m);
            log("STOPPED COMING inventory differ=" + diff(before, player, "stopped") + " kit=" + m.kits().hasStash(player)
                    + " (expect 0 false)");
        });
        context.waitTicks(10);

        // --- 9. /time set back in time: a sane next siege day.
        game.getServer().runCommand("time set 240500");
        context.waitTicks(40);
        EventLookTest.onServer(context, (player, m) -> state("time day 10", player, m));
        context.waitTicks(10);
        game.getServer().runCommand("time set 1000");
        context.waitTicks(40);
        EventLookTest.onServer(context, (player, m) -> {
            long d = m.clockDayFor(player.level().getServer());
            state("time back", player, m);
            log("TIME BACK: day=" + d + " nextSiegeDay=" + m.save().nextSiegeDay + " ahead=" + (m.save().nextSiegeDay - d)
                    + " (expect 0 < ahead <= " + m.config().daysBetweenSieges + ")");
        });
        context.waitTicks(10);

        // --- 10. Peaceful: the real night is called off at the horn (no free win).
        EventLookTest.onServer(context, (player, m) -> {
            m.save().nextSiegeDay = m.clockDayFor(player.level().getServer());
            m.save().clearVotes();
        });
        context.waitTicks(10);
        game.getServer().runCommand("time set 1100");
        context.waitTicks(60);
        EventLookTest.onServer(context, (player, m) -> m.vote(player, true));
        context.waitTicks(20);
        game.getServer().runCommand("difficulty peaceful");
        game.getServer().runCommand("time set 12100");
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        EventLookTest.onServer(context, (player, m) -> {
            state("peaceful", player, m);
            log("PEACEFUL: nightComing=" + m.nightComing(player.level().getServer()) + " kit=" + m.kits().hasStash(player)
                    + " nextSiegeDay=" + m.save().nextSiegeDay + " (expect false false 3)");
        });
        context.waitTicks(10);
        game.getServer().runCommand("difficulty normal");
        game.getServer().runCommand("gamemode creative @p");
        log("done");
    }
}
