package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.CoreRecord;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.WarriorArts;
import com.mki.nightsiege.game.WarriorSpirit;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The warrior rework (Simon 05.11.), scene "krieger": Kampfgeist fills with hits, kills and hits taken
 * and falls after 5 s without a fight; every 4th hit in a row is the subclass's finisher (empowered at a
 * full meter), a chain breaks after 2 s; each of the four F abilities does its thing; F needs 50
 * Kampfgeist and a fight; the meter starts at 0 with the kit and every night; the kit HUD shows the bar,
 * the lit F field and the dots. Played in creative (skills hold there) on a stone floor away from the
 * core, against zombies without AI. Log lines start with "[krieger]".
 */
final class KriegerScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static final String TAG = "krieger_test";
    /** The target's Slowness level right after the 4th hit of {@link #fourHits} (-1 none). */
    private static int lastSlow = -1;
    /** The spot of the scene: x, y, z. */
    private static final double[] HOME = new double[3];

    private KriegerScenes() {
    }

    private static void log(String line) {
        System.out.println("[krieger] " + line);
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

    /** A zombie without AI with this much life, tagged for the clean-up. */
    private static Mob dummy(ServerLevel level, double dx, double dz, float hp) {
        Mob mob = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            throw new IllegalStateException("no zombie");
        }
        if (mob instanceof Zombie z) {
            z.setBaby(false);
        }
        mob.setPos(HOME[0] + dx, HOME[1], HOME[2] + dz);
        mob.setPersistenceRequired();
        mob.setNoAi(true);
        mob.addTag(TAG);
        AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(hp);
        }
        mob.setHealth(hp);
        level.addFreshEntity(mob);
        return mob;
    }

    private static void clearDummies(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AABB box = new AABB(HOME[0] - 24, HOME[1] - 8, HOME[2] - 24, HOME[0] + 24, HOME[1] + 8, HOME[2] + 24);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e.entityTags().contains(TAG))) {
            m.discard();
        }
    }

    /** The player as a warrior of this subclass (its node) with this weapon, at home, looking east. */
    private static void become(ServerPlayer player, SiegeManager m, String subNode, ItemStack weapon) {
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        own.unlockClass(SkillClass.WARRIOR);
        own.switchTo(SkillClass.WARRIOR);
        own.levels.clear();
        own.levels.put(subNode, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        home(player);
    }

    /** A stone wall {@code dx} east of home, 3 high, from z-3 to z+3 (or air again). */
    private static void wall(ServerPlayer player, int dx, boolean stone) {
        ServerLevel level = (ServerLevel) player.level();
        int x = (int) Math.floor(HOME[0]) + dx;
        int y = (int) Math.floor(HOME[1]);
        int z = (int) Math.floor(HOME[2]);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dy = 0; dy < 3; dy++) {
                level.setBlock(new net.minecraft.core.BlockPos(x, y + dy, z + dz),
                        (stone ? net.minecraft.world.level.block.Blocks.STONE : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(), 3);
            }
        }
    }

    private static void home(ServerPlayer player) {
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo((ServerLevel) player.level(), HOME[0], HOME[1], HOME[2], java.util.Set.of(), -90f, 0f, true);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    /** Four charged hits on {@code target}, {@code gap} ticks apart; the life of {@code side} before and after the 4th. */
    private static void fourHits(ClientGameTestContext context, String what, Mob[] target, Mob[] side, int gap,
                                 float[] sideHp, int[] finBefore) {
        for (int i = 1; i <= 4; i++) {
            final int n = i;
            server(context, what + " hit " + n, (player, m) -> {
                if (n == 4) {
                    finBefore[0] = WarriorSpirit.finishers();
                    sideHp[0] = side[0] == null ? 0f : side[0].getHealth();
                }
                target[0].invulnerableTime = 0;
                player.attack(target[0]);
                if (n == 4) {
                    sideHp[1] = side[0] == null ? 0f : side[0].getHealth();
                    MobEffectInstance slow = target[0].getEffect(MobEffects.SLOWNESS);
                    lastSlow = slow == null ? -1 : slow.getAmplifier();
                }
                log(String.format(Locale.ROOT, "%s hit %d: spirit=%.1f chain=%d finishers=%d last=%s emp=%s target=%.1f",
                        what, n, WarriorSpirit.value(player), WarriorSpirit.chain(player), WarriorSpirit.finishers(),
                        WarriorSpirit.lastFinisher(), WarriorSpirit.lastEmpowered(), target[0].getHealth()));
            });
            context.waitTicks(gap);
        }
    }

    // ------------------------------------------------------------------ the scene

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 18000");   // night: the zombies do not burn in the sun
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
            log("spot " + HOME[0] + " " + HOME[1] + " " + HOME[2] + " mode=" + player.gameMode());
        });
        context.waitTicks(5);
        int x = (int) Math.floor(HOME[0]);
        int y = (int) Math.floor(HOME[1]);
        int z = (int) Math.floor(HOME[2]);
        game.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x - 10, y, z - 10, x + 10, y + 5, z + 10));
        game.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", x - 10, y - 1, z - 10, x + 10, y - 1, z + 10));
        context.waitTicks(10);
        context.getInput().lookAt(-90.0f, 0.0f);
        server(context, "setup", (player, m) -> {
            player.getInventory().clearContent();
            become(player, m, "w_combo", new ItemStack(Items.IRON_SWORD));
            WarriorArts.reset();
            WarriorSpirit.reset();
            WarriorSpirit.set(player, 0);
            player.setHealth(player.getMaxHealth());
            log("blademaster: sub=" + m.skillTree().subclassOf(m.save().skillsOf(player.getUUID().toString()))
                    + " has F=" + WarriorArts.has(m.save().skillsOf(player.getUUID().toString()), m.skillTree()));
        });
        context.waitTicks(20);

        // ------------------------------------------------ 1. the meter fills, the 4th hit is the finisher
        final Mob[] target = new Mob[1];
        final Mob[] side = new Mob[1];
        server(context, "dummies", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            target[0] = dummy(level, 1.6, 0, 400f);
            side[0] = dummy(level, -1.6, 0, 400f);   // behind the warrior: only the whirl reaches it
        });
        context.waitTicks(20);
        final float[] sideHp = new float[2];
        final int[] finBefore = new int[1];
        final float[] whirlNormal = new float[1];
        for (int i = 1; i <= 4; i++) {
            final int n = i;
            server(context, "hit " + n, (player, m) -> {
                float sideBefore = side[0].getHealth();
                int fin = WarriorSpirit.finishers();
                target[0].invulnerableTime = 0;
                player.attack(target[0]);
                double v = WarriorSpirit.value(player);
                int chain = WarriorSpirit.chain(player);
                log(String.format(Locale.ROOT, "hit %d: spirit=%.1f chain=%d finishers=%d last=%s target=%.1f side=%.1f",
                        n, v, chain, WarriorSpirit.finishers(), WarriorSpirit.lastFinisher(), target[0].getHealth(), side[0].getHealth()));
                check(near(v, 4.0 * n), "hit " + n + ": Kampfgeist " + (4 * n) + " (is " + v + ")");
                check(chain == n, "hit " + n + ": chain " + n + " (is " + chain + ")");
                if (n < 4) {
                    check(WarriorSpirit.finishers() == fin && side[0].getHealth() == sideBefore, "hit " + n + ": no finisher yet");
                } else {
                    check(WarriorSpirit.finishers() == fin + 1 && "klingenwirbel".equals(WarriorSpirit.lastFinisher())
                            && !WarriorSpirit.lastEmpowered(), "hit 4: finisher Klingenwirbel, not empowered");
                    check(side[0].getHealth() < sideBefore, "Klingenwirbel hits the monster behind the warrior ("
                            + sideBefore + " -> " + side[0].getHealth() + ")");
                    whirlNormal[0] = sideBefore - side[0].getHealth();
                }
            });
            context.waitTicks(16);
        }
        context.runOnClient(client -> log("client: spirit=" + com.mki.nightsiege.client.WarriorHud.value()
                + " finishers=" + com.mki.nightsiege.client.WarriorHud.finishers()));

        // ------------------------------------------------ 2. the chain breaks after 2 s
        server(context, "break a", (player, m) -> {
            target[0].invulnerableTime = 0;
            player.attack(target[0]);
            check(WarriorSpirit.chain(player) == 1, "after the finisher a new chain starts at 1");
        });
        context.waitTicks(50);
        server(context, "break b", (player, m) -> {
            check(WarriorSpirit.chain(player) == 0, "2.5 s without a hit: the chain broke");
            int fin = WarriorSpirit.finishers();
            target[0].invulnerableTime = 0;
            player.attack(target[0]);
            check(WarriorSpirit.chain(player) == 1 && WarriorSpirit.finishers() == fin, "the next hit starts a new chain");
        });
        context.waitTicks(3);
        // An uncharged click right after a hit counts for nothing.
        server(context, "spam", (player, m) -> {
            double v = WarriorSpirit.value(player);
            target[0].invulnerableTime = 0;
            player.attack(target[0]);
            check(near(WarriorSpirit.value(player), v) && WarriorSpirit.chain(player) == 1,
                    "an uncharged click fills nothing (" + v + " -> " + WarriorSpirit.value(player) + ")");
        });
        context.waitTicks(16);
        // Simon 05.11.: the blademaster clicks fast (his combo, no cooldown); his clicks fill the chain at
        // its rhythm (one counted swing every 5 ticks at most), not only fully charged ones.
        final double[] fast = new double[2];
        final int[] fastFin = new int[1];
        for (int i = 0; i < 10; i++) {
            final int n = i;
            server(context, "fast " + n, (player, m) -> {
                if (n == 0) {
                    fast[0] = WarriorSpirit.value(player);
                    fastFin[0] = WarriorSpirit.finishers();
                    log("fast: combo active=" + com.mki.nightsiege.game.Combo.active(player)
                            + " charge=" + player.getAttackStrengthScale(0.5f));
                }
                target[0].invulnerableTime = 0;
                player.attack(target[0]);
                if (n == 9) {
                    fast[1] = WarriorSpirit.value(player);
                    double counted = (fast[1] - fast[0]) / 4.0;
                    log(String.format(Locale.ROOT, "fast: spirit %.1f -> %.1f (counted %.1f) chain=%d finishers +%d",
                            fast[0], fast[1], counted, WarriorSpirit.chain(player), WarriorSpirit.finishers() - fastFin[0]));
                    check(counted >= 3 - 1.0e-6 && counted <= 4 + 1.0e-6,
                            "blademaster: 10 fast clicks in 1 s count 3-4 swings (counted " + counted + ")");
                }
            });
            context.waitTicks(2);
        }
        context.waitTicks(16);

        // ------------------------------------------------ 3. a kill and a hit taken
        server(context, "kill", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            Mob weak = dummy(level, 1.4, 1.4, 1f);
            double v = WarriorSpirit.value(player);
            player.attack(weak);
            double gained = WarriorSpirit.value(player) - v;
            check(!weak.isAlive() && near(gained, 14.0), "a killing hit: +4 for the hit, +10 for the kill (gained " + gained + ")");
        });
        context.waitTicks(5);
        server(context, "hurt", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            double v = WarriorSpirit.value(player);
            player.getAbilities().invulnerable = false;
            player.invulnerableTime = 0;
            boolean hit = player.hurtServer(level, level.damageSources().mobAttack(side[0]), 1f);
            player.getAbilities().invulnerable = true;
            player.setHealth(player.getMaxHealth());
            double gained = WarriorSpirit.value(player) - v;
            check(near(gained, 6.0), "a hit taken from a monster: +6 (hit=" + hit + ", gained " + gained + ")");
        });
        context.waitTicks(5);

        // ------------------------------------------------ 4. it falls after 5 s without a fight
        server(context, "decay set", (player, m) -> WarriorSpirit.set(player, 40));
        context.waitTicks(90);
        server(context, "decay idle", (player, m) -> check(near(WarriorSpirit.value(player), 40.0),
                "4.5 s without a fight: still 40 (is " + WarriorSpirit.value(player) + ")"));
        context.waitTicks(50);
        server(context, "decay falls", (player, m) -> {
            double v = WarriorSpirit.value(player);
            check(v < 37.0 && v > 28.0, "7 s without a fight: about 5 a second less (40 -> " + v + ")");
        });

        // ------------------------------------------------ 5. an empowered finisher at a full meter
        server(context, "full", (player, m) -> {
            target[0].setHealth(400f);
            side[0].setHealth(400f);
            WarriorSpirit.set(player, 100);
        });
        context.waitTicks(16);
        fourHits(context, "full", target, side, 16, sideHp, finBefore);
        server(context, "full check", (player, m) -> {
            double v = WarriorSpirit.value(player);
            float whirl = sideHp[0] - sideHp[1];
            float normalWhirl = whirlNormal[0];
            check(WarriorSpirit.finishers() == finBefore[0] + 1 && WarriorSpirit.lastEmpowered(), "a full meter empowers the finisher");
            check(near(v, 70.0), "the empowered finisher costs 30 (100 -> " + v + ")");
            check(whirl > normalWhirl * 1.5f, "empowered: double effect (whirl " + normalWhirl + " -> " + whirl + ")");
            clearDummies(player);
        });
        context.waitTicks(5);

        // ------------------------------------------------ 6. the other finishers
        // Berserker: Blutiger Hieb, the target bleeds.
        final Mob[] none = new Mob[1];
        server(context, "berserker", (player, m) -> {
            become(player, m, "bloodlust", new ItemStack(Items.IRON_SWORD));
            WarriorSpirit.set(player, 0);
            player.setHealth(player.getMaxHealth() * 0.5f);
            target[0] = dummy((ServerLevel) player.level(), 1.6, 0, 400f);
        });
        context.waitTicks(16);
        fourHits(context, "berserker", target, none, 16, sideHp, finBefore);
        final float[] bleedHp = new float[1];
        server(context, "berserker check", (player, m) -> {
            check("blutiger_hieb".equals(WarriorSpirit.lastFinisher()) && WarriorSpirit.finishers() == finBefore[0] + 1,
                    "berserker: finisher Blutiger Hieb");
            check(WarriorSpirit.bleeding(target[0]), "Blutiger Hieb: the target bleeds");
            bleedHp[0] = target[0].getHealth();
        });
        context.waitTicks(45);
        server(context, "bleed", (player, m) -> {
            check(target[0].getHealth() < bleedHp[0], "the bleeding takes life without a hit (" + bleedHp[0] + " -> " + target[0].getHealth() + ")");
            clearDummies(player);
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(5);
        // Axe fighter: Spalter, broken armour and knocked down.
        server(context, "axe", (player, m) -> {
            become(player, m, "heavy_blow", new ItemStack(Items.IRON_AXE));
            WarriorSpirit.set(player, 0);
            target[0] = dummy((ServerLevel) player.level(), 1.6, 0, 400f);
        });
        context.waitTicks(25);
        fourHits(context, "axe", target, none, 25, sideHp, finBefore);
        server(context, "axe check", (player, m) -> {
            MobEffectInstance slow = target[0].getEffect(MobEffects.SLOWNESS);
            AttributeInstance armour = target[0].getAttribute(Attributes.ARMOR);
            log("axe: armour=" + (armour == null ? -1 : armour.getValue()) + " slow=" + (slow == null ? "-" : slow.getAmplifier() + "/" + slow.getDuration()));
            check("spalter".equals(WarriorSpirit.lastFinisher()) && WarriorSpirit.finishers() == finBefore[0] + 1, "axe fighter: finisher Spalter");
            check(WarriorSpirit.cracked(target[0]), "Spalter: the target's armour is broken");
            check(lastSlow >= 3, "Spalter knocks the target down (Slowness IV right after the hit, is " + lastSlow + ")");
        });
        context.waitTicks(90);
        server(context, "axe armour back", (player, m) -> {
            check(!WarriorSpirit.cracked(target[0]), "after 4 s the armour is back");
            clearDummies(player);
        });
        context.waitTicks(5);
        // Paladin: Lichtschlag heals and sets the undead on fire.
        final double[] healBefore = new double[1];
        server(context, "paladin", (player, m) -> {
            become(player, m, "smite", new ItemStack(Items.IRON_AXE));
            WarriorSpirit.set(player, 0);
            target[0] = dummy((ServerLevel) player.level(), 1.6, 0, 400f);
            target[0].clearFire();
        });
        context.waitTicks(25);
        for (int i = 1; i <= 4; i++) {
            final int n = i;
            server(context, "paladin hit " + n, (player, m) -> {
                if (n == 4) {
                    finBefore[0] = WarriorSpirit.finishers();
                    healBefore[0] = WarriorSpirit.finisherHeal();
                    player.setHealth(10f);
                }
                target[0].invulnerableTime = 0;
                player.attack(target[0]);
            });
            context.waitTicks(25);
        }
        server(context, "paladin check", (player, m) -> {
            double healed = WarriorSpirit.finisherHeal() - healBefore[0];
            check("lichtschlag".equals(WarriorSpirit.lastFinisher()) && WarriorSpirit.finishers() == finBefore[0] + 1,
                    "paladin: finisher Lichtschlag");
            check(healed >= 1.99, "Lichtschlag heals the players around by 2 (healed " + healed + ")");
            check(target[0].isOnFire() || target[0].getRemainingFireTicks() > 0, "Lichtschlag: the undead burn");
            clearDummies(player);
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(5);

        // ------------------------------------------------ 7. the four F abilities
        // Klingentanz: three jumps, one hit each, no damage while dancing.
        final Mob[] three = new Mob[3];
        final float[] hp3 = new float[3];
        final int[] counter = new int[2];
        server(context, "dance", (player, m) -> {
            become(player, m, "w_combo", new ItemStack(Items.IRON_SWORD));
            ServerLevel level = (ServerLevel) player.level();
            three[0] = dummy(level, 3, 2, 200f);
            three[1] = dummy(level, 4, -2, 200f);
            three[2] = dummy(level, -3, 3, 200f);
            WarriorArts.reset();
            WarriorSpirit.set(player, 60);
        });
        context.waitTicks(5);
        server(context, "dance F", (player, m) -> {
            for (int i = 0; i < 3; i++) {
                hp3[i] = three[i].getHealth();
            }
            counter[0] = WarriorArts.used();
            counter[1] = WarriorArts.danceHits();
            m.onCoreAction(player, "ult2");
            check(WarriorArts.used() == counter[0] + 1 && near(WarriorSpirit.value(player), 10.0),
                    "F as blademaster: Klingentanz for 50 Kampfgeist (left " + WarriorSpirit.value(player) + ")");
            check(WarriorArts.dancing(player), "Klingentanz: invulnerable while dancing");
            ServerLevel level = (ServerLevel) player.level();
            player.getAbilities().invulnerable = false;
            float before = player.getHealth();
            player.invulnerableTime = 0;
            player.hurtServer(level, level.damageSources().mobAttack(three[0]), 2f);
            player.getAbilities().invulnerable = true;
            check(player.getHealth() >= before, "no damage while dancing (" + before + " -> " + player.getHealth() + ")");
        });
        context.waitTicks(25);
        server(context, "dance check", (player, m) -> {
            int hit = 0;
            for (int i = 0; i < 3; i++) {
                if (three[i].getHealth() < hp3[i]) {
                    hit++;
                }
            }
            log("dance: hits=" + (WarriorArts.danceHits() - counter[1]) + " damaged=" + hit + " at " + player.position());
            check(hit == 3 && WarriorArts.danceHits() - counter[1] == 3, "Klingentanz: three jumps, each monster hit");
            check(!WarriorArts.dancing(player), "the dance is over");
            clearDummies(player);
            home(player);
        });
        context.waitTicks(5);
        // Fehler-Stichprobe 05.11.: the dance never jumps through a wall. The one he sees falls at the first
        // jump; the next one stands behind a wall, in reach but out of sight.
        final Mob[] behind = new Mob[1];
        final float[] behindHp = new float[1];
        server(context, "dance wall", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            wall(player, 2, true);
            dummy(level, -2, 0, 1f);
            behind[0] = dummy(level, 4, 0, 200f);
            behindHp[0] = behind[0].getHealth();
            WarriorArts.reset();
            WarriorSpirit.set(player, 60);
            m.onCoreAction(player, "ult2");
        });
        context.waitTicks(25);
        server(context, "dance wall check", (player, m) -> {
            double wallX = Math.floor(HOME[0]) + 2;
            log("dance wall: at " + player.position() + " behind hp " + behindHp[0] + "->" + behind[0].getHealth());
            check(player.getX() < wallX && behind[0].getHealth() >= behindHp[0],
                    "Klingentanz: no jump through the wall (x " + player.getX() + ", wall at " + wallX + ")");
            clearDummies(player);
            wall(player, 2, false);
            home(player);
        });
        context.waitTicks(5);
        // Raserei: faster, more damage taken, a kill lengthens it.
        final long[] left = new long[1];
        server(context, "frenzy", (player, m) -> {
            become(player, m, "bloodlust", new ItemStack(Items.IRON_SWORD));
            WarriorArts.reset();
            WarriorSpirit.set(player, 60);
            counter[0] = WarriorArts.used();
            m.onCoreAction(player, "ult2");
            check(WarriorArts.used() == counter[0] + 1 && WarriorArts.frenzied(player) && WarriorArts.frenzySpeedOn(player),
                    "F as berserker: Raserei with more attack speed");
            check(WarriorArts.takenFactor(player) > 1.1f, "Raserei: more damage taken (" + WarriorArts.takenFactor(player) + ")");
            left[0] = WarriorArts.frenzyLeft(player);
            Mob weak = dummy((ServerLevel) player.level(), 1.4, 0, 1f);
            player.attack(weak);
            long now = WarriorArts.frenzyLeft(player);
            check(!weak.isAlive() && now >= left[0] + 15, "a kill in the Raserei lengthens it (" + left[0] + " -> " + now + " ticks)");
        });
        context.waitTicks(165);
        server(context, "frenzy over", (player, m) -> {
            check(!WarriorArts.frenzied(player) && !WarriorArts.frenzySpeedOn(player) && WarriorArts.takenFactor(player) == 1f,
                    "after the Raserei everything is back");
            clearDummies(player);
        });
        context.waitTicks(5);
        // Axtwurf: out and back, everything on the way hit and cleaved.
        final Mob[] line = new Mob[2];
        final float[] lineHp = new float[2];
        context.getInput().lookAt(-90.0f, 0.0f);
        server(context, "throw", (player, m) -> {
            become(player, m, "heavy_blow", new ItemStack(Items.IRON_AXE));
            ServerLevel level = (ServerLevel) player.level();
            line[0] = dummy(level, 4, 0, 200f);
            line[1] = dummy(level, 8, 0, 200f);
            WarriorArts.reset();
            WarriorSpirit.set(player, 60);
        });
        context.waitTicks(5);
        server(context, "throw F", (player, m) -> {
            lineHp[0] = line[0].getHealth();
            lineHp[1] = line[1].getHealth();
            counter[0] = WarriorArts.used();
            counter[1] = WarriorArts.throwHits();
            log("throw: look=" + player.getLookAngle());
            m.onCoreAction(player, "ult2");
            check(WarriorArts.used() == counter[0] + 1 && WarriorArts.axesInFlight() == 1, "F as axe fighter: the axe flies");
        });
        context.waitTicks(30);
        server(context, "throw check", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            int displays = level.getEntitiesOfClass(Display.ItemDisplay.class, player.getBoundingBox().inflate(20),
                    d -> d.entityTags().contains("nightsiege_axe_throw")).size();
            log("throw: hits=" + (WarriorArts.throwHits() - counter[1]) + " hp " + lineHp[0] + "->" + line[0].getHealth()
                    + ", " + lineHp[1] + "->" + line[1].getHealth() + " displays=" + displays);
            check(line[0].getHealth() < lineHp[0] && line[1].getHealth() < lineHp[1], "Axtwurf hits both monsters in its way");
            check(WarriorSpirit.cracked(line[0]) && WarriorSpirit.cracked(line[1]), "Axtwurf: with Spalter's broken armour");
            check(WarriorArts.throwHits() - counter[1] >= 3, "Axtwurf hits on the way out and back");
            check(WarriorArts.axesInFlight() == 0 && displays == 0, "the axe came back, its display is gone");
            clearDummies(player);
        });
        context.waitTicks(5);
        // Fehler-Stichprobe 05.11.: the axe strikes nothing behind a wall.
        server(context, "throw wall", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            home(player);
            wall(player, 2, true);
            line[0] = dummy(level, 3.5, 0, 200f);
            lineHp[0] = line[0].getHealth();
            WarriorArts.reset();
            WarriorSpirit.set(player, 60);
            m.onCoreAction(player, "ult2");
        });
        context.waitTicks(30);
        server(context, "throw wall check", (player, m) -> {
            log("throw wall: behind hp " + lineHp[0] + "->" + line[0].getHealth() + " in flight=" + WarriorArts.axesInFlight());
            check(line[0].getHealth() >= lineHp[0] && WarriorArts.axesInFlight() == 0,
                    "Axtwurf: no hit through the wall, the axe came back");
            clearDummies(player);
            wall(player, 2, false);
        });
        context.waitTicks(5);
        // Weihegrund: regeneration for the players inside, the undead burn and are slowed.
        final Mob[] undead = new Mob[1];
        server(context, "ground", (player, m) -> {
            become(player, m, "smite", new ItemStack(Items.IRON_AXE));
            undead[0] = dummy((ServerLevel) player.level(), 3, 0, 200f);
            undead[0].clearFire();
            player.removeAllEffects();
            WarriorArts.reset();
            WarriorSpirit.set(player, 60);
            counter[0] = WarriorArts.used();
            counter[1] = WarriorArts.groundPulses();
            m.onCoreAction(player, "ult2");
            check(WarriorArts.used() == counter[0] + 1 && WarriorArts.grounds() == 1, "F as paladin: Weihegrund on the ground");
        });
        context.waitTicks(30);
        server(context, "ground check", (player, m) -> {
            check(player.hasEffect(MobEffects.REGENERATION), "Weihegrund: the player inside regenerates");
            check(undead[0].isOnFire() || undead[0].getRemainingFireTicks() > 0, "Weihegrund: the undead burn");
            check(undead[0].hasEffect(MobEffects.SLOWNESS), "Weihegrund: monsters are slowed");
            check(WarriorArts.groundPulses() - counter[1] >= 2, "Weihegrund pulses every second");
            clearDummies(player);
        });
        context.waitTicks(5);
        // F needs 50 Kampfgeist, and a fight (outside creative).
        server(context, "low", (player, m) -> {
            WarriorArts.reset();
            WarriorSpirit.set(player, 20);
            counter[0] = WarriorArts.used();
            m.onCoreAction(player, "ult2");
            check(WarriorArts.used() == counter[0] && near(WarriorSpirit.value(player), 20.0), "F with 20 Kampfgeist does nothing");
        });
        server(context, "no fight", (player, m) -> {
            player.setGameMode(GameType.SURVIVAL);
            WarriorSpirit.set(player, 60);
            counter[0] = WarriorArts.used();
            boolean defending = m.isDefending(player);
            m.onCoreAction(player, "ult2");
            log("no fight: mode=" + player.gameMode() + " instabuild=" + player.getAbilities().instabuild + " defending=" + defending
                    + " used " + counter[0] + "->" + WarriorArts.used() + " spirit=" + WarriorSpirit.value(player));
            check(WarriorArts.used() == counter[0] && near(WarriorSpirit.value(player), 60.0), "F outside a fight does nothing (survival)");
            player.setGameMode(GameType.CREATIVE);
        });
        context.waitTicks(10);

        // ------------------------------------------------ 8. 0 with the kit and every night; the kit HUD
        server(context, "kit", (player, m) -> {
            become(player, m, "w_combo", new ItemStack(Items.IRON_SWORD));
            WarriorArts.reset();
            WarriorSpirit.set(player, 80);
            m.kits().equip(player, 1);
            check(m.kits().hasStash(player) && near(WarriorSpirit.value(player), 0.0), "the kit put on: Kampfgeist 0");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            home(player);
            target[0] = dummy((ServerLevel) player.level(), 1.6, 0, 400f);
            WarriorSpirit.set(player, 66);
        });
        context.waitTicks(20);
        for (int i = 0; i < 2; i++) {
            server(context, "hud hit", (player, m) -> {
                target[0].invulnerableTime = 0;
                player.attack(target[0]);
            });
            context.waitTicks(i == 0 ? 16 : 6);
        }
        context.waitTicks(20);   // the kit HUD's own state comes once a second
        final String[] hud = new String[1];
        context.runOnClient(client -> {
            UltState s = com.mki.nightsiege.client.UltHud.state();
            boolean ok = s != null && s.gauge && "nightsiege.kithud.spirit".equals(s.gaugeName) && s.gaugeGlow && s.hasAb2 && !s.ab2Dim;
            hud[0] = (ok ? "ok" : "bad") + " gauge=" + (s != null && s.gauge) + " name=" + (s == null ? "?" : s.gaugeName)
                    + " value=" + (s == null ? -1 : s.gaugeValue) + " glow=" + (s != null && s.gaugeGlow) + " F=" + (s != null && s.hasAb2)
                    + " icon=" + (s == null ? "?" : s.iconAb2) + " dim=" + (s != null && s.ab2Dim)
                    + " client spirit=" + com.mki.nightsiege.client.WarriorHud.value()
                    + " dots=" + com.mki.nightsiege.client.WarriorHud.chain()
                    + " kit hud=" + com.mki.nightsiege.client.KitHud.active();
        });
        log("hud: " + hud[0]);
        check(hud[0].startsWith("ok"), "kit HUD: the Kampfgeist bar glows (from 50) and F lights up");
        boolean dots = context.computeOnClient(client -> com.mki.nightsiege.client.WarriorHud.chain() == 2);
        check(dots, "the dots under the crosshair show the chain (2)");
        context.takeScreenshot("krieger_hud");
        server(context, "hud low", (player, m) -> WarriorSpirit.set(player, 30));
        context.waitTicks(10);
        boolean grey = context.computeOnClient(client -> {
            UltState s = com.mki.nightsiege.client.UltHud.state();
            return s != null && s.ab2Dim && !s.gaugeGlow;
        });
        check(grey, "below 50: the bar stops glowing, F is grey");
        context.takeScreenshot("krieger_hud_low");
        server(context, "night", (player, m) -> {
            WarriorSpirit.set(player, 50);
            m.combatEffects().newSiege();
            check(near(WarriorSpirit.value(player), 0.0), "a new night: Kampfgeist 0");
        });
        context.waitTicks(5);

        // The end: kit back, dummies gone, nothing learned.
        server(context, "end", (player, m) -> {
            clearDummies(player);
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
        String summary = checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS);
        log("summary: " + summary);
        System.out.println("[Night Siege test] krieger summary: " + summary);
    }
}
