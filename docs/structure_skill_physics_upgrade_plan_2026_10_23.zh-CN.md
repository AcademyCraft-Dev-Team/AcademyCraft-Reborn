# 技能物理结构：性能、控制、归块与战斗完善方案

日期：2026-10-23（Asia/Singapore）。审查基线：`711feb72`，开始审查时工作树干净。

**状态：DESIGN ONLY。** 本轮核查代码、既有测试记录和外部参考，未修改运行逻辑、替换 DLL、运行新性能测试或启用自动还原。下文类型、节点扩展及参数均为拟议契约；不能作为已实现功能使用。

后续实施补充：已建立[可替换骨架与 FILL-SPEC](structure_upgrade_fill_spec.zh-CN.md)，新玩法保持禁用；顺序推进到 [S0 暖启动对照](structure_upgrade_s0_warmstart_2026_10_23.zh-CN.md)。全局参数候选因堆叠倒塌被否决，原生红测仍在；以下保留原设计，不把方案描述视为实现状态。

## 1. 决策与现状

采用一套后端中立的技能结构服务，让本地 SubLevel 与 Sable 共用控制、来源、伤害和生命周期规则。引擎优化单独验证；考虑本项目后续流体、软体规划，MPS 同时作为统一物理底座的重点候选，不因性能宣称直接替换当前 DLL。先完成防楔紧和真实破坏路径，再启用自动归块、增强投掷及有限碎裂，并预留流体/软体的独立对象与耦合契约。

| 用户需求 | 当前实际基础 | 本轮方案 |
| --- | --- | --- |
| 本地与 Sable 性能同步优化 | 本地逐结构地形暂停已通过隔离；共享预算和重复遍历仍存在；Sable 同场景测试未完成 | 相同工作负载测量；先减少编排/几何/跨语言成本，再独立选择引擎参数 |
| 避免技能楔紧 | 本地有一次源姿态归位、10 tick 稳定、角速度伺服；产品夹具通过，但原生扰动测试仍红 | 捕获前提取规划、子步控制、卡住检测、有界退让；补齐 Sable 姿态控制 |
| 技能结构静止归块 | 捕获请求和 SubLevel 清单无持久技能来源；现有恢复事务不足以直接自动化 | 原子来源标记、休眠候选队列、安全归块事务 |
| 回正、旋转节点 | 三系已有 `structure/rotate`；本地直接 setPose，Sable 返回 unavailable | 升级既有 rotate；新增 reset_orientation、spin，共用角控制 |
| 高速投掷、首撞增强、散落 | H 当前为 50/60 格每秒；只有部分实体扫掠伤害，没有统一物理接触流 | 75/90 起测；一次性首撞预算；有界碎裂及落地归块 |
| 冲击波破坏本地结构 | BreakTask 只查询宿主 BlockPos；本地结构内容位于独立 plot | 宿主和结构局部格统一查询、授权、破坏与掉落 |

当前状态以[最新接续](sublevel_continuation_2026_10_24.zh-CN.md)及 [P0 报告](sublevel_p0_test_report_2026_10_23.zh-CN.md)的最新补充为准。原生 `disturbed_step_socket_lifts_with_com_force` 仍失败；通过产品层防护不等于消除了底层接触问题。旧[扩容方案](sublevel_scaling_optimization_plan.zh-CN.md)中“未提交原型”的描述已成为历史，不能重复实现现有隔离。

## 2. 共用接口和行为边界

保留 `StructureRef`、`SpaceBlockRef`、`StructureDirectControl`、`ProgramStructureControl`、后端 router、捕获 journal 和现有掉落作用域。新增能力放在 `org.academy.api.common.structure`，适配实现放 `internal`；不让技能直接操作 native body、plot 坐标或 Sable 内部句柄。

