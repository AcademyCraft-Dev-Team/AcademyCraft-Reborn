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
3. **双后端捕获接入**：未装 Sable 创建独立结构，兼容混装创建 Sable 结构；未知版本/不可用时给出失败并保留源方块，不降级为旧实体。外部 assembly helper 仍只作测试工具，不能代替生产事务。**P24 已交付执行器抽象与 Sable 执行器（见下节）；恢复方向与长期行为边界见 P24 未验项。**
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

### P24 双后端捕获接入（2026-10-14）

- **第一步（已提交 `b4c6dd79`）**：新增 `CaptureTarget` 执行器边界与 `NativeCaptureTarget`，协调器完全后端无关；行为等价通过全套验证。
- **第二步（本批）**：新增 `SableStructureTransactions implements CaptureTarget`：
  - `allocate`：`allocateNewSubLevel`（occupancy 自动分配）后立刻以 `rotationPoint = plotOrigin + 0.5`、`position = origin + 0.5` 重锚，使公共 `StructurePose.worldOrigin() == 选区下角`、局部偏移 `n → 世界 origin+n`（与原生捕获同语义）；同一调用链预建 2×2 分区空 chunk，满足“空船跨 tick 会被质量失效销毁”的约束。
  - 写入/读取/清除：`LevelChunk.setBlockState(pos, state, SKIP_ON_PLACE|SKIP_BLOCK_ENTITY_SIDEEFFECTS)` 真实更新链路（Sable 自动更新质量/碰撞/热图/边界），坐标映射与只读桥完全一致；`releaseEmpty` = `removeSubLevel(ship, REMOVED)`（预决策回滚/容量回收）；`save` = `getHoldingChunkMap().saveAll()`（Javadoc 注明其吞异常边界）；`deactivate` 空实现；`resolve` 不强制加载未加载船。
  - 路由接线：`Bridge.captureTarget`（中立默认空）+ `StructureBackendRouter.captureTarget`（仅 SABLE 模式且有桥时转发）+ 协调器 `begin`/`targetFor`/`recoverPending`/`started` 按模式分派；INCOMPATIBLE/MIGRATION_REQUIRED 明细不变；打包必检清单加入 `SableStructureTransactions.class`。
- 烟测：`SableStructureSmoke` 新增 CAPTURE 阶段（`-PacademySableCapture=true`，默认关闭保持旧流程）：真实事务捕获 3 格石头并断言源 AIR、逐格读回一致、journal 回收；chest 负例返回 `block_entity_not_supported` 且源保留。
- 验证：单元 2558 + Sable 11 零失败（`build/p24b-tests-final.log`）；纯 Sable 专服 `run/sable-compat/p24-server-r2` PASSED、退出 0，结果文件含 `sable_capture_committed=true`、`sable_capture_rejected_be_preserved=true`；Suite 集成客户端 `run/sable-compat/p24-suite-client-r3` PASSED：`real_sable_plot_received=true`、运动样本 49、截图 `academy-sable-structure-49784.png`（354 KB）已查看存在、结果含两条捕获证据；`build -DisDev=true/-DisDev=false` 通过（`build/p24b-build-dev.log`、`build/p24b-build-release.log`）。
- 失败轨迹保留：专服 r1 在 NPC 阶段失败——CAPTURE 阶段跨 2 tick 未续租，NPC 牵引租约（leaseTicks=3）到期；修复为每 tick `session.update` 续租后 r2 通过。客户端 r1 因世界未预置失败（新建了 New World）；r2 按约定预置 `level.dat`+`world_gen_settings.dat` 后通过；r3 为用户关闭告警屏幕后按同一约定重跑复核通过。
- 未验边界：Sable 侧的**恢复方向**（PREPARED/TARGET_ALLOCATED 回滚、COMMIT_DECIDED 前向补全）未注入触发；`save` 失败重试到 `target_save_timeout`、多船容量回收、`quarantined`（协调器当前无调用点）未专项验证；捕获船交给 Sable 物理后的长期行为（持续下落、保存/重载一致性）未断言；`saveAll()` 吞部分 I/O 异常，持久性边界仍以 P21/P24 记录为准。
- 当前构建（含 P19–P24）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `856CF4988B7E4B2AA52E104456FB1362CE65386DA6A7764EF4DF5FBE0C770474`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `4B3924482CA545822B6F807487AC332FC0ECEE7713B6DADFC78B17D622A1E799`。未复制到整合包，未操作用户存档。

### P25 动能投掷迁移到公共结构服务（2026-10-14）

- 改动/新增文件：`KineticThrow.java`（Server 全路径重写）、`KineticStructureControl.java`（新增公共能力加速器控制预算）、`StructurePhysicsRegion.java`（`rebind` 提升为接口方法）、`NativeStructurePhysics.java`（`@Override`）、`NativeStructurePhysicsKineticThrowSmokeChecks.java`（新增专服烟测）、`NativeStructurePhysicsWorldSmoke.java`（新 `KINETIC` 相位与开关）、`physics_world.init.gradle`（`academyStructurePhysicsKineticThrowSmoke` 透传）、`KineticThrowTest.java`（纯函数用例）、中英文 `kinetic_throw.desc`（描述与实际行为对齐：无世界碰撞爆炸结算、不再变回方块）。
- 捕获：选区（下半椭球+上圆柱、前方半空间裁剪、立即阻挡裁剪）保持；新增确定性预算预裁 `cropToBudget`（按到 seed 距离平方升序、坐标 tie-break、seed 优先保留；NATIVE 128 / SABLE 256，经 `StructureBackendRouter.status().mode()` 判定；`maximumSpan=24`）。`executeActive` 扣费时机与不退款语义不变；改走 `StructureTransactions.capture/poll` 跨 tick `CaptureState` 驱动，失败以 `status:detail` 走日志+actionbar（沿用 `capture_rejected` 文案）。
- 抬升：原生以选区+lift 计算区域盒（每轴 ≤24、含自适应余量、宿主高度/世界边框夹取；无法容纳则明确失败并保留结构），`StructurePhysics.openRegion`+`attach`，等 `motion` 出现后 `acquireEntityControl`+`StructurePhysicsControl.Anchor`，每 tick `setControl/renew`；Sable 不建区域，`StructureQueries.raycast/validate` 取同 ref 命中后 `StructureTraction.tryAcquire`，每 tick `update` 续租。新增公共 `KineticStructureControl.settings(totalMass, tier, milestone, range)`：总质量预算 128/256/512，刚度/阻尼/最大力按参考质量 2.25 缩放并受绝对上限约束，`leaseTicks=20`。
- 投掷与飞行：原生先 `setVelocity(0)` 清除抬升残速，再 `region.applyImpulse(ref, direction × 50 m/s × milestone3(×1.2) × mass, 质心)`，纯惯性、控制槽为空；飞行每 tick 用投影 `worldCollisionBoxes()` 计算占用盒，按 `velocity×0.05 + 角速度半径余量` 预测，距区域边缘 <2 格时以同尺寸、宿主/世界边框夹取的候选盒调用 `region.rebind`（接口提升）；候选不可得或 rebind 失败 → 释放控制、保留结构、安全结束（不让区域 FAILED）。Sable 投掷/飞行明确不做：结束并保留结构（代码与本节注明）。
- 近邻伤害：抬升与飞行期间投影碰撞盒与附近实体求交，排除控制者/友军/旁观者/被承载者/`noPhysics`；per-target latch+10 tick 冷却；伤害 `5×blockCount`、击退沿用旧公式（milestone3 ×1.25，实体仍用格/tick），`SkillDamageSource.ofDirect` 的直接实体用投影（找不到投影时不会进入该分支）；每结构一次爆炸音效/粒子，锚点用结构中心；不再调用 `beginGravitySettlement`、不再变回方块。
- 清理：`releaseHeld` 只关闭租约/牵引会话并清 map，结构/物理区域/惯性保留；登录、换维、死亡、停服为静默清理，不抛异常。
- 单位与标定依据：旧投掷 2.5 格/tick × 20 = 50 m/s（`LegacyMotionUnits.velocityPerSecond`）；旧抬升 PD 0.22/0.65（1/tick）重标定为质量缩放的力 PD，参考体（质量 2.25）取 120 刚度、36 阻尼（与已审计的共享气流控制一致），重力静差约 m·g/k = 0.18 格；最大力 `min(2000<<tier, 1000×m/2.25)`，租约 20 tick。
- 验证：单测 2562 项 + Sable 11 项零失败/错误/跳过（`build/p25-tests-final.log`）；`build -DisDev=true` 22 秒、`-DisDev=false` 35 秒通过（`build/p25-build-dev-final.log`、`build/p25-build-release-final.log`）；专服烟测 `run/native-structure-physics-smoke-server-p25-r10` `PASSED`、`exit_status=0`，结果含 `kinetic_capture_committed`、`kinetic_capture_pending_observed`、`kinetic_source_cleared`、`kinetic_region_ready`、`kinetic_lift_anchor`、`kinetic_lift_rise=3.25`、`kinetic_throw_speed=50.0`、`kinetic_throw_control_cleared`、`kinetic_impact_damage=45.0`、`kinetic_impact_knockback`、`kinetic_follow_rebinds=10`、`kinetic_region_never_failed`、`kinetic_terminal_structure_active`、`kinetic_source_not_restored`、`kinetic_no_drops`（日志 `build/p25-world-smoke-r10.log`）；Sable 专服烟测 `run/sable-compat/p25-server-r1` `PASSED`、`exit_status=0`，仍含 `sable_capture_committed`、`sable_capture_rejected_be_preserved`（日志 `build/p25-sable-server-r1.log`）。
- 失败轨迹（目录与日志均保留）：p25-r1 投掷时抬升末速未清（y≈1.09）导致速度校验失败，改为冲量前 `setVelocity(0)`；r2/r3 单次 45 请求实际扣 94.32，逐层临时日志定位为 `KineticEnergyApplied` 被动（玩家伤害 ×2 并追加 CTA 伤害），烟测夹具改为只启用被测技能并清零目标护甲，技能新增 `Diagnostic.impacts` 断言“每接触只结算一次”；r7 白昼僵尸自燃引入 1.0 火焰伤害被误判为重复结算，夹具补火焰免疫；r4–r6 为临时诊断运行。r1–r10 目录、`build/p25-world-smoke-r*.log`、`build/p25-sable-server-r1.log` 均保留。
- 未验边界：Sable 的动能投掷发射与飞行；10 秒悬停超时后仅释放控制、结构与已开区域继续保留（每技能使用最多新增一个区域，长期回收未实现）；原生投掷损伤沿用加速器回退的玩家攻击类型，受常规护甲/减伤结算；真实双端与真实 TCP 客户端未验；静态化操作、载荷重新落回方块不在本节点。
- 当前构建（含 P19–P25）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `B9E72F45CAC4CC0E3F785E4C570EB9219659E56A5AA002917536C30B03CA9804`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `C192909C00F0BD1B0782340D4380F9E321DCA4E574F315793D0A56F1CAB21839`。未复制到整合包，未操作用户存档。

### P26 原生承载验收矩阵扩展（2026-10-14）

