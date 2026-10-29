# 动能投掷 H 输入、软方块捕获与保持计时修复接续记录

记录日期：2026-10-29。仓库：`D:/mcmodtest/AcademyCraft-Reborn-26.2`。本批开始时 HEAD 为 `c1eb76f1`（完善物理结构传送断裂与精密操作）。本文记录本批设计、最终验证、历史排障与后续边界。

## 当前验证状态

- 冻结最终源码后，**主测试 3361 / Sable 专项 49 / 编辑器 104 全部通过**，失败、错误、跳过均为 0；开发与发布构建均成功。早期 3360 项通过仅保留为历史记录。
- 最终 **8 份游戏报告、共 38 个场景全部通过**：native/suite 各九场景准备边界、各三系真实 VM、正常 JVM 干净地面 H 六案、vegetation H 六案，以及 127 格结构的 75/90 两个穿墙场景。精确目录和范围见后文验证表。
- 正常 JVM `clean-final` 干净地面三档 **6/6 全部通过**；r9 独立 vegetation 也为 **6/6 全部通过**，到位后 200/201 ticks 自然释放，发射速度 75.0 blocks/s，8 个飞行 ticks 前进 30.0 格。r7 的历史干净六案通过和第 7 案误断言继续保留于排障记录。
- r9 验证受保护 WHEAT 随结构完整转移，tier0 保留 36 格、tier1/2 各保留 30 格作物，短草按规则清理。默认 `MOTION_PROTECTED` 包含作物和树苗，不能将此结果表述为“所有植物已清除”。
- 单独九场景准备夹具已将用于清理断言的作物替换为 POPPY/短草，并要求源格加载稳定 10 ticks 后才发起；`native-r3` 和 suite 均已通过九场景。
- r1/r2 是夹具站位、瞄准高度导致的选区拒绝；r3 排障进一步发现并修正新增 guard 与提取预览之间的请求对象身份问题；r4 已观察首案实际到位，但随后出现 JBR GC 原生崩溃。各轮的失败、未完成报告均保留，不能改写为通过。
- 最终机器报告为 [`kinetic_throw_h_validation_2026_10_29.json`](../validation/native_structure/kinetic_throw_h_validation_2026_10_29.json)，记录 34 个本批文件、源码哈希、八份实机报告、两种 JAR 校验及 148 文件源测试存档哈希。源测试存档未改，既有 Rust 哈希保持不变。
- 用户已确认本批 34 个相关文件的本地提交，提交信息为“修复动能投掷捕获发射与悬停计时”，以包含本记录的提交为准。既有 `native/academy_physics/src/lib.rs` 改动属于前置工作，不能混入本批提交。

## 用户报告及诊断链

用户本次报告三个严重问题：

1. 动能投掷在本地物理后端捕获含杂草、种子或其他不完整方块的地面时，先破坏软方块，随后结构创建失败。
2. 按 H 后经常无法推动刚刚创建的本地结构，技能看起来接受了输入却没有有效发射。
3. 结构上升后可保持的时间明显不足，tick 计时不符合实际服务器时间。

已有现场诊断涉及三个独立但可能连续出现的原因：

| 诊断 | 含义及影响 | 本批处理方向 |
| --- | --- | --- |
| `scheduled_tick_pending` | 明确破坏允许清理的草、花等软格后，原版支持更新或计划 tick 尚未稳定；紧接着提交捕获可能被正确的源格检查拒绝。 | 将短暂不稳定视为有时限的准备态，保留同一次操作，按后续 server tick 重新观察。 |
| `terrain_coverage_unknown` | 新选区及其上升路线的宿主地形尚未具备完整观测，不能据此断言无碰撞并转移实心方块。 | 等待真实地形覆盖，再重新预览；保持原截止时间和权限检查。 |
| `damage_sweep_shape_budget` 等 shape budget 拒绝 | 现场结构质量/规模出现过约 67、122、127 的情形。原路径检测为多个目标反复预扣整组形状乘以最大采样数，可能很快耗尽预算；发射登记成功后又停速，会表现为 H 无效。 | 预计算扫掠形状、先做包围盒过滤、按实际精测计费并复用相同世界碰撞盒的相交结果；增强失败时记录原因并保留正常物理运动。 |

