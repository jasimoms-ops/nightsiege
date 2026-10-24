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
        if (stack.isDamageableItem()) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        }
        if (stack.isEnchanted()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
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
        if (!hasStash(player)) {
            SiegeSave.KitStash stash = new SiegeSave.KitStash();
            HolderLookup.Provider regs = player.level().registryAccess();
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                String json = encode(stack, regs);
                if (!stack.isEmpty() && json.isEmpty()) {
                    // Something the save cannot hold: better no kit than a lost item.
                    player.sendSystemMessage(Component.translatable("nightsiege.kit.failed")
                            .withStyle(net.minecraft.ChatFormatting.RED));
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
        player.sendSystemMessage(Component.translatable("nightsiege.kit.equipped").withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    /** Swaps the kit for that of another class (the class window before the night). */
    public void refit(ServerPlayer player, int level) {
        if (!hasStash(player)) {
            return;
        }
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
        if (stash.gameMode != null && !stash.gameMode.isEmpty()) {
            GameType mode = GameType.byName(stash.gameMode, GameType.SURVIVAL);
            player.setGameMode(mode);
        }
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
    public boolean valuable(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        SiegeConfig cfg = config.get();
        if (cfg.valuableDrops != null && cfg.valuableDrops.contains(id.toString())) {
            return true;
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

    /** Hands out the kit of the class the player plays, at the night's level, with the crate gains. */
    private void give(ServerPlayer player, int level) {
        PlayerSkills skills = save.get().skillsOf(player.getUUID().toString());
        SkillClass clazz = skills.clazz();
        String way = skills.pathTaken(tree);
        HolderLookup.RegistryLookup<Enchantment> ench = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Inventory inv = player.getInventory();

        // Armour.
        int protection = NightKit.plusOne(NightKit.protection(level), gainLevel(skills, clazz, "schutz"), 4);
        int projectile = gainLevel(skills, clazz, "projektilschutz") > 0 ? protection : 0;
        boolean belt = gainLevel(skills, clazz, "kriegsgurt") > 0;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < 4; i++) {
            ItemStack piece = new ItemStack(item(NightKit.armor(level, i), pieces[i]));
            enchant(piece, ench, Enchantments.PROTECTION, protection);
            if (projectile > 0) {
                enchant(piece, ench, Enchantments.PROJECTILE_PROTECTION, projectile);
            }
            if (belt) {
                addAttribute(piece, Attributes.ARMOR, "kit_belt_" + i, 1.0,
                        new EquipmentSlotGroup[]{EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST,
                                EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET}[i]);
            }
            player.setItemSlot(slots[i], mark(piece));
        }

        NightKit.Material weapon = NightKit.weapon(level);
        int sharp = NightKit.plusOne(NightKit.sharpness(level), gainLevel(skills, clazz, "schaerfe"), 5);
        List<ItemStack> hotbar = new ArrayList<>();
        if (clazz == SkillClass.WARRIOR) {
            boolean blade = way.isEmpty() || way.equals(SkillTree.WAY_BLADE);
            boolean might = way.isEmpty() || way.equals(SkillTree.WAY_MIGHT);
            boolean spare = gainLevel(skills, clazz, "ersatzwaffe_w") > 0;
            if (blade || spare) {
                hotbar.add(warriorWeapon(skills, clazz, ench, item(weapon, "sword"), sharp, true));
            }
            if (might || spare) {
                hotbar.add(warriorWeapon(skills, clazz, ench, item(weapon, "axe"), sharp, false));
            }
        } else if (clazz == SkillClass.ARCHER) {
            boolean crossbow = way.equals(SkillTree.WAY_CROSSBOW);
            boolean spare = gainLevel(skills, clazz, "ersatzwaffe_a") > 0;
            if (!crossbow || way.isEmpty() || spare) {
                ItemStack bow = new ItemStack(weapon == NightKit.Material.INFERNITE ? gear("infernite_bow", Items.BOW) : Items.BOW);
                enchant(bow, ench, Enchantments.POWER, NightKit.plusOne(NightKit.sharpness(level),
                        gainLevel(skills, clazz, "staerke"), 5));
                hotbar.add(bow);
            }
            if (crossbow || way.isEmpty() || spare) {
                ItemStack xbow = new ItemStack(weapon == NightKit.Material.INFERNITE
                        ? gear("infernite_crossbow", Items.CROSSBOW) : Items.CROSSBOW);
                int quick = NightKit.plusOne(NightKit.quickCharge(level), gainLevel(skills, clazz, "schnellladen"), 3);
                if (quick > 0) {
                    enchant(xbow, ench, Enchantments.QUICK_CHARGE, quick);
                }
                hotbar.add(xbow);
            }
            hotbar.add(sword(ench, weapon, Math.max(1, sharp - 2)));
            int arrows = NightKit.archerArrows(level) + 16 * gainLevel(skills, clazz, "koecherfuellung");
            hotbar.addAll(stacks(Items.ARROW, arrows));
            if (gainLevel(skills, clazz, "spektralpfeile") > 0) {
                hotbar.add(new ItemStack(Items.SPECTRAL_ARROW, 16));
            }
        } else if (clazz == SkillClass.GUARDIAN) {
            hotbar.add(sword(ench, weapon, sharp));
            ItemStack shield = new ItemStack(weapon == NightKit.Material.INFERNITE ? gear("infernite_shield", Items.SHIELD) : Items.SHIELD);
            player.setItemSlot(EquipmentSlot.OFFHAND, mark(shield));
            int potions = (way.equals(SkillTree.WAY_BLESSING) ? 2 : 0) + gainLevel(skills, clazz, "verbandskasten");
            for (int i = 0; i < potions; i++) {
                hotbar.add(PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING));
            }
        } else {
            // Engineer (and the fallback for a player without a class).
            hotbar.add(sword(ench, weapon, Math.max(1, sharp - 1)));
            hotbar.add(new ItemStack(Items.BOW));
            boolean mech = way.equals(SkillTree.WAY_MECHANICS);
            int arrows = NightKit.engineerArrows(level, mech) + (gainLevel(skills, clazz, "pfeilkiste") > 0 ? 32 : 0);
            hotbar.addAll(stacks(Items.ARROW, arrows));
        }

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
        if (gainLevel(skills, clazz, "baukiste") > 0) {
            hotbar.add(new ItemStack(Items.COBBLESTONE, 32));
            hotbar.add(new ItemStack(Items.LADDER, 8));
        }
        for (ItemStack stack : hotbar) {
            inv.placeItemBackInInventory(mark(stack));
        }

        // Traps and the remote, as before (topped up to the free amount).
        if (clazz == SkillClass.ENGINEER) {
            traps.giveFreeTraps(player);
            if (gainLevel(skills, clazz, "notfallmine") > 0 && ModContent.TRAP_ITEMS.get(TrapKind.MINE) != null
                    && tree.trapTier(TrapKind.MINE, skills) > 0) {
                inv.placeItemBackInInventory(new ItemStack(ModContent.TRAP_ITEMS.get(TrapKind.MINE), 1));
            }
        } else {
            traps.giveRemote(player, skills);
        }
    }

    private ItemStack warriorWeapon(PlayerSkills skills, SkillClass clazz, HolderLookup.RegistryLookup<Enchantment> ench,
                                    Item item, int sharp, boolean sword) {
        ItemStack stack = new ItemStack(item);
        enchant(stack, ench, Enchantments.SHARPNESS, sharp);
        int looting = gainLevel(skills, clazz, "pluenderung");
        if (looting > 0) {
            enchant(stack, ench, Enchantments.LOOTING, looting);
        }
        if (gainLevel(skills, clazz, "feuerklinge") > 0) {
            enchant(stack, ench, Enchantments.FIRE_ASPECT, 2);
        }
        if (sword && gainLevel(skills, clazz, "schwung") > 0) {
            enchant(stack, ench, Enchantments.SWEEPING_EDGE, gainLevel(skills, clazz, "schwung"));
        }
        if (gainLevel(skills, clazz, "gestaehlt") > 0) {
            addAttribute(stack, Attributes.ATTACK_DAMAGE, "kit_steel", 1.0, EquipmentSlotGroup.MAINHAND);
        }
        if (sword && gainLevel(skills, clazz, "zweihaender") > 0) {
            addAttribute(stack, Attributes.ENTITY_INTERACTION_RANGE, "kit_greatsword", 1.5, EquipmentSlotGroup.MAINHAND);
        }
        return stack;
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
            Item infernite = InferniteContent.gear("infernite_" + piece);
            if (infernite != null) {
                return infernite;
            }
            material = NightKit.Material.NETHERITE;
        }
        String id = material.name().toLowerCase(java.util.Locale.ROOT) + "_" + piece;
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
    }

    private static Item gear(String id, Item fallback) {
        Item item = InferniteContent.gear(id);
        return item != null ? item : fallback;
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

    static String encode(ItemStack stack, HolderLookup.Provider regs) {
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

    static ItemStack decode(String json, HolderLookup.Provider regs) {
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
