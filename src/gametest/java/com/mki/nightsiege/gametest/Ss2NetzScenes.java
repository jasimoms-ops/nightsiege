package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.CurseKind;
import com.mki.nightsiege.core.ExpeditionKind;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.CoreRecord;
import com.mki.nightsiege.game.CrateService;
import com.mki.nightsiege.game.Curses;
import com.mki.nightsiege.game.DungeonContent;
import com.mki.nightsiege.game.ExpeditionLayout;
import com.mki.nightsiege.game.ModContent;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.SiegeSave;
import com.mki.nightsiege.game.SkillService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Survival safety 2, package "netz" (Simon 31.10.): a flood of window actions from one player is cut
 * down (handled actions, window states built, saves written); a profile switch with a kit on is refused
 * outside the class window's countdown (in a dungeon fight the kit stays as it was); /nightsiege skill
 * as a non-defender during a test night changes no attribute; a playtest night takes only its players;
 * a playtest dungeon win gives no night fragment and no crate, and no fragment goes into the core
 * during a playtest; only operators place a core, and only in the overworld; the starter core goes to
 * an operator only. Log lines start with "[ss2-netz]".
 */
final class Ss2NetzScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private Ss2NetzScenes() {
    }

    private static void log(String line) {
        System.out.println("[ss2-netz] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    /** Runs an action on the server, a throw becomes a failed check instead of a lost line. */
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

    // ------------------------------------------------------------------ helpers

    /** Everything the player carries, slot by slot (inventory, armour, off hand). */
    private static List<ItemStack> carried(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            out.add(inv.getItem(i).copy());
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            out.add(player.getItemBySlot(slot).copy());
        }
        return out;
    }

    private static int differences(List<ItemStack> a, List<ItemStack> b) {
        int n = Math.abs(a.size() - b.size());
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                n++;
            }
        }
        return n;
    }

    private static int count(ServerPlayer player, java.util.function.Predicate<ItemStack> what) {
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty() && what.test(inv.getItem(i))) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    private static int fragments(ServerPlayer player) {
        return count(player, s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().startsWith("night_fragment"));
    }

    private static int crates(ServerPlayer player) {
        return count(player, s -> s.is(CrateService.CRATE));
    }

    private static int pendingCrates(SiegeManager m, ServerPlayer player) {
        List<String> list = m.save().pendingCrates == null ? null : m.save().pendingCrates.get(player.getUUID().toString());
        return list == null ? 0 : list.size();
    }

    private static int cores(ServerPlayer player) {
        return count(player, s -> s.is(ModContent.SIEGE_CORE_ITEM));
    }

    @SuppressWarnings("unchecked")
    private static final Holder<Attribute>[] ATTRIBUTES = new Holder[]{Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE,
            Attributes.ATTACK_SPEED, Attributes.ARMOR, Attributes.MOVEMENT_SPEED, Attributes.KNOCKBACK_RESISTANCE,
            Attributes.ENTITY_INTERACTION_RANGE};

    /** The base values the skills set (SkillService), one per attribute. */
    private static List<Double> bases(ServerPlayer player) {
        List<Double> out = new ArrayList<>();
        for (Holder<Attribute> a : ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(a);
            out.add(instance == null ? Double.NaN : instance.getBaseValue());
        }
        return out;
    }

    /** A playtest that runs but does nothing by itself (its tick does not know the phase). */
    private static SiegeSave.PlaytestState fakePlaytest(UUID who) {
        SiegeSave.PlaytestState s = new SiegeSave.PlaytestState();
        s.target = 5;
        s.phase = "ss2-netz";
        s.levels.add(5);
        s.step = 0;
        SiegeSave.PlaytestPlayer pp = new SiegeSave.PlaytestPlayer();
        pp.name = "ss2-netz";
        s.players.put(who.toString(), pp);
        return s;
    }

    private static void toCore(ServerPlayer player, int[] core) {
        player.teleportTo((ServerLevel) player.level(), core[0] + 2.5, core[1], core[2] + 0.5, Set.of(), 0f, 0f, true);
    }

    // ------------------------------------------------------------------ the scene

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.ensureCore(context, game);
        final int[] core = new int[3];
        final String[] keys = new String[2];   // the warrior and the archer profile
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 900 255 true");
        context.waitTicks(10);

        server(context, "setup", (player, m) -> {
            CoreRecord c = m.mainCore();
            core[0] = c.x;
            core[1] = c.y;
            core[2] = c.z;
            boolean wasAdmin = m.isAdmin(player);
            if (!wasAdmin) {
                player.level().getServer().getPlayerList().op(player.nameAndId());
            }
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            if (!own.hasUnlocked(SkillClass.WARRIOR)) {
                own.unlockClass(SkillClass.WARRIOR);
            }
            if (!own.hasUnlocked(SkillClass.ARCHER)) {
                own.unlockClass(SkillClass.ARCHER);
            }
            keys[0] = own.profileFor(SkillClass.WARRIOR);
            keys[1] = own.profileFor(SkillClass.ARCHER);
            own.switchToProfile(keys[0]);
            m.config().nightKits = true;
            m.config().cursesEnabled = true;
            m.config().skillsEnabled = true;
            toCore(player, core);
            log("setup: core=" + c.x + " " + c.y + " " + c.z + " admin before=" + wasAdmin + " now=" + m.isAdmin(player)
                    + " registered=" + m.isRegistered(player) + " profiles=" + own.profiles() + " playing=" + own.chosenClass);
        });
        context.waitTicks(30);
        EventLookTest.closeScreen(context);

        floodPart(context);
        corePart(context, core);
        classWindowPart(context, core, keys);
        skillCommandPart(context, core);
        dungeonPart(context, game, core, keys);

        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }

    // ------------------------------------------------------------------ K1: the flood

    private static void floodPart(ClientGameTestContext context) {
        final int[] at = new int[4];
        context.waitTicks(30);   // the last window is older than the window limit, the bucket is full
        server(context, "flood before", (player, m) -> {
            at[0] = CoreNetworking.admitted();
            at[1] = CoreNetworking.dropped();
            at[2] = m.windowsSent();
            at[3] = m.savesWritten();
        });
        context.waitTicks(5);
        // 500 window actions in one client tick (a modified client).
        context.runOnClient(client -> {
            for (int i = 0; i < 500; i++) {
                ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("x"));
            }
        });
        context.waitTicks(20);
        server(context, "flood windows", (player, m) -> {
            int handled = CoreNetworking.admitted() - at[0];
            int dropped = CoreNetworking.dropped() - at[1];
            int windows = m.windowsSent() - at[2];
            log("flood 500 x \"x\": handled=" + handled + " dropped=" + dropped + " windows sent=" + windows
                    + " saves=" + (m.savesWritten() - at[3]));
            check(handled + dropped >= 500 && handled + dropped <= 505, "every packet counted (" + (handled + dropped) + " of 500)");
            check(handled <= 40, "at most two bursts handled (" + handled + ")");
            check(windows >= 1 && windows <= 3, "1 to 3 window states built, not one per packet (" + windows + ")");
        });
        context.waitTicks(10);
        EventLookTest.closeScreen(context);

        // The curse stones: each click wrote the whole save and told everyone.
        final String curse = CurseKind.values()[0].id();
        server(context, "curse before", (player, m) -> {
            if (m.save().curses != null) {
                m.save().curses.clear();
            }
            m.persist();   // nothing waits
            at[0] = CoreNetworking.admitted();
            at[1] = CoreNetworking.dropped();
            at[2] = m.windowsSent();
            at[3] = m.savesWritten();
        });
        context.waitTicks(30);   // the bucket refills
        context.runOnClient(client -> {
            for (int i = 0; i < 500; i++) {
                ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("curse:" + curse));
            }
        });
        context.waitTicks(4);
        final int[] early = new int[1];
        server(context, "curse flood", (player, m) -> {
            int handled = CoreNetworking.admitted() - at[0];
            int dropped = CoreNetworking.dropped() - at[1];
            early[0] = m.savesWritten() - at[3];
            boolean on = Curses.chosen(m.save()).stream().anyMatch(k -> k.id().equals(curse));
            log("flood 500 x curse: handled=" + handled + " dropped=" + dropped + " saves so far=" + early[0]
                    + " save waiting=" + m.shortSavePending() + " stone on=" + on + " windows=" + (m.windowsSent() - at[2]));
            check(handled <= 40, "curse clicks handled at most two bursts (" + handled + ")");
            check(on, "the stone toggled exactly once (on)");
            check(early[0] <= 1, "no save per click (" + early[0] + " so far)");
        });
        context.waitTicks(60);
        server(context, "curse after", (player, m) -> {
            int saves = m.savesWritten() - at[3];
            log("curse flood after 3 s: saves written=" + saves + " save waiting=" + m.shortSavePending());
            check(saves >= 1 && saves <= 2 && !m.shortSavePending(), "the change written once within two seconds (" + saves + ")");
            m.save().curses.clear();
            m.persist();
        });
        context.waitTicks(10);
        EventLookTest.closeScreen(context);
    }

    // ------------------------------------------------------------------ M3: who may place a core, and where

    private static void corePart(ClientGameTestContext context, int[] core) {
        server(context, "core rights", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            ServerLevel over = server.overworld();
            ServerLevel nether = server.getLevel(Level.NETHER);
            int coresBefore = m.save().cores.size();
            FakePlayer guest = FakePlayer.get(over);
            log("core rights: host admin=" + m.isAdmin(player) + " guest admin=" + m.isAdmin(guest)
                    + " host here=" + SiegeManager.coreRefusal(player, over) + " guest here=" + SiegeManager.coreRefusal(guest, over)
                    + " host nether=" + SiegeManager.coreRefusal(player, nether));
            check(SiegeManager.coreRefusal(player, over) == null, "an operator may place a core in the overworld");
            check("nightsiege.msg.core_op_only".equals(SiegeManager.coreRefusal(guest, over)), "a non-operator may not");
            check("nightsiege.msg.core_overworld_only".equals(SiegeManager.coreRefusal(player, nether)),
                    "not in the Nether, not even an operator");

            // The real placement by a non-operator: nothing placed, the item stays.
            BlockPos target = new BlockPos(core[0] + 6, core[1], core[2] + 6);
            over.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            over.setBlock(target.above(), Blocks.AIR.defaultBlockState(), 3);
            over.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
            guest.setPos(target.getX() + 3.5, target.getY(), target.getZ() + 3.5);
            guest.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.SIEGE_CORE_ITEM));
            InteractionResult r = guest.getMainHandItem().useOn(new UseOnContext(guest, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target.below()), Direction.UP, target.below(), false)));
            boolean placed = ModContent.isCore(over.getBlockState(target));
            log("core by a non-operator: result=" + r + " placed=" + placed + " item=" + guest.getMainHandItem().getCount()
                    + " cores " + coresBefore + "->" + m.save().cores.size());
            check(!placed && guest.getMainHandItem().getCount() == 1 && m.save().cores.size() == coresBefore,
                    "a non-operator's core is refused and kept");
            guest.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            // The real placement in the Nether.
            if (nether != null) {
                FakePlayer netherGuest = FakePlayer.get(nether);
                BlockPos np = new BlockPos(0, 100, 0);
                nether.setBlock(np.below(), Blocks.NETHERRACK.defaultBlockState(), 3);
                nether.setBlock(np, Blocks.AIR.defaultBlockState(), 3);
                netherGuest.setPos(3.5, 100, 3.5);
                netherGuest.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.SIEGE_CORE_ITEM));
                InteractionResult nr = netherGuest.getMainHandItem().useOn(new UseOnContext(netherGuest, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(np.below()), Direction.UP, np.below(), false)));
                boolean netherPlaced = ModContent.isCore(nether.getBlockState(np));
                log("core in the Nether: result=" + nr + " placed=" + netherPlaced + " item=" + netherGuest.getMainHandItem().getCount()
                        + " reason=" + SiegeManager.coreRefusal(netherGuest, nether));
                check(!netherPlaced && netherGuest.getMainHandItem().getCount() == 1, "a core in the Nether is refused and kept");
                netherGuest.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                // Any other way a core block gets set in the Nether: it goes again and is not counted.
                BlockPos np2 = np.east(2);
                nether.setBlock(np2, ModContent.SIEGE_CORE.defaultBlockState(), 3);
                m.onCorePlaced(nether, np2, null);
                boolean stays = ModContent.isCore(nether.getBlockState(np2));
                log("core block set in the Nether: stays=" + stays + " cores=" + m.save().cores.size());
                check(!stays && m.save().cores.size() == coresBefore, "a core block set in the Nether is taken away, not counted");
            } else {
                check(false, "no Nether level");
            }

            // The starter core: not to a non-operator (it waits), then to the first operator.
            boolean given = m.save().starterCoreGiven;
            m.config().giveStarterCore = true;
            m.save().starterCoreGiven = false;
            m.giveStarterCore(guest);
            boolean guestGot = cores(guest) > 0;
            boolean waits = !m.save().starterCoreGiven;
            int hostBefore = cores(player);
            m.giveStarterCore(player);
            int hostAfter = cores(player);
            log("starter core: guest got=" + guestGot + " still waiting=" + waits + " host cores " + hostBefore + "->" + hostAfter
                    + " given now=" + m.save().starterCoreGiven + " (before the scene " + given + ")");
            check(!guestGot && waits, "no starter core for a non-operator, it waits");
            check(hostAfter == hostBefore + 1 && m.save().starterCoreGiven, "the starter core goes to the operator");
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(ModContent.SIEGE_CORE_ITEM)) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
            }
        });
        context.waitTicks(20);
    }

    // ------------------------------------------------------------------ M1 + H1: the class window, playtest nights

    private static void classWindowPart(ClientGameTestContext context, int[] core, String[] keys) {
        final List<ItemStack> kit = new ArrayList<>();
        server(context, "playtest night", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            ServerLevel level = (ServerLevel) player.level();
            toCore(player, core);
            BlockPos anchor = new BlockPos(core[0], core[1], core[2]);
            // A playtest of someone else: its night does not take the host along.
            m.save().playtest = fakePlaytest(UUID.randomUUID());
            boolean started = m.prepareTest(server, level, anchor, 5, SiegeEvent.NONE);
            log("playtest night without the host: started=" + started + " host kit=" + m.kits().hasStash(player));
            check(!started && !m.kits().hasStash(player), "a playtest night does not pull in a player outside the playtest");
            if (started) {
                m.cancelPreparation();
            }
            // H2: no night fragment into the core while a playtest runs.
            Item fragment = DungeonContent.NIGHT_FRAGMENTS.values().iterator().next();
            String before = m.save().nightFragment;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fragment));
            InteractionResult r = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(player, level,
                    InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false));
            log("fragment into the core in a playtest: result=" + r + " fragment " + before + "->" + m.save().nightFragment
                    + " in hand=" + player.getMainHandItem().getCount());
            check(java.util.Objects.equals(before, m.save().nightFragment) && player.getMainHandItem().is(fragment),
                    "no night fragment into the core during a playtest, it stays in the hand");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            m.save().playtest = null;

            // The admin's test night still takes the registered player at the core, with the class window.
            boolean admin = m.prepareTest(server, level, anchor, 5, SiegeEvent.NONE);
            log("admin test night: started=" + admin + " kit=" + m.kits().hasStash(player) + " class window=" + m.inClassWindow(player));
            check(admin && m.kits().hasStash(player), "the admin's test night takes the registered player near the core");
        });
        context.waitTicks(30);
        EventLookTest.closeScreen(context);
        server(context, "switch in the countdown", (player, m) -> {
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            boolean window = m.inClassWindow(player);
            List<ItemStack> before = carried(player);
            m.onPlayClass(player, keys[1]);
            int changed = differences(before, carried(player));
            log("countdown: class window=" + window + " switch to " + keys[1] + " -> playing " + own.chosenClass
                    + ", kit slots changed=" + changed);
            check(window && keys[1].equals(own.chosenClass) && changed > 0, "in the class window's countdown the switch works (new kit)");
            kit.addAll(carried(player));
            m.onPlayClass(player, keys[0]);   // the window was confirmed: no second fresh kit
            int again = differences(kit, carried(player));
            log("after the confirm: switch back to " + keys[0] + " -> playing " + own.chosenClass + ", kit slots changed=" + again);
            check(keys[1].equals(own.chosenClass) && again == 0, "after the confirm the next switch with the kit on is refused");
            m.cancelPreparation();
        });
        context.waitTicks(30);
        EventLookTest.closeScreen(context);
        server(context, "switch without kit", (player, m) -> {
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            m.onPlayClass(player, keys[0]);
            log("without a kit: switch to " + keys[0] + " -> playing " + own.chosenClass + " kit=" + m.kits().hasStash(player));
            check(keys[0].equals(own.chosenClass) && !m.kits().hasStash(player), "without a kit the switch works as before");
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);
    }

    // ------------------------------------------------------------------ H3: /nightsiege skill

    private static void skillCommandPart(ClientGameTestContext context, int[] core) {
        server(context, "skill command", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            ServerLevel level = (ServerLevel) player.level();
            toCore(player, core);
            boolean started = m.startTest(server, level, new BlockPos(core[0], core[1], core[2]), 5, SiegeEvent.NONE);
            // Out of tonight's defenders (as one who declined), in the same tick.
            m.testJoin(player.getUUID(), false);
            SkillService.clear(player);
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            SkillTree tree = m.skillTree();
            own.award(60);
            // The profile holds some life from its skills (what an apply would give).
            SkillNode hp = null;
            for (SkillNode n : tree.ofClass(own.clazz())) {
                if (n.effect() == SkillEffect.MAX_HEALTH && own.levelOf(n.id()) == 0) {
                    hp = n;
                    break;
                }
            }
            if (hp != null && tree.totalBonus(SkillEffect.MAX_HEALTH, own) <= 0) {
                own.setLevel(hp.id(), 1);
            }
            SkillNode buy = null;
            for (SkillNode n : tree.ofClass(own.clazz())) {
                if (tree.canUnlock(n.id(), own)) {
                    buy = n;
                    break;
                }
            }
            List<Double> before = bases(player);
            int levelBefore = buy == null ? 0 : own.levelOf(buy.id());
            int result = -1;
            if (buy != null) {
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "nightsiege skill " + buy.id());
                result = 1;
            }
            List<Double> after = bases(player);
            log("skill command: night active=" + m.isActive() + " started=" + started + " defending=" + m.isDefending(player)
                    + " node=" + (buy == null ? "none" : buy.id()) + " level " + levelBefore + "->" + (buy == null ? 0 : own.levelOf(buy.id()))
                    + " hp bonus held=" + tree.totalBonus(SkillEffect.MAX_HEALTH, own) + " result=" + result
                    + " attributes " + before + " -> " + after);
            check(m.isActive() && !m.isDefending(player), "a test night runs, the player is no defender");
            check(buy != null && own.levelOf(buy.id()) == levelBefore + 1, "the level is bought (it holds from the next night)");
            check(before.equals(after), "no attribute changed for the non-defender");
            m.abort(server);
        });
        context.waitTicks(40);
        EventLookTest.closeScreen(context);
        server(context, "after the test night", (player, m) -> log("after the test night: active=" + m.isActive()
                + " kit=" + m.kits().hasStash(player) + " max health=" + player.getMaxHealth()));
        context.waitTicks(10);
    }

    // ------------------------------------------------------------------ H1 + H2: a playtest dungeon

    private static void dungeonPart(ClientGameTestContext context, TestSingleplayerContext game, int[] core, String[] keys) {
        int fx = core[0] - 340;
        int bz = core[2] + 220;
        int top = -6;
        int rooms = 1;
        int cz = 20 + 43 * rooms + 38;   // the arena's middle (ExpeditionLayout.arenaCenter)
        int fy = top;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(fx - 34, bz - 14, fx + 34, bz + cz + 32));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(fx - 8, -60, bz - 10, fx + 8, top - 2, bz + 7));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(fx - 8, top - 1, bz - 10, fx + 8, top - 1, bz + 7));
        context.waitTicks(40);
        final int[] before = new int[2];
        server(context, "dungeon build", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            m.expeditions().testBuild(level, new BlockPos(fx, fy, bz), 0, ExpeditionKind.STONE_CRYPT, rooms);
            m.save().playtest = fakePlaytest(player.getUUID());
            before[0] = crates(player);
            before[1] = pendingCrates(m, player);
            log("dungeon built, playtest running=" + m.inPlaytest() + " level=" + m.currentLevel()
                    + " crates=" + before[0] + " pending=" + before[1] + " fragments=" + fragments(player));
        });
        context.waitTicks(10);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 900 255 true");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                fx + 2, fy, bz - 4, fx, fy, bz + 2));
        context.waitTicks(20);
        server(context, "seal", (player, m) -> {
            ExpeditionLayout.Plan plan = m.expeditions().testPlan();
            boolean used = m.expeditions().onSeal(player, plan.seal);
            log("seal: used=" + used + " inRound=" + m.expeditions().inRound(player) + " class window=" + m.inClassWindow(player));
        });
        context.waitTicks(30);
        server(context, "ready", (player, m) -> m.onPlayClass(player, m.save().skillsOf(player.getUUID().toString()).chosenClass));
        context.waitTicks(10);
        EventLookTest.closeScreen(context);
        context.waitTicks(50);
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                fx, fy - 12, bz + 12, fx, fy - 16, bz + 20));
        context.waitTicks(30);
        EventLookTest.closeScreen(context);
        context.waitTicks(20);

        // H1: a profile switch in the fight, the kit on.
        server(context, "switch in the fight", (player, m) -> {
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            boolean fighter = m.expeditions().isFighter(player);
            boolean kit = m.kits().hasStash(player);
            if (!kit) {
                log("no kit from the dungeon (fighter=" + fighter + "), equip by hand");
                m.kits().equip(player, m.currentLevel());
            }
            String playing = own.chosenClass;
            List<ItemStack> was = carried(player);
            m.onPlayClass(player, keys[1].equals(playing) ? keys[0] : keys[1]);
            int changed = differences(was, carried(player));
            log("fight: fighter=" + fighter + " kit=" + m.kits().hasStash(player) + " class window=" + m.inClassWindow(player)
                    + " playing " + playing + " -> " + own.chosenClass + ", kit slots changed=" + changed
                    + " | " + m.expeditions().testState());
            check(fighter && kit, "in the dungeon fight with the kit on");
            check(playing.equals(own.chosenClass) && changed == 0, "the profile switch in the fight is refused, the kit is as it was");
        });
        context.waitTicks(10);

        // Through the hall and the arena to the champion.
        int z0 = bz + 32;
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                fx, fy - 20, z0 + 2, fx, fy - 19, z0 + 14));
        context.waitTicks(90);
        EventLookTest.killDungeonMobs(context, "ss2-netz room 1");
        context.waitTicks(25);
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d.5 %d %d.5",
                fx, fy - 24, bz + cz - 14, fx, fy - 24, bz + cz));
        context.waitTicks(100);
        EventLookTest.killDungeonMobs(context, "ss2-netz wave 1");
        boolean won = false;
        for (int i = 0; i < 14 && !won; i++) {
            context.waitTicks(50);
            EventLookTest.killDungeonMobs(context, "ss2-netz champion " + (i + 1));
            context.waitTicks(5);
            won = context.computeOnClient(client -> {
                SiegeSave.ExpeditionRecord rec = SiegeManager.get().save().expedition;
                return rec != null && rec.won;
            });
        }
        context.waitTicks(20);
        final boolean wonFinal = won;
        server(context, "dungeon won", (player, m) -> {
            int crates = crates(player);
            int pending = pendingCrates(m, player);
            int frags = fragments(player);
            log("playtest dungeon: won=" + wonFinal + " kit=" + m.kits().hasStash(player) + " fragments=" + frags
                    + " crates " + before[0] + "->" + crates + " pending " + before[1] + "->" + pending
                    + " core fragment=" + m.save().nightFragment);
            check(wonFinal, "the playtest dungeon is won");
            check(frags == 0, "no night fragment from the playtest dungeon");
            check(crates == before[0] && pending == before[1], "no real crate from the playtest dungeon");
            m.save().playtest = null;
            m.persist();
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);
    }
}
