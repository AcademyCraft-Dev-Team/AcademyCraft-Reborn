# widget 归属

界面用到的自定义 widget，按“是否通用、是否可复用”决定放置位置：

1. **通用且可复用**的 widget 放在 `org.academy.api.client.gui.widget`
   （如 `ImageWidget`、`ProgressBarWidget`、`CircleImageWidget`）。
2. **仅服务某个界面**的 widget，作为该界面类的**内部类**
   （`private class`，需要访问界面状态时用 `private inner class`）就地声明，与界面代码同文件。
3. **不要**新建 `org.academy.internal.client.gui.widget` 之类的包来放界面专用 widget；
   该包不存在，也应避免创建。

判断依据：需要被其它界面复用 → 第 1 种；只被本界面使用 → 第 2 种。

示例（界面专用，作内部类）：

```kotlin
class SomeScreen : UiScreen(Component.empty()) {
    // ...

    private class SkillIconWidget(
        private val iconTexture: Identifier,
        private val progressProvider: () -> Float
    ) : AbstractWidget() {
        override fun renderInternal(context: Canvas) {
            // ...
        }
    }
}
```
