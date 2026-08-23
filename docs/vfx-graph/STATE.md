# 当前状态快照

> 每会话结束必须更新本文件。按「已完成 / 进行中 / 下一步 / 阻塞 / 关键决策」组织。

## 已完成

- **⚠️ M30 电弧一比一复刻 Blender「闪电附着」（2026-08-23 实现，但用户否决——电弧曲线生成有底层问题，已由 M30b 按实际 .blend 权威数据修复，见下）**：
  - **权威数据**：用 Blender 5.2 解压实际 `闪电附着.blend`，提取 modifier 实际生效 socket 值
    （`m.properties.inputs.Socket_xx.value`，与界面默认/socket 缓存均不同）与全部 FloatCurve 控制点、
    实测 frame40 几何（表面弧 spine/半径/弧数）。
  - **表面电弧（`arc_surface`）重写**：`CurveGenerator.generateSurfaceArc` 改复刻 Blender 流水线——
    基线平躺表面（Curve Line + Align axis=X）沿切平面随机方向、Bezier 控制柄沿**法线**上推
    （FloatCurve.001 age 成长 × Random 0.4~1.2 × 电弧高度 × 电弧粗细）、重采样 12 点 → 帐篷拱；
    管半径 = FloatCurve.002(端粗中细) × FloatCurve.005(age 衰减) × 基准（实测 0.0024~0.0034）。
    弧拱基线存 ArcCurve，`VfxSystemSimulator` 每帧 `sampleSurfaceArch` 重采样（复刻 Blender 每帧求值）。
  - **接触闪电（`arc_contact`）独立路径**：`CurveGenerator.generateContactArc`——从表面点 P 到接触面
    最近点 N 的**直线弧**（无拱，curve=0），仅末端吸附接触面（pinStart，Blender End Size=1），
    半径仅 age 衰减（flatRadius，FloatCurve.009）。接触范围 4.1、Density 1.47、生命周期 6、发光强度 3.0。
  - **粒子（`arc_spark`）重写**：弧→点（Count=10）概率删减（保留 0.48）+ 溅射方向 + 重力 + 迷你管对齐
    速度（Align Rotation to Vector），`ArcCurve.sparkVelocity` 每帧速度/重力积分（Blender Simulation Input），
    半径 0.005×Random(0.2~0.6)，长度 = 实例 Scale(0.01~0.03×粒子缩放)×FloatCurve.003 生命衰减。
  - **渲染**：`vfxgraph_arc.fsh` 改**不透明自发光**（Blender Principled BSDF Emission，alpha=1，
    color = 顶点色 × (Light×6)，aces 开关保留），删 rim 半透明。
  - **资产重写**：demo_blender_arc/surface_arc/contact_arc/spark 全部用权威参数
    （密度 1.0/1.47、出现概率 0.0204/0.15、粗细 0.78、宽度 1.0、高度 1.0、游离 1.5、亮度 1.0、
    噪波 0.5、寿命 20/6、接触范围 4.1、粒子密度 0.48、缩放 0.83、溅射 1.23、重力 -0.9）。
  - **验证**：新 `BlenderArcGeometryTest`（6 用例：平躺/拱成长/半径剖面/基线/曲线采样/实测对照）+ 更新
    CurveGeneratorSurfaceArcTest/ContactArcBlockTest/SparkArcBlockTest 断言为新语义；arc 相关测试全绿，
    `./gradlew check` 全绿，glslangValidator 过。
-   **文档**：STATE.md 更新 + BLENDER_ARC_REFERENCE.md 补权威参数表（见下）。

- **M30b 电弧一比一复刻修复（2026-08-23，用户否决 M30「看起来直线、无弯折、持续」，判定为底层顶点生成问题；用实际 `闪电附着.blend` 提取全部权威数据后修复；主 test 870→881，check 全绿）**：
  - **权威数据补全**：Blender 5.2 解压 `~/Downloads/闪电附着.blend`，提取 6 条此前缺失的 FloatCurve 控制点（FloatCurve 无名 pa 脉冲/`.007` shapep 同脉冲、`.004` Light 亮度先亮后灭、`.009` 接触半径/发光生命、`.006`/`.008` 寿命沿弧变化），固化进 `BlenderArcCurves`/`BlenderArcReference`。
  - **根因（5 处 Blender 行为缺失/错位）**：① 噪声弱 ~9 倍——噪声乘数原是常量 `pa=0.27×0.27`，Blender 是逐点 `pa=脉冲曲线(spline因子)×Random[0.4..2.2]×噪波强度(0.5)`（端点 0、中段满幅）→ 弧纹丝不动读作"直线无弯折"；② **无仿真区爬行**——Blender Set Position 每帧 `cross(随机,法线)×Random[0.01..0.03]×游离速度` 让弧基座滑移游走，移植缺失 → 弧冻在原地"持续"；③ 无 age 亮度闪烁（Blender Light=FloatCurve.004×亮度+0.33×亮度 ×6 先亮后灭）→ 亮度恒定；④ 弧跨度恒等（Blender 实例 Scale=Random[0.4..1.2]×电弧宽度）→ 大小雷同；⑤ 控制柄多乘电弧粗细（Blender 不含）→ 拱偏矮 22%。外加噪声漂移语义错误（原 `Position+time×游离速度`，实为 `Position+(1,1,1)×场景秒`，游离速度只驱动爬行）。
  - **修复**：`NoiseAnimator` 改逐点 `(noise−0.5)×pa×噪波强度`、漂移=场景秒；`sampleSurfaceArch` 每点写 pa（脉冲×Random[0.4..2.2]，确定性）、span=height×实例随机跨度、handle 去 curve、接触半径走 `.009`、弧基座中心+累积 wander；`VfxSystemSimulator.wanderArcBase` 仿真区爬行（端点随后 SurfaceConstraint 拉回）；`VfxBlocks` arc_surface/arc_contact 接线 `noise_strength`/`drift_speed` 到弧（修接线断裂）；`VfxGraphRenderer.arcLight` 按 age 烘焙亮度闪烁进顶点色（surface=.004+0.33 / contact=.009 / spark=.003）；demo_blender_arc 接触色 1→0.5 灰补偿共享 ×6 发射（Blender touch 直连 3.0）。
  - **验证**：新 `M30FaithfulReplicationTest`（10 用例：pa 脉冲端点 0/噪声幅度对齐 Blender/漂移不乘游离速度/跨度随机 0.4~1.2×/仿真区爬行累积+端点拉回/Light 先亮后灭/handle 不含 curve/接触半径 .009/接触噪声端点 0/spawn 块噪声接线）；更新 BlenderArcGeometryTest span 语义（实例随机跨度）。主 test 870→881 + editorTest 101，check 全绿。**编辑器肉眼复验待显示环境**：`./gradlew graphEditor` 打开 `demo_blender_arc`——弧基座游走爬行、中段逐点抖动弯折、大小各异、亮度先亮后灭闪烁、平躺→帐篷拱成长、接触弧顶端连球、火花溅射衰减。

- M0-01 接力构件已创建（PROGRAM/DECISIONS/GLOSSARY/MODULES/TASK_LEDGER/STATE）
- M0-02 抽象层黑名单扫描工具
- M0-03 graph/ 核心接口骨架（MOD-01~06 契约冻结）
- M0-04 构建门禁验证通过
- **M1 节点图核心**（M1-01~09 全部 done，28 个单测通过）
  - `graph.type`：`Value`（sealed + record 变体）、`Curve`/`Gradient`、`TypeConversions`
  - `graph.model`：`Port`/`Edge`/`GraphNode`/`Graph`/`GraphParameter`（record）
  - `graph.registry`：`NodeType`/`PortSpec`/`PropertySpec`（record）+ `SimpleNodeRegistry`
  - `graph.serialize`：`JsonGraphCodec` + `GraphMigrations` 迁移链
  - `graph.validate`：`DefaultGraphValidator`（类型检查 + 环检测）
- **M2 图编译**（M2-01~03 全部 done，34 个单测通过）
  - `graph.compile`：`DefaultGraphCompiler`（拓扑排序 + 死代码消除 + 常量折叠）、
    `NodeEvaluator`、`CompiledGraph`（record）、`GraphCompileException`
- **M3 Shader 代码生成**（M3-01~07 全部 done，累计 46 个单测通过）
  - `shader/codegen`：`Expr`/`GlslType`/`Swizzle`/`GlslNames`/`GlslWriter`/`GlslLiterals`/`GlslGenerator`
  - `shader/nodes`：`ShaderNodes`（~27 节点：constant/color/time/uv/param、math、combine/split、noise、output.color）
  - `shader/pipeline`：`UniformLayout`（std140）、`DynamicShaderSource`（内容哈希）、
    `ShaderGraphPipeline`（`precompilePipeline` 预编译）、`GraphMaterial`（含 std140 写入）
  - 新增 `ValueType.STRING`（ADR-009）；texture 采样延后 M4（ADR-010）
- **M4 编辑器 MVP + 预览**（M4-00~07 全部 done，编译门禁通过）
  - `ImGuiBackend`（游戏/桌面共享，抽象层渲染）+ `EditorApp` ImGui 钩子 + `DesktopUiHost` 接入（ADR-011）
  - `editor/grapheditor`：`GraphEditorApp`（三段布局）、`GraphEditorModel`/`Camera2D`/`NodeCanvas`、
    `NodePalette`、`PropertyInspector`、`ShaderPreview`（全窗口实时预览）
  - 新增 `graphEditor` 运行任务
- **M5 VFX 模拟**（M5-01~05 全部 done，累计 56 个单测通过）
  - `vfxgraph/sim`：`ParticleBuffer`（SoA + swap-remove + 扩容）、`SimContext`、`SimNode`、`VfxSimulator`
  - `vfxgraph/nodes`：`VfxNodeFactory`/`VfxNodeRegistry`/`VfxNodes`（spawn_rate/velocity/gravity/age/fade/output_point）
  - `vfxgraph/shape`：`EmitterShape` + Point/Sphere/Box/Cone
  - 设计决策 ADR-012（有序 passes 非数据流）；曲线/梯度/碰撞/mesh 延后 M6
- **M6 VFX 自持渲染 + 播放**（M6-01~04 全部 done，累计 58 个单测通过）
  - `vfxgraph/render`：`GraphCamera`、`VfxGraphRenderer`（专用 billboard 管线，抽象层，自持 UBO）
  - `editor/grapheditor/preview/VfxPreview.kt`（播放/步进/重置）；`GraphEditorApp` Shader/VFX 模式切换
  - 设计决策 ADR-013（不桥接现有 VFX 系统，取代 ADR-004 桥接方案）
- **M7 资产管线 + 运行时集成（库层）**（M7-01~04 全部 done，累计 63 个单测通过）
  - `graph/assets/GraphAssets`（加载/缓存/失效热重载）；`vfxgraph/GraphEffect`（运行时效果 + 参数覆盖）
  - 版本迁移经 `JsonGraphCodec` 迁移链接入并测试
  - 游戏内实际挂点（技能/实体 spawn、客户端 tick/render、文件监听）延后（需游戏运行时环境）
- **M9 编辑器基建**（M9-01~09 全部 done，editorTest 49 个单测通过）
  - `command/`：`Command`/`CompositeCommand`/`UndoManager`（do/undo/redo、栈深 100、mergeKey 合并）
  - `GraphEditorModel` 全部 mutation 命令化（加删节点/连断边/重连/移动/属性/参数/输出，移动与属性拖拽合并为单命令）
  - `clipboard/GraphClipboard` + `PasteNodesCommand`（JSON snippet 复制/粘贴/复制(D)，复用 JsonGraphCodec）
  - `canvas/AlignOps` 对齐/分布（纯函数）；`NodeCanvas` 多选成组移动 + 右键菜单请求 + 边重连 + 端口高亮/兼容反馈
  - `shortcut/ShortcutRegistry` 快捷键注册表；`commandpalette/CommandPalette` Ctrl+P 命令面板
  - 菜单栏 Edit/Arrange、画布/节点/边右键弹窗；`editorTest` source set 接入 `check` 门禁
- **M10 画布增强**（M10-01~08 全部 done，editorTest 累计 68 个单测通过）
  - `document/`：`EditorMetadata`（FrameData/NoteData/camera/panels）+ sidecar `EditorMetadataCodec`（ADR-015）
  - `GraphEditorModel` 增 frames/notes + 10 个命令类；frame 拖拽带动框内节点/右下角缩放；sticky note 编辑
  - `canvas/Minimap` 缩略图 + 点击跳转；`Camera2D.frameToBounds` + 网格吸附（Ctrl+G 开关）
  - `app/GraphEditorApp` 重写为 ImGui `dockSpace`（Canvas 无背景，预览透出）+ 可停靠面板 + View 菜单显隐 +
    布局经 `imgui-graph.ini` 持久化
  - `project/`：`ProjectBrowser` 资产树（双击打开）+ `RecentFiles` LRU（File>Recent）
  - 元数据持久化走 sidecar：核心 `<name>.json` 不变，运行时 `GraphAssets` 零改动
- **M11 Shader 节点补全**（M11-01~09 全部 done，主 test 累计 575 单测通过）
  - **纹理系统**：`texture.sample` 节点（采样 `Sampler0`）+ `ShaderGraphPipeline` SAMPLER0 bind group +
    `ShaderPreview` 绑定棋盘格预览贴图；SAMPLER 参数从 std140 跳过（ADR-016）
  - `ImGuiBackend` 多纹理 ID 注册表（M11-02）：字体 ID 1，任意纹理自 2 起，`renderDrawData` 逐命令切换纹理
  - **节点目录 27 → 83**（≥80 达标）：数学全量/区间缓动/三角向量/噪声库(value/perlin/simplex/voronoi)/
    颜色渐变(hsv/contrast/luminance/blend/gradient ramp)/坐标输入(UV 近似)/自定义函数
  - 坐标/几何为全屏 quad 预览近似；transpose（无矩阵类型）与顶点输出（固定顶点阶段）延后
- **M12 黑板+子图+曲线/渐变编辑器**（M12-01~06 全部 done，主 test 597 + editorTest 71 通过）
  - `Curve.Keyframe` 扩为切线+插值模式（ADR-017）；`CurveSampler`/`GradientSampler`（CPU）+ `CurveGradientGlsl` 分段 GLSL
  - `curve.sample`/`gradient.sample` 节点；`GlslGenContext` 增 `curve(id)`/`gradient(id)`；`UniformLayout`/`GraphMaterial` 跳过非 uniform 参数
  - `editorcurve/CurveEditor`（拖关键帧/切线/增删）+ `gradient/GradientEditor`（色带/停靠点），docked 面板，可撤销
  - 黑板类型补全（VEC2/VEC4/SAMPLER/CURVE/GRADIENT）+ 范围编辑 + 分组（sidecar `paramGroups`，ADR-018）
  - sub-graph 编译内联：`SubGraphRegistry`/`SubGraphFlattener` + `subgraph` 节点动态端口 + `ShaderPreview` 展开
- **M13 VFX 节点补全**（M13-01~09 全部 done，主 test 累计 613 单测通过）
  - `ParticleBuffer` 增 rotation/mass/trail 历史；`SimContext` 增 spawnStart/curve/gradient；`VfxSimulator` 穿参数（ADR-019）
  - **VFX 节点 6 → 40**：spawn（burst/periodic/distance）、init 全量（position/velocity/color/size/rotation/lifetime/mass/randomize）、
    力场（force/noise/turbulence/vortex/drag/damping）、collision（plane/sphere/ground）+ kill/bounds、
    over-life（color/size/alpha/velocity，引用曲线/渐变参数）、orient（face_camera/velocity/fixed/spin）、
    输出变体（point/quad/mesh/line/ribbon）
  - `VfxGraphRenderer` 4 拓扑管线：旋转 billboard / 实例化 mesh / trail 折线 / ribbon 条带
  - 形状补全：`CylinderShape`/`TorusShape`/`CircleEdgeShape`（mesh surface 延后 M15）
- **M14 视口 overhaul**（M14-01~07 全部 done，editorTest 累计 76 单测通过）
  - **独立 docked 视口**：`ViewportPanel` 离屏 `TextureTarget` + `ImGuiBackend` 多纹理 ID + `ImGui.image`（UV 翻转），
    全窗口背景预览移除（`DesktopEnvironment.imguiBackend` 注入）
  - `OrbitCamera` 轨道相机（yaw/pitch/distance，左键旋转/右键平移/滚轮缩放）
  - ImGuizmo translate gizmo 编辑发射器 origin（可撤销）+ 网格地面
  - VFX 播放/暂停/步进/重置/循环 + 时间显示；统计 overlay（FPS/ms/粒子数）；质量档位（分辨率缩放 0.5x~2x）
- **M15 运行时集成**（M15-01~06 全部 done，主 test 642 + editorTest 74 通过）
  - `vfxgraph/runtime/`：`VfxGraphManager`（单例：tick/renderFrame/spawn/reload + 共享 `VfxNodeRegistry` +
    `GraphAssets` + 按拓扑渲染器池）、`ActiveEffect`（世界变换 + 实体跟随 + 存活绑定 + 生命周期）、
    `EffectBudget`（粒子上限 + 距离/视锥剔除）、`VfxGraphAssetLoader`（资源重载监听，F3+T 热重载）、
    `GraphFileWatcher`（dev WatchService，`run/vfxgraph/`）
  - **接线**：`AcademyCraftClient` init/tick/close + `/academy vfx spawn <graph> [x y z]`；`MixinLevelRenderer.render`
    `VfxManager.renderFrame()` 后渲到主 RT 颜色视图（`GraphCamera.fromGameCamera`）
  - **存活参数（ADR-020）**：`SimContext`/`VfxSimulator.setLiveParam`（不重建）+ 驱动节点 `param` 属性 +
    5 个 `vfx.param_*` 节点（VFX 节点目录 40 → **45**，≥45 达标）；`VfxGraphRenderer` 增 `WorldTransform`
  - **示例资产**：`assets/academy/vfxgraph/demo_burst/fountain/ribbon.json`（SampleAssetsTest 验证）
- **M16 兼容/性能/发布审计**（M16-01~05 全部 done，主 test 696 + editorTest 74 通过）
  - **Iris 兼容**：`MixinLevelRenderer` 的 VFX 图 renderFrame 包 `IrisCompat.runWithBypass`（与 VfxManager/PostEffect 同款）；
    清理 `AcademyCraftClient` 死 import；`VfxGraphIrisCompatTest`
  - **性能门禁**：`VfxSimulatorPerfTest`（10k 稳态 step 最坏 ~4ms）+ `ParticleBufferPerfTest`（10k spawn/kill）；
    `VfxGraphRenderer` 消除每帧分配（`worldScratch`/`TRAIL_FORMAT`/`CLEAR_COLOR`）
  - **单测补全**：serialize/subgraph/codegen/pipeline/runtime/nodes/render 缺口（~15 新测试类）
  - **文档**：新 `docs/vfx-graph/USER_GUIDE.md`（用户手册）+ README 补章节 + 死链修复 + 许可审计（图系统全自研无新增依赖）
  - **GLSL 黄金测试**：`GlslGoldenTest`（6 图精确快照，`-Dgolden.update=true` 更新）+ `src/test/resources/shader/golden/*.glsl`
