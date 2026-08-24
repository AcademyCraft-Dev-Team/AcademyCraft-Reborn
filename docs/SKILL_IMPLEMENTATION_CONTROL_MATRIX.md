# 技能实现与调控总表

本文档由当前 `Skills` 注册表、全技能效果总表和前置关系清单汇总，共 92 个已注册技能。运行 `powershell -NoProfile -ExecutionPolicy Bypass -File tools/docs/sync_skill_control_matrix.ps1` 可在基础文档变更后重新生成本表。按键是源码默认值或“见源码”提示；玩家实时覆盖值以 `config/academy-client.json` 为准。

## 标记说明

- `↓`：按下；`↑`：松开；同一技能同时列出两项表示按住/蓄力或开始/结束。
- `IF` 为当前源码 `energyCost`；消耗栏来自全技能效果总表。
- “见源码（可配置）”表示尚未在本表固化默认组合，不表示技能没有按键。

## 全局控制

| 功能 | 默认按键 | 说明 |
| --- | --- | --- |
| 能力 HUD | `V↓` | 打开或关闭技能 HUD |
| HUD 上一技能 | `↑↓` | 技能轮盘向上 |
| HUD 下一技能 | `↓键↓` | 技能轮盘向下 |
| 数据终端 | `右 Alt↓` | 打开数据终端及设置应用 |

## Aeromanip 气动操纵

| 技能 | 状态 | 等级 / IF / 消耗 | 实现与当前效果 | 默认按键 | 直接前置 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `airflow_jet` 气流喷射 | 现行 | L1 / 5k / 每 10 tick 10 | 按住沿视线推进；初速 1.4、速度倍率 1.5，水下速度再 ×0.4；潜行制动。 | `R` 按住 | 无 | `AirflowJet` |
| `air_cushion` 气垫缓冲 | 现行 | L1 / 5k / 每次触发 10 | 自动把坠落伤害降低 70%/85%/100%；熟练度可把 3 格友军保护扩至 5 格。 | 见源码（可配置） | `academy:airflow_jet` | `AirCushion` |
| `flow_sense` 流场感知 | 现行 | L1 / 5k / 0 | 每 10 tick 感知移动实体/投射物并显示方向与速度；技能级范围 12/16/20，熟练度后 +4，最高频率 5 tick。 | 见源码（可配置） | `academy:airflow_jet` | `FlowSense` |
| `atmosphere_shield` 大气护盾 | 现行 | L3 / 30k / 维持 30；低伤免疫 10；一般防御 `min(30,4+2×减免伤害)` | 维持时攻击击退 `+0.5A`、真实抗性 +6，并停止附近投射物；普通伤害减免 20%/28%/35%，高熟练度最高 50%。 | `Alt+N↑` | `academy:breathing_film` | `AtmosphereShield` |
| `breathing_film` 呼吸薄膜 | 现行 | L2 / 10k / 维持 20；主动 15 | 每 10 tick 恢复自身氧气；主动施放为 16 格内友军补满氧气。 | 无 | `academy:flow_sense` | `BreathingFilm` |
| `pneumatic_grasp` 气动牵引 | 现行 | L2 / 10k / 每 10 tick 10；满熟练度抬升落地生物另每 5 tick 5 | 牵引/推动物品、经验球、投射物和敌对生物；基础范围 16/20/24，高熟练度 +8，控制距离 2–16。 | 见源码（可配置） | `academy:flow_sense` | `PneumaticGrasp` |
| `tailwind_field` 顺风场 | 现行 | L2 / 10k / 维持 20 | 维持半径 4、长度 14 的风道；强度 0.15/0.20/0.25，友军与顺向投射物加速、逆向敌人减速。 | 见源码（可配置） | `academy:air_cushion` | `TailwindField` |
| `laminar_cutter` 层流切割 | 现行 | L3 / 30k / 20 | 发射长度 24/28/32 的层流刃，伤害 `4AD`；可切除获准软方块。 | 见源码（可配置） | `academy:pneumatic_grasp` | `LaminarCutter` |
| `vortex_pull` 涡流牵引 | 现行 | L3 / 30k / 30 | 在 16 格内生成半径 9/10/11、持续 80 tick 的上升涡流；可捕获并重新发射投射物。 | 见源码（可配置） | `academy:pneumatic_grasp` | `VortexPull` |
| `atmosphere_blast_gun` 大气爆枪 | 现行 | L4 / 60k / 宽域 30；聚焦 40 | 宽域：长度 8、半宽 1、`10AD`；聚焦：长度 20、半宽 0.5、`14AD`；击退 1.8、上抛至少 0.45。 | `Alt+鼠标左键↑` | `academy:atmosphere_shield` | `AtmosphereBlastGun` |
| `wind_corridor` 定向风道 | 现行 | L4 / 60k / 40；重定向 20 | 生成半径 2.5、长度 24、持续 160 tick 的运输风道；高熟练度为长度 30、持续 220，可半价重定向旧风道。 | 见源码（可配置） | `academy:tailwind_field` | `WindCorridor` |
| `pressure_lock` 压力禁锢 | 现行 | L4 / 60k / 每次锁定 40 | 锁定 18 格视线目标并压制位移，持续 200 tick；高熟练度 240 tick。 | 见源码（可配置） | `academy:vortex_pull` | `PressureLock` |
| `flight` 飞行 | 现行 | L5 / 100k / 维持 50；加速每 20 tick 10 | 获得服务端控制飞行；普通速度上限 0.7，加速上限 1.2。 | `Alt+F↑` | `academy:wind_corridor` | `Flight` |
| `vacuum_domain` 真空领域 | 现行 | L5 / 100k / 50+20%最大CP | 16 格内创建半径 12 的真空区，每 10 tick 造成 `max(1,5%Hmax)AD` 并清空氧气；领域持续到再次施放取消。 | `Y↑` | `academy:atmospheric_dominion` | `VacuumDomain` |
| `atmospheric_dominion` 大气支配 | 现行 | L5 / 100k / 100 | 自身中心半径 22、持续 400 tick：友军速度 II、免坠落并补氧；敌人减速，投射物受风向影响。高熟练度为半径 26、480 tick。 | 见源码（可配置） | `academy:atmosphere_blast_gun`<br>`academy:vortex_pull` | `AtmosphericDominion` |

