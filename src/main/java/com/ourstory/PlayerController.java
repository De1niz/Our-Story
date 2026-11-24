
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
 * Our Story - Third-person player controller.
 *
 * Controls:
 * WASD / Arrow Keys - Move
 * Mouse - Rotate camera
 * Space - Jump
 *
 * Features:
 * - Camera-relative movement
 * - Physics-based collision
 * - Gravity and jumping
 * - Third-person follow camera
 */
public class PlayerController implements ActionListener, AnalogListener {

    // ===== PLAYER SETTINGS =====
    private static final float WALK_SPEED = 0.2f;
    private static final float JUMP_SPEED = 8f;
    private static final float GRAVITY = 20f;
    private static final float FALL_SPEED = 28f;

    // ===== CAMERA SETTINGS =====
    private static final float CAMERA_DISTANCE = 8f;
    private static final float CAMERA_SENSITIVITY = 1.15f;
    private static final float CAPSULE_CENTER = 1.2f;

    // ===== PHYSICS =====
    private final CharacterControl physics;
    private final PhysicsSpace space;

    // ===== SCENE REFERENCES =====
    private final Node avatar;
    private final Camera camera;
    private final InputManager input;

    // ===== PLAYER STATE =====
    private boolean enabled;
    private boolean forward;
    private boolean backward;
    private boolean left;
    private boolean right;

    // ===== CAMERA ROTATION =====
    private float yaw = 0f;
    private float pitch = 0.28f;

    public PlayerController(
            PhysicsSpace space,
            Node avatar,
            Camera camera,
            InputManager input
    ) {
        this.space = space;
        this.avatar = avatar;
        this.camera = camera;
        this.input = input;

        // Create the player's capsule collision shape.
        physics = new CharacterControl(
                new CapsuleCollisionShape(0.38f, 1.65f, 1),
                0.24f
        );

        // Configure player physics.
        physics.setGravity(GRAVITY);
        physics.setJumpSpeed(JUMP_SPEED);
        physics.setFallSpeed(FALL_SPEED);

        space.add(physics);

        // ===== MOVEMENT INPUT =====
        input.addMapping(
                "PlayerForward",
                new KeyTrigger(KeyInput.KEY_W),
                new KeyTrigger(KeyInput.KEY_UP)
        );

        input.addMapping(
                "PlayerBackward",
                new KeyTrigger(KeyInput.KEY_S),
                new KeyTrigger(KeyInput.KEY_DOWN)
        );

        input.addMapping(
                "PlayerLeft",
                new KeyTrigger(KeyInput.KEY_A),
                new KeyTrigger(KeyInput.KEY_LEFT)
        );

        input.addMapping(
                "PlayerRight",
                new KeyTrigger(KeyInput.KEY_D),
                new KeyTrigger(KeyInput.KEY_RIGHT)
        );

        // ===== JUMP INPUT =====
        input.addMapping(
                "PlayerJump",
                new KeyTrigger(KeyInput.KEY_SPACE)
        );

        // ===== MOUSE CAMERA INPUT =====
        input.addMapping(
                "CamLeft",
                new MouseAxisTrigger(MouseInput.AXIS_X, true)
        );

        input.addMapping(
                "CamRight",
                new MouseAxisTrigger(MouseInput.AXIS_X, false)
        );

        input.addMapping(
                "CamUp",
                new MouseAxisTrigger(MouseInput.AXIS_Y, false)
        );

        input.addMapping(
                "CamDown",
                new MouseAxisTrigger(MouseInput.AXIS_Y, true)
        );

        // Register keyboard actions.
        input.addListener(
                (ActionListener) this,
                "PlayerForward",
                "PlayerBackward",
                "PlayerLeft",
                "PlayerRight",
                "PlayerJump"
        );

        // Register mouse movement.
        input.addListener(
                (AnalogListener) this,
                "CamLeft",
                "CamRight",
                "CamUp",
                "CamDown"
        );
    }

