# AcademyCraft 命令参考

本文档覆盖当前代码中注册的全部 `/academy` 命令，包括服务端管理命令和客户端调试命令。命令注册入口分别位于：

- 服务端：`src/main/java/org/academy/internal/server/commands/AcademyCraftCommand.java`
- 客户端：`src/main/java/org/academy/AcademyCraftClient.java`

服务端 `/academy debug` 下的几棵子树由独立类注册：`CPDebugCommands`（`cp`）、`DarkmatterDebugCommands`（`darkmatter`）、`ControlDebugCommands`（`mental_control`）在 `AcademyCraftCommand.java` 内，`TemporalTickDebugCommands`（`tick`，独立文件）；`profile` 在 `AcademyCraftCommand.java` 的 `ClientProfileCommand` 内部类中。改动分支时应同时检查这些位置。

核对基线：2026-10-18，HEAD `782d935b`。

## 阅读约定

- `<参数>` 表示必填参数，`[参数]` 表示可选参数，`a|b` 表示任选其一。
- `<target>` 是单个在线玩家，可使用玩家名或 Minecraft 支持的单玩家选择器。
- `<skill_name>` 和 `<category_name>` 是带命名空间的资源 ID，例如 `academy:vector_reflection`；输入时会提供可用值补全。
- `<broadcast>` 是布尔值，只能填写 `true` 或 `false`。
- “玩家执行”表示命令需要当前执行者是玩家，不能直接由专用服务器控制台或命令方块以自身身份执行。

## 权限和可用环境

除 `/academy props reset` 和将自身能力重置为未开发的 `/academy set_category academy:level0` 外，服务端命令都要求游戏管理员权限（`Commands.LEVEL_GAMEMASTERS`）。`/academy props reset` 无需 OP 权限，但只能由玩家重置自己的 P.R.O.P.S；不能指定其他目标，也不能由服务器控制台执行。带 `<target>` 的管理命令、性能分析命令、矢量兼容命令和 `/academy dev` 可以从服务器控制台执行。

客户端命令在本地执行，不要求服务端管理员权限：

- `/academy debug skillgui ...` 在普通客户端和开发客户端中都会注册。
- `/academy debug textdump`、`atlasdump`、`font` 用于字形与图集调试，见下文“客户端字体调试”。
- `/academy vfx spawn <graph> [<x> <y> <z>]` 在本地生成一个 VFXGraph 实例，见“客户端特效生成”。
- `/academy debug imgui [on|off|toggle]` 在支持 ImGui 的构建中注册（`Dev.HAS_IM_GUI`），用于开关运行时控件检查器。

## 命令总览

```text
/academy
├─ learn_all
├─ learned
├─ learn <skill_name>
├─ set_category <category_name>
├─ level <level>
├─ set_exp
│  ├─ <skill_name> <amount>
│  └─ max
├─ debug
│  ├─ god
│  ├─ vfxstats
│  ├─ cp
│  │  ├─ info [<target> [<broadcast>]]
│  │  ├─ get <target> value|max|curr_sp|max_sp|level|timer|status
│  │  ├─ set <target> <value> [<broadcast>]
│  │  ├─ set_max <target> <value> [<broadcast>]
│  │  └─ set_status <target> <status> [<timer> [<broadcast>]]
│  ├─ darkmatter
│  │  ├─ info [<target>]
│  │  ├─ level <target> <level>
│  │  ├─ phase <target> <alpha_points>
│  │  ├─ mp <target> <natural> <created> <cp_debt> <reserved>
│  │  ├─ gamma <target> <active>
│  │  └─ proficiency <target> <skill_name> <value>
│  ├─ mental_control <target>
│  ├─ tick
│  │  ├─ [<targets>]
│  │  ├─ inspect [entities <targets> | position <dimension> <position>]
│  │  ├─ field add save|dimension|sphere|entities …
│  │  ├─ field list|remove <control_id>|clear
│  │  ├─ immunity add <targets> …|list|remove <control_id>|clear
│  │  ├─ list
│  │  ├─ clear
│  │  └─ help
│  ├─ skillgui [on|off|toggle|reset|export]                 （客户端）
│  ├─ textdump <px> <text>                                 （客户端）
│  ├─ atlasdump [all|bitmap|msdf]                          （客户端）
│  ├─ font [<text>]                                        （客户端）
│  └─ imgui [on|off|toggle]                                （客户端，仅支持 ImGui 的构建）
├─ dev <state>
├─ ability_exp
│  ├─ get [<target>]
│  ├─ set <target> <amount>
│  ├─ add <target> <amount>
│  └─ info [<target>]
├─ props
│  └─ reset
├─ profile
│  ├─ start [<interval_ms>]
│  ├─ stop
│  ├─ reset
│  ├─ status
│  ├─ zones [<thread> [<depth>]]
│  ├─ sampler [<top>]
│  ├─ snapshot
│  └─ dump
└─ vectorcompat
   ├─ inspect
   └─ mode [strict|safe|aggressive]
```

