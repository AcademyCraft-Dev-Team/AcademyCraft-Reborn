# 本地物理化结构多实例渲染性能排查

日期：2026-10-20，时间均为 UTC+08:00。范围：用户正在运行的 `test2` 现场、该存档副本的同视角渲染回归，以及本轮相关实现。原存档与用户游戏进程未用于自动化写入测试；副本的自动保存仅发生在独立运行目录。

本轮确认并修复了一个主要客户端热点：逐块移动方块渲染造成 `PreparedRenderType.equals` 和绘制批次线性查找大量重复。真实 `test2` 副本在相同视角、18 个结构、1,807 个视图方块下，**9.12 → 187.83 FPS，约 20.59 倍**；最终无遮挡截图已消除批处理初版的黑区，实际提交顶点的 UV、光照及位置检查通过。初次实验的 239.88 FPS 存在画面回归，只保留为诊断过程，不作为验收数据。本轮没有新的同硬件 Sable A/B，不能据此宣称达到 Sable。

## 现场证据与根因

用户现场进程 PID 为 `24304`。保留的证据：

- `build/test2-live-before.jfr`：约 45 秒 JFR profile 录制。
- `build/test2-live-before-threads.txt`：21:31:14 的线程快照。
- `build/test2-live-hot-stacks.txt`：初次热点统计。
- `build/test2-live-window-stacks.txt`：修正线程识别后的完整统计。

线程快照中，Render thread 累积 CPU 时间约 917 秒，已运行约 999 秒；服务端线程没有相同程度的占用。单次线程快照停在 `GlCommandEncoder.applyPipelineState → drawIndexed → PreparedRenderType`，之后的持续采样进一步确定了主要 Java 热点：

```text
PreparedRenderType.equals
  → ArrayList.indexOfRange / indexOf
  → RenderTypeFeatureRenderer$Group.getOrAddDraw
  → MovingBlockFeatureRenderer.putBakedQuad
  → ModelBlockRenderer
```

JFR 共 3,274 个 `jdk.ExecutionSample`，其中渲染线程 3,245 个；包含 `PreparedRenderType.equals` 路径的渲染采样为 **2,839 / 3,245，即 87.488%**。这是执行采样占比，不是精确计时的 CPU 百分比。

同一约 45 秒现场录制只有 3 次 `jdk.GCPhasePause`，合计 **6.5877 ms**、最长 **2.3509 ms**。该证据不支持把持续个位数帧率归因于 GC 停顿。

原实现把大部分结构中的每个可见方块作为独立 `MovingBlockRenderState` 提交。原缓存批处理仅覆盖至少 512 格且带 `solid` 标记的结构，实际草地、树叶和不规则结构大量落入逐块路径。Minecraft 26.2 的绘制分组在 `getOrAddDraw` 中用列表查找已准备的渲染状态，逐块及逐面重复准备、比较使成本随提交数量急剧增长。现场表现与该路径一致；不能仅凭方块总数估计成本。

## 最终真实副本回归

修复前证据为 `run/native-sublevel-scene-smoke-before-r1`、`build/test2-scene-before-r1.log`；最终批处理、逐顶点光照及 UV 修复版本为 `run/native-sublevel-scene-smoke-after-r5`、`build/test2-scene-after-r5.log`。均从同一源快照准备独立副本，854×480、相同玩家视角，等待加载并暂停集成世界后测量约 15 秒。最终版本在预热后、计时前执行模型及实际提交顶点检查，避免初次区块光照未到达时错误判定测试夹具不可用。

| 指标 | 修复前 before-r1 | 最终 after-r5 |
| --- | ---: | ---: |
| 结构数 / 视图方块数 | 18 / 1,807 | 18 / 1,807 |
| 采样帧数 | 137 | 2,818 |
| 区间平均 FPS | 9.1208 | 187.8254 |
| 帧间隔 P50 | 108.7518 ms | 5.1187 ms |
| 帧间隔 P95 | 116.7993 ms | 6.4362 ms |
| 最终材质批 / 逐块 fallback / 缓存 quad | 未采用该批处理 | 32 / 0 / 5,106 |

平均帧率比约 **20.59 倍**。这是保留用户 `enableVsync:true`、`maxFps:260` 设置下的单场景实测，不是无上限吞吐或服务端物理性能倍率。

