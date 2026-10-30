package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The quick shot's tap (Simon 30.10.: "schnellschütze: schnelles rechts klick drücken funktioniert
 * nicht wirklich flüssig"). A bow shoots nothing when it is let go within two ticks of the press,
 * so every fast click of a quick-shot archer that was a little too fast came to nothing. For him a
 * tap counts as four ticks drawn; his draw assist pulls the arrow on to full force as before.
 */
public final class QuickDraw {

    /** At least this many ticks drawn count for the quick shot (the bow needs three to shoot at all). */
    public static final int MIN_TICKS = 4;
    /** For the tests: taps that were too short and now shot. */
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
