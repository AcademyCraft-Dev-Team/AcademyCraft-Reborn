# 架构决策记录（ADR）

每条决策：背景 → 决策 → 理由 → 影响。编号唯一，变更需追加新 ADR 并在此登记，不得篡改旧记录。

---

## ADR-001 编辑器宿主：独立桌面编辑器优先

- **背景**：Shader/VFX Graph 需要图形化作者工具。项目已有 `src/editor` 的 `DesktopApplication`
  （独立 GLFW 窗口 + Blaze3D + ImGui）与 `ClasspathShaderSource`。
- **决策**：图编辑器运行在独立桌面编辑器（`src/editor/kotlin/org/academy/desktop/grapheditor/`），
  复用 `DesktopApplication` / `EditorApp`。
- **理由**：作者工具无需进游戏，桌面进程可自由控制设备创建（注入自定义 `ShaderSource`），
  迭代与调试成本低；与现有 `UiEditorApp`/`HudEditorApp` 架构一致。
- **影响**：图模型/序列化/编译核心必须放 `src/main`（运行时也要加载编译图资产），编辑器只放 UI 壳。
  游戏内编辑器留作后续可选，共享同一核心。

## ADR-002 Shader Graph：GLSL 代码生成 + 运行时动态管线

- **背景**：需要类 Unity Shader Graph 的节点图 → 着色器能力。
- **决策**：节点图编译为 GLSL 字符串，运行时组装 `RenderPipeline` 并通过
  `GpuDevice.precompilePipeline(pipeline, DynamicShaderSource)` 编译缓存。
- **理由**：Blaze3D 的着色器源语言即 GLSL（OpenGL/Vulkan 后端均经 `ShaderSource` 接收），
  `precompilePipeline` 会把产物写入 `pipelineCache`，渲染时按 `RenderPipeline` identity 命中缓存，
  全程无需 Mixin、无需绕过抽象层（见 PROGRAM.md R1/R2）。
- **影响**：需要 `DynamicShaderSource`、内容哈希的 `Identifier` 生成、按图缓存编译产物的生命周期管理。

## ADR-003 VFX 模拟：CPU 模拟 + GPU 渲染

- **背景**：Blaze3D 计算着色器能力有限，完全对齐 Unity VFX Graph（GPU compute）风险过高。
- **决策**：节点图编译为 CPU 端粒子模拟器（SoA 缓冲区），渲染走 GPU 实例化。
- **理由**：可控、可测、可调；实例化渲染可直接复用现有 `VfxPipelines` 模式。
- **影响**：`vfxgraph/sim` 模块承担模拟逻辑，`vfxgraph/bridge` 负责把模拟结果桥接为
  `VfxRenderData` 交给现有 `VfxManager`/`VfxPhase` 渲染。

## ADR-004 与现有 VFX 关系：共存并按效果择优

- **背景**：已有 8600 行手写 VFX（`org.academy.api.client.render.vfx` + `internal/.../vfx`）。
- **决策**：不迁移现有效果。图系统生成数据驱动的 `Vfx`（`GraphVfx implements Vfx`），
  复用 `VfxManager`/`VfxPhase`/`VfxRegistry`。每条效果在实现时做二选一并登记：
  手写（几何复杂/定制管线，如闪电弧、羽翼）vs 图资产（组合式粒子/后处理/需热迭代）。
- **理由**：迁移成本巨大且无收益；图系统先服务新效果与后处理，成熟后再考虑逐步替换。
- **影响**：`vfxgraph/bridge` 必须产出与现有渲染器同构的数据或走独立但同相位的渲染器；
  择优结论逐条记入 `TASK_LEDGER.md` 或本文件。

## ADR-005 优先级：共享节点图地基优先

- **背景**：80k 行分期交付，Shader/VFX 两套图共享节点模型、序列化、编译、编辑器画布。
- **决策**：先做 `graph/` 核心（M1-M2），再做 Shader（M3-M4），再做 VFX（M5-M6）。
- **理由**：避免两套图重复造轮子；接口契约先冻结，后续模块可并行。
- **影响**：`graph/` 的类型系统必须同时覆盖 shader（向量/标量/采样器）与 vfx（标量/曲线/渐变/网格）两类值域。