- 改动文件：仅 `NativeStructurePhysicsSupportSmokeChecks.java`（+270/-1；DESCEND 后新增 ROTATE/JUMP/EDGE/CEILING 相位，DONE 前清理临时物；既有 ARM/SETTLE/LIFT/DESCEND 语义不变）。Sable 后端与真实双端不在本节点。
- ROTATE：Anchor 抬离地板 0.9 → `setVelocity(ZERO, (0, 1.5, 0))`；旋转累计 60.13° 期间每 tick 断言支撑面存在且 |feet−surface|<0.05；rider 随转（世界位移 0.290、结构本地漂移 2.6e-4）；停转后回落稳定（feet=112.00000427）。证据 `support_rotation_supported/rider_followed=true`。
- JUMP：`setDeltaMovement(0, 0.55, 0)` 后连续 5 tick 离面 >0.5 且未被承载拉回，峰值 2.03 格，落回后重新被支撑。夹具 rider 为 noAI：`aiStep` 对非有效 AI 跳过 `travel`，夹具每 tick 显式调用 `rider.travel(Vec3.ZERO)` 推进原版积分（模拟跳跃，非键盘输入）。证据 `support_jump_leaves_surface/relands=true`。
- EDGE：水平 Anchor +2.0（静止摩擦下实际滑移 1.752），滑动期每 tick 支撑对齐、|Δrider.x−Δcom.x| 最大 0.043；再给 rider 每 tick +0.15 水平速度走出边缘，支撑消失后自然下落并落到真实地板（landing_feet=111.0=floorTop），region 保持 READY。证据 `support_edge_translation_follows/walk_off_falls/region_ready=true`。
- CEILING：rider 头顶 2 格放 3 块真实 STONE；Anchor 抬升 +2 全程 rider.maxY ≤ 天花板底面+1e-3（不穿透、无异常），rider 仅升 1.05 后被顶住；清理后回落稳定。当前无结构推挤能力：结构最终穿过 rider（`support_ceiling_structure_through_rider=true`，夹具只按“不穿透/不崩溃”断言并注明该限制）。证据 `support_ceiling_blocks_rider/no_clip=true`。
- 失败轨迹（目录与日志保留）：r1 JUMP 卡死（noAI 不积分 `setDeltaMovement`，显式 `travel` 修复）；r2 CEILING 放块后当 tick 挂 Anchor 被拒（宿主地形快照失效，需隔一个服务端 tick 重抓）。`build/p26-world-smoke-r1.log`、`r2`、`r3` 均保留。
- 验证：世界烟测 `run/native-structure-physics-smoke-server-p26-r3` PASSED、exit_status=0（858 tick；既有 `support_*`、`grasp_*`、`kinetic_*` 证据无回归，日志 `build/p26-world-smoke-r3.log`）；单测 2562 + Sable 11 零失败（`build/p26-tests-final.log`）；双构建通过（`build/p26-build-dev-final.log`、`build/p26-build-release-final.log`）。Sable 专服烟测未重跑（本改动仅在原生世界烟测夹具内，Sable 烟测不执行该相位）。
- 未验边界：Sable 后端承载；真实双端（真实客户端+服务端观察）与真实键盘行走/跳跃；EDGE 锚目标 +2.0 与摩擦实际滑移 1.752 属环境性质；CEILING 无推挤（结构穿 rider）。
- 当前构建（含 P19–P26）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `359243C796BC7D68EE6052441BAC312FD4AD695D4247A20AAE348CCC154AB0C8`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `266E1CAFEB39ECBB00E9DD36A21C482E378DB9237381E377FCBDB2F9906E10A6`。未复制到整合包，未操作用户存档。

### P27 高速喷流迁移到公共结构服务（2026-10-14）

- 改动/新增文件：新增公共 `JetStructureControl.java`（喷流推力与速度标定）；重写 `HighSpeedJetStructureService.java`（捕获/控制/发射/释放全路径）；`HighSpeedJetNozzle.java`（结构支持由旧 `BlockStructure` 实体改为公共 `StructureRef`，方块/实体支持不变，新增 ref 存档键）；`HighSpeedJet.java`（新增 `Events`：`PlayerTickEvent.Post` 驱动、登录/换维/死亡/停服清理）；新增 `NativeStructurePhysicsHighSpeedJetSmokeChecks.java` 与 `JetStructureControlTest.java`；扩展 `HighSpeedJetStructureServiceTest.java`（预算预裁/区域盒）；`NativeStructurePhysicsWorldSmoke.java`（新 `HIGH_SPEED_JET` 相位与开关、失败清理）；`physics_world.init.gradle`（`academyStructurePhysicsHighSpeedJetSmoke` 透传）。
- 捕获：选区（半径 2 球形，沿推力方向 `cropImmediatelyBlocked`）与喷嘴选择保持；新增确定性预算预裁 `cropToBudget`（NATIVE 128 / SABLE 256，经 `StructureBackendRouter.status().mode()` 判定，seed 优先保留，其余按 seed 距离平方 + 坐标 tie-break）；改走 `StructureTransactions.capture/poll` 跨 tick `CaptureState` 驱动。扣费时机与旧实现一致：**捕获提交并 attach 成功后**才扣 `(activationCpCost(1)+0.25/格)·成本曲线·costMultiplier` CP 与 `(activationAirCost(1)+0.5/格)·成本曲线` 压缩空气，之后不退款；捕获拒绝/超时以 `status:detail` 走日志 + actionbar（沿用 `structure_launch_rejected` 文案），失败保留源方块。
- 控制/发射：原生以捕获格包围盒构造区域盒（推力轴在宿主可用空间内扩展至 ≤24、以捕获体为中心；其余轴对称余量 ≤6；各轴 ≤24 且不越世界边界/高度），`StructurePhysics.openRegion`+`attach`，随后 `acquireEntityControl` + `StructurePhysicsControl.Force`，每 tick `setControl/renew`（租约 20 tick）。推力沿用旧标定：`JetStructureControl` 把旧 0.48 格/tick² 经 `LegacyMotionUnits.accelerationPerSecondSquared`（×400）换算为 mass·格/s²，乘 `effectMultiplier(power)` 与 milestone3 ×1.25，并受共享 `MAX_FORCE=10000` 绝对上限；速度上限 3/6/8 格/tick 经 `velocityPerSecond`（×20）换算为 60/120/160 格/s，`limitedThrustForce` 以每 tick 有限力复现旧 `clamp(v+F/m,上限)` 的整矢量收敛（lease 独占下不能瞬时置速）。飞行每 tick 用投影 `worldCollisionBoxes` 预测并按 P25 同构算法 `region.rebind` 跟随；无法重绑时释放控制、保留结构安全结束。喷嘴经公共 `StructureRef` 挂到结构并按 `StructureHandle.pose()` 跟随（位置/朝向随结构旋转；结构不可解析时回退方块支持判定）。
- Sable 边界：捕获经 `StructureBackendRouter` 路由（混装创建 Sable 结构）；提交后若 `reference.backend()==academy:sable`，附挂喷嘴后明确结束并保留结构（日志 + actionbar `sable_launch_unavailable`），不建旧实体、不回退旧路径；Sable 发射/飞行仍属未实现边界。
- 接触效果：沿用 P23 投影碰撞（`worldCollisionBoxes` + 一次性结算 + 目标 10 tick 冷却），闭速以格/s→格/tick 换算后调用旧 `BlockStructureKinetics.collisionDamage/collisionKnockback`（0.35 格/tick 门限、伤害上限 24、击退上限 2.4），伤害再乘玩家能力/伤害乘区，击退乘 `AeromanipTargeting.forceMultiplier`；受 `canAffectNegatively`/承载/`noPhysics` 过滤。
- 释放契约：`LaunchHandle.close`、登录、换维、死亡、停服均静默清理（关闭租约、恢复喷嘴计时、清 map），结构、物理区域与惯性保留；不再调用 `restoreToGrid/settleToGrid`，结构不会自动变回方块，`LaunchRequest.restoreWhenSettled` 仅为存档兼容字段。每玩家同一时间一个待捕获/一个会话，新发射先终止旧会话并保留其结构。
- 验证：单测 2570 + Sable 11 零失败/错误/跳过（`build/p27-tests-final2.log`，任务 `test sableCompatTest -DisDev=true`）；`build -DisDev=true` 21 秒、`-DisDev=false` 33 秒通过（`build/p27-build-dev-final2.log`、`build/p27-build-release-final2.log`）。专服烟测 `run/native-structure-physics-smoke-server-p27-r3` PASSED、exit_status=0（881 tick），证据含 `hsjet_source_cells=1`、`hsjet_nozzle_placed`、`hsjet_capture_committed`、`hsjet_capture_pending_observed=true`、`hsjet_source_cleared`、`hsjet_block_count=1`、`hsjet_resource_charged`、`hsjet_nozzle_attached`、`hsjet_region_ready`、`hsjet_control_active`、`hsjet_expected_speed=120.0`、`hsjet_launch_speed=115.37633965527773`、`hsjet_thrust_direction`、`hsjet_travel=41.11`、`hsjet_region_never_failed`、`hsjet_control_cleared`、`hsjet_terminal_structure_active`、`hsjet_source_not_restored`、`hsjet_no_drops`、`hsjet_no_legacy_entity=true`；既有 `grasp_*`、`kinetic_*`、`support_*` 证据无回归（`build/p27-world-smoke-r3.log`；最终修订前一轮 `p27-r2` 亦通过，`build/p27-world-smoke-r2.log`）。Sable 专服烟测 `run/sable-compat/p27-server-r2`（`-PacademySableCapture=true`）PASSED、exit_status=0，含 `sable_capture_committed=true`、`sable_capture_rejected_be_preserved=true`（`build/p27-sable-server-r2.log`；无 capture 的 `p27-server-r1` 亦 PASSED，`build/p27-sable-server-r1.log`）。
- 复现命令：`. ./build/native-physics-p16-env.ps1; .\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-NEW @physicsNativeArgs -DisDev=true`；Sable 侧 `.\gradlew.bat runServerSable -DisDev=true -PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true -PacademySableRunDirectory=run/sable-compat/NEW`（需先按 P25 约定准备隔离目录）。
- 失败轨迹（目录与日志保留）：`run/native-structure-physics-smoke-server-p27-r1` 首个 THRUST tick 即 `follow_rebase_failed`——区域沿推力轴单侧扩展使结构贴尾边、`edgeDistance` 立即 <2，首次 rebase 候选与既有夹具区域间隔 <1 被拒；改为以捕获体为中心、在宿主可用空间内对称扩展推力轴，并把烟测源点移到 `base+(0,4,32)` 后 r2 通过（`build/p27-world-smoke-r1.log` 保留）。Sable 首个 `p27-server-r1` 目录未准备 `server.properties/eula` 导致启动失败/挂起，补齐隔离目录后通过；该次失败日志被同名成功重跑覆盖，只能按“目录未准备”记录，原始输出不可再核对。
- 未验边界：真实双端与真实 TCP/键盘输入，喷口结构跟随/客户端高亮的双端视觉未验；Sable 的喷流发射与飞行（捕获后保留，Sable 烟测不执行 hsjet 相位）；原生接触的每 tick 投影采样在 ≥120 格/s 下可能跨过薄目标（旧实体 swept 碰撞语义），烟测未触发/断言接触；极轻单格结构的落地摩擦/边界滞留；每玩家单会话（新发射终止旧会话）；每次使用最多新开一个物理区域（上限 3）且长期回收未实现（同 P25）；结构卸载后喷口结构支持回退为方块判定，找不到支撑 100 tick 后丢弃。
- 当前构建（含 P19–P27）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `1FDD6FE893B088F44F4263035926D7357B9C174150EC3EB3B0C5271587905C05`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `5BD03EDC3F6F82117B7E6377EF51483EBF74A82A6C5FCC69C10C2C26FB15FBAC`。未复制到整合包，未操作用户存档。

### P28 磁场操作迁移到公共结构服务（2026-10-14）

