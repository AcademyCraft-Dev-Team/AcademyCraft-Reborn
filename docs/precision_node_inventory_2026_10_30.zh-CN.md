# 精密操作全部节点实测清单（2026-10-30）

本清单直接来自真实 `runClientDev` 进程在注册、客户端资源加载后对 `AbilityProgramDefinitions.all()`、`editorCatalog().entries()` 和真实 codec/schema 的只读导出。覆盖七个能力分类、通用代数、兼容隐藏节点以及当次加载的扩展。没有通过正则数声明行、手填数字或把跨能力共享节点重复计为独立节点。

原始机器清单：[`node_inventory_2026_10_30.json`](../validation/precision_nodes/node_inventory_2026_10_30.json)。导出器：[`PrecisionNodeCatalogAuditSmoke.java`](../src/gametest/java/org/academy/internal/client/validation/PrecisionNodeCatalogAuditSmoke.java)，仅在 `academy.precisionNodeCatalogAudit=true` 且独立测试目录中运行；不改世界、不执行节点动作、不关闭客户端。

## 计数口径与结论

- 去重后的注册节点 ID：**342**，其中编辑器目录可见 **277**、隐藏兼容 **65**。
- 通用节点 **189 = 155 可见 + 34 隐藏**；各能力专属节点合计 **153 = 122 可见 + 31 隐藏**。
- 各能力目录呈现发生数合计 **1476**，等于 `189 × 7 + 153`；它不是独立节点数。
- “可见”只表示 `Entry.visible`，还没有经过玩家已学技能、当前类别、搜索、图内是否已有入口等 UI 条件过滤。隐藏不等于无 executor；可见也不等于动作已经开放。
- 当次 `ProgramNodePalette` provider 数为 **0**，因此没有把玩家动态命名预设当作注册 ID。将来安装扩展或注册 provider 后必须重新导出；本报告不代表所有可能外部扩展。
- 所有非 `ENTRY` 类型都有对应 executor；编辑器类型与运行时 lookup 的不一致数为 **0**；同一 ID 在不同类别的默认配置/schema/可见状态差异数为 **0**。入口节点没有独立 executor 是现有 VM 的正常机制。
- 三系“还原为方块（未开放）”节点仍然可见并有 executor，但现有文案明确说明只提供检查、不执行还原；本表单独标记，不能以 executor 存在作为功能完成证明。

| 能力 | 目录发生数 | 可见 | 隐藏 | 其中专属 ID | 专属可见 / 隐藏 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 矢量操作 | 215 | 179 | 36 | 26 | 24 / 2 |
| 空力使 | 212 | 176 | 36 | 23 | 21 / 2 |
| 未元物质 | 198 | 162 | 36 | 9 | 7 / 2 |
| 电击使 | 214 | 178 | 36 | 25 | 23 / 2 |
| 原子崩坏 | 196 | 160 | 36 | 7 | 5 / 2 |
| 心理掌握 | 245 | 192 | 53 | 56 | 37 / 19 |
| 传送使 | 196 | 160 | 36 | 7 | 5 / 2 |

角色与纯度是不同维度，不能将 `VALUE` 角色一律当作可跨 tick 缓存的纯函数：

| 维度 | 实测去重数量 |
| --- | --- |
| role | ACTION=79, CONTROL=17, ENTRY=13, QUERY=85, SUSPEND=1, VALUE=147 |
| purity | ACTION=83, PURE=141, STATE=18, SUSPEND=1, WORLD_QUERY=99 |
| group | ACTION=84, COLLECTION=59, FILTER=33, FLOW=27, LOGIC=51, TARGET=80, VALUE=8 |

每能力角色统计（包含该能力呈现的通用节点）：

| 能力 | 角色数量 |
| --- | --- |
| 矢量操作 | ACTION=21, CONTROL=13, ENTRY=7, QUERY=52, SUSPEND=1, VALUE=121 |
| 空力使 | ACTION=20, CONTROL=13, ENTRY=7, QUERY=52, SUSPEND=1, VALUE=119 |
| 未元物质 | ACTION=6, CONTROL=13, ENTRY=7, QUERY=52, SUSPEND=1, VALUE=119 |
| 电击使 | ACTION=18, CONTROL=13, ENTRY=7, QUERY=56, SUSPEND=1, VALUE=119 |
| 原子崩坏 | ACTION=5, CONTROL=13, ENTRY=7, QUERY=51, SUSPEND=1, VALUE=119 |
| 心理掌握 | ACTION=11, CONTROL=17, ENTRY=7, QUERY=64, SUSPEND=1, VALUE=145 |
| 传送使 | ACTION=4, CONTROL=13, ENTRY=7, QUERY=52, SUSPEND=1, VALUE=119 |

## 文本完整性及需修正项

- 342 个去重 ID 的中文、英文节点名与说明键均存在；默认 schema 涉及的端口名称键也均存在。由此不能推断文本已经易懂或语义一致。
- 缺少配置字段名称键只有两项，且中英文均缺：`screen.academy.program.configuration.field.tier`（矢量“提取结构”）与 `screen.academy.program.configuration.field.charge_tier`（空力“层流切割”）。建议统一为“提取档位 / Extraction tier”和“蓄力档位 / Charge tier”，避免直接把 wire 字段暴露给用户。
- 同名并不自动意味着可以合并：三系结构动作名称相同但施力、门槛、附件与费用不同；各系“施放时”入口也有独立类别 ID。应优先合并目录卡片和参数面板，保持运行时 ID、强类型端口和能力语义。
- 真正容易混淆的可见同名项包括：通用 `academy:program/core/collection/entity/random` 与心理旧 `academy:program/collection/random` 都叫“随机实体”；方向集合取项与心理两点间方向旧节点都叫“获取方向”。它们端口和作用不同，不能只按中文标题替换。

## 状态、兼容与动态 schema 边界

以下逐项表保留全部 342 个 ID，不删隐藏项。`?` 表示输入可不连接，省略连接时可能使用配置或默认目标；它不意味着无权限、无费用或无失败。端口类型省略统一前缀 `academy:program_type/`。输出连接上限通常为 `Integer.MAX_VALUE`，精确值、两种语言端口名、配置可选端口及默认 schema 全部保留在 JSON。

**表内 schema 仅对应默认配置。** 数字类型、集合域、运算符、变量类型和可变输入数量等会改变端口；JSON 中 `configuration_fields.<field>.finite_options` 同时保留 UI 选项和“其余字段保持默认”的可解码变体 schema，不是无限字面量、所有字段笛卡尔积或所有游戏状态的验收。`inputs` 等整数字段还可生成不同数量的端口，应以每个图实例的 `resolvedSchema` 为准。

隐藏兼容由三条现有规则组成：

1. 通用目录隐藏旧布尔/整数/大整数/浮点常量和三种数值族的算术、比较专用 ID，使用已有“数值常量/运算/比较/转换”等统一入口。
2. 各系重复的 `target/caster`、`target/look_target`、`target/look_living` 由通用查询代替；心理旧过滤路径也由通用过滤器代替。
3. `PrecisionProgramAliases` 对心理五种旧别名（实体转集合、并/交/差、实体位置）做显式类型及端口迁移。保留隐藏类型不等于所有旧图都已自动迁移，迁移后仍应经 compiler/VM 验证。

## 通用节点（189 个，只列一次）

