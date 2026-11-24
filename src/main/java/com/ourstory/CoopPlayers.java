package com.ourstory;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;

/**
 * Two players in the same world with one shared follow camera.
 * Player 1: W A S D + Space.  Player 2: arrow keys + Right Shift.  Mouse rotates the camera.
 * The camera zooms out automatically when the players walk apart.
 */
public final class CoopPlayers implements ActionListener, AnalogListener {
    // ===== EASY SETTINGS =====
    /** Walking speed (distance per physics step). Make it bigger to run faster. */
    private static final float WALK_SPEED = 0.085f;
    private static final float JUMP_SPEED = 9f;
    private static final float GRAVITY = 24f;
    private static final float CAMERA_MIN_DISTANCE = 8.5f;
    private static final float CAMERA_MAX_DISTANCE = 28f;
    private static final float CAMERA_SENSITIVITY = 1.15f;
    /** Players cannot walk farther apart than this (meters). */
    private static final float LEASH = 26f;

    private static final float RADIUS = .42f, CYLINDER = 1.3f;
    private static final float CENTER = CYLINDER / 2f + RADIUS;

    private final CharacterControl[] body = new CharacterControl[2];
    private final Node[] avatar;
    private final WalkAnimator[] animator = new WalkAnimator[2];
    private final boolean[][] keys = new boolean[2][4]; // forward, back, left, right
    private final float[] face = new float[2];
    private final float[] speed = new float[2];
    private final Vector3f[] lastFeet = {new Vector3f(), new Vector3f()};
    private final Camera camera;
    private final InputManager input;
    private final Vector3f camCenter = new Vector3f();
    private boolean enabled;
    private boolean frozen, overrideOn;
    private final Vector3f ovPos = new Vector3f(), ovTarget = new Vector3f();
    private float blend;
    private float yaw, pitch = .30f;

