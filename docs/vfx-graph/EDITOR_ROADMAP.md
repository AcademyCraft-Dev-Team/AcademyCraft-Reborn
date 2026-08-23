# 编辑器扩展路线图（Phase 2）

> **已完结**：Phase 2（M9–M16）及 A1–A4 已全部完成。本文件为历史路线图，保留作阶段规划记录；
> 后续工作见 `STATE.md` / `TASK_LEDGER.md`（M21 火焰、M22 电弧[方向暂停]、M23–M28 容器化、A 系列余量）。

> 本文档是 M0–M8（Phase 1，地基 + 最小链路）之后的**产品化扩展路线图**。
> Phase 1 已验证：图模型/编译/GLSL 生成/动态管线/CPU 粒子模拟/自持渲染全部可用，但编辑器
> 功能只覆盖约 1%，**尚未达到最小可用**。Phase 2 的目标是把它扩展为类 Unity 的可用编辑器。

## 1. 目标

### 1.1 总体目标

构建一个**类 Unity 的图形化 Shader Graph + VFX Graph 编辑器**，做到：

1. **端到端可用**：建图 → 编辑 → 实时预览 → 存读资产 → 游戏内生效，全流程无死角。
2. **编辑器生产力对齐 Unity**：undo/redo、复制粘贴、多选变换、右键菜单、快捷键、minimap、
   分组、docking 布局、项目/资产浏览器、曲线/渐变编辑器、视口 gizmo。
3. **节点目录对标 Unity**：Shader 图 ~80 节点、VFX 图 ~45 节点，覆盖数学/三角/向量/噪声/
   纹理采样/坐标/颜色/渐变、spawn/init/update/over-life/输出全谱系。
4. **渲染正确**：纹理采样、sampler 绑定、独立视口、双后端（OpenGL/Vulkan）、Sodium/Iris 兼容。
5. **可维护**：全量单测、抽象层扫描、文档、控制矩阵可追踪。

### 1.2 量化目标

| 指标 | Phase 1 基线 | Phase 2 目标 |
| --- | --- | --- |
| 代码量（src） | ~8k 行 | **≥ 30k 行** |
| Shader 节点 | ~27 | ≥ 80 |
| VFX 节点 | ~6 | ≥ 45 |
| 单测 | 63 | ≥ 300 |
| 编辑器 | 无 undo/无纹理/无曲线/无视口 | 全具备 |
| 游戏内 | 无挂点 | 可 spawn 图效果 |

### 1.3 「可用」的定义（DoD，按用户口径）

- 打开编辑器，能像 Unity 一样**纯鼠标完成**建图/连线/调参/预览，不依赖手写 JSON。
- 所有编辑操作**可撤销**（Ctrl+Z）。
- 纹理采样节点能在预览里看到实际贴图。
- 曲线/渐变节点有**可视化编辑器**（bezier 拖点 / 渐变色带）。
- VFX 图能在**独立视口**里播放/暂停/步进，看到粒子。
- 保存的图资产能在**游戏内**被技能/实体 spawn 出来。

## 2. 现状基线（Phase 1 交付）

见 `PROGRAM.md` 模块树。已实现：

- `graph/`：类型系统（含 STRING）、模型（record）、JSON 序列化+迁移、校验、编译（拓扑/死代码/常量折叠）
- `shader/`：GLSL codegen、~27 节点、动态管线（`precompilePipeline`）、std140 材质
- `vfxgraph/`：SoA 粒子缓冲、模拟器、6 节点、4 发射形状、自持 billboard 渲染、`GraphEffect`
- `graph/assets/GraphAssets`：资产缓存 + 热重载失效
- `editor/grapheditor/`：ImGui 画布（基础平移缩放/节点/连线/选择/框选/移动）、面板/检查器、
  全屏背景预览（shader/vfx 双模式）、文件存读
- `ImGuiBackend`：游戏/桌面共享 ImGui 后端（仅字体纹理 texID=1）

## 3. 关键缺口与技术风险（Phase 2 必须正面解决）

1. **纹理采样（最大功能缺口）**：当前 `ShaderNodes` 无 `texture.sample`，`UniformLayout` 不支持
   sampler。需：纹理资产加载 → sampler bind group → 预览缩略图。**同时** `ImGuiBackend` 目前只
   注册字体图集 texID=1，需扩展为**多纹理 ID 注册表**，否则无法在 ImGui 内显示预览纹理/视口。
2. **独立视口**：当前预览是"全窗口背景"，需改为 docked 视口 = 离屏 `RenderTarget` + blit 到
   dock rect（或注册进 ImGui 纹理表）。M4 曾刻意规避此难点，Phase 2 必须解决。
