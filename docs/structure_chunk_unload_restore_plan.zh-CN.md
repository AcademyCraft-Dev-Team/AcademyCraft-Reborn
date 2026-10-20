# 本地物理结构在宿主区块卸载前回正：设计方案

日期：2026-10-20

状态：**DESIGN ONLY。未添加自动回正逻辑、配置、事件注册、Mixin 或运行时 API。**
适用版本：当前工作区 Minecraft 26.2 / NeoForge 26.2.0.70。

本方案处理本项目拥有的物理化结构。目标是：宿主区块不再需要驻留时，尽量在区块仍然可用的阶段，把整座结构回正到附近的方块格；无法安全放回时，保存完整结构并暂停，释放无必要的物理资源，等待目标区域自然加载后重试。不得以清空结构、掉落全部物品、覆盖地形或无限强加载作为失败兜底。

本轮只提交方案。下文中的类型、方法、配置名、阈值和测试均为建议，不能当作已经实现。

## 1. 已核查的现有实现与不能直接复用的行为

| 位置 | 当前行为 | 对方案的约束 |
| --- | --- | --- |
| `StructureBackendRouter.captureTarget(host, physical)` | 新建本地物理结构使用 `SubLevelCaptureTarget`；旧 `native_level` 引用仍保留原后端 | 默认先实现 SubLevel，旧数据单独适配，不能按当前默认后端误认既有结构 |
| `SubLevelRuntime` / `SubLevelManifest` | 方块和 BE 在 plot 区块；清单保存 plot、位姿、局部边界和状态；启动恢复清单中的结构 | 需要独立持久化的“等待回正”状态；不能把普通 `INACTIVE` 当作事务暂停或已恢复 |
| `PlotStorage` | `claim/open` 会加载 plot；`release` 同时释放票据并 `allocator.free`；下一次新分配会清理旧内容 | 必须分离“内容所有权/保留编号”和“内存驻留/区块票据”。失败待处理不能调用现有 `release` |
| `NativeStructureTickets` | plot 使用无超时 ENTITY_TICKING 票；宿主地形使用无超时 FULL 碰撞票；两者均可保持维度活动 | 仅等待原版自然卸载事件会形成循环：结构自己的票据使事件永远不发生 |
| `TerrainColliderCache` | 按同一宿主中所有活动结构需要的区块维护一组碰撞票 | 挂起一座结构后应重算共用需求；不能直接撤销仍被其他结构需要的票 |
| `SubLevelRestoreService.staticize` | 以 `floor(worldOrigin)` 加原局部坐标放置，未应用当前旋转；只接受空气；迁移 plot 实体后删除 SubLevel | 当前入口不是完整的“最近姿态回正”。不得直接从卸载回调批量调用 |
| `SubLevelStaticizeJournal` | 日志主要记录目标坐标；未提交恢复会清理这些位置；提交后完成结构删除 | 缺少目标前后镜像、内容摘要和精确回滚归属；不能为新自动流程提供足够的防覆盖证据 |
| `SubLevelRestoreService` 的提交顺序 | 当前路径未在删除源拥有者前显式等待全部目标宿主区块落盘 | 新流程需要目标保存完成屏障，不能认为写了日志就等于 Minecraft 区块已保存 |
| `TargetedChunkDurability.save` | 使用 `level.getChunk` 获取目标，再保存并等待 `synchronize(true)` | 不能原样用于“禁止强加载”的自动流程；需要已加载 chunk 的保存入口和缺失即延期的契约 |
| `SubLevelRuntime.deactivate` | 移除物理 body、取消运动控制，但保留 plot；不是完整的交互、碰撞、网络撤销 | 不能以此单方法实现待处理挂起，否则可能留下幽灵碰撞或客户端投影 |
| `NativeStructureRuntime.unload` | 关闭交互、保存、标记未加载并释放存储票；保留永久 slot 和内容 | 可借鉴“卸载不等于删除”的所有权模型；它没有把块放回宿主 |
| `NativeStructureRuntime.discardEmpty` | 只允许已卸载、空、无物理附件且无恢复问题的 slot 释放 | 旧后端只有完成回正、清空及持久化后，才能走正常容量回收 |
| `BlockStructureManager.restore` / `BlockStructureGridAlignment` | 旧快照实体支持最近 yaw 90° 回正及带前后镜像的内存回滚 | 可复用纯整数旋转辅助逻辑；不可把实体消失或内存回滚等同于跨区块崩溃事务 |
| `DurableFile` | 优先原子替换，失败后存在删除旧文件再移动新文件的降级路径 | 新所有权提交凭证不能依赖“替换过程绝不会出现空窗”的假设 |

