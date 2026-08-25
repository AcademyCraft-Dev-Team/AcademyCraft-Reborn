# 电弧/路径驱动子系统设计（M22，方向 Y）

> 状态：**已实现（M22f 终态，2026-08-16）**。本文档是事实源（含 M22f 改用旧 vfx 渲染决策）。
> **容器化适配**：实现按容器模型落地（`vfx.block.arc_*` 发射块 + `VfxSystemSimulator` 并行驱动 `ArcBuffer`），
> 取代本文 §4 的扁平 `ArcNodeFactory`/`VfxSimulator` 并行路径（M23–M28 容器化后旧扁平路径待移除）。
> **方向暂停（2026-08-16）**：用户判断 arc 代码复用改动太大不可行，拟转向「毛笔笔迹」（brush）子系统重设计；
> 本设计文档保留作决策记录，M22b/c/d/e 自研渲染方案全部废弃（详见 STATE.md 会话日志）。
> 前置阅读：`PROGRAM.md`（架构规则 R1–R5）、`MODULES.md`（MOD-08 粒子模型、MOD-11 arc）、`NODE_CATALOG.md`。

## 1. 背景与目标

把旧 VFX 框架的「电弧（Arc）」能力迁入 vfxgraph，服务以下需求：

| 需求 | 说明 |
| --- | --- |
| 两点路径 | 起点→终点的经典闪电螺栓（技能/瞬移/连锁） |
| 玩家身边环绕 | 绕玩家/相机的持续环境电弧（aura） |
| 模型环绕 | 绕任意模型 transform 的环（世界变换复用 `WorldTransform`，M15） |
| 分叉状电弧 | 主螺栓随机附着点派生子路径（树形拓扑） |
| 模型表面游走 | polyline 在模型网格表面随时间爬行（贴面约束） |

## 2. 核心决策（ADR-026 摘要）

1. **路径驱动，不是粒子 trail**：`ParticleBuffer.TRAIL_LENGTH = 8` 且 trail 是"衰减历史"语义，
   装不下 50~200 点螺栓、做不了持续环、贴不了面 → arc 用**独立的权威 polyline 路径缓冲**，不走粒子。
2. **方向 Y：约束 spine 在 CPU、观感在 GPU，无线程**：CPU 只生成宏观 spine
   （两点端点/环绕环/表面采样点/分支附着点 + 每点宽度），锯齿/噪声场/辉光全部由着色器承担 →
   把旧 arc 迫使单线程的两大成本（递归中点位移 + 每帧 Perlin 场）从 CPU 移除，CPU 量级降至 O(spine 点数)，
   **不再需要 `ArcExecutor` 后台线程**，同时消除旧方案的一帧发布延迟。
3. **渲染：M22f 起统一旧 vfx 管渲染（无 Ribbon fallback）**：
   - 原设计「Tube 主渲染 + Ribbon 廉价 fallback」：表面游走必须管状贴曲率、环绕任意视角可见、分叉管状更真、
     两点路径扁带即可作 fallback。
   - **M22f 决策（已批准）**：用户否定全部自研方案（M22b 线芯/纸带、M22c X 形高斯、M22d 胖管、M22e 链式光束）后，
     **改用旧 vfx 电弧渲染**——`drawArcs` 复用 `LightningMeshBuilder`（parallel transport ring 管网格）建管，
     无 ribbon fallback 分支。`RenderSpec` 按输出节点选几何（M21l 已支持）。

## 3. 三层流水线

> **M22f 渲染改用旧 vfx 电弧渲染（已批准）**：用户否定全部自研方案（M22b 线芯/纸带、M22c X 形高斯、M22d 胖管+三层高斯、
> M22e 链式光束×4 版：接缝/噪声/贴图感/颜色白太粗），明确「**改用旧 vfx 的电弧渲染**」。
> 定为复用 `org.academy.api.client.render.vfx.lightning.LightningMeshBuilder`（parallel transport ring 管网格，
> 同 `ArcTube`/`LightningRenderer`）+ `vfxgraph_arc` 着色器（**颜色 100% 图数据驱动、零代码常量**，UBO 仅渲染标量
> aces 开关 + 发射增强，不透明度 = 顶点 alpha）+ bloom。

