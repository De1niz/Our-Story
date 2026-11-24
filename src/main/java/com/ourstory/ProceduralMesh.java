package com.ourstory;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;

/** Smooth, vertex-coloured grid meshes used to build realistic bodies, faces and hair. */
public final class ProceduralMesh {
    private ProceduralMesh() {}

    /** Position of one grid vertex. */
    public interface Pos { void at(int row, int col, Vector3f out); }
    /** Colour of one grid vertex. */
    public interface Tint { ColorRGBA at(int row, int col, Vector3f p); }

    /** One horizontal cross-section (super-ellipse) of a loft. */
    public static final class Ring {
        public float y, cx, cz, rx, rz, n;
        public Ring(float y, float cx, float cz, float rx, float rz, float n) {
            this.y = y; this.cx = cx; this.cz = cz; this.rx = rx; this.rz = rz; this.n = n;
        }
    }

    public static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    /** Smooth 2D value noise in 0..1. */
    public static float noise(float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = x - xi, fy = y - yi;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        float a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
        float top = a + (b - a) * fx, bottom = c + (d - c) * fx;
        return top + (bottom - top) * fy;
    }

    public static float spow(float v, float e) {
        return Math.signum(v) * (float) Math.pow(Math.abs(v), e);
    }

    /** Catmull-Rom interpolation of ring control points at u in [0,1]. */
    public static Ring sample(Ring[] c, float u) {
        float f = FastMath.clamp(u, 0f, 1f) * (c.length - 1);
        int i = Math.min((int) f, c.length - 2);
        float t = f - i;
        Ring p0 = c[Math.max(i - 1, 0)], p1 = c[i], p2 = c[i + 1], p3 = c[Math.min(i + 2, c.length - 1)];
        return new Ring(cr(p0.y, p1.y, p2.y, p3.y, t), cr(p0.cx, p1.cx, p2.cx, p3.cx, t),
                cr(p0.cz, p1.cz, p2.cz, p3.cz, t), Math.max(0.0005f, cr(p0.rx, p1.rx, p2.rx, p3.rx, t)),
                Math.max(0.0005f, cr(p0.rz, p1.rz, p2.rz, p3.rz, t)), cr(p0.n, p1.n, p2.n, p3.n, t));
    }

    private static float cr(float a, float b, float c, float d, float t) {
        return .5f * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t);
    }

    /** Point on a ring at angle theta (0 = back/+Z, PI = front/-Z). */
    public static void ringPoint(Ring r, float theta, Vector3f out) {
        float e = 2f / Math.max(r.n, 1.2f);
        out.set(r.cx + r.rx * spow(FastMath.sin(theta), e), r.y, r.cz + r.rz * spow(FastMath.cos(theta), e));
    }

    /** Closed loft through control rings. Rows are resampled for smooth shading. */
    public static Mesh loft(Ring[] control, int rows, int cols, Tint tint) {
        Ring[] rs = new Ring[rows];
        for (int i = 0; i < rows; i++) rs[i] = sample(control, i / (float) (rows - 1));
        Pos pos = (r, c, out) -> ringPoint(rs[r], FastMath.TWO_PI * c / cols, out);
        return grid(rows, cols, true, pos, tint);
    }

    /** Generic grid mesh with smooth normals, automatic outward orientation and vertex colours. */
    public static Mesh grid(int rows, int cols, boolean wrap, Pos pos, Tint tint) {
        return grid(rows, cols, wrap, pos, tint, null);
    }

    /** Same as above; for flat sheets pass the direction the visible side should face as upHint. */
    public static Mesh grid(int rows, int cols, boolean wrap, Pos pos, Tint tint, Vector3f upHint) {
        int n = rows * cols;
        Vector3f[] p = new Vector3f[n];
        Vector3f tmp = new Vector3f();
        Vector3f center = new Vector3f();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                pos.at(r, c, tmp);
                p[r * cols + c] = tmp.clone();
                center.addLocal(tmp);
            }
        center.divideLocal(n);
        Vector3f[] nm = new Vector3f[n];
        float orient = 0;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                int c1 = wrap ? (c + 1) % cols : Math.min(c + 1, cols - 1);
                int c0 = wrap ? (c + cols - 1) % cols : Math.max(c - 1, 0);
                Vector3f tc = p[r * cols + c1].subtract(p[r * cols + c0]);
                Vector3f tr = p[Math.min(r + 1, rows - 1) * cols + c].subtract(p[Math.max(r - 1, 0) * cols + c]);
                Vector3f v = tr.cross(tc);
                Vector3f out = p[r * cols + c].subtract(center);
                if (v.lengthSquared() < 1e-14f) v = out.lengthSquared() < 1e-12f ? Vector3f.UNIT_Y.clone() : out.clone();
                v.normalizeLocal();
                orient += upHint != null ? v.dot(upHint) : v.dot(out);
                nm[r * cols + c] = v;
            }
        boolean flip = orient < 0;
        float[] pb = new float[n * 3], nb = new float[n * 3], cb = new float[n * 4], tb = new float[n * 2];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                Vector3f q = p[i], v = nm[i];
                if (flip) v = v.negate();
                pb[i * 3] = q.x; pb[i * 3 + 1] = q.y; pb[i * 3 + 2] = q.z;
                nb[i * 3] = v.x; nb[i * 3 + 1] = v.y; nb[i * 3 + 2] = v.z;
                ColorRGBA col = tint == null ? ColorRGBA.White : tint.at(r, c, q);
                cb[i * 4] = col.r; cb[i * 4 + 1] = col.g; cb[i * 4 + 2] = col.b; cb[i * 4 + 3] = col.a;
                tb[i * 2] = c / (float) Math.max(cols - 1, 1);
                tb[i * 2 + 1] = r / (float) Math.max(rows - 1, 1);
            }
        int cc = wrap ? cols : cols - 1;
        int[] ib = new int[(rows - 1) * cc * 6];
        int k = 0;
        for (int r = 0; r < rows - 1; r++)
            for (int c = 0; c < cc; c++) {
                int c1 = (c + 1) % cols;
                int a = r * cols + c, b = r * cols + c1, d = (r + 1) * cols + c, e = (r + 1) * cols + c1;
                if (!flip) { ib[k++] = a; ib[k++] = d; ib[k++] = b; ib[k++] = b; ib[k++] = d; ib[k++] = e; }
                else { ib[k++] = a; ib[k++] = b; ib[k++] = d; ib[k++] = b; ib[k++] = e; ib[k++] = d; }
            }
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, pb);
        m.setBuffer(VertexBuffer.Type.Normal, 3, nb);
        m.setBuffer(VertexBuffer.Type.Color, 4, cb);
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, tb);
        m.setBuffer(VertexBuffer.Type.Index, 3, ib);
        m.updateBound();
        m.updateCounts();
        return m;
    }
}
