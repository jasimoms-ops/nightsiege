package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A melee balance simulation (/nightsiege sim): a bot fights test nights at the core,
 * one after another, as every melee subclass with a full skill tree, once with the old
 * values and once with the new ones. It swings when its weapon is ready, blocks with the
 * shield between swings (guardians), fires its ultimate and the shield action, and eats
 * or drinks when its health runs low – a plain but honest player.
 *
 * <p>The player's own skills and the config are restored afterwards.</p>
 */
public final class MeleeSim {

    /** Class, subclass node, subclass key. */
    public static final String[][] SUBCLASSES = {
            {"WARRIOR", "w_combo", "blademaster"},
            {"WARRIOR", "bloodlust", "berserker"},
            {"WARRIOR", "heavy_blow", "axe"},
            {"WARRIOR", "smite", "paladin"},
            {"GUARDIAN", "riposte", "shieldfighter"},
            {"GUARDIAN", "g_bastion", "bulwark"},
            {"GUARDIAN", "g_healing_hand", "medic"},
            {"GUARDIAN", "g_ward", "warden"}};

    private enum Phase { SETUP, PREP, FIGHT, PAUSE, DONE }

    private record Run(String[] sub, String variant) {
    }

    /** One finished run. */
    public record Result(String subclass, String variant, double seconds, boolean survived, double dealt,
                         double taken, int kills) {
    }

    private static List<Run> queue = new ArrayList<>();
    private static final List<Result> RESULTS = new ArrayList<>();
    private static Phase phase = Phase.DONE;
    private static int index;
    private static UUID playerId;
    private static BlockPos core;
    private static int siegeLevel;
    private static int maxTicks;
    private static long phaseTick;
    private static long fightStart;
    private static double dealt;
    private static double taken;
    private static int kills;
    private static long nextUse;
    private static String skillsBackup;
    private static Backup configBackup;
    private static boolean started;

    private MeleeSim() {
    }

    public static boolean running() {
        return started && phase != Phase.DONE;
    }

    public static boolean done() {
        return started && phase == Phase.DONE;
    }

    public static List<Result> results() {
        return new ArrayList<>(RESULTS);
    }

    /** Starts the simulation for this player at the main core. Returns null or an error key. */
    public static String start(ServerPlayer player, int level, int seconds) {
        return start(player, level, seconds, List.of());
    }

    /**
     * As above, only for some classes or subclasses (e.g. "GUARDIAN", "bulwark"); empty = all.
     */
    public static String start(ServerPlayer player, int level, int seconds, List<String> only) {
        SiegeManager m = SiegeManager.get();
        if (m == null || running()) {
            return "nightsiege.sim.busy";
        }
        if (m.isActive() || m.isPreparing()) {
            return "nightsiege.cmd.already_active";
        }
        if (m.save().cores == null || m.save().cores.isEmpty()) {
            return "nightsiege.msg.no_core";
        }
        CoreRecord c = m.save().cores.get(0);
        core = new BlockPos(c.x, c.y, c.z);
        playerId = player.getUUID();
        siegeLevel = level;
        maxTicks = seconds * 20;
        queue = new ArrayList<>();
        for (String[] sub : SUBCLASSES) {
            if (!only.isEmpty() && !only.contains(sub[0]) && !only.contains(sub[2])) {
                continue;
            }
            queue.add(new Run(sub, "alt"));
            queue.add(new Run(sub, "neu"));
        }
        RESULTS.clear();
        index = 0;
        String id = player.getUUID().toString();
        if (!m.save().isRegistered(id)) {
            m.save().register(id);
        }
        skillsBackup = Json.toJson(m.save().skillsOf(id));
        configBackup = Backup.of(m.config());
        m.config().testPrepSeconds = 2;
        phase = Phase.SETUP;
        phaseTick = 0;
        started = true;
        NightSiegeMod.LOGGER.info("[Night Siege sim] Start: {} Läufe, Stufe {}, höchstens {} s", queue.size(), level, seconds);
        return null;
    }

