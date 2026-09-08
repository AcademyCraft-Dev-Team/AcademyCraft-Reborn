# 附属 MOD 对外注册 API 设计方案

日期：2026-09-08。依据：当前工作区 Minecraft 26.2.0 / NeoForge 26.2.0.70 实现。

本文同时记录设计目标与第一轮实现。四类注册的首个可用闭环已落地；可编译调用方式见 [附属 API 开发指南](addon_api_guide.md) 和 [示例附属](../examples/addon/README.md)。第 2 节保留改造前的问题，其他章节明确当前实现与后续工作；不把设计目标视为已发布的接口。

| 本轮已实现 | 公开入口 |
| --- | --- |
| 分类/技能/伤害行为/程序节点原生注册门面 | `AcademyRegistrations`、`AcademyKeys`、`Registries.Keys.DAMAGE_PROFILES` |
| 分类 ID 索引、构造声明与统一关联、只读完成通知 | `AbilityCategory.builder()`、`Skill.Builder.of(key)`、`AcademyRegistrationsReadyEvent` |
| 默认伤害、技能/请求覆盖、三种既有结算路径 | `AbilityDamageProfile`、`AbilityDamageService` |
| 技能 Codec 状态、版本迁移、未知数据保留 | `SkillStateType<T>`、`Skill.state/updateState` |
| 新分类通用程序、动作暂存与持续效果释放 | `ProgramProfile`、`ProgramNodes`、`ProgramExecutionContext.submit`、`AbilityProgramService` |
| 目标免疫反馈与标签抗性 | 第 8.1 节，规则与上一轮保持一致 |

当前尚未提供统一非玩家施术账户、可替换的分类运行 SPI、全内容登录清单和 Academy 专用 datagen 辅助；见第 8、10、11 节。

## 1. 推荐方案

保留现有 NeoForge 注册表，以可选的 `AcademyRegistrations` 薄封装统一附属 MOD 的入口；补齐声明模型、延迟关联、校验、运行服务及兼容约定。普通 `DeferredRegister` 和 `RegisterEvent` 必须仍然可用，且经过同一套最终校验。

四类内容共享命名、引用、诊断和文档规范，但不强行使用相同的加载机制：分类、技能和程序节点是静态代码注册；原版 `DamageType` 是数据包注册，Academy 特有的伤害行为使用单独的静态描述对象。

目标验收场景：附属 MOD 只引用公开 API，就能新增一个能力分类、两个技能、一种伤害及一个精密操作动作节点；能够在开发机学习技能、保存并执行程序、正确造成伤害，并能在独立服务端及重进世界后正常工作。

## 1.1 附属接入约束与测试版本兼容边界

以下约束适用于附属 MOD 对 AcademyCraft 的扩展，包括分类、技能、伤害、精密操作及其配套运行逻辑。

- **禁止破坏性注入。** 不得覆盖或替换本 MOD 的核心实现，不得取消原方法后接管完整流程，不得篡改注册表、冻结索引或框架状态以绕过正常入口，也不得绕过能力授权、资源消耗、伤害结算、状态保护及同步校验。限制按实际行为判断，不能通过改用反射、字节码转换、访问变换或其他注入技术规避。按照公开事件契约取消事件、通过公开策略接口调整行为属于受支持的扩展，不等同于破坏性注入。
- **非必要禁止对本 MOD 的方法进行 Mixin。** 能通过公开注册 API、事件、SPI 或服务完成的功能，必须使用这些入口；不得为了方便或少写适配代码，对公开 API 方法或内部方法使用 `@Inject`、`@Redirect`、`@ModifyArg`、`@ModifyVariable` 等方法注入，也不得以 `@Overwrite` 覆盖本 MOD 方法实现。
- **必要的兼容注入仅作为临时适配。** 必须存在公开扩展点确实无法满足的具体兼容需求，并优先补充或申请所需 API。临时适配应限定最小影响范围，记录必要性、目标方法、适用的本 MOD 版本、退出条件和移除计划；目标版本不匹配时禁用相关适配或明确报告不兼容，不得继续强行匹配其他方法。必要性不能豁免破坏性注入禁令，也不能将注入失败转为跳过框架校验。
- **测试版本只能尽可能保障公开 API 的兼容性。** 本 MOD 在测试阶段可能随时重构类、方法、字段和调用流程；只对明确文档化的公开 API 契约尽可能提供兼容适配和迁移说明，不承诺所有测试版本之间绝对兼容。`public` 修饰符不代表对外 API，`org.academy.internal`、Mixin 目标、方法描述符、调用点、局部变量布局和内部执行顺序均不属于兼容保证范围。
- **API 调用兼容不等于方法注入兼容。** 即使公开 API 的调用签名保持不变，其实现体仍可重构；不能据此要求保留某个注入位置。现存内部兼容桥接仅是针对特定历史问题的过渡措施，不构成继续注入或永久保留内部方法的承诺。受影响的附属负责限定支持版本并迁移到公开 API。

对外示例必须能够完全通过公开 API 完成注册和执行，不包含针对 AcademyCraft 类的方法 Mixin。上述内容是附属接入规范；本设计不声称已经实现自动拦截第三方注入的运行机制。

## 2. 改造前的实现与本轮问题来源

