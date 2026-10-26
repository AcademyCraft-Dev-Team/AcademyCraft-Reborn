# 持久发射账本与明确授权接续

基线：`0b38b01e 完善 Sable 删除诊断与写盘失败恢复`。本批接续 C 线公共破坏服务的 Launched 授权；卫星/轨道等具体复杂来源技能与 S4 接触汇聚尚未迁移。

## 已接线能力

- 新公开入口 `StructureLaunches.issue/registerPolicy/revoke`。真实服务端实体发起登记，成功返回前将授权写入 `data/academy/structure_launches/<UUID>.dat`。没有新增网络包或客户端自行签发入口。
- 保存发射 ID、宿主维度、真实来源实体 UUID 与类型、可选所有者、策略 ID/版本、创建和到期 tick，以及可选的物理结构创建身份。结构来源必须已加载且有相符的技能创建标记；来源卸载、换创建或被退休后不再授权。
- 内置 `academy:player_present_v1` 要求来源是真实在线玩家、所有者是该玩家，并继续检查玩家破坏开关、服务端总开关、维度策略及宿主保护。没有按 UUID 构造玩家或默认授予离线权限。
- 非玩家或离线来源必须由可信集成在每次服务器初始化时注册明确策略，实现签发、当前使用和逐格破坏三个检查。策略版本不符、未注册、异常或重入均拒绝。集成的逐格检查必须连接自己的领地/保护权限；普通玩家保护事件不能在没有真实玩家时伪造。
- 新可取消事件 `StructureLaunchBreakEvent` 提供真实宿主坐标、方块状态、可选结构格地址、账本归属与可空的真实实体。真实玩家仍走已有 `BreakBlockEvent`。新事件之后再次检查逐格策略、授权、来源身份与维度规则，事件中撤权/修改权限可阻止本格执行。
- 球形 `query/apply/status/cancel`、即时 `path/cut` 共用 `DamageAuthorization` 和既有形状、预算、保护、投射掉落服务。路径预算按服务器与归属主体共享，换 launch ID 不会重置该 tick 预算。
- 不可用或未经授权的调用返回 `DENIED`，调用者必须终止，不能降级为绕过结构保护的旧世界方块破坏路径。普通活动非玩家 `ActiveActor` 仍不直接授权，需先签发并使用 `Launched`。
- `LevelUtil` 原玩家方法保留，新增接收真实 `Entity` 的已授权内部公共入口。主格和无支撑邻格的掉落传递真实实体；离线来源为 null，不替换成 FakePlayer。仅真实玩家继续使用空间背包及原玩家专用自定义掉落回调。

## 一次受理与恢复约定

每个授权在整个生命周期中只受理一次破坏操作。球形查询在返回计划之前持久记录 effect ID；路径/切割在执行之前记录。后续球形分页仍属于同一内存操作，逐次复核授权。查询得到空集、保护拒绝或主动取消也不退回这个授权。路径模拟只读，不消耗授权、不发破坏/掉落回调。

重启后恢复未使用、未过期且当前策略仍允许的授权；已受理操作不会自动重放或续跑，避免重复掉落。撤销记录持久保存，成功回执只在写入完成后返回。写入失败后当前进程将该账本视为不可用，拒绝继续授权；失败的撤销没有成功回执，不承诺它跨重启已经生效。缺失/损坏/未知记录不会变成默认允许。

记录压缩镜像上限 16 KiB，总记录上限 65,536，授权最长 72,000 game tick；时钟早于创建或达到到期时间都拒绝。当前保留已受理/撤销记录，容量耗尽时拒绝签发；尚未加入过期日志回收或管理员清理命令。不得通过删除有效记录给旧操作“重新发一次”权限。

这是授权与防重放协议，不是方块内容、战利品回调与账本之间的跨文件原子事务。中断后可能保留未执行完的破坏，但不自动补发。S4 的首撞状态和接触事实消费仍需独立接线。

## 测试与复跑

`StructureLaunchLedgerTest` 验证持久受理/撤销、重复或伪造身份、到期边界、写入失败、损坏记录与来源维度。`StructureLaunchSmoke` 在 fresh 副本中创建真实物理结构和非玩家实体，验证球形/路径/刀面、真实掉落归属、未知/伪造/跨宿主授权、撤销前后、取消消耗、保护回调、来源消失与创建变化、模拟、玩家开关、回调中的逐格策略变化，以及策略异常、重入和受理过程中的权限撤回。策略回调异常产生预期 ERROR，需以最终报告和正常退出判断结果。

```powershell
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name launch-new -Fresh
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/structure_launch.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-launch-new --console=plain
```

混装使用 `runClientSable -PacademySableRuntime=suite -PacademyLaunchRun=clientSable`。中断恢复另建 fresh 目录，先以 `-PacademyLaunchMode=crash` 运行，预期 halt(86)；再以同目录 `-PacademyLaunchMode=verify` 重启，不重新复制。结果在运行目录的 `structure-launch-<mode>-result.json`。原有球形/路径/刀面技能和精密节点回归沿用各自专项 init 脚本。