已实际打开并对照下列截图：

- `run/native-sublevel-scene-smoke-fallback-r1/screenshots/scene.png`：强制原版逐块路径，约 9.345 FPS，地形结构正常。
- `run/native-sublevel-scene-smoke-after-r2/screenshots/scene.png`：中间版本存在大面积黑区，验收失败。
- `run/native-sublevel-scene-smoke-after-r4/screenshots/scene.png`：仅修正采光仍有黑区，不能放行。
- `run/native-sublevel-scene-smoke-after-r5/screenshots/scene.png`：修正顶点编码后黑区消失，地表、草叶、树木可见，未发现明显错贴或结构缺失。

这次静态截图验收不等于逐像素完全相同；活体实体位置、世界推进及光照更新时间会产生差异。透明叠层与运动中的复杂模型仍需持续场景观察。

最终 JFR 窗口为 **22:14:56–22:15:11**，结果保存在 `build/test2-after-r5-window-stacks.txt`：

| 采样指标 | 修复前窗口 | 最终 after-r5 窗口 |
| --- | ---: | ---: |
| 总执行采样 | 1,527 | 2,096 |
| 渲染线程采样 | 1,377 | 1,288 |
| 包含 `PreparedRenderType.equals` | 1,180，85.694% | 1，0.078% |
| 包含 `getOrAddDraw` | 1,188 | 3 |
| 包含 `MovingBlockFeatureRenderer` | 1,293 | 0 |
| 包含 `CustomFeatureRenderer` | 0 | 84 |

最终残留的唯一 `PreparedRenderType.equals` 栈来自 `ModelFeatureRenderer.prepareModel`，不是结构的 `MovingBlockFeatureRenderer` 路径。当前主要渲染采样转为世界光照查询、AO 计算及顶点写入；没有宣称所有绘制分组比较从整个游戏中消失。最终窗口 GC phase pause 为 29 次、合计 77.6810 ms、最长 5.2223 ms。后台字体生成线程另有 758 个执行采样，绝对帧率仍受本机并发任务影响。

## 次要回归：16 × 128 格移动发光石

为覆盖非暂停的移动负荷，使用 `SubLevelMultiClientSmoke` 的 `moving=true` 模式，在每个服务端 tick 给 16 个结构施加预定的速度，每个结构 128 格发光石。该探针没有复现用户混合草叶场景的原始极低帧率，因此只能作为补充回归。

前后证据分别为 `build/multi-before-r1.log`、`build/multi-after-r1.log`，运行目录为 `run/native-sublevel-direct-smoke-multi-before-r1` 与 `run/native-sublevel-direct-smoke-multi-after-r1`。窗口 1280×720；两份保存的 options 均为 `enableVsync:false`、`maxFps:260`、`renderDistance:16`、`simulationDistance:12`。探针预热 80 个客户端 tick，采样 240 tick，前后各得到 239 个服务端事件区间样本。

| 指标 | 修复前 | 修复后 |
| --- | ---: | ---: |
| 客户端 tick 采样 FPS 均值 | 367.3958 | 486.7542 |
| tick 采样 FPS 最低值 | 268 | 391 |
| 实际采样帧数 | 4,517 | 5,952 |
| 帧间隔 P50 | 2.4234 ms | 1.7832 ms |
| 帧间隔 P95 | 4.1297 ms | 3.1626 ms |
| 服务端 tick 事件区间 P50 | 5.7666 ms | 5.5034 ms |
| 服务端 tick 事件区间 P95 | 11.1349 ms | 10.3331 ms |

该结果未显示新批处理在此移动负荷下产生性能回退。服务端样本计时围绕 `ServerTickEvent.Pre/Post`，不包含探针在 Pre 开头注入运动速度的时间。样本窗口较短，JIT、后台字体任务与系统调度都可能影响数值；两次运行的微小服务端差值不能证明物理引擎存在同等比例的普遍改善，更不能用于推导与 Sable 的性能差距。

## 真实副本中间实验（初版批处理有画面回归）

源快照位于 `run/native-sublevel-scene-smoke-source`。前后运行使用分别准备的独立副本：

- 修复前：`run/native-sublevel-scene-smoke-before-r1`，日志 `build/test2-scene-before-r1.log`。
- 修复后：`run/native-sublevel-scene-smoke-after-r1`，日志 `build/test2-scene-after-r1.log`。

