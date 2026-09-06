# 自定义伤害入口兼容修复

## 问题与修复

技能首次攻击时会加载 `SkillDamageUtil`。旧扩展依赖该类中 `applyDirectWithFallback(ServerLevel, ServerPlayer, LivingEntity, Skill, DamageSource, float): boolean` 的注入签名；移除该方法会导致整个类转换失败，连带 CTA、VEC、原子崩坏、心智和空间等共用伤害入口无法使用。

保留当前 `applyDirectDamage`，由它调用恢复的旧签名入口。两种 `apply` 重载与 `hurtServer` 直接伤害分支实际经过此入口，因此已有扩展的取消逻辑仍有效。恢复方法名不会恢复旧的普通近战重试：伤害被阻止时仍返回失败，伤害类别和原有直接扣血／真实生命结算方式保持不变。本次未对第三方类添加 Mixin，也没有修改第三方 Jar。

## 回归

- `VerifiedTrueDamageRouteTest` 检查旧方法的完整参数签名、静态属性和布尔返回值，防止再次误删。
- 使用用户安装的 Imagine Breaker 1.0.0、Alice Expansion 0.0.2 与 Chest Item 1.1.2.5 副本运行 `academy:damage_policy`，通过。测试覆盖各自定义伤害的实际扣血、伤害归属、百分比组成、常规增减伤隔离及分类附效。
- 测试副本仅存在于 `run/gametest/mods`，测试结束已移除。原安装文件未改动。
- 全量 1,749 项主测试、开发和发布构建通过；开发客户端完成模组与资源加载。
- 日志：`build/compat-inspect/damage-entry-*.log`。

替换主模组后须完整重启客户端／服务端；已经失败的 JVM 类加载状态无法靠重新进入世界恢复。
