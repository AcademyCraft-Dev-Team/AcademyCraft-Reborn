# 附属 MOD API 开发指南

适用当前工作区：Minecraft 26.2 / NeoForge 26.2.0.70 / Academy 0.0.4-alpha，Java 25。本文描述已实现接口；后续规划见 [注册 API 设计方案](addon_registration_api_design.md)。测试版只尽可能保障文档化的 API 兼容。

## 接入规则

禁止破坏性注入，非必要禁止对 Academy 方法进行 Mixin。公开注册、事件、效果服务能完成的功能必须通过这些入口；不得用反射、覆写、字节码转换绕过冻结、授权、状态保护或费用校验。内部实现可随时重构，`public` 修饰符和公开方法签名稳定都不保证某个注入位置稳定。必要临时兼容的限制详见设计方案第 1.1 节。

## 注册与初始化

`AcademyRegistrations.create(modId)` 提供 `categories() / skills() / damageProfiles() / programNodes()` 四个原生 `DeferredRegister`；在附属构造器调用一次 `bind(IEventBus)`。也可直接使用 `Registries.Keys` 和原生注册事件，得到相同关联与校验结果。

条目以完整 `Identifier` 为身份。注册声明引用 `ResourceKey` 或 holder 的 `getKey()`，不要在声明时提前 `get()`、读取世界或修改其他条目。当前注册完成阶段按 ID 统一解析技能与依赖，校验后冻结分类集合，并在 NeoForge 游戏事件总线发布一次 `AcademyRegistrationsReadyEvent`；这是只读通知，世界的 DamageType 此时尚不可用。

`AbilitySystemFinalizedEvent` 保留兼容但已弃用；不要在该旧事件中添加新内容。静态注册仅执行一次，不在每次服务器启动时重复注册。

## 分类与技能

`AbilityCategory.builder()` 必填 `translationKey` 与 `icon`。`getDescriptionId()` 返回显示翻译键，当前分类展示与程序界面使用该键。默认不参加初始能力开发；显式调用 `development(weight, props)` 或 `developmentProfile(props)` 才参加，权重须为正有限值。

可选项：`commonSkills(boolean)`、`resource(AbilityResourceSpec)`、`defaultDamageType(key)` 或 `defaultDamageProfile(key)`、`program(ProgramProfile)`。两种默认伤害声明互斥。分类技能按完整技能 ID 索引，排序为 `displayOrder` 后再按 ID；相同 Java 技能类的不同实例可以共存。

`Skill.Builder.of(categoryKey)` 与 `of(holder)` 不提前解析分类。向内置分类增加技能可引用 `AcademyKeys.ELECTROMASTER` 等常量；`AcademyKeys.skill("path")` 只创建内置技能 key，不保证该版本存在此技能。通用技能使用 `Skill.common()`。

必需依赖：`dependsOn(ResourceKey<Skill>...)`；可选依赖：`optionallyDependsOn(ResourceKey<Skill>...)`，缺失可选项才会忽略。跨分类不可学习的依赖和环会阻止初始化。附属分类技能必须明确调用 `proficiencyProfile(...)`，允许 `SkillProficiencyProfile.NONE`。可继续设置等级、学习能量、CP、维护费用、开发条件和图标。

主动技能实现调用继承的 `executeActive` / `executeContinuous` 等方法，通过既有学习、启用、状态、CP 和事件流程，再调用公开效果服务。框架的分类、等级、启用和 CP 状态保护保持有效；不能把状态 setter 当作附属任意授权或补充资源的入口。示例 GameTest 用正式管理员命令准备玩家。

## 技能状态与存档

`SkillStateType<T>` 描述附属自己的不可变状态：ID、Codec、默认值工厂、当前版本、迁移函数。通过 builder 的 `stateType(type)` 关联；多个技能可以共享同一个描述对象，每个玩家每个技能独立保存实际值。

```java
public static final SkillStateType<Integer> COUNT = SkillStateType.of(
        Identifier.fromNamespaceAndPath("example", "cast_count"), Codec.INT, () -> 0);

int count = skill.state(player, COUNT).orElseThrow();
skill.updateState(player, COUNT, count + 1);
```

