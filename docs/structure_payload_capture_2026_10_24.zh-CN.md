# 箱子、含水方块的技能捕获与归块接入

本轮从本地提交 `e2cec71f`（修复物理结构提取抬升并统一软方块清理）继续，落实此前 `structure_sable_suite_capture_2026_10_24.zh-CN.md` 的 payload 方案。该实现已单独提交为 `c3b9fc5d`（接入物理结构容器和含水方块的安全捕获归块）。以下验证记录对应这次 payload 实现；后续本地结构扩建的验证另见 `sublevel_growth_2026_10_24.zh-CN.md`。运行测试只使用 `run/native-sublevel-direct-smoke-payload-*` 的独立副本，没有替换用户客户端的模组或修改原 test/test2。

## 支持边界

| 内容 | 本地后端 | Sable 后端 | 约束 |
| --- | --- | --- | --- |
| 原版箱子、陷阱箱、木桶 | 支持完整状态与 BE NBT | 支持完整状态与 BE NBT | 正被打开的容器不参与捕获／归块；保留物品组件、名称、锁、未生成的战利品表及种子 |
| 原版双箱／双陷阱箱 | 支持 | 支持 | 必须完整选择两半，互相邻接、朝向和类型须一致；不偷偷扩大技能选区 |
| 含水箱子、无 BE 的含水台阶／楼梯等 | 支持 | 支持 | 保留 `WATERLOGGED`；水随块搬走，源格清空；归块完成后恢复流体调度 |
| 纯水、熔岩 | 仍拒绝 | 仍拒绝 | 不把纯流体当刚体创建，也不借含水支持放开流体后端 |
| 熔炉、告示牌、潜影盒、漏斗、模组容器／机器等其他 BE | 仍拒绝 | 仍拒绝 | 需要单独验证其 tick、能力、实体和拆装语义后再扩大支持范围 |
| 不可破坏方块、保护区、未加载源区块 | 沿用拒绝 | 沿用拒绝 | 原有权限、容量、跨度和几何限制保留 |

箱子／木桶用于重新检查开箱人数的待执行方块 tick 可接受；其他待执行方块 tick 仍拒绝。含水方块的流体调度在事务锁期间延后，归块发布后重新调度。没有宣称搬运任意方块的 tick 队列。

磁力操纵继续要求磁性材料；纯木箱不是天然磁性目标。含箱子的铁块结构可以正常提取，精密操作的强制磁化选项可直接提取箱子。H 与高速喷流没有这一材料限制。

## 完整内容与单一所有权

- `StructureCellPayload` 统一准入、双箱成组检查、完整 NBT 编解码、坐标规范化、摘要及写后复核。摘要包含物品数量和组件，忽略容器在世界／plot 中的存储坐标；CompoundTag 键顺序不影响摘要。
- `CaptureTarget` 增加完整 `StructureBlockData` 的读／写／清除与归块快照。本地 plot 与 Sable plot 使用真实 BE；旧纯状态日志仍能读取。出现 BE 状态却没有完整 NBT 的记录会保留并拒绝推断恢复。
- 捕获预演、完成证据、提取绑定都复核库存摘要。只改白名单而绕过证据校验的做法没有采用。
- 源与目标格在事务未完成时锁定。菜单、常规容器读写／插入、战利品生成、破坏、掉落和流体／方块调度不能消费暂存副本。只有事务内部对明确位置的写入可穿过锁。
- 清除原容器前清空其旧对象的战利品表和库存，再通过完整 before/after image 写入；避免 removal hook 额外掉出一份库存。成功归块读取**当时的库存**，不会恢复创建时的旧库存。
- 含 payload 的捕获在删除源格的决定前，先确认目标和原世界源格落盘。决定前中断回滚目标；决定后中断校验目标并继续删除源格。
- Sable 的普通 `saveAll()` 会吞掉部分 IO 错误，且不负责普通世界源区块。ACR 额外通过已核对身份的存储槽、索引、占用表和源区块持久化取得确认。回滚已保存的 Sable 结构同样先持久化删除其副本，再释放源库存。
- 自动归块的落位日志、源退休证据和回滚都携带完整 payload；目标放置可取消，取消后清除完整目标副本并保留源。未知内容、写后不一致或落盘失败时保留日志和隔离，不擅自覆盖或生成另一份物品。
- `StructureTransactions.discard` 不再允许直接丢弃含 BE／含水内容的物理结构，也不将无法读取的内容视为空结构。技能接管失败不能通过删除结构使箱子消失。

第三方航空学等结构没有被补打 ACR 技能标记。自动归块继续只处理符合 ACR 来源与策略的结构，所有者离线不阻止归块。

## 技能与节点

H 动能投掷、磁力操纵、高速喷流的默认入口与对应精密操作节点共同走完整捕获事务。H／高速喷流中旧有的“必须没有 BE”筛选已改为共享支持范围；磁力预演同样采用完整内容准入。

