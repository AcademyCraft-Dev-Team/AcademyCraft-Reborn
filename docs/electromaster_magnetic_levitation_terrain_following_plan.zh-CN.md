# 磁悬浮地形跟随与支撑参照优化方案

针对「高低不平表面悬浮前进时经常直接坠落」的问题，本文先给出根因，再给出可实施的设计、参数与验证计划。本轮仅产出方案，未修改生产代码。

## 1. 结论

当前悬浮实际只做两件事：

1. **布尔准入**：`MagneticSupportQuery.supported()` 判断「半径 X 内任意方向是否存在碰撞形状」。
2. **输入速度**：`MagneticMovement.flightDirection()` 加 `MagneticMovement.approach()`（0.08/tick 平滑）。

也就是说，**悬浮路径上没有任何高度语义，也没有地形跟随**。`MagneticMovement` 中为「锚点／离地高度／贴面跟随」准备的 `anchorPosition`、`hoverVelocity`、`adjustHoverHeight` 以及 `DEFAULT_HOVER_HEIGHT`、`MIN_HOVER_HEIGHT`、`MAX_HOVER_HEIGHT`、`GROUND_REACH` 在 `src/main` 中**零调用**，仅被 `MagneticMovementTest` 覆盖。方案阶段设计的运动学层没有接到实际悬浮路径上。

用户直觉中的「选取的悬浮参照节点」是准确的：参照是一个**无方向、无高度、单点缓存**的最近方块，因此参照可能落在身侧墙或头顶天花板，脚下完全没有基准。

## 2. 具体缺陷

### D1 参照无方向、无高度 → 无法地形跟随（根本原因）

`MagneticSupportQuery.supported()` 只返回 `boolean`，且首个命中即返回；`cached` 仅记录「最近一次命中的方块」。当地面／墙／天花板都在半径内时，参照可能是侧向方块：

- 高度不受任何约束，脚部相对地面的间隙完全由输入速度间接决定；
- 地面升高时没有向上修正，会被支撑包络「顶穿」后判定失败；
- 地面下降时支撑距离持续增大，直到越界才由硬布尔发现。

### D2 硬布尔 + 4 tick 锁定 + 立即归还重力 → 瞬断即坠

`MagneticFieldRuntime.java:91` 一旦判定失败：

```java
if (unsupportedTicks > 0) { unsupportedTicks--; return; }
if (!movement.supported(player, radius) || ...) {
    hovering = false;
    GravityControl.set(player, MagneticFieldEffects.SOURCE, false);
    ...
    unsupportedTicks = 4;
    return;
}
```

- 失败后**连续 4 tick 完全跳过输入处理与速度写入**；
- `:94` 立即归还重力租约，真实重力恢复；
- 这 4 tick 内 `setDeltaMovement` 不再被覆盖，`deltaMovement.y` 自由累积。

量化：4 tick 自由落体后 `deltaMovement.y ≈ -0.31`；恢复时 `approach` 每 tick 最多改变 0.08，需约 4 tick 才能抵消，期间继续下沉。合计约 0.2 秒失控加约 1 格额外下坠——即观察到的「直接坠落」。0.08 是为手感平滑设定的速率，但它同时是**抗扰动的恢复率**，在失配场景下偏低。

### D3 两处支撑检查几何不一致，逐轴回退会吞掉前进分量

准入检查用 `player.getBoundingBox()`（`MagneticFieldRuntime.java:92`），运动检查用 `bounds.move(desired)`（`MagneticLevitation.java:24`）。两者可以不一致：准入通过而前瞻失败时走逐轴回退（`MagneticLevitation.java:27-29`），回退只保留能单独通过支撑的轴，可能只保留 x、丢掉 y，或全部不通过而返回 `Vec3.ZERO`。

此时 `MagneticFieldRuntime.java:108-110` 仍然 `setDeltaMovement(partial / ZERO)` 并把重力租约置真，于是前进速度被静默吞掉，且下一 tick 可能转入 D2 的坠落分支。这是「前进时」出现异常的直接触发点。

### D4 最坏 O(reach³)，且最坏情况恰是失败（坠落）情况

`MagneticSupportQuery.java:16` 令 `reach = ceil(radius + maxAABB)`：