探针为 `SubLevelScenePerformanceSmoke`。以保存的玩家位置和朝向进入副本，等待后暂停集成世界，测量约 15 秒帧间隔。窗口为 854×480。原 `run/options.txt` 与两个副本均为 `enableVsync:true`、`maxFps:260`，保留了用户的垂直同步与帧率上限设置。前后记录的玩家坐标均为 `(95.25830086118344, 63.0, -64.16241278079606)`，yaw `0.8979492`，pitch `4.2000217`。

| 指标 | 修复前 | 修复后 |
| --- | ---: | ---: |
| 结构数 | 18 | 18 |
| 视图方块数 | 1,807 | 1,807 |
| 采样帧数 | 137 | 3,599 |
| 区间平均 FPS | 9.1208 | 239.8776 |
| 帧间隔 P50 | 108.7518 ms | 4.1511 ms |
| 帧间隔 P95 | 116.7993 ms | 4.9582 ms |

该次中间实验平均帧率比约 **26.3 倍**，因光照回归不能当作最终验收倍率。239.88 FPS 很可能已受到约 240 Hz 垂直同步节奏限制，这是依据已开启 VSync 与测得帧率作出的推断；本轮没有关闭 VSync 测量无上限吞吐。视图方块数是客户端投影内容计数，不等于包含内部隐藏块的完整物理结构体积。

前后视图材质计数完全一致：

| 材质 | 数量 |
| --- | ---: |
| 泥土 | 775 |
| 草方块 | 721 |
| 橡树树叶 | 127 |
| 石头 | 122 |
| 白桦树叶 | 53 |
| 白桦原木 | 4 |
| 橡树原木 | 3 |
| 煤矿石 | 1 |
| 铜矿石 | 1 |

修复后实际提交诊断为 **32 个材质批、0 个逐块 fallback、5,106 个缓存四边形**。因此本次收益覆盖了现场草、土和树叶的组合，并非只优化纯石头或单一不透明材质。

### 中间实验的 JFR 分析方法

两个副本均从 JVM 启动时录制 JFR。JFR 中该 Java 线程的名称仍可能保留初始的 `main`，不能只用字符串 `"Render thread"` 过滤，否则会错误得到“没有渲染样本”。

`build/JfrHotStacks.java` 已改为先从 Minecraft 渲染栈识别 Java thread ID，再按该 ID 分析指定时间窗。两个副本均识别到 `main#3`，现场录制则显示 `Render thread#3`。

| 采样指标 | 修复前窗口 21:38:12–21:38:27 | 修复后窗口 21:46:25–21:46:40 |
| --- | ---: | ---: |
| 总执行采样 | 1,527 | 1,368 |
| 渲染线程采样 | 1,377 | 631 |
| 包含 `PreparedRenderType.equals` | 1,180，85.694% | 0 |
| 包含 `getOrAddDraw` | 1,188 | 0 |
| 包含 `MovingBlockFeatureRenderer` | 1,293 | 0 |
| 包含 `CustomFeatureRenderer` | 0 | 141 |

结果保存在 `build/test2-before-window-stacks.txt` 和 `build/test2-after-window-stacks.txt`。这确认热点调用路径在采样窗口中已消失，而不仅是 FPS 数字变化。采样零次不等于对所有未来场景证明绝不会调用该方法。

两个测量窗口的 GC pause 总量分别为 26.9871 ms 和 60.6390 ms，最长分别为 6.1005 ms 和 5.1617 ms。修复后每秒绘制帧数大幅增加，绝对分配与 GC 次数不能直接按相同墙钟时长推断单帧分配变差；本轮未测每帧分配量。

## 采用的实现

参考了本地 Sable 源码中的机制：

```text
D:/mcmodtest/create-fork/.porting/sources/Sable/common/src/main/java/
  dev/ryanhcode/sable/sublevel/render/vanilla/VanillaChunkedSubLevelRenderData.java
```

该实现按 section 的内容变更标记编译网格，位姿变化复用几何；其 GPU 网格、后台编译和 shader 路径是更完整的独立架构。本轮采用内容与位姿分离的原则独立实现，没有把 Sable 的完整 GPU 管线接入本地后端。

本轮变更：

