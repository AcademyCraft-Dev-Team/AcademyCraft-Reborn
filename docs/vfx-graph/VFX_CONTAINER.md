# VFX 容器化设计（M23–M28，Unity 式 Context + 数据流）

> 状态：**M23–M28b 已实现**，M28 仅剩「游戏内冒烟 + 彻底移除旧扁平路径」。本文档是事实源。设计决策见 `DECISIONS.md` ADR-027。
> 前置阅读：`PROGRAM.md`（架构规则）、`MODULES.md`（MOD-12/13/14/15）、`NODE_CATALOG.md`。

## 1. 背景与目标

用户反馈 VFX 节点编辑"不直观"：47 个 `vfx.*` 节点全部零端口，执行顺序靠节点列表插入序 +
隐藏的 `SimContext.spawnStart` 隐式耦合 spawn→init；多 spawn + init 组合需小心顺序。目标对标
Unity VFX Graph / UE5 Niagara 的容器流水线编辑体验：

| 现状痛点 | 容器化后 |
| --- | --- |
| 无端口、无连线，顺序靠列表序 | Spawn/Initialize/Update/Output 容器 + 显式 flow 边 |
| `spawnStart` 单点隐式耦合 | flow 边携带批次，每个 init 只处理上游 spawn 的批次 |
| 粒子属性藏在 ParticleBuffer 的 SoA | attr-read 算子把属性变成显式数据流 |
| 执行顺序徽标/右键重排 | 由容器结构 + 连线决定 |

## 2. 核心决策（ADR-027 摘要）

1. **新容器模型与核心 `Graph` 并行**，不破坏契约冻结；旧扁平 VFX schema 彻底废弃（无迁移链）。
2. **批次携带（完整 flow 语义）**：spawn 块输出「本帧新粒子索引批次 [start,end)」，flow 边传给下游
   init；每个 init 只处理自己上游 spawn 的批次。
3. **全属性数据流（本期）**：attr-read 算子逐粒子读取粒子属性 + 数学/曲线/渐变/参数算子驱动块属性。
4. **渲染层零改动**：`ParticleBuffer`→`VfxGraphRenderer`→`RenderSpec` 保持；Output 块 → `RenderSpec`
   保留 M21l 数据驱动（vertex/shader/blend 属性）。

## 3. 数据模型（M23，已冻结）

```
VfxSystem
 ├── contexts: [VfxContext]        # SPAWN/INITIALIZE/UPDATE/OUTPUT 容器
 │     ├── id/type/name/x/y
 │     └── blocks: [VfxBlock]      # id/type/properties/ports（端口由目录派生）
 ├── operators: [VfxOperatorNode]  # 自由算子 id/type/properties/ports/x/y
 ├── flowEdges: [VfxFlowEdge(from,to)]      # context 间批次 flow
 ├── dataEdges: [VfxDataEdge(from,to)]      # 算子输出 → 块输入（Edge.PortRef）
 ├── parameters: [GraphParameter]  # 黑板参数
 └── outputs: [String]             # 输出块 id
```

- `VfxNode` 公共接口（块/算子统一），`VfxSystem.nodes()` 扁平视图供校验/执行器。
- `ParticleAttribute` 枚举：POSITION/VELOCITY/SIZE/COLOR/ALPHA/AGE/LIFETIME/ROTATION/MASS/SEED/LAYER，
  各带 `ValueType` 与通道数。

## 4. JSON schema（新，无旧兼容）

顶层 `{version, kind:"vfx", id, parameters, contexts[], operators[], flow[], dataEdges[], outputs[]}`。
块/算子不序列化端口（目录派生）；`properties` 为字符串化 Map；flow 为 `{from,to}` context id；
dataEdges 为 `{from:{nodeId,portId}, to:{nodeId,portId}}`。`JsonGraphCodec.encodeValue/decodeValue`
转 public 供复用（曲线/渐变/参数值）。

## 5. 校验器（M23，已实现）

- context id 唯一；flow 边引用存在且成 DAG；非 SPAWN context 必须有上游 flow；
  至少一个 SPAWN 与一个 OUTPUT context；数据边节点/端口存在、方向与类型兼容；输出块存在。

## 6. 执行模型（M24 已实现）

```
VfxSystemSimulator.step(dt):
  Phase 1 SPAWN:   按 flow 拓扑序执行 spawn context 的块 → 每 context 收集 emitBatch 批次
  Phase 2 INITIALIZE: 按 flow 拓扑序执行 init context 的块，ctx.incomingBatches = 上游 SPAWN 批次并集
  Phase 3 UPDATE:   按 flow 拓扑序执行 update context 的块（全部存活粒子，无批次）
  OUTPUT context 不执行模拟（仅提供 RenderSpec）
```

- `SpawnBatch(start,end)` + `SimContext` 批次 API（`emitBatch`/`setIncomingBatches`/`forEachIncoming`），
  替代旧 `SimContext.spawnStart` 单点耦合（`spawnStart` 保留兼容旧执行器，M28 随旧扁平路径移除）。
