# 图形化 Shader / VFX 编辑器用户手册

> Phase 2 产物：类 Unity 的图形化 Shader Graph + VFX Graph 编辑器，以及游戏内 VFX 图运行时。
> 关联文档：`docs/vfx-graph/EDITOR_ROADMAP.md`（路线图）、`NODE_CATALOG.md`（节点清单）。

## 1. 概览

AcademyCraft 的图系统由两半构成：

- **编辑器（桌面工具）**：独立运行的 ImGui 应用，可建图 / 连线 / 调参 / 实时预览，存读 JSON 资产。
- **运行时（游戏内）**：把图资产加载进游戏，可被技能 / 实体 spawn，支持参数绑定与热重载。

两者共享同一套核心：`graph`（模型/编译/序列化）、`shader`（GLSL 代码生成 + 管线）、`vfxgraph`（CPU 粒子模拟 + 自持 GPU 渲染）。

## 2. 启动编辑器

需要显示环境。项目根目录执行：

```bash
./gradlew graphEditor
```

编辑器布局（可停靠，布局持久化到 `imgui-graph.ini`）：

- 中央 **Canvas**：节点图编辑区（平移/缩放、框选、连线）。
- **Node Palette**：搜索/分类添加节点（支持 Shader 与 VFX 两套目录）。
- **Property Inspector**：选中节点属性 / 黑板参数编辑。
- **Shader Preview / VFX Viewport**：实时预览（全屏 quad 或独立 docked 视口）。
- **Project Browser**：图资产树，双击打开。

## 3. 基本操作

| 操作 | 方式 |
| --- | --- |
| 平移画布 | 画布空白处拖拽 / 鼠标中键 |
| 缩放画布 | 滚轮 |
| 框选 | 空白处左键拖拽 |
| 添加节点 | 右键空白处 → Add Node，或 Palette 拖入 |
| 连线 | 从输出端口拖到输入端口（类型不兼容即时反馈） |
| 断边 | 右键边 → Delete |
| 删除节点 | 选中后 Del |
| 撤销 / 重做 | Ctrl+Z / Ctrl+Y |
| 复制 / 粘贴 / 复制(D) | Ctrl+C / Ctrl+V / Ctrl+D |
| 对齐 / 分布 | 菜单 Arrange 或快捷键 |
| 命令面板 | Ctrl+P（搜索节点 / 命令） |

所有编辑操作可撤销。

## 4. 黑板参数（Graph Parameters）

图对外暴露的可调参数。类型支持：FLOAT / INT / BOOL / VEC2 / VEC3 / VEC4 / COLOR / SAMPLER / CURVE / GRADIENT。

- **分组**：参数可在 Inspector 中分组（存 sidecar `<name>.editor.json`，不改核心资产）。
- **范围**：FLOAT 参数可设 min/max。
- **曲线 / 渐变**：CURVE / GRADIENT 参数有可视化编辑器（bezier 拖点 / 色带停靠点）。

## 5. Shader 图

- 输出节点：`output.color`（片段颜色）。
- 纹理采样：`texture.sample` 按 `texture` 属性（如 `minecraft:textures/block/stone.png`）加载真实资产纹理，
  多样本按图自动分配 `Sampler0..N-1`（ADR-021）；未指定/加载失败的槽位预览显示品红兜底。
- 曲线 / 渐变采样：`curve.sample` / `gradient.sample` 引用黑板 CURVE / GRADIENT 参数。
- 自定义函数：`output.custom`（body 属性内联 GLSL）。
- 子图：`subgraph` 节点引用其它图资产，编译期内联展开。**多文档标签页**：画布顶部 TabBar 切换/关闭文档，
  每文档独立撤销栈；**双击 `subgraph` 节点**（或右键 → Open Sub Graph）把子图资产打开为新文档编辑
  （子图 id = 文档名，如 `assets/.../graphs/<名字>.json`）。

**注意**：编辑器预览的坐标/几何节点（world/object position、normal、view dir 等）为全屏 quad 近似，
真实顶点属性在游戏运行时阶段仍未完整支持。

