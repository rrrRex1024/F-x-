package com.soarclient.management.mod.impl.combat;

import com.soarclient.Soar;
import com.soarclient.event.EventBus;
import com.soarclient.event.client.ClientTickEvent;
import com.soarclient.event.impl.Render3DEvent;
import com.soarclient.management.config.ConfigType;
import com.soarclient.management.mod.Mod;
import com.soarclient.management.mod.ModCategory;
import com.soarclient.management.mod.settings.impl.BooleanSetting;
import com.soarclient.management.mod.settings.impl.KeybindSetting;
import com.soarclient.management.mod.settings.impl.NumberSetting;
import com.soarclient.skia.Skia;
import com.soarclient.skia.font.Icon;
import com.soarclient.utils.RotationUtil;
import com.soarclient.utils.SilentRotationHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * KillAura 模块 - 自动攻击附近的实体
 */
public class KillAuraMod extends Mod {

    private KeybindSetting keybindSetting = new KeybindSetting("setting.keybind", "setting.keybind.description",
        Icon.KEYBOARD, this, InputUtil.fromKeyCode(GLFW.GLFW_KEY_R, 0));

    private NumberSetting rangeSetting = new NumberSetting("setting.range", "setting.range.description",
        Icon.ARROW_RANGE, this, 4.5f, 3.0f, 6.0f, 0.1f);

    private NumberSetting cpsSetting = new NumberSetting("setting.cps", "setting.cps.description",
        Icon.MOUSE, this, 12.0f, 1.0f, 20.0f, 1.0f);

    private NumberSetting fovSetting = new NumberSetting("setting.fov", "setting.fov.description",
        Icon.VISIBILITY, this, 180.0f, 1.0f, 180.0f, 1.0f);

    private BooleanSetting playersSetting = new BooleanSetting("setting.players", "setting.players.description",
        Icon.PERSON, this, true);

    private BooleanSetting mobsSetting = new BooleanSetting("setting.mobs", "setting.mobs.description",
        Icon.PETS, this, true);

    private BooleanSetting animalsSetting = new BooleanSetting("setting.animals", "setting.animals.description",
        Icon.PET_SUPPLIES, this, false);

    private BooleanSetting autoRotateSetting = new BooleanSetting("setting.autorotate", "setting.autorotate.description",
        Icon.ROTATE_RIGHT, this, true);

    private BooleanSetting visibleOnlySetting = new BooleanSetting("setting.visibleonly", "setting.visibleonly.description",
        Icon.VISIBILITY, this, true);

    private BooleanSetting renderTargetSetting = new BooleanSetting("setting.rendertarget", "setting.rendertarget.description",
        Icon.RECTANGLE, this, true);

    private BooleanSetting silentRotationSetting = new BooleanSetting("setting.silentrotation", "setting.silentrotation.description",
        Icon.ROTATE_RIGHT, this, true);

    private NumberSetting smoothSpeedSetting = new NumberSetting("setting.smoothspeed", "setting.smoothspeed.description",
        Icon.SPEED, this, 10.0f, 1.0f, 30.0f, 1.0f);

    private BooleanSetting fakeSwingSetting = new BooleanSetting("setting.fakeswing", "setting.fakeswing.description",
        Icon.GESTURE, this, true);

    private NumberSetting swingRangeSetting = new NumberSetting("setting.swingrange", "setting.swingrange.description",
        Icon.ARROW_RANGE, this, 5.5f, 3.0f, 8.0f, 0.1f);

    private Entity currentTarget;
    private long lastAttackTime;
    private RotationUtil.Rotation targetRotation;
    private SilentRotationHandler rotationHandler;

    public KillAuraMod() {
        super("mod.killaura.name", "mod.killaura.description", Icon.SWORDS, ModCategory.PLAYER);
        this.rotationHandler = SilentRotationHandler.getInstance();
    }

    public final EventBus.EventListener<ClientTickEvent> onTick = event -> {
        // 处理按键绑定切换
        if (keybindSetting.isPressed()) {
            this.toggle();
            Soar.getInstance().getConfigManager().save(ConfigType.MOD);
            return;
        }

        // 如果模块未启用，不执行后续逻辑
        if (!this.isEnabled()) return;
        if (client.player == null || client.world == null) return;

        // 更新静默旋转处理器
        rotationHandler.onUpdate();

        updateTarget();

        if (currentTarget != null && shouldAttack()) {
            performAttack();
        }
    };

    public final EventBus.EventListener<Render3DEvent> onRender3D = event -> {
        if (!renderTargetSetting.isEnabled() || currentTarget == null) return;

        renderTargetBox(currentTarget, event.getPartialTicks());
    };

    private void updateTarget() {
        if (client.player == null || client.world == null) {
            currentTarget = null;
            return;
        }

        double range = rangeSetting.getValue();
        double fov = fovSetting.getValue();

        List<Entity> entities = new java.util.ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            entities.add(entity);
        }

