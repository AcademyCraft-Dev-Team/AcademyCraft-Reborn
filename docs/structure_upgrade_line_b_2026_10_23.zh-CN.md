# 开发线 B：控制算法与节点契约

> 本文保留 B 线第一阶段（独立算法）的验证记录。后续已完成真实后端及节点接线，当前能力、限制与验证请看 [B 线接线记录](structure_upgrade_line_b_live_2026_10_23.zh-CN.md)。下文“当前/未接线”均指第一阶段结束时，不表示后续实现状态。

本线基于本地提交 `26635388`（建立可替换物理服务骨架），实现 [FILL-SPEC](structure_upgrade_fill_spec.zh-CN.md) 中 B 线可独立推进的部分。新增算法已具有方法体，节点具有真实 schema、codec 与异步 VM 执行语义。**当前不切换现有 H/rotate，也不启用本地或 Sable 新控制后端。** 这不是 S1 游戏内验收通过；接线依赖和未验证项见下文。

## 已实现的控制内核

| 文件（`internal/common/structure/upgrade/control/`） | 实现与约束 |
| --- | --- |
| `TranslationSweep` | 固定姿态的 OBB–OBB 连续 SAT；逐真实碰撞盒检测整段平移，整体 AABB 仅用于候选过滤。允许贴面滑动和离开，不能穿越薄障碍，也不瞬移消除初始穿入。 |
| `ExtractionPlanner` | 源 COM 高度加指定抬升量；当前位姿只用于起点与实际形状。先直升，再在已验证路径末端尝试有限水平退让。返回路径是 worldOrigin，不是 COM。预算覆盖候选、查询、形状和路径长度。缺区块、快照变化、预算耗尽分别返回明确状态，不返回可执行的部分路径。 |
| `OrientationTargets` | 绝对轴角、四元数最短误差、世界/局部轴；可信捕获基准、保留 yaw 的回正及最近网格 yaw；缺少基准时拒绝或按显式策略使用世界网格，不能把当前倾斜当基准。绕真实 COM 计算候选位姿。 |
| `OrientationController` | 每个真实子步（≤0.05 秒）计算有界角速度修正；同时受角加速度、力矩和整座组合体惯量上界约束。已有超速逐步制动。到位由实际角误差和实际角速度共同判定。 |
| `OrientationSweep` | 逐局部碰撞盒构建连续转动弧的保守包络，包含 COM 平移和超过 π 的未折叠转动；不靠首尾采样批准转动。预算、地形 epoch、缺失覆盖均有拒绝结果。接触附近可能保守拒绝，不能将其解释为精确 TOI 或自动退让许可。 |
| `OrientationSession` | 可注入后端的单操作执行状态机；绑定结构、激活、主体、控制令牌及内容版本。初次复核计划版本，每个子步先观测、再 sweep、最后原子复核并应用。相同子步不能重复施力，轮询不推进时间；卸载/令牌失效/内容变化/提交失败终止，未知地形等待且有超时，spin 结束后按策略制动或保留惯性。截止时刻允许只读确认完成，不再施加新命令。 |

软障碍仍走授权边界：planner 只报告稳定障碍 ID 和 `NEEDS_AUTHORIZATION`，不直接删除草、树叶或其他方块。服务端适配器执行权限检查和内容变更后必须重新规划。`CLEAR` 只表示该不可变几何快照的净空，不意味着获得世界写权限。

spin 的剩余时间可由 `remainingSpinSeconds()` 提供给真实 scheduler 拆分子步。若适配器不拆步，则在不足完整下一子步时提前结束/制动，时间误差小于一个子步；不能仅缩短扫掠时间却继续执行完整求解步。所有已批准的命令仍扫掠完整实际子步。

## 精密操作节点与旧图兼容

一方通行、超电磁炮、空气操纵三系新增 `action/structure/reset_orientation` 和 `action/structure/spin`，schema 均为 1。共用 `ProgramStructureOrientationRuntime`、配置 codec 与 `ProgramStructureOrientationExecutors`，避免三系各自维护一套旋转逻辑。

- `reset_orientation`：已有 control 输入；配置回正模式、缺失基准策略和超时。
- `spin`：已有 control 和 axis 输入；配置世界/局部轴、带符号 rad/s、持续秒数和结束制动策略。
- PENDING 必须带 operationId；VM 只提交一次，随后轮询；未完成不向后触发 flow；身份变化和失败走现有诊断。
- 旧 `rotate` 的 ID、schema 1、配置和绝对轴角输入未修改；没有将旧图的“转到角度”改成“施加角速度”。
- 已有 server runtime 默认明确返回 `structure_orientation_unavailable`。新节点可编排/保存，但目前不会驱动物理体，也不会假报完成。

## 接线顺序与剩余工作

