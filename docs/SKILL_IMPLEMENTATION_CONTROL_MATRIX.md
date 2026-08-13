# 技能实现与调控总表

本文档按当前 `Skills` 注册表和各技能源码整理，共 89 个已注册技能。表内按键是源码默认值；玩家在数据终端修改后的实时绑定以 `config/academy-client.json` 为准。

## 标记说明

- `↓`：按下；`↑`：松开；同一技能同时列出两项表示按住/蓄力或开始/结束。
- `移植`：1.21.1 合同已经迁入或合并；`保留`：26.2 原有技能；`P6`：已有实现，但仍在 Accelerator 高版本适配阶段。
- `IF=0` 通常表示现存技能尚未补齐开发消耗，而不代表已确认应免费学习。
- `维持 CP` 表示切换开启后的永久或周期占用。普通 `CP` 为施放成本或源码 Builder 基值。

## 全局控制

| 功能 | 默认按键 | 说明 |
| --- | --- | --- |
| 能力 HUD | `V↓` | 打开或关闭技能 HUD |
| HUD 上一技能 | `↑↓` | 技能轮盘向上 |
| HUD 下一技能 | `↓键↓` | 技能轮盘向下 |
| 数据终端 | `右 Alt↓` | 打开数据终端及设置应用 |

## Level0 公共脑开发

| 技能 | 状态 | 等级 / IF / CP | 实现与当前效果 | 默认按键 | 依赖 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `level0_passive_lv1` 频率提升 | 移植 | L1 / 0 / 0 | 被动：+4 最大生命、+2 攻击；配置默认另加 20 最大 CP、1 CP 恢复 | 无 | 无 | `Level0PassiveLv1` |
| `level0_passive_lv2` 简化运算 | 移植 | L2 / 10k / 0 | 被动维持急迫 I；配置默认 +5% 计算效率 | 无 | Lv1 | `Level0PassiveLv2` |
| `level0_passive_lv3` 基础体术 | 移植 | L3 / 30k / 0 | 被动：+2 护甲、+2 护甲韧性；另加 100 最大 CP、5 CP 恢复 | 无 | Lv2 | `Level0PassiveLv3` |
| `level0_passive_lv4` 工程科学 | 移植 | L4 / 60k / 0 | 被动：+0.02 移速、+0.5 攻速；另加 15% 计算效率 | 无 | Lv3 | `Level0PassiveLv4` |
| `level0_passive_lv5` 带宽拓展 | 移植 | L5 / 100k / 0 | 被动：+2 最大生命、+2 护甲；另加 500 最大 CP、25 CP 恢复、30% 计算效率 | 无 | Lv4 | `Level0PassiveLv5` |

## Aeromanip 气动操纵

| 技能 | 状态 | 等级 / IF / CP | 实现与当前效果 | 默认按键 | 依赖 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `airflow_jet` 气流喷射 | 完善 | L1 / 5k / 每 10 tick 消耗 10 | 按住时由服务端持续沿视线推进，松开结束；潜行时快速制动并重置坠落距离 | `R` 按住 | 无 | `AirflowJet` |
| `atmosphere_shield` 大气护盾 | 移植 | L3 / 30k / 维持 50 | 切换压缩空气薄膜，增强近战伤害和击退 | `Alt+N↑` | 气流喷射 | `AtmosphereShield` |
| `breathing_film` 呼吸薄膜 | 移植 | L2 / 10k / 维持 20 | 学会后自动维持空气，周期恢复水下氧气 | 无 | 大气护盾 | `BreathingFilm` |
| `atmosphere_blast_gun` 大气爆破枪 | 移植 | L4 / 60k / 40 | 8 格短程气爆，8 基础伤害并强力击飞敌人 | `Alt+鼠标左键↑` | 大气护盾 | `AtmosphereBlastGun` |
| `flight` 飞行 | 移植 | L5 / 100k / 维持 50 | 切换受服务端控制的创造式飞行租约 | `Alt+F↑` | 呼吸薄膜 | `Flight` |
| `vacuum_domain` 真空领域 | 移植 | L5 / 100k / 100 | 在 16 格目标处创建半径 12、持续 200 tick 的真空领域，周期造成最大生命 5% 伤害 | `Y↑` | 呼吸薄膜 | `VacuumDomain` |

