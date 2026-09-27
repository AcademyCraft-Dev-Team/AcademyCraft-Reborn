# 布局参数（`lp`）规范

## 规则

1. `lp { ... }` 必须写在 builder 代码块内的**第一行**。
2. 布局参数（`LayoutParams`）的读写必须收在 `lp { ... }` 内。
3. 不要在 `lp` 之外直接操作 `LayoutParams`。

```kotlin
// 错：LayoutParams 的使用落在 lp 之外
learnBtn.layoutParams.gravity(Gravity.CENTER_HORIZONTAL)
```

```kotlin
// 对：收敛进 lp 代码块
learnBtn.lp {
    gravity(Gravity.CENTER_HORIZONTAL)
}
```

4. `lp` 的 lambda 接收者是 `LayoutParams`，块内直接调用其方法：

```kotlin
lp {
    widthMode(SizeMode.WRAP_CONTENT)
    heightMode(SizeMode.WRAP_CONTENT)
    margin((leftPos - 16).toFloat(), (topPos - 22).toFloat(), 0f, 0f)
}
```

## 为什么

`lp { ... }` 是布局参数统一的书写入口：它在需要时创建 `LayoutParams` 并把参数写入其中。把参数散落在块外直接改 `layoutParams`，会破坏代码块结构的一致性与可读性。

## 说明

仓库中部分实现把 `gravity(...)` / `size(...)` / `margin(...)` 之类的简写 helper 直接写在代码块里，这不是本规范的写法。规范写法是显式 `lp { ... }` 代码块，见 `ContainerUiScreen.init`。
