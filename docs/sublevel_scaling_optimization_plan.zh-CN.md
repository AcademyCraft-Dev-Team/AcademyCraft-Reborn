# SubLevel 物理结构扩展优化骨架

**状态：设计稿；不改变当前运行路径。** 本文基于当前工作树（含未提交的地形隔离/不平整抬升原型）定义目标架构、容量模型、分阶段实现与验收；未提交原型不等于已验收能力。

## 1. 目的与范围

单结构性能已接近 Sable（parity capture 1.09×/1.20×、lift p95 0.78×/0.90×），但同世界可承载数量明显低于 Sable，且在上限附近性能反而更差。本文给出从“64 active + 共享 64 section 地形缓存 + 单一 native world”扩展到可配置容量的完整骨架，并把当前未提交原型纳入基线，而不是重复建设。

范围：地形订阅/发布、调度预算、物理容量、捕获存储、客户端同步/渲染、旧 native/Sable 兼容、观测验收。不包含具体代码实现、Sable 源码复用或未测量的容量承诺。

## 2. 现状

### 2.1 硬上限

| 资源 | 当前上限 | 位置 | 影响 |
| --- | ---: | --- | --- |
| SubLevel active/宿主 | **64** | `SubLevelPhysicsUniverse.MAX_ACTIVE` | 直接数量上限；暂停结构仍占名额 |
| 共享地形 section | **64** | `TerrainColliderCache.DEFAULT_MAX_SECTIONS` | 多结构覆盖竞争；远处结构 admission 失败 |
| 共享地形盒 | **28,000** | `TerrainColliderCache.DEFAULT_MAX_BOXES` | 复杂地形/多结构更早耗尽 |
| native body/world | **256** | `NativePhysicsWorld.MAX_BODIES` | 动态结构 + 地形静态体 + 分批 body 共享 |
| native 盒/world | **32,768** | `NativePhysicsWorld.MAX_BOXES` | 大结构/多地形时先于 64 结构耗尽 |
| 单 body 盒 | **256** | `NativePhysicsWorld.MAX_BOXES_PER_BODY` | 超过即拆 body + fixed joint |
| plot/manifest | 4,096 | `PlotStorage.MAX_PLOTS` / `SubLevelManifest.MAX_ENTRIES` | 当前不是首要绑定 |
| 旧 native 槽/区域 | 4 / 3 | `NativeStructureDimensions.SLOT_COUNT` / `NativeStructurePhysicsRegionLayout.MAX_REGIONS` | 仅旧引用；新物理捕获走 SubLevel |

### 2.2 当前未提交原型（重要输入）

工作树已经包含一组针对“地形覆盖不足冻结所有结构”的未提交改动，设计必须在其上继续：

- **TerrainColliderCache**：从单一全局 plan 改为对每个 active bounds 分别 `requiredPlan`/admission；新增 `covers(bounds)`、`AdmissionBudget`、`sectionEstimates`、`requiredPlans` LRU、`prepare/install` 分离；每个请求必须完整装下才 reserve；失败请求不保留半覆盖。
- **SubLevelRuntime**：`terrainRequests` 生成每结构 swept bounds，按玩家距离/已覆盖优先排序；每结构独立 `covers`，失败的加入 `blocked`；`stepCovered(..., blocked)` 只暂停缺覆盖结构，其他结构继续。
- **SubLevelPhysicsUniverse / NativePhysicsWorld / ABI v2**：新增 per-body `set_simulation_paused`；缺地形结构变成不可控但可碰撞的静止体；其他 body 继续使用同一 solver。ABI 升到 v2，新增 paused 快照语义；旧 ABI 1 被拒绝。
- **测试/夹具**：`SubLevelTerrainIsolationSmoke` 验证“暂停结构不动且拒绝控制、附近 body 仍动”；`SubLevelLiftTiersSmoke` 增加 embedded/slope/step/corner/roof/disturbance、3/5/7 和 200 tick 检查；`docs/sublevel_uneven_lift_validation_2026_10_21.zh-CN.md` 记录已通过/未验证项。
- **native**：`contact_surface.rs`/Rust 库/Windows DLL 也已改动，需与 ABI v2 一起验收。

