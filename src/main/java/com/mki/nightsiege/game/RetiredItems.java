package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the content update took out of the game (Simon, 28/29 October 2026): infernite (ore,
 * scrap, ingots, gear, the upgrade template), the old dungeons' cores, maps and compasses and the
 * core perks' fragments. Their ids stay registered so old worlds load; the things themselves go
 * without replacement – from the inventory and the ender chest when a player logs in, from a
 * chest or barrel when it is opened, as an item on the ground when it loads. The ore turns into
 * netherrack when its chunk loads.
 */
public final class RetiredItems {

    private static final Set<String> OLD_DUNGEON = Set.of("dungeon_core", "dungeon_core_spent", "dungeon_map",
            "dungeon_compass", "remote_control");   // Tor 4b: the engineer's remote left the game

    private RetiredItems() {
    }

    /** Tor 5.7: the gadgets that left the kit (spears, axes; banner and barricades are passive or charges now). */
    private static final Set<String> GAIN_GADGETS = Set.of("wurfspeer", "wurfbeil", "kriegsbanner", "barrikade");
    /** Tor 5.7: kit stacks of these gains are charges on 1-3 now (or gone with their points). */
    private static final Set<String> GAIN_STACKS = Set.of("wurfspeere", "wurfbeile", "kriegsbanner", "barrikaden",
            "heiltrank", "verbandskasten", "wurfperlen");

    /**
     * Tor 5.7 (Simon 09.11.): a loot item of the old kits – a thrown or set gadget, a gain's stack, or a kit
     * potion, golden apple or ender pearl. They come as charges on the gain keys now. A player's own
     * potions and apples (no kit mark) stay.
     */
    static boolean oldLoot(ItemStack stack) {
        if (GAIN_GADGETS.contains(Gadgets.of(stack)) || GAIN_STACKS.contains(KitService.gainOf(stack))) {
            return true;
        }
        return KitService.isKit(stack) && (stack.is(net.minecraft.world.item.Items.POTION)
                || stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE) || stack.is(net.minecraft.world.item.Items.ENDER_PEARL));
    }

    public static boolean retired(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (oldLoot(stack)) {
            return true;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!NightSiegeMod.MOD_ID.equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path.startsWith("infernite_") || path.startsWith("core_fragment_") || OLD_DUNGEON.contains(path);
    }

    /** Takes every retired stack out of a container; returns how many stacks went. */
    public static int purge(Container container) {
        int gone = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (retired(container.getItem(i))) {
                container.setItem(i, ItemStack.EMPTY);
                gone++;
            } else {
                gone += purgeInside(container.getItem(i), 0);   // shulker boxes and bundles (Prüfer-Fund 4b)
            }
        }
        if (gone > 0) {
            container.setChanged();
        }
        return gone;
    }

    /**
     * Takes retired stacks out of what an item carries (a shulker box's contents, a bundle), nested
     * a few levels deep; returns how many stacks went. A broken component is left alone.
     */
    static int purgeInside(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty() || depth > 4) {
            return 0;
        }
        int gone = 0;
        try {
            ItemContainerContents box = stack.get(DataComponents.CONTAINER);
            if (box != null && box != ItemContainerContents.EMPTY) {
                List<ItemStack> items = box.allItemsCopyStream().collect(Collectors.toCollection(ArrayList::new));
                int here = 0;
                for (int i = 0; i < items.size(); i++) {
                    if (retired(items.get(i))) {
                        items.set(i, ItemStack.EMPTY);   // keep the slots in place
                        here++;
                    } else {
                        here += purgeInside(items.get(i), depth + 1);
                    }
                }
                if (here > 0) {
                    stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
                    gone += here;
                }
            }
            BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null && !bundle.isEmpty()) {
                List<ItemStackTemplate> kept = new ArrayList<>();
                int here = 0;
                for (ItemStack inner : bundle.itemCopyStream().toList()) {
                    if (retired(inner)) {
                        here++;
                        continue;
                    }
                    here += purgeInside(inner, depth + 1);
                    if (!inner.isEmpty()) {
                        kept.add(ItemStackTemplate.fromNonEmptyStack(inner));
                    }
                }
                if (here > 0) {
                    stack.set(DataComponents.BUNDLE_CONTENTS, kept.isEmpty() ? BundleContents.EMPTY : new BundleContents(kept));
                    gone += here;
                }
            }
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Inhalt von {} nicht geprüft: {}", stack, e.toString());
        }
        return gone;
    }

    /** At login: the inventory (armour and off hand included) and the ender chest. */
    public static void purgePlayer(ServerPlayer player) {
        int gone = purge(player.getInventory()) + purge(player.getEnderChestInventory());
        if (gone > 0) {
            NightSiegeMod.LOGGER.info("[Night Siege] {}: {} alte Gegenstände (Infernit, alte Dungeons) entfernt",
                    player.getScoreboardName(), gone);
        }
    }

    /** True for a retired item lying on the ground (it goes when it loads). */
    public static boolean strayDrop(Entity entity) {
        return entity instanceof ItemEntity drop && retired(drop.getItem());
    }

    public static void register() {
        // A chest, barrel or other container: emptied of retired things before it opens.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer) {
                BlockEntity be = world.getBlockEntity(hit.getBlockPos());
                if (be instanceof Container container) {
                    purge(container);
                }
            }
            return InteractionResult.PASS;
        });
        // After the load, not inside it: the blocks are set on the next server step.
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            if (InferniteContent.ORE != null && hasOre(chunk)) {
                level.getServer().execute(() -> oreToNetherrack(level, chunk));
            }
        });
    }

    private static boolean hasOre(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (section != null && !section.hasOnlyAir() && section.maybeHas(state -> state.is(InferniteContent.ORE))) {
                return true;
            }
        }
        return false;
    }

    /** The infernite ore of old worlds turns into netherrack (only sections that hold it are looked at). */
    private static void oreToNetherrack(ServerLevel level, LevelChunk chunk) {
        Block ore = InferniteContent.ORE;
        if (ore == null) {
            return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        for (int s = 0; s < sections.length; s++) {
            LevelChunkSection section = sections[s];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> state.is(ore))) {
                continue;
            }
            int baseY = level.getSectionYFromSectionIndex(s) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(ore)) {
                            BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y,
                                    chunk.getPos().getMinBlockZ() + z);
                            level.setBlock(pos, Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }
}