3. **曲线/渐变编辑器**：`Curve`/`Gradient` 目前仅数据载体，无采样与编辑器。需 bezier 关键帧 UI
   + 采样器（线性/步进/平滑插值）+ 渐变 ramp 色带编辑器。
4. **undo/redo**：`GraphEditorModel` 的 mutation 目前直接改集合。需改为**命令模式**（可逆命令栈），
   否则后续所有编辑功能无法撤销。这是 M9 的第一块砖。
5. **游戏内挂点**：自持渲染器已就绪，但缺少客户端 tick/render 循环接入（事件/Mixin）与
   技能/实体 spawn 路径。需在游戏运行时环境验证，不可盲写。

## 4. Phase 2 里程碑（M9–M16，合计 ~40k 行）

> 每里程碑含任务表：任务 ID、模块、文件、DoD、预估行数。任务 ID 沿用 `M<n>-<序号>`。

### M9 编辑器基建（~6k 行）

**目标**：编辑器操作可撤销、可复制、可多选变换；右键菜单 + 快捷键齐全。这是所有后续编辑功能的地基。

| ID | 任务 | 文件/产物 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M9-01 | 命令栈：`Command`/`UndoManager`（do/undo/redo，栈深 + 合并） | editor/command/ | 400 | 通用可逆命令框架 |
| M9-02 | `GraphEditorModel` 全部 mutation 改为命令（加删节点/加删边/移动/改属性/改参数/改输出） | canvas/GraphEditorModel.kt | 800 | 所有编辑可撤销 |
| M9-03 | 剪贴板：复制/粘贴/复制节点（序列化为 JSON snippet，含偏移粘贴） | editor/clipboard/ | 500 | Ctrl+C/V/D |
| M9-04 | 多选 + 框选增强 + 成组移动 + 对齐（左/中/右/上/中/下） + 均匀分布 | canvas/NodeCanvas.kt | 800 | 多选变换/对齐 |
| M9-05 | 右键上下文菜单（空白处加节点、节点处删除/复制/设为输出、边处删边） | canvas/context/ | 600 | 右键加节点 |
| M9-06 | 快捷键系统（删除/复制/粘贴/撤销/重做/框选全选/聚焦/加节点）+ 键位注册表 | editor/shortcut/ | 500 | 快捷键可用 |
| M9-07 | 搜索命令面板（Ctrl+P：搜索节点/命令） | editor/commandpalette/ | 500 | 可搜索执行 |
| M9-08 | 边重连（拖已连线端点改连他端口）+ 端口高亮 + 类型兼容即时反馈 | canvas/NodeCanvas.kt | 700 | 可重连 |
| M9-09 | 测试：undo/redo、剪贴板、对齐、命令栈 | 测试 | 1200 | 覆盖齐全 |

### M10 画布增强（~4k 行）

**目标**：可组织大图（分组、注释、minimap、吸附、缩放到适配），布局可持久化，有项目浏览器。

| ID | 任务 | 文件/产物 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M10-01 | 节点分组 frame（框住若干节点，随动） | canvas/frame/ | 600 | 可分组 |
| M10-02 | sticky note 注释（标题+正文+颜色） | canvas/note/ | 400 | 可注释 |
| M10-03 | minimap 缩略图（画布导航 + 点击跳转） | canvas/minimap/ | 500 | 可导航 |
| M10-04 | zoom-to-fit + 网格吸附（节点/端口吸附） | canvas/Camera2D.kt | 400 | 吸附可用 |
| M10-05 | docking 布局 + 面板显隐 + 布局持久化（保存窗口布局到文件） | editor/layout/ | 800 | 布局可存读 |
| M10-06 | 项目/资产浏览器（图资产树 + 双击打开） | editor/project/ | 700 | 可浏览打开 |
| M10-07 | 最近文件列表 | editor/project/ | 200 | 最近文件 |
| M10-08 | 测试 | 测试 | 400 | 覆盖 |

### M11 Shader 节点补全（~6k 行）

**目标**：Shader 节点目录 ≥ 80，覆盖数学/三角/向量/噪声/坐标/颜色/渐变/纹理采样。