客户端另有独立的 `/academy` 命令树，只影响本机：

```text
/academy
└─ vfx
   └─ spawn <graph> [<x> <y> <z>]                           （客户端，仅开发/特效调试）
```

## P.R.O.P.S

| 命令 | 权限 | 作用 |
| --- | --- | --- |
| `/academy props reset` | 普通玩家可用 | 重置执行者自己的 P.R.O.P.S 因子、锁定项、结构探索记录、里程碑奖励记录和启用状态，并立即撤销相应属性效果。不会重置能力类别、能力等级或已学习技能；重置后需在 P.R.O.P.S 应用中输入 `start` 重新启用。 |

## 能力类别和技能

| 命令 | 执行者 | 参数限制 | 作用 |
| --- | --- | --- | --- |
| `/academy learn_all` | 玩家 | 无 | 学会当前能力类别下的全部可用技能。若该类别没有可学技能，只返回提示。 |
| `/academy learned` | 玩家 | 无 | 列出当前玩家已经学习的全部技能资源 ID。 |
| `/academy learn <skill_name>` | 玩家 | 当前类别中尚未学习的技能 | 学习指定技能。其他能力类别的技能、未知技能和已学习技能会被拒绝。 |
| `/academy set_category <category_name>` | 玩家 | 已注册的能力类别 | 替换当前能力类别；清除原类别的专属技能，同时保留通用技能。 |
| `/academy level <level>` | 玩家 | 整数 `0`～`5` | 将当前玩家的能力等级直接设为指定值。 |
| `/academy set_exp <skill_name> <amount>` | 玩家 | 已学习技能；浮点数 `0`～`3000` | 将指定技能的熟练度直接设为给定值。这里的数值是技能熟练度，不是能力等级经验。 |
| `/academy set_exp max` | 玩家 | 无 | 将执行者当前已掌握的全部技能（含通用技能）的熟练度设为最大值 `3000`；不会学习新技能。没有已掌握技能时只返回提示，失效的技能记录会跳过并报告数量。 |

示例：

```mcfunction
/academy set_category academy:electromaster
/academy learn academy:arc_generate
/academy level 3
/academy set_exp academy:arc_generate 1200
/academy set_exp max
```

## 能力等级经验

这组命令操作用于能力等级成长的经验值，与 `/academy set_exp` 操作的技能熟练度不同。

| 命令 | 参数限制 | 作用 |
| --- | --- | --- |
| `/academy ability_exp get` | 玩家执行 | 查看当前玩家的能力经验。 |
| `/academy ability_exp get <target>` | 单个在线玩家 | 查看目标玩家的能力经验。 |
| `/academy ability_exp set <target> <amount>` | 浮点数，最小为 `0` | 将目标玩家的能力经验直接设为指定值。 |
| `/academy ability_exp add <target> <amount>` | 任意浮点数，可为负数 | 增加或扣除目标玩家的能力经验，并显示操作后的总量。 |
| `/academy ability_exp info` | 玩家执行 | 显示当前玩家的能力等级、经验和当前是否满足升级条件。 |
| `/academy ability_exp info <target>` | 单个在线玩家 | 显示目标玩家的能力等级、经验和当前是否满足升级条件。 |

## 调试模式和 CP

### 通用调试开关

