package com.mki.nightsiege.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The armour model of a class outfit: the usual armour boxes plus the outfit's
 * own parts. Cloaks swing like the player's cape.
 */
public class ClassArmorModel extends HumanoidModel<HumanoidRenderState> {

    private static final float DEGREES = (float) (Math.PI / 180.0);

    private final List<ModelPart> capes = new ArrayList<>();

    public ClassArmorModel(ModelPart root, Collection<String> capeNames) {
        super(root);
        for (String name : capeNames) {
            if (this.body.hasChild(name)) {
                capes.add(this.body.getChild(name));
            }
        }
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        super.setupAnim(state);
        if (capes.isEmpty()) {
            return;
        }
        float lean = 6.0F;
        if (state instanceof AvatarRenderState avatar) {
            lean = 6.0F + avatar.capeLean / 2.0F + avatar.capeFlap;
        }
        lean = Math.max(0.0F, Math.min(75.0F, lean));
        for (ModelPart cape : capes) {
            cape.xRot = cape.getInitialPose().xRot() + lean * DEGREES;
        }
    }
}