### 选取与查询（48）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/core/query/block_normal` | 检测方块法向量<br>Detect Block Normal | `QUERY` / `WORLD_QUERY` | 可见 | `entity:entity_reference` | `normal?:direction` | `{"mode":"view"}` | — |
| `academy:program/core/query/block_volume` | 获取点到点方块集合<br>Block Volume Between Points | `QUERY` / `WORLD_QUERY` | 可见 | `first:block_position`<br>`second:block_position` | `blocks?:block_position_set` | `{}` | — |
| `academy:program/core/query/caster` | 能力者<br>Caster | `QUERY` / `WORLD_QUERY` | 可见 | — | `entity?:entity_reference` | `{}` | — |
| `academy:program/core/query/chat_read` | 读取聊天记录<br>Read Chat History | `VALUE` / `STATE` | 可见 | `distance?:integer`<br>`start?:integer`<br>`length?:integer` | `text?:text`<br>`exists?:boolean` | `{"distance":0,"start":0,"length":256}` | — |
| `academy:program/core/query/chat_trigger_message` | 本次聊天消息<br>Trigger Chat Message | `VALUE` / `STATE` | 可见 | — | `text?:text`<br>`exists?:boolean` | `{}` | — |
| `academy:program/core/query/damage_amount` | 受击伤害值<br>Incoming Damage Amount | `QUERY` / `WORLD_QUERY` | 可见 | — | `amount?:float` | `{}` | — |
| `academy:program/core/query/damage_attacker` | 受击来源<br>Damage Attacker | `QUERY` / `WORLD_QUERY` | 可见 | — | `entity?:entity_reference` | `{}` | — |
| `academy:program/core/query/entities_around` | 坐标附近实体<br>Entities Around Position | `QUERY` / `WORLD_QUERY` | 可见 | `center:world_position`<br>`radius:float` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/query/entity_data` | 实体数据查询<br>Entity Data | `QUERY` / `WORLD_QUERY` | 可见 | `entity:entity_reference` | `value?:float` | `{"data":"health"}` | — |
| `academy:program/core/query/entity_height` | 实体高度<br>Entity Height | `QUERY` / `WORLD_QUERY` | 可见 | `entity:entity_reference` | `height?:float` | `{}` | — |
| `academy:program/core/query/entity_look_direction` | 实体视线方向<br>Entity Look Direction | `QUERY` / `WORLD_QUERY` | 可见 | `entity:entity_reference` | `direction?:direction` | `{}` | — |
| `academy:program/core/query/entity_motion` | 实体速度向量<br>Entity Motion Vector | `QUERY` / `WORLD_QUERY` | 可见 | `entity:entity_reference` | `vector?:vector`<br>`speed?:float` | `{}` | — |
| `academy:program/core/query/entity_movement_direction` | 实体运动方向<br>Entity Movement Direction | `QUERY` / `WORLD_QUERY` | 可见 | `entity:entity_reference` | `direction?:direction` | `{}` | — |
| `academy:program/core/query/entity_position` | 实体坐标<br>Entity Position | `QUERY` / `WORLD_QUERY` | 可见 | `entity:entity_reference` | `position?:world_position` | `{"anchor":"feet"}` | — |
| `academy:program/core/query/game_time` | 游戏时间<br>Game Time | `QUERY` / `WORLD_QUERY` | 可见 | — | `time?:big_integer` | `{}` | — |
| `academy:program/core/query/look_structure` | 视线结构目标<br>Look Structure Target | `QUERY` / `WORLD_QUERY` | 可见 | — | `structure?:structure_reference` | `{}` | — |
| `academy:program/core/query/look_target` | 能力者视线目标<br>Caster Look Target | `QUERY` / `WORLD_QUERY` | 可见 | — | `entity?:entity_reference` | `{"target_type":"entity"}` | — |
| `academy:program/core/query/loop_index` | 循环触发序号<br>Loop Trigger Index | `QUERY` / `WORLD_QUERY` | 可见 | — | `loop_index?:big_integer` | `{}` | — |
| `academy:program/core/query/melee_target` | 近战目标<br>Melee Target | `QUERY` / `WORLD_QUERY` | 可见 | — | `entity?:entity_reference` | `{}` | — |
| `academy:program/core/query/nearest_structure` | 选取点最近结构<br>Nearest structure to point | `QUERY` / `WORLD_QUERY` | 可见 | `center:world_position`<br>`radius:float` | `structure?:structure_reference` | `{}` | — |
| `academy:program/core/query/raycast_block` | 方块射线检测<br>Raycast Block | `QUERY` / `WORLD_QUERY` | 可见 | `origin:world_position`<br>`direction:direction`<br>`range:float` | `block?:block_position` | `{}` | — |
| `academy:program/core/query/raycast_entity` | 实体射线检测<br>Raycast Entity | `QUERY` / `WORLD_QUERY` | 可见 | `origin:world_position`<br>`direction:direction`<br>`range:float` | `entity?:entity_reference` | `{}` | — |
| `academy:program/core/query/structure_motion` | 结构物理状态<br>Structure Physics State | `QUERY` / `WORLD_QUERY` | 可见 | `structure:structure_reference` | `position?:world_position`<br>`velocity?:vector`<br>`angular_velocity?:vector`<br>`mass?:float` | `{}` | — |
| `academy:program/core/query/structure_valid` | 结构是否有效<br>Is structure valid | `QUERY` / `WORLD_QUERY` | 可见 | `structure:structure_reference` | `result?:boolean` | `{}` | — |
| `academy:program/core/query/structures_around` | 选取点附近结构集合<br>Structures near point | `QUERY` / `WORLD_QUERY` | 可见 | `center:world_position`<br>`radius:float` | `structures?:structure_set` | `{}` | — |
| `academy:program/core/spatial/block_position` | 方块坐标<br>Block Position | `VALUE` / `PURE` | 可见 | `x?:integer`<br>`y?:integer`<br>`z?:integer` | `position?:block_position` | `{"dimension":"minecraft:overworld","x":0,"y":0,"z":0}` | — |
| `academy:program/core/spatial/block_position_components` | 分解方块坐标<br>Block Position Components | `VALUE` / `PURE` | 可见 | `position:block_position` | `x?:integer`<br>`y?:integer`<br>`z?:integer` | `{}` | — |
| `academy:program/core/spatial/block_position_construct` | 构造方块坐标<br>Construct Block Position | `VALUE` / `PURE` | 可见 | `x:integer`<br>`y:integer`<br>`z:integer` | `position?:block_position` | `{"dimension":"minecraft:overworld"}` | — |
| `academy:program/core/spatial/block_to_center` | 方块中心坐标<br>Block Center Position | `VALUE` / `PURE` | 可见 | `block:block_position` | `position?:world_position` | `{}` | — |
| `academy:program/core/spatial/direction` | 方向<br>Direction | `VALUE` / `PURE` | 可见 | `x?:float`<br>`y?:float`<br>`z?:float` | `direction?:direction` | `{"x":0.0,"y":0.0,"z":1.0}` | — |
| `academy:program/core/spatial/direction_between` | 两点间方向<br>Direction Between Positions | `VALUE` / `PURE` | 可见 | `from:world_position`<br>`to:world_position` | `direction?:direction` | `{}` | — |
| `academy:program/core/spatial/direction_components` | 分解方向<br>Direction Components | `VALUE` / `PURE` | 可见 | `direction:direction` | `x?:float`<br>`y?:float`<br>`z?:float` | `{}` | — |
| `academy:program/core/spatial/direction_construct` | 构造方向<br>Construct Direction | `VALUE` / `PURE` | 可见 | `x:float`<br>`y:float`<br>`z:float` | `direction?:direction` | `{}` | — |
| `academy:program/core/spatial/direction_dot` | 方向点积<br>Direction Dot Product | `VALUE` / `PURE` | 可见 | `left:direction`<br>`right:direction` | `result?:float` | `{}` | — |
| `academy:program/core/spatial/direction_opposite` | 反向<br>Opposite Direction | `VALUE` / `PURE` | 可见 | `direction:direction` | `direction?:direction` | `{}` | — |
| `academy:program/core/spatial/position_to_block` | 世界坐标转方块<br>World Position To Block | `VALUE` / `PURE` | 可见 | `position:world_position` | `block?:block_position` | `{}` | — |
| `academy:program/core/spatial/structure_anchor` | 结构局部锚点<br>Structure local anchor | `VALUE` / `PURE` | 可见 | `structure:structure_reference`<br>`local_x:integer`<br>`local_y:integer`<br>`local_z:integer` | `anchor?:structure_anchor` | `{}` | — |
| `academy:program/core/spatial/structure_pose` | 结构目标姿态<br>Structure target rotation | `VALUE` / `PURE` | 可见 | `axis:direction`<br>`angle_radians:float` | `pose?:structure_pose` | `{}` | — |
| `academy:program/core/spatial/text_to_vec3` | 文本转 Vec3<br>Text to Vec3 | `VALUE` / `STATE` | 可见 | `text:text`<br>`match_index?:integer`<br>`reference?:entity_reference`<br>`mode?:text` | `result?:world_position`<br>`success?:boolean` | `{"type":"world_position","mode":"extract","match_index":0}` | — |
| `academy:program/core/spatial/vec3_operation` | Vec3 运算<br>Vec3 Operation | `VALUE` / `PURE` | 可见 | `left:direction`<br>`right:direction` | `result?:direction` | `{"type":"direction","operator":"add"}` | — |
| `academy:program/core/spatial/vector_components` | 分解向量<br>Vector Components | `VALUE` / `PURE` | 可见 | `value:vector` | `x?:float`<br>`y?:float`<br>`z?:float` | `{}` | — |
| `academy:program/core/spatial/vector_construct` | 构造向量<br>Construct Vector | `VALUE` / `PURE` | 可见 | `x:float`<br>`y:float`<br>`z:float` | `vector?:vector` | `{}` | — |
| `academy:program/core/spatial/world_position` | 世界坐标<br>World Position | `VALUE` / `PURE` | 可见 | `x?:float`<br>`y?:float`<br>`z?:float` | `position?:world_position` | `{"dimension":"minecraft:overworld","x":0.0,"y":0.0,"z":0.0}` | — |
| `academy:program/core/spatial/world_position_components` | 分解世界坐标<br>World Position Components | `VALUE` / `PURE` | 可见 | `position:world_position` | `x?:float`<br>`y?:float`<br>`z?:float` | `{}` | — |
| `academy:program/core/spatial/world_position_construct` | 构造世界坐标<br>Construct World Position | `VALUE` / `PURE` | 可见 | `x:float`<br>`y:float`<br>`z:float` | `position?:world_position` | `{"dimension":"minecraft:overworld"}` | — |
| `academy:program/core/spatial/world_position_distance` | 坐标间距离<br>Distance Between Positions | `VALUE` / `PURE` | 可见 | `left:world_position`<br>`right:world_position` | `result?:float` | `{}` | — |
| `academy:program/core/spatial/world_position_offset` | 偏移世界坐标<br>Offset World Position | `VALUE` / `PURE` | 可见 | `position:world_position`<br>`direction:direction`<br>`distance:float` | `position?:world_position` | `{}` | — |
| `academy:program/core/spatial/world_position_same_dimension` | 同一维度<br>Same Dimension | `VALUE` / `PURE` | 可见 | `left:world_position`<br>`right:world_position` | `result?:boolean` | `{}` | — |

### 集合（47）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/core/collection/block_position/contains` | 方块坐标集合包含<br>Block Position Set Contains | `VALUE` / `PURE` | 可见 | `values:block_position_set`<br>`value:block_position` | `result?:boolean` | `{}` | — |
| `academy:program/core/collection/block_position/difference` | 方块坐标集合差集<br>Block Position Set Difference | `VALUE` / `PURE` | 可见 | `left:block_position_set`<br>`right:block_position_set` | `values?:block_position_set` | `{}` | — |
| `academy:program/core/collection/block_position/empty` | 构建方块坐标集合<br>Build Block Position Set | `VALUE` / `PURE` | 可见 | — | `values?:block_position_set` | `{"inputs":0}` | — |
| `academy:program/core/collection/block_position/get` | 获取方块坐标<br>Get Block Position | `VALUE` / `PURE` | 可见 | `values:block_position_set`<br>`index:integer` | `value?:block_position` | `{}` | — |
| `academy:program/core/collection/block_position/intersection` | 方块坐标集合交集<br>Intersect Block Position Sets | `VALUE` / `PURE` | 可见 | `left:block_position_set`<br>`right:block_position_set` | `values?:block_position_set` | `{}` | — |
| `academy:program/core/collection/block_position/random` | 随机方块坐标<br>Random Block Position | `QUERY` / `WORLD_QUERY` | 可见 | `blocks:block_position_set` | `block?:block_position` | `{}` | — |
| `academy:program/core/collection/block_position/singleton` | 单方块坐标集合<br>Single Block Position Set | `VALUE` / `PURE` | 可见 | `value:block_position` | `values?:block_position_set` | `{}` | — |
| `academy:program/core/collection/block_position/size` | 方块坐标集合大小<br>Block Position Set Size | `VALUE` / `PURE` | 可见 | `values:block_position_set` | `size?:integer` | `{}` | — |
| `academy:program/core/collection/block_position/union` | 方块坐标集合并集<br>Union Block Position Sets | `VALUE` / `PURE` | 可见 | `left:block_position_set`<br>`right:block_position_set` | `values?:block_position_set` | `{}` | — |
| `academy:program/core/collection/direction/contains` | 方向集合包含<br>Direction Set Contains | `VALUE` / `PURE` | 可见 | `values:direction_set`<br>`value:direction` | `result?:boolean` | `{}` | — |
| `academy:program/core/collection/direction/difference` | 方向集合差集<br>Direction Set Difference | `VALUE` / `PURE` | 可见 | `left:direction_set`<br>`right:direction_set` | `values?:direction_set` | `{}` | — |
| `academy:program/core/collection/direction/empty` | 构建方向集合<br>Build Direction Set | `VALUE` / `PURE` | 可见 | — | `values?:direction_set` | `{"inputs":0}` | — |
| `academy:program/core/collection/direction/get` | 获取方向<br>Get Direction | `VALUE` / `PURE` | 可见 | `values:direction_set`<br>`index:integer` | `value?:direction` | `{}` | — |
| `academy:program/core/collection/direction/intersection` | 方向集合交集<br>Intersect Direction Sets | `VALUE` / `PURE` | 可见 | `left:direction_set`<br>`right:direction_set` | `values?:direction_set` | `{}` | — |
| `academy:program/core/collection/direction/random` | 随机方向<br>Random Direction | `QUERY` / `WORLD_QUERY` | 可见 | `directions:direction_set` | `direction?:direction` | `{}` | — |
| `academy:program/core/collection/direction/singleton` | 单方向集合<br>Single Direction Set | `VALUE` / `PURE` | 可见 | `value:direction` | `values?:direction_set` | `{}` | — |
| `academy:program/core/collection/direction/size` | 方向集合大小<br>Direction Set Size | `VALUE` / `PURE` | 可见 | `values:direction_set` | `size?:integer` | `{}` | — |
| `academy:program/core/collection/direction/union` | 方向集合并集<br>Union Direction Sets | `VALUE` / `PURE` | 可见 | `left:direction_set`<br>`right:direction_set` | `values?:direction_set` | `{}` | — |
| `academy:program/core/collection/entity/contains` | 实体集合包含<br>Entity Set Contains | `VALUE` / `PURE` | 可见 | `values:entity_set`<br>`value:entity_reference` | `result?:boolean` | `{}` | — |
| `academy:program/core/collection/entity/difference` | 实体集合差集<br>Entity Set Difference | `VALUE` / `PURE` | 可见 | `left:entity_set`<br>`right:entity_set` | `values?:entity_set` | `{}` | — |
| `academy:program/core/collection/entity/empty` | 构建实体集合<br>Build Entity Set | `VALUE` / `PURE` | 可见 | — | `values?:entity_set` | `{"inputs":0}` | — |
| `academy:program/core/collection/entity/get` | 获取实体<br>Get Entity | `VALUE` / `PURE` | 可见 | `values:entity_set`<br>`index:integer` | `value?:entity_reference` | `{}` | — |
| `academy:program/core/collection/entity/intersection` | 实体集合交集<br>Intersect Entity Sets | `VALUE` / `PURE` | 可见 | `left:entity_set`<br>`right:entity_set` | `values?:entity_set` | `{}` | — |
| `academy:program/core/collection/entity/nearest_to_position` | 点距离最近实体<br>Nearest Entity to Point | `QUERY` / `WORLD_QUERY` | 可见 | `position:world_position`<br>`entities:entity_set` | `entity?:entity_reference` | `{}` | — |
| `academy:program/core/collection/entity/random` | 随机实体<br>Random Entity | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set` | `entity?:entity_reference` | `{}` | — |
| `academy:program/core/collection/entity/singleton` | 单实体集合<br>Single Entity Set | `VALUE` / `PURE` | 可见 | `value:entity_reference` | `values?:entity_set` | `{}` | — |
| `academy:program/core/collection/entity/size` | 实体集合大小<br>Entity Set Size | `VALUE` / `PURE` | 可见 | `values:entity_set` | `size?:integer` | `{}` | — |
| `academy:program/core/collection/entity/union` | 实体集合并集<br>Union Entity Sets | `VALUE` / `PURE` | 可见 | `left:entity_set`<br>`right:entity_set` | `values?:entity_set` | `{}` | — |
| `academy:program/core/collection/point/distance_sort` | 点距离排序<br>Point Distance Sort | `QUERY` / `WORLD_QUERY` | 可见 | `values:entity_set`<br>`origin:world_position` | `values?:entity_set` | `{"type":"entity","order":"ascending"}` | — |
| `academy:program/core/collection/structure/contains` | 结构集合包含<br>Structure Set Contains | `VALUE` / `PURE` | 可见 | `values:structure_set`<br>`value:structure_reference` | `result?:boolean` | `{}` | — |
| `academy:program/core/collection/structure/difference` | 结构集合差集<br>Structure Set Difference | `VALUE` / `PURE` | 可见 | `left:structure_set`<br>`right:structure_set` | `values?:structure_set` | `{}` | — |
| `academy:program/core/collection/structure/empty` | 构建结构集合<br>Build Structure Set | `VALUE` / `PURE` | 可见 | — | `values?:structure_set` | `{"inputs":0}` | — |
| `academy:program/core/collection/structure/get` | 获取结构<br>Get Structure | `VALUE` / `PURE` | 可见 | `values:structure_set`<br>`index:integer` | `value?:structure_reference` | `{}` | — |
| `academy:program/core/collection/structure/intersection` | 结构集合交集<br>Intersect Structure Sets | `VALUE` / `PURE` | 可见 | `left:structure_set`<br>`right:structure_set` | `values?:structure_set` | `{}` | — |
| `academy:program/core/collection/structure/nearest_to_position` | 集合中距选取点最近结构<br>Nearest structure in set | `QUERY` / `WORLD_QUERY` | 可见 | `position:world_position`<br>`structures:structure_set` | `structure?:structure_reference` | `{}` | — |
| `academy:program/core/collection/structure/singleton` | 单结构集合<br>Single Structure Set | `VALUE` / `PURE` | 可见 | `value:structure_reference` | `values?:structure_set` | `{}` | — |
| `academy:program/core/collection/structure/size` | 结构集合大小<br>Structure Set Size | `VALUE` / `PURE` | 可见 | `values:structure_set` | `size?:integer` | `{}` | — |
| `academy:program/core/collection/structure/union` | 结构集合并集<br>Union Structure Sets | `VALUE` / `PURE` | 可见 | `left:structure_set`<br>`right:structure_set` | `values?:structure_set` | `{}` | — |
| `academy:program/core/collection/world_position/contains` | 世界坐标集合包含<br>World Position Set Contains | `VALUE` / `PURE` | 可见 | `values:world_position_set`<br>`value:world_position` | `result?:boolean` | `{}` | — |
| `academy:program/core/collection/world_position/difference` | 世界坐标集合差集<br>World Position Set Difference | `VALUE` / `PURE` | 可见 | `left:world_position_set`<br>`right:world_position_set` | `values?:world_position_set` | `{}` | — |
| `academy:program/core/collection/world_position/empty` | 构建世界坐标集合<br>Build World Position Set | `VALUE` / `PURE` | 可见 | — | `values?:world_position_set` | `{"inputs":0}` | — |
| `academy:program/core/collection/world_position/get` | 获取世界坐标<br>Get World Position | `VALUE` / `PURE` | 可见 | `values:world_position_set`<br>`index:integer` | `value?:world_position` | `{}` | — |
| `academy:program/core/collection/world_position/intersection` | 世界坐标集合交集<br>Intersect World Position Sets | `VALUE` / `PURE` | 可见 | `left:world_position_set`<br>`right:world_position_set` | `values?:world_position_set` | `{}` | — |
| `academy:program/core/collection/world_position/random` | 随机世界坐标<br>Random World Position | `QUERY` / `WORLD_QUERY` | 可见 | `positions:world_position_set` | `position?:world_position` | `{}` | — |
| `academy:program/core/collection/world_position/singleton` | 单世界坐标集合<br>Single World Position Set | `VALUE` / `PURE` | 可见 | `value:world_position` | `values?:world_position_set` | `{}` | — |
| `academy:program/core/collection/world_position/size` | 世界坐标集合大小<br>World Position Set Size | `VALUE` / `PURE` | 可见 | `values:world_position_set` | `size?:integer` | `{}` | — |
| `academy:program/core/collection/world_position/union` | 世界坐标集合并集<br>Union World Position Sets | `VALUE` / `PURE` | 可见 | `left:world_position_set`<br>`right:world_position_set` | `values?:world_position_set` | `{}` | — |

### 筛选（19）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/core/filter/block/exact` | 方块精确过滤<br>Exact Block Filter | `QUERY` / `WORLD_QUERY` | 可见 | `blocks:block_position_set`<br>`selectors?:text` | `blocks?:block_position_set` | `{"selectors":"minecraft:stone"}` | — |
| `academy:program/core/filter/entity/alive` | 过滤存活实体<br>Filter Living Entities | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/allied_to` | 过滤盟友实体<br>Filter Allied Entities | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`reference:entity_reference` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/distance` | 过滤范围内实体<br>Filter Entities By Distance | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`center:world_position`<br>`radius:float` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/exact` | 实体精确过滤<br>Exact Entity Filter | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`selectors?:text` | `entities?:entity_set` | `{"selectors":"minecraft:player"}` | — |
| `academy:program/core/filter/entity/has_target` | 过滤持有目标实体<br>Filter Entities With Target | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/health_at_least` | 过滤生命值下限<br>Filter Minimum Health | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`percent:float` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/health_at_most` | 过滤生命值上限<br>Filter Maximum Health | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`percent:float` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/hostile_to` | 过滤敌对实体<br>Filter Hostile Entities | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`reference:entity_reference` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/last_damaged_by` | 过滤最近受击来源<br>Filter Last Attacker | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`attacker:entity_reference` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/max_health_at_least` | 过滤最大生命值下限<br>Filter Minimum Maximum Health | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`health:float` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/max_health_at_most` | 过滤最大生命值上限<br>Filter Maximum Maximum Health | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`health:float` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/targeted_by` | 过滤目标指向<br>Filter Current Target | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`target:entity_reference` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/entity/type` | 过滤实体类型<br>Filter Entity Type | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set` | `entities?:entity_set` | `{"type":"living"}` | — |
| `academy:program/core/filter/entity/visible_from` | 过滤可见实体<br>Filter Visible Entities | `QUERY` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`observer:entity_reference` | `entities?:entity_set` | `{}` | — |
| `academy:program/core/filter/structure/blocks` | 按方块或标签筛选结构<br>Filter structures by block or tag | `QUERY` / `WORLD_QUERY` | 可见 | `structures:structure_set`<br>`selectors?:text`<br>`match?:text` | `structures?:structure_set` | `{"selectors":"minecraft:iron_block","match":"any"}` | — |
| `academy:program/core/filter/structure/distance` | 筛选选取点附近结构<br>Filter structures near point | `QUERY` / `WORLD_QUERY` | 可见 | `center:world_position`<br>`radius:float`<br>`structures:structure_set` | `structures?:structure_set` | `{}` | — |
| `academy:program/core/filter/structure/source` | 按来源筛选结构<br>Filter structures by source | `QUERY` / `WORLD_QUERY` | 可见 | `structures:structure_set`<br>`source_mode?:text`<br>`skill?:text` | `structures?:structure_set` | `{"source_mode":"owned_by_caster","skill":"academy:kinetic_throw"}` | — |
| `academy:program/core/filter/structure/valid` | 筛选有效结构<br>Filter valid structures | `QUERY` / `WORLD_QUERY` | 可见 | `structures:structure_set` | `structures?:structure_set` | `{}` | — |

