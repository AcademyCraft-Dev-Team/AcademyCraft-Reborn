# 开发线 B：真实后端角控制与节点接线

基于已提交骨架 `26635388`，承接 [独立算法阶段](structure_upgrade_line_b_2026_10_23.zh-CN.md)。本轮接入已有本地 SubLevel 和可选 Sable 求解器，启用三系精密操作的 `rotate`、`reset_orientation`、`spin`。未替换原生 DLL/ABI，未接入 MPS、新提取 planner、自动归块或碎裂。

## 运行入口与方法契约

| 位置 | 已实现行为 |
| --- | --- |
| `api/common/structure/upgrade/StructureOrientations` | 公开服务与内容版本 context；必须已有精确 direct-control 租约，可供非玩家服务端实体使用，不隐式获取控制权。 |
| `internal/common/structure/upgrade/control/LiveStructureOrientation` | 每服务器最多 16 个活动操作、256 个回执；结束回执保留 200 tick。绑定结构、主体、runtime、content revision 和 controlId；替换产生新 operationId，旧 stop 不取消新操作。死亡/断线/换维/租约失效/内容变化撤销，全部暂停也有外层 tick 超时。 |
| `SubLevelOrientationAccess`、`SubLevelPhysicsUniverse` | 实际每个子步前读角速度与姿态、复核并调整角速度。welded 结构保持整体 COM 线速度，并按新的 ω×r 更新各部件切向速度；惯量上界包含平行轴项。无角命令时保留原批量步进路径。 |
| `SableOrientationAccess`、`SableStructureTraction` | 使用 Sable 真正 pre-physics 子步，读取实际惯量张量/速度/姿态，增量调整角速度而不重写 COM 线速度。独立内容/姿态版本及真实碰撞几何缓存；可选依赖不泄漏到无 Sable 的加载路径。 |
| `OrientationWorldSnapshot`、`OrientationSweep` | 逐真实局部盒扫每实际子步计划的完整转动弧，包含 COM 自由位移及重力积分余量；只读已加载区块，邻居和 BE 依赖也受预算约束，应用前复核状态。其他本地/Sable 刚体作为运动障碍；相交的旧版物理体缺少完整适配时拒绝批准，不把它们当空气。 |
| `ProgramOrientationControl`、三系 server runtime/executor | 旧 rotate 保持 schema 1、绝对轴角、原 ID/输入/配置，改为有 operationId 的异步到位。reset/spin 共用同一服务，提交一次、轮询不施力也不重复扣 CP，到位/制动完成后才向后执行 flow。 |
| `ServerProgramExecution`、技能结构服务 | 程序正常结束、异常、取消都注销自身未完成角命令；不会撤销后继操作。持续角命令的原租约不被旧 200 tick 程序悬浮到期误杀。手动 H 原 10 秒窗口不变；同令牌角操作期间旧姿态 hold 让出角控制，继续平移力，结束后以实际角度接续。成功发射结束角操作。 |

`BRAKE` 按实际角速度有界制动，连刚排队、继承旧惯性的操作也等待真实子步，不提前报告停止。stop 请求只能收紧原角速度/加速度/力矩/超时/碰撞预算，不能扩大原授权。`RELEASE_INERTIA` 只停止该角命令；真实碰撞和其他显式控制仍会影响后续运动。

旋转完成要求实际误差和实际角速度都进入容差，提交不会直接改 pose、传送 COM 或穿墙回正。有限 spin 按实际求解时间累计；不可整除时提前不足一子步结束，不延长请求时长。Sable 带缩放或 Create contraption 的潜在相交几何目前保守拒绝，远处不相关结构不会全局阻断。

## 节点用法及当前限制

三系均从精密操作的结构控制令牌连接下列行动：

- `rotate`：已有绝对轴角目标；等待真实到位，默认 100 tick 超时。
- `reset_orientation`：默认 `world_up_keep_yaw`；可选最近网格 yaw。捕获基准尚未持久化，因此 `capture_baseline` 配合 `reject` 会明确拒绝；只有显式 fallback 才用世界网格。
- `spin`：方向输入为世界轴或局部轴，速度有正负号，单位 rad/s；结束可制动或保留惯性。

