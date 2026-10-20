# Local / Sable 服务端配对基线

日期：2026-10-20。**这是方案阶段的小样本测量，不能表示所有玩法或客户端性能已验收。**

## 测量结果

固定 128 格石头（8×2×8）、相同地形/种子和公开 API；不加载 Sable 的 local 组，对比加载固定 Sable `2.0.6-port.26.2.0.70` 的组。每个场景每后端两个独立进程，第二轮反转运行顺序，共 8 次。最终运行 ID 为 `local/sable-ground/floating-r5/r6`。所有结果均完成采集且初始质心对齐通过。

创建耗时是同步 capture 调用，不含客户端首帧；表中为两次运行均值，括号是原始两次值。

| 场景 | 本地创建 ms | Sable 创建 ms | 本地 / Sable |
| --- | ---: | ---: | ---: |
| 地面提取 | 204.82（191.10、218.55） | 74.16（72.86、75.46） | **2.76×** |
| 悬空创建 | 203.84（200.25、207.43） | 77.55（76.78、78.32） | **2.63×** |

抬升阶段的服务器 tick 耗时如下。p95 先在每次运行内按 nearest-rank 计算，再对两次 p95 取均值；不是把两组原始数据混在一起的整体 p95。该阶段约 259 个样本/运行，暖机和 capture 单列。数字包含服务器其他工作，不是纯求解器耗时。

| 场景 | 本地 p95 均值 ms | Sable p95 均值 ms | 本地 / Sable 成本比 |
| --- | ---: | ---: | ---: |
| 地面抬升 | 4.35 | 5.32 | 0.82× |
| 悬空抬升 | 4.34 | 5.37 | 0.81× |

**在这个简单场景中，本地创建显著更慢，但运行阶段没有复现“本地服务端总是更慢”。** 两侧控制精度未达到相同结果，成本比也不能解释成相同功能的吞吐优势。

功能观测：

- 本地质量约 128，Sable 为 256；测试使用相同 k/m=30、d/m=16、最大 F/m=30，避免质量差直接造成控制力不公平。此参数不是当前 H 的全部参数/资格逻辑。
- 本地最终上升约 **3.11445 格**，Sable 约 **3.02994 格**。以 3±0.1 为窗口，本地连续在窗内仅 27 tick，Sable 地面 223 tick、悬空 226 tick。因此本地在本夹具的“连续 200 tick 精度”判定失败。不能把这项直接换算为真实 H 技能的悬浮时长结论。
- 两侧提交投掷后立即读到初速 **50 blocks/s**。20 tick 后本地水平移动 **50.00 格**，Sable 约 **47.87 格**，墙钟均约 1 秒。在无复杂地形停顿的场景里，没有发现本地发射参数本身更慢；Sable 后续速度演化不同，应独立标定阻尼等后端行为。
- 本次 cuboid 提取能抬起，不覆盖此前原 H 不规则 128 格在槽内卡死的反例。形状与接触邻域必须作为专项，不得被简单块状结构的成功替代。

## 与真实 test 故障的关系

上一轮真实 H/JFR 已测到创建 244–658 ms、慢 tick 116–1704 ms；服务端 2155 个执行采样中 1779 个调用栈包含 `TerrainSectionGeometry.merge`（82.6%，是采样占比，不是墙钟占比）。该算法反复两两合并并重启搜索；复杂地形/持续失效与本次简单石台产生的负载不同。

所以本轮方案按四项分解：捕获事务与同步保存造成创建尖峰；复杂地形碰撞编译造成慢 tick；子步控制造成位置精度差；逐块渲染和无时间轴姿态同步可能影响画面流畅性。后两项中的客户端性能倍数仍未测，不从服务端数据推算 FPS。

## 发现的对照条件问题

初始原 H 清单在 Sable 桥创建后，实际质心相对源格中心 X/Z 各约 -4 格；本地无此偏移。r3 中 Sable 的 pose.worldOrigin 已带该偏移，而 localCenterOfMass 与格子平均中心一致。这是桥接/初始变换的待审计异常，不能归因于求解器性能。换用首格位于原点的 cuboid 后，对齐误差通过 ≤0.01 门槛；没有修改 Sable 或 Academy 现有捕获逻辑来消除异常。

