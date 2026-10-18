# SubLevel 原生子系统 Phase 1：Plot 存储与统一物理接入

日期：2026-10-18  
状态：Phase 1 基础闭环已跑通；256 持久/64 活跃预算、重启恢复、真实捕获/BlockEntity 已实测；技能事务入口仍待接入。

## 1. 本阶段新增

| 组件 | 作用 |
| --- | --- |
| `data/academy/dimension/sublevel_plots.json` | 共享虚空 plot 维度，替代每结构独立 `structure_space_N` |
| `NativeStructureDimensions.PLOT_KEY` | plot 维度身份；加入保留维度集合，禁止玩家进入 |
| `PlotStorage` | plot 分配、chunk ticket、force-load、局部/世界坐标映射、方块读写 |
| `PlotGeometry` | 从真实 plot 方块读取碰撞形状，生成 struct-local physics boxes |
| `SubLevelRuntime` | plot lease + SubLevel + 统一 universe 的生命周期；创建/激活/停用/移除/step；超出 64 活跃预算的条目保存为 INACTIVE |
| `SubLevelManifest` | 原子 NBT manifest；保存 plot、bounds、pose、state、revision，支持重启 claim/restore |
| `TerrainSectionGeometry` | 直接读取 16³ section 的真实方块碰撞形状，跳过空碰撞（草/花等） |
| `TerrainColliderCache` | 只对已加载 chunk 的活动区生成静态地形 body；16 section 预算，2 tick 滞后回收；每 256 盒分批 |
| `SubLevelCaptureService` | 第一个真实 plot 写入器：复制宿主方块与 BlockEntity 到 plot、建体、再移除源方块；失败恢复源方块/BlockEntity 并回滚 |
| `MixinNativeStructureBoundary` | 旧 structure_space 的 barrier 边界不再误伤 plot 维度；plot 读写走真实方块 |
| legacy auto-resume | 旧 Region 在重启后自动 resume（最多 3 个），修复 H/R/喷流重启后的 `structure_not_physical` |
| `SubLevelServer` | 每服务器一个 `PlotStorage` + 一个 `SubLevelPhysicsUniverse`；仅在 native 后端被选择时启动，负责启动/停止/诊断 |
| `SubLevelDebugCommands` | `/academy debug sublevel status|probe|remove` |

## 2. 本阶段验证证据

专用服务器（非 Sable，`runServerCompat`）启动参数：

```
-Dacademy.sublevel.probeOnStart=true
-Dacademy.sublevel.probeAndStop=true
```

服务器日志：

```
[Server thread/INFO] [academy/]: SubLevel backend ready: plot=academy:sublevel_plots capacity=4096 active=0
[Server thread/INFO] [academy/]: SubLevel startup probe: created 50b4ea0c-... plot=PlotPos[x=0, z=0] boxes=1
[Server thread/INFO] [academy/]: SubLevel startup probe: created 8933a40b-... plot=PlotPos[x=0, z=0] boxes=1

--- 持久化/重启验证 ---
[Server thread/INFO] [academy/]: SubLevel backend ready: plot=academy:sublevel_plots capacity=4096 active=0 restored=0
[Server thread/INFO] [academy/]: SubLevel startup probe persisted: SubLevelId[value=b805f7e8-...]
[Server thread/INFO] [academy/]: SubLevel backend ready: plot=academy:sublevel_plots capacity=4096 active=1 restored=1
[Server thread/INFO] [academy/]: SubLevel restore verify: active=1 entries=1

--- 地形碰撞验证 ---
[Server thread/INFO] [academy/]: SubLevel terrain probe: Diagnostics[sections=16, boxes=31921, created=16, skipped=0]

--- 捕获写入 plot 验证 ---
[Server thread/INFO] [academy/]: SubLevel capture chest contents: 3 minecraft:diamond
[Server thread/INFO] [academy/]: SubLevel capture probe: captured id=5fa221e0-... plot=PlotPos[x=0, z=0] blocks=8 boxes=8 block_entities=1
[Server thread/INFO] [academy/]: SubLevel restore first plot=PlotPos[x=0, z=0] blockEntity=ChestBlockEntity item0=3 minecraft:diamond
[Server thread/INFO] [academy/]: SubLevel restore verify: active=1 entries=1

--- 旧 Region 自动恢复验证（复制用户测试世界）---
[Server thread/INFO] [academy/]: Resumed 2 saved physics region(s) in minecraft:overworld

--- 64 活跃 + 16 地形 section 基准 ---
SUB_LEVEL_PHYSICS_BENCHMARK bodies=64 ticks=120 elapsed_ms=31.3043
SUB_LEVEL_ACTIVE_BENCHMARK bodies=64 terrain=16 ticks=120 elapsed_ms=16.7735
```

同时日志确认 `academy:sublevel_plots` 与四个旧 `structure_space_N` 一起加载、保存，并且旧结构维度仍可用。probe 还验证了：

1. plot 维度真实存在；
2. plot chunk 能通过 ticket 加载；
3. 写入石头方块后，`PlotGeometry` 能读取真实碰撞形状；
4. `SubLevelRuntime` 能在统一 `SubLevelPhysicsUniverse` 中创建刚体；
5. 服务器停止时 plot lease、body、world、library 能按顺序释放。

## 3. 当前边界

尚未完成：

- 捕获事务写入 plot/BlockEntity 已由 `SubLevelCaptureService` 打通，但尚未接入技能权限、崩溃事务 journal 与客户端可视化；
- 多结构/复杂几何的重启恢复（当前 manifest/restore 只验证了单方块、ACTIVE 状态）；
- 地形缓存的多活动体调度/邻接 chunk ticket（当前只处理已加载中心 chunk，16 section 上限）；
- 旧 `structure_space_N`/Region/Projection 迁移；
- 客户端同步与渲染；
- 真实技能 H/R/喷流接入新 SubLevel。

Phase 1 Gate（256 持久 / 64 活跃、重启恢复、旧结构迁移、H/R/喷流不再 `structure_not_physical`）尚未达到，不能把本文件当作 Phase 1 完成。

## 4. 下一步

1. `TerrainColliderCache`：按活动 body 周围 ChunkSection 增量添加/移除静态 body。
2. 捕获事务桥：`StructureCaptureCoordinator` 的目标写完后把方块落到 plot，并创建 SubLevel；旧路径保留开关。
4. 旧结构迁移器：把旧 storage 结构复制到 plot，校验后切换引用。
5. 256 持久 / 64 活跃基准与重启恢复测试。

## 5. 风险

- 目前 plot 维度使用 `minecraft:overworld` 维度类型与 flat void 生成器；后续需评估高度、光照、天气和 chunk 保存成本。
- `PlotStorage` 每次分配 2×2 chunks，容量 4096；尚未做 plot 空间回收/压缩。
- `PlotGeometry` 目前每方块生成一个 AABB；大规模结构还要按 section 合并和缓存。
- probe 只验证单方块，未验证大型/旋转/多 body 的 chunk 几何。
