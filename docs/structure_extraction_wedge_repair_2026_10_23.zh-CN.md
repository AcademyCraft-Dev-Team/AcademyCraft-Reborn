# 物理结构卡位修复与三系提取动作接续

> 本文记录从 2026-10-23 延续至 2026-10-24 的实际提取服务、卡位修复与三系接线。新提取路线已默认启用，仍标为 EXPERIMENTAL。最终结果以本节新增验证及[机器汇总](../validation/native_structure/structure_extraction_wedge_validation_2026_10_23.json)为准；单项通过不代表整个物理系统已验收。

## 2026-10-24 补充：Sable 数值接触与控制归属

Sable 植被 r6 在捕获准入修复后，抬升约 0.84 格返回 `extraction_blocked`。初始真实原点为整数，但第一子步将世界质心转换成 float 再回推原点，X/Z 分别出现固定的 `-5.0738453865e-6`、`+1.2814998627e-6` 格误差，数值与 `float(sourceCOM)-sourceCOM` 一致；姿态与角速仍为零。低位盒 `[8,1,4]→[9,2,5]` 上升经过宿主 `(-152,125,58)` 的侧边时，该 Z 偏差被当成新的墙面穿入。真实运动沿该轴朝外，并非树叶没有清除。

`OrientationBackendAccess.positionRounding` 默认返回零；仅 Sable 按各轴世界 COM 的 float 半 ULP 提供观察误差，宿主静态接触按法向投影使用这个边界。整个曲线不得加深既有数值偏差，起步有明确间隙也不得借容差穿入墙内。原生后端仍使用原 `1e-7` 基线，其他动态体不获此额外余量。单轴半 ULP 超过 `1e-4` 时不启用该轴豁免；因此远坐标（例如绝对 COM ≥ 2048 的对应轴）仍可能拒绝这类贴墙提取，不能宣称已解决远坐标精度。未改写位姿、碰撞盒、摩擦或 DLL。

同时修复两个控制归属缺口：磁力尝试操作已有结构时，不再轮询或关闭他人的提取；helper 取消必须使用精确所有者句柄，迟到句柄不能影响后继控制。对已有结构直接 H 发射先取得临时租约，外人遇到 `OCCUPIED` 不得替换速度或终止原提取，同一玩家提前 H 仍为 `PREPARING`。

Sable r7/r8 均已完成第一档 3 格/200 tick，但旧夹具沿用选地时向下的视角，实际发射速度 `(0,-11.4596,-48.6691)`，一 tick 后撞到台阶草方块并反弹，20 tick 位移不足 3 格。这是有效硬碰撞，不能删除地面或强推穿透以通过夹具。保留两次失败；最终植被配对统一将发射俯仰改为向上 15°，地形、实体碰撞、旧结构保留与全部抬升要求不变。新增飞行日志同时记录真实速度、包围盒和宿主接触候选。

Sable 植被 r9 已读取独立 JSON：实升 **3.032445272 / 5.032404043 / 7.032411628** 格，各连续 **200 tick**；捕获植物 **32/39/38**，清叶 **49/55/54**，`BreakBlockEvent` **47/52/51**、`BlockDropsEvent` **49/55/54**；20 tick 发射位移分别 **46.774698/46.774786/46.774738** 格。掉落计数可能包含自然邻居更新/衰变，不能将其大于 Break 计数直接解释为重复破坏。文件为 `run/native-sublevel-direct-smoke-extraction-vegetation-sable-r9/sublevel-lift-tiers-result.json`，日志 `build/extraction-vegetation-sable-r9.log`。

本地最终植被 r6 同样通过：实升 **3.022992187 / 5.022992187 / 7.022992187** 格，各 **200 tick**；植物 **32/24/24**、清叶 **51/44/43**、Break **47/39/39**、Drop **51/44/43**，三次飞行距离均约 **48.955530 格**。源为 `run/native-sublevel-direct-smoke-extraction-vegetation-native-r6/sublevel-lift-tiers-result.json`。两后端使用相同地形参数和发射角，但实际选区植物数不同；这些结果不是等体积性能比较。

默认技能最终 r4 的 15 阶段于 **122 server tick** 完成：空气提取 **0.971012576 格**、磁力 **0.976264649 格**，提取令牌释放后原控制接续；H **2.987185613 格**，提前 H 保持提取。新增不同 UUID 的本地 FakePlayer 实际调用节点 poll、取消和直接 H，全部拒绝/忽略，原会话、租约、位姿与线角速度保持。成功日志为 `build/extraction-default-focused-r4.log` 中 `CLIENT_SUBLEVEL_FOREIGN_EXTRACTION_REJECTED`；这不替代真实多 TCP 客户端网络验收。r3 因测试 FakePlayer 未走 Academy 登录初始化而 NPE，已在夹具中补正常登录/退出钩子并保留失败记录。

## 提交与范围

