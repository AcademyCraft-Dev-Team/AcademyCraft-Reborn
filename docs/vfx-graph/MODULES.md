# 模块清单与接口契约

每个模块一节，含：职责、**公开接口契约**（接口/类型，冻结于实现前）、依赖、状态、TODO。
状态值：`planned` / `contracted`(契约已冻结) / `in-progress` / `done`。

---

## MOD-01 graph.type — 值类型系统

- **包**：`org.academy.api.client.render.graph.type`
- **职责**：定义端口/参数的值类型、值表示、隐式转换规则。
- **接口契约**：
  - `ValueType`（枚举）：`FLOAT VEC2 VEC3 VEC4 COLOR BOOL INT SAMPLER TIME CURVE GRADIENT MESH`
  - `Value`（不可变值持有者，按 `ValueType` 区分载体）
  - `TypeConverter`：`canConvert(from,to)` / `convert(Value,to)`
  - `Curve`（Keyframe 含切线/插值，ADR-017）、`Gradient`、`CurveSampler`/`GradientSampler`
- **依赖**：无
- **状态**：`done`
- **TODO**：M13 over-life 曲线消费（VFX）

## MOD-02 graph.model — 图数据模型

- **包**：`org.academy.api.client.render.graph.model`
- **职责**：节点、端口、边、图、黑板参数的不可变模型。
- **接口契约**：
  - `PortDirection`（枚举）：`INPUT OUTPUT`
  - `Port`：`id() name() type() direction() defaultValue()`
  - `Edge`：`from() to()`
  - `GraphNode`：`id() type() properties() inputs() outputs() position()`
  - `Graph`：`nodes() edges() parameters() outputs()`
  - `GraphParameter`：`id() name() type() defaultValue() min() max()`
- **依赖**：`graph.type`
- **状态**：`done`
- **TODO**：M1 已实现为 record（ADR-008）

## MOD-03 graph.registry — 节点目录

- **包**：`org.academy.api.client.render.graph.registry`
- **职责**：注册/查询 `NodeType`，向编辑器暴露节点元数据。
- **接口契约**：
  - `PortSpec`：`name() type() direction() defaultValue()`
  - `PropertySpec`：`name() type() defaultValue() min() max()`
  - `NodeType`：`id() category() displayName() ports() properties()`
  - `NodeRegistry`：`register(NodeType)` / `find(id)` / `all()`
- **依赖**：`graph.type`
- **状态**：`done`
- **TODO**：M1 已实现（record + `SimpleNodeRegistry`）；M3/M5 填充节点目录

## MOD-04 graph.serialize — 序列化

- **包**：`org.academy.api.client.render.graph.serialize`
- **职责**：图 ↔ JSON（Gson），schema 版本化与迁移。
- **接口契约**：
  - `GraphSchemaVersion`（常量/枚举）：当前版本号
  - `GraphCodec`：`encode(Graph): JsonObject` / `decode(JsonObject): Graph`
  - `GraphMigration`（接口）：`fromVersion()` / `apply(JsonObject)`
- **依赖**：`graph.model`
- **状态**：`done`
- **TODO**：M1 已实现 `JsonGraphCodec` + `GraphMigrations` 迁移链 + 往返测试

## MOD-05 graph.validate — 校验

- **包**：`org.academy.api.client.render.graph.validate`
- **职责**：类型检查、环检测、非法图诊断。
- **接口契约**：
  - `GraphIssue`：`severity() message() nodeId()`
  - `GraphValidator`：`validate(Graph): List<GraphIssue>`
- **依赖**：`graph.model`、`graph.registry`
- **状态**：`done`
- **TODO**：M1 已实现 `DefaultGraphValidator`（类型检查 + 环检测）

## MOD-06 graph.compile — 图编译

- **包**：`org.academy.api.client.render.graph.compile`
- **职责**：拓扑排序、DAG 执行计划、常量折叠。
- **接口契约**：
  - `CompiledGraph`：`execOrder() parameters()`
  - `GraphCompiler`：`compile(Graph): CompiledGraph`（非法图抛异常）
- **依赖**：`graph.model`、`graph.registry`、`graph.validate`
- **状态**：`done`
- **TODO**：M2 已实现 `DefaultGraphCompiler`（拓扑排序 + 死代码消除 + 常量折叠）、`NodeEvaluator`、`GraphCompileException`

