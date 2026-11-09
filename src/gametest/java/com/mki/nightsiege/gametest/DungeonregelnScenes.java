package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.AbilityKeys;
import com.mki.nightsiege.core.GainCharges;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.Combo;
import com.mki.nightsiege.game.Companions;
import com.mki.nightsiege.game.EngineerAbilities;
import com.mki.nightsiege.game.FriendlyFire;
import com.mki.nightsiege.game.GainKeys;
import com.mki.nightsiege.game.SecondUlts;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scene "dungeonregeln" (Simon 09.11.: "im dungeon müssen ... genau die selben Regeln gelten wie in der Nacht";
 * "bogen macht anderen spieler schaden, klingenmeister kann nur normal schnell schlagen"). The same checks first
 * in a test night (the counter-check), then in a dungeon fight run as the playtest runs it (noRetry): an arrow of
 * a second player (FakePlayer) does 0 damage and FriendlyFire.allow is false; the blademaster's Kombo gives the
 * same hits per second as in the night and goes when the fight ends; one subclass per class: every key fires
 * (pause or statistics), 1 picks no hotbar place, I opens the inventory, a charge on 1 is used. Survival with
 * the kit. Lines "[dungeonregeln] OK/FAIL ...", summary "x von y"; FAIL with 0 checks.
 */
