package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.AbilityKeys;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.TrapKind;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.EngineerAbilities;
import com.mki.nightsiege.game.EngineerEscape;
import com.mki.nightsiege.game.ModContent;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tor 4b "techniker" (Variante B "Turm als Waffe"): for every engineer subclass every key of places 1-4
 * fires on foot and from the seat of his tower (its pause starts), the abilities hurt the night's monsters,
 * the remote is gone from the kit. Log lines start with "[techniker]"; the scene fails with 0 checks.
 */
final class TechnikerScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static final String HUSK = "ns_tech_husk";

    /** Subclass → its node and the tower it sits in. */
    private static final Map<String, TrapKind> TOWER = Map.of(
            SkillTree.SUB_GUNNER, TrapKind.TURRET,
            SkillTree.SUB_ELECTRICIAN, TrapKind.TESLA,
            SkillTree.SUB_DEMOLITION, TrapKind.MORTAR,
            SkillTree.SUB_ALCHEMIST, TrapKind.BREWER);
    /**
     * Subclass → the tower he sits in. Only the arrow turret and the tesla coil have a seat
     * (TowerBlock.mannable); the demolition expert and the alchemist sit in an arrow turret.
     */
    private static final Map<String, TrapKind> SEAT = Map.of(
            SkillTree.SUB_GUNNER, TrapKind.TURRET,
            SkillTree.SUB_ELECTRICIAN, TrapKind.TESLA,
            SkillTree.SUB_DEMOLITION, TrapKind.TURRET,
            SkillTree.SUB_ALCHEMIST, TrapKind.TURRET);

    private TechnikerScenes() {
    }

    private static void log(String line) {
        System.out.println("[techniker] " + line);
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

    private static List<Mob> husks(ServerLevel level) {
        List<Mob> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob mob && mob.isAlive() && e.entityTags().contains(HUSK)) {
                out.add(mob);
            }
        }
        return out;
    }

    private static float lost(ServerLevel level) {
        float sum = 0f;
        for (Mob m : husks(level)) {
            sum += m.getMaxHealth() - m.getHealth();
        }
        return sum;
    }

    private static void setup(ServerPlayer player, SiegeManager m, String sub) {
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        own.unlockClass(SkillClass.ENGINEER);
        own.switchTo(SkillClass.ENGINEER);
        own.levels.clear();
        own.levels.put(TOWER.get(sub).id(), 2);
        if (SkillTree.SUB_ALCHEMIST.equals(sub)) {
            own.levels.put("brew_heal", 1);
            own.levels.put("brew_poison", 1);
            own.levels.put("e_acid_flask", 1);
        }
        m.kits().equip(player, 6);
        EngineerAbilities.reset();
        EngineerEscape.reset();
        m.kits().traps().resetZones();
    }

    /** Fires every box of the bar once (looking at the first husk) and checks its pause started. */
    private static void fireAll(ServerPlayer player, SiegeManager m, String sub, String where) {
        ServerLevel level = (ServerLevel) player.level();
        List<UltState.Slot> slots = m.abilitySlots(player);
        int places = 0;
        for (int i = 0; i < slots.size(); i++) {
            UltState.Slot slot = slots.get(i);
            List<Mob> targets = husks(level);
            if (!targets.isEmpty()) {
                Mob t = targets.get(0);
                player.lookAt(EntityAnchorArgument.Anchor.EYES, t.position().add(0, t.getBbHeight() * 0.5, 0));
            }
            m.onCoreAction(player, "slot:" + i);
            int left = m.abilitySlots(player).get(i).cooldownTicks;
            AbilityKeys.Key key = AbilityKeys.keyOf(sub, slot.id);
            check(key != null && key.ability(), sub + " " + where + ": " + slot.id + " has a place (" + key + ")");
            check(left > 0, sub + " " + where + ": " + slot.id + " fired, pause " + left + " ticks");
            places++;
        }
        int want = SkillTree.SUB_ALCHEMIST.equals(sub) ? 4 : 3;
        check(places == want, sub + " " + where + ": " + places + " of " + want + " places on the bar");
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 4 true");
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        context.getInput().lookAt(-90.0f, 0.0f);
        context.waitTicks(10);
        final int[] saved = new int[2];
        final boolean[] refill = new boolean[1];
        server(context, "night", (player, m) -> {
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

        for (String sub : List.of(SkillTree.SUB_GUNNER, SkillTree.SUB_ELECTRICIAN, SkillTree.SUB_DEMOLITION, SkillTree.SUB_ALCHEMIST)) {
            final BlockPos[] tower = new BlockPos[2];
            final float[] before = new float[1];
            game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
            String husk = "execute as @p at @p run summon minecraft:husk ^%s ^ ^7 {NoAI:1b,PersistenceRequired:1b,Tags:[\"nightsiege\",\"" + HUSK + "\"]}";
            for (String side : new String[]{"0", "0.9", "-0.9"}) {
                game.getServer().runCommand(husk.formatted(side));
            }
            context.waitTicks(10);
            server(context, sub + " foot", (player, m) -> {
                ServerLevel level = (ServerLevel) player.level();
                for (Mob h : husks(level)) {
                    h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
                    h.setHealth(1000f);
                }
                setup(player, m, sub);
                boolean remote = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    remote |= ModContent.REMOTE != null && player.getInventory().getItem(i).is(ModContent.REMOTE);
                }
                check(!remote, sub + ": no remote in the kit");
                // his tower 2 blocks beside him, a mortar for the target point of the demolition expert
                tower[0] = player.blockPosition().offset(0, 0, 2);
                EventLookTest.place(level, m, player, tower[0], TOWER.get(sub));
                tower[1] = tower[0];
                if (SEAT.get(sub) != TOWER.get(sub)) {
                    // his seat 2 blocks on the other side (mortar and potion thrower have none)
                    tower[1] = player.blockPosition().offset(0, 0, -2);
                    EventLookTest.place(level, m, player, tower[1], SEAT.get(sub));
                }
                before[0] = lost(level);
                fireAll(player, m, sub, "on foot");
            });
            context.waitTicks(30);   // the grenade's fuse, the static field's first sting
            server(context, sub + " foot damage", (player, m) -> {
                ServerLevel level = (ServerLevel) player.level();
                float got = lost(level) - before[0];
                log(sub + " on foot: monsters lost " + got);
                check(got > 0f, sub + " on foot: the abilities hurt the monsters (" + got + ")");
            });
            server(context, sub + " seat", (player, m) -> {
                ServerLevel level = (ServerLevel) player.level();
                EngineerAbilities.reset();
                EngineerEscape.reset();
                m.kits().traps().resetZones();
                boolean ok = m.kits().traps().tryMount(level, tower[1], player, ItemStack.EMPTY);
                check(ok && m.kits().traps().isSeated(player), sub + ": sits in his " + SEAT.get(sub).id());
                before[0] = lost(level);
                fireAll(player, m, sub, "in the seat");
            });
            context.waitTicks(30);
            server(context, sub + " seat end", (player, m) -> {
                ServerLevel level = (ServerLevel) player.level();
                float got = lost(level) - before[0];
                log(sub + " in the seat: monsters lost " + got);
                check(got > 0f, sub + " in the seat: the abilities hurt the monsters (" + got + ")");
                m.kits().traps().releaseAll(level.getServer());
                level.setBlock(tower[0], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(tower[1], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            });
            context.waitTicks(10);
        }

        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        server(context, "end", (player, m) -> {
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            m.config().refillSpawns = refill[0];
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
        log("summary: " + (checks - FAILS.size()) + " of " + checks + " OK" + (FAILS.isEmpty() ? "" : ", FAIL: " + FAILS));
    }
}
