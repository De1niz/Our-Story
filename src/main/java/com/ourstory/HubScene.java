package com.ourstory;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import java.util.Random;

/**
 * The Memory Garden: a sunny park with a heart fountain in the middle and 12 memory lanterns
 * (one for each month) around a stone ring road. Walk to a lantern and press E / Enter.
 */
public class HubScene {
    public static final int COUNT = 12;
    private static final float ROAD_INNER = 17f, ROAD_OUTER = 25f, STATION_RADIUS = 24f, ARENA = 58f;

    private final WorldBuilder world;
    private final Vector3f[] stations = new Vector3f[COUNT];
    private SkyDome sky;
    private GameManager games;

    public HubScene(WorldBuilder world) { this.world = world; }

    /** Game tables are built together with the garden. */
    public void setGames(GameManager manager) { this.games = manager; }

    public SkyDome sky() { return sky; }

    public Vector3f spawn(int player) { return new Vector3f(player == 0 ? -1.6f : 1.6f, .1f, 44f); }

    public void build() {
        sky = new SkyDome(world.assets());
        world.node().attachChild(sky.geometry());
        Scenery sc = new Scenery(world);
        Random rnd = new Random(2024);
        Material stone = world.texture("stone.png");

        // ---- ground, tiled so the grass stays sharp
        Geometry ground = world.block("Grass terrain", 0, -.6f, 0, 150, .6f, 150, world.texture("grass.png"), true);
        ground.getMesh().scaleTextureCoordinates(new Vector2f(70f, 70f));
        Material hedge = world.material(new ColorRGBA(.13f, .28f, .13f, 1f));
        world.block("North wall", 0, 1.6f, -ARENA, ARENA, 1.6f, .8f, hedge, true);
        world.block("South wall", 0, 1.6f, ARENA, ARENA, 1.6f, .8f, hedge, true);
        world.block("West wall", -ARENA, 1.6f, 0, .8f, 1.6f, ARENA, hedge, true);
        world.block("East wall", ARENA, 1.6f, 0, .8f, 1.6f, ARENA, hedge, true);

        // ---- stone plaza, ring road and entrance path
        sc.annulus(0f, 10f, .02f, 14f, 6f, stone);
        sc.annulus(ROAD_INNER, ROAD_OUTER, .02f, 64f, 3f, stone);
        Geometry entrance = world.block("Entrance path", 0, .015f, 41.5f, 2.8f, .012f, 16.5f, stone, false);
        entrance.getMesh().scaleTextureCoordinates(new Vector2f(2f, 8f));
        Geometry link = world.block("Plaza link", 0, .015f, 13.5f, 2.2f, .012f, 4f, stone, false);
        link.getMesh().scaleTextureCoordinates(new Vector2f(2f, 2f));

        // ---- heart fountain and benches
        sc.fountain(0, 0);
        if (games != null) games.build(sc);
        for (int i = 0; i < 6; i++) {
            float a = i * FastMath.TWO_PI / 6f + .26f;
            float bx = FastMath.sin(a) * 12.2f, bz = -FastMath.cos(a) * 12.2f;
            sc.bench(bx, bz, FastMath.atan2(-bx, -bz));
        }
        for (int i = 0; i < 10; i++) {
            float a = i * FastMath.TWO_PI / 10f;
            sc.flowerPatch(FastMath.sin(a) * 14.6f, -FastMath.cos(a) * 14.6f, 1.8f, 22, rnd);
        }
        for (int i = 0; i < 5; i++) {
            float a = i * FastMath.TWO_PI / 5f + .5f;
            sc.tree(FastMath.sin(a) * 14.5f, -FastMath.cos(a) * 14.5f, 1.15f, 100 + i, 1);
        }

        // ---- 12 memory lanterns
        ColorRGBA amber = new ColorRGBA(1f, .78f, .42f, 1f), rose = new ColorRGBA(1f, .45f, .62f, 1f);
        for (int k = 0; k < COUNT; k++) {
            float a = k * FastMath.TWO_PI / COUNT;
            float sx = FastMath.sin(a), sz = -FastMath.cos(a);
            stations[k] = new Vector3f(sx * STATION_RADIUS, 0, sz * STATION_RADIUS);
            float tx = FastMath.cos(a), tz = FastMath.sin(a); // tangent
            float px = sx * 27.4f, pz = sz * 27.4f;
            sc.sign(px, pz, -a, "MONTH " + (k + 1), 1.15f);
            sc.lantern(px + tx * 2.9f, pz + tz * 2.9f, k == 0 ? rose : amber, k == 0 ? 1.25f : 1f);
            sc.lantern(px - tx * 2.9f, pz - tz * 2.9f, k == 0 ? rose : amber, k == 0 ? 1.25f : 1f);
            sc.flowerPatch(sx * 30f, sz * 30f, 3.2f, 30, rnd);
            sc.bush(sx * 31.5f + tx * 4f, sz * 31.5f + tz * 4f, 1f, k);
            sc.bush(sx * 31.5f - tx * 4f, sz * 31.5f - tz * 4f, 1.1f, k + 5);
        }

        // ---- lanterns along the entrance path
        for (int z = 52; z >= 30; z -= 7) {
            sc.lantern(-4.2f, z, amber, 1f);
            sc.lantern(4.2f, z, amber, 1f);
        }

        // ---- woods, bushes, rocks and wildflowers
        for (int i = 0; i < 190; i++) {
            float a = rnd.nextFloat() * FastMath.TWO_PI;
            float d = 33f + rnd.nextFloat() * 22f;
            float x = FastMath.sin(a) * d, z = -FastMath.cos(a) * d;
            if (Math.abs(x) > ARENA - 2f || Math.abs(z) > ARENA - 2f) continue;
            if (Math.abs(x) < 6f && z > 26f) continue; // keep the entrance open
            int pick = rnd.nextInt(10);
            if (pick < 4) sc.tree(x, z, .95f + rnd.nextFloat() * .75f, rnd.nextInt(50), pick == 0 ? 1 : 0);
            else if (pick < 6) sc.bush(x, z, .8f + rnd.nextFloat() * .6f, rnd.nextInt(30));
            else if (pick < 7) sc.rock(x, z, .8f + rnd.nextFloat() * 1.4f, rnd.nextInt(30));
            else sc.flowerPatch(x, z, 3f, 18, rnd);
        }
        for (int i = -ARENA_INT; i <= ARENA_INT; i += 6) {
            sc.tree(i + rnd.nextFloat() * 2f, -ARENA + 2f, 1.5f + rnd.nextFloat() * .6f, rnd.nextInt(50), 0);
            sc.tree(i + rnd.nextFloat() * 2f, ARENA - 2f, 1.5f + rnd.nextFloat() * .6f, rnd.nextInt(50), 0);
            sc.tree(-ARENA + 2f, i + rnd.nextFloat() * 2f, 1.5f + rnd.nextFloat() * .6f, rnd.nextInt(50), 0);
            sc.tree(ARENA - 2f, i + rnd.nextFloat() * 2f, 1.5f + rnd.nextFloat() * .6f, rnd.nextInt(50), 0);
        }

        // ---- far hills for depth
        for (int i = 0; i < 16; i++) {
            float a = i * FastMath.TWO_PI / 16f + rnd.nextFloat() * .3f;
            float d = 120f + rnd.nextFloat() * 40f;
            sc.hill(FastMath.sin(a) * d, -FastMath.cos(a) * d, 38f + rnd.nextFloat() * 30f, 16f + rnd.nextFloat() * 24f, i);
        }
        sc.finish();
    }

    private static final int ARENA_INT = (int) ARENA;

    /** Index of the memory lantern closest to feet (within reach), or -1. */
    public int nearby(Vector3f feet) {
        int best = -1;
        float bestDist = 5.5f;
        for (int i = 0; i < stations.length; i++) {
            if (stations[i] == null) continue;
            float dx = feet.x - stations[i].x, dz = feet.z - stations[i].z;
            float d = FastMath.sqrt(dx * dx + dz * dz);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        return best;
    }
}