### 逻辑与状态（46）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/core/logic/big_integer/add` | 大整数相加<br>Add Big Integers | `VALUE` / `PURE` | 隐藏/兼容 | `left:big_integer`<br>`right:big_integer` | `result?:big_integer` | `{}` | — |
| `academy:program/core/logic/big_integer/divide` | 大整数相除<br>Divide Big Integers | `VALUE` / `PURE` | 隐藏/兼容 | `left:big_integer`<br>`right:big_integer` | `result?:big_integer` | `{}` | — |
| `academy:program/core/logic/big_integer/equal` | 大整数相等<br>Big Integers Equal | `VALUE` / `PURE` | 隐藏/兼容 | `left:big_integer`<br>`right:big_integer` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/big_integer/greater` | 大整数大于<br>Big Integer Greater Than | `VALUE` / `PURE` | 隐藏/兼容 | `left:big_integer`<br>`right:big_integer` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/big_integer/greater_equal` | 大整数大于等于<br>Big Integer At Least | `VALUE` / `PURE` | 隐藏/兼容 | `left:big_integer`<br>`right:big_integer` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/big_integer/less` | 大整数小于<br>Big Integer Less Than | `VALUE` / `PURE` | 隐藏/兼容 | `left:big_integer`<br>`right:big_integer` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/big_integer/less_equal` | 大整数小于等于<br>Big Integer At Most | `VALUE` / `PURE` | 隐藏/兼容 | `left:big_integer`<br>`right:big_integer` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/big_integer/modulo` | 大整数取余<br>Big Integer Remainder | `VALUE` / `PURE` | 隐藏/兼容 | `left:big_integer`<br>`right:big_integer` | `result?:big_integer` | `{}` | — |
| `academy:program/core/logic/big_integer/multiply` | 大整数相乘<br>Multiply Big Integers | `VALUE` / `PURE` | 隐藏/兼容 | `left:big_integer`<br>`right:big_integer` | `result?:big_integer` | `{}` | — |
| `academy:program/core/logic/big_integer/subtract` | 大整数相减<br>Subtract Big Integers | `VALUE` / `PURE` | 隐藏/兼容 | `left:big_integer`<br>`right:big_integer` | `result?:big_integer` | `{}` | — |
| `academy:program/core/logic/block_position/equal` | 方块坐标相等<br>Block Positions Equal | `VALUE` / `PURE` | 可见 | `left:block_position`<br>`right:block_position` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/boolean/and` | 布尔与<br>Boolean AND | `VALUE` / `PURE` | 可见 | `left:boolean`<br>`right:boolean` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/boolean/not` | 布尔非<br>Boolean NOT | `VALUE` / `PURE` | 可见 | `value:boolean` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/boolean/or` | 布尔或<br>Boolean OR | `VALUE` / `PURE` | 可见 | `left:boolean`<br>`right:boolean` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/boolean/xor` | 布尔异或<br>Boolean XOR | `VALUE` / `PURE` | 可见 | `left:boolean`<br>`right:boolean` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/direction/equal` | 方向相等<br>Directions Equal | `VALUE` / `PURE` | 可见 | `left:direction`<br>`right:direction` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/entity/equal` | 实体相等<br>Entities Equal | `VALUE` / `PURE` | 可见 | `left:entity_reference`<br>`right:entity_reference` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/float/add` | 浮点数相加<br>Add Floats | `VALUE` / `PURE` | 隐藏/兼容 | `left:float`<br>`right:float` | `result?:float` | `{}` | — |
| `academy:program/core/logic/float/divide` | 浮点数相除<br>Divide Floats | `VALUE` / `PURE` | 隐藏/兼容 | `left:float`<br>`right:float` | `result?:float` | `{}` | — |
| `academy:program/core/logic/float/equal` | 浮点数相等<br>Floats Equal | `VALUE` / `PURE` | 隐藏/兼容 | `left:float`<br>`right:float` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/float/greater` | 浮点数大于<br>Float Greater Than | `VALUE` / `PURE` | 隐藏/兼容 | `left:float`<br>`right:float` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/float/greater_equal` | 浮点数大于等于<br>Float At Least | `VALUE` / `PURE` | 隐藏/兼容 | `left:float`<br>`right:float` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/float/less` | 浮点数小于<br>Float Less Than | `VALUE` / `PURE` | 隐藏/兼容 | `left:float`<br>`right:float` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/float/less_equal` | 浮点数小于等于<br>Float At Most | `VALUE` / `PURE` | 隐藏/兼容 | `left:float`<br>`right:float` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/float/modulo` | 浮点数取余<br>Float Remainder | `VALUE` / `PURE` | 隐藏/兼容 | `left:float`<br>`right:float` | `result?:float` | `{}` | — |
| `academy:program/core/logic/float/multiply` | 浮点数相乘<br>Multiply Floats | `VALUE` / `PURE` | 隐藏/兼容 | `left:float`<br>`right:float` | `result?:float` | `{}` | — |
| `academy:program/core/logic/float/subtract` | 浮点数相减<br>Subtract Floats | `VALUE` / `PURE` | 隐藏/兼容 | `left:float`<br>`right:float` | `result?:float` | `{}` | — |
| `academy:program/core/logic/integer/add` | 整数相加<br>Add Integers | `VALUE` / `PURE` | 隐藏/兼容 | `left:integer`<br>`right:integer` | `result?:integer` | `{}` | — |
| `academy:program/core/logic/integer/divide` | 整数相除<br>Divide Integers | `VALUE` / `PURE` | 隐藏/兼容 | `left:integer`<br>`right:integer` | `result?:integer` | `{}` | — |
| `academy:program/core/logic/integer/equal` | 整数相等<br>Integers Equal | `VALUE` / `PURE` | 隐藏/兼容 | `left:integer`<br>`right:integer` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/integer/greater` | 整数大于<br>Integer Greater Than | `VALUE` / `PURE` | 隐藏/兼容 | `left:integer`<br>`right:integer` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/integer/greater_equal` | 整数大于等于<br>Integer At Least | `VALUE` / `PURE` | 隐藏/兼容 | `left:integer`<br>`right:integer` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/integer/less` | 整数小于<br>Integer Less Than | `VALUE` / `PURE` | 隐藏/兼容 | `left:integer`<br>`right:integer` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/integer/less_equal` | 整数小于等于<br>Integer At Most | `VALUE` / `PURE` | 隐藏/兼容 | `left:integer`<br>`right:integer` | `result?:boolean` | `{}` | — |
| `academy:program/core/logic/integer/modulo` | 整数取余<br>Integer Remainder | `VALUE` / `PURE` | 隐藏/兼容 | `left:integer`<br>`right:integer` | `result?:integer` | `{}` | — |
| `academy:program/core/logic/integer/multiply` | 整数相乘<br>Multiply Integers | `VALUE` / `PURE` | 隐藏/兼容 | `left:integer`<br>`right:integer` | `result?:integer` | `{}` | — |
| `academy:program/core/logic/integer/subtract` | 整数相减<br>Subtract Integers | `VALUE` / `PURE` | 隐藏/兼容 | `left:integer`<br>`right:integer` | `result?:integer` | `{}` | — |
| `academy:program/core/logic/numeric/arithmetic` | 数值运算<br>Numeric Arithmetic | `VALUE` / `PURE` | 可见 | `left:integer`<br>`right:integer` | `result?:integer` | `{"type":"integer","operator":"add"}` | — |
| `academy:program/core/logic/numeric/compare` | 数值比较<br>Compare Numbers | `VALUE` / `PURE` | 可见 | `left:integer`<br>`right:integer` | `result?:boolean` | `{"type":"integer","operator":"equal"}` | — |
| `academy:program/core/logic/numeric/convert` | 数值类型转换<br>Convert Number | `VALUE` / `PURE` | 可见 | `value:float` | `result?:integer` | `{"source_type":"float","target_type":"integer"}` | — |
| `academy:program/core/logic/select_value` | 条件取值<br>Select Value | `VALUE` / `PURE` | 可见 | `condition:boolean`<br>`when_true:boolean`<br>`when_false:boolean` | `value?:boolean` | `{"type":"academy:program_type/boolean"}` | — |
| `academy:program/core/logic/text/compare` | 判断字符串<br>Compare Text | `VALUE` / `PURE` | 可见 | `left:text`<br>`right:text`<br>`mode?:text` | `result?:boolean` | `{"mode":"equals","ignore_case":false}` | — |
| `academy:program/core/logic/text/split` | 文本分割<br>Split text | `VALUE` / `PURE` | 可见 | `text:text`<br>`fragment_index?:integer`<br>`mode?:text`<br>`delimiter?:text` | `text?:text`<br>`count?:integer`<br>`exists?:boolean` | `{"mode":"sentence","delimiter":",","fragment_index":0,"trim":true,"skip_empty":true}` | — |
| `academy:program/core/logic/world_position/equal` | 世界坐标相等<br>World Positions Equal | `VALUE` / `PURE` | 可见 | `left:world_position`<br>`right:world_position` | `result?:boolean` | `{}` | — |
| `academy:program/core/state/shared_variable_get` | 读取共享变量<br>Read Shared Variable | `VALUE` / `STATE` | 可见 | `name?:text` | `value?:text`<br>`exists?:boolean` | `{"name":"value","type":"academy:program_type/text"}` | — |
| `academy:program/core/state/variable_get` | 读取变量<br>Read Variable | `VALUE` / `STATE` | 可见 | `name?:text` | `value?:boolean` | `{"name":"value","type":"academy:program_type/boolean"}` | — |

### 触发与流程（20）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/core/collection/block_position/foreach` | 遍历方块坐标<br>For Each Block Position | `CONTROL` / `STATE` | 可见 | `flow:flow`<br>`values:block_position_set` | `body?:flow`<br>`done?:flow`<br>`value?:block_position`<br>`index?:integer` | `{}` | — |
| `academy:program/core/collection/direction/foreach` | 遍历方向<br>For Each Direction | `CONTROL` / `STATE` | 可见 | `flow:flow`<br>`values:direction_set` | `body?:flow`<br>`done?:flow`<br>`value?:direction`<br>`index?:integer` | `{}` | — |
| `academy:program/core/collection/entity/foreach` | 遍历实体<br>For Each Entity | `CONTROL` / `STATE` | 可见 | `flow:flow`<br>`values:entity_set` | `body?:flow`<br>`done?:flow`<br>`value?:entity_reference`<br>`index?:integer` | `{}` | — |
| `academy:program/core/collection/structure/foreach` | 遍历结构<br>For Each Structure | `CONTROL` / `STATE` | 可见 | `flow:flow`<br>`values:structure_set` | `body?:flow`<br>`done?:flow`<br>`value?:structure_reference`<br>`index?:integer` | `{}` | — |
| `academy:program/core/collection/world_position/foreach` | 遍历世界坐标<br>For Each World Position | `CONTROL` / `STATE` | 可见 | `flow:flow`<br>`values:world_position_set` | `body?:flow`<br>`done?:flow`<br>`value?:world_position`<br>`index?:integer` | `{}` | — |
| `academy:program/core/flow/branch` | 条件分支<br>Branch | `CONTROL` / `PURE` | 可见 | `flow:flow`<br>`condition:boolean` | `true?:flow`<br>`false?:flow` | `{}` | — |
| `academy:program/core/flow/break_loop` | 结束循环<br>Break Loop | `CONTROL` / `STATE` | 可见 | `flow:flow` | — | `{}` | — |
| `academy:program/core/flow/continue_loop` | 继续下一轮<br>Continue Loop | `CONTROL` / `STATE` | 可见 | `flow:flow` | — | `{}` | — |
| `academy:program/core/flow/stop` | 停止<br>Stop | `CONTROL` / `PURE` | 可见 | `flow:flow` | — | `{}` | — |
| `academy:program/core/flow/trigger/chat` | 收到聊天消息<br>Chat Message Trigger | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{"mode":"any","keyword":"","ignore_case":false,"sender":"all"}` | — |
| `academy:program/core/flow/trigger/health_threshold` | 生命值阈值触发<br>Health Threshold Trigger | `ENTRY` / `STATE` | 可见 | — | `flow?:flow` | `{"mode":"below","threshold":10.0}` | — |
| `academy:program/core/flow/trigger/hurt` | 受击时触发<br>When Hurt | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{}` | — |
| `academy:program/core/flow/trigger/loop` | 循环触发<br>Periodic Trigger | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{"enabled":true,"interval":40}` | — |
| `academy:program/core/flow/trigger/melee` | 近战时触发<br>After Melee Attack | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{}` | — |
| `academy:program/core/flow/trigger/movement` | 运动时触发<br>Movement Trigger | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{"condition":"jump"}` | — |
| `academy:program/core/flow/wait` | 等待<br>Wait | `SUSPEND` / `SUSPEND` | 可见 | `flow:flow`<br>`ticks:integer` | `flow?:flow` | `{}` | — |
| `academy:program/core/state/shared_variable_clear` | 清除共享变量<br>Clear Shared Variable | `CONTROL` / `STATE` | 可见 | `flow:flow`<br>`name?:text` | `flow?:flow` | `{"name":"value"}` | — |
| `academy:program/core/state/shared_variable_clear_all` | 清除全部共享变量<br>Clear All Shared Variables | `CONTROL` / `STATE` | 可见 | `flow:flow` | `flow?:flow` | `{}` | — |
| `academy:program/core/state/shared_variable_set` | 写入共享变量<br>Write Shared Variable | `CONTROL` / `STATE` | 可见 | `flow:flow`<br>`value:text`<br>`name?:text` | `flow?:flow` | `{"name":"value","type":"academy:program_type/text"}` | — |
| `academy:program/core/state/variable_set` | 写入变量<br>Write Variable | `CONTROL` / `STATE` | 可见 | `flow:flow`<br>`value:boolean`<br>`name?:text` | `flow?:flow` | `{"name":"value","type":"academy:program_type/boolean"}` | — |

### 动作（1）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/core/action/debug_output` | 聊天输出<br>Chat Output | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`value:entity_reference`<br>`text?:text` | `flow?:flow` | `{"value_type":"entity","text":"{value}","audience":"self"}` | — |

