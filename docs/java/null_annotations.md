# 空值声明：`package-info.java`、`@NullMarked`、`@Nullable`

本文定义空值可空性的声明方式。所有规则以 JSpecify 注解为准。

## 1. `@NullMarked` 是默认基线

JSpecify 的 `@NullMarked` 表示：**该作用域内所有未显式标注的类型默认非空（non-null）**。因此它不是可选项，而是整个包的空值基线。

未标注 `@NullMarked` 的包处于“未指定（unspecified）”状态，既不是非空也不是可空，本仓库视为不合规。

## 2. 每个包必须有 `package-info.java`

每个含 Java 源码的包（`src/main/java` 与 `src/test/java`）都必须有一个 `package-info.java`，内容固定为：

```java
@NullMarked
package org.academy.internal.server.storage;

import org.jspecify.annotations.NullMarked;
```

要点：

- `@NullMarked` 标在 `package` 声明上，而不是任意类上。
- import 必须是 `org.jspecify.annotations.NullMarked`。
- `package-info.java` 里除注解与 `package` 声明外不要放其它内容。
- 新增包缺少该文件时，运行 `./gradlew generateMissingPackageInfo`（Gradle 任务 `generateMissingPackageInfo`，group `academy`）自动补齐。**不要手工手写格式不一致的文件**；该任务会为每个“含 `.java` 文件但没有 `package-info.java`”的目录生成上述模板。

## 3. 可空位置使用 `@Nullable`

在 `@NullMarked` 基线下，只有确实可能为 `null` 的位置才加 `@Nullable`，且统一使用：

```java
import org.jspecify.annotations.Nullable;
```

禁止使用其它来源的同名注解：

| 禁止                                            | 原因                                   |
|--------------------------------------------------|----------------------------------------|
| `javax.annotation.Nullable`                      | JSR-305，语义与本仓库基线不一致        |
| `org.jetbrains.annotations.Nullable`             | IntelliJ 专用，不是 JSpecify 契约      |
| `androidx.annotation.Nullable` 等第三方注解      | 同上，且不参与 JSpecify 类型系统       |

## 4. `@Nullable` 是类型注解（type-use）

`@Nullable` 修饰的是**类型**，写在类型前、泛型参数内或数组维度上，而不是当作“修饰符”散落：

```java
// 正确：字段类型可空
private final @Nullable AbilityFactorProfile developmentProfile;

// 正确：参数类型可空
public static boolean canBreakBlock(BlockState state, @Nullable BlockGetter level, @Nullable BlockPos pos, int miningLevel)

// 正确：返回值类型可空
public @Nullable BlockEntity findBlockEntity(BlockPos pos)

// 正确：泛型实参可空
Map<Identifier, @Nullable Skill> overrides
```

```java
// 错误：把 @Nullable 当成声明修饰符写在方法/字段最前，语义含糊
@Nullable private AbilityFactorProfile developmentProfile;   // 不要这样

// 错误：类型不可空的容器却用裸 null 表示“无”
List<Skill> skills = null;                                    // 用空列表
```

## 5. `@Nullable` 的判定

对每个引用类型位置问一句：

> 这个位置**正常运行时**是否可能为 `null`？

- 不可能：不加注解，保持 `@NullMarked` 的默认非空。
- 可能：加 `@Nullable`，并在 Javadoc 里说明**什么时候**为 `null`。

不要为了“防御性”给永远非空的位置加 `@Nullable`——那会把非空契约降级，迫使所有调用方都做无意义的判空。

## 6. 与 `Optional` 的关系

`@Nullable` 描述“值可能缺失”；`Optional` 描述“可选返回”。二者分工：

- **返回值**表达“可能没有结果”时，优先 `Optional<T>`，不要再叠加 `@Nullable`（`@Nullable Optional<T>` 无意义）。
- **字段 / 参数**内部状态用 `@Nullable` 即可；对外暴露时可用 `Optional.ofNullable(...)` 转换：

```java
private final @Nullable AbilityFactorProfile developmentProfile;

public final Optional<AbilityFactorProfile> getDevelopmentProfile() {
    return Optional.ofNullable(developmentProfile);
}
```

- 集合类型始终返回空集合，不用 `Optional<List<T>>`，也不用 `@Nullable List<T>` 表示“没有元素”。

## 7. 编译期能力边界

当前仓库通过 JSpecify 注解表达契约，但**未接入 NullAway / Error Prone 等强制空值检查器**，注解不会在编译期报错。因此：

- 注解是契约，靠代码评审与本文档保证；
- 不要依赖“编译器会拦住”来省略边界判空；
- 引入检查器属于独立决策，不在本文范围内。
