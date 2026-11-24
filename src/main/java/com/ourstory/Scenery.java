package com.ourstory;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.BatchNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.ourstory.ProceduralMesh.Ring;
import java.util.HashMap;
import java.util.Map;

/** Procedural scenery: trees, flowers, lanterns, signs, a fountain. Static parts are merged for speed. */
public final class Scenery {
    public static final ColorRGBA[] FLOWERS = {
            new ColorRGBA(.93f, .45f, .58f, 1f), new ColorRGBA(.95f, .30f, .32f, 1f), new ColorRGBA(.97f, .95f, .90f, 1f),
            new ColorRGBA(.98f, .82f, .30f, 1f), new ColorRGBA(.62f, .45f, .86f, 1f), new ColorRGBA(.98f, .62f, .74f, 1f)};

    private final WorldBuilder world;
    private final AssetManager assets;
    private final BatchNode decor = new BatchNode("Decor");
    private final Node fx = new Node("Effects");
    private final Map<String, Mesh> meshes = new HashMap<>();
    private final Map<String, Material> mats = new HashMap<>();
    private final Material veg, metal;

    public Scenery(WorldBuilder world) {
        this.world = world;
        this.assets = world.assets();
        world.node().attachChild(decor);
        world.node().attachChild(fx);
        veg = vertexMat(.05f, 6f);
        metal = vertexMat(.45f, 40f);
    }

    // ------------------------------------------------------------ materials

    private Material vertexMat(float spec, float shine) {
        String key = "v" + spec + "/" + shine;
        Material m = mats.get(key);
        if (m == null) {
            m = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
            m.setBoolean("UseVertexColor", true);
            m.setBoolean("UseMaterialColors", true);
            m.setColor("Diffuse", ColorRGBA.White);
            m.setColor("Ambient", ColorRGBA.White.mult(.75f));
            m.setColor("Specular", ColorRGBA.White.mult(spec));
            m.setFloat("Shininess", shine);
            mats.put(key, m);
        }
        return m;
    }

    /** Lit material that uses vertex colours (for procedural meshes made with ProceduralMesh). */
    public Material vertex(float spec, float shine) { return vertexMat(spec, shine); }

    public Material color(ColorRGBA c, float spec, float shine) {
        String key = "c" + c + "/" + spec + "/" + shine;
        Material m = mats.get(key);
        if (m == null) {
            m = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
            m.setBoolean("UseMaterialColors", true);
            m.setColor("Diffuse", c);
            m.setColor("Ambient", c.mult(.75f));
            m.setColor("Specular", ColorRGBA.White.mult(spec));
            m.setFloat("Shininess", shine);
            mats.put(key, m);
        }
        return m;
    }

    private Material glow(ColorRGBA c) {
        String key = "g" + c;
        Material m = mats.get(key);
        if (m == null) {
            m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setColor("Color", c);
            mats.put(key, m);
        }
        return m;
    }

    private Material halo(ColorRGBA c) {
        String key = "h" + c;
        Material m = mats.get(key);
        if (m == null) {
            m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setColor("Color", c);
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            m.getAdditionalRenderState().setDepthWrite(false);
            mats.put(key, m);
        }
        return m;
    }

    // ------------------------------------------------------------ mesh helpers

    private static float clamp01(float v) { return FastMath.clamp(v, 0f, 1f); }

    private static ColorRGBA mul(ColorRGBA c, float k) {
        return new ColorRGBA(clamp01(c.r * k), clamp01(c.g * k), clamp01(c.b * k), 1f);
    }

    /** Lumpy unit ellipsoid with shading baked into vertex colours (dark underneath, light on top). */
    private Mesh blob(String key, int seed, ColorRGBA base, float lumps) {
        Mesh m = meshes.get(key);
        if (m != null) return m;
        final int rows = 12, cols = 18;
        final float sd = seed * 1.7f;
        ProceduralMesh.Pos pos = (r, c, out) -> {
            float e = -FastMath.HALF_PI + FastMath.PI * r / (rows - 1);
            float th = FastMath.TWO_PI * c / cols;
            float rad = 1f + lumps * (ProceduralMesh.noise(FastMath.cos(th) * 1.8f + sd, FastMath.sin(th) * 1.8f + e * 2.4f) - .5f) * 2f;
            out.set(rad * FastMath.cos(e) * FastMath.sin(th), rad * FastMath.sin(e), rad * FastMath.cos(e) * FastMath.cos(th));
        };
        ProceduralMesh.Tint tint = (r, c, p) -> {
            float shade = .55f + .55f * (p.y * .5f + .5f);
            return mul(base, shade * (.86f + .28f * ProceduralMesh.hash(r * 7 + seed, c)));
        };
        m = ProceduralMesh.grid(rows, cols, true, pos, tint);
        meshes.put(key, m);
        return m;
    }