| 熟练度 | supportRadius | reach | 候选位置 |
| --- | ---: | ---: | ---: |
| 0 | 4 | 6 | 13³ ≈ 2 197 |
| 3000 | 16 | 18 | 37³ ≈ 50 653 |

逐壳展开在近壳命中时很快返回，但**完全无支撑（正是坠落／越界场景）必须扫完整个立方体**。方案 §4.3 明确禁止「每玩家每 tick 固定扫描 33³」。

### D5 缓存无方块更新失效

`MagneticSupportQuery.java:37-38` 的注释说明失败不应驱逐缓存，但没有任何方块破坏／放置的失效路径，全仓库也没有 `BlockEvent.BreakEvent` 订阅先例。后果是支撑被挖掉后最多延迟一拍才反映，并且旧参照与新参照可在 `radius` 边界上交替命中造成抖动。

## 3. 设计目标

| 编号 | 目标 |
| --- | --- |
| G1 | 不平表面水平移动时不得出现可见坠落或高度抖动 |
| G2 | 支撑参照必须携带方向与高度语义，并提供「脚下地面参照」 |
| G3 | 短暂失去支撑要降级，而不是断崖式失效 |
| G4 | 单 tick 成本有硬上限，不随半径立方增长 |
| G5 | 保持通用：AI、程序、非玩家实体可复用，无玩家 UUID／客户端输入依赖 |
| G6 | 不引入无限制创造飞行，保留「离开场即失去悬浮」的约束 |

## 4. 方案

> 实施结果与偏差记录见 §10。以下保留原始设计，便于对照。

### 4.1 把布尔查询换成「分层参照」

新增公开记录（`api.server.ability.electromaster`）：

```java
public record SupportReference(BlockPos pos, Vec3 closestPoint, Direction face, double distance) {}
```

`MagneticSupportQuery` 提供三个入口：

```java
// 兼容现有调用与 GameTest：任意方向最近支撑是否存在
public boolean supported(ServerLevel level, AABB bounds, double radius);

// 场包络参照：任意方向最近支撑，用于准入与边界约束
public @Nullable SupportReference nearest(ServerLevel level, AABB bounds, double radius);

// 地面参照：脚下柱状范围内最近的朝天支撑，用于高度控制
public @Nullable SupportReference groundBelow(ServerLevel level, AABB bounds, double radius);
```

- `nearest` 保留「任意方向」语义（地面、墙壁、天花板、独立方块均计为支撑），符合方案 §4.3 的支撑定义；
- `groundBelow` 限定为包围盒 XZ 外扩一个容差半宽、且碰撞面法线朝上的最近支撑，是**地形跟随唯一的基准来源**；
- 墙壁与天花板只参与准入与边界约束，**不参与高度控制**——这是与现状最本质的区别（解决 D1）。

### 4.2 高度模型：间隙量，而非固定吸附点

不采用固定吸附点（方案 §4.3 明确不强制吸附），也不恢复旧 0.5–4 格固定离地上限。改为间隙量模型：

- 定义 `clearance` = 脚部 Y − `groundBelow` 顶面 Y（基准做平滑）；
- 垂直输入驱动 `clearance` 的**变化率**（限速），而非直接设速度：跳跃增、潜行减；
- **只约束下限，不封顶上限**：下限 `MIN_CLEARANCE`（约 0.3）保证不被地面顶穿；上限放开，由 `supportRadius` 自然限制（升太高就离开场，属玩家选择）；
- 无垂直输入时 `clearance` 以有限速率回归默认间隙 `DEFAULT_CLEARANCE`（约 1.5）。

垂直速度取 `clamp(clearanceError * followGain, -maxFollow, maxFollow)` 再叠加输入变化率并二次限速。关键点是方向不对称：地面**升高**时立即产生向上修正（防顶穿）；地面**下降**时以受限速率缓慢下降（防被甩下去），而不是维持绝对高度直到越界。

### 4.3 参照黏滞与迟滞（消除抖动）

- 每个移动者维护**黏滞参照**（`nearest` 与 `groundBelow` 各一），优先复用；
- 复用条件带迟滞：`distance <= radius * SUPPORT_HYSTERESIS` 时继续复用，避免参照在 `radius` 边界上两方块交替命中产生逐 tick 抖动；
- 仅在参照被破坏或超出迟滞范围时才重新搜索；
- 每 tick 重新读取黏滞方块的当前 `BlockState` 即可立刻反映破坏／放置，因此无需引入 `BlockEvent` 订阅，也避免为多移动者维护监听器生命周期（同时满足 G5）。

