# 技能总表

> 单一事实来源。原 `ABILITY_SKILL_USAGE_GUIDE.md`、`SKILL_IMPLEMENTATION_CONTROL_MATRIX.md`、
> `SKILL_EFFECT_COST_ITERATION_STACK_MATRIX.md`、`ability_skill_dependencies.md` 已并入本文。
> 数据基线：2026-10-18，HEAD `782d935b`，`Skills` 注册 99 个技能。

## 1. 阅读规则

**记号。** `A` 为能力强度倍率，`D` 为玩家伤害倍率，`H` / `Hmax` 为目标当前 / 最大生命，`Htmax` 为目标真实最大生命，`M` 为原子崩坏镭射标记倍率（未标记 1、已标记 1.5）；20 tick = 1 秒。表中伤害均为出力 `O=1` 的基准值。

**出力。** `O` 为出力控制的能力出力，范围 `0.01–2`。技能伤害最终乘边际曲线 `E(O)`，且 `E(0.01/1/2)=0.01/1/2`；标记为伤害技能的独立攻击 CP 乘 `C(O)`，且 `C(0.01/1/2)=0.1/1/4`。维持占用、上限占用、非伤害技能消耗与精密操作不受 `O` 调整。

**CP、Q 与 MP。** `CP` 是计算力；未特别说明时“消耗”指源码基础 CP，实际还会乘计算强度，并受熟练度档位、服务端配置、电流共生等修正。气动操纵的 `Q` 为压缩空气，默认容量 128、自然恢复 4/tick，使用气动技能期间暂停恢复。未元物质的 `MP` 为未元物质资源；本系相态用 `α`、`β`、`γ` 表示（每 50 相态点为 1 单位，`α+β=能力等级`，六翼时 `γ=能力等级`），基础 MP 容量 `100+8×能力等级²`，自然恢复 1 MP/s。

**占用、消耗与迭代。** “占用”表示技能开启/维持期间降低可用计算力；“消耗”表示每次施放或生效支付计算力。迭代是一次临时 CP 占用归还前所需的迭代点；默认每秒获得 1 点（需要 SP），所以 10 点基线约为 10 秒；永久占用只在关闭技能或效果结束后返还。带施放 CP 或维持占用的技能，其 Builder 迭代声明会被 `MAX_CP_ITERATION_TICKS=20` 封顶；声明为 0 时，临时占用通常按 `ceil(基础消耗/2)` 推导。

**堆栈。** `迭代 / 堆叠` 列写作 `迭代点数 / 有效堆栈（声明值）`；`永久` 表示占用持续到关闭/结束，`—` 表示无临时迭代。当前 `STACK_LIMITS_ENABLED=false`，Builder 声明堆栈的运行时有效上限都是 `∞`；独立运行态可另设限制，例如矢量冲击射线通道 10 层、空间切割同时只能激活 1 个运行态。

**熟练度。** 按 0/1000/2000/3000 四个阈值计算，对应界面档位 1/2/3/4；下文“1000 后 / 2000 后 / 3000 后”指达到该阈值后的强化。修正由各技能的 `SkillProficiencyProfile` 或自定义逻辑计算，并非所有消耗统一打折；呆然自失与印象操作的动态消耗档位为 `×1/0.9/0.8/0.8`，矢量反射与矢量偏移在 3000 熟练度时迭代由 10 降为 5。

**等级、开发能量与前置。** 等级、开发能量与直接前置以仓库根 `skill-registry.tmp.csv` 为准：等级已按 `Skill.Builder.level(AbilityLevel.LEVELn)` 解析，开发能量即源码 `energyCost`（控制矩阵旧称 `IF`），前置已含可变参数形式。这里的等级是推荐能力等级，不是熟练度推导出的技能效果等级。前置列省略 `academy:` 命名空间，多个前置用 `、` 分隔。冲突时优先级为 CSV（等级/能量/前置）> 源码常量（数值）> 效果总表 > 使用说明。`5k` = 5,000，`10k` = 10,000，`30k` = 30,000，`60k` = 60,000，`100k` = 100,000；`0` 表示源码显式 0，或未设置 `energyCost` 时的默认 0。

**按键。** `↓` 按下，`↑` 松开，`↓ / ↑` 表示按住/蓄力或开始/结束；`Alt+…` 为组合键。表内是源码默认值，玩家实时覆盖值以 `config/academy-client.json` 为准。标为“见源码（可配置）”表示默认组合尚未在本表固化，不表示技能没有按键；被动技能通常为“无”。

**GUI 文案口径。** 技能未学习时也应显示用途说明，放在名称/等级之后、开发条件之前，正文对应 `skill.academy.<技能 ID>.desc`；名称、推荐等级、操作形态、开发条件和消耗由数据分别渲染，不重复塞进描述正文。描述优先写操作、目标、核心结果和关键限制，内部公式、调试术语与实现类名不进入玩家文案；按键使用“施放键”“切换键”“按住并松开”等动作，不写死默认键；明确区分“占用 CP”与“消耗 CP”。

## 2. 分类总览

| 分类 | 技能数 | 定位 |
| --- | ---: | --- |
| 通用 Level 0 / 公共脑域 | 8 | CP、迭代、堆叠、身体属性、出力与过载相关的通用强化。 |
| 气动操纵 Aeromanip | 15 | 位移、感知、牵引、领域控制、飞行、气爆与真空领域。 |
| 矢量操作 Accelerator | 15 | 冲击、偏移、反射、动能强化、高速移动、结构投掷、飞行形态与处决。 |
| 电击使 Electromaster | 16 | 电弧、雷击、磁力控制、装备充能、自动武装与电磁防御。 |
| 原子崩坏 Meltdowner | 10 | 高速电子束、持续射束、范围轰击、光盾、追踪与解离攻击。 |
| 空间移动 Teleport | 15 | 自身/目标传送、控制与伤害、坐标管理、区域搬移及自动防御。 |
| 未元物质 Darkmatter | 9 | 物质生成/强化、相态调谐、分解、切割、照射、自愈、召唤与六翼形态。 |
| 心理掌握 Mentalout | 11 | 目标干预、感知控制、意识接管、痛觉消减与广域干涉。 |
| **合计** | **99** | **8 个能力类别。** |

## 3. 通用 Level 0（8）

通用课程技能自动生效，通常没有独立施放键（出力控制另有调节界面）；重置能力分类时保留。`T` 为熟练度档位（满档 4）。通用被动技能会在每个非 Level 0 类别中分别显示，但共享同一组技能数据和前置链。控制矩阵将耐力训练、体术训练的默认键标为“见源码（可配置）”，本表照录；二者本身是常驻被动。

| 技能 | 等级 / 开发能量 | 直接前置 | 默认按键 | CP / 资源 | 迭代 / 堆叠 | 效果与数值 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 脑域开发 `brain_domain_development` | L1 / 5k | 无 | 无 | 0（常驻被动） | —（声明 40）/ ∞（2） | 最大 CP 提高 `T×能力等级×5`，能力 L5 满档 +100。 | `BrainDomainDevelopment` |
| 耐力训练 `endurance_training` | L1 / 5k | 无 | 见源码（可配置） | 0（常驻被动） | — / ∞（2） | 耐受力提高 `50T`，满档 +200。 | `EnduranceTraining` |
| 体术训练 `physical_training` | L1 / 5k | 无 | 见源码（可配置） | 0（常驻被动） | — / ∞（2） | 肌肉力量与灵巧度各提高 `25T`，满档各 +100。 | `PhysicalTraining` |
| 多重脑域分割 `multiple_brain_domain_segmentation` | L2 / 10k | `brain_domain_development` | 无 | 0（常驻被动） | —（声明 40）/ ∞（2） | 有限堆栈技能的声明上限 +T；当前 `STACK_LIMITS_ENABLED=false`，不生效。 | `MultipleBrainDomainSegmentation` |
| 出力控制 `output_control` | L3 / 30k | `brain_domain_development` | `Tab` 按住 | 0（调节界面） | —（声明 40）/ ∞（2） | 调整 `O=0.01–2`、移动速度与跳跃高度加成；技能伤害乘 `E(O)`，伤害技能独立攻击 CP 乘 `C(O)`；只影响伤害技能及其攻击 CP，不影响维持、上限占用与精密操作。 | `OutputControl` |
| 思考并列演算 `parallel_thought_computation` | L3 / 30k | `multiple_brain_domain_segmentation` | 无 | 0（常驻被动） | —（声明 40）/ ∞（2） | CP 迭代速度 `×(1+5%T)`，满档 ×1.2；与技能调试加速乘算。 | `ParallelThoughtComputation` |
| 意识完全解析 `complete_consciousness_analysis` | L4 / 60k | `parallel_thought_computation` | 无 | 0（常驻被动） | —（声明 40）/ ∞（2） | 每点 SP 可迭代的 CP 提高 `10×(1+10%T)`，满档 14 CP/SP。 | `CompleteConsciousnessAnalysis` |
| 自我绝对控制 `absolute_self_control` | L5 / 100k | `complete_consciousness_analysis` | 无 | 0（常驻被动） | —（声明 40）/ ∞（2） | 免疫 CP 归零后的过载；已过载时立即恢复正常。 | `AbsoluteSelfControl` |

