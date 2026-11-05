package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.KitWeapons;
import com.mki.nightsiege.core.NightKit;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.game.KitService;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * "Streitkolben" (Simon 05.11.: "die Tanks könnten … den Mace als Waffe haben, der viel Schaden macht, aber nicht
 * schnell angreifen kann"): the shield fighter and the bulwark get a mace in the kit (their own look, the sword's
 * Sharpness), a hit deals 2.5 times the kit sword's, 0.8 swings a second, a hit after a fall brings no smash bonus,
 * the shield in the off hand blocks, and the guardian's shield effects (riposte, iron ward, revenge) still work
 * with the mace in hand. Log lines start with "[streitkolben]".
 */
final class StreitkolbenScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static final String PIG = "ns_kolben_pig";
    private static final String HUSK = "ns_kolben_husk";

    private StreitkolbenScenes() {
    }

    private static void log(String line) {
        System.out.println("[streitkolben] " + line);
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

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** The subclass node that makes the subclass, and with it the way. */
    private static String subNode(String sub) {
        return switch (sub) {
            case SkillTree.SUB_SHIELDFIGHTER -> "riposte";
            case SkillTree.SUB_BULWARK -> "g_bastion";
            case SkillTree.SUB_MEDIC -> "g_healing_hand";
            default -> "g_ward";
        };
    }

    /** Guardian of this subclass with these extra nodes, the kit of this level on. */
    private static void kit(ServerPlayer player, SiegeManager m, String sub, int level, String... extra) {
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        own.unlockClass(SkillClass.GUARDIAN);
        own.switchTo(SkillClass.GUARDIAN);
        own.levels.clear();
        own.levels.put(subNode(sub), 1);
        for (String id : extra) {
            own.levels.put(id, 1);
        }
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

    /** Puts a stack into the selected slot (the main hand); what was there goes where the stack was, if anywhere. */
    private static void hold(ServerPlayer player, ItemStack stack, int from) {
        Inventory inv = player.getInventory();
        int sel = inv.getSelectedSlot();
        if (from == sel) {
            return;
        }
        ItemStack old = inv.getItem(sel);
        inv.setItem(sel, stack);
        if (from >= 0) {
            inv.setItem(from, old);
        }
    }

    private static double attackDamage(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ATTACK_DAMAGE, NightKit.PLAYER_DAMAGE, EquipmentSlot.MAINHAND);
    }

    private static double attackSpeed(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ATTACK_SPEED, NightKit.PLAYER_SPEED, EquipmentSlot.MAINHAND);
    }

    private static Mob tagged(ServerLevel level, String tag) {
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob mob && mob.isAlive() && e.entityTags().contains(tag)) {
                return mob;
            }
        }
        return null;
    }

    /** One full-charge hit of the main hand on the mob; what it lost. {@code fall}: a fall distance for the hit. */
    private static float hit(ServerPlayer player, Mob target, double fall, String what) {
        target.invulnerableTime = 0;
        float before = target.getHealth();
        float charge = player.getAttackStrengthScale(0.5f);
        player.setSprinting(false);
        player.setOnGround(true);   // no critical hit (it needs the air), only the mace's smash looks at the fall
        player.fallDistance = fall;
        player.attack(target);
        double left = player.fallDistance;
        player.fallDistance = 0.0;
        float lost = before - target.getHealth();
        log("  hit " + what + ": charge " + f(charge) + ", lost " + f(lost) + ", fall " + f(fall) + " -> " + f(left)
                + ", main hand " + player.getMainHandItem().getItem());
        if (charge < 0.999f) {
            FAILS.add("not charged before the hit " + what + " (" + charge + ")");
        }
        return lost;
    }

    // ------------------------------------------------------------------ the scene

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode creative @p");   // the skills count in creative (no night needed)
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("kill @e[tag=" + PIG + "]");
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        context.getInput().lookAt(-90.0f, 0.0f);
        context.waitTicks(10);
        server(context, "setup", (player, m) -> {
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);   // a kit of a scene before
            }
            player.getInventory().clearContent();
        });
        context.waitTicks(5);

        // ---------------------------------------------------------------- 1: the kit of both tanks, level by level
        server(context, "kits", (player, m) -> {
            HolderLookup.RegistryLookup<Enchantment> ench = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            for (String sub : new String[]{SkillTree.SUB_SHIELDFIGHTER, SkillTree.SUB_BULWARK}) {
                KitWeapons.Look look = KitWeapons.of(sub);
                for (int level : new int[]{1, 6, 11, 14, 20}) {
                    kit(player, m, sub, level);
                    String at = sub + " level " + level;
                    int slot = find(player, KitService::isKitMace);
                    ItemStack mace = slot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(slot);
                    check(!mace.isEmpty(), at + ": a mace in the kit");
                    check(find(player, s -> s.is(ItemTags.SWORDS)) < 0, at + ": no sword in the kit");
                    check(player.getItemBySlot(EquipmentSlot.OFFHAND).has(DataComponents.BLOCKS_ATTACKS), at + ": the shield in the off hand");
                    if (mace.isEmpty()) {
                        continue;
                    }
                    check(mace.is(Items.MACE) && KitService.isKit(mace) && mace.has(DataComponents.UNBREAKABLE),
                            at + ": a marked, unbreakable vanilla mace");
                    check(look != null && Objects.equals(String.valueOf(mace.get(DataComponents.ITEM_MODEL)), look.itemModel())
                            && Objects.equals(mace.get(DataComponents.ITEM_NAME), Component.translatable(look.nameKey())),
                            at + ": own look " + (look == null ? "?" : look.key()) + " (" + mace.get(DataComponents.ITEM_MODEL) + ")");
                    int sharp = NightKit.sharpness(level);
                    ItemEnchantments enchants = mace.get(DataComponents.ENCHANTMENTS);
                    int maceSharp = enchants == null ? 0 : ench.get(Enchantments.SHARPNESS).map(enchants::getLevel).orElse(0);
                    check(maceSharp == sharp, at + ": Sharpness " + maceSharp + " like the sword's " + sharp);
                    ItemStack sword = KitService.kitSword(ench, NightKit.weapon(level), sharp);
                    double e = NightKit.sharpnessBonus(sharp);
                    double swordHit = attackDamage(sword) + e;
                    double maceHit = attackDamage(mace) + e;
                    log(String.format(Locale.ROOT, "%s: sword %s (%s + %s), mace %s, x%s, speed sword %s mace %s", at, f(swordHit),
                            sword.getItem(), f(e), f(maceHit), f(maceHit / swordHit), f(attackSpeed(sword)), f(attackSpeed(mace))));
                    check(Math.abs(maceHit - 2.5 * swordHit) < 0.01, at + ": a mace hit " + f(maceHit) + " = 2.5 x the sword's " + f(swordHit));
                    check(Math.abs(maceHit - NightKit.maceHit(level, sharp)) < 0.01, at + ": the table's " + f(NightKit.maceHit(level, sharp)));
                    check(Math.abs(attackSpeed(mace) - 0.8) < 0.001 && Math.abs(attackSpeed(sword) - 1.6) < 0.001,
                            at + ": 0.8 swings a second (sword 1.6)");
                }
            }
            // The class window's kit page shows the mace too.
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            List<String> preview = m.kits().preview(player, own, 6);
            String all = String.join("\n", preview);
            check(all.contains("minecraft:mace") && all.contains("bulwark_wallmace") && !all.contains("_sword\""),
                    "the kit page of the bulwark shows the mace, no sword");
            // The healers keep the sword.
            kit(player, m, SkillTree.SUB_MEDIC, 6);
            check(find(player, s -> s.is(ItemTags.SWORDS)) >= 0 && find(player, KitService::isKitMace) < 0,
                    "the medic keeps his sword");
        });
        context.waitTicks(5);

        // ---------------------------------------------------------------- 2: real hits, mace against sword
        game.getServer().runCommand("execute as @p at @p run summon minecraft:pig ^ ^ ^2 {NoAI:1b,PersistenceRequired:1b,Tags:[\"" + PIG + "\"]}");
        context.waitTicks(5);
        float[] lost = new float[8];
        for (int level : new int[]{1, 20}) {
            final int lv = level;
            final int base = level == 1 ? 0 : 4;
            server(context, "mace " + level, (player, m) -> {
                kit(player, m, SkillTree.SUB_SHIELDFIGHTER, lv);
                Mob pig = tagged((ServerLevel) player.level(), PIG);
                check(pig != null, "a pig to hit");
                if (pig != null) {
                    pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0);
                    pig.setHealth(2000f);
                }
                int slot = find(player, KitService::isKitMace);
                if (slot >= 0) {
                    hold(player, player.getInventory().getItem(slot), slot);
                }
            });
            context.waitTicks(30);   // the mace's charge (25 ticks)
            server(context, "mace hit " + level, (player, m) -> {
                Mob pig = tagged((ServerLevel) player.level(), PIG);
                log("level " + lv + ": attack speed " + f(player.getAttributeValue(Attributes.ATTACK_SPEED)) + ", charge time "
                        + f(player.getCurrentItemAttackStrengthDelay()) + " ticks");
                check(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - 0.8) < 0.01, "level " + lv + ": the player swings 0.8 a second with the mace");
                if (pig != null && KitService.isKitMace(player.getMainHandItem())) {
                    lost[base] = hit(player, pig, 0.0, "mace level " + lv);
                }
            });
            context.waitTicks(12);
            server(context, "mace charge " + level, (player, m) -> {
                float half = player.getAttackStrengthScale(0.5f);
                log("level " + lv + ": 12 ticks after the mace hit the charge is " + f(half));
                check(half < 0.6f, "level " + lv + ": half a second after a mace hit it is only half charged (" + f(half) + ")");
            });
            context.waitTicks(18);
            server(context, "mace fall " + level, (player, m) -> {
                Mob pig = tagged((ServerLevel) player.level(), PIG);
                if (pig != null && KitService.isKitMace(player.getMainHandItem())) {
                    lost[base + 1] = hit(player, pig, 6.0, "mace after a 6-block fall, level " + lv);
                }
            });
            context.waitTicks(5);
            server(context, "sword " + level, (player, m) -> {
                HolderLookup.RegistryLookup<Enchantment> ench = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                ItemStack sword = KitService.mark(KitService.kitSword(ench, NightKit.weapon(lv), NightKit.sharpness(lv)));
                hold(player, sword, -1);
            });
            context.waitTicks(30);
            server(context, "sword hit " + level, (player, m) -> {
                Mob pig = tagged((ServerLevel) player.level(), PIG);
                log("level " + lv + ": sword attack speed " + f(player.getAttributeValue(Attributes.ATTACK_SPEED)) + ", charge time "
                        + f(player.getCurrentItemAttackStrengthDelay()) + " ticks");
                if (pig != null && player.getMainHandItem().is(ItemTags.SWORDS)) {
                    lost[base + 2] = hit(player, pig, 0.0, "sword level " + lv);
                }
                int sharp = NightKit.sharpness(lv);
                float mace = lost[base];
                float sword = lost[base + 2];
                log(String.format(Locale.ROOT, "level %d: mace %.2f, sword %.2f, x%.3f; after a fall %.2f; table mace %.2f sword %.2f",
                        lv, mace, sword, sword > 0 ? mace / sword : 0, lost[base + 1], NightKit.maceHit(lv, sharp), NightKit.swordHit(lv, sharp)));
                check(sword > 0 && Math.abs(mace / sword - 2.5) < 0.05, "level " + lv + ": a mace hit " + f(mace) + " is 2.5 x a sword hit " + f(sword));
                check(Math.abs(mace - NightKit.maceHit(lv, sharp)) < 0.05, "level " + lv + ": the mace hit counts its Sharpness (" + f(mace)
                        + " = " + f(NightKit.maceHit(lv, sharp)) + ")");
                check(Math.abs(lost[base + 1] - mace) < 0.05, "level " + lv + ": no smash bonus after a fall (" + f(lost[base + 1]) + " = " + f(mace) + ")");
            });
            context.waitTicks(5);
        }
        // Control: a vanilla mace (no kit) does smash, so the fall above was a real one.
        server(context, "vanilla mace", (player, m) -> hold(player, new ItemStack(Items.MACE), -1));
        context.waitTicks(40);   // the vanilla mace swings 0.6 a second (33 ticks)
        server(context, "vanilla smash", (player, m) -> {
            Mob pig = tagged((ServerLevel) player.level(), PIG);
            if (pig != null) {
                float plain = hit(player, pig, 6.0, "vanilla mace after a 6-block fall");
                check(plain > 6.0f + 10.0f, "control: a vanilla mace smashes after the same fall (" + f(plain) + " > 16)");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        context.waitTicks(5);

        // ---------------------------------------------------------------- 3: the shield and the shield fighter's effects
        server(context, "guard kit", (player, m) -> {
            kit(player, m, SkillTree.SUB_SHIELDFIGHTER, 1, "g_ironward", "g_revenge");
            m.save().skillsOf(player.getUUID().toString()).levels.put("g_ironward", 2);
            int slot = find(player, KitService::isKitMace);
            if (slot >= 0) {
                hold(player, player.getInventory().getItem(slot), slot);
            }
            // Hits must land on the creative player (the skills count in creative): not invulnerable for now.
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
            player.setHealth(player.getMaxHealth());
        });
        game.getServer().runCommand("execute as @p at @p run summon minecraft:husk ^ ^ ^1.6 {NoAI:1b,PersistenceRequired:1b,Tags:[\"" + HUSK + "\"]}");
        context.waitTicks(10);
        final double[] armor = new double[2];
        final ItemStack[] shield = {ItemStack.EMPTY};
        server(context, "iron ward on", (player, m) -> {
            armor[0] = player.getAttributeValue(Attributes.ARMOR);
            shield[0] = player.getItemBySlot(EquipmentSlot.OFFHAND).copy();
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            Mob husk = tagged((ServerLevel) player.level(), HUSK);
            if (husk != null) {
                husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
                husk.setHealth(500f);
            }
        });
        context.waitTicks(5);
        server(context, "iron ward off", (player, m) -> {
            armor[1] = player.getAttributeValue(Attributes.ARMOR);
            player.setItemSlot(EquipmentSlot.OFFHAND, shield[0]);
            log("iron ward: armour with the mace and the shield " + f(armor[0]) + ", without the shield " + f(armor[1]));
            check(KitService.isKitMace(player.getMainHandItem()) && armor[0] - armor[1] > 0.5,
                    "iron ward: with the mace in hand the shield in the off hand still gives its armour");
        });
        context.waitTicks(5);
        // Raised like a player does it: the use key held (a raise on the server alone is let go by the client).
        context.runOnClient(client -> client.options.keyUse.setDown(true));
        context.waitTicks(12);
        server(context, "block", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            Mob husk = tagged(level, HUSK);
            boolean blocking = player.isBlocking();
            check(blocking && KitService.isKitMace(player.getMainHandItem()), "the shield blocks with the mace in the main hand");
            if (husk == null) {
                check(false, "a husk to block");
                return;
            }
            player.invulnerableTime = 0;
            float life = player.getHealth() + player.getAbsorptionAmount();
            float huskLife = husk.getHealth();
            boolean hit = husk.doHurtTarget(level, player);
            float took = life - (player.getHealth() + player.getAbsorptionAmount());
            float riposte = huskLife - husk.getHealth();
            log("block: blocking=" + blocking + " hit=" + hit + " took " + f(took) + ", the husk lost " + f(riposte) + " (riposte)");
            check(took < 0.5f, "the blocked blow does no harm (" + f(took) + ")");
            check(riposte > 1.0f, "riposte: the blocked husk is struck back (" + f(riposte) + ")");
        });
        context.runOnClient(client -> client.options.keyUse.setDown(false));
        context.waitTicks(5);
        server(context, "lowered", (player, m) -> {
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
        });
        context.waitTicks(3);
        // Revenge charges from what came through: a blow without the shield up, then the mace hit takes it along.
        server(context, "unblocked", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            Mob husk = tagged(level, HUSK);
            if (husk == null) {
                return;
            }
            player.invulnerableTime = 0;
            float life = player.getHealth() + player.getAbsorptionAmount();
            husk.doHurtTarget(level, player);
            float took = life - (player.getHealth() + player.getAbsorptionAmount());
            log("unblocked: blocking=" + player.isBlocking() + " took " + f(took) + " (charges the revenge)");
            check(took > 0.1f, "without the shield up the blow comes through (" + f(took) + ")");
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(30);
        final float[] blows = new float[2];
        server(context, "revenge", (player, m) -> {
            Mob husk = tagged((ServerLevel) player.level(), HUSK);
            if (husk != null && KitService.isKitMace(player.getMainHandItem())) {
                blows[0] = hit(player, husk, 0.0, "mace with the revenge of the blocked blow");
            }
        });
        context.waitTicks(30);
        server(context, "plain", (player, m) -> {
            Mob husk = tagged((ServerLevel) player.level(), HUSK);
            if (husk != null && KitService.isKitMace(player.getMainHandItem())) {
                blows[1] = hit(player, husk, 0.0, "mace without revenge");
            }
            log("revenge: " + f(blows[0]) + " against " + f(blows[1]));
            check(blows[1] > 10f && blows[0] - blows[1] > 0.7f, "revenge: the mace hit after a blocked blow is harder (" + f(blows[0]) + " > " + f(blows[1]) + ")");
        });
        context.waitTicks(5);

        // ---------------------------------------------------------------- the end: everything back
        server(context, "end", (player, m) -> {
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            m.save().skillsOf(player.getUUID().toString()).reset();
            player.setHealth(player.getMaxHealth());
        });
        game.getServer().runCommand("kill @e[tag=" + PIG + "]");
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(10);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
