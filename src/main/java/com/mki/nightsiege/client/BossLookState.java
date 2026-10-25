package com.mki.nightsiege.client;

/** Added to every living render state: the boss look the monster wears ("grave_king" …), or null. */
public interface BossLookState {
    String nightsiege$boss();

    void nightsiege$setBoss(String boss);
}