软格、计划 tick 和地形覆盖属于捕获阶段；扫掠预算属于已经存在的结构发射阶段。回归时需分别记录捕获结果、服务会话、实际 solver 速度与位置，不能只根据技能接受输入或 `FLYING` 状态作结论。

## 修复体系

### 软方块和地形准备

共享入口位于 `StructureCaptureExtractions`，三系使用同一准备和提取流程：

1. 检查源格、权限、结构和力预算，生成包含实心格、受保护植物保留格与可明确清理软格的选择快照。
2. 预先观察可用地形及相关限制；未知地形覆盖继续等待。
3. 明确执行允许破坏的软格清理，通过原版破坏、掉落和支持更新流程处理短草、允许清理的花等；受保护作物和树苗保留并随支持方块转移。
4. 原版更新仍未稳定时，保留准备操作，下一实际 server tick 重新检查。软格清理不因此重复执行、重复掉落。
5. 用准备后的实际源格重新预览整个提取路线；只有预览有效时才提交内容事务。
6. 事务完成后绑定同一结构的提取控制，实际脱离源槽、到位后才允许节点输出结构引用或 H 发射。

`CapturePreparationPolicy` 当前将 `TERRAIN_PENDING` 和精确的计划 tick 等待原因作为可重试观察，准备窗口为 100 ticks；同一 game tick 最多尝试一次。硬阻挡、权限拒绝、源格改变和其他实质失败仍终止操作。不得为了通过夹具直接跳过这些检查。

默认标签 `academy:structure_motion_protected`（`src/main/resources/data/academy/tags/block/structure_motion_protected.json`，运行时常量 `MOTION_PROTECTED`）包含作物和树苗。软格处理不能仅按“碰撞形状不完整”判定删除：短草等允许清理内容应走明确破坏流程，受保护植物应作为结构内容完整转移。保留标签规则，夹具据此分别检查“允许清理格已清理”和“受保护作物没有丢失”，不应修改产品标签以满足错误断言。

准备流程还包含在途 guard，防止权限或破坏回调重入后继续提交已取消的操作。**预览与内容事务必须使用同一个 `StructureCaptureRequest` 实例**：协调器按对象身份关联预览证明，不能在预览后重新构造 guard 请求再提交。r3 曾暴露此问题，已将 guard 请求提前构造，并让预览和 `StructureTransactions.capture(host, request.capture())` 共用它。

关键代码：

- `src/main/java/org/academy/api/common/structure/StructureCaptureResult.java`
- `src/main/java/org/academy/api/common/structure/upgrade/StructureCaptureExtractions.java`
- `src/main/java/org/academy/internal/common/structure/upgrade/control/CaptureSoftClearance.java`
- `src/main/java/org/academy/internal/common/structure/upgrade/control/CapturePreparationPolicy.java`

### H 路由和实际发射

H 的线上入口是 `KineticThrow.PressPacket` / `ReleasePacket`，由服务器决定当前操作：

- 没有结构会话时，按下蓄力，释放捕获。
- 同一次捕获还在准备或提交时，重复 H 保持 `PREPARING`，不得开始第二次捕获或提前发射。
- 自己的已到位悬停结构可以沿当前视线发射，准星无需重新指向该结构。
- 服务拒绝或飞行观察能力不可用时，保留明确失败原因，不把结构置零速后报告发射成功。
- 发射后必须验证实际 solver 的速度、方向和位移。标准速度为 75 blocks/s，熟练度第三里程碑为 90 blocks/s。

`PreparedStructureImpactSweep` 为一次路径操作准备不可变的姿态采样及各格包围盒。远离目标的组和采样先通过 AABB 剔除，只有真正执行的精确相交测试计入预算；同一次路径中相同世界碰撞盒的纯几何相交结果可以复用，缓存最多 65,536 条。来源、姿态、内容和权限仍在应用时重新校验。总预算仍有效，不能改为无界检测。

本批保留用户既定行为：64 格增强范围内破坏可破坏阻挡；真正最终碰撞才进入爆炸、断裂和还原/掉落流程；到达增强距离但没有碰撞时不爆炸。

关键代码：

- `src/main/java/org/academy/internal/common/ability/accelerator/movement/VectorStructureService.java`
- `src/main/java/org/academy/internal/common/structure/PreparedStructureImpactSweep.java`
- `src/main/java/org/academy/internal/common/structure/upgrade/damage/LiveStructureDamage.java`
- `src/main/java/org/academy/internal/common/structure/upgrade/launch/KineticStructureFlight.java`

