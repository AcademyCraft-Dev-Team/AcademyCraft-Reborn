# Sable 退休诊断与故障恢复接续

基线：`866404cd 完善 Sable 拆分来源继承与永久删除恢复`。本轮按上一批“后续顺序”的前置项补齐只读诊断、故障注入与有界重试；持久发射账本、非玩家授权和 S4 尚未开始接线。

## 管理员诊断

```text
/academy debug structure sable_provenance
/academy debug structure sable_provenance <结构 UUID>
```

沿用 `/academy debug` 的管理员权限，并在子命令上重复限定；控制台可用。指定 UUID 时查询命令执行所在维度，跨维度可用 `execute in <维度> run academy debug structure sable_provenance <UUID>`。

这两个入口只读取已装入内存的观察数据，不打开世界文件、不初始化账本、不强载区块、不触发重试，也不修复或删除任何记录。`backend_unavailable` 明确表示 Sable 缺失或版本不受支持；`not_initialized` 表示服务尚未观察，不等同于没有待办。

输出：

- `backend`：固定版本 gate 的状态。
- `state`：`ready`、`journal_unavailable`、`journal_unhealthy`、`creation_ledger_unobserved` 或 `creation_ledger_unavailable` 等；未观察过的来源账本不会被报告为健康。
- `loaded_pending`：已经成功读取的待办数量；损坏或不可读时不代表磁盘待办总数。
- 默认最多 10 条待办与最近 16 条结果；指定 UUID 精确查询该维度身份，并显示对应近期结果。
- `status / attempts / last_tick / next_tick`：观察到的失败阶段、尝试次数与下次重试时间。
- 最多 8 个问题文件名与原因，另外保留问题计数；不会因为诊断采样截断而删除文件。

观察历史与退避属于本次服务进程，重启后重新计时。真正的永久删除授权始终来自原持久日志，不来自诊断记录、UUID 文本或“当前没查到”。没有记录时命令会明确说明“缺席不是删除证据”。

## 故障分类与重试

`SableStructureTransactions` 内部退休操作现在返回具体结果，原布尔入口仍保持兼容。诊断命令不调用这些有副作用的方法。已有归块流程仍只把 `RETIRED` 作为成功。

| 状态 | 含义 |
| --- | --- |
| `QUEUED` | 尚未进行一次退休检查 |
| `EMPTY_SLOT_DURABLE_INDEX` | 槽已空，但磁盘 holding 索引仍占用该指针 |
| `EMPTY_SLOT_CACHED_INDEX` | 槽已空，但内存 holding 索引仍占用该指针 |
| `HOLDING_SOURCE_PRESENT` / `HOLDING_POINTER_CONFLICT` | holding 中仍有旧来源，或同 UUID 指针不符 |
| `LIVE_POINTER_CONFLICT` | 活体、保存指针或槽内 UUID 不一致 |
| `STORAGE_FAILURE` | 存储读写失败或镜像无法解码 |
| `CREATION_CONFLICT` | 当前来源元数据不匹配日志中的原创建 |
| `MARK_RETIREMENT_FAILED` | 后端退休已确认，来源账本尚未完成精确退休 |
| `JOURNAL_COMPLETION_FAILED` | 后端与来源账本已处理，最后删除意图文件失败；保留意图重试，不回滚已退休内容 |
| `UNSAVED_SOURCE_LIVE` | 日志称来源未保存，但出现无法解释的活体，继续保留 |
| `INTENT_WRITE_FAILED` | 删除前记录意图失败，仅记录近期错误，不伪造持久授权 |
| `RETIRED` | 本次确切来源的存储与元数据退休、日志完成均已确认 |

其他后端结果还包括宿主不可用、容器更新中、缺保存指针、活体未消失、外来槽归属和 plot 归属冲突。故障不赋予强删权限。

每 20 tick 最多检查 4 条记录，仍至多执行一次存储退休。相同原因连续失败的等待间隔为 20、40、80、160、320、600 tick，之后封顶 600；原因改变后恢复 20 tick 的短重试。每条记录独立计时，未到期的记录跳过，不阻塞另一条可完成的退休。日志删除失败时保留可重放记录，后续重试仍复核原创建与槽位归属。

## 文件头写入失败修复

