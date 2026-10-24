# 技能结构自动归块验收夹具

后续新增 `canopy`（本地/Sable 的倾斜铁条落到阶梯树叶后自动归块）、`admin`（实际批量指令，两维度、未加载位置、未标记、容器/保护拒绝、取消重试、Sable 对照）和 `admin_crash_targets_saved` / `admin_crash_commit_saved`（未标记管理员事务中断，随后同目录 `recover_verify`）。管理员模式主动调用指令，不属于自动归块测试。详细范围见 `docs/structure_restore_all_2026_10_24.zh-CN.md`。

`StructureRestorationSmoke` 使用真实捕获事务、真实物理解算和正式自动归块调度器。夹具只查询归块预演，不主动提交成功的归块请求。默认技能和精密操作节点的接入由原有技能、VM 夹具中的 `StructureProvenanceSmokeAssertions` 单独验证，不能把通用捕获夹具记作实际按键或 VM 测试。

## 隔离运行

只使用 `run/native-sublevel-direct-smoke*` 下的副本。不要直接使用 `run/saves/test`、`test2` 或服务端正式存档；`prepare` 与 `verify` 两次启动必须使用同一个副本目录和同一个玩家身份，不要在两次运行之间重新复制存档。

由主任务串行运行 Gradle 与客户端，复用已配置的 JBR 25、原生库参数和副本准备流程：

```powershell
.\gradlew.bat runClientDev -DisDev=true -I validation/native_structure/structure_restoration.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-restoration-native -PacademyStructureRestorationMode=functional
.\gradlew.bat runClientSable -DisDev=true -I validation/native_structure/structure_restoration.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-restoration-sable -PacademyStructureRestorationRun=clientSable -PacademyStructureRestorationMode=functional
.\gradlew.bat runClientSable -DisDev=true -I validation/native_structure/structure_restoration.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-restoration-sable-joint -PacademyStructureRestorationRun=clientSable -PacademyStructureRestorationMode=joint
```

持久化测试对两种后端分别执行 `-PacademyStructureRestorationMode=prepare`，客户端正常退出后再对同一副本执行 `-PacademyStructureRestorationMode=verify`。默认世界名为 `test`；如副本使用其他名字，追加 `-PacademySubLevelSmokeWorld=<副本世界名>`。

批量回填另对两种后端分别执行 `-PacademyStructureRestorationMode=multiblock`，使用各自独立副本目录。该模式只运行一个四格倾斜结构案例；结果 `structure-restoration-multiblock-result.json` 必须通过且 `placement_count=4`。

故障恢复必须另外使用独立副本。执行 `crash_targets_saved`（预期游戏进程退出码 81）或 `crash_commit_saved`（预期退出码 82），再对完全相同的副本执行 `recover_verify`。Gradle 因预期游戏进程中断通常返回失败，不能把这一轮标为构建通过或普通客户端崩溃。必须读取已强制落盘的 `structure-restoration-crash.json`，确认实际命中的检查点与退出码，再核对 `structure-restoration-recover_verify-result.json`。

中断代码只存在于 gametest 源集，生产事务仅提供默认无监听器的检查点观察接口。夹具同时检查显式目录前缀和当前世界确实位于该目录的 `saves` 下。`TARGET_SAVED` 位于目标强制保存成功后、COMMIT 决策前；`COMMIT_SAVED` 位于 COMMIT 日志保存后、源清理前。两个断点均使用 `Runtime.halt`，不执行普通关闭或测试清理。

## 断言与结果