    private Geometry put(Node parent, Mesh mesh, Material mat, float x, float y, float z, float sx, float sy, float sz, float rotY) {
        Geometry g = new Geometry("Decor", mesh);
        g.setMaterial(mat);
        g.setLocalTranslation(x, y, z);
        g.setLocalScale(sx, sy, sz);
        if (rotY != 0f) g.setLocalRotation(new Quaternion().fromAngleAxis(rotY, Vector3f.UNIT_Y));
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        parent.attachChild(g);
        return g;
    }

    private Geometry box(Node parent, Material mat, float x, float y, float z, float hx, float hy, float hz) {
        Geometry g = new Geometry("Plank", new Box(hx, hy, hz));
        g.setMaterial(mat);
        g.setLocalTranslation(x, y, z);
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        parent.attachChild(g);
        return g;
    }

    private Geometry cyl(Node parent, Material mat, float x, float y, float z, float radius, float height, boolean upright) {
        Geometry g = new Geometry("Cylinder", new Cylinder(2, 48, radius, height, true));
        g.setMaterial(mat);
        g.setLocalTranslation(x, y, z);
        if (upright) g.setLocalRotation(new Quaternion().fromAngles(FastMath.HALF_PI, 0, 0));
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        parent.attachChild(g);
        return g;
    }

    // ------------------------------------------------------------ plants and stones

    /** kind 0 = green tree, 1 = cherry blossom. */
    public void tree(float x, float z, float s, int seed, int kind) {
        float h = 4.4f * s;
        Ring[] trunk = {new Ring(0, 0, 0, .64f * s, .64f * s, 2f), new Ring(.4f * s, 0, 0, .40f * s, .40f * s, 2f),
                new Ring(h * .5f, 0, 0, .30f * s, .30f * s, 2f), new Ring(h * .85f, 0, 0, .24f * s, .24f * s, 2f),
                new Ring(h, 0, 0, .15f * s, .15f * s, 2f)};
        final int sd = seed;
        Mesh tm = ProceduralMesh.loft(trunk, 12, 12, (r, c, p) -> mul(new ColorRGBA(.31f, .22f, .15f, 1f),
                .75f + .45f * ProceduralMesh.hash(c, sd) + .08f * ProceduralMesh.hash(r, c)));
        put(decor, tm, veg, x, 0, z, 1, 1, 1, 0);
        ColorRGBA base = kind == 1 ? new ColorRGBA(.96f, .68f, .78f, 1f)
                : new ColorRGBA(.17f + .07f * ProceduralMesh.hash(seed, 1), .36f + .10f * ProceduralMesh.hash(seed, 2), .15f, 1f);
        Mesh canopy = blob("leaf" + kind + "_" + (seed % 4), seed % 4, base, .16f);
        put(decor, canopy, veg, x, h + 1.4f * s, z, 2.7f * s, 2.2f * s, 2.7f * s, seed);
        for (int k = 0; k < 6; k++) {
            float a = k * FastMath.TWO_PI / 6f + seed;
            put(decor, canopy, veg, x + FastMath.sin(a) * 1.9f * s, h + (.5f + (k % 2) * .9f) * s,
                    z + FastMath.cos(a) * 1.9f * s, 1.7f * s, 1.4f * s, 1.7f * s, a * 2f);
        }
        world.cylinderCollider(x, h * .5f, z, .5f * s, h * .5f);
    }

    public void bush(float x, float z, float s, int seed) {
        ColorRGBA base = new ColorRGBA(.16f + .06f * ProceduralMesh.hash(seed, 3), .34f, .15f, 1f);
        Mesh b = blob("bush" + (seed % 3), seed % 3 + 10, base, .2f);
        put(decor, b, veg, x, .45f * s, z, 1.1f * s, .8f * s, 1.1f * s, seed);
        put(decor, b, veg, x + .8f * s, .35f * s, z + .3f * s, .75f * s, .6f * s, .75f * s, seed * 2f);
    }