- 改动/新增文件：新增公共 `MagneticStructureControl.java`（磁场牵引控制预算与旧速度曲线到原生力的换算）；新增内部 `MagneticStructureService.java`（吸附方块跨 tick 捕获、原生区域控制、P25 同构跟随、Sable 边界与释放契约）；重写 `MagnetManipulation.java` 的 Server/MoveContext 方块路径（实体路径、悬浮开关与资源/心跳语义不变；新增 `Events` 生命周期清理与 `Server.diagnostic/startTargetPull/stopTargetPull` 公共入口）；新增 `NativeStructurePhysicsMagnetManipulationSmokeChecks.java`、`MagneticStructureControlTest.java`、`MagneticStructureServiceTest.java`、`NativeStructurePhysicsSmokeCleanup.java`；扩展 `NativeStructurePhysicsWorldSmoke.java`（新 `MAGNET` 相位与开关）、`physics_world.init.gradle`（`academyStructurePhysicsMagnetManipulationSmoke` 透传）、中英文 `message.academy.magnet_manipulation.structure_pull_rejected`。
- 审计结论（迁移依据）：旧 `MagnetManipulation` **没有整结构/方块组路径**，也不引用 `BlockStructureApi/BlockStructureEntity/BlockStructureKinetics/beginGravitySettlement/asEntity`（全仓检索只命中旧实体自身与验证夹具）。旧方块路径仅把准星命中的**单格磁力方块**转为 `FallingBlockEntity`（`createFallingBlockTarget`，旧第 424–438 行），随后每 tick 直接赋速（`pullTargetToPlayer`，旧第 388–422 行），释放时清除 `GravityControl`（`releaseControlledTarget`，旧第 482–488 行）；筛选在 `findCrosshairTarget`（旧第 440–480 行）与 `createFallingBlockTarget`（`isMagnetic(state)`、无 BE、可破坏）；资源为 `movingTicks % 20 == 0` 时 `tryTimedOccupation(... MOVE_CP_COST=10 ...)`（旧第 372–377 行），无捕获费且不退款。据此范围收缩为“磁力筛选出的单格方块操纵”最小正确切片：只迁移这一条方块路径，不虚构多格结构捕获、旋转、静态化等旧实现不存在的语义；实体路径、悬浮（`MagneticFieldRuntime`）与全部筛选谓词保持不变。
- 捕获：`MagneticStructureService.begin` 复用旧入场谓词（`AbilityEffectPolicy.blockDestruction`、区块已载、`mayInteract`、`SkillTuning.allowBlockDestruction`、`MagneticTargets.isMagnetic`、无 BE、`getDestroySpeed ≥ 0`），随后确定性预算预裁 `cropToBudget`（NATIVE 128 / SABLE 256，经 `StructureBackendRouter.status().mode()` 判定；seed 优先）并走 `StructureTransactions.capture/poll` 跨 tick；`status:detail` 走日志 + 新 actionbar 文案，失败保留源方块。扣费时机沿用旧语义：捕获/牵引本身不收费，CP 仍是移动期间每 20 tick 10 点，捕获失败不退款。
- 控制：提交后原生以捕获格 + 每轴 ≤6 余量、宿主高度/世界边框夹取生成 ≤24 的区域盒，`StructurePhysics.openRegion` + `attach`，`motion` 出现并复核 `MagneticStructureControl.massBudget(milestone)`（128/256/512）后 `acquireEntityControl` + `StructurePhysicsControl.Force`，每 tick `setControl/renew`（租约 20 tick）；`Force` 每物理步被消耗属预期，会话状态以租约判定。移动跟随用投影 `worldCollisionBoxes` 预测，距区域边缘 <2 格时 `region.rebind`（P25 同构）；无法重绑时释放控制、保留结构、安全结束（不让区域 FAILED）。超距、租约失效、结构不可用均与旧实体路径一致地结束并保留结构。
- 标定依据：新公共 `MagneticStructureControl.pullForce` 直接复用旧 `MagneticMovement.calculatePullVelocity`（2.3 格/tick 上限、0.65 停止距离、0.2/0.8 平滑），经 `LegacyMotionUnits.velocityPerTick/velocityPerSecond`（÷20/×20）换算，取一个 0.05 秒物理步 `F = m·(v目标 − v当前)/0.05`，全矢量夹取在共享 `StructurePhysicsControl.MAX_FORCE` 之下；实测 `mag_expected_speed=46.0`、`mag_pull_speed=45.58`（99.1% 标定值）。
- Sable 边界：捕获经路由（混装创建 Sable 结构）；提交后若 `reference.backend()==academy:sable`，不做未标定的牵引，明确结束并保留结构（日志 + `sable_pull_unavailable` actionbar），不建旧实体、不回退旧路径。释放契约：`stopTargetPull`、登录/换维/死亡/停服静默释放租约；结构、物理区域与惯性保留，结构不回方块。
- 验证：单测 2580 项 + Sable 11 项零失败/错误/跳过（`build/p28-tests-final2.log`）；`build -DisDev=true` 21 秒、`-DisDev=false` 36 秒通过（`build/p28-build-dev-final2.log`、`build/p28-build-release-final2.log`）；专服烟测 `run/native-structure-physics-smoke-server-p28-r6` PASSED、`exit_status=0`（903 tick），证据含 `mag_source_cells=1`、`mag_filter_stone_rejected=true`、`mag_capture_pending_observed=true`、`mag_capture_committed=true`、`mag_source_cleared=true`、`mag_cp_charged=true`、`mag_region_ready=true`、`mag_control_active=true`、`mag_expected_speed=46.0`、`mag_pull_speed=45.57584523660533`、`mag_region_never_failed=true`、`mag_pull_travel=16.46`、`mag_follow_rebinds=2`、`mag_control_cleared=true`、`mag_terminal_structure_active=true`、`mag_source_not_restored=true`、`mag_no_drops=true`、`mag_no_legacy_entity=true`；既有 `grasp_*`、`kinetic_*`、`hsjet_*`、`support_*` 证据无回归（`build/p28-world-smoke-r6.log`）。
- 夹具槽位回收：原生容器仅 4 个永久槽，P25/P27/P28 三个捕获相位链式运行会耗尽；`NativeStructurePhysicsSmokeCleanup` 在 KINETIC/HSJ/MAGNET 各自 VERIFY 证据落盘后清空捕获内容并 `unload` + `discardEmpty`（仅测试夹具，保留断言先于释放；非生产路径）。r6 日志含 3 次 `Released empty native structure slot`。
- 复现命令：`. ./build/native-physics-p16-env.ps1; .\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsMagnetManipulationSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-NEW @physicsNativeArgs -DisDev=true`（新隔离目录并预置 `eula.txt`/`server.properties`；不要复用 r1–r6）。
- 失败轨迹（目录与日志保留）：`p28-r1` 在 MAGNET 因 `BACKEND_UNAVAILABLE:no_free_native_slot`（4 槽被 a/b/kinetic/hsjet 占满）失败（`build/p28-world-smoke-r1.log`）；`p28-r2` 加入槽位回收后仍失败，因清理误按宿主世界坐标清空而捕获目标以最小角为局部偏移（`build/p28-world-smoke-r2.log`）；`p28-r3` 修正偏移后失败于 `region.control` 在 Post 时因一步 Force 已被消耗而读空（`build/p28-world-smoke-r3.log`）；`p28-r4` 改为租约/阶段断言后通过（`build/p28-world-smoke-r4.log`）；`p28-r5` 收紧速度门限后通过（`build/p28-world-smoke-r5.log`）；`p28-r6` 为最终修订（仅 actionbar 文案措辞变化）全量复跑（`build/p28-world-smoke-r6.log`）。
- 未验边界：Sable 的磁场牵引（捕获提交后保留结构；本批未跑 Sable 专服烟测，`sableCompatTest` 11 项零失败）；真实双端与真实 TCP/键盘输入；多格或连通磁力结构（本节点只迁移旧有的单格语义）；旧 `FallingBlockEntity` 的落地磨损/伤害/变回方块表现未逐一对照；每次使用最多新开一个物理区域（上限 3）且长期回收未实现（同 P25/P27）；`ServerElectromasterProgramRuntime` 的磁力行动节点仍走旧实体路径。
- 当前构建（含 P19–P28）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `091B56C01D7778A48B9DE3FE557FD6094024D71DE3F29D53B8110F4005775706`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `8BFDC79DD0BA0523C34EDC2C4ECB04C354D204CE4F6393796C35DDB1215A5063`。未复制到整合包，未操作用户存档。

## 精密操作节点阶段设计（P29 起）

本节把计划书“精密操作节点的数据流与版本迁移”与三类能力职责表（`docs/block_structure_physics_and_sable_compat_plan.zh-CN.md` 第 571–603 行）落成阶段、节点、value types、迁移与验收计划。节点未实现前不得提前发布到编辑器、模板或翻译；未完成前置必须以明确拒绝返回，不得虚假交付，也不得用旧实体或静默成功顶替。

### 阶段划分

| 阶段 | 范围 | 出口条件 |
| --- | --- | --- |
| P29（本批） | 公共契约落点与矢量结构服务抽取：`VectorStructureService` 承接动能投掷全部结构逻辑；不新增节点 | 抽取后行为逐值等价，单测、烟测与双构建通过 |
| P30 | 矢量操作节点族：创建/提取、获取控制、移动、旋转、发射、终止控制、安全静态化 | 每类“创建 → 移动 → 旋转 → 终止”与“发射/静态化失败”图，玩家/程序/非玩家入口同服务 |
| P31 | 电击使节点族：磁性提取、显式强制磁化、磁性牵引/旋转/发射；`ServerElectromasterProgramRuntime.magneticMove` 从 `FallingBlockEntity` + 直接赋速 + `damageAlongTrajectory` 旧路径迁移 | 磁性参与质量、作用点与真实接触结算；旧路径删除或显式拒绝 |
| P32 | 空力使节点族：喷口锚点、局部方向逐物理子步、力偶旋转、停喷保留线角速度；把现有 `action/launch_block_structure` 纳入统一节点族 | 喷口破坏/卸载重映射、共享资源预算、对称喷口抵消验收 |
| P33 | 收尾：安全静态化拒绝路径、图版本迁移器、编辑器 palette/模板/翻译/剪贴板、全矩阵图验收 | 旧图不因 `schemaVersion` 被拒；静态化失败保留内容与恢复证据 |

### 节点职责与三套 ID

节点 ID 沿用各能力 `program/<ability>/action/...` 命名空间与 `action/` 前缀；三类能力采用同一路径形状，便于公共 value types、编辑器和迁移器复用。

| 职责 | 必需输入 | 输出与规则（含拒绝） | 三类路径（accelerator/electromaster/aeromanip 同形） |
| --- | --- | --- | --- |
| 创建/提取 | flow、选区或宿主格、能力配置、预算 | structure、result、flow；完成内容事务后才发布结构引用；选区空/被占/超预算/权限不足显式拒绝并保留源方块 | `action/structure/extract` |
| 获取控制 | flow、structure/anchor、能力控制配置 | control、result、flow；权限/占用/质量/范围拒绝均有明确结果，不抢占继任控制 | `action/structure/control` |
| 移动 | flow、control、世界目标或显式跟随锚点、持续时长 | result、flow；矢量动量修正、磁性牵引、气动受力保持不同约束；越界/失联安全结束并保留结构 | `action/structure/move` |
| 旋转 | flow、control、目标姿态或角运动、作用点/喷口配置 | result、flow；能力无法形成所需力矩时拒绝或返回有限结果，不无条件赋姿态 | `action/structure/rotate` |
| 发射 | flow、control、方向及冲量/能力强度 | result、flow；交接为有界发射操作，不能提前对整条预设轨迹结算伤害 | `action/structure/launch` |
| 终止控制 | flow、control | result、flow；幂等撤销自己的后续命令，保留结构与运动，不触及继任控制 | `action/structure/release` |
| 安全静态化 | flow、structure、目标网格姿态、放置策略 | result、flow；与终止分开，失败保留内容和恢复证据，不先 discard 再补方块 | `action/structure/staticize` |

- 公共查询节点 `program/core/query/look_structure`、`program/core/query/structure_motion`（P18）语义不变；行动节点不得按“该玩家最近控制的结构”隐式寻址，必须经显式 structure/control 端口。
- 空力使现有 `action/launch_block_structure`（P27 已走 `HighSpeedJetStructureService`）在 P32 映射到统一族；旧 ID 通过迁移器保留端口语义，避免旧图失效。
- 电击使的强制磁化是显式、付费、有期限的覆盖效果，与控制令牌分别授权与计费；一格铁不得给予整船无限推力。
- 安全静态化独立于终止：终止保留结构、惯性与重力；静态化必须先验证完整目标 footprint、加载、权限、占用和可支持的方块状态旋转，全部接纳后才撤销来源。

### value types 计划

只新增 P30–P32 真正需要的最小集合，避免过度设计；其余继续使用既有世界点/格/实体/向量类型：

| 类型 | 用途 | 约束 |
| --- | --- | --- |
| `structure_control` | 绑定 actor、runtime 与 invocation 的运行期控制令牌 | 不持久化、不可保存后自动恢复；失效后不重获权限 |
| `structure_operation_result` | 结构操作结果（status/detail/reference） | 机器可读拒绝原因，程序分支不解析文本日志 |
| `structure_anchor` | 结构局部格/作用点（磁性点、喷口支撑点、旋转作用点） | 运行时解析到世界位置；结构卸载/拆分后失效不猜测 |
| `structure_anchor_set` | 多作用点有限力组（力偶/旋转） | 合成总力与 `Σ(r × F)`，节点数/锚点数/拆分碎片不得复制预算 |
| `structure_pose` | 目标姿态或角运动输入 | 未具备有限力矩控制前旋转节点显式拒绝 |

- `structure_reference` 已有；`ACTION_RESULT` 保留通用动作语义，结构操作结果单独成类型以携带 reference 与拒绝码。
- 编辑器 palette/options、模板、中英翻译、剪贴板和导入导出随类型发布同步更新；未实现类型不得提前出现在可选列表。

### 控制令牌

- 获取控制成功后签发，绑定 actor（玩家/非玩家资源提供方）、runtime 与 invocation；同一结构同一时刻只有一个独占令牌。
- 不写入存档、不跨会话恢复；持久保存的结构身份在运行时重新解析和授权，失效令牌不得重获权限。
- 终止幂等：重复终止是空操作；只撤销本令牌自己下发的后续命令与持续力，保留结构、区域与惯性；绝不夺取或影响继任者的控制。
- 令牌不是权限：reach、保护、资源仍由 actor context 逐次校验；使用公共 API 不产生无限预算。

### 版本迁移

