# 服务端技能维度规则

服务端启动时自动创建 `config/academy-dimension-effects.json`。独立服务器和单人游戏内置服务器都从自己的运行目录读取此文件；客户端配置及玩家发来的设置包不能修改它。修改文件后重启服务器（单人游戏退出存档后重新进入）。

默认文件：

```json
{
  "enabled": false,
  "pvp": {
    "mode": "BLACKLIST",
    "dimensions": []
  },
  "blockDestruction": {
    "mode": "BLACKLIST",
    "dimensions": []
  }
}
```

- `enabled: false`：关闭维度规则，完整保留原有玩家开关、单技能开关和服务端配置行为。
- `enabled: true`：服务端强制决定技能 PVP 和方块破坏开关，忽略玩家个人及单技能效果开关，包括采掘光束独立开关。
- `WHITELIST`：仅允许列表中的维度；空列表表示全部禁止。
- `BLACKLIST`：禁止列表中的维度，其余允许；空列表表示全部允许。
- PVP 和方块破坏分别选择模式。维度 ID 必须完整填写，如 `minecraft:overworld`、`minecraft:the_nether`、`minecraft:the_end`、`example:arena`。允许填写尚未加载的模组维度 ID。
- 配置错误会明确报错并停止服务器初始化，不会悄悄关闭保护或覆盖原文件。

例如，只允许下界技能 PVP，并禁止主世界技能破坏：

```json
{
  "enabled": true,
  "pvp": {
    "mode": "WHITELIST",
    "dimensions": ["minecraft:the_nether"]
  },
  "blockDestruction": {
    "mode": "BLACKLIST",
    "dimensions": ["minecraft:overworld"]
  }
}
```

PVP 根据受影响玩家当前所在的服务端维度判断，涵盖技能伤害和使用统一 PVP 检查的控制效果；不限制 PVE 或技能自身作用。方块破坏根据实际破坏发生的服务端维度判断，覆盖光束、延迟破坏、反射及代理挖掘的公共破坏入口。开启规则不会赋予技能原本没有的破坏能力，也不会跳过队伍友伤、创造／旁观保护、方块权限、硬度、技能条件等其他判定。普通原版攻击、原版挖掘与方块搬运不属于此配置。

公开扩展入口：`AbilityEffectPolicy.pvp(effectLevel)` 和 `AbilityEffectPolicy.blockDestruction(effectLevel)`。返回 `DEFAULT` 时使用原逻辑，`ALLOW` 时服务端强制开启相应开关，`DENY` 时必须阻止相应效果。技能、精密操作、非玩家施法者及延迟任务应传入效果实际发生的世界。