| 命令 | 执行者 | 作用 |
| --- | --- | --- |
| `/academy debug god` | 玩家 | 为当前玩家切换技能 God Mode。该状态按玩家保存，命令每次执行都会在开启和关闭之间切换。 |
| `/academy debug vfxstats` | 玩家或控制台 | 打印技能特效运行时的实时统计：在途特效数、按类型的分布、待发送与已丢弃的包数、被预算裁剪的数量。用于确认特效是否在多人场景下被预算限制。 |
| `/academy dev <state>` | 玩家或控制台 | 开启或关闭服务端能力系统的开发模式。`<state>` 为 `true` 或 `false`。 |

### CP 状态查看

| 命令 | 作用 |
| --- | --- |
| `/academy debug cp info` | 显示当前玩家的 UUID、等级、当前/最大 CP、当前/最大 SP、状态和状态计时器；必须由玩家执行。 |
| `/academy debug cp info <target>` | 显示目标玩家的完整 CP 状态。 |
| `/academy debug cp info <target> <broadcast>` | 与上一条相同；`broadcast=true` 时向全部在线玩家广播结果。 |
| `/academy debug cp get <target> value` | 以命令返回值给出目标的当前 CP，浮点部分会被截断。 |
| `/academy debug cp get <target> max` | 以命令返回值给出目标的最大 CP，浮点部分会被截断。 |
| `/academy debug cp get <target> curr_sp` | 以命令返回值给出目标的当前 SP。 |
| `/academy debug cp get <target> max_sp` | 以命令返回值给出目标的最大 SP。 |
| `/academy debug cp get <target> level` | 以命令返回值给出目标的能力等级。 |
| `/academy debug cp get <target> timer` | 以命令返回值给出目标的状态计时器。 |
| `/academy debug cp get <target> status` | 以命令返回值给出状态序号：`NORMAL=0`、`PERSONAL_REALITY_OVERLOAD=1`、`OVERLOAD=2`。 |

`cp get` 不发送聊天文本，其值是 Brigadier 命令返回值，适合与原版 `/execute store result ...` 或命令方块比较器配合使用。

### CP 状态修改

| 命令 | 参数限制 | 作用 |
| --- | --- | --- |
| `/academy debug cp set <target> <value> [<broadcast>]` | `<value>` 为任意浮点数 | 设置目标的当前可用 CP。省略 `<broadcast>` 时按 `false` 处理。 |
| `/academy debug cp set_max <target> <value> [<broadcast>]` | `<value>` 为不小于 `0` 的浮点数 | 设置目标的最大 CP。省略 `<broadcast>` 时按 `false` 处理。 |
| `/academy debug cp set_status <target> <status>` | 见下方状态列表 | 设置目标状态，并将状态计时器设为 `0`。 |
| `/academy debug cp set_status <target> <status> <timer> [<broadcast>]` | `<timer>` 为不小于 `0` 的整数 | 同时设置目标状态和状态计时器。省略 `<broadcast>` 时按 `false` 处理。 |

`<status>` 支持以下值，输入时会自动补全，大小写均可：

- `NORMAL`
- `PERSONAL_REALITY_OVERLOAD`
- `OVERLOAD`

修改命令的 `<broadcast>` 为 `true` 时会向全部在线玩家广播结果；为 `false` 或省略时使用标准命令反馈。

### 未元物质资源调试

这组命令直接改写目标的未元物质资源池与相态点数，用于复现结算问题。全部要求游戏管理员权限。

| 命令 | 参数限制 | 作用 |
| --- | --- | --- |
| `/academy debug darkmatter info [<target>]` | 省略 `<target>` 时取执行者 | 显示等级、α/β/γ 点数与比例、γ 是否激活，以及 MP 的自然量、创生量、总量、有效容量、基础容量、CP 欠账与预留量。 |
| `/academy debug darkmatter level <target> <level>` | 与 `/academy level` 相同的等级范围 | 设置目标能力等级，并立即重新同步未元物质资源。 |
| `/academy debug darkmatter phase <target> <alpha_points>` | 整数 `0`～`250` | 设置目标的 α 相态点数；服务端拒绝时命令失败并返回 0。 |
| `/academy debug darkmatter mp <target> <natural> <created> <cp_debt> <reserved>` | 四个不小于 `0` 的浮点数 | 一次性设置自然物质、创生物质、CP 欠账与预留物质，随后打印资源概览。 |
| `/academy debug darkmatter gamma <target> <active>` | `true` 或 `false` | 设置 γ 相态开关。 |
| `/academy debug darkmatter proficiency <target> <skill_name> <value>` | 资源 ID；浮点 `0`～`3000` | 设置指定技能熟练度，候选值为目标已学习的技能。 |