- 三套能力图、节点 schema 与二进制 envelope 分别迁移；旧图保留原端口语义，由显式迁移器补齐新类型端口并保留原数据。
- 不得直接把 catalog `schemaVersion` 改成 2 导致当前严格版本检查拒绝旧图；迁移器必须能回读旧 action 节点并保留其端口。
- 控制令牌不可持久恢复；迁移后的图在运行时重新获取控制并再次授权。
- 编辑器元数据、翻译键、模板、剪贴板与 `*ProgramExecutionBridge` 的 stage/undo 补偿同步迁移，`ProgramActionTransaction` 补偿按计划书第 603 行区分未提交内容与已参与物理的效果。

### 验收计划与未实现前置

- 每类能力验收两张图：“创建 → 移动 → 旋转 → 终止”与“发射/安全静态化失败”；玩家、程序、非玩家入口必须复用同一能力服务，不新增旁路。
- 内容守恒、资源预算、伤害去重与停止控制后的自由运动按计划书“后续迁移清单与验收”第 6 项执行；真实接触才结算伤害，不提前对预设轨迹结算。
- **未实现前置，不得虚假交付**：有限力矩姿态控制（角速度反馈、最短四元数误差、有限力矩与角冲量预算）与安全静态化（完整 footprint/加载/权限/占用/方块状态旋转）尚未实现。P30 前 `action/structure/rotate` 与 `action/structure/staticize` 必须返回明确的 `unavailable` 结果，发射节点不得预结算整条轨迹，普通移动/旋转不得用 `detach → setPose → attach` 顶替。
- 安全静态化失败图：目标 footprint 被占、未加载、权限不足或不可放置时拒绝并保留唯一内容所有者；成功结束明确释放控制或移交有期限操作。

### 现状（P29 开始时）

- P29 抽取前 `KineticThrow` 的结构逻辑全部在技能类内（`cropToBudget`、`StructureTransactions.capture/poll`、区域挂载、Anchor 抬升、冲量发射、逐 tick 跟随、接触伤害/击退、释放清理）；P29 已抽到 `VectorStructureService`，抽取前后对照与验证见下节。
- 三套 `Server*ProgramRuntime` 尚无任何结构行动节点：只有公共查询节点 `query/look_structure`、`query/structure_motion`，以及空力使 `action/launch_block_structure`（P27 已走 `HighSpeedJetStructureService`）。
- `ServerElectromasterProgramRuntime.magneticMove` 仍走 `FallingBlockEntity` + 直接赋速 + `damageAlongTrajectory` 预设轨迹伤害旧路径（P31 迁移）。
- Sable 侧只有 P24 捕获与 P18 查询/牵引；Sable 的发射、飞行与静态化必须显式拒绝，不回退旧实体、不静默成功。

### P29 矢量结构服务抽取（2026-10-14）

- 改动/新增文件：
  - 新增 `src/main/java/org/academy/internal/common/ability/accelerator/movement/VectorStructureService.java`（967 行）：捕获选区/预裁/事务、区域挂载与 Anchor 抬升、Sable 牵引抬升、冲量发射、逐 tick 跟随 rebind、接触伤害/击退、释放清理，以及 `Stage`/`Diagnostic`/`CapturePlan`/`Session`。
  - 重写 `src/main/java/org/academy/internal/common/ability/accelerator/skills/lv3/KineticThrow.java`（1268 → 428 行）：保留 HUD/按键/网络包/蓄力/资源执行/入口/生命周期清理；`releaseCharge` 的选区改为服务 `planCapture`，扣费仍在 `executeActive` 内，随后 `beginCapture` 启动事务；`throwHeld`/`stopHeld`/`diagnostic` 委托服务（Diagnostic 使用服务类型）。技能几何、每格伤害与投掷方向公式保留为公开静态配置，与 P27 服务读取 `HighSpeedJet.*` 同构。
  - 新增 `src/test/java/org/academy/internal/common/ability/accelerator/movement/VectorStructureServiceTest.java`：从 `KineticThrowTest` 原样迁移 `cropToBudget`/`regionBounds`/`followBounds`/`edgeDistance` 四个用例，断言与期望值一字未改。
  - `KineticThrowTest.java`：保留蓄力几何（`chargeTier`+半径表）、每格伤害、投掷方向三个用例；删去的四个用例在服务测试中等价存在，测试总数不变。
  - `NativeStructurePhysicsKineticThrowSmokeChecks.java`：仅把 `KineticThrow.Server.Stage.CAPTURING` 引用改为 `VectorStructureService.Stage.CAPTURING`（与 P27/P28 烟测引用各自服务 `Stage` 一致），所有证据与断言未改。
- 抽取前后对照：
  - 技能内 `CHARGES`/`CAPTURES`/`HELD_STRUCTURES` 三表：`CHARGES`（蓄力输入）留在技能；`CAPTURES`/`HELD_STRUCTURES` 原样变为服务 `CAPTURES`/`SESSIONS`，键仍为玩家 UUID，pending 事务与已持结构并存的既有语义保留。
  - `captureAndLift` 的选区、前方半空间裁剪、上方安全裁剪与确定性预算预裁成为服务 `planCapture`（只读，拒绝日志与 actionbar 文案不变）；`StructureTransactions.capture/poll` 与跨 tick `CaptureState` 成为服务 `beginCapture`/`tickCapture`。
  - `HeldStructure` → 服务 `Session`：stage/anchor 控制/Sable 牵引/expiry/impact latch/不可用宽限逐字段迁移；`releaseHeld` → `terminate`（幂等关闭 lease 与牵引，保留结构与物理区域及惯性）。
  - `followRegion`/`tryRebind`/`applyImpacts`/`settleImpact`、`projection`/`union`、`lookedAtStructure`/`aimedAtHeld`/`viewerInsideStructure`、`playKineticCue`/`playImpactCue`、`capturePolicy`/`capturePermission`/`cellBudget`/`sableMode`，以及 `cropToBudget`/`regionBounds`/`edgeDistance`/`followBounds` 与私有几何工具全部逐行搬迁。
- 服务入口（本批只留入口，不接节点）：`planCapture`/`beginCapture`/`session`/`tick`/`launch`/`terminate`/`clear`/`clearAll`；`Session` 暴露 stage/reference/blockCount/lifted/controlActive/flying/sable/impacts，供 P30 程序节点与查询复用；`CapturePlan` 携带 seed/tier/positions。
- 行为等价：专服烟测 `run/native-structure-physics-smoke-server-p29-r1` PASSED、`exit_status=0`（903 tick）；结果文件 258 行中 `kinetic_*`、`hsjet_*`、`mag_*`、`support_*`、`grasp_*`、`terrain_*` 等全部证据与 P28 r6 逐行一致，仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID 不同（`kinetic_lift_rise=3.2498730230960433`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=45.0`、`kinetic_follow_rebinds=10` 等未变）。
- 验证：单测 2580 项 + Sable 11 项零失败/错误/跳过（`build/p29-tests-r1.log`）；`build -DisDev=true` 22 秒、`-DisDev=false` 35 秒通过（`build/p29-build-dev.log`、`build/p29-build-release.log`）；编译日志 `build/p29-compile.log`；烟测日志 `build/p29-world-smoke-r1.log`。
- 失败轨迹：本轮烟测首轮（也是唯一一轮）即通过，无失败目录需要保留；服务初稿中 `edgeDistance` 曾有一处转录笔误，在首次编译前人工复核修正，未进入任何构建、测试或世界运行。
- 未验边界：Sable 的矢量发射与飞行仍未实现（捕获提交后保留结构，同 P25）；悬停 10 秒/飞行 30 秒超时只释放控制；每次使用最多新开一个物理区域（上限 3）且长期回收未实现；真实双端与真实 TCP/键盘输入未验；程序节点、非玩家施术者、旋转与安全静态化不在本批。
- 当前构建（含 P19–P29）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `E29EB6C03BC1448714C7876E805CB23B0EEE88F919E2190C1299D465610BC774`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `D529315355B5725E2A1F24437D309619196B6049252662FA96A0DCFBFE4B7DF3`。未复制到整合包，未操作用户存档。

<!-- P24 第一步历史记录（保留供核对）
- 新增 `CaptureTarget`（transaction 包内接口）：`allocate/cellBudget/ready/writeCell/readCell/clearCell/quarantined/deactivate/save/releaseEmpty/resolve(recovery)/verifyCopy/backendId`；新增 `NativeCaptureTarget` 独占全部 `NativeStructureRuntime`/`StructureHandle`/几何预算调用。`StructureCaptureCoordinator` 不再直接接触任何后端类型，恢复路径经 `targetFor(server, record.backend())` 按日志 `Record.backend` 分派（目前仅原生，非原生仍保留记录并报错）。公共 API、状态/明细/日志文本、journal 阶段与保留/回滚语义均未改变。
- 验证（行为等价）：单元 2558 + Sable 11 零失败（`build/p24a-tests-final.log`）；事务 gametest `run/native-structure-transaction-gametest-r9` 通过（完整提交、拒绝、回滚/前向恢复、容量回收，`All 1 required tests passed`）；`build -DisDev=true/-DisDev=false` 均通过。
-->

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

### P30 矢量操作节点族（2026-10-14）

- 改动/新增文件：
  - 新增 api value types：`ProgramStructureControl.java`（绑定 actor、结构代次、服务 controlId 与运行实例令牌；不持久、失效不重获）、`ProgramStructureOperationResult.java`（status/detail/reference）、`ProgramStructureAnchor.java`（结构代次 + 局部格）、`ProgramStructurePose.java`（轴 + 弧度目标姿态）；`ProgramValueTypes` 增加 `structure_control`、`structure_operation_result`、`structure_anchor`、`structure_pose`。未新增 `structure_anchor_set`（本批无节点端口需要）。
  - `AcceleratorProgramNodeIds.java` / `AcceleratorProgramNodeCatalog.java`：新增 `program/accelerator/action/structure/{extract,control,move,rotate,launch,release,staticize}` 七个节点。extract 配置 `tier`(0–2)/`budget`(0=后端默认)，move 配置 `duration_seconds`(0.1–10)，launch 复用 `strength`；extract 输出 `structure`/`result`，control 输出 `control`/`result`，其余输出 `result` + flow；rotate 可选 `pose`、staticize 可选 `target`，节点本身明确 unavailable。
  - `AcceleratorProgramRuntime.java`：新增 7 个结构方法与嵌套 `StructureOperation(structure/control/result)`；结构效果直接落入长期会话，不进入动作事务补偿（与设计“已参与物理的效果不按未提交内容回滚”一致）。
  - `ServerAcceleratorProgramRuntime.java`：extract/control 同步调用 `VectorStructureService` 程序入口，只有内容事务提交后才返回结构引用；move/launch/release 走服务程序入口并逐次计费（extract 30/control 10/move 5/launch 15 CP × 倍率）；rotate/staticize 不校验技能开关、直接返回 `unavailable`，不触碰控制。控制令牌绑定 player UUID、结构 runtimeId、服务 controllerId 与本次 runtime 实例（`runtimeToken`，不落盘），跨调用/跨程序失效即拒绝。
  - `AcceleratorProgramExecutionBridge.java`：装配 7 个执行器与可选输入（target/anchor/seconds/pose）和结果端口；`compileFor` 增加已学习的 `kinetic_throw` 能力。
  - `AcceleratorProgramCapabilities.java`：新增 `STRUCTURE = academy:kinetic_throw`，7 个节点统一使用。
  - `AcceleratorProgramDefinition.java`：新节点默认配置（tier 1/budget 0、duration 1.0s、strength standard）与编辑器条目；rotate/staticize 可见但描述明确 unavailable，未进入任何模板（`ProgramStarterTemplates` 只使用入口/输出/文本三个节点）。
  - `VectorStructureService.java`（改动物理路径）：`planCapture`/`beginCapture` 拆出无副作用 `planCaptureInternal`/`startCapture` 并加 `report` 标志（程序路径不发送技能 actionbar，日志与拒绝语义不变）；新增程序入口 `programExtract`（显式 `COMPLETED/PENDING/REJECTED`，PENDING 不发布引用、失败保留源方块）、`programControl`（复用同结构会话，或接管已有活跃物理区域并签发独占会话；他人控制/捕获中的结构拒绝；替代旧会话时保留旧结构、区域与惯性；Sable 明确 unavailable）、`programMove`（有界锚点修正 + 时长上限 600 tick，越界/失联安全结束并保留结构，program move 时 `tickLifting` 复用 `followRegion` 重绑区域）、`programLaunch`（显式方向有界冲量，`launchNative` 增加方向与强度倍率参数，技能路径仍传视角方向与 1.0，逐值不变）、`programRelease`（幂等、仅匹配本令牌时终止，绝不触碰继任会话）；`Session.controllerId()` 访问器与 `programMove` 标志。
  - 翻译：en_us/zh_cn 各新增 7 个节点名与说明、5 个端口键（anchor/control/host/pose/seconds）与 3 个配置字段端口键（tier/budget/duration_seconds），均按自然排序插入。
  - 测试：新增 `AcceleratorProgramStructureNodesTest`（4 项）；`AcceleratorProgramExecutionBridgeTest.FakeRuntime` 补齐新接口桩。
- 两张图验收（真实性级别：目录、schema、编译器、程序 VM、`AcceleratorProgramExecutionBridge`、节点执行器与动作计数为生产实现；accelerator runtime 为脚本化接口实现，不落地真实方块捕获/物理，真实世界程序图执行列为未验边界）：
  - 图一“创建→获取控制→移动→旋转→终止”：`extract(host,1,0)` → `control(structure)` → `move(control,target=12.5,70,10.5,1.0s)` → `rotate` 返回 `unavailable` 且同一令牌随后仍可终止（控制未被破坏）→ `release` 两次（第一次 `released`、第二次 `noop`，结构保留）。VM `COMPLETED`，调用顺序与参数逐字断言。
  - 图二“发射/静态化失败”：`extract(host,2,64)` → `control` → `launch(direction=+Y,MAXIMUM)` 记录有界冲量 75 且无任何轨迹伤害结算 → `staticize` 返回 `unavailable` 且唯一内容所有者保留。
  - 另加 PENDING 提取图：extract 返回 PENDING 时不发布 `structure` 输出，程序仍完成（机器可读 result 为 PENDING）。
- 回归验证：
  - 单测 2584 项 + Sable 11 项零失败/错误/跳过（`build/p30-tests-r2.log`、`build/p30-sable-r2.log`；基线 2580，本轮 +4）。
  - `build -DisDev=true` 22 秒、`-DisDev=false` 35 秒通过（`build/p30-build-dev-final.log`、`build/p30-build-release-final.log`）。
  - 因改动 `VectorStructureService`，专服烟测新隔离目录 `run/native-structure-physics-smoke-server-p30-r2` PASSED、`exit_status=0`（903 tick，`build/p30-world-smoke-r2.log`）；结果文件 258 行与 P29 r1 逐行一致，仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID 不同；关键证据 `kinetic_capture_pending_observed=true`、`kinetic_lift_rise=3.2498730230960433`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=45.0`、`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_pull_speed=45.57584523660533`，grasp/support/terrain 全部逐值无回归。p30-r1 同配置先行通过，r2 为最终修订（仅 import 整理）复跑。