    public void rock(float x, float z, float s, int seed) {
        Mesh r = blob("rock" + (seed % 3), seed % 3 + 20, new ColorRGBA(.52f, .52f, .50f, 1f), .22f);
        put(decor, r, veg, x, .3f * s, z, 1.0f * s, .65f * s, .8f * s, seed);
        if (s > 1.3f) world.collider(x, .4f * s, z, .8f * s, .45f * s, .7f * s);
    }

    public void flower(float x, float z, int colorIndex, float s) {
        ColorRGBA c = FLOWERS[colorIndex % FLOWERS.length];
        Mesh f = blob("flower" + (colorIndex % FLOWERS.length), colorIndex + 30, c, .1f);
        Mesh leaf = blob("leafy", 40, new ColorRGBA(.22f, .45f, .18f, 1f), .12f);
        put(decor, leaf, veg, x, .12f * s, z, .22f * s, .12f * s, .22f * s, x * 3f);
        put(decor, f, veg, x, .30f * s, z, .11f * s, .09f * s, .11f * s, 0);
    }

    public void flowerPatch(float cx, float cz, float radius, int count, java.util.Random rnd) {
        for (int i = 0; i < count; i++) {
            float a = rnd.nextFloat() * FastMath.TWO_PI, d = FastMath.sqrt(rnd.nextFloat()) * radius;
            flower(cx + FastMath.sin(a) * d, cz + FastMath.cos(a) * d, rnd.nextInt(FLOWERS.length), .8f + rnd.nextFloat() * .6f);
        }
    }

    // ------------------------------------------------------------ lantern, sign, fountain

    public void lantern(float x, float z, ColorRGBA light, float s) {
        Ring[] pole = {new Ring(0, 0, 0, .18f, .18f, 2f), new Ring(.15f, 0, 0, .11f, .11f, 2f),
                new Ring(.5f, 0, 0, .075f, .075f, 2f), new Ring(3.0f, 0, 0, .06f, .06f, 2f), new Ring(3.15f, 0, 0, .09f, .09f, 2f)};
        Mesh pm = ProceduralMesh.loft(pole, 12, 12, (r, c, p) -> new ColorRGBA(.10f, .10f, .12f, 1f));
        put(decor, pm, metal, x, 0, z, s, s, s, 0);
        Ring[] cap = {new Ring(3.85f, 0, 0, .36f, .36f, 2f), new Ring(3.98f, 0, 0, .30f, .30f, 2f), new Ring(4.22f, 0, 0, .02f, .02f, 2f)};
        Mesh cm = ProceduralMesh.loft(cap, 8, 12, (r, c, p) -> new ColorRGBA(.10f, .10f, .12f, 1f));
        put(decor, cm, metal, x, 0, z, s, s, s, 0);
        Mesh core = blob("lamp", 50, ColorRGBA.White, .02f);
        put(decor, core, glow(light), x, 3.5f * s, z, .30f * s, .42f * s, .30f * s, 0);
        ColorRGBA h = new ColorRGBA(light.r, light.g, light.b, .16f);
        put(fx, core, halo(h), x, 3.5f * s, z, 1.5f * s, 1.5f * s, 1.5f * s, 0).setShadowMode(RenderQueue.ShadowMode.Off);
        fx.setQueueBucket(RenderQueue.Bucket.Transparent);
        world.cylinderCollider(x, 1.5f, z, .2f * s, 1.5f);
    }

