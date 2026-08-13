# 1.21.1 → 26.2 缺失物品与物品图标清单

> 整理日期：2026-08-13  
> 参照版本：`D:\mcmodtest\AcademyCraft-neoforge-1.21.1\AcademyCraft`  
> 当前版本：`D:\mcmodtest\AcademyCraft-Reborn-26.2`

## 使用说明

- 请在每项的“移植决定”列填写：`移植`、`暂缓` 或 `不移植`。
- 本文只进行差分和迁移范围整理，不包含实际移植。
- 统计以两个版本的显式物品注册 ID 为准，并复核了物品模型、物品纹理、方块资源、护甲纹理和旧版自定义逻辑。
- 26.2 的物品定义和模型应通过数据生成器补齐，不应直接手改 `src/generated/resources`。

## 差分概览

| 项目 | 1.21.1 | 26.2 | 差异 |
| --- | ---: | ---: | ---: |
| 已注册物品 ID | 38 | 20 | 26.2 缺失 21 个，共有 17 个 |
| `textures/item/*.png` | 33 | 19 | 旧版有 16 个同名文件在 26.2 中不存在 |
| 实际缺失的独立物品 PNG | — | — | 15 个；另有 1 个已改名复用 |
| 26.2 新增、非旧版移植项 | — | 3 | `ability_control_tablet`、`cat_engine`、`solar_gen` |

结论：26.2 共缺失 **21 个旧版物品注册项**。其中 **15 个独立物品 PNG 确实缺失**；`wind_gen_base_screen.png` 已由内容完全相同的 `screen.png` 取代；虚相位探矿器的专用模型纹理仍在；另有 4 个方块物品不使用独立物品 PNG，但其方块模型和方块纹理整套缺失。

## 本轮移植执行记录

| 物品 | 执行状态 | 说明 |
| --- | --- | --- |
| `imag_phase_circuit` | 物品已移植 | 注册、图标、平面模型、物品定义和双语翻译已恢复；旧版注魔配方需等待 `omni_infusion` 加工系统恢复 |
| `imag_phase_ingot` | 物品已移植 | 注册、图标、平面模型、物品定义和双语翻译已恢复；旧版注魔配方需等待 `omni_infusion` 加工系统恢复 |
| `imag_phase_plate` | 已完成 | 注册、图标、模型、翻译及原版三格竖排合成配方已恢复 |
| `imag_phase_polymer` | 物品已移植 | 注册、图标、平面模型、物品定义和双语翻译已恢复；旧版来源依赖本轮未选择的 `monocrystalline_silicon` 转化逻辑 |
| `needle` | 物品已移植 | 注册、图标、平面模型、物品定义和双语翻译已恢复；旧版成型配方需等待 `omni_forming` 加工系统恢复 |
| `wind_gen_base_screen` | 已完成 | 注册、模型、翻译和旧版配方已恢复；直接复用内容相同的现有 `screen.png` |
| `imag_phase_dowsing_rod` | 已完成 | 注册、专用模型、手持/GUI 变换、服务端已加载区块扫描、客户端同步和立体地图渲染已适配到 26.2 |

> 未写入当前版本无法识别的 `academy:omni_infusion` 和 `academy:omni_forming` 配方 JSON，避免数据包加载时报未知配方类型。本轮未选择的物品仍保持未移植状态。

## 一、材料与机器组件（10 项）

| # | 物品 ID | 1.21.1 中文名 | 旧版用途/行为 | 26.2 图标与模型状态 | 移植时需要包含 | 移植决定 |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | `basic_chip` | 基础芯片 | 普通合成材料；用于能力开发机等配方 | `basic_chip.png` 和物品模型均缺失 | 注册、图标、模型/物品定义、翻译、配方 | 待定 |
| 2 | `computing_chip` | 运算芯片 | 普通合成材料；用于能力开发机、采掘枪、超电磁炮等 | `computing_chip.png` 和物品模型均缺失 | 注册、图标、模型/物品定义、翻译、配方 | 待定 |
| 3 | `imag_phase_circuit` | 虚相位电路 | 高级合成材料；用于虚数合金护甲和两把工具 | `imag_phase_circuit.png` 和物品模型均缺失 | 注册、图标、模型/物品定义、翻译、注魔/合成配方 | 需移植 |
| 4 | `imag_phase_ingot` | 虚相位锭 | 高级合成材料；用于虚数合金护甲 | `imag_phase_ingot.png` 和物品模型均缺失 | 注册、图标、模型/物品定义、翻译、注魔/合成配方 | 需移植 |
| 5 | `imag_phase_plate` | 虚相位板 | 旧版核心材料，参与大量机器、工具和护甲配方 | `imag_phase_plate.png` 和物品模型均缺失 | 注册、图标、模型/物品定义、翻译、材料链配方 | 需移植 |
| 6 | `imag_phase_polymer` | 虚相位聚合物 | 虚相位板的前置材料 | `imag_phase_polymer.png` 和物品模型均缺失 | 注册、图标、模型/物品定义、翻译、材料链配方 | 需移植 |
| 7 | `mechanical_frame` | 机械框架 | 旧版机器和工具的通用结构件 | `mechanical_frame.png` 和物品模型均缺失 | 注册、图标、模型/物品定义、翻译、相关机器配方 | 待定 |
| 8 | `monocrystalline_silicon` | 单晶硅 | 自定义物品；浸入虚相位流体后转化为虚相位聚合物，右击钻石块可制得基础芯片 | `monocrystalline_silicon.png` 和物品模型均缺失 | 注册、图标、模型/物品定义、翻译、转化逻辑、配方及可选 AE 兼容配方 | 待定 |
| 9 | `needle` | 钢针 | 普通合成材料；用于超电磁炮 | `needle.png` 和物品模型均缺失 | 注册、图标、模型/物品定义、翻译、配方 | 需移植 |
| 10 | `wind_gen_base_screen` | 风机显示屏 | 风力发电机底座的组件 | 旧文件名缺失，但旧版 PNG 与当前 `textures/item/screen.png` **逐字节相同** | 注册、复用 `screen.png` 的模型/物品定义、翻译、配方；无需重复复制图标 | 需移植 |

