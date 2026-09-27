# `@EventBusSubscriber` 不写 `modid`

## 1. 结论

本项目所有 `@EventBusSubscriber` 一律**不写 `modid`**。

NeoForge 的 `EventBusSubscriber.modid` 有默认值 `""`。当不写时，框架会根据被标注类所属的 mod 容器自动推断 mod id，行为与显式声明一致；显式写 `modid` 属于冗余信息。

## 2. 写法

| 场景 | 写法 |
|------|------|
| 服务端 / 双端 | `@EventBusSubscriber` |
| 仅客户端 | `@EventBusSubscriber(Dist.CLIENT)` |

需要同时指定总线时可保留其它参数，但同样不要写 `modid`。

## 3. 为什么

- **冗余**：mod id 由类所属的 mod 容器决定，框架会自动推断，手写只是重复。
- **易产生不一致**：历史代码里同时出现过 `modid = AcademyCraft.MOD_ID` 与硬编码 `modid = "academy"` 两种写法，指向同一值却风格不统一。
- **无行为差异**：省略后订阅者仍注册在同一事件总线上，`Dist` 过滤等语义不变。
- **更清洁**：移除后注解只保留真正随语义变化的参数。

## 4. 适用范围

适用于 `mod/`、`editor/`、`hack/` 的全部 Java 源码。

其中 `editor/` 自身是独立 mod 容器（`academy_editor`）。省略 `modid` 后，其订阅者归属为 `academy_editor`（而不再绑定 `academy`），这是预期行为。

Kotlin 源文件同样遵循本约定（Kotlin 侧一直如此）。

## 5. 例外

若确有订阅者需要显式绑定到与其类所属容器不同的 mod（当前仓库不存在此类需求），必须在代码注释与本文档中写明理由。默认禁止，不要以“保险”为由随手加 `modid`。

## 6. 反例与正例

```java
// 反例
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SomeServerEvents { }

@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class SomeClientEvents { }
```

```java
// 正例
@EventBusSubscriber
public final class SomeServerEvents { }

@EventBusSubscriber(Dist.CLIENT)
public final class SomeClientEvents { }
```
