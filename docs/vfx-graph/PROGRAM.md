# VFX Graph + Shader Graph 主路线图

本文档是整个图系统的**唯一事实源**（single source of truth）。任何参与开发的会话必须先读本文档，
再读 `STATE.md`（当前状态）与对应的 `MODULES.md`（模块清单），最后按 `TASK_LEDGER.md` 取任务。

> 术语见 `GLOSSARY.md`，架构决策见 `DECISIONS.md`。

## 1. 目标

构建类 Unity 的图形化 **Shader Graph** 与 **VFX Graph**：

- 节点图作者工具（独立桌面编辑器，复用 `src/editor` 的 `DesktopApplication`）
- 图 → 编译 → 运行时数据驱动渲染
- 与现有手写 VFX 共存；图系统**自持渲染**（`VfxGraphRenderer`，ADR-013，不桥接 `VfxManager`/`VfxPhase`）

## 2. 范围边界

| 维度 | 范围内 | 排除 |
| --- | --- | --- |
| Shader Graph | 效果/材质 fragment 着色器代码生成，服务 VFX 与全屏后处理 | 完整 PBR 材质图、替换原版方块/实体渲染 |
| VFX Graph | CPU 粒子/效果模拟 + GPU 实例化渲染 | GPU compute 模拟 |
| 编辑器 | 独立桌面编辑器 | 游戏内编辑器（后续可加，共享核心） |
| 与现有 VFX | 共存，按效果择优（见 DECISIONS.md ADR-004） | 强制迁移现有 8600 行手写效果 |

## 3. 硬性架构规则

### R1 — 只走图形 API 无关抽象层（最高优先级，违反即 fail）

只允许使用 `com.mojang.blaze3d.*` 中的 API 无关抽象接口与构建器：

**白名单**：`GpuDevice`、`RenderSystem`、`RenderPipeline` / `RenderPipeline.Builder`、`RenderPass`、
`GpuCommandEncoder`、`GpuBuffer` / `GpuBufferSlice`、`GpuTexture` / `GpuTextureView`、`GpuFormat`、
`VertexFormat`、`BindGroupLayout` / `BindGroupLayouts`、`PrimitiveTopology`、`BlendFunction`、
`DepthStencilState`、`ColorTargetState`、`ShaderSource`、`ShaderDefines`、`GlslPreprocessor`、
`CompiledRenderPipeline`。

**黑名单（任何模块不得引用）**：`GlStateManager`、`GlDevice`、`VulkanDevice`、`GpuDeviceBackend`、
`GlRenderPipeline`、`VulkanRenderPipeline`、`GlShaderModule`、`IntermediaryShaderModule`、`GlslCompiler`、
`com.mojang.blaze3d.opengl.*`、`com.mojang.blaze3d.vulkan.*`、LWJGL `GLxx`/`vk*`。

`tools/check_abstraction.sh` 做静态扫描，CI 挂钩。

### R2 — 动态管线编译走 `precompilePipeline`

着色器源语言是 GLSL（OpenGL 与 Vulkan 后端都通过 `ShaderSource` 接收 GLSL，Vulkan 内部用 `GlslCompiler`
转 SPIR-V）。动态管线流程固定为：

1. `GlslGenerator` 产出 GLSL 字符串；
2. `ShaderGraphPipeline` 构建 `RenderPipeline`，着色器 `Identifier` 使用**内容哈希路径**（`academy:graph/<sha256>`），
   避免 `shaderCache` 的 `(id, type, defines)` 键碰撞；
3. 显式调用 `GpuDevice.precompilePipeline(pipeline, DynamicShaderSource)` 编译并写入 `pipelineCache`；
4. 渲染时仅 `renderPass.setPipeline(pipeline)` 命中缓存。

**不写 Mixin，不碰 `defaultShaderSource`，不访问任何后端类。**

### R3 — 图核心与渲染无关、可单元测试

`graph` 核心（类型/模型/序列化/校验/编译）**不得依赖**客户端渲染类与 Minecraft 世界类；模拟与编译逻辑
纯函数化，JUnit 直接测试。`shader` 与 `vfxgraph` 模块才允许依赖渲染层。

### R4 — 接口优先契约

每个模块的公开 API 在实现前冻结为接口骨架（M1 已在 `graph/` 提交）。后续会话只依赖接口，不依赖实现细节。

### R5 — 序列化版本化

图资产 JSON 带 schema 版本号，所有破坏性变更写迁移规则（见 `serialize` 模块）。

## 4. 模块树与行数预算

