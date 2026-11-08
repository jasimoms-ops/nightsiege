package com.mki.nightsiege.core;

/**
 * The gain wheel (Tor 3.2, Simon 08.11.: one key instead of N, M, J). Pure rules without Minecraft,
 * so JUnit can check them: hold or tap, which place the mouse points at, the mouse wheel, the
 * cooldown left and its ring.
 */
public final class GainWheelLogic {

    /** Held this many ticks (a quarter second) the wheel opens; let go sooner, it was a tap. */
    public static final int HOLD_TICKS = 5;
    /** Inside this many GUI pixels around the middle the mouse points at nothing (the last place stays). */
    public static final double DEAD_ZONE = 10.0;

    private GainWheelLogic() {
    }

    /** True while a press is still a tap: let go before the wheel opened. */
    public static boolean isTap(int heldTicks) {
        return heldTicks < HOLD_TICKS;
    }

    /**
     * The place the mouse points at, seen from the middle of the wheel: place 0 at the top, then
     * clockwise. -1 inside the dead zone or without places.
     */
    public static int sector(double dx, double dy, int count) {
        if (count <= 0 || dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
            return -1;
        }
        double angle = Math.atan2(dx, -dy);   // 0 up, growing clockwise (screen y points down)
        if (angle < 0) {
            angle += Math.PI * 2;
        }
        double step = Math.PI * 2 / count;
        return (int) Math.floor((angle + step / 2) / step) % count;
    }

    /** The middle of place {@code index} as an angle (0 up, clockwise), for drawing. */
    public static double angleOf(int index, int count) {
        return count <= 0 ? 0.0 : Math.PI * 2 * index / count;
    }

    /** The mouse wheel: {@code steps} places on (negative back), round the wheel. */
    public static int scroll(int selected, int steps, int count) {
        if (count <= 0) {
            return -1;
        }
        int from = selected < 0 ? 0 : selected;
        return Math.floorMod(from + steps, count);
    }

    /** What a tap uses: the place used last, or the first while the last is gone. */
    public static int tapIndex(int last, int count) {
        if (count <= 0) {
            return -1;
        }
        return last >= 0 && last < count ? last : 0;
    }

    /** Ticks left of a cooldown sent {@code elapsed} ticks ago. */
    public static long leftTicks(int cooldownTicks, long elapsedTicks) {
        return Math.max(0L, cooldownTicks - Math.max(0L, elapsedTicks));
    }

    /** The seconds shown in the ring, rounded up (0.05 s left still shows 1). */
    public static int seconds(long leftTicks) {
        return leftTicks <= 0 ? 0 : (int) ((leftTicks + 19) / 20);
    }

    /** How much of the ring is gold: 1 when ready, 0 right after use. */
    public static double readyShare(long leftTicks, int totalTicks) {
        if (leftTicks <= 0 || totalTicks <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - leftTicks / (double) totalTicks));
    }

    /** How many of {@code segments} ring pieces are lit for that share. */
    public static int litSegments(double readyShare, int segments) {
        return (int) Math.floor(Math.max(0.0, Math.min(1.0, readyShare)) * segments + 1e-9);
    }
}
