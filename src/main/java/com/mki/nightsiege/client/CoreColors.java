package com.mki.nightsiege.client;

import com.mki.nightsiege.core.SkillClass;

/** The colour of each class in the windows (the same as the tabs of the core window had). */
final class CoreColors {

    private CoreColors() {
    }

    static int classColor(SkillClass clazz) {
        if (clazz == null) {
            return 0xFFFFD86B;
        }
        return switch (clazz) {
            case ARCHER -> 0xFF8BD45C;
            case WARRIOR -> 0xFFFF6B4A;
            case GUARDIAN -> 0xFF6FA8FF;
            case ENGINEER -> 0xFFF0A040;
            case SUMMONER -> 0xFF5FD8B8;
            default -> 0xFFC88CFF;
        };
    }
}