```
① CPU 路径生成（宏观拓扑，同步每帧，20~50 点）
   - 两点：A→B spine（中点位移锯齿 + Laplacian 平滑）+ 分叉（1~2 层）+ strands 多股（每股独立种子）
   - 环绕：环/椭圆 spine（相位/半径噪声动画，strands 多股）
   - 表面游走：mesh 表面采样点 → 贴面 polyline（复用 M18 三角资产）
   → 输出：spine polyline（多股，每点宽度 = 管半径）+ 颜色 + seed/age
              ↓
② 管网格（复用 LightningMeshBuilder，同旧 ArcTube）
   - parallel transport right/up → 每 spine 点 ring（SEGMENT_RESOLUTION=4），半径 = poly.width(i) × thicknessVariation
   - 顶点 Position(3)+UV(2)+Color(4)，UV0.x = 发射强度，Color = 电弧色（数据驱动）
              ↓
③ 管 pass（vfxgraph_arc = 复刻 vfx_lightning 但颜色数据驱动，结构性）
   - ArcLightning UBO：仅 LightningParams（aces 开关 + 发射增强标量），零颜色常量
   - 主 pass 透明（LIGHTNING_TUBE 同款）；bloom pass additive（LIGHTNING_TUBE_BLOOM 同款）
   - 深度 GEQUAL 不写深度；无 hash / 无贴图
```

## 4. 模块与包

新增包 `org.academy.api.client.render.vfxgraph.arc`（模块 **MOD-11**，见 `MODULES.md`）：

| 子包/类 | 职责 |
| --- | --- |
| `ArcBuffer`/`Arc`/`Polyline` | 每帧活电弧集合（多股 spine polyline + 每点宽度 + 颜色 + seed/age/lifetime），由容器执行器老化 |
| `VfxBlocks` 注册 `vfx.block.arc_bolt/orbit/surface/output_arc` | arc 块语义（SPAWN 类发射块 + OUTPUT 输出块），经 `SimContext.arcs()` 写入 ArcBuffer |
| `path/BoltPath` | 两点 / 中点位移粗锯齿 / 分叉（1~2 层）/ **strands 多股** 生成器 |
| `path/OrbitPath` | 环绕（环/椭圆，相位 + 半径噪声动画，tilt 倾斜，strands 多股）生成器 |
| `path/SurfaceWalk` | 表面游走（三角形质心/法线/面积 + 最近质心邻居，无共享顶点鲁棒）生成器 |
| `LightningMeshBuilder`（旧 vfx 复用） | spine → parallel transport ring 管网格（Position+UV+Color，UV0.x=发射强度，Color=电弧色） |
| `RenderSpec.Geometry.ARC`/`VfxGraphRenderer.drawArcs` | 渲染（管 pass，透明主 / additive bloom，深度 GEQUAL，见 §6） |

## 5. 路径生成层设计

### 5.1 数据模型

```
ArcPath      = (源 id, List<Polyline> 多股主干+分支, 世界变换, 生命周期, 颜色)
Polyline     = 点列 + 每点宽度（宽度 = 管半径；可来自曲线参数）
```

- `WorldTransform`（M15 已有）在渲染端应用，arc 保持发射器局部坐标，技能/实体只设位置/朝向/缩放。
- 宽度 taper：每点宽度由宽度曲线（黑板 CURVE 参数，M12 已有）驱动，或路径源内建 taper；
  管半径 = poly.width（M22f 旧 vfx 渲染，细管 + bloom 辉光）。

### 5.2 路径源

| 路径源 | 属性（示例） | 说明 |
| --- | --- | --- |
| `arc_bolt`（两点） | `from/to`（或参数）、`segments`(32~64)、`jagged`、`strands`、`width`、`branch_count`/`branch_depth`、`flicker` | A→B spine + 中点位移锯齿（粗锯齿可 CPU 给 5~10 个大幅度拐点）；分叉随机附着点派生 1~2 层子路径；`strands` 多股并线（主闪电 + 副丝）；`flicker` 存活期反复改道 |
| `arc_orbit`（环绕） | `radius`、`speed`、`axis`(Y)、`segments`、`width`、`wobble` | 绕玩家/模型/指定中心，相位+半径噪声动画；`followEntity` 复用 M15 `spawnFollow` |
| `arc_surface`（表面游走） | `mesh`、`walk_speed`、`segments`、`width`、`seed` | 复用 M18 `MeshShape`（面积加权选三角形 + 重心坐标取表面点），沿表面游走的 polyline |

- 表面游走算法：预计算三角形邻居或随机重采样 → 在表面上取当前点 → 以噪声/随机游走推进 →
  每帧刷新 polyline 点集，形成"电流爬过表面"的效果。纯 CPU，20~50 点，不重。

### 5.3 为什么不搬 CPU 修饰器（JaggedModifier/NoiseFieldModifier）

