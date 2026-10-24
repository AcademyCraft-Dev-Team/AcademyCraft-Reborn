# 不平整地形抬升与地形暂停隔离回归

本文件接续 `sublevel_embedded_lift_repair_2026_10_20.zh-CN.md`。上一轮规则密合坑壁通过不能替代本轮不平整地形和多结构场景验收。

## 已定位的独立阻断

用户最新 `run/logs/latest.log` 中，00:13:00 出现 `UNLOADED:SectionKey[x=0,y=5,z=-1]`；00:14:45 至 00:15:40 持续出现 `BUDGET:SectionKey[x=3,y=4,z=-5]`，缓存 `sections=64`、`skipped` 持续增长。旧实现的 `stepCovered(..., false)` 不推进任何结构，期间质量 54、122、46 的结构仍被 H 发射入口接受并记录速度 50。

因此，必须把“地形覆盖不足冻结所有结构”与“有完整地形覆盖但不对称接触使 H 倾斜卡位”分别验证。前者不能证明后者已经修复，也不能通过增加 H 力量消除。

## 不平整地形夹具

`SubLevelLiftTiersSmoke` 保留真实 H 按下、蓄力、松开、3/5/7 格抬升、连续 200 tick 悬浮及显式发射流程。参数：

| Gradle 属性 | 默认 | 用途 |
| --- | --- | --- |
| `academySubLevelLiftEmbedded` | `false` | 设为 `true` 构造有完整地下体积与紧贴坑壁的土体 |
| `academySubLevelLiftTerrain` | `flat` | `flat`、`slope`、`step`、`corner`、`roof` |
| `academySubLevelLiftTier` | `-1` | `-1` 跑三档；`0/1/2` 只跑指定档 |
| `academySubLevelLiftDisturbance` | `false` | 首次观察到 capture ready 时，一次性增加 X 速度 0.2 格/秒及 Z 角速度 0.05 弧度/秒 |

水平夹具范围为中心 ±10 格，底为相对 y=-8。各列的表面高度为：

| 模式 | 表面高度 | 覆盖目的 |
| --- | --- | --- |
| `flat` | 0 | 上一轮规则土体基线 |
| `slope` | `floor((x+4)/2)-2` | 逐两格升一级，偏心坡面 |
| `step` | x>0 时 2，否则 0 | 两格台阶，不对称上部质量和接触 |
| `corner` | x>0 且 z>0 时 2，否则 0 | 四分之一高台，两个方向的凹角 |
| `roof` | 0；部分列 y=2 放基岩 | 隔一格空气的不可捕获硬顶盖碰撞负例 |

普通顶盖位于 H 上圆柱选区内时也会被捕获，不能作为“遗留硬顶盖”负例；`roof` 使用不可捕获基岩，确认它实际与抬升路径相交。该模式观察 160 tick，要求物体先走过空气间隙、保留硬顶盖、不穿过硬顶盖。它不要求被硬顶盖真实挡住的结构完成抬升。

新增日志：

- `SUBLEVEL_LIFT_ADOPT`：业务接管时的源格原点、真实本地 COM、源格 COM、实际 COM、差值、初始位姿、线速度、角速度。仅烟雾测试属性开启时输出。
- `SUBLEVEL_LIFT_INITIAL`：验证线程首次读到结构时的同组数据及地形覆盖状态。
- `SUBLEVEL_EMBEDDED_TERRAIN`：源位、+0.1、+1.01、目标高度的实际地形重叠数；正例要求均为空，负例要求 +1.01 有硬挡块。
- `SUBLEVEL_LIFT357_PROGRESS`：实际位置、旋转、速度、`terrain_covered`、水平漂移与最大角速度。
- `SUBLEVEL_LIFT_DISTURBANCE`：可选扰动的施加记录；随后不再施加测试控制，保留正常 H 租约。正常网格零速度出发不等价于抵抗初始接触扰动。

本地单档台阶命令（须先在隔离目录准备名为 `test` 的世界）：