已按用户授权创建本地提交 `6383946a 修复物理结构捕获预演与来源绑定`，前置提交为 `8dec409b`、`edea3a45`。本轮继续处理提取动作和卡位；原 `test`、`test2` 存档不用于试验写入，客户端验证使用 `run/native-sublevel-direct-smoke-*` 隔离副本。

原有 `native/academy_physics/src/lib.rs` 未提交诊断改动基线是相对 HEAD **374 行新增、56 行删除**。原生分工已保存 `build/wedge-lib-preexisting.rs` 和本轮差异 `build/wedge-lib-this-turn.patch`，用于区分既有修改与本轮新增；不能直接把整个文件的工作区差异当成本轮成果提交。移除未采用 slip 后，`lib.rs` 当前为相对 HEAD **376 行新增、56 行删除**，即旧诊断基线加两行跟踪模块声明。已核对生产源码前缀、无残留 slip 生产接线及开发/发布 JAR 内原生资源；本轮未修改分发 DLL 或清单，具体校验值见最终验证节。

## 共用提取契约

公开入口为 `org.academy.api.common.structure.upgrade.StructureCaptureExtractions`，由矢量、电磁、空气的技能与精密操作节点共用：

1. 在修改源方块前调用只读 `planCapture`，保留原 `StructureCaptureRequest` 实例；实际捕获使用同一实例，使事务完成证据可以验证真实来源。
2. 等待事务发布，取得自己的控制令牌，再以预演 ID 和实际事务 ID 绑定。瞬时实体/地形未就绪返回有界等待，期限沿用原预演有效期，不重新捕获、不刷新超时。
3. 在同一 tick 内绑定并 `begin`。服务在后端真实物理子步前检查主体、宿主、激活、内容版本、独占租约、当前地形和实际扫掠路径。
4. 按力/加速度/速度上限推进，维持源旋转；不使用 `setPose`、关闭碰撞、删除旧结构来脱困。
5. 到位与稳定后才发布 `HOVERING`。当前阈值为位置差 ≤ 0.08 格、线速 ≤ 0.15 格/秒、角误差 ≤ 0.01 弧度、角速 ≤ 0.05 弧度/秒。悬浮时间独立于提取等待。
6. 取消、失权、超时、硬障碍导致明确终态，停止自己的控制并保留已提交结构。捕获 API 当前不提供提交过程的取消/回滚接口，不能把停止跟踪描述成恢复原方块。

`RELEASED` 并不总是“已到位”：外部主动发射会产生 `extraction_launched`。helper 只将 `HOVERING` 或 `COMPLETED/RELEASED/extraction_hover_completed` 当作成功到位，避免提前发射冒充提取成功。

| 入口 | 目标与交接 |
| --- | --- |
| 动能投掷 H | 源选区基准上方 3/5/7 格；到位后完整 200 tick 悬浮。提取途中再次 H 返回 PREPARING，到位后明确按 H 发射 |
| 矢量精密提取节点 | 按 tier 提取 3/5/7 格；真实到位后输出结构，释放提取令牌，交给后续控制节点 |
| 磁力默认捕获与电磁精密提取 | 先从源位置抬升 1 格；默认技能随后接原拉动，节点到位后交接 |
| 空气默认捕获与精密提取 | 先从源位置抬升 1 格；默认技能随后接喷嘴推进，节点到位后交接 |

磁/气的 1 格是当前明确的预提取动作，不代表所有深槽都已完全脱离；复杂侧向目标和大型结构仍须场景验证。已有结构的旋转/移动控制不获得“新捕获初始化”权限。

三系新捕获 helper 的默认 `maxRetreat` 已由 2 格改为 **0**：新结构保持源 X/Z 锚点竖直提出，硬顶不能通过时在源方块修改前拒绝。原因是低位方块横向绕行可能进入不平整坑壁，而且硬顶上的无效侧向候选会耗尽预演预算。下层公开 planner 仍支持调用方明确授权的退让；本轮只收紧新捕获技能与节点的业务策略，不删除通用接口能力。

`StructureCaptureExtractions.enabled()` 现将 `academy.structureExtractionGameplay` 缺省值设为 `true`，`StructureUpgradeBootstrap.LIVE` 同步登记实际提取能力；显式 `false` 仅保留为旧控制器诊断对照。H 与三系 VM 的两个专用 init 脚本仅在提供 Gradle 属性时覆盖开关，缺省运行会验证生产默认值。`DRAFT_GAMEPLAY_ENABLED` 仍为 `false`，模板另以 `live_services` 标注已接线服务，不会因此开启自动归块、碎裂等草稿能力集合。

构建脚本的历史骨架提示仍写有 `gameplay disabled`，不能据此判断已接线提取服务的状态；当前运行结果明确记录 `extraction_gameplay_enabled=true`，关闭的仍是其他 DRAFT 能力集合。

通用提取超时 400 tick，节点交接预留 40 tick，H 悬浮 200 tick；三系 VM 的提取等待上限为 800 tick。

## 软障碍与破坏事件

