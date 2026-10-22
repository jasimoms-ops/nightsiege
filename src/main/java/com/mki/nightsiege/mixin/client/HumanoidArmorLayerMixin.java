package com.mki.nightsiege.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mki.nightsiege.client.ClassLooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Draws a worn armour piece in the outfit of its wearer's subclass. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @WrapOperation(method = "renderArmorPiece", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"))
    private void nightsiege$classLook(EquipmentLayerRenderer renderer, EquipmentClientInfo.LayerType type,
                                      ResourceKey<EquipmentAsset> asset, Model<?> model, Object state, ItemStack stack,
                                      PoseStack pose, SubmitNodeCollector collector, int light, int outline,
                                      Operation<Void> original) {
        ResourceKey<EquipmentAsset> look = ClassLooks.assetFor(state);
        if (look == null) {
            original.call(renderer, type, asset, model, state, stack, pose, collector, light, outline);
        } else {
            original.call(renderer, type, look, model, state, ClassLooks.withoutTrim(stack), pose, collector, light, outline);
        }
    }

    @WrapOperation(method = "renderArmorPiece", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V"))
    private void nightsiege$classLookTextured(EquipmentLayerRenderer renderer, EquipmentClientInfo.LayerType type,
                                              ResourceKey<EquipmentAsset> asset, Model<?> model, Object state, ItemStack stack,
                                              PoseStack pose, SubmitNodeCollector collector, int light, Identifier texture,
                                              int outline, int order, Operation<Void> original) {
        ResourceKey<EquipmentAsset> look = ClassLooks.assetFor(state);
        if (look == null) {
            original.call(renderer, type, asset, model, state, stack, pose, collector, light, texture, outline, order);
        } else {
            original.call(renderer, type, look, model, state, ClassLooks.withoutTrim(stack), pose, collector, light, texture, outline, order);
        }
    }
}
