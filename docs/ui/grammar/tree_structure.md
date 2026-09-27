# 界面树与代码块结构

> 参照 `ContainerUiScreen.init`。除它以外的实现视为反例。

## 1. 用 `apply` 省略主语

界面树的构建应包在根容器的 `apply { ... }` 代码块中（`ContainerUiScreen` 中是 `root.apply { ... }`），块内不再重复书写主语：

```kotlin
root.apply {
    clearChildren()

    row("main") {
        // ...
    }
}
```

多个使用推荐统一 `apply`，不要逐句重复 `root.`。

## 2. 代码块即树级

DSL builder（`row` / `column` / `frame` / `anchor` / `radioGroup` / `image` / `blendQuad` / `add` / `replace` …）以代码块的形式向当前容器添加子 widget。

- 一个 builder 代码块 = 一层树级；
- 嵌套缩进直接对应界面层级；
- 界面结构靠代码块嵌套表达，而不是把子 widget 平铺后用坐标硬摆。

## 3. 名称可省略

builder 的名称参数用于后续按名查找（`children["..."]` / `removeChild("...")` / `replaceChild("...")`）：

```kotlin
row("main") { ... }
image("inv", R.textures.gui.element.ui_inventory) { ... }
```

不需要按名查找时可以省略，交给 DSL 生成默认名：

```kotlin
frame { ... }
row { ... }
```

## 4. 代码块内的书写顺序

一个 builder 代码块内的语句按固定顺序排列：

1. `lp { ... }` —— **必须是代码块内的第一行**；
2. 一个空行；
3. widget 自身属性赋值，例如：

```kotlin
orientation = Orientation.VERTICAL
```

4. 一个空行；
5. 子 builder 代码块、`startAnimation(...)`、赋值等其它语句。

## 5. 空行分隔

相邻的独立语句之间用空行分隔，尤其：

- 两个 `startAnimation(...)` 之间；
- `startAnimation(...)` 与紧随其后的子 builder（如 `buttonGroupPage = radioGroup(...)`）之间；
- `lp { ... }` 与其后的属性、子块之间。

## 6. 权威示例

`ContainerUiScreen.init` 的相关片段：

```kotlin
root.apply {
    clearChildren()

    val finalHeight = 187f
    val duration = 600L

    lateinit var buttonGroupPage: RadioGroupWidget
    lateinit var buttonInv: RadioButtonWidget
    lateinit var content: FrameLayoutWidget
    lateinit var pageInv: FrameLayoutWidget

    row("main") {
        lp {
            widthMode(SizeMode.WRAP_CONTENT)
            heightMode(SizeMode.WRAP_CONTENT)
            margin((leftPos - 16).toFloat(), (topPos - 22).toFloat(), 0f, 0f)
        }

        startAnimation(
            ObjectAnimator.ofFloat({ alpha = it }, 0f, 1.0f)
                .setDuration(duration)
                .setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        )

        startAnimation(
            ObjectAnimator.ofFloat({ height = it }, 0f, finalHeight)
                .setDuration(duration)
                .setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        )

        buttonGroupPage = radioGroup("button_group_page") {
            lp {
                width(16f)
                heightMode(SizeMode.WRAP_CONTENT)
            }

            orientation = Orientation.VERTICAL

            buttonInv = add(
                "inv", createButtonPage(R.textures.gui.icon.icon_inv).apply {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(16f)
                    }
                    selectButton(this)
                }
            )
        }

        content = frame("content") {
            // ...
        }
    }

    onInit(buttonGroupPage, buttonInv, content, pageInv)

    if (!isAttached()) dispatchAttached()
}
```

## 7. 反例

- 不用 `apply`，逐句重复 `root.` 之类的主语。
- 属性写在 `lp { ... }` 之前。
- `lp { ... }` 不在代码块第一行。
- 子 builder 与 `startAnimation(...)` 之间、两个 `startAnimation(...)` 之间不留空行。
