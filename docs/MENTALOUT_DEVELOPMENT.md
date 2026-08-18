# Mentalout 心理掌握开发记录

## 基本信息

- 能力 ID：`academy:mentalout`
- 当前里程碑：M4 玩家编队、意识接管与全地形寻路
- 当前状态：`开发中`
- 文档建立日期：2026-08-05
- M0 已交付：Mental Control 公共 API、租约 Runtime、目标误认与呆然自失基础能力
- M1 已交付：受控清单、心灵介入、批量控制、永久效果、左侧 HUD、Boss 与第三方实体兼容
- M2 + M3 目标：心灵潜入、感官扭曲、精密操作节点编辑器、四槽编译与执行
- M4 目标：玩家编队、双客户端输入接管、服务端动作/移动校验和多模态玩家寻路
- 当前启动注册预期：8 个能力类别、92 个技能；下方进展日志中的 89/92 为对应日期的历史快照

状态字段只能使用以下取值：

| 状态 | 含义 |
| --- | --- |
| `未开始` | 尚未进入实现或验证 |
| `开发中` | 正在实现，行为合同可能尚未全部满足 |
| `代码完成` | 实现已写入工作树，但尚未完成自动验证 |
| `自动验证通过` | 对应测试或构建命令已经通过 |
| `运行时验收通过` | 已在实际客户端或专用服务器完成行为验收 |
| `阻塞` | 存在明确且已记录的外部阻塞条件 |

## 目标与安全边界

Mentalout 通过可组合、可撤销的运行时覆盖控制实体行为，不持久化或替换原版 `Goal`、`Brain`、龙阶段图或 Scoreboard Team。

- C2S 只表达施放或重同步意图，不携带可信实体 ID；服务端重新执行 16 格视线射线。
- 符合友伤、旁观者和精神保护规则的其他玩家可加入清单；玩家不写入 Mob 召回记忆。
- `academy:mental_control_immune` 标签、矢量反射、电磁护盾和未元物质六翼统一进入精神控制保护门禁。
- 名单与永久效果仅存在于当前服务器会话，不跨卸载、维度、登出或服务器重启持久化。
- 所有控制句柄和清理操作必须幂等。退出、死亡、过载、换能力、换维度、实体卸载和停服不得遗留租约或 CP occupation。
- Mixin 只执行 O(1) effective-query，不扫描 Adapter、租约或受控清单。

## 里程碑

| 阶段 | 交付结果 | 状态 |
| --- | --- | --- |
| M0 | 控制 API、租约 Runtime、单体目标误认、限时呆然自失 | `自动验证通过` |
| M1 | 受控清单、心灵介入、批量及永久效果、HUD、Boss/第三方 Adapter | `自动验证通过` |
| M2 | 心灵潜入、感官扭曲、观察者视角与感官过滤 | `自动验证通过` |
| M3 | 精密操作节点编辑器、四快捷槽、图校验和服务端编译 | `自动验证通过` |
| M4 | 玩家输入替换、服务端移动包校验、多模态寻路代理和玩家控制 | `开发中` |

## 技能树与合同

| 等级 | ID | 中文名 | 依赖 | 里程碑 | 状态 |
| --- | --- | --- | --- | --- | --- |
| Lv1 | `mental_intervention` | 心灵介入 | 无 | M1 | `自动验证通过` |
| Lv1 | `target_misidentification` | 目标误认 | `mental_intervention` | M1 | `自动验证通过` |
| Lv1 | `mental_intrusion` | 心灵潜入 | 无 | M2 | `代码完成` |
| Lv2 | `mental_stupor` | 呆然自失 | `target_misidentification` | M1 | `自动验证通过` |
| Lv2 | `sensory_distortion` | 感官扭曲 | `mental_intrusion` | M2 | `代码完成` |
| Lv3 | `impression_manipulation` | 印象操作 | `mental_intervention`、`target_misidentification` | M1 | `自动验证通过` |
| Lv3 | `command_positioning` | 指挥定位 | `mental_intervention` | M1 | `自动验证通过` |
| Lv4 | `mental_takeover` | 意识接管 | `mental_intrusion`、`command_positioning`、`mental_stupor` | M4 | `开发中` |
| Lv5 | `precision_operation` | 精密操作 | `impression_manipulation`、`mental_stupor` | M3 | `代码完成` |

### 心灵介入