### 保持计时

`ExtractionHoverClock` 记录实际服务器 tick，避免把 solver 子步、重复 receipt 查询或重复技能调用当成多个保持 tick。

- 到位依据实际世界质心、位置误差以及线速度、角速度稳定性，不能仅依据命令目标或上升经过目标高度。
- 每个完整 server tick 最多提交一次保持进度；同一 tick 的所有子步均须获准且稳定。
- 地形等待、未获准运动或扰动暂停进度；同 tick 后续一次成功不能抹掉先前的暂停。
- 时间跳跃不补算为实际模拟的保持时长。
- 技能外层截止时间仅用于有界生命周期，不能先于共享提取控制的完整 200 ticks 窗口释放正常悬停。

相关代码及测试为 `LiveExtractionMotion`、`ExtractionHoverClock`、`ExtractionHoverClockTest`。完整游戏保持验证需同时检查真实姿态和实际 server tick，不能由单元测试代替。

## 准备态 API 与三系节点续跑

新增语义必须继续保持：

| 返回/调用 | 约定 |
| --- | --- |
| `StructureCaptureResult.preparing(operationId, detail)` | `status=PENDING`，`structure` 为空；尚无真实结构，不发布伪造引用。 |
| `StructureCaptureExtractions.poll(originalHost, originalActor, operationId)` | 无引用准备期间使用 facade ID；之后仍以同一 facade ID 续跑并返回真实引用。对普通真实内容事务 ID 兼容回退到原事务查询。 |
| `StructureCaptureExtractions.cancel(originalHost, originalActor, operationId)` | 只取消该 actor 的这次准备或对应提取控制。已进入持久事务的内容仍由事务恢复流程持有，不复制或删除真实结构。 |
| `sourcePositions(actor, reference)` | 返回软格处理后的实际、不可变源格，用于质量、数量、质心、附件和喷嘴局部坐标。 |
| `owned(actor, reference)` / `release(handle)` | 使用保存的精确 Handle 取消本次控制，避免退出旧程序时清除后继会话。 |

同步准备若立刻取得真实引用，仍返回真实 journal ID，并移除临时 guard。只有经历无引用等待的操作保留 facade ID。facade 内部提取 Session 绑定的始终是真实内容事务 ID。未创建内容的准备取消后立即移除；已进入 pending 内容事务的 facade 保留到持久事务结束。终态 facade 的保留与回收有界，不能永久积累。

三系适配点：

| 能力 | 技能服务 | 程序运行器 |
| --- | --- | --- |
| 矢量操作 | `VectorStructureService` | `ServerAcceleratorProgramRuntime` |
| 空力使 | `HighSpeedJetStructureService` | `ServerAeromanipProgramRuntime` |
| 电击使 | `MagneticStructureService` | `ServerElectromasterProgramRuntime` |

三者均必须满足：

1. 接受 `PENDING + 无 reference`，保存独立操作 ID；不能因引用尚未出现而报错、重新发起或再次扣费。
2. `ProgramStructureCaptureMonitor` 通过 facade 查询准备操作，保留延迟失败原因，不能把 facade ID 直接交给 `StructureTransactions.poll`。
3. 首次真实引用出现后更新实际源格；Jet 需同步重基喷嘴局部原点；Magnetic 需按实际保留格刷新磁性参与比例总数。
4. 保存原 host 与原 actor。换维、死亡、断线或程序退出时取消原 host 的本次操作。
5. `cancelProgramCapture` 必须匹配本次操作 ID；旧节点退出不得清除同玩家的新捕获或其他控制者的会话。
6. 保存精确提取 Handle。不能在退出时重新查找一个可能属于后继控制的句柄再释放。
7. `PENDING` 节点恢复时继续观察原会话；只在首次合法发起时收取节点资源费用。

Magnetic 的捕获前置准入另做精确适配：只有 `begin`/`programExtract` 进入已启用的共享准备流程时，允许精确的 `scheduled_tick_pending` 原因留给准备操作等待。普通 `admit`、公共 `magneticSelection` 及直接结构控制仍保持原拒绝语义；其他 payload、权限、流体和不可破坏原因不会被放宽。规则单测覆盖共享准备启用/关闭，以及其他拒绝原因原样保留。