## Accelerator 矢量操纵

| 技能 | 状态 | 等级 / IF / CP | 实现与当前效果 | 默认按键 | 依赖 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `vector_blast` 矢量冲击 | 移植 | L1 / 5k / 10 | 64 格服务端射线，10 基础技能伤害并沿射线击退 | `Alt+鼠标左键↑` | 无 | `VectorBlast` |
| `vector_accel` 矢量加速 | P6 | L1 / 5k / 10 | 最长蓄力 2 秒的定向冲刺，服务端速度上限 2.5，带残影预览 | `C↓ / C↑` | 无 | `VectorAccel` |
| `flow_control` 气流操控 | 保留 | L1 / 0 / 实际 55 | 前方锥形推开目标；潜行时改为较短距离拉近，范围和力度随技能等级增长 | `V↓` | 无 | `FlowControl` |
| `directed_shock` 集束冲击 | 保留 | L1 / 0 / 50 | 蓄力锥形冲击，当前 8 伤害、3–4 格范围并强击退 | `Shift+R↓ / Shift+R↑` | 无 | `DirectedShock` |
| `kinetic_energy_applied` 动能加持 | P6 | L2 / 10k / 10 | 切换后加速玩家发射的投射物，并附加 2–5 点额外伤害 | `K↓` | 矢量加速 | `KineticEnergyApplied` |
| `dir_strike` 定向打击 | P6 | L2 / 10k / 20 | 向视线方向抛射方块并对目标造成当前 6 点伤害 | `Alt+R↓` | 矢量冲击 | `DirStrike` |
| `kinetic_superposition` 动能叠加 | 保留 | L2 / 0 / 55 | 切换急迫效果；近战追加 4–6 魔法伤害，重型武器额外提高 20% | `Alt+P↓` | 动能加持 | `KineticSuperposition` |
| `vector_reduction` 减速力场 | 保留 | L2 / 0 / 维持 75 | 半径 6–10 的减速场，降低实体速度 50–80%，投射物速度降至 10% 并施加虚弱/挖掘疲劳 | `N↓` | 矢量加速 | `VectorReduction` |
| `hyper_accelerate` 矢量跳跃 | 保留 | L3 / 0 / 50 | 蓄力高速发射自身，最高速度 3；接触目标时造成轻伤和位移 | `Shift+C↓ / Shift+C↑` | 矢量加速 | `HyperAccelerate` |
| `vector_reflection` 矢量反射 | P6 | L3 / 30k / 维持 50 | 拦截并反射部分来袭伤害；吸收上限为最大 CP 的 5–8%，完全反射倍率当前为 1.5 | `R↓` | 动能加持 | `VectorReflection` |
| `storm_wing` 风暴之翼 | P6 | L4 / 60k / 20 | 切换同步风暴翼状态，并通过服务端控制包提供空中移动和坠落控制 | `B↓` | 矢量反射 | `StormWing` |
| `bloodflow_reverse` 血流逆流 | P6 | L5 / 100k / 100 | 短程目标攻击；叠加减速、虚弱和挖掘疲劳，伤害为最大生命 20% 起并随层数增加 | `Alt+Shift+R↓` | 矢量反射 | `BloodflowReverse` |
| `plasma_generation` 等离子体 | P6 | L5 / 100k / 500 | 3–20 秒蓄力的高成本等离子攻击，基础伤害 50 并按蓄力增长 | `Alt+Ctrl+C↓ / Alt+Ctrl+C↑` | 矢量反射、风暴之翼 | `PlasmaGeneration` |

尚未注册、因此未列入现存表：`reflection_filter`、`black_wing`、`white_wing`、`crossing_the_abyss`、`platinum_wing`。

## Electromaster 电气操纵

