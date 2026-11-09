package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.AbilityKeys;
import com.mki.nightsiege.core.AbilityTip;
import com.mki.nightsiege.core.AbilityTips;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.ArrowRain;
import com.mki.nightsiege.game.Companions;
import com.mki.nightsiege.game.EngineerAbilities;
import com.mki.nightsiege.game.EngineerEscape;
import com.mki.nightsiege.game.GainGear;
import com.mki.nightsiege.game.GainKeys;
import com.mki.nightsiege.game.Gadgets;
import com.mki.nightsiege.game.KitService;
import com.mki.nightsiege.game.SecondUlts;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.WarriorArts;
import com.mki.nightsiege.game.WarriorDash;
import com.mki.nightsiege.game.WarriorSpirit;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scene "klassencheck" (Tor 4c.1): all 20 subclasses on levels 1, 5, 10 and 20 with the whole tree of their
 * own way, in a test night in survival with the kit on. The bar: no key twice, nothing cut off, no box without
 * its table entry (none on C by fallback), every box with an icon and a tooltip; the kit complete. Every key
 * (Q/E/F/C from the bar, E as F field, X, R, 1-3) pressed once: something happens (monsters hurt, life healed,
 * an entity spawned, an effect, a move), its pause starts and the statistics ("tasten") count it; the ult made
 * ready by the test hook fires. "Keine zweiten Wege": the weapons' right click, sneaking and a gain item of the
 * gain keys start no ability. No error of the mod in the log. Lines "[klassencheck] OK/FAIL <sub> <level> ...",
 * summary "x von y"; FAIL with 0 checks.
 */