## 4. 气动操纵 Aeromanip（15）

本系蓄力统一分为瞬发（少于 8 tick）、半蓄力（8–23 tick）与满蓄力（至少 24 tick）；表中 CP 与 Q 按三档排列。

| 技能 | 等级 / 开发能量 | 直接前置 | 默认按键 | CP / 资源 | 迭代 / 堆叠 | 效果与数值 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 气流喷射 `airflow_jet` | L1 / 5k | 无 | `R` 按住 | 瞬发 12 CP / 半蓄 14 CP / 满蓄 24 CP；Q 4/8/16 | 5 / ∞（∞） | 瞬发 12 格射流造成 `1.5D`（1000 后 `2D`）并击退 1.1/1.4；半蓄沿视线以 1.35 推进（2000 后 1.62）；满蓄半径 6 排斥并以 2.35 推进（3000 后 2.82），持续 30 tick（2000 后 40），水下推进 ×0.4。 | `AirflowJet` |
| 层流缓冲 `laminar_buffer` | L1 / 5k | `airflow_jet` | `U` 按住 | 瞬发永久占用 12 CP；半蓄 10 CP+32Q；满蓄 18 CP+48Q | 永久；主动笔 5 / ∞（∞） | 瞬发切换层流场：下坠速度不低于 -0.12，保留水平惯性并强化起跳，1000 后共享给 16 格友军；半蓄定高悬停 60 tick（2000 后 100）；满蓄生成 200 tick 空气平台（3000 后 300）。 | `LaminarBuffer` |
| 呼吸空泡 `breathing_bubble` | L2 / 10k | `flow_sense` | `Alt+U↑` | 维持 20 CP；激活 8Q（1000 后 6） | 永久；激活笔 20 / ∞（∞） | 开启后跟随自身排开半径 2 格水体并持续补氧，关闭/离开/失效后恢复原水体，含水方块仅暂时脱水；2000 后队友与驯养生物共享补氧及真空保护，3000 后半径 3，不持续耗气。 | `BreathingBubble` |
| 流场感知 `flow_sense` | L2 / 10k | `airflow_jet` | `Alt+N↑` | 0 | —（声明 10）/ ∞（∞） | 每 10 tick 感知半径 24（1000 后 32）内最多 64 个移动实体/投射物（2000 后 96，间隔 5 tick），显示方向与速度；3000 后潜行玩家也会暴露。 | `FlowSense` |
| 气动牵引 `pneumatic_grasp` | L2 / 10k | `flow_sense` | `G↓ / G↑` | 有效控制时 10 CP+2Q/10 tick（结构目标两项均乘质量倍率）；满熟练度抬升落地生物另 5 CP+2Q/5 tick | 5 / ∞（∞） | 牵引或推动物品、经验球、投射物和敌对生物；技能级范围 16/20/24（1000 后 +8），保持距离 2–12/14/16；3000 后可用半强度抬升落地非 Boss 生物。 | `PneumaticGrasp` |
| 暴流空泡 `turbulent_cavitation` | L2 / 10k | `breathing_bubble` | `=`↑ | 0（开关与维持均无消耗） | —（声明 10）/ ∞（∞） | 气动位移作用于非友方生物时施加/刷新 100 tick 标记，首次标记立刻造成 2 点穿甲伤害并爆裂；每 10 tick 按累计路程结算：每 0.2 格造成 2 点穿甲伤害并损伤护甲 `min(40,ceil(12d))`，单次基础伤害上限 20（2000 后系数 18，3000 后上限 64），撞墙追加碰撞速度伤害。 | `TurbulentCavitation` |
| 大气护盾 `atmosphere_shield` | L3 / 30k | `breathing_bubble` | `Ctrl+R↑` | 维持 30 CP；每次防御效果 8Q；低伤免疫 10 CP；一般防御 `min(30,4+2×减免伤害)` CP（1000 后 ×0.9） | 永久；动态 5 / ∞（∞） | 维持压缩空气薄膜：攻击击退 +0.5A、真实抗性 +6，并停止附近投射物；普通伤害减免 20%/28%/35%，3000 后再 +10%、上限 50%；低于 4 点（3000 后 6）的伤害全免，空气耗尽不自动关闭。 | `AtmosphereShield` |
| 层流切割 `laminar_cutter` | L3 / 30k | `pneumatic_grasp` | `Alt+鼠标右键` 按住 | CP 20/35/50（1000 后 ×0.9）；Q 10/18/28 | 10 / ∞（20） | 发射长度 24/28/32（2000 后 ×1.2）的层流刃，半宽 2.5/4，伤害 `4AD`/`6AD`/`8AD`；满蓄穿甲并使每件装备损耗 80（2000 后缴械），3000 后附加 5 秒护甲效能降低（生物 20%、玩家 10%）。 | `LaminarCutter` |
| 拒绝之风 `rejecting_wind` | L3 / 30k | `tailwind_field` | `B` 按住 | CP 25/40/60；Q 24/44/68 | 10 / ∞（4） | 引爆周围 8 格（1000 后 10）大气：伤害 `3AD`/`5AD`/`7AD`，水平推力 0.65/1.15/1.65（3000 后 ×1.2）；瞬发减速 40 tick，半/满蓄减速 II 60 tick，满蓄另漂浮 50 tick（2000 后持续 ×1.5）。 | `RejectingWind` |
| 顺风场 `tailwind_field` | L3 / 30k | `pneumatic_grasp` | `Alt+G` 按住 | CP 25/35/45；Q 24/36/48 | 10 / ∞（3） | 三档定向/跟随/径向流场：半径 8（1000 后 ×1.2），强度 0.22/tick（3000 后 0.26），持续 120/160/160 tick（2000 后 ×1.25）；为友军和顺向投射物加速并压制逆向敌人。 | `TailwindField` |
| 飞行 `flight` | L4 / 60k | `high_speed_jet` | `Alt+R↑` | 永久占用 30 CP；移动时 2Q/20 tick | 永久（声明 10）/ ∞（∞） | 获得服务端控制的创造模式式飞行；基础速度 0.035，1000/2000/3000 后 0.05/0.065/0.08；悬停不耗 Q，空气耗尽不自动关闭。 | `Flight` |
| 高速喷流 `high_speed_jet` | L4 / 60k | `airflow_jet` | `Alt+H` 按住放置；`H↑` 激活；`Shift+H↑` 结构发射 | 放置 18 CP+12Q；激活 N 个 `(8+2N)` CP+`8N` Q；结构发射 B 个方块另加 `0.25B` CP+`0.5B` Q | 15 / ∞（5） | 8 格内放置喷口，最多 8 个（1000 后 12），可控制 64 格内全部自有喷口；喷流长度 14、半径 1.35，每 tick 加速 0.12（3000 后 0.15），速度上限 6，多喷口叠加；普通激活 40 tick（2000 后 60），结构发射把向内覆盖的方块沿喷口反向推出。 | `HighSpeedJet` |
| 涡流牵引 `vortex_pull` | L4 / 60k | `rejecting_wind` | `Alt+B` 按住 | CP 24/38/55（1000 后 ×0.9）；Q 24/40/64 | 15 / ∞（5） | 16 格内选点：瞬发半径 7、强度 0.85 的脉冲；半蓄半径 8、强度 0.26、持续 80 tick；满蓄半径 12、强度 0.42、持续 120 tick，2000 后半径与持续 ×1.2；3000 后捕获并以 75% 速度重发最多 16 个投射物。 | `VortexPull` |
| 压缩绝热 `adiabatic_compression` | L5 / 100k | `vortex_pull` | `Alt+Y` 按住 | 12 CP+8Q/10 tick（1000 后 CP ×0.9） | 20 / ∞（∞） | 32 格内维持半径 6 压缩区（2000 后 8），目标移动与跳跃降至 5%，离开后残留 60 tick；每 10 tick 每层造成 `0.5sAD` 穿甲伤害，层数无上限，3000 后 ×1.25。 | `AdiabaticCompression` |
| 真空领域 `vacuum_domain` | L5 / 100k | `vortex_pull` | `Y↑` | 永久占用 80 CP；8Q/10 tick（1000 后 6） | 永久（声明 20）/ ∞（∞） | 以自身为中心维持半径 50 真空域（3000 后 100）；每 2 tick 抽取 40 氧气（2000 后 60）直至 -20，之后每 10 tick 造成 `5%Hmax`；呼吸空泡保护的目标不受影响。 | `VacuumDomain` |

