# 结构技能与精密操作迁移接续记录

更新日期：2026-10-13。此文件随每个实现/验证节点更新；状态以实际代码和结果文件为准。

## 用户目标及约定

- 修复动能投掷拉起结构后无法稳定站立、抖动和移动困难。
- 整个物理结构是一个技能目标，命中的局部方块只提供锚点；不能把每一格分别当作实体施力。
- 同时推进动能投掷、高速喷流、磁场操作，以及矢量操作、电击使、空力使精密节点迁移。
- 用户已确认复现组合：Academy + Sable + Create + 航空学。独立模式也必须保留完整能力目标。
- 最新明确要求：未安装 Sable 时，动能搬运也必须创建新独立后端的物理化方块，不能继续使用旧 `BlockStructureEntity`。修补旧实体承载仅可作为对照，不能视为完成迁移。
- 用户已确认“终止”释放自己的控制，保留结构、惯性与重力；“安全静态化”另设操作。
- 公共能力服务同时供技能、程序和非玩家入口调用。不能跳过权限、总质量、有限力或内容守恒。

## 工作区基线与提交边界

- 仓库 `D:/mcmodtest/AcademyCraft-Reborn-26.2`，分支 `26.2`，最近提交 `05935f82 接入Sable结构牵引与整结构精密查询`；第十七批 Sable 查询/有限力牵引桥已随之提交。
- P19 公共捕获事务（新 API、事务包、gametest、单测、验证脚本）为新增未跟踪文件；待用户确认后提交，不能与其它既有改动混提或回退。
- `docs/vfx/darkmatter_reconstruction_v3/runtime/multiplayer/` 中既有未跟踪记录与本任务无关，不能混入提交。
- `AGENTS.md` 要求完整功能与必要验证结束后，先获得该批本地提交确认；本轮修复尚无新的提交授权。
- 外部 `D:/mcmodtest/create-fork` 有大量既有改动。Suite 专服模型注册补丁保存在 `validation/sable_compat/`，尚未套用；先前跨仓库范围问题仍待答复，不能当作已修复。

## 已知实现与定位

| 路径 | 当前情况 |
| --- | --- |
| `KineticThrow.captureAndLift` | 仍调用 `BlockStructureApi.capture` 与 `BlockStructureKinetics.holdAt`，创建旧 `BlockStructureEntity`；混装时也没有转成 Sable 船 |
| `BlockStructureEntity.tick/maintainStandingEntities` | 两端各自推进结构运动，但承载仅服务端执行；每个受支撑实体每 tick 设置 `hurtMarked=true`；需复现客户端/服务端纠正及跳跃/边缘行为 |
| `MixinEntityGetter` | 用旧结构复合形状替换宽相 AABB，另有空间索引补充；不能删除其它模组提供的碰撞形状 |
| `PneumaticGrasp` | 定位时固定质量上限为 32，且 Sable 烟测只有一格；P18 已改为公共整质量预算，并通过 25 格质量 50 的混装牵引。旧实体分支仍按 Entity 获取和写速度 |
| `StructureQueries/StructureTraction` | 已接入固定哈希 Sable 查询及真实 prePhysics 有限力；旧 `BlockStructureEntity` 尚未统一进入该后端控制契约 |
| `StructureTransactions`/`StructureCaptureCoordinator` | P19 新增：后端无关的分阶段捕获事务；预检、WAL 日志、目标预留、复制校验、提交决定、源移除、发布回收与重启恢复；Sable 捕获仍显式返回不可用 |
| `NativeStructureManifest.removeEntry/releaseSlot`、`NativeStructureRuntime.discardEmpty` | P20 新增：空结构槽位回收；仅未加载、空、未隔离、无恢复证据、无物理附件的结构可释放；P19 回滚不再永久烧槽 |
| `NativePhysicsArtifact` 解析 | P21 更新：显式属性 → 显式环境 → home（属性/环境）→ 游戏目录 `academy/native_physics/` 固定布局；部分配置显式失败；物理打开异常统一为 `IllegalStateException` 并保留原因 |
| `NativeStructurePhysics.Region.rebind`/`rebindPhysicsAttachments` | P22 新增：物理区域重绑（成员全体必须容纳、布局分离校验、附件先原子持久化再采纳、控制保留、下一 tick 重建）；牵引目标越界时自动重绑一次；初次抓取仍严格要求盒内 |
| `NativeStructureProjectionEntity` 碰撞/承载、`NativeStructureCollisionBoxes` | P23 新增：投影按同步视图提供双方同源的实体碰撞（轴对齐精确、任意姿态有界细分），`canBeCollidedWith` 有内容时为真；服务端按“脚底接触点经旧/新姿态映射”搬运站立的实体，平移与旋转都正确 |
| `NativeCaptureWhitelist` | P19 新增：普通无 BE 正质量满方块的起步白名单；BE/流体/waterlogged/随机或计划 tick/重力和非满形状在首次写入前显式拒绝，不静默丢弃 |
| 三类 `Server*ProgramRuntime` | 仍存在 Entity/宿主 BlockPos、FallingBlock、直接赋速及回滚旧速度路径；不能把 Sable plot 地址当宿主格 |