新增 `ProgramStructureCaptureMonitorTest` 用连续三次无引用准备、出现真实引用、最终成功的同一操作序列验证监控兼容。三系实际 VM 的准备续跑与后续操作链已在 native 和 suite 通过，见下文；其证据是生产 executor、运行器和 scheduler 的真实执行，`actual_save_execute_packets=false`，不能据此宣称另行完成了保存/执行 C2S 编辑器路径测试。

## 真实 H 夹具及验证方法

新增文件：

- `src/gametest/java/org/academy/internal/client/validation/KineticThrowHProtocolSmoke.java`
- `validation/native_structure/kinetic_h_protocol.init.gradle`

### 夹具覆盖和证据标准

夹具在真实客户端发送 `MisakaNetworkClient.send(PressPacket/ReleasePacket)`，先发送实际视角移动包，再发送技能 C2S。没有直接调用 `VectorStructureService.launch` 或 `applyMotion` 冒充 H。

默认 13 个场景：

- 干净完整地面，以及混合短草、新播种小麦和成熟小麦地面。
- 每种地面分别以 tier 0/1/2 捕获，预期抬升 3/5/7 格。
- 植被场景分别检查短草清理、受 `MOTION_PROTECTED` 保护的新播种/成熟小麦随结构转移；“所有植物都应清除”不是合法通过条件。
- 每个档位拆为自然保持和 H 发射两次捕获。保持场景从实际到位后观察完整 200 server ticks，并等待自然释放；发射场景另一次到位后用 H 发射，避免恰好到期时真实网络排队造成竞争。
- 最后追加 tier2 不规则 127 格结构：13×11 平面去掉一个 4×4 角。实际 H 发射后需破坏前方两列、共 10 格石墙，并继续产生预期前向位移。
- 同一客户端 tick 发送重复按下/释放包，覆盖服务器重复输入防护。

只对观察玩家设置无重力和零运动，防止玩家掉落、误碰结构或鼠标输入漂移。**夹具不向结构添加力，不设置结构速度，不设置结构姿态。**

报告写至隔离目录的 `kinetic-h-protocol-result.json`，启动时先写 `status=running, passed=false`，完成后才写通过。检查以下字段及日志：

- `actual_h_c2s=true`、`structure_assistance=false`。
- 每案真实引用、捕获格数、solver 质量、源质心及目标质心。
- 首个实际稳定 tick、服务到位 tick、到位后释放时间和有效保持观察数。
- 实际前向速度、8 个飞行 server ticks 后位移、最终速度和被破坏墙格数。
- 日志 `KINETIC_H_PROTOCOL_CASE_PASSED` 与最终 `KINETIC_H_PROTOCOL_PASSED`。

`status=running`、崩溃、夹具超时、只出现到位日志、旧目录中的历史 `passed=true`，均不能算本轮通过。

### 隔离启动示例

使用 JBR 25，在仓库根目录执行。每轮使用全新目录名；以下仅是可复现命令，本文未执行这些启动或 Gradle 命令。

```powershell
& .\validation\native_structure\prepare_default_skill_smoke.ps1 -Name kinetic-h-standard-20261029 -Fresh
.\gradlew.bat runClientDev -DisDev=true --init-script validation/native_structure/kinetic_h_protocol.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-kinetic-h-standard-20261029

& .\validation\native_structure\prepare_default_skill_smoke.ps1 -Name kinetic-h-speed90-20261029 -Fresh
.\gradlew.bat runClientDev -DisDev=true --init-script validation/native_structure/kinetic_h_protocol.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-kinetic-h-speed90-20261029 -PacademyKineticHSpeedMilestone=true
```

夹具要求 native-only、默认提取路径开启。它没有覆盖 Sable 混装，不能用其 native-only 结果代替双后端适配验证。准备脚本只复制隔离世界并记录源世界 SHA256 清单；完成后应复核源世界，不能把原用户存档直接作为可变测试场。

聚焦排障属性：