    /** Every server tick. */
    public static void tick(MinecraftServer server) {
        if (!running()) {
            return;
        }
        SiegeManager m = SiegeManager.get();
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (m == null || player == null) {
            finish(server, "Spieler weg");
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        switch (phase) {
            case SETUP -> {
                if (index >= queue.size()) {
                    finish(server, null);
                    return;
                }
                Run run = queue.get(index);
                applyVariant(m, run.variant());
                PlayerSkills skills = build(m.skillTree(), SkillClass.valueOf(run.sub()[0]), run.sub()[1]);
                m.save().skills.put(player.getUUID().toString(), skills);
                clearMonsters(level);
                player.setGameMode(GameType.SURVIVAL);
                player.removeAllEffects();
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5f);
                player.setShiftKeyDown(false);
                stand(player);
                dealt = 0;
                taken = 0;
                kills = 0;
                boolean ok = m.prepareTest(server, level, core, siegeLevel, SiegeEvent.THUNDER);
                if (!ok) {
                    finish(server, "Testnacht ließ sich nicht starten");
                    return;
                }
                phase = Phase.PREP;
                phaseTick = now;
            }
            case PREP -> {
                if (m.isActive()) {
                    phase = Phase.FIGHT;
                    fightStart = now;
                    nextUse = now;
                    // The kit came with the night: back to full health for a fair start.
                    player.setHealth(player.getMaxHealth());
                } else if (now - phaseTick > 400) {
                    finish(server, "Testnacht startete nicht");
                }
            }
            case FIGHT -> {
                long ticks = now - fightStart;
                boolean fell = player.isSpectator() || !m.isDefending(player);
                if (fell || ticks >= maxTicks || !m.isActive()) {
                    Run run = queue.get(index);
                    Result r = new Result(run.sub()[2], run.variant(), ticks / 20.0, !fell, dealt, taken, kills);
                    RESULTS.add(r);
                    String line = String.format(Locale.ROOT, "[Night Siege sim] %-13s %-3s %5.0f s %-9s Schaden %6.0f  eingesteckt %6.0f  Kills %3d",
                            r.subclass(), r.variant(), r.seconds(), r.survived() ? "gehalten" : "gefallen", r.dealt(), r.taken(), r.kills());
                    System.out.println(line);
                    NightSiegeMod.LOGGER.info(line);
                    m.abort(server);
                    clearMonsters(level);
                    phase = Phase.PAUSE;
                    phaseTick = now;
                    index++;
                    return;
                }
                fight(m, player, level, now);
            }
            case PAUSE -> {
                if (now - phaseTick >= 40) {
                    phase = Phase.SETUP;
                }
            }
            default -> {
            }
        }
    }

    /** One tick of the bot: stay at the core, swing when ready, block between swings, eat when low. */
    private static void fight(SiegeManager m, ServerPlayer player, ServerLevel level, long now) {
        PlayerSkills skills = m.save().skillsOf(player.getUUID().toString());
        boolean guardian = skills.clazz() == SkillClass.GUARDIAN;
        boolean bulwark = SkillTree.SUB_BULWARK.equals(m.skillTree().subclassOf(skills));
        if (player.position().distanceToSqr(Vec3.atBottomCenterOf(standPos())) > 36.0) {
            stand(player);
        }
        Mob target = nearest(level, player, 10.0);
        if (target != null) {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        }
        double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        boolean inReach = target != null && player.distanceTo(target) <= reach + 0.3;
        if (inReach && player.getAttackStrengthScale(0.5f) >= 0.95f) {
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            player.attack(target);
            player.swing(InteractionHand.MAIN_HAND, true);
        } else if (guardian && !player.isUsingItem() && !player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
            player.startUsingItem(InteractionHand.OFF_HAND);
        }
        player.setShiftKeyDown(bulwark);
        if (now % 60 == 0) {
            m.onCoreAction(player, "ult");
        }
        if (guardian && player.isBlocking() && now % 40 == 20) {
            m.onCoreAction(player, "shield");
        }
        if (now >= nextUse) {
            boolean low = player.getHealth() < player.getMaxHealth() * 0.45f;
            if (low && use(player, level, true)) {
                nextUse = now + 40;
            } else if (player.getFoodData().getFoodLevel() < 14 && use(player, level, false)) {
                nextUse = now + 40;
            }
        }
    }