以上为代码观察，尚未把“可能原因”写成已复现的结论。

## 本次进度

1. **完成**：读取工作区、既有计划和关键技能/碰撞入口；确认用户复现组合。
2. **进行中**：动能抬升站立抖动的混装复现与两端承载检查。
3. **部分完成**：公共整结构质量/运动查询、气流牵引质量预算及 25 格 Sable 混装场景；多材料/更大规模矩阵待验。
4. **部分完成**：公共精密查询节点及结构引用；三类行动节点、捕获/终止/旋转/移动/静态化待迁移。
5. 最终验证见下表；独立客户端、承载及节点编辑器人工操作仍待验。

### P18 当前编辑与验证进度

- 新增 `StructureDynamics.motion`：读取真实整刚体质量、质心和运动；不加载结构或启动求解器，不伪造外部后端的碰撞 epoch/睡眠状态。
- 新增公共 `PneumaticStructureControl`，按整结构质量设置 PD 增益、按技能等级限制总质量（128/256/512）与有限力（2000/4000/8000），资源倍率为质量/32、范围 1–16；气流牵引接入。已有内容变更会撤销控制，不能扩大结构后沿用低消耗。
- Sable 烟测新增 `-PacademySableCompound=true`（须同时开启普通 smoke），5×5 石头、真实总质量 50 的专服及 Suite 客户端 r1 均通过；随后保留轻型结构旧响应并加入程序查询断言，最终 Suite 客户端 r3 也通过。
- 公共精密节点新增 `query/look_structure`、`query/structure_motion`，使用 `ProgramStructureReference(reference,runtimeId)`，可以读取世界质心、格/秒线速度、弧度/秒角速度、总质量；不同方块命中共享结构身份。变量和选择节点支持该类型，不与 Entity/BlockPos/CONTROL_DESTINATION 隐式互转，不持久化到跨会话共享变量。共同节点自动提供给各能力分类，现有图格式/节点含义不变。创建、旋转、移动、终止等行动节点仍待迁移。
- 整体身份在卸载/重载后失效；读取状态受宿主维度和质心距离限制。大船可能命中近处表面但质心超出查询范围，此时状态节点没有输出；不是按命中方块读取部分质量。
- `p18-tests-r1` 通过；r2 因语言键排序和变量类型选项计数失败，已修正；r3 和最终轻型增益修订后的全量 2530 + Sable 11 均通过。
- 独立专服 `p18-r1` 已通过整结构程序查询、真实技能资源/释放/NPC 控制，但最后自由下落偏离固定区域，失败保留。按总质量直接放大轻型增益改变了旧落点，已改为质量不超过 2.25 时保留原 120/144 刚度、36 阻尼和 1000 力限，较重结构按质量/2.25 扩展并受等级绝对上限约束。
- 独立专服 r2 被随机生成高地占用夹具空间而拒绝启动，未修改世界；r3 改用新固定种子超平坦隔离世界重跑。失败日志/结果均保留。
- `StructureSupportClientSmoke` 为旧实体的真实集成玩家移动对照夹具，输入是合成 `ClientInput`，不是人工键盘；尚未运行。不能把它的结果当新独立后端玩家承载验证。
- 必须先补齐的生产差距：独立后端现为 4 个永久容器槽、最多 3 个固定 24 格物理区域、显式外部 DLL/native-access 配置；投影实体没有承载。Sable 也尚缺生产捕获事务。不能只把 `capture` 的返回类型换名就宣称完成技能迁移。