## Accelerator 矢量操纵

| 技能 | 状态 | 等级 / IF / 消耗 | 实现与当前效果 | 默认按键 | 直接前置 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `vector_blast` 矢量冲击 | 现行 | L1 / 5k / 射击 10；拉/推每 10 tick 10 | 64 格、半径 1 射线造成 `10AD`；也可在 32 格内持续拉/推。跨越深渊启用时追加半径 8 的真实范围伤害。 | `Alt+鼠标左键↑` | `academy:vector_accel` | `VectorBlast` |
| `vector_accel` 矢量加速 | 现行 | L1 / 5k / 10 | 最长蓄力 40 tick，速度 `7×sin(0.4+0.6C)`；满熟练度冲撞追加 `6AD`。 | `C↓ / C↑` | 无 | `VectorAccel` |
| `vector_deviation` 矢量偏移 | 现行 | L3 / 10k / 维持 40；投射物拦截及成功的伤害折射按处理量动态计费（满熟练时低于 `1%最大CP` 的伤害无消耗） | 维持减速场并折射投射物；未满熟练时每次可折射来伤有 50% 概率尝试完整折射，未折射的实际生命损失按 0/1000/2000 熟练减免 50%/70%/90%并播放偏移反馈；满熟练恢复完整实例保护及稳定折射。 | `N↓` | `academy:kinetic_energy_applied` | `VectorDeviation` |
| `kinetic_energy_applied` 动能加持 | 现行 | L2 / 10k / 维持 15；每次冲击 `10L` | 维持移动/攻击强化；1–3 级冲击伤害 `(4+L²)AD`、半径 `L²+2`，并可破坏方块；其他攻击先 ×2，再追加 `4AD`。 | `K↓` | `academy:vector_accel` | `KineticEnergyApplied` |
| `dir_strike` 导向踏击 | 现行 | L2 / 10k / 15 | 半径 12 地面环形冲击造成 `12AD`；空中俯冲半径再 +6。 | `Alt+R↓` | `academy:vector_blast` | `DirStrike` |
| `vector_reflection` 矢量反射 | 现行 | L4 / 30k / 维持基础 40；过滤模式为 40/60/80，名单每项 +5；另按来伤动态扣除（满熟练时低于 `1%最大CP` 的伤害无消耗） | 反射可处理的来伤并把投射物速度反向 ×1.2；每点处理伤害消耗 CP 倍率为 2/1/0.5/0.5（熟练度档位）。 | `R↓` | `academy:vector_deviation` | `VectorReflection` |
| `reflection_filter` 过滤网 | 现行 | L4 / 60k / 自身 0；会改变矢量反射维持占用 | 配置矢量反射的全反射/正面过滤/中性过滤模式，以及效果白名单和黑名单（合计最多 256 项）。 | 见源码（可配置） | `academy:vector_reflection` | `ReflectionFilter` |
| `storm_wing` 风暴之翼 | 现行 | L4 / 60k / 维持 40；每 20 tick 维持费 10 | 维持矢量飞行、悬停和高速推进。 | `B↓` | `academy:vector_reflection` | `StormWing` |
| `bloodflow_reverse` 血流逆流 | 现行 | L5 / 100k / 100或最大CP的20%取高 | 近距离造成 `Hmax`，叠加缓慢/虚弱/挖掘疲劳（最高效果等级 V），持续 200 tick。 | `Alt+Shift+R↓` | `academy:vector_reflection` | `BloodflowReverse` |
| `black_wing` 黑翼 | 现行 | L5 / 100k / 维持 60；每 20 tick 20；每次扇击 20 | 双翼矢量飞行；32 格扇击造成 `(基础攻击+1%Htmax+10)D` 真实/复合伤害。 | 见源码（可配置） | `academy:storm_wing` | `BlackWing` |
| `white_wing` 白翼 | 现行 | L5 / 100k / 维持 80；每 20 tick 40；每次扇击 20 | 保留黑翼飞行与 32 格真实生命扇击。 | 见源码（可配置） | `academy:black_wing` | `WhiteWing` |
| `platinum_wing` 白金翼 | 现行 | L5 / 100k / 维持 160；每 20 tick 80；每次扇击 20 | 保留扇击；潜行攻击可处决 128 格非玩家目标：通常 `(2Htmax+1000)D`，高熟练度对 Boss 改为 `15%Htmax×D`。 | 见源码（可配置） | `academy:white_wing` | `PlatinumWing` |
| `crossing_the_abyss` 跨越深渊 | 现行 | L5 / 100k / 维持 100 | 压低目标真实生命并锁定治疗上限；目标连续 3 次从致死伤害中存活后强制终结，并强化矢量冲击。 | 见源码（可配置） | `academy:white_wing` | `CrossingTheAbyss` |
| `plasma_generation` 等离子体 | 现行 | L5 / 100k / 每完成 1 秒蓄力 40 | 每 40 tick 增长 1 阶，最多 6 阶；每阶伤害 `50AD`、伤害半径 5阶数、爆破半径2.5阶数，最大选点 128。 | `Alt+Ctrl+C↓ / Alt+Ctrl+C↑` | `academy:storm_wing` | `PlasmaGeneration` |