| 范围 | 当前实现 | 对外规范需要解决的问题 |
| --- | --- | --- |
| 注册表 | `api.common.registries.Registries` 已公开分类、技能、程序节点的 registry 和 key | 沿用现有 ID，不另建同用途注册表 |
| 分类 | `AbilityCategory` 是抽象类，技能集合按 Java `Class` 索引 | 同一技能实现类无法在同一分类注册多个实例；应按注册 ID 建立关系 |
| 技能 | 构造器立即 `category.addSkill(this)`、注册数据类型，并逐个挂载依赖解析监听器 | 构造带副作用；分类引用需要过早取值；自定义数据签名依赖 `internal.SkillData` |
| 完成阶段 | common setup 中先冻结节点扩展，再发布 `AbilitySystemFinalizedEvent`，随后校验技能并封闭分类 | “Finalized” 实际承担解析依赖的中间阶段，订阅者不能据此假定关联已校验完成 |
| 默认伤害 | `SkillDamageTypeResolver` 按内置分类 Java 类型分支 | 新分类不能声明自己的默认伤害，缺少技能级覆盖和稳定的通用伤害入口 |
| 特殊伤害 | `DamageTypes` 内部维护固定集合，`SkillDamageUtil` 负责不同结算路径 | 注册一个 JSON 不等于接入 Academy 的伤害路径和附效 |
| 节点扩展 | `ProgramNodeExtension` 已组合 codec、schema、执行器和编辑元数据，并生成兼容指纹 | 应继续使用；错误节点当前记日志后跳过，需要明确错误处理契约 |
| 新分类程序 | `AbilityProgramDefinitions` 是固定列表，`AbilityProgramManager` 另有固定执行分派 | 单独注册分类或节点不能使新分类拥有可执行的精密操作程序 |
| 动作节点 | `ProgramExecutionContext` 提供查询接口；事务与执行环境在 `internal` | 附属动作缺少正规的暂存、消耗结算、应用和清理入口 |
| 技能数据 | `SkillDataSerializer.registerType` 直接覆盖 map；未知类型退回通用数据 | 重复 ID 可能覆盖，移除附属后重存可能损失其自定义字段 |

已有的 `AbilityHitEffects` 已公开电荷、辐射、装备损耗等通用效果，应继续复用。公开门面在实现体内调用 `internal` 是正常封装；真正需要清理的是公开签名要求附属依赖内部类型，以及对内置分类的硬编码。

主要代码依据：

- [注册表](../src/main/java/org/academy/api/common/registries/Registries.java)
- [能力分类](../src/main/java/org/academy/api/common/ability/AbilityCategory.java)、[技能](../src/main/java/org/academy/api/common/ability/Skill.java)
- [启动关联顺序](../src/main/java/org/academy/AcademyCraftRegister.java)
- [默认伤害解析](../src/main/java/org/academy/internal/common/world/damagesource/SkillDamageTypeResolver.java)、[伤害类型规则](../src/main/java/org/academy/internal/common/world/damagesource/DamageTypes.java)
- [节点公开契约](../src/main/java/org/academy/api/common/ability/program/ProgramNodeExtension.java)、[节点扩展索引](../src/main/java/org/academy/internal/common/ability/program/ProgramNodeExtensionIndex.java)
- [程序定义](../src/main/java/org/academy/internal/common/ability/program/AbilityProgramDefinitions.java)、[程序执行分派](../src/main/java/org/academy/internal/common/ability/program/AbilityProgramManager.java)
- [技能数据序列化](../src/main/java/org/academy/internal/server/world/level/storage/SkillDataSerializer.java)

## 3. 公开包规划与当前入口

下表包含后续包规划；当前可用类型以文首状态表和开发指南为准。

| 位置 | 公开职责 |
| --- | --- |
| `org.academy.api.common.registries` | 现有 `Registries`；新增可选 `AcademyRegistrations` 门面 |
| `org.academy.api.common.ability` | 分类、技能、成长和资源声明；公开内置分类/技能的 `ResourceKey` 常量 |
| `org.academy.api.common.ability.data` | 技能状态基础类型、状态描述和版本迁移契约 |
| `org.academy.api.common.entitycontrol` | 现有实体控制 API；已公开心理掌握 tag 常量、免疫反馈与抗性剩余时间查询，后续防护规则提供者见第 8.1 节 |
| `org.academy.api.common.damage` | `AbilityDamageProfile`、结算模式、公开内置伤害 key、现有伤害源与附效门面 |
| `org.academy.api.common.ability.program` | 现有节点接口；新增分类程序声明和动作描述契约 |
| `org.academy.api.server.ability` | 施术上下文、能力效果执行及资源结算服务 |
| `org.academy.api.server.damage` | `AbilityDamageService`，统一服务器伤害入口 |
| `org.academy.api.server.ability.program` | 分类运行适配、程序动作服务；不公开 VM 实现 |
| `org.academy.api.client.ability` | 客户端技能绑定、可选专用节点控件/呈现 |
| `org.academy.api.data` | 伤害 JSON 与标签的数据生成辅助 |

`AcademyRegistrations.create(modId)` 提供四组方法：`categories()`、`skills()`、`damageProfiles()`、`programNodes()`，最后在附属构造器内 `bind(modEventBus)`。分类、技能、程序节点及新增伤害行为描述都由原生 `DeferredRegister` 实现；门面不自行创建第二份可写内容仓库。

新增静态注册表 `Registries.Keys.DAMAGE_PROFILES`，值类型 `AbilityDamageProfile`。它保存 Academy 的伤害语义，不替代 `minecraft:damage_type`。仅想使用普通原版伤害的技能可以直接引用 `ResourceKey<DamageType>`，无需注册 profile。

注册辅助必须是可选的便利层。不能出现“用门面注册可运行，用原生注册却不被索引”的差异。附属向现有分类增加技能，应引用公开 key，不引用 `internal.AbilityCategories` 或 `internal.Skills`。

## 4. 统一注册约定与生命周期

