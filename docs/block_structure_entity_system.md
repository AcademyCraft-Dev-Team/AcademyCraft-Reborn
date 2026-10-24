# 方块结构实体化系统

## 目标与边界

本系统为能力技能、精密操作程序和其他服务端逻辑提供统一的方块结构操作 API。当前快照实体后端覆盖：

- 从明确位置集合、六向连通区域或球形网格区域捕获方块；
- 保存方块状态与方块实体 NBT，在实体生成获准后无掉落地移出原方块；
- 将结构作为单一实体生成、移动、施加冲量、投掷和启停重力；
- 使用随水平旋转更新的复合碰撞体与世界、实体发生碰撞并按质量和闭合速度造成伤害、击退；
- 让站在结构顶面的实体随平台的实际位移移动；
- 将水平朝向吸附到最近的 90 度，并在当前位置对齐方块网格后还原；
- 在捕获或还原中途失败时尝试回滚；回滚不能完成时保留完整载荷和写入前数据并隔离结构。

动能投掷、高速喷流与磁场方块路径已迁移到公共结构服务：动能投掷的入口只保留按键/网络/蓄力，捕获、抬升、发射与终止委托 `VectorStructureService`（`KineticThrow.java:292/295/308/314`，捕获在 `VectorStructureService.java:672,1017`）；高速喷流经 `HighSpeedJet.java:365` 调服务；磁场方块路径经 `MagnetManipulation.java:490` 与 `MagneticStructureService.java:989` 接入。短时操纵用的快照实体仍保留，作为旧存档和普通实体对照路径。新增的独立后端已具备真实 `ServerLevel` 方块容器，提供服务端内容访问、原版方块运行、保存生命周期、旋转投影与受限逐格交互。最终目标仍是独立安装时也能运行完整动态方块空间，安装 Sable 时通过可选后端统一接管，详见[完整设计方案](D:/mcmodtest/AcademyCraft-Reborn-26.2/docs/block_structure_physics_and_sable_compat_plan.zh-CN.md)。

2026 年 10 月 12 日的第一批实现增加了公共空间与单位契约、失败保留和回滚数据隔离，以及可组合的碰撞查询。第二批增加四个独占维度槽、实体无关的公共生命周期 API 和真实区块容器。第三批加入模型投影、任意四元数姿态下的逐格命中、作用域上下文与受限拉杆交互。第四批接入原版普通单箱的专用模型、开盖状态与真实库存菜单，第五批完成六种原版方块的受限逐格放置及对应验收。第六批完成受限持续采掘、真实工具与掉落归属处理，基础检查、最终采掘客户端 r3 及既有客户端回归均已通过。第七批完成独立双精度刚体的原生绑定基础；第八批将真实容器几何、已加载宿主地形和姿态发布接入有界刚体区域，专项/全量测试、两种构建、三组 GameTest、真实专服 r3 和客户端 r1 均已通过。第九批已写入移动玩家操作的有界姿态协调、请求去重和最新采掘观察，真实双客户端直连及双向各 100 ms 延迟已通过，最终全量/两种构建与既有采掘客户端回归也已通过；各批证明范围与失败记录见文末。当前仍为 P0 部分完成；运动期间的通用交互、玩家承载、通用放置/采掘/菜单、技能迁移和 Sable 桥尚未完成。