## 5. 矢量操作 Accelerator（15）

白翼与白金翼的当前行为口径见 13.3。

| 技能 | 等级 / 开发能量 | 直接前置 | 默认按键 | CP / 资源 | 迭代 / 堆叠 | 效果与数值 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 矢量加速 `vector_accel` | L1 / 5k | 无 | `C↓ / C↑` | 10 | 5 / ∞（10） | 蓄力最长 40 tick，速度 `7×sin(0.4+0.6C)`；满熟练度冲撞追加 `6AD`；由服务端校验，预览轨迹显示碰撞路径。 | `VectorAccel` |
| 矢量冲击 `vector_blast` | L1 / 5k | `vector_accel` | `Alt+鼠标左键↑` | 射击 10；拉/推每 10 tick 10 | 5 / 射线攻击 10、拉/推 ∞（∞） | 64 格、半径 1 射线造成 `10AD`，也可在 32 格内持续牵引/推离目标；跨越深渊开启时追加半径 8 的真实范围伤害。 | `VectorBlast` |
| 动能加持 `kinetic_energy_applied` | L2 / 10k | `vector_accel` | `K↓` | 维持 15；每次冲击 `10L` | 永久；冲击笔 5 / ∞（∞） | 占用 CP 强化移动与攻击；1–3 级冲击伤害 `(4+L²)AD`、半径 `L²+2` 并可破坏方块；其他攻击先 ×2，再追加 `4AD`。 | `KineticEnergyApplied` |
| 导向踏击 `dir_strike` | L2 / 10k | `vector_blast` | `Alt+R↓` | 15 | 5 / ∞（2） | 向周围地面传导半径 12 的环形冲击，造成 `12AD` 并伤害敌对目标；空中俯冲时半径再 +6，另附纯视觉地表波。 | `DirStrike` |
| 动能投掷 `kinetic_throw` | L3 / 20k | `kinetic_energy_applied` | `H↓ / H↑`（瞄准已有结构时按下即投出） | 30 CP（结构抬升与投掷） | 5 / ∞（3） | 瞄准 24 格内方块按住蓄力 0/1/2 秒，捕获下方半椭球（水平半径 5/7/9、竖向 2/4/6）与上方圆柱（半径 5/7/9、高 5/7/9），并向上拉起 4/5/7 格；结构由有限力锚点悬浮 10 秒，再次按键沿视线以统一标定速度掷出，飞行最长 30 秒，命中每个结构方块造成 5 点伤害并击退，投掷后不再变回方块。1000 后施放 CP ×0.9，2000 后蓄力时间 -20%，3000 后投掷速度 +20%、击退 +25%。 | `KineticThrow` |
| 矢量偏移 `vector_deviation` | L3 / 10k | `kinetic_energy_applied` | `N↓` | 维持 40；投射物拦截与成功折射按处理量动态计费（满熟练时低于 `1%最大CP` 的伤害无消耗） | 永久；动态 10（3000 后 5）/ ∞（∞） | 展开球形矢量场，大幅减速附近实体和投射物并施加虚弱、挖掘疲劳；未满熟练时每次可折射来伤有 50% 概率尝试完整折射，未折射的实际生命损失按 0/1000/2000 减免 50%/70%/90% 并播放偏移反馈；满熟练恢复完整实例保护与稳定折射。 | `VectorDeviation` |
| 矢量反射 `vector_reflection` | L4 / 30k | `vector_deviation` | `R↓` | 维持基础 40；过滤模式 40/60/80，名单每项 +5；另按来伤动态扣除（满熟练时低于 `1%最大CP` 的伤害无消耗） | 永久；动态 10（3000 后 5）/ ∞（∞） | 维持矢量场，用 CP 抵消可反射的来袭伤害，把投射物速度反向 ×1.2，并把符合条件的攻击反射给敌对来源；每点处理伤害的 CP 倍率为 2/1/0.5/0.5（熟练度档位）。 | `VectorReflection` |
| 过滤网 `reflection_filter` | L4 / 60k | `vector_reflection` | 见源码（可配置） | 自身 0；会改变矢量反射维持占用 | — / ∞（∞） | 配置矢量反射的全反射/正面过滤/中性过滤模式，以及效果白名单和黑名单（合计最多 256 项）。 | `ReflectionFilter` |
| 风暴之翼 `storm_wing` | L4 / 60k | `vector_reflection` | `B↓` | 维持 40；每 20 tick 维持费 10 | 永久；维持费笔 5 / ∞（∞） | 占用 CP 获得矢量飞行；移动键控制方向，按住跳跃键高速推进，可悬停。 | `StormWing` |
| 血流逆流 `bloodflow_reverse` | L5 / 100k | `vector_reflection` | `Alt+Shift+R↓` | 100 或最大 CP 的 20% 取高 | 20 / ∞（2） | 逆转近距离目标血流，造成 `5%Hmax+200`，并叠加缓慢、虚弱、挖掘疲劳（最高 V 级），持续 200 tick。 | `BloodflowReverse` |
| 黑翼 `black_wing` | L5 / 100k | `storm_wing` | 见源码（可配置） | 维持 60；每 20 tick 20；每次扇击 20 | 永久；维持费 5、扇击 10 / ∞（∞） | 进入双翼矢量飞行形态；主手挥击横扫前方 32 格扇形区域，造成 `(基础攻击+10)D+1%Htmax` 真实/复合伤害。 | `BlackWing` |
| 白翼 `white_wing` | L5 / 100k | `black_wing` | 见源码（可配置） | 维持 80；每 20 tick 40；每次扇击 20 | 永久；维持费 5、扇击 10 / ∞（∞） | 进入白翼矢量飞行形态；当前战斗效果与黑翼基本相同，主手挥击可发动 32 格扇形真实生命伤害。 | `WhiteWing` |
| 白金翼 `platinum_wing` | L5 / 100k | `white_wing` | 见源码（可配置） | 维持 160；每 20 tick 80；每次扇击 20 | 永久；维持费 5、扇击 10 / ∞（∞） | 保留翼形态飞行与 32 格扇形攻击；潜行挥击可处决准星指向的 128 格内非玩家生物，通常 `(2Htmax+1000)D`，高熟练度对 Boss 改为 `15%Htmax×D`。 | `PlatinumWing` |
| 跨越深渊 `crossing_the_abyss` | L5 / 100k | `white_wing` | 见源码（可配置） | 维持 100 | 永久 / ∞（∞） | 占用 CP，使自身伤害压低目标真实生命并锁定治疗上限；目标连续 3 次从致死攻击中存活后会被强制终结，同时强化矢量冲击。 | `CrossingTheAbyss` |
| 等离子体 `plasma_generation` | L5 / 100k | `storm_wing` | `Alt+Ctrl+C↓ / Alt+Ctrl+C↑` | 每完成 1 秒蓄力 40 | 20 / ∞（∞） | 在头顶聚集等离子核心，至少蓄力 3 秒后向视线选定位置发射大范围爆炸；每 40 tick 增长 1 阶，最多 6 阶，每阶伤害 `50AD`、伤害半径 5×阶数、爆破半径 2.5×阶数，最大选点 128。 | `PlasmaGeneration` |

