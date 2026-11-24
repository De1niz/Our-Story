package com.ourstory;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.ourstory.ProceduralMesh.Ring;

/** Hair and beard shells built on top of the parametric head. */
public final class HairKit {
    private HairKit() {}

    private static float smooth(float x) { x = FastMath.clamp(x, 0f, 1f); return x * x * (3f - 2f * x); }

    private static ColorRGBA mix(ColorRGBA a, ColorRGBA b, float w) {
        w = FastMath.clamp(w, 0f, 1f);
        return new ColorRGBA(a.r + (b.r - a.r) * w, a.g + (b.g - a.g) * w, a.b + (b.b - a.b) * w, 1f);
    }

    private static ColorRGBA strand(ColorRGBA col, float noise, float rootDark, float shine) {
        float k = (.74f + .5f * noise) * (1f - .22f * rootDark) * (1f + .14f * shine);
        return new ColorRGBA(FastMath.clamp(col.r * k, 0, 1), FastMath.clamp(col.g * k, 0, 1),
                FastMath.clamp(col.b * k, 0, 1), 1f);
    }

    private static void add(Node root, String part, Mesh m, Material mat) {
        Geometry g = new Geometry(part, m);
        g.setMaterial(mat);
        g.setUserData("part", part);
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        root.attachChild(g);
    }

    // ---------------------------------------------------------------- hair

    public static void hair(Node root, Head h, int style, ColorRGBA col, ColorRGBA skin, Material mat) {
        if (style == 0) return; // bald
        // thTop, thSide, edgeFront, edgeSide, edgeBack, lumps
        float[][] p = {
                null,
                {.0035f, .003f, .76f, .52f, .38f, 0f},   // buzz
                {.014f, .009f, .77f, .54f, .36f, .004f}, // short
                {.016f, .010f, .73f, .54f, .36f, .004f}, // side part
                {.014f, .010f, .77f, .50f, .30f, 0f},    // long
                {.012f, .008f, .78f, .52f, .34f, 0f},    // bun
                {.012f, .008f, .78f, .52f, .34f, 0f},    // ponytail
                {.028f, .022f, .78f, .44f, .26f, .015f}  // curly
        };
        final float[] v = p[style];
        final boolean buzz = style == 1, part = style == 3;
        final int rows = 26, cols = 56;
        final float[] tEdge = new float[cols];
        for (int c = 0; c < cols; c++) {
            float th = FastMath.TWO_PI * c / cols;
            float cs = FastMath.cos(th);
            float e = v[3] + (v[2] - v[3]) * (float) Math.pow(Math.max(0f, -cs), 1.5) + (v[4] - v[3]) * Math.max(0f, cs);
            if (part && FastMath.sin(th) > 0f) e -= .06f * smooth(-cs * 2f) * FastMath.sin(th);
            tEdge[c] = e;
        }
        ProceduralMesh.Pos pos = (r, c, out) -> {
            float u = r / (float) (rows - 1);
            float th = FastMath.TWO_PI * c / cols;
            float t = 1f - u * (1f - tEdge[c]);
            float lump = v[5] * (.5f + .5f * FastMath.sin(th * 9f + r * .9f) * FastMath.sin(r * 1.3f + th * 5f));
            float d = v[0] * (1f - u) + v[1] * u + lump * (1f - u * .6f);
            if (part && FastMath.sin(th) > 0f) d += .008f * (1f - u) * smooth(-FastMath.cos(th) + .3f);
            h.point(t, th, d, out);
        };
        ProceduralMesh.Tint tint = (r, c, q) -> {
            float u = r / (float) (rows - 1);
            float n = ProceduralMesh.hash(c, 7);
            ColorRGBA s = strand(col, n, 1f - u, Math.max(0f, 1f - Math.abs(u - .35f) * 5f));
            if (buzz) s = mix(skin, s, .8f);
            if (part) {
                float th = FastMath.TWO_PI * c / cols;
                if (Math.abs(th - FastMath.PI * 1.0f + .0f) < 3.2f && Math.abs(FastMath.sin(th) - .18f) < .05f && u < .8f)
                    s = mix(s, skin, .5f);
            }
            return s;
        };
        add(root, "Hair", ProceduralMesh.grid(rows, cols, true, pos, tint), mat);

        if (style == 4) longHang(root, h, v, col, mat);
        if (style == 5) {
            Mesh bun = ellipsoid(0f, h.y(1f) + .02f, .06f, .052f, .046f, .052f, col, 1);
            add(root, "Hair", bun, mat);
        }
        if (style == 6) ponytail(root, h, col, mat);
    }