- 失败轨迹：本轮首轮 `compileJava` 即失败（`StructureCaptureResult` 漏导入、桥接 lambda 需显式参数类型才能推断配置泛型），未进入任何运行；首轮全量单测 2 失败：`ProgramEditorDocumentTest` 缺 `port.tier/budget/duration_seconds` 翻译、`SkillProficiencyCoverageTest` 语言键未按自然序（节点键插入位置），修复后通过。世界烟测 p30-r1/r2 无失败目录保留。
- 未实现前置（仍 unavailable）：`action/structure/rotate` 缺有限力矩姿态控制（角速度反馈、最短四元数误差、有界力矩与角冲量），`action/structure/staticize` 缺安全静态化（完整 footprint/加载/权限/占用/可旋转方块状态）；两者均返回明确 `unavailable`，不无条件赋姿态、不用 `detach → setPose → attach` 顶替、不先 discard 再补方块，失败保留内容与唯一所有者。
- 未验边界：程序真实世界图验收（原生 extract 跨 tick 捕获，单次程序执行无法在提交前取得引用，故“创建→移动”同图只做脚本运行时验证；真实世界仅验证服务与技能回归）；非玩家施术者入口；真实双端与真实键盘输入；Sable 的程序控制/移动/发射（显式拒绝，未跑专服）；`programControl` 只接管已有活跃物理区域或既有会话的结构，不会为无区域结构新开区域；每玩家同时只有一个程序会话，程序移动上限 600 tick，物理区域长期回收同 P25–P29。
- 当前构建（含 P19–P30）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `E14EBC9E39220D2D98E40C168D5FB0BEE6E84B6A9683293AD6765FECCD0DE88D`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `E616D3E42D950B6CB651A161F4F876048E2114D7A2A2399F5784C647F593AC27`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 修复：捕获白名单扩展与随机刻语义放开（2026-10-14）

- 根因（真实客户端用户日志已定位）：`NativeCaptureWhitelist.rejectionDetail` 以 `state.isRandomlyTicking()` 一刀切拒绝，并只收显式列出的方块；真实地形的草方块落入捕获选区后，`StructureCaptureCoordinator` 预检对任一格失败即整笔 fail-closed，动能投掷/H 键因此报 `REJECTED:random_tick_not_supported`。既有烟测全绿是因为夹具只用石/台阶/楼梯，且旧 `StructureTransactionGameTests` 把该拒绝写成了期望断言。
- 政策变更：`NativeCaptureWhitelist` 不再按随机刻语义拒绝，改为“精选集合即安全边界”。草/菌丝等会随机刻的表面全块进入精选集后即可捕获；捕获后的结构空间不跑随机刻，内容保持惰性。保留 scheduled tick 待处理门（格子正处于行为中，复制后可能立刻变化）；保留 BE、waterlogged、流体、非全块、不可破坏、重力方块门。
- 扩集清单（刻意数据变更）：`GRASS_BLOCK`、`MYCELIUM`、`MOSS_BLOCK`、`SNOW_BLOCK`、`CLAY`、`GRAVEL`。其中 `GRAVEL` 是重力方块，加入后仍被既有重力门以更精确的 `gravity_block_not_supported` 拒绝；农田/土径与非全块植被仍不在集合内。
- 植被处理：审计 `VectorStructureService.planCaptureInternal` 后确认非全块植被会进入捕获集（`capturePolicy` 只要求非空气且可破坏）。新增确定性选区裁剪 `BlockStructureApi.cropNonFullBlocks`（纯函数 `BlockStructureManager.cropNonFullBlocks`，按 `isCollisionShapeFullBlock` 排除草簇/花），在 `cropImmediatelyBlocked` 之后应用；这是选择策略而非静默丢内容，交易预检仍对漏网非全块 fail-closed；未加载/越界格保留以便仍显式报错，空气本来就不在选区内。seed 被裁掉时仍拒绝整笔（`selection_blocked_or_seed_missing`），不做静默重锚。
- 改动文件：`NativeCaptureWhitelist.java`（政策注释、扩集、移除随机刻门、精选集放入惰性 holder 并把准入判定抽为纯函数）、`BlockStructureManager.java`、`BlockStructureApi.java`、`VectorStructureService.java`、`StructureTransactionGameTests.java`、`BlockStructureSelectionTest.java`（+2）、新增 `NativeCaptureWhitelistTest.java`（3 项：精选全块准入、各结构门与拒绝原因、scheduled 门优先级）。
- 验证：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：单测 2589 项 + Sable 11 项零失败/错误/跳过（基线 2584，本轮 +5；`build/fixwl-tests.log`）。
  - 事务 gametest 新隔离目录 `run/native-structure-transaction-gametest-fixwl-r1`：`All 1 required tests passed`（`build/fixwl-gametest-r1.log`）。夹具改动：旧的 `GRASS_BLOCK → random_tick_not_supported` 期望替换为 `FARMLAND`/`SHORT_GRASS → not_in_plain_block_whitelist`，新增水方块 `fluid_not_supported`，并新增“随机刻草地+泥土提交成功”用例（断言 `isRandomlyTicking` 为真、目标内容与源一致、源被移除、日志回收、释放槽位不影响后续容量断言）。
  - 世界烟测新隔离目录 `run/native-structure-physics-smoke-server-fixwl-r1` PASSED、`exit_status=0`（903 tick，`build/fixwl-world-smoke-r1.log`）；结果文件 258 行与 P30 r2 逐行一致，仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID 不同；关键证据 `kinetic_capture_pending_observed=true`、`kinetic_lift_rise=3.2498730230960433`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=45.0`、`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_pull_speed=45.57584523660533`、`support_rider_standing=true`，grasp/support/terrain 全部逐值无回归。
  - `build -DisDev=true` 25 秒、`-DisDev=false` 39 秒通过（`build/fixwl-build-dev.log`、`build/fixwl-build-release.log`）。
- 失败轨迹：首轮定向单测失败——`NativeCaptureWhitelist` 及 `Blocks` 的类初始化在纯 JUnit 下需要游戏 bootstrap（先 `Not bootstrapped`，改用 `Bootstrap.bootStrap()` 后因无 FML Loader 再失败）。修复方式为把准入判定抽成不触碰注册表的纯函数、精选集改为惰性 holder，并使夹具改用真实方块断言（`isRandomlyTicking`、实际提交）。失败轮输出被同名 Tee 日志覆盖，未单独留存；实现、gametest 与烟测最终均一次通过，无失败运行目录需要保留。
- 未验边界：真实客户端需用户复测（本轮只有用户日志定位，未启动真实客户端）；瞄准点本身是非全块植被时 seed 被裁剪，整笔以 `selection_blocked_or_seed_missing` 拒绝（有意不静默重锚）；非全块植被不被捕获，结构抬升时留在原地；精选集外的矿石/沙（重力）/其他方块仍 fail-closed；`not_full_block` 门当前无可达精选方块，仅由纯函数单测覆盖；随机刻仅在“结构空间不跑随机刻”的设计前提下可接受。
- 复现命令（每次使用全新 `fixwl-*` 隔离目录，不覆盖成功/失败产物）：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`
  - `.\gradlew.bat runGameTestServer -I validation/native_structure/transaction.init.gradle -PacademyGameTests=academy:structure_transactions -PacademyStructureTransactionDirectory=run/native-structure-transaction-gametest-fixwl-r1 -DisDev=true`
  - `.\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsMagnetManipulationSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-fixwl-r1 @physicsNativeArgs -DisDev=true`（目录需预置 `eula.txt`、`server-ip=127.0.0.1`、`level-name=native-structure-physics-smoke` 的 `server.properties`，且世界目录尚不存在）
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `A9DABD667B2359BB43E45C60029B4825872CBAABC2AA758825249A9EBDB8C4C1`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `B4F8A121B2085853F36B0DA5194316AA4852D017B19FA1256D836C27B4D04456`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 修复：Sable 容器重入崩溃与投掷/悬浮/内容扩展（2026-10-14）

- 崩溃机制判定（证据等级：字节码级，可复核）：
  - 崩溃报告 `crash-2026-10-14_19.23.25-server.txt`：`ArrayIndexOutOfBoundsException: Index 10 out of bounds for length 10` 栈顶 `ReferenceArrayList.forEach:726` ← `SubLevelContainer.tick:144`。`javap -p -c -l` 复核该 Sable 制品（SHA-256 与 `docs/sable_compat_artifacts.json` 锁定值一致）：`tick()` 第 144 行是 `allSubLevels.forEach(SubLevel::tick)`，第 147 行才是 `observers.forEach`。
  - fastutil 8.5.18 `ReferenceArrayList.forEach` 在循环开始时缓存底层数组引用、每次迭代读取实时 `size`；迭代期间任何 `add` 导致底层数组扩容后，旧数组仍被按下标读取，`size` 超过旧容量即抛该异常。
  - 增删来自 Sable 自身：`ServerSubLevel.tick()` → `SubLevelHeatMapManager.tick/step`（热图分裂）→ `split()` → `SubLevelAssemblyHelper.assembleBlocks` → `SubLevelContainer.allocateNewSubLevel`（`allSubLevels.add`，`javap` 可见该调用链；`SableCommonEvents.handleBlockChange` 在真实 `chunk.setBlockState` 路径上以 `onSolidAdded` 播种热图，我们的捕获写入与玩家编辑都会进入）。崩溃发生在 `ServerLevel.tick` 的 HEAD（`sable$tickPlotContainer` 注入点），实体 tick 在其之后，故“实体 tick 重入”假设被排除；用户设置的仓库集成侧风险仍是惰性 `addObserver` 与捕获提交/回滚可在迭代中改容器。
  - 16 秒前的 `sable_target_not_visible` 与"崩溃前最后一次捕获"同源，但不是本次崩溃的增删来源；证据强度：崩溃机制字节码级确证，具体触发方块变化无法从日志逐格复原。
