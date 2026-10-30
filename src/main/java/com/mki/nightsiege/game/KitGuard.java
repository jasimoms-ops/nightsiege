package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Night kit items stay with their player (Simon 29.10.: kit items put into a vase never came back).
 * No container slot of any menu takes them (with {@code KitSlotMixin} and {@code KitMenuMixin}: clicks,
 * shift-clicks, number keys, dragging, bundles), no block or entity takes them by right-click (decorated
 * pots, shelves, chiseled bookshelves, jukeboxes, lecterns, flower pots, campfires, brushable blocks; item
 * frames, armour stands, allays) and a dropped kit item vanishes ({@code PlayerDropMixin}). Inside the
 * player's own inventory they move freely. When a kit ends, kit items still found around the player are
 * taken away ({@link #sweepAround}).
 */
public final class KitGuard {

    /** How far around the player the end of a kit looks for kit items left behind. */
    public static final double SWEEP_RADIUS = 64.0;

    private KitGuard() {
    }

    public static void register() {
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                refusesBlock(player, world, hand, hit.getBlockPos()) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                refusesEntity(player, hand, entity) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    // ------------------------------------------------------------------
    // menus
    // ------------------------------------------------------------------

    /** True for a slot of any container but the player's own inventory (chest, furnace, ender chest, crafter ...). */
    public static boolean foreign(Slot slot) {
        return slot != null && !(slot.container instanceof Inventory);
    }

    /**
     * Whether a click would put a kit item into a foreign slot: placing the carried stack, the number
     * keys and the off-hand key (swap), or dragging the carried stack over it.
     */
    public static boolean refusesClick(AbstractContainerMenu menu, int slotId, int button, ContainerInput input, Player player) {
        if (menu == null || player == null || slotId < 0 || slotId >= menu.slots.size()) {
            return false;
        }
        Slot slot = menu.slots.get(slotId);
        if (!foreign(slot)) {
            return false;
        }
        if (input == ContainerInput.PICKUP) {
            return KitService.isKit(menu.getCarried());
        }
        if (input == ContainerInput.SWAP) {
            Inventory inv = player.getInventory();
            return button >= 0 && button < inv.getContainerSize() && KitService.isKit(inv.getItem(button));
        }
        if (input == ContainerInput.QUICK_CRAFT) {
            return AbstractContainerMenu.getQuickcraftHeader(button) == 1 && KitService.isKit(menu.getCarried());
        }
        return false;
    }

    /** True when a shift-click's target range holds a foreign slot. */
    public static boolean outsidePlayer(List<Slot> slots, int start, int end) {
        for (int i = Math.max(0, start); i < end && i < slots.size(); i++) {
            if (foreign(slots.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** A bundle (or any item that takes others when clicked) must not take a kit item, nor a kit item be put into one. */
    public static boolean refusesStacking(ItemStack clicked, ItemStack carried) {
        boolean kitCarried = KitService.isKit(carried);
        boolean kitClicked = KitService.isKit(clicked);
        return kitCarried && clicked != null && !clicked.isEmpty() && !kitClicked
                || kitClicked && carried != null && !carried.isEmpty() && !kitCarried;
    }

    /** After a click on the server: a kit item that still got into a foreign slot goes back to the player. */
    public static void sweepMenu(AbstractContainerMenu menu, Player player) {
        if (!(player instanceof ServerPlayer server) || menu == null) {
            return;
        }
        for (Slot slot : menu.slots) {
            if (foreign(slot) && KitService.isKit(slot.getItem())) {
                ItemStack back = slot.getItem().copy();
                slot.set(ItemStack.EMPTY);
                server.getInventory().placeItemBackInInventory(back);
            }
        }
    }

    // ------------------------------------------------------------------
    // right-click on blocks and entities
    // ------------------------------------------------------------------

    /** Blocks whose right-click puts the held item in without a menu (by id; flower pots and shelves below). */
    private static final java.util.Set<String> TAKING_BLOCKS = java.util.Set.of("decorated_pot", "chiseled_bookshelf",
            "jukebox", "lectern", "flower_pot", "campfire", "soul_campfire", "suspicious_sand", "suspicious_gravel");

    public static boolean refusesBlock(Player player, Level world, InteractionHand hand, BlockPos pos) {
        if (player == null || world == null || pos == null || player.isSpectator()) {
            return false;
        }
        String id = BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()).getPath();
        if (id.endsWith("_shelf")) {
            // A shelf swaps with the held item, a powered one with the whole hotbar row.
            return KitService.isKit(player.getItemInHand(hand)) || kitInHotbar(player);
        }
        if (!KitService.isKit(player.getItemInHand(hand))) {
            return false;
        }
        if (TAKING_BLOCKS.contains(id) || id.startsWith("potted_")) {
            return true;
        }
        // Anything else that holds items but opens no menu (a menu has its own slot rules).
        BlockEntity be = world.getBlockEntity(pos);
        return be instanceof Container && !(be instanceof MenuProvider);
    }

    private static boolean kitInHotbar(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9 && i < inv.getContainerSize(); i++) {
            if (KitService.isKit(inv.getItem(i))) {
                return true;
            }
        }
        return KitService.isKit(player.getOffhandItem());
    }

    public static boolean refusesEntity(Player player, InteractionHand hand, Entity entity) {
        if (player == null || entity == null || player.isSpectator() || !KitService.isKit(player.getItemInHand(hand))) {
            return false;
        }
        return entity instanceof ItemFrame || entity instanceof ArmorStand || entity instanceof Allay;
    }

    // ------------------------------------------------------------------
    // the safety net at the end of a kit
    // ------------------------------------------------------------------

    /**
     * Takes away the kit items still found around the player (and in his ender chest) when his kit
     * ends: in containers and campfires within {@link #SWEEP_RADIUS} blocks, in item frames, on armour
     * stands, in allays, in chest boats and minecarts, and lying on the ground. Returns how many stacks went.
     */
    public static int sweepAround(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        int removed = clear(player.getEnderChestInventory());
        BlockPos at = player.blockPosition();
        double r2 = SWEEP_RADIUS * SWEEP_RADIUS;
        int c = (int) Math.ceil(SWEEP_RADIUS / 16.0);
        int cx = at.getX() >> 4;
        int cz = at.getZ() >> 4;
        for (int x = cx - c; x <= cx + c; x++) {
            for (int z = cz - c; z <= cz + c; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    if (be == null || be.getBlockPos().distSqr(at) > r2) {
                        continue;
                    }
                    int n = 0;
                    if (be instanceof Container box) {
                        n = clear(box);
                    } else if (be instanceof CampfireBlockEntity fire) {
                        List<ItemStack> items = fire.getItems();
                        for (int i = 0; i < items.size(); i++) {
                            if (KitService.isKit(items.get(i))) {
                                items.set(i, ItemStack.EMPTY);
                                n++;
                            }
                        }
                    }
                    if (n > 0) {
                        removed += n;
                        be.setChanged();
                        level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                    }
                }
            }
        }
        AABB box = new AABB(at).inflate(SWEEP_RADIUS);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, x -> x.distanceToSqr(player) <= r2)) {
            if (e instanceof ItemFrame frame && KitService.isKit(frame.getItem())) {
                frame.setItem(ItemStack.EMPTY);
                removed++;
            } else if (e instanceof ArmorStand stand) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (KitService.isKit(stand.getItemBySlot(slot))) {
                        stand.setItemSlot(slot, ItemStack.EMPTY);
                        removed++;
                    }
                }
            } else if (e instanceof Allay allay) {
                if (KitService.isKit(allay.getItemInHand(InteractionHand.MAIN_HAND))) {
                    allay.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    removed++;
                }
                removed += clear(allay.getInventory());
            } else if (e instanceof Container carrier && !(e instanceof Player)) {
                removed += clear(carrier);   // chest boats and minecarts
            } else if (e instanceof ItemEntity drop && KitService.isKit(drop.getItem())) {
                drop.discard();
                removed++;
            }
        }
        return removed;
    }

    /** Empties the kit items of a container; returns how many stacks went. */
    private static int clear(Container box) {
        if (box == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            if (KitService.isKit(box.getItem(i))) {
                box.setItem(i, ItemStack.EMPTY);
                n++;
            }
        }
        if (n > 0) {
            box.setChanged();
        }
        return n;
    }
}