## 二、功能物品与装备（7 项）

| # | 物品 ID | 1.21.1 中文名 | 旧版用途/行为 | 26.2 图标与模型状态 | 移植时需要包含 | 移植决定 |
| ---: | --- | --- | --- | --- | --- | --- |
| 11 | `imag_phase_dowsing_rod` | 虚相位探矿器 | 右击扫描已加载区块中的虚相位流体并在客户端标记位置 | 没有普通物品图标；旧版物品模型以空气纹理占位。专用纹理 `textures/model/imag_phase_dowsing_rod.png` 已在 26.2 保留且内容相同，但物品、模型和渲染器缺失 | 注册、自定义物品逻辑、网络同步、自定义物品渲染器/模型、物品定义、翻译 | 需移植 |
| 12 | `mining_gun` | 采掘枪 | 200,000 FE 容量；持续使用时发射采掘光束，并包含方块掉落处理 | `mining_gun.png` 与约 41 KB 的手工 3D 物品模型均缺失；PNG 只是模型调色纹理，不能单独移植 | 注册、能源组件、使用逻辑、光束控制器、掉落/仓储兼容、3D 模型、纹理、翻译、配方 | 待定 |
| 13 | `railgun_tool` | 超电磁炮 | 100,000 FE 容量；消耗硬币发射超电磁炮并进入冷却 | `railgun_tool.png` 与约 46 KB 的手工 3D 物品模型均缺失；PNG 不能单独移植 | 注册、能源组件、硬币消耗、发射服务、伤害/特效/冷却、3D 模型、纹理、翻译、配方 | 待定 |
| 14 | `imaginary_alloy_helmet` | 虚数合金头盔 | 能量护甲，容量 100,000 FE；属于虚数合金停滞场/PSI 联动系统 | 物品图标、物品模型及整套护甲穿戴纹理均缺失 | 四件套建议一并迁移：注册、材质属性、能源、防护/停滞场、PSI 插槽与桥接、客户端提示/HUD、穿戴纹理、图标、配方 | 待定 |
| 15 | `imaginary_alloy_chestplate` | 虚数合金胸甲 | 能量护甲，容量 300,000 FE；同上 | 同上 | 同上 | 待定 |
| 16 | `imaginary_alloy_leggings` | 虚数合金护腿 | 能量护甲，容量 200,000 FE；同上 | 同上 | 同上 | 待定 |
| 17 | `imaginary_alloy_boots` | 虚数合金靴子 | 能量护甲，容量 100,000 FE；同上 | 同上 | 同上 | 待定 |

> 虚数合金护甲与 26.2 已有的暗物质护甲不是同名替换关系，旧版具有独立图标、两层穿戴纹理和功能系统，不应仅复制图标或映射成暗物质护甲。

## 三、虚相位生态方块物品（4 项）

| # | 物品 ID | 1.21.1 中文名 | 类型 | 26.2 图标与模型状态 | 移植时需要包含 | 移植决定 |
| ---: | --- | --- | --- | --- | --- | --- |
| 18 | `imag_phase_vegetation` | 虚相位植被 | `BlockItem` | 无独立物品 PNG；对应方块注册、方块状态、方块模型和方块纹理整套缺失 | 方块与物品注册、状态/模型/纹理、掉落与生成规则、翻译、数据生成 | 待定 |
| 19 | `imag_phase_leaves` | 虚相位树叶 | 自定义树叶方块的 `BlockItem` | 同上；26.2 仅保留了同名落叶粒子，不等于方块资源仍存在 | 方块类与物品注册、状态/模型/纹理、落叶行为、掉落与生成规则、翻译、数据生成 | 待定 |
| 20 | `imag_phase_log` | 虚相位原木 | 可旋转原木的 `BlockItem` | 无独立物品 PNG；原木侧面/端面纹理和横向模型均缺失 | 方块与物品注册、状态/纵横模型/纹理、掉落与生成规则、翻译、数据生成 | 待定 |
| 21 | `imag_phase_lichen` | 虚相位地衣 | 自定义地衣方块的 `BlockItem` | 无独立物品 PNG；动画方块纹理及 `.mcmeta` 均缺失 | 方块类与物品注册、状态/模型/动画纹理、放置与生成行为、掉落、翻译、数据生成 | 待定 |

