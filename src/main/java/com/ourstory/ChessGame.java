package com.ourstory;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Full chess on a wooden board. Player 1 = White, Player 2 = Black. Same keys as every game. */
public final class ChessGame extends MiniGame {
    private static final float S = .22f;               // size of one square
    private static final float Y0 = TOP + .076f;       // top of the squares

    private ChessRules rules = ChessRules.start();
    private final Node pieces = new Node("Chess pieces");
    private final Node marks = new Node("Chess marks");
    private final Map<Integer, Mesh> meshes = new HashMap<>();
    private Material vertexMat;
    private Material ivory, ebony;

    private final int[] cursor = {12, 52};              // e2 and e7
    private int selected = -1;
    private List<ChessRules.Move> targets = new ArrayList<>();
    private int lastFrom = -1, lastTo = -1;

    private final Node[] cursorFrame = new Node[2];
    private Node selectFrame;
    private Geometry lastA, lastB, checkMark;
    private final Geometry[] targetMark = new Geometry[32];
    private Material targetMat, captureMat;

    public ChessGame() { super("Chess"); }

    // ------------------------------------------------------------------ table

    private float sx(int sq) { return tx + ((sq & 7) - 3.5f) * S; }
    private float sz(int sq) { return tz + (3.5f - (sq >> 3)) * S; }

    @Override protected void buildTable() {
        Material frame = color(new ColorRGBA(.30f, .17f, .09f, 1f), .25f, 25f);
        Material light = color(new ColorRGBA(.93f, .84f, .66f, 1f), .15f, 18f);
        Material dark = color(new ColorRGBA(.45f, .27f, .15f, 1f), .15f, 18f);
        box(dyn, frame, tx, TOP + .03f, tz, 4 * S + .09f, .03f, 4 * S + .09f);
        for (int sq = 0; sq < 64; sq++) {
            boolean isLight = (((sq & 7) + (sq >> 3)) & 1) == 1;
            box(dyn, isLight ? light : dark, sx(sq), TOP + .068f, sz(sq), S / 2f, .008f, S / 2f);
        }
        vertexMat = sc.vertex(.35f, 30f);
        ivory = color(new ColorRGBA(.94f, .90f, .80f, 1f), .5f, 40f);
        ebony = color(new ColorRGBA(.17f, .14f, .13f, 1f), .5f, 40f);
        targetMat = color(new ColorRGBA(.25f, .80f, .35f, 1f), .1f, 8f);
        captureMat = color(new ColorRGBA(.90f, .25f, .22f, 1f), .1f, 8f);

        dyn.attachChild(marks);
        dyn.attachChild(pieces);
        Material last = color(new ColorRGBA(.95f, .85f, .35f, 1f), .05f, 5f);
        lastA = overlay(last);
        lastB = overlay(last);
        checkMark = overlay(captureMat);
        for (int i = 0; i < targetMark.length; i++) targetMark[i] = overlay(targetMat);
        cursorFrame[0] = frame(P1_COLOR, S * .5f, .011f);
        cursorFrame[1] = frame(P2_COLOR, S * .5f - .012f, .016f);
        selectFrame = frame(new ColorRGBA(.98f, .85f, .10f, 1f), S * .5f - .024f, .021f);
        refresh();
    }

    private Geometry overlay(Material m) {
        Geometry g = new Geometry("Chess mark", new Box(S * .48f, .003f, S * .48f));
        g.setMaterial(m);
        g.setCullHint(Spatial.CullHint.Always);
        marks.attachChild(g);
        return g;
    }

    /** Four thin bars around a square. */
    private Node frame(ColorRGBA c, float half, float lift) {
        Node n = new Node("Chess frame");
        Material m = color(c, .1f, 8f);
        float t = .008f;
        float[][] bars = {{0, -half, half, t}, {0, half, half, t}, {-half, 0, t, half}, {half, 0, t, half}};
        for (float[] b : bars) {
            Geometry g = new Geometry("bar", new Box(b[2], .004f, b[3]));
            g.setMaterial(m);
            g.setLocalTranslation(b[0], lift, b[1]);
            n.attachChild(g);
        }
        n.setCullHint(Spatial.CullHint.Always);
        marks.attachChild(n);
        return n;
    }

    private void put(Spatial s, int sq, float lift) {
        s.setLocalTranslation(sx(sq), Y0 + lift, sz(sq));
        s.setCullHint(Spatial.CullHint.Inherit);
    }

    // ------------------------------------------------------------------ pieces