预演本身保持只读。业务动作首次预演返回 BLOCKED 时，可显式调用 `StructureCaptureClearance.clearSoftObstacles`，成功清除后只重新预演一次。

`CaptureSoftClearance` 根据源真实碰撞盒的竖直扫掠，与候选软方块真实轮廓盒相交来筛选；排除源选区，使用已加载视图和读取复核，不把结构外层 AABB 内的空洞都当作应清理区域。候选扫描体积限 4,096 格、形状测试限 65,536、单次最多销毁 48 格。超预算不会因此宣称路径已清空。

实际销毁共用 `SubLevelTerrainBreakPolicy.applyCandidates`。主体/区域/世界边界、建造权限和状态在 `BreakBlockEvent` 前后复核；事件取消保留方块。`destroyBlock(..., actor)` 沿正常 `dropResources → BlockDropsEvent` 处理掉落与经验，创造模式沿原版无掉落分支。硬度、保护标签、BE 和流体限制继续生效。Sable 的前置选区也允许新路线授权清理的软障碍进入正式预演，避免在 helper 之前就因草被拒绝。

捕获后新增的软障碍也须经过相同权限及事件链。`LiveExtractionMotion` 在实际子步扫掠被阻挡时尝试有界清理；只有真实销毁数量大于零才返回 `PREPARING/extraction_soft_obstacles_changed` 并暂停本步，下一帧重新拍摄地形快照后再推进。不得销毁后仍用旧快照发出本步移动命令，也不得将清理尝试本身当作路径已通。

后续增加 `ServerTickEvent.Pre → LiveExtractionMotion.beforeTerrain`，在两后端创建本 tick 地形快照之前，每体每 tick 最多准备一次软障碍清理。该时序修正本身不够：`build/extraction-vegetation-native-r3.log` 与 `build/extraction-vegetation-trace-r4.log` 均在 tier 0、age 225、最佳悬浮 0 tick 失败，r4 明确记录 `extraction_blocked`。这些失败保留。

r4 的真实跟踪进一步定位到严格相切的残留树叶：首次清理 30 格后下一 tick 无候选，直立向上扫掠曾返回 CLEAR，随后物理解算产生微倾；首次阻挡宿主格 `(-160,125,58)` 是与结构局部盒在 z=58 相切的树叶，体积严格相交筛选漏掉了它。现仅对**软清理候选形状**增加 0.01 格接触邻域，真实硬碰撞体、位置与法向碰撞均不修改；并修正运行期权限 guard 只接受本地后端而跳过 Sable 的限制。

native 植被 r5 已实际通过：台阶/嵌地、保留前档结构，三档 **3.0229921875 / 5.0229921875 / 7.0229921875 格，各 200 tick**；真实捕获植物 32/24/24，清除树叶 52/42/42，`BreakBlockEvent` 47/39/39，`BlockDropsEvent` 52/42/42。事件计数是实际观测值，不把所有树叶消失都推断成同一破坏路径。

Sable 植被 r5 在捕获前失败，真实射线 seed 为短草，选区最初包含它，旧 `cropNonFullBlocks` 却将它删去，造成 `selection_blocked_or_seed_missing`。后续检查还发现 Sable 捕获目标继承的完整方块白名单会继续拒绝草、小麦和耕地。最小修复保留新提取路线的完整选区，Sable 目标仅额外接受短草、蕨、枯灌木、小麦、胡萝卜、马铃薯、甜菜、耕地与土径；不放宽 BE、流体、水浸、预定 tick、不可破坏或任意其他部分形状。该目标准入由预演与真实事务共用，覆盖三能力及节点。

为防装饰格零质量造成 Sable 分配后立即失效，内部 `CaptureTarget.canSeed` 允许后端从**同一已验证选区**挑选初始格，Sable 要求非空碰撞且质量有限并大于零。没有可用格时在 claim、journal 和 allocate 前拒绝，其他植物仍全部复制。原 `stepCopy` 和独立 `sliceCursor` 都从索引 0 开始，只跳过实际预先写入的 seed；`copyProgress=seedIndex+1` 是回滚高水位，保证复制前方植物失败时仍清理后方已写入的支撑格。准入后暴露的数值接触与后续 r9 通过证据见本文顶部。

## 两层卡位证据

### 原生重放与候选策略

`build/wedge-native-trace-1.txt` 记录真实成功 FFI 调用的 CREATE 几何/相对姿态、STATE、IMPULSE、VELOCITY、STEP 序列。Rust 精确重放 **503 步，最大 STATE 误差 0**，日志 `build/wedge-replay-baseline.log`。这一案例能在原生层独立重现，因此不能归因于 Java 可视同步；但它不自动证明所有幽灵碰撞都具有同一根因。

接触证据显示新体与真实槽侧壁近水平法线、微小姿态漂移和暖启动法向预载会产生切向摩擦。每个 cuboid 一个 collider 导致 128 格形成 191 个接触 pair，Rapier 的按 pair 聚合不能跨这些 pair 生效；目前仍不能宣称这是唯一根因。

