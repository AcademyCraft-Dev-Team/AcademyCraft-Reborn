# AcademyCraft 命令参考

本文档覆盖当前代码中注册的全部 `/academy` 命令，包括服务端管理命令和客户端调试命令。命令注册入口分别位于：

- 服务端：`src/main/java/org/academy/internal/server/commands/AcademyCraftCommand.java`
- 客户端：`src/main/java/org/academy/AcademyCraftClient.java`

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
- `/academy debug ui ...`、`/academy debug hud`、`/academy debug save` 和 `/academy uieditor ...` 只会在 ImGui 可用且环境变量 `IS_DEV=true` 时注册，通常通过 `runClientDev` 使用。

## 命令总览

```text
/academy
├─ learn_all
├─ learned
├─ learn <skill_name>
├─ set_category <category_name>
├─ level <level>
├─ set_exp <skill_name> <amount>
├─ debug
│  ├─ god
│  ├─ cp
│  │  ├─ info [<target> [<broadcast>]]
│  │  ├─ get <target> value|max|curr_sp|max_sp|level|timer|status
│  │  ├─ set <target> <value> [<broadcast>]
│  │  ├─ set_max <target> <value> [<broadcast>]
│  │  └─ set_status <target> <status> [<timer> [<broadcast>]]
│  ├─ skillgui [on|off|toggle|reset|export]                 （客户端）
│  ├─ ui [<layout>]                                        （仅开发客户端）
│  ├─ hud                                                  （仅开发客户端）
│  └─ save                                                 （仅开发客户端）
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
│  ├─ snapshot
│  └─ dump
├─ vectorcompat
│  ├─ inspect
│  └─ mode [strict|safe|aggressive]
└─ uieditor [<layout>]                                     （仅开发客户端）
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

示例：

```mcfunction
/academy set_category academy:electromaster
/academy learn academy:arc_generate
/academy level 3
/academy set_exp academy:arc_generate 1200
```

## 能力等级经验

这组命令操作用于能力等级成长的经验值，与 `/academy set_exp` 操作的单个技能熟练度不同。

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

## 性能分析

| 命令 | 参数限制 | 作用 |
| --- | --- | --- |
| `/academy profile start` | 无 | 以默认 `1 ms` 采样间隔启动采样器，同时开启性能区段捕获。 |
| `/academy profile start <interval_ms>` | 整数 `1`～`1000` | 以指定毫秒间隔启动采样器，同时开启性能区段捕获。 |
| `/academy profile stop` | 无 | 停止采样器和性能区段捕获，保留已收集的数据。 |
| `/academy profile reset` | 无 | 清空采样数据和性能区段统计。 |
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

兼容模式的行为、档案格式和安全边界参见 [第三方线性攻击矢量兼容](vector-compatibility.zh-CN.md)。

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

## 开发客户端 UI 调试

以下命令只在 ImGui 可用且 `IS_DEV=true` 时注册。

| 命令 | 作用 |
| --- | --- |
| `/academy debug ui` | 打开已注册 GUI 布局的调试浏览器。 |
| `/academy debug ui <layout>` | 按布局 ID 直接打开指定 GUI 布局编辑器；输入时会补全已注册的 GUI 布局。 |
| `/academy debug hud` | 打开四区域能力 HUD 预览。 |
| `/academy debug save` | 发布当前会话中全部已修改的布局。 |
| `/academy uieditor` | 打开 GUI 布局调试浏览器，是兼容入口。 |
| `/academy uieditor <layout>` | 按布局 ID 打开布局编辑器，可补全全部已注册布局。 |

布局发布位置、备份策略和实时挂接方法参见 [RunClientDev UI Debug Workflow](UI_DEBUG_WORKFLOW.md)。