## Electromaster 电气操纵

| 技能 | 状态 | 等级 / IF / 消耗 | 实现与当前效果 | 默认按键 | 直接前置 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `arc_generate` 电弧生成 | 现行 | L1 / 5k / 10 | 10 格短射线造成 `4AD`，路径半径 0.125。 | `Alt+G↓` | 无 | `ArcGenerate` |
| `electrical_contact` 接触电击 | 现行 | L1 / 0 / 维持 10 | 每 20 tick 电击半径 2 内敌人，并反击近战攻击者；每次 `2AD`。 | `H↓` | `academy:arc_generate` | `ElectricalContact` |
| `current_recharge` 电流充能 | 现行 | L3 / 30k / 每 20 tick 30 | 按住为 5 格内方块、生物与装备充能；仅有效充能时收费。 | `H↓ / H↑` | `academy:magnet_manipulation` | `CurrentRecharge` |
| `lightning_nova` 闪电新星 | 现行 | L2 / 10k / 15 | 扩张电环持续 200 tick、最大半径 16，波前每次造成 `4AD`。 | `Ctrl+N↓` | `academy:thunder_lance` | `LightningNova` |
| `magnet_manipulation` 磁力操纵 | 现行 | L3 / 30k / 移动期间每 20 tick 10 | 48 格内把自身拉向含铁目标（0.9/tick），或把目标拉到面前 2.5 格（1.15/tick）。 | `Alt+X↓ / Alt+Shift+X↓ / R↓ / R↑` | `academy:arc_generate` | `MagnetManipulation` |
| `mine_detect` 矿物探测 | 现行 | L3 / 5k / 维持 30 | 显示已加载区块内半径 64 的矿物；每 tick 最多扫描 32768 点，每 100 tick 重扫。 | `Alt+M↑` | `academy:magnet_manipulation` | `MineDetect` |
| `magnetic_weapon` 磁悬武装 | 现行 | L3 / 30k / 维持 30 | 悬浮武器每 10 tick 攻击半径 16 内威胁，伤害 `0.6×武器攻击×D`。 | `Alt+Shift+M↓` | `academy:magnet_manipulation` | `MagneticWeapon` |
| `current_symbiosis` 电流共生 | 现行 | L3 / 30k / 维持 30 | 每 10 tick 为手持与穿戴装备补充能量；过充可使下一次同系施放成本 ×0.8（触发后冷却 100 tick）。 | `Y↑` | `academy:current_recharge` | `CurrentSymbiosis` |
| `bioelectric_operation` 生物电操作 | 现行 | L4 / 60k / 维持 40；满熟练度低血量时每 20 tick 5 | 提供移速 +0.1、台阶 +0.4、移动效率 +1、跳跃 +0.58、攻速 +2.4、挖掘速度 +0.5、安全坠落 +10；攻击伤害属性 `+4A`。 | `Alt+N↑` | `academy:electrical_contact` | `BioelectricOperation` |
| `electromagnetic_shield` 电磁护盾 | 现行 | L4 / 60k / 维持 40；每次冷却 20 | 容量 `100A`，先吸收来伤；每 20 tick 可清除 `10A` 负荷。 | `Alt+K↑` | `academy:magnet_manipulation` | `ElectromagneticShield` |
| `iron_sand_arsenal` 铁砂操作 | 现行 | L4 / 60k / 维持 40 | 半径 2 近身脉冲 `4AD`；主手挥动向前 120°、半径 12 横扫并造成 `10AD`。 | `Alt+Shift+I↓ / Alt+Shift+G↓` | `academy:magnetic_weapon` | `IronSandArsenal` |
| `thunder_lance` 雷击之枪 | 现行 | L2 / 10k / 20 | 向 32 格路径发射四道闪电，路径半径 2，伤害 `16AD`。 | `Ctrl+T↓ / Alt+鼠标右键↑` | `academy:arc_generate` | `ThunderLance` |
| `lightning_storm` 闪电风暴 | 现行 | L5 / 60k / 80 | 50 格选点，在半径 8 内召唤 21 次雷击；每次技能伤害 `2%Hmax+8AD`。 | `Alt+Shift+L↓` | `academy:ball_lightning` | `LightningStorm` |
| `railgun` 电磁炮 | 现行 | L4 / 60k / 100 + 1 个弹药 | 蓄力并消耗弹药；基础伤害 `150AD×弹药倍率`。硬币/铁锭/铁块/铁砧倍率为 0.8/1/1.5/2，射程与宽度也随弹药增加。 | `X↓` | `academy:thunder_lance` | `Railgun` |
| `ball_lightning` 球状闪电 | 现行 | L5 / 100k / 80 | 最长存在 2000 tick、索敌半径 64；接近目标后在半径 5 造成 `(0.3Hmax+10)AD`。 | `Y↓` | `academy:lightning_nova` | `BallLightning` |
| `thunderclap` 雷鸣 | 现行 | L5 / 100k / 100 | 64 格选点、半径 5；先造成 1 点，再造成 `20%Hmax+20AD`。 | `Alt+Shift+Y↓` | `academy:ball_lightning` | `Thunderclap` |