本次检查的源码见文末索引。以上是静态审查结论，不是本轮新增功能的测试结果。

## 2. 触发语义和区块卸载时机

### 2.1 哪些变化算触发

以**服务端宿主维度的区块驻留需求**为准。客户端镜头朝向、FPS、视锥裁剪、实体渲染距离和单个客户端隐藏结构不触发回正；玩家仅从模拟距离退到仍有 FULL 区块的加载距离内，也不等于区块将卸载。

为每座结构维护三组区块：

- `occupiedChunks`：当前变换后实际非空气单元覆盖的宿主区块，包括无碰撞的附属植物；宽相位 AABB 可筛选，但内部空洞不能制造占用。
- `destinationChunks`：候选回正方案真正需要写入的区块。
- `physicsSupportChunks`：碰撞预测及邻域需要的区块。这一组可以更大，但不能单独触发整个结构回正。

**建议默认规则**：`occupiedChunks` 中任一区块失去“排除本地结构系统票据后的外部 FULL 需求”，整座结构进入回正候选。跨多个区块的结构必须整体处理，不能只恢复先卸载的那一半。该规则允许很长的结构在边缘部分将离开加载区时提前回正，而不是等中心区块最后卸载。

玩家远离、登出、传送走，或者第三方区块加载器撤票，都会改变上述外部需求。出生点票、`/forceload`、其他模组真实加载器仍算外部需求；存在这些外部需求时不强制回正。单纯“没人看”不是此功能的目标。

初始建议候选去抖 20 tick，期间使用结构已经拥有的驻留，不额外扩展范围；目标是避免玩家在区块边界往返时反复回正。此数值需要游戏测试调整。外部需求恢复且尚未开始提交，可取消候选，保持结构可操作。

### 2.2 为什么不能在 `ChunkEvent.Unload` 中放方块

