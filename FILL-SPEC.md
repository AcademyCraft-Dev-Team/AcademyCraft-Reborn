# 本地物理结构五项修复 — 实现记录与验收交接

## 2026-10-23 可替换服务与引擎骨架

新一轮六项技能结构完善和 MPS/流体/软体采用的接口、分工、TODO 标记、构建命令与未验证项见 [可替换物理骨架 FILL-SPEC](docs/structure_upgrade_fill_spec.zh-CN.md)。骨架已本地提交为 `26635388`。[B 线独立算法与节点契约](docs/structure_upgrade_line_b_2026_10_23.zh-CN.md)之后，已接入本地/Sable 真实子步角控制与三系 rotate/reset_orientation/spin 节点，见 [B 线接线与验证](docs/structure_upgrade_line_b_live_2026_10_23.zh-CN.md)。未替换 DLL、接入新提取 planner 或启用自动归块；其余服务仍为禁用骨架。

## 2026-10-20 后续：贴地接触、嵌地抬升与卸载设计

此节属于现有 live 路径修复，不启用下方 PIPE 草案。用户补充的“运动结构原位置有不可见阻挡”尚不能仅凭存档包围盒归因；专项证据与未覆盖范围分别记录如下：

- [贴地行走及幽灵阻挡审计](docs/sublevel_ground_contact_audit_2026_10_20.zh-CN.md)：普通承载不再调用服务端玩家传送确认流程；客户端承载与位置插值同 tick；斜面跨步按真实表面落地，空框角落不作为碰撞。
- [嵌地 H 抬升](docs/sublevel_embedded_lift_repair_2026_10_20.zh-CN.md)：原生缓存相邻盒内部面并修正接触法线，保持外墙、地板、顶板；精确合并真实几何；手动选区保留顶部依赖。Sable 捕获按实际重心重锚真实求解器，手动抬升在稳定到位后开始 200 tick，超时释放而不自动投掷。
- [源格同步](docs/structure_host_block_sync_2026_10_20.zh-CN.md)：已加载 FULL 但不 tick 的可见区块不会可靠发送普通方块更新；捕获提交和回滚按 section 显式发布当前状态。已用对照格复现并验证；它是独立确定缺陷，尚未证明就是用户所见不可见阻挡的唯一原因。
- [挖掘补充](docs/sublevel_mining_regression_2026_10_20.zh-CN.md)：修正种子等无耐久物品被人工附加 DAMAGE 组件而误判工具改变，创意种子破铁块、生存种子破泥土与铁镐破铁块均完成实机回归。
- [区块卸载回正方案](docs/structure_chunk_unload_restore_plan.zh-CN.md)：**DESIGN ONLY**。没有增加自动回正运行逻辑、配置或注册；需先解决排除自身票据的驻留判定、跨 chunk 耐久事务和冲突时的持久化暂停。

本轮最终命令、结果和剩余限制统一见接续记录最新节；不要用先前悬空平台通过代替紧密坑壁验收，也不要把修正内部面误解为禁用物理碰撞。

## 2026-10-20 晚间：live 渲染、植被与挖掘回归

此节补充实际生产路径的修复，不表示下方七个 `PIPE` 草案端口已经接线，也不改变 `GAMEPLAY_ENABLED=false`。

- `test2` 的 18 个结构、1,807 个视图方块已定位到逐块绘制状态比较热点。普通/cutout/透明模型按结构合批，保留原版顶点编码与逐顶点光照；最终有效同视角副本由 9.12 FPS 提升至 187.83 FPS，P50 帧时间 108.75→5.12 ms。早期 239.88 FPS 版本存在纹理坐标错误，不作为最终验收。详见 [性能诊断](docs/sublevel_render_performance_2026_10_20.zh-CN.md)。
- 空碰撞植被不再裁掉 H 选取；前置软障碍清理修复候选范围导致的预算饥饿。真实技能入口验证 3/5/7 格、各完整 200 tick、清理叶盖及发射；旧后端新增植物准入全状态检查通过。详见 [植被回归](docs/sublevel_vegetation_lift_2026_10_20.zh-CN.md)。
- 左键反馈、铁块挖掘会话终止、原版工具消耗及全局 `BlockDropsEvent` 已接入 live 路径。真实铁块输入烟测及默认/取消/替换掉落/经验/余草保护 GameTest 通过。详见 [挖掘回归](docs/sublevel_mining_regression_2026_10_20.zh-CN.md)。

仍未覆盖新的同场景 Sable 客户端配对、大规模碰撞吞吐、全部精密节点组合、所有模组方块实体/含水格投影与掉落异常故障注入。不要由上述局部验证关闭下方整个草案的 UNVERIFIED 清单。最终构建记录统一见 [接续记录](docs/structure_skill_migration_handoff.zh-CN.md)。

## 2026-10-20 新增：Sable 对照后的 v2 方案骨架

**状态：DRAFT PORTS IMPLEMENTED；未接入运行路径，GAMEPLAY_ENABLED=false，玩法验收 UNVERIFIED。** 本节优先于下方历史 IMPLEMENTED 标签；不表示旧问题已修复。设计与配对数据见 [v2 方案](docs/structure_pipeline_v2_plan.zh-CN.md)。新骨架没有替换现有后端，没有变更物理 ABI、存档或网络包。Sable 主体源码没有复制进 Academy。2026-10-20 后续另落地了 capture 分阶段计时、plot 预开池、局部 chunk 耐久与 3/5/7 悬浮 parity 验证（见下表与基线文档）；它们属于 live 调度/耐久/控制验证，不改变七个 PIPE 端口仍未接入运行路径的事实。

公开契约：`src/main/java/org/academy/api/common/structure/StructurePipeline.java`。
实现门面：`src/main/java/org/academy/internal/common/sublevel/pipeline/SubLevelPipelineDraft.java`。
七个方法已改为委托到有界草案服务，不再保留 TODO 或占位异常；主类仍只输出 `DRAFT_REGISTERED`，没有任何生产 tick/网络/渲染路径调用它们。