- 默认按键：`Alt+C`，绑定名 `mental_intervention_use`。
- 开发消耗：5000；成功加入普通目标消耗 10 CP。
- 服务端重新射线选择 LivingEntity，并执行自身、旁观者、友伤、免疫、距离、存活、维度和 Adapter 支持校验。
- 玩家可同时位于多个施术者的编队，但移动、视角和动作领域始终由单个最高优先级租约占用。
- 未在名单中的有效目标加入当前施术者的有序清单；再次施放同一目标时免费移除。
- 目标死亡、卸载或换维度时立即移除。名单不设玩法数量上限。
- 若全局永久效果已经开启，新目标只有在 CP 聚合占用可原子扩容且对应 Adapter 激活成功后才能加入。

### 目标误认

- 默认按键：`Alt+R`，绑定名 `target_misidentification_use`。
- 单次服务端射线选择错误目标，对名单中支持 `FORCE_TARGET` 的存活目标建立永久租约；成功批次只在建立时固定消耗 40 CP，不占用 CP 上限。
- 当前施术者同一时间只保留一个误认批次。选择不同目标会以新批次替换旧批次；至少一个名单成员成功建立控制后才扣除 40 CP。
- 再次选择当前错误目标时免费解除整个批次，不重复扣除 CP。
- 错误目标死亡、卸载或换维度时立即解除整个批次；名单成员移除时只关闭该成员的误认句柄。
- 不支持或已丢失的名单项被跳过；新加入名单的目标不继承已经存在的误认批次。
- 精确 `ForceTarget` 攻击许可高于印象关系，可使同一受控清单内的实体互相误认为敌人。

### 呆然自失

- 默认按键：`Alt+G`，绑定名 `mental_stupor_use`。
- 技能为名单级永久开关；再次施放关闭并释放全部该技能 occupation。
- 普通目标占用 30 CP，Boss 目标占用 60 CP；Boss 权重由 `academy:mental_control_boss_cost` 实体类型标签声明。
- 激活后冻结所有支持目标的移动与行动。目标仍处理重力、火焰、伤害、击退和状态效果。
- 开关已开启时，新加入名单的目标自动继承；移除单个目标时同步缩减聚合 CP 占用。

### 印象操作

- 默认按键：`Alt+H`，绑定名 `impression_manipulation_use`。
- 开发消耗：30000；技能为名单级永久开关。
- 普通目标占用 20 CP，Boss 目标占用 40 CP。
- 受控目标将施术者以及同一施术者清单内的其他目标视为盟友，不修改真实 Team。
- 印象生效时启用仇恨白名单：除自身受击反击、施术者受击或成为其他实体仇恨目标时的护主敌意、显式目标误认外，拒绝原版与第三方 AI 新建其他仇恨目标。
- 自身受击与施术者受击只在 `LivingDamageEvent.Post` 确认伤害序列实际完成后授权，避免被取消或被无敌帧丢弃的攻击伪造反击/护主；施术者成为实体最终仇恨目标时也会触发护主。
- 普通目标、Brain 目标、`NeutralMob` 持久愤怒、Brain `ANGRY_AT`/`UNIVERSAL_ANGER` 与 Boss 私有仇恨状态均持续复核；`HURT_BY_ENTITY` 作为真实受击证据保留。
- 显式 `ForceTarget` 始终高于印象关系和仇恨白名单，可精确授权目标误认；同名单成员被误认驱动去攻击施术者时，其他成员仍可护主。
- 每个护主目标绑定产生它的印象 relation lease ID；关系替换、关闭或过期时立即撤销派生目标和旧反击授权，并由原生 AI 恢复选敌。
- 关闭技能、移除目标或生命周期清理时撤销虚拟关系并调整聚合 CP 占用。

### 心灵潜入

- 默认按键：`Alt+V`，开发消耗 5000；三级射线范围为 12/14/16 格，维持占用为 20/17/14 CP。
- C2S 只携带递增请求序列。服务端重新执行视线、范围、维度、存活、旁观者、友伤和精神保护校验。
- S2C 会话包含 UUID、revision、实体 ID 与目标 UUID；客户端只在目标已追踪且 UUID 匹配时切换相机，并回传 ready。
- Mob 可持续观察；玩家目标最长 60/80/100 tick，同一玩家目标有 200 tick 冷却。
- 观察期间不转发移动或输入，客户端关闭准星目标、攻击、使用与采掘；原始相机实体和视角模式在所有退出路径幂等恢复。

### 感官扭曲

- 默认按键：`Alt+B`，开发消耗 10000，依赖心灵潜入；三级维持占用为 30/25/21 CP。
- 普通施放只让当前心灵潜入目标无法感知施术者，不扩大 M1 受控清单。
- Mob 路径覆盖 Sensing、自然目标、Brain 可见实体和仇恨记忆、Warden 振动以及 Wither/Dragon 特殊感知。
- 玩家路径过滤实体渲染、准星命中、直接攻击和实体交互；每个隐藏目标保留客户端 revision 墓碑，迟到旧包不会复活已解除关系。
- 感知句柄按来源独立计数，任一来源关闭不会覆盖其他来源；显式目标误认高于感知隐藏。

