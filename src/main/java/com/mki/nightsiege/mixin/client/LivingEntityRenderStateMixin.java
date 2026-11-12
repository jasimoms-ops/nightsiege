package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.BossLookState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every living render state room for the boss look its monster wears. */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements BossLookState, com.mki.nightsiege.client.FlashState {

    @Unique
    private float nightsiege$flash;

    @Override
    public float nightsiege$flash() {
        return nightsiege$flash;
    }

    @Override
    public void nightsiege$setFlash(float flash) {
        this.nightsiege$flash = flash;
    }

    @Unique
    private String nightsiege$boss;

    @Override
    public String nightsiege$boss() {
        return nightsiege$boss;
    }

    @Override
    public void nightsiege$setBoss(String boss) {
        this.nightsiege$boss = boss;
    }
}