| 原生诊断 | 结果与限制 |
| --- | --- |
| 额外 8 次局部 solver iteration | 合并盒场景 6/6 通过；未合并首例只上升约 0.072 格失败，余例因失败中止；`wedge-s0-iterations8.log` |
| 单 compound collider 候选 | 12 个场景多项失败，已撤回；`wedge-s0-compound.log` |
| 仅侧壁摩擦 0.05 的诊断 | 11/12 通过，剩余案例 2.6445 格不足 3 格；`wedge-s0-side-friction005.log` |
| 侧壁摩擦 0 的全局诊断 | 28/28 通过，外部重放器另有 1 项明确 ignored；该环境诊断分支已删除，不能作为最终玩法方案；`wedge-s0-side-friction0-all.log` |

曾评估的候选是**显式提取期间、指定结构**的临时切向滑移授权：仅 fixed 地形、`abs(normal.y) < 0.1` 且接触法线与授权提取方向近切向时，侧壁切向摩擦置零；法向碰撞、地板、顶盖、正面墙、其他动态体的摩擦继续保留。授权最多 0.1 秒，原生每步衰减，暂停/删除清理；Java 到位、取消、失权时必须清除。

这属于提取策略候选，**不是引擎通用根治**。它当时默认关闭，试验用可选 ABI 2 导出 `academy_physics_body_set_extraction_slip`，旧 DLL 缺少导出时返回不支持；从未更新 Windows base64 分发资源。主任务已确认 H r3 在不启用此策略时通过，因此已撤去本轮未使用的 slip 生产 API、FFM/ABI、接触策略、额外测试及实验开关，仅保留原生调用跟踪与重放器。以下授权矩阵是撤回候选的历史证据，不是最终运行依赖；31 项矩阵与 4 项生命周期结果均不得计入最终测试总数。

Rust 授权矩阵 `build/wedge-authorized-slip-matrix.log` 为 **31 通过、0 失败、1 ignored**，唯一 ignored 是显式依赖外部跟踪文件的重放器；随后增加的生命周期测试单独运行 **4/4 通过**（`build/wedge-slip-lifecycle.log`）。默认无授权的 S0 原有 5 项失败基线仍未关闭，不能用开启策略的结果宣称无条件修复或完全优化。

撤回候选后的最终 Rust 默认运行结果为 **23 通过、5 失败、1 ignored**，144.01 秒、退出码 101，日志 `build/extraction-final-rust-baseline.log`；失败为 raw linear/angular/both、settle10 clean 和 production startup 五项 step-socket 压力。`build/extraction-final-rust-replay.log` 的显式外部轨迹重放为 **1 通过、0 失败，503 步，最大状态误差 0**。它重放的是历史失败轨迹，证明记录一致性，不是新控制器的通过证据。

### 新提取控制器的独立问题

H r1 在台阶/嵌地场景约升 0.077225 格后返回 `extraction_blocked`，未完成悬浮，保留 `build/extraction-h-native-r1.log`。

r2 首次命令误将 `--tests` 绑定给 `runClientDev`，属于 Gradle 参数失败，日志 `build/extraction-h-native-r2.log`，没有产生玩法证据。重试 `build/extraction-h-native-r2-retry.log` 重现拒绝，关键记录是 identity 姿态、角速 0、请求位移 `(0.004258, 0.056595, 0)` 和 `new_contact_not_proven_clear`：旧控制器对较大上升速度与微小横向制动请求统一限长，留下残余侧移进入坑壁；此处碰撞拒绝是正确行为，不能直接放松硬墙检查。

`ExtractionController` 改为优先消除横向速度，再将剩余加速度/力预算分配给纵向；总预算保持不变。`ExtractionContactSweep` 只在严格扫掠 BLOCKED 后进一步证明真实运动：既有宿主穿插全程不得加深、终点必须减浅，非宿主动态体不获得豁免，硬顶新接触仍须始终分离。

H r3 已由主任务核实 `SUBLEVEL_LIFT357_SMOKE_PASSED`，未启用 slip：保留前档结构、同一固定锚点的台阶/嵌地/外扰场景上升 **3.022992 / 5.022992 / 7.022992 格，各 200 tick**；日志 `build/extraction-h-native-r3.log:4234`。这是新提取路线的一项实机结果；不能据此声称无授权 S0 已通过或所有地形都已解决。

### Sable 接触数值边界

Sable 用 float 质心回推 double 原点时，实测 identity 姿态也可能留下 **7.450580596923828×10⁻⁹ 格**的接触差。严格分离检查把这类数值舍入当成真实侧墙穿插，继而拒绝没有继续向墙内运动的竖直提取。`ExtractionContactSweep.CONTACT_ROUNDING` 现在为 **1×10⁻⁷ 格**，仅用于接触投影比较，不改变碰撞体、摩擦或位置。对应测试允许该舍入偏差的竖直运动，同时要求 **1×10⁻⁵ 格**的真实已有穿插以及向墙内新增位移继续返回 BLOCKED。