### 精密操作

- 默认按键：`Alt+P` 打开编辑器，`Alt+1..4` 执行四槽；开发消耗 100000，依赖印象操作和呆然自失。
- 持久化数据固定四槽，包含 schema version 与 revision；编译缓存只存在于当前服务端会话。
- 图上限为 16 KiB、32 节点、48 条数据连接和 31 条流程连接；服务端依次执行有限值、稳定 wire ID、节点/端口、类型、必需输入、单输入、流程单链、无环和规范化验证。
- 数据端口固定为 `ENTITY`、`ENTITY_SET`，行动顺序使用独立 `FLOW` 端口；43 个节点覆盖目标来源、集合操作、过滤、心灵行动和实体控制，不支持循环、任意 UUID、坐标、命令或客户端目标。
- 三级行动主体上限为 16/32/64；目标、观察对象和目的地引用不占主体名额。持续时间为 80/140/200 tick；同槽再次执行取消，不同槽可并行。
- 目标数据支持生物、全部实体、掉落物与弹射物集合，并可将实体转为固定位置、计算两位置方向和沿方向偏移位置；范围节点留空时默认 32 格。
- `PATH_TO` 与 `VIEW_CONTROL` 可把施术者自身作为主体；施术者的移动、转向、攻击或使用输入会正常中断自身效果。
- 所有槽聚合为一个精密操作 occupation，动作使用高于 M1 的优先级并在任一步失败时逆序关闭句柄、恢复原 occupation。
- 编辑器使用响应式侧栏和 80 像素紧凑节点，支持四槽状态、分类滚动搜索、统一缩放平移、拖拽/吸附/重连、兼容节点快速插入、自动排布、参数检查、撤销重做、保存和恢复服务端版本。

### 意识接管与玩家寻路

- `academy:mental_takeover` 为 Lv4 单实例技能，开发消耗 60000、迭代间隔 10 tick，默认按键 `Alt+Y`，技能树坐标 `(164,112)`。
- 只有当前心灵潜入目标仍是施术者编队内的 `ServerPlayer` 时才能升级接管；建立前原子预占 100 CP，并创建优先级 300 的 `DIRECT_CONTROL` 租约。
- 控制端与目标端必须在 20 tick 内完成同一 session UUID/revision 的 `Ready`。任一端拒绝或超时会关闭租约并完整释放 occupation。
- C2S 输入只含 session、revision、递增 sequence、量化移动轴、视角与动作位；目标实体和攻击目标均由服务端会话索引及被控玩家视角重射线确定。
- 控制端每游戏 tick 最多提交一帧；5 tick 无帧下发中立输入，20 tick 无帧终止。目标移动包复核有限值、授权视角、单包速度和垂直模式，连续三次非法移动会纠正并结束会话。
- 接管覆盖移动、视角、跳跃、潜行、疾跑、实体攻击和当前主手使用；来自目标客户端的攻击、使用、采掘、丢弃、热栏切换包在接管期间被拒绝，聊天与菜单仍由目标本人使用。
- 目标的原始硬件方向切换及跳跃/潜行按下边沿每 tick 最多增加 2 点挣脱值；10 tick 无挣扎后每 tick 衰减 1 点，达到 100 结束接管。控制端实际承受生命伤害也会结束接管。
- 接管、玩家呆然或玩家自动寻路结束后获得默认 400 tick 输入控制抗性；抗性只清理移动、视角和动作租约，编队与印象关系保留。
- `PlayerNavigationApi` 支持第三方按 ID 与优先级注册适配器。内置适配器使用每路径 4096 节点、每 tick 每路径 256 展开、全服 1024 展开的有界 A*。
- 内置节点覆盖 WALK、JUMP、DROP、CLIMB、SWIM、FLY、GLIDE、BOAT、MOUNT 和 RAIL；所有移动通过授权输入或技能速度完成，不传送玩家。
- 地面路径校验玩家或当前载具包围盒、头顶空间、落点和最多三格安全落差；升高节点只在着地及跳跃冷却允许时发送跳跃。
- 梯子/藤蔓/脚手架、水域升降、已滑翔鞘翅、船、可控坐骑和矿车使用对应模式；当前载具不受支持时返回 `UNSUPPORTED_MOVEMENT_MODE`，不会自动上下车。
- FLY 路径可开启目标已学习且可负担的 Flight、Storm Wing、Black/White/Platinum Wing，并只关闭本次导航开启的状态；未元物质六翼不在候选列表。
- 路径可通过目标服务端 `gameMode` 交互打开门、栅栏门和活板门；不会破坏、放置、传送或操作红石。