### 4.4 有界搜索（替换整立方体扫描）

按代价从低到高分级，命中即停：

1. **黏滞参照复用**（O(1)），预计覆盖绝大多数帧；
2. **地面柱状探针**（`groundBelow`）：在包围盒 XZ 容差内自脚部向下逐层探测，最多 `ceil(radius)` 层，采样量约 `3 × 3 × radius`（radius = 16 时约 144）；
3. **场包络近壳搜索**（`nearest`）：自壳 0 向外展开，但受硬预算 `MAX_SUPPORT_SAMPLES` 约束，预算耗尽即返回当前最优（可为 null）。同时收紧 `reach`：由 `ceil(radius + maxAABB)` 改为 `ceil(radius + 半对角线)`，减少无效外圈。

由此成本有上限，且与成功／失败解耦（解决 D4）。

### 4.5 运动解算顺序（消除 D3）

改为「先定基准，再算位移，再裁边界」：

1. 解析 `nearest` 与 `groundBelow`（黏滞优先）；
2. 若 `nearest == null`，进入 §4.6 降级流程，不进入常规解算；
3. 水平分量由 `flightDirection` 给出（不变）；
4. 垂直分量由 §4.2 的间隙量模型给出；
5. 合成 `desired = approach(deltaMovement, direction * speed)`，保留 0.08 手感平滑；
6. 边界约束改为**带余量**的逐轴裁剪：接受某轴的条件是「移动后 `nearest` 仍存在且 `distance <= radius - SUPPORT_MARGIN`」。与现状的区别在于用距离余量而非布尔翻转，避免恰好压在 `radius` 上时反复通过／失败。

### 4.6 失去支撑：降级而非断崖（消除 D2）

把 `unsupportedTicks` 的「4 tick 全面锁定」改为**降级窗口**：

- 窗口长度 `SUPPORT_GRACE_TICKS`（建议 6）；
- 窗口内**保留重力租约**、保留水平操控（权限可降低，如 ×0.6）、施加受限下沉速度 `SINK_SPEED`，并持续尝试重新获取支撑；观感是「正在滑出场」，而不是突然自由落体，玩家有机会飞回包络内；
- 窗口耗尽才 `GravityControl.set(SOURCE, false)` 真正交还重力，按正常摔落处理；
- **仅在有效悬浮期间清理 `fallDistance`**，维持方案 §4.3 语义；
- 全程不跳过输入处理；悬浮暂停期间本就不收持续成本，维持现有计费语义。

### 4.7 状态与可观测性

在 `MAGNETIC_LEVITATION_REQUESTED` / `MAGNETIC_LEVITATION_ACTIVE` 之外增加降级态，HUD 新增「正在失去支撑」文案，与现有 `hud.academy.magnetic_field.hovering`、`hud.academy.magnetic_field.no_support` 并列。并提供一个仅开发可见的诊断输出（当前参照坐标、距离、`clearance`），便于按实测反馈调参。

## 5. 参数建议

数值落到 `AbilityConfig.ElectromasterSettings`（`src/main/java/org/academy/internal/server/config/AbilityConfig.java`），与 `paralysis*` 同级，便于服务端调整。

| 参数 | 建议值 | 说明 |
| --- | --- | --- |
| `MIN_CLEARANCE` | 0.3 | clearance 下限，防顶穿 |
| `DEFAULT_CLEARANCE` | 1.5 | 无输入时回归的目标间隙 |
| `CLEARANCE_RATE` | 0.12/tick | 垂直输入改变 clearance 的速率 |
| `FOLLOW_GAIN` | 0.35 | clearance 误差 → 垂直速度 |
| `MAX_FOLLOW_SPEED` | 0.45/tick | 地形跟随上行速度上限 |
| `MAX_DESCENT_SPEED` | 0.30/tick | 地面下降时自动下降上限 |
| `SUPPORT_GRACE_TICKS` | 6 | 降级窗口长度 |
| `SINK_SPEED` | 0.25/tick | 降级窗口内下沉速度 |
| `SUPPORT_HYSTERESIS` | 1.15 | 黏滞参照复用倍率 |
| `SUPPORT_MARGIN` | 0.25 | 边界裁剪距离余量 |
| `MAX_SUPPORT_SAMPLES` | 512 | 单 tick 硬采样上限 |

