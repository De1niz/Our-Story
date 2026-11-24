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
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import java.util.Random;

/** Memory cards: find the matching pairs. Players take turns; a match gives a point and another turn. */
public final class MemoryGame extends MiniGame {
    private static final int N = 4, CARDS = N * N;
    private static final float SIZE = .30f, STEP = .385f, Y = TOP + .02f;
    private static final String[] WORDS = {"HEART", "STAR", "MOON", "SUN", "FLOWER", "KISS", "RING", "CAKE"};
    private static final ColorRGBA[] COLORS = {
            new ColorRGBA(.95f, .35f, .50f, 1f), new ColorRGBA(.98f, .78f, .15f, 1f),
            new ColorRGBA(.45f, .45f, .90f, 1f), new ColorRGBA(.98f, .55f, .15f, 1f),
            new ColorRGBA(.45f, .80f, .40f, 1f), new ColorRGBA(.85f, .25f, .25f, 1f),
            new ColorRGBA(.40f, .75f, .90f, 1f), new ColorRGBA(.80f, .55f, .90f, 1f)};

    private final Node cardsNode = new Node("Memory cards");
    private final Node[] card = new Node[CARDS];
    private final BitmapText[] label = new BitmapText[CARDS];
    private final Geometry[] face = new Geometry[CARDS];
    private final int[] kind = new int[CARDS];
    private final boolean[] matched = new boolean[CARDS];
    private final float[] angle = new float[CARDS], goal = new float[CARDS];
    private final int[] score = new int[2];
    private Node frame;
    private int cursor, turn, first = -1, second = -1, matchesLeft;
    private float wait;
    private final Random rnd = new Random();

    public MemoryGame() { super("Memory Cards"); }

    private float cx(int i) { return tx + ((i % N) - (N - 1) / 2f) * STEP; }
    private float cz(int i) { return tz + ((N - 1) / 2f - (i / N)) * STEP; }

    @Override protected void buildTable() {
        Material cloth = color(new ColorRGBA(.36f, .08f, .18f, 1f), .05f, 5f);
        Material back = color(new ColorRGBA(.92f, .45f, .62f, 1f), .3f, 20f);
        box(dyn, cloth, tx, TOP + .008f, tz, 1.0f, .008f, 1.0f);
        dyn.attachChild(cardsNode);
        BitmapFont font = sc.font();
        for (int i = 0; i < CARDS; i++) {
            Node n = new Node("Card");
            Geometry top = new Geometry("Card back", new Box(SIZE / 2f, .012f, SIZE / 2f));
            top.setMaterial(back);
            top.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            n.attachChild(top);
            Geometry f = new Geometry("Card face plate", new Box(SIZE / 2f - .012f, .003f, SIZE / 2f - .012f));
            f.setMaterial(color(ColorRGBA.White, .2f, 10f).clone());
            f.setLocalTranslation(0, -.0145f, 0);
            n.attachChild(f);
            BitmapText t = new BitmapText(font);
            t.setSize(.065f);
            t.setColor(ColorRGBA.Black);
            t.setText("");
            t.setBox(new Rectangle(-SIZE / 2f + .01f, .05f, SIZE - .02f, .1f));
            t.setAlignment(BitmapFont.Align.Center);
            t.setVerticalAlignment(BitmapFont.VAlign.Center);
            Node faceText = new Node("Face text");
            faceText.setLocalTranslation(0, -.0185f, 0);
            faceText.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
            faceText.attachChild(t);
            n.attachChild(faceText);
            card[i] = n;
            label[i] = t;
            face[i] = f;
            cardsNode.attachChild(n);
        }
        frame = new Node("Memory frame");
        Material fm = color(new ColorRGBA(.98f, .85f, .10f, 1f), .1f, 8f);
        float h = SIZE / 2f + .02f, th = .008f;
        float[][] bars = {{0, -h, h, th}, {0, h, h, th}, {-h, 0, th, h}, {h, 0, th, h}};
        for (float[] b : bars) {
            Geometry g = new Geometry("bar", new Box(b[2], .005f, b[3]));
            g.setMaterial(fm);
            g.setLocalTranslation(b[0], .02f, b[1]);
            frame.attachChild(g);
        }
        dyn.attachChild(frame);
        deal();
    }