**标识与引用。** 使用 `modid:lower_snake_case`，节点可采用 `modid:program/category/action/name`。Java 类型不是内容身份。不同 registry 允许复用相同 ID，同一 registry 内重复 ID 报错。自己的注册方法只声明自己命名空间的条目；引用其他 MOD 的分类、技能或节点使用完整 `ResourceKey`。

**延迟引用。** 注册元数据使用 `ResourceKey<T>`；注册返回 `DeferredHolder<T, S>`。提供接收 holder 的便利重载时，仅提取其 key，不提前 `.get()`。构造器和静态字段初始化不查询其他条目、不访问世界、不注册运行事件。

**初始化顺序。**

```text
附属构造器声明内容并绑定自己的 mod event bus
  → Academy 创建自定义 registry，NeoForge 完成所有静态条目注册
  → Academy 在 common setup 的串行 enqueueWork 中统一处理：
      解析分类、技能依赖、伤害行为及程序引用
      → 构建分类技能集合、程序目录与运行适配
      → 汇总校验
      → 发布不可变索引与兼容描述
      → 发布 AcademyRegistrationsReadyEvent（只读通知）
  → 世界加载，获得本次世界的 DamageType registry 与标签
      → 解析并验证伤害 key，初始化本次服务器运行状态
  → 玩家登录，沿用注册表同步与程序节点兼容指纹检查
      （全内容玩法清单和差异报告仍待实现）
```

Ready 事件已在 NeoForge 游戏事件总线上发布一次，发生在本进程静态内容关联、校验、冻结之后；它不代表世界数据包已可访问。静态初始化不得依赖其他 MOD 的 common setup 执行先后；所有必需信息必须随 registry 条目预先声明。

旧 `AbilitySystemFinalizedEvent` 在兼容期保留原有中间阶段语义并标记弃用；不要直接把它改成新的 Ready 事件。技能的逐实例依赖监听已移除，统一关联在旧事件之前完成。

冻结后禁止修改内容声明、分类技能集合和运行路由。世界实例、玩家/实体状态、已解析的动态 registry holder 和程序会话不放进静态注册对象；同一 JVM 重建服务器时重新创建，并在停止/卸载时释放。

**错误策略（目标）。** 重复 ID、缺失必需引用、技能依赖环、无运行适配的程序分类、节点默认配置无效等错误汇总后阻止静态内容进入 Ready 状态。格式包含 MOD、registry、完整 ID、字段路径和关联链。可选依赖必须显式声明；不得默默忽略必需节点。运行中遇到存档缺失附属内容则保留原始数据、禁用对应技能或程序，并报告可读诊断。

当前关联阶段聚合技能引用/状态错误，节点阶段聚合无效节点；分类/依赖环、伤害冲突等校验仍按阶段抛出异常。错误会阻止 Ready 事件，并包含内容 ID；全流程统一的 registry/字段路径/关联链诊断仍待补齐。

## 5. 能力分类与技能

分类提供 `AbilityCategory.builder()`，生成默认实现；保留现有继承方式供复杂分类使用。声明至少覆盖显示名翻译键、图标、是否支持通用技能；可选项包括开发选择权重/P.R.O.P.S、分类资源、默认伤害及程序支持。

- 初始开发选择必须明确是否参与；参与时校验所用权重和 P.R.O.P.S 参数，不把新增分类误排除或使用隐含的内置分支。
- 分类技能集合由已注册 `Skill` 的归属声明派生，按完整技能 key 索引，UI 顺序采用显式排序值再按 ID 排序。
- 支持“给内置分类增加技能”及“同一实现类注册多个技能实例”。不向附属开放任意改写其他技能声明的 setter。

技能沿用 `Skill` 与 `Skill.Builder`，补充接收 `ResourceKey<AbilityCategory>` 的工厂和 `dependsOn(ResourceKey<Skill>...)`；公共技能提供独立工厂 `common()`，避免要求调用者选择一个没有语义的占位分类。兼容期内部可适配旧占位约定。

技能构造只保存声明，统一关联阶段才绑定分类并建立依赖。保留等级、开发能量、CP、维护占用、熟练度、开发条件等既有能力；附属分类技能仍须显式声明 `proficiencyProfile`，允许明确声明 `NONE`。新增一致的链式 `icon(...)`、`damageType(...)` 和 `damageProfile(...)`，两种伤害覆盖方式互斥。

自定义状态公开 `SkillStateType<T>` 描述，包含 namespaced 类型 ID、`Codec<T>`、默认值工厂、数据版本及迁移函数，由技能声明引用。同一描述可被多个技能共享；最终阶段从技能声明导出只读类型索引，同 ID 的不同定义必须报错。当前已用框架内部 envelope 承载公开 Codec 状态；旧 `withCustomData` 仍可读取历史数据，附属无需继承内部数据类。通过 `state(player, type)` 读取、`updateState(player, type, value)` 在服务端线程更新并标记存档脏状态。请使用不可变状态值。

熟练度、启用状态、CP 等框架字段由 Academy 的状态服务管理；附属扩展状态独立编码。不得因开放自定义状态而放开当前状态写保护。旧 `internal.SkillData` 及受影响的工厂/方法签名须用兼容桥接处理，不能只移动包名后删除旧类型。

## 6. 伤害类型与能力伤害行为

必须区分三个概念：`DamageType` 是数据定义；`AbilityDamageProfile` 是 Academy 结算行为；`SkillDamageSource` 是某次攻击及其归属上下文。