| 技能 | 状态 | 等级 / IF / CP | 实现与当前效果 | 默认按键 | 依赖 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `arc_generate` 电弧生成 | 移植 | L1 / 5k / 10 | 十格短射线，当前 4 基础技能伤害并使用电弧效果 | `Alt+G↓` | 无 | `ArcGenerate` |
| `pulse_charge` 电流回充 | 保留 | L1 / 0 / 15 | 在视线位置生成电弧并触发目标方块的红石邻居更新 | `Ctrl+G↓` | 无 | `PulseCharge` |
| `electrical_contact` 带电接触 | 保留 | L1 / 0 / 维持 15 | 切换后每 40 tick 对两格内目标或攻击者造成 2 点闪电伤害 | `H↓` | 无 | `ElectricalContact` |
| `mine_detect` 矿物探测 | 移植 | L1 / 5k / 维持 20 | 客户端分批扫描已加载区块并显示 64 格内矿物轮廓 | `Alt+M↑` | 磁力操纵 | `MineDetect` |
| `bioelectric_surge` 生物电涌 | 保留 | L2 / 0 / 维持 30；技能3级为15 | 开启时维持力量、再生、速度、急迫和饥饿；关闭时按持续时间施加多种负面效果 | `J↓` | 电弧生成 | `BioelectricSurge` |
| `magnet_moment_charge` 磁矩爆发 | 保留 | L2 / 0 / 40 | 生成前进的磁力球，并持续牵引附近目标 | `Alt+Shift+G↓` | 电弧生成 | `MagnetMomentCharge` |
| `lightning_nova` 闪电新星 | 保留 | L2 / 0 / 60 | 持续 200 tick 向外扩张至半径 16，脉冲造成 4 点闪电伤害 | `Ctrl+N↓` | 带电接触 | `LightningNova` |
| `magnet_manipulation` 磁力操纵 | 移植/合并 | L3 / 30k / 30 | Alt+X 拉动自身或目标；按住 R 以 10 CP/s 朝 64 格服务端目标飞行 | `Alt+X↓ / Alt+Shift+X↓ / R↓ / R↑` | 电弧生成 | `MagnetManipulation` |
| `current_recharge` 电流充能 | 移植 | L3 / 30k / 周期 5/tick | 按住 H 为五格内方块、生物或装备的 NeoForge 能量存储充能 | `H↓ / H↑` | 磁力操纵 | `CurrentRecharge` |
| `current_symbiosis` 电流共生 | 移植 | L3 / 30k / 维持 30 | 切换后周期为手持和穿戴装备充能 | `Y↑` | 电流充能 | `CurrentSymbiosis` |
| `magnetic_weapon` 磁悬武装 | 保留 | L3 / 0 / 维持 40 | 切换悬浮武器，约每 15 tick 自动攻击四格内目标，伤害取武器伤害的 60% | `Alt+Shift+M↓` | 磁力操纵、磁矩爆发 | `MagneticWeapon` |
| `thunder_lance` 雷击之枪 | 移植/合并 | L3 / 0 / 60 | 保留蓄力雷枪，并加入 Alt+右键松开的 32 格快速闪电枪模式 | `Ctrl+T↓ / Alt+鼠标右键↑` | 电弧生成 | `ThunderLance` |
| `bioelectric_operation` 生物电操纵 | 移植 | L4 / 60k / 维持 40 | 通过瞬态属性强化移动、攻击、挖掘、跳跃、台阶和坠落控制 | `Alt+N↑` | 电流共生 | `BioelectricOperation` |
| `electromagnetic_shield` 电磁护盾 | 移植 | L4 / 60k / 维持 40 | 吸收伤害，上限为 `100 × 能力强度`；每 40 tick 花 20 CP 冷却 10 点负荷 | `Alt+K↑` | 磁力操纵 | `ElectromagneticShield` |
| `iron_sand_arsenal` 铁砂之剑 | 保留 | L4 / 0 / 维持 50 | 切换铁砂武装并循环剑、鞭、锤形态；当前伤害 15/8/25 | `Alt+Shift+I↓ / Alt+Shift+G↓` | 磁悬武装 | `IronSandArsenal` |
| `lightning_storm` 闪电风暴 | 保留 | L4 / 0 / 80 | 在目标区域生成 21 次雷击，半径 8，单次当前 8 点伤害 | `Alt+Shift+L↓` | 闪电新星 | `LightningStorm` |
| `railgun` 电磁炮 | 移植/增强 | L4 / 60k / 200 | 蓄力消耗硬币、铁锭、铁块或已抛硬币，发射 150 基础伤害的电磁弹 | `X↓` | 雷击之枪、磁力操纵 | `Railgun` |
| `ball_lightning` 球状闪电 | 保留/高风险 | L5 / 0 / 80 | 生成自动索敌球状闪电；命中五格范围时直接将生命乘 0.7 后再造成 10 点伤害 | `Y↓` | 当前 Builder 无依赖 | `BallLightning` |
| `thunderclap` 终极落雷 | 移植 | L5 / 100k / 100 | 64 格服务端选点，半径 5 内造成 `20% 最大生命 × 玩家倍率` 的普通闪电伤害 | `Alt+Shift+Y↓` | 闪电风暴 | `Thunderclap` |