无效/预备样本保留在 run/build：r1 的增益超限；r2 的观测超出已加载区；r3 的坐标错位；r4 的 4 格厚结构抬升 3 格后仍与台壁相交。这些均未进入上表。

## 证据与限制

- 汇总与原始文件 SHA-256：[baseline-2026-10-20.json](../validation/structure_parity/baseline-2026-10-20.json)。每次运行的 p50/p95/max、功能结果与质量均单列。
- JBR、CPU 标识、夹具与 native 库哈希：[environment-2026-10-20.json](../validation/structure_parity/environment-2026-10-20.json)。固定 Sable 闭包由原有 manifest 校验。
- 原始逐 tick 数据：`run/sable-compat/parity-<RunId>/server/result.json`；启动/校验日志：`build/parity-<RunId>.log`。这些临时输出不纳入 Git。
- [复跑与汇总命令](../validation/structure_parity/README.md)。每次使用新目录，脚本不会覆盖世界。
- 原 `run/saves/test` 160 个数据文件与上一轮基线一致，变更 0，见 `build/structure-parity-source-check.json`。
- 样本量每格仅 2 次；未独立固定 JVM heap、CPU 电源策略或后台负载，60 tick 暖机不能消除全部 JIT/缓存差异。没有统计置信区间，不作总体性能倍数或发布性能承诺。
- **UNVERIFIED**：客户端帧 p50/p95、首次可见延迟、GC/分配、带宽、多结构/大结构、复杂 test 地形中的同场景 Sable 对照、真实 H 三档/精密节点、同质量同惯量同材质参数的纯物理微基准。

实施契约见 [FILL-SPEC.md](../FILL-SPEC.md)，方案见 [v2 设计](structure_pipeline_v2_plan.zh-CN.md)。七个 PIPE 草案端口已实现并有单元测试，但均未接入生产路径，`GAMEPLAY_ENABLED` 仍为 false；本文的功能失败与性能结论不因草案单测通过而改变。

## 2026-10-20 r7/r8 复测（地形 section 合并优化后）

在 `TerrainSectionGeometry.merge` 从两两重启合并改为三轴分组贪心合并后，用同一 8×2×8 `cuboid_128.csv`、同一平坦/悬空夹具和全新隔离世界复测 8 次（local/sable × ground/floating × 两轮，第二轮颠倒顺序）。所有运行 `measurement_status=COMPLETE`、`alignment_valid=true`。

| 场景 | 本地创建 ms | Sable 创建 ms | 本地 / Sable | 本地 lift p95 ms | Sable lift p95 ms | p95 比 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 地面 | 407.77（329.28、486.25） | 139.22（134.42、144.02） | **2.93×** | 11.54 | 16.67 | **0.69×** |
| 悬空 | 365.60（382.45、348.74） | 124.80（112.84、136.77） | **2.93×** | 9.74 | 11.82 | **0.82×** |

对照 r5/r6：地面创建 204.82/74.16 = 2.76×，悬空 203.84/77.55 = 2.63×；地面 p95 4.35/5.32 = 0.82×，悬空 4.34/5.37 = 0.81×。本轮**两侧绝对值都明显高于上一轮**（创建约 1.6–2.0×，p95 约 2.2–3.1×），环境/负载未受控，不能把绝对值差直接归因于本轮改动；只能谨慎看同一轮的本地/Sable 比值。

结论：

