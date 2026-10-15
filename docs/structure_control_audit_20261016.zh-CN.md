# 后续迁移核查与结构操作回归（2026-10-16）

## 范围与基线

用户要求核查接续开发的新内容，测试并完善技能和精密节点，使物理结构保留原有操作逻辑。基线 `ac3ea5ea`，包含 P19–P33、结构压力测试和文件替换重试。工作区原有 `docs/vfx/darkmatter_reconstruction_v3/runtime/multiplayer/` 未跟踪记录不属于本批。外部 `create-fork` 未修改。

本轮覆盖动能投掷/搬运、磁场操作、高速喷流和三类结构程序的提取、获取控制、移动、发射、释放。测试使用生产编译器、VM、服务端运行时、真实 ServerPlayer 和物理后端。嵌入式连接不等于真实键盘操作、客户端配置确认或 Sable UDP 登录测试。

## 已发现并修复

1. **捕获尚未完成就进入下游节点**：原桥接把 `PENDING` 当普通结果放行，原生后端跨 tick 捕获尚无结构输出，随后控制节点报缺失输入。新增 `ProgramNodeStep.RETRY`，VM 保持当前节点，下一 tick 重试；三个运行时保存同一个捕获引用，等待事务和物理就绪，拒绝替换会话或超时。不会重做捕获，也不会在等待中输出半成品引用。空力程序闲置会话可以一直处于 `AWAITING_PHYSICS`，因此等待实际 motion 就绪，不能只按阶段名判断。
2. **Sable 移动节点全部返回 unavailable**：独立后端的移动入口已存在，但 Sable 路径未接。新增 `StructureDirectControl.submit` 与 Sable 物理步有限力消费；矢量使用公共 PD 参数，磁力使用磁性参与率和公共拉力计算，空力使用现有喷口/目标推力计算。每次提交最多消费 50 ms 冲量预算，子步数不放大预算，物理步重新校验实体、宿主、运行期身份和精确控制令牌。
3. **Sable 喷流提取后控制节点误要求 native region**：真实程序图复现 `rejected:region_unavailable`，现在捕获得到的 Sable 会话也正确取得直接控制租约。
4. **Sable 喷流技能只捕获、不给推力**：移除捕获成功后的 `sable_launch_unavailable` 分支，按原技能消耗 CP/空气，绑定喷口、取得控制并持续提交有限推力。既有 Sable 程序发射的直接速度标定保持原行为。
5. **释放后重新激活喷口只有特效**：新增 `MountedJetStructureControls`。已有结构上的活跃喷口按结构引用分组，复用同一个整结构推力预算；不同保留结构各自持有控制槽。每 tick 重新解析本地支撑格和当前姿态，不重新捕获方块，不覆盖已有技能/程序控制，不抢占外部控制。原 `activateOwnedNozzles` 入口再次激活即可推动原结构；关闭喷口、支撑失效、离线或停服释放控制。原生驱动复用技能原有区域跟随逻辑，避免再次激活后飞出初始区域。
6. **旧令牌可能抹掉继任控制**：`StructureDirectControl.validate` 只清理自己持有的无效租约。Sable 牵引与物理法杖占用判断也识别直接控制租约，探针使用 traction-only 判断避免递归或把自己的租约当作冲突。
7. **磁力抓取失败删除已捕获方块**：控制取得失败只结束会话，保留捕获后唯一内容所有者，移除 discard 分支。
8. **喷口支撑失效误绑定地形**：结构喷口校验真实本地单元；格子移除或结构卸载立即停止喷流，保留原引用，宽限期内不推实体，不回退成最后世界坐标上的普通方块喷口。`isAttachedTo(BlockPos, face)` 排除结构挂载。
9. **CP 不足但动作已生效**：`StructureProgramActions` 和 `tryTimedOccupationIf` 复用现有 CP 管理器的动作门控，先检查费用/状态/叠层限制，再执行服务入口，仅对接受的动作计费。捕获开始时计费一次，等待不重计；强磁化费用合并进本次捕获费用。服务明确拒绝时不计费。异步事务接受后若后续捕获失败，目前没有跨 tick 退款机制。
10. **原生捕获探测误报 LOADING 访问错误**：`NativeCaptureTarget.readCell` 在目标尚未 ready 时返回空结果，避免合法的初始分配探测访问未激活句柄。

