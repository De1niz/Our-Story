package com.ourstory;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Full-screen loading screen: dreamy gradient, drifting light bubbles, rising hearts, a beating heart,
 * a progress bar and rotating tips. Work is done step by step so the screen keeps animating.
 */
public final class LoadingScreen {
    /** One piece of loading work. */
    public static final class Step {
        final String label;
        final Runnable work;
        public Step(String label, Runnable work) { this.label = label; this.work = work; }
    }

    private static final String[] TIPS = {
            "Player 1 walks with WASD and jumps with SPACE. Player 2 uses the ARROW KEYS and RIGHT SHIFT.",
            "Walk up to a glowing lantern and press E or ENTER to visit that month of our story.",
            "Around the heart fountain you will find game tables: chess, connect four and sweet couple games.",
            "Move the mouse to turn the camera. It zooms out by itself when you walk apart.",
            "Press BACKSPACE to leave a game or to go back.",
            "Take your time. The best part of a story is the people in it."};

    private final SimpleApplication app;
    private final Node root = new Node("Loading screen");
    private final List<Material> fadeMats = new ArrayList<>();
    private final List<ColorRGBA> fadeBase = new ArrayList<>();
    private final List<BitmapText> fadeTexts = new ArrayList<>();
    private final List<ColorRGBA> textBase = new ArrayList<>();
    private final Random rnd = new Random(5);

    private BitmapFont font;
    private float W, H, S;
    private Geometry heart, heartGlow, bar, barTrack, highlight;
    private BitmapText statusText, tipText, percentText, subtitle;
    private Geometry[] bokeh, hearts;
    private float[][] bokehState, heartState;

    private List<Step> steps = new ArrayList<>();
    private Runnable onDone;
    private boolean active, finished;
    private int stepIndex, frames;
    private boolean labelShown;
    private float time, shown, target, minTime, fade = 1f, tipTimer;
    private int tipIndex;

    public LoadingScreen(SimpleApplication app) { this.app = app; }

    public boolean isActive() { return active; }

    /** True while the screen is still loading; false once it only fades away. */
    public boolean isBlocking() { return active && !finished; }

    /** Starts the loading screen. minSeconds is the shortest time the screen stays up. */
    public void begin(String title, List<Step> work, float minSeconds, Runnable done) {
        steps = work;
        onDone = done;
        minTime = minSeconds;
        stepIndex = 0;
        frames = 0;
        labelShown = false;
        time = 0;
        shown = 0;
        target = 0;
        fade = 1f;
        finished = false;
        tipTimer = 0;
        tipIndex = rnd.nextInt(TIPS.length);
        active = true;
        build(title);
        app.getGuiNode().attachChild(root);
        setFade(1f);
    }

    // ------------------------------------------------------------------ scene

    private Material material(ColorRGBA base, boolean vertex) {
        Material m = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", base);
        m.setBoolean("VertexColor", vertex);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        fadeMats.add(m);
        fadeBase.add(base);
        return m;
    }

    private Geometry shape(String name, Mesh mesh, Material m, float z) {
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(m);
        g.setLocalTranslation(0, 0, z);
        root.attachChild(g);
        return g;
    }

    private BitmapText text(String s, float size, ColorRGBA color, float top, float z) {
        BitmapText t = new BitmapText(font);
        t.setSize(size * S);
        t.setColor(color);
        t.setText(s);
        t.setBox(new Rectangle(0, 0, W, size * S * 3f));
        t.setAlignment(BitmapFont.Align.Center);
        t.setLocalTranslation(0, top, z);
        root.attachChild(t);
        fadeTexts.add(t);
        textBase.add(color);
        return t;
    }

