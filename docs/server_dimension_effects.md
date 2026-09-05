# 服务端技能维度规则

服务端启动时自动创建 `config/academy-dimension-effects.json`。独立服务器和单人游戏内置服务器都从自己的运行目录读取此文件；客户端配置及玩家发来的设置包不能修改它。修改文件后重启服务器（单人游戏退出存档后重新进入）。

默认文件：

```json
{
  "enabled": false,
  "pvp": {
    "whitelist": [],
    "blacklist": []
  },
  "blockDestruction": {
    "whitelist": [],
    "blacklist": []
  }
}
```

- `enabled: false`：关闭维度规则，完整保留原有玩家开关、单技能开关和服务端配置行为。
- `enabled: true`：分别查询 PVP 和方块破坏的黑白名单。每一项的两份名单可同时填写，互不影响其他项。
- 命中 `whitelist`：强制开启该效果，玩家无法通过个人或单技能开关关闭，包括采掘光束独立开关。
- 命中 `blacklist`：强制关闭该效果，玩家无法通过个人或单技能开关开启。同一维度同时在黑白名单中时，黑名单优先。
- 两份名单均未命中：该效果由玩家原有开关控制，继续遵守原有单技能设置和其他服务端限制。
- 空名单或未填写某个项目：不接管该项开关。例如只设置 PVP 白名单时，方块破坏仍由玩家自行调整。
- 维度 ID 必须完整填写，如 `minecraft:overworld`、`minecraft:the_nether`、`minecraft:the_end`、`example:arena`。允许填写尚未加载的模组维度 ID。
- 配置错误会明确报错并停止服务器初始化，不会悄悄关闭保护或覆盖原文件。

例如，下界强制开启技能 PVP、主世界强制关闭技能 PVP；末地强制开启技能破坏、主世界强制关闭技能破坏。其余维度的对应效果由玩家自行调整：

```json
{
  "enabled": true,
  "pvp": {
    "whitelist": ["minecraft:the_nether"],
    "blacklist": ["minecraft:overworld"]
  },
  "blockDestruction": {
    "whitelist": ["minecraft:the_end"],
    "blacklist": ["minecraft:overworld"]
  }
}
```

只接管下界 PVP、保留所有维度的玩家方块破坏开关，也可以仅填写：

```json
{
  "enabled": true,
  "pvp": {
    "whitelist": ["minecraft:the_nether"]
  }
}
```

兼容旧配置：`mode: "WHITELIST"` / `mode: "BLACKLIST"` 加 `dimensions` 会在读取时映射到对应名单，不改写原文件。未列出的维度采用新的玩家自主规则。某项只要已经填写 `whitelist` 或 `blacklist`，该项旧字段就不再参与判定，避免残留字段产生隐藏规则。

PVP 根据受影响玩家当前所在的服务端维度判断，涵盖技能伤害和使用统一 PVP 检查的控制效果；不限制 PVE 或技能自身作用。方块破坏根据实际破坏发生的服务端维度判断，覆盖光束、延迟破坏、反射及代理挖掘的公共破坏入口。开启规则不会赋予技能原本没有的破坏能力，也不会跳过队伍友伤、创造／旁观保护、方块权限、硬度、技能条件等其他判定。普通原版攻击、原版挖掘与方块搬运不属于此配置。

公开扩展入口：`AbilityEffectPolicy.pvp(effectLevel)` 和 `AbilityEffectPolicy.blockDestruction(effectLevel)`。返回 `DEFAULT` 时使用原逻辑，`ALLOW` 时服务端强制开启相应开关，`DENY` 时必须阻止相应效果。技能、精密操作、非玩家施法者及延迟任务应传入效果实际发生的世界。