- 容器块目录 `VfxBlocks`（M24 最小集：spawn_rate/burst、init_velocity/color/size、update_velocity/gravity/age/fade、
  output_quad×3）；M27 全量迁移（粒子 42 块 + M22 后 arc 4 块 = **46 块**）。

## 7. 算子节点集（M25 已实现）

| 类别 | 算子 |
| --- | --- |
| attr-read | `vfx.op.attr_position/velocity/size/color/alpha/age/lifetime/rotation/mass/seed/layer` |
| 常量/输入 | `vfx.op.constant`、`vfx.op.param_float/vec3/color`（黑板/存活参数，无绑定用属性兜底） |
| 数学 | `vfx.op.add/sub/mul/div`（输入端口 a/b 可被上游算子驱动，无绑定回退属性） |
| 曲线/渐变 | `vfx.op.curve`、`vfx.op.gradient`（`SimContext` 黑板参数采样） |

- `OperatorContext(buffer, particleIndex, simContext)`：particleIndex=-1 为非粒子上下文，attr-read 返回默认值。
- `VfxSystemSimulator` 构建算子求值 DAG（数据边 + 算子间连接 + 环检测），经 `PortValueSource`
  向块输入端口提供逐粒子值（无绑定回退属性）。

## 8. 编辑器（M26 已实现）

- 新 `src/editor/kotlin/.../container/` 包：`VfxContainerModel`（容器编辑态 + 命令 undo/redo + toSystem/load 桥）、
  `VfxContainerCanvas`（ImGui 容器画布：context 框内 block 垂直排列、算子自由放置、flow/data 贝塞尔连线、
  拖拽/连线/框选/右键、端口高亮）、`VfxContainerModelRef`（多文档切换）。
- GraphEditorApp VFX 模式路由容器画布/调色板/检查器；`EditorDocument` 增容器模型（每文档独立 undo）；
  保存/加载/热重载按 `kind:"vfx"` 走 `JsonVfxGraphCodec`；SHADER 扁平路径保留。
- 移除 VFX 执行顺序徽标与「Move Up/Down in Execution Order」（执行序由 context 内 blocks 列表序决定）。

## 9. 节点/资产迁移（M27 已实现）

- **VfxBlocks 全量 42 块（粒子）**：spawn 4（rate/burst/periodic/distance，含 shape）/ init 8 / update 10 / collision 5 /
  over-life 4 / orient 4 / output 7；块含输入端口（数据流）与 shape 支持；M22 后 +4 arc 块（`vfx.block.arc_*` + `output_arc`）→ **46 块**。
- **VfxOperators 全量 23 算子**：attr-read×11、constant、param_float/vec3/color/curve/gradient×5、add/sub/mul/div×4、curve/gradient×2。
- **7 个打包资产转档容器 schema**（`kind:"vfx"`）：minimal_burst/demo_burst/demo_fountain/demo_ribbon/demo_fire/demo_arc/skill_dirstrike。
- **运行时容器加载**：`VfxGraphManager` 增 `kind:"vfx"` 分支（containerAssets + JsonVfxGraphCodec），
  `GraphEffect`/`ActiveEffect` 增容器构造（VfxSystem → VfxSystemSimulator）；扁平资产（旧 schema）仍兼容，M28 彻底移除。

## 10. 运行时接线（M28 部分完成）

- `GraphEffect`/`VfxGraphManager`/`ActiveEffect` 容器路径已就绪（M27 双路径并存）；`VfxGraphManagerTest.spawnContainerAssetThroughManager`
  验证 `kind:"vfx"` 资产经管理器端到端。
- 性能门禁：`VfxSystemSimulatorPerfTest`（10k 稳态 ~4.6ms/帧 + 600 帧 churn ~43ms，对标 M16 预算）。
- **待办**：游戏内冒烟（需显示环境）确认容器资产渲染后移除旧扁平路径（`VfxNodes`/`VfxSimulator`）。

## 11. 里程碑状态

| ID | 状态 |
| --- | --- |
| M23 容器模型+序列化+校验 | **done**（新包 model/serialize/validate，主 test 通过） |
| M24 容器执行器 + 批次 + 最小块集 | **done**（`VfxSystemSimulator` + `SpawnBatch`/`SimContext` 批次 API + `VfxBlocks`，主 test 761） |
| M25 数据流算子 + 数据边接线 | **done**（`vfxgraph/operator` + 算子 DAG + `PortValueSource`，主 test 768） |
| M26 容器编辑器 | **done**（`VfxContainerModel`/`VfxContainerCanvas`/接线 + 每文档容器模型 + 容器 schema 读写，editorTest 95） |
| M27 节点+资产迁移 | **done**（粒子 42 块全量 + 算子 23 + 7 资产容器转档 + 运行时容器加载，主 test 773） |
| M28 运行时接线 | **部分 done**（性能门禁 + 容器管理器回归，主 test 775；M28b 块级批次 flow done；移除旧扁平路径 + 冒烟待显示环境） |

> 任务分解见 `TASK_LEDGER.md` M23–M28；决策见 `DECISIONS.md` ADR-027。
> **M22 电弧**：按此设计在容器化后启动（`vfx.block.arc_*`），已完成（M22f 终态）；方向暂停中（arc 复用弃，拟转 brush 子系统）。
