package com.mki.nightsiege.client;

import com.mki.nightsiege.NightSiegeMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the armour models of the class outfits from {@link ClassArmorShapes}:
 * one model per outfit and armour slot, like the game's own armour, so every
 * piece keeps its own pose while the frame is drawn.
 */
public final class ClassArmorModels {

    private static final Map<String, ArmorModelSet<ClassArmorModel>> MODELS = new HashMap<>();
    private static final Map<String, Boolean> FAILED = new HashMap<>();

    private ClassArmorModels() {
    }

    /** The model of an outfit for one slot, or null when it could not be built. */
    public static ClassArmorModel model(String look, EquipmentSlot slot) {
        if (look == null || slot == null || FAILED.containsKey(look)) {
            return null;
        }
        ArmorModelSet<ClassArmorModel> set = MODELS.get(look);
        if (set == null) {
            set = build(look);
            if (set == null) {
                FAILED.put(look, true);
                return null;
            }
            MODELS.put(look, set);
        }
        return set.get(slot);
    }

    private static ArmorModelSet<ClassArmorModel> build(String look) {
        try {
            ArmorModelSet<MeshDefinition> meshes = HumanoidModel.createArmorMeshSet(
                    new CubeDeformation(0.5F), new CubeDeformation(1.0F));
            List<String> capes = new ArrayList<>();
            for (ClassArmorShapes.Box box : ClassArmorShapes.BOXES.getOrDefault(look, List.of())) {
                MeshDefinition mesh = meshes.get(slotOf(box.slot()));
                PartDefinition parent = mesh.getRoot().getChild(box.part());
                String name = "ns_" + box.name();
                CubeListBuilder cubes = CubeListBuilder.create().texOffs(box.u(), box.v()).mirror(box.mirror())
                        .addBox(box.x(), box.y(), box.z(), box.w(), box.h(), box.d(), new CubeDeformation(box.grow()));
                parent.addOrReplaceChild(name, cubes,
                        PartPose.offsetAndRotation(box.px(), box.py(), box.pz(), box.rx(), box.ry(), box.rz()));
                if (box.cape()) {
                    capes.add(name);
                }
            }
            return meshes.map(mesh -> new ClassArmorModel(
                    LayerDefinition.create(mesh, ClassArmorShapes.TEXTURE_WIDTH, ClassArmorShapes.TEXTURE_HEIGHT).bakeRoot(),
                    capes));
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Klassenkleidung {} nicht gebaut: {}", look, e.toString());
            return null;
        }
    }

    private static EquipmentSlot slotOf(String slot) {
        return switch (slot) {
            case "head" -> EquipmentSlot.HEAD;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> EquipmentSlot.CHEST;
        };
    }
}