Sable H r4 在编译问题修正后已进入实机并开始悬浮，但失焦暂停，随后由主任务主动终止；**不计为通过**。r5 在隔离副本的 `options.txt` 中设置 `pauseOnLostFocus:false`，完整保留台阶、嵌地、外扰及前档结构，未采用 slip，实测 **3.032447815 / 5.032447815 / 7.032409668 格，各 200 tick**。`build/extraction-h-sable-r5.log:5273` 包含三档 `SUBLEVEL_LIFT357_SMOKE_PASSED`，各档 `angular_reversals=0`，最大横向偏移低于 2×10⁻⁸ 格。这仍是无植被场景的功能验收，不替代草叶、保护和掉落检查。

### 硬顶拒绝与证据通道

首次硬顶运行的日志观察器通过 `Logger.addAppender` 创建了新的非叠加 `academy` logger 配置，导致后续原日志输出消失；该次无通过或失败证据，不能算验收通过。夹具现直接向已有有效 `LoggerConfig` 添加精确过滤的观察器，不改变原配置和输出，并在隔离目录独立写入 `sublevel-lift-tiers-result.json`；启动先写 `running/passed=false`，成功或失败证据写完才通知客户端退出。

native roof r2 的独立结果为 **failed**，真实原因是 `REJECTED:extraction_preview:CONFLICT:planning_budget_exhausted`。默认 2 格横向搜索最多生成 9 个候选，在硬顶场景耗尽预算；“没有创建结构”不能代替实际 BLOCKED 证据。改为新捕获默认不横向退让后，native/Sable roof r3 均返回 `REJECTED:extraction_preview:BLOCKED:extraction_path_blocked`，连续观察 5 tick，**8,379 个源区域格与 9 个基岩格不变，新增结构 0**，严格负用例才成立。

## 已读取的客户端证据

以下每份 JSON 已实际读取 `passed=true`，不能以 Gradle 退出码替代这些结果。路径均在 `run/native-sublevel-direct-smoke-` 下，未纳入 Git 的运行日志需由主任务汇总机器证据。

| 隔离副本后缀 | 结果文件 | 已证明的范围 |
| --- | --- | --- |
| `extraction-motion-native-r1-20261023` | `structure-capture-preview-result.json`，36 项通过 | 线性初版：实际提取 3.004598/5.004598/7.004598 格；三档各观察及回报 200 tick 悬浮，总耗时 224/229/234 tick |
| `extraction-motion-sable-r1-20261023` | 同上，36 项通过 | 加入角保持后：实际提取 3.025513/5.025513/7.025513 格；三档各 200 tick，总耗时 224/229/234 tick |
| `extraction-vm-native-r1-20261023` | `structure-program-orientation-result.json`，三系通过 | 真实 VM 的 extract→control→rotate→reset→spin 250 tick→release；当时旧夹具未记录实际提取高度，不能用它独立证明高度合格 |
| `extraction-vm-sable-r1-20261023` | 同上，三系通过 | 新高度夹具：矢量 2.976471 格、等待 22 tick；电磁 0.984344 格、17 tick；空气 0.978241 格、15 tick；分别符合 3/1/1 格 ±0.1，且输出晚于捕获发布 |
| `extraction-vm-native-final` | `structure-program-orientation-result.json`，三系通过 | 生产默认接线为 true；矢量 2.978786540 格/23 tick、电磁 0.976264649 格/17 tick、空气 0.979810365 格/16 tick；实际 VM 完成提取、控制、旋转、回正、250 tick spin 与释放，三系均有 44 个超过 200 tick 的旋转采样 |
| `extraction-vm-sable-final` | 同上，三系通过 | 生产默认接线为 true；矢量 2.976470947 格/22 tick、电磁 0.984344482 格/17 tick、空气 0.978240967 格/15 tick；同一节点链及 250 tick spin，三系均有 44 个超过 200 tick 的旋转采样 |
| `extraction-roof-native-r3`、`extraction-roof-sable-r3` | `sublevel-lift-tiers-result.json`，均通过 | 真实 H 释放收到 BLOCKED；8,379 个源区域格和 9 个基岩格不变，0 新结构，5 tick 连续观察；不是 CONFLICT 或未命中的替代证据 |
| `extraction-default-focused-r2` | `sublevel-default-skills-result.json`，通过 | 独立 `SubLevelDirectSkillSmoke`，15 个阶段、122 server tick，默认提取接线开启；`direct_complete=true`，明确 `content_matrix_executed=false` |
| `extraction-vegetation-native-r5` | `sublevel-lift-tiers-result.json`，三档通过 | 台阶/嵌地、保留旧体；3.022992/5.022992/7.022992 格，各 200 tick；植物 32/24/24、清叶 52/42/42、Break 47/39/39、Drop 52/42/42；该场景未加外扰 |