## ADR-006 硬约束：只走图形 API 无关抽象层

- **背景**：项目跨 OpenGL/Vulkan 后端，且需保持 Sodium/Iris 兼容。
- **决策**：全系统只使用 PROGRAM.md R1 白名单中的抽象接口；黑名单（后端类/原生调用）经
  `tools/check_abstraction.sh` 静态扫描把关。
- **理由**：脱离抽象层会在不同后端/兼容层下崩溃或失效。
- **影响**：动态管线用 `precompilePipeline` 而非 Mixin 注入默认 shader 源；渲染只用 `RenderPass`+`GpuBuffer`。

## ADR-007 序列化格式：Gson JSON + 版本号

- **背景**：需要人类可读、可 diff、可迁移的图资产格式；项目已有 `UiLayoutCodecs` 的 Gson 先例。
- **决策**：图资产为 JSON，`GraphCodec` 基于 Gson，schema 顶层带 `version` 字段，破坏性变更走迁移表。
- **理由**：与现有约定一致，可版本控制、可审查。
- **影响**：`serialize` 模块需维护迁移链与往返测试。

## ADR-008 数据契约细化为 record/sealed

- **背景**：M0 将数据契约（Port/Edge/Graph/GraphNode/GraphParameter/PortSpec/PropertySpec/NodeType/GraphIssue/Value）
  以接口呈现；M1 实现时评估实现策略。
- **决策**：数据型契约改为 `record`（`Value` 为 sealed interface + record 实现），行为型契约
  （TypeConverter/GraphCodec/GraphValidator/NodeRegistry/GraphCompiler/GraphMigration）保持接口。
- **理由**：record 提供结构相等，便于往返/校验测试断言；与代码库先例一致（`VfxRegistry.Registration` 等）；
  行为型保留接口以便 shader/vfx 领域模块注入不同实现（如不同 TypeConverter、自定义 codec）。
- **影响**：无依赖模块受影响（M1 为首次实现）；契约冻结规则解释为「数据契约的 record 形态即为冻结形态」。

## ADR-009 新增 STRING 值类型

- **背景**：M3 实现 Shader 参数引用节点（`input.param_*`）时，节点属性「parameter id」需要一种字符串载体，
  而 `ValueType` 无字符串类型。
- **决策**：`ValueType` 增加 `STRING`，`Value` 增加 `StringVal` 变体与 `asString()`；
  序列化编解码同步支持。`STRING` 不与数值族互转，不是 GLSL 类型（`GlslType` 抛异常）。
- **理由**：参数/采样器/网格引用、编辑器文本框等都需要通用字符串值；enum 增项是加性变更，无破坏。
- **影响**：`TypeConversions`/`GlslType`/`GlslLiterals` 对 STRING 显式抛异常；JsonGraphCodec 支持 STRING 往返。

## ADR-010 M3 Shader 图先做 fragment-only，texture 采样延后

- **背景**：texture.sample 节点需要 sampler 绑定（bind group 布局 + 纹理绑定），与参数 UBO 是两条管线装配路径。
- **决策**：M3 生成纯 fragment 着色器（全屏 quad 顶点模板），节点目录先覆盖 math/input/combine/split/噪声；
  `texture.sample` 与 sampler 绑定留到 M4（编辑器预览）实现。
- **理由**：优先打通「图→GLSL→动态管线→材质参数绑定」这条最高风险链路，纹理采样可独立追加。
- **影响**：`UniformLayout` 仅支持标量/向量/颜色参数；sampler 参数暂不受支持。

## ADR-011 编辑器 UI 用 ImGui，后端泛化到桌面

- **背景**：M4 节点图编辑器需要自由画布/贝塞尔连线/直接操作，且编辑器宿主是独立桌面进程（ADR-001）。
- **决策**：编辑器 UI 用 ImGui（imgui-java，已内置 ImPlot）。把 `ImGuiUtilInternal` 的 Blaze3D 渲染器
  抽成可复用 `ImGuiBackend`（参数化 window handle + 物理尺寸），游戏与桌面共享；`ImGuiUtilInternal`
  变为游戏侧薄适配器，`EditorApp` 增 `usesImGui`/`renderImGui`/`renderBackground` 钩子，`DesktopUiHost`
  在 widget 渲染后、surface blit 前跑 ImGui pass。