### 常量与数值（8）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/core/value/big_integer` | 大整数<br>Big Integer | `VALUE` / `PURE` | 隐藏/兼容 | `value?:big_integer` | `value?:big_integer` | `{"value":"0"}` | — |
| `academy:program/core/value/boolean` | 布尔值<br>Boolean | `VALUE` / `PURE` | 隐藏/兼容 | — | `value?:boolean` | `{"value":false}` | — |
| `academy:program/core/value/float` | 浮点数<br>Float | `VALUE` / `PURE` | 隐藏/兼容 | `value?:float` | `value?:float` | `{"value":0}` | — |
| `academy:program/core/value/integer` | 整数<br>Integer | `VALUE` / `PURE` | 隐藏/兼容 | `value?:integer` | `value?:integer` | `{"value":0}` | — |
| `academy:program/core/value/random_number` | 随机数<br>Random Number | `VALUE` / `STATE` | 可见 | `lower?:integer`<br>`upper?:integer` | `value?:integer` | `{"type":"integer","lower":"0","upper":"100"}` | — |
| `academy:program/core/value/scalar` | 数值常量<br>Scalar Constant | `VALUE` / `PURE` | 可见 | `value?:integer` | `value?:integer` | `{"type":"integer","value":"0"}` | — |
| `academy:program/core/value/tag` | 标签<br>Tag | `VALUE` / `PURE` | 可见 | `value?:tag` | `value?:tag` | `{"value":"minecraft:mineable/pickaxe"}` | — |
| `academy:program/core/value/text` | 文本<br>Text | `VALUE` / `PURE` | 可见 | `value?:text` | `value?:text` | `{"value":""}` | — |

