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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Set;

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
            "dungeon_compass");

    private RetiredItems() {
    }

    public static boolean retired(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
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
            }
        }
        if (gone > 0) {
            container.setChanged();
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
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
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