## Runtime 与公共 API

- `MentaloutControlContext` 按施术者维护有序名单、当前永久误认批次、名单全局开关、每目标句柄和两项聚合 CP occupation。
- Runtime 同时维护 `controller -> subjects` 与 `subject -> controllers` 索引。多个施术者可以控制同一目标，最终效果仍按领域优先级和最新租约解析。
- `ControlRequest.permanent(...)` 使用 `Long.MAX_VALUE` 表示由句柄关闭终止的租约，不改变已有绝对结束 tick 合同。
- 控制领域包含独立 `VIEW`；新增 `DIRECT_CONTROL(MOVEMENT, VIEW, ACTION)`、`DirectControl`、统一 `PlayerControlFrame` 与玩家导航扩展点。
- Adapter v2 返回 `FULL`、`BEST_EFFORT` 或 `UNSUPPORTED`，并通过 binding 的 `activate/tick/close` 管理实体专用行为。
- 同一 capability 下不同 ID 的最高优先级 Adapter 并列时返回 `AMBIGUOUS_ADAPTER`，不得依赖注册或模组加载顺序。
- 动态 CP 调整必须先预检新增施放费和两个永久效果的目标权重总额，再原子替换 occupation；失败时旧名单、旧效果和旧 CP 占用保持不变。
- `MentalPerceptionApi.perceptionDecision(observer, target)` 是只读公共查询；外部模组不能通过该 API 建立感知句柄或发起施放。
- `MentalIntrusionManager`、`MentalPerceptionRuntime` 与 `PrecisionOperationRuntime` 共用 M1 生命周期事件，在死亡、卸载、换维度、登出、技能禁用和停服时统一清理。

## 注入与实体兼容矩阵

| 目标 | Target | Freeze | Relation | 实现合同 |
| --- | --- | --- | --- | --- |
| 普通原版 `Mob` | `BEST_EFFORT` 或 `FULL` | 按 capability | `FULL` | 维护 `setTarget` 与 Brain `ATTACK_TARGET`，不替换 Goal/Brain |
| 普通第三方 `Mob` | `BEST_EFFORT` | 按原版 AI 路径 | `FULL` | 通用 Adapter 不静态引用第三方类 |
| Warden | `FULL` | `FULL` | `FULL` | 维护 Brain 目标与虚拟愤怒许可，清理非法 active suspect 与 `ROAR_TARGET`；冻结时抑制新增振动感知 |
| Wither | `FULL` | `FULL` | `FULL` | 同步主头及侧头目标；出生无敌阶段拒绝冻结且不扣 CP |
| Ender Dragon | `FULL` | `FULL` | `FULL` | 误认切入扫射阶段；冻结切入悬停并抑制接触攻击，释放后回到盘旋阶段 |
| 玩家 | `UNSUPPORTED` | `FULL` | `FULL` | 可入列；另支持 `PATH_CONTROL`、`VIEW_CONTROL`、`DIRECT_CONTROL`，首版跳过 Force/Guard |
| 免疫标签实体 | `UNSUPPORTED` | `UNSUPPORTED` | `UNSUPPORTED` | 稳定返回免疫拒绝原因 |

第三方模组可以在 common setup 注册高优先级 Adapter。自定义 AI 绕过原版 `Mob`/Brain 路径时，模组应通过公开 `inspect/effectiveDirective/attackDecision` 查询在自身 tick 或 Mixin 中执行控制。

## 网络与客户端 HUD

- S2C 名单同步使用 revision。完整同步先声明分块数和条目数，每块最多 64 项；收齐并校验后一次性发布不可变快照。
- 增量同步只接受 `currentRevision + 1`。旧包和重复包被忽略，revision 缺口触发携带客户端当前 revision 的 C2S 重同步请求。
- 客户端快照保存 UUID、客户端实体 ID、类型、显示名、生命值、距离、支持等级、效果 flags、永久误认状态和两项 occupation 的 CP 占用。
- `MENTAL_CONTROL` HUD 区域默认位于左侧中部，可在 HUD 布局编辑器中拖动、缩放和重置。
- HUD 仅在当前能力为 Mentalout 且名单非空时显示，按距离展示最近 8 项并用 `+N` 表示隐藏数量。
- HUD 采用 168 像素标称宽度的窄版布局、淡灰高透明度遮罩和行背景、深色高对比文字；名称、状态和数值列不得越界或互相遮挡。
- 每项显示名称、类型、生命条、距离、Adapter 支持等级、永久误认、呆然/印象状态和控制覆盖标记；底部显示呆然与印象两项永久效果占用的 CP。

