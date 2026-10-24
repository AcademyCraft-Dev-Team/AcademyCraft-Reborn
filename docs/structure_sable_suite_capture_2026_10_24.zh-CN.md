# Sable／航空学组合中的技能构建、抬升与方块准入

后续箱子与含水方块的实际接入、当前支持范围和恢复验证见 [payload 接入记录](structure_payload_capture_2026_10_24.zh-CN.md)。下文保留本次提交时的诊断与方案，箱子／含水方块“当前拒绝”的描述属于接入前状态。

基于 `37a7b9b8`。本轮修复 ACR 的捕获与提取接线；没有修改航空学、Sable 的 JAR，也没有修改原存档或替换 PID 48132 已加载的模组。机器证据见 [JSON](../validation/native_structure/structure_sable_suite_capture_2026_10_24.json)。

## 现场与已修复原因

现场为 `D:/MC/cs/.minecraft/versions/ACclient1.1`，Minecraft 26.2、NeoForge 26.2.0.70、Java 25。Sable 与航空学组合包的 SHA-256 与 `docs/sable_compat_artifacts.json` 一致；使用该组合在 fresh 隔离副本验证。未复跑整套客户端的其他全部模组，不能据此宣称排除了所有模组间冲突。

1. **新提取控制器仍经过旧 Sable 牵引质量门槛。** 现场多次记录 `sable_traction_too_heavy`。H 捕获仍受 128／256／512 的旧门槛，tier 0 选区也裁到 128 格；普通石头／铁块按 Sable 质量单位计算很容易失败。默认新控制器改用共同 256 格预算、真实质量对应的有界力预算；旧质量门槛只留在显式关闭新提取的诊断路径。
2. **远坐标精度容差突然归零。** 现场 `(-2162.000120066, 67.839975326, -1481.999993991)` 等位置报 `new_contact_not_proven_clear`，随后 `extraction_blocked`。固定版本 Rapier 公布 float 世界质心，减去 double 局部质心后会出现约 0.00012 格横向误差。原桥接在 half-ULP 大于 0.0001 时返回零容差，约从绝对坐标 2048 起触发。改为 half-ULP 连续增长并在 0.001 格封顶；只处理已有宿主接触的观察误差。新增接触、加深穿透、真实顶盖、其他运动结构的包络仍拒绝。没有缩小碰撞盒或关闭碰撞。
3. **固定 10,000 力上限不足以悬浮重型选区。** 256 格铁块质量为 1024，默认重力下仅抗重力已需约 10,045。新预算 `max(10000, mass * 40)`，上限 1,000,000；超过可支持质量在捕获前拒绝。未知质量后端保留原预算，不改真实质量。
4. **选区中的软方块被按“必须搬运”送入 Sable 窄白名单。** 新增共用显式清理动作：先检查所有源格的范围、加载、权限与后端准入，再通过正常破坏／掉落事件清理可破坏的非完整软方块，使用清理后的同一请求实例做预演和事务提交。取消破坏、替换源格时不能静默省略该格。创建标记随新请求完整保留。
5. **清理软方块可能改变局部原点。** 新增仅原主体可读取的 `sourcePositions`，三个技能及节点保存实际捕获的实体格；高速喷射同时修正喷口局部偏移。原选区最低格只有藤蔓的实机反例覆盖计数、源相对高度和喷口固定在正确铁块上，避免成功创建却失去喷口支撑。

第 4 项用于动能投掷、磁力操控、高速喷射的默认服务及对应精密操作捕获动作；只读规划 API 不执行破坏。清理属于显式技能动作，已清除的软方块不会因后续净空检查失败而凭空复原；实体方块的搬运仍由原事务负责回滚。

## 当前 ACR 技能入口的方块行为

这是 **ACR 捕获事务的支持范围**，不是 Sable／航空学本身的完整支持范围。

