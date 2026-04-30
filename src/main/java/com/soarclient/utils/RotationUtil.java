package com.soarclient.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

/**
 * 旋转工具类 - 用于处理视角旋转和瞄准计算
 */
public class RotationUtil {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * 计算两个角度之间的差值
     */
    public static float getAngleDifference(float a, float b) {
        return ((a - b) % 360.0F + 540.0F) % 360.0F - 180.0F;
    }

    /**
     * 获取玩家当前的视角向量
     */
    public static Vec3d getLook() {
        return getLook(mc.player.getYaw(), mc.player.getPitch());
    }

    /**
     * 根据给定的yaw和pitch获取视角向量
     */
    public static Vec3d getLook(float yaw, float pitch) {
        float f = MathHelper.cos(-pitch * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-pitch * 0.017453292F - (float) Math.PI);
        float f2 = MathHelper.cos(-yaw * 0.017453292F);
        float f3 = MathHelper.sin(-yaw * 0.017453292F);
        return new Vec3d(f3 * f, f1, f2 * f);
    }

    /**
     * 计算从一个点到另一个点所需的旋转角度
     */
    public static Rotation getRotations(Vec3d eye, Vec3d target) {
        double x = target.x - eye.x;
        double y = target.y - eye.y;
        double z = target.z - eye.z;
        double diffXZ = Math.sqrt(x * x + z * z);
        float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.atan2(y, diffXZ)));
        return new Rotation(MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch));
    }

    /**
     * 计算看向指定方块位置所需的旋转角度
     */
    public static Rotation getRotations(BlockPos pos, float partialTicks) {
        if (mc.player == null) return null;

        Vec3d playerVector = new Vec3d(
            mc.player.getX() + mc.player.getVelocity().x * partialTicks,
            mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()) + mc.player.getVelocity().y * partialTicks,
            mc.player.getZ() + mc.player.getVelocity().z * partialTicks
        );
        double x = pos.getX() - playerVector.x + 0.5;
        double y = pos.getY() - playerVector.y + 0.5;
        double z = pos.getZ() - playerVector.z + 0.5;
        return diffCalc(x, y, z);
    }

    /**
     * 根据坐标差值计算旋转角度
     */
    public static Rotation diffCalc(double diffX, double diffY, double diffZ) {
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        return new Rotation(MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch));
    }

    /**
     * 获取实体中心点的旋转角度
     */
    public static Rotation getRotationToEntity(Entity entity) {
        if (mc.player == null || entity == null) return null;

        Vec3d eyesPos = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
        Vec3d entityPos = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ());
        return getRotations(eyesPos, entityPos);
    }

    /**
     * 获取实体眼睛高度位置的旋转角度
     */
    public static Rotation getRotationToEntityHead(Entity entity) {
        if (mc.player == null || entity == null) return null;

        Vec3d eyesPos = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
        Vec3d entityPos = new Vec3d(entity.getX(), entity.getY() + entity.getStandingEyeHeight(), entity.getZ());
        return getRotations(eyesPos, entityPos);
    }

    /**
     * 执行射线追踪检测
     */
    public static HitResult rayTrace(double range, float partialTicks, boolean hitFluids, float yaw, float pitch) {
        if (mc.player == null || mc.world == null) return null;

        Entity entity = mc.getCameraEntity();
        if (entity == null) return null;

        Vec3d eyePos = entity.getCameraPosVec(partialTicks);
        Vec3d lookVec = getLook(yaw, pitch);
        Vec3d traceEnd = eyePos.add(lookVec.x * range, lookVec.y * range, lookVec.z * range);

        RaycastContext.ShapeType blockBehaviour = hitFluids ?
            RaycastContext.ShapeType.COLLIDER : RaycastContext.ShapeType.OUTLINE;
        RaycastContext.FluidHandling fluidBehaviour = hitFluids ?
            RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE;

        RaycastContext context = new RaycastContext(eyePos, traceEnd, blockBehaviour, fluidBehaviour, entity);
        HitResult blockHitResult = mc.world.raycast(context);

        Vec3d hitVec = blockHitResult.getType() != HitResult.Type.MISS ?
            blockHitResult.getPos() : traceEnd;

        Box box = entity.getBoundingBox().stretch(lookVec.multiply(range)).expand(1.0, 1.0, 1.0);

        double minDistance = blockHitResult.getType() != HitResult.Type.MISS ?
            eyePos.squaredDistanceTo(blockHitResult.getPos()) : range * range;

        Entity pointedEntity = null;
        for (Entity e : mc.world.getOtherEntities(entity, box)) {
            if (e.canHit()) {
                Box entityBox = e.getBoundingBox().expand(e.getTargetingMargin());
                Optional<Vec3d> intercept = entityBox.raycast(eyePos, hitVec);
                if (intercept.isPresent()) {
                    double dist = eyePos.squaredDistanceTo(intercept.get());
                    if (dist < minDistance) {
                        pointedEntity = e;
                        hitVec = intercept.get();
                        minDistance = dist;
                    }
                }
            }
        }

        if (pointedEntity != null) {
            return new EntityHitResult(pointedEntity, hitVec);
        }

        return blockHitResult;
    }

    /**
     * 面向指定的旋转角度
     */
    public static void faceRotations(Rotation rotation) {
        if (rotation != null && mc.player != null) {
            mc.player.setYaw(rotation.getYaw());
            mc.player.setPitch(rotation.getPitch());
        }
    }

    /**
     * 面向指定的向量
     */
    public static void faceVector(Vec3d vec) {
        if (mc.player == null) return;
        Rotation rotation = getRotations(mc.player.getEyePos(), vec);
        faceRotations(rotation);
    }

    /**
     * 面向指定的位置
     */
    public static void faceBlock(BlockPos pos) {
        Rotation rotation = getRotations(pos, 1.0F);
        faceRotations(rotation);
    }

    /**
     * 面向指定的实体
     */
    public static void faceEntity(Entity entity) {
        Rotation rotation = getRotationToEntity(entity);
        faceRotations(rotation);
    }

    /**
     * 检查目标实体是否在视线范围内
     */
    public static boolean canEntityBeSeen(Entity entity) {
        if (mc.player == null || mc.world == null || entity == null) return false;

        Vec3d startVec = mc.player.getEyePos();
        Vec3d endVec = new Vec3d(entity.getX(), entity.getY() + entity.getStandingEyeHeight(), entity.getZ());

        BlockHitResult result = mc.world.raycast(new RaycastContext(
            startVec, endVec,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        return result.getType() == HitResult.Type.MISS;
    }

    /**
     * 检查指定位置是否可以被看到
     */
    public static boolean canBeSeen(Vec3d target) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d start = mc.player.getEyePos();

        BlockHitResult result = mc.world.raycast(new RaycastContext(
            start, target,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        return result.getType() == HitResult.Type.MISS;
    }

    /**
     * 平滑地转向目标旋转角度
     */
    public static Rotation smoothRotation(Rotation current, Rotation target, float speed) {
        if (current == null || target == null) return target;

        float yawDiff = getAngleDifference(target.getYaw(), current.getYaw());
        float pitchDiff = getAngleDifference(target.getPitch(), current.getPitch());

        float newYaw = current.getYaw() + MathHelper.clamp(yawDiff, -speed, speed);
        float newPitch = current.getPitch() + MathHelper.clamp(pitchDiff, -speed, speed);

        return new Rotation(newYaw, newPitch);
    }

    /**
     * 计算两点之间的距离
     */
    public static double getDistance(Vec3d pos1, Vec3d pos2) {
        return pos1.distanceTo(pos2);
    }

    /**
     * 旋转类，用于存储Yaw和Pitch值
     */
    public static class Rotation {
        private final float yaw;
        private final float pitch;

        public Rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        @Override
        public String toString() {
            return "Rotation{yaw=" + yaw + ", pitch=" + pitch + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Rotation rotation = (Rotation) obj;
            return Float.compare(rotation.yaw, yaw) == 0 && Float.compare(rotation.pitch, pitch) == 0;
        }
    }
}