### 动能搬运的后续顺序（最高优先级）

1. **公共捕获事务**：预检选择、权限、内容与几何预算；目标预留、持久化日志、复制校验、源移除、目标提交；每个阶段提供异常及重启恢复证据。先明确普通块范围，BE/计划 tick 不得静默丢弃。**P19 已交付机制与原生后端接入（见下节）；Sable 生产捕获与真实跨 JVM 强杀恢复仍属后续。**
2. **独立后端可供技能使用**：制品部署、容器容量/回收、活动物理区域跟随与恢复，以及真实玩家碰撞/承载。不能让玩家每四次使用便耗尽永久槽，也不能发布仅在测试 JVM 外部属性下可运行的默认技能。**全部子项已交付：P20 槽位回收、P21 制品部署约定、P22 区域重绑与跟随、P23 真实碰撞与承载（见下节）。**
3. **双后端捕获接入**：未装 Sable 创建独立结构，兼容混装创建 Sable 结构；未知版本/不可用时给出失败并保留源方块，不降级为旧实体。外部 assembly helper 仍只作测试工具，不能代替生产事务。
4. **迁移 `KineticThrow`**：保持区域选择和能力预算；HeldStructure 改为公共引用/租约；抬升、投掷、终止遵守有限力、单位、惯性契约；接触伤害只结算一次，不在世界碰撞时自动变回方块。
5. **承载验收**：独立和完整混装各测静止、升降、行走、跳跃、边缘、顶墙和旋转，之后测真实双端延迟。旧实体对照测试不能替代这些结果。
6. 再迁移高速喷流/磁场操作和三类行动节点；矢量改变运动、磁力筛选材料、空气通过受力锚点控制，共用结构身份、质量、资源和释放契约。

**当前不能对用户声称：动能搬运已改用新独立后端、站立抖动已修复、三个能力的行动节点已迁移完成。** 事务机制与原生后端接入口已就绪，但技能生产路径尚未改动，以上是接续工作的实现依赖，并非已交付功能。

### P19 公共捕获事务（2026-10-13）

- 新增公共入口 `StructureTransactions.capture/poll`；请求为 `StructureCaptureRequest`（位置集合、格数与跨度预算、逐格权限谓词），结果为 `StructureCaptureResult`（PENDING/SUCCESS、显式拒绝原因与 `RECOVERY_REQUIRED`、交易 ID 与结构引用）。公共签名不含 Sable 类型。
- 新增后端无关事务实现 `internal/common/structure/transaction`：
  - `StructureTransactionJournal`：每交易一个原子替换、强制刷新、往返校验的 NBT 日志；阶段 `PREPARED → TARGET_ALLOCATED → COPIED → COMMIT_DECIDED → PUBLISHED` 只进不退；损坏或身份不符的记录只隔离保留并报警，绝不猜测方向；`records()` 重新扫描目录以观察其它会话的写入。
  - `StructureCaptureClaims`：来源格全有或全无互斥；拿不准或待恢复的交易持续占用，阻止重复捕获。
  - `NativeCaptureWhitelist`：起步普通方块白名单 + 语义门（BE/流体/waterlogged/随机 tick/计划 tick/重力/非满方块/不可破坏），全部返回机器可读原因。
  - `StructureCaptureCoordinator`：服务端线程状态机，含提交前回滚与提交后前向完成两条收敛路径；重启时在服务器启动阶段读取日志并先占用来源格。
