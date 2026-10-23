# 结构技能与精密操作迁移接续记录

## 2026-10-23 新方案与可替换骨架入口

- [技能物理结构完善方案](structure_skill_physics_upgrade_plan_2026_10_23.zh-CN.md)：性能与 Sable 适配、防楔紧、技能来源标记与静止归块、角控制节点、统一技能破坏、高速首撞与有限碎裂；含接口契约、分期和验收。审查基线 `711feb72`。
- [MPS 采用评估](mps_physics_evaluation_2026_10_23.zh-CN.md)：固定源码/子模块与许可证，批量接口和求解器候选，实际流体/软体能力及耦合待修项。按用户后续流体、软体规划，将 MPS 列为统一物理底座的重点候选，尚未构建或替换 DLL。
- [骨架 FILL-SPEC](structure_upgrade_fill_spec.zh-CN.md)：公开接口、禁用实现、主类注册、资源模板、构建检查及协作分工已本地提交 `26635388`。[B 线算法](structure_upgrade_line_b_2026_10_23.zh-CN.md)之后，已完成 [本地/Sable 后端角控制与三系节点接线](structure_upgrade_line_b_live_2026_10_23.zh-CN.md)，旧 rotate 保留图格式和绝对轴角语义并改为异步完成。新提取 planner、来源/归块及 MPS 仍未接入；S0 历史红测不因角控制接线关闭。
- 当前实现与已跑测试以[最新接续](sublevel_continuation_2026_10_24.zh-CN.md)和 [P0 报告](sublevel_p0_test_report_2026_10_23.zh-CN.md)的最新补充为准；原生楔紧红测、Sable 同场景和受控 N 扫描仍未关闭。下方保留历史记录。

更新日期：2026-10-20。此文件随每个实现/验证节点更新；状态以实际代码和结果文件为准。最新补充为贴地接触、紧密地形 H 抬升、Sable 捕获坐标及源格同步修复，并另交付仅设计的区块卸载回正方案。此前渲染、反射生命周期与冻结铁块恢复记录保留。下方 v2 骨架和早期性能段落保留当轮历史状态，其他 Agent 后续实现及验证见 `FILL-SPEC.md`，不能把旧诊断数值当当前性能结论。

## 2026-10-20 贴地接触与紧密地形抬升

用户确认 `test2` 草地有不可见阻挡，之后关闭存档并补充怀疑运动结构在原位置留下阻挡。原存档只读；隔离夹具用于真实客户端输入和独立物理回归。存档外层包围盒包含空角落，不能由外框覆盖玩家直接推断那里存在实体碰撞。现场坐标与验证边界见 [接触审计](sublevel_ground_contact_audit_2026_10_20.zh-CN.md)。

已修正的确定问题：

1. 承载逻辑对极小位移也调用 `ServerPlayer.teleportTo`，不断启动传送确认并丢弃普通移动输入。现在使用受碰撞约束的位移、重置网络位置基准，并在客户端正确时机预测一次承载；斜面跨步通过向下碰撞落到实际面。
2. 多子盒与坑壁的共享内部面产生斜向接触法线，将合法向上滑动锁住。原生 `ContactSurfaces` 缓存遮挡面并剔除内部法线分量；没有扩大空隙、缩小碰撞盒或绕过实体墙。`PlotGeometry` 同时精确合并相邻盒。随包 Windows 库与源码同步更新。
3. Sable 首个实际种子不在最小角时，原桥接没有按首次及最终重心修正刚体位置，导致碰撞几何偏入周边地形。现在通过真实 pipeline teleport 重锚并更新历史姿态/边界。手动 H 使用质心控制，预算裁剪保留抬升所需顶部依赖；稳定到位后开始完整 200 tick，到期不再自动发射。精密提取保留原选区裁剪。
4. 可见但不 tick 的 FULL 区块会跳过普通方块更新。捕获源格移除及回滚现在显式发送当前 section 状态；独立对照验证通过。此缺陷与用户所报运动原点不可见阻挡之间尚未建立唯一对应，见 [同步报告](structure_host_block_sync_2026_10_20.zh-CN.md)。
5. 非耐久物品的挖掘校验误加 DAMAGE 组件，种子等普通持有物会导致破坏被撤销。仅对耐久物品校正磨损；三种真实挖掘输入通过，见 [挖掘补充](sublevel_mining_regression_2026_10_20.zh-CN.md)。

抬升根因、Rust 负例和实机命令见 [嵌地抬升记录](sublevel_embedded_lift_repair_2026_10_20.zh-CN.md)。[卸载回正设计](structure_chunk_unload_restore_plan.zh-CN.md) 保持 **DESIGN ONLY**，没有接入任何自动写回逻辑。方案采用排除全部本地结构票据后的外部驻留需求，在当前质心附近回正到直立格网；目标冲突时持久化暂停并保留完整内容，不覆盖已有方块，不强制无限加载。

本轮最终验证：

| 检查 | 结果 / 本机日志 |
| --- | --- |
| 本地真实 H，紧密 21×21×9 土体 | 三档实升 3.064378 / 5.064378 / 7.064378，各连续保持 200 tick 后发射；`ground-native-final-r1.log`，使用随包库 |
| Sable 相同紧密土体流程 | 3.016769 / 5.022888 / 7.022888，各连续 200 tick 后显式发射；`ground-sable-r4.log` |
| 贴地双向行走、跨两级半砖 | 79 次承载、传送等待 0、回拉 0，高度误差 0.001676 格；`ground-walking-r5.log` |
| 空心结构 / 外框空域 | 外框内正常行走 28 tick、起跳 1.252203 格；`empty-space-r2.log` |
| 发射后原位置通行及方块交互 | 结构仍运动 28 tick 时穿过旧体积、起跳并操作宿主拉杆；旧索引/旧射线无残留；`moving-origin-r2.log` |
| FULL 非 tick 源格同步对照 | 原 flags 对照格在客户端残留，真实捕获格即时为空；显式发布也修复对照格；`host-block-sync-r2.log` |
| Sable 捕获与节点服务端烟测 | 非零种子三格的逻辑及求解器位置一致、重复 finalize、回滚、三类能力真实精密图捕获/移动/释放通过；`sable-seed-grid-r1.log`。这是内嵌测试玩家，非真实 TCP/键盘输入验收 |
| 16×128 格运动性能复测 | 同此前画面设置平均 805.71 FPS，帧 P50/P95 1.0626/1.9816 ms，服务器 tick P50/P95 5.2712/12.3281 ms；`ground-final-moving-perf-r2.log`。不推导 Sable 性能倍率 |

上表日志均位于忽略目录 `build/`。主 JVM 测试 2,849、FFM 原生适配 27、编辑器 104、Sable 兼容 16，共 2,996 项，失败/错误/跳过均为 0；Rust 18 项通过。使用 JBR 25 运行 `.\gradlew.bat test nativePhysicsTest -DisDev=true`、`.\gradlew.bat test build -DisDev=true`、`.\gradlew.bat build -DisDev=false`，日志分别为 `ground-final-tests-r2.log`、`ground-final-build-dev.log`（67 秒）、`ground-final-build-release.log`（63 秒）。之后仅更新文档。开发和发布 JAR 内的 Windows native envelope 解码校验一致，SHA-256 为 `d4e9e43737708a397dc05b1b9af11279ce2906ae54d97320997c19da8d4a80ee`；校验报告 `ground-final-bundle-checks.json`。

未覆盖或未闭合的边界：原 `test2` 故障发生瞬间没有重现，不能将以上夹具当作现场唯一根因证明；Sable r3 曾出现额外发射及新会话，未留下调用源，r4 全部命令均由夹具显式发出，不能推断旧轮次是用户输入。实际本地 512 格容量、海量结构及大关节装配显式 setPose 的缓存重建成本仍待验证。本地三档本轮均为 128 格，Sable 为 128/256/256 格；本轮不是等规模 Sable 性能比较。原存档未修改，未创建 Git 提交。重新启动开发客户端或使用本轮新构建产物方能加载改动。

## 2026-10-20 test2 渲染、植被抬升与挖掘事件

原 `test2` 客户端保持运行，通过只读 JFR 与独立存档副本定位。主要渲染热点是逐方块 `MovingBlockFeatureRenderer` 提交导致 `PreparedRenderType.equals` / 绘制列表线性查找反复执行，现场占渲染执行采样约 87.5%。合批覆盖普通、cutout 和透明材质；同场景数字、光照回归处理、截图与验收边界见 [test2 性能诊断](sublevel_render_performance_2026_10_20.zh-CN.md)，不能将纯发光石基准或暂停渲染倍率当作 Sable 物理求解对照。

H 的空碰撞植物选取、抬升前软障碍清理及扫描预算已修复。原扫描用结构原点大球覆盖纯平移，预算耗尽前尚未到达叶盖；改为真实端点碰撞箱范围并保守覆盖旋转过程。`vegetation-r4` 验证三档 3.064/5.064/7.064 格、各连续 200 tick、叶破坏与掉落事件及发射。旧后端另外通过 29 种植物 145 个状态、536 个不安全状态的准入正负检查。见 [植被抬升回归](sublevel_vegetation_lift_2026_10_20.zh-CN.md)。

挖掘恢复左键挥手、声音和粒子，修复终止回执未清除旧会话导致后续挖掘卡死；移除材料/工具白名单，保留原版硬度、工具、保护和真实方块实体规则。掉落使用原版 `playerDestroy` / `BlockDropsEvent`，输出从真实 plot 一次转换到宿主。真实客户端铁块回归与两个独立 GameTest 通过；受保护的附属余格仍可选取，异常输出保存恢复证据而不自动重放。见 [挖掘与全局掉落回归](sublevel_mining_regression_2026_10_20.zh-CN.md)。

**最终验收**：`test2` 相同视角 9.1208→187.8254 FPS（约 20.59 倍），帧 P50 108.7518→5.1187 ms、P95 116.7993→6.4362 ms；最终 `after-r5` 截图黑区消失，实际顶点 UV/光照/位置对照通过。初版 239.88 FPS 有 UV 错误，不作为验收数据。16×128 格持续运动回归 367.40→486.75 FPS、服务器 tick P50 5.7666→5.5034 ms，仅作为该夹具回归，不推导 Sable 性能倍率。

最终主测试 2,840、原生 JVM 27、编辑器 104、Sable 兼容 16，共 2,987 项 JVM 测试，失败/错误/跳过为 0；Rust 13 项通过。开发与发布 `build` 均成功（各 37 秒），日志 `build/structure-fixes-build-dev.log` / `build/structure-fixes-build-release.log`。真实客户端另有 `build/sublevel-vegetation-r4.log`、`build/sublevel-mining-r2.log`、`build/test2-scene-after-r5.log`、`build/multi-after-r1.log`；GameTest 为 `build/sublevel-mining-gametests-r2.log` 和 `build/native-mining-policy-r2.log`。轮廓发光分支最后恢复为原版逐格路径，编译通过，但其新增客户端断言未单独重跑；未把它列为实机验收完成。其余未覆盖范围见各专项文档。本轮未关闭或热替换原 `test2` 客户端，需重新启动游戏才能加载修复代码；未创建 Git 提交。

## 2026-10-20 退出旧世界后创建新世界崩溃

20:56 服务端报告确认矢量反射的全局锚点持有旧集成服务器的时间豁免，在新世界同 UUID 玩家 tick 时跨服务器关闭句柄。此前退出清理中的 `Temporal runtime has stopped` 与此同源：Mixin 直接调用被权限守卫视为外部调用，停止未执行、退出反而继续维护。已将反射运行时归属各自服务器，在服务停止前释放，并把玩家退出清理移入 Academy 的实际生命周期入口。具体证据、验证入口及本轮结果见 [世界切换生命周期报告](reflection_world_switch_crash_2026_10_20.zh-CN.md)。

## 2026-10-20 固定铁块观察与冻结结构恢复

详见 [test 世界定点铁块崩溃排查](structure_look_crash_audit_2026_10_20.zh-CN.md)。用户确认目标为原玩家位置上方倾斜悬空单铁块，未开编辑器。新证据是 `run/hs_err_pid53656.log` 的 G1 原生退出；用户给出的 9 月 crash-report 属于旧编辑器 NaN 坐标异常。

已确认该铁块 `860aa2e6-7a95-4f90-ac1a-98cdc3ba5b00` 属于旧 `academy:native_level`，因 H 投掷区域跟随失败后继续越界，被保留在最后姿态且 `resume_allowed=false`。这解释冻结，尚不能解释观察退出。原存档未修改；隔离复测、覆盖限制和独立发现均在上述报告中。没有以删除结构、关闭碰撞或改 GC 作为修复。

用户后续确认不再崩溃，但仍无法破坏、推动且悬空。新增整组恢复事务与启动退避重试，保持结构身份/方块/姿态，清除旧速度后重建物理；另修复铁块遗漏于破坏白名单，以及 H 跟随失败仅释放控制却保留越界惯性的问题。恢复区域首选纵向 24 格、水平按实际范围每侧留 3 格，避免两组旧区域互相挤占旋转余量。

最终 dev/release 构建、2,830 项主测试、27 项原生 JVM 测试、13 项 Rust 测试及铁块挖掘策略 GameTest 均通过。`legacy-frozen-iron-smoke-r3` 两阶段副本验收：铁块自然下落 4.723 格、推动 0.770 格；保存重进时两组区域正常恢复，目标再次可推动、长按左键有破坏进度、恰好掉落一个铁块并消耗一点铁镐耐久。原 test 的 164 个文件 SHA-256 未变。详细证据与未覆盖范围见报告末节；不要据此扩大为全部技能、全部节点或无限区域运动已验证。

## 2026-10-20 Sable 对照与 v2 骨架（当前，业务待实现）

用户要求先设计骨架。新增公开 `StructurePipeline` 七个端口、带 `// [TODO: Agent B] PIPE-01..07` 的 `SubLevelPipelineDraft`、仅作发现注册的 bootstrap、资源模板和 `verifyStructurePipelineDraft`。`GAMEPLAY_ENABLED=false`；没有替换现有 tick、网络、存档或物理 ABI。实现顺序、方法契约和完成判据见根目录 `FILL-SPEC.md` 新增首节。

方案：[structure_pipeline_v2_plan.zh-CN.md](structure_pipeline_v2_plan.zh-CN.md)。对照报告：[structure_parity_baseline.zh-CN.md](structure_parity_baseline.zh-CN.md)。测量工具在 `validation/structure_parity/`，通过固定 Sable runtime 和完全不加载 Sable 的 local runtime 串行创建新隔离世界。客户端帧率、复杂 test 世界、真实 H/节点完整流程仍为 UNVERIFIED。

额外发现：原 H 128 格清单在 Sable 捕获桥中出现 X/Z 各约 -4 格的初始质心偏移；不得将该组当公平碰撞基线。最终改用首格为局部原点的 8×2×8 清单，并强制记录质心对齐。这个桥接异常仍待修复，不能由基准夹具调整掩盖。

许可只记录实查边界：Sable 主体 PolyForm Shield、Companion MIT；本轮未复制外部业务源码/二进制，保留固定输入与禁止捆绑检查。

## 2026-10-20 H 地面提取与性能诊断（上一轮，修复待实施）

用户报告创建卡顿、短暂不可见后落回、200 tick 悬浮无效、发射缓慢及与 Sable 差距大。本轮保留生产代码，使用 `run/native-sublevel-direct-smoke-kinetic-analysis-r1..r6` 的独立 test 副本、临时诊断类与 JFR 定位。临时源码/入口位于 `build/kinetic-analysis-src` 与 `build/kinetic-analysis.init.gradle`，不属于发布产物。原世界 160 个数据文件哈希未变，见 `build/kinetic-analysis-source-check.json`。

### 已确认：地面多盒接触卡死，抬升从未完成

- r2 经真实 `KineticThrow.Server.startCharge/releaseCharge` 入口创建悬空平台（实际 9 / 52 块），正常升高约 3.064 格，`lifted=true`，到位后保留完整悬浮窗口；到期释放后才下落。200 tick 常量与到位后续期分支本身有效。
- r3 对完整石质地面执行三档真实 H 提取，三次均捕获 128 块；`LIFTING/controlActive=true`、必要地形覆盖完整，但约 200 tick 内只移动几千分之一至几百分之一格，始终 `lifted=false`。因此不会进入“到位后再悬浮 200 tick”，而是耗尽创建时设定的 200 tick 初始期限后静默释放。
- r4 排除源块残留和上方阻挡：128 个源格均为空，向上 3 格的路径无剩余碰撞方块；强制失效重建地形、整体上移 0.1 格均不能解开。r5/r6 确认附近无其他结构干涉；把同一 128 格结构与 717 个宿主方块放入独立原生物理世界仍复现。
- r6 关闭摩擦、关闭 CCD 均不能恢复抬升。各盒缩进 0.05 格可恢复，但该试验也改变质量，故又补了独立的等质量对照：所有变体质量保持 128、控制参数/重力/地形/步长相同，模拟 200 tick。结果见下表及 `build/kinetic-socket-mass-control.log` / `build/kinetic-socket-mass-control-summary.json`。

| 每个动态碰撞盒的单侧缩进 | 200 tick 后原点 Y 位移 | 结论 |
| --- | --- | --- |
| 0 | 0.000312 | 卡在原坑 |
| 0.01 | 0.016545 | 仍卡住 |
| 0.02 / 0.03 / 0.05 | 3.064378 | 正常抬升 |

定位为**逐方块碰撞盒与宿主坑壁紧密贴合时的接触约束/容差适配问题**。`native/academy_physics/src/lib.rs:create_body` 为每个 cuboid 建一个 Collider；`PlotGeometry` 只有完整体积 ≥512 才合并，H 本地最多捕获 128，实际始终使用密集逐块 collider。所用 Rapier 0.36.1 默认 `normalized_prediction_distance=0.02`、`normalized_allowed_linear_error=0.005`，与对照阈值相符；尚未逐接触导出法线/冲量，不能声称已证明引擎本身存在 bug。缩进试验只是定位证据，不能直接对所有方块永久缩小碰撞箱，否则会影响薄块、放置、站立与外观一致性。

相关状态缺陷：`beginSubLevelLift` 从创建时设置 `expiresAt=now+200`，只有 `measureCompletion(...).reached()` 才转为到位后 200 tick；未到位超时走普通 terminate，没有明确的抬升受阻原因。需要拆开抬升期限/悬浮期限并保留诊断。`SubLevelDirectControl` 按外层 50ms 一次冲量，Sable 牵引按子步重算，当前本地悬浮还存在约 +0.064 格的离散偏差与发布速度约 -0.2146 格/秒的相位偏差。

### 已确认：卡顿主要发生在 Java 主线程的地形构建与同步落盘

- r2 JFR：服务器线程 2155 个执行采样中，1779 个包含 `TerrainSectionGeometry.merge`（约 **82.6%**，这是执行样本占比，不是总墙钟时间比例）。25 个记录到的慢 tick 为 116–1704ms。汇总见 `build/kinetic-analysis-summary-r2.json`，原始录制在 r2 目录的 `kinetic-analysis.jfr`。
- `TerrainSectionGeometry.merge` 对最多 4096 格生成的盒反复双重遍历，合并一次就从头重扫；最坏可达立方复杂度。宿主方块变动会使整个 section 及必要邻段重建；当前没有按时限分摊这段工作。
- r2 真实 H 释放调用：9 块约 **658ms**，52 块约 **312ms**；r3 的 128 块捕获约 **654 / 316 / 244ms**。`StructureCaptureCoordinator.drive` 可在同一次调用推进全部阶段，`SubLevelRuntime.finalizeStaged/flushStorage` 及 `SubLevelCaptureTarget.save` 在服务器线程同步保存 plot 维度、manifest 和宿主维度。JFR 捕获到 `flushStorage` 单次等待约 **183ms**，另有多次 85–124ms 的保存等待。
- `SubLevelSimulationBudget.plan` 的结果在 runtime 中仅用于记录/日志，未驱动实际任务执行或延期；6ms 参数不是执行时间上限。不能把“有预算类”写成已实现帧预算调度。

### 显示空档、速度和 Sable 差距

- 源方块移除包先发送，之后仍需同步 save，投影全量快照在 `SubLevelServer.sendSnapshots` 才发送。链路存在可确认的显示空档；具体不可见帧数尚未采集，不能把用户看到的全部消失时间都归因于这一项。
- H 当前给 SubLevel 设置的发射速度是 **50 格/秒**，同 Sable 的目标标定；`SubLevelMotionController` 的 8 格/秒限速不是 H launch 路径。服务器每完成一个 tick 只推进 0.05 秒物理，单 tick 卡住几百毫秒到 1.7 秒会直接形成现实时间里的慢速与停顿。客户端姿态包只有位姿，没有服务端时刻/速度；单 tick previous/current 插值无法平滑主线程长时间不发包，停顿后会跳到新目标。
- `BatchedBlockGeometry.eligible` 要求可见 cells ≥512，而 H 本地预算只有128（通用 SubLevel 捕获为256）；这些技能结构永远进不了该批量路径。每帧仍逐块提交模型/采样光照。r2 渲染线程2812个执行样本中239个涉及逐块宿主光照取样；存在成本证据，但没有 Sable 同场景配对数据，不能给出性能倍率。
- 新 SubLevel 的共享物理、地形构建、承载、软块扫描、同步都在服务器主线程；一处必要地形不足会暂停整个宿主物理。原日志另有旧 `academy:native_level` 区域跟随失败、离开有界区域后停止，属于旧后端独立缺陷，不能混作新 SubLevel 的限速。

### 修复顺序与验收要求

1. **P0 地面提取**：把本轮等质量原生坑体对照和三档真实 H 地面案例固化成回归；处理逐块内部接触面、接触预测距离与实际可行抬升路径，测试几何合并/接触参数方案；完整保留地面与墙体碰撞。区分抬升受阻、控制失效、抬升超时和完整200 tick悬浮，不能靠全局关闭碰撞或延长常量掩盖。
2. **P1 主线程成本**：用确定性的体素贪心合并/轴向索引替换全表重启双循环；按脏格/脏段更新，真正使用执行预算；将捕获持久化改为有完成回执的分阶段写入，保留 journal 与崩溃一致性，不在一次 H 调用保存整个维度；协调源块移除与客户端投影发布。
3. **P2 运动与绘制**：接入子步控制，增加带服务端时刻的姿态历史与有界插值；为128格及不规则结构建立可复用表面网格，避免逐块每帧重算；完善必要地形准备与局部暂停策略。
4. **验收**：三档实体地面提取→3/5/7格→到位后完整200 tick→转向H发射；记录首个可见帧、创建各阶段耗时、逻辑/墙钟飞行距离、接触轨迹；再与 Sable 在相同机器/地形/方块数对照 p50/p95、包量与帧耗时。

本轮没有修改生产实现或宣称修复完成。原先 r8 的落地/撞墙/F3+B 专项仍是有效的局部证据，但没有覆盖此次真实 H 从密合地面拔出的过程；下方历史通过记录不得替代该回归验收。

## 2026-10-20 碰撞回归修复（上一轮，专项验收通过）

用户再次报告“F3+B 外层大框仍存在、移动卡顿、结构穿透宿主方块”。本轮确认并修复的路径如下：

- **宿主地形**：多结构 union 把远处结构之间的空区纳入有限 section 预算，旧虚空结构也会扩大范围。`SubLevelRuntime` 改为每结构下一 tick 的平移/重力/旋转扫掠范围；`TerrainColliderCache` 先安装 required sections，空 section 不占刚体预算。必要覆盖不全时暂停物理步并报告原因，不能继续在缺失碰撞器的世界推进。
- **必要区块票据与预算**：仅对 required section 对应的去重 chunk 使用独立 `sublevel_collision` FULL 票据，不覆盖 halo/结构间空区，不请求 simulation/persist；每宿主最多 64 个，每 tick 最多同步加载 4 个，离开需求或关闭时释放。原 test 的 14 个结构暴露旧 16 solid section 上限不足，默认必需容量改为 64；可选 halo 只在总数不足 16 时预热，28,000 盒及原生刚体硬上限保留。超过上限仍暂停整个宿主物理并报告，不能把缺失地形当空气。
- **客户端时钟**：旧碰撞由渲染主循环按 25% 追目标，模型五 tick 插值、选取最新目标，三者不同步。改为每服务器 tick 仅发送变化姿态；包只更新 pending target，实际游戏 tick 在实体移动前锁存 previous/current。碰撞使用 current，模型和选取按 partial tick 插值，与原版实体保持同相；不依赖世界时间在 tick 内递增的顺序，静止时消费上次位移。
- **F3+B**：新增 `MixinEntityHitboxDebugRenderer`，仅跳过所有 `NativeStructureProjectionEntity` 的原版通用大框，包括旧 native 非 clientOnly 投影。实际 bounds、碰撞及 culling 不变，其他实体调试框保留。SubLevel clientOnly 投影不再进入旧碰撞索引/支撑判断，局部几何不随姿态包重建。
- **OBB 与承载**：修正 SAT 近平行轴、非单位交叉轴深度及重叠后向外/沿表面移动；支撑使用真实脚底 footprint 和朝上接触法线；台阶、去穿透、承载均受宿主墙/顶棚裁剪。服务端用独立 `carryPose`，在 finally 消费搭载位移，保留 `previousPose` 供延迟交互校验；每个结构独立查询附近实体并跨结构去重，避免全局大范围查询与重复承载。

**最终测试与构建**：普通 2760 项、原生 27 项、Sable 16 项、编辑器 104 项，共 2907 项 JVM，失败/错误/跳过均为 0；最终开发构建同时执行 Rust，13 项通过。最终开发与发布构建分别在 49 秒、46 秒成功，日志为 `build/sublevel-collision-repair-build-dev-final.log`、`build/sublevel-collision-repair-build-release-final.log`；产物 SHA-256 见 [FILL-SPEC.md](../FILL-SPEC.md) 第 3 节。

**真实客户端 r8 通过**：仅使用 `run/native-sublevel-direct-smoke-collision-repair-r8` 内的 test 副本，无 Sable。恢复 14 个既有结构后，通过生产捕获事务新增两个相距 80 格的 8 方块结构，缓存实际使用 28 sections / 1052 boxes。两个结构落定底面为 `247.99998537` / `247.99999893`，石台顶面 248；实体移动被侧面阻挡且可反向离开，能落在结构顶面。50 格/秒撞墙最大 X=`-185.00439051`，墙面 X=`-185`；拆上层地板后第二结构下落 `4.00001698` 格并落定下层石台。日志 `build/sublevel-collision-repair-client-r8.log` 包含实体/地板/墙/拆地板全部 PASS 及最终 `SUBLEVEL_COLLISION_REGRESSION_PASSED`。

**F3+B 截图已查看**：`run/native-sublevel-direct-smoke-collision-repair-r8/screenshots/sublevel-collision-regression-1792434640891.png`；铁块结构无外层实体框，周围普通实体仍显示白/红/蓝调试框。客户端观察 225 tick，日志断言 `hitbox_debug_enabled=true`。原 `run/saves/test` 的 160 个数据文件与本轮开始基线 SHA-256 全部一致（排除锁文件），证据 `build/sublevel-collision-source-world-hash-check.json`。

**现场另见两个既有问题**：旧 native 区域 `9060e07a-0406-4b0b-a987-509cbad61cb0` 在存档中已为 FAILED，仍拒绝自动重组，未删除附件；r8 碰撞/截图通过后退出时，矢量反射在时间运行时关闭后清理免疫触发 `Temporal runtime has stopped`。这是独立退出清理问题，不能将本次称为“退出无异常”。详细失败夹具轨迹与边界见 FILL-SPEC 第 3 节。

**剩余验收**：必要区块覆盖、缺地形暂停/恢复已有回归证据；票据释放、64 chunk 超限等完整生命周期实机矩阵仍待补。空心/旋转平台与真实玩家轨迹、多人交互、3/5/7 悬浮及软块完整矩阵、Sable 配对性能仍 **UNVERIFIED**；本次专项通过不替代五项整体测试。未创建 Git 提交。