    /** Wooden sign whose front looks along the direction given by yaw (rotation about Y). */
    public void sign(float x, float z, float yaw, String text, float s) {
        Node pivot = new Node("Sign");
        pivot.setLocalTranslation(x, 0, z);
        pivot.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        pivot.setLocalScale(s);
        Material wood = color(new ColorRGBA(.40f, .27f, .16f, 1f), .08f, 8f);
        Material board = color(new ColorRGBA(.90f, .84f, .70f, 1f), .05f, 6f);
        box(pivot, wood, -.8f, .95f, 0f, .07f, .95f, .07f);
        box(pivot, wood, .8f, .95f, 0f, .07f, .95f, .07f);
        box(pivot, wood, 0f, 1.95f, 0f, 1.0f, .45f, .045f);
        box(pivot, board, 0f, 1.95f, .03f, .93f, .38f, .03f);
        decor.attachChild(pivot);
        BitmapFont font = assets.loadFont("Interface/Fonts/Default.fnt");
        BitmapText t = new BitmapText(font);
        t.setSize(.34f);
        t.setColor(ColorRGBA.Black);
        t.setText(text);
        t.setBox(new Rectangle(-.9f, .17f, 1.8f, .34f));
        t.setAlignment(BitmapFont.Align.Center);
        t.setVerticalAlignment(BitmapFont.VAlign.Center);
        t.setLocalTranslation(0f, 1.95f, .066f);
        Node holder = new Node("Sign text");
        holder.setLocalTranslation(x, 0, z);
        holder.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        holder.setLocalScale(s);
        holder.attachChild(t);
        fx.attachChild(holder);
        world.cylinderCollider(x, 1f * s, z, 1.0f * s, 1f * s);
    }

    public BitmapFont font() { return assets.loadFont("Interface/Fonts/Default.fnt"); }

