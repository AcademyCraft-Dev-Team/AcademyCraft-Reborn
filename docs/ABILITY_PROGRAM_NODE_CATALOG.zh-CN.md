# 自定义能力程序节点总表

本文档对应当前工作区实现，用于统一调整节点归属、端口、参数、效果和数值。节点定义以服务端代码为准，本地化文本仅用于显示。

## 1. 统计口径

- 已注册的唯一节点类型：230 个。
- 编辑器当前可见的唯一节点类型：165 个；被合并节点仍注册为隐藏兼容节点。
- 共享节点：131 个，全部能力分类都可使用。
- 入口节点：7 个分类手动入口、5 个共享自动入口；每个槽位严格限制为一个入口。Level 0 不再注册程序定义和入口。
- 非心理系分类专属节点：37 个。
- 心理掌握专属节点：入口 1 个、功能节点 55 个；其中 5 个旧精密操作别名已隐藏，但仍可导入和执行。
- 每个程序最多 128 个节点、256 条边；一次执行燃料上限为 16385 步。
- 共享世界查询的服务端硬上限通常为 32 格、128 个结果。心理系自己的“服务端视线目标”和“视线位置”可进行 64 格视线检测，但共享查询节点仍受 32 格上限控制。
- 分类行动的默认强度均为 `1`。矢量操作的离散档位继续使用 `strength=0/1/2`；其他分类和动能冲击波使用连续 `power=0.00–2.00` 滑条。伤害按 `power` 线性缩放，CP 按 `power²` 缩放，因此 `power=1` 保持原技能伤害与消耗。
- 检查器中的有限配置统一使用左右按键循环选择，包括数值类型、运算符、比较符、实体大类、变量类型、离散强度、运动条件及心理系有限枚举；`power` 使用拖动滑条；常量实际数值、循环 tick、范围、持续时间、坐标、维度和变量名等自由值继续使用文本输入。

分类定义：`electromaster`、`teleport`、`accelerator`、`meltdowner`、`aeromanip`、`darkmatter`、`mentalout`。

各分类编辑器标题统一显示为“XXX精密操作”。原 `precision_operation` 技能仅保留为 Level 5 占位技能及旧存档数据载体，不再注册 GUI、按键或网络生命周期；心理掌握精密操作由通用入口在分类 Level 5 时直接解锁和路由。

### 端口类型缩写

| 缩写 | 类型 |
|---|---|
| `F` | 流程 `flow` |
| `B` | 布尔值 `boolean` |
| `I` | 32 位整数 `integer` |
| `BI` | 任意精度整数 `big_integer` |
| `N` | 浮点数 `float`，实现为有限 `double` |
| `D` | 归一化方向 `direction` |
| `WP` / `WPS` | 世界坐标 / 世界坐标集合 |
| `BP` / `BPS` | 方块坐标 / 方块坐标集合 |
| `E` / `ES` | 实体引用 / 实体集合 |
| `LE` / `LES` | 生物实体引用 / 生物实体集合 |
| `CD` | 心理控制目标，可兼容世界坐标、方块坐标或实体引用 |

`输入 → 输出` 中，方括号表示流程可作为开放的首/末行动留空；其他输入均为数据必填输入。

## 2. 分类入口节点

| 归属 | 完整节点 ID | 类别 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| 电气掌握 | `academy:program/electromaster/entry/on_cast` | 流程 | `— → flow:F` | 执行所选电气掌握程序。 |
| 空间移动 | `academy:program/teleport/entry/on_cast` | 流程 | `— → flow:F` | 执行所选空间移动程序。 |
| 矢量操作 | `academy:program/accelerator/entry/on_cast` | 流程 | `— → flow:F` | 执行所选矢量操作程序。 |
| 原子崩坏 | `academy:program/meltdowner/entry/on_cast` | 流程 | `— → flow:F` | 执行所选原子崩坏程序。 |
| 气流操纵 | `academy:program/aeromanip/entry/on_cast` | 流程 | `— → flow:F` | 执行所选气流操纵程序。 |
| 未元物质 | `academy:program/darkmatter/entry/on_cast` | 流程 | `— → flow:F` | 执行所选未元物质程序。 |
| 心理掌握 | `academy:program/entry/on_cast` | 流程 | `— → flow:F` | 精密操作/心理掌握程序入口。ID 格式与其他分类不一致。 |

### 2.1 共享自动入口节点

下列节点无输入且仅输出 `flow:F`。图验证、编辑器和服务端保存均要求每个槽位最多存在一个入口节点。

| 完整节点 ID | 配置 | 自动触发时机 | 手动按键 |
|---|---|---|---|
| `academy:program/core/flow/trigger/hurt` | — | 入站攻击被接受后、实际伤害结算前触发。 | 不触发 |
| `academy:program/core/flow/trigger/loop` | `interval=0..1200`，默认 `20` tick | 按固定世界时间间隔触发；`0` 按每 tick 处理。 | 可触发 |
| `academy:program/core/flow/trigger/melee` | — | 玩家完成一次近战攻击后触发。 | 不触发 |
| `academy:program/core/flow/trigger/movement` | `condition=jump/sneak/sprint/elytra/swim` | 跳跃，或潜行、疾跑、鞘翅飞行、游泳状态发生切换时触发。 | 可触发 |
| `academy:program/core/flow/trigger/health_threshold` | `mode=below/above`、`threshold>=0` | 生命值首次进入阈值触发区间时触发；离开区间后重新武装。 | 不触发 |

自动触发完全由服务端判定；自动循环不会逐次发送成功提示。为防止技能造成的伤害递归触发自身，同一玩家正在执行自动入口时不会重入。