程序策略限制：角速度最高 2 rad/s、角加速度 6 rad/s²、力矩 1000、角误差 0.01 rad、角速度容差 0.02 rad/s、每步 16384 次形状比较；spin 最长 58 秒，另留 40 tick 制动预算。schema 的通用 API 范围更宽，超过实际 server 策略会给出拒绝原因。大质量/大惯量结构在同样力矩下转得较慢，可能到超时，不能把这种失败包装为完成。

每宿主、每后端的一次 pre-substep 调用共享 65536 格采样预算（不是跨维度、跨后端的服务器总预算），每操作 16384 格、近邻 128 个。Sable 最多检查 4096 个已加载候选，单体 geometry 65536 格/4096 盒；超预算或覆盖未知进入等待/失败，不隐式加载区块。接触附近的连续角包络偏保守，可能拒绝贴地回正；本轮没有用 setPose 脱困。

自由步进预测包括已经施加的冲量和当前后端重力。Sable 事件之后其他 mod 排队的力、求解接触冲量仍由实际求解器处理；该扫掠不是对任意后续外力的完整轨迹保证。其他结构的邻近/运动测试和复杂地形仍需扩展。

## 构建与可复现验证

使用 JBR25。本机环境脚本位于忽略目录，其他机器需自行配置相同外部原生库与 manifest。

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat test sableCompatTest -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat nativePhysicsTest -x testNativePhysicsRust -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat build -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat build -DisDev=false @physicsNativeArgs --console=plain
```

FFM 命令显式跳过已知失败的 Rust 前置，仅用于隔离验证 Java/FFM。未移除 Gradle 中原有测试依赖；普通 build 不包含 Rust 专项。S0 历史 23 通过/5 失败的接触问题没有由本轮修复，见 [S0 报告](structure_upgrade_s0_warmstart_2026_10_23.zh-CN.md)。工作区已有 `native/academy_physics/src/lib.rs` 诊断改动未由本轮编辑或提交。

后端 API 夹具（每次用新目录名；不会修改原 `run/saves/test`、`test2`）：

```powershell
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name 'orientation-native-live-final' -Fresh
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
  -I validation/native_structure/structure_orientation.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-orientation-native-live-final --console=plain