```
src/main/java/org/academy/api/client/render/
  graph/                      # 共享地基：节点图核心 (M1-M2, ~7k)
    type/       值类型、值表示、隐式转换
    model/      节点/端口/边/图/黑板参数
    serialize/  GraphCodec(Gson)、schema 版本、迁移
    validate/   类型检查、环检测、校验器
    compile/    拓扑排序、DAG 执行计划、常量折叠
    registry/   NodeType、NodeRegistry、PortSpec、PropertySpec
  shader/                      # Shader Graph (M3-M4, ~18k)
    nodes/      math/input/texture/geometry/噪声/工具 节点目录
    codegen/    GlslGenerator、GlslWriter、Swizzle、表达式 IR
    pipeline/   ShaderGraphPipeline、DynamicShaderSource、GraphMaterial、UniformLayout
  vfxgraph/                    # VFX Graph (M5-M7, 自持渲染)
    sim/        ParticleBuffer(SoA)、Simulator、SimContext、SimNode
    nodes/      spawn/update/输出 节点目录 + VfxNodeFactory/Registry
    render/     GraphCamera、VfxGraphRenderer（自持 billboard，ADR-013）
    shape/      发射器形状
  graph/assets/  GraphAssets（资产加载/缓存/热重载失效）
  assets/  (GraphAssets 资源加载/缓存)

src/editor/kotlin/org/academy/desktop/grapheditor/   # 桌面编辑器 (M4/M6/M7)
  canvas/  palette/  inspector/  preview/  app/

测试 + 文档/生成
```

> Phase 1 已交付 ~8k 行（M0–M7）。**Phase 2（M9–M16，~40k 行）见 `EDITOR_ROADMAP.md`**，
> 目标是扩到 ≥30k 行、类 Unity 可用编辑器。

## 5. 里程碑与出口关卡

| 里程碑 | 范围 | 出口关卡（DoD） |
| --- | --- | --- |
| M0 地基工具 | 接力构件、控制矩阵、抽象层检查、包约定、接口骨架 | 全部构件就绪，`check_package_info` + `check_abstraction` + `gradle build` 通过 |
| M1 节点图核心 | 类型系统、节点/端口/边模型、JSON 序列化+版本迁移、类型检查+环检测 | 全量单测通过；示例图可往返序列化 |
| M2 图编译 | 拓扑排序、DAG 执行计划、常量折叠、黑板参数模型 | 非法图被拒；执行计划单测覆盖 |
| M3 Shader 代码生成 | GLSL 生成器 + 节点目录 + 动态管线注册 + 材质运行时 | 图→GLSL 编译成功，`precompilePipeline` 产出可用管线 |
| M4 编辑器 MVP + 预览 | 画布/节点/连线/属性检查器 + Shader 实时预览 | 可建图→改参→看 GLSL→看预览 |
| M5 VFX 模拟 | CPU 粒子系统 + 模拟节点目录 | 粒子生命周期正确，确定性可测 |
| M6 VFX 自持渲染 + 播放 | 实例化 GPU 渲染（自持，不接 VfxManager）+ 编辑器播放/步进 | 图 VFX 在编辑器自持渲染 |
| M7 资产管线 + 运行时集成（库层） | 资产缓存 + 运行时效果框架 + 参数覆盖 + 迁移接线 | 库层单测通过 |
| M8 兼容/性能/发布审计 | （并入 Phase 2 M16） | — |

### Phase 2（M9–M16，产品化）

| 里程碑 | 范围 | 预估行 | 出口关卡（DoD） |
| --- | --- | --- | --- |
| M9 编辑器基建 | undo/redo、剪贴板、多选变换/对齐、右键菜单、快捷键、命令面板、边重连 | ~6k | 所有编辑可撤销，Ctrl+C/V/D 可用 |
| M10 画布增强 | 分组 frame、note、minimap、zoom-to-fit、网格吸附、docking 持久化、项目浏览器 | ~4k | 可组织大图 |
| M11 Shader 节点补全 | 纹理系统 + 采样、数学/三角/向量/噪声/坐标/颜色/渐变节点 | ~6k | Shader 节点 ≥ 80 |
| M12 黑板+子图+曲线编辑器 | 参数分组、子图、bezier 曲线编辑器、渐变 ramp 编辑器 | ~4k | 曲线/渐变可编辑 |
| M13 VFX 节点补全 | spawn/init/update/over-life/orient/输出全谱系 | ~6k | VFX 节点 ≥ 45 |
| M14 视口 overhaul | docked 视口、轨道相机、gizmo、暂停/步进/质量/统计 | ~4k | 独立视口 |
| M15 运行时集成 | 游戏内挂点、技能/实体 spawn、参数绑定、热重载、池化/剔除 | ~4k | 游戏内可 spawn |
| M16 兼容/性能/发布 | Sodium/Iris 兼容、性能门禁、测试补全、文档/许可 | ~4k | 回归通过 |

