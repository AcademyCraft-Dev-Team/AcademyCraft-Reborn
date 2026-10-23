# 可替换物理骨架 FILL-SPEC

日期：2026-10-23。来源：[主方案](structure_skill_physics_upgrade_plan_2026_10_23.zh-CN.md)、[MPS 评估](mps_physics_evaluation_2026_10_23.zh-CN.md)。

**状态：B 线本地/Sable 真实角控制及三系 rotate/reset_orientation/spin 已接线，ORIENTATION_ENABLED=true（EXPERIMENTAL）；其余 DRAFT_GAMEPLAY_ENABLED=false。** `StructureOrientations.service()` 与 `StructureUpgradeBootstrap.LIVE.orientation()` 使用实际子步服务；DRAFT 中的禁用占位实现继续返回 UNSUPPORTED。当前证据、限制和剩余验收见 [B 线接线记录](structure_upgrade_line_b_live_2026_10_23.zh-CN.md)，此前独立算法阶段见 [B 线第一阶段](structure_upgrade_line_b_2026_10_23.zh-CN.md)。

## 1. 接手与替换方式

公共玩法契约位于 `src/main/java/org/academy/api/common/structure/upgrade/`，公共引擎契约位于 `src/main/java/org/academy/api/common/physics/`。实现分别位于 `internal/common/structure/upgrade/` 与 `internal/common/physics/`。

`StructureUpgradeServices` 通过构造器注入各服务；实现者可以单独替换某个接口并声明真实能力。`StructureUpgradeBootstrap.DRAFT` 保留禁用服务包；`LIVE` 仅替换 ORIENTATION。`LiveStructureOrientation` 管理有界回执与生命周期，两个 adapter 在各自真实物理子步调用。三系 runtime/executor 共用该服务；未注册自动归块或新 DLL 加载。

`PhysicsProvider` 与玩法后端分层：MPS provider 是 native world 的候选；Sable 保持自己的内容和求解权威，走独立结构适配器，不能拿 MPS 句柄替换 Sable 句柄。默认 provider 目录必须显式区分已有引擎描述与尚未接线的新 adapter，不能误称 openWorld 已可用。

当前 native ABI、DLL、SubLevel 清单、网络协议和 live router 不由骨架切换。业务实现按阶段加入自己的集成测试，然后用窄入口接线；不要只把 GAMEPLAY_ENABLED 改 true。

## 2. 标记位置 × 方法契约 × 完成判据

业务方法使用 `// [TODO: Agent B][UPG-...]`。实现后删除对应 TODO，并在本表写入验证证据；勿修改其他模块的标记。