- 原生后端流程：预检 → 持久化 PREPARED → 槽位预留并记录目标（写入占用失败保持源格）→ 等待目标激活（超时回滚）→ 同 tick 逐格 `compareAndSet` 复制、整批复核与几何预算校验 → 持久化 COPIED 与提交决定 → 宿主源格以前后镜像移除并整批复核 → 目标保存 → PUBLISHED → 延后邻居通知 → 回收日志。提交前异常清空已写目标、停用并删除日志；提交后异常或未知内容保留日志与占用；目标保存失败在时限内重试。
- 重启恢复：`PREPARED` 直接回收（从未写入内容）；`TARGET_ALLOCATED/COPIED` 在来源镜像成立时清空目标、停用并回收；`COMMIT_DECIDED/PUBLISHED` 前向完成剩余源移除与目标保存；来源或目标出现未知内容时保留证据，不自动补发。
- 验证（最终修订版）：单元 2546 项（新增 16 项：日志往返/阶段推进/损坏隔离/超限拒绝/载荷防御、占用互斥、请求与结果契约）与 Sable 11 项零失败/错误/跳过（`build/p19-tests-final.log`）；`build -DisDev=true` 34 秒、`-DisDev=false` 55 秒通过（`build/p19-build-dev-final.log`、`build/p19-build-release-final.log`）；专用 gametest `academy:structure_transactions`（隔离目录 `run/native-structure-transaction-gametest-r5`，专用 init 脚本 `validation/native_structure/transaction.init.gradle`）通过：完整提交与源移除、目标内容一致、无掉落、日志回收、重复捕获拒绝；BE/随机 tick/waterlogged/白名单外/计划 tick/权限/块数/跨度的显式拒绝且源保留；`COPIED` 回滚与 `COMMIT_DECIDED` 前向两种恢复完成（日志 `build/p19-gametest-r5.log`）。gametest 仅在其专用系统属性开启时注册，并消耗永久槽位，必须使用新隔离目录。
- 失败轨迹保留：r1 在 `thenWaitUntil` 抛 `AssertionError` 崩溃服务器（改用 `helper.assertTrue` 使等待循环可重试）；r2 waterlogged 楼梯被 `fluid_not_supported` 先拦（调整检查顺序输出精确原因）；r3 夹具向新分配槽位预写内容触发 `emptyContainer` 隔离（改为激活后经 `compareAndSet` 合法写入）；r4 通过后为 `begin` 增加异常收敛（回收日志、释放占用、返回明确失败）并复跑 r5。
- 未验边界：真实进程强杀后的跨 JVM 重启恢复（当前由同会话预置日志驱动同一恢复代码路径）；Sable 侧生产捕获仍显式不可用；原生槽位为永久认领，失败或回滚会留下空占位，容量与回收属后续「独立后端可供技能使用」；恢复完成不重发放置/邻居通知；技能尚未迁移到该事务；白名单为起步子集（不含箱子等后续 BE 适配）。
- 当前构建（含 P19，不含技能迁移）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `25C0ED95BA75FCD35200E98D09F4F1B584B69D4D1245A0BEBF1289AF97082C9A`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `926591F12222B6EB066EE1A8B209B5A79424624EE4F5BC978684D62FF83DB7C1`。未复制到整合包，未操作用户存档。

### P20 空结构槽位回收（2026-10-13）

- 新增 `NativeStructureManifest.removeEntry(UUID)` 与 `releaseSlot(int, UUID)`：候选索引先整体校验再原子写入并发布；存在恢复证据或仍被索引引用的槽位拒绝释放；owner 标记最后删除，中断只会泄漏认领，不会复活已删内容。
- 新增 `NativeStructureRuntime.discardEmpty(StructureRef)`：仅接纳未加载、内容为空、未隔离、无恢复证据、无物理附件且存储维正常的结构；先确认空并清理该槽位区域计划/流体 tick、保存存储层并复核，再移除索引，最后删除 owner 标记。成功即释放一个永久槽位。
- P19 集成：事务回滚（含重启恢复回滚）与分配日志失败路径在「清理目标 → 回收日志 → 停用」后调用 `discardEmpty`；日志删除在前、槽位释放在后，崩溃时最坏只是空占位泄漏。
- 验证（最终修订版）：单元 2548 项（新增 2 项回收用例：删除条目与释放标记的顺序、恢复证据拦截）与 Sable 11 项零失败/错误/跳过（`build/p20-tests-final.log`）；`build -DisDev=true` 35 秒、`-DisDev=false` 55 秒通过（`build/p20-build-dev-final.log`、`build/p20-build-release-final.log`）；gametest `academy:structure_transactions` 在隔离目录 `run/native-structure-transaction-gametest-r6` 通过：回滚恢复自动释放槽位、容量上限拒绝第五个分配、已加载空探针显式 unload+discardEmpty 后新结构复用释放槽（日志 `build/p20-gametest-r6.log`）。
- 失败轨迹：首次运行单测暴露 `persist()` 直传活索引导致的清空别名 bug（候选持久化内 clear+putAll 同一 map），拆分为普通持久化与候选持久化两条路径后修复。
- 未验边界：成功捕获或保留内容的结构仍占用永久槽，静态化或显式销毁属后续；启动不自动修复历史遗留的「已认领但无索引」空槽（仅记录为安全泄漏）；`discardEmpty` 目前是内部 API，尚未接入技能或命令入口；真实玩家流程未验。
- 当前构建（含 P19/P20，不含技能迁移）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `055082A2E4DE4555A96A99F1E27D5F8B45DDD4ED723F206633C8B14F744FDD6D`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `A96D6A4164327EC3FBED5D395C95457AB35224B76891D2A82032D8AC7D89863F`。未复制到整合包，未操作用户存档。

