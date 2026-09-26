# NeoForge 26.2 coremod 迁移记录

## 已迁移的字节码处理

`TemporalBoundaryTransformer`、`WorldWeaverConfigTransformer` 和 `HealthReadInliner`
现在随独立的 `FMLModType: LIBRARY` JAR 加载。`ClassProcessorProvider` 使用
`META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessorProvider`
注册三个定向处理器，显式安排在 Mixin 后执行。生命读取处理器先内联真实生命
偏移，再于 `LivingEntity#getHealth` 的每个返回点调用按玩家实例判断的生命钳制。
主模组的 `MixinPlugin` 仍负责
`IrisIntegration.init()`，不再在 `postApply` 中改写目标类。

coremod JAR 只引用 FML SPI、ASM 和日志接口；它在字节码中写入的游戏侧方法引用，
由游戏类加载层在目标类实际加载时解析。开发运行通过 `runtimeOnly` 加载该 JAR，
发布 JAR 则通过 `jarJar` 内嵌同一个文件。

在 NeoForge 26.2.0.70 的开发客户端启动日志中，FML 发现了
`AcademyCoremodProvider`，并执行了生命读取、时间边界及 WorldWeaver 处理器。
未安装 BetterEnd 时，WorldWeaver 处理器的重写计数为零，这是预期结果；
安装该兼容模组后的实际 NBT 路径仍需单独验证。

## 玩家保护现状

`PlayerProtectionLoadTimeProbeTest` 验证了：类加载时注入的方法守卫可以按玩家
实例切换；只处理基类时，第三方玩家子类的覆写会绕过守卫。测试还用
`MethodHandles.Lookup#defineClass` 定义运行时子类，复现了不经过既有加载时
处理器的路径。

主模组不再改写 HotSpot 对象头，也不生成或切换玩家派发子类。`VectorHealthLedger`
以玩家实例为键维护生命值状态；Mixin 在 `SynchedEntityData.DataItem#setValue` 处
限制普通写入，并在玩家维护循环中修复不一致的同步值。技能自身的合法扣血使用
限定作用域的写入许可。服务端的 `SurvivalDefense` 租约与现有伤害、死亡、效果和
移除守卫继续生效。

父类的读取钳制无法覆盖不调用 `super.getHealth()` 的第三方覆写，运行时定义的
玩家子类也不一定经过 FML 的类处理器。旧实现可从提交 `639dd484` 提取；如需
恢复这部分更强的兼容范围，后续应以 `academy_hack` 为独立 modid 构建附属包，
不再让主模组依赖 HotSpot 对象头布局。主模组的保护范围以原版玩家及调用父类
实现的兼容子类为准。

## 参考

- [NeoForge 26.2 官方 coremod 实现](https://github.com/neoforged/NeoForge/blob/26.2.x/coremods/src/main/java/net/neoforged/neoforge/coremods/NeoForgeCoreMod.java)
- [ModDevGradle 独立 coremod JAR 打包说明](https://github.com/neoforged/ModDevGradle#local-files)
