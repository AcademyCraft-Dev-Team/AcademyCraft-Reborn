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

现有技能继续使用短时操纵用的快照实体。新增的独立后端已具备真实 `ServerLevel` 方块容器原型，提供服务端内容访问、原版方块运行、保存生命周期、旋转投影与受限逐格交互；它尚未接到旧技能上。最终目标仍是独立安装时也能运行完整动态方块空间，安装 Sable 时通过可选后端统一接管，详见[完整设计方案](D:/mcmodtest/AcademyCraft-Reborn-26.2/docs/block_structure_physics_and_sable_compat_plan.zh-CN.md)。

2026 年 10 月 12 日的第一批实现增加了公共空间与单位契约、失败保留和回滚数据隔离，以及可组合的碰撞查询。第二批增加四个独占维度槽、实体无关的公共生命周期 API 和真实区块容器。第三批加入模型投影、任意四元数姿态下的逐格命中、作用域上下文与受限拉杆交互。第四批接入原版普通单箱的专用模型、开盖状态与真实库存菜单，第五批完成六种原版方块的受限逐格放置及对应验收。第六批已写入受限持续采掘、真实工具与掉落归属处理，最终采掘客户端 r3 通过，其余本批检查状态见下文。当前仍为 P0 部分完成；通用放置、采掘与菜单、原生刚体后端和 Sable 桥尚未实现。各批的历史验收与本批待验证状态分别记录于下文。

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

活动容器的模型通过独立 `NativeStructureProjectionEntity` 呈现在宿主世界。投影不保存、不持有方块归属、不承担碰撞、重力或结算；移除投影不会丢弃或还原容器内容。姿态与内容分开同步，姿态使用现有 double 原点和单位四元数，渲染与查询使用相同的当前姿态，尚未加入网络插值或物理驱动。

模型视图只包含 `BlockPos + BlockState`，不会发送完整 BE NBT 或库存。最多 4096 格，支持无方块实体的普通 `MODEL` 状态；方块实体目前仅允许原版熔炉、高炉、烟熏炉、漏斗、发射器与投掷器的状态模型。26.2 的箱子等专用模型也可能报告 `MODEL`，因此不能仅凭这个标记认定可呈现。存在流体、未支持的方块实体、非模型方块或超过上限时，整个投影隐藏，容器和内容继续保留。公共 `StructureSpaces.presentationStatus` 区分空内容、不支持内容、超限、宿主不可用和已显示。投影实体以可见内容中心作为跟踪位置；模型仍按结构原点变换，避免局部坐标较大时在错误区段被裁剪。未加载宿主中心区块时不为投影强制加载世界。

初始视图扫描跳过空 section，后续从 `LevelChunk.setBlockState` 收集实际变化格，覆盖原版机器、红石与计划 tick。脏格集合最多 8192 项，超过时转一次全量扫描；超过视图上限的扫描及时停止。当前状态变化仍同步完整有界视图，没有分片、单格网络差量或 BE 专用渲染。

`StructureQueries.raycast(host, from, to)` 返回 `StructureHit`，记录结构局部格、局部面、局部/宿主命中点、姿态、运行期 UUID 与几何版本。查询逆变换射线并使用实际原版 `OUTLINE` 形状，排除容器外围的虚拟屏障，处理宿主与其他结构遮挡；它不保证结构已呈现，也不授予任何操作权限。宿主查询遇到未加载区块会拒绝，不为射线加载区块。每次激活产生新的 `runtimeId`；姿态、API 写入或原生区块状态变化会使旧命中失效，持久引用保持不变。

`StructureContexts.call/run` 提供仅当前线程、最多 16 层的 `SpaceContext`，异常退出也会恢复外层上下文；它不修改玩家所属世界、位置或朝向。新调用方可用公共空间引用与内容访问接口集成精密操作或非玩家逻辑。

本批唯一接入的玩家操作为**双手空闲时右键原版拉杆**。客户端单独查询结构命中，服务端使用真实玩家的眼睛、朝向和交互距离重新计算；再检查结构激活/呈现版本、方块与实体遮挡、建造资格、宿主保护/世界边界，以及时停和精神控制门禁。公开可取消的 `StructureInteractionEvent` 携带真实玩家和空间上下文；事件后再次校验，防止监听器改变姿态、内容或玩家。拉杆调用原版容器内 `pull`，红石更新留在容器内，点击声转到宿主。动作期间禁止 API 写入、保存、卸载与姿态更改重入；回调异常会保留数据并隔离，不尝试撤销已经发出的副作用。

这个专用事件不等价于所有原版 `PlayerInteractEvent` 保护插件已兼容。第四至六批分别增加普通单箱、受限放置和受限采掘适配；通用物品、菜单和第三方交互仍需逐项处理，不能把局部命中直接交给绑定宿主玩家的原版交互包。旧技能尚未迁移到此容器，Sable 混装也尚未验证。

## 后续阶段

后续按[完整设计方案](D:/mcmodtest/AcademyCraft-Reborn-26.2/docs/block_structure_physics_and_sable_compat_plan.zh-CN.md) 推进，当前未完成项包括：

1. 完成第六批持续采掘的实际验收，再将受限放置、采掘与单箱菜单扩展到更多方块、工具及运动期间的操作；不以白名单场景代替完整原版语义。
2. 持久交易协调、明确的隔离数据恢复入口，以及跨存储崩溃恢复验证。
3. 补齐通用原版放置、破坏、菜单、BE 专用呈现、局部实体与流体边界，以及分区同步/渲染闭环；扩展容量和回收协议须先处理永久槽的内容归属。
4. 完整刚体后端、角速度、旋转承载、拆分、质心/惯性及材料响应；全方向网格还原需方块及 BE 旋转适配。
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
