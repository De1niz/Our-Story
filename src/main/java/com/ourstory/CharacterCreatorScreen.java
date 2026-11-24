package com.ourstory;

import com.jme3.app.SimpleApplication;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Quad;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.font.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Two-player split-screen character creator. Each player has an own camera, so both can edit and
 * zoom at the same time. Touch a body part (or move to a category) and that player's camera zooms in;
 * after the choice is made the view eases back to the normal full-body size.
 */
public final class CharacterCreatorScreen {
    /** Turn this off if your GPU shows shadow artefacts in the second view. */
    private static final boolean SHADOWS = true;

    private static final ColorRGBA BACKGROUND = new ColorRGBA(.70f, .77f, .86f, 1f);
    private static final ColorRGBA BLACK = ColorRGBA.Black;
    private static final ColorRGBA[] PASTEL = {new ColorRGBA(.62f, .78f, .95f, 1f), new ColorRGBA(.96f, .70f, .78f, 1f)};
    private static final float[] CHAR_X = {-3.2f, 3.2f};
    private static final float PEDESTAL_TOP = .08f;
    private static final float HOLD_SECONDS = 2.6f;

    // regions: y, distance, azimuth, camera lift
    private static final int FULL = 0, FACE = 1, EYES = 2, NOSE = 3, HAIR = 4, BEARD = 5, BODY = 6;
    private static final float[][] REGION = {
            {.95f, 4.0f, 0f, .25f}, {1.64f, .95f, .28f, .03f}, {1.665f, .52f, 0f, 0f}, {1.62f, .60f, .75f, 0f},
            {1.70f, 1.2f, .8f, .22f}, {1.58f, .72f, .35f, 0f}, {1.22f, 2.2f, .35f, .10f}};
    private static final int[] CATEGORY_REGION = {FACE, FACE, EYES, NOSE, HAIR, HAIR, BEARD, BODY, FULL, BODY, BODY};

    private final SimpleApplication app;
    private final Node stage;
    private final DirectionalLight sun;
    private final CharacterModelBuilder models;
    private final CharacterData[] players = {new CharacterData(1), new CharacterData(2)};
    private final Node[] previews = new Node[2];
    private final Camera[] cams = new Camera[2];
    private final ViewPort vp2;
    private final DirectionalLightShadowRenderer shadows2;
    private final int[] region = {FULL, FULL};
    private final float[] hold = new float[2];
    private final float[] orbit = new float[2];
    private final Vector3f[] curPos = {new Vector3f(), new Vector3f()};
    private final Vector3f[] curTarget = {new Vector3f(), new Vector3f()};
    private float time;

    // mouse state
    private boolean mouseDown, dragging;
    private int dragPlayer = -1;
    private final Vector2f startCursor = new Vector2f(), lastCursor = new Vector2f();

    // layout + HUD
    private final Node hud = new Node("Creator HUD");
    private DirectionalLight fill, rim;
    private BitmapFont font;
    private BitmapText[] header, status;
    private BitmapText title, topRight, helpP1, helpP2, helpAll;
    private float W, H, sideW, topH, botH, uiScale, rowH;
    private boolean dirty = true;
    private final Random rnd = new Random();

    // professional side panel: tabs, sections and sliders
    private static final String[] TABS = {"PRESET", "BODY", "FACE", "HAIR", "OUTFIT"};
    private static final int[][] TAB_CATS = {{}, {CharacterData.BUILD, CharacterData.HEIGHT},
            {CharacterData.FACE, CharacterData.SKIN, CharacterData.EYES, CharacterData.NOSE, CharacterData.BEARD},
            {CharacterData.HAIR, CharacterData.HAIR_COLOR}, {CharacterData.OUTFIT, CharacterData.OUTFIT_COLOR}};
    private static final String[][] TAB_SECTIONS = {{}, {"Body build", "Height"},
            {"Face shape", "Skin tone", "Eyes", "Nose", "Beard"}, {"Hair style", "Hair color"}, {"Clothes", "Clothes color"}};
    private static final String[] PRESET_NAMES = {"Classic", "Sporty", "Elegant", "Rugged", "Artist", "Sweet"};
    private static final int[][] PRESETS = {
            {1, 1, 0, 1, 3, 1, 0, 2, 1, 0, 7}, {3, 2, 1, 1, 1, 2, 1, 1, 2, 0, 0},
            {0, 0, 3, 0, 4, 4, 0, 0, 1, 2, 5}, {3, 3, 0, 2, 2, 1, 3, 3, 2, 1, 4},
            {4, 1, 2, 1, 5, 3, 5, 2, 1, 1, 3}, {4, 0, 3, 0, 6, 6, 0, 4, 0, 2, 1}};
    private static final int CATS = CharacterData.CATEGORIES.length;

