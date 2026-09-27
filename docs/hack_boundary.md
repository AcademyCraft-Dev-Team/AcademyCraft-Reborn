# JVM 层 hack 的边界与归属

## 1. 目的

本文定义 AcademyCraft-Reborn 中「hack」的含义，并规定其代码归属。

- 「hack」专指**直接作用于 JVM / 字节码加载层**的机制；
- 所有 hack 代码一律放在 `hack/` 子项目（`academy_hack`），`mod/` 不得包含任何 coremod、java agent 或等价的 JVM 层代码；
- Mixin、access transformer 等**不算 hack**，继续留在 `mod/`。

## 2. 属于 hack 的机制

### coremod

通过 NeoForge 的类处理器接口在游戏 module layer 建立之前改写字节码：

- 实现 `net.neoforged.neoforgespi.transformation.ClassProcessorProvider` / `SimpleClassProcessor`；
- 打包为 `FMLModType=LIBRARY` 的独立 jar；
- 通过 `META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessorProvider` 注册。

### java agent

通过 `java.lang.instrument` 在 JVM 启动或附加时拦截类加载：

- `premain` / `agentmain` 入口；
- `Instrumentation` + `ClassFileTransformer`；
- 以 `-javaagent:` 加载的 agent jar（`Premain-Class` / `Agent-Class` 清单属性）。

### 其它 JVM 层手段

- 直接操纵 class / klass、`Unsafe`、原始内存等绕过常规类加载的手段；
- 为放宽校验而修改 JVM 启动参数，例如 `-XX:+AllowEnhancedClassRedefinition`、`-Xverify:none`。这类参数属于 run 配置而非 mod 代码，无需迁移，但按本约定同样归入 hack 范畴。

## 3. 不属于 hack 的机制

以下机制由 NeoForge / SpongePowered 的官方扩展点在受支持的加载阶段生效，属于普通 mod 能力，**不算 hack**：

- Mixin：`@Mixin`、`@Inject`、`@Redirect`、`@ModifyVariable` 等，以及 `*.mixins.json`、`IMixinConfigPlugin`；
- Mixin accessor / invoker：`@Accessor`、`@Invoker`；
- access transformer / access widener（`*.at` / `*.accesswidener`）与 interface injection（`interface_injections.json`）。

这些代码继续留在 `mod/` 的常规源码与资源中，不进入 `hack/`。

## 4. 判定方法

对一段新机制依次提问：

> 1. 它是否在游戏 module layer 建立**之前**、绕过 Mixin，直接改写字节码？（→ coremod）
> 2. 它是否走 `java.lang.instrument`、由 `-javaagent:` 或 agent 入口加载？（→ java agent）
> 3. 它是否直接操纵 class / klass / 原始内存，或依赖放宽校验的 JVM 启动参数？（→ 其它 JVM 层手段）
> 4. 以上皆否，而是通过官方扩展点（Mixin、AT、interface injection 等）参与加载？（→ 不是 hack）

只要命中 1–3 中任一条，即为 hack，代码必须位于 `hack/`。

| 机制 | 归类 | 说明 |
|------|------|------|
| coremod（`ClassProcessorProvider`，`FMLModType=LIBRARY`） | hack | 在 module layer 建立前改写字节码 |
| java agent（`premain` / `agentmain` / `Instrumentation`） | hack | JVM 级类加载拦截 |
| 直接操纵 class / `Unsafe` / 放宽校验的启动参数 | hack | JVM 层手段，代码放 `hack/`（启动参数仅登记） |
| Mixin（`@Mixin` / `@Inject` / `@Accessor` / `@Invoker` / `*.mixins.json`） | 非 hack | 保留在 `mod/` |
| access transformer / access widener / interface injection | 非 hack | 保留在 `mod/` |

## 5. 反例与正例

```text
// 反例：在 mod 中新增 coremod 处理器
mod/src/coremod/java/org/academy/internal/coremod/transform/SomeTransformer.java

// 反例：把 agent 源集挂到 mod 的构建里
mod/build.gradle.kts  ->  javaagent / premain 相关配置

// 正例：hack 代码一律位于 hack 子项目
hack/src/coremod/java/org/academy/hack/coremod/transform/SomeTransformer.java
hack/src/agent/java/org/academy/hack/agent/SomeAgent.java        // 未来扩展位置
```

```java
// 反例：JVM 层注入放在主 mod
package org.academy.internal.coremod.transform;

// 正例：JVM 层注入归 hack 包
package org.academy.hack.coremod.transform;
```

## 6. 与其它约定文档的关系

`docs/api_internal_boundary.md` 中「hack」一词指**越过 `api` / `internal` 边界的包依赖违规**，即外部代码强行使用 `internal`。那是关于依赖方向与使用面合规性的约定，与本文的「JVM 层 hack」是两回事。

两者互不替代：一段代码可以是 JVM 层 hack（归本文管辖），同时在其包归属上完全合规；反之亦然。

## 7. 当前实现

- coremod 位于 `hack/src/coremod/`，包名 `org.academy.hack.coremod.transform`，编译为 `FMLModType=LIBRARY` 的嵌套 jar（`Automatic-Module-Name=academy.hack.coremod`）后并入 `academy_hack`。
- 处理器命名空间统一使用 `academy_hack`。
- java agent 目前无实现，`hack/` 为其预留位置。
- **注意**：分离后 `academy_hack` 成为独立附加 mod，需与 `academy` 一同安装。仅安装 `academy` 时，coremod 提供的健康读取保护、temporal boundary、WorldWeaver 兼容等注入不会生效。

## 8. 一句话总结

coremod、java agent 及其它直接作用于 JVM / 字节码加载层的机制是 hack，其代码一律放 `hack/`；Mixin、access transformer、interface injection 不是 hack，留在 `mod/`。
