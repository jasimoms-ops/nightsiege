package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.CrateGains;
import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.CrateRarity;
import com.mki.nightsiege.core.CrateRoll;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reward crates: every defender of a won night gets one for the class they
 * played. Opened (any time, also by day), a band of gains spins in a window and
 * stops on one; the gain goes into that class's night kit for good.
 */
public final class CrateService {

    public static Item CRATE;

    private final Supplier<SiegeSave> save;
    private final Supplier<SiegeConfig> config;
    private final SkillTree tree;
    private final Runnable persist;
    private final Random random = new Random();
    /** When each player last opened a crate (game time): the next waits until the band stopped. */
    private final java.util.Map<UUID, Long> lastOpened = new java.util.HashMap<>();

    public CrateService(Supplier<SiegeSave> save, Supplier<SiegeConfig> config, SkillTree tree, Runnable persist) {
        this.save = save;
        this.config = config;
        this.tree = tree;
        this.persist = persist;
    }

    /** Registers the crate item (called with the other content). */
    public static void registerItem() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ModContent.id("reward_crate"));
        CRATE = new CrateItem(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.EPIC));
        Registry.register(BuiltInRegistries.ITEM, key, CRATE);
    }

    /** The crate item: right click opens it. */
    public static final class CrateItem extends Item {
        public CrateItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
                SiegeManager.get().crates().open(serverPlayer, hand);
            }
            return InteractionResult.SUCCESS;
        }
    }

    // ------------------------------------------------------------------
    // handing out
    // ------------------------------------------------------------------

    /** A crate for one class, night kind and level. */
    public static ItemStack crate(SkillClass clazz, CrateKind kind, int level) {
        return crate(clazz, kind, level, 0);
    }

    /** As above, from a night won under {@code curses} curse stones (update 2): it rolls better odds. */
    public static ItemStack crate(SkillClass clazz, CrateKind kind, int level, int curses) {
        ItemStack stack = new ItemStack(CRATE);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString("crate_class", clazz == null ? "" : clazz.name());
            tag.putString("crate_kind", kind.name());
            tag.putInt("crate_level", level);
            if (curses > 0) {
                tag.putInt("crate_curse", curses);
            }
        });
        List<Component> lines = new ArrayList<>();
        if (clazz != null) {
            lines.add(Component.translatable(clazz.translationKey()).withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("nightsiege.crate.kind." + kind.key()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("nightsiege.crate.level", level).withStyle(ChatFormatting.GRAY));
        if (curses > 0) {
            lines.add(Component.translatable("nightsiege.crate.cursed", curses).withStyle(ChatFormatting.DARK_PURPLE));
        }
        lines.add(Component.translatable("nightsiege.crate.hint").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    /**
     * After a won night: one crate for every defender, for the class they played, and with
     * the chance {@code bonusCrateChance} a second one (Simon, 27 October 2026).
     */
    public void award(MinecraftServer server, Set<UUID> defenders, CrateKind kind, int level) {
        award(server, defenders, kind, level, 0);
    }

    /**
     * A crate read back from the old JSON form of the stash had its numbers shrunk (the level as a
     * byte), so it no longer stacked with a new crate (Simon 31.10.): the numbers become ints again.
     */
    public static void normalize(ItemStack stack) {
        if (CRATE == null || stack == null || !stack.is(CRATE) || !stack.has(DataComponents.CUSTOM_DATA)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.getInt("crate_level").ifPresent(v -> tag.putInt("crate_level", v));
            tag.getInt("crate_curse").ifPresent(v -> tag.putInt("crate_curse", v));
        });
    }

    /** A crate of the playtest (28 October 2026): it opens only for players of a running playtest. */
    public static ItemStack playtestCrate(SkillClass clazz, CrateKind kind, int level) {
        ItemStack stack = crate(clazz, kind, level, 0);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("crate_playtest", true));
        ItemLore lore = stack.get(DataComponents.LORE);
        List<Component> lines = new ArrayList<>(lore == null ? List.of() : lore.lines());
        lines.add(0, Component.translatable("nightsiege.playtest.crate_lore").withStyle(ChatFormatting.AQUA));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    /**
     * As above for a night won under curse stones (update 2 "Fluch & Rache"): the crates roll
     * better odds, and every curse adds {@code curseCrateChance} to the chance of a second one.
     */
    public void award(MinecraftServer server, Set<UUID> defenders, CrateKind kind, int level, int curses) {
        SiegeConfig cfg = config.get();
        double bonus = (cfg == null ? 0.0 : cfg.bonusCrateChance) + Curses.extraCrateChance(cfg, curses);
        for (UUID id : defenders) {
            int count = java.util.concurrent.ThreadLocalRandom.current().nextDouble() < bonus ? 2 : 1;
            for (int i = 0; i < count; i++) {
                awardOne(server, id, kind, level, curses);
            }
        }
        persist.run();
    }

    /** Exactly one crate for every defender, without the chance of a second (the treasure raider, 27 October 2026). */
    public void awardEach(MinecraftServer server, Set<UUID> defenders, CrateKind kind, int level) {
        for (UUID id : new java.util.ArrayList<>(defenders)) {
            awardOne(server, id, kind, level, 0);
        }
        persist.run();
    }

    private void awardOne(MinecraftServer server, UUID id, CrateKind kind, int level, int curses) {
        {
            PlayerSkills own = save.get().skillsOf(id.toString());
            SkillClass clazz = own.clazz();
            if (clazz == null) {
                return;
            }
            SiegeManager m = SiegeManager.get();
            if (m != null && m.playtest().takesPart(id)) {
                // A playtest gives its own crates; a real one from it would outlive the playtest (Simon 31.10.).
                NightSiegeMod.LOGGER.info("[Night Siege] Keine echte Kiste im Playtest fuer {}", id);
                return;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && !waits(player)) {
                give(player, crate(clazz, kind, level, curses));
                KitService.savePlayer(player);   // on disk at once, not at the next autosave
            } else {
                // Offline, or the night kit is on: the crate waits in the save (Simon 31.10.: never into the
                // kit inventory, which goes at the restore); it comes when they are back or the kit goes.
                Map<String, List<String>> pending = save.get().pendingCrates;
                if (pending == null) {
                    save.get().pendingCrates = pending = new java.util.HashMap<>();
                }
                pending.computeIfAbsent(id.toString(), k -> new ArrayList<>())
                        .add(clazz.name() + "|" + kind.name() + "|" + level + (curses > 0 ? "|" + curses : ""));
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("nightsiege.crate.after_kit").withStyle(ChatFormatting.LIGHT_PURPLE));
                    player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
                }
            }
        }
    }

    /**
     * True while crates must wait for a player (Simon 31.10.: survival safety): the night kit is on
     * (its inventory goes at the restore), they take part in a playtest (its inventory goes at the end)
     * or they are dead (the body's inventory is gone on respawn).
     */
    private static boolean waits(ServerPlayer player) {
        SiegeManager m = SiegeManager.get();
        return player.isDeadOrDying() || m != null && (m.kits().hasStash(player) || m.playtest().takesPart(player.getUUID()));
    }

    /** Crates won while the player was away or wore the kit. */
    public void deliverPending(ServerPlayer player) {
        Map<String, List<String>> pending = save.get().pendingCrates;
        if (pending == null || !pending.containsKey(player.getUUID().toString()) || waits(player)) {
            return;   // they come after the kit, the playtest or the respawn
        }
        List<String> list = pending.remove(player.getUUID().toString());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (String spec : list) {
            String[] parts = spec.split("\\|");
            if (parts.length < 3) {
                continue;
            }
            int level;
            try {
                level = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                level = 1;
            }
            int curses = 0;
            if (parts.length > 3) {
                try {
                    curses = Integer.parseInt(parts[3]);
                } catch (NumberFormatException e) {
                    curses = 0;
                }
            }
            give(player, crate(SkillClass.byName(parts[0]), CrateKind.byName(parts[1]), level, curses));
        }
        KitService.savePlayer(player);   // in the player's data before the save forgets them
        persist.run();
    }

    private static void give(ServerPlayer player, ItemStack crate) {
        player.getInventory().placeItemBackInInventory(crate);
        player.sendSystemMessage(Component.translatable("nightsiege.crate.got").withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    // ------------------------------------------------------------------
    // opening
    // ------------------------------------------------------------------

    /** Opens the crate in the player's hand: rolls it, applies the gain, shows the band. */
    public void open(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !stack.is(CRATE)) {
            return;
        }
        // One crate at a time: holding the button opened several in a second and the band of each
        // was gone before it stopped (Simon 28.10.: "Kisten können nicht richtig geöffnet werden").
        long now = player.level().getGameTime();
        Long last = lastOpened.get(player.getUUID());
        if (last != null && now - last < CrateFx.SPIN_TICKS + 10 && now >= last) {
            return;
        }
        lastOpened.put(player.getUUID(), now);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag tag = data == null ? new net.minecraft.nbt.CompoundTag() : data.copyTag();
        SkillClass clazz = SkillClass.byName(tag.getStringOr("crate_class", ""));
        PlayerSkills own = save.get().skillsOf(player.getUUID().toString());
        if (clazz == null || clazz.isShared()) {
            clazz = own.clazz();
        }
        if (clazz == null) {
            player.sendSystemMessage(Component.translatable("nightsiege.crate.no_class").withStyle(ChatFormatting.RED));
            return;
        }
        if (tag.getBooleanOr("crate_playtest", false) && !SiegeManager.get().playtest().takesPart(player.getUUID())) {
            // A playtest crate left over: it belongs to the playtest only.
            stack.shrink(stack.getCount());
            player.sendSystemMessage(Component.translatable("nightsiege.playtest.crate_gone").withStyle(ChatFormatting.GRAY));
            return;
        }
        CrateKind kind = CrateKind.byName(tag.getStringOr("crate_kind", "NORMAL"));
        int level = Math.max(1, tag.getIntOr("crate_level", 1));
        int curses = Math.max(0, tag.getIntOr("crate_curse", 0));
        CrateRoll roll = roll(own, clazz, kind, level, curses);
        stack.shrink(1);
        try {
            SiegeManager.get().stats().crate(player, clazz.name(), kind.name(), level, roll);
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.debug("[Night Siege] Kisten-Statistik: {}", e.toString());
        }
        if (roll.infernite && InferniteContent.INGOT != null) {
            player.getInventory().placeItemBackInInventory(new ItemStack(InferniteContent.INGOT));
        }
        persist.run();
        KitService.savePlayer(player);   // the crate is gone from the inventory on disk too (Simon 31.10.: no second roll after a crash)
        boolean window = ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE);
        if (window) {
            ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("crate", Json.toJson(roll)));
        }
        // Light, sparks and (from epic on) a firework when the band stops.
        CrateFx.schedule(player, com.mki.nightsiege.core.CrateRarity.byName(roll.rarity), roll.gain,
                window ? CrateFx.SPIN_TICKS : 0);
        // The chat keeps a short note of it (after the window, it is still there to read).
        player.sendSystemMessage(Component.translatable(roll.duplicate ? "nightsiege.crate.result_dup" : "nightsiege.crate.result",
                Component.translatable("nightsiege.gain." + roll.gain), Component.translatable(clazz.translationKey()),
                roll.points).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (roll.infernite) {
            player.sendSystemMessage(Component.translatable("nightsiege.crate.infernite").withStyle(ChatFormatting.GOLD));
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Kiste von {}: {} ({}){}", player.getScoreboardName(), roll.gain,
                roll.rarity, roll.duplicate ? " doppelt" : "");
    }

    /** The odds of a crate from a night won under curses: half a step up the chain for each one. */
    public static int[] cursedOdds(SiegeConfig cfg, CrateKind kind, int curses) {
        int[][] chain = {cfg.crateOddsNormal, cfg.crateOddsEvent, cfg.crateOddsFragment, cfg.crateOddsCursed};
        return com.mki.nightsiege.core.CurseKind.shiftOdds(chain, kind.ordinal(), curses);
    }

    /** Rolls a crate and applies the result to the player's skills. Public for tests. */
    public CrateRoll roll(PlayerSkills own, SkillClass clazz, CrateKind kind, int level) {
        return roll(own, clazz, kind, level, 0);
    }

    /** As above for a crate of a cursed night: every curse moves the odds half a step up (update 2). */
    public CrateRoll roll(PlayerSkills own, SkillClass clazz, CrateKind kind, int level, int curses) {
        SiegeConfig cfg = config.get();
        String way = own.hasUnlocked(clazz) ? own.as(clazz, () -> own.pathTaken(tree)) : "";
        // The subclass of the profile played in this class (learned or chosen with the class): gains it
        // has no use for stay in the crate (balance update 2, 29 October 2026).
        String sub = own.hasUnlocked(clazz) ? tree.subclassOfProfile(own, own.profileFor(clazz)) : "";
        if (way.isEmpty() && !sub.isEmpty()) {
            String planned = tree.wayOfSubclass(clazz, sub);
            way = planned == null ? "" : planned;
        }
        CrateRoll roll = new CrateRoll();
        roll.clazz = clazz.name();
        roll.level = level;
        // The luck crate: this crate rolls like the next better night, then it is used up.
        if (own.gainStage(clazz, "glueck") > 0) {
            own.setGainStage(clazz, "glueck", 0);
            kind = kind.better();
            roll.lucky = true;
        }
        roll.kind = kind.name();
        int[] weights = curses > 0 ? cursedOdds(cfg, kind, curses) : cfg.crateOdds(kind);
        roll.odds = weights.clone();
        CrateGains.Gain win = CrateGains.roll(clazz, way, sub, weights, cfg.crateClassShare, random);
        if (win == null) {
            win = CrateGains.get("fackeln");
        }
        roll.gain = win.id();
        roll.rarity = win.rarity().name();
        roll.icon = CrateGains.icon(win.id());
        roll.twoPulls = win.twoPulls();
        roll.topStage = win.topStage();
        int stage = own.gainStage(clazz, win.id());
        if (stage >= win.topStage()) {
            roll.duplicate = true;
            roll.stage = stage;
            roll.points = win.rarity().duplicatePoints();
            own.award(roll.points);
        } else {
            roll.stage = stage + 1;
            own.setGainStage(clazz, win.id(), roll.stage);
        }
        roll.infernite = false;   // infernite is gone, from level 20 on the crates bring nothing extra (29 October 2026)
        // The band: random cards around the winner, which stops under the marker.
        int length = 40;
        roll.winIndex = 32;
        for (int i = 0; i < length; i++) {
            CrateGains.Gain g = i == roll.winIndex ? win : CrateGains.filler(clazz, way, sub, weights, random);
            roll.band.add(new CrateRoll.Slot(g.id(), g.rarity().name(), CrateGains.icon(g.id())));
        }
        for (CrateGains.Gain g : CrateGains.forCollection(clazz)) {
            roll.total++;
            if (g.level(own.gainStage(clazz, g.id())) > 0 || (g.consumable() && own.gainStage(clazz, g.id()) > 0)) {
                roll.collected++;
            }
        }
        return roll;
    }

    /** The rarity colour, for chat and windows. */
    public static int color(String rarity) {
        return CrateRarity.byName(rarity).color();
    }
}