### 心理控制调试

| 命令 | 作用 |
| --- | --- |
| `/academy debug mental_control <target>` | 打印目标生物当前生效的全部心理控制：每条控制的控制者 UUID、来源、作用域、优先级、到期 tick 与指令；另外输出工作指令、当前方块、待办量、移动状态、停滞 tick，以及生物寻路是否完成、路径节点数与可达性。命令返回值等于生效控制条目数，可配合 `/execute store result` 做断言。 |

### 时间场调试

`/academy debug tick` 用于在不改代码的前提下验证时停边界、免疫租约与各类时间场的作用范围。

| 命令 | 作用 |
| --- | --- |
| `/academy debug tick` | 等同于 `inspect`，查看命令执行位置所在的时间场。 |
| `/academy debug tick <targets>` | 查看目标实体受到的时间缩放。 |
| `/academy debug tick inspect entities <targets>` | 逐实体列出生效的时间场、通道与暂停来源。 |
| `/academy debug tick inspect position <dimension> <position>` | 查看指定维度坐标处生效的时间场。 |
| `/academy debug tick field add save <scale> [<channels>] [<pause_source>]` | 在整份存档范围施加时间场。 |
| `/academy debug tick field add dimension <dimension> <scale> …` | 在单个维度施加时间场。 |
| `/academy debug tick field add sphere <dimension> <center> <radius> <scale> …` | 在球形区域施加时间场。 |
| `/academy debug tick field add entities <targets> <scale> [<pause_source>]` / `… <scale> channels <channels> [<pause_source>]` | 对指定实体施加时间场。 |
| `/academy debug tick field list` / `field remove <control_id>` / `field clear` | 列出、删除单个或清空全部时间场。 |
| `/academy debug tick immunity add <targets> [<pause_source>]` 等 | 授予/查询/撤销时间场免疫租约，子命令为 `add`、`list`、`remove <control_id>`、`clear`。 |
| `/academy debug tick list` / `clear` / `help` | 列出全部调试控制、清空全部调试控制、打印参数说明。 |

`<scale>` 为 `0`～`TemporalScale.DEFAULT_MAX_SCALE` 的浮点数，`0` 表示完全暂停。`<channels>` 与 `<pause_source>` 均带补全：前者可选 `integrated`、`spatial`、`world`、`all` 或单个通道名，后者可选 `all` 或单个暂停来源名。

## 性能分析

| 命令 | 参数限制 | 作用 |
| --- | --- | --- |
| `/academy profile start` | 无 | 以默认 `1 ms` 采样间隔启动采样器，同时开启性能区段捕获。 |
| `/academy profile start <interval_ms>` | 整数 `1`～`1000` | 以指定毫秒间隔启动采样器，同时开启性能区段捕获。 |
| `/academy profile stop` | 无 | 停止采样器和性能区段捕获，保留已收集的数据。 |
| `/academy profile reset` | 无 | 清空采样数据和性能区段统计。 |
| `/academy profile status` | 无 | 显示采样器是否运行、当前间隔与已收集的样本规模。 |
| `/academy profile zones` | 无 | 按默认线程、默认深度 `8` 打印各区段耗时。 |
| `/academy profile zones <thread>` | 线程名（带补全） | 只打印指定线程的性能区段。 |
| `/academy profile zones <thread> <depth>` | 深度整数 `1`～`20` | 指定线程并限制展开深度。 |
| `/academy profile sampler` | 无 | 打印默认前 `30` 条采样热点。 |
| `/academy profile sampler <top>` | 整数 `1`～`200` | 打印指定条数的采样热点。 |
| `/academy profile snapshot` | 无 | 在命令反馈中显示当前采样概况和各区段最耗时的前十项。 |
| `/academy profile dump` | 无 | 将当前数据写入服务器目录下的 `logs/academy-profile-<时间戳>.txt`。 |

建议先执行 `reset`，再执行 `start`；复现需要分析的场景后执行 `stop`，最后使用 `snapshot` 快速查看或使用 `dump` 保存完整结果。

## 第三方矢量攻击兼容