## 生命周期清理

以下事件统一关闭受影响句柄、调整或释放 CP occupation，并向客户端同步最新名单：

- 目标死亡、卸载、换维度或进入精神控制免疫状态。
- 施术者死亡、登出、换维度、过载或切换能力类别。
- 技能被禁用、服务器停止或 Adapter binding 激活失败。

保护技能在准入时和每个服务端 tick 复核。矢量反射、电磁护盾或未元物质六翼任一激活都会立即撤销玩家输入控制，但保留其编队条目并同步保护状态。

## M0 验收矩阵

| ID | 明确预期结果 | 验证方式 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| `MO-REG-01` | 类别和 M0 技能通过注册校验，开发树与 HUD 无裸翻译键 | 启动检查 | `代码完成` | 等待 M1 注册调整后的完整启动复验 |
| `MO-LEASE-01` | 优先级、同源替换、到期和重复关闭符合合同 | JUnit | `自动验证通过` | 2026-08-05：`MentalControlRuntimeTest` 通过 |
| `MO-TARGET-01` | 僵尸持续攻击显式错误目标，直到按当前误认生命周期解除 | 手工运行 | `代码完成` | 自动测试已覆盖 Runtime 目标维护；等待客户端行为验收 |
| `MO-TARGET-02` | Brain Mob 的 `ATTACK_TARGET` 被维护，解除后恢复原生选敌 | 手工运行 | `代码完成` | 等待客户端行为验收 |
| `MO-STUPOR-01` | 寻路 Mob 在呆然生效时停止行动但仍受环境影响，解除后恢复 | 手工运行 | `代码完成` | GameTest 已覆盖冻结后寻路恢复；环境效果仍待客户端验收 |
| `MO-LIFE-01` | 退出、死亡、换维度、卸载和停服不遗留租约 | JUnit、手工运行 | `代码完成` | 已有 Runtime 清理测试；等待运行时验收 |
| `MO-NET-01` | 伪造、重复或越界施放不能控制实体或重复扣 CP | JUnit | `代码完成` | C2S 不携带实体 ID，请求序列与限流单测通过；仍缺少控制和扣费的一体化测试 |
| `MO-BUILD-01` | M0 测试及开发/发布构建成功 | Gradle | `自动验证通过` | 2026-08-05：三项自动门禁通过 |
| `MO-RUNTIME-01` | 客户端无 Academy Mixin apply、注册和专服类加载错误 | 启动检查 | `运行时验收通过` | 2026-08-05：`runClientDev` 完成 Academy 注册校验（8 类别、89 技能）与客户端资源加载，无 Academy Mixin apply 或专服类加载错误 |

## M1 验收矩阵

新验收项目只有附带实际命令输出、GameTest 结果或手工运行记录后才能更新为通过状态。