- **理由**：节点编辑器是 ImGui 的天命场景（draw list/贝塞尔/即时模式）；项目已有成熟 ImGui 后端，
  且渲染器纯 Blaze3D 抽象层（符合 R1）。自家 widget 框架是保留模式 + 布局模型，不适合自由画布。
- **影响**：`ShaderPreview` 以全窗口背景渲染编译后 shader，规避 ImGui 纹理绑定复杂度；游戏内 ImGui
  行为不变（委托共享后端）。

## ADR-012 VFX 是「有状态有序 passes」，不复用数据流编译

- **背景**：M5 实现 CPU 粒子模拟，需决定 VFX 图与共享图编译器（M2，数据流 DAG）的关系。
- **决策**：VFX 图是有状态的共享缓冲 mutation（spawn→update→… 顺序敏感），不是标量/向量数据流。
  `VfxSimulator` 接受**有序 `List<GraphNode>`** 驱动执行；节点目录仍注册到核心 `NodeRegistry`（编辑器元数据），
  但模拟语义由 `VfxNodeFactory` 按节点类型定义。M6 桥接层负责从图结构产出有序序列。
- **理由**：通用编译器的死代码消除/拓扑排序基于数据流边，对无数据流的副作用节点不适用；
  顺序敏感的粒子 passes 用显式顺序更直观、可测。
- **影响**：VFX 节点当前无端口；若 M6 需要编辑器连线表达顺序，可引入 dummy flow 端口成链。

## ADR-013 VFX 图自持渲染，不桥接现有 VFX 系统（取代 ADR-004 的桥接方案）

- **背景**：M6 原计划把 VFX 图桥接到现有 `Vfx`/`VfxManager`/`VfxRegistry`/`VfxRenderData` 数据与相位层。
- **决策**：**不桥接**。VFX 图自持渲染：`VfxGraphRenderer` 直接读 `ParticleBuffer`，用专用 billboard
  `RenderPipeline`（`GraphCamera` UBO：View+Projection）渲染，不依赖现有 VFX 数据桶/注册表/相位，
  也不复用 `VfxPipelines` 中的管线对象；仅复用抽象层工具（`RenderSystem`、`DynamicShaderSource` 等）。
- **理由**：图系统与手写 VFX 是两条独立渲染路径，桥接会引入耦合、被手写 VFX 重构牵连。
- **影响**：`vfxgraph/render` 替代原 `bridge` 包；M7 游戏内挂点须为自持渲染循环（不接 `VfxManager`）。
  ADR-004 的「复用 VfxManager/VfxPhase/VfxRegistry」部分作废，仅保留「共存不迁移」原则。

## ADR-014 编辑器撤销/重做用命令模式，拖拽经 mergeKey 合并

- **背景**：M4 遗留的「编辑器撤销/重做模型」待定。既有 `UiEditorDocument` 是快照式 undo
  （`ArrayDeque<WidgetNode>`，深度 50），但 M9 需支撑多选删除/对齐/粘贴等批量操作与拖拽连续性。
- **决策**：采用命令模式：`Command`（`execute`/`undo`/`label`/`mergeKey`/`mergeWith`）+
  `UndoManager`（栈深 100，相邻同 `mergeKey` 命令经 `mergeWith` 合并）。`GraphEditorModel`
  所有 mutation 一律经 `UndoManager` 提交；移动/属性拖拽逐帧提交可合并命令，一次撤销恢复整段拖拽。
  批量操作（多选删除/对齐/粘贴）用 `CompositeCommand` 包一层。`undo/redo` 触发 `markDirty` 驱动预览重编译。
- **理由**：命令模式可逆粒度精确、支持合并与批量组合，是后续复制粘贴/多选变换/分组的地基；
  快照式对整树复制代价高且难以合并拖拽。编辑器测试经新增 `editorTest` source set 落地。