**这组原型修复的是“一个结构缺地形时不再冻结全部结构”**，不是容量/性能的最终方案；它仍有 64 active、64 section/28k 盒、单一 world/256 body/32k 盒、paused 结构占 active、没有捕获前容量 admission、没有真正生效的 tick 预算、O(N) 编排和客户端每结构渲染。

### 2.3 根因

1. **数量上限是共享预算，不是引擎极限**：`MAX_ACTIVE=64` 注释明确是 first-version activity target；地形 64/28k、world 256/32k 与 64 一起构成实际上限。
2. **paused 结构仍占 active 名额**：原型只让缺地形结构停止模拟并保持可碰撞，但 `entries` 仍计入 64；新捕获在 64 处失败，实际可动数更低。
3. **地形仍是单一全局预算**：每个结构有自己的 request/covers，但总 section/盒仍由 64/28k 共享；`terrainRequests` 仍遍历所有结构；任何位姿变化仍可能触发全量 `terrain.update`。远处/复杂结构会反复 admission 失败。
4. **单一 Rapier world**：动态结构、地形 FIXED body、分批 body、fixed joint 和一个共享 solver；每 body 最多 256 盒，超过即拆 body；动态 body CCD 常开。
5. **每 tick O(N) Java 编排/O(N×P) 快照**：`SubLevelRuntime.tick` 多轮遍历、地形 demand 排序、carry 实体查询、逐结构指标；`SubLevelServer.sendSnapshots` 对每结构×每玩家构造 view/pose 包。
6. **6 ms 预算没有真正生效**：`SubLevelSimulationBudget.plan` 的 scheduled/deferred 没有被用于跳过工作；超预算仍尝试全部，表现为 tick 拉长而不是降级。
7. **客户端每结构一套投影/几何/渲染**：没有跨结构 instancing/合批；N 增大后 FPS/带宽下降。

## 3. 目标与非目标

### 目标

- 容量可配置：先 128、再 256 active/宿主；上限由 body/盒/section/plot/客户端预算推导，不再固定 64。
- N 增长时成本按“脏结构 + 可见/近处”增长，而不是每次全量地形 replan。
- 目标 N 下服务端 tick p95、客户端 FPS/带宽有硬指标；同夹具 Sable 对照可复现。
- 正确性：碰撞/接触、捕获/恢复、3/5/7 悬浮、交互语义不退化；不允许缩盒/放宽容差/重复重力。
- 兼容：旧 4 槽 native、Sable 桥、现有存档/API；新能力窄开关逐阶段启用。

### 非目标

- 不复制 Sable 源码；不把 Sable 内部实现作为本地设计依据。
- 不在 N 扫描/同夹具对照前承诺“达到 Sable 数量”。
- 不把当前未提交原型直接当最终验收；不默认改写存档格式。

## 4. 目标架构

```
ServerTick
  FrameScheduler (soft/hard budget, priority)
    ├─ TerrainCoordinator
    │    ├─ TerrainSectionStore (immutable sections, refcount, epoch)
    │    ├─ TerrainSubscription per structure (dirty-only leases)
    │    ├─ ChunkLease/Prefetch (async budget)
    │    └─ Native static bodies (shared per section)
    ├─ PhysicsCoordinator
    │    ├─ World/Shard(s) (body+box budget)
    │    ├─ Structure bodies (compound colliders, selective CCD, sleep/pause)
    │    └─ Contact/pose publication
    ├─ CaptureCoordinator
    │    ├─ Early capacity admission
    │    ├─ PlotPool (size-aware) + manifest
    │    └─ Journal/local durability
    └─ SnapshotBroker (interest, delta, byte budget)
         └─ Client (batch/instancing, LOD, collision index)

Legacy: native 4-slot (frozen/migrate) | Sable bridge (unchanged, parity test)
```

原则：**订阅而非全局并集；共享不可变 section + refcount；预算真正生效；paused 不占模拟容量；先测量后扩容；旧路径可回退。**

## 5. 核心设计

### 5.1 地形订阅与共享 section（P1）

当前 `TerrainColliderCache` 的 per-request admission 是过渡形态，P1 把它收敛为：