| ID | 明确预期结果 | 验证方式 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| `MO-ROSTER-01` | 心灵介入可加入、再次施放移除；死亡与卸载立即清除 | JUnit、GameTest | `代码完成` | 名单与生命周期实现已落盘；仍缺少直接名单 GameTest 证据 |
| `MO-HUD-01` | 窄版淡灰高透明度布局正确展示最近 8 项、`+N`、生命、距离和效果 | 客户端手工 | `开发中` | 窄版布局、客户端状态单测与 Kotlin 编译已完成；截图和布局编辑仍待验收 |
| `MO-BATCH-01` | 误认批次永久生效；同目标重选免费解除，错误目标死亡、卸载或换维度时清除；新成员不继承且成功批次只扣一次 40 CP | JUnit、GameTest | `开发中` | 永久租约与目标死亡场景已加入代码和 GameTest；合同变更后仍待重跑完整门禁 |
| `MO-PERM-01` | 呆然和印象永久开关、新成员继承、移除后释放对应 CP | JUnit、GameTest | `代码完成` | 永久句柄与聚合 occupation 已实现；名单级完整场景仍待 GameTest |
| `MO-CP-02` | 聚合扩缩容原子化，额度不足或 Adapter 异常不破坏旧状态 | JUnit | `代码完成` | CP 原子替换、租约快照回滚与部分领域生命周期单测通过；名单聚合的一体化异常场景仍待补测 |
| `MO-REL-01` | 名单成员与施术者互为盟友，强制误认可精确覆盖关系 | GameTest | `自动验证通过` | 2026-08-05：`relation_force_target_override` GameTest 通过 |
| `MO-REL-02` | 印象生效时，未获授权的原版或第三方自然索敌无法建立仇恨目标 | GameTest、第三方夹具 | `代码完成` | 2026-08-05：普通目标、Brain 目标、持久愤怒与通用愤怒 GameTest 通过；第三方自定义 AI 夹具仍待补充 |
| `MO-REL-03` | 自身受击反击、施术者受击或被锁定时护主、显式目标误认三类授权来源均可建立目标 | JUnit、GameTest | `自动验证通过` | 2026-08-05：严格事件链 GameTest 与 relation/白名单 JUnit 通过 |
| `MO-REL-04` | 关闭印象后派生护主目标清除，原版 AI 可重新自然选敌 | GameTest、手工 | `自动验证通过` | 2026-08-05：relation lease 级联清理与 Zombie 原生选敌恢复 GameTest 通过；客户端手工仍待执行 |
| `MO-PROTECT-01` | 三种防护均返回稳定拒绝原因且不能建立玩家控制 | JUnit、手工 | `代码完成` | 统一保护门禁已接入；M1 玩家仍不可入列，待专项验证 |
| `MO-BOSS-01` | Warden、Wither 三头和 Dragon Phase 分别符合控制合同 | GameTest、手工 | `自动验证通过` | 2026-08-05：三项 Boss GameTest 通过；特殊阶段与客户端表现仍待手工验收 |
| `MO-ADAPTER-02` | capability 解析、歧义拒绝、binding 切换和幂等关闭正确 | JUnit | `自动验证通过` | 2026-08-05：capability fallback、歧义、异常回滚和多领域句柄测试通过 |
| `MO-THIRD-01` | 标准第三方 Mob 走 best-effort，自定义 AI 通过专用 Adapter 生效 | 测试夹具 | `代码完成` | 通用 fallback 与公开扩展 API 已完成；自定义 AI 夹具仍待补充 |
| `MO-NET-02` | 增量缺口和分段停滞可重同步，伪造和重复请求不重复控制或扣 CP | JUnit | `代码完成` | revision 高水位、乱序/错误分块、超时重试、限流和请求序列测试通过；控制与扣费一体化仍待补测 |
| `MO-BUILD-02` | 测试、GameTest、开发/发布构建及客户端启动全部成功 | Gradle | `自动验证通过` | 2026-08-05：JUnit、10 项 Mentalout GameTest（总计 11/11）、开发/发布构建与客户端启动烟测通过 |

## M2 + M3 验收矩阵

| ID | 明确预期结果 | 验证方式 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| `MO-INTRUSION-01` | 服务端权威建立单观察会话，客户端只接受已追踪且 UUID 匹配目标，所有退出路径恢复相机 | JUnit、客户端手工 | `代码完成` | 会话 UUID/revision、ready 超时、目标校验、相机快照与生命周期清理已落盘；双人相机行为仍待手工验收 |
| `MO-PERCEPTION-01` | 多来源感知句柄独立清理，Mob 视线被过滤，显式目标误认优先 | GameTest | `自动验证通过` | 2026-08-05：`perception_multi_source_override` 通过，总计 12/12 GameTest |
| `MO-PERCEPTION-02` | 玩家渲染、准星、攻击和交互均服从隐藏关系，乱序解除包不能重新隐藏 | 客户端启动、双人手工 | `代码完成` | 客户端与服务端 Mixin 成功应用，按实体保留 revision 墓碑；双人视觉与伪造交互仍待手工验收 |
| `MO-GRAPH-01` | 图 codec 拒绝错误版本、尾随数据、超限、NaN、类型错误、重复输入和环，并确定性规范化 | JUnit | `自动验证通过` | 2026-08-05：`PrecisionGraphTest` 全部通过 |
| `MO-DATA-01` | 四槽数据固定槽数，revision 单调，非法旧图归零且副本隔离 | JUnit | `自动验证通过` | 2026-08-05：`PrecisionOperationDataTest` 全部通过 |
| `MO-EXEC-01` | 同槽取消、异槽并行、CP 聚合、高优先级租约与失败回滚符合合同 | JUnit、多人手工 | `代码完成` | 服务端执行与逆序回滚已落盘；多槽 CP 扩缩容的一体化运行测试仍待补充 |
| `MO-EDITOR-01` | 四槽编辑器支持节点搜索、拖动、连线、参数、撤销重做、保存和恢复 | 客户端手工 | `代码完成` | 编辑器及中英文资源已通过客户端类加载与资源重载；交互截图和低帧率操作仍待验收 |
| `MO-BUILD-03` | M2 + M3 JUnit、GameTest、双构建与客户端启动无注册、资源或 Mixin 错误 | Gradle | `自动验证通过` | 2026-08-05：JUnit、12/12 GameTest、开发/发布构建通过；客户端确认 8 类别、92 技能及声音引擎启动 |

## M4 验收矩阵