- **影响**：`GraphEditorModel` 公开集合保持可读但所有变更必须经命令方法；
  `AddNodeCommand` 复用 node id 保证 redo 身份稳定；`editorTest` source set 接入 `check` 门禁。

## ADR-015 编辑器元数据走 sidecar，核心 Graph 与运行时零改动

- **背景**：M10 引入分组 frame / sticky note / 相机 / 面板布局等编辑器装饰与视图状态。核心
  `Graph` 记录（id/nodes/edges/parameters/outputs）契约冻结，且运行时编译/渲染/`GraphAssets` 完全无视这些编辑器概念。
- **决策**：编辑器元数据单独持久化为 sidecar `<name>.editor.json`（`document/EditorMetadataCodec`），
  核心 `<name>.json` 仍由 `JsonGraphCodec` 编解码，二者文件解耦。docking 布局另存 `imgui-graph.ini`
  （ImGui 内建机制）。
- **理由**：不破坏核心契约、不引入 schema 迁移；运行时资产（游戏内 `GraphAssets`）加载路径不受编辑器文件影响；
  编辑器可自由演进装饰层。
- **影响**：保存/加载逻辑由 `GraphEditorApp` 同时读写两个文件；`GraphEditorModel` 增 frames/notes
  集合（可撤销命令），但 `toGraph()` 不包含它们。

## ADR-016 Shader 纹理采样用固定单 sampler（Sampler0），SAMPLER 参数不进 std140

- **背景**：M11-01 引入 `texture.sample`。BindGroupLayout 是编译期固定的绑定集合，动态任意多样本
  需要可变绑定数，超出当前管线装配路径。
- **决策**：所有 `texture.sample` 节点采样固定 uniform `Sampler0`；`ShaderGraphPipeline` 增
  `SAMPLER0` bind group；`GlslGenerator` 固定声明 `uniform sampler2D Sampler0;`；`ShaderPreview`
  绑定预览贴图（当前为程序化棋盘格）。SAMPLER 黑板参数从 `UniformLayout`/UBO 中跳过。
- **理由**：单 sampler 打通「纹理 → 绑定 → 采样 → 预览」最小链路，无需可变绑定复杂度。
- **影响**：多样本/真实资产按纹理属性加载留待 **A1 多样本纹理绑定（ADR-021）**；`ImGuiBackend`
  增多纹理 ID 注册表（字体 ID 1、其余自 2）为预览/视口显示任意纹理铺路（M11-02）。

## ADR-021 Shader 多样本纹理绑定：按图采样槽位动态装配 + 真实资产加载（A1）

- **背景**：A1 把 ADR-016 的单 sampler 折衷升级为「多样本 + 真实资产」。`texture.sample` 的
  `texture` 属性此前是死属性，预览只绑程序化棋盘格；`GlslGenerator` 无条件声明 `Sampler0`。
- **决策**：
  1. **槽位规划**：`GlslGenerator.samplePlan(Graph)` 收集 SAMPLER 黑板参数与 `texture.sample` 节点
     `texture` 属性（空串也占位，绑兜底纹理），按声明序去重 → `Sampler0..SamplerN-1`（`SamplerBinding` record）。
  2. **生成**：`GlslGenContext.samplerName(id)` 解析纹理标识 → uniform 名；`assembleFragment`
     **只声明实际用到的 sampler**（无纹理零声明，去死 uniform）；`texture.sample` 生成器读属性查槽位。
  3. **管线**：`ShaderGraphPipeline` 按 `samplePlan` 数量动态 `withSampler("SamplerN")`（0 则省略
     sampler bind group）；`ShaderGraphResult` 布局（`UniformLayout.samplers()`）携带绑定清单。
  4. **预览**：`ShaderPreview` 注入 `DesktopEnvironment.loadTexture(Identifier)`，逐槽位加载真实
     资产纹理（失败绑 1x1 品红兜底），渲染逐 `bindTexture(uniformName, view, sampler)`；删除棋盘格。
