package com.ourstory;

import com.jme3.math.ColorRGBA;
import java.util.Random;

/** Editable, independent appearance of one player. */
public final class CharacterData {
    // Category indices
    public static final int FACE = 0, SKIN = 1, EYES = 2, NOSE = 3, HAIR = 4, HAIR_COLOR = 5,
            BEARD = 6, BUILD = 7, HEIGHT = 8, OUTFIT = 9, OUTFIT_COLOR = 10;

    public static final String[] CATEGORIES = {"Face Shape", "Skin Tone", "Eye Color", "Nose", "Hair Style",
            "Hair Color", "Beard", "Body Build", "Height", "Outfit", "Outfit Color"};

    public static final String[][] OPTIONS = {
            {"Narrow", "Balanced", "Wide", "Square Jaw", "Round"},
            {"Fair", "Light", "Medium", "Tan", "Brown", "Deep"},
            {"Brown", "Hazel", "Green", "Blue", "Gray"},
            {"Small", "Medium", "Large"},
            {"Bald", "Buzz Cut", "Short", "Side Part", "Long", "Bun", "Ponytail", "Curly"},
            {"Black", "Dark Brown", "Brown", "Auburn", "Blonde", "Gray", "Red"},
            {"None", "Stubble", "Short Beard", "Full Beard", "Goatee", "Moustache"},
            {"Slim", "Athletic", "Average", "Broad", "Curvy", "Heavy"},
            {"Short", "Average", "Tall"},
            {"T-Shirt", "Long Sleeve", "Dress"},
            {"Sky Blue", "Rose", "Forest", "Sand", "Charcoal", "Burgundy", "White", "Navy"}
    };

    public static final ColorRGBA[] SKIN_COLORS = {
            c(.93f, .78f, .68f), c(.88f, .69f, .55f), c(.78f, .56f, .41f),
            c(.66f, .45f, .31f), c(.47f, .31f, .21f), c(.30f, .19f, .13f)};
    public static final ColorRGBA[] EYE_COLORS = {
            c(.30f, .17f, .08f), c(.50f, .38f, .15f), c(.25f, .47f, .30f), c(.27f, .46f, .70f), c(.52f, .57f, .60f)};
    public static final ColorRGBA[] HAIR_COLORS = {
            c(.05f, .04f, .04f), c(.18f, .11f, .07f), c(.33f, .21f, .11f), c(.42f, .16f, .09f),
            c(.78f, .62f, .34f), c(.62f, .62f, .64f), c(.62f, .22f, .09f)};
    public static final ColorRGBA[] OUTFIT_COLORS = {
            c(.42f, .62f, .82f), c(.80f, .42f, .52f), c(.22f, .42f, .27f), c(.78f, .68f, .50f),
            c(.18f, .19f, .21f), c(.46f, .12f, .18f), c(.93f, .93f, .91f), c(.12f, .19f, .38f)};

    private static ColorRGBA c(float r, float g, float b) { return new ColorRGBA(r, g, b, 1f); }

    public final int playerNumber;
    public final int[] choices;
    public int category;
    public boolean ready;

    public CharacterData(int playerNumber) {
        this.playerNumber = playerNumber;
        if (playerNumber == 1) {
            choices = new int[]{1, 1, 0, 1, 2, 1, 1, 2, 1, 0, 0};
        } else {
            choices = new int[]{1, 1, 1, 1, 4, 2, 0, 4, 1, 0, 1};
        }
    }

    public String categoryName(int i) { return CATEGORIES[i]; }
    public String choiceName(int i) { return OPTIONS[i][choices[i]]; }
    public String categoryName() { return CATEGORIES[category]; }
    public String choiceName() { return OPTIONS[category][choices[category]]; }

    /** Swatch colour for colour categories, otherwise null. */
    public ColorRGBA swatch(int cat) {
        int v = choices[cat];
        switch (cat) {
            case SKIN: return SKIN_COLORS[v];
            case EYES: return EYE_COLORS[v];
            case HAIR_COLOR: return HAIR_COLORS[v];
            case OUTFIT_COLOR: return OUTFIT_COLORS[v];
            default: return null;
        }
    }

    public void changeCategory(int increment) {
        category = Math.floorMod(category + increment, CATEGORIES.length);
    }

    public void changeOption(int increment) {
        choices[category] = Math.floorMod(choices[category] + increment, OPTIONS[category].length);
        ready = false;
    }

    /** Sets one option directly (used by the sliders). */
    public void set(int cat, int index) {
        choices[cat] = Math.max(0, Math.min(OPTIONS[cat].length - 1, index));
        ready = false;
    }

    /** Applies a complete look (one value per category). */
    public void apply(int[] look) {
        for (int i = 0; i < choices.length && i < look.length; i++) choices[i] = look[i];
        ready = false;
    }

    public void reset() {
        apply(playerNumber == 1 ? new int[]{1, 1, 0, 1, 2, 1, 1, 2, 1, 0, 0}
                : new int[]{1, 1, 1, 1, 4, 2, 0, 4, 1, 0, 1});
    }

    public void randomize(Random rnd) {
        for (int i = 0; i < choices.length; i++) choices[i] = rnd.nextInt(OPTIONS[i].length);
        // keep the random look sensible: a beard only sometimes
        if (rnd.nextInt(3) != 0) choices[BEARD] = 0;
        ready = false;
    }
}