## 6. VFX 图

VFX 图为 **Unity 式容器模型（Context + 数据流，ADR-027，M23–M28）**：

- **Context（阶段容器）**：`SPAWN`（发射）→ `INITIALIZE`（初始化新粒子）→ `UPDATE`（逐帧更新）→ `OUTPUT`（渲染输出）。
  Context 经 **flow 边**连接，每帧按 SPAWN→INITIALIZE→UPDATE 阶段驱动（拓扑序）。
- **批次（flow 语义）**：spawn 块批量生成后经 `emitBatch` 记录本帧批次，flow 边把上游 spawn 的批次注入下游 init 块——
  init 只处理本帧新粒子，彻底替代旧的 `spawnStart` 单点隐式耦合。
- **块（`vfx.block.*`，46 块）**：spawn（rate/burst/periodic/distance + arc 系）、init（position/velocity/color/size/rotation/lifetime/mass/randomize）、
  力场（gravity/force/noise/turbulence/vortex/drag/damping）、collision（plane/sphere/ground）+ kill/bounds、
  over-life（color/size/alpha/velocity）、orient（face_camera/velocity/fixed/spin）、输出变体（point/quad/mesh/line/ribbon/arc）。
- **算子（`vfx.op.*`，23 个）**：attr-read×11、constant、param_float/vec3/color/curve/gradient、add/sub/mul/div、curve/gradient——
  自由算子经 **data edge** 把值连到块端口（逐粒子求值），替代旧"属性即数据"的扁平模型。
- 存活参数经算子 `vfx.op.param_*` 读取（不重建模拟器，游戏值可连续绑定）。

发射形状（9 个）：`point`/`sphere`/`box`/`cone`/`cylinder`/`torus`/`circle_edge`/`disc`/`mesh`。
`shape=mesh` 用属性 `mesh`（OBJ 资产 id）从 `MeshAssets` 取三角形（`ObjMeshParser` 解析，面积加权表面采样），
`mesh_scale` 控制缩放；未注册资产时回退单位立方体（ADR-023）。

## 7. 资产格式

VFX 图资产为容器 schema JSON（`JsonVfxGraphCodec` 编解码；`kind:"vfx"`，schema 版本化）：

```json
{
  "version": 1,
  "kind": "vfx",
  "id": "demo_burst",
  "parameters": [],
  "contexts": [
    {
      "id": "ctx0",
      "type": "SPAWN",
      "name": "Spawn",
      "blocks": [
        { "id": "spawn", "type": "vfx.block.spawn_burst", "properties": { "count": "80" } }
      ]
    }
  ],
  "flow": [ { "from": "ctx0", "to": "ctx1" } ],
  "operators": [],
  "dataEdges": [],
  "blockFlows": [],
  "outputs": ["out"]
}
```

编辑器存读：图资产本体分两套——SHADER 图（扁平 schema）与 VFX 图（`kind:"vfx"` 容器 schema，编辑器按文件内容自动判定模式）；
核心 `<name>.json`（图语义）+ `<name>.editor.json`（编辑器元数据 sidecar：frames/notes/camera/布局）。

## 8. 游戏内运行时

### 深度遮挡（反向 Z）

VFX 图粒子**与场景正确深度遮挡**：与 Minecraft 主渲染一致使用**反向 Z**（近=1.0、远=0.0），
管线 `DepthStencilState(GREATER_THAN_OR_EQUAL, 不写深度)`——粒子被地形/方块/实体遮挡，不再穿墙；
半透明粒子彼此不写深度，避免互相遮挡。游戏内用主渲染目标深度缓冲叠加，编辑器视口也带深度。

### 资产位置

游戏内图资产放 `assets/academy/vfxgraph/*.json`，资源重载（F3+T）自动加载，变更即热重载。

### spawn 命令

游戏内使用客户端命令 spawn 图效果（调试）：

```
/academy vfx spawn <graph> <x> <y> <z>
/academy vfx spawn <graph>           # 在玩家视线位置 spawn
```