- **理由**：BindGroupLayout 支持任意 `withSampler` 串联，管线按内容哈希预编译，槽位数可编译期固定
  （每图固定，非可变绑定数），即可支持任意多样本；真实资产加载复用编辑器侧 `DesktopTextures`。
- **影响**：`UniformLayout` 增 `samplers()`/构造重载；`GraphMaterial.samplerBindings()`；
  golden 快照 6 个中 5 个移除无条件 Sampler0 声明；`ShaderNodesCatalogTest` 断言改为「仅用则声明」。
  契约增量（`GlslGenContext` 增 default 方法）不改破坏性。

## ADR-022 编辑器多文档标签页：每文档独立模型 + 共享子图注册表（A2）

- **背景**：A2 需支持「双击 subgraph 节点打开/编辑子图资产」。原编辑器单文档：单 `GraphEditorModel`
  持有全部状态，subgraph 节点端口动态派生依赖的 `SubGraphRegistry` 从未注入。
- **决策**：
  1. **`GraphEditorModelRef`**：可变模型持有者；`NodeCanvas`/`NodePalette`/`PropertyInspector`/
     `ShaderPreview`/`VfxPreview`/`ViewportPanel` 构造改为接收 ref，内部 `get() = ref.model`，
     标签页切换只换模型指针，组件零重建（预览以 `model === lastModel && version==lastVersion` 判重编译）。
  2. **多文档**：`GraphEditorDocuments`（newDoc/openDoc/activate/close）+ `EditorDocument`
     （name/path/model/metadata/mode/camera 状态）。**每文档独立模型 → 独立 undo 栈**。
  3. **子图注册表**：文档集合每次变更重建共享 `SubGraphRegistry`（key=文档名去扩展名）并推给所有
     文档模型 + `ShaderPreview.subGraphs`；Canvas 窗口顶部 `BeginTabBar` 切换/关闭标签；
     **双击 subgraph 节点**或右键「Open Sub Graph」把子图资产打开为新文档。
- **理由**：ref 方案最小侵入（6 个消费类共 70+ 处 `model.` 引用零改动）；每文档独立模型天然隔离
   undo/redo；共享子图注册表让「打开的子图资产」即可被任意图引用并编译期内联。
- **影响**：`GraphMode` 从 `GraphEditorApp` 私有枚举提升为顶层（每文档独立）；Mode 菜单改为切换
  当前文档模式（不再重置画布）；保存/改名经 `refresh()` 重建子图注册表。

## ADR-023 MeshSurface 发射形状：OBJ 三角形 + 面积加权采样（A3）

- **背景**：A3 补 `MeshShape`。`vfx.output_mesh` 渲染硬编码单位立方体；`ValueType.MESH`/`MeshVal`
  只存路径字符串无消费者；`buildShape` 无 mesh 分支。
- **决策**：
  1. **解析**：`ObjMeshParser.parse(String)` 纯函数解析 `v`/`f`（1 基/负索引/`v/vt` 形态，多边形扇形
     三角化）→ 三角形顶点数组，headless 可测。
  2. **采样**：`MeshShape(ox,oy,oz,scale,triangles)` 预计算每三角形面积与累计权重，面积加权选三角形 +
     重心坐标取表面点；`unitCube()` 兜底（与 `vfx.output_mesh` 渲染一致）。
  3. **接线**：`MeshAssets` 静态注册表（id → 三角形）；`buildShape` 增 `case "mesh"`（属性 `mesh` +
     `mesh_scale`），未注册/空 id 回退单位立方体；spawn/init_position 节点属性补 `mesh`/`mesh_scale`。
- **理由**：OBJ 是纯文本可 headless 单测；面积加权保证表面采样均匀；网格资产缺失时回退立方体保证
  任何 `shape=mesh` 图不崩。形状是发射器属性而非节点类型，VFX 节点目录保持 45。
- **影响**：VFX 形状 7 → 8 全完成；`NODE_CATALOG` MeshSurface ✅；VFX 节点数（45）不变。

## ADR-017 Curve.Keyframe 增切线 + 插值模式

- **背景**：M12-03 bezier 曲线编辑器与 M13-05 over-life 需要非线性的关键帧插值，而 `Curve.Keyframe(time,value)`
  仅支持线性，且当时无任何消费者。
