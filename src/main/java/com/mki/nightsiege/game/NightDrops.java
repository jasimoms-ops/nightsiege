package com.mki.nightsiege.game;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Siege monsters drop nothing (Simon, 26 October 2026): no items and no experience orbs
 * from the monsters of a night. Their deaths are noted just before they happen; whatever
 * item or orb appears right there in the same moment is removed as it loads. The dungeons'
 * monsters and their champion drop nothing either (Simon 30.10.: "mob drops in dungeons sind
 * noch an"); the dungeon's reward goes straight into the inventory and is not touched.
 */
public final class NightDrops {

    private record Death(ServerLevel level, Vec3 at, long time) {
    }

    private static final Deque<Death> recent = new ArrayDeque<>();

    private NightDrops() {
    }

    /** A siege monster is about to die: note where. */
    public static void onDeath(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !dropsNothing(entity)) {
            return;
        }
        recent.addLast(new Death(level, entity.position(), level.getGameTime()));
        while (recent.size() > 64) {
            recent.removeFirst();
        }
    }

    /** The monsters of a night and of a dungeon (halls, waves, champion). */
    static boolean dropsNothing(Entity entity) {
        return entity.entityTags().contains(MobFactory.TAG) || entity.entityTags().contains(Expeditions.TAG);
    }

    /** True when this item or orb is the loot of a siege monster that just died (the caller removes it). */
    public static boolean isLoot(Entity entity) {
        if (!(entity instanceof ItemEntity || entity instanceof ExperienceOrb) || recent.isEmpty()
                || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        while (!recent.isEmpty() && now - recent.peekFirst().time() > 2) {
            recent.removeFirst();
        }
        for (Death death : recent) {
            if (death.level() == level && death.at().distanceToSqr(entity.position()) < 2.5 * 2.5) {
                return true;
            }
        }
        return false;
    }
}