    /**
     * Enable or disable player movement.
     */
    public void setEnabled(boolean value) {
        enabled = value;

        if (!enabled) {
            forward = false;
            backward = false;
            left = false;
            right = false;

            physics.setWalkDirection(Vector3f.ZERO);
        }

        input.setCursorVisible(!enabled);
    }

    /**
     * Move the player to a new position.
     */
    public void teleport(Vector3f feet) {
        physics.setPhysicsLocation(
                feet.add(0, CAPSULE_CENTER, 0)
        );

        physics.setWalkDirection(Vector3f.ZERO);
    }

    /**
     * Get the player's feet position.
     */
    public Vector3f getFeet() {
        return physics.getPhysicsLocation()
                .subtract(0, CAPSULE_CENTER, 0);
    }

    /**
     * Update player movement and camera.
     */
    public void update(float tpf) {

        if (!enabled) {
            return;
        }

        // Calculate camera-relative directions.
        Vector3f forwardDir = new Vector3f(
                -FastMath.sin(yaw),
                0,
                -FastMath.cos(yaw)
        );

        Vector3f rightDir = new Vector3f(
                FastMath.cos(yaw),
                0,
                -FastMath.sin(yaw)
        );

        Vector3f direction = new Vector3f();

        // Apply keyboard movement.
        if (forward) {
            direction.addLocal(forwardDir);
        }

        if (backward) {
            direction.subtractLocal(forwardDir);
        }

        if (right) {
            direction.addLocal(rightDir);
        }

        if (left) {
            direction.subtractLocal(rightDir);
        }

        // Normalize movement and rotate character.
        if (direction.lengthSquared() > 0f) {

            direction.normalizeLocal();

            float rotation = FastMath.atan2(
                    -direction.x,
                    -direction.z
            );

            avatar.setLocalRotation(
                    new Quaternion().fromAngleAxis(
                            rotation,
                            Vector3f.UNIT_Y
                    )
            );
        }

        // ===== REALISTIC WALKING SPEED =====
        // Change WALK_SPEED at the top to adjust movement.
        physics.setWalkDirection(
                direction.multLocal(WALK_SPEED)
        );

        // Synchronize character with physics.
        Vector3f feet = getFeet();
        avatar.setLocalTranslation(feet);

        // Camera target.
        Vector3f target = feet.add(0, 1.45f, 0);

        float horizontal =
                CAMERA_DISTANCE * FastMath.cos(pitch);

        Vector3f offset = new Vector3f(
                FastMath.sin(yaw) * horizontal,
                CAMERA_DISTANCE * FastMath.sin(pitch) + 1.1f,
                FastMath.cos(yaw) * horizontal
        );

        // Position third-person camera.
        camera.setLocation(target.add(offset));
        camera.lookAt(target, Vector3f.UNIT_Y);
    }

    /**
     * Handle keyboard actions.
     */
    @Override
    public void onAction(
            String name,
            boolean pressed,
            float tpf
    ) {

        switch (name) {

            case "PlayerForward":
                forward = pressed;
                break;

            case "PlayerBackward":
                backward = pressed;
                break;

            case "PlayerLeft":
                left = pressed;
                break;

            case "PlayerRight":
                right = pressed;
                break;

            case "PlayerJump":
                if (enabled && pressed && physics.onGround()) {
                    physics.jump();
                }
                break;

            default:
                break;
        }
    }

    /**
     * Handle mouse camera rotation.
     */
    @Override
    public void onAnalog(
            String name,
            float value,
            float tpf
    ) {

        if (!enabled) {
            return;
        }

        switch (name) {

            case "CamLeft":
                yaw += value * CAMERA_SENSITIVITY;
                break;

            case "CamRight":
                yaw -= value * CAMERA_SENSITIVITY;
                break;

            case "CamUp":
                pitch += value * CAMERA_SENSITIVITY;
                break;

            case "CamDown":
                pitch -= value * CAMERA_SENSITIVITY;
                break;

            default:
                break;
        }

        // Limit vertical camera rotation.
        pitch = FastMath.clamp(
                pitch,
                -0.1f,
                0.85f
        );
    }
}
