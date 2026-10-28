# 精密结构群组平移

基线：`8b9cc8e1 新增精密结构群组姿态控制与退出清理`。本批在矢量操作、电击使、空力使新增“群组平移结构”，承接结构集合的选取、筛选和群组姿态动作。

## 节点与操作语义

- 节点 ID：`academy:program/{accelerator,electromaster,aeromanip}/action/structure/move_group`。必需输入为流程、结构集合、世界位移向量；配置为持续时间，默认 1 秒，可选 0.1–10 秒。输出为流程和操作结果。
- 从“选取点附近结构集合”接可选筛选，再接群组平移。用“构造向量”的 x/y/z 输入确定世界方向和格数，例如 `(0,0,3)` 为世界 Z 正方向前移 3 格；位移长度最多 64 格，也允许零位移作为有限时长的定点目标。
- 首次执行保存每个成员的初始质心，并分别加上输入位移得到目标，不把整组拉向同一点。轮询不重选目标或重读位移输入。最大位移仍须通过各系距离、世界高度、边界和已加载区块检查。
- 动作在指定时间窗口内持续施加有限力，并等待窗口结束后释放控制。**完成表示施力窗口结束，不保证到达目标、悬停、刚性队形或自动制动**。碰撞、质量、能力推力上限与重力仍决定实际运动；释放后保留惯性。
- 每组、每玩家跨运行时最多 8 个群组成员，全服务器最多 256 个；移动与群组姿态动作共用这一预算。已有占用（包括同一玩家的其他会话）会拒绝本组，不能通过新组替换已有控制。

## 三系动作与预算

| 能力 | 施力方式 | 保留的规则 |
| --- | --- | --- |
| 矢量操作 | 各体质心的有限力 PD 锚点，使用既有动能参数与重力补偿 | 捕获级别仍影响参数；直接设置世界位移目标，不传送结构 |
| 电击使 | 既有磁性参与比例与拉拽速度换算成有限力 | 原生临时磁化有效期、材料比例；Sable 完整 4096 格摘要，内容未知拒绝 |
| 空力使 | 本人喷口在当前姿态下计算的合成推力 | 所选有效喷口共享单体推力额度，实际作用点由喷口位置决定；不自动安装喷口 |

每体群组平移力上限固定为 `StructurePhysicsControl.MAX_FORCE / 8 = 1250`，另受原能力自身上限约束。因此同一玩家跨多个群组/VM 同时控制的 8 个成员，其群组平移总力上限为 10000。未占用的份额不动态转给其他成员；旧单体技能的力额度不是这个群组预算的一部分。喷口数量不会额外放大单体额度，每个成员最多接受 64 个喷口。

CP 基础占用仍为每体控制 10 + 平移 5，按成员数相加后经过现有程序倍率、技能调节及计算效率，首次提交一次计费，轮询不重复计费。未开始工作的预检/获取失败不收费；已经受理或效果不确定的部分工作按上一批规则保留费用并终止全组。空力使沿原精密移动的 CP 规则，本批不另设 MP 消耗，不改变原空力资源管理。

## 控制会话与清理

新 `ServerStructureGroupMovement` 按本组成员的结构引用、runtimeId、玩家和独立 controlId 保存运动会话；`ServerStructureGroupControl` 统一做整组预检、获取全部租约、同时启动、每 tick 一次施力、超时与清理。公用的 `ProgramStructureMoveRequest` 和 `ProgramStructureGroupRuntime.moveStructures` 可供其他执行器使用；未实现移动的旧运行时默认返回不可用。

本批根据现有代码调整了原计划的会话改造方式：保留 `VectorStructureService.SESSIONS`、`MagneticStructureService.PROGRAM_CONTROLS` 和 `HighSpeedJetStructureService.SESSIONS` 承担原单体技能的捕获、悬停、发射与按键操作；群组建立独立逐体会话，调用相同的 `KineticStructureControl`、`MagneticStructureControl`、`JetStructureControl` 和双后端 `StructureDirectControl`。不循环调用会替换旧成员的单体获取接口，也不把旧默认会话字典强行改为多人多体字典。

移动与旋转仍一次只占用一个群组回执，回执不保存到图或世界。任何成员失效、改维度、失去确切租约、内容版本变化、喷口丢失/变更所有者、技能不可用或目标不可用时，终止全组并释放其余成员。正常结束、取消、失败、断线及服务器停止共用已有清理入口。喷口的原激活状态只在仍属于本会话的结构激活身份和控制权范围内恢复，不能覆盖同玩家继任控制者，也不能恢复到已经重挂到其他结构或更换所有者的喷口上。