```java
record SectionKey(int x, int y, int z) {}
record TerrainSection(SectionKey key, long revision, Object chunkIdentity,
                      List<PhysicsBox> boxes, int sourceCells) {}
record TerrainSubscription(SubLevelId id, Set<SectionKey> required, Set<SectionKey> optional,
                           long epoch, int priority, boolean coverageComplete) {}
```

- **SectionStore**：每宿主/每 shard 一份；section 不可变，native FIXED body 每 section 只建一次，被多个结构共享；`refcount`/lease 防止驱逐仍被订阅的 section。
- **Subscription**：由结构 swept bounds 推导 required；只在跨 section、section 失效、增删/改尺寸时重算；用 lease 替代当前“全局 requests + covers 扫描”。
- **Admission**：先对 required 整组做 section/盒/chunk 预算检查；成功才 install/reserve；失败返回具体原因；不保留半覆盖。全局预算按优先级/距离/可见性分配，required 不被 optional 挤出。
- **构建流水线**：服务端线程抓取不可变 snapshot → 工作线程编译盒 → 主线程安装 native static body/发布 lease；PIPE-03 的 epoch/revision/coverage 门禁保证原子切换。
- **失效**：方块变更只 invalidate 受影响 section；订阅者按 epoch 重建；不全局重扫。
- **chunk**：lease/queue/每 tick 上限；预取前方 section；PENDING 结构等待，不拖累其他结构。
- **当前原型的保留/替换**：保留 per-request admission、`covers`、`sectionEstimates`、`requiredPlans` 的经验；用 lease/refcount 替换“每个 request 重新装/驱逐”的路径；旧缓存作为 fallback 开关。

**验收**：128 结构、单结构移动只重建跨边界 section；无 refcount 泄漏/悬空体；覆盖失败结构不进入物理步；新捕获在 admission 拒绝而不是半成品。

### 5.2 调度、预算与 active 容量（P2）

- **FrameScheduler**：soft 6 ms/hard 50 ms（可配置）；工作项 `(phase, id, priority, deadline, deferrable)`；优先受控/玩家附近/可见 > 移动 > active sleeping > 远处。
- **只采集脏工作**：地形脏、位姿变化、快照需要、持久化到期；sleeping/无交互跳过。
- **真实使用 scheduled/deferred**：延后可选地形 build、快照、持久化；**不延后**移动/受控结构的物理步和已就绪碰撞发布。不能因预算冻结移动结构；容量不足在 admission 拒绝新结构。
- **paused 策略**：当前 `simulationPaused` 只解决“缺地形时不冻结其他结构”；P2 必须定义 paused 生命周期：
  - 缺覆盖超过限定 tick → 转为静态/睡眠并释放动态 body/active 名额（保留可恢复姿态/内容）；
  - 覆盖恢复后按优先级恢复，不无限占用 64 槽；
  - `CapacityController` 区分 simulated/paused/static，activeCap 由真实可模拟资源推导。
- **指标**：固定环形缓冲，避免每结构每 tick 分配；记录 phase 实际耗时、deferred 原因、admission 失败、paused 数。

**验收**：目标 N 下 tick p95/hard 达标；deferred/paused 原因可见；无预算分配热点；paused 不永久占容量。

### 5.3 物理世界与容量（P3）

- **单 world 先优化**：
  - 每 body 盒上限提高或“单 body 多 collider”，避免 >256 盒拆 body + fixed joint；目标一结构一 body。
  - CCD 选择性：仅受控/高速/即将碰撞；悬停/低速/睡眠关闭。
  - 原生 sleep；地形 section 静态体共享；body/盒预算按 host 统计。
- **PhysicsShard（仅在单 world 实测不足时）**：
  - 空间 cell + 重叠 AABB union-find 同组；每组独立 world/terrain lease/预算；跨组接触按玩法需求决定（若必须结构-结构接触则同组或单 world）。
  - 组迁移保存姿态/速度/控制租约，不闪断。
- **CapacityController**：

```java
record CapacityEstimate(int cells, int boxes, int sections, int bodies) {}
record Admission(boolean ok, String reason, Reservation reservation) {}
Admission admit(CapacityEstimate estimate, Priority priority);
```

  - 捕获前检查 active/body/box/section/plot/manifest；预留；失败返回原因；回收 plot/body/journal。
  - `activeCap = f(bodyBudget, boxBudget, sectionBudget, plotBudget, clientBudget)`，不是常量 64。