| Gradle 属性 | 取值/作用 |
| --- | --- |
| `academySubLevelSmokeDirectory` | 独立 `run/native-sublevel-direct-smoke*` 目录。 |
| `academyKineticHSpeedMilestone` | `false` 为 75 blocks/s；`true` 为 90 blocks/s，并验证熟练度策略确实启用该里程碑。 |
| `academyKineticHSurface` | `all`、`clean`、`vegetation`、`irregular`。 |
| `academyKineticHMode` | `all`、`hold`、`launch`。 |
| `academyKineticHTier` | `-1` 全档位，或 `0` / `1` / `2`。 |
| `academyKineticHRun` | 默认 `clientDev`。 |
| `academyNativePhysicsTrace` | 可选开启现有原生物理跟踪。 |

`irregular` 仅有 tier2 发射场景；不要选 `irregular + hold` 或 `irregular + tier0/1`，这些组合会因没有场景而明确拒绝。

### r1—r9 排障记录

| 轮次/隔离目录 | 观察及修正 | 结果解释 |
| --- | --- | --- |
| r1：`run/native-sublevel-direct-smoke-kinetic-h-fix-20261029` | 首案等待捕获超时；日志 `selection_blocked_or_seed_missing`。观察玩家最初位于选区正上方同一 X/Z，触发技能排除脚下结构的前向安全半空间。 | 夹具站位不满足真实选区规则，不能归为软格修复失败。 |
| r2：`run/native-sublevel-direct-smoke-kinetic-h-fix-r2-20261029` | 调整站位后继续排查瞄准高度和真实 ray 的 seed；仍记录 `selection_blocked_or_seed_missing` 及捕获超时。最终按玩家实际眼高、平台距离计算俯仰，并把 seed 保持在前向安全半空间。 | 需保证视角包与服务器 ray 一致；不应放宽生产选区安全检查来迎合夹具。 |
| r3：`run/native-sublevel-direct-smoke-kinetic-h-fix-r3-20261029` | 捕获仍未完成；排障发现准备取消 guard 在预览后重建了请求对象，破坏协调器的预览身份关联。已改成 guard 请求在预览前创建，提交复用 `request.capture()` 的同一实例。 | 属本批新增加固路径的问题；修正后需重新编译和从全新隔离目录复测。 |
| r4：`run/native-sublevel-direct-smoke-kinetic-h-fix-r4-20261029` | 日志已出现首案 `KINETIC_H_PROTOCOL_ARRIVED`，实际高度误差约 0.013 格、线速度约 0.067 blocks/s；之后 JBR 在 GC 原生路径崩溃。报告仍为 `running`。 | 该轮中断，不能算保持或发射通过。保留 `hs_err_pid*.log`，按运行时崩溃单独调查并重跑。 |
| r5：`run/native-sublevel-direct-smoke-kinetic-h-fix-r5-20261029` | 干净地面 tier0/1/2 的六个保持/发射场景已通过，观察到 200/201 ticks 自然释放，以及约 75 blocks/s、8 ticks 约 30 格实际前进。切换下一场景时未清理上一 `FLYING` 服务视图，`observeCapture` 将旧会话误当成下一次捕获。 | 六个已完成场景是局部证据；后续中断属于夹具场景隔离错误，不能写成生产捕获或发射缺陷。 |
| r6：`run/native-sublevel-direct-smoke-kinetic-h-fix-r6-20261029` | 已修复场景间旧服务视图清理；客户端旋转包和 H 包经过不同 dispatch，H 仍可能按服务器旧瞄准方向发射，速度模约 75 正确。 | 夹具输入同步问题，不能把方向断言失败直接归为生产物理速度错误。 |
| r7：`run/native-sublevel-direct-smoke-kinetic-h-fix-r7-20261029` | 新增 `AIM_LAUNCH` 阶段，先等实际服务器朝向确认，再发送真实 H C2S。干净地面三档六案均逐案通过；第 7 案因旧夹具要求作物清除而中断。 | 干净六案具有局部通过证据；作物本应受标签保护随结构转移，第 7 案属于夹具误断言，不是生产捕获失败。 |
| r8：`run/native-sublevel-direct-smoke-kinetic-h-vegetation-r8-20261029` | 独立 vegetation 轮次仍因旧断言报 `soft vegetation was copied into the body`。 | 默认标签本就要求 WHEAT 随结构转移；修正夹具的分类断言，保留生产标签和捕获逻辑。 |
| r9：`run/native-sublevel-direct-smoke-kinetic-h-vegetation-r9-20261029` | vegetation 三档保持/发射六案全部通过，报告 `passed=true`。tier0 保留 36 格 WHEAT，tier1/2 各保留 30 格；保持 200/201 ticks，发射速度 75.0，8 ticks 前进 30.0 格。 | 已验证草清理与受保护作物完整转移，并在同一植被矩阵验证完整保持和真实 H 发射；127 格、90 速度与准备边界的独立结果见后文专项表。 |

