package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;

/**
 * Which infernite piece belongs to which path. Carrying it keeps that path's
 * skills and ultimate working outside a siege; any other infernite piece is
 * just good gear.
 */
public final class ClassGear {

    private static final Map<String, List<String>> BY_PATH = ordered(
            Map.entry(SkillTree.WAY_BOW, List.of("infernite_bow")),
            Map.entry(SkillTree.WAY_CROSSBOW, List.of("infernite_crossbow")),
            Map.entry(SkillTree.WAY_BLADE, List.of("infernite_sword")),
            Map.entry(SkillTree.WAY_MIGHT, List.of("infernite_axe", "infernite_mace")),
            Map.entry(SkillTree.WAY_SHIELD, List.of("infernite_shield", "infernite_chestplate")),
            Map.entry(SkillTree.WAY_BLESSING, List.of("infernite_helmet")),
            Map.entry(SkillTree.WAY_MECHANICS, List.of("infernite_pickaxe")),
            Map.entry(SkillTree.WAY_CHEMISTRY, List.of("infernite_pickaxe")));

    private ClassGear() {
    }

    @SafeVarargs
    private static Map<String, List<String>> ordered(Map.Entry<String, List<String>>... entries) {
        Map<String, List<String>> map = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    /** Every path this item belongs to, in tree order. */
    public static java.util.List<String> pathsFor(Item item) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (item == null) {
            return out;
        }
        for (Map.Entry<String, List<String>> entry : BY_PATH.entrySet()) {
            for (String id : entry.getValue()) {
                if (InferniteContent.gear(id) == item) {
                    out.add(entry.getKey());
                    break;
                }
            }
        }
        return out;
    }

    /** The paths of the first class item the player holds or wears; empty when none. */
    public static java.util.List<String> heldPaths(ServerPlayer player) {
        for (net.minecraft.world.item.ItemStack stack : new net.minecraft.world.item.ItemStack[]{
                player.getMainHandItem(), player.getOffhandItem(),
                player.getItemBySlot(EquipmentSlot.HEAD), player.getItemBySlot(EquipmentSlot.CHEST)}) {
            java.util.List<String> paths = pathsFor(stack.getItem());
            if (!paths.isEmpty()) {
                return paths;
            }
        }
        return java.util.List.of();
    }

    /** The infernite items of a path (one, or two for the ways of might and of the shield). */
    public static java.util.List<Item> forPath(String path) {
        java.util.List<Item> out = new java.util.ArrayList<>();
        java.util.List<String> ids = path == null ? null : BY_PATH.get(path);
        if (ids != null) {
            for (String id : ids) {
                Item item = InferniteContent.gear(id);
                if (item != null) {
                    out.add(item);
                }
            }
        }
        return out;
    }

    /** True when the player holds or wears an infernite item of their own path. */
    public static boolean carries(ServerPlayer player, PlayerSkills skills, SkillTree tree) {
        if (skills == null || !skills.hasClass()) {
            return false;
        }
        for (Item item : forPath(skills.pathTaken(tree))) {
            if (player.getMainHandItem().is(item) || player.getOffhandItem().is(item)
                    || player.getItemBySlot(EquipmentSlot.HEAD).is(item)
                    || player.getItemBySlot(EquipmentSlot.CHEST).is(item)) {
                return true;
            }
        }
        return false;
    }
}