故障注入确认固定版本 Sable 的 `SubLevelStorageFile.clear` 在写入文件头之前清空 `sectorSpans`、`usedIndices` 和 `usedSectors`。当写头抛出异常时，下一次读取直接看到缓存空槽，可能跳过真正的磁盘清理并错误退休来源；文件关闭时也可能依照已清空的占用位图截断内容。

新增 `MixinSableStorageClear`，沿用已有版本与 SHA-256 指纹 gate，仅在已审计的 `2.0.6-port.26.2.0.70` 上启用。清空失败时恢复原槽跨度、索引位与受影响的扇区位（最多 255 位），原异常继续抛出，让既有退休意图保留并重试。它不在异常分支写磁盘、不驱逐或关闭文件，也不绕过来源归属核对。未改动 Sable JAR、pin 或 fork 源码。

`retirement-header-repro` 在修复前真实复现了“尚未释放文件头锁，退休却已报告完成”。正式测试用第二个 Windows FileChannel 锁住 4096 字节文件头，确认数据区仍可读，再观察至少两次写入失败与退避；解锁后直接用独立文件通道读取磁盘槽跨度，必须为零才通过。这个修复保证同步清空异常不会留下假空缓存，不代表已实现断电或所有短写情形的原子存储协议。

## 故障注入范围

`SableRetirementFaultSmoke` 只在隔离副本中启用，先捕获真实技能结构，再针对其确切存储身份注入：

1. 空槽残留磁盘索引；同场另一个来源正常退休，验证无队头阻塞。
2. 空槽残留缓存索引。
3. holding 中同 UUID 指向另一槽位。
4. 已记录删除意图的活体与槽内外来 UUID 冲突；保留并隔离活体，恢复原镜像后继续删除。
5. 关闭真实 Sable 存储文件通道，触发 `ClosedChannelException`；移除测试缓存中的确切旧文件对象并重新打开后恢复。
6. 文件头被 Windows 范围锁拒绝写入，但来源数据区仍可读取；失败时保留缓存占用与退休意图，解锁后核对实际磁盘头。
7. 写入无有效 Sable 身份的 NBT 镜像；未知数据不被当成已删空槽。
8. 用 Windows `NOSHARE_DELETE` 打开确切意图文件，触发真实日志删除失败；此时来源标记已经退休，但意图仍保留，释放句柄后完成。
9. 旧来源消失后槽位保存外来 UUID；旧来源退休不得改写该镜像、索引或外来删除队列。

每个阻塞用例观察至少两次实际失败、检查退避，再由夹具恢复已知注入点，验证自动继续。夹具的定点修复只适用于这些自行制造的已知故障，不是产品管理员修复命令。初次运行 a1 因反射只读 Map 视图不支持夹具 `put` 而失败；修正夹具为针对已知运行时字段写入确切测试对象。正式桥接 API 没有增加任意 Map 写入能力。

另有两个安装/启动场景：无 Sable 时命令不创建 Sable 账本；启动前放入损坏意图文件时，诊断列出问题且原字节保持不变。JUnit 补充日志完成删除失败的重启重放、损坏文件诊断采样上限、退避与近期历史上限。

## 复跑与结果

使用仓库规定的 JBR 25；不改原 test/test2、模组 pin、JBR/GC、原生 DLL 或既有 Rust 改动。

```powershell
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name retirement-fault-new -Fresh
./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite `
  -I validation/native_structure/sable_retirement_fault.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-retirement-fault-new --console=plain
