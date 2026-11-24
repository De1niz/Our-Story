package com.ourstory;

import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Who is more likely to...?"  Both players secretly answer with one key:
 * LEFT = Player 1, RIGHT = Player 2. When you both pick the same person, you score a heart.
 */
public final class LoveQuizGame extends MiniGame {
    private static final String[] QUESTIONS = {
            "Who is more likely to cry at a movie?",
            "Who is more likely to get us lost?",
            "Who is more likely to say \"I love you\" first?",
            "Who is more likely to forget an anniversary?",
            "Who is more likely to eat the last bite?",
            "Who is more likely to fall asleep first during a film?",
            "Who is more likely to plan a surprise date?",
            "Who is more likely to be late?",
            "Who is more likely to steal the blanket?",
            "Who is more likely to spend too much on a gift?",
            "Who is more likely to sing in the shower?",
            "Who is more likely to win an argument?",
            "Who is more likely to dance in public?",
            "Who is more likely to cook the better dinner?",
            "Who is more likely to adopt a pet without asking?",
            "Who is more likely to say sorry first?"};
    private static final int ROUNDS = 8;

    private final List<String> deck = new ArrayList<>();
    private final int[] vote = {-1, -1};
    private int round, hearts;
    private boolean reveal;
    private Node gui;
    private BitmapText title, question, info, result;

    public LoveQuizGame() { super("Love Quiz"); }

    @Override protected void buildTable() {
        Material pink = color(new ColorRGBA(.95f, .55f, .70f, 1f), .3f, 20f);
        Material cream = color(new ColorRGBA(.98f, .94f, .85f, 1f), .1f, 8f);
        for (int i = 0; i < 6; i++) box(dyn, i % 2 == 0 ? pink : cream, tx + .02f * i - .05f, TOP + .012f + i * .024f, tz, .45f, .012f, .3f);
        box(dyn, color(new ColorRGBA(.85f, .15f, .30f, 1f), .5f, 40f), tx, TOP + .17f, tz, .12f, .02f, .12f);
    }

    private void newGame() {
        deck.clear();
        for (String q : QUESTIONS) deck.add(q);
        Collections.shuffle(deck);
        round = 0;
        hearts = 0;
        nextRound();
    }

    private void nextRound() {
        vote[0] = vote[1] = -1;
        reveal = false;
        refresh();
    }

    @Override public void start(Context context) {
        super.start(context);
        float s = ctx.scale, w = 860f * s, h = 330f * s;
        float x = ctx.width / 2f - w / 2f, y = ctx.height / 2f - h / 2f + 30f * s;
        gui = new Node("Love quiz gui");
        ctx.gui.attachChild(gui);
        guiRect(gui, x - 6f * s, y - 6f * s, w + 12f * s, h + 12f * s, new ColorRGBA(.85f, .25f, .45f, 1f), -.5f);
        guiRect(gui, x, y, w, h, new ColorRGBA(.99f, .96f, .90f, 1f), -.4f);
        ColorRGBA ink = new ColorRGBA(.10f, .08f, .08f, 1f);
        title = guiText(gui, "", 30f * s, new ColorRGBA(.75f, .12f, .32f, 1f), x, y + h - 18f * s, w, -.3f);
        question = guiText(gui, "", 40f * s, ink, x + 30f * s, y + h - 80f * s, w - 60f * s, -.3f);
        info = guiText(gui, "", 28f * s, ink, x + 30f * s, y + h - 200f * s, w - 60f * s, -.3f);
        result = guiText(gui, "", 30f * s, new ColorRGBA(.75f, .12f, .32f, 1f), x + 30f * s, y + 90f * s, w - 60f * s, -.3f);
        if (deck.isEmpty() || round >= ROUNDS) newGame();
        else refresh();
    }

    @Override public void stop() {
        if (gui != null) gui.removeFromParent();
        gui = null;
    }

    private void refresh() {
        if (gui == null) return;
        if (round >= ROUNDS) {
            title.setText("Quiz finished");
            question.setText("You thought alike " + hearts + " out of " + ROUNDS + " times!");
            info.setText(hearts >= 7 ? "You are in perfect sync. Soulmates!"
                    : hearts >= 5 ? "You know each other really well."
                    : hearts >= 3 ? "Still so much to discover about each other."
                    : "Opposites attract! Talk about your answers.");
            result.setText("Press ACTION to play again");
            return;
        }
        title.setText("Round " + (round + 1) + " / " + ROUNDS + "     Hearts: " + hearts);
        question.setText(deck.get(round));
        if (!reveal) {
            info.setText("Answer secretly:  LEFT = Player 1     RIGHT = Player 2\n"
                    + "Player 1: " + (vote[0] < 0 ? "thinking..." : "LOCKED")
                    + "     Player 2: " + (vote[1] < 0 ? "thinking..." : "LOCKED"));
            result.setText("");
        } else {
            info.setText("Player 1 said: Player " + (vote[0] + 1) + "     Player 2 said: Player " + (vote[1] + 1));
            result.setText((vote[0] == vote[1] ? "You agree!  +1 heart" : "You disagree!  Tell each other why.")
                    + "\nPress ACTION for the next question");
        }
    }

    @Override public void input(int player, int key) {
        if (round >= ROUNDS) {
            if (key == ACTION) newGame();
            return;
        }
        if (!reveal) {
            if (vote[player] >= 0) return;
            if (key == LEFT) vote[player] = 0;
            else if (key == RIGHT) vote[player] = 1;
            else return;
            if (vote[0] >= 0 && vote[1] >= 0) {
                reveal = true;
                if (vote[0] == vote[1]) hearts++;
            }
            refresh();
        } else if (key == ACTION) {
            round++;
            nextRound();
        }
    }

    @Override public String status() {
        if (round >= ROUNDS) return "Quiz finished - " + hearts + " hearts!";
        return reveal ? "Compare your answers!" : "Answer the question about each other";
    }

    @Override public Vector3f cameraPosition() { return new Vector3f(tx, TOP + 1.7f, tz + 2.1f); }
}