**验收**：目标 N 下 body/盒无硬失败；接触/3/5/7/持久化回归；p95 达标；shard 若启用，跨组语义明确。

### 5.4 捕获、存储与耐久（P4）

- 捕获前早期 admission（cells/boxes/sections/body/plot），不足直接拒绝；不先建 plot/body。
- PlotPool：保留空闲预开；按结构大小分级（小结构 1 chunk、常规 2×2），降低 chunk/ticket/内存；启动/空闲 tick 分摊预开。
- 局部耐久：保留 targeted chunk save + journal 顺序；补崩溃/恢复矩阵（写一半、重启、旧 journal、重复恢复）。
- manifest/存档版本化；旧 native 4 槽冻结/迁移，不扩容。

**验收**：目标 N 连续捕获/释放/重启；成功率、恢复一致性、无孤儿/无整级保存尖峰。

### 5.5 同步与客户端（P5）

- SnapshotBroker：每玩家兴趣/可见/距离；全量 view 一次，之后 pose/盒 delta；批量包、每 tick 字节预算；远处降频；不可见不发。
- 客户端：跨结构材质/atlas 合批/instancing、mesh 缓存、LOD、后台构建 + 主线程上传预算、视锥/遮挡剔除；每结构碰撞索引只保留近处。
- PIPE-06 pose timeline、PIPE-07 mesh planner 按阶段接入。

**验收**：目标 N 客户端 FPS p95/帧时间/draw call/包字节达标；无逐帧全量重建；视觉/碰撞一致。

### 5.6 旧 native、Sable 与 API

- 旧 `academy:native_level`：4 槽/3 区域只保留兼容加载/迁移/错误提示，不扩容量。
- Sable 桥：不改变所有权/存档；同夹具对照；不复制源码。
- PIPE-01..07 按阶段接线，窄开关；`GAMEPLAY_ENABLED` 独立。

## 6. 容量模型

设 N 结构、平均 boxes `b`、sections `s`、plot chunks `p`；地形 boxes `T`、sections `S`。

```
N <= activeBudget
Σb + T <= boxBudget
Σbodies(b) + terrainBodies <= bodyBudget
Σs <= sectionBudget
N * p <= plotChunkBudget
N * clientGeometry <= clientBudget
```

当前：active 64、section 64、terrain 28k、body 256、box 32,768、plot 4,096。
建议目标（待 P0 实测确认）：

| 阶段 | active/宿主 | 地形 section | body | box | plot |
| --- | ---: | ---: | ---: | ---: | ---: |
| 现状 | 64 | 64 | 256 | 32k | 4,096 |
| P1 后 | 128 | 128–256 | 512 | 64k | 4,096 |
| P3 后 | 256（或 shard 等效） | 256–512 | 1,024 | 128k | 8,096–16k |

这些是待验证预算，不是承诺；`activeCap` 必须由实测 body/盒/section/客户端预算推导。

## 7. 分阶段路线图

### P0 当前原型收尾 + 基线（不改目标架构）

- 完成当前未提交原型的验收：`SubLevelLiftTiersSmoke` 的 slope/step/corner/roof/disturbance（本地与 Sable）、`SubLevelTerrainIsolationSmoke` test2 多结构 80 tick、ABI v2/contact surface/DLL 回归。
- 运行 N 扫描 1/4/16/32/64（静止/移动、聚集/分散、简单/复杂地形）；记录 capture 失败原因、paused/blocked、`/academy sublevel status`、phase 指标、JFR、客户端 FPS/带宽。
- 明确当前实际上限是 64 active、body/盒、terrain admission 还是 paused 占位；与 Sable 同 N 对照。
- 产出：原型验收报告 + 目标 N/预算决策。

**门槛**：P0 未通过前不进入扩容；不把当前原型当最终实现。

### P1 地形订阅化

- 在现有 per-request admission 上引入 SectionStore/Subscription/Lease/refcount、共享静态体、异步/分片 build、per-structure admission、失效/epoch。
- 保留旧缓存 fallback；A/B。

**门槛**：128 结构、无全局 replan；覆盖失败有界/可解释；p95 不劣化；碰撞/交互回归。