    /** Square wooden game table, 2.4 m wide, surface at y = 1.0. */
    public void table(float x, float z) {
        Material wood = color(new ColorRGBA(.52f, .35f, .20f, 1f), .12f, 12f);
        Material dark = color(new ColorRGBA(.33f, .21f, .12f, 1f), .10f, 10f);
        box(decor, wood, x, .94f, z, 1.2f, .06f, 1.2f);
        box(decor, dark, x, .84f, z, 1.1f, .04f, 1.1f);
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2)
                box(decor, dark, x + sx * 1.02f, .45f, z + sz * 1.02f, .07f, .45f, .07f);
        world.collider(x, .5f, z, 1.2f, .5f, 1.2f);
    }

    /** Wooden park bench; the seat faces the direction given by yaw (rotation about Y). */
    public void bench(float x, float z, float yaw) {
        Node pivot = new Node("Bench");
        pivot.setLocalTranslation(x, 0, z);
        pivot.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        Material wood = color(new ColorRGBA(.45f, .30f, .18f, 1f), .08f, 8f);
        Material dark = color(new ColorRGBA(.12f, .12f, .14f, 1f), .3f, 30f);
        box(pivot, wood, 0f, .62f, 0f, 1.25f, .06f, .42f);
        box(pivot, wood, 0f, 1.12f, -.40f, 1.25f, .30f, .05f);
        box(pivot, dark, -1.05f, .30f, 0f, .06f, .30f, .38f);
        box(pivot, dark, 1.05f, .30f, 0f, .06f, .30f, .38f);
        decor.attachChild(pivot);
        world.cylinderCollider(x, .6f, z, 1.1f, .6f);
    }

    // ------------------------------------------------------------ forest extras

    /** Big rustic board on two posts. The front looks toward +Z when yaw = 0; the text wraps over two lines. */
    public void bigSign(float x, float z, float yaw, String text) {
        Node pivot = new Node("Big sign");
        pivot.setLocalTranslation(x, 0, z);
        pivot.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        Material wood = color(new ColorRGBA(.34f, .22f, .13f, 1f), .08f, 8f);
        Material board = color(new ColorRGBA(.88f, .80f, .62f, 1f), .05f, 6f);
        box(pivot, wood, -1.7f, 1.1f, 0f, .09f, 1.1f, .09f);
        box(pivot, wood, 1.7f, 1.1f, 0f, .09f, 1.1f, .09f);
        box(pivot, wood, 0f, 2.0f, 0f, 1.95f, .62f, .06f);
        box(pivot, board, 0f, 2.0f, .05f, 1.85f, .54f, .03f);
        decor.attachChild(pivot);
        BitmapText t = new BitmapText(font());
        t.setSize(.36f);
        t.setColor(ColorRGBA.Black);
        t.setText(text);
        t.setBox(new Rectangle(-1.7f, .38f, 3.4f, .76f));
        t.setAlignment(BitmapFont.Align.Center);
        t.setVerticalAlignment(BitmapFont.VAlign.Center);
        t.setLocalTranslation(0f, 2.0f, .086f);
        Node holder = new Node("Big sign text");
        holder.setLocalTranslation(x, 0, z);
        holder.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        holder.attachChild(t);
        fx.attachChild(holder);
        world.collider(x, 1.1f, z, 1.9f, 1.1f, .3f);
    }

    /** Brass name plate on the front of a bench backrest (use the same x, z and yaw as the bench). */
    public void benchPlaque(float x, float z, float yaw, String text) {
        Node pivot = new Node("Plaque");
        pivot.setLocalTranslation(x, 0, z);
        pivot.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        box(pivot, color(new ColorRGBA(.82f, .64f, .26f, 1f), .8f, 60f), 0f, 1.12f, -.345f, .70f, .12f, .01f);
        decor.attachChild(pivot);
        BitmapText t = new BitmapText(font());
        t.setSize(.085f);
        t.setColor(new ColorRGBA(.12f, .07f, .02f, 1f));
        t.setText(text);
        t.setBox(new Rectangle(-.66f, .095f, 1.32f, .19f));
        t.setAlignment(BitmapFont.Align.Center);
        t.setVerticalAlignment(BitmapFont.VAlign.Center);
        t.setLocalTranslation(0f, 1.12f, -.332f);
        Node holder = new Node("Plaque text");
        holder.setLocalTranslation(x, 0, z);
        holder.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        holder.attachChild(t);
        fx.attachChild(holder);
    }

    /** A small floating glowing light. */
    public void firefly(float x, float y, float z, float s, ColorRGBA light) {
        Mesh core = blob("lamp", 50, ColorRGBA.White, .02f);
        put(decor, core, glow(light), x, y, z, .06f * s, .06f * s, .06f * s, 0);
        put(fx, core, halo(new ColorRGBA(light.r, light.g, light.b, .20f)), x, y, z, .42f * s, .42f * s, .42f * s, 0)
                .setShadowMode(RenderQueue.ShadowMode.Off);
        fx.setQueueBucket(RenderQueue.Bucket.Transparent);
    }

    /** A string of warm lights hanging between two points. */
    public void fairyLights(float x1, float y1, float z1, float x2, float y2, float z2, int count) {
        ColorRGBA warm = new ColorRGBA(1f, .82f, .48f, 1f);
        for (int i = 0; i <= count; i++) {
            float t = i / (float) count;
            float sag = .9f * 4f * t * (1f - t);
            firefly(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t - sag, z1 + (z2 - z1) * t, 1.5f, warm);
        }
    }

    /** Glowing memory marker: a floating light with a soft ring on the ground. */
    public void memoryOrb(float x, float z, ColorRGBA light) {
        Mesh core = blob("lamp", 50, ColorRGBA.White, .02f);
        put(decor, core, glow(light), x, 1.5f, z, .2f, .26f, .2f, 0);
        put(fx, core, halo(new ColorRGBA(light.r, light.g, light.b, .22f)), x, 1.5f, z, 1.6f, 1.6f, 1.6f, 0)
                .setShadowMode(RenderQueue.ShadowMode.Off);
        Geometry ring = cyl(fx, halo(new ColorRGBA(light.r, light.g, light.b, .30f)), x, .05f, z, 1.1f, .01f, true);
        ring.setShadowMode(RenderQueue.ShadowMode.Off);
        fx.setQueueBucket(RenderQueue.Bucket.Transparent);
    }

    /** Small forest pond with a ring of rocks and lily pads. */
    public void pond(float x, float z, float radius) {
        cyl(decor, color(new ColorRGBA(.10f, .26f, .34f, 1f), .9f, 90f), x, .04f, z, radius, .05f, true);
        for (int i = 0; i < 16; i++) {
            float a = i * FastMath.TWO_PI / 16f;
            rock(x + FastMath.sin(a) * radius * 1.04f, z + FastMath.cos(a) * radius * 1.04f, .55f + .35f * ProceduralMesh.hash(i, 5), i);
        }
        Material pad = color(new ColorRGBA(.20f, .50f, .22f, 1f), .2f, 10f);
        for (int i = 0; i < 6; i++) {
            float a = i * 2.1f + .5f, d = radius * (.25f + .12f * (i % 4));
            cyl(decor, pad, x + FastMath.sin(a) * d, .075f, z + FastMath.cos(a) * d, .26f, .01f, true);
        }
        world.cylinderCollider(x, .5f, z, radius, .5f);
    }

    /** Checkered picnic blanket with a basket and two cushions. */
    public void picnic(float x, float z) {
        Material red = color(new ColorRGBA(.80f, .18f, .20f, 1f), .05f, 5f);
        Material cream = color(new ColorRGBA(.96f, .92f, .82f, 1f), .05f, 5f);
        for (int i = 0; i < 7; i++)
            for (int j = 0; j < 7; j++)
                box(decor, ((i + j) & 1) == 0 ? red : cream, x + (i - 3) * .34f, .025f, z + (j - 3) * .34f, .17f, .012f, .17f);
        Material wicker = color(new ColorRGBA(.58f, .40f, .20f, 1f), .05f, 5f);
        box(decor, wicker, x + .6f, .22f, z - .5f, .30f, .18f, .22f);
        box(decor, wicker, x + .6f, .42f, z - .5f, .32f, .03f, .24f);
        box(decor, color(new ColorRGBA(.95f, .62f, .72f, 1f), .1f, 8f), x - .7f, .14f, z + .2f, .24f, .08f, .24f);
        box(decor, color(new ColorRGBA(.62f, .78f, .95f, 1f), .1f, 8f), x - .2f, .14f, z + .9f, .24f, .08f, .24f);
    }

    /** Far-away rounded hill, tinted blue-green like distant land. */
    public void hill(float x, float z, float radius, float height, int seed) {
        Mesh h = blob("hill" + (seed % 4), seed % 4 + 60, new ColorRGBA(.36f, .54f, .40f, 1f), .12f);
        put(decor, h, veg, x, -height * .30f, z, radius, height, radius, seed);
    }

    public void fountain(float x, float z) {
        Material stone = world.texture("stone.png");
        Material water = color(new ColorRGBA(.20f, .47f, .70f, 1f), .9f, 90f);
        cyl(decor, stone, x, .45f, z, 4.3f, .9f, true);
        cyl(decor, water, x, .80f, z, 3.8f, .05f, true);
        cyl(decor, stone, x, 1.9f, z, .75f, 2.2f, true);
        cyl(decor, stone, x, 3.0f, z, 1.9f, .4f, true);
        cyl(decor, water, x, 3.22f, z, 1.6f, .05f, true);
        Material rose = glow(new ColorRGBA(1f, .42f, .58f, 1f));
        Geometry a = cyl(decor, rose, x - .26f, 4.62f, z, .30f, .34f, false);
        Geometry b = cyl(decor, rose, x + .26f, 4.62f, z, .30f, .34f, false);
        Geometry d = box(decor, rose, x, 4.30f, z, .38f, .38f, .17f);
        d.setLocalRotation(new Quaternion().fromAngles(0, 0, FastMath.QUARTER_PI));
        a.setShadowMode(RenderQueue.ShadowMode.Off);
        b.setShadowMode(RenderQueue.ShadowMode.Off);
        world.cylinderCollider(x, .45f, z, 4.3f, .45f);
        world.cylinderCollider(x, 2f, z, .8f, 2f);
    }

    /** Flat textured ring (or disc when r0 = 0) lying on the ground. */
    public void annulus(float r0, float r1, float y, float repeatAround, float repeatAcross, Material m) {
        final int rows = 2, cols = 129;
        ProceduralMesh.Pos pos = (r, c, out) -> {
            float rad = r == 0 ? r0 : r1;
            float th = FastMath.TWO_PI * c / (cols - 1);
            out.set(rad * FastMath.sin(th), y, rad * FastMath.cos(th));
        };
        Mesh mesh = ProceduralMesh.grid(rows, cols, false, pos, null, Vector3f.UNIT_Y);
        mesh.scaleTextureCoordinates(new Vector2f(repeatAround, repeatAcross));
        Geometry g = new Geometry("Ring road", mesh);
        g.setMaterial(m);
        g.setShadowMode(RenderQueue.ShadowMode.Receive);
        world.node().attachChild(g);
    }

    /** Merge all static decor into a few big meshes. */
    public void finish() {
        try {
            decor.batch();
        } catch (RuntimeException e) {
            System.err.println("Scenery batching skipped: " + e);
        }
    }
}
