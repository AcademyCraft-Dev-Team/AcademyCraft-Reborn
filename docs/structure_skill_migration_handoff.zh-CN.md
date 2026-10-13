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

- 仓库 `D:/mcmodtest/AcademyCraft-Reborn-26.2`，分支 `26.2`，最近提交 `b6d20626 接入气动牵引与公共结构牵引会话`。
- 第十七批 Sable 查询/有限力牵引桥尚未提交；本次继续建立在这些修改上，不能清理或回退它们。
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

1. **公共捕获事务**：预检选择、权限、内容与几何预算；目标预留、持久化日志、复制校验、源移除、目标提交；每个阶段提供异常及重启恢复证据。先明确普通块范围，BE/计划 tick 不得静默丢弃。
2. **独立后端可供技能使用**：制品部署、容器容量/回收、活动物理区域跟随与恢复，以及真实玩家碰撞/承载。不能让玩家每四次使用便耗尽永久槽，也不能发布仅在测试 JVM 外部属性下可运行的默认技能。
3. **双后端捕获接入**：未装 Sable 创建独立结构，兼容混装创建 Sable 结构；未知版本/不可用时给出失败并保留源方块，不降级为旧实体。外部 assembly helper 仍只作测试工具，不能代替生产事务。
4. **迁移 `KineticThrow`**：保持区域选择和能力预算；HeldStructure 改为公共引用/租约；抬升、投掷、终止遵守有限力、单位、惯性契约；接触伤害只结算一次，不在世界碰撞时自动变回方块。
5. **承载验收**：独立和完整混装各测静止、升降、行走、跳跃、边缘、顶墙和旋转，之后测真实双端延迟。旧实体对照测试不能替代这些结果。
6. 再迁移高速喷流/磁场操作和三类行动节点；矢量改变运动、磁力筛选材料、空气通过受力锚点控制，共用结构身份、质量、资源和释放契约。

**当前不能对用户声称：动能搬运已改用新独立后端、站立抖动已修复、三个能力的行动节点已迁移完成。** 相关生产路径尚未改动，以上是接续工作的实现依赖，并非已交付功能。

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
```

外部固定依赖由 `docs/sable_compat_artifacts.json` 与 `gradle/sable-compat.gradle` 锁定。不要为了启动未知 JAR 放宽哈希门禁。新游戏验证使用新的隔离目录，不覆盖成功或失败结果，不操作用户存档。

后续每完成一个节点在此增加：修改文件、行为改变、验证命令及结果、失败轨迹、未验边界和紧接着应做的工作。已有完整架构设计位于 `docs/block_structure_physics_and_sable_compat_plan.zh-CN.md`。
