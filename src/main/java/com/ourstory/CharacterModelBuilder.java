package com.ourstory;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Sphere;
import com.ourstory.ProceduralMesh.Ring;
import java.util.HashMap;
import java.util.Map;

/**
 * Procedural, smooth-shaded human model (about 1.76 m tall, feet at y = 0, face looks toward -Z).
 * Every mesh carries a "part" tag (Face, Eyes, Nose, Hair, Beard, Body, Legs, Feet) used for click picking.
 */
public final class CharacterModelBuilder {
    private final AssetManager assets;
    private final Map<String, Material> cache = new HashMap<>();
    private final Mesh unitSphere = new Sphere(14, 20, 1f);

    public CharacterModelBuilder(AssetManager assets) { this.assets = assets; }

    public static float heightScale(CharacterData d) {
        return new float[]{.94f, 1f, 1.06f}[d.choices[CharacterData.HEIGHT]];
    }

    // ------------------------------------------------------------ materials

    private Material vertexMat(float spec, float shine, boolean twoSided) {
        String key = "v" + spec + "/" + shine + "/" + twoSided;
        Material m = cache.get(key);
        if (m == null) {
            m = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
            m.setBoolean("UseVertexColor", true);
            m.setBoolean("UseMaterialColors", true);
            m.setColor("Diffuse", ColorRGBA.White);
            m.setColor("Ambient", ColorRGBA.White.mult(.75f));
            m.setColor("Specular", ColorRGBA.White.mult(spec));
            m.setFloat("Shininess", shine);
            if (twoSided) m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            cache.put(key, m);
        }
        return m;
    }

    private Material colorMat(ColorRGBA c, float spec, float shine) {
        String key = "c" + c + "/" + spec + "/" + shine;
        Material m = cache.get(key);
        if (m == null) {
            m = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
            m.setBoolean("UseMaterialColors", true);
            m.setColor("Diffuse", c);
            m.setColor("Ambient", c.mult(.75f));
            m.setColor("Specular", ColorRGBA.White.mult(spec));
            m.setFloat("Shininess", shine);
            cache.put(key, m);
        }
        return m;
    }

    // ------------------------------------------------------------ helpers

    private static ColorRGBA mix(ColorRGBA a, ColorRGBA b, float w) {
        w = FastMath.clamp(w, 0f, 1f);
        return new ColorRGBA(a.r + (b.r - a.r) * w, a.g + (b.g - a.g) * w, a.b + (b.b - a.b) * w, 1f);
    }

    private static ColorRGBA mul(ColorRGBA a, float k) {
        return new ColorRGBA(FastMath.clamp(a.r * k, 0, 1), FastMath.clamp(a.g * k, 0, 1), FastMath.clamp(a.b * k, 0, 1), 1f);
    }

    private static float smooth(float x) { x = FastMath.clamp(x, 0f, 1f); return x * x * (3f - 2f * x); }

    private Geometry mesh(Node parent, String part, Mesh m, Material mat) {
        Geometry g = new Geometry(part, m);
        g.setMaterial(mat);
        g.setUserData("part", part);
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        parent.attachChild(g);
        return g;
    }

    private Geometry ell(Node parent, String part, float x, float y, float z, float sx, float sy, float sz,
                         Material mat, float rx, float ry, float rz) {
        Geometry g = mesh(parent, part, unitSphere, mat);
        g.setLocalTranslation(x, y, z);
        g.setLocalScale(sx, sy, sz);
        if (rx != 0 || ry != 0 || rz != 0) g.setLocalRotation(new Quaternion().fromAngles(rx, ry, rz));
        return g;
    }

    private Geometry ell(Node parent, String part, float x, float y, float z, float sx, float sy, float sz, Material mat) {
        return ell(parent, part, x, y, z, sx, sy, sz, mat, 0, 0, 0);
    }

    private static Ring R(float y, float cx, float cz, float rx, float rz, float n) { return new Ring(y, cx, cz, rx, rz, n); }

    private static ProceduralMesh.Tint flat(ColorRGBA c, float noise) {
        return (r, q, p) -> mul(c, 1f + noise * (ProceduralMesh.hash(r, q) - .5f));
    }

    // ------------------------------------------------------------ build

