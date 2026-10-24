package com.mki.nightsiege.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The clothes of the subclasses. Whoever has chosen a subclass shows its
 * outfit on every armour piece they wear, whatever the armour is made of: only
 * the look changes, never the protection. The subclass comes from the name tag
 * team the server puts every player in, so everybody sees everybody's outfit.
 * An armour stand named after a subclass wears its outfit too.
 */
public final class ClassLooks {

    public static final List<String> LOOKS = List.of(
            "berserker", "blademaster", "axe", "paladin",
            "swift", "heavy", "bolter", "trapper",
            "shieldfighter", "bulwark", "medic", "warden",
            "gunner", "electrician", "demolition", "alchemist",
            "tamer", "golemsmith");

    private static final String TEAM_PREFIX = "ns_c_";
    private static final Map<String, ResourceKey<EquipmentAsset>> ASSETS = new LinkedHashMap<>();
    private static final Map<String, String> BY_NAME = new HashMap<>();

    static {
        for (String look : LOOKS) {
            ASSETS.put(look, ResourceKey.create(EquipmentAssets.ROOT_ID,
                    Identifier.fromNamespaceAndPath("nightsiege", "class_" + look)));
        }
    }

    private ClassLooks() {
    }

    /** The outfit an entity shows: its subclass, or for an armour stand its name. */
    public static String lookOf(LivingEntity entity) {
        try {
            PlayerTeam team = entity.getTeam();
            if (team != null && team.getName().startsWith(TEAM_PREFIX)) {
                String key = team.getName().substring(TEAM_PREFIX.length());
                if (ASSETS.containsKey(key)) {
                    return key;
                }
            }
            if (entity.getType() == EntityTypes.ARMOR_STAND && entity.hasCustomName() && entity.getCustomName() != null) {
                return byName(entity.getCustomName().getString());
            }
        } catch (RuntimeException e) {
            // A look is decoration; never let it break the rendering.
        }
        return null;
    }

    private static String byName(String name) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        String known = BY_NAME.get(wanted);
        if (known != null) {
            return known.isEmpty() ? null : known;
        }
        String found = "";
        for (String look : LOOKS) {
            if (look.equals(wanted) || Component.translatable("nightsiege.subclass." + look).getString()
                    .toLowerCase(Locale.ROOT).equals(wanted)) {
                found = look;
                break;
            }
        }
        BY_NAME.put(wanted, found);
        return found.isEmpty() ? null : found;
    }

    /** The outfit a render state shows, or null. Baby models keep their plain armour. */
    public static String lookFor(Object renderState) {
        if (renderState instanceof ClassLookState state) {
            if (renderState instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living
                    && living.isBaby) {
                return null;
            }
            return state.nightsiege$look();
        }
        return null;
    }

    /** The equipment asset of an outfit (its textures). */
    public static ResourceKey<EquipmentAsset> asset(String look) {
        return look == null ? null : ASSETS.get(look);
    }

    /**
     * The piece as an outfit draws it: without trim patterns (the outfit has none of
     * its own) and without the enchantment glint, which would shimmer over the cloth.
     */
    public static ItemStack forOutfit(ItemStack stack) {
        if (stack == null || (!stack.has(DataComponents.TRIM) && !stack.hasFoil())) {
            return stack;
        }
        ItemStack copy = stack.copy();
        copy.remove(DataComponents.TRIM);
        copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        return copy;
    }
}