- **M17 多样本纹理绑定（A1）**（主 test 704→712）
  - `SamplerBinding` record + `GlslGenerator.samplePlan`（SAMPLER 参数 + texture.sample 属性 → `Sampler0..N-1` 去重分槽）
  - `GlslGenContext.samplerName` + `texture.sample` 读属性解析槽位；`assembleFragment` **只发实际用到的 sampler**（去死 uniform）
  - `ShaderGraphPipeline` 按槽位数动态 `withSampler`；`UniformLayout.samplers()`/`GraphMaterial.samplerBindings()`
  - `ShaderPreview` 注入 `DesktopEnvironment.loadTexture`，逐槽位加载真实资产纹理（品红兜底），删棋盘格（ADR-021）
- **M18 MeshSurface 形状（A3）**（主 test 712→723）
  - `ObjMeshParser`（OBJ v/f 纯解析 + 扇形三角化）+ `MeshShape`（面积加权采样 + barycentric + 单位立方体兜底）
  - `MeshAssets` 注册表 + `buildShape` `case "mesh"`（`mesh`/`mesh_scale` 属性）接线；形状 7→8 全完成（节点目录 45 不变，ADR-023）
- **M19 子图编辑器标签页（A2）**（editorTest 74→81）
  - `GraphEditorDocuments`/`EditorDocument`（**每文档独立模型 + undo**）+ `GraphEditorModelRef`（6 个消费类零重构切换）
  - Canvas 窗口顶部 TabBar 切换/关闭；共享 `SubGraphRegistry` 重建推给所有模型 + 预览；**双击 subgraph 节点**/右键「Open Sub Graph」打开子图资产为新文档（ADR-022）
- **M20 游戏内技能挂点（A4）**（主 test 723→722，替换手写 VFX 净减）
  - `SpawnVfxGraphPacket`（assetId/position/followEntityId/scale/float params，注册 `PacketTypes`）+ `VfxGraphManager.DIR_STRIKE_ASSET`
  - DirStrike 替换：`executeStrike` 广播图资产，移除 `DirStrikeVisualPacket` + `DirStrikeGround{Data,Effect,Renderer,VfxClient}` + 对应测试（8 文件）
  - 新资产 `skill_dirstrike.json`（param 驱动 size，过 SampleAssetsTest）；缺失时客户端静默兜底不影响技能
- **审计修复（2026-08-13）**：javadoc 两处既有错误修复（JsonGraphCodec 链接、SubGraphFlattener 标签嵌套），build 门禁恢复全绿
- **M29 三 VFX 返工（M29b，2026-08-23，弧数爆炸/场景不符/无测试场景三点全修复；主 test 859→863）**：
  - **帧周期断续门控（M29b-01）**：`SurfaceDistributor.distribute` 删除死代码门控（`timePhase>probability*10` 恒不成立）；
    `arc_surface`/`arc_contact` 增 `frame_period`（帧，默认 3）/`fps`（默认 30）——`frequency>0` 时只在
    `frame%frame_period==0` 且非本周期已 spawn 的帧 spawn 一批（复刻 Blender `Compare(Frame MOD 0.03) EQUAL 0`），
    `frequency<=0` 保留 legacy 每帧 spawn（兼容旧资产/测试）→ **稳态弧数 ~450 → <30**
  - **火花指数放大修复（M29b-02）**：`ArcCurve` 增 `fresh` 标记（`ArcBuffer.add` 置 true、`advance` 开头清全量）；
    `arc_spark` 只处理 `fresh` 且带表面的弧 + 每弧 `max_sparks` 上限（默认 3）→ 旧 ~1000+ 常驻 → 有界；
    另修复 `ArcBuffer.add` 复用槽位未重置 `age` 的潜在 bug（回收弧立即死亡）
  - **视口渲染表面网格（M29b-03，"和blender一样" 实体着色表面）**：`VfxGraphRenderer` 增 `SurfaceMesh` +
    `drawSurfaces` + `vfxgraph_surface.vsh/fsh`（半透明材质色三角面，相机相对烘焙同电弧）+ `surfacePipeline`；
    `render()` 增可选 surfaces 参数（spec 循环前先画）；`VfxPreview.collectSurfaces` 从容器模型扫描
    `arc_surface`/`arc_contact` 的 `mesh`/`contact_mesh` + origin → `MeshAssets.resolve` → 编辑器可见 2×2 地面 + 悬浮球
  - **新测试场景（M29b-04）**：`demo_blender_arc.json`（地面 plane + 悬浮 sphere(0.52/4.34/0.38) +
    `arc_surface` 面上爬行短弧 + `arc_contact` 平面↔球连接弧 + `arc_spark` 火花，全断续低频，单 GLOW 输出白炽）；
    接入 SampleAssetsTest（10→11 资产）
  - **既有资产同步（M29b-05）**：`surface_arc.json`/`contact_arc.json`/`spark.json` 参数调至低频（density 1.2/
    frame_period 3/lifetime 0.5/max_sparks 3）
  - 单测：新 `M29bArcGatingTest`（帧周期门控稳态 <30 / frequency=0 legacy / spark fresh-only 有界 / 火花不递归），
    主 test 859→**863** + editorTest 101，check 全绿；glslangValidator 过
- **M29c 电弧噪声累积漂移修复（2026-08-23，用户反馈「电弧都往一个方向飘/都长一个样」；主 test 863→864）**：
  - **根因**：`NoiseAnimator.animate` 每帧相对**当前**（已被上一帧位移过的）位置叠加噪声位移 → 位移逐帧累积，
    全体电弧朝同一方向**飞走 + 拉长**（观感"片元连一起"）。Blender 几何节点每帧从基准几何重新求值，位移恒相对原始位置
  - **修复**：`ArcCurve` 增**基准位置**（`baseX/baseY/baseZ`，`addPoint` 时记录；`setPoint` 只改当前位置供噪声/表面吸附；
    `copyRange` 一并拷贝）；`NoiseAnimator.animate` 改 `pos = base + noise(base)`——每帧在基准附近**有界摆动**不漂移
  - **观感差异化**：噪声种子改**每弧独立**（`arcNoiseSeed + arc.seed()*7919`，复刻 Blender 唯一ID 子组），
    不再全部电弧共用同一噪声场同向形变
  - 回归单测：`NoiseAnimatorTest.repeatedAnimationDoesNotDrift`（60 帧连续动画位移有界 <1.5，不累积）
  - check 全绿，editorTest 101
- **M21 火焰特效 + additive/glow 渲染（2026-08-14）**：`VfxGraphRenderer` 增 `BILLBOARD_ADDITIVE`/`BILLBOARD_GLOW` 拓扑 + additive 管线（`BlendFunction.ADDITIVE`，GEQUAL 反向 Z）；
  `VfxNodes` 增 `vfx.output_quad_additive`/`vfx.output_quad_glow` 输出节点（目录 45→47）；`VfxGraphManager` 增 `renderGlowFrame`/`hasGlowData`，
  glow 效果同时渲入主 RT（实心 additive 主体）与 bloom 输入（光晕）；`GlowEffect.process()` 接线 graph glow；
  新资产 `demo_fire.json`（3 层 spawn_rate 内焰/外焰/火星 + 渐变/曲线生命周期 + 无限持续）
- **M21b 火焰视觉修正（2026-08-14）**：billboard 增 `InstanceVel` 属性 + shader 沿视图空间速度方向拉伸（速度越快火舌越长，`stretch = clamp(speed*0.35, 0, 3)`）；
  `WorldTransform.applyDirection` 旋转/缩放速度向量（平移无关）；`demo_fire.json` 增每层 `init_randomize`、梯度 6 停靠点（黄→橙→红→暗红）、alpha 快速起跳后缓落、size 先涨后缩
- **M21c 火焰不可见修复（2026-08-14）**：片元火焰形 `taper = mix(1.35, 0.45, …)` 方向颠倒——底部 taper>1 使 `flame.y` 越界，配合 `discard` 把粒子底部大半裁掉，只剩顶部细条（近乎不可见 + 偶闪亮色）；
  改为**去掉 discard**，用 `smoothstep(0.45,1.6)` 软边衰减 + `widthScale = mix(1.0, 0.35, texCoord.y)` 底宽顶尖（沿速度方向为尖端），并 `fire_alpha` 起点 0→0.5 避免出生帧透明
- **M21f 火焰修正（速度/形态/颜色层次，2026-08-14）**：顶点拉伸系数 `1.4*speed+0.25`→`0.6*speed+0.2`（消除拉伸造成的"更快"观感）、随机倾斜/宽窄收敛；片元**去掉高频正弦波浪扰动**（`sin(h*14)` 造成火舌凹缺），改平滑 `taper=1.25→0.5` 底宽顶尖软轮廓 + **内部颜色层次**（中心白热 `exp(-dist2*2.5)` → 边缘基色、顶部渐暗转红，单粒子内也有层次）；资产降速（vy 0.35/0.25/0.35 + 浮力 0.15 + turbulence 0.7）；暂停时 `renderFrame` 完全跳过模拟只渲染（双保险，此前已修 spawnStart）；增 `FirePauseTest` 回归
- **M21g 火焰修正（速度根因 + 噪声图「被啃」轮廓 + 编辑器资产共享，2026-08-14）**：
  - **根因修复**：顶点 `pSeed = hash3(InstancePos)` 用当前坐标、随移动每帧漂移 → 火舌每帧随机变形（观感"非常快"）。`ParticleBuffer` 增每粒子稳定 `seed`（spawn 递增计数器），实例属性加 `InstanceSeed`/`InstanceAge`（stride 48→56），`pSeed = hash(seed)` 全程稳定
  - **噪声图**：新增 256×256 fBm value-noise 灰度瓦片（构造器 CPU 生成 + `writeToTexture` 上传），`createSampler(REPEAT, REPEAT, LINEAR, LINEAR)`；billboard/additive/glow 三管线换新 bind group（uniform + `Sampler0`），mesh/line/ribbon 沿用旧 layout
  - **片元火焰形重写**：底窄→中鼓→尖收轮廓 + 噪声「被啃」缺口（`smoothstep` 阈值离散啃掉几口）+ 细碎参差边缘 + 缓慢摇摆 + 闪烁，全部按 `(pSeed, pAge)` 采样 → 每根火舌独立被啃、随时间演化、不再全同
  - 拉伸系数收敛（`0.6*speed+0.2`→`0.5*speed+0.15`）；`demo_fire.json` 再降速（湍流 0.7→0.45、浮力 0.15→0.10、vy 0.35/0.25/0.35→0.28/0.20/0.28、init random 收敛）
  - **grapheditor 共享 main resources**：`ProjectBrowser` 增 extra roots，Project 面板列出 `src/main/resources/assets/academy/vfxgraph` 全部打包资产，双击打开自动判定 VFX 模式实时预览；`save()` 优先写回 `doc.path`（打包资产直接回写源文件，方便编辑器调参迭代）；editorTest 85（+1 多根列出测试）
- **M21h 火焰体积化 + 编辑器复用 glow（2026-08-14）**：
  - **体积火焰型**：片元火焰形改软圆斑 puff（底宽顶尖、柔和噪声边缘、膨胀/闪烁随 age 脉动、内部暖色亮核→边缘基色，无硬缺口/无怪异色调）；`demo_fire.json` 全面体积化——新增 **`DiscShape` 平面基盘**发射（XZ 半径内均匀，形状 8→9），内焰/外焰/火星从基盘升起；粒子更大更少（0.24/0.38/0.06，rate 55/30/18），比例与速度重调（vy 0.45/0.38/0.5 + 浮力 0.2 + 湍流 0.4/1.5Hz），解决"太密太碎/比例不对/颜色怪异"
  - **编辑器复用 glow**：新增 `EditorGlow`（editor 源集）——BILLBOARD_GLOW 主体渲进清黑离屏输入，复用游戏同款 `GAUSSIAN_BLUR` 降采样模糊（H+V）+ `GLOW_BLEND` 叠加回视口（`Sampler0`=视口、模糊层 2/3 纯黑占位、`GlowEffect.GlowUniforms` UBO），与游戏内 bloom 观感一致；`VfxPreview` 在渲染主体后调用
  - NODE_CATALOG shape 表 +1（Disc）
- **M21i 引擎式程序化火焰 + 分层 + soft particles（2026-08-14）**：
  - **引擎式火焰片元**：`FIRE_FRAGMENT` 采用 IQ simplex noise + 4 阶 fbm **域扭曲**（`fbm(q−vec2(0,3t))` 啃蚀火焰轮廓 + 暖色指数色带 `vec3(1.5c1, 1.5c1³, c1⁶)` + 顶部渐隐），每粒子独立相位（`pSeed`+`pAge`）；additive/glow 用
  - **分层渲染**：`ParticleBuffer` 增 `layer`（0=fire/1=smoke）；spawn 节点 `layer` 属性（4 节点）；over-life 节点 `layer` 过滤（life_color/alpha/size/velocity）；渲染器对 additive/glow 按层拆分——fire→additive 火焰、smoke→translucent 软圆斑（`billboardPipeline` 复用），新增 `smokeInstanceBuffer`；`bloomPass` 参数使 glow/bloom 输入只画 fire
  - **soft particles**：新 `SceneDepth`（深度附件拷到可采样纹理，格式跟随源深度）+ 片元 `Sampler1` 采样，`dFdx` 深度梯度做近表面软化 + `discard` 背面剔除；billboard 系渲染前先 `clearDepthTexture`（清屏时）再拷贝
  - **demo_fire 四层**：core（disc r0.1/rate60/size0.12/vy1.2）＋主焰（r0.22/rate40/0.35/0.9）＋火星（r0.18/rate25/0.05/1.6）＋smoke（r0.3/rate18/0.5→2.4/灰 a0.4/0.45），smoke 独立 `smoke_alpha`/`smoke_size` 曲线
  - 单测：ParticleBuffer layer swap + VfxNodes layer 过滤（life_alpha/life_size 仅作用 smoke），主 test 741
- **M21t Iris 兼容修复 + 清理 bypass（2026-08-14）**：
  - **根因**：Iris + shader pack 时世界深度渲染在 Iris 内部 gbuffer，`mainRenderTarget` 深度附件非场景深度（仅颜色被 `finalizeLevelRendering` 合成进来）；旧 vfx 只用主深度做 GEQUAL 深度测试（stale 下通过，可见），新 vfxgraph 却把主深度拷进 `SceneDepth` 当 `Sampler1` 采样做 soft particles → `depthDiff = fragZ - sceneDepth < 0` → billboard 全 `discard`、fire alpha→0 → **粒子全灭不可见**
  - **修复**：`VfxGraphRenderer` 增 `sceneDepthUsable(shaderPackInUse, geometry)`——Iris shader pack 生效时跳过 `SceneDepth.copyFrom`、`Sampler1` 改用 `farView`（常量 0.0，反向 Z 远平面）→ `depthDiff ≥ 0` 永不 discard；深度附件 + GEQUAL 测试保留（与旧 vfx 同级遮挡行为）；无 Iris/无 pack/编辑器路径不变
  - **清理 legacy**：删除全部 `IrisCompat.runWithBypass`（PostEffect/WorldLineOverlayPass/PlatinumCosmosPass×3/MixinLevelRenderer）——新兼容方式为注入点后直接渲染（Iris 在该点后不再拦截 GPU 命令）；`IrisCompat` 移除 `runWithBypass`/`enableBypass`/`resetBypass`/`BYPASS_STATES`；删除 `IrisCompatTest`/`VfxGraphIrisCompatTest`（纯 bypass 机制测试），新增 `sceneDepthUsable` 纯函数单测。主 test 741→738（净减），check 全绿
  - **属性块去穷举**：`VfxNodes` 节点注册的重复属性块提取共享常量（`OUTPUT_PROPERTIES`/`SPAWN_TAIL_PROPS`/`PARTICLE_BASIC_PROPS`/`SHAPE_PROPS`/`CURVE_LAYER_PROPS`/`NOISE_PROPS`/`BOUNCE_KILL_PROPS` + `props()` 拼接）——output×7/spawn×3+1/init_position/update_noise+turbulence/collision_ground+plane/over-life curve 系去重，净 -70 行，属性序与默认值逐项保留，check 全绿
  - **billboard.fsh 去硬 discard**：`vfxgraph_billboard.fsh` 的 `if (depthDiff < 0.0) discard` 在场景深度不连续处（几何边缘/深度量化台阶）会切出硬条纹带并整片裁掉粒子（有条纹 + 消失）；改为与 `vfxgraph_fire.fsh` 一致仅 `smoothstep` 软衰减（背面遮挡由 alpha 归零保证，glslangValidator 通过，check 全绿）
  - **billboard 边缘锯齿修复**：wobble 采样 UV 横跨 `h*2.4` 个瓦片周期，噪声最细 octave（32px 特征）在粒子边缘形成 ~2px 周期边界位移 → 锯齿；降频（`h*0.6`/`pAge*0.5`）+ 减幅（`*0.5→*0.2`）+ 加宽过渡带（`smoothstep(0.35,1,dist)`）+ 噪声瓦片 octave 4→3（`VfxGraphRenderer.buildNoiseTile`，仅 billboard 使用）。glslangValidator 通过，check 全绿
- **M22 电弧/路径驱动子系统（已实现；渲染为 M22f 终态，方向暂停待重设计）**：
  - **需求（原始）**：两点路径、玩家/模型环绕、分叉状电弧、模型表面游走。
  - **结论（保留）**：`ParticleBuffer.TRAIL_LENGTH=8` 且 trail 是衰减历史语义 → 粒子 trail（A/C 路线）装不下螺栓/做不了环/贴不了面，出局；选定**路径驱动 + 方向 Y**——CPU 只产约束 spine（两点/环绕/表面游走/分叉 + 每点宽度，20~50 点），锯齿/噪声场/辉光全在着色器（GPU），**去旧 arc 的后台线程**（旧 CPU 修饰器 `JaggedModifier` 递归 + `NoiseFieldModifier` Perlin 正是线程存在的原因）。
  - **渲染（M22f 终态，经 M22b/c/d/e 全部否决后改用旧 vfx）**：复用 `LightningMeshBuilder`（parallel transport ring 管网格，同旧 `ArcTube`/`LightningRenderer`）从 `ArcBuffer` spine 建管；
    `ARC_TUBE_FORMAT` = Position+UV+**Color**（颜色数据驱动，零代码常量）；`vfxgraph_arc.vsh/.fsh` + `arcTubePipeline(bloomPass)`（透明主 / additive bloom）；
    `thicknessVariation` 自然粗细起伏；`BoltPath` Laplacian 平滑（有折有曲）+ 细微分支；`drawSparks` 迷你电弧 tube 飞出（M22g，`sparkTubePipeline` 恒 additive）。
  - **观感数据驱动（M22h）**：`RenderSpec.ArcRender`（sparks/spark_speed/spark_size/spark_period/spark_travel/spark_length/spark_radius/spark_curve/spark_wobble/thickness/emission）全部来自 `output_arc` 块属性，渲染器零硬编码常量。
  - **波动自然化（M22i）**：火花沿主干 `u=s/(count-1)` 平滑渐变分布 + 稳定扇形爆发（世界参考投影切线平面），非逐颗随机。
  - **容器化实现**：新 `arc` 包（`ArcBuffer`/`Arc`/`Polyline` + `path/BoltPath`/`OrbitPath`/`SurfaceWalk`）——
    4 块注册（`vfx.block.arc_bolt/orbit/surface` SPAWN 类发射块 + `vfx.block.output_arc` OUTPUT 输出块，块目录 42→46）；
    `VfxSystemSimulator` 持有 `ArcBuffer` 并老化（`SimContext.arcs()` 暴露给块）；`RenderSpec.Geometry.ARC` + `VfxGraphRenderer.drawArcs`（GEQUAL 深度，arc 非空不早退、arcsDrawn 去重）；
    `GraphEffect`/`VfxPreview`/`EditorGlow` 透传 arcBuffer；容器画布 arc 块不画批次端口。
    新资产 `demo_arc.json`（仅 bolt 周期，单 GLOW 输出，strands 多股 + flicker）。主 test 784→814、editorTest 101。
  - **方向暂停（2026-08-16 用户决策）**：用户判断 arc 代码复用改动太大不可行，拟转向「毛笔笔迹」（brush）子系统重新设计。
    M22b/c/d/e 自研渲染方案（线芯/ribbon/X 形高斯/胖管/链式光束）全部被否决，仅 M22 容器化 spine 保留；详见会话日志。
