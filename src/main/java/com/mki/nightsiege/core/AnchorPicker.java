package com.mki.nightsiege.core;

import java.util.List;

/**
 * Chooses which registered bed gets attacked: the one with the most players
 * within the anchor radius. Ties go to the bed with the closest player, so the
 * result is stable instead of random.
 */
public final class AnchorPicker {

    /** A point in a world, used for both beds and players. */
    public record Point(String dimension, double x, double y, double z) {

        public double distanceSqTo(Point other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }

        public boolean sameDimension(Point other) {
            return dimension != null && dimension.equals(other.dimension);
        }
    }

    /** The chosen bed together with how many players were near it. */
    public record Result(Point anchor, int playerCount, double nearestDistanceSq) {
    }

    private AnchorPicker() {
    }

    public static Result pick(List<Point> beds, List<Point> players, double radius) {
        if (beds == null || beds.isEmpty() || players == null || players.isEmpty()) {
            return null;
        }
        double radiusSq = radius * radius;
        Result best = null;
        for (Point bed : beds) {
            if (bed == null) {
                continue;
            }
            int count = 0;
            double nearest = Double.MAX_VALUE;
            for (Point player : players) {
                if (player == null || !bed.sameDimension(player)) {
                    continue;
                }
                double distSq = bed.distanceSqTo(player);
                if (distSq <= radiusSq) {
                    count++;
                    nearest = Math.min(nearest, distSq);
                }
            }
            if (count == 0) {
                continue;
            }
            Result candidate = new Result(bed, count, nearest);
            if (best == null
                    || candidate.playerCount() > best.playerCount()
                    || (candidate.playerCount() == best.playerCount()
                        && candidate.nearestDistanceSq() < best.nearestDistanceSq())) {
                best = candidate;
            }
        }
        return best;
    }
}