| 方块／状态 | 本地 `academy:sublevel` | ACR 的 Sable 捕获 | 本轮处理 |
| --- | --- | --- | --- |
| 草方块、完整雪块 | 支持 | 已在白名单中 | 搬进结构，保留方块状态，不生成重复掉落 |
| 薄雪、短／高草、蕨、花、作物、树苗、枯草、藤蔓等合格软方块 | 原本可作为状态复制，部分不能独立成体 | 部分被白名单／非完整碰撞拒绝 | 非完整软方块先破坏，再捕获剩余实体部分 |
| 细雪、落叶堆、蘑菇、悬根、干草类植被 | 部分原本可复制 | 多数不在白名单 | 补充共同软方块策略；符合权限、硬度与保护标签时破坏后创建 |
| 树叶 | 支持 | 原版树叶家族在白名单 | 被选中的完整树叶保留；抬升路径上的合格树叶走清障；存在待执行方块 tick 时仍可能拒绝搬运 |
| 普通石材、泥土、沙砾、原木／木板、矿物块、混凝土、陶瓦等 | 状态支持且无下述限制时允许 | 限定原版集合 | 仍按后端准入；准确集合见 `NativeCaptureWhitelist.CuratedSet` |
| 耕地、土径 | 支持 | 有专门地形扩展 | 保留其非完整碰撞状态 |
| 任意模组块、非软的半砖／楼梯等 | 无 BE、无流体、可破坏、无待执行方块 tick 时原则上允许，仍需有效碰撞体 | 未列入白名单时拒绝 | 本轮没有把未知内容假定为可安全搬运 |
| 箱子、熔炉、告示牌等方块实体 | 当前 ACR 事务拒绝 | 当前 ACR 事务拒绝 | 后端本身不等于不支持，见下一节 |
| 含水方块、纯水／熔岩等流体状态 | 当前 ACR 事务拒绝 | 当前 ACR 事务拒绝 | 需补齐完整搬运、流体 tick 与归块语义 |
| 不可破坏方块、权限禁止／受保护位置 | 拒绝 | 拒绝（可能先返回白名单原因） | 保留 |
| 待执行方块 tick | 搬运拒绝 | 搬运拒绝 | 合格软方块可以按正常破坏清理，不能借此搬运复杂状态 |

额外共同边界：最多 256 个源格、各轴跨度最多 24、源区块必须已加载、必须有可成体的实体格；净空、物理容量、控制租约等也可拒绝。软方块清理沿用 `DestroyBlocksSetting`、交互权限、`BreakBlockEvent`、`BlockDropsEvent`、`MOTION_PROTECTED`／`MOTION_BREAKABLE` 策略，硬度须有限且 `0 <= hardness < 0.5`。纯草丛被清空后不能制造空结构；没有任何实体支撑时在破坏前拒绝。

## 箱子和含水方块：可以支持，但当前 ACR 尚未完整接入

已核查本机固定版本源码，航空学 `SimAssemblyHelper.assembleFromSingleBlock` 调用 Sable `SubLevelAssemblyHelper.assembleBlocks`。后者的 `moveBlocks`：

- 保存方块实体完整 NBT，改写目标坐标，并在新方块实体上 `loadWithComponents`；
- 对容器处理 loot table／`Clearable`，避免原位置移除时额外掉出一份物品；
- 搬运完整 BlockState，旋转不会自动去掉 `WATERLOGGED`；
- 航空学解体同样调用 `moveBlocks`，并另行处理 Create contraption、胶水、实体和跟踪点。

源码位于 `D:/mcmodtest/create-fork/.porting/sources/Sable/common/src/main/java/dev/ryanhcode/sable/api/SubLevelAssemblyHelper.java`、`D:/mcmodtest/create-fork/.porting/sources/Simulated-Project/simulated/common/src/main/java/dev/simulated_team/simulated/util/SimAssemblyHelper.java`。本轮只调用公开 API 做验证，没有复制这些实现。

新增 `SablePayloadCapabilityProbe` 在实机组合中执行：铁块＋含 19 个钻石的箱子＋含水木台阶 → Sable plot → 世界。验证源格清空、箱子完整 NBT（排除坐标）一致、19 个物品不丢不增、含水状态往返不变、源位置和 plot 都没有额外 ItemEntity。**通过**。这证明后端搬运能力，不代表 ACR 的掉线恢复／自动归块已支持这些内容；没有把一次同步往返冒充重启恢复验收。

ACR 的阻塞点明确存在于多层，而非只有白名单：

| 层 | 当前缺口 | 接入契约 |
| --- | --- | --- |
| `CaptureTarget` | `writeCell/readCell/clearCell/RestorationCell` 只传 BlockState | 改为完整 `StructureBlockData` 或版本化 `CapturedCell`，覆盖状态、规范化 BE NBT、必要的调度数据；旧状态记录可读取 |
| `StructureCaptureCoordinator` | `encodeState`、源复核、回滚都只处理状态；BE 检查硬拒绝 | 先保存完整不可变快照和有界日志，再分配／复制／核对；源清除不能触发容器掉落；失败或重启始终只有一个内容所有者 |
| `LevelCaptureSource`、完成证据及绑定 | 源摘要只含 `state.toString()`，明确拒绝 BE／流体 | 摘要包含规范化 payload；捕获后核对 BE 内容；不能删除校验后让内容变更被误认为原预演 |
| 本地／Sable 后端 | 本地有真实 plot 存储，Sable 有完整 NBT 搬运能力；ACR 接口没贯通 | 在隔离／冻结阶段写入 BE；恢复 level／position、能力缓存与通知；按后端声明支持类型，不全放开未知机器 |
| `MarkedGridRestoration` | `RestorationCell`、落位日志、源退休证据都拒绝 BE／fluid | 同步扩展完整快照和 crash recovery；接收端空间、权限、朝向、邻接、含水策略必须先确定，成功后才删除原体 |