- **M23 VFX 容器模型+序列化（Unity 式 Context + 数据流，ADR-027；主 test 741→753）**：
  - **新 `vfxgraph/model` 包**：`VfxSystem`（顶层）/`VfxContext`（SPAWN/INITIALIZE/UPDATE/OUTPUT 容器）/`VfxBlock`（context 内块）/`VfxOperatorNode`（自由算子）/`VfxFlowEdge`（context 间批次 flow）/`VfxDataEdge`（算子→块数据流）/`ParticleAttribute`（粒子属性枚举）/`VfxNode`（块/算子公共接口）——**与核心 `Graph` 并行，不破坏契约冻结**
  - **新 `vfxgraph/serialize`**：`JsonVfxGraphCodec`（新 schema `kind:"vfx"`，**无旧扁平格式兼容**）+ `VfxGraphSchemaVersion`；复用 `JsonGraphCodec` 值编解码（`encodeValue/decodeValue` 转 public）
  - **新 `vfxgraph/validate`**：`VfxGraphValidator`（flow 引用/连通/无环、数据边类型、输出存在；非 SPAWN 须有上游 flow）
  - 新设计文档 `VFX_CONTAINER.md`（M23–M28 权威设计）+ ADR-027 + MODULES MOD-12/13 + TASK_LEDGER M23–M28 + PROGRAM Phase 3 扩展
  - **批次语义设计**：flow 边携带「本帧新粒子索引批次」，每个 init 只处理上游 spawn 的批次——彻底消除 `SimContext.spawnStart` 单点耦合（M24 实现）
- **M24 容器执行器（ADR-027 第二阶段；主 test 753→761）**：
  - **批次机制**：新 `SpawnBatch(start,end)` record + `SimContext` 批次 API（`emitBatch`/`emittedBatches`/`setIncomingBatches`/`incomingBatches`/`forEachIncoming`）；
    `spawnStart` 字段保留兼容旧 `VfxSimulator`/`VfxNodes`（M27 迁移后移除）
  - **新 `VfxSystemSimulator`**：SPAWN→INITIALIZE→UPDATE 阶段驱动（Kahn 拓扑序）+ flow 边批次注入——init 块只处理上游 spawn 的批次（多 spawn 独立 init 互不干扰）；
    OUTPUT 不执行模拟（仅提供 RenderSpec）
  - **容器块目录**：`VfxBlockFactory`/`VfxBlockRegistry`/`VfxBlocks`（M24 最小集：spawn_rate/burst、init_velocity/color/size、update_velocity/gravity/age/fade、output_quad×3），与粒子 `VfxNodeFactory` 平行
  - 单测 7 用例：单链路、多 spawn→多 init 独立、fan-in 批次并集、暂停冻结、孤立 init 空跑、重力、缺块抛错
- **M25 数据流算子（ADR-027 第三阶段；主 test 761→768）**：
  - **算子目录**：新 `vfxgraph/operator` 包——`VfxOperator`/`VfxOperatorFactory`/`VfxOperatorRegistry`/`OperatorContext` +
    `VfxOperators.registerAll`（attr-read×ParticleAttribute、constant、param_float/vec3/color、add/sub/mul/div、curve、gradient）
  - **数据边接线**：`VfxSystemSimulator` 构建算子求值 DAG（算子间连接 + 环检测）+ `PortValueSource` 块端口值源；
    `VfxBlockFactory` 增带端口源创建；块经端口源读算子值（逐粒子），无绑定回退属性默认
  - `OperatorContext(particleIndex=-1)` 非粒子上下文：attr-read 返回默认值，支持参数/常量折叠语义
  - 单测 8 用例：attr→math→写回链、逐粒子不同、算子链、param 兜底、color 驱动、算子环抛错、元数据端口
- **M26 容器编辑器（ADR-027 第四阶段；editorTest 87→95）**：
  - **新容器编辑模型** `src/editor/kotlin/.../container/`：`VfxContainerModel`（contexts/blocks/operators/flow/data 编辑态，
    全部 mutation 命令化 + undo/redo + `toSystem()`/`load()` 桥）+ 命令集（Add/Remove/Connect/Disconnect/SetProperty/Move/SetOutput）
    + `VfxContainerCanvas`（ImGui 容器画布：context 框内 block 垂直排列、算子自由放置、flow/data 贝塞尔连线、拖拽/连线/框选/右键、端口高亮）
    + `VfxContainerModelRef`（多文档切换）
  - **GraphEditorApp 接线**：VFX 模式路由容器画布/调色板/检查器；`EditorDocument` 增容器模型（每文档独立 undo）；
    保存/加载/热重载按 `kind:"vfx"` 走 `JsonVfxGraphCodec`（容器 schema）；SHADER 扁平路径保留
  - **移除执行序徽标/Reorder**：容器画布不画顺序徽标（执行序由 context 内 blocks 列表序决定），SHADER 扁平保留
  - 单测 8 用例：增删 context/block 撤销、flow/data 连线、目标端口不存在拒绝、移动合并撤销、toSystem/load 往返、setOutput 过滤
- **M27 节点+资产迁移（ADR-027 第五阶段；主 test 768→773）**：
  - **VfxBlocks 全量**：spawn 4（rate/burst/periodic/distance，含 shape）/ init 8（position/velocity/color/size/rotation/lifetime/mass/randomize）/
    update 10（velocity/gravity/force/noise/turbulence/vortex/drag/damping/age/fade）/ collision 5（ground/plane/sphere/bounds/kill）/
    over-life 4（color/alpha/size/velocity）/ orient 4（face_camera/velocity/fixed/spin）/ output 7（point/quad×3/mesh/line/ribbon）= **42 块**
  - **VfxOperators 补全**：param_curve/param_gradient（5+ 算子，黑板书复制）
  - **7 资产转档容器 schema**（`kind:"vfx"`）：minimal_burst/demo_burst/demo_fountain/demo_ribbon/demo_fire/skill_dirstrike
    经转换脚本转档；运行时 `VfxGraphManager` 增 `kind:"vfx"` 分支（containerAssets + JsonVfxGraphCodec），
    `GraphEffect`/`ActiveEffect` 增容器构造（VfxSystem → VfxSystemSimulator），扁平资产仍兼容（M28 彻底移除旧路径）
  - 修复 `ParticleBuffer.setColor` 破坏 startAlpha 的潜在 bug（新增 `setColorRgb`，life_color 用 RGB-only 避免 alpha 复合衰减）
  - 单测：VfxContainerFullCatalogTest（全目录 + 全链路 + over-life 曲线/渐变）+ VfxContainerAssetsTest（6 资产解码/校验）
  - 已删除调试测试
- **M28 运行时收尾（部分完成；主 test 773→775）**：
  - **性能门禁**：`VfxSystemSimulatorPerfTest`——10k 粒子稳态 step 最坏 ~4.6ms、600 帧 churn ~43ms（对标旧 `VfxSimulatorPerfTest`，容器执行器不退化）
  - **运行时容器回归**：`VfxGraphManagerTest.spawnContainerAssetThroughManager`——`kind:"vfx"` 资产经管理器 `registerAsset`/`spawn`/`tick` 端到端（验证 M27 容器分支）
  - **待办**：游戏内冒烟（需显示环境）确认容器资产渲染后，彻底移除旧扁平路径（`VfxNodes`/`VfxSimulator`，8 测试类随迁容器）；M22 电弧随之启动
- **M28b 块级批次 flow（连线指定 spawn→init 配对；主 test 775→783，editorTest 98→100）**：
  - **动机**：用户反馈「多个 set velocity 好长」——demo_fire 4 层 spawn/init 拆成 4 组 context 链太长；希望在一个 context 里用连线指定配对
  - **模型**：新 `VfxBlockFlowEdge(fromBlockId, toBlockId)` + `VfxSystem.blockFlows`（序列化 `blockFlows`、校验源=SPAWN 块/目标=INITIALIZE 块）
  - **执行器**：`VfxSystemSimulator` 按块收集批次 + 分发——**精确配对模式**（图内存在任一 blockFlow 时，未配对 init 块收空批次）；无 blockFlow 回退 context 级（兼容旧资产）
  - **编辑器**：spawn 块右缘批次输出端口、init 块左缘批次输入端口（绿）；拖 spawn 输出→init 输入建块级 flow；绿线 + hover tooltip；命令/撤销/round-trip
  - **demo_fire 重写为块级 flow 紧凑结构**：1 SPAWN + 1 INITIALIZE + 4 条 blockFlows 配对（替代 8 context 长链）
  - 单测：VfxBlockFlowTest（配对独立/context 回退/未配对空）+ codec blockFlows round-trip + editorTest connectBlockFlow/disconnect + undoSteps
  - **后续修复**：loop 编辑后渲染不可见（重启节流 250ms + 延续 time）、输出 shader 默认值去穷举（空串中性）、容器画布右键弹窗改画布内渲染、撤销路由到容器模型
- **M21n 移除 smoke_shader 穷举（双输出数据驱动，B-20）**（主 test 783→784，editorTest 100→101）：
  - **渲染规格**：`RenderSpec` 去 `smokeFragmentShader`/`smokeSpec()`（硬编码 QUAD/TRANSLUCENT），增 `layer` 过滤（`""`=全部）+ `matchesLayer`/`feedsBloom`；`VfxGraphRenderer.render` 改收 `List<RenderSpec>` 逐 spec 按 `layer` 过滤绘制（同一 render pass 切管线），删 `smokeInstanceBuffer`/`growSmokeInstances`，`bloomPass` 只画 GLOW 规格（translucent 层不参与 bloom）
  - **节点/资产**：输出节点/块 `OUTPUT_PROPERTIES` 去 `smoke_shader` 增 `layer`；demo_fire 拆两输出块——fire（`output_quad_glow` glow+fire 片元 `layer=fire`）+ smoke（`output_quad` translucent+particle 片元 `layer=smoke`）
  - **接线**：`GraphEffect`/`ActiveEffect` 多 `specs()`；`VfxGraphManager` rendererPool 按 specs 列表 + `renderGlowFrame`/`hasGlowData` 改 `feedsBloom`；`VfxPreview`/`EditorGlow` 全改 specs 列表；容器 `SetOutputCommand` 改**切换语义**（多输出块共存，右键菜单 Set/Remove 动态）；layer 映射统一到 `ParticleBuffer.layerByte/layerFilter`（VfxNodes/VfxBlocks 委托去重）
  - 单测：VfxGraphRendererTest layer 过滤/多输出（替换 smokeSpec 测试）+ VfxContainerModelTest.setOutputTogglesMultipleOutputs
- **M21o 着色器命名修正（billboard→particle + fire/smoke 拆分，B-21）**：
  - **改名**：`vfxgraph_billboard` → **`vfxgraph_particle`**（中性软圆斑默认；vsh 去火舌拉伸/倾斜/长度宽度变体，仅绕视图轴旋转；fsh 改纯软圆 + soft particles）
  - **拆分**：火舌拉伸 + 宽度/长度变体 + 倾斜逻辑 → 新 **`vfxgraph_fire.vsh`**（demo_fire fire 输出 vertex 改指 fire，与引擎式火焰 fsh 配对）
  - **新增 smoke**：**`vfxgraph_smoke.vsh/fsh`**（轻微速度拉伸 + 噪声卷须软圆斑，smoke 输出专用）
  - **接线**：`R.shaders.core` 改 particle + 增 smoke；`RenderSpec` 默认兜底改 particle；demo_fire smoke 输出改 smoke 着色器；其余 4 资产（demo_burst/fountain/minimal_burst/skill_dirstrike）billboard→particle；VfxGraphRendererTest 同步
  - glslangValidator 全过，check 全绿
- **BUGFIX 容器画布右键菜单不响应（B-22）**：`VfxContainerCanvas.renderContextMenus` 每帧无条件清空 `contextRequest`，`beginPopup` 只在请求存在时才调用 → 弹窗仅存活一帧即被 ImGui 关闭（菜单不显示/点击无响应）。修复：请求在弹窗打开期间存活——仅当 `beginPopup` 返回 false 且 `!isPopupOpen`（弹窗真正关闭）才清除，每帧重绘菜单。容器全部右键（block/operator/context/edge/canvas）恢复
- **M22f 电弧改回旧 vfx 渲染（2026-08-16，经多轮修观感）**：用户否定全部自研方案（M22b 线芯/纸带、M22c X 形高斯、M22d 胖管+三层高斯、M22e 链式光束×4 版），明确「**改用旧 vfx 的电弧渲染**」：
  - **渲染**：`VfxGraphRenderer.drawArcs` 复用 `LightningMeshBuilder`（parallel transport ring 管网格，同 `ArcTube`/`LightningRenderer`）从 `ArcBuffer` spine 同步建管；`ARC_TUBE_FORMAT` = Position+UV+**Color**；`arcTubePipeline(bloomPass)`（透明主 / additive bloom）+ `render` 传 `bloomPass`
  - **颜色数据驱动 + 零代码常量**：`ArcLightning` UBO 仅 1 个 `LightningParams`（16B，aces 开关 + 发射增强 0.3 标量）；`vfxgraph_arc.fsh` `color = max(vColor.rgb,1e-4) × (1 + 增强×强度)`，`fragColor.a = vColor.a`（不透明）——**代码/着色器零颜色常量，颜色 100% 由图数据 `color` 属性驱动**
  - **自然观感**：`BoltPath` Laplacian 平滑（2 迭代 α0.4，有折有曲）+ demo bolt jagged 0.5；`thicknessVariation(t, φ)` 确定性平滑粗细起伏（`1+0.16·sin(2π·1.5t+φ)+0.09·sin(2π·3.7t+2φ)`，φ=(arc.seed()%10000)·0.618）
  - **细微分支**：BoltPath 分支宽度 0.35×、长度 0.2~0.4（细短副闪电）、`branchCount` 上限 6；demo branch_count 3
  - **飞出小电弧（M22g 改 tube + M22h 数据驱动 + M22i 波动自然化）**：`drawSparks` 每电弧 `sparks` 颗（图数据），沿主干**平滑渐变分布**（`u=s/(count-1)` 主导位置/速度/周期/尺寸），方向**稳定扇形展开**（世界参考投影切线平面 + `(u-0.5)×2.0` 扇形角），`arc.age` 循环、`alpha=(1-age)²` 衰减、颜色=电弧色；**迷你闪电 tube**（`buildSparkBolt`：5 点中点位移锯齿折线 + parallel transport 环 + `sparkTubePipeline` 恒 additive，复用弧管管线），根粗尖细 taper + 粗细起伏，主/bloom 均可见；**观感参数（sparks/speed/size/period/travel/length/radius/curve/wobble/thickness/emission）全部由图属性驱动（`RenderSpec.ArcRender`），渲染器零硬编码常量**
  - **清理**：删 beam 全套（`buildBeamTexture`/`beamTexture·View·Sampler`/`ARC_RIBBON_FORMAT`/`ArcRibbonBuilder`/`ArcRibbonBuilderTest`/`ArcBeamTextureTest`/ribbon 缓冲）
  - demo_arc：bolt width **0.005**（细管，无 orbit 块）、jagged 0.5、branch_count 3、颜色水蓝 `0.2,0.6,1`
  - 单测：主 test **808** + editorTest 101，check 全绿；glslangValidator 全过；**编辑器肉眼确认待显示环境**（`./gradlew graphEditor` 打开 `demo_arc`：水蓝细管 + 自然粗细起伏 + 细分支 + 尖头小电弧飞出 + bloom）
  - 历史：M22b/c/d/e（beam 系）全部废弃，仅 M22 容器化 spine 保留
- **M29 Blender「闪电附着」三 VFX 忠实复刻（2026-08-22，主 test 809→859 + editorTest 101 通过；**⚠️ 已实现但被用户否决，观感不符，见「进行中」M29 条目**）**：
  - **基础改造**：`ArcCurve.setSurface`（可选的端点吸附表面）；`SurfaceConstraint` 升级为**真最近表面点**
    （`MeshDistance.nearestPoint`，Closest Point on Triangle，复刻 Blender `Sample Nearest Surface`，不再要求投影落三角形内）；
    `VfxSystemSimulator.step` 接线 SurfaceConstraint（噪声动画后对带表面弧每帧端点吸附）——解决 ARC_SURFACE.md 核心缺口；
    `MeshAssets` 内置 `plane(size)`（2×2 地面，法线 +Y）/`sphere(radius, segments)`（UV 球，法线向外）+ `resolve` 查内置/注册表；
    `CurveGenerator.generateSurfaceArc`（per-point 短弧：沿法线展开 + 控制柄起拱 + 重采样，复刻 Curve Line→Bezier→Resample）；
    `MeshDistance`（点到网格最近距离 + 最近点，Ericson 算法）
  - **三块（VFX 目录 46→48）**：`vfx.block.arc_surface` **重写**（表面布点 + 概率删减 + 断续时序 + 端点吸附，
    复刻 `随机点云阵列` 子组；删除未实现的 walk_speed 语义）；`vfx.block.arc_contact` **新增**（接触闪电：
    源面布点 + `MeshDistance` 距离剔除 + 端点吸附接触面，`contact_origin_*` 定位接触对象，复刻主组第二套系统）；
    `vfx.block.arc_spark` **新增**（粒子火花：弧→点 + 随机删减 + 溅射方向 + 重力 + 迷你管，复刻主组第三套系统；
    只从带表面弧派生防指数增长）
  - **三示例资产**：`surface_arc.json`（平面蓝电弧）、`contact_arc.json`（平面↔悬浮球连接弧）、`spark.json`（面弧 + 蓝火花），
    均单 GLOW 输出白炽观感；SampleAssetsTest 10 资产全过
  - 单测：SurfaceArcBlockTest/ContactArcBlockTest/SparkArcBlockTest/MeshDistanceTest/MeshAssetsBuiltinTest/
    CurveGeneratorSurfaceArcTest/VfxSystemSimulatorSurfaceSnapTest + 目录 46→48；`./gradlew check` 全绿
  - **编辑器肉眼复验待显示环境**：`./gradlew graphEditor` 打开 `surface_arc`/`contact_arc`/`spark`——表面电弧拱起、
    接触弧只出现在球附近、火花溅射飞出；游戏内 `./gradlew clientDev` + `/academy vfx spawn academy:vfxgraph/surface_arc x y z`

