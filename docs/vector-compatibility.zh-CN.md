# 第三方线性攻击矢量兼容

第三方兼容入口只会联动以下能力：

- `VectorReflection`：180 度反射。
- `VectorReduction`：按玩家朝向侧向折射。

`ElectromagneticShield` 的随机折射和 `LightShield` 的侧向折射仍是 AcademyCraft 项目内技能联动，不会处理第三方伤害、投射物或兼容档案。

## 兼容层级

1. 项目内技能继续使用精确的 `LinearReflectionResolver`。
2. 标准 `Projectile` 无需第三方修改，直接修改原实体速度、所有者和重定向深度。
3. `data/<namespace>/academy_vector_compat/*.json` 可描述第三方 hitscan 或自定义射线。
4. `SAFE` 模式可根据伤害源坐标、直接实体运动和攻击者视线推断方向。
5. 只有低置信度方向时仅消除本次伤害并显示镜面反馈，不生成虚构返束。
6. 爆炸、近战、环境伤害、状态伤害、自伤和已重定向伤害直接放行。

模式可通过 `/academy vectorcompat mode strict|safe|aggressive` 调整。默认是 `safe`。`/academy vectorcompat inspect` 显示最近的分类层级、方向、置信度、结果和档案模板。

## 档案示例

```json
{
  "damage_type": ["thirdparty:particle_lance"],
  "direct_entity": ["thirdparty:beam_anchor"],
  "shape": "hitscan",
  "direction": "source_position",
  "range": 96.0,
  "radius": 0.25,
  "piercing": false,
  "continuous": false,
  "safe_motion_redirect": false,
  "visual": "energy",
  "block_policy": "clip_no_break",
  "priority": 0
}
```

`direction` 支持 `auto`、`source_position`、`direct_motion`、`attacker_look`、`attacker_to_defender`。`visual` 支持 `none`、`energy`、`arc`、`projectile`。`block_policy` 支持 `clip_no_break`、`pass_through`、`break_allowed`。

`break_allowed` 仍受反射者的方块破坏设置、`BreakBlockEvent` 和区域权限控制，不加载新区块。第三方攻击在伤害事件发生前已经造成的原始视觉和方块破坏无法回滚；兼容层只裁剪并执行新生成的重定向路径。