- 旧 `JaggedModifier` = 递归 `2^subdivisions` 点 + 每帧 `new Random` + 大量 `ArrayList/Vector3f` 分配。
- 旧 `NoiseFieldModifier` = 每帧每点 3×`ImprovedNoise.noise()`（3D Perlin）。
- 方向 Y 把这两块全部移到顶点/片元着色器（GPU），CPU 只剩 §5.2 的约束 spine 生成。

## 6. 渲染层设计

### 6.1 几何

- **M22f**：`RenderSpec.Geometry.ARC`（改用旧 vfx 管网格，取代 M22b/c/d/e beam 系）。
  `RenderSpec.fromOutputNode` 由 `vfx.block.output_arc` 派生几何（无着色器枚举；扁平 `vfx.output_arc` 仅防御性兼容）。
- 管 pass：spine → parallel transport ring 管网格（复用 `LightningMeshBuilder`，SEGMENT_RESOLUTION=4，每点 R 顶点，TRIANGLES），
  半径 = poly.width(i) × thicknessVariation；顶点数 ≈ Σ polyline 点数 × 4，<0.1ms/条，无需后台线程。

### 6.2 着色器

- `vfxgraph_arc.vsh/.fsh`（管，TRIANGLES）：**颜色数据驱动、零代码常量**——`ArcLightning` UBO 仅 `LightningParams`
  （aces 开关 + 发射增强标量，M22f 起无任何颜色常量）；
  `color = max(vColor.rgb,1e-4) × (1 + 增强×强度)`，aces 可选色调映射，additive/glow → bloom；不透明度 = 顶点 alpha（vColor.a）。
- `ARC_TUBE_FORMAT`（Position+UV+Color，自建格式，非 POSITION_TEX）；`ARC_BIND_GROUP`（GraphCamera + ArcLightning）。
- 主 pass 透明（复刻 `LIGHTNING_TUBE`）、bloom pass additive（复刻 `LIGHTNING_TUBE_BLOOM`）。
- 着色器 id 由图数据指定：管 = 输出节点 `vertex`/`shader`（默认 `vfxgraph_arc`），零 Java 枚举（M21l）。

### 6.3 深度

- 半透明 additive：不写深度、GEQUAL 深度测试（与现有 billboard 一致）；英雄级可不透明写深度（可选）。

## 7. 节点目录规划（并入 `NODE_CATALOG.md`）

| 节点 | id | 属性 | 状态 |
| --- | --- | --- | --- |
| Arc Bolt（两点） | `vfx.block.arc_bolt`（规划 `vfx.arc_bolt`） | from/to、segments、jagged、**strands**、width（管半径）、branch_count/depth、lifetime、interval、**flicker**、color | ✅ |
| Arc Orbit（环绕） | `vfx.block.arc_orbit`（规划 `vfx.arc_orbit`） | radius、speed、segments、**strands**、width、wobble、tilt、lifetime、color | ✅ |
| Arc Surface（表面游走） | `vfx.block.arc_surface`（规划 `vfx.arc_surface`） | mesh、walk_speed、segments、**strands**、width、scale、lifetime、color | ✅ |
| Output Arc | `vfx.block.output_arc`（规划 `vfx.output_arc`） | vertex/shader/blend/layer + ARC 观感属性（sparks/spark_speed/size/period/travel/length/radius/curve/wobble/thickness/emission，M22h） | ✅ |

> VFX 块目录 42 → **46**（+4，与粒子系共存）。容器化后为块（见 §4 容器化适配），规划 id 保留作别名说明。

## 8. 与旧 arc 的关系

- **M22f 渲染直接复用旧 vfx**：`LightningMeshBuilder`（ring 管网格）+ `vfxgraph_arc`（颜色数据驱动，观感与旧 `ArcTube`/`LightningRenderer` 一致）；**去掉异步 executor**（新 arc spine 同步生成）。
- **保留旧 arc 系统共存**（ADR-004「共存不迁移」原则）：SkyStrike/VectorRedirect/MagBlade 等现有效果不动。
- 技能侧：新 arc 作为图资产经 `VfxGraphManager.spawn`/`SpawnVfxGraphPacket`（M20）接入，取代手写时再择优登记。

## 9. 里程碑分解（M22，见 `TASK_LEDGER.md`）