### P21 制品部署解析与统一异常（2026-10-13）

- `NativePhysicsArtifact` 新增纯解析 `resolve(properties, environment, conventionalDirectory)`：显式属性对 → 显式环境对 → `academy.nativePhysics.home`/`ACADEMY_NATIVE_PHYSICS_HOME` → 游戏目录固定布局 `academy/native_physics/`（平台库文件名 + `academy_physics.properties`）。部分配置（只设一项）或显式 home 缺件均显式失败，不再回退到更弱来源；空白值按未设置处理；哈希/版本校验与私有副本逻辑不变，仍不打包、不下载二进制。
- `NativeStructurePhysics.createRegion` 打开物理的统一失败面：`IOException` 与 `RuntimeException` 一律转为携带原始消息与原因的 `IllegalStateException`，权限缺失等诊断保持可读。
- 新增验证脚本 `validation/native_structure/physics_deploy.init.gradle`：隔离专服目录内按固定布局放置 `prepareNativePhysics` 制品、清空原生环境变量、不设任何 `-Dacademy.nativePhysics.*`，仅保留 `--enable-native-access=ALL-UNNAMED` 运行既有 `nativePhysicsSmoke=server` 探针。
- 验证（最终修订版）：单元 2551 项（新增 3 项解析顺序/部分配置/未配置用例）与 Sable 11 项零失败/错误/跳过（`build/p21-tests-final.log`）；原生 JVM 专项 20 项通过（`nativePhysicsTest`，显式属性路径回归）；`build -DisDev=true` 36 秒、`-DisDev=false` 57 秒通过（`build/p21-build-dev-final.log`、`build/p21-build-release-final.log`）；部署专服探针 `run/native-physics-smoke-server-deploy1` 退出 0：真实求解器 720 步、`binding_native_access=true`、`height=0.4999`、结果 `native-physics-server-result.txt` 为 PASSED，证明仅凭游戏目录约定即可发现并加载原生库（日志 `build/p21-deploy-probe.log`）。
- 未验边界：Linux `.so` 布局仅由同一代码路径覆盖，未在本机实跑；`--enable-native-access` 仍需启动器/部署方提供，模组无法在运行时添加 JVM 参数（仅在缺失时给出可操作诊断）；未在整合包/真实玩家环境验证。
- 当前构建（含 P19/P20/P21，不含技能迁移）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `0899E1505E8BCB1FB26CC5169197F1E9185411D499A9FFD215BD77726EAEB962`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `6C2429DCC682E2F7D64D8458814839510C4F8251B86B5990B97F55C3CF5B756B`。未复制到整合包，未操作用户存档。

### P22 物理区域重绑与牵引跟随（2026-10-13）

