# 磁场操作与铁砂操作：实现与验收

## 已实现行为

保留存档 ID `academy:magnet_manipulation` 和 `academy:iron_sand_arsenal`。

| 操作 | 默认输入 | 行为 |
| --- | --- | --- |
| 磁场完全被动 | 学会后生效 | 实例级心灵免疫；提示“目标的电磁场干扰了你的能力”；真实抗性 +2 |
| 磁悬浮 | Shift+R 开关 | 任意有碰撞方块附近自由移动，跳跃／潜行升降；支撑距离 4／6／8／16 格 |
| 目标牵引 | 按住 R | 磁性目标范围 48 格，2000 后 60 格；最大速度 2.3，2000 后 2.645 格/tick |
| 铁砂防御 | Shift+G 开关 | 按实际生命损失消耗质量抵伤；2 格球形范围拦截敌对弹射物，含单 tick 扫掠路径 |
| 铁砂鞭击 | 点按 Alt+G | 消耗 5 MP，前方 120°、12 格，10AD，无视护甲，成功扣血附加 1 电荷 |
| 链锯云雾 | 长按 Alt+G | 6 tick 后进入云雾；每 tick 消耗 0.5 MP，每 10 次成功支付攻击半径 16 格内的敌人，16AD |

伤害保留用户确认的 `基础值 × A × D`。`D` 沿用项目玩家伤害倍率的原有定义，不改变其内部能力强度计算。

铁砂 MP 上限为 `100A`，每秒恢复 `10A`，富集群系再恢复 `10A`；恢复期间可以继续使用技能。数据包标签为 `academy:iron_sand_rich`，默认包含沙漠及三个恶地群系。

- 1000：维持／动态 CP ×0.9；四类 MP 消耗统一 ×0.9，质量折扣位于公开数值方法中。
- 2000：鞭击／云雾范围变为 15／20 格；MP 上限变为 `120A`。
- 3000：云雾追加通用装备磨损；鞭击读取命中前电荷，已带电时一次结算 `15AD`，不多加一次电荷。
- 防御维持占用 40 CP；磁悬浮和目标牵引各沿用 10 CP／20 tick 的计费节奏，可同时使用。
- 首次学会铁砂充满；重登、切换开关、容量提升不补满；死亡清空，无离线恢复。
- 释放／取消长按不补鞭击；GUI、失焦、死亡、失去技能、换维度、超时会清理主动会话。

## 公共接口

通用能力位于 `org.academy.api`，玩家事件、数据存储及网络适配仍位于 `org.academy.internal`。

| 接口 | 用途 |
| --- | --- |
| `MagneticTargets` | 方块、物品、实体的磁性判定；旧技能兼容方法和精密操作转调此处 |
| `MagneticMovement` | 牵引速度、方向和平滑加速计算 |
| `MagneticSupportQuery` / `MagneticLevitation` | 碰撞表面支撑查询与自由悬浮速度，支持玩家、AI 和程序提供输入 |
| `MagneticFieldTuning` / `IronSandTuning` | 熟练度、距离、MP、伤害等纯数值方法 |
| `GravityControl` | 按来源持有重力状态，释放时恢复原状态并保留其他来源 |
| `MentalImmunity` | 按实体实例及来源授予免疫、反馈和撤销；兼容现有实体类型 tag |
| `AbilityDefenseEffects` | 按来源添加真实抗性；所有已注册属性的生物类型具备默认值为 0 的属性 |
| `AbilityActorContext` / `AbilityResourceAccount` | 非玩家施术者、能力强度、伤害倍率和调用者自有资源账本 |
| `IronSandResourceService` | 玩家 MP 查询、恢复、消费、容量对账与防御模式控制 |
| `HealthLossGuards` | 只读预览、提交前抵消、嵌套交易去重、失败退款及内部维护写入 |
| `HostileTargets` / `HostileProjectiles` | 统一敌对准入、归属与队伍过滤、球形扫掠相交、全额付款后销毁 |
| `IronSandActions` | 已获准、已付款的一次鞭击／云雾脉冲；费用由调用方按施放结算，不按目标数结算 |
| `AbilityDamageService` / `AbilityHitEffects` | 带来源的伤害结果，以及共用电荷、护甲磨损 |