建议按以下顺序落地，不能只移除拒绝条件：

1. 完整 cell payload、摘要和日志版本兼容；保持旧存档可恢复，限制单格／总 NBT 体积。
2. 首批支持普通单箱、木桶和无 BE 的含水台阶／楼梯；测试物品组件、名字、锁和 loot table。双箱必须整组选择或明确拆分为独立单箱，不能留下指向未搬运半边的状态。
3. 将上述类型接入三技能和三个捕获节点，再接自动归块。捕获完成到正式接管之间暂停容器／流体副作用；活动阶段按后端正常运作，归块时重新读取当前内容，不能恢复初始旧库存。
4. 含水块先定义“水随块搬走，源位置不额外复制一份”的策略；纯流体不是刚体种子，单独留给后续流体后端。源与目标的 pending fluid/block tick 要迁移或按已证明等价的方式重新调度。
5. 按 PREPARED、COPIED、COMMIT_DECIDED、归块写入中等断点注入失败／重启；验证每份物品与水状态只存在一次、所有权恢复、容量释放。测试其他模组的箱子、Create 机器后再逐类扩展。

已有航空学等第三方生成结构仍不打 ACR 技能生成标记，也不进入 ACR 自动归块。通过 ACR 新捕获的复杂结构只有在其完整归块路径通过验收后才允许创建；不能靠“禁用自动归块”掩盖容量泄漏。

## 验证与复跑

各 fresh 副本由 `validation/native_structure/prepare_default_skill_smoke.ps1 -Fresh` 创建，未写原 `test`／`test2`。Gradle 成功退出之外，必须检查对应结果 JSON 的 `passed`。

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
./gradlew.bat test sableCompatTest -DisDev=true --console=plain
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name suite-soft-repeat -Fresh
./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite `
  -I validation/native_structure/structure_soft_capture.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-suite-soft-repeat `
  -PacademySoftCaptureRun=clientSable -PacademySablePayloadCapabilityProbe=true --console=plain
```

无 Sable 时改为 `runClientDev`，省略 `academySableRuntime`、`academySoftCaptureRun` 和 `academySablePayloadCapabilityProbe`，并使用另一个 fresh 副本。

重型不平坦抬升使用 `sublevel_lift_tiers.init.gradle`：`academySubLevelLiftRun=clientSable`、`academySableRuntime=suite`、`academySubLevelLiftEmbedded=true`、`academySubLevelLiftTerrain=step`、`academySubLevelLiftAnchor=-2160,150,-1480`、`academySubLevelLiftMaterial=iron`。三个 tier 的场地在夹具中相隔一段距离；tier 0 在报告故障的远坐标区间，tier 1／2 沿用夹具偏移。记录实际质量 1024，实测抬升约 3.0324／5.0324／7.0324 格，各悬浮 200 tick。

软方块本地／组合环境各 11 项通过；Sable 组合额外完整 payload 往返通过。全量 JVM 3,192 项、Sable 专项 32 项均零失败。三个默认技能、三个 VM 图和最终构建结果记录在机器证据中。默认技能夹具增加 `-PacademyStructureDefaultProvenanceSoftOrigin=true`，在最低源格放置会被清除的悬挂藤蔓，验证真实服务仍抬升 3／1／1 格，计数只含实体格且喷口 offset 归零。

排除的测试干扰也保留：初次使用旧含本地结构的世界触发迁移保护，不能算组合捕获失败；初次重型 tier 2 收到真实 H 网络包提前发射，重跑窗口最小化后通过；软方块夹具最初在传送当 tick 创建导致 Sable 卸载／重新激活，等待新位置跟踪就绪后通过，未放宽 runtime 身份校验。

本轮没有做 FPS／解算吞吐基准，不报告性能提升倍数；没有修改未提交的 `native/academy_physics/src/lib.rs` 或更新打包的原生 DLL。箱子／含水的 ACR 完整接入、模组机器、重启恢复仍属于上面的后续工作，不能从后端能力验证推断为已经实现。