旧版这四项共依赖以下 16 个方块显示资源：4 个 blockstate、5 个方块模型、6 个 PNG 和 1 个动画 `.mcmeta`。因此它们应按“方块功能包”评估，而不是按物品图标单独评估。

## 独立物品 PNG 清单

### 26.2 中确实缺失（15 个）

- [ ] `textures/item/basic_chip.png`
- [ ] `textures/item/computing_chip.png`
- [ ] `textures/item/imag_phase_circuit.png`
- [ ] `textures/item/imag_phase_ingot.png`
- [ ] `textures/item/imag_phase_plate.png`
- [ ] `textures/item/imag_phase_polymer.png`
- [ ] `textures/item/imaginary_alloy_boots.png`
- [ ] `textures/item/imaginary_alloy_chestplate.png`
- [ ] `textures/item/imaginary_alloy_helmet.png`
- [ ] `textures/item/imaginary_alloy_leggings.png`
- [ ] `textures/item/mechanical_frame.png`
- [ ] `textures/item/mining_gun.png`
- [ ] `textures/item/monocrystalline_silicon.png`
- [ ] `textures/item/needle.png`
- [ ] `textures/item/railgun_tool.png`

### 已保留或可直接复用（2 个）

| 旧版资源 | 26.2 对应资源 | 结论 |
| --- | --- | --- |
| `textures/item/wind_gen_base_screen.png` | `textures/item/screen.png` | 文件内容完全相同，只需让新物品模型引用现有纹理 |
| `textures/model/imag_phase_dowsing_rod.png` | 同路径同文件名 | 文件内容完全相同；仍需恢复自定义模型与渲染代码 |

### 没有独立物品 PNG（4 个）

- `imag_phase_vegetation`：继承方块模型。
- `imag_phase_leaves`：继承方块模型。
- `imag_phase_log`：继承方块模型。
- `imag_phase_lichen`：物品模型直接引用方块纹理。

## 建议按依赖成组决定

| 建议批次 | 包含内容 | 原因 | 整批决定 |
| --- | --- | --- | --- |
| A：基础材料链 | 单晶硅、基础芯片、运算芯片、虚相位聚合物、虚相位板、虚相位锭、虚相位电路、机械框架、钢针 | 多个机器、工具和护甲的共同配方前置；若只移植成品，需要同步简化配方 | 待定 |
| B：风机显示屏 | 风机显示屏 | 图标可直接复用，迁移成本相对独立 | 待定 |
| C：采掘枪 | 采掘枪及光束/能源/掉落逻辑 | 不能只移植物品壳和 PNG | 待定 |
| D：超电磁炮工具 | 超电磁炮及发射/能源/硬币逻辑 | 不能只移植物品壳和 PNG | 待定 |
| E：虚相位探矿器 | 探矿器、扫描网络包和自定义渲染 | 现有专用纹理可复用，但功能链需要整体重写到 26.2 架构 | 待定 |
| F：虚数合金护甲 | 四件护甲及停滞场/PSI/能源系统 | 四件套共享模型、材质和功能系统，适合整体决定 | 待定 |
| G：虚相位生态方块 | 植被、树叶、原木、地衣 | 四项依赖方块注册、世界内容和完整显示资源 | 待定 |

## 迁移实现注意项

1. 所有 21 个缺失物品在 26.2 中都没有对应注册项，也没有当前格式的物品定义；选择移植后需同步修改注册、创造模式页签、翻译和数据生成器。
2. `mining_gun`、`railgun_tool` 的旧版 JSON 是手工 3D 模型，需核实 26.2/当前 Minecraft 物品模型定义格式，不能把小尺寸 PNG 当作完整图标直接生成平面物品。
3. `imag_phase_dowsing_rod` 旧版普通物品模型故意引用空气纹理，实际显示由自定义渲染器提供；仅恢复 JSON 会得到不可见物品。
4. 虚数合金护甲除 4 个库存图标外，还缺少 `imaginary_alloy_layer_1.png` 与 `imaginary_alloy_layer_2.png`，以及停滞场、PSI 联动和客户端显示代码。
5. 虚相位生态的方块资源当前全部缺失；现存的 `imag_phase_leaves` 粒子只能复用为落叶效果，无法代替方块状态、模型和纹理。
6. 恢复旧配方前应先确认选中的材料链，否则会产生引用未注册物品的配方或无法获取的中间产物。
