# S0：可替换骨架与原生抬升自锁诊断

日期：2026-10-23。基线提交 `711feb72`。本记录承接[升级方案](structure_skill_physics_upgrade_plan_2026_10_23.zh-CN.md)的 S0，不代表 S1–S6 已实现。骨架接手见 [FILL-SPEC](structure_upgrade_fill_spec.zh-CN.md)。

## 修改与证据

旧 Rust 扰动测试在设置速度后立即清零，而且连续重置源姿态；linear/angular/both 实际没有分别进入求解器。本轮拆成 6 个测试、22 个场景，扰动统一在控制之后、求解之前施加并断言读回。源姿态只初始化一次，分别覆盖立即抬升、清速静置 10 tick、纯线/纯角/双扰动，以及当前 Java 控制的首次 tick 0/1、启动稳定和后续角 PD。原 `3.0 ± 0.12` 要求不变，没有 ignore、跳过或缩短测试。

所有场景为 128 格台阶槽，移动体分别为 7 个合并盒或 128 个独立盒，16 个地形盒，0.05 s、2 子步、200 tick。仅改变表中一个求解因素，重力、抬升力、摩擦系数、碰撞形状与 CCD 保持不变。

| 配置 | 通过场景 / 总数 | 关键结果 |
| --- | --- | --- |
| 原默认 Simplified，暖启动 1.0 | 2 / 22 | 仅立即干净抬升达 3.064；静置后合并/独立盒仅 0.1124 / 0.0131 格 |
| Coulomb，暖启动 1.0，仅静置对照 | 0 / 2 | 0.0830 / 0.0105 格，换摩擦模型不能解除 |
| Simplified，暖启动 0，仅静置对照 | 2 / 2 | 均 3.064 格；仅为诊断，没有采用全关方案 |
| Simplified，暖启动 0.8 | 20 / 22 | 一个生产控制场景和一个静置场景失败，未采用 |
| Simplified，暖启动 0.5 | 22 / 22 | 抬升通过，但后续堆叠倒塌，否决全局修改 |

逐例高度、最大漂移、角误差与原始日志摘要保存在 [S0 数据](../validation/native_structure/structure_upgrade_s0_2026_10_23.json)。运行时长包含诊断输出、不同接触驻留时间和其他构建负载，**不是性能基准，也不能用于宣称比 Sable 快多少**。

原诊断另有两个读取错误已修正：默认 Simplified 不写 Coulomb 的 `tangent_impulse` 字段；同一 patch 的暖启动摩擦会重复写入各接触点。现在按真实 solver contact indices 读取活动点，单独记录被过滤点旧值，patch 摩擦只计一次；缓存穿入距离与当前姿态重算穿入量分列。旧日志里 tangent=0 不能说明无摩擦，旧全部点法向总和也不能直接视作本步真实冲量。

证据将诊断范围缩小到紧密槽接触的暖启动历史约束复用：微小偏移之后，完全复用缓存可能维持侧壁预载与摩擦自锁；衰减到 0.5 可在原抬升质量门槛下解除。零系数还会改变 Rapier 内部执行分支，因此不单凭“设零通过”认定上游具体缺陷；0.5 的正系数对照保持暖启动分支。尚未证明具体上游缺陷或所有形状、负载和接触排列均已覆盖。

## 否决全局参数候选，保留生产行为

曾试验在 `World::new` 设置暖启动系数 0.5，全量 Rust 结果为 27 通过、1 失败：新增八层堆叠倒塌，中上层横倒，顶部滑出地板。原系数 1.0 的同夹具保持八层完整且自然睡眠，顶层横向偏移约 0.066 格。0.7 也发生倒塌，顶部最终低于地面，不能作为折中。**全局 0.5 候选已撤回，最终没有修改生产求解代码或内嵌 DLL。** 骨架依旧禁用；Sable、ABI 2、碰撞形状、抬升力和摩擦均保持原状。

该系数缩放每个求解器内部子步的历史法向、切向和扭转初始冲量，随后照常解算；不是把摩擦系数减半，也不是每个游戏 tick 只乘一次。大堆叠、重载和异质量比的收敛可能受影响，需要后续性能与稳定性矩阵。

新增普通接触回归：8 层方块自由落下，10 秒后保持层高和层间距 ±0.04 格、横向偏移 ≤0.1 格、线角速度 ≤0.02 并自然睡眠；无阻尼滑块靠接触摩擦停下。新夹具最初将横向门槛设为 0.04 格，原默认顶层 0.066 也会失败；确认原状态后将新堆叠横移门槛明确为 0.1 格，其余门槛不变，倒塌候选仍不通过。旧单块测试的 0.04 是纵向高度容差，没有横向断言。现有抬升断言完全没放宽。

最终 `lib.rs` 差异全部位于 `cfg(test)`。原生抬升红测继续保留；未来修复必须同时通过抬升和普通接触回归。真正修改生产 native 后必须同步 Windows 内嵌 DLL 与 manifest，不能只在外置开发 DLL 上验证。

