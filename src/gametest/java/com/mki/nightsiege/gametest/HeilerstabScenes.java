package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.KitWeapons;
import com.mki.nightsiege.core.NightKit;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.game.HealerStaffItem;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * "heilerstab" (Tor 4.7, Simon: "Geschoss auf Distanz"): medic and warden get the healers' staff instead of the
 * sword, the bolt deals a kit sword hit, and a left click with the staff hits a husk 10 blocks away. Pictures of
 * the staff in hand; behind a wall it finds no target and a bolt stops at a wall (Prüfer-Fund 4). Log lines
 * start with "[heilerstab]"; a scene without a picture fails.
 */
final class HeilerstabScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static final String HUSK = "ns_heilerstab_husk";

    private HeilerstabScenes() {
    }

    private static void log(String line) {
        System.out.println("[heilerstab] " + line);
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

    private static void kit(ServerPlayer player, SiegeManager m, String node, int level) {
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        own.unlockClass(SkillClass.GUARDIAN);
        own.switchTo(SkillClass.GUARDIAN);
        own.levels.clear();
        own.levels.put(node, 1);
        m.kits().equip(player, level);
    }

    private static int find(ServerPlayer player, java.util.function.Predicate<ItemStack> wanted) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (wanted.test(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    /** A stone block of 3 x 4 x 3 five blocks ahead of the player's eyes (or air again). */
    private static void wall(ServerPlayer player, boolean stone) {
        net.minecraft.core.BlockPos mid = net.minecraft.core.BlockPos.containing(
                player.getEyePosition().add(player.getLookAngle().scale(5.0)));
        net.minecraft.world.level.block.state.BlockState state = (stone
                ? net.minecraft.world.level.block.Blocks.STONE : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {   // from the feet up, the floor stays
                for (int dz = -1; dz <= 1; dz++) {
                    player.level().setBlockAndUpdate(mid.offset(dx, dy, dz), state);
                }
            }
        }
    }

    private static Mob husk(ServerPlayer player) {
        for (Entity e : player.level().getAllEntities()) {
            if (e instanceof Mob mob && mob.isAlive() && e.entityTags().contains(HUSK)) {
                return mob;
            }
        }
        return null;
    }

    private static int shot(ClientGameTestContext context, String name) {
        Path p = context.takeScreenshot(name);
        return p != null && Files.exists(p) ? 1 : 0;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        int pics = 0;
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode survival @p");   // a normal inventory (Lehre Tor 3)
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 120 4 true");
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        context.getInput().lookAt(-90.0f, 0.0f);
        context.waitTicks(10);
        server(context, "setup", (player, m) -> {
            if (!m.isRegistered(player)) {
                m.save().register(player.getUUID().toString());   // the left click goes through the mod's keys
            }
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            player.getInventory().clearContent();
            HealerStaffItem.resetForTest();
        });
        context.waitTicks(5);

        // ---------------------------------------------------------------- 1: the kit of both healers
        server(context, "kits", (player, m) -> {
            String[][] subs = {{"g_healing_hand", SkillTree.SUB_MEDIC}, {"g_ward", SkillTree.SUB_WARDEN}};
            for (String[] sub : subs) {
                KitWeapons.Look look = KitWeapons.of(sub[1]);
                for (int level : new int[]{1, 6, 20}) {
                    kit(player, m, sub[0], level);
                    String at = sub[1] + " level " + level;
                    int slot = find(player, s -> s.getItem() instanceof HealerStaffItem);
                    check(slot >= 0, at + ": the healers' staff in the kit");
                    check(find(player, s -> s.is(ItemTags.SWORDS)) < 0, at + ": no sword in the kit");
                    check(player.getItemBySlot(EquipmentSlot.OFFHAND).has(DataComponents.BLOCKS_ATTACKS), at + ": the shield in the off hand");
                    if (slot < 0) {
                        continue;
                    }
                    ItemStack staff = player.getInventory().getItem(slot);
                    check(look != null && Objects.equals(String.valueOf(staff.get(DataComponents.ITEM_MODEL)), look.itemModel()),
                            at + ": own look " + staff.get(DataComponents.ITEM_MODEL));
                    double want = NightKit.healerBolt(level, NightKit.sharpness(level));
                    double bolt = HealerStaffItem.boltDamage(staff);
                    log(at + ": bolt " + String.format(Locale.ROOT, "%.2f", bolt) + " (sword hit " + String.format(Locale.ROOT, "%.2f", want) + ")");
                    check(Math.abs(bolt - want) < 0.6, at + ": a bolt deals the kit sword's hit");
                }
            }
            // Medic of level 6 for the shot, the staff in the main hand.
            kit(player, m, "g_healing_hand", 6);
            int slot = find(player, s -> s.getItem() instanceof HealerStaffItem);
            if (slot >= 0 && slot < 9) {
                player.getInventory().setSelectedSlot(slot);
            }
        });
        context.waitTicks(10);
        boolean inHand = context.computeOnClient(client -> client.player != null
                && client.player.getMainHandItem().getItem() instanceof HealerStaffItem);
        check(inHand, "the staff is in the main hand");

        // ---------------------------------------------------------------- 2: a bolt at 10 blocks
        game.getServer().runCommand("execute as @p at @p run summon minecraft:husk ^ ^ ^10 {NoAI:1b,PersistenceRequired:1b,Tags:[\"" + HUSK + "\"]}");
        context.waitTicks(20);
        pics += shot(context, "heilerstab_hand");
        final double[] before = new double[2];
        server(context, "aim", (player, m) -> {
            Mob h = husk(player);
            check(h != null, "the husk stands");
            if (h != null) {
                before[0] = h.getHealth();
                before[1] = player.distanceTo(h);
                player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, h.position().add(0, h.getBbHeight() * 0.5, 0));
            }
            HealerStaffItem.resetForTest();
        });
        check(before[1] >= 8.0, "the husk is " + String.format(Locale.ROOT, "%.1f", before[1]) + " blocks away (8+)");
        net.minecraft.client.KeyMapping attack = context.computeOnClient(client -> client.options.keyAttack);
        context.getInput().pressKey(attack);   // the real left click: mixin, key, server
        context.waitTicks(3);
        pics += shot(context, "heilerstab_bolt");
        context.waitTicks(20);
        server(context, "hit", (player, m) -> {
            Mob h = husk(player);
            double after = h == null ? 0 : h.getHealth();
            log("husk " + before[0] + " -> " + after + ", hits " + HealerStaffItem.hits()
                    + ", dealt " + String.format(Locale.ROOT, "%.2f", HealerStaffItem.dealt()));
            check(HealerStaffItem.hits() >= 1 && after < before[0], "the left click's bolt hits the husk at 8+ blocks");
        });
        check(pics > 0, "pictures: " + pics + " of 2");

        // ---------------------------------------------------------------- 3: no bolt through a wall (Prüfer-Fund 4)
        final boolean[] shot = new boolean[2];
        server(context, "wall", (player, m) -> {
            HealerStaffItem.resetForTest();
            wall(player, true);
            shot[0] = HealerStaffItem.attack(player);   // the husk behind the wall: no target
            wall(player, false);
            HealerStaffItem.resetForTest();
            shot[1] = HealerStaffItem.attack(player);   // in sight: the bolt flies ...
            wall(player, true);                         // ... and a wall rises in its way
        });
        context.waitTicks(20);
        server(context, "wall end", (player, m) -> {
            log("wall: behind it " + shot[0] + ", in sight " + shot[1] + ", hits " + HealerStaffItem.hits());
            check(!shot[0], "behind a wall the staff finds no target");
            check(shot[1] && HealerStaffItem.hits() == 0, "a bolt in flight stops at the wall");
            wall(player, false);
        });

        // The end: kit back, husk gone, nothing learned.
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        server(context, "end", (player, m) -> {
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            m.save().skillsOf(player.getUUID().toString()).levels.clear();
            player.getInventory().clearContent();
            player.removeAllEffects();
            HealerStaffItem.resetForTest();
        });
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(5);
        String summary = checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS);
        log("summary: " + summary);
        System.out.println("[Night Siege test] heilerstab summary: " + summary);
    }
}