- **创建差距没有缩小**：2.93× vs 上一轮 2.76×/2.63×。地形合并发生在 terrain section 编译，不在同步 capture 调用内，本来就不应改善 `capture_call_ms`；本轮数据与这个判断一致。
- **lift p95 比值**地面从 0.82× 变为 0.69×、悬空 0.81× 变为 0.82×，只有地面一项变化，且绝对值同时受环境放大；128 格平坦石台地形过简单，不能把这一项单独归功于合并算法。复杂地形需另建夹具/JFR 对照。
- **功能结果不变**：本地仍 `lift_status=FAILED`、最长连续 hover 27 tick、prelaunch rise 3.11445；Sable `PASSED`、223/226 tick、3.02972。3/5/7±0.1 与 200 tick 的本地精度问题仍未解决。
- 首次运行 `local-ground-r7` 时，PowerShell 5.1 把 JVM 的 `-Xverify:none` stderr 警告当成终止错误，`run-pair.ps1` 在检查 `result.json` 前假失败；服务器实际已完成，结果仍为 COMPLETE。之后已在 `run-pair.ps1` 中把 Gradle 调用改为非终止并只看退出码/result.json；其余 7 次正常完成。这类假失败不应再被记成测量失败。

证据：[remeasure-2026-10-20-r7r8.json](../validation/structure_parity/remeasure-2026-10-20-r7r8.json)（SHA-256 `1A66FBFCE06489A6551F3B834AC1E591366691EC3DCBF3D7B89F29F927EA5F8C`）；逐 tick 原始数据在 `run/sable-compat/parity-{local,sable}-{ground,floating}-r{7,8}/server/result.json`，日志在 `build/parity-*.log`，均不纳入 Git。原 `run/saves/test` 未被读写。

下一步优先级据此调整为：**先定位 capture 的固定开销（PIPE-01/03），而不是继续只优化 merge**；r11–r14 已完成该定位并把 plot 区块加载移出 capture，见下节。merge 算法已有 9 项单测和 4096 格 1 盒/5 秒上限证据，但其真实复杂地形收益仍需专项 JFR 与 Sable 对照。

## 2026-10-20 捕获分阶段剖析与 plot 预开（r11–r14）

为定位 `capture_call_ms` 的固定开销，新增 opt-in 的 `CaptureTimings`：设置 `ACADEMY_CAPTURE_TIMINGS=true` 后，parity init 把 `-Dacademy.captureTimings=true` 传给服务器，捕获链路按阶段输出 `CAPTURE_STAGE owner=... stage=... ms=...`。未设置时只保留一次布尔判断，不改变捕获行为。以下 r11/r12 均为单次 local-ground 捕获，包含日志开销，不是 p95，也不能替代多轮配对。

### 分阶段结果

| 阶段 | r11 无预开 ms | r12 有预开 ms |
| --- | ---: | ---: |
| `capture_call_ms`（探针） | 363.30 | 227.19 |
| `capture_total`（链路内） | 357.11 | 224.44 |
| `plot_chunk_load` | **115.07** | **0** |
| `target_allocate`（含 create_staged） | 126.56 | 17.66 |
| `finalize_flush_storage` | 71.57 | 67.03 |
| `save_host` | 30.38 | 29.64 |
| `save_plot_level` | 10.11 | 9.97 |
| `journal_prepare` | 27.04 | 13.97 |
| `capture_encode_cells` | 9.73 | 3.73 |
| `step_remove_source` | 9.16 | 6.95 |
| `copy_verify` + geometry + body + view | 约 16 | 约 15 |
| `journal_target` + copied + commit | 约 15 | 约 19 |

结论很明确：**创建尖峰的大头不是几何或物理，而是 plot 区块的同步加载和两处整世界保存**。副本写入、读回校验、body 创建、视图捕获合计不到 20 ms；`plot_chunk_load` 一项就占 r11 的 32%，两处整世界保存（finalize flush + host + 第二次 plot）合计约 107 ms。

### plot 预开池

`PlotStorage` 现在默认保留 2 个已加载、已清空的空 plot（`academy.sublevel.plotPrewarm`，0 关闭）。`SubLevelServer.tick` 每 tick 最多推进一个预开区块：先 `reserve` 票据和 lease，再逐 tick `getChunk`，全部到齐后 `clearUnusedContent`，放入 ready 队列；`allocate()` 优先取 ready lease，队列空时仍走原来的同步分配兜底。`close()` 释放 ready/pending lease。

