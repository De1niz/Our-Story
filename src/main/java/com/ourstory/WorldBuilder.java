package com.ourstory;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;
import java.util.ArrayList;
import java.util.List;

/** Shared, editable scenery helpers. Visual meshes and physics are created together. */
public class WorldBuilder {
    private final AssetManager assets;
    private final PhysicsSpace space;
    private final Node world;
    private final List<RigidBodyControl> bodies = new ArrayList<>();

    public WorldBuilder(AssetManager assets, PhysicsSpace space, Node world) {
        this.assets = assets;
        this.space = space;
        this.world = world;
    }

    public AssetManager assets() { return assets; }
    public Node node() { return world; }

    /** Invisible solid box (static physics only). */
    public void collider(float x, float y, float z, float hx, float hy, float hz) {
        Node n = new Node("Collider");
        n.setLocalTranslation(x, y, z);
        RigidBodyControl body = new RigidBodyControl(new BoxCollisionShape(new Vector3f(hx, hy, hz)), 0f);
        n.addControl(body);
        world.attachChild(n);
        space.add(body);
        bodies.add(body);
    }

    /** Invisible solid upright cylinder (static physics only). */
    public void cylinderCollider(float x, float y, float z, float radius, float halfHeight) {
        Node n = new Node("Cylinder collider");
        n.setLocalTranslation(x, y, z);
        RigidBodyControl body = new RigidBodyControl(
                new CylinderCollisionShape(new Vector3f(radius, halfHeight, radius), 1), 0f);
        n.addControl(body);
        world.attachChild(n);
        space.add(body);
        bodies.add(body);
    }

    public void clear() {
        for (RigidBodyControl body : bodies) space.remove(body);
        bodies.clear();
        world.detachAllChildren();
    }

    public Material material(ColorRGBA color) {
        Material mat = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", color);
        mat.setColor("Ambient", color.mult(.8f));
        mat.setColor("Specular", ColorRGBA.White.mult(.22f));
        mat.setFloat("Shininess", 24f);
        return mat;
    }

    public Material texture(String filename) {
        Material mat = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        Texture texture = assets.loadTexture("Textures/" + filename);
        texture.setWrap(Texture.WrapMode.Repeat);
        mat.setTexture("DiffuseMap", texture);
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", ColorRGBA.White);
        mat.setColor("Ambient", ColorRGBA.White.mult(.8f));
        mat.setColor("Specular", ColorRGBA.White.mult(.1f));
        mat.setFloat("Shininess", 12f);
        return mat;
    }

    public Geometry block(String name, float x, float y, float z, float hx, float hy, float hz,
                          Material mat, boolean collidable) {
        Geometry geometry = new Geometry(name, new Box(hx, hy, hz));
        geometry.setMaterial(mat);
        geometry.setLocalTranslation(x, y, z);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        world.attachChild(geometry);
        if (collidable) {
            RigidBodyControl body = new RigidBodyControl(new BoxCollisionShape(new Vector3f(hx, hy, hz)), 0f);
            geometry.addControl(body);
            space.add(body);
            bodies.add(body);
        }
        return geometry;
    }

    public void sphere(String name, float x, float y, float z, float r, Material mat) {
        Geometry sphere = new Geometry(name, new Sphere(20, 24, r));
        sphere.setMaterial(mat);
        sphere.setLocalTranslation(x, y, z);
        sphere.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        world.attachChild(sphere);
    }

    public void tree(float x, float z) {
        Material bark = material(new ColorRGBA(.35f,.24f,.15f,1));
        Material leaves = material(new ColorRGBA(.19f,.37f,.20f,1));
        block("Solid tree trunk",x,1.4f,z,.34f,1.4f,.34f,bark,true);
        sphere("Leaf canopy",x,3.5f,z,1.5f,leaves);
        sphere("Leaf cluster",x+.8f,3.1f,z-.2f,1.0f,leaves);
    }

    public void ground() {
        // Ground has a flat top at y = 0. The player stands on this collider.
        block("Grass terrain",0,-.6f,0,42,.6f,42,texture("grass.png"),true);
        // Invisible boundaries stop the player at the world edge.
        Material edge = material(new ColorRGBA(.22f,.32f,.28f,1));
        block("North boundary",0,2,-40,40,2,1,edge,true);
        block("South boundary",0,2,40,40,2,1,edge,true);
        block("West boundary",-40,2,0,1,2,40,edge,true);
        block("East boundary",40,2,0,1,2,40,edge,true);
    }

    public void path(float z, float halfLength) {
        // Decorative: collision stays on the level ground so steps do not snag.
        block("Stone path",0,.013f,z,1.65f,.012f,halfLength,texture("stone.png"),false);
    }

    public void bench(float x, float z) {
        Material wood=texture("wood.png");
        block("Solid bench seat",x,.65f,z,1.1f,.12f,.42f,wood,true);
        block("Solid bench back",x,1.12f,z-.33f,1.1f,.48f,.12f,wood,true);
        block("Bench left leg",x-.82f,.32f,z,.12f,.32f,.12f,wood,true);
        block("Bench right leg",x+.82f,.32f,z,.12f,.32f,.12f,wood,true);
    }
}
