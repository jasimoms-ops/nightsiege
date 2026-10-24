package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.SiegeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During a siege (from the warning on) no natural monsters spawn around the
 * core: only the siege's own attackers come. Animals and the rest spawn as
 * usual, and far from the core nothing changes.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    @Inject(method = "spawnCategoryForChunk", at = @At("HEAD"), cancellable = true, require = 0)
    private static void nightsiege$noMonstersInSiege(MobCategory category, ServerLevel level, LevelChunk chunk,
                                                     NaturalSpawner.SpawnPredicate predicate,
                                                     NaturalSpawner.AfterSpawnCallback callback, CallbackInfo ci) {
        if (category == MobCategory.MONSTER && SiegeManager.blocksNaturalSpawns(level,
                chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ())) {
            ci.cancel();
        }
    }
}