1. 原版类型：声明 `ResourceKey<DamageType>`，提供 `data/<modid>/damage_type/<path>.json`。护甲、火焰等原版分类关系尽量通过标准 damage type 标签表达。数据生成可使用 `RegistrySetBuilder` / `BootstrapContext`，生成文件随附属 JAR 分发；运行时创建一个 key 不会创建对应条目。
2. 能力行为：注册 `AbilityDamageProfile`，引用上述 key，声明公开结算模式、是否绕过吸收与兼容修订。当前可选 `STANDARD`、`DIRECT`、`TRUE_HEALTH`，分别路由到已有普通、直接、真实生命结算流程；分类附效提供者尚未加入 profile；不把 `actuallyHurt`、Mixin 或重入防护暴露为附属调用接口。
3. 技能关联：伤害请求显式指定 > 技能声明 > 分类默认。显式引用缺失应报错；只有完全未声明类型的旧玩家技能，才保留原来的普通玩家攻击回退。
4. 执行入口：`AbilityDamageService.apply(ServerPlayer, LivingEntity, Skill, Request)` 用于已通过授权/扣费的施术，复用既有归属、目标规则、伤害组成和完成流程，不额外扣费或回退重试。当前 `Result` 返回 `applied/healthLost/absorptionLost`；显式无效引用和异常不伪装成普通攻击。更细的阻止/失败原因与公开完成回调仍待扩展。

一个 damage type 最多对应一个 Academy 行为 profile，多个技能可以共享它。冲突必须报出两个注册来源；禁止用注册顺序决定最终规则。这样来自普通 `DamageSource` 的同类型伤害也能有确定行为。

后续分类附效方案：分类通用附效与伤害结算模式分别声明：例如电荷累积属于分类效果，不应通过某个技能类的私有方法触发。框架在确认结算后调用一次分类附效，技能附加效果按明确顺序执行；反射、追加伤害与自动脉冲使用上下文标志沿用现有抑制重复触发的规则。

动态 damage type holder 从当前服务器/世界的 `RegistryAccess` 解析，不在静态字段永久缓存。标签或其他可重载配置变化时重建相关派生结果；不承诺普通 `/reload` 可以增删静态 Java 注册对象，也不假定所有世界级 registry 都会随它重建。