复验命令（JBR 25；每轮新副本）：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat verifySubLevelScaffold test nativePhysicsTest sableCompatTest editorTest testNativePhysicsRust build -DisDev=true @physicsNativeArgs
./gradlew.bat build -DisDev=false @physicsNativeArgs
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name collision-final-rN
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/sublevel_collision_regression.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-collision-final-rN @physicsNativeArgs
```

专项应出现 `SUBLEVEL_COLLISION_FLOORS_PASSED`、`SUBLEVEL_COLLISION_WALL_PASSED`、`SUBLEVEL_COLLISION_MUTATION_PASSED` 与最终 `SUBLEVEL_COLLISION_REGRESSION_PASSED`，并查看截图；覆盖相距 80 格的地板、50 blocks/s 撞墙、地形删除和客户端投影。详细根因、边界与剩余五项验收见 [FILL-SPEC.md](../FILL-SPEC.md)。

## 2026-10-20 后续五项问题实现（初次实现基线）

用户追加的五项需求——本地结构性能、旋转 AABB 接触抖动、完整挖掘/掉落/放置/交互、按选区抬升 **3/5/7 格**、运动破坏草/叶——已从骨架补成实现并接入运行路径：

- 实体碰撞不再向宿主 `Level` 注入结构 AABB；`Entity.collide` 以真实 OBB 做窄相裁剪、台阶与有界去穿透，客户端使用局部盒与插值姿态。
- `SubLevelRuntime` 只对真实姿态/几何变化更新碰撞与清单；地形缓存按需刷新、全休眠跳过求解步；快照改为“首次/几何变化全量 + 后续姿态差量”，并只在变化时发送。
- 挖掘改为服务端权威会话（START/CONTINUE/ABORT、硬度/工具速度/裂纹/耐久/loot/XP/一次结算），放置使用局部空间 `BlockPlaceContext`，旧 BREAK 只续约/启动会话，不再瞬破。
- `KineticThrow` 抬升档位改为 3/5/7；`SubLevelKineticLiftProfile` 以源位姿×局部质心为基准、实际世界质心验收（容差 0.1）、首次到位锁存 200 tick；PD 目标加入重力前馈。
- 软块扫掠、授权、预算与掉落已实现；`structure_motion_breakable` / `structure_motion_protected` 已填入草/叶/保护项。

**初次实现基线：IMPLEMENTED；实机验收 UNVERIFIED。** 当时编译、`verifySubLevelScaffold`、2735 项普通测试、26 项原生、16 项 Sable、104 项编辑器、13 项 Rust 与开发/发布构建均通过；`runClientDev` 到主菜单启动成功但未进入世界。用户后续报告的碰撞回归及当前验证状态以上一节为准。这些历史结果不构成五项整体验收；尚未采集 Sable 配对性能数据，也未完整实测交互、3/5/7 格与草叶破坏。完整文件索引、逐项边界和复验命令见仓库根目录 [FILL-SPEC.md](../FILL-SPEC.md)。此前“只写骨架”的段落已由实现记录取代。

## 2026-10-20 普通 test 退出追查与 H 悬浮操作修复（上一轮）

用户在修复后的普通 `run/saves/test` 推动旧结构、创建新结构时报告游戏窗口直接退出。已保存现场到 `build/crash-evidence-20261019-2338/`，后续复现仅使用 `run/native-sublevel-direct-smoke-crash2338-*` 副本。

- 游戏 `debug.log` 与 Gradle daemon 47572 的输出均在 23:38:21 中断，没有 Java 崩溃报告、新 `hs_err`、原生错误或正常关闭尾部。Windows System 事件 43 在同秒记录开始安装 `OpenAI.Codex` 更新；23:38:22 沙盒服务收到停止请求并停止，23:38:23 新服务启动，23:38:24 事件 19 记录安装成功。现有证据更支持外部服务重启终止进程树，尚不能把该次直接退出归因于物理求解器。
- 日志另外确认一个独立功能缺陷：旧结构 `f4628c1c-15e4-4f8a-9b23-22dea15cce2e` 原位置接入物理区域报 `Physics region conflicts with live or saved reservations`。此前仅复用完全包含结构的区域，遗漏部分重叠的区域。
- 原地复现 r1 进一步确认两个已有区域都处于 `PAUSED/unsupported_content`：宿主短草被旧碰撞采样白名单拒绝。区域确实已自动恢复注册，不能把 `activeRegion` 查不到误判为“未自动恢复”。原地采样兼容与相邻区域接入都须修复。
- 现场成功创建了 55 与 81 方块的 SubLevel，并完成多次 H 投掷及 81→80→79 方块客户端全量同步后才中断。此前直接技能主要在高空以少量方块测控制，旧结构测试还调整过场地；其通过结果不能替代这个原地、地面、多方块场景。

本轮保留所有原结构和区域身份，允许在检查全部成员、控制租约、24 格轴长和相邻区域间隔后，重绑并复用部分重叠的已有区域；恢复中的区域按明确状态接入，真正失败或内容不受支持的区域仍拒绝。重绑先完成持久化，再释放旧物理世界；真实写入失败时原刚体、速度与控制可用性保持不变。

旧地形采样现在略过审计过的原版无碰撞植物，以及没有固体碰撞的水/岩浆；水浸台阶、楼梯、栅栏采样实际固体形状。不增加浮力，结构内容捕获的流体限制不变。H 网络入口按玩家和服务端 tick 去重，并明确保证自身处理器注册幂等；现场同 tick 四次 H 可能来自卡顿积压，不能仅凭该日志认定重复注册。

用户追加 H 操作合同：新建结构从源方块原位抬高，抬升完成后悬浮 200 tick（正常 20 TPS 下 10 秒），允许转向后再次 H 投掷。手动托举有独立 `manualHover` 标记；第二次 H 优先该会话，不再要求看回结构，程序成功复用手动会话时清除该标记，接管失败则保留原手动状态。SubLevel 抬升目标以源格最小坐标加局部质心为基准，避免事务完成前的物理步改变悬浮 X/Z。超时处置保留既有规则。

### 本轮验证

- 原 `test` 的 r3 副本：`build/sublevel-crash2338-ground-r3.log`，`SUBLEVEL_GROUND_SMOKE_PASSED`。两个旧区域都是 READY；原 59 方块结构保持原姿态接入同一区域，H 后实际移动约 2.4 格，速度约 49.8 格/秒，59 方块保留。
- 真实客户端分别捕获 55/81 方块并按 H 推动，运行至少 140 服务端 tick，完成 81→80→79 两次内容/刚体重建、7 次宿主地形修改及客户端碰撞/内容同步；同一 activation 保留，未再出现窗口直接退出。
- 真实 H 蓄力/释放创建 9 方块结构，源中心 `(-113.5,66.5,123.5)`，稳定悬浮中心 `(-113.5,70.38044,123.5)`；到位后保持 199 tick，期间转身，最后再次 H 得到沿新视线的 `(0,0,-50)` 速度并继续飞行。无旧结构基线也通过，日志 `build/sublevel-crash2338-ground-fresh-r1.log`。
- `NATIVE_TERRAIN_ADMISSION_PASSED`：17 种植物、6 种纯流体状态、3 种水浸固体；空碰撞内容改成石头会使缓存失效并产生碰撞，粉雪/火/蜘蛛网仍保持明确拒绝。
- `SubLevelDenseTerrainStressTest`：真实 DLL 下 55/81 方块 50 格/秒碰撞，动态刚体及地形各重建 100 轮，共 820 tick，通过且无刚体泄漏；`build/sublevel-crash2338-native-stress.log` 及 nativePhysicsTest XML 留存。
- 区域保存/重启：`run/native-structure-physics-regions-20261020-rebind-r2/` 的 prepare/resume 结果均 `PASSED`；包含真实持久化失败注入、自动恢复身份/边界、首步速度、独立暂停/恢复及关闭隔离。旧 resume 夹具原本假设启动不会自动恢复，已按当前合同修正，未关闭生产自动恢复。
- 最终完整客户端矩阵：`build/sublevel-crash2338-matrix-final-r2.log`，三族直接技能、六张实际保存/执行的程序图、旧结构共享区域、三宿主地形及内容事务通过；最终 `SUBLEVEL_CLIENT_SMOKE_PASSED`、快照清理与正常退出均通过。截图 `run/native-sublevel-direct-smoke-crash2338-matrix-final-r2/screenshots/sublevel-client-smoke-47472-1792426769051.png` 已查看，角色存活，目标结构清楚可见。
- 完整测试：2722 普通、26 原生、16 Sable、104 编辑器，共 **2868 项 JVM 测试**，失败/错误/跳过均为 0；Rust 13 项通过。最终代码开发/发布构建均通过，分别见 `build/sublevel-crash2338-build-dev-final-r2.log`、`build/sublevel-crash2338-build-release-final-r2.log`。三族完整矩阵见上项；未改生成资源。

矩阵首轮的三族/六图本身均通过，最后截图因夹具按方块数选中原世界旧 55/81 方块结构而超时；夹具现按本次捕获的唯一 plot 和投影视图匹配，不再假设世界中只有一个结构。首轮另外出现一次 NeoForge 空维度附件 `data_attachments.dat` 原子替换拒绝，一秒后成功写入；当前日志没有并发覆盖或结构数据丢失证据，最终 r2 未再出现。没有将这些夹具/文件事件认作用户窗口退出根因。

原 `run/saves/test` 当前 **151 个文件**与本轮开始保存的 SHA-256 基线全部一致。此前记录的 140 文件是上轮基线，用户再次游玩后的本轮基线为 151，不能混用。所有测试只修改隔离副本；未提交。

当前开发产物 `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar`，SHA-256 `8B17AC93F087486C5BAB0688861E04410346D66AE865D515F5F11E24A809FF79`；发布产物 `build/libs/academy-26.2.0-0.0.4-alpha-release.jar`，SHA-256 `48CFE6A26EB4F3AFDE5FBEECABDB177DB19C7D2DDC9196B614DADDCB1872476F`。下方上一轮产物哈希仅作历史记录。

复现地面与悬浮场景：

```powershell
. ./build/native-physics-p16-env.ps1
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name ground-new
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/sublevel_ground_skills.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-ground-new @physicsNativeArgs
```

## 2026-10-19 默认物理化修复（上一轮实现与验收）

本节覆盖下方历史记录中“两个捕获开关默认关闭”的结论。用户已授权修复；无 Sable 的普通启动现在默认通过公共事务创建 SubLevel，三族直接技能及提取节点不再读取 `academy.sublevelDirectSkills` / `academy.sublevelProgramNodes`，也不再受旧四槽占满阻塞。修改基于已有未提交迁移继续完成，尚未创建 Git 提交。

### 已完成修复

- `StructureBackendRouter.captureTarget(host, physical)` 统一新建后端；无 Sable 的物理捕获使用 `SubLevelCaptureTarget`，非物理捕获和已有旧引用保留原后端。Sable、INCOMPATIBLE、MIGRATION_REQUIRED 的原有选择与拒绝规则保留。
- SubLevel 接入公共权限复核、源格 claim、复制核对、journal 与回滚。暂存 plot 不公开操作；目标内容落盘后才决定移除源块，重启前向恢复再次核对副本。PREPARED 使用事务 ID 找回空 plot，回滚后才能释放；不完整回滚保留 plot、claim 与 journal，并隔离物理、碰撞、投影和交互。
- 每个宿主维度独立物理世界，共享唯一 plot 分配器；启动先保留全部 manifest 的 plot，包括暂不可用宿主。主世界 manifest 沿用原路径，客户端快照带宿主维度，客户端碰撞默认开启。恢复事务主动读取有限源区块，宿主/plot 保存失败立即隔离副本，不再让未完成事务继续接受操作。
- 铜块、铁栏杆、普通有碰撞的模组方块不再被旧 vanilla 白名单拒绝；公共捕获仍明确拒绝不能保存的 BlockEntity、流体、水浸及待执行 tick，以及不可破坏/空碰撞方块。具体技能原有选区、磁性比例和完整方块要求仍各自生效。
- 地形碰撞缓存响应宿主方块新增、移除和捕获移除，连带使邻接区段失效；新放入原空区段的地形不再等待旧重试周期。
- 旧静态 native 结构可原位接入物理区域后由 H 投掷；可复用包含它的就绪区域，存在有效控制租约时明确拒绝，原内容、引用与 claim 保留。直接技能关闭只撤销自己的控制，保留结构与惯性。
- 新增公共结构姿态与局部锚点构造节点，现有三族节点 ID、端口和旧图格式保留。三族程序独立保存捕获事务 ID，异步服务会话结束后仍能报告原始失败原因。
- 磁力 R 操作原本按 COLLIDER 选取，却按 OUTLINE 复核，导致无碰撞前景也能错误阻挡接管。`StructureQueries.validate` 增加形状重载并贯通三后端；旧签名仍使用 OUTLINE，R 操作选取与复核都保持原 COLLIDER 语义。

### 最终验证证据

测试配置 `validation/native_structure/sublevel_default_skills.init.gradle` 不开启两个旧捕获开关、不关闭客户端碰撞；`prepare_default_skill_smoke.ps1` 只在独立目录准备副本。Gradle 退出码不能代替 smoke 标记。

| 验证 | 结果与证据 |
| --- | --- |
| 用户 `test` 副本，旧四槽已占满 | `build/sublevel-default-client-test-r7.log` 出现 `SUBLEVEL_CLIENT_SMOKE_PASSED` |
| 干净基线，无 Academy 结构存档 | `build/sublevel-default-client-fresh-r2.log` 出现相同完整通过标记 |
| 最终副本真实关闭再启动 | `build/sublevel-default-client-test-r7-restore.log`：`SUBLEVEL_DEFAULT_RESTORE_PASSED`、`SUBLEVEL_MULTIHOST_RESTART_PASSED`、`SUBLEVEL_CAPTURE_RECOVERY_RESTART_PASSED` |
| 完整测试与开发构建 | `build/sublevel-default-build-dev-final.log`；`test nativePhysicsTest sableCompatTest build -DisDev=true` 通过 |
| 发布构建 | `build/sublevel-default-build-release-final.log`；`build -DisDev=false` 通过 |

两次完整客户端均覆盖：三族跨 tick 控制、直接动能托举/H 投掷、喷气撞击/旋转再激活、磁力拉取/接管/释放；旧静态 H 物理化、共享区域租约保护；三维度地形缓存、权限撤销、源格变化回滚、claim 冲突、目标物理失败清理，以及铜/铁栏杆/模组块捕获。

每次保留五个同时存在的新 SubLevel，并在旧槽满时执行六张图（三族各两张），通过真实保存包、执行包、编译器及 scheduler 运行 extract → control → move → wait → rotate → launch → release → wait → staticize，检查运动、控制权、惯性、身份与内容恢复。磁力追加蜘蛛网前景负例与 R Start/Stop 服务端处理器跨 tick 验证，标记 `CLIENT_SUBLEVEL_MAGNETIC_R_PACKET_PASSED`。

重启检查主世界、下界、末地共六个结构的同一身份、plot、内容和继续控制；另在远区各保存 PREPARED 与 COMMIT_DECIDED 记录，重启自动回滚三个空 reservation、完成三个前向恢复并清理日志。此项是持久化阶段注入后真正重启，**不等同于随机强杀进程或硬盘故障注入**。

普通 JUnit **2719** 项、原生专用 **25** 项、Sable 专用 **16** 项、编辑器 **104** 项，共 **2864** 项 JVM 测试，失败/错误/跳过均为 0；Rust 原生测试 **13** 项通过。语言键排序的早期失败已由最终结果覆盖。

最终截图已查看，角色存活、无菜单遮挡，现有物理结构在空中清楚可见：

- `run/native-sublevel-direct-smoke-default-test-r7/screenshots/sublevel-client-smoke-35908-1792423915060.png`
- `run/native-sublevel-direct-smoke-default-fresh-r2/screenshots/sublevel-client-smoke-4652-1792424001263.png`

原 `run/saves/test` 的 140 个文件与首轮记录的 SHA-256 一致，原世界未改动。未改 `src/generated/resources`；三个既有无关 VFX 脚本不属于本次修改。

开发产物 `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar`，SHA-256 `2B45EFF4EC63AEC44A209596F0A6728DEB2811E12A32E86B9EF4D6424CB6A288`；发布产物 `build/libs/academy-26.2.0-0.0.4-alpha-release.jar`，SHA-256 `B0A5866D74CECFB4EEDB46973B9F9EC647BD472DC1E224887A0318CBF9D06475`。未部署到其他整合包。

### 复现与边界

本机使用 JBR 25 与已配置的原生构建环境；新名称必须尚不存在。完整运行后在同一副本追加 restore probe：

```powershell
. ./build/native-physics-p16-env.ps1
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name default-test-new
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/sublevel_default_skills.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-default-test-new @physicsNativeArgs
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/sublevel_default_skills.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-default-test-new -PacademySubLevelRestoreProbe=true @physicsNativeArgs
```

干净基线准备时加 `-Fresh`。两个客户端均为真实单机集成服自动 fixture：程序图走实际网络包，直接技能包含真实服务端包处理器，未声称人工键盘/滚轮全流程、多人延迟、Sable 四模组混装实机、16k 网络压力或随机强杀全部完成。Sable 的编译、JVM 与打包检查已验证；其混装实机仍待专项验证。staticize 仍按局部格和目标原点写回，不把任意倾斜姿态烘焙成宿主方块。

中间失败记录保留作诊断：r1/r2 喷气测试场地含真实地形，r3 H 和 r4 程序图缺少客户端朝向同步，r5 测试传送未清坠落距离导致死亡画面；最终夹具修复了这些问题，新增有效瞄准、角色存活与无遮挡画面断言。地形缓存与磁力形状校验是独立确认的生产缺陷，不将夹具失败误报为生产根因。

## 2026-10-19 普通 run / test 世界故障复盘与修复计划（历史审阅）

用户在未加载 Sable 的普通 `run` 实例报告三族技能及精密操作节点无法正常创建、操作物理结构。本次只读审阅日志、代码和存档 NBT，未启动客户端、未修改代码或世界；仅补充计划。下面的实验配置测试结果仍有效，但**不能据此判定普通默认启动已经可用**。

### 已确认的失败链路

1. `run/logs/latest.log:3` 确认游戏目录为仓库 `run`；该次 Mod List 没有 Sable。日志 `:298` 显示已恢复两个旧物理区域，`:299` 显示 `SubLevel backend ready ... capacity=4096 stored=0 active=0 restored=0`。因此此次故障不是缺少 Sable、原生 DLL 未加载或 plot 维度未初始化。
2. 普通 `build.gradle.kts:307` 的 `clientDev` 只设置 `IS_DEV`，没有开启 `academy.sublevelDirectSkills` / `academy.sublevelProgramNodes`。三族新捕获入口都由 `Boolean.getBoolean` 守门，未设置即 false。旧引用的查询/控制按 backend 分流，不能把“捕获分支未启用”误写成所有 SubLevel 控制 API 都被关闭。
3. 默认新建继续进入 `StructureTransactions` → `StructureCaptureCoordinator.begin` → `NativeCaptureTarget`。该原生目标仍由 `NativeStructureRuntime.createEmpty` 分配四个独立存储维度；它不使用已经就绪的 SubLevel plot 池。
4. `test/data/academy/native_structures/owner_0.txt` 至 `owner_3.txt` 全部存在，NBT 索引也有四个匹配条目（version=3，均 active=1、quarantined=0）；`sublevels/metadata.dat` 则为 version=1、entries=[]。四个旧槽位会被分配器确定跳过。
5. `run/logs/latest.log:325` 起直接动能投掷反复报 `BACKEND_UNAVAILABLE:no_free_native_slot`；`:389` 起真实精密操作的“提取磁性结构 [#1]”也报相同错误。提取失败后没有可用新结构引用，后续控制链自然无法继续。

| 旧槽位 | 结构 ID | 存储原点 | 已保存物理附件 |
| --- | --- | --- | --- |
| 0 | `860aa2e6-7a95-4f90-ac1a-98cdc3ba5b00` | (-210, 65, 145) | 无 |
| 1 | `f4628c1c-15e4-4f8a-9b23-22dea15cce2e` | (-195, 67, 130) | 无 |
| 2 | `d54a1631-7157-45f2-8ba9-6e712b655756` | (-204, 63, 131) | 有，resume_allowed=1 |
| 3 | `50601f9c-d0cf-4899-8d06-f20e0c6f7849` | (-216, 62, 151) | 有，resume_allowed=1 |

这些条目不是可以直接删除的残留文件。是否为空体、运行时是否持续就绪、已有结构被实际瞄准时的控制结果，仍须在副本中逐项验证；`active` 存档标记与“Resumed”启动日志不等同于完成了控制验收。当前日志直接证明动能投掷和磁提取节点的创建故障；高速喷射及其他提取节点共享同一失败路由，由代码审阅确认，未伪称已有其逐项现场日志。

### 上轮验收缺口

- `validation/native_structure/sublevel_direct_skill.init.gradle` 强制开启两个捕获开关，并从没有旧 native 占用的隔离世界运行，绕过了当前故障组合。
- fixture 直接调用技能服务和 `new Server*ProgramRuntime`，赋予技能与大量资源；没有覆盖普通默认后端选择、真实按键/网络包、已保存程序图、图编译和 scheduler 的完整用户入口。
- 普通启动与测试的行为合同没有被测试锁定。应新增默认路径验收，不能靠再次加 `-D...=true` 或建议安装 Sable 代替修复。

### 修复实施顺序与验收门槛

**P0：统一新建后端选择。** 在公共结构捕获层建立按服务器后端状态和宿主维度选择的单一策略，三族直接技能、三族精密节点及公共捕获 API 共用。无 Sable 时新物理结构默认使用可用的 SubLevel；兼容 Sable 模式继续走 Sable；INCOMPATIBLE / MIGRATION_REQUIRED 保持明确拒绝。旧 `academy:native_level` 引用继续由旧后端解析、恢复和控制。两个实验开关退出生产决策，或仅保留显式诊断覆盖，不能继续让直接技能与节点各自选择后端。

**P0：补齐公共事务与能力检查。** 将 SubLevel 捕获接入统一选择/预检，复核原事务的选区限制、权限、源格占用冲突、内容快照、CP/MP 支付时机、失败回滚及 journal 语义，避免技能直接绕开公共捕获合同。为 plot 未就绪/用尽、宿主不支持、原生库不可用和旧槽位占满返回不同原因；在用户可见错误与诊断中同时显示实际选择的后端。失败不得删除源块或只留下被扣除资源。

**P1：宿主维度与生产配置。** 当前 `SubLevelServer` 只构建 overworld runtime；直接把开关默认改 true 会让 Nether/End 捕获因 host/runtime 不符失败。正式默认启用前，按宿主维度管理 runtime/物理世界/持久化与网络身份，并复核重启、换维度清理；禁止悄悄回退到已经耗尽的旧四槽。同步明确客户端碰撞配置的正式默认值，验证玩家站立/碰撞与服务器一致，不能只沿用 smoke 的 `clientCollision=false`。

**P1：旧内容兼容和可诊断性。** 首先保留 `test` 四个旧结构、claim 和原维度，新捕获不再被它们阻塞。区分没有物理附件的旧结构与已保存物理结构，分别验证查询、控制、恢复和静态化。可另设计受事务保护的迁移或空槽释放，但不将删除 owner 文件、清空维度或扩大旧 SLOT_COUNT 作为本次修复。装 Sable 时现有 native 数据会触发 MIGRATION_REQUIRED，不能用全局默认 true 绕过该保护。

**P0 验收：普通配置和真实入口。** 从用户 `test` 创建隔离副本，记录副本来源与原文件哈希；保持无 Sable、不传两个 SubLevel 强制开关，分别在全新世界和四槽占满副本验证：

- 直接技能：真实蓄力/释放→抬升→H 投掷；喷嘴放置→激活→旋转再激活；磁力持续输入→接管已有结构→释放。
- 三族已保存精密图：经真实执行包、编译和 scheduler 运行目标→extract→control→move→等待→rotate→launch/release→staticize，检查端口、资源消耗、跨 tick 运动和失败提示。
- 保留至少五个同时存在的新结构，再运行超过四次捕获/释放/静态化循环，证明新建不再受四槽限制；重进世界后查询并继续控制，检查身份、内容和所有权守恒。
- 验证原四个旧结构仍可访问；新旧结构的瞄准遮挡、租约争用、过期/断线/换维、释放惯性与客户端投影清理均正常。
- 验证兼容 Sable、不兼容 Sable、存在 native 内容的迁移阻断、Nether/End、后端启动失败的清晰结果。
- 运行普通单测、原生物理与 Sable 专用测试及开发/发布构建；保留上轮底层 smoke，新增不依赖测试开关的默认启动集成测试。报告明确区分服务层、真实图、实际输入和多人验收。

实施完成的判定是普通 `runClientDev` 默认行为和用户存档副本通过上述入口检查；仅底层 smoke 再次通过不能关闭此问题。

## 2026-10-19 操作兼容性审计（实验配置验证，已完成）

基线仍为 `c154e680`；本轮在已有未提交迁移实现上继续修复，尚未创建 Git 提交。以下结论覆盖下方历史快照；三个无关 VFX 脚本不属于本轮。

### 修复范围

- 三族程序 control/move/rotate/launch/release 共用 `StructureDirectControl` 权威租约。补齐 actor、维度、runtimeId、generation、到期与失权检查；撤销待执行推力，旧 release/close 不得撤销后继令牌。
- 恢复旧捕获选区、喷嘴条件、磁性参与率、强磁时限和三族各自的推力/发射速度标定。节点 ID 与端口不变；程序状态由 `SubLevelProgramControl` 维护，力学计算复用公开能力 API。
- 磁力的偏心锚点反馈使用实际点速度（含 `ω × r`）；程序控制提交固定先于物理求解，避免额外一 tick 延迟。
- 绝对轴角旋转与平移保留原刚体、世界质心/速度和 activation；修复休眠后受力不更新姿态、分片体只控制首刚体的问题。
- 公共 resolve/motion/raycast/validate/launch/control 接入 SubLevel，宿主地形遮挡命中，拒绝错误运行身份与非法作用点。气流抓取通过 `StructureTraction` 接管同一租约，保留旧有限力、质量、权限、视距与短时效语义。
- 直接动能投掷恢复抬升后 H 投掷；电磁操控可接管已有 SubLevel；高速喷射恢复旋转喷嘴再激活、速度预算和扫掠撞击伤害/击退。
- **关闭喷射会话仅停止自己的推力，保留结构和惯性**。历史记录中的 close 自动回原点属于迁移偏差，已移除；显式 staticize 才写回宿主。
- 静态化读取当前 plot 内容，覆盖全部租用 chunk 的实体。Minecraft 普通实体查询排除 HIDDEN 区段，新分配 plot 同 tick 会漏实体；现在先排空实体存储加载，再枚举原始区段，并使用 teleport 返回的新实例处理回滚。
- 捕获/写回避免容器掉落复制；plot 重用清除旧方块和 BlockEntity。捕获完整回滚后退休 journal，防止启动恢复覆盖之后的合法编辑；未完整回滚保留恢复记录。
- 快照 full/delta 编解码明确分离，16,384 碰撞盒边界有单测；移除、离开同步范围、换维及后端异常关闭清理旧客户端投影/碰撞，并保证新 baseline。

### 验证方法与结果

本轮新增 `SubLevelControlSmoke`、`SubLevelDirectSkillSmoke`、`SubLevelContentSmoke`、`SubLevelTractionSmoke`。跨 tick 断言由服务端 tick 驱动，客户端帧率不影响测试持续时间。每次使用全新 `run/native-sublevel-direct-smoke-audit-rN`；Gradle 退出成功不能代替 `SUBLEVEL_CLIENT_SMOKE_PASSED`。

- 完整客户端 r12：`build/sublevel-control-client-r12.log`，`SUBLEVEL_CLIENT_SMOKE_PASSED`。三族同 tick 节点链和跨 tick 控制、直接技能、牵引、内容守恒、隐藏实体、捕获回滚以及服务端/客户端关闭清理全部通过。截图 `run/native-sublevel-direct-smoke-audit-r12/screenshots/sublevel-client-smoke-56496-1792415290802.png` 已查看。
- 最终 `test nativePhysicsTest sableCompatTest -DisDev=true` 通过，日志 `build/sublevel-control-tests-final.log`；普通 JUnit 2705 项、原生专用 25 项、Sable 专用 16 项，失败/错误/跳过均为 0。新增 180° 细长结构和偏心质心的扫掠候选范围回归也通过。
- 开发构建 `build -DisDev=true` 与发布构建 `build -DisDev=false` 均通过，日志分别为 `build/sublevel-control-build-dev.log`、`build/sublevel-control-build-release.log`；编辑器 104 项测试通过。四类 JVM 测试共 2850 项，失败/错误/跳过均为 0。
- 最终冻结代码的完整客户端 r13 再次通过：`build/sublevel-control-client-r13.log`。包含最新旋转扫掠候选范围与直接矢量控制状态检查，所有矩阵和 `SUBLEVEL_CLIENT_SMOKE_PASSED` 均出现；截图 `run/native-sublevel-direct-smoke-audit-r13/screenshots/sublevel-client-smoke-49388-1792415626431.png` 已查看。
- 客户端为真实集成服的自动 fixture，按服务端 tick 调用技能服务及节点运行时；并非人工键鼠全套施放或多人网络验收。截图证明基础投影可见，运动、伤害、控制权和内容断言以日志标记为证。
- 开发产物 `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar`，SHA-256 `53E76D0E603B432F88CFDF44AFF3CBCE02969CE3789090BBDDEC606E004452EC`；发布产物 `build/libs/academy-26.2.0-0.0.4-alpha-release.jar`，SHA-256 `B79544EC765B044C05CF6ED13E7516CA9F8BA2676B61BC598179B8DB6DED9486`。未部署到用户整合包，未更改生成资源。

本机复现使用 JBR 25 与 `build/native-physics-p16-env.ps1` 配置的 Rust/原生库；新目录需要从种子存档复制 `level.dat`、`dimensions/minecraft`、`data/minecraft`（26.2 世界生成设置也在其中），以及配置与 options，不复制 `data/academy`、`dimensions/academy` 或旧结果文件。运行：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/sublevel_direct_skill.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-audit-rN @physicsNativeArgs
```

可用 `-PacademySubLevelProgramChainProbe=false` 单独诊断跨 tick 矩阵；最终验收保留默认 true。中间 r7 暴露磁力偏心反馈，r9 暴露隐藏区实体遗漏，r10 暴露测试时钟依赖帧率，r11 暴露平移移除刚体；这些失败日志保留在 `build/`，不得当成通过证据。

### 验证边界

- `academy.sublevelProgramNodes` 与 `academy.sublevelDirectSkills` 继续默认关闭；本轮隔离测试显式开启。
- staticize 按目标原点和局部格地址写回；不声称支持把任意倾斜旋转直接烘焙成宿主格状方块。
- 尚未完成强杀进程后的真实 journal 恢复、真实 16k 网络负载、多人网络与本轮 Sable 四模组实机组合验收。codec 边界单测和确定性回滚负例不替代这些检查。
- 旧 Region/Projection 清理、捕获 journal 超过 1 MiB 载荷分片不在本轮完成范围；超限仍安全失败。

## 2026-10-19 审计前接续快照（历史）

### 当前基线

- 仓库：`D:/mcmodtest/AcademyCraft-Reborn-26.2`，分支 `26.2`。
- HEAD：`c154e680 补齐 SubLevel 程序成本与直接技能喷嘴 VFX`。
- 目标处于 active：捕获/静态化 journal、实体迁移、快照网络预算、直接路径撞击、feature flag 评估、CAS 负例。
- 按 AGENTS 约定，本轮改动**尚未提交**；不要继承旧记录的“已提交”结论。

### 已实现/已提交

- `a3012736`：SubLevel 合成 `StructureRef`、activation generation/runtimeId、`SubLevelStructureHandle`；
- `f4017515`：`look_structure` / `structure_motion` 查询节点；
- `57f18207` / `eb34c42b` / `7fb6eba7`：Aeromanip 程序 `extract/control/move/rotate/launch/release/staticize`；
- `7aacf0c1`：`SubLevelRestoreService` 静态化；
- `beb0e6a2`：`SubLevelMotionController` 连续移动；
- `808115df` / `57115268`：Accelerator / Electromaster 程序节点接入；
- `ab17ca0a` / `24d6940e` / `572eab78`：HighSpeedJet 直接 SubLevel 路径、喷嘴 plan、独立验证脚本；
- `a5123ad6` / `067f7471`：持续推力、CP、静态化；
- `5dea8e8a`：喷嘴 `isPickable=false` 修复 plasma 瞄准；`WorldBlockTargets.Target.Source`；
- `c154e680`：三族程序节点成本、直接路径喷嘴 VFX。

### 当前工作区未提交改动

修改：

```text
docs/sublevel_skill_node_migration.zh-CN.md
src/gametest/java/org/academy/internal/client/validation/SubLevelClientSmoke.java
src/main/java/org/academy/internal/common/ability/accelerator/program/ServerAcceleratorProgramRuntime.java
src/main/java/org/academy/internal/common/ability/aeromanip/movement/HighSpeedJetStructureService.java
src/main/java/org/academy/internal/common/ability/aeromanip/program/ServerAeromanipProgramRuntime.java
src/main/java/org/academy/internal/common/ability/electromaster/program/ServerElectromasterProgramRuntime.java
src/main/java/org/academy/internal/common/network/SubLevelStatePacket.java
src/main/java/org/academy/internal/common/sublevel/SubLevelCaptureService.java
src/main/java/org/academy/internal/common/sublevel/SubLevelMotionController.java
src/main/java/org/academy/internal/common/sublevel/SubLevelRestoreService.java
src/main/java/org/academy/internal/common/sublevel/SubLevelServer.java
src/main/java/org/academy/internal/common/sublevel/SubLevelSkillSupport.java
src/main/java/org/academy/internal/common/sublevel/SubLevelStructureHandle.java
```

新增（应提交）：

```text
src/main/java/org/academy/internal/common/sublevel/SubLevelCaptureJournal.java
src/main/java/org/academy/internal/common/sublevel/SubLevelStaticizeJournal.java
src/main/java/org/academy/internal/common/sublevel/SubLevelCollisionBudget.java
src/test/java/org/academy/internal/common/sublevel/SubLevelCollisionBudgetTest.java
```

无关未跟踪脚本，禁止混入提交：

```text
scripts/generate_darkmatter_item_textures.py
scripts/summarize_vfx_validation.py
scripts/vfx_validation_proxy.py
```

### 本轮新增能力

- 静态化 journal：`TARGET_ALLOCATED` → 写回 → `COMMIT_DECIDED` → 移除 SubLevel → 删除；启动恢复回滚未提交目标或完成已提交移除。
- 捕获 journal：创建 SubLevel 后、删除源前记录源位置/state/BE；启动恢复未提交捕获的源方块并移除 SubLevel。
- 静态化目标校验：维度、逐格作用距离、`mayInteract`；direct smoke 验证跨维度和 1,000 格外目标均 `REJECTED`。
- 实体迁移：plot 内实体通过 `teleportTo(host, ...)` 映射到目标坐标；失败回滚方块与已迁移实体。
- CAS 回滚：BlockEntity 加载失败时恢复 expected state/BE，并移除 replacement 遗留 BE。
- 碰撞盒网络预算：默认 4,096，超出按空间聚类合并；视图 cell 暂为精确全量。
- 视图增量：`fullView=false` 时不再重复编码 view cells；客户端沿用已有 view。
- 直接会话生命周期、CP 顺序、inactive move 拒绝、服务器停止清理。

### 验证证据

- direct smoke（`validation/native_structure/sublevel_direct_skill.init.gradle`）：
  - `CLIENT_SUBLEVEL_CAS_ROLLBACK result=REJECTED state=Block{minecraft:glowstone}`
  - `CLIENT_SUBLEVEL_ENTITY_MIGRATION count=1 item=(-194.5, 73.5, 133.5)`
  - `CLIENT_SUBLEVEL_PROGRAM_CHAIN ... staticize=COMPLETED`
  - `CLIENT_SUBLEVEL_DIRECT_JET ...` + `High-Speed Jet direct restore ...: true staticized`
  - `CLIENT_SUBLEVEL_ACCELERATOR_CHAIN ... staticize=COMPLETED`
  - `CLIENT_SUBLEVEL_ELECTROMASTER_CHAIN ... staticize=COMPLETED`
  - `SUBLEVEL_CLIENT_SMOKE_PASSED`
- 专服基线：`run/native-structure-physics-smoke-server-sublevel-r6`，PASSED，含 plasma 结构目标/遮挡和三族 real graph。
- 客户端基线：`run/native-structure-physics-smoke-client-sublevel-r4`，`NATIVE_STRUCTURE_PHYSICS_CLIENT_PASSED moving_samples=40`。该目录准备时必须清除 `data/academy` 及残留 `dimensions/academy/structure_space_*`、`sublevel_plots`。
- 最新全量：`test -DisDev=true`、`build -DisDev=true`、`build -DisDev=false` 均通过（2026-10-19）。

### 仍未完成（当前目标）

1. journal 启动恢复的真实崩溃负例（当前只验证正常路径和 CAS 负例）。
2. 真实 16k 盒网络压测；视图只做了“非 fullView 不重复发送”，尚未做 cell 增量/压缩；该 codec 分支目前缺少专门单测，只由既有客户端 smoke 间接触及。
3. HighSpeedJet 直接路径撞击结算（伤害/击退/停止）。
4. feature flag 默认启用评估结论：**暂不开启**；等待上述项完成。
5. 旧 Region/Projection 兼容层清理。
6. 捕获 journal 的 BlockEntity 载荷超过 1 MiB 时尚未分片，当前会安全失败。

### 接续注意事项

- 程序节点默认仍由 `-Dacademy.sublevelProgramNodes=true` 开启；直接技能由 `-Dacademy.sublevelDirectSkills=true` 开启。
- 客户端/专服 smoke 需使用全新隔离目录；不要复用有 `native-structure-physics-*result.txt` 的目录。
- 静态化/捕获 journal 位于世界 `data/academy/sublevel_transactions`；排查恢复时先查该目录和 `NATIVE_STRUCTURE_PHYSICS_*` 日志。
- `SubLevelStatePacket` 变更后，`fullView=false` 的包不再包含 cells；客户端必须已有同 id 的 full view。

## 当前接续入口：物理结构的技能世界坐标瞄准

- 基线：`df036f5a 修复物理结构技能控制与程序续跑`（上一批 34 个文件已按用户授权提交）。当前任务是修复电浆炮等位置锁定技能攻击物理结构时飞向远方。
- 根因：Sable 的 `Level.clip` 可以返回远端 plot 内的命中位置；普通射线又不能覆盖独立后端的结构局部世界。技能不能直接把这些命中位置或局部格坐标当宿主世界坐标。
- 公共入口 `WorldBlockTargets` 分别查询宿主地形与所选结构后端，选择最近的世界坐标表面。只返回瞄准位置，不伪造宿主 `BlockPos`；实际内容读写仍须使用结构引用和局部地址。`StructureQueries` 新增形状类型重载，原交互查询继续默认 OUTLINE。
- 已接入电浆炮、雷霆、雷暴、黑翼、矢量冲击、涡流牵引、绝热压缩、高速电子束、气流喷射、矢量反射的阻挡式重定向，以及公共视距/两处程序实体射线截断。电浆炮先确定地形/结构遮挡，再选择前方实体；保留施放时锁定位置，不新增追踪制导。
- 新增 `StructureAimWorldSmoke`：真实电浆实体的目标与飞行落点、结构前后实体遮挡，以及旋转结构/半砖形状查询。接入既有真实图 fixture。普通宿主方块仍按原模式查形状，未知区块保守阻挡，不主动加载；长射线分段查询，每段小于 256 格，总长度上限 4096 格。
- 用户已于 2026-10-16 明确授权本批 25 个相关文件的本地提交，提交信息为 `修复物理结构技能瞄准与世界坐标落点`；本记录随该批一并提交。既有 VFX 文件与外部 fork 不纳入本批。

### 本批验证与产物

