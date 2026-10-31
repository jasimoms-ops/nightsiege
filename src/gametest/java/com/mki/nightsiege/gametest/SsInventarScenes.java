package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.game.CrateService;
import com.mki.nightsiege.game.KitService;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.SiegeSave;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Survival safety, package "inventar" (Simon 31.10.): the kit swap of a real night with an own
 * survival inventory. Things taken from a chest with the kit on stay after the night, a crate won
 * with the kit on comes after the restore, the own inventory comes back slot by slot, a kit item on
 * the cursor or in the crafting grid is gone. Then the swap after a crash (half done either way), an
 * unreadable stash entry, a restore on a dead player, a new kit after a death before the night and
 * a second playtest that must not overwrite a waiting restore. Log lines start with "[ss-inventar]".
 */
final class SsInventarScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private SsInventarScenes() {
    }

    private static void log(String line) {
        System.out.println("[ss-inventar] " + line);
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

    // ------------------------------------------------------------------ helpers on the server

    private static List<ItemStack> snapshot(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            out.add(inv.getItem(i).copy());
        }
        return out;
    }

    /** Slots of {@code before} with something in them that differ now (item, count or components). */
    private static List<String> differ(ServerPlayer player, List<ItemStack> before) {
        List<String> diff = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < before.size() && i < inv.getContainerSize(); i++) {
            ItemStack b = before.get(i);
            if (!b.isEmpty() && !ItemStack.matches(b, inv.getItem(i))) {
                diff.add("slot " + i + ": " + describe(b) + " -> " + describe(inv.getItem(i)));
            }
        }
        return diff;
    }

    private static String describe(ItemStack stack) {
        if (stack.isEmpty()) {
            return "-";
        }
        String name = stack.has(DataComponents.CUSTOM_NAME) ? " \"" + stack.getHoverName().getString() + "\"" : "";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + " x" + stack.getCount() + name
                + (KitService.isKit(stack) ? " [kit]" : "");
    }

    /** Kit items anywhere on the player: inventory, both cursors, the 2x2 grid. */
    private static int kitAnywhere(ServerPlayer player) {
        int n = EventLookTest.kitCount(player.getInventory());
        if (KitService.isKit(player.inventoryMenu.getCarried())) {
            n += player.inventoryMenu.getCarried().getCount();
        }
        if (player.containerMenu != player.inventoryMenu && KitService.isKit(player.containerMenu.getCarried())) {
            n += player.containerMenu.getCarried().getCount();
        }
        for (int s = 1; s <= 4; s++) {
            if (KitService.isKit(player.inventoryMenu.getSlot(s).getItem())) {
                n += player.inventoryMenu.getSlot(s).getItem().getCount();
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

    private static int crates(ServerPlayer player) {
        return count(player, s -> s.is(CrateService.CRATE));
    }

    private static int pending(SiegeManager manager, ServerPlayer player) {
        List<String> list = manager.save().pendingCrates == null ? null : manager.save().pendingCrates.get(player.getUUID().toString());
        return list == null ? 0 : list.size();
    }

    private static SiegeSave.KitStash stash(SiegeManager manager, ServerPlayer player) {
        return manager.save().kitStashes == null ? null : manager.save().kitStashes.get(player.getUUID().toString());
    }

    private static void respawn(ServerPlayer player) {
        player.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
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
        final List<ItemStack> before = new ArrayList<>();
        final List<String> stashAtHorn = new ArrayList<>();
        final BlockPos[] chestPos = new BlockPos[1];
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("difficulty normal");
        game.getServer().runCommand("gamerule advance_time true");
        game.getServer().runCommand("gamerule doDaylightCycle true");
        game.getServer().runCommand("gamerule keepInventory false");
        game.getServer().runCommand("time set 500");
        context.waitTicks(10);

        // 1. A survival inventory, a chest with things to take in the night, an own item on the cursor
        //    and one in the 2x2 grid when the horn sounds.
        server(context, "setup", (player, manager) -> {
            com.mki.nightsiege.game.CoreRecord c = manager.mainCore();
            core[0] = c.x;
            core[1] = c.y;
            core[2] = c.z;
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            if (manager.kits().hasStash(player)) {
                manager.kits().restore(player);   // a kit of a scene before
            }
            com.mki.nightsiege.core.PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(SkillClass.WARRIOR);
            own.switchTo(SkillClass.WARRIOR);
            HolderLookup.RegistryLookup<net.minecraft.world.item.enchantment.Enchantment> ench =
                    player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Inventory inv = player.getInventory();
            inv.clearContent();
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            ench.get(Enchantments.SHARPNESS).ifPresent(h -> sword.enchant(h, 5));
            sword.set(DataComponents.CUSTOM_NAME, Component.literal("Simons Schwert"));
            sword.set(DataComponents.DAMAGE, 120);
            ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
            shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                    new ItemStack(Items.DIAMOND_BLOCK, 9), new ItemStack(Items.NETHERITE_INGOT, 4))));
            inv.setItem(0, sword);
            inv.setItem(1, shulker);
            inv.setItem(2, new ItemStack(Items.ENDER_PEARL, 16));
            inv.setItem(3, PotionContents.createItemStack(Items.POTION, Potions.HEALING));
            inv.setItem(4, CrateService.crate(SkillClass.WARRIOR, CrateKind.NORMAL, 9));   // an own crate (number types)
            inv.setItem(5, new ItemStack(Items.TORCH, 64));
            net.minecraft.world.item.Item[] fill = {Items.COBBLESTONE, Items.OAK_LOG, Items.IRON_INGOT, Items.BREAD, Items.ARROW};
            for (int i = 9; i < 27; i++) {
                inv.setItem(i, new ItemStack(fill[i % fill.length], 10 + i));
            }
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            ench.get(Enchantments.PROTECTION).ifPresent(h -> chest.enchant(h, 4));
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            helmet.set(DataComponents.DAMAGE, 50);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            toCore(player, core);
            before.addAll(snapshot(player));
            // The chest beside the player.
            ServerLevel level = (ServerLevel) player.level();
            BlockPos at = BlockPos.containing(core[0] + 2.5, core[1], core[2] + 2.5);
            chestPos[0] = at;
            level.setBlock(at, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(at) instanceof ChestBlockEntity box) {
                box.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
                box.setItem(1, new ItemStack(Items.DIRT, 5));
                box.setItem(2, new ItemStack(Items.ELYTRA));
            }
            manager.config().requiredAccepts = 1;
            manager.save().nextSiegeDay = manager.clockDayFor(level.getServer());
            manager.save().clearVotes();
            log("setup: core=" + c.x + " " + c.y + " " + c.z + " filled=" + before.stream().filter(s -> !s.isEmpty()).count()
                    + " level=" + manager.currentLevel());
        });
        context.waitTicks(20);
        game.getServer().runCommand("time set 1100");   // morning: the invitation
        context.waitTicks(60);
        server(context, "vote", (player, manager) -> {
            manager.vote(player, true);
            // Own things in open windows when the horn sounds: on the cursor and in the 2x2 grid.
            player.inventoryMenu.setCarried(new ItemStack(Items.GOLD_INGOT, 7));
            player.inventoryMenu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS, 3));
        });
        context.waitTicks(20);
        game.getServer().runCommand("time set 12100");   // evening: the horn
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "horn", (player, manager) -> {
            if (!manager.kits().hasStash(player)) {
                log("horn: no kit from the rhythm (nightComing=" + manager.nightComing(player.level().getServer()) + "), equip by hand");
                manager.kits().equip(player, manager.currentLevel());
            }
            SiegeSave.KitStash st = stash(manager, player);
            check(st != null, "kit on at the horn");
            if (st == null) {
                return;
            }
            stashAtHorn.addAll(st.items);
            check(KitService.STATE_APPLIED.equals(st.state), "stash state after equip = applied (" + st.state + ")");
            check(player.inventoryMenu.getCarried().isEmpty() && player.inventoryMenu.getSlot(1).getItem().isEmpty(),
                    "cursor and 2x2 grid empty after equip");
            long snbt = st.items.stream().filter(x -> x.startsWith("snbt:")).count();
            check(snbt > 0, "stash entries stored as SNBT (" + snbt + ")");
            check(String.join("|", st.items).contains("gold_ingot") && String.join("|", st.items).contains("oak_planks"),
                    "cursor item and grid item went into the stash");
            check(EventLookTest.kitCount(player.getInventory()) > 0, "kit items in the inventory");
        });
        context.waitTicks(20);

        // 2. A real death between the horn and the start: after the respawn a fresh kit, the stash unchanged.
        server(context, "death before the night", (player, manager) -> {
            player.hurtServer((ServerLevel) player.level(), player.damageSources().generic(), 500f);
            log("death before the night: dead=" + player.isDeadOrDying() + " active=" + manager.isActive());
        });
        context.waitTicks(30);
        server(context, "respawn", (player, manager) -> respawn(player));
        context.waitTicks(40);
        EventLookTest.closeScreen(context);
        server(context, "after respawn", (player, manager) -> {
            SiegeSave.KitStash st = stash(manager, player);
            int kit = EventLookTest.kitCount(player.getInventory());
            check(player.isAlive() && st != null && kit > 0, "fresh kit after the death before the night (kit items " + kit + ")");
            check(st != null && st.items.equals(stashAtHorn), "stash unchanged by the new kit");
            toCore(player, core);
        });
        context.waitTicks(20);

        // 3. The night: things from the chest, an elytra worn, a crate won, a kit item on the cursor and in the grid.
        game.getServer().runCommand("time set 15100");
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        boolean natural = context.computeOnClient(client -> SiegeManager.get().isActive());
        if (!natural) {
            log("no natural night, starting by command");
            game.getServer().runCommand("execute as @p at @p run nightsiege start");
            context.waitTicks(80);
            EventLookTest.closeScreen(context);
        }
        server(context, "in the night", (player, manager) -> {
            log("night: active=" + manager.isActive() + " defending=" + manager.isDefending(player) + " kit=" + manager.kits().hasStash(player));
            Inventory inv = player.getInventory();
            if (player.level().getBlockEntity(chestPos[0]) instanceof ChestBlockEntity box) {
                player.openMenu(box);
                AbstractContainerMenu menu = player.containerMenu;
                for (int s = 0; s < 3; s++) {
                    menu.clicked(s, 0, ContainerInput.QUICK_MOVE, player);   // shift-click out of the chest
                }
                log("chest after taking: " + describe(box.getItem(0)) + ", " + describe(box.getItem(1)) + ", " + describe(box.getItem(2)));
            }
            // The elytra on, the kit's chestplate into the bag.
            for (int i = 0; i < 36; i++) {
                if (inv.getItem(i).is(Items.ELYTRA)) {
                    ItemStack kitChest = player.getItemBySlot(EquipmentSlot.CHEST).copy();
                    player.setItemSlot(EquipmentSlot.CHEST, inv.getItem(i).copy());
                    inv.setItem(i, kitChest);
                    break;
                }
            }
            int cratesBefore = crates(player);
            int pendingBefore = pending(manager, player);
            manager.crates().awardEach(player.level().getServer(), Set.of(player.getUUID()), CrateKind.NORMAL, manager.currentLevel());
            check(crates(player) == cratesBefore && pending(manager, player) == pendingBefore + 1,
                    "crate won with the kit on waits (inventory " + cratesBefore + "->" + crates(player) + ", pending "
                            + pendingBefore + "->" + pending(manager, player) + ")");
            // A kit item on the cursor of the open chest window, another in the own 2x2 grid.
            int swordSlot = -1;
            for (int i = 0; i < 36; i++) {
                if (KitService.isKit(inv.getItem(i)) && inv.getItem(i).getMaxStackSize() == 1) {
                    swordSlot = i;
                    break;
                }
            }
            AbstractContainerMenu menu = player.containerMenu;
            int menuSlot = EventLookTest.menuSlotOf(menu, inv, swordSlot);
            if (menuSlot >= 0) {
                menu.clicked(menuSlot, 0, ContainerInput.PICKUP, player);
            }
            player.inventoryMenu.getSlot(2).set(KitService.mark(new ItemStack(Items.BREAD, 2)));
            log("cursor now: " + describe(menu.getCarried()) + " (chest window open=" + (menu != player.inventoryMenu)
                    + "), grid: " + describe(player.inventoryMenu.getSlot(2).getItem()));
            check(KitService.isKit(menu.getCarried()), "a kit item is on the cursor before the night ends");
        });
        context.waitTicks(20);

        // 4. The night ends (stopped, counts as survived): the restore.
        game.getServer().runCommand("nightsiege stop");
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        server(context, "after the night", (player, manager) -> {
            check(!manager.kits().hasStash(player), "stash gone after the night");
            check(kitAnywhere(player) == 0, "no kit item left (inventory, cursor, grid): " + kitAnywhere(player));
            List<String> diff = differ(player, before);
            for (String d : diff) {
                log("DIFF " + d);
            }
            check(diff.isEmpty(), "own inventory back slot by slot (" + diff.size() + " differ)");
            check(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && player.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD)
                    && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "own armour and shield worn again");
            check(count(player, s -> s.is(Items.POTION) && s.get(DataComponents.POTION_CONTENTS) != null
                    && s.get(DataComponents.POTION_CONTENTS).is(Potions.SWIFTNESS)) == 1, "potion from the chest kept");
            check(count(player, s -> s.is(Items.DIRT)) == 5, "dirt from the chest kept (" + count(player, s -> s.is(Items.DIRT)) + ")");
            check(count(player, s -> s.is(Items.ELYTRA)) == 1, "elytra worn in the night kept (in the bag)");
            check(count(player, s -> s.is(Items.GOLD_INGOT)) == 7 && count(player, s -> s.is(Items.OAK_PLANKS)) == 3,
                    "cursor and grid items of the horn back");
            int crates = crates(player);
            check(crates >= 2 && pending(manager, player) == 0, "crates after the restore: " + crates + " (own + won in the night), pending "
                    + pending(manager, player));
            ItemStack fresh = CrateService.crate(SkillClass.WARRIOR, CrateKind.NORMAL, 9);
            check(ItemStack.isSameItemSameComponents(fresh, player.getInventory().getItem(4)),
                    "own crate stacks with a new one after the stash (number types kept)");
            if (player.level().getBlockEntity(chestPos[0]) instanceof ChestBlockEntity box) {
                check(EventLookTest.kitCount(box) == 0, "no kit item in the chest");
            }
            java.util.List<net.minecraft.world.entity.item.ItemEntity> dropped = player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(16));
            for (net.minecraft.world.entity.item.ItemEntity e : dropped) {
                log("dropped near player: " + describe(e.getItem()));
            }
        });
        context.waitTicks(20);

        // 5. Crash safety: a swap half done (applying with the own inventory saved, restoring after it).
        final List<ItemStack> now = new ArrayList<>();
        server(context, "crash: applying", (player, manager) -> {
            now.clear();
            now.addAll(snapshot(player));
            HolderLookup.Provider regs = player.level().registryAccess();
            for (String state : List.of(KitService.STATE_APPLYING, KitService.STATE_RESTORING)) {
                SiegeSave.KitStash st = new SiegeSave.KitStash();
                st.state = state;
                Inventory inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    st.items.add(KitService.store(inv.getItem(i), regs));
                }
                manager.save().kitStashes.put(player.getUUID().toString(), st);
                boolean gone = manager.kits().settle(player);
                check(gone && !manager.kits().hasStash(player) && differ(player, now).isEmpty()
                                && count(player, s -> true) == countOf(now),
                        "crash while " + state + " with the own inventory saved: stash dropped, nothing doubled");
            }
            // Restoring, but the kit still in the player's data: the restore runs again.
            manager.kits().equip(player, manager.currentLevel());
            SiegeSave.KitStash st = stash(manager, player);
            if (st != null) {
                st.state = KitService.STATE_RESTORING;
                manager.kits().restore(player);
            }
            check(!manager.kits().hasStash(player) && kitAnywhere(player) == 0 && differ(player, now).isEmpty()
                    && count(player, s -> true) == countOf(now), "crash while restoring with the kit saved: restored once");
        });
        context.waitTicks(20);

        // 6. An unreadable stash entry: into the quarantine, the rest comes back, no stash left.
        server(context, "quarantine", (player, manager) -> {
            now.clear();
            now.addAll(snapshot(player));
            int free = -1;
            for (int i = 27; i < 36; i++) {
                if (now.get(i).isEmpty()) {
                    free = i;
                    break;
                }
            }
            int q0 = manager.save().kitQuarantine == null || manager.save().kitQuarantine.get(player.getUUID().toString()) == null ? 0
                    : manager.save().kitQuarantine.get(player.getUUID().toString()).size();
            manager.kits().equip(player, manager.currentLevel());
            SiegeSave.KitStash st = stash(manager, player);
            if (st == null || free < 0) {
                check(false, "quarantine setup (stash " + (st != null) + ", free slot " + free + ")");
                return;
            }
            st.items.set(free, "{\"id\":\"gonemod:lost_thing\",\"count\":1}");
            st.items.add("snbt:{id:\"minecraft:stone\",count:1b,broken");
            manager.kits().restore(player);
            List<String> q = manager.save().kitQuarantine == null ? null : manager.save().kitQuarantine.get(player.getUUID().toString());
            int q1 = q == null ? 0 : q.size();
            check(!manager.kits().hasStash(player) && q1 - q0 == 2, "two unreadable entries in the quarantine (" + (q1 - q0) + "), stash gone");
            check(differ(player, now).isEmpty(), "the readable things came back");
        });
        context.waitTicks(20);

        // 7. The restore on a dead player waits for the respawn; a crate won while dead comes after it.
        server(context, "dead restore", (player, manager) -> {
            now.clear();
            now.addAll(snapshot(player));
            manager.kits().equip(player, manager.currentLevel());
            player.hurtServer((ServerLevel) player.level(), player.damageSources().generic(), 500f);
            manager.kits().restore(player);
            check(player.isDeadOrDying() && manager.kits().hasStash(player) && manager.kits().waitsForRespawn(player),
                    "restore on a dead player waits (dead " + player.isDeadOrDying() + ")");
            manager.crates().awardEach(player.level().getServer(), Set.of(player.getUUID()), CrateKind.NORMAL, 3);
            check(pending(manager, player) == 1, "crate won while dead waits");
        });
        context.waitTicks(30);
        server(context, "respawn 2", (player, manager) -> respawn(player));
        context.waitTicks(40);
        EventLookTest.closeScreen(context);
        server(context, "after respawn 2", (player, manager) -> {
            check(player.isAlive() && !manager.kits().hasStash(player) && kitAnywhere(player) == 0, "kit gone after the respawn");
            List<String> diff = differ(player, now);
            for (String d : diff) {
                log("DIFF dead " + d);
            }
            check(diff.isEmpty(), "own inventory back after the respawn (" + diff.size() + " differ)");
            check(pending(manager, player) == 0 && crates(player) == countCrates(now) + 1, "crate of the dead time arrived");
            toCore(player, core);
        });
        context.waitTicks(20);

        // 8. A second playtest keeps the waiting restore of an offline player of the first one.
        final String offline = "00000000-0000-0000-0000-0000000000a1";
        server(context, "playtest", (player, manager) -> {
            now.clear();
            now.addAll(snapshot(player));
            SiegeSave.PlaytestState old = new SiegeSave.PlaytestState();
            old.finished = true;
            old.phase = "done";
            SiegeSave.PlaytestPlayer away = new SiegeSave.PlaytestPlayer();
            away.name = "Weg";
            away.restorePending = true;
            away.items = new ArrayList<>(List.of("real-marker"));
            away.skills = "{\"points\":7}";
            old.players.put(offline, away);
            manager.save().playtest = old;
            String problem = manager.playtest().start(player.level().getServer(), 4, player);
            SiegeSave.PlaytestPlayer kept = manager.save().playtestRestores == null ? null : manager.save().playtestRestores.get(offline);
            check(problem == null, "second playtest started (" + problem + ")");
            check(kept != null && kept.items.contains("real-marker"), "waiting restore of the first playtest kept aside");
            manager.playtest().finish(player.level().getServer(), "nightsiege.playtest.reset_restart");
            kept = manager.save().playtestRestores == null ? null : manager.save().playtestRestores.get(offline);
            check(kept != null && kept.items.contains("real-marker"), "still kept after the second playtest");
            check(differ(player, now).isEmpty(), "own inventory back after the playtest");
            if (manager.save().playtestRestores != null) {
                manager.save().playtestRestores.remove(offline);   // the test's fake player
            }
            manager.persist();
        });
        context.waitTicks(30);
        EventLookTest.closeScreen(context);
        log("RESULT checks=" + checks + " failed=" + FAILS.size() + (FAILS.isEmpty() ? "" : " -> " + String.join(" | ", FAILS)));
        game.getServer().runCommand("gamemode creative @p");
    }

    private static int countOf(List<ItemStack> stacks) {
        int n = 0;
        for (ItemStack s : stacks) {
            n += s.getCount();
        }
        return n;
    }

    private static int countCrates(List<ItemStack> stacks) {
        int n = 0;
        for (ItemStack s : stacks) {
            if (s.is(CrateService.CRATE)) {
                n += s.getCount();
            }
        }
        return n;
    }
}