## 矢量操作专属节点（26）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/accelerator/action/apply_vector` | 施加矢量<br>Apply Vector | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`direction:direction`<br>`strength?:integer` | `flow?:flow` | `{"strength":1}` | `academy:vector_accel` |
| `academy:program/accelerator/action/displace_block` | 控制方块位移<br>Displace Block | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`block:block_position`<br>`destination:block_position`<br>`strength?:integer` | `flow?:flow` | `{"strength":1}` | `academy:kinetic_energy_applied` |
| `academy:program/accelerator/action/displace_entity` | 控制实体位移<br>Displace Entity | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`destination:world_position`<br>`strength?:integer` | `flow?:flow` | `{"strength":1}` | `academy:vector_accel` |
| `academy:program/accelerator/action/kinetic_impact` | 动量冲击<br>Kinetic Impact | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`direction:direction`<br>`strength?:integer` | `flow?:flow` | `{"strength":1}` | `academy:kinetic_energy_applied` |
| `academy:program/accelerator/action/kinetic_shockwave` | 生成动能附加冲击波<br>Kinetic Shockwave | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`position:world_position`<br>`direction:direction`<br>`power?:float`<br>`radius?:integer` | `flow?:flow` | `{"power":1.0,"destroy_blocks":false,"radius":8}` | `academy:kinetic_energy_applied` |
| `academy:program/accelerator/action/redirect_projectile` | 偏转投射物<br>Redirect Projectile | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`projectile:entity_reference`<br>`direction:direction` | `flow?:flow` | `{}` | `academy:vector_reflection` |
| `academy:program/accelerator/action/rewrite_motion` | 重写运动<br>Rewrite Motion | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`motion:vector`<br>`power?:float` | `flow?:flow` | `{"power":1.0}` | `academy:vector_accel` |
| `academy:program/accelerator/action/structure/control` | 取得结构控制<br>Acquire structure control | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structure:structure_reference` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/extract` | 提取结构<br>Extract Structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`host:block_position`<br>`tier?:integer`<br>`budget?:integer` | `structure?:structure_reference`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"tier":1,"budget":0}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/launch` | 发射结构<br>Launch Structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`direction:direction`<br>`strength?:integer` | `result?:structure_operation_result`<br>`flow?:flow` | `{"strength":1}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/move` | 移动受控结构<br>Move controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`target?:world_position`<br>`anchor?:structure_anchor`<br>`seconds?:float`<br>`duration_seconds?:float` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"duration_seconds":1.0}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/move_group` | 移动结构<br>Move structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`displacement?:vector`<br>`seconds?:float`<br>`duration_seconds?:float` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"duration_seconds":1.0}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/release` | 释放结构控制<br>Release structure control | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control` | `result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/reset_group` | 回正结构<br>Right structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`timeout_seconds?:float` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"reset_mode":"world_up_keep_yaw","missing_baseline":"reject","timeout_seconds":5.0}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/reset_orientation` | 回正受控结构<br>Right controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`timeout_seconds?:float` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"reset_mode":"world_up_keep_yaw","missing_baseline":"reject","timeout_seconds":5.0}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/rotate` | 转向受控结构<br>Turn controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`pose?:structure_pose` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/rotate_group` | 转向结构<br>Turn structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`pose?:structure_pose` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/spin` | 持续旋转受控结构<br>Spin controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`axis:direction`<br>`angular_speed?:float`<br>`duration_seconds?:float` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"frame":"world","angular_speed":1.0,"duration_seconds":1.0,"finish_mode":"brake"}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/spin_group` | 持续旋转结构<br>Spin structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`axis?:direction`<br>`angular_speed?:float`<br>`duration_seconds?:float` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"frame":"world","angular_speed":1.0,"duration_seconds":1.0,"finish_mode":"brake"}` | `academy:kinetic_throw` |
| `academy:program/accelerator/action/structure/staticize` | 还原为方块（未开放）<br>Restore world blocks (unavailable) | `ACTION` / `ACTION` | 可见/仅检查，未开放还原 | `flow:flow`<br>`structure:structure_reference`<br>`target?:block_position` | `result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:kinetic_throw` |
| `academy:program/accelerator/entry/on_cast` | 施放时<br>On Cast | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{}` | — |
| `academy:program/accelerator/logic/observation_inverse` | 观测逆算<br>Observation Inverse | `VALUE` / `PURE` | 可见 | `observed:vector`<br>`expected:vector` | `correction?:vector`<br>`magnitude?:float` | `{}` | — |
| `academy:program/accelerator/logic/vector_reflection` | 矢量反射<br>Vector Reflection | `VALUE` / `PURE` | 可见 | `incident:vector`<br>`normal:direction` | `reflected?:vector` | `{}` | — |
| `academy:program/accelerator/target/caster` | 能力者<br>Caster | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |
| `academy:program/accelerator/target/incoming_projectiles` | 来袭投射物<br>Incoming Projectiles | `QUERY` / `WORLD_QUERY` | 可见 | — | `entities?:entity_set` | `{}` | — |
| `academy:program/accelerator/target/look_target` | 视线目标<br>Look Target | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |

## 空力使专属节点（23）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/aeromanip/action/airflow_push` | 风压推动<br>Airflow Push | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`direction:direction`<br>`power?:float` | `flow?:flow` | `{"power":1.0}` | `academy:pneumatic_grasp` |
| `academy:program/aeromanip/action/converging_airflow` | 汇聚气流<br>Converging Airflow | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entities:entity_set`<br>`center:world_position`<br>`power?:float`<br>`maximum_targets?:integer` | `flow?:flow` | `{"power":1.0,"maximum_targets":6}` | `academy:pneumatic_grasp` |
| `academy:program/aeromanip/action/fire_jets` | 喷射射流<br>Fire Jets | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`duration?:integer` | `flow?:flow` | `{"duration":8}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/laminar_cut` | 层流切割<br>Laminar Cut | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`origin?:world_position`<br>`direction:direction`<br>`power?:float` | `flow?:flow` | `{"power":1.0,"charge_tier":"instant","charge_acceleration":"standard","plane_mode":"disabled"}` | `academy:laminar_cutter` |
| `academy:program/aeromanip/action/launch_block_structure` | 喷流发射结构<br>Launch Block Structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`block:block_position`<br>`direction:direction`<br>`power?:float`<br>`structure_radius?:integer`<br>`duration?:integer` | `flow?:flow` | `{"power":1.0,"radius":2,"duration":8,"restore_when_settled":true}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/place_temporary_jet_nozzle` | 放置临时射流喷口<br>Place Temporary Jet Nozzle | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`direction:direction` | `flow?:flow` | `{"target_type":"entity"}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/control` | 取得结构控制<br>Acquire structure control | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structure:structure_reference` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/extract` | 提取喷口结构<br>Extract Nozzle Structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`host:block_position`<br>`radius?:integer`<br>`budget?:integer` | `structure?:structure_reference`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"radius":2,"budget":0}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/launch` | 发射喷口结构<br>Launch Nozzle Structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`direction:direction`<br>`power?:float`<br>`duration_seconds?:float` | `result?:structure_operation_result`<br>`flow?:flow` | `{"power":1.0,"duration_seconds":8.0,"restore_when_settled":true}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/move` | 移动受控结构<br>Move controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`target?:world_position`<br>`anchor?:structure_anchor`<br>`seconds?:float`<br>`duration_seconds?:float` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"duration_seconds":1.0}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/move_group` | 移动结构<br>Move structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`displacement?:vector`<br>`seconds?:float`<br>`duration_seconds?:float` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"duration_seconds":1.0}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/release` | 释放结构控制<br>Release structure control | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control` | `result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/reset_group` | 回正结构<br>Right structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`timeout_seconds?:float` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"reset_mode":"world_up_keep_yaw","missing_baseline":"reject","timeout_seconds":5.0}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/reset_orientation` | 回正受控结构<br>Right controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`timeout_seconds?:float` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"reset_mode":"world_up_keep_yaw","missing_baseline":"reject","timeout_seconds":5.0}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/rotate` | 转向受控结构<br>Turn controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`pose?:structure_pose` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/rotate_group` | 转向结构<br>Turn structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`pose?:structure_pose` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/spin` | 持续旋转受控结构<br>Spin controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`axis:direction`<br>`angular_speed?:float`<br>`duration_seconds?:float` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"frame":"world","angular_speed":1.0,"duration_seconds":1.0,"finish_mode":"brake"}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/spin_group` | 持续旋转结构<br>Spin structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`axis?:direction`<br>`angular_speed?:float`<br>`duration_seconds?:float` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"frame":"world","angular_speed":1.0,"duration_seconds":1.0,"finish_mode":"brake"}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/action/structure/staticize` | 还原为方块（未开放）<br>Restore world blocks (unavailable) | `ACTION` / `ACTION` | 可见/仅检查，未开放还原 | `flow:flow`<br>`structure:structure_reference`<br>`target?:block_position` | `result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/entry/on_cast` | 施放时<br>On Cast | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{}` | — |
| `academy:program/aeromanip/query/owned_jet_nozzle_count` | 有效喷口计数<br>Valid Nozzle Count | `QUERY` / `WORLD_QUERY` | 可见 | `center:world_position`<br>`radius:float`<br>`direction?:direction` | `count?:integer` | `{}` | `academy:high_speed_jet` |
| `academy:program/aeromanip/target/caster` | 能力者<br>Caster | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |
| `academy:program/aeromanip/target/look_target` | 视线目标<br>Look Target | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |

## 未元物质专属节点（9）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/darkmatter/action/create_beetle` | 塑造未元物质独角仙<br>Create Beetle | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`position:world_position`<br>`power?:float` | `flow?:flow` | `{"power":1.0}` | `academy:darkmatter_creation` |
| `academy:program/darkmatter/action/darkmatter_cut` | 未元物质切割<br>Darkmatter Cut | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`direction:direction`<br>`power?:float` | `flow?:flow` | `{"power":1.0}` | `academy:darkmatter_cut` |
| `academy:program/darkmatter/action/disassemble_block` | 分解方块<br>Disassemble Block | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`block:block_position`<br>`power?:float` | `flow?:flow` | `{"power":1.0}` | `academy:darkmatter_disassemble` |
| `academy:program/darkmatter/action/disassemble_entity` | 分解实体<br>Disassemble Entity | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`power?:float` | `flow?:flow` | `{"power":1.0}` | `academy:darkmatter_disassemble` |
| `academy:program/darkmatter/action/disassembly_field` | 未元解构场<br>Darkmatter Disassembly Field | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entities:entity_set`<br>`power?:float`<br>`maximum_targets?:integer` | `flow?:flow` | `{"power":1.0,"maximum_targets":4}` | `academy:darkmatter_disassemble` |
| `academy:program/darkmatter/entry/on_cast` | 施放时<br>On Cast | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{}` | — |
| `academy:program/darkmatter/query/phase_state` | 未元相位状态<br>Darkmatter Phase State | `QUERY` / `WORLD_QUERY` | 可见 | — | `alpha?:float`<br>`beta?:float`<br>`gamma?:float`<br>`matter?:float`<br>`capacity?:float` | `{}` | — |
| `academy:program/darkmatter/target/caster` | 能力者<br>Caster | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |
| `academy:program/darkmatter/target/look_target` | 视线目标<br>Look Target | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |

## 电击使专属节点（25）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/electromaster/action/arc_discharge` | 电弧放电<br>Arc Discharge | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`power?:float` | `flow?:flow` | `{"power":1.0}` | `academy:arc_generate` |
| `academy:program/electromaster/action/chain_discharge` | 链式放电<br>Chain Discharge | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entities:entity_set`<br>`power?:float`<br>`maximum_jumps?:integer` | `flow?:flow` | `{"power":1.0,"maximum_jumps":4}` | `academy:arc_generate` |
| `academy:program/electromaster/action/current_recharge` | 电流充能<br>Current Recharge | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference` | `flow?:flow` | `{"target_type":"entity"}` | `academy:current_recharge` |
| `academy:program/electromaster/action/magnetic_move` | 磁力操控<br>Magnetic Control | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`destination:world_position`<br>`power?:float` | `flow?:flow` | `{"power":1.0,"target_type":"entity","mode":"pull","force_magnetize":false}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/control` | 取得结构控制<br>Acquire structure control | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structure:structure_reference` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/extract` | 提取磁性结构<br>Extract Magnetic Structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`host:block_position`<br>`budget?:integer` | `structure?:structure_reference`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"budget":0,"force_magnetize":false}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/launch` | 发射结构<br>Launch Structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`direction:direction`<br>`power?:float` | `result?:structure_operation_result`<br>`flow?:flow` | `{"power":1.0}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/move` | 移动受控结构<br>Move controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`target?:world_position`<br>`anchor?:structure_anchor`<br>`seconds?:float`<br>`duration_seconds?:float` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"duration_seconds":1.0}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/move_group` | 移动结构<br>Move structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`displacement?:vector`<br>`seconds?:float`<br>`duration_seconds?:float` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"duration_seconds":1.0}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/release` | 释放结构控制<br>Release structure control | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control` | `result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/reset_group` | 回正结构<br>Right structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`timeout_seconds?:float` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"reset_mode":"world_up_keep_yaw","missing_baseline":"reject","timeout_seconds":5.0}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/reset_orientation` | 回正受控结构<br>Right controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`timeout_seconds?:float` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"reset_mode":"world_up_keep_yaw","missing_baseline":"reject","timeout_seconds":5.0}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/rotate` | 转向受控结构<br>Turn controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`pose?:structure_pose` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/rotate_group` | 转向结构<br>Turn structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`pose?:structure_pose` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/spin` | 持续旋转受控结构<br>Spin controlled structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`control:structure_control`<br>`axis:direction`<br>`angular_speed?:float`<br>`duration_seconds?:float` | `control?:structure_control`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"frame":"world","angular_speed":1.0,"duration_seconds":1.0,"finish_mode":"brake"}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/spin_group` | 持续旋转结构<br>Spin structure | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`structures?:structure_set`<br>`structure?:structure_reference`<br>`axis?:direction`<br>`angular_speed?:float`<br>`duration_seconds?:float` | `structures?:structure_set`<br>`result?:structure_operation_result`<br>`flow?:flow` | `{"frame":"world","angular_speed":1.0,"duration_seconds":1.0,"finish_mode":"brake"}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/action/structure/staticize` | 还原为方块（未开放）<br>Restore world blocks (unavailable) | `ACTION` / `ACTION` | 可见/仅检查，未开放还原 | `flow:flow`<br>`structure:structure_reference`<br>`target?:block_position` | `result?:structure_operation_result`<br>`flow?:flow` | `{}` | `academy:magnet_manipulation` |
| `academy:program/electromaster/entry/on_cast` | 施放时<br>On Cast | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{}` | — |
| `academy:program/electromaster/logic/energy_detection` | 能量检测<br>Energy Detection | `QUERY` / `WORLD_QUERY` | 可见 | `entity:entity_reference`<br>`percent?:float` | `result?:boolean` | `{"target_type":"entity","mode":"below","percent":50.0}` | — |
| `academy:program/electromaster/logic/redstone_detection` | 红石充能检测<br>Redstone Charge Detection | `QUERY` / `WORLD_QUERY` | 可见 | `block:block_position`<br>`level?:integer` | `result?:boolean` | `{"mode":"below","level":8}` | — |
| `academy:program/electromaster/query/energy_level` | 能量读数<br>Energy Reading | `QUERY` / `WORLD_QUERY` | 可见 | `entity:entity_reference` | `percent?:float`<br>`available?:boolean` | `{"target_type":"entity"}` | — |
| `academy:program/electromaster/target/caster` | 能力者<br>Caster | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |
| `academy:program/electromaster/target/chargeable_blocks` | 坐标附近可充能方块<br>Chargeable Blocks Near Position | `QUERY` / `WORLD_QUERY` | 可见 | `center:world_position`<br>`radius:float` | `blocks?:block_position_set` | `{}` | — |
| `academy:program/electromaster/target/look_target` | 视线目标<br>Look Target | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |
| `academy:program/electromaster/target/magnetic_entities` | 磁性实体扫描<br>Magnetic Entity Scan | `QUERY` / `WORLD_QUERY` | 可见 | `center:world_position`<br>`radius:float` | `entities?:entity_set` | `{}` | — |

## 原子崩坏专属节点（7）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/meltdowner/action/atomic_jet` | 原子喷射<br>Atomic Jet | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`direction:direction`<br>`power?:float` | `flow?:flow` | `{"power":1.0,"destroy_blocks":true}` | `academy:jet_strike` |
| `academy:program/meltdowner/action/electron_beam` | 定向电子束<br>Directional Electron Beam | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`origin?:world_position`<br>`direction:direction`<br>`power?:float` | `flow?:flow` | `{"power":1.0,"aim_mode":"direction","destroy_blocks":true,"destroy_projectiles":false}` | `academy:single_high_speed_electron_beam` |
| `academy:program/meltdowner/action/electron_fan` | 电子扇面<br>Electron Fan | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`origin?:world_position`<br>`direction:direction`<br>`power?:float`<br>`beam_count?:integer`<br>`spread_degrees?:float` | `flow?:flow` | `{"power":1.0,"beam_count":5,"spread_degrees":12.0,"destroy_blocks":true}` | `academy:single_high_speed_electron_beam` |
| `academy:program/meltdowner/action/mining_beam` | 定点采掘束<br>Targeted Mining Beam | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`origin?:world_position`<br>`direction?:direction`<br>`block?:block_position`<br>`power?:float` | `flow?:flow` | `{"power":1.0,"aim_mode":"direction"}` | `academy:mining_beam` |
| `academy:program/meltdowner/entry/on_cast` | 施放时<br>On Cast | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{}` | — |
| `academy:program/meltdowner/target/caster` | 能力者<br>Caster | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |
| `academy:program/meltdowner/target/look_target` | 视线目标<br>Look Target | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |

## 心理掌握专属节点（56）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/collection/entity_to_set` | 实体转集合<br>Entity To Set | `VALUE` / `PURE` | 隐藏/兼容 | `entity:entity_reference`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/collection/exclude` | 排除实体<br>Exclude Entity | `VALUE` / `PURE` | 可见 | `entities:entity_set`<br>`excluded:entity_reference`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/collection/farthest` | 最远实体<br>Farthest Entity | `VALUE` / `PURE` | 可见 | `entities:entity_set`<br>`parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/collection/highest_health` | 最高生命实体<br>Highest Health | `VALUE` / `PURE` | 可见 | `entities:entity_set`<br>`parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/collection/intersection` | 集合交集<br>Intersect Sets | `VALUE` / `PURE` | 隐藏/兼容 | `left:entity_set`<br>`right:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/collection/limit` | 数量限制<br>Limit Count | `VALUE` / `PURE` | 可见 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":4.0}` | — |
| `academy:program/collection/lowest_health` | 最低生命实体<br>Lowest Health | `VALUE` / `PURE` | 可见 | `entities:entity_set`<br>`parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/collection/nearest` | 最近实体<br>Nearest Entity | `VALUE` / `PURE` | 可见 | `entities:entity_set`<br>`parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/collection/random` | 随机实体<br>Random Entity | `VALUE` / `PURE` | 可见 | `entities:entity_set`<br>`parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/collection/sort_by_distance` | 按距离排序<br>Sort By Distance | `VALUE` / `PURE` | 可见 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/collection/subtract` | 集合相减<br>Subtract Sets | `VALUE` / `PURE` | 隐藏/兼容 | `left:entity_set`<br>`right:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/collection/union` | 集合合并<br>Union Sets | `VALUE` / `PURE` | 隐藏/兼容 | `left:entity_set`<br>`right:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/entry/on_cast` | 施放时<br>On Cast | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{}` | — |
| `academy:program/filter/alive` | 存活筛选<br>Alive Filter | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/filter/allies` | 友方筛选<br>Allies | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/filter/distance` | 距离筛选<br>Distance Filter | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":64.0}` | — |
| `academy:program/filter/enemies` | 敌方筛选<br>Enemies | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/filter/entity_type` | 实体类型<br>Entity Type | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/filter/has_target` | 拥有目标<br>Has Target | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/filter/health` | 生命不低于<br>Health At Least | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":50.0}` | — |
| `academy:program/filter/health_below` | 生命不高于<br>Health At Most | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":50.0}` | — |
| `academy:program/filter/hostile_to` | 敌对指定实体<br>Hostile To Entity | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`target:entity_reference`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/filter/last_damaged_by` | 被指定实体伤害<br>Damaged By Entity | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`attacker:entity_reference`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/filter/targeted_by` | 正在攻击指定实体<br>Targeting Entity | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`target:entity_reference`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/filter/visible_from` | 观察者可见<br>Visible To Observer | `VALUE` / `WORLD_QUERY` | 隐藏/兼容 | `entities:entity_set`<br>`observer:entity_reference`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/flow/distance_branch` | 条件分支：距离<br>Branch: Distance | `CONTROL` / `ACTION` | 可见 | `subject:entity_reference`<br>`flow?:flow`<br>`parameter?:float` | `true?:flow`<br>`false?:flow` | `{"parameter":64.0}` | — |
| `academy:program/flow/entity_type_branch` | 条件分支：实体类型<br>Branch: Entity Type | `CONTROL` / `ACTION` | 可见 | `subject:entity_reference`<br>`flow?:flow`<br>`parameter?:float` | `true?:flow`<br>`false?:flow` | `{"parameter":0.0}` | — |
| `academy:program/flow/health_ratio_branch` | 条件分支：生命比例<br>Branch: Health Ratio | `CONTROL` / `ACTION` | 可见 | `subject:entity_reference`<br>`flow?:flow`<br>`parameter?:float` | `true?:flow`<br>`false?:flow` | `{"parameter":50.0}` | — |
| `academy:program/flow/status_effect_branch` | 条件分支：状态效果<br>Branch: Has Status Effect | `CONTROL` / `ACTION` | 可见 | `subject:entity_reference`<br>`flow?:flow`<br>`parameter?:float` | `true?:flow`<br>`false?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/action/end_intrusion` | 结束心灵潜入<br>End Mental Intrusion | `ACTION` / `ACTION` | 可见 | `flow?:flow`<br>`parameter?:float` | `flow?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/action/guard_mode` | 守卫模式<br>Guard Mode | `ACTION` / `ACTION` | 可见 | `subjects:entity_set`<br>`destination:control_destination`<br>`flow?:flow`<br>`parameter?:float` | `flow?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/action/impression_manipulation` | 印象操作<br>Impression Manipulation | `ACTION` / `ACTION` | 可见 | `subjects:entity_set`<br>`flow?:flow`<br>`parameter?:float` | `flow?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/action/mental_stupor` | 呆然自失<br>Mental Stupor | `ACTION` / `ACTION` | 可见 | `subjects:entity_set`<br>`flow?:flow`<br>`parameter?:float` | `flow?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/action/path_to` | 寻路至目标<br>Path To Target | `ACTION` / `ACTION` | 可见 | `subjects:entity_set`<br>`destination:control_destination`<br>`flow?:flow`<br>`parameter?:float` | `flow?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/action/perception_mask` | 感官过滤<br>Perception Mask | `ACTION` / `ACTION` | 可见 | `observers:entity_set`<br>`hidden:entity_reference`<br>`flow?:flow`<br>`parameter?:float` | `flow?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/action/remove_control` | 解除控制<br>Release Control | `ACTION` / `ACTION` | 可见 | `subjects:entity_set`<br>`flow?:flow`<br>`parameter?:float` | `flow?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/action/start_intrusion` | 开始心灵潜入<br>Start Mental Intrusion | `ACTION` / `ACTION` | 可见 | `target:entity_reference`<br>`flow?:flow`<br>`parameter?:float` | `flow?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/action/target_misidentification` | 目标误认<br>Target Misidentification | `ACTION` / `ACTION` | 可见 | `subjects:entity_set`<br>`target:entity_reference`<br>`flow?:flow`<br>`parameter?:float` | `flow?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/action/view_control` | 视线朝向目标<br>Look At Target | `ACTION` / `ACTION` | 可见 | `subjects:entity_set`<br>`target:entity_reference`<br>`flow?:flow`<br>`parameter?:float` | `flow?:flow` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/filter/affected` | 正受心灵控制<br>Under Mental Control | `VALUE` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/filter/control_supported` | 能力支持筛选<br>Capability Support | `VALUE` / `WORLD_QUERY` | 可见 | `entities:entity_set`<br>`parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/intrusion_target` | 当前观察目标<br>Observation Target | `QUERY` / `WORLD_QUERY` | 可见 | `parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/mentalout/roster` | 受控清单<br>Controlled Roster | `QUERY` / `WORLD_QUERY` | 可见 | `parameter?:float` | `entities?:entity_set` | `{"parameter":0.0}` | — |
| `academy:program/target/caster` | 能力者<br>Caster | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | `parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/target/current_target` | 实体当前目标<br>Entity Current Target | `QUERY` / `WORLD_QUERY` | 可见 | `subject:entity_reference`<br>`parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/target/direction_between` | 获取方向<br>Direction Between | `QUERY` / `WORLD_QUERY` | 可见 | `origin:control_destination`<br>`target:control_destination`<br>`parameter?:float` | `direction?:direction` | `{"parameter":0.0}` | — |
| `academy:program/target/entity_position` | 实体位置<br>Entity Position | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | `entity:entity_reference`<br>`parameter?:float` | `destination?:control_destination` | `{"parameter":0.0}` | — |
| `academy:program/target/last_attacker` | 实体最近攻击者<br>Entity Last Attacker | `QUERY` / `WORLD_QUERY` | 可见 | `subject:entity_reference`<br>`parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/target/look_living` | 服务端视线目标<br>Server Look Target | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | `parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/target/nearby_all_entities` | 附近实体<br>Nearby Entities | `QUERY` / `WORLD_QUERY` | 可见 | `parameter?:float` | `entities?:entity_set` | `{"parameter":64.0}` | — |
| `academy:program/target/nearby_items` | 附近掉落物<br>Nearby Dropped Items | `QUERY` / `WORLD_QUERY` | 可见 | `parameter?:float` | `entities?:entity_set` | `{"parameter":64.0}` | — |
| `academy:program/target/nearby_living` | 附近生物<br>Nearby Living Entities | `QUERY` / `WORLD_QUERY` | 可见 | `parameter?:float` | `entities?:entity_set` | `{"parameter":64.0}` | — |
| `academy:program/target/nearby_projectiles` | 附近弹射物<br>Nearby Projectiles | `QUERY` / `WORLD_QUERY` | 可见 | `parameter?:float` | `entities?:entity_set` | `{"parameter":64.0}` | — |
| `academy:program/target/player_target` | 最近攻击目标<br>Last Attacked Target | `QUERY` / `WORLD_QUERY` | 可见 | `parameter?:float` | `entity?:entity_reference` | `{"parameter":0.0}` | — |
| `academy:program/target/position_offset` | 位置偏移<br>Position Offset | `QUERY` / `WORLD_QUERY` | 可见 | `origin:control_destination`<br>`direction:direction`<br>`parameter?:float` | `destination?:control_destination` | `{"parameter":1.0}` | — |
| `academy:program/target/sight_position` | 视线位置<br>Sight Position | `QUERY` / `WORLD_QUERY` | 可见 | `observer:entity_reference`<br>`parameter?:float` | `destination?:control_destination` | `{"parameter":0.0}` | — |

