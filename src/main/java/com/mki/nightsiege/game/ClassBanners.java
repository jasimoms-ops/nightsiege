package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SkillClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class banners (update 6, Simon 4 November 2026: "es gibt einfach nur Klassenbanner, so dass es nur
 * 5 Designs gibt"; copying them is shut, 23:15). An ordinary Minecraft banner in the class's dye with
 * two patterns of the mod on it: the dark one (tinted black) and the gold one (tinted yellow), drawn
 * by tools/hud/class_banner.py. It comes with the first class and every further class, and once a
 * Minecraft day from the class card in the core window.
 */
public final class ClassBanners {

    private ClassBanners() {
    }

    /** The class's banner dye: Techniker brown, because gold on orange disappears. */
    public static DyeColor dye(SkillClass clazz) {
        return switch (clazz) {
            case WARRIOR -> DyeColor.RED;
            case GUARDIAN -> DyeColor.BLUE;
            case ARCHER -> DyeColor.GREEN;
            case ENGINEER -> DyeColor.BROWN;
            case SUMMONER -> DyeColor.CYAN;
            default -> DyeColor.WHITE;
        };
    }

    private static Item item(SkillClass clazz) {
        return switch (clazz) {
            case WARRIOR -> Items.BANNER.red();
            case GUARDIAN -> Items.BANNER.blue();
            case ARCHER -> Items.BANNER.green();
            case ENGINEER -> Items.BANNER.brown();
            case SUMMONER -> Items.BANNER.cyan();
            default -> Items.BANNER.white();
        };
    }

    /** "warrior", "archer" … as in the textures. */
    public static String id(SkillClass clazz) {
        return clazz.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static ResourceKey<BannerPattern> pattern(SkillClass clazz, String layer) {
        return ResourceKey.create(Registries.BANNER_PATTERN,
                Identifier.fromNamespaceAndPath(NightSiegeMod.MOD_ID, "crest_" + id(clazz) + "_" + layer));
    }

    /** The banner of a class, or an empty stack when the patterns are missing. */
    public static ItemStack create(SkillClass clazz, HolderLookup.Provider registries) {
        if (clazz == null || clazz.isShared() || registries == null) {
            return ItemStack.EMPTY;
        }
        try {
            HolderGetter<BannerPattern> patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
            BannerPatternLayers layers = new BannerPatternLayers.Builder()
                    .add(patterns.getOrThrow(pattern(clazz, "dark")), DyeColor.BLACK)
                    .add(patterns.getOrThrow(pattern(clazz, "gold")), DyeColor.YELLOW)
                    .build();
            ItemStack banner = new ItemStack(item(clazz));
            banner.set(DataComponents.BANNER_PATTERNS, layers);
            banner.set(DataComponents.ITEM_NAME, Component.translatable("nightsiege.banner.name",
                    Component.translatable(clazz.translationKey())).withStyle(ChatFormatting.GOLD));
            return banner;
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Klassenbanner {}: Muster fehlen ({})", clazz, e.toString());
            return ItemStack.EMPTY;
        }
    }

    /** True for a banner (or shield) that carries a pattern of the mod: it may not be copied. */
    public static boolean isClassBanner(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        BannerPatternLayers layers = stack.get(DataComponents.BANNER_PATTERNS);
        if (layers == null) {
            return false;
        }
        for (BannerPatternLayers.Layer layer : layers.layers()) {
            boolean ours = layer.pattern().unwrapKey()
                    .map(k -> k.identifier().getNamespace().equals(NightSiegeMod.MOD_ID)).orElse(false);
            if (ours) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ handing out

    private static long today(MinecraftServer server) {
        return SiegeMath.dayOf(server.overworld().getOverworldClockTime());
    }

    /** True when the player fetched this class's banner today already. */
    public static boolean takenToday(SiegeManager m, ServerPlayer player, SkillClass clazz) {
        Map<String, Map<String, Long>> days = m.save().bannerDays;
        if (days == null || player == null || clazz == null) {
            return false;
        }
        Long day = days.getOrDefault(player.getUUID().toString(), Map.of()).get(clazz.name());
        return day != null && day == today(player.level().getServer());
    }

    /** The button on the class card: once a Minecraft day, for own classes only. */
    public static void request(SiegeManager m, ServerPlayer player, String className) {
        SkillClass clazz = SkillClass.byName(className);
        PlayerSkills skills = m.save().skillsOf(player.getUUID().toString());
        if (clazz == null || clazz.isShared() || skills == null || !skills.hasUnlocked(clazz)) {
            player.sendSystemMessage(Component.translatable("nightsiege.banner.not_own").withStyle(ChatFormatting.RED));
            return;
        }
        if (takenToday(m, player, clazz)) {
            player.sendSystemMessage(Component.translatable("nightsiege.banner.wait").withStyle(ChatFormatting.RED));
            return;
        }
        give(m, player, clazz);
    }

    /** A new class (the first, or one opened after an ultimate at level 3): its banner comes with it. */
    public static void onNewClass(SiegeManager m, ServerPlayer player, SkillClass clazz) {
        give(m, player, clazz);
    }

    private static void give(SiegeManager m, ServerPlayer player, SkillClass clazz) {
        if (waits(m, player)) {
            // The kit (or a playtest) is on: its inventory goes back at the end, the banner would go with it.
            Map<String, List<String>> pending = m.save().pendingBanners;
            if (pending == null) {
                m.save().pendingBanners = pending = new HashMap<>();
            }
            List<String> mine = pending.computeIfAbsent(player.getUUID().toString(), k -> new ArrayList<>());
            if (!mine.contains(clazz.name())) {
                mine.add(clazz.name());
            }
            markDay(m, player, clazz);
            m.persistShortly();
            player.sendSystemMessage(Component.translatable("nightsiege.banner.after_kit").withStyle(ChatFormatting.GOLD));
            return;
        }
        ItemStack banner = create(clazz, player.level().registryAccess());
        if (banner.isEmpty()) {
            return;
        }
        player.getInventory().placeItemBackInInventory(banner);
        markDay(m, player, clazz);
        m.persistShortly();
        player.sendSystemMessage(Component.translatable("nightsiege.banner.got",
                Component.translatable(clazz.translationKey())).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.0f);
        NightSiegeMod.LOGGER.info("[Night Siege] Klassenbanner {} an {}", clazz, player.getScoreboardName());
    }

    /** Banners that waited for the kit, the playtest or the respawn. */
    public static void deliverPending(SiegeManager m, ServerPlayer player) {
        Map<String, List<String>> pending = m.save().pendingBanners;
        if (pending == null || waits(m, player)) {
            return;
        }
        List<String> mine = pending.remove(player.getUUID().toString());
        if (mine == null || mine.isEmpty()) {
            return;
        }
        for (String name : mine) {
            SkillClass clazz = SkillClass.byName(name);
            ItemStack banner = create(clazz, player.level().registryAccess());
            if (!banner.isEmpty()) {
                player.getInventory().placeItemBackInInventory(banner);
                player.sendSystemMessage(Component.translatable("nightsiege.banner.got",
                        Component.translatable(clazz.translationKey())).withStyle(ChatFormatting.GOLD));
            }
        }
        m.persistShortly();
    }

    private static void markDay(SiegeManager m, ServerPlayer player, SkillClass clazz) {
        if (m.save().bannerDays == null) {
            m.save().bannerDays = new HashMap<>();
        }
        m.save().bannerDays.computeIfAbsent(player.getUUID().toString(), k -> new HashMap<>())
                .put(clazz.name(), today(player.level().getServer()));
    }

    private static boolean waits(SiegeManager m, ServerPlayer player) {
        return player.isDeadOrDying() || m.kits().hasStash(player) || m.playtest().takesPart(player.getUUID());
    }
}
