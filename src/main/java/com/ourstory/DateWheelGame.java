package com.ourstory;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import java.util.Random;

/** A big wheel of sweet date ideas and dares. Press ACTION to spin it, then do what it says together. */
public final class DateWheelGame extends MiniGame {
    private static final String[] SHORT = {"KISS", "DANCE", "HUG", "COMPLIMENT", "PICNIC", "STORY", "SELFIE", "WISH"};
    private static final String[] LONG = {
            "Give each other a slow, sweet kiss. Make it last!",
            "Dance together for one whole song. No excuses, no shy steps!",
            "Hug for 20 seconds without letting go. Count out loud!",
            "Tell your partner three things you love about them.",
            "Plan a picnic: what would you bring and where would you go?",
            "Tell the story of the day you first met, from your own point of view.",
            "Take a silly selfie together with the funniest faces you can make.",
            "Make a wish for the two of you and whisper it to each other."};
    private static final ColorRGBA[] COLORS = {
            new ColorRGBA(.95f, .35f, .50f, 1f), new ColorRGBA(.98f, .70f, .20f, 1f),
            new ColorRGBA(.55f, .80f, .45f, 1f), new ColorRGBA(.40f, .70f, .95f, 1f),
            new ColorRGBA(.85f, .50f, .90f, 1f), new ColorRGBA(.98f, .55f, .30f, 1f),
            new ColorRGBA(.35f, .85f, .75f, 1f), new ColorRGBA(.95f, .45f, .70f, 1f)};
    private static final int SECTORS = 8;
    private static final float RADIUS = .78f;

    private final Node wheel = new Node("Date wheel");
    private final Random rnd = new Random();
    private float theta, speed;
    private boolean spinning;
    private int picked = -1;
    private Node gui;
    private BitmapText resultText;

    public DateWheelGame() { super("Date Wheel"); }

    @Override protected void buildTable() {
        Material wood = color(new ColorRGBA(.30f, .17f, .09f, 1f), .25f, 25f);
        Material gold = color(new ColorRGBA(.95f, .75f, .20f, 1f), .8f, 80f);
        box(dyn, wood, tx, TOP + .03f, tz, RADIUS + .12f, .03f, RADIUS + .12f);
        wheel.setLocalTranslation(tx, TOP + .075f, tz);
        dyn.attachChild(wheel);
        Material flat = sc.vertex(.3f, 20f);
        BitmapFont font = sc.font();
        for (int i = 0; i < SECTORS; i++) {
            final float a0 = i * FastMath.TWO_PI / SECTORS, a1 = (i + 1) * FastMath.TWO_PI / SECTORS;
            final ColorRGBA col = COLORS[i];
            Mesh m = ProceduralMesh.grid(2, 8, false, (r, c, out) -> {
                if (r == 0) { out.set(0, 0, 0); return; }
                float a = a0 + (a1 - a0) * c / 7f;
                out.set(FastMath.sin(a) * RADIUS, 0, -FastMath.cos(a) * RADIUS);
            }, (r, c, p) -> col, new Vector3f(0, 1, 0));
            Geometry g = new Geometry("Wheel sector", m);
            g.setMaterial(flat);
            g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            wheel.attachChild(g);

            float mid = (a0 + a1) / 2f;
            BitmapText t = new BitmapText(font);
            t.setSize(.085f);
            t.setColor(ColorRGBA.Black);
            t.setText(SHORT[i]);
            t.setBox(new Rectangle(-.25f, .05f, .5f, .1f));
            t.setAlignment(BitmapFont.Align.Center);
            t.setVerticalAlignment(BitmapFont.VAlign.Center);
            Node holder = new Node("Wheel text");
            holder.setLocalTranslation(FastMath.sin(mid) * RADIUS * .6f, .012f, -FastMath.cos(mid) * RADIUS * .6f);
            Quaternion qy = new Quaternion().fromAngleAxis(-mid, Vector3f.UNIT_Y);
            Quaternion qx = new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X);
            holder.setLocalRotation(qy.mult(qx));
            holder.attachChild(t);
            wheel.attachChild(holder);
        }
        // golden rim, hub and pointer (the pointer stays still at the top)
        for (int i = 0; i < 48; i++) {
            float a = i * FastMath.TWO_PI / 48f;
            Geometry pearl = new Geometry("Rim", new Box(.025f, .02f, .025f));
            pearl.setMaterial(gold);
            pearl.setLocalTranslation(FastMath.sin(a) * (RADIUS + .02f), .0f, -FastMath.cos(a) * (RADIUS + .02f));
            pearl.setLocalRotation(new Quaternion().fromAngleAxis(-a, Vector3f.UNIT_Y));
            wheel.attachChild(pearl);
        }
        Geometry hub = new Geometry("Hub", new com.jme3.scene.shape.Cylinder(2, 20, .07f, .05f, true));
        hub.setMaterial(gold);
        hub.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        hub.setLocalTranslation(0, .02f, 0);
        wheel.attachChild(hub);
        Geometry pointer = box(dyn, color(new ColorRGBA(.85f, .1f, .2f, 1f), .5f, 40f), tx, TOP + .15f, tz - RADIUS - .04f,
                .04f, .05f, .08f);
        pointer.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.QUARTER_PI * .0f, Vector3f.UNIT_Y));
    }

    @Override public void start(Context context) {
        super.start(context);
        float s = ctx.scale, w = 760f * s, h = 150f * s;
        float x = ctx.width / 2f - w / 2f, y = 110f * s;
        gui = new Node("Date wheel gui");
        ctx.gui.attachChild(gui);
        guiRect(gui, x - 5f * s, y - 5f * s, w + 10f * s, h + 10f * s, new ColorRGBA(.85f, .25f, .45f, 1f), -.5f);
        guiRect(gui, x, y, w, h, new ColorRGBA(.99f, .96f, .90f, 1f), -.4f);
        resultText = guiText(gui, "", 32f * s, new ColorRGBA(.10f, .08f, .08f, 1f), x + 25f * s, y + h - 25f * s, w - 50f * s, -.3f);
        showResult();
    }

    @Override public void stop() {
        if (gui != null) gui.removeFromParent();
        gui = null;
        spinning = false;
    }

    private void showResult() {
        if (resultText == null) return;
        if (spinning) resultText.setText("Spinning...");
        else if (picked < 0) resultText.setText("Press ACTION (SPACE or ENTER) to spin the wheel!");
        else resultText.setText(SHORT[picked] + ":  " + LONG[picked] + "\n\nPress ACTION to spin again");
    }

    @Override public void update(float tpf) {
        if (!spinning) return;
        theta += speed * tpf;
        speed *= (float) Math.exp(-.75f * tpf);
        wheel.setLocalRotation(new Quaternion().fromAngleAxis(theta, Vector3f.UNIT_Y));
        if (speed < .08f) {
            spinning = false;
            float t = theta % FastMath.TWO_PI;
            if (t < 0) t += FastMath.TWO_PI;
            picked = Math.min(SECTORS - 1, (int) (t / (FastMath.TWO_PI / SECTORS)));
            showResult();
        }
    }

    @Override public void input(int player, int key) {
        if (key != ACTION || spinning) return;
        spinning = true;
        speed = 9f + rnd.nextFloat() * 6f;
        picked = -1;
        showResult();
    }

    @Override public String status() {
        return spinning ? "The wheel is spinning..." : (picked < 0 ? "Spin the wheel!" : "Do it together: " + SHORT[picked]);
    }

    @Override public Vector3f cameraPosition() { return new Vector3f(tx, TOP + 2.3f, tz + 1.55f); }
}