| ID | 任务 | 状态 | 说明 |
| --- | --- | --- | --- |
| M22-01 | `ArcBuffer` + 数据模型 | done | polyline/宽度/颜色/seed/age/lifetime，headless 可测 |
| M22-02 | `BoltPath`（两点 + 中点位移 + 分叉） | done | 纯函数可测 |
| M22-03 | `OrbitPath` 路径源（玩家/模型环绕） | done | 相位/半径噪声动画 |
| M22-04 | `SurfaceWalk` 路径源（表面游走，复用 M18 三角资产） | done | 贴面 polyline |
| M22-05 | 自研 builder（M22b/c/d/e beam 系） | done | 全部废弃（M22f 改用旧 vfx） |
| M22-06 | `RenderSpec.Geometry.ARC` + `VfxGraphRenderer.drawArcs`（M22f） | done | 复用 `LightningMeshBuilder` + `arcTubePipeline(bloomPass)`；深度 GEQUAL |
| M22-07 | `vfxgraph_arc.vsh/.fsh`（颜色数据驱动、零代码常量，UBO 仅渲染标量） | done | glslangValidator 通过 |
| M22-08 | arc 块注册（`arc_bolt/orbit/surface/output_arc`，块目录 42→46） | done | NODE_CATALOG/MODULES 更新 |
| M22-09 | 容器执行器/预览并行驱动 arc 缓冲 | done | 编辑器可视（容器 `VfxPreview`） |
| M22-10 | 游戏内冒烟：spawn 一个 arc 图资产 | partial | `demo_arc.json`（仅 bolt）+ SampleAssetsTest 已接入；游戏内肉眼确认待显示环境；surface 演示资产未做 |
| M22c-01~04 | X 形同心高斯光带（M22c） | done | **被用户否决** |
| M22d-01~05 | 胖辉光管 + 三层高斯（M22d） | done | **被用户否决** |
| M22e-01~05 | 链式光束（M22e v3.0~v3.3） | done | **被用户否决**（接缝/噪声/贴图感/颜色白太粗） |
| M22f-01 | `drawArcs` 复用 `LightningMeshBuilder` 建管（parallel transport right/up，半径=poly.width） | done | 同 ArcTube；`ARC_SEGMENT_RESOLUTION=4` |
| M22f-02 | `vfxgraph_arc`（颜色数据驱动）+ `ArcLightning` UBO（仅标量）+ `ARC_TUBE_FORMAT`(Position+UV+Color) + `ARC_BIND_GROUP` | done | 零颜色常量；glslangValidator 通过 |
| M22f-03 | `arcTubePipeline(bloomPass)`（透明主 / additive bloom）+ `render` 传 bloomPass | done | 复刻 LIGHTNING_TUBE / LIGHTNING_TUBE_BLOOM |
| M22f-04 | 删 beam 全套（贴图/ribbon/builder/测试） | done | demo_arc 细管（bolt 0.005，无 orbit）；主 test 817→807 |
| M22f-05 | 编辑器肉眼确认（蓝色细管 + bloom 辉光） | pending | 需显示环境（并入 brush 决策） |
| M22g-01 | 飞出火花改迷你电弧 tube（`buildSparkBolt` + `sparkTubePipeline` 恒 additive） | done | 弃 billboard 四边形火花 |
| M22g-02 | 火花观感修正（纯电弧色/砍半半径/加长） | done | 用户反馈迭代 |
| M22g-03 | 火花轨迹变化（抛物线弯曲 + 行波波动） | done | 用户反馈迭代 |
| M22h-01 | ARC 观感参数数据驱动（`RenderSpec.ArcRender` + output_arc 属性） | done | 渲染器零硬编码常量 |
| M22i-01 | 火花波动自然化（`u` 平滑渐变 + 稳定扇形爆发） | done | 用户反馈「太随机」 |

> 建议拓扑序：M22-01 → 02 → 03 → 04 → 06(M22f) → 07 → 08 → 09 → 10。

## 10. 性能预算与风险

- **CPU/帧**：~O(spine 点数)（每 arc 20~50 点 + 管网格生成），多 arc 也不回落到旧 arc 的线程/延迟问题。
- **GPU**：fill-rate 受 additive/glow 片元成本主导（与 fire 同级）；管三角数 = Σ polyline 点数 × 4 × 2。
- **风险（均已解决）**：
  - 表面游走贴面精度依赖 M18 `MeshShape` 的三角形数据结构 → 已用 `SurfaceWalk` 最近质心三角形邻居解决（无共享顶点鲁棒）。
  - 与现有 `vfx.output_*` 几何派生 `switch` 兼容 → `output_arc` 走 `Geometry.ARC` 新分支，不破坏 M21l 数据驱动约定。

## 11. 待定

- **无**（原三项待定均已解决：管半径/颜色 → 零代码常量、颜色数据驱动；分叉层数 → BoltPath depth 1~2 上限、branchCount ≤6；表面游走邻接表 → SurfaceWalk 最近质心邻居）。
- 方向暂停（2026-08-16）：arc 复用弃，拟转「毛笔笔迹」（brush）子系统重设计。