## Meltdowner 原子崩坏

| 技能 | 状态 | 等级 / IF / CP | 实现与当前效果 | 默认按键 | 依赖 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `single_high_speed_electron_beam` 单发高速电子束 | 移植 | L1 / 5k / 20 | 40 tick 延迟电子束，造成 `20 + 1% 目标最大生命` 的缩放伤害 | `Alt+鼠标左键↓` | 无 | `SingleHighSpeedElectronBeam` |
| `radiation_intensify` 辐射强化 | 移植 | L1 / 5k / — | 被动：命中施加 200 tick 辐射标记和负面效果，后续兼容射束伤害 ×1.5 | 无 | 单发高速电子束 | `RadiationIntensify` |
| `trace_ring` 轨迹环绕 | 保留 | L1 / 0 / 维持 60 | 生成内外两圈共 12 个光球，持续环绕并对接触目标造成 2 点伤害 | `C↓` | 无 | `TraceRing` |
| `spreading_blast` 霰流射束 | 移植/合并 | L2 / 10k / 40 | 蓄力 20–80 tick 后释放 7–8 条延迟射束，应用生命比例伤害、辐射和方块权限 | `Alt+鼠标右键↓ / Alt+鼠标右键↑` | 单发高速电子束 | `SpreadingBlast` |
| `electron_barrier` 电子屏障 | 保留 | L2 / 0 / 维持 120 | 前方半径 3 的持续屏障，造成 4 点伤害并击退 | `Alt+B↓` | 无 | `ElectronBarrier` |
| `mining_beam` 采矿光束 | 移植 | L2 / 10k / 周期 5 | 按住维持最长 48 格的采矿射束；周期破坏有权限方块并造成 12 点缩放伤害 | `M↓ / M↑` | 单发高速电子束 | `MiningBeam` |
| `homing_blast` 归一射束 | 保留 | L3 / 0 / 200 | 生成当前 24 个追踪光球，在八格索敌并造成每个 4 点伤害 | `Alt+Shift+H↓` | 霰流射束 | `HomingBlast` |
| `cloudroom` 粒子云室 | 保留 | L3 / 0 / 维持 30 | 在半径 16 内追踪实体并生成短寿命烟雾轨迹，主要为侦测/视觉效果 | `Alt+U↓` | 无 | `Cloudroom` |
| `beta_particle_stream` β粒子流 | 保留 | L3 / 0 / 40 | 蓄力后向 16 格目标方向发射多束粒子流，单束当前 6 点伤害 | `Alt+Shift+F↓ / Alt+Shift+F↑` | 无 | `BetaParticleStream` |
| `light_shield` 光盾 | 移植 | L3 / 30k / 周期 5 | 按住获得抗性提升 II，并每四 tick 对半径 3.5 的敌人造成伤害和击退 | `H↓ / H↑` | 单发高速电子束 | `LightShield` |
| `hell_flare` 地狱烈焰 | 保留 | L4 / 0 / 600 | 锁定 32 格目标并进入三阶段持续射线；每 30 tick 伤害依次为 2/6/12 | `Alt+Shift+N↓` | 当前 Builder 无依赖 | `HellFlare` |
| `particle_wave_cannon` 粒机波形高速炮 | 移植 | L4 / 60k / 周期 10 | 蓄力 25 tick 后维持最长 85 格宽射束，周期破坏方块并造成 `40 + 1% 最大生命` 伤害 | `C↓ / C↑` | 霰流射束 | `ParticleWaveCannon` |
| `jet_strike` 突击喷射 | 移植/合并 | L4 / 60k / 20 | 服务端选择八格内安全落点并突进，对半径 3.25 目标造成伤害 | `R↓` | 光盾 | `JetStrike` |
| `chain_fusion` 链式聚变 | 保留 | L5 / 0 / 150 | 发射聚变球；初始 15 伤害，随后在五格范围最多链式跳转五次、每次 10 伤害 | `Alt+Shift+U↓` | 当前 Builder 无依赖 | `ChainFusion` |
| `disintegrate` 解离射线 | 保留/高风险 | L5 / 0 / 200 | 30 格窄射线破坏高硬度方块；当前伤害变量取枚举中最后目标生命的 99% 并应用到全部目标 | `Alt+Shift+K↓` | 当前 Builder 无依赖 | `Disintegrate` |
| `auto_cruise_beam_cannon` 自动巡航电子炮 | 移植 | L5 / 100k / 维持 50，单发 10 | 扫描 16 格敌人并自动发射带 40 tick 延迟的电子束 | `Y↑` | 霰流射束 | `AutoCruiseBeamCannon` |