对应构建输出为 `build/extraction-motion-native-r1.log`、`build/extraction-motion-sable-r1.log`、`build/extraction-vm-native-r1.log`、`build/extraction-vm-sable-r1.log`。这些运行分属逐步开发中的修订；线性初版结果不能替代最终角保持、扫掠和软障碍修复的复测。上述 native H r3 与 Sable H r5 为后续实际技能证据，采用运行日志中的明确通过标记；未把它们虚构为既有 JSON 项。

上表中期节点、硬顶与默认技能对应 `build/extraction-vm-{native,sable}-final.log`、`build/extraction-roof-{native,sable}-r3.log`、`build/extraction-default-focused-r2.log`。默认技能的具体高度记录在该隔离副本 `logs/latest.log`：喷嘴 **0.971012576 格**、磁力 **0.976264649 格**，均确认提取令牌已释放且后续控制生效；H **2.987185613 格**，提取中提前按 H 仍保留结构，随后正常发射。加入所有者隔离与最终数值修复后的节点复测为 `extraction-vm-{native,sable}-owner-final`，默认技能为 `extraction-default-focused-r4`，见最终验证节及本文顶部。

旧完整默认技能矩阵 `build/extraction-default-skills-final.log` 在尚未进入新 helper 场景之前，由 ContentSmoke 的 `fixture did not reuse the freed plot` 断言终止。当前没有旧基线对照，不能断言这是既有缺陷，也不能把独立 direct 的 15 阶段通过称作 control/content/direct 完整矩阵通过。其余完整矩阵及该地块复用失败须单独调查。

## 源码与定位入口

| 位置 | 职责 |
| --- | --- |
| `src/main/java/org/academy/api/common/structure/upgrade/StructureCaptureExtractions.java` | 三系共用捕获/提取会话、到位判定、取消与有界等待 |
| 同目录 `StructureExtraction.java`、`StructureExtractions.java` | 公开可替换契约与实际服务入口 |
| `src/main/java/org/academy/internal/common/structure/upgrade/control/LiveStructureExtraction.java`、`LiveExtractionMotion.java` | 结构侧 plan/begin/status/cancel、实际子步调度与身份复核 |
| 同目录 `ExtractionController.java`、`ExtractionContactSweep.java` | 有界平移控制、横向优先分配、接触解析验证 |
| 同目录 `CaptureSoftClearance.java` | loaded-only、真实形状软障碍清理 |
| `src/main/java/org/academy/internal/common/structure/sable/SableCaptureAdmission.java`、`SableStructureTransactions.java` | 有限植物/农地准入，保持其他安全门，使用真实碰撞与质量选择可持续 seed |
| `src/main/java/org/academy/internal/common/structure/transaction/CaptureTarget.java`、`StructureCaptureCoordinator.java` | 目标特定只读 canSeed，从原选区选初始格并保留完整复制/回滚集合 |
| `src/main/java/org/academy/internal/common/sublevel/SubLevelTerrainBreakPolicy.java` | 共同权限、破坏事件、掉落链 |
| `src/main/java/org/academy/internal/common/ability/accelerator/movement/VectorStructureService.java` | H 与矢量捕获接线、3/5/7 及 200 tick |
| `src/main/java/org/academy/internal/common/ability/electromaster/magnet/MagneticStructureService.java` | 磁力捕获、预提取后交接拉动 |
| `src/main/java/org/academy/internal/common/ability/aeromanip/movement/HighSpeedJetStructureService.java` | 喷嘴捕获、预提取后交接推进 |
| 三系 `program/Server*ProgramRuntime.java`、`ability/program/ProgramStructureExtraction.java` | 节点恢复时继续同一操作，实际到位才输出；程序退出清理 |
| `native/academy_physics/src/lib.rs` 与原生调用跟踪 | 实际 FFI 序列重放与接触诊断；未使用的 slip 候选已撤去 |
| `src/gametest/java/org/academy/internal/client/validation/StructureCapturePreviewSmoke.java` | 实际事务、来源绑定、提取与 200 tick 验证 |
| 同目录 `StructureProgramOrientationSmoke.java` | 三系真实 VM、提取高度/等待和角节点链验证 |
| 同目录 `SubLevelLiftTiersSmoke.java` | H 三档/200 tick、连续旧体及外扰；真实 BLOCKED 硬顶负用例；两后端真实已捕获植物与动态树叶全局事件 |

## 复现实验命令

使用仓库 JBR 25 环境，每次用新的隔离目录名；不要以 `-Fresh` 覆盖需要保留的失败证据。无人值守客户端应在该隔离目录的 `options.txt` 中设置 `pauseOnLostFocus:false`，避免把暂停误判为控制器超时；不修改用户日常运行实例的选项。

```powershell
. ./build/native-physics-p16-env.ps1
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name 'extraction-motion-check' -Fresh
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
  -I validation/native_structure/structure_capture_preview.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-extraction-motion-check `
  -PacademyStructureExtractionMotion=true --console=plain