r12 的预开日志：第一个区块 **119.456 ms**（tick 1，plot 维度首次区块生成/加载），随后同一 plot 的区块约 0.017–0.023 ms，第二个 plot 的四个区块约 0.005–0.009 ms——第一次加载把该区域/region 的固定成本付掉了，后续预开几乎免费。因而：

- **capture 从 363 ms 降到 227 ms**（r11→r12），`plot_allocate` 从 117 ms 降到 0.02 ms；
- 代价是 tick 1 出现一个约 119 ms 的预开 tick；这是**把工作移出 capture，不是消除工作**。夹具的 60 tick 暖机把它排除在 lift p95 之外，首次进服/启动延迟尚未测；
- 队列目标 2 是有界内存/票据占用；捕获消耗一个后下一 tick 补一个，本会话后续区块已在缓存中，补位约 0.005–0.02 ms；新开服务器仍会在首次预开时付 119 ms。

### r13/r14 两轮配对复测

预开后用 local/Sable × ground/floating × 两轮（第二轮颠倒顺序，floating 因一次外层命令超时改用 `local-floating-r14b` 补跑）复测 8 次，全部 COMPLETE、对齐通过：

| 场景 | 本地创建 ms | Sable 创建 ms | 创建比 | 本地 lift p95 ms | Sable lift p95 ms | p95 比 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 地面 | 221.82（221.85、221.80） | 128.54（127.15、129.93） | **1.73×** | 8.69 | 15.00 | **0.58×** |
| 悬空 | 208.64（198.43、218.84） | 125.18（142.32、108.04） | **1.67×** | 10.65 | 13.44 | **0.79×** |

对照 r7/r8 的 2.93×/2.93×，本地创建均值从约 408/366 ms 降到约 222/209 ms，Sable 均值基本稳定在约 125–129 ms；创建比降到 **1.67–1.73×**。但：

- 仍不是 1×；剩余约 107 ms 是整世界保存（`finalize_flush_storage` 67 + `save_host` 30 + 第二次 `save_plot_level` 10）。下一步应做**局部 chunk 耐久协议**（只保存受影响的 plot/host chunk 并等待落盘），而不是继续挪动或缩小保存范围。
- 本地 `lift_status` 仍 FAILED、最长连续 hover 27 tick；Sable PASSED（223/226）。创建变快没有改变 3/5/7 与 200 tick 的精度问题。
- r13/r14 每格仍只有 2 次，环境/负载未固定；Sable 绝对值在 108–142 ms 间波动，不能用单轮小数点比较。tick 1 的 119 ms 预开尖峰需要另测首次进服/启动延迟，不能只看 capture 指标。

证据：[capture-profile-2026-10-20.json](../validation/structure_parity/capture-profile-2026-10-20.json)（SHA-256 `7DCF8915595D526BB1A89AFD704AEFBDFA2EAE8E28972890A3000E3B76B1A179`）、[prewarm-2026-10-20-r13r14.json](../validation/structure_parity/prewarm-2026-10-20-r13r14.json)（SHA-256 `591AA82F4B6D6EB203002A2B2F6F8C015714864B31BD8C424F9A08CF32D419CF`）；逐次原始结果在 `run/sable-compat/parity-*/server/result.json`，日志在 `build/parity-*.log` 与各 server `logs/latest.log`，均不纳入 Git。

下一步已由 r15–r17 完成：**只保存受影响的 chunk 并同步落盘**替换了 `finalize_flush_storage` 与 `save_host` 的整级保存；见下节。剩余主要功能问题转为本地 3/5/7 与 200 tick 精度。

## 2026-10-20 局部 chunk 耐久（r15–r17）

### 实现

整级保存在 capture 里只承担“把改动落盘”的职责，实际只需要目标 plot 的 lease chunks 和源 host cells 所在 chunks。新增 `TargetedChunkDurability`：