final class DungeonregelnScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static final String HUSK = "ns_dr_husk";
    private static final double[] HOME = new double[3];
    private static final List<String> SUBS = List.of(SkillTree.SUB_SWIFT, SkillTree.SUB_BLADEMASTER,
            SkillTree.SUB_MEDIC, SkillTree.SUB_GUNNER, SkillTree.SUB_TAMER);
    private static final List<String> KEY_GAINS = List.of("kletterhaken", "kampfhorn", "tarnumhang", "signalhorn",
            "mauerkelle", "ersatzbatterie", "oelfass", "rueckrufpfeife", "geisterross", "futternapf", "totenglocke");
    private static final double[] COMBO = {-1, -1};

    private DungeonregelnScenes() {
    }

    private static void log(String line) {
        System.out.println("[dungeonregeln] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            FAILS.add(what);
        }
        log((ok ? "OK   " : "FAIL ") + what);
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

    private static List<Mob> husks(ServerLevel level) {
        List<Mob> out = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(HOME[0] - 12, HOME[1] - 6, HOME[2] - 12,
                HOME[0] + 12, HOME[1] + 6, HOME[2] + 12), e -> e.entityTags().contains(HUSK))) {
            out.add(mob);
        }
        return out;
    }

    private static void home(ServerPlayer player, SiegeManager m) {
        player.teleportTo(HOME[0], HOME[1], HOME[2]);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.resetFallDistance();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        COMBO[0] = -1;
        COMBO[1] = -1;
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        try {
            night(context, game);
        } catch (RuntimeException e) {
            check(false, "night: " + e);
            e.printStackTrace(System.out);
        }
        try {
            dungeon(context, game);
        } catch (RuntimeException e) {
            check(false, "dungeon: " + e);
            e.printStackTrace(System.out);
        }
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(10);
        if (checks == 0) {
            check(false, "no check ran");
        }
        log("summary: " + (checks - FAILS.size()) + " von " + checks + " OK" + (FAILS.isEmpty() ? "" : ", FAIL: " + FAILS.size()));
        System.out.println("[Night Siege test] dungeonregeln: " + (checks - FAILS.size()) + " von " + checks);
    }

    // ------------------------------------------------------------------ the night (counter-check)

    private static void night(ClientGameTestContext context, TestSingleplayerContext game) {
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 1200 4 true");
        context.waitTicks(10);
        final int[] saved = new int[2];
        final boolean[] refill = new boolean[1];
        server(context, "night start", (player, m) -> {
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
            log("night prepared=" + m.prepareTest(level.getServer(), level, player.blockPosition(), 1,
                    com.mki.nightsiege.core.SiegeEvent.NONE));
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "night clear", (player, m) -> {
            ((ServerLevel) player.level()).getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(160),
                    e -> e instanceof net.minecraft.world.entity.monster.Enemy).forEach(Mob::discard);
            check(m.isActive() && m.isDefending(player), "night: the player defends the test night");
            HOME[0] = player.getX();
            HOME[1] = player.getY();
            HOME[2] = player.getZ();
        });
        context.waitTicks(5);
        rules(context, game, "night", false);
        server(context, "night end", (player, m) -> {
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            m.config().refillSpawns = refill[0];
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
        });
        context.waitTicks(20);
    }

    // ------------------------------------------------------------------ the dungeon (as the playtest runs it)

    private static void dungeon(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("gamemode creative @p");
        int fx = pos.getX() + 520;
        int bz = pos.getZ() - 40;
        int fy = EventLookTest.dungeonGround(context, game, fx, bz, 1);
        server(context, "dungeon build", (player, m) -> {
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            m.expeditions().testBuild((ServerLevel) player.level(), new BlockPos(fx, fy, bz), 0,
                    com.mki.nightsiege.core.ExpeditionKind.STONE_CRYPT, 1);
            m.expeditions().openEntry(true);   // straight in, no ready round
            m.expeditions().noRetry(true);   // as the playtest's dungeon (Playtest: one attempt)
        });
        context.waitTicks(10);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 1200 4 true");
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5", fx, fy - 20, bz + 45));
        context.waitTicks(40);
        EventLookTest.closeScreen(context);
        server(context, "dungeon join", (player, m) -> {
            if (!m.expeditions().isFighter(player)) {
                m.expeditions().testJoin(player);
            }
            for (Mob mob : m.expeditions().testMobs((ServerLevel) player.level())) {
                mob.setNoAi(true);
            }
            check(!m.isActive() && m.expeditions().isFighter(player), "dungeon: the player fights in the dungeon (no night) "
                    + m.expeditions().testState());
            HOME[0] = player.getX();
            HOME[1] = player.getY();
            HOME[2] = player.getZ();
        });
        context.waitTicks(10);
        EventLookTest.closeScreen(context);
        rules(context, game, "dungeon", true);
        server(context, "dungeon end", (player, m) -> {
            m.expeditions().testEndAll(player.level().getServer(), false);
        });
        context.waitTicks(10);
        server(context, "dungeon after", (player, m) -> {
            check(!Combo.active(player), "dungeon: the Kombo goes with the end of the fight (speed "
                    + String.format(Locale.ROOT, "%.2f", player.getAttributeValue(Attributes.ATTACK_SPEED)) + ")");
            m.expeditions().removeAll(player.level().getServer());
            m.expeditions().openEntry(false);
            m.expeditions().noRetry(false);
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            m.save().skillsOf(player.getUUID().toString()).reset();
        });
        context.waitTicks(10);
    }

    // ------------------------------------------------------------------ the rules, the same in both

    private static void rules(ClientGameTestContext context, TestSingleplayerContext game, String where, boolean dungeon) {
        // 1. Friendly fire: a second player's arrow does nothing.
        server(context, where + " friendly fire", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            net.fabricmc.fabric.api.entity.FakePlayer friend = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
            if (dungeon) {
                m.expeditions().testFighter(friend.getUUID(), true);
            } else {
                m.testJoin(friend.getUUID(), true);
            }
            boolean blocked = !FriendlyFire.allow(player, level.damageSources().mobProjectile(friend, friend));
            player.removeEffect(MobEffects.RESISTANCE);
            player.invulnerableTime = 0;
            player.setHealth(player.getMaxHealth());
            float hp = player.getHealth();
            boolean hurt = player.hurtServer(level, level.damageSources().mobProjectile(friend, friend), 4.0f);
            float lost = hp - player.getHealth();
            if (dungeon) {
                m.expeditions().testFighter(friend.getUUID(), false);
            } else {
                m.testJoin(friend.getUUID(), false);
            }
            boolean stranger = FriendlyFire.allow(player, level.damageSources().mobProjectile(friend, friend));
            check(blocked && !hurt && lost <= 0.0f, where + ": an arrow of a fellow fighter does 0 damage (allow="
                    + !blocked + " hurt=" + hurt + " lost=" + lost + ")");
            check(stranger, where + ": a player outside the fight is no fellow (allow=" + stranger + ")");
            player.setHealth(player.getMaxHealth());
        });
        game.getServer().runCommand("effect give @p minecraft:resistance 1200 4 true");
        context.waitTicks(5);

        // 2. One subclass per class: the keys, 1-3, I, a charge; the blademaster's Kombo.
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        String husk = "summon minecraft:husk %s %s %s {NoAI:1b,PersistenceRequired:1b,Tags:[\"nightsiege\",\"" + HUSK + "\"]}";
        for (double side : new double[]{0, 1.2, -1.2}) {
            game.getServer().runCommand(String.format(Locale.ROOT, husk, HOME[0] + 3.5, HOME[1], HOME[2] + side));
        }
        context.waitTicks(5);
        final List<String> learned = new ArrayList<>();
        final Set<String> subNodes = new HashSet<>();
        SkillTree.SUBCLASSES.forEach(i -> subNodes.add(i.node()));
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            if (!SUBS.contains(info.key())) {
                continue;
            }
            String tag = where + " " + info.key();
            final List<String> presses = new ArrayList<>();
            server(context, tag + " home", DungeonregelnScenes::home);
            context.waitTicks(5);
            server(context, tag + " setup", (player, m) -> {
                for (Mob h : husks((ServerLevel) player.level())) {
                    h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
                    h.setHealth(1000f);
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
                List<String> gains = new ArrayList<>(KEY_GAINS);
                String second = SecondUlts.gainFor(info.clazz());
                if (second != null) {
                    gains.add(second);
                }
                for (String g : gains) {
                    own.setGainStage(info.clazz(), g, 1);
                }
                own.setGainStage(info.clazz(), GainCharges.POTION, 2);
                if (own.gainKeyChoice != null) {
                    own.gainKeyChoice.remove(info.clazz().name());
                }
                m.kits().equip(player, 10);
                KlassencheckScenes.ready(player, m);
                m.sendUltHud(player);
                List<UltState.Slot> slots = m.abilitySlots(player);
                for (int i = 0; i < slots.size(); i++) {
                    presses.add("slot:" + i);
                }
                if (AbilityKeys.keyOf(info.key(), AbilityKeys.F_ACTION) != null) {
                    presses.add("ult2");
                }
                presses.add("ult");
                if (second != null && GainKeys.assigned(m, own, info.clazz()).contains(second)) {
                    presses.add("ult2x");
                }
                List<String> gk = GainKeys.assigned(m, own, info.clazz());
                for (int i = 0; i < Math.min(3, gk.size()); i++) {
                    presses.add("gain:" + i);
                }
                log("     " + tag + ": presses " + presses + " gains " + gk);
            });
            context.waitTicks(25);   // the bar and the key ownership reach the client
            if (SkillTree.SUB_BLADEMASTER.equals(info.key())) {
                combo(context, where, dungeon ? 1 : 0);
            }
            for (String press : presses) {
                press(context, tag, info.clazz(), press);
            }
            client(context, tag, info.clazz());
            server(context, tag + " end", (player, m) -> {
                player.setHealth(player.getMaxHealth());
                player.removeAllEffects();
            });
            game.getServer().runCommand("effect give @p minecraft:resistance 1200 4 true");
            context.waitTicks(3);
        }
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        context.waitTicks(3);
    }

    /** The Kombo: a sword in hand, w_combo learned; the attack speed (hits a second) as in the night. */
    private static void combo(ClientGameTestContext context, String where, int index) {
        server(context, where + " combo", (player, m) -> {
            m.save().skillsOf(player.getUUID().toString()).setLevel("w_combo", 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        });
        context.waitTicks(15);   // Combo.tick every 5 ticks
        server(context, where + " combo read", (player, m) -> {
            double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
            double want = com.mki.nightsiege.core.SkillValues.get("w_combo", "hits", Combo.HITS_PER_SECOND);
            COMBO[index] = speed;
            check(Combo.active(player) && Math.abs(speed - want) < 0.01, where + ": the blademaster hits "
                    + String.format(Locale.ROOT, "%.2f", speed) + " a second (want " + want + ")");
            if (index == 1) {
                check(COMBO[0] > 0 && Math.abs(COMBO[0] - speed) < 0.01, "dungeon: as many hits a second as in the night ("
                        + String.format(Locale.ROOT, "%.2f / %.2f", COMBO[0], speed) + ")");
            }
        });
        context.waitTicks(2);
    }

    /** One key pressed on the server: its pause starts or the statistics count it. */
    private static void press(ClientGameTestContext context, String tag, SkillClass clazz, String press) {
        final int[] pauseBefore = new int[1];
        final int[] usesBefore = new int[1];
        final String[] key = new String[1];
        final String[] what = {press};
        final boolean[] skip = new boolean[1];
        server(context, tag + " " + press + " place", DungeonregelnScenes::home);
        context.waitTicks(3);
        server(context, tag + " " + press, (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            player.setHealth(player.getMaxHealth() * 0.5f);
            player.setAbsorptionAmount(0f);
            List<Mob> targets = husks(level);
            if (!targets.isEmpty()) {
                Mob t = targets.get(0);
                if (press.equals("ult")) {
                    t.setHealth(t.getMaxHealth() * 0.1f);
                }
                player.lookAt(EntityAnchorArgument.Anchor.EYES, t.position().add(0, t.getBbHeight() * 0.5, 0));
                if (Companions.get() != null && clazz == SkillClass.SUMMONER) {
                    for (Mob h : targets) {
                        Companions.get().onMonsterDeath(h, false);
                    }
                }
            }
            if (press.startsWith("slot:")) {
                List<UltState.Slot> slots = m.abilitySlots(player);
                UltState.Slot s = slots.get(Integer.parseInt(press.substring(5)));
                what[0] = s.key + "(" + s.id + ")";
                if ("wall".equals(s.id)) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition().add(-5, 0, 0));
                }
                if (EngineerAbilities.TARGET.equals(s.id) && !targets.isEmpty()) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, targets.get(0).position());
                }
            }
            if (press.startsWith("gain:")) {
                PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
                List<String> gk = GainKeys.assigned(m, own, own.clazz());
                int gi = Integer.parseInt(press.substring(5));
                what[0] = press + "(" + (gi < gk.size() ? gk.get(gi) : "?") + ")";
            }
            key[0] = KlassencheckScenes.statsKey(player, m, press);
            usesBefore[0] = uses(player, m, key[0]);
            pauseBefore[0] = KlassencheckScenes.pause(player, m, press);
            skip[0] = pauseBefore[0] > 0;
            m.onCoreAction(player, press);
        });
        context.waitTicks(10);
        server(context, tag + " " + press + " after", (player, m) -> {
            if (skip[0]) {
                log("SKIP " + tag + ": " + what[0] + " still in its pause (" + pauseBefore[0] + ")");
            } else {
                int left = KlassencheckScenes.pause(player, m, press);
                int uses = uses(player, m, key[0]);
                boolean fired = left > 0 || key[0] != null && uses > usesBefore[0];
                check(fired, tag + ": " + what[0] + " fires (pause " + left + ", counted " + usesBefore[0] + " -> " + uses + ")");
                if (key[0] != null) {
                    check(uses > usesBefore[0], tag + ": " + what[0] + " counted in the statistics on " + key[0]);
                }
            }
            KlassencheckScenes.ready(player, m);
        });
        context.waitTicks(clazz == SkillClass.SUMMONER ? 9 : 2);
    }

    /** Key uses in the night's statistics, or in the dungeon's (camp's) attempt file. */
    private static int uses(ServerPlayer player, SiegeManager m, String key) {
        if (key == null) {
            return 0;
        }
        return m.isActive() ? m.stats().testKeyUses(player, key) : m.stats().testAttemptKeyUses(player, key);
    }

    /** The client: the keys are the mod's; 1 picks no hotbar place but uses a charge; I opens the inventory. */
    private static void client(ClientGameTestContext context, String tag, SkillClass clazz) {
        final int[] charges = new int[1];
        server(context, tag + " charge", (player, m) -> {
            GainKeys.choose(m, player, clazz.name(), GainCharges.POTION);
            GainKeys.pauseForTests(player, GainCharges.POTION, 0);
            m.sendUltHud(player);
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            charges[0] = GainKeys.chargesLeft(m, player, GainCharges.POTION);
            log("     " + tag + ": key 1 = " + GainKeys.assigned(m, own, clazz) + " charges " + charges[0]);
        });
        context.waitTicks(10);
        EventLookTest.closeScreen(context);
        boolean owned = context.computeOnClient(c -> com.mki.nightsiege.client.NightKeys.owned());
        check(owned, tag + ": the keys are Night Siege's (NightKeys.owned)");
        context.runOnClient(c -> c.player.getInventory().setSelectedSlot(5));
        context.waitTicks(3);
        KeyMapping one = context.computeOnClient(c -> com.mki.nightsiege.client.GainClient.gainKey(0));
        context.getInput().pressKey(one);
        context.waitTicks(8);
        int selected = context.computeOnClient(c -> c.player.getInventory().getSelectedSlot());
        check(selected == 5, tag + ": key 1 picks no hotbar place (selected " + (selected + 1) + ", want 6)");
        server(context, tag + " charge used", (player, m) -> {
            int left = GainKeys.chargesLeft(m, player, GainCharges.POTION);
            check(charges[0] > 0 && left == charges[0] - 1, tag + ": key 1 uses a charge (" + charges[0] + " -> " + left + ")");
        });
        context.waitTicks(3);
        KeyMapping inv = context.computeOnClient(c -> com.mki.nightsiege.client.GainClient.inventoryKey());
        context.getInput().pressKey(inv);
        context.waitTicks(8);
        boolean open = context.computeOnClient(c -> c.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen);
        check(open, tag + ": I opens the inventory");
        EventLookTest.closeScreen(context);
        context.waitTicks(3);
    }
}