| ID | 任务 | 文件/产物 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M11-01 | **纹理系统**：`TextureAssets`（加载 `Identifier`→`GpuTextureView`）、sampler bind group 支持、`texture.sample` 节点（含 tiling/offset） | shader/texture/ | 1500 | 预览可见贴图 |
| M11-02 | `ImGuiBackend` 多纹理 ID 注册表（供预览/视口显示任意纹理） | imgui/ImGuiBackend.kt | 800 | ImGui 显示任意纹理 |
| M11-03 | 数学全量：mod/frac/reciprocal/abs/sign/floor/ceil/round/trunc/pow/sqrt/exp/log/exp2/log2/min/max/saturate | shader/nodes/ | 800 | 每节点 codegen 单测 |
| M11-04 | 区间/缓动：smoothstep/step/remap/inverse-lerp | shader/nodes/ | 400 | 单测 |
| M11-05 | 三角/向量：tan/asin/acos/atan/atan2/degrees/radians/dot/cross/distance/reflect/refract/transpose | shader/nodes/ | 800 | 单测 |
| M11-06 | 噪声库：value/perlin/simplex/voronoi（2D/3D，helper 函数库） | shader/nodes/noise/ | 1200 | 各噪声单测 |
| M11-07 | 坐标/几何：world/object position、normal、view dir、camera pos、screen pos、UV、tiling/offset、fresnel | shader/nodes/ | 1000 | 单测 |
| M11-08 | 颜色/渐变：gradient ramp、HSV↔RGB、contrast、luminance、blend(mix/multiply/screen)、split/combine | shader/nodes/ | 900 | 单测 |
| M11-09 | 顶点阶段输出 + 自定义函数节点（inline GLSL） | shader/codegen/ | 500 | 支持顶点输出 |

### M12 黑板 + 子图 + 曲线/渐变编辑器（~4k 行）

**目标**：参数可分组/可设范围；支持子图；曲线与渐变有可视化编辑器。

| ID | 任务 | 文件/产物 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M12-01 | 黑板增强：参数分组、类型补全（含 sampler/curve/gradient）、默认/范围/说明 | inspector/PropertyInspector.kt | 700 | 分组编辑 |
| M12-02 | 曲线采样器：`CurveSampler`（线性/步进/平滑插值） + 曲线 → GLSL 生成（贴图或分段） | graph/type/ + shader/codegen/ | 700 | 曲线生效 |
| M12-03 | bezier 曲线编辑器（ImGui 拖关键帧、切线、增删） | editor/curve/ | 900 | 可拖点编辑 |
| M12-04 | 渐变 ramp 编辑器（色带 + 停靠点拖拽） + 渐变 → GLSL（或贴图） | editor/gradient/ | 900 | 可拖点编辑 |
| M12-05 | sub-graph 节点（嵌套图，输入/输出暴露为端口） | graph/subgraph/ | 700 | 子图可用 |
| M12-06 | 测试 | 测试 | 500 | 覆盖 |

### M13 VFX 节点补全（~6k 行）

**目标**：VFX 节点目录 ≥ 45，覆盖 spawn/init/update/over-life/输出全谱系。

| ID | 任务 | 文件/产物 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M13-01 | spawn：burst（一次性/周期）、按距离/时间发射、随机 seed | vfxgraph/nodes/ | 700 | 各 spawn 单测 |
| M13-02 | init 全量：position（形状）/velocity/color/size/rotation/lifetime/mass/randomize | vfxgraph/nodes/ | 900 | 单测 |
| M13-03 | update 力场：constant force/noise/turbulence/vortex/drag/damping | vfxgraph/nodes/ | 900 | 单测 |
| M13-04 | collision：平面/球/地面（反弹 + kill） | vfxgraph/nodes/ | 700 | 单测 |
| M13-05 | over-life：color/size/alpha/velocity over lifetime（曲线） | vfxgraph/nodes/ | 700 | 曲线采样单测 |
| M13-06 | orient：face camera/align velocity/fixed rotation/spin | vfxgraph/nodes/ | 400 | 单测 |
| M13-07 | 输出变体：quad/billboard、mesh、line/trail、ribbon（`VfxGraphRenderer` 扩展多种拓扑） | vfxgraph/render/ | 1200 | 多输出渲染 |
| M13-08 | 形状补全：cylinder/torus/circle edge/mesh surface | vfxgraph/shape/ | 500 | 形状单测 |

### M14 视口 overhaul（~4k 行）

**目标**：独立 docked 视口（非全屏背景），轨道相机 + gizmo + 暂停/步进/质量/统计。

