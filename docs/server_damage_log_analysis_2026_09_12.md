# 2026-09-12 服务端伤害日志排查

## 输入与结论

日志：`C:\Users\Dusk\Downloads\latest (5).log`；模组清单及咒术字节码：`D:\MC\ACserver1.1\mods`。日志运行的是 Minecraft 26.2、NeoForge 26.2.0.70、Academy 0.0.4-alpha。仓库也使用 NeoForge 26.2.0.70。

| 日志 | 数量 | 核实结果 |
| --- | ---: | --- |
| Direct actuallyHurt failed | 231 | 226 条调用来自 AeromanipDisplacementTracker，5 条来自 HighSpeedElectronBeam → LinearAttackExecutor；不能全部归为电子束问题。 |
| Vector Reflection using Mixin fallback | 36 | 原始类型均为 ServerPlayer，写入后的初始类指针验证失败，回退到 Mixin 防护。 |
| invalid or missing user | 3 | 无线节点检测失效连接，执行 SavedData 清理；同一条日志合并了设备缺失、连接不匹配、超距等原因。 |
| recovered from overload | 9 | 正常 INFO，表示能力过载恢复。不是网络异常。 |

## 伤害异常根因与证据

`SkillDamageUtil.applyDirect(..., SkillDamageSource, ...)` 没有校验目标是否已死亡，也没有校验伤害是否为正且有限。`MixinLivingEntity` 在 `hurtServer` 的 HEAD 将技能伤害转到该入口，因此绕过了原版 `hurtServer` 的死亡检查，随后直接调用 `actuallyHurt`。

气流的 `AeromanipTargeting.canAffectNegatively` 只排除已移除实体，未排除死亡动画阶段的生物，位移票据因此还能继续结算。咒术的 `JJKPartEntity.hurtServer` 则将分体命中转发给本体，本体死亡后仍可能收到下一次转发。

NeoForge 在 Pre 返回后检查的是 `!this.dead`，没有记录进入 Pre 之前的死亡状态。对尸体调用 `actuallyHurt` 同样会触发“在 Pre 阶段击杀实体”的报错，不能据此断言某个 Pre 监听器执行了击杀。

官方实现：[NeoForge LivingEntity 补丁](https://github.com/neoforged/NeoForge/blob/26.2.x/patches/net/minecraft/world/entity/LivingEntity.java.patch)。本地咒术字节码显示 `CursedSpirit.actuallyHurt` 先调用父类，再更新仇恨目标；堆栈中出现该方法或 Friends&Foes 的包装方法本身不是其导致异常的证据。

无需安装这些兼容模组，也能在真实 NeoForge GameTest 服务端复现：首次直伤正常击杀牛，在死亡动画移除前经公共直伤入口和 `hurtServer` 各重复命中三次，产生相同的六条异常。新增用例通过计数验证 `actuallyHurt` 是否被再次执行，不会因为原代码捕获异常而误判为通过。

## 修复

- 为技能直伤入口及最终实际扣血入口补充服务器世界一致性、正有限伤害、未移除、存活、原始 `dead` 标记和非自身伤害检查。最终入口仍保留扩展使用的原始方法签名。
- 气流的通用负面作用筛选排除死亡生物。现有位移结算逻辑会据此清除失效票据。
- 保留原有伤害类别、护甲和吸收策略、首次击杀与归属结算；不通过重试普通近战或吞掉更多异常来规避错误。
- 新增真实游戏回归：正常首次击杀、死后反复直伤及转发、非法数值拒绝、伤害容器栈清理、拒绝后正常命中。

## 其余日志的边界

反射日志只能证明初始验证失败；旧日志没有区分原始指针写入验证与 `getClass()` 观察验证，无法据此指定 PlayerRevive/Pehkui 为根因。当前实现已经选择 Mixin 防护。关闭反射会移除该保护状态，再启用时可能重新验证并再次记录 WARN。此次没有削弱验证或强制启用失败的后端。

无线日志对应已加载位置的自校验与断连。代码会从 SavedData 删除连接并标记脏数据，在设备仍指向该节点时清空设备端连接；三个警告不能证明存档持续损坏，也不能反推出当时具体哪一种失效原因。此次没有修改用户存档或屏蔽这些日志。

## 验证

- 修复前 `academy:damage_penetration`：按预期失败，六条与用户日志相同的异常；回归断言确认死亡后仍进入 actuallyHurt。
- 修复后同一 GameTest：通过，零条 Direct actuallyHurt failed。
- JBR 25 + `gradlew test -DisDev=true`：2005 个测试通过，零失败、错误或跳过。
- `gradlew build -DisDev=true` 与 `gradlew build -DisDev=false`：均通过，包含 API 示例校验、编辑器测试及三种 JVM 类指针配置测试。
- 隔离目录中的 `runClientDev -DisDev=true`：完成模组及资源加载；启动冒烟结束后主动关闭该测试进程，没有进行客户端操作验证。
- 发布包：`build/libs/academy-26.2.0-0.0.4-alpha-release.jar`；SHA-256：`B2454FB1EDE6EED49DF28C19828B33F881AF6E9E63640DF06A2E29E79FB941A2`。

本次没有替换用户服务端 mods 中的 JAR，也没有在完整服务端模组组合中做长时间联机验证。构建与补充验证结果随最终交付说明列出。