## 复现

使用 JBR25 与仓库固定 Rust：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat nativePhysicsTest -DisDev=true @physicsNativeArgs
```

测试专用环境变量 `ACADEMY_STEP_SOCKET_WARMSTART=1` 可以复现默认矩阵；`0`、`0.8`、`0.5` 用于单因素比较。`ACADEMY_STEP_SOCKET_FRICTION=coulomb` 只改变摩擦模型，与前一变量互斥。最终默认仍是 1.0。相同覆盖也用于 `eight_layer_stack` 测试，便于排查普通接触副作用。测试结束移除环境覆盖；这些变量在 `cfg(test)` 中读取，游戏不读取。

```powershell
$env:ACADEMY_STEP_SOCKET_WARMSTART='1'
& "$physicsToolBin/cargo.exe" test --manifest-path native/academy_physics/Cargo.toml --locked `
  --target x86_64-pc-windows-msvc --target-dir build/native-physics/cargo `
  disturbed_step_socket -- --nocapture --test-threads=1
Remove-Item Env:ACADEMY_STEP_SOCKET_WARMSTART
```

## 验证与后续门槛

| 最终检查 | 结果 | 证据位置（build/run 均不入 Git） |
| --- | --- | --- |
| 编译、骨架校验、JVM 全量 | 2,895 通过，含新骨架 38 项 | `build/structure-upgrade-tests-final.log` |
| Sable 兼容专项 | 16 通过，未启动 Sable 客户端 | 同上、`build/test-results/sableCompatTest/` |
| 原生完整任务 | 23 通过 / 5 失败 / 0 ignored；默认堆叠已通过，抬升仍红 | `build/structure-upgrade-native-baseline-final.log` |
| Java/FFM 隔离运行 | 33 通过；显式排除已运行失败的 Rust 前置后单测，不代表完整任务通过 | `build/structure-upgrade-ffm-isolated.log` |
| 开发 / 发布构建 | 均通过；普通 build 不依赖 Rust 专项 | `build/structure-upgrade-build-dev-final.log`、`build/structure-upgrade-build-release-final.log` |
| 隔离客户端启动、原路径 H 台阶双扰动 | 3 档均通过；3.064 / 5.064 / 7.064 格，各 200 tick | `build/structure-upgrade-client-smoke.log` |

客户端由 `prepare_default_skill_smoke.ps1 -Name upgrade-s0-20261023 -Fresh` 准备，目录 `run/native-sublevel-direct-smoke-upgrade-s0-20261023`；使用 `sublevel_lift_tiers.init.gradle`，embedded=true、terrain=step、disturbance=true。正常保存退出，注册日志确认骨架 gameplay disabled。该既有产品夹具再次通过，但依旧不代表所有原生紧密槽或真实玩家场景通过；它与原生测试的差异仍是 S0 待对齐项。

所有结果均在撤回生产参数候选后复核。全局 0.5 因普通接触回归被否决，不能用 22 项抬升通过宣称修复完成。新骨架可以独立接手，S0 尚未关闭。

S0 的局部接触历史处理、Sable 相同复杂地形、受控 1/4/16/32/64 数量扫描、真实玩家操作/目视抖动验收仍待完成。Rapier 0.36.1 的公开接触查询和 hook 不能直接修改单体接触缓存；下一步需评估固定版本的最小引擎扩展，或保持真实形状的控制/净空方案，不能用持续传送或全世界降参数替代。S1–S6 继续保持禁用骨架，按主方案门槛顺序接线。

### 下一修复切片（方案，尚未实现）

1. 在固定 Rapier 源码补丁中评估接触级 `warmstart_scale` / 一次性 `reset_history`，默认 1/false。覆盖 Simplified、Coulomb 和 generic 路径及内部子步，保留许可和补丁来源；当前公开 hook 没有这些字段。
2. 首次只作用于有效提取租约下的刚体与原槽固定地形侧壁；底部承重、顶板、其他动态体接触保持原设置，不删除真实接触或降低实际摩擦系数。
3. 处理 normal、tangent、world-tangent、twist 缓存及当前/前轮 solver cluster，避免重聚类恢复旧值；不能把碰撞事件的历史测量数据误当暖启动缓存清掉。
4. 以 world/body generation、controlId、epoch、TTL 绑定策略。完成、取消、超时、暂停、卸载与销毁均撤销；FFM 与 native 双层校验，native 独立到期兜底。正式采用时补能力/ABI、DLL 与 manifest，禁止热替换。
5. 同一世界同时验证 22 项抬升、旁边的八层堆叠、结构上承托的堆叠、滑块和关节；补租约过期/争抢/句柄复用负例。接触查询按目标 collider 邻接执行，测量开销，不逐 tick 扫描整个维度。
