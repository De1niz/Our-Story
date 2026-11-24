package com.ourstory;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Game tables around the fountain. Walk up to a table and press E / ENTER to play.
 * While playing, both characters sit at the table, the camera moves to it and the keyboard
 * controls the game: Player 1 = W A S D + SPACE, Player 2 = ARROWS + ENTER.
 */
public final class GameManager implements ActionListener {
    /** Receives text for the top and bottom bars. All null = go back to the normal garden text. */
    public interface Hud { void show(String title, String status, String help); }

    private static final float REACH = 3.8f;
    private static final float TABLE_RADIUS = 7.4f;

    private final SimpleApplication app;
    private final Node worldNode;
    private final CoopPlayers coop;
    private final Hud hud;
    private final Node dyn = new Node("Games");
    private final List<MiniGame> games = new ArrayList<>();
    private MiniGame active;
    private String lastTitle, lastStatus, lastHelp;
    private float cooldown;

    public GameManager(SimpleApplication app, Node worldNode, CoopPlayers coop, InputManager input, Hud hud) {
        this.app = app;
        this.worldNode = worldNode;
        this.coop = coop;
        this.hud = hud;
        int[][] keys = {
                {KeyInput.KEY_W, KeyInput.KEY_S, KeyInput.KEY_A, KeyInput.KEY_D, KeyInput.KEY_SPACE},
                {KeyInput.KEY_UP, KeyInput.KEY_DOWN, KeyInput.KEY_LEFT, KeyInput.KEY_RIGHT, KeyInput.KEY_RETURN}};
        String[] suffix = {"U", "D", "L", "R", "A"};
        for (int p = 0; p < 2; p++)
            for (int i = 0; i < 5; i++) {
                String name = "Gm" + (p + 1) + suffix[i];
                input.addMapping(name, new KeyTrigger(keys[p][i]));
                input.addListener(this, name);
            }
    }

    /** Creates all tables. Called whenever the garden is built. */
    public void build(Scenery sc) {
        active = null;
        games.clear();
        dyn.detachAllChildren();
        dyn.removeFromParent();
        worldNode.attachChild(dyn);
        games.add(new ChessGame());
        games.add(new ConnectFourGame());
        games.add(new MemoryGame());
        games.add(new LoveQuizGame());
        games.add(new DateWheelGame());
        for (int i = 0; i < games.size(); i++) {
            float a = i * FastMath.TWO_PI / games.size();
            games.get(i).place(sc, dyn, FastMath.sin(a) * TABLE_RADIUS, -FastMath.cos(a) * TABLE_RADIUS);
        }
    }

    public boolean isPlaying() { return active != null; }

    /** The table closest to either player (within reach), or null. */
    public MiniGame nearest() {
        MiniGame best = null;
        float bestDist = REACH;
        for (MiniGame g : games) {
            Vector3f t = g.tablePosition();
            for (int p = 0; p < 2; p++) {
                Vector3f f = coop.getFeet(p);
                float d = FastMath.sqrt((f.x - t.x) * (f.x - t.x) + (f.z - t.z) * (f.z - t.z));
                if (d < bestDist) { bestDist = d; best = g; }
            }
        }
        return best;
    }

    public void start(MiniGame g) {
        active = g;
        cooldown = .35f;
        Vector3f t = g.tablePosition();
        coop.setFrozen(true);
        coop.teleport(0, new Vector3f(t.x, .1f, t.z + 2.4f), 0f);
        coop.teleport(1, new Vector3f(t.x, .1f, t.z - 2.4f), FastMath.PI);
        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        g.start(new MiniGame.Context(app, app.getGuiNode(), font));
        coop.setCameraOverride(g.cameraPosition(), g.cameraTarget());
        lastTitle = lastStatus = lastHelp = null;
        pushHud();
    }

    public void stop() {
        if (active == null) return;
        active.stop();
        active = null;
        coop.clearCameraOverride();
        coop.setFrozen(false);
        hud.show(null, null, null);
    }

    public void update(float tpf) {
        if (active == null) return;
        if (cooldown > 0f) cooldown -= tpf;
        active.update(tpf);
        coop.setCameraOverride(active.cameraPosition(), active.cameraTarget());
        pushHud();
    }

    private void pushHud() {
        String t = active.name.toUpperCase(), s = active.status(), h = active.help();
        if (t.equals(lastTitle) && s.equals(lastStatus) && h.equals(lastHelp)) return;
        lastTitle = t; lastStatus = s; lastHelp = h;
        hud.show(t, s, h);
    }

    @Override public void onAction(String name, boolean pressed, float tpf) {
        if (!pressed || active == null || cooldown > 0f || name.length() != 4) return;
        int p = name.charAt(2) - '1';
        int key;
        switch (name.charAt(3)) {
            case 'U': key = MiniGame.UP; break;
            case 'D': key = MiniGame.DOWN; break;
            case 'L': key = MiniGame.LEFT; break;
            case 'R': key = MiniGame.RIGHT; break;
            default: key = MiniGame.ACTION; break;
        }
        active.input(p, key);
    }
}
