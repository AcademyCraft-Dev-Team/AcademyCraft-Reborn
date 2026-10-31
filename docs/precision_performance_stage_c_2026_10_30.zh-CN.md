# 精密操作 C 阶段：测量与性能优化

基线提交 `0ad66ef7`（批次 B）。本批先增加观测和可复跑夹具，采集基线后再修改热点。本轮编译、查询及大图热点优化已验证，用户已确认本批 41 个相关文件的本地提交，提交信息为“优化精密操作编译查询与大图性能”，以包含本记录的提交为准；总方案 C 的多人/网络等完整矩阵仍待后续。完整证据见[机器报告](../validation/precision_nodes/precision_batch_c_validation_2026_10_30.json)。

## 测量范围与方法

- Windows 11、Core i9-14900KF（24 核 / 32 线程）、RTX 5070 Ti、JBR `25.0.3+1-b329.124`、客户端 G1。客户端副本 1600×900、GUI scale 2、zh_cn、关闭 VSync、120 FPS 上限。各重负载基准串行运行。基线模组为 Academy 0.0.4-alpha、NeoForge 26.2.0.70、MC 26.2、GeckoLib 5.5.5、KotlinForForge 6.3.0、MisakaNetwork 26.2.0.1，未加载 Sable/Create/航空学。
- 编译：真实 definition（包含迁移）与直接 compiler 两条路径；2/32/128 节点的数据 DAG 和状态流程链共 12 场。每场 100 次预热、1000 次计时，另开 profiler 采集 128 次阶段计数。状态链只编译，不冒充随机/共享变量实际运行负载。
- 编辑器：真实屏幕中的 2/32/128 节点，分别有 1/64/256 条有效边；每方法 150 次预热、1000 次测量，profiler 开/关各 120 tick。微基准包括预先取得的反射 Method.invoke 开销；真实绘制准备另有固定区段计时。
- 查询：真实 native ServerLevel、ServerPlayer 对象、实体与物理结构；实体密度 0/64/256/1024，以及结构距离、附近、完整内容摘要、范围与失效场景。具体采样、通过状态以独立报告为准。不是 Sable 或多人全负载验收。
- P50/P95/P99 使用 nearest rank，预热不计入。线程分配量不含原生内存与其他线程。`nanoTime` 为墙钟耗时，不能标成 CPU 时间；线程 CPU 单独读取，在本机 Windows 上粒度较粗。
- `EditorSurface.renderInternal` 测绘制准备，不含前置输入同步、tooltip 和 GPU 完成时间。RenderFrame Pre/Post 包围 GameRenderer.render，前置 extract 与后置提交/present/FPS limiter 在外；两种区段不能相加或把事件分配量当整帧分配。
- 两轮均保留 `inactivityFpsLimit:afk`；本地 MC 的闲置阈值为 60 秒后限 30 FPS、600 秒后限 10 FPS。128 节点关闭 profiler 的窗口实际采到基线 123 帧、优化 180 帧，并非固定 120 FPS 的吞吐测试。表面区段不含外部限帧等待，但本批没有隔离频率/闲置状态影响；未来测真实帧率需明确锁定并记录该配置。

## 优化前证据

原始报告：[UI 与编译基线](../validation/precision_nodes/precision_c_ui_compile_baseline_2026_10_30.json)，[观测源码摘要](../validation/precision_nodes/precision_c_baseline_source_hashes_2026_10_30.json)。后续不会把旧报告改写成优化后的结果。

| 编辑器图 | 表面 P50 / ms | 表面 P95 / ms | 每帧被计数的全图校验 |
| --- | ---: | ---: | ---: |
| 2 节点 | 0.30 | 0.38 | 3 |
| 32 节点 | 3.66 | 3.99 | 33 |
| 128 节点 | 43.64 | 48.23 | 129 |

基线计数只覆盖 `firstDiagnostic()`；工具栏还有一次直接校验，因此每帧实际校验次数更高。128 节点每帧还重新构造 896 个 NodeView/schema；profiler 显示全图重复校验占表面耗时主要部分。独立 `nodes()` 约分配 516,592 字节，`firstDiagnostic()` 约 662,464 字节，`paletteCards()` 约 836,192 字节。优先消除重复工作，不先改变渲染裁剪或减少画质。

128 节点 DAG 的 definition 编译 P50 0.584 ms / P95 0.7404 ms，平均约 1,099,319 字节/请求；128 个 profiler 请求仍执行 128 次真实编译。直接 compiler 是独立参照，不会因 definition 缓存命中而跳过工作。

基线旧十节点重建拓扑在 30/140/1200 tick 截图中节点背景、文字、端口和边完整。这只证明本次重建图未复现原漏绘，不能代替原用户存档的验收。基线重载后的 32 节点截图仍有 Mojang 加载 overlay，因此该截图不作为重载显示验收；夹具后续增加 overlay 消失并稳定十 tick 的等待。

## 实施边界