## 6. 电击使 Electromaster（16）

`electrical_contact` 的开发能量在 CSV 中为空，源码未显式调用 `energyCost`，按控制矩阵记 0；`mine_detect` 为 L3/5k，`lightning_storm` 为 L5/60k。

| 技能 | 等级 / 开发能量 | 直接前置 | 默认按键 | CP / 资源 | 迭代 / 堆叠 | 效果与数值 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 电弧生成 `arc_generate` | L1 / 5k | 无 | `Alt+G↓` | 10 | 5 / ∞（20） | 向视线方向释放最长 10 格的短程电弧，路径半径 0.125，造成 `8AD`。 | `ArcGenerate` |
| 接触电击 `electrical_contact` | L1 / 0 | `arc_generate` | `H↓` | 维持 10 | 永久（声明 20）/ ∞（∞） | 占用 CP 周期电击半径 2 内敌人，每次 `2AD`；遭到敌对近战攻击时向攻击者返还一次电击。 | `ElectricalContact` |
| 雷击之枪 `thunder_lance` | L2 / 10k | `arc_generate` | `Ctrl+T↓ / Alt+鼠标右键↑` | 20 | 10 / ∞（10） | 从手边向视线方向发射四道闪电，攻击 32 格路径（半径 2）内的目标，伤害 `20AD`。 | `ThunderLance` |
| 闪电新星 `lightning_nova` | L2 / 10k | `thunder_lance` | `Ctrl+N↓` | 15 | 10 / ∞（1） | 释放持续扩张的电环，持续 200 tick、最大半径 16；波前每次掠过生物造成 `4AD`。 | `LightningNova` |
| 磁力操纵 `magnet_manipulation` | L3 / 30k | `arc_generate` | `Alt+X↓ / Alt+Shift+X↓ / R↓ / R↑` | 移动期间每 20 tick 10 | 10 / ∞（∞） | 瞄准含铁方块或实体时将自身拉向目标（0.9/tick），或把目标牵引到面前 2.5 格（1.15/tick），范围 48 格；配合潜行操作可切换。 | `MagnetManipulation` |
| 磁悬武装 `magnetic_weapon` | L3 / 30k | `magnet_manipulation` | `Alt+Shift+M↓` | 维持 30 | 永久（声明 20）/ ∞（∞） | 占用 CP 展开磁悬武装，每 10 tick 攻击半径 16 内的威胁，伤害 `0.6×武器攻击×D`，并阻挡来自正面的投射物。 | `MagneticWeapon` |
| 电流充能 `current_recharge` | L3 / 30k | `magnet_manipulation` | `H↓ / H↑` | 每 20 tick 30 | 10 / ∞（∞） | 按住持续为视线中的方块、生物及其手持/穿戴装备补充能量，仅找到有效目标时消耗 CP，范围 5 格。 | `CurrentRecharge` |
| 电流共生 `current_symbiosis` | L3 / 30k | `current_recharge` | `Y↑` | 维持 30 | 永久（声明 20）/ ∞（∞） | 占用 CP，每 10 tick 为自己手持和穿戴装备中的能量容器充能；过充可使下一次同系施放成本 ×0.8（触发后冷却 100 tick）。 | `CurrentSymbiosis` |
| 矿物探测 `mine_detect` | L3 / 5k | `magnet_manipulation` | `Alt+M↑` | 维持 30 | 永久（声明 20）/ ∞（∞） | 持续占用 CP，标记已加载区块内半径 64 的矿物；每 tick 最多扫描 32768 点，每 100 tick 重扫。 | `MineDetect` |
| 生物电操作 `bioelectric_operation` | L4 / 60k | `electrical_contact` | `Alt+N↑` | 维持 40；满熟练度低血量时每 20 tick 5 | 永久；低血量笔 10（声明 20）/ ∞（∞） | 占用 CP，以生物电强化移动、攻击、采掘、跳跃、攻速、跨步和坠落控制：移速 +0.1、台阶 +0.4、移动效率 +1、跳跃 +0.58、攻速 +2.4、挖掘速度 +0.5、安全坠落 +10，攻击伤害 `+4A`。 | `BioelectricOperation` |
| 电磁护盾 `electromagnetic_shield` | L4 / 60k | `magnet_manipulation` | `Alt+K↑` | 维持 40；每次冷却 20 | 永久；冷却笔 15 / ∞（∞） | 占用 CP，以容量 `100A` 的电磁屏障优先吸收伤害；每 20 tick 可清除 `10A` 负荷，并额外消耗 CP 冷却。 | `ElectromagneticShield` |
| 铁砂之剑 `iron_sand_arsenal` | L4 / 60k | `magnet_manipulation` | `Alt+Shift+I↓ / Alt+Shift+G↓` | 维持 40 | 永久（声明 20）/ ∞（∞） | 占用 CP，在剑、鞭、锤、盾四种形态间切换；半径 2 近身脉冲 `4AD`，主手挥动向前 120°、半径 12 横扫 `10AD`，盾形态减半所受伤害。 | `IronSandArsenal` |
| 电磁炮 `railgun` | L4 / 60k | `thunder_lance` | `X↓` | 100 + 1 个弹药 | 20 / ∞（∞） | 蓄力并消耗硬币、铁锭、铁块或铁砧，发射高伤害电磁弹：基础 `150AD×弹药倍率`，四者倍率 0.8/1/1.5/2，射程与宽度随弹药增加；允许方块破坏时贯穿路径方块。 | `Railgun` |
| 闪电风暴 `lightning_storm` | L5 / 60k | `ball_lightning` | `Alt+Shift+L↓` | 80 | 20 / ∞（1） | 在视线选定位置周围半径 8 内连续召唤 21 道闪电；每次技能伤害 `2%Hmax+8AD`，选点距离 50 格。 | `LightningStorm` |
| 球状闪电 `ball_lightning` | L5 / 100k | `lightning_nova` | `Y↓` | 80 | 20 / ∞（1） | 生成长时间存在的自主闪电球（最长 2000 tick），索敌半径 64，自动追踪并在接近目标后于半径 5 范围引爆，造成 `5%Hmax+135AD`。 | `BallLightning` |
| 雷鸣 `thunderclap` | L5 / 100k | `ball_lightning` | `Alt+Shift+Y↓` | 100 | 20 / ∞（∞） | 在视线选定目标处（64 格选点、半径 5）召唤雷击，先造成 1 点，再造成 `5%Hmax+95AD` 范围伤害。 | `Thunderclap` |

## 7. 原子崩坏 Meltdowner（10）

`M` 为镭射标记倍率：未标记为 1、已标记为 1.5。当前实现只把射束的固定基础伤害乘 `M`，最大生命百分比部分不乘。