已核对本地 `minecraft-patched-26.2.0.70-sources.jar` 的 `ChunkMap.scheduleUnload`：先标记 `LevelChunk` 未加载，再调用保存，然后发布 `ChunkEvent.Unload`，之后才执行宿主卸载。官方事件说明也明确服务端 Unload 发生在 chunk 保存之后。事件不是可取消的“即将保存”通知。[NeoForge 26.2 ChunkEvent 源码](https://github.com/neoforged/NeoForge/blob/26.2.x/src/main/java/net/neoforged/neoforge/event/level/ChunkEvent.java)

因此：

1. 正常路径由宿主生命周期协调器在外部需求消失后、释放自身票据前执行。
2. `ChunkEvent.Unload` 只用于记录异常漏网、失效缓存和待处理区块索引；不能在其中写回、同步加载相邻区块或阻塞等待区块。
3. `ChunkEvent.Load` 只标记重试候选，下个服务端 tick 再核验 FULL/可写状态。不能在 Load 回调直接访问相邻区块并运行恢复。
4. 若未来需要额外的 `ChunkMap` 防线，Mixin 只能在尚未进入实际保存/卸载阶段时提交调度信号，不在异步保存回调中执行世界修改；必须针对 26.2 的调用顺序写集成测试。

## 3. 解除自身票据的循环依赖

不能通过“临时移除所有结构票，看哪些区块会卸载，再把票加回来”探测需求。这个办法会让真实区块生命周期产生副作用，并且可能打断别的结构。

建议加入只读的 `ExternalChunkResidency`：

1. 根据 TicketStorage 的真实加载票建立排除本地结构票的传播视图。排除本地宿主碰撞票、自动回正短期持有票，以及本地结构自身产生的其他驻留来源。
2. 按原版加载等级传播规则判定 FULL。不能只检查某 chunk 上直接存放的票，因为相邻区块上的票会传播加载需求。
3. 票据增减、到期清理、批量删除、等级替换及维度关闭都标记脏源；仅增量更新受影响的结构区块，不每 tick 枚举全世界方块。
4. **不要覆盖** `TicketStorage.setLoadingChunkUpdatedListener`。该槽位已被原版距离管理器使用。实现阶段应采用不修改原逻辑的观察式适配器，或在完整快照基础上维护旁路传播器。
5. 观察器失配、传播预算不足或暂未追上原版变化时返回 `UNKNOWN`，不能把未知解释成可以卸载。需要日志、指标和有界重新对齐机制。
6. 所有本地结构票都应被排除；不能让 A 的结构票成为 B 的“外部使用者”，又让 B 的票反过来永久保留 A。

在队列中冻结后，协调器停止该结构的物理步进和新的运动命令。完成或安全挂起后，立刻从共用地形需求中移除它，再由 `TerrainColliderCache` 正常收缩票据。已有真实外部票保持原样，绝不删除其他系统的票。

短期事务票只能保持**已加载的** source/target 区块，不能用它异步拉起此前缺失的区块。建议最长 40 tick；耗时超过预算应安全退回持久化待处理，而不是无期限延期。磁盘 I/O 已启动时不得贸然释放事务保护，必须先完成可恢复的阶段记录。

## 4. 回正坐标、方块朝向和冲突

### 4.1 默认采用保持世界 UP 的四向回正

建议默认 `UPRIGHT_NEAREST_YAW`：候选是绕世界 Y 轴旋转 0°、90°、180°、270°，消除横滚和俯仰，取与当前姿态最接近且可放置的候选。角距离相同使用固定顺序，防止重启后候选变化。方块状态通过对应的 `Rotation` 和可注册适配器变换。

不默认使用立方体的全部 24 种正交旋转。把门、床、火把、箱子、流体容器倒放，需要超过普通 `BlockState.rotate` 的适配能力。可为支持任意轴旋转的方块另加 `NEAREST_ORTHOGONAL` 策略，但不因此强行改变不支持方块的语义。

对局部质心 `c`、当前质心世界位置 `C` 和候选整数旋转矩阵 `Q`：

```text
alignedOrigin = componentRound(C - Q * c)
targetCellCenter(p) = alignedOrigin + Q * (p + (0.5, 0.5, 0.5))
targetBlock(p) = componentFloor(targetCellCenter(p))
```

这里 `p` 是局部方块位置，不是局部中心；实现应使用整数轴置换/符号和确定性的取整，避免浮点误差导致重复目标格。必须统一 Minecraft yaw 与 JOML 的旋转方向，不能仅凭角度正负拼出坐标。默认保持当前质心附近位置，**不回传到最初捕获坐标**。过去捕获位置可能早已重新建造。

算法按“旋转误差、平移距离、固定旋转序号”排序。默认只试这些格对齐候选，不扫描远处空地，不向地下或上方任意平移。若以后允许相邻一格搜索，应作为显式策略，并把搜索范围写入预览结果。

### 4.2 全体检查，全体成功

预检必须包括：

- 所有目标 chunk 已加载、FULL 且尚未开始卸载；世界边界、高度和维度存在。
- 不存在重复目标坐标；完整 cell 数量、内容摘要和 BE 摘要一致。
- 默认只写空气，水、可替换植物、已有结构单元或其他非空气方块都算冲突。不能因为“低硬度”就自动破坏或产生战利品。
- 检查其他活动结构的真实碰撞形状和保留中的回正目标；宽相位大框相交不应错误拒绝空洞。
- 任何目标实体碰撞、安全空间不足、玩家站立或骑乘转换未完成时延期。不要为了回正把玩家推入实体方块；玩家、实体传送必须有独立且受验证的承接方案。
- 基于整座候选的虚拟方块视图检查植物支撑、双格植物、床、门、双箱等依赖，不能把中间放置状态当作最终状态。
- `BlockState`、BE 数据、方块/流体计划 tick、实体附件、外部方块引用必须有已知迁移契约。不能迁移的内容留下明确 `UNSUPPORTED_CONTENT`，保留原物。
- 自动操作仍必须遵守宿主保护接口。用持久化拥有者/来源身份和 `AUTO_CHUNK_UNLOAD` 原因进行授权；不能把 `actor == null` 当成可绕过领地的通行证。需要在线玩家才能判断的保护系统应延期。

回正本身是搬迁，不是挖掘：不触发 `BlockDropsEvent`、不掉落箱子内容、不消耗工具。若目标植物在完整支撑检查下不能存活，默认整结构待处理。未来若添加“无法保留的装饰改为掉落”策略，必须独立授权并采用一次性输出账本，不能藏在卸载兜底中。

## 5. 生命周期、技能和玩家交互

建议状态由专用恢复协调器维护，不直接复用现有 `SubLevelState` 的序号：

| 状态 | 可见性/所有权 | 允许的操作 |
| --- | --- | --- |
| `LIVE` | plot 是唯一可交互拥有者，正常物理投影 | 原有技能、挖掘、放置、菜单 |
| `CANDIDATE` | 仍由 plot 拥有，等待去抖和预检 | 可取消候选；内容变化使计划失效 |
| `FREEZING` | 阻止新操作，读取最终一致快照 | 关闭会话、取消控制、保存最终内容 |
| `PREPARED` | plot 仍是所有者；宿主目标被事务保留 | 仅事务写入、校验、回滚 |
| `HOST_DURABLE` | 目标已保存，但尚未发布；源仍不可访问 | 完成所有权提交或回滚 |
| `COMMITTED` | host 为唯一拥有者；源 plot 为不可访问的清理副本 | 只允许幂等清理，不能重放原技能 |
| `PENDING` | 已保存的 plot/事务记录拥有内容，无刚体和宿主碰撞 | 自然加载后的重试、管理员只读诊断 |
| `QUARANTINED` | 所有权不确定或证据冲突，保存源与日志 | 禁止自动恢复、自动掉落、重新分配 plot |
| `DONE` | 仅普通宿主方块；plot 已安全回收 | 原版方块玩法 |

冻结必须覆盖 `SubLevelMotionController`、`SubLevelDirectControl`、通用 `StructureDirectControl`、traction/抓取、挖掘会话、容器菜单和精密操作节点：

1. 先设置事务门禁，再关闭菜单并处理玩家 carried stack，避免关闭回调重新开箱。
2. 保存实际控制器状态，然后撤销本次激活的租约和引用，发送明确终止原因。旧引用不得自动指向新方块或后续复用的 plot。
3. H 正在 200 tick 抬升/悬浮时，若合法控制者仍使区域有外部驻留，不进入候选；控制者远离、死亡、换维度或失去合法引用后，撤销控制，再按普通回正处理。技能不能凭持有引用无限保 chunk。
4. 精密节点需要返回“已回正/等待回正/结构不可用”的可区分结果，释放能耗与控制租约，不能在下一 tick 把结构自动重新物理化。
5. 新事务开始后收到的旧挖掘包、姿态包、续租包按 generation/activation 拒绝；不能只检查 UUID。
6. 暂停或提交时，服务端实体碰撞索引、预测历史、承载状态和客户端投影必须一起撤销。客户端方块更新与投影删除应带转换序号/屏障，让客户端在目标块数据可用后切换，避免一帧双碰撞或长时间无碰撞。

正常停服不等同于玩家远离。进入 `ServerStoppingEvent` 后不批量回正世界；停止接收任务，完成可完成的事务阶段，保存剩余队列和源内容，由原版最终保存处理区块。`ServerStoppedEvent` 仅清理本次服务器实例资源，不能再进行世界写入。

玩家换维度只改变其原维度的外部需求和控制资格；结构仍属于原 host。动态维度关闭/卸载时，如果无法在其有效期内完成回正，保存 `PENDING`，不新建维度、不在主世界代放。重新加载另一存档不能复用上个 `MinecraftServer` 的队列、票据或控制引用。

## 6. 存储和事务：保持唯一所有权

### 6.1 先分离 reservation 与 residency

SubLevel 的 plot 编号必须在持久化清单中一直被保留，直到回正确认完成。建议拆成：

- `PlotReservation`：持久身份和唯一内容所有权；没有票据也能保留编号。
- `PlotResidencyLease`：暂时加载/可 tick 的区块与实体资源；关闭不会使编号可重新分配。

启动时先从所有 host 清单和未完成事务重建 reservation，包括当前缺失的维度；再按需打开允许恢复的驻留。`PENDING` 项不能在启动时被当前 `restore()` 自动重建刚体、强加载 host 或为整个 plot 创建永久 ticking 票。

挂起前先冻结 plot 中相关 BE、计划 tick、实体与输出，保存块、BE、tick 和实体存储，等待完成后释放 residency。这样漏斗、熔炉、随机 tick 或其他自动化不能在快照之后继续改动唯一副本。已有 plot 是二维区块租约：需校验 plot 的完整纵向实体/计划 tick 所有权，不仅扫描渲染 cells。

### 6.2 新日志应记录什么

建立单独版本的 `GridRestoreJournal`，不要原地把旧 `SubLevelStaticizeJournal` 记录解释成新格式。最低字段：

- 事务 UUID、world/server session 边界、后端/结构 UUID、持久 generation、host 标识、plot 或旧 slot 的所有权凭证。
- 源 revision、完整 cell/BE/计划 tick 摘要、源原姿态和速度、选定的整数旋转及对齐格。
- 每个目标格的前镜像与后镜像、BE 完整内容、完整目标 chunk 集合。
- plot 内所有实体 UUID、来源/目标维度与坐标、迁移结果凭证；挂载关系按整个实体树记录。
- 阶段、格式版本、校验和、上次失败原因、重试条件；未完成输出账本的引用。
- 所有权提交凭证与源清理完成凭证，二者不能仅靠是否存在临时文件推断。

完整镜像可能超过现有 1 MiB 的 `StructureTransactionJournal` 限额。应使用有上限的分片及总索引摘要，并在**任何世界修改前**完成体积预检；超限返回保留，不无限制扩容。

建议用不可变阶段记录/提交凭证或保留上一代的双文件协议。首次 PREPARED 镜像完整落盘后才能改 host；COMMIT 凭证不得通过删除唯一旧记录再移动临时文件产生“恢复时看不见任何所有权记录”的空窗。复用 `DurableFile` 前需专项修正并验证这一点。

### 6.3 正常提交顺序

1. 获取同一结构的互斥事务门禁和所有目标格的保留，暂停模拟，关闭交互，最终复核权限与源 revision。
2. 保存完整源快照与 PREPARED 日志；日志写入失败时不改 host。
3. 在服务端线程写目标块和 BE。使用搬迁事务作用域抑制源容器掉落，隔离目标交互与 tick；按目标前镜像 CAS 验证，任何异常停止继续写入。
4. 迁移计划 tick 和 plot 实体。不得把 `teleport()` 返回非空直接当作成功，需核验目标登记和 UUID；玩家不作为 plot 内自动迁移的普通实体。
5. 验证所有目标后镜像，保存所有目标 chunk 以及涉及的实体/计划 tick 存储，等待实际保存完成。不得仅调用 `setChanged()` 后继续。
6. 写入不可变 COMMIT 凭证，所有权转到 host。此前 plot 仍为来源；此后 plot 禁止恢复交互和产生输出。
7. 从持久结构清单移除或记录 tombstone；移除物理 body、客户端投影、选择/碰撞索引和控制引用；发布目标方块更新并按完整结构顺序恢复邻居通知。
8. 幂等清理源块、BE、计划 tick、实体残留并保存；验证无未完成输出/迁移后，才解除 plot reservation。保留清理凭证至所有源/目标保存确认完成。
9. 释放剩余事务驻留，回到原版区块生命周期。

在多 tick 写入/异步 I/O 期间，目标块不能对玩家、漏斗、方块 tick、实体拾取、邻居自动化变成第二份可消费库存。既有 bulk flags 并不自动证明全部 mod 回调都被隔离。实现阶段必须建立有效门禁，或限定单次原子写入的内容范围；未满足时不能启用自动功能。

### 6.4 失败与重启

| 断点 | 恢复规则 |
| --- | --- |
| 计划未写入 | 源保持原样；不需要接触 host |
| PREPARED 已保存、host 未改或部分改 | 源保持所有权；只对仍匹配事务后镜像的目标做精确回滚；先核验源仍完整 |
| host 已落盘、COMMIT 未保存 | 仍按未提交回滚，恢复前镜像后保存；不能发布两份内容 |
| COMMIT 已保存、源未删除 | host 拥有；源标记不可访问并继续清理，不重新放块、不重新迁移实体 |
| 已发布、清理未完成 | 只清理有所有权凭证的源副本；玩家后续已挖掉的目标不能被“恢复”出来 |
| host 目标不匹配已知前/后镜像 | 隔离并保留证据，绝不能清空气或覆盖玩家新放的块 |
| 实体迁移结果不确定、物品输出已尝试但回执不明 | 不重发；保存 UUID/账本证据并隔离，避免复制 |
| 磁盘失败、清单损坏、未知格式 | 不释放 reservation、不继续改世界；标为需人工恢复，不假定空存储 |

启动恢复只建立索引和保留，不强加载宿主区块。若目标尚未加载，保持待处理；收到加载信号后下个 tick 再验证。若 COMMIT 后需要清理 source，可以有界按需打开**已知属于该事务的内部存储**，但不能借此强加载宿主地形。

崩溃时不同 region 文件并非天然原子提交。这里承诺的是有日志可恢复的唯一逻辑所有权和保守隔离，不应在尚未覆盖故障注入前宣称任意 mod 回调、任意磁盘故障都能无损自动恢复。

## 7. 待处理、预算和可观察性

`PENDING` 保存选定目标映射，失败后不随意更换目标。重试前重新核验权限、占用、源内容与全部 chunk；若需要重新规划，应生成新计划版本，而不是悄悄移动结构。

建议按 `(host, destinationChunk)` 建索引，优先由自然加载、目标方块变化、保护权限变更和显式管理操作唤醒。保留低频有界兜底巡检，不能每 tick 扫描全部 pending cells。多结构目标相交按事务序号稳定排序，避免互相饿死。

预算初值只用于测试调优：

- 每 tick 最多启动 1 个事务，预检最多 2,048 cells，目标世界写入分阶段受预算限制。
- 同宿主同时持有少量事务；全局 I/O 队列有上限，不能与每座结构同 tick 启动 `save(true)`。
- 采集候选、完成、冲突、缺 chunk、冻结耗时、写入/保存耗时、释放票数、待处理数和保留 plot 数。
- 管理查询提供结构 UUID、host、当前/目标区域、阶段、首个冲突位置和最近失败原因；不把内部 plot 维度坐标泄露为玩家传送目标。
- 数据存储目录可继续位于 `world/data/academy/...`；实际路径和格式在实施阶段定稿并提供迁移说明。

挂起后允许宿主自然卸载。玩家返回时如果全部目标仍有冲突，内容继续保留，管理诊断必须能发现它；不能让“不可见但占着库存”成为无提示的永久状态。此情况可以提示拥有者待处理数量，自动功能默认不生成聊天刷屏。

## 8. 本地默认、legacy 与 Sable 范围

| 后端 | 建议实施方式 |
| --- | --- |
| `academy:sublevel` | 首个完整实现，覆盖普通活动、睡眠以及内容非空但无 body 的本地结构。完成阶段 P0–P4 后才默认启用 |
| `academy:native_level` | 第二阶段适配相同 planner/事务契约。保留旧 slot owner marker、恢复/隔离信息及物理 region cohort 约束；不能绕过 `RECOVERY_REQUIRED` 把旧铁块盲目回正 |
| 旧 `BlockStructureEntity` 快照 | 保留现有落地恢复语义；如要纳入卸载自动化，先把实体 NBT 源所有权接入持久事务，不能在普通 entity unload/discard 事件里直接恢复 |
| Sable 联动结构 | **明确排除本方案。** 不监听并静态化 Sable 的普通结构，不改其区块票、存储维度或卸载政策。本轮 H 抬升修复是另一项工作，与本方案无关 |

安装 Sable 时遇到保存的本地结构，必须按其持久后端身份和路由兼容状态判断；不能因当前选择 Sable 就把本地记录当作 Sable，也不能绕过 `MIGRATION_REQUIRED`。尚未迁移/无法加载的本地数据保持保留并提示，不能自动转换到另一后端。

初始实验配置建议默认关闭。验证通过后的产品默认可为 SubLevel 开启；旧 native 在专门回归通过前保持显式启用。配置变更只影响下一次候选判断，不能中途撤销已提交事务或丢弃 pending。

## 9. 建议公开契约与内部模块

以下只是签名草案，**本轮不创建这些 Java 文件**。公共 API 放在 `org.academy.api.common.structure`，内部实现放在 `org.academy.internal.common.structure.restore`；不要在 API 签名泄露 Sable 类型、存储 ServerLevel 或可写 live BE。

```java
interface StructureGridRestoration {
    RestorePreview preview(ServerLevel host, StructureRef reference, RestoreOptions options);
    RestoreRequestResult request(ServerLevel host, StructureRef reference,
                                 RestoreOptions options, RestoreCause cause);
    Optional<RestoreStatus> status(MinecraftServer server, UUID operationId);
    CancelResult cancel(MinecraftServer server, UUID operationId);
}

interface StructureRestorePermission {
    PermissionDecision check(RestoreActor actor, RestoreCandidate candidate);
}
```

契约：

- `preview` 只读、不加载 chunk、不获取票、不改变引用；结果附 source revision、目标摘要、缺失/冲突与不支持内容。
- `request` 服务端线程调用；同一结构/同一计划幂等合并，返回操作 ID 或明确拒绝原因。不能以 `boolean` 混淆入队成功和方块已恢复。
- `status` 只读、跨后端统一、返回明确阶段；持久操作身份跨重启仍可查询。
- `cancel` 只允许提交前取消；已有部分写入必须完成受验证的回滚后才返回成功。
- `RestoreActor` 区分玩家、非玩家实体、程序所有者和维护触发，授权仍可核验；不能伪装真实玩家。
- `RestoreOptions` 包含对齐/冲突策略和已加载限制；自动调用固定 `AIR_ONLY`、`LOADED_ONLY`，不允许持有者通过参数绕过。
- `RestoreRequestResult` 至少区分 `QUEUED`、`ALREADY_PENDING`、`UNSUPPORTED_BACKEND`、`MISSING`、`STALE_REFERENCE`、`BUSY`、`PERMISSION_DENIED`、`RECOVERY_REQUIRED`。

内部建议拆分为 `ExternalChunkResidency`、`GridAlignmentPlanner`、`RestoreContentAdapter`、`RestoreCoordinator`、`GridRestoreJournal`、`PlotResidencyManager` 和 `PendingRestoreIndex`。纯 planner 不依赖 native DLL，可独立测试。生命周期协调器统一调用，H 和精密节点不各自复制恢复逻辑。

## 10. 实施阶段和完成判据

| 阶段 | 工作 | 合入判据 |
| --- | --- | --- |
| P0 | 只读外部驻留判断、候选预览、索引和诊断 | 与原版距离管理结果在排除结构票后吻合；不加载、不写方块、不改变现有行为 |
| P1 | reservation/residency 分离、持久 pending、完整挂起/唤醒 | 重启、缺维度和 plot 再分配压力下内容不丢失；挂起无刚体、无幽灵碰撞、无无限票 |
| P2 | 整数回正 planner、内容适配、完整镜像事务 | 所有失败点可恢复或保守隔离；库存、实体、输出唯一所有权成立 |
| P3 | 手动调用统一恢复 API，接入菜单/技能/节点终止和客户端转换 | 手动恢复与取消在真实客户端通过；网络延迟下无双体、幽灵块与旧引用复活 |
| P4 | 接入自动候选队列，实验开关下实测 | 多玩家、区块边界、维度切换、停服/崩溃、低 TPS 和大量结构通过；再讨论默认开启 |
| P5 | legacy native / 旧实体适配 | 专门的旧存档和 region cohort 回归完成；无混后端修改，Sable 零自动回正 |

最低测试矩阵：

1. **区块语义**：单块、跨 2/4 chunk、内部空洞、只邻域离开、中心仍加载、普通移动、传送/登出、两玩家不同距离、FULL 但不 ticking、`/forceload`、第三方票、多个本地结构相互覆盖。
2. **回正**：四个 yaw、正负坐标/半格取整、倒置/倾斜、非对称形状、稀疏结构、极限高度/世界边界、目标重复检查、相同计划跨重启确定性。
3. **内容**：铁块、草/种子作物、树叶、双高植物、门、床、楼梯/台阶、含物箱/双箱、熔炉/漏斗、带计划 tick 和流体的块、mod BE、BE 外部引用、plot 掉落物及挂载实体树。
4. **冲突**：目标固体/水/植物、其他物理结构、目标玩家/实体、回调变更目标、权限取消、关闭容器回调重入、I/O 等待期间挖掘/放置与机器取物。
5. **控制**：H 抬升/悬浮/投射、抓取、吸引、精密节点持续续租、所有者死亡/离线/换维度、旧 generation 包晚到；不能自动重捕获已回正块。
6. **故障注入**：每个日志/目标写/BE 写/实体迁移/目标 flush/COMMIT/源清理/清单修改/释放 slot 的前后中断；对每种断点做至少一次硬终止再进入副本。验证源与目标总物品数及 UUID，不只检查方块数。
7. **磁盘与边界**：源/目标保存失败、原子替换失败、目标镜像被外部修改、日志超限/损坏、未知版本、host 缺失、已提交后玩家挖块再重启，均不得复制或盲目覆盖。
8. **性能与驻留**：至少复用 `test2` 的 18 结构/1,807 cells 副本，另测百座结构逐步离开。记录 tick P50/P95/P99、最长冻结、磁盘等待和 ticket 数；稳定无候选时不进行逐 cell 扫描。玩家离开后无正常结构永久自保区块。
9. **网络表现**：服务端和客户端均无残留结构碰撞/选择框；方块更新、光照、BE、客户端断线重连、跨维度重连不留下幽灵块。
10. **隔离**：未启用实验开关时无新增行为；Sable 结构不被此功能调度；所有测试使用副本，不在用户正在打开的世界注入故障。

实施时使用 JBR 25，按仓库规范运行 `.\gradlew.bat test -DisDev=true`、开发与发布 `build`，以及涉及的 native JVM/GameTest 和 `runClientDev` 实景验证。故障注入、网络与性能记录不能由普通单元测试替代。

## 11. 尚未验证与源码索引

**UNVERIFIED**：本方案全部新增调度、票据旁路传播、内容适配、事务协议、网络转换及故障恢复均未实现；本文没有宣称这些已经通过构建或游戏验收。数值预算为起始建议。需确认第三方模组票据观察的完整性、BE/计划 tick 迁移范围、实体保存屏障和 Windows 提交凭证在故障下的行为。

本轮仅生成和检查文档，按仓库规则不为文档变更运行 Gradle 或启动客户端。

主要源码：

- [SubLevelRestoreService](../src/main/java/org/academy/internal/common/sublevel/SubLevelRestoreService.java)、[SubLevelStaticizeJournal](../src/main/java/org/academy/internal/common/sublevel/SubLevelStaticizeJournal.java)、[TargetedChunkDurability](../src/main/java/org/academy/internal/common/sublevel/TargetedChunkDurability.java)
- [SubLevelRuntime](../src/main/java/org/academy/internal/common/sublevel/SubLevelRuntime.java)、[SubLevelServer](../src/main/java/org/academy/internal/common/sublevel/SubLevelServer.java)、[SubLevelManifest](../src/main/java/org/academy/internal/common/sublevel/SubLevelManifest.java)
- [PlotStorage](../src/main/java/org/academy/internal/common/sublevel/PlotStorage.java)、[TerrainColliderCache](../src/main/java/org/academy/internal/common/sublevel/TerrainColliderCache.java)、[NativeStructureTickets](../src/main/java/org/academy/internal/common/structure/space/NativeStructureTickets.java)
- [NativeStructureRuntime](../src/main/java/org/academy/internal/common/structure/NativeStructureRuntime.java)、[NativeStructureManifest](../src/main/java/org/academy/internal/common/structure/space/NativeStructureManifest.java)、[NativeStructurePhysics](../src/main/java/org/academy/internal/common/structure/physics/NativeStructurePhysics.java)
- [BlockStructureGridAlignment](../src/main/java/org/academy/api/common/structure/BlockStructureGridAlignment.java)、[BlockStructureManager](../src/main/java/org/academy/internal/common/structure/BlockStructureManager.java)、[StructureBackendRouter](../src/main/java/org/academy/internal/common/structure/StructureBackendRouter.java)
- [StructureTransactionJournal](../src/main/java/org/academy/internal/common/structure/transaction/StructureTransactionJournal.java)、[DurableFile](../src/main/java/org/academy/internal/common/structure/DurableFile.java)、[NativeStructureDropLedger](../src/main/java/org/academy/internal/common/structure/space/NativeStructureDropLedger.java)、[SubLevelMiningRecovery](../src/main/java/org/academy/internal/common/sublevel/SubLevelMiningRecovery.java)
- 本地依赖：`build/moddev/artifacts/minecraft-patched-26.2.0.70-sources.jar` 中的 `ChunkMap`、`ServerChunkCache`、`TicketStorage`、`DistanceManager`、`ChunkTracker`、`ChunkLevel`；以及 NeoForge `26.2.0.70-sources.jar` 中的 `ChunkEvent`。具体实现时必须继续对照项目锁定版本，不能以其他 Minecraft 版本的卸载顺序代替。
