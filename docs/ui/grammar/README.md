# UI 界面代码书写规范（grammar）

本目录规范UI代码的编写：如何用 Kotlin DSL 组织界面树、如何书写代码块、如何声明常量与跨块引用。

## 权威示例

唯一的最佳规范示例是 `org.academy.api.client.gui.screen.ContainerUiScreen` 的 `init` 方法，本目录所有规则以它为准。

除 `ContainerUiScreen` 外，仓库中其它界面实现多多少少都有不规范之处，**一律视为错误，不得作为参考**。需要参照写法时只看 `ContainerUiScreen`。

## 文档索引

| 文档                                     | 内容                                                         |
|------------------------------------------|--------------------------------------------------------------|
| [tree_structure.md](./tree_structure.md) | `apply` 省略主语、代码块即树级、命名、代码块内顺序与空行分隔 |
| [layout_params.md](./layout_params.md)   | `lp` 与 `LayoutParams` 的使用规则                            |
| [declarations.md](./declarations.md)     | 声明区：局部常量、`R.ui` 常量、`lateinit` 跨块引用           |

## 总则

1. 一棵界面树包在 `apply { ... }` 中构建，块内省略主语。
2. 每个 DSL builder 代码块对应一层树级，缩进即层级。
3. 代码块内顺序固定：`lp` → 空行 → widget 属性 → 空行 → 子块/语句。
4. 相邻的独立语句之间用空行分隔。
5. 常量：少量就地 `val`，较多集中到 `R.ui` 并按界面细分。
6. 需要在块外使用的 widget 引用，用 `lateinit var` 在块顶声明。
7. 名称与英语复合词相反，应使用后置修饰，如 `page_settings / pageSetting` 而非 `settings_page/settingsPage` （仅ui界面代码，其他代码命名则应遵循复合词，如 TerminalHud 才是正确的）。
