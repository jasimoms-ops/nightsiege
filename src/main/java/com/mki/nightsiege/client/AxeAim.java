package com.mki.nightsiege.client;

import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
import com.mki.nightsiege.core.UltState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tor 4.10: the axe fighter's aim mark. While his F (Axtwurf) is ready and an axe is in his hand, a small
 * orange ring of dust marks where the axe will strike first along the crosshair: the first monster on its
 * way (as wide as the throw hits), else the first block, else the end of its range. Client only.
 */
public final class AxeAim {

    private static final DustParticleOptions MARK = new DustParticleOptions(0xFF8C1A, 0.6f);
    private static Vec3 mark;
    private static boolean onMonster;

    private AxeAim() {
    }

    /** The mark now (null without one) and whether it sits on a monster, for the tests. */
    public static Vec3 mark() {
        return mark;
    }

    public static boolean onMonster() {
        return mark != null && onMonster;
    }

    /**
     * A summoner's companion (the server's throw skips them, TrapService.enemies). Its entity tag stays on the
     * server; every companion that counts as a monster (skeleton, vex, minion) carries a name
     * {@code nightsiege.companion.*}, which the client sees.
     */
    static boolean companion(LivingEntity e) {
        return e.getCustomName() != null && e.getCustomName().getContents() instanceof TranslatableContents t
                && t.getKey().startsWith("nightsiege.companion.");
    }

    public static void tick(Minecraft mc) {
        mark = null;
        UltState s = UltHud.state();
        if (mc.player == null || mc.level == null || s == null || !(s.keys || s.live) || !SkillTree.SUB_AXE.equals(s.sub) || !s.hasAb2 || s.ab2Dim
                || s.cooldownAb2Ticks - UltHud.elapsedTicks() > 0 || !mc.player.getMainHandItem().is(ItemTags.AXES)) {
            return;
        }
        double range = SkillValues.get("heavy_blow", "f_range", 12);
        double width = SkillValues.get("heavy_blow", "f_width", 1.3);
        Vec3 eye = mc.player.getEyePosition();
        Vec3 dir = mc.player.getLookAngle().normalize();
        Vec3 end = eye.add(dir.scale(range));
        BlockHitResult wall = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (wall.getType() == HitResult.Type.BLOCK) {
            end = wall.getLocation();
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        AABB around = new AABB(eye, end).inflate(width + 1.0, 2.5, width + 1.0);
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, around, e -> e instanceof Enemy && e.isAlive() && !companion(e))) {
            AABB body = e.getBoundingBox().inflate(width, 0.6, width);
            if (body.contains(eye) || body.clip(eye, end).isPresent()) {
                double d = e.distanceToSqr(eye);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        onMonster = best != null;
        mark = best != null ? new Vec3(best.getX(), best.getY(0.5), best.getZ()) : end;
        if (mc.level.getGameTime() % 2 == 0) {
            // A ring facing the player.
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(dir).normalize();
            double r = best != null ? 0.45 : 0.25;
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                Vec3 p = mark.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)).subtract(dir.scale(0.3));
                mc.level.addParticle(MARK, p.x, p.y, p.z, 0, 0, 0);
            }
        }
    }
}
