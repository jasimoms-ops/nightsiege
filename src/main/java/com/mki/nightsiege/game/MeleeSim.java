package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.PlayerSkills;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The class simulation (/nightsiege sim): a bot defends test nights at the core, one after
 * another, as every subclass with a full skill tree (no crate gains), on one or more levels.
 * Melee classes swing when ready and block between swings (guardians); archers shoot bow or
 * crossbow and draw the sword when a monster is close; engineers set up the traps of their
 * kit in the preparation and overcharge their towers; summoners use the staff whenever it is
 * ready. All of them fire the ultimate and eat or drink when their health runs low.
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
            {"GUARDIAN", "g_ward", "warden"},
            {"ARCHER", "swift_draw", "swift"},
            {"ARCHER", "heavy_draw", "heavy"},
            {"ARCHER", "shrapnel", "bolter"},
            {"ARCHER", "a_netbolt", "trapper"},
            {"ENGINEER", "arrow_turret", "gunner"},
            {"ENGINEER", "tesla_coil", "electrician"},
            {"ENGINEER", "mortar_tower", "demolition"},
            {"ENGINEER", "potion_tower", "alchemist"},
            {"SUMMONER", "s_tamer", "tamer"},
            {"SUMMONER", "s_golemsmith", "golemsmith"},
            {"SUMMONER", "d_necro", "necromancer"},
            {"SUMMONER", "d_soulcatcher", "soulcatcher"}};

    private enum Phase { SETUP, PREP, FIGHT, PAUSE, DONE }

    private record Run(String[] sub, int level) {
    }

    /** One finished run. */
    public record Result(String subclass, int level, double seconds, boolean survived, double dealt,
                         double taken, int kills) {
    }

    private static List<Run> queue = new ArrayList<>();
    private static final List<Result> RESULTS = new ArrayList<>();
    private static List<Integer> levels = new ArrayList<>();
    private static Phase phase = Phase.DONE;
    private static int index;
    private static UUID playerId;
    private static BlockPos core;
    private static int maxTicks;
    private static long phaseTick;
    private static long fightStart;
    private static double dealt;
    private static double taken;
    private static int killsAtStart;
    private static long nextUse;
    private static long nextStaff;
    private static long nextRemote;
    private static boolean trapsSet;
    private static final List<BlockPos> TOWERS = new ArrayList<>();
    private static String skillsBackup;
    private static int prepBackup;
    private static boolean started;

    /** Seconds of preparation: time for the engineers to set up their traps. */
    private static final int PREP_SECONDS = 10;

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
        return start(player, List.of(level), seconds, List.of());
    }

    public static String start(ServerPlayer player, int level, int seconds, List<String> only) {
        return start(player, List.of(level), seconds, only);
    }

    /**
     * As above, on these levels and only for some classes or subclasses (e.g. "GUARDIAN",
     * "bulwark"); empty = all.
     */
    public static String start(ServerPlayer player, List<Integer> onLevels, int seconds, List<String> only) {
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
        levels = new ArrayList<>(onLevels.isEmpty() ? List.of(10) : onLevels);
        maxTicks = seconds * 20;
        queue = new ArrayList<>();
        for (int level : levels) {
            for (String[] sub : SUBCLASSES) {
                if (!only.isEmpty() && !only.contains(sub[0]) && !only.contains(sub[2])) {
                    continue;
                }
                queue.add(new Run(sub, level));
            }
        }
        RESULTS.clear();
        index = 0;
        String id = player.getUUID().toString();
        if (!m.save().isRegistered(id)) {
            m.save().register(id);
        }
        skillsBackup = Json.toJson(m.save().skillsOf(id));
        prepBackup = m.config().testPrepSeconds;
        m.config().testPrepSeconds = PREP_SECONDS;
        phase = Phase.SETUP;
        phaseTick = 0;
        started = true;
        NightSiegeMod.LOGGER.info("[Night Siege sim] Start: {} Läufe, Stufen {}, höchstens {} s", queue.size(), levels, seconds);
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
                m.config().testPrepSeconds = PREP_SECONDS;
                PlayerSkills skills = build(m.skillTree(), SkillClass.valueOf(run.sub()[0]), run.sub()[1]);
                m.save().skills.put(player.getUUID().toString(), skills);
                clearMonsters(level);
                clearTraps(level, m, player);
                player.setGameMode(GameType.SURVIVAL);
                player.removeAllEffects();
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5f);
                player.setShiftKeyDown(false);
                stand(player);
                dealt = 0;
                taken = 0;
                trapsSet = false;
                boolean ok = m.prepareTest(server, level, core, run.level(), SiegeEvent.NONE);
                if (!ok) {
                    finish(server, "Testnacht ließ sich nicht starten");
                    return;
                }
                phase = Phase.PREP;
                phaseTick = now;
            }
            case PREP -> {
                if (!trapsSet && now - phaseTick >= 20) {
                    trapsSet = true;
                    setUpTraps(m, player, level);
                }
                if (m.isActive()) {
                    phase = Phase.FIGHT;
                    fightStart = now;
                    nextUse = now;
                    nextStaff = now;
                    nextRemote = now + 100;
                    killsAtStart = m.nightKills();
                    // The kit came with the night: back to full health for a fair start.
                    player.setHealth(player.getMaxHealth());
                } else if (now - phaseTick > 20L * (PREP_SECONDS + 20)) {
                    finish(server, "Testnacht startete nicht");
                }
            }
            case FIGHT -> {
                long ticks = now - fightStart;
                boolean fell = player.isSpectator() || !m.isDefending(player);
                if (fell || ticks >= maxTicks || !m.isActive()) {
                    Run run = queue.get(index);
                    Result r = new Result(run.sub()[2], run.level(), ticks / 20.0, !fell, dealt, taken,
                            Math.max(0, m.nightKills() - killsAtStart));
                    RESULTS.add(r);
                    String line = String.format(Locale.ROOT,
                            "[Night Siege sim] Stufe %2d %-13s %5.0f s %-9s Schaden %6.0f  eingesteckt %6.0f  Kills %3d",
                            r.level(), r.subclass(), r.seconds(), r.survived() ? "gehalten" : "gefallen", r.dealt(), r.taken(), r.kills());
                    System.out.println(line);
                    NightSiegeMod.LOGGER.info(line);
                    if (player.isUsingItem()) {
                        player.stopUsingItem();
                    }
                    m.abort(server);
                    clearMonsters(level);
                    phase = Phase.PAUSE;
                    phaseTick = now;
                    index++;
                    return;
                }
                try {
                    fight(m, player, level, now);
                } catch (RuntimeException e) {
                    NightSiegeMod.LOGGER.warn("[Night Siege sim] Bot-Fehler: {}", e.toString());
                }
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

    /** One tick of the bot. */
    private static void fight(SiegeManager m, ServerPlayer player, ServerLevel level, long now) {
        PlayerSkills skills = m.save().skillsOf(player.getUUID().toString());
        SkillClass clazz = skills.clazz();
        boolean guardian = clazz == SkillClass.GUARDIAN;
        boolean bulwark = SkillTree.SUB_BULWARK.equals(m.skillTree().subclassOf(skills));
        if (player.position().distanceToSqr(Vec3.atBottomCenterOf(standPos())) > 36.0) {
            stand(player);
        }
        Mob close = nearest(level, player, 10.0);
        Mob far = nearest(level, player, 32.0);
        double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        boolean inReach = close != null && player.distanceTo(close) <= reach + 0.3;

        // The summoner uses the staff whenever it is ready (call, raise; the item says no when it can't).
        // (Also with monsters in reach: a real summoner calls first and fights after.)
        if (clazz == SkillClass.SUMMONER && now >= nextStaff) {
            nextStaff = now + 20;
            if (hold(player, s -> s.is(ModContent.STAFF))) {
                player.setShiftKeyDown(false);
                player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
            }
        }
        if (clazz == SkillClass.SUMMONER && (now - fightStart) % 200 == 0) {
            System.out.println(String.format(Locale.ROOT,
                    "[Night Siege sim] Begleiter t=%ds: alle=%d Wolf=%d Golem=%d Diener=%d Seelen=%d Leben=%.0f/%.0f Monster=%d",
                    (now - fightStart) / 20, Companions.countOf(player.getUUID()), Companions.countOf(player.getUUID(), "wolf"),
                    Companions.countOf(player.getUUID(), "golem"), Companions.countOf(player.getUUID(), "minion"),
                    Companions.get().soulsOf(player.getUUID()), player.getHealth(), player.getMaxHealth(),
                    level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
                            e -> e instanceof Enemy && !Companions.isCompanion(e)).size()));
        }
        // The engineer overcharges a tower now and then.
        if (clazz == SkillClass.ENGINEER && now >= nextRemote && !TOWERS.isEmpty()) {
            nextRemote = now + 200;
            RemoteItem.handler.use(player, TOWERS.get((int) ((now / 200) % TOWERS.size())));
        }

        boolean ranged = clazz == SkillClass.ARCHER || clazz == SkillClass.ENGINEER;
        if (inReach || !ranged) {
            if (close != null) {
                player.lookAt(EntityAnchorArgument.Anchor.EYES, close.getEyePosition());
            }
            if (inReach && hold(player, MeleeSim::isMelee) && player.getAttackStrengthScale(0.5f) >= 0.95f) {
                if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                    player.stopUsingItem();
                }
                if (player.isUsingItem() && guardian) {
                    player.stopUsingItem();
                }
                player.attack(close);
                player.swing(InteractionHand.MAIN_HAND, true);
            } else if (guardian && !player.isUsingItem() && !player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
                player.startUsingItem(InteractionHand.OFF_HAND);
            }
        } else if (far != null) {
            shoot(player, level, far);
        } else if (player.isUsingItem()) {
            player.stopUsingItem();
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

    /** Bow: draw fully and let go. Crossbow: load, then fire. Aimed a little above the target. */
    private static void shoot(ServerPlayer player, ServerLevel level, Mob target) {
        boolean crossbow = !hold(player, s -> s.is(Items.BOW)) && hold(player, s -> s.is(Items.CROSSBOW));
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.is(Items.BOW) && !weapon.is(Items.CROSSBOW)) {
            return;
        }
        double dist = player.distanceTo(target);
        Vec3 aim = target.getEyePosition().add(0, dist * (crossbow ? 0.03 : 0.05), 0);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
        if (crossbow) {
            ChargedProjectiles loaded = weapon.get(DataComponents.CHARGED_PROJECTILES);
            if (loaded != null && !loaded.isEmpty()) {
                if (player.isUsingItem()) {
                    player.stopUsingItem();
                }
                weapon.use(level, player, InteractionHand.MAIN_HAND);
            } else if (!player.isUsingItem()) {
                player.startUsingItem(InteractionHand.MAIN_HAND);
            } else if (player.getTicksUsingItem() >= 30) {
                player.releaseUsingItem();
            }
            return;
        }
        if (!player.isUsingItem() || player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            player.startUsingItem(InteractionHand.MAIN_HAND);
        } else if (player.getTicksUsingItem() >= 21) {
            player.releaseUsingItem();
        }
    }

    private static boolean isMelee(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.getItem() instanceof net.minecraft.world.item.MaceItem;
    }

    /** Puts the first matching item of the inventory into the main hand. False when there is none. */
    private static boolean hold(ServerPlayer player, java.util.function.Predicate<ItemStack> wanted) {
        if (wanted.test(player.getMainHandItem())) {
            return true;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && wanted.test(stack) && stack != player.getItemBySlot(EquipmentSlot.OFFHAND)
                    && stack != player.getItemBySlot(EquipmentSlot.HEAD) && stack != player.getItemBySlot(EquipmentSlot.CHEST)
                    && stack != player.getItemBySlot(EquipmentSlot.LEGS) && stack != player.getItemBySlot(EquipmentSlot.FEET)) {
                if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                    player.stopUsingItem();
                }
                ItemStack hand = player.getMainHandItem();
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                inv.setItem(i, hand);
                return true;
            }
        }
        return false;
    }

    /** The engineer sets up every trap of the kit in a ring around the core, as a player would. */
    private static void setUpTraps(SiegeManager m, ServerPlayer player, ServerLevel level) {
        TOWERS.clear();
        Inventory inv = player.getInventory();
        int slot = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            com.mki.nightsiege.core.TrapKind kind = ModContent.trapOf(stack);
            while (kind != null && !stack.isEmpty()) {
                if (m.trapService().placeBlocked(player, kind) != null) {
                    break;
                }
                BlockPos pos = ringSpot(level, kind, slot++);
                if (pos == null) {
                    break;
                }
                level.setBlock(pos, ModContent.TRAP_BLOCKS.get(kind).defaultBlockState(), 3);
                m.onTrapPlaced(level, pos, player, kind);
                stack.shrink(1);
                if (kind == com.mki.nightsiege.core.TrapKind.TURRET || kind == com.mki.nightsiege.core.TrapKind.TESLA
                        || kind == com.mki.nightsiege.core.TrapKind.MORTAR || kind == com.mki.nightsiege.core.TrapKind.BREWER) {
                    TOWERS.add(pos);
                }
            }
        }
        System.out.println("[Night Siege sim] Fallen gestellt: " + slot + ", Türme " + TOWERS.size());
    }

    /** Towers close to the core, floor traps further out where the monsters come. */
    private static BlockPos ringSpot(ServerLevel level, com.mki.nightsiege.core.TrapKind kind, int n) {
        boolean tower = kind == com.mki.nightsiege.core.TrapKind.TURRET || kind == com.mki.nightsiege.core.TrapKind.TESLA
                || kind == com.mki.nightsiege.core.TrapKind.MORTAR || kind == com.mki.nightsiege.core.TrapKind.BREWER;
        for (int tries = 0; tries < 40; tries++) {
            int k = n + tries * 7;
            double radius = tower ? 3.5 + (k % 2) : 6 + (k % 3) * 2;
            double angle = k * 2.3999632;   // golden angle: spread evenly
            BlockPos pos = core.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
            if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()
                    && !pos.equals(standPos()) && pos.distManhattan(standPos()) > 1) {
                return pos;
            }
        }
        return null;
    }

    private static void clearTraps(ServerLevel level, SiegeManager m, ServerPlayer player) {
        for (BlockPos pos : BlockPos.betweenClosed(core.offset(-12, 0, -12), core.offset(12, 2, 12))) {
            if (ModContent.TRAP_BLOCKS.containsValue(level.getBlockState(pos).getBlock())) {
                m.trapService().onBroken(level, pos.immutable());
                level.removeBlock(pos, false);
            }
        }
        TOWERS.clear();
    }

    /** Drinks a healing potion or eats a golden apple (heal), or eats normal food. */
    private static boolean use(ServerPlayer player, ServerLevel level, boolean heal) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            boolean fits = heal ? stack.is(Items.POTION) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                    : stack.has(net.minecraft.core.component.DataComponents.FOOD) && !stack.is(Items.GOLDEN_APPLE)
                    && !stack.is(Items.ENCHANTED_GOLDEN_APPLE) && !stack.is(Items.BEEF) && !stack.is(Items.IRON_INGOT);
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
                e -> e instanceof Enemy && e.isAlive() && !Companions.isCompanion(e))) {
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
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, e -> e instanceof Enemy && !Companions.isCompanion(e))) {
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
    // bookkeeping
    // ------------------------------------------------------------------

    /** Damage of the bot's side: its own hits, its traps and towers, its companions. */
    public static void onDamage(LivingEntity victim, DamageSource source, float amount) {
        if (!running() || phase != Phase.FIGHT || amount <= 0) {
            return;
        }
        if (victim.getUUID().equals(playerId)) {
            taken += amount;
            return;
        }
        if (!(victim instanceof Enemy) || Companions.isCompanion(victim)) {
            return;
        }
        Entity by = source.getEntity();
        if (by != null && (by.getUUID().equals(playerId) || playerId.equals(Companions.ownerOf(by)))) {
            dealt += amount;
        }
    }

    public static void onKill(Entity killer) {
        // Kills are counted by the night itself (all of them: own hits, traps, companions).
    }

    private static void finish(MinecraftServer server, String problem) {
        SiegeManager m = SiegeManager.get();
        if (m != null) {
            if (m.isActive() || m.isPreparing()) {
                m.abort(server);
            }
            m.config().testPrepSeconds = prepBackup;
            ServerPlayer p = server == null || playerId == null ? null : server.getPlayerList().getPlayer(playerId);
            if (p != null) {
                clearTraps((ServerLevel) p.level(), m, p);
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

    /** The table: one row per subclass, one column per level. */
    public static List<String> report() {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "[Night Siege sim] Normale Nacht, Stufen %s, höchstens %d s, allein, voll geskillt, ohne Kisten-Gewinne",
                levels, maxTicks / 20));
        out.add("[Night Siege sim] Zelle: Zeit (+ = gehalten) / Schaden / eingesteckt / Kills");
        StringBuilder head = new StringBuilder(String.format(Locale.ROOT, "[Night Siege sim] %-13s", "Unterklasse"));
        for (int level : levels) {
            head.append(String.format(Locale.ROOT, " | Stufe %-24d", level));
        }
        out.add(head.toString());
        for (String[] sub : SUBCLASSES) {
            boolean any = false;
            StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "[Night Siege sim] %-13s", sub[2]));
            for (int level : levels) {
                Result r = find(sub[2], level);
                any |= r != null;
                row.append(" | ").append(cell(r));
            }
            if (any) {
                out.add(row.toString());
            }
        }
        return out;
    }

    private static String cell(Result r) {
        if (r == null) {
            return String.format(Locale.ROOT, "%-30s", "-");
        }
        return String.format(Locale.ROOT, "%4.0f s%s / %5.0f / %5.0f / %3d", r.seconds(), r.survived() ? "+" : " ",
                r.dealt(), r.taken(), r.kills());
    }

    private static Result find(String sub, int level) {
        for (Result r : RESULTS) {
            if (r.subclass().equals(sub) && r.level() == level) {
                return r;
            }
        }
        return null;
    }
}