图名为 `vfxgraph/` 之后的文件名（如 `demo_burst`）。随包示例（7 个）：`demo_burst`（爆发）、`demo_fountain`（喷泉）、
`demo_ribbon`（轨迹带）、`minimal_burst`（最小示例）、`demo_fire`（多层无限火焰）、`demo_arc`（电弧/路径驱动，方向暂停中）、
`skill_dirstrike`（DirStrike 技能冲击波）。

### 开发热重载

dev 模式（`IS_DEV=true`，即 `./gradlew clientDev`）下，`run/vfxgraph/*.json` 目录被文件监听（WatchService），
变更自动重载并刷新使用中的效果。**同时** 打包进资源包的资产经 F3+T 资源重载刷新。

### 技能 / 实体 spawn API

```java
// spawn 到世界坐标
var fx = VfxGraphManager.INSTANCE.spawn(Identifier.fromNamespaceAndPath("academy", "vfxgraph/demo_burst"), new Vector3f(...));
// 跟随实体（实体移除即停）
var fx = VfxGraphManager.INSTANCE.spawnFollow(assetId, entity);
// 世界变换（位置/朝向/缩放）
fx.setPosition(...); fx.setRotation(...); fx.setScale(...);
// 参数绑定（每帧采样，不重建）
fx.bind("strength", () -> Value.of(skillStrength));
// 停止
fx.stop();
```

### 技能接线（A4）

服务端可用通用包 `SpawnVfxGraphPacket.broadcast(level, assetId, position[, followEntityId, scale, params])`
把图效果广播给附近客户端（图资产缺失时客户端静默忽略）。示例：

```java
SpawnVfxGraphPacket.broadcast(level,
        org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager.DIR_STRIKE_ASSET,
        player.position(), -1, 1f,
        Map.of("size", 0.35f));
```

DirStrike（加速系 Lv2）已替换为图资产驱动（原手写 `DirStrikeGroundEffect` 移除）；`skill_dirstrike.json`
用 `param` 驱动粒子尺寸，爆发半径经 packet 的 `scale` 控制。其他技能接入同模式即可。

## 9. 性能手测（有显示环境）

1. `./gradlew clientDev` 启动。
2. `/academy vfx spawn demo_burst` 观察粒子。
3. 调整视口统计 overlay（FPS / 帧耗时 / 粒子数）与质量档位（分辨率缩放）。

headless 自动门禁：10k 粒子稳态模拟帧耗时与 ParticleBuffer spawn/kill 压力已纳入 `test`（见
`VfxSimulatorPerfTest` / `ParticleBufferPerfTest` / `VfxSystemSimulatorPerfTest`，宽松预算防 CI 抖动）。

## 10. Iris / Sodium 兼容

图系统的自持渲染（`VfxGraphRenderer`）与 Iris shader pack 共存：渲染钩子排到 `iris$endLevelRender` 之后，
并整体包进 `IrisCompat.runWithBypass`（与现有 VFX 系统同款），shader pack 生效时不冲突。
Iris / Sodium 为可选依赖（未声明 mod 硬依赖）。

## 11. 测试与门禁

```bash
./gradlew test editorTest check          # 全部单测 + 门禁
./gradlew test -Dgolden.update=true      # GLSL 黄金快照更新模式
tools/check_abstraction.sh               # 抽象层黑名单扫描
tools/check_package_info.sh              # package-info @NullMarked 检查
```

## 12. 已知限制

- 多样本纹理绑定的**游戏内**材质消费路径（`GraphMaterial` → 真实绑定）待后续；编辑器预览已接真实资产。
- 嵌套子图（`SubGraphFlattener` 仅一层展开）待后续。
- `vfx.output_mesh` 渲染仍为单位立方体（OBJ 资产 → GPU 顶点缓冲未接）；mesh 发射形状采样已就绪。
- 仅 DirStrike 一个技能接线图 VFX（其余技能仍走旧系统）。
- Shader 图顶点阶段输出未支持（固定全屏 quad）。
- 编辑器与游戏内渲染正确性（深度/混合/Iris 共存）需有显示环境手动冒烟。
