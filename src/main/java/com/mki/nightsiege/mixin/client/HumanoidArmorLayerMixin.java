package com.mki.nightsiege.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mki.nightsiege.client.ClassArmorModel;
import com.mki.nightsiege.client.ClassArmorModels;
import com.mki.nightsiege.client.ClassLooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a worn armour piece as the outfit of its wearer's subclass: the outfit's
 * own model (with its hats, cloaks and the like) and its textures.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Unique
    private EquipmentSlot nightsiege$slot;

    @Inject(method = "renderArmorPiece", at = @At("HEAD"))
    private void nightsiege$rememberSlot(PoseStack pose, SubmitNodeCollector collector, ItemStack stack, EquipmentSlot slot,
                                         int light, HumanoidRenderState state, CallbackInfo ci) {
        this.nightsiege$slot = slot;
    }

    @WrapOperation(method = "renderArmorPiece", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"))
    private void nightsiege$classLook(EquipmentLayerRenderer renderer, EquipmentClientInfo.LayerType type,
                                      ResourceKey<EquipmentAsset> asset, Model<?> model, Object state, ItemStack stack,
                                      PoseStack pose, SubmitNodeCollector collector, int light, int outline,
                                      Operation<Void> original) {
        String look = ClassLooks.lookFor(state);
        ClassArmorModel outfit = ClassArmorModels.model(look, nightsiege$slot);
        if (outfit == null) {
            original.call(renderer, type, asset, model, state, stack, pose, collector, light, outline);
        } else {
            original.call(renderer, type, ClassLooks.asset(look), outfit, state, ClassLooks.withoutTrim(stack),
                    pose, collector, light, outline);
        }
    }

    @WrapOperation(method = "renderArmorPiece", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V"))
    private void nightsiege$classLookTextured(EquipmentLayerRenderer renderer, EquipmentClientInfo.LayerType type,
                                              ResourceKey<EquipmentAsset> asset, Model<?> model, Object state, ItemStack stack,
                                              PoseStack pose, SubmitNodeCollector collector, int light, Identifier texture,
                                              int outline, int order, Operation<Void> original) {
        String look = ClassLooks.lookFor(state);
        ClassArmorModel outfit = ClassArmorModels.model(look, nightsiege$slot);
        if (outfit == null) {
            original.call(renderer, type, asset, model, state, stack, pose, collector, light, texture, outline, order);
        } else {
            original.call(renderer, type, ClassLooks.asset(look), outfit, state, ClassLooks.withoutTrim(stack),
                    pose, collector, light, texture, outline, order);
        }
    }
}