编辑器缓存限定于屏幕当前不可变 document；配置、布局、导入、撤销、槽位变化统一失效，语言实例（包括同语言资源重载）、扩展快照和预设注册代次也参与失效。只保留语义对象和列表，不持有 Canvas、字形或纹理。存在第三方扩展或动态预设的目录保留实时构造路径。保存仍重新校验。

Java 调用者还可能把 `AtomicInteger` 等可变 Number 放进 Gson；`JsonPrimitive.deepCopy()` 不会复制该 Number。编译与编辑器均明确拒绝对这些配置使用缓存，不能只凭容器有 deepCopy 就声称整个图不可变。编辑器检查只在 document/环境改变时进行。

编译缓存限定七个真实内建 definition 的实例，每个最多 64 项、4 MiB 估算权重，并发访问同步。权重包含配置文本、编译图估算和可变长度能力 ID，不是实测堆大小。迁移和 envelope 检查仍逐请求执行；只缓存成功且配置经过明确 Class 白名单与不可变字段检查的结果。严格键包含节点顺序、ID、类型、schema version、配置精确内容、边与能力集合；UUID、名称与编辑布局不参与。配置对象键序可归一，但数字具体类型及原文不会合并。Gson 2.14 的 parsed `9007199254740992` 与 `9007199254740993` 相等且 hash 相同、取 int 却为 0/1，已加实际反例测试，不能用 Gson equals 或仅哈希替代严格键。

实例本身隔离 category/limits，未知 definition 与扩展节点绕过；全部 1476 个当前原生目录默认配置逐项验证，未来新增配置默认不缓存。服务端停止、客户端断线和退出清理；单个玩家离线不会清空其他玩家可复用的纯编译图。缓存没有 VM Session、玩家、世界、执行数据或授权结果。

实体查询仅在单次调用中预取每候选的 UUID 字符串，保持旧字典序（不是 UUID 自然序）、同键稳定顺序、原过滤和结果上限。未新增世界查询缓存；完整结构内容仍逐格扫描。

## 编译与大图复测结果

[优化原始报告](../validation/precision_nodes/precision_c_ui_compile_optimized_2026_10_30.json)的 12 个编译场景及四个编辑器场景全部通过，四图 codec SHA 与基线逐一相同。下表为关闭 profiler 的预热稳态窗口，不代表打开屏幕后从未重建，也不代表完整帧/GPU 时间。

| 编辑器图 | 表面 P50：前 → 后 / ms | 表面 P95：前 → 后 / ms |
| --- | ---: | ---: |
| 2 节点 | 0.30 → 0.1014 | 0.38 → 0.1517 |
| 32 节点 | 3.66 → 0.1919 | 3.99 → 0.2344 |
| 128 节点 | 43.6351 → 1.0267 | 48.2250 → 1.5413 |

128 节点稳态 NodeView/schema、候选列表、校验重建均为 0；原先每帧至少 129 次校验及 896 次 NodeView/schema 构造不再重复。三个反射微基准的 P50 分配均为 16 字节，包含反射调用开销，不写成整个编辑器零分配。

128 节点 DAG 的重复 definition 编译 P50 584.0 → 6.3 µs、P95 740.4 → 6.7 µs，平均分配约 1,099,319 → 10,696 字节。独立 profiler 128 请求全部命中，真实 compiler 调用从 128 降至 0；迁移仍逐请求执行。直接 compiler 对照仍完整编译，不将命中收益外推为首次/缓存未命中编译的降幅。

配置由二元改为一元后的真实 schema、布局与节点索引、缺必接边诊断、undo/redo、语言/同语言资源重载、动态 provider 注册及 supplier 更新、扩展 snapshot 替换与恢复均通过。provider 与 snapshot 注入仅限隔离夹具，最终确切恢复，不代表支持冻结后动态注册 NeoForge 节点。

真实窗口缩放、最小化/恢复后，旧十节点重建图停留 12,004 client tick；包括 30/140/1200/12000 tick 在内的全部九张最终截图均逐张审查，节点背景、文字、端口和边完整，资源重载截图无加载遮罩。**仍有遗留显示问题**：基线与优化版在 65% 缩放下都出现配置多行文字拥挤；此前原用户存档的漏绘、混装与首轮 `Missing uniform Globals` 根因不因本次未复现而关闭。

## 查询复测结果

[原始基线](../validation/precision_nodes/precision_c_query_baseline_2026_10_30.json)与[优化结果](../validation/precision_nodes/precision_c_query_optimized_2026_10_30.json)各 10 场全部通过，每场 32 次预热、128 次墙钟/分配采样及独立 16 次 profiler 查询。1024 密度每次仍检查 1032 候选、接受 1024、按旧序返回前 128；4096 摘要每次仍完整读取 4096 格。同 tick 实体移动/删除、结构内容变化、范围及过期引用检查保持通过。

