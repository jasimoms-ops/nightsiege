package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.NightKit;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.TankHeal;
import com.mki.nightsiege.game.BossService;
import com.mki.nightsiege.game.Expeditions;
import com.mki.nightsiege.game.GuardianEffects;
import com.mki.nightsiege.game.KitService;
import com.mki.nightsiege.game.Nemesis;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.WardenNight;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Tor 4.2 "tanks" (Simon 08.11.): the shield fighter and the bulwark heal themselves on Y (only themselves, instead
 * of potions; the medic has no such key), their mace hits one of three husks standing close and leaves the other two
 * whole, and a mace hit on an elite deals 1.25 times a hit on a plain monster. Log lines start with "[tanks]";
 * the scene fails with 0 checks.
 */
final class TankScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static final String GROUP = "ns_tank_group";
    private static final String MIDDLE = "ns_tank_middle";
    private static final String PLAIN = "ns_tank_plain";
    private static final String ELITE = "ns_tank_elite";

    private TankScenes() {
    }

    private static void log(String line) {
        System.out.println("[tanks] " + line);
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

    private static String subNode(String sub) {
        return switch (sub) {
            case SkillTree.SUB_SHIELDFIGHTER -> "riposte";
            case SkillTree.SUB_BULWARK -> "g_bastion";
            default -> "g_healing_hand";
        };
    }

    private static void kit(ServerPlayer player, SiegeManager m, String sub) {
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        own.unlockClass(SkillClass.GUARDIAN);
        own.switchTo(SkillClass.GUARDIAN);
        own.levels.clear();
        own.levels.put(subNode(sub), 1);
        m.kits().equip(player, 6);
    }

    private static void holdMace(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (KitService.isKitMace(s)) {
                int sel = inv.getSelectedSlot();
                if (i != sel) {
                    ItemStack old = inv.getItem(sel);
                    inv.setItem(sel, s);
                    inv.setItem(i, old);
                }
                return;
            }
        }
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

    private static void tough(Mob mob) {
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0);
        mob.setHealth(2000f);
        mob.invulnerableTime = 0;
    }

    /** A full-charge hit on the ground, no crit, no sprint. */
    private static void strike(ServerPlayer player, Mob target) {
        target.invulnerableTime = 0;
        player.setSprinting(false);
        player.setOnGround(true);
        player.fallDistance = 0.0;
        if (player.getAttackStrengthScale(0.5f) < 0.999f) {
            FAILS.add("not charged before the hit");
        }
        player.attack(target);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("gamemode creative @p");   // the skills count in creative (no night needed)
        game.getServer().runCommand("effect clear @p");
        for (String t : new String[]{GROUP, PLAIN, ELITE}) {
            game.getServer().runCommand("kill @e[tag=" + t + "]");
        }
        context.getInput().lookAt(-90.0f, 0.0f);
        context.waitTicks(10);
        server(context, "setup", (player, m) -> {
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            player.getInventory().clearContent();
        });
        context.waitTicks(5);

        // ---------------------------------------------------------------- 1: the self heal on Y, both tanks
        for (String sub : new String[]{SkillTree.SUB_SHIELDFIGHTER, SkillTree.SUB_BULWARK}) {
            final float[] life = new float[3];
            server(context, "heal " + sub, (player, m) -> {
                kit(player, m, sub);
                int slot = m.abilitySlotOf(player, TankHeal.ID);
                check(slot >= 0, sub + ": the self heal is on the bar (slot " + slot + ")");
                player.setHealth(player.getMaxHealth() * 0.3f);
                life[0] = player.getHealth();
                m.onCoreAction(player, "slot:" + slot);   // the key's own path
                life[1] = player.getHealth();
                m.onCoreAction(player, "slot:" + slot);   // at once again: it waits
                life[2] = player.getHealth();
                float want = player.getMaxHealth() * 0.2f;
                log(sub + ": max " + f(player.getMaxHealth()) + ", life " + f(life[0]) + " -> " + f(life[1]) + " -> " + f(life[2]));
                check(life[1] - life[0] > 0.5f, sub + ": the self heal raises the life (" + f(life[0]) + " -> " + f(life[1]) + ")");
                check(Math.abs(life[1] - life[0] - want) < 0.05f, sub + ": by 20 % of the max health (" + f(life[1] - life[0]) + " = " + f(want) + ")");
                check(Math.abs(life[2] - life[1]) < 0.01f, sub + ": pressed again at once it heals nothing (the pause)");
                player.setHealth(player.getMaxHealth());
            });
            context.waitTicks(25);   // creative pause of 20 ticks
            server(context, "heal full " + sub, (player, m) -> {
                float before = player.getHealth();
                float got = m.combatEffects().guardian().castTankHeal(player);
                check(got == 0f && player.getHealth() == before, sub + ": at full life it heals nothing (" + f(got) + ")");
                // Prüfer-Fund 5: the call at full life used no cooldown, a wounded call right after heals.
                player.setHealth(player.getMaxHealth() * 0.5f);
                float wounded = player.getHealth();
                check(m.combatEffects().guardian().castTankHeal(player) > 0.5f,
                        sub + ": at full life it keeps its pause, wounded right after it heals (" + f(player.getHealth() - wounded) + ")");
                player.setHealth(player.getMaxHealth());
            });
            context.waitTicks(5);
        }
        server(context, "medic", (player, m) -> {
            kit(player, m, SkillTree.SUB_MEDIC);
            check(m.abilitySlotOf(player, TankHeal.ID) < 0, "the medic has no tank self heal");
            player.setHealth(player.getMaxHealth() * 0.3f);
            float before = player.getHealth();
            m.combatEffects().guardian().castTankHeal(player);
            check(player.getHealth() == before, "the medic's call of the tank heal does nothing");
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(5);

        // ---------------------------------------------------------------- 2: the mace hits one of three
        server(context, "group kit", (player, m) -> {
            kit(player, m, SkillTree.SUB_SHIELDFIGHTER);
            holdMace(player);
        });
        String husk = "execute as @p at @p run summon minecraft:husk ^%s ^ ^2 {NoAI:1b,PersistenceRequired:1b,Tags:[\"%s\"%s]}";
        game.getServer().runCommand(husk.formatted("0.7", GROUP, ""));
        game.getServer().runCommand(husk.formatted("0", GROUP, ",\"" + MIDDLE + "\""));
        game.getServer().runCommand(husk.formatted("-0.7", GROUP, ""));
        context.waitTicks(30);   // the mace's charge (20 ticks)
        server(context, "group hit", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            List<Mob> group = tagged(level, GROUP);
            check(group.size() == 3, "three husks stand close (" + group.size() + ")");
            group.forEach(TankScenes::tough);
            Mob middle = tagged(level, MIDDLE).stream().findFirst().orElse(null);
            check(middle != null && KitService.isKitMace(player.getMainHandItem()), "the mace in hand, a husk in the middle");
            if (middle == null || group.size() != 3) {
                return;
            }
            strike(player, middle);
            int hurt = 0;
            for (Mob mob : group) {
                float lost = 2000f - mob.getHealth();
                log("  husk " + (mob == middle ? "middle" : "beside") + " lost " + f(lost) + " at " + f(mob.distanceTo(middle)) + " blocks");
                if (lost > 0.01f) {
                    hurt++;
                }
            }
            check(2000f - middle.getHealth() > 1f, "the mace hits the husk it aims at (" + f(2000f - middle.getHealth()) + ")");
            check(hurt == 1, "only one of three husks loses life (" + hurt + ")");
        });
        context.waitTicks(5);
        game.getServer().runCommand("kill @e[tag=" + GROUP + "]");

        // ---------------------------------------------------------------- 3: +25 % on an elite
        String own = "execute as @p at @p run summon minecraft:husk ^%s ^ ^2 {NoAI:1b,PersistenceRequired:1b,Tags:[\"nightsiege\",\"%s\"%s]}";
        game.getServer().runCommand(own.formatted("0.8", PLAIN, ""));
        game.getServer().runCommand(own.formatted("-0.8", ELITE, ",\"nightsiege_affix_elite\""));
        context.waitTicks(30);
        final float[] lost = new float[2];
        server(context, "plain hit", (player, m) -> {
            Mob plain = tagged((ServerLevel) player.level(), PLAIN).stream().findFirst().orElse(null);
            Mob elite = tagged((ServerLevel) player.level(), ELITE).stream().findFirst().orElse(null);
            check(plain != null && elite != null, "a plain husk and an elite husk");
            if (plain != null && elite != null) {
                tough(plain);
                tough(elite);
                strike(player, plain);
                lost[0] = 2000f - plain.getHealth();
            }
        });
        context.waitTicks(30);
        server(context, "elite hit", (player, m) -> {
            Mob elite = tagged((ServerLevel) player.level(), ELITE).stream().findFirst().orElse(null);
            if (elite != null) {
                strike(player, elite);
                lost[1] = 2000f - elite.getHealth();
            }
            double ratio = lost[0] > 0 ? lost[1] / lost[0] : 0;
            log("mace: plain " + f(lost[0]) + ", elite " + f(lost[1]) + ", x" + f(ratio));
            check(lost[0] > 1f && Math.abs(ratio - NightKit.MACE_ELITE_FACTOR) < 0.05,
                    "a mace hit on an elite deals x" + f(ratio) + " (want " + NightKit.MACE_ELITE_FACTOR + ")");

            // Prüfer-Fund 3: the nemesis, the warden of the night and a dungeon champion are special foes too.
            for (String tag : new String[] {Nemesis.TAG, WardenNight.TAG, Expeditions.CHAMPION_TAG, BossService.TAG_BOSS}) {
                Mob probe = EntityTypes.HUSK.create((ServerLevel) player.level(), EntitySpawnReason.COMMAND);
                if (probe != null) {
                    probe.addTag(tag);
                }
                check(probe != null && GuardianEffects.isSpecialFoe(probe), "the mace's bonus reaches a foe tagged " + tag);
            }
            Mob plainProbe = EntityTypes.HUSK.create((ServerLevel) player.level(), EntitySpawnReason.COMMAND);
            check(plainProbe != null && !GuardianEffects.isSpecialFoe(plainProbe), "a plain husk is no special foe");
        });
        context.waitTicks(5);
        game.getServer().runCommand("kill @e[tag=" + PLAIN + "]");
        game.getServer().runCommand("kill @e[tag=" + ELITE + "]");
        server(context, "end", (player, m) -> {
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            m.save().skillsOf(player.getUUID().toString()).reset();
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(10);
        if (checks == 0) {
            check(false, "no check ran");
        }
        log("summary: " + (checks - FAILS.size()) + " of " + checks + " OK" + (FAILS.isEmpty() ? "" : ", FAIL: " + FAILS));
    }
}
