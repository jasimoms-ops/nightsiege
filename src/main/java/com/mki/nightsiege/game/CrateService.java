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
        ItemStack stack = new ItemStack(CRATE);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString("crate_class", clazz == null ? "" : clazz.name());
            tag.putString("crate_kind", kind.name());
            tag.putInt("crate_level", level);
        });
        List<Component> lines = new ArrayList<>();
        if (clazz != null) {
            lines.add(Component.translatable(clazz.translationKey()).withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("nightsiege.crate.kind." + kind.key()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("nightsiege.crate.level", level).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("nightsiege.crate.hint").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    /**
     * After a won night: one crate for every defender, for the class they played, and with
     * the chance {@code bonusCrateChance} a second one (Simon, 27 October 2026).
     */
    public void award(MinecraftServer server, Set<UUID> defenders, CrateKind kind, int level) {
        SiegeConfig cfg = config.get();
        double bonus = cfg == null ? 0.0 : cfg.bonusCrateChance;
        for (UUID id : defenders) {
            int count = java.util.concurrent.ThreadLocalRandom.current().nextDouble() < bonus ? 2 : 1;
            for (int i = 0; i < count; i++) {
                awardOne(server, id, kind, level);
            }
        }
        persist.run();
    }

    private void awardOne(MinecraftServer server, UUID id, CrateKind kind, int level) {
        {
            PlayerSkills own = save.get().skillsOf(id.toString());
            SkillClass clazz = own.clazz();
            if (clazz == null) {
                return;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                give(player, crate(clazz, kind, level));
            } else {
                // Offline: the crate waits in the save until they come back.
                Map<String, List<String>> pending = save.get().pendingCrates;
                if (pending == null) {
                    save.get().pendingCrates = pending = new java.util.HashMap<>();
                }
                pending.computeIfAbsent(id.toString(), k -> new ArrayList<>())
                        .add(clazz.name() + "|" + kind.name() + "|" + level);
            }
        }
    }

    /** Crates won while the player was away. */
    public void deliverPending(ServerPlayer player) {
        Map<String, List<String>> pending = save.get().pendingCrates;
        List<String> list = pending == null ? null : pending.remove(player.getUUID().toString());
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
            give(player, crate(SkillClass.byName(parts[0]), CrateKind.byName(parts[1]), level));
        }
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
        CrateKind kind = CrateKind.byName(tag.getStringOr("crate_kind", "NORMAL"));
        int level = Math.max(1, tag.getIntOr("crate_level", 1));
        CrateRoll roll = roll(own, clazz, kind, level);
        stack.shrink(1);
        if (roll.infernite && InferniteContent.INGOT != null) {
            player.getInventory().placeItemBackInInventory(new ItemStack(InferniteContent.INGOT));
        }
        persist.run();
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

    /** Rolls a crate and applies the result to the player's skills. Public for tests. */
    public CrateRoll roll(PlayerSkills own, SkillClass clazz, CrateKind kind, int level) {
        SiegeConfig cfg = config.get();
        String way = own.hasUnlocked(clazz) ? own.as(clazz, () -> own.pathTaken(tree)) : "";
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
        int[] weights = cfg.crateOdds(kind);
        roll.odds = weights.clone();
        CrateGains.Gain win = CrateGains.roll(clazz, way, weights, cfg.crateClassShare, random);
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
        roll.infernite = level >= cfg.infernitCrateLevel && random.nextDouble() < cfg.infernitCrateChance;
        // The band: random cards around the winner, which stops under the marker.
        int length = 40;
        roll.winIndex = 32;
        for (int i = 0; i < length; i++) {
            CrateGains.Gain g = i == roll.winIndex ? win : CrateGains.filler(clazz, way, weights, random);
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