## Meltdowner 原子崩坏

| 技能 | 状态 | 等级 / IF / 消耗 | 实现与当前效果 | 默认按键 | 直接前置 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `single_high_speed_electron_beam` 粒机波形高速炮 | 现行 | L1 / 5k / 15 | 从随机始发点发射延迟 10 tick 成形的 50 格窄射束，伤害 `16MAD+1%Hmax`；准星校正率随熟练度由 50% 提升至 100%，近距离始发散布自动收紧至 2° 直射锥。 | `Alt+鼠标左键↓` | 无 | `SingleHighSpeedElectronBeam` |
| `scatter_bomb` 电子弹散射 | 现行 | L2 / 10k / 40 | 蓄力 20–80 tick 后释放 7 束 50 格射线；每束 `16MAD+1%Hmax`。 | 见源码（可配置） | `academy:single_high_speed_electron_beam` | `ScatterBomb` |
| `radiation_intensify` 镭射强化 | 现行 | L1 / 5k / 0 | 被动：射束命中施加 200 tick 标记，使后续兼容射束的固定基础伤害 ×1.5。 | 无 | `academy:single_high_speed_electron_beam` | `RadiationIntensify` |
| `mining_beam` 采矿光束 | 现行 | L2 / 10k / 每 20 tick 20 | 最长 48 格；每 20 tick 对路径实体造成 `12AD`，每 3 tick 破坏半径 0.35、采掘等级 4 的方块。 | `M↓ / M↑` | `academy:single_high_speed_electron_beam` | `MiningBeam` |
| `light_shield` 光盾 | 现行 | L3 / 30k / 每 2 tick 4 | 按住获得抗性提升 II；每 4 tick 对半径 3.5 的敌人造成 `3AD` 并击退。 | `H↓ / H↑` | `academy:single_high_speed_electron_beam` | `LightShield` |
| `cloudroom` 粒子云室 | 现行 | L3 / 30k / 维持 30 | 显示 16 格内生物轨迹；每实体每 5 tick 最多 6 条，轨迹寿命 30 tick。 | `Alt+U↓` | `academy:light_shield` | `Cloudroom` |
| `particle_wave_cannon` 波形粒子炮 | 现行 | L4 / 60k / 启动 10；维持每 2 tick 10 | 蓄力 25 tick 后维持 85 格宽射束；每 10 tick `40MAD+1%Hmax`，破坏半径 0.6、采掘等级 4。 | `C↓ / C↑` | `academy:scatter_bomb` | `ParticleWaveCannon` |
| `jet_strike` 突击喷射 | 现行 | L4 / 60k / 20 | 突进 8 格，在落点半径 3.25 造成 `10AD`。 | `R↓` | `academy:light_shield` | `JetStrike` |
| `disintegrate` 解离射线 | 现行 | L5 / 100k / 100或最大CP的20%取高 | 30 格射线造成 `20%Hmax×D` 并按服务器权限破坏路径方块；击杀后最多散射 3 束，二段击杀可再散射 1 束且不继续连锁。 | `Alt+Shift+K↓` | `academy:particle_wave_cannon` | `Disintegrate` |
| `auto_cruise_beam_cannon` 自动巡航光束炮 | 现行 | L5 / 100k / 维持 50；每发 10 | 每 10 tick 扫描 16 格敌人，最快每 2 tick 发射一束 `(10M+1%Hmax)D` 延迟射线。 | `Y↑` | `academy:scatter_bomb` | `AutoCruiseBeamCannon` |