## MOD-07 shader — Shader Graph

- **包**：`org.academy.api.client.render.shader`（`nodes`/`codegen`/`pipeline`）
- **职责**：GLSL 代码生成 + 动态管线 + 材质运行时。
- **接口契约**：
  - `codegen`：`Expr`、`GlslType`、`Swizzle`、`GlslNames`、`GlslWriter`、`GlslLiterals`、
    `GlslNodeGenerator`、`GlslNodeRegistry`、`GlslGenerator`（含 `SAMPLER_UNIFORM`）、`GlslProgram`
  - `nodes`：`ShaderNodes`（节点元数据 + codegen 目录，`registerAll(NodeRegistry, GlslNodeRegistry)`）
  - `pipeline`：`UniformLayout`（SAMPLER 参数跳过 std140）、`DynamicShaderSource`、`ShaderGraphPipeline`（含 SAMPLER0 bind group）、`ShaderGraphResult`、`GraphMaterial`
- **依赖**：`graph.*`
- **状态**：`done`（M3 + M11 + M16-05 黄金测试 + A1 多样本绑定）
- **TODO**：顶点输出（固定全屏 quad 阶段）

## MOD-08 vfxgraph — VFX Graph

- **包**：`org.academy.api.client.render.vfxgraph`（`sim`/`nodes`/`shape`/`render`/`runtime`）
- **职责**：CPU 粒子模拟 + 自持 GPU 渲染（不经现有 VFX 管理器/数据层，ADR-013）+ 游戏运行时接入（M15）。
- **接口契约**：
  - `sim`：`ParticleBuffer`（SoA + swap-remove + rotation/mass/trail）、`SimContext`（dt/time/random/spawnStart/curve/gradient/**liveParams**）、`SimNode`、`VfxSimulator`（穿参数 + `setLiveParam`）
  - `nodes`：`VfxNodeFactory`、`VfxNodeRegistry`、`VfxNodes`（spawn/init/update/collision/over-life/orient/output/param 目录，**旧扁平路径，M28 待移除**；容器化后为 `VfxBlocks`/`VfxOperators`）
  - `shape`：`EmitterShape` + Point/Sphere/Box/Cone/Cylinder/Torus/CircleEdge/**Disc**/**MeshShape**（OBJ 三角形 + 面积加权采样，A3）
  - `render`：`GraphCamera`（含 `fromGameCamera`）、`VfxGraphRenderer`（billboard/mesh/line/ribbon 4 拓扑 + `WorldTransform`）、`WorldTransform`
  - `runtime`：`VfxGraphManager`（单例，tick/renderFrame/spawn/reload + 渲染器池）、`ActiveEffect`、`EffectBudget`、`VfxGraphAssetLoader`、`GraphFileWatcher`
- **依赖**：`graph.*`、`shader/pipeline/DynamicShaderSource`
- **状态**：`done`（M5 + M6 + M13 + M15 + A3 MeshShape + M21 火焰 + 容器化 M23–M28 并行模型）
- **TODO**：M28 移除旧扁平路径（`VfxNodes`/`VfxSimulator`）；形状 9/9 全完成

## MOD-09 editor — 桌面编辑器

- **包**：`org.academy.desktop.grapheditor`（`canvas`/`palette`/`inspector`/`preview`/`app`/`command`/`clipboard`/`shortcut`/`commandpalette`/`document`/`project`/`dialog`/`editorcurve`/`gradient`/`viewport`）
- **职责**：ImGui 节点图编辑 UI + 独立 docked 视口（M14）+ 实时预览。
- **接口契约**：
  - `canvas`：`GraphEditorModel`（可变文档→核心 `Graph`，M9 命令化 + M10 frames/notes）、`Camera2D`（取景/吸附）、`NodeCanvas`、`AlignOps`、`Minimap`
  - `command`：`Command`/`CompositeCommand`/`UndoManager` + 模型命令 + frame/note 命令（M9/M10）
  - `clipboard`：`GraphClipboard`/`PasteNodesCommand`；`shortcut`：`ShortcutRegistry`；`commandpalette`：`CommandPalette`
  - `document`：`EditorMetadata`/`EditorMetadataCodec`（sidecar，ADR-015/018）；`project`：`ProjectBrowser`/`RecentFiles`；`dialog`：`PromptDialog`/`NoteEditDialog`
  - `editorcurve`：`CurveEditor`（M12-03）；`gradient`：`GradientEditor`（M12-04）
  - `viewport`：`ViewportPanel`（离屏视口 + gizmo/网格/统计，M14）、`OrbitCamera`（M14-02）
  - `app`：`GraphEditorApp`（implements `EditorApp`，`usesImGui = true`，docking 布局）
  - 前置：`ImGuiBackend`（`org.academy.internal.client.gui.imgui`，游戏/桌面共享；多纹理 ID 经 `DesktopEnvironment.imguiBackend` 注入）