| 标记 × 方法 | 方法契约与接入点 | 完成判据 |
| --- | --- | --- |
| PIPE-01 `advanceCapture` | 接入既有 `StructureCaptureCoordinator.drive`；仅主服务器线程写世界；按 WorkLimit 分片，提交前必须完成原 journal 的持久化屏障；支持取消、失败恢复、重复推进；不得直接后台调用 `ServerLevel.save` | 捕获全部成功后源/目标所有权唯一；注入任一阶段失败能恢复；不再在一次 H 调用串行保存整个宿主与 plot 世界；记录各阶段 p95 与最坏帧 |
| PIPE-02 `compileGeometry` | 输入不可变 CellShape；section 占用掩码 O(V+K) 扫描，整块同材质合并，非整块保留真实形状；内部面/接触邻接在 native adapter 中有明确表示；保留质量与惯量；禁止把全局缩盒当最终修复 | 相同 128 格地面提取成功，连续表面无卡缝；空心、薄块、台阶、不同材料不被填实；单 section 工作量有上界；缩盒 0.02 仅保留作诊断对照 |
| PIPE-03 `publishTerrain` | 接入 `TerrainColliderCache`/`SubLevelRuntime`；发布验证 body epoch、sourceRevision、coverageGeneration；老任务拒绝；所有必需 section 准备齐才切换并步进；预算限制真实执行而非仅日志 | 缺地形不得穿透；预算压力/卸载/拆地板/旋转/高速情况下正确；保持现有保守暂停，直到有可验证的逐 body 隔离机制；票据/worker 在停服清理 |
| PIPE-04 `controlSubstep` | 接入 `SubLevelDirectControl` 与 native step；使用真实子步时长、当前姿态和速度；输出力/力矩而不是重复积分的 impulse；外层租约验证不移动到 worker | 一外层 tick 的累计冲量等于各子步之和；2/4/8 子步响应在约定误差内；无重力补偿重复、无过期命令泄漏；不修改投掷 50 blocks/s 语义 |
| PIPE-05 `advanceLift` | 接入 `VectorStructureService`，复用 `KineticLiftProfile`；sourceCenter 固定原位世界质心；首次到位锁存 arrival；hoverDeadline=arrival+200；liftDeadline 独立，受阻返回 BLOCKED | 三档实际升高 3/5/7±0.1；完整悬浮 200 tick、超时与取消可区分；再次 H 按当前朝向发射；真实键盘和精密节点使用同一控制契约 |
| PIPE-06 `samplePose` | 接入 `SubLevelPosePacket/Interpolation`；版本化协议携带 epoch/seq/serverTick/速度；有界历史，拒绝乱序旧帧，瞬移清历史；最大外推建议 1 tick；接触/模型共享姿态 | 模拟 0/50/150 ms 延迟和重复/乱序包，无持续抖动；超出外推窗口冻结并标 stale；不得用视觉平滑掩盖服务端慢 tick；协议与重连测试通过 |
| PIPE-07 `planMesh` | 接入 `BatchedBlockGeometry`/投影 renderer；9..128 格也可复用网格；content/light/resource revision 分离；BE 单独绘制；worker 只读快照，GPU 上传仅渲染线程 | 姿态更新零几何重编译；光照/内容/资源重载正确失效；无逐块每帧亮度查询；GPU/任务资源释放；真实客户端帧 p50/p95 有配对证据 |

公共 `Epoch` 中的 revision 由调用端按端口约定传递：几何端口用 content/collision revision，姿态端口用内容 epoch，不能把每次姿态更新的 revision 当作内容脏标记。实现时需补全有限值、大小、线程、重复输入与代际校验；当前 record 的防御性列表拷贝不是完整参数校验。

### PIPE 草案端口实现（2026-10-20 第二轮）