- `NativeStructurePhysics.Region.rebind(AABB)`：显式把活动区域重绑到同宿主新边界。前置校验新边界尺寸、与其他活动/存档区域的 1 格分离、以及**所有当前成员**（不只发起者）在新盒内的实际占用（避免多成员编队跟随导致同伴出界终态失败）；随后 `NativeStructureRuntime.rebindPhysicsAttachments` 先整体校验并按候选索引 `putAll` + 持久化全部成员附件（失败恢复内存与磁盘旧值），再采纳新边界与原点、重建地形缓存并就地释放原生世界（**保留瞬时控制**），状态置 PAUSED 由下一 tick 重建发布。被拒绝的重绑不改变任何一侧。`bounds/origin/terrainCache` 因此改为可变字段（原先 final）。
- 牵引跟随：`NativeStructureTraction.Session.update` 在目标仅因当前盒边界被拒时，用纯函数 `NativeStructureTractionBounds.recentered` 计算同尺寸、按宿主边界与高度夹取的候选盒，重绑成功后复检并按原有限力路径推进；每次 update 最多重绑一次。初次 `acquire` 仍要求身体与目标都必须在当前盒内，不自动迁移。恢复一致性：重绑先持久化附件，重启 `resumeRegion` 直接使用重绑后的边界。
- 验证（最终修订版）：单元 2553 项（新增 2 项 `recentered` 纯函数用例）与 Sable 11 项零失败/错误/跳过（`build/p22-tests-final2.log`）；区域两阶段真实专服探针 `run/native-structure-physics-regions-20261013-p22-r3`：prepare 与 resume 均 PASSED、退出 0，证据含 `region_rebase_conflict_rejected`、`region_rebase_adopted_and_persisted`、`region_rebase_resumed`、`region_rebase_pose_preserved`，新 JVM 恢复后 `resumed_rebased_bounds=true`（日志 `build/p22-regions-prepare-r3.log`、`build/p22-regions-resume-r3.log`）；含真实抓取技能路径的世界烟测 `run/native-structure-physics-smoke-server-p22-r2` 退出 0（`build/p22-world-smoke-r2.log`）；事务 gametest `academy:structure_transactions` r7 沿用通过；`build -DisDev=true` 38 秒、`-DisDev=false` 58 秒通过（`build/p22-build-dev-final2.log`、`build/p22-build-release-final2.log`）。
- 失败轨迹：首次 prepare（p22-r1）在迁移后的 `verifyRegionInfo` 失败——该断言与 resume 阶段都硬编码原始边界；改为阶段感知期望（resume 阶段预期迁移后边界）并为迁移后摘要增加专用断言。
- 未验边界：跟随目标必须能放进同尺寸重绑盒（含 0.25 安全内边距），因此长距离牵引需要技能逐 tick 更新较近目标，一次越界远目标仍以 `OUT_OF_BOUNDS` 结束；`rebind` 目前是具体 `NativeStructurePhysics.Region` 的公开方法而非 `StructurePhysicsRegion` 接口成员，尚未接入技能/命令显式入口；重绑不改变成员内容与存档；真实玩家承载仍未实现。
- 当前构建（含 P19/P20/P21/P22，不含技能迁移）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `E8BDA66804D06917EBE22A3C08AB0EAD840D00A41CA062087F2AB25FEE34B6B0`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `21A77CD5AB884A2E1414E90BB81D02B0589280FE1FD9B7D81BFC22F4307AB399`。未复制到整合包，未操作用户存档。

### P23 真实玩家碰撞与承载（2026-10-13）