## 进行中

- **M30b 电弧一比一复刻（已实现，待显示环境肉眼确认）**：弧基座爬行游走 + 逐点噪声抖动 + 大小各异 + age 亮度闪烁 + 平躺→帐篷拱 + 接触弧顶端连球 + 火花衰减——全部按实际 `闪电附着.blend` 权威数据复刻（见「已完成」M30b 条目）。**待显示环境**：`./gradlew graphEditor` 打开 `demo_blender_arc`/`surface_arc`/`contact_arc`/`spark` 肉眼确认；游戏内 `./gradlew clientDev` + `/academy vfx spawn academy:vfxgraph/demo_blender_arc x y z`。
- **M21 火焰特效（headless 完成，游戏内冒烟待确认）**：引擎式分层火焰已实现并通过门禁；
  视觉经 19 轮迭代（M21b–s：拉伸火舌/火焰形/去 discard/降速/不规则化/内部颜色层次/暂停冻结/稳定种子+噪声图/体积化/编辑器 glow/IQ 程序化火焰+分层+soft particles/着色器与 JSON 热重载/isCompat 运行期去模组）。
  火焰片元最终回到**最开始的解析 IQ simplex + sin-hash** 观感版（用户指定）；GPU 优化为无 trig hash（M21p ②）后被用户要求回退观感，现为原始 sin-hash 版。
  `./gradlew graphEditor` 直接打开打包 `demo_fire`，支持**着色器（.fsh/.vsh）与图资产（.json）双热重载**、调参回写源资产，**尚需显示环境肉眼确认最终效果**。
  已修复 Iris + shader pack 下粒子不可见（M21t：soft particles 深度根因 + 清理 legacy bypass），需 `./gradlew clientDev -PisCompat=true` 开 pack 确认火焰可见。
- **M22 电弧/路径驱动子系统（历史）**：容器化实现（`ArcBuffer`/spine）→ 自研渲染 M22b/c/d/e（beam 系否决）→ M22f 改用旧 vfx 电弧渲染 → 2026-08-19 复刻 Blender 流水线（M22-Rev2）→ **M30 已按实际 .blend 重做几何/算法/参数**（此前的 `generateSurfaceArc` 方向错误已修）。**余量**：MC 方块/玩家模型 → 通用三角面转换器（ARC_SURFACE.md）；编辑器肉眼复验待显示环境。
- **M23–M28 VFX 容器化（Unity 式 Context + 数据流，ADR-027）**：M23（模型/序列化/校验）+ M24（容器执行器/批次/最小块集）+ M25（数据流算子/数据边接线）+ M26（容器编辑器）+ M27（全节点+资产迁移：42 块全量 + 算子补全 + 6 资产容器转档 + 运行时容器加载）+ M28b（块级批次 flow：VfxBlockFlowEdge + demo_fire 紧凑重写 + 编辑器连线配对）**done**；彻底移除旧扁平路径 + 游戏内冒烟待显示环境。
- **编辑器容器模式联调修复（本会话）**：loop 编辑后渲染不可见（重启节流 + 延续 time）、t 冻结、输出 shader 默认值去穷举、右键弹窗（画布内渲染）、撤销/重做路由容器模型、块选中/断线——详见 TASK_LEDGER B-13~B-19。
- **编辑器容器模式右键菜单（B-22，本会话修复待复验）**：右键弹窗只存活一帧的根因已修（`contextRequest` 改为弹窗打开期间存活，`beginPopup` false 且 `!isPopupOpen` 才清除）。**需显示环境复验**：`./gradlew graphEditor` 打开容器 `demo_fire`，右键块/算子/context/边应弹菜单并可点击（Set as Output / Remove from Outputs / Disconnect / Delete / Add Block）。

## 缺口评估（Phase 2 收尾，用户口径）

- Shader 节点目录 **83 ≥ 80 达标**；VFX 块目录 **48 块 + 23 算子**（M29：arc_surface 重写 + arc_contact/arc_spark 新增，46→48），形状 **9/9 全完成**（含 Disc + MeshShape）。
- 曲线/渐变采样器与编辑器、黑板分组、sub-graph 编译内联 + **多文档标签页编辑器（A2）**、
  **多样本真实纹理绑定（A1）**、VFX 多拓扑渲染、独立 docked 视口 + 轨道相机 + gizmo、
  **游戏内运行时集成 + 技能挂点（A4）**（管理器/资产加载/spawn/参数绑定/热重载/剔除）、
  **兼容/性能/发布审计**（Iris 兼容、性能门禁、单测补全、用户手册、GLSL 黄金测试）均已落地。
  **Phase 2 + A1–A4 目标全部达成。**
- 仍缺（后续会话）：**M30b 三 VFX 编辑器肉眼复验（几何/算法/参数已按实际 .blend 一比一复刻，待显示环境确认）**、
  M21 火焰游戏内冒烟确认（需显示环境）、运行时游戏内冒烟（需显示环境）、嵌套子图、
  多样本纹理绑定后的游戏内实际资产绑定路径（编辑器侧已就绪）、mesh surface 的 GPU 渲染（输出仍用单位立方体）。
- 单测：主 test 881 + editorTest 101 = 982（目标 ≥300 已大幅超标；M30b 新增 M30FaithfulReplicationTest 10 用例 + 更新 BlenderArcGeometryTest span 语义，目录 46→48）。
- **审计修复（2026-08-13）**：发现并修复 7 处 bug——runtime dt 单位（慢 20 倍）、资源重载后台并发写、
  spawn_burst/periodic/distance 缺默认 lifetime（首帧即灭+槽位残留）、buildShape 未接线三形状、
  视锥剔除坐标系错误（改 JOML FrustumIntersection）、dev 热重载并发、粒子上限未生效。详见 TASK_LEDGER BUGFIX。
- **A 系列完成（2026-08-13）**：A1（ADR-021 多样本纹理）、A2（ADR-022 多文档标签页）、A3（ADR-023 MeshShape）、
  A4（SpawnVfxGraphPacket + DirStrike 替换）。详情见「已完成 M17–M20」。

## 剩余工作清单（handoff，A1–A4 已全部完成）

### A. 真实功能缺口（可 headless 实现 + 单测）

| 项 | 现状 | 缺口 | 涉及 |
| --- | --- | --- | --- |
| A1 多样本纹理绑定 | **done**（ADR-021） | 游戏内材质路径（`GraphMaterial`→真实绑定）尚未消费；编辑器预览已接真实资产 | shader/pipeline |
| A2 sub-graph 编辑器标签页 | **done**（ADR-022） | 嵌套子图展开（当前仅一层） | graph/subgraph |
| A3 mesh surface 形状 | **done**（ADR-023） | `vfx.output_mesh` 渲染仍为单位立方体（资产→GPU 网格未接）；OBJ 资产打包加载 | vfxgraph/render |
| A4 游戏内技能/实体实际挂点 | **done**（M20） | 仅 DirStrike 一个技能接线；`spawnFollow`/多参数绑定待更多技能验证 | skill + packet |

### A0. 火焰特效（M21，本会话新增）

| 项 | 现状 | 缺口 | 涉及 |
| --- | --- | --- | --- |
| additive/glow 渲染 | **done**（`output_quad_additive`/`output_quad_glow` + `renderGlowFrame`/`hasGlowData` 接线） | 游戏内冒烟确认火焰观感（需显示环境） | vfxgraph/render + post |
| 多层无限火焰资产 | **done**（`demo_fire.json`，3 层 spawn_rate + 渐变/曲线 + 浮力/湍流） | 观感微调参数（vy/turbulence/拉伸系数随需求再调）；可选加火焰贴图采样（用户已选软边圆盘） | vfxgraph/assets |
| 暂停冻结 | **done**（`renderFrame` 暂停跳过模拟只渲染 + spawnStart 修复 + `FirePauseTest`） | 游戏内确认暂停/恢复表现 | vfxgraph/runtime |

### B. 文档修正（✅ 已随本 handoff 完成）

- NODE_CATALOG：`input.delta_time/sine_time/cosine_time`、`subgraph` 已改为 ✅（此前误标 ❌）。

### C. 需显示环境验证（无法 headless）

- `./gradlew clientDev` 冒烟**已验证**（2026-08-13）：`/academy vfx spawn minimal_burst` 粒子显示、深度遮挡正确、动画平滑、游戏暂停冻结。**待验证**：`F3+T` 热重载、`run/vfxgraph/` 文件监听、Iris shader pack 共存、DirStrike 技能替换视觉。
- DirStrike 替换后技能冲击波视觉验证（图资产 `skill_dirstrike` 按原效果行为设计，需肉眼确认）。

### D. 附带可选项

- `renderFrame` 每 effect 矩阵合成仍每帧 new（`FrustumIntersection` 已复用）
- 多输出节点只编译第一个（shader）/ ~~只取第一个输出节点拓扑（vfx）~~ —— **vfx 已支持多输出（M21n：`VfxGraphRenderer` 逐 spec 按 layer 过滤绘制）**；shader 仍取第一个——已知简化

## 下一步（下一会话第一步）

0. **M29 三 VFX 编辑器肉眼复验（M29b 已实现 + M29c 漂移修复，需显示环境）**：`./gradlew graphEditor` 打开 `demo_blender_arc`（新 Blender 测试场景：
   2×2 地面 + 悬浮球 + 面上爬行短弧 + 平面↔球连接弧 + 火花，全断续低频）肉眼确认：**地面/球可见（半透明面）**、弧数稳态 <30、
   火花有界不放大、**电弧不同向漂移且形态各异（M29c）**、火花/爬行/连接可见；再逐个打开 `surface_arc`/`contact_arc`/`spark` 确认低频断续观感。
0. **M30b 电弧一比一复刻肉眼复验（已实现 + 单测，待显示环境）**：`./gradlew graphEditor` 打开 `demo_blender_arc`——
   确认弧基座爬行游走、中段逐点抖动弯折、大小各异、亮度先亮后灭闪烁、平躺→帐篷拱成长、接触弧顶端连球、火花溅射衰减（稳态稀少）；再逐个打开 `surface_arc`/`contact_arc`/`spark` 确认低频断续观感。
1. **M28 余下：游戏内冒烟 + 移除旧扁平路径**（部分需显示环境）：`./gradlew clientDev` + `/academy vfx spawn academy:vfxgraph/demo_fire x y z` 确认容器资产渲染（需显示环境）。
   冒烟通过后彻底移除旧扁平路径（`VfxNodes`/`VfxSimulator`/`GraphEffect` 扁平构造，8 测试类随迁容器执行器）。
2. **表面转换器（ARC_SURFACE.md）**：MC 方块/玩家模型 → 通用三角面转换器
   （`BlockModelSurfaceSource`/`EntityModelSurfaceSource`），供真实游戏对象表面附着。
3. **M21 火焰冒烟确认**（需显示环境）：`./gradlew graphEditor` 打开打包 `demo_fire`（视口实时预览 + **着色器/JSON 双热重载**调参回写源资产），或 `./gradlew clientDev` + `/academy vfx spawn academy:vfxgraph/demo_fire x y z`，
   肉眼确认最终观感（被啃参差轮廓/单粒子白热→暗红层次/暂停冻结）。当前火焰片元为原始解析 IQ simplex + sin-hash 观感版；若需性能再优化，在其基础上做屏幕尺寸 LOD 而非改观感。
4. **C 冒烟收尾**（有显示环境）：`F3+T` 资源重载验证；`run/vfxgraph/` 文件监听热重载；**Iris + shader pack 下 `demo_fire`/`minimal_burst` 可见性验证（M21t 修复）**（`./gradlew clientDev -PisCompat=true`）；触发 DirStrike 验证技能替换效果。
5. **A1 余量**：游戏内材质真实纹理绑定消费（编辑器预览已就绪）；多样本绑定经黄金测试 + 预览验证。
6. **A2 余量**：嵌套子图展开（`SubGraphFlattener` 递归）。
7. **A3 余量**：`vfx.output_mesh` 资产网格渲染（OBJ → GPU 顶点缓冲替换单位立方体）。
8. **A4 扩展**：更多技能接入（SkyStrike impact、实体 spawnFollow）。

> 运行方式：默认 `./gradlew graphEditor`/`clientDev` **运行时无第三方模组**（sodium/iris/jade/jei 仅编译期）；`-PisCompat=true` 仅追加 jei/sodium/iris。
> 建议顺序：**M30b 电弧肉眼复验（显示环境）** → M28 冒烟/移除旧路径 → 表面转换器（ARC_SURFACE.md）→ M21 火焰确认 → C 收尾 → A2 嵌套子图 → A3 mesh 渲染 → A1 游戏内材质 → A4 扩展。

## 阻塞

- （无）

## 关键决策（最近）

- ADR-013：VFX 图自持渲染，不桥接现有 VFX 系统（取代 ADR-004 桥接方案）。
- ADR-014：编辑器撤销/重做采用命令模式（Command/UndoManager），移动/属性拖拽经 mergeKey 合并。
- ADR-015：frames/notes/camera/layout 编辑器元数据走 sidecar（`<name>.editor.json`），核心 Graph 与运行时零改动。
- ADR-016：texture.sample 采样固定 uniform `Sampler0`（单 sampler），SAMPLER 参数不进 std140。
- ADR-017：`Curve.Keyframe` 增切线 + 插值模式（LINEAR/STEP/SMOOTH/BEZIER），codec 防御式解析。
- ADR-018：黑板参数分组存 sidecar `paramGroups`，不改核心 `GraphParameter`。
- ADR-019：VFX spawn/init 相位边界用 `SimContext.spawnStart`（init 只处理本帧新粒子），over-life 引用黑板曲线参数。
- ADR-020：VFX 运行时存活参数绑定（`setLiveParam` 不重建模拟器）+ `param` 属性 + 5 个 `vfx.param_*` 节点 +
  `WorldTransform` 渲染端应用 + `GraphCamera.fromGameCamera`。
- ADR-021：Shader 多样本纹理绑定——`samplePlan` 槽位规划 + 只发用到的 sampler + 动态 `withSampler` bind group +
  `ShaderPreview` 真实资产加载（取代 ADR-016 单 sampler 折衷）。
- ADR-022：编辑器多文档标签页——`GraphEditorModelRef` 换模型指针 + `GraphEditorDocuments`/`EditorDocument`
  每文档独立模型/undo + 共享 `SubGraphRegistry` + 双击 subgraph 节点开子图。
- ADR-023：MeshSurface 发射形状——OBJ 纯解析 + 面积加权采样 + `MeshAssets` 注册表 + buildShape `mesh` 分支
  （形状 7→8，节点目录 45 不变）。
- ADR-024：VFX 图 additive/glow 渲染（M21）——`output_quad_additive`（additive 软边圆盘）+ `output_quad_glow`（同时渲主 RT 实心主体 + bloom 输入光晕）；
  `VfxGraphManager.renderGlowFrame`/`hasGlowData` 由 `GlowEffect.process()` 接线；火焰单粒子内部白热→暗红渐变；暂不接火焰贴图（软边圆盘方案）。
- ADR-025：Iris 兼容改为**注入点后直接渲染**（M21t）——删除 legacy `runWithBypass`（Iris 在 `iris$endLevelRender` 后的 `popMatrix` 注入点不再拦截 GPU 命令，bypass 为死代码）；
  Iris shader pack 下主目标深度非场景深度，vfxgraph soft particles 采样主深度会 `discard`/alpha 灭掉粒子 → `sceneDepthUsable()` 门控，pack 生效时 `Sampler1` 退回 `farView`（0.0）保证可见（与旧 vfx 同级遮挡行为，不接 Iris 内部 depthtex）。
- ADR-026：电弧子系统方向 Y——路径驱动（非粒子 trail），CPU 约束 spine + GPU 观感（无线程），Tube 主渲染。
- ADR-027：**VFX 容器化（M23–M28）**——新 `VfxSystem` 容器模型与核心 `Graph` 并行（不破坏契约冻结）；批次携带 flow（spawn 输出批次 → init 只处理上游批次，替代 spawnStart 单点耦合）；全属性数据流（attr-read 算子）；旧扁平 schema 彻底废弃；渲染层零改动。
- M14：视口改独立 docked（离屏 target + ImGui 多纹理），轨道相机 + ImGuizmo gizmo/网格。
- Phase 2 目标：类 Unity 可用编辑器，≥30k 行、Shader ≥80 节点、VFX ≥45 节点、≥300 单测（见 EDITOR_ROADMAP.md）。

## 会话日志