| 端口 | 实现 | 已写入的约束 | 仍未完成/未验证 |
| --- | --- | --- | --- |
| PIPE-01 `advanceCapture` | `StructureCaptureCoordinator.advance` + 私有 `slice`/`copySlice`/`finishCopy`；`SubLevelPipelineDraft` 转发 | 仅服务端线程；单次最多一个阶段转换与 `maximumCells` 个复制格；源/权限/激活复核；journal COPIED/COMMIT_DECIDED 持久化屏障仍在；中途中止可由原 journal 回滚/恢复；重复推进使用独立 `sliceCursor` | 后端 `save` 仍是现有整级保存协议；未改成局部耐久协议；未跑真实捕获/崩溃恢复矩阵；正常 tick 的 `driveAll` 仍会完整驱动 pending，接入时必须切换所有权；PIPE-01 尚未降低创建尖峰；后端整级 save 未替换 |
| PIPE-02 `compileGeometry` | `PipelineGeometryCompiler` | 重复格/非法盒拒绝；O(V) 占用扫描 + 同材质满格贪心 3D 合并；非满格保留真实盒；无全局缩盒；时间/格预算返回 `complete=false` | 未接 native 体素/邻接 ABI；内部面与接触邻接仍需 native adapter；未跑 128 格真实轮廓 |
| PIPE-02 实机热点（继续开发） | `TerrainSectionGeometry.merge` 改为三轴分组贪心合并 | 保持精确并集；4096 格实心 section 合并为 1 盒；加入 9 项单测与 5 秒上限压力用例；旧两两重启 O(n³) 路径删除 | 仍需真实 H 捕获/JFR 与 Sable 配对复测；未接 PIPE-02 草案端口，未改 native ABI |
| 捕获热点（继续开发） | `PlotStorage` 预开池 + `CaptureTimings` 分阶段日志 + `SubLevelServer.tick` 调度 | 默认保留 2 个已加载空 plot；每 tick 最多一个预开区块；`academy.sublevel.plotPrewarm=0` 关闭；capture 363→227ms，r13/r14 两轮创建比 1.73×/1.67×（r7/r8 2.93×） | 首个预开区块 tick 1 仍约119ms（工作移出 capture，非消除）；整世界保存约107ms 未改；旧探针 ad-hoc 增益下 hover 27，live 设置 3/5/7 已通过（r22/r23）；预开无单测，靠 parity 实测；首次进服/启动延迟未测 |
| 局部 chunk 耐久（继续开发） | `TargetedChunkDurability` + `ChunkMapAccessor`；`SubLevelRuntime.finalizeStaged` / `SubLevelCaptureTarget.save(…, positions)` | `ChunkMap.save(ChunkAccess)` + `synchronize(true)`；只保存目标 plot lease chunks 与源 host chunks；journal COPIED/COMMIT_DECIDED/PUBLISHED 顺序不变；r15 capture 227→139.5ms；r16/r17 两轮创建比 1.09×/1.20× | 仅 SubLevel live 路径；native/Sable 与无 positions 调用仍整级保存；崩溃/恢复矩阵未跑；只覆盖方块数据，实体/level.dat 未保存；旧探针 hover 27，live 设置 3/5/7 已通过 |
| 3/5/7 悬浮（继续开发） | live `SubLevelKineticLiftProfile` + `KineticStructureControl`；parity probe 顺序三档；客户端 `SubLevelLiftTiersSmoke` | 每档连续 200 tick、±0.1；server parity local 3.064/5.064/7.064、Sable 3.023/5.023/7.023；客户端 tier 0/1/2 rise 3.064/5.064/7.064，各 200 tick；tier-0 ground smoke 200 tick；生产 lift 代码未改增益/容差 | 客户端是模拟 H 包，不是手动键盘/相机/网络；Sable 客户端路径未跑；Sable 256 tier 0 被 live mass gate 拒绝；legacy 失败检查点按 skip 处理，显式 discard 仍待产品路径； |
| PIPE-03 `publishTerrain` | `PipelineTerrainPublisher` | 宿主线程/维度校验；epoch/revision/coverage 代际拒绝；required section 缺失或几何不完整拒绝；原子替换已发布集合；`coverageComplete` 供未来步进门禁 | 未把 `Geometry` 写入 native world；live 门禁仍是 `TerrainColliderCache.coverageComplete`；未接 runtime/票据释放；未跑预算/卸载矩阵 |
| PIPE-04 `controlSubstep` | `PipelineSubstepControl` | 真实子步时长校验；`StructurePhysicsControls.force` 的 PD/阻尼；力/力矩输出；力矩 = r×F；不乘外层 dt、不重复重力 | 未接 `SubLevelDirectControl`/native step 的子步循环；未验证 2/4/8 子步与累计冲量 |
| PIPE-05 `advanceLift` | `PipelineLiftController` | 仅接受 3/5/7；原位质心；垂直/水平 0.1 容差；首次到位锁存 arrival 与 hoverDeadline；liftDeadline 返回 BLOCKED；终态不复活 | 未接 `VectorStructureService`/真实按键；三档实机高度、悬浮 200 tick、转向投掷未采 |
| PIPE-06 `samplePose` | `PipelinePoseTimeline` | 最新 epoch；sequence 去重；serverTick 单调校验；teleport 清旧历史；窗口内线速度/角速度外推；超窗外冻结并标 stale；历史最多 256 帧 | 未扩展现有包携带 epoch/seq/serverTick；未接 `SubLevelPoseInterpolation`/渲染/接触取样；延迟/乱序/重连未测 |
| PIPE-07 `planMesh` | `PipelineMeshPlanner` | content/light/resource revision 分离；姿态-only 请求零重建；脏 section 精确重建；光照变更可复用几何；BE 单独绘制；9..128 格无 512 门槛；LRU 512 epoch | 未接 `BatchedBlockGeometry`/投影 renderer；未验证 GPU 上传线程/资源释放/真实帧 p50/p95 |

测试（只覆盖草案服务，不代表玩法）：`src/test/java/org/academy/internal/common/sublevel/pipeline/` 新增 7 个测试类共 49 项，覆盖几何合并/空腔/预算、力矩与子步标定、3/5/7 到位/锁存/超时、姿态插值/外推/瞬移/新 epoch、mesh revision 与 terrain 代际/覆盖/旧 section revision；`verifyStructurePipelineDraft` 改为检查七个端口委托、无占位异常、bootstrap 仍 disabled、模板仍 UNVERIFIED。

骨架注册：`SubLevelPipelineBootstrap.register` 已在 `AcademyCraftRegister` 接入，仅输出 `DRAFT_REGISTERED`。`DRAFT` 供后续实现接线发现；七个方法不得在填完、通过对应回归前接入 tick。资源模板 `src/main/resources/academy/structure_pipeline_v2.template.json` 不被运行时读取，预算值不是实测承诺。