```

Sable 改为 `runClientSable`，加 `-PacademySableRuntime=sable -PacademyStructureCapturePreviewRun=clientSable`，另用 fresh 副本。该 API 夹具直接验证提取服务；实际技能与节点另由以下两套夹具检验。

```powershell
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name 'extraction-vm-check' -Fresh
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
  -I validation/native_structure/structure_program_orientation.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-extraction-vm-check `
  --console=plain
```

VM 的 Sable 变体使用 `runClientSable -PacademySableRuntime=sable -PacademyStructureOrientationRun=clientSable`。命令不传 `academyStructureExtractionGameplay`，应仍输出 `extraction_gameplay_enabled=true`、各系 `actual_extraction_rise` 和 `wait_ticks`，才证明生产默认接线生效。

```powershell
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name 'extraction-h-check' -Fresh
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
  -I validation/native_structure/sublevel_lift_tiers.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-extraction-h-check `
  -PacademySubLevelLiftEmbedded=true -PacademySubLevelLiftTerrain=step `
  -PacademySubLevelLiftDisturbance=true '-PacademySubLevelLiftAnchor=-156,123,61' --console=plain
```

默认保留上一档发射结构，不启用任何 slip 策略；不能改成逐档删除结构后宣称连续场景通过。H 的 Sable 变体使用 `runClientSable -PacademySableRuntime=sable -PacademySubLevelLiftRun=clientSable`。新 H 夹具同时输出日志 `SUBLEVEL_LIFT357_SMOKE_PASSED/FAILED` 和 `sublevel-lift-tiers-result.json`；应读取 JSON 的 `status` 与 `passed`，不能只看 Gradle 正常退出。

植被验收加 `-PacademySubLevelLiftVegetation=true`，通过公共 `StructureSpaces` 查询真实捕获的草/小麦，并在捕获后放置树叶，要求实际清除且全局 `BreakBlockEvent`、`BlockDropsEvent` 计数均大于零。硬顶验收改 `-PacademySubLevelLiftTerrain=roof`；新路线必须在真实 H 释放时得到 `REJECTED:extraction_preview:BLOCKED:<detail>`，随后至少 5 tick 核验源区域与基岩未变、无新增结构及遗留控制会话。没有会话、未命中目标或其他拒绝原因不能代替 BLOCKED 证据。显式 `-PacademyStructureExtractionGameplay=false` 仍保留原控制器硬顶下受阻的旧断言，用于对照。

```powershell
# 真实原生调用序列重放：需要此前实际采集的文件。
. ./build/native-physics-p16-env.ps1
$env:ACADEMY_PHYSICS_TRACE = Join-Path (Get-Location) 'build/wedge-native-trace-1.txt'
cargo test --manifest-path native/academy_physics/Cargo.toml --locked --offline replay_client_trace -- --ignored --nocapture
Remove-Item Env:ACADEMY_PHYSICS_TRACE
```

以下命令仅记录已撤回 slip 候选当时的实验配置；不要求在最终源码重复运行，也不能据此声称默认 S0 全绿。

```powershell
# 历史候选：显式对受测提取体授权。
$env:ACADEMY_STEP_SOCKET_SLIP = 'true'
cargo test --manifest-path native/academy_physics/Cargo.toml --locked --offline -- --nocapture
Remove-Item Env:ACADEMY_STEP_SOCKET_SLIP
cargo test --manifest-path native/academy_physics/Cargo.toml --locked --offline extraction_slip -- --nocapture
```

最终修订已实际执行以下必需验证；两条命令均成功：

```powershell
./gradlew.bat test sableCompatTest nativePhysicsTest build -x testNativePhysicsRust -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat build -DisDev=false @physicsNativeArgs --console=plain
```

以上开发命令显式跳过原生 Rust S0 前置；默认 Rust 另以 `cargo test --manifest-path native/academy_physics/Cargo.toml --locked --offline -- --nocapture` 运行，真实结果为前述 **23 通过、5 失败、1 ignored**。Java FFM 通过不代表 native S0 默认场景通过。Gradle 的 `--tests` 应紧跟相应 `test` 任务单独执行，不能绑定给 `runClientDev`。

## 最终验证与产物（2026-10-24）

下表数量来自最终 XML；不重复计算开发/发布构建，也不混入已撤回 slip 测试或 Rust 红测。