    /** Drinks a healing potion or eats a golden apple (heal), or eats normal food. */
    private static boolean use(ServerPlayer player, ServerLevel level, boolean heal) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            boolean fits = heal ? stack.is(Items.POTION) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                    : stack.has(net.minecraft.core.component.DataComponents.FOOD) && !stack.is(Items.GOLDEN_APPLE)
                    && !stack.is(Items.ENCHANTED_GOLDEN_APPLE);
            if (fits) {
                ItemStack rest = stack.finishUsingItem(level, player);
                inv.setItem(i, rest);
                return true;
            }
        }
        return false;
    }

    private static Mob nearest(ServerLevel level, ServerPlayer player, double radius) {
        Mob best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                e -> e instanceof Enemy && e.isAlive())) {
            double d = mob.distanceToSqr(player);
            if (d < bestD) {
                bestD = d;
                best = mob;
            }
        }
        return best;
    }

    private static BlockPos standPos() {
        return core.offset(2, 0, 0);
    }

    private static void stand(ServerPlayer player) {
        BlockPos p = standPos();
        player.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    private static void clearMonsters(ServerLevel level) {
        AABB box = new AABB(core).inflate(96.0);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, e -> e instanceof Enemy)) {
            mob.discard();
        }
    }

    // ------------------------------------------------------------------
    // the skill tree of one subclass, fully learned
    // ------------------------------------------------------------------

    /** A full skill tree for one subclass: its way, its subclass node, every other node that can be had. */
    public static PlayerSkills build(SkillTree tree, SkillClass clazz, String subclassNode) {
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = clazz.name();
        SkillNode sub = tree.get(subclassNode);
        String way = sub == null ? "" : sub.path();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SkillNode n : tree.ofClass(clazz)) {
                if (!(n.onTrunk() || way.equals(n.path()))) {
                    continue;
                }
                // The subclass group: only the subclass node itself.
                if (sub != null && n.inGroup() && n.group().equals(sub.group()) && !n.id().equals(subclassNode)) {
                    continue;
                }
                while (p.levelOf(n.id()) < n.maxLevel()) {
                    String why = tree.blockedReason(n.id(), p);
                    if (why != null && !why.equals("points")) {
                        break;
                    }
                    p.setLevel(n.id(), p.levelOf(n.id()) + 1);
                    changed = true;
                }
            }
        }
        return p;
    }

    // ------------------------------------------------------------------
    // old and new values
    // ------------------------------------------------------------------

    /** "alt" = the values of Simon's test on 24 October, "neu" = the fix after it. */
    private static void applyVariant(SiegeManager m, String variant) {
        SiegeConfig cfg = m.config();
        configBackup.restore(cfg);
        Map<String, SiegeConfig.SkillTweak> tweaks = new LinkedHashMap<>(configBackup.skillTweaks);
        if (variant.equals("alt")) {
            cfg.meleeLifesteal = 0.0;
            cfg.maxDamageReduction = 0.85;
            cfg.classDamageTaken.put("warrior", 1.0);
            cfg.subclassDamage.put("shieldfighter", 1.0);
            cfg.subclassDamageTaken.put("shieldfighter", 1.0);
            tweak(tweaks, "riposte", 1.5);
            tweak(tweaks, "g_shieldthrow", 6.0);
            tweak(tweaks, "g_shieldbash", 5.0);
            tweak(tweaks, "g_thorns", 2.0);
            tweak(tweaks, "g_vengeance", 6.0);
            tweak(tweaks, "g_anchor", 0.4);
        } else {
            cfg.meleeLifesteal = 0.08;
            cfg.maxDamageReduction = 0.70;
            cfg.classDamageTaken.put("warrior", 0.85);
            cfg.subclassDamage.put("shieldfighter", 1.25);
            cfg.subclassDamageTaken.put("shieldfighter", 0.8);
        }
        cfg.testPrepSeconds = 2;
        m.skillTree().applyTweaks(tweaks);
    }

    private static void tweak(Map<String, SiegeConfig.SkillTweak> tweaks, String id, double amount) {
        SiegeConfig.SkillTweak t = new SiegeConfig.SkillTweak();
        t.amount = amount;
        tweaks.put(id, t);
    }

    /** The config values the simulation touches, to put back afterwards. */
    private record Backup(double lifesteal, double maxReduction, Map<String, Double> classTaken,
                          Map<String, Double> subDamage, Map<String, Double> subTaken,
                          Map<String, SiegeConfig.SkillTweak> skillTweaks, int prepSeconds) {
        static Backup of(SiegeConfig cfg) {
            return new Backup(cfg.meleeLifesteal, cfg.maxDamageReduction, new LinkedHashMap<>(cfg.classDamageTaken),
                    new LinkedHashMap<>(cfg.subclassDamage), new LinkedHashMap<>(cfg.subclassDamageTaken),
                    new LinkedHashMap<>(cfg.skillTweaks), cfg.testPrepSeconds);
        }

        void restore(SiegeConfig cfg) {
            cfg.meleeLifesteal = lifesteal;
            cfg.maxDamageReduction = maxReduction;
            cfg.classDamageTaken = new LinkedHashMap<>(classTaken);
            cfg.subclassDamage = new LinkedHashMap<>(subDamage);
            cfg.subclassDamageTaken = new LinkedHashMap<>(subTaken);
            cfg.skillTweaks = new LinkedHashMap<>(skillTweaks);
            cfg.testPrepSeconds = prepSeconds;
        }
    }

    // ------------------------------------------------------------------
    // bookkeeping
    // ------------------------------------------------------------------

    public static void onDamage(LivingEntity victim, DamageSource source, float amount) {
        if (!running() || phase != Phase.FIGHT || amount <= 0) {
            return;
        }
        if (victim.getUUID().equals(playerId)) {
            taken += amount;
        } else if (victim instanceof Enemy && source.getEntity() != null && source.getEntity().getUUID().equals(playerId)) {
            dealt += amount;
        }
    }

    public static void onKill(Entity killer) {
        if (running() && phase == Phase.FIGHT && killer != null && killer.getUUID().equals(playerId)) {
            kills++;
        }
    }

    private static void finish(MinecraftServer server, String problem) {
        SiegeManager m = SiegeManager.get();
        if (m != null) {
            if (m.isActive() || m.isPreparing()) {
                m.abort(server);
            }
            if (configBackup != null) {
                configBackup.restore(m.config());
                m.skillTree().applyTweaks(m.config().skillTweaks);
            }
            if (skillsBackup != null && playerId != null) {
                PlayerSkills back = Json.fromJson(skillsBackup, PlayerSkills.class);
                if (back != null) {
                    m.save().skills.put(playerId.toString(), back);
                }
            }
            m.persist();
        }
        phase = Phase.DONE;
        List<String> lines = report();
        if (problem != null) {
            lines.add(0, "[Night Siege sim] abgebrochen: " + problem);
        }
        for (String line : lines) {
            System.out.println(line);
        }
        try {
            Files.write(Json.configFile("nightsiege-sim.txt"), lines);
        } catch (java.io.IOException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege sim] Ergebnis nicht gespeichert: {}", e.toString());
        }
        ServerPlayer player = server == null || playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            for (String line : lines) {
                player.sendSystemMessage(Component.literal(line.replace("[Night Siege sim] ", "")).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /** The table: per subclass old and new side by side. */
    public static List<String> report() {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "[Night Siege sim] Stufe %d, Gewitter, höchstens %d s, voll geskillt, ohne Kisten-Gewinne",
                siegeLevel, maxTicks / 20));
        out.add("[Night Siege sim] Unterklasse    alt: Zeit / Schaden / eingesteckt   neu: Zeit / Schaden / eingesteckt");
        for (String[] sub : SUBCLASSES) {
            Result alt = find(sub[2], "alt");
            Result neu = find(sub[2], "neu");
            out.add(String.format(Locale.ROOT, "[Night Siege sim] %-13s %s   %s", sub[2], cell(alt), cell(neu)));
        }
        return out;
    }

    private static String cell(Result r) {
        if (r == null) {
            return "        -                    ";
        }
        return String.format(Locale.ROOT, "%4.0f s%s / %5.0f / %5.0f", r.seconds(), r.survived() ? "+" : " ", r.dealt(), r.taken());
    }

    private static Result find(String sub, String variant) {
        for (Result r : RESULTS) {
            if (r.subclass().equals(sub) && r.variant().equals(variant)) {
                return r;
            }
        }
        return null;
    }
}