构建命令（JBR 25）：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat verifyStructurePipelineDraft test -DisDev=true @physicsNativeArgs
./gradlew.bat build -DisDev=true @physicsNativeArgs
./gradlew.bat build -DisDev=false @physicsNativeArgs
```

配对测试入口：`validation/structure_parity/run-pair.ps1` 与 `server_parity.init.gradle`。完整命令、采样含义与无效样本规则见该目录 README。夹具是已实现的测量工具，不属于留给 Agent B 的玩法业务方法。它通过公开 capture/direct-control API 调用当前后端，使用全新隔离世界，不写入 `run/saves/test`。

**UNVERIFIED 清单**：七个端口仍未接入生产 tick/网络/渲染路径，其实机验收全部待做；PIPE-01 的 SubLevel 捕获已改为局部 chunk 耐久，但 native/Sable 与无 positions 的调用仍整级保存，崩溃/恢复矩阵未跑；3/5/7 的 200 tick 已在 dedicated-server parity 与真实客户端 lift-tiers smoke 通过（各 200 tick），tier-0 ground smoke 也为 200 tick；但手动键盘/相机/网络、Sable 客户端和 legacy 失败检查点的显式 discard/恢复仍未验收；PIPE-02/03 native 体素/邻接 ABI 与实际地形写入、PIPE-06 包 epoch/seq 字段、PIPE-07 renderer 接线均未完成；真实 H 三档与节点、客户端渲染/可视首帧、多人网络、GC/分配/带宽、1/16/64 个结构与 256/4096 格大结构、复杂地形和 Sable 坐标/质心对齐审计、许可范围内任何后续源码复用的逐文件来源记录仍未关闭。当前服务端小样本与草案单测不能关闭这些项目。

完成顺序：先修 PIPE-02/03 的真实碰撞与热点 → PIPE-04/05 控制闭环 → PIPE-01 创建停顿/可视发布 → PIPE-06/07 流畅性。2026-10-20 的 capture 剖析、plot 预开与局部 chunk 耐久已完成 PIPE-01 的主要性能项（r11–r17，创建比 2.93×→1.09×/1.20×）；3/5/7 的 200 tick 已由 live 法则在 parity 通过（r22/r23），但真实 H、客户端和崩溃/恢复矩阵仍未验收。每步先启用窄范围验收开关；旧 backend ID、保存格式、控制租约、权限与掉落路径保持原入口。所有门槛通过后另立实现校验，才允许改变 `GAMEPLAY_ENABLED`，不得把草案任务的通过当实现验收。

本轮骨架验证结果（2026-10-20）：

| 检查 | 结果 |
| --- | --- |
| `verifyStructurePipelineDraft` | PASSED：DRAFT_IMPLEMENTED / Business logic: IMPLEMENTED (unwired draft ports) / Gameplay UNVERIFIED；校验七个端口委托、无占位异常、模板 disabled、注册恰好一次 |
| JVM / Rust | 普通 2809（含新增 pipeline 49 项）、native 27、Sable 16、editor 104，共 2956；失败/错误/跳过为 0。Rust 13 通过 |
| 开发 / 发布构建 | 第二轮均通过（dev 48 秒、release 68 秒）；产物哈希见下方。第一轮骨架日志 `build/structure-pipeline-draft-*.log` 仅作对照 |
| 真实后端服务端夹具 | r5/r6 共 8 次完成采集、质心对齐通过；Sable 200 tick 精度条件通过，本地在本夹具参数下失败；不得视为玩法全通过 |
| 原存档 | 原 test 160 个数据文件、变更 0；`build/structure-parity-source-check.json` |
| 客户端与真实 H/精密节点 | 本轮未执行；没有启用新玩法业务路径。帧性能/可视发布和完整玩法保留 UNVERIFIED |

PIPE 端口实现第二轮产物 SHA-256：dev `F30A2AF1C40BF0B3874FE9323E503A6911068643C9EA7A411B142A9D456D0CA4`；release `A665ADED24AADFFAEAE5C21FEE95C92E5572ADF688A8F44C2903AAE21BC6CAD7`（2026-10-20 09:04 +08:00）。开发/发布构建均通过。工作区仍包含此前开发改动，这些构建不是“只有本轮草案端口”的独立补丁包。第一轮骨架哈希（历史，已被本轮构建替换）：dev `8F6D0AA7231F3703020AA42DB657D0AA881E949F1AE9BE1FAFDC711D51CEA96B`、release `FC36D9D0DAC38D8C8F0B33D7A494B9B73252F60D3F365B29F36E34A30ED5A9F1`。

下方为此前修复与验证的历史记录；本轮新增的配对基线和草案状态以本节及 [对照报告](docs/structure_parity_baseline.zh-CN.md) 为准。

日期：2026-10-20。状态：**碰撞专项实机、最终测试与双构建通过；五项整体实机验收 UNVERIFIED**。

**后续诊断更新：真实 H 地面提取与性能仍不合格，修复待实施。** 128 格密合地面提取的三档抬升都可复现卡死；等质量原生对照确认逐块接触/容差问题。JFR 的服务器执行样本约82.6%落在地形盒合并，真实 H 捕获约244–658ms；批量渲染门槛512使本地H最多128格无法使用该路径。当前仅完成分析，生产代码未修；完整证据与修复顺序见[接续记录最新一节](docs/structure_skill_migration_handoff.zh-CN.md)。此前 r8 落地/撞墙/F3+B 通过不能替代 H 抬升和性能验收。

此前的骨架已补齐并接入运行路径；随后用户反馈 F3+B 仍显示结构外层大框、运动卡顿及穿透宿主方块，本轮继续修复这些回归。最终碰撞专项 r8 已在原 test 的隔离副本中通过并检查截图，具体数值与边界见第 3 节。没有五项完整实机证据或 Sable 对照性能数据，不得把碰撞专项通过写成五项问题已全部验收。

## 0. 本轮碰撞回归根因与修复

- **宿主地形漏采样**：把多个结构范围合为一个 union，会让相距很远的结构之间的空区、甚至落入虚空的旧结构消耗地形 section 预算。改为逐结构收集本 tick 的扫掠范围，覆盖平移速度、重力及旋转半径；过滤世界高度之外的范围，不采结构之间的空隙。`TerrainColliderCache` 优先满足 required sections，空 section 作为覆盖记录而不占刚体预算；缺失/失败/超预算时暂停原生步进并输出原因，不能在碰撞器缺失时继续穿透。
- **必要区块加载与预算**：`NativeStructureTickets` / `TerrainColliderCache` 为 required section 对应的去重 chunk 增加独立 `sublevel_collision` FULL 票据；不覆盖 halo 或结构间空区，不请求 simulation/persist。每宿主最多 64 个票据，每服务端 tick 最多同步加载 4 个 chunk；移出 required 集合、clear/close 时释放。原 test 恢复 14 个结构，旧 16 solid section 上限在仅 277 盒时就让整宿主暂停；默认必需容量现为 64，可选 halo 只在总数不足 16 时预热，28,000 盒与原生刚体硬上限保留。未完整安装必要碰撞器时整个宿主物理步暂停并报告原因，不能把未加载/超限地形当空气。
- **客户端三套姿态不同步**：旧碰撞每次 `Minecraft.runTick` 按 25% 追目标，实际受渲染 FPS 影响；模型另用五 tick 线性插值，选取又读最新包目标。现在移动姿态每服务器 tick 发小型差量；包只替换 pending target，`SubLevelPoseInterpolation` 在真正 `Minecraft.tick` 的实体移动前锁存 previous/current。碰撞使用 current，渲染与选取按 previous/current 和 partial tick 插值，与原版实体同相；积压包不会在帧中途直接改变显示，也不依赖世界时间在 tick 内何时递增。首次/内容变化仍全量，静止不发；包线格式及维度筛选不变。
- **F3+B 外层大框**：来源是原版 `EntityHitboxDebugRenderer` 对投影实体宽相 AABB 的调试绘制，不能据此判定该 AABB 仍参与玩家碰撞。新 Mixin 只跳过所有 `NativeStructureProjectionEntity` 的通用框，包括旧 native 非 clientOnly 投影；其他实体 F3+B 正常。真实 bounds、碰撞与 culling 不变。SubLevel clientOnly 投影同时退出旧碰撞索引及支撑判断，局部碰撞盒按内容缓存，不因姿态包反复重建。
- **OBB 接触边界**：修正近乎平行 SAT 轴的容差、非单位交叉轴的穿透深度比较，以及已重叠时沿表面/向外移动被错误锁死的问题。支撑改用真实脚底 footprint 向下扫掠并要求朝上的接触法线，不再把局部顶面附近的竖墙或空气算作平台。台阶、去穿透和承载位移同时受宿主墙/顶棚碰撞裁剪。
- **承载历史重复消费**：服务端使用独立 `carryPose` 锚点，`carryEntities` 在 `finally` 调用 `consumeCarryPose`，即使没有乘客、结构失活或正在等待地形也消费本轮位移。显式 shift/rotate 累积到本轮搭载；`previousPose` 与碰撞索引历史保留给移动结构的延迟交互校验。每个结构独立查询前后姿态覆盖范围，跨结构共享已搭载实体集合；不再把远处结构合为巨大实体查询范围。客户端静止 tick 同样令 previous=current，避免反复施加最后一次移动。

本节是代码修复与验收交接，不代表五项整体通过。新实机 fixture `SubLevelCollisionRegressionSmoke` 覆盖相距 80 格的两处地板、50 blocks/s 撞宿主墙、删除地板后落到下层、客户端投影与 F3+B 截图；完整手动操作、交互/抬升/软块矩阵及配对性能测试仍按第 4 节补齐。

## 1. 五项结论

| 项目 | 当前状态 | 实现位置 | 仍未验证 |
| --- | --- | --- | --- |
| PERF 性能 | IMPLEMENTED：脏版本、休眠跳过、地形按需、差量同步、阶段实测 | `SubLevelSimulationBudget`、`SubLevelRuntime.tick`、`SubLevelServer.sendSnapshots`、`SubLevelPhysicsUniverse.step` | 无 Sable/local 配对基线、无 p50/p95、无 GC/包字节数据；不宣布“性能问题已解决” |
| CONTACT 碰撞 | IMPLEMENTED：世界 AABB 不再进入实体碰撞；OBB 窄相裁剪、台阶、去穿透、精确承载、客户端插值 | `SubLevelOrientedCollision`、`SubLevelContactResolver`、`SubLevelCollisionIndex`、`MixinEntity`、`MixinCollisionGetter` | 真实玩家在斜面/空心结构/旋转平台上的轨迹与画面未测；原版摩擦、支撑方块与摔落判定仍读宿主方块 |
| INTERACT 交互 | IMPLEMENTED：服务端权威挖掘会话、裂纹/进度、真实 loot/XP/耐久、放置/使用映射 | `SubLevelInteractionController`、`SubLevelMiningRules`、`SubLevelPlaceContext`、`SubLevelMiningPacket/ProgressPacket`、`SubLevelClientInteraction` | 未在生存/创造多人世界实测；未处理 HIT_BLOCK 附魔、BlockDropsEvent 全局钩子 |
| LIFT 抬升 | IMPLEMENTED：0/1/2 → 3/5/7，世界质心基准，0.1 格容差，首次到位 +200 tick，重力前馈 | `SubLevelKineticLiftProfile`、`KineticStructureControl.gravityCompensatedTarget`、`VectorStructureService` | 三档实机高度/横移/到位 tick 未采；Sable 牵引的实际质心未实测 |
| SOFT 软块 | IMPLEMENTED：旋转扫掠、允许/保护 tag、授权、预算、掉落与缓存失效 | `SubLevelTerrainBreakPolicy`、`SubLevelTerrainBreaker`、两个 block tag | 未在真实结构运动时验证草/叶破坏与保护区拒绝；非玩家默认拒绝 |

## 2. 逐项实现与契约

### PERF（PERF-01..04）

- `SubLevelSimulationBudget` 维护每宿主、每 `StructureRef` 的独立 `DirtyVersion(content, collision, pose, terrain)`。`invalidate` 只递增对应部分，宿主/维度不匹配直接拒绝。
- `plan` 为每个结构排出 TERRAIN_SCAN / GEOMETRY_BUILD / PHYSICS_STEP / CONTACTS / SNAPSHOT_ENCODE / PERSIST。必需物理步与接触永不被预算裁掉；上一 tick 的实测超预算时只裁掉可选快照编码，延后项写入 `FramePlan.deferred`。
- `record` 保存实测阶段纳秒/工作量，每 tick 有上限、历史保留 200 tick；`metrics` 对没有采样的 tick 返回空列表，不伪造零耗时。`closeHost`/宿主关闭时释放。
- `SubLevelRuntime.tick`：仅在姿态真正变化时调用 `setPose`/`collision.update`/`remember`；碰撞索引改为“局部盒 + 姿态”的可变条目，姿态变化不再重建几何；地形缓存按各结构下一 tick 扫掠范围及宿主变更更新，不再合并全部结构为一个 union；全休眠刚体可跳过求解，必要地形覆盖不足时禁止推进。
- `SubLevelServer.sendSnapshots`：每服务器 tick 检查，首次/几何变化发完整 `SubLevelStatePacket`（携带**局部**碰撞盒），之后只发 `SubLevelPosePacket`；每玩家记录已发视图/姿态，未变化不发送。`SubLevelCollisionIndex` 的局部盒空间网格把实体查询从 O(全盒) 降到 O(附近格)。
- 实测入口：`SubLevelRuntime.recordSnapshot` + `SubLevelSimulationBudget.metrics`；通过 `academy.sublevel.budgetLog` 可打印每次 plan 的调度/延后数量。

### CONTACT（CONTACT-01..04）

- `SubLevelCollisionIndex` 保存局部碰撞 cuboid 与当前/上一姿态；世界 AABB 只作宽相与 AI 寻路提示。
- `MixinCollisionGetter` 只对 `PathNavigationRegion` 追加粗盒；`Level` 的普通 `getBlockCollisions` 不再返回结构 AABB，避免空心/倾斜结构的“隐形墙”。
- `MixinEntity` 在私有 `Entity.collide` 返回处调用 `SubLevelContactResolver.resolveMovement`：以真实 OBB 做逐轴扫掠 SAT（15 分离轴、进入/离开区间），保留空隙、支持滑动、按原版顺序 Y→较小水平轴→另一水平轴裁剪；水平被挡时用 OBB 角点高度做 0.6 级台阶；起点已重叠时沿最小平移轴有界去穿透（≤0.55）。
- 客户端从同步的**局部盒 + 姿态**构建同一份 OBB，不再从预算合并后的世界 AABB 近似；`SubLevelPoseInterpolation` 统一一 tick 插值，`SubLevelClientState.tick` 在实际游戏 tick 推进接触，选取与模型使用相同采样路径，不按渲染帧次数改变运动。
- `SubLevelContactResolver.carry`：仅对上一姿态真实支撑的实体施加一次姿态映射位移（含旋转），位移受宿主碰撞裁剪，每实体每 tick 至多一次，乘客共享同一 delta；`SubLevelServer.carryEntities` 无论是否发现乘客都在结束时推进独立 `carryPose` 锚点，防止休眠/暂停后重复承载，保留交互使用的 `previousPose`。
- 保留 `SubLevelCollisionIndex.navigationBoxes` 作为原版寻路的粗提示；它不是实体碰撞，也不用于交互授权。

### INTERACT（INTERACT-01..10）

- 服务端 `SubLevelInteractionController` 按连接/玩家维护单一会话：START 绑定局部格、运行期 UUID、几何 revision、原方块/BE、真实工具、槽位与模式；CONTINUE 只续约（10 tick 租约），ABORT 幂等；旧 requestId 不能结束新会话。
- `tick` 每服务端 tick 最多推进一次：复核宿主/结构/激活、几何 revision、原状态、工具/模式、reach、宿主遮挡与保护；按原版硬度/工具速度/可收获、创造即时完成累积；完成调用 `finishMining`。
- `finishMining` 在 plot 上走原版 `playerWillDestroy → mineBlock → onDestroyedByPlayer → destroy → Block.getDrops/XP`，物品和经验生成在当前姿态变换后的宿主位置；工具耐久、附魔与创造规则由原版路径处理；提交前后重验状态/BE；成功后重建几何。全局 `BlockDropsEvent` 注入尚未处理（见第 6 节）。
- 裂纹/进度通过 `SubLevelMiningProgressPacket` 发给本人与 64 格内观察者（最多 24 人），终态 stage=-1 清裂纹；客户端 `SubLevelMiningClient` 按 actor+request+局部格排序，投影渲染器合并显示。
- 放置使用 `SubLevelPlaceContext`：plot Level、局部朝下/旋转、最近朝向与局部 yaw；多格放置由原版 `BlockItem.place` 处理；真实持物/模式、保护区、宿主实体阻挡与 `StructurePlacementEvent` 在写入前复核，写入后刷新几何。
- 使用保留主副手/潜行/原版调用顺序，命中点统一映射到 plot 坐标；容器菜单的 `stillValid` 使用实时姿态中心。
- 旧 `SubLevelInteractPacket.BREAK` 兼容规则：不直接 `destroyBlock`；同格重复点击续约既有会话，否则以服务器分配号 START。旧 USE/PLACE 同样路由到控制器。

### LIFT（LIFT-01..03）

- `SubLevelKineticLiftProfile`：tier 0/1/2 → 3.0/5.0/7.0 格；源世界质心 = 源位姿 × 局部质心；目标只改 Y，X/Z 保持。
- 验收用实际世界质心：`actualRise >= requested - 0.1`；不再保留旧的 `-0.6` 提前判定。
- 首次实际到位 tick 锁存一次，截止 = 该 tick + 200；后续到位不重算。`KineticThrow.LIFT_HEIGHTS` 同步改为 3/5/7。
- 悬浮使用 `KineticStructureControl.gravityCompensatedTarget` 把 PD 目标抬高 `mass*g/stiffness`，使平衡质心真正到达目标；容差判定与补偿力分开，仍以 0.1 格验收。
- 接入 `VectorStructureService`：SubLevel 用捕获源格位姿 + 局部质心；native region 用就绪时实际位姿；Sable 保留牵引表面目标，但用实际质心测量完成。

### SOFT（SOFT-01..03）

- `SubLevelTerrainBreakPolicy.scan`：用 `StructureImpactSweep.sweptBounds` 限定世界范围，限制读格/形状测试/不加载缺失区块；按 travel+旋转弧采样 1..48 个姿态，把候选方块反变换到局部网格，用局部盒空间网格判断真实扫掠接触，不把世界 AABB 空区算命中。
- `authorize`：显式 actor（默认仅玩家；非玩家由 `SubLevelTerrainBreaker.setNonPlayerPolicy` 扩展，默认拒绝）、`DestroyBlocksSetting` 开关、宿主 `mayInteract`、保护 tag 优先、硬度 < 0.5、无 BlockEntity、允许 tag 或默认低硬度植物；受 `visitedCells/shapeTests/destroyedBlocks` 预算。
- `apply`：逐格重验状态/权限/保护事件，`host.destroyBlock` 一次，随后 `SubLevelServer.onBlockChanged` 失效地形缓存；掉落由原版路径产生。
- 资源：`structure_motion_breakable` 已列出草/蕨/花/树叶/藤/雪等；`structure_motion_protected` 保护 `#minecraft:crops`、`#minecraft:saplings` 与紫水晶芽。
- `SubLevelDirectControl.submit` 记录当前控制 actor，授权 30 秒；松开控制后仍保留有界窗口，让已投掷结构按原施术者权限继续破坏软块。结构移除时清理。

