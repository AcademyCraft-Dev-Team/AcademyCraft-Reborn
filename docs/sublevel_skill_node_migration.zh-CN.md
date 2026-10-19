# 技能与节点迁移计划

日期：2026-10-19

## 1. 范围

- **直接技能**：HighSpeedJet、Magnetic、Vector 三个结构技能服务。
- **程序图节点**：三族各自的 extract / control / move / rotate / launch / release / staticize，以及 look_structure / structure_motion 查询节点。
- **兼容层**：旧技能树 ID 迁移、旧程序图迁移、旧 StructureRef 的只读兼容。

本计划不新增节点 ID，不改节点端口，不要求旧程序图重建。

### 1.1 技能树节点兼容

- `AeromanipSkillMigration` 与 `ElectromasterSkillMigration` 的旧 ID 别名继续保留；
- 旧 `SkillData` 在读取时一次性合并/重命名，写回新 ID；
- 不新增“SubLevel 专用”技能树节点，后端选择由运行时 capability/flag 决定；
- 旧客户端配置与服务端程序图在缺少新字段时走默认迁移。

## 2. 现状

| 对象 | 规模/耦合 |
| --- | --- |
| HighSpeedJetStructureService | 1924 行，捕获、喷嘴、traction、region、launch |
| MagneticStructureService | 1279 行，捕获、region、lease、program control |
| VectorStructureService | 1729 行，捕获、region、anchor、控制租约 |
| 三族 ProgramExecutionBridge | 节点到旧结构服务的映射 |
| 三族 ServerProgramRuntime | 旧 StructurePhysics / Region 调用 |
| ProgramStructureReference/Control/Result | 直接持有 StructureRef |

关键约束：只把 capture 换成 SubLevel，后续 control/move 会找不到刚体；必须同时迁移身份、租约和运动入口。

## 3. 目标模型

### 3.1 合成 StructureRef

- `backend = academy:sublevel`
- `id = SubLevelId.value()`
- `dimension = 宿主维度`
- `generation = SubLevel 激活代次`

这样现有程序值类型和序列化格式不变。

### 3.2 StructureHandle 适配

新增 `SubLevelStructureRefs` 与 `SubLevelStructureHandle`：

- ref ↔ SubLevelId 解析
- pose / setPose 委托 SubLevel
- geometryRevision 使用 view revision
- runtimeId 使用 `SubLevelActivationRegistry`
- 陈旧 generation 拒绝

### 3.3 控制租约

新增 `SubLevelControlRegistry`：

- controlId → SubLevelId + actor + runtimeToken
- tick 过期、登出/死亡/换维度释放
- move/rotate/launch 前校验独占租约
- release 只释放租约，不删除结构

## 4. 节点映射

| 节点 | SubLevel 入口 | 备注 |
| --- | --- | --- |
| structure/extract | SubLevelSkillSupport.capture | 权限谓词在 plot 分配前执行 |
| structure/control | SubLevelControlRegistry.acquire | 绑定 actor/controlId/activation |
| structure/move | translate 或租约控制 | 先短路径，后连续运动 |
| structure/rotate | rotate | 当前为绕局部中心 yaw |
| structure/launch | launch | 已有 5 格/秒冲量实测 |
| structure/release | registry.release | 保留结构 |
| structure/staticize | 新增 SubLevelRestoreService | 方块和 BlockEntity 写回宿主 |
| query/look_structure | SubLevelClientState / NativeStructureQueries | 客户端命中 + 服务端复核 |
| query/structure_motion | SubLevelPhysicsUniverse.motion | 线速度/角速度/质量现成 |

## 5. 分阶段

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| 0 | 冻结节点 ID/端口/schemaVersion，跑旧路径基线 | 现有 StructureProgramWorldSmoke 三族通过 |
| 1 | 身份适配层，只读解析，不改变行为 | ref/SubLevelId、runtimeId、陈旧 generation 单测 |
| 2 | look_structure / structure_motion | 只看不动的程序图三族可运行 |
| 3 | Aeromanip 垂直切片：extract→control→launch→release | Aeromanip 程序链路 + 客户端 launch 探针 |
| 4 | HighSpeedJet 直接技能 | 捕获、发射、释放、失败回滚 |
| 5 | Electromaster / Accelerator 程序节点 | 三族 StructureProgramWorldSmoke 全矩阵 |
| 6 | Magnetic / Vector 直接技能 | 实机技能 + 程序控制 + 释放 |
| 7 | staticize / 还原 | 宿主方块、BlockEntity、容器内容一致 |
| 8 | 清理与性能 | 64 / 256 / 24³ 实心体不回退 |

## 6. 兼容与回滚

- 旧 `StructureRef` 消费方按 backend 分支；旧 ref 继续走旧路径。
- 每族使用 feature flag，按族切换。
- 节点 schema 不轻易升版；只有新增配置字段才升版并补迁移。
- 旧图 `launch_block_structure` 重写逻辑保持不变。
- 每阶段独立提交，失败只回滚该阶段。

## 7. 风险