    private void deal() {
        int[] order = new int[CARDS];
        for (int i = 0; i < CARDS; i++) order[i] = i / 2;
        for (int i = CARDS - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int t = order[i]; order[i] = order[j]; order[j] = t; }
        for (int i = 0; i < CARDS; i++) {
            kind[i] = order[i];
            matched[i] = false;
            angle[i] = goal[i] = 0f;
            label[i].setText(WORDS[kind[i]]);
            face[i].getMaterial().setColor("Diffuse", COLORS[kind[i]]);
            face[i].getMaterial().setColor("Ambient", COLORS[kind[i]].mult(.75f));
            card[i].setLocalTranslation(cx(i), Y, cz(i));
            card[i].setLocalRotation(Quaternion.IDENTITY);
        }
        first = second = -1;
        score[0] = score[1] = 0;
        matchesLeft = CARDS / 2;
        turn = 0;
        cursor = 5;
        wait = 0;
        moveFrame();
    }

    private void moveFrame() {
        frame.setLocalTranslation(cx(cursor), Y, cz(cursor));
    }

    @Override public void update(float tpf) {
        for (int i = 0; i < CARDS; i++) {
            float d = goal[i] - angle[i];
            if (Math.abs(d) > .001f) {
                angle[i] += FastMath.sign(d) * Math.min(Math.abs(d), tpf * 9f);
                card[i].setLocalRotation(new Quaternion().fromAngleAxis(angle[i], Vector3f.UNIT_X));
                float lift = FastMath.sin(angle[i]) * .12f;
                card[i].setLocalTranslation(cx(i), Y + Math.abs(lift), cz(i));
            }
        }
        if (wait > 0f) {
            wait -= tpf;
            if (wait <= 0f) resolve();
        }
    }

    private void resolve() {
        if (kind[first] == kind[second]) {
            matched[first] = matched[second] = true;
            score[turn]++;
            matchesLeft--;
        } else {
            goal[first] = 0f;
            goal[second] = 0f;
            turn = 1 - turn;
        }
        first = second = -1;
    }

    @Override public void input(int player, int key) {
        if (matchesLeft == 0) {
            if (key == ACTION) deal();
            return;
        }
        if (player != turn || wait > 0f) return;
        int c = cursor % N, r = cursor / N;
        if (key == UP) r = Math.max(0, r - 1);
        else if (key == DOWN) r = Math.min(N - 1, r + 1);
        else if (key == LEFT) c = Math.max(0, c - 1);
        else if (key == RIGHT) c = Math.min(N - 1, c + 1);
        else if (key == ACTION) {
            if (matched[cursor] || cursor == first) return;
            goal[cursor] = FastMath.PI;
            if (first < 0) first = cursor;
            else { second = cursor; wait = 1.2f; }
            return;
        }
        cursor = r * N + c;
        moveFrame();
    }

    @Override public String status() {
        String scores = "P1 " + score[0] + "  -  P2 " + score[1];
        if (matchesLeft == 0) {
            String r = score[0] == score[1] ? "It's a tie!" : (score[0] > score[1] ? "Player 1 wins!" : "Player 2 wins!");
            return r + "  (" + scores + ")  -  press ACTION to play again";
        }
        return "Player " + (turn + 1) + "'s turn  -  " + scores + "  -  pick two cards";
    }

    @Override public Vector3f cameraPosition() { return new Vector3f(tx, TOP + 2.2f, tz + 1.55f); }

    @Override public Vector3f cameraTarget() { return new Vector3f(tx, TOP, tz + .05f); }
}