    private static Mesh heartMesh() {
        final int rows = 5, cols = 73;
        ProceduralMesh.Pos pos = (r, c, out) -> {
            float t = FastMath.TWO_PI * c / (cols - 1);
            float s = r / (float) (rows - 1);
            float bx = 16f * (float) Math.pow(Math.sin(t), 3);
            float by = (float) (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
            out.set(bx * s / 17f, (-1f + (by + 1f) * s) / 17f, 0f);
        };
        ProceduralMesh.Tint tint = (r, c, p) -> {
            float s = r / (float) (rows - 1);
            float k = (float) Math.pow(s, 1.6);
            float hi = FastMath.clamp(.14f * (-p.x * 1.6f + p.y), -.1f, .22f);
            return new ColorRGBA(FastMath.clamp(1f + (.84f - 1f) * k + hi, 0, 1), FastMath.clamp(.62f + (.10f - .62f) * k + hi * .6f, 0, 1),
                    FastMath.clamp(.74f + (.30f - .74f) * k + hi * .6f, 0, 1), 1f);
        };
        return ProceduralMesh.grid(rows, cols, false, pos, tint, new Vector3f(0, 0, 1));
    }

    private static Mesh discMesh(float centerAlpha) {
        final int rows = 5, cols = 41;
        ProceduralMesh.Pos pos = (r, c, out) -> {
            float a = FastMath.TWO_PI * c / (cols - 1);
            float s = r / (float) (rows - 1);
            out.set(FastMath.cos(a) * s, FastMath.sin(a) * s, 0f);
        };
        ProceduralMesh.Tint tint = (r, c, p) -> {
            float s = 1f - r / (float) (rows - 1);
            return new ColorRGBA(1f, 1f, 1f, centerAlpha * s * s);
        };
        return ProceduralMesh.grid(rows, cols, false, pos, tint, new Vector3f(0, 0, 1));
    }

    private static Mesh crispDisc() {
        final int rows = 2, cols = 41;
        ProceduralMesh.Pos pos = (r, c, out) -> {
            float a = FastMath.TWO_PI * c / (cols - 1);
            out.set(FastMath.cos(a) * r, FastMath.sin(a) * r, 0f);
        };
        return ProceduralMesh.grid(rows, cols, false, pos, null, new Vector3f(0, 0, 1));
    }

    private static Mesh gradient(float w, float h) {
        final int rows = 12;
        ProceduralMesh.Pos pos = (r, c, out) -> out.set(c * w, r * h / (rows - 1), 0f);
        ProceduralMesh.Tint tint = (r, c, p) -> {
            float t = r / (float) (rows - 1); // 0 = bottom, 1 = top
            ColorRGBA bottom = new ColorRGBA(.99f, .86f, .76f, 1f), mid = new ColorRGBA(.97f, .74f, .83f, 1f),
                    top = new ColorRGBA(.72f, .66f, .92f, 1f);
            ColorRGBA a = t < .5f ? bottom : mid, b = t < .5f ? mid : top;
            float k = t < .5f ? t * 2f : (t - .5f) * 2f;
            k = k * k * (3f - 2f * k);
            return new ColorRGBA(a.r + (b.r - a.r) * k, a.g + (b.g - a.g) * k, a.b + (b.b - a.b) * k, 1f);
        };
        return ProceduralMesh.grid(rows, 2, false, pos, tint, new Vector3f(0, 0, 1));
    }

    private void build(String title) {
        root.detachAllChildren();
        fadeMats.clear(); fadeBase.clear(); fadeTexts.clear(); textBase.clear();
        font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        W = app.getCamera().getWidth();
        H = app.getCamera().getHeight();
        S = H / 720f;
        ColorRGBA ink = new ColorRGBA(.10f, .04f, .09f, 1f);

        shape("Background", gradient(W, H), material(ColorRGBA.White, true), -.95f);

        // soft light bubbles
        Mesh disc = discMesh(.55f);
        bokeh = new Geometry[14];
        bokehState = new float[14][];
        for (int i = 0; i < bokeh.length; i++) {
            ColorRGBA c = i % 3 == 0 ? new ColorRGBA(1f, .95f, .75f, 1f) : i % 3 == 1 ? new ColorRGBA(1f, .85f, .92f, 1f)
                    : new ColorRGBA(.90f, .88f, 1f, 1f);
            bokeh[i] = shape("Bubble", disc, material(c, true), -.9f);
            float r = (40f + rnd.nextFloat() * 110f) * S;
            bokehState[i] = new float[]{rnd.nextFloat() * W, rnd.nextFloat() * H, r, 6f + rnd.nextFloat() * 14f, rnd.nextFloat() * 6f};
            bokeh[i].setLocalScale(r, r, 1f);
        }

        // rising hearts
        Mesh small = heartMesh();
        ColorRGBA[] hc = {new ColorRGBA(1f, 1f, 1f, .75f), new ColorRGBA(1f, .78f, .86f, .8f), new ColorRGBA(.95f, .35f, .5f, .7f)};
        Material[] hm = {material(hc[0], true), material(hc[1], true), material(hc[2], true)};
        hearts = new Geometry[18];
        heartState = new float[18][];
        for (int i = 0; i < hearts.length; i++) {
            hearts[i] = shape("Floating heart", small, hm[i % 3], -.6f);
            heartState[i] = new float[]{rnd.nextFloat() * W, rnd.nextFloat() * H, (14f + rnd.nextFloat() * 30f) * S,
                    30f + rnd.nextFloat() * 55f, rnd.nextFloat() * 6.28f, rnd.nextFloat() - .5f};
            hearts[i].setLocalScale(heartState[i][2], heartState[i][2], 1f);
        }

        // glowing, beating heart
        heartGlow = shape("Heart glow", discMesh(.65f), material(new ColorRGBA(1f, .72f, .82f, 1f), true), -.4f);
        heart = shape("Heart", small, material(ColorRGBA.White, true), -.3f);
        highlight = shape("Heart shine", discMesh(.8f), material(ColorRGBA.White, true), -.25f);
        subtitle = null;

        text(title, 58f, ink, H - 70f * S, .3f);
        text("OUR STORY", 24f, new ColorRGBA(.45f, .12f, .30f, 1f), H - 40f * S, .3f);
        statusText = text("", 24f, ink, H * .30f, .3f);

        // player chips
        ColorRGBA[] chip = {new ColorRGBA(.40f, .62f, .95f, 1f), new ColorRGBA(.96f, .50f, .66f, 1f)};
        Mesh chipMesh = crispDisc();
        for (int p = 0; p < 2; p++) {
            Geometry g = shape("Chip", chipMesh, material(chip[p], false), -.2f);
            float cx = W * .5f + (p == 0 ? -1 : 1) * 190f * S, cy = H * .56f;
            g.setLocalTranslation(cx, cy, -.2f);
            g.setLocalScale(34f * S, 34f * S, 1f);
            BitmapText t = text("P" + (p + 1), 24f, ink, cy + 12f * S, .3f);
            t.setBox(new Rectangle(cx - W * .5f, 0, W, 60f * S));
            t.setLocalTranslation(0, cy + 12f * S, .3f);
            t.setBox(new Rectangle(cx - W * .5f, 0, W, 60f * S));
        }

        // progress bar
        float bw = W * .5f, bh = 16f * S, bx = (W - bw) * .5f, by = H * .22f;
        barTrack = shape("Bar track", new Quad(1, 1), material(new ColorRGBA(.25f, .08f, .20f, .28f), false), -.2f);
        barTrack.setLocalTranslation(bx - 3f * S, by - 3f * S, -.2f);
        barTrack.setLocalScale(bw + 6f * S, bh + 6f * S, 1f);
        bar = shape("Bar fill", new Quad(1, 1), material(new ColorRGBA(.93f, .28f, .46f, 1f), false), -.1f);
        bar.setLocalTranslation(bx, by, -.1f);
        bar.setLocalScale(1f, bh, 1f);
        percentText = text("0%", 22f, ink, by - 10f * S, .3f);
        tipText = text(TIPS[tipIndex], 21f, ink, H * .12f, .3f);
        tipText.setBox(new Rectangle(W * .12f, 0, W * .76f, 70f * S));
    }

    private void setFade(float a) {
        fade = a;
        for (int i = 0; i < fadeMats.size(); i++) {
            ColorRGBA b = fadeBase.get(i);
            fadeMats.get(i).setColor("Color", new ColorRGBA(b.r, b.g, b.b, b.a * a));
        }
        for (int i = 0; i < fadeTexts.size(); i++) {
            ColorRGBA b = textBase.get(i);
            fadeTexts.get(i).setColor(new ColorRGBA(b.r, b.g, b.b, b.a * a));
        }
    }

    // ------------------------------------------------------------------ update

    public void update(float tpf) {
        if (!active) return;
        time += tpf;
        animate(tpf);

        if (!finished) {
            frames++;
            if (frames > 3 && stepIndex < steps.size()) {
                Step s = steps.get(stepIndex);
                if (!labelShown) {
                    statusText.setText(s.label);
                    labelShown = true;
                } else {
                    try {
                        s.work.run();
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                    stepIndex++;
                    labelShown = false;
                    target = stepIndex / (float) steps.size();
                }
            }
            shown = Math.min(target, shown + tpf / Math.max(minTime, .5f));
            if (stepIndex >= steps.size() && shown >= .999f && time >= minTime) {
                finished = true;
                statusText.setText("Ready!");
                if (onDone != null) onDone.run();
            }
        } else {
            fade -= tpf / .8f;
            if (fade <= 0f) {
                active = false;
                root.removeFromParent();
                return;
            }
            setFade(fade);
        }
        float bw = W * .5f;
        bar.setLocalScale(Math.max(1f, bw * shown), 16f * S, 1f);
        percentText.setText(Math.round(shown * 100f) + "%");
    }

    private void animate(float tpf) {
        // heartbeat: two quick pulses per second
        float ph = time * 1.15f;
        float b1 = (float) Math.pow(Math.max(0, Math.sin(FastMath.TWO_PI * ph)), 10);
        float b2 = (float) Math.pow(Math.max(0, Math.sin(FastMath.TWO_PI * ph - 1.1f)), 10);
        float beat = 1f + .13f * b1 + .07f * b2;
        float base = 150f * S;
        float cx = W * .5f, cy = H * .56f;
        heart.setLocalTranslation(cx, cy, -.3f);
        heart.setLocalScale(base * beat, base * beat, 1f);
        heartGlow.setLocalTranslation(cx, cy, -.4f);
        float glow = 330f * S * (1f + .12f * b1 + .06f * b2);
        heartGlow.setLocalScale(glow, glow, 1f);
        highlight.setLocalTranslation(cx - base * .36f * beat, cy + base * .42f * beat, -.25f);
        highlight.setLocalScale(base * .20f, base * .11f, 1f);
        highlight.setLocalRotation(new Quaternion().fromAngles(0, 0, .6f));

        for (int i = 0; i < bokeh.length; i++) {
            float[] s = bokehState[i];
            s[1] += s[3] * S * tpf;
            s[4] += tpf * .4f;
            if (s[1] - s[2] > H) { s[1] = -s[2]; s[0] = rnd.nextFloat() * W; }
            bokeh[i].setLocalTranslation(s[0] + FastMath.sin(s[4]) * 30f * S, s[1], -.9f);
        }
        for (int i = 0; i < hearts.length; i++) {
            float[] s = heartState[i];
            s[1] += s[3] * S * tpf;
            s[4] += tpf * (.8f + Math.abs(s[5]));
            if (s[1] - s[2] > H) { s[1] = -s[2]; s[0] = rnd.nextFloat() * W; }
            hearts[i].setLocalTranslation(s[0] + FastMath.sin(s[4]) * 40f * S, s[1], -.6f);
            hearts[i].setLocalRotation(new Quaternion().fromAngles(0, 0, FastMath.sin(s[4]) * .35f * (s[5] > 0 ? 1 : -1)));
        }
        tipTimer += tpf;
        if (tipTimer > 3.6f) {
            tipTimer = 0;
            tipIndex = (tipIndex + 1) % TIPS.length;
            tipText.setText(TIPS[tipIndex]);
        }
    }
}
