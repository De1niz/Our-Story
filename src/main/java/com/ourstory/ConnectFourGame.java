package com.ourstory;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Cylinder;

/** Connect 4. Player 1 = red, Player 2 = yellow. LEFT / RIGHT chooses the column, ACTION drops the disc. */
public final class ConnectFourGame extends MiniGame {
    private static final int COLS = 7, ROWS = 6;
    private static final float C = .22f;                 // cell size
    private static final float BY = TOP + .13f;          // bottom of the grid

    private final int[][] board = new int[COLS][ROWS];   // 0 empty, 1 = P1, 2 = P2
    private final Geometry[][] discs = new Geometry[COLS][ROWS];
    private final Node discNode = new Node("Connect4 discs");
    private Material[] discMat;
    private Geometry[] hover = new Geometry[2];
    private int column = 3, turn = 0;
    private int winner = 0;                               // 0 none, 1/2 player, 3 draw
    private final int[][] winCells = new int[4][2];
    private Geometry falling;
    private float fallY, fallV, fallTarget;
    private int bounces;
    private float pulse;

    public ConnectFourGame() { super("Connect 4"); }

    private float cx(int c) { return tx + (c - (COLS - 1) / 2f) * C; }
    private float cy(int r) { return BY + (r + .5f) * C; }

    @Override protected void buildTable() {
        Material blue = color(new ColorRGBA(.12f, .28f, .78f, 1f), .5f, 50f);
        Material dark = color(new ColorRGBA(.07f, .15f, .45f, 1f), .3f, 30f);
        Material wood = color(new ColorRGBA(.30f, .17f, .09f, 1f), .25f, 25f);
        discMat = new Material[]{color(new ColorRGBA(.90f, .15f, .18f, 1f), .6f, 60f),
                color(new ColorRGBA(.98f, .82f, .12f, 1f), .6f, 60f)};

        float half = COLS * C / 2f;
        float top = BY + ROWS * C;
        box(dyn, wood, tx, TOP + .04f, tz, half + .16f, .04f, .26f);               // base
        box(dyn, dark, tx, (BY + top) / 2f, tz - .045f, half + .02f, (top - BY) / 2f + .02f, .012f); // back
        box(dyn, blue, tx - half - .02f, (BY + top) / 2f, tz, .035f, (top - BY) / 2f + .02f, .06f);   // sides
        box(dyn, blue, tx + half + .02f, (BY + top) / 2f, tz, .035f, (top - BY) / 2f + .02f, .06f);
        box(dyn, blue, tx, BY - .02f, tz, half + .055f, .03f, .06f);
        box(dyn, blue, tx, top + .02f, tz, half + .055f, .03f, .06f);

        Mesh ring = ProceduralMesh.grid(2, 32, true, (r, c, out) -> {
            float a = FastMath.TWO_PI * c / 32f;
            float cs = FastMath.cos(a), sn = FastMath.sin(a);
            if (r == 0) out.set(.083f * cs, .083f * sn, 0f);
            else { float m = Math.max(Math.abs(cs), Math.abs(sn)); out.set(.11f * cs / m, .11f * sn / m, 0f); }
        }, (r, c, p) -> new ColorRGBA(1f, 1f, 1f, 1f), new Vector3f(0, 0, 1));
        for (int c = 0; c < COLS; c++)
            for (int r = 0; r < ROWS; r++) {
                Geometry g = new Geometry("Connect4 cell", ring);
                g.setMaterial(color(new ColorRGBA(.12f, .30f, .85f, 1f), .5f, 50f));
                g.setLocalTranslation(cx(c), cy(r), tz + .06f);
                g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                dyn.attachChild(g);
            }

        dyn.attachChild(discNode);
        for (int p = 0; p < 2; p++) {
            hover[p] = newDisc(p);
            hover[p].setLocalTranslation(cx(column), top + .2f, tz);
            discNode.attachChild(hover[p]);
            hover[p].setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        }
        showHover();
    }

    private Geometry newDisc(int player) {
        Geometry g = new Geometry("Connect4 disc", new Cylinder(2, 24, .083f, .09f, true));
        g.setMaterial(discMat[player]);
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        return g;
    }