| 标记 | 接手文件 / 接口 | 必须保持的契约 | 完成判据 |
| --- | --- | --- | --- |
| UPG-CTRL-01..04、10/11 | ScaffoldStructureControls / StructureExtraction；独立 ExtractionPlanner 已实现 | 源姿态/内容/覆盖绑定；新捕获初始化与已有体控制分开；独占租约；真实子步提取；到位才开始 H 200 tick | 仍待来源绑定、planner 接线与双后端复杂地形/3/5/7/200 tick 验证 |
| UPG-CTRL-05..09 | LiveStructureOrientation / StructureOrientation；SubLevelOrientationAccess、SableOrientationAccess | 实际子步限速限力矩、连续角扫掠、COM 不传送；精确租约/版本/操作身份、替换与制动；三系异步节点 | 已接线，双后端实机与剩余矩阵见 B 线接线记录；DRAFT 角方法是刻意禁用的占位，不再是待填业务入口 |
| UPG-LIFE-* | ScaffoldStructureLifecycle / StructureProvenance、StructureGridRestoration | 来源在捕获提交内持久化；离线身份不是无限授权；未知策略保留；sleep 不等于 terrain-paused；完整目标镜像与保存屏障，唯一所有权 | 未标记不处理；重启/碎片继承；有效控制不归块；冲突延期；各提交断点无丢块/复制/幽灵碰撞；Sable 保存契约完整 |
| UPG-DAMAGE-01 | ScaffoldStructureCombat.query / StructureDamage | 宿主和局部格的真实内容选择体，含无碰撞植物；有界查询；server-owned 计划 | 静止/旋转/高速铁块可查询；空洞不命中；权限与缺区块失败可解释 |
| UPG-DAMAGE-02 | ScaffoldStructureCombat.apply | effect+稳定格去重；版本只作复核；全局 BlockDropsEvent 与已有作用域；内容立即失效、昂贵重建合并 | 普通/精密冲击波、取消/替换掉落/经验、最后一格、事件重入与移动命中通过 |
| UPG-DAMAGE-03/04 | ScaffoldStructureCombat.status/cancel | Authority 区分活动主体与持久发射授权，均不要求已有结构；异步查询返回已认证计划；取消只停止剩余格 | 查询、应用、取消和旧操作重试可追踪；不会回滚已产生的掉落 |
| UPG-IMPACT-01 | ScaffoldStructureCombat.drain / StructureImpactFeed | 真实接触按 step/substep 聚合；有限队列；溢出令首撞 UNKNOWN；不从求解回调写世界 | 首撞一次；多部件/多子步不重复；溢出/断线/重启不重发奖励；Sable 内置伤害归属明确 |
| UPG-FRACTURE-01 | ScaffoldStructureCombat.plan | 局部能量/材质/连通性；完整源内容覆盖；未知 BE 保留；子格无重复 | 稀疏/空心/大结构规划有界；子碎片不继承奖励；完整内容账本可核对 |
| UPG-FRACTURE-02 | ScaffoldStructureCombat.reserve | 创建前整组预留 body/shape/terrain/plot/network；失败零副作用 | 容量不足保留原物；预留取消/过期无泄漏 |
| UPG-FRACTURE-03 | ScaffoldStructureCombat.commit | journal → 目标保存 → 所有权提交 → 发布 → 清源；搬运不掉落，真实破坏才进入掉落管线 | 各断点可恢复；碎片有限、质量与初速合理；落地走相同安全归块服务 |
| UPG-FRACTURE-04/05 | ScaffoldStructureCombat.status/abort | 持久 LaunchAuthority 支持玩家离线和源结构退休后的查询；提交后中止只能继续清理 | 完成阶段必须有拥有者回执；中止不复活父结构或删除已提交子结构 |
| UPG-ENGINE-* | PhysicsProvider 与禁用 provider adapters | 独立 world/epoch/object 身份；能力组合；有限预算；批处理原子失败；稳定 ABI；MPS 不热替换当前 DLL | 先通过现有原生刚体矩阵，再做隔离游戏对照；流体/软体耦合与守恒逐项验证 |

资源模板：`src/main/resources/academy/structure_upgrade.template.json`。它不是运行配置，75/90、静止周期及碎片数量都是待标定值。角节点的运行能力由 LIVE 服务决定，模板仍禁用且 UNVERIFIED，不能据此启用其他玩法。

标记明细：`UPG-CTRL-01..09` 为已有结构提取与角控制，`UPG-CTRL-10/11` 为捕获前规划与捕获日志绑定；`UPG-ENGINE-01/02` 为候选 MPS 与现有 Rapier 的 adapter，`03..07` 为配置、步进、批命令、快照、接触，`08..11` 为创建、移除、真实形状查询及地形覆盖发布。`UPG-LIFE-*` 共 9 个入口：QUERY/PREPARE/PREVIEW/REQUEST/STATUS/CANCEL/HOLD/HOLD-RENEW/HOLD-RELEASE。可用 `rg -n '\[TODO: Agent B\]\[UPG-' src/main/java` 定位每一个方法。

捕获前只需宿主和执行主体，尚无结构句柄；绑定阶段必须解析服务端捕获日志并复核相同源摘要、版本、主体和租约。碰撞流使用事件游标支持同一步分页，并显式记录时序精度；离线发射授权必须由服务端持久账本验证，不能仅凭 UUID 取得权限。Provider 的新对象默认等待覆盖发布；缺失 section 与已验证的空 section 不等价。

## 3. 可并行分工与依赖

| 开发线 | 可独立推进 | 接线依赖 |
| --- | --- | --- |
| A：S0 原生基线 | 原生楔紧诊断、真实接触报告、Sable 同夹具、受控 N 扫描 | 先定位已知红测，不静默 ignore 或放宽断言 |
| B：S1 控制 | 提取 planner、角控制、节点 schema/codec 兼容 | 来源基准字段、真实碰撞扫掠、两个后端 adapter |
| C：S2 破坏 | 区域查询、作用域抽取、稳定格去重 | 保持现有挖掘/掉落行为；不能自行创建碎片 |
| D：S3 生命周期 | 来源记录、只读恢复规划、候选索引 | 自动写世界前必须完成事务与持久化门槛 |
| E：S4 战斗 | 只读首撞汇聚、碎裂 planner | 接触流、内容 mutation、资源预留、来源/归块事务 |
| F：S5/S6 MPS | provider adapter、批量 ABI、流体/软体探针和耦合修复 | 固定源码/许可/构建来源；原生回归后才能游戏接线 |