这些运行时写入接口供服务器线程调用。非玩家调用方提供 `AbilityResourceAccount`，使用 `AbilityActorContext` 调用攻击，并负责费用和生命周期。移除能力时撤销相同来源的免疫、抗性、重力与生命保护；NPC 不依赖玩家 UUID 存储或客户端输入包。

## 生命值结算

标准路径在生命 `SynchedEntityData.DataItem.value` 的写入处结算，因此同时覆盖 `setHealth`、同步数据写入和 `DataItem.setValue`。直接真实生命定位器及 CTA 结算使用同一事务。

一次提交按剩余 MP 抵消可支付的生命损失，写入未被接受时退款；嵌套调用不再付款。已有真实生命投影属于过去已提交的损失，不重复计费。投影对账、最大生命夹取、自定义伤害通知和死亡收尾使用维护写入。

矢量防御、幻想杀手、熔毁抗性规则和伤害归属保留原有入口。成功电击命中仍由分类伤害结算统一附加电荷。

兼容范围不包括任意外部 `Unsafe`／反射直接修改字段而绕过所有支持的写入点；此类实体可通过项目真实生命适配器和公开提交接口接入。

## 存档与输入迁移

- 旧电磁护盾保留为隐藏兼容注册，不再提供独立技能效果或默认键位。
- 登录时将旧护盾和铁砂熟练度取最大值合并，清理旧护盾学习项、占用与抗性来源；迁移标记防止重复处理。
- 铁砂前置改为磁场操作，并解除与磁悬武装的互斥。
- 默认按键更新时保留用户自定义键；旧按下／释放记录折叠，旧自我牵引自定义键转入磁悬浮。

## 验证

使用 JBR 25 和项目 Gradle wrapper。

- `test -DisDev=true`：2105 项单元测试通过。
- `build -DisDev=true` 与 `build -DisDev=false`：完整构建通过；同时包含 API 示例、103 项编辑器测试、Javadoc 和 JVM 类指针相关检查。
- 最终全量 GameTest：120/121 通过，本次新增 5 项全部通过。唯一失败为 `academy:time_player_tick_scaling`；单独运行该项通过。较早批次还出现一次心理掌握敌对链时序失败，最终批次已通过，未为此改动生产逻辑。
- 玩家时钟项单独命令：`runGameTestServer -DisDev=true -PacademyGameTests=academy:time_player_tick_scaling`。批量运行与隔离运行存在差异，尚未将整个 GameTest 集合宣称为稳定全绿。
- 新增服务器场景：普通／直接／CTA 生命写入、失败退款、已有投影、自定义回调、NPC 调用、伤害与电荷、装备磨损、MP 恢复、死亡、迁移、高速弹射物、墙壁和天花板支撑。
- 隔离客户端自动场景：真实网络点按／长按／取消、防御与悬浮共存、MP 和状态同步、第一／第三人称云雾、关闭后清理。入口为 `ElectromasterReworkClientSmoke`，不进入发布 JAR。
- 客户端截图位于 `build/rework-client-captures/`。第一次截图定位并修复了铁砂依赖角色模型渲染坐标的问题，现使用玩家世界坐标。

客户端启动日志出现 `Globals` 未初始化导致的一次资源重载警告，自动重载后进入游戏并通过测试；日志也包含现有字体和复制测试存档的缺失游戏规则警告。本次未调整通用纹理图集启动流程。

复现客户端回归：先在 `run/electromaster-rework/saves/railgun` 准备隔离测试存档，再运行：

```powershell
.\gradlew.bat runClientDev -DisDev=true -I tools/vfxgraph-editor/scripts/electromaster-rework-client.init.gradle
```

该场景会修改上述隔离存档中的场地、角色能力和测试实体，并在验收后退出。

未创建 Git 提交；保留了工作区已有修改。构建产物包含整个当前工作区，输出为 `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` 和 `academy-26.2.0-0.0.4-alpha-release.jar`。