`MagneticFieldTuning.supportRadius` 的 4／6／8／16 与 `FLIGHT_SPEED_PER_TICK` 的 1.8 保持不变，避免改动已验收数值。

## 6. 兼容与影响面

- `MagneticSupportQuery.supported(...)` 与 `MagneticLevitation.supported/velocity(...)` 是公开 API，被 `ElectromasterReworkGameTests` 与 `ShieldAndMagneticRegressionGameTests` 直接调用，**保留签名作为薄适配层**，结构化能力以新增入口提供，现有 GameTest 断言（墙／天花板支持、缓存失效、重力租约）继续成立。
- `SupportReference` 与三个查询入口不依赖 `ServerPlayer`、不依赖客户端输入，AI／程序／非玩家实体可直接复用（满足 G5 与仓库「为其他非玩家实体开放 API」的补充规范）。
- 手感回归风险：`approach` 的 0.08 平滑保留，但垂直方向改由间隙量模型驱动，需客户端实测确认贴地滑行不产生晕动。

## 7. 验证计划

**单元测试**（新增／扩展 `MagneticMovementTest`、`ElectromasterGeometryTest`）

- 参照选择：`nearest` 取最近；`groundBelow` 只接受朝天面；侧墙不作为高度参照（D1 回归）。
- 黏滞与迟滞：在 `radius` 边界往复微小移动时参照不翻转（抖动回归）。
- 间隙量模型：地面升高产生向上修正且 `clearance >= MIN_CLEARANCE`；地面下降速率 ≤ `MAX_DESCENT_SPEED`；无输入收敛到 `DEFAULT_CLEARANCE`；限速不越界。
- 边界裁剪：带余量的逐轴裁剪保留切向分量，不吞掉前进分量（D3 回归）。
- 降级窗口：窗口内不归还重力且持续尝试重获；仅窗口耗尽后归还。
- 预算：构造无支撑场景，断言采样计数 ≤ `MAX_SUPPORT_SAMPLES`（D4 回归）。

**GameTest**（扩展 `ElectromasterReworkGameTests.support`）

- 场景：平地 → 1 格台阶 → 2 格高差 → 楼梯 → 半砖 → 1 格沟壑 → 身侧墙 → 头顶天花板 → 脚下支撑被挖掉。
- 核心回归断言：**在支撑持续可用的整段位移中，任意 tick 的 `deltaMovement.y` 不得低于阈值**，即「持续可用支撑下不得出现坠落」。
- 释放语义：窗口耗尽后 `isNoGravity()` 恢复，且 `fallDistance` 正常累计。

**客户端实测**

平地／台阶／楼梯／墙顶／沟壑前进、跳跃与潜行升降、地形跟随观感、HUD 三态、联机观察者；按仓库规则保存截图或短录像。

## 8. 备选方案与被否原因

| 备选 | 被否原因 |
| --- | --- |
| A 固定吸附点（回到「吸附到某个面」） | 吸附点会被半径内最近方块频繁改写，且方案 §4.3 明确不强制吸附 |
| B 直接给玩家创造飞行 | 违反 G6，与仓库「不用无限制创造飞行替代支撑约束」冲突 |
| C 仅调大 `supportRadius` 掩盖坠落 | 同时放大 D4 的立方成本，且不解决「无高度参照」根因，只是推迟坠落 |
| D 引入 `BlockEvent` 订阅做缓存失效 | 全仓库无此先例，且为多移动者维护监听器生命周期复杂；每 tick 重读黏滞参照状态已足够，作为可选优化保留 |

## 9. 实施顺序

1. 结构化支撑参照 + 有界搜索 + 黏滞迟滞（D4／D5，纯查询层，可独立单测）；
2. 间隙量高度模型 + 运动解算顺序（D1／D3）；
3. 降级窗口替代 4 tick 锁定（D2）；
4. HUD 第三态与诊断输出；
5. 参数落到 `AbilityConfig.ElectromasterSettings`；
6. 单元测试 → GameTest → 客户端实测 → 按仓库规则征询是否创建本地提交。

