# 禁止过度 `null` 传递

本文定义 `null` 的传递规则。核心要求：**`null` 只能是极少数位置上的受控契约，不能成为贯穿调用链的默认“无值”通道。**

## 1. 什么叫“过度 `null` 传递”

以下都属于过度传递，禁止：

- 一个可空参数被原样传进下一层，再传进下一层，中途无人消费、无人判断；
- 一个方法返回 `@Nullable`，其调用者不加处理直接把结果继续往上传；
- 用 `null` 同时表示多种含义（“不存在”“未初始化”“计算失败”“被禁用”），调用方无法区分；
- 为了少写一个重载 / 一个配置对象，到处加“可有可无”的可空参数；
- 在集合、字符串、数组位置用 `null` 表示“空”。

只要一个可空值跨越了两层以上仍未被处理，就应停下来重构，而不是继续传播。

## 2. 硬性规则

### 2.1 可空值就近收敛

可空值必须在**产生它的那一层**被处理（判空、转换、回退）或显式做出业务决策，不得沿着调用链向下或向上扩散。

```java
// 反例：null 一路穿透三层
@Nullable Config loadConfig(...);           // 第 1 层，可能返回 null
void applyConfig(@Nullable Config c) { ... } // 第 2 层，原样接收
void applyTo(@Nullable Config c) { ... }     // 第 3 层，继续原样下传
```

```java
// 正例：在产生处收敛为具体类型 / 默认值 / Optional
Config config = loadConfig();                 // 返回非空，内部完成默认值处理
if (config == null) config = Config.DEFAULT;  // 或返回 Optional<Config> 由调用方显式处理
```

### 2.2 非空参数在边界快速失败

确定应当非空的入参，在方法开头用 `Objects.requireNonNull` 明确失败，不要把 `null` 继续带下去：

```java
public void register(Skill skill) {
    Objects.requireNonNull(skill, "skill");
    ...
}
```

这比默默接受 `null`、在深处再抛 `NullPointerException` 更容易定位问题。

### 2.3 返回可空必须让调用方立刻处理

方法返回 `@Nullable` 时，调用方必须在这一处做出决策：判空、提供默认、转 `Optional`、或抛出明确异常。禁止直接转手继续返回。

```java
// 反例
@Nullable Entity findTarget() { return scanForTarget(); }     // 只是转发

// 正例：要么给默认，要么让缺席成为显式结果
Optional<Entity> findTarget() { return Optional.ofNullable(scanForTarget()); }
```

### 2.4 集合/字符串用空实例，不用 `null`

```java
// 反例
@Nullable List<String> keys;
String name = null;

// 正例
List<String> keys = List.of();
String name = "";
```

### 2.5 可空参数要少而明确

方法参数中 `@Nullable` 至多用于**语义清晰的可选依赖**（如可选的 `breaker`、可选的 `level`），并在 Javadoc 中写明“为 `null` 时表示什么”。若一个方法出现两个以上可空参数，优先改为：

- 拆成重载：`foo(x)` 与 `foo(x, opt)`；
- 或传入一个配置/上下文对象，用非空字段 + 明确的“未设置”表示；
- 或把可选性上移到返回值/结果类型（如 `Optional`、密封结果）。

`LevelUtil` 里 `destroyBlocksAlongPath(...)` 的多层重载是允许的形态：外层重载只负责补默认值（`null`），**实际实现只在最内层接收一次可空参数**，不在内部继续向下扩散。

### 2.6 不用 `null` 区分多种状态

若需要表达“不存在 / 未初始化 / 失败”等多种情况，用显式类型，不要用不同含义的 `null`：

```java
// 反例：null 既可能是“没有”，也可能是“禁用”
@Nullable Config config;

// 正例：状态显式化
sealed interface ConfigState permits Absent, Disabled, Present { ... }
```

## 3. 允许使用 `null` 的场景

以下场景使用 `@Nullable` 是合理的，但仍受“就近收敛”约束：

- 与 Minecraft / NeoForge 原版 API 对接：原版大量方法以 `null` 表达可选参数或可空返回，包装层需照常接收，并在包装层内收敛或转换为本仓库的契约；
- JVM 生命周期导致的“可能尚未初始化”的字段（如懒加载缓存），且提供非空访问入口；
- 可选的协作者 / 调用上下文（如可选的 `ServerPlayer breaker`）。

## 4. 评审清单

提交前逐条自查：

1. 我新增的包有 `@NullMarked` 的 `package-info.java` 吗？
2. 我加的 `@Nullable` 都是 `org.jspecify.annotations.Nullable` 吗？
3. 每个 `@Nullable` 位置都能一句话说清“何时为 `null`”吗？
4. 这个可空值跨越了几层？是否已在该处理的地方处理？
5. 有没有把集合/字符串的“空”写成 `null`？
6. 非空入参是否在边界 `Objects.requireNonNull`？
7. 有没有用 `null` 承担多种含义？

任一为“否/不确定”，先修再提交。