- **决策**：`Curve.Keyframe` 扩为 `(time, value, inTangent, outTangent, Interpolation{LINEAR,STEP,SMOOTH,BEZIER})`，
  mode 为「进入该关键帧」的插值；codec 增 `it`/`ot`/`i` 字段，解码缺省回退 0/LINEAR（旧资产无迁移）。
  新增 `CurveSampler`/`GradientSampler`（CPU）与 `CurveGradientGlsl`（分段 GLSL 函数，与采样器语义一致）。
- **理由**：曲线为编辑器作者工具数据，扩展风险低；分段 mix 链与 CPU 采样共用同一插值语义，可测可对齐。
- **影响**：`UniformLayout`/`GraphMaterial` 跳过 CURVE/GRADIENT 参数（不进 std140）；`GlslGenContext`
  增 `curve(id)`/`gradient(id)`；新增 `curve.sample`/`gradient.sample` 节点。

## ADR-018 黑板参数分组存 sidecar

- **背景**：M12-01 黑板增强需参数分组，但 `GraphParameter` 记录契约冻结且 runtime 消费。
- **决策**：分组为编辑器元数据 `EditorMetadata.paramGroups: paramId→group`（`<name>.editor.json`），
  不改核心 `GraphParameter`；`PropertyInspector` 读改组。
- **理由**：分组是纯编辑器呈现，不属图语义；sidecar 方案与 ADR-015 一致，零迁移。
- **影响**：无核心改动；加载/新建后刷新 inspector 的分组引用。

## ADR-019 VFX spawn/init 相位边界 + over-life 引用黑板曲线

- **背景**：M13 引入分离的 init 节点（只设置新 spawn 粒子）与 over-life 节点（按生命周期采样曲线），
  但 M5 的 `ParticleBuffer` 无相位区分，`VfxNodeFactory.create` 无参数访问。
- **决策**：`SimContext` 增可变 `spawnStart`（spawn 节点在本帧 spawn 前置为「本帧首新粒子索引」，init 节点
  只迭代 `[spawnStart, count)`）；over-life `life_*` 节点引用黑板 CURVE/GRADIENT 参数 id，`SimContext.curve(id)`
  /`gradient(id)` 解析（`VfxSimulator` 构造穿 `List<GraphParameter>`）。`ParticleBuffer` 增 rotation/mass/trail 历史
  （TRAIL_LENGTH=8 环形）支撑 orient 与 line/ribbon 输出。
- **理由**：分离 spawn/init 是 Unity VFX 的明确模型；spawnStart 边界不依赖 age 哨兵，图作者无需关心 init 与
  update_age 的相对顺序。曲线经参数引用可复用 M12 曲线编辑器。
- **影响**：`VfxPreview`/`GraphEffect` 构造 `VfxSimulator` 时传入参数；`VfxGraphRenderer` 扩展为 4 拓扑管线
  （billboard 旋转/mesh/line/ribbon），拓扑由输出节点类型派生。

## ADR-020 VFX 运行时存活参数绑定（不重建模拟器）+ 世界变换渲染

- **背景**：M15 需把图效果接入游戏运行时（技能/实体 spawn）。现有 `GraphEffect.setParameter` 重建模拟器
  （重置粒子），连续游戏值（实体速度/技能强度）每帧绑定不可接受；且效果发射器需在任意世界位置/朝向/缩放。
- **决策**：
  1. **存活参数**：`SimContext`/`VfxSimulator` 增存活参数 map（`Map<paramId, Value>`），`step` 每帧喂给上下文，
     `setLiveParam` 不重建模拟器。驱动节点（spawn_rate.rate、init_velocity、init_color、init_size、update_gravity）
     增 `param` 属性（STRING = 参数 id）：有则 step 时读存活值，否则用烘焙值（向后兼容）。
  2. **param 节点**：新增 `vfx.param_float/vec3/color/curve/gradient` 5 节点，在无外部绑定时注入兜底值；
     curve/gradient 按源参数复制，over-life 节点可按 param 引用采样。VFX 节点目录 40 → 45。
  3. **世界变换**：`VfxGraphRenderer.render` 增可选 `WorldTransform`（默认恒等），写实例/轨迹顶点时应用
     `world = p + R·(s·local)` 再减相机位；编辑器路径零改动。`GraphCamera.fromGameCamera` 由游戏相机状态构建。