- 修复设计：
  - 版本门控兼容补丁：新增 `SableContainerMixinGate`（只校验 Sable 版本与 SHA-256）与 `MixinSableSubLevelContainerTick`（`@Pseudo`，仅对锁定制品应用，`require = 1`）。`tick()` 两个 `List.forEach` 改为快照迭代——迭代中新增的子级从下一 tick 开始 tick，删除仍由 `processSubLevelRemovals` 负责；每个回调包在 `SableContainerGuard.enter/exitContainerTick` 的 try/finally 内。不同 Sable 制品自动不应用而不是启动失败。
  - 延迟队列与守卫：新增 `SableContainerGuard`（无任何可选 mod 类型引用）。容器回调内的 `addObserver`/`removeSubLevel` 一律入队，`ServerTickEvent.Post`（所有等级 tick 完成后）统一 drain，单项失败只记日志不阻断后续；`allocateNewSubLevel` 是同步契约无法延迟，检测到重入时 fail-closed 返回空并记录错误，交易以 `no_free_native_slot` 干净失败、不产生半成品。守卫带不平衡 exit / tick 内 drain 断言与 `enteredCallbacks()` 校验计数。
  - 观察者急切注册：`StructureBackendRouter` 在 `LevelEvent.Load`（每个 `ServerLevel`）与 `ServerStartedEvent` 调 `Bridge.observeLevel`；`SableStructureAccess.observe` 立即注册。`state()` 惰性兜底保留，但注册走同一守卫通道。
  - 改动协作：`SableStructureTransactions.allocate/releaseEmpty/removeQuietly`、`SableStructureAccess.state` 接入守卫/队列；`SableStructureSmoke` 断言 `sable_container_tick_guard_active=true`（真实容器 tick 至少进入过一次被守卫的回调，证明 mixin 生效）。
- 左击投掷链审计与修复：
  - 真实失败点 1（Sable 会话必然秒退）：`beginSableLift` 创建 `Session` 时漏设 `expiresAt`（默认 0），下一个玩家 tick 的 `tick()` 即判定超时并终止——即使抬升成功，左击时也没有会话。修复：`beginSableLift` 设 `expiresAt = gameTime + HOVER_TICKS`。
  - 真实失败点 2（显式未实现）：`VectorStructureService.launch` 对 `held.traction != null` 直接记录"not implemented"并终止。修复：新增中性 `StructureBackendRouter.launch` + `Bridge.launch` 默认拒绝，Sable 桥实现 `SableStructureTraction.launch`：锁定制品物理管线上以 `addLinearAndAngularVelocity(目标速度−当前速度, −角速度)` 精确赋速（同原生 `setVelocity(ZERO)` + 质心冲量的语义），有独占会话占用时拒绝；随后会话进入 `FLYING`，`tickSableFlight` 只观察惯性（Sable 自管接触），到期释放控制保留结构。
  - 客户端链审计结论：`InputSystem` 的 `KEY_NAME_THROW` 绑定 `MOUSE_BUTTON_LEFT/PRESS`，`MixinMouseHandler` 仅在 `MouseButtonEvent` 被取消时 `ci.cancel()`，本技能从不取消；因此按住悬浮时左击同时保留原版攻击行为，非持有时左击只是发一个被服务器拒绝的包，不吞攻击、无客户端状态门。瞄准门保留（必须瞄住本结构或身处其中），与旧实现一致。
- 悬浮 10 秒语义：抬升完成（`lifted` 置真）时 `expiresAt = hoverDeadline(gameTime) = gameTime + HOVER_TICKS`（10 秒完整悬浮窗口，不再与抬升时间共享预算；新增 `VectorStructureService.hoverDeadline` 单测）。超时行为（旧实现考古：`git show 6751ce92:KineticThrow.java` 的 `holdAt` 到期 `releaseHeld` 只关句柄、结构回落到重力）选择为：仍瞄准本结构时沿视角自动投掷（同一 `launchHeld` 通道），否则安全结束并记录日志，结构与惯性保留——不走静默掉落，符合任务允许的两种语义之一。
- 内容扩展（`NativeCaptureWhitelist`）：
  - 新增 `SAND`、`RED_SAND`（`GRAVEL` 原已在集合）与 11 种树叶：oak/spruce/birch/jungle/acacia/dark_oak/mangrove/cherry/pale_oak/azalea/flowering_azalea。
  - `FallingBlock` 独立门删除，白名单即边界：`classifyAdmission` 移除 gravity 参数；沙/红沙/砂砾作为已列重力方块直接准入，未列重力方块（如 gametest 用的铁砧）以 `not_in_plain_block_whitelist` 拒绝。BE、流体、waterlogged、scheduled tick、非全块、不可破坏门保持。
  - 原生碰撞几何补齐树叶：gametest 首轮暴露 `NativeStructurePhysicsGeometry` 拒绝 `oak_leaves`（`target_geometry_budget` 回滚）；`STATE_SHAPES` 增加 `LeavesBlock`/`TintedParticleLeavesBlock`/`UntintedParticleLeavesBlock`/`MangroveLeavesBlock`（四类精确类，花瓣粒子子类全覆盖；树叶碰撞形状只依赖方块状态，不含 BE/tick/实体条件）。
- 验证结果：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：普通单测 2592 项（基线 2589，本轮 +3：白名单重力语义、投掷速度标定、悬浮窗口）+ Sable 16 项（基线 11，+5：新增 `SableContainerGuardTest`）零失败/错误/跳过（`build/fixp31-tests-final.log`）。
  - 事务 gametest 新隔离目录 `run/native-structure-transaction-gametest-fixp31-r2`：`All 1 required tests passed`（`build/fixp31-gametest-r2.log`）。新增沙/红沙/砂砾/橡树叶/杜鹃树叶提交用例（目标内容与源一致、源移除、journal 回收、释放槽位）与铁砧 `not_in_plain_block_whitelist` 用例。
  - 世界烟测新隔离目录 `run/native-structure-physics-smoke-server-fixp31-r1` PASSED、`exit_status=0`（904 tick，`build/fixp31-world-smoke-r1.log`）；结果文件与 `fixwl-r1` 逐行 diff 仅 `first_ref`/`second_ref`/`shared_session` 随机 UUID 与 `ticks=903→904`，`kinetic_lift_rise=3.2498730230960433`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=45.0`、`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_pull_speed=45.57584523660533` 全部逐值无回归。
  - Sable 专服烟测新隔离目录 `run/sable-compat/fixp31-server-r1`（`-PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true`，目录按 P27 约定预置）PASSED、`exit_status=0`（`build/fixp31-sable-server-r1.log`），证据含新断言 `sable_container_tick_guard_active=true` 与既有 `sable_capture_committed=true`、`sable_capture_rejected_be_preserved=true`、`pneumatic_real_server_entry_selects_sable=true` 等全部保持。
  - `build -DisDev=true` 25 秒、`-DisDev=false` 39 秒通过（`build/fixp31-build-dev.log`、`build/fixp31-build-release.log`），`verifySableCompatPackaging`（桥接类存在、无嵌套外部 Sable 依赖）通过。
- 失败轨迹：gametest `fixp31-r1` 在新增树叶用例失败（`Structure capture target geometry was rejected ... Unsupported collision state ... oak_leaves`，`build/fixp31-gametest-r1.log`），按上述几何补齐后 `fixp31-r2` 通过；中途一次 `compileJava` 失败（`SableStructureTraction.launch` 误用不存在的本地 `requireThread`）与一次 mixin 文件格式手误均在提交任何运行前修复；无其他失败运行目录。
- 未验边界：真实客户端需用户复测（步骤见下）；Sable 热图分裂本身未在烟测中直接复现（mixin 只以 `sable_container_tick_guard_active` 证明真实容器 tick 被守卫，快照迭代的正确性由字节码判定与单测覆盖）；Sable 端 Kinetic Throw 的投掷速度/悬浮未跑专用夹具（现有 Sable 烟测不执行动能投掷，Sable 发射路径仅由编译、守卫单测与本体烟测无回归保护）；Sable 质量预算（`sable_traction_too_heavy`）与 `sable_target_not_visible` 的具体现场未再复现（诊断已细化为打印 `StructureHitValidation.Status`，便于用户复测定位）；悬浮 10 秒自动投掷未在自动化中触发；`docs/vfx/...` 未触碰。
- 用户复测步骤（真实客户端，Sable 后端）：
  1. 进入 Sable 存档，学习动能投掷并按住 H 蓄力，瞄准石质地表松开，观察结构抬升后**至少 10 秒**悬停且位置保持。
  2. 悬停期间正对结构左击：结构应沿视线以约 50 格/秒抛出；左击同时不应丢失原版攻击反馈。
  3. 不瞄准结构左击/空手左击：应有正常原版攻击，不出现异常。
  4. 悬停 10 秒内不操作：结构应沿当时视角自动抛出；若未瞄准则记录 `hover expired without a launchable aim` 并释放控制（结构保留，不凭空消失）。
  5. 捕获含沙/砂砾/树叶与新增白名单方块的区域：应提交成功；含 BE/流体/水logged/不可破坏/未列方块时应有明确 `capture_rejected` 且源方块不变。
  6. 目标为过重结构（`sable_traction_too_heavy`）或不可见（`sable_target_not_visible`）时，日志应给出精确状态，便于回传。
- 复现命令（每次全新隔离目录）：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`
  - `.\gradlew.bat runGameTestServer -I validation/native_structure/transaction.init.gradle -PacademyGameTests=academy:structure_transactions -PacademyStructureTransactionDirectory=run/native-structure-transaction-gametest-fixp31-r2 -DisDev=true`
  - `.\gradlew.bat runServerCompat -I validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsGraspSmoke=true -PacademyStructurePhysicsKineticThrowSmoke=true -PacademyStructurePhysicsHighSpeedJetSmoke=true -PacademyStructurePhysicsMagnetManipulationSmoke=true -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-fixp31-r1 @physicsNativeArgs -DisDev=true`
  - `.\gradlew.bat runServerSable -PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true -PacademySableRunDirectory=run/sable-compat/fixp31-server-r1 -DisDev=true`
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `5798E3C77B99E8472BA0F37891838A173D4B826FECEC0D8E6996931D1A5B817F`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `89207934F8DC9DD198A940E9A7D625CF92C2315057787F048C10302454C73A29`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 修复：原地向上生成与 H 键投掷任意结构（2026-10-14）

- 问题 1 根因与生成策略：
  - 用户反馈“对准地面捕获得出的物理结构陷入地下、生成位置错位”。现状链审计：`VectorStructureService.beginLift`（第 703 行起）在捕获提交后直接以原方块位姿 `handle.pose()` 作为初始物理位姿 `openRegion/attach`；物理体在 `NativeStructurePhysics.Region.replaceWorld` 创建，而区域转 READY 的那一 tick 会先执行一整步**无控制**的 `world.step(0.05, 3)`（`Region.tick` → `stepWithControls` 无 control 分支），抬升锚点要到 `tickAwaitingPhysics` 才安装。旧实现（P25 前，`git show a58fd123:src/main/java/org/academy/internal/common/world/entity/structure/BlockStructureEntity.java` 的 `collisionGeometry.collideWithWorld` 扫掠 + `BlockStructureKinetics.holdAt` 速度 PD）只截断移动、不做穿透推挤，结构即使嵌在地面炮眼里也会沿请求方向被扫掠向上拉起；新 Rapier 冲量求解器从“原方块完全填充炮眼”的零间隙姿态起步且首步不受控，接触/穿透修正可挤动、下沉或错位。
  - 生成策略（计划书允许的“初始生成”例外，已在代码注释注明，不是 `detach → setPose → attach` 普通移动）：捕获提交后、物理 attach 前，把结构位姿整体上移 `INITIAL_GROUND_CLEARANCE = 0.25`（`VectorStructureService.java:90`、第 719–733 行），区域盒用带 clearance 的 `required` 计算（重载 `regionBounds`，`VectorStructureService.java:1311`），打开区域/挂接失败的路径调用 `restoreInitialPose` 回滚原位姿。水平位置不变；抬升目标仍是“当前质心 + liftHeight”，即仍在原位向上抬。一步自由落体上限约 1.23 cm（0.05 s），远小于 25 cm 清空，首个不受控物理步不可能把结构底压到原选区底以下。
  - 负向实验与诚实边界：把 clearance 临时置 0 复跑同一嵌入地面夹具（`run/native-structure-physics-smoke-server-fixp32-r7`、`r8`，日志 `build/fixp32-world-smoke-r7-noclearance.log`、`r8-noclearance.log` 保留），首个物理 tick 的 `kinetic_first_physics_min_y` 仍为 112.048/112.05（≥ 原选区底 112.0），**未能在合成专服夹具复现用户所述的下沉量**；但同夹具出现了可测差异（抬升高度 3.480 vs 3.483、飞行轨迹偏低导致僵尸接触未命中）。因此本修复以构造性保证（首个不受控步不可能低于原选区底）回应真实客户端风险，最终由用户复测确认。
