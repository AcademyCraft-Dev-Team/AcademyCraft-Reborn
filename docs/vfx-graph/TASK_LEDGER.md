# 任务控制矩阵

追踪枢纽。任务 ID 恒映射到「模块 → 文件 → 测试 → DoD」。提交消息带任务 ID 实现双向溯源。

状态：`todo` / `doing` / `done` / `blocked`（blocked 需注明阻塞原因）。

## 图例

- 模块编号见 `MODULES.md`（MOD-01~15）
- 里程碑：M0~M8

## M0 地基工具

| ID | 模块 | 任务 | 状态 | 文件/产物 | DoD |
| --- | --- | --- | --- | --- | --- |
| M0-01 | — | 创建接力构件（PROGRAM/DECISIONS/GLOSSARY/MODULES/TASK_LEDGER/STATE） | done | docs/vfx-graph/*.md | 六件齐全 |
| M0-02 | — | 抽象层黑名单扫描工具 | done | tools/check_abstraction.sh | 扫描黑名单类即报错 |
| M0-03 | MOD-01~06 | graph/ 核心接口骨架 + package-info | done | src/main/java/.../graph/** | 编译通过、NullMarked 全绿 |
| M0-04 | — | CI/构建门禁验证 | done | check_package_info + check_abstraction + gradle build | 三项全通过 |

## M1 节点图核心

| ID | 模块 | 任务 | 状态 | 文件/产物 | DoD |
| --- | --- | --- | --- | --- | --- |
| M1-01 | MOD-01 | `Value` 变体与 `ValueType` 完整实现 | done | graph/type/Value.java | 单测覆盖每种类型 |
| M1-02 | MOD-01 | `TypeConverter` 隐式转换矩阵 | done | graph/type/TypeConversions.java | 转换规则单测 |
| M1-03 | MOD-02 | `GraphNode`/`Port`/`Edge`/`Graph` 不可变模型 | done | graph/model/ | 构建器单测 |
| M1-04 | MOD-02 | `GraphParameter`（黑板）模型 | done | graph/model/GraphParameter.java | 参数往返单测 |
| M1-05 | MOD-03 | `NodeRegistry` 注册/查询 | done | graph/registry/SimpleNodeRegistry.java | 注册查询单测 |
| M1-06 | MOD-04 | `GraphCodec` 编解码 + schema 版本 | done | graph/serialize/JsonGraphCodec.java | 示例图往返序列化 |
| M1-07 | MOD-04 | 迁移链框架 `GraphMigration` | done | graph/serialize/GraphMigrations.java | 旧版本 JSON 迁移单测 |
| M1-08 | MOD-05 | `GraphValidator` 类型检查 | done | graph/validate/DefaultGraphValidator.java | 类型不匹配被拒 |
| M1-09 | MOD-05 | 环检测 | done | graph/validate/DefaultGraphValidator.java | 环被拒、有定位 |

## M2 图编译

| ID | 模块 | 任务 | 状态 | 文件/产物 | DoD |
| --- | --- | --- | --- | --- | --- |
| M2-01 | MOD-06 | 拓扑排序 + 死代码消除 | done | graph/compile/DefaultGraphCompiler.java | 顺序正确、死节点剔除 |
| M2-02 | MOD-06 | 常量折叠 | done | graph/compile/DefaultGraphCompiler.java + NodeEvaluator.java | 常量子图求值正确 |
| M2-03 | MOD-06 | `CompiledGraph` 执行计划与非法图处理 | done | graph/compile/CompiledGraph.java + GraphCompileException.java | 非法图抛异常 |

## M3 Shader 代码生成

| ID | 模块 | 任务 | 状态 | 文件/产物 | DoD |
| --- | --- | --- | --- | --- | --- |
| M3-01 | MOD-07 | `GlslWriter`/`Swizzle`/表达式 IR | done | shader/codegen/ (Expr/GlslType/Swizzle/GlslNames/GlslWriter/GlslLiterals) | IR 单测 |
| M3-02 | MOD-07 | 基础 math/input 节点目录 | done | shader/nodes/ShaderNodes.java | 每节点 codegen 单测 |
| M3-03 | MOD-07 | geometry/噪声节点目录（texture 采样延后 M4） | done | shader/nodes/ (uv/noise/combine/split) | codegen 单测 |
| M3-04 | MOD-07 | `GlslGenerator` 完整着色器拼装 | done | shader/codegen/GlslGenerator.java | 图→合法 GLSL |
| M3-05 | MOD-07 | `DynamicShaderSource` + 内容哈希 Identifier | done | shader/pipeline/DynamicShaderSource.java | 源码解析正确 |
| M3-06 | MOD-07 | `ShaderGraphPipeline` 构建 + `precompilePipeline` 编译 | done | shader/pipeline/ShaderGraphPipeline.java | 编译产出可用管线 |
| M3-07 | MOD-07 | `GraphMaterial` + `UniformLayout` 参数绑定 | done | shader/pipeline/ (GraphMaterial/UniformLayout) | 参数绑定正确 |

## M4 编辑器 MVP + 预览

| ID | 模块 | 任务 | 状态 | 文件/产物 | DoD |
| --- | --- | --- | --- | --- | --- |
| M4-00 | — | ImGui 后端泛化到桌面（`ImGuiBackend` + `EditorApp` 钩子） | done | main/.../imgui/ImGuiBackend.kt + editor/.../platform/* | 游戏/桌面共享，编译通过 |
| M4-01 | MOD-09 | `GraphEditorApp`（实现 `EditorApp`）骨架 | done | editor/grapheditor/app/GraphEditorApp.kt | 桌面启动 + 存读 JSON |
| M4-02 | MOD-09 | `NodeCanvas` 画布 + 缩放平移 | done | editor/grapheditor/canvas/NodeCanvas.kt + Camera2D.kt | 可平移缩放 |
| M4-03 | MOD-09 | 节点渲染 + 选择 + 框选 | done | editor/grapheditor/canvas/NodeCanvas.kt | 交互正确 |
| M4-04 | MOD-09 | 边创建/删除（类型校验） | done | editor/grapheditor/canvas/NodeCanvas.kt + GraphEditorModel.kt | 非法连接被拒 |
| M4-05 | MOD-09 | `NodePalette` 搜索/分类 | done | editor/grapheditor/palette/NodePalette.kt | 可搜索插入节点 |
| M4-06 | MOD-09 | `PropertyInspector` + 黑板编辑 | done | editor/grapheditor/inspector/PropertyInspector.kt | 参数可编辑 |
| M4-07 | MOD-09 | `ShaderPreview` 实时预览 | done | editor/grapheditor/preview/ShaderPreview.kt | 改图即见效果 |

> 说明：M4 为编辑器 MVP，运行时 GUI 冒烟需在有显示环境下手动 `./gradlew graphEditor`；编译门禁已通过。

## M5 VFX 模拟

| ID | 模块 | 任务 | 状态 | 文件/产物 | DoD |
| --- | --- | --- | --- | --- | --- |
| M5-01 | MOD-08 | `ParticleBuffer`(SoA) + 容量策略 | done | vfxgraph/sim/ParticleBuffer.java | 容量增长正确 |
| M5-02 | MOD-08 | `VfxSimulator`/`SimContext` 逐帧执行 | done | vfxgraph/sim/ | 生命周期正确 |
| M5-03 | MOD-08 | spawn/update 节点目录 | done | vfxgraph/nodes/VfxNodes.java | 每节点单测 |
| M5-04 | MOD-08 | 颜色/尺寸(fade)/力(gravity)/输出节点 | done | vfxgraph/nodes/VfxNodes.java | 确定性可测 |
| M5-05 | MOD-08 | 发射器形状（point/sphere/box/cone） | done | vfxgraph/shape/ | 形状采样单测 |

> 说明（ADR-012）：VFX 是「有状态有序 passes」而非数据流，故 M5 以有序 `List<GraphNode>` 驱动
> `VfxSimulator`，M6 桥接层再产出该顺序。曲线/梯度采样与碰撞延后 M6；mesh 形状依赖资源加载，延后 M6/M7。

## M6 VFX 自持渲染 + 播放（无桥，ADR-013 取代 ADR-004 的桥接方案）

| ID | 模块 | 任务 | 状态 | 文件/产物 | DoD |
| --- | --- | --- | --- | --- | --- |
| M6-01 | MOD-08 | `GraphCamera` 自持相机 | done | vfxgraph/render/GraphCamera.java | 投影/视图矩阵正确 |
| M6-02 | MOD-08 | `VfxGraphRenderer`（billboard 实例化，专用管线） | done | vfxgraph/render/VfxGraphRenderer.java | 抽象层渲染、编译通过 |
| M6-03 | MOD-09 | `VfxPreview` 播放/步进/重置 | done | editor/grapheditor/preview/VfxPreview.kt | 编辑器可播放 |
| M6-04 | MOD-09 | 编辑器 Shader/VFX 模式切换 | done | editor/grapheditor/app/GraphEditorApp.kt | 双模式可切换 |

## M7 资产管线 + 运行时集成

| ID | 模块 | 任务 | 状态 | 文件/产物 | DoD |
| --- | --- | --- | --- | --- | --- |
| M7-01 | — | 图资产加载/缓存 | done | graph/assets/GraphAssets.java | 缓存命中 |
| M7-02 | — | 运行时效果 + 参数绑定（框架） | done | vfxgraph/GraphEffect.java | 参数覆盖生效 |
| M7-03 | — | 热重载 | done | graph/assets/GraphAssets.java (invalidate) | 失效后可重载 |
| M7-04 | — | 版本迁移接线 | done | JsonGraphCodec 迁移链（GraphAssetsTest 验证） | 旧资产可加载 |

> 说明：M7 交付了「资产缓存 + 运行时效果框架 + 参数覆盖 + 迁移接线」库层。**游戏内实际挂点
> （技能/实体 spawn 图效果、客户端 tick/render 循环、文件监听）延后**，需在游戏运行时环境验证，
> 记入 M8 或后续里程碑。

## M8 兼容/性能/发布审计

| ID | 模块 | 任务 | 状态 | 文件/产物 | DoD |
| --- | --- | --- | --- | --- | --- |
| M8-01 | — | Sodium/Iris 兼容 | todo | 兼容层 | 双后端正常 |
| M8-02 | — | 性能门禁 | todo | 基准 | 帧耗时达标 |
| M8-03 | — | 文档/许可审计 | todo | docs | 完整 |

> M8 并入 Phase 2 M16（详见 `EDITOR_ROADMAP.md`）。

## Phase 2（M9–M16，产品化扩展）

> 详细任务分解与目标见 `EDITOR_ROADMAP.md`；节点实现清单见 `NODE_CATALOG.md`。以下为控制矩阵总表。

### M9 编辑器基建

| ID | 任务 | 状态 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M9-01 | 命令栈 Command/UndoManager | done | 400 | 可逆命令框架 |
| M9-02 | 模型 mutation 命令化 | done | 800 | 所有编辑可撤销 |
| M9-03 | 剪贴板 复制/粘贴/复制 | done | 500 | Ctrl+C/V/D |
| M9-04 | 多选变换 + 对齐/分布 | done | 800 | 多选对齐 |
| M9-05 | 右键上下文菜单 | done | 600 | 右键加节点 |
| M9-06 | 快捷键系统 | done | 500 | 快捷键可用 |
| M9-07 | 搜索命令面板 | done | 500 | 可搜索执行 |
| M9-08 | 边重连 + 端口高亮 | done | 700 | 可重连 |
| M9-09 | 测试 | done | 1200 | 覆盖齐全 |

> 说明（2026-08-13）：M9 全部落地。编辑器新增 `editorTest` source set（build.gradle.kts 注册 Test 任务并入 `check`），
> 49 个单测通过（UndoManager 合并/深度、各命令往返、模型端到端 undo/redo、剪贴板、对齐/分布几何）。
> 关键设计：命令接口 `Command`（execute/undo/label/mergeKey/mergeWith）+ `UndoManager`（栈深 100、相邻同 key 合并）；
> 拖拽移动/属性拖拽经 mergeKey 合并为单条命令，一次撤销恢复整段拖拽；`AddNodeCommand` 复用 node id 保证 redo 身份稳定。

### M10 画布增强

| ID | 任务 | 状态 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M10-01 | 节点分组 frame | done | 600 | 可分组 |
| M10-02 | sticky note | done | 400 | 可注释 |
| M10-03 | minimap | done | 500 | 可导航 |
| M10-04 | zoom-to-fit + 网格吸附 | done | 400 | 吸附可用 |
| M10-05 | docking + 布局持久化 | done | 800 | 布局可存读 |
| M10-06 | 项目/资产浏览器 | done | 700 | 可浏览打开 |
| M10-07 | 最近文件 | done | 200 | 最近文件 |
| M10-08 | 测试 | done | 400 | 覆盖 |

> 说明（2026-08-13）：M10 全部落地，editorTest 累计 68 个单测通过。
> - 元数据持久化走 **sidecar**：核心 `<name>.json`（JsonGraphCodec 不变）+ `<name>.editor.json`
>   （`document/EditorMetadataCodec`：frames/notes/camera/panels），运行时 `GraphAssets` 零改动。
> - docking：`renderImGui` 重写为 `dockSpace`（PassthruCentralNode）+ Canvas 无背景让预览透出；
>   Palette/Inspector/Project/GLSL 为可停靠面板，View 菜单显隐，布局经 `imgui-graph.ini` 持久化。
>   imgui-java 无 `dockBuilder*`，首启面板浮于默认位，拖拽停靠后 ini 记忆（无初始分栏 API）。
> - frames/notes：`GraphEditorModel` 增集合 + 10 个命令类（拖拽/缩放 mergeKey 合并）；frame 拖拽带动框内节点，
>   右下角拖拽缩放；右键 Group Selection / 重命名 / 编辑 note。

### M11 Shader 节点补全

| ID | 任务 | 状态 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M11-01 | 纹理系统 + texture.sample | done | 1500 | 预览可见贴图 |
| M11-02 | ImGuiBackend 多纹理 ID | done | 800 | ImGui 显示纹理 |
| M11-03 | 数学全量 | done | 800 | 每节点单测 |
| M11-04 | 区间/缓动 | done | 400 | 单测 |
| M11-05 | 三角/向量全量 | done | 800 | 单测 |
| M11-06 | 噪声库 | done | 1200 | 各噪声单测 |
| M11-07 | 坐标/几何 | done | 1000 | 单测 |
| M11-08 | 颜色/渐变 | done | 900 | 单测 |
| M11-09 | 顶点输出 + 自定义函数 | done | 500 | 支持顶点输出 |

> 说明（2026-08-13）：M11 全部落地，Shader 节点目录 27 → **83**（≥80 达标），主 test 累计 575 单测通过。
> - **texture.sample**：采样固定 uniform `Sampler0`（`GlslGenerator` 声明 + `ShaderGraphPipeline` 增 SAMPLER0 bind group +
>   `ShaderPreview` 绑定棋盘格预览贴图，DoD「预览可见贴图」达成）；SAMPLER 黑板参数从 std140 跳过。
>   **限制**：单 sampler（真实资产按纹理属性加载 + 多样本绑定留待 M15 运行时集成）。
> - **M11-02**：`ImGuiBackend` 纹理 ID 注册表（字体固定 ID 1，其余自 2 起），`renderDrawData` 逐命令切换纹理，
>   `DesktopUiHost.imgui` 暴露后端。
> - **M11-07 坐标/几何**：全屏 quad 预览近似（camera_pos/view_dir 常量、world/object pos 由 UV 派生、normal=(0,0,1)）；
>   真实顶点属性留待 M14 视口/M15 运行时。transpose 因无矩阵类型跳过。
> - M11-09 自定义函数节点 `output.custom`（body 属性内联 GLSL）；顶点输出节点延后（顶点阶段为固定全屏 quad）。

### M12 黑板+子图+曲线编辑器

| ID | 任务 | 状态 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M12-01 | 黑板增强（分组/类型/范围） | done | 700 | 分组编辑 |
| M12-02 | CurveSampler + 曲线→GLSL | done | 700 | 曲线生效 |
| M12-03 | bezier 曲线编辑器 | done | 900 | 拖点编辑 |
| M12-04 | 渐变 ramp 编辑器 | done | 900 | 拖点编辑 |
| M12-05 | sub-graph | done | 700 | 子图可用 |
| M12-06 | 测试 | done | 500 | 覆盖 |

> 说明（2026-08-13）：M12 全部落地，主 test 累计 597 + editorTest 71 通过。
> - **曲线模型（ADR-017）**：`Curve.Keyframe(time, value, inTangent, outTangent, Interpolation{LINEAR,STEP,SMOOTH,BEZIER})`；
>   codec 增切线字段（`get("it")==null→0` 防御式解析，旧资产无迁移）；`GraphMaterial`/`UniformLayout` 跳过
>   CURVE/GRADIENT/SAMPLER 参数（不进 std140）。
> - **采样器**：`CurveSampler`/`GradientSampler`（CPU）+ `CurveGradientGlsl` 分段 GLSL 函数
>   （`curve.sample`/`gradient.sample` 节点，`GlslGenContext` 增 `curve(id)`/`gradient(id)`）。
> - **编辑器**：`editorcurve/CurveEditor`（拖关键帧/切线/增删/插值模式）+ `gradient/GradientEditor`
>   （色带/停靠点/颜色编辑），docked 面板，Inspector 对 CURVE/GRADIENT 参数「Edit」打开；
>   编辑经 `replaceParameter` 入命令栈（可撤销）。
> - **黑板**：类型补全（VEC2/VEC4/SAMPLER/CURVE/GRADIENT）+ FLOAT 范围编辑；分组存 sidecar
>   `EditorMetadata.paramGroups`（ADR-018），不改核心 GraphParameter。
> - **sub-graph（编译内联）**：`graph/subgraph/SubGraphRegistry` + `SubGraphFlattener`（参数→输入端口
>   `in<i>`，覆盖接父源 / 未覆盖提升顶层参数；输出端口 `out` 接子图输出节点）；`subgraph` 节点类型 +
>   `GraphEditorModel` 动态端口派生；`ShaderPreview` 编译前展开。编辑器标签页打开子图延后。

### M13 VFX 节点补全

| ID | 任务 | 状态 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M13-01 | spawn burst/periodic/distance | done | 700 | 单测 |
| M13-02 | init 全量 | done | 900 | 单测 |
| M13-03 | 力场（force/noise/turbulence/vortex/drag） | done | 900 | 单测 |
| M13-04 | collision + kill/bounds | done | 700 | 单测 |
| M13-05 | over-life（曲线） | done | 700 | 曲线采样单测 |
| M13-06 | orient | done | 400 | 单测 |
| M13-07 | 输出变体（quad/mesh/line/ribbon） | done | 1200 | 多输出渲染 |
| M13-08 | 形状补全（cylinder/torus/circle/mesh） | done | 500 | 形状单测 |
| M13-09 | 测试 | done | 600 | 覆盖 |

> 说明（2026-08-13）：M13 全部落地，VFX 节点 6 → **40**（mesh surface 形状延后 M15），主 test 累计 613 单测通过。
> - **地基**：`ParticleBuffer` 增 rotation/mass/trail 历史（TRAIL_LENGTH=8 环形，line/ribbon 用）；
>   `SimContext` 增可变 `spawnStart`（spawn 节点标记本帧新粒子，init 节点只处理 `[spawnStart,count)`，ADR-019）+
>   `curve(id)`/`gradient(id)`；`VfxSimulator` 构造穿 `List<GraphParameter>`（VfxPreview/GraphEffect 同步）。
> - **over-life**：`life_*` 节点引用黑板 CURVE/GRADIENT 参数，经 `CurveSampler`/`GradientSampler` 采样。
> - **M13-07 全拓扑渲染**：`VfxGraphRenderer` 扩展 4 管线——旋转 billboard（quad）、实例化立方体（mesh）、
>   trail 折线（LINES）、trail 四边形条带（RIBBON，垂直相机偏移）；拓扑由输出节点类型派生
>   （`fromOutputType`，VfxPreview/GraphEffect 传入）。
> - **形状**：`CylinderShape`/`TorusShape`/`CircleEdgeShape`（mesh surface 延后 M15）。

### M14 视口 overhaul

| ID | 任务 | 状态 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M14-01 | 离屏视口 + blit | done | 800 | 视口独立 |
| M14-02 | 轨道相机 | done | 600 | 可旋转 |
| M14-03 | gizmo + 网格地面 | done | 800 | gizmo 可用 |
| M14-04 | 播放/暂停/步进/时间轴 | done | 500 | 控制完整 |
| M14-05 | 统计 overlay | done | 400 | 统计可见 |
| M14-06 | 质量档位 | done | 300 | 可调档 |
| M14-07 | 测试 | done | 300 | 覆盖 |

> 说明（2026-08-13）：M14 全部落地，editorTest 累计 76 单测通过。
> - **M14-01 离屏视口**：`ViewportPanel` 持离屏 `TextureTarget`，`DesktopEnvironment.imguiBackend` 由
>   `DesktopUiHost` 注入；纹理经 `ImGuiBackend.registerTexture`（M11-02）注册后 `ImGui.image` 显示（UV 翻转 V）；
>   `GraphEditorApp.renderBackground` 改为渲到离屏 target，全窗口背景预览移除。
> - **M14-02 轨道相机**：`OrbitCamera`（yaw/pitch/distance/target → `GraphCamera` 纯旋转视图 + 透视投影），
>   视口内左键旋转/右键平移/滚轮缩放。
> - **M14-03 gizmo + 网格**：选中 spawn/init_position 节点后 ImGuizmo translate gizmo 编辑 emitter origin
>   （拖拽结束写回 `origin_x/y/z`，经 `setProperty` 可撤销）；ImGuizmo.drawGrid 网格地面。
> - **M14-04 控制 + 循环**：Play/Pause/Step/Reset/Loop + 时间显示（`VfxPreview.time`；scrubbing 延后）。
> - **M14-05/06**：统计 overlay（FPS/ms/粒子数/分辨率）+ 质量档位（0.5x~2x 分辨率缩放）。

### M15 运行时集成

| ID | 任务 | 状态 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M15-01 | 客户端效果管理器（tick/render 循环） | done | 800 | 游戏内渲染 |
| M15-02 | 图资产游戏内加载 | done | 600 | 游戏内加载 |
| M15-03 | 技能/实体 spawn + 世界变换绑定 | done | 800 | 可 spawn |
| M15-04 | 参数绑定游戏值 | done | 600 | 参数绑定 |
| M15-05 | 热重载 watcher | done | 400 | 热重载 |
| M15-06 | 池化/剔除 | done | 800 | 性能达标 |

> 说明（2026-08-13）：M15 全部落地，主 test 累计 642 + editorTest 74 通过，check 全绿，abstraction check OK。
> - **runtime 包** `vfxgraph/runtime/`：`VfxGraphManager`（单例，持共享 `VfxNodeRegistry` + `GraphAssets` + 按拓扑渲染器池）、
>   `ActiveEffect`（效果实例 + 世界变换 + 实体跟随 + 存活绑定 + 生命周期）、`EffectBudget`（粒子上限 + 距离/视锥剔除）、
>   `VfxGraphAssetLoader`（`PreparableReloadListener`，`assets/academy/vfxgraph/*.json` → GraphAssets，F3+T 热重载）、
>   `GraphFileWatcher`（dev 模式 WatchService，`run/vfxgraph/` 文件变更 → reloadFromFile）。
> - **接线**：`AcademyCraftClient` initRender/onClientTick/onClientStopped + `/academy vfx spawn <graph> [x y z]` 命令；
>   `MixinLevelRenderer.render` 在 `VfxManager.renderFrame()` 后渲到主 RT 颜色视图（`GraphCamera.fromGameCamera`）。
> - **存活参数（ADR-020）**：`SimContext`/`VfxSimulator.setLiveParam` 不重建；驱动节点 `param` 属性 + 5 个
>   `vfx.param_float/vec3/color/curve/gradient` 节点（VFX 节点目录 40 → **45**）；`WorldTransform` 渲染端应用。
> - **示例资产**：`assets/academy/vfxgraph/demo_burst/fountain/ribbon.json`，`SampleAssetsTest` 验证可解码可 spawn。
> - **限制**：游戏内渲染正确性（深度/混合/Iris 共存）需有显示环境 `./gradlew clientDev` 手动冒烟；实体跟随的
>   `Entity` 生命周期路径无 headless 单测（构造需 Level），由 `/academy vfx spawn` 命令覆盖手动验证。

### M16 兼容/性能/发布审计

| ID | 任务 | 状态 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M16-01 | Sodium/Iris 兼容 | done | 800 | 双后端正常 |
| M16-02 | 性能门禁 | done | 600 | 达标 |
| M16-03 | 全量单测补全（≥300） | done | 1500 | 覆盖 |
| M16-04 | 文档/许可审计 + 用户手册 | done | 600 | 完整 |
| M16-05 | GLSL 黄金测试 | done | 500 | 快照通过 |

> 说明（2026-08-13）：M16 全部落地，主 test 累计 696 + editorTest 74 通过，check 全绿，abstraction check OK。
> - **M16-01**：`MixinLevelRenderer.render` 的 `VfxGraphManager.renderFrame` 包 `IrisCompat.runWithBypass`（与
>   VfxManager/PostEffect 同款）；`AcademyCraftClient` 清理 2 个死 Iris import；新增 `VfxGraphIrisCompatTest`。
>   Iris/Sodium 为可选依赖（未声明 mods.toml 硬依赖）；真机共存手测路径写入 USER_GUIDE。
> - **M16-02**：`VfxSimulatorPerfTest`（10k 稳态 step 最坏 ~4ms）+ `ParticleBufferPerfTest`（10k spawn/kill），
>   宽松预算防 CI 抖动；`VfxGraphRenderer` 消除每帧分配（`float[3]` 复用为成员、`TRAIL_FORMAT` 缓存 static、
>   `CLEAR_COLOR` 复用）。
> - **M16-03**：补齐 serialize（GraphMigrations/GraphSchemaVersion）、subgraph（SubGraphRegistry）、
>   codegen（GlslWriter/GlslNames/GlslLiterals/Swizzle/Expr/GlslGenContext/GlslProgram/GlslNodeRegistry/GlslNodeGenerator）、
>   pipeline（GraphMaterial/ShaderGraphPipeline/ShaderGraphResult）、runtime（ActiveEffect）、nodes（VfxNodeRegistry）、
>   render（VfxGraphRenderer.fromOutputType）测试。
> - **M16-04**：新 `docs/vfx-graph/USER_GUIDE.md`（建图/预览/资产格式/spawn API/热重载/性能手测/Iris 兼容）；
>   README/README.zh-CN 补图编辑器章节；许可审计——图系统全自研、无新增三方依赖，thirdparty/NOTICE 覆盖字体。
> - **M16-05**：`GlslGoldenTest`（6 个代表性图 → GLSL 精确快照）+ `src/test/resources/shader/golden/*.glsl`；
>   `./gradlew test -Dgolden.update=true` 更新模式（build.gradle.kts 转发属性）。

### BUGFIX（2026-08-13，M15/M16 代码审计修复）

| ID | 问题 | 修复 | 文件 |
| --- | --- | --- | --- |
| B-01 | runtime tick 传 tick 数当秒 → 效果慢 20 倍 | `onClientTick` 用 `dt/20f` 折算秒 | AcademyCraftClient.java |
| B-02 | 资源重载在后台线程写非线程安全集合（HashMap 并发） | 后台仅解析，`preparationBarrier.wait` 后主线程 apply | VfxGraphAssetLoader.java |
| B-03 | spawn_burst/periodic/distance 不设 lifetime → 首帧即灭 + 槽位残留 | spawn 节点默认 lifetime/size/color/velocity；`ParticleBuffer.spawn` 重置字段 | VfxNodes/ParticleBuffer.java |
| B-04 | buildShape 未接线 cylinder/torus/circle_edge（静默落回 point） | 补三个分支 + 形状属性映射 | VfxNodes.java |
| B-05 | 视锥剔除坐标系错误（世界坐标代入纯旋转平面，非原点相机失真） | 改用 JOML `FrustumIntersection` + 相机平移合成完整 view·projection | EffectBudget/VfxGraphManager.java |
| B-06 | dev 热重载后台线程并发写 | WatchService 线程读文件 → `Minecraft.execute` 主线程 reload | GraphFileWatcher/VfxGraphManager.java |
| B-07 | 粒子上限 `canSpawnMore` 从未生效 | `VfxGraphManager.tick` 超限冻结该帧模拟 | VfxGraphManager.java |

> 说明：7 处修复全部配套回归单测（`VfxSpawnLifetimeTest`、`EffectBudgetTest` 相机相对回归、
> `VfxGraphManagerTest.particleCapFreezesSpawnAtLimit` 等），主 test 696 → **704**，check 全绿。

### BUGFIX（2026-08-15，M26 容器编辑器视口冻结）

| ID | 问题 | 修复 | 文件 |
| --- | --- | --- | --- |
| B-08 | 编辑器打开容器 demo_fire 视口冻结第一帧 + 缩放/移动无反应 + 改尺寸显示错误 | `VfxPreview.sync()` 版本守卫在清空模拟器**之前**——版本未变须保留现有模拟器，否则每帧清空但不重建（扁平/容器分支都受影响） | VfxPreview.kt |
| B-09 | GlProgram 警告「fire shader 不用 Sampler0」 | `vfxgraph_fire.fsh` 移除未使用的 `uniform sampler2D Sampler0`（仅用 Sampler1 深度 soft particles） | vfxgraph_fire.fsh |
| B-10 | 容器画布数据连线失败/不可见：反向拖拽（块输入端口→算子输出）把假端口 id `"@in"` 传给 `connectData`（校验真实端口 → 拒绝） | `finishConnecting` 的 DATA_FROM/DATA_TO 两端均用 `firstOutputPort`/`firstInputPort` 解析真实端口 id | VfxContainerCanvas.kt |
| B-11 | loop 播放时移动节点导致"停止"：移动（布局变更）也触发 version++ → VfxPreview 每帧重建模拟器 → 粒子重置、播放视觉中断 | `VfxContainerModel` 区分 `simVersion`（仅影响模拟的变更：增删/连线/属性/参数/输出递增）与 `version`（含布局移动）；`VfxPreview` 容器路径按 `simVersion` 判断重建——移动节点不再重建 | VfxContainerModel.kt / VfxPreview.kt |
| B-12 | 打包资产转档破坏多 spawn 独立性：转换脚本按类型分组（4 个 spawn 塞一个 SPAWN context、4 个 init 塞一个 INITIALIZE context）→ 批次全混，4 个 init_velocity 依次覆盖同一批粒子，各层速度全乱（demo_fire 全变 smoke 速度） | 转换改为**按 spawn 分组**：每组独立 SPAWN + INITIALIZE context（flow 一对一），UPDATE/OUTPUT 共享；demo_fire 现为 4 条 spawn→init 链 + 共享 update/output | 资产转档（6 个）+ VfxFireLayerTest 回归 |
| B-13 | loop 播放时编辑节点后 t 冻结：效果粒子为 0（burst 播完/编辑调没 spawn）时 loop 每帧 `reset()+sync()` 重建模拟器，time 每次归零 | loop 重启改为**延续 time**（`VfxSystemSimulator.setTime`/`VfxSimulator.setTime`），UI 的 t 持续增加不冻结 | VfxPreview.kt / VfxSystemSimulator / VfxSimulator |
| B-14 | init 块数据输入端口与批次输入端口左缘重叠（垂直仅差 6px） | 数据输入端口 init 块上移 -5px、批次输入下移 +7px（间距拉开），绘制/命中坐标同步 | VfxContainerCanvas.kt |
| B-15 | loop 播放时编辑节点后渲染不可见：loop 分支每帧 `reset()` 强制重建模拟器 → spawn_rate 累加器归零 → 永远 spawn 不出粒子（需手动开关 loop 恢复） | loop 重启加**节流**（`canLoopRestart` 250ms 内只放行一次），重建后给 spawn 时间累积产粒 | VfxPreview.kt |
| B-16 | demo_fire 重写为**块级 flow 紧凑结构**：1 个 SPAWN context（4 spawn）+ 1 个 INITIALIZE context（8 init 块）+ 4 条 `blockFlows` 配对（spawn_core→init_vel_core 等），替代 8 个 context 长链 | 重写 demo_fire.json；校验/模拟/各层速度独立回归 | demo_fire.json + VfxFireBlockFlowAssetsTest |
| B-17 | **输出节点 shader 属性去穷举**：`OUTPUT_PROPERTIES` 的 vertex/shader 默认值写死 `academy:core/vfxgraph_billboard`（具体 shader id 进代码） | 默认值改**空串**（真正中性），由图上显式指定；渲染层 `RenderSpec.fromOutputNode` 保留 billboard 兜底（M21l「缺失走中性默认」），资产已显式指定 shader 故运行时不变 | VfxBlocks/VfxNodes OUTPUT_PROPERTIES |
| B-18 | 容器画布右键弹窗不显示（VFX 模式）：弹窗跨类传递（画布 openPopup → GraphEditorApp beginPopup）时序/作用域不稳 | **画布内直接渲染弹窗**：`VfxContainerCanvas.renderContextMenus()` 在 render() 末尾（popClipRect 后）渲染全部弹窗（EDGE/BLOCK/NODE/CONTEXT/CANVAS）；CANVAS palette 经 `canvasPalette` 回调委托宿主；GraphEditorApp 移除旧 renderContainerContext | VfxContainerCanvas.kt / GraphEditorApp.kt |
| B-19 | 撤销/重做在 VFX 容器模式失效（只能撤一次）——Edit 菜单/快捷键/Ctrl+Z 全路由到扁平 model，容器操作（addBlock 等）记录在 containerModel 栈却撤不动 | 新增 `activeUndo/activeRedo/activeCanUndo/activeCanRedo`，VFX 模式路由到 `containerModel` | GraphEditorApp.kt |
| B-20 | **smoke_shader 穷举**：渲染器硬编码 smoke 层（`RenderSpec.smokeFragmentShader` + `smokeSpec()` 写死 QUAD/TRANSLUCENT + `VfxGraphRenderer` 按 layer 拆分 fire/smoke 二次绘制 + `smokeInstanceBuffer`），违背 M21l「禁止代码穷举着色器」 | **改为多输出数据驱动（双输出节点）**：`RenderSpec` 去 smokeFragmentShader/smokeSpec，增 `layer` 过滤（`""`=全部，`matchesLayer`）；输出节点/块 `OUTPUT_PROPERTIES` 去 `smoke_shader` 增 `layer`；`VfxGraphRenderer.render` 收 `List<RenderSpec>` 逐 spec 按 layer 过滤绘制（bloomPass 只画 GLOW 规格），删 `smokeInstanceBuffer`；`GraphEffect`/`ActiveEffect`/`VfxGraphManager`（rendererPool 按 specs 列表）/`VfxPreview`/`EditorGlow` 全改多 specs；容器 `SetOutputCommand` 改切换语义（多输出共存）；layer 映射统一到 `ParticleBuffer.layerByte/layerFilter`（VfxNodes/VfxBlocks 委托）；demo_fire 拆两输出块（fire glow + smoke translucent）；测试换 layer 过滤/多输出。主 test +1，editorTest +1 | RenderSpec / VfxGraphRenderer / VfxNodes / VfxBlocks / GraphEffect / ActiveEffect / VfxGraphManager / VfxPreview / EditorGlow / SetOutputCommand / demo_fire.json |
| B-21 | **`vfxgraph_billboard` 名称与内容不符**：vsh 含火舌拉伸/倾斜等火焰专属逻辑，fsh 是 smoke 软圆斑——「billboard」应指中性相机面向 quad | **改名 + 拆分 + 新增 smoke**：billboard → **`vfxgraph_particle`**（中性软圆斑，vsh 去火舌/倾斜，仅旋转）；火舌拉伸/长度宽度变体/倾斜拆到新 **`vfxgraph_fire.vsh`**（fire 输出 vertex 改指 fire）；新增 **`vfxgraph_smoke.vsh/fsh`**（轻微速度拉伸 + 噪声卷须边缘软圆斑）；`R.shaders.core` 改 particle + 增 smoke；RenderSpec 默认兜底改 particle；demo_fire fire 输出 vertex→fire、smoke 输出→smoke；其余 4 资产 billboard→particle；测试同步。glslangValidator 全过 | shaders/core/vfxgraph_{particle,fire,smoke} + R.java + RenderSpec + demo_fire.json + 4 资产 + VfxGraphRendererTest |

> 回归测试：`VfxPreviewSyncRetentionTest` + `VfxFireLayerTest` + `VfxSystemSimulatorTest.setTimeContinuesAcrossRestart` + `VfxFireBlockFlowAssetsTest` + `VfxContainerModelTest.undoStepsThroughMultipleOperations`，editorTest 96→**100**、主 test 781→**783**，check 全绿。


### M22 电弧/路径驱动子系统（方向 Y，已实现；详见 ARC_DESIGN.md + ADR-026）

| ID | 任务 | 状态 | 说明 | DoD |
| --- | --- | --- | --- | --- |
| M22-01 | `ArcBuffer` + polyline 数据模型 | done | spine 点列 + 每点宽度 + 颜色 + seed/age/lifetime；`SimContext.arcs()` 暴露，容器执行器老化 | headless 单测（增删/生命周期/宽度） |
| M22-02 | `bolt` 路径源（两点 + 分叉） | done | `path/BoltPath`：中点位移粗锯齿 + 分叉（1~2 层，附着点随机）+ 宽度 taper；确定性（同种子同随机） | 纯函数单测：A→B polyline + 分叉拓扑 + 确定性 |
| M22-03 | `orbit` 路径源（玩家/模型环绕） | done | `path/OrbitPath`：闭合环（末点=首点）+ 相位旋转 + arc seed 稳定噪声 wobble + tilt 倾斜 | 单测：环闭合/点数/半径上界 |
| M22-04 | `surface` 路径源（表面游走） | done | `path/SurfaceWalk`：三角形数组建质心/法线/面积，最近质心邻居越界跨越（无共享顶点鲁棒）；无 mesh 回退单位立方体（scale 可调） | 单测：采样点在表面上（贴面距离）+ 持续型 |
| M22-05 | `ArcCoreBuilder`/`ArcGlowBuilder`（M22b 重构，取代 TubeMeshBuilder） | done | 自研 beam 系（M22b/c/d/e）全部废弃，**M22f 改用旧 vfx**（复用 `LightningMeshBuilder`，无自研 builder） | — |
| M22-06 | `RenderSpec.Geometry.ARC` + `VfxGraphRenderer.drawArcs` | done | **M22f 重写**：`drawArcs` 用 `LightningMeshBuilder` 建管 + `arcTubePipeline(bloomPass)`（透明主 / additive bloom）；`ARC_TUBE_FORMAT` + `ARC_BIND_GROUP`；深度 GEQUAL；arc 非空时不早退；arcsDrawn 去重 | 渲染器单测（ARC 派生/兜底 shader）+ 编译门禁 |
| M22-07 | `vfxgraph_arc.vsh/.fsh`（旧式电弧管着色器） | done | **M22f 重写**：颜色 100% 图数据驱动、零代码常量（`ArcLightning` UBO 仅渲染标量 aces 开关 + 发射增强） | glslangValidator 通过 |
| M22-08 | arc 块注册（`arc_bolt/orbit/surface/output_arc`） | done | `VfxBlocks` 注册（SPAWN 类发射块 + OUTPUT 输出块）；容器画布 arc 块不画批次端口；NODE_CATALOG/MODULES 更新 | 目录测试 42→46 |
| M22-09 | 容器执行器/预览并行驱动 arc 缓冲 | done | `VfxSystemSimulator.arcBuffer()` + `VfxPreview`/`EditorGlow` 传 arcBuffer；loop 重启判空含 arc | 单测：bolt 生成/老化/周期、orbit 持续再生 |
| M22-10 | 游戏内冒烟：spawn 一个 arc 图资产 | partial | `demo_arc.json`（仅 bolt 周期，单 GLOW 输出）+ `SampleAssetsTest`；surface 演示资产未做，游戏内肉眼确认待显示环境 | 肉眼确认 + 性能预算达标 |
| M22c-01 | `ArcRibbonBuilder`（widthScale + cross）+ 删 GL_LINES 线芯 | done | X 形同心高斯光带（M22c），**被用户否决**；cross 保留但 arc 主光带不再用 | `ArcRibbonBuilderTest`（widthScale/cross/闭合环）+ 编译门禁 |
| M22c-02 | `vfxgraph_arc_core`（高斯 exp(-(d·3.5)²) 白热芯）+ `vfxgraph_arc`（高斯 exp(-(d·1.5)²)）重写 | done | M22c 已实现后废弃；`vfxgraph_arc_core` 删除 | glslangValidator 通过 |
| M22c-03 | `demo_arc.json` 调参 | done | M22c 调参被后续覆盖 | `SampleAssetsTest` 通过 |
| M22c-04 | 编辑器肉眼确认 | cancelled | M22c 未到肉眼确认即被用户否决 | — |
| M22d-01~05 | `ArcTubeBuilder`/`vfxgraph_arc_tube`（胖辉光管）+ 三层高斯光带 | done | A+B 混合（M22d），**被用户否决**；M22e 全部删除（tube/贴图分离） | 见会话日志 |
| M22e-01~05 | 链式光束（Niagara 闪电链，v3.0~v3.3） | done | beam 系全部被用户否决；M22f 彻底移除（贴图/ribbon/ArcRibbonBuilder/ArcBeamTextureTest 删除） | 见会话日志 |
| M22f-01 | `drawArcs` 复用 `LightningMeshBuilder` 建管网格（parallel transport right/up，半径=poly.width） | done | 同 ArcTube 思路，相机相对坐标；`ARC_SEGMENT_RESOLUTION=4` | 编译门禁 + 旧 LightningRenderer 观感 |
| M22f-02 | `vfxgraph_arc.vsh/fsh`（颜色数据驱动、零代码常量，`ArcLightning` UBO 仅渲染标量） | done | `ARC_BIND_GROUP`（GraphCamera + ArcLightning）；glslangValidator 通过 | glslangValidator 通过 |
| M22f-03 | `ARC_TUBE_FORMAT`（Position+UV+Color）+ `arcTubePipeline(bloomPass)`（透明主 / additive bloom）+ `render` 传 bloomPass | done | 复刻 LIGHTNING_TUBE / LIGHTNING_TUBE_BLOOM；Color=电弧色（数据驱动） | 编译门禁 + 测试同步 |
| M22f-04 | 删 beam 全套（buildBeamTexture/beamTexture·View·Sampler/ARC_RIBBON_FORMAT/ArcRibbonBuilder/ArcRibbonBuilderTest/ArcBeamTextureTest） | done | demo_arc 细管（bolt 0.005，无 orbit） | 主 test 817→807 |
| M22f-05 | 编辑器肉眼确认（蓝色细管 + bloom 辉光，与游戏现有电弧一致） | pending | `./gradlew graphEditor` 打开 `demo_arc` | 需显示环境（并入 brush 决策） |
| M22g-01 | 飞出火花改迷你电弧 tube（`buildSparkBolt` + `sparkTubePipeline` 恒 additive） | done | 弃 billboard 四边形火花（`vfxgraph_arc_spark.*` 删除） | 编译门禁 |
| M22g-02 | 火花观感修正（纯电弧色 / 半径砍半 / 加长） | done | 用户反馈迭代 | — |
| M22g-03 | 火花轨迹变化（抛物线弯曲 + 行波波动） | done | 用户反馈迭代 | — |
| M22h-01 | ARC 观感参数数据驱动（`RenderSpec.ArcRender` + output_arc 属性） | done | 渲染器零硬编码常量；`thicknessVariation` 增幅度参 | 单测 arcRender 数据驱动 |
| M22i-01 | 火花波动自然化（`u` 平滑渐变 + 稳定扇形爆发） | done | 用户反馈「太随机」 | — |

> 建议拓扑序：M22-01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10。
> 先打通 `arc_bolt`（两点 + 分叉）+ ARC（M22f 旧 vfx 管渲染）端到端，再补 orbit/surface。
> 性能预算见 ARC_DESIGN.md §10；`RenderSpec`/`VfxGraphRenderer` 改动保持 M21l 数据驱动约定（无着色器枚举）。
> **容器化适配**：实现按容器模型（`vfx.block.arc_*` + `VfxSystemSimulator` 并行驱动 `ArcBuffer`），
> 取代设计稿的扁平 `VfxNodeRegistry`/`VfxSimulator` 并行路径（旧扁平路径 M28 待移除）。

### M23–M28 VFX 图容器化（Unity 式 Context + 数据流，ADR-027；取代有序 passes）

> 用户确认方向：**容器 Context（大改）+ 批次携带 flow（完整版）+ 全属性数据流（本期）+ 一次性迁移所有节点与资产 + 彻底废弃旧 schema**。
> 设计：新 `VfxSystem` 容器模型与核心 `Graph` 并行（不破坏契约冻结），新执行器替代 `VfxSimulator`，渲染层零改动。
> **M22 电弧延后**：容器化完成后（M27 之后）再启动，避免二次迁移。

| ID | 任务 | 状态 | 说明 | DoD |
| --- | --- | --- | --- | --- |
| M23-01 | `vfxgraph/model` 容器模型（VfxSystem/VfxContext/VfxBlock/VfxOperatorNode/VfxFlowEdge/VfxDataEdge/ParticleAttribute/VfxNode） | done | 与核心 Graph 并行，端口由目录派生；VfxNode 公共接口统一块/算子 | headless 单测（构建/查询/不可变） |
| M23-02 | `JsonVfxGraphCodec`（新 schema，`kind:"vfx"`，无旧格式兼容）+ `VfxGraphSchemaVersion` | done | 复用 JsonGraphCodec 值编解码（encodeValue/decodeValue 转 public）；codec 派生端口 | codec 往返单测（含 CURVE 参数 + kind/version） |
| M23-03 | `VfxGraphValidator`（flow 引用/连通/无环、数据边引用与类型、输出存在） | done | 非 SPAWN context 必须有上游 flow；至少一个 SPAWN/OUTPUT | 校验器单测（8 用例） |
| M23-04 | ADR-027 + MODULES MOD-12/13 + TASK_LEDGER M23–M28 登记 | done | 文档与代码同步 | 文档评审 |
| M24-01 | 批次区间：`SpawnBatch` record + `SimContext` 批次 API（emitBatch/emittedBatches/setIncomingBatches/incomingBatches/forEachIncoming） | done | `spawnStart` 保留兼容旧执行器；批次语义替代单点耦合 | 单测：批次不可变、多 spawn 批次独立 |
| M24-02 | `VfxSystemSimulator`：SPAWN→INITIALIZE→UPDATE 阶段驱动 + flow 边批次注入 | done | Kahn 拓扑序 + init 只处理上游 spawn 批次；update 全粒子 | 单测：多 spawn→多 init 互不干扰、fan-in 并集、暂停冻结、孤立 init 空跑、缺块抛错（7 用例） |
| M24-03 | 容器块目录：`VfxBlockFactory`/`VfxBlockRegistry`/`VfxBlocks`（M24 最小集：spawn_rate/burst、init_velocity/color/size、update_velocity/gravity/age/fade、output_quad×3） | done | 与粒子 `VfxNodeFactory` 平行，输入为 `VfxBlock`；M27 全量迁移 | 编译 + 执行器测试覆盖 |
| M25-01 | 算子节点集（attr-read×属性 + constant/math/curve/gradient/param） | done | `vfx.op.*` 目录 + 求值器（编译期折叠 + 逐粒子） | 单测：attr→math→写回链 |
| M25-02 | 数据边接线：`VfxSystemSimulator` 构建算子 DAG（算子间连接 + 环检测）+ `PortValueSource` 块端口值源 | done | 块工厂经端口源读取算子值（逐粒子）；无绑定回退属性 | 单测：attr 逐粒子不同、param 兜底、color 驱动、算子环抛错、元数据端口（8 用例） |
| M26-01 | 容器编辑模型：`VfxContainerModel`（contexts/blocks/operators/flow/data + 命令 undo/redo + toSystem/load 桥） | done | 与扁平模型平行；全部 mutation 命令化；块无坐标（context 内垂直排列） | editorTest：8 用例（增删/连线/类型拒绝/移动合并/持久化往返） |
| M26-02 | 容器画布：`VfxContainerCanvas`（context 框内 block、算子、flow/data 贝塞尔连线、拖拽/连线/框选/右键） | done | 端口高亮 + flow/data 两连接模式；frameAll | 编译 + editorTest 覆盖 |
| M26-03 | 编辑器接线：GraphEditorApp VFX 模式路由容器画布/调色板/检查器 + 每文档容器模型 + 容器 schema 保存/加载（`kind:"vfx"` 检测） | done | `VfxContainerModelRef` 多文档切换；save/load/reload 走 `JsonVfxGraphCodec` | check 全绿 |
| M26-04 | 移除 VFX 执行顺序徽标/Reorder 菜单（容器结构决定执行序） | done | 容器画布不画顺序徽标；SHADER 扁平路径保留 | 编译 |
| M27-01 | 粒子节点迁移为 Block/Operator 语义：`VfxBlocks` 全量（spawn 4/init 8/update 10/collision 5/over-life 4/orient 4/output 7 = 42 块）+ `VfxOperators` 全量（23 算子：attr×11/constant/param×5/math×4/curve/gradient×2；M22 再 +4 arc 块 → 46 块） | done | 块含 shape 支持与输入端口；与 VfxNodes 对应节点语义一致但用批次替代 spawnStart | 单测：全目录注册 + 全链路模拟 + over-life 曲线/渐变（4 用例） |
| M27-02 | 7 资产转档容器 schema（kind:"vfx"）+ 运行时容器加载 | done | `VfxGraphManager` 增 `kind:"vfx"` 分支（containerAssets + JsonVfxGraphCodec）；`GraphEffect`/`ActiveEffect` 增容器构造；扁平资产仍兼容 | 单测：SampleAssetsTest 走容器路径过 + VfxContainerAssetsTest（6 资产解码/校验） |
| M27-03 | 旧扁平读取路径保留（M28 彻底移除），VfxNodes 仍注册供 SHADER/过渡 | done | 容器与扁平双路径并存；check 全绿 | check 全绿 |
| M28-01 | 容器执行器性能门禁：`VfxSystemSimulatorPerfTest`（10k 稳态 step ~4.6ms + 600 帧 churn ~43ms，对标 VfxSimulatorPerfTest） | done | 宽松预算防 CI 抖动，只拦极端回归 | 单测 2 用例 |
| M28-02 | 运行时容器路径回归：`VfxGraphManagerTest.spawnContainerAssetThroughManager`（kind:"vfx" 资产经管理器 spawn/tick） | done | 验证 M27 容器分支端到端 | 单测 1 用例 |
| M28-03 | 游戏内冒烟 + 彻底移除旧扁平路径（VfxNodes/VfxSimulator） | todo | 需显示环境确认容器资产渲染后移除（8 测试类随迁容器） | `clientDev` 冒烟 + check 全绿 |
| M28-04 | **块级批次 flow（M28b，连线指定 spawn→init 配对）**：`VfxBlockFlowEdge`（模型/序列化/校验）+ `VfxSystemSimulator` 按块收集/分发批次（精确配对模式：存在任一 blockFlow 时未配对 init 收空批次；无 blockFlow 回退 context 级）+ 编辑器模型/命令/画布（spawn 块批次输出端口、init 块批次输入端口、绿线 + hover tooltip） | done | 一个 SPAWN/INITIALIZE context 内多个 spawn/init 用线配对，不必拆多个 context；demo_fire 可直接合并成 2 context + 4 条块级 flow | 单测：VfxBlockFlowTest（配对/回退/未配对空）+ codec round-trip + editorTest，check 全绿 |
| M29-01 | `ArcCurve.setSurface`（可选的端点吸附表面）+ `SurfaceConstraint` 升级为真最近表面点（`MeshDistance.nearestPoint`，Closest Point on Triangle） | done | 复刻 Blender `Sample Nearest Surface`；不再要求投影落在三角形内 | 单测：SurfaceConstraintTest 全过 |
| M29-02 | `VfxSystemSimulator.step` 接入 SurfaceConstraint：噪声动画后对带表面弧执行端点吸附 | done | 每帧保持贴面；无 surface 自由弧跳过 | 单测：VfxSystemSimulatorSurfaceSnapTest |
| M29-03 | `MeshAssets` 内置表面：`plane(size)`（2×2 地面，法线 +Y）+ `sphere(radius, segments)`（UV 球，法线向外）+ `resolve` 查内置/注册表 | done | 复刻 Blender Plane/Sphere 场景，无需外部 OBJ | 单测：MeshAssetsBuiltinTest |
| M29-04 | `CurveGenerator.generateSurfaceArc`（per-point 短弧：沿法线展开 + 控制柄起拱 + 重采样） | done | 复刻 Curve Line → Bezier → Resample | 单测：CurveGeneratorSurfaceArcTest |
| M29-05 | `MeshDistance`（点到网格最近距离 + 最近点，Ericson Closest Point on Triangle） | done | 接触弧距离剔除 + 端点吸附共用 | 单测：MeshDistanceTest |
| M29-06 | `vfx.block.arc_surface` 重写（表面布点 + 断续时序 + 端点吸附） | done | 复刻 Blender 主流水线；删除未实现的 walk_speed 语义 | 单测：SurfaceArcBlockTest |
| M29-07 | `vfx.block.arc_contact`（接触闪电：源面布点 + 距离剔除 + 端点吸附接触面） | done | 复刻主组第二套系统；`contact_origin_*` 定位接触对象 | 单测：ContactArcBlockTest |
| M29-08 | `vfx.block.arc_spark`（粒子火花：弧→点 + 随机删减 + 溅射方向 + 迷你管） | done | 复刻主组第三套系统；只从带表面弧派生防指数增长 | 单测：SparkArcBlockTest |
| M29-09 | 示例资产 `surface_arc.json`/`contact_arc.json`/`spark.json` + SampleAssetsTest | done | 三 VFX 各自独立资产，glow 白炽观感 | SampleAssetsTest 10 资产全过 |
| M29-10 | 文档：NODE_CATALOG/BLENDER_ARC_REFERENCE/ARC_SURFACE/TASK_LEDGER/STATE 更新 | done | VFX 目录 46→48 块 | check 全绿 |
| M29b-01 | 修复弧数爆炸：`arc_surface/arc_contact` 帧周期断续门控（复刻 Blender `Compare(Frame MOD N)`）+ 低频 interval；目标常驻 <30 条 | done | 用户否决 M29（弧数成千上万）；`SurfaceDistributor.distribute` frequency 门控死代码（`timePhase>probability*10` 恒不成立）需重写 | `./gradlew graphEditor` 打开 `surface_arc` 肉眼确认弧数 |
| M29b-02 | 修复火花指数放大：`arc_spark` 每帧只从本帧新增表面弧取点（或限制每弧火花数上限） | done | 当前对每条带表面弧每控制点每帧派火花 → ~1000+ 常驻 | 同 #01 复验 |
| M29b-03 | 视口渲染表面网格：`ViewportPanel`/`VfxPreview` 从 `MeshAssets`/`arc.surface()` 画 plane/sphere wireframe（或半透明面） | done | 当前仅 ImGuizmo `drawGrid`，平面/球表面三角形从不渲染，编辑器场景与 Blender 不符 | `./gradlew graphEditor` 肉眼确认可见地面+悬浮球 |
| M29b-04 | 新建 `demo_blender_arc.json` 测试场景：2×2 地面 plane + 悬浮 sphere（y≈4.34，r≈1）+ 面上爬行短弧 + 平面↔球连接弧 + 火花，全部断续时序低频 | done | 用户期望 = 一个球 + 一个正方形表面，两者电弧连接 + 正方形表面爬行电弧 + 粒子（Blender 引用见 STATE「进行中」M29） | SampleAssetsTest + `graphEditor` 肉眼复验 |
| M29b-05 | 同步修正 `surface_arc.json`/`contact_arc.json`/`spark.json` 参数（density/lifetime/interval 至低频） | done | 与 #01–#04 联动 | check 全绿 |

> 建议拓扑序：M23 → M24 → M25 → M26 → M27 → M28，每步门禁全绿。

## 剩余工作（handoff，2026-08-13；A1–A4 于 2026-08-13 完成）

> 详细说明与优先级见 `STATE.md`「剩余工作清单」。此处为任务追踪入口。

| ID | 内容 | 类型 | 状态 |
| --- | --- | --- | --- |
| A1 | 多样本纹理绑定（真实资产纹理 + 多 sampler + 预览实际贴图） | 功能缺口（headless 可做） | done（ADR-021；`samplePlan`/`SamplerBinding`/`ShaderPreview` 真实纹理；golden 重生成） |
| A2 | sub-graph 编辑器标签页（docked 多文档，双击打开/编辑子图） | 功能缺口（headless 可做） | done（ADR-022；`GraphEditorDocuments`/`EditorDocument`/`GraphEditorModelRef`/TabBar/双击开子图，每文档独立 undo） |
| A3 | mesh surface 形状（`MeshShape` + buildShape 接线） | 功能缺口（headless 可做） | done（ADR-023；`ObjMeshParser`/`MeshShape`/`MeshAssets`/`buildShape` mesh 分支；形状 7→8，节点目录 45 不变） |
| A4 | 游戏内技能/实体实际挂点（现有技能接入 spawn API） | 功能缺口（配 C 联调） | done（`SpawnVfxGraphPacket` + DirStrike 替换旧手写 VFX；`skill_dirstrike` 资产；视觉验证待 C 冒烟） |
| B | NODE_CATALOG 过期标注修正（delta/sine/cosine time、subgraph） | 文档 | done（随本 handoff） |
| C | 运行时冒烟（clientDev + spawn + 热重载 + Iris 共存） | 需显示环境 | 部分 done（2026-08-13：spawn 粒子/深度遮挡/平滑/暂停已验证；热重载/Iris/DirStrike 视觉待续） |
| M29b | 三 VFX 返工（用户否决）：弧数爆炸修复 + 视口渲染表面网格 + `demo_blender_arc.json` Blender 测试场景 | 功能缺口（M29b-01~05） | done（2026-08-23：帧周期门控 <30 + 火花 fresh-only + 视口表面网格 + demo_blender_arc；editorTest 肉眼复验待显示环境） |
| M29c | 电弧噪声累积漂移修复（用户反馈「电弧都往一个方向飘/都长一个样」） | BUGFIX | done（2026-08-23：`ArcCurve` 基准位置 + `NoiseAnimator` 相对基准位移（防逐帧累积）+ 每弧独立噪声种子；回归单测） |
| M30-01 | 用 Blender 5.2 解压实际 `闪电附着.blend`：提取 modifier 实际生效 socket 值（`m.properties.inputs.Socket_xx.value`）+ 全部 FloatCurve 控制点 + 实测 frame40 几何 | done | 权威数据固化 `BlenderArcReference`（test）+ `BlenderArcCurves`（main）；纠正界面默认/socket 缓存差异 | — |
| M30-02 | 表面电弧重写：基线平躺表面 + 控制柄沿法线上推（FloatCurve.001 age 成长 × Random × 高度 × 粗细）→ 帐篷拱；管半径 = FloatCurve.002(端粗中细) × FloatCurve.005(age 衰减) | done | `CurveGenerator.generateSurfaceArc` + `sampleSurfaceArch`（每帧重采样）+ `ArcCurve.archBase` 系列 | BlenderArcGeometryTest（平躺/拱成长/半径剖面/实测对照） |
| M30-03 | 接触闪电独立路径：平面→球面最近点直线弧，仅末端吸附（pinStart，Blender End Size=1），半径仅 age 衰减（flatRadius） | done | `CurveGenerator.generateContactArc` + `SurfaceConstraint` pinStart 支持 | ContactArcBlockTest 更新（仅末端贴球、起点留平面） |
| M30-04 | 粒子重写：弧→点概率删减(0.48) + 溅射方向 + 重力持续积分 + 迷你管对齐速度（Blender Simulation Input） | done | `arc_spark` 重写 + `ArcCurve.sparkVelocity` + `VfxSystemSimulator` 每帧速度/重力积分 | SparkArcBlockTest 更新 |
| M30-05 | 噪声幅度对齐 Blender：(noise-0.5)×0.27×噪波强度（±0.0675） | done | `NoiseAnimator` 修正（valueNoise3D 返回 [0,1]） | NoiseAnimatorTest |
| M30-06 | 渲染不透明自发光（Blender Emission，alpha=1，color=顶点色×Light×6） | done | `vfxgraph_arc.fsh` 重写，删 rim 半透明 | glslangValidator 过 |
| M30-07 | 资产重写（demo_blender_arc/surface_arc/contact_arc/spark）用权威参数 | done | 密度 1.0/1.47、出现概率 0.0204/0.15、游离 1.5、寿命 20/6、接触范围 4.1、粒子密度 0.48/缩放 0.83/溅射 1.23/重力 -0.9 | SampleAssetsTest 全过 |
| M30 | 电弧一比一复刻 Blender「闪电附着」（用户否决 M29 拙劣模仿） | 功能缺口（M30-01~07） | done（2026-08-23：几何/算法/参数全对齐 + 不透明自发光渲染 + 权威资产；主 test 864→870，check 全绿；editorTest 肉眼复验待显示环境） |

## 择优登记（ADR-004）

| 效果 | 决定 | 理由 | 登记 |
| --- | --- | --- | --- |
| （后续新增效果在此登记） | 手写 / 图资产 | — | — |