- **依赖**：`graph.*`、`shader.*`、`ImGuiBackend`、`DesktopApplication`
- **状态**：`done`（M4 + M9 + M10 + M12 + M14 + A2 多文档标签页）
- **TODO**：运行冒烟 `./gradlew graphEditor`（C）

## MOD-10 graph.subgraph — 子图

- **包**：`org.academy.api.client.render.graph.subgraph`
- **职责**：子图资产注册与编译期内联展开（`subgraph` 节点 → 参数输入端口 `in<i>` / 输出端口 `out`）。
- **接口契约**：
  - `SubGraphRegistry`：`register(id, Graph)` / `find(id)`
  - `SubGraphFlattener`：`flatten(Graph, SubGraphRegistry): Graph`（ID 重映射，覆盖参数接父源，未覆盖参数提升）
- **依赖**：`graph.model`
- **状态**：`done`（M12-05 + A2 编辑器标签页接线）
- **TODO**：嵌套子图

## MOD-11 vfxgraph.arc — 电弧/路径驱动子系统

- **包**：`org.academy.api.client.render.vfxgraph.arc`（ADR-026，详见 `ARC_DESIGN.md`）
- **职责**：路径驱动电弧——CPU 产约束 spine（两点/环绕/表面布点/接触闪电/粒子火花 + 每点宽度），
  **渲染改用旧 vfx 电弧渲染**（M22f）：`VfxGraphRenderer.drawArcs` 复用 `LightningMeshBuilder`
  （parallel transport ring 管网格，同 `ArcTube`/`LightningRenderer`）+ `vfxgraph_arc`（**颜色 100% 图数据驱动、
  零代码常量**，UBO 仅渲染标量 aces 开关 + 发射增强，不透明度 = 顶点 alpha），**无线程**
  （对比旧 `ArcTube` 后台线程 + 一帧延迟）。
  M22 按容器模型实现（`vfx.block.arc_*` 发射块 + `vfx.block.output_arc` 输出块，经 `VfxSystemSimulator`
  并行驱动 `ArcBuffer`；`SimContext.arcs()` 暴露给块）。
- **接口契约（M22 容器化 spine + M22f 旧 vfx 渲染 + M22h 观感数据驱动 + M29 Blender 复刻）**：
  - `ArcBuffer`/`ArcCurve`：活电弧集合（spine 点列 + 每点宽度 + 颜色 + seed/age/lifetime + 可选表面）
  - `CurveGenerator`：`generateFromTo`（两点闪电 + 递归分支）/`generateSurfaceArc`（per-point 短弧 Bezier 起拱）
  - `NoiseAnimator`（低频 value noise + 域扭曲）/`SurfaceConstraint`（端点真最近表面点吸附）/`SurfaceDistributor`（面积加权布点）/
    `MeshDistance`（点到网格最近距离 + 最近点，Ericson）/`SparkGenerator`
  - 渲染复用：`org.academy.api.client.render.vfx.lightning.LightningMeshBuilder`（ring 管网格）+ `vfxgraph_arc`
  - 块目录：`VfxBlocks` 注册 `vfx.block.arc_bolt/orbit/surface/contact/spark/output_arc`（SPAWN 类发射块 + OUTPUT 输出块）；
    `output_arc` 含 ARC 观感属性（sparks/spark_speed/size/period/travel/length/radius/curve/wobble/thickness/emission）→ `RenderSpec.ArcRender`
