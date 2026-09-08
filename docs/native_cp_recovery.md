# 本体能力分类的 CP 恢复保护

当玩家使用本体注册的能力分类（含无能力）时，CP 总上限与恢复流程由本体管理。生产实现按 AbilityCategories 的注册条目判断归属，不依赖任何附属 ID，也不调用附属的私有清理方法。

## 总上限

Player.academyMaxCp 单独保存本体成长值，区别于附属可以改写的 cpData.maxCP。刷新成长时按已发放加成的新增差额更新记录；切回本体分类、登录和本体分类每次 CP tick 时将可变视图校正到该记录。新记录的 setter 只允许本体来源调用，保存后跨重启保留。

首次迁移采用现有总上限与 `100 + 已发放加成` 中较大的值。这样可以修复例如总上限被覆盖为 400、已发放加成为 500 的旧存档，使其恢复至 600，而不会重发同一份加成。有记录的历史额外成长也会保留。若在修复版本安装前已经丢失了没有其他记录的额外上限，无法从现有数据完整还原；原存档备份仍是最准确的依据。

正常成长和修复上限只补偿上限差额，保留现有消耗。调试命令的临时有效上限继续生效，不写入持久化成长值。分类切换原有的释放全部占用并回满 CP 的语义保持不变。

## 占用与恢复

本体分类的 CP tick 会移除已卸载/未注册技能、不适用于当前分类的技能以及数值异常的占用；对有效金额退款。合法的当前分类占用、通用技能占用继续保留，正常计时占用仍需迭代并支付 SP。

原有 processOccupations 和 tickOverload 方法作为附属分类的扩展入口保留，签名不变；本体分类直接调用共用恢复实现，避免附属在旧入口的取消型注入把其恢复禁用状态带入本体分类。过载仍按本体时长结束、回满 CP 并触发 AbilityRecoveryEvent。

这覆盖已验证的附属对可变上限字段、processOccupations 和 tickOverload 的修改。任意第三方 Mixin 仍能修改其他本体代码，因此不能承诺抵御任何形式的字节码改写。

## 回归验证

- NativeCpRecoveryTest：旧存档修复不重发、额外成长跨保存重启、附属反复覆盖时记录新增成长、拒绝后续可变视图膨胀、旧格式迁移、调试上限、合法/非法占用混合与异常金额。
- NativeCpRecoveryGameTests：实际分类切换后恢复 600 CP；仅退款不兼容占用并按正常 SP 成本恢复有效计时占用；过载结束恢复 CP。
- 同一组 GameTest 支持安装真实 Shadow Master / Alice Expansion。测试使用 NeoForge 的 configureMockConnection 完成模拟连接配置；可选附属仅在测试夹具中通过反射触发耗尽状态，生产代码没有该依赖。
- 附属测试临时使用原始 shadowmaster-26.2.0-1.0.30.jar 和 AcademyCraft-Reborn-Alice-Expansion-26.2.0-0.0.2.jar 的副本，运行后删除副本；不修改用户客户端、附属 JAR 或原存档。

验证命令：

```powershell
.\gradlew.bat test -DisDev=true
.\gradlew.bat runGameTestServer -DisDev=true '-PacademyGameTests=academy:native_cp_*'
.\gradlew.bat runGameTestServer -DisDev=true '-PacademyGameTests=academy:darkmatter_resource_*'
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
```

验证结果（2026-09-06）：完整 JUnit 1686 项通过；本体 CP GameTest 3 项通过；加载两个用户指定原始附属后同样 3 项通过；未元物质资源 GameTest 17 项通过；开发版与发布版 build 均通过。runClientDev 在独立 run/native-cp-client-smoke 目录完成资源、音频和模组加载；客户端检查仅覆盖启动，玩法行为由上述服务器 GameTest 验证。