- 问题 2 H 键投掷任意结构：
  - 客户端：删除左键 `KEY_NAME_THROW` 绑定与 `throwLookedAtStructure`；H 按下改发新包 `kinetic_throw_press`（`PressPacket`），H 松开仍发 `ReleasePacket`；旧配置里的 `_throw` 行不再被读取（无副作用，配置兼容）。
  - 服务端统一决策：`KineticThrow.Server.pressHeld`（`KineticThrow.java:243`）→ `VectorStructureService.press`（`VectorStructureService.java:426`）。① 自己 LIFTING 会话且瞄住/身处其中 → 走上一批的 `launchHeld`（含 10 秒悬浮与超时沿视角自动投掷语义）；② 否则跨后端 `StructureQueries.raycast/validate`，命中→`pressRoute(true, mayInteract, isPhysical)`；未命中→`CHARGING`，由 `pressHeld` 调原 `startCharge`（长按蓄力→松开捕获原样）；③ 命中且允许→`directThrow`（`VectorStructureService.java:465`）：原生 `StructurePhysics.activeRegion` + `region.setVelocity(ref, dir×speed, ZERO)` 直接置速（质量预算、有限力、牵引租约质量门全部不参与；他人活跃租约仍拒绝 `structure_controlled`，不抢控），Sable 走 `StructureBackendRouter.launch` 直接置速入口；随后建立 FLYING 会话（原生继续区域跟随与接触结算，Sable 只观察惯性），不要求结构属于玩家；④ 拒绝码 `permission_denied`/`structure_not_physical`/`motion_unavailable`/`structure_controlled`/`sable_launch_rejected`/`ray_*`，日志 + actionbar 新文案 `message.academy.kinetic_throw.throw_rejected`，不静默、不回退蓄力。`Server.throwHeld` 保持程序化/烟测入口不变。
  - 速度标定：新增纯函数 `directThrowSpeed(mass, milestone)` 显式忽略质量并返回 `KineticThrow.throwSpeed`（50 m/s；三级熟练 60），保证重结构投掷速度与标定一致。
  - 改动文件：`VectorStructureService.java`、`KineticThrow.java`、`PacketTypes.java`、中英 `lang`（技能描述改为“按住 H 蓄力；瞄准任意物理化结构按 H 投掷”、新增 `throw_rejected`）、`VectorStructureServiceTest.java`、`NativeStructurePhysicsKineticThrowSmokeChecks.java`、`NativeStructurePhysicsSmokeCleanup.java`、`SableStructureSmoke.java`；`docs/vfx/...` 未触碰。
- 验证（最终修订）：
  - 单测 2596 项（基线 2592，+4：`pressRoute` 命中/未命中/无权限/非物理、`directThrowSpeed` 质量无关、`regionBounds` clearance 位移）+ Sable 16 项零失败/错误/跳过（`build/fixp32-tests-final.log`）。
  - 世界烟测新隔离目录 `run/native-structure-physics-smoke-server-fixp32-r9` PASSED、`exit_status=0`（908 tick，`build/fixp32-world-smoke-r9.log`）。夹具改为“3×3 源方块嵌在 5×5×6 真实地面中”——捕获透镜 51 格（`kinetic_block_count=51`，在原生物理 64 盒/刚体上限内）、质量 51。新证据：`kinetic_hpressed_miss_charging=true`（未命中→蓄力→原捕获流程）、`kinetic_capture_horizontal_aligned=true`（首个物理 tick 位姿原点 x/z 与捕获原点差 ≤1e-3）、`kinetic_first_physics_min_y=112.26426572712418` ≥ `kinetic_selection_min_y=112.0`、`kinetic_lift_min_y=112.36533004493464` ≥ 112.0、`kinetic_hpressed_structure_throw=true`、`kinetic_throw_mass=51.0`、`kinetic_throw_speed_mass_independent=true`、`kinetic_throw_speed=50.0`；未受夹具影响的既有证据逐值无回归：`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_pull_speed=45.57584523660533`、`support_*`/`grasp_*`/`terrain_*` 与 fixp31 全部一致；夹具缩放导致的既有证据取值变化仅 `kinetic_lift_rise` 3.2499→3.4831、`kinetic_impact_damage` 45.0→255.0（透镜 9→51 格）。结果文件与 fixp31 的逐行 diff 仅剩随机 UUID、`ticks=904→908` 与本修复的新证据与上述两项夹具缩放。
  - Sable 专服烟测新隔离目录 `run/sable-compat/fixp32-server-r1`（`-PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true`）PASSED、`exit_status=0`（`build/fixp32-sable-server-r1.log`）。捕获夹具改为 6×6×4=144 格石头（真实质量 288 > tier-0 预算 128），先断言常规 `StructureTraction.tryAcquire` 返回 `TOO_HEAVY`，再按 H：`sable_hpressed_heavy_throw=true`、`sable_heavy_mass=288.0`、`sable_traction_too_heavy_bypassed=true`、`sable_h_throw_speed=49.999999319218475`；既有 `sable_capture_committed`、`sable_capture_rejected_be_preserved`、`sable_container_tick_guard_active`、`pneumatic_*`、`npc_*`、`resource_callback_*` 全部保留（仅 `ticks=43→44`）。
  - `build -DisDev=true` 37 秒、`-DisDev=false` 55 秒通过（`build/fixp32-build-dev.log`、`build/fixp32-build-release.log`）。
- 失败轨迹（目录与日志保留）：`r2` 9×9×3 地面捕获透镜超出原生物理“每刚体最多 64 碰撞盒”上限，捕获以 `TARGET_WRITE_FAILED:target_geometry_budget` 干净回滚；`r3` 缩小夹具后区域与主烟测区域间隔不足（`region_open_failed: Physics region conflicts with live or saved reservations`），夹具外移到 `base+(20,4,0)`；`r4` 重结构命中后仍在 10 tick 冷却外合法二次结算，探针旧断言误报；`r5` 探针状态机把“持续低于初始血量”误判为重复结算，改为只对累计伤害值的新增做冷却断言；`r6`（5×5×3 浅夹具）首次全量通过；`r7`/`r8` 为把 clearance 临时置 0 的负向实验（见上，失败保留）。全部失败目录与 `build/fixp32-world-smoke-r*.log` 保留，r9 为最终修订全量复跑。
- 未验边界：真实客户端最终由用户复测（合成专服未能复现用户所述的下沉量，仅给出构造性保证）；真实双端/键盘输入、`kinetic_hpressed` 在 Sable 客户端侧的 HUD 表现、投掷“任意归属”结构时他人正在牵引的原生结构（活跃租约会拒绝为 `structure_controlled`）与 Sable 被他人牵引时的 `sable_launch_rejected` 只由代码路径与既有约束保护，未跑专项夹具；投掷后仍不静态化/不变回方块（同上一批）；`Server.startCharge`/`StartPacket` 保留但新客户端不再发送。
- 用户复测步骤：
  1. 未装 Sable：瞄准地面按住 H 蓄力、松开捕获；结构应在原位水平对齐、向上生成并抬升，底部不低于原选区底，10 秒悬停不抖动。
  2. 悬浮期间（或瞄准任意其他物理化结构）按 H：结构应沿视线以约 50 格/秒投出；重结构速度不变；松开 H 不再重复触发。
  3. 瞄准空处/普通地形按 H：应照旧进入蓄力，松开后捕获；左键不再投掷，且有正常原版攻击。
  4. Sable 存档重复 1–3：对准重结构（>128 质量）按 H 应直接投出而不是 `sable_traction_too_heavy`；被拒绝时应有 `message.academy.kinetic_throw.throw_rejected` 与带拒绝码的日志。
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `91E714EF38A4996C5513BB57A798E58D418F6C1E4F8A9A9C86DCABD358A84FF3`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `AEB9493FE061B73B15C08DF29EE03B05A494A9F139914BAA008DB4908EDD6B8D`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### P31 电击使结构操作节点族（2026-10-14）

- 审计发现（改动前行号）：
  - `ServerElectromasterProgramRuntime.magneticMove` 的方块分支是旧物理路径：`validate` 第 162–179 行把方块转成受控清单键，`apply` 第 200–207 行以 `FallingBlockEntity.fall`（旧第 204 行）+ `setNoGravity(true)` 创建临时实体，第 226 行沿 `origin→destination` 调用 `damageAlongTrajectory` 预结算轨迹伤害，第 228 行直接 `setVelocity`；旧第 249–255 行在撤销时 `discard` 实体并补回源方块（先丢弃后补的路径）。`damageAlongTrajectory`（旧第 438–470 行）同时服务实体与方块发射。
  - `MagneticStructureService`（P28）只有技能入口 `begin` + `Session.tick`（捕获/区域/租约/跟随），没有程序入口；`MagneticStructureControl` 只有 `massBudget`/`pullForce`，没有磁性参与质量概念，也没有程序发射标定。
  - 旧覆盖效果语义：`force_magnetize` 只在 `magneticMove` 的一次动作内生效（旧第 174–178、198–199 行，`FORCED_MAGNETIZATION_COST` 与移动费合并），无到期、无独立撤销，且与方块临时实体绑定。
- 改动/新增文件：
  - `MagneticStructureControl.java`：新增 `magneticParticipation(magneticCells, totalCells)`（0..1 夹取）与 8 参 `pullForce(...)` 重载（7 参保持逐值等价），程序牵引力按参与质量缩放；`MAGNETIZATION_TICKS=200`、`LAUNCH_SPEED_PER_TICK=1.4` 等标定常量落在服务。
  - `MagneticStructureService.java`：新增 `ProgramStatus`/`ProgramResult` 与程序入口 `programExtract`（磁性格子 BFS 邻域选取 `magneticSelection`、显式 `forceMagnetize` 准入、跨 tick 捕获、PENDING 不发布引用、失败保留源方块、覆盖到期记录）、`programControl`（复用同结构会话或接管未被租约占用的活跃区域并签发独占会话，Sable 返回 `sable_program_control`）、`programMove`（磁性参与质量 + 可选局部作用点 + 有界时长；越界/无参与/失联安全结束保留结构）、`programLaunch`（已取得控制权后的有界冲量；不预结算轨迹伤害；发射后撤销本次磁场与磁化覆盖）、`programRelease`（幂等、令牌作用域、不触继任）、`programSession`/`releasePrograms`/`clearProgramsAll`/`tickPrograms`；`Session` 增加 `report`（程序路径不发送技能 actionbar）、`participation`、`forcePoint`、`controllerId()` 与区域接管构造器；`admit` 增加 `allowNonMagnetic` 重载（技能路径调用保持逐值等价）。
  - 节点基础设施：`ElectromasterProgramNodeIds`/`Catalog`/`Capabilities`/`Definition`/`ExecutionBridge`/`Runtime`/`ServerElectromasterProgramRuntime` 增加 `program/electromaster/action/structure/{extract,control,move,rotate,launch,release,staticize}` 七个节点；extract 配置 `budget`(0–256)/`force_magnetize`，move 配置 `duration_seconds`(0.1–10)，launch 复用 `power`(0–2)；`ElectromasterProgramRuntime` 增加 7 个结构方法与嵌套 `StructureOperation`；bridge 装配执行器与可选 `target/anchor/seconds/pose` 端口；`STRUCTURE = academy:magnet_manipulation` 能力。
  - 旧路径迁移：`magneticMove` 方块分支整段删除，`target_type=block` 在 `validate` 显式抛出 `magnetic_block_target_moved_to_structure_nodes`（不保留双路径）；`FallingBlockEntity` 引用仅保留在磁力实体分支（pull/launch 语义与撤销逐行不变，代码注释注明实体不属于结构迁移面）；`damageAlongTrajectory` 现仅服务磁力实体发射。`releaseControlled` 追加 `MagneticStructureService.releasePrograms`，新增 `ServerStoppedEvent` 清理，`onServerTick` 驱动 `tickPrograms`。
  - 翻译：en_us/zh_cn 各新增 7 个节点名与说明、端口键 `port.force_magnetize`（`port.host/control/target/anchor/seconds/pose` 等 P30 键复用），`magnetic_move` 说明改为“方块目标请改用结构节点族”；未新增 value types（P30 的 4 个类型复用）。
  - 测试：新增 `ElectromasterProgramStructureNodesTest`（5 项）；`ElectromasterProgramExecutionBridgeTest.FakeRuntime` 补齐新接口桩；`MagneticStructureControlTest` +2（参与质量、程序力缩放）。