## 复跑入口

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
./gradlew.bat test sableCompatTest editorTest -DisDev=true --no-daemon --console=plain
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name group-move-native-new -Fresh
./gradlew.bat runClientDev -DisDev=true --no-daemon -I validation/native_structure/structure_group_movement.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-group-move-native-new --console=plain
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name group-move-suite-new -Fresh
./gradlew.bat runClientSable -DisDev=true --no-daemon -PacademySableRuntime=suite -PacademyStructureGroupRun=clientSable -I validation/native_structure/structure_group_movement.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-group-move-suite-new --console=plain
./gradlew.bat build -DisDev=true --no-daemon --console=plain
./gradlew.bat build -DisDev=false --no-daemon --console=plain
```

只运行新隔离副本；检查 `structure-group-move-result.json` 的总通过、三系结果、实际位移和正常退出，不能只看 Gradle 返回码。中文截图在副本 `options.txt` 设置 `lang:zh_cn`。群组姿态回归沿 `structure_groups.init.gradle`，原单体/筛选链回归沿 `structure_filters.init.gradle`；后者混装启动属性仍为 `academyStructureOrientationRun=clientSable`。

单测检查三系实际节点注册与只提交一次后轮询、位移和时间边界、零向量、溢出、配置往返及跨群组固定力份额。实机每系同时驱动两个结构，检查位移、按两体计费、轮询零额外成本、越界目标、取消、继任控制和喷口状态、成员删除。**平移夹具没有额外重力补偿、位置辅助或角辅助**；新动作实际执行三系节点和调度器，但不发送保存/执行网络包。

独立首轮与最终复跑、混装首轮均通过。最终每系一次 1 秒、目标前移 3 格的实际 Z 位移如下，表中两体近似相同的结果合并显示：

| 能力 | 独立后端 | Sable 混装 |
| --- | ---: | ---: |
| 矢量操作 | 约 2.68 格 | 约 2.94 格 |
| 电击使 | 约 2.48 格 | 约 2.53 格 |
| 空力使 | 约 4.79 格 | 约 1.79–1.83 格 |

喷流在独立夹具中越过了目标位置，符合本动作沿原喷口推力逻辑、没有到达制动的时间窗口语义；不能据此宣称精确到点或双后端相同轨迹。电击使在两种环境中也有自然下落。数据只描述这一双铁块夹具，其他质量、碰撞或配置需人工验收；若后续需求为精确到点，应另设计减速/到达状态，而不能扩大成功判定掩盖误差。

最终机器报告：[`structure_group_movement_validation_2026_10_28.json`](../validation/native_structure/structure_group_movement_validation_2026_10_28.json)。主测试 3310、Sable 兼容 49、编辑器 104 全部通过，失败、错误和跳过均为 0。最终双后端各完成三系群组平移、原群组姿态、原单体/筛选链，共 18 个实机能力场景，均通过并正常退出；首轮独立平移另有 3 个通过场景，不重复计入最终矩阵。

开发构建 `build -DisDev=true`（54 秒）与发布构建 `build -DisDev=false`（89 秒）成功。两包均包含新增移动 API、节点和实现，不包含测试夹具。日志、每轮隔离存档、结果文件与截图路径及 SHA-256 见机器报告。

| 产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` | 32389728 | `794A8D503653678305D79623132F0211F744D0E3DAC8FBED1ADBD81098CE26CA` |
| `build/libs/academy-26.2.0-0.0.4-alpha-release.jar` | 24918627 | `B22D2306414139E548A528DF0641D63CD7604C6E32B04456019524776517833B` |

只使用测试存档的隔离副本；源测试存档 148 个文件与七次准备快照哈希全部一致，没有使用用户世界。既有 `native/academy_physics/src/lib.rs` 改动哈希保持 `0B172501A5DA088A1CDA8AD13A41C88005D64B8D62D5347103DB566F84AC3C70`，不纳入本批。用户已确认本批 32 个相关文件的本地提交，提交信息为“新增精密结构群组平移与有限力预算”，以包含本条记录的提交为准；下述编辑器显示问题仍待处理。

## 界面发现与后续接续

新节点名称、时长配置和连线显示正常，图校验通过。独立首轮打开后 30 tick 的截图显示完整 10 节点，但有成就提示覆盖工具栏；延迟到 140 tick 的两种后端截图中，已有“浮点数 0”和“构造向量”节点的背景/文字出现漏绘，端口和连线仍在。保留全部截图，**不将延迟截图标记为完整界面验收通过**；尚未定位根因，不能直接归因于缓存或物理后端。本批没有修改编辑器生产渲染代码。

1. 优先复现并修复上述精密编辑器漏绘，检查普通图在长时间停留、缩放、滚动和窗口失焦后的重绘，再做真人编排、保存/执行网络往返与按键手感验收。
2. 验证八体异形接触、多人争用、群组与旧单体技能并用，以及具体领地模组。当前自动实机只证明两体运动；数量/力预算单测不能替代八体压力测试。
3. 若需长期保持、独立群组获取/释放、每体显式局部锚点或同时旋转与平移，再设计可跨动作持有的群组控制句柄；本批窗口回执不能持久化，也不支持从旧单体获取节点循环建立并行会话。
4. 卫星/轨道持久授权、S4 首撞一次/有限碎裂、I/O 全故障组合、长期账本回收与原生接触压力红测仍按原专项接续。既有 Rust 诊断改动保持原样，不纳入本批；不改 Sable fork/pin、JBR 或 GC。