| 实体数 | P50：前 → 后 / µs | P95：前 → 后 / µs | P50 分配：前 → 后 / 字节 |
| --- | ---: | ---: | ---: |
| 64 | 25.0 → 12.6 | 69.4 → 61.0 | 52,864 → 9,936 |
| 256 | 44.6 → 31.9 | 77.9 → 52.0 | 287,664 → 34,944 |
| 1024 | 235.1 → 173.1 | 282.0 → 218.0 | 1,462,112 → 131,184 |

1024 场景 P95 降约 22.7%，每查询分配降约 91.0%。这是本机本夹具单轮前后对比，不推算多人 TPS。未改算法的结构场也有波动，例如 4096 摘要 P95 303.0 → 241.8 µs；可能受 JIT/GC 或系统调度影响，本轮未做独立归因，不将其归功于实体排序优化。也没有隐藏尾部退步：64 实体 P99 123.4 → 326.5 µs；未改算法的结构距离 P99 52.2 → 61.8 µs、过期引用 8.5 → 10.7 µs、预算拒绝 8.3 → 23.3 µs。P99 样本仅 128 次，对少量异常值敏感；不能承诺所有尾延迟均改善，完整数据保留在原始报告。

为核对尾部波动，客户端退出后又做了一次独立新世界[优化复测 r2](../validation/precision_nodes/precision_c_query_optimized_repeat_2026_10_30.json)，仍全部通过、签名与全部扫描计数相同。1024 场景 P50/P95/P99 为 162.2/298.0/510.9 µs，P95/P99 高于基线；64 场为 15.8/84.1/122.6 µs。两轮三个非空实体密度的 P50 分配分别稳定为 9,936/34,944/131,184 字节。4096 摘要分配则回到基线的 166,648 字节。结论限定为重复格式化被消除、实体分配明显下降与当前两轮中位数改善，不承诺尾延迟普遍降低，也不挑选较好的一轮覆盖另一轮。

VM 的每动作数据缓存、权限检查、查询半径与结果上限、自动触发预算、物理控制频率不变。不因 DATA 标签把随机数、玩家位置、共享变量或世界查询跨动作复用。未测得瓶颈的局部增量更新、长边裁剪、调度 continuation、网络增量与 trace 压缩留待后续。

## 复跑入口

使用仓库指定 JBR 25。先 `test compileGametestJava -DisDev=true`，最后依仓库要求运行主测试、专项测试、开发/发布 build 和真实客户端。

```powershell
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name precision-c-ui-<唯一名> -Fresh
# 仅将副本 options.txt 设置为上述语言、缩放、VSync 与 FPS。
./gradlew.bat runClientDev -DisDev=true -I validation/precision_nodes/precision_performance.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-precision-c-ui-<唯一名> -PacademyPrecisionPerformanceStage=optimized -PacademyPrecisionPerformanceLongStayTicks=12000
./gradlew.bat runGameTestServer -DisDev=true -I validation/precision_nodes/precision_query_performance.init.gradle -PacademyGameTests=academy:precision_query_benchmark -PacademyPrecisionQueryStage=optimized -PacademyPrecisionQueryRun=<唯一名>
```

测试夹具不保存或执行编辑器图；查询夹具只改变自己创建的区域。查询必须新目录，拒绝复用旧测试世界。基线首次查询夹具在随机 GameTest 坐标遇到 native ±1,000,000 范围保护，未完成结果不作整体性能验收；后续使用明确固定且完整加载的独立高空区域。

## 验证与接续

主测试 3408、Sable 专项 49、编辑器 104 全通过（合计 3561，失败/错误/跳过均为 0），开发与发布 build 均成功；真实 UI 与两轮优化查询正常退出。缓存的数值碰撞、可变配置、能力隔离、预算淘汰、并发和生命周期测试均通过。冻结 342 ID/353 单字段模式契约继续回归，原始清单 SHA 保持。28 项冻结源码哈希与运行时证据一致，既有 Rust 文件 SHA `0B172501A5DA088A1CDA8AD13A41C88005D64B8D62D5347103DB566F84AC3C70` 未变并排除提交；完整产物 SHA 见机器报告。

本轮对应 C 阶段的编译、查询和大图热点闭环，不将总方案 C 的全部验收矩阵标成完成。保守视口裁剪也没有为了勾选计划而先行修改；应根据缓存后实测再判断是否值得增加跨屏长边、选中/拖动例外和失效复杂度。真实多人自动触发与网络量仍是下一批 C 验收入口，不由 32 个并发 Java 编译请求的单元测试替代。

下一批先做 1/8/32 玩家、各 1/4 个自动触发图的真实服务端基准，记录每 tick 编译/命中/扫描/预算拒绝、会话上限与网络字节；保持原费用、冷却、轮转与锁存。再根据热点决定冷编译去重、查询 continuation、显式快照或 UI 增量/裁剪，不扩大现有数据缓存语义。低缩放文字应先量测实际字形高度与配置行高；动态扩展/预设目前保守旁路的性能也需单独测量。

八体异形接触、64 体长期压力、混装全压力、网络延迟与 I/O 故障矩阵仍沿原专项接续。不要将本批保存/执行次数为 0 的屏幕夹具作为实际技能施放或网络验证。