| 命令 | 作用 |
| --- | --- |
| `/academy vectorcompat inspect` | 显示当前兼容模式、已加载档案数量、最近最多八条外部线性伤害诊断，以及可复制的兼容档案模板。 |
| `/academy vectorcompat mode` | 显示当前兼容模式。 |
| `/academy vectorcompat mode strict` | 切换到 `STRICT` 模式。 |
| `/academy vectorcompat mode safe` | 切换到 `SAFE` 模式；这是默认模式。 |
| `/academy vectorcompat mode aggressive` | 切换到 `AGGRESSIVE` 模式。 |

兼容模式作用于第三方线性伤害的接管策略；`/academy vectorcompat inspect` 会打印当前模式、已加载档案数量、最近的诊断记录以及可复制的兼容档案模板。该机制的档案格式目前没有独立文档，以 `inspect` 输出和 `vectorcompat` 相关配置为准。

## 客户端技能 GUI 布局

这些命令只修改当前客户端的技能树布局调试状态，不要求服务器管理员权限。

| 命令 | 作用 |
| --- | --- |
| `/academy debug skillgui` | 切换技能 GUI 布局编辑模式，效果与 `toggle` 相同。 |
| `/academy debug skillgui on` | 开启技能 GUI 布局编辑模式。 |
| `/academy debug skillgui off` | 关闭技能 GUI 布局编辑模式。 |
| `/academy debug skillgui toggle` | 切换技能 GUI 布局编辑模式。 |
| `/academy debug skillgui reset` | 清除本次客户端会话中的技能坐标修改，恢复内置布局；不会删除已经导出的文件。 |
| `/academy debug skillgui export` | 导出所有能力类别当前使用的技能坐标到 `config/academy/ability_developer_gui_layout.txt`。 |

## 客户端字体调试

这些命令只影响当前客户端，不要求服务器管理员权限，用于检查字形光栅化与图集。

| 命令 | 作用 |
| --- | --- |
| `/academy debug textdump <px> <text>` | 对 `<text>` 中每个码点按 `<px>` 字号走生产位图光栅化路径，逐字形导出 PNG（不量化尺寸、相位 0）。 |
| `/academy debug atlasdump` | 等价于 `atlasdump all`。 |
| `/academy debug atlasdump all` | 导出当前已构建的位图图集页与全部字体的 MSDF 图集页。 |
| `/academy debug atlasdump bitmap` | 只导出共享位图图集页（R8）。 |
| `/academy debug atlasdump msdf` | 只导出各字体 MSDF 图集页（RGBA）。 |
| `/academy debug font` | 打印字体仓库快照（已加载字体、回退链）与最近 32 个分辨率缓存条目，不写文件。 |
| `/academy debug font <text>` | 在上面的基础上，逐码点打印 `U+XXXX -> 字体资源 ID (glyphIndex=N)`，用于确认某个字符实际落到哪个字体、是否缺字形。 |

导出目录为 `<gameDir>/academy/debug`：位图页命名 `bitmap_atlas_page<页码>.png`，MSDF 页命名 `msdf_<字体命名空间>_<字体路径>_page<页码>_0.png`。图集不存在时（尚未绘制过对应文本）会在命令反馈中说明，不会强制创建。整页导出保留字形槽位、gutter 与空白区域，便于直接观察排布。

## UI 控件检查器

`/academy debug imgui [on|off|toggle]` 在支持 ImGui 的构建中开关运行时控件检查器，可查看当前界面的控件树、布局参数与状态。UI 布局已改为 Kotlin DSL 构建，不再有 JSON 布局文件或独立布局编辑器。

## 客户端特效生成

该命令只在本机生成一个 VFXGraph 实例，不影响服务器状态，也不要求任何权限；用于编辑器之外的快速预览与截图。

| 命令 | 作用 |
| --- | --- |
| `/academy vfx spawn <graph>` | 在相机实体的眼睛位置生成一次 `academy:vfxgraph/<graph>`。未加载世界时失败。 |
| `/academy vfx spawn <graph> <x> <y> <z>` | 在指定世界坐标生成同一个图。 |

`<graph>` 只填图名，命令会自己补上 `academy:vfxgraph/` 前缀。生成只发生在客户端本地，其他玩家看不到；需要观察多人表现时，应由服务端技能触发，或使用 `tools/vfxgraph-editor/scripts/` 下的双客户端脚本。