1. `BatchedBlockGeometry` 按不可变视图缓存局部模型几何，去除 512 格门槛。使用原版模型编译、实际邻块遮挡与模型偏移；不把外层 AABB 当作实心体剔除内部面。
2. SOLID、CUTOUT、TRANSLUCENT 分开提交，每个结构最多三个普通材质批。透明层沿用 `translucentMovingBlock` 的排序和输出目标。单个草块、树叶或玻璃不会令整结构退出批处理。
3. 局部 AO 与原模型顶点颜色烘焙后保留；草叶等注册的 tint source、世界光照和随姿态变化的方向明暗独立更新。Chest 保持专用动画路径，裂纹仍使用原逐块模型。
4. 内容身份、模型资源集、AO 设置、树叶画质变化使缓存失效；单纯移动、旋转复用网格。仍需特殊处理且未受该缓存支持的模型保留既有 fallback。`outlineColor != 0` 的 glowing/team 轮廓结构单独沿用原版逐块路径，保证轮廓行为；普通结构仍走批处理。
5. `TerrainColliderCache` 按必须覆盖的 section 范围复用地形计划，避免每个小数位姿变化都重新构建、排序 halo。新增方块、空 section 变实、区块替换、覆盖范围变化仍触发更新，覆盖不完整仍禁止物理前进。
6. `SubLevelPhysicsUniverse.readMotion` 复用主刚体已经读取的状态，消除每次查询对主刚体的重复 JNI 解码。

后两项有独立正确性检查安排；上述 26.3 倍不能归因于它们，也不能当作服务端物理优化倍率。

### 初版光照回归与修正

`run/native-sublevel-scene-smoke-after-r2/screenshots/scene.png` 出现大面积黑色结构；`run/native-sublevel-scene-smoke-fallback-r1/screenshots/scene.png` 在同一快照、同视角强制原版逐块渲染后正常。`build/test2-scene-fallback-r1.log` 的诊断显示，模型顶点颜色均非黑，但初版缓存中多个结构的所有方块光照为零。

进一步分离验证发现两个问题。初版用宿主世界中方块中心的单个光照值覆盖整个方块，丢失了原版模型的邻面和 AO 顶点采样；实心宿主格的中心可能无光，而外侧面有光。它还只按 game time 失效，暂停时异步光照数据到达后可能永久保留首次零光照。但仅修正光照的 after-r4 已通过逐顶点对照，黑区仍完全存在，说明不能将截图黑区单独归因于采光。

黑区的确定性编码错误是手写解包将纹理图集的 U/V 顺序颠倒。Minecraft `UVPair.pack` 将 U 放高 32 位、V 放低 32 位，初版批提交相反，因而采到了错误的图集区域。最终改为调用原版 `VertexConsumer.putBakedQuad` 完整写顶点，统一遵守 UV、overlay、光照自发光和模型法线规则。新增录制消费者检查直接调用生产批提交函数，核对提交后的每个顶点 UV、光照和位置；不再仅检查提交前缓存内容。after-r5 的实际提交检查和截图均通过，结果见最终回归。

修正后缓存原始局部 quad 及其 AO/flat 采样契约，通过原版 `EnhancedBlockModelLighter` 刷新逐顶点光照。每格使用错开的 50 ms 墙钟刷新区间，覆盖暂停时光照变化；不重新收集模型部件、不逐块提交渲染。全局弱键网格缓存仅持有 quad 与采样契约，实例采光上下文保存在投影渲染状态中，避免反向持有视图键。动态模型换 quad 身份或同 quad 复用不同采光契约时回退原版路径。新客户端检查逐顶点对照原版光照，另验证“宿主实心格中心零光照、上方有光”的地表顶面不能变黑。

## 验证与复测方法

`compileJava`、`compileGametestJava` 已通过。本轮客户端模型检查日志为：

```text
PROJECTION_MESH_CHECKS PASS adjacent=10 sparse=12 mixed_fallback=0
grass_tint=true leaves_tint=true glass_sorted_layer=true slab_height=true
pose_reuse=true crack_stage=4 ao_invalidation=true
vanilla_vertex_light=true exposed_face_light=true
submitted_uv=true submitted_light=true submitted_position=true
```