| 会话 | 摘要 |
| --- | --- |
| 2026-08-13 | M0 落地：接力构件 + 抽象层检查 + 接口骨架 + 门禁验证 |
| 2026-08-13 | M1 节点图核心：类型/模型/注册/序列化/校验全部实现，28 单测通过 |
| 2026-08-13 | M2 图编译：拓扑排序/死代码消除/常量折叠，34 单测通过 |
| 2026-08-13 | M3 Shader 代码生成：GLSL 生成 + 动态管线 + 材质参数，46 单测通过 |
| 2026-08-13 | M4 编辑器 MVP：ImGui 后端泛化 + 节点画布/面板/检查器/实时预览，编译门禁通过 |
| 2026-08-13 | M5 VFX 模拟：CPU 粒子系统 + 模拟节点目录 + 发射器形状，56 单测通过 |
| 2026-08-13 | M6 VFX 自持渲染：GraphCamera/VfxGraphRenderer/VfxPreview + 模式切换，58 单测通过 |
| 2026-08-13 | M7 资产管线 + 运行时集成（库层）：GraphAssets/GraphEffect + 迁移接线，63 单测通过 |
| 2026-08-13 | Phase 2 handoff：EDITOR_ROADMAP + NODE_CATALOG 写入，M9-M16 路线图与节点清单就绪 |
| 2026-08-13 | M9 编辑器基建：命令栈 + 模型命令化 + 剪贴板 + 多选对齐 + 右键菜单 + 快捷键 + 命令面板 + 边重连，editorTest 49 单测通过，check 全绿 |
| 2026-08-13 | M10 画布增强：分组 frame + sticky note + minimap + 吸附/取景 + docking 布局 + 项目浏览器 + 最近文件 + sidecar 元数据持久化，editorTest 累计 68 单测通过，check 全绿 |
| 2026-08-13 | M11 Shader 节点补全：纹理系统(texture.sample/Sampler0) + ImGuiBackend 多纹理 ID + 节点目录 27→83（数学/三角/噪声/颜色/坐标/自定义函数），主 test 累计 575 单测通过，check 全绿 |
| 2026-08-13 | M12 黑板+子图+曲线/渐变编辑器：Curve 切线/插值模型 + 采样器 + 曲线/渐变→GLSL + bezier/ramp 编辑器 + 黑板类型/范围/分组 + sub-graph 编译内联，主 test 597 + editorTest 71 通过，check 全绿 |
| 2026-08-13 | M13 VFX 节点补全：ParticleBuffer rotation/mass/trail + spawn/init 相位边界 + 节点 6→40（spawn/init/力场/collision/over-life/orient/输出变体）+ 4 拓扑渲染 + 形状补全，主 test 613 通过，check 全绿 |
| 2026-08-13 | M14 视口 overhaul：独立 docked 视口（离屏 target + ImGui 多纹理）+ OrbitCamera 轨道相机 + ImGuizmo gizmo/网格 + 播放/循环 + 统计 overlay + 质量档位，editorTest 累计 76 通过，check 全绿 |
| 2026-08-13 | M15 运行时集成：VfxGraphManager/ActiveEffect/EffectBudget + 资产加载（F3+T）+ dev 文件监听 + /academy vfx spawn 命令 + 存活参数绑定（ADR-020，节点 40→45）+ WorldTransform，主 test 642 + editorTest 74 通过，check 全绿 |
| 2026-08-13 | M16 兼容/性能/发布审计：Iris bypass + 性能门禁（10k step ~4ms）+ 单测补全（696）+ USER_GUIDE + GLSL 黄金测试（6 图快照），Phase 2 全部达成，check 全绿 |
| 2026-08-13 | BUGFIX 审计修复：dt 单位（慢 20 倍）/资源重载并发/spawn 缺 lifetime/buildShape 未接线三形状/视锥剔除坐标/F3+T 热重载并发/粒子上限未生效，7 处全部修复 + 回归单测，主 test 704，check 全绿 |
| 2026-08-13 | M17（A1）多样本纹理绑定：SamplerBinding/samplePlan/只发用到的 sampler/动态 bind group/ShaderPreview 真实资产纹理（品红兜底），golden 重生成，主 test 704→712，check 全绿 |
| 2026-08-13 | M18（A3）MeshSurface 形状：ObjMeshParser/MeshShape（面积加权+立方体兜底）/MeshAssets/buildShape mesh 分支，形状 7→8（节点目录 45 不变），主 test 712→723，check 全绿 |
| 2026-08-13 | M19（A2）子图编辑器标签页：GraphEditorDocuments/EditorDocument/GraphEditorModelRef（6 类零重构）/TabBar/双击开子图/每文档独立 undo，editorTest 74→81，check 全绿 |
| 2026-08-13 | M20（A4）技能挂点：SpawnVfxGraphPacket + DirStrike 替换旧手写 VFX（删 8 文件）+ skill_dirstrike 资产（param 驱动 size）+ 编解码/资产测试，主 test 723→722（替换净减），check 全绿 |
| 2026-08-13 | 文档收尾：ADR-021/022/023 + STATE/TASK_LEDGER/NODE_CATALOG/MODULES/USER_GUIDE 更新，A1–A4 全部完成，主 test 722 + editorTest 81 = 803 |
| 2026-08-13 | BUGFIX 编辑器独立运行崩溃：`GraphEditorApp.<init>` 在 ImGui context 创建（`DesktopUiHost.bind`→`ImGuiBackend.init`）前调 `ImGui.getIO()`。`setIniFilename` 延迟到首个 ImGui 帧（`ensureImGuiIni`），`./gradlew graphEditor` 启动不再崩溃 |
| 2026-08-13 | BUGFIX 画布层级：多文档 TabBar 内置于 Canvas 窗口后，网格/节点按窗口绝对坐标绘制覆盖标签栏。`NodeCanvas.topInset` + `pushClipRect` 裁剪到标签栏下方内容区（drawGrid/frameToBounds/Minimap 同步偏移），标签栏不再被画布内容遮挡 |
| 2026-08-13 | BUGFIX 画布内容拖拽误移窗口：根因是 ImGui 默认 `ConfigWindowsMoveFromTitleBarOnly=false`，而 Canvas 内容用 drawList 绘制、无 item，空白区拖拽被当作窗口移动。首个 ImGui 帧设 `setConfigWindowsMoveFromTitleBarOnly(true)`——内容拖拽只操作画布，标题/停靠标签仍可拖动重排 |
| 2026-08-13 | BUGFIX ImGui 1.90 `addRect/addRectFilled` 旧 `ImDrawCornerFlags` 位掩码（`0b1111`/`0b100`）触发断言（NodeCanvas 3 处 + Minimap 1 处），改用 `ImDrawFlags.RoundCorners*` |
| 2026-08-13 | 节点窗口跟随修复 + VFX 执行顺序可重排：① 节点用相机绝对屏幕坐标绘制，拖动 Canvas 窗口（标题/重停靠）时节点不跟随——新增 `NodeCanvas.syncCameraToWindow` 每帧按窗口位移同步相机 pan；② 节点右键菜单增「Move Up/Down in Execution Order」（`ReorderNodeCommand` 可撤销）+ VFX 节点标题右上角执行顺序徽标（nodes 列表序，1 起），editorTest 81→83 |
| 2026-08-13 | 视口空 + 最小示例：`minimal_burst.json` 示例资产（9 节点，spawn 爆发→velocity→gravity→age→fade→output_quad）。根因：加载 VFX 图时文档继承当前模式（默认 SHADER），视口用 shader 预览渲染 VFX 图致空——`loadGraph` 按节点类型自动判定模式（含 `vfx.*` → VFX） |
| 2026-08-13 | 深度测试方向修正：Minecraft 主渲染为**反向 Z**（`DepthStencilState.DEFAULT = GREATER_THAN_OR_EQUAL`，近=1.0/远=0.0），VFX 管线原先误用 `LESS_THAN_OR_EQUAL`（正向 Z）→ 粒子只在深度≈近平面（玩家处）通过、其余被误遮挡。改为 `GREATER_THAN_OR_EQUAL` + 清屏深度 0.0（反向 Z 远平面），粒子被场景正确遮挡；USER_GUIDE 记为功能 |
| 2026-08-13 | 游戏内资产键修复：`VfxGraphAssetLoader` 从 `listResources` 拿到的键带 `.json`，而命令/API 拼的是不带 `.json` 的 id → `spawn` 报 "no vfx graph asset"。统一去掉 `.json`（loader 注册 + `spawn` 归一化键），两种写法都兼容；另加 `spawn` 懒加载兜底（资源管理器 + classpath）与空重载不清缓存防御 |
| 2026-08-13 | 游戏内特效三修：① **卡顿**——模拟改由 `renderFrame` 每帧按真实帧时间步进（移除 `onClientTick` 的 20Hz tick 调用），动画平滑；② **剔除**——`EffectBudget.sphereInFrustum` 的 `setTranslation(-camPos)` 改 `translate(-camPos)`（补 R·(-camPos) 旋转，相机远离原点时视锥坐标正确）；③ **深度**——`VfxGraphRenderer` 管线加 `DepthStencilState(GEQUAL, 不写深度)` + render 贯穿 depth 附件，游戏用主渲染目标深度与世界遮挡，编辑器视口 target 改 `useDepth=true` |
| 2026-08-13 | 游戏暂停冻结：`renderFrame` 检测 `Minecraft.isPaused()`，暂停时 dt=0（特效冻结，仍渲染）；恢复从上一帧起算。另修复 `onClientTick` 合并冲突残留（保留 `ImagPhaseDowsingRodClient.tick()` + VFX 注释）。至此游戏内冒烟通过：粒子显示/遮挡/平滑/暂停均验证 |
| 2026-08-14 | M21 火焰特效：`VfxGraphRenderer` additive 管线 + `BILLBOARD_ADDITIVE`/`BILLBOARD_GLOW` 拓扑、`vfx.output_quad_additive`/`vfx.output_quad_glow` 节点（目录 45→47）、`VfxGraphManager.renderGlowFrame`/`hasGlowData`、`GlowEffect` 接线 graph glow、`demo_fire.json` 多层无限火焰。check 全绿 |
| 2026-08-14 | M21b 火焰视觉修正：billboard `InstanceVel` 属性 + 沿速度拉伸成火舌 + 火焰形轮廓（底宽顶尖）+ `WorldTransform.applyDirection` + `demo_fire.json` 重调（每层 randomize/6 停靠点梯度/曲线）。check 全绿 |
| 2026-08-14 | M21c 火焰不可见修复：片元火焰形 taper 方向颠倒 + `discard` 裁掉粒子底部大半（不可见+偶闪）——去掉 discard、改 `smoothstep` 软边 + `widthScale` 底宽顶尖，`fire_alpha` 起点 0→0.5。check 全绿 |
| 2026-08-14 | M21d 火焰形 + 暂停修复：片元改柔和竖椭圆（去水滴感）、资产降速 + 浮力上升 + 缩 cone、spawn 节点无新粒子帧标记 `spawnStart` 使 init 空跑（暂停抖动/消失根因）+ 回归单测。check 全绿 |
| 2026-08-14 | M21e 火焰降速 + 不规则化：再降速（vy 0.55/0.4/0.5）+ 水平随机 + 尺寸随机；billboard 增 per-particle 伪随机（火舌长短/宽度各异）+ 片元正弦波浪扰动，打破规则形态。check 全绿 |
| 2026-08-14 | M21f 火焰修正：拉伸系数降（去"更快"观感）、片元去高频正弦（火舌凹缺）改平滑底宽顶尖 + 内部白热→边缘颜色层次、资产再降速、暂停完全跳过模拟只渲染 + FirePauseTest 回归。check 全绿 |
| 2026-08-14 | M21g 火焰速度根因 + 噪声图被啃轮廓 + 编辑器资产共享：`ParticleBuffer` 稳定 seed + 实例 `InstanceSeed/InstanceAge`（stride 48→56）；新增 256×256 fBm 噪声纹理（`Sampler0` bind group）；片元火焰形重写（底窄→中鼓→尖收 + 被啃缺口/参差/摇摆/闪烁，`(pSeed,pAge)` 驱动）；拉伸收敛 + demo_fire 再降速；grapheditor ProjectBrowser 共享 main 打包资产（双击预览、Save 回写源文件）。主 test 738 + editorTest 85，check 全绿 |
| 2026-08-14 | M21h 火焰体积化 + 编辑器复用 glow：片元改软圆斑体积 puff（柔和噪声边缘/膨胀/闪烁/暖色亮核）；新增 `DiscShape` 平面基盘发射（形状 8→9）；demo_fire 体积化（更少更大、基盘升起、比例/速度重调）；`EditorGlow` 复用 GAUSSIAN_BLUR + GLOW_BLEND 在编辑器视口合成光晕。check 全绿 |
| 2026-08-14 | M21i 引擎式程序化火焰 + 分层 + soft particles：`FIRE_FRAGMENT` 用 IQ simplex noise+fbm 域扭曲火焰轮廓（暖色指数色带）；`ParticleBuffer` layer（fire/smoke）+ spawn `layer` 属性 + over-life `layer` 过滤；渲染按层拆分（additive 火 + translucent 烟，`bloomPass` 控制 bloom 只画 fire）；`SceneDepth` 深度拷贝 + `Sampler1` soft particles（`dFdx` 深度梯度软边 + discard）；demo_fire 四层（core/主焰/火星/smoke）。主 test 741，check 全绿 |
| 2026-08-14 | M21j 烟雾黑底修复：`BlendFunction.TRANSLUCENT` 是标准 alpha（SRC_ALPHA,1−SRC_ALPHA），`BILLBOARD_FRAGMENT` 却输出 `vec4(rgb*a,1.0)`（alpha 恒 1）→ `rgb*a` 直接替换背景、a≈0 处变黑；改直通 alpha `vec4(rgb,a)`，烟雾/通用 output_quad 背景透明。check 全绿 |
| 2026-08-14 | M21k VFX 图着色器去写死：`VfxGraphRenderer` 7 段内嵌字符串着色器迁至资源文件 `assets/academy/shaders/core/vfxgraph_{billboard,fire,mesh,simple}.{vsh,fsh}`，经 `R.shaders.core.vfxgraph_*` 标识引用（同 `vfx_particle` 惯例）；`buildPipeline` 改收 Identifier、预编译改 `device.precompilePipeline(pipeline)` 单参（走设备默认 ShaderSource：游戏=资源管理器、编辑器=ClasspathShaderSource）；删除 `DynamicShaderSource` 依赖。glslangValidator 全过，主 test 741 + editorTest 85，check 全绿 |
| 2026-08-14 | M21l 渲染去穷举（数据驱动 RenderSpec，**禁止代码穷举着色器**）：`RenderSpec(geometry, blend, vertexShader, fragmentShader, smokeFragmentShader)` —— 顶点/片元着色器、混合、smoke 片元全部来自**输出节点属性**（`vertex`/`shader`/`blend`/`smoke_shader`，图数据显式指定，无按几何/类型枚举、无兼容回退，缺失走中性默认 billboard/translucent）；几何（quad/mesh/line/ribbon）结构派生；`VfxGraphRenderer` 移除 `Topology` 枚举/固定管线/geometry→vertex switch/`SMOKE_SPEC`，`pipelineFor` 只按 spec 字段动态构建（**渲染器零着色器 id 引用**）；`fromOutputType` 删除，`GraphEffect`/`ActiveEffect`/`VfxGraphManager`/`VfxPreview`/`EditorGlow` 全改 `spec`；6 个示例资产输出节点显式写全 `vertex`/`shader`/`blend`（demo_fire 加 `smoke_shader`）。新增粒子外观 = 新 `.fsh` + 图上设属性，零 Java 改动。主 test 743 + editorTest 85，check 全绿 |
| 2026-08-14 | M21n 编辑器 glow 预览内存泄漏修复：`VfxPreview.render` 每帧 `new EditorGlow(...)`，其延迟创建的 3 个离屏 `TextureTarget` + UBO + 黑纹理从不释放 → 预览 glow 效果（demo_fire）每帧泄漏 GPU 纹理；改 `EditorGlow` 复用单实例 + 增 `destroy()`（销毁 targets/UBO/黑纹理），`VfxPreview` 增 `close()`，`GraphEditorApp.onDispose` 调用释放。check 全绿 |
| 2026-08-14 | M21o 资源重载 NPE 修复：编辑器 `save()` 回写源资产时会生成 `<name>.editor.json` sidecar，`VfxGraphAssetLoader` 的 `endsWith(".json")` 过滤器把它也当图资产加载 → `JsonGraphCodec.decode` 缺 `id` 字段 NPE 中断 F3+T/重载。修复：① 加载器过滤排除 `.editor.json`；② `GraphAssets.load` 解码失败记日志返回 null 不中断重载；③ `registerAsset`/`reloadFromContent` 跳过 null；④ 删除仓库残留 sidecar。check 全绿 |
| 2026-08-14 | M21p 火焰 GPU 压力优化（两轮）：① 原解析 IQ simplex noise + 4 阶 fbm 每像素 ~24 次 sin/hash，火焰贴屏 fill-rate 骤降 → 改预烘焙 fBm 纹理采样（每像素 2 次纹理采样）但**观感退化**（256px 瓦片细节丢失、缺口消失）；② 回退为**解析 simplex+fbm 保留观感**，仅把最贵的 sin-hash 换成**无 trig 廉价 hash**（`fract(dot)` 系），simplex 结构与火焰轮廓公式完全不变，GPU 压力仍大幅下降。glslangValidator 通过，主 test 743 + editorTest 85，check 全绿 |
| 2026-08-14 | M21r 编辑器着色器热重载（mtime 轮询版）：`ClasspathShaderSource.sourceDir`（指向 `src/main/resources`）优先读源目录（`DesktopApplication.run` 设置）；`ShaderHotReload.scanNow()` 由 `GraphEditorApp.renderImGui` 每帧调用（渲染线程，~300ms 节流），递归统计 `assets/academy/shaders` 下 `.fsh/.vsh/.glsl` 的 mtime，有变更则 `GpuDevice.clearPipelineCache()` → 设备级 shader/pipeline 缓存清空 → 下一帧 `pass.setPipeline` 从源目录重新编译。改 `vfxgraph_fire.fsh` 保存后视口即时生效（终端打印 `[shader-hot-reload]`）。主 test 743 + editorTest 85，check 全绿 |
| 2026-08-14 | M21s 图资产热重载：新增 `GraphHotReload`（mtime 轮询，`GraphEditorApp.renderImGui` 每帧调用，首扫只初始化不触发）；`GraphEditorDocuments.reload()` 就地替换已打开文档（保留标签页/相机，不新增）；`reloadGraphFile` 解码 + 就地重载；`save()` 后 `acknowledge` 避免"自己保存"触发重载。外部 IDE 改 `demo_fire.json` 保存 → 已打开的文档/视口即时刷新。editorTest 85→87（reload + 检测测试），check 全绿 |
| 2026-08-14 | M21q 编辑器 Iris 崩溃改为**构建层解决（isCompat）**：新增 `isCompat` 属性（`-PisCompat=true`/`-DisCompat=true`/环境变量 `IS_COMPAT`）；第三方模组 sodium/iris/jade/jei 改 `compileOnly`（**运行时默认一律不加载**，编译期始终可见），`isCompat` 时 `runtimeOnly` 仅追加 jei/sodium/iris —— 编辑器任务（graphEditor 等）与默认 client/clientDev 不再加载 Iris → 其 `setWindowHints` mixin 不再触发（移除 M21q 反射引导方案）。缺席安全已核验：`MixinPlugin` 条件应用 Iris 手部 mixin、`WirelessNodeModel` 的 Iris 调用在 `IrisCompat.isShaderPackInUse()` 保护内、`JEIPlugin` 由 JEI 扫描发现、主代码无 jade/sodium 引用。`runtimeClasspath` 验证：默认无 iris/sodium/jei/jade，`-PisCompat=true` 仅 iris/sodium/jei。check 全绿 |
| 2026-08-14 | **M21t Iris 兼容修复 + 清理 bypass**：根因——Iris shader pack 下主目标深度非场景深度，vfxgraph soft particles（`SceneDepth`→`Sampler1`，`depthDiff<0` 则 discard/alpha 灭）把粒子全灭不可见；修复——`VfxGraphRenderer.sceneDepthUsable()`，pack 生效时跳过深度拷贝改用 `farView`（0.0）。清理 legacy `runWithBypass`（新兼容方式为注入点后直接渲染，Iris 不再拦截），删 `IrisCompat` 相关方法/状态与两个 bypass 测试，新增 `sceneDepthUsable` 纯函数单测；`VfxNodes` 属性块去穷举（共享常量 + `props()` 拼接，净 -70 行）。主 test 738 + editorTest 87 = 825，check 全绿 |
| 2026-08-14 | billboard 观感收尾：`vfxgraph_billboard.fsh` 去硬 `discard`（深度不连续处切条纹带/整片消失 → 与 fire.fsh 一致仅 `smoothstep` 软衰减）+ 边缘锯齿修复（wobble 降频减幅 + 加宽过渡带 + 噪声瓦片 octave 4→3，`VfxGraphRenderer.buildNoiseTile`）。glslangValidator 通过，check 全绿 |
| 2026-08-14 | **M22 电弧/路径驱动子系统（设计落地，未实现）**：评估三条路线（粒子 trail A / trail 宽度曲线 C / 忠实 TUBE B）→ `ParticleBuffer.TRAIL_LENGTH=8` + 衰减历史语义使 A/C 出局；选定**路径驱动 + 方向 Y**（CPU 约束 spine：两点/环绕/表面游走/分叉 + 每点宽度；锯齿/噪声场/辉光全在着色器，去后台线程）+ **Tube 主渲染**（表面游走贴曲率/环绕任意视角/分叉物理细丝），Ribbon fallback。产出：新 `ARC_DESIGN.md`（三层流水线/模块/路径源/里程碑/性能预算）+ ADR-026 + TASK_LEDGER M22-01~10 + NODE_CATALOG arc 节点（VFX 45→49）+ MODULES MOD-11 + PROGRAM Phase 3。check 全绿（本轮纯文档，无代码改动） |
| 2026-08-15 | **M23 VFX 容器模型+序列化（Unity 式 Context + 数据流，ADR-027）**：新 `vfxgraph/model`（VfxSystem/VfxContext/VfxBlock/VfxOperatorNode/VfxFlowEdge/VfxDataEdge/ParticleAttribute/VfxNode，与核心 Graph 并行）+ `vfxgraph/serialize`（JsonVfxGraphCodec 新 schema `kind:"vfx"`，无旧兼容；复用 JsonGraphCodec 值编解码）+ `vfxgraph/validate`（VfxGraphValidator flow 连通/无环/类型）。新 `VFX_CONTAINER.md` 权威设计 + ADR-027 + MODULES MOD-12/13 + TASK_LEDGER M23–M28 + PROGRAM Phase 3 扩展。主 test 741→753（+16），check 全绿 |
| 2026-08-15 | **M24 容器执行器 + 批次 flow（ADR-027 第二阶段）**：新 `SpawnBatch` + `SimContext` 批次 API（emitBatch/setIncomingBatches/forEachIncoming，spawnStart 保留兼容旧执行器）；新 `VfxSystemSimulator` 按 SPAWN→INITIALIZE→UPDATE 阶段驱动 + flow 边批次注入（多 spawn 独立 init 互不干扰、fan-in 并集、孤立 init 空跑）；新容器块目录 `VfxBlockFactory`/`VfxBlockRegistry`/`VfxBlocks`（最小集 12 块）。主 test 753→761（+7），check 全绿 |
| 2026-08-15 | **M25 数据流算子 + 数据边接线（ADR-027 第三阶段）**：新 `vfxgraph/operator` 包（VfxOperator/VfxOperatorFactory/VfxOperatorRegistry/OperatorContext + VfxOperators 目录：attr-read×属性、constant、param_float/vec3/color、add/sub/mul/div、curve、gradient）；`VfxSystemSimulator` 构建算子求值 DAG（算子间连接 + 环检测）+ `PortValueSource` 块端口值源；`VfxBlockFactory` 增带端口源创建（逐粒子读算子值，无绑定回退属性）。主 test 761→768（+8），check 全绿 |
| 2026-08-15 | **M26 容器编辑器（ADR-027 第四阶段）**：新 `src/editor/.../container/` 包——`VfxContainerModel`（容器编辑态 + 命令 undo/redo + toSystem/load 桥 + portsFor/firstPort）+ 命令集 + `VfxContainerCanvas`（ImGui 容器画布：context 框内 block、算子、flow/data 贝塞尔连线、拖拽/连线/框选/右键、端口高亮）+ `VfxContainerModelRef`。GraphEditorApp VFX 模式路由容器画布/调色板/检查器；EditorDocument 增每文档容器模型；保存/加载/热重载按 `kind:"vfx"` 走 JsonVfxGraphCodec；VfxBlocks 块元数据补输入端口（数据流）。editorTest 87→95（+8），check 全绿 |
| 2026-08-15 | **M27 节点+资产迁移（ADR-027 第五阶段）**：`VfxBlocks` 全量 42 块（spawn 4/init 8/update 10/collision 5/over-life 4/orient 4/output 7，含 shape + 输入端口）+ `VfxOperators` 补 param_curve/gradient；6 打包资产转档容器 schema（`kind:"vfx"`）；`VfxGraphManager`/`GraphEffect`/`ActiveEffect` 增容器加载分支（双路径并存，M28 移除旧路径）；修复 `ParticleBuffer.setColor` 破坏 startAlpha bug（增 setColorRgb）。主 test 768→773（+5），check 全绿 |
| 2026-08-15 | **M28 运行时收尾（部分）**：`VfxSystemSimulatorPerfTest`（10k 稳态 ~4.6ms/帧 + 600 帧 churn ~43ms，容器执行器性能不退化）+ `VfxGraphManagerTest.spawnContainerAssetThroughManager`（kind:"vfx" 资产经管理器端到端）。主 test 773→775（+3），check 全绿。移除旧扁平路径 + 游戏内冒烟待显示环境 |
| 2026-08-15 | **BUGFIX 编辑器容器视口冻结（B-08/B-09）**：编辑器打开容器 demo_fire 视口冻结第一帧——`VfxPreview.sync()` 版本守卫在清空模拟器之后，版本未变时每帧清空但不重建（`systemSimulator`/`simulator` 置 null 后提前 return）→ `render()` 走扁平分支且 simulator 亦 null → 什么都不渲染；改尺寸同理。修复：守卫提前到清空之前。另移除 `vfxgraph_fire.fsh` 未使用的 `Sampler0` 声明（GlProgram 警告）。回归 `VfxPreviewSyncRetentionTest`，editorTest 96→97，check 全绿 |
| 2026-08-15 | **BUGFIX 容器编辑器连线/flow 可见性 + simVersion（B-10~B-12）**：① 画布数据连线反向拖拽把假端口 `"@in"` 传给 `connectData` 被拒（无线）→ `finishConnecting` 两端解析真实端口；② flow 边未绘制 → 渲染循环补 `drawFlowEdge`；③ 移动节点触发模拟器重建致 loop 播放中断 → `VfxContainerModel` 区分 `simVersion`（仅影响模拟的变更），`VfxPreview` 按它判断重建；④ 转档脚本按类型分组破坏多 spawn 独立（4 个 init 互相覆盖）→ 改按 spawn 分组独立 SPAWN+INITIALIZE context。editorTest 98、主 test +1，check 全绿 |
| 2026-08-15 | **M28b 块级批次 flow（连线指定 spawn→init 配对）**：新 `VfxBlockFlowEdge` + `VfxSystem.blockFlows`（序列化/校验）；`VfxSystemSimulator` 按块收集/分发批次（精确配对模式：存在任一 blockFlow 时未配对 init 收空；无则回退 context 级）；编辑器 spawn 块批次输出端口 + init 块批次输入端口 + 绿线 + tooltip。解决「多个 set velocity 好长」——同一 context 内用线配对。主 test 775→781、editorTest 98，check 全绿 |
| 2026-08-15 | **BUGFIX 编辑器容器模式联调（B-13~B-19）**：① loop 编辑后渲染不可见 + t 冻结——loop 重启节流（250ms）+ `setTime` 延续时间戳；② 输出 shader 属性默认值去穷举（VfxBlocks/VfxNodes OUTPUT_PROPERTIES 改空串中性，由图上显式指定）；③ 容器画布右键弹窗**改画布内渲染**（`renderContextMenus` 在 popClipRect 后 + `canvasPalette` 委托，删除宿主 renderContainerContext）；④ 撤销/重做路由容器模型（activeUndo/activeRedo）；⑤ 块点击选中优先 + 边右键断线（hitAnyEdge + EDGE 菜单）；⑥ demo_fire 重写块级 flow 紧凑结构。主 test 783 + editorTest 100 = 883，check 全绿 |
| 2026-08-15 | **M21n 移除 smoke_shader 穷举（双输出数据驱动，B-20）**：`RenderSpec` 去 `smokeFragmentShader`/`smokeSpec()`（硬编码 QUAD/TRANSLUCENT），增 `layer` 过滤（`""`=全部）+ `matchesLayer`/`feedsBloom`；输出节点/块 `OUTPUT_PROPERTIES` 去 `smoke_shader` 增 `layer`；`VfxGraphRenderer.render` 改收 `List<RenderSpec>` 逐 spec 按 layer 过滤绘制（同一 render pass 内切管线），删 `smokeInstanceBuffer`/`growSmokeInstances`，`bloomPass` 只画 GLOW 规格；`GraphEffect`/`ActiveEffect` 多 `specs()`，`VfxGraphManager` rendererPool 按 specs 列表 + `renderGlowFrame`/`hasGlowData` 改 `feedsBloom`；`VfxPreview`/`EditorGlow` 全改 specs 列表；容器 `SetOutputCommand` 改**切换语义**（多输出块共存，右键菜单 Set/Remove 动态）；layer 映射统一到 `ParticleBuffer.layerByte/layerFilter`（VfxNodes/VfxBlocks 委托去重）；demo_fire 拆两输出块（fire glow + smoke translucent，各带 layer）；单测换 layer 过滤/多输出 + setOutput 切换。主 test +1，editorTest +1，check 全绿 |
| 2026-08-15 | **BUGFIX 容器画布右键菜单不响应（B-22）**：`VfxContainerCanvas.renderContextMenus` 每帧无条件清空 `contextRequest`，`beginPopup` 只在请求存在时才调用 → 弹窗仅存活一帧即被 ImGui 关闭（菜单不显示/点击无响应）。修复：请求在弹窗打开期间存活——仅当 `beginPopup` 返回 false 且 `!isPopupOpen`（弹窗真正关闭）才清除，每帧重绘菜单。容器全部右键（block/operator/context/edge/canvas）恢复 | VfxContainerCanvas.kt |
| 2026-08-15 | **M21o 着色器命名修正（billboard→particle + fire/smoke 拆分，B-21）**：`vfxgraph_billboard` 名称与内容不符（vsh 火舌拉伸/倾斜、fsh smoke 软圆斑）——改名 **`vfxgraph_particle`**（中性软圆斑，vsh 仅绕视图轴旋转、无拉伸）；火舌/宽度/长度变体/倾斜拆到新 **`vfxgraph_fire.vsh`**（fire 输出 vertex 改指 fire）；新增 **`vfxgraph_smoke.vsh/fsh`**（轻微速度拉伸 + 噪声卷须软圆斑，smoke 输出专用）；`R.shaders.core` 增 particle/smoke，RenderSpec 默认兜底改 particle；demo_fire fire 输出 vertex→fire、smoke 输出→smoke；其余 4 资产 billboard→particle；测试同步。glslangValidator 全过，check 全绿 |
| 2026-08-15 | **M22 电弧/路径驱动子系统（容器化实现，ADR-026）**：新 `arc` 包——`ArcBuffer`/`Arc`/`Polyline`（活电弧 spine + 宽度 + seed/age/lifetime，`SimContext.arcs()` 暴露、容器执行器老化）+ `path/BoltPath`（两点中点位移 + 分叉 + taper，确定性）+ `OrbitPath`（闭合环 + 相位 + arc seed 稳定噪声 + tilt）+ `SurfaceWalk`（三角形质心/法线/面积 + 最近质心邻居，无共享顶点鲁棒，unitCube fallback）+ `TubeMeshBuilder`（并行 transport 同步 ring 网格）；4 块注册（`vfx.block.arc_bolt/orbit/surface` SPAWN 类 + `vfx.block.output_arc` OUTPUT，目录 42→46）；`RenderSpec.Geometry.TUBE` + `VfxGraphRenderer.drawTubes`（TRIANGLES + IndexType.INT，整环位移 + GEQUAL，arc 非空不早退）+ `vfxgraph_arc.vsh/.fsh`（glslangValidator 过）；`GraphEffect`/`VfxPreview`/`EditorGlow` 透传 arcBuffer，loop 重启判空含 arc，overlay 计 arc；容器画布 arc 块不画批次端口；新资产 `demo_arc.json`（bolt 周期 + orbit 持续 + additive/glow 双输出）接入 SampleAssetsTest。主 test 784→808（+24）、editorTest 101，check 全绿 |
| 2026-08-15 | **M22b 电弧渲染重构（游戏引擎式组成，参考 keijiro/SpektrLightning）**：管（空壳表面、无横截面光强轮廓 → 读成折线）移除 → **两 pass**：细亮线芯 `vfxgraph_arc_core`（GL_LINES，3D 任意角度可见，逐线段 `hash(Ring,Seed,Age)` 闪烁 + 随机熄灭 flat + 尖端 taper）+ 宽软辉光 `vfxgraph_arc`（相机 ribbon，`cross(tangent,toCamera)` 宽度方向，fragment UV.y 径向衰减 + 脉动，additive → bloom）。新 `ArcCoreBuilder`/`ArcGlowBuilder` 取代 `TubeMeshBuilder`；`RenderSpec.Geometry.ARC` + `drawArcs`（`corePipelines` 缓存 + `arcsDrawn` 去重）；`output_arc` 增 `core` 属性；`arc_bolt/orbit/surface` 增 **strands 多股**（bolt 3 / orbit 2 / surface 1，每股独立种子 → 主闪电+副丝）；demo_arc 单 GLOW 输出 + strands/flicker/jagged 调参。主 test 808→814（+6，TubeMeshBuilderTest -4 / 新 builder+strands +10）、editorTest 101，glslangValidator 全过，check 全绿 |
| 2026-08-15 | **M22b BUGFIX 辉光拓扑 + 观感调参**：① **根因（顶点连一起/糊成粗带）**：辉光 ribbon 每段发 6 索引（2 三角形），但管线拓扑误用 `QUADS`——GL 把索引 4 个一组解释 → 四边形退化/交叉、顶点连成一片；改 `TRIANGLES`。② 太粗：demo 辉光半宽收敛（bolt 0.1→0.045、orbit 0.05→0.022）+ bolt taper 收紧（tip 0.35→0.08，尖端成点）+ branch_count 2→1。③ 好假：辉光加**沿线两频热点噪声**（`hot=0.72+0.7·n1·n2`，随 u/seed/age 起伏 → 电弧跳动，不再像纸带）；线芯随机熄灭 8%→3%（保持连续亮线）。④ 闭合环（orbit 首=末点）**回绕接缝**（core/glow 两 builder 补 wrap 段），环无断口。主 test 814→816（+2 闭合测试）、editorTest 101，check 全绿 |
| 2026-08-16 | **handoff（M22c 电弧视觉重设计已批准未实现）**：M22b 落地后用户判定观感「太丑」——根因：① 1px GL_LINES 线芯（铁丝感、锯齿、无能量）；② 辉光是独立宽条带、与线芯脱节（纸带）；③ 热点噪声像静电噪点；④ 无横截面径向光强轮廓。调研后定为**游戏引擎式组成（Unity VFX/Unreal/Genshin/Dark Souls 系）**：**X 形同心高斯光带**（2 层 × 2 条：相机面向 + 90° 交叉，任意视角可见）——核心层窄白热 `exp(-(d·3.5)²)` + 辉光层宽蓝晕 `exp(-(d·1.5)²)` + bloom。用户确认 X 形双光带。实施要点：删 ArcCoreBuilder(GL_LINES)/vfxgraph_arc_core 线版；ArcGlowBuilder→ArcRibbonBuilder(widthScale+cross)；drawArcs 每 pass 2 条光带；去热点噪声；demo_arc 调参。主 test 816 + editorTest 101，check 全绿。详见「下一步」#2 |
| 2026-08-16 | **M22c 电弧视觉重设计实现（X 形同心高斯光带）**：`ArcGlowBuilder`→`ArcRibbonBuilder`（`addPolyline` 增 `widthScale`+`cross`，宽度方向 `W1=cross(T,toCam)`/`W2=T×W1` 绕切线 90°）；删 `ArcCoreBuilder`/`ArcCoreBuilderTest`/`ARC_CORE_FORMAT`(含 Ring)/GL_LINES 线芯；`VfxGraphRenderer.drawArcs` 两 pass 每 pass 每 polyline 构建 2 条交叉光带——核心层 widthScale 0.25 → `vfxgraph_arc_core`（fragment `I=exp(-pow(d*3.5,2))`、`mix(color,white,0.6)·(1.5+0.6·shimmer)` 白热芯）、辉光层 widthScale 1.0 → `vfxgraph_arc`（`I=exp(-pow(d*1.5,2))`，去热点噪声），均 TRIANGLES + additive；`drawArcLines`/`drawArcQuads` 合并 `drawArcRibbon`；demo_arc 调参（bolt width 0.08/strands 2、orbit width 0.04/strands 2）；`ArcRibbonBuilderTest`（widthScale 半宽缩放 + cross 宽度方向 ⊥ 相机面向）。glslangValidator 全过，主 test 816→813（净 -3）+ editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22d 电弧视觉重设计 v2（A+B 混合，用户否决 M22c「还是好丑」后选定）**：调研 SpektrLightning 真实源码 + Unreal/Unity/Dark Souls 系。**B 胖辉光管**：git 恢复 `TubeMeshBuilder`→`ArcTubeBuilder`（并行 transport ring，顶点存法线 `Position+Normal+Color+Seed+Age`），新 `vfxgraph_arc_tube.vsh/.fsh`（`t=1−|dot(N,V)|` 径向辉光剖面，cull=false 实心体积），`ARC_TUBE_FORMAT` + `arcTubePipeline`；**A 厚能量光带**：`vfxgraph_arc.fsh` 重写（单 pass 三层高斯 `exp(−(d·5)²)白热 + exp(−(d·2.4)²)本体 + exp(−(d·1.3)²)宽晕`，同芯不脱节 + 闪烁 + shimmer，**不加噪声**）；删 `vfxgraph_arc_core.*`/`arcCorePipeline`/`corePipelines`/core pass；`RenderSpec` 删 `coreShader` 字段、`VfxBlocks` output_arc 删 `core` 属性、`R` 增 `vfxgraph_arc_tube`；demo_arc 重调（bolt width 0.18/orbit 0.1）；新 `ArcTubeBuilderTest`（ring 计数/法线径向/索引范围/复用，5 用例）。glslangValidator 全过，主 test 813→**818** + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22e Niagara 闪电链（链式多节光束，用户否决 M22d「太丑」后明确「niagara 闪电链」+「好像是 beam」）**：**链节光束贴图** `buildBeamLink(128,256)`（横穿贴图微型锯齿螺栓，R=白热芯/G=辉光 双通道高斯，透明底，固定种子确定性）→ `beamTexture/beamView/beamSampler`（REPEAT+LINEAR）；**`vfxgraph_arc.fsh` 重写**：沿 u 铺 `CHAIN_LINKS=6` 节，`linkBright=mix(hash(link),hash(link+1),smoothstep)` 逐节独立亮度 + 节间平滑过渡（能量沿链流动 = 闪电链核心观感）+ 白热芯 `mix(vColor,white,0.9)·tex.r·2.2` + 辉光 `vColor·tex.g·0.7` + 整条闪烁 + 尖端 taper；**渲染器** ARC 走 `NOISE_BIND_GROUP`（`Sampler0=beamView`/`Sampler1=farView`），`drawArcs` 单 pass；**删 M22d 管全套**（`ArcTubeBuilder`/`ArcTubeBuilderTest`/`vfxgraph_arc_tube.*`/`ARC_TUBE_FORMAT`/`arcTubePipeline`/`tubePipelines`/`R.vfxgraph_arc_tube`）；demo_arc bolt jagged 0.75→0.25（锯齿全交贴图）。新 `ArcBeamTextureTest`（尺寸/白热芯/辉光/确定性，3 用例）。glslangValidator 全过，主 test 818→**816**（删 tube 5 + 增 beam 3）+ editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22e v3.1 修观感（用户反馈「纹理衔接生硬，噪声太丑」）**：根因——① 链节贴图随机游走折线 + 逐节 hash 亮度 = 随机噪声观感；② 6 节平铺接缝（每节路径端点回中心 + 逐节随机亮度硬跳变）= 生硬。修正：**`buildBeamTexture(512,256)` 单条连续平滑螺栓**（多谐波正弦蜿蜒 `sin(u·1.3·τ+0.9)·0.1H + sin(u·2.7·τ+2.1)·0.05H + sin(u·0.7·τ+4.2)·0.03H`，无随机；**行主序修正**——v3.0 原 x 外 y 内导致纹理转置）；**`vfxgraph_arc.fsh` 去 tiling 直接按 `vUv` 采样**（无接缝），亮度改**正弦流动明暗带** `0.45+0.55·(0.5+0.5·sin(u·6·TAU−age·1.2+seed·2.7))`（明暗带沿链流动 = 链感）+ 温和正弦脉动，**全移除 hash**（无随机噪声）。`ArcBeamTextureTest` 增「蜿蜒平滑性」用例（相邻列芯位移 ≤6px，防随机尖刺），共 4 用例。glslangValidator 全过，主 test 816→**817** + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22e v3.2 修观感（用户反馈「边缘衔接不好；贴图感太明显」）**：根因——贴图里的正弦蜿蜒把「画好的螺栓」打印在 ribbon 上 → 贴图感 + 螺栓轮廓硬边（边缘衔接差）。修正：**`buildBeamTexture` 改纯辉光剖面**（中心线固定 v=0.5，沿 u 均匀无蜿蜒无图案；R=白热芯 0.022H、G=宽辉光 0.30H 软边缘过渡）；**闪电形状全交 3D spine**（demo bolt jagged 0.25→**0.8** 强锯齿 = 真实闪电折线）；`vfxgraph_arc.fsh` **尖端 taper 加宽**（0.06→**0.12**，边缘衔接柔和）；保留正弦流动明暗带 + 温和脉动。`ArcBeamTextureTest` 「蜿蜒平滑性」改「剖面沿 u 均匀」（峰值行 v=0.5 固定），共 4 用例。glslangValidator 全过，主 test 817 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22e v3.3 修观感（用户指出「贴图感主要来自软边，可用 glow 替代」）**：`buildBeamTexture` 改**实心亮体（硬边）**——R 通道身体 1 / 边缘窄过渡（`smoothstep` 防锯齿）/ 外侧 0，**无烘焙软辉光渐变**（贴图感根除）；**辉光全部由 bloom（GLOW pass）提供**；`vfxgraph_arc.fsh` **删 `tex.g` 辉光项**，实心亮体过曝 `mix(vColor,white,0.9)·tex.r·2.0` 驱动 bloom；删 `expFalloff` 死代码；保留 3D spine 强锯齿（bolt jagged 0.8）+ 正弦流动明暗带 + 温和脉动 + 宽尖端 taper。`ArcBeamTextureTest` 「白热芯/辉光」改「实心亮体硬边」（中心 R=255、上下 0、亮行 ≈0.30H）；「剖面沿 u 均匀」改亮体带中心（bandCenter），共 4 用例。glslangValidator 全过，主 test 817 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22e v3.3b 颜色/粗细修正（用户反馈「颜色为什么白的，太粗了」）**：颜色白因——`mix(vColor,white,0.9)` 90% 拉白 + 过曝 `·2.0`（additive+bloom 像素白化）；粗细因——实心体 `bodyHalf=0.15H` 太宽。修正：着色器 `mix(vColor,white,0.9)`→**0.35**、`·2.0`→**1.5**（保留电弧本色青蓝，仍过曝驱动 bloom）；`buildBeamTexture` bodyHalf **0.15H→0.08H**（细实心芯，宽度主要由 bloom 辉光决定）；demo_arc bolt width **0.18→0.12**、orbit **0.1→0.08**。`ArcBeamTextureTest` 亮行断言 0.30H→0.16H。glslangValidator 全过，主 test 817 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22f 电弧改用旧 vfx 渲染（用户否定全部自研方案后明确「改用旧 vfx 的 arc 渲染」）**：研究旧管线——`ArcEffectVfx`→`ArcTube`（异步 parallel transport ring 管）→`LightningMeshBuilder`（Position+UV 网格，UV0.x=发射强度）→`LightningRenderer`（`vfx_lightning` 着色器：蓝基色 0.059/0.224/0.710 + 过曝发射 0.80/4.0/10.0 + aces + 不透明度 0.4；`LIGHTNING_TUBE` 透明 / `LIGHTNING_TUBE_BLOOM` additive）。实现：`VfxGraphRenderer.drawArcs` **复用 `LightningMeshBuilder`** 从 `ArcBuffer` spine 建管（parallel transport right/up 同 ArcTube，半径=poly.width，SEGMENT_RESOLUTION=4）；`vfxgraph_arc.vsh/fsh` **重写为复刻 `vfx_lightning`**（GraphCamera + 新 `ArcLightning` UBO）；`ARC_BIND_GROUP` + `ARC_TUBE_FORMAT`（Position+UV）+ `arcTubePipeline(bloomPass)`（透明主 / additive bloom，`render` 传 bloomPass）；删 beam 全套（`buildBeamTexture`/`beamTexture·View·Sampler`/`ARC_RIBBON_FORMAT`/`ArcRibbonBuilder`/`ArcRibbonBuilderTest`/`ArcBeamTextureTest`/`smoothstepClamp`/`expFalloff` 残余/ribbon 缓冲）；demo_arc 细管（bolt width 0.03/orbit 0.02）。主 test 817→**807**（删 10 用例）+ editorTest 101，check 全绿，glslangValidator 全过。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22f 修观感（用户反馈「不要半透明，太白了，需要深蓝一点，太粗了，只有折没有曲」）**：① **不透明**——`writeArcLightning` params opacity 0.4→**1.0**（实心管，不透明）；② **深蓝**——基色 `0.059/0.224/0.710`→**0.02/0.08/0.45**、发射 `0.80/4.0/10.0`→**0.5/1.1/2.8**、**aces 1→0**（aces 把过曝映射冲白，关掉保蓝色）；③ **更细**——demo bolt width 0.03→**0.015**、orbit 0.02→**0.01**；④ **加曲线（有折有曲）**——`BoltPath.buildPolyline` 增 **Laplacian 平滑**（2 迭代 alpha 0.4，内部点向相邻中点移动、端点固定、点数不变、确定性），锐角折点圆成曲线保留蜿蜒形状；demo bolt jagged 0.8→**0.5**。主 test 807 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22f 修观感 2（用户反馈「太粗了，改为三分之一粗，颜色再深蓝些」）**：demo bolt width 0.015→**0.005**（1/3）、orbit 0.01→**0.003**（≈1/3）；`writeArcLightning` 基色 `0.02/0.08/0.45`→**0.01/0.04/0.4**、发射 `0.5/1.1/2.8`→**0.3/0.7/2.0**（更暗、更蓝主导）。主 test 807 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22f 颜色数据驱动**：电弧颜色不再用 `writeArcLightning` 硬编码——`ARC_TUBE_FORMAT` 增 **Color 顶点属性**（Position+UV+Color）；`drawArcs` 按 mesh 顶点序记录每点 RGBA（来自 `Arc` 颜色属性，即 arc 块的 `color` 属性），展开为每顶点色传入 `drawArcTube` 打包；`vfxgraph_arc.fsh` 改 **基色 = 顶点色 × 全局乘数（LightningBaseColor，中性 1,1,1）、发射 = 基色 × 增强系数（LightningParams.z=2.5）× 强度、不透明度 = 顶点 alpha**；`writeArcLightning` 改中性常量；demo_arc 颜色改深蓝（bolt `0.12,0.3,1`、orbit `0.08,0.22,0.95`）。主 test 807 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22f 自然粗细变化**：电弧管粗细在 taper 之上叠加**确定性平滑低频起伏**——`VfxGraphRenderer.thicknessVariation(t, phase)`（两谐波 `1 + 0.16·sin(2π·1.5·t+φ) + 0.09·sin(2π·3.7·t+2φ)`，乘数恒>0 且适中），`drawArcs` 建管时 `radius = poly.width(i) × thicknessVariation`，相位 `φ = (arc.seed() % 10000)·0.618`（每电弧独立、确定性）；所有路径类型（bolt/orbit/surface）通用。新增 `thicknessVariationIsPositiveAndVaries` 单测（恒正/沿线起伏/幅度适中）。主 test 807→**808** + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **BUGFIX imgui color 显示不准确**：根因——`vfxgraph_arc.fsh` 发射增强 `LightningParams.z=2.5` → `color = 基色 × (1+2.5)` = 基色 × 3.5，把所选深蓝冲成亮色/白，与 colorEdit4 显示严重不符。修复：`writeArcLightning` 发射增强 **2.5 → 0.3**（渲染 ≈ 所选题色，保留 bloom 辉光；数据驱动色准确）。主 test 808 + editorTest 101，check 全绿 |
| 2026-08-16 | **电弧颜色零代码常量（禁止代码穷举颜色）**：`ArcLightning` UBO 去掉全部颜色 vec4（原 LightningBaseColor/LightningEmissionColor）——UBO 缩为 1 个 `LightningParams`（16B，仅 aces 开关 + 发射增强 0.3 渲染标量）；`vfxgraph_arc.fsh` 删颜色 uniform，`color = max(vColor.rgb, 1e-4) × (1 + 增强×强度)`——**电弧颜色 100% 由图数据顶点色驱动，代码/着色器零颜色常量**；`writeArcLightning` 只写标量参数。主 test 808 + editorTest 101，check 全绿 |
| 2026-08-16 | **M22f 细微分支 + 飞出火花 + 水蓝**：① **细微分支**——BoltPath 分支宽度系数 0.6→**0.35**、分支长度 0.3~0.6→**0.2~0.4**（细短副闪电）、`branchCount` 上限 3→**6**，demo_arc branch_count **1→3**（BoltPathTest 分支宽度断言同步）；② **飞出火花**——`drawArcs` 末尾 `drawSparks`：每电弧 8 颗，沿主干确定性取点（`hash01` 相位），垂直方向随机飞出（`randomPerpDir` 绕切线旋转）+ 少量前向，`age = fract(arc.age()/period + phase)` 循环飞出，`alpha=(1-age)²` 衰减，颜色=电弧色提亮（mix 向白 0.3），additive billboard（复用 `vfxgraph_particle` 软圆斑 + `sparkPipeline`），主/bloom pass 均可见；③ **水蓝**——demo_arc 颜色改 `bolt 0.2,0.6,1`、`orbit 0.12,0.45,0.95`。主 test 808 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22f 飞出火花改小电弧细丝（用户反馈「是飞出小电弧，不是方块」）**：原火花用 vfxgraph_particle 软圆斑 → 观感为小方块/圆点。新增专用 **`vfxgraph_arc_spark.vsh`**（billboard 沿速度方向强拉伸成细丝，`stretch = clamp(speed·1.0+0.5, 0.6, 3.5)`）+ 复用 particle fsh 软辉光；`sparkPipeline` 改 `vfxgraph_arc_spark`(vsh) + `vfxgraph_particle`(fsh)；`R` 增 `vfxgraph_arc_spark`。飞出观感为**细长小电弧/电火花**沿垂直方向飞出并衰减。主 test 808 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **BUGFIX 飞出火花改尖头小电弧（用户反馈「飞出的不是电弧，是四边形」）**：根因——`vfxgraph_particle.fsh` 的 `smoothstep(0.35,1.0,length(d))` 在拉伸 quad 上中央 35% 为**实心矩形**（仅两端软衰减）→ 读作四边形。修复：新 **`vfxgraph_arc_spark.fsh`**（尖头细丝：`tip = pow(1-|d.y|, 1.6)` 两端收尖成点 × `wid = 1-smoothstep(0.15,0.8,|d.x|)` 横向细，**无实心核心**）；`sparkPipeline` vsh+fsh 均用 `vfxgraph_arc_spark`、bind group 改 `CAMERA_BIND_GROUP`（fsh 无 sampler），`drawSparks` `billboard=false`（不绑噪声/深度纹理）。飞出观感为**尖头小电弧**（中心亮、两端尖、横向细）。主 test 808 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **handoff（电弧渲染 M22f 终态）**：本轮电弧视觉历经 M22c（X 形高斯）→ M22d（A+B 混合）→ M22e（链式光束 v3.0~v3.3）全部被用户否决，最终**改用旧 vfx 电弧渲染（M22f）**并经多轮修观感定型：① `LightningMeshBuilder` 管网格 + `vfxgraph_arc`（Position+UV+Color，颜色数据驱动、零代码常量）；② 不半透明（opacity=vColor.a）、发射增强 0.3（渲染≈所选色）；③ BoltPath Laplacian 平滑（有折有曲）+ `thicknessVariation` 自然粗细起伏；④ 细微分支（宽 0.35×/长 0.2~0.4/上限 6，demo branch_count 3）；⑤ `drawSparks` 飞出尖头小电弧（`vfxgraph_arc_spark.vsh/fsh`：沿速度拉伸 + `tip=pow(1-|d.y|,1.6)` 尖头细丝，非四边形）；⑥ demo 水蓝（bolt 0.2,0.6,1 / orbit 0.12,0.45,0.95）。主 test 808 + editorTest 101，check 全绿。**下一会话第一验证**：`./gradlew graphEditor` 打开 `demo_arc` 肉眼确认；随后 M21 火焰冒烟、M28 移除旧扁平路径、A2 嵌套子图、A3 mesh 渲染。详见「下一步」#2/#3/#4 |
| 2026-08-16 | **M22g 飞出火花改迷你电弧 tube（用户反馈「飞出的小电弧只是单个四边形」）**：弃用 billboard 四边形火花（`vfxgraph_arc_spark.vsh/fsh` + `sparkPipeline` + instanced QUADS）——每颗火花改**迷你闪电 tube**（新 `buildSparkBolt`：沿飞出方向 5 点中点位移锯齿折线，parallel transport 环，复用 `LightningMeshBuilder` + 弧管管线 `sparkTubePipeline`，恒 ADDITIVE，`vfxgraph_arc` 颜色数据驱动，无 sampler）；火花长度 ≈ size×2~4、管半径 `size×0.5×(1−0.85u)` taper + `thicknessVariation` 起伏、alpha=(1−age)²；删除 `sparkPipeline()`/`vfxgraph_arc_spark.*`/`R.vfxgraph_arc_spark`。主 test 808 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22g 火花观感修正（用户反馈「颜色不对/非常粗/太短」）**：① **颜色**——火花色从 `lerp(arc,白,0.3)` 提亮改**纯电弧色**（与主干一致，不再泛白）；② **粗细**——管半径 `size×0.5` → **`size×0.2`**（砍半以上，接近主干细管宽度 0.005）；③ **长度**——`size×(2~4)` → **`size×(8~12)`**（细长小电弧飞出，不再是小点）。主 test 808 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22g 火花轨迹变化（用户反馈「飞出的电弧需要变化」）**：火花不再直线飞出——`buildSparkBolt` 沿垂直平面加**抛物线弯曲**（随机曲率/方向，`curve=±0.35×(0.4+age)` 随 age 增强 → 飞出划弧）+ **行波波动**（`wobble×sin(u·6π+age·9+seed)`，随 age 相位推进 → 飞行中蠕动），曲率/波动轴每颗火花独立（`randomPerpDir` + `cross`）；结合原有长度/锯齿/粗细各异，火花轨迹互不相同且随时间变化。主 test 808 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22h ARC 观感参数数据驱动（去穷举）**：电弧全部观感常量从渲染器移到图上——新 `RenderSpec.ArcRender`（sparks/spark_speed/spark_size/spark_period/spark_travel/spark_length/spark_radius/spark_curve/spark_wobble/thickness/emission），由 `output_arc` 块属性解析（默认值=原硬编码观感，非法回退默认）；`VfxBlocks` 增 `ARC_OUTPUT_PROPERTIES`（output_arc 元数据）；`VfxGraphRenderer` 删 `SPARKS_PER_ARC` 常量，`drawArcs/drawSparks/buildSparkBolt/thicknessVariation(amp)/writeArcLightning(emission)` 全读 `arcRender`；`demo_arc.json` 显式写出全部观感属性；新 `arcRenderParamsAreDataDriven` 单测（缺省/显式/非法回退）。主 test 808→809 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-16 | **M22i 火花波动自然化（用户反馈「太随机了，电弧的波动差别要自然」）**：火花参数从逐颗独立随机改**按主干平滑渐变**——`u=s/(count-1)` index 主导：① 位置沿主干均匀分布（0.12+0.76×(u+轻微抖动)）而非随机散点；② 方向改**稳定扇形展开**（每电弧稳定世界参考垂直方向投影到切线平面 → 扇形平面沿主干连贯，`angle=(u-0.5)×2.0±抖动`，锥形爆发而非各向随机）；③ 速度/周期/行程/相位/尺寸均 `0.5+0.7u` 渐变 + 轻微抖动，火花沿主干依次爆发；④ `buildSparkBolt` 长度/曲率/波动幅度乘 `(0.7+0.5×sparkU)` 渐变，相邻火花连续自然。新增 `clamp01`。主 test 809 + editorTest 101，check 全绿。**编辑器肉眼确认待显示环境** |
| 2026-08-19 | **BUGFIX M22-Rev2 电弧渲染三修（用户报告：编辑器缩放/移动对 arc 无效；顶点连接/片元模式问题）**：① **矩阵/顶点数据**——`VfxGraphRenderer.drawArcTubes` 之前 `camPos`/`identity` 计算了却从未使用（死代码），管顶点写的是局部/世界坐标；`GraphCamera` 视图是纯旋转矩阵（平移必须写进顶点，粒子/轨迹均如此），导致相机平移/缩放与 `WorldTransform`（出生点/跟随/缩放）对 arc 全部无效。新增包内静态 `transformArcTubeVertices`（可单测）：局部 × `overall_scale`（此前解析了但渲染器从不消费）→ `transform.apply` → `-camera.position()`，法线 `applyDirection` 旋转并归一，主/bloom 双 pass 一次修复。② **顶点连接**——`CurveToMeshBuilder` 把整条 `ArcCurve` 的所有点（含互不相连的递归分支，gen 0 主弧 + 分支顺序追加）当**一根连续折线**缝成管 → 主弧末端到分支起点出现跨越空间的管。`ArcCurve` 增 `segment` 数组（`addPoint` 6 参重载），`CurveGenerator` 每根分支分配独立 segment id，`CurveToMeshBuilder` 按 segment 分 run 逐段建管（parallel transport 每 run 重置）。③ **片元模式**——`vfxgraph_arc.fsh` 原 `viewDir = normalize(-vNormal)` 自抵消（rim 恒 0，alpha 恒 0.85×数据，软边/体积感失效）；vsh 增 `vViewDir = normalize(-Position)`（顶点已相机相对），fsh 用 `dot(normalize(vNormal), vViewDir)` 计算真实 rim 软边。④ arc 块（arc_bolt/orbit/surface）增 `origin_x/y/z` 属性（此前硬编码 (0,0,0) 生成，粒子经 buildShape 支持 origin 而 arc 不支持 → 移动发射器对 arc 无效）。新测试：`ArcTubeTransformTest`（6 用例：identity/camPos/overall_scale/translate/组合/法线旋转）、`CurveToMeshBuilderTest`（disconnectedSegmentsAreBuiltAsSeparateRuns + generatorAssignsDistinctSegmentPerBranch）、`VfxContainerFullCatalogTest.arcBoltRespectsOrigin`。注意：LWJGL `BufferUtils` 缓冲是 LITTLE_ENDIAN，`ByteBuffer.duplicate()` 会重置为 BIG_ENDIAN 使浮点读错位（测试勿用 duplicate 读浮点）。glslangValidator 全过，主 test 829 + editorTest 101，check 全绿。**编辑器肉眼复验待显示环境**：`./gradlew graphEditor` 打开 `demo_arc_v2`——相机平移/缩放、`overall_scale`、arc 块 origin 均应生效，分支不再出现跨空间缝合管 |
| 2026-08-19 | **M22-Rev2 电弧复刻 Blender「闪电附着」流水线（用户：参考闪电附着.blend，要求完整复刻 + 通用数据 + 白炽自发光）**：解析参考 `.blend` 几何节点树（`Curve Line→Set Spline Type(Bezier)→Set Handle Positions(沿法线×粗细)→Resample→Set Position(噪声)` + `随机点云阵列`Delete Geometry + `Sample Nearest Surface`(Endpoint Selection)）。**A 曲线生成**——`CurveGenerator` 重写：主弧 `from→to` 两点 + 控制柄沿法线伸开成拱（`generateFromTo` 新入口，旧 `generate` 保留为兼容包装）；`arc_bolt` 接 `from_x/y/z`、`to_x/y/z`（此前读 `height` 硬编码起点，`demo_arc.json` 早写了 from/to 却没用）。**B 噪声**——`NoiseAnimator` 改低频 value noise（Scale2/Detail2/Roughness0.5，2 octave 归一 [-1,1]）+ `Position + time×游离速度` 域扭曲（取代 4-octave 大幅噪声）。**C 表面约束**——`SurfaceConstraint` 改**端点-only** 吸附（复刻 `Endpoint Selection`，中间点保留拱起/漂浮形态）。**D 断续出现**——`arc_bolt` 按 `probability` 随机跳过（复刻 `随机点云阵列` Delete Geometry），产生零星断档。**E 通用数据**——新 `docs/vfx-graph/ARC_SURFACE.md` 定型 `float[]` 通用三角面 + `SurfaceSource` 目标（MC 方块/玩家/OBJ → 通用三角面转换器留作后续目标；`MeshAssets`+`ObjMeshParser`+`SurfaceDistributor`+`SurfaceConstraint` 已就绪）。**F 观感**——`demo_arc_v2.json`/`demo_arc.json` 改 from/to 起拱、`probability` 0.5 断续、颜色白 0.9、`output_arc` emission 3.0 + glow bloom 白炽自发光。测试：`CurveGeneratorTest`（generateFromToArchKeepsEndpointsAndArches + generateFromToBranchesDistinctSegments）、`SurfaceConstraintTest`（endpoint-only + interiorPointsNotConstrained）、`VfxContainerFullCatalogTest.arcBoltRespectsOrigin` 改用 from/to+probability=1。glslangValidator 全过，主 test + editorTest，check 全绿。**编辑器肉眼复验待显示环境**：`./gradlew graphEditor` 打开 `demo_arc_v2`——from→to 起拱电浆束、断续出现、白炽 glow；容器执行器端点表面吸附（`SurfaceConstraint` 接入）按 ARC_SURFACE.md 目标待做 |
| 2026-08-19 | **M22-Rev2 电弧改「两点锯齿闪电」（用户：好丑…不如仿照 blender 做两点连线场景）**：第一版平滑大拱管（`handleLen = chordLen×0.5`）观感是**粗光滑彩虹弓**，不像闪电。改为**递归中点位移 fractal jagged**——`generateArch` → `generateBolt`：from→to 两点固定，`JAGGED_DEPTH=4` 递归中点沿切平面随机位移（幅度 `chordLen×0.12/(depth+1)` 递减）+ 轻微法线起拱（0.3），等距重采样，得到**细锯齿闪电折线**。`demo_arc_v2.json` 收敛为两闪电观感：`width 0.004`（细丝）、`color 1,1,1`（白炽）、`output emission 5.0 + glow`、`branch_depth 1 / branch_count 3` 细分支。`CurveGeneratorTest`（arch 起拱 + 分支独立 segment）保持通过，主 test 832 + editorTest 101，check 全绿。**编辑器肉眼复验待显示环境**：`./gradlew graphEditor` 打开 `demo_arc_v2`——细锯齿白炽两点闪电 + 分支 + glow bloom |
| 2026-08-19 | **解压 Blender 项目到文件夹 + 写权威参考 docs**：用 Blender Python 脚本完整导出 `闪电附着.blend`（Blender 5.2.0）到 `docs/vfx-graph/blender-reference/`（`scene.json` 物体/材质、`node_groups.json` 全部几何节点组含 276 节点主组 + 6 子组 + link 图、`group_inputs.json` 组输入默认值、`texts.md` 内嵌文本）。基于导出写 `docs/vfx-graph/BLENDER_ARC_REFERENCE.md`（权威分析，作废此前猜测）。**关键确认（纠正此前错误理解）**：① 场景 = **Plane（原点地面）+ Sphere（悬浮 z≈4.34）**；② 主弧在 **Plane 表面布点**（`随机点云阵列`：density 3.8 + 随机删 ~50% + 按帧周期断续），每点实例化**短 Curve Line**（长度=`电弧高度`1.0），沿法线定向 + `Set Handle Positions`（控制柄×`电弧粗细`0.78）Bezier 起拱 + `Resample(12)` + 噪声位移（`噪波强度`0.5/`游离速度`0.5）+ **端点吸附**（`Sample Nearest Surface`+`Endpoint`）；③ **「两个物体连接」= 第二套弧**：`输入菜单`(接触对象=Sphere) → `Sample Nearest Surface.002` → `Vector Math.017(DISTANCE)` → `Compare GREATER_THAN 接触范围4.1` → `Delete Geometry` → `Instance on Points.002`——**只保留距 Sphere 4.1 内的点生成弧**，从而平面↔球之间出现电弧连接；④ 材质 `electricity` = Emission `Attribute(LColor)` 蓝 [0.13,0.21,1.0] × `Light×6` 白炽自发光。**结论：当前 `arc` 是「单条 from→to 闪电」结构，与 Blender「表面多点短弧 + 按接触对象距离剔除的连接弧」结构不同**，需新增表面布点+断续时序、per-point 短弧、按距离剔除的第二套弧、容器执行器接入端点表面吸附（详见 BLENDER_ARC_REFERENCE.md §4 差距表）。主 test 832 + editorTest 101，check 全绿 |
| 2026-08-22 | **M29 Blender「闪电附着」三 VFX 忠实复刻**：基础改造——`ArcCurve.setSurface` + `SurfaceConstraint` 升级真最近表面点（`MeshDistance.nearestPoint`，Ericson Closest Point on Triangle，复刻 `Sample Nearest Surface`）+ `VfxSystemSimulator.step` 每帧端点吸附接线（ARC_SURFACE.md 目标完成）+ `MeshAssets` 内置 `builtin:plane`/`builtin:sphere` + `CurveGenerator.generateSurfaceArc`（per-point 短弧 Bezier 起拱）+ `MeshDistance`（最近距离/最近点）。三块——`arc_surface` 重写（表面布点 + 概率删减 + 断续时序 + 端点吸附，删除未实现 walk_speed）、`arc_contact` 新增（接触闪电：距离剔除 + 端点吸附接触面 + contact_origin 定位）、`arc_spark` 新增（粒子火花：弧→点 + 溅射 + 迷你管，只从带表面弧派生防指数增长）；VFX 目录 46→48。三示例资产 `surface_arc.json`/`contact_arc.json`/`spark.json` + SampleAssetsTest 10 资产全过。单测 +50（SurfaceArcBlock/ContactArcBlock/SparkArcBlock/MeshDistance/MeshAssetsBuiltin/CurveGeneratorSurfaceArc/VfxSystemSimulatorSurfaceSnap/目录），主 test 809→859 + editorTest 101，check 全绿。**编辑器肉眼复验待显示环境**：`./gradlew graphEditor` 打开 `surface_arc`/`contact_arc`/`spark` |
| 2026-08-22 | **M29 三 VFX 用户否决（电弧数量巨大 / 编辑器场景与 Blender 不符 / 需 Blender 测试场景）**：用户实测三个资产全部不符预期——弧数成千上万（`arc_surface/arc_contact` 每帧无节流 spawn，`SurfaceDistributor.distribute` frequency 门控死代码 `timePhase>probability*10` 恒不成立，Blender `Compare(Frame MOD 0.03)` 断续时序未复刻 → 稳态 ~450 弧；`arc_spark` 对每条带表面弧每控制点每帧派火花 → ~1000+ 指数放大）；编辑器场景不符（`ViewportPanel` 仅 ImGuizmo `drawGrid`，平面/球表面三角形从不渲染，无可见地面/球/连接观感）。用户明确期望 = **一个球 + 一个正方形表面，两者电弧连接 + 正方形表面爬行电弧 + 粒子**。从 `blender-reference/` 核实引用（scene.json：Plane 2×2 + Sphere loc(0.52,0.38,4.34) 半径≈1 + 材质 electricity/spark/touch_electricity；node_groups.json 主组三套系统：`随机点云阵列`表面布点断续→per-point Curve Line Bezier 起拱→Resample→噪声→`Sample Nearest Surface`端点吸附→Curve to Mesh；`输入菜单`(Sphere)→`Sample Nearest Surface.002`→DISTANCE→`Compare >4.1`→`Delete Geometry`→`Instance on Points.002` 连接弧→端点吸附球面；`Curve to Points`→`Delete(0.5)`→速度/重力→`Instance on Points.001` 迷你 Curve Line 对齐速度→`Curve to Mesh.001` 粒子）。方向待重做（见「进行中」M29 条目与 TASK_LEDGER M29b）：修复 spawn 节流 + 视口渲染表面网格 + 建 `demo_blender_arc.json` 测试场景 |
| 2026-08-23 | **M29b 三 VFX 返工（弧数爆炸/场景/测试场景三点全修复，check 全绿）**：① **帧周期门控**——`SurfaceDistributor.distribute` 删死代码门控；`arc_surface`/`arc_contact` 增 `frame_period`/`fps`（frequency>0 时只在 `frame%frame_period==0` 帧 spawn 一批，frequency≤0 保留 legacy 每帧）→ 稳态弧数 <30；② **火花有界**——`ArcCurve.fresh` 标记（`ArcBuffer.add` 置 true/`advance` 开头清）+ `arc_spark` 只处理 fresh 带表面弧 + `max_sparks` 上限；顺带修 `ArcBuffer.add` 未重置 `age` 的回收弧立即死亡 bug；③ **视口表面网格**——`VfxGraphRenderer.SurfaceMesh`/`drawSurfaces`/`vfxgraph_surface.vsh.fsh` + `render()` surfaces 参数，`VfxPreview.collectSurfaces` 从容器模型扫描 mesh/contact_mesh+origin → 编辑器可见 2×2 地面 + 悬浮球（半透明实体面，"和blender一样"）；④ **新测试场景** `demo_blender_arc.json`（地面 + 悬浮球(0.52/4.34/0.38) + 面弧 + 连接弧 + 火花，全断续低频，单 GLOW 白炽）+ SampleAssetsTest 10→11；⑤ 既有三资产（surface_arc/contact_arc/spark）参数低频化。新 `M29bArcGatingTest`（稳态 <30 / frequency=0 legacy / spark fresh-only / 火花不递归），主 test 859→**863** + editorTest 101，check 全绿，glslangValidator 过。**编辑器肉眼复验待显示环境**：`./gradlew graphEditor` 打开 `demo_blender_arc`/`surface_arc`/`contact_arc`/`spark` |
| 2026-08-23 | **M29c 电弧噪声累积漂移修复（用户反馈「电弧怎么都往一个方向飘，感觉片元连一起；都长一个样，没有粒子/爬行/连接」）**：根因——`NoiseAnimator.animate` 每帧相对**当前**位置（已被上一帧位移过）叠加噪声位移 → 位移逐帧累积，全体电弧同向飞走 + 拉长；且全部电弧共用同一噪声种子 → 同向形变观感雷同。修复：① `ArcCurve` 增**基准位置**（`baseX/baseY/baseZ`，`addPoint` 记录；`setPoint` 只改当前；`copyRange` 一并拷）→ `NoiseAnimator` 改 `pos = base + noise(base)`（Blender 每帧从基准几何重新求值语义），每帧基准附近有界摆动不漂移；② 噪声种子**每弧独立**（`arcNoiseSeed + arc.seed()*7919`，复刻 Blender 唯一ID 子组）。回归 `NoiseAnimatorTest.repeatedAnimationDoesNotDrift`（60 帧位移有界 <1.5）。主 test 863→**864** + editorTest 101，check 全绿。**编辑器肉眼复验待显示环境**：`./gradlew graphEditor` 打开 `demo_blender_arc` 确认电弧不再漂移/不雷同、火花/爬行/连接可见 |
| 2026-08-23 | **M30 电弧一比一复刻 Blender「闪电附着」（用户否决 M29 拙劣模仿，用实际 .blend 提取权威参数重做）**：Blender 5.2 解压 `闪电附着.blend`，提取 modifier 实际生效 socket 值（`m.properties.inputs.Socket_xx.value`，与界面默认/socket 缓存不同）与全部 FloatCurve 控制点、实测 frame40 几何（表面弧 spine/半径/弧数）。① **表面电弧**——`CurveGenerator.generateSurfaceArc` 重写：基线平躺表面（Curve Line+Align axis=X）沿切平面随机方向、控制柄沿**法线**上推（FloatCurve.001 age 成长×Random 0.4~1.2×高度×粗细）、重采样 12 点→帐篷拱；管半径 FloatCurve.002(端粗中细)×FloatCurve.005(age 衰减)×基准（实测 0.0024~0.0034）；弧拱基线存 ArcCurve，`VfxSystemSimulator` 每帧 `sampleSurfaceArch` 重采样；② **接触闪电**——`CurveGenerator.generateContactArc` 直线弧（P→接触面最近点 N，无拱），仅末端吸附（pinStart，Blender End Size=1），半径仅 age 衰减（flatRadius）；③ **粒子**——`arc_spark` 重写：弧→点概率删减(0.48)+溅射方向+重力+迷你管对齐速度，`ArcCurve.sparkVelocity` 每帧速度/重力积分；④ **渲染**——`vfxgraph_arc.fsh` 改不透明自发光（Blender Emission，alpha=1，color=顶点色×Light×6）；⑤ **资产**——demo_blender_arc/surface_arc/contact_arc/spark 用权威参数重写（密度 1.0/1.47、出现概率 0.0204/0.15、游离 1.5、寿命 20/6、接触范围 4.1、粒子密度 0.48/缩放 0.83/溅射 1.23/重力 -0.9）。验证：新 `BlenderArcGeometryTest`（6 用例）+ 更新 CurveGeneratorSurfaceArcTest/ContactArcBlockTest/SparkArcBlockTest 断言为新语义，arc 相关测试全绿，`./gradlew check` 全绿，glslangValidator 过。**待显示环境肉眼复验**：`./gradlew graphEditor` 打开 `demo_blender_arc`——地面帐篷拱/平面↔球连接弧/溅射火花/亮蓝白炽，弧数稳态稀少 |
| 2026-08-23 | **M30b 电弧一比一复刻修复（用户否决 M30「看起来直线、无弯折、持续」→ 用实际 `~/Downloads/闪电附着.blend` 提取缺失 FloatCurve 后修复）**：Blender 5.2 headless 提取 6 条此前缺失曲线（FloatCurve 无名 pa 脉冲/`.007` shapep、`.004` Light 亮度先亮后灭、`.009` 接触半径/发光生命、`.006`/`.008` 寿命沿弧变化）。**根因 5 处**：噪声弱 ~9 倍（常量 pa0.27×0.27 vs Blender 逐点 `脉冲×Random[0.4..2.2]×0.5`，端点 0）；无仿真区爬行（Blender Set Position `cross(随机,法线)×Random[0.01..0.03]×游离速度` 弧基座游走）；无 age 亮度闪烁（Light=`.004`×亮度+0.33×亮度 ×6）；弧跨度恒等（实例 Scale=Random[0.4..1.2]×宽度）；控制柄多乘电弧粗细（拱矮 22%）；漂移语义错误（`Position+time×游离速度` → 实为 `Position+(1,1,1)×场景秒`）。**修复**——`NoiseAnimator` 逐点 `(noise−0.5)×pa×噪波强度` + 漂移=场景秒；`sampleSurfaceArch` 每点写 pa（确定性）、span=height×实例随机跨度、handle 去 curve、接触半径走 `.009`、弧基座中心+累积 wander；`VfxSystemSimulator.wanderArcBase` 仿真区爬行 + 端点 SurfaceConstraint 拉回；`VfxBlocks` arc_surface/arc_contact 接线 `noise_strength`/`drift_speed` 到弧；`VfxGraphRenderer.arcLight` 按 age 烘焙亮度（surface=.004+0.33 / contact=.009 / spark=.003）进顶点色；demo_blender_arc 接触色 1→0.5 灰补偿共享 ×6 发射。验证：新 `M30FaithfulReplicationTest`（10 用例）+ BlenderArcGeometryTest span 语义更新，主 test 870→**881** + editorTest 101，check 全绿。**待显示环境肉眼复验**：`./gradlew graphEditor` 打开 `demo_blender_arc`——弧爬行游走/中段抖动/大小各异/亮度闪烁/平躺→帐篷拱 |
