package com.mki.nightsiege.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.CrateGains;
import com.mki.nightsiege.core.NightKit;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.TrapKind;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The night kit: when a night starts, a defender's own inventory is put away
 * (in the save, so nothing is lost on a crash) and the kit of the class they
 * play takes its place. After the night their own things come back, together
 * with the valuable drops they picked up; everything else of the night goes.
 *
 * <p>Kit items carry a mark: they never break, cannot be put into containers
 * and vanish when dropped.</p>
 */
public final class KitService {

    /** Mark in the custom data of every kit item. */
    public static final String KIT_TAG = "nightsiege_kit";

    private final Supplier<SiegeSave> save;
    private final Supplier<SiegeConfig> config;
    private final SkillTree tree;
    private final TrapService traps;
    private final Runnable persist;

    public KitService(Supplier<SiegeSave> save, Supplier<SiegeConfig> config, SkillTree tree, TrapService traps,
                      Runnable persist) {
        this.save = save;
        this.config = config;
        this.tree = tree;
        this.traps = traps;
        this.persist = persist;
    }

    // ------------------------------------------------------------------
    // marking
    // ------------------------------------------------------------------

    public static boolean isKit(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(KIT_TAG, false);
    }

    /** Marks an item as part of the kit: unbreakable, no glint, not to be stored. */
    public static ItemStack mark(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(KIT_TAG, true));
        // Shields keep their bar: it shows the shield energy (Simon 26.10.); kit items never wear anyway.
        if (stack.isDamageableItem() && !stack.has(DataComponents.BLOCKS_ATTACKS)) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        }
        if (stack.isEnchanted()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        }
        // Design update (29 October 2026): kit netherite has a violet rim (tools/armor/kit20_icons.py).
        String piece = stack.is(net.minecraft.world.item.Items.NETHERITE_HELMET) ? "helmet"
                : stack.is(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE) ? "chestplate"
                : stack.is(net.minecraft.world.item.Items.NETHERITE_LEGGINGS) ? "leggings"
                : stack.is(net.minecraft.world.item.Items.NETHERITE_BOOTS) ? "boots" : null;
        if (piece != null && !stack.has(DataComponents.ITEM_MODEL)) {
            stack.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath("nightsiege",
                    "kit/netherite_" + piece));
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // stash
    // ------------------------------------------------------------------

    public boolean hasStash(ServerPlayer player) {
        SiegeSave s = save.get();
        return s.kitStashes != null && s.kitStashes.containsKey(player.getUUID().toString());
    }

    /** Puts the own inventory away and hands out the kit. Nothing happens if the player already wears it. */
    public void equip(ServerPlayer player, int level) {
        if (!config.get().nightKits) {
            return;
        }
        List<ItemStack> carried = new ArrayList<>();
        if (!hasStash(player)) {
            SiegeSave.KitStash stash = new SiegeSave.KitStash();
            HolderLookup.Provider regs = player.level().registryAccess();
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (playtestCrate(stack)) {
                    // The playtest's crates stay at hand with the kit (Simon 29.10.: they vanished into the stash).
                    carried.add(stack.copy());
                    stash.items.add("");
                    continue;
                }
                String json = encode(stack, regs);
                if (!stack.isEmpty() && json.isEmpty()) {
                    // Something the save cannot hold: better no kit than a lost item.
                    player.sendSystemMessage(Component.translatable("nightsiege.kit.failed_item", stack.getHoverName())
                            .withStyle(net.minecraft.ChatFormatting.RED));
                    NightSiegeMod.LOGGER.warn("[Night Siege] Kit fuer {} nicht angelegt: {} laesst sich nicht speichern",
                            player.getScoreboardName(), BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    return;
                }
                stash.items.add(json);
            }
            if (save.get().kitStashes == null) {
                save.get().kitStashes = new java.util.HashMap<>();
            }
            save.get().kitStashes.put(player.getUUID().toString(), stash);
            persist.run();   // written before the inventory is touched
            inv.clearContent();
        } else {
            clearKit(player);
        }
        give(player, level);
        for (ItemStack crate : carried) {
            player.getInventory().placeItemBackInInventory(crate);
        }
        resync(player);
        player.sendSystemMessage(Component.translatable("nightsiege.kit.equipped").withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    /** Swaps the kit for that of another class (the class window before the night). */
    public void refit(ServerPlayer player, int level) {
        if (!hasStash(player)) {
            return;
        }
        rememberLayout(player);
        clearKit(player);
        give(player, level);
    }

    /** Takes the kit away and gives the own inventory back, with the valuable drops of the night. */
    public void restore(ServerPlayer player) {
        SiegeSave s = save.get();
        String id = player.getUUID().toString();
        SiegeSave.KitStash stash = s.kitStashes == null ? null : s.kitStashes.get(id);
        if (stash == null) {
            return;
        }
        if (Companions.get() != null) {
            Companions.get().endFight(player);   // the souls of a camp or dungeon fight do not carry into the night
        }
        GainGear.removeBanners(player);   // the kit's war banner goes with the kit
        if (stash.gameMode != null && !stash.gameMode.isEmpty()) {
            GameType mode = GameType.byName(stash.gameMode, GameType.SURVIVAL);
            player.setGameMode(mode);
        }
        rememberLayout(player);   // the next kit of this class comes sorted the same way
        GainGear.emptyQuiver(player);
        GainKeys.newKit(player);
        Inventory inv = player.getInventory();
        List<ItemStack> keep = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && !isKit(stack) && valuable(stack)) {
                keep.add(stack.copy());
            }
        }
        inv.clearContent();
        HolderLookup.Provider regs = player.level().registryAccess();
        List<String> broken = new ArrayList<>(stash.broken == null ? List.of() : stash.broken);
        for (int i = 0; i < stash.items.size() && i < inv.getContainerSize(); i++) {
            String json = stash.items.get(i);
            if (json == null || json.isEmpty()) {
                continue;
            }
            ItemStack stack = decode(json, regs);
            if (stack.isEmpty()) {
                broken.add(json);
            } else {
                inv.setItem(i, stack);
            }
        }
        for (ItemStack stack : keep) {
            inv.placeItemBackInInventory(stack);
        }
        resync(player);
        if (broken.isEmpty()) {
            s.kitStashes.remove(id);
        } else {
            // Never throw an item away: what could not be read stays in the save.
            NightSiegeMod.LOGGER.warn("[Night Siege] {} Gegenstände von {} konnten nicht zurückgegeben werden",
                    broken.size(), player.getScoreboardName());
            stash.items = new ArrayList<>();
            stash.broken = broken;
            stash.gameMode = "";
        }
        persist.run();
        if (!keep.isEmpty()) {
            player.sendSystemMessage(Component.translatable("nightsiege.kit.loot", keep.size())
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
        player.sendSystemMessage(Component.translatable("nightsiege.kit.returned").withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    /** Notes the game mode a fallen defender had, so the night's end puts it back. */
    public void rememberGameMode(ServerPlayer player) {
        SiegeSave.KitStash stash = save.get().kitStashes == null ? null : save.get().kitStashes.get(player.getUUID().toString());
        if (stash != null && (stash.gameMode == null || stash.gameMode.isEmpty())) {
            stash.gameMode = player.gameMode().getName();
            persist.run();
        }
    }

    /** Removes kit items from the inventory, keeping everything else. */
    private void clearKit(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isKit(stack) || isTrapOrRemote(stack)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static boolean isTrapOrRemote(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ModContent.REMOTE != null && stack.is(ModContent.REMOTE)) {
            return true;
        }
        for (Item item : ModContent.TRAP_ITEMS.values()) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    /** True for drops that may leave the night: the listed items and armour or weapons of diamond or better. */
    /** A crate of the playtest: it goes with the player, kit or not. */
    static boolean playtestCrate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr("crate_playtest", false);
    }

    public boolean valuable(ItemStack stack) {
        if (playtestCrate(stack)) {
            return true;   // unopened playtest crates come back with the own things
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        SiegeConfig cfg = config.get();
        if (cfg.valuableDrops != null && cfg.valuableDrops.contains(id.toString())) {
            return true;
        }
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("nemesis_trophy")) {
            return true;   // a nemesis's trophy (update 2) always goes home
        }
        if (!cfg.keepDiamondGear) {
            return false;
        }
        String path = id.getPath();
        boolean material = path.startsWith("diamond_") || path.startsWith("netherite_") || path.startsWith("infernite_");
        boolean gear = stack.has(DataComponents.EQUIPPABLE) || stack.has(DataComponents.WEAPON)
                || stack.has(DataComponents.TOOL);
        return material && gear;
    }

    // ------------------------------------------------------------------
    // the kit itself
    // ------------------------------------------------------------------

    /** The pieces of one kit: armour (head to feet), the off hand (or empty) and everything else. */
    public record Kit(ItemStack[] armor, ItemStack offhand, List<ItemStack> items) {
    }

    /** Hands out the kit of the class the player plays, at the night's level, with the crate gains. */
    private void give(ServerPlayer player, int level) {
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        SkillClass clazz = skills.clazz();
        Inventory inv = player.getInventory();
        Kit kit = build(player, skills, level);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            player.setItemSlot(slots[i], mark(kit.armor()[i]));
        }
        if (!kit.offhand().isEmpty()) {
            player.setItemSlot(EquipmentSlot.OFFHAND, mark(kit.offhand()));
        }
        for (ItemStack stack : kit.items()) {
            inv.placeItemBackInInventory(mark(stack));
        }
        if (clazz == SkillClass.SUMMONER && Companions.get() != null) {
            Companions.get().beginNight(player, skills);   // the souls the staff shows are the ones the night starts with
        }
        // Balance update 2 (Simon 29.10.): the arrow kinds in the quiver, the gain abilities on their keys.
        GainGear.fillQuiver(player, this, skills);
        GainKeys.newKit(player);

        // Traps and the remote, as before (topped up to the free amount).
        if (clazz == SkillClass.ENGINEER) {
            traps.giveFreeTraps(player);
            // Crate gain "Notfall-Mörser" (once the emergency mine): one more mortar tower tonight.
            if (gainLevel(skills, clazz, "notfallmine") > 0 && ModContent.TRAP_ITEMS.get(TrapKind.MORTAR) != null
                    && tree.trapTier(TrapKind.MORTAR, skills) > 0) {
                inv.placeItemBackInInventory(new ItemStack(ModContent.TRAP_ITEMS.get(TrapKind.MORTAR), 1));
            }
        } else {
            traps.giveRemote(player, skills);
        }
        applyLayout(player, clazz);
        resync(player);
    }

    /**
     * Sends the whole inventory to the client again. placeItemBackInInventory tells the client at once
     * where a stack landed, but not the menu's record of what the client has; when applyLayout then moved
     * the stack to its remembered slot, the old slot looked unchanged to the server and the client kept
     * a ghost copy there (Simon 29.10.: "beim nächsten Kit dupliziert").
     */
    private static void resync(ServerPlayer player) {
        player.inventoryMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.sendAllDataToRemote();
        }
    }

    // ------------------------------------------------------------------
    // the kit's order (Simon 29.10.: "sort the kit inventory once, not anew every round")
    // ------------------------------------------------------------------

    /** Slots of the main inventory (hotbar 0-8, the rest 9-35). */
    private static final int MAIN_SLOTS = 36;

    /** What an item of the kit is, for its place: the item, its gadget, a potion's name. */
    static String layoutKey(ItemStack stack) {
        StringBuilder key = new StringBuilder(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        String gadget = Gadgets.of(stack);
        if (gadget != null && !gadget.isEmpty()) {
            key.append('|').append(gadget);
        }
        if (stack.has(DataComponents.POTION_CONTENTS)) {
            key.append('|').append(stack.getHoverName().getString());
        }
        return key.toString();
    }

    private static boolean kitThing(ItemStack stack) {
        return !stack.isEmpty() && (isKit(stack) || isTrapOrRemote(stack));
    }

    /** Notes where the player keeps each kit item now (several stacks of one item: in their order). */
    private void rememberLayout(ServerPlayer player) {
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        SkillClass clazz = skills == null ? null : skills.clazz();
        if (clazz == null) {
            return;
        }
        Inventory inv = player.getInventory();
        Map<String, Integer> layout = new java.util.LinkedHashMap<>();
        Map<String, Integer> seen = new java.util.HashMap<>();
        for (int i = 0; i < MAIN_SLOTS && i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!kitThing(stack)) {
                continue;
            }
            String key = layoutKey(stack);
            int n = seen.merge(key, 1, Integer::sum);
            layout.put(n == 1 ? key : key + "#" + n, i);
        }
        if (layout.isEmpty()) {
            return;
        }
        if (save.get().kitLayouts == null) {
            save.get().kitLayouts = new java.util.HashMap<>();
        }
        save.get().kitLayouts.computeIfAbsent(player.getUUID().toString(), k -> new java.util.HashMap<>())
                .put(clazz.name(), layout);
    }

    /** Puts the new kit's items where the player kept them last time; new things fill the free slots. */
    private void applyLayout(ServerPlayer player, SkillClass clazz) {
        Map<String, Map<String, Integer>> mine = save.get().kitLayouts == null || clazz == null ? null
                : save.get().kitLayouts.get(player.getUUID().toString());
        Map<String, Integer> layout = mine == null ? null : mine.get(clazz.name());
        if (layout == null || layout.isEmpty()) {
            return;
        }
        Inventory inv = player.getInventory();
        List<ItemStack> kit = new ArrayList<>();
        for (int i = 0; i < MAIN_SLOTS && i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (kitThing(stack)) {
                kit.add(stack);
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        List<ItemStack> rest = new ArrayList<>();
        Map<String, Integer> seen = new java.util.HashMap<>();
        for (ItemStack stack : kit) {
            String key = layoutKey(stack);
            int n = seen.merge(key, 1, Integer::sum);
            Integer slot = layout.get(n == 1 ? key : key + "#" + n);
            if (slot != null && slot >= 0 && slot < MAIN_SLOTS && inv.getItem(slot).isEmpty()) {
                inv.setItem(slot, stack);
            } else {
                rest.add(stack);
            }
        }
        for (ItemStack stack : rest) {
            inv.placeItemBackInInventory(stack);
        }
    }

    /**
     * The kit a player would get tonight with the class they play (see {@link PlayerSkills#as}),
     * for the class window: every item as JSON, the engineer's traps and remote included.
     */
    public List<String> preview(ServerPlayer player, PlayerSkills skills, int level) {
        List<ItemStack> all = new ArrayList<>();
        Kit kit = build(player, skills, level);
        all.addAll(java.util.Arrays.asList(kit.armor()));
        if (!kit.offhand().isEmpty()) {
            all.add(kit.offhand());
        }
        all.addAll(kit.items());
        if (skills.clazz() == SkillClass.ENGINEER) {
            for (java.util.Map.Entry<TrapKind, Integer> e : traps.freeTrapCounts(skills).entrySet()) {
                Item trap = ModContent.TRAP_ITEMS.get(e.getKey());
                if (trap != null && e.getValue() > 0) {
                    all.add(new ItemStack(trap, e.getValue()));
                }
            }
            if (ModContent.REMOTE != null) {
                all.add(new ItemStack(ModContent.REMOTE));
            }
        }
        // Same items stack together (the potions, arrows in several stacks).
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : all) {
            boolean joined = false;
            for (ItemStack have : merged) {
                if (ItemStack.isSameItemSameComponents(have, stack)) {
                    have.grow(stack.getCount());
                    joined = true;
                    break;
                }
            }
            if (!joined) {
                merged.add(stack.copy());
            }
        }
        List<String> out = new ArrayList<>();
        for (ItemStack stack : merged) {
            String json = encode(mark(stack), player.level().registryAccess());
            if (!json.isEmpty()) {
                out.add(json);
            }
        }
        return out;
    }

    /** Builds the kit of the class the player plays, without handing anything out. */
    private Kit build(ServerPlayer player, PlayerSkills skills, int level) {
        SkillClass clazz = skills.clazz();
        String way = skills.pathTaken(tree);
        if (way.isEmpty()) {
            // No way learned yet (a new class, the playtest): the way of the subclass chosen with the
            // class decides, so a warrior carries one melee weapon only (content update, 29 October 2026).
            String planned = tree.wayOfSubclass(clazz, tree.subclassOfProfile(skills, skills.chosenClass));
            way = planned == null ? "" : planned;
        }
        HolderLookup.RegistryLookup<Enchantment> ench = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack[] armor = new ItemStack[4];
        ItemStack offhand = ItemStack.EMPTY;

        // Armour.
        int protection = NightKit.plusOne(NightKit.protection(level), gainLevel(skills, clazz, "schutz"), 4);
        int projectile = gainLevel(skills, clazz, "projektilschutz") > 0 ? protection : 0;
        boolean belt = gainLevel(skills, clazz, "kriegsgurt") > 0;
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < 4; i++) {
            NightKit.Material material = NightKit.armor(level, i);
            if (clazz == SkillClass.ENGINEER && material.ordinal() > NightKit.Material.DIAMOND.ordinal()) {
                material = NightKit.Material.DIAMOND;   // the engineer wears diamond at most (Simon, 25 October 2026)
            }
            ItemStack piece = new ItemStack(item(material, pieces[i]));
            enchant(piece, ench, Enchantments.PROTECTION, protection);
            if (projectile > 0) {
                enchant(piece, ench, Enchantments.PROJECTILE_PROTECTION, projectile);
            }
            if (belt) {
                addAttribute(piece, Attributes.ARMOR, "kit_belt_" + i, 1.0,
                        new EquipmentSlotGroup[]{EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST,
                                EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET}[i]);
            }
            if (material == NightKit.Material.INFERNITE) {
                // From level 20 on: netherite with the old infernite numbers (content update, 29 October 2026):
                // chest 9 and legs 7 instead of 8 and 6, toughness 3.5, knockback resistance 0.15.
                EquipmentSlotGroup group = new EquipmentSlotGroup[]{EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST,
                        EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET}[i];
                if (i == 1 || i == 2) {
                    addAttribute(piece, Attributes.ARMOR, "kit_tier20_armor_" + i, 1.0, group);
                }
                addAttribute(piece, Attributes.ARMOR_TOUGHNESS, "kit_tier20_tough_" + i, 0.5, group);
                addAttribute(piece, Attributes.KNOCKBACK_RESISTANCE, "kit_tier20_kb_" + i, 0.05, group);
            }
            if (i == 3 && gainLevel(skills, clazz, "sprungstiefel") > 0) {
                Gadgets.setFlag(piece, Gadgets.DOUBLE_JUMP);   // jump boots: a second jump in the air
            }
            armor[i] = piece;
        }

        NightKit.Material weapon = NightKit.weapon(level);
        int sharp = NightKit.plusOne(NightKit.sharpness(level), gainLevel(skills, clazz, "schaerfe"), 5);
        List<ItemStack> hotbar = new ArrayList<>();
        if (clazz == SkillClass.WARRIOR) {
            boolean might = way.equals(SkillTree.WAY_MIGHT);
            boolean blade = !might;   // one melee weapon: the axe for might, else the sword
            // The spare weapon (axe and sword) left the crates (Simon, 27 October 2026).
            if (blade) {
                // The blademaster's combo (no cooldown) comes with an iron sword at most.
                NightKit.Material swordMaterial = SkillTree.has(skills, "w_combo") ? NightKit.Material.IRON : weapon;
                hotbar.add(warriorWeapon(skills, clazz, ench, item(swordMaterial, "sword"), sharp, true));
            }
            if (might) {
                hotbar.add(warriorWeapon(skills, clazz, ench, item(weapon, "axe"), sharp, false));
            }
        } else if (clazz == SkillClass.ARCHER) {
            boolean crossbow = way.equals(SkillTree.WAY_CROSSBOW);
            // The archer's spare weapon (bow and crossbow) left the crates (Simon 26.10.).
            if (!crossbow || way.isEmpty()) {
                ItemStack bow = new ItemStack(weapon == NightKit.Material.INFERNITE ? gear("infernite_bow", Items.BOW) : Items.BOW);
                String sub = tree.subclassOf(skills);
                // Playtest 25 October 2026 (Simon): the heavy shot gets one more power level,
                // the quick shot never runs out of arrows.
                int power = NightKit.plusOne(NightKit.sharpness(level), gainLevel(skills, clazz, "staerke"), 5);
                if (SkillTree.SUB_HEAVY.equals(sub)) {
                    power = Math.min(6, power + (int) com.mki.nightsiege.core.SkillValues.get("heavy_draw", "power", 1));
                }
                if (SkillTree.SUB_SWIFT.equals(sub)) {
                    power = 0;   // the quick shot: no power on the bow (Simon, 25 October 2026)
                }
                enchant(bow, ench, Enchantments.POWER, power);
                // Every archer bow never runs out of arrows (Simon, 26 October 2026; was the quick shot only).
                enchant(bow, ench, Enchantments.INFINITY, 1);
                enchant(bow, ench, Enchantments.PUNCH, NightKit.bowPunch(level));
                enchant(bow, ench, Enchantments.FLAME, NightKit.bowFlame(level));
                if (gainLevel(skills, clazz, "zielfernrohr") > 0) {
                    Gadgets.setFlag(bow, Gadgets.SCOPE);
                }
                hotbar.add(bow);
            }
            if (crossbow || way.isEmpty()) {
                ItemStack xbow = new ItemStack(weapon == NightKit.Material.INFERNITE
                        ? gear("infernite_crossbow", Items.CROSSBOW) : Items.CROSSBOW);
                int quick = NightKit.plusOne(NightKit.quickCharge(level), gainLevel(skills, clazz, "schnellladen"), 3);
                if (quick > 0) {
                    enchant(xbow, ench, Enchantments.QUICK_CHARGE, quick);
                }
                enchant(xbow, ench, Enchantments.PIERCING, NightKit.crossbowPiercing(level));
                // The crossbow too (Simon, 26 October 2026): Infinity works on any weapon that loads arrows.
                enchant(xbow, ench, Enchantments.INFINITY, 1);
                if (gainLevel(skills, clazz, "zielfernrohr") > 0) {
                    Gadgets.setFlag(xbow, Gadgets.SCOPE);
                }
                hotbar.add(xbow);
            }
            // No second sword for the archer any more (Simon, 27 October 2026).
            // Köcherfüllung gives arrow damage since every bow has Infinity (26 October 2026).
            int arrows = NightKit.archerArrows(level);
            hotbar.addAll(stacks(Items.ARROW, arrows));
            // The spectral arrows (and every other arrow kind) are shots in the quiver now (balance update 2).
        } else if (clazz == SkillClass.GUARDIAN) {
            hotbar.add(sword(ench, weapon, sharp));
            ItemStack shield = new ItemStack(weapon == NightKit.Material.INFERNITE ? gear("infernite_shield", Items.SHIELD) : Items.SHIELD);
            GainGear.decorateShield(shield, gainLevel(skills, clazz, "turmschild") > 0, gainLevel(skills, clazz, "leichtschild") > 0);
            ClassShields.decorate(shield, tree.subclassOf(skills), player.level().registryAccess());   // the subclass's colours (27 October 2026)
            offhand = shield;
            int potions = (way.equals(SkillTree.WAY_BLESSING) ? 2 : 0) + gainLevel(skills, clazz, "verbandskasten");
            for (int i = 0; i < potions; i++) {
                hotbar.add(PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING));
            }
        } else if (clazz == SkillClass.SUMMONER) {
            // The staff (the summoner's own attack, orders, calls and spells) and no sword (Simon,
            // 27 October 2026); food for the wolves or iron for the golem. Nothing here adds a companion.
            if (ModContent.STAFF != null) {
                ItemStack staff = new ItemStack(ModContent.STAFF);
                if (way.equals(SkillTree.WAY_DEAD)) {
                    // The way of the dead starts the night with three souls (the bar of the staff),
                    // more with bone dust and what the soul vessel kept from the last night.
                    int cap = Companions.soulCapOf(skills);
                    int start = Companions.startSouls(skills);
                    net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            staff, tag -> {
                                tag.putInt(SummonStaffItem.SOULS, start);
                                tag.putInt(SummonStaffItem.SOUL_CAP, cap);
                            });
                }
                hotbar.add(staff);
            }
            int bag = 8 * gainLevel(skills, clazz, "futterbeutel");   // Futterbeutel: more to heal with
            if (SkillTree.has(skills, "s_golemsmith")) {
                hotbar.add(new ItemStack(Items.IRON_INGOT, 16 + bag));   // a golem mends with iron
            } else if (!way.equals(SkillTree.WAY_DEAD) || SkillTree.has(skills, "d_cross_wolf")) {
                hotbar.add(new ItemStack(Items.BEEF, 8 + bag));   // a wolf heals from meat
            }
        } else {
            // Engineer (and the fallback for a player without a class): the sword only. The bow and its
            // arrows left the kit (Simon, 27 October 2026); the towers have their own magazines.
            hotbar.add(sword(ench, weapon, Math.max(1, sharp - 1)));
        }

        // The subclass's own kit weapon: look and name on the main weapon (Simon, 27 October 2026).
        if (weapon == NightKit.Material.INFERNITE) {
            // Level 20: the netherite weapons hit one point harder (the old infernite gear).
            for (ItemStack stack : hotbar) {
                if (stack.is(net.minecraft.tags.ItemTags.SWORDS) || stack.is(net.minecraft.tags.ItemTags.AXES)
                        || stack.is(Items.MACE)) {
                    addAttribute(stack, Attributes.ATTACK_DAMAGE, "kit_tier20_damage", 1.0, EquipmentSlotGroup.MAINHAND);
                }
            }
        }
        applyKitLook(hotbar, tree.subclassOf(skills));

        // Food and the general gains.
        int foodTier = NightKit.foodTier(level, gainLevel(skills, clazz, "proviant"));
        Item food = foodTier == 0 ? Items.BREAD : foodTier == 1 ? Items.COOKED_BEEF : Items.GOLDEN_CARROT;
        hotbar.add(new ItemStack(food, 32));
        int apples = NightKit.provisionApples(level, gainLevel(skills, clazz, "proviant")) + gainLevel(skills, clazz, "notration");
        if (apples > 0) {
            hotbar.add(new ItemStack(Items.GOLDEN_APPLE, apples));
        }
        int heal = gainLevel(skills, clazz, "heiltrank");
        for (int i = 0; i < (heal >= 2 ? 3 : heal >= 1 ? 2 : 0); i++) {
            hotbar.add(PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING));
        }
        if (gainLevel(skills, clazz, "fackeln") > 0) {
            hotbar.add(new ItemStack(Items.TORCH, 16));
        }
        if (gainLevel(skills, clazz, "wurfperlen") > 0) {
            hotbar.add(new ItemStack(Items.ENDER_PEARL, 2));
        }
        // Building blocks for everyone to patch the walls (Simon, 25 October 2026); the building
        // box doubles them and adds ladders.
        boolean box = gainLevel(skills, clazz, "baukiste") > 0;
        hotbar.add(new ItemStack(Items.COBBLESTONE, box ? 64 : 32));
        if (box) {
            hotbar.add(new ItemStack(Items.LADDER, 8));
        }
        GainGear.addKitItems(this, skills, clazz, way, hotbar);
        // Wooden tools in every kit (Simon 29.10.): to dig, chop and clear; last, so they never push
        // the fighting things off the hotbar.
        // Only those the kit has no tool of yet (Simon 29.10.: "man hat eine Axt zu viel" – the axe
        // warriors and the throwing axes chop already).
        boolean axe = false;
        boolean pick = false;
        boolean shovel = false;
        for (ItemStack stack : hotbar) {
            axe |= stack.is(net.minecraft.tags.ItemTags.AXES);
            pick |= stack.is(net.minecraft.tags.ItemTags.PICKAXES);
            shovel |= stack.is(net.minecraft.tags.ItemTags.SHOVELS);
        }
        if (offhand != null && offhand.is(net.minecraft.tags.ItemTags.AXES)) {
            axe = true;
        }
        if (!pick) {
            hotbar.add(new ItemStack(Items.WOODEN_PICKAXE));
        }
        if (!axe) {
            hotbar.add(new ItemStack(Items.WOODEN_AXE));
        }
        if (!shovel) {
            hotbar.add(new ItemStack(Items.WOODEN_SHOVEL));
        }
        return new Kit(armor, offhand, hotbar);
    }

    private ItemStack warriorWeapon(PlayerSkills skills, SkillClass clazz, HolderLookup.RegistryLookup<Enchantment> ench,
                                    Item item, int sharp, boolean sword) {
        ItemStack stack = new ItemStack(item);
        enchant(stack, ench, Enchantments.SHARPNESS, sharp);
        if (gainLevel(skills, clazz, "feuerklinge") > 0) {
            enchant(stack, ench, Enchantments.FIRE_ASPECT, 2);
        }
        if (sword && gainLevel(skills, clazz, "schwung") > 0) {
            enchant(stack, ench, Enchantments.SWEEPING_EDGE, gainLevel(skills, clazz, "schwung"));
        }
        if (gainLevel(skills, clazz, "gestaehlt") > 0) {
            addAttribute(stack, Attributes.ATTACK_DAMAGE, "kit_steel", 1.0, EquipmentSlotGroup.MAINHAND);
        }
        if (gainLevel(skills, clazz, "zweihaender") > 0) {   // sword or axe (27 October 2026)
            addAttribute(stack, Attributes.ENTITY_INTERACTION_RANGE, "kit_greatsword", 1.5, EquipmentSlotGroup.MAINHAND);
        }
        return stack;
    }

    /** The subclass's kit weapon look on the kit's main weapon; only look and name change. */
    private static void applyKitLook(List<ItemStack> hotbar, String subclass) {
        com.mki.nightsiege.core.KitWeapons.Look look = com.mki.nightsiege.core.KitWeapons.of(subclass);
        if (look == null) {
            return;
        }
        for (ItemStack stack : hotbar) {
            if (kitLookFits(stack, look.kind())) {
                stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("nightsiege", "kit/" + look.key()));
                stack.set(DataComponents.ITEM_NAME, Component.translatable(look.nameKey()));
                return;
            }
        }
    }

    /** True if the stack is the kind of kit item a look goes on. */
    public static boolean kitLookFits(ItemStack stack, com.mki.nightsiege.core.KitWeapons.Kind kind) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return switch (kind) {
            case SWORD -> path.endsWith("_sword");
            case AXE -> path.endsWith("_axe");
            case BOW -> stack.getItem() instanceof net.minecraft.world.item.BowItem;
            case CROSSBOW -> stack.getItem() instanceof net.minecraft.world.item.CrossbowItem;
            case STAFF -> ModContent.STAFF != null && stack.is(ModContent.STAFF);
        };
    }

    private static ItemStack sword(HolderLookup.RegistryLookup<Enchantment> ench, NightKit.Material material, int sharp) {
        ItemStack stack = new ItemStack(item(material, "sword"));
        enchant(stack, ench, Enchantments.SHARPNESS, sharp);
        return stack;
    }

    private static List<ItemStack> stacks(Item item, int count) {
        List<ItemStack> out = new ArrayList<>();
        while (count > 0) {
            int n = Math.min(64, count);
            out.add(new ItemStack(item, n));
            count -= n;
        }
        return out;
    }

    public TrapService traps() {
        return traps;
    }

    /** The working level of a crate gain in this class's kit. */
    public int gainLevel(PlayerSkills skills, SkillClass clazz, String gainId) {
        CrateGains.Gain gain = CrateGains.get(gainId);
        if (gain == null || skills == null || clazz == null) {
            return 0;
        }
        return gain.level(skills.gainStage(clazz, gainId));
    }

    private static Item item(NightKit.Material material, String piece) {
        if (material == NightKit.Material.INFERNITE) {
            material = NightKit.Material.NETHERITE;   // infernite is gone: netherite with bonuses (29 October 2026)
        }
        String id = material.name().toLowerCase(java.util.Locale.ROOT) + "_" + piece;
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
    }

    private static Item gear(String id, Item fallback) {
        return fallback;   // the infernite bow, crossbow and shield are gone (29 October 2026)
    }

    private static void enchant(ItemStack stack, HolderLookup.RegistryLookup<Enchantment> ench,
                                ResourceKey<Enchantment> key, int level) {
        if (level <= 0) {
            return;
        }
        ench.get(key).ifPresent(holder -> stack.enchant(holder, level));
    }

    private static void addAttribute(ItemStack stack, Holder<Attribute> attribute, String name, double amount,
                                     EquipmentSlotGroup group) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, mods.withModifierAdded(attribute,
                new AttributeModifier(ModContent.id(name), amount, AttributeModifier.Operation.ADD_VALUE), group));
    }

    // ------------------------------------------------------------------
    // item <-> text
    // ------------------------------------------------------------------

    public static String encode(ItemStack stack, HolderLookup.Provider regs) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            return ItemStack.CODEC.encodeStart(regs.createSerializationContext(JsonOps.INSTANCE), stack)
                    .result().map(JsonElement::toString).orElse("");
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Gegenstand nicht speicherbar: {}", e.toString());
            return "";
        }
    }

    public static ItemStack decode(String json, HolderLookup.Provider regs) {
        if (json == null || json.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return ItemStack.CODEC.parse(regs.createSerializationContext(JsonOps.INSTANCE), element)
                    .result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }
}