        List<Entity> validTargets = entities.stream()
            .filter(this::isValidEntity)
            .filter(entity -> client.player.squaredDistanceTo(entity) <= range * range)
            .filter(entity -> isInFov(entity, fov))
            .filter(entity -> !visibleOnlySetting.isEnabled() || RotationUtil.canEntityBeSeen(entity))
            .sorted(Comparator.comparingDouble(entity -> client.player.squaredDistanceTo((Entity) entity)))
            .collect(Collectors.toList());

        if (validTargets.isEmpty()) {
            currentTarget = null;
            targetRotation = null;
        } else {
            currentTarget = validTargets.get(0);
            if (autoRotateSetting.isEnabled()) {
                targetRotation = RotationUtil.getRotationToEntity(currentTarget);
            }
        }
    }

    private boolean isValidEntity(Entity entity) {
        if (entity == client.player) return false;
        if (!(entity instanceof LivingEntity)) return false;
        if (((LivingEntity) entity).isDead()) return false;
        if (entity.isSpectator()) return false;

        if (entity instanceof PlayerEntity) {
            return playersSetting.isEnabled();
        } else if (entity instanceof HostileEntity) {
            return mobsSetting.isEnabled();
        } else if (entity instanceof PassiveEntity) {
            return animalsSetting.isEnabled();
        }

        return false;
    }

    private boolean isInFov(Entity entity, double fov) {
        if (fov >= 180.0) return true;

        Vec3d lookVec = RotationUtil.getLook(client.player.getYaw(), client.player.getPitch());
        Vec3d toEntity = entity.getPos().subtract(client.player.getEyePos()).normalize();

        double angle = Math.acos(lookVec.dotProduct(toEntity)) * (180.0 / Math.PI);
        return angle <= fov / 2.0;
    }

    private boolean shouldAttack() {
        long currentTime = System.currentTimeMillis();
        long delay = (long) (1000.0 / cpsSetting.getValue());

        if (currentTime - lastAttackTime < delay) {
            return false;
        }

        if (currentTarget == null) return false;
        if (client.player.isUsingItem()) return false;
        if (client.interactionManager == null) return false;

        return true;
    }

    private void performAttack() {
        if (currentTarget == null || client.player == null || client.interactionManager == null) return;

        if (autoRotateSetting.isEnabled() && targetRotation != null) {
            // 使用静默旋转系统
            if (silentRotationSetting.isEnabled()) {
                rotationHandler.setSmoothSpeed(smoothSpeedSetting.getValue());
                rotationHandler.setTargetRotation(
                    targetRotation.getYaw(), 
                    targetRotation.getPitch(), 
                    true,  // 静默模式
                    3      // 持续3个tick
                );
            } else {
                // 传统旋转方式
                RotationUtil.faceRotations(targetRotation);
            }
        }

        // 计算与目标的距离
        double distance = client.player.squaredDistanceTo(currentTarget);
        double attackRange = rangeSetting.getValue();
        double swingRange = swingRangeSetting.getValue();

        // 保存当前疾跑状态
        boolean wasSprinting = client.player.isSprinting();

        // 如果在攻击范围内，执行真实攻击
        if (distance <= attackRange * attackRange) {
            client.interactionManager.attackEntity(client.player, currentTarget);
            client.player.swingHand(Hand.MAIN_HAND);
            
            // 恢复疾跑状态
            if (wasSprinting && !client.player.isSprinting()) {
                client.player.setSprinting(true);
            }
        } 
        // 如果在挥动范围内但超出攻击范围，且启用了假挥动，则只发送swing
        else if (fakeSwingSetting.isEnabled() && distance <= swingRange * swingRange) {
            client.player.swingHand(Hand.MAIN_HAND);
        }

        lastAttackTime = System.currentTimeMillis();
    }

    private void renderTargetBox(Entity entity, float partialTicks) {
        if (entity == null || client.cameraEntity == null) return;

        Box box = entity.getBoundingBox();

        double x = box.minX + (box.maxX - box.minX) / 2.0;
        double y = box.minY;
        double z = box.minZ + (box.maxZ - box.minZ) / 2.0;

        double dx = x - client.cameraEntity.lastRenderX + (x - client.cameraEntity.getX()) * (partialTicks - 1.0);
        double dy = y - client.cameraEntity.lastRenderY + (y - client.cameraEntity.getY()) * (partialTicks - 1.0);
        double dz = z - client.cameraEntity.lastRenderZ + (z - client.cameraEntity.getZ()) * (partialTicks - 1.0);

        float width = (float) (box.maxX - box.minX);
        float height = (float) (box.maxY - box.minY);

        Skia.save();
        Skia.translate((float) dx, (float) dy);

        Color color = new Color(255, 50, 50, 100);
        Skia.drawOutline(0, 0, width, height, 0, 2, color);

        Skia.restore();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        currentTarget = null;
        targetRotation = null;
        rotationHandler.stop();
    }

    public Entity getCurrentTarget() {
        return currentTarget;
    }
}