- `test sableCompatTest -DisDev=true`、`build -DisDev=true`、`build -DisDev=false` 全部通过。普通 JUnit 2662 项、Sable 专用 16 项、编辑器 104 项，共 2782 项，失败/错误/跳过均为 0。日志：`build/world-target-tests-final.log`、`build/world-target-build-dev.log`、`build/world-target-build-release.log`。
- 独立客户端：`run/native-structure-physics-smoke-client-world-target-r2/native-structure-physics-client-result.txt`，PASSED，803 tick。验证真实旋转结构及半砖空隙、动能投掷捕获后的电浆炮目标/落点、前后实体遮挡。截图 `screenshots/native-structure-physics-before-60788.png` 已查看。对应命令为 `. ./build/native-physics-p16-env.ps1` 后运行 `./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=client -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-client-world-target-r2 @physicsNativeArgs`；再次运行须换全新目录。
- 四模组混装客户端：`run/sable-compat/world-target-suite-client-r3/client/sable-structure-client-result.txt`，PASSED，120 tick。三类真实程序图产生的结构均通过电浆目标/落点与实体遮挡断言；90 度旋转和宿主墙体遮挡也通过。截图时 26 个移动样本，最终客户端观察记录 110 个样本；截图 `screenshots/academy-sable-structure-80076.png` 已查看。命令：`./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite -PacademySableSmoke=true -PacademySableCapture=true -PacademySableRunDirectory=run/sable-compat/world-target-suite-client-r3`；再次运行须换全新目录。
- 本轮客户端操作属于服务端自动 fixture；只通过窗口点击继续启动警告页，没有人工键鼠施放全套技能。电浆落点测试使用零伤害、关闭方块破坏的真实 `Plasma.tick`，验证到达与终止，不代表完整爆炸破坏策略或多人网络验收。逐格内容操作仍需走各后端真实地址与权限链，不能拿世界命中点直接替代结构局部地址。
- 原生客户端通过后，仅补充了不参与该 fixture 的矢量重定向调用点、Sable 观察者定位和喷口测试瞄准点；核心查询未再变化，复用原生结果。上述最终改动已进入混装客户端运行与最终全量测试/两种构建。
- 开发版：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar`，SHA-256 `4670BC02404BCDB8252720E8D40A856558AE80D91026158B3D4838D76AE96678`。
- 发布版：`build/libs/academy-26.2.0-0.0.4-alpha-release.jar`，SHA-256 `B25BF0E9062A3E2AB063C19A7B569492267E15FEE0F3B62CD66A33AE57FF83B3`。未复制至用户整合包；测试只使用隔离存档。

### 保留的中间验证记录

- 原生服务端 `world-target-r1` 基础物理通过，但旧入口在未开启 grasp 时跳过 kinetic，不能算作电浆回归。已让测试入口独立进入 kinetic 阶段；后续客户端 r2 确认执行了新断言。
- 原生客户端 r1 的无施法实体查询触发 `CollisionContext.of(null)`；公共入口现已对无实体使用 `CollisionContext.empty()`，r2 通过。
- 混装客户端 r1 观察者出生在 y=-60，测试结构在 y=200，未收到可截图的结构；现由隔离 fixture 自动将真实观察者置于结构旁，r2/r3 均收到真实移动结构。
- 混装客户端 r2 的矢量/电击图电浆断言已通过，空力图命中了底部可单独选取的喷口，原断言误要求方块表面。r3 将测试射线移到方块上部，保留喷口正常可选行为，三类均通过。全部失败目录与日志保留，不计为通过。

## 上一批：后续迁移核查与操作回归

- 本轮基线为 `ac3ea5ea 修复结构文件原子替换偶发AccessDeniedException`。P19–P33、压力测试及持久化重试已在该基线历史中；下方早期进度仅供追溯，不代表当前待办。
- 本轮修复、真实后端证据、复现命令、失败记录和仍未完成的操作，集中记录在 `structure_control_audit_20261016.zh-CN.md`（本地归档，不随仓库分发）。接手时先读该归档，再读相关批次。
- 已修复的关键流程包括跨 tick 捕获节点续跑、Sable 三类移动、Sable 高速喷流技能推力、保留结构的喷口再次激活、控制令牌隔离、失效喷口支撑和动作前 CP 检查。旋转成功路径、安全静态化成功路径仍不能宣称完成。
- 用户已于 2026-10-16 明确授权本批本地提交，提交信息为 `修复物理结构技能控制与程序续跑`。本记录随该批一并提交；保留既有 VFX 未跟踪文件，不修改 `create-fork` 的既有工作区。

## 用户目标及约定

- 修复动能投掷拉起结构后无法稳定站立、抖动和移动困难。
- 整个物理结构是一个技能目标，命中的局部方块只提供锚点；不能把每一格分别当作实体施力。
- 同时推进动能投掷、高速喷流、磁场操作，以及矢量操作、电击使、空力使精密节点迁移。
- 用户已确认复现组合：Academy + Sable + Create + 航空学。独立模式也必须保留完整能力目标。
- 最新明确要求：未安装 Sable 时，动能搬运也必须创建新独立后端的物理化方块，不能继续使用旧 `BlockStructureEntity`。修补旧实体承载仅可作为对照，不能视为完成迁移。
- 用户已确认“终止”释放自己的控制，保留结构、惯性与重力；“安全静态化”另设操作。
- 公共能力服务同时供技能、程序和非玩家入口调用。不能跳过权限、总质量、有限力或内容守恒。

## 工作区基线与提交边界

- 仓库 `D:/mcmodtest/AcademyCraft-Reborn-26.2`，分支 `26.2`。早期基线 `05935f82 接入Sable结构牵引与整结构精密查询` 已被后续提交推进；本轮基线见文首。
- P19 公共捕获事务等后续批次已进入提交历史；本轮待提交范围以当前 diff 和核查记录为准。
- `docs/vfx/darkmatter_reconstruction_v3/runtime/multiplayer/` 中既有未跟踪记录与本任务无关，不能混入提交。
- `AGENTS.md` 要求完整功能与必要验证结束后，先获得该批本地提交确认；上一批 34 个文件已提交为 `df036f5a`，当前批次已完成验证并获得用户明确授权。
- 外部 `D:/mcmodtest/create-fork` 有大量既有改动。Suite 专服模型注册补丁保存在 `validation/sable_compat/`，尚未套用；先前跨仓库范围问题仍待答复，不能当作已修复。

## 已知实现与定位

| 路径 | 当前情况 |
| --- | --- |
| `KineticThrow` / `VectorStructureService` | `captureAndLift` 已不存在。`KineticThrow.java:292/295/308/314` 只保留按键、网络与蓄力入口，捕获/抬升/发射/终止委托 `VectorStructureService`；捕获经 `StructureTransactions.capture/poll`（`VectorStructureService.java:672,1017`） |
| `HighSpeedJet` / `HighSpeedJetStructureService` | `HighSpeedJet.java:365` 调服务 `launch`；P27 起捕获、区域控制、推力与释放走公共服务。Sable 捕获提交后保留结构并明确 `sable_launch_unavailable`；技能的 Sable 发射/飞行仍未实现，程序节点的一次性发射已由 P32 接入 |
| `MagnetManipulation` / `MagneticStructureService` | 方块路径由 `MagnetManipulation.java:490` 调 `MagneticStructureService.begin`；P31 起程序结构节点走服务，Sable 技能拖动经 `MagneticStructureService.java:989` 的轻量直接控制租约。实体分支与磁悬浮保留旧路径 |
| `NativeStructureRuntime.carryStandingEntities` | P23/P26 的真实承载：`NativeStructureRuntime.java:980,992,1013` 每 tick 把脚底接触点经旧/新姿态映射后搬运；平移、旋转、跳跃、边缘与低顶矩阵已验。站立抖动真实客户端仍待复测 |
| `BlockStructureEntity.tick/maintainStandingEntities` | 仅旧快照实体保留；两端推进与 `hurtMarked=true` 为旧路径，不再承载新独立后端。新后端承载见上一行 |
| `NativeStructureProjectionEntity` 碰撞/承载、`NativeStructureCollisionBoxes` | P23：投影按同步视图提供双方同源碰撞（轴对齐精确、任意姿态有界细分），`canBeCollidedWith` 有内容时为真 |
| `MixinEntityGetter` | 只包裹实现 `BlockStructureCollision` 的结构外包框，替换为复合形状并补入空间索引遗漏；保留原查询与其他模组形状（`MixinEntityGetter.java:42,66`） |
| `PneumaticGrasp` | 结构目标走 `StructureTraction.tryAcquire`，P18 起按整结构质量预算；旧 `BlockStructure` 实体分支与普通实体路径保留（`PneumaticGrasp.java:298,465`） |
| `StructureQueries` / `StructureTraction` | 已有真实射线、固定哈希 Sable 查询与 prePhysics 有限力（P17）、P24 捕获，以及 P30–P32 的 `StructureDirectControl` Sable 程序控制/发射；旧 `BlockStructureEntity` 尚未统一进入该后端控制契约 |
| `StructureTransactions` / `StructureCaptureCoordinator` | P19 后端无关分阶段捕获事务，含 journal、claims、coordinator 与 `NativeCaptureTarget`；P20 槽位回收；P24 Sable 执行器（`SableStructureTransactions.java:47`），Sable 捕获已可用。跨 JVM 强杀恢复与跨存储 WAL 未验/未实现 |
| `NativeStructureManifest.removeEntry/releaseSlot`、`NativeStructureRuntime.discardEmpty` | P20：空结构槽位回收；仅未加载、空、未隔离、无恢复证据、无物理附件的结构可释放；`NativeStructureRuntime.java:283`、`NativeStructureManifest.java:218,236` |
| `NativePhysicsArtifact` 解析 | P21：显式属性 → 显式环境 → home（属性/环境）→ 游戏目录 `academy/native_physics/` 固定布局；部分配置显式失败；物理打开异常统一为 `IllegalStateException` 并保留原因 |
| `NativeStructurePhysics.Region.rebind`/`rebindPhysicsAttachments` | P22 新增并在 P25 提升为接口方法：成员全体容纳、布局分离校验、附件先原子持久化再采纳、控制保留、下一 tick 重建；牵引越界自动重绑一次，初次抓取仍严格要求盒内 |
| `NativeCaptureWhitelist` | P19 起步白名单，后续扩集草/菌丝/苔藓/雪/黏土/砂砾/沙/红沙/树叶；随机刻不再作为拒绝门（结构空间不跑随机刻），保留 BE、流体、waterlogged、计划 tick、非全块、不可破坏与未列方块门；已列重力方块准入 |
| 三类 `Server*ProgramRuntime` | P30–P32 各注册 7 个 `action/structure/*`；extract/control/move/launch/release 接公共能力服务，rotate/staticize 明确 `unavailable`；电击使 `magnetic_move` 的方块目标改为显式 `magnetic_block_target_moved_to_structure_nodes` 拒绝，结构节点族另含 staticize 的显式拒绝路径 |

以上为 HEAD 782d935b 的代码观察；仍待运行验证的项已在各处显式标注，不把“可能原因”写成已复现结论。

## 本次进度

1. **完成**：读取工作区、既有计划和关键技能/碰撞入口；确认用户复现组合。
2. **已完成（P23/P26）**：独立后端真实碰撞与承载，以及平移/旋转/跳跃/边缘/低顶矩阵；站立抖动是否完全解决仍须真实客户端运行验证。
3. **部分完成**：公共整结构质量/运动查询、气流牵引质量预算及 25 格 Sable 混装场景；多材料/更大规模矩阵待验。
4. **已完成（P30–P33）**：三类能力各注册 7 个 `action/structure/*` 行动节点；extract/control/move/launch/release 已接入，rotate/staticize 成功路径仍未实现。
5. 最终验证见下表；真实客户端输入与节点编辑器人工操作仍需复测。

### P18 当前编辑与验证进度

> 历史批次（P18，2026-10-13）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 新增 `StructureDynamics.motion`：读取真实整刚体质量、质心和运动；不加载结构或启动求解器，不伪造外部后端的碰撞 epoch/睡眠状态。
- 新增公共 `PneumaticStructureControl`，按整结构质量设置 PD 增益、按技能等级限制总质量（128/256/512）与有限力（2000/4000/8000），资源倍率为质量/32、范围 1–16；气流牵引接入。已有内容变更会撤销控制，不能扩大结构后沿用低消耗。
- Sable 烟测新增 `-PacademySableCompound=true`（须同时开启普通 smoke），5×5 石头、真实总质量 50 的专服及 Suite 客户端 r1 均通过；随后保留轻型结构旧响应并加入程序查询断言，最终 Suite 客户端 r3 也通过。
- 公共精密节点新增 `query/look_structure`、`query/structure_motion`，使用 `ProgramStructureReference(reference,runtimeId)`，可以读取世界质心、格/秒线速度、弧度/秒角速度、总质量；不同方块命中共享结构身份。变量和选择节点支持该类型，不与 Entity/BlockPos/CONTROL_DESTINATION 隐式互转，不持久化到跨会话共享变量。共同节点自动提供给各能力分类，现有图格式/节点含义不变。创建、旋转、移动、终止等行动节点仍待迁移。
- 整体身份在卸载/重载后失效；读取状态受宿主维度和质心距离限制。大船可能命中近处表面但质心超出查询范围，此时状态节点没有输出；不是按命中方块读取部分质量。
- `p18-tests-r1` 通过；r2 因语言键排序和变量类型选项计数失败，已修正；r3 和最终轻型增益修订后的全量 2530 + Sable 11 均通过。
- 独立专服 `p18-r1` 已通过整结构程序查询、真实技能资源/释放/NPC 控制，但最后自由下落偏离固定区域，失败保留。按总质量直接放大轻型增益改变了旧落点，已改为质量不超过 2.25 时保留原 120/144 刚度、36 阻尼和 1000 力限，较重结构按质量/2.25 扩展并受等级绝对上限约束。
- 独立专服 r2 被随机生成高地占用夹具空间而拒绝启动，未修改世界；r3 改用新固定种子超平坦隔离世界重跑。失败日志/结果均保留。
- `StructureSupportClientSmoke` 为旧实体的真实集成玩家移动对照夹具，输入是合成 `ClientInput`，不是人工键盘；尚未运行。不能把它的结果当新独立后端玩家承载验证。
- P18 当时的生产差距：独立后端为 4 个永久容器槽、最多 3 个固定 24 格物理区域、显式外部 DLL/native-access 配置，投影实体没有承载；Sable 也尚缺生产捕获事务。后续 P23 已接入真实碰撞与承载，P24 已交付 Sable 生产捕获；仍待真实跨 JVM 强杀恢复、完整生命周期和长期区域回收。不能只把 `capture` 的返回类型换名就宣称完成技能迁移。

### 动能搬运的后续顺序（最高优先级）

1. **公共捕获事务**：预检选择、权限、内容与几何预算；目标预留、持久化日志、复制校验、源移除、目标提交；每个阶段提供异常及重启恢复证据。先明确普通块范围，BE/计划 tick 不得静默丢弃。**P19 已交付机制与原生后端接入（见下节）；P24 已交付 Sable 生产捕获；真实跨 JVM 强杀恢复仍属后续。**
2. **独立后端可供技能使用**：制品部署、容器容量/回收、活动物理区域跟随与恢复，以及真实玩家碰撞/承载。不能让玩家每四次使用便耗尽永久槽，也不能发布仅在测试 JVM 外部属性下可运行的默认技能。**全部子项已交付：P20 槽位回收、P21 制品部署约定、P22 区域重绑与跟随、P23 真实碰撞与承载（见下节）。**
3. **双后端捕获接入**：未装 Sable 创建独立结构，兼容混装创建 Sable 结构；未知版本/不可用时给出失败并保留源方块，不降级为旧实体。外部 assembly helper 仍只作测试工具，不能代替生产事务。**P24 已交付执行器抽象与 Sable 执行器（见下节）；恢复方向与长期行为边界见 P24 未验项。**
4. **迁移 `KineticThrow`**：保持区域选择和能力预算；HeldStructure 改为公共引用/租约；抬升、投掷、终止遵守有限力、单位、惯性契约；接触伤害只结算一次，不在世界碰撞时自动变回方块。
5. **承载验收**：独立和完整混装各测静止、升降、行走、跳跃、边缘、顶墙和旋转，之后测真实双端延迟。旧实体对照测试不能替代这些结果。
6. 再迁移高速喷流/磁场操作和三类行动节点；矢量改变运动、磁力筛选材料、空气通过受力锚点控制，共用结构身份、质量、资源和释放契约。

**当前状态（HEAD 782d935b）：动能搬运在未装 Sable 时已改用新独立后端（P25），P23/P26 已接入承载并覆盖平移/旋转/跳跃/边缘/低顶，站立抖动仍需真实客户端运行复测；三个能力中 extract/control/move/launch/release 已完成，rotate/staticize 的成功路径未实现，不能声称全部行动节点迁移完成。** 原“事务机制已就绪但技能生产路径尚未改动”的描述不再适用。

### P19 公共捕获事务（2026-10-13）

> 历史批次（P19，2026-10-13）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 新增公共入口 `StructureTransactions.capture/poll`；请求为 `StructureCaptureRequest`（位置集合、格数与跨度预算、逐格权限谓词），结果为 `StructureCaptureResult`（PENDING/SUCCESS、显式拒绝原因与 `RECOVERY_REQUIRED`、交易 ID 与结构引用）。公共签名不含 Sable 类型。
- 新增后端无关事务实现 `internal/common/structure/transaction`：
  - `StructureTransactionJournal`：每交易一个原子替换、强制刷新、往返校验的 NBT 日志；阶段 `PREPARED → TARGET_ALLOCATED → COPIED → COMMIT_DECIDED → PUBLISHED` 只进不退；损坏或身份不符的记录只隔离保留并报警，绝不猜测方向；`records()` 重新扫描目录以观察其它会话的写入。
  - `StructureCaptureClaims`：来源格全有或全无互斥；拿不准或待恢复的交易持续占用，阻止重复捕获。
  - `NativeCaptureWhitelist`：起步普通方块白名单 + 语义门（BE/流体/waterlogged/随机 tick/计划 tick/重力/非满方块/不可破坏），全部返回机器可读原因。
  - `StructureCaptureCoordinator`：服务端线程状态机，含提交前回滚与提交后前向完成两条收敛路径；重启时在服务器启动阶段读取日志并先占用来源格。
- 原生后端流程：预检 → 持久化 PREPARED → 槽位预留并记录目标（写入占用失败保持源格）→ 等待目标激活（超时回滚）→ 同 tick 逐格 `compareAndSet` 复制、整批复核与几何预算校验 → 持久化 COPIED 与提交决定 → 宿主源格以前后镜像移除并整批复核 → 目标保存 → PUBLISHED → 延后邻居通知 → 回收日志。提交前异常清空已写目标、停用并删除日志；提交后异常或未知内容保留日志与占用；目标保存失败在时限内重试。
- 重启恢复：`PREPARED` 直接回收（从未写入内容）；`TARGET_ALLOCATED/COPIED` 在来源镜像成立时清空目标、停用并回收；`COMMIT_DECIDED/PUBLISHED` 前向完成剩余源移除与目标保存；来源或目标出现未知内容时保留证据，不自动补发。
- 验证（最终修订版）：单元 2546 项（新增 16 项：日志往返/阶段推进/损坏隔离/超限拒绝/载荷防御、占用互斥、请求与结果契约）与 Sable 11 项零失败/错误/跳过（`build/p19-tests-final.log`）；`build -DisDev=true` 34 秒、`-DisDev=false` 55 秒通过（`build/p19-build-dev-final.log`、`build/p19-build-release-final.log`）；专用 gametest `academy:structure_transactions`（隔离目录 `run/native-structure-transaction-gametest-r5`，专用 init 脚本 `validation/native_structure/transaction.init.gradle`）通过：完整提交与源移除、目标内容一致、无掉落、日志回收、重复捕获拒绝；BE/随机 tick/waterlogged/白名单外/计划 tick/权限/块数/跨度的显式拒绝且源保留；`COPIED` 回滚与 `COMMIT_DECIDED` 前向两种恢复完成（日志 `build/p19-gametest-r5.log`）。gametest 仅在其专用系统属性开启时注册，并消耗永久槽位，必须使用新隔离目录。
- 失败轨迹保留：r1 在 `thenWaitUntil` 抛 `AssertionError` 崩溃服务器（改用 `helper.assertTrue` 使等待循环可重试）；r2 waterlogged 楼梯被 `fluid_not_supported` 先拦（调整检查顺序输出精确原因）；r3 夹具向新分配槽位预写内容触发 `emptyContainer` 隔离（改为激活后经 `compareAndSet` 合法写入）；r4 通过后为 `begin` 增加异常收敛（回收日志、释放占用、返回明确失败）并复跑 r5。
- 未验边界：真实进程强杀后的跨 JVM 重启恢复（当前由同会话预置日志驱动同一恢复代码路径）；Sable 侧生产捕获仍显式不可用；原生槽位为永久认领，失败或回滚会留下空占位，容量与回收属后续「独立后端可供技能使用」；恢复完成不重发放置/邻居通知；技能尚未迁移到该事务；白名单为起步子集（不含箱子等后续 BE 适配）。
- 当前构建（含 P19，不含技能迁移）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `25C0ED95BA75FCD35200E98D09F4F1B584B69D4D1245A0BEBF1289AF97082C9A`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `926591F12222B6EB066EE1A8B209B5A79424624EE4F5BC978684D62FF83DB7C1`。未复制到整合包，未操作用户存档。

### P20 空结构槽位回收（2026-10-13）

> 历史批次（P20，2026-10-13）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 新增 `NativeStructureManifest.removeEntry(UUID)` 与 `releaseSlot(int, UUID)`：候选索引先整体校验再原子写入并发布；存在恢复证据或仍被索引引用的槽位拒绝释放；owner 标记最后删除，中断只会泄漏认领，不会复活已删内容。
- 新增 `NativeStructureRuntime.discardEmpty(StructureRef)`：仅接纳未加载、内容为空、未隔离、无恢复证据、无物理附件且存储维正常的结构；先确认空并清理该槽位区域计划/流体 tick、保存存储层并复核，再移除索引，最后删除 owner 标记。成功即释放一个永久槽位。
- P19 集成：事务回滚（含重启恢复回滚）与分配日志失败路径在「清理目标 → 回收日志 → 停用」后调用 `discardEmpty`；日志删除在前、槽位释放在后，崩溃时最坏只是空占位泄漏。
- 验证（最终修订版）：单元 2548 项（新增 2 项回收用例：删除条目与释放标记的顺序、恢复证据拦截）与 Sable 11 项零失败/错误/跳过（`build/p20-tests-final.log`）；`build -DisDev=true` 35 秒、`-DisDev=false` 55 秒通过（`build/p20-build-dev-final.log`、`build/p20-build-release-final.log`）；gametest `academy:structure_transactions` 在隔离目录 `run/native-structure-transaction-gametest-r6` 通过：回滚恢复自动释放槽位、容量上限拒绝第五个分配、已加载空探针显式 unload+discardEmpty 后新结构复用释放槽（日志 `build/p20-gametest-r6.log`）。
- 失败轨迹：首次运行单测暴露 `persist()` 直传活索引导致的清空别名 bug（候选持久化内 clear+putAll 同一 map），拆分为普通持久化与候选持久化两条路径后修复。
- 未验边界：成功捕获或保留内容的结构仍占用永久槽，静态化或显式销毁属后续；启动不自动修复历史遗留的「已认领但无索引」空槽（仅记录为安全泄漏）；`discardEmpty` 目前是内部 API，尚未接入技能或命令入口；真实玩家流程未验。
- 当前构建（含 P19/P20，不含技能迁移）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `055082A2E4DE4555A96A99F1E27D5F8B45DDD4ED723F206633C8B14F744FDD6D`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `A96D6A4164327EC3FBED5D395C95457AB35224B76891D2A82032D8AC7D89863F`。未复制到整合包，未操作用户存档。

### P21 制品部署解析与统一异常（2026-10-13）

> 历史批次（P21，2026-10-13）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- `NativePhysicsArtifact` 新增纯解析 `resolve(properties, environment, conventionalDirectory)`：显式属性对 → 显式环境对 → `academy.nativePhysics.home`/`ACADEMY_NATIVE_PHYSICS_HOME` → 游戏目录固定布局 `academy/native_physics/`（平台库文件名 + `academy_physics.properties`）。部分配置（只设一项）或显式 home 缺件均显式失败，不再回退到更弱来源；空白值按未设置处理；哈希/版本校验与私有副本逻辑不变，仍不打包、不下载二进制。
- `NativeStructurePhysics.createRegion` 打开物理的统一失败面：`IOException` 与 `RuntimeException` 一律转为携带原始消息与原因的 `IllegalStateException`，权限缺失等诊断保持可读。
- 新增验证脚本 `validation/native_structure/physics_deploy.init.gradle`：隔离专服目录内按固定布局放置 `prepareNativePhysics` 制品、清空原生环境变量、不设任何 `-Dacademy.nativePhysics.*`，仅保留 `--enable-native-access=ALL-UNNAMED` 运行既有 `nativePhysicsSmoke=server` 探针。
- 验证（最终修订版）：单元 2551 项（新增 3 项解析顺序/部分配置/未配置用例）与 Sable 11 项零失败/错误/跳过（`build/p21-tests-final.log`）；原生 JVM 专项 20 项通过（`nativePhysicsTest`，显式属性路径回归）；`build -DisDev=true` 36 秒、`-DisDev=false` 57 秒通过（`build/p21-build-dev-final.log`、`build/p21-build-release-final.log`）；部署专服探针 `run/native-physics-smoke-server-deploy1` 退出 0：真实求解器 720 步、`binding_native_access=true`、`height=0.4999`、结果 `native-physics-server-result.txt` 为 PASSED，证明仅凭游戏目录约定即可发现并加载原生库（日志 `build/p21-deploy-probe.log`）。
- 未验边界：Linux `.so` 布局仅由同一代码路径覆盖，未在本机实跑；`--enable-native-access` 仍需启动器/部署方提供，模组无法在运行时添加 JVM 参数（仅在缺失时给出可操作诊断）；未在整合包/真实玩家环境验证。
- 当前构建（含 P19/P20/P21，不含技能迁移）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `0899E1505E8BCB1FB26CC5169197F1E9185411D499A9FFD215BD77726EAEB962`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `6C2429DCC682E2F7D64D8458814839510C4F8251B86B5990B97F55C3CF5B756B`。未复制到整合包，未操作用户存档。

### P22 物理区域重绑与牵引跟随（2026-10-13）

> 历史批次（P22，2026-10-13）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- `NativeStructurePhysics.Region.rebind(AABB)`：显式把活动区域重绑到同宿主新边界。前置校验新边界尺寸、与其他活动/存档区域的 1 格分离、以及**所有当前成员**（不只发起者）在新盒内的实际占用（避免多成员编队跟随导致同伴出界终态失败）；随后 `NativeStructureRuntime.rebindPhysicsAttachments` 先整体校验并按候选索引 `putAll` + 持久化全部成员附件（失败恢复内存与磁盘旧值），再采纳新边界与原点、重建地形缓存并就地释放原生世界（**保留瞬时控制**），状态置 PAUSED 由下一 tick 重建发布。被拒绝的重绑不改变任何一侧。`bounds/origin/terrainCache` 因此改为可变字段（原先 final）。
- 牵引跟随：`NativeStructureTraction.Session.update` 在目标仅因当前盒边界被拒时，用纯函数 `NativeStructureTractionBounds.recentered` 计算同尺寸、按宿主边界与高度夹取的候选盒，重绑成功后复检并按原有限力路径推进；每次 update 最多重绑一次。初次 `acquire` 仍要求身体与目标都必须在当前盒内，不自动迁移。恢复一致性：重绑先持久化附件，重启 `resumeRegion` 直接使用重绑后的边界。
- 验证（最终修订版）：单元 2553 项（新增 2 项 `recentered` 纯函数用例）与 Sable 11 项零失败/错误/跳过（`build/p22-tests-final2.log`）；区域两阶段真实专服探针 `run/native-structure-physics-regions-20261013-p22-r3`：prepare 与 resume 均 PASSED、退出 0，证据含 `region_rebase_conflict_rejected`、`region_rebase_adopted_and_persisted`、`region_rebase_resumed`、`region_rebase_pose_preserved`，新 JVM 恢复后 `resumed_rebased_bounds=true`（日志 `build/p22-regions-prepare-r3.log`、`build/p22-regions-resume-r3.log`）；含真实抓取技能路径的世界烟测 `run/native-structure-physics-smoke-server-p22-r2` 退出 0（`build/p22-world-smoke-r2.log`）；事务 gametest `academy:structure_transactions` r7 沿用通过；`build -DisDev=true` 38 秒、`-DisDev=false` 58 秒通过（`build/p22-build-dev-final2.log`、`build/p22-build-release-final2.log`）。
- 失败轨迹：首次 prepare（p22-r1）在迁移后的 `verifyRegionInfo` 失败——该断言与 resume 阶段都硬编码原始边界；改为阶段感知期望（resume 阶段预期迁移后边界）并为迁移后摘要增加专用断言。
- 未验边界：跟随目标必须能放进同尺寸重绑盒（含 0.25 安全内边距），因此长距离牵引需要技能逐 tick 更新较近目标，一次越界远目标仍以 `OUT_OF_BOUNDS` 结束；`rebind` 目前是具体 `NativeStructurePhysics.Region` 的公开方法而非 `StructurePhysicsRegion` 接口成员，尚未接入技能/命令显式入口；重绑不改变成员内容与存档；真实玩家承载仍未实现。
- 当前构建（含 P19/P20/P21/P22，不含技能迁移）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `E8BDA66804D06917EBE22A3C08AB0EAD840D00A41CA062087F2AB25FEE34B6B0`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `21A77CD5AB884A2E1414E90BB81D02B0589280FE1FD9B7D81BFC22F4307AB399`。未复制到整合包，未操作用户存档。

### P23 真实玩家碰撞与承载（2026-10-13）

> 历史批次（P23，2026-10-13）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- `NativeStructureProjectionEntity` 实现 `BlockStructureCollision`：碰撞盒由同步视图推导（`NativeStructureCollisionBoxes.localBoxes` 以视图自身作为 BlockGetter 解析邻接状态），轴对齐姿态精确变换，任意四元数姿态按输出预算（2048 盒）做 3³→2³→整盒 的均匀细分近似；服务端与客户端从同一发布状态导出同源形状。投影在加入/同步数据/姿态视图更新时注册进 `BlockStructureCollisionRuntime`，移除时注销；`canBeCollidedWith` 仅在当前内容有碰撞时为真，`isPushable=false`、`isPickable=false` 不变。
- 服务端承载：`NativeStructureRuntime` 每 tick 在投影更新后，对脚底落在结构表面容差内（站立 0.18 / 恢复 0.5）的实体执行搬运——把脚底接触点经**上一姿态**映射到局部网格坐标、再经**新姿态**映射回世界（平移与旋转都正确），随后对齐表面、保留落地状态与 `hurtMarked=true`；带物理附件与非附件结构同样适用（非附件结构姿态不变则自然无操作）。
- 集成修复（P22 缺陷）：牵引跟随重绑后区域处于 PAUSED 待下一 tick 重建，技能若同一 tick 继续 `lease.setControl` 会被 `controllable` 判定不可用而结束会话；现在重绑当 tick 保留会话并延后一 tick 下发新指令。
- 失败轨迹：初版承载按 `pose.worldOrigin()` 增量搬运；该结构局部质心位于局部 y≈80，旋转时姿态原点每 tick 扫掠约 10 格，导致骑乘实体被甩飞并连带真实抓取技能目标失控（GRASP HOLD 失败、眼位漂移）；改为接触点映射后修复。
- 验证（最终修订版）：单元 2558 项（新增 5 项碰撞盒用例：轴对齐判定、独立角点变换、细分预算与覆盖、支撑面语义）与 Sable 11 项零失败/错误/跳过（`build/p23-tests-final.log`）；世界烟测 `run/native-structure-physics-smoke-server-p23-r12` 退出 0，承载证据 `support_rider_standing=true`（脚底 112.0 = 期望表面 112.0）、`support_carried_delta=1.63`、`support_descend_feet=112.0`、`support_carrying_both_directions=true`，真实抓取技能与其余全部阶段通过（`build/p23-world-smoke-r12.log`）；区域两阶段探针 `run/native-structure-physics-regions-20261013-p23-r4` prepare/resume 均 PASSED（含重绑恢复）；事务 gametest r8 通过；`nativePhysicsTest` 20 项通过；`build -DisDev=true` 38 秒、`-DisDev=false` 60 秒通过（`build/p23-build-dev-final.log`、`build/p23-build-release-final.log`）。
- 未验边界：非轴对齐碰撞是有界细分近似（盒预算 2048，超限时降低细分精度）；承载只对支撑判定容差内的实体生效，未测高速自旋与跳跃/下船继承表面速度等细节；客户端本地玩家碰撞依赖同步视图，未做真实双端键盘操作验证；投影仍不可交互（`isPickable=false`）。
- 当前构建（含 P19–P23，不含技能迁移）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `2197C2B2211B737ADA5E7B756BEDE0D3540D87D31A7E2E435C3D8791EB096AFB`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `D66097FB72A76F53312FB932998891C13207BAD06677820BDAD5C2352997D932`。未复制到整合包，未操作用户存档。

### P24 双后端捕获接入（2026-10-14）

> 历史批次（P24，2026-10-14）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- **第一步（已提交 `b4c6dd79`）**：新增 `CaptureTarget` 执行器边界与 `NativeCaptureTarget`，协调器完全后端无关；行为等价通过全套验证。
- **第二步（本批）**：新增 `SableStructureTransactions implements CaptureTarget`：
  - `allocate`：`allocateNewSubLevel`（occupancy 自动分配）后立刻以 `rotationPoint = plotOrigin + 0.5`、`position = origin + 0.5` 重锚，使公共 `StructurePose.worldOrigin() == 选区下角`、局部偏移 `n → 世界 origin+n`（与原生捕获同语义）；同一调用链预建 2×2 分区空 chunk，满足“空船跨 tick 会被质量失效销毁”的约束。
  - 写入/读取/清除：`LevelChunk.setBlockState(pos, state, SKIP_ON_PLACE|SKIP_BLOCK_ENTITY_SIDEEFFECTS)` 真实更新链路（Sable 自动更新质量/碰撞/热图/边界），坐标映射与只读桥完全一致；`releaseEmpty` = `removeSubLevel(ship, REMOVED)`（预决策回滚/容量回收）；`save` = `getHoldingChunkMap().saveAll()`（Javadoc 注明其吞异常边界）；`deactivate` 空实现；`resolve` 不强制加载未加载船。
  - 路由接线：`Bridge.captureTarget`（中立默认空）+ `StructureBackendRouter.captureTarget`（仅 SABLE 模式且有桥时转发）+ 协调器 `begin`/`targetFor`/`recoverPending`/`started` 按模式分派；INCOMPATIBLE/MIGRATION_REQUIRED 明细不变；打包必检清单加入 `SableStructureTransactions.class`。
- 烟测：`SableStructureSmoke` 新增 CAPTURE 阶段（`-PacademySableCapture=true`，默认关闭保持旧流程）：真实事务捕获 3 格石头并断言源 AIR、逐格读回一致、journal 回收；chest 负例返回 `block_entity_not_supported` 且源保留。
- 验证：单元 2558 + Sable 11 零失败（`build/p24b-tests-final.log`）；纯 Sable 专服 `run/sable-compat/p24-server-r2` PASSED、退出 0，结果文件含 `sable_capture_committed=true`、`sable_capture_rejected_be_preserved=true`；Suite 集成客户端 `run/sable-compat/p24-suite-client-r3` PASSED：`real_sable_plot_received=true`、运动样本 49、截图 `academy-sable-structure-49784.png`（354 KB）已查看存在、结果含两条捕获证据；`build -DisDev=true/-DisDev=false` 通过（`build/p24b-build-dev.log`、`build/p24b-build-release.log`）。
- 失败轨迹保留：专服 r1 在 NPC 阶段失败——CAPTURE 阶段跨 2 tick 未续租，NPC 牵引租约（leaseTicks=3）到期；修复为每 tick `session.update` 续租后 r2 通过。客户端 r1 因世界未预置失败（新建了 New World）；r2 按约定预置 `level.dat`+`world_gen_settings.dat` 后通过；r3 为用户关闭告警屏幕后按同一约定重跑复核通过。
- 未验边界：Sable 侧的**恢复方向**（PREPARED/TARGET_ALLOCATED 回滚、COMMIT_DECIDED 前向补全）未注入触发；`save` 失败重试到 `target_save_timeout`、多船容量回收、`quarantined`（协调器当前无调用点）未专项验证；捕获船交给 Sable 物理后的长期行为（持续下落、保存/重载一致性）未断言；`saveAll()` 吞部分 I/O 异常，持久性边界仍以 P21/P24 记录为准。
- 当前构建（含 P19–P24）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `856CF4988B7E4B2AA52E104456FB1362CE65386DA6A7764EF4DF5FBE0C770474`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `4B3924482CA545822B6F807487AC332FC0ECEE7713B6DADFC78B17D622A1E799`。未复制到整合包，未操作用户存档。

### P25 动能投掷迁移到公共结构服务（2026-10-14）

> 历史批次（P25，2026-10-14）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 改动/新增文件：`KineticThrow.java`（Server 全路径重写）、`KineticStructureControl.java`（新增公共能力加速器控制预算）、`StructurePhysicsRegion.java`（`rebind` 提升为接口方法）、`NativeStructurePhysics.java`（`@Override`）、`NativeStructurePhysicsKineticThrowSmokeChecks.java`（新增专服烟测）、`NativeStructurePhysicsWorldSmoke.java`（新 `KINETIC` 相位与开关）、`physics_world.init.gradle`（`academyStructurePhysicsKineticThrowSmoke` 透传）、`KineticThrowTest.java`（纯函数用例）、中英文 `kinetic_throw.desc`（描述与实际行为对齐：无世界碰撞爆炸结算、不再变回方块）。
- 捕获：选区（下半椭球+上圆柱、前方半空间裁剪、立即阻挡裁剪）保持；新增确定性预算预裁 `cropToBudget`（按到 seed 距离平方升序、坐标 tie-break、seed 优先保留；NATIVE 128 / SABLE 256，经 `StructureBackendRouter.status().mode()` 判定；`maximumSpan=24`）。`executeActive` 扣费时机与不退款语义不变；改走 `StructureTransactions.capture/poll` 跨 tick `CaptureState` 驱动，失败以 `status:detail` 走日志+actionbar（沿用 `capture_rejected` 文案）。
- 抬升：原生以选区+lift 计算区域盒（每轴 ≤24、含自适应余量、宿主高度/世界边框夹取；无法容纳则明确失败并保留结构），`StructurePhysics.openRegion`+`attach`，等 `motion` 出现后 `acquireEntityControl`+`StructurePhysicsControl.Anchor`，每 tick `setControl/renew`；Sable 不建区域，`StructureQueries.raycast/validate` 取同 ref 命中后 `StructureTraction.tryAcquire`，每 tick `update` 续租。新增公共 `KineticStructureControl.settings(totalMass, tier, milestone, range)`：总质量预算 128/256/512，刚度/阻尼/最大力按参考质量 2.25 缩放并受绝对上限约束，`leaseTicks=20`。
- 投掷与飞行：原生先 `setVelocity(0)` 清除抬升残速，再 `region.applyImpulse(ref, direction × 50 m/s × milestone3(×1.2) × mass, 质心)`，纯惯性、控制槽为空；飞行每 tick 用投影 `worldCollisionBoxes()` 计算占用盒，按 `velocity×0.05 + 角速度半径余量` 预测，距区域边缘 <2 格时以同尺寸、宿主/世界边框夹取的候选盒调用 `region.rebind`（接口提升）；候选不可得或 rebind 失败 → 释放控制、保留结构、安全结束（不让区域 FAILED）。Sable 投掷/飞行明确不做：结束并保留结构（代码与本节注明）。
- 近邻伤害：抬升与飞行期间投影碰撞盒与附近实体求交，排除控制者/友军/旁观者/被承载者/`noPhysics`；per-target latch+10 tick 冷却；伤害 `5×blockCount`、击退沿用旧公式（milestone3 ×1.25，实体仍用格/tick），`SkillDamageSource.ofDirect` 的直接实体用投影（找不到投影时不会进入该分支）；每结构一次爆炸音效/粒子，锚点用结构中心；不再调用 `beginGravitySettlement`、不再变回方块。
- 清理：`releaseHeld` 只关闭租约/牵引会话并清 map，结构/物理区域/惯性保留；登录、换维、死亡、停服为静默清理，不抛异常。
- 单位与标定依据：旧投掷 2.5 格/tick × 20 = 50 m/s（`LegacyMotionUnits.velocityPerSecond`）；旧抬升 PD 0.22/0.65（1/tick）重标定为质量缩放的力 PD，参考体（质量 2.25）取 120 刚度、36 阻尼（与已审计的共享气流控制一致），重力静差约 m·g/k = 0.18 格；最大力 `min(2000<<tier, 1000×m/2.25)`，租约 20 tick。
- 验证：单测 2562 项 + Sable 11 项零失败/错误/跳过（`build/p25-tests-final.log`）；`build -DisDev=true` 22 秒、`-DisDev=false` 35 秒通过（`build/p25-build-dev-final.log`、`build/p25-build-release-final.log`）；专服烟测 `run/native-structure-physics-smoke-server-p25-r10` `PASSED`、`exit_status=0`，结果含 `kinetic_capture_committed`、`kinetic_capture_pending_observed`、`kinetic_source_cleared`、`kinetic_region_ready`、`kinetic_lift_anchor`、`kinetic_lift_rise=3.25`、`kinetic_throw_speed=50.0`、`kinetic_throw_control_cleared`、`kinetic_impact_damage=45.0`、`kinetic_impact_knockback`、`kinetic_follow_rebinds=10`、`kinetic_region_never_failed`、`kinetic_terminal_structure_active`、`kinetic_source_not_restored`、`kinetic_no_drops`（日志 `build/p25-world-smoke-r10.log`）；Sable 专服烟测 `run/sable-compat/p25-server-r1` `PASSED`、`exit_status=0`，仍含 `sable_capture_committed`、`sable_capture_rejected_be_preserved`（日志 `build/p25-sable-server-r1.log`）。
- 失败轨迹（目录与日志均保留）：p25-r1 投掷时抬升末速未清（y≈1.09）导致速度校验失败，改为冲量前 `setVelocity(0)`；r2/r3 单次 45 请求实际扣 94.32，逐层临时日志定位为 `KineticEnergyApplied` 被动（玩家伤害 ×2 并追加 CTA 伤害），烟测夹具改为只启用被测技能并清零目标护甲，技能新增 `Diagnostic.impacts` 断言“每接触只结算一次”；r7 白昼僵尸自燃引入 1.0 火焰伤害被误判为重复结算，夹具补火焰免疫；r4–r6 为临时诊断运行。r1–r10 目录、`build/p25-world-smoke-r*.log`、`build/p25-sable-server-r1.log` 均保留。
- 未验边界：Sable 的动能投掷发射与飞行；10 秒悬停超时后仅释放控制、结构与已开区域继续保留（每技能使用最多新增一个区域，长期回收未实现）；原生投掷损伤沿用加速器回退的玩家攻击类型，受常规护甲/减伤结算；真实双端与真实 TCP 客户端未验；静态化操作、载荷重新落回方块不在本节点。
- 当前构建（含 P19–P25）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `B9E72F45CAC4CC0E3F785E4C570EB9219659E56A5AA002917536C30B03CA9804`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `C192909C00F0BD1B0782340D4380F9E321DCA4E574F315793D0A56F1CAB21839`。未复制到整合包，未操作用户存档。

### P26 原生承载验收矩阵扩展（2026-10-14）

> 历史批次（P26，2026-10-14）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 改动文件：仅 `NativeStructurePhysicsSupportSmokeChecks.java`（+270/-1；DESCEND 后新增 ROTATE/JUMP/EDGE/CEILING 相位，DONE 前清理临时物；既有 ARM/SETTLE/LIFT/DESCEND 语义不变）。Sable 后端与真实双端不在本节点。
- ROTATE：Anchor 抬离地板 0.9 → `setVelocity(ZERO, (0, 1.5, 0))`；旋转累计 60.13° 期间每 tick 断言支撑面存在且 |feet−surface|<0.05；rider 随转（世界位移 0.290、结构本地漂移 2.6e-4）；停转后回落稳定（feet=112.00000427）。证据 `support_rotation_supported/rider_followed=true`。
- JUMP：`setDeltaMovement(0, 0.55, 0)` 后连续 5 tick 离面 >0.5 且未被承载拉回，峰值 2.03 格，落回后重新被支撑。夹具 rider 为 noAI：`aiStep` 对非有效 AI 跳过 `travel`，夹具每 tick 显式调用 `rider.travel(Vec3.ZERO)` 推进原版积分（模拟跳跃，非键盘输入）。证据 `support_jump_leaves_surface/relands=true`。
- EDGE：水平 Anchor +2.0（静止摩擦下实际滑移 1.752），滑动期每 tick 支撑对齐、|Δrider.x−Δcom.x| 最大 0.043；再给 rider 每 tick +0.15 水平速度走出边缘，支撑消失后自然下落并落到真实地板（landing_feet=111.0=floorTop），region 保持 READY。证据 `support_edge_translation_follows/walk_off_falls/region_ready=true`。
- CEILING：rider 头顶 2 格放 3 块真实 STONE；Anchor 抬升 +2 全程 rider.maxY ≤ 天花板底面+1e-3（不穿透、无异常），rider 仅升 1.05 后被顶住；清理后回落稳定。当前无结构推挤能力：结构最终穿过 rider（`support_ceiling_structure_through_rider=true`，夹具只按“不穿透/不崩溃”断言并注明该限制）。证据 `support_ceiling_blocks_rider/no_clip=true`。
- 失败轨迹（目录与日志保留）：r1 JUMP 卡死（noAI 不积分 `setDeltaMovement`，显式 `travel` 修复）；r2 CEILING 放块后当 tick 挂 Anchor 被拒（宿主地形快照失效，需隔一个服务端 tick 重抓）。`build/p26-world-smoke-r1.log`、`r2`、`r3` 均保留。
- 验证：世界烟测 `run/native-structure-physics-smoke-server-p26-r3` PASSED、exit_status=0（858 tick；既有 `support_*`、`grasp_*`、`kinetic_*` 证据无回归，日志 `build/p26-world-smoke-r3.log`）；单测 2562 + Sable 11 零失败（`build/p26-tests-final.log`）；双构建通过（`build/p26-build-dev-final.log`、`build/p26-build-release-final.log`）。Sable 专服烟测未重跑（本改动仅在原生世界烟测夹具内，Sable 烟测不执行该相位）。
- 未验边界：Sable 后端承载；真实双端（真实客户端+服务端观察）与真实键盘行走/跳跃；EDGE 锚目标 +2.0 与摩擦实际滑移 1.752 属环境性质；CEILING 无推挤（结构穿 rider）。
- 当前构建（含 P19–P26）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `359243C796BC7D68EE6052441BAC312FD4AD695D4247A20AAE348CCC154AB0C8`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `266E1CAFEB39ECBB00E9DD36A21C482E378DB9237381E377FCBDB2F9906E10A6`。未复制到整合包，未操作用户存档。

### P27 高速喷流迁移到公共结构服务（2026-10-14）

> 历史批次（P27，2026-10-14）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 改动/新增文件：新增公共 `JetStructureControl.java`（喷流推力与速度标定）；重写 `HighSpeedJetStructureService.java`（捕获/控制/发射/释放全路径）；`HighSpeedJetNozzle.java`（结构支持由旧 `BlockStructure` 实体改为公共 `StructureRef`，方块/实体支持不变，新增 ref 存档键）；`HighSpeedJet.java`（新增 `Events`：`PlayerTickEvent.Post` 驱动、登录/换维/死亡/停服清理）；新增 `NativeStructurePhysicsHighSpeedJetSmokeChecks.java` 与 `JetStructureControlTest.java`；扩展 `HighSpeedJetStructureServiceTest.java`（预算预裁/区域盒）；`NativeStructurePhysicsWorldSmoke.java`（新 `HIGH_SPEED_JET` 相位与开关、失败清理）；`physics_world.init.gradle`（`academyStructurePhysicsHighSpeedJetSmoke` 透传）。
- 捕获：选区（半径 2 球形，沿推力方向 `cropImmediatelyBlocked`）与喷嘴选择保持；新增确定性预算预裁 `cropToBudget`（NATIVE 128 / SABLE 256，经 `StructureBackendRouter.status().mode()` 判定，seed 优先保留，其余按 seed 距离平方 + 坐标 tie-break）；改走 `StructureTransactions.capture/poll` 跨 tick `CaptureState` 驱动。扣费时机与旧实现一致：**捕获提交并 attach 成功后**才扣 `(activationCpCost(1)+0.25/格)·成本曲线·costMultiplier` CP 与 `(activationAirCost(1)+0.5/格)·成本曲线` 压缩空气，之后不退款；捕获拒绝/超时以 `status:detail` 走日志 + actionbar（沿用 `structure_launch_rejected` 文案），失败保留源方块。
- 控制/发射：原生以捕获格包围盒构造区域盒（推力轴在宿主可用空间内扩展至 ≤24、以捕获体为中心；其余轴对称余量 ≤6；各轴 ≤24 且不越世界边界/高度），`StructurePhysics.openRegion`+`attach`，随后 `acquireEntityControl` + `StructurePhysicsControl.Force`，每 tick `setControl/renew`（租约 20 tick）。推力沿用旧标定：`JetStructureControl` 把旧 0.48 格/tick² 经 `LegacyMotionUnits.accelerationPerSecondSquared`（×400）换算为 mass·格/s²，乘 `effectMultiplier(power)` 与 milestone3 ×1.25，并受共享 `MAX_FORCE=10000` 绝对上限；速度上限 3/6/8 格/tick 经 `velocityPerSecond`（×20）换算为 60/120/160 格/s，`limitedThrustForce` 以每 tick 有限力复现旧 `clamp(v+F/m,上限)` 的整矢量收敛（lease 独占下不能瞬时置速）。飞行每 tick 用投影 `worldCollisionBoxes` 预测并按 P25 同构算法 `region.rebind` 跟随；无法重绑时释放控制、保留结构安全结束。喷嘴经公共 `StructureRef` 挂到结构并按 `StructureHandle.pose()` 跟随（位置/朝向随结构旋转；结构不可解析时回退方块支持判定）。
- Sable 边界：捕获经 `StructureBackendRouter` 路由（混装创建 Sable 结构）；提交后若 `reference.backend()==academy:sable`，附挂喷嘴后明确结束并保留结构（日志 + actionbar `sable_launch_unavailable`），不建旧实体、不回退旧路径；Sable 发射/飞行仍属未实现边界。
- 接触效果：沿用 P23 投影碰撞（`worldCollisionBoxes` + 一次性结算 + 目标 10 tick 冷却），闭速以格/s→格/tick 换算后调用旧 `BlockStructureKinetics.collisionDamage/collisionKnockback`（0.35 格/tick 门限、伤害上限 24、击退上限 2.4），伤害再乘玩家能力/伤害乘区，击退乘 `AeromanipTargeting.forceMultiplier`；受 `canAffectNegatively`/承载/`noPhysics` 过滤。
- 释放契约：`LaunchHandle.close`、登录、换维、死亡、停服均静默清理（关闭租约、恢复喷嘴计时、清 map），结构、物理区域与惯性保留；不再调用 `restoreToGrid/settleToGrid`，结构不会自动变回方块，`LaunchRequest.restoreWhenSettled` 仅为存档兼容字段。每玩家同一时间一个待捕获/一个会话，新发射先终止旧会话并保留其结构。
- 验证：单测 2570 + Sable 11 零失败/错误/跳过（`build/p27-tests-final2.log`，任务 `test sableCompatTest -DisDev=true`）；`build -DisDev=true` 21 秒、`-DisDev=false` 33 秒通过（`build/p27-build-dev-final2.log`、`build/p27-build-release-final2.log`）。专服烟测 `run/native-structure-physics-smoke-server-p27-r3` PASSED、exit_status=0（881 tick），证据含 `hsjet_source_cells=1`、`hsjet_nozzle_placed`、`hsjet_capture_committed`、`hsjet_capture_pending_observed=true`、`hsjet_source_cleared`、`hsjet_block_count=1`、`hsjet_resource_charged`、`hsjet_nozzle_attached`、`hsjet_region_ready`、`hsjet_control_active`、`hsjet_expected_speed=120.0`、`hsjet_launch_speed=115.37633965527773`、`hsjet_thrust_direction`、`hsjet_travel=41.11`、`hsjet_region_never_failed`、`hsjet_control_cleared`、`hsjet_terminal_structure_active`、`hsjet_source_not_restored`、`hsjet_no_drops`、`hsjet_no_legacy_entity=true`；既有 `grasp_*`、`kinetic_*`、`support_*` 证据无回归（`build/p27-world-smoke-r3.log`；最终修订前一轮 `p27-r2` 亦通过，`build/p27-world-smoke-r2.log`）。Sable 专服烟测 `run/sable-compat/p27-server-r2`（`-PacademySableCapture=true`）PASSED、exit_status=0，含 `sable_capture_committed=true`、`sable_capture_rejected_be_preserved=true`（`build/p27-sable-server-r2.log`；无 capture 的 `p27-server-r1` 亦 PASSED，`build/p27-sable-server-r1.log`）。
- 复现命令：`. ./build/native-physics-p16-env.ps1; .\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-NEW @physicsNativeArgs -DisDev=true`；Sable 侧 `.\gradlew.bat runServerSable -DisDev=true -PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true -PacademySableRunDirectory=run/sable-compat/NEW`（需先按 P25 约定准备隔离目录）。
- 失败轨迹（目录与日志保留）：`run/native-structure-physics-smoke-server-p27-r1` 首个 THRUST tick 即 `follow_rebase_failed`——区域沿推力轴单侧扩展使结构贴尾边、`edgeDistance` 立即 <2，首次 rebase 候选与既有夹具区域间隔 <1 被拒；改为以捕获体为中心、在宿主可用空间内对称扩展推力轴，并把烟测源点移到 `base+(0,4,32)` 后 r2 通过（`build/p27-world-smoke-r1.log` 保留）。Sable 首个 `p27-server-r1` 目录未准备 `server.properties/eula` 导致启动失败/挂起，补齐隔离目录后通过；该次失败日志被同名成功重跑覆盖，只能按“目录未准备”记录，原始输出不可再核对。
- 未验边界：真实双端与真实 TCP/键盘输入，喷口结构跟随/客户端高亮的双端视觉未验；Sable 的喷流发射与飞行（捕获后保留，Sable 烟测不执行 hsjet 相位）；原生接触的每 tick 投影采样在 ≥120 格/s 下可能跨过薄目标（旧实体 swept 碰撞语义），烟测未触发/断言接触；极轻单格结构的落地摩擦/边界滞留；每玩家单会话（新发射终止旧会话）；每次使用最多新开一个物理区域（上限 3）且长期回收未实现（同 P25）；结构卸载后喷口结构支持回退为方块判定，找不到支撑 100 tick 后丢弃。
- 当前构建（含 P19–P27）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `1FDD6FE893B088F44F4263035926D7357B9C174150EC3EB3B0C5271587905C05`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `5BD03EDC3F6F82117B7E6377EF51483EBF74A82A6C5FCC69C10C2C26FB15FBAC`。未复制到整合包，未操作用户存档。

### P28 磁场操作迁移到公共结构服务（2026-10-14）

> 历史批次（P28，2026-10-14）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 改动/新增文件：新增公共 `MagneticStructureControl.java`（磁场牵引控制预算与旧速度曲线到原生力的换算）；新增内部 `MagneticStructureService.java`（吸附方块跨 tick 捕获、原生区域控制、P25 同构跟随、Sable 边界与释放契约）；重写 `MagnetManipulation.java` 的 Server/MoveContext 方块路径（实体路径、悬浮开关与资源/心跳语义不变；新增 `Events` 生命周期清理与 `Server.diagnostic/startTargetPull/stopTargetPull` 公共入口）；新增 `NativeStructurePhysicsMagnetManipulationSmokeChecks.java`、`MagneticStructureControlTest.java`、`MagneticStructureServiceTest.java`、`NativeStructurePhysicsSmokeCleanup.java`；扩展 `NativeStructurePhysicsWorldSmoke.java`（新 `MAGNET` 相位与开关）、`physics_world.init.gradle`（`academyStructurePhysicsMagnetManipulationSmoke` 透传）、中英文 `message.academy.magnet_manipulation.structure_pull_rejected`。
- 审计结论（迁移依据）：旧 `MagnetManipulation` **没有整结构/方块组路径**，也不引用 `BlockStructureApi/BlockStructureEntity/BlockStructureKinetics/beginGravitySettlement/asEntity`（全仓检索只命中旧实体自身与验证夹具）。旧方块路径仅把准星命中的**单格磁力方块**转为 `FallingBlockEntity`（`createFallingBlockTarget`，旧第 424–438 行），随后每 tick 直接赋速（`pullTargetToPlayer`，旧第 388–422 行），释放时清除 `GravityControl`（`releaseControlledTarget`，旧第 482–488 行）；筛选在 `findCrosshairTarget`（旧第 440–480 行）与 `createFallingBlockTarget`（`isMagnetic(state)`、无 BE、可破坏）；资源为 `movingTicks % 20 == 0` 时 `tryTimedOccupation(... MOVE_CP_COST=10 ...)`（旧第 372–377 行），无捕获费且不退款。据此范围收缩为“磁力筛选出的单格方块操纵”最小正确切片：只迁移这一条方块路径，不虚构多格结构捕获、旋转、静态化等旧实现不存在的语义；实体路径、悬浮（`MagneticFieldRuntime`）与全部筛选谓词保持不变。
- 捕获：`MagneticStructureService.begin` 复用旧入场谓词（`AbilityEffectPolicy.blockDestruction`、区块已载、`mayInteract`、`SkillTuning.allowBlockDestruction`、`MagneticTargets.isMagnetic`、无 BE、`getDestroySpeed ≥ 0`），随后确定性预算预裁 `cropToBudget`（NATIVE 128 / SABLE 256，经 `StructureBackendRouter.status().mode()` 判定；seed 优先）并走 `StructureTransactions.capture/poll` 跨 tick；`status:detail` 走日志 + 新 actionbar 文案，失败保留源方块。扣费时机沿用旧语义：捕获/牵引本身不收费，CP 仍是移动期间每 20 tick 10 点，捕获失败不退款。
- 控制：提交后原生以捕获格 + 每轴 ≤6 余量、宿主高度/世界边框夹取生成 ≤24 的区域盒，`StructurePhysics.openRegion` + `attach`，`motion` 出现并复核 `MagneticStructureControl.massBudget(milestone)`（128/256/512）后 `acquireEntityControl` + `StructurePhysicsControl.Force`，每 tick `setControl/renew`（租约 20 tick）；`Force` 每物理步被消耗属预期，会话状态以租约判定。移动跟随用投影 `worldCollisionBoxes` 预测，距区域边缘 <2 格时 `region.rebind`（P25 同构）；无法重绑时释放控制、保留结构、安全结束（不让区域 FAILED）。超距、租约失效、结构不可用均与旧实体路径一致地结束并保留结构。
- 标定依据：新公共 `MagneticStructureControl.pullForce` 直接复用旧 `MagneticMovement.calculatePullVelocity`（2.3 格/tick 上限、0.65 停止距离、0.2/0.8 平滑），经 `LegacyMotionUnits.velocityPerTick/velocityPerSecond`（÷20/×20）换算，取一个 0.05 秒物理步 `F = m·(v目标 − v当前)/0.05`，全矢量夹取在共享 `StructurePhysicsControl.MAX_FORCE` 之下；实测 `mag_expected_speed=46.0`、`mag_pull_speed=45.58`（99.1% 标定值）。
- Sable 边界：捕获经路由（混装创建 Sable 结构）；提交后若 `reference.backend()==academy:sable`，不做未标定的牵引，明确结束并保留结构（日志 + `sable_pull_unavailable` actionbar），不建旧实体、不回退旧路径。释放契约：`stopTargetPull`、登录/换维/死亡/停服静默释放租约；结构、物理区域与惯性保留，结构不回方块。
- 验证：单测 2580 项 + Sable 11 项零失败/错误/跳过（`build/p28-tests-final2.log`）；`build -DisDev=true` 21 秒、`-DisDev=false` 36 秒通过（`build/p28-build-dev-final2.log`、`build/p28-build-release-final2.log`）；专服烟测 `run/native-structure-physics-smoke-server-p28-r6` PASSED、`exit_status=0`（903 tick），证据含 `mag_source_cells=1`、`mag_filter_stone_rejected=true`、`mag_capture_pending_observed=true`、`mag_capture_committed=true`、`mag_source_cleared=true`、`mag_cp_charged=true`、`mag_region_ready=true`、`mag_control_active=true`、`mag_expected_speed=46.0`、`mag_pull_speed=45.57584523660533`、`mag_region_never_failed=true`、`mag_pull_travel=16.46`、`mag_follow_rebinds=2`、`mag_control_cleared=true`、`mag_terminal_structure_active=true`、`mag_source_not_restored=true`、`mag_no_drops=true`、`mag_no_legacy_entity=true`；既有 `grasp_*`、`kinetic_*`、`hsjet_*`、`support_*` 证据无回归（`build/p28-world-smoke-r6.log`）。
- 夹具槽位回收：原生容器仅 4 个永久槽，P25/P27/P28 三个捕获相位链式运行会耗尽；`NativeStructurePhysicsSmokeCleanup` 在 KINETIC/HSJ/MAGNET 各自 VERIFY 证据落盘后清空捕获内容并 `unload` + `discardEmpty`（仅测试夹具，保留断言先于释放；非生产路径）。r6 日志含 3 次 `Released empty native structure slot`。
- 复现命令：`. ./build/native-physics-p16-env.ps1; .\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsMagnetManipulationSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-NEW @physicsNativeArgs -DisDev=true`（新隔离目录并预置 `eula.txt`/`server.properties`；不要复用 r1–r6）。
- 失败轨迹（目录与日志保留）：`p28-r1` 在 MAGNET 因 `BACKEND_UNAVAILABLE:no_free_native_slot`（4 槽被 a/b/kinetic/hsjet 占满）失败（`build/p28-world-smoke-r1.log`）；`p28-r2` 加入槽位回收后仍失败，因清理误按宿主世界坐标清空而捕获目标以最小角为局部偏移（`build/p28-world-smoke-r2.log`）；`p28-r3` 修正偏移后失败于 `region.control` 在 Post 时因一步 Force 已被消耗而读空（`build/p28-world-smoke-r3.log`）；`p28-r4` 改为租约/阶段断言后通过（`build/p28-world-smoke-r4.log`）；`p28-r5` 收紧速度门限后通过（`build/p28-world-smoke-r5.log`）；`p28-r6` 为最终修订（仅 actionbar 文案措辞变化）全量复跑（`build/p28-world-smoke-r6.log`）。
- 未验边界（P28 当时；**P31 已迁移方块路径与程序节点，实体分支保留**）：Sable 的磁场牵引当时捕获提交后保留结构（本批未跑 Sable 专服烟测，`sableCompatTest` 11 项零失败）；真实双端与真实 TCP/键盘输入；多格或连通磁力结构（本节点只迁移旧有的单格语义）；旧 `FallingBlockEntity` 的落地磨损/伤害/变回方块表现未逐一对照；每次使用最多新开一个物理区域（上限 3）且长期回收未实现（同 P25/P27）。`ServerElectromasterProgramRuntime.magneticMove` 的方块目标已改为显式拒绝并走结构节点（P31），实体路径保留；Sable 技能拖动另于 2026-10-15 修复，真实客户端表现仍待复测。
- 当前构建（含 P19–P28）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `091B56C01D7778A48B9DE3FE557FD6094024D71DE3F29D53B8110F4005775706`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `8BFDC79DD0BA0523C34EDC2C4ECB04C354D204CE4F6393796C35DDB1215A5063`。未复制到整合包，未操作用户存档。

## 精密操作节点阶段设计（P29 起）

本节把计划书“精密操作节点的数据流与版本迁移”与三类能力职责表（`docs/block_structure_physics_and_sable_compat_plan.zh-CN.md` 第 571–603 行）落成阶段、节点、value types、迁移与验收计划。节点未实现前不得提前发布到编辑器、模板或翻译；未完成前置必须以明确拒绝返回，不得虚假交付，也不得用旧实体或静默成功顶替。

### 阶段划分

| 阶段 | 范围 | 出口条件 |
| --- | --- | --- |
| P29（本批） | 公共契约落点与矢量结构服务抽取：`VectorStructureService` 承接动能投掷全部结构逻辑；不新增节点 | 抽取后行为逐值等价，单测、烟测与双构建通过 |
| P30 | 矢量操作节点族：创建/提取、获取控制、移动、旋转、发射、终止控制、安全静态化 | 每类“创建 → 移动 → 旋转 → 终止”与“发射/静态化失败”图，玩家/程序/非玩家入口同服务 |
| P31 | 电击使节点族：磁性提取、显式强制磁化、磁性牵引/旋转/发射；`ServerElectromasterProgramRuntime.magneticMove` 从 `FallingBlockEntity` + 直接赋速 + `damageAlongTrajectory` 旧路径迁移 | 磁性参与质量、作用点与真实接触结算；旧路径删除或显式拒绝 |
| P32 | 空力使节点族：喷口锚点、局部方向逐物理子步、力偶旋转、停喷保留线角速度；把现有 `action/launch_block_structure` 纳入统一节点族 | 喷口破坏/卸载重映射、共享资源预算、对称喷口抵消验收 |
| P33 | 收尾：安全静态化拒绝路径、图版本迁移器、编辑器 palette/模板/翻译/剪贴板、全矩阵图验收 | 旧图不因 `schemaVersion` 被拒；静态化失败保留内容与恢复证据 |

### 节点职责与三套 ID

节点 ID 沿用各能力 `program/<ability>/action/...` 命名空间与 `action/` 前缀；三类能力采用同一路径形状，便于公共 value types、编辑器和迁移器复用。

| 职责 | 必需输入 | 输出与规则（含拒绝） | 三类路径（accelerator/electromaster/aeromanip 同形） |
| --- | --- | --- | --- |
| 创建/提取 | flow、选区或宿主格、能力配置、预算 | structure、result、flow；完成内容事务后才发布结构引用；选区空/被占/超预算/权限不足显式拒绝并保留源方块 | `action/structure/extract` |
| 获取控制 | flow、structure/anchor、能力控制配置 | control、result、flow；权限/占用/质量/范围拒绝均有明确结果，不抢占继任控制 | `action/structure/control` |
| 移动 | flow、control、世界目标或显式跟随锚点、持续时长 | result、flow；矢量动量修正、磁性牵引、气动受力保持不同约束；越界/失联安全结束并保留结构 | `action/structure/move` |
| 旋转 | flow、control、目标姿态或角运动、作用点/喷口配置 | result、flow；能力无法形成所需力矩时拒绝或返回有限结果，不无条件赋姿态 | `action/structure/rotate` |
| 发射 | flow、control、方向及冲量/能力强度 | result、flow；交接为有界发射操作，不能提前对整条预设轨迹结算伤害 | `action/structure/launch` |
| 终止控制 | flow、control | result、flow；幂等撤销自己的后续命令，保留结构与运动，不触及继任控制 | `action/structure/release` |
| 安全静态化 | flow、structure、目标网格姿态、放置策略 | result、flow；与终止分开，失败保留内容和恢复证据，不先 discard 再补方块 | `action/structure/staticize` |

- 公共查询节点 `program/core/query/look_structure`、`program/core/query/structure_motion`（P18）语义不变；行动节点不得按“该玩家最近控制的结构”隐式寻址，必须经显式 structure/control 端口。
- 空力使现有 `action/launch_block_structure`（P27 已走 `HighSpeedJetStructureService`）在 P32 映射到统一族；旧 ID 通过迁移器保留端口语义，避免旧图失效。
- 电击使的强制磁化是显式、付费、有期限的覆盖效果，与控制令牌分别授权与计费；一格铁不得给予整船无限推力。
- 安全静态化独立于终止：终止保留结构、惯性与重力；静态化必须先验证完整目标 footprint、加载、权限、占用和可支持的方块状态旋转，全部接纳后才撤销来源。

### value types 计划

只新增 P30–P32 真正需要的最小集合，避免过度设计；其余继续使用既有世界点/格/实体/向量类型：

| 类型 | 用途 | 约束 |
| --- | --- | --- |
| `structure_control` | 绑定 actor、runtime 与 invocation 的运行期控制令牌 | 不持久化、不可保存后自动恢复；失效后不重获权限 |
| `structure_operation_result` | 结构操作结果（status/detail/reference） | 机器可读拒绝原因，程序分支不解析文本日志 |
| `structure_anchor` | 结构局部格/作用点（磁性点、喷口支撑点、旋转作用点） | 运行时解析到世界位置；结构卸载/拆分后失效不猜测 |
| `structure_anchor_set` | 多作用点有限力组（力偶/旋转） | 合成总力与 `Σ(r × F)`，节点数/锚点数/拆分碎片不得复制预算 |
| `structure_pose` | 目标姿态或角运动输入 | 未具备有限力矩控制前旋转节点显式拒绝 |

- `structure_reference` 已有；`ACTION_RESULT` 保留通用动作语义，结构操作结果单独成类型以携带 reference 与拒绝码。
- 编辑器 palette/options、模板、中英翻译、剪贴板和导入导出随类型发布同步更新；未实现类型不得提前出现在可选列表。

### 控制令牌

- 获取控制成功后签发，绑定 actor（玩家/非玩家资源提供方）、runtime 与 invocation；同一结构同一时刻只有一个独占令牌。
- 不写入存档、不跨会话恢复；持久保存的结构身份在运行时重新解析和授权，失效令牌不得重获权限。
- 终止幂等：重复终止是空操作；只撤销本令牌自己下发的后续命令与持续力，保留结构、区域与惯性；绝不夺取或影响继任者的控制。
- 令牌不是权限：reach、保护、资源仍由 actor context 逐次校验；使用公共 API 不产生无限预算。

### 版本迁移

- 三套能力图、节点 schema 与二进制 envelope 分别迁移；旧图保留原端口语义，由显式迁移器补齐新类型端口并保留原数据。
- 不得直接把 catalog `schemaVersion` 改成 2 导致当前严格版本检查拒绝旧图；迁移器必须能回读旧 action 节点并保留其端口。
- 控制令牌不可持久恢复；迁移后的图在运行时重新获取控制并再次授权。
- 编辑器元数据、翻译键、模板、剪贴板与 `*ProgramExecutionBridge` 的 stage/undo 补偿同步迁移，`ProgramActionTransaction` 补偿按计划书第 603 行区分未提交内容与已参与物理的效果。

### 验收计划与未实现前置

- 每类能力验收两张图：“创建 → 移动 → 旋转 → 终止”与“发射/安全静态化失败”；玩家、程序、非玩家入口必须复用同一能力服务，不新增旁路。
- 内容守恒、资源预算、伤害去重与停止控制后的自由运动按计划书“后续迁移清单与验收”第 6 项执行；真实接触才结算伤害，不提前对预设轨迹结算。
- **未实现前置，不得虚假交付**：有限力矩姿态控制（角速度反馈、最短四元数误差、有限力矩与角冲量预算）与安全静态化（完整 footprint/加载/权限/占用/方块状态旋转）尚未实现。P30 前 `action/structure/rotate` 与 `action/structure/staticize` 必须返回明确的 `unavailable` 结果，发射节点不得预结算整条轨迹，普通移动/旋转不得用 `detach → setPose → attach` 顶替。
- 安全静态化失败图：目标 footprint 被占、未加载、权限不足或不可放置时拒绝并保留唯一内容所有者；成功结束明确释放控制或移交有期限操作。

### 现状（P29 开始时）

- P29 抽取前 `KineticThrow` 的结构逻辑全部在技能类内（`cropToBudget`、`StructureTransactions.capture/poll`、区域挂载、Anchor 抬升、冲量发射、逐 tick 跟随、接触伤害/击退、释放清理）；P29 已抽到 `VectorStructureService`，抽取前后对照与验证见下节。
- 三套 `Server*ProgramRuntime` 尚无任何结构行动节点：只有公共查询节点 `query/look_structure`、`query/structure_motion`，以及空力使 `action/launch_block_structure`（P27 已走 `HighSpeedJetStructureService`）。
- `ServerElectromasterProgramRuntime.magneticMove` 仍走 `FallingBlockEntity` + 直接赋速 + `damageAlongTrajectory` 预设轨迹伤害旧路径（P31 迁移）。
- Sable 侧只有 P24 捕获与 P18 查询/牵引；Sable 的发射、飞行与静态化必须显式拒绝，不回退旧实体、不静默成功。

### P29 矢量结构服务抽取（2026-10-14）

> 历史批次（P29，2026-10-14）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 改动/新增文件：
  - 新增 `src/main/java/org/academy/internal/common/ability/accelerator/movement/VectorStructureService.java`（967 行）：捕获选区/预裁/事务、区域挂载与 Anchor 抬升、Sable 牵引抬升、冲量发射、逐 tick 跟随 rebind、接触伤害/击退、释放清理，以及 `Stage`/`Diagnostic`/`CapturePlan`/`Session`。
  - 重写 `src/main/java/org/academy/internal/common/ability/accelerator/skills/lv3/KineticThrow.java`（1268 → 428 行）：保留 HUD/按键/网络包/蓄力/资源执行/入口/生命周期清理；`releaseCharge` 的选区改为服务 `planCapture`，扣费仍在 `executeActive` 内，随后 `beginCapture` 启动事务；`throwHeld`/`stopHeld`/`diagnostic` 委托服务（Diagnostic 使用服务类型）。技能几何、每格伤害与投掷方向公式保留为公开静态配置，与 P27 服务读取 `HighSpeedJet.*` 同构。
  - 新增 `src/test/java/org/academy/internal/common/ability/accelerator/movement/VectorStructureServiceTest.java`：从 `KineticThrowTest` 原样迁移 `cropToBudget`/`regionBounds`/`followBounds`/`edgeDistance` 四个用例，断言与期望值一字未改。
  - `KineticThrowTest.java`：保留蓄力几何（`chargeTier`+半径表）、每格伤害、投掷方向三个用例；删去的四个用例在服务测试中等价存在，测试总数不变。
  - `NativeStructurePhysicsKineticThrowSmokeChecks.java`：仅把 `KineticThrow.Server.Stage.CAPTURING` 引用改为 `VectorStructureService.Stage.CAPTURING`（与 P27/P28 烟测引用各自服务 `Stage` 一致），所有证据与断言未改。
- 抽取前后对照：
  - 技能内 `CHARGES`/`CAPTURES`/`HELD_STRUCTURES` 三表：`CHARGES`（蓄力输入）留在技能；`CAPTURES`/`HELD_STRUCTURES` 原样变为服务 `CAPTURES`/`SESSIONS`，键仍为玩家 UUID，pending 事务与已持结构并存的既有语义保留。
  - `captureAndLift` 的选区、前方半空间裁剪、上方安全裁剪与确定性预算预裁成为服务 `planCapture`（只读，拒绝日志与 actionbar 文案不变）；`StructureTransactions.capture/poll` 与跨 tick `CaptureState` 成为服务 `beginCapture`/`tickCapture`。
  - `HeldStructure` → 服务 `Session`：stage/anchor 控制/Sable 牵引/expiry/impact latch/不可用宽限逐字段迁移；`releaseHeld` → `terminate`（幂等关闭 lease 与牵引，保留结构与物理区域及惯性）。
  - `followRegion`/`tryRebind`/`applyImpacts`/`settleImpact`、`projection`/`union`、`lookedAtStructure`/`aimedAtHeld`/`viewerInsideStructure`、`playKineticCue`/`playImpactCue`、`capturePolicy`/`capturePermission`/`cellBudget`/`sableMode`，以及 `cropToBudget`/`regionBounds`/`edgeDistance`/`followBounds` 与私有几何工具全部逐行搬迁。
- 服务入口（本批只留入口，不接节点）：`planCapture`/`beginCapture`/`session`/`tick`/`launch`/`terminate`/`clear`/`clearAll`；`Session` 暴露 stage/reference/blockCount/lifted/controlActive/flying/sable/impacts，供 P30 程序节点与查询复用；`CapturePlan` 携带 seed/tier/positions。
- 行为等价：专服烟测 `run/native-structure-physics-smoke-server-p29-r1` PASSED、`exit_status=0`（903 tick）；结果文件 258 行中 `kinetic_*`、`hsjet_*`、`mag_*`、`support_*`、`grasp_*`、`terrain_*` 等全部证据与 P28 r6 逐行一致，仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID 不同（`kinetic_lift_rise=3.2498730230960433`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=45.0`、`kinetic_follow_rebinds=10` 等未变）。
- 验证：单测 2580 项 + Sable 11 项零失败/错误/跳过（`build/p29-tests-r1.log`）；`build -DisDev=true` 22 秒、`-DisDev=false` 35 秒通过（`build/p29-build-dev.log`、`build/p29-build-release.log`）；编译日志 `build/p29-compile.log`；烟测日志 `build/p29-world-smoke-r1.log`。
- 失败轨迹：本轮烟测首轮（也是唯一一轮）即通过，无失败目录需要保留；服务初稿中 `edgeDistance` 曾有一处转录笔误，在首次编译前人工复核修正，未进入任何构建、测试或世界运行。
- 未验边界：Sable 的矢量发射与飞行仍未实现（捕获提交后保留结构，同 P25）；悬停 10 秒/飞行 30 秒超时只释放控制；每次使用最多新开一个物理区域（上限 3）且长期回收未实现；真实双端与真实 TCP/键盘输入未验；程序节点、非玩家施术者、旋转与安全静态化不在本批。
- 当前构建（含 P19–P29）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `E29EB6C03BC1448714C7876E805CB23B0EEE88F919E2190C1299D465610BC774`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `D529315355B5725E2A1F24437D309619196B6049252662FA96A0DCFBFE4B7DF3`。未复制到整合包，未操作用户存档。

<!-- P24 第一步历史记录（保留供核对）
- 新增 `CaptureTarget`（transaction 包内接口）：`allocate/cellBudget/ready/writeCell/readCell/clearCell/quarantined/deactivate/save/releaseEmpty/resolve(recovery)/verifyCopy/backendId`；新增 `NativeCaptureTarget` 独占全部 `NativeStructureRuntime`/`StructureHandle`/几何预算调用。`StructureCaptureCoordinator` 不再直接接触任何后端类型，恢复路径经 `targetFor(server, record.backend())` 按日志 `Record.backend` 分派（目前仅原生，非原生仍保留记录并报错）。公共 API、状态/明细/日志文本、journal 阶段与保留/回滚语义均未改变。
- 验证（行为等价）：单元 2558 + Sable 11 零失败（`build/p24a-tests-final.log`）；事务 gametest `run/native-structure-transaction-gametest-r9` 通过（完整提交、拒绝、回滚/前向恢复、容量回收，`All 1 required tests passed`）；`build -DisDev=true/-DisDev=false` 均通过。
-->

### P18 最终结果（2026-10-13）

> 历史批次（P18，2026-10-13）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

| 验证 | 结果 / 证据 |
| --- | --- |
| 全量测试 | `build/p18-tests-final.log`：2530 个基础测试、11 个 Sable 测试通过，零失败/错误/跳过；包括新结构类型的图编译、变量保存、转发运行时、质心与物理单位 |
| 构建 | `build/p18-build-dev.log` 23 秒通过；`build/p18-build-release.log` 33 秒通过；包括编辑器测试及 Sable 打包/依赖门禁。均基于最终代码，之后仅补充文档 |
| 独立专服 | `run/native-structure-physics-smoke-server-20261013-p18-r3`：572 tick、退出 0、正常服务端线程结束；真实技能资源、运动、释放、NPC、程序整结构查询及旧 activation 拒绝通过；日志 `build/p18-native-server-r3.log` |
| 最终 Suite 集成客户端 | `run/sable-compat/p18-compound-suite-client-r3/client`：25 格、质量 50、55 tick；查询、真实气流牵引、手杖互斥、释放及回调撤销通过，客户端观察 47 个运动样本，退出 0；截图 `screenshots/academy-sable-structure-20688.png` 已查看；日志 `build/p18-compound-suite-client-r3.log` |
| Suite 客户端 r2 失败 | 等待截图 410 tick 后技能结束；测试 options 开启失焦暂停，未出现截图完成日志。r3 仅将新隔离目录 `pauseOnLostFocus` 设为 false 后通过；不能把 r2 计为成功 |
| 本轮独立客户端 | `run/native-structure-physics-smoke-client-20261013-p18-r1` 已准备测试元数据，**尚未启动**。用户按 Esc 停止 Computer Use 后未继续窗口操作；`runClientDev` 新回归和节点编辑器人工检查待验 |
| 站立抖动 | 新后端承载未实现，旧实体对照夹具未运行，不计为修复 |

验证动作由服务器夹具发起；Sable 施术者是嵌入连接，未冒充真实 TCP/UDP 多人输入。相机为真实集成客户端，`actual_keyboard_actions=false`。r1/r2 失败结果均保留。原生 ABI/Rust/存储写入逻辑未改动，可复用 P17 对应专项；本轮没有重新声称已做全矩阵。

最新构建仍**不含动能搬运新后端捕获/承载修复**：

- `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256：`2F3E99EC1F686FB0CC5881C7EF5E196937DEC88BC3B3FA1DC84EFAE7C2E73332`。
- `build/libs/academy-26.2.0-0.0.4-alpha-release.jar` SHA-256：`03487257FFD40D38AB9BE6B14230B9B189F21A5A28D17DB0FBAB060EB50753FE`。
- 未复制到用户整合包，未操作用户存档，未创建本地 Git 提交。

重跑命令模板（每次先准备新目录，替换 `NEW`，不要复用本批 r1/r2/r3）：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-NEW @physicsNativeArgs -DisDev=true
./gradlew.bat runClientSable -PacademySableRuntime=suite -PacademySableSmoke=true -PacademySableCompound=true -PacademySableRunDirectory=run/sable-compat/NEW -DisDev=true
```

独立专服应采用固定种子超平坦新世界，测试空间在 y≈105–120；随机高地会被正确拒绝。客户端只复制既有隔离测试世界的 `level.dat` 和 `data/minecraft/world_gen_settings.dat`，不复制旧区块/结构；仅在新测试目录设置 `pauseOnLostFocus:false`，避免等待客户端观察时服务器夹具继续消耗资源。用户已停止本轮窗口自动化，不应在本轮继续输入或启动另一个客户端。

## 前一批可复用证据及未完成项

- 最新人工测试 JAR 于 2026-10-13 构建；`build/manual-test-20261013-{tests,build-dev,build-release}.log` 通过。基础 2527、Sable 11、编辑器 104 项零失败/错误/跳过。
- 开发 JAR SHA-256：`A9ACBB77DA0C787ABA5E4C65751DAE324C76E58296C82B3257B34D28D6431C03`；发布 JAR：`34940D99DBC87EA4EF707AEFF303D4E345FAAD5D471A21B487E8AE07A3E8A0AB`。这两份正是用户报告问题前提供的包，不代表本次问题已验证。
- P17 纯 Sable 专服、纯 Sable/Suite 集成客户端、独立物理专服/客户端通过限定场景，详见 `docs/block_structure_entity_system.md` 第十七批。
- 固定 Suite 专服在外部模组构造时加载客户端模型类而失败；`build/sable-p17-suite-server-r1.log`。客户端通过不代表专服通过。
- `run/sable-compat/p17-retention-r1` 完整旧世界副本运行 120 tick 的内容保护断言通过，但退出夹具竞争写入 FAILED；外部核验失败，不能计为通过。夹具修复/新副本重跑尚未完成，原失败文件必须保留。
- `SableStructureHandle.read` 已修复 bbox 内缺 holder 不得返回 AIR；编译/基础测试通过，专项真实缺 holder 场景尚未运行。
- Sable 生产捕获已完成（P24，`SableStructureTransactions.java:47` 接入 P19 协调器）；可恢复静态化成功路径、跨后端迁移、通用拆分/接触仍未完成。公开装配 helper 仅用于隔离测试，不可直接作为生产内容事务。

## 接续操作入口

使用 JBR 25：`C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2`。可在仓库根 `. ./build/native-physics-p16-env.ps1` 设置既有验证环境；该文件不提交。

```powershell
.\gradlew.bat test sableCompatTest -DisDev=true
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
.\gradlew.bat runGameTestServer -I validation/native_structure/transaction.init.gradle -PacademyGameTests=academy:structure_transactions -PacademyStructureTransactionDirectory=run/native-structure-transaction-gametest-NEW -DisDev=true
```

外部固定依赖由 `docs/sable_compat_artifacts.json` 与 `gradle/sable-compat.gradle` 锁定。不要为了启动未知 JAR 放宽哈希门禁。新游戏验证使用新的隔离目录，不覆盖成功或失败结果，不操作用户存档。

后续每完成一个节点在此增加：修改文件、行为改变、验证命令及结果、失败轨迹、未验边界和紧接着应做的工作。已有完整架构设计位于 `docs/block_structure_physics_and_sable_compat_plan.zh-CN.md`。

### P30 矢量操作节点族（2026-10-14）

> 历史批次（P30，2026-10-14）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 改动/新增文件：
  - 新增 api value types：`ProgramStructureControl.java`（绑定 actor、结构代次、服务 controlId 与运行实例令牌；不持久、失效不重获）、`ProgramStructureOperationResult.java`（status/detail/reference）、`ProgramStructureAnchor.java`（结构代次 + 局部格）、`ProgramStructurePose.java`（轴 + 弧度目标姿态）；`ProgramValueTypes` 增加 `structure_control`、`structure_operation_result`、`structure_anchor`、`structure_pose`。未新增 `structure_anchor_set`（本批无节点端口需要）。
  - `AcceleratorProgramNodeIds.java` / `AcceleratorProgramNodeCatalog.java`：新增 `program/accelerator/action/structure/{extract,control,move,rotate,launch,release,staticize}` 七个节点。extract 配置 `tier`(0–2)/`budget`(0=后端默认)，move 配置 `duration_seconds`(0.1–10)，launch 复用 `strength`；extract 输出 `structure`/`result`，control 输出 `control`/`result`，其余输出 `result` + flow；rotate 可选 `pose`、staticize 可选 `target`，节点本身明确 unavailable。
  - `AcceleratorProgramRuntime.java`：新增 7 个结构方法与嵌套 `StructureOperation(structure/control/result)`；结构效果直接落入长期会话，不进入动作事务补偿（与设计“已参与物理的效果不按未提交内容回滚”一致）。
  - `ServerAcceleratorProgramRuntime.java`：extract/control 同步调用 `VectorStructureService` 程序入口，只有内容事务提交后才返回结构引用；move/launch/release 走服务程序入口并逐次计费（extract 30/control 10/move 5/launch 15 CP × 倍率）；rotate/staticize 不校验技能开关、直接返回 `unavailable`，不触碰控制。控制令牌绑定 player UUID、结构 runtimeId、服务 controllerId 与本次 runtime 实例（`runtimeToken`，不落盘），跨调用/跨程序失效即拒绝。
  - `AcceleratorProgramExecutionBridge.java`：装配 7 个执行器与可选输入（target/anchor/seconds/pose）和结果端口；`compileFor` 增加已学习的 `kinetic_throw` 能力。
  - `AcceleratorProgramCapabilities.java`：新增 `STRUCTURE = academy:kinetic_throw`，7 个节点统一使用。
  - `AcceleratorProgramDefinition.java`：新节点默认配置（tier 1/budget 0、duration 1.0s、strength standard）与编辑器条目；rotate/staticize 可见但描述明确 unavailable，未进入任何模板（`ProgramStarterTemplates` 只使用入口/输出/文本三个节点）。
  - `VectorStructureService.java`（改动物理路径）：`planCapture`/`beginCapture` 拆出无副作用 `planCaptureInternal`/`startCapture` 并加 `report` 标志（程序路径不发送技能 actionbar，日志与拒绝语义不变）；新增程序入口 `programExtract`（显式 `COMPLETED/PENDING/REJECTED`，PENDING 不发布引用、失败保留源方块）、`programControl`（复用同结构会话，或接管已有活跃物理区域并签发独占会话；他人控制/捕获中的结构拒绝；替代旧会话时保留旧结构、区域与惯性；Sable 明确 unavailable）、`programMove`（有界锚点修正 + 时长上限 600 tick，越界/失联安全结束并保留结构，program move 时 `tickLifting` 复用 `followRegion` 重绑区域）、`programLaunch`（显式方向有界冲量，`launchNative` 增加方向与强度倍率参数，技能路径仍传视角方向与 1.0，逐值不变）、`programRelease`（幂等、仅匹配本令牌时终止，绝不触碰继任会话）；`Session.controllerId()` 访问器与 `programMove` 标志。
  - 翻译：en_us/zh_cn 各新增 7 个节点名与说明、5 个端口键（anchor/control/host/pose/seconds）与 3 个配置字段端口键（tier/budget/duration_seconds），均按自然排序插入。
  - 测试：新增 `AcceleratorProgramStructureNodesTest`（4 项）；`AcceleratorProgramExecutionBridgeTest.FakeRuntime` 补齐新接口桩。
- 两张图验收（真实性级别：目录、schema、编译器、程序 VM、`AcceleratorProgramExecutionBridge`、节点执行器与动作计数为生产实现；accelerator runtime 为脚本化接口实现，不落地真实方块捕获/物理，真实世界程序图执行列为未验边界）：
  - 图一“创建→获取控制→移动→旋转→终止”：`extract(host,1,0)` → `control(structure)` → `move(control,target=12.5,70,10.5,1.0s)` → `rotate` 返回 `unavailable` 且同一令牌随后仍可终止（控制未被破坏）→ `release` 两次（第一次 `released`、第二次 `noop`，结构保留）。VM `COMPLETED`，调用顺序与参数逐字断言。
  - 图二“发射/静态化失败”：`extract(host,2,64)` → `control` → `launch(direction=+Y,MAXIMUM)` 记录有界冲量 75 且无任何轨迹伤害结算 → `staticize` 返回 `unavailable` 且唯一内容所有者保留。
  - 另加 PENDING 提取图：extract 返回 PENDING 时不发布 `structure` 输出，程序仍完成（机器可读 result 为 PENDING）。
- 回归验证：
  - 单测 2584 项 + Sable 11 项零失败/错误/跳过（`build/p30-tests-r2.log`、`build/p30-sable-r2.log`；基线 2580，本轮 +4）。
  - `build -DisDev=true` 22 秒、`-DisDev=false` 35 秒通过（`build/p30-build-dev-final.log`、`build/p30-build-release-final.log`）。
  - 因改动 `VectorStructureService`，专服烟测新隔离目录 `run/native-structure-physics-smoke-server-p30-r2` PASSED、`exit_status=0`（903 tick，`build/p30-world-smoke-r2.log`）；结果文件 258 行与 P29 r1 逐行一致，仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID 不同；关键证据 `kinetic_capture_pending_observed=true`、`kinetic_lift_rise=3.2498730230960433`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=45.0`、`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_pull_speed=45.57584523660533`，grasp/support/terrain 全部逐值无回归。p30-r1 同配置先行通过，r2 为最终修订（仅 import 整理）复跑。
- 失败轨迹：本轮首轮 `compileJava` 即失败（`StructureCaptureResult` 漏导入、桥接 lambda 需显式参数类型才能推断配置泛型），未进入任何运行；首轮全量单测 2 失败：`ProgramEditorDocumentTest` 缺 `port.tier/budget/duration_seconds` 翻译、`SkillProficiencyCoverageTest` 语言键未按自然序（节点键插入位置），修复后通过。世界烟测 p30-r1/r2 无失败目录保留。
- 未实现前置（仍 unavailable）：`action/structure/rotate` 缺有限力矩姿态控制（角速度反馈、最短四元数误差、有界力矩与角冲量），`action/structure/staticize` 缺安全静态化（完整 footprint/加载/权限/占用/可旋转方块状态）；两者均返回明确 `unavailable`，不无条件赋姿态、不用 `detach → setPose → attach` 顶替、不先 discard 再补方块，失败保留内容与唯一所有者。
- 未验边界：程序真实世界图验收（原生 extract 跨 tick 捕获，单次程序执行无法在提交前取得引用，故“创建→移动”同图只做脚本运行时验证；真实世界仅验证服务与技能回归）；非玩家施术者入口；真实双端与真实键盘输入；Sable 的程序控制/移动/发射（显式拒绝，未跑专服）；`programControl` 只接管已有活跃物理区域或既有会话的结构，不会为无区域结构新开区域；每玩家同时只有一个程序会话，程序移动上限 600 tick，物理区域长期回收同 P25–P29。
- 当前构建（含 P19–P30）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `E14EBC9E39220D2D98E40C168D5FB0BEE6E84B6A9683293AD6765FECCD0DE88D`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `E616D3E42D950B6CB651A161F4F876048E2114D7A2A2399F5784C647F593AC27`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 修复：捕获白名单扩展与随机刻语义放开（2026-10-14）

- 根因（真实客户端用户日志已定位）：`NativeCaptureWhitelist.rejectionDetail` 以 `state.isRandomlyTicking()` 一刀切拒绝，并只收显式列出的方块；真实地形的草方块落入捕获选区后，`StructureCaptureCoordinator` 预检对任一格失败即整笔 fail-closed，动能投掷/H 键因此报 `REJECTED:random_tick_not_supported`。既有烟测全绿是因为夹具只用石/台阶/楼梯，且旧 `StructureTransactionGameTests` 把该拒绝写成了期望断言。
- 政策变更：`NativeCaptureWhitelist` 不再按随机刻语义拒绝，改为“精选集合即安全边界”。草/菌丝等会随机刻的表面全块进入精选集后即可捕获；捕获后的结构空间不跑随机刻，内容保持惰性。保留 scheduled tick 待处理门（格子正处于行为中，复制后可能立刻变化）；保留 BE、waterlogged、流体、非全块、不可破坏、重力方块门。
- 扩集清单（刻意数据变更）：`GRASS_BLOCK`、`MYCELIUM`、`MOSS_BLOCK`、`SNOW_BLOCK`、`CLAY`、`GRAVEL`。其中 `GRAVEL` 是重力方块，加入后仍被既有重力门以更精确的 `gravity_block_not_supported` 拒绝；农田/土径与非全块植被仍不在集合内。
- 植被处理：审计 `VectorStructureService.planCaptureInternal` 后确认非全块植被会进入捕获集（`capturePolicy` 只要求非空气且可破坏）。新增确定性选区裁剪 `BlockStructureApi.cropNonFullBlocks`（纯函数 `BlockStructureManager.cropNonFullBlocks`，按 `isCollisionShapeFullBlock` 排除草簇/花），在 `cropImmediatelyBlocked` 之后应用；这是选择策略而非静默丢内容，交易预检仍对漏网非全块 fail-closed；未加载/越界格保留以便仍显式报错，空气本来就不在选区内。seed 被裁掉时仍拒绝整笔（`selection_blocked_or_seed_missing`），不做静默重锚。
- 改动文件：`NativeCaptureWhitelist.java`（政策注释、扩集、移除随机刻门、精选集放入惰性 holder 并把准入判定抽为纯函数）、`BlockStructureManager.java`、`BlockStructureApi.java`、`VectorStructureService.java`、`StructureTransactionGameTests.java`、`BlockStructureSelectionTest.java`（+2）、新增 `NativeCaptureWhitelistTest.java`（3 项：精选全块准入、各结构门与拒绝原因、scheduled 门优先级）。
- 验证：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：单测 2589 项 + Sable 11 项零失败/错误/跳过（基线 2584，本轮 +5；`build/fixwl-tests.log`）。
  - 事务 gametest 新隔离目录 `run/native-structure-transaction-gametest-fixwl-r1`：`All 1 required tests passed`（`build/fixwl-gametest-r1.log`）。夹具改动：旧的 `GRASS_BLOCK → random_tick_not_supported` 期望替换为 `FARMLAND`/`SHORT_GRASS → not_in_plain_block_whitelist`，新增水方块 `fluid_not_supported`，并新增“随机刻草地+泥土提交成功”用例（断言 `isRandomlyTicking` 为真、目标内容与源一致、源被移除、日志回收、释放槽位不影响后续容量断言）。
  - 世界烟测新隔离目录 `run/native-structure-physics-smoke-server-fixwl-r1` PASSED、`exit_status=0`（903 tick，`build/fixwl-world-smoke-r1.log`）；结果文件 258 行与 P30 r2 逐行一致，仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID 不同；关键证据 `kinetic_capture_pending_observed=true`、`kinetic_lift_rise=3.2498730230960433`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=45.0`、`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_pull_speed=45.57584523660533`、`support_rider_standing=true`，grasp/support/terrain 全部逐值无回归。
  - `build -DisDev=true` 25 秒、`-DisDev=false` 39 秒通过（`build/fixwl-build-dev.log`、`build/fixwl-build-release.log`）。
- 失败轨迹：首轮定向单测失败——`NativeCaptureWhitelist` 及 `Blocks` 的类初始化在纯 JUnit 下需要游戏 bootstrap（先 `Not bootstrapped`，改用 `Bootstrap.bootStrap()` 后因无 FML Loader 再失败）。修复方式为把准入判定抽成不触碰注册表的纯函数、精选集改为惰性 holder，并使夹具改用真实方块断言（`isRandomlyTicking`、实际提交）。失败轮输出被同名 Tee 日志覆盖，未单独留存；实现、gametest 与烟测最终均一次通过，无失败运行目录需要保留。
- 未验边界：真实客户端需用户复测（本轮只有用户日志定位，未启动真实客户端）；瞄准点本身是非全块植被时 seed 被裁剪，整笔以 `selection_blocked_or_seed_missing` 拒绝（有意不静默重锚）；非全块植被不被捕获，结构抬升时留在原地；精选集外的矿石/沙（重力）/其他方块仍 fail-closed；`not_full_block` 门当前无可达精选方块，仅由纯函数单测覆盖；随机刻仅在“结构空间不跑随机刻”的设计前提下可接受。
- 复现命令（每次使用全新 `fixwl-*` 隔离目录，不覆盖成功/失败产物）：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`
  - `.\gradlew.bat runGameTestServer -I validation/native_structure/transaction.init.gradle -PacademyGameTests=academy:structure_transactions -PacademyStructureTransactionDirectory=run/native-structure-transaction-gametest-fixwl-r1 -DisDev=true`
  - `.\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsMagnetManipulationSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-fixwl-r1 @physicsNativeArgs -DisDev=true`（目录需预置 `eula.txt`、`server-ip=127.0.0.1`、`level-name=native-structure-physics-smoke` 的 `server.properties`，且世界目录尚不存在）
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `A9DABD667B2359BB43E45C60029B4825872CBAABC2AA758825249A9EBDB8C4C1`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `B4F8A121B2085853F36B0DA5194316AA4852D017B19FA1256D836C27B4D04456`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 修复：Sable 容器重入崩溃与投掷/悬浮/内容扩展（2026-10-14）

- 崩溃机制判定（证据等级：字节码级，可复核）：
  - 崩溃报告 `crash-2026-10-14_19.23.25-server.txt`：`ArrayIndexOutOfBoundsException: Index 10 out of bounds for length 10` 栈顶 `ReferenceArrayList.forEach:726` ← `SubLevelContainer.tick:144`。`javap -p -c -l` 复核该 Sable 制品（SHA-256 与 `docs/sable_compat_artifacts.json` 锁定值一致）：`tick()` 第 144 行是 `allSubLevels.forEach(SubLevel::tick)`，第 147 行才是 `observers.forEach`。
  - fastutil 8.5.18 `ReferenceArrayList.forEach` 在循环开始时缓存底层数组引用、每次迭代读取实时 `size`；迭代期间任何 `add` 导致底层数组扩容后，旧数组仍被按下标读取，`size` 超过旧容量即抛该异常。
  - 增删来自 Sable 自身：`ServerSubLevel.tick()` → `SubLevelHeatMapManager.tick/step`（热图分裂）→ `split()` → `SubLevelAssemblyHelper.assembleBlocks` → `SubLevelContainer.allocateNewSubLevel`（`allSubLevels.add`，`javap` 可见该调用链；`SableCommonEvents.handleBlockChange` 在真实 `chunk.setBlockState` 路径上以 `onSolidAdded` 播种热图，我们的捕获写入与玩家编辑都会进入）。崩溃发生在 `ServerLevel.tick` 的 HEAD（`sable$tickPlotContainer` 注入点），实体 tick 在其之后，故“实体 tick 重入”假设被排除；用户设置的仓库集成侧风险仍是惰性 `addObserver` 与捕获提交/回滚可在迭代中改容器。
  - 16 秒前的 `sable_target_not_visible` 与"崩溃前最后一次捕获"同源，但不是本次崩溃的增删来源；证据强度：崩溃机制字节码级确证，具体触发方块变化无法从日志逐格复原。
- 修复设计：
  - 版本门控兼容补丁：新增 `SableContainerMixinGate`（只校验 Sable 版本与 SHA-256）与 `MixinSableSubLevelContainerTick`（`@Pseudo`，仅对锁定制品应用，`require = 1`）。`tick()` 两个 `List.forEach` 改为快照迭代——迭代中新增的子级从下一 tick 开始 tick，删除仍由 `processSubLevelRemovals` 负责；每个回调包在 `SableContainerGuard.enter/exitContainerTick` 的 try/finally 内。不同 Sable 制品自动不应用而不是启动失败。
  - 延迟队列与守卫：新增 `SableContainerGuard`（无任何可选 mod 类型引用）。容器回调内的 `addObserver`/`removeSubLevel` 一律入队，`ServerTickEvent.Post`（所有等级 tick 完成后）统一 drain，单项失败只记日志不阻断后续；`allocateNewSubLevel` 是同步契约无法延迟，检测到重入时 fail-closed 返回空并记录错误，交易以 `no_free_native_slot` 干净失败、不产生半成品。守卫带不平衡 exit / tick 内 drain 断言与 `enteredCallbacks()` 校验计数。
  - 观察者急切注册：`StructureBackendRouter` 在 `LevelEvent.Load`（每个 `ServerLevel`）与 `ServerStartedEvent` 调 `Bridge.observeLevel`；`SableStructureAccess.observe` 立即注册。`state()` 惰性兜底保留，但注册走同一守卫通道。
  - 改动协作：`SableStructureTransactions.allocate/releaseEmpty/removeQuietly`、`SableStructureAccess.state` 接入守卫/队列；`SableStructureSmoke` 断言 `sable_container_tick_guard_active=true`（真实容器 tick 至少进入过一次被守卫的回调，证明 mixin 生效）。
- 左击投掷链审计与修复：
  - 真实失败点 1（Sable 会话必然秒退）：`beginSableLift` 创建 `Session` 时漏设 `expiresAt`（默认 0），下一个玩家 tick 的 `tick()` 即判定超时并终止——即使抬升成功，左击时也没有会话。修复：`beginSableLift` 设 `expiresAt = gameTime + HOVER_TICKS`。
  - 真实失败点 2（显式未实现）：`VectorStructureService.launch` 对 `held.traction != null` 直接记录"not implemented"并终止。修复：新增中性 `StructureBackendRouter.launch` + `Bridge.launch` 默认拒绝，Sable 桥实现 `SableStructureTraction.launch`：锁定制品物理管线上以 `addLinearAndAngularVelocity(目标速度−当前速度, −角速度)` 精确赋速（同原生 `setVelocity(ZERO)` + 质心冲量的语义），有独占会话占用时拒绝；随后会话进入 `FLYING`，`tickSableFlight` 只观察惯性（Sable 自管接触），到期释放控制保留结构。
  - 客户端链审计结论：`InputSystem` 的 `KEY_NAME_THROW` 绑定 `MOUSE_BUTTON_LEFT/PRESS`，`MixinMouseHandler` 仅在 `MouseButtonEvent` 被取消时 `ci.cancel()`，本技能从不取消；因此按住悬浮时左击同时保留原版攻击行为，非持有时左击只是发一个被服务器拒绝的包，不吞攻击、无客户端状态门。瞄准门保留（必须瞄住本结构或身处其中），与旧实现一致。
- 悬浮 10 秒语义：抬升完成（`lifted` 置真）时 `expiresAt = hoverDeadline(gameTime) = gameTime + HOVER_TICKS`（10 秒完整悬浮窗口，不再与抬升时间共享预算；新增 `VectorStructureService.hoverDeadline` 单测）。超时行为（旧实现考古：`git show 6751ce92:KineticThrow.java` 的 `holdAt` 到期 `releaseHeld` 只关句柄、结构回落到重力）选择为：仍瞄准本结构时沿视角自动投掷（同一 `launchHeld` 通道），否则安全结束并记录日志，结构与惯性保留——不走静默掉落，符合任务允许的两种语义之一。
- 内容扩展（`NativeCaptureWhitelist`）：
  - 新增 `SAND`、`RED_SAND`（`GRAVEL` 原已在集合）与 11 种树叶：oak/spruce/birch/jungle/acacia/dark_oak/mangrove/cherry/pale_oak/azalea/flowering_azalea。
  - `FallingBlock` 独立门删除，白名单即边界：`classifyAdmission` 移除 gravity 参数；沙/红沙/砂砾作为已列重力方块直接准入，未列重力方块（如 gametest 用的铁砧）以 `not_in_plain_block_whitelist` 拒绝。BE、流体、waterlogged、scheduled tick、非全块、不可破坏门保持。
  - 原生碰撞几何补齐树叶：gametest 首轮暴露 `NativeStructurePhysicsGeometry` 拒绝 `oak_leaves`（`target_geometry_budget` 回滚）；`STATE_SHAPES` 增加 `LeavesBlock`/`TintedParticleLeavesBlock`/`UntintedParticleLeavesBlock`/`MangroveLeavesBlock`（四类精确类，花瓣粒子子类全覆盖；树叶碰撞形状只依赖方块状态，不含 BE/tick/实体条件）。
- 验证结果：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：普通单测 2592 项（基线 2589，本轮 +3：白名单重力语义、投掷速度标定、悬浮窗口）+ Sable 16 项（基线 11，+5：新增 `SableContainerGuardTest`）零失败/错误/跳过（`build/fixp31-tests-final.log`）。
  - 事务 gametest 新隔离目录 `run/native-structure-transaction-gametest-fixp31-r2`：`All 1 required tests passed`（`build/fixp31-gametest-r2.log`）。新增沙/红沙/砂砾/橡树叶/杜鹃树叶提交用例（目标内容与源一致、源移除、journal 回收、释放槽位）与铁砧 `not_in_plain_block_whitelist` 用例。
  - 世界烟测新隔离目录 `run/native-structure-physics-smoke-server-fixp31-r1` PASSED、`exit_status=0`（904 tick，`build/fixp31-world-smoke-r1.log`）；结果文件与 `fixwl-r1` 逐行 diff 仅 `first_ref`/`second_ref`/`shared_session` 随机 UUID 与 `ticks=903→904`，`kinetic_lift_rise=3.2498730230960433`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=45.0`、`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_pull_speed=45.57584523660533` 全部逐值无回归。
  - Sable 专服烟测新隔离目录 `run/sable-compat/fixp31-server-r1`（`-PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true`，目录按 P27 约定预置）PASSED、`exit_status=0`（`build/fixp31-sable-server-r1.log`），证据含新断言 `sable_container_tick_guard_active=true` 与既有 `sable_capture_committed=true`、`sable_capture_rejected_be_preserved=true`、`pneumatic_real_server_entry_selects_sable=true` 等全部保持。
  - `build -DisDev=true` 25 秒、`-DisDev=false` 39 秒通过（`build/fixp31-build-dev.log`、`build/fixp31-build-release.log`），`verifySableCompatPackaging`（桥接类存在、无嵌套外部 Sable 依赖）通过。
- 失败轨迹：gametest `fixp31-r1` 在新增树叶用例失败（`Structure capture target geometry was rejected ... Unsupported collision state ... oak_leaves`，`build/fixp31-gametest-r1.log`），按上述几何补齐后 `fixp31-r2` 通过；中途一次 `compileJava` 失败（`SableStructureTraction.launch` 误用不存在的本地 `requireThread`）与一次 mixin 文件格式手误均在提交任何运行前修复；无其他失败运行目录。
- 未验边界：真实客户端需用户复测（步骤见下）；Sable 热图分裂本身未在烟测中直接复现（mixin 只以 `sable_container_tick_guard_active` 证明真实容器 tick 被守卫，快照迭代的正确性由字节码判定与单测覆盖）；Sable 端 Kinetic Throw 的投掷速度/悬浮未跑专用夹具（现有 Sable 烟测不执行动能投掷，Sable 发射路径仅由编译、守卫单测与本体烟测无回归保护）；Sable 质量预算（`sable_traction_too_heavy`）与 `sable_target_not_visible` 的具体现场未再复现（诊断已细化为打印 `StructureHitValidation.Status`，便于用户复测定位）；悬浮 10 秒自动投掷未在自动化中触发；`docs/vfx/...` 未触碰。
- 用户复测步骤（真实客户端，Sable 后端）：
  1. 进入 Sable 存档，学习动能投掷并按住 H 蓄力，瞄准石质地表松开，观察结构抬升后**至少 10 秒**悬停且位置保持。
  2. 悬停期间正对结构左击：结构应沿视线以约 50 格/秒抛出；左击同时不应丢失原版攻击反馈。
  3. 不瞄准结构左击/空手左击：应有正常原版攻击，不出现异常。
  4. 悬停 10 秒内不操作：结构应沿当时视角自动抛出；若未瞄准则记录 `hover expired without a launchable aim` 并释放控制（结构保留，不凭空消失）。
  5. 捕获含沙/砂砾/树叶与新增白名单方块的区域：应提交成功；含 BE/流体/水logged/不可破坏/未列方块时应有明确 `capture_rejected` 且源方块不变。
  6. 目标为过重结构（`sable_traction_too_heavy`）或不可见（`sable_target_not_visible`）时，日志应给出精确状态，便于回传。
- 复现命令（每次全新隔离目录）：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`
  - `.\gradlew.bat runGameTestServer -I validation/native_structure/transaction.init.gradle -PacademyGameTests=academy:structure_transactions -PacademyStructureTransactionDirectory=run/native-structure-transaction-gametest-fixp31-r2 -DisDev=true`
  - `.\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsMagnetManipulationSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-fixp31-r1 @physicsNativeArgs -DisDev=true`
  - `.\gradlew.bat runServerSable -PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true -PacademySableRunDirectory=run/sable-compat/fixp31-server-r1 -DisDev=true`
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `5798E3C77B99E8472BA0F37891838A173D4B826FECEC0D8E6996931D1A5B817F`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `89207934F8DC9DD198A940E9A7D625CF92C2315057787F048C10302454C73A29`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 修复：原地向上生成与 H 键投掷任意结构（2026-10-14）

- 问题 1 根因与生成策略：
  - 用户反馈“对准地面捕获得出的物理结构陷入地下、生成位置错位”。现状链审计：`VectorStructureService.beginLift`（第 703 行起）在捕获提交后直接以原方块位姿 `handle.pose()` 作为初始物理位姿 `openRegion/attach`；物理体在 `NativeStructurePhysics.Region.replaceWorld` 创建，而区域转 READY 的那一 tick 会先执行一整步**无控制**的 `world.step(0.05, 3)`（`Region.tick` → `stepWithControls` 无 control 分支），抬升锚点要到 `tickAwaitingPhysics` 才安装。旧实现（P25 前，`git show a58fd123:src/main/java/org/academy/internal/common/world/entity/structure/BlockStructureEntity.java` 的 `collisionGeometry.collideWithWorld` 扫掠 + `BlockStructureKinetics.holdAt` 速度 PD）只截断移动、不做穿透推挤，结构即使嵌在地面炮眼里也会沿请求方向被扫掠向上拉起；新 Rapier 冲量求解器从“原方块完全填充炮眼”的零间隙姿态起步且首步不受控，接触/穿透修正可挤动、下沉或错位。
  - 生成策略（计划书允许的“初始生成”例外，已在代码注释注明，不是 `detach → setPose → attach` 普通移动）：捕获提交后、物理 attach 前，把结构位姿整体上移 `INITIAL_GROUND_CLEARANCE = 0.25`（`VectorStructureService.java:90`、第 719–733 行），区域盒用带 clearance 的 `required` 计算（重载 `regionBounds`，`VectorStructureService.java:1311`），打开区域/挂接失败的路径调用 `restoreInitialPose` 回滚原位姿。水平位置不变；抬升目标仍是“当前质心 + liftHeight”，即仍在原位向上抬。一步自由落体上限约 1.23 cm（0.05 s），远小于 25 cm 清空，首个不受控物理步不可能把结构底压到原选区底以下。
  - 负向实验与诚实边界：把 clearance 临时置 0 复跑同一嵌入地面夹具（`run/native-structure-physics-smoke-server-fixp32-r7`、`r8`，日志 `build/fixp32-world-smoke-r7-noclearance.log`、`r8-noclearance.log` 保留），首个物理 tick 的 `kinetic_first_physics_min_y` 仍为 112.048/112.05（≥ 原选区底 112.0），**未能在合成专服夹具复现用户所述的下沉量**；但同夹具出现了可测差异（抬升高度 3.480 vs 3.483、飞行轨迹偏低导致僵尸接触未命中）。因此本修复以构造性保证（首个不受控步不可能低于原选区底）回应真实客户端风险，最终由用户复测确认。
- 问题 2 H 键投掷任意结构：
  - 客户端：删除左键 `KEY_NAME_THROW` 绑定与 `throwLookedAtStructure`；H 按下改发新包 `kinetic_throw_press`（`PressPacket`），H 松开仍发 `ReleasePacket`；旧配置里的 `_throw` 行不再被读取（无副作用，配置兼容）。
  - 服务端统一决策：`KineticThrow.Server.pressHeld`（`KineticThrow.java:243`）→ `VectorStructureService.press`（`VectorStructureService.java:426`）。① 自己 LIFTING 会话且瞄住/身处其中 → 走上一批的 `launchHeld`（含 10 秒悬浮与超时沿视角自动投掷语义）；② 否则跨后端 `StructureQueries.raycast/validate`，命中→`pressRoute(true, mayInteract, isPhysical)`；未命中→`CHARGING`，由 `pressHeld` 调原 `startCharge`（长按蓄力→松开捕获原样）；③ 命中且允许→`directThrow`（`VectorStructureService.java:465`）：原生 `StructurePhysics.activeRegion` + `region.setVelocity(ref, dir×speed, ZERO)` 直接置速（质量预算、有限力、牵引租约质量门全部不参与；他人活跃租约仍拒绝 `structure_controlled`，不抢控），Sable 走 `StructureBackendRouter.launch` 直接置速入口；随后建立 FLYING 会话（原生继续区域跟随与接触结算，Sable 只观察惯性），不要求结构属于玩家；④ 拒绝码 `permission_denied`/`structure_not_physical`/`motion_unavailable`/`structure_controlled`/`sable_launch_rejected`/`ray_*`，日志 + actionbar 新文案 `message.academy.kinetic_throw.throw_rejected`，不静默、不回退蓄力。`Server.throwHeld` 保持程序化/烟测入口不变。
  - 速度标定：新增纯函数 `directThrowSpeed(mass, milestone)` 显式忽略质量并返回 `KineticThrow.throwSpeed`（50 m/s；三级熟练 60），保证重结构投掷速度与标定一致。
  - 改动文件：`VectorStructureService.java`、`KineticThrow.java`、`PacketTypes.java`、中英 `lang`（技能描述改为“按住 H 蓄力；瞄准任意物理化结构按 H 投掷”、新增 `throw_rejected`）、`VectorStructureServiceTest.java`、`NativeStructurePhysicsKineticThrowSmokeChecks.java`、`NativeStructurePhysicsSmokeCleanup.java`、`SableStructureSmoke.java`；`docs/vfx/...` 未触碰。
- 验证（最终修订）：
  - 单测 2596 项（基线 2592，+4：`pressRoute` 命中/未命中/无权限/非物理、`directThrowSpeed` 质量无关、`regionBounds` clearance 位移）+ Sable 16 项零失败/错误/跳过（`build/fixp32-tests-final.log`）。
  - 世界烟测新隔离目录 `run/native-structure-physics-smoke-server-fixp32-r9` PASSED、`exit_status=0`（908 tick，`build/fixp32-world-smoke-r9.log`）。夹具改为“3×3 源方块嵌在 5×5×6 真实地面中”——捕获透镜 51 格（`kinetic_block_count=51`，在原生物理 64 盒/刚体上限内）、质量 51。新证据：`kinetic_hpressed_miss_charging=true`（未命中→蓄力→原捕获流程）、`kinetic_capture_horizontal_aligned=true`（首个物理 tick 位姿原点 x/z 与捕获原点差 ≤1e-3）、`kinetic_first_physics_min_y=112.26426572712418` ≥ `kinetic_selection_min_y=112.0`、`kinetic_lift_min_y=112.36533004493464` ≥ 112.0、`kinetic_hpressed_structure_throw=true`、`kinetic_throw_mass=51.0`、`kinetic_throw_speed_mass_independent=true`、`kinetic_throw_speed=50.0`；未受夹具影响的既有证据逐值无回归：`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_pull_speed=45.57584523660533`、`support_*`/`grasp_*`/`terrain_*` 与 fixp31 全部一致；夹具缩放导致的既有证据取值变化仅 `kinetic_lift_rise` 3.2499→3.4831、`kinetic_impact_damage` 45.0→255.0（透镜 9→51 格）。结果文件与 fixp31 的逐行 diff 仅剩随机 UUID、`ticks=904→908` 与本修复的新证据与上述两项夹具缩放。
  - Sable 专服烟测新隔离目录 `run/sable-compat/fixp32-server-r1`（`-PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true`）PASSED、`exit_status=0`（`build/fixp32-sable-server-r1.log`）。捕获夹具改为 6×6×4=144 格石头（真实质量 288 > tier-0 预算 128），先断言常规 `StructureTraction.tryAcquire` 返回 `TOO_HEAVY`，再按 H：`sable_hpressed_heavy_throw=true`、`sable_heavy_mass=288.0`、`sable_traction_too_heavy_bypassed=true`、`sable_h_throw_speed=49.999999319218475`；既有 `sable_capture_committed`、`sable_capture_rejected_be_preserved`、`sable_container_tick_guard_active`、`pneumatic_*`、`npc_*`、`resource_callback_*` 全部保留（仅 `ticks=43→44`）。
  - `build -DisDev=true` 37 秒、`-DisDev=false` 55 秒通过（`build/fixp32-build-dev.log`、`build/fixp32-build-release.log`）。
- 失败轨迹（目录与日志保留）：`r2` 9×9×3 地面捕获透镜超出原生物理“每刚体最多 64 碰撞盒”上限，捕获以 `TARGET_WRITE_FAILED:target_geometry_budget` 干净回滚；`r3` 缩小夹具后区域与主烟测区域间隔不足（`region_open_failed: Physics region conflicts with live or saved reservations`），夹具外移到 `base+(20,4,0)`；`r4` 重结构命中后仍在 10 tick 冷却外合法二次结算，探针旧断言误报；`r5` 探针状态机把“持续低于初始血量”误判为重复结算，改为只对累计伤害值的新增做冷却断言；`r6`（5×5×3 浅夹具）首次全量通过；`r7`/`r8` 为把 clearance 临时置 0 的负向实验（见上，失败保留）。全部失败目录与 `build/fixp32-world-smoke-r*.log` 保留，r9 为最终修订全量复跑。
- 未验边界：真实客户端最终由用户复测（合成专服未能复现用户所述的下沉量，仅给出构造性保证）；真实双端/键盘输入、`kinetic_hpressed` 在 Sable 客户端侧的 HUD 表现、投掷“任意归属”结构时他人正在牵引的原生结构（活跃租约会拒绝为 `structure_controlled`）与 Sable 被他人牵引时的 `sable_launch_rejected` 只由代码路径与既有约束保护，未跑专项夹具；投掷后仍不静态化/不变回方块（同上一批）；`Server.startCharge`/`StartPacket` 保留但新客户端不再发送。
- 用户复测步骤：
  1. 未装 Sable：瞄准地面按住 H 蓄力、松开捕获；结构应在原位水平对齐、向上生成并抬升，底部不低于原选区底，10 秒悬停不抖动。
  2. 悬浮期间（或瞄准任意其他物理化结构）按 H：结构应沿视线以约 50 格/秒投出；重结构速度不变；松开 H 不再重复触发。
  3. 瞄准空处/普通地形按 H：应照旧进入蓄力，松开后捕获；左键不再投掷，且有正常原版攻击。
  4. Sable 存档重复 1–3：对准重结构（>128 质量）按 H 应直接投出而不是 `sable_traction_too_heavy`；被拒绝时应有 `message.academy.kinetic_throw.throw_rejected` 与带拒绝码的日志。
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `91E714EF38A4996C5513BB57A798E58D418F6C1E4F8A9A9C86DCABD358A84FF3`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `AEB9493FE061B73B15C08DF29EE03B05A494A9F139914BAA008DB4908EDD6B8D`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### P31 电击使结构操作节点族（2026-10-14）

> 历史批次（P31，2026-10-14）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 审计发现（改动前行号）：
  - `ServerElectromasterProgramRuntime.magneticMove` 的方块分支是旧物理路径：`validate` 第 162–179 行把方块转成受控清单键，`apply` 第 200–207 行以 `FallingBlockEntity.fall`（旧第 204 行）+ `setNoGravity(true)` 创建临时实体，第 226 行沿 `origin→destination` 调用 `damageAlongTrajectory` 预结算轨迹伤害，第 228 行直接 `setVelocity`；旧第 249–255 行在撤销时 `discard` 实体并补回源方块（先丢弃后补的路径）。`damageAlongTrajectory`（旧第 438–470 行）同时服务实体与方块发射。
  - `MagneticStructureService`（P28）只有技能入口 `begin` + `Session.tick`（捕获/区域/租约/跟随），没有程序入口；`MagneticStructureControl` 只有 `massBudget`/`pullForce`，没有磁性参与质量概念，也没有程序发射标定。
  - 旧覆盖效果语义：`force_magnetize` 只在 `magneticMove` 的一次动作内生效（旧第 174–178、198–199 行，`FORCED_MAGNETIZATION_COST` 与移动费合并），无到期、无独立撤销，且与方块临时实体绑定。
- 改动/新增文件：
  - `MagneticStructureControl.java`：新增 `magneticParticipation(magneticCells, totalCells)`（0..1 夹取）与 8 参 `pullForce(...)` 重载（7 参保持逐值等价），程序牵引力按参与质量缩放；`MAGNETIZATION_TICKS=200`、`LAUNCH_SPEED_PER_TICK=1.4` 等标定常量落在服务。
  - `MagneticStructureService.java`：新增 `ProgramStatus`/`ProgramResult` 与程序入口 `programExtract`（磁性格子 BFS 邻域选取 `magneticSelection`、显式 `forceMagnetize` 准入、跨 tick 捕获、PENDING 不发布引用、失败保留源方块、覆盖到期记录）、`programControl`（复用同结构会话或接管未被租约占用的活跃区域并签发独占会话，Sable 返回 `sable_program_control`）、`programMove`（磁性参与质量 + 可选局部作用点 + 有界时长；越界/无参与/失联安全结束保留结构）、`programLaunch`（已取得控制权后的有界冲量；不预结算轨迹伤害；发射后撤销本次磁场与磁化覆盖）、`programRelease`（幂等、令牌作用域、不触继任）、`programSession`/`releasePrograms`/`clearProgramsAll`/`tickPrograms`；`Session` 增加 `report`（程序路径不发送技能 actionbar）、`participation`、`forcePoint`、`controllerId()` 与区域接管构造器；`admit` 增加 `allowNonMagnetic` 重载（技能路径调用保持逐值等价）。
  - 节点基础设施：`ElectromasterProgramNodeIds`/`Catalog`/`Capabilities`/`Definition`/`ExecutionBridge`/`Runtime`/`ServerElectromasterProgramRuntime` 增加 `program/electromaster/action/structure/{extract,control,move,rotate,launch,release,staticize}` 七个节点；extract 配置 `budget`(0–256)/`force_magnetize`，move 配置 `duration_seconds`(0.1–10)，launch 复用 `power`(0–2)；`ElectromasterProgramRuntime` 增加 7 个结构方法与嵌套 `StructureOperation`；bridge 装配执行器与可选 `target/anchor/seconds/pose` 端口；`STRUCTURE = academy:magnet_manipulation` 能力。
  - 旧路径迁移：`magneticMove` 方块分支整段删除，`target_type=block` 在 `validate` 显式抛出 `magnetic_block_target_moved_to_structure_nodes`（不保留双路径）；`FallingBlockEntity` 引用仅保留在磁力实体分支（pull/launch 语义与撤销逐行不变，代码注释注明实体不属于结构迁移面）；`damageAlongTrajectory` 现仅服务磁力实体发射。`releaseControlled` 追加 `MagneticStructureService.releasePrograms`，新增 `ServerStoppedEvent` 清理，`onServerTick` 驱动 `tickPrograms`。
  - 翻译：en_us/zh_cn 各新增 7 个节点名与说明、端口键 `port.force_magnetize`（`port.host/control/target/anchor/seconds/pose` 等 P30 键复用），`magnetic_move` 说明改为“方块目标请改用结构节点族”；未新增 value types（P30 的 4 个类型复用）。
  - 测试：新增 `ElectromasterProgramStructureNodesTest`（5 项）；`ElectromasterProgramExecutionBridgeTest.FakeRuntime` 补齐新接口桩；`MagneticStructureControlTest` +2（参与质量、程序力缩放）。
- 两张图验收（真实性级别：目录、schema、编译器、程序 VM、`ElectromasterProgramExecutionBridge`、节点执行器与动作计数为生产实现；electromaster runtime 为脚本化接口实现，不落地真实方块捕获/物理，真实世界程序图执行列为未验边界）：
  - 图一“创建→获取控制→移动→旋转(unavailable)→终止”：`extract(host,64,force_magnetize=true)` → `control(structure)` → `move(control,target=12.5,70,10.5,1.0s)` → `rotate` 返回 `unavailable` 且同一令牌随后仍可终止 → `release` 两次（第一次 `released`、第二次 `noop`，结构保留）。VM `COMPLETED`，调用顺序与参数逐字断言；move 记录 `participation=0.75`（生产 `magneticParticipation(3,4)`）。
  - 图二“发射/静态化失败”：`extract` → `control` → `launch(direction=+Y,power=2.0)` 记录标定速度 42.0（`LegacyMotionUnits.velocityPerSecond(LAUNCH_SPEED_PER_TICK)×interpolate(power)`）且无任何轨迹伤害结算 → `staticize` 返回 `unavailable` 且唯一内容所有者保留。
  - 另加“非磁性提取被拒绝”图：`force_magnetize=false` 时提取返回 `rejected:not_magnetic`、不发布 structure、源方块保留；PENDING 图：extract 返回 PENDING 时不发布 `structure` 输出，程序仍完成。
- 回归验证：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：单测 2603 项（基线 2596，本轮 +7）+ Sable 20 项零失败/错误/跳过（`build/p31-tests-final.log`，`build/test-results/test` 汇总 failures=0 errors=0 skipped=0）。
  - 因改动 `MagneticStructureService` 物理路径，专服烟测新隔离目录 `run/native-structure-physics-smoke-server-p31-r1` PASSED、`exit_status=0`（`build/p31-world-smoke-r1.log`）；结果文件与 `fixp32-r9` 逐行 diff 仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID 与 `ticks=908→906`，`kinetic_lift_rise=3.4831345682826043`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=255.0`、`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_pull_speed=45.57584523660533`、`support_*`/`grasp_*`/`terrain_*` 全部逐值无回归。
  - Sable 专服烟测新隔离目录 `run/sable-compat/p31-server-r1`（`-PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true`，目录按既有约定预置）PASSED、`exit_status=0`（`build/p31-sable-server-r1.log`），`sable_capture_committed=true`、`sable_capture_rejected_be_preserved=true`、`sable_hpressed_heavy_throw=true`、`sable_h_throw_speed=49.999999319218475`、`sable_container_tick_guard_active=true`、`pneumatic_*`、`npc_*`、`resource_callback_*` 全部保持。
  - `build -DisDev=true` 23 秒、`-DisDev=false` 37 秒通过（`build/p31-build-dev.log`、`build/p31-build-release.log`）。
- 失败轨迹：首轮编译通过；首轮定向单测 1 失败——`launchAndStaticizeGraphReportsBoundedLaunchAndNoPresetDamage` 的脚本化 runtime 对 `force_magnetize=false` 一律返回拒绝，extract 未发布 `structure`，staticize 因此 `MISSING_INPUT_VALUE → FAILED`（`build/p31-tests-r1.log`）；修复为脚本显式 `rejectExtraction` 开关、发射图改用显式覆盖后通过（`build/p31-tests-r2.log`）。实现中有一次编辑误插重复 `isEditableBlock`、一次测试误删节点行，均在编译/运行前自查修复，未进入任何构建或运行产物。世界/Sable 烟测无需保留失败目录。
- 未实现前置（仍 unavailable）：`action/structure/rotate` 缺有限力矩姿态控制，`action/structure/staticize` 缺安全静态化；两者均返回明确 `unavailable`，不无条件赋姿态、不用 `detach → setPose → attach` 顶替、不先 discard 再补方块，失败保留内容与唯一所有者。
- 未验边界：真实世界程序图（原生 extract 跨 tick 捕获，单次程序执行无法在提交前取得引用，故“创建→移动”同图只做脚本运行时验证；真实世界仅验证服务与技能回归）；非玩家施术者入口；真实双端与真实键盘输入；Sable 的程序提取/控制/移动/发射（`programControl` 显式 `sable_program_control`，程序提取仍经路由捕获，未跑 Sable 程序图专用夹具）；磁性结构发射后的真实接触伤害未实现（本批只保证不预结算，接触结算仍由物理求解器/既有区域表现承担）；强制磁化覆盖到期仅在 `tickPrograms` 判定（到期且参与质量为 0 时安全终止，否则降为原生磁性参与比例继续）；`programControl` 只接管已有活跃物理区域的结构，不会为无区域结构新开区域；每玩家同时只有一个程序会话，程序移动/空闲窗口上限 600 tick，物理区域长期回收同 P25–P30；节点暂无可产生 `structure_anchor`/`structure_pose` 的公共输入节点（端口为后续扩展预留）。
- 当前构建（含 P19–P31）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `2ECA357DDF2F77C818C66A91DBBA0528EBC689FC09AEA40F8669070E8409C87A`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `0A271C035779828F9BF472DFCC73A8A1B90A8DCEA37B5A12D26ACE9AA2BC9CD4`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 修复：程序动作失败呈现与缺值端口诊断（2026-10-14）

- 机制（用户真实客户端日志已定位）：客户端 `latest.log` 5336–5342 报“节点未产生所需数据…节点 发射结构 [#4]”，服务器真实原因是 `获取结构控制` 的 Sable 程序控制被拒（`Magnetic pull sable control is not implemented; retaining StructureRef[... backend=academy:sable]` + `Magnetic pull rejected …: sable_pull_unavailable`）。两个桥的 `structureResult(...)`（`ElectromasterProgramExecutionBridge.java` / `AcceleratorProgramExecutionBridge.java`）对 `REJECTED/UNAVAILABLE` 结果仍调用 `next("flow")` 且只在非空时输出 `structure`/`control`，控制被拒后流程静默继续；下游发射结构解析 `control` 输入失败，`ProgramVm` 在消费节点抛 `MISSING_INPUT_VALUE`，失败归属与原因都被替换成“缺数据”，根因被掩盖。
- 失败语义审计：`ProgramNodeStep` 没有失败指令；`ProgramActionTransaction` 的顺序结算失败已通过 `SettlementException` → VM 通用异常通道按执行节点归属；VM 缺值只带枚举、不带端口。因此新增专用异常通道 `ProgramActionFailure`（`org.academy.internal.common.ability.program`，携带 `ProgramVmDiagnostic` 与机器可读 `detail`），由 VM 在动作节点转换为带原因失败，不新增静默路径。
- 改动文件：
  - `ProgramActionFailure.java`（新增）：动作已执行但未成功时抛出，携带诊断与 `detail`。
  - `ProgramVm.java`：`ExecutionFailure` 增加 `port`/`detail`；缺值抛出点补端口名（`resolveInputs` 用消费节点的输入名覆盖上游输出端口名，`resolveValue` 用源输出端口）；`ProgramActionFailure` 转为节点失败；`FAILED` 结果携带两者。
  - `ProgramVmResult.java`：新增 `@Nullable String port` 与 `@Nullable String detail`（保留三参构造，旧调用不破）。
  - `ProgramVmDiagnostic.java`：追加 `ACTION_UNAVAILABLE`（序列化按 ordinal，追加在末尾）。
  - 两个桥 `structureResult`：`REJECTED` → `ACTION_REJECTED`、`UNAVAILABLE` → `ACTION_UNAVAILABLE`，`detail = 小写status + ":" + result.detail()`（如 `unavailable:sable_program_control`、`rejected:not_magnetic`）并在该动作节点失败；`COMPLETED` 但 structure/control 输出缺失 → 该节点失败并给出 `completed:structure_missing`/`completed:control_missing`；`PENDING` 保持现状（不输出 structure/control、流程继续，交由图的 result 分支处理，见未验边界）。
  - `ProgramSessionScheduler.Termination`、`ServerProgramExecution.finishDeferred`、`AbilityProgramManager`（`ExecutionOutcome`、`failureOutcome`、`ResultPacket` 新增 `detail` 字段、自动/手动/延迟失败路径）逐层转发 port/detail。
  - 客户端：`ProgramDiagnosticText.locateRuntimeFailure`（新格式）+ `AbilityProgramEditorClient`/`ModularProgramScreen.kt` 保存并显示 VM 失败端口与详情；旧 `ProgramDiagnosticText.locate` 与旧消息键保留不动。
  - lang（en_us/zh_cn，按自然序插入）：`message.academy.program.execution.diagnostic.action_unavailable`、`screen.academy.program.diagnostic.missing_input_port`、`screen.academy.program.diagnostic.detail`。
  - 失败呈现对照（旧 → 新）：
    - 控制被拒（Sable）：旧 = 发射结构 [#4] `节点未产生所需数据；请检查…`；新 = 获取结构控制 [#3] `该操作在当前环境下不可用；节点 获取结构控制 [#3]；详情：unavailable:sable_program_control`，发射结构不再执行。
    - 提取被拒：旧 = 同名图继续、下游缺值；新 = 提取节点 [#2] `ACTION_REJECTED` + `rejected:not_magnetic`，源方块保留断言不变。
    - 旋转/静态化 unavailable：旧 = 图继续到终止/下一节点；新 = 该节点失败 + `unavailable:finite_torque_control_unavailable` / `unavailable:safe_staticization_unavailable`，控制不被破坏、内容所有者保留断言不变。
    - 真缺值：旧 = `节点未产生所需数据…节点 发射结构 [#4]`；新 = 追加 `缺少输入 结构控制令牌` 端口信息（中英键均新增）。
- 验证：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：普通单测 2609 项（基线 2603，本轮 +6：ProgramVm 缺值端口/动作详情 +2，电击使旋转失败/控制被拒 +2，矢量旋转失败/控制被拒 +2）+ Sable 16 项零失败/错误/跳过（`build/fixprog-tests.log`；`build/test-results/test` 汇总 599 文件 failures=0 errors=0 skipped=0）。
  - `build -DisDev=true` 通过（`build/fixprog-build-dev.log`，24 秒）、`build -DisDev=false` 通过（`build/fixprog-build-release.log`，34 秒），含 Sable 打包/依赖门禁。
  - 未改动 `MagneticStructureService`/`VectorStructureService` 的 Sable 拒绝行为与任何物理路径，因此世界烟测未复跑（条件不触发）；测试期望仅从“静默完成”改为“正确节点失败 + 正确原因”，未放宽断言；PENDING 用例仍断言程序 `COMPLETED`。
- 失败轨迹（日志均保留）：首轮 `compileJava` 失败两次——`ProgramDiagnosticText` 对嵌套类型的 `@Nullable` 位置不合法（改为 `ProgramEditorNodeCatalog.@Nullable Entry`）、`AbilityProgramManager` 缺 `(…, port)` 旧重载（补回）；首轮定向单测 2 失败——旋转失败图仍断言 `applied` 缺 `move` 行（`build/fixprog-tests-focused.log`），补齐后 `build/fixprog-tests-focused2.log` 全绿。无失败运行目录产生。
- 未验边界：真实客户端与真实键盘输入需用户复测（本轮只有用户日志定位；`runClientDev` 未启动）；Sable 磁力/矢量 `programControl/programLaunch` 的实现（含绕过质量门）由后续任务完成，本棒只改呈现；`PENDING` 结构操作仍按原语义继续流程，引用该输出的下游节点会在自身节点报告缺值端口（图的 result 分支应自行处理 PENDING）；precision/mentalout 等非结构动作的 detail 转发为空（沿用既有诊断）；`docs/vfx/...` 未触碰。
- 复现命令：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`
  - `.\gradlew.bat build -DisDev=true`
  - `.\gradlew.bat build -DisDev=false`
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `9EB1550644CEF53714FF84109856D399E86D390A8F671C12270FFA5C93FA2BFB`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `A9F3372640C726FDBCACD80FB175B79C23F99CB4FE7EB6600FD6261B9BCBA3F4`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 实现：Sable 结构程序控制与发射（矢量/电击使）（2026-10-14）

- 审计结论（改动前行号，改动前工作区 `git status` 仅有 `docs/vfx/...` 未跟踪项，未触碰）：
  - `VectorStructureService.programControl` 第 257 行对 Sable 显式 `unavailable("sable_program_control")`；`programLaunch` 第 348 行对 `held.traction != null` 显式 `unavailable("sable_launch")`；`beginLift` 第 706–708 行对 Sable 一律调用技能路径 `beginSableLift`（需要瞄准、`StructureTraction` 会话与质量预算），程序提取得到的会话因此带活动牵引，控制节点既无法签发令牌、发射也被显式拒绝。
  - `MagneticStructureService.programControl` 第 393 行对 Sable 显式 `unavailable("sable_program_control")`；`programLaunch` 第 468–504 行只有原生区域冲量分支；`Session.tick` 第 820–829 行在捕获提交后走 `sable_pull_unavailable` 并 `close()` —— 即用户真实日志中的 `Magnetic pull rejected …: sable_pull_unavailable` 与图表“获取结构控制”失败链。
  - `SableStructureTraction.launch`（fixp31，第 66–93 行）已按 `addLinearAndAngularVelocity(目标速度−当前速度, −角速度)` 精确赋速、不查质量、有活动牵引会话时拒绝，可复用为直接置速入口；`StructureBackendRouter.launch`（第 186–197 行）是中性路由。
- 改动文件：
  - 新增 `src/main/java/org/academy/api/common/structure/StructureDirectControl.java`：轻量直接控制租约的公共入口与进程内注册表（`acquire/validate/release/clearAll`、`Probe`、`Lease`、`Status`，以及可单测的纯判定 `decideAcquire/decideValidate`）。
  - 新增 `src/main/java/org/academy/internal/common/structure/sable/SableStructureDirectControl.java`：Sable 后端探测（已加载船、rapier 管线未暂停、单位缩放、刚体有效、无活动 Academy 牵引会话、Suite 手杖 FREE）。
  - `StructureBackendRouter.java`：`Bridge.probeDirectControl` 默认拒绝 + 路由静态方法（仅 SABLE 且有桥时转发；非 Sable 返回 `direct_control_unsupported`）。
  - `SableStructureBridge.java`：实现探针；`shutdown` 清空租约注册表。
  - `SableStructureTraction.java`：抽出包内 `controllable(SableStructureHandle)`，供会话门禁与直接控制共用（原 `available` 语义逐值不变）。
  - `MagneticStructureService.java`、`VectorStructureService.java`：程序控制/发射的 Sable 分支（见下）。
  - `SableStructureSmoke.java`：新增 PROGRAM 相位（两能力的程序控制/发射/幂等释放证据）。
  - `gradle/sable-compat.gradle`：打包必检清单加入 `SableStructureDirectControl.class`。
  - 测试：新增 `StructureDirectControlTest`（3 项）；`MagneticStructureServiceTest` +1；`AcceleratorProgramStructureNodesTest` +1；`ElectromasterProgramStructureNodesTest` +1。
- 租约与置速设计：
  - **公共层**：租约以 `StructureRef` 为键，绑定 actor UUID、服务 controlId 与探测返回的结构 runtimeId；不同 actor 的既有租约返回 `OCCUPIED`（不抢占），同一 actor 可用新 controlId 取代自身；绑定 epoch 变化时旧令牌 `UNAVAILABLE` 且不重获；`release(ref, controlId)` 幂等且只撤销本令牌；租约不落盘、不跨会话恢复。服务侧继续持有各自的 `ProgramControl`/`Session`（`controllerId` 即租约 controlId），玩家退出/换维/死亡/停服经既有清理调用 `releaseProgram`。
  - **电击使**：新增 `Stage.SABLE_RETAINED`；程序捕获提交后不再走 `sable_pull_unavailable`，而是保留等待控制节点；`programControl` 对 Sable 只签发租约（不建区域、不建实体、不做质量门），他人控制/无结构分别返回 `structure_controlled`/`structure_unavailable`（epoch 不符为 `stale_structure_epoch`）；`programLaunch` 校验租约后按原生标定直接置速（`StructureBackendRouter.launch` → `SableStructureTraction.launch`），**绕过质量预算与 `sable_traction_too_heavy`**，发射前 `releaseProgram` 撤销本次磁场与磁化覆盖、不预结算轨迹伤害；`programMove` 对 Sable 恒 `unavailable:sable_move`；`tickPrograms` 对直接租约会话只做租约复验（磁化覆盖到期不再强制终止），空闲窗口沿用 600 tick。
  - **矢量**：新增 `Session.directControl` 与 `controlSableFresh/controlSableExisting`（轻量租约，窗口沿用 `HOVER_TICKS`=200）；`programLaunch` 的 Sable 分支 `launchSableDirect` 用技能侧标定直接置速、无质量门，成功后进入 FLYING 观察（`tickSableFlight`）；`programMove` 对 Sable 恒 `unavailable:sable_move`；程序提取（`programExtract`）在 Sable 世界不再调用 `beginSableLift`（避免瞄准/牵引/质量门），改为 `beginSableProgramExtract` 保留会话，控制节点再签发租约；`tickLifting` 对直接租约与保留会话分别复验，失效即安全结束并保留结构；技能 Sable 路径（牵引升举/技能投掷）逐值不变。
  - 原生路径行为逐值不变：两服务的原生 `programControl/programLaunch/programMove` 仅插入 `isSable`/`sable` 分支；`beginLift`/`startCapture` 新增 `program` 标志，技能调用恒为 `false`。
- 标定依据：
  - 电击使直接置速与原生程序发射共用纯函数 `MagneticStructureService.launchSpeed(scale) = LegacyMotionUnits.velocityPerSecond(1.4) × scale`（0.5/1.0/1.5 → 14/28/42 m/s），实测标准强度 28.0。
  - 矢量直接置速与技能侧共用 `KineticThrow.throwSpeed(milestone3) × speedScale`（50 m/s，三级熟练 60），实测 50.0；两者均与结构质量无关（烟测夹具质量 288 > tier-0 预算 128）。
- 验证（最终修订）：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：普通单测 2615 项（基线 2609，本轮 +6）+ Sable 16 项零失败/错误/跳过（`build/sablefp-tests-final.log`；`build/test-results` 汇总 failures=0 errors=0 skipped=0）。
  - 单元级图验收（真实目录/编译器/VM/`*ProgramExecutionBridge`，runtime 为脚本化实现）：accelerator 与 electromaster 各新增“extract→control→launch→release×2”图，断言全链 COMPLETED、重结构绕过质量门（脚本在非 Sable 路径返回 `sable_traction_too_heavy` 对照）、置速等于标定值、无轨迹伤害结算、release 幂等（`released`→`noop`）、控制令牌未破坏。
  - Sable 专服烟测新隔离目录 `run/sable-compat/sablefp-server-r4`（`-PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true`）PASSED、`exit_status=0`（`build/sablefp-server-r4.log`）：新增 `sable_program_control=true`、`sable_program_release_idempotent=true`、`sable_program_heavy_mass=288.0`、`sable_program_launch_speed=50.0`、`sable_magnetic_program_control=true`、`sable_magnetic_program_launch_speed=28.0`；既有 `sable_capture_committed`、`sable_capture_rejected_be_preserved`、`sable_container_tick_guard_active`、`sable_hpressed_heavy_throw=true`、`sable_h_throw_speed=49.999999319218475`、`pneumatic_*`、`npc_*`、`resource_callback_*`、`fixture_cleanup_complete` 全部保留（ticks=45）。
  - 世界烟测新隔离目录 `run/native-structure-physics-smoke-server-sablefp-r2` PASSED、`exit_status=0`（`build/sablefp-world-smoke-r2.log`）；结果文件与 `fixp32-r9` 逐行 diff 仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID 与 `ticks=908→906`，`kinetic_*`、`hsjet_*`、`mag_*`、`support_*`、`grasp_*`、`terrain_*` 全部逐值无回归。
  - `build -DisDev=true` 21 秒、`-DisDev=false` 34 秒通过（`build/sablefp-build-dev-final.log`、`build/sablefp-build-release-final.log`），含 `verifySableCompatPackaging`（新增桥类存在、无嵌套外部 Sable 依赖）。
- 失败轨迹（目录与日志保留）：
  - `sablefp-server-r1`：新 PROGRAM 证据全部通过，但既有 NPC 相位在 age=1 失败 `NPC traction ended unexpectedly: NOT_VISIBLE`——H 键重投后 288 质量船体沿 -z 飞向 NPC 夹具，遮挡了 `StructureTraction` 的验证射线（`run/sable-compat/sablefp-server-r1`、`build/sablefp-server-r1.log` 保留）。
  - `sablefp-server-r2`：跨相位续租后仍在 PROGRAM 起点失败（船体仍在上一条飞行路径上；结果与日志保留）。
  - 修复：H 投掷改从 -z 侧瞄准、把船体投离夹具（+z 方向），并在 HEAVY_THROW 与 PROGRAM 相位续租 NPC 会话；`sablefp-server-r3` 与最终 `r4` 通过。
- 未验边界：真实客户端/键盘输入需用户复测；程序图在真实 Sable 世界的“提取→控制→发射”单次执行仍受跨 tick 捕获提交限制（extract 为 PENDING 时不发布引用，与原生 P30 同一边界；烟测对已提交捕获直接执行控制/发射）；`StructureDirectControl` 为进程内注册表、不持久（跨服务互斥由该表保证，玩家退出/换维/死亡/停服走各自服务清理）；非玩家施术者入口未验；Suite 手杖锁定/被他人牵引时的 `OCCUPIED` 拒绝未在 Suite 运行时跑专项夹具（纯 Sable 运行时手杖缺失为 FREE）；无活动物理管线/非单位缩放/结构卸载后的租约失效率由纯判定单测与 probe 代码路径保证，未逐一构造现场；矢量与电击使的 rotate/staticize 仍按设计 `unavailable`（有限力矩与安全静态化未实现前置不变）；`docs/vfx/...` 未触碰。
- 复现命令（每次使用全新隔离目录，替换 `NEW`，不要复用成功/失败产物）：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`
  - `.\gradlew.bat runServerSable -DisDev=true -PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true -PacademySableRunDirectory=run/sable-compat/NEW`（需按既有约定预置 `server/eula.txt` 与 `server/server.properties`）
  - `.\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsMagnetManipulationSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-NEW @physicsNativeArgs -DisDev=true`
- 当前构建（含本实现）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `0A2D8FA08E492CA954C8E05CBD431B509F524D391FB5978AA18992E3993138FA`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `CB8CAB6D618C12D2A7AF1FEEFA727BEFC20F1A87E5CCE4283BE4345B4D5845ED`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 修复：Sable 捕获空船窗口与孤儿结构（2026-10-14）

- 根因（证据：`build/repro-suite-r1-jbr.log`，用户存档副本 `run/sable-compat/repro-crash-suite-r1`）：
  - `SableStructureTransactions.allocate`（旧第 59–90 行）先建零质量空船，首格写入要等协调器复制阶段才发生；空窗期内 Sable 的 `SubLevelContainer.processSubLevelRemovals` 对零质量子级 `destroyAllBlocks` + `markRemoved`，随后全容器 `saveAll` 在 `SubLevelSerializer.serialize` 里经 `RigidBodyHandle.getLinearVelocity` → `assertBodyValid` 抛 `java.lang.RuntimeException: Body has been removed`（日志 5 次，栈经 `save:168 → stepSaveTarget:448 → … → MagnetManipulation.beginStructureTarget`）。
  - `save()` 旧实现只 `log + false`，协调器保留事务继续重试；Sable 自身移除 sweep 在 fastutil 列表迭代中删除元素会跳过已标记移除的子级，窗口内任何检查点都会再次抛错；技能侧又是在**捕获提交后**才 `sable_pull_unavailable` 拒绝（`Session.tick`），每次尝试留一条船——用户存档“仅由磁力/电击使产生”的结构即由此累积（r1 日志 10 次 `retaining StructureRef`，5 次检查点失败）。
- 接口与生命周期改动：
  - **分配+首格同调用链**：`CaptureTarget.allocate(host, origin, seedOffset, seedState)` 扩展（旧签名移除）；协调器把捕获顺序首格的 local offset/state 随分配传入，`readCell` 复核 seeded 后令 `copyProgress = seedIndex + 1`、复制阶段跳过首格，回滚核验仍覆盖首格（`copyProgress` 取 `max` 防止跳过导致的覆盖回退）。Sable 在 `allocateNewSubLevel` + 重建 rotation point + 预建 chunk 后**同一调用链**经现有 `writeCell` 写入首格，失败即 `removeQuietly` 整船并返回空；原生目标忽略 seed，仍走复制阶段（行为逐值不变）。
  - **回滚即移除整船**：`stepClearTarget` 先调 `releaseEmpty`；Sable 直接 `removeSubLevel(ship, REMOVED)` 并跳过逐格清空（旧路径逐格清空最后一格会触发零质量销毁并使句柄失效），原生在 `discardEmpty` 拒绝时仍走“逐格核验清空 + unload + 容量回收”，语义不变；deactivate/quarantined 路径未改。
  - **检查点 prune + 单次重试**：`SableStructureTransactions.save` 失败时按新增纯策略 `CaptureCheckpointPolicy.invalidBodies`（保护本事务目标）扫描容器，镜像 Sable sweep 处理 `isRemoved()` 或零质量子级（`destroyAllBlocks` + `markRemoved` + `removeSubLevel`）后重试一次；仍失败才报错保留事务。`saveAll` 与指针簿记是 Sable 唯一现成契约（`SubLevelHoldingChunkMap.moveAndSaveSubLevel`/`SubLevelStorage` 指针非稳定公开 API，逐船保存不可安全复刻），故保留 `saveAll` + prune。
  - **兜底移除 API**：新增 `StructureTransactions.discard(server, reference)`（协调器 `discard` → 后端 `releaseEmpty`），供技能侧“建后拒绝”兜底路径移除刚建的船。
  - **技能侧前置拒绝**：`MagneticStructureService.begin` 在 Sable 模式直接 `reject`（日志 + `structure_pull_rejected` actionbar）并在任何分配前返回；`Session.tick` 的 `sable_pull_unavailable` 分支降级为异常兜底并调用 `discard`。`VectorStructureService` 技能路径在 `beginCapture` 用新增 `CaptureTarget.estimateMass`（Sable 按 `PhysicsBlockPropertyHelper.getMass` 求和，与捕获质量同源）在捕获前做质量门，超预算直接 `sable_traction_too_heavy` 拒绝；`beginSableLift`/`beginSableProgramExtract` 各后置拒绝分支补 `discardSableCapture` 兜底。
- 复现回归对照（同一用户存档副本、同 suite/JBR 启动；r1 为先备好的失败日志，r2 为新目录、`pauseOnLostFocus:false`、日志 `build/repro-suite-r2-jbr.log`）：
  - 修复前：`Sable capture checkpoint failed`=5、`Body has been removed`=5、`retaining StructureRef`=10、`Magnetic pull rejected`=10（每次拒绝前先有一条 `Magnetic pull sable control is not implemented; retaining …`）。
  - 修复后：`Sable capture checkpoint failed`=0、`Body has been removed`=0、`retaining StructureRef`=0、`sable control is not implemented`=0、`Magnetic pull rejected`=18（全部来自前置拒绝，捕获 0 次）；overworld `sublevels` 目录文件集与总字节数与 r1 副本逐一相同（无新增船；`build/repro-suite-r2-summary.txt`）。
  - 驱动方式（诚实边界）：临时 PowerShell 以 `PostMessage` 向游戏窗口发送 R 按下/抬起 12 轮（每轮 1.6 秒）触发方块路径；`WM_CHAR` 聊天命令未进入游戏（无 `issued server command`，铁块未放置），但 18 次拒绝证明 `begin` 被反复调用；驱动日志 `%TEMP%\opencode\ac-repro-r2-driver.log`。首次 r2 尝试因 `latest.log` 尾部 400 行判定加入失败（日志增长过快）记 `DRIVER=NO_JOIN`，该次运行与日志保留为 `build/repro-suite-r2-jbr-partial-a.log`、`run/sable-compat/repro-crash-suite-r2/client/logs-partial-a`。
- 验证：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：普通单测 2617 项（基线 2615，+2 `CaptureCheckpointPolicyTest`）+ Sable 16 项零失败/错误/跳过（`build/fixcap-tests-r1.log`）。
  - Sable 专服烟测新隔离目录 `run/sable-compat/fixcap-server-r1`（`-PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true`）PASSED、`exit_status=0`（`build/fixcap-sable-server-r1.log`）；新证据 `sable_capture_seed_same_chain=true`（直接 `allocate` 后立即断言船 `mass>0` 且 COM 非空再释放）、`sable_capture_rollback_removed=true`（提交前撤销权限 → `PERMISSION_DENIED:permission_revoked_before_commit` 回滚，容器子级数不变、源方块保留、journal 回收）、`sable_checkpoint_no_body_removed=true`（人为 `markRemoved` 子级使 `saveAll` 抛错 → prune + 重试 → 提交且无效子级移除）；结果文件与 `sablefp-server-r4` 逐行 diff 仅 +3 行新证据与 `ticks=45→46`，其余全部逐值一致。
  - 事务 gametest 新隔离目录 `run/native-structure-transaction-gametest-fixcap-r1`：`All 1 required tests passed`（`build/fixcap-gametest-r1.log`）。
  - 世界烟测新隔离目录 `run/native-structure-physics-smoke-server-fixcap-r1` PASSED、`exit_status=0`（`build/fixcap-world-smoke-r1.log`）；结果文件与 `sablefp-r2` 逐行 diff 仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID（`ticks=906` 不变），`kinetic_*`、`hsjet_*`、`mag_*`、`support_*`、`grasp_*`、`terrain_*` 全部逐值无回归。
  - `build -DisDev=true`（`build/fixcap-build-dev.log`）、`build -DisDev=false`（`build/fixcap-build-release.log`）通过，含 `verifySableCompatPackaging`。
- 失败轨迹（日志均保留）：首轮 Sable 专服烟测在 shell 默认 Zulu 25 上因 `-XX:+AllowEnhancedClassRedefinition`（JBR 专用）无法启动（`build/fixcap-sable-server-r1.log`，随后被同名通过轮覆盖）；以临时 init 脚本（`%TEMP%\opencode\ac-server-jbr.init.gradle`，不提交）为 run 任务显式指定 JBR 25 后通过。世界烟测首轮因 `buildNativePhysics` 需要 cargo（本机无 Rust）失败；改用既有 `build/native-physics/cargo` 产物并 `-x buildNativePhysics` 通过（属既有本地构建条件，不改仓库）。客户端 r2 首次加入判定失败见上（保留 partial 日志）。
- 未验边界：真实客户端真人输入与 Sable 客户端表现未验（本轮只以窗口消息自动发 R；聊天文字与程序编辑器 UI 无法自动化，程序提取/控制/发射由 Sable 专服烟测的真实程序入口 `sable_program_*`/`sable_magnetic_program_*` 覆盖，客户端未执行程序图）；存量孤儿存档自愈未验——prune 只在新捕获检查点失败时执行，纯客户端不再产生捕获时 Sable 自身 sweep 仅回收零质量船，已提交的有质量孤儿船需后续独立清理策略；`saveAll` 内部 I/O 吞错与断电语义沿用计划书既有边界，未新增观察；`docs/vfx/...` 未触碰。
- 复现命令（每次使用全新隔离目录，替换 `NEW`）：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`
  - `.\gradlew.bat runServerSable -DisDev=true -PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true -PacademySableRunDirectory=run/sable-compat/NEW`（预置 `server/eula.txt` 与 `server/server.properties`；run 任务需 JBR 25）
  - `.\gradlew.bat runGameTestServer -I validation/native_structure/transaction.init.gradle -PacademyGameTests=academy:structure_transactions -PacademyStructureTransactionDirectory=run/native-structure-transaction-gametest-NEW -DisDev=true`
  - 客户端复现：`gradlew.bat runClientSable -PacademySableRuntime=suite -PacademySableRunDirectory=run/sable-compat/NEW -I <quickPlay 注入 init 脚本>`（存档副本、`pauseOnLostFocus:false`；r1/r2 运行记录保留）
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `47BE0EC0583873566086AAE8FCC8915D8105A1B5B34F4767DEE5EB818F53954E`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `FAD243574E74F200910B745910BA73BFF1F918003917B5BF35C874BCF795D0C4`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 修复：Sable 世界磁力操控技能拖动铁块（2026-10-15）

- 根因：上一批为"不再留下孤儿结构"把技能路径改为 **Sable 模式创建前直接拒绝**（`MagneticStructureService.begin` 的 `sableMode` 前置判定），导致 Sable 世界磁力操控永远拿不到可拖动的结构（铁块根本不会物理化）。
- 修复：移除创建前拒绝；技能路径照常经 `StructureTransactions.capture` 捕获（首格同链种子、失败不留船），提交后在 `startSableSkillPull` 通过 `StructureDirectControl.acquire` 取得轻量直控租约（不持久、失效不重获、幂等释放、不触继任），逐 tick 用 `MagneticMovement.calculatePullVelocity`（旧 2.3 格/tick 牵引曲线，经 `LegacyMotionUnits` 换算为格/秒）驱动新增的 `SableStructureTraction.setLinearVelocity`（精确置速、清旋转、不受质量门），越界/失联安全结束并保留结构；`Session.close` 释放直接控制租约，`controlActive()` 计入直控状态；获租约失败时回滚移除刚创建目标（`StructureTransactions.discard`）。
- 验证：单测 2617 + Sable 16 零失败/错误/跳过（`build/skillfix-tests.log`）；世界烟测 `run/native-structure-physics-smoke-server-skillfix-r1` PASSED、`exit_status=0`，与 `fixcap-r1` 逐行 diff 仅 3 个随机 UUID（`build/skillfix-world-smoke-r1.log`）；Sable 专服烟测 `run/sable-compat/skillfix-r1` PASSED、`exit_status=0`，`sable_capture_committed`、`sable_program_control`、`sable_program_launch_speed=50.0`、`sable_magnetic_program_launch_speed=28.0`、`sable_capture_seed_same_chain`、`sable_capture_rollback_removed`、`sable_checkpoint_no_body_removed` 等全部保持（`build/skillfix-sable-server-r1.log`）；`build -DisDev=true`（`build/skillfix-build-dev.log`）、`build -DisDev=false`（`build/skillfix-build-release.log`）通过。
- 未验边界：技能拖动无专用烟测证据（Sable 烟测覆盖程序入口与捕获生命周期），真实客户端拖动表现待用户复测；Sable 技能拖动只做线性置速，不含姿态/旋转与接触伤害结算；每次拖动占用一个直控租约（与程序路径同一机制），继任冲突明确拒绝。
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `976E6949CE93E68B0C9D12BC3F723329066B66E35ED7FE8FF23514E237A8CDA4`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `B5E70991F63DC2F310B1DADC30501D24CD9325342807CF78F55AF02FDB2E5F7C`。未复制到整合包，未操作用户存档。

### P32 空力使结构操作节点族（2026-10-15）

> 历史批次（P32，2026-10-15）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 审计发现（改动前行号）：
  - `ServerAeromanipProgramRuntime.launchBlockStructure` 旧第 255–295 行是唯一结构入口：直接构造 `HighSpeedJetStructureService.LaunchRequest` 并作为动作事务走技能服务 `launch`，没有 extract/control/move/release 粒度，也与技能共用同一会话窗口。
  - `HighSpeedJetStructureService`（改动前 1077 行）只有技能/旧节点入口：`launch`(126)、`plan`(183)、`cropToBudget`(227)、`regionBounds`(257)、`tick`(329)、`tickCapture`(375)、`beginSession`(413)、`tickAwait`(499)、`tickThrust`(542)（单一 `launchDirection` + 质心单点力）、`stopSession`(778)、`Session`(1010)、`closeControl`(1067)；无 program 状态、无多锚点力组、无 Sable 直控分支。
  - `JetStructureControl`（改动前 95 行）只有 `thrustForce`(47) 与 `limitedThrustForce`(67) 单矢量接口，没有共享预算在多锚点间拆分、对称抵消与 `Σ(r × F)` 口径。
  - `AeromanipProgramNodeCatalog` 旧第 61–64 行注册 legacy `action/launch_block_structure`，配置 `BlockStructureLaunchConfiguration` 为旧第 297–314 行；`AeromanipProgramDefinition` 旧第 54–59 行只有 legacy 默认配置；`AeromanipProgramCapabilities` 无 `STRUCTURE`；`SableStructureSmoke` PROGRAM 相位旧第 608–674 行只有矢量/电击使程序证据。
- 改动/新增文件：
  - `JetStructureControl.java`：新增 `thrustForceMagnitude`、`combinedThrustForce`（共享预算按活动锚点数均分，方向可抵消、不随锚点复制）、`limitedCombinedThrustForce`（整矢量速度上限的单步换算）、`ThrustAnchor`/`ThrustGroup` 与 `thrustGroup`（合成总力与 `Σ(r × F)` 力矩；预算只作用于合成力，力矩为派生量、不二次计费）；`thrustForce`/`limitedThrustForce` 逐值等价地改走同一实现（原单测 192/240/上限断言未改）。
  - `HighSpeedJetStructureService.java`：新增 `ProgramStatus`/`ProgramResult` 与程序入口 `programExtract`（喷口宿主捕获；半径 2–8、预算 0–256，0 取后端默认；喷口支撑格必须解析到实际捕获格；PENDING 不发布引用、失败保留源方块）、`programControl`（同结构程序会话签发控制；原生无会话明确 `no_matching_session`、不抢继任、技能会话明确 `skill_session_active`；Sable 走 `StructureDirectControl` 轻量租约）、`programMove`（局部锚点有限力、方向用实时完整姿态逐子步变换、可选世界目标；锚点必须命中捕获的喷口支撑格；Sable 明确 `sable_move`）、`programLaunch`（原生有界推力至标定速度上限；Sable `SableStructureTraction.launch` 一次性有界置速 `JetStructureControl.maximumSpeed(power)`；不预结算轨迹伤害）、`programRelease`（幂等、令牌作用域、保留线角速度、绝不触碰继任）、`programSession`；新增 `Session` 程序字段（`program/controlled/engaged/programMove/mounts/directControl/sableBody/duration/窗口/power`）与 `programNative`/`programSable` 构造、`Stage.SABLE_DIRECT`、`thrustCommand` 统一单步力（技能单矢量 / 程序多锚点力组）、`programAnchors`/`localAnchorCentroid`/`localThrustDirection`、`engageNozzle`；新增公共判定 `resolvesSupportCell` 与纯回退判定 `SupportFallback.supportFallback`（结构+格→STRUCTURE、结构失去格→NONE 不猜、结构卸载→宿主方块可支撑才 BLOCK）；技能路径逐值不变（世界烟测 `hsjet_*` 无回归）。
  - `AeromanipProgramNodeIds.java` / `AeromanipProgramNodeCatalog.java`：新增 `program/aeromanip/action/structure/{extract,control,move,rotate,launch,release,staticize}` 七节点；extract 配置 `radius`(2–8)/`budget`(0–256)，move 配置 `duration_seconds`(0.1–10)，launch 配置 `power`(0–2)/`duration_seconds`(0.1–60)/`restore_when_settled`（仅回读，沿用旧语义）；rotate/staticize 可见但明确 `unavailable`；新增旧 ID 兼容 API `unifiedIdForLegacy`、`legacyExtractConfiguration`、`legacyLaunchConfiguration`（legacy `radius→extract.radius`，`power/duration/restore_when_settled→launch`），legacy 节点注册、schemaVersion=1 与执行器全部保留（未改 2）。
  - `AeromanipProgramRuntime.java` / `ServerAeromanipProgramRuntime.java`：新增 7 个结构方法与嵌套 `StructureOperation`；extract/control/move/launch 按 P30/P31 口径逐次计费（30/10/5/15 CP × 倍率，仅 COMPLETED 时扣），控制令牌绑定 player/结构 runtimeId/服务 `controllerId`/本次 runtime `runtimeToken`（不落盘，跨运行时失效即 `control_token_stale`/`control_runtime_expired`）；rotate 校验令牌后 `unavailable:finite_torque_control_unavailable`（不破坏控制），staticize `unavailable:safe_staticization_unavailable`（保留唯一内容所有者）。
  - `AeromanipProgramExecutionBridge.java`：装配 7 个执行器与可选 `target/anchor/seconds/pose` 端口，失败语义沿用 `ProgramActionFailure`（`unavailable:...`/`rejected:...` 归属该动作节点）。
  - `AeromanipProgramCapabilities.java`：新增 `STRUCTURE = academy:high_speed_jet`；`AeromanipProgramDefinition.java`：七节点默认配置与编辑器条目。
  - 翻译：en_us/zh_cn 新增 7 个节点名与说明、`configuration.field.budget`、`configuration.field.duration_seconds`；legacy `launch_block_structure` 说明改为统一族旧式入口（旧图端口与配置保留）。
  - 测试：新增 `AeromanipProgramStructureNodesTest`（6 项）；`JetStructureControlTest` +3（共享预算/取消、单步上限、力矩口径）；`HighSpeedJetStructureServiceTest` +3（局部推力方向、回退矩阵、程序窗口常量）；`AeromanipProgramExecutionBridgeTest.FakeRuntime` 补齐接口桩。
- 两张图验收（真实性级别：目录、schema、编译器、程序 VM、`AeromanipProgramExecutionBridge`、节点执行器与 `JetStructureControl` 标定常量为生产实现；aeromanip runtime 为脚本化接口实现，不落地真实方块捕获/物理；真实世界仅验证服务入口，见烟测）：
  - 图一“创建→获取控制→移动→终止”：`extract(host,4,64)` → `control(structure)` → `move(control,target=12.5,70,10.5,1.0s,2 锚点)`（记录共享预算 `combinedThrustForce(ctx两平行锚点)=192.0` 与对称锚点 `0.0`，均取生产函数）→ `release` 两次（`released`→`noop`，结构保留）。VM `COMPLETED`，调用顺序与参数逐字断言。
  - 旋转未实现前置：同图链 `extract→control→move→rotate` 在 rotate 节点失败（`ACTION_UNAVAILABLE` + `unavailable:finite_torque_control_unavailable`），断言控制令牌未被破坏、结构保留；终止由上一图与 release 幂等覆盖（与 P30/P31 同一拆分）。
  - 图二“发射/静态化失败”：`extract(host,2,64)` → `control` → `launch(direction=+Y,power=2.0,8.0s)` 记录有限力 `limitedThrustForce(…)`（零速下 = 768.0，生产换算）且无任何轨迹伤害结算 → `staticize` 返回 `unavailable` 且唯一内容所有者保留。
  - 另加 PENDING 图：extract 返回 PENDING 时不发布 `structure` 输出，程序仍完成。
  - 旧 ID 兼容：legacy 节点仍存在于目录/执行器、schemaVersion=1、端口与 `power/structure_radius/duration/restore_when_settled` 回读逐值断言；`unifiedIdForLegacy` 与两个迁移辅助的映射逐值断言；`AeromanipProgramExecutionBridgeTest.blockStructureLaunchStagesConfiguredTransactionalAction` 旧图用例原样通过。
- 服务/桥单测断言：`supportFallback` 四态（结构+格、结构缺格不猜、卸载回退宿主、双失联 NONE）；`localThrustDirection`（NORTH→+z、UP→−y）；共享预算（单锚 192、双平行 192 不复制、双反向 0、正交 96√2）；单步上限（过顶回拉 −200）；`thrustGroup`（对称力与力矩同时抵消、单锚满预算、力矩 = −192 ŷ 且不二次计费）；程序窗口常量（600/600/1200/256）。
- 回归验证（最终修订 r2；r1 为加入“技能会话明确拒绝”前的同配置通过轮，改动后全量复跑 r2）：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：普通单测 2629 项（基线 2617，本轮 +12）+ Sable 16 项零失败/错误/跳过（`build/p32-tests-final.log`；`build/test-results/test` 汇总 failures=0 errors=0 skipped=0）。
  - 世界烟测新隔离目录 `run/native-structure-physics-smoke-server-p32-r2` PASSED、`exit_status=0`（926 tick，`build/p32-world-smoke-r2.log`）；结果文件与 `fixcap-r1` 逐行 diff 仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID、`ticks=906→926` 与本批新增证据：`hsjet_program_extract_pending=true`、`hsjet_program_session_committed=true`、`hsjet_program_support_resolved=true`、`hsjet_program_control=true`、`hsjet_program_move_engaged=true`、`hsjet_program_move_shared_direction=true`、`hsjet_program_launch_speed=115.40448730010463`（门槛 95% 首次采样）、`hsjet_program_release_idempotent=true`、`hsjet_program_control_cleared=true`、`hsjet_program_retained=true`、`hsjet_program_source_not_restored=true`；既有 `kinetic_*`、`hsjet_*`、`mag_*`、`support_*`、`grasp_*`、`terrain_*` 全部逐值无回归（`build/p32-world-smoke-r2-result.txt`）。
  - Sable 专服烟测新隔离目录 `run/sable-compat/p32-server-r2`（`-PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true`）PASSED、`exit_status=0`（`build/p32-sable-server-r2.log`）；结果文件与 `skillfix-r1` 逐行 diff 仅本批新增 4 行：`sable_aeromanip_program_control=true`、`sable_aeromanip_program_move_unavailable=true`、`sable_aeromanip_program_launch_speed=120.0`（= `JetStructureControl.maximumSpeed(1.0)`，直控置速无质量门）、`sable_aeromanip_program_release_idempotent=true`；既有 `sable_capture_*`、`sable_program_*`、`sable_magnetic_program_*`、`sable_container_tick_guard_active`、`pneumatic_*`、`npc_*`、`resource_callback_*` 全部保持（`build/p32-sable-server-r2-result.txt`）。
  - `build -DisDev=true` 23 秒、`-DisDev=false` 34 秒通过（`build/p32-build-dev-final.log`、`build/p32-build-release-final.log`），含 `verifySableCompatPackaging` 与 Sable 构建门禁。
- 失败轨迹：首轮 `compileJava` 失败（技能 `launch()` 的 `CaptureState` 构造调用漏传新增 `program` 参数）、首轮 `compileTestJava` 失败（legacy schema 断言对 `find(...)` 返回的捕获类型直接传 `JsonObject`，改用编辑器目录 `schema(id, json)`），均在提交运行前修复、未进入任何测试或世界运行；两处失败日志未单独 Tee 留存。世界/Sable 烟测 r1 一次通过；r2 为加入“程序控制不接管技能会话”明确拒绝后的最终修订全量复跑，无失败运行目录需要保留。
- 未实现前置（仍 unavailable）：`action/structure/rotate` 缺有限力矩姿态控制（角速度反馈、最短四元数误差、有界力矩与角冲量），`action/structure/staticize` 缺安全静态化（完整 footprint/加载/权限/占用/可旋转方块状态）；两者均明确拒绝、不无条件赋姿态、不用 `detach→setPose→attach` 顶替、不先 discard 再补方块，失败保留内容与唯一所有者。
- 未验边界：程序真实世界图（原生 extract 跨 tick 捕获，单次程序执行无法在提交前取得引用，故图验收为脚本运行时；世界烟测直接驱服务入口）；非玩家施术者入口；真实双端与真实键盘输入；旋转/静态化的“成功”路径不存在；对称喷口的**物理**力矩等价性只做到图/服务层与纯函数断言——原生一次只携带一个 `Force`，多锚点组按合成力施加在锚点质心，非对称喷口组与精确 `Σ(r × F)` 的物理力矩差异未逐一对照（如实标注）；喷口在世界内被摧毁/结构卸载结束会话的现场未专门构造（`supportFallback` 纯判定与 `resolvesSupportCell` 烟测解析已断言，会话失联走 `owner_or_structure_unavailable` 安全结束）；Sable 程序 move 明确 unavailable、Sable 直接发射是一次性置速而非逐子步力；技能会话不会被程序接管（`skill_session_active`）；非法旧 ID 图仍按原技能路径执行（迁移器为显式辅助，P33 的自动图迁移与版本器仍待做）；每玩家单会话、程序空闲窗口 600 tick、launch 最长 1200 tick，物理区域长期回收同 P25–P31；`docs/vfx/...` 未触碰。
- 复现命令（每次使用全新隔离目录，替换 `NEW`，不要复用成功/失败产物）：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`
  - `.\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsMagnetManipulationSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-NEW @physicsNativeArgs -DisDev=true`（预置 `eula.txt`、`server-ip=127.0.0.1`、`level-name=native-structure-physics-smoke` 的 `server.properties`，世界目录尚不存在）
  - `.\gradlew.bat runServerSable -DisDev=true -PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true -PacademySableRunDirectory=run/sable-compat/NEW`（预置 `server/eula.txt` 与 `server/server.properties`；run 任务需 JBR 25）
  - `.\gradlew.bat build -DisDev=true` / `.\gradlew.bat build -DisDev=false`
- 当前构建（含 P19–P32）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `EC04B43E4ED7D8DA1916FA9EBC0202F3CF5AA120DF96D256301C8004CE0CE0D6`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `B0CA55A386F6C096F67CFAD92BFD829B8532C3DC4FD50456A105BABCAA2CC0EC`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### P33 收尾：图迁移器、静态化拒绝路径与全矩阵验收（2026-10-15）

> 历史批次（P33，2026-10-15）：以下记录仅代表该批范围，当前状态见文首“当前接续入口”。

- 改动/新增文件：
  - 迁移器公共层：新增 `src/main/java/org/academy/internal/common/ability/program/migrate/ProgramGraphMigrator.java`（显式重写/审计/schema 升级三段式；重写按占位槽分配新节点 id、重接端口边、迁移编辑器布局并保持幂等；不可迁移节点保留原样并给 per-node 诊断；诊断按（code,nodeId,detail）去重）与 `ProgramGraphMigrations.java`（按能力分类注册迁移器、`migrate`/`migrateBook`、`registeredCategories`）。
  - 三套能力迁移器：`AeromanipProgramGraphMigrations.java`（把 legacy `action/launch_block_structure` 重写为 `action/structure/extract → control → launch`，`radius→extract.radius`、`power/duration/restore_when_settled→launch` 回读，`block→extract.host`、`direction→launch.direction`、`flow→launch.flow`；配置不可解析时保留节点并报 `legacy_launch_block_structure_configuration_unparsed`）、`ElectromasterProgramGraphMigrations.java`（`magnetic_move` 的 `target_type=block` 给 `legacy_magnetic_block_target_use_structure_nodes` 诊断而不重写，因为统一节点族没有与旧 `power`/`mode` 等价的字段，自动重写会静默丢配置；节点保留以维持 P31 的显式运行时拒绝）、`AcceleratorProgramGraphMigrations.java`（该能力结构节点自 P30 起就在统一族内，无旧结构动作，注册为可观测的空迁移器）。
  - 加载/编译接入：`AbilityProgramDefinition.compile` 编译前迁移；`AbilityProgramManager.decodeStoredBook`（服务器存档读入）、`importBook`/`save` 报文（迁移后再校验、存储与广播）；`AbilityProgramEditorClient.decodeBook`（客户端缓存书）；`ProgramEditorDocument` 构造（编辑器打开即迁移）；`ProgramClipboardCodec.decode`（片段与整程序导入均迁移）。`schemaVersion` 策略保持：`AbilityProgram.CURRENT_SCHEMA_VERSION`/`ProgramBook.CURRENT_SCHEMA_VERSION` 仍为 1，迁移保留原信封与程序版本，legacy 节点 `schemaVersion=1` 不变，只有 `!= 当前版本` 才走严格拒绝。
  - 安全静态化：新增 api 只读规划器 `src/main/java/org/academy/api/common/structure/StructureStaticization.java`（`LocalCell`/`World`/`Permission`/`Status`/`Plan`；按调用序逐格验证目标 footprint：姿态对齐、BE 内容旋转、重复目标、世界边界、加载、权限、占用、可放置并可存活，首个失败格给 `problemPosition`；全程不写入）；新增服务器接线 `StructureStaticizationRunner.java`（解析结构句柄、校验生命周期/代次/维度、读取捕获格内容、用 `level.mayInteract` 与真实方块状态执行相同预检；任一失败返回 `rejected:<code>[@pos]`，成功前置仍返回 `unavailable:safe_staticization_unavailable`，绝不虚假成功）；accel/electro/aeromanip 三个 `*ProgramRuntime.staticizeStructure` 改为取本会话捕获格并调用该 runner。
  - 会话捕获格回读：`VectorStructureService.Session` 新增 `capturedCells`（捕获提交时记录）、`HighSpeedJetStructureService.Session` 与 `MagneticStructureService.Session` 新增 `capturedCells()`（捕获路径填充；采纳/Sable 未捕获会话为空）。
  - 翻译：en_us/zh_cn 的 rotate/staticize 节点描述与 legacy `launch_block_structure` 描述同步（静态化说明新的拒绝语义与“成功路径未实现仍 unavailable”；legacy 说明加载/导入时自动迁移）；仅改值，键序不变。
  - 测试：新增 `ProgramGraphMigrationTest`（7 项：legacy 重写与端口/配置逐值、二进制 envelope 解码后迁移、不可解析配置 per-node 诊断且不拒绝整图、accelerator 空迁移可观测、electromaster block 诊断、schema 升级机制、混合图只重写可行节点且每项失败只报一次）、`StructureStaticizationTest`（6 项纯函数：逐格映射与 quarterTurns、重复目标、空/无锚、越界/未加载/权限/占用区分、BE 与姿态旋转拒绝、不可放置区分）；三套 `*ProgramStructureNodesTest` 各加“拒绝静态化在 staticize 节点失败并保留内容所有者与控制”与“矩阵：创建→移动→旋转 unavailable→随后同一会话仍可幂等终止”图用例；`ProgramClipboardCodecTest` 新增 legacy 程序经剪贴板导入后迁移到统一族且端口/配置保留；`AeromanipProgramStructureNodesTest` 新增 legacy 图迁移后经生产编译器与桥执行、行为等价（发射有限力等于生产 `JetStructureControl.limitedThrustForce`）。
- 迁移器策略（逐条对应计划书 585–614 行与设计节 250–255 行）：
  - 旧图不因版本被拒：旧图程序/信封版本本就是 1，迁移器只在版本不等于当前时才报 `unsupported_program_schema` 并保持原图；不存在“把 schemaVersion 改成 2”的路径（测试断言迁移前后均为 1）。
  - 只改结构不丢数据：重写保留原端口连线语义（legacy `flow/block/direction` 逐一映射），保留全部配置字段回读，保留程序 id/名称/分类/编辑器布局（替换节点在旧位置按 90 间距展开）；不迁移的 legacy 节点原样保留并可编译执行（P31/P32 的显式运行时语义不变）。
  - 失败给明确诊断而非拒绝整图：`rewrite_failed`/`legacy_action_not_migrated`/`unmapped_rewrite_port`/`unsupported_node_schema` 均为 per-node 诊断；图本身不被判无效，编译只在节点真的无法解析时按既有诊断报错。
- 静态化拒绝语义：`staticize` 与 `release` 分离，成功路径不存在，永远不先 discard 再补方块；拒绝时结构、其控制令牌、惯性/重力、捕获内容与事务 journal 均未被触碰（runner 只读），`detail` 为机器可读的小写状态码（如 `occupied@10,64,10`、`unloaded@…`、`permission_denied@…`、`rotation_unsupported@…`、`not_placeable@…`、`structure_unavailable`、`stale_structure_epoch`、`structure_dimension_mismatch`、`structure_content_unavailable`）。
- 编辑器同步：结构族 palette/options/模板在 P30–P32 已就位，本批补齐加载与剪贴板迁移、客户端缓存书迁移、编辑器文档迁移与中英描述；rotate/staticize 保持可见与明确说明且未进入 `ProgramStarterTemplates`（模板仍只用入口/输出/文本三类节点）；`SkillProficiencyCoverageTest` 断言中英键集与自然序一致。
- 全矩阵图验收（生产 catalog/schema/编译器/VM/`*ProgramExecutionBridge` + 脚本化 runtime；每类能力两张图 + 终止在失败后仍可执行）：
  - accelerator：图一 `extract(1,0)→control→move(target=12.5,70,10.5,1.0s)→rotate` 在 rotate 失败（`ACTION_UNAVAILABLE`/`unavailable:finite_torque_control_unavailable`，控制与内容保留），随后同 runtime 执行 `extract→control→release×2` 得 `released→noop` 且 `COMPLETED`；图二 `extract(2,64)→control→launch(+Y,MAXIMUM)→staticize` 记录有界冲量 75、无轨迹伤害，staticize 失败；另加 staticize 拒绝图（`rejected:target_occupied`，`ACTION_REJECTED`，内容所有者保留）。
  - electromaster：同上（图一 move 记录 `participation=0.75`；图二 launch 标定速度与 staticize 失败/拒绝）。
  - aeromanip：同上（图一 move 记录共享预算组与扭矩抵消，`anchors=2`；图二 launch 有限力等于生产换算；legacy 迁移图另经桥执行）。
  - PENDING 提取图三套均在：extract 返回 PENDING 不发布 `structure` 输出、程序仍完成。
- 回归验证：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：普通单测 2650 项（`build/test-results/test` 604 个套件汇总，failures=0 errors=0 skipped=0）+ Sable 16 项零失败/错误/跳过（`build/p33-tests.log`；相对 P32 记录的 2629 基线净增 21，来自迁移器、静态化预检、拒绝静态化图、矩阵图与剪贴板迁移用例）。
  - 世界烟测新隔离目录 `run/native-structure-physics-smoke-server-p33-r1` PASSED、`exit_status=0`（926 tick，`build/p33-world-smoke-r1.log`）；结果文件 281 行与 `p32-r2` 逐行 diff **仅** `first_ref`/`second_ref`/`shared_session` 三个随机 UUID（`build/p33-world-smoke-r1-diff.txt`），`kinetic_lift_rise=3.4831404156863925`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=255.0`、`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_expected_speed=46.0`、`mag_pull_speed=45.57584523660533`、`support_*`/`grasp_*`/`terrain_*` 全部逐值无回归（连 `ticks=926` 都相同）。
  - Sable 专服烟测新隔离目录 `run/sable-compat/p33-server-r1`（`-PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true`）PASSED、`exit_status=0`（`build/p33-sable-server-r1.log`）；结果文件与 `p32-server-r2` **75 行完全一致（零 diff）**（`build/p33-sable-server-r1-diff.txt`），`sable_capture_*`、`sable_program_*`、`sable_magnetic_program_*`、`sable_aeromanip_program_*`、`sable_container_tick_guard_active`、`pneumatic_*`、`npc_*`、`resource_callback_*` 全部保持。
  - `build -DisDev=true` 21 秒（`build/p33-build-dev.log`）、`build -DisDev=false` 34 秒（`build/p33-build-release.log`）通过，含 `verifySableCompatPackaging` 与 Sable 构建门禁；`build/p33-compile-1.log`/`p33-compile-2.log` 为编译检查。
- 失败轨迹：本批所有测试、烟测与双构建最终一次通过，无失败运行目录需要保留。唯一失败在编辑阶段：用 PowerShell 就地替换 zh_cn.json 中文串时因控制台编码未命中目标并以 `Set-Content` 重写了整文件（2849 行伪 diff + 换行/BOM 变化），在运行任何构建前即以 `git checkout` 还原并改用 UTF-8 感知编辑器完成三处替换（`build/p33-compile-2.log` 起的全部产物均为还原后的代码）。
- 未验边界：真实客户端与真实键盘输入未启动（编辑器 palette/剪贴板/迁移提示只在单测与客户端代码路径覆盖，未在 `runClientDev` 里人工核对）；`StructureStaticizationRunner` 的服务器接线（`ServerLevel` 上的真实占用/未加载/权限拒绝）没有专用夹具，纯规划器由单测覆盖、接线只由编译与脚本化 runtime 图用例保证；真实存档中的旧图迁移未在世界内跑（fixture 覆盖 JSON 图、二进制 envelope 与剪贴板三种入口）；电击使 `magnetic_move` 的 block 目标有意不自动重写（保数据优先，保留显式诊断与运行时拒绝）；Sable 结构的 staticize 未跑专用夹具（runner 会读取 Sable 句柄内容，成功路径不存在）；采纳会话（无捕获格）返回 `unavailable:structure_content_unavailable` 而不推导内容；非玩家施术者入口、跨维度 staticize 现场（维度守卫已加但无夹具）、物理区域长期回收同 P25–P32；`docs/vfx/...` 未触碰。
- 复现命令（每次使用全新隔离目录，替换 `NEW`）：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`
  - `.\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsMagnetManipulationSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-NEW @physicsNativeArgs -DisDev=true`（预置 `eula.txt`、`server-ip=127.0.0.1`、`level-name=native-structure-physics-smoke` 的 `server.properties`，世界目录尚不存在）
  - `.\gradlew.bat runServerSable -DisDev=true -PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true -PacademySableRunDirectory=run/sable-compat/NEW`（预置 `server/eula.txt` 与 `server/server.properties`；run 任务需 JBR 25）
  - `.\gradlew.bat build -DisDev=true` / `.\gradlew.bat build -DisDev=false`
- 当前构建（含 P19–P33）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `6F90D498E886E13944BC7C26283D185B6BF39D14790AC2D9CBF2B8DD84A6C8BE`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `A633086FE163079D50DB54EBBBB988045D7EEBE4AA5DDBF88A1DEC256449D8C4`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 性能压力测试工具与基线（2026-10-15）

- 工具位置（全部 opt-in；只有显式 `-I validation/structure_stress/<场景>.init.gradle` 才把探针临时加入 `sourceSets.main`，普通构建与发布产物不受影响）：
  - `validation/structure_stress/README.zh-CN.md`：目录说明、前置条件、指标口径与已知边界。
  - `close-startup-dialogs.ps1`：可复用模块，等待游戏窗口并自动关闭启动警告弹窗（Escape/Enter 投递 + 真实系统点击），以 `latest.log` 出现世界继续标志为成功判定，驱动日志记录 `DIALOG_SCAN/DIALOG_ACTION/DIALOG_RESULT`。
  - `run-server-native-stress.ps1` + `server_native_stress.init.gradle`：原生专服基线（隔离 `run/stress-server-native-rN`；需 `. ./build/native-physics-p16-env.ps1`，脚本内部自动加载）。
  - `run-server-sable-stress.ps1` + `server_sable_stress.init.gradle`：Sable 专服基线（隔离 `run/sable-compat/stress-server-sable-rN/server`；run 任务固定 JBR 25）。
  - `run-client-stress.ps1` + `client_stress.init.gradle`：客户端基线（隔离 `run/sable-compat/stress-client-rN`、存档副本、quickPlay、自动弹窗关闭）。
  - `java/org/academy/validation/structure/`：原生 / Sable / 客户端三个探针；启动时校验隔离目录、拒绝已有结果与旧世界，失败写 `FAILED` 结果文件并由 shutdown hook 兜底。
- 场景与规模（每档 60 秒采样；每次运行必须使用全新隔离目录，失败产物一律保留）：
  - 服务端原生：3 个物理区域并发、各 1 个驻留结构，第 4 个区域开放被拒绝（`region_cap_probe=rejected`）；循环执行“捕获→挂接→升力→发射→脱离→清格→槽位回收”，规模 30 与 60 次操作。
  - 服务端 Sable：创建 10 / 20 个真实子级（走生产捕获事务），对子级循环“直控租约获取→置速发射→运动采样→释放”，每 10 次插入一次容器 `saveAll`。
  - 客户端：quickPlay 进入隔离存档副本并自动关闭启动弹窗；在集成服务器上用生产捕获事务创建 1 / 3 个结构，200 tick 预热后采样 60 秒。
- 指标口径：
  - 服务端：`tick_ms_avg/p50/p95/p99/max`（ServerTick 全程）、`mspt_avg`、`tps_avg`、`heap_used_mb_avg/max`（每 20 tick 采样）、事务阶段耗时（原生 `capture_ms`/`recycle_ms`；Sable `capture_ms`/`lease_ms`/`release_ms`/`launch_ms`/`save_ms`，其中 lease/release/launch 是轻量 API 调用本身、不含阶段等待）、区域/槽位/子级计数。
  - 客户端：`load_ms`（JVM 启动→可操作）、`frame_ms_*`（相邻 `RenderFrameEvent.Pre` 间隔，即真实帧节奏）、`render_ms_*`（渲染段事件耗时）、`tick_ms_*`、`fps_avg`、`heap_used_mb_avg/max`。
  - 口径变更：客户端 r1 的 `frame_ms_*`/`fps_avg` 为旧口径（渲染段耗时而非帧间隔），已被 r3 取代；Sable r3 的 `lease_ms`/`launch_ms` 为旧口径（含 4–5 tick 阶段等待），已被 r4 取代。
- 基线数据（通过运行；摘要 `build/stress-*-summary.txt`，原始日志 `build/stress-*.log`）：
  - 原生 r4（30 次操作 / 3 区域 / 60 秒）：`tick_ms_avg=3.2155`、`p95=4.9002`、`p99=31.6592`、`max=75.6327`、`mspt_avg=3.2155`、`tps_avg=20.0000`；`capture_ms_avg=81.6387`、`p99=214.9783`；`recycle_ms_avg=110.3733`、`p99=264.9250`；槽位回收 30/30；`heap_used_mb_avg=452.5710`、`max=610.6183`；`lift_rise_max=1.0383`、`launch_travel_max=3.6033`。
  - 原生 r6（60 次操作 / 3 区域 / 60 秒）：`tick_ms_avg=3.9039`、`p95=5.7421`、`p99=31.3982`、`max=134.9836`、`tps_avg=20.0000`；`capture_ms_avg=83.2880`、`p99=180.7290`；`recycle_ms_avg=101.1634`、`p99=120.7477`；槽位回收 60/60、`cleared_cells=1620`、`capture_retries=0`；`heap_used_mb_avg=421.8564`、`max=577.2995`；`launch_samples=60`。
  - Sable r4（20 子级 / 30 次循环 / 60 秒）：`children_created=20/20`、`save_children_min=max=20`、`operations_completed=30/30`、`leases_acquired=30`、`launches_ok=30`、`motion_samples=30`；`capture_ms_avg=35.4119`、`p99=67.2360`（20 次样本）；`lease_ms_avg=0.1437`、`release_ms_avg=0.0097`、`launch_ms_avg=0.0348`；`save_ms_avg=18.1386`、`max=25.2572`（6 次样本）；`tick_ms_avg=2.0333`、`p95=3.7320`、`max=49.4370`、`tps_avg=20.0000`；`heap_used_mb_avg=420.7663`、`max=584.2428`。
  - Sable r3（10 子级，旧计时口径）：`save_ms_avg=14.4637`、`save_children_min=max=10`、`tick_ms_avg=1.7425`、`heap_used_mb_avg=380.2222`、`max=572.0787`；本轮 `capture_ms_count=0`（探针当时未采集捕获耗时），其余阶段数为 30。
  - 客户端 r3（3 结构 / 60 秒 / 1024x576）：`structures_created=3/3`、`load_ms=28596`、`operable_ms=28596`、`sample_ticks=1200`、`warmup_ticks=200`；`frame_ms_avg=18.4786`、`p95=33.3227`、`p99=34.9818`、`max=47.1133`（≈54 fps）、`render_ms_avg=1.7484`；`tick_ms_avg=1.0832`、`p95=1.3323`、`max=6.8972`；`heap_used_mb_avg=1174.2776`、`max=1584.2418`。
  - 客户端 r4（1 结构 / 60 秒 / 1024x576）：`structures_created=1/1`、`load_ms=28647`、`operable_ms=28647`、`sample_ticks=1200`；`frame_ms_avg=18.4098`、`p95=33.4156`、`p99=34.8980`、`max=45.7273`（≈54 fps）、`render_ms_avg=1.7231`；`tick_ms_avg=1.0198`、`p95=1.2728`、`max=4.9422`；`heap_used_mb_avg=1185.8216`、`max=1658.4684`。单/多结构两档的帧节奏与 tick 耗时在噪声范围内一致，结构数量（1→3）未拉开可辨差异。
  - 客户端 r1（1 结构，旧帧口径）：结构 1/1、`load_ms=306504`（弹窗关闭耗时计入）、`tick_ms_avg=0.2880`、`heap_used_mb_avg=1096.6200`、`max=1480.8653`。
- 启动弹窗自动关闭（用户可复现步骤）：
  - 现象：NeoForge 启动后显示列出未来/废弃变更警告的 `LoadingErrorScreen`，quickPlay 停在警告屏、世界创建/载入不再前进。
  - 手动处理：点击屏幕中下偏左的“继续（Proceed）”按钮，或按 Escape/Enter 即可继续；不修改任何游戏选项。
  - 自动处理：脚本轮询游戏窗口，先在左下候选区发送真实系统点击（`SetForegroundWindow` + `SetCursorPos` + `mouse_event`；GLFW 不响应 `PostMessage` 的鼠标消息），并叠加 Escape/Enter 投递；每轮记 `DIALOG_ACTION`，`latest.log` 出现 `Preparing spawn area|logged in with entity id|joined the game|Saving chunks for level` 之一即记 `DIALOG_RESULT dialog dismissed=true attempts=N evidence='<命中行>'`，超时则 `dismissed=false`。
  - 证据（客户端 r3/r4 各 attempts=8 且 dismissed=true）：`DIALOG_RESULT dialog dismissed=true attempts=8 evidence='... [Server thread/INFO] [net.minecraft.server.MinecraftServer/]: Saving chunks for level 'ServerLevel[新的世界]'/minecraft:overworld'`（`build/stress-client-r3-driver.log`、`build/stress-client-r4-driver.log`；r1 另有 attempts=103 的早期证据）。
  - 单独使用：`. validation/structure_stress/close-startup-dialogs.ps1; Invoke-StartupDialogDismissal -ClientDir <客户端目录> -DriverLog <日志路径> -JoinWaitSeconds 600`；`-NoDialogAutomation` 可做对照。
- 失败轨迹（目录、结果文件与日志全部保留）：
  - Sable r1：探针隔离目录校验把 `<run>/server` 的文件名 `server` 当 run 基名比对，`ServerStartedEvent` 抛 `IllegalStateException` 使专服崩溃（`run/sable-compat/stress-server-sable-r1`、`build/stress-server-sable-r1.log`）；修复为接受 `stress-server-sable-*/server` 后 r2 才真正进入探针。
  - Sable r2：探针创建循环在“同步成功捕获”路径未推进 `createIndex`，产生 24 次连续同点捕获，暴露出生产 `StructureTransactionJournal.writeAtomically` 的 Windows `AccessDeniedException`（`TARGET_WRITE_FAILED/journal_copied_persist_failed`，`build/stress-server-sable-r2.log`）；修复循环推进并加入“仅 journal 类失败、每操作≤3 次”的有界捕获重试（r3/r4 均 `capture_retries=0`）。
  - 原生 r1：`RESIDENT_ATTACH` 抛 `Structure handle is not active: LOADING`、0 次操作（`build/stress-server-native-r1-summary.txt`）。
  - 原生 r2 / r3：分别在 `LIFT` / `DETACH` 抛 `Structure left the bounded physics domain; last published pose retained`（`build/stress-server-native-r2-summary.txt`、`r3-summary.txt`）。
  - 原生 r5（60 次）：第 20 次捕获在 `COMMIT` 阶段 `TARGET_WRITE_FAILED/journal_decision_persist_failed`（同一 `AccessDeniedException`，`build/stress-server-native-r5.log`）；加入有界重试后 r6 复跑 60 次全通过且 `capture_retries=0`。
  - 客户端 r2：弹窗脚本坐标表达式在 PowerShell 中因逗号优先级被解析为“数组减 28”，所有点击失败、600 秒超时 `NO_WORLD`（`build/stress-client-r2-driver.log`）；修正为显式括号后 r3 通过。
- 本批发现（生产代码未改，供后续修复）：`StructureTransactionJournal.writeAtomically` 在 Windows 上用 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` 原子替换同一 `.dat`，在连续捕获压力下偶发 `java.nio.file.AccessDeniedException`（观测约 2/108 次捕获；Sable r2 与原生 r5 的完整栈分别在 `build/stress-server-sable-r2.log`、`build/stress-server-native-r5.log`）。本轮仅在 validation 探针层做有界重试与计数，生产语义未变。
- 未验边界：
  - 服务端“程序图运行”相位未纳入本轮压测：真实 `extract` 在专服走跨 tick PENDING 且不发布结构引用，跑通结构节点族程序需要喷口/技能能量与玩家夹具；结构节点族在程序侧的覆盖仍是 P30–P33 的单测图验收与专服程序入口烟测，本批探针未实现该相位。
  - 客户端结构为“后端持有”（捕获提交后存放于结构空间），未在视野内物理化/渲染；帧数据反映“后端存在结构 + 玩家静置”。
  - 客户端无 vsync/画质对照，未跑低配/核显；GUI 自动化只关闭弹窗，未驱动结构操作（真实键盘结构操作仍未验）。
  - Sable 探针无法分离 Rapier 内部步进耗时（`saveAll` 是唯一可计时的原生库接口）；未跑长稳（>60 秒）与内存泄漏曲线（仅均值/峰值）。
  - 所有数字为本机一次性基线，非产品硬性指标；区域并发上限 3、Sable 子级上限 20 之外的规模未测。