r4 的原生崩溃证据位于 `run/native-sublevel-direct-smoke-kinetic-h-fix-r4-20261029/hs_err_pid54836.log`。早期 G1/GC 和 C2 原生崩溃的两项运行时证据继续保留，不能改写为 gameplay 断言失败或通过。中间测试曾通过 `JAVA_TOOL_OPTIONS` 使用 Tier1 编译与 SerialGC 隔离该问题；该调整仅限测试进程，**没有修改产品 JVM 配置**。后续 127 格 75/90 与 native/suite 两轮 VM 均已在正常客户端 JVM 配置通过，不再将临时 Tier1/SerialGC 作为这些通过结果的前提。

最终通过结果见下节。原失败目录保留以便追踪，不能复用或改写其报告。场景准备需清理上一场景的技能服务视图，并确认服务器实际朝向，不能只根据客户端已发送旋转包就假定服务器已处理。

### 单独九场景准备夹具：native/suite 均已通过

`StructureCapturePreparationSmoke` 及 `validation/native_structure/structure_capture_preparation.init.gradle` 专门覆盖 `scheduled_before`、`scheduled_after`、`cancel`、`timeout`、`denied`、`source_changed`、`cancel_in_callback`、`reentrant`、`many_plants` 九种准备/取消边界。

原夹具用 WHEAT 作为“应破坏软格”，与默认作物保护标签冲突。现已将该类源格改成 POPPY 和短草，并要求源格连续加载稳定至少 10 ticks、构造后也至少经过 10 ticks，再开始操作；否则新建源格尚未稳定会使原版计划 tick 和地形等待断言失真。这些属于测试材料与加载前置条件修正，不能写成产品删除受保护植物的改动。

`run/native-sublevel-direct-smoke-capture-prep-native-r3-20261029` 与 `run/native-sublevel-direct-smoke-capture-prep-suite-20261029` 两轮的 `structure-capture-preparation-result.json` 均为 `passed=true`、9/9 场景通过。

## 当前已通过的验证证据

以下目录均位于 `run/`，最终 8 份报告共 38 案全部为 `passed=true`。报告是运行产物，不纳入 Git。通过范围按具体夹具区分，已完成且代码未变化的检查可以复用。

| 专项 | 隔离目录 | 已通过结果 |
| --- | --- | --- |
| native 准备边界 | `native-sublevel-direct-smoke-capture-prep-native-r3-20261029` | 9/9，含计划 tick、取消、超时、拒绝、源变化、回调取消、重入和多软格清理。 |
| suite 准备边界 | `native-sublevel-direct-smoke-capture-prep-suite-20261029` | 同一九场景 9/9。 |
| native 三系真实 VM | `native-sublevel-direct-smoke-kinetic-preparation-vm-native-20261029` | backend 为 `academy:sublevel`，三系均首次无引用准备、同一操作续跑、真实引用出现后完成原控制链。 |
| suite 三系真实 VM | `native-sublevel-direct-smoke-kinetic-preparation-vm-suite-20261029` | backend 为 `academy:sable`，同一三系 VM 全部通过。 |
| 正常 JVM 真实 H 干净地面三档 | `native-sublevel-direct-smoke-kinetic-h-clean-final-20261029` | 6/6，最终 `kinetic-h-protocol-result.json` 为 `passed=true`；覆盖完整保持和准星朝空处的真实 H 发射。 |
| 真实 H 植被三档 | `native-sublevel-direct-smoke-kinetic-h-vegetation-r9-20261029` | 6/6；受保护 WHEAT 保留 36/30/30 格、短草清理；保持 200/201 ticks；75.0 blocks/s，8 ticks 前进 30.0 格。 |
| 127 格真实 H，75 | `native-sublevel-direct-smoke-kinetic-h-irregular-75-20261029` | 正常客户端 JVM；真实捕获 127 格、破坏 10 格石墙；速度 75.0，8 ticks 前进约 30.0 格，`structure_assistance=false`。 |
| 127 格真实 H，90 | `native-sublevel-direct-smoke-kinetic-h-irregular-90-20261029` | 正常客户端 JVM；真实捕获 127 格、破坏 10 格石墙；速度 90.0，8 ticks 前进 36.0 格，`structure_assistance=false`。 |