更新只能发生在服务端线程，并自动验证 Codec 与标记存档脏状态；未学会或未知状态返回空/false。启用和熟练度是框架字段，不在附属状态内。不要就地修改从 `state` 返回的可变对象。

版本升级使用 `new SkillStateType<>(id, codec, defaults, version, migration)`，迁移收到旧版本号与旧 `state` 的副本，返回当前格式。未知未来版本、无法解码、缺失类型保留整个原始 JSON 且禁用执行；重新安装附属并重新加载存档时可恢复。不同描述对象重复占用一个类型 ID 会报错。历史 `withCustomData` 路径继续兼容，新的附属不应依赖其内部数据类型。

持久化引用使用 `AbilityCategory.ID_CODEC`、`Skill.ID_CODEC` 或完整 key；原有 `CODEC` 为数字 ID，未改变格式，不适合新的长期存档。

## 伤害

先提供 `data/<modid>/damage_type/<path>.json`，然后按需注册 `AbilityDamageProfile.builder(damageTypeKey)`。创建 key 不会自动生成数据包条目；缺失引用在服务器启动时明确报错，holder 始终从当前世界解析。

| 结算 | 当前行为 |
| --- | --- |
| `STANDARD` | 使用原版 hurtServer 路径，保留既有事件/标签和 Academy 规则 |
| `DIRECT` | 接入已有直接结算与完成流程；不按普通玩家攻击重试 |
| `TRUE_HEALTH` | 接入已有权威生命结算与死亡归属流程 |

另有 `bypassAbsorption(boolean)` 和 `compatibilityVersion(int)`。同一个 DamageType 只能有一个 profile，禁止覆盖 Academy 内置特殊伤害规则。原版标签仍定义护甲/火焰等归类；profile 暂未提供任意附效策略回调。

`AbilityDamageService.apply(player, target, skill, request)` 在服务端线程处理一次已授权施术的伤害。优先级为请求显式类型/profile > 技能声明 > 分类声明 > 旧内置规则/普通玩家攻击回退。显式引用错误不会触发回退。

`Request.of(amount)` 可接 `withType(key)` 或 `withProfile(key)`。完整构造器的 `maximumHealthPart` 是总伤害中已计算好的最大生命值来源部分，须在 `[0, amount]`，不是生命值比例。`Result` 返回 `applied`、观测到的生命与吸收减少量。该服务不额外扣 CP，避免多目标或持续伤害重复收取施术费用；不要在动作 apply 中再次调用会扣费的主动技能包装器。

## 精密操作

分类声明 `ProgramProfile.standard(entryKey)`，再注册 `ProgramNodes.manualEntry(categoryKey)` 对应的入口。默认 Level 5、128 节点、256 边、32 格查询/动作范围。可使用 `new ProgramProfile(entryKey, limits, spatialLimits)` 调整限制；当前不开放自定义解锁规则或内部 VM 工厂。

附属节点实现 `ProgramNodeExtension<C>`，声明 Codec、schema/version、role/purity、scope、required capabilities、执行器和编辑元数据。新增分类自动接入现有程序目录、保存/同步、编译和通用执行路由；扩展内置分类只需注册作用于该分类的节点。配置错误或缺失必需能力不会静默跳过。

当前执行器收到独立 `ProgramExecutionContext`，不是内部 VM。仅正在执行的 ACTION 节点可以 `submit(ProgramAction)`；回调返回后不能保留上下文继续提交。查询与实体引用是调用约定，不是 Java 安全沙箱。

`ProgramAction` 声明必需技能、未调整的 CP 费用、目标列表；必需技能必须出现在节点 scope 中。`validate` 无副作用，可多次调用；`apply` 在通过验证并支付 CP 后运行，返回 `ProgramEffect`。框架重新核验技能、当前分类、连接、目标存活/世界/范围、PvP 与友伤规则，并先检查同帧附属动作的合计 CP。目标最多 256 个。