## Teleport 空间移动

| 技能 | 状态 | 等级 / IF / CP | 实现与当前效果 | 默认按键 | 依赖 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `threatening_teleport` 威胁传送 | 移植 | L1 / 5k / 10 | 将主手物品传入 24 格目标体内，造成 `4 + 武器攻击加成` 伤害 | `Alt+鼠标左键↓` | 无 | `ThreateningTeleport` |
| `space_folding_theorem` 空间折叠理论 | 移植 | L1 / 5k / — | 被动：兼容的空间伤害 ×1.25 | 无 | 威胁传送 | `SpaceFoldingTheorem` |
| `matter_warp` 危险传送 | 保留 | L1 / 0 / 20 | 在视线落点生成空间攻击和烟雾，对附近目标造成等级缩放魔法伤害 | `Alt+V↓` | 无 | `MatterWarp` |
| `clip_through` 隧穿移动 | 保留/待审计 | L1 / 0 / 150 | 沿客户端提交方向穿越至技能距离末端，碰撞失败时回退半格 | `Alt+F↓` | 无 | `ClipThrough` |
| `self_teleport` 自身传送 | 移植 | L2 / 10k / 释放时计算 | 按住并滚轮预览 20 格内安全位置，松开后服务端校验传送 | `R↓ / R↑` | 威胁传送 | `SelfTeleport` |
| `cut_through` 穿透传送 | 移植/合并 | L2 / 10k / 20 | 按住预览最长 36 格、可穿过中间方块的安全落点 | `Alt+R↓ / Alt+R↑` | 自身传送 | `CutThrough` |
| `spatial_synergy` 空间协同 | 保留 | L2 / 0 / 维持 20 | 玩家传送时同步传送两格内其他玩家，并按额外人数增加 CP 消耗 | `X↓` | 自身传送 | `SpatialSynergy` |
| `visual_teleport` 目视传送 | 保留/高风险 | L2 / 0 / 0 | 直接使用客户端提交的视线目标坐标传送，当前缺少完整服务端距离/碰撞重算 | `Shift+X↓` | 无 | `VisualTeleport` |
| `disarm` 缴械传送 | 保留 | L2 / 0 / 实际 50 | 将视线目标的手持物品弹出并造成 1 点攻击伤害 | `Alt+D↓` | 危险传送 | `Disarm` |
| `flesh_ripping` 肉体撕裂 | 移植 | L3 / 30k / 30 | 锁定 14 格目标，松开造成 `12 + 5% 最大生命` 的空间伤害 | `Alt+鼠标右键↓ / Alt+鼠标右键↑` | 穿透传送 | `FleshRipping` |
| `location_teleport` 位置传送 | 移植 | L3 / 30k / 30 | 保存最多 32 个命名位置并通过界面执行跨维度自身传送 | `L↓` | 穿透传送 | `LocationTeleport` |
| `coordinate_teleport` 坐标传送 | 保留/待审计 | L3 / 0 / 0 | 保存客户端提交坐标；请求后计算 200 tick，再传送到最后一个保存点 | `Alt+Shift+T↓ / Alt+Shift+Y↓` | 空间协同、穿透传送 | `CoordinateTeleport` |
| `shackle` 禁锢传送 | 保留 | L3 / 0 / 80 | 定身目标并施加 160 tick 高级减速、挖掘疲劳和虚弱，同时造成 3 点伤害 | `Alt+Shift+S↓` | 危险传送 | `Shackle` |
| `quick_location_teleport` 快速位置传送 | 移植 | L4 / 60k / 30 | 将自身或服务端选中的目标快速送往位置传送当前标记 | `C↓` | 位置传送 | `QuickLocationTeleport` |
| `area_teleport_select` 区域传送·选择 | 移植 | L4 / 60k / — | 服务端射线选择并同步最大 32³ 的源区域角点 | `U↓` | 位置传送 | `AreaTeleportSelect` |
| `area_teleport_setup` 区域传送·设置 | 移植 | L4 / 60k / — | 设置区域传送目标锚点 | `Alt+U↓` | 区域选择 | `AreaTeleportSetup` |
| `area_teleport_start` 区域传送·启动 | 移植 | L4 / 60k / 50 | 使用缓冲、保护事件、区块租约和回滚移动区域方块、方块实体及实体 | `Shift+U↓` | 区域设置 | `AreaTeleportStart` |
| `flash_back` 高速闪现 | 保留 | L4 / 0 / — | 切换后在受实体攻击时向远离攻击者方向瞬移约三格 | `Alt+Shift+Q↓` | 自身传送、隧穿移动 | `FlashBack` |
| `phantom_falling` 坠落幻痛 | 保留 | L4 / 0 / 60 | 将十格内目标上移八格后强制下坠，造成 8 点坠落伤害并施加减速/虚弱 | `Alt+Shift+F↓` | 穿透传送 | `PhantomFalling` |
| `flashing` 连续闪现 | 移植 | L5 / 100k / 维持 30，单次 10 | 开启后沿移动方向执行八格安全闪现 | `H↓` | 位置传送 | `Flashing` |
| `defensive_teleport` 防御传送 | 移植 | L5 / 100k / 维持 30，单目标 10 | 扫描附近敌对生物和来袭投射物并将其传送至安全位置 | `Alt+G↑` | 快速位置传送 | `DefensiveTeleport` |
| `spacial_replace` 空间置换 | 保留/高风险 | L5 / 0 / 300 | 选择两个角点并把选区方块复制到目标位置；当前两个角点绑定完全相同 | `Alt+Shift+Ctrl+任意键↓` 两条 / `Alt+Shift+Ctrl+P↓` | 坐标传送 | `SpacialReplace` |
| `spacial_excision` 空间切除 | 保留/高风险 | L5 / 0 / 0 | 持续扩大球形范围，周期造成 20 魔法伤害并遍历/切除范围方块 | `Alt+Shift+Ctrl+O↓` | 坐标传送 | `SpacialExcision` |