- 两张图验收（真实性级别：目录、schema、编译器、程序 VM、`ElectromasterProgramExecutionBridge`、节点执行器与动作计数为生产实现；electromaster runtime 为脚本化接口实现，不落地真实方块捕获/物理，真实世界程序图执行列为未验边界）：
  - 图一“创建→获取控制→移动→旋转(unavailable)→终止”：`extract(host,64,force_magnetize=true)` → `control(structure)` → `move(control,target=12.5,70,10.5,1.0s)` → `rotate` 返回 `unavailable` 且同一令牌随后仍可终止 → `release` 两次（第一次 `released`、第二次 `noop`，结构保留）。VM `COMPLETED`，调用顺序与参数逐字断言；move 记录 `participation=0.75`（生产 `magneticParticipation(3,4)`）。
  - 图二“发射/静态化失败”：`extract` → `control` → `launch(direction=+Y,power=2.0)` 记录标定速度 42.0（`LegacyMotionUnits.velocityPerSecond(LAUNCH_SPEED_PER_TICK)×interpolate(power)`）且无任何轨迹伤害结算 → `staticize` 返回 `unavailable` 且唯一内容所有者保留。
  - 另加“非磁性提取被拒绝”图：`force_magnetize=false` 时提取返回 `rejected:not_magnetic`、不发布 structure、源方块保留；PENDING 图：extract 返回 PENDING 时不发布 `structure` 输出，程序仍完成。
- 回归验证：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：单测 2603 项（基线 2596，本轮 +7）+ Sable 20 项零失败/错误/跳过（`build/p31-tests-final.log`，`build/test-results/test` 汇总 failures=0 errors=0 skipped=0）。
  - 因改动 `MagneticStructureService` 物理路径，专服烟测新隔离目录 `run/native-structure-physics-smoke-server-p31-r1` PASSED、`exit_status=0`（`build/p31-world-smoke-r1.log`）；结果文件与 `fixp32-r9` 逐行 diff 仅 `first_ref`/`second_ref`/`shared_session` 三个随机 UUID 与 `ticks=908→906`，`kinetic_lift_rise=3.4831345682826043`、`kinetic_throw_speed=50.0`、`kinetic_impact_damage=255.0`、`kinetic_follow_rebinds=10`、`hsjet_launch_speed=115.37633965527773`、`mag_pull_speed=45.57584523660533`、`support_*`/`grasp_*`/`terrain_*` 全部逐值无回归。
  - Sable 专服烟测新隔离目录 `run/sable-compat/p31-server-r1`（`-PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableCapture=true`，目录按既有约定预置）PASSED、`exit_status=0`（`build/p31-sable-server-r1.log`），`sable_capture_committed=true`、`sable_capture_rejected_be_preserved=true`、`sable_hpressed_heavy_throw=true`、`sable_h_throw_speed=49.999999319218475`、`sable_container_tick_guard_active=true`、`pneumatic_*`、`npc_*`、`resource_callback_*` 全部保持。
  - `build -DisDev=true` 23 秒、`-DisDev=false` 37 秒通过（`build/p31-build-dev.log`、`build/p31-build-release.log`）。
- 失败轨迹：首轮编译通过；首轮定向单测 1 失败——`launchAndStaticizeGraphReportsBoundedLaunchAndNoPresetDamage` 的脚本化 runtime 对 `force_magnetize=false` 一律返回拒绝，extract 未发布 `structure`，staticize 因此 `MISSING_INPUT_VALUE → FAILED`（`build/p31-tests-r1.log`）；修复为脚本显式 `rejectExtraction` 开关、发射图改用显式覆盖后通过（`build/p31-tests-r2.log`）。实现中有一次编辑误插重复 `isEditableBlock`、一次测试误删节点行，均在编译/运行前自查修复，未进入任何构建或运行产物。世界/Sable 烟测无需保留失败目录。
- 未实现前置（仍 unavailable）：`action/structure/rotate` 缺有限力矩姿态控制，`action/structure/staticize` 缺安全静态化；两者均返回明确 `unavailable`，不无条件赋姿态、不用 `detach → setPose → attach` 顶替、不先 discard 再补方块，失败保留内容与唯一所有者。
- 未验边界：真实世界程序图（原生 extract 跨 tick 捕获，单次程序执行无法在提交前取得引用，故“创建→移动”同图只做脚本运行时验证；真实世界仅验证服务与技能回归）；非玩家施术者入口；真实双端与真实键盘输入；Sable 的程序提取/控制/移动/发射（`programControl` 显式 `sable_program_control`，程序提取仍经路由捕获，未跑 Sable 程序图专用夹具）；磁性结构发射后的真实接触伤害未实现（本批只保证不预结算，接触结算仍由物理求解器/既有区域表现承担）；强制磁化覆盖到期仅在 `tickPrograms` 判定（到期且参与质量为 0 时安全终止，否则降为原生磁性参与比例继续）；`programControl` 只接管已有活跃物理区域的结构，不会为无区域结构新开区域；每玩家同时只有一个程序会话，程序移动/空闲窗口上限 600 tick，物理区域长期回收同 P25–P30；节点暂无可产生 `structure_anchor`/`structure_pose` 的公共输入节点（端口为后续扩展预留）。
- 当前构建（含 P19–P31）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `2ECA357DDF2F77C818C66A91DBBA0528EBC689FC09AEA40F8669070E8409C87A`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `0A271C035779828F9BF472DFCC73A8A1B90A8DCEA37B5A12D26ACE9AA2BC9CD4`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。

### 修复：程序动作失败呈现与缺值端口诊断（2026-10-14）

- 机制（用户真实客户端日志已定位）：客户端 `latest.log` 5336–5342 报“节点未产生所需数据…节点 发射结构 [#4]”，服务器真实原因是 `获取结构控制` 的 Sable 程序控制被拒（`Magnetic pull sable control is not implemented; retaining StructureRef[... backend=academy:sable]` + `Magnetic pull rejected …: sable_pull_unavailable`）。两个桥的 `structureResult(...)`（`ElectromasterProgramExecutionBridge.java` / `AcceleratorProgramExecutionBridge.java`）对 `REJECTED/UNAVAILABLE` 结果仍调用 `next("flow")` 且只在非空时输出 `structure`/`control`，控制被拒后流程静默继续；下游发射结构解析 `control` 输入失败，`ProgramVm` 在消费节点抛 `MISSING_INPUT_VALUE`，失败归属与原因都被替换成“缺数据”，根因被掩盖。
- 失败语义审计：`ProgramNodeStep` 没有失败指令；`ProgramActionTransaction` 的顺序结算失败已通过 `SettlementException` → VM 通用异常通道按执行节点归属；VM 缺值只带枚举、不带端口。因此新增专用异常通道 `ProgramActionFailure`（`org.academy.internal.common.ability.program`，携带 `ProgramVmDiagnostic` 与机器可读 `detail`），由 VM 在动作节点转换为带原因失败，不新增静默路径。
- 改动文件：
  - `ProgramActionFailure.java`（新增）：动作已执行但未成功时抛出，携带诊断与 `detail`。
  - `ProgramVm.java`：`ExecutionFailure` 增加 `port`/`detail`；缺值抛出点补端口名（`resolveInputs` 用消费节点的输入名覆盖上游输出端口名，`resolveValue` 用源输出端口）；`ProgramActionFailure` 转为节点失败；`FAILED` 结果携带两者。
  - `ProgramVmResult.java`：新增 `@Nullable String port` 与 `@Nullable String detail`（保留三参构造，旧调用不破）。
  - `ProgramVmDiagnostic.java`：追加 `ACTION_UNAVAILABLE`（序列化按 ordinal，追加在末尾）。
  - 两个桥 `structureResult`：`REJECTED` → `ACTION_REJECTED`、`UNAVAILABLE` → `ACTION_UNAVAILABLE`，`detail = 小写status + ":" + result.detail()`（如 `unavailable:sable_program_control`、`rejected:not_magnetic`）并在该动作节点失败；`COMPLETED` 但 structure/control 输出缺失 → 该节点失败并给出 `completed:structure_missing`/`completed:control_missing`；`PENDING` 保持现状（不输出 structure/control、流程继续，交由图的 result 分支处理，见未验边界）。
  - `ProgramSessionScheduler.Termination`、`ServerProgramExecution.finishDeferred`、`AbilityProgramManager`（`ExecutionOutcome`、`failureOutcome`、`ResultPacket` 新增 `detail` 字段、自动/手动/延迟失败路径）逐层转发 port/detail。
  - 客户端：`ProgramDiagnosticText.locateRuntimeFailure`（新格式）+ `AbilityProgramEditorClient`/`ModularProgramScreen.kt` 保存并显示 VM 失败端口与详情；旧 `ProgramDiagnosticText.locate` 与旧消息键保留不动。
  - lang（en_us/zh_cn，按自然序插入）：`message.academy.program.execution.diagnostic.action_unavailable`、`screen.academy.program.diagnostic.missing_input_port`、`screen.academy.program.diagnostic.detail`。
  - 失败呈现对照（旧 → 新）：
    - 控制被拒（Sable）：旧 = 发射结构 [#4] `节点未产生所需数据；请检查…`；新 = 获取结构控制 [#3] `该操作在当前环境下不可用；节点 获取结构控制 [#3]；详情：unavailable:sable_program_control`，发射结构不再执行。
    - 提取被拒：旧 = 同名图继续、下游缺值；新 = 提取节点 [#2] `ACTION_REJECTED` + `rejected:not_magnetic`，源方块保留断言不变。
    - 旋转/静态化 unavailable：旧 = 图继续到终止/下一节点；新 = 该节点失败 + `unavailable:finite_torque_control_unavailable` / `unavailable:safe_staticization_unavailable`，控制不被破坏、内容所有者保留断言不变。
    - 真缺值：旧 = `节点未产生所需数据…节点 发射结构 [#4]`；新 = 追加 `缺少输入 结构控制令牌` 端口信息（中英键均新增）。
- 验证：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`：普通单测 2609 项（基线 2603，本轮 +6：ProgramVm 缺值端口/动作详情 +2，电击使旋转失败/控制被拒 +2，矢量旋转失败/控制被拒 +2）+ Sable 16 项零失败/错误/跳过（`build/fixprog-tests.log`；`build/test-results/test` 汇总 599 文件 failures=0 errors=0 skipped=0）。
  - `build -DisDev=true` 通过（`build/fixprog-build-dev.log`，24 秒）、`build -DisDev=false` 通过（`build/fixprog-build-release.log`，34 秒），含 Sable 打包/依赖门禁。
  - 未改动 `MagneticStructureService`/`VectorStructureService` 的 Sable 拒绝行为与任何物理路径，因此世界烟测未复跑（条件不触发）；测试期望仅从“静默完成”改为“正确节点失败 + 正确原因”，未放宽断言；PENDING 用例仍断言程序 `COMPLETED`。
- 失败轨迹（日志均保留）：首轮 `compileJava` 失败两次——`ProgramDiagnosticText` 对嵌套类型的 `@Nullable` 位置不合法（改为 `ProgramEditorNodeCatalog.@Nullable Entry`）、`AbilityProgramManager` 缺 `(…, port)` 旧重载（补回）；首轮定向单测 2 失败——旋转失败图仍断言 `applied` 缺 `move` 行（`build/fixprog-tests-focused.log`），补齐后 `build/fixprog-tests-focused2.log` 全绿。无失败运行目录产生。
- 未验边界：真实客户端与真实键盘输入需用户复测（本轮只有用户日志定位；`runClientDev` 未启动）；Sable 磁力/矢量 `programControl/programLaunch` 的实现（含绕过质量门）由后续任务完成，本棒只改呈现；`PENDING` 结构操作仍按原语义继续流程，引用该输出的下游节点会在自身节点报告缺值端口（图的 result 分支应自行处理 PENDING）；precision/mentalout 等非结构动作的 detail 转发为空（沿用既有诊断）；`docs/vfx/...` 未触碰。
- 复现命令：
  - `.\gradlew.bat test sableCompatTest -DisDev=true`
  - `.\gradlew.bat build -DisDev=true`
  - `.\gradlew.bat build -DisDev=false`
- 当前构建（含本修复）：`build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` SHA-256 `9EB1550644CEF53714FF84109856D399E86D390A8F671C12270FFA5C93FA2BFB`；`academy-26.2.0-0.0.4-alpha-release.jar` SHA-256 `A9F3372640C726FDBCACD80FB175B79C23F99CB4FE7EB6600FD6261B9BCBA3F4`。未复制到整合包，未操作用户存档，未创建本地 Git 提交。
