package com.soarclient.utils;

import com.soarclient.Soar;
import com.soarclient.event.EventBus;
import com.soarclient.event.client.RotationEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * 静默旋转处理器 - 管理客户端和服务器的旋转状态
 * 
 * 工作原理：
 * 1. 客户端显示旋转（视觉）- 玩家看到的视角
 * 2. 服务器旋转（数据包）- 发送给服务器的视角
 * 3. 通过分离两者，实现"静默"旋转效果
 */
public class SilentRotationHandler {
    
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static SilentRotationHandler instance;
    
    // 服务器旋转（实际发送给服务器的角度）
    private float serverYaw;
    private float serverPitch;
    
    // 客户端旋转（视觉上显示的角度）
    private float clientYaw;
    private float clientPitch;
    
    // 目标旋转（希望到达的角度）
    private float targetYaw;
    private float targetPitch;
    
    // 状态标志
    private boolean isActive;
    private boolean isSilent;
    private int ticksRemaining;
    
    // 平滑旋转参数
    private float smoothSpeed;
    private boolean useSmoothRotation;
    
    public SilentRotationHandler() {
        this.serverYaw = 0;
        this.serverPitch = 0;
        this.clientYaw = 0;
        this.clientPitch = 0;
        this.targetYaw = 0;
        this.targetPitch = 0;
        this.isActive = false;
        this.isSilent = false;
        this.ticksRemaining = 0;
        this.smoothSpeed = 10.0f;
        this.useSmoothRotation = true;
    }
    
    public static SilentRotationHandler getInstance() {
        if (instance == null) {
            instance = new SilentRotationHandler();
        }
        return instance;
    }
    
    /**
     * 设置目标旋转角度
     * @param yaw 目标偏航角
     * @param pitch 目标俯仰角
     * @param silent 是否静默（不更新服务器旋转）
     * @param ticks 持续tick数（0表示无限）
     */
    public void setTargetRotation(float yaw, float pitch, boolean silent, int ticks) {
        this.targetYaw = MathHelper.wrapDegrees(yaw);
        this.targetPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        this.isSilent = silent;
        this.ticksRemaining = ticks;
        this.isActive = true;
        
        // 如果不使用平滑旋转，立即设置
        if (!useSmoothRotation) {
            applyRotation();
        }
    }
    
    /**
     * 设置目标旋转（默认非静默，持续5ticks）
     */
    public void setTargetRotation(float yaw, float pitch) {
        setTargetRotation(yaw, pitch, false, 5);
    }
    
    /**
     * 停止静默旋转
     */
    public void stop() {
        this.isActive = false;
        this.isSilent = false;
        this.ticksRemaining = 0;
    }
    
    /**
     * 每tick调用，更新旋转状态
     */
    public void onUpdate() {
        if (!isActive || mc.player == null) return;
        
        // 更新tick计数
        if (ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining <= 0) {
                stop();
                return;
            }
        }
        
        // 应用平滑旋转
        if (useSmoothRotation) {
            applySmoothRotation();
        }
    }
    
    /**
     * 应用旋转到玩家
     */
    private void applyRotation() {
        if (mc.player == null) return;
        
        float previousYaw = mc.player.getYaw();
        float previousPitch = mc.player.getPitch();
        
        // 触发旋转事件
        RotationEvent event = new RotationEvent(targetYaw, targetPitch, previousYaw, previousPitch, isSilent);
        EventBus.getInstance().post(event);
        
        if (event.isCancelled()) return;
        
        // 更新客户端旋转（视觉）
        clientYaw = event.getYaw();
        clientPitch = event.getPitch();
        
        // 如果不是静默模式，也更新服务器旋转
        if (!isSilent) {
            serverYaw = clientYaw;
            serverPitch = clientPitch;
            
            // 应用到玩家
            mc.player.setYaw(clientYaw);
            mc.player.setPitch(clientPitch);
        } else {
            // 静默模式：只更新视觉，不更新服务器
            mc.player.setYaw(clientYaw);
            mc.player.setPitch(clientPitch);
        }
    }
    
    /**
     * 应用平滑旋转
     */
    private void applySmoothRotation() {
        if (mc.player == null) return;
        
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        
        // 计算角度差
        float yawDiff = RotationUtil.getAngleDifference(targetYaw, currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        
        // 限制每tick的最大旋转角度
        float maxYawChange = smoothSpeed;
        float maxPitchChange = smoothSpeed;
        
        // 计算新的旋转角度
        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -maxYawChange, maxYawChange);
        float newPitch = currentPitch + MathHelper.clamp(pitchDiff, -maxPitchChange, maxPitchChange);
        
        // 检查是否已经到达目标
        if (Math.abs(yawDiff) < 0.1f && Math.abs(pitchDiff) < 0.1f) {
            newYaw = targetYaw;
            newPitch = targetPitch;
        }
        
        setTargetRotation(newYaw, newPitch, isSilent, ticksRemaining);
    }
    
    /**
     * 获取服务器旋转角度（用于发送数据包）
     */
    public float getServerYaw() {
        return serverYaw;
    }
    
    public float getServerPitch() {
        return serverPitch;
    }
    
    /**
     * 获取客户端旋转角度（用于渲染）
     */
    public float getClientYaw() {
        return clientYaw;
    }
    
    public float getClientPitch() {
        return clientPitch;
    }
    
    /**
     * 重置服务器旋转到当前玩家旋转
     */
    public void resetServerRotation() {
        if (mc.player != null) {
            serverYaw = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
        }
    }
    
    /**
     * 检查是否正在静默旋转
     */
    public boolean isSilentActive() {
        return isActive && isSilent;
    }
    
    /**
     * 检查是否有活动的旋转
     */
    public boolean isActive() {
        return isActive;
    }
    
    // Getters and Setters
    public boolean isSilent() {
        return isSilent;
    }
    
    public void setSilent(boolean silent) {
        this.isSilent = silent;
    }
    
    public float getSmoothSpeed() {
        return smoothSpeed;
    }
    
    public void setSmoothSpeed(float smoothSpeed) {
        this.smoothSpeed = smoothSpeed;
    }
    
    public boolean isUseSmoothRotation() {
        return useSmoothRotation;
    }
    
    public void setUseSmoothRotation(boolean useSmoothRotation) {
        this.useSmoothRotation = useSmoothRotation;
    }
    
    public int getTicksRemaining() {
        return ticksRemaining;
    }
}