| 技能 | 等级 / 开发能量 | 直接前置 | 默认按键 | CP / 资源 | 迭代 / 堆叠 | 效果与数值 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 粒机波形高速炮 `single_high_speed_electron_beam` | L1 / 5k | 无 | `Alt+鼠标左键↓` | 15 | 10 / ∞（20） | 从随机始发点发射延迟 10 tick 成形的 50 格窄射束，伤害 `16MAD+1%Hmax`；弹道向准星目标校正，精准度随熟练度由 50% 提升至 100%，近距离始发散布自动收紧至 2° 直射锥。 | `SingleHighSpeedElectronBeam` |
| 镭射强化 `radiation_intensify` | L1 / 5k | `single_high_speed_electron_beam` | 无 | 0 | — / ∞（∞） | 被动：电子束命中会标记目标 200 tick 并施加短暂虚弱与缓慢；后续兼容射束的固定基础伤害 ×1.5（最大生命百分比部分不乘）。 | `RadiationIntensify` |
| 采矿光束 `mining_beam` | L2 / 10k | `single_high_speed_electron_beam` | `M↓ / M↑` | 每 20 tick 20 | 5 / ∞（∞） | 发射最长 48 格的持续采矿光束，每 20 tick 对路径半径 0.6 内实体造成 `12AD`（2000 后半径 0.72），每 3 tick 破坏半径 0.35、采掘等级 4 的方块；3000 后可选自动熔炼、时运 III 或精准采集。 | `MiningBeam` |
| 电子弹散射 `scatter_bomb` | L2 / 10k | `single_high_speed_electron_beam` | 见源码（可配置） | 40 | 20 / ∞（2） | 蓄力 20–80 tick 后依次释放最多 7 束 50 格延迟电子束，每束 `16MAD+1%Hmax`，均可独立命中。 | `ScatterBomb` |
| 光盾 `light_shield` | L3 / 30k | `single_high_speed_electron_beam` | `H↓ / H↑` | 每 2 tick 4 | 5 / ∞（∞） | 按住展开可见光盾并获得抗性提升 II；每 4 tick 对半径 3.5 内敌人造成 `3AD` 并击退。 | `LightShield` |
| 粒子云室 `cloudroom` | L3 / 30k | `light_shield` | `Alt+U↓` | 维持 30 | 永久 / ∞（∞） | 占用 CP，用同步烟迹显示 16 格内生物最近的移动轨迹；每实体每 5 tick 最多 6 条，轨迹寿命 30 tick。 | `Cloudroom` |
| 突击喷射 `jet_strike` | L4 / 60k | `light_shield` | `R↓` | 20 | 10 / ∞（1） | 向视线前方 8 格内的安全位置突进，并在落点半径 3.25 造成 `10AD`。 | `JetStrike` |
| 波形粒子炮 `particle_wave_cannon` | L4 / 60k | `scatter_bomb` | `C↓ / C↑` | 启动 10；维持每 2 tick 10 | 15 / ∞（∞） | 蓄力 25 tick 后维持最长 85 格的宽幅射束，持续破坏路径方块；每 10 tick 造成 `40MAD+1%Hmax`，破坏半径 0.6、采掘等级 4。 | `ParticleWaveCannon` |
| 自动巡航光束炮 `auto_cruise_beam_cannon` | L5 / 100k | `scatter_bomb` | `Y↑` | 维持 50；每发 10 | 永久；每发 5 / ∞（∞） | 占用 CP 每 10 tick 扫描 16 格范围，自动锁定敌对目标并最快每 2 tick 发射一束 `10MD+1%Hmax` 延迟电子束。 | `AutoCruiseBeamCannon` |
| 解离射线 `disintegrate` | L5 / 100k | `particle_wave_cannon` | `Alt+Shift+K↓` | 100 或最大 CP 的 20% 取高 | 20 / ∞（2） | 发射最长 30 格的解离射线，对路径上每个实体独立造成 `5%Hmax+75D`（辐射标记仍强化基础段）并破坏允许处理的路径方块；击杀后最多散射 3 束，二段击杀可再散射 1 束且不继续连锁。 | `Disintegrate` |

## 8. 空间移动 Teleport（15）

`spatial_synergy` 的 CP/半径与 `spacial_excision` 的命名口径见 13.3。

| 技能 | 等级 / 开发能量 | 直接前置 | 默认按键 | CP / 资源 | 迭代 / 堆叠 | 效果与数值 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 危险传送 `threatening_teleport` | L1 / 5k | 无 | `Alt+鼠标左键↓` 预览 / `Alt+鼠标左键↑` 施放；HUD 选中后 `C↓ / C↑` | 10 + 1 个主手物品 | 5 / ∞（10） | 将一件主手物品传送进 64 格内的生物体内，造成 `(4+武器攻击加成)AD`；空放时物品在视线前方 16 格掉落，击杀时随目标死亡掉落返还，未击杀时消耗。 | `ThreateningTeleport` |
| 空间折叠理论 `space_folding_theorem` | L1 / 5k | `threatening_teleport` | 无 | 0 | — / ∞（∞） | 常驻被动：使威胁传送和肉体撕裂造成的伤害 ×1.25，熟练度档位提高到 1.30/1.35/1.40；满熟练度击杀可返还 20% 实际施放 CP（60 tick 冷却）。 | `SpaceFoldingTheorem` |
| 自我传送 `self_teleport` | L2 / 10k | `threatening_teleport` | `R↓` + 滚轮 / `R↑` | 去程 10；返程 5 | 5 / ∞（20） | 默认搜索 40 格、最大 64 格，传送到首个方块阻挡前或更近的安全落点；可返回上次起点。 | `SelfTeleport` |
| 穿透传送 `piercing_teleportation` | L2 / 10k | `self_teleport` | `Alt+R↓` + 滚轮 / `Alt+R↑` | 15 | 5 / ∞（20） | 默认显示 40 格内第一处阻挡后的安全落点；滚轮从当前预览位置切换为 64 格内自由调距，可选择未穿墙的安全位置。 | `PiercingTeleportation` |
| 空间协同 `spatial_synergy` | L2 / 10k | `self_teleport` | `X↓` | 维持 20（1000 后 ×0.9）；携带非玩家实体每个 10 CP，玩家不另收费 | 永久；非玩家携带笔 5 / ∞（∞） | 占用 20 CP；自己传送时把身边 4 格内的其他存活队友一同传送到目标位置，2000 后半径 6，3000 后还可携带同盟或驯服的非玩家实体。 | `SpatialSynergy` |
| 缴械传送 `disarm` | L2 / 10k | `self_teleport` | `Alt+D↓` | 单手 20；双手 40 | 10 / ∞（5） | 将视线选定生物手中的物品传送出去，并造成 1 点技能伤害；高熟练度可同时取走双手物品。 | `Disarm` |
| 肌体撕裂 `flesh_ripping` | L3 / 30k | `piercing_teleportation` | `Alt+鼠标右键↓ / Alt+鼠标右键↑` | 20 | 10 / ∞（10） | 按住锁定 64 格内的生物，松开后造成 `12AD+5%Hmax`，再受空间折叠倍率影响。 | `FleshRipping` |
| 禁锢传送 `shackle` | L3 / 30k | `self_teleport` | `Alt+Shift+S↓` | 30 | 10 / ∞（1） | 禁锢视线选定目标，造成 3 点技能伤害，并施加强力减速、虚弱和挖掘疲劳；32 格内持续 160 tick，高熟练度非玩家目标 200 tick。 | `Shackle` |
| 位置传送 `location_teleport` | L3 / 30k | `piercing_teleportation` | `L↓` | 去程 40；返程 20 | 10 / ∞（∞） | 打开管理界面，保存、命名和管理最多 32 个位置，并安全传送到选中位置，支持跨维度使用与返回上次起点。 | `LocationTeleport` |
| 快速位置传送 `quick_location_teleport` | L4 / 60k | `location_teleport` | `C↓` | 30 | 15 / ∞（15） | 将准星指向的 32 格内实体传送到当前选中的已保存位置；没有有效目标时传送自己。 | `QuickLocationTeleport` |
| 区域传送 `area_teleport_select` | L4 / 60k | `location_teleport` | `Y↓` 标角；`Alt+Y↓` 设目标；`Shift+Y↓` 启动；高熟练度 `Shift+Alt+Y↓` 变换、`Ctrl+Alt+Y↓` 交换 | 50 | 20 / ∞（∞） | 在 80 格服务端射线范围内选择源区与目标（每轴基础上限 32 格），事务搬移方块、方块实体和非玩家实体，失败回滚；支持变换与交换模式。 | `AreaTeleportSelect` + 动作处理类 |
| 高速闪现 `flashing` | L5 / 100k | `location_teleport` | `H↓` | 维持 50；每次 5 | 永久；每次 5 / ∞（∞） | 开启后占用 CP；按住移动方向时沿该方向反复安全闪现 8 格，客户端每 6 tick 可重复。 | `Flashing` |
| 防御传送 `defensive_teleport` | L5 / 100k | `quick_location_teleport` | `Alt+G↑` | 每次 20 | 10 / ∞（20） | 开启后占用 CP，按住框选前方 5×5×5 区域，松开后把其中敌对生物和来袭投射物传送到位置传送的当前坐标。 | `DefensiveTeleport` |
| 区块跃迁 `chunk_leap` | L5 / 100k | `area_teleport_select` | `Alt+C↓` | 基础 100 CP + 每区块 4 CP + 每实体 30 CP；单体传送 30 CP | 20 / ∞（∞） | 打开可缩放、可拖动的跨维度区块地图，框选两个等大的区块区域交换方块、方块实体、实体与生物群系；同维交换玩家随区块无缝位移，跨维度按绝对 Y 重叠带交换并用轻量传送画面替代加载界面；也可把选中生物传送到地图落点。 | `ChunkLeap` |
| 空间切割 `spacial_excision` | L5 / 100k | `area_teleport_select` | `Alt+X↓` | 100 CP | 20 / 1（声明 1；独立运行态） | 激活后持续 600 tick（30 秒）；期间自身每次成功完成同维传送时，在起点至终点之间留下世界空间裂缝；裂缝生成后前 20 tick 对 3×3 轨迹内每个目标追加一次 40 点空间伤害，同时每 10 tick 造成 12 点空间伤害，并以最高 1.10 的速度吸引 7×7 内非友方生物；无切块或熟练度强化。 | `SpacialExcision` |