| 场景 | 实际验证 |
| --- | --- |
| `unmarked_retained` | 通用捕获不产生技能标记；自然落地后至少 180 tick 仍保持物理结构 |
| `lease_then_rest` | 已标记结构持有控制租约至少 180 tick 仍保留；释放后由正式调度器归块 |
| `occupied_target_then_rest` | 用无碰撞但非空气的 `LIGHT` 占据预演目标格；至少 180 tick 不覆盖该格、不改变源内容，清障后自动归块 |
| `tilted_yaw_then_rest` | 仅布景时设置 37° yaw、20° roll 并抬高 2 格；随后自然落地静止，预演回正为竖直、90°整数倍朝向并自动归块 |
| `multiblock` | 捕获 `seed`、东侧、南侧、上侧四个铁块组成的非对称 L 形结构，采用相同倾斜及自然落地流程；严格验证四个互不重复目标、逐格内容和客户端同步、源体清除、容量释放，并再次捕获全部四格确认容量复用 |
| `offline_owner_then_rest` | 由受信捕获 API 指定不在 PlayerList 的随机 owner UUID；正式调度完成归块，保护事件携带 `offlineOwner=true` 和原归属 UUID，全程不创建 FakePlayer |
| `protection_cancel_then_retry` | 仅对当前测试结构取消 `StructureRestorationEvent`；至少 180 tick 且实际收到取消事件后检查源体、标记和目标空气均保留，允许事件后由正式调度重试成功 |
| `joint`（仅 Sable） | 对自然静止的已标记铁块添加真实 world fixed constraint，释放 ACR 租约后至少 180 tick 保留，正式调度与预演都拒绝归块；显式移除 solver 句柄后预演恢复并由正式调度归块。旁边未标记金块的 UUID、runtime、内容和未标记状态全程保持 |
| `prepare` / `verify` | 保存标记结构及创建事务 ID；正常重启后先 teleport 到原场地，最多等待 600 tick 异步激活和 READY，再验证持久归属、创建身份并取得租约；等待阶段不发起新捕获，释放租约后自动归块 |
| `crash_targets_saved` / `recover_verify` | 真正中断进程后重启，正式恢复回滚目标，源结构及标记恢复，源拥有可观察物理体，本地源碰撞恢复；连续 40 tick 保持唯一内容 |
| `crash_commit_saved` / `recover_verify` | 真正中断进程后重启，正式恢复完成清源，目标世界方块唯一保留、源标记退役；从首个服务端 Pre tick 起观察已提交源从未公开可选或参与本地求解/碰撞 |

每次成功归块还检查：目标方块内容、元数据退役、后端实体和 solver 观察消失、容量减少、本地服务端与客户端碰撞索引移除、客户端真实方块同步；功能测试再捕获还原出的方块，确认释放的容量可继续使用。Sable 客户端以容器中对应结构消失验证清理。

结果写入副本根目录的 `structure-restoration-<模式>-result.json`。必须读取 JSON 中 `passed`、`cases` 和 `failure`，不能仅依据客户端退出码。日志前缀为 `STRUCTURE_RESTORATION_`；`prepare` 的成功仅表示保留与保存成功，不能作为重启归块通过的依据。

`recover_verify` 会只针对中断结构取消新的自动归块请求，防止“先回滚又重新自动归块”掩盖实际恢复方向。恢复完成后保留副本供核查，不执行功能夹具的方块清理。`quarantined_raw_source_observations` 记录原始源仍在容器但公开访问已被隔离的观察次数；如果为 0，报告不能声称看到了这个中间窗口。

## 验证边界

- 默认三系技能标记：运行原有 `sublevel_default_skills_focused.init.gradle`，核对三个 `STRUCTURE_SKILL_PROVENANCE_VERIFIED`。
- 两后端默认服务入口：使用 `structure_default_provenance.init.gradle`，本地运行 `runClientDev`；Sable 运行 `runClientSable -PacademyStructureProvenanceRun=clientSable`，均指定独立的 `academySubLevelSmokeDirectory`。结果文件为 `structure-default-provenance-result.json`，必须包含三系实际默认服务捕获来源及第四项 `existing_unmarked_after_control_rotation`。该夹具使用默认服务方法，明确不模拟键盘或网络包；普通未标记结构经 ACR 租约和真实 `rotateTo` 后必须仍未标记。
- 两后端三系节点标记：运行原有 `structure_program_orientation.init.gradle`，每个类别输出包含 `skill_provenance_verified=true`。
- 本夹具检查碰撞索引和网络状态清理，不替代真人行走、跨客户端碰撞体验或完整多人网络验收。
- 离线场景使用可信测试 API 写入离线归属，验证正式调度的离线策略；并未模拟真实玩家登录后断网。要求副本当前服务器配置允许离线 owner 归块，夹具不会临时改写配置。
- 运动中、terrain pause、未知策略和批次预算应由调度资格测试覆盖；该夹具不伪造静止速度来替代真实落地。
- 区块卸载、断电中断、具体第三方保护插件、方块实体或液体内容等边界应按生产实现的支持范围另行验收；本夹具只验证公开保护事件的取消契约。

本文件描述可执行夹具和完成判据；实际通过记录由本轮运行产物提供。