### Phase 3（M22 扩展 + M23–M28 容器化，ADR-026/027）

| 里程碑 | 范围 | 预估行 | 出口关卡（DoD） |
| --- | --- | --- | --- |
| M22 电弧/路径驱动子系统 | 路径驱动电弧（两点/环绕/表面游走/分叉），CPU 约束 spine + GPU 观感（无线程），**M22f 改旧 vfx 管渲染（无 Ribbon fallback）** | ~5k | **done**（M22f/g/h/i）：arc 图资产端到端可 spawn，check 全绿；方向暂停（拟转 brush 子系统） |
| M23 VFX 容器模型+序列化 | `VfxSystem` 容器模型（Context/Block/Operator/Flow/DataEdge/ParticleAttribute）+ `JsonVfxGraphCodec`（新 schema）+ 校验器 | ~1.5k | codec 往返 + 校验器单测，check 全绿 |
| M24 容器执行器 | `VfxSystemSimulator` 阶段驱动 + `ParticleBuffer` 批次区间（替代 spawnStart） | ~1.5k | 多 spawn 批次独立单测，check 全绿 |
| M25 数据流算子 | attr-read + constant/math/curve/gradient/param 算子集 + 求值器 | ~2.5k | 逐粒子求值单测，check 全绿 |
| M26 容器编辑器 | VFX 画布重写（容器渲染/block 增删/flow+数据连线）+ PropertyInspector 适配 | ~4k | editorTest，check 全绿 |
| M27 节点+资产迁移 | 粒子节点 Block/Operator 化（42 块 + 23 算子）+ 7 资产转档 + 旧 schema 删除 | ~3k | SampleAssetsTest + check 全绿 |
| M28 运行时接线 | GraphEffect/VfxGraphManager/VfxPreview 切新执行器 + 游戏内冒烟 + 性能门禁 | ~2k | `clientDev` 冒烟 + check 全绿 |

> Phase 2 详细任务分解见 `EDITOR_ROADMAP.md`，节点实现清单见 `NODE_CATALOG.md`。
> M22 详细设计见 `ARC_DESIGN.md`，决策见 `DECISIONS.md` ADR-026，任务见 `TASK_LEDGER.md` M22。
> M23–M28 为 VFX 容器化（Unity 式 Context + 数据流，ADR-027），任务见 `TASK_LEDGER.md` M23–M28。

## 6. 编码约定

- Java 为主（对齐现有 `render/vfx`），编辑器 UI 用 Kotlin（对齐 `src/editor`）。
- 每个新包加 `@NullMarked package-info.java`（`tools/check_package_info.sh` 强制）。
- 序列化用 Gson（对齐 `UiLayoutCodecs`），schema 带版本号。
- 图编译/校验/模拟逻辑纯函数化、无客户端依赖，JUnit 直接测。

## 7. 接力（Handoff）协议

每个会话：

1. **开**：读 `STATE.md` → 相关 `MODULES.md` → `DECISIONS.md`；`git status`/`git log`；`./gradlew test` 基线。
2. **做**：按 `TASK_LEDGER.md` 拓扑序取任务，对照接口契约实现；写测试；跑 `tools/check_package_info.sh`、
   `tools/check_abstraction.sh`、`./gradlew test` 与 build。
3. **收**：更新 `STATE.md` 与 `TASK_LEDGER.md`；有决策写 ADR；提交消息带任务 ID（如 `M1-03: ...`）；
   写三行接力备注（做了什么 / 下一步 / 坑）。

## 8. 相关文件

- `EDITOR_ROADMAP.md` —— Phase 2 扩展路线图（目标 + M9–M16 详细任务）
- `ARC_DESIGN.md` —— M22 电弧/路径驱动子系统设计
- `VFX_CONTAINER.md` —— M23–M28 VFX 容器化（Unity 式 Context + 数据流）设计
- `NODE_CATALOG.md` —— 完整节点清单（shader/vfx 实现清单）
- `MODULES.md` —— 模块清单与接口契约
- `TASK_LEDGER.md` —— 任务控制矩阵（追踪枢纽）
- `STATE.md` —— 当前状态快照（每会话更新）
- `DECISIONS.md` —— 架构决策记录（ADR）
- `GLOSSARY.md` —— 术语表