其中第 2、3 步是消除用户所报「前进时直接坠落」的关键，第 1 步是消除抖动与性能风险的前置条件。

## 10. 实施结果（2026-09-15）

已按本方案实施，并在过程中按实测与测试反馈修正了三处设计。

### 10.1 与原始设计的偏差

| 项 | 原设计 | 实际实现 | 原因 |
| --- | --- | --- | --- |
| 场包络搜索 | 逐壳展开（shell）+ 采样预算兜底 | **近场穷举 + 26 条归一化射线步进（每条首次命中即停）** | 逐壳展开本质是 O(r³)。GameTest 实测 radius 6 时即因预算耗尽漏掉距离 4.2 的墙并误报「无支撑」——正是造成坠落的误判类型。改为近场（±2 格，125 个）穷举保证贴身接触不因角度间隙漏检，外圈用 26 条射线，与半径线性 |
| 采样预算 | 512 | **1152** | 近场 125 + 26 条射线 × 33 步 ≈ 983。512 会在最大半径（16 格）截断最外圈射线，重新引入误判 |
| 边界裁剪判据 | 用「黏滞参照 + 距离余量」在同一参照上比较 | **在目标位置重新探测是否存在支撑**（`probeAt`，无状态、不碰缓存） | 关键缺陷：在单个记忆参照上比较距离，会因为角色「远离了那个方块」而触发裁剪。GameTest 实测移动 3.75 格后前进被冻结，即「前进被吞掉」。改为询问「目标位置还有没有支撑」才是正确的场约束语义 |
| `supportMargin` 参数 | 有 | **删除** | 改为直探目标位置后不再需要，留着会变成误导性的死配置 |
| 参照迟滞 | 黏滞倍率 `radius × hysteresis` | **取消倍率：包络按 `radius` 硬校验后复用；地面参照每 tick 重新探测** | 1.15 倍迟滞会把超出有效半径的旧参照判为有效，与「准入必须确定」冲突。地面参照若缓存，走上更高地形时旧的低面仍被复用，间隙被读大、解法器会把角色压进新地面——恰是要修复的缺陷类型 |
| 失去支撑时的垂直操控 | 下沉速度 + 受限操控 | 下沉速度 + **保留完整上行权限** | 若上行也被按 0.6 比例削弱，玩家更难飞出包络恢复；保留上行权限才符合「可恢复」语义 |
| 消抖实现 | 依赖参照迟滞 | 依赖「目标位置重探 + 近场穷举」后的判据确定性 | 原抖动来自「布尔在 radius 边界翻转」，改为对目标位置直接判定后，边界反复通过／失败的前提消失 |

### 10.2 实际改动文件

| 文件 | 改动 |
| --- | --- |
| `api/server/ability/electromaster/SupportReference.java` | 新增。支撑参照记录，含 `isGround()` / `surfaceY()` |
| `api/server/ability/electromaster/MagneticSupportQuery.java` | 重写。分层查询、射线探测、地面柱状探测、`revalidate` 静态入口 |
| `api/server/ability/electromaster/MagneticLevitation.java` | 重写为有状态解法器：`step()` 一次解析参照并解算，`degradedVelocity()` 提供降级速度 |
| `api/common/ability/electromaster/LevitationTuning.java` | 新增。悬浮调参记录与逐字段校验 |
| `api/common/ability/electromaster/MagneticMovement.java` | 删除孤儿 `hoverVelocity` / `adjustHoverHeight` / `GROUND_REACH` 等，新增 `hoverVertical()`、`degradedVertical()`、`approachHorizontal()` |
| `api/server/ability/electromaster/MagneticFieldEffects.java` | 新增 `levitationTuning()` 解析服务端配置 |
| `internal/common/ability/electromaster/MagneticFieldRuntime.java` | 降级窗口取代 4 tick 锁定；每 tick 只探测一次 |
| `internal/common/attachment/AttachmentTypes.java` | 新增 `MAGNETIC_LEVITATION_DEGRADED` |
| `internal/server/config/AbilityConfig.java` | `ElectromasterSettings` 新增 11 项悬浮调参 |
| `internal/common/ability/electromaster/skills/lv3/MagnetManipulation.java` | HUD 三态显示 |
| `resources/assets/academy/lang/{en_us,zh_cn}.json` | 新增 `hud.academy.magnetic_field.losing_support` |
| 测试 | `MagneticMovementTest` 重写并新增地形跟随回归；`ElectromasterReworkTuningTest`、`ElectromasterGeometryTest` 扩充；`ElectromasterReworkGameTests.support` 拆为重力租约、地面参照、地形跟随三组 |