    private static void longHang(Node root, Head h, float[] v, ColorRGBA col, Material mat) {
        final int rows = 18, cols = 40;
        final float span = .62f * FastMath.PI;
        ProceduralMesh.Pos pos = (r, c, out) -> {
            float th = -span + 2f * span * c / (cols - 1);
            float cs = FastMath.cos(th);
            float e = v[3] + (v[4] - v[3]) * Math.max(0f, cs);
            Ring top = h.ring(e, .013f);
            float vv = r / (float) (rows - 1);
            float hem = 1.47f - .33f * (float) Math.pow(Math.max(0f, cs), 1.2f);
            float y = top.y + (hem - top.y) * vv;
            float rx = top.rx + (.118f - top.rx) * vv;
            float rz = top.rz + (.175f - top.rz) * smooth(vv * 1.6f);
            float flare = 1f + .05f * FastMath.sin(vv * 6f + th * 3f);
            out.set(rx * FastMath.sin(th) * flare, y, rz * cs * flare);
        };
        ProceduralMesh.Tint tint = (r, c, q) -> {
            float vv = r / (float) (rows - 1);
            return strand(col, ProceduralMesh.hash(c, 11), 1f - vv, .4f * (1f - vv));
        };
        add(root, "Hair", ProceduralMesh.grid(rows, cols, false, pos, tint), mat);
    }

    private static void ponytail(Node root, Head h, ColorRGBA col, Material mat) {
        Vector3f[] pts = {
                new Vector3f(0, h.y(.93f), .085f), new Vector3f(0, h.y(.9f) + .01f, .15f),
                new Vector3f(0, h.y(.55f), .195f), new Vector3f(0, h.y(.05f), .185f), new Vector3f(0, h.y(-.5f), .16f)};
        float[] rad = {.03f, .042f, .04f, .03f, .012f};
        final int rows = 20, cols = 14;
        Vector3f[] c = new Vector3f[rows], tg = new Vector3f[rows];
        float[] rr = new float[rows];
        for (int i = 0; i < rows; i++) {
            float f = i / (float) (rows - 1) * (pts.length - 1);
            int k = Math.min((int) f, pts.length - 2);
            float t = f - k;
            Vector3f a = pts[Math.max(k - 1, 0)], b = pts[k], cc = pts[k + 1], d = pts[Math.min(k + 2, pts.length - 1)];
            c[i] = new Vector3f(cr(a.x, b.x, cc.x, d.x, t), cr(a.y, b.y, cc.y, d.y, t), cr(a.z, b.z, cc.z, d.z, t));
            rr[i] = FastMath.interpolateLinear(t, rad[k], rad[k + 1]);
        }
        for (int i = 0; i < rows; i++) tg[i] = c[Math.min(i + 1, rows - 1)].subtract(c[Math.max(i - 1, 0)]).normalizeLocal();
        ProceduralMesh.Pos pos = (r, q, out) -> {
            Vector3f n = tg[r].cross(Vector3f.UNIT_X);
            if (n.lengthSquared() < 1e-6f) n.set(Vector3f.UNIT_Z);
            n.normalizeLocal();
            Vector3f b = tg[r].cross(n).normalizeLocal();
            float a = FastMath.TWO_PI * q / cols;
            out.set(c[r]).addLocal(n.mult(FastMath.cos(a) * rr[r])).addLocal(b.mult(FastMath.sin(a) * rr[r]));
        };
        ProceduralMesh.Tint tint = (r, q, p) -> strand(col, ProceduralMesh.hash(q, 5), 0f, .3f);
        add(root, "Hair", ProceduralMesh.grid(rows, cols, true, pos, tint), mat);
    }

