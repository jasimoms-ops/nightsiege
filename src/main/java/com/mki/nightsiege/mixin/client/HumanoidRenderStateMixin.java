package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.ClassLookState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every humanoid render state room for the class outfit its armour shows. */
@Mixin(HumanoidRenderState.class)
public abstract class HumanoidRenderStateMixin implements ClassLookState {

    @Unique
    private String nightsiege$look;

    @Override
    public String nightsiege$look() {
        return nightsiege$look;
    }

    @Override
    public void nightsiege$setLook(String look) {
        this.nightsiege$look = look;
    }
}