- `NativeStructureProjectionEntity` 实现 `BlockStructureCollision`：碰撞盒由同步视图推导（`NativeStructureCollisionBoxes.localBoxes` 以视图自身作为 BlockGetter 解析邻接状态），轴对齐姿态精确变换，任意四元数姿态按输出预算（2048 盒）做 3³→2³→整盒 的均匀细分近似；服务端与客户端从同一发布状态导出同源形状。投影在加入/同步数据/姿态视图更新时注册进 `BlockStructureCollisionRuntime`，移除时注销；`canBeCollidedWith` 仅在当前内容有碰撞时为真，`isPushable=false`、`isPickable=false` 不变。
- 服务端承载：`NativeStructureRuntime` 每 tick 在投影更新后，对脚底落在结构表面容差内（站立 0.18 / 恢复 0.5）的实体执行搬运——把脚底接触点经**上一姿态**映射到局部网格坐标、再经**新姿态**映射回世界（平移与旋转都正确），随后对齐表面、保留落地状态与 `hurtMarked=true`；带物理附件与非附件结构同样适用（非附件结构姿态不变则自然无操作）。
- 集成修复（P22 缺陷）：牵引跟随重绑后区域处于 PAUSED 待下一 tick 重建，技能若同一 tick 继续 `lease.setControl` 会被 `controllable` 判定不可用而结束会话；现在重绑当 tick 保留会话并延后一 tick 下发新指令。
- 失败轨迹：初版承载按 `pose.worldOrigin()` 增量搬运；该结构局部质心位于局部 y≈80，旋转时姿态原点每 tick 扫掠约 10 格，导致骑乘实体被甩飞并连带真实抓取技能目标失控（GRASP HOLD 失败、眼位漂移）；改为接触点映射后修复。
- 验证（最终修订版）：单元 2558 项（新增 5 项碰撞盒用例：轴对齐判定、独立角点变换、细分预算与覆盖、支撑面语义）与 Sable 11 项零失败/错误/跳过（`build/p23-tests-final.log`）；世界烟测 `run/native-structure-physics-smoke-server-p23-r12` 退出 0，承载证据 `support_rider_standing=true`（脚底 112.0 = 期望表面 112.0）、`support_carried_delta=1.63`、`support_descend_feet=112.0`、`support_carrying_both_directions=true`，真实抓取技能与其余全部阶段通过（`build/p23-world-smoke-r12.log`）；区域两阶段探针 `run/native-structure-physics-regions-20261013-p23-r4` prepare/resume 均 PASSED（含重绑恢复）；事务 gametest r8 通过；`nativePhysicsTest` 20 项通过；`build -DisDev=true` 38 秒、`-DisDev=false` 60 秒通过（`build/p23-build-dev-final.log`、`build/p23-build-release-final.log`）。
- 未验边界：非轴对齐碰撞是有界细分近似（盒预算 2048，超限时降低细分精度）；承载只对支撑判定容差内的实体生效，未测高速自旋与跳跃/下船继承表面速度等细节；客户端本地玩家碰撞依赖同步视图，未做真实双端键盘操作验证；投影仍不可交互（`isPickable=false`）。
- 当前构建（含 P19–P23，不含技能迁移）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `2197C2B2211B737ADA5E7B756BEDE0D3540D87D31A7E2E435C3D8791EB096AFB`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `D66097FB72A76F53312FB932998891C13207BAD06677820BDAD5C2352997D932`。未复制到整合包，未操作用户存档。

### P18 最终结果（2026-10-13）

| 验证 | 结果 / 证据 |
| --- | --- |
| 全量测试 | `build/p18-tests-final.log`：2530 个基础测试、11 个 Sable 测试通过，零失败/错误/跳过；包括新结构类型的图编译、变量保存、转发运行时、质心与物理单位 |
| 构建 | `build/p18-build-dev.log` 23 秒通过；`build/p18-build-release.log` 33 秒通过；包括编辑器测试及 Sable 打包/依赖门禁。均基于最终代码，之后仅补充文档 |
| 独立专服 | `run/native-structure-physics-smoke-server-20261013-p18-r3`：572 tick、退出 0、正常服务端线程结束；真实技能资源、运动、释放、NPC、程序整结构查询及旧 activation 拒绝通过；日志 `build/p18-native-server-r3.log` |
| 最终 Suite 集成客户端 | `run/sable-compat/p18-compound-suite-client-r3/client`：25 格、质量 50、55 tick；查询、真实气流牵引、手杖互斥、释放及回调撤销通过，客户端观察 47 个运动样本，退出 0；截图 `screenshots/academy-sable-structure-20688.png` 已查看；日志 `build/p18-compound-suite-client-r3.log` |
| Suite 客户端 r2 失败 | 等待截图 410 tick 后技能结束；测试 options 开启失焦暂停，未出现截图完成日志。r3 仅将新隔离目录 `pauseOnLostFocus` 设为 false 后通过；不能把 r2 计为成功 |
| 本轮独立客户端 | `run/native-structure-physics-smoke-client-20261013-p18-r1` 已准备测试元数据，**尚未启动**。用户按 Esc 停止 Computer Use 后未继续窗口操作；`runClientDev` 新回归和节点编辑器人工检查待验 |
| 站立抖动 | 新后端承载未实现，旧实体对照夹具未运行，不计为修复 |