### 注册与资源（BOOT-01）

- `SubLevelEnhancementBootstrap` 持有五个共享实例，`commonSetup` 标记 installed 并打印 3/5/7 抬升标定；不安装第二套碰撞/掉落路径。
- 新网络包：`SubLevelPosePacket`（S2C 姿态差量）、`SubLevelMiningPacket`（C2S START/CONTINUE/ABORT）、`SubLevelMiningProgressPacket`（S2C 裂纹/进度）。旧 `SubLevelStatePacket`/`SubLevelInteractPacket` 线布局未改；`SubLevelStatePacket` 的 box 语义改为**局部**盒。
- 服务端在 `SubLevelServer.tick` 调用交互 tick（独立 try/catch，不拖垮物理），停服关会话；玩家离线/换维/重生取消会话。

## 3. 构建与测试记录

命令（JBR 25 + 仓库 wrapper + 本机 native env）：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat verifySubLevelScaffold test nativePhysicsTest sableCompatTest editorTest testNativePhysicsRust build -DisDev=true @physicsNativeArgs
./gradlew.bat build -DisDev=false @physicsNativeArgs
```

历史实现基线与本轮中间结果（均不能替代最终冻结代码复验）：

- `verifySubLevelScaffold`：通过；输出 `IMPLEMENTATION_CHECKED / Gameplay: UNVERIFIED`。检查五个实现无 TODO/占位异常、运行路径确实引用、tag 非空、验收模板五组仍为 UNVERIFIED。
- 初次实现基线：普通 JVM 2735 项、native JVM 26 项、Sable 16 项、编辑器 104 项、Rust 13 项通过；只到主菜单，没有五项世界验收。
- 本轮碰撞修复 r1：普通 JVM **2755 项**、native JVM **27 项**、Sable **16 项**、编辑器 **104 项**、Rust **13 项**通过；开发/发布构建成功，日志 `build/sublevel-collision-repair-build-dev-r1.log`、`build/sublevel-collision-repair-build-release-r1.log`。其后 required chunk 票据等仍有改动，最终代码必须重跑相应检查。
- 客户端失败与夹具修正轨迹均保留：r1/r2 停在首次资源回退/加载提示，未进世界；r3 进入世界后暴露 16 section 预算不足，推动生产修复到 64；r4/r5 把首次接触约 0.119 格的瞬时嵌入误判为穿透；r6/r7 窄矮墙允许刚体翻过或绕出，累计 X 越过平面不能证明穿墙。最终夹具使用宽 49、高 40、厚 2 的石墙；接触瞬间容差 0.25，100 tick 后落定容差收紧到 0.03，没有修改求解器来迁就测试。
- **碰撞专项 r8 通过**：`build/sublevel-collision-repair-client-r8.log`，恢复 14 个既有结构后新增两个 8 方块结构，相距 80 格，地形缓存实际使用 28 sections / 1052 boxes。两个结构底面落定为 `247.99998537` / `247.99999893`，石台顶面为 248；实体真实移动被侧面阻挡、可反向离开，并落在顶面 `249.99999442`。以 50 格/秒撞墙，最大 X=`-185.00439051`，墙面 X=`-185`；拆上层地板后第二结构下落 `4.00001698` 格，稳定底面 `243.99998195`，下层台顶面 244。
- r8 出现 `SUBLEVEL_COLLISION_ENTITY/FLOORS/WALL/MUTATION_PASSED` 及 `SUBLEVEL_COLLISION_REGRESSION_PASSED`，客户端观察 225 tick；截图 `run/native-sublevel-direct-smoke-collision-repair-r8/screenshots/sublevel-collision-regression-1792434640891.png` 已查看：铁块结构无外层实体调试框，周围普通实体仍有 F3+B 框。日志断言 `hitbox_debug_enabled=true`。
- **本轮范围外的现场问题仍保留**：一个旧 native 区域 `9060e07a-0406-4b0b-a987-509cbad61cb0` 在存档中已为 FAILED，启动拒绝自动重组；未删除附件或伪造恢复。r8 退出时，启用矢量反射的玩家在时间运行时已停止后清理免疫，出现 `Temporal runtime has stopped`；这是独立退出清理异常，不能将该次记录为“正常退出无异常”。碰撞与截图 PASS 发生在此异常之前。
- 原 `run/saves/test` 不作为写入目标；实机仅使用准备脚本创建的副本。结束时原世界 160 个数据文件与 r1 开始基线 SHA-256 全部一致（排除运行中的 `session.lock`），证据 `build/sublevel-collision-source-world-hash-check.json`。

最终冻结代码结果（实机 r8 后仅完善文档；生产代码未继续变动）：

| 验收项 | 最终状态 | 结果/证据 |
| --- | --- | --- |
| 普通/native/Sable/编辑器/Rust 测试 | PASSED | 普通 2760、native 27、Sable 16、编辑器 104，共 2907 项 JVM，失败/错误/跳过均 0；最终开发构建同时执行 Rust，13 项通过 |
| 开发/发布构建 | PASSED | `build/sublevel-collision-repair-build-dev-final.log`（49 秒）、`build/sublevel-collision-repair-build-release-final.log`（46 秒）；最终开发命令为 `test nativePhysicsTest sableCompatTest build -DisDev=true`，发布为 `build -DisDev=false`，均带 `@physicsNativeArgs`；build 同时运行 editorTest / 校验任务 |
| 必要覆盖与缺地形暂停 | PASSED（已测范围） | 原生缺覆盖暂停/恢复回归；原存档超过 16 sections 的实机落地通过。票据释放、64 chunk 超限的全生命周期实机矩阵仍待补 |
| 碰撞回归客户端与 F3+B 画面 | PASSED | r8 的实体/地板/墙/拆地板标记及已查看截图；退出清理异常另记 |
| 五项整体实机与 Sable 配对性能 | UNVERIFIED | 不随本轮局部回归通过自动关闭 |

最终产物（`build/libs/`，后续构建可能覆盖）：

- `academy-26.2.0-0.0.4-alpha-dev.jar`，SHA-256 `49DB0F4C1E3947AB83503C75B050459EC7525470FAA44C32363E47DB33FF80BA`。
- `academy-26.2.0-0.0.4-alpha-release.jar`，SHA-256 `201E9C015618A6BBA95EDE5A32F4F756F2F3645C359E098EECE3D49F3F8F5DEE`。

## 4. 未验证清单（必须在实机补齐）

1. **PERF**：固定机器/种子/布局，采集 1×55、1×81、16×256、64×256、1×4096 在休眠/运动/交互下的服务端 tick 与客户端帧 p50/p95、GC/分配、包字节/秒、重建次数，并与同场景 Sable 配对。当前只是结构上消除了每 tick 的世界盒重建/全量重发/全维度实体扫描；没有数据证明相对 Sable 的差距。
2. **CONTACT**：空心/倾斜结构可走过、真实表面稳定阻挡、台阶/跳跃/墙边/纯旋转/平移/骑乘、客户端预测与服务端校正、每 tick 单次承载、无持续来回校正。另验相隔多个 chunk 的结构均有地形支撑、高速撞墙、必要 chunk 加载与释放、覆盖不足暂停后恢复，以及 F3+B 不显示所有本地投影外框但其他实体仍显示。需轨迹与画面。
3. **INTERACT**：生存/创造/不同工具速度的裂纹、取消清除、loot/XP/耐久/附魔只结算一次、创造无额外物品、重复/乱序不复制、宿主拾取/plot 无残留、放置/多格/双手/容器/保护/移动中断线。
4. **LIFT**：三档实际质心升高 3/5/7（目标误差 ≤0.1、横移 ≤0.1）、悬浮完整 200 tick、转向 H 按当前视线投掷、手动/程序优先级。
5. **SOFT**：草/叶只在真实扫掠接触且授权时破坏；旋转/高速不漏扫、不误伤 AABB 空区；硬块/BE/保护区/关闭破坏开关/无授权 actor 拒绝；预算耗尽有界、掉落与地形缓存同步。
6. **协议与兼容**：旧 native/Sable 路由、旧包、离线/重连/跨维/停服重启、多客户端延迟与乱序。

## 5. 已知边界（非本轮目标）

- 实体移动的原版“支撑方块/摩擦/摔落伤害重置”仍读取宿主方块；结构内部的冰、粘液、岩浆等方块不会被当作运动介质。
- 挖掘掉落不走全局 `BlockDropsEvent`（直接使用原版 loot 表并生成到宿主）；依赖该事件的附属模组不会看到 SubLevel 掉落。HIT_BLOCK 附魔效果未实现。
- 一个 SubLevel 的局部碰撞盒超过 16384 时，同步到客户端的盒会被截断（服务端仍完整）；这是有界传输保护，不是精确同步。
- AI 寻路使用粗世界盒提示；实体接触使用精确 OBB。两者精度不同是刻意的。
- `SubLevelStatePacket` 的 box 语义已从世界盒改为局部盒，旧客户端不能与新服务端混用；服务端/客户端需同版本。
- 必需地形超过 64 solid sections、64 chunk、28,000 盒或原生刚体上限时，当前仍保守暂停整个宿主物理并限频报原因；没有逐结构暂停 ABI，不能声称任意规模都流畅运行。
- 碰撞专项通过不关闭第 4 节的完整玩法、多人、生命周期和配对性能未验证项。

## 6. 复验入口

- 结构校验：`./gradlew.bat verifySubLevelScaffold -DisDev=true @physicsNativeArgs`，查看 `build/reports/sublevel-scaffold/summary.txt`。
- 单测：`./gradlew.bat test -DisDev=true --tests "org.academy.internal.common.sublevel.*"`。
- 碰撞专项实机（每轮使用新目录，不运行原 `run/saves/test`）：

```powershell
. ./build/native-physics-p16-env.ps1
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name collision-final-rN
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/sublevel_collision_regression.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-collision-final-rN @physicsNativeArgs
```

- 专项成功须同时有 `SUBLEVEL_COLLISION_FLOORS_PASSED`、`SUBLEVEL_COLLISION_WALL_PASSED`、`SUBLEVEL_COLLISION_MUTATION_PASSED`、最终 `SUBLEVEL_COLLISION_REGRESSION_PASSED`，并检查其截图。仅客户端启动/退出成功或只有阶段标记不能代替最终通过；专项通过也不代表五项整体验收。
- 实机证据仍按 `validation/native_structure/sublevel_enhancement_cases.template.json` 复制填写，保留 UNVERIFIED 直到真的进入世界测过；缺失性能数值留空，不填假零。
- 本轮未创建 Git 提交；按 AGENTS.md，完整功能与必要验证完成后再确认提交范围。