最终结果见[结构化验证汇总](../validation/native_structure/structure_launch_validation_2026_10_26.json)。该文件同时记录 8 个生产源文件的 SHA，便于核对复跑版本。

| 检查 | 结果 | 日志 |
| --- | --- | --- |
| 独立后端授权矩阵 | 24/24，531 tick，正常退出 | `build/launch-authority-native-final.log` |
| 四模组混装授权矩阵 | 24/24，531 tick，正常退出 | `build/launch-authority-sable-final.log` |
| 持久受理/撤销后中断 | 1 组准备断言，25 tick，按预期 halt(86) | `build/launch-authority-crash-final.log` |
| 同存档重启 | 4/4，25 tick，正常退出；已受理、撤销、版本不符拒绝，未使用授权恢复 | `build/launch-authority-recover-final.log` |
| 原球形技能/精密节点回归 | 独立与混装各 22/22、606 tick，正常退出 | `build/launch-final-regression-sphere-{native,sable}.log` |
| 原路径技能/精密节点回归 | 独立与混装各 28/28、763 tick，正常退出 | `build/launch-final-regression-path-{native,sable}.log` |
| 原层流切割/精密节点回归 | 独立与混装各 28/28、756 tick，正常退出 | `build/launch-final-regression-cut-{native,sable}.log` |
| `test` / `sableCompatTest` / `editorTest` | 分别 3275 / 49 / 104，均 0 failure/error/skipped | `build/launch-final-dev-build.log` |
| 开发构建 | 通过，47 秒 | `build/launch-final-dev-build.log` |
| 发布构建 | 通过，43 秒 | `build/launch-final-release-build.log` |

任务包含 UP-TO-DATE / 缓存复用；专项 JVM 任务可能重复执行部分用例，不合计为独立测试总数。首轮 20/21 项授权矩阵和首轮旧技能回归属于阶段证据；补齐形状/液体写入前权限复核、回调中的逐格策略改变、不可变预算归属及 24 项矩阵后，已完整复跑上表最终版本。

最终开发/发布 JAR 均包含新公开 API、权限事件与账本实现，均不含新实机夹具、既有 Sable 强退夹具或外部 Sable 类。测试基线存档的 148 个文件数量与哈希未变，既有 Rust 文件 SHA 保持 `0B172501A5DA088A1CDA8AD13A41C88005D64B8D62D5347103DB566F84AC3C70`。

- `build/libs/academy-26.2.0-0.0.4-alpha-release.jar`：24,844,755 字节；SHA-256 `146C6B61FE035AD15C8DEAF6B6C1ABB247C8C2CEF385B7D2A952FEBA0E72ABC8`。
- `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar`：32,315,856 字节；SHA-256 `3CB47FD7ECB5856F7F07719158F3E4BC6863E272D76D14F0301ABEAC47345C1F`。

用户已确认本批 16 个相关文件的本地 Git 提交，提交信息为“接入持久发射账本与非玩家结构破坏授权”，以包含本条记录的提交为准。既有 Rust 改动及构建/运行产物不纳入。自动客户端矩阵不等于真人按键、多人服务器或任意领地模组验收。

## 下一阶段

1. 按具体技能逐一登记策略和发射时机，先清点卫星/轨道/持续或延迟投射来源；保留原技能的伤害、破坏开关、掉落、消耗及中止条件。一次授权对应一次可破坏效果，不让连续束流复用已消耗授权。
   接线时先取得持久签发回执，再将 launch 身份随实体/操作保存并发射；发射失败应撤销，签发失败不得继续破坏。重载缺失的 launch 关联不靠位置或所有者 UUID 猜补，重复命中也不重新签发。
2. 对具体非玩家调用者提供保护系统适配器；不得仅实现恒真策略后宣称已兼容所有领地模组。本批验证策略仅在 gametest 中注册。
3. 再接入 `StructureImpactFeed`、S4 首撞一次和有限碎裂。创建碎片后重新核实来源与预算，不能自动复制旧 launch 的未消耗权限。
4. 长期容量和过期记录回收、全部 I/O 故障组合、真人键盘/精密节点手感、原生接触压力红测及 JBR 崩溃根因仍独立待办。本批不更改原 Rust 文件、模组 pin、JBR 或 GC。

具体入口已经核对：`internal/common/world/entity/misaka/RelaySatelliteEntity.java` 的 `explodeOnImpact` 使用真实 `TNT` 爆炸；Ops 强制坠毁可归属于玩家，供电超时使用卫星实体。后续须保留爆炸选择/衰减、gamerule 和领地爆炸保护契约。`internal/server/misaka/MisakaOrbitalStrikeSupport.java` 的方块处理包含熔岩替换与 `breaker.gameMode.destroyBlock`，后续需保留标定者权限、工具/掉落及该分支顺序，不能直接用球形或普通路径替代。以上文件本批未改动。