    // profiles: pairs of {height, radius} from bottom to top
    private static final float[][] PAWN = {{0, .001f}, {0, .07f}, {.012f, .075f}, {.026f, .058f}, {.05f, .033f},
            {.08f, .025f}, {.092f, .04f}, {.1f, .028f}, {.118f, .042f}, {.136f, .042f}, {.15f, .03f}, {.158f, .001f}};
    private static final float[][] ROOK = {{0, .001f}, {0, .08f}, {.014f, .085f}, {.03f, .065f}, {.07f, .052f},
            {.13f, .05f}, {.15f, .068f}, {.19f, .068f}, {.19f, .001f}};
    private static final float[][] BISHOP = {{0, .001f}, {0, .08f}, {.014f, .085f}, {.03f, .062f}, {.08f, .034f},
            {.11f, .03f}, {.118f, .05f}, {.128f, .03f}, {.15f, .05f}, {.185f, .04f}, {.215f, .015f}, {.232f, .02f},
            {.246f, .012f}, {.252f, .001f}};
    private static final float[][] QUEEN = {{0, .001f}, {0, .085f}, {.014f, .09f}, {.03f, .066f}, {.09f, .036f},
            {.14f, .036f}, {.155f, .062f}, {.2f, .068f}, {.225f, .052f}, {.235f, .03f}, {.26f, .028f},
            {.28f, .02f}, {.29f, .001f}};
    private static final float[][] KING = {{0, .001f}, {0, .085f}, {.014f, .09f}, {.03f, .066f}, {.1f, .038f},
            {.15f, .04f}, {.165f, .064f}, {.21f, .066f}, {.24f, .05f}, {.255f, .036f}, {.262f, .001f}};
    private static final float[][] KNIGHT_BASE = {{0, .001f}, {0, .08f}, {.014f, .085f}, {.03f, .065f}, {.07f, .05f},
            {.09f, .045f}, {.09f, .001f}};

    private Mesh lathe(float[][] profile, ColorRGBA color) {
        ProceduralMesh.Ring[] rings = new ProceduralMesh.Ring[profile.length];
        for (int i = 0; i < profile.length; i++)
            rings[i] = new ProceduralMesh.Ring(profile[i][0], 0, 0, profile[i][1], profile[i][1], 2f);
        ColorRGBA shade = color;
        return ProceduralMesh.loft(rings, 44, 22, (r, c, p) -> shade);
    }

    private Mesh meshFor(int type, boolean white) {
        int key = type + (white ? 0 : 10);
        Mesh m = meshes.get(key);
        if (m == null) {
            ColorRGBA col = white ? new ColorRGBA(.94f, .90f, .80f, 1f) : new ColorRGBA(.20f, .16f, .15f, 1f);
            float[][] prof;
            switch (type) {
                case ChessRules.PAWN: prof = PAWN; break;
                case ChessRules.ROOK: prof = ROOK; break;
                case ChessRules.BISHOP: prof = BISHOP; break;
                case ChessRules.QUEEN: prof = QUEEN; break;
                case ChessRules.KING: prof = KING; break;
                default: prof = KNIGHT_BASE;
            }
            m = lathe(prof, col);
            meshes.put(key, m);
        }
        return m;
    }

    private Geometry part(Node parent, Material m, float x, float y, float z, float hx, float hy, float hz, float tiltX) {
        Geometry g = new Geometry("Piece part", new Box(hx, hy, hz));
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        if (tiltX != 0f) g.setLocalRotation(new Quaternion().fromAngleAxis(tiltX, Vector3f.UNIT_X));
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        parent.attachChild(g);
        return g;
    }

    private Node makePiece(int type, boolean white) {
        Node n = new Node("Piece");
        Geometry body = new Geometry("Piece body", meshFor(type, white));
        body.setMaterial(vertexMat);
        body.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        n.attachChild(body);
        Material m = white ? ivory : ebony;
        switch (type) {
            case ChessRules.ROOK:
                for (int i = 0; i < 4; i++) {
                    float a = i * FastMath.HALF_PI + FastMath.QUARTER_PI;
                    part(n, m, FastMath.sin(a) * .056f, .205f, FastMath.cos(a) * .056f, .02f, .018f, .02f, 0);
                }
                break;
            case ChessRules.KING:
                part(n, m, 0, .292f, 0, .008f, .028f, .008f, 0);
                part(n, m, 0, .302f, 0, .026f, .008f, .008f, 0);
                break;
            case ChessRules.KNIGHT: {
                Node head = new Node("Knight head");
                part(head, m, 0, .13f, .005f, .032f, .06f, .028f, -.30f);
                part(head, m, 0, .20f, -.025f, .028f, .03f, .05f, 0);
                part(head, m, 0, .185f, -.08f, .02f, .018f, .03f, .2f);
                part(head, m, -.016f, .238f, .0f, .008f, .016f, .01f, 0);
                part(head, m, .016f, .238f, .0f, .008f, .016f, .01f, 0);
                part(head, m, 0, .215f, .045f, .014f, .04f, .012f, .5f);
                head.setLocalRotation(new Quaternion().fromAngleAxis(white ? 0f : FastMath.PI, Vector3f.UNIT_Y));
                n.attachChild(head);
                break;
            }
            default: break;
        }
        return n;
    }

