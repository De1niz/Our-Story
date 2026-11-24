package com.ourstory;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.control.AbstractControl;

/** Swings legs and arms of a CharacterModelBuilder model while it moves. */
public final class WalkAnimator extends AbstractControl {
    private final Node[] hip = new Node[2], knee = new Node[2], shoulder = new Node[2], elbow = new Node[2];
    private final Quaternion[] hipBase = new Quaternion[2], kneeBase = new Quaternion[2],
            shoulderBase = new Quaternion[2], elbowBase = new Quaternion[2];
    private float phase, amp, speed;

    public WalkAnimator(Node model) {
        for (int i = 0; i < 2; i++) {
            int s = i == 0 ? -1 : 1;
            hip[i] = (Node) model.getChild("Hip" + s);
            knee[i] = (Node) model.getChild("Knee" + s);
            shoulder[i] = (Node) model.getChild("Shoulder" + s);
            elbow[i] = (Node) model.getChild("Elbow" + s);
            hipBase[i] = hip[i].getLocalRotation().clone();
            kneeBase[i] = knee[i].getLocalRotation().clone();
            shoulderBase[i] = shoulder[i].getLocalRotation().clone();
            elbowBase[i] = elbow[i].getLocalRotation().clone();
        }
    }

    /** Horizontal speed in meters per second (0 = standing still). */
    public void setSpeed(float metersPerSecond) { speed = metersPerSecond; }

    @Override protected void controlUpdate(float tpf) {
        float target = FastMath.clamp(speed / 4f, 0f, 1f);
        amp += (target - amp) * Math.min(1f, tpf * 8f);
        if (amp > .02f) phase += tpf * (5f + speed * .7f);
        for (int i = 0; i < 2; i++) {
            float ph = phase + (i == 0 ? 0f : FastMath.PI);
            float swing = .55f * amp * FastMath.sin(ph);
            float bend = -(.15f * amp + .95f * amp * Math.max(0f, FastMath.cos(ph)));
            float arm = -.5f * amp * FastMath.sin(ph);
            float fore = .35f * amp * (.5f + .5f * FastMath.sin(ph + .6f));
            hip[i].setLocalRotation(hipBase[i].mult(new Quaternion().fromAngles(swing, 0, 0)));
            knee[i].setLocalRotation(kneeBase[i].mult(new Quaternion().fromAngles(bend, 0, 0)));
            shoulder[i].setLocalRotation(shoulderBase[i].mult(new Quaternion().fromAngles(arm, 0, 0)));
            elbow[i].setLocalRotation(elbowBase[i].mult(new Quaternion().fromAngles(fore, 0, 0)));
        }
    }

    @Override protected void controlRender(RenderManager rm, ViewPort vp) {}
}