## 9. 未元物质 Darkmatter（9）

本系用 `α`、`β`、`γ` 表示相态强度（每 50 相态点为 1 单位）：`α+β=能力等级`；六翼开启时额外获得独立且满额的 `γ=能力等级`，否则 `γ=0`。基础 MP 容量为 `100+8×能力等级²`，自然恢复 1 MP/s。`darkmatter_creation` 的开发能量在 CSV 中为 0（源码 `energyCost(0)`），施放成本为每只召唤物的 MP 与永久 CP 占用。

| 技能 | 等级 / 开发能量 | 直接前置 | 默认按键 | CP / 资源 | 迭代 / 堆叠 | 效果与数值 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 未元物质创造 `darkmatter_generation` | L1 / 5k | 无 | `G` 按住（创造）/ `Alt+G` 按住（抹消） | 创造每 1 MP 永久占用 2 CP（2000 后 1.8）；消去不新增；基础 MP 上限 `100+能力等级²×8`，自然恢复 1 MP/s | 创造永久；消去 20（2000 后 16）/ ∞（∞） | 按住主键逐 tick 创造额外 MP（每秒基础速率为基础上限的 1/10，受 `α`/`β` 与 `γ` 加成，`g` 基础 0.10、3000 后 0.15，1000 后总量 ×1.15），副键实时抹消；创造的 MP 消耗后由永久占用转为限时迭代。 | `DarkmatterGeneration` |
| 未元物质塑形 `darkmatter_shaping` | L1 / 5k | `darkmatter_generation` | `U↑` | 材料 1 MP；装备塑形按形制与词条计费：工具/剑/矛 4、三叉戟 6、弓 5、弩/锤 6、16 支箭 4、整套护甲 16、覆层 3、方块 8，每模块 +0.5（1000 后 ×0.9） | 20 / ∞（∞） | 正常游戏中打开塑形编辑器，在物品栏内指向可放置的空槽位时生成未元物质材料，其他界面操作无效；可塑工具/剑/矛/三叉戟/弓/弩/锤/箭/护甲/覆层/方块并写入 `α`/`β` 与模块。直接伤害基值 6/7/5/8/3/1 再加 `2α`，穿甲 `min(50%,10%β)`（其余 `min(40%,8%β)`）；原生装备寿命 10 分钟（2000/3000 后 12/15），3000 后 γ 塑形 ×1.25。 | `DarkmatterShaping` |
| 未元分解 `darkmatter_disassemble` | L2 / 10k | `darkmatter_shaping` | `Alt+鼠标左键↑` | 20 CP（1000 后 18） | 5（2000 后 4）/ ∞（20） | 32 格射线命中实体时在半径 `1+2α` 内造成 `(4+2α)AD`，穿甲 `min(50%,10%β)` 并腐蚀 `40+20β` tick；先引爆 β 标记可追加 `1+0.8β` 直接伤害。方块硬度上限 `10+10α`，同类连锁 `1+2floor(α)`；六翼时另解构半径 `1+γ` 球区。 | `DarkmatterDisassemble` |
| 相态调谐 `darkmatter_phase_tuning` | L2 / 10k | `darkmatter_shaping` | `J` 按住（α）/ `Alt+J` 按住（β） | 0（调谐不消耗资源） | — / ∞（2） | 主键连续向 α 调谐、副键向 β 调谐；只在 α/β 共享池内重新分配，γ 独立且默认始终满值；每 tick 移动 `总点数/200×(1+0.25×熟练度档位)`，基线 10 秒调满一端。 | `DarkmatterPhaseTuning` |
| 未元物质切割 `darkmatter_cut` | L3 / 30k | `darkmatter_disassemble` | `R↑` | 3 MP（1000 后 2.7） | 10 / ∞（10） | 释放前方锥形斩击（普通半径 8、`12AD`；六翼 24 格、`16AD`），源码按半径 `5+α`、半角 `30°+6°α`、伤害 `(6+2α)×max(0.6,1-0.08β)AD` 结算，穿甲 `min(50%,10%β)`；2000 后范围与角度 ×1.15，满熟练度命中后延迟 6 tick 追加 50% 伤害。β 标记持续 `40+20β` tick（2000 后 ×1.5）；六翼开启 8 tick 后追加 `0.25+0.10γ` 倍镜像斩击（3000 后延迟 4 tick、倍率 +0.15）。 | `DarkmatterCut` |
| 未元干涉 `darkmatter_interference` | L3 / 30k | `darkmatter_phase_tuning` | `N` 按住 | 2 MP/s（1000 后 1.8） | 10 / ∞（∞） | 按住持续释放并每 5 tick（2000 后 4）结算一次：α 聚束造成 `(0.5+0.4α)AD`，β 扩散造成 `(0.25+0.25β)AD`；β 暴露每 20 tick（3000 后 15）爆发 `(1+β)AD`。每秒另发射 `1+floor(α/2)` 枚 `(0.75+0.35α)AD` 羽刃，六翼时追加 `2+round(γ)` 枚 `(0.5+0.2γ)AD` γ 羽刃（3000 后 +2）。 | `DarkmatterRadiation` |
| 未元修复 `darkmatter_repair` | L4 / 60k | `darkmatter_interference` | `Alt+U↑` | 每个有效脉冲 1 MP（1000 后 0.8） | 15 / ∞（∞） | 开启持续自我修补，只在生命未满并实际恢复生命时消耗；每秒脉冲：α 修复至多 `1+[γ>0]+[满3000]` 件原生装备的 `1%+0.5%α` 完整度并授予至多 `2+2α` 吸收，β 治疗 `0.5+0.5β` 并缩短负面 `20+10β` tick。2000 后修复/治疗/缩短 ×1.25，3000 后每 5 个有效脉冲移除一个负面效果。 | `DarkmatterRepair` |
| 未元创生 `darkmatter_creation` | L4 / 0 | `darkmatter_repair`、`darkmatter_cut` | `G↑` | 每只一次性消耗蓝图投入 MP；每只永久占用 40 CP | 永久；生成释债 15 / ∞（∞） | 召唤最多 16 只所属未元物质独角仙协助战斗，看向自己的独角仙再次施放可解散；每只投入 5 至 `25×能力等级`（步长 5），`S=有效投入/5`（1000 后 ×1.1）。基础生命/攻击/护甲/移速/跟随范围为 `8+2S`、`2+0.4S`、`min(20,0.5S)`、`0.2+0.004S`、`16+S`，近战乘 `(1+0.16α头)`、远程乘 `(0.75+0.20α头)` 并乘 `AD`；2000 后跟随/索敌 ×1.2，3000 后模块 ×1.25。 | `DarkmatterCreation` |
| γ态·六翼 `darkmatter_six_wings` | L5 / 100k | `darkmatter_creation` | `Alt+R↑` | 开启 10 MP（3000 后 5）；永久占用 `max(120,35%最大CP)`（1000 后 30%） | 永久（声明 20）/ ∞（∞） | 开启独立满额 γ 相并飞行，速度 `0.05×(1+0.08β)`（2000 后 ×1.15）；对有实体来源的来伤减免 `min(20%,4%α)`，近战每目标每 10 tick 追加 `1+α` 技能伤害；低于 2 点的来伤可花 CP 免疫。2000 后 γ 影响范围 ×1.15，3000 后 γ 效果强度 ×1.2。 | `DarkmatterSixWings` |