| ID | 明确预期结果 | 验证方式 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| `MO-PLAYER-ROSTER-01` | 玩家可多编队入列，按 3 倍权重计费，且不写入 Mob 召回记忆 | JUnit、双人手工 | `代码完成` | LivingEntity 泛化、玩家专用验证与费用权重已落盘；等待专项测试与联机验收 |
| `MO-TAKEOVER-01` | 双端握手、唯一输入租约、环形控制拒绝和 CP 原子回滚符合合同 | JUnit、双人手工 | `代码完成` | session 双索引、100 CP 原子 occupation 与 DirectControl 租约已落盘 |
| `MO-INPUT-01` | 移动/视角/跳跃/潜行/疾跑按授权帧生效，乱序重放及超时被拒绝 | JUnit、双人手工 | `代码完成` | 量化 codec、双序列高水位、中立帧和移动包校验已落盘 |
| `MO-ACTION-01` | 攻击和当前手使用服务端重射线；采掘、热栏和伪造世界动作被拒绝 | 双人手工 | `代码完成` | 服务端实体/方块射线及入站动作包拦截已落盘 |
| `MO-RESIST-01` | 挣脱、控制端受伤、保护技能与生命周期均结束会话并正确施加抗性 | JUnit、GameTest、双人手工 | `代码完成` | 挣脱衰减、Post 健康伤害、保护 tick 复核和 400 tick 抗性已落盘 |
| `MO-PATH-01` | 有界 A* 支持跳障、门、攀爬、游泳、飞行与当前载具，且不传送 | JUnit、GameTest、双人手工 | `代码完成` | 10 模式节点、包围盒校验、全服规划预算和授权输入驱动已落盘 |
| `MO-BUILD-04` | M4 JUnit、Mentalout GameTest、开发/发布构建与双客户端烟测通过 | Gradle、手工 | `开发中` | 2026-08-09：JUnit、开发/发布双构建和单客户端启动通过；23 项 GameTest 中 19 项通过，4 项既有 Mob 场景失败；双客户端玩法验收待执行 |

自动门禁顺序：

```powershell
.\gradlew.bat test -DisDev=true
.\gradlew.bat runGameTestServer -DisDev=true
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
.\gradlew.bat runClientDev
```

## 已知限制

- `BEST_EFFORT` 只保证通用目标、关系和原版 AI 路径覆盖，不保证第三方自定义攻击、移动或阶段机完整服从控制。
- 印象仇恨白名单依赖第三方 AI 通过原版攻击判定或公开 `attackDecision` 查询；完全绕开这些入口的自定义攻击必须由专用 Adapter 或第三方 Mixin 配合。
- Warden 愤怒、Wither 出生阶段和 Ender Dragon 阶段图不会保存快照；控制结束后由实体原生 AI 重新决策。
- 呆然自失不冻结动画、年龄、药水、燃烧、击退、重力或环境伤害。
- HUD 完整度取决于服务端同步；客户端实体未追踪时仍使用服务端提供的名称、生命和距离快照。
- M1 控制状态不持久化，服务器重启后自然消失。
- 玩家控制、防护结束后的 200 tick 抗性和地面寻路代理推迟到 M4。
- 玩家感官过滤、心灵潜入相机与四槽并发仍需要至少两名真实玩家完成最终手工验收；客户端启动烟测不替代玩法验收。
- 完全绕开 `Sensing`、Brain、原版攻击与交互入口的第三方 AI/玩家交互仍需通过 `MentalPerceptionApi` 或兼容 Mixin 查询感知裁决。
- 精密操作按节点报告保护、能力不支持、无有效主体/目标、不可达、移动模式、规划预算、客户端超时与无效方向等结构化错误，不向客户端泄露服务端实体细节。

## 进展日志

### 2026-08-09

- `代码完成`：M4 玩家编队、意识接管、输入租约/挣脱/抗性、服务端动作校验与 10 模式玩家导航实现已落盘。
- `自动验证通过`：`test -DisDev=true`、`build -DisDev=true` 与 `build -DisDev=false` 通过。
- `运行时验收通过`：`runClientDev` 完成 8 类别、92 技能注册校验、资源重载和声音引擎初始化，未发现 Academy Mixin apply 或资源注册错误。
- `开发中`：GameTest 总计 23 项，M4 新增的 `player_freeze_and_relation` 与 `player_path_requires_client_ready` 均通过；既有 `wither_flight_path`、`guard_reactive_target_and_return`、`warden_path_relation_and_dig_guard`、`path_unreachable_reports_failure` 仍失败。
- `开发中`：双客户端真实玩家的延迟、挣脱、攻击/使用、载具和飞行画面同步仍需手工验收。

### 2026-08-05