- **理由**：存活参数避免每帧重建/重置，语义贴近 Unity VFX 的外部参数；param 节点让图作者可声明外部输入并兜底；
  世界变换在渲染端应用，粒子模拟保持发射器局部坐标，技能/实体只需设位置/朝向/缩放。
- **影响**：`GraphEffect` 增 `setLiveParam`/`setWorldTransform` 透传；`VfxGraphManager`/`ActiveEffect`
  （runtime 包）持有存活绑定 + 变换 + 实体跟随；驱动节点元数据增 `param` 属性。

## ADR-026 电弧子系统：约束 spine CPU + 观感 GPU，路径驱动无线程（方向 Y）

- **背景**：把旧 VFX 框架的「电弧（Arc）」能力迁入 vfxgraph，需求：两点路径、玩家/模型环绕、
  分叉状电弧、模型表面游走。评估三条路线：A（粒子 trail + 闪电 shader）、C（trail 带宽度曲线）、
  B（忠实移植旧 `ArcTube` 管状 + 后台线程）。`ParticleBuffer.TRAIL_LENGTH=8` 且 trail 是衰减历史
  语义 → A/C 装不下 50~200 点螺栓、做不了持续环、贴不了面，出局。旧 arc 的 CPU 修饰器
  （`JaggedModifier` 递归 `2^subdivisions` + `NoiseFieldModifier` 每点 3×Perlin + 大量分配）确实重到
  需要 `ArcExecutor` 单线程 + 一帧发布延迟。
- **决策**：
  1. **路径驱动，不走粒子**：新增独立 `ArcBuffer`（权威 polyline 路径），与粒子缓冲平行，
     `VfxSimulator` 同帧驱动。
  2. **方向 Y（去线程）**：CPU 只生成**约束 spine**（两点端点/环绕环/表面采样点/分支附着点 + 每点宽度，
     20~50 点）；锯齿/噪声场/辉光全部移至顶点/片元着色器（GPU）。CPU 量级降 1~2 个数量级，
     **不再需要后台线程**，同时消除旧方案的一帧发布延迟。
  3. **渲染主用 Tube（真 3D 管），Ribbon 作廉价 fallback**（原始决策）：表面游走必须贴曲率（扁带是广告牌会飘）、
     环绕需任意视角可见（扁带转角即消失）、分叉需物理细丝；两点路径可用扁带。
     **M22f 修订（已批准）**：用户否定全部自研方案（M22b 线芯/纸带、M22c X 形高斯、M22d 胖管、M22e 链式光束）后，
     **渲染一律复用旧 vfx 管网格**（`drawArcs` = `LightningMeshBuilder` + `vfxgraph_arc`），无 Ribbon fallback 分支。
  4. **复用但不桥接旧 arc**：parallel transport（`ArcTube.collectFrames`）与 ring 网格
     （`LightningMeshBuilder`）算法照搬/精简，去掉异步 executor 与 CPU 修饰器；旧 arc 系统保留共存。
- **理由**：需求以"表面游走/环绕/分叉"为主角，只有路径驱动的管状渲染能覆盖；把重活挪到 GPU
  正好消除旧 arc 需要单线程的根因，是"因为旧 arc 重到要线程，才更要让它变轻"。
- **影响**：新增 `vfxgraph/arc` 包（MOD-11）与 `vfx.block.arc_bolt/orbit/surface/output_arc` 4 块（VFX 目录 42→46）；
  `RenderSpec` 增 `Geometry.ARC`；`VfxGraphRenderer` 增 `drawArcs` 管状渲染 + `vfxgraph_arc.vsh/.fsh`；
  `VfxSimulator`/`VfxPreview` 并行驱动 arc 缓冲。详细设计见 `ARC_DESIGN.md`，任务见 `TASK_LEDGER.md` M22。
