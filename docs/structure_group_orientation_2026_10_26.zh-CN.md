# 精密结构群组姿态控制

基线：`5d099411 新增精密结构有效性与材质来源筛选`。本批让矢量操作、电击使、空力使直接控制结构集合的姿态，承接选取点附近集合、最近项和材质/来源筛选。

## 使用方式

三系各新增以下动作，能力门槛分别沿动能投掷、磁场操作、高速喷流：

| 节点 | 必需输入 | 行为 |
| --- | --- | --- |
| 群组旋转结构 | 流程、结构集合、结构目标姿态 | 同时让每个结构绕自身质心转到同一目标方向 |
| 群组结构回正 | 流程、结构集合 | 每个结构分别按所选模式回正，等待整组完成 |
| 群组持续旋转 | 流程、结构集合、方向 | 各结构按同一轴、角速度、时长旋转，按配置制动或释放 |

建议接线：选取点附近结构集合 → 可选的有效性/方块/来源筛选 → 群组动作。连续多个动作可先写入“物理结构集合”变量，后续读取同一集合。群组动作自行获取与释放控制；不需要也不接受前置单体“获取控制”产生的占用。已有单体控制需先正常释放。

节点 ID 为 `academy:program/{accelerator,electromaster,aeromanip}/action/structure/{rotate_group,reset_group,spin_group}`，配置复用单体回正与持续旋转的 Codec、下拉框和默认值。输出为操作结果与流程；群组回执和租约不进入图保存数据。

这是各结构围绕自身质心的姿态动作，不是围绕共同中心公转，也不维持队形或悬停。群组动作不会抬升或平移结构；重力、碰撞和已有线速度继续生效。空力使要求每个成员具有本人所属且支撑格有效的结构喷口；节点不自动创建喷口。

## 控制契约

- 首次执行快照输入集合，按完整结构激活身份去重，保留顺序。原始输入最多 128 项，去重后最多 8 个；同一结构出现冲突的 runtimeId 拒绝。轮询不重新选取周围结构，也不改变已提交参数。
- 空集合立即完成，无控制获取、无 CP 占用。非空集合先检查全部成员，再获取全部租约，最后同一服务器 tick 内向所有成员提交姿态命令。这里的同时指同时启动并持续控制，不保证不同质量/形状的结构在相同 tick 完成。
- 已被任何会话控制的成员均拒绝，包括同一玩家的旧单体会话。不会通过替换同一玩家的旧租约来“成功”获取控制。若后续获取失败，释放此前已获取的本组租约。
- 每名玩家跨运行时最多同时持有 8 个群组成员，每台服务器最多 256 个群组成员。这是本批群组控制的共享上限，不是所有历史单体技能的全局上限。一个运行时同一时刻只保留一个未消费群组回执。
- 每个成员复用已有有限扭矩姿态求解器：角速度上限 2 rad/s、扭矩上限 1000、碰撞形状预算 16384。旋转默认 100 tick；持续旋转最长 1160 tick，另留 40 tick 制动窗口。服务器另设命令期限，避免 VM 不再轮询时永久保留控制。
- 每次检查当前维度、施术者存活/连接/技能可用状态、结构生命周期/runtimeId、内容版本、能力作用距离和确切租约。电击使原生结构沿既有磁性参与检查（含原有强制磁化）；Sable 群组需在完整且不超过 4096 格的内容摘要内找到磁性材料。内容无法完整读取时拒绝，不把未知内容当作可控金属。
- 每个成员基础 CP 占用为原来的控制 10 + 姿态动作 5；整组一次提交按成员数相加，再沿技能调节和既有 CP 占用系统计费。轮询不重复计费。预检或获取失败且未启动命令时不计费；已有命令受理或提交抛异常而效果不确定时，整组费用保留，随后停止整组。
- 一旦有成员失效、失去租约、内容变化或命令失败，停止本组其余命令，释放本组确切 token。已被其他会话接替的租约不会被旧组清掉。清理异常仍尝试释放其他成员；不回滚已经发生的位移/旋转。
- 全部成员完成、程序取消/失败、技能不可用、玩家离开和服务器停止时均有清理路径。正常结束、失败和取消都保留物理结构，不自动静态化；制动只按动作自身的停止模式执行。

公共扩展点为 `ProgramStructureGroupRuntime` 与 `ProgramStructureGroupControl`。后者只编排有界集合、回执与精确清理，不依赖具体服务器实体；运行时默认返回不可用，旧扩展无需立即实现。`ServerStructureGroupControl` 负责玩家、技能、资源与实际物理服务接入。

