package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The quick shot's tap (Simon 30.10.: "schnellschütze: schnelles rechts klick drücken funktioniert
 * nicht wirklich flüssig") – until Tor 4 a tap counted as four ticks drawn. Tor 4 (Simon 08.11.,
 * "spannen statt spammen"): the quick shot has to draw his bow briefly, at least MIN_TICKS; a release
 * before that shoots nothing (no arrow is used). His draw assist pulls the arrow on to full force as before.
 */
public final class QuickDraw {

    /** The quick shot has to draw at least this many ticks (half a second) before a release shoots. */
    public static final int MIN_TICKS = 10;
    /** For the tests: releases that were too short and shot nothing. */
    private static int saved;

    private QuickDraw() {
    }

    /** True for a quick-shot archer whose skills count now (night, dungeon, camp or creative). */
    public static boolean applies(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        SiegeManager m = SiegeManager.get();
        if (m == null) {
            return false;
        }
        PlayerSkills skills = m.liveSkillsFor(player);
        return skills != null && SkillTree.SUB_SWIFT.equals(m.skillTree().subclassOf(skills));
    }

    public static void saved() {
        saved++;
    }

    public static int savedTaps() {
        return saved;
    }
}