- `自动验证通过`：M0 `test -DisDev=true`、开发构建和发布构建均通过。
- `代码完成`：M0 控制 API、租约 Runtime、普通 Mob Adapter、网络施放意图和 Mixin 注入已落盘。
- `代码完成`：M1 技能树、受控清单、永久 CP occupation、Boss Adapter、普通第三方 Mob fallback 和公开第三方扩展点已落盘。
- `自动验证通过`：JUnit 覆盖租约优先级与替换、Adapter capability fallback 与歧义拒绝、异常快照回滚、多领域句柄、CP 原子替换、请求序列和名单同步协议。
- `自动验证通过`：`runGameTestServer -DisDev=true` 已运行并通过 10 项 Mentalout 场景（连同默认测试共 11/11），覆盖永久误认、冻结恢复、严格仇恨事件链、内部愤怒状态以及 Warden、Wither、Ender Dragon 控制。
- `自动验证通过`：M1 最新变更的 `test -DisDev=true`、开发构建和发布构建均已重新执行成功。
- `运行时验收通过`：`runClientDev` 已完成能力注册与客户端资源加载，未发现 Academy Mixin apply、注册或专服类加载错误；JEI 对当前 NeoForge beta 的非阻断兼容异常不计入 Mentalout 验收。
- `代码完成`：名单同步支持 revision 高水位、每块最多 64 项的完整同步、乱序校验、分段停滞重试、20 tick 服务端限流和每 tick 固定增量预算。
- `代码完成`：左侧中部 `MENTAL_CONTROL` HUD 已调整为 168 像素窄版淡灰高透明度布局，支持最近 8 项、`+N`、支持/效果/CP 信息和中英文资源。
- `开发中`：HUD 客户端视觉和布局编辑仍待手工验收。
- `开发中`：目标误认升级为永久批次，增加同目标免费解除、错误目标生命周期清理和新成员不继承合同；对应 GameTest 已加入但尚未取得重跑证据。
- `自动验证通过`：印象操作严格白名单、自身真实受击反击、施术者护主、显式误认授权、关系级联清理与原生 AI 恢复均已通过 JUnit/GameTest；第三方自定义 AI 与客户端表现仍待手工验收。
- `代码完成`：客户端启动烟测、三种保护技能运行验证、第三方自定义 AI 夹具和 HUD 截图验收尚未完成，不标记为运行时验收通过。
- `代码完成`：本地分支已合并官方 `upstream/26.2` 的 `f53a413c`，合并提交为 `6c17c0e5`；Mentalout 已恢复到新上游工作树且无文本冲突。
- `代码完成`：本地反射电子束已迁移到上游批处理 VFX，以独立入射段和返回段保留侧向偏移、宽度、反射点与返回轨迹。
- `自动验证通过`：NeoForge 已升级并成功解析为 `26.2.0.45-beta`，恢复 Mentalout 后的 `test -DisDev=true` 通过；GameTest、双构建与客户端烟测等待本轮重跑。
- `自动验证通过`：上游适配后的 `runGameTestServer -DisDev=true` 为 11/11，通过开发与发布两种 `build`；所有门禁均使用 NeoForge `26.2.0.45-beta`。
- `运行时验收通过`：`runClientDev` 在 NeoForge `26.2.0.45-beta` 下完成 8 类别、89 技能校验、资源重载和声音引擎初始化，未发现 Academy Mixin apply、注册或专服类加载错误；反射电子束 VFX 的实际场景画面仍待手工确认。
- `代码完成`：M2 心灵潜入、感官扭曲、公共只读感知 API、Mob/玩家双端过滤、会话与生命周期清理已落盘。
- `代码完成`：M3 固定四槽数据、bounded codec、类型化 DAG 校验与编译、原子执行回滚、节点编辑器和快捷键已落盘。
- `自动验证通过`：新增 JUnit 覆盖图边界、类型、环、NaN、恶意 codec、确定性编译及四槽数据 revision/副本；完整 `test -DisDev=true` 通过。
- `自动验证通过`：新增感知多来源、Mob 视线和显式误认优先级 GameTest；Mentalout 共 11 项，总计 12/12 GameTest 通过。
- `自动验证通过`：开发/发布双构建通过；客户端启动确认 8 类别、92 技能、资源重载和声音引擎初始化，无 Academy Mixin apply 错误。
- `代码完成`：精密操作编辑器升级为 v2，提供 51 节点分类库、响应式紧凑画布、统一缩放、拖拽/吸附/重连、`FLOW` 单链和 v1 程序自动迁移。
- `自动验证通过`：2026-08-06 完整 JUnit 214/214、GameTest 12/12、开发/发布双构建通过；客户端完成 8 类别、92 技能及资源和声音重载。
