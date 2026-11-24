package com.ourstory;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import java.util.Random;

/**
 * Chapter 1 - Month 1: an enchanted forest. A winding lantern path leads to a clearing with
 * the bench where it all began, a pond with a picnic blanket and a blossoming wishing tree.
 * Story text is edited in ChapterOneContent.java.
 */
public class ChapterOneScene {
    private static final float ARENA = 46f;
    private static final float CLEARING_Z = -7f;

    private final WorldBuilder world;
    private SkyDome sky;
    /** Memory 1: the bench. Memory 2: the picnic by the pond. Memory 3: the wishing tree. */
    private final Vector3f[] markers = {
            new Vector3f(0f, 0, -5.0f),
            new Vector3f(-7f, 0, 3f),
            new Vector3f(8.6f, 0, 0f)
    };

    public ChapterOneScene(WorldBuilder world) { this.world = world; }

    public SkyDome sky() { return sky; }

    private static float pathX(float z) {
        float u = Math.min(z, 27f);
        return 3.2f * FastMath.sin(.16f * (27f - u)) * Math.min(1f, Math.max(0f, (u + 4f) / 20f));
    }

    private static boolean blocked(float x, float z) {
        if (z > -5f && z < 46f && Math.abs(x - pathX(z)) < 5f) return true;
        if (dist(x, z, 0f, CLEARING_Z) < 11f) return true;
        if (dist(x, z, -13f, -1f) < 6.5f) return true;
        if (dist(x, z, -7f, 3f) < 4.5f) return true;
        if (dist(x, z, 10f, -1.5f) < 7f) return true;
        return false;
    }

    private static float dist(float x, float z, float cx, float cz) {
        return FastMath.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
    }