## Teleport 空间移动

| 技能 | 状态 | 等级 / IF / 消耗 | 实现与当前效果 | 默认按键 | 直接前置 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `threatening_teleport` 危险传送 | 现行 | L1 / 5k / 10 + 1 个主手物品 | 将 1 个主手物品传入 64 格内目标，伤害 `(4+武器攻击加成)AD`；空放时物品在前方 16 格掉落，击杀时随死亡掉落返还。 | `Alt+鼠标左键↓` 预览 / `Alt+鼠标左键↑` 施放；HUD 选中后 `C↓ / C↑` | 无 | `ThreateningTeleport` |
| `space_folding_theorem` 空间折叠理论 | 现行 | L1 / 5k / 0 | 被动：适用传送伤害 ×1.25；熟练度档位提高到 1.30/1.35/1.40，满熟练度击杀可返还 20% 实际施放 CP（60 tick 冷却）。 | 无 | `academy:threatening_teleport` | `SpaceFoldingTheorem` |
| `self_teleport` 自我传送 | 现行 | L2 / 10k / 去程 10；返程 5 | 默认 40 格、最大 64 格，选择首个方块阻挡前或更近的安全落点；可返回上次起点。 | `R↓` + 滚轮 / `R↑` | `academy:threatening_teleport` | `SelfTeleport` |
| `spatial_synergy` 空间协同 | 现行 | L2 / 10k / 维持 20；每名被携带玩家 10 | 自我/穿透/定位传送时携带半径 4 内同队玩家。 | `X↓` | `academy:self_teleport` | `SpatialSynergy` |
| `piercing_teleportation` 穿透传送 | 现行 | L2 / 10k / 15 | 默认显示 40 格内第一处阻挡后的安全落点；滚轮从显示位置切换为 64 格内自由调距，可选择未穿墙落点。 | `Alt+R↓` + 滚轮 / `Alt+R↑` | `academy:self_teleport` | `PiercingTeleportation` |
| `disarm` 缴械传送 | 现行 | L2 / 10k / 单手 20；双手 40 | 16 格内缴械目标并造成 1 点技能伤害；高熟练度可同时取走双手物品。 | `Alt+D↓` | `academy:self_teleport` | `Disarm` |
| `flesh_ripping` 肌体撕裂 | 现行 | L3 / 30k / 20 | 锁定 64 格内目标，造成 `(12A+5%Hmax)D`，再受空间折叠倍率影响。 | `Alt+鼠标右键↓ / Alt+鼠标右键↑` | `academy:piercing_teleportation` | `FleshRipping` |
| `shackle` 禁锢传送 | 现行 | L3 / 30k / 30 | 禁锢 32 格内目标 160 tick 并造成 3 点技能伤害；高熟练度非玩家目标持续 200 tick。 | `Alt+Shift+S↓` | `academy:self_teleport` | `Shackle` |
| `location_teleport` 位置传送 | 现行 | L3 / 30k / 去程 40；返程 20 | 保存最多 32 个命名位置并跨维度传送；可返回上次起点。 | `L↓` | `academy:piercing_teleportation` | `LocationTeleport` |
| `quick_location_teleport` 快速位置传送 | 现行 | L4 / 60k / 30 | 将准星 32 格内实体或自身送往当前已保存位置。 | `C↓` | `academy:location_teleport` | `QuickLocationTeleport` |
| `area_teleport_select` 区域传送 | 现行 | L4 / 60k / 50 | 80 格服务端射线选择源区与目标，每轴基础上限 32 格；事务搬移方块、方块实体和非玩家实体，失败回滚。 | `Y↓` 标角；`Alt+Y↓` 设目标；`Shift+Y↓` 启动；高熟练度 `Shift+Alt+Y↓` 变换、`Ctrl+Alt+Y↓` 交换 | `academy:location_teleport` | `AreaTeleportSelect` + 动作处理类 |
| `flashing` 高速闪现 | 现行 | L5 / 100k / 维持 50；每次 5 | 启用后沿移动方向安全闪现 8 格，客户端每 6 tick 可重复。 | `H↓` | `academy:location_teleport` | `Flashing` |
| `defensive_teleport` 防御传送 | 现行 | L5 / 100k / 每次 20 | 按住框选前方 5×5×5 区域，松开后把其中敌对生物/投射物传送到位置传送当前坐标。 | `Alt+G↑` | `academy:quick_location_teleport` | `DefensiveTeleport` |
| `spacial_excision` 空间切除 | 现行 | L5 / 100k / `最大CP`；1000 熟练度后 `0.9×最大CP` | 蓄力 40 tick 后每 10 tick 造成 `20D` 并切除获准方块；半径从 2 按每 tick +0.05 增长，最多持续 200 tick。 | `Alt+Shift+Ctrl+O↓` | `academy:area_teleport_select` | `SpacialExcision` |