```

无 Sable：另建 fresh 副本，改用 `runClientDev -PacademyRetirementFaultRun=clientDev`。损坏日志场景：另建 fresh 副本，在该副本的 `saves/test/data/academy/sable_provenance_retirements/injected-corrupt.dat` 写入专用无效测试字节，再加 `-PacademyRetirementCorruptJournal=true` 运行混装入口。不得拿真实意图文件做这种测试。

结果 JSON 为运行目录下 `sable-retirement-fault-result.json`。故障案例会产生预期的错误堆栈，必须检查最终结果、具体案例数量与正常退出，不能只按日志是否含 ERROR 判断成败。旧生命周期回归继续使用 `sable_provenance.init.gradle`。

最终结果见[结构化验证汇总](../validation/native_structure/sable_retirement_fault_validation_2026_10_26.json)：

| 检查 | 结果 | 日志 |
| --- | --- | --- |
| 四模组混装故障与诊断 | 11/11，674 tick，正常退出；含实际文件头拒写及磁盘空槽核对 | `build/retirement-fault-final.log` |
| 无 Sable 启动与只读命令 | 3/3，1 tick，正常退出 | `build/retirement-native-final.log` |
| 损坏日志启动与证据保留 | 3/3，2 tick，正常退出 | `build/retirement-corrupt-final.log` |
| 原拆分/永久删除/归块生命周期 | 14/14，180 tick，正常退出 | `build/retirement-lifecycle-final.log` |
| 中断准备 | 6 项断言，14 tick，按预期 halt(86) | `build/retirement-crash-final.log` |
| 同存档重启恢复 | 2/2，20 tick，正常退出 | `build/retirement-recover-final.log` |
| `test` / `sableCompatTest` / `editorTest` | 分别 3267 / 49 / 104，均 0 failure/error/skipped | `build/retirement-final-dev-build.log` |
| 开发构建 | 通过，39 秒 | `build/retirement-final-dev-build.log` |
| 发布构建 | 通过，39 秒 | `build/retirement-final-release-build.log` |

专项 JVM 任务可能重复执行部分测试，以上数量不合计为独立用例数。同修订同配置的成功检查按仓库规范复用；构建包含 UP-TO-DATE / 缓存结果。测试基线存档 148 个文件哈希及数量未变，既有 Rust 文件 SHA 仍为 `0B172501A5DA088A1CDA8AD13A41C88005D64B8D62D5347103DB566F84AC3C70`。

两个 JAR 均已核对包含新命令、状态、退避类及注册的存储回滚 Mixin，不含故障/强退夹具或外部 Sable 类：

- `build/libs/academy-26.2.0-0.0.4-alpha-release.jar`：24,816,017 字节；SHA-256 `65EB4592FF6E2FBE4960A810286CFF4E04A55DC344ABF9BB75DB6DFD372CAC25`。
- `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar`：32,287,118 字节；SHA-256 `6E684CE47F7CA3AEBA6B58144A5AFC355D16395705123D63F162B48B6127B79D`。

修复前的文件头故障报告与预期失败原因也保存在结构化汇总中；早期成功矩阵不替代上述最终修订结果。用户已确认本批代码、测试、接续记录共 19 个相关文件的本地 Git 提交，提交信息为“完善 Sable 删除诊断与写盘失败恢复”，以包含本条记录的提交为准；既有 Rust 改动和构建/运行产物不纳入。

## 接续顺序与未完成边界

2026-10-26 后续实现：第 2 项公共 Launched 授权与持久发射账本已进入[发射授权接续](structure_launch_authority_2026_10_26.zh-CN.md)。具体技能迁移、S4 与其余边界仍按该记录继续，下列内容保留为本批交接时的计划。

1. 人工验证混装实际键盘/蓄力/行走和精密节点操作；本批自动夹具不是操控手感验收。
2. 接入服务端持久发射账本与非玩家明确授权，再迁移复杂来源破坏入口。离线 UUID 不能单独授予攻击权限。
3. S4 接触汇聚、首撞一次与有限碎裂继续等待上述授权与预算能力。
4. 大量结构的长期性能、真实物理断电、所有文件保存中断点与混合 I/O 失败组合仍未覆盖。Sable 父子内容保存不是本项目原子拆分事务；损坏/未知证据继续保守等待，暂不增加强制清理命令。
5. 原生密集接触红测与既有 JBR 崩溃根因独立核查。本批普通 build 不代表 Rust 压力测试已转绿。

下一阶段代码入口：`api/common/structure/upgrade/StructureDamage.java` 的 `Launched` 与 `StructureImpactFeed.java` 的 `LaunchAuthority/LaunchAccess` 已声明契约，但 `internal/common/structure/upgrade/damage/LiveStructureDamage.java` 的 `context/available/lookup` 当前仍只接受活动 `ServerPlayer`。先补服务端持久账本、创建/策略/到期/撤销核对，再贯穿 query/apply/path/cut/status/cancel；不要把传入的 launch UUID 或离线 owner UUID 直接当成授权。球形、路径和刀面已有保护及掉落入口应继续共用，不为非玩家伪造 Player。最小验收应覆盖重启、伪造/过期/撤销来源、跨宿主及保护回调取消，之后再迁移复杂来源技能与 S4。
