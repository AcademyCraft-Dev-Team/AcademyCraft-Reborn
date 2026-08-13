# AcademyCraft 文档索引

本目录只保留当前版本仍需维护的玩家参考、开发契约和进行中计划。历史阶段审计、一次性实现报告和已经完成的合并记录由 Git 历史保存，不再作为现行文档维护。

当前源码基线：8 个能力类别、94 个已注册技能；其中 Level0 7 个，非通用技能 87 个。

## 玩家与服主参考

- [能力与技能使用说明](ABILITY_SKILL_USAGE_GUIDE.md)：技能操作形态与玩家可见效果。
- [命令参考](COMMANDS.zh-CN.md)：管理、调试和客户端命令。
- [第三方线性攻击矢量兼容](vector-compatibility.zh-CN.md)：矢量兼容模式、档案格式和安全边界。
- [缺失物品与图标移植清单](MISSING_ITEMS_AND_ICONS_VS_1_21_1.md)：旧版本物品迁移决策与当前剩余项。

## 技能与实现基线

- [全技能效果、消耗、迭代与堆栈总表](SKILL_EFFECT_COST_ITERATION_STACK_MATRIX.md)：94 个技能的现行数值基线。
- [技能实现与调控总表](SKILL_IMPLEMENTATION_CONTROL_MATRIX.md)：实现状态、输入、依赖与实现类索引。
- [能力技能前置关系清单](ability_skill_dependencies.md)：94 个技能的直接前置关系。
- [非通用技能熟练度里程碑方案](NON_COMMON_SKILL_PROFICIENCY_PLAN.md)：87 个非通用技能的熟练度规则，也是本地化同步脚本的输入。
- [Mentalout 开发记录](MENTALOUT_DEVELOPMENT.md)：心理掌握架构、安全边界、限制与验收记录。

## UI 契约

- [UI 布局序列化](UI_LAYOUT_SERIALIZATION.md)：布局文件、覆盖规则和控件名称契约。
- [开发客户端 UI 调试流程](UI_DEBUG_WORKFLOW.md)：布局编辑、发布和备份流程。

## 进行中计划与验收

- [全能力可配置技能方案系统计划](SKILL_PROJECT_SYSTEM_PLAN.md)：尚未实施的技能方案系统设计与决策项。
- [当前运行时验收清单](RUNTIME_ACCEPTANCE.md)：从历史移植和审计文档中汇总的未关闭验证项。

## 维护规则

1. 技能注册数量和 ID 以 `src/main/java/org/academy/internal/common/ability/Skills.java` 与 `SkillNames.java` 为准。
2. 数值或行为发生变化时，优先更新全技能总表，再同步玩家使用说明、调控表和前置关系清单。
3. 一次性审计和实现报告完成后，把未关闭事项迁入当前验收清单，再从现行文档中移除。
4. `src/generated/resources` 由数据生成产生，不在文档中要求手工编辑。
