package com.mki.nightsiege.client;

/** Z4: added to every living render state - how white the monster flashes after an ability hit (0 to 1). */
public interface FlashState {
    float nightsiege$flash();

    void nightsiege$setFlash(float flash);
}
