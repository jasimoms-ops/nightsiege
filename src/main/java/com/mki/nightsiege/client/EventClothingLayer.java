package com.mki.nightsiege.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

import java.util.function.Predicate;

/**
 * A second, slightly larger layer of clothes (hoods, rags) that zombies and
 * skeletons wear during some events, like the stray's and the drowned's.
 */
public class EventClothingLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {

    private final Model<? super S> clothes;
    private final String mob;
    private final Predicate<S> when;

    public EventClothingLayer(RenderLayerParent<S, M> parent, Model<? super S> clothes, String mob, Predicate<S> when) {
        super(parent);
        this.clothes = clothes;
        this.mob = mob;
        this.when = when;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
        Identifier texture = EventTextures.clothing(mob);
        if (texture == null || !when.test(state)) {
            return;
        }
        coloredCutoutModelCopyLayerRender(clothes, texture, poseStack, collector, lightCoords, state, -1, 1);
    }
}