- `ChunkMapAccessor`（mixin）调用 `ChunkMap` 的私有 `save(ChunkAccess)`，再用公开的 `SimpleRegionStorage.synchronize(true)` 等待 region 异步写完成；
- `SubLevelRuntime.finalizeStaged` 改为 `flushPlotChunks(id)`：只保存该 plot 的 4 个 lease chunks，不再 `storage.level().save(...)`；
- `SubLevelCaptureTarget.save(target, sourcePositions)` 只保存目标 plot chunks、manifest 和源 host cells 所在 chunks；`CaptureTarget` 增加带 positions 的默认重载，native/Sable 与无 positions 的调用仍走原整级保存；
- journal 顺序不变：目标 plot 落盘后写 COPIED/COMMIT_DECIDED，源 host 落盘后才写 PUBLISHED；`flushStorage()` 仍保留给非 capture 路径。

### 结果

| 阶段 | r12 仅预开 ms | r15 局部耐久 ms |
| --- | ---: | ---: |
| `capture_call_ms` | 227.19 | **139.53** |
| `capture_total` | 224.44 | **137.47** |
| `finalize_flush_storage` | 67.03 | **12.25** |
| `save_host` | 29.64 | **6.41** |
| `save_plot_level` | 9.97 | **4.51** |
| `save_manifest` | 4.05 | 6.13 |
| `step_save_target` | 51.56 | 25.58 |
| `drive_total` | 171.01 | 86.22 |

r16/r17 两轮配对（第二轮颠倒顺序）：

| 场景 | 本地创建 ms | Sable 创建 ms | 创建比 | 本地 lift p95 | Sable lift p95 | p95 比 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 地面 | 145.26（158.43、132.09） | 133.68（136.85、130.51） | **1.09×** | 11.24 | 14.37 | 0.78× |
| 悬空 | 152.51（152.26、152.76） | 127.31（123.01、131.62） | **1.20×** | 10.45 | 11.60 | 0.90× |

创建比从 r13/r14 的 1.73×/1.67× 进一步降到 **1.09×/1.20×**，相对 r7/r8 的 2.93× 已接近消除；本地 capture 132–158 ms，Sable 123–137 ms。剩余大项是 journal/manifest 写入（约 14+6+5+6+4 ms）、copy 校验/写入和 finalize body/view，不再是整世界保存。

### 限制

- 这次只替换 **SubLevel live capture** 的保存范围；native/Sable 与 `save(target)` 无 positions 的调用仍整级保存。只保证方块数据；不保存实体/level.dat。SubLevel admission 拒绝 block entity，因此不覆盖带 BE 的捕获。
- **崩溃/恢复矩阵尚未跑**：journal 顺序未变，目标/源 chunk 的落盘依赖 `ChunkMap.save` + `synchronize(true)`，还没有“写一半杀进程/重启后再读”的实测；这是下一步必须补的验证，不能只凭一次成功 capture 认定耐久完成。
- r16/r17 每格仅 2 次，环境/负载未固定；Sable 绝对值 123–137 ms，本地 132–158 ms，1.09×/1.20× 不是统计承诺。tick 1 的 119 ms 预开尖峰仍在，首次进服/启动延迟未测。
- 本地 `lift_status` 仍 FAILED、hover 27 tick；Sable PASSED（223/226）。创建性能已接近配对，**剩余的主要功能问题转为 3/5/7 与 200 tick 的本地精度**，不应继续用性能微优化掩盖。

证据：[targeted-2026-10-20-r16r17.json](../validation/structure_parity/targeted-2026-10-20-r16r17.json)（SHA-256 `D39B355C8C22539B5AF715489833B2CC0502369E4786F18C7C1FE3A01A9F552D`）；分阶段数据并入 [capture-profile-2026-10-20.json](../validation/structure_parity/capture-profile-2026-10-20.json)（SHA-256 `7DCF8915595D526BB1A89AFD704AEFBDFA2EAE8E28972890A3000E3B76B1A179`）。逐次原始结果与日志路径同前，均不纳入 Git。