- **依赖**：`graph.*`、`vfxgraph.render`（`RenderSpec.Geometry.ARC` + `VfxGraphRenderer.drawArcs` +
  `vfxgraph_arc.vsh/.fsh`）、`vfx.lightning`（`LightningMeshBuilder`）、`vfxgraph.shape`（`MeshAssets` 表面）
- **状态**：`done`（M22 + M22f/g/h/i + **M29 表面电弧/接触闪电/粒子火花**：`arc_surface` 重写 +
  `arc_contact`/`arc_spark` 新增，VFX 目录 46→48；`SurfaceConstraint` 接入 `VfxSystemSimulator.step`）
- **TODO**：游戏内冒烟（需显示环境）；MC 方块/玩家模型 → 通用三角面转换器（ARC_SURFACE.md 目标）

## MOD-12 vfxgraph.model — VFX 容器图数据模型

- **包**：`org.academy.api.client.render.vfxgraph.model`
- **职责**：Unity 式容器资产模型（M23，ADR-027）——`VfxSystem`（顶层）+ `VfxContext`
  （SPAWN/INITIALIZE/UPDATE/OUTPUT 容器）+ `VfxBlock`（context 内块）+ `VfxOperatorNode`（自由算子）+
  `VfxFlowEdge`（context 间批次 flow）+ `VfxDataEdge`（算子→块数据流）+ `ParticleAttribute`（粒子属性）。
  与核心 `Graph` 并行，不破坏其契约冻结；旧扁平 VFX schema 彻底废弃。
- **接口契约（冻结）**：
  - `VfxSystem`：`id() contexts() operators() flowEdges() dataEdges() parameters() outputs()` + `nodes()/findNode/findContext`
  - `VfxContext`：`id() type() name() blocks() x() y()` + `displayName()`；`VfxContextType`（SPAWN/INITIALIZE/UPDATE/OUTPUT）
  - `VfxBlock`：`id() type() properties() ports()`（实现 `VfxNode`）
  - `VfxOperatorNode`：`id() type() properties() ports() x() y()`（实现 `VfxNode`）
  - `VfxNode`：公共视图接口（块/算子统一引用）
  - `VfxFlowEdge(fromContextId,toContextId)`、`VfxBlockFlowEdge(fromBlockId,toBlockId)`（M28b 块级批次配对）、`VfxDataEdge(from:Edge.PortRef,to:Edge.PortRef)`
  - `ParticleAttribute`：`valueType() channels()`（POSITION/VELOCITY/SIZE/COLOR/ALPHA/AGE/LIFETIME/ROTATION/MASS/SEED/LAYER）
- **依赖**：`graph.model`、`graph.registry`、`graph.type`
- **状态**：`done`（M23）
- **TODO**：M24 执行器、M25 算子集、M27 全节点迁移

## MOD-13 vfxgraph.container — 容器执行器与数据流求值

- **包**：`org.academy.api.client.render.vfxgraph.sim`（执行器 M24）+ `.../vfxgraph/nodes`（块目录）+ `.../vfxgraph/operator`（算子 M25）
- **职责**：按 Context 阶段 + flow 边批次驱动 `ParticleBuffer`；算子数据流求值
  （attr-read 逐粒子 + 数学/曲线/渐变/参数）。
- **接口契约**：
  - `SpawnBatch(start,end)`：本帧新 spawn 批次（左闭右开）
  - `SimContext.emitBatch/clearEmittedBatches/emittedBatches/setIncomingBatches/incomingBatches/hasIncoming/forEachIncoming`
    （批次替代旧 `spawnStart` 单点耦合；`spawnStart` 保留兼容 `VfxSimulator`，M28 随旧扁平路径移除）
  - `VfxBlockFactory`/`VfxBlockRegistry`：块类型 → `SimNode` 工厂（容器块目录）
  - `VfxBlocks`：容器块目录（M24 最小集；M27 全量粒子 42 块 + M22 后 arc 4 块 = **46 块**）
  - `VfxSystemSimulator`：`step(float dt)`，SPAWN→INITIALIZE→UPDATE 阶段驱动 + flow 批次注入 + 数据边算子 DAG + 块级批次 flow（M28b：精确配对模式）
  - `PortValueSource`：块输入端口值源（`eval(portId, particleIndex, buffer, ctx)`，无绑定返回 null）
  - `VfxOperator`/`VfxOperatorFactory`/`VfxOperatorRegistry`：算子求值器 + 目录
  - `VfxOperators`：算子集（attr-read×ParticleAttribute、constant、param_float/vec3/color/curve/gradient、add/sub/mul/div、curve、gradient，**23 算子**）
  - `OperatorContext`：`(buffer, particleIndex, simContext)`，particleIndex=-1 表示非粒子上下文