| 风险 | 处理 |
| --- | --- |
| 旧值类型不识别 SubLevel ref | Phase 1 只读适配，不先改 capture |
| 控制租约泄漏 | registry 过期 + actor 生命周期释放 |
| staticize 丢 BlockEntity | 复用已验证的 capture/rollback，再写回宿主 |
| 技能行为回退 | 按族 feature flag + 旧分支保留 |
| 节点图兼容 | 不改 ID/端口，schema 变更必须迁移 |

## 8. Phase 1 状态

已实现：

- `SubLevelStructureRefs`：合成 ref、backend 判定、generation 校验；
- `SubLevelActivationRegistry`：runtimeId、generation 前进、陈旧代次拒绝；
- `SubLevelStructureHandle`：lifecycle、pose、setPose、local world access、CAS 写回；
- `SubLevelStructureRefsTest`：ref 映射、外部 backend 拒绝、activation 代次。

已通过：

- 定向单测；
- `test -DisDev=true`；
- `build -DisDev=true`；
- `build -DisDev=false`。

尚未执行：`StructureProgramWorldSmoke` 旧路径基线；它属于 Phase 1 的下一步验收。

## 9. Phase 2 状态

已实现 `ServerProgramTargetResolver` 的 SubLevel 分支：

- `look_structure` 对活动 SubLevel 做服务端 view 射线，返回合成 ref + 当前 activation runtimeId；
- `structure_motion` 从 `SubLevelPhysicsUniverse.motion` 读取姿态、线速度、角速度、局部质心和 mass；
- 校验 generation、宿主维度、活动状态和最大距离。

已通过构建与全量单测；尚未跑三族 `StructureProgramWorldSmoke` 的 SubLevel 变体。

## 10. Phase 3 状态

已在 `ServerAeromanipProgramRuntime` 增加受 feature flag 保护的 SubLevel 分支：

- `academy.sublevelProgramNodes=true` 时，`extract` 使用 `SubLevelSkillSupport.capture`；
- `control` 为 SubLevel ref 生成 `ProgramStructureControl` 并登记；
- `launch` 调用 `SubLevelSkillSupport.launch`；
- `release` 只释放控制 token；
- `move`、`rotate`、`staticize` 已进入 Phase 4 并通过程序链路。

默认关闭，旧 Aeromanip 程序路径不变。已在客户端烟测中打开 flag 跑通最短链路：

```text
CLIENT_SUBLEVEL_PROGRAM_CHAIN extract=COMPLETED control=COMPLETED launch=COMPLETED release=COMPLETED
ref=StructureRef[id=..., backend=academy:sublevel, generation=1]
```

另将全填充几何合并阈值设为体积 >= 512，避免 2×2×2 小结构合并后影响承载探针；24³ 实心体仍为 boxes=1。

## 11. Phase 4 进展

Aeromanip SubLevel 程序链路已扩展并通过客户端烟测：

```text
extract=COMPLETED control=COMPLETED move=COMPLETED rotate=COMPLETED
launch=COMPLETED release=COMPLETED staticize=COMPLETED detail=staticized
CLIENT_SUBLEVEL_MOTION_PROBE delta=3.046
```

- `move` 使用 `SubLevelMotionController` 连续速度控制，20 tick 实测上移约 3.05 格；
- `rotate` 当前只支持 Y 轴 yaw；
- `staticize` 已通过：把 plot 内容写回宿主指定原点并移除 SubLevel；
- 直接 HighSpeedJet 已增加 SubLevel 实验路径：`academy.sublevelDirectSkills=true` 时经现有喷嘴 plan 捕获并施加一次方向冲量；独立 `sublevel_direct_skill.init.gradle` 实测 `CLIENT_SUBLEVEL_DIRECT_JET launch_id=...`；已接入持续速度推力（`SubLevelMotionController.thrust`）和 30 CP 资源扣除；`restoreWhenSettled=true` 时 handle 关闭会事务式 staticize 回源坐标，实测 `High-Speed Jet direct restore ... true staticized`；尚未接入撞击结算/粒子音效。

## 12. Phase 5：Accelerator / Electromaster 状态

`ServerAcceleratorProgramRuntime` 与 `ServerElectromasterProgramRuntime` 均已增加同样的 SubLevel 分支：

- extract/control/move/rotate/launch/release/staticize；
- 独立 direct smoke 实测：

```text
CLIENT_SUBLEVEL_ACCELERATOR_CHAIN extract=COMPLETED control=COMPLETED
move=COMPLETED rotate=COMPLETED launch=COMPLETED release=COMPLETED staticize=COMPLETED

CLIENT_SUBLEVEL_ELECTROMASTER_CHAIN extract=COMPLETED control=COMPLETED
move=COMPLETED rotate=COMPLETED launch=COMPLETED release=COMPLETED staticize=COMPLETED
```

Electromaster 的 extract 使用 4 格半径并受预算约束；staticize 测试目标上移，避免把地下捕获块写回原地面。

## 13. 下一步

1. 补三族 StructureProgramWorldSmoke 基线；
2. 补 HighSpeedJet 直接路径的撞击结算/粒子音效；
3. 清理旧 Region/Projection 兼容层的剩余调用。