```powershell
.\gradlew.bat runClientDev -DisDev=true `
  -I validation/native_structure/sublevel_lift_tiers.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-uneven-step-r1 `
  -PacademySubLevelLiftEmbedded=true `
  -PacademySubLevelLiftTerrain=step `
  -PacademySubLevelLiftTier=0
```

Sable 使用同一夹具，改为 `runClientSable`，并提供：

```text
-PacademySableRuntime=sable
-PacademySableRunDirectory=run/sable-compat/uneven-step-r1
-PacademySubLevelLiftRun=clientSable
-PacademySubLevelSmokeDirectory=run/sable-compat/uneven-step-r1/native-sublevel-direct-smoke-uneven-step-r1
```

## 真实 test2 的暂停隔离

`SubLevelTerrainIsolationSmoke` 只允许 `run/native-sublevel-terrain-isolation-*` 隔离目录。它不创建或清空地形，不直接调用 `runtime.tick` 或 native step。

正常服务端暖机 100 tick 后，从已有结构选择远处暂停结构及玩家 96 格内的可动结构。后者需通过精确 OBB 对原地形的当前位置、+0.5、+1 格穿插检查。测试连续 3 tick 给予向上 8 格/秒和 Y 轴 1 弧度/秒，随后让正常服务端推进，观察至少 80 tick：

- 暂停结构的 native 位姿及发布位姿不变，可读运动快照仍存在且位姿相同，对外线速度和角速度为零。
- 暂停结构的 direct-control probe 为 `UNAVAILABLE`，`moveTo`、`thrust`、`setVelocity`、`impulse` 返回拒绝。
- 附近可动结构没有一起暂停，真实上升至少 0.5 格且真实转角超过 0.02 弧度。
- 输出结构总数、暂停数、选中结构、位移、转角和地形缓存统计；失败和完成都会自动退出隔离客户端。

```powershell
.\gradlew.bat runClientDev -DisDev=true `
  -I validation/native_structure/sublevel_terrain_isolation.init.gradle `
  -PacademySubLevelTerrainIsolationDirectory=run/native-sublevel-terrain-isolation-test2-r1 `
  -PacademySubLevelTerrainIsolationWorld=test
```

测试世界名属性默认为 `test2`；上面的实际副本保存为 `test`，所以显式覆盖。世界源文件哈希由执行方保存在隔离目录的 `source-world-sha256.json`。

## 姿态控制调查与待验证项

当前公开 `StructurePhysicsControl` 只有单点 `Force` 和 `Anchor`，不会约束旋转；COM 施力自身不产生净转矩，但不对称的碰撞接触仍可能转动结构。H 的朝向稳定是否需要修复，必须依据上述正例在完整地形覆盖条件下的实际旋转与漂移判断。

如果证实需要姿态稳定，可在公开控制协议中增加有界姿态锚点，保留现有互斥控制租约、生命周期和发射后自由运动契约。两个后端均有读取真实惯量的基础：本地 `PhysicsBodyState` 提供主惯量和惯量本地坐标系，Sable 的 `MassData` 提供本地惯量矩阵，`RigidBodyHandle` 提供扭矩冲量。该方案尚未接入业务，不能把它记为已完成修复。

已通过：

- `build/uneven-step-r1.log`：本地 `step`、tier0、128 格，接管时源 COM 与实际 COM 完全相同，初始 identity 朝向、零速度。地形四个高度检查全部零重叠，抬升 3.064378 格并连续悬浮 200 tick，水平偏移和最大角速度均为零，最终 `SMOKE_PASSED`。此轮未施加扰动，不能据此排除用户的偏移卡位。

UNVERIFIED：

- 本轮坡面、凹角、顶盖、非零初始扰动，以及 Sable 的实际客户端结果。
- test2 真实多结构的连续 80 tick 暂停隔离结果。
- 不同材质与偏心几何下是否需要额外姿态控制。
- 所有验证结果须由执行日志回填，不能仅以源码或编译成功认定通过。