- 复现命令（每次使用全新隔离目录，替换 `rN`；客户端需先复制存档副本，见 README）：
  - `powershell -ExecutionPolicy Bypass -File validation/structure_stress/run-server-native-stress.ps1 -RunSuffix rN -Operations 60 -Regions 3 -Seconds 60`
  - `powershell -ExecutionPolicy Bypass -File validation/structure_stress/run-server-sable-stress.ps1 -RunSuffix rN -Children 20 -Operations 30 -Seconds 60`
  - `powershell -ExecutionPolicy Bypass -File validation/structure_stress/run-client-stress.ps1 -RunSuffix rN -SampleSeconds 60 -Structures 3`
  - 产物：`build/stress-server-native-rN-summary.txt`、`build/stress-server-sable-rN-summary.txt`、`build/stress-client-rN-summary.txt` 与对应 `build/stress-*.log`、客户端驱动日志 `build/stress-client-rN-driver.log`；探针原始结果保存在各自的隔离运行目录。
- 本批未改动生产代码（探针与脚本仅位于 `validation/structure_stress/`，通过 init 脚本临时注入）；未操作用户存档（客户端只使用存档副本），未创建本地 Git 提交。

### 修复：事务日志原子替换偶发 AccessDeniedException（2026-10-15）

- 根因：`StructureTransactionJournal.writeAtomically` 与 `NativeStructureManifest.writeAtomically` 都用“临时文件 + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`”提交 `.dat`；临时文件本身已 `channel.force(true)`，但 Windows 的 `MoveFileEx` 替换目标时若目标被文件过滤器（杀毒/搜索索引）或未共享 delete 的并发句柄短暂持有，就会以 `java.nio.file.AccessDeniedException` 失败，旧实现没有重试：journal 失败即 `TARGET_WRITE_FAILED/journal_*_persist_failed`，manifest 失败即 `Native structure unload refused after save failure`。旧 `finally` 无条件删除临时文件，在兜底已删除目标后还可能丢掉唯一副本。
- 修复（不改变 journal 文件格式与阶段语义）：
  - 新增 `internal/common/structure/DurableFile`：临时文件写入 + `force(true)`，替换失败时有界重试（5 次尝试，10/20/40/80 ms 退避，总 ≤150 ms；仅重试 `FileSystemException`，`AtomicMoveNotSupportedException` 立即走兜底）；重试耗尽后兜底为非原子“先删后移”（`REPLACE_EXISTING` 语义）。重试前本进程读路径均已关闭流（`NbtIo.readCompressed(Path)`/`readAttributes`/`Files.list` 均为即开即关）。
  - 失败从不吞掉：兜底已删除目标（临时文件成唯一副本）时保留临时文件并把路径以 suppressed 形式附在抛出的异常上；否则清理临时文件。崩溃遗留的孤儿 `.tmp` 不会被当作记录读取（journal 只扫描 `.dat`，manifest 只读 `metadata.dat`）。
  - `StructureTransactionJournal` 增加包内可注入 `DurableFile.FileOperations` 构造器（单测 seam），公开构造器、`persist`/`delete` 行为与记录内容均未变；类注释已注明兜底窗口与保留语义。
  - `NativeStructureManifest` 是同一缺陷的第二处站点，且正是它（而非 journal）让修复后首轮压测在 `RECYCLE` 失败，故一并改走共享工具；其余 `ATOMIC_MOVE` 站点（`WorldDataStore`、音乐缓存、validation 烟测）不在本压测路径，未改动。
- 单测（`StructureTransactionJournalTest` 8 → 12 项）：首次 `AccessDeniedException` 后重试成功（3 次尝试、0 次兜底）；5 次原子尝试耗尽后兜底成功（6 次原子尝试、1 次兜底）；`AtomicMoveNotSupportedException` 一次失败即兜底；兜底删除目标后仍失败时显式抛出、`.dat` 缺失、`.tmp` 保留且 suppressed 带临时路径。全套 2654 项普通单测（P33 基线 2650，本轮 +4）+ Sable 16 项，失败/错误/跳过均为 0（`build/jnl-tests-full2.log`；定向 `build/jnl-tests-targeted.log`、`jnl-tests-targeted2.log`）。
- 压测对照（修复前 2/108 次捕获失败 → 本轮 0/N）：
  - 基线：Sable r2 `TARGET_WRITE_FAILED/journal_copied_persist_failed`（`build/stress-server-sable-r2.log:24247`）；原生 r5 第 20 次捕获 `TARGET_WRITE_FAILED/journal_decision_persist_failed`（`build/stress-server-native-r5.log:24427`）。
  - 本轮原生 r8（120 次操作 / 3 区域 / 60 秒，新目录 `run/stress-server-native-r8`）：PASSED，`operations_completed=120/120`、`capture_retries=0`、`slot_recycle_ok=120`、`cleared_cells=3240`、`ticks=3310`；日志中 `AccessDeniedException`/`journal_*`/`writeAtomically` 计数为 0（`build/stress-server-native-r8.log`、`build/stress-server-native-r8-summary.txt`、`build/jnl-stress-native-r8-driver.log`）。
  - 本轮 Sable r5（20 子级 / 30 次循环 / 60 秒，新目录 `run/sable-compat/stress-server-sable-r5`）：PASSED，`children_created=20/20`、`operations_completed=30/30`、`capture_retries=0`、`ticks=1197`；日志 0 次同类失败（`build/stress-server-sable-r5.log`、`build/stress-server-sable-r5-summary.txt`、`build/jnl-stress-sable-r5-driver.log`）。
- 失败轨迹（目录、日志与结果全部保留）：修复后首轮原生 r7 仍在 `RECYCLE` 第 5 次操作抛出 `NativeStructureManifest.writeAtomically` 的同一 `AccessDeniedException`（`build/stress-server-native-r7.log:23943`、`build/jnl-stress-native-r7-driver.log`、`run/stress-server-native-r7`）；第二处站点改走共享工具后 r8 全通过。另：首轮编译失败为测试里 `AccessDeniedException(String,String)` 构造器不存在（JDK 只有单参或三参），改用三参构造器后重跑（`build/jnl-tests-targeted.log` 保留失败原文）。
- 回归：`.\gradlew.bat test sableCompatTest -DisDev=true`（如上）；`build -DisDev=true`（`build/jnl-build-dev.log`）、`build -DisDev=false`（`build/jnl-build-release.log`）通过，含 `verifySableCompatPackaging` 等 Sable 门禁。
- 未验边界：真实断电/拔盘下的兜底窗口（先删后移导致目标短暂缺失）未做断电注入，仅按崩溃安全语义推进；杀毒/索引器长期强占导致重试与兜底全部失败时只保证“显式抛出 + 保留临时副本”，自动恢复未实现（孤儿 `.tmp` 不参与扫描，需人工按 suppressed 路径取证）；`WorldDataStore` 等其余 `ATOMIC_MOVE` 站点保持原样；`run/stress-server-native-r7` 的失败世界目录保留未清理。
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `7808724F3E12C7A1B4336F94F20B47E21E8DF2AE17EA74552CCC79D5F6EE5F30`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `A6A4F01AE3B578AD62903AD19BFCBBDF66462B79A896A43AB00399085CD9D94A`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。
