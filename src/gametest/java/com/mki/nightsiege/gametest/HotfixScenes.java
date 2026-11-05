package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.game.BossService;
import com.mki.nightsiege.game.Expeditions;
import com.mki.nightsiege.game.LadderClimb;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.Stuns;
import com.mki.nightsiege.game.Wounds;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The balance hotfix of 5 November 2026 (Simon: "funktionieren Anti-Heal und Schild-Durchschlag überhaupt?"):
 * the anti-heal halves a heal, the breaker's blow goes through a raised shield, the stunner's slam stuns
 * (and a shield takes it), bosses take no push, a dungeon monster's hit takes its share of the max health,
 * and a ladder without a way off at the top is no ladder for the monsters. Log lines start with "[hotfix]".
 */
final class HotfixScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private HotfixScenes() {
    }

    private static void log(String line) {
        System.out.println("[hotfix] " + line);
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

    private static Mob mob(ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z) {
        Mob mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            return null;
        }
        mob.setPos(x, y, z);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        return mob;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 260;
        int cz = pos.getZ() + 260;
        int cy = EventLookTest.yard(context, game, cx, cz, 30, 12);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy, cz));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(20);
        final int[] saved = new int[2];
        // A test night without waves: the foes tick, the stuns and wounds work as in a real night.
        server(context, "night", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            m.config().testPrepSeconds = 1;
            m.config().batchIntervalSeconds = 600;
            boolean ok = m.prepareTest(level.getServer(), level, new BlockPos(cx + 8, cy, cz + 8), 10,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            log("night prepared=" + ok);
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);

        // ---------------------------------------------------------------- anti-heal
        server(context, "wounds", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            check(m.isActive(), "the test night runs");
            Mob spider = mob(level, EntityTypes.SPIDER, player.getX() + 2, player.getY(), player.getZ());
            Mob zombie = mob(level, EntityTypes.ZOMBIE, player.getX() - 2, player.getY(), player.getZ());
            Wounds.cure(player);
            Wounds.afterDamage(player, level.damageSources().mobAttack(zombie), 2f);
            check(!Wounds.wounded(player), "a zombie's hit leaves no wounds");
            Wounds.afterDamage(player, level.damageSources().mobAttack(spider), 2f);
            check(Wounds.wounded(player), "a spider's hit leaves wounds (level " + m.currentLevel() + ")");
            player.setHealth(10f);
            player.heal(4f);
            float wounded = player.getHealth() - 10f;
            Wounds.cure(player);
            player.setHealth(10f);
            player.heal(4f);
            float healthy = player.getHealth() - 10f;
            log(String.format(java.util.Locale.ROOT, "heal 4 wounded=%.2f healthy=%.2f", wounded, healthy));
            check(Math.abs(wounded - 2f) < 0.05f, "wounded, a heal of 4 gives 2");
            check(Math.abs(healthy - 4f) < 0.05f, "healed of the wounds, a heal of 4 gives 4");
            spider.discard();
            zombie.discard();
            player.setHealth(player.getMaxHealth());
        });

        // ---------------------------------------------------------------- dungeon monster share
        final float[] took = new float[2];
        server(context, "dungeon share", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            Mob plain = mob(level, EntityTypes.ZOMBIE, player.getX(), player.getY(), player.getZ() + 1.5);
            Mob dungeon = mob(level, EntityTypes.ZOMBIE, player.getX(), player.getY(), player.getZ() + 1.5);
            dungeon.addTag(Expeditions.TAG);
            for (Mob z : new Mob[]{plain, dungeon}) {
                z.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            plain.doHurtTarget(level, player);
            took[0] = player.getMaxHealth() - player.getHealth();
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            // A dungeon's monsters only hurt the players in its fight: for this one blow he is one of them.
            java.util.Set<UUID> fighters = fightersOf(m.expeditions());
            boolean added = fighters != null && fighters.add(player.getUUID());
            dungeon.doHurtTarget(level, player);
            if (added) {
                fighters.remove(player.getUUID());
            }
            took[1] = player.getMaxHealth() - player.getHealth();
            double share = m.config().dungeonMobPercent(m.currentLevel()) * player.getMaxHealth();
            log(String.format(java.util.Locale.ROOT, "dungeon share: plain took=%.2f dungeon took=%.2f share=%.2f (level %d)",
                    took[0], took[1], share, m.currentLevel()));
            // The share goes through the player's own protection (class, armour), as the guardians' does.
            check(took[1] - took[0] > share * 0.5 && took[1] - took[0] < share * 1.2,
                    "a dungeon monster's blow takes its share of the max health on top");
            plain.discard();
            dungeon.discard();
            player.setHealth(player.getMaxHealth());
        });

        // ---------------------------------------------------------------- bosses take no push
        server(context, "push", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            Mob boss = mob(level, EntityTypes.ZOMBIE, player.getX() + 4, player.getY(), player.getZ() + 4);
            Mob plain = mob(level, EntityTypes.ZOMBIE, player.getX() - 4, player.getY(), player.getZ() + 4);
            Mob champion = mob(level, EntityTypes.ZOMBIE, player.getX(), player.getY(), player.getZ() + 6);
            boss.addTag(BossService.TAG_BOSS);
            champion.addTag(Expeditions.CHAMPION_TAG);
            for (Mob z : new Mob[]{boss, plain, champion}) {
                z.setDeltaMovement(0, 0, 0);
                z.push(1.5, 0.3, 0.0);
                z.push(new net.minecraft.world.phys.Vec3(0.0, 0.0, 1.5));
            }
            log("push boss=" + boss.getDeltaMovement() + " champion=" + champion.getDeltaMovement() + " plain=" + plain.getDeltaMovement());
            check(boss.getDeltaMovement().lengthSqr() < 1e-6, "a boss is not pushed");
            check(champion.getDeltaMovement().lengthSqr() < 1e-6, "a dungeon champion is not pushed");
            check(plain.getDeltaMovement().lengthSqr() > 1.0, "a plain monster still is");
            boss.discard();
            plain.discard();
            champion.discard();
        });

        // ---------------------------------------------------------------- the breaker's blow through the shield
        final Mob[] breaker = new Mob[1];
        server(context, "breaker", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            player.setHealth(player.getMaxHealth());
            breaker[0] = m.foesHost().spawn("nightsiege:breaker", player.blockPosition().south(2));
            if (breaker[0] != null) {
                breaker[0].setNoAi(true);
                breaker[0].teleportTo(player.getX(), player.getY(), player.getZ() + 1.5);
            }
            player.setYRot(0f);   // he looks south, at the breaker
            player.startUsingItem(InteractionHand.OFF_HAND);
            check(breaker[0] != null, "a breaker can be made");
        });
        context.waitTicks(12);
        server(context, "breaker blow", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            if (breaker[0] == null) {
                return;
            }
            boolean blocking = player.isBlocking();
            int before = m.foes().shieldsBroken;
            player.invulnerableTime = 0;
            float health = player.getHealth();
            breaker[0].doHurtTarget(level, player);
            float lost = health - player.getHealth();
            boolean locked = player.getCooldowns().isOnCooldown(player.getOffhandItem());
            log(String.format(java.util.Locale.ROOT, "breaker: blocking=%s lost=%.2f of %.0f locked=%s broken=%d",
                    blocking, lost, player.getMaxHealth(), locked, m.foes().shieldsBroken - before));
            check(blocking, "the shield is up");
            check(m.foes().shieldsBroken == before + 1, "the breaker's blow counts as through the shield");
            check(lost >= player.getMaxHealth() * m.config().breakerPercent / 100.0 * 0.5, "the blow takes life through the shield");
            check(locked, "the shield is locked afterwards");
            breaker[0].discard();
            player.stopUsingItem();
            player.setHealth(player.getMaxHealth());
        });

        // ---------------------------------------------------------------- the stunner
        final Mob[] stunner = new Mob[1];
        final double[] speed = new double[1];
        server(context, "stunner", (player, m) -> {
            player.stopUsingItem();
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            stunner[0] = m.foesHost().spawn("nightsiege:stunner", player.blockPosition().south(2));
            check(stunner[0] != null, "a stunner can be made");
            if (stunner[0] == null) {
                return;
            }
            stunner[0].setNoAi(true);
            stunner[0].teleportTo(player.getX(), player.getY(), player.getZ() + 1.5);
            m.foesHost().addMob(stunner[0]);
            stunner[0].setTarget(player);
            m.foes().stunnerNow(stunner[0]);
            speed[0] = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        });
        // Wind-up (1 s) and the slam; the server may lag behind the test's ticks, so wait for it, at most 6 s.
        final boolean[] first = new boolean[1];
        for (int i = 0; i < 12 && !first[0]; i++) {
            context.waitTicks(10);
            server(context, "stunner wait", (player, m) -> first[0] = stunner[0] == null || Stuns.stunned(player));
        }
        server(context, "stunned", (player, m) -> {
            if (stunner[0] == null) {
                return;
            }
            double now = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            log(String.format(java.util.Locale.ROOT, "stunner: stunned=%s speed %.3f -> %.3f landed=%d",
                    Stuns.stunned(player), speed[0], now, m.foes().stunsLanded));
            check(Stuns.stunned(player), "the stunner's slam stuns");
            check(now < 1e-6, "stunned, the player cannot move");
            check(Stuns.blocks("ult") && Stuns.blocks("slot") && !Stuns.blocks("buy"), "stunned, the ability keys do nothing");
        });
        final boolean[] over = new boolean[1];
        for (int i = 0; i < 12 && !over[0]; i++) {
            context.waitTicks(10);
            server(context, "stun wait", (player, m) -> over[0] = !Stuns.stunned(player));
        }
        server(context, "stun over", (player, m) -> {
            if (stunner[0] == null) {
                return;
            }
            check(!Stuns.stunned(player), "the stun wears off after 1.5 s");
            check(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed[0]) < 1e-6, "and the player moves again");
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            player.startUsingItem(InteractionHand.OFF_HAND);
        });
        context.waitTicks(10);
        server(context, "stunner shield", (player, m) -> {
            if (stunner[0] == null) {
                return;
            }
            raiseShield(player);
            stunner[0].teleportTo(player.getX(), player.getY(), player.getZ() + 1.5);
            stunner[0].setTarget(player);
            m.foes().stunnerNow(stunner[0]);
        });
        // The server may lag behind the test's ticks: wait until the slam came, at most 6 s. The night's
        // archers may knock him away in between: keep the stunner at his side and the shield up.
        final boolean[] slammed = new boolean[1];
        for (int i = 0; i < 12 && !slammed[0]; i++) {
            context.waitTicks(10);
            server(context, "stunner wait", (player, m) -> {
                slammed[0] = m.foes().stunsBlocked >= 1 || Stuns.stunned(player);
                if (!slammed[0] && stunner[0] != null) {
                    raiseShield(player);
                    stunner[0].teleportTo(player.getX(), player.getY(), player.getZ() + 1.5);
                    stunner[0].setTarget(player);
                }
            });
        }
        server(context, "stunner blocked", (player, m) -> {
            if (stunner[0] == null) {
                return;
            }
            log("stunner with shield: stunned=" + Stuns.stunned(player) + " blocked=" + m.foes().stunsBlocked
                    + " blocking=" + player.isBlocking());
            check(!Stuns.stunned(player) && m.foes().stunsBlocked >= 1, "a raised shield takes the slam");
            stunner[0].discard();
            player.stopUsingItem();
        });

        // ---------------------------------------------------------------- ladders without a way off
        server(context, "ladders", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            int x = cx + 6;
            int z = cz - 6;
            // A: a wall three high with a ladder up to its top: off at the top onto the wall.
            for (int dy = 0; dy < 3; dy++) {
                level.setBlockAndUpdate(new BlockPos(x, cy + dy, z + 1), Blocks.STONE_BRICKS.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, cy + dy, z), Blocks.LADDER.defaultBlockState()
                        .setValue(LadderBlock.FACING, Direction.NORTH));
            }
            // B: the same ladder on a wall six high: the ladder ends three below its top, nowhere to go.
            int x2 = x + 4;
            for (int dy = 0; dy < 6; dy++) {
                level.setBlockAndUpdate(new BlockPos(x2, cy + dy, z + 1), Blocks.STONE_BRICKS.defaultBlockState());
            }
            for (int dy = 0; dy < 3; dy++) {
                level.setBlockAndUpdate(new BlockPos(x2, cy + dy, z), Blocks.LADDER.defaultBlockState()
                        .setValue(LadderBlock.FACING, Direction.NORTH));
            }
            boolean a = LadderClimb.hasExit(level, new BlockPos(x, cy, z));
            boolean b = LadderClimb.hasExit(level, new BlockPos(x2, cy, z));
            log("ladder exits: up the wall=" + a + " ends below the top=" + b);
            check(a, "a ladder up to the top of its wall has a way off");
            check(!b, "a ladder that ends below the top of its wall has none");
        });

        server(context, "end", (player, m) -> {
            m.abort(player.level().getServer());
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            Stuns.clear(player.level().getServer());
            Wounds.cure(player);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        });
        context.waitTicks(20);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<UUID> fightersOf(Expeditions expeditions) {
        try {
            java.lang.reflect.Field field = Expeditions.class.getDeclaredField("fighters");
            field.setAccessible(true);
            return (java.util.Set<UUID>) field.get(expeditions);
        } catch (ReflectiveOperationException e) {
            log("no fighters field: " + e);
            return null;
        }
    }


    /** Shield in the off hand, off its cooldown (the breaker locked it), raised. */
    private static void raiseShield(ServerPlayer player) {
        ItemStack shield = player.getOffhandItem();
        if (!shield.is(Items.SHIELD)) {
            shield = new ItemStack(Items.SHIELD);
            player.setItemSlot(EquipmentSlot.OFFHAND, shield);
        }
        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(shield));
        if (!player.isUsingItem()) {
            player.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

}