| ID | 任务 | 文件/产物 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M14-01 | 离屏视口 `RenderTarget` + blit 到 dock rect（解决 M4 规避的难点） | editor/viewport/ | 800 | 视口独立 |
| M14-02 | 轨道相机（旋转/平移/缩放，VFX 视口）+ shader 视口适配 | editor/viewport/ | 600 | 可旋转 |
| M14-03 | gizmo（移动/旋转/缩放发射器位置）+ 网格地面 | editor/viewport/ | 800 | gizmo 可用 |
| M14-04 | 播放/暂停/步进/重置/循环 + 时间轴 | preview/VfxPreview.kt | 500 | 控制完整 |
| M14-05 | 统计 overlay（FPS、粒子数、draw call、帧耗时） | editor/viewport/ | 400 | 统计可见 |
| M14-06 | 质量档位（分辨率缩放、粒子上限） | editor/viewport/ | 300 | 可调档 |

### M15 运行时集成（~4k 行）

**目标**：游戏内可 spawn 图效果，参数绑定到游戏值，热重载，池化/剔除。

| ID | 任务 | 文件/产物 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M15-01 | 客户端效果管理器（tick + render 循环接入，自持渲染到主帧缓冲） | runtime/ | 800 | 游戏内渲染 |
| M15-02 | 图资产从游戏资源/数据包加载（复用 `GraphAssets` + 资源加载器） | runtime/ | 600 | 游戏内加载 |
| M15-03 | 技能/实体 spawn 图效果 API + 世界变换绑定（位置/朝向/缩放） | runtime/ | 800 | 可 spawn |
| M15-04 | 参数绑定到游戏值（实体速度/技能强度 → 图参数） | runtime/ | 600 | 参数绑定 |
| M15-05 | 热重载 watcher（文件变更 → 重新编译图） | runtime/ | 400 | 热重载 |
| M15-06 | 池化/剔除（视锥剔除、粒子上限、实例缓冲复用） | runtime/ | 800 | 性能达标 |

### M16 兼容/性能/发布审计（~4k 行）

**目标**：双后端正常、性能达标、文档/许可齐全、测试覆盖充分。

| ID | 任务 | 文件/产物 | 预估行 | DoD |
| --- | --- | --- | --- | --- |
| M16-01 | Sodium/Iris 兼容（自持渲染与两者共存、不冲突） | 兼容层 | 800 | 双后端正常 |
| M16-02 | 性能门禁（基准测试 + 粒子 10k 帧耗时） | 基准 | 600 | 达标 |
| M16-03 | 全量单测补全（≥ 300） | 测试 | 1500 | 覆盖 |
| M16-04 | 文档/许可审计 + 用户手册 | docs | 600 | 完整 |
| M16-05 | GLSL 黄金测试（图 → 期望 GLSL 快照） | 测试 | 500 | 快照通过 |

> **状态（2026-08-13）**：M16 全部 done，Phase 2 收尾完成。主 test 696 + editorTest 74，check 全绿。
> 用户手册：`USER_GUIDE.md`。

## 5. 并行拆分建议（多会话接力）

- **会话 A（编辑器 UI）**：M9 → M10 → M14，依赖 `GraphEditorModel` 命令化 + `ImGuiBackend` 多纹理。
- **会话 B（节点/内容）**：M11（纹理系统 + shader 节点）→ M12 → M13，依赖接口契约（`ShaderNodes`/`VfxNodes` 注册约定、`UniformLayout` sampler 扩展、`CurveSampler`）。
- **会话 C（运行时）**：M15 → M16，依赖 M13/M14 的渲染与节点。
- **关键顺序约束**：M9-02（命令化模型）必须先于 M9 其余项与 M10；M11-01/02（纹理+多纹理 ID）
  必须先于 M14（视口）；M12-02（CurveSampler）必须先于 M13-05（over-life 曲线）。
- 三会话共享 `TASK_LEDGER.md` 更新 + `DECISIONS.md` 记录，接口契约冻结于 `MODULES.md`。

## 6. 会话接力协议（不变）

1. **开**：读 `STATE.md` → `EDITOR_ROADMAP.md` → `MODULES.md` → `DECISIONS.md`；`git status`；
   `./gradlew test` 基线。
2. **做**：按 `TASK_LEDGER.md` 拓扑序取任务；实现 + 单测；跑 `tools/check_package_info.sh`、
   `tools/check_abstraction.sh`、`./gradlew test`。
3. **收**：更新 `STATE.md`/`TASK_LEDGER.md`；有决策写 ADR；提交消息带任务 ID；写三行接力备注。

## 7. 相关文件

- `NODE_CATALOG.md` —— 完整节点清单（实现清单）
- `PROGRAM.md` —— 主路线图（含 Phase 2）
- `TASK_LEDGER.md` —— 任务控制矩阵
- `STATE.md` —— 当前状态快照
- `MODULES.md` —— 模块清单与接口契约
- `DECISIONS.md` —— 架构决策记录