### 10.3 调参默认值（`AbilityConfig.ElectromasterSettings`）

| 参数 | 值 | 参数 | 值 |
| --- | ---: | --- | ---: |
| `levitationRestClearance` | 1.5 | `levitationGraceSpeedFactor` | 0.60 |
| `levitationInputClearanceOffset` | 4.0 | `levitationMaxSupportSamples` | 1152 |
| `levitationFollowGain` | 0.40 | `levitationGraceTicks` | 6 |
| `levitationMaxClimbSpeed` | 0.50 | | |
| `levitationMaxDescentSpeed` | 0.30 | | |
| `levitationInputVerticalSpeed` | 0.30 | | |
| `levitationSinkSpeed` | 0.25 | | |

校验约定：`followGain`、上行／下行／垂直输入速度、`inputClearanceOffset` 必须为正，否则回退默认——它们为零会让解法器完全失去高度修正能力，即本方案要消除的失效模式。`sinkSpeed` 允许为 0（降级时保持高度是合法选择）。`restClearance` 下限被钳制在 `MIN_CLEARANCE`（0.3）。

### 10.4 验证结果

- `test -DisDev=true`：**2118 项通过，0 失败**（较改动前的 2105 项增加 13 项）。
- `editorTest`：通过。
- 定向 GameTest 全部通过（9/9）：`electromaster_rework_{health,resources,attacks,projectiles,support}`、`shield_magnetic_{shield_paths,beam_probe,resistance_damage,magnetic_flight}`。
- `build -DisDev=true` 与 `build -DisDev=false`：均通过，含 Javadoc 与编辑器测试。
- 产物：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar`、`academy-26.2.0-0.0.4-alpha-release.jar`。

### 10.5 实施中发现并修复的问题

按发现顺序，全部由 GameTest 捕获：

1. **逐壳展开的预算漏检**（改前实现）：radius 6 时预算耗尽，漏掉距离 4.2 的墙并误报无支撑（`Side wall supports upgraded hover on tick 0`）。这是坠落误判在查询层的同源问题，已由近场穷举 + 射线探测取代。
2. **地面参照缓存不安全**：见 §10.1。已改为每 tick 重新探测。
3. **旧的「缓存失效即返回无支撑」语义**：原实现失败后直接返回 `false` 并保留缓存，会使一次误判持续；现改为过期即重扫。
4. **轴回退注入固定速度**：逐轴回退时误用单位轴向量（±1.0）而非真实分量，会注入 1.0 格/tick 的速度（`Solver output must stay bounded: y=1.0`）。已改为携带真实分量。
5. **边界裁剪冻结前进**（最关键）：在单个黏滞参照上比较距离，角色远离该方块后即触发裁剪，实测移动 3.75 格后前进被吞（`Forward travel must not be swallowed`）。已改为在目标位置重新探测支撑。
6. **驱动的重复探测**：原准入与运动各探测一次；现由 `step()` 一次返回场状态与间隙，每 tick 仅探测一次。

其中第 5 项说明一个重要的设计结论：**场约束必须表述为「目标位置是否仍有支撑」，而不是「相对某个参照的距离是否仍在阈值内」**。后者把参照本身变成了约束中心，必然随移动而误裁。

### 10.6 第二轮：速度被钳到近乎为零

第一轮修好后现场反馈：**边界不再坠落，但水平与升降速度被限制到几乎为零，移动一步一卡**。这是一个与坠落不同、且更严重的手感缺陷。

**根因（两个独立缺陷叠加）：**

1. **边界裁剪把整轴分量清零。** 原实现按 x／y／z 三轴独立试算，只保留「单独也能通过」的轴。当角色贴着包络边缘时，三轴都可能单独失败，于是速度被清零；下 tick 位置略微变化又恢复，形成「满速 ↔ 零速」的逐 tick 振荡——即肉眼看到的卡死。方案 §4.3 原本要求的是「限制向外分量、保留切向移动」，而我第一轮实现成了「整轴丢弃」，属于实现偏离设计。
2. **升降目标间隙没有上限。** 上升就是远离地面支撑，而目标间隙由固定偏移给出、不受场半径约束。角色按住跳跃后会一直要求超过场半径的高度，于是每 tick 都撞上边界裁剪又被拉回，表现为升降几乎不动。

**修正：**

- **改为只移除向外分量。** 当目标位置仍在场内时整步接受；只有确实出场时，才沿「支撑点 → 自身」方向扣掉向外分量，保留全部切向速度。这与方案 §4.3 的原始表述一致，也是「沿边界飞行不掉速」的正确语义。
- **升降目标间隙按场半径设上限**（`radius - 1.0`，即 `CLEARANCE_REACH_MARGIN`）。达到上限后继续按住跳跃不再产生向上速度，而是稳定停住，不再与边界对抗。
- **准入与裁剪统一判据。** 我把地面柱状探测放在判据首位：脚下有地面即在场内。这是绝大多数情形，且与准入用的是同一个问题、同一次探测，两条路径不可能得出相反结论（第一轮正是因为准入用缓存参照、裁剪用从头探测，才出现「准入说在、裁剪说不在」）。
- `Step` 增加 `clamped` 标志，使「本步是否被裁剪」可被测试直接断言，不再只能靠速度间接推测。

**为什么第一轮的测试没抓到：** 第一轮 `terrainFollowingNeverFalls` 只断言「60 tick 内前进超过 6 格」。角色实际只走了约 4.3 格却仍然通过了该断言（阈值过低），而卡死本身就是「走得极慢」，所以断言形同虚设。现已改为：

- 起手就赋予满水平速度（缺陷是「丢失已达成的速度」，而非「起步慢」），逐 tick 断言水平速度保持 ≥ 90% 且 `clamped == false`；
- 新增 `ascentKeepsClimbingAndSettlesAtACeiling`：按住跳跃必须真实爬升、在 15 tick 内抵达上限，且抵达后 `clamped == false`、间隙连续 5 tick 稳定（±0.01）。

两处断言我都做了**反向验证**：临时还原旧实现（去掉间隙上限、或改回整轴丢弃）后对应测试确实失败，确认断言有实际约束力，而不是恒真。

### 10.7 关键设计结论（修订）

第一轮我写下的「场约束应表述为目标位置是否仍有支撑」这句结论不够准确，容易再次导致上述实现偏离。准确的表述是：

> **场约束应表述为「是否仍有支撑」，判据必须与准入完全一致；而越界时的处理是移除向外分量、保留切向分量，绝不可以把速度整体或整轴清零。**

三者的顺序很重要：判据统一解决「准入与裁剪互相打架」，移除向外分量解决「沿边界飞行掉速」，间隙上限解决「升降与边界对抗」。任一缺失都会重新产生「卡死」这一观感。

### 10.8 测试断言的取舍

初期写下的几条断言本身是错的，已在实现过程中修正，记录以免后人重犯：

- 「2 格瞬时抬升地形不得下沉」——2 格超过单 tick 可通过高度，物理上不可能，断言无意义。改为楼梯／半砖（0.5 格）序列 + 一条「过高抬升需在有限 tick 内回升且不得下行」的独立用例。
- 「地面平稳就不得下行」——当间隙大于静止高度时，下行是**正确的收敛行为**；只有「间隙不小于静止值时仍下行」才是缺陷。断言已收窄。
- 「海拔上升不得出现停顿」——抵达上限后的停住是**正确结果**，不是缺陷。改为断言「在 15 tick 内抵达上限」+「抵达后不触发裁剪且间隙稳定」，这才区分得开「正常停住」与「与边界逐 tick 对抗」。
- 最关键的一条教训：**断言必须有下界意义**。第一轮「前进超过 6 格」这类弱阈值，在缺陷存在时同样能通过，等于没有测试。凡涉及手感的断言，都应写成「保持一个与配置值接近的量」（如 ≥90% 满速），而不是「大于某个远低于正常值的小数」。

### 10.9 尚未执行

客户端实测（台阶／楼梯／墙顶／沟壑前进、跳跃与潜行升降、HUD 三态、联机观察者）按 §7 计划仍需在真实客户端进行；本次未启动客户端，因此手感（尤其地形跟随与沿边界飞行的观感）尚未经人眼确认。未创建 Git 提交。
