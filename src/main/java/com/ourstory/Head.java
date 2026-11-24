package com.ourstory;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.ourstory.ProceduralMesh.Ring;

/** Parametric head. t = 0 is the chin, t = 1 the crown. Face looks toward -Z. */
public final class Head {
    public static final float BASE_Y = 1.535f;
    private static final float RX0 = .078f, RZ0 = .098f;
    private static final Ring[] PROF_X = knots(.14f, .50f, .70f, .84f, .93f, .98f, 1.0f);
    private static final Ring[] PROF_Z = knots(.62f, .80f, .88f, .93f, .96f, .98f, 1.0f);

    public final float width, jaw, cheek, nLow, height;

    public Head(int faceShape) {
        // Narrow, Balanced, Wide, Square Jaw, Round
        float[][] p = {
                {.92f, .92f, 1.00f, 2.0f, 1.05f},
                {1.00f, 1.00f, 1.00f, 2.1f, 1.00f},
                {1.08f, 1.06f, 1.04f, 2.2f, .97f},
                {1.02f, 1.15f, 1.00f, 3.3f, 1.00f},
                {1.05f, 1.00f, 1.09f, 2.0f, .95f}};
        float[] v = p[faceShape];
        width = v[0]; jaw = v[1]; cheek = v[2]; nLow = v[3]; height = .235f * v[4];
    }

    private static Ring[] knots(float... v) {
        Ring[] r = new Ring[v.length];
        for (int i = 0; i < v.length; i++) r[i] = new Ring(0, 0, 0, v[i], v[i], 2);
        return r;
    }

    private static float profile(Ring[] k, float t) {
        if (t >= .62f) {
            float e = (t - .62f) / .38f;
            return Math.max(.03f, (float) Math.sqrt(Math.max(0f, 1f - e * e)));
        }
        return ProceduralMesh.sample(k, t / .62f).rx;
    }

    public float y(float t) { return BASE_Y + t * height; }

    /** Ring (cross-section) of the head at t, grown outward by d meters. */
    public Ring ring(float t, float d) {
        float jw = 1f + (jaw - 1f) * FastMath.clamp(1f - t / .45f, 0f, 1f);
        float ch = 1f + (cheek - 1f) * (float) Math.exp(-Math.pow((t - .42f) / .14f, 2));
        float n = nLow + (2.2f - nLow) * FastMath.clamp(t / .5f, 0f, 1f);
        float g = (float) Math.sqrt(Math.max(0f, 1f - t * t));
        float gg = 1f + (g - 1f) * FastMath.clamp((t - .4f) / .3f, 0f, 1f);
        float rx = RX0 * width * profile(PROF_X, t) * jw * ch + d * gg;
        float rz = RZ0 * profile(PROF_Z, t) + d * gg;
        float yy = y(t) + d * t * t;
        return new Ring(yy, 0, 0, rx, rz, n);
    }

    public void point(float t, float theta, float d, Vector3f out) {
        ProceduralMesh.ringPoint(ring(t, d), theta, out);
    }

    /** Surface point on the front of the face at height t and sideways offset x (off = outward meters). */
    public Vector3f front(float t, float x, float off) {
        Ring r = ring(t, 0f);
        float q = Math.min(Math.abs(x) / r.rx, .999f);
        float z = -r.rz * (float) Math.pow(1f - Math.pow(q, r.n), 1f / r.n);
        return new Vector3f(x, r.y, z - off);
    }

    public float halfWidth(float t) { return ring(t, 0f).rx; }

}