## 验证与复跑

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
./gradlew.bat test sableCompatTest editorTest -DisDev=true --console=plain
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name groups-native-new -Fresh
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/structure_groups.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-groups-native-new --console=plain
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name groups-suite-new -Fresh
./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite -PacademyStructureGroupRun=clientSable -I validation/native_structure/structure_groups.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-groups-suite-new --console=plain
./gradlew.bat build -DisDev=true --console=plain
./gradlew.bat build -DisDev=false --console=plain
```

每次采用新的隔离目录。中文截图需将该副本 `options.txt` 的 `lang` 改为 `zh_cn`。检查 `structure-group-result.json` 的总通过与三系结果，并检查客户端正常退出；不能仅看 Gradle 返回码。禁止对用户原存档启用自动夹具。

- 核心单测验证整组预检/获取顺序、快照去重与预算、部分失败、提交异常的计费标记、回执变化、已完成成员失效、清理异常以及继任控制权保护。
- 节点单测通过三系实际注册表检查九个执行器、只提交一次后轮询、输入变化不换组、非法回执、保存往返、能力门槛、空集合真实 VM 与共享程序清理。
- 真实客户端每系捕获两个铁块结构，经实际节点和调度器执行旋转 → 回正 → 持续旋转；逐体核对姿态、制动和连续同时运动，核对按人数计费且轮询不重复收费。
- 真实客户端同时覆盖过期激活引用、已有同玩家占用、CP 不足、跨运行时玩家预算、错误群组回执、控制权接替、程序取消、成员删除与剩余租约释放。
- 为隔离角运动，本测试仅额外使用线性重力补偿保持测试结构在观察范围内，不提供角辅助；不能由此宣称群组节点支持悬停。真实编辑器展示同一合法 12 节点图并截图，但夹具不发送保存/执行网络包。

最终机器报告为 [structure_group_validation_2026_10_26.json](../validation/native_structure/structure_group_validation_2026_10_26.json)，记录源文件、客户端报告、截图、日志与 JAR 的 SHA，以及本批文件清单。

| 检查 | 结果 | 日志 |
| --- | --- | --- |
| 主测试 / Sable 专项 / 编辑器 | 3307 / 49 / 104，0 failure/error/skipped | `build/structure-groups-tests-final.log` |
| 独立群组实机 | 三系通过，36 秒，正常退出 | `build/structure-groups-native-r1.log` |
| Academy + Sable + Create + 航空学群组实机 | 三系通过，38 秒，正常退出 | `build/structure-groups-suite-r1.log` |
| 独立原有单体控制与筛选回归 | 三系通过，64 秒，正常退出 | `build/structure-groups-regression-native-r1.log` |
| 混装原有单体控制与筛选回归 | 三系通过，73 秒，正常退出 | `build/structure-groups-regression-suite-r1.log` |
| 开发 / 发布构建 | 25 秒 / 39 秒，均通过 | `build/structure-groups-{dev,release}-build.log` |

两种群组实机每系完成顺序均为 `[8,9,10]`，都有 29 次双体同时旋转样本；每系三个节点总 CP 占用为 64.285675（该测试玩家的等级/计算效率下），并逐次核对单组成本与轮询零额外成本。专项 JVM 任务可能覆盖重叠用例，不将各任务数字相加宣称独立用例总数。

已目视检查两种环境的 `run/native-sublevel-direct-smoke-groups-{native,suite}-r1/screenshots/structure-groups-editor.png`：12 节点完整接线、三个新动作与中文配置可见，底部显示“程序校验通过”。长配置摘要仍沿现有编辑器宽度省略显示。

最初定向测试编译发现夹具使用了不存在的流程枚举名，已改为 `CONTINUE` 后通过定向与全量验证；失败日志保留，不计为通过证据。四轮客户端均使用新副本，原基线存档 148 个文件的数量和 SHA 未变。

| 产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` | 32370806 | `ECDDCAA48158AAF25B7CC376521D6A0F85EE900F080E4A4F1E26A0E59D7599AF` |
| `build/libs/academy-26.2.0-0.0.4-alpha-release.jar` | 24899705 | `1D9E5D756F3B01993FF3E0AB8AF02A21763B2D45F2EA8151C06A95B38C09F178` |

两种产物均含群组公共 API 和实际实现，不含本批新增实机夹具。既有 Rust 文件 SHA 保持 `0B172501A5DA088A1CDA8AD13A41C88005D64B8D62D5347103DB566F84AC3C70`，不纳入本批。用户已确认本批 35 个相关文件的本地 Git 提交，提交信息为“新增精密结构群组姿态控制与退出清理”，以包含本条记录的提交为准；构建产物和运行目录均不提交。

## 下一阶段接续

1. 人工用“附近集合 → 筛选 → 群组动作”检查目标选择、配置提示、自然坠落、碰撞中失败反馈。真实多人、八体异形接触压力、具体领地模组和编辑器网络往返仍需独立验收。本批最多两体同时运动的实机证据不能当作八体压力测试。
2. 多结构平移先重构 Sable 技能会话：`accelerator/movement/VectorStructureService.SESSIONS`、`electromaster/magnet/MagneticStructureService.PROGRAM_CONTROLS`、`aeromanip/movement/HighSpeedJetStructureService.SESSIONS` 仍以玩家为键，重复获取会替换前一个。新增按玩家+会话 token+结构身份的句柄，保留原按键技能默认单会话语义；补齐向量锚点、磁性参与及喷口推力分配后，再接群组平移节点。不能直接对现有单体获取动作循环调用来宣称同时移动。
3. 平移群组复用本批快照、确切 token 清理和失败语义，但需另外定义总力/每体力、距离/队形、总体 CP 与 MP 预算，验证原始控制手感、同玩家多个程序争用、失联与卸载清理。群组持续持有控制/显式群组释放也需另立运行时句柄，不能把本批 UUID 回执持久化。
4. 卫星/轨道持久授权、S4 首撞一次/有限碎裂、I/O 完整故障组合、长期账本回收及原生接触压力红测按既有专项接续。本批不修改 Sable fork/pin、DLL、JBR 或 GC；已有 Rust 诊断改动继续排除。