## Darkmatter 未元物质

| 技能 | 状态 | 等级 / IF / CP | 实现与当前效果 | 默认按键 | 依赖 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `darkmatter_shaping` 未元物质塑型 | 移植 | L1 / 5k / 50 | 空手生成未元物质；持物时修复并切换未元物质强化 | `U↑` | 无 | `DarkmatterShaping` |
| `darkmatter_disassemble` 未元物质分解 | 移植 | L1 / 5k / 10 | 服务端 32 格射线分解目标实体或通过保护检查的方块 | `Alt+鼠标左键↑` | 未元物质塑型 | `DarkmatterDisassemble` |
| `darkmatter_cut` 未元物质切割 | 移植 | L2 / 10k / 20 | 前方八格锥形斩击，并生成同步斩击实体效果 | `R↑` | 未元物质分解 | `DarkmatterCut` |
| `darkmatter_radiation` 未元物质照射 | 移植 | L3 / 30k / 周期 10 | 按住照射前方半球 32 格内的敌对目标 | `C↓ / C↑` | 未元物质切割 | `DarkmatterRadiation` |
| `darkmatter_repair` 未元物质修补 | 移植 | L4 / 60k / 治疗时 10 | 切换持续自我修复，仅实际恢复生命时消耗 CP | `Alt+U↑` | 未元物质塑型 | `DarkmatterRepair` |
| `darkmatter_creation` 未元物质创生 | 移植 | L4 / 60k / 80 + 每虫维持 20 | 最多召唤八只所属独角仙；看向所属独角仙再次施放可解散 | `G↑` | 未元物质修补 | `DarkmatterCreation` |
| `darkmatter_six_wings` 未元物质六翼 | 移植 | L5 / 100k / 维持 70 | 切换六翼飞行，并强化未元物质切割与分解 | `Alt+R↑` | 未元物质塑型 | `DarkmatterSixWings` |