    public void build() {
        sky = new SkyDome(world.assets());
        world.node().attachChild(sky.geometry());
        Scenery sc = new Scenery(world);
        Random rnd = new Random(11);
        Material stone = world.texture("stone.png");

        // ---- ground and soft boundary
        Geometry ground = world.block("Forest floor", 0, -.6f, 0, 150, .6f, 150, world.texture("grass.png"), true);
        ground.getMesh().scaleTextureCoordinates(new Vector2f(70f, 70f));
        Material hedge = world.material(new ColorRGBA(.10f, .22f, .10f, 1f));
        world.block("North wall", 0, 1.6f, -ARENA, ARENA, 1.6f, .8f, hedge, true);
        world.block("South wall", 0, 1.6f, ARENA, ARENA, 1.6f, .8f, hedge, true);
        world.block("West wall", -ARENA, 1.6f, 0, .8f, 1.6f, ARENA, hedge, true);
        world.block("East wall", ARENA, 1.6f, 0, .8f, 1.6f, ARENA, hedge, true);

        // ---- winding stone path and the clearing
        for (float z = 44f; z >= -4f; z -= .85f) {
            float x = pathX(z) + (ProceduralMesh.hash((int) (z * 10), 3) - .5f) * .35f;
            world.block("Path stone", x, .012f, z, .75f + .25f * ProceduralMesh.hash((int) (z * 10), 4), .012f, .5f, stone, false);
        }
        world.block("Clearing stones", 0, .015f, CLEARING_Z, 4.6f, .012f, 3.8f, stone, false);

        // ---- the bench where it all began
        sc.bench(0f, CLEARING_Z - 1.2f, 0f);
        sc.benchPlaque(0f, CLEARING_Z - 1.2f, 0f, "The bench where I first asked you out");
        sc.bigSign(0f, CLEARING_Z - 4.4f, 0f, "THE BENCH WHERE I FIRST ASKED YOU OUT");
        sc.lantern(-3.4f, CLEARING_Z - 2f, new ColorRGBA(1f, .78f, .42f, 1f), 1f);
        sc.lantern(3.4f, CLEARING_Z - 2f, new ColorRGBA(1f, .78f, .42f, 1f), 1f);
        sc.flowerPatch(-2.6f, CLEARING_Z - 3.4f, 1.6f, 22, rnd);
        sc.flowerPatch(2.6f, CLEARING_Z - 3.4f, 1.6f, 22, rnd);
        // two big trees frame the clearing and carry fairy lights
        sc.tree(-6.5f, CLEARING_Z - 2.5f, 1.6f, 201, 0);
        sc.tree(6.5f, CLEARING_Z - 2.5f, 1.6f, 202, 0);
        sc.fairyLights(-6.5f, 4.6f, CLEARING_Z - 2.5f, 6.5f, 4.6f, CLEARING_Z - 2.5f, 20);
        sc.fairyLights(-6.5f, 4.0f, CLEARING_Z - 2.5f, 0f, 4.4f, CLEARING_Z - 5.5f, 10);
        sc.fairyLights(6.5f, 4.0f, CLEARING_Z - 2.5f, 0f, 4.4f, CLEARING_Z - 5.5f, 10);

        // ---- lantern path
        ColorRGBA amber = new ColorRGBA(1f, .78f, .42f, 1f);
        for (int z = 38; z >= 6; z -= 8) {
            sc.lantern(pathX(z) - 2.3f, z, amber, 1f);
            sc.lantern(pathX(z) + 2.3f, z - 4f, amber, 1f);
        }
        sc.tree(-5.5f, 32f, 1.5f, 301, 0);
        sc.tree(5.5f, 32f, 1.5f, 302, 0);
        sc.fairyLights(-5.5f, 4.3f, 32f, 5.5f, 4.3f, 32f, 16);

        // ---- pond and picnic (memory 2)
        sc.pond(-13f, -1f, 3.6f);
        sc.picnic(-7f, 3f);
        sc.flowerPatch(-9.5f, 5.5f, 2.2f, 24, rnd);
        sc.memoryOrb(markers[1].x, markers[1].z, new ColorRGBA(1f, .62f, .76f, 1f));

        // ---- wishing tree (memory 3)
        sc.tree(12.5f, -3f, 2.0f, 77, 1);
        sc.rock(10.2f, -0.2f, 1.5f, 5);
        sc.fairyLights(9.5f, 4.5f, -3f, 15.5f, 4.5f, -3f, 12);
        sc.fairyLights(12.5f, 4.2f, -6f, 12.5f, 4.2f, 0f, 12);
        sc.flowerPatch(10f, -3f, 3.2f, 30, rnd);
        sc.memoryOrb(markers[2].x, markers[2].z, new ColorRGBA(.98f, .85f, .45f, 1f));

        // ---- bench memory orb (memory 1)
        sc.memoryOrb(markers[0].x, markers[0].z, new ColorRGBA(1f, .70f, .55f, 1f));

        // ---- the forest
        for (int i = 0; i < 360; i++) {
            float x = (rnd.nextFloat() * 2f - 1f) * (ARENA - 2f);
            float z = (rnd.nextFloat() * 2f - 1f) * (ARENA - 2f);
            if (blocked(x, z)) continue;
            int pick = rnd.nextInt(12);
            if (pick < 7) sc.tree(x, z, .9f + rnd.nextFloat() * .9f, rnd.nextInt(60), pick == 0 ? 1 : 0);
            else if (pick < 9) sc.bush(x, z, .8f + rnd.nextFloat() * .7f, rnd.nextInt(30));
            else if (pick < 10) sc.rock(x, z, .8f + rnd.nextFloat() * 1.3f, rnd.nextInt(30));
            else sc.flowerPatch(x, z, 2.6f, 14, rnd);
        }
        for (int i = -ARENA_INT; i <= ARENA_INT; i += 5) {
            sc.tree(i + rnd.nextFloat() * 2f, -ARENA + 2f, 1.6f + rnd.nextFloat() * .6f, rnd.nextInt(60), 0);
            sc.tree(i + rnd.nextFloat() * 2f, ARENA - 2f, 1.6f + rnd.nextFloat() * .6f, rnd.nextInt(60), 0);
            sc.tree(-ARENA + 2f, i + rnd.nextFloat() * 2f, 1.6f + rnd.nextFloat() * .6f, rnd.nextInt(60), 0);
            sc.tree(ARENA - 2f, i + rnd.nextFloat() * 2f, 1.6f + rnd.nextFloat() * .6f, rnd.nextInt(60), 0);
        }
        // undergrowth along the path and fireflies in the air
        for (float z = 40f; z > -3f; z -= 4.5f) {
            sc.bush(pathX(z) - 4.2f - rnd.nextFloat(), z, 1f, (int) z);
            sc.bush(pathX(z) + 4.2f + rnd.nextFloat(), z + 1f, 1.1f, (int) z + 7);
            sc.flowerPatch(pathX(z) + (rnd.nextBoolean() ? 3f : -3f), z, 1.2f, 8, rnd);
        }
        ColorRGBA glowGreen = new ColorRGBA(.85f, 1f, .45f, 1f);
        for (int i = 0; i < 70; i++) {
            float a = rnd.nextFloat() * FastMath.TWO_PI, d = 3f + rnd.nextFloat() * 18f;
            sc.firefly(FastMath.sin(a) * d, .6f + rnd.nextFloat() * 2.4f, CLEARING_Z + FastMath.cos(a) * d, 1f, glowGreen);
        }
        for (int i = 0; i < 24; i++) {
            float z = 40f - i * 1.6f;
            sc.firefly(pathX(z) + (rnd.nextFloat() - .5f) * 7f, .5f + rnd.nextFloat() * 2f, z, 1f, glowGreen);
        }
        for (int i = 0; i < 14; i++) {
            float a = i * FastMath.TWO_PI / 14f + rnd.nextFloat() * .3f;
            float d = 110f + rnd.nextFloat() * 40f;
            sc.hill(FastMath.sin(a) * d, -FastMath.cos(a) * d, 36f + rnd.nextFloat() * 28f, 16f + rnd.nextFloat() * 22f, i);
        }
        sc.finish();
    }

    private static final int ARENA_INT = (int) ARENA;

    /** Index of the memory spot within reach of the given position, or -1. */
    public int nearby(Vector3f feet) {
        for (int i = 0; i < markers.length; i++) if (feet.distance(markers[i]) < 2.9f) return i;
        return -1;
    }
}
