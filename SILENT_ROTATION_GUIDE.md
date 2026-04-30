# Silent Rotation（静默旋转）系统

## 📖 概述

Silent Rotation 是一个高级旋转控制系统，它将**客户端视觉旋转**与**服务器数据包旋转**分离，从而实现更隐蔽的自动瞄准功能。

## 🎯 核心原理

### 双层旋转架构

```
┌─────────────────────────────────────┐
│     客户端旋转 (Client Rotation)      │
│   - 玩家实际看到的视角                │
│   - 用于渲染和视觉效果                │
│   - 可以平滑过渡                      │
└─────────────────────────────────────┘
              ↕ (可能不同步)
┌─────────────────────────────────────┐
│     服务器旋转 (Server Rotation)      │
│   - 发送给服务器的视角                │
│   - 影响实际的游戏逻辑                │
│   - 可以被控制/延迟                   │
└─────────────────────────────────────┘
```

### 工作流程

1. **目标检测** → KillAura 找到最近的敌人
2. **计算旋转** → 计算需要转向的角度
3. **应用静默旋转** → 
   - 客户端：立即或平滑转向目标（视觉）
   - 服务器：延迟或分批次发送旋转数据
4. **执行攻击** → 在最佳时机攻击
5. **恢复状态** → 重置旋转到正常状态

## 🔧 主要组件

### 1. SilentRotationHandler.java

**位置**: `com.soarclient.utils.SilentRotationHandler`

**核心功能**:
- 管理客户端和服务器旋转状态
- 提供平滑旋转算法
- 控制旋转的持续时间
- 支持静默/非静默模式

**关键方法**:
```java
// 设置目标旋转
setTargetRotation(float yaw, float pitch, boolean silent, int ticks)

// 每tick更新
onUpdate()

// 停止旋转
stop()

// 获取服务器旋转角度
getServerYaw() / getServerPitch()

// 获取客户端旋转角度
getClientYaw() / getClientPitch()
```

### 2. RotationEvent.java

**位置**: `com.soarclient.event.client.RotationEvent`

**作用**: 在旋转发生时触发事件，允许其他模块拦截或修改旋转

**属性**:
- `yaw` / `pitch`: 目标旋转角度
- `previousYaw` / `previousPitch`: 之前的旋转角度
- `silent`: 是否为静默旋转
- `cancelled`: 是否取消此次旋转

### 3. KillAuraMod 集成

KillAura 现在支持两种旋转模式：

#### 传统模式（禁用静默旋转）
```java
RotationUtil.faceRotations(targetRotation);
```
- 直接设置玩家视角
- 客户端和服务器同步
- 容易被检测

#### 静默模式（启用静默旋转）
```java
rotationHandler.setTargetRotation(
    targetYaw, 
    targetPitch, 
    true,   // 静默模式
    3       // 持续3个tick
);
```
- 分离客户端和服务器旋转
- 平滑过渡
- 更难被检测

## ⚙️ 配置选项

### KillAura 新增设置

| 设置项 | 说明 | 默认值 | 范围 |
|--------|------|--------|------|
| **Silent Rotation** | 启用静默旋转 | ✅ 启用 | - |
| **Smooth Speed** | 旋转平滑速度 | 10.0 | 1.0 - 30.0 |

### 使用建议

**隐蔽性优先**（推荐）:
- Silent Rotation: ✅ 启用
- Smooth Speed: 8-12
- CPS: 10-14
- Range: 4.0-4.5

**性能优先**:
- Silent Rotation: ❌ 禁用
- Smooth Speed: 20-30
- CPS: 15-20
- Range: 5.0-6.0

## 💡 技术细节

### 平滑旋转算法

```java
// 计算角度差
float yawDiff = RotationUtil.getAngleDifference(targetYaw, currentYaw);

// 限制每tick的最大旋转角度
float maxYawChange = smoothSpeed;

// 应用限制
float newYaw = currentYaw + clamp(yawDiff, -maxYawChange, maxYawChange);
```

### 静默模式工作原理

1. **视觉层**（客户端）:
   ```java
   mc.player.setYaw(clientYaw);  // 玩家看到转向
   mc.player.setPitch(clientPitch);
   ```

2. **网络层**（服务器）:
   - 不立即发送 PlayerMoveC2SPacket
   - 或在移动包中使用不同的旋转值
   - 延迟同步真实角度

3. **时间窗口**:
   - 设置 `ticks` 参数控制持续时间
   - 过期后自动恢复到正常旋转

## 🎮 实际应用场景

### PvP 战斗

```
场景: 与另一名玩家对战

传统方式:
- 瞬间转向敌人 → 明显作弊痕迹
- 服务器立即收到大角度旋转

静默方式:
- 客户端平滑转向（视觉上自然）
- 服务器接收小幅度渐进旋转
- 攻击时精确对准
```

### 多目标切换

```
场景: 同时面对多个敌人

优势:
- 可以在视觉上快速切换目标
- 服务器看到的是合理的视角移动
- 减少可疑的大角度跳跃
```

## ⚠️ 注意事项

### 优点
✅ 更难被反作弊系统检测  
✅ 提供更自然的视觉效果  
✅ 可配置的平滑度  
✅ 与其他模块兼容  

### 局限性
⚠️ 不能完全避免检测  
⚠️ 在某些严格服务器上仍可能被标记  
⚠️ 需要合理配置参数  
⚠️ 网络延迟可能影响效果  

### 最佳实践

1. **不要过度使用**: 只在必要时启用
2. **调整参数**: 根据服务器调整 smooth speed
3. **结合其他功能**: 配合 FOV 限制、可见性检测
4. **监控效果**: 观察是否被踢出或警告

## 🔍 调试技巧

### 检查旋转状态

```java
SilentRotationHandler handler = SilentRotationHandler.getInstance();

// 检查是否活跃
if (handler.isActive()) {
    System.out.println("剩余ticks: " + handler.getTicksRemaining());
}

// 获取当前角度
System.out.println("客户端: " + handler.getClientYaw() + ", " + handler.getClientPitch());
System.out.println("服务器: " + handler.getServerYaw() + ", " + handler.getServerPitch());
```

### 测试不同配置

1. 在单人世界测试基本功能
2. 在多人测试服测试隐蔽性
3. 记录被检测的情况
4. 逐步调整参数

## 📚 扩展开发

### 添加新的旋转模式

```java
// 在 SilentRotationHandler 中添加
public enum RotationMode {
    INSTANT,      // 瞬时旋转
    SMOOTH,       // 平滑旋转
    HUMANIZED,    // 人类化旋转（随机波动）
    SERVER_SIDE   // 仅服务器端
}
```

### 集成到其他模块

任何需要自动旋转的模块都可以使用：

```java
// 在模块中
private SilentRotationHandler rotationHandler;

public void onEnable() {
    rotationHandler = SilentRotationHandler.getInstance();
}

// 使用时
rotationHandler.setTargetRotation(yaw, pitch, true, 5);
```

## 🎓 总结

Silent Rotation 系统通过分离客户端和服务器的旋转状态，提供了更高级的自动瞄准控制。它不是万能的，但合理使用可以显著提高隐蔽性和用户体验。

**记住**: 最好的作弊是看起来不像作弊的作弊。

---

*最后更新: 2026-04-29*  
*版本: 1.0.0*
