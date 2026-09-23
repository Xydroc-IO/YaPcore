package com.yapcore.ultrawide;

/**
 * Horizontal Panini projection. Rectilinear perspective stretches the edges
 * once horizontal FOV gets wide (the bowed floor lines at FOV 100). Panini
 * keeps the center on the same scale as the FOV slider and compresses the
 * edges. {@code d == 0} is rectilinear.
 *
 * <pre>
 * n(u) = u * (1 + d) / (1 + d * sqrt(1 + u * u))
 * </pre>
 */
public final class Panini {
    /** Off, or not an ultrawide frame. */
    public static final Frame OFF = new Frame(false, 0.0f, 1.0f, 1.0f, 1.0f);

    private Panini() {
    }

    /**
     * @param verticalFovDegrees vertical FOV the slider produced (after Hor+)
     * @param aspect             framebuffer width / height
     * @param strength           0 = rectilinear, 1 = full edge correction. The
     *                           center of the view stays on the FOV slider.
     */
    public static Frame solve(float verticalFovDegrees, float aspect, float strength) {
        if (strength <= 0.01f || aspect < 1.2f || verticalFovDegrees <= 1.0f) {
            return OFF;
        }
        double halfV = Math.toRadians(verticalFovDegrees) * 0.5;
        double vert = Math.tan(halfV);
        double edge = vert * aspect;
        if (edge < 0.25) {
            return OFF;
        }
        // Highest distance that still fits in the widen budget. More distance
        // squeezes the edges harder. The center scale stays on the slider.
        double maxWiden = 1.2 + clamp01(strength);
        double d = maxDistance(edge, maxWiden);
        if (d < 0.02) {
            return OFF;
        }
        double renderEdge = invert(edge, d);
        if (!(renderEdge > edge)) {
            return OFF;
        }
        return new Frame(true, (float) d, (float) edge, (float) renderEdge, (float) vert);
    }

    /** Forward map. {@code d == 0} returns {@code u}. */
    public static double project(double u, double d) {
        if (d <= 1.0e-6) {
            return u;
        }
        return u * (1.0 + d) / (1.0 + d * Math.sqrt(1.0 + u * u));
    }

    /**
     * Inverse of {@link #project}. The positive root is the one that lands
     * back on {@code p}. Falls back to {@code p} when the distance is past
     * the projection's reach.
     */
    public static double invert(double p, double d) {
        if (d <= 1.0e-5 || Math.abs(p) < 1.0e-6) {
            return p;
        }
        double sign = Math.signum(p);
        double mag = Math.abs(p);
        double a = 1.0 + d;
        double A = a * a - (mag * d) * (mag * d);
        double B = -2.0 * a * mag;
        double C = mag * mag * (1.0 - d * d);
        double disc = B * B - 4.0 * A * C;
        if (disc < 0.0 || Math.abs(A) < 1.0e-8) {
            return sign * mag;
        }
        double u = (-B + Math.sqrt(disc)) / (2.0 * A);
        if (u < mag) {
            return sign * mag;
        }
        return sign * u;
    }

    /** Highest {@code d} whose rendered tangent stays within {@code maxRatio} of the edge. */
    private static double maxDistance(double edge, double maxRatio) {
        double lo = 0.0;
        double hi = 1.5;
        double best = 0.0;
        for (int i = 0; i < 28; i++) {
            double mid = (lo + hi) * 0.5;
            double u = invert(edge, mid);
            if (u <= edge || u / edge > maxRatio) {
                hi = mid;
            } else {
                best = mid;
                lo = mid;
            }
        }
        return best;
    }

    private static double clamp01(float strength) {
        return Math.max(0.0, Math.min(1.0, strength));
    }

    static void selfCheck() {
        double u = invert(1.0, 0.5);
        double back = project(u, 0.5);
        if (Math.abs(u - 1.183) > 0.01 || Math.abs(back - 1.0) > 1.0e-3) {
            throw new IllegalStateException("panini inverse failed: u=" + u + " back=" + back);
        }
        if (Math.abs(project(0.4, 0.0) - 0.4) > 1.0e-6) {
            throw new IllegalStateException("panini d=0 is not rectilinear");
        }
    }

    /**
     * @param edge       desired horizontal tangent (slider FOV, center scale)
     * @param renderEdge wider tangent the world is rendered with
     * @param vertEdge   desired vertical tangent
     */
    public record Frame(boolean active, float distance, float edge, float renderEdge, float vertEdge) {
        /** Multiply projection m00/m11 by this so the render frustum matches {@code renderEdge}. */
        public float widenScale() {
            if (!active || renderEdge <= 1.0e-4f) {
                return 1.0f;
            }
            return edge / renderEdge;
        }
    }
}
