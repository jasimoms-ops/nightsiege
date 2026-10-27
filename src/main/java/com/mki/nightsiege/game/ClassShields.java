package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SkillTree;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

import java.util.List;
import java.util.Map;

/**
 * The kit shield of each guardian subclass in the colours of its outfit (Simon, 27 October 2026:
 * "shield designs for the classes with a shield in the kit"). Made of banner patterns, so every
 * player sees them and the shield keeps its vanilla shape and blocking.
 */
public final class ClassShields {

    private record Layer(ResourceKey<BannerPattern> pattern, DyeColor color) {
    }

    private record Design(DyeColor base, List<Layer> layers) {
    }

    private static final Map<String, Design> DESIGNS = Map.of(
            // Schildkämpfer: the blue and gold tabard – a gold cross on blue, gold rim.
            SkillTree.SUB_SHIELDFIGHTER, new Design(DyeColor.BLUE, List.of(
                    new Layer(BannerPatterns.STRAIGHT_CROSS, DyeColor.YELLOW),
                    new Layer(BannerPatterns.CIRCLE_MIDDLE, DyeColor.BLUE),
                    new Layer(BannerPatterns.BORDER, DyeColor.YELLOW))),
            // Bollwerk: the red and gold breastplate – gold masonry on red, a steel rim.
            SkillTree.SUB_BULWARK, new Design(DyeColor.RED, List.of(
                    new Layer(BannerPatterns.BRICKS, DyeColor.YELLOW),
                    new Layer(BannerPatterns.BORDER, DyeColor.LIGHT_GRAY),
                    new Layer(BannerPatterns.CURLY_BORDER, DyeColor.GRAY))),
            // Sanitäter: the white coat with the green cross.
            SkillTree.SUB_MEDIC, new Design(DyeColor.WHITE, List.of(
                    new Layer(BannerPatterns.STRAIGHT_CROSS, DyeColor.GREEN),
                    new Layer(BannerPatterns.BORDER, DyeColor.LIGHT_GRAY))),
            // Bannwächter: the violet robe with the lavender rune diamond.
            SkillTree.SUB_WARDEN, new Design(DyeColor.PURPLE, List.of(
                    new Layer(BannerPatterns.RHOMBUS_MIDDLE, DyeColor.MAGENTA),
                    new Layer(BannerPatterns.CIRCLE_MIDDLE, DyeColor.PURPLE),
                    new Layer(BannerPatterns.GRADIENT, DyeColor.BLACK),
                    new Layer(BannerPatterns.BORDER, DyeColor.BLACK))));

    private ClassShields() {
    }

    /** For the tests: the shield's components as SNBT ("" for subclasses without a shield design). */
    public static String componentsSnbt(String subclass) {
        Design design = subclass == null ? null : DESIGNS.get(subclass);
        if (design == null) {
            return "";
        }
        StringBuilder out = new StringBuilder("\"minecraft:base_color\":\"" + design.base().getSerializedName()
                + "\",\"minecraft:banner_patterns\":[");
        for (int i = 0; i < design.layers().size(); i++) {
            Layer layer = design.layers().get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{pattern:\"").append(layer.pattern().identifier()).append("\",color:\"")
                    .append(layer.color().getSerializedName()).append("\"}");
        }
        return out.append(']').toString();
    }

    /** Paints the shield in the subclass's colours; nothing for other subclasses. */
    public static void decorate(ItemStack shield, String subclass, HolderLookup.Provider registries) {
        Design design = subclass == null ? null : DESIGNS.get(subclass);
        if (design == null || shield == null || shield.isEmpty() || registries == null) {
            return;
        }
        try {
            HolderGetter<BannerPattern> patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
            BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
            for (Layer layer : design.layers()) {
                builder.add(patterns.getOrThrow(layer.pattern()), layer.color());
            }
            shield.set(DataComponents.BASE_COLOR, design.base());
            shield.set(DataComponents.BANNER_PATTERNS, builder.build());
        } catch (RuntimeException e) {
            // A look only: a missing pattern never costs the kit its shield.
        }
    }
}
