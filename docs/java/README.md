# Java 空值（null）书写规范

本目录规范 Java 代码中 `null` 的使用：如何声明可空性、如何传递与返回空值、如何避免把 `null` 当作跨层通信的默认手段。

- 本目录适用于 `mod`、`editor`、`hack` 的全部 `src/main/java` 与 `src/test/java`。

## 强制性前提

1. **每个 Java 包必须有 `package-info.java`，并标注 JSpecify 的 `@NullMarked`。**
2. **可空位置一律使用 `org.jspecify.annotations.Nullable`，不得使用其它 `Nullable`（如 `javax.annotation.Nullable`、`org.jetbrains.annotations.Nullable`）。**
3. **禁止把 `null` 当作跨层、跨模块的默认“无值”传递手段**；可空值必须收敛在最小范围内，不得层层贯穿。

## 文档索引

| 文档                                         | 内容                                                         |
|----------------------------------------------|--------------------------------------------------------------|
| [null_annotations.md](./null_annotations.md) | `package-info.java`、`@NullMarked`、`@Nullable` 的声明规则   |
| [null_passing.md](./null_passing.md)         | 禁止过度 `null` 传递：返回、参数、集合、失败处理的约定        |

## 总则

1. 先声明“包默认非空”，再逐点标注可空：`@NullMarked` 是基线，`@Nullable` 是例外。
2. 可空性是**类型契约**，不是注释：能表达为类型（`Optional`、空集合、专用类型）时不要用裸 `null`。
3. 可空值必须在其产生处被处理或显式标注，**不得在调用链中无声地一站站传递**。
4. 对确定非空的输入在边界处快速失败（`Objects.requireNonNull`），而不是把 `null` 继续往里带。
5. 可空参数应当有明确语义并写入 Javadoc；仅为“懒得写重载”而加的可空参数应改为重载或新方法。
6. 集合、数组、字符串等应优先返回空实例而非 `null`。
7. 新增 Java 包若缺少 `package-info.java`，运行 `./gradlew generateMissingPackageInfo` 补齐，不得手写缺失。