## Darkmatter 未元物质

| 技能 | 状态 | 等级 / IF / 消耗 | 实现与当前效果 | 默认按键 | 直接前置 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `darkmatter_shaping` 未元物质塑型 | 现行 | L1 / 5k / 空手 50；持物且熟练度≥2000 为 25；自动修复按修复比例计费 | 空手生成未元物质；持物时修复并切换强化。满熟练度每秒可自动修复最多 15% 耐久。 | `U↑` | 无 | `DarkmatterShaping` |
| `darkmatter_disassemble` 未元物质分解 | 现行 | L1 / 5k / 10 | 32 格射线对实体造成 `8AD` 或分解方块；六翼启用时对目标周围半径 3 同样生效。 | `Alt+鼠标左键↑` | `academy:darkmatter_shaping` | `DarkmatterDisassemble` |
| `darkmatter_cut` 未元物质切割 | 现行 | L2 / 10k / 20 | 前方约 120° 锥形斩击：普通半径 8、`12AD`；六翼半径 24、`16AD`。满熟练度命中后延迟 6 tick 追加 50% 伤害。 | `R↑` | `academy:darkmatter_disassemble` | `DarkmatterCut` |
| `darkmatter_radiation` 未元物质照射 | 现行 | L3 / 30k / 每 2 tick 10 | 持续照射前方半球 32 格；每 tick 造成 `2AD + max(2,0.1%Hmax)A`。 | `C↓ / C↑` | `academy:darkmatter_cut` | `DarkmatterRadiation` |
| `darkmatter_repair` 未元物质修补 | 现行 | L4 / 60k / 完整治疗笔 10；不足 4 点时按比例 | 仅受伤时每 tick 治疗 `4A`，并按实际治疗比例收费。 | `Alt+U↑` | `academy:darkmatter_shaping` | `DarkmatterRepair` |
| `darkmatter_creation` 未元物质创生 | 现行 | L4 / 60k / 每只召唤 80；每只维持 20（最多 160） | 最多召唤 8 只所属独角仙；每只攻击 `12AD`。 | `G↑` | `academy:darkmatter_repair` | `DarkmatterCreation` |
| `darkmatter_six_wings` 未元物质六翼 | 现行 | L5 / 100k / 维持 50；低伤免疫每次 10 | 获得飞行、真实抗性 +4，并强化分解/切割；低于 2 点的来伤可花 CP 免疫。满熟练度还降低同系技能成本。 | `Alt+R↑` | `academy:darkmatter_shaping` | `DarkmatterSixWings` |

