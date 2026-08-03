# 与 1.21.1 的技能移植核对

核对基线为当前仓库与 `D:\mcmodtest\AcademyCraft-neoforge-1.21.1\AcademyCraft`，更新时间为 2026-08-03。比较范围包括技能注册、等级、学习能量、依赖、默认按键、服务端效果、资源、本地化和旧存档迁移。

## 总览

| 项目 | 结果 |
| --- | ---: |
| 1.21.1 注册技能 | 61 |
| 当前已覆盖 | 61 / 61 |
| 仍缺失 | 0 |
| 当前同 ID 实现 | 51 |
| 高版本改名实现 | 10 |
| 当前版本扩展技能 | 25 |
| 当前总注册技能 | 86 |

## 本轮完成的缺失技能

| 分类 | 技能 | 状态 |
| --- | --- | --- |
| Accelerator | Reflection Filter | 已移植模式、白名单/黑名单、配置界面、同步与存档 |
| Accelerator | Black Wing | 已移植飞行、扇形真实生命攻击、按键与动画 |
| Accelerator | White Wing | 已移植形态继承、飞行、攻击与动画 |
| Accelerator | Platinum Wing | 已移植渲染、处决、实体清理与防复生控制 |
| Accelerator | Crossing the Abyss | 已移植真实生命伤害、治疗上限和致命存活计数 |
| Meltdowner | Scatter Bomb | 已移植七束电子弹、蓄力资源、伤害与技能树依赖 |

## 改名与存档兼容

| 1.21.1 ID | 当前 ID |
| --- | --- |
| `current_recharge` | `pulse_charge` |
| `lightning_spear` | `thunder_lance` |
| `thunder_clap` | `thunderclap` |
| `penetrate_teleport` | `cut_through` |
| `assault_jet` | `jet_strike` |
| `brain_development_lv1..5` | `level0_passive_lv1..5` |

旧技能进度、占用状态和旧按键键名均保留迁移路径。本地化显示采用 1.21.1 术语，并为旧 ID 补充兼容语言键。

## 高版本适配说明

- 伤害、真实生命、友军判定、投射物反射和执行逻辑均由服务端判定。
- 翼系飞行、第一/第三人称动画和白金翼着色器已改用当前渲染与网络接口。
- Kinetic Energy Applied 的方块掉落直接生成到世界；参考版依赖的 `StorageContainer` 在当前架构中不存在。
- Vector Reflection 的默认技能行为已经对齐；参考版中面向恶意第三方模组的反篡改、反冻结和观察者重建框架未机械移植。
- 当前版本原有的 25 个扩展技能继续保留，不覆盖 1.21.1 技能树与旧档迁移。

## 验证

- `./gradlew test --no-daemon`
- `./gradlew build -DisDev=true --no-daemon`
- `./gradlew build -DisDev=false --no-daemon`

最终仍建议在客户端重点验证：数据终端按键设置、V 键技能 HUD、翼系飞行/攻击、Reflection Filter、Scatter Bomb、Railgun、Plasma Generation 和 Vector Reflection。