各线不要共同改 router、主注册、包协议、manifest 或 native ABI；由集成人统一处理这些文件。一次提交围绕一个验证闭环，不把所有骨架填充混成不可审阅的大改动。

## 4. 构建命令

使用 JBR25；本机环境辅助文件位于忽略的 build 下，不假定他人 checkout 已有。

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat verifyStructureUpgradeScaffold test -DisDev=true @physicsNativeArgs
./gradlew.bat testNativePhysicsRust -DisDev=true @physicsNativeArgs
./gradlew.bat nativePhysicsTest -DisDev=true @physicsNativeArgs
./gradlew.bat build -DisDev=true @physicsNativeArgs
./gradlew.bat build -DisDev=false @physicsNativeArgs
```

共享检查仅复用同修订/同配置的成功结果。构建含现有 Rust 红测时须明确报告失败；不能为了让骨架绿而把该测试从 check 拿掉。客户端验证使用隔离存档和 runClientDev；Sable 使用固定依赖和 runClientSable。新增业务后必须补对应玩法烟测，禁止修改原 test/test2。

## 5. 验证记录与 UNVERIFIED

- 最新角控制接线验证与未覆盖矩阵统一见 [B 线接线记录](structure_upgrade_line_b_live_2026_10_23.zh-CN.md)。以下骨架/独立算法数据保留为阶段历史证据；其中“未接线/未启用”不再适用于当前 ORIENTATION。

- B 线新增 62 项测试包含在当前全量 JVM 2,957 项中，全部通过；Sable 专项 16 项、开发/发布 build 均通过。隔离客户端现有台阶双扰动 H 3/5/7 与各 200 tick 通过并正常保存退出；该烟测不调用新算法。新 planner、角控制/连续角扫掠/状态机和三系 schema/异步 VM 已实现，真实双后端接线、来源绑定和新玩法验收仍 UNVERIFIED。详见 [B 线记录](structure_upgrade_line_b_2026_10_23.zh-CN.md)。以下骨架/S0 数据保留为各自阶段证据。
- 本骨架：编译与 `verifyStructureUpgradeScaffold` 通过；新契约测试 38 项包含在全量 JVM 2,895 项中，全部通过。Sable 专项 16 项通过。开发与发布 `build` 两种配置均通过。
- 隔离 `runClientDev` 通过：注册日志明确 gameplay disabled、7 项服务能力、2 个 provider 描述；现有台阶双扰动 H 的 3/5/7 实测 3.064/5.064/7.064 格，各稳定悬浮 200 tick。原 test/test2 未改动。此为原路径自动夹具，不是新业务、Sable 或人工验收。
- S0 原生全量：23 通过、5 失败、0 ignored。失败为拆分后的静置/扰动抬升；全局降暖启动虽能通过抬升，却损坏堆叠，已撤回候选。记录见 [S0 报告](structure_upgrade_s0_warmstart_2026_10_23.zh-CN.md)。
- `nativePhysicsTest` 受其 Rust 前置失败阻止；另显式用 `-x testNativePhysicsRust` 隔离运行 Java/FFM，33 项通过。该结果只验证 FFM，不消除上条失败。普通 `build` 未包含 Rust 专项，不能把 build 成功理解为原生全通过；本轮没有移除任何测试依赖。
- 未启用任何新玩法；业务 TODO 不代表测试通过后的实现。
- 已知基线：原生 disturbed socket 红测；产品层本地 3/5/7 双扰动通过不等于底层接触修复。Sable 同夹具、受控 N 扫描及人工验收仍缺。
- MPS、流体/软体：无 DLL 加载或游戏接入；刚体替换、SPH/代理耦合、XPBD/碰撞组合和守恒均待验证。
- 完成判据：每个模块既有边界测试又有对应 live 路径证据，能力声明与实际一致，自动恢复/碎裂故障注入通过，最后补全本表；不能以骨架注册日志或编译成功关闭玩法 UNVERIFIED。