## Mentalout 心理掌握

| 技能 | 状态 | 等级 / IF / 消耗 | 实现与当前效果 | 默认按键 | 直接前置 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `mental_intervention` 心灵介入 | 现行 | L1 / 5k / 每次加入 10 | 将 16 格内兼容生物/允许的玩家加入或移出受控清单。 | 见源码（可配置） | 无 | `MentalIntervention` |
| `target_misidentification` 目标误认 | 现行 | L1 / 5k / 每次设置 40 | 令受控兼容生物永久把选中目标视为敌人；再次选择解除。 | 见源码（可配置） | `academy:mental_intervention` | `TargetMisidentification` |
| `mental_stupor` 呆然自失 | 现行 | L2 / 10k / 每目标维持 10；Boss ×2、玩家 ×3 | 持续冻结受控目标的移动与 AI。 | 见源码（可配置） | `academy:target_misidentification` | `MentalStupor` |
| `impression_manipulation` 印象操作 | 现行 | L3 / 30k / 每目标维持 10；Boss ×2、玩家 ×3 | 令受控目标把施术者和同清单目标视为盟友并抑制自然仇恨。 | 见源码（可配置） | `academy:mental_intervention`<br>`academy:target_misidentification` | `ImpressionManipulation` |
| `mental_intrusion` 心灵潜入 | 现行 | L1 / 5k / 维持 20/17/14 | 观察目标而不移动/控制它；技能级 0/1/2 的范围为 16/24/32，玩家观察上限移除 | 见源码（可配置） | `academy:mental_intervention` | `MentalIntrusion` |
| `mental_takeover` 意识接管 | 现行 | L4 / 60k / 维持 100 | 在心灵潜入期间接管清单目标的移动、视角和攻击；玩家可挣扎解除。 | 见源码（可配置） | `academy:mental_intrusion`<br>`academy:command_positioning`<br>`academy:mental_stupor` | `MentalTakeover` |
| `sensory_distortion` 感官扭曲 | 现行 | L2 / 10k / 维持 30/25/21；Boss ×2 、玩家 ×3 | 潜入期间屏蔽目标对施术者的视线、仇恨记忆、渲染和直接交互感知。 | 见源码（可配置） | `academy:mental_intrusion` | `SensoryDistortion` |
| `command_positioning` 指挥定位 | 现行 | L3 / 25k / 每目标 10；Boss ×2 | 命令受控实体移动至准星方块外侧或跟随准星实体，最远 64 格。 | 见源码（可配置） | `academy:mental_intervention` | `CommandPositioning` |
| `mind_destruction` 心智破坏 | 现行 | L5 / 100k / 施放 100（熟练度档位为 100/90/80/70） | 对清单内准星目标持续 10 秒，每秒造成 `1% 最大生命 + 10` 心智伤害；精神防护只阻止呆然自失，不阻止伤害。 | `Alt+H↓` | `academy:sensory_distortion` | `MindDestruction` |
| `precision_operation` 精密操作 | 现行 | L5 / 100k / 按节点累加：路径 5、视角 5、守卫 10、误认 20、呆然 10、印象 10、感官 20/15/10、潜入 20/15/10；路径/视角/守卫/呆然/印象对 Boss ×2 | 编辑并并行执行 4 个无环心理程序；各持续行动可限时或永久。 | 见源码（可配置） | `academy:impression_manipulation`<br>`academy:mental_stupor` | `PrecisionOperation` |