## 自动回归覆盖

`StructureProgramWorldSmoke` 构建三类生产程序图：提取 → 获取控制 → 移动到源格上方 → 等待 12 tick → 释放。断言：

- CP 为零时程序在提取节点失败，源铁块不变、没有捕获会话；恢复资源后使用同一运行时可正常执行。
- 等待期间只有同一个结构引用，结构实际接近目标，不能用自然下落的位移冒充移动成功。
- 释放后源格仍为空，唯一结构仍 ACTIVE；不恢复或删除内容。
- 空力额外从原 `activateOwnedNozzles` 入口再次激活喷口，确认同一个结构再次向上加速、没有新捕获会话。
- 原生后端实际移除喷口所在本地格；Sable 实际移除结构；确认喷口停用且不改绑地形。

另有独立的 Sable 技能入口捕获 → 持续向上推力 → 停止保留结构，既有重型结构发射速度、三类程序发射、控制排他、NPC 牵引、终止回调、继任控制和原生技能/承载矩阵仍运行。

## 当前验证记录

- `build/control-audit-tests-final.log`：普通 JUnit 2655、Sable 专用 JVM 测试 16，均通过。开发/发布 build 均通过，editorTest 104 通过，合计 2775 个 JUnit 测试，失败/错误/跳过均为 0；兼容打包检查也通过。日志为 `build/control-audit-build-dev.log`、`build/control-audit-build-release.log`。
- 原生服务端 `run/native-structure-physics-smoke-server-audit-r2/`：通过，正常退出；三类真实图均跨 tick 等待并接近目标，CP 和支撑失效断言通过。该轮早于喷口再次激活驱动，后者需看后续客户端/服务端结果。
- Sable 服务端 `run/sable-compat/control-audit-r6/server/`：通过，正常退出；包含三类真实图、喷口再次激活、CP、支撑卸载和实际技能推力。更早的 r5 也通过基础流程。
- 原生客户端 `run/native-structure-physics-smoke-client-audit-r4/`：整轮通过，正常退出。包含承载、三类图、喷口再次激活与实际窗口 G/滚轮/H 输入；输入由外部 Win32 驱动发送，fixture 只观察窗口回调，不能写成人工手感验收。`build/control-audit-native-client-r4-input.log` 记录输入阶段，结果文件记录各项断言，screenshots 保存物理结构前/落地/控制阶段并已查看。
- 四模组混装客户端 `run/sable-compat/control-audit-suite-client-r1/client/`：整轮通过，119 tick，25 个移动姿态样本；真实图和喷口再激活通过，实际喷流技能上升速度约 87.873 blocks/s。截图 `screenshots/academy-sable-structure-70528.png` 已查看。本轮为服务器 fixture 操作，`actual_keyboard_actions=false`。
- 最终原生服务端 `run/native-structure-physics-smoke-server-audit-r3/`：988 tick，通过且正常退出；包含更长的再次激活窗口和区域跟随的最终代码。独立客户端 r4 早于这最后一处共用区域跟随提取；后续原生服务端和 Suite 客户端验证了最终代码，客户端输入和渲染未随该提取改变。

最终产物位于 `build/libs/`，未复制到用户整合包：

| 产物 | SHA-256 |
| --- | --- |
| `academy-26.2.0-0.0.4-alpha-dev.jar`（29,082,973 字节） | `EE63DEE225159576188AF9A8E2612EC245B15EC75AC8E8BE989FDF26F1974169` |
| `academy-26.2.0-0.0.4-alpha-release.jar`（21,611,872 字节） | `94ABEF83B3C52D17EB49CEEFC5E9A9DF39B771D30E8937EBC85BE3FAF0127103` |

