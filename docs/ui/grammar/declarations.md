# 声明区：常量与跨块引用

`apply { ... }`（或其它容器 `apply`）代码块的顶部是**声明区**，用于放置本次构建需要的常量与需要跨块使用的引用。声明区的顺序是：

1. 局部常量 `val`；
2. `lateinit var` 跨块引用。

## 1. 少量常量：就地 `val`

数量少、仅本界面使用的数值，就地声明为 `val`，命名用 `lowerCamelCase`：

```kotlin
val finalHeight = 187f
val duration = 600L
```

## 2. 较多常量：集中到 `R.ui`

当数值较多（或需要跨界面复用）时，集中到 `org.academy.api.client.resources.R.ui`，**按界面细分**为嵌套分区，常量名**全小写 + 下划线**：

```java
public static final class ui {
    public static final class container_ui {
        public static final float final_height = 187f;
        public static final long duration = 600L;

        private container_ui() {
        }
    }
}
```

Kotlin 侧引用：

```kotlin
val finalHeight = R.ui.container_ui.final_height
val duration = R.ui.container_ui.duration
```

约定：

- 界面分区名用该界面的名字，全小写 + 下划线，例如 `container_ui`、`solar_gen`；
- 分区内常量名全小写 + 下划线，不加界面前缀（层级已表达归属）；
- `R.ui` 目前为空，本规范确立其结构；新增常量时按此补充。

## 3. 跨块引用：`lateinit var`

需要在代码块上下文之外使用的 widget 引用，在声明区用 `lateinit var` 声明，在对应 builder 内赋值，构建结束后统一使用：

```kotlin
lateinit var buttonGroupPage: RadioGroupWidget
lateinit var buttonInv: RadioButtonWidget
lateinit var content: FrameLayoutWidget
lateinit var pageInv: FrameLayoutWidget

row("main") {
    // ...
    buttonGroupPage = radioGroup("button_group_page") { /* ... */ }
    // ...
}

onInit(buttonGroupPage, buttonInv, content, pageInv)

if (!isAttached()) dispatchAttached()
```

需要跨块使用 widget 时，用声明区的 `lateinit var` 接住，而不是在块外反复用 `children["..."]` 之类的方式查找。
