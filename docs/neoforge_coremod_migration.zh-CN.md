# NeoForge 26.2 coremod 迁移记录

## 已迁移的字节码处理

`TemporalBoundaryTransformer`、`WorldWeaverConfigTransformer` 和 `HealthReadInliner`
现在随独立的 `FMLModType: LIBRARY` JAR 加载。`ClassProcessorProvider` 使用
`META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessorProvider`
注册三个定向处理器，显式安排在 Mixin 后执行。主模组的 `MixinPlugin` 仍负责
`IrisIntegration.init()`，不再在 `postApply` 中改写目标类。

coremod JAR 只引用 FML SPI、ASM 和日志接口；它在字节码中写入的游戏侧方法引用，
由游戏类加载层在目标类实际加载时解析。开发运行通过 `runtimeOnly` 加载该 JAR，
发布 JAR 则通过 `jarJar` 内嵌同一个文件。

在 NeoForge 26.2.0.70 的开发客户端启动日志中，FML 发现了
`AcademyCoremodProvider`，并执行了生命读取、时间边界及 WorldWeaver 处理器。
未安装 BetterEnd 时，WorldWeaver 处理器的重写计数为零，这是预期结果；
安装该兼容模组后的实际 NBT 路径仍需单独验证。

## 玩家保护迁移判定

`PlayerProtectionLoadTimeProbeTest` 验证了：类加载时注入的方法守卫可以按玩家
实例切换；只处理基类时，第三方玩家子类的覆写会绕过守卫。测试还用
`MethodHandles.Lookup#defineClass` 定义运行时子类，复现了不经过既有加载时
处理器的路径。

当前 `DispatchSubclassFactory` 会以玩家的实际运行时类型为父类生成派发子类，
所以它能覆盖符合其约束的第三方玩家子类方法。仅靠 NeoForge 的加载时
`ClassProcessor` 无法保证相同覆盖范围：它需要识别并改写所有可能的玩家
子类，且不能追溯已定义的类。未完成等价兼容证明前，保留
`ClassPointerProtectionManager`、`HotSpotClassPointerAccess`、派发模板及
Mixin 后备逻辑，不删除现有 JVM 配置专项测试。

若后续要继续替换，需要先定义可接受的第三方动态子类兼容范围，再验证服务端与
客户端的生命读取、真实生命写入、同步数据、伤害、死亡、药水效果、移除及技能
停用恢复流程。每个已知入口都需在启用和停用两种状态下与现实现比对，
最后在实际客户端和服务端验证。

## 参考

- [NeoForge 26.2 官方 coremod 实现](https://github.com/neoforged/NeoForge/blob/26.2.x/coremods/src/main/java/net/neoforged/neoforge/coremods/NeoForgeCoreMod.java)
- [ModDevGradle 独立 coremod JAR 打包说明](https://github.com/neoforged/ModDevGradle#local-files)