## Level0 公共脑开发

| 技能 | 状态 | 等级 / IF / 消耗 | 实现与当前效果 | 默认按键 | 直接前置 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- |
| `brain_domain_development` 脑域开发 | 现行 | L1 / 5k / 0 | 最大 CP `+T×能力等级×5`，能力 L5 满档为 +100。 | 无 | 无 | `BrainDomainDevelopment` |
| `endurance_training` 耐力训练 | 现行 | L1 / 5k / 0 | 耐受力 `+50T`，满档 +200。 | 见源码（可配置） | `academy:brain_domain_development` | `EnduranceTraining` |
| `physical_training` 体术训练 | 现行 | L1 / 5k / 0 | 肌肉力量、灵巧度各 `+25T`，满档各 +100。 | 见源码（可配置） | `academy:brain_domain_development` | `PhysicalTraining` |
| `multiple_brain_domain_segmentation` 多重脑域分割 | 现行 | L2 / 10k / 0 | 有限堆栈技能的声明上限 `+T`；当前因全局堆栈限制关闭而不生效。 | 无 | `academy:brain_domain_development` | `MultipleBrainDomainSegmentation` |
| `parallel_thought_computation` 思考并列演算 | 现行 | L3 / 30k / 0 | CP 迭代速度 `×(1+5%T)`，满档 ×1.2。 | 无 | `academy:multiple_brain_domain_segmentation` | `ParallelThoughtComputation` |
| `complete_consciousness_analysis` 意识完全解析 | 现行 | L4 / 60k / 0 | 每点 SP 可恢复的 CP `10×(1+10%T)`，满档 14 CP/SP。 | 无 | `academy:parallel_thought_computation` | `CompleteConsciousnessAnalysis` |
| `absolute_self_control` 自我绝对控制 | 现行 | L5 / 100k / 0 | 免疫能力过载；已过载时立即恢复正常。 | 无 | `academy:complete_consciousness_analysis` | `AbsoluteSelfControl` |

## 默认按键冲突与调控优先级

| 优先级 | 冲突/问题 | 影响 | 建议 |
| --- | --- | --- | --- |
| P0 | Electromaster：`electrical_contact` 与 `current_recharge` 都以 `H↓` 启动 | 同一按下动作可能切换带电接触并开始充能 | 为其中一个技能更换默认绑定，并在客户端验收按下/松开事件 |
| P1 | Electromaster：`ball_lightning` 为 `Y↓`，`current_symbiosis` 为 `Y↑` | 一次完整按键可能施放球状闪电并切换电流共生 | 为其中一个技能换键，或明确消费输入事件 |
| P1 | 新增和重命名技能仍有“见源码”按键项 | 表格不能独立用于默认键冲突审计 | 后续从统一输入注册表导出默认键，避免手工维护 |
| P1 | 高伤害、方块修改、区域传送和玩家控制技能 | 数值统一前仍需服务端权威、权限与多人生命周期验收 | 按 `RUNTIME_ACCEPTANCE.md` 完成客户端和双玩家专服门禁 |

## 建议的统一修改入口

- 等级、IF、基础 CP、维持 CP、依赖：各技能构造器的 `Skill.Builder`。
- 默认键：各技能 `initClient()` 中的 `InputSystem.combo(...)`；玩家覆盖值由统一 `InputSystem` 配置保存。
- 范围、伤害、持续时间、扫描间隔：各类常量、计算方法和运行时管理器。
- 熟练度成本与迭代修正：`SkillProficiencyProfiles.java`。
- 当前验收边界和未关闭人工检查：`RUNTIME_ACCEPTANCE.md`。