## 2.X 分类调整计划
`level 0`不需要有进入界面的入口
所有能力分类进入入口按键统一调整为`\`，矢量操作的过滤网技能的默认按键为`=`

**已实施**：Level 0 程序定义已移除；全部分类在能力达到 Level 5 后解锁精密操作，默认 GUI 键为 `\`，10 个槽位的默认施放键依次为 `Alt+1` 至 `Alt+9`、`Alt+0`；心理掌握沿用同一入口并转发到心理专属程序会话。旧 `=` 默认键会迁移为 `\`，但不覆盖玩家自定义键位；反射过滤网默认键仍为 `=`。统一 GUI/槽位键可在“设置 → 按键设置”中修改，未达到 Level 5 或当前能力不支持精密操作时不显示该组。

## 3. 共享节点（131 个）

共享节点 ID 前缀均为 `academy:program/core/`，归属为“所有分类”。

### 3.1 数值节点

| ID 后缀 | 类别 | 配置 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `value/scalar` | 数值 | `type=boolean/integer/big_integer/float`、`value` | `— → value:T` | 可见合并节点；类型使用左右按键切换，修改类型后动态改变输出端口并把值重置为合法默认值。实际常量值保留文本输入，并按所选类型严格解析。 |
| `value/boolean` | 数值·隐藏兼容 | `value:B` | `— → value:B` | 旧布尔常量，保留旧程序执行。 |
| `value/integer` | 数值·隐藏兼容 | `value:I` | `— → value:I` | 旧 32 位整数常量，保留旧程序执行。 |
| `value/big_integer` | 数值·隐藏兼容 | `value:BI`，字符串编码 | `— → value:BI` | 旧任意精度整数常量，保留旧程序执行。 |
| `value/float` | 数值·隐藏兼容 | `value:N` | `— → value:N` | 旧有限浮点数常量，保留旧程序执行。 |

### 3.1.X 数值节点调整计划
四个数值节点合并，可以在I界面中选择是哪种数值

**已实施**：新增 `value/scalar`；原四节点从节点库隐藏但未注销，避免旧图失效。

### 3.2 整数、大整数与浮点运算（32 个注册，2 个可见）

下表每行代表 3 个实际节点；将 `{integer|big_integer|float}` 分别展开为整数、大整数、浮点数。输入类型与展开的数值域一致。

| ID 后缀模板 | 节点 | 配置 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `logic/numeric/arithmetic` | 数值运算 | `type=integer/big_integer/float`、`operator=add/subtract/multiply/divide/modulo` | `left:T, right:T → result:T` | 可见合并节点；类型与运算符由左右按键选择，输入输出端口随类型动态变化。整数运算保持溢出与零除检查，浮点结果必须有限。 |
| `logic/{integer|big_integer|float}/{add|subtract|multiply|divide|modulo}` | 运算·隐藏兼容（15 个） | — | `left:T, right:T → result:T` | 旧分类型运算节点继续编译执行，新建图不再显示。 |
| `logic/numeric/compare` | 数值比较 | `type=integer/big_integer/float`、`operator=equal/less/less_equal/greater/greater_equal` | `left:T, right:T → result:B` | 可见合并节点；类型与运算符由检查器选择，输入端口随类型动态变化。 |
| `logic/{integer|big_integer|float}/{equal|less|less_equal|greater|greater_equal}` | 比较·隐藏兼容（15 个） | — | `left:T, right:T → result:B` | 旧比较节点继续编译执行，新建图不再显示。 |

其中 `T` 分别为 `I`、`BI`、`N`。

### 3.2.X 整数、大整数与浮点运算（30 个）调整计划
几个判断值大小的节点合并为一个节点，可以在I界面中选择是哪种判断；三类数值的五种运算也同样合并。

**已实施**：新增 `logic/numeric/arithmetic` 与 `logic/numeric/compare`，原 30 个分类型运算、比较节点隐藏兼容。类型、运算符和比较符均使用左右按键切换。

### 3.3 布尔、相等与流程状态节点

| ID 后缀 | 类别 | 配置 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `logic/boolean/not` | 逻辑 | — | `value:B → result:B` | 布尔取反。 |
| `logic/boolean/and` | 逻辑 | — | `left:B, right:B → result:B` | 布尔与。 |
| `logic/boolean/or` | 逻辑 | — | `left:B, right:B → result:B` | 布尔或。 |
| `logic/boolean/xor` | 逻辑 | — | `left:B, right:B → result:B` | 布尔异或。 |
| `logic/entity/equal` | 逻辑 | — | `left:E, right:E → result:B` | 判断是否为同一实体引用。 |
| `logic/world_position/equal` | 逻辑 | — | `left:WP, right:WP → result:B` | 判断维度和坐标数值完全相等。 |
| `logic/block_position/equal` | 逻辑 | — | `left:BP, right:BP → result:B` | 判断是否为同维度同一方块。 |
| `logic/direction/equal` | 逻辑 | — | `left:D, right:D → result:B` | 精确比较方向分量。 |
| `flow/branch` | 流程 | — | `flow:F, condition:B → true:F, false:F` | 根据条件选择流程出口。 |
| `flow/stop` | 流程 | — | `flow:F → —` | 终止当前流程。 |
| `flow/trigger/hurt` | 流程入口 | — | `— → flow:F` | 受击伤害结算前自动触发。 |
| `flow/trigger/loop` | 流程入口 | `interval=0..1200` | `— → flow:F` | 固定间隔自动触发，也可手动触发。 |
| `flow/trigger/melee` | 流程入口 | — | `— → flow:F` | 近战攻击目标后自动触发。 |
| `flow/trigger/movement` | 流程入口 | `condition` | `— → flow:F` | 指定运动事件发生时自动触发，也可手动触发。 |
| `flow/trigger/health_threshold` | 流程入口 | `mode=below/above`、`threshold` | `— → flow:F` | 生命值首次高于或低于阈值时触发一次；回到阈值另一侧后可再次触发。 |
| `state/variable_get` | 逻辑 | `name`、`type` | `— → value:T` | 读取当前执行会话中的类型化变量。 |
| `state/variable_set` | 逻辑 | `name`、`type` | `flow:F, value:T → flow:F` | 写入会话变量；变量使循环具备可变状态。 |

变量支持 `B/I/BI/N/identifier/duration/D/WP/BP/E/LE/DS/WPS/BPS/ES/LES`，变量名长度为 1–64。

### 3.4 目标与方向：世界坐标、方块坐标与方向节点（17 个）

| ID 后缀 | 类别 | 配置 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `spatial/world_position` | 目标与方向 | `dimension,x,y,z` | `— → position:WP` | 输出固定世界坐标。 |
| `spatial/world_position_construct` | 目标与方向 | `dimension` | `x:N,y:N,z:N → position:WP` | 动态构造世界坐标。 |
| `spatial/world_position_components` | 目标与方向 | — | `position:WP → x:N,y:N,z:N` | 分解世界坐标。 |
| `spatial/world_position_offset` | 目标与方向 | — | `position:WP,direction:D,distance:N → position:WP` | 沿方向偏移世界坐标。 |
| `spatial/world_position_distance` | 目标与方向 | — | `left:WP,right:WP → result:N` | 计算两坐标距离；跨维度输入无有效距离。 |
| `spatial/world_position_same_dimension` | 目标与方向 | — | `left:WP,right:WP → result:B` | 判断坐标维度是否相同。 |
| `spatial/block_position` | 目标与方向 | `dimension,x,y,z` | `— → position:BP` | 输出固定整数方块坐标。 |
| `spatial/block_position_construct` | 目标与方向 | `dimension` | `x:I,y:I,z:I → position:BP` | 动态构造方块坐标。 |
| `spatial/block_position_components` | 目标与方向 | — | `position:BP → x:I,y:I,z:I` | 分解方块坐标。 |
| `spatial/position_to_block` | 目标与方向 | — | `position:WP → block:BP` | 向下取整为所在方块。 |
| `spatial/block_to_center` | 目标与方向 | — | `block:BP → position:WP` | 转换为方块中心点。 |
| `spatial/direction` | 目标与方向 | `x,y,z` | `— → direction:D` | 输出固定方向向量。 |
| `spatial/direction_construct` | 目标与方向 | — | `x:N,y:N,z:N → direction:D` | 构造并归一化方向；零向量无效。 |
| `spatial/direction_components` | 目标与方向 | — | `direction:D → x:N,y:N,z:N` | 分解方向分量。 |
| `spatial/direction_between` | 目标与方向 | — | `from:WP,to:WP → direction:D` | 输出由起点指向终点的单位方向。 |
| `spatial/direction_opposite` | 目标与方向 | — | `direction:D → direction:D` | 反转方向。 |
| `spatial/direction_dot` | 目标与方向 | — | `left:D,right:D → result:N` | 计算方向点积。 |

### 3.5 共享世界查询节点（7 个）

| ID 后缀 | 类别 | 输入 → 输出 | 效果 |
|---|---|---|---|
| `query/caster` | 目标 | `— → entity:E` | 返回当前程序的施术者。 |
| `query/look_target` | 目标 | `— → entity:E` 或 `block:BP` | `target_type=entity/block`，返回服务端验证的施术者视线实体或方块坐标；输出端口随配置改变，未命中时不输出数据。 |
| `query/entity_position` | 目标与方向 | `entity:E → position:WP` | 读取同维度有效实体的当前位置。 |
| `query/entity_look_direction` | 目标与方向 | `entity:E → direction:D` | 读取实体视线方向。 |
| `query/entities_around` | 目标与方向 | `center:WP,radius:N → entities:ES` | 收集坐标附近实体；半径最大 32，结果最多 128。 |
| `query/raycast_block` | 目标与方向 | `origin:WP,direction:D,range:N → block:BP` | 沿方向返回首个方块命中。 |
| `query/raycast_entity` | 目标与方向 | `origin:WP,direction:D,range:N → entity:E` | 沿方向返回首个无遮挡实体命中。 |

### 3.5.X 共享世界查询节点（5 个）调整计划
施术者，施术者视线目标应作为通用的节点

**已实施**：新增两个共享目标节点；各分类原 `target/caster`、`target/look_target` 和心理系 `target/look_living` 隐藏兼容。

### 3.6 共享实体过滤节点（13 个）

完整 ID 前缀为 `academy:program/core/filter/entity/`，所有节点均为只读世界查询并输出去重且顺序稳定的实体集合。

| ID 后缀 | 配置 | 输入 → 输出 | 效果 |
|---|---|---|---|
| `filter/entity/alive` | — | `entities:ES → entities:ES` | 保留存活且未移除实体。 |
| `filter/entity/distance` | — | `entities:ES,center:WP,radius:N → entities:ES` | 保留与中心同维度且在半径内的实体。 |
| `filter/entity/allied_to` | — | `entities:ES,reference:E → entities:ES` | 保留参照实体自身或其盟友。 |
| `filter/entity/hostile_to` | — | `entities:ES,reference:E → entities:ES` | 保留会攻击参照实体或最近被其伤害的生物。 |
| `filter/entity/targeted_by` | — | `entities:ES,target:E → entities:ES` | 保留当前攻击目标为指定实体的生物。 |
| `filter/entity/last_damaged_by` | — | `entities:ES,attacker:E → entities:ES` | 保留最近被指定攻击者伤害的生物。 |
| `filter/entity/type` | `type=any/living/player/mob/hostile/animal/friendly/projectile/item` | `entities:ES → entities:ES` | 按检查器所选实体大类过滤；友善生物不包含敌对生物与动物。 |
| `filter/entity/health_at_least` | — | `entities:ES,percent:N → entities:ES` | 保留生命百分比不低于 0–100 阈值的生物。 |
| `filter/entity/health_at_most` | — | `entities:ES,percent:N → entities:ES` | 保留生命百分比不高于 0–100 阈值的生物。 |
| `filter/entity/max_health_at_least` | — | `entities:ES,health:N → entities:ES` | 保留最大生命值不低于输入数值的生物。 |
| `filter/entity/max_health_at_most` | — | `entities:ES,health:N → entities:ES` | 保留最大生命值不高于输入数值的生物。 |
| `filter/entity/has_target` | — | `entities:ES → entities:ES` | 保留当前存在攻击目标的生物。 |
| `filter/entity/visible_from` | — | `entities:ES,observer:E → entities:ES` | 保留与观察者同维度且视线可见的实体。 |

### 3.7 集合节点（40 个）

集合域共有 4 种：

| 域名 | 元素类型 | 集合类型 | ID 中的域 |
|---|---|---|---|
| 实体 | `E` | `ES` | `entity` |
| 世界坐标 | `WP` | `WPS` | `world_position` |
| 方块坐标 | `BP` | `BPS` | `block_position` |
| 方向 | `D` | `DS` | `direction` |

每个域都注册下列 9 个节点，完整 ID 为 `academy:program/core/collection/<域>/<操作>`：

| 操作 | 类别 | 输入 → 输出 | 效果 |
|---|---|---|---|
| `empty` | 集合 | `— → values:SET` | 创建空集合。 |
| `singleton` | 集合 | `value:ELEM → values:SET` | 将单值包装为集合。 |
| `union` | 集合 | `left:SET,right:SET → values:SET` | 并集并去重。 |
| `intersection` | 集合 | `left:SET,right:SET → values:SET` | 保留交集。 |
| `difference` | 集合 | `left:SET,right:SET → values:SET` | 从左集合移除右集合。 |
| `contains` | 集合 | `values:SET,value:ELEM → result:B` | 判断包含关系。 |
| `size` | 集合 | `values:SET → size:I` | 返回元素数量。 |
| `get` | 集合 | `values:SET,index:I → value:ELEM` | 获取零基索引元素。 |
| `foreach` | 集合/流程 | `flow:F,values:SET → body:F,done:F,value:ELEM` | 逐项执行循环体，结束后从 `done` 继续。 |

四种集合各提供一个服务端随机选择节点：

| ID 后缀 | 类别 | 输入 → 输出 | 效果 |
|---|---|---|---|
| `collection/entity/random` | 集合 | `entities:ES → entity:E` | 由服务端从去重后的实体集合中等概率随机选取一个实体；空集合不输出数据。 |
| `collection/world_position/random` | 集合 | `positions:WPS → position:WP` | 随机选取一个世界坐标；空集合不输出数据。 |
| `collection/block_position/random` | 集合 | `blocks:BPS → block:BP` | 随机选取一个方块坐标；空集合不输出数据。 |
| `collection/direction/random` | 集合 | `directions:DS → direction:D` | 随机选取一个方向；空集合不输出数据。 |

`foreach`、会话变量和可循环流程边共同构成当前图灵完备控制核心。

## 4. 矢量操作专属节点（9 个）

ID 前缀：`academy:program/accelerator/`。一般行动节点使用配置 `strength=0/1/2`，分别表示受控/标准/最大。`kinetic_shockwave` 改用连续 `power`，并具有 `destroy_blocks` 和 `radius` 配置。

| ID 后缀 | 类别 | 能力归属 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `target/caster` | 目标 | 矢量操作 | `— → entity:E` | 返回施术者。 |
| `target/look_target` | 目标 | 矢量操作 | `— → entity:E` | 返回 32 格内服务端视线实体。 |
| `target/incoming_projectiles` | 目标 | 矢量操作 | `— → entities:ES` | 返回 32 格内速度方向正在威胁施术者的投射物，最多 128 个。 |
| `action/apply_vector` | 行动 | 矢量加速 | `flow:F,entity:E,direction:D → flow:F` | 给可移动实体叠加指定方向速度。 |
| `action/kinetic_impact` | 行动 | 动能附加 | `flow:F,entity:E,direction:D → flow:F` | 在实体中心生成定向动能伤害、冲量、视觉，并按设置破坏方块。 |
| `action/kinetic_shockwave` | 行动 | 动能附加 | `flow:F,position:WP,direction:D → flow:F` | 无需开启动能附加技能，以强度 3 的原技能数值生成冲击波；`power` 缩放伤害/CP，`radius=0–32` 同时控制伤害与方块破坏范围；0 时只破坏坐标所在方块。 |
| `action/redirect_projectile` | 行动 | 矢量反射 | `flow:F,projectile:E,direction:D → flow:F` | 保持原速度幅值并改变投射物方向和所有者；无强度配置。 |
| `action/displace_entity` | 行动 | 矢量加速 | `flow:F,entity:E,destination:WP → flow:F` | 将可移动实体放置到同维度、无碰撞、安全目标坐标。 |
| `action/displace_block` | 行动 | 动能附加 | `flow:F,block:BP,destination:BP → flow:F` | 将允许修改的方块实体化并无重力飞向空目标方块。 |

### 矢量操作数值

| 节点 | 受控 `0` | 标准 `1` | 最大 `2` |
|---|---|---|---|
| 施加矢量 | 冲量 0.4，CP 5 | 冲量 0.8，CP 10 | 冲量 1.2，CP 20 |
| 动量冲击 | 等级 1，半径 3，基础伤害 5，CP 10 | 等级 3，半径 11，基础伤害 13，CP 30 | 等级 5，半径 27，基础伤害 29，CP 50 |
| 冲击波节点（连续 `power`） | 强度 3，伤害倍率 0，CP 0 | 强度 3，基础伤害 13，CP 30 | 强度 3，伤害倍率 2，CP 120 |

冲击波节点的默认配置为 `power=1`、`destroy_blocks=false`、`radius=11`。`radius` 同时用于伤害判定和方块破坏；`radius=0` 不扫描邻近方块，只处理 `BlockPos.containing(position)`。伤害仍会乘以服务端能力威力与伤害倍率，方块破坏还必须通过全局及技能级破坏权限。旧 `strength/damage/block_radius` 配置在编辑器打开时迁移为新字段。
| 实体位移 | 位移上限 4，CP 8 | 位移上限 8，CP 16 | 位移上限 16，CP 30 |
| 方块位移 | 位移上限 2，速度 0.65，CP 20 | 位移上限 4，速度 0.9，CP 40 | 位移上限 8，速度 1.15，CP 80 |
| 偏转投射物 | — | 无档位；速度钳制 0.5–4.0，CP 为 `8 + 2 × 速度` | — |

## 5. 气流操纵专属节点（4 个）

ID 前缀：`academy:program/aeromanip/`；行动配置为连续 `power=0.00–2.00`。

| ID 后缀 | 类别 | 能力归属 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `target/caster` | 目标 | 气流操纵 | `— → entity:E` | 返回施术者。 |
| `target/look_target` | 目标 | 气流操纵 | `— → entity:E` | 返回服务端视线实体。 |
| `action/airflow_push` | 行动 | 气动抓取 | `flow:F,entity:E,direction:D → flow:F` | 按实体受力倍率沿方向推动非 Boss、可受力目标。 |
| `action/laminar_cut` | 行动 | 层流切割 | `flow:F,direction:D → flow:F` | 沿方向发射服务端限制射程和伤害的空气刃。 |

| 节点 | 受控 `0` | 标准 `1` | 最大 `2` |
|---|---|---|---|
| 风压推动 | 射程 8，速度 0.45，CP 0 | 射程 16，速度 0.85，CP 10 | 射程 24，速度 1.35，CP 40 |
| 层流切割 | 射程 32，伤害倍率 0，CP 0 | 射程 32，伤害倍率 1，CP 20 | 射程 32，伤害倍率 2，CP 80 |

## 6. 未元物质专属节点（6 个）

ID 前缀：`academy:program/darkmatter/`；行动配置为连续 `power=0.00–2.00`。

| ID 后缀 | 类别 | 能力归属 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `target/caster` | 目标 | 未元物质 | `— → entity:E` | 返回施术者。 |
| `target/look_target` | 目标 | 未元物质 | `— → entity:E` | 返回服务端视线实体。 |
| `action/disassemble_block` | 行动 | 未元物质分解 | `flow:F,block:BP → flow:F` | 分解一个已加载、允许修改的方块；不继承六翼范围扩张。 |
| `action/disassemble_entity` | 行动 | 未元物质分解 | `flow:F,entity:E → flow:F` | 对一个存活、非友方、可见生物实体造成分解伤害；不扩散到周围实体。 |
| `action/darkmatter_cut` | 行动 | 未元物质切割 | `flow:F,direction:D → flow:F` | 沿非垂直方向造成锥形未元物质斩击，保留熟练度二段斩和视觉。 |
| `action/create_beetle` | 行动 | 未元物质塑造 | `flow:F,position:WP → flow:F` | 在已加载、世界边界内、无碰撞坐标塑造一只甲虫；受八只上限和维护 CP 约束。 |

| 节点 | 受控 `0` | 标准 `1` | 最大 `2` |
|---|---|---|---|
| 分解方块 | 射程 8，CP 0 | 射程 16，CP 10 | 射程 32，CP 40 |
| 分解实体 | 射程 16，伤害倍率 0，CP 0 | 射程 16，伤害倍率 1，CP 10 | 射程 16，伤害倍率 2，CP 40 |
| 未元物质切割 | 半径上限 10，伤害倍率 0，CP 0 | 半径上限 10，伤害倍率 1，CP 20 | 半径上限 10，伤害倍率 2，CP 80 |
| 塑造甲虫 | 射程 8，CP 0 | 射程 16，CP 60 | 射程 32，CP 240 |

每只甲虫额外占用 20 基础维护 CP。

## 7. 电气掌握专属节点（8 个）

ID 前缀：`academy:program/electromaster/`；行动配置为连续 `power=0.00–2.00`。

| ID 后缀 | 类别 | 能力归属 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `target/caster` | 目标 | 电气掌握 | `— → entity:E` | 返回施术者。 |
| `target/look_target` | 目标 | 电气掌握 | `— → entity:E` | 返回服务端视线实体。 |
| `target/chargeable_blocks` | 目标与方向 | 电流充能 | `center:WP,radius:N → blocks:BPS` | 收集半径最大 32 格内、已加载且具有 FE 能量槽的方块坐标，最多 128 个。 |
| `logic/energy_detection` | 逻辑 | 电流充能 | `entity:E → result:B` 或 `block:BP → result:B` | `target_type=entity/block`；按 `mode=below/above` 检测目标 FE 总容量百分比是否低于/高于 `percent=0..100`。实体统计本体、双手和护甲的 FE。 |
| `logic/redstone_detection` | 逻辑 | 电流充能 | `block:BP → result:B` | 按 `mode=below/above` 检测方块最佳邻接红石信号是否低于/高于 `level=0..15`。 |
| `action/arc_discharge` | 行动 | 电弧激发 | `flow:F,entity:E → flow:F` | 对一个有效生物实体造成电击伤害并生成连接电弧。 |
| `action/current_recharge` | 行动 | 电流充能 | `flow:F,entity:E → flow:F` 或 `flow:F,block:BP → flow:F` | `target_type=entity/block`；持续 10 tick 为目标输入 FE，方块无 FE 槽时改为持续红石充能。 |
| `action/magnetic_move` | 行动 | 磁力操纵 | `flow:F,entity:E,destination:WP → flow:F` 或 `flow:F,block:BP,destination:WP → flow:F` | `target_type=entity/block`、`mode=pull/launch`。牵引会将目标加入持久清单，并逐 tick 把清单内全部目标移向坐标；发射会沿目标坐标射出、伤害轨迹实体并解除控制。ID 为兼容旧图保持不变，显示名改为“磁力操控”。 |

| 节点 | 受控 `0` | 标准 `1` | 最大 `2` |
|---|---|---|---|
| 电弧放电 | 射程 12，伤害倍率 0，CP 0 | 射程 12，伤害倍率 1，CP 10 | 射程 12，伤害倍率 2，CP 40 |
| 磁力操控·牵引 | 移动上限 6，速度 0.45，CP 0 | 移动上限 12，速度 0.8，CP 16 | 移动上限 20，速度 1.15，CP 64 |
| 磁力操控·发射 | 发射速度 0.8，伤害倍率 0，CP 0 | 发射速度 1.4，伤害倍率 1，CP 16 | 发射速度 2.1，伤害倍率 2，CP 64 |

## 8. 原子崩坏专属节点（5 个）

ID 前缀：`academy:program/meltdowner/`；行动配置为连续 `power=0.00–2.00`。

| ID 后缀 | 类别 | 能力归属 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `target/caster` | 目标 | 原子崩坏 | `— → entity:E` | 返回施术者。 |
| `target/look_target` | 目标 | 原子崩坏 | `— → entity:E` | 返回服务端视线实体。 |
| `action/atomic_jet` | 行动 | 突击喷射 | `flow:F,entity:E,direction:D → flow:F` | 在目标实体中心生成粒机波形高速炮；该目标免疫此束伤害并被向反方向推进，其他目标仍会受伤。配置 `power`、`destroy_blocks`。 |
| `action/electron_beam` | 行动 | 单发高速电子束 | `flow:F,[origin:WP],direction:D → flow:F` 或 `flow:F,[origin:WP],target_position:WP → flow:F` | 在 `origin`（未接线时兼容为施术者眼部）生成粒机波形高速炮；`aim_mode=direction/target` 切换瞄准端口，`destroy_blocks` 控制是否申请方块破坏。 |
| `action/mining_beam` | 行动 | 采掘束 | `flow:F,[origin:WP],[direction:D] → flow:F` 或 `flow:F,[origin:WP],target_position:WP → flow:F` | 在 `origin` 生成无实体伤害采掘束并按 `aim_mode` 发射；方向模式保留可选旧 `block:BP` 兼容端口。 |

| 节点 | 受控 `0` | 标准 `1` | 最大 `2` |
|---|---|---|---|
| 定向电子束 | 长度 32，伤害倍率 0，束宽 1，CP 0 | 长度 32，伤害倍率 1，束宽 1，CP 15 | 长度 32，伤害倍率 2，束宽 1，CP 60 |
| 定点采掘束 | 射程 12，视觉缩放 0.75，CP 0 | 射程 28，视觉缩放 1.0，CP 20 | 射程 48，视觉缩放 1.25，CP 80 |
| 原子喷射 | 长度 32，伤害倍率 0，CP 0 | 长度 32，伤害倍率 1，CP 20 | 长度 32，伤害倍率 2，CP 80 |

## 9. 空间移动专属节点（5 个）

ID 前缀：`academy:program/teleport/`；行动配置为连续 `power=0.00–2.00`。

| ID 后缀 | 类别 | 能力归属 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `target/caster` | 目标 | 空间移动 | `— → entity:E` | 返回施术者。 |
| `target/look_target` | 目标 | 空间移动 | `— → entity:E` | 返回服务端视线实体。 |
| `logic/space_safety` | 逻辑 | 空间移动通用 | `entity:E,position:WP → result:B` | 精确检测实体包围盒放置到世界坐标后是否位于世界边界内且无碰撞或挤压。 |
| `action/self_teleport` | 行动 | 自身传送 | `flow:F,destination:WP → flow:F` | “安全传送”；将施术者传送至同维度、已加载、无碰撞目标。ID 为兼容旧图保持不变。 |
| `action/entity_teleport` | 行动 | 快速定位传送 | `flow:F,entity:E,destination:CD,[direction:D] → flow:F` 或 `flow:F,block:BP,destination:CD,[direction:D] → flow:F` | “目标传送”；`target_type=entity/block` 切换来源端口，目标可为方块或世界坐标；可选方向同步实体朝向或带朝向属性方块的方向。ID 为兼容旧图保持不变。 |

| 节点 | 受控 `0` | 标准 `1` | 最大 `2` |
|---|---|---|---|
| 安全传送 | 距离 8，CP 0 | 距离 16，CP 10 | 距离 32，CP 40 |
| 目标传送 | 目标/移动距离 8，CP 0 | 目标/移动距离 16，CP 30 | 目标/移动距离 32，CP 120 |

## 10. 心理掌握专属节点（55 个，另有入口 1 个）

以下节点均限定为 `mentalout` 分类。ID 前缀统一为 `academy:program/`。心理行动的 `flow` 输入允许作为首行动留空，普通输出可作为末行动留空。

### 10.1 心理参数编码

| 参数 | 合法值 | 默认值 |
|---|---|---|
| `RANGE` | 1–32 格 | 32 |
| `COUNT` | 整数 1–8 | 4 |
| `CAPABILITY` | 整数 0–6 | 0 |
| `SORT_DIRECTION` | 0 近到远；1 远到近 | 0 |
| `ENTITY_TYPE` | 0 怪物；1 动物；2 玩家；3 Boss；4 弹射物；5 非生物；6 生物；7 掉落物 | 0 |
| `HEALTH_PERCENT` | 整数 1–100 | 50 |
| `DURATION_SECONDS` | 0 永久，或整数 1–3600 秒 | 0 |
| `OFFSET_DISTANCE` | 整数 -32–32 | 1 |

`CAPABILITY` 的代码真实对应：0 强制目标、1 冻结 AI、2 关系控制、3 寻路控制、4 视线控制、5 直接控制、6 守卫控制。中英文本地化已按该枚举修正。

### 10.2 目标与方向节点

| ID 后缀 | 类别 | 参数 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `target/caster` | 目标 | — | `— → entity:E` | 返回施术者。 |
| `mentalout/roster` | 目标·心理特有 | — | `— → entities:ES` | 返回当前受控清单。 |
| `mentalout/intrusion_target` | 目标·心理特有 | — | `— → entity:E` | 返回当前心灵潜入目标。 |
| `target/look_living` | 目标 | — | `— → entity:E` | 返回 64 格内服务端视线生物实体。 |
| `target/nearby_living` | 目标 | `RANGE` | `— → entities:ES` | 收集范围内生物实体。 |
| `target/player_target` | 目标 | — | `— → entity:E` | 返回施术者最近攻击的存活实体。 |
| `target/current_target` | 目标 | — | `subject:E → entity:E` | 返回主体当前有效攻击目标。 |
| `target/last_attacker` | 目标 | — | `subject:E → entity:E` | 返回最近伤害主体的生物。 |
| `target/sight_position` | 目标与方向 | — | `observer:E → destination:CD` | 从观察者执行 64 格射线；实体命中动态跟随，方块命中返回外侧位置。 |
| `target/nearby_all_entities` | 目标 | `RANGE` | `— → entities:ES` | 收集范围内除施术者外全部实体。 |
| `target/nearby_items` | 目标 | `RANGE` | `— → entities:ES` | 收集附近掉落物。 |
| `target/nearby_projectiles` | 目标 | `RANGE` | `— → entities:ES` | 收集附近弹射物。 |
| `target/entity_position` | 目标与方向·隐藏兼容 | — | `entity:E → destination:CD` | 旧实体位置节点；导入时规范化为共享 `query/entity_position`。 |
| `target/direction_between` | 目标与方向 | — | `origin:CD,target:CD → direction:D` | 解析实体或位置目标，输出单位方向。 |
| `target/position_offset` | 目标与方向 | `OFFSET_DISTANCE` | `origin:CD,direction:D → destination:CD` | 沿方向偏移目标，输出固定位置。 |

### 10.3 过滤节点

| ID 后缀 | 类别 | 参数 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `filter/alive` | 过滤 | — | `entities:ES → entities:ES` | 保留存活且未移除实体。 |
| `filter/distance` | 过滤 | `RANGE` | `entities:ES → entities:ES` | 保留距施术者不超过阈值的实体。 |
| `filter/allies` | 过滤 | — | `entities:ES → entities:ES` | 按队伍与友伤规则保留友方。 |
| `filter/enemies` | 过滤 | — | `entities:ES → entities:ES` | 保留施术者之外的非友方。 |
| `mentalout/filter/control_supported` | 过滤·心理特有 | `CAPABILITY` | `entities:ES → entities:ES` | 保留控制适配器支持所选控制能力的实体。 |
| `filter/targeted_by` | 过滤 | — | `entities:ES,target:E → entities:ES` | 保留正在攻击指定目标的实体。 |
| `filter/hostile_to` | 过滤 | — | `entities:ES,target:E → entities:ES` | 保留对指定目标敌对的实体。 |
| `filter/last_damaged_by` | 过滤 | — | `entities:ES,attacker:E → entities:ES` | 保留最近被指定攻击者伤害的实体。 |
| `filter/entity_type` | 过滤 | `ENTITY_TYPE` | `entities:ES → entities:ES` | 按实体大类过滤。 |
| `filter/health` | 过滤 | `HEALTH_PERCENT` | `entities:ES → entities:ES` | 保留生命百分比不低于阈值的生物。 |
| `filter/has_target` | 过滤 | — | `entities:ES → entities:ES` | 保留有有效攻击目标的生物。 |
| `mentalout/filter/affected` | 过滤·心理特有 | — | `entities:ES → entities:ES` | 保留受控清单内或存在心理控制租约的生物。 |
| `filter/health_below` | 过滤 | `HEALTH_PERCENT` | `entities:ES → entities:ES` | 保留生命百分比不高于阈值的生物。 |
| `filter/visible_from` | 过滤 | — | `entities:ES,observer:E → entities:ES` | 保留观察者服务端视线可见的实体。 |

### 10.3.X 过滤节点调整计划
需要增加通用的过滤节点

**已实施**：上列除 `mentalout/filter/control_supported` 与 `mentalout/filter/affected` 外的 12 个旧心理过滤节点均由 3.6 的 11 个显式输入共享过滤节点取代，并作为隐藏兼容节点保留。盟友/敌人、距离等旧节点原先隐式使用施术者，新节点要求连接 `query/caster` 或明确的中心/参照实体。


### 10.4 心理集合节点

| ID 后缀 | 类别 | 参数 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `collection/exclude` | 集合 | — | `entities:ES,excluded:E → entities:ES` | 排除指定实体。 |
| `collection/nearest` | 集合 | — | `entities:ES → entity:E` | 选择距施术者最近实体。 |
| `collection/limit` | 集合 | `COUNT` | `entities:ES → entities:ES` | 按稳定顺序限制数量。 |
| `collection/sort_by_distance` | 集合 | `SORT_DIRECTION` | `entities:ES → entities:ES` | 按距施术者距离排序。 |
| `collection/random` | 集合 | — | `entities:ES → entity:E` | 服务端随机选择实体。 |
| `collection/entity_to_set` | 集合·隐藏兼容 | — | `entity:E → entities:ES` | 旧单例集合节点；规范化为共享实体 `singleton`。 |
| `collection/union` | 集合·隐藏兼容 | — | `left:ES,right:ES → entities:ES` | 旧并集节点；规范化为共享实体 `union`。 |
| `collection/intersection` | 集合·隐藏兼容 | — | `left:ES,right:ES → entities:ES` | 旧交集节点；规范化为共享实体 `intersection`。 |
| `collection/subtract` | 集合·隐藏兼容 | — | `left:ES,right:ES → entities:ES` | 旧差集节点；规范化为共享实体 `difference`。 |
| `collection/farthest` | 集合 | — | `entities:ES → entity:E` | 选择距施术者最远实体。 |
| `collection/lowest_health` | 集合 | — | `entities:ES → entity:E` | 选择生命百分比最低实体。 |
| `collection/highest_health` | 集合 | — | `entities:ES → entity:E` | 选择生命百分比最高实体。 |

### 10.5 心理行动节点

| ID 后缀 | 类别 | 参数 | 输入 → 输出 | 效果 |
|---|---|---|---|---|
| `mentalout/action/target_misidentification` | 行动·心理特有 | `DURATION_SECONDS` | `subjects:ES,target:E,[flow:F] → flow:F` | 强制支持目标控制的主体攻击指定实体。 |
| `mentalout/action/mental_stupor` | 行动·心理特有 | `DURATION_SECONDS` | `subjects:ES,[flow:F] → flow:F` | 冻结支持 AI 控制的主体。 |
| `mentalout/action/impression_manipulation` | 行动·心理特有 | `DURATION_SECONDS` | `subjects:ES,[flow:F] → flow:F` | 使主体将施术者视为友方。 |
| `mentalout/action/perception_mask` | 行动·心理特有 | `DURATION_SECONDS` | `observers:ES,hidden:E,[flow:F] → flow:F` | 使观察者无法感知隐藏实体。 |
| `mentalout/action/start_intrusion` | 行动·心理特有 | `DURATION_SECONDS` | `target:E,[flow:F] → flow:F` | 开始对目标的心灵潜入。 |
| `mentalout/action/end_intrusion` | 行动·心理特有 | — | `[flow:F] → flow:F` | 结束当前潜入会话。 |
| `mentalout/action/path_to` | 行动·心理特有 | `DURATION_SECONDS` | `subjects:ES,destination:CD,[flow:F] → flow:F` | 命令支持寻路控制的主体前往实体或位置目标。 |
| `mentalout/action/view_control` | 行动·心理特有 | `DURATION_SECONDS` | `subjects:ES,target:E,[flow:F] → flow:F` | 命令支持视线控制的主体持续看向目标。 |
| `mentalout/action/remove_control` | 行动·心理特有 | — | `subjects:ES,[flow:F] → flow:F` | 解除精密操作控制并暂时阻止自动召回重加。 |
| `mentalout/action/guard_mode` | 行动·心理特有 | `DURATION_SECONDS` | `subjects:ES,destination:CD,[flow:F] → flow:F` | 命令主体跟随实体或驻守地点并攻击威胁。 |

### 10.6 心理条件流程节点

| ID 后缀 | 类别 | 参数 | 输入 → 输出 | 成立条件 |
|---|---|---|---|---|
| `flow/health_ratio_branch` | 行动/流程·心理特有 | `HEALTH_PERCENT` | `subject:E,[flow:F] → true:F,false:F` | 主体生命百分比不高于阈值。 |
| `flow/distance_branch` | 行动/流程·心理特有 | `RANGE` | `subject:E,[flow:F] → true:F,false:F` | 主体与施术者同维度且在阈值内。 |
| `flow/entity_type_branch` | 行动/流程·心理特有 | `ENTITY_TYPE` | `subject:E,[flow:F] → true:F,false:F` | 主体符合实体大类。 |
| `flow/status_effect_branch` | 行动/流程·心理特有 | — | `subject:E,[flow:F] → true:F,false:F` | 主体至少具有一个状态效果。 |

## 11. 统一调控时应优先处理的问题

1. **能力枚举本地化错位**：`ControlCapability` 有 7 项，但心理参数文本只有 0–5；代码 5 是“直接控制”，当前 UI 显示成“守卫控制”，代码 6 没有文本。
2. **行动强度语义有两套**：矢量操作一般节点使用离散 `strength`，其余分类与动能冲击波使用连续 `power`；前者选档，后者按线性伤害和平方 CP 规则缩放。
3. **基础数值仍分散**：连续 `power` 的缩放公式已集中到 `ProgramPowerScale`，但射程、速度和标准 CP 仍由各 `Server*ProgramRuntime` 提供，尚不能通过一个配置面统一调整。
4. **重复目标节点已隐藏兼容**：各分类旧 `caster` 和 `look_target` 仍在注册表中供旧图解码，但节点库只显示共享版本；删除旧 ID 前仍需要正式迁移。
5. **入口 ID 不一致**：心理系为 `academy:program/entry/on_cast`，其他分类为 `academy:program/<category>/entry/on_cast`。
6. **隐藏兼容节点仍属于注册表**：心理系的 `entity_to_set`、`union`、`intersection`、`subtract`、`entity_position` 不在节点库显示，但旧程序仍可使用，调整或删除时必须提供迁移。
7. **端口类型宽于实际要求**：多项行动端口声明为通用 `E`，运行时才限制为生物、弹射物、磁性实体或可移动实体，容易产生“连线合法、执行失败”。应考虑增加显式生物/投射物/磁性目标转换或过滤节点。
8. **统一 Level 5 门槛**：客户端入口、服务端请求/保存/导入/执行和自动触发均校验分类等级达到 5；调整等级规则时必须同时修改两端，避免只隐藏 GUI 但仍可发包执行。
9. **世界查询与行动射程是两套上限**：共享查询通常固定 32 格，而电子束、采掘束、层流切割可达到 48 格；远距离行动需要由常量坐标或其他来源提供目标，不能完全依赖共享射线节点。
10. **不可逆行动混入事务**：伤害、方块破坏和冲击波使用空回滚；实体/方块位移和传送可回滚。多行动程序后段失败时，前段不可逆效果不会恢复。

建议将分类档位集中为一张 `ProgramBalanceProfile` 数据表，至少统一字段：`queryRange`、`actionRange`、`speed/impulse`、`damageScale`、`radius`、`cpCost`、`maxTargets`、`worldMutation`、`rollbackPolicy`，节点只引用档位键，不再自行硬编码数值。