## 10. 心理掌握 Mentalout（11）

以下均列服务器默认配置值。受控清单中的呆然/印象占用对 Boss 默认 ×2、对玩家默认 ×3；其他技能是否应用目标倍率以各行说明为准。精密操作已是分类级系统入口（Level 5 解锁），不再注册为独立技能；运行时实现为 `PrecisionOperationRuntime`，界面与节点目录见 `docs/precision_operation_text_nodes.md`。

| 技能 | 等级 / 开发能量 | 直接前置 | 默认按键 | CP / 资源 | 迭代 / 堆叠 | 效果与数值 | 实现类 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 心灵介入 `mental_intervention` | L1 / 5k | 无 | 见源码（可配置） | 每次加入 10 | 5 / ∞（∞） | 将 16 格内兼容生物或服务器允许的玩家加入/移出受控清单。 | `MentalIntervention` |
| 心灵潜入 `mental_intrusion` | L1 / 5k | `mental_intervention` | 见源码（可配置） | 维持 20/17/14 | 永久 / ∞（∞） | 观察受控目标的感知而不移动或直接控制它；技能级 0/1/2 范围 16/24/32，玩家观察上限移除。 | `MentalIntrusion` |
| 目标误认 `target_misidentification` | L1 / 5k | `mental_intervention` | 见源码（可配置） | 每次设置 40 | 10 / ∞（∞） | 令受控兼容生物永久把指定实体视为敌人；再次选择解除。 | `TargetMisidentification` |
| 呆然自失 `mental_stupor` | L2 / 10k | `target_misidentification` | 见源码（可配置） | 每目标维持 10；Boss ×2、玩家 ×3 | 永久 / ∞（∞） | 占用 CP 冻结受控目标的移动和 AI。 | `MentalStupor` |
| 感官扭曲 `sensory_distortion` | L2 / 10k | `mental_intrusion` | 见源码（可配置） | 维持 30/25/21；Boss ×2、玩家 ×3 | 永久 / ∞（∞） | 潜入期间屏蔽目标对施术者的视线、仇恨记忆、渲染和直接交互感知。 | `SensoryDistortion` |
| 指挥定位 `command_positioning` | L3 / 25k | `mental_intervention` | 见源码（可配置） | 每目标 10；Boss ×2 | 10 / ∞（10） | 命令受控实体前往准星方块外侧或跟随准星实体，最远 64 格。 | `CommandPositioning` |
| 印象操作 `impression_manipulation` | L3 / 30k | `mental_intervention`、`target_misidentification` | 见源码（可配置） | 每目标维持 10；Boss ×2、玩家 ×3 | 永久 / ∞（∞） | 让受控目标把施术者和同清单目标视为盟友，并抑制自然仇恨。 | `ImpressionManipulation` |
| 意识接管 `mental_takeover` | L4 / 60k | `mental_intrusion`、`command_positioning`、`mental_stupor` | 见源码（可配置） | 维持 100 | 永久 / ∞（∞） | 在心灵潜入期间接管清单目标的移动、视角和攻击；玩家目标可挣扎解除。 | `MentalTakeover` |
| 痛觉消减 `pain_suppression` | L4 / 60k | `sensory_distortion` | `Alt+J↑` | 开启占用 40 CP；每次最终伤害 >2 时消耗等量 CP，单次上限 20，CP 不足自动关闭 | 永久；受伤笔 10 / ∞（∞） | 开启时占用 40 CP 并获得 8 点真实抗性；每次最终受到伤害大于 2 点时按伤害量消耗 CP（单次最多 20），CP 不足时自动关闭。 | `PainSuppression` |
| 心智破坏 `mind_destruction` | L5 / 100k | `sensory_distortion` | `Alt+H↓` | 施放 100（熟练度档位 100/90/80/70） | 20 / 1（1） | 攻击 64 格内准星所指目标，持续 10 秒，每秒造成 `1%Hmax+10` 心智伤害；清单内目标额外尝试施加呆然自失，精神防护只阻止呆然自失、不阻止伤害。即将击杀有精神防护或抗性的目标时保留 1 点生命（不足 1 点时不治疗），剥离精神免疫与抗性并结束本次效果，剥离持续至死亡或离开当前世界。 | `MindDestruction` |
| 广域干涉 `wide_area_interference` | L5 / 100k | `impression_manipulation`、`mental_stupor` | `=↓` | 0（界面入口）；群体命令成本由对应技能结算 | —（声明 10）/ ∞（∞） | 打开广域干涉界面：受控清单与心灵介入、精密操作双向同步；可同时查看最多 9 个目标视角，或在 RTS 上帝视角中选取目标、隐藏方块并下达强制移动、目标误认、呆然自失、印象修改、资源采掘与持续耕作命令；服务端复核目标与区域（可批量登记 96 格内最多 64 个目标）。 | `WideAreaInterference` |

## 11. 前置关系总览

以下只列直接前置链，完整学习依赖沿箭头递归；以 `Skills.Builder.dependsOn(...)` 为准，前置列省略 `academy:` 命名空间。共 99 个技能，其中 89 个具有直接前置，10 个为无前置根节点。开发机连线会在客户端技能注册完成后统一从 `dependsOn(...)` 重建；手写 `SkillInfo` 只保留纹理和坐标，缺失节点使用自动回退。

