package com.soarclient.event.client;

import com.soarclient.event.Event;

/**
 * 旋转事件 - 在玩家视角旋转时触发
 */
public class RotationEvent extends Event {
    
    private float yaw;
    private float pitch;
    private final float previousYaw;
    private final float previousPitch;
    private final boolean silent;

    public RotationEvent(float yaw, float pitch, float previousYaw, float previousPitch, boolean silent) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.previousYaw = previousYaw;
        this.previousPitch = previousPitch;
        this.silent = silent;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getPreviousYaw() {
        return previousYaw;
    }

    public float getPreviousPitch() {
        return previousPitch;
    }

    public boolean isSilent() {
        return silent;
    }
}
