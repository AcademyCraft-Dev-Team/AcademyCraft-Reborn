# 退出旧世界后创建新世界崩溃：矢量反射生命周期

## 证据与根因

用户报告 `run/crash-reports/crash-2026-10-20_20.56.10-server.txt`。报告为 `Ticking player`：
`VectorReflection.Server.clearProtection → VectorReflectionRuntime.deactivate → closeTemporalImmunity → TemporalRuntime.Lease.close` 抛出 `Temporal state may only change on the server thread.`。

同次 `run/logs/latest.log` 记录了完整前因：

1. 20:55:56 退出 `test`，`AcademyCraftServer.onServerStopping` 已停止时间系统。
2. 随后的 `PlayerList.remove` 注入调用 `clearProtection`，却进入维护分支，重新申请时间豁免，抛出 `Temporal runtime has stopped.`。原版停止流程中断，随后还出现原世界文件锁冲突。
3. 20:56:07 启动 `test2`，同一玩家 UUID 加入。反射运行时静态表仍保存旧服务器的豁免句柄；新服务器 tick 关闭旧句柄时触发服务器线程归属检查。

线程检查本身正确：两个集成服务器的线程名称都叫 `Server thread`，但并非同一个线程。

状态残留有两个具体入口问题。Mixin 注入后的方法属于 Minecraft 的目标类，其 CodeSource 也是 Minecraft。原先从 `MinecraftServer.halt` 调用的反射 `shutdown` 被“仅 Academy 调用者”检查忽略，而且 halt 可能由客户端线程调用；从 `PlayerList.remove` 直接调用 `clearProtection` 同样不受信任，技能开启时反而会维持保护。

## 修复

- `AcademyCraftServer` 独立持有一个 `VectorReflectionRuntime`。玩家锚点按服务器实例隔离，不再使用跨世界的静态 UUID 表；tick 显式传入所属服务器。
- 在 `ServerStoppingEvent` 的 Academy 代码中，先关闭反射持有的生存保护和时间豁免，再停止相关服务。停止状态先置位，退出过程中迟到的维护调用不会重新申请句柄；重复停止无副作用。
- 玩家退出的反射清理由 `AbilitySystemServer.onPlayerLogout` 执行，调用者归属正确，随后执行原有退出清理。
- 同服务器内更换玩家实例、弱引用消失时，也成对关闭两种句柄。
- 保留时间系统的线程归属和句柄所有者检查，没有吞掉跨服务器异常，也没有放宽外部调用者权限。

## 回归方法

新增 `ReflectionWorldSwitchClientSmoke`，位于 `src/gametest`，仅显式开启时运行。入口为 `validation/temporal/world_switch.init.gradle`，要求目录为 `run/reflection-world-switch-smoke-*`，预先准备 `saves/test` 副本，并确保 `saves/fresh` 不存在。

同一个客户端 JVM 按顺序：

1. 打开旧世界副本，使用实际玩家启用反射，验证关闭、重新开启和时间豁免。
2. 保持反射开启，调用原版退出流程；检查停止、玩家退出、迟到维护不再申请豁免。
3. 通过原版 `WorldOpenFlows.createFreshLevel` 创建全新世界，验证同 UUID 没有继承旧技能/豁免，且反射可再次启停。
4. 再次退出，重新打开旧世界副本，重复反射验证；最后退出并重新取得世界存储锁。
5. 在新服务器中显式尝试关闭测试保留的旧句柄，确认仍被线程检查拒绝。

构建使用 JBR 25 和仓库 wrapper。原生桥构建参数沿用本机 `build/native-physics-p16-env.ps1` 中的固定输入：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat test build -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
    -I validation/temporal/world_switch.init.gradle `
    -PacademyReflectionWorldSwitchDirectory=run/reflection-world-switch-smoke-r1 --console=plain
./gradlew.bat build -DisDev=false @physicsNativeArgs --console=plain
```

## 验证结果

- `test build -DisDev=true` 成功，主测试 2,830、编辑器测试 104、Sable 兼容测试 16，均无失败/错误/跳过。日志：`build/world-switch-dev.log`。
- `build -DisDev=false` 成功，含测试与打包门禁。日志：`build/world-switch-release.log`。
- `runClientDev` 在 `run/reflection-world-switch-smoke-r1` 完成同 JVM 回归，正常退出，Gradle 返回成功。21:09:24 / 21:09:30 / 21:09:35 分别完成旧世界、新建世界、重进旧世界的技能与豁免检查；三次玩家 UUID 均为 `380df991-f603-344c-a090-369bad2a924a`。
- 最终标记 `REFLECTION_WORLD_SWITCH_SMOKE_PASSED`：`stops=3`、`logouts=3`，新世界创建、旧世界重进、技能启停/重新获取豁免、停止后的维护空操作、旧句柄线程拒绝和存储锁释放全部通过。证据：`build/world-switch-smoke-r1.log` 及隔离目录 `logs/latest.log`。
- 回归日志中没有 `Temporal runtime has stopped`、`Temporal state may only change`、`Exception stopping the server` 或 `OverlappingFileLockException`。Windows 性能计数器的既有 OSHI 启动报错仍存在，不影响本项验收。
- 原始 `run/saves/test`、`test2` 合计 227 个文件（不含会话锁）的 SHA-256 在测试前后全部相同。证据：`build/world-switch-source-before.json`、`build/world-switch-source-check.json`。没有删除世界、修剪结构或修改原始存档。

本轮验证范围为本机开发客户端、无外置 Sable 的集成服务器生命周期。没有把此结果扩大为所有第三方模组组合、多人服务器重连或先前结构性能问题的全面验收。生产修改及验证源码均未创建 Git 提交。