- 通用 Level 0：`brain_domain_development` → `multiple_brain_domain_segmentation` → `parallel_thought_computation` → `complete_consciousness_analysis` → `absolute_self_control`；`brain_domain_development` → `output_control`；`endurance_training`、`physical_training` 无前置。
- 气动：`airflow_jet` → `laminar_buffer`；`airflow_jet` → `flow_sense` → `pneumatic_grasp` → `laminar_cutter`；`pneumatic_grasp` → `tailwind_field` → `rejecting_wind` → `vortex_pull` → `adiabatic_compression`、`vacuum_domain`；`flow_sense` → `breathing_bubble` → `turbulent_cavitation`、`atmosphere_shield`；`airflow_jet` → `high_speed_jet` → `flight`。
- 矢量：`vector_accel` → `vector_blast` → `dir_strike`；`vector_accel` → `kinetic_energy_applied` → `kinetic_throw`；`kinetic_energy_applied` → `vector_deviation` → `vector_reflection` → `reflection_filter`、`bloodflow_reverse`、`storm_wing`；`storm_wing` → `plasma_generation`、`black_wing`；`black_wing` → `white_wing` → `platinum_wing`、`crossing_the_abyss`。
- 电击：`arc_generate` → `electrical_contact` → `bioelectric_operation`；`arc_generate` → `thunder_lance` → `railgun`、`lightning_nova` → `ball_lightning` → `lightning_storm`、`thunderclap`；`arc_generate` → `magnet_manipulation` → `magnetic_weapon`、`current_recharge` → `current_symbiosis`、`mine_detect`、`electromagnetic_shield`、`iron_sand_arsenal`。
- 原子崩坏：`single_high_speed_electron_beam` → `radiation_intensify`、`mining_beam`、`scatter_bomb`、`light_shield`；`light_shield` → `cloudroom`、`jet_strike`；`scatter_bomb` → `particle_wave_cannon` → `disintegrate`、`auto_cruise_beam_cannon`。
- 空间：`threatening_teleport` → `space_folding_theorem`；`threatening_teleport` → `self_teleport` → `disarm`、`piercing_teleportation`、`spatial_synergy`、`shackle`；`piercing_teleportation` → `flesh_ripping`、`location_teleport`；`location_teleport` → `quick_location_teleport`、`area_teleport_select`、`flashing`；`quick_location_teleport` → `defensive_teleport`；`area_teleport_select` → `chunk_leap`、`spacial_excision`。
- 未元物质：`darkmatter_generation` → `darkmatter_shaping` → `darkmatter_disassemble` → `darkmatter_cut`；`darkmatter_shaping` → `darkmatter_phase_tuning` → `darkmatter_interference` → `darkmatter_repair`；`darkmatter_repair` + `darkmatter_cut` → `darkmatter_creation` → `darkmatter_six_wings`。
- 心理掌握：`mental_intervention` → `mental_intrusion` → `sensory_distortion` → `pain_suppression`、`mind_destruction`；`mental_intervention` → `target_misidentification` → `mental_stupor`；`mental_intervention` → `command_positioning`；`mental_intrusion` + `command_positioning` + `mental_stupor` → `mental_takeover`；`mental_intervention` + `target_misidentification` → `impression_manipulation`；`impression_manipulation` + `mental_stupor` → `wide_area_interference`。

## 12. 全局按键与冲突

全局控制的默认值如下；所有默认键都可在 `config/academy-client.json` 覆盖。

| 功能 | 默认按键 | 说明 |
| --- | --- | --- |
| 能力 HUD | `V↓` | 打开或关闭技能 HUD |
| HUD 上一技能 | `↑↓` | 技能轮盘向上 |
| HUD 下一技能 | `↓键↓` | 技能轮盘向下 |
| 数据终端 | `右 Alt↓` | 打开数据终端及设置应用 |

默认按键冲突分级如下。P0/P1 是发布前应处理的默认绑定问题，不代表运行时一定会同时触发，实际行为还取决于输入消费顺序。

| 优先级 | 冲突/问题 | 影响 | 建议 |
| --- | --- | --- | --- |
| P0 | 电击使：`electrical_contact` 与 `current_recharge` 都以 `H↓` 启动 | 同一按下动作可能切换接触电击并开始电流充能 | 为其中一个技能更换默认绑定，并在客户端验收按下/松开事件 |
| P1 | 电击使：`ball_lightning` 为 `Y↓`，`current_symbiosis` 为 `Y↑` | 一次完整按键可能施放球状闪电并切换电流共生 | 为其中一个技能换键，或明确消费输入事件 |
| P1 | 新增和重命名技能仍有“见源码”按键项 | 表格不能独立用于默认键冲突审计 | 后续从统一输入注册表导出默认键，避免手工维护 |
| P1 | 高伤害、方块修改、区域传送和玩家控制技能 | 数值统一前仍需服务端权威、权限与多人生命周期验收 | 按 `docs/structure_skill_migration_handoff.zh-CN.md` 的验收门禁完成客户端和双玩家专服验证 |

## 13. 维护约定

### 13.1 唯一维护入口

- 枚举型信息（注册 ID、中文名、分类、等级、开发能量、直接前置、默认按键、实现类）只在本表维护；旧的使用说明、控制矩阵、效果总表和前置清单不再单独更新。
- 修改 `Skills` 注册表或 `Skill.Builder` 后同步更新本表：从 `Skills.java` 与各技能类重新导出（等级取 `.level(AbilityLevel.LEVELn)`，不要用包名 `lvN`；开发能量取 `.energyCost(...)`；前置取 `dependsOn(...)`，注意它是可变参数），核对 99 行，并按 8/15/15/16/10/15/9/11 核对各分类行数。
- 数值以源码常量为准：等级、基础 CP、维持占用、迭代与声明堆栈在 `Skill.Builder`；伤害、范围、持续时间、间隔与动态消耗在技能类常量、计算方法及运行时管理器；熟练度成本与迭代修正在 `SkillProficiencyProfiles.java`；出力曲线在 `ProgramPowerScale.java`，出力对 CP、伤害与移动属性的接入在 `OutputControl.java`；Mentalout 默认成本在 `AbilityConfig.MentaloutSettings`；压缩空气与 MP 的容量、恢复、占用和债务由各自资源管理器处理。
- 默认键在各技能 `initClient()` 的 `InputSystem.combo(...)`；玩家覆盖值由统一 `InputSystem` 配置保存。改键后同步第 12 节的冲突表。
- 全局堆栈是否生效由 `Skill.STACK_LIMITS_ENABLED` 控制；在重新启用前，需要先验证多重脑域分割的额外层数与所有动态占用路径。
- 本表不记录临时平衡结论；“修改项”式目标数值应进入对应源码常量或专门的平衡文档，避免与本文的事实基线混淆。

### 13.2 未纳入的旧语言键

以下名称是旧版名称、兼容别名或已移除技能，当前没有在 `Skills.java` 注册，不应出现在能力开发机的现行技能清单中：

`air_cushion`、`assault_jet`、`atmosphere_blast_gun`、`atmospheric_dominion`、`beta_particle_stream`、`brain_development_lv1`、`brain_development_lv2`、`brain_development_lv3`、`brain_development_lv4`、`brain_development_lv5`、`breathing_film`、`clip_through`、`coordinate_teleport`、`cut_through`、`darkmatter_radiation`、`flash_back`、`flow_control`、`hyper_accelerate`、`lightning_spear`、`magnet_moment_charge`、`matter_warp`、`penetrate_teleport`、`phantom_falling`、`precision_operation`、`pressure_lock`、`pulse_charge`、`spacial_replace`、`thunder_clap`、`vector_reduction`、`visual_teleport`、`wind_corridor`。

这些键可能是旧版名称、兼容别名或已被拆分/替换的技能。后续清理语言文件前，应先确认是否仍有配置迁移或旧存档兼容代码引用它们。

### 13.3 当前实现备注

- 能力开发机右侧技能区分为“通用课程”和“能力课程”两页；未学习分支仍未展示用途说明。
- `spatial_synergy` 当前源码占用 20 CP（`maintenanceCost(20)`），生效半径为 4 格（`RADIUS = 4.0f`；熟练度里程碑 2 起扩大到 6 格），且没有按同行玩家额外扣除 CP；旧平衡表中的 50 CP／2 格与逐人扣费说法已不符合当前实现。
- `white_wing` 当前战斗行为与 `black_wing` 基本相同，文案不应暗示尚未实现的额外强化。
- `platinum_wing` 的潜行处决明确排除玩家，只对非玩家生物生效。
- 注册 ID `spacial_excision` 保留了现有拼写，GUI 显示名使用“空间切割”；除非提供数据迁移，不应直接重命名注册 ID。

### 13.4 验收边界

高伤害、方块修改、区域传送和玩家控制类技能在数值统一前仍需服务端权威、权限与多人生命周期验收；按 `docs/structure_skill_migration_handoff.zh-CN.md` 的验收门禁完成客户端和双玩家专服验证。本文只负责静态事实汇总，不替代运行时验收记录。