| 拟议公共能力 | 方法契约 | 主要接入点 |
| --- | --- | --- |
| `StructureCapabilities` | 查询角控制、接触报告、安全归块、内容破坏、拆分能力及缺失原因；静态能力与当前可用性分开 | StructureBackendRouter / Bridge |
| `StructureExtraction` | `plan / begin / status / cancel`；计划绑定内容、姿态、覆盖版本；输出净空、受阻位置与阶段 | VectorStructureService、KineticStructureControl |
| `StructureOrientation` | `rotateTo / reset / spin / stop`；共享独占租约，输出操作 ID 和明确完成状态 | SubLevelDirectControl、SableStructureDirectControl、三系节点 |
| `StructureProvenance` | 创建事务内写入来源，查询只读；拆分继承，重启保留 | StructureCaptureRequest / Coordinator、SubLevelManifest、Sable ledger |
| `StructureGridRestoration` | `preview / request / status`；READY 只表示预检通过，RESTORED 才表示完成 | StructureStaticization、恢复协调器 |
| `StructureDamage` | `query / apply`；世界格与 SpaceBlockRef 共用授权、真实形状和格级去重 | KineticEnergyApplied、SubLevelInteractionController、NativeStructureBreakDrops |
| `StructureImpactFeed` | 按物理步批量发布接触事实；接触时不得回调 Java 改世界 | native/FFM、Sable adapter、VectorStructureService |
| `StructureFracture` | `plan / reserve / commit`；预留资源后拆分，返回每格最终所有权 | 捕获/恢复事务基础、物理/网络发布 |

所有操作在宿主服务器线程验证 actor、后端、宿主、UUID、generation、runtime 和 revision。公开单位统一为格/秒、秒、rad/s；UI 可以显示度/秒。输入必须有限且受预算限制。返回 `RUNNING / COMPLETED / BLOCKED / TERRAIN_PENDING / CONFLICT / UNSUPPORTED / STALE / DENIED` 等原因；入队、提交指令不能伪装为动作完成。

三系能力各自校验解锁、能耗和目标条件，共用结构操作；同时保留非玩家实体调用的 actor/权限上下文。不得通过伪装玩家绕过保护或制造工具战利品。

**已确认的适配缺口：** `StructureBackendRouter.submitDirectControl(..., holdOrientation/holdPose)` 只向 SubLevel 转发姿态参数，Sable 分支忽略。新能力必须显式传递角控制；不支持则拒绝，不能静默执行一半。

## 3. 性能：先定位成本，再选择引擎

### 3.1 对照组

固定版本、硬件、电源、JBR、视距、画面、结构内容和随机种子，分别测：本地当前基线、本地优化版、固定 Sable、Sable 优化 fork；MPS 候选独立加入。相同场景分别运行，避免同时抢 CPU。记录构建 SHA、DLL 哈希、Rapier revision、编译 feature、线程数、子步与迭代数。

原生层采用相同碰撞几何、质量、惯量、步长和质量门槛，区分绑定开销与求解成本；游戏层采用相同方块内容，允许各后端正常编译几何，记录其实际盒/体/接触数量。不能用暂停了更多结构、关闭碰撞或降低精度制造“性能领先”。

先扫 N=1/4/16/32/64，睡眠/移动、聚集/分散、密实/空心/复杂台阶；通过后才试 128/256。除 FPS 外必须采集：

- 服务端 tick、捕获、地形构建/安装、控制、求解、接触消费、内容变更、归块、同步的 p50/p95/p99/max。
- 活跃/睡眠/缺覆盖暂停结构数、刚体/形状/关节/接触簇、FFM 调用与字节、地形重建数、重试数、分配及 GC。
- 客户端帧时间、draw calls、网格重建和上传量、网络字节及姿态延迟。
- 稳态持续至少 60 秒，预热后做至少三轮交错对照；捕获/首次撞击/归块尖峰单独统计，不能混进稳定平均值。

性能目标先按实际硬件标定：同等可动数量维持 20 TPS，关键帧和碰撞正确性不退化。方案不承诺未测的倍数；任何“优于 Sable”结论必须附配对数据和质量误差。

### 3.2 本地优先项