- **M22 容器化适配（实现期）**：设计稿先于 M23–M28 容器化，实现按容器模型落地——电弧为
  **块**（`vfx.block.arc_bolt/orbit/surface/output_arc`，SPAWN 类发射块 + OUTPUT 输出块），
  `VfxSystemSimulator` 持有 `ArcBuffer` 并老化（`SimContext.arcs()` 暴露给块），
  取代设计稿的扁平 `VfxNodeRegistry`/`VfxSimulator` 并行路径；渲染/着色器/性能决策不变。
  surface 走面用「最近质心三角形」邻居（对不共享顶点的三角化鲁棒），无 mesh 回退单位立方体。
- **方向暂停（2026-08-16）**：用户判断 arc 代码复用改动太大不可行，拟转向「毛笔笔迹」（brush）子系统重设计。

## ADR-027 VFX 图改造为 Unity 式容器 Context + 数据流（M23–M28，取代有序 passes）

- **背景**：用户反馈 VFX 节点编辑"不直观"——47 个 `vfx.*` 节点全部零端口，执行顺序靠节点列表插入序 +
  隐藏的 `SimContext.spawnStart` 隐式耦合 spawn→init；多 spawn + init 组合需小心顺序，违背 UE5/Unity
  式"可见可控"的编辑体验。目标对标 Unity VFX Graph 的 Spawn/Initialize/Update/Output 容器流水线。
- **决策**：
  1. **新容器模型 `VfxSystem`，与核心 `Graph` 并行**：新增 `vfxgraph/model` 包——
     `VfxSystem`（顶层）/`VfxContext`（SPAWN/INITIALIZE/UPDATE/OUTPUT 容器，内含 `VfxBlock`）/
     `VfxOperatorNode`（自由算子）/`VfxFlowEdge`（context 间批次 flow）/`VfxDataEdge`（算子→块数据流）/
     `ParticleAttribute`（粒子属性枚举）。**不破坏核心 `Graph` 契约冻结**。
  2. **批次携带（完整 flow 语义）**：spawn 块输出「本帧新粒子索引批次 [start,end)」，flow 边把它传给
     下游 init 块；每个 init 只处理自己上游 spawn 的批次——**彻底消除 `SimContext.spawnStart` 单点耦合**。
  3. **全属性数据流（本期）**：attr-read 算子读粒子属性（逐粒子每帧 CPU 求值）+ 数学/曲线/渐变/参数
     算子驱动块属性输入端口；编译期可折叠的（常量/参数/曲线）求值一次。
  4. **旧 schema 彻底废弃**：新 `JsonVfxGraphCodec`（`kind:"vfx"` + 容器 schema），旧扁平节点列表
     读取路径不保留、不迁移；7 个示例资产与新节点目录在 M27 一次性转档。
  5. **渲染层零改动**：`ParticleBuffer`→`VfxGraphRenderer`→`RenderSpec` 保持；Output 块 → `RenderSpec`
     保留 M21l 数据驱动（vertex/shader/blend 属性）。
- **理由**：容器 + 批次 flow + 数据流是把「spawn→init 耦合从隐式顺序变成显式连线」的唯一彻底解法；
  新模型并行可避免动核心契约与既有测试；渲染层不动则把改造面收窄到模拟/编辑器两层。
- **影响**：新增 MOD-12（`vfxgraph.model`）、MOD-13（`vfxgraph.container` 执行器）；M24 新执行器
  `VfxSystemSimulator` 替代 `VfxSimulator`；M25 算子集；M26 编辑器容器画布；M27 全节点+资产迁移；
  M28 运行时接线。M22 电弧延后到容器化之后（避免二次迁移）。任务见 `TASK_LEDGER.md` M23–M28。

## 待定决策（后续会话补充）

- 粒子 SoA 缓冲区的容量/增长策略（M5）
- 图资产在游戏内的存储位置与热重载触发（M7）——M15 定为 `assets/academy/vfxgraph/*.json`（资源重载监听）+
  dev 模式 `run/vfxgraph/` 文件监听（WatchService）双通道。
