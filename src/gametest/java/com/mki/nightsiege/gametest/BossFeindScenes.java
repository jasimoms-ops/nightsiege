package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.BossKind;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.game.BossService;
import com.mki.nightsiege.game.TrapService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Scene "bossfeind" (Paket 11.11., boss sim: frost jarl never under 97 %, storm witch never under 57 %):
 * the frost jarl (an iron golem) counts as a foe, so a tower's arrow strikes him instead of flying
 * through; the storm witch drinks no healing potion when hurt. Prints "[bossfeind] OK/FAIL ...".
 */
final class BossFeindScenes {
    private static final List<String> FAILS = new ArrayList<>();
    private static int checks;

    private BossFeindScenes() {
    }

    private static void check(boolean ok, String what) {
        checks++;
        System.out.println("[bossfeind] " + (ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 220;
        int cz = pos.getZ() + 220;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(cx - 32, cz - 32, cx + 32, cz + 32));
        context.waitTicks(40);
        final int[] ground = new int[1];
        final int[] prep = new int[1];
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            ground[0] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            prep[0] = manager.config().testPrepSeconds;
            manager.config().testPrepSeconds = 1;
        });
        context.waitTicks(5);
        int cy = ground[0];
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        for (BossKind kind : new BossKind[] {BossKind.FROST_JARL, BossKind.STORM_WITCH, BossKind.BONE_LICH}) {
            game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(cx, cy, cz));
            context.waitTicks(5);
            EventLookTest.onServer(context, (player, manager) -> {
                ServerLevel level = (ServerLevel) player.level();
                manager.prepareTest(level.getServer(), level, new BlockPos(cx, cy, cz), 8, SiegeEvent.NONE, kind);
            });
            context.waitTicks(40);
            EventLookTest.closeScreen(context);
            EventLookTest.onServer(context, (player, manager) -> manager.bossNow(player.level().getServer()));
            context.waitTicks(10);
            final float[] half = new float[1];
            EventLookTest.onServer(context, (player, manager) -> {
                ServerLevel level = (ServerLevel) player.level();
                Mob boss = manager.bosses().boss(level);
                if (kind == BossKind.FROST_JARL) {
                    check(boss != null && BossService.isFoe(boss), "frost_jarl ist ein Feind");
                    Arrow arrow = new Arrow(level, cx, cy + 3, cz, new ItemStack(Items.ARROW), null);
                    arrow.addTag("nightsiege_turret");
                    check(boss != null && !TrapService.sparedByTowerArrow(arrow, boss), "Turmpfeil trifft frost_jarl");
                    arrow.discard();
                } else if (kind == BossKind.STORM_WITCH && boss != null) {
                    half[0] = boss.getMaxHealth() * 0.5f;
                    boss.setHealth(half[0]);
                }
            });
            if (kind == BossKind.STORM_WITCH) {
                // Vanilla: 5 % a tick she starts a healing potion while hurt, 32 ticks later +4 life.
                context.waitTicks(200);
                EventLookTest.onServer(context, (player, manager) -> {
                    Mob boss = manager.bosses().boss((ServerLevel) player.level());
                    check(boss instanceof Witch && half[0] > 0 && boss.getHealth() <= half[0] + 0.01f,
                            "storm_witch heilt sich nicht (Leben " + (boss == null ? "-" : boss.getHealth()) + " / " + half[0] + ")");
                });
                // 12.11.: a defender's hit on her phantom goes to her (the invulnerable phantom swallowed every arrow).
                EventLookTest.onServer(context, (player, manager) -> {
                    ServerLevel level = (ServerLevel) player.level();
                    Mob boss = manager.bosses().boss(level);
                    if (boss != null && boss.getVehicle() instanceof net.minecraft.world.entity.LivingEntity phantom) {
                        float before = boss.getHealth();
                        boss.invulnerableTime = 0;
                        phantom.hurtServer(level, level.damageSources().playerAttack(player), 6.0f);
                        check(boss.getHealth() < before && phantom.isAlive() && phantom.getHealth() >= phantom.getMaxHealth(),
                                "Treffer aufs Phantom trifft storm_witch (" + before + " -> " + boss.getHealth() + ")");
                        float after = boss.getHealth();
                        boss.invulnerableTime = 0;
                        phantom.hurtServer(level, level.damageSources().mobAttack(boss), 6.0f);
                        check(boss.getHealth() >= after, "Monster-Treffer aufs Phantom trifft storm_witch nicht");
                    } else {
                        System.out.println("[bossfeind] Hinweis: storm_witch gerade gelandet, Phantom-Pruefung entfaellt");
                    }
                });
            }
            if (kind == BossKind.BONE_LICH) {
                // 12.11. (boss sim: lich 4-7 %): with the crystals broken he sank outside the walls, out of a sword's
                // reach. Now he drifts in towards the core (crystal ring, edge - 3) and lies on the ground there.
                final double[] before = {-1};
                EventLookTest.onServer(context, (player, manager) -> {
                    ServerLevel level = (ServerLevel) player.level();
                    Mob boss = manager.bosses().boss(level);
                    if (boss != null) {
                        before[0] = Math.hypot(boss.getX() - (cx + 0.5), boss.getZ() - (cz + 0.5));
                    }
                    for (java.util.UUID part : manager.bosses().partHitboxes()) {
                        if (level.getEntity(part) instanceof net.minecraft.world.entity.LivingEntity crystal) {
                            crystal.invulnerableTime = 0;
                            crystal.hurtServer(level, level.damageSources().playerAttack(player), 5000f);
                        }
                    }
                });
                context.waitTicks(160);
                EventLookTest.onServer(context, (player, manager) -> {
                    ServerLevel level = (ServerLevel) player.level();
                    Mob boss = manager.bosses().boss(level);
                    if (boss == null) {
                        check(false, "bone_lich fehlt");
                        return;
                    }
                    double after = Math.hypot(boss.getX() - (cx + 0.5), boss.getZ() - (cz + 0.5));
                    double above = boss.getY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, boss.getBlockX(), boss.getBlockZ());
                    check(manager.bosses().testLichExhausted(level.getGameTime()) && before[0] > 0 && after <= before[0] - 3.0 && above <= 1.5,
                            String.format(java.util.Locale.ROOT, "bone_lich liegt erschoepft innen am Boden (Abstand %.1f -> %.1f, Hoehe %.1f)",
                                    before[0], after, above));
                });
            }
            EventLookTest.onServer(context, (player, manager) -> {
                if (manager.isActive()) {
                    manager.abort(player.level().getServer());
                }
            });
            context.waitTicks(40);
        }
        EventLookTest.onServer(context, (player, manager) -> manager.config().testPrepSeconds = prep[0]);
        System.out.println("[bossfeind] summary: " + checks + " checks, " + FAILS.size() + " failed"
                + (FAILS.isEmpty() ? "" : ": " + FAILS));
        if (checks == 0) {
            System.out.println("[bossfeind] FAIL keine Pruefung");
        }
    }
}