第十批已完成有界区域与运动检查点、无原生配置时的数据保留、显式恢复及停服门禁。最新 r2 三个独立专服 JVM、客户端 r2、最终全量/原生测试、三组 GameTest 及两种构建均已通过，证据独立于第九批；设计与验收状态见[第十批](#第十批有界物理区域与运动状态保存恢复)。

第十一批已完成同宿主多个独立有界区域、稳定区域 UUID、manifest v3 兼容迁移和按区暂停/恢复；真实专服/客户端 r1、实际 v2 存档副本升级、新 v3 三 JVM、双区域跨 JVM r2、原生测试、三组 GameTest、最终全量测试和两种构建均已通过。结果按本批证据核对，不继承第十批通过结论。本轮只按宿主已加载区块独立调度，不增加主动加载、区域自动迁移/合并或分区几何缓存。

第十二批已写入保守的宿主地形分区缓存：复用未变化分片的碰撞形状和盒列表，仍逐格复验已加载区块、方块状态及实际读取的邻居。真实专服/客户端 r1、三 JVM 持久化、双区域专项、2476 项最终基础测试、17 项原生 JVM、12 项 Rust 与三组 GameTest 已通过，两种构建与成品核对也已通过，结果不沿用第十一批结论。本批不增加区域/存储容量、主动加载或自动分区迁移，也不宣称已有性能指标改善。

第十一、十二批已按用户授权合并为本地提交 `f2a2d828`，共 22 个相关文件。第十三批加入有限力与单点 PD 控制：公共请求与逐子步求解已写入，2493 项基础测试、20 项原生 JVM、12 项 Rust 及真实专服/客户端 r1、三 JVM 持久化、双区域与三组 GameTest 已通过；最终两种构建和成品核对也已通过。设计与证据范围见[第十三批](#第十三批有限力与单点-pd-控制)。

第十三批已按用户授权提交为 `e2bf5ccb`。第十四批接续 P1 的公共控制会话，加入独占限时租约；2507 项基础测试、104 项编辑器测试、20 项原生 JVM、12 项 Rust、真实专服 r1/客户端 r2、跨 JVM 回归、三组 GameTest 及两种构建与成品核对均已通过。各项采用本批证据，边界和首次客户端超时记录见[第十四批](#第十四批独占限时控制租约)。

第十四批已按用户授权提交为 `4f3a1a0a`。第十五批已补齐公共控制会话的实体生命周期绑定：玩家与非玩家共用实体绑定租约入口，离开宿主、注销或在控制边界发现死亡/失效时撤销。2507 项基础测试、104 项编辑器测试、20 项原生 JVM、12 项 Rust、真实专服 r1、客户端 r1、三 JVM 持久化、双区域、三组 GameTest、两种构建与成品核对均已通过。本批采用独立证据，契约和限定验收范围见[第十五批](#第十五批实体绑定控制租约与生命周期撤销)。

## 模块

公开 API 位于 `org.academy.api.common.structure`：

- `BlockStructureApi`：捕获、球形预选与平移阻挡裁剪入口；
- `BlockStructureSelectionResult`：不修改世界的结构预选结果；
- `BlockStructureCaptureOptions`：数量、NBT 大小、重力与捕获策略；旧的静止还原开关仅保留作数据兼容；
- `BlockStructure`：技能与非玩家实体共用的稳定操纵接口；
- `StructureRef`、`SpaceBlockRef`：结构身份及固定局部格地址，包含维度、后端和身份代次，复制可变 BlockPos 输入；
- `StructurePose`：double 世界原点与归一化四元数，提供点/方向正逆变换及世界表面速度；
- `StructureAnchor`：局部格、偏移、单位法线和可选格代次；目前只描述锚点，尚不提供运行时查找或拆分重映射；
- `LegacyMotionUnits`：显式转换旧格/tick 与格/秒单位，拒绝非有限值和转换溢出；
- `StructureSpaces`、`StructureBackend`：独立后端入口，以及创建空容器、精确查询、装载和停用；
- `StructureHandle`、`StructureLifecycle`：运行时句柄与 `LOADING`、`ACTIVE`、`UNLOADED`、`RECOVERY_REQUIRED` 状态；
- `StructureWorldAccess`、`StructureBlockData`、`StructureWriteResult`：局部格读取、完整 BE 数据的防御复制和服务端单格比较后写入；
- `StructureHit`、`StructureQueries`、`StructureContexts`、`SpaceContext`：逐格查询、命中重验和显式空间上下文；查询本身不授予操作权限；
- `StructureInteractionEvent`、`StructurePlacementEvent`、`StructureMiningEvent`：受限玩家操作的可取消保护入口；采掘区分 `START` 与 `FINISH`，事件中的工具数据为防御副本；
- `StructurePhysics`、`StructurePhysicsRegion`、`StructureMotionState`：有界物理区域、精确结构附加、运动控制与快照；第十批增加保存区域查询、显式恢复及显式放弃物理关联，第十一批增加稳定区域 UUID 与按 ID 选择；
- `StructurePhysicsRegionInfo`：指定宿主的已保存区域描述，含 ID、bounds、关联结构数和整区恢复许可；不是原生句柄或操作授权；
- `StructurePhysicsControl`、`StructurePhysicsControls`：第十三批的有限力/单点 PD 请求与公共纯数学；通过区域控制槽逐子步施力，独立于玩家、技能和原生句柄，已通过本批限定验收；
- `BlockStructureSnapshot`：不可变、原点相对的结构数据；
- `BlockStructureGridAlignment`：纯坐标网格吸附算法；
- `BlockStructureKinetics`：带生命周期句柄的质量感知推进与扫掠碰撞回调；
- `BlockStructurePlacementPolicy`：还原覆盖策略；
- 捕获、还原与结算结果类型：以稳定状态区分成功、保留和需要恢复；结算结果的 `droppedBlocks` 字段保留兼容性，当前结算路径不生成掉落物。

内部实现位于 `org.academy.internal.common.structure` 和
`org.academy.internal.common.world.entity.structure`。`BlockStructureTransferState` 保存尝试写入位置的原方块及 BE 数据、预期写入结果和完成状态，用于当前调用内回滚和失败隔离。客户端渲染器逐块提交原版移动方块模型。

## 快照实体生命周期和不变量

1. 调用方在服务端选定位置，并提供纯函数式捕获策略。
2. 系统去重、排序并验证所有位置、区块、方块和方块实体数据上限。
3. 快照与 BE 保存/加载往返校验成功后，先让隔离中的结构实体通过生成事件，再核对来源方块和 BE 数据是否仍与快照一致，然后无掉落地移除来源。生成被取消时不移除来源；回调修改来源时拒绝陈旧快照。
4. 能力逻辑通过 `BlockStructure` 设置速度、冲量、重力和水平姿态；新增 `reference()`、`pose()`、`velocityPerSecond()` 可直接读取旧实体的空间契约表示。
5. 现有停止判定仍会尝试最近网格姿态结算，不要求落地。失败后延后再次尝试；这不是新后端设计中的持久刚体睡眠。
6. `settleToGrid` 使用整体还原：受阻、越界或未加载时返回 `RETAINED` 并保留完整结构，不再逐格转成普通物品。`beginGravitySettlement` 整批接纳自然落方块、写入固定格后才撤销源结构；任一阶段失败则撤销已接纳的落方块并回滚固定格，BE 格不进入原版落方块路径。
7. 提交前校验所有写后状态。回滚先整批核对预期结果，全部恢复后再核对写前状态；若回调已转移库存或改变目标，不盲目覆盖世界或重新激活旧快照。回滚完成后，失败的还原保留原结构，失败的捕获撤销候选实体。回滚失败则保留 `pendingTransfer`，返回适用的 `RECOVERY_REQUIRED` 状态，结构保持不可移动、不可选中、无碰撞的隔离状态。

放置阶段先抑制 `onPlace` 副作用，在整个结构的内容归属提交后再补发放置回调与邻居更新。例如雕刻南瓜放在雪块上可能消费方块并生成雪傀儡，这种已提交的世界效果不能再通过恢复旧快照补回，否则会同时保留新实体和旧材料。提交后通知失败应记录为通知失败，不触发整份载荷重新生成。

关键不变量：

- 服务端是世界数据和物理状态的唯一权威；
- 单个快照最多 4096 方块，默认捕获上限为 256；
- 快照坐标相对其最小角，且不能重复；
- 默认不能捕获不可破坏方块，调用方可通过策略进一步收紧；
- 默认还原只允许写入空气，显式传入策略才可覆盖可替换方块；
- `restoreToGrid` 与 `settleToGrid` 失败不会将完整载荷降级为普通方块物品；异常回滚本身也检查写入结果，不能完成时保留待恢复数据；
- `pendingTransfer` 将完整写前/写后数据及执行状态随实体保存，重载后继续隔离，不自动重放或重新激活完整快照，以免覆盖外部修改或复制部分已写入的库存；
- 捕获具有可配置 BE 数据预算，快照格数、碰撞形状数及速度具有限额，网络解码再次验证格数和形状数；统一所有入口的数据预算仍属后续工作；
- 当前只支持绕 Y 轴旋转，还原时使用原版 `BlockState.rotate` 同步方向属性。

这里的写入前数据不是已实现的持久崩溃原子日志。源区块、实体和目标区块仍可能分开落盘；本批次没有跨文件提交协议、启动恢复协调器或自动修复隔离实体的管理入口。保存/加载 pending 数据只能证明恢复材料得以保留，不能证明进程在任意提交点崩溃都不会复制或丢失数据。

## 调用示例

```java
var result = BlockStructureApi.captureConnected(
        serverLevel,
        selectedBlock,
        BlockStructureCaptureOptions.defaults()
);
result.structure().ifPresent(structure -> {
    structure.setVelocity(lookDirection.scale(1.4).add(0.0, 0.35, 0.0));
    structure.setGravityEnabled(true);
});
```

已有快照也可以通过 `BlockStructureApi.spawn` 直接生成结构实体，不会读取或移除当前位置的世界方块，适合能力技能生成预定义结构。

`structure.pose()` 将旧实体绕快照中心的 yaw 转为固定局部原点的世界姿态，调用方不必自行补偿 pivot。其四元数输入和输出不共享可变状态。`structure.velocity()` 与已有推进 API 仍使用旧 tick 单位；只有显式调用 `velocityPerSecond()` 或 `LegacyMotionUnits` 才进行转换。速度换算系数为 20，加速度为 400；现有每方块质量模型和技能力值没有在本批次重新标定。

精密操作节点应组合这一公共入口，而不是重新实现方块移除、NBT 保存或还原流程。非玩家实体也可直接持有 `BlockStructure`，冲量按当前“每方块一质量单位”的质量模型换算速度。

持续推进使用 `BlockStructureKinetics.propel`。调用方提供每 tick 的世界坐标力、控制实体、持续时间、速度上限、单目标碰撞冷却和效果回调；基础层负责按质量换算速度、执行高速扫掠检测并报告碰撞点、法线和闭合速度，具体技能只实现伤害、击退与资源规则。

高速喷流的直接结构发射以准星所指喷口的支撑方块为表面中心，沿推动方向向结构内部平移一格作为球心，使用固定半径 2 的整数网格球（最多 33 个候选位置）。因此最小范围完整覆盖以支撑块为外表面中心、向内延伸的 3×3×3 区域，同时保留球形外缘。发射方向取喷口喷射方向的反向。捕获前会沿推动方向递归裁剪被外部非空气方块紧邻阻挡的候选列；只有仍包含喷口支撑方块的可动部分才会实体化。位于被捕获方块上的自有喷口会转挂到结构实体，随结构移动，并在结构恢复后重新附着到对应方块面。精密操作的同名行动复用相同逻辑，其 `radius=2..8` 配置球形选区大小，方向输入用于选择反向推力与之相符的支撑喷口。结构实体还可以像其他可选实体一样安装普通喷口并被反向推力发射，也会被其他喷口射流、气流喷射、气动抓取、拒绝之风、顺风场、涡流牵引及精密操作的通用实体推动节点控制。

普通实体喷口也支持弹射物。箭等射弹可安装喷口；激活时会清除箭的入地/静止状态并保留原所有者，以喷口反向推力重新进入飞行。`Shift+H` 瞄准已安装自有喷口的弹射物时仅激活该弹射物的喷口，瞄准方块时仍执行结构捕获发射。

## 快照实体当前交互模型

结构会在捕获时保存各方块的原版碰撞形状，并在每个 16×16×16 局部分区内合并连续碰撞盒。单方块最多保存 64 个碰撞盒，单结构最多保存 16,384 个，超过上限时捕获会以 `COLLISION_DATA_TOO_LARGE` 失败且不改动世界。实体碰撞查询使用旋转后的复合体素形状；外包框只负责空间索引、选择和渲染裁剪。顶部承载也复用同一份复合碰撞数据，因此实体可以站在结构内部的楼板或台阶上，而不再局限于结构最高处。移动结构以扫掠碰撞检测命中实体，基础伤害和击退由公开的 `BlockStructureKinetics` 质量/速度函数计算并带单目标冷却；高速射流可以在此基础上叠加技能威力与资源规则。

实体碰撞接入不再在 HEAD 取消整个原版查询：仅包裹我方结构外包框的提交，将其替换为复合形状，在返回阶段补入空间索引找到的遗漏结构并去重。普通实体和原查询结果继续走原有路径；这减少了与其他模组注入互相覆盖的风险，具体混装组合仍需运行验证。

传送系统的单体、区域和精密操作入口均使用通用 `Entity` 传送，因此可直接移动结构。结构碰撞伤害把结构本身保留为直接伤害源：矢量反射/折射能够识别其运动方向，取消原命中、终止原推进会话，并把整个结构沿反射或偏移方向重新定位和赋速。

当前仍有以下限制：

- 任意非 90 度水平角度会把每个合并盒转换为保守的轴对齐包围盒；网格朝向下是精确体素碰撞；
- 结构平移使用复合碰撞盒逐段与世界方块、世界边界求解；合并盒顺序固定以保证服务端结果可复现；
- 结构旋转本身不会对周围实体产生扫掠推力，仅平移会搬运各层顶面实体；
- 方块实体 NBT 会完整保存，但实体化期间不 tick、不开菜单，也暂不提交方块实体专用渲染；
- 独立流体方块和依赖方块实体专用渲染的模型可能在实体化期间不可见。

## 独立真实区块容器原型（P0 第二批）

### 存储与边界

`academy:native_level` 后端使用数据包静态声明的四个空维度：`academy:structure_space_0` 至 `academy:structure_space_3`。每个结构永久独占一个 `ServerLevel`；它们采用 `minecraft:overworld` 维度类型和无层、无结构、无装饰的 flat 生成器，不与宿主或另一结构共享远处 plot。此版本没有运行时注册维度或容量扩展能力，一个存档最多分配四个身份，停用不会释放槽位。

每个容器允许的局部 X/Z 为 `[-32, 31]`，Y 按实际存储 level 的最小构建高度到最大构建高度（含端点）校验。局部坐标固定，允许负坐标；`StructureRef.dimension` 表示可见宿主维度，公开 API 不暴露存储维度或原始 `LevelChunk`。姿态变化只更新局部到宿主的变换元数据，不改变存储格子，也不会使方块在宿主中出现或运动。

对四个保留维度，`Level.setBlock(BlockPos, BlockState, int, int)` 拒绝边界外写入；对应的 `getBlockState`、`getFluidState`、`getBlockEntity` 分别返回屏障、空流体和 null。普通维度保持原行为。这里的虚拟屏障约束这些 Level 入口及经它们访问的邻居/流体逻辑，不能视为实体物理围墙，也不能阻止任意模组直接调用 `LevelChunk`、直接注册实体或绕过这些方法。

传送事件拒绝玩家和普通实体进入四个保留维度；原版传送命令和门户最终经过这一事件。已经被旧存档记录在这些精确维度中的玩家，登录时尝试回到主世界安全出生位置，不修改背包；恢复被其他逻辑拒绝或抛错时记录失败。直接 `addFreshEntity`、`summon`、实体越界及跨空间归属迁移不在本批保护范围内。实际搭建门户和真实玩家重登录仍须单独验证。

每个活动槽为 X/Z 区块 `[-2, 1]` 的 16 个区块持有可移除的 `ENTITY_TICKING` 票据，标志为加载、模拟和保持维度活动（14），不使用持久强加载票。原版票据等级会使外围区块参与加载或模拟，因此 16 是直接持票数量，不是所有驻留区块的硬上限。此实现不修改宿主的世界边界或全局游戏规则。

### 公共生命周期与内容操作

使用 `StructureSpaces.backend(server)` 获取当前服务器后端。接口面向技能、精密操作和非玩家调用者，不要求实体或玩家；所有操作必须在所属服务端线程进行。

| 操作或状态 | 本批行为 |
| --- | --- |
| `createEmpty(hostLevel, pose)` | 只创建空容器，不捕获或删除宿主方块；分配前检查空内容并永久认领槽位，实体加载完成后再次验空，发现旧内容则隔离 |
| `resolve(reference)` | 只查找已经加载的精确身份，不创建内容、不增加票据；调用方仍需检查生命周期 |
| `load(reference)` | 校验 UUID、宿主维度、后端、代次、owner 和恢复记录；装载成功先返回 `LOADING` 句柄 |
| `LOADING → ACTIVE` | 等待全部持票区块与其实体加载就绪；只有 `ACTIVE` 可读取内容、修改姿态或写格 |
| `unload(reference)` | 保存原版区块/实体与索引，停用、撤票并永久作废旧句柄；不删除内容、不回收槽位 |
| 重新 `load` | 保留原 `StructureRef`，返回新的运行时句柄；旧句柄及其 `world()` 访问对象不会重新生效 |
| `RECOVERY_REQUIRED` | 禁止普通内容访问和变更，冻结世界运行并保留恢复证据；不自动重放或重新激活 |

`StructureWorldAccess.read(localPos)` 返回实际方块状态和脱离世界的完整 BE 数据副本。`compareAndSet(localPos, expected, replacement)` 只有在当前 state 和完整 BE NBT 都等于预期值时才写入；结果区分 `APPLIED`、`CONFLICT`、`REJECTED`、`OUT_OF_BOUNDS`、`NOT_READY` 和 `RECOVERY_REQUIRED`。前后镜像验证沿用 `BlockStructureTransferState`，含 BE 的写入先校验保存/加载往返；失败只能在确认可安全回滚时返回拒绝，否则隔离。单格写入对写前和写后 BE 各限制为 256 KiB，恢复文件仍有独立的大小限制。

这是受信服务端内容操作，不是玩家放置或挖掘协议：调用方负责权限、工具、物品消耗和掉落。第二批本身没有菜单、采掘进度、客户端写入包或多格事务；第四至六批另建受限玩家适配，不能用 CAS 绕过其规则。提交后的 `onPlace` 与邻居更新允许原版副作用继续改变方块；`APPLIED` 不保证该格在回调或后续 tick 后仍等于 replacement，回调异常也不能通过重建旧库存来补偿。

### 运行、保存与故障边界

已激活容器通过自己的真实 `ServerLevel` 运行原版 BE、红石、block/fluid 计划 tick 和局部实体，并由原版区块及实体存储链保存。票据使它们在没有玩家进入时也能模拟；这不等于所有第三方机器、随机 tick 或跨维度依赖都已适配。`LOADING` 也允许 level tick，以完成实体加载。没有可运行句柄、停用、隔离或已锁存 I/O 失败的槽在 `ServerLevel.tick` 入口暂停整次世界 tick；这是停用/冻结机制，不保证区块和实体立即从内存驱逐，也不阻止外部模组直接修改该 level。

上述暂停指不执行本容器的逻辑 tick，不冻结原版共享的 `gameTime`。停用期间熔炉等 BE 状态不推进，计划 tick 保留在队列中；重新装载后，已经超过触发时刻的计划 tick 可以立即执行，与普通区块暂停模拟时的时间语义一致。独立逻辑时钟和技能时停映射尚未实现。

索引位于存档的 `data/academy/native_structures/metadata.dat`，记录身份、槽位、宿主、姿态及 active/quarantined 标记；内容使用各维度的原版区块与实体文件。`owner_N.txt` 永久认领槽位，即使分配中断或索引缺失也不自动复用。损坏、未知版本或无法安全读取的索引会禁止继续分配和装载，不覆盖成空索引。`recovery_N.dat` 记录恢复证据，其存在本身阻止重新激活。元数据和恢复文件使用有界 NBT、完整往返校验及临时文件原子替换，但不与原版文件形成共同事务。

正常停服后，健康且 active 的已保存结构在启动时重新装载；inactive 结构需显式 `load`。正常重启验证必须在同一存档进行两次独立服务器启动，比较身份、槽位、姿态、完整库存、实体和后续 tick 行为，不能用同一次进程内的 unload/load 代替。当前验证状态见下表。

`MinecraftServer.reportChunkLoadFailure` 和 `reportChunkSaveFailure` 对四个存储维度报告的 I/O 错误会按服务器/槽位锁存；报告来自异步线程时也只登记维度，不在那里访问世界。该槽在本次服务器会话内不能通过再次装载解除失败；运行时禁止继续活动并尝试持久化隔离，原版诊断仍照常执行。普通维度报告不会污染结构槽。这只覆盖原版明确报告的错误：解析器静默跳过无效 BE/实体、缺失注册项或吞掉字段等不一定进入报告入口，尚无完整内容校验和、原版数据损坏审计或自动修复器。I/O 锁存本身在服务器会话结束后清理，跨重启拒绝依赖成功保存的 quarantine/recovery 记录，不能夸大为任意存储故障后的永久保证。

第二批未实现宿主与容器之间的捕获/还原迁移、移动中的放置/破坏/菜单、空间事件转换、渲染/网络、刚体、Sable 桥或旧技能迁移。索引、BE/实体保存和恢复证据也不构成跨文件崩溃原子提交；硬终止、断电、磁盘损坏和原版静默解析丢弃不属于“正常重启已支持”的等价场景。

## 宿主投影与逐格命中（P0 第三批）

活动容器的模型通过独立 `NativeStructureProjectionEntity` 呈现在宿主世界。投影不保存、不持有方块归属、不承担碰撞、重力或结算；移除投影不会丢弃或还原容器内容。姿态与内容分开同步，姿态使用现有 double 原点和单位四元数，渲染与查询使用相同的当前姿态，尚未加入网络插值。第八批另由服务端物理区域发布结构姿态，再沿此投影链同步；投影实体仍不是刚体或内容所有者。

模型视图只包含 `BlockPos + BlockState`，不会发送完整 BE NBT 或库存。最多 4096 格，支持无方块实体的普通 `MODEL` 状态；方块实体目前仅允许原版熔炉、高炉、烟熏炉、漏斗、发射器与投掷器的状态模型。26.2 的箱子等专用模型也可能报告 `MODEL`，因此不能仅凭这个标记认定可呈现。存在流体、未支持的方块实体、非模型方块或超过上限时，整个投影隐藏，容器和内容继续保留。公共 `StructureSpaces.presentationStatus` 区分空内容、不支持内容、超限、宿主不可用和已显示。投影实体以可见内容中心作为跟踪位置；模型仍按结构原点变换，避免局部坐标较大时在错误区段被裁剪。未加载宿主中心区块时不为投影强制加载世界。

初始视图扫描跳过空 section，后续从 `LevelChunk.setBlockState` 收集实际变化格，覆盖原版机器、红石与计划 tick。脏格集合最多 8192 项，超过时转一次全量扫描；超过视图上限的扫描及时停止。当前状态变化仍同步完整有界视图，没有分片、单格网络差量或 BE 专用渲染。

`StructureQueries.raycast(host, from, to)` 返回 `StructureHit`，记录结构局部格、局部面、局部/宿主命中点、姿态、运行期 UUID 与几何版本。查询逆变换射线并使用实际原版 `OUTLINE` 形状，排除容器外围的虚拟屏障，处理宿主与其他结构遮挡；它不保证结构已呈现，也不授予任何操作权限。宿主查询遇到未加载区块会拒绝，不为射线加载区块。每次激活产生新的 `runtimeId`；姿态、API 写入或原生区块状态变化会使旧命中失效，持久引用保持不变。

`StructureContexts.call/run` 提供仅当前线程、最多 16 层的 `SpaceContext`，异常退出也会恢复外层上下文；它不修改玩家所属世界、位置或朝向。新调用方可用公共空间引用与内容访问接口集成精密操作或非玩家逻辑。

本批唯一接入的玩家操作为**双手空闲时右键原版拉杆**。客户端单独查询结构命中，服务端使用真实玩家的眼睛、朝向和交互距离重新计算；再检查结构激活/呈现版本、方块与实体遮挡、建造资格、宿主保护/世界边界，以及时停和精神控制门禁。公开可取消的 `StructureInteractionEvent` 携带真实玩家和空间上下文；事件后再次校验，防止监听器改变姿态、内容或玩家。拉杆调用原版容器内 `pull`，红石更新留在容器内，点击声转到宿主。动作期间禁止 API 写入、保存、卸载与姿态更改重入；回调异常会保留数据并隔离，不尝试撤销已经发出的副作用。

这个专用事件不等价于所有原版 `PlayerInteractEvent` 保护插件已兼容。第四至六批分别增加普通单箱、受限放置和受限采掘适配；通用物品、菜单和第三方交互仍需逐项处理，不能把局部命中直接交给绑定宿主玩家的原版交互包。动能投掷、高速喷流与磁场方块路径已迁移到公共结构服务，Sable 生产捕获也已实现（`SableStructureTransactions.java:47`，由 `StructureBackendRouter.java:94` 路由）；混装仍受完整生命周期、跨后端迁移和真实多人异常矩阵等未验边界限制，不能把限定烟测写成完整兼容。

## 后续阶段

后续按[完整设计方案](D:/mcmodtest/AcademyCraft-Reborn-26.2/docs/block_structure_physics_and_sable_compat_plan.zh-CN.md) 推进，当前未完成项包括：

1. 第九批已通过移动共享单箱、竞争放置/采掘及有界延迟的真实双客户端场景；继续补齐迟加入、断线重连、高转速边界等尚未覆盖的部署与网络矩阵。
2. 持久交易协调、明确的隔离数据恢复入口，以及跨存储崩溃恢复验证。
3. 补齐通用原版放置、破坏、菜单、BE 专用呈现、局部实体与流体边界，以及分区同步/渲染闭环；扩展容量和回收协议须先处理永久槽的内容归属。
4. 第十批已完成区域与运动检查点、显式恢复及本批验收；第十一批同宿主多区域的已加载区块独立调度也已通过限定场景和最终回归。第十二批保守地形缓存已通过限定场景和最终回归；有限力控制随后推进，主动加载、材料密度、旋转承载和拆分仍需扩展，高速与旋转扫掠继续专项验证。全方向网格还原仍需方块及 BE 旋转适配。
5. Sable 可选桥、跨后端迁移，以及喷口、磁力、反射、传送、时停和精密操作向公共服务迁移。

## 验证清单

- 单元测试：角度吸附、四向坐标旋转、矩形结构整体网格平移、姿态正逆变换、四元数复制、局部锚点、单位换算和碰撞查询组合；
- GameTest：`academy:block_structure_safety`，覆盖库存捕获/保留/还原、生成取消与来源变更、失败写入回滚、落方块批次取消、pending 保存加载后持续隔离，以及自然落方块与箱子混合正常结算、雪傀儡放置副作用不复制材料、写后库存外移时拒绝回滚复制；
- 构建：开发与发布变体；
- 游戏内：含方块实体结构的捕获/重载/还原，水平与垂直碰撞，玩家站立搬运；静止后结算、占位时完整保留、解除阻挡后重试；喷口/气流技能推动、箭喷口再发射、单体与区域传送、碰撞伤害和矢量反射/折射；
- 兼容：活塞、流体、跨区块边界、受保护区域和卸载边界由调用策略与集成测试逐项确认。

## 第一批验证记录

2026 年 10 月 12 日使用 JetBrains Runtime 25 和仓库 Gradle wrapper 完成以下检查，最终均通过：

| 检查 | 命令或配置 | 结果 |
| --- | --- | --- |
| 单元测试 | `./gradlew test -DisDev=true` | 2319 项测试，零失败、零跳过 |
| 真实服务端 | `./gradlew runGameTestServer -DisDev=true -PacademyGameTests=academy:block_structure_safety` | 1 个注册 GameTest，内部九类场景全部通过 |
| 隔离客户端 | `./gradlew runClientDev -DisDev=true --init-script build/block-structure-client-smoke.init.gradle` | `BLOCK_STRUCTURE_CLIENT_SMOKE_PASSED`；完整 NBT、碰撞、阻挡保留、7 钻石还原、实体移除和无掉落检查通过 |
| 开发构建 | `./gradlew build -DisDev=true` | 成功，包含项目配置的检查任务 |
| 发布构建 | `./gradlew build -DisDev=false` | 成功，包含项目配置的检查任务 |
| 文档与改动格式 | `git diff --check`、文档本地链接核对 | 通过 |

客户端检查由显式系统属性 `academy.blockStructureSmoke=true` 启用，使用 `run/block-structure-client-smoke-20261012` 下的隔离世界。临时 init script 将 `clientDev.gameDirectory` 指向该目录，并加入 `--quickPlaySingleplayer "Block Structure Smoke"`；它与测试世界、日志均位于忽略的 build/run 目录，不能依赖其存在于新检出中。重新执行时须先准备独立测试世界，游戏目录名须以 `block-structure-client-smoke` 开头。检查等待实体状态与方块更新完成同步后再验证稳定状态，并有超时限制。

第一批客户端验证检查了快照实体同步、碰撞和渲染器注册，没有证明移动箱子的专用渲染、菜单或逐格交互已经实现。真实 Sable/航空学混装、技能全链路人工检查、独立真实区块原型、完整刚体及跨存储崩溃恢复均未纳入第一批通过结论。

## 第二批验证记录

2026 年 10 月 12 日使用 JBR 25 对本批最终代码完成以下验证；第一批结果未代替新增实现的检查。

| 检查 | 命令或场景 | 结果 |
| --- | --- | --- |
| 单元测试 | `./gradlew test -DisDev=true`；公共值类型、Manifest 编解码、错误输入与 owner/recovery 约束 | 2333 项，零失败、零错误、零跳过；其中新增 14 项 Manifest 测试 |
| 真实容器 GameTest | `./gradlew runGameTestServer -DisDev=true -PacademyGameTests=academy:native_structure_space`；双空间隔离、负坐标、完整 NBT CAS、真实熔炉/漏斗/红石、边界、传送命令与卸载重载 | 1 个注册测试通过；无人状态持续 550 tick，停用 60 tick 后机器与待执行 tick 保留，重载继续 |
| 数据安全回归 | `./gradlew runGameTestServer -DisDev=true -PacademyGameTests=academy:block_structure_safety` | 1 个注册测试内九类现有场景通过 |
| 正常重启 | 同一测试存档两次独立 JVM；自动激活、身份/槽位/姿态、完整库存、实体及恢复后 tick | write/read 均通过；7 钻石、1 个铁矿物料守恒，盔甲架 UUID/属性一致且数量为 1，原生未来 tick 执行完成 |
| I/O 失败门禁 | 原版 load/save report 入口、异步报告、普通维度不污染、拒绝分配失败槽 | 容器 GameTest 通过；注入报告而非实际损坏磁盘，三条预期错误由 `NATIVE_STRUCTURE_SYNTHETIC_IO_REPORTS_BEGIN/PASSED` 标识 |
| 隔离客户端 | `runClientDev` 两进程 native smoke；真实普通世界加载新增维度/Mixin | 通过；未重复旧 snapshot 客户端探针，旧路径由安全 GameTest 回归；不证明新容器移动呈现 |
| 开发构建 | `./gradlew build -DisDev=true` | 成功，包含项目配置的检查任务 |
| 发布构建 | `./gradlew build -DisDev=false` | 成功，包含项目配置的检查任务 |
| 文档与改动格式 | `git diff --check`、本地链接与实现范围核对 | 通过 |

最终跨进程测试使用 `run/native-structure-client-smoke-20261012-final`，write PID 为 56180，read PID 为 68424。两次记录的结构 UUID 都为 `160e10f7-3777-44d1-8e89-a6416b14c3ab`，局部盔甲架 UUID 都为 `fc9f5cff-db02-4b76-b6f6-75d9b7cfe854`。read 观察到保存的熔炉进度从 40/200 继续，未来 tick 剩余 996 tick，最终灯熄灭、计划队列清空、1 个铁锭和 7 个钻石均保留。日志及验证清单位于 `build/native-structure-final-*.log`、`build/native-structure-client-final-write.log` 和 `build/native-structure-validation.json`。

真实门户、保留维度中的玩家重登录、实际磁盘损坏/断电、Sable/航空学混装及移动空间交互仍待对应阶段验证，本表不覆盖这些场景。

### 重跑入口

容器 GameTest 使用 `src/test/gametest_packs/native_structure_fixture`。原版 GameTestServer 从 flat preset 建立测试世界，会忽略普通数据包维度条目；Gradle 仅为 GameTest 运行添加这个测试 pack，保留原版三维度并补齐四个容器维度。它不会覆盖普通客户端或专服的 flat preset。

跨进程检查使用已入库的 `validation/native_structure/run.init.gradle`。先在仓库 `run/native-structure-client-smoke-自定名称/saves/Native Structure Smoke` 准备一次性测试世界；可复制已退出游戏的测试存档，不使用正在游玩的世界。要求至少一个未分配槽，且没有本次探针的 `academy-native-structure-smoke.nbt`。脚本会校验目录和世界名称，拒绝覆盖已有验证证据。首次出现原版实验设置提示时，可为这个测试副本创建备份并加载。

按顺序运行下列两个独立命令，并等待前一个客户端正常退出。两次使用相同目录；JBR 25 和 `JAVA_HOME` 要求与仓库其余验证一致。

```powershell
.\gradlew.bat runClientDev -DisDev=true --init-script validation/native_structure/run.init.gradle -PacademyNativeStructureSmoke=write -PacademyNativeStructureSmokeDirectory=run/native-structure-client-smoke-自定名称
.\gradlew.bat runClientDev -DisDev=true --init-script validation/native_structure/run.init.gradle -PacademyNativeStructureSmoke=read -PacademyNativeStructureSmokeDirectory=run/native-structure-client-smoke-自定名称
```

两个进程分别记录 `NATIVE_STRUCTURE_SMOKE_WRITE_PASSED` 和 `NATIVE_STRUCTURE_SMOKE_READ_PASSED`。证据包含不同的进程 ID/启动时间、结构引用、姿态、完整箱子 NBT、尚未完成的真实熔炉、HIGH 优先级未来计划 tick，以及局部盔甲架的 UUID、位置、自定义名和无重力状态。read 阶段必须依靠启动自动激活，之后观察铁锭产出、红石灯熄灭、队列清空和单个实体保持，再保存停用。世界、备份、证据和日志均留在被忽略的 run/build 目录，不进入 Git。

## 第三批验证记录

2026 年 10 月 12 日使用 JBR 25 验证。真实测试发现并修复了两项问题：26.2 的 `MODEL` 标记不能区分箱子专用部件；实体局部原点所在区段可能遮蔽实际可见的远处结构。测试保留对应断言，未通过减少检查范围处理失败。

| 检查 | 实际证明范围 | 结果 |
| --- | --- | --- |
| `test -DisDev=true` | 上下文异常/嵌套/线程隔离、任意姿态编解码、命中包信任边界、视图网络上限；含原有测试 | 2350 项，零失败、零错误、零跳过；比第二批增加 17 项 |
| `academy:native_structure_query` | 旋转楼梯真实空隙、任意轴四元数和负格坐标、宿主/双结构遮挡、原生状态即时版本、pose/运行期身份过期；脏格视图刷新、真实箱子/流体隐藏保留、EMPTY 恢复、4096/4097 格保留与恢复 | 最终代码通过 |
| `runClientDev` 投影探针 | 真实玩家建造资格、保护取消、监听器改变姿态、写入重入拒绝；空手客户端入口实际发包、拉杆/灯和熔炉状态同步、旧命中包重放；任意姿态模型截图、卸载后无残影且完整炉内 NBT 保留 | 通过；截图已人工查看，五个模型可见 |
| 原容器与旧数据安全回归 | `academy:native_structure_space`、`academy:block_structure_safety` | 两个注册 GameTest 通过；原 I/O 故障注入与九类旧安全场景均通过 |
| 开发/发布构建 | `build -DisDev=true`、`build -DisDev=false` | 两种配置均成功，包含各自检查任务 |

最终客户端使用 `run/native-structure-projection-smoke-20261012-final`，进程 65156，结构 UUID `b2b1a310-d89b-45b3-9b5e-8a578f5e4c3d`。`NATIVE_STRUCTURE_PROJECTION_SMOKE_PASSED` 记录实际发包、旧包重放、模型帧提交、卸载和炉内 3 单位矿物料守恒；图片为该目录 `screenshots/native-structure-projection-65156-1791802361997.png`，已核对旋转后的点亮熔炉、拉杆、红石灯、石块与楼梯。日志在 `build/native-structure-p3-*.log`，顺序验证清单为 `build/native-structure-p3-validation.json`。这些运行产物不进入 Git。

第三批未重新执行第二批的双 JVM 持久化探针；本批没有修改 manifest/vanilla 存储格式，采用原容器 GameTest 回归保存/重载。真实多人延迟、第三方保护插件、Sable 混装、完整放置/采掘/菜单、刚体及运动承载仍待验证和后续实现。

### 第三批重跑入口

查询/呈现 GameTest 用独立存档，占两个永久槽；通过 init script 显式启用，普通未筛选 GameTest 不注册此测试，避免与存储故障注入用例共享四个有限槽。测试脚本复用专用 fixture，不删除世界或回收 owner 标记。

```powershell
.\gradlew.bat runGameTestServer -DisDev=true -PacademyGameTests=academy:native_structure_query --init-script validation/native_structure/query.init.gradle
```

客户端先在 `run/native-structure-projection-smoke-自定名称/saves/Native Structure Projection Smoke` 准备已退出游戏的一次性存档副本，保留至少一个未分配槽，玩家双手空闲、允许建造且非旁观者。探针仅在显式属性开启并通过路径守卫后运行，使用一个专用槽，改变该测试玩家的飞行状态及宿主内位置以控制镜头；不会传送玩家到容器或重写玩家库存。不要将实际游玩存档改名作为测试副本。负例要求拉杆与灯初始关闭，成功完成后保留的开启状态不能直接作为下次测试输入；重跑应从原始测试前副本新建隔离目录。

```powershell
.\gradlew.bat runClientDev -DisDev=true --init-script validation/native_structure/projection.init.gradle -PacademyNativeStructureProjectionDirectory=run/native-structure-projection-smoke-自定名称
```

探针等待真实状态同步和至少 20 个模型提交帧，再保存 PNG、验证卸载与内容保留并正常关闭。出现原版实验设置提示时，对这个副本备份并加载即可。检查必须见到 `NATIVE_STRUCTURE_PROJECTION_SMOKE_PASSED`，且实际打开 PNG 确认模型可见；不能仅凭 Gradle 编译或单纯截图文件存在认定呈现成功。

## 第四批：原版普通单箱

这一批沿用独立容器内容所有权，增加普通、未含水、`ChestType.SINGLE` 的原版箱子。客户端复用原版 `ChestModel`、朝向、材质及开合曲线；投影仅增加有界的开盖位置集合，最坏 4096 个位置占 16386 字节，不包含库存、锁或战利品表。开合动画保存在客户端投影生命周期中，不推动几何版本，也不重发方块视图。

空手右键沿已有旋转命中和服务端复验通道打开菜单。公共 `StructureInteractionEvent.action()` 区分 `USE_LEVER` 与 `OPEN_CONTAINER`，原三参数构造保持拉杆语义。初次打开继续校验建造资格、世界保护、遮挡、原版箱盖阻塞及锁；未展开的战利品表本批明确拒绝，不自动生成内容。钥匙持物开箱、双箱、陷阱箱、末影箱和模组容器不在本批支持范围。

服务端 `NativeStructureChestMenu` 使用真实 `ChestBlockEntity` 与真实玩家背包，客户端使用原版三行箱子界面和槽位协议。玩家不进入存储维度，也不复制库存给替身。会话绑定结构引用、运行期 UUID、局部格和精确 BE 实例；持续菜单按当前姿态把玩家眼睛逆变换到局部空间，使用原版单位格 AABB 距离与四格容器缓冲。转动或近距离移动不会仅因几何版本变化而关箱；越距、失活、换维度、BE 替换或变为不支持状态会关闭。

菜单持续有效性不要求双手为空，时间缩放的非动作 tick 只拒绝写入。普通点击、快捷转移和收纳袋选择入口持有结构写入锁；拒绝预测点击后全量纠正客户端槽位。广播读取同样受控，回调无法在读取或取放中卸载、保存、CAS 或改变姿态。回调中的关闭和原版换菜单请求延后到处理完成后的服务器 tick，避免原数据包后续更新到另一菜单。

卸载先禁止新会话，再走原版关闭归还鼠标持物，最后保存。会话维护独立于玩家时间倍率。箱子的原版开盖人数重查定点合并宿主世界中的实际使用者，保留计数、方块事件及本地游戏事件；开关声音使用变换后的宿主位置。没有把这种定点适配宣称为任意方块事件、容器或保护插件的完整兼容。

### 第四批验证记录

2026 年 10 月 12 日使用 JBR 25，在未安装 Sable 的开发环境验证：

| 检查 | 实际证明范围 | 结果 |
| --- | --- | --- |
| `test -DisDev=true` | 新增 7 个开盖集合编码边界用例和 3 个旋转容器距离用例，含全部原有测试 | 2360 项，零失败、零错误、零跳过 |
| 三组 GameTest | `academy:native_structure_query`、`academy:native_structure_space`、`academy:block_structure_safety`；普通单箱获准，双箱、含水箱及陷阱箱仍拒绝 | 通过 |
| 单箱真实客户端 | 空手入口真实发包、63 槽同步、快捷取出/放回、右键拆分；鼠标持物时移远和卸载关闭、重载运行期身份变化、旧菜单包拒绝、替换 BE 关闭 | 通过；7 件标记钻石全程守恒 |
| 时间与独立库存入口 | 实体时间倍率为 0 时，真实点击预测被全量纠正，菜单继续存在；真实收纳袋选择包正常时获准，暂停时拒绝 | 通过；测试收纳袋内 1 石块、1 泥土保留 |
| 方块限制与呈现 | 箱盖阻塞、锁定和未展开战利品表拒绝；原版 opener 超过 5 tick 重查仍为 1，关闭归 0；真实模型提交、开盖插值、GUI 和世界截图 | 通过；两张 PNG 已打开查看 |
| 旧逐格交互客户端回归 | 拉杆、红石灯、熔炉、楼梯；保护取消、回调重入拒绝、旧命中包、任意姿态呈现与卸载保留 | 通过；PNG 已打开查看 |
| 开发／发布构建 | `build -DisDev=true`、`build -DisDev=false`，包含对应检查任务 | 均成功 |

单箱测试世界为 `run/native-structure-chest-smoke-20261012`，进程 63240，结构 UUID `b3f3b5b9-1aaa-4761-b084-f340a2457f8f`。真实[箱子界面](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-chest-smoke-20261012/screenshots/native-structure-chest-gui-63240-1791807788472.png)和[旋转箱体](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-chest-smoke-20261012/screenshots/native-structure-chest-world-63240-1791807794326.png)已核对。世界截图中的锁定提示来自此前的拒绝用例；箱子的锁已恢复为空，最终库存保存后停用。

旧场景回归最终使用 `run/native-structure-projection-smoke-20261012-p4-fresh`，进程 4356，结构 UUID `8d752599-90f2-4521-93b3-28fdad922350`。[旋转五格场景](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-projection-smoke-20261012-p4-fresh/screenshots/native-structure-projection-4356-1791808094021.png)通过。首次误用上轮已操作完的存档，因拉杆初始已开启而被探针拒绝；失败存档和 `build/native-structure-p4-projection-fixture-failure.log` 均保留，改用未操作副本重跑，没有放宽断言或清空内容。成功日志在 `build/native-structure-p4-*.log`。

本轮单箱的 unload/load 在同一 JVM 中验证；双 JVM 内容持久化继续采用第二批证据，没有将本轮菜单探针计作新的进程崩溃或重启恢复证明。真实多人同步、断线重连、第三方回调兼容、通用放置采掘、物理运动和 Sable 混装仍属后续验收。

### 第四批重跑入口

先在 `run/native-structure-chest-smoke-自定名称/saves/Native Structure Chest Smoke` 准备已经退出游戏的隔离副本，保留一个空闲永久槽。玩家双手空闲、允许建造且非旁观者；背包需有空闲快捷栏位置。探针只操作专用箱子中的标记测试物品，统计箱内、玩家、鼠标持物及两个世界中的掉落，不清空玩家既有库存。

```powershell
.\gradlew.bat runClientDev -DisDev=true --init-script validation/native_structure/chest.init.gradle -PacademyNativeStructureChestDirectory=run/native-structure-chest-smoke-自定名称
```

必须看到 `NATIVE_STRUCTURE_CHEST_SMOKE_PASSED` 并打开 `screenshots/` 下真实界面及世界 PNG 核对。出现失败时保留世界与日志；不能清空库存后重复探针来制造通过。

## 第五批：旋转结构上的受限逐格放置

本批为独立动态空间增加玩家放置入口，范围是原版精确 `BlockItem` 的石头、圆石、橡木木板、橡木楼梯、橡木台阶和拉杆。状态、BE 和容器组件导入暂不开放；未知物品子类、门床等多格方块、流体、重力块及模组机器需另行适配。已支持的箱子和拉杆在未蹲下时优先交互，蹲下时使用可放置物品；主手为空时可使用副手。客户端命中结构后，未适配物品只走原版空中使用，避免透过结构对宿主方块执行 `useOn`。

使用包明确携带主手或副手，服务端依据真实双手与当前目标重新选择，不接受客户端指定任意手。客户端和服务端需使用本批一致的包格式，未实现旧格式互通。客户端在发送使用请求前发送原版快捷栏选择包，覆盖同一 tick 切换槽位后立即使用的情况。26.2 的原版包通过 `PacketProcessor` 排队，Misaka 包原先通过 `server.execute` 排队，仅按发送顺序调用两个入口仍可能乱序。`MixinC2SPacketNativeStructureOrder` 仅将 `NATIVE_STRUCTURE_USE` 对应的外层包先送入同一个 `PacketProcessor`，随后执行原有解码和派发，使正常网络路径先处理此前的快捷栏、移动及玩家指令包；其他 Misaka 包保持原处理方式。

玩家始终留在宿主世界。专用 `BlockPlaceContext` 使用真实玩家、真实结构存储和局部命中，并逆变换视线提供方向与转角。实际放置调用原版 `BlockItem.place`，保留台阶合并、楼梯形状、拉杆支撑、邻居更新及本地游戏事件。放置声音转换到宿主位置；原版按玩家 Level 读取局部坐标的放置成就暂不触发，不能把宿主同坐标方块当成放置结果。

服务端重新执行真实射线与手选择，校验运行期身份、几何版本、姿态、建造资格、动作 tick、手持物品与目标完整数据。公开的 `StructurePlacementEvent` 提供明确空间的保护入口，回调后再次校验。最后一轮形状回调之后、预留材料之前，再用真实双手和潜行状态确认仍为同一只手的放置；实际写入前继续复验这些快照，避免回调改变主副手优先级。普通 NeoForge `PlayerInteractEvent` 的玩家 Level 仍是宿主，因此本适配不冒充任意第三方保护插件已经兼容。

候选方块使用任意四元数下的有向包围盒分解和 SAT 检查宿主方块、阻挡建造的实体及其他活动结构，保留台阶和楼梯空隙；相切允许。空间、区块加载状态、形状复杂度与检查工作量均有限制，新增格数不能超过当前 4096 格呈现上限。这里是放置前的重叠判定，并非刚体求解器或运动碰撞实现。

生存物品采用单件预留：完成预检后从真实手持堆栈取一件交给原版放置，明确未写入才退回；已发生写入的异常保留内容并隔离结构，不回放回调或覆盖玩家库存快照。这仍不是玩家数据与结构区块跨文件的崩溃原子事务；进程中止、断电与跨后端迁移的完整恢复留在总体计划中。

当前仍为 P0 部分完成。本批没有持续采掘、破坏进度或掉落协议，没有为独立空间增加刚体运动、碰撞求解或乘载，也没有将旧技能迁移到该空间；Sable 桥与混装适配尚未实现。旋转静态结构上的受限放置通过，不代表运动期间的完整原版操作或任意物品放置已经完成。

### 第五批验证记录

2026 年 10 月 12 日使用 JBR 25、未安装 Sable 的环境验证。第四轮隔离客户端运行通过；补充预扣前策略复验后，第六轮以最终放置代码再次完整通过。以下区分真实客户端发包和同一集成服务器内的直接调用。

| 检查 | 实际证明范围 | 结果 |
| --- | --- | --- |
| 真实客户端主手放置 | 先以空槽稳定视线，再于同一客户端回调切换楼梯槽并立即调用使用入口；在任意轴旋转结构上放置局部朝北、下半部楼梯 | `APPLIED`；生存楼梯 2→1，副手台阶未消费 |
| 真实客户端副手放置与合并 | 主手空槽，副手放下半台阶，再向同一局部格合并为双台阶 | 两次 `APPLIED`；副手材料 2→1→0，上方未多放一格 |
| 真实客户端拉杆 | 放置依附局部地面的朝北拉杆，再持拉杆普通使用已放置拉杆 | 放置与切换均通过；互动优先，第二次没有额外消费 |
| 旧包与主链守恒 | 重发首次放置的旧命中包；统计已放置方块、全部标记库存及宿主／存储世界掉落 | 旧包 `STALE`；全程 8 单位守恒、无标记掉落 |
| 服务端直接保护负例 | 取消事件、回调切换真实手持槽、回调改变原始支撑格；事件中尝试保存、卸载、CAS 和改变姿态 | 全部拒绝放置且未消费；重入操作被拒绝 |
| 服务端直接碰撞负例 | 石头／楼梯与阻挡建造的真实玩家相交；宿主石头与候选石头／拉杆相交；第二活动结构占据候选位置 | 均拒绝；空碰撞形状的拉杆仍允许与玩家重叠 |
| 写入后异常守恒 | 第三结构成功写入石头后，宿主放置声监听器主动抛错；随后重放原请求并尝试卸载 | 方块保留，库存 2→1，合计仍为 2；结构进入 `RECOVERY_REQUIRED`，重放和卸载拒绝，无补发物品 |
| 保存、重载与呈现 | 主结构同 JVM 保存、卸载、重新加载；比较楼梯／双台阶／拉杆状态、运行期身份及材料数，等待实际模型提交后截图 | 通过；最终卸载无投影残留，PNG 已打开查看 |
| 普通单箱客户端回归 | 真实取放／拆分、移动与卸载关闭、运行期身份与旧菜单包、BE 替换、时间暂停纠正、收纳袋入口、开盖与截图 | 通过；7 件标记钻石守恒，两张 PNG 已打开查看 |
| 旧投影客户端回归 | 建造资格、保护取消、回调重入、姿态变化拒绝、空手真实发包、旧包、拉杆／红石灯／熔炉、旋转呈现和卸载 | 通过；3 单位矿物料保留，PNG 已打开查看 |
| `test -DisDev=true` | 新增 8 项 OBB 几何、3 项局部朝向、5 项主副手策略和 2 项手字段编码测试，含原有测试 | 2378 项，零失败、零错误、零跳过 |
| 三组既有 GameTest | `academy:native_structure_query`、`academy:native_structure_space`、`academy:block_structure_safety` | 最终代码重新运行，三个注册测试均通过，包含原有存储故障与旧安全场景 |
| 开发／发布构建 | `build -DisDev=true`、`build -DisDev=false`，包含各自检查任务 | 最终代码均成功 |

最后一处手选择修正只涉及放置分支；本批箱子及空手投影成功结果复用于各自未变的交互分支，放置探针、JUnit、两种构建与三组 GameTest 均在此修正后重新执行。最终顺序记录为 `build/native-structure-p5-validation.json`，源码哈希清单为 `build/native-structure-p5-source-manifest.json`；日志为 `build/native-structure-p5-*.log`，运行产物不进入 Git。

最终成功世界为 `run/native-structure-placement-smoke-20261012-r6`，进程 51116，主结构 UUID `df0d8ac5-8531-4647-b255-2dd04328aa59`。日志 `build/native-structure-p5-placement-client.log` 含 `NATIVE_STRUCTURE_PLACEMENT_SMOKE_PASSED`；[旋转放置结果截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-placement-smoke-20261012-r6/screenshots/native-structure-placement-51116-1791811655190.png)已核对。故障结构 UUID 为 `9f95473f-9eef-4d20-a00c-5b5fad5f5ab8`，保留隔离证据。日志中的 `NATIVE_STRUCTURE_PLACEMENT_EXPECTED_POST_WRITE_SOUND_FAILURE` 是这一负例的预期异常，必须同时看到后续 `NATIVE_STRUCTURE_PLACEMENT_SERVER_DIRECT_POST_WRITE_FAULT` 守恒标记与整轮通过标记，不能忽略其他异常。

各轮均保留世界和日志，没有通过修改产品射线、碰撞或材料校验来绕过失败：

| 轮次 | 观察与处理 | 证据 |
| --- | --- | --- |
| r1 | 首次楼梯请求发出后未放置，阶段超时；没有结构隔离，增加服务端返回值和状态诊断 | `build/native-structure-p5-placement-first-failure.log` |
| r2 | 收到使用包时服务端仍选空槽，返回 `UNSUPPORTED`；稍后才选中楼梯，且碰撞检查通过。定位到原版与 Misaka 两条调度队列，修复结构包的处理顺序 | `build/native-structure-p5-placement-order-failure.log` |
| r3 | 首次放置已 `APPLIED`、旧包已 `STALE`；副手阶段客户端命中正确，服务端视线却恢复为水平朝南。探针传送记录了零角度，后续 `lookAt` 不能替换待确认的传送角度；改为把目标计算出的 yaw／pitch 直接写入 `TeleportTransition` | `build/native-structure-p5-placement-camera-failure.log` |
| r4 | 使用新的原始存档副本，真实主／副手放置、负例、异常守恒、保存重载和截图全部完成 | `build/native-structure-p5-placement-r4-passed.log` |
| r5 | 增加最终手选择复验后，放置及写后异常守恒通过；重载时 Windows 对元数据原子替换返回 `AccessDeniedException`，整轮失败。保留世界和元数据，未放宽断言或关闭原子写 | `build/native-structure-p5-placement-metadata-failure.log` |
| r6 | 与 r5 相同代码、全新原始副本，完整链和保存重载通过 | `build/native-structure-p5-placement-client.log` |

r5 的文件访问拒绝原因未确定。只读检查未发现 manifest 读写流未关闭：写入流在原子替换前关闭，失败发生在创建新运行期句柄前，加载返回拒绝并恢复原索引；失败世界保留三个 owner 标记及故障结构的 recovery 文件。r6 通过不能证明文件访问拒绝永不复现，后续 Windows 长时间运行与重启恢复验收仍需覆盖此问题。

箱子回归使用 `run/native-structure-chest-smoke-20261012-p5`，进程 6824，结构 UUID `9003bd35-aea9-4112-86fd-2ec37c2fdba0`；[界面截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-chest-smoke-20261012-p5/screenshots/native-structure-chest-gui-6824-1791811078649.png)与[箱体截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-chest-smoke-20261012-p5/screenshots/native-structure-chest-world-6824-1791811084355.png)已核对。世界中的锁定提示来自拒绝用例，最终锁已恢复为空。

旧投影探针本轮也触发了原有零角度传送竞态，停在首次建造资格负例之后，最终超时；失败世界 `run/native-structure-projection-smoke-20261012-p5` 与日志 `build/native-structure-p5-projection-camera-failure.log` 保留。仅将探针的 `positionPlayer` 改为在传送中直接给出实际角度，保留原位置、距离及全部断言。新副本 `run/native-structure-projection-smoke-20261012-p5-r2` 完整通过，进程 72228，结构 UUID `1872ed3b-1ac8-43a0-b78a-ca851822fd43`；[旋转五格截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-projection-smoke-20261012-p5-r2/screenshots/native-structure-projection-72228-1791811333625.png)已核对。两个成功日志分别为 `build/native-structure-p5-chest-client.log`、`build/native-structure-p5-projection-client.log`。

本轮没有对六种白名单材料逐一做真实客户端放置；客户端成功链覆盖楼梯、台阶与拉杆，石头另由服务端异常路径覆盖。真实多人网络延迟、第三方保护插件、通用物品组件、跨 JVM／崩溃恢复、持续采掘、物理运动和 Sable 混装均不在上述通过结论内。

### 第五批重跑入口

先在 `run/native-structure-placement-smoke-自定名称/saves/Native Structure Placement Smoke` 准备已经退出游戏的全新测试前副本。需要至少三个未分配的永久槽：主链、第二结构碰撞负例、写入后异常隔离各占一个；卸载不回收槽位。玩家开始时双手均空、允许建造且非旁观者，快捷栏至少有五个空槽，副手必须为空。探针只往这些空槽及空副手放入标记材料，不清空或覆盖既有库存；它会切换生存模式、启用飞行并在宿主世界内调整测试镜头。

使用 JBR 25 与仓库规定的 `JAVA_HOME`，执行：

```powershell
.\gradlew.bat runClientDev -DisDev=true --init-script validation/native_structure/placement.init.gradle -PacademyNativeStructurePlacementDirectory=run/native-structure-placement-smoke-自定名称
```

脚本显式启用 `academy.nativeStructurePlacementSmoke`，检查游戏目录位于仓库 `run/` 下且名称以 `native-structure-placement-smoke` 开头，并要求指定世界已有 `level.dat`。探针另检查世界真实路径及专用 fixture 索引为空；成功或失败的运行均不可直接重新执行，应保留原世界与日志，从测试前副本另建目录。出现原版实验设置提示时，仅对这个隔离副本备份并加载。

必须看到完整的 `NATIVE_STRUCTURE_PLACEMENT_SMOKE_PASSED`，并打开 `screenshots/` 下本轮 PNG 确认旋转模型。第三槽的故障隔离必须保留，不能重置结构或归还材料以制造通过。世界、截图与日志均在忽略的 run/build 目录，不进入 Git。

## 第六批：受限持续采掘

本批代码为独立后端增加持续按住攻击键的逐格采掘，最终代码的隔离客户端 r3 已完成下述场景验证。玩家始终属于宿主世界，工具和背包使用真实对象；不会暂改玩家 Level、位置或朝向，也不创建假玩家或覆盖库存副本。客户端命中结构后阻止攻击穿透到宿主方块或实体，不预测删除结构内容；只有服务端完成破坏后才更新投影。

### 支持范围与会话

方块白名单为原版石头、圆石、橡木木板、橡木楼梯、橡木台阶、拉杆、红石灯、钻石矿石、青金石矿石和普通单箱。所有含流体状态均拒绝；箱子须为单格、未含水的实际原版 `ChestBlockEntity`，并且没有尚未展开的战利品表。其他 BE、熔炉、双箱、陷阱箱、门床、重力块及模组方块不在本批采掘范围内。

主手可为空手或审核过的原版镐、剑、斧、锹、锄及剪刀，含当前版本的铜工具。服务端同时核对物品身份、实际类和组件修改；非空工具必须只有一件。普通附魔、磨损、修复代价、不可破坏和显示组件可保留，替换工具规则、耐久上限或增加未知行为组件则拒绝。未知工具子类不因继承原版类而获准。普通效率、急迫、挖掘疲劳、水下和离地修正使用真实玩家状态；掉落采用真实工具的精准采集、时运和耐久路径。本批没有执行原版开始击打时的 `HIT_BLOCK` 附魔效果，不能据此宣称任意数据驱动附魔都已完整适配。

`NativeStructureMiningSessions` 持有服务端会话，入口为 `start`、`hold`、`stop`、`abort`，统一由 `tick` 推进并在结构关闭时清理。网络使用 `START / HOLD / STOP / ABORT`；同一连接的请求 ID 必须为递增正数，只有 START 携带完整命中。旧请求不能续租、完成或取消新会话。

| 阶段 | 服务端行为 |
| --- | --- |
| START | 使用真实眼睛、视线和射程重算目标，严格核对原请求的运行期身份、版本和姿态；在结构操作锁及 `StructureContexts` 内发出可取消的 START 事件，回调后复验并计算首个 tick |
| HOLD | 只续租，不增加采掘进度；最后一次有效续租超过 40 个服务端 tick 后，会话过期且不能复活 |
| 逐 tick | 每个服务端 tick 至多累计一次实际挖掘速度；绑定同一结构、运行期身份和局部格，重新验证视线、权限、工具、游戏模式、菜单和控制资格 |
| READY / STOP | 进度到 1 才发送 READY；STOP 可以提前锁存，但不能跳过所需 tick。普通完成统一延后到投影更新之后的会话 tick，仍须通过最终重验；没有原版提前 STOP 的宽限完成路径 |
| ABORT / 失效 | 松开、移开、超距、换工具或槽位、打开菜单、迁维、断线、内容变化及结构停用等会终止会话并清除裂纹；创造模式可在有效 START 内立即完成 |

后续 tick 不锁住 START 时的姿态；只改变结构 pose、玩家继续瞄准同一局部格时可持续采掘。当前内容校验采用整个存储 level 的方块变化版本以及目标完整 state/BE 数据，其他格的方块状态版本变化或目标 BE 数据变化也会保守取消，尚未细分为逐格采掘版本。时间倍率没有动作机会的 tick 不累计进度；完全失去操作资格时终止，客户端续租仍须正常维持。原版宿主采掘与结构采掘互斥。

进度包只传结构身份、运行期 UUID、局部格、状态 ID、破坏者、请求 ID、递增更新序号、状态和裂纹阶段，不传库存或完整 BE NBT。服务端广播给宿主附近玩家及操作者，每 20 tick 至少刷新一次；结束时也清理曾收到该会话的观察者。客户端按破坏者的更新序号拒绝旧进度，裂纹随当前姿态渲染，并设置本地超时清理。真实多人观察、重连和延迟条件仍须另行实测。

### 破坏、保护事件与掉落归属

`StructureMiningEvent` 的 `START` 和 `FINISH` 均携带真实玩家、命中、`SpaceContext`、写前数据与防御复制的工具；监听器可取消操作。最终破坏由 `NativeStructureMining.destroy` 在结构写入锁内执行，保护回调、关闭该格箱子菜单、方块回调及工具回调后继续复验身份、权限、目标和真实工具。动作期间拒绝普通 API 写入、保存、卸载与姿态更改重入，异常进入 `RECOVERY_REQUIRED`，不重放破坏回调。

最终作用域处理同时覆盖 `RuntimeException`、`AssertionError` 和 `LinkageError`；已拦截的输出失败会锁存失败状态，即使回调吞掉异常，也不能继续成功发布。物品生成拦截覆盖原版私有 `popResource` 入口。致命 `VirtualMachineError` 不被转换成普通失败结果；这些边界仍不构成进程崩溃安全保证。

适配仍使用原版方块的 `playerWillDestroy`、工具 `mineBlock`、`onDestroyedByPlayer`、`destroy` 和适用时的 `playerDestroy`，保留实际工具耐久、采集资格、统计及消耗。NeoForge 破坏和掉落事件使用存储 level 与局部格，事件中的玩家仍属于宿主；保护插件须识别 `StructureContexts` 或直接使用公开结构事件。不能仅靠 `player.level()` 解释局部坐标，也不能把本批视为所有原版左键事件或第三方保护已等价兼容。

破坏作用域暂存本次新产生的物品实体与经验，再将其位置、方向和速度转换到宿主。只改变这些新掉落实体的空间归属，不迁移玩家。每个输出都有唯一对象、UUID 和交付状态；部分交付后失败不再次生成已交付项。实际箱子库存从真实 BE 取出，原版战利品计算使用真实工具与当前 BE；不会从初始捕获快照恢复被取走的物品。

当前输出预算为 512 项、累计经验 32767、编码后的输出数据 256 KiB 和恢复证据 900000 字节。未加载宿主、越界、输出超限或交付拒绝都必须保留已有归属证据并停止继续交付。最后一格被挖掉后，呈现状态转为 `EMPTY` 并移除投影，结构身份、存储内容和永久槽仍保留，可以保存和重新加载；本批没有自动回收槽位。

### 恢复证据的含义

发生写入或部分交付异常时，`recovery_N.dat` 的 `adapter_evidence` 保存 `schema=native_mining_drops_v1`、`manual_recovery_only=true`，以及结构身份、工具观察、写入状态、剩余 BE 和输出账本。账本区分 `PENDING`、`ATTEMPTED`、`PUBLISHED`、`REJECTED`、`UNCERTAIN`；其中 `UNCERTAIN` 表示不能安全判断外部回调后的交付结果，不能当作未交付而补发。

`before_cell_observation_do_not_restore` 和工具的 `before_observation_do_not_restore` 只是写前观察，不授权重建整箱或恢复工具。`raw_loot_observation_do_not_replay` 记录尚未完成的战利品阶段，也不能直接再执行掉落。核对守恒时应区分宿主已交付对象、账本中未交付的唯一对象和真实 BE 剩余内容，不能把已交付记录再计作一份待恢复库存。

恢复文件存在即阻止重新激活。序列化或落盘失败时，运行时仍保留证据供应者及其持有的实际输出对象，并继续隔离；这只是在当前进程保留恢复材料，不能保证未落盘对象在硬终止后仍存在。本批尚无自动重放、管理员恢复命令，或联合玩家数据、原版区块、宿主实体与 manifest 的崩溃原子提交协议。

### 第六批验证状态

2026 年 10 月 12 日最终采掘客户端 r3、JUnit、两种构建及三组 GameTest 均已通过；既有放置、单箱和投影客户端均已按相同最终代码重新验收通过。第五批的历史通过记录不代替本批新增采掘、网络和掉落路径的验证。

| 检查 | 必须记录的证明范围 | 当前结果 |
| --- | --- | --- |
| JUnit | `test -DisDev=true`；逐 tick 进度、续租与提前 STOP、暂停和变速、旧更新拒绝、包边界及输出账本，含原有测试 | XML 汇总 2398 项，零失败、零错误、零跳过 |
| 开发与发布构建 | `build -DisDev=true`、`build -DisDev=false` | 最终代码分别成功，退出码均为 0 |
| 三组既有 GameTest | `academy:native_structure_query`、`academy:native_structure_space`、`academy:block_structure_safety` | 三个注册测试均通过；查询标记、容器 550 tick 与 I/O 报告注入标记、旧安全九类场景均保留 |
| 真实客户端采掘 | 真实按键链、松开取消、换工具/槽位、移动 pose 后继续、旧包拒绝、START/FINISH 取消 | r3 全部通过；石头与单箱裂纹各有清晰截图，均已打开核对 |
| 实际资源 | 石头产物与耐久、7 件箱内标记钻石、矿石产物与经验、精准采集和创造模式 | r3 通过；1 圆石、7 件箱内钻石及箱子本体保留，普通矿石增加 1 钻石和 5 经验，精准采集产 1 矿石且不增经验，工具按次磨损，创造模式不产掉落/不磨损 |
| 空结构与重载 | 最后一格删除后无投影，保存/卸载/重新加载后身份和空内容保留 | r3 同 JVM 重载通过，runtimeId 更新，已挖四格仍为空；没有回收永久槽 |
| 部分交付故障 | 第二个宿主输出被取消后隔离；宿主、未交付账本与 BE 剩余的 12 件标记物品守恒；旧请求不重放 | r3 通过：已交付 7、未交付 5、BE 剩余 0；再次调用不追加输出，隔离结构拒绝卸载。该项为集成服务器内直接调用破坏入口 |
| 既有客户端受影响分支 | 放置、普通单箱和投影交互使用最终代码及各自全新隔离副本顺序重跑 | 三项全部通过；菜单 7 件库存、放置 8 单位及故障 2 单位守恒，投影交互与卸载保留正常 |

基础检查的实际命令、开始/结束时间和退出码记录于 `build/native-structure-p6-validation.json`，对应日志为 `build/native-structure-p6-test.log`、`build/native-structure-p6-build-dev.log`、`build/native-structure-p6-build-release.log`、`build/native-structure-p6-query.log`、`build/native-structure-p6-space.log` 和 `build/native-structure-p6-safety.log`。`build/native-structure-p6-source-manifest.json` 保存本轮 34 个文件的源码哈希。三项既有客户端使用同一份未变源码重跑，日志和截图见下表；运行产物不进入 Git。

真实双客户端专服、所有白名单材料/工具/附魔组合、第三方保护、跨 JVM／崩溃恢复、物理运动与 Sable 混装均不在本探针的通过范围内。

### 第六批客户端记录

最终世界为 `run/native-structure-mining-smoke-20261012-r3`，进程 79632，主结构 UUID 为 `f083627c-7705-4d93-8ce1-47d5daa2887b`，故障结构 UUID 为 `da3d690a-3c83-4108-a808-e3a83f8ecb46`。`build/native-structure-p6-mining-client.log` 包含 `NATIVE_STRUCTURE_MINING_SMOKE_PASSED`，记录真实输入、取消、换工具、移动姿态、旧包、箱内 7 件物品、工具磨损、矿石 5 经验、精准采集、创造模式、空结构重载及部分交付隔离全部完成。[石头裂纹截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-mining-smoke-20261012-r3/screenshots/native-structure-mining-stone-cracks-79632-1791814119051.png)与[单箱裂纹截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-mining-smoke-20261012-r3/screenshots/native-structure-mining-chest-cracks-79632-1791814123647.png)均已打开核对，裂纹清晰贴合任意轴旋转后的实际模型。

故障证据保存为该世界的 `data/academy/native_structures/recovery_1.dat`。故障注入明确取消第二次宿主物品生成，日志中的 `Host refused a native structure drop` 和对应 quarantine 是预期拒绝；其后两次 `NATIVE_STRUCTURE_MINING_RECOVERY_EVIDENCE` 均为 `delivered=7 pending=5 remaining=0 total=12`，最后仍有整轮通过标记。不能仅忽略 ERROR 日志而认定通过，也不能将这一确定取消场景扩展为所有 `UNCERTAIN` 或磁盘故障均已验证。

| 轮次 | 观察与处理 | 证据 |
| --- | --- | --- |
| r1 | 使用 `run/native-structure-mining-smoke-20261012`，首次 START 之前生存模式的初始 onGround 状态关闭探针飞行，玩家掉离目标；服务端 ABORT 阶段持续无会话，最终超时。保留失败世界，只修探针通过正常玩家能力包维持已明确准许的飞行 | `build/native-structure-p6-mining-flight-failure.log` |
| r2 | 全新副本 `run/native-structure-mining-smoke-20261012-r2` 完成真实采掘及故障守恒，普通矿石经验为 3；属于中间修订验证，不作最终源码验收 | `build/native-structure-p6-mining-r2-passed.log` |
| r3 | 最终收口代码在全新副本重跑全部链路，并分别捕获石头与普通单箱裂纹；普通矿石经验为 5，故障交付 7+5+0=12。两张 PNG 均已实际查看 | `build/native-structure-p6-mining-client.log` |

各轮没有放宽真实射线、距离、工具、进度或资源守恒断言。r2 与 r3 的矿石经验不同属于原版允许的 3 至 7 范围；该探针校验增量及精准采集不产经验，不把一次随机值固定为所有运行的期望。

三个既有客户端回归均使用 `run/native-structure-{chest,placement,projection}-smoke-20261012-p6` 下的新世界，未复用已完成的 fixture。对应 `validation/native_structure/{chest,placement,projection}.init.gradle` 与目录参数记录在 `build/native-structure-p6-validation.json`，三轮均正常退出且没有追加源码修改。

| 回归 | 通过标记及日志 | 结构身份与已核对截图 |
| --- | --- | --- |
| 单箱菜单 | `NATIVE_STRUCTURE_CHEST_SMOKE_PASSED`；`build/native-structure-p6-chest-client.log`；真实菜单包、快捷移动、拆分、游标、关闭、失效会话及 7 件库存守恒 | 进程 51356，`f7b69315-6384-44f2-b7dd-3b013b848b64`；[菜单](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-chest-smoke-20261012-p6/screenshots/native-structure-chest-gui-51356-1791814417972.png)、[世界模型](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-chest-smoke-20261012-p6/screenshots/native-structure-chest-world-51356-1791814423674.png) |
| 旋转放置 | `NATIVE_STRUCTURE_PLACEMENT_SMOKE_PASSED`；`build/native-structure-p6-placement-client.log`；主副手放置、旧包、保护、碰撞、写后故障隔离及保存重载 | 进程 20592，`9a469838-1822-4fee-9150-e38c753e9d10`；[放置结果](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-placement-smoke-20261012-p6/screenshots/native-structure-placement-20592-1791814500229.png) |
| 投影交互 | `NATIVE_STRUCTURE_PROJECTION_SMOKE_PASSED`；`build/native-structure-p6-projection-client.log`；真实交互包、过期包、熔炉状态、旋转呈现及卸载保留 | 进程 59096，`e492bf85-111c-4b46-b91a-b06cc1217a58`；[旋转五格](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-projection-smoke-20261012-p6/screenshots/native-structure-projection-59096-1791814595226.png) |

本次放置回归的预期写后故障结构为 `9c17e163-c073-49f7-915d-786604fcbeab`；日志中的异常须与 `NATIVE_STRUCTURE_PLACEMENT_SERVER_DIRECT_POST_WRITE_FAULT` 一起读取，其结果为方块保留、库存余 1、总单位 2、隔离且拒绝重放。

### 第六批重跑入口

使用 JBR 25 和仓库 Gradle wrapper，按顺序运行基础验证：

```powershell
.\gradlew.bat test -DisDev=true
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
.\gradlew.bat runGameTestServer -DisDev=true -PacademyGameTests=academy:native_structure_query --init-script validation/native_structure/query.init.gradle
.\gradlew.bat runGameTestServer -DisDev=true -PacademyGameTests=academy:native_structure_space
.\gradlew.bat runGameTestServer -DisDev=true -PacademyGameTests=academy:block_structure_safety
```

客户端先准备已经退出游戏的全新测试前副本：`run/native-structure-mining-smoke-自定名称/saves/Native Structure Mining Smoke`。需要至少两个未分配永久槽，分别用于主链和部分掉落交付故障；玩家双手为空、快捷栏至少四个空槽，测试世界不能有干扰经验守恒统计的既有经验球。探针向空槽放入标记铁镐、精准采集铁镐和铁斧，不覆盖原有库存；会切换生存/创造模式、启用飞行并在宿主调整镜头。

```powershell
.\gradlew.bat runClientDev -DisDev=true --init-script validation/native_structure/mining.init.gradle -PacademyNativeStructureMiningDirectory=run/native-structure-mining-smoke-自定名称
```

脚本显式启用 `academy.nativeStructureMiningSmoke`，检查目录位于 `run/` 内且名称以 `native-structure-mining-smoke` 开头，并要求指定世界已有 `level.dat`。探针另验真实世界路径及专用 fixture 索引为空；成功或失败后均保留本次世界，从测试前副本另建目录重跑，不能清空库存、删除 owner/recovery 或重置 fixture 制造通过。

验收必须同时看到完整的 `NATIVE_STRUCTURE_MINING_SMOKE_PASSED` 和 `NATIVE_STRUCTURE_MINING_RECOVERY_EVIDENCE`，打开 `NATIVE_STRUCTURE_MINING_SCREENSHOT` 指向的石头及单箱 PNG 确认旋转结构上的裂纹。故障槽的 recovery 文件必须保留并核对交付分类，不能仅凭通过日志推断恢复安全。最终 r3 的实际记录见上文；重跑时仍须独立核对每项断言和证据。世界、截图与日志保留在忽略的 run/build 目录。

## 第七批：独立双精度刚体绑定基础

本批将求解器候选落实为官方 `rapier3d-f64 = 0.36.1`，使用精确 Rust `1.99.0` stable 工具链和 `Cargo.lock`。下载的 Rapier crate SHA-256 为 `f4588855e9c5451702147b5c6ddff4d93887f1e59c9504b6ac3571818f6ec3d2`。我方 Rust 源码位于 `native/academy_physics`，采用自有版本化 C ABI；Java 25 通过正式 FFM API 调用，不依赖 Sable 的 Rust 分支、JNI 实现或原生二进制。[ABI 契约](D:/mcmodtest/AcademyCraft-Reborn-26.2/native/academy_physics/ABI.md)记录数据布局、限额和错误语义。

这是显式启用的独立求解器原型。普通 `test`、开发/发布 `build` 不要求 Rust，也不加载或打包该原生库。第七批交付时尚未连接 `NativeStructureRuntime`、方块碰撞形状、内容版本、投影或旧技能；真实结构接入另见第八批，不能把本批自由落体、复合刚体、碰撞和旋转结果直接作为后续路径的验收。P0 整体仍未完成。

内部 Java 封装位于 `org.academy.internal.common.structure.physics`，只交换数值和受控句柄，不把 Minecraft 对象传到原生侧。世界持有 double 宿主原点，送入求解器的是相对坐标；每个世界、刚体和库都有明确关闭顺序及线程归属。世界关闭后不能再使用旧刚体；原生句柄不复用，不能用另一世界的句柄越界访问。Rust panic 不能越过 C ABI，异常世界停止后续求解，但仍允许销毁。

加载只接受显式配置的库文件和配套 manifest，检查 ABI、引擎、平台、文件名和 SHA-256。manifest 还记录 Rust 版本、目标 triple 和源码摘要。此摘要用于核对本地构建输入及产物一致性，不是发布者签名，也不保证不同编译器/系统环境生成逐字节相同的二进制。Windows x86_64 为本轮实际验证平台；Linux x86_64 的构建接线仍需独立机器验收，其他平台尚未提供。

NeoForge 在 JVM 启动后创建 `academy` 模块，启动参数 `--enable-native-access=academy` 无法预先授权该模块。本批将唯一执行受限 FFM 调用的 Java 类独立编译为固定的 `academy/native_physics/FfmPhysicsAccess.bin` 资源，再由受控类加载器定义到未命名模块。显式探针使用 `--enable-native-access=ALL-UNNAMED`，桥接类检查自身实际授权状态；该 JVM 参数授权所有未命名模块，并非仅授权此桥。普通运行不会打开此桥或加载 DLL，不依赖 JDK 当前的兼容警告放行。库先销毁所属世界、关闭代码 arena，再删除已复核摘要的私有库副本；清理失败只允许重试关闭。

本批锁定依赖图包含 59 个第三方 crate，完整许可原文、源内归属和固定版本来源见 [依赖清单](D:/mcmodtest/AcademyCraft-Reborn-26.2/thirdparty/native_physics_dependencies.md)。索引同时覆盖可选与构建时依赖，不表示每个列出的 crate 都链接进当前 DLL。

### 第七批验证与重跑

2026 年 10 月 12 日，Windows x86_64、JBR 25.0.3、MSVC 14.44 与 Windows SDK 10.0.26100.0 上完成下列检查。客户端只在标题界面运行独立求解器；专服只使用全新的隔离世界，没有玩家接入。这不是移动方块、多人或 Sable 混装验收。

| 检查 | 实际结果与证明范围 | 记录 |
| --- | --- | --- |
| 基础 JUnit | 2414 项，零失败、错误或跳过；包含 16 项本批数值、manifest、桥接权限和生命周期检查 | `build/native-physics-p7-bridge-tests.log`；最终构建日志见下文 |
| Rust | 12 项通过，含 C ABI 长度/对齐/别名、句柄归属与耗尽、预算、故障隔离和真实引擎行为 | `build/native-physics-p7-bridge-tests.log` |
| 真实引擎 JVM | 17 项通过，含重力、接触、睡眠、复合质量/惯性、偏心冲量、旋转、CCD、远坐标、分步、线程和资源释放 | `build/native-physics-p7-bridge-tests.log` |
| 真实 NeoForge 客户端 | `owner_module=academy`、`binding_module=ALL-UNNAMED`、实际原生访问授权为 true；720 步后高度 `0.4999309852979097`、竖直速度 0、关闭成功，退出 0 | `build/native-physics-p7-client.log`；`run/native-physics-smoke-client-20261012-p7-r1/native-physics-client-result.txt` |
| 真实独立专服 | 最终 r3 与客户端相同数值及模块证据；正常停止事件、服务端线程结束、退出码 0；日志记录各维度保存完成，没有保存/关闭异常或 crash report | `build/native-physics-p7-server-r3.log`；r3 目录下 result 与 exit 两份记录 |
| 隔离守卫 | 已有世界与指向外部目录的 junction 均在启动前被拒绝；外部哨兵文件未改变 | `build/native-physics-p7-guard-existing.log`、`build/native-physics-p7-guard-junction.log` |

最终 `test build -DisDev=true` 与 `build -DisDev=false` 均成功退出 0，日志为 `build/native-physics-p7-build-dev-final.log` 和 `build/native-physics-p7-build-release.log`；构建同时执行的 104 项编辑器测试亦全部通过。两份成品均包含唯一的 11572 字节桥接资源与 71 份原生依赖许可/索引文件，不含正常模组路径下的 `FfmPhysicsAccess.class`、本库 DLL/SO 或 Rust 工具链。核对了 59 个锁定 crate 对应的 139 条完整许可/源内归属引用与摘要。

成品、模块探针、XML 汇总及日志索引保存在 `build/native-physics-p7-validation.json`，本批相关文件摘要保存在 `build/native-physics-p7-source-manifest.json`。开发 JAR SHA-256 为 `6f0d96ebdac11793d2a026ee44ef4e37ffe236f895f1c0f83cbc82007e95e1ea`，发布 JAR 为 `e80885486216c228a395d6b834b45037a2ce125b0b1392d53865976806608c9e`。本批没有重跑旧结构三组 GameTest 和逐格交互客户端；没有更改这些运行路径，本批探针也不代替其未来真实物理接入后的回归。

本轮 DLL SHA-256 为 `c8ab64f663ecb2ca9727ce94f57f9d90fe547a5c77c6b77518ca479f1def2dbd`；最终原生源码/ABI 文档摘要为 `cdd61b70d56679574508228bdfd363ac3e4c97fa1bd57300304b8f62cabe7f25`。验收后仅补充 ABI 文档中 no-op 操作不保证唤醒的语义，并重新生成 manifest，DLL 摘要未改变。模组默认构建不需要 Cargo；原生测试则显式调用固定工具链，不以缺库跳过测试。

初次 CCD 测试错误假设关闭显式 CCD 就必然穿过静态薄墙；Rapier 0.36.1 自带快速动态体对固定碰撞体的自动 CCD。最终测试保留自动保护检查，并改用动态薄墙验证显式 CCD 的对照。输入速度上限 `10000` 是 ABI 校验范围，求解器仍保留默认 `400` 单位/秒速度限制，见 ABI 契约；不声称旋转扫掠与真实方块世界高速碰撞已验收。

初次专服 r1 暴露动态模块原生访问未授权，保留 `build/native-physics-p7-server.log`、失败结果与崩溃记录；修复后客户端 r1、专服 r2 通过，收紧存档隔离后专服 r3 再次通过。专服探针仅在显式启用且完成隔离检查后，等待正常停止流程和服务端线程结束，再由守护线程退出自身 JVM，避免已有非守护线程使测试挂起。此停止顺序不代表任意崩溃的数据安全保证。

客户端及专服启动日志仍有本机 OSHI/Windows Perflib 计数器读取异常和可选类缺失警告，均发生在物理探针前；本批没有修改这些既有路径。验收要求完整 PASSED 数值/模块/关闭证据、退出码与无物理或保存失败，不能仅依据 Gradle 成功或忽略所有 ERROR。Linux、真实方块物理、旧技能迁移、双客户端与 Sable 混装均仍待验证。

开发机须先有固定版本 Rust、Windows MSVC C++/SDK 或 Linux linker；Gradle 不自动安装工具链。可通过 `-PacademyCargo=<cargo绝对路径>` 指定项目外或构建目录中的 cargo。原生输出全部留在 `build/native-physics`；不要提交 DLL、Cargo target、日志或测试世界。

```powershell
.\gradlew.bat nativePhysicsTest -DisDev=true
```

该显式任务执行 Rust 测试、构建 release 库、生成 `build/native-physics/artifacts/academy_physics.properties`，随后运行独立 Java 测试。缺失工具链、原生库、错误摘要或 ABI 不匹配会失败，不静默跳过。

模组探针使用 `validation/native_structure/physics.init.gradle`。先创建全新 `run/native-physics-smoke-client-自定名称` 或 `run/native-physics-smoke-server-自定名称`；客户端无需存档。专服只复制已接受的 EULA，并准备 `server-ip=127.0.0.1`、空闲端口、`level-name=native-physics-smoke` 的 `server.properties`，世界目录必须不存在。已有世界、目录链接、旧结果或退出标记在启动前拒绝，探针启动任务禁用配置缓存以每次重新检查；不得复用通过或失败的目录。

```powershell
$physicsLibrary = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.dll'
$physicsManifest = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.properties'
.\gradlew.bat runClientDev -DisDev=true -DisCompat=false --init-script validation/native_structure/physics.init.gradle -PacademyNativePhysicsSmoke=client -PacademyNativePhysicsSmokeDirectory=run/native-physics-smoke-client-自定名称 "-PacademyNativePhysicsLibrary=$physicsLibrary" "-PacademyNativePhysicsManifest=$physicsManifest"
.\gradlew.bat runServerCompat -DisDev=true -DisCompat=false --init-script validation/native_structure/physics.init.gradle -PacademyNativePhysicsSmoke=server -PacademyNativePhysicsSmokeDirectory=run/native-physics-smoke-server-自定名称 "-PacademyNativePhysicsLibrary=$physicsLibrary" "-PacademyNativePhysicsManifest=$physicsManifest"
```

两次启动顺序执行；如 Cargo 不在 PATH，均追加 `-PacademyCargo=<绝对路径>`。结果必须包含 `NATIVE_PHYSICS_CLIENT_PASSED` 或 `NATIVE_PHYSICS_SERVER_PASSED`，并核对目录内结果文件、实际 module/native-access、`closed=true` 与进程退出码。专服还须核对 `native-physics-server-exit.txt` 和保存/关闭日志。客户端超时、OOM 或缺少通过文件均算失败，即使外层进程碰巧退出 0。运行日志、世界及原生产物留在忽略的 build/run 目录。

## 第八批：真实结构与宿主地形的有界刚体接入

本批通过 [StructurePhysics](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/api/common/structure/StructurePhysics.java)、`StructurePhysicsRegion` 和 `StructureMotionState` 将第七批求解器连接到真实容器。调用方在服务端线程显式打开宿主区域、附加精确活动句柄，随后可读取运动快照、设置质心线速度/角速度或在宿主点施加冲量。接口不要求玩家，也不返回原生句柄。`openRegion` 仍要求显式配置原生库、manifest 和 `--enable-native-access=ALL-UNNAMED`；普通启动不会自动为结构启用物理，旧技能仍使用原来的快照后端。

### 区域、几何与发布边界

每宿主 level 最多一个固定区域，每服务器最多三个区域；每轴长度必须为 `(0, 24]` 格，坐标受世界边界和有限值限制。三个是区域数量，不是结构数量。每个区域的结构共享一个求解世界，重建时临时创建第二个世界；不连接不同区域或宿主维度之间的接触。结构槽仍沿用四个永久 owner 的限制。

动态结构最多 128 个非空气格、64 个碰撞盒；每个原生世界的动态结构和宿主地形合计最多 2048 盒，宿主按每组最多 64 盒建立固定刚体。地形扫描最多 65536 格，另有限制形状读取次数；这些是原型工作预算，不是大型船只的性能承诺。超限明确暂停，不截断几何、不删除内容，也不继续使用陈旧碰撞运动。

[NativeStructurePhysicsGeometry](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/internal/common/structure/physics/NativeStructurePhysicsGeometry.java) 每次物理 tick 从实际 storage 与宿主状态提取 `getCollisionShape(..., CollisionContext.empty())`，保留 `VoxelShape.toAabbs()` 的各个盒与空隙。楼梯、台阶、栅栏、墙和漏斗不以整体包围盒填实。宿主区域外扩一格，以包含伸出自身格子的碰撞形状；邻居读取另受有界守卫约束。所有区块访问使用已加载的 `getChunkNow`，缺失时暂停，不添加宿主区块票据或隐式加载。结构存储仍沿用自身已有生命周期票据，二者不能混淆。

支持门禁按原版注册身份和精确审核类执行：普通完整块、柱、台阶、楼梯、栅栏、墙、常见土石地形、矿石、灯、已有审核机器、普通干燥单箱和拉杆。拉杆自身没有碰撞盒。含流体、动态/实体上下文形状、未知模组块或子类、依赖 BE 内容的形状明确拒绝；审核机器的普通形状通过，不代表已适配其通用菜单或跨空间机器语义。

提取不读取或消费 `NativeStructureChanges.take`。结构使用独立于姿态的存储变化版本，覆盖扫描时跳过的空气格；宿主记录扫描内包括空气在内的全部状态。提取器还记录区块身份及形状邻居读取，在提取结束与步进前复验。每 tick 重新提取实际形状并比较盒列表，不依赖新增宿主变更 hook。形状回调期间持有运行时排他门禁，禁止生命周期、保存、内容写入和姿态控制重入；状态不稳定时暂停该区域。

几何、附加成员或宿主地形改变时，先构造完整候选世界，成功后替换旧世界。形状改变引起局部质心变化时，按 `v_new = v_old + ω × R(c_new - c_old)` 保留原刚体的速度场，角速度保持；不重新编号局部格，也不承诺在增删材料时保持总动量或动能。重建会唤醒接触体，避免移除支撑后继续睡眠。当前统一密度为 1、摩擦为 0.6、回弹为 0，动态体显式开启 CCD；数据驱动材料、容器内容质量和高速旋转扫掠仍待扩展与验收。

正常 tick 固定模拟 `0.05` 秒、三个子步，重力沿宿主 Y 轴为 `-9.81`。求解后先验证所有结构仍完整位于采样区域和宿主边界内，再成组发布 double 原点和四元数姿态；不把部分成功的物理步发布给一部分成员。发布之后才进行菜单有效性、投影视图和采掘会话 tick。附加期间 `StructureHandle.setPose` 被拒绝，显式传送须先 detach。越出区域或求解异常使该区域进入 `FAILED`，保留最后发布姿态和存储内容，停止求解并释放原生世界；不能回滚 Java 姿态后重放已经执行的原生步。

### 暂停、空结构与持久化

宿主不可用、tick freeze、缺块、不支持内容、预算不足或混合几何观察使区域暂停，恢复有效条件后再从现有状态继续，不追赶暂停期间的时间。时间系统当前仅检查每个成员的锚点：有碰撞几何时为局部几何包围盒中心变换到宿主的位置，否则为结构原点；该点 `TemporalChannel.ENTITY` 的有效倍率只要不等于 1，就暂停整个区域并拒绝新的速度/冲量控制。这不是跨整个船体的时间域积分，也不等于暂停 storage level 的 BE、红石、计划 tick 和局部实体。

最后一个碰撞盒消失时释放该成员的刚体、清除运动快照并保留最后姿态；活动容器、结构引用、内容归属和永久槽仍保留，不执行槽回收。再次产生有效几何会重新建体。成功 unload 会解除物理附加并释放刚体；load 产生新的运行期身份，不自动附加，也不恢复旧速度。关闭区域只撤销物理并保留结构，不代替保存或卸载容器；释放失败保留待关闭所有权并报告错误。

第八批交付时，普通保存只记录最后已发布姿态及既有真实内容，区域定义、附加关系、线角速度和睡眠状态尚未持久化，正常重载不承诺继续原轨迹。后续第十批新增的显式运动恢复见文末，不能反计为第八批验收。本批也没有增加崩溃原子交易、连通域拆分、自动捕获/还原、玩家或实体承载、伤害归属、技能控制迁移和 Sable 混装。

连续物理运动会持续推进姿态版本。已有放置、拉杆和菜单入口仍按原有真实视线、当前姿态及版本门禁运行；本批没有添加客户端预测、姿态插值、历史姿态窗口或运动点击的网络延迟适配。服务端夹具能改变局部方块，并不证明玩家能在持续运动中可靠完成同样操作。第八批交付时的下一阶段目标是补齐这一纵向交互链及真实双客户端验证，再扩展承载、加载调度、材料与容量；后续实现见第九批，不能反计为第八批验收范围。

### 第八批验证与重跑

截至 2026 年 10 月 13 日，当前已取得以下证据；待验证项不能用第六、七批通过记录替代。

| 检查 | 当前结果与证明范围 | 记录 |
| --- | --- | --- |
| 专项 JUnit | 11 项通过：8 项几何空隙、预算、分组及越界检查，3 项公共运动值/区域边界检查；零失败、错误或跳过 | `build/test-results/test/TEST-org.academy.internal.common.structure.physics.NativeStructurePhysicsGeometryTest.xml`、`TEST-org.academy.api.common.structure.StructureMotionStateTest.xml` |
| Rust ABI/引擎回归 | 12 项通过；复用第七批原生绑定，未以 Java 夹具代替 C ABI 边界验证 | `build/native-physics-p8-rust.log` |
| 真实引擎 JVM 回归 | 17 项通过，XML 已核对为零失败、错误；`nativePhysicsTest` 任务成功 | `build/native-physics-p8-native-tests.log`；`build/test-results/nativePhysicsTest/TEST-org.academy.internal.common.structure.physics.NativePhysicsEngineIntegrationTest.xml` |
| 真实 NeoForge 专服 r3 | 240 tick 完成最终服务端夹具；真实结构下落、非 yaw 接触、地形增删重建、局部内容增删、共享世界动态体碰撞、空/卸载释放、重载仅姿态、缺块暂停及四类新增保护均通过；正常保存/停止、服务端线程结束、退出 0 | `build/native-physics-p8-server-r3.log`；`run/native-structure-physics-smoke-server-20261013-p8-r3` |
| 真实客户端 r1 | 272 tick 完成相同最终夹具与保护场景，42 个移动姿态样本；实际渲染提交、下落前/接触后截图及卸载后投影消失通过，退出 0；仍非真实键鼠操作测试 | `build/native-physics-p8-client-r1.log`；`run/native-structure-physics-smoke-client-20261013-p8-r1` |
| 全量 JUnit | 2425 项通过，零失败、错误或跳过；包含上述 11 项定向检查，不重复计数 | `build/native-physics-p8-test.log` |
| 开发/发布构建与编辑器测试 | 两种构建均通过；104 项编辑器测试零失败、错误或跳过 | `build/native-physics-p8-build-dev.log`、`build/native-physics-p8-build-release.log` |
| 查询/交互 GameTest | `academy:native_structure_query` 在同源码全新 r2 目录通过；r1 的文件访问失败另见下文 | `build/native-physics-p8-query-gametest-r2.log`；`run/native-structure-query-gametest-20261013-p8-r2` |
| 真实容器 GameTest | `academy:native_structure_space` 通过，运行 550 tick，含 `SYNTHETIC_IO_REPORTS_PASSED` | `build/native-physics-p8-space-gametest.log`；`run/native-structure-space-gametest-20261013-p8` |
| 旧快照安全 GameTest | `academy:block_structure_safety` 通过，覆盖既有捕获、还原与失败守恒契约 | `build/native-physics-p8-safety-gametest.log`；`run/block-structure-safety-gametest-20261013-p8` |
| 成品内容 | 开发与发布 JAR 均包含新公开物理 API 和固定 FFM 桥资源，均不包含 DLL/SO/dylib | `build/native-physics-p8-artifacts.json` |
| 新增保护负例 | 专服 r3 与客户端 r1 均通过：附加期间 `setPose` 拒绝；NaN 和 `10001` 速度拒绝且不改变运动状态/区域；蛛网不支持时暂停 10 tick，移除后恢复；原版 tick freeze 暂停 10 tick，解冻后恢复 | 两端 `native-structure-physics-*-result.txt` 的保护字段 |
| 真实双客户端、连续运动下的玩家操作、时间域混合及异常发布 | 待后续专项验证；本轮服务端夹具未覆盖 | 待补 |

本轮静态交互 adapter 源码未改，采用三组 GameTest 回归既有契约，并用新增真实客户端验证物理投影；没有将旧静态交互客户端另列为本轮必需待验。最终验证索引为 `build/native-physics-p8-validation.json`。成品核对记录开发 JAR SHA-256 为 `0db5d47b9ad60be8e17c683a6841cada10af919e490cffabf00283fdca696876`，发布 JAR 为 `c75d8821fe9a6b1f2634e764f9685ed93e00ebcad88b27bcf6ae93db745fce67`。

首次查询 GameTest 在第 15 tick 卸载保存时，`metadata.dat` 的临时文件原子替换发生 `AccessDeniedException`，日志 `build/native-physics-p8-query-gametest.log` 和失败目录 `run/native-structure-query-gametest-20261013-p8` 保留。只读复审确认 manifest 读写句柄按作用域关闭；具体访问拒绝原因仍未知，不能推断为某个进程或杀毒软件持锁。未修改代码绕过失败，同一源码改用全新 r2 目录后通过。第五批曾记录相似文件访问拒绝，这次成功重跑不证明该问题已修复或不会再发生，仍需后续 Windows 长时间运行与持久化专项排查。

最终专服 r3 的结果文件记录初始三格、四个实际碰撞盒、质量 `2.25`，首次落地最低 Y 为 `110.99998865943796`；地形 epoch 和局部几何 epoch 均到 3。两个结构引用为 `1bf1f452-5aaa-4b32-9fe3-440a2ff7dd9c` 与 `a144718d-8d63-471e-9bcb-0b084c5644b3`，共享求解会话 `6b0a1557-27c7-473f-81a7-1b7d64a56f05`。远端未加载区域执行步数为 0，姿态不推进且区块保持未加载；最终 `closed=true`、`fixture_tickets_released=true`。专服退出文件同时记录 `exit_status=0`、`server_stopped_event=true` 和 `server_thread_terminated=true`，日志显示正常保存完成。

客户端 r1 的[下落前截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p8-r1/screenshots/native-structure-physics-before-21816.png)与[接触后截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p8-r1/screenshots/native-structure-physics-landed-21816.png)已实际打开核对：悬空倾斜的三格复合结构及落地姿态清晰可见，三个方块模型正常呈现。日志记录 `moving_samples=42` 和两个最终通过标记。两端结果仍明确写入 `actions=server_fixture`、`actual_keyboard_actions=false`，不是玩家输入、库存消耗或移动中开箱的证明。

| 运行 | 排查与结果 | 记录 |
| --- | --- | --- |
| 专服 r1 | 夹具只初次加载宿主区块，没有持续票据，无玩家专服随后卸载了区块；物理正确停在 `PAUSED: unloaded_chunk`，探针 FALL 阶段失败 | `build/native-physics-p8-server-r1.log` |
| 专服 r2 | 为隔离夹具显式持有并最终释放宿主票据后，基础链在 218 tick 通过；产品提取器仍不加载缺失区块 | `build/native-physics-p8-server-r2.log` |
| 专服 r3 / 客户端 r1 | 增加四类保护场景后，两端最终通过；缺块负例仍不使用夹具票据，不能把 r2 计作新增保护的验证 | 最终两端日志与结果文件 |

客户端初始化资源重载期间仍出现 `Missing uniform Globals (should be UNIFORM_BUFFER)` 异常，旧 `build/native-structure-p4-client.log` 中也有同样记录。本轮之后正常进入世界、目标三模型渲染和上述物理/关闭断言通过，未将该既有问题算作本批修复；也不声称启动日志零异常。视觉通过依据实际渲染提交、两张已打开的截图及完整探针结果，而不是仅凭进程退出码。

专用入口为 `validation/native_structure/physics_world.init.gradle`。每次使用全新的 `run/native-structure-physics-smoke-server-*` 或 `run/native-structure-physics-smoke-client-*` 目录；探针需要两个从未认领的永久槽，成功后槽不会回收。专服准备已接受的 EULA 和 `server-ip=127.0.0.1`、`level-name=native-structure-physics-smoke` 的配置，世界目录必须尚不存在；客户端准备隔离副本 `saves/Native Structure Physics Smoke`。已有结果/退出标记、链接世界目录或不匹配目录被拒绝。夹具只在明确验证为空的宿主区域放置平台，不覆盖既有方块；它为自身准备显式加载并最终释放局部宿主票据，远端缺块负例不使用这些票据。

```powershell
$physicsLibrary = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.dll'
$physicsManifest = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.properties'
.\gradlew.bat test -DisDev=true --tests org.academy.internal.common.structure.physics.NativeStructurePhysicsGeometryTest --tests org.academy.api.common.structure.StructureMotionStateTest
.\gradlew.bat runServerCompat -DisDev=true -DisCompat=false --init-script validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-自定名称 "-PacademyNativePhysicsLibrary=$physicsLibrary" "-PacademyNativePhysicsManifest=$physicsManifest"
.\gradlew.bat runClientDev -DisDev=true -DisCompat=false --init-script validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=client -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-client-自定名称 "-PacademyNativePhysicsLibrary=$physicsLibrary" "-PacademyNativePhysicsManifest=$physicsManifest"
```

启动顺序执行，Cargo 不在 PATH 时追加 `-PacademyCargo=<绝对路径>`。专服须同时核对 `NATIVE_STRUCTURE_PHYSICS_WORLD_SMOKE_PASSED role=server`、目录内 result/exit 文件、`server_stopped_event=true`、`server_thread_terminated=true`、退出码与正常保存。客户端还须核对 `NATIVE_STRUCTURE_PHYSICS_CLIENT_PASSED`、至少三个实际移动姿态样本、`before`/`landed` PNG 内容及卸载后投影消失。客户端同样使用服务端夹具，不因存在画面就记作真实键鼠交互验收。本批完整 `test -DisDev=true`、`build -DisDev=true` 和 `build -DisDev=false` 的结果已列入上表；将来代码修订后按受影响范围重新验收，不把本次通过记录无限沿用。

## 第九批：移动玩家操作与有界延迟协调

本批在第八批真实物理区域上接续受限右键、放置、普通单箱和持续采掘。代码已加入服务器姿态历史、同帧目标回执、右键请求编号与应答，以及采掘后续包中的最新命中观察。直连 r6 和双向各 100 ms 的延迟 r1 均已完成真实专服加双客户端验收，六张截图已由根代理打开核对；最终全量检查、两种构建及既有采掘客户端回归也已通过。各项证据分别说明其范围，不能用此前夹具或单客户端记录替代多人验收。

### 服务器历史与当前目标

[NativeStructurePoseHistory](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/internal/common/structure/space/NativeStructurePoseHistory.java) 为每个运行期句柄保存最多八帧、最长六个服务器 tick 的已发布物理姿态。第六 tick 仍可查询，第七 tick 过期；这不是不受服务器运行速度影响的固定毫秒保证。历史只来自处于可用物理区域中的真实刚体与成功投影发布，客户端不能提交任意姿态作为历史。内容版本改变、生命周期或物理附加失效会清除或禁用历史；时间或版本倒退开启新的连续域，同一版本不得对应不同姿态。

公开 `StructureQueries.validate` 保留当前姿态、版本和命中表面的严格契约。只有内部玩家路径 `NativeStructureQueries.validateMoving` 使用有界历史：首先尝试当前严格命中；旧观察必须匹配当前结构的精确运行期身份、服务器保存的姿态版本和未改变的内容。服务器以玩家真实眼睛和视线验证历史目标的实际 OUTLINE 形状，宿主方块及其他结构始终使用当前状态，不能回放整个世界来绕过新遮挡。

通过历史验证后，将同一局部命中点重投到当前姿态。该表面点的世界位移不得超过 `0.75` 格，仍须在当前交互距离内，并从真实眼睛再次射向当前点；结构、局部格、表面方向、内外命中和局部点须一致。限制的是表面点位移，因此高速旋转的外缘也可能被拒绝；不能仅检查结构原点距离。任一步失败都会拒绝，不跳到当前视线上的其他格，不外推姿态，也不追赶超出窗口的旧输入。

两段射线共享一次宿主读取回执，记录实际读过的方块状态、已加载区块身份及方块实体身份，最终以不执行形状回调的读取复验。共享读取预算为 16384 次；缺块、预算耗尽或回调期间内容改变均拒绝。普通公开射线也进行自身回执检查。该保护不等于把所有第三方 BE 内部数据或任意动态形状语义都纳入版本管理。

### 同帧回执与操作提交

[NativeStructurePlayerTarget](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/internal/common/structure/space/NativeStructurePlayerTarget.java) 统一检查宿主权限、世界边界、真实玩家位置、眼睛、视线和交互距离。实体遮挡同时检查原始视线到原命中点的一段，以及眼睛到当前重投点的一整段；原目标后方的实体不应误挡原命中，当前目标前方的实体不能因历史容忍被穿透。读取回调前后的玩家快照必须相同。

操作初次获准时生成 `Observation`，固定原网络观察、接受时的当前命中和玩家快照。保护事件、放置准备和最终写入前通过同一回执重验，结果必须完全等于固定命中；不能在监听器改动玩家或结构后悄悄重新选定一个位置。第九批没有放宽放置内部的严格目标、真实槽位/双手、物品组件、单件预留和碰撞回执门禁，也没有复制库存或临时改动玩家的 level、坐标。

普通单箱仍使用真实 BE 和实际 `ChestMenu`。菜单身份固定为结构、运行期、局部格与 BE 对象，每次按结构当前姿态检查距离和权限，不因正常姿态版本推进而无条件关闭。离开范围、内容替换、身份失效或玩家资格变化仍关闭；本批明确拒绝骑乘玩家的新操作及继续维持菜单。此门禁不提供玩家承载、船上运动坐标或上下船语义。

### 请求去重与最新采掘观察

右键请求携带每连接单调递增的正数编号。服务器先消费编号，再处理操作；重复或倒退编号返回 `REPLAYED`，已经被拒绝的编号也不能重新使用。应答仅包含编号与 `APPLIED`、`DENIED`、`STALE`、`UNSUPPORTED`、`BUSY` 或 `REPLAYED`，不携带库存和 BE 内容；客户端校验应答所属连接和已发送编号，不因失败应答自动重放操作。该 nonce 用于去重，不授予权限。包继续进入原版 `PacketProcessor` 顺序，避免抢在同连接的移动或选中槽位更新前执行。

采掘网络 `START`、`HOLD`、`STOP` 都携带最新结构命中，`ABORT` 只标识会话。后续观察必须属于原局部格和运行期，姿态版本不可倒退；服务器逐 tick 根据最近一次获准观察执行同样的历史/当前验证，不始终依赖已经过期的 START 姿态。真实内容版本、目标 state/BE、工具对象与组件、槽位和资格仍固定，内容改变保守取消。START 保护事件和 FINISH 提交各自固定同帧回执；HOLD 只续租，不增加采掘进度，STOP 不能少算服务器 tick，时间系统非动作 tick 不推进工作量。

本批协议字段发生变化，参与测试的客户端与服务器必须使用一致版本。裂纹仍由服务端进度驱动并随当前模型姿态呈现。本批不扩大第六批方块/工具和恢复账本的范围，不实现玩家或实体承载、网络插值、通用菜单/物品、多格放置、技能迁移、Sable 桥或跨后端迁移；P0 仍未完成。

### 第九批验证状态

| 检查 | 需要保留的证据 | 当前结果 |
| --- | --- | --- |
| 定向与全量 JUnit | 历史窗口/容量/内容重置/版本倒退、请求去重、实体遮挡与玩家回执，以及既有全量回归 | 最终 XML 合计 2446 项，零失败、错误或跳过；`build/native-physics-p9-test.log` 为 `BUILD SUCCESSFUL in 11s` |
| 原生绑定回归 | 真实引擎 JVM 与 Rust ABI/引擎检查 | 17 项 JVM、12 项 Rust 通过，零失败；`build/native-physics-p9-native-tests.log` 与 `build/test-results/nativePhysicsTest/TEST-org.academy.internal.common.structure.physics.NativePhysicsEngineIntegrationTest.xml` |
| 三组结构 GameTest | query、space、safety 各自必需测试通过；真实容器持续运行与存储错误报告注入 | 三组均通过；`build/native-physics-p9-query-gametest.log`、`build/native-physics-p9-space-gametest.log`、`build/native-physics-p9-safety-gametest.log`；space 为 550 tick，含 `SYNTHETIC_IO_REPORTS_PASSED` |
| 开发/发布构建 | 两种配置的最终构建与编辑器测试 | 均通过；`build/native-physics-p9-build-dev.log` 为 24 秒、`build/native-physics-p9-build-release.log` 为 28 秒；104 项编辑器测试零失败、错误或跳过 |
| 既有采掘 `runClientDev` 回归 | 第六批完整输入链、工具/取消/移动/旧包、库存与战利品、保存重载和部分交付故障隔离 | 通过；`build/native-physics-p9-mining-client.log` 为 `BUILD SUCCESSFUL`，世界 `run/native-structure-mining-smoke-20261013-p9` 正常保存退出，两张裂纹图已核对 |
| 同帧与旧观察保护 | 过期/篡改/内容替换/脱离物理区域，以及现有遮挡、距离、骑乘、保护事件和回调复验门禁 | 两轮多人验证真实历史接受、伪造姿态/过期/内容变化/重新附加旧历史/卸载拒绝；其他门禁由既有 GameTest、相关单测和源码复审分别覆盖，不声称逐项都作为新增多人负例执行 |
| 真实专服与双客户端直连 | 移动中共享单箱、竞争放置/采掘、编号重放、资源守恒、离远关闭与观察者裂纹；三端结果、正常停服与截图 | `20261013-p9-direct-r6` 三端 PASSED；服务器 717 tick，actor/observer 分别 571/569 个移动版本；正常保存退出 0，三张截图已核对 |
| 双向固定延迟 | 全新会话通过相同三进程链，代理每方向固定 100 ms；保存代理配置/流量与三端证据 | `20261013-p9-delay-r1` 三端 PASSED；服务器 910 tick，actor/observer 分别 672/670 个移动版本；正常保存退出 0，三张截图已核对；存在一次采掘取消后新请求完成 |

最终 `test -DisDev=true`、`build -DisDev=true`、`build -DisDev=false` 均已成功，结果按最终 XML 统计为 2446 项基础 JUnit、104 项编辑器、17 项原生 JVM，均零失败、错误或跳过；Rust 为 12 项通过。三组 GameTest 各自记录 `All 1 required tests passed`，安全组不依赖不存在的自定义 PASSED 标记。生产代码只读复审未发现新增可行动问题。上述是本批有界功能的验收，不覆盖全部部署/网络/内容矩阵；迟加入、断线重连、丢包/乱序、骑乘承载、高速边界与 Sable 混装等仍须后续专项验证。第八批表格和截图仍只证明其原有夹具范围。

验证与产物索引保存在 `build/native-physics-p9-validation.json`、`build/native-physics-p9-artifacts.json`，源码及完整文件摘要分别为 `build/native-physics-p9-source-manifest.json`、`build/native-physics-p9-all-files-manifest.json`，本次 P8/P9 收口文件列表为 `build/native-physics-p9-commit-files.txt`。开发 JAR SHA-256 为 `ee952d94e403b17bbcea605ec0953eaf3808e73fd9208fed4b63a4dd219dc91a`，发布 JAR 为 `a00b00350cde14f3d52d606af9574f093bb284f6f78b01d87dbdda221a11722f`。两份成品均已核对包含新增物理 API、玩家回执、网络包、输入路径和隔离 FFM 桥接资源，不包含 DLL、SO 或 dylib；原生产物仍须显式配置。

既有采掘客户端在全新隔离副本完成 `actual_input`、`abort`、`tool_change`、`start_finish_cancel`、`moving_pose`、`old_packets`、工具耐久、精准采集、创造模式、最后一格清空后重载及部分交付隔离，全部为 PASSED；普通单箱核对 7 件，矿石经验本轮为 4。预期故障注入记录 `delivered=7 pending=5 remaining=0 total=12`，并触发 `partial_delivery_quarantine=true`、`fault_units=12`；该隔离错误属于主动验证，不是意外丢失，也不能将已交付 7 件再计作待补发。日志记录正常停止及全部维度保存。根代理实际打开[石头裂纹图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-mining-smoke-20261013-p9/screenshots/native-structure-mining-stone-cracks-76432-1791825370530.png)和[单箱裂纹图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-mining-smoke-20261013-p9/screenshots/native-structure-mining-chest-cracks-76432-1791825375132.png)，确认裂纹正常；该回归与多人六图分别保留，不互相替代。

两轮完整证据位于 `run/native-structure-multiplayer-20261013-p9-direct-r6` 和 `run/native-structure-multiplayer-20261013-p9-delay-r1`，各自保留三份 `*-result.txt`、`server-exit.txt` 及三份 `*-console.log`。两轮均为两个实际 TCP 客户端，`server_gameplay_calls=false`、`physical_keyboard_mouse=false`；主采掘经 Minecraft 输入路径，不能宣称整个场景使用物理键鼠。每轮最终均为 7 钻石、17 圆石、storage 玩家数 0；重复右键、旧采掘包与已关闭菜单的旧包均未改变归属，距离失效正确关闭共享单箱。服务端退出文件记录 `exit_status=0`、`server_thread_terminated=true`，日志包含正常保存全部维度。

| 已核对截图 | actor 共享单箱 | observer 共享单箱与移动裂纹 |
| --- | --- | --- |
| 直连 r6 | [单箱 GUI](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-multiplayer-20261013-p9-direct-r6/actor/screenshots/actor-moving-shared-chest-58340.png) | [单箱 GUI](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-multiplayer-20261013-p9-direct-r6/observer/screenshots/observer-moving-shared-chest-80908.png)、[裂纹](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-multiplayer-20261013-p9-direct-r6/observer/screenshots/observer-moving-cracks-80908.png) |
| 延迟 r1 | [单箱 GUI](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-multiplayer-20261013-p9-delay-r1/actor/screenshots/actor-moving-shared-chest-9300.png) | [单箱 GUI](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-multiplayer-20261013-p9-delay-r1/observer/screenshots/observer-moving-shared-chest-52448.png)、[裂纹](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-multiplayer-20261013-p9-delay-r1/observer/screenshots/observer-moving-cracks-52448.png) |

六图均由根代理实际打开，共享箱 GUI 中七钻石及观察者所见裂纹可辨；toast 略遮右侧但未遮挡关键槽位或目标格。截图与双方 `shared_chest_capture=true`、观察者 `observer_cracks_capture=true`、服务端 `observer_rendered_cracks=true` 配合使用，不能仅据截图推断网络或物品守恒。

延迟轮 MINE 阶段首个采掘请求被取消，随后新编号 2 的请求完成，阶段共 242 tick；直连对应阶段为 157 tick。首次取消的具体原因尚未定位，不写成延迟完全无拒绝或必定连续完成；历史窗口与位移边界继续保守拒绝。代理 `build/native-physics-p9-delay-proxy.log` 记录每方向 100 ms、两条连接和双向数据量：客户端到服务器分别为 94453/38991 字节，返回分别为 310682/309085 字节。正常关服后代理记录 `Connection lost`，不能因此宣称日志零异常，也不把预期连接结束算作玩法验收失败。

已完成的失败排查与重跑记录如下；r1–r5 不计作多人通过证据。目录均为 `run/native-structure-multiplayer-20261013-p9-direct-<轮次>`，角色日志为各目录的 `actor-console.log`、`observer-console.log`、`server-console.log`。

| 直连轮次 | 事实与处理 | 证明边界 |
| --- | --- | --- |
| r1 | 观察者用户名 `StructureObserver` 为 17 字符，超过原版 16 字符限制，登录包编码失败；改为 `StructureWatch` | 启动配置错误，未完成多人验收 |
| r2 | actor 在 GLFW 原生 `pollEvents` 路径崩溃，保留 `actor/hs_err_pid78568.log`；同一时段执行过外部窗口激活 | 具体原因未定位，不能据时间关联断言激活导致崩溃；该 actor 未加载 `academy_physics.dll`，也不能归因为物理 DLL |
| r3 | 夹具在采掘完成的同 tick、几何尚未重新发布时调用 `setVelocity`，被生产门禁正确拒绝；夹具改为等待后续 tick 的 `runtime.isPresented` 后再控制 | 不放宽内容变更后的物理控制屏障，不以旧几何继续求解 |
| r4 | 多客户端读取协调单文件时，替换 `coord/phase.properties` 遇到 Windows `AccessDeniedException`；协调改成只追加不可变阶段文件 | 仅修改测试编排；没有改动生产 `metadata.dat`，不表示第八批持久化访问拒绝已定位或修复 |
| r5 | 共享箱因移动超距关闭后，夹具仍持续水平驱动，在等待旧包检查的 20 tick 内越出有界物理区域，区域正确失败并保留最后姿态 | 夹具修改为确认双方菜单关闭后将水平速度设为 0；未扩大区域或放宽边界 |
| r6 | 使用上述修订，在全新目录完成三端链与旧菜单包检查 | 三端 PASSED、资源守恒、截图及正常停服均通过；详细数值见上表 |

### 三进程与延迟重跑入口

入口为 [multiplayer.init.gradle](D:/mcmodtest/AcademyCraft-Reborn-26.2/validation/native_structure/multiplayer.init.gradle)，探针为 `NativeStructureMultiplayerSmoke` 与 `NativeStructureMultiplayerClientSmoke`。先预建全新且非链接的 `run/native-structure-multiplayer-<session>/{server,actor,observer,coord}`，session 仅允许 1–64 位英文字母、数字、下划线和连字符。不得复用通过或失败的世界、角色结果、ready/phase 或退出标记。`coord/phases` 如已存在，须为非链接的空目录；旧 `coord/phase.properties` 只用于拒绝复用旧会话，不再作为当前阶段来源。

专服目录准备已经接受的 EULA，以及 `server-ip=127.0.0.1`、`server-port=25575`、`online-mode=false`、`level-name=native-structure-multiplayer-smoke` 的 `server.properties`；该世界目录必须尚不存在。原生库与 manifest 使用绝对路径，仍需固定工具链；init 显式配置原生访问权限，禁用旧 VFX 场景。

以下只依次导出三个启动说明，不启动游戏；`$session` 必须替换为本轮新目录名称，`$port` 直连为 `25575`，代理轮为 `25576`。两个轮次分别使用新 session。

```powershell
$session = 'p9-direct-new'
$port = '25575'
$physicsLibrary = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.dll'
$physicsManifest = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.properties'
foreach ($role in @('server', 'observer', 'actor')) {
    .\gradlew.bat exportNativeStructureMultiplayerLaunch -DisDev=true -DisCompat=false --no-configuration-cache --init-script validation/native_structure/multiplayer.init.gradle "-PacademyStructureMultiplayerSession=$session" "-PacademyStructureMultiplayerRole=$role" "-PacademyStructureMultiplayerPort=$port" "-PacademyNativePhysicsLibrary=$physicsLibrary" "-PacademyNativePhysicsManifest=$physicsManifest"
    if ($LASTEXITCODE -ne 0) { throw "Launch export failed: $role" }
}
```

Cargo 不在 PATH 时追加 `-PacademyCargo=<绝对路径>`。导出的 `<root>/<role>-launch.json` 包含 `executable`、`allJvmArgs`、`classpath`、`mainClass`、`args`、`workingDir` 与 `environment`，必须完整用于各自独立 JVM；按 server、observer、actor 顺序启动，并使三进程同时在线，让 actor 最后启动后自然位于前台。导出任务复用 `runServerVfxValidation`、`runClientVfxCaster`、`runClientVfxObserver` 的运行依赖，避免同时执行多个 Gradle 游戏任务。客户端用户名固定为 `StructureActor`、`StructureWatch`，角色参数仍为 `actor`、`observer`。

延迟轮在启动客户端前另开 [tcp_delay.py](D:/mcmodtest/AcademyCraft-Reborn-26.2/validation/native_structure/tcp_delay.py)，其中 `python` 须为可执行的 Python 3，而非未安装运行时的 WindowsApps 别名：

```powershell
python validation/native_structure/tcp_delay.py --listen-port 25576 --target-port 25575 --delay-ms 100
```

代理仅绑定 loopback，为每方向每次读取的数据按到达时间安排固定延迟并保持流顺序，不逐块累计延迟。理论上额外往返约 200 ms；它不模拟应用包乱序、丢包、抖动或断线。必须记录代理 `ready`、延迟参数和双向流量，不能仅使用 25576 端口就认定施加了延迟。最终轮次目录与代理记录见上表，各目录保留三个 `*-launch.json`；重跑时自行解析本机已安装 Python 的可执行路径。

出现 `<root>/actor-focus-required.txt` 后确认 actor 窗口仍在前台并具有输入焦点；当前重跑按上述启动顺序保留其自然前台状态。主采掘走完整 Minecraft 输入流程，其他编排行为和竞争动作使用实际客户端路径/网络包；这不是所有动作均由物理键鼠完成的声明。

协调阶段采用 `coord/phases/000001.properties`、`000002.properties` 等单调编号的不可变文件。服务器先写临时文件，再移动到从未存在的目标，不替换读者正在读取的旧记录；客户端取最大序号并验证 session、文件名及内容 sequence 一致，禁止倒退。客户端在 level 尚不可用时也先检查服务端 `FAILED` 结果，避免因失败退出而卡在旧阶段。`coord` 只同步编排，不绕过网络直接写玩家物品或目标方块，也不是生产存档的持久化协议。

最终须核对 `<root>/{server,actor,observer}-result.txt` 首行均为 `PASSED`、`server-exit.txt` 中 `exit_status=0` 与正常保存/停止记录。双方还须记录 `shared_chest_capture=true`，实际打开各自 `screenshots/<role>-moving-shared-chest-<pid>.png`，确认共享箱模型；观察者另须保存 `observer-moving-cracks-<pid>.png`，核对服务端裂纹、实际渲染提交及结束后清除。两个轮次的三张截图都需查看，不能以旧单客户端截图代替。

## 第十批：有界物理区域与运动状态保存、恢复

本批将有界物理关联及最后一次一致运动观察写入既有 manifest，采用显式安全恢复。服务器启动仍按原规则装载健康的 active 结构内容，不自动加载原生库或启动刚体。调用方先用 `StructurePhysics.savedRegionBounds(host)` 查询 `Optional<AABB>`，再显式调用 `resumeRegion(host)`；缺少库路径、manifest 或原生访问权限时明确失败，保留待恢复记录。普通 `openRegion` 遇到已有记录会拒绝，不能用新建零速刚体替换它。

### 存档格式与一致性

[NativeStructureManifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/internal/common/structure/space/NativeStructureManifest.java) 升级为版本 2，每条结构记录可带 [NativeStructurePhysicsAttachment](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/internal/common/structure/physics/NativeStructurePhysicsAttachment.java)：区域 bounds、可空 motion 和 `resumeAllowed`。motion 保存世界线速度、世界角速度、旧局部质心、睡眠观察及碰撞几何摘要。姿态仍位于同一 Entry；每次成功物理发布先验证整组记录，再用 `putAll` 一次替换，任何验证失败都不提交半组姿态或运动。速度/冲量控制也更新该检查点，正常保存无需从已经关闭的原生世界重新读取。

v1 无物理字段的存档继续读取为未附加、静止的结构；v1 若夹带 physics 字段则拒绝，不猜测格式。新增 NBT codec 严格验证字段、数值类型、有限值、布尔值、几何摘要以及预算，不接受未知字段或隐式数值转换。无效 metadata 保留原文件并关闭后端写入。区域仍每轴不超过 24 格，同一宿主的关联 bounds 必须一致，最多三个宿主；线角速度各分量绝对值不超过 `1e4`，局部质心不超过 `1e6`。运行期 UUID、几何计数器、姿态历史、裂纹会话及求解器接触缓存不存盘、不作为持久身份。

碰撞摘要由排序、规范化后的局部盒列表与固定求解配置生成 SHA-256，包含算法版本、引擎/ABI 和当前统一密度等参数。它识别的是刚体几何和配置，不是完整方块、箱子 NBT、宿主地形或跨文件内容的校验和，不能用于证明库存完整。真实内容仍由原版存储持有，不生成第二份可交付物品副本。

### 恢复、内容变化与生命周期

恢复逐 tick 等待 active 结构完成普通装载，并重新读取当前局部几何、已加载宿主地形、边界和时间域。inactive 结构不会隐式装载；显式 `load` 后才加入已恢复区域。非空运动检查点的碰撞摘要必须完全匹配，任一成员不匹配都会让整区 `PAUSED`，不推进或发布部分成员；记录继续保留，几何重新匹配后可继续恢复。缺失宿主区块不会被强制加载，暂停期间不累计补跑时间。

睡眠字段仅保存当时的观察，恢复统一唤醒，由新求解世界重建接触，不直接恢复旧睡眠/支撑缓存。`motion=null` 表示没有可恢复的碰撞体运动，例如尚未建立刚体或当前碰撞几何为空；后来形成刚体时从零速度开始，不猜测已失去的旧速度。已运行区域中的普通几何重建继续按 `v_new = v_old + ω × R(c_new - c_old)` 转移质心速度，保留原刚体速度场；跨重启遇到摘要不一致则暂停，不能把该公式作为绕过恢复校验的理由。此处未实现按材料密度变化的完整动量重分配。

卸载成员保留姿态和运动关联，并暂停当前共享区域；重新装载得到新的运行期身份，旧句柄与旧网络命中失效。存在实时或保存关联时 `setPose` 拒绝，须先 detach 或明确放弃关联。`detach` 仅清除指定成员；显式 `Region.close()` 清除该区域的关联；没有实时区域时 `discardSavedRegion(host)` 清除保存关联并返回是否有记录。它们都保留真实内容及最后发布姿态。区域失败或存储隔离保留原检查点并置 `resumeAllowed=false`，需要明确放弃后重新装配，不自动重试失败运动。

正常停服与显式 `close()` 语义不同：停服释放原生世界/库，保留保存关联。运行时在任何菜单关闭回调之前设置 stopping 门禁，拒绝新建/装载、物理 open/resume/discard、外部 metadata 变更、放置/菜单写入、姿态修改和物理步进；重复 save/unload 也拒绝。内部关闭、失败标记及最后的证据/索引持久化继续允许，最终原版存储报告失败时仍可保留并隔离检查点。

manifest 内的整组更新不构成与原版区块、实体文件的崩溃原子事务。正常保存/正常重启、缺少配置和主动故障拒绝是本批范围；断电、任意写盘点硬终止、损坏文件自动修复及完整跨存储交易仍未完成。物理暂停也不等于冻结局部原版方块逻辑。本批不增加材料系统、玩家/实体承载、技能迁移或 Sable 桥，P0 仍未完成。

### 第十批验证状态

| 检查 | 需要证明的范围 | 当前结果 |
| --- | --- | --- |
| 模型、codec、Manifest 与摘要定向测试 | v1 兼容、v2 往返、严格拒绝、整组失败无半写、稳定摘要和数值边界 | 首轮 27 项 focused 通过，最终全量再次覆盖；最终基础 JUnit 为 2459 项，零失败、错误或跳过 |
| 三个独立专服 JVM | prepare 保存非零线角速度与另一睡眠体；unavailable 不配置原生库仍保留；resume 显式重建、几何不匹配暂停/重新匹配、库存与身份守恒 | 最新 r2 三阶段均 PASSED；67/42/23 tick，三个独立 PID、正常保存退出 0；完整七钻石箱 NBT 保持 |
| 卸载/停服与清除语义 | 活跃组卸载后无步进、重载恢复速度且旧句柄失效；detach/close 精确清除；stopping 回调不能新增或覆盖检查点 | r2 通过；旧句柄/命中拒绝，三份退出文件均为 `stopping_guards_verified=true` |
| 全量、原生测试及两种构建 | 最新源码的 JUnit、编辑器、Rust/真实引擎 JVM，开发和发布成品 | 2459 项基础 JUnit、104 项编辑器、17 项原生 JVM 均零失败/错误/跳过，Rust 12 项通过；dev/release 成功，24/28 秒 |
| 三组结构 GameTest | query、space、safety 的必需测试，保存/重载与既有安全契约 | query r2、space、safety 均通过；space 550 tick，包含 `SYNTHETIC_IO_REPORTS_PASSED` |
| 客户端物理回归 | 实际物理投影、内容/地形重建、保护门禁、卸载暂停/重载与正常保存 | 全新 r2 在 276 tick 通过，41 个移动样本，两张截图已实际打开核对，退出 0；r1 夹具断言失败另见下文 |

首轮通过记录仅用于保留开发过程，不能计作最终版本验收。最终须同时核对每阶段结果与退出文件；`PASSED` 场景文件先于停服检查写入，仅有该文件不足以证明保存成功或 stopping 门禁通过。

最新三 JVM 验收目录为 [native-structure-physics-persistence-20261013-p10-r2](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-persistence-20261013-p10-r2)，控制台日志分别为 `build/native-physics-p10-persistence-prepare-r2.log`、`build/native-physics-p10-persistence-unavailable-r2.log`、`build/native-physics-p10-persistence-resume-r2.log`。prepare/unavailable/resume 的 PID 为 81064/72192/83332，启动时间分别为 `1791848641423`、`1791848656219`、`1791848668144`，三阶段分别 67/42/23 tick。三份 result 首行均为 PASSED，三份 exit 同时记录退出 0、服务器线程已结束及 stopping 门禁通过。

保存时真实质心线速度为 `(0.75, 2.5094999999999974, 0.15)`，角速度为 `(0.20000670308806096, 0.4006618590076997, 0.09729965496566864)`；恢复首次实际步进后的线速度为 `(0.75, 2.018999999999995, 0.15)`，角速度为 `(0.19995739525909756, 0.40133179276392333, 0.09460174945235322)`。这是恢复速度后正常求解一 tick 的观察，包含重力与旋转演化，不要求与保存值逐位相同；另一原本睡眠的结构也醒着重建接触。

unavailable 在没有两个原生产物路径的 JVM 内确认缺配置异常、检查点保持及无离线步进；resume 先令碰撞形状不同，持续三 tick 验证整区暂停、步数 0、检查点未覆盖，恢复原形状后才出现第一个实际求解步。随后确认真实平移/旋转继续、卸载十 tick 不推进、显式 load 后速度恢复、运行期身份更新以及旧句柄/命中拒绝。最终 detach 仅清指定成员，close 清余下关联，保存不会重建已明确清除的记录。完整七钻石箱 NBT 与内容断言通过，没有把物品载荷写成可重放的运动检查点。

r1 prepare 同样在 67 tick 通过，但早于 stopping 门禁补丁，只保留为历史预检，不是失败轮次，也不能替代 r2。第十批最终检查见上表；三 JVM 正常退出通过不证明 Windows 文件替换的历史访问拒绝永不复现，也不证明任意崩溃点安全。

客户端物理回归 r1 的失败目录为 `run/native-structure-physics-smoke-client-20261013-p10-r1`，日志 `build/native-physics-p10-client-r1.log`，结果 `native-structure-physics-client-result.txt` 明确为 FAILED。日志在第 223 tick 进入 UNLOAD，区域正确处于 `PAUSED: awaiting_structure_activation`；旧 `NativeStructurePhysicsWorldSmoke` 的分支前统一检查仍要求 READY，因而尚未执行 UNLOAD 分支就抛出 `Unexpected physics region status`。这是夹具未跟随第十批“卸载保留关联并暂停共享组”的语义调整，不是原生求解或保存失败；此前若干检查通过也不计作完整客户端验收。

修订仅改夹具：UNLOAD 不再经过 READY 的统一检查，而在该分支持续 20 tick 确认 PAUSED、原因匹配、步数不变、刚体数为零以及旧结构不可 resolve，再继续 load。没有修改生产实现或放宽暂停门禁，三 JVM r2 证据仍有效。全新客户端 r2 在 `run/native-structure-physics-smoke-client-20261013-p10-r2` 完成 276 tick，`native-structure-physics-client-result.txt` 为 PASSED，明确记录 `unload_cohort_paused=true`、`parked_steps_unchanged=true`、`unload_pause_ticks=20` 和 `reload_motion_restored=true`。[客户端日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-client-r2.log) 记录 41 个移动样本、正常保存全部维度及 `BUILD SUCCESSFUL in 56s`，进程退出 0。

根代理实际打开 r2 的[下落前截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p10-r2/screenshots/native-structure-physics-before-13264.png)和[接触后截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p10-r2/screenshots/native-structure-physics-landed-13264.png)，确认倾斜三格/四盒复合结构从悬空到落地没有明显模型错位。该场景仍为 `actions=server_fixture`、`actual_keyboard_actions=false`，证明物理投影和生命周期回归，不替代第九批真实玩家输入/网络并发验证；截图本身也不证明库存或存档守恒。

查询 GameTest 首次命令遗漏 `-I validation/native_structure/query.init.gradle`，日志 `build/native-physics-p10-query-gametest.log` 报告 `Test selection matcher (academy:native_structure_query) found no tests`。这是启动未启用测试，不是测试执行失败或生产代码故障。补入该 init 后，在全新目录 `run/native-structure-query-gametest-20261013-p10-r2` 通过，日志为 [query r2](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-query-gametest-r2.log)；[space](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-space-gametest.log) 与 [safety](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-safety-gametest.log) 使用各自 `-p10` 隔离目录并通过。三组均有 `All 1 required tests passed`，space 另有 550 tick 和存储错误报告注入标记；安全组不依赖额外自定义 PASSED 标记。

最终 [test](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-test.log) 用 10 秒通过；[开发构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-build-dev.log)与[发布构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-build-release.log)分别为 24/28 秒。[原生回归日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-native-tests.log)记录 17 项真实引擎 JVM 与 12 项 Rust 通过。基础/编辑器/原生 JVM 的最终 XML 分别为 2459/104/17 项，均零失败、错误或跳过；最终独立只读复审未发现新增可行动缺陷。

证据入口为 [validation.json](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-validation.json) 与 [artifacts.json](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-artifacts.json)，源码、完整文件及本批增量摘要分别保存于 [source-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-source-manifest.json)、[all-files-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-all-files-manifest.json)、[delta-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p10-delta-manifest.json)。关联文件累计 56 个，包含仍未提交的 P8/P9；本 P10 影响 16 个文件，没有生成资源更改。当前尚无本批提交授权，文件清单不代表已经创建提交。

开发 JAR SHA-256 为 `5db0e2c5b37644ceb2612c95359d2320447b39f650cf8c7ff4af20f3e5454a34`，发布 JAR 为 `24a9474907904536c8e8e8e7053511110f2e1f55738c58359f4f0c835e86284b`。两份成品均含新增物理关联、codec、稳定摘要及公开恢复 API 和固定 FFM 桥资源，不含 DLL/SO/dylib。本批 DLL 未变，SHA-256 仍为 `c8ab64f663ecb2ca9727ce94f57f9d90fe547a5c77c6b77518ca479f1def2dbd`；正常运行继续要求显式配置，不因 JAR 包含 Java 类而自动启用原生后端。

本批完整验收仍限于上述有界功能。跨文件崩溃原子恢复、材料与有限力、分区调度、长期压力、玩家/实体承载、网络插值、Temporal 混合倍率与异常多体发布故障注入、完整重连/迟加入/换维度矩阵、技能迁移、Sable 混装及 Linux 原生产物均未由本批证明。

### 三 JVM 持久化重跑入口

入口为 [physics_persistence.init.gradle](D:/mcmodtest/AcademyCraft-Reborn-26.2/validation/native_structure/physics_persistence.init.gradle)，探针为 [NativeStructurePhysicsPersistenceSmoke](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/internal/common/structure/physics/validation/NativeStructurePhysicsPersistenceSmoke.java)。预建一个全新、无链接的 `run/native-structure-physics-persistence-*` 目录，准备已接受的 EULA 与 `server-ip=127.0.0.1`、`level-name=native-structure-physics-persistence-smoke`、`pause-when-empty-seconds=0` 配置。prepare 的世界、检查点和所有结果/退出标记必须不存在；后两个阶段必须复用同一隔离世界，并在前一 JVM 正常保存退出后执行。

```powershell
$persistenceRun = 'run/native-structure-physics-persistence-p10-new'
$physicsLibrary = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.dll'
$physicsManifest = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.properties'
foreach ($stage in @('prepare', 'unavailable', 'resume')) {
    $nativeArgs = @()
    if ($stage -ne 'unavailable') {
        $nativeArgs = @("-PacademyNativePhysicsLibrary=$physicsLibrary", "-PacademyNativePhysicsManifest=$physicsManifest")
    }
    .\gradlew.bat runServerCompat -DisDev=true -DisCompat=false --no-configuration-cache --init-script validation/native_structure/physics_persistence.init.gradle "-PacademyStructurePhysicsPersistence=$stage" "-PacademyStructurePhysicsPersistenceDirectory=$persistenceRun" @nativeArgs
    if ($LASTEXITCODE -ne 0) { throw "Persistence JVM failed: $stage" }
}
```

Cargo 不在 PATH 时，启用原生库的阶段按第七批入口指定 `-PacademyCargo=<绝对路径>`。unavailable 阶段必须省略两个原生产物参数，也不使用全局配置补入它们；init 不会为该阶段调用原生构建任务或授予 native access。每阶段都必须使用新 JVM，不以进程内 unload/load 代替跨进程恢复。

保留 `checkpoint.nbt`、`unavailable.nbt`、三份控制台日志及 `native-physics-persistence-<stage>-result.txt` / `native-physics-persistence-<stage>-exit.txt`。结果首行应为 `PASSED`，退出文件必须同时记录 `exit_status=0`、`server_thread_terminated=true` 和 `stopping_guards_verified=true`，并核对三个 PID/启动时间与正常保存记录。检查点 NBT 是探针的不可变观察凭据，不是生产自动回滚或补发协议。

## 第十一批：同宿主多区域与独立加载门禁

本批将物理区域从“每宿主唯一”改为稳定 UUID 标识的多个独立区域。每个区域仍在原宿主内有固定 bounds 和自己的原生世界，按已加载区块及成员状态决定是否步进；缺块、内容不支持、成员卸载或恢复几何不匹配只暂停所属区域。其他满足条件的区域可继续正常求解。这是有界的独立调度，不是按玩家距离主动载入区块，也不包含结构跨分区自动迁移、边界接触桥接或区域合并。

### 区域身份、配额与选择

`StructurePhysicsRegion.id()` 返回区域身份，区别于每次原生求解会话的 session ID 和结构运行期 ID。`StructurePhysics.savedRegions(host)` 返回不可变的 `StructurePhysicsRegionInfo` 列表，描述该宿主的保存关联；调用方不得依赖列表顺序。关联数包含该区域已保存的成员，整区 `resumeAllowed` 由各成员许可共同决定。查询不加载原生库或区块，也不授予修改内容的权限。

`resumeRegion(host, regionId)` 与 `discardSavedRegion(host, regionId)` 精确选择区域，未知 ID 不会转向其他区域。旧 `savedRegionBounds(host)`、无 ID 的 resume/discard 保留单区域便利语义；若该宿主有多个保存区域就明确抛出歧义错误，不选择第一个，更不清空整个宿主。关闭某区域只清该区域关联和释放其原生资源；detach 仍仅作用于指定成员。另一区域的检查点、暂停状态与运动不会因按 ID 关闭或放弃而被清除。

[NativeStructurePhysicsRegionLayout](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/internal/common/structure/physics/NativeStructurePhysicsRegionLayout.java) 共用同一套运行期和存档校验：所有宿主的 live 与 saved 预留合计最多三个不同区域 UUID，相同区域的多个成员不重复计数。没有成员的实时空区域也占配额；没有保存关联的空区域不会出现在 savedRegions，也不会仅因打开过就持久化。空碰撞几何但仍有成员关联的区域则继续保留检查点。

各区域每轴仍不超过 24 格。同一宿主的不同区域须至少沿一个轴保留一整格间隔；相交、相切或间隔不足一格均拒绝，包括尚未加载、等待恢复的区域。校验合并 live 和 saved 预留，在加载原生库之前完成。该限制避免独立求解世界之间出现未求解的动态接触；可能接触的结构应放进同一区域。结构越出所属固定区域仍按原有失败策略处理，不自动跳到相邻区域。四个永久结构槽和原生 ABI/资源上限均未扩大。

### Manifest v3 与旧存档

`NativeStructurePhysicsAttachment` 新增 `regionId`，manifest 写版本升为 3，physics 的 `region_id` 只接受完整、小写 canonical UUID 字符串。全索引检查同一 UUID 必须对应相同宿主和 bounds；不同区域的预算和间隔也在原子 `putAll` 提交前统一验证，失败不写半组姿态或运动。

v1 无 physics 的旧结构继续作为静止、未附加内容读取；v1 夹带 physics 仍拒绝。v2 先保留原先“同宿主只有一组相同 bounds”的校验，再用固定命名域、规范宿主名与六个边界 double 字节确定区域 UUID，正负零归一；不使用随机数、运行期计数、记录顺序或文件路径。相同 v2 文件重复打开或成员倒序得到同样区域身份，姿态、速度、质心、睡眠观察、恢复许可和关联成员保持。坏 v2 不能借升级被偷偷拆成多个合法区域；v2 夹带 v3 region_id 也拒绝。

单独解码不会重写原文件，后续正常持久化写出 v3。旧构建不认识 v3 时会按未知版本拒绝写入，因此不能把本升级解释为可任意降级覆盖。无原生配置时仍保留迁移后的检查点，不自动启用求解。第十批的显式恢复、摘要匹配、睡眠唤醒、无离线补跑、停服门禁和内容归属规则保持；本批只把相关读取、发布、关闭、丢弃及失败作用域改为选定区域。

### 第十一批验证状态

| 检查 | 必需证据 | 当前结果 |
| --- | --- | --- |
| 布局、模型与 API 单测 | 重复记录去重、全局三个预留、同 ID 归属一致、六向一格边界、重叠/相切拒绝、旧便利 API 歧义拒绝 | 已通过，包含在本批最终 2472 项 JUnit 中 |
| Manifest v1/v2/v3 | v1 静止、v2 稳定迁移和内容不变、严格字段、批量失败无半写、按区清除不影响其他检查点 | 已通过最终 JUnit，并分别执行真实旧 v2 升级和新 v3 跨 JVM 回归 |
| 同宿主独立区域实测 | 远处缺块区域零步进且不加载区块，同时近处已加载区域正常运动；按区关闭/卸载隔离 | 专服 r1 240 tick、客户端 r1 273 tick 通过，均确认隔离缺块区域时已加载区域推进 20 步 |
| 双区域跨 JVM 专项 | 布局拒绝、配额、按 ID 选择、独立暂停/恢复/关闭与两组运动检查点 | r2 prepare/resume 在 114/25 tick 通过，两 JVM 退出 0、停服门禁通过 |
| 实际 v2 存档升级 | 使用第十批完整存档的隔离副本，经缺少原生配置、显式恢复的独立 JVM 验证 UUID、运动、完整库存和正常退出 | `p11-v2-r1` 两个新 JVM 通过；原始 v2 世界保持不变，非零线角速度、七钻石完整 NBT 与停服门禁均通过 |
| 新 v3 正常保存/恢复 | 全新存档依次 prepare/unavailable/resume，跨进程保持检查点与内容 | `p11-r1` 三个新 JVM 在 67/42/24 tick 通过，均正常退出 0 并通过 stopping 门禁 |
| 原生与结构 GameTest | 真实引擎 JVM、Rust、query/space/safety 回归 | 17 项原生 JVM、12 项 Rust 与三组 GameTest 已通过；space 包含存储错误报告注入 |
| 全量与构建回归 | 最新 JUnit、编辑器、开发/发布构建及最终成品核对 | 最终 JUnit 2472、编辑器 104、原生 JVM 17 项均零失败/错误/跳过；开发/发布构建通过 |

同宿主区域实测沿用 [physics_world.init.gradle](D:/mcmodtest/AcademyCraft-Reborn-26.2/validation/native_structure/physics_world.init.gradle) 和 `NativeStructurePhysicsWorldSmoke`，每轮仍需全新隔离目录，保留服务端结果、客户端移动样本与实际截图；命令模板见第八批入口。专服 r1 的 [日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-world-server-r1.log) 记录 240 tick PASSED、正常保存与成功退出，结果记录 `missing_chunk_region_isolated=true`、`loaded_region_steps=20`。

客户端 r1 在 [隔离目录](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p11-r1) 完成 273 tick，记录 41 个移动样本，同样确认缺块区域零步进且不隐式加载，另一已加载区域推进 20 步。[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-client-r1.log) 记录 PASSED、正常保存与 `BUILD SUCCESSFUL`；根代理已实际打开[下落前](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p11-r1/screenshots/native-structure-physics-before-84060.png)和[落地后](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p11-r1/screenshots/native-structure-physics-landed-84060.png)两图核对完整模型及接触后呈现。界面操作仅用于载入隔离副本，场景仍为 `actions=server_fixture`、`actual_keyboard_actions=false`，不代替玩家输入或多人网络验收。

实际旧存档升级沿用 [physics_persistence.init.gradle](D:/mcmodtest/AcademyCraft-Reborn-26.2/validation/native_structure/physics_persistence.init.gradle)，使用第十批原始 v2 完整世界的全新隔离副本 [p11-v2-r1](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-persistence-20261013-p11-v2-r1)，没有修改来源世界。unavailable 与 resume 分别在 PID 71828/30452 的新 JVM 运行 42/23 tick，两份 result 为 PASSED，两份 exit 均确认 `exit_status=0`、服务器线程结束和 `stopping_guards_verified=true`。缺少原生配置时保留检查点，恢复首步线速度为 `(0.75, 2.018999999999995, 0.15)`，角速度为 `(0.19995739525909756, 0.40133179276392333, 0.09460174945235322)`；七钻石完整 NBT、摘要不匹配暂停、重载与旧命中拒绝通过。控制台日志为 [unavailable](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-persistence-unavailable-v2-r1.log) 与 [resume](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-persistence-resume-v2-r1.log)；这些限定升级结果与其余本批验收分别记录。

新 v3 格式另在 [p11-r1](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-persistence-20261013-p11-r1) 依次执行 prepare/unavailable/resume，PID 为 46940/37036/51388，分别运行 67/42/24 tick。三份结果均 PASSED，退出文件均确认退出 0、服务器线程结束及 stopping 门禁通过；日志为 [prepare](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-persistence-prepare-r1.log)、[unavailable](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-persistence-unavailable-r1.log) 与 [resume](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-persistence-resume-r1.log)。这是新格式正常保存/恢复回归，与使用旧完整世界的 v2 升级验证分别记录。

[原生测试](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-native-tests.log) 已通过 17 项真实引擎 JVM 和 12 项 Rust；[query](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-query-gametest.log)、[space](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-space-gametest.log) 与 [safety](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-safety-gametest.log) 均记录 `All 1 required tests passed`，space 另含 `SYNTHETIC_IO_REPORTS_PASSED`。最终 [test](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-test-final.log)、[开发构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-build-dev.log)与[发布构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-build-release.log)分别在 11/23/28 秒成功；XML 核对 JUnit 2472、编辑器 104、原生 JVM 17 项，全部零失败、错误或跳过。

新增区域专项 r1 在 KICK 阶段失败，[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-regions-prepare-r1.log)与失败目录保留。夹具在贴地时同时设置平移和旋转，正常接触使区域 B 的 X 速度变为约 `-0.365`，绝对值未满足 `> 0.4` 的预期；实际运动未丢失。修订仅让夹具先真实离地再采样，保留非零运动断言，没有修改生产求解或放宽控制门禁。

修订后的 [区域专项 r2](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-regions-20261013-p11-r2) 已完成 prepare/resume 两个独立 JVM，PID 为 78636/692，分别运行 114/25 tick，两份 result 均为 PASSED，两份 exit 均确认退出 0、服务端线程结束和 stopping 门禁通过。[prepare 日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-regions-prepare-r2.log)及结果记录两结构碰撞几何最低 Y 先真实超过 `111.35`，才设置运动；下一实际步进确认线速度分别为 `(0.6, 2.5094999999999974, 0.1)` 和 `(-0.6, 2.5094999999999974, 0.1)`，角速度均非零，再保存两组检查点。

[resume 日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-regions-resume-r2.log)确认跨 JVM 区域 UUID 保持、结构运行期身份更新、无 ID 便利入口歧义拒绝和未知 UUID 隔离。只恢复 A 时 B 保持待恢复；B 几何不匹配暂停并保留检查点时，A 的步数和姿态继续推进；修复 B 后恢复其首步运动。关闭 A 只清 A 关联，B 继续运动，旧 A 区域句柄拒绝使用，最后关闭 B 才清剩余关联。prepare 还覆盖相交/相切/不足一格拒绝、精确一格允许、全局三区域配额与卸载隔离；这些是限定服务端夹具的证据，不代表自动分区迁移或任意多人负载已通过。

最终证据索引为 [validation](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-validation.json) 与 [artifacts](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-artifacts.json)，源码及全部相关文件摘要分别为 [source-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-source-manifest.json) 与 [all-files-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p11-all-files-manifest.json)。本批共 18 个相关文件，其中 16 个为源码/测试/验证配置；文件清单不代表已提交，验收冻结时尚无本批提交授权；后续与第十二批一并提交为 `f2a2d828`。

开发 JAR SHA-256 为 `23d4bcf3c7aff75db3ecf2ecc7876bc21bcfb2bf3c3efd526746f7d3cdef856e`，发布 JAR 为 `7bc2e87d7f648e42a1d1226c2fa678a7e6beceb89b8dd2cb83d620620b816dfa`。两份成品均包含新增区域类型、共享布局校验与固定 FFM 桥资源，不包含 DLL/SO/dylib。外部 DLL 与第十批相同，SHA-256 仍为 `c8ab64f663ecb2ca9727ce94f57f9d90fe547a5c77c6b77518ca479f1def2dbd`；显式配置与原生权限要求未变。

### 双区域跨 JVM 重跑入口

入口为 [physics_regions.init.gradle](D:/mcmodtest/AcademyCraft-Reborn-26.2/validation/native_structure/physics_regions.init.gradle)。先预建全新、无链接的 `run/native-structure-physics-regions-*` 隔离目录，并准备已接受的 EULA 与 `server-ip=127.0.0.1`、`level-name=native-structure-physics-regions-smoke`、`pause-when-empty-seconds=0`。prepare 的世界、`regions-checkpoint.nbt` 和两个阶段结果/退出标记必须不存在；resume 必须复用 prepare 的完整世界，且只在其正常保存退出后启动新 JVM。

```powershell
$regionsRun = 'run/native-structure-physics-regions-p11-new'
$physicsLibrary = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.dll'
$physicsManifest = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.properties'
foreach ($stage in @('prepare', 'resume')) {
    .\gradlew.bat runServerCompat -DisDev=true -DisCompat=false --no-configuration-cache --init-script validation/native_structure/physics_regions.init.gradle "-PacademyStructurePhysicsRegions=$stage" "-PacademyStructurePhysicsRegionsDirectory=$regionsRun" "-PacademyNativePhysicsLibrary=$physicsLibrary" "-PacademyNativePhysicsManifest=$physicsManifest"
    if ($LASTEXITCODE -ne 0) { throw "Regions JVM failed: $stage" }
}
```

两个阶段都必须显式传入原生库与 manifest 的绝对路径，init 明确启用 `ALL-UNNAMED` 原生访问。Cargo 不在 PATH 时另传 `-PacademyCargo=<绝对路径>`。每阶段同时检查 `native-physics-regions-<stage>-result.txt` 首行 PASSED 与对应 `-exit.txt` 中的退出 0、`server_thread_terminated=true`、`stopping_guards_verified=true`，并保留日志、不同 PID/启动时间和 `regions-checkpoint.nbt`。场景结果先于停服写出，不能仅凭 PASSED 或 Gradle 成功代替联合核对；该检查点仍是探针观察证据，不提供生产自动恢复协议。

本批不增加区域自动迁移/合并、跨区域动态体接触、地形分区缓存、主动宿主加载、永久槽扩容、材料、实体承载、网络插值、技能迁移、Sable 桥或跨存储崩溃原子协议。未开启的保存区域按 UUID 显式放弃这一正向路径，未另做独立实机用例；不能将已测的关闭活动区域和错误 ID 拒绝当作该路径的实机证据。P0 仍部分完成，独立区域按区暂停也不代表 storage level 的原版逻辑一起暂停。

## 第十二批：保守地形分区缓存

本批只缓存物理区域内宿主地形的碰撞提取结果。每个区域独占缓存，以原有含 halo 的地形扫描范围与 16³ 区段的交集分片；每个来源格只归属一个分片，形状仍完整保留，不在分片边缘裁剪或替换为整立方体。缓存不改变结构局部几何的提取、原生求解参数、单次成组姿态/运动发布或 manifest v3 格式。

### 复用与提交契约

每个分片保留提取时读取的全部方块状态（包括空气）、已加载区块对象身份和形状实际读取的邻居；邻居读取范围沿用整个区域扫描范围的额外一格，不因分片边界收窄。跨片读取归入读取方的回执，被读取格变化时对应旧分片不能复用。宿主实例、世界边界、当前容量和区块可用性仍须检查，不能仅凭没有收到方块变化事件便认定缓存有效；直接写入 section 的变化也必须由状态复验发现。

未变化分片可复用形状/盒列表，变化分片重新提取到候选集合；所有分片完成后统一检查回执、读取预算和总盒预算，再替换缓存。后面分片的提取回调若改变前面已复用或新提取分片，最终复验必须拒绝整批。任何脏分片因缺块、不支持内容、超限或不稳定而失败，区域都不能退回旧 aggregate 继续步进。缓存成功提交只代表获得候选几何，实际物理步前仍执行原有最后复验。

原有 65,536 个地形来源格和 131,072 次提取状态读取的全域预算不随切片倍增；组合缓存时也计入复用分片原有的读取成本。动态几何每 tick 占用的盒数变化后，以当前剩余容量重新约束完整缓存地形，总量仍受每原生世界 2048 盒限制。冷热路径及单片重建使用稳定盒序，不能仅因 map 插入顺序不同而推进 terrain epoch 或重建原生世界。

缓存仅在服务端线程和当前区域会话内使用，不持久化，不作为恢复许可。卸载引起的原生世界释放、最后成员移除、区域失败、关闭和停服都清除缓存引用；暂停期间即使保留可复验的记录，恢复仍须按当前状态重新检查。清理不能保留可被误用的旧 aggregate 或宿主区块引用，诊断累计计数与当前缓存项数分开处理。

这项优化减少重复调用形状逻辑、分解碰撞盒和分配列表；逐格 loaded/state receipt 检查没有减少。复杂度不能表述为只检查区段数，也不能从缓存命中次数推导 MSPT、帧率或总体内存改善。本批仍保持全服务器三个 live/saved 区域预留、四个永久存储槽及已有每轴 24 格边界；不增加自动分区迁移/合并、主动宿主加载、有限力/PD 控制或玩家承载。

### 第十二批验证状态

| 检查 | 必需证据 | 当前结果 |
| --- | --- | --- |
| 冷/暖/局部失效 | 暖缓存避免重复形状提取；单片变化仅重建必要分片，组合盒与冷提取一致 | 专服/客户端 r1 均通过几何多重集对照、暖复用与单脏片；真实静态帧 captures 分别保持 18/8 |
| 回执与边界 | 空气变实块、原始 section 写入、跨片邻居/halo、区块身份替换、世界边界收缩、负坐标及分片边缘 | 两端 r1 通过原始 section 写入、世界边界、缺块不加载；四项新增单测覆盖负坐标/halo 归属、半开分片稳定序、非法范围及共享预算。跨片邻居变化和区块对象替换仅有源码契约复审，无独立专项实机 |
| 预算与失败 | 全域读取不倍增、动态盒数增长后缓存预算收缩、脏片失败零步进、失败提交不回退旧几何 | 两端均通过缩小总盒预算拒绝、宿主蛛网暂停 5 tick 且无旧几何回退；基础单测最终通过 |
| 生命周期与真实场景 | 原生重建、卸载/重载、关闭、失败、停服清理；真实专服和客户端物理投影回归 | 专服 246 tick、客户端 279 tick 通过，unload/reload/close 缓存项与正常退出通过；客户端 41 个移动样本、两图已目视 |
| 正常持久化回归 | prepare/unavailable/resume 三个独立 JVM，检查点与内容保留、恢复和停服门禁 | r1 在 67/42/23 tick 全部通过，均退出 0 且 stopping 门禁通过 |
| 双区域回归 | 稳定 UUID、独立暂停/恢复/关闭、两组运动检查点与跨 JVM 恢复 | prepare/resume r1 在 114/25 tick 通过，两端退出 0 且 stopping 门禁通过 |
| 基础与成品 | 最终 JUnit/编辑器、原生 JVM/Rust、三组结构 GameTest、开发/发布构建、产物与源码摘要 | JUnit 2476、编辑器 104、原生 JVM 17 项均零失败/错误/跳过，Rust 12 项和三组 GameTest 通过；开发/发布构建及成品核对通过，不继承第十一批统计 |

真实专服 r1 使用 [隔离目录](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-server-20261013-p12-r1)，[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-world-server-r1.log)与结果记录 246 tick PASSED；退出文件同时确认 `exit_status=0`、服务器停止事件和线程结束，日志保留正常保存记录。首次缓存有 18 个分片、扫描 10,051 格，真实静态帧的成功提取计数保持 18。完整提取以盒的多重集比较，避免不同遍历顺序制造假差异；单脏片、原始 section 写入、缩预算、边界变化、clear 后重抓及缺块不加载均有独立结果标记。

运行中在区域内远离结构中心的安全测试空气格放置不支持的蛛网，区域连续 5 tick 暂停，步数不前进且没有旧碰撞回退；将测试格恢复为空气后继续求解。结构卸载时缓存项清零、重载后重新提取，最后关闭区域也清零；原有下落/接触、内容与地形更新、tick freeze、多区域缺块隔离和运动恢复回归均通过。场景仍为 `actions=server_fixture`、`actual_keyboard_actions=false`，这些计数只证明复用和失效行为，不是运行耗时改善数据。

[Rust 日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-rust.log)记录 12 项通过，任务用时 1 秒。首次[编译](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-compile.log)失败仅因限定嵌套类型的 `@Nullable` 注解位置不合法；调整为类型段注解后，[compile r2](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-compile-r2.log) 在 4 秒通过，未将编译通过当作实机验收。首轮[全量测试](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-test-initial.log)执行 2476 项，新增测试把空范围的既有 `INVALID_BOUNDS` 误期望为 `LIMIT_EXCEEDED`；只修正测试区分空范围和超额预算，生产行为未改。随后[最终基础测试](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-test-final.log)已在 9 秒通过 2476 项；首轮失败历史保留，不改写成首次全部通过。

客户端 r1 使用 [隔离目录](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p12-r1)，在 279 tick 完成所有 `terrain_cache` 标记，记录 41 个移动样本；[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-client-r1.log)为 `BUILD SUCCESSFUL in 1m 2s`，正常保存退出 0。初始 8 个分片同样扫描 10,051 格，静态帧 captures 保持 8；与专服的 18 分片不同来自区域位置相对 16³ 区段的对齐，不是少查了来源格。根代理已实际打开[悬空旋转](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p12-r1/screenshots/native-structure-physics-before-77776.png)和[落地后](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p12-r1/screenshots/native-structure-physics-landed-77776.png)两张截图，确认完整模型与接触后显示正确。`actual_keyboard_actions=false`，截图与移动样本仍只证明服务端夹具的物理呈现。

三 JVM 持久化回归在 [p12-r1](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-persistence-20261013-p12-r1) 完成 prepare/unavailable/resume，PID 为 57408/41792/62388，分别 67/42/23 tick；对应任务用时 14/11/10 秒。三份结果均为 PASSED，三份 exit 均同时确认退出 0、服务器线程结束和 `stopping_guards_verified=true`，日志为 [prepare](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-persistence-prepare-r1.log)、[unavailable](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-persistence-unavailable-r1.log) 与 [resume](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-persistence-resume-r1.log)。缺库保留检查点、无离线补跑、摘要不匹配暂停、恢复首步运动、卸载/重载与旧运行期拒绝均通过；缓存自身不存盘，也不因本轮重启获得自动恢复控制的语义。双区域专项、专用原生 JVM、三组 GameTest 与两种构建的通过证据见下文。

双区域回归使用 [p12-r1](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-regions-20261013-p12-r1)，prepare/resume 的 PID 为 58032/45448，分别 114/25 tick、任务 17/11 秒，两个 result 均 PASSED，exit 均记录退出 0、服务器线程结束和 stopping 门禁通过；日志为 [prepare](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-regions-prepare-r1.log) 与 [resume](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-regions-resume-r1.log)。本轮再次覆盖布局门禁、持久区域身份、按区恢复/暂停/关闭与运动检查点，没有将第十一批结果直接计入。

[原生 JVM 测试](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-native-tests.log)本次 2 秒成功，XML 对应 17 项，依赖同时重跑 Rust 12 项并通过。[query](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-query-gametest.log)、[space](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-space-gametest.log)和[safety](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-safety-gametest.log)分别在 10/12/9 秒通过。新增四项缓存单测与实机标记的证明范围分别列在表中；跨片邻居变化、区块对象替换未另做独立实机，不把源码门禁推断写成执行结果。

最终[开发构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-build-dev.log)与[发布构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-build-release.log)分别 23/28 秒成功、退出 0。最终 XML 核对基础测试 2476、编辑器 104、原生 JVM 17 项，均零失败、错误或跳过；Rust 12 项通过。开发 JAR SHA-256 为 `80169f5cff6267ed455ba72608ff2d3fc4e33e157d6678ff91907f03f3237c3f`，发布 JAR 为 `bb1ccb7f6a8959f1202a92129b4e70baae2bde866eb7c1f006e903fa62231e34`。两份成品均包含缓存新类与固定 FFM 桥，不含 DLL/SO/dylib；外部 DLL 与第十一批相同，显式加载要求不变。

证据索引为 [validation](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-validation.json)、[artifacts](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-artifacts.json)、[source-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-source-manifest.json)、[all-files-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-all-files-manifest.json)及[delta-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p12-delta-manifest.json)。验收冻结时累计 22 个相关文件含当时未提交的第十一批内容，本 P12 影响 8 个文件（含两份文档）；20 项源码/测试/验证配置的摘要与冻结版本一致。第十一批的 18 个文件仍保留于 `build/native-structure-p12-baseline` 作为增量对照，该清单形成于提交之前；第十一、十二批现已按用户授权提交为 `f2a2d828`。

本批验证限于缓存、安全门禁、物理呈现与持久化/区域回归，没有重新执行全部玩家交互流程或真实双客户端网络矩阵。跨片邻居变化和区块对象替换的独立实机、性能指标、主动加载、有限力控制及其他范围外事项仍保留其未验收状态。

### 第十二批重跑入口

沿用 [physics_world.init.gradle](D:/mcmodtest/AcademyCraft-Reborn-26.2/validation/native_structure/physics_world.init.gradle) 的专服/客户端入口，不增加默认启动行为。分别预建全新、无链接的 `run/native-structure-physics-smoke-server-*` 和 `run/native-structure-physics-smoke-client-*` 隔离目录，不能复用已有结果或退出标记。专服准备已接受的 EULA 与 `server-ip=127.0.0.1`、`level-name=native-structure-physics-smoke`，世界目录必须不存在；客户端按第八批入口准备 `saves/Native Structure Physics Smoke` 的全新副本及两个未使用的 owner 槽。

```powershell
$physicsLibrary = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.dll'
$physicsManifest = Join-Path (Get-Location) 'build/native-physics/artifacts/academy_physics.properties'
.\gradlew.bat runServerCompat -DisDev=true -DisCompat=false --no-configuration-cache --init-script validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=server -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-server-p12-new "-PacademyNativePhysicsLibrary=$physicsLibrary" "-PacademyNativePhysicsManifest=$physicsManifest"
.\gradlew.bat runClientDev -DisDev=true --no-configuration-cache --init-script validation/native_structure/physics_world.init.gradle -PacademyStructurePhysicsSmoke=client -PacademyStructurePhysicsSmokeDirectory=run/native-structure-physics-smoke-client-p12-new "-PacademyNativePhysicsLibrary=$physicsLibrary" "-PacademyNativePhysicsManifest=$physicsManifest"
```

两个命令分别执行并核对退出码，不在失败后自动继续。Cargo 不在 PATH 时另传 `-PacademyCargo=<绝对路径>`；两个原生产物路径必须为绝对路径，原生访问继续由 init 显式授权。保留每端 `native-structure-physics-<mode>-result.txt`、控制台日志、正常保存与退出证据，专服另核对 `native-structure-physics-server-exit.txt`，客户端实际打开 before/landed 截图并核对移动样本。新增缓存断言与诊断记录按最终夹具实际输出验收，不能仅用原有物理 PASSED 标记证明缓存行为；场景仍为服务端夹具，不代替真实玩家操作或多人压力测试。

## 第十三批：有限力与单点 PD 控制

本批在既有独立物理区域中加入受限控制，不迁移技能，不增加玩家抓取协议，也不改原生 ABI 或 manifest v3。`StructurePhysicsControl` 是公开的不可变请求，`StructurePhysicsControls` 提供玩家技能、程序及非玩家控制者可复用的纯数学。区域仍由服务器线程拥有，调用不会取得内容所有权、加载区块或绕过玩家权限。

### 控制槽与单位

每个已附加结构只有一个临时控制槽：`setControl(reference, control)` 验证成功后覆盖原请求，重复设置不叠加；`control(reference)` 只返回最后接受的配置，不保证下一步可运行；`clearControl(reference)` 可在暂停时清槽，保持实际姿态与速度。三者均遵守所属线程及重入限制。

- `Force(localPoint, force)` 表示在刚体局部点施加世界方向的力，单位为求解器质量单位乘格/秒²，向量范数上限 10,000。它只作用于下一次成功的 0.05 秒外层物理 tick，随后消费；暂停或失败会撤销请求，不在恢复时补跑。
- `Anchor(localPoint, targetPosition, targetVelocity, stiffness, damping, maxForce)` 表示单点弹簧阻尼。目标位置为宿主世界坐标；目标速度单位为格/秒、范数上限 100，描述期望点速度，不自动推进目标位置。刚度与阻尼最大 10,000，刚度与最大力须为正，阻尼可为零；最大力不超过 10,000。

局部点必须在当前碰撞 AABB 内，可位于质心或实体几何间的空隙；它是刚体局部点，不是带格代次的 `SpaceBlockRef` 或可重映射的 `StructureAnchor`。目标须在当前区域及宿主世界边界内。非法、过期或不可用请求在替换控制槽前拒绝；成功的 `setVelocity` 或 `applyImpulse` 撤销该结构的持续控制。

### 子步与生命周期

每个外层 tick 仍求解 0.05 秒，控制期间分为三个子步。每子步读取真实原生姿态、局部质心、线速度与角速度，以 `v_point = v_COM + ω × (point − COM)` 计算施力点速度；PD 输出为 `stiffness × 位置误差 + damping × 速度误差`，最后按整个向量范数限制最大力。没有重力前馈、质量归一化或姿态锁定，因此有限力可能无法托起重物，悬浮会有静态误差，偏心施力也可能旋转。

所有成员本子步的冲量 `J = F × (0.05 / 3)` 先计算、校验，再调用现有原生点冲量接口并求解接触；无世界回调介入原生子步，零力不额外唤醒。各子步检查区域边界，整组成功后才一次发布姿态与运动检查点。部分原生步进失败沿用隔离规则，保留上次一致发布的检查点，不能只回退 Java 姿态后重放。

控制随所属运行期失效，不追随卸载后重新激活的句柄。内容版本、已读状态或碰撞形状改变时，在新几何步进前撤销对应成员控制；仅宿主地形重建且全程未暂停时可以保留请求，但每次从当前成员取得新的原生 body。区域暂停，包括缺块、时间冻结及 Temporal 门禁，会清除全区控制。卸载、detach、dispose、失败、关闭与停服也清除，清理先于可能失败的原生释放。

控制配置不进存档，重启只恢复已发布的运动检查点，须由调用方重新设置控制。释放控制不销毁结构、不清零速度、不还原方块；它也不提供玩家退出、死亡或程序超时的自动绑定，后续技能适配须负责这些调用方生命周期。本批不开放方向 PD、独立扭矩约束、多控制者力混合、材料质量、实体承载或 Sable 控制。

### 第十三批验收状态

| 检查 | 当前状态与要求 |
| --- | --- |
| 纯数学与参数 | 本批基础测试通过 2493 项，含 17 项新增；原生 JVM 20 项（含 3 项新增）和 Rust 12 项通过 |
| 真实有限力与 PD | 专服/客户端 r1 通过一次力、覆盖、质量/时间步、抬升收敛、接触阻挡及释放；客户端三图已目视核对 |
| 失效与生命周期 | 专服/客户端 r1 通过两类直接区段变更、内容变化、暂停/冻结、卸载/重载、直接赋速及非法请求保留；不等于完整异常矩阵 |
| 保存及既有区域回归 | 三 JVM r1 已过：保存时存在 Force/Anchor，下一 JVM 不恢复控制但恢复运动；双区域 r1 同样通过，含错误区域控制隔离 |
| 全量、原生及成品 | test、原生专项、三 JVM、双区域、三组 GameTest、两种 build 与成品核对均已通过 |

已核对本轮 XML：基础 2493 项、原生 JVM 20 项均零失败、错误或跳过；[基础测试日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-test-final.log)记录 19 秒成功，[原生专项日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-native-tests.log)记录 2 秒成功，其依赖重跑 Rust 12 项并全部通过。本批最终源码/测试摘要与冻结版本一致，成品与文件统计见下文。

专服 r1 在[隔离目录](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-server-20261013-p13-r1)完成 314 tick；[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-world-server-r1.log)为 27 秒成功，结果首行为 PASSED，退出记录同时确认退出 0、停服事件及服务器线程结束，所有维度正常保存。场景仍为 `actions=server_fixture`、`actual_keyboard_actions=false`。

本轮质量 2.25 的复合体通过有限力抬升并收敛，平衡位置误差为 `0.015790130406315942`；验证 `F × dt / m`、后写覆盖、一次消费、清槽后重力释放及平台接触阻挡 20 tick。控制观察窗口记录 `control_window_pose_writes=0`、`control_window_velocity_writes=0`，未用直接姿态或赋速完成该窗口。直接区段同盒状态替换和原 AIR 新增空碰撞格都在下一步撤销控制；宿主不支持内容暂停、原版冻结/恢复、结构内容变化、卸载/重载及赋速覆盖也都清槽，非法配置保留原控制。已有缓存、接触、区域隔离和卸载回归标记同时通过；这些是限定夹具的执行结果，不扩大为玩家抓取输入或完整时间/异常矩阵的证明。

客户端 r1 在[隔离目录](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p13-r1)完成 355 tick，记录 46 个移动样本；[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-client-r1.log)为 1 分 31 秒成功、退出 0，并确认所有维度正常保存。全部 P13 控制与 P12 缓存标记通过，抬升平衡误差为 `0.015790130407253855`。根代理已实际打开[初始旋转](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p13-r1/screenshots/native-structure-physics-before-40516.png)、[落地](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p13-r1/screenshots/native-structure-physics-landed-40516.png)及[受力悬停](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p13-r1/screenshots/native-structure-physics-controlled-40516.png)三张截图，确认结构投影与真实控制运动一致；`actual_keyboard_actions=false`，不把截图当作玩家抓取协议已完成。

三 JVM 持久化 r1 使用[完整隔离世界](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-persistence-20261013-p13-r1)，prepare/unavailable/resume 的 PID 为 74664/83740/38280，分别 67/42/25 tick、任务用时 15/11/10 秒；三个 result 均 PASSED，三个 exit 均确认退出 0、服务器线程结束与 stopping 门禁通过。日志为 [prepare](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-persistence-prepare-r1.log)、[unavailable](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-persistence-unavailable-r1.log)及[resume](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-persistence-resume-r1.log)。保存时明确同时存在 Anchor 和 Force，记录 `transient_controls_present_at_save=true`；冻结无重放，缺库阶段保留检查点，下一 JVM 记录 `transient_controls_not_restored=true` 且原运动首步恢复通过。控制清理与运动持久化分别验证，没有以空槽保存冒充控制不恢复。

双区域 r1 的[完整世界](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-regions-20261013-p13-r1)也已通过 prepare/resume，PID 84012/16288，分别 114/26 tick、17/11 秒；两个 result 均 PASSED，两个 exit 均确认退出 0、服务器线程结束与 stopping 门禁。新增 `wrong_region_controls_isolated=true`，验证跨区域控制请求不影响另一结构，既有区域身份、运动恢复和独立生命周期一并回归；日志为 [prepare](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-regions-prepare-r1.log)与[resume](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-regions-resume-r1.log)。

三组 GameTest 的 [query](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-query-gametest.log)、[space](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-space-gametest.log)和[safety](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-safety-gametest.log)分别 10/12/9 秒通过，均记录 `All 1 required tests passed`，space 另有合成 I/O 故障注入通过标记。客户端仍出现与第十二批相同的 Perflib 009 环境错误，不宣称整个日志零异常；本批未扩大修复该既有环境路径。两种构建与成品核对也已完成，证据与摘要见下文。

最终[开发构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-build-dev.log)与[发布构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-build-release.log)分别 23/27 秒成功、退出 0。最终 XML 核对基础 2493、编辑器 104、原生 JVM 20 项均零失败、错误或跳过，Rust 12 项通过。本批共 13 个相关文件，11 项源码/测试摘要与冻结版本一致；用户已在验收后明确授权为这 13 个文件创建本地 Git 提交。证据索引为 [validation](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-validation.json)、[artifacts](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-artifacts.json)、[source-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-source-manifest.json)及[all-files-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p13-all-files-manifest.json)。

开发 JAR SHA-256 为 `4f62019d079636edac24699d4f0f86d90b47154857a4d0f182fac9d5249b803e`，发布 JAR 为 `7317b83014a7decede034209871212cdbfb5e046506d9c3acf065b6581ec451c`。两份均包含新公共控制类型与固定 FFM 桥，不含 DLL/SO/dylib；外部 DLL 与第十二批相同，原生 ABI 和 manifest v3 不变。

本批物理重跑沿用[第十二批入口](#第十二批重跑入口)及 `physics_world.init.gradle`，必须换用全新 `*-p13-*` 隔离目录，并联合检查本批 `control_*`、PASSED、正常保存和退出标记，不能只凭旧物理标记验收控制。持久化沿用第十批三 JVM 入口，必须保留 prepare 的完整世界，并分别启动缺库与恢复 JVM；双区域沿用第十一批 prepare/resume 入口。仅地形重建时保留控制目前只有源码审查，任意质量或 gain 的稳定性也未独立验收，真实 PD 结果限于夹具规定质量与参数。通用交互、玩家抓取输入和完整网络/部署矩阵仍在本批之外。

## 第十四批：独占限时控制租约

本批在第十三批有限力与单点 PD 之上补充控制归属和超时回收，供技能、精密操作程序及非玩家服务端调用者共享。公开入口为 [StructurePhysicsRegion.acquireControl](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/api/common/structure/StructurePhysicsRegion.java)，返回 [StructurePhysicsLease](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/api/common/structure/StructurePhysicsLease.java)。租约绑定当前区域的精确结构成员与运行期控制槽，既不持有原生 body 指针，也不改变物理求解、内容保存或碰撞参数。这是 P1 公共控制会话的有界子集，P0 与完整 P1 仍未完成。

### 获取、更新与独占

`acquireControl(reference, controllerId, initialControl, durationTicks)` 在现有几何、区域状态与命令边界均通过后，原子地安装初始 `Force` 或 `Anchor` 并取得租约。任何既有无租约命令或活动租约都会返回空结果，不抢占、不合并推力；即使旧 `Force` 已消费，活动租约仍占有空槽。请求参数非法则拒绝并保留原命令和租期。`controllerId` 仅为调用方会话标签，相同 UUID 不提供找回或更新他人租约的能力，返回的对象实例才是访问能力。

持有租约时，通过其 `setControl` 替换命令、`renew` 续期、`control` 读取当前命令与 `close` 释放。区域上的无租约 `setControl`、`clearControl`、`setVelocity` 和 `applyImpulse` 均拒绝活动租约，不允许绕过独占门禁。无租约时，既有接口仍保持原来的行为。区域/结构生命周期可以强制撤销租约；独占不授予内容所有权，也不阻止正常的结构卸载或关闭。

### 租期与终态

租期范围为 1 至 200 个服务器 tick。每次区域服务器 tick 处理结束后递减一次，与物理成功步数及三个求解子步无关；租期 1 允许下一次正常物理步处理后即到期。暂停路径直接撤销租约，不将剩余时间冻结到恢复后继续使用。`renew(n)` 将剩余租期重设为 `n`，不累加；更新命令不续期，无效续期不改变原剩余时间，已结束租约不能续期复活。

成功外层物理 tick 只消费 `Force` 命令，租约在自己的期限内仍为 `ACTIVE`；续期不重放已经消费的力。`Anchor` 持续到替换、释放、到期或失效。主动 `close` 进入 `RELEASED`，计时归零进入 `EXPIRED`，结构与区域失效进入 `INVALIDATED`。所有终态均报告剩余 0、无命令，且原因不会被后来重复清理改写。旧对象的 `close` 幂等，其更新/续期拒绝；相同结构或相同 `controllerId` 重新获取的租约不受旧对象影响。

全部方法，包括只读查询和终态 `close`，均要求所属服务器线程。获取、更新和续期沿用物理步重入门禁及当前未暂停几何校验；活动租约的 `close` 只要求线程与区域可变门禁，不依赖几何有效。终态 `close` 仅检查线程，区域或服务器关停后仍可幂等调用。释放保留实际速度、姿态和内容，不自动停止刚体、静态化或销毁方块。

### 生命周期与调用边界

内容版本、状态回执、非空气格数或碰撞形状变化沿用第十三批的保守失效条件；即使实际碰撞盒不变，旧控制也不能自动延续。精确句柄替换、detach、卸载、区域暂停、时间冻结、失败、关闭与停服撤销活动租约，成员移出集合前完成撤销，清理失败不能让旧对象成为后来租约的入口。仅地形重建且区域始终未暂停时，可以在成员不变的前提下保留租约，并从当前刚体施力。

租约身份、控制标签、剩余租期和命令全部不保存、不恢复，也不补跑离线控制。普通保存仍只记录既有的内容和已发布运动；ABI、manifest v3、三区域/四永久槽预算以及显式原生配置要求不变。

本批没有自动绑定玩家或实体生命周期。调用方须在技能/程序取消、控制者死亡、离线或跨维度时及时 `close`，超时仅提供兜底；不能把这些场景写为已自动检测。射线、距离、所有权、资源消耗和保护权限仍由调用方核验。本批不增加玩家抓取网络协议、自动锚点重映射、优先级抢占、多个控制者叠力、旧技能迁移或 Sable 控制桥。

### 第十四批验证状态

| 检查 | 验证范围 | 当前结果 |
| --- | --- | --- |
| 租约状态机 | 占用竞争、相同标签、1/200 tick 边界、续期重设、更新不续期、非法请求不变性、Force 消费、三类终态与旧对象隔离 | 新增 14 项单测，最终全量 2507 项零失败、错误或跳过 |
| 真实物理与生命周期 | 四种无租约写入拒绝、到期释放、重获与旧对象隔离、暂停/内容变化/卸载/关闭撤销，既有有限力/PD 与碰撞回归 | 专服 r1 385 tick、客户端 r2 428 tick 通过所有 P13/P14 标记并正常退出；客户端 44 个移动样本与三张物理截图已核对 |
| 持久化与区域隔离 | 保存时带活动租约、随后冻结撤销、正常停服门禁与下一 JVM 不恢复；原运动恢复与错误区域隔离 | 三 JVM r1 在 67/42/24 tick、双区域 r1 在 114/25 tick 通过，全部退出 0 且 stopping 门禁通过 |
| 基础与成品 | 全量测试、原生 JVM/Rust、三组结构 GameTest、开发/发布构建、源码与成品核对 | 基础 2507、编辑器 104、原生 JVM 20 项零失败/错误/跳过，Rust 12 项与三组 GameTest 通过；两种构建、成品及冻结源码核对通过 |

本批[最终基础测试](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-test-final.log)用时 20 秒，XML 核对 2507 项全部通过，新增 14 项状态机测试。[原生测试](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-native-tests.log)用时 5 秒，原生 JVM 20 项与 Rust 12 项通过；本批没有修改原生求解或 ABI，复用同一源码配置的成功结果，不重复扩充为新增原生能力验收。

真实专服 r1 使用[隔离目录](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-server-20261013-p14-r1)，[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-world-server-r1.log)为 385 tick PASSED、31 秒构建成功，所有维度正常保存；结果与退出标记联合确认退出 0、服务器停止事件和线程结束。租约使真实单点 PD 跨原截止时间持续抬升，续期重设与更新不续期分别断言；1 tick 的 Force 在最后许可步积分后到期，Anchor 到期及 Force 消费后的紧接下一步均恢复自然重力。租约窗口直接 pose/velocity writes 均为 0，未用赋位或赋速代替施力。

专服同时验证既有无租约命令阻止获取、相同/不同控制标签竞争拒绝、四种无租约写入拒绝、非法参数不变性、空命令仍独占以及旧对象不影响新租约。原始 section 同碰撞形状替换、空气新增空碰撞格、一般内容变更、宿主不支持地形暂停、tick freeze、detach、空几何、卸载/重载和区域关闭都有租约失效标记。第十三批有限力/接触与第十二批缓存回归也通过；场景仍为 `actions=server_fixture`、`actual_keyboard_actions=false`。

持久化探针在 prepare 保存时确认两个活动租约，随后用 tick freeze 撤销再停服。因此停服路径不单独宣称“仍活动的租约直接停服撤销”已实测，该路径由源码审查与既有 stopping 门禁覆盖。客户端重拍下落前、落地后及有限力控制三张物理截图，租约行为以真实服务端夹具断言为证，不额外声明存在租约专用截图。

客户端首次 r1 停在实验性世界提示页，`Create Backup and Load` 未及时处理，达到探针全局 4000 tick/200 秒超时限制；[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-client-r1.log)记录 `Structure physics client probe timed out`、3 分 34 秒构建失败与非零退出，[原始隔离目录](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p14-r1)及 crash report 保留。该次没有完成租约场景，不能算通过，也不能从超时推导租约断言失败。后续使用全新 r2 隔离副本完成加载与重跑，生产代码及探针保持同一冻结版本；没有把本批改写成首次全部通过。

客户端 r2 使用[完整隔离副本](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p14-r2)，[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-client-r2.log)记录 428 tick PASSED、44 个移动样本、1 分 2 秒构建成功、正常全部维度保存和退出 0。全部 P13/P14 断言通过，根代理实际打开[下落前](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p14-r2/screenshots/native-structure-physics-before-41912.png)、[落地后](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p14-r2/screenshots/native-structure-physics-landed-41912.png)与[有限力悬停](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p14-r2/screenshots/native-structure-physics-controlled-41912.png)三图，确认投影下落、接触及悬停正常；它们是本批重拍的物理场景，不是租约专用界面或实际玩家输入。启动仍出现 Perflib 009 ERROR 以及 OSHI 进程信息采集的 NPE（`SystemReport.ignoreErrors`），不能称日志零异常；没有租约断言失败。

三 JVM 持久化 r1 使用[同一完整世界](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-persistence-20261013-p14-r1)，prepare/unavailable/resume 的 PID 分别为 74464/78796/81944，运行 67/42/24 tick、用时 14/12/11 秒。三个 result 均 PASSED，三个 exit 均确认退出 0、服务器停止事件、线程结束与 `stopping_guards_verified=true`；日志为 [prepare](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-persistence-prepare-r1.log)、[unavailable](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-persistence-unavailable-r1.log)与[resume](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-persistence-resume-r1.log)。保存时两个租约均为 ACTIVE，`transient_leases_present_at_save=true`；随后冻结撤销，缺库 JVM 保留检查点。新恢复 JVM 确认 `transient_leases_not_restored=true`、`restored_members_reacquire_control=true`，原线角速度的首步恢复、摘要不匹配暂停及旧运行期命中拒绝一并通过。

双区域 r1 使用[完整隔离世界](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-regions-20261013-p14-r1)，prepare/resume 的 PID 为 72748/49184，运行 114/25 tick、用时 17/11 秒，结果均 PASSED；两个退出文件均确认退出 0、服务器停止事件、线程结束与 stopping 门禁通过。两端新增 `wrong_region_leases_isolated=true`，错误区域的租约获取不会占用真正所属区域的控制槽；既有区域身份、独立暂停/恢复、运动恢复与关闭隔离回归通过。[prepare 日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-regions-prepare-r1.log)与[resume 日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-regions-resume-r1.log)保留完整阶段记录。

三组 GameTest 的 [query](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-query-gametest.log)、[space](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-space-gametest.log)与[safety](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-safety-gametest.log)分别 11/12/9 秒通过，各自记录 `All 1 required tests passed`。这些是结构查询、容器与安全行为回归，不替代租约物理专项或完整玩家网络矩阵。

最终[开发构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-build-dev.log)与[发布构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-build-release.log)分别 21/27 秒成功、退出 0。最终 XML 核对基础 2507、编辑器 104、原生 JVM 20 项全部零失败、错误或跳过，Rust 12 项通过。[最终证据收集器](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-finalize.py)已核对 9 个源码/测试/探针文件与冻结版本完全一致、所有阶段日志/结果/退出标记、客户端三图、文档链接及 `git diff --check`；本批共 11 个相关文件，另 2 个为文档。限定验收后，用户已明确授权并创建本地提交 `4f3a1a0a`。

证据索引为 [validation](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-validation.json)、[artifacts](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-artifacts.json)、[source-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-source-manifest.json)与[all-files-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p14-all-files-manifest.json)。[开发 JAR](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/libs/academy-26.2.0-0.0.4-alpha-dev.jar)的 SHA-256 为 `5881302a01711901a32ccc8be787f24f529018641e803709d759404848784d7b`，[发布 JAR](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/libs/academy-26.2.0-0.0.4-alpha-release.jar)为 `42ee9aceb3aa71f60dbbfd17f33cc4a668e28088edc7a9a48b17552e6f5cb297`。两份包含新租约公共类型、内部控制槽与区域租约实现，固定 FFM 桥齐全，不含 DLL/SO/dylib；外部 DLL 与第十三批完全一致，SHA-256 仍为 `c8ab64f663ecb2ca9727ce94f57f9d90fe547a5c77c6b77518ca479f1def2dbd`，ABI 与 manifest v3 不变。

重跑复用既有物理世界、持久化与双区域入口，但使用全新 `*-p14-*` 隔离目录。每阶段联合核对新增租约断言、PASSED、正常保存及退出标记；客户端仍须核对实际投影，服务端夹具不能代替真实玩家抓取输入。仅地形重建保留租约及仍活动租约直接停服撤销未另设独立实机场景，沿用源码复审与相邻生命周期回归的证明范围；完整多人故障矩阵、任意方块兼容、Linux 原生制品与跨文件崩溃原子恢复仍待后续阶段。

## 第十五批：实体绑定控制租约与生命周期撤销

本批已按用户授权提交为 `1b027ac3`。以下保留当批实现与验收记录。

本批承接第十四批的调用者生命周期缺口，为技能、精密操作程序和非玩家服务端调用者增加同一条实体绑定入口。公开方法为 [StructurePhysicsRegion.acquireEntityControl](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/api/common/structure/StructurePhysicsRegion.java)：`acquireEntityControl(reference, actor, controllerId, initialControl, durationTicks)`，继续返回 [StructurePhysicsLease](D:/mcmodtest/AcademyCraft-Reborn-26.2/src/main/java/org/academy/api/common/structure/StructurePhysicsLease.java)。原有 UUID 标签获取入口保持可用，不隐式绑定任何实体。本节实现及限定验收已完成，不代表整个 P1 或旧技能迁移完成。

### 实体身份与撤销边界

实体绑定同时保存精确对象实例、获取时的宿主与实体 UUID。获取要求实体存活、未移除、实际属于当前服务器宿主，且宿主按 UUID 注册的对象就是该实例；玩家还必须是 `PlayerList` 中的精确实例并保持连接。持有相同 UUID 的重生、重连或跨维度替代对象不继承租约。`controllerId` 仍只描述控制会话，不授予权限，也不用于重新查找控制者或找回租约。

物理施力前以及租约更新、续期边界复核实体状态；宿主 `EntityLeaveLevelEvent` 与玩家注销事件经已经存在的运行时即时撤销相应绑定，不为了处理事件创建新的结构运行时。按当前活动绑定检查，不扫描世界全部实体，也不加载实体或区块。事件按精确实例匹配，所以旧实体的迟到清理不能撤销后来实体的新租约。

死亡由控制边界的 `isAlive()` 检查确认，不监听可取消死亡事件提前判定终态。实体离开宿主后即使在同一 tick 返回也不能自动恢复旧租约；没有事件或控制边界观察到的同 tick 死亡后立即恢复不在本批承诺内。非玩家控制者使用同一规则，实体移除、卸载或跨宿主之后不再保持施力。

控制者失效将该绑定租约转入 `INVALIDATED`，清空命令与绑定，并保留实际速度、姿态和存储内容；它本身不使物理区域暂停或失败。其他控制者和原 UUID 标签租约继续遵守自身期限。区域暂停、内容改变、结构卸载、关闭与停服仍按第十四批统一撤销，旧租约不能更新、续期或释放后来取得的控制槽。

### 复用范围

实体绑定不自动续期，也不改变 1 至 200 服务器 tick 的租期、一次力消费和单点 PD 语义。技能或程序主动取消仍须 `close()`；射线、距离、资源、保护检查由调用方负责。获取实体绑定租约不要求玩家身份，不新增网络输入协议或直接姿态/速度控制路径。

本批不迁移动能投掷或高速喷流；它们的捕获、旧实体结算、喷口挂载、撞击效果与资源回滚仍需独立迁移验收。材料密度、方向控制、承载、结构拆分和 Sable 桥也不在本批内。实体实例、UUID 绑定、租约和命令均不持久化；原生 ABI、manifest v3 与现有区域/存储预算保持不变。

### 第十五批验证状态

| 检查 | 本批要求 | 当前结果 |
| --- | --- | --- |
| 实体绑定契约 | 精确实例/UUID/宿主、存活、注册与玩家连接门禁；无效获取不改原控制 | 专服 r1 与客户端 r1 新增实体绑定标记通过 |
| 真实物理与隔离 | 非玩家租约实际施力；死亡、移除、离开宿主、注销及连接失效撤销；旧句柄隔离，其他控制者与无实体租约不受影响 | 专服 r1 449 tick、客户端 r1 494 tick 通过，区域持续 READY，控制窗口 pose/velocity writes 均为 0 |
| 客户端与玩家范围 | 真实客户端加载及物理投影；正常相机玩家可取得绑定；注册 `ServerPlayer` 与 EmbeddedChannel 夹具覆盖指定生命周期 | 客户端 r1 45 个移动样本、三图已核对，正常相机绑定通过；EmbeddedChannel 不等于真实 TCP 断线/重连 |
| 持久化与旧功能回归 | 实体绑定不保存、原运动检查点恢复；既有有限力/PD、租期、区域与内容生命周期回归 | 三 JVM r1 在 67/42/23 tick、双区域 r1 在 114/25 tick 通过，退出与 stopping 门禁均通过 |
| 全量与成品 | 基础、原生及编辑器测试、三组结构 GameTest、开发/发布构建、源码与制品核对 | 基础 2507、编辑器 104、原生 JVM 20 项零失败/错误/跳过，Rust 12 项及三组 GameTest 通过；两种构建、成品及源码冻结核对通过 |

本批[基础测试](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-test-final.log) 16 秒通过，XML 核对 2507 项零失败、错误或跳过；[原生测试](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-native-tests.log) 4 秒通过，20 项原生 JVM 零失败、错误或跳过，12 项 Rust 通过。本批新增行为主要由真实 Minecraft 实体、事件和物理夹具验证，没有把既有 2507 项基础测试描述为新增实体绑定单测。

专服 r1 使用[完整隔离目录](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-server-20261013-p15-r1)，[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-world-server-r1.log)记录 449 tick PASSED、34 秒构建成功及全维度保存；[结果](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-server-20261013-p15-r1/native-structure-physics-server-result.txt)与[退出门禁](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-server-20261013-p15-r1/native-structure-physics-server-exit.txt)联合确认退出 0、服务器停止事件和线程结束。NPC 续期 PD 抬升后设置下一步有限力，再令控制者死亡，紧接的真实求解步只发生重力，租约 INVALIDATED 而区域继续 READY；新 NPC 的有限力按 `F × dt / m` 积分一次，消费后的空租约在移除时仍被即时撤销。另一个实体绑定成员与无实体租约都通过隔离断言，观察窗口直接姿态/速度写入为 0。

获取门禁验证未注册、死亡、异宿主及相同 UUID 冒名实体拒绝，保留原占用者和命令；同一实例同 tick 离开再返回不恢复旧租约，相同 UUID 替代对象须重新获取，旧对象 close 不影响新租约。死亡后立即 set/renew 均拒绝；注册 `ServerPlayer` 夹具还实际调用原版跨维度往返、断连标志、连接关闭和玩家注销路径。结果明确记录 `entity_player_fixture=embedded_channel`、`entity_player_actual_tcp_disconnect=false`，不扩大为真实 TCP 断线、重连或重生链全部通过。

客户端 r1 使用[完整隔离副本](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p15-r1)，[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-client-r1.log)记录 494 tick PASSED、45 个移动样本、2 分 24 秒构建成功及全维度正常保存，退出 0；[结果](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p15-r1/native-structure-physics-client-result.txt)包含全部新增实体标记和 `entity_camera_registered_acquire_release=true`。根代理通过实际界面确认实验世界提示并点击 `Create Backup and Load`，观察备份完成和地形加载，随后实际打开[下落前](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p15-r1/screenshots/native-structure-physics-before-72972.png)、[落地后](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p15-r1/screenshots/native-structure-physics-landed-72972.png)及[有限力悬停](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p15-r1/screenshots/native-structure-physics-controlled-72972.png)三图，确认投影下落、接触和悬停正常。本批首次专服/客户端均通过，无失败重跑；第十四批的首次客户端超时仍保留在历史章节。

三 JVM 持久化 r1 使用[同一完整世界](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-persistence-20261013-p15-r1)，prepare/unavailable/resume 的 PID 为 65304/30460/74720，分别运行 67/42/23 tick、用时 15/11/10 秒；[prepare](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-persistence-prepare-r1.log)、[unavailable](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-persistence-unavailable-r1.log)和[resume](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-persistence-resume-r1.log)均成功，三个 result 与 exit 联合确认 PASSED、退出 0、停止事件、线程结束和 `stopping_guards_verified=true`。保存时真实 ArmorStand 绑定 Anchor、另一成员保留无实体 Force 租约，记录 `entity_bound_lease_present_at_save=true`；随后冻结使两租约失效，夹具显式移除控制实体再正常停服。新 JVM 保留并恢复原运动，原租约和命令不恢复；夹具读取独立 checkpoint 中的控制者 UUID，创建同 UUID 新实体并显式获取/释放新租约，记录 `entity_binding_not_restored=true` 与 `entity_replacement_explicit_acquire=true`。这个 UUID 是验收夹具的 oracle 字段，不是生产 manifest 新增的绑定存档；本轮仍不把冻结后停服写成活动实体租约直接停服撤销的独立证明。

双区域 r1 使用[完整隔离世界](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-regions-20261013-p15-r1)，prepare/resume 的 PID 为 71752/82060，分别 114/25 tick、16/10 秒；[prepare 日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-regions-prepare-r1.log)与[resume 日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-regions-resume-r1.log)均成功，结果与退出文件确认 PASSED、退出 0、停止事件、线程结束及 stopping 门禁通过。错误区域操作、无租约控制与租约均保持隔离；只恢复 A 不推进 B、关闭 A 不清 B 且 B 持续运动等旧多区域行为通过。本批没有把这些 UUID 标签租约回归写成新增跨区域实体绑定专项。

三组 GameTest 的 [query](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-query-gametest.log)、[space](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-space-gametest.log)与[safety](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-safety-gametest.log)分别 10/12/9 秒通过，各自记录 `All 1 required tests passed`。它们验证既有结构查询、空间和交易安全回归，不代替本批实体绑定的真实物理专项。本批未运行旧 oracle 缺少实体字段的恢复分支，也没有重新执行旧 v2 迁移；相关向后兼容分支仅经源码复审，历史迁移证明仍限于对应批次。

最终[开发构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-build-dev.log)与[发布构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-build-release.log)分别 21/24 秒成功、退出 0。最终 XML 核对基础 2507、编辑器 104、原生 JVM 20 项全部零失败、错误或跳过，Rust 12 项通过。[证据收集器](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-finalize.py)已核对 8 个源码/探针文件与冻结版本一致、全部阶段日志/结果/退出门禁、客户端三图和成品。本批共 10 个相关文件，另 2 个为文档；限定验收已完成，用户已确认创建本批本地 Git 提交。

证据索引为 [validation](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-validation.json)、[artifacts](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-artifacts.json)、[source-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-source-manifest.json)与[all-files-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p15-all-files-manifest.json)。[开发 JAR](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/libs/academy-26.2.0-0.0.4-alpha-dev.jar)的 SHA-256 为 `d23ce0cbf36b9acc3c3fbf5674139a9bd34922ef970b57dc7be2eee4e3f2b64c`，[发布 JAR](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/libs/academy-26.2.0-0.0.4-alpha-release.jar)为 `13eea9362175675c51ffd2fec06e0294a298513bcd71d117363ed675ecfdcbf0`。新实体绑定类型、既有公共控制类型与固定 FFM 桥齐全，JAR 不含 DLL/SO/dylib；外部 DLL 与第十四批完全一致，SHA-256 仍为 `c8ab64f663ecb2ca9727ce94f57f9d90fe547a5c77c6b77518ca479f1def2dbd`，原生 ABI 与 manifest v3 不变。

两端沿用 `actions=server_fixture`、`actual_keyboard_actions=false`。三图是本批重拍的既有物理动作，不代表实体绑定 UI 或实际抓取输入；相机玩家仅验证正常绑定入口。客户端仍有既有 Perflib 009 与 OSHI 进程采集 NPE，不能宣称整个日志零异常。本批所有执行阶段均在首轮通过，没有新增失败重跑。后续重跑须使用全新 `*-p15-*` 隔离目录，并联合检查新增实体绑定断言、PASSED、正常保存和退出门禁。真实 TCP 断线/重连、旧技能迁移、Sable 适配、材料与承载、完整异常/网络矩阵仍属后续工作。

## 第十六批：气动牵引接入独立物理结构

本批在已有活动独立刚体上接入现有气动牵引技能，并抽出玩家、程序和非玩家可复用的公共牵引会话。空气操纵的通用效果是：从可信观察选定可见抓点，按当前姿态跟踪固定局部锚点，以有界弹簧阻尼力牵引，失效或取消后保留求解运动并撤销控制。玩家输入、技能等级、CP 与压缩空气消耗留在技能层，公共服务不自动支付资源或授予权限。

范围限于已加载、已有物理区域的结构；不会捕获宿主方块、创建或恢复求解区域、加载区块或替换旧结构结算。已有普通实体与旧快照结构继续走原有牵引路径。目标超出固定区域时明确拒绝并释放，不扩大区域、不传送或直接赋速。材料密度与方向锁定仍属后续工作；本段列出的动能投掷、高速喷流、程序节点事务迁移与 Sable 桥已于 P25/P27/P30–P32 完成。

> 状态更新（2026-10-18，HEAD 782d935b）：以下批次的“后续工作”多数已完成，详见结构技能接续记录 docs/structure_skill_migration_handoff.zh-CN.md。

验收将分别记录公共非玩家入口、服务端技能与资源路径、真实客户端输入。本批测试副本可通过现有内存按键配置临时将停止键设为 H 松开，以便输入工具执行真实 G 启动、滚轮调距、H 释放；完成与失败均还原原绑定，不写磁盘配置。该路径不等于默认 G 持续按住实测，默认 G 的按下/松开另验证一次点击，不伪造持续按键证据。

公共入口为 `StructureTraction.tryAcquire(actor, controllerId, hit, settings, permission)`。它按控制者当前眼位与朝向重新验证命中，绑定精确结构运行实例、局部抓点和实体控制租约。每次 `update(targetPosition, targetVelocity)` 重新检查实体、权限、质量、距离、遮挡、区域及宿主边界，成功后续期；失败为终态并释放，不自动抢占或重新获取。权限回调异常保留原异常，同时清理控制。旧会话关闭不能撤销新持有者。许可策略由可信服务器调用方提供，UUID 标签本身不授予权限。

气动牵引使用最大求解质量 32、弹簧系数 120（对应熟练度里程碑提升至 144）、阻尼 36、最大力 1000 和 3 tick 租期；这些是当前求解单位，不是材料密度换算后的千克。技能沿用每 10 个有效控制 tick 的 CP 与压缩空气扣费；支付失败在物理步之前撤销命令。普通实体、宿主方块与原生结构按距离参与选取和遮挡，前景结构占用时不会穿透选择后方物品。重复启动不叠加会话或收费，停止幂等。

目标边界检查使用当前旋转结构的完整 AABB，并检查平移到目标与按目标速度预测 0.05 秒后的位置；区域保留 0.25 格余量。不预测未来角运动，也不消除释放后的惯性，实际越界仍由既有求解区域保护处理。公共 API 可由注册的非玩家实体使用；本批没有修改精密操作节点的事务语义，不能把原实体速度回滚直接套到已发生碰撞的刚体上。

### 第十六批验证记录

专服使用[隔离 r6 世界](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-server-20261013-p16-r6)，[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-world-server-r6.log)记录 572 tick、42 秒、退出 0；[结果](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-server-20261013-p16-r6/native-structure-physics-server-result.txt)与[退出文件](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-server-20261013-p16-r6/native-structure-physics-server-exit.txt)确认 PASSED、正常保存、停止事件和线程终止。自动场景通过普通物品优先选取、后方无宿主碰撞物品不抢占已持有结构、重复启停与扣费次数、实际 PD 运动、滚轮共用服务端调距、停止后精确重力步、占用者隔离、遮挡/距离/资源失效撤销。NPC 场景通过权限拒绝与撤销、质量预算、越界终态、权限异常原样传播并清理精确租约、回调移除实体和真实刚体运动。结构控制窗口直接姿态/速度写入均为 0。

客户端使用[隔离 r2 世界](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p16-r2)，[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-client-r2.log)记录 4068 tick（其中手动窗口 3457 tick）、43 个移动姿态样本、4 分 11 秒和退出 0；[结果](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p16-r2/native-structure-physics-client-result.txt)含全部牵引标记。根代理通过 Computer Use 实际点击默认 G，随后在临时 `G_PRESS_H_RELEASE` 绑定下以真实 G 启动、3 个滚轮事件调距、真实 H 释放。滚轮实际为向上 1 次、向下 2 次：向外目标较低，拉近到 2 格后满足释放净空；没有把它写成一次滚轮完成全部检查。结构移动、CP/压缩空气扣费以及释放后下一步仅重力通过。默认 G 持续按住未执行，默认点击仅证明回调与无残留，不推断该点击的网络包到达；完整网络技能路径由后续自定义绑定证明。手动窗口仅临时将空气容量设为 2048，期间没有周期补充，结束恢复容量、原按键绑定与失焦暂停设置。

根代理已实际打开并核对本轮[下落前](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p16-r2/screenshots/native-structure-physics-before-79648.png)、[落地后](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p16-r2/screenshots/native-structure-physics-landed-79648.png)、[有限力悬停](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p16-r2/screenshots/native-structure-physics-controlled-79648.png)三图，投影、接触及悬停正常。这三张自动截图对应既有物理阶段，真实技能输入另由窗口操作、输入观察与服务端物理断言联合证明。自动玩家夹具仍为 EmbeddedChannel，不是实际 TCP 断线/重连专项。

本批保留失败轨迹，没有覆盖失败日志：`compile-r1` 因沙箱不能读取 JBR 的 `java.security`，在编译前退出；`compile-r2` 使用了当前映射不存在的客户端消息方法，改为 `sendSystemMessage` 后 `compile-r3` 通过。专服 r1 在后方物品与调距共用的 SCROLL 阶段会话消失；r2 在移除遮挡后未等待下一物理步重建地形便获取控制，被当前几何有效性门禁正常拒绝；r3 多次相对结构重定位观察者造成累积位移，最后落地前触发真实区域越界保护；r4 固定观察点使用较小 Z 偏移，调距目标太低而未获得释放净空；r5 直接证实后方物品与当前视线段相交，宿主推挤是基于生成位置与原版行为的解释。最终夹具固定初始观察点、等待地形恢复，并将后方物品独立为一完整 tick 的无碰撞/位置稳定/距离断言，再移除物品进行调距；生产边界与遮挡规则没有为测试放宽。

客户端 r1 在真实 G 创建会话后未取得原生结构而失败；窗口观察显示结构偏离准星，首次聚焦/抓取鼠标引起偏移是可能解释，没有作为已证实根因。r2 仅在默认点击结束后重新瞄准一次作为下一场景准备，并要求精确结构控制已激活后才进入持续验证，不合成技能包或自动启动。本次专服 r6 的生产与自动场景代码未因客户端手动分支修正改变，其成功结果按仓库复用规则保留。

最终[基础测试](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-test-final.log) 11 秒通过，[原生专项](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-native-tests.log) 1 秒通过，Rust 12 项通过。[三 JVM 持久化 r1](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-persistence-20261013-p16-r1) prepare/unavailable/resume 的 PID 为 81744/58164/29344，分别 67/42/23 tick、14/11/10 秒通过；无引擎启动保持检查点，显式恢复运动，瞬时控制与实体绑定不恢复。[双区域 r1](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-regions-20261013-p16-r1) prepare/resume 分别 114/26 tick、16/11 秒通过，逐区控制隔离和独立恢复均通过。两个流程的每个阶段结果与退出文件联合确认正常保存、退出 0、停止事件、线程结束和 stopping 门禁。这些属于既有持久化/区域回归，不代替完整异常恢复或新增牵引存档的专项。

三组 GameTest 的 [query](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-query-gametest.log)、[space](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-space-gametest.log)、[safety](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-safety-gametest.log) 分别 10/12/9 秒通过，每组记录 `All 1 required tests passed`。[开发构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-build-dev.log) 20 秒、[发布构建](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-build-release.log) 24 秒通过。最终 XML 核对基础 2517、编辑器 104、原生 JVM 20 项全部零失败、错误或跳过，Rust 12 项通过；新增纯参数/边界单测 10 项，真实技能与 NPC 行为由上述 Minecraft 场景证明。

[证据收集器](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-finalize.py)已核对 16 个源码/测试/验证文件与冻结版本一致、各阶段完整日志/结果/退出记录、人工阅图确认及成品。本批共 18 个相关文件（另 2 个为文档），限定验收完成，用户已确认创建本批本地 Git 提交。索引为 [validation](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-validation.json)、[artifacts](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-artifacts.json)、[source-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-source-manifest.json)、[all-files-manifest](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/native-physics-p16-all-files-manifest.json)。逐区重复 checkpoint 和隔离标记完整保存为数组，没有用最后一条覆盖先前区域的证据。

[开发 JAR](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/libs/academy-26.2.0-0.0.4-alpha-dev.jar) SHA-256 为 `62333152e7e52174bdf216f5d70741df454671d2e5dca7687246b98ac1db8bf1`，[发布 JAR](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/libs/academy-26.2.0-0.0.4-alpha-release.jar)为 `35a58568a9fa7f7ffbebf0e4f74ed6a0cb7698cb096c70a2853c1afaf1d3f9a1`。公共牵引与会话类型、实现和固定 FFM 桥齐全；JAR 不含原生二进制。外部 DLL 的 SHA-256 仍为 `c8ab64f663ecb2ca9727ce94f57f9d90fe547a5c77c6b77518ca479f1def2dbd`，原生 ABI 与 manifest v3 不变。日志中的既有 Perflib/OSHI 异常不等于本轮零异常日志。

后续仍需其他技能与精密操作节点迁移、技能驱动的捕获/创建/恢复、材料与承载、Sable 桥、真实多人异常网络矩阵、通用方块/菜单兼容和跨平台原生制品验收。本批通过仅说明气动牵引对已有活动独立结构的限定闭环，不提前宣告整个 P0/P1/P2 或混装完成。

## 第十七批：Sable 查询、有限力牵引与安装门禁

第十六批已提交为 `b6d20626`（接入气动牵引与公共结构牵引会话）。本批将公共 `StructureSpaces`、`StructureQueries` 与 `StructureTraction` 路由到经版本和成品 SHA-256 验证的 Sable 桥。未安装 Sable 时继续使用独立后端；安装受支持 Sable 后由 Sable 唯一负责结构内容、求解、渲染和原版方块交互。桥只查询已经加载的对象，不加载区块，不仿造空船生命周期，也不再创建 Academy 投影。

当前 Sable 后端支持已有结构的解析、姿态、逐格只读状态与完整分离 BE 数据、旋转射线、有限力牵引，以及 P24 的生产捕获（`SableStructureTransactions.java:47`，路由 `StructureBackendRouter.java:94`）。`createEmpty` 返回空，`load` 仅解析已经加载的对象，`unload` 返回 false，写入及直接设置姿态拒绝。静态化成功路径、拆分映射、接触归属、跨后端迁移仍未实现；不能把此桥写成完整 Sable 生命周期后端。既有动能投掷、高速喷流、磁场操作已迁移；三类能力各注册 7 个 `action/structure/*` 节点（`AcceleratorProgramNodeIds.java:22-28`、`ElectromasterProgramNodeIds.java:21-27`、`AeromanipProgramNodeIds.java:22-28`），extract/control/move/launch/release 已接入，rotate/staticize 的成功路径仍返回 unavailable。其公共服务与事务设计见[后续计划](D:/mcmodtest/AcademyCraft-Reborn-26.2/docs/block_structure_physics_and_sable_compat_plan.zh-CN.md)。用户已确定“终止”只释放控制，另设安全静态化。

> 状态更新（2026-10-18，HEAD 782d935b）：以下批次的“后续工作”多数已完成，详见结构技能接续记录 docs/structure_skill_migration_handoff.zh-CN.md。

安装门禁使用 `NATIVE`、`SABLE`、`MIGRATION_REQUIRED`、`INCOMPATIBLE` 四种状态。只要检测到 Sable，独立结构就不激活，也不能打开独立求解区域。只读预检发现旧 native 索引、永久槽占用、恢复文件或损坏索引时保留原文件并报告迁移要求；兼容 Sable 的既有结构仍可查询和控制。旧内部调用可以创建一个被禁用的 native Java 对象，但不能创建/加载结构、开启区域或覆写索引。这不等于已经把旧结构迁移到 Sable。

Sable 的局部原点固定在 plot 中心格，与随内容变化的质心分离。引用保留船 UUID，运行句柄绑定精确实例；同 UUID、同 plot 的卸载/重建不会复活旧句柄。宿主和结构均使用真实 OUTLINE 选择形状，返回世界命中点；宿主重合面优先。公共 `clearSightline` 只有在整个观察完整、已加载且没有宿主或结构轮廓阻挡时返回 true，空命中不等于透明。缺失区块、非单位缩放、读预算耗尽和查询回调期间的内容/姿态变化均保守拒绝。最多读取 16384 格、检查 256 个相交候选；桥不会为射线申请 ticket。

真实牵引在 Sable 的公开 prePhysics 子步事件执行。它读取实际质量、质心、线速度和角速度，用公共 PD 计算世界力，乘本次实际秒数，再变换为该 fork 所需的局部冲量和 plot 内抓点。不会额外乘质量、重复减质心、传送刚体或改写速度。控制者的精确实体实例、活动连接、宿主、质量、可见性、权限、边界和限时独占槽会在获取、更新及每个子步复核；编辑内容、卸载、实体离开、租期结束或关闭立即撤销未来力。Sable 边界是宿主高度及世界边界，独立后端仍是有 0.25 格余量的有限区域。

气动牵引沿用原有资源预算和公共入口。普通实体选择不再拿 Sable `Level.clip` 返回的内部 plot 坐标比较距离；选择及持续作用都检查公共完整视线。当前采用 OUTLINE，因此火把等无碰撞轮廓也会保守遮挡。资源/权限/形状回调若停止技能，捕获的精确会话或目标必须仍属于原上下文才继续操作；已经停止的上下文不会产生后续移动、特效或活动报告。

Suite 兼容限于固定版本的物理手杖锁定与拖拽。早期门禁同时验证 Simulated、Sable 和 Companion 的版本及 SHA，两个窄 Mixin 只读取现有 handler/drag 对象并拦截新占用；不会提前加载手杖 SavedData、写私有集合或抢占现有控制。未知版本、缺少有效 handler 时明确拒绝 Academy 牵引。外部锁定/拖拽排除 Academy，Academy 控制期间排除新手杖锁定/拖拽，释放后恢复获取。此结果不证明航空器控制器、Create 轴承和所有外部约束都已统一仲裁。

### 固定依赖与复现

[成品清单](D:/mcmodtest/AcademyCraft-Reborn-26.2/docs/sable_compat_artifacts.json)保存坐标、字节数和 SHA-256；[Gradle 集成](D:/mcmodtest/AcademyCraft-Reborn-26.2/gradle/sable-compat.gradle)在编译前检查固定 Sable、Companion 及嵌入依赖，在打包后递归检查桥存在且未夹带 Sable/Veil/ImGuiMC 类。普通测试 JVM 不含可选 API，`sableCompatTest` 单独加载实际 Companion。缺失或错误成品必须明确失败，不静默省略桥。

普通开发和 CI 现在需要预置这两个固定 compileOnly 成品。默认从 `../create-fork/.porting/maven` 读取，也可传 `-PacademySableFork=<目录>` 或 `-PacademySableMaven=<固定 Maven 目录>`；Suite 成品可传 `-PacademySableSuiteJar=<路径>`。这些依赖不会打包进入 Academy。运行组合只添加顶层 JAR：`sable` 为 Sable 本体，`suite` 为 Sable、Create、Suite，避免再次添加已嵌入的 Companion/Veil/Rapier。ImGuiMC 没有加入该运行组合。

使用 JBR 25，在新的 `run/sable-compat/<批次>` 目录运行：

```powershell
.\gradlew.bat verifySableCompatArtifacts testSableCompatBuildChecks -DisDev=true
.\gradlew.bat test sableCompatTest -DisDev=true
.\gradlew.bat runServerSable -DisDev=true -PacademySableRuntime=sable -PacademySableSmoke=true -PacademySableRunDirectory=run/sable-compat/<新批次>
.\gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite -PacademySableSmoke=true -PacademySableRunDirectory=run/sable-compat/<另一新批次>
```

专服 `<批次>/server` 需隔离的 EULA/server.properties，绑定 `127.0.0.1`、`level-name=academy-sable-smoke`；客户端使用 `<批次>/client/saves/Academy Sable Smoke`。本次客户端从已生成测试世界复制 `level.dat` 和 `data/minecraft/world_gen_settings.dat` 创建无旧区块的测试副本，未使用用户存档。26.2 只复制 level.dat 不足以加载世界。每次结果使用 CREATE_NEW，不能复用已经完成的目录。普通启动不启用任何验证夹具。

### 第十七批验证记录

| 场景 | 本批结果与证明范围 |
| --- | --- |
| 纯 Sable 专服 r4 | 41 tick、退出 0；真实技能扣费和刚体运动、释放保留速度/重力、NPC、独占及旧会话隔离、实际资源完成事件中 STOP/重新获取全部通过 |
| 纯 Sable 客户端 r2 | 42 tick、34 个客户端移动样本，真实 plot/姿态同步与截图通过；此轮早于新增资源回调专项，后者由专服 r4 与 Suite 客户端覆盖 |
| Suite 客户端 r1 | 55 tick、46 个客户端移动样本；手杖锁定/拖拽双向争用、释放重新获取、资源回调停止以及公共物理路径通过 |
| Suite 专服 r1 | 启动失败；固定 fork 三个模组构造时过早引用仅客户端的 BlockModelGenerators，未进入 Academy 场景，不算专服兼容通过 |
| 独立模式专服 r1 | 572 tick、退出 0；包括原气动牵引/NPC 场景，有限力/PD、地形、控制生命周期和保存/重载回归通过 |
| 独立模式 runClientDev r1 | 494 tick、退出 0；既有物理投影、接触和悬停三图通过，本轮未再次执行 G/滚轮/H 人工技能操作 |
| 基础和专项 | 基础 2527、Sable 11、原生 JVM 20 项均零失败/错误/跳过；Rust 12 项通过；query/space/safety 三组 GameTest 各 1 项通过 |

纯 Sable 的[专服结果](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/sable-compat/p17-sable-server-r4/server/sable-structure-server-result.txt)及[退出文件](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/sable-compat/p17-sable-server-r4/server/sable-structure-server-exit.txt)与[完整日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/sable-p17-server-r4.log)联合核验。Suite 的[客户端结果](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/sable-compat/p17-suite-client-r1/client/sable-structure-client-result.txt)、[客户端观察](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/sable-compat/p17-suite-client-r1/client/sable-structure-client-observation.txt)及[日志](D:/mcmodtest/AcademyCraft-Reborn-26.2/build/sable-p17-suite-client-r1.log)记录了实际手杖公开 API 调用；没有用伪造占用集合代替兼容测试。

根代理已实际查看纯 Sable [牵引截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/sable-compat/p17-sable-client-r2/client/screenshots/academy-sable-structure-62656.png)、Suite [牵引截图](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/sable-compat/p17-suite-client-r1/client/screenshots/academy-sable-structure-65236.png)，以及独立客户端的[下落前](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p17-r1/screenshots/native-structure-physics-before-72124.png)、[落地后](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p17-r1/screenshots/native-structure-physics-landed-72124.png)、[悬停](D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-smoke-client-20261013-p17-r1/screenshots/native-structure-physics-controlled-72124.png)。混装截图能观察旋转方块、施术者和牵引效果；输入工具只操作启动菜单，技能由服务器夹具调用。

上述自动场景明确记录 `actual_keyboard_actions=false`、`embedded_player_is_real_tcp=false`。嵌入玩家使用 NeoForge 公开测试网络配置、真实配置同步发送及 PLAY listener；它不是实际 TCP/UDP 登录客户端，不证明客户端配置 ACK、UDP 认证、断线重连或默认持续按键。相机客户端是真实集成客户端。`native_runtime_remained_absent=true` 只证明混装场景没有创建 Academy native runtime，不是 OS 层 DLL 枚举。Sable 内容由公开装配 helper 创建，仅用于隔离夹具，不能反过来作为生产捕获的守恒证明。

失败轨迹保留：编译 r1/r2 修正 26.2 的 ChunkPos 构造映射及构建中的缺失文件；专服 r1 的嵌入玩家未配置 NeoForge payload，r2 未执行配置同步，补齐公开测试连接生命周期后 r3/r4 通过。纯 Sable 客户端 r1 仅复制 level.dat，缺少独立 world_gen_settings.dat，世界加载失败，退出 0 也没有算通过；补齐测试副本后 r2 通过。Suite 专服 r1 的外部客户端类泄漏见[最小补丁及验证要求](D:/mcmodtest/AcademyCraft-Reborn-26.2/validation/sable_compat/README.zh-CN.md)，补丁尚未计为经过外部构建或专服验收。日志仍有既有 OSHI/Perflib 和外部模组弃用警告，不宣称零异常日志。

开发/发布构建及真实旧存档安装门禁的最终记录已由后续批次补齐。完整 Sable 生命周期、安全静态化成功路径（当前只有预检/拒绝，`StructureStaticizationRunner.java:101` 在预检通过后仍返回 unavailable）、跨后端迁移、一般外部约束、真实多人和完整异常/网络矩阵仍待后续实现与验收；Sable 生产捕获（P24）与动能投掷/高速喷流/磁场操作、三类行动节点迁移（P25–P33）已完成。

> 状态更新（2026-10-18，HEAD 782d935b）：以下批次的“后续工作”多数已完成，详见结构技能接续记录 docs/structure_skill_migration_handoff.zh-CN.md。

## 第十八批：整结构质量预算与精密查询入口（2026-10-13）

用户补充要求：未安装 Sable 的动能搬运也须创建新独立后端物理方块，不得继续默认创建旧结构实体。HEAD 已无 `KineticThrow.captureAndLift`；生产捕获改由 `VectorStructureService` 经 `StructureTransactions.capture/poll` 执行，承载代码在 `NativeStructureRuntime.java:980,992,1013`，按脚底接触点经旧/新姿态映射搬运站立实体。站立抖动是否完全解决仍须真实客户端运行验证，合成专服夹具不能单独证明。后端生产捕获、容量/恢复和承载的依赖顺序及后续工作见 [接续记录](D:/mcmodtest/AcademyCraft-Reborn-26.2/docs/structure_skill_migration_handoff.zh-CN.md)。

新增 `StructureDynamics` 后端中立整刚体观测和公共 `PneumaticStructureControl`。气流牵引不再固定拒绝质量超过 32 的结构：总质量上限按等级为 128/256/512，单体力上限 2000/4000/8000；质量不超过 2.25 时保持既有轻型响应，较重结构扩展 PD 增益并遵守全局上限，资源按整质量计费。每个结构只有一份控制预算，命中格子只决定受力锚点。

精密系统新增强类型 `STRUCTURE_REFERENCE` 与 `query/look_structure`、`query/structure_motion` 公共查询节点，输出世界质心、格/秒线速度、弧度/秒角速度和整质量。引用绑定运行期 activation，卸载重载不能恢复旧引用；现有图的实体/世界方块端口不变。三类能力各注册 7 个 `action/structure/*` 节点，其中 extract/control/move/launch/release 已接入（P30–P32），rotate 与 staticize 的成功路径未实现，三系 runtime 均返回 unavailable；不能将查询节点或显式拒绝计为行动迁移完成。

> 状态更新（2026-10-18，HEAD 782d935b）：以下批次的“后续工作”多数已完成，详见结构技能接续记录 docs/structure_skill_migration_handoff.zh-CN.md。

本批全量测试 2530 + Sable 11 通过；独立专服 r3 572 tick/退出 0，最终完整混装客户端 r3 的 25 格石头（总质量 50）通过真实牵引、手杖互斥、释放及程序查询，55 tick/退出 0，客户端观察 47 个运动样本。失败轨迹、命令、截图、构建和待验项统一记录在上述接续文档。用户以 Esc 停止界面自动化后，未继续窗口操作；本轮独立 `runClientDev` 和节点编辑器人工验证仍待进行。
