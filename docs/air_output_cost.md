# 层流切割与压缩空气出力消耗

层流切割的基础压缩空气消耗减半：瞬发 10、半蓄 18、满蓄 28。手动释放与精密操作节点共用同一组基础值；原有 CP 基础消耗保持不变。

标记为伤害技能（`Skill.Builder.damage()`）的主动和持续压缩空气付款，现在与 CP 复用 `OutputControl.adjustResourceCost` 的出力费用曲线。工具技能的压缩空气消耗保持原规则。这里仅共享出力调整，不额外把 CP 的演算强度或熟练度折扣应用到压缩空气。

| 出力 | 费用倍率 | 瞬发气耗 | 半蓄气耗 | 满蓄气耗 |
| --- | --- | --- | --- | --- |
| 1% | 0.1 | 1 | 1.8 | 2.8 |
| 100% | 1 | 10 | 18 | 28 |
| 200% | 4 | 40 | 72 | 112 |

中间出力继续使用 CP 已有的连续曲线。精密操作执行期间保留全局出力隔离：层流切割节点将自身出力换算成气耗倍率传入共用施放入口，不与全局出力重复相乘；原有精密操作 CP 费用计算不变。

压缩空气与 CP 仍通过同一事务判定和扣除，资源不足时不会部分付款。资源不足提示使用调整后的实际气耗。

游戏回归 `academy:aeromanip_output_cost` 覆盖三个蓄力档位、四个出力采样、精密节点与全局出力隔离、持续伤害技能、工具技能对照、关闭出力控制以及失败付款的原子性。

## 本次验证（2026-09-08）

- `test build -DisDev=true` 与 `build -DisDev=false` 均通过；主测试 1,790 项，0 失败、0 错误。
- `academy:damage_penetration` 通过：穿防、吸收保留、直接空力伤害、电荷放电、击杀归属，以及反射副本保留零电荷标记。
- `academy:damage_policy` 与 `academy:aeromanip_output_cost` 通过。
- `academy:time_player_tick_scaling` 首次在尚未触发麻痹的半速基线失败，独立复跑通过。
- `runClientDev -DisDev=true` 完成模组及资源加载，测试客户端正常关闭。战斗和扣费行为由服务端 GameTest 验证。

日志位于 `build/compat-inspect/combat-*.log` 及 `build/compat-inspect/air-output-gametest.log`。发布产物为 `build/libs/academy-26.2.0-0.0.4-alpha-release.jar`。