另修复同步捕获完成到提取绑定之间的一 tick 空窗：成功后立即建立提取控制，再按原状态机推进。否则箱子碰撞模型比整块低，刚创建后可能先下沉，再被绑定检查误判为路径阻塞。

本地容器菜单增加实际 BE 身份和双箱另一半的有效性检查；结构退休或箱体被替换后，旧菜单失效。

`/academy debug sublevel restore_all` 同步改用完整 payload 准入，能够还原本地含物品箱子、含水方块；仍只处理本地结构。管理员验证包含两个维度、远处未加载目标、未标记本地结构、事件取消与重试，并确认组合环境中的 Sable 对照结构不受影响。

## 验证入口

所有图形测试使用真实 NeoForge 集成服务器和真实后端，不使用假物理服务。无 Sable 使用 `runClientDev`，组合环境使用 `runClientSable -PacademySableRuntime=suite`。**无 Sable 命令不要传 `academySableRuntime=suite`**，该属性会把整套依赖加入运行类路径。

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
./gradlew.bat test sableCompatTest -DisDev=true --console=plain
./gradlew.bat build -DisDev=true --console=plain
./gradlew.bat build -DisDev=false --console=plain

./validation/native_structure/prepare_default_skill_smoke.ps1 -Name payload-repeat -Fresh
./gradlew.bat runClientDev -DisDev=true `
  -I validation/native_structure/structure_payload.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-payload-repeat --console=plain
```

其他独立验证：

| init 脚本 | 选项 | 检查内容 |
| --- | --- | --- |
| `structure_payload.init.gradle` | Sable 额外 `-PacademyPayloadRun=clientSable` | 六组容器往返、3 格抬升、修改实时库存、自动归块、离线归块、整组双箱、含水块、战利品、取消后重试、无额外掉落 |
| `structure_default_provenance.init.gradle` | `-PacademyStructurePayloadRoutes=true` | 三技能真实服务入口、抬升、库存和标记；控制未标记结构不会补打标记 |
| `structure_program_orientation.init.gradle` | `-PacademyStructurePayloadRoutes=true` | 三类真实节点 VM 的提取／控制／旋转／回正／持续旋转／释放及库存检查 |
| `structure_capture_payload_crash.init.gradle` | `-PacademyCapturePayloadCrash=copied_saved` 或 `commit_saved`；同副本重启为 `verify` | 创建过程中强制退出，恢复后只存在一份完整库存 |
| `structure_restoration.init.gradle` | `-PacademyStructurePayloadRoutes=true -PacademyStructureRestorationMode=crash_targets_saved` 或 `crash_commit_saved`；同副本重启为 `recover_verify` | 归块过程中强制退出，验证目标回滚或已提交源退休 |

强制退出的 Gradle 非零退出码是预期现象，必须同时核对阶段证据 JSON；重启后还须检查结果 JSON 的 `passed`。节点测试直接运行生产 VM，默认技能测试直接调用生产服务，没有冒充鼠标／键盘或网络包测试。强制退出测试模拟进程中断，不代表已测试磁盘损坏或操作系统断电。

最终验收记录见 `validation/native_structure/structure_payload_validation_2026_10_24.json`，其中区分已通过与未验证项目。

## 本轮验收结果

- 单元测试 3195 项、Sable 独立兼容测试 32 项通过。
- 开发版与发行版构建通过；本轮汇总 60 组实机用例全部通过。发行包为 `build/libs/academy-26.2.0-0.0.4-alpha-release.jar`，SHA-256：`32CE9027DAB56F7365B88F11D8AA6DB959971AD783FCA8A8B6B26CA540CBCDB5`。
- 两套环境各 6 组正常 payload 往返通过：库存 19 → 活动物理结构内修改为 37 → 自动归块后保持 37；双箱、含水箱／台阶／楼梯、未展开战利品、离线所有者和放置取消后重试均覆盖。
- 三个默认技能服务和三类真实节点 VM 在两套后端通过。节点验证持续旋转超过 200 tick 后仍生效，库存保持完整。
- 八组强制进程退出／重启恢复通过：两套后端分别覆盖捕获 COPIED、捕获 COMMIT、归块 TARGET_SAVED、归块 COMMIT；每组重启后连续 40 tick 确认仅一份完整库存可用。
- 本地实际菜单服务验证单箱、木桶、双箱可打开；双箱另一半锁定时拒绝打开，锁定任意一半会使旧菜单失效。
- 管理员批量归块在无 Sable／组合环境均通过，含物品的含水箱子正确落位。
- 软方块、受保护方块、不可破坏方块、未支持 BE／纯流体的回归结果及最终构建哈希见 JSON。

未验证：独立多人服务端长时运行、实际鼠标／键盘与多人并发菜单操作、任意模组 capability 绕开原版 Container 的直接 NBT 写入、系统断电或文件系统损坏。遇到不能确认所有权的异常记录，代码保留隔离与恢复日志，需要按日志处理，不自动猜测并重建物品。纯流体和未列入支持范围的机器不属于本轮开放内容。