三系 VM 使用已有 `StructureProgramOrientationSmoke` 与 `structure_program_orientation.init.gradle`，增加 `-PacademyStructurePreparationVmSmoke=true`（JVM 属性 `academy.structurePreparationVmSmoke`）。夹具在真正启动图之前给实际铁块源安排 12 ticks 后执行的普通 `ScheduledTick`，没有代替运行器直接发起或轮询捕获。两后端均记录：

| 能力 | 无引用准备 server tick 样本 | 提取 CP 扣费次数 | 续跑调用最大 CP 差 |
| --- | --- | --- | --- |
| 矢量操作 | 11 | 1 | 0.0 |
| 电击使 | 12 | 1 | 0.0 |
| 空力使 | 11 | 1 | 0.0 |

每系均保持同一准备操作 ID，出现真实结构后继续完成提取、控制、旋转、复位、250 ticks 自转/制动和释放链。该角向夹具沿用 `linear_gravity_support_only=true`，在获得真实控制后施加有限的线性重力支撑以隔离角运动测试；**这是 VM 角向测试的既有支撑条件，不是 H 发射辅助，也不能替代 `structure_assistance=false` 的 H 证据。**

## 最终构建与交付

在 JBR 25 下执行 `test sableCompatTest build -DisDev=true` 及 `build -DisDev=false`，两轮均通过。为避开已记录的 Gradle C2 编译崩溃，本轮 Gradle 进程使用 `-XX:TieredStopAtLevel=1`、Kotlin 编译在进程内执行；没有改写项目 JBR/GC 配置。正常客户端 JVM 的干净 H、127 格 75/90 及两后端三系 VM 均通过；vegetation 与准备边界场景的诊断 JVM 选项逐项列在机器报告中。

| 产物 | 大小 | SHA-256 |
| --- | ---: | --- |
| `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` | 32,548,485 字节 | `D5EB9E6FD4E1DEF92691BA64C77E3157B8F7D1B222DB7C1FCAE2813CD84432FC` |
| `build/libs/academy-26.2.0-0.0.4-alpha-release.jar` | 25,077,384 字节 | `71335C4A65D4767F8CE91842D272205AA16F0FB86D24E93EC03156A8D90200CF` |

包内关键修复类与最终编译输出一致，新客户端夹具未打入 JAR。构建日志为 `build/kinetic-h-final-dev-build-20261029.log` 和 `build/kinetic-h-final-release-build-20261029.log`。本批已完成验证并获得本批提交授权；原有 Rust 改动不纳入提交。

## 后续复测与接续

1. 先读 `AGENTS.md`、本记录和当前 `git status`，核实其他 agent 是否仍在改文件。保留既有 Rust 改动以及原始用户存档。
2. 查看最新真实 H 报告和日志，确认当前代码、启动配置和隔离目录相匹配。客户端崩溃后的 `running` 报告必须重跑。
3. 本批最终测试与两种构建已完成；后续代码变化后再按影响范围和 `AGENTS.md` 重跑，不复用过期检查。
4. 复核正常 JVM `clean-final`、vegetation-r9 与 127 格 75/90 的最终报告。只有后续相关代码变化或新失败出现时才重跑已经通过的同配置专项。
5. 复用已通过的 native/suite 各九场景准备边界和各三系真实 VM 结果；核实最终构建仍包含这些验证对应的代码版本。
6. 90 的全部地表/档位组合、64 个真实异形体长期压力、旧编辑器漏绘、跨维传送及原完整故障矩阵仍可继续扩展。人工复测记录后端、材料、破坏设置和实际服务器 tick，不能仅凭发射日志或 `FLYING` 标志判断成功。
7. 完成验证并取得用户本批提交确认后，只提交本次代码、测试和记录。排除既有 Rust 改动、`run/`、`build/`、崩溃日志及其他运行产物。