    private static float cr(float a, float b, float c, float d, float t) {
        return .5f * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t);
    }

    private static Mesh ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz, ColorRGBA col, int seed) {
        final int rows = 14, cols = 20;
        ProceduralMesh.Pos pos = (r, c, out) -> {
            float a = -FastMath.HALF_PI + FastMath.PI * r / (rows - 1);
            float k = FastMath.cos(a);
            float th = FastMath.TWO_PI * c / cols;
            out.set(cx + rx * k * FastMath.sin(th), cy + ry * FastMath.sin(a), cz + rz * k * FastMath.cos(th));
        };
        return ProceduralMesh.grid(rows, cols, true, pos,
                (r, c, q) -> strand(col, ProceduralMesh.hash(c, seed), 0f, .3f));
    }

    // ---------------------------------------------------------------- beard

    /** Thickness of the beard in front of the mouth, so lips can be placed on top of it. */
    public static float beardThickness(int style) {
        switch (style) {
            case 2: return .005f;
            case 3: return .009f;
            case 4: return .006f;
            case 5: return .008f;
            default: return 0f;
        }
    }

    public static void beard(Node root, Head h, int style, ColorRGBA col, ColorRGBA skin, Material mat) {
        if (style < 2) return;
        if (style == 2) shell(root, h, col, skin, mat, 1.45f, 0f, .30f, .50f, .0045f, 0f);
        if (style == 3) shell(root, h, col, skin, mat, 1.55f, 0f, .30f, .56f, .007f, .016f);
        if (style == 4) {
            shell(root, h, col, skin, mat, .46f, 0f, .27f, .27f, .008f, .010f);
            shell(root, h, col, skin, mat, .60f, .235f, .31f, .31f, .008f, 0f);
        }
        if (style == 5) shell(root, h, col, skin, mat, .62f, .235f, .31f, .31f, .008f, 0f);
    }

    private static void shell(Node root, Head h, ColorRGBA col, ColorRGBA skin, Material mat, float halfAngle,
                              float tLow, float tHighC, float tHighE, float thick, float chin) {
        final int rows = 12, cols = 36;
        final float[] fade = new float[rows * cols];
        ProceduralMesh.Pos pos = (r, c, out) -> {
            float a = (c / (float) (cols - 1) * 2f - 1f);
            float th = FastMath.PI + a * halfAngle;
            float cw = Math.abs(a);
            float u = r / (float) (rows - 1);
            float tHigh = tHighC + (tHighE - tHighC) * cw * cw;
            float t = tLow + (tHigh - tLow) * u;
            float f = smooth((1f - cw) * 5f) * smooth((1f - u) * 6f);
            fade[r * cols + c] = f;
            float centre = (1f - cw) * (1f - cw);
            float d = .0008f + thick * f + chin * (1f - u) * (1f - u) * centre;
            h.point(t, th, d, out);
            out.y -= chin * 1.2f * (1f - u) * (1f - u) * centre;
        };
        ProceduralMesh.Tint tint = (r, c, q) -> {
            float f = fade[r * cols + c];
            ColorRGBA s = strand(col, ProceduralMesh.hash(c, 13) * .6f + ProceduralMesh.hash(r, c) * .4f, 0f, .2f);
            return mix(skin, s, .35f + .65f * f);
        };
        add(root, "Beard", ProceduralMesh.grid(rows, cols, false, pos, tint), mat);
    }
}