| 检查 | 最终结果 | 证据 |
| --- | --- | --- |
| `test` | 3,032 项通过，0 失败/错误/跳过 | `build/test-results/test` |
| `sableCompatTest` | 28 项通过，0 失败/错误/跳过 | `build/test-results/sableCompatTest` |
| `nativePhysicsTest` | 36 项通过，0 失败/错误/跳过 | `build/test-results/nativePhysicsTest` |
| `editorTest` | 104 项通过，0 失败/错误/跳过 | `build/test-results/editorTest` |
| JVM 合计 | **3,200 项通过** | 四套 XML 汇总 |
| 开发构建 | `BUILD SUCCESSFUL`，44 秒 | `build/extraction-final-dev.log` |
| 发布构建 | `BUILD SUCCESSFUL`，42 秒 | `build/extraction-final-release.log` |
| 本地最终三系 VM | 矢量 2.978786540 格 / 23 tick；电磁 0.976264649 格 / 17 tick；空气 0.979810365 格 / 16 tick | `build/extraction-vm-native-owner-final.log` |
| Sable 最终三系 VM | 矢量 2.976470947 格 / 22 tick；电磁 0.984344482 格 / 17 tick；空气 0.978240967 格 / 15 tick | `build/extraction-vm-sable-owner-final.log` |

两个最终 VM 均在默认开启提取的条件下执行实际 extract→control→rotate→reset→spin 250 tick→release，完成节点顺序为 `[2,3,4,8,9,10]`，每系均有 44 个超过 200 tick 的 spin 采样。它们直接运行真实 VM，`actual_save_execute_packets=false`，不代替编辑器保存/执行网络包验收。独立 JSON 位于对应 `run/native-sublevel-direct-smoke-extraction-vm-*-owner-final/structure-program-orientation-result.json`。

最终产物与 SHA-256：

| 产物 | 字节数 | SHA-256 |
| --- | --- | --- |
| `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` | 31,031,215 | `c8f1ae1e2447baf3a27d16c4965b371a57a79bb85dae7a18625e5d47d0511dea` |
| `build/libs/academy-26.2.0-0.0.4-alpha-release.jar` | 23,560,114 | `b9a5de87f1ad24a38acaf16703404b4dffa4a75f8ab113ce7cf9b14cfe418720` |

两份 JAR 内 Windows 原生资源解码后均为 **2,735,616 字节、ABI 2**，SHA-256 均为 `f68c901911b18be6f92a9ea9e44b1e09cea8d34dd75ff08af50f035f6dcf4a06`，与随包清单及已跟踪资源一致。这是完整性校验，不是数字签名。工作区源码指纹因原有 `cfg(test)` 诊断及新增重放器而不同于分发清单，不能把这种差异解释为生产解算器已经更新。

原始日志、隔离副本 JSON 和 JAR 位于 Git 忽略的 `build/`、`run/` 下；可提交的[机器汇总](../validation/native_structure/structure_extraction_wedge_validation_2026_10_23.json)保留命令、结果、产物校验值、失败历史与未完成项。

## UNVERIFIED 与完成判据

- 最终 3,200 项 JVM 测试、开发/发布构建及两个后端的 `owner-final` 三系 VM 已通过；这些测试不能替代真实事务重试与完整生命周期矩阵。
- 无植被 H 的 native r3、Sable r5 已分别通过台阶/嵌地/外扰、三档连续保留旧体与 200 tick。硬顶两后端 r3 为严格 BLOCKED 通过，仍不扩大为所有场景验收。Sable 默认磁力/空气技能包入口尚未单独专项验证，不能用 VM 接线通过替代。
- 最终 native 植被 r6 / Sable r9 已通过三档、200 tick、植物保留、树叶全局事件和开阔轨迹发射；受保护区域事件取消、任意模组植被及远坐标仍待实机验收，保留全部失败历史。
- 完整 control/content/direct 矩阵因 ContentSmoke 地块复用断言失败尚未通过，缺少旧基线对照；独立默认 direct 的 15 阶段通过只覆盖列明技能和交接。
- 新提取与外部旋转/发射争用、卸载、身体破坏、主体死亡/断线、预演过期、捕获完成后来源变化等完整矩阵仍待实际验证。已有捕获预演与来源反例继续保留。
- 已核对未使用的 native slip 候选无残留生产接线、分发 DLL 无变化。现有硬顶负例、接触扫掠单测与到位交接证据不能替代各种墙面、动态体碰撞、地板摩擦及取消/暂停/失权的完整实机矩阵。
- 原无授权 S0 红测不因已撤回授权矩阵通过而关闭。默认开启的是本轮有界实际提取路线；不能以此宣称通用原生摩擦问题或 S0 基线已全部修复。
- 性能承诺需多结构、真实地形和子步耗时采样；这里的功能通过不构成与 Sable 性能等价的证据。自动归块、攻击碎裂及 MPS/流体/软体仍属后续阶段。

节点或技能失败时先区分等待预算：生产 VM 总寿命 72,000 tick；本轮单次提取节点 800 tick；预演从计划起 400 tick；磁化覆盖从开始 200 tick，磁/气会话约 600 tick。`SubLevelProgramGraphSmoke` 总夹具 600 tick，`StructureProgramOrientationSmoke` 及其专用调度 800 tick，其中 spin 固定 250 tick。应根据 `capture_session_ended`、`extraction_preview_expired`、`extraction_timeout` 或夹具超时的具体阶段定位，不统一加大等待掩盖卡位。
