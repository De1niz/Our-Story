package com.ourstory;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.scene.shape.Quad;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.shape.Box;

/** One two-player game on a table in the garden. Keyboard only: both players share one keyboard. */
public abstract class MiniGame {
    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3, ACTION = 4;
    /** Height of the table surface. */
    public static final float TOP = 1.0f;
    public static final ColorRGBA P1_COLOR = new ColorRGBA(.25f, .50f, .95f, 1f);
    public static final ColorRGBA P2_COLOR = new ColorRGBA(.95f, .36f, .58f, 1f);

    /** Things a game needs while it is played. */
    public static final class Context {
        public final SimpleApplication app;
        public final Node gui;
        public final BitmapFont font;
        public final float width, height, scale;

        public Context(SimpleApplication app, Node gui, BitmapFont font) {
            this.app = app;
            this.gui = gui;
            this.font = font;
            this.width = app.getCamera().getWidth();
            this.height = app.getCamera().getHeight();
            this.scale = height / 720f;
        }
    }

    public final String name;
    protected Scenery sc;
    protected Node dyn;
    protected float tx, tz;
    protected Context ctx;

    protected MiniGame(String name) { this.name = name; }

    public Vector3f tablePosition() { return new Vector3f(tx, 0, tz); }

    /** Creates the objects that live on the table. Called every time the garden is built. */
    public final void place(Scenery scenery, Node dynamicParent, float x, float z) {
        this.sc = scenery;
        this.dyn = dynamicParent;
        this.tx = x;
        this.tz = z;
        scenery.table(x, z);
        buildTable();
        label(name, x, TOP + 1.9f, z);
    }

    protected abstract void buildTable();

    /** Players sit down and the game starts. */
    public void start(Context context) { this.ctx = context; }

    /** Game closed (BACKSPACE). Remove anything drawn on the screen. */
    public void stop() {}

    public void update(float tpf) {}

    /** key is UP, DOWN, LEFT, RIGHT or ACTION; player is 0 (Player 1) or 1 (Player 2). */
    public abstract void input(int player, int key);

    /** Line shown in the top bar while playing. */
    public abstract String status();

    /** Controls shown in the bottom bar while playing. */
    public String help() { return "P1: W A S D + SPACE   |   P2: ARROWS + ENTER   |   BACKSPACE: leave the game"; }

    public abstract Vector3f cameraPosition();

    public Vector3f cameraTarget() { return new Vector3f(tx, TOP + .1f, tz); }

    // ------------------------------------------------------------------ helpers for games

    protected Material color(ColorRGBA c, float spec, float shine) { return sc.color(c, spec, shine); }

    protected Geometry box(Node parent, Material m, float x, float y, float z, float hx, float hy, float hz) {
        Geometry g = new Geometry("Game box", new Box(hx, hy, hz));
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        parent.attachChild(g);
        return g;
    }

    // ------------------------------------------------------------------ screen (GUI) helpers

    /** A flat coloured rectangle on the screen (x, y = bottom-left corner in pixels). */
    protected Geometry guiRect(Node parent, float x, float y, float w, float h, ColorRGBA c, float z) {
        Geometry g = new Geometry("Game panel", new Quad(w, h));
        Material m = new Material(ctx.app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", c);
        if (c.a < 1f) m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    /** Centered, wrapped screen text. x, y = top-left corner of the text box in pixels. */
    protected BitmapText guiText(Node parent, String s, float size, ColorRGBA c, float x, float y, float w, float z) {
        BitmapText t = new BitmapText(ctx.font);
        t.setSize(size);
        t.setColor(c);
        t.setText(s);
        t.setBox(new Rectangle(0, 0, w, size * 8f));
        t.setAlignment(BitmapFont.Align.Center);
        t.setLocalTranslation(x, y, z);
        parent.attachChild(t);
        return t;
    }

    /** A small name tag that always faces the camera. */
    protected void label(String text, float x, float y, float z) {
        Node n = new Node("Label");
        n.setLocalTranslation(x, y, z);
        Geometry bg = new Geometry("Label board", new Box(.95f, .24f, .01f));
        bg.setMaterial(color(new ColorRGBA(.98f, .95f, .88f, 1f), .05f, 5f));
        n.attachChild(bg);
        BitmapText t = new BitmapText(sc.font());
        t.setSize(.36f);
        t.setColor(ColorRGBA.Black);
        t.setText(text);
        t.setBox(new Rectangle(-.9f, .18f, 1.8f, .36f));
        t.setAlignment(BitmapFont.Align.Center);
        t.setVerticalAlignment(BitmapFont.VAlign.Center);
        t.setLocalTranslation(0, 0, .02f);
        n.attachChild(t);
        n.addControl(new BillboardControl());
        dyn.attachChild(n);
    }
}
