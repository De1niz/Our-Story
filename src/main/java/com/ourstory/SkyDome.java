package com.ourstory;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

/** Gradient sky with a sun glow and soft clouds, painted with vertex colours. Follows the camera. */
public final class SkyDome {
    private static final float RADIUS = 250f;
    private final Geometry dome;

    public SkyDome(AssetManager assets) {
        final int rows = 44, cols = 110;
        final Vector3f sun = new Vector3f(.45f, 1f, .35f).normalizeLocal();
        ProceduralMesh.Pos pos = (r, c, out) -> {
            float e = -.25f + (FastMath.HALF_PI + .25f) * r / (rows - 1);
            float th = FastMath.TWO_PI * c / cols;
            out.set(RADIUS * FastMath.cos(e) * FastMath.sin(th), RADIUS * FastMath.sin(e),
                    RADIUS * FastMath.cos(e) * FastMath.cos(th));
        };
        ProceduralMesh.Tint tint = (r, c, p) -> {
            Vector3f d = p.normalize();
            float h = FastMath.clamp(d.y, 0f, 1f);
            float k = (float) Math.pow(h, .55);
            ColorRGBA horizon = new ColorRGBA(.93f, .90f, .86f, 1f);
            ColorRGBA zenith = new ColorRGBA(.27f, .50f, .85f, 1f);
            float cr = horizon.r + (zenith.r - horizon.r) * k;
            float cg = horizon.g + (zenith.g - horizon.g) * k;
            float cb = horizon.b + (zenith.b - horizon.b) * k;
            if (d.y < 0f) { cr = horizon.r * .85f; cg = horizon.g * .88f; cb = horizon.b * .9f; }
            float glow = Math.max(0f, d.dot(sun));
            float g1 = (float) Math.pow(glow, 18f) * .35f, g2 = glow > .9985f ? 1f : 0f;
            cr += g1 + g2; cg += g1 * .9f + g2; cb += g1 * .65f + g2;
            float th = FastMath.TWO_PI * c / cols;
            float cloud = fbm(FastMath.cos(th) * 3.2f + 5f, FastMath.sin(th) * 3.2f + d.y * 7f);
            cloud = smooth((cloud - .52f) / .22f) * smooth(d.y * 5f) * (1f - .5f * h);
            cr += (1f - cr) * cloud * .85f; cg += (1f - cg) * cloud * .85f; cb += (1f - cb) * cloud * .85f;
            return new ColorRGBA(Math.min(cr, 1f), Math.min(cg, 1f), Math.min(cb, 1f), 1f);
        };
        dome = new Geometry("Sky", ProceduralMesh.grid(rows, cols, true, pos, tint));
        Material m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setBoolean("VertexColor", true);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        m.getAdditionalRenderState().setDepthWrite(false);
        dome.setMaterial(m);
        dome.setQueueBucket(RenderQueue.Bucket.Sky);
        dome.setCullHint(Spatial.CullHint.Never);
        dome.setShadowMode(RenderQueue.ShadowMode.Off);
    }

    public Geometry geometry() { return dome; }

    public void follow(Vector3f cameraPosition) { dome.setLocalTranslation(cameraPosition); }

    private static float smooth(float x) { x = FastMath.clamp(x, 0f, 1f); return x * x * (3f - 2f * x); }

    private static float vnoise(float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = smooth(x - xi), fy = smooth(y - yi);
        float a = ProceduralMesh.hash(xi, yi), b = ProceduralMesh.hash(xi + 1, yi);
        float c = ProceduralMesh.hash(xi, yi + 1), d = ProceduralMesh.hash(xi + 1, yi + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static float fbm(float x, float y) {
        return .55f * vnoise(x, y) + .3f * vnoise(x * 2.1f, y * 2.1f) + .15f * vnoise(x * 4.3f, y * 4.3f);
    }
}