### P2 调度与 active 容量

- FrameScheduler 真正使用 scheduled/deferred；优先级；软/硬预算；paused 生命周期/回收；捕获前 admission；指标环形缓冲。

**门槛**：目标 N 下 p95；paused 不永久占槽；deferred 原因可见；无预算热点。

### P3 物理扩容

- 单 body 多 collider/提高盒上限；选择性 CCD；sleep；容量 admission；按需 shard。
- 目标 256 或 shard 等效；同夹具 Sable 对照。

**门槛**：body/盒无硬失败；接触/3/5/7/持久化回归；p95 达标。

### P4 捕获/存储扩展

- 早期 admission；plot 分级/池；崩溃矩阵；manifest 版本化；旧 native 冻结。
- 目标 N 连续捕获/释放/重启。

**门槛**：成功率/恢复/无孤儿/无保存尖峰。

### P5 客户端/网络扩展

- SnapshotBroker、批量/增量/兴趣；合批/instancing/LOD；PIPE-06/07。
- 目标 N FPS/带宽/内存。

### P6 容量与 Sable 对照验收

- 1/4/16/32/64/128/256 静态/移动/多人/复杂地形/崩溃；Sable 同夹具；发布门槛。

## 8. 测试与观测

**正确性**：section refcount/失效/驱逐/epoch；无悬空静态体；碰撞/接触与单结构基准；3/5/7 200 tick；捕获/恢复/崩溃；交互/采掘/放置/掉落；旧存档/旧 native/Sable 兼容。

**性能**：服务端 tick/phase p50/p95/max、CPU、分配、GC、body/盒/section、admission/paused 数、快照字节；客户端 FPS/帧时间/draw call/包字节/内存；固定硬件/电源/后台负载，至少两轮 AB/BA。

**观测**：`/academy sublevel status` 扩展 active/body/box/section/paused/blocked/admission 原因/每 shard；`SubLevelSimulationBudget`；`academy.sublevel.budgetLog`；JFR；现有 parity/隔离 smoke。

## 9. 风险与回滚

| 风险 | 缓解 |
| --- | --- |
| 地形 refcount/旧碰撞发布 | lease/epoch + PIPE-03 原子发布；单测/注入；旧缓存开关 |
| paused 结构长期占槽 | P2 paused 生命周期/回收；capacity admission；paused 不进入 simulated 预算 |
| 结构-结构跨 shard 接触 | 先单 world；shard 用重叠 union-find 同组；跨组接触需求则不做 shard 或加桥 |
| native ABI/盒上限修改 | Java/Rust 同步；原生测试；feature flag；失败回退 ABI/旧 DLL |
| 存档/manifest 迁移 | 版本化；只读旧格式；迁移前备份 |
| 线程安全 | 只传不可变快照；世界访问/安装留主线程 |
| 预算冻结移动结构 | 只延后可选工作；移动/受控物理不可延后；容量不足 admission 拒绝 |
| 客户端视觉/碰撞不一致 | 服务端精确碰撞优先；LOD 仅视觉；截图/几何对照 |
| Sable 许可 | 不复制源码；只行为对照 |

## 10. 待决问题

1. P0 N 扫描的目标 active N、客户端 FPS 目标、Sable 同夹具上限。
2. 当前 prototype 的 paused 体是否允许长期作为可碰撞静止障碍；若不允许，回收/转静态策略。
3. 结构-结构接触是否必须；决定单 world vs shard。
4. 旧 native 4 槽是否只迁移不扩容。
5. 目标硬件/地形复杂度/多人规模。
6. 存档版本化迁移窗口。

## 11. 结论

当前未提交原型已经解决了“缺地形冻结全部结构”的一部分，但容量和性能根因仍在：**64 active 与 64 section/28k 盒/256 body/32k 盒共享预算、paused 占槽、单一 world、O(N) 编排、没有真正预算、客户端每结构渲染**。

优化顺序必须是：**P0 收尾/基线 → P1 地形订阅化 → P2 调度与 active 容量 → P3 物理扩容 → P4 存储 → P5 客户端 → P6 Sable 对照**。先测量、后扩容；任何阶段都不以放宽碰撞/容差/存档语义换指标。