## 传送使专属节点（7）

| 节点 ID | 中文 / English | 角色 / 纯度 | 状态 | 默认输入 | 默认输出 | 默认配置 | capability 门槛 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `academy:program/teleport/action/block_item_teleport` | 方块与物品传送<br>Block & Item Teleport | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`position:block_position`<br>`slot?:integer` | `flow?:flow` | `{"mode":"place"}` | `academy:self_teleport` |
| `academy:program/teleport/action/entity_teleport` | 目标传送<br>Target Teleport | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`entity:entity_reference`<br>`destination:control_destination`<br>`direction?:direction`<br>`power?:float` | `flow?:flow` | `{"power":1.0,"target_type":"entity"}` | `academy:quick_location_teleport` |
| `academy:program/teleport/action/self_teleport` | 安全传送<br>Safe Teleport | `ACTION` / `ACTION` | 可见 | `flow:flow`<br>`destination:world_position`<br>`power?:float` | `flow?:flow` | `{"power":1.0}` | `academy:self_teleport` |
| `academy:program/teleport/entry/on_cast` | 施放时<br>On Cast | `ENTRY` / `PURE` | 可见 | — | `flow?:flow` | `{}` | — |
| `academy:program/teleport/logic/space_safety` | 空间安全检测<br>Space Safety Check | `QUERY` / `WORLD_QUERY` | 可见 | `entity:entity_reference`<br>`position:world_position` | `result?:boolean` | `{}` | — |
| `academy:program/teleport/target/caster` | 能力者<br>Caster | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |
| `academy:program/teleport/target/look_target` | 视线目标<br>Look Target | `QUERY` / `WORLD_QUERY` | 隐藏/兼容 | — | `entity?:entity_reference` | `{}` | — |

## 有限配置模式索引（66 个具有有限选项的节点）

本索引列出 UI 实际提供的有限选项；同一字段值是否能与其他字段的默认值组合，以原始 JSON 的 `valid_with_other_defaults` 为准。例如把数值常量类型切换为布尔时，还须同步把值文本改为合法布尔文本，不能把单字段无效组合误判为节点不支持该类型。

