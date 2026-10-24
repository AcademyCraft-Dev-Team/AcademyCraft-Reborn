# SubLevel 原生子系统 Phase 0 原型记录

日期：2026-10-18  
状态：Phase 0 原型完成，尚未接入玩法与网络  
目标：按已确认顺序实现“共享 Plot + 每维度统一物理世界 + 真实 Sub-Level 碰撞/交互/渲染”，首版验收线为 256 持久 / 64 活跃 / 60 FPS。

## 1. 与旧架构的关系

旧 `Region/Projection`（`NativeStructurePhysics`、每结构 `NativeStructureProjectionEntity`、每区域独立 Rapier world）保留为迁移兼容层，不再扩展。新代码位于：

```
org.academy.internal.common.sublevel
  PlotPos / PlotLayout / PlotAllocator
  SubLevelId / SubLevel / SubLevelState
  SubLevelPhysicsUniverse
  SubLevelCollisionIndex
```

Phase 0 不修改任何现有玩法入口，不改变旧结构的存档格式；技能仍走旧链路，直到 Phase 1 接入开关。

## 2. Phase 0 已实现

| 组件 | 作用 |
| --- | --- |
| `PlotLayout` | 共享 plot 网格的 chunk 尺寸、原点、容量；plot 坐标到 chunk 坐标的稳定映射 |
| `PlotAllocator` | 确定性 first-free 分配/释放，占用位图；不依赖 Minecraft 世界 |
| `SubLevel` | plot-backed 结构的身份、plot、局部包围盒、pose、生命周期、revision |
| `SubLevelPhysicsUniverse` | **一个 Rapier world** 承载多个 SubLevel；每结构可拆分为多个 fixed-joint 刚体；64 活跃体预算；统一 step 与姿态/质量发布 |
| `SubLevelCollisionIndex` | 纯几何逐格碰撞索引；按实际 world boxes 查询，不依赖实体 AABB |

### 设计约束

- 物理层不依赖 Minecraft 类型：`SubLevelPhysicsUniverse` 只使用 `PhysicsPose`/`PhysicsBox`/`PhysicsVector`，因此可在 standalone native test source set 中直接验证。
- 每个 SubLevel 的 body frame 一致；超过 `MAX_BOXES_PER_BODY` 时按 256 盒分批，用 fixed joint 锁定。
- 单个 world 最多 128 body（原生 ABI 限制），Phase 0 主动限制 64 活跃 SubLevel，符合首版验收线。
- 碰撞索引是“真实盒子列表 + 每结构 union 仅用于宽相位”；union 不参与最终碰撞形状。
- 旧 `NativeStructureProjectionEntity` 的 union AABB 没有被新系统复用。

## 3. 验证证据

### 单元测试

```
.gradlew.bat test -DisDev=true --tests "org.academy.internal.common.sublevel.*"
```

覆盖：

- plot 分配/释放/容量/chunk 映射；
- SubLevel pose/revision/world bounds；
- 两个墙体之间空腔查询为空；
- 旋转结构中心可命中、远处不命中；
- 移除结构后索引清空。

### 原生 64 体基准

```
.gradlew.bat nativePhysicsTest -DisDev=true   --tests org.academy.internal.common.sublevel.SubLevelPhysicsUniverseTest
```

本机结果：

```
SUB_LEVEL_PHYSICS_BENCHMARK bodies=64 ticks=120 elapsed_ms=35.0668
```

即 64 个动态刚体、120 个物理 tick，总计约 35 ms，平均约 0.29 ms/tick。该数字仅覆盖 Rapier 步进与姿态读取，**不包含**地形碰撞、SubLevel 区块、网络、渲染和交互；Phase 1/2 必须重新测量完整链路。

另有一个 300 盒结构拆成 2 个 fixed-joint 刚体的生命周期测试。

## 4. Phase 0 未覆盖

- 共享 plot 维度与真实 `LevelChunk` 存储；
- 地形按 ChunkSection 增量碰撞体；
- SubLevel 的 chunk 快照/差量网络；
- 服务端/客户端碰撞 Mixin；
- 通用方块破坏/放置/右键与容器；
- SubLevel 渲染；
- 旧结构迁移；
- 64 活跃体 + 地形的完整性能基准。

## 5. Phase 1 入口

1. 新建共享 `sublevel_plots` 维度（虚空、无玩家、chunk 远离主世界），用 `PlotAllocator` 分配 plot。
2. `PlotStorage` 持有 `LevelChunk`/BlockEntity，活动结构 force-load；未活动卸载。
3. `SubLevelRuntime` 维护 manifest、ticket、pose、生命周期；启动时按需加载，不恢复全部。
4. `SubLevelPhysicsUniverse` 接入 `TerrainColliderCache`：按活动 body 周围 ChunkSection 增量生成静态碰撞体。
5. 旧 `StructureTransactions` 捕获写入 plot，保留旧路径为迁移兼容层。
6. Gate：无 Sable 专服中 256 个持久 SubLevel、64 活跃，重启后恢复；旧结构可迁移；H/R/喷流不再 `structure_not_physical`。

## 6. 已知风险

- 共享 plot 维度需要在 NeoForge 26.2 下验证维度注册/传送/区块保存。
- 旋转碰撞的 AABB 近似仍需 Phase 2 的误差测试。
- fixed-joint 多 body 的完整质量/惯性/休眠行为需要真实结构几何验证。
- 首版性能数字必须包含地形、网络和渲染，不能引用本文件的 headless 数字作为最终结论。