    public CoopPlayers(PhysicsSpace space, Node[] avatars, Camera camera, InputManager input) {
        this.avatar = avatars;
        this.camera = camera;
        this.input = input;
        for (int i = 0; i < 2; i++) {
            body[i] = new CharacterControl(new CapsuleCollisionShape(RADIUS, CYLINDER, 1), .35f);
            body[i].setGravity(GRAVITY);
            body[i].setJumpSpeed(JUMP_SPEED);
            body[i].setFallSpeed(45f);
            space.add(body[i]);
        }
        int[][] k = {
                {KeyInput.KEY_W, KeyInput.KEY_S, KeyInput.KEY_A, KeyInput.KEY_D, KeyInput.KEY_SPACE},
                {KeyInput.KEY_UP, KeyInput.KEY_DOWN, KeyInput.KEY_LEFT, KeyInput.KEY_RIGHT, KeyInput.KEY_RSHIFT}};
        String[] suffix = {"F", "B", "L", "R", "J"};
        for (int p = 0; p < 2; p++)
            for (int i = 0; i < 5; i++) {
                String name = "Co" + (p + 1) + suffix[i];
                input.addMapping(name, new KeyTrigger(k[p][i]));
                input.addListener((ActionListener) this, name);
            }
        input.addMapping("CoCamLeft", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        input.addMapping("CoCamRight", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        input.addMapping("CoCamUp", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        input.addMapping("CoCamDown", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        input.addListener((AnalogListener) this, "CoCamLeft", "CoCamRight", "CoCamUp", "CoCamDown");
    }

    /** Lets a model move its legs and arms while walking. */
    public void setAnimator(int p, WalkAnimator a) { animator[p] = a; }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            for (int p = 0; p < 2; p++) {
                for (int i = 0; i < 4; i++) keys[p][i] = false;
                body[p].setWalkDirection(Vector3f.ZERO);
                if (animator[p] != null) animator[p].setSpeed(0f);
            }
        }
        input.setCursorVisible(!enabled);
    }

    /** Put player p at the given feet position, facing the given yaw (0 = looking toward -Z). */
    public void teleport(int p, Vector3f feet, float facing) {
        body[p].setPhysicsLocation(feet.add(0, CENTER, 0));
        body[p].setWalkDirection(Vector3f.ZERO);
        face[p] = facing;
        lastFeet[p].set(feet);
        avatar[p].setLocalTranslation(feet);
        avatar[p].setLocalRotation(new Quaternion().fromAngleAxis(facing, Vector3f.UNIT_Y));
    }

    public void resetCamera(float newYaw) {
        yaw = newYaw;
        pitch = .30f;
        camCenter.set(middle().add(0, 1.6f, 0));
    }

    /** While frozen the players stand still (used at game tables). */
    public void setFrozen(boolean value) { frozen = value; }

    /** Smoothly moves the camera to this view. */
    public void setCameraOverride(Vector3f position, Vector3f target) {
        ovPos.set(position);
        ovTarget.set(target);
        overrideOn = true;
    }

    public void clearCameraOverride() { overrideOn = false; }

    public Vector3f getFeet(int p) { return body[p].getPhysicsLocation().subtract(0, CENTER, 0); }

    public Vector3f middle() { return getFeet(0).add(getFeet(1)).multLocal(.5f); }

    public void update(float tpf) {
        if (!enabled) return;
        Vector3f fwd = new Vector3f(-FastMath.sin(yaw), 0, -FastMath.cos(yaw));
        Vector3f right = new Vector3f(FastMath.cos(yaw), 0, -FastMath.sin(yaw));
        Vector3f[] feet = {getFeet(0), getFeet(1)};
        for (int p = 0; p < 2; p++) {
            Vector3f dir = new Vector3f();
            if (keys[p][0]) dir.addLocal(fwd);
            if (keys[p][1]) dir.subtractLocal(fwd);
            if (keys[p][3]) dir.addLocal(right);
            if (keys[p][2]) dir.subtractLocal(right);
            if (frozen) dir.set(0, 0, 0);
            if (dir.lengthSquared() > 0f) {
                dir.normalizeLocal();
                Vector3f away = feet[p].subtract(feet[1 - p]);
                away.y = 0;
                if (away.length() > LEASH && dir.dot(away) > 0f) dir.set(0, 0, 0);
            }
            if (dir.lengthSquared() > 0f) {
                float target = FastMath.atan2(-dir.x, -dir.z);
                float diff = target - face[p];
                while (diff > FastMath.PI) diff -= FastMath.TWO_PI;
                while (diff < -FastMath.PI) diff += FastMath.TWO_PI;
                face[p] += diff * Math.min(1f, tpf * 14f);
            }
            body[p].setWalkDirection(dir.mult(WALK_SPEED));
            Vector3f f = getFeet(p);
            avatar[p].setLocalTranslation(f);
            avatar[p].setLocalRotation(new Quaternion().fromAngleAxis(face[p], Vector3f.UNIT_Y));
            float dx = f.x - lastFeet[p].x, dz = f.z - lastFeet[p].z;
            float measured = tpf > 0f ? FastMath.sqrt(dx * dx + dz * dz) / tpf : 0f;
            speed[p] += (measured - speed[p]) * Math.min(1f, tpf * 10f);
            lastFeet[p].set(f);
            if (animator[p] != null) animator[p].setSpeed(speed[p] < .3f ? 0f : speed[p]);
        }
        // shared follow camera
        Vector3f a = getFeet(0), b = getFeet(1);
        float sep = FastMath.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
        float dist = FastMath.clamp(CAMERA_MIN_DISTANCE + sep * .85f, CAMERA_MIN_DISTANCE, CAMERA_MAX_DISTANCE);
        Vector3f goal = middle().add(0, 1.6f, 0);
        camCenter.interpolateLocal(goal, Math.min(1f, tpf * 6f));
        float horizontal = dist * FastMath.cos(pitch);
        Vector3f offset = new Vector3f(FastMath.sin(yaw) * horizontal, dist * FastMath.sin(pitch) + 1.1f,
                FastMath.cos(yaw) * horizontal);
        Vector3f followPos = camCenter.add(offset);
        blend += ((overrideOn ? 1f : 0f) - blend) * Math.min(1f, tpf * 3.2f);
        if (!overrideOn && blend < .002f) blend = 0f;
        if (blend > 0f) {
            float k = blend * blend * (3f - 2f * blend);
            Vector3f pos = followPos.mult(1f - k).add(ovPos.mult(k));
            Vector3f look = camCenter.mult(1f - k).add(ovTarget.mult(k));
            camera.setLocation(pos);
            camera.lookAt(look, Vector3f.UNIT_Y);
        } else {
            camera.setLocation(followPos);
            camera.lookAt(camCenter, Vector3f.UNIT_Y);
        }
    }

    @Override public void onAction(String name, boolean pressed, float tpf) {
        if (!name.startsWith("Co") || name.length() != 4) return;
        int p = name.charAt(2) - '1';
        if (p < 0 || p > 1) return;
        switch (name.charAt(3)) {
            case 'F': keys[p][0] = pressed; break;
            case 'B': keys[p][1] = pressed; break;
            case 'L': keys[p][2] = pressed; break;
            case 'R': keys[p][3] = pressed; break;
            case 'J': if (enabled && !frozen && pressed && body[p].onGround()) body[p].jump(); break;
            default: break;
        }
    }

    @Override public void onAnalog(String name, float value, float tpf) {
        if (!enabled) return;
        switch (name) {
            case "CoCamLeft": yaw += value * CAMERA_SENSITIVITY; break;
            case "CoCamRight": yaw -= value * CAMERA_SENSITIVITY; break;
            case "CoCamUp": pitch += value * CAMERA_SENSITIVITY; break;
            case "CoCamDown": pitch -= value * CAMERA_SENSITIVITY; break;
            default: break;
        }
        pitch = FastMath.clamp(pitch, -.1f, .85f);
    }
}