`ProgramEffect.completed()` 表示即时效果；`lasting(ticks, cleanup)` 表示 1–72000 tick 的可清理效果。清理在到期、主动取消、施术者/目标失效、失去技能/分类/等级资格、换维度或服务器停止时执行一次；请使用来源隔离的效果句柄，不要无条件清除其他来源的状态。`cleanup` 要能安全释放自己创建的资源。

先验证再提交不等于整图原子回滚：框架可逆序调用已应用效果的补偿，但不会退还已扣 CP、恢复死亡/掉落或自动回滚附属状态。`apply` 抛出异常前造成的副作用须由附属自行清理。内置动作/MP 账户尚未与附属 CP 合并成一个事务。

服务端入口 `AbilityProgramService`：

| 方法 | 用途 |
| --- | --- |
| `save(player, slot, program)` | 校验归属、Level 5、已学能力和图后保存/同步，替换前取消旧程序效果 |
| `book(player)` | 读取当前分类的不可变程序槽位快照 |
| `execute(player, slot, program)` | 重新编译校验并执行手动程序；不要求先保存，不开启新的全局触发事件 |
| `cancel(player, slot, programId)` | 取消当前分类的指定程序，包含已经执行完成但仍持续的附属效果 |

槽位为 0–9，所有调用在服务端线程。结果状态为 `COMPLETED/DEFERRED/REJECTED/FAILED`，带编译诊断或执行节点；失败后不能推断未发生部分提交。原有自动触发类型继续可用，自定义触发源通过服务端 execute 接入手动程序。

节点兼容摘要按 ID 排序，包含语义修订、scope、默认配置与端口结构；翻译键不参与。执行语义或其他配置下的 schema 变更仍需提升 compatibilityVersion。全内容登录清单尚未实现，客户端和服务端必须安装同版本附属。

## 心理掌握

`MentalControlTags.IMMUNE`（`academy:mental_control_immune`）和 `MentalControlFeedbackApi.registerImmuneFeedback(entityTypeId, provider)` 允许为免疫实体类型设置基于实际目标的翻译文本。注册反馈本身不授予免疫。

`MentalControlTags.RESISTANCE`（`academy:mental_control_resistance`）使带标签的实体在连续心灵影响超过 400 tick 后自动挣脱并阻断 100 tick；心灵介入清单关系除外。`MentalControlApi.resistanceRemainingTicks(subject)` 查询剩余阻断。三种心理标签都是实体类型标签，默认抗性列表为空。叠加、重施、玩家按键机制及清理边界以 [设计方案第 8.1 节](addon_registration_api_design.md#81-心理掌握精神保护目标反馈与精神抗性) 为准。

通用非玩家施术上下文、资源授权账户、分类运行 SPI 和全内容协议清单尚未开放，不要通过内部类或方法注入提前实现这些契约。


## 本轮验证记录（2026-09-08）

- 主单元测试 1805 项通过，其中新增状态契约 6 项、节点契约 3 项；编辑器测试 109 项通过。三组 JVM 类指针兼容检查按原有跳过规则通过。
- `academy_api_example:registration_execution` 在独立 GameTest 服务端通过：同类技能、原生/门面注册、必需/可选依赖、只读集合、学习门禁、Codec 状态隔离、保存/执行、合计 CP 不足时不扣费/不应用、主动取消与实际 tick 到期、三种伤害模式和声明覆盖优先级。
- `academy:mental_defense` 回归通过，覆盖已确定的精神保护、目标反馈和标签抗性。
- `build -DisDev=true`、`build -DisDev=false`、`apiExampleJar`、`verifyApiExample` 通过；`-p examples/addon build --offline` 仅依赖主 MOD JAR 编译并打包成功。
- 隔离 `runClientDev -PacademyApiExample=true` 成功校验 9 个分类、100 个技能并加载附属资源，随后正常退出。本项是客户端加载检查；未声称已完成开发机逐项交互或双客户端联机验收。

没有新增或修改 Mixin 来实现本轮 API。运行日志和制品位于已忽略的 `build/`、`run/`，不会随源码提交。