final class KlassencheckScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> ERRORS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static int skips;
    private static final String HUSK = "ns_kc_husk";
    private static final int[] LEVELS = {1, 5, 10, 20};
    /** Gains with an ability on the gain keys (GainGear.useAbility) and the second ultimates. */
    private static final List<String> KEY_GAINS = List.of("kletterhaken", "kampfhorn", "tarnumhang", "signalhorn",
            "mauerkelle", "ersatzbatterie", "leibwaechter", "oelfass", "rueckrufpfeife", "geisterross", "futternapf", "totenglocke");

    private KlassencheckScenes() {
    }

    private static void log(String line) {
        System.out.println("[klassencheck] " + line);
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
                checks++;
                FAILS.add(step + ": " + t);
                log("FAIL " + step + ": " + t);
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

    /** What a key can change: monsters' lost life and effects, own life, own effects, entities near, place, speed. */
    private static double[] look(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        double lost = 0;
        double huskEffects = 0;
        for (Mob m : husks(level)) {
            lost += m.getMaxHealth() - m.getHealth();
            huskEffects += m.getActiveEffects().size();
        }
        int near = level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(32),
                e -> e != player && !e.entityTags().contains(HUSK)).size();
        return new double[]{lost, huskEffects, player.getHealth() + player.getAbsorptionAmount(),
                player.getActiveEffects().size(), near, player.getX(), player.getY(), player.getZ(),
                player.getDeltaMovement().length()};
    }

    /** Something happened: monsters hurt or with an effect, life healed, an effect, an entity more, moved or pushed. */
    private static boolean changed(double[] a, double[] b) {
        for (int i = 0; i < 5; i++) {
            if (b[i] - a[i] > 1e-3) {
                return true;
            }
        }
        double moved = Math.abs(b[5] - a[5]) + Math.abs(b[6] - a[6]) + Math.abs(b[7] - a[7]);
        return moved > 0.3 || Math.abs(b[8] - a[8]) > 0.2;
    }

    /** The test hook "the ult charged" and every other pause back to 0, so each key can fire once. */
    private static void ready(ServerPlayer player, SiegeManager m) {
        m.combatEffects().forget(player.getUUID());
        m.combatEffects().guardian().forget(player.getUUID());
        EngineerAbilities.reset();
        EngineerEscape.reset();
        WarriorDash.reset();
        ArrowRain.reset();
        WarriorArts.reset();
        SecondUlts.reset();
        for (String g : KEY_GAINS) {
            GainKeys.pauseForTests(player, g, 0);
        }
        WarriorSpirit.set(player, 100);
        if (Companions.get() != null) {
            Companions.get().soulsForTest(player, 99);
        }
    }

    /** The pause behind a press, -1 when the key has none to read here. */
    private static int pause(ServerPlayer player, SiegeManager m, String press) {
        long now = player.level().getGameTime();
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        if (press.startsWith("slot:")) {
            int i = Integer.parseInt(press.substring(5));
            List<UltState.Slot> slots = m.abilitySlots(player);
            return i < slots.size() ? slots.get(i).cooldownTicks : -1;
        }
        if (press.startsWith("gain:")) {
            int i = Integer.parseInt(press.substring(5));
            List<String> keys = GainKeys.assigned(m, own, own.clazz());
            return i < keys.size() ? (int) GainKeys.pauseLeft(player, keys.get(i)) : -1;
        }
        return switch (press) {
            case "ult" -> m.combatEffects().cooldownLeft(player.getUUID(), now);
            case "ult2x" -> SecondUlts.secondsLeft(player) * 20;
            case "ult2" -> ArrowRain.has(own) ? ArrowRain.cooldownLeft(player, now)
                    : WarriorArts.has(own, m.skillTree()) ? WarriorArts.cooldownLeft(player, now) : -1;
            default -> -1;
        };
    }

    /** The statistics key of a press ("tasten"), null for the gain keys (they are not counted per key). */
    private static String statsKey(ServerPlayer player, SiegeManager m, String press) {
        if (press.startsWith("slot:")) {
            int i = Integer.parseInt(press.substring(5));
            List<UltState.Slot> slots = m.abilitySlots(player);
            return i < slots.size() ? slots.get(i).key : null;
        }
        return switch (press) {
            case "ult" -> AbilityKeys.Key.ULT.name();
            case "ult2" -> AbilityKeys.Key.ABILITY_2.name();
            case "ult2x" -> AbilityKeys.Key.ULT2.name();
            default -> null;
        };
    }

    /** A log4j appender for WARN and ERROR lines of the mod ("[Night Siege]" or its logger). */
    private static org.apache.logging.log4j.core.Appender listen() {
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        org.apache.logging.log4j.core.Appender app = new org.apache.logging.log4j.core.appender.AbstractAppender(
                "klassencheck", null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override
            public void append(org.apache.logging.log4j.core.LogEvent event) {
                String text = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
                String name = event.getLoggerName() == null ? "" : event.getLoggerName();
                if (event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.WARN)
                        && (text.contains("[Night Siege]") || name.toLowerCase().contains("nightsiege") || event.getThrown() != null
                        && String.valueOf(event.getThrown().getStackTrace().length > 0 ? event.getThrown().getStackTrace()[0] : "").contains("nightsiege"))) {
                    ERRORS.add(text + (event.getThrown() == null ? "" : " " + event.getThrown()));
                }
            }
        };
        app.start();
        ctx.getConfiguration().getRootLogger().addAppender(app, org.apache.logging.log4j.Level.WARN, null);
        ctx.updateLoggers();
        return app;
    }

    private static void unlisten(org.apache.logging.log4j.core.Appender app) {
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().removeAppender(app.getName());
        ctx.updateLoggers();
        app.stop();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        ERRORS.clear();
        checks = 0;
        skips = 0;
        org.apache.logging.log4j.core.Appender app = listen();
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 1200 4 true");
        game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
        context.getInput().lookAt(-90.0f, 0.0f);
        context.waitTicks(10);
        final int[] saved = new int[2];
        final boolean[] refill = new boolean[1];
        server(context, "night", (player, m) -> {
            if (!m.isRegistered(player)) {
                m.register(player);
            }
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

        final Set<String> subNodes = new HashSet<>();
        SkillTree.SUBCLASSES.forEach(i -> subNodes.add(i.node()));
        final List<String> learned = new ArrayList<>();
        int subsOk = 0;
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            int failsBefore = FAILS.size();
            String sub = info.key();
            // Tor 4c.6: the night of level 1 ends after about four minutes (CI: during demolition); from then on
            // nothing counted and the gain items worked by day. A new test night when the last one is over.
            final boolean[] renewed = new boolean[1];
            server(context, sub + " night", (player, m) -> {
                if (!m.isActive()) {
                    ServerLevel level = (ServerLevel) player.level();
                    renewed[0] = m.prepareTest(level.getServer(), level, player.blockPosition(), 1, com.mki.nightsiege.core.SiegeEvent.NONE);
                    log("     " + sub + ": night renewed=" + renewed[0]);
                }
            });
            context.waitTicks(2);
            if (renewed[0]) {
                context.waitTicks(80);
                EventLookTest.closeScreen(context);
                server(context, sub + " night clear", (player, m) -> {
                    ((ServerLevel) player.level()).getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(160),
                            e -> e instanceof net.minecraft.world.entity.monster.Enemy).forEach(Mob::discard);
                    check(m.isDefending(player), sub + ": the player defends the renewed night");
                });
                context.waitTicks(5);
            }
            for (int lvl : LEVELS) {
                final String tag = sub + " " + lvl;
                game.getServer().runCommand("kill @e[tag=" + HUSK + "]");
                String husk = "execute as @p at @p run summon minecraft:husk ^%s ^ ^6 {NoAI:1b,PersistenceRequired:1b,Tags:[\"nightsiege\",\"" + HUSK + "\"]}";
                for (String side : new String[]{"0", "1.2", "-1.2"}) {
                    game.getServer().runCommand(husk.formatted(side));
                }
                context.waitTicks(5);
                final List<String> presses = new ArrayList<>();
                final int[] problems = new int[1];
                server(context, tag + " setup", (player, m) -> {
                    ServerLevel level = (ServerLevel) player.level();
                    for (Mob h : husks(level)) {
                        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
                        h.setHealth(1000f);
                    }
                    PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
                    own.unlockClass(info.clazz());
                    own.switchTo(info.clazz());
                    own.chosenClass = info.clazz().name();
                    for (String id : learned) {
                        own.setLevel(id, 0);
                    }
                    learned.clear();
                    own.setLevel(info.node(), 1);
                    learned.add(info.node());
                    boolean ult = false;
                    for (SkillNode n : m.skillTree().all()) {
                        if (n.clazz() != info.clazz() || subNodes.contains(n.id())) {
                            continue;
                        }
                        // only the subclass's own way (pathTaken takes the first of a HashMap otherwise)
                        if (!n.onTrunk() && !n.clazz().isShared() && !info.way().equals(n.path())) {
                            continue;
                        }
                        if (n.effect().target() == SkillEffect.Target.ULTIMATE) {
                            if (ult || !info.way().equals(n.path())) {
                                continue;
                            }
                            ult = true;
                        }
                        own.setLevel(n.id(), n.maxLevel());
                        learned.add(n.id());
                    }
                    // the gains with a key ability and the class's second ultimate
                    List<String> gains = new ArrayList<>(KEY_GAINS);
                    String second = SecondUlts.gainFor(info.clazz());
                    if (second != null) {
                        gains.add(second);
                    }
                    for (String g : gains) {
                        own.setGainStage(info.clazz(), g, 1);
                    }
                    if (own.gainKeyChoice != null) {
                        own.gainKeyChoice.remove(info.clazz().name());
                    }
                    m.kits().equip(player, lvl);
                    ready(player, m);
                    m.sendUltHud(player);
                    problems[0] = SiegeManager.barProblems().size();
                    // what to press
                    List<UltState.Slot> slots = m.abilitySlots(player);
                    for (int i = 0; i < slots.size(); i++) {
                        presses.add("slot:" + i);
                    }
                    if (AbilityKeys.keyOf(sub, AbilityKeys.F_ACTION) != null) {
                        presses.add("ult2");   // the F field on place 2 (E)
                    }
                    presses.add("ult");
                    if (second != null && GainKeys.assigned(m, own, info.clazz()).contains(second)) {
                        presses.add("ult2x");
                    }
                    int gainKeys = GainKeys.assigned(m, own, info.clazz()).size();
                    for (int i = 0; i < Math.min(3, gainKeys); i++) {
                        presses.add("gain:" + i);
                    }
                });
                context.waitTicks(5);
                server(context, tag + " bar", (player, m) -> {
                    PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
                    check(sub.equals(m.skillTree().subclassOf(own)), tag + ": plays " + m.skillTree().subclassOf(own));
                    List<UltState.Slot> slots = m.abilitySlots(player);
                    Set<String> keys = new HashSet<>();
                    AbilityKeys.Key f = AbilityKeys.keyOf(sub, AbilityKeys.F_ACTION);
                    if (f != null) {
                        keys.add(f.name());
                    }
                    List<AbilityTip> tips = AbilityTips.barTips(m.skillTree(), own);
                    boolean twice = false;
                    boolean noEntry = false;
                    boolean noLook = false;
                    StringBuilder bar = new StringBuilder();
                    for (UltState.Slot s : slots) {
                        bar.append(s.id).append('=').append(s.key).append(' ');
                        twice |= !keys.add(s.key);
                        noEntry |= AbilityKeys.keyOf(sub, s.id) == null;
                        boolean tip = tips.stream().anyMatch(t -> s.id.equals(t.slot));
                        boolean icon = s.icon != null && !s.icon.isBlank() && s.name != null && !s.name.isBlank();
                        if (!tip || !icon) {
                            noLook = true;
                            log("     " + tag + ": " + s.id + " tooltip=" + tip + " icon=" + icon);
                        }
                    }
                    log("     " + tag + ": bar " + bar + "presses " + presses);
                    // Tor 4c.6: archers without the arrow gain have only their F field (E), which is no box
                    check(!slots.isEmpty() || f != null, tag + ": the bar has boxes (or the F field)");
                    check(!twice, tag + ": no key twice");
                    check(slots.size() <= AbilityKeys.ABILITY_PLACES.size(), tag + ": " + slots.size() + " boxes, nothing cut off");
                    check(!noEntry && SiegeManager.barProblems().size() == problems[0]
                                    && SiegeManager.barProblems().stream().noneMatch(p -> p.contains(" " + sub + "/") || p.contains("for " + sub)),
                            tag + ": every box has its own key entry (none on C by fallback)");
                    check(!noLook, tag + ": every box with icon and tooltip");
                    // the kit complete: armour, off hand and every item of the kit in the inventory
                    KitService.Kit kit = m.kits().kitForTests(player, lvl);
                    boolean full = true;
                    EquipmentSlot[] armor = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                    for (int i = 0; i < kit.armor().length && i < armor.length; i++) {
                        ItemStack want = kit.armor()[i];
                        if (want != null && !want.isEmpty() && !player.getItemBySlot(armor[i]).is(want.getItem())) {
                            full = false;
                            log("     " + tag + ": armour " + armor[i] + " missing " + want);
                        }
                    }
                    if (kit.offhand() != null && !kit.offhand().isEmpty() && !player.getOffhandItem().is(kit.offhand().getItem())) {
                        full = false;
                        log("     " + tag + ": off hand missing " + kit.offhand());
                    }
                    for (ItemStack want : kit.items()) {
                        int have = 0;
                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                            ItemStack st = player.getInventory().getItem(i);
                            if (st.is(want.getItem()) && Gadgets.of(st).equals(Gadgets.of(want))) {
                                have += st.getCount();
                            }
                        }
                        if (have < want.getCount()) {
                            full = false;
                            log("     " + tag + ": kit item missing " + want + " (have " + have + ")");
                        }
                    }
                    check(full && !kit.items().isEmpty(), tag + ": kit complete (" + kit.items().size() + " items)");
                    // "keine zweiten Wege": right click with the weapons, a gain item of the gain keys, sneaking
                    ServerLevel level = (ServerLevel) player.level();
                    player.getMainHandItem().getItem().use(level, player, InteractionHand.MAIN_HAND);
                    player.getOffhandItem().getItem().use(level, player, InteractionHand.OFF_HAND);
                    player.stopUsingItem();
                    ItemStack horn = Gadgets.make(Items.GOAT_HORN, "kampfhorn", 1);
                    ItemStack hand = player.getMainHandItem().copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, horn);
                    InteractionResult r = GainGear.onUseItem(player, level, InteractionHand.MAIN_HAND);
                    player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                    check(r == InteractionResult.FAIL && GainKeys.pauseLeft(player, "kampfhorn") <= 0,
                            tag + ": a gain item of the gain keys does nothing on right click at night (" + r + ")");
                    player.setShiftKeyDown(true);
                });
                context.waitTicks(5);
                server(context, tag + " second ways", (player, m) -> {
                    player.setShiftKeyDown(false);
                    boolean none = true;
                    for (String press : presses) {
                        int left = pause(player, m, press);
                        if (left > 0) {
                            none = false;
                            log("     " + tag + ": " + press + " pause " + left + " without its key");
                        }
                    }
                    check(none, tag + ": right click and sneaking start no ability");
                    ready(player, m);
                });
                context.waitTicks(2);
                for (String press : presses) {
                    final double[][] before = new double[1][];
                    final int[] pauseBefore = new int[1];
                    final int[] usesBefore = new int[1];
                    final String[] key = new String[1];
                    final String[] what = new String[1];
                    final int[] total = new int[1];
                    server(context, tag + " " + press, (player, m) -> {
                        ServerLevel level = (ServerLevel) player.level();
                        player.setHealth(player.getMaxHealth() * 0.5f);
                        player.getFoodData().setFoodLevel(15);   // no natural regeneration: a heal is the key's
                        player.getFoodData().setSaturation(0f);
                        List<Mob> targets = husks(level);
                        if (!targets.isEmpty()) {
                            Mob t = targets.get(0);
                            if (press.equals("ult")) {
                                t.setHealth(t.getMaxHealth() * 0.1f);   // Tor 4c.6: the execute strikes only a weakened monster
                            }
                            player.lookAt(EntityAnchorArgument.Anchor.EYES, t.position().add(0, t.getBbHeight() * 0.5, 0));
                        }
                        List<UltState.Slot> slots = m.abilitySlots(player);
                        what[0] = press;
                        total[0] = 1;
                        if (press.startsWith("slot:")) {
                            UltState.Slot s = slots.get(Integer.parseInt(press.substring(5)));
                            what[0] = s.key + "(" + s.id + ")";
                            total[0] = s.totalTicks;
                        }
                        key[0] = statsKey(player, m, press);
                        usesBefore[0] = key[0] == null ? 0 : m.stats().testKeyUses(player, key[0]);
                        pauseBefore[0] = pause(player, m, press);
                        before[0] = look(player);
                        m.onCoreAction(player, press);
                    });
                    context.waitTicks(10);
                    server(context, tag + " " + press + " after", (player, m) -> {
                        if (pauseBefore[0] > 0) {
                            skips++;
                            log("SKIP " + tag + ": " + what[0] + " still in its pause from before (" + pauseBefore[0] + ")");
                            return;
                        }
                        double[] after = look(player);
                        int left = pause(player, m, press);
                        check(changed(before[0], after), tag + ": " + what[0] + " does something");
                        if (pauseBefore[0] >= 0 && total[0] > 0) {
                            check(left > 0, tag + ": " + what[0] + " pause started (" + left + ")");
                        }
                        if (key[0] != null) {
                            int uses = m.stats().testKeyUses(player, key[0]);
                            check(uses > usesBefore[0], tag + ": " + what[0] + " counted on " + key[0] + " (" + usesBefore[0] + " -> " + uses + ")");
                        }
                    });
                    context.waitTicks(2);
                }
                server(context, tag + " end", (player, m) -> {
                    player.setHealth(player.getMaxHealth());
                    player.removeAllEffects();
                });
                game.getServer().runCommand("effect give @p minecraft:resistance 1200 4 true");
                context.waitTicks(2);
            }
            subsOk += FAILS.size() == failsBefore ? 1 : 0;
            log((FAILS.size() == failsBefore ? "OK   " : "FAIL ") + sub + " all levels");
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
        unlisten(app);
        for (String e : new ArrayList<>(ERRORS)) {
            log("     log: " + e);
        }
        check(ERRORS.isEmpty(), "no error or warning of the mod in the log (" + ERRORS.size() + ")");
        if (checks == 0) {
            check(false, "no check ran");
        }
        log("subclasses " + subsOk + " von " + SkillTree.SUBCLASSES.size() + ", skipped " + skips);
        log("summary: " + (checks - FAILS.size()) + " von " + checks + " OK" + (FAILS.isEmpty() ? "" : ", FAIL: " + FAILS.size()));
        System.out.println("[Night Siege test] klassencheck: " + (checks - FAILS.size()) + " von " + checks);
    }
}