保留失败证据：`control-audit-sable-r1` 暴露空力控制误要求 native region；r3 是新测试对 OPEN transaction 错调 release（已改 abort）；r4 是测试误用 Sable 只读 bridge 的 CAS（改为后端真实删除）；r2/r5 为中间通过版本。原生客户端 `audit-r1` 准备存档时只复制了 level.dat，遗漏新版 `data/minecraft/world_gen_settings.dat`，导致 `Overworld settings missing` 后探针超时；r2 已补齐元数据，但启动提示占用了过多总时限，在后段超时；r3 暴露旧人工牵引断言要求已到悬停点的结构仍移动 0.25 格，现改为持续保持时检查资源、滚轮改变目标后检查真实位移，r4 完整通过。没有删除失败证据或把这些失败运行计为通过。

## 复现要点

统一使用 JBR 25 和仓库 Gradle wrapper；先 `. ./build/native-physics-p16-env.ps1`。该文件只属于本机 build 产物，提供 `$physicsNativeArgs` 的绝对 native 库/manifest 参数。

- 普通/专用测试：`./gradlew.bat test sableCompatTest -DisDev=true`。
- 原生：`runServerCompat` 或 `runClientDev -I validation/native_structure/physics_world.init.gradle`，开启 `academyStructurePhysicsSmoke=server/client` 和 `academyStructurePhysicsGraspSmoke`、`academyStructurePhysicsKineticThrowSmoke`、`academyStructurePhysicsHighSpeedJetSmoke`、`academyStructurePhysicsMagnetManipulationSmoke=true`，传 `$physicsNativeArgs` 与全新 `academyStructurePhysicsSmokeDirectory`。
- Sable：`runServerSable` / `runClientSable -PacademySableRuntime=sable/suite -PacademySableSmoke=true -PacademySableCapture=true -PacademySableRunDirectory=run/sable-compat/<fresh>`。
- 专服只绑定 127.0.0.1；沿用测试 EULA，原生 world 名 `native-structure-physics-smoke`，Sable world 名 `academy-sable-smoke`。客户端测试 world 名分别为 `Native Structure Physics Smoke` 和 `Academy Sable Smoke`。
- 新版世界需要 level.dat 和 `data/minecraft/` 元数据；不要复制旧 Academy 结构所有权记录或 Sable plot 到新 fixture。保留失败目录，另建 rN，不清理失败证据重跑。

## 尚未完成及下一步

1. 三类 rotate 仍明确返回 `finite_torque_control_unavailable`。需要公共有限力矩/姿态控制及真实旋转回归，不能用 setPose/瞬移伪造旋转。
2. 安全静态化仍只有预检/拒绝路径；成功提交、全姿态网格对齐、内容事务与喷口迁回宿主的闭环仍未实现。退出控制保持物理结构的约定不变。
3. 锚点/姿态值的生产节点、对已有移动结构新放置/移除喷口的完整客户端瞄准链仍需专项核查；本批只保证原结构已有喷口的再次激活与失效处理。
4. 本轮窗口输入只覆盖原生气流牵引的 G/滚轮/H 路径，没有通过窗口输入完成全部技能组合；多人真实 UDP/高延迟、站立行走手感仍待人工验收。自动承载与混装同步 fixture 不能代替这些项目。
5. `DurableFile` 基线的 delete-then-move 降级仍有目标文件缺失窗口，孤儿 tmp 不自动恢复；虽然当前单测通过，这不构成断电安全证明。建议独立修复为保持旧提交记录或可恢复日志的方案，不能把该已有风险写成已解决。
6. 自动喷口驱动目前在整结构质心施加汇总力，延续旧实体平移推动行为；喷口分布产生的完整力偶和精确旋转应随有限力矩接口一并验证。

本批 34 个相关文件（代码、测试、验证入口与文档）已完成上述验证，没有修改用户实际存档。用户已于 2026-10-16 明确授权本批本地提交，提交信息为 `修复物理结构技能控制与程序续跑`；本记录随该批一并提交，不包含既有 VFX 未跟踪文件。