| 节点 ID | 字段 | 有限值 | 默认组合有效数 / 选项数 |
| --- | --- | --- | ---: |
| `academy:program/accelerator/action/apply_vector` | `strength` | `0, 1, 2` | 3 / 3 |
| `academy:program/accelerator/action/displace_block` | `strength` | `0, 1, 2` | 3 / 3 |
| `academy:program/accelerator/action/displace_entity` | `strength` | `0, 1, 2` | 3 / 3 |
| `academy:program/accelerator/action/kinetic_impact` | `strength` | `0, 1, 2` | 3 / 3 |
| `academy:program/accelerator/action/kinetic_shockwave` | `destroy_blocks` | `false, true` | 2 / 2 |
| `academy:program/accelerator/action/structure/launch` | `strength` | `0, 1, 2` | 3 / 3 |
| `academy:program/accelerator/action/structure/reset_group` | `reset_mode` | `"capture_baseline", "world_up_keep_yaw", "nearest_grid_yaw"` | 3 / 3 |
| `academy:program/accelerator/action/structure/reset_group` | `missing_baseline` | `"reject", "use_world_grid"` | 2 / 2 |
| `academy:program/accelerator/action/structure/reset_orientation` | `reset_mode` | `"capture_baseline", "world_up_keep_yaw", "nearest_grid_yaw"` | 3 / 3 |
| `academy:program/accelerator/action/structure/reset_orientation` | `missing_baseline` | `"reject", "use_world_grid"` | 2 / 2 |
| `academy:program/accelerator/action/structure/spin` | `frame` | `"world", "local"` | 2 / 2 |
| `academy:program/accelerator/action/structure/spin` | `finish_mode` | `"brake", "release_inertia"` | 2 / 2 |
| `academy:program/accelerator/action/structure/spin_group` | `frame` | `"world", "local"` | 2 / 2 |
| `academy:program/accelerator/action/structure/spin_group` | `finish_mode` | `"brake", "release_inertia"` | 2 / 2 |
| `academy:program/aeromanip/action/laminar_cut` | `charge_tier` | `"instant", "half", "full"` | 3 / 3 |
| `academy:program/aeromanip/action/laminar_cut` | `charge_acceleration` | `"standard", "accelerated", "instant"` | 3 / 3 |
| `academy:program/aeromanip/action/laminar_cut` | `plane_mode` | `"disabled", "direction", "random"` | 3 / 3 |
| `academy:program/aeromanip/action/launch_block_structure` | `restore_when_settled` | `false, true` | 2 / 2 |
| `academy:program/aeromanip/action/place_temporary_jet_nozzle` | `target_type` | `"entity", "block"` | 2 / 2 |
| `academy:program/aeromanip/action/structure/launch` | `restore_when_settled` | `false, true` | 2 / 2 |
| `academy:program/aeromanip/action/structure/reset_group` | `reset_mode` | `"capture_baseline", "world_up_keep_yaw", "nearest_grid_yaw"` | 3 / 3 |
| `academy:program/aeromanip/action/structure/reset_group` | `missing_baseline` | `"reject", "use_world_grid"` | 2 / 2 |
| `academy:program/aeromanip/action/structure/reset_orientation` | `reset_mode` | `"capture_baseline", "world_up_keep_yaw", "nearest_grid_yaw"` | 3 / 3 |
| `academy:program/aeromanip/action/structure/reset_orientation` | `missing_baseline` | `"reject", "use_world_grid"` | 2 / 2 |
| `academy:program/aeromanip/action/structure/spin` | `frame` | `"world", "local"` | 2 / 2 |
| `academy:program/aeromanip/action/structure/spin` | `finish_mode` | `"brake", "release_inertia"` | 2 / 2 |
| `academy:program/aeromanip/action/structure/spin_group` | `frame` | `"world", "local"` | 2 / 2 |
| `academy:program/aeromanip/action/structure/spin_group` | `finish_mode` | `"brake", "release_inertia"` | 2 / 2 |
| `academy:program/collection/sort_by_distance` | `parameter` | `0.0, 1.0` | 2 / 2 |
| `academy:program/core/action/debug_output` | `value_type` | `"boolean", "integer", "big_integer", "float", "text", "tag", "identifier", "duration", "vector", "direction", "world_position", "block_position", "entity", "direction_list", "world_position_list", "block_position_list", "entity_list", "structure", "structure_list"` | 19 / 19 |
| `academy:program/core/action/debug_output` | `audience` | `"self", "all"` | 2 / 2 |
| `academy:program/core/collection/point/distance_sort` | `type` | `"entity", "world_position", "block_position"` | 3 / 3 |
| `academy:program/core/collection/point/distance_sort` | `order` | `"ascending", "descending"` | 2 / 2 |
| `academy:program/core/filter/entity/type` | `type` | `"any", "living", "player", "mob", "hostile", "animal", "friendly", "projectile", "item"` | 9 / 9 |
| `academy:program/core/filter/structure/blocks` | `match` | `"any", "all"` | 2 / 2 |
| `academy:program/core/filter/structure/source` | `source_mode` | `"marked", "unmarked", "owned_by_caster", "skill", "fragment"` | 5 / 5 |
| `academy:program/core/flow/trigger/chat` | `mode` | `"any", "equals", "contains", "starts_with", "ends_with"` | 5 / 5 |
| `academy:program/core/flow/trigger/chat` | `ignore_case` | `false, true` | 2 / 2 |
| `academy:program/core/flow/trigger/chat` | `sender` | `"all", "self", "others"` | 3 / 3 |
| `academy:program/core/flow/trigger/health_threshold` | `mode` | `"below", "above"` | 2 / 2 |
| `academy:program/core/flow/trigger/loop` | `enabled` | `false, true` | 2 / 2 |
| `academy:program/core/flow/trigger/movement` | `condition` | `"jump", "sneak", "sprint", "elytra", "swim"` | 5 / 5 |
| `academy:program/core/logic/numeric/arithmetic` | `type` | `"integer", "big_integer", "float"` | 3 / 3 |
| `academy:program/core/logic/numeric/arithmetic` | `operator` | `"add", "subtract", "multiply", "divide", "integer_divide", "modulo", "power", "absolute"` | 8 / 8 |
| `academy:program/core/logic/numeric/compare` | `type` | `"integer", "big_integer", "float"` | 3 / 3 |
| `academy:program/core/logic/numeric/compare` | `operator` | `"equal", "less", "less_equal", "greater", "greater_equal"` | 5 / 5 |
| `academy:program/core/logic/numeric/convert` | `source_type` | `"integer", "big_integer", "float"` | 3 / 3 |
| `academy:program/core/logic/numeric/convert` | `target_type` | `"integer", "big_integer", "float"` | 3 / 3 |
| `academy:program/core/logic/select_value` | `type` | `"academy:program_type/boolean", "academy:program_type/integer", "academy:program_type/big_integer", "academy:program_type/float", "academy:program_type/text", "academy:program_type/tag", "academy:program_type/identifier", "academy:program_type/duration", "academy:program_type/vector", "academy:program_type/direction", "academy:program_type/world_position", "academy:program_type/block_position", "academy:program_type/entity_reference", "academy:program_type/structure_reference", "academy:program_type/structure_set", "academy:program_type/living_entity_reference", "academy:program_type/direction_set", "academy:program_type/world_position_set", "academy:program_type/block_position_set", "academy:program_type/entity_set", "academy:program_type/living_entity_set"` | 21 / 21 |
| `academy:program/core/logic/text/compare` | `mode` | `"equals", "contains", "starts_with", "ends_with"` | 4 / 4 |
| `academy:program/core/logic/text/compare` | `ignore_case` | `false, true` | 2 / 2 |
| `academy:program/core/logic/text/split` | `mode` | `"whitespace", "line", "sentence", "delimiter"` | 4 / 4 |
| `academy:program/core/logic/text/split` | `trim` | `false, true` | 2 / 2 |
| `academy:program/core/logic/text/split` | `skip_empty` | `false, true` | 2 / 2 |
| `academy:program/core/query/block_normal` | `mode` | `"view", "position_direction"` | 2 / 2 |
| `academy:program/core/query/entity_data` | `data` | `"health", "cp", "sp"` | 3 / 3 |
| `academy:program/core/query/entity_position` | `anchor` | `"feet", "center", "eyes"` | 3 / 3 |
| `academy:program/core/query/look_target` | `target_type` | `"entity", "block"` | 2 / 2 |
| `academy:program/core/spatial/text_to_vec3` | `type` | `"vector", "direction", "world_position"` | 3 / 3 |
| `academy:program/core/spatial/text_to_vec3` | `mode` | `"exact", "extract"` | 2 / 2 |
| `academy:program/core/spatial/vec3_operation` | `type` | `"vector", "direction", "world_position"` | 3 / 3 |
| `academy:program/core/spatial/vec3_operation` | `operator` | `"dot", "cross", "add", "subtract", "scale", "length", "normalize"` | 7 / 7 |
| `academy:program/core/state/shared_variable_get` | `type` | `"academy:program_type/boolean", "academy:program_type/integer", "academy:program_type/big_integer", "academy:program_type/float", "academy:program_type/text"` | 5 / 5 |
| `academy:program/core/state/shared_variable_set` | `type` | `"academy:program_type/boolean", "academy:program_type/integer", "academy:program_type/big_integer", "academy:program_type/float", "academy:program_type/text"` | 5 / 5 |
| `academy:program/core/state/variable_get` | `type` | `"academy:program_type/boolean", "academy:program_type/integer", "academy:program_type/big_integer", "academy:program_type/float", "academy:program_type/text", "academy:program_type/tag", "academy:program_type/identifier", "academy:program_type/duration", "academy:program_type/vector", "academy:program_type/direction", "academy:program_type/world_position", "academy:program_type/block_position", "academy:program_type/entity_reference", "academy:program_type/structure_reference", "academy:program_type/structure_set", "academy:program_type/living_entity_reference", "academy:program_type/direction_set", "academy:program_type/world_position_set", "academy:program_type/block_position_set", "academy:program_type/entity_set", "academy:program_type/living_entity_set"` | 21 / 21 |
| `academy:program/core/state/variable_set` | `type` | `"academy:program_type/boolean", "academy:program_type/integer", "academy:program_type/big_integer", "academy:program_type/float", "academy:program_type/text", "academy:program_type/tag", "academy:program_type/identifier", "academy:program_type/duration", "academy:program_type/vector", "academy:program_type/direction", "academy:program_type/world_position", "academy:program_type/block_position", "academy:program_type/entity_reference", "academy:program_type/structure_reference", "academy:program_type/structure_set", "academy:program_type/living_entity_reference", "academy:program_type/direction_set", "academy:program_type/world_position_set", "academy:program_type/block_position_set", "academy:program_type/entity_set", "academy:program_type/living_entity_set"` | 21 / 21 |
| `academy:program/core/value/boolean` | `value` | `false, true` | 2 / 2 |
| `academy:program/core/value/random_number` | `type` | `"integer", "big_integer", "float"` | 3 / 3 |
| `academy:program/core/value/scalar` | `type` | `"boolean", "integer", "big_integer", "float"` | 3 / 4 |
| `academy:program/electromaster/action/current_recharge` | `target_type` | `"entity", "block"` | 2 / 2 |
| `academy:program/electromaster/action/magnetic_move` | `target_type` | `"entity", "block"` | 2 / 2 |
| `academy:program/electromaster/action/magnetic_move` | `mode` | `"pull", "launch"` | 2 / 2 |
| `academy:program/electromaster/action/magnetic_move` | `force_magnetize` | `false, true` | 2 / 2 |
| `academy:program/electromaster/action/structure/extract` | `force_magnetize` | `false, true` | 2 / 2 |
| `academy:program/electromaster/action/structure/reset_group` | `reset_mode` | `"capture_baseline", "world_up_keep_yaw", "nearest_grid_yaw"` | 3 / 3 |
| `academy:program/electromaster/action/structure/reset_group` | `missing_baseline` | `"reject", "use_world_grid"` | 2 / 2 |
| `academy:program/electromaster/action/structure/reset_orientation` | `reset_mode` | `"capture_baseline", "world_up_keep_yaw", "nearest_grid_yaw"` | 3 / 3 |
| `academy:program/electromaster/action/structure/reset_orientation` | `missing_baseline` | `"reject", "use_world_grid"` | 2 / 2 |
| `academy:program/electromaster/action/structure/spin` | `frame` | `"world", "local"` | 2 / 2 |
| `academy:program/electromaster/action/structure/spin` | `finish_mode` | `"brake", "release_inertia"` | 2 / 2 |
| `academy:program/electromaster/action/structure/spin_group` | `frame` | `"world", "local"` | 2 / 2 |
| `academy:program/electromaster/action/structure/spin_group` | `finish_mode` | `"brake", "release_inertia"` | 2 / 2 |
| `academy:program/electromaster/logic/energy_detection` | `target_type` | `"entity", "block"` | 2 / 2 |
| `academy:program/electromaster/logic/energy_detection` | `mode` | `"below", "above"` | 2 / 2 |
| `academy:program/electromaster/logic/redstone_detection` | `mode` | `"below", "above"` | 2 / 2 |
| `academy:program/electromaster/query/energy_level` | `target_type` | `"entity", "block"` | 2 / 2 |
| `academy:program/filter/entity_type` | `parameter` | `0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0` | 8 / 8 |
| `academy:program/flow/entity_type_branch` | `parameter` | `0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0` | 8 / 8 |
| `academy:program/meltdowner/action/atomic_jet` | `destroy_blocks` | `false, true` | 2 / 2 |
| `academy:program/meltdowner/action/electron_beam` | `aim_mode` | `"direction", "target"` | 2 / 2 |
| `academy:program/meltdowner/action/electron_beam` | `destroy_blocks` | `false, true` | 2 / 2 |
| `academy:program/meltdowner/action/electron_beam` | `destroy_projectiles` | `false, true` | 2 / 2 |
| `academy:program/meltdowner/action/electron_fan` | `destroy_blocks` | `false, true` | 2 / 2 |
| `academy:program/meltdowner/action/mining_beam` | `aim_mode` | `"direction", "target"` | 2 / 2 |
| `academy:program/mentalout/filter/control_supported` | `parameter` | `0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0` | 7 / 7 |
| `academy:program/teleport/action/block_item_teleport` | `mode` | `"place", "collect"` | 2 / 2 |
| `academy:program/teleport/action/entity_teleport` | `target_type` | `"entity", "block", "structure"` | 3 / 3 |

## 可复核来源与验收边界

- 定义/目录：`AbilityProgramDefinitions`、`AbilityProgramDefinition`、`CommonProgramNodeCatalog`、七系 catalog/definition、`ProgramEditorNodeCatalog`。
- 实例端口/配置：`ProgramNodeType.resolvedSchema/configurationPorts`、`ProgramConfigurationOptions`；编译使用真实 codec，不用文案推测连接类型。
- 当前界面：`ModularProgramScreen.paletteEntries/visibleEntries`、`ProgramNodePalette`；本清单记录目录可见性，不自动选择或修改玩家已学能力。
- 双语文本来自同次客户端资源管理器加载的 `academy:lang/zh_cn.json` 与 `academy:lang/en_us.json`，各 3378 个资源键；不依赖当前客户端语言。
- 该审计只证明目录、默认 schema、选项和文字资源的实际状态，不证明所有配置组合、所有世界动作或所有旧图都已经游戏验收。尤其三系安全静态化仍是检查状态。
- 原始 JSON 大小 2,272,451 字节，SHA-256：`C78157DC66C0885CBFC8012EA1CCDC4D2230A730E9E7F9ADAE8FF92BCC3A1A24`。
