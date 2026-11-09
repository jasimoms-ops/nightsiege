package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.CrateGains;
import com.mki.nightsiege.core.GainCharges;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.GainGear;
import com.mki.nightsiege.game.GainKeys;
import com.mki.nightsiege.game.Gadgets;
import com.mki.nightsiege.game.KitService;
import com.mki.nightsiege.game.ModContent;
import com.mki.nightsiege.game.RetiredItems;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tor 5.7, scene "inventar" (Simon 09.11. 08:50: "im Inventar bleiben nur Holzwerkzeuge, die Kit-Waffe(n),
 * Blöcke, Essen, Schilde"): in a test night every class gets its kit with every affected gain – the
 * inventory holds only allowed things; the healing potion on key 1 heals at once and takes one charge, a
 * second press right after is locked; apple and pearl work; the old kit items go from inventory, ender
 * chest, chests and shulker boxes, the player's own apples stay. Lines start with "[inventar]",
 * "OK/FAIL"; the summary "x von y" (0 checks = FAIL).
 */
final class InventarScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private InventarScenes() {
    }

    private static void log(String line) {
        System.out.println("[inventar] " + line);
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
            } catch (RuntimeException e) {
                check(false, step + ": " + e);
            }
        });
    }

    /** Every gain the inventory list (qa/tor-5/inventar.md) touches, for one class. */
    private static Map<String, Integer> gains(SkillClass c) {
        Map<String, Integer> g = new HashMap<>();
        g.put("heiltrank", 2);
        g.put("notration", 1);
        g.put("wurfperlen", 1);
        g.put("kriegsbanner", 1);
        g.put("baukiste", 1);
        g.put("proviant", 1);
        g.put("wurfspeere", 1);   // retired: must bring nothing
        if (c == SkillClass.WARRIOR) {
            g.put("wurfbeile", 3);   // retired
            g.put("wetzstein", 2);
        }
        if (c == SkillClass.GUARDIAN) {
            g.put("verbandskasten", 1);
            g.put("barrikaden", 1);
            g.put("dritte_hand", 1);
        }
        return g;
    }

    /** What may stay in the inventory: wooden tools, the kit weapon(s) and their ammunition, tower and trap things, blocks, food, shields. */
    private static boolean allowed(ItemStack st) {
        if (st.isEmpty()) {
            return true;
        }
        String gadget = Gadgets.of(st);
        if (gadget.equals("feldstecher") || gadget.equals("alchemie_blende")) {
            return true;   // the demolition expert's spyglass, the engineers' flash grenade (kit tools)
        }
        if (!gadget.isEmpty() || st.is(Items.POTION) || st.is(Items.GOLDEN_APPLE) || st.is(Items.ENDER_PEARL)) {
            return false;
        }
        if (ModContent.TRAP_ITEMS.containsValue(st.getItem())) {
            return true;
        }
        if (st.is(Items.WOODEN_PICKAXE) || st.is(Items.WOODEN_SHOVEL) || st.is(Items.WOODEN_AXE)) {
            return true;
        }
        if (st.is(ItemTags.SWORDS) || st.is(ItemTags.AXES) || st.is(Items.MACE) || st.is(Items.BOW) || st.is(Items.CROSSBOW)
                || st.is(Items.ARROW) || st.is(Items.SHIELD) || st.getItem() == ModContent.STAFF || st.getItem() == ModContent.HEALER_STAFF) {
            return true;
        }
        if (st.getItem() instanceof BlockItem || st.has(DataComponents.FOOD) || st.is(Items.IRON_INGOT)) {
            return true;   // blocks, food (the wolves' meat), the golem's iron
        }
        return false;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("gamemode survival @p");   // the normal inventory only in survival (Lehre Tor 3)
        game.getServer().runCommand("effect give @p minecraft:resistance 600 4 true");
        context.waitTicks(10);
        final int[] saved = new int[2];
        server(context, "night", (player, m) -> {
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            player.getInventory().clearContent();
            ServerLevel level = (ServerLevel) player.level();
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            m.config().testPrepSeconds = 1;
            m.config().batchIntervalSeconds = 600;
            boolean ok = m.prepareTest(level.getServer(), level, player.blockPosition(), 1, com.mki.nightsiege.core.SiegeEvent.NONE);
            log("night prepared=" + ok);
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "clear", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(160),
                    e -> e instanceof net.minecraft.world.entity.monster.Enemy).forEach(Mob::discard);
            check(m.isDefending(player), "the player defends the test night");
        });
        context.waitTicks(5);

        // 1. The kit of every class: only allowed things in the inventory.
        for (SkillClass c : new SkillClass[]{SkillClass.WARRIOR, SkillClass.ARCHER, SkillClass.GUARDIAN,
                SkillClass.ENGINEER, SkillClass.SUMMONER}) {
            server(context, "kit " + c, (player, m) -> {
                PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
                own.unlockClass(c);
                own.switchTo(c);
                own.levels.clear();
                own.gains.put(c.name(), new HashMap<>(gains(c)));
                m.kits().equip(player, 10);
                StringBuilder bad = new StringBuilder();
                StringBuilder all = new StringBuilder();
                for (int i = 0; i < 36; i++) {
                    ItemStack st = player.getInventory().getItem(i);
                    if (st.isEmpty()) {
                        continue;
                    }
                    String name = st.getItem().toString() + (Gadgets.of(st).isEmpty() ? "" : "/" + Gadgets.of(st));
                    all.append(st.getCount()).append('x').append(name).append(' ');
                    if (!allowed(st)) {
                        bad.append(name).append(' ');
                    }
                }
                ItemStack off = player.getOffhandItem();
                if (!allowed(off)) {
                    bad.append("offhand:").append(off.getItem()).append(' ');
                }
                log(c + " kit: " + all);
                check(bad.isEmpty(), c + ": only allowed things in the inventory" + (bad.isEmpty() ? "" : " – not allowed: " + bad));
                List<String> owned = GainKeys.owned(m, own, c);
                check(owned.contains(GainCharges.POTION) && owned.contains(GainCharges.APPLE) && owned.contains(GainCharges.PEARL)
                        && !owned.contains("kriegsbanner") && !owned.contains("verbandskasten"),
                        c + ": charge fields on 1-3, the banner passive: " + owned);
                check(!GainGear.bannerOwnerNear(player), c + ": the banner does not count for its owner himself");
            });
            context.waitTicks(5);
        }

        // 2. The healing potion on key 1: health up at once, one charge less; a second press right after is locked.
        final int[] charges = new int[1];
        server(context, "potion", (player, m) -> {
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            own.switchTo(SkillClass.GUARDIAN);
            own.gains.put(SkillClass.GUARDIAN.name(), new HashMap<>(gains(SkillClass.GUARDIAN)));
            m.kits().equip(player, 10);
            GainKeys.choose(m, player, SkillClass.GUARDIAN.name(), GainCharges.POTION);
            List<String> keys = GainKeys.assigned(m, own, SkillClass.GUARDIAN);
            int before = GainKeys.chargesLeft(m, player, GainCharges.POTION);
            check(keys.indexOf(GainCharges.POTION) == 0, "the healing potion is on key 1: " + keys);
            check(before == 4, "potion belt 2 + first-aid kit 1 stack in one field: " + before + " charges (want 4)");
            List<UltState.Slot> slots = GainKeys.slots(m, player, player.level().getGameTime());
            UltState.Slot field = slots.isEmpty() ? null : slots.get(0);
            check(field != null && "nightsiege.wheel.count".equals(field.sub) && field.subArgs.contains(String.valueOf(before)),
                    "the field shows the charges as a number: " + (field == null ? "-" : field.sub + " " + field.subArgs));
            player.setHealth(6.0f);
            float hp = player.getHealth();
            m.onCoreAction(player, "gain:0");
            float after = player.getHealth();
            int left = GainKeys.chargesLeft(m, player, GainCharges.POTION);
            check(after > hp + 7.0f, "key 1: health up at once " + hp + " -> " + after);
            check(left == before - 1, "key 1: one charge less " + before + " -> " + left);
            m.onCoreAction(player, "gain:0");
            int locked = GainKeys.chargesLeft(m, player, GainCharges.POTION);
            check(locked == left, "a second press right after is locked: " + left + " -> " + locked);
            check(GainKeys.pauseLeft(player, GainCharges.POTION) > 0, "the lock runs: " + GainKeys.pauseLeft(player, GainCharges.POTION) + " ticks");
            charges[0] = locked;
        });
        context.waitTicks(GainCharges.LOCK_TICKS + 5);
        server(context, "potion again", (player, m) -> {
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            int index = GainKeys.assigned(m, own, SkillClass.GUARDIAN).indexOf(GainCharges.POTION);
            m.onCoreAction(player, "gain:" + index);
            int left = GainKeys.chargesLeft(m, player, GainCharges.POTION);
            check(left == charges[0] - 1, "after the lock the key works again: " + charges[0] + " -> " + left);
            // the golden apple and the ender pearl on their keys
            GainKeys.choose(m, player, SkillClass.GUARDIAN.name(), GainCharges.APPLE);
            int apples = GainKeys.chargesLeft(m, player, GainCharges.APPLE);
            m.onCoreAction(player, "gain:0");
            check(player.hasEffect(MobEffects.ABSORPTION) && GainKeys.chargesLeft(m, player, GainCharges.APPLE) == apples - 1,
                    "golden apple: absorption at once, " + apples + " -> " + GainKeys.chargesLeft(m, player, GainCharges.APPLE));
            GainKeys.choose(m, player, SkillClass.GUARDIAN.name(), GainCharges.PEARL);
            int pearls = GainKeys.chargesLeft(m, player, GainCharges.PEARL);
            m.onCoreAction(player, "gain:0");
            check(GainKeys.chargesLeft(m, player, GainCharges.PEARL) == pearls - 1,
                    "ender pearl thrown: " + pearls + " -> " + GainKeys.chargesLeft(m, player, GainCharges.PEARL));
            m.kits().restore(player);
            m.kits().equip(player, 10);
            check(GainKeys.chargesLeft(m, player, GainCharges.POTION) == 4, "a new kit fills the charges again: "
                    + GainKeys.chargesLeft(m, player, GainCharges.POTION));
            m.kits().restore(player);
        });
        context.waitTicks(5);

        // 3. Old loot items: gone from inventory, ender chest, a chest and a shulker box; own apples stay.
        server(context, "old items", (player, m) -> {
            ItemStack kitPotion = KitService.mark(PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING));
            ItemStack axe = KitService.fromGain(Gadgets.make(Items.IRON_AXE, "wurfbeil", 4), "wurfbeile");
            ItemStack banner = KitService.fromGain(Gadgets.make(Items.BANNER.red(), "kriegsbanner", 1), "kriegsbanner");
            ItemStack ownApple = new ItemStack(Items.GOLDEN_APPLE, 5);
            player.getInventory().setItem(20, kitPotion.copy());
            player.getInventory().setItem(21, ownApple.copy());
            player.getEnderChestInventory().setItem(0, axe.copy());
            player.getEnderChestInventory().setItem(1, KitService.mark(new ItemStack(Items.ENDER_PEARL, 2)));
            RetiredItems.purgePlayer(player);
            check(player.getInventory().getItem(20).isEmpty(), "the kit potion in the inventory went");
            check(player.getInventory().getItem(21).is(Items.GOLDEN_APPLE) && player.getInventory().getItem(21).getCount() == 5,
                    "the player's own golden apples stay");
            check(player.getEnderChestInventory().getItem(0).isEmpty() && player.getEnderChestInventory().getItem(1).isEmpty(),
                    "throwing axe and kit pearls in the ender chest went");
            ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
            shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(banner.copy(), ownApple.copy())));
            SimpleContainer chest = new SimpleContainer(27);
            chest.setItem(0, axe.copy());
            chest.setItem(1, shulker);
            chest.setItem(2, ownApple.copy());
            int gone = RetiredItems.purge(chest);
            ItemContainerContents inside = chest.getItem(1).get(DataComponents.CONTAINER);
            long bannersLeft = inside == null ? -1 : inside.allItemsCopyStream().filter(s -> !Gadgets.of(s).isEmpty()).count();
            long applesLeft = inside == null ? -1 : inside.allItemsCopyStream().filter(s -> s.is(Items.GOLDEN_APPLE)).count();
            check(gone == 2 && chest.getItem(0).isEmpty() && chest.getItem(2).is(Items.GOLDEN_APPLE),
                    "chest: the axe went, the own apples stay (" + gone + " gone)");
            check(bannersLeft == 0 && applesLeft == 1, "shulker box: the banner went, the apples stay");
            player.getInventory().setItem(21, ItemStack.EMPTY);
        });
        context.waitTicks(5);

        // 4. Throwing spears and axes: no longer in the crates; owners get the duplicate points back once.
        server(context, "retired", (player, m) -> {
            check(CrateGains.get("wurfspeere") == null && CrateGains.get("wurfbeile") == null,
                    "throwing spears and axes are no longer obtainable");
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            Map<String, Integer> warrior = new HashMap<>();
            warrior.put("wurfspeere", 1);
            warrior.put("wurfbeile", 3);
            own.gains.put(SkillClass.WARRIOR.name(), warrior);
            boolean was = m.save().loot57Migrated;
            m.save().loot57Migrated = false;
            int paid = m.save().migrateLoot57();
            m.save().loot57Migrated = was || m.save().loot57Migrated;
            check(paid > 0 && !warrior.containsKey("wurfspeere") && !warrior.containsKey("wurfbeile"),
                    "spear 1 + axe 3 stages: gains gone, " + paid + " points back");
            check(m.save().migrateLoot57() == 0, "the payback runs only once");
        });
        context.waitTicks(5);

        server(context, "end", (player, m) -> {
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            m.save().skillsOf(player.getUUID().toString()).reset();
            player.setHealth(player.getMaxHealth());
        });
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(10);
        if (checks == 0) {
            check(false, "no check ran");
        }
        log((FAILS.isEmpty() ? "OK   " : "FAIL ") + (checks - FAILS.size()) + " von " + checks
                + (FAILS.isEmpty() ? "" : ", FAIL: " + FAILS));
        log("done");
    }
}
