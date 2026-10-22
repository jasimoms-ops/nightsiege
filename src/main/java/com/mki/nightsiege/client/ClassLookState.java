package com.mki.nightsiege.client;

/** Added to the humanoid render state: the class outfit its armour shows, or null. */
public interface ClassLookState {

    String nightsiege$look();

    void nightsiege$setLook(String look);
}