1. 地形改为脏 section 订阅、共享不可变几何、版本化安装；sleeping 未变结构不因另一结构移动重复规划。保留当前逐结构完整覆盖准入，缺覆盖只暂停所属结构。
2. 真正应用 `SubLevelSimulationBudget` 的可延后任务预算；延后可选重建、规划、远处快照，不能漏掉高速结构的必要碰撞或少算物理时间。容量不足在捕获/拆分预留阶段拒绝。
3. 批量命令和状态读取、复用缓冲，减少每体 FFM 调用和 Java 对象。接触事件按被关注的结构启用，并在原生端聚合。
4. 评估一结构一刚体挂多个 collider，减少当前超过 256 盒后的多刚体与 fixed joints；真实空洞、材质、质量及惯量必须保留。
5. 休眠保留碰撞与唤醒；高速度/高角速度用扫掠和 CCD。不要全局关闭 CCD 或靠放大 dt 提速。Rapier 的休眠会由接触唤醒，CCD 也有额外成本；参数应按工作负载验证。[休眠](https://rapier.rs/docs/user_guides/rust/rigid_body_sleeping/) · [CCD](https://rapier.rs/docs/user_guides/rust/rigid_body_ccd/)
6. 最后评估有限线程池、SIMD 与空间分片。线程池需给 Minecraft 主线程和渲染留余量；小场景并行开销也要测。结构跨分片接触未解决前不启用分片。

### 3.3 Sable 与 MPS

Sable 同步采用来源、归块候选、技能控制、伤害及碎片预算。其内部求解、地形和每子步遍历优化在独立 fork 中做；不能将本地的参数或 ABI 直接套用。当前桥有固定版本及哈希检查，fork 发布必须同步更新 `docs/sable_compat_artifacts.json`、router 的匹配项及闭包验证。

MPS 采用路径及许可证见 [MPS 评估](mps_physics_evaluation_2026_10_23.zh-CN.md)。优先评估批量边界、接触事件、真实形状查询和体素构建，同时审查流体、软体的真实求解与耦合能力，再决定局部移植或统一内核 provider。其宽泛包装符合后续目标，不作为排除理由；但公式库、FFI 包装、实际世界求解器及 Minecraft 内容系统仍须分别验收。它不能自动解决 Java 层宿主查询遗漏、持久标记、恢复事务和节点语义。

## 4. 技能防楔紧：提取规划与受限控制

### 4.1 捕获前规划

`ExtractionPlan` 固定源姿态、源质心、捕获内容/顶部依赖、实际碰撞体、3/5/7 目标高度、硬障碍、允许退让范围及地形版本。用真实形状验证整个上升路径；大 AABB 仅用于候选筛选。保持原位上升语义，净空允许时才有有限横向退让，不因质心不同直接改换选区位置。

植物/无碰撞附属格纳入内容处理；沿既有权限和软障碍规则清理，不把草当硬墙。屋顶、倒扣岩层等真实硬阻挡应明确 `BLOCKED`，保留原结构或安全释放，不能为了完成抬升穿墙。

### 4.2 控制阶段

`PREPARING → EXTRACTING → CLEAR → HOVERING → LAUNCHED/RELEASED`。

新捕获在第一次物理步前初始化源姿态与速度，避免先被碰撞推偏、随后再传送回原位。已有运动结构禁止使用这条新捕获初始化捷径。沿路径使用限力平移和角控制，按真实子步 dt 重算；每外层 tick 的总冲量预算仍为 0.05 秒，不随子步数倍增。

狭窄提取段保持选区原方向，达到净空后允许技能定义的角自由度。H 的 200 tick 从首次稳定到位开始；提取超时与悬浮到期分开，阻塞不能被解释为自动发射。

### 4.3 卡住判定与处理

持续若干 tick 有显著位置误差和控制输出、沿路径进度却接近零时标记 `STALLED`。区分：地形未就绪、真实阻挡、控制被占用、接触楔紧。记录实际法向、摩擦/法向冲量、穿入深度和几何版本。

处理顺序：减速并制动角运动 → 在已验证的局部路径内尝试有限退让 → 仍不可行则返回受阻。退让建议从不超过半格的范围起测，必须保留最初源高度目标并限制累计漂移；不采用不断加力、每 tick setPose、统一缩盒或全局去摩擦。

原生诊断保留红测：分别对接触缓存、接触聚合、摩擦、暖启动、几何重建做 A/B，检查实际 solver cluster 与 `contact_surface` 法向修正是否一致。锁定 Rapier 0.36.1 已有 `contact_clustering/contact_recycling/solver_clusters`，但尚无证据确认其中某一项就是根因。只修实际内部面，外墙/地板/顶板不得被误过滤。[接触与求解参数](https://rapier.rs/docs/user_guides/rust/integration_parameters/)

## 5. 技能来源标记与低开销静止归块

### 5.1 标记规则

在捕获事务 COMMIT 前写入 `creationOperationId、rootLineageId、parentId、skillId、ownerId、createdGameTime、sourceGridRotation、restorePolicyId、restorePolicyVersion、受限策略参数`，与内容所有权一起持久化。本地扩展版本化 manifest；Sable 使用 Academy sidecar ledger，绑定后端、宿主、持久 UUID 和 generation，不保存 native handle。未知策略/版本默认保留结构，不按当前配置猜测旧结构是否允许掉落。`sourceGridRotation` 记录捕获时局部方块坐标系相对宿主的基准方向，碎片保持该语义。

只有技能新创建的结构自动标记；抓取、推动已有结构不改变来源。旧存档无字段按 UNMARKED 处理；普通 Sable 船不因被技能碰触而自动归块。碎片继承来源和策略，不能继承首撞奖励。混合来源合并初期禁止自动继承归块策略，保留完整来源记录。

标记不授予破坏权限。H 提取/200 tick 悬浮、有效精密租约、牵引、连接关节、打开菜单、挖掘/破碎事务持有有期限的 `RestoreHold`；结束后重新累计静止时间。纯只读引用不能无限持有。

### 5.2 候选与预算

使用 sleep/wake、内容/姿态变化和租约事件更新候选索引，时间轮或到期堆每 20 tick 检查到期项。候选判断不扫描全部方块。初始标定值：连续 100 tick，线速度 <0.05 格/秒、角速度 <0.05 rad/s，姿态漂移也在容差内，并有可信支撑。

必须排除 terrain-paused、inactive/recovery、控制悬停和没有可信状态的后端。**缺覆盖暂停的公开速度为零，绝不能因此认定已落地。** Sable 若没有睡眠事件，定额轮询有标记结构的运动和支撑状态。

静止先利用真实休眠降低求解消耗，到期后归块进一步释放模拟、同步和渲染资源。建议每 tick 最多启动 1 个规划、预检 2,048 格，分片完成且提交前复核；冲突按 100→600 tick 退避，目标格变更/自然区块加载可提前唤醒。数值均待基准测试，不能同步遍历全维度结构归块。

### 5.3 安全还原

当前位置附近回正，默认从保持世界 UP 的四个 yaw 90° 候选择优，不传回原捕获地点。整数旋转保证一格一个目标；BlockState 方向及 BE 必须有适配。初版仅支持当前捕获白名单内容，后来放入 BE/液体等需重新检查，不能凭创建时检查放行。

整结构预检已加载 chunk、空气目标、实体占位、其他结构真实形状、权限及完整方块依赖。冲突保留原结构并延期；不强加载缺失宿主 chunk、不覆盖建筑。普通静止归块失败不自动转成掉落。

归块状态独立维护：`ACTIVE → REST_CANDIDATE → PLANNING → RESTORING → RESTORED`；旁路为 `DEFERRED / RECOVERY_REQUIRED`。

现有 `SubLevelRestoreService` 的 floor(origin)+local 与弱 journal 不足以直接挂定时器。复用并落实[卸载还原设计](structure_chunk_unload_restore_plan.zh-CN.md)中的强事务：完整前后镜像和 revision → PREPARED 持久化 → 写目标及 BE/tick/实体迁移 → 目标实际保存完成 → COMMIT 转移所有权 → 撤销投影/碰撞/引用 → 清理并保存源 → 释放 plot reservation。

部分写入期间两份库存都不能被消费。回滚仅修改仍匹配事务后镜像的目标；提交后只清理源，不能重放玩家已挖掉的方块。拆分 plot reservation 与 residency，延期/恢复中不能释放编号复用。

Sable 现有 saveAll 是 best-effort checkpoint，缺少本方案需要的完整保存回执；安全归块适配未完成时能力返回 UNSUPPORTED，并保留睡眠结构。不能绕过拒绝未记日志写入的 handle。静止触发先落地；未来区块卸载触发复用同一协调器，本轮不顺带启用完整卸载自动化。

## 6. 精密操作的角度回正与旋转

| 节点 | 输入/行为 | 完成与受阻语义 |
| --- | --- | --- |
| 既有 `structure/rotate` | 保留 ID、`control + pose` 及旧图解码；升级为绝对目标姿态伺服，保持世界质心，带角速度/角加速度/超时限制 | 达到角误差及角速度阈值后完成；不再直接 setPose |
| 新 `structure/reset_orientation` | `control + mode`；默认原始方块基准方向，可选世界 UP+保留 yaw、最近网格 yaw | 物体仍物理化；不等同静止归块；无可行旋转路径则 rotation_blocked |
| 新 `structure/spin` | `control + axis + frame + angular_speed + duration`；世界轴/局部轴显式选择，界面用度/秒 | 持续执行；结束默认限力制动，可配置释放惯性 |

“强制回正”表示取得角控制优先权并制动旧角速度，不表示关闭碰撞或把玩家夹入方块。完整旋转扫掠要考虑长杆、空心和非对称结构；默认保持质心，若允许有限让位必须显式配置并验路径，禁止任意改变位置。

原始方向取持久 `sourceGridRotation`，不从当前倾斜姿态猜测。无标记 Sable 结构或旧档没有可靠基准时，原始方向模式返回 `baseline_unavailable`；调用者可显式改选世界网格回正。

同一有效租约可同时有一条平移和一条角命令；目标角度和持续自转互斥，替换有明确回执。H 的提取方向约束优先，发射、销毁、失去控制或拆分撤销旧角命令，防止发射后继续暗中锁角。

三系只负责能力条件和能耗，共用公共执行器；沿现有 schema、compiler、runtime、codec 和翻译注册路径扩展。旧图加载与执行结果需兼容；不新增一套同名重复 rotate。Sable 在自身真实子步消费角控制，不从宿主 tick 反复传送姿态。

## 7. 统一技能破坏：先修冲击波漏查

当前 `KineticEnergyApplied.BreakTask.tryMutate` 只读 `level.getBlockState`，所以无法命中本地 plot 内容。`KineticShockwave` 是表现实体，修改其渲染/实体 tick 不能解决此问题。

改为 `StructureDamage` 区域任务：先查宿主世界格和结构空间索引，世界攻击体积变换到结构局部空间，真实内容选择形状复核后生成 `SpaceBlockRef`。碰撞形状与可破坏内容选择形状分开，草/植物等空碰撞方块仍参与内容命中。空洞和大 AABB 里的空气不能受伤；跨多个子步/查询到同一格只结算一次。普通和精密冲击波共用入口，后续射线、爆炸、路径破坏技能逐个迁移。

每格稳定去重键为 `effectId + reference/generation + localPos`；runtime、contentRevision 和预期方块数据属于授权复核条件，不进入去重键，以免版本变化获得第二次伤害额度。真正修改前再检查当前内容和宿主投影位置权限。查询与 mutation 共用每 tick 扫描/变更预算；排队过程中结构移动或内容改变时复核，旧请求不能攻击被替换的格。

复用并抽取 `SubLevelInteractionController.destroyBlock`、`NativeStructureBreakDrops`、`AbilityBlockDrops` 的回调与作用域：保持全局 `BlockDropsEvent`、loot modifiers、事件取消/替换、经验及容器输出的宿主映射。技能破坏用实际来源和工具语义，不模拟一段玩家挖掘，也不裸 setBlock(AIR)+手工生成物品。

区分 `BREAK` 与 `TRANSFER`：真正破坏才执行战利品管线；归块和拆分搬运不掉落。同一被批准的破坏只进入一次全局掉落管线，不再额外手工补发事件。事件可能改变世界，回调后再次验证；不同目标分别报告成功/取消，不能声称批量副作用可任意回滚。

每次内容修改立即使旧 revision/观察失效，避免事件重入读取到已变内容与旧版本；受控批次结束时合并昂贵的几何/质量/惯量重建与网络发布。最后一格销毁按既有生命周期清理。铁块等普通可破坏方块不能因后端不同而免疫；不可破坏、权限、掉落开关等遵守该技能原有规则。

## 8. 增强投掷、首次碰撞和有限碎裂

### 8.1 速度与有效首撞

H 当前 50 格/秒、里程碑 60；建议从 75/90 格/秒做 A/B，保留 1.2 倍关系。400 是当前局部硬上限，不作为目标。投掷只设定一次发射动量，随后正常求解；先验证前方地形、薄墙/动态体 CCD、旋转扫掠和网络，不靠每 tick 续设速度抵消碰撞。

原生现在 `step_with_events(..., &())` 不输出碰撞报告。新增有界接触 feed：结构身份/几何版本、step/substep、对方身份、接触点/法线、碰撞前相对法向速度、求解冲量。接触簇/多部件/子步先聚合，Rust 回调只记录事实，外层服务器线程处理世界变化。溢出可见；若无法确认最早撞击，该 LaunchId 转 `IMPACT_UNKNOWN`，取消首撞增益及接触驱动碎裂，保留普通物理碰撞，不能把下一次撞击当首撞。已确认的完整首撞事实可正常消费。Rapier 的接触对不一定实际接触，且步末查询可能丢掉子步瞬态接触，应在事件发生时采集所需信息。[接触事件](https://rapier.rs/docs/user_guides/rust/advanced_collision_detection/)

每次发射分配 LaunchId：`ARMED → FIRST_IMPACT_CONSUMED → SETTLING`。只有新的、超过速度/冲量阈值的有效接触才消费奖励；起飞前已经支撑物体的地面接触不算。记录原有接触直到分离，不使用覆盖所有碰撞的长无敌窗口。一个物理步内按最早可信碰撞时序选取，不能取任意回调顺序；无法提供精确时序的适配器须明确精度并限制增强。

实体扫掠结果进入同一去重账本；本地、Sable 自带伤害与 Academy 技能伤害必须明确由谁结算，避免重复伤害、重复掉落。发射后的短期已授权飞行不依赖玩家仍在线，使用持久来源与服务端授权有效期；每次破坏仍复核当前保护策略，原控制租约可以释放。重启若恢复飞行，须同时保存 LaunchId 的消费状态和剩余额度；否则按无首撞奖励的普通运动恢复，不能重新 ARMED。

### 8.2 能量和碎裂

求解器已施加的接触冲量不得再完整施加一次。首撞增强是额外、一次性的技能预算，按相对法向速度/有效质量分配，封顶并扣除用于伤害、散射的额度。速度提高本身会提高动能，倍率必须单独标定；不要把每个接触点都赋予整座结构的 m·v。

碎裂先在接触邻域按材质和连接关系规划，再做连通分量；大块保留为结构，细屑可进入真实破坏/掉落流程。初始建议单次最多 8 个动态碎片，全维额外碎片预算 64，且服从实际 body/box/terrain/plot 余量；这些不是扩大当前 64 active 限额的授权。预算不足保留较大残块或延后，不能删内容，也不能每格生成刚体。

拆分顺序：验证源版本 → 预留所有目标存储/刚体/同步资源 → 持久化所有权分配 → 写入并确认目标 → 原子切换逻辑拥有者 → 发布碎片 → 清理源。父引用失效，子引用唯一；全程一格只有一个可消费拥有者。初版不拆未支持的 BE 库存/多块依赖，保留完整组或返回不支持。

碎片初速继承 `v + ω × (C_fragment − C_parent)`，重算质量/惯量；散射采用有限能量且控制总动量偏差。子碎片保留来源标记，不继承首撞增益。落地稳定后走第 5 节归块。战斗策略明确允许的细碎/无法安置残余才转成正常战利品，事件取消时不能强行补物品。

放回世界与转掉落互斥。不能简单“超时就掉落”：先确认目标区块可判定、冲突策略和权限；跨未加载区块保留持久待处理内容。掉落输出有事务 UUID/实体 UUID 账本；崩溃后回执不确定时隔离核对，不无条件重发，也不宣称未验证的 exactly-once。

## 9. 分期实施与完成判据

| 阶段 | 交付和涉及文件 | 开启下一阶段的条件 |
| --- | --- | --- |
| S0 基线与能力 | 最新 P0 补齐、Capabilities、接触诊断；SubLevelRuntime、native lib/contact_surface、Sable bridge | 红测根因明确并修复；Sable 同夹具及受控 N 扫描；不得静默 ignore 红测 |
| S1 防楔紧与角节点 | Extraction、子步角控制；三系 rotate 升级、reset_orientation/spin、旧图兼容 | 本地/Sable 复杂地形、偏心扰动、硬顶负例；真实 H 3/5/7 和完整 200 tick；角扫掠不穿墙 |
| S2 内容破坏 | StructureDamage、BreakTask、共享掉落作用域、批量几何更新 | 静止/旋转/运动铁块可受冲击波；空洞不误伤；保护/取消/全局掉落生效 |
| S3 来源与归块 | versioned provenance、hold、候选队列、强 journal；本地先启用，Sable 补保存契约后启用 | 重启继承；未标记不处理；paused 不误归块；全断点故障注入无重复所有权 |
| S4 高速及碎裂 | ImpactFeed、LaunchId、首撞预算、Fracture、碎片归块/输出账本 | 薄墙/动态碰撞/加载边界；首撞一次；有限碎片；无穿透、丢块、重复战利品 |
| S5 优化与扩容 | dirty subscriptions、批量 FFM、线程/形状优化、Sable fork、MPS provider 对照 | 同质量配对性能证据；小场景不明显退化；不靠更多暂停换性能；再定容量 |
| S6 流体/软体接入 | 统一物理内核的独立对象、耦合事件和存储协议；详见 MPS 评估 | 流体体积/软体拓扑保持、相互作用稳定、查询和预算完整；不套用刚体归块规则 |

S0 的诊断、S2 的查询修复、S3 的只读来源/规划可以独立开发；自动写世界与碎裂必须等事务基础完成。S5 的测量和小范围热点优化可贯穿各阶段，生产引擎切换与扩容需要独立验收，不把六项捆成一次巨大提交。

### 必测矩阵

- 防楔紧：平地、坡、凹角、台阶、低顶、植物；对称/偏心/细长/空心；纯线/纯角/双扰动；已有倾斜体，真实玩家站旁。
- 节点：三系同图、本地/Sable；绝对旋转/回正/持续转动；旧图加载、争抢、旧 token、换维、断线、发射撤销角控制。
- 静止归块：未标记、带标记、碎片继承、控制中、缺地形暂停、半空、玩家/箱子占用、负坐标、跨区块、目标冲突、重启。
- 战斗：动能附加普通与精密入口；铁块/植物/非满格/空洞；事件修改/取消、掉落开关、权限拒绝、最后一格、移动中命中。
- 高速/碎裂：50/60 与 75/90 对照、薄墙、结构对撞、多接触/多子步、事件队列溢出、容量不足、子碎片不重复增强、保存中断、原位无幽灵碰撞。
- 性能：记录实际可动数量、质量门槛、sleep/paused 分开、各阶段 p95/p99；归块和首撞尖峰单列。

### 构建和运行门槛

本轮仅文档，不需要运行 Gradle。实现阶段使用 JBR 25 和仓库 wrapper：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat test -DisDev=true @physicsNativeArgs
./gradlew.bat testNativePhysicsRust -DisDev=true @physicsNativeArgs
./gradlew.bat nativePhysicsTest -DisDev=true @physicsNativeArgs
./gradlew.bat sableCompatTest -DisDev=true
./gradlew.bat build -DisDev=true @physicsNativeArgs
./gradlew.bat build -DisDev=false @physicsNativeArgs
```

`build/native-physics-p16-env.ps1` 是本机辅助文件；其他环境先配置 JBR25、Rust 和固定 native 路径，不将该文件假定为仓库发布内容。复用同修订已成功检查；nativePhysicsTest 若已依赖 Rust 测试，不重复跑已通过的相同任务。新增 FFM/ABI 必须同时更新 Java/Rust、manifest、打包 DLL 来源摘要和故障测试。

客户端复用 `validation/native_structure/sublevel_lift_tiers.init.gradle`、`sublevel_terrain_isolation.init.gradle` 和 `validation/structure_parity/run-pair.ps1`，新增节点/归块/碎裂夹具；分别使用 `runClientDev` 与 `runClientSable -PacademySableRuntime=sable` 的隔离副本。真实按键、相机、多人延迟及目视抖动不能由服务端夹具替代；禁止写原 test/test2。

## 10. UNVERIFIED 与可回退边界

本轮没有新增实测数据。新接口、节点、标记、自动归块、统一技能破坏、首撞事件、碎裂和 MPS 接入均未实现；75/90、静止阈值、规划与碎片预算均待标定。已有产品夹具通过不能关闭原生楔紧红测、Sable 对照、受控扩容及人工验收。

功能分别开关；关闭自动归块后停止新任务，但必须恢复/完成已有事务；关闭碎裂不能遗弃已创建碎片。引擎选择在启动加载阶段确定，运行中不热替换 DLL。存档保存内容和物理语义，不持久化引擎句柄；不支持的新版事务必须拒绝旧版继续写入，不能把回滚理解成强行降级读档。

完成一个独立功能及必要验证后再询问是否本地提交；本设计文档不构成实现完成或新的提交授权。