这些检查验证相邻方块剔面、空洞面保留、混合材质批处理、草叶染色值、半砖高度、Chest 独立路径、位姿复用、裂纹、AO 设置失效、原版逐顶点采光、暗中心亮表面场景，以及实际提交顶点的 UV/光照/位置。glowing 轮廓回退是在 after-r5 启动后补充的最小分支，新增路径选择断言待最终验证汇总；它不改变本次 `outlineColor == 0` 场景的批处理性能路径。

复测必须先从保留的源快照准备**新的** `run/native-sublevel-scene-smoke-*` 目录，使其包含 `saves/test2/level.dat` 及一致的设置，不得把目录参数指向原 `run`。以下是修复后复测命令形式；目录名需对应已准备的独立副本：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
    -I validation/native_structure/sublevel_scene_benchmark.init.gradle `
    -PacademySceneDirectory=run/native-sublevel-scene-smoke-after-r5 `
    --console=plain
```

init 脚本设置 `academy.sublevelSceneSmoke=true`、`--quickPlaySingleplayer test2`、854×480 窗口，以及 `-XX:StartFlightRecording=...,settings=profile,dumponexit=true`。应在同一源快照、相同依赖和客户端设置上对照。仅重跑当前已修复源码不能重建旧实现基线；本轮修复前已编译产物启动所得日志和 JFR 作为旧基线保留。

以下命令复核本轮已保存的 JFR 测量窗口，辅助文件与输出均在 ignored `build/` 中：

```powershell
. ./build/native-physics-p16-env.ps1
java build/JfrHotStacks.java `
    run/native-sublevel-scene-smoke-before-r1/scene.jfr `
    2026-10-20T21:38:12+08:00 2026-10-20T21:38:27+08:00
java build/JfrHotStacks.java `
    run/native-sublevel-scene-smoke-after-r1/scene.jfr `
    2026-10-20T21:46:25+08:00 2026-10-20T21:46:40+08:00
java build/JfrHotStacks.java `
    run/native-sublevel-scene-smoke-after-r5/scene.jfr `
    2026-10-20T22:14:56+08:00 2026-10-20T22:15:11+08:00
java build/JfrHotStacks.java build/test2-live-before.jfr
```

线程名修复后的统计工具同时输出实际采样时段、Java thread ID、热点栈和 GC pause。指定窗口为左闭右开，日期时间参数必须带时区。

## 适用边界与待验证项

- **纯发光石基准不能代表用户现场。** 初始 16 个结构、每个 128 格的移动发光石探针已达平均 367.40 FPS，帧 P50/P95 为 2.4234/4.1297 ms，服务端 tick P50/P95 为 5.7666/11.1349 ms（`build/multi-before-r1.log`）。它没有复现 `test2` 的主要材质与渲染状态组合，不能用于否定用户报告。
- **没有新的同硬件 Sable A/B。** 本轮读取了 Sable 架构并测量本地实现前后变化，未用相同存档内容、相同视角和相同硬件重新测量 Sable，因此不能宣称本地性能已经等同或超过 Sable。
- **测量主要针对暂停场景渲染。** 不覆盖飞行、碰撞密集场景、结构创建时的所有长帧，也不能替代 H 技能、软植被、挖掘掉落事件的独立功能回归。
- **当前静态截图已验收，其他运动场景需持续观察。** after-r5 已实际查看草地和树木，黑区消失；透明叠层、运动中的阴影和裂纹仍不由该静态图覆盖。
- **世界光照逐顶点采样有刷新间隔。** 光照修正沿用原版 lighter，在正常帧节奏下每格约每 50 ms 刷新一次，且各格错开；不是逐帧重新编译模型。未接入 Sable 的专用 GPU shader 管线。
- **glowing 结构保留原版轮廓路径。** 本轮未实现轮廓材质批处理，大量同时 glowing 的结构不能套用普通结构的性能提升数据。
- **统计范围是单机一次真实场景前后回归。** 后台字体生成、JIT、其他运行中的客户端等可能影响绝对帧率；相同视图内容、数量、玩家视角和热点栈消失提供了本次修复的直接证据。尚需持续场景测试才可判断长期稳定性与其他硬件表现。
- 整轮要求的单测、开发/发布构建以及其他功能烟测由主任务统一记录；本报告不把尚未汇总的检查标成通过。

`run/`、`build/`、JFR、截图与日志均为本地证据，不应提交进仓库。
