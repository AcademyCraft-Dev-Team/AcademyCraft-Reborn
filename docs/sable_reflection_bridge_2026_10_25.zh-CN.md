# Sable 无构建依赖联动

## 范围

当前 Sable 为我方非官方移植，仅用于本地学习与验证，不提供公开下载或随 Academy 分发。普通 `test`、开发版／发布版 `build` 不再要求 `create-fork`、Sable 或 Companion JAR；安装对应模组的游戏仍可使用既有联动。

本次不放宽运行时版本／指纹门禁。未知实现仍明确拒绝进入未经验证的联动路径，尤其不猜测存档格式与私有恢复字段。无 Sable 时继续使用本地物理后端；安装 Sable 后沿用原有后端选择规则。

## 实现契约

- `sable/reflect/api` 中是 Academy 自己的接口视图，仅声明所访问的接口和运行时名称，不包含第三方实现、JAR、JNI 或第三方包名下的替身类。
- `SableReflection` 延迟解析运行时类型并缓存成员及类型映射。普通技能、查询、捕获、物理子步、旋转与自动归块共用该边界。热调用不重复扫描目标类的方法列表。
- 包装对象用后端实例身份进行弱引用缓存。只要某个视图仍被控制会话引用，同一实例就得到同一视图；不会将相同 UUID 的新实例视为旧激活，也不会让缓存永久保留世界。
- 存档所有权集合使用原集合的实时视图，增删转换回后端对象。反射调用保留原始异常；持久化的 `IOException` 继续交给原事务恢复逻辑，不能用空结果掩盖失败。
- 外部观察器通过反向接口代理接入；物理事件按实际运行时事件 Class 注册，保留原事件优先级。没有增加额外 tick 或改变冲量预算。
- Mixin 使用字符串目标及 `@Coerce Object` 参数。关节索引仍保存实际原对象，手杖独占判断仍比较真实结构实例。原类型化存档 Invoker／Accessor 已由缓存反射替代。
- 普通单元测试不加载可选模组；增加身份、集合写回、回调、异常传播、缺少 Sable 时加载，以及桥／Mixin 字节码无硬类型链接检查。变换测试使用独立的 JOML 姿态夹具；真实后端通过本地游戏夹具验证。

## 构建与示例任务

`verifySableCompatArtifacts` 及其对所有 Java/Kotlin/Javadoc 任务的强制依赖已移除。`verifySableCompatPackaging` 和独立的归档拒绝用例保留，防止桥被遗漏或误打包外部库。

`verifyApiExample` 是 `build.gradle.kts` 内的任务定义，不能通过 `.gitignore` 单独忽略。已删除该任务、`check` 依赖、示例 source set、示例 JAR 任务及示例运行注册；此前被忽略的本地 `examples/` 文件保留。

普通构建（即使以下本地依赖路径不存在也应通过）：

```powershell
.\gradlew.bat test build -DisDev=true -PacademySableFork=build/absent-private-fork -PacademySableMaven=build/absent-private-maven
.\gradlew.bat build -DisDev=false -PacademySableFork=build/absent-private-fork -PacademySableMaven=build/absent-private-maven
```

本地实机验证可以显式启用 `-PacademySableRuntime=sable|suite`。`docs/sable_compat_artifacts.json` 仅用于核对本地私有测试成品，`verifySableCompatRuntime` 在启动该类测试前检查 SHA 和嵌入依赖。普通构建不会执行该任务，也不会获取这些文件。

## 验证记录

本轮验证结果与复现命令见 `validation/native_structure/sable_reflection_validation_2026_10_25.json`。尚未完成的项目必须标记为未验证；本地结果不等同于 GitHub Actions 已重新通过。

2026-10-25 本轮结果：

- 普通单元测试 3239 项、编辑器测试 104 项通过；开发版与发布版均在指定不存在的私有依赖目录时构建成功。
- 25 项实机用例通过：无 Sable 与 Sable＋航空学环境各六组完整 payload 往返，组合环境和纯 Sable 各三个默认技能入口及未标记结构控制，组合环境三个实际节点 VM，真实关节保护与解除后归块，以及一次归块 COMMIT 后强制退出／重启恢复。
- 恢复验证中源内容副本为 0、世界目标副本为 1，连续 40 tick 保持完整含水箱子 payload。记录中隔离中间窗口观察次数为 0，不宣称本次看到了该窗口；强制退出不是断电或文件系统损坏测试。
- 上述验证完成时尚未提交或推送。该验证快照不包含 GitHub Actions 的 Linux 干净环境、多人长期运行、未知后端版本、实际手杖竞争操作及反射前后吞吐量比较。完整边界见 JSON 的 `unverified`。