## 默认按键冲突与调控优先级

| 优先级 | 冲突/问题 | 影响 | 建议 |
| --- | --- | --- | --- |
| P0 | 全局能力 HUD `V↓` 与 Accelerator `flow_control` 的 `V↓` 完全相同 | Accelerator 玩家按 V 会同时切 HUD 并施放技能 | 立即为 `flow_control` 更换默认键，或让 HUD 激活键在技能输入前消费事件 |
| P0 | Electromaster：`electrical_contact` 与 `current_recharge` 都使用 `H↓` | 按 H 会切换带电接触并开始充能；松开才停止充能 | 至少修改其中一个默认绑定 |
| P0 | Electromaster：`magnet_moment_charge` 与 `iron_sand_arsenal` 形态切换都是 `Alt+Shift+G↓` | 两个技能可能同时发送动作 | 为铁砂形态切换指定独立按键 |
| P0 | Meltdowner：`trace_ring` 和 `particle_wave_cannon` 都以 `C↓` 启动 | 蓄力粒机炮时会同时切换轨迹环绕 | 修改其中一个默认键 |
| P0 | Teleport：`spacial_replace` 的角点 1/2 都是 `Alt+Shift+Ctrl+任意键↓` | 同一次输入会同时设置两个角点，默认选择不可用 | 改为两个明确且不同的键，并禁止 `anyKey` 用作可编辑绑定默认值 |
| P1 | Electromaster：`ball_lightning` 为 `Y↓`，`current_symbiosis` 为 `Y↑` | 一次完整按键会施放球状闪电并切换电流共生 | 为其中一个技能换键 |
| P1 | 多个现存技能 `IF=0` 或依赖为空 | 开发树平衡和技能顺序不统一 | 先集中补齐当前保留技能的等级、IF、依赖和图标节点 |
| P1 | `BallLightning` 直接改生命值；`Disintegrate` 复用最后目标伤害；部分现存 Teleport 接受客户端坐标 | 难以统一倍率，也存在服务端权威与兼容风险 | 在统一数值调整前先重构为 `SkillDamageSource`、逐目标计算和服务端射线/位置验证 |

## 建议的统一修改入口

目前技能参数仍分散在各实现类中：

- 等级、IF、基础 CP、维持 CP、依赖：各技能构造器的 `Skill.Builder`。
- 默认键：各技能 `initClient()` 中的 `InputSystem.combo(...)`；玩家覆盖值由统一 `InputSystem` 配置保存。
- 范围、伤害、持续时间、扫描间隔：各类的常量或 `getDamage/getRange` 等方法。
- 少量全局参数：服务端 `AbilityConfig`，目前仅覆盖总体倍率、脑开发、金属列表及部分 Railgun 参数。

后续建议增加按技能 ID 索引的 `SkillBalanceConfig`，至少统一 `enabled`、`damage`、`range`、`duration`、`cpCost`、`maintenanceCost`、`cooldown/iterationTicks`，并让源码常量仅作为默认值。这样可在不重新构建 JAR 的情况下完成服务器统一调控。