    public Node build(CharacterData d) {
        Node root = new Node("Player " + d.playerNumber + " Preview");
        final ColorRGBA skin = CharacterData.SKIN_COLORS[d.choices[CharacterData.SKIN]];
        final ColorRGBA hairCol = CharacterData.HAIR_COLORS[d.choices[CharacterData.HAIR_COLOR]];
        final ColorRGBA eyeCol = CharacterData.EYE_COLORS[d.choices[CharacterData.EYES]];
        final ColorRGBA cloth = CharacterData.OUTFIT_COLORS[d.choices[CharacterData.OUTFIT_COLOR]];
        final ColorRGBA pants = new ColorRGBA(.17f, .21f, .29f, 1f);
        final int outfit = d.choices[CharacterData.OUTFIT];
        final boolean dress = outfit == 2, longSleeve = outfit == 1;
        final int beardStyle = d.choices[CharacterData.BEARD];

        // shoulder, chest, waist, hip, depth, belly, limb, thigh, bust
        float[][] bt = {
                {.93f, .90f, .88f, .92f, .88f, 0f, .85f, .88f, 0f},
                {1.08f, 1.10f, .92f, .96f, 1.0f, 0f, 1.08f, 1.05f, 0f},
                {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, .008f, 1f, 1f, 0f},
                {1.17f, 1.15f, 1.12f, 1.05f, 1.12f, .015f, 1.2f, 1.1f, 0f},
                {.92f, 1.0f, .82f, 1.2f, 1.0f, 0f, .95f, 1.2f, .02f},
                {1.12f, 1.18f, 1.35f, 1.2f, 1.3f, .05f, 1.3f, 1.3f, 0f}};
        float[] b = bt[d.choices[CharacterData.BUILD]];
        final float sF = b[0], cF = b[1], wF = b[2], hF = b[3], dp = b[4], belly = b[5], L = b[6], T = b[7], bust = b[8];

        Material skinMat = vertexMat(.16f, 26f, false);
        Material clothMat = vertexMat(.04f, 8f, false);
        Material hairMat = vertexMat(.30f, 14f, true);
        // Sphere meshes have no vertex colours, so they use plain coloured materials.
        Material skinSph = colorMat(skin, .16f, 26f);
        Material clothSph = colorMat(cloth, .04f, 8f);
        Material pantsSph = colorMat(pants, .04f, 8f);

        // ---- torso
        Ring[] torso = {
                R(.78f, 0, 0, .06f, .05f, 2f),
                R(.82f, 0, 0, .150f * hF, .098f * dp, 2.2f),
                R(.92f, 0, 0, .168f * hF, .108f * dp, 2.2f),
                R(1.00f, 0, -belly * .5f, .148f * wF, .098f * dp + belly * .4f, 2.2f),
                R(1.08f, 0, -belly, .146f * wF, .096f * dp + belly * .6f, 2.2f),
                R(1.20f, 0, -bust * .3f, .160f * cF, .102f * dp, 2.3f),
                R(1.31f, 0, -bust, .185f * cF, .112f * dp + bust, 2.4f),
                R(1.41f, 0, 0, .196f * sF, .092f * dp, 2.7f),
                R(1.47f, 0, 0, .100f * sF, .066f, 2.4f),
                R(1.51f, 0, 0, .050f, .050f, 2f),
                R(1.525f, 0, 0, .010f, .010f, 2f)};
        final int tcols = 40;
        ProceduralMesh.Tint torsoTint = (r, c, p) -> {
            float th = FastMath.TWO_PI * c / tcols;
            float front = Math.max(0f, -FastMath.cos(th));
            float line = 1.452f - .05f * front * front;
            float n = 1f + .05f * (ProceduralMesh.hash(r, c) - .5f);
            if (p.y > line + .012f) return skin;
            ColorRGBA body = cloth;
            if (!dress && p.y < .915f) body = pants;
            if (!dress && Math.abs(p.y - .915f) < .008f) body = mul(cloth, .7f);
            ColorRGBA out = mul(body, n);
            return p.y > line - .012f ? mix(out, skin, smooth((p.y - (line - .012f)) / .024f)) : out;
        };
        mesh(root, "Body", ProceduralMesh.loft(torso, 46, tcols, torsoTint), clothMat);

        // ---- neck
        Ring[] neck = {R(1.42f, 0, 0, .056f * L, .056f * L, 2f), R(1.50f, 0, -.004f, .050f, .052f, 2f),
                R(1.565f, 0, -.008f, .048f, .050f, 2f)};
        mesh(root, "Face", ProceduralMesh.loft(neck, 8, 20, flat(mul(skin, .94f), .02f)), skinMat);

        // ---- arms
        for (int s = -1; s <= 1; s += 2) {
            Node shoulder = new Node("Shoulder" + s);
            shoulder.setLocalTranslation(s * .188f * sF, 1.405f, 0);
            shoulder.setLocalRotation(new Quaternion().fromAngles(0, 0, s * (.10f + (wF - 1f) * .12f)));
            root.attachChild(shoulder);
            ColorRGBA topCol = dress ? skin : cloth;
            ell(shoulder, "Body", 0, 0, 0, .056f * L, .056f * L, .056f * L, dress ? skinSph : clothSph);
            Ring[] upper = {R(.04f, 0, 0, .050f * L, .048f * L, 2f), R(-.10f, 0, 0, .046f * L, .045f * L, 2f),
                    R(-.20f, 0, 0, .040f * L, .040f * L, 2f), R(-.29f, 0, 0, .036f * L, .036f * L, 2f)};
            mesh(shoulder, "Body", ProceduralMesh.loft(upper, 14, 18, flat(skin, .02f)), skinMat);
            if (!dress) {
                float end = longSleeve ? -.29f : -.13f;
                float mid = longSleeve ? -.17f : -.06f;
                Ring[] sl = {R(.05f, 0, 0, .056f * L, .054f * L, 2f), R(mid, 0, 0, .052f * L, .050f * L, 2f),
                        R(end, 0, 0, longSleeve ? .043f * L : .050f * L, longSleeve ? .043f * L : .048f * L, 2f)};
                mesh(shoulder, "Body", ProceduralMesh.loft(sl, 10, 18, flat(topCol, .05f)), clothMat);
            }
            Node elbow = new Node("Elbow" + s);
            elbow.setLocalTranslation(0, -.29f, 0);
            elbow.setLocalRotation(new Quaternion().fromAngles(.22f, 0, 0));
            shoulder.attachChild(elbow);
            ell(elbow, "Body", 0, 0, 0, .038f * L, .038f * L, .038f * L, longSleeve ? clothSph : skinSph);
            Ring[] fore = {R(0f, 0, 0, .036f * L, .035f * L, 2f), R(-.08f, 0, 0, .035f * L, .033f * L, 2f),
                    R(-.18f, 0, 0, .030f * L, .028f * L, 2f), R(-.26f, 0, 0, .025f * L, .023f * L, 2f)};
            mesh(elbow, "Body", ProceduralMesh.loft(fore, 12, 16, flat(skin, .02f)), skinMat);
            if (longSleeve) {
                Ring[] fs = {R(.01f, 0, 0, .041f * L, .040f * L, 2f), R(-.10f, 0, 0, .039f * L, .037f * L, 2f),
                        R(-.22f, 0, 0, .032f * L, .030f * L, 2f)};
                mesh(elbow, "Body", ProceduralMesh.loft(fs, 8, 16, flat(cloth, .05f)), clothMat);
            }
            Node hand = new Node("Hand");
            hand.setLocalTranslation(0, -.26f, 0);
            hand.setLocalRotation(new Quaternion().fromAngles(.12f, 0, 0));
            elbow.attachChild(hand);
            Ring[] hr = {R(.0f, 0, 0, .024f, .016f, 2f), R(-.03f, 0, 0, .033f, .018f, 2.4f),
                    R(-.07f, 0, 0, .032f, .016f, 2.4f), R(-.095f, 0, 0, .023f, .012f, 2f),
                    R(-.108f, 0, 0, .004f, .004f, 2f)};
            mesh(hand, "Body", ProceduralMesh.loft(hr, 12, 14, flat(mul(skin, .97f), .02f)), skinMat);
            ell(hand, "Body", -s * .031f, -.045f, -.012f, .010f, .030f, .012f, skinSph, 0, 0, s * .25f);
        }

        // ---- legs, skirt, shoes
        Material shoeMat = colorMat(new ColorRGBA(.13f, .12f, .12f, 1f), .25f, 30f);
        Material soleMat = colorMat(new ColorRGBA(.88f, .87f, .84f, 1f), .1f, 10f);
        for (int s = -1; s <= 1; s += 2) {
            float lx = s * .088f * hF;
            Node hip = new Node("Hip" + s);
            hip.setLocalTranslation(lx, .90f, 0);
            hip.setLocalRotation(new Quaternion().fromAngles(0, 0, s * .012f));
            root.attachChild(hip);
            float e = dress ? 0f : .005f;
            ColorRGBA legCol = dress ? skin : pants;
            Material legMat = dress ? skinMat : clothMat;
            Ring[] thigh = {R(.04f, 0, 0, .086f * T + e, .084f * T + e, 2f), R(-.05f, 0, 0, .088f * T + e, .085f * T + e, 2f),
                    R(-.20f, 0, 0, .076f * T + e, .074f * T + e, 2f), R(-.34f, 0, 0, .060f * T + e, .062f * T + e, 2f),
                    R(-.41f, 0, 0, .054f * T + e, .056f * T + e, 2f)};
            mesh(hip, "Legs", ProceduralMesh.loft(thigh, 14, 20, flat(legCol, dress ? .02f : .06f)), legMat);
            Node knee = new Node("Knee" + s);
            knee.setLocalTranslation(0, -.41f, 0);
            hip.attachChild(knee);
            ell(knee, "Legs", 0, 0, 0, .056f * T + e, .056f * T + e, .056f * T + e, dress ? skinSph : pantsSph);
            Ring[] calf = {R(0f, 0, 0, .054f * T + e, .056f * T + e, 2f), R(-.10f, 0, .008f, .057f * T + e, .058f * T + e, 2f),
                    R(-.25f, 0, .004f, .042f * T + e, .044f * T + e, 2f), R(-.36f, 0, 0, .034f + e, .034f + e, 2f),
                    R(-.40f, 0, 0, .031f + e, .031f + e, 2f)};
            mesh(knee, "Legs", ProceduralMesh.loft(calf, 14, 18, flat(legCol, dress ? .02f : .06f)), legMat);
            // shoes
            float sx = s * .090f * hF;
            ell(root, "Feet", sx, .048f, -.040f, .046f, .042f, .115f, shoeMat);
            ell(root, "Feet", sx, .038f, -.105f, .045f, .034f, .075f, shoeMat);
            ell(root, "Feet", sx, .010f, -.050f, .049f, .012f, .150f, soleMat);
        }
        if (dress) {
            Ring[] skirt = {R(1.07f, 0, -belly, .150f * wF, .098f * dp + belly * .5f, 2.2f),
                    R(.95f, 0, 0, .185f * hF, .125f * dp, 2.2f), R(.78f, 0, 0, .225f * hF, .160f * dp, 2.1f),
                    R(.60f, 0, 0, .260f * hF, .190f * dp, 2.0f), R(.56f, 0, 0, .265f * hF, .194f * dp, 2.0f)};
            ProceduralMesh.Tint st = (r, c, p) -> mul(cloth, (p.y < .575f ? .82f : 1f) * (1f + .05f * (ProceduralMesh.hash(c, 3) - .5f)));
            mesh(root, "Body", ProceduralMesh.loft(skirt, 24, 40, st), clothMat);
        }

        // ---- head
        final Head h = new Head(d.choices[CharacterData.FACE]);
        final int hrows = 32, hcols = 56;
        final float[] rowT = new float[hrows];
        for (int i = 0; i < hrows; i++) rowT[i] = (1f - FastMath.cos(FastMath.PI * i / (hrows - 1))) * .5f;
        final ColorRGBA stub = mix(hairCol, skin, .45f);
        ProceduralMesh.Pos hp = (r, c, out) -> h.point(rowT[r], FastMath.TWO_PI * c / hcols, 0f, out);
        ProceduralMesh.Tint ht = (r, c, p) -> {
            float t = rowT[r];
            float th = FastMath.TWO_PI * c / hcols;
            float fw = FastMath.clamp(-FastMath.cos(th), 0f, 1f);
            float ax = Math.abs(p.x);
            float blush = (float) Math.exp(-(Math.pow((t - .40f) / .09f, 2) + Math.pow((ax - .05f) / .028f, 2))) * smooth(fw * 2f);
            float eye = (float) Math.exp(-(Math.pow((t - .50f) / .03f, 2) + Math.pow((ax - .03f) / .02f, 2))) * fw;
            ColorRGBA s = mix(skin, new ColorRGBA(Math.min(1f, skin.r * 1.08f + .02f), skin.g * .85f, skin.b * .85f, 1f), .4f * blush);
            s = mix(s, mul(s, .88f), eye);
            if (beardStyle >= 1) {
                float low = smooth((.42f - t) / .14f);
                float front = smooth((fw - .15f) * 2.2f);
                float side = smooth(1f - Math.abs(FastMath.sin(th)) * .0f);
                float k = (beardStyle == 1 ? .55f : .30f) * low * front * side
                        * (.65f + .35f * ProceduralMesh.hash(r * 3, c));
                s = mix(s, stub, k);
            }
            return s;
        };
        mesh(root, "Face", ProceduralMesh.grid(hrows, hcols, true, hp, ht), skinMat);

        // ears
        for (int s = -1; s <= 1; s += 2) {
            ell(root, "Face", s * (h.halfWidth(.50f) + .002f), h.y(.52f), .008f, .0085f, .029f, .019f,
                    skinSph, 0, 0, s * .15f);
        }
        // eyes
        Material white = colorMat(new ColorRGBA(.95f, .94f, .92f, 1f), .7f, 70f);
        Material irisMat = colorMat(eyeCol, .5f, 50f);
        Material dark = colorMat(new ColorRGBA(.02f, .02f, .02f, 1f), .6f, 80f);
        Material glint = colorMat(ColorRGBA.White, 1f, 100f);
        Material lidMat = colorMat(mul(skin, .93f), .12f, 20f);
        for (int s = -1; s <= 1; s += 2) {
            float ex = s * .031f * h.width;
            Vector3f sf = h.front(.54f, ex, 0f);
            float cz = sf.z + .0035f;
            ell(root, "Eyes", ex, sf.y, cz, .0115f, .0115f, .0115f, white);
            ell(root, "Eyes", ex, sf.y, cz - .0100f, .0062f, .0062f, .0021f, irisMat);
            ell(root, "Eyes", ex, sf.y, cz - .0112f, .0029f, .0029f, .0013f, dark);
            ell(root, "Eyes", ex + .0025f, sf.y + .0026f, cz - .0120f, .0012f, .0012f, .0008f, glint);
            ell(root, "Eyes", ex, sf.y + .0072f, cz - .0020f, .0127f, .0068f, .0125f, lidMat);
            ell(root, "Eyes", ex, sf.y - .0085f, cz - .0015f, .0120f, .0034f, .0120f, lidMat);
            ell(root, "Eyes", ex, sf.y + .0034f, cz - .0105f, .0105f, .0012f, .0030f,
                    colorMat(mul(hairCol, .5f), .1f, 10f));
        }
        // brows
        ColorRGBA browCol = mix(hairCol, new ColorRGBA(.20f, .15f, .10f, 1f), .35f);
        Material browMat = colorMat(browCol, .1f, 10f);
        for (int s = -1; s <= 1; s += 2) {
            float bx = s * .034f * h.width;
            Vector3f bf = h.front(.645f, bx, 0f);
            ell(root, "Face", bx, bf.y, bf.z - .0005f, .0245f, .0045f, .0065f, browMat, 0, 0, s * .12f);
        }
        // nose
        float ns = new float[]{.85f, 1f, 1.2f}[d.choices[CharacterData.NOSE]];
        Vector3f nb = h.front(.45f, 0f, 0f);
        ell(root, "Nose", 0, nb.y, nb.z - .002f * ns, .0085f * ns, .029f, .0105f * ns, skinSph, .35f, 0, 0);
        Vector3f nt = h.front(.345f, 0f, 0f);
        ell(root, "Nose", 0, nt.y, nt.z - .0145f * ns, .0115f * ns, .0105f * ns, .0125f * ns, skinSph);
        for (int s = -1; s <= 1; s += 2) {
            Vector3f nw = h.front(.338f, s * .0125f * ns, 0f);
            ell(root, "Nose", s * .0125f * ns, nw.y, nw.z - .004f, .0085f * ns, .0085f * ns, .0095f * ns, skinSph);
            Vector3f nn = h.front(.322f, 0f, 0f);
            ell(root, "Nose", s * .0058f * ns, nn.y, nn.z - .0115f * ns, .0032f, .0022f, .0035f,
                    colorMat(new ColorRGBA(.14f, .07f, .06f, 1f), .0f, 5f));
        }
        // lips
        float lo = HairKit.beardThickness(beardStyle);
        ColorRGBA lipCol = mix(skin, new ColorRGBA(.62f, .26f, .27f, 1f), .5f);
        Material lipMat = colorMat(lipCol, .35f, 30f);
        Vector3f ul = h.front(.228f, 0f, 0f), ll = h.front(.196f, 0f, 0f);
        ell(root, "Face", 0, ul.y, ul.z - lo - .0035f, .0185f, .0042f, .0065f, lipMat);
        ell(root, "Face", 0, ll.y, ll.z - lo - .0045f, .0165f, .0058f, .0078f, lipMat);
        ell(root, "Face", 0, (ul.y + ll.y) * .5f - .0015f, ul.z - lo - .0035f, .0175f, .0009f, .0045f,
                colorMat(new ColorRGBA(.22f, .09f, .09f, 1f), .0f, 5f));

        // ---- hair + beard
        HairKit.hair(root, h, d.choices[CharacterData.HAIR], hairCol, skin, hairMat);
        HairKit.beard(root, h, beardStyle, hairCol, skin, hairMat);

        root.setLocalScale(heightScale(d));
        return root;
    }
}
