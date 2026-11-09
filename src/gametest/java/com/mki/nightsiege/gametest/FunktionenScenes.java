package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.WarriorSpirit;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tor 3.0d, scene "funktionen": the open proofs of Tor 2 in a real night (survival, night kit).
 * (a) Erdstoß (w_earthshock, paladin): a real jump, the charged hit while falling splashes the zombie
 * beside the target; on the ground, half charged, on a ladder and in water it does not.
 * (b) Köcherwart (a_quiver, bolt shooter, chance set to 100 % for the scene only): a crossbow shot
 * looses 1 + 1 twin, with Multishot 3 + 1 (the twin only for the main bolt).
 * Every check is "[funktionen] OK/FAIL ..."; no check at all counts as FAIL.
 */
final class FunktionenScenes {

    private static final String TAG = "funktionen_test";
    private static final double[] HOME = new double[3];
    private static int checks;
    private static int passed;

    private FunktionenScenes() {
    }

    private static void log(String line) {
        System.out.println("[funktionen] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        passed += ok ? 1 : 0;
        log((ok ? "OK   " : "FAIL ") + what);
    }

    private static void server(ClientGameTestContext context, String step, EventLookTest.OnServer action) {
        EventLookTest.onServer(context, (player, manager) -> {
            try {
                action.run(player, manager);
            } catch (Throwable t) {
                check(false, step + ": " + t);
                t.printStackTrace(System.out);
            }
        });
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        checks = 0;
        passed = 0;
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 300;
        int cz = pos.getZ() - 300;
        int cy = EventLookTest.yard(context, game, cx, cz, 24, 12);
        HOME[0] = cx + 0.5;
        HOME[1] = cy;
        HOME[2] = cz + 0.5;
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 -90 0", cx, cy, cz));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(20);
        final int[] saved = new int[2];
        final String[] oldKey = {""};
        final Map<String, Map<String, Integer>> oldLevels = new LinkedHashMap<>();
        // A test night without waves: the skills count in survival only for a defender.
        server(context, "night", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            oldKey[0] = m.save().skillsOf(player.getUUID().toString()).chosenClass;
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            m.config().testPrepSeconds = 1;
            m.config().batchIntervalSeconds = 600;
            log("night prepared=" + m.prepareTest(level.getServer(), level, new BlockPos(cx + 8, cy, cz + 8), 10,
                    com.mki.nightsiege.core.SiegeEvent.NONE));
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "night runs", (player, m) -> check(m.isActive() && !player.getAbilities().instabuild,
                "test night runs, survival"));

        earthshock(context, oldLevels);
        quiver(context, oldLevels);

        server(context, "end", (player, m) -> {
            m.skillTree().applyTweaks(m.config().skillTweaks);
            clearDummies(player);
            m.abort(player.level().getServer());
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            for (Map.Entry<String, Map<String, Integer>> e : oldLevels.entrySet()) {
                own.switchToProfile(e.getKey());
                own.levels.clear();
                own.levels.putAll(e.getValue());
            }
            if (!oldKey[0].isEmpty()) {
                own.switchToProfile(oldKey[0]);
            }
        });
        context.waitTicks(20);
        game.getServer().runCommand("time set 1000");
        if (checks == 0) {
            check(false, "no check ran");
        }
        log(passed + " von " + checks);
    }

    /** The player as this class with exactly these nodes, the night kit for it on. */
    private static void become(ServerPlayer player, SiegeManager m, SkillClass c,
                               Map<String, Map<String, Integer>> oldLevels, String... nodes) {
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        own.unlockClass(c);
        own.switchTo(c);
        oldLevels.putIfAbsent(own.chosenClass, new HashMap<>(own.levels));
        own.levels.clear();
        for (String n : nodes) {
            own.levels.put(n, 1);
        }
        m.kits().equip(player, 10);
    }

    /** The first hotbar slot whose item id ends like this, or -1. */
    private static int slotOf(ServerPlayer player, String end) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith(end)) {
                return i;
            }
        }
        return -1;
    }

    private static Mob dummy(ServerLevel level, double x, double y, double z) {
        Mob mob = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            throw new IllegalStateException("no zombie");
        }
        mob.setPos(x, y, z);
        mob.setPersistenceRequired();
        mob.setNoAi(true);
        mob.addTag(TAG);
        AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(500.0);
        }
        mob.setHealth(500f);
        level.addFreshEntity(mob);
        return mob;
    }

    private static void clearDummies(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AABB box = new AABB(HOME[0] - 24, HOME[1] - 8, HOME[2] - 24, HOME[0] + 24, HOME[1] + 12, HOME[2] + 24);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, e -> e.entityTags().contains(TAG))) {
            mob.discard();
        }
    }

    // ------------------------------------------------------------------ (a) Erdstoß

    /** One hit of a case: the target, the zombie beside it, what was seen at the hit. */
    private static final class Hit {
        Mob target;
        Mob side;
        boolean done;
        boolean setupOk;
        float charge;
        float splash;
        float absorb;   // Tor 9b (P1): absorption the hit gave the paladin
        String state = "";
    }

    private static void earthshock(ClientGameTestContext context, Map<String, Map<String, Integer>> oldLevels) {
        final int[] axe = {-1};
        server(context, "paladin", (player, m) -> {
            become(player, m, SkillClass.WARRIOR, oldLevels, "smite", "w_earthshock");
            axe[0] = slotOf(player, "_axe");
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            log("paladin sub=" + m.skillTree().subclassOf(own) + " axe slot=" + axe[0]);
        });
        context.waitTicks(3);
        check(axe[0] >= 0, "paladin kit has an axe");
        if (axe[0] < 0) {
            return;
        }
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(axe[0]));
        KeyMapping jump = context.computeOnClient(client -> client.options.keyJump);

        // 1: real jump, full charge, hit while falling -> splash
        Hit a = jumpHit(context, jump, "jump full", false);
        check(a.done && a.setupOk && a.splash > 0.01f, String.format(Locale.ROOT,
                "Erdstoss on a real jump crit: splash %.2f (charge %.2f, %s)", a.splash, a.charge, a.state));
        // Tor 9b (P1): one golden heart per monster struck (target + side = 2 hearts), at most 4
        check(a.done && a.absorb > 3.99f && a.absorb < 8.01f, String.format(Locale.ROOT,
                "paladin jump hit gives golden hearts: +%.2f absorption (2 monsters, expected 4)", a.absorb));
        // 2: on the ground, full charge -> nothing
        Hit g = new Hit();
        place(context, g, 0);
        server(context, "ground settle", (player, m) -> {
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.resetFallDistance();
        });
        context.waitTicks(45);
        // CI 08.11.: the player was still falling from the jump before; hit only once he stands.
        for (int t = 0; t < 40 && !g.done; t++) {
            server(context, "ground hit", (player, m) -> {
                if (!g.done && player.onGround() && player.fallDistance == 0) {
                    strike(player, g, true);
                }
            });
            context.waitTicks(1);
        }
        context.waitTicks(2);
        if (!g.done) {
            log("ground: setup, the player never stood still on the ground");
        }
        check(g.done && g.setupOk && g.splash < 0.01f, String.format(Locale.ROOT,
                "no Erdstoss on the ground: splash %.2f (charge %.2f, %s)%s", g.splash, g.charge, g.state,
                g.done ? "" : " setup: never on the ground"));
        check(g.done && g.absorb < 0.01f, String.format(Locale.ROOT,
                "no golden hearts from a hit on the ground: +%.2f", g.absorb));
        // 3: real jump, half charged -> nothing
        Hit h = jumpHit(context, jump, "jump half", true);
        check(h.done && h.setupOk && h.charge < 0.9f && h.splash < 0.01f, String.format(Locale.ROOT,
                "no Erdstoss half charged: splash %.2f (charge %.2f, %s)", h.splash, h.charge, h.state));
        // 4: on a ladder, in the air, falling, full charge -> nothing
        Hit l = special(context, "ladder");
        check(l.done && l.setupOk && l.splash < 0.01f, String.format(Locale.ROOT,
                "no Erdstoss on a ladder: splash %.2f (charge %.2f, %s)", l.splash, l.charge, l.state));
        // 5: in water, off the ground, falling, full charge -> nothing
        Hit w = special(context, "water");
        check(w.done && w.setupOk && w.splash < 0.01f, String.format(Locale.ROOT,
                "no Erdstoss in water: splash %.2f (charge %.2f, %s)", w.splash, w.charge, w.state));
        server(context, "clear a", (player, m) -> clearDummies(player));
        context.waitTicks(2);
    }

    /** Home, fresh target 2 east and a zombie beside it ({@code dz} moves both). */
    private static void place(ClientGameTestContext context, Hit hit, int dz) {
        server(context, "place", (player, m) -> {
            clearDummies(player);
            WarriorSpirit.reset(player);
            player.teleportTo((ServerLevel) player.level(), HOME[0], HOME[1], HOME[2] + dz, java.util.Set.of(), -90f, 0f, true);
            hit.target = dummy((ServerLevel) player.level(), HOME[0] + 2, HOME[1], HOME[2] + dz);
            hit.side = dummy((ServerLevel) player.level(), HOME[0] + 2, HOME[1], HOME[2] + dz + 1.5);
        });
        context.waitTicks(2);
    }

    /** The hit itself, on the server: the charge, the state and what the side zombie lost. */
    private static void strike(ServerPlayer player, Hit hit, boolean setupOk) {
        hit.charge = player.getAttackStrengthScale(0.5f);
        hit.state = String.format(Locale.ROOT, "ground=%s fall=%.2f ladder=%s water=%s",
                player.onGround(), player.fallDistance, player.onClimbable(), player.isInWater());
        hit.setupOk = setupOk;
        float before = hit.side.getHealth();
        hit.target.invulnerableTime = 0;
        float absorbBefore = player.getAbsorptionAmount();
        player.attack(hit.target);
        hit.splash = before - hit.side.getHealth();
        hit.absorb = player.getAbsorptionAmount() - absorbBefore;
        hit.done = true;
        log("hit " + hit.state + String.format(Locale.ROOT, " charge=%.2f splash=%.2f", hit.charge, hit.splash));
    }

    /** A real jump (the jump key), the hit the first tick the player falls. */
    private static Hit jumpHit(ClientGameTestContext context, KeyMapping jump, String what, boolean half) {
        Hit hit = new Hit();
        place(context, hit, 0);
        context.waitTicks(45);   // full charge, the chain of the last hit is over
        if (half) {
            server(context, what + " reset", (player, m) -> player.resetAttackStrengthTicker());
        }
        context.getInput().holdKey(jump);
        for (int t = 0; t < 20 && !hit.done; t++) {
            context.waitTicks(1);
            if (t == 2) {
                context.getInput().releaseKey(jump);
            }
            server(context, what, (player, m) -> {
                if (!hit.done && !player.onGround() && player.fallDistance > 0 && player.getDeltaMovement().y <= 0.05) {
                    strike(player, hit, half ? player.getAttackStrengthScale(0.5f) < 0.9f : player.getAttackStrengthScale(0.5f) > 0.9f);
                }
            });
        }
        context.getInput().releaseKey(jump);
        context.waitTicks(20);
        if (!hit.done) {
            log(what + ": the player never fell");
        }
        return hit;
    }

    /** Ladder or water at the player, then a charged hit in the air with a fall set. */
    private static Hit special(ClientGameTestContext context, String kind) {
        Hit hit = new Hit();
        int dz = 6;
        place(context, hit, dz);
        server(context, kind + " build", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            int x = (int) Math.floor(HOME[0]);
            int y = (int) Math.floor(HOME[1]);
            int z = (int) Math.floor(HOME[2]) + dz;
            for (int dy = 0; dy < 4; dy++) {
                if (kind.equals("ladder")) {
                    level.setBlock(new BlockPos(x, y + dy, z - 1), Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(x, y + dy, z),
                            Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH), 3);
                } else {
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        level.setBlock(new BlockPos(x, y + dy, z).relative(d), Blocks.GLASS.defaultBlockState(), 3);
                    }
                    level.setBlock(new BlockPos(x, y + dy, z), Blocks.WATER.defaultBlockState(), 3);
                }
            }
        });
        context.waitTicks(45);
        server(context, kind + " up", (player, m) -> player.teleportTo((ServerLevel) player.level(),
                HOME[0], HOME[1] + 1.6, HOME[2] + dz, java.util.Set.of(), -90f, 0f, true));
        context.waitTicks(2);
        server(context, kind + " hit", (player, m) -> {
            player.fallDistance = 1.0;   // falling, as after a jump: only the ladder or the water may stop it
            boolean there = kind.equals("ladder") ? player.onClimbable() : player.isInWater();
            strike(player, hit, there && !player.onGround() && player.getAttackStrengthScale(0.5f) > 0.9f);
        });
        context.waitTicks(2);
        server(context, kind + " clear", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            int x = (int) Math.floor(HOME[0]);
            int y = (int) Math.floor(HOME[1]);
            int z = (int) Math.floor(HOME[2]) + dz;
            for (BlockPos p : BlockPos.betweenClosed(x - 1, y, z - 1, x + 1, y + 3, z + 1)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            player.teleportTo(level, HOME[0], HOME[1], HOME[2], java.util.Set.of(), -90f, 0f, true);
        });
        context.waitTicks(5);
        return hit;
    }

    // ------------------------------------------------------------------ (b) Köcherwart

    private static void quiver(ClientGameTestContext context, Map<String, Map<String, Integer>> oldLevels) {
        final int[] xbow = {-1};
        server(context, "bolter", (player, m) -> {
            clearDummies(player);
            become(player, m, SkillClass.ARCHER, oldLevels, "heavy_bolts", "shrapnel", "a_quiver");
            // The quiver's chance at 100 % for this scene only (a skill tweak, the config stays as it is).
            Map<String, SiegeConfig.SkillTweak> tweaks = new HashMap<>();
            if (m.config().skillTweaks != null) {
                tweaks.putAll(m.config().skillTweaks);
            }
            SiegeConfig.SkillTweak sure = new SiegeConfig.SkillTweak();
            sure.amount = 1.0;
            tweaks.put("a_quiver", sure);
            m.skillTree().applyTweaks(tweaks);
            xbow[0] = slotOf(player, "crossbow");
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            log("bolter sub=" + m.skillTree().subclassOf(own) + " crossbow slot=" + xbow[0]
                    + " quiver chance=" + m.skillTree().totalBonus(com.mki.nightsiege.core.SkillEffect.QUIVER, own));
            player.teleportTo((ServerLevel) player.level(), HOME[0], HOME[1], HOME[2], java.util.Set.of(), -90f, 0f, true);
        });
        context.waitTicks(3);
        check(xbow[0] >= 0, "bolt shooter kit has a crossbow");
        if (xbow[0] < 0) {
            return;
        }
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(xbow[0]));
        context.getInput().lookAt(-90.0f, -5.0f);
        context.waitTicks(3);
        int single = shot(context, "plain");
        check(single == 2, "Koecherwart without Multishot: " + single + " bolts (expected 1 + 1 twin)");
        server(context, "multishot", (player, m) -> {
            ItemStack stack = player.getInventory().getItem(xbow[0]);
            player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.MULTISHOT)
                    .ifPresent(h -> stack.enchant(h, 1));
            log("crossbow " + stack.getEnchantments());
        });
        context.waitTicks(3);
        int multi = shot(context, "multishot");
        check(multi == 4, "Koecherwart with Multishot: " + multi + " bolts (expected 3 + 1 twin, not 3 + 3)");
    }

    /** Loads the crossbow (use held), fires it (use pressed): how many of the player's arrows fly then. */
    private static int shot(ClientGameTestContext context, String what) {
        server(context, what + " clear", (player, m) -> arrows(player, true));
        KeyMapping use = context.computeOnClient(client -> client.options.keyUse);
        context.getInput().holdKey(use);
        context.waitTicks(40);
        context.getInput().releaseKey(use);
        context.waitTicks(5);
        final int[] before = {-1};
        final boolean[] loaded = {false};
        server(context, what + " loaded", (player, m) -> {
            before[0] = arrows(player, false);
            loaded[0] = CrossbowItem.isCharged(player.getMainHandItem());
        });
        context.waitTicks(1);
        context.getInput().pressKey(use);
        context.waitTicks(3);
        final int[] after = {-1};
        server(context, what + " count", (player, m) -> after[0] = arrows(player, false));
        context.waitTicks(1);
        log(what + ": loaded=" + loaded[0] + " arrows before=" + before[0] + " after=" + after[0]);
        return loaded[0] && before[0] == 0 ? after[0] : -1;
    }

    /** The player's arrows around the spot (and away with them when {@code clear}). */
    private static int arrows(ServerPlayer player, boolean clear) {
        AABB box = new AABB(HOME[0] - 80, HOME[1] - 20, HOME[2] - 80, HOME[0] + 80, HOME[1] + 40, HOME[2] + 80);
        int n = 0;
        for (AbstractArrow a : player.level().getEntitiesOfClass(AbstractArrow.class, box, a -> a.getOwner() == player)) {
            n++;
            if (clear) {
                a.discard();
            }
        }
        return n;
    }
}
