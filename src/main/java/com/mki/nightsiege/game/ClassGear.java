package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * Which infernite piece belongs to which path. Carrying it keeps that path's
 * skills and ultimate working outside a siege; any other infernite piece is
 * just good gear.
 */
public final class ClassGear {

    private static final Map<String, String> BY_PATH = Map.ofEntries(
            Map.entry(SkillTree.PATH_SWIFT, "infernite_bow"),
            Map.entry(SkillTree.PATH_HEAVY, "infernite_bow"),
            Map.entry(SkillTree.PATH_HUNTER, "infernite_bow"),
            Map.entry(SkillTree.PATH_CROSSBOW, "infernite_crossbow"),
            Map.entry(SkillTree.PATH_BERSERKER, "infernite_sword"),
            Map.entry(SkillTree.PATH_BLADE, "infernite_sword"),
            Map.entry(SkillTree.PATH_SPEAR, "infernite_spear"),
            Map.entry(SkillTree.PATH_AXE, "infernite_axe"),
            Map.entry(SkillTree.PATH_BULWARK, "infernite_chestplate"),
            Map.entry(SkillTree.PATH_MEDIC, "infernite_helmet"),
            Map.entry(SkillTree.PATH_SHIELD, "infernite_shield"),
            Map.entry(SkillTree.PATH_PALADIN, "infernite_mace"),
            Map.entry(SkillTree.PATH_DEMOLITION, "infernite_pickaxe"),
            Map.entry(SkillTree.PATH_GUNNER, "infernite_pickaxe"),
            Map.entry(SkillTree.PATH_ALCHEMIST, "infernite_pickaxe"),
            Map.entry(SkillTree.PATH_ELECTRICIAN, "infernite_pickaxe"));

    private ClassGear() {
    }

    /** The infernite item of a path, or null. */
    public static Item forPath(String path) {
        String id = path == null ? null : BY_PATH.get(path);
        return id == null ? null : InferniteContent.gear(id);
    }

    /** True when the player holds or wears the infernite item of their own path. */
    public static boolean carries(ServerPlayer player, PlayerSkills skills, SkillTree tree) {
        if (skills == null || !skills.hasClass()) {
            return false;
        }
        Item item = forPath(skills.pathTaken(tree));
        if (item == null) {
            return false;
        }
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item)
                || player.getItemBySlot(EquipmentSlot.HEAD).is(item)
                || player.getItemBySlot(EquipmentSlot.CHEST).is(item);
    }
}
