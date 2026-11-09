package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.core.WarriorSpiritMath;
import com.mki.nightsiege.game.Combo;
import com.mki.nightsiege.game.CoreRecord;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.WarriorArts;
import com.mki.nightsiege.game.WarriorSpirit;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Tor 4.10 (Krieger), scene "krieger410": Blutrausch grows with the life missing (aura, red vignette), the axe
 * flies along the crosshair without a drop and hits 3 at most (aim mark), Klingensturm's turns 2 and 3 still
 * hit, the blademaster's Kombo hits push only 0.3 as far, the swing ring at the crosshair. Played in creative
 * like "krieger", against zombies. Log lines start with "[krieger410]"; FAIL with 0 checks or 0 pictures.
 */
final class Krieger410Scenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static int shots;
    private static final String TAG = "krieger410_test";
    private static final double[] HOME = new double[3];

    private Krieger410Scenes() {
    }

    private static void log(String line) {
        System.out.println("[krieger410] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.takeScreenshot(name);
        shots++;
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

    /** A zombie at home + (dx, dy, dz); {@code ai} false: frozen and floating, true: it stands still but can be pushed. */
    private static Mob zombie(ServerLevel level, double dx, double dy, double dz, float hp, boolean ai) {
        Mob mob = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            throw new IllegalStateException("no zombie");
        }
        if (mob instanceof Zombie z) {
            z.setBaby(false);
        }
        mob.setPos(HOME[0] + dx, HOME[1] + dy, HOME[2] + dz);
        mob.setPersistenceRequired();
        mob.setNoAi(!ai);
        mob.setNoGravity(!ai);
        mob.addTag(TAG);
        AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(hp);
        }
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.0);
        }
        mob.setHealth(hp);
        level.addFreshEntity(mob);
        return mob;
    }

    private static void clear(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AABB box = new AABB(HOME[0] - 24, HOME[1] - 8, HOME[2] - 24, HOME[0] + 24, HOME[1] + 10, HOME[2] + 24);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e.entityTags().contains(TAG))) {
            m.discard();
        }
    }

    private static void become(ServerPlayer player, SiegeManager m, ItemStack weapon, String... nodes) {
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        own.unlockClass(SkillClass.WARRIOR);
        own.switchTo(SkillClass.WARRIOR);
        own.levels.clear();
        for (String n : nodes) {
            own.levels.put(n, n.equals("whirlwind") ? 3 : 1);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        home(player);
    }

    private static void home(ServerPlayer player) {
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo((ServerLevel) player.level(), HOME[0], HOME[1], HOME[2], java.util.Set.of(), -90f, 0f, true);
    }

    private static double flat(Vec3 v) {
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        shots = 0;
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(10);
        server(context, "spot", (player, m) -> {
            CoreRecord c = m.mainCore();
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            HOME[0] = c.x + 30.5;
            HOME[1] = c.y;
            HOME[2] = c.z + 0.5;
        });
        context.waitTicks(5);
        int x = (int) Math.floor(HOME[0]);
        int y = (int) Math.floor(HOME[1]);
        int z = (int) Math.floor(HOME[2]);
        game.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x - 8, y, z - 8, x + 18, y + 7, z + 8));
        game.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", x - 8, y - 1, z - 8, x + 18, y - 1, z + 8));
        context.waitTicks(10);
        context.getInput().lookAt(-90.0f, 0.0f);

        // ------------------------------------------------ 1. Blutrausch: grows with the life missing, aura, vignette
        final double[] rage = new double[4];
        server(context, "rage", (player, m) -> {
            player.getInventory().clearContent();
            become(player, m, new ItemStack(Items.IRON_SWORD), "bloodlust");
            WarriorArts.reset();
            WarriorSpirit.reset();
            WarriorSpirit.set(player, 60);
            player.setHealth(player.getMaxHealth());
            m.onCoreAction(player, "ult2");
            long now = player.level().getGameTime();
            rage[0] = WarriorArts.rageBonus(player);
            rage[1] = player.getAttributeValue(Attributes.ATTACK_SPEED);
            log(String.format(Locale.ROOT, "rage full life: frenzied=%s left=%d cd=%d dmg=%.3f speed=%.3f",
                    WarriorArts.frenzied(player), WarriorArts.frenzyLeft(player), WarriorArts.cooldownLeft(player, now), rage[0], rage[1]));
            check(WarriorArts.frenzied(player) && WarriorArts.frenzyLeft(player) >= 155 && WarriorArts.frenzyLeft(player) <= 160,
                    "Blutrausch lasts 8 s (" + WarriorArts.frenzyLeft(player) + " ticks)");
            check(WarriorArts.cooldownLeft(player, now) >= 395, "Blutrausch: 20 s pause (" + WarriorArts.cooldownLeft(player, now) + ")");
            check(Math.abs(rage[0] - 0.25) < 0.01, "Blutrausch at full life: +25 % damage (" + rage[0] + ")");
            player.setHealth(player.getMaxHealth() * 0.3f);
        });
        context.waitTicks(12);
        server(context, "rage low", (player, m) -> {
            rage[2] = WarriorArts.rageBonus(player);
            rage[3] = player.getAttributeValue(Attributes.ATTACK_SPEED);
            log(String.format(Locale.ROOT, "rage 30 %% life: dmg=%.3f speed=%.3f", rage[2], rage[3]));
            check(Math.abs(rage[2] - WarriorSpiritMath.rageDamage(0.7)) < 0.01 && rage[2] > rage[0],
                    "Blutrausch at 30 % life: more damage (" + rage[0] + " -> " + rage[2] + ")");
            check(rage[3] > rage[1] + 0.01, "Blutrausch at 30 % life: faster swings (" + rage[1] + " -> " + rage[3] + ")");
        });
        context.waitTicks(3);
        boolean vignette = context.computeOnClient(client -> com.mki.nightsiege.client.RageFx.active());
        check(vignette, "the berserker's screen shows the red vignette");
        shot(context, "krieger410_blutrausch_vignette");
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(6);
        shot(context, "krieger410_blutrausch_aura");
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        server(context, "rage end", (player, m) -> {
            player.setHealth(player.getMaxHealth());
            WarriorArts.reset();
        });
        context.waitTicks(5);

        // ------------------------------------------------ 2. Klingensturm: turns 2 and 3 still hit
        final Mob[] ring = new Mob[3];
        final float[][] hp = new float[3][4];
        server(context, "storm", (player, m) -> {
            become(player, m, new ItemStack(Items.IRON_SWORD), "w_combo", "whirlwind", "w_bladestorm");
            ServerLevel level = (ServerLevel) player.level();
            ring[0] = zombie(level, 2.5, 0, 0, 1000f, true);
            ring[1] = zombie(level, -1.5, 0, 2.5, 1000f, true);
            ring[2] = zombie(level, -1.5, 0, -2.5, 1000f, true);
        });
        context.waitTicks(10);
        server(context, "storm go", (player, m) -> {
            for (int i = 0; i < 3; i++) {
                hp[i][0] = ring[i].getHealth();
            }
            m.onCoreAction(player, "ult");
            for (int i = 0; i < 3; i++) {
                hp[i][1] = ring[i].getHealth();
            }
        });
        for (int p = 2; p <= 3; p++) {
            final int turn = p;
            context.waitTicks(10);
            server(context, "storm " + p, (player, m) -> {
                for (int i = 0; i < 3; i++) {
                    hp[i][turn] = ring[i].getHealth();
                }
            });
        }
        context.waitTicks(3);
        server(context, "storm check", (player, m) -> {
            int[] hits = new int[4];
            for (int i = 0; i < 3; i++) {
                for (int p = 1; p <= 3; p++) {
                    if (hp[i][p] < hp[i][p - 1] - 0.01f) {
                        hits[p]++;
                    }
                }
                log(String.format(Locale.ROOT, "storm zombie %d: hp %.1f %.1f %.1f %.1f dist %.2f", i, hp[i][0], hp[i][1], hp[i][2], hp[i][3],
                        ring[i].position().distanceTo(player.position())));
            }
            check(hits[1] == 3, "Klingensturm turn 1 hits all 3 (" + hits[1] + ")");
            check(hits[2] >= 2 && hits[3] >= 2, "Klingensturm turns 2 and 3 still hit (" + hits[2] + ", " + hits[3] + " of 3)");
            clear(player);
        });
        context.waitTicks(5);

        // ------------------------------------------------ 3. Kombo: the blademaster's hits push only 0.3 as far
        final Mob[] dummy = new Mob[1];
        final double[] push = new double[2];
        server(context, "knock plain", (player, m) -> {
            become(player, m, new ItemStack(Items.IRON_SWORD), "bloodlust");
            dummy[0] = zombie((ServerLevel) player.level(), 1.6, 0, 0, 1000f, true);
        });
        context.waitTicks(20);
        server(context, "knock plain hit", (player, m) -> {
            dummy[0].setDeltaMovement(Vec3.ZERO);
            dummy[0].invulnerableTime = 0;
            player.attack(dummy[0]);
            push[0] = flat(dummy[0].getDeltaMovement());
            clear(player);
        });
        context.waitTicks(5);
        server(context, "knock combo", (player, m) -> {
            become(player, m, new ItemStack(Items.IRON_SWORD), "w_combo");
            dummy[0] = zombie((ServerLevel) player.level(), 1.6, 0, 0, 1000f, true);
        });
        context.waitTicks(20);
        server(context, "knock combo hit", (player, m) -> {
            dummy[0].setDeltaMovement(Vec3.ZERO);
            dummy[0].invulnerableTime = 0;
            boolean combo = Combo.active(player);
            player.attack(dummy[0]);
            push[1] = flat(dummy[0].getDeltaMovement());
            log(String.format(Locale.ROOT, "knockback: plain %.3f, Kombo %.3f (combo=%s)", push[0], push[1], combo));
            check(combo && push[0] > 0.05 && push[1] < push[0] * 0.5,
                    "Kombo hits push less (" + String.format(Locale.ROOT, "%.3f -> %.3f", push[0], push[1]) + ")");
            clear(player);
        });
        context.waitTicks(5);

        // ------------------------------------------------ 4. the axe fighter with his kit: ring, aim mark, the throw
        final Mob[] high = new Mob[1];
        final float[] highHp = new float[1];
        server(context, "axe", (player, m) -> {
            become(player, m, new ItemStack(Items.IRON_AXE), "heavy_blow");
            WarriorArts.reset();
            m.kits().equip(player, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            home(player);
            WarriorSpirit.set(player, 60);
            // 12 blocks out and 3 up, floating: only a throw along the crosshair (no drop, not flat) reaches it.
            high[0] = zombie((ServerLevel) player.level(), 12, 3, 0, 500f, false);
            highHp[0] = high[0].getHealth();
        });
        // Eye 1.62 above the floor, the zombie's middle 3 + 0.975: look up at it.
        float pitch = (float) -Math.toDegrees(Math.atan2(3 + 0.975 - 1.62, 12.0));
        context.getInput().lookAt(-90.0f, pitch);
        context.waitTicks(30);   // the kit HUD's state (F ready) comes once a second
        // The swing ring: empty right after a swing, full a while later.
        final float[] charge = new float[2];
        boolean shown = context.computeOnClient(client -> {
            client.player.resetAttackStrengthTicker();
            charge[0] = com.mki.nightsiege.client.StrikeRing.charge();
            return com.mki.nightsiege.client.StrikeRing.shown();
        });
        context.waitTicks(4);
        shot(context, "krieger410_ring_laedt");
        context.waitTicks(30);
        charge[1] = context.computeOnClient(client -> com.mki.nightsiege.client.StrikeRing.charge());
        log("ring: shown=" + shown + " charge " + charge[0] + " -> " + charge[1]);
        check(shown && charge[0] < 0.5f && charge[1] >= 0.999f, "the swing ring shows the charge (" + charge[0] + " -> " + charge[1] + ")");
        shot(context, "krieger410_ring_voll");
        String aim = context.computeOnClient(client -> {
            UltState s = com.mki.nightsiege.client.UltHud.state();
            Vec3 mk = com.mki.nightsiege.client.AxeAim.mark();
            return com.mki.nightsiege.client.AxeAim.onMonster() + " mark=" + mk + " sub=" + (s == null ? "?" : s.sub)
                    + " F=" + (s != null && s.hasAb2) + " dim=" + (s != null && s.ab2Dim);
        });
        log("aim: " + aim);
        check(aim.startsWith("true"), "the aim mark sits on the monster at the crosshair (" + aim + ")");
        shot(context, "krieger410_axt_zielmarke");
        server(context, "axe throw", (player, m) -> {
            log("throw look=" + player.getLookAngle());
            m.onCoreAction(player, "ult2");
        });
        context.waitTicks(3);
        shot(context, "krieger410_axt_flug");
        context.waitTicks(30);
        server(context, "axe high check", (player, m) -> {
            log("high zombie: " + highHp[0] + " -> " + high[0].getHealth() + " y=" + high[0].getY());
            check(high[0].getHealth() < highHp[0], "the axe hits the monster at the crosshair 12 blocks out, 3 up (no drop)");
            clear(player);
            WarriorArts.reset();
            WarriorSpirit.set(player, 60);
        });
        context.getInput().lookAt(-90.0f, 0.0f);
        context.waitTicks(5);
        // Durchschlag 2: five in a row, only the first three are hit.
        final Mob[] row = new Mob[5];
        final float[] rowHp = new float[5];
        final int[] before = new int[1];
        server(context, "axe row", (player, m) -> {
            home(player);
            for (int i = 0; i < 5; i++) {
                row[i] = zombie((ServerLevel) player.level(), 3 + 2 * i, 0, 0, 500f, false);
                rowHp[i] = row[i].getHealth();
            }
        });
        context.waitTicks(5);
        server(context, "axe row throw", (player, m) -> {
            before[0] = WarriorArts.throwHits();
            m.onCoreAction(player, "ult2");
        });
        context.waitTicks(40);
        server(context, "axe row check", (player, m) -> {
            int hit = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                boolean h = row[i].getHealth() < rowHp[i];
                hit += h ? 1 : 0;
                sb.append(h ? "x" : "-");
            }
            log("row: " + sb + " hits=" + (WarriorArts.throwHits() - before[0]) + " in flight=" + WarriorArts.axesInFlight());
            check(hit == 3 && sb.toString().equals("xxx--"), "Axtwurf: 3 monsters at most, the nearest (" + sb + ")");
            check(WarriorArts.axesInFlight() == 0, "the axe came back");
        });
        context.waitTicks(5);

        // The end: kit back, nothing learned.
        server(context, "end", (player, m) -> {
            clear(player);
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            WarriorArts.reset();
            WarriorSpirit.reset();
            m.save().skillsOf(player.getUUID().toString()).levels.clear();
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(10);
        game.getServer().runCommand("time set 1500");
        context.waitTicks(5);
        if (checks == 0 || shots == 0) {
            FAILS.add("no checks or no pictures (" + checks + ", " + shots + ")");
        }
        String summary = checks + " checks, " + shots + " pictures, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS);
        log((FAILS.isEmpty() ? "OK " : "FAIL ") + "summary: " + summary);
        System.out.println("[Night Siege test] krieger410 summary: " + summary);
    }
}