    private static final class Hit {
        final float x, y, w, h;
        final int player, tab;
        final java.util.function.Consumer<Vector2f> action;
        Hit(float x, float y, float w, float h, int player, int tab, java.util.function.Consumer<Vector2f> action) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.player = player; this.tab = tab; this.action = action;
        }
        boolean contains(Vector2f c) { return c.x >= x && c.x <= x + w && c.y >= y && c.y <= y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final int[] tab = {2, 2};
    private final Node[][] tabNode = new Node[2][TABS.length];
    private final Geometry[][] tabBg = new Geometry[2][TABS.length];
    private final Geometry[][] marker = new Geometry[2][CATS], sliderFill = new Geometry[2][CATS],
            handle = new Geometry[2][CATS], swatch = new Geometry[2][CATS];
    private final BitmapText[][] valueText = new BitmapText[2][CATS];
    private final float[][] trackX = new float[2][CATS], trackW = new float[2][CATS];
    private final Geometry[] readyBg = new Geometry[2];
    private final BitmapText[] readyText = new BitmapText[2];

    public CharacterCreatorScreen(SimpleApplication app, Node stage, DirectionalLight sun) {
        this.app = app;
        this.stage = stage;
        this.sun = sun;
        this.models = new CharacterModelBuilder(app.getAssetManager());
        cams[0] = app.getCamera();
        cams[1] = app.getCamera().clone();
        vp2 = app.getRenderManager().createMainView("Creator Player 2 View", cams[1]);
        vp2.setClearFlags(true, true, true);
        vp2.setBackgroundColor(BACKGROUND);
        vp2.attachScene(app.getRootNode());
        vp2.setEnabled(false);
        if (SHADOWS) {
            shadows2 = new DirectionalLightShadowRenderer(app.getAssetManager(), 2048, 3);
            shadows2.setLight(sun);
            shadows2.setShadowIntensity(.55f);
            vp2.addProcessor(shadows2);
        } else {
            shadows2 = null;
        }
    }

    public CharacterData get(int player) { return players[player]; }
    public boolean bothReady() { return players[0].ready && players[1].ready; }

    // ------------------------------------------------------------------ show / hide

    public void show() {
        Camera c = app.getCamera();
        W = c.getWidth();
        H = c.getHeight();
        uiScale = H / 720f;
        sideW = .25f * W;
        topH = .10f * H;
        botH = .13f * H;
        float bottom = botH / H, top = 1f - topH / H;
        cams[0].setViewPort(sideW / W, .5f, bottom, top);
        cams[1].setViewPort(.5f, 1f - sideW / W, bottom, top);
        float aspect = ((.5f * W - sideW)) / (H - topH - botH);
        for (Camera cam : cams) cam.setFrustumPerspective(32f, aspect, .05f, 80f);
        vp2.setEnabled(true);

        stage.detachAllChildren();
        buildStudio();
        fill = new DirectionalLight();
        fill.setDirection(new Vector3f(.6f, -.25f, -.5f).normalizeLocal());
        fill.setColor(new ColorRGBA(.55f, .66f, .90f, 1f).mult(.45f));
        rim = new DirectionalLight();
        rim.setDirection(new Vector3f(-.2f, -.2f, 1f).normalizeLocal());
        rim.setColor(new ColorRGBA(1f, .95f, .85f, 1f).mult(.35f));
        stage.addLight(fill);
        stage.addLight(rim);

        previews[0] = null;
        previews[1] = null;
        for (int p = 0; p < 2; p++) {
            rebuild(p);
            region[p] = FULL;
            hold[p] = 0;
            orbit[p] = 0;
            snapCamera(p);
        }
        buildHud();
        app.getGuiNode().attachChild(hud);
        dirty = true;
        refreshHud();
    }

    public void hide() {
        stage.detachAllChildren();
        if (fill != null) { stage.removeLight(fill); stage.removeLight(rim); }
        hud.detachAllChildren();
        hud.removeFromParent();
        vp2.setEnabled(false);
        Camera c = app.getCamera();
        c.setViewPort(0f, 1f, 0f, 1f);
        c.setFrustumPerspective(45f, (float) c.getWidth() / c.getHeight(), .1f, 400f);
    }

    // ------------------------------------------------------------------ input from Main

    public void changeCategory(int p, int inc) {
        players[p].changeCategory(inc);
        showTabOf(p, players[p].category);
        focus(p, CATEGORY_REGION[players[p].category]);
        dirty = true;
    }

    public void changeOption(int p, int inc) {
        players[p].changeOption(inc);
        rebuild(p);
        focus(p, CATEGORY_REGION[players[p].category]);
        dirty = true;
    }

    public void toggleReady(int p) {
        players[p].ready = !players[p].ready;
        region[p] = FULL;
        hold[p] = 0;
        dirty = true;
    }

    /** Player finished editing this part: ease back to the normal size. */
    public void done(int p) { region[p] = FULL; hold[p] = 0; }

    public void resetViews() { done(0); done(1); orbit[0] = 0; orbit[1] = 0; }

    public void mouseDown(Vector2f cursor) {
        if (uiClick(cursor)) { mouseDown = false; dragPlayer = -1; return; }
        mouseDown = true;
        dragging = false;
        startCursor.set(cursor);
        lastCursor.set(cursor);
        dragPlayer = viewAt(cursor);
    }

    public void mouseUp(Vector2f cursor) {
        if (mouseDown && !dragging && dragPlayer >= 0) pick(dragPlayer, startCursor);
        mouseDown = false;
        dragging = false;
        dragPlayer = -1;
    }

    // ------------------------------------------------------------------ internals

    private void focus(int p, int r) {
        region[p] = r;
        hold[p] = r == FULL ? 0f : HOLD_SECONDS;
    }

    private int viewAt(Vector2f c) {
        float y = c.y;
        if (y < botH || y > H - topH) return -1;
        if (c.x >= sideW && c.x < .5f * W) return 0;
        if (c.x >= .5f * W && c.x <= W - sideW) return 1;
        return -1;
    }

    private void pick(int p, Vector2f cursor) {
        Camera cam = cams[p];
        Vector3f o = cam.getWorldCoordinates(cursor, 0f);
        Vector3f e = cam.getWorldCoordinates(cursor, 1f);
        CollisionResults hits = new CollisionResults();
        previews[p].collideWith(new Ray(o, e.subtract(o).normalizeLocal()), hits);
        if (hits.size() == 0) { done(p); return; }
        Geometry g = hits.getClosestCollision().getGeometry();
        String part = g.getUserData("part");
        int category = -1;
        if ("Face".equals(part)) category = CharacterData.FACE;
        else if ("Eyes".equals(part)) category = CharacterData.EYES;
        else if ("Nose".equals(part)) category = CharacterData.NOSE;
        else if ("Hair".equals(part)) category = CharacterData.HAIR;
        else if ("Beard".equals(part)) category = CharacterData.BEARD;
        else if ("Body".equals(part)) category = CharacterData.BUILD;
        else if ("Legs".equals(part) || "Feet".equals(part)) category = CharacterData.HEIGHT;
        if (category < 0) return;
        players[p].category = category;
        showTabOf(p, category);
        focus(p, CATEGORY_REGION[category]);
        dirty = true;
    }

    private void rebuild(int p) {
        if (previews[p] != null) stage.detachChild(previews[p]);
        Node n = models.build(players[p]);
        n.setLocalTranslation(CHAR_X[p], PEDESTAL_TOP, 0f);
        n.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_Y));
        previews[p] = n;
        stage.attachChild(n);
    }

    private void cameraGoal(int p, Vector3f pos, Vector3f target) {
        float[] r = REGION[region[p]];
        float hs = CharacterModelBuilder.heightScale(players[p]);
        float sway = region[p] == FULL ? .22f * FastMath.sin(time * .6f + p * 1.7f) : 0f;
        float a = r[2] + orbit[p] + sway;
        target.set(CHAR_X[p], r[0] * hs + PEDESTAL_TOP, 0f);
        pos.set(CHAR_X[p] + FastMath.sin(a) * r[1], target.y + r[3], FastMath.cos(a) * r[1]);
    }

    private void snapCamera(int p) {
        cameraGoal(p, curPos[p], curTarget[p]);
        cams[p].setLocation(curPos[p]);
        cams[p].lookAt(curTarget[p], Vector3f.UNIT_Y);
    }

    public void update(float tpf, Vector2f cursor) {
        time += tpf;
        if (mouseDown && dragPlayer >= 0) {
            if (!dragging && cursor.distance(startCursor) > 6f) dragging = true;
            if (dragging) {
                orbit[dragPlayer] = FastMath.clamp(orbit[dragPlayer] - (cursor.x - lastCursor.x) * .008f,
                        -FastMath.PI, FastMath.PI);
            }
            lastCursor.set(cursor);
        }
        float alpha = FastMath.clamp(tpf * 5.5f, 0f, 1f);
        Vector3f pos = new Vector3f(), target = new Vector3f();
        for (int p = 0; p < 2; p++) {
            if (region[p] != FULL) {
                hold[p] -= tpf;
                if (hold[p] <= 0f) region[p] = FULL;
            }
            cameraGoal(p, pos, target);
            curPos[p].interpolateLocal(pos, alpha);
            curTarget[p].interpolateLocal(target, alpha);
            cams[p].setLocation(curPos[p]);
            cams[p].lookAt(curTarget[p], Vector3f.UNIT_Y);
        }
        if (dirty) refreshHud();
    }

    // ------------------------------------------------------------------ studio scene

    private Material lit(ColorRGBA c, float spec, float shine) {
        Material m = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", c);
        m.setColor("Ambient", c.mult(.8f));
        m.setColor("Specular", ColorRGBA.White.mult(spec));
        m.setFloat("Shininess", shine);
        return m;
    }

    private Geometry solid(String name, com.jme3.scene.Mesh mesh, Material m, float x, float y, float z) {
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        stage.attachChild(g);
        return g;
    }

    private void buildStudio() {
        solid("Studio floor", new Box(9f, .05f, 7f), lit(new ColorRGBA(.80f, .82f, .86f, 1f), .12f, 16f), 0, -.05f, -1f);
        solid("Studio wall", new Box(9f, 4f, .1f), lit(new ColorRGBA(.72f, .79f, .89f, 1f), .02f, 4f), 0, 3.9f, -4.6f);
        for (int p = 0; p < 2; p++) {
            Geometry ring = solid("Ring " + p, new Cylinder(2, 56, .80f, .03f, true),
                    lit(PASTEL[p].mult(.9f), .3f, 30f), CHAR_X[p], .015f, 0f);
            ring.setLocalRotation(new Quaternion().fromAngles(FastMath.HALF_PI, 0, 0));
            Geometry ped = solid("Pedestal " + p, new Cylinder(2, 56, .70f, .08f, true),
                    lit(new ColorRGBA(.90f, .90f, .92f, 1f), .35f, 40f), CHAR_X[p], .04f, 0f);
            ped.setLocalRotation(new Quaternion().fromAngles(FastMath.HALF_PI, 0, 0));
        }
    }


    // ------------------------------------------------------------------ HUD

    private static final ColorRGBA PAPER = new ColorRGBA(.97f, .96f, .93f, 1f);
    private static final ColorRGBA STRIP = new ColorRGBA(.88f, .86f, .81f, 1f);
    private static final ColorRGBA BUTTON = new ColorRGBA(.82f, .80f, .75f, 1f);
    private static final ColorRGBA GOLD = new ColorRGBA(.83f, .62f, .22f, 1f);
    private static final ColorRGBA TRACK = new ColorRGBA(.17f, .17f, .20f, 1f);

    private Geometry quad(Node parent, String name, float x, float y, float w, float h, float z, ColorRGBA c) {
        Geometry g = new Geometry(name, new Quad(w, h));
        Material m = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", c);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    private Geometry quad(String name, float x, float y, float w, float h, float z, ColorRGBA c) {
        return quad(hud, name, x, y, w, h, z, c);
    }

    /** Text with its top-left corner at (x, y). Pass w > 0 to center it inside a box of that width. */
    private BitmapText text(Node parent, String s, float x, float y, float size, float w) {
        BitmapText t = new BitmapText(font);
        t.setSize(size * uiScale);
        t.setColor(BLACK);
        t.setText(s);
        if (w > 0f) {
            t.setBox(new Rectangle(0, 0, w, size * uiScale * 1.6f));
            t.setAlignment(BitmapFont.Align.Center);
        }
        t.setLocalTranslation(x, y, 0f);
        parent.attachChild(t);
        return t;
    }

    private BitmapText text(String s, float x, float y, float size) { return text(hud, s, x, y, size, 0f); }

    private float panelX(int p) { return p == 0 ? 0f : W - sideW; }

    private void addHit(int p, int tabIndex, float x, float y, float w, float h, java.util.function.Consumer<Vector2f> action) {
        hits.add(new Hit(x, y, w, h, p, tabIndex, action));
    }

    private boolean uiClick(Vector2f c) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if ((h.tab < 0 || h.tab == tab[h.player]) && h.contains(c)) { h.action.accept(c); return true; }
        }
        return false;
    }

    private void showTabOf(int p, int category) {
        for (int t = 1; t < TABS.length; t++)
            for (int c : TAB_CATS[t]) if (c == category) tab[p] = t;
        dirty = true;
    }

    private void setChoice(int p, int cat, int index) {
        players[p].category = cat;
        players[p].set(cat, index);
        rebuild(p);
        focus(p, CATEGORY_REGION[cat]);
        dirty = true;
    }

    private void applyLook(int p, int[] look, boolean random) {
        if (random) players[p].randomize(rnd); else if (look != null) players[p].apply(look);
        rebuild(p);
        region[p] = FULL;
        hold[p] = 0;
        dirty = true;
    }

    private void buildHud() {
        hud.detachAllChildren();
        hits.clear();
        font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        float k = uiScale;
        header = new BitmapText[2];
        status = new BitmapText[2];

        quad("Top bar", 0, H - topH, W, topH, -.8f, PAPER);
        quad("Bottom bar", 0, 0, W, botH, -.8f, PAPER);
        quad("Divider", W * .5f - 2f * k, botH, 4f * k, H - topH - botH, -.7f, new ColorRGBA(.15f, .15f, .18f, 1f));
        title = text("OUR STORY  -  CHARACTER CREATOR", 26f * k, H - 20f * k, 30f);
        topRight = text("", W * .56f, H - 26f * k, 20f);

        for (int p = 0; p < 2; p++) {
            final int pl = p;
            float x0 = panelX(p), pw = sideW, top = H - topH;
            ColorRGBA pastel = PASTEL[p];
            quad("Panel " + p, x0, botH, pw, H - topH - botH, -.8f, PAPER);
            quad("Header " + p, x0, top - 46f * k, pw, 46f * k, -.7f, pastel);
            header[p] = text("PLAYER " + (p + 1), x0 + 14f * k, top - 6f * k, 22f);
            status[p] = text("", x0 + 14f * k, top - 29f * k, 14f);

            // ---- tabs
            float tabW = pw / TABS.length, tabY = top - 46f * k - 32f * k;
            for (int t = 0; t < TABS.length; t++) {
                final int ti = t;
                tabBg[p][t] = quad("Tab " + t, x0 + t * tabW + 1f, tabY, tabW - 2f, 32f * k, -.6f, STRIP);
                text(hud, TABS[t], x0 + t * tabW, tabY + 24f * k, 11.5f, tabW);
                addHit(p, -1, x0 + t * tabW, tabY, tabW, 32f * k, pt -> { tab[pl] = ti; dirty = true; });
                tabNode[p][t] = new Node("Tab content " + p + "_" + t);
                hud.attachChild(tabNode[p][t]);
            }

            // ---- tab content
            float contentTop = tabY - 8f * k;
            buildPresetTab(p, tabNode[p][0], x0, pw, contentTop);
            for (int t = 1; t < TABS.length; t++) {
                float y = contentTop;
                for (int j = 0; j < TAB_CATS[t].length; j++) {
                    buildRow(p, t, TAB_CATS[t][j], TAB_SECTIONS[t][j], x0, pw, y);
                    y -= 78f * k;
                }
            }

            // ---- bottom buttons: randomize, reset, ready
            float bw = (pw - 40f * k) / 3f, by = botH + 8f * k, bh = 34f * k;
            String[] names = {"Randomize", "Reset", "Ready"};
            for (int i = 0; i < 3; i++) {
                final int bi = i;
                float bx = x0 + 10f * k + i * (bw + 10f * k);
                Geometry g = quad("Button " + names[i], bx, by, bw, bh, -.5f, i == 2 ? new ColorRGBA(.60f, .84f, .62f, 1f) : BUTTON);
                BitmapText t = text(hud, names[i], bx, by + bh - 8f * k, 15f, bw);
                if (i == 2) { readyBg[p] = g; readyText[p] = t; }
                addHit(p, -1, bx, by, bw, bh, pt -> {
                    if (bi == 0) applyLook(pl, null, true);
                    else if (bi == 1) { players[pl].reset(); applyLook(pl, null, false); }
                    else toggleReady(pl);
                });
            }
        }
        helpP1 = text("P1:  W / S = part    A / D = change    Q = done    R = ready", 16f * k, botH - 12f * k, 16f);
        helpP2 = text("P2:  Up / Down = part    Left / Right = change    R-Shift = done    Enter = ready",
                W * .5f + 14f * k, botH - 12f * k, 16f);
        helpAll = text("Click tabs and sliders  |  Click a body part to zoom in  |  Drag to rotate  |  ESC = reset view  |  F = start when BOTH are ready",
                16f * k, botH - 50f * k, 16f);
    }

    private void buildPresetTab(int p, Node parent, float x0, float pw, float top) {
        final int pl = p;
        float k = uiScale;
        quad(parent, "Section", x0 + 8f * k, top - 22f * k, pw - 16f * k, 22f * k, -.55f, STRIP);
        text(parent, "Choose a starting look", x0 + 16f * k, top - 4f * k, 13f, 0f);
        float bw = (pw - 34f * k) / 2f, bh = 62f * k;
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            final int pi = i;
            float bx = x0 + 12f * k + (i % 2) * (bw + 10f * k);
            float by = top - 34f * k - bh - (i / 2) * (bh + 10f * k);
            quad(parent, "Preset " + i, bx, by, bw, bh, -.5f, PASTEL[p].mult(.82f + .06f * (i % 3)));
            text(parent, PRESET_NAMES[i], bx, by + bh / 2f + 9f * k, 16f, bw);
            addHit(p, 0, bx, by, bw, bh, pt -> applyLook(pl, PRESETS[pi], false));
        }
        text(parent, "Then fine-tune it in the other tabs.", x0 + 12f * k, top - 34f * k - 3f * (bh + 10f * k) - 8f * k, 13f, 0f);
    }

    private void buildRow(int p, int tabIndex, int cat, String section, float x0, float pw, float y) {
        final int pl = p, c = cat;
        float k = uiScale;
        Node parent = tabNode[p][tabIndex];
        int n = CharacterData.OPTIONS[cat].length;
        // section strip
        quad(parent, "Section", x0 + 8f * k, y - 22f * k, pw - 16f * k, 22f * k, -.55f, STRIP);
        text(parent, section, x0 + 16f * k, y - 4f * k, 13f, 0f);
        swatch[p][cat] = quad(parent, "Swatch", x0 + pw - 34f * k, y - 20f * k, 18f * k, 18f * k, -.4f, ColorRGBA.Gray);
        marker[p][cat] = quad(parent, "Selected", x0 + 3f * k, y - 74f * k, 4f * k, 72f * k, -.4f, PASTEL[p].mult(.7f));
        // slider
        float ys = y - 22f * k - 22f * k;
        float tx = x0 + 50f * k, tw = pw - 100f * k;
        trackX[p][cat] = tx;
        trackW[p][cat] = tw;
        quad(parent, "Minus", x0 + 14f * k, ys - 13f * k, 26f * k, 26f * k, -.5f, BUTTON);
        text(parent, "-", x0 + 14f * k, ys + 10f * k, 20f, 26f * k);
        quad(parent, "Plus", x0 + pw - 40f * k, ys - 13f * k, 26f * k, 26f * k, -.5f, BUTTON);
        text(parent, "+", x0 + pw - 40f * k, ys + 10f * k, 20f, 26f * k);
        quad(parent, "Track", tx, ys - 4f * k, tw, 8f * k, -.5f, TRACK);
        sliderFill[p][cat] = quad(parent, "Fill", tx, ys - 4f * k, 1f, 8f * k, -.45f, GOLD);
        handle[p][cat] = quad(parent, "Handle", tx, ys - 12f * k, 12f * k, 24f * k, -.4f, new ColorRGBA(1f, .90f, .62f, 1f));
        valueText[p][cat] = text(parent, "", tx, ys - 16f * k, 15f, tw);
        // clicks
        addHit(p, tabIndex, x0 + 8f * k, y - 22f * k, pw - 60f * k, 22f * k, pt -> selectCategory(pl, c));
        addHit(p, tabIndex, x0 + 14f * k, ys - 14f * k, 26f * k, 28f * k, pt -> setChoice(pl, c, players[pl].choices[c] - 1));
        addHit(p, tabIndex, x0 + pw - 40f * k, ys - 14f * k, 26f * k, 28f * k, pt -> setChoice(pl, c, players[pl].choices[c] + 1));
        final float fx = tx, fw = tw;
        final int count = n;
        addHit(p, tabIndex, tx - 6f * k, ys - 14f * k, tw + 12f * k, 28f * k,
                pt -> setChoice(pl, c, Math.round((pt.x - fx) / fw * (count - 1))));
    }

    private void selectCategory(int p, int cat) {
        players[p].category = cat;
        focus(p, CATEGORY_REGION[cat]);
        dirty = true;
    }

    private void refreshHud() {
        dirty = false;
        if (title == null) return;
        for (int p = 0; p < 2; p++) {
            CharacterData d = players[p];
            status[p].setText(d.ready ? "READY - press Ready again to edit" : "Editing...");
            for (int t = 0; t < TABS.length; t++) {
                boolean active = tab[p] == t;
                tabNode[p][t].setCullHint(active ? com.jme3.scene.Spatial.CullHint.Inherit : com.jme3.scene.Spatial.CullHint.Always);
                tabBg[p][t].getMaterial().setColor("Color", active ? PASTEL[p] : STRIP);
            }
            for (int cat = 0; cat < CATS; cat++) {
                if (sliderFill[p][cat] == null) continue;
                int n = CharacterData.OPTIONS[cat].length;
                float frac = n <= 1 ? 0f : d.choices[cat] / (float) (n - 1);
                float x = trackX[p][cat] + frac * trackW[p][cat];
                sliderFill[p][cat].setLocalScale(Math.max(1f, frac * trackW[p][cat]), 1f, 1f);
                handle[p][cat].setLocalTranslation(x - 6f * uiScale, handle[p][cat].getLocalTranslation().y, -.4f);
                valueText[p][cat].setText(d.choiceName(cat));
                marker[p][cat].setCullHint(d.category == cat ? com.jme3.scene.Spatial.CullHint.Inherit
                        : com.jme3.scene.Spatial.CullHint.Always);
                ColorRGBA sw = d.swatch(cat);
                if (sw == null) swatch[p][cat].setCullHint(com.jme3.scene.Spatial.CullHint.Always);
                else {
                    swatch[p][cat].setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
                    swatch[p][cat].getMaterial().setColor("Color", sw);
                }
            }
            readyBg[p].getMaterial().setColor("Color", d.ready ? new ColorRGBA(.35f, .75f, .40f, 1f) : new ColorRGBA(.60f, .84f, .62f, 1f));
            readyText[p].setText(d.ready ? "READY!" : "Ready");
        }
        topRight.setText(bothReady() ? "BOTH READY  -  press F to start!" :
                "P1: " + (players[0].ready ? "READY" : "editing") + "     P2: " + (players[1].ready ? "READY" : "editing"));
    }
}