    private void showHover() {
        boolean on = winner == 0 && falling == null;
        for (int p = 0; p < 2; p++) {
            hover[p].setCullHint(on && p == turn ? com.jme3.scene.Spatial.CullHint.Inherit
                    : com.jme3.scene.Spatial.CullHint.Always);
            hover[p].setLocalTranslation(cx(column), BY + ROWS * C + .2f, tz);
        }
    }

    private void reset() {
        for (int c = 0; c < COLS; c++)
            for (int r = 0; r < ROWS; r++) {
                board[c][r] = 0;
                if (discs[c][r] != null) { discs[c][r].removeFromParent(); discs[c][r] = null; }
            }
        falling = null;
        winner = 0;
        turn = 0;
        column = 3;
        showHover();
    }

    @Override public void input(int player, int key) {
        if (winner != 0) {
            if (key == ACTION) reset();
            return;
        }
        if (player != turn || falling != null) return;
        if (key == LEFT) column = Math.max(0, column - 1);
        else if (key == RIGHT) column = Math.min(COLS - 1, column + 1);
        else if (key == ACTION) drop();
        showHover();
    }

    private void drop() {
        int r = 0;
        while (r < ROWS && board[column][r] != 0) r++;
        if (r >= ROWS) return;
        board[column][r] = turn + 1;
        Geometry d = newDisc(turn);
        discs[column][r] = d;
        discNode.attachChild(d);
        falling = d;
        fallY = BY + ROWS * C + .2f;
        fallTarget = cy(r);
        fallV = 0f;
        bounces = 0;
        d.setLocalTranslation(cx(column), fallY, tz);
        showHover();
    }

    @Override public void update(float tpf) {
        pulse += tpf;
        if (falling != null) {
            fallV -= 9.8f * tpf;
            fallY += fallV * tpf;
            if (fallY <= fallTarget) {
                fallY = fallTarget;
                if (bounces < 2) { fallV = -fallV * .28f; bounces++; }
                else {
                    falling = null;
                    afterDrop();
                }
            }
            if (falling != null) falling.setLocalTranslation(falling.getLocalTranslation().x, fallY, tz);
            else {
                for (int c = 0; c < COLS; c++)
                    for (int r = 0; r < ROWS; r++)
                        if (discs[c][r] != null && board[c][r] != 0)
                            discs[c][r].setLocalTranslation(cx(c), cy(r), tz);
            }
        }
        if (winner == 1 || winner == 2) {
            float s = 1f + .12f * FastMath.sin(pulse * 8f);
            for (int[] w : winCells) discs[w[0]][w[1]].setLocalScale(s, s, 1f);
        }
    }

    private void afterDrop() {
        int who = turn + 1;
        if (findWin(who)) winner = who;
        else {
            boolean full = true;
            for (int c = 0; c < COLS; c++) if (board[c][ROWS - 1] == 0) full = false;
            if (full) winner = 3;
        }
        if (winner == 0) turn = 1 - turn;
        showHover();
    }

    private boolean findWin(int who) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int c = 0; c < COLS; c++)
            for (int r = 0; r < ROWS; r++)
                for (int[] d : dirs) {
                    int k = 0;
                    while (k < 4) {
                        int cc = c + d[0] * k, rr = r + d[1] * k;
                        if (cc < 0 || cc >= COLS || rr < 0 || rr >= ROWS || board[cc][rr] != who) break;
                        k++;
                    }
                    if (k == 4) {
                        for (int i = 0; i < 4; i++) { winCells[i][0] = c + d[0] * i; winCells[i][1] = r + d[1] * i; }
                        return true;
                    }
                }
        return false;
    }

    @Override public String status() {
        if (winner == 1) return "Player 1 (RED) wins!  -  press ACTION to play again";
        if (winner == 2) return "Player 2 (YELLOW) wins!  -  press ACTION to play again";
        if (winner == 3) return "The board is full - it's a draw!  -  press ACTION to play again";
        return (turn == 0 ? "Player 1 (RED)" : "Player 2 (YELLOW)") + " - LEFT / RIGHT to choose a column, ACTION to drop";
    }

    @Override public Vector3f cameraPosition() { return new Vector3f(tx, TOP + 1.25f, tz + 2.55f); }

    @Override public Vector3f cameraTarget() { return new Vector3f(tx, TOP + .88f, tz); }
}