    private void refresh() {
        pieces.detachAllChildren();
        for (int sq = 0; sq < 64; sq++) {
            int p = rules.at(sq);
            if (p == 0) continue;
            Node n = makePiece(Math.abs(p), p > 0);
            put(n, sq, sq == selected ? .06f : 0f);
            pieces.attachChild(n);
        }
        for (Geometry g : targetMark) g.setCullHint(Spatial.CullHint.Always);
        lastA.setCullHint(Spatial.CullHint.Always);
        lastB.setCullHint(Spatial.CullHint.Always);
        checkMark.setCullHint(Spatial.CullHint.Always);
        if (lastFrom >= 0) { put(lastA, lastFrom, .004f); put(lastB, lastTo, .004f); }
        if (rules.inCheck(rules.whiteToMove())) put(checkMark, rules.kingSquare(rules.whiteToMove()), .002f);
        if (selected >= 0) {
            put(selectFrame, selected, 0);
            int i = 0;
            for (ChessRules.Move m : targets) {
                if (i >= targetMark.length) break;
                Geometry g = targetMark[i++];
                boolean capture = rules.at(m.to) != 0 || m.enPassant;
                g.setMaterial(capture ? captureMat : targetMat);
                g.setLocalScale(capture ? 1f : .34f, 1f, capture ? 1f : .34f);
                put(g, m.to, .008f);
            }
        } else {
            selectFrame.setCullHint(Spatial.CullHint.Always);
        }
        for (int p = 0; p < 2; p++) put(cursorFrame[p], cursor[p], 0);
        if (cursor[0] == cursor[1]) cursorFrame[1].setLocalTranslation(sx(cursor[1]), Y0 + .002f, sz(cursor[1]));
    }

    // ------------------------------------------------------------------ play

    @Override public void start(Context context) {
        super.start(context);
        refresh();
    }

    @Override public void input(int player, int key) {
        int over = rules.result();
        if (key != ACTION) {
            int c = cursor[player];
            int f = c & 7, r = c >> 3;
            if (key == UP) r = Math.min(7, r + 1);
            if (key == DOWN) r = Math.max(0, r - 1);
            if (key == LEFT) f = Math.max(0, f - 1);
            if (key == RIGHT) f = Math.min(7, f + 1);
            cursor[player] = r * 8 + f;
            refresh();
            return;
        }
        if (over == ChessRules.CHECKMATE || over == ChessRules.STALEMATE || over == ChessRules.DRAW) {
            rules = ChessRules.start();
            selected = -1;
            targets.clear();
            lastFrom = lastTo = -1;
            cursor[0] = 12;
            cursor[1] = 52;
            refresh();
            return;
        }
        boolean whiteMoves = rules.whiteToMove();
        if ((player == 0) != whiteMoves) return; // not your turn
        int sq = cursor[player];
        if (selected >= 0) {
            ChessRules.Move m = rules.find(selected, sq);
            if (m != null) {
                rules = rules.after(m);
                lastFrom = m.from;
                lastTo = m.to;
                selected = -1;
                targets.clear();
                refresh();
                return;
            }
        }
        int p = rules.at(sq);
        if (p != 0 && (p > 0) == whiteMoves && sq != selected) {
            List<ChessRules.Move> list = rules.legalFrom(sq);
            if (!list.isEmpty()) { selected = sq; targets = list; refresh(); return; }
        }
        selected = -1;
        targets.clear();
        refresh();
    }

    @Override public String status() {
        int res = rules.result();
        boolean w = rules.whiteToMove();
        if (res == ChessRules.CHECKMATE)
            return "CHECKMATE!  " + (w ? "Player 2 (Black)" : "Player 1 (White)") + " wins  -  press ACTION to play again";
        if (res == ChessRules.STALEMATE) return "Stalemate - a draw  -  press ACTION to play again";
        if (res == ChessRules.DRAW) return "Draw  -  press ACTION to play again";
        String who = w ? "Player 1 (White)" : "Player 2 (Black)";
        String check = rules.inCheck(w) ? "  CHECK!" : "";
        return who + " to move" + check + (selected >= 0 ? "  -  choose where to go" : "  -  choose a piece");
    }

    @Override public Vector3f cameraPosition() { return new Vector3f(tx, TOP + 2.35f, tz + 1.75f); }

    @Override public Vector3f cameraTarget() { return new Vector3f(tx, TOP + .05f, tz + .05f); }
}
