package com.mki.nightsiege.client;

import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.core.WarriorSpiritMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Set;

/**
 * Tor 4.10: the swing readiness ring around the crosshair for the six melee subclasses. Client only, read
 * from Minecraft's own attack strength: it fills clockwise from the top; from {@code w_spirit.charge}
 * (80 %) on it turns gold (the swing counts for Kampfgeist), full it is white (a full swing), bright
 * white when the crosshair is on a creature. Minecraft's own crosshair indicator is off meanwhile
 * ({@code CrosshairRingMixin}).
 */
public final class StrikeRing {

    private static final Set<String> SUBS = Set.of(SkillTree.SUB_BLADEMASTER, SkillTree.SUB_BERSERKER, SkillTree.SUB_AXE,
            SkillTree.SUB_PALADIN, SkillTree.SUB_SHIELDFIGHTER, SkillTree.SUB_BULWARK);
    private static final int DOTS = 32;
    private static final double RADIUS = 6.5;

    private StrikeRing() {
    }

    /**
     * True when the ring stands in for Minecraft's indicator: a melee subclass with the kit on (night, kit,
     * camp, dungeon or creative, like the other kit HUD parts), first person. Else Minecraft's own shows.
     */
    public static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        UltState s = UltHud.state();
        return s != null && (s.keys || s.live) && s.sub != null && SUBS.contains(s.sub) && mc.player != null && !mc.player.isSpectator()
                && mc.options.getCameraType().isFirstPerson() && !DroneClient.localDrone();
    }

    /** The share of the ring that is filled now (for the tests too). */
    public static float charge() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0f : Math.max(0f, Math.min(1f, mc.player.getAttackStrengthScale(0f)));
    }

    public static void draw(GuiGraphicsExtractor gui) {
        if (!shown()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float charge = charge();
        boolean full = charge >= 0.999f;
        boolean onTarget = mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity
                && hit.getEntity().isAlive();
        int fill;
        if (full) {
            fill = onTarget ? 0xFFFFFFFF : 0xB0FFFFFF;
        } else if (charge >= WarriorSpiritMath.minCharge()) {
            fill = 0xE0FFD84A;
        } else {
            fill = 0xB0C8C8C8;
        }
        // the middle pixel of the vanilla crosshair, not the screen half (Simon 09.11.: ring sat right and low)
        int cx = com.mki.nightsiege.core.CrosshairMath.center(mc.getWindow().getGuiScaledWidth());
        int cy = com.mki.nightsiege.core.CrosshairMath.center(mc.getWindow().getGuiScaledHeight());
        int lit = full ? DOTS : (int) Math.floor(charge * DOTS);
        // Z4 (Simon 12.11.): with full effects the ring is larger and its dots bigger, a full one glows gold-white
        boolean big = FxSettings.full();
        double radius = big ? RADIUS + 2.0 : RADIUS;
        int dot = big ? 2 : 1;
        if (big && full) {
            fill = onTarget ? 0xFFFFFFFF : 0xE0FFF2C0;
        }
        for (int i = 0; i < DOTS; i++) {
            double a = 2.0 * Math.PI * i / DOTS - Math.PI / 2.0;
            int x = cx + com.mki.nightsiege.core.CrosshairMath.symRound(Math.cos(a) * radius) - (dot - 1) / 2;
            int y = cy + com.mki.nightsiege.core.CrosshairMath.symRound(Math.sin(a) * radius) - (dot - 1) / 2;
            gui.fill(x, y, x + dot, y + dot, i < lit ? fill : 0x50000000);
        }
    }
}