验证动作由服务器夹具发起；Sable 施术者是嵌入连接，未冒充真实 TCP/UDP 多人输入。相机为真实集成客户端，`actual_keyboard_actions=false`。r1/r2 失败结果均保留。原生 ABI/Rust/存储写入逻辑未改动，可复用 P17 对应专项；本轮没有重新声称已做全矩阵。

最新构建仍**不含动能搬运新后端捕获/承载修复**：

- `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256：`2F3E99EC1F686FB0CC5881C7EF5E196937DEC88BC3B3FA1DC84EFAE7C2E73332`。
- `build/libs/academy-26.2.0-0.0.4-alpha-release.jar` SHA-256：`03487257FFD40D38AB9BE6B14230B9B189F21A5A28D17DB0FBAB060EB50753FE`。
- 未复制到用户整合包，未操作用户存档，未创建本地 Git 提交。

重跑命令模板（每次先准备新目录，替换 `NEW`，不要复用本批 r1/r2/r3）：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-NEW @physicsNativeArgs -DisDev=true
./gradlew.bat runClientSable -PacademySableRuntime=suite -PacademySableSmoke=true -PacademySableCompound=true -PacademySableRunDirectory=run/sable-compat/NEW -DisDev=true
```

独立专服应采用固定种子超平坦新世界，测试空间在 y≈105–120；随机高地会被正确拒绝。客户端只复制既有隔离测试世界的 `level.dat` 和 `data/minecraft/world_gen_settings.dat`，不复制旧区块/结构；仅在新测试目录设置 `pauseOnLostFocus:false`，避免等待客户端观察时服务器夹具继续消耗资源。用户已停止本轮窗口自动化，不应在本轮继续输入或启动另一个客户端。

## 前一批可复用证据及未完成项

- 最新人工测试 JAR 于 2026-10-13 构建；`build/manual-test-20261013-{tests,build-dev,build-release}.log` 通过。基础 2527、Sable 11、编辑器 104 项零失败/错误/跳过。
- 开发 JAR SHA-256：`A9ACBB77DA0C787ABA5E4C65751DAE324C76E58296C82B3257B34D28D6431C03`；发布 JAR：`34940D99DBC87EA4EF707AEFF303D4E345FAAD5D471A21B487E8AE07A3E8A0AB`。这两份正是用户报告问题前提供的包，不代表本次问题已验证。
- P17 纯 Sable 专服、纯 Sable/Suite 集成客户端、独立物理专服/客户端通过限定场景，详见 `docs/block_structure_entity_system.md` 第十七批。
- 固定 Suite 专服在外部模组构造时加载客户端模型类而失败；`build/sable-p17-suite-server-r1.log`。客户端通过不代表专服通过。
- `run/sable-compat/p17-retention-r1` 完整旧世界副本运行 120 tick 的内容保护断言通过，但退出夹具竞争写入 FAILED；外部核验失败，不能计为通过。夹具修复/新副本重跑尚未完成，原失败文件必须保留。
- `SableStructureHandle.read` 已修复 bbox 内缺 holder 不得返回 AIR；编译/基础测试通过，专项真实缺 holder 场景尚未运行。
- Sable 生产捕获、可恢复静态化、跨后端迁移、通用拆分/接触仍未完成。公开装配 helper 仅用于隔离测试，不可直接作为生产内容事务。

## 接续操作入口

使用 JBR 25：`C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2`。可在仓库根 `. ./build/native-physics-p16-env.ps1` 设置既有验证环境；该文件不提交。

```powershell
.\gradlew.bat test sableCompatTest -DisDev=true
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
.\gradlew.bat runGameTestServer -I validation/native_structure/transaction.init.gradle -PacademyGameTests=academy:structure_transactions -PacademyStructureTransactionDirectory=run/native-structure-transaction-gametest-NEW -DisDev=true
```

外部固定依赖由 `docs/sable_compat_artifacts.json` 与 `gradle/sable-compat.gradle` 锁定。不要为了启动未知 JAR 放宽哈希门禁。新游戏验证使用新的隔离目录，不覆盖成功或失败结果，不操作用户存档。

后续每完成一个节点在此增加：修改文件、行为改变、验证命令及结果、失败轨迹、未验边界和紧接着应做的工作。已有完整架构设计位于 `docs/block_structure_physics_and_sable_compat_plan.zh-CN.md`。
