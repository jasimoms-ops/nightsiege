package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.TrapKind;
import com.mki.nightsiege.game.KitProjectile;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.TrapService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scene "faktoren" (Paket 11.11., acceptance C.2): the server's config v27 is loaded like
 * /nightsiege reload (with the migration), then for six changed subclasses and three controls, at the
 * levels 5, 10 and 20 of a test night, a fixed hit of every damage source of the subclass goes through
 * the real damage chain (hurtServer) into a husk without armour. No bots, no chance, no crits.
 * Prints per line "FAKTOR;unterklasse;stufe;quelle;schaden;faktor_rel" with
 * faktor_rel = schaden / (hit x classDamage x lowLevelDealt), i.e. subclassDamage x level curve x
 * alchemistDirectHit, and checks it against the factor the loaded config names.
 */
final class FaktorenScenes {
    private static final String TAG = "nightsiege_faktor_ziel";
    private static final float HIT = 10f;
    private static final int[] LEVELS = {5, 10, 20};
    /** Subclass, its sources (bolzen = the crossbow's bolt; tesla/trankturm = the tower's blow). */
    private static final String[][] SUBS = {
            {SkillTree.SUB_ELECTRICIAN, "hieb", "turmpfeil", "tesla"},
            {SkillTree.SUB_TRAPPER, "hieb", "bolzen"},
            {SkillTree.SUB_GUNNER, "hieb", "pfeil", "turmpfeil"},
            {SkillTree.SUB_AXE, "hieb", "pfeil"},
            {SkillTree.SUB_SHIELDFIGHTER, "hieb", "pfeil"},
            {SkillTree.SUB_ALCHEMIST, "hieb", "pfeil", "turmpfeil", "trankturm", "saeure"},
            {SkillTree.SUB_BERSERKER, "hieb", "pfeil"},
            {SkillTree.SUB_PALADIN, "hieb", "pfeil"},
            {SkillTree.SUB_MEDIC, "hieb", "pfeil"},
    };
    private static final List<String> FAILS = new ArrayList<>();
    private static int checks;
    private static int lines;

    private FaktorenScenes() {
    }