```

Sable 使用另一个目录和 `runClientSable`，增加 `-PacademySableRuntime=sable -PacademyStructureOrientationRun=clientSable`。夹具输出 `structure-orientation-result.json`：任意轴负角旋转、WORLD/LOCAL 自旋制动、网格回正、替换后旧 stop 隔离、墙体阻挡、排队替换操作制动、发射接管、租约释放、非玩家控制者移除后清理租约共 10 项。

真实三系 VM 图夹具将 init 改为 `validation/native_structure/structure_program_orientation.init.gradle`，单独准备目录，其他参数同上。它执行真实 server runtime 与节点 executor、scheduler：extract → control → rotate → reset_orientation → spin(250 tick) → release；仅附加同令牌 COM 悬浮力来隔离旋转行为，不额外续租。输出 `structure-program-orientation-result.json`，检查 200 tick 后仍有实际转动且最后完成制动/释放。该夹具未经过编辑器保存/执行网络包，不替代人工编辑器端到端验收或手动 H 的复杂地形抬升验收。

JBR25 最终全量 JVM **2,978 项**、Sable 专项 **23 项**、Java/FFM **36 项**全部通过，开发与发布 build 均通过。日志为 `build/line-b-live-build-dev.log`、`build/line-b-live-build-release.log`。FFM 仍按上文显式跳过 Rust 已知失败前置，不能称原生全绿。

双后端 API 各 **10 项**通过，双后端三系真实 VM 共 **6 条节点链**通过；每条 spin 请求 250 tick，200 tick 后均采到 44 个转动样本，本地约 0.8000、Sable 约 0.7982 rad/s，最后制动并释放。所有客户端正常保存退出。结果目录分别为 `run/native-sublevel-direct-smoke-orientation-{native,sable}-live-final-20261023`、`run/native-sublevel-direct-smoke-program-orientation-{native,sable}-ack-20261023`。机器可读证据见 [本轮验证摘要](../validation/native_structure/structure_upgrade_line_b_live_2026_10_23.json)。

真实 Sable VM 首轮暴露了“solver 完成 → 技能超时释放 → VM 读取回执”的时序缺陷，现用每会话精确 controlId 的 **20 tick 有界交接窗口**修复。只在曾见活动角操作且原租约仍有效时保留，终态轮询不续期，不延长平移/喷口工作和手动 H；复测三系均通过。

最终本地 VM 重跑曾在提取前失败（`build/line-b-live-vm-native-final.log`）：夹具传送使用零朝向，随后服务端设置的目标朝向被客户端传送回执/移动包覆盖，种子因此不在前方。现将目标朝向直接写入传送，并等待回执、客户端种子加载和双端位置/朝向连续两 tick 一致后启动图；不重试提取或放宽权限/选区。该失败记录保留，修正后使用 `*-ack-20261023` 新目录复测。

额外旧 H 台阶/嵌地/双扰动回归本轮 **FAILED**：tier0 实测 3.064378 格并悬浮 200 tick；tier1 目标 5 格，只升至 0.319660 后超时，tier2 未继续。日志 `build/line-b-live-h-regression.log`。该 tier1 捕获 128 个完整土/草格，合并前至多 128 盒，小于 256/body，属于单刚体；无新角会话，未走 welded 切向速度或新子步分支。旧通过点为 `(-78,120,56)`，本次为 `(-79,120,47)`，地形分区也不同。静态审计未找到此单体路径的新求解输入差异，但尚未做同点旧/新逻辑 A/B，**既不能宣称是本轮回归，也不能宣称 H 已修复**。此问题保留到提取/接触线，不能因上述角节点成功就关闭。

增加了仅供夹具使用的固定布局参数 `academySubLevelLiftAnchor`，不改变生产代码或高度/悬浮判据。相同当前生产修订、全新独立世界、只跑 tier1 的两个固定点随后均 **PASSED：5.064378 格、200 tick**：

| source_origin | 参数（同时 `-PacademySubLevelLiftTier=1`） | 日志 |
| --- | --- | --- |
| `(-78,120,56)` | `'-PacademySubLevelLiftAnchor=-155,123,70'` | `build/line-b-live-h-fixed-old.log` |
| `(-79,120,47)` | `'-PacademySubLevelLiftAnchor=-156,123,61'` | `build/line-b-live-h-fixed-new.log` |

这两次是独立单档复测，**不是旧/新实现的 A/B，也没有复刻连续多档时的完整世界历史**。因此坐标并非充分解释，顺序多结构/地形缓存/接触历史仍需定位；保留首次失败，不用独立成功覆盖它。

## UNVERIFIED 与后续完成判据

- 新 ExtractionPlanner 尚未接入 H；来源捕获日志、复杂台阶/凹角/顶盖/偏心扰动的真实提取与随后完整 200 tick，按原 B 线下一阶段继续。
- 本轮不解决 S0 原生接触红测，不声明本地性能已追平 Sable；多个活动角命令、密集运动邻居的 CPU/帧率对照仍待测。
- 超大 welded 结构、多结构相互碰撞、暂停/卸载后重载及任意其他 mod 外力的组合矩阵仍待扩展。
- 编辑器节点文字/枚举的人工视觉检查、编辑器保存/执行网络包端到端检查尚未进行；schema、翻译与真实执行证据分别记录，不以自动 VM 检查代替人工界面验收。
- 自动归块、技能区域破坏、首撞增益/碎裂、MPS/流体/软体仍为其他线的骨架，不随 ORIENTATION 启用。

本轮完成判据：双后端实际子步角控制、三系旧图兼容与新增节点链、实际制动/停止/替换、必要构建与隔离烟测均通过，并保留上述未覆盖项。`LIVE` 仅将 ORIENTATION 声明为 EXPERIMENTAL，不将全部升级标为 VERIFIED。