1. 完成 S0 接触历史问题的修复和原生回归。全局降低 warmstart 的方案仍被拒绝，见 [S0 报告](structure_upgrade_s0_warmstart_2026_10_23.zh-CN.md)。本线未改 native 生产代码或测试。
2. 接入服务端捕获日志和可信来源字段：源姿态/摘要、内容版本、捕获 operationId、激活和租约绑定。`NEW_CAPTURE` 不是调用者可自行声明的重置授权。之后才填 `UPG-CTRL-10/11`。
3. 分别实现本地/Sable 的不可变真实形状、地形覆盖、其他结构障碍 snapshot；新捕获排除源方块必须依据同一捕获日志。来源字段缺失时仍可使用显式世界回正，不能伪造捕获基准。
4. 实现两个后端的 `OrientationSession.Backend` 和服务端操作注册表：每服务器/结构有界管理、明确替换回执、结束保留期限、生命周期注销；在真实求解子步调用。`applyAngularVelocity` 必须原子复核租约及 pose/content/terrain/step；welded 结构必须保留整体 COM 线速度与各部件切向速度。惯量上界包含所有部件与平行轴项。
5. 把 planner 接入提取服务与 H 路径跟随：新捕获首步初始化独立处理；已有结构不得 setPose 脱困。到达完整目标且稳定才开始独立 200 tick 悬浮，不从提交/捕获时开始计时。当前本线只规划路径，不另造一个未接入的 H 计时器。
6. 让三系 server runtime 委托已验证的 `StructureOrientation` 服务，再迁移旧 rotate 的执行实现，保持图格式和语义。完成双后端游戏矩阵后，单独声明实际能力；禁止仅翻转 `GAMEPLAY_ENABLED`。

## 构建、测试与验收

使用 JBR25；本机可先 `. ./build/native-physics-p16-env.ps1`，`@physicsNativeArgs` 指向已构建的原生依赖。新控制代码不加载 MPS、没有改 DLL/ABI/manifest。

```powershell
./gradlew.bat test -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat sableCompatTest -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat build -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat build -DisDev=false @physicsNativeArgs --console=plain
```

本轮全量 JVM **2,957 项通过**，其中新增 **62 项**（控制算法与状态机 50，节点/API/配置 12）；Sable 专项 **16 项通过**。开发与发布两种 build 均通过。首次定向测试的法线 signed-zero 比较、首次全量测试的新增翻译排序均已修正并全量重跑通过。日志分别为本机忽略目录下的 `build/line-b-tests.log`、`build/line-b-build-dev.log`、`build/line-b-build-release.log`。

隔离 `runClientDev` 启动、资源/目录注册及正常保存退出通过。沿用原 live H 路径的台阶、嵌地、双扰动自动夹具测得 **3.064378 / 5.064378 / 7.064378 格**，各 **200 tick** 悬浮通过。运行目录为 `run/native-sublevel-direct-smoke-upgrade-line-b-20261023`，日志为 `build/line-b-client.log`，没有使用或修改原 `run/saves/test`、`test2`。该烟测不调用新 planner/session，不构成新回正/旋转玩法验收，也没有人工打开编辑器检查新节点文字显示。机器可读记录见 [B 线验证摘要](../validation/native_structure/structure_upgrade_line_b_2026_10_23.json)。

复现隔离客户端命令（换用新的唯一目录名）：

```powershell
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name 'upgrade-line-b-20261023' -Fresh
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
  -I validation/native_structure/sublevel_lift_tiers.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-upgrade-line-b-20261023 `
  -PacademySubLevelLiftEmbedded=true -PacademySubLevelLiftTerrain=step `
  -PacademySubLevelLiftDisturbance=true --console=plain
```

独立算法与 VM 测试通过不关闭以下 UNVERIFIED：

- 本地/Sable 真实子步 adapter、可信捕获日志绑定、其他结构运动中的 snapshot 原子性。
- 不平坦台阶/凹角/顶盖/偏心扰动的实际脱困，3/5/7 格完整抬升与随后 200 tick。
- 长杆扫墙、贴地回正、控制争抢、卸载/重载、程序终止对真实刚体的撤销。
- Sable 同夹具、多个结构的受控性能对照；保守旋转包络在接触附近的拒绝率与成本标定。
- 新节点文字与枚举选项的人工编辑器检查；已完成的 schema/codec/VM 自动测试不能替代此项。
- 原生 S0 已知失败仍独立保留；本轮 Java 算法通过不代表修复原生接触问题。

完成判据：独立代码的边界测试与旧图兼容通过；随后补齐上述适配器和双后端游戏证据，才能将 UPG-CTRL 对应入口从骨架替换为启用服务。目前不删除仍返回 UNSUPPORTED 的骨架 TODO，以免把未接线的方法标成已完成。