    private static void check(boolean ok, String what) {
        checks++;
        System.out.println("[faktoren] " + (ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    /** The server's config v27 (tools/sim or the JUnit resources), searched upwards from the run folder. */
    private static Path configV27() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            for (String name : new String[] {"tools/sim/server-config-v27.json", "src/test/resources/server-config-v27.json"}) {
                Path p = dir.resolve(name);
                if (Files.isRegularFile(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        lines = 0;
        Path source = configV27();
        check(source != null, "Server-Config v27 gefunden (" + source + ")");
        if (source != null) {
            EventLookTest.onServer(context, (player, m) -> {
                try {
                    Files.copy(source, com.mki.nightsiege.game.Json.configFile("nightsiege.json"), StandardCopyOption.REPLACE_EXISTING);
                    m.reloadKeepingState();
                    SiegeConfig c = m.config();
                    System.out.println("[faktoren] Config geladen: settingsVersion " + c.settingsVersion + ", subclassDamage " + c.subclassDamage);
                    check(c.settingsVersion == SiegeConfig.SETTINGS_VERSION, "Migration gelaufen (settingsVersion " + c.settingsVersion + ")");
                } catch (java.io.IOException | RuntimeException e) {
                    check(false, "Server-Config laden: " + e);
                }
            });
            context.waitTicks(5);
        }
        final int[] saved = new int[2];
        final boolean[] refill = new boolean[1];
        EventLookTest.onServer(context, (player, m) -> {
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            refill[0] = m.config().refillSpawns;
        });
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
        for (int lvl : LEVELS) {
            EventLookTest.onServer(context, (player, m) -> {
                if (m.kits().hasStash(player)) {
                    m.kits().restore(player);
                }
                player.getInventory().clearContent();
                m.config().testPrepSeconds = 1;
                m.config().batchIntervalSeconds = 600;
                m.config().refillSpawns = false;
                ServerLevel level = (ServerLevel) player.level();
                m.prepareTest(level.getServer(), level, player.blockPosition(), lvl, SiegeEvent.NONE);
            });
            context.waitTicks(80);
            EventLookTest.closeScreen(context);
            EventLookTest.onServer(context, (player, m) -> {
                ServerLevel level = (ServerLevel) player.level();
                level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(160),
                        e -> e instanceof net.minecraft.world.entity.monster.Enemy).forEach(Mob::discard);
                check(m.isActive() && m.currentLevel() == lvl, "Testnacht Stufe " + lvl + " laeuft (Stufe " + m.currentLevel() + ")");
            });
            context.waitTicks(5);
            for (String[] row : SUBS) {
                game.getServer().runCommand("kill @e[tag=" + TAG + "]");
                game.getServer().runCommand("execute as @p at @p run summon minecraft:husk ~3 ~ ~ "
                        + "{NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"nightsiege\",\"" + TAG + "\"]}");
                context.waitTicks(5);
                EventLookTest.onServer(context, (player, m) -> measure(player, m, row, lvl));
                context.waitTicks(2);
            }
            game.getServer().runCommand("kill @e[tag=" + TAG + "]");
            EventLookTest.onServer(context, (player, m) -> {
                if (m.isActive()) {
                    m.abort(player.level().getServer());
                }
            });
            context.waitTicks(40);
        }
        EventLookTest.onServer(context, (player, m) -> {
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
        check(lines > 0, lines + " FAKTOR-Zeilen");
        System.out.println("[faktoren] summary: " + checks + " checks, " + FAILS.size() + " failed"
                + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }

    private static void measure(ServerPlayer player, SiegeManager m, String[] row, int lvl) {
        String sub = row[0];
        ServerLevel level = (ServerLevel) player.level();
        Mob victim = null;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob mob && mob.isAlive() && e.entityTags().contains(TAG)) {
                victim = mob;
            }
        }
        if (victim == null) {
            check(false, sub + " L" + lvl + ": Ziel fehlt");
            return;
        }
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        victim.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        victim.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.0);
        SkillTree.SubclassInfo info = null;
        for (SkillTree.SubclassInfo i : SkillTree.SUBCLASSES) {
            if (i.key().equals(sub)) {
                info = i;
            }
        }
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        own.unlockClass(info.clazz());
        own.switchTo(info.clazz());
        own.levels.clear();
        own.levels.put(info.node(), 1);   // the subclass only, no further node
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);   // no kit mace
        check(sub.equals(m.skillTree().subclassOf(own)), sub + " L" + lvl + ": Unterklasse gesetzt");
        SiegeConfig cfg = m.config();
        double outside = cfg.damageFactor(own.className(), null) * cfg.lowLevelDealt(m.currentLevel());
        for (int s = 1; s < row.length; s++) {
            String src = row[s];
            int reps = src.equals("saeure") ? 1 : 2;   // the acid marks the target: once, last
            float[] loss = new float[reps];
            double raw = src.equals("saeure") ? TrapService.acidHitForTests() : HIT;
            for (int r = 0; r < reps; r++) {
                victim.removeAllEffects();
                victim.setHealth(victim.getMaxHealth());
                victim.invulnerableTime = 0;
                loss[r] = hit(level, player, m, victim, src);
            }
            double rel = loss[0] / (raw * outside);
            double soll = soll(cfg, sub, src, lvl);
            System.out.println(String.format(Locale.ROOT, "FAKTOR;%s;%d;%s;%.4f;%.4f", sub, lvl, src, loss[0], rel));
            lines++;
            if (reps > 1) {
                check(Math.abs(loss[0] - loss[1]) < 1e-3, sub + " L" + lvl + " " + src + ": gleich bei Wiederholung ("
                        + loss[0] + " / " + loss[1] + ")");
            }
            check(Math.abs(rel - soll) < 0.002, String.format(Locale.ROOT, "%s L%d %s: faktor_rel %.4f = Config %.4f",
                    sub, lvl, src, rel, soll));
        }
        victim.discard();
    }

    /** One fixed hit of this source; returns the life the target lost. */
    private static float hit(ServerLevel level, ServerPlayer player, SiegeManager m, Mob victim, String src) {
        float before = victim.getHealth();
        switch (src) {
            case "hieb" -> victim.hurtServer(level, level.damageSources().playerAttack(player), HIT);
            case "pfeil", "bolzen", "turmpfeil" -> {
                Arrow arrow = new Arrow(level, victim.getX(), victim.getY() + 3, victim.getZ(), new ItemStack(Items.ARROW), null);
                arrow.setOwner(player);
                if (src.equals("turmpfeil")) {
                    arrow.addTag("nightsiege_turret");   // a turret's or drone's arrow
                }
                DamageSource source = level.damageSources().arrow(arrow, player);
                victim.hurtServer(level, source, HIT);
                arrow.discard();
            }
            case "tesla" -> {
                return TrapService.trapBlowForTests(level, player, TrapKind.TESLA.id(), victim, HIT);
            }
            case "trankturm" -> {
                return TrapService.trapBlowForTests(level, player, TrapKind.BREWER.id(), victim, HIT);
            }
            case "saeure" -> {
                KitProjectile shot = new KitProjectile(level, player, new ItemStack(Items.SPLASH_POTION), KitProjectile.ACID);
                m.kits().traps().onAcidLanded(level, shot, victim.position());
                shot.discard();
            }
            default -> {
                return 0f;
            }
        }
        return before - victim.getHealth();
    }

    /** The factor the loaded config names: subclassDamage x level curve x alchemistDirectHit (both new, if there). */
    private static double soll(SiegeConfig cfg, String sub, String src, int lvl) {
        double f = cfg.subclassDamage == null ? 1.0 : cfg.subclassDamage.getOrDefault(sub, 1.0);
        boolean tower = src.equals("turmpfeil") || src.equals("tesla") || src.equals("trankturm");
        try {
            boolean only = (Boolean) cfg.getClass().getMethod("damageLevelTrapsOnly", String.class).invoke(cfg, sub);
            if (tower || !only) {
                f *= (Double) cfg.getClass().getMethod("damageLevelFactor", String.class, int.class).invoke(cfg, sub, lvl);
            }
        } catch (ReflectiveOperationException e) {
            // before Paket 11.11.: no level curve
        }
        if (SkillTree.SUB_ALCHEMIST.equals(sub) && (src.equals("hieb") || src.equals("pfeil"))) {
            try {
                f *= cfg.getClass().getField("alchemistDirectHit").getDouble(cfg);
            } catch (ReflectiveOperationException e) {
                // before Paket 11.11.: no direct-hit factor
            }
        }
        return f;
    }
}