- **依赖**：`vfxgraph.model`、`vfxgraph.sim`（ParticleBuffer）、`graph.type`
- **状态**：`done`（M24 执行器 + M25 算子数据流 + M27 全量 46 块迁移 + M28b 块级批次 flow）
- **TODO**：M28 运行时彻底移除旧扁平路径

## MOD-14 vfxgraph.operator — 数据流算子

- **包**：`org.academy.api.client.render.vfxgraph.operator`
- **职责**：VFX 数据流算子（M25）——attr-read 逐粒子读取粒子属性、常量/参数/数学/曲线/渐变算子，
  输出端口经 `VfxDataEdge` 驱动块/算子的输入端口。
- **接口契约（冻结）**：
  - `VfxOperator`：`Value eval(OperatorContext)`；`OperatorContext(buffer, particleIndex, simContext)`
    （particleIndex=-1 = 非粒子上下文，attr-read 返回默认值）
  - `VfxOperatorFactory`：`create(VfxOperatorNode, Map<String,VfxOperator> inputs)`（输入端口求值器）
  - `VfxOperatorRegistry`：typeId → factory
  - `VfxOperators.registerAll(metadata, ops)`：算子目录
- **节点集**：`vfx.op.attr_{position,velocity,size,color,alpha,age,lifetime,rotation,mass,seed,layer}`、
  `vfx.op.constant`、`vfx.op.param_{float,vec3,color,curve,gradient}`、`vfx.op.{add,sub,mul,div}`、`vfx.op.curve`、`vfx.op.gradient`
- **依赖**：`vfxgraph.model`（VfxOperatorNode）、`vfxgraph.sim`（ParticleBuffer/SimContext）、`graph.type`
- **状态**：`done`（M25 + M27 param_curve/gradient 补全）
- **TODO**：M28 随运行时收尾

## MOD-15 vfxgraph.container — 容器编辑器

- **包**：`src/editor/kotlin/org/academy/desktop/grapheditor/container`（编辑器源集）
- **职责**：VFX 容器图编辑（M26）——`VfxContainerModel`（contexts/blocks/operators/flow/data 边编辑态 +
  命令 undo/redo + `toSystem()`/`load()` 桥）+ `VfxContainerCanvas`（ImGui 容器画布：context 框内 block
  垂直排列、算子自由放置、flow/data 贝塞尔连线、拖拽/连线/框选/右键）+ `VfxContainerModelRef`（多文档切换）。
  与扁平 `GraphEditorModel`/`NodeCanvas` 平行（SHADER 用扁平，VFX 用容器）。
- **接口契约（冻结）**：
  - `VfxContainerModel`：`addContext/addBlock/addOperator/removeX/connectFlow/connectData/disconnectX/setProperty/moveX/setOutput`
    （全部经命令，`undo/redo`）；`toSystem(): VfxSystem` / `load(VfxSystem)`；`portsFor/firstInputPort/firstOutputPort`
  - 命令：`Add/Remove{Context,Block,Operator}Command`、`Connect/Disconnect{Flow,Data}Command`、
    `SetContainerPropertyCommand`（mergeKey 合并）、`Move{Context,Operator}Command`、`SetOutputCommand`
  - `VfxContainerCanvas`：`render()` + `frameAll()`；`ContextRequest(CANVAS/NODE/BLOCK/CONTEXT)`
- **依赖**：`vfxgraph.model`、`vfxgraph.nodes`、`vfxgraph.operator`、编辑器 `GraphEditorModelRef`/`Camera2D`
- **状态**：`done`（M26：模型 + 画布 + 接线；多文档每文档容器模型）
- **TODO**：M27 全量块迁移后画布块增删菜单完善

## 契约冻结规则

- 契约（接口与数据类型）一旦进入 `contracted`，**只允许追加方法，不允许破坏性修改**。
- 破坏性变更须先在 `DECISIONS.md` 追加 ADR，并同步更新所有依赖模块的 `TODO`。
