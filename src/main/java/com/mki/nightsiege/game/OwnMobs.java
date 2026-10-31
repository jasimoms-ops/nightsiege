package com.mki.nightsiege.game;

import net.minecraft.world.entity.Entity;

import java.util.Set;

/**
 * Night Siege's own monsters, by their entity tags (survival safety 2, Simon 31.10.): only these
 * crack a player's armour. A zombie or a creeper of everyday play stays vanilla.
 *
 * <p>Merge note: the survival safety package 1 brings the same check as
 * {@code SiegeManager.isNightSiegeMob}; at the merge this class can simply call it.</p>
 */
public final class OwnMobs {

    /** Entity tags of the mod's monsters: the night's, bosses, the war camp, dungeons, machines, foes. */
    private static final Set<String> TAGS = Set.of(
            MobFactory.TAG, MobFactory.TAG_ELITE, SiegeCamp.TAG, Expeditions.TAG, Expeditions.CHAMPION_TAG,
            BossService.TAG_BOSS, BossService.TAG_PART, BossService.TAG_MOUNT,
            SiegeMachines.TAG_CREW, SiegeMachines.TAG_MACHINE, Foes.TAG, Foes.TAG_RISEN, Foes.TAG_GRAVE,
            WardenNight.TAG, Nemesis.TAG);

    private OwnMobs() {
    }

    /** True for a monster of Night Siege (a night's, a boss, the war camp's, a dungeon's, a playtest's). */
    public static boolean is(Entity entity) {
        if (entity == null) {
            return false;
        }
        Set<String> tags = entity.entityTags();
        if (tags.isEmpty()) {
            return false;
        }
        for (String tag : tags) {
            if (TAGS.contains(tag)) {
                return true;
            }
        }
        return false;
    }
}