原版伤害数据包机制依据 [NeoForge Damage Types 文档](https://docs.neoforged.net/docs/resources/server/damagetypes/)，代码注册机制依据 [NeoForge Registries 文档](https://docs.neoforged.net/docs/concepts/registries/)。查阅时文档版本为 26.1；具体签名和生命周期接入以项目 26.2 依赖源码及编译验证为准。

## 7. 精密操作节点与新分类程序

继续以 `ProgramNodeExtension<C>` 为附属节点契约，一次声明配置 codec、schema/version、role/purity、分类范围、所需能力、执行器及编辑元数据。编辑器、编译器、运行器使用同一条注册项，避免维护三份目录。

分类已有可选 `ProgramProfile(entryNode, limits, spatialLimits)`，框架为所有注册了 profile 的新增分类建立程序定义和通用运行路由。原有内置分类运行器保留；可替换的运行适配工厂和内置分类统一迁移属于后续工作。

声明程序支持后，通用 GUI、槽位保存、同步、编译、调度和释放必须自动生效。当前统一保持 Level 5 解锁，首版不开放自定义解锁条件。仅增加节点时不要求附属重新注册整个分类的程序。共享节点也必须遵守节点自身的 required capabilities 与当前施术者权限。

入口节点需要声明它支持的触发方式并能被调度器识别；注册一个 `ENTRY` role 不自动增加新的全局触发事件。首版复用已有手动和自动触发类型，自定义触发源另行通过公开运行服务调用。

查询/运算节点继续使用只读 `ProgramExecutionContext`。动作节点增加受控的动作提交接口：由当前执行绑定节点身份，接收公开的 `ProgramAction` 描述；框架验证已学会且启用的能力、当前分类、范围、目标和 CP，暂存后统一应用。首版自动结算 CP；分类 MP/自定义资源账户尚未纳入该动作服务。附属不自行取得内部 transaction、attachment 或 executor state。

本轮已将原来直接传递的 `ProgramVmContext` 替换为独立公开视图，仅当前 ACTION 执行器返回前允许提交动作，并使 `ProgramTargetResolver` 的实体引用语义明确，避免把“只读接口”误当成 Java 沙箱。它是稳定调用契约，不能隔离同 JVM 中有完整权限的 MOD。

动作补偿必须区分可撤销效果与不可逆效果。可撤销动作返回清理句柄；伤害、死亡、掉落等不能承诺整张图原子回滚。统一先验证，再按约定提交；发生部分提交时保留已完成效果并报告状态，不通过普通伤害重试造成第二次攻击。持续效果在自然到期、取消、死亡、维度切换或服务器停止时释放。

当前公开动作使用 `ProgramEffect` 的清理回调补偿已成功应用的可撤销效果；已扣 CP、伤害和附属自行修改的持久状态不回滚。附属 `apply` 自身抛异常前已经产生的效果，须由附属自行清理。框架汇总同一执行帧中尚未支付的附属动作 CP 预算，混合内置动作的自有资源事务不会被合并成一笔全图原子事务。`AbilityProgramService.Result` 返回成功/延期/拒绝/失败及节点诊断；失败后不能假定图中从未发生过副作用。

## 8. 通用效果与非玩家施术扩展

遵循仓库“先整理分类通用效果、再实现具体技能”的要求：技能和动作节点调用同一公开效果方法，节点不伪装玩家按键，也不直接调用具体技能的 `Server` 内部类。

建议公开 `AbilityActor` 与 `AbilityExecutionContext`：提供服务器世界、实际施术实体、伤害直接来源/归属、能力 key、授权信息及资源账户。目标优先使用 `LivingEntity` 或明确类型的世界引用，玩家专属资料通过可选适配获取。

本轮伤害和程序执行服务以 `ServerPlayer` 为施术者；统一 `AbilityActor`、上下文和非玩家资源授权适配留待下一轮。目标方案为：没有玩家学习/CP 数据的实体由调用方提供明确的授权和资源账户，未提供时拒绝需要这些能力的操作，不能自动获得免费施术权限。资源、权限与熟练度分别建模，不把 `ServerPlayer` 替换成 `LivingEntity` 后继续强转。

既有公开效果入口如 `AbilityHitEffects` 保持兼容。纯效果方法说明是否已经包含伤害完成附效；通用执行服务负责消耗和一次性触发，避免技能与节点各扣一次 CP 或各触发一次命中效果。

## 8.1 心理掌握：精神保护、目标反馈与精神抗性

2026-09-08 调整。本节明确区分已实现入口与后续方案：本次已提供公开标签常量、免疫反馈 API、抗性剩余时间查询，并实现带标签实体的自动挣脱。其他统一防护规则提供者、来源隔离的抗性授予服务仍属于后续设计。

### 8.1.1 公开标签

公开常量位于 `org.academy.api.common.entitycontrol.MentalControlTags`。

| 标签 ID | 公开常量 | 默认内容及用途 |
| --- | --- | --- |
| `academy:mental_control_immune` | `IMMUNE` | 默认空列表；阻止受精神保护门禁管理的控制、潜入及感知干预；心灵介入拒绝时可使用目标专属反馈 |
| `academy:mental_control_resistance` | `RESISTANCE` | 默认空列表；生物和玩家持续受到心灵效果影响超过 20 秒后自动挣脱，临时阻断 5 秒；心灵介入清单关系除外 |
| `academy:mental_control_boss_cost` | `BOSS_COST` | 默认 Warden、Wither、Ender Dragon；现有部分心理程序过滤及 `MentalControlApi.isBossCost` 查询使用；不等于自动提高当前通用控制费用 |

三者均为 `TagKey<EntityType<?>>`，属于**实体类型标签**，影响该类型的所有实例；不是 `/tag` 命令操作的实体字符串标签。附属通过数据包追加成员，保留原有 ID 和 `replace: false`，不得覆盖其他附属的标签成员。

例如为附属的无心智实体提供保护，在附属 JAR 的 `data/academy/tags/entity_type/mental_control_immune.json` 中声明：

```json
{
  "replace": false,
  "values": ["example:mindless_entity"]
}
```

为附属的高抗性实体启用自动挣脱，在 `data/academy/tags/entity_type/mental_control_resistance.json` 中声明：

```json
{
  "replace": false,
  "values": ["example:resistant_entity"]
}
```

文件使用 `academy` 命名空间，因为扩展的是 Academy 的标签；条目使用实际注册实体的命名空间。不会给任何原版实体默认添加精神抗性；需要玩家参与此规则时，显式追加 `minecraft:player`。

### 8.1.2 免疫目标的反馈 API（已实现）

入口：`org.academy.api.common.entitycontrol.MentalControlFeedbackApi`。

```java
// 在附属初始化阶段注册一次；不要求此时已解析到 EntityType 实例。
MentalControlFeedbackApi.registerImmuneFeedback(
        Identifier.fromNamespaceAndPath("example", "mindless_entity"),
        (controller, subject) -> Component.translatable(
                "message.example.mental_control.no_mind", subject.getDisplayName())
);
```

附属应在自己的语言文件中定义 `message.example.mental_control.no_mind`。回调取得实际施术者和目标，可根据目标装备、名称或其他实例状态选择不同的 `Component`，同一实体类型的不同实例也能提供不同说明。

契约：

- 以完整实体类型 ID 注册；每个 ID 只允许注册一个提供者。重复注册抛出 `IllegalArgumentException`，不会覆盖已有提供者。空 ID、空回调立即拒绝。
- 注册表在当前 JVM 中持续有效；不在每次服务器启动时重复注册。附属负责为自己的实体类型注册，并保持初始化顺序明确。
- 只有属于 `MentalControlTags.IMMUNE` 的目标才调用该回调；注册反馈本身不赋予免疫，也不改变门禁结果。
- 回调在服务端线程执行，只负责返回文本，不得修改游戏状态、扣 CP、发包或改变免疫结果。返回 `null`、没有注册回调时沿用内置提示；回调抛出运行时异常时回退，并按实体类型只记录一次错误，避免持续效果刷屏。
- 心灵介入被保护拒绝时，实际发送该目标的专属提示。其他使用共同保护反馈入口的控制技能同样复用此文本；无法适配且未受保护的目标仍提示“不支持该目标”。
- `immuneFeedback(controller, subject)` 是不发送消息的文本解析入口，返回组件副本；未标记目标或没有可用结果时返回通用保护文本。实际技能门禁发送时会保留原有保护来源的默认说明及特效，例如未元物质甲虫的内置提示。
- 使用 `Component.translatable` 支持客户端本地化，不要求附属通过 Mixin 覆写内部反馈方法。

### 8.1.3 标签精神抗性的确定规则（已实现）

| 项目 | 规则 |
| --- | --- |
| 适用对象 | 实体类型属于 `academy:mental_control_resistance` 的 `LivingEntity`，包括服务器玩家；不要求能力等级、按键输入或玩家能力数据 |
| 计时条件 | 从首个服务端确认有效的外来影响的游戏 tick 开始；连续影响的经过时间 **大于 400 tick** 才触发。恰好 400 tick 不触发，经过 401 tick 时挣脱 |
| 效果范围 | 共同控制运行时的控制效果，包括呆然、印象、误认、接管和精密操作产生的控制；感知干预、确认后的心灵潜入，以及精神破坏的持续伤害和反应迟钝 |
| 心灵介入例外 | 单纯加入/保留受控清单不计时；挣脱不删除清单关系。自动抗性的 5 秒内仍允许心灵介入清单操作，但其附带开启的呆然、印象等独立效果继续受阻断 |
| 连续与叠加 | 按目标合并不同技能、不同施术者的有效影响；同 tick 多个效果不加速，续期、重施或切换施术者不重置连续计时。完整一个 tick 没有有效影响后重新计时，不跨间断累计 |
| 挣脱行为 | 结束受控会话，释放以该实体为目标的控制租约、呆然、印象及增益、误认、潜入和感知干预，并清除针对它的持续精神破坏及反应迟钝；相关精密操作沿既有实体清理入口终止 |
| 临时阻断 | 挣脱当 tick 获得 100 tick 抗性；阻止任意施术者重新施加上述效果，5 秒到期后恢复接受。阻断期间的失败尝试不延长时长，也不积累下一轮计时 |
| 与原有玩家机制共存 | 玩家按键挣脱和标签自动挣脱均可触发；原有按键资格、门槛及按施术者等级决定的时长保持不变。查询取仍有效阻断中的最长剩余时间，不以 100 tick 覆盖更长的玩家抗性 |
| 生命周期 | 服务端内存状态；死亡、离开世界、登出、换维度、服务器停止会清理。标签移除会终止尚未触发的连续计时；已经获得的 100 tick 阻断自然到期。重载标签后按当前成员重新判断计时资格 |

时间以服务器 `gameTime` 计量，秒数按正常 20 TPS 换算，不是物理时钟。标签抗性不等于永久免疫，也不提供精神伤害百分比减免；精神破坏本身一次只有 10 秒，单次不会独立触发 20 秒阈值，但无间断重施或与其他心灵效果连续衔接会计时。

公开查询：`MentalControlApi.resistanceRemainingTicks(LivingEntity subject)`，返回服务端剩余游戏 tick，无抗性为 `0`；查询同时包含标签自动挣脱与原有玩家按键挣脱的临时阻断，不改变状态或播放特效。

附属通过 `MentalControlApi` 申请控制租约时自动接入这套计时、准入和持续复核。自行维护的附属效果不会因实体有 tag 就自动得到清理；这类效果需要后续统一效果生命周期接口，不应依赖或注入内部管理器来伪装接入。

### 8.1.4 原有精神保护与玩家按键挣脱

普通效果的保护判断依次考虑：未元物质甲虫的网络保护、免疫类型标签、标签挣脱的临时抗性、玩家按键挣脱的临时抗性、玩家矢量反射/偏移、电磁护盾、未元物质六翼。任一有效保护阻止相应效果，顺序主要决定提示来源。除新标签抗性外，现有技能产生的动态保护仍以服务器玩家为对象。

`MentalControlApi.evaluate` 先检查保护，再选择适配器；持续租约也复核保护。公开拒绝原因保留 `IMMUNE_TAG` / `PROTECTED_PLAYER` 的既有映射，后者可能用于非玩家目标，不能据此反推目标一定是玩家。清单同步的“受保护”和“有抗性”标志可以同时存在，现在也对生物同步抗性。

原有玩家按键挣脱保持以下行为：

| 项目 | 规则 |
| --- | --- |
| 资格 | 玩家能力等级不低于当前施术者等级，施术者等级大于 0；等级按 0～5 归一化 |
| 多个施术者 | 当前有效影响中的最高施术者等级决定门槛与时长；存在接管影响时使用接管输入倍率 |
| 门槛 | `2 × 施术者等级² + 5`；Lv1～Lv5 为 7、13、23、37、55 点 |
| 输入 | WASD 与鼠标左右键的按下边沿；普通每键 1 点，接管每键 2 点；持续按住不重复计数 |
| 服务端校验 | 六类输入掩码、递增序号、每游戏 tick 最多一个有效输入包 |
| 挣脱抗性时长 | `(20 - 2 × 施术者等级) × 20 tick`；Lv1～Lv5 为 360、320、280、240、200 tick |
| 清理边界 | 保留心灵介入清单，释放控制、印象及增益、潜入、精密操作和感知效果；原有按键挣脱仍不直接清除精神破坏持续伤害 |

原有 `mental_control_immune` 标签和技能防护**不自动免疫 `academy:mentaldamage`**：精神破坏的呆然受保护门禁限制，持续伤害仍能启动。本次增加的是标签自动挣脱后的完整效果清理和 5 秒阻断，未扩大旧免疫标签的伤害含义。友伤、PvP、存活、维度及适配器支持仍为独立判定。

对自己申请寻路/视线操作的既有准入例外与持续复核可能不一致；这不是本次新增的契约，应另行专项验证后统一。

### 8.1.5 废弃配置与后续 API 边界

- `PlayerControlSessionManager.grantResistance` 仍只释放玩家输入租约，名称不代表授予定时抗性；不得将其转发为附属的稳定抗性授予 API。
- Boss 标签与部分费用配置保留；通用控制费用目前主要按目标最大生命值和施术者能力强度分档，不将旧“普通 30 CP / Boss 60 CP”描述为新契约。

后续方案仍需提供统一只读 `MentalDefenseApi.inspect(query)`，携带施术者、目标、效果来源、作用种类，明确区分控制与精神伤害；扩展动态防护规则时，普通提供者不得撤销标签或其他来源的拒绝。若开放抗性授予与撤销，须另行定义来源隔离、续期/叠加和撤销权限。本次反馈注册表为独立可用入口，尚未并入本方案其他注册项的统一校验与冻结生命周期。

遵循第 1.1 节：**禁止破坏性注入；非必要禁止对本 MOD 方法进行 Mixin。** 附属应通过标签、反馈提供者和已公开的查询/控制 API 接入，不依赖内部类、静态 map 或 tick 调用顺序。测试版本内部方法可能随时重构，只对明确列出的公开 API 尽可能保障兼容。

### 8.1.6 实现依据与验证

主要入口：[公开标签](../src/main/java/org/academy/api/common/entitycontrol/MentalControlTags.java)、[免疫反馈 API](../src/main/java/org/academy/api/common/entitycontrol/MentalControlFeedbackApi.java)、[精神抗性标签](../src/main/resources/data/academy/tags/entity_type/mental_control_resistance.json)、[保护门禁](../src/main/java/org/academy/internal/common/ability/mentalout/control/MentalControlProtection.java)、[抗性管理器](../src/main/java/org/academy/internal/common/ability/mentalout/MentalResistanceManager.java)、[控制运行时](../src/main/java/org/academy/internal/common/ability/mentalout/control/MentalControlRuntime.java)、[精神破坏](../src/main/java/org/academy/internal/common/ability/mentalout/skills/lv5/MindDestruction.java)。

新增 `MentalResistanceTrackerTest` 验证严格 20 秒边界、准确 5 秒时长、重复施放、间断重计、阻断期重试、资格变化及生命周期清理。`MentalDefenseGameTests` 使用实际标签、玩家、生物与控制租约，验证目标专属反馈与回退、重复注册拒绝、玩家/生物自动挣脱、清单例外、感知释放、临时准入拒绝和到期恢复。保留原有玩家挣脱公式与协议测试。已通过 1796 项单元测试及 1 项精神保护 GameTest；GameTest 在隔离环境中推进服务端时钟验证边界，完成后恢复时钟与标签，不代表多客户端实机验收。开发版与发布版 build 均通过，隔离 runClientDev 完成 MOD/资源加载并正常退出；三组额外 JVM 类指针兼容检查也通过。

## 9. 附属开发者使用形态

下面的注册入口已实现；`PROPS`、`FrostBolt`、`FreezeNode` 等是示意的附属定义。完整可编译的 `ExampleAddon` 和对应资源、GameTest 在 `examples/addon`，以该工程为准。

```java
private static final AcademyRegistrations CONTENT =
        AcademyRegistrations.create(MOD_ID);

// 只声明 key；真实 DamageType 由附属的数据包文件提供。
public static final ResourceKey<DamageType> FROST_TYPE =
        ResourceKey.create(Registries.DAMAGE_TYPE, id("frost"));

public static final DeferredHolder<AbilityDamageProfile, AbilityDamageProfile> FROST =
        CONTENT.damageProfiles().register("frost", () ->
                AbilityDamageProfile.builder(FROST_TYPE)
                        .settlement(DamageSettlement.STANDARD)
                        .build());

public static final DeferredHolder<AbilityCategory, AbilityCategory> CRYOKINESIS =
        CONTENT.categories().register("cryokinesis", () ->
                AbilityCategory.builder()
                        .translationKey("ability.example.cryokinesis")
                        .icon(id("textures/gui/ability/cryokinesis.png"))
                        .developmentProfile(PROPS)
                        .defaultDamageProfile(FROST.getKey())
                        .program(ProgramProfile.standard(ENTRY_NODE_KEY))
                        .build());

public static final DeferredHolder<Skill, FrostBolt> FROST_BOLT =
        CONTENT.skills().register("frost_bolt", () ->
                new FrostBolt(Skill.Builder.of(CRYOKINESIS.getKey())
                        .level(AbilityLevel.LEVEL1)
                        .cpCost(20)
                        .proficiencyProfile(FROST_PROFICIENCY)));

static {
    // 对应 ENTRY_NODE_KEY；框架提供标准手动入口节点实现。
    CONTENT.programNodes().register("program/cryokinesis/entry/on_cast", () ->
            ProgramNodes.manualEntry(CRYOKINESIS.getKey()));
    CONTENT.programNodes().register("program/cryokinesis/action/freeze", () ->
            new FreezeNode(CRYOKINESIS.getKey(), FROST_BOLT.getKey()));
}

public ExampleAddon(IEventBus modBus) {
    CONTENT.bind(modBus);
}
```

示例里的 `Registries.DAMAGE_TYPE` 指 Minecraft 原版注册表 key；Academy 的 key 保持在 `org.academy.api.common.registries.Registries.Keys`。`.getKey()` 读取注册句柄的身份，不解析条目实例。翻译、图标、伤害 JSON 和标签由附属自己的资源或数据生成器提供。

## 10. 客户端、同步与存档兼容

实现状态：已新增字符串 `ID_CODEC` 而保留旧数字 codec；已保留缺失/未来/解码失败的扩展状态；节点指纹已排除翻译键，加入默认配置端口的名称、类型、必需性和连接限制。节点仍需作者维护兼容修订，配置相关的全部 schema 变体不能仅靠默认配置摘要证明一致。

下面的全内容清单、登录差异报告、别名迁移及正式 SDK 发布仍是目标；当前注册表同步和节点指纹不等同于实现了这些目标。

- 玩法内容在客户端和服务端一致注册。公共声明只含翻译键、资源 ID 和结构化编辑元数据；客户端按键、控件和渲染实现通过客户端入口绑定，不在 common 构造器中加载客户端类。
- registry 的 `.sync(true)` 不等于同步 Java 实现或保证节点语义一致。建立玩法内容清单，至少包含 ID、依赖/归属、状态版本、节点 schema/compatibilityVersion、端口结构与伤害行为修订；区分 API 兼容版本、数据格式版本和玩法协议版本。
- 登录时先比较玩法协议和清单，缺失必需条目或不兼容语义时显示具体差异并拒绝进入相关玩法。服务端始终重新校验程序，不信任客户端提交的能力列表、成本或目标权限。
- 指纹按 namespaced ID 排序并使用确定编码。界面语言和图标不应导致玩法指纹不同；编辑元数据可维护独立摘要。显式 compatibilityVersion 仍由作者维护，摘要不能证明两端代码行为完全一致。
- 存档、配置和长期导出文件只写稳定 key。当前 `AbilityCategory.CODEC` 和 `Skill.CODEC` 使用数字 ID，应新增 ID codec 并审计调用方；网络可以继续使用会话内同步映射。不要直接修改所有旧 codec 而不给已持久化数据安排迁移。
- 当前玩家主要存档已有 namespaced 字符串，保持格式并补充必要别名。遗留裸数字 ID 若没有原始映射不能可靠恢复，必须报告问题，不按本次注册顺序猜测。
- 缺失技能/节点保留原始数据和类型 ID；程序作为不可执行内容展示诊断。附属重新安装后可以恢复。状态和节点升级提供版本迁移，未知未来版本不覆盖原始数据。
- 兼容政策以第 1.1 节为准：测试阶段只尽可能保障文档化的公开 API 契约。公开 API 变更优先提供兼容桥接、弃用说明和迁移指引；确有无法兼容的调整时明确说明受影响版本。现有特殊伤害兼容入口可在迁移期保留并验证，但不保证历史内部签名或 Mixin 注入点永久存在，附属应迁移到公开伤害服务。
- 发布可供 Gradle 引用的开发制品、sources/Javadoc 与独立示例工程，明确支持的 Minecraft、NeoForge 和 Academy 版本范围。新 SPI 在稳定前标注实验状态；CI 检查示例工程不导入 `org.academy.internal`、不包含以 AcademyCraft 类为目标的方法 Mixin，并检查公开签名没有泄漏内部类型。公开门面的实现体允许调用内部实现。必要的临时兼容模块另行记录目标版本和退出路径，不能作为推荐的 API 使用示例。

## 11. 实施顺序与验证

本轮交付四类注册的首个可用闭环及只依赖公开 API 的示例。后续仍须补齐：统一结构化注册诊断、分类附效策略、非玩家授权/资源账户、分类运行 SPI、完整玩法清单及登录差异提示、状态/节点别名迁移、专用数据生成辅助和正式 SDK 发布。原阶段表保留为完整设计的验收标准，不表示整阶段已全部完成。

| 阶段 | 工作 | 完成标准 |
| --- | --- | --- |
| 1：注册与关联 | key 引用、分类 ID 索引、公开 builder、统一解析/诊断、Ready 事件、旧接口适配 | 原生 DeferredRegister 与门面得到相同结果；两个 MOD 注册先后不同也能正确解析 |
| 2：伤害与状态 | 伤害 profile、数据生成辅助、统一伤害服务、公开状态类型和兼容读取 | 新分类伤害正确路由，特殊伤害不回退重试，重复状态 ID 可定位，旧数据可恢复 |
| 3：精密操作闭环 | 分类 ProgramProfile、公共运行适配、动作服务、动态目录/执行路由 | 新分类无需改核心列表即可打开编辑器、保存、同步、编译、执行和释放程序 |
| 4：对外验收与发布 | 最小独立附属示例、实体适配示例、兼容清单、开发文档 | 示例仅依赖公开 API；客户端和独立服务端验证通过 |

重点测试：

1. 同类多实例技能、向已有分类增加技能、跨 MOD 的必需/可选依赖、循环依赖、重复 ID、冻结后修改和两种注册入口等价。
2. 新分类的完整学习与精密操作流程；查询与动作权限一致；未学会技能、目标越界、CP 不足时无副作用；损坏节点配置可定位到具体字段。
3. 伤害默认/覆盖优先级、缺失 JSON、行为冲突、伤害归属、反射/递归、附效只执行一次、最大生命值组成和特殊伤害兼容入口。
4. 可补偿效果清理、不可逆效果部分提交、同 JVM 重进世界、服务器停止及多个实体状态隔离。
5. 调整注册顺序、升级状态格式、移除再安装附属、客户端服务端不匹配、语言资源不同，以及独立服务端不加载客户端类型。
6. 示例附属不依赖内部方法或方法注入；检查文档中的公开 API 兼容承诺与第 1.1 节一致；必要临时兼容模块在目标版本不匹配时明确停用或报告不兼容。

实施阶段按仓库要求执行 `test -DisDev=true`、开发构建和发布构建；涉及数据生成时运行 `runClientData` 并审查输出；用开发客户端和独立服务端验证示例附属。纯设计阶段不需要启动游戏或执行构建。

第一轮应完成四类注册的使用闭环；暂不扩展运行时热增删 Java 内容、任意脚本执行、自定义 VM 指令或通用第三方伤害管线替换。这些功能各有独立生命周期和兼容成本，不作为附属注册的前置条件。


本轮实际验证结果见 [开发指南的验证记录](addon_api_guide.md#本轮验证记录2026-09-08)。独立示例已经编译和服务端验收；完整阶段的未完成项仍按本节继续跟踪。
