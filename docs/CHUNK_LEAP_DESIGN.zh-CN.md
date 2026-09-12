# 区块跃迁（`chunk_leap`）详细设计方案

> 目标技能：传送使（`academy:teleport`）LV5 —— 区块跃迁。
> 按键开启后打开一张以区块为网格、可缩放拖动的**跨维度区块地图**；地图可自行加载同维度与跨维度区块、显示地图上的生物实体；玩家可选中等额区块（可跨维度）交换区块位置（含其中的实体），或选中目标实体将其传送至选中的位置。传送时玩家随区块一并位移（含跨维度），不进入加载界面。

> **实施状态（P1 + P2 已落地）**：同维度与跨维度交换闭环均已实现并通过构建与测试；保真项按 §0.2 继续推进。
> 已落地（P1）：技能注册与 `Alt+C` 按键、地图 GUI（缩放/拖动/框选/维度页切换）、视图租约加载、区块瓦片流、生物标记、**同维度区块交换**（区块段引用交换 + 方块实体重定位 + 实体/玩家随区块位移 + 高度图/天光列/光照重建 + 整区块重发）、实体传送、体积计费、设置项与本地化。
> 已落地（P2）：**跨维度区块交换**（`ChunkVerticalBand` 按绝对 Y 求重叠带 + 段字节回环交换，带外段保持不动）、跨维度实体与玩家搬运、`ChunkLeapLoadingScreen` 替代跨维加载界面（仅在跃迁归因窗口内生效）、目的地预加载进度下发与 GUI 门控、客户端跨维度校验放行。
> 尚未落地（P3）：计划刻重定位、POI 重建、崩溃恢复日志、地图瓦片增量刷新。
> 实现与本文档的差异详见文末「§14 实施记录」。

本文档为设计文档。文中标注了已核验的 Minecraft 26.2 / NeoForge 26.2.0.70 API 依据，以及需要 AccessTransformer 或 Mixin 才能完成的少数环节。

---

## 0. 结论摘要与实施分期

### 0.1 核心结论

| 需求 | 可行性 | 关键依据 |
| --- | --- | --- |
| 同维度区块交换 | ✅ 可行，且有高效实现 | `ChunkAccess.getSections()` 返回**内部数组引用**，可直接交换 `LevelChunkSection`；`LevelChunkSection.copy()` + `write/read(FriendlyByteBuf)` 可安全交换 section 内容 |
| 跨维度区块交换 | ⚠️ 可行但受限 | 各维度 `getSectionsCount()`/`getMinY()` 不同（主世界 minY=-64/24 段，下界 minY=0/16 段），**不能整段指针交换**，必须按绝对 Y 配对 + 字节回环交换内容 |
| 区块内实体随区块迁移 | ✅ 可行 | `level.getEntities(null, chunkAABB, sel)` 枚举 + `TeleportSync.teleportInstantly(entity, destLevel, pos)` 跨维度搬运 |
| 地图自行加载区块（含跨维度） | ✅ 可行 | `ServerChunkCache.addTicketWithRadius(TicketType, ChunkPos, int)` / `addTicketAndLoadWithRadius(...)`（公开，返回 `CompletableFuture`，**不阻塞主线程**） |
| 地图显示生物实体 | ✅ 可行 | 服务端 `getEntitiesOfClass(LivingEntity.class, aabb, sel)` 汇总坐标后轻量下发 |
| 同维度玩家随区块传送、无加载界面 | ✅ 天然无缝 | 同维度 `ServerPlayer.teleport` 只发 `ClientboundPlayerPositionPacket`，**不重建 ClientLevel** |
| 跨维度玩家随区块传送、无加载界面 | ⚠️ 可做到「无可见加载界面」 | 必须走 `ClientboundRespawnPacket` → 重建 `ClientLevel` → `LevelLoadTracker`；用 NeoForge `RegisterDimensionTransitionScreenEvent` 注册**不可见的 `LevelLoadingScreen` 子类**即可满足 `hasClientLoaded` 握手，同时不显示加载画面；但仍有 ≥500ms 的 `LEVEL_LOAD_CLOSE_DELAY_MS` 窗口 |

### 0.2 分期落地建议

- **P1（核心闭环）**：技能注册 + 按键 + 地图 GUI（缩放/拖动/框选/维度切换）+ 瓦片与实体流 + 视图租约加载 + **同维度**区块交换（含方块实体与实体）+ 同维度玩家随区块无缝传送。
- **P2（跨维度）**：跨维度区块交换（绝对 Y 配对 + 字节回环）+ 跨维度实体/玩家搬运 + `RegisterDimensionTransitionScreenEvent` 隐藏加载界面 + 预加载进度条。
- **P3（保真与韧性）**：计划刻重定位、POI 重建、结构引用处理、崩溃日志（SavedData）与恢复、地图瓦片按方块变更增量刷新。

---

## 1. 技能定义与注册

### 1.1 基础元数据

```java
public final class ChunkLeap extends Skill {
    public ChunkLeap() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)                 // 推荐等级
                .energyCost(100_000)                        // IF 学习消耗，与既有 L5 一致
                .cpCost(200)                                // 基础施放 CP，实际按体积计费
                .iterationTicks(20)                         // CP 迭代/冷却间隔，上限 MAX_CP_ITERATION_TICKS=20
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.AREA_TELEPORT_SELECT)     // 前置：区域传送
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Area Teleport", "academy:area_teleport_select"))
        );
    }
}
```

- **不加熟练度档位**：`Builder` 默认 `SkillProficiencyProfile.NONE`，与同为 L5 的 `SpacialExcision` 一致。
- **计费模型**（服务端在 `executeActive` 内用 `CostCalculator` 二次核算，防止客户端谎报）：
  `actualCost = 100 + 4 × 交换区块总数 + 30 × 实体传送个数`
  客户端在 GUI 校验阶段做同样的预估并展示（`ChunkLeapCost.estimate(...)` 单一实现，双端复用）。
- **等级门禁**：`LEVEL5` 只影响推荐等级与学习条件；运行期无额外等级校验（与现有一致）。

### 1.2 注册点清单

| 文件 | 修改 |
| --- | --- |
| `src/main/java/org/academy/internal/common/ability/SkillNames.java` | 新增 `public static final String CHUNK_LEAP = "chunk_leap";` |
| `src/main/java/org/academy/internal/common/ability/Skills.java` | 新增 `CHUNK_LEAP = SKILLS.register(SkillNames.CHUNK_LEAP, ChunkLeap::new);`（紧随 `SPACIAL_EXCISION` 之后） |
| `src/main/java/org/academy/api/client/resources/R.java` | `R.textures.chunk_leap_icon = academy("textures/ability/teleport/skill/chunk_leap/icon.png")`；地图/实体图标（见 §3.5） |
| `src/main/resources/assets/academy/textures/ability/teleport/skill/chunk_leap/icon.png` | 技能图标（32×32，风格对齐既有 `*/icon.png`） |
| `src/main/java/org/academy/internal/common/network/PacketTypes.java` | 新增 §4.1 全部 packet type（`PACKET_TYPES.register("chunk_leap_*", ...)`） |
| `src/main/resources/assets/academy/lang/{en_us,zh_cn}.json` | `skill.academy.chunk_leap`、`app.academy.chunk_leap.*`（GUI 文案/按键标签/设置项） |
| `src/main/resources/assets/academy/ui/layout/chunk_leap.json` | 新 GUI 布局（§3.2） |
| `docs/ability_skill_dependencies.md` | 追加：`5 | 区块跃迁 (academy:chunk_leap) | academy:area_teleport_select` |
| `docs/SKILL_IMPLEMENTATION_CONTROL_MATRIX.md` | 追加技能行（运行 `tools/docs/sync_skill_control_matrix.ps1` 重生成） |
| `docs/SKILL_EFFECT_COST_ITERATION_STACK_MATRIX.md` | 同步消耗/迭代数值 |

### 1.3 技能树位置（`SkillInfo`）

```java
public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
        AbilityCategories.TELEPORT.get(),
        new AbilitySystemClient.SkillInfo(
                Skills.CHUNK_LEAP.get(),
                List.of(AreaTeleportSelect.Client.SKILL_INFO),
                R.textures.chunk_leap_icon, 190, 60));   // 与 L5 行对齐，位于区域传送右侧
```

> 即使不写，`AbilitySystemClient.ensureCompleteSkillInfos()` 也会自动补位；显式声明只为控制坐标与依赖连线。

### 1.4 服务端初始化

```java
@Override public void initClient() {
    var key = getKey();
    AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
    Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
    Client.CONFIG.registerSettings(this);                          // SkillSettingsRegistry 模块
    MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    InputSystem.addKeyBinding(Client.KEY_NAME_OPEN,                 // 唯一全局键
            Client.CONFIG.getKeyBinding(Client.KEY_NAME_OPEN, InputSystem.combo(
                    InputSystem.InputType.KEYBOARD, InputConstants.KEY_C,
                    InputConstants.PRESS, InputConstants.MOD_ALT)),
            ctx -> Client.openMap());
}

@Override public void initServer(MinecraftServerContext context) {
    MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);      // 处理 C2S
    ChunkMapViewService.init();                                      // 视图租约 tick 钩子
    ChunkSwapService.init();                                        // 交换任务 tick 钩子
}
```

---

## 2. 按键与配置

### 2.1 全局按键（仅一个）

| 名称 | 默认组合 | 语义 |
| --- | --- | --- |
| `chunk_leap_open` | **`Alt+C` 按下** | 开启/关闭区块跃迁地图（**按键开启**语义，重复按下关闭） |

选择理由：
- `C↓` 已被 `quick_location_teleport` 占用；`Alt+C` 在当前仓库全部默认键中**未被使用**（已核对 `Alt+D/G/H/N/R/X/Y`、`Ctrl+Alt+Y`、`Shift+Alt+Y` 等组合）。
- 名称必须以技能注册路径开头（`InputSystem.isBindingForSkill` 规则），故为 `chunk_leap_open`。

### 2.2 界面内按键（走 `Screen.keyPressed`，不占用全局键）

地图打开时由 `ChunkLeapScreen.keyPressed` 直接处理，避免与全局技能键冲突：

| 键 | 操作 |
| --- | --- |
| `Tab` | 切换 选区模式：**区块选择** ↔ **实体选择** |
| `1` / `2` | 区块模式下切换 正在编辑 源选区 / 目标选区 |
| `Enter` | 执行（区块交换 或 实体传送） |
| `R` | 清空当前选区 |
| `F` | 视图自适应（fit 到当前选区或玩家所在区块） |
| `+` / `=` / `]` | 放大 |
| `-` / `[` | 缩小 |
| `方向键` | 平移视图（每次 4 区块） |
| `G` | 跳转到玩家所在区块（跨维度时自动切到玩家维度） |
| `Esc` | 关闭地图 |

### 2.3 配置与设置项

`ChunkLeapConfig extends KeyBindingConfig`：

| 绑定 | 默认 | 说明 |
| --- | --- | --- |
| `chunk_leap_open` | `Alt+C↓` | 全局开关 |

`SkillSettingsRegistry` 模块（id `chunk_leap`，标题 `app.academy.skill_settings.advanced.chunk_leap.title`）：

| Entry | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `max_region_chunks` | `IntegerRange` 16–1024，step 16 | 256 | 单次交换区块总数上限（防止误选超大区域导致性能事故） |
| `auto_load_map` | `Toggle` | `true` | 地图是否自动为可见区域申请区块加载（关闭后只显示已加载区块） |
| `view_radius_chunks` | `IntegerRange` 8–48，step 4 | 24 | 单维度地图视图租约半径（区块） |
| `tile_detail` | `Choice`（低/中/高） | 中 | 瓦片采样分辨率 4×4 / 8×8 / 16×16 |
| `show_entities` | `Toggle` | `true` | 是否在地图上显示生物实体 |
| `entity_refresh_ticks` | `IntegerRange` 5–40，step 5 | 10 | 实体标记刷新间隔 |
| `confirm_swap` | `Toggle` | `true` | 执行前是否需要二次确认（危险操作） |
| `move_players_with_chunks` | `Toggle` | `true` | 玩家所在区块被交换时是否随区块一并位移 |
| `seamless_transition` | `Toggle` | `true` | 跨维度时是否隐藏加载界面 |

---

## 3. 客户端 GUI

### 3.1 基类与生命周期

采用 **`UiScreen`**（`org.academy.api.client.gui.screen.UiScreen`，Kotlin 基类），与 `LocationTeleportScreen`、`ModularProgramScreen`、`WideAreaInterferenceScreen` 同族：

- 需要模态全屏、背景世界模糊/变暗（`extractBackground` 提供）；
- 需要 `ScreenDispatcher` 的命令录制 + 离屏 RenderTarget + 前景模糊隔离（符合 Academy UI 视觉语言）；
- 需要键盘焦点与鼠标捕获，且**不需要**容器菜单/物品槽。

不使用 `App`/`TerminalHud`：地图是技能触发的模态操作界面，不是终端工作区。

```java
public final class ChunkLeapScreen extends UiScreen implements SerializedUiDebugHost {
    public ChunkLeapScreen() { super(Component.translatable("skill.academy.chunk_leap")); }

    @Override protected void onInit() { /* §3.2 加载布局 + 绑定控制器 */ }

    // 画布走即时模式（ProgramUiGraphics），外壳走 JSON 布局槽位
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a);
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a);

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl);
    @Override public boolean mouseReleased(MouseButtonEvent e);
    @Override public boolean mouseDragged(MouseButtonEvent e, double mx, double my);
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy);
    @Override public boolean keyPressed(KeyEvent e);

    @Override public String debugLayoutId() { return "chunk_leap"; }
    @Override public FrameLayoutWidget debugLayoutRoot() { return getRoot(); }
}
```

复用已核验的几何工具：`PrecisionEditorGeometry.screenToGraph/graphToScreen/zoomAt/selectionBounds`（`org.academy.internal.client.ability.mentalout`）——它把画布原点和 pan/zoom 的换算抽得很干净，正好是区块网格需要的同一套数学。

### 3.2 布局

**布局文件**：`src/main/resources/assets/academy/ui/layout/chunk_leap.json`（schema 与 `location_teleport.json` 完全一致：`{ "version": 1, "root": { "type": "frame_layout", ... } }`，`gravity` 用 `Gravity` 位常量：17=CENTER、48=TOP、80=BOTTOM、3=LEFT、5=RIGHT）。
加载方式沿用 `SerializedUiLayout.INSTANCE.load(id, requiredNames, this::fallbackLayout)`，缺失/不兼容时回退到代码构建的 `fallbackLayout()`。

**槽位表**（`empty` 占位，屏幕按槽位矩形绘制/定位）：

| 槽位名 | 尺寸（逻辑 px） | 用途 |
| --- | --- | --- |
| `panel` | `MATCH_PARENT` | 全屏根面板 |
| `panel_background` | `MATCH_PARENT` | `BlendQuadWidget`，`alpha 0.12`，四边 `margin 4` |
| `header_divider` | `MATCH_PARENT × 1` | 标题分割线（`fill`，色 `DataTerminalTheme.DIVIDER`） |
| `title` | 240×16 | 标题「区块跃迁 CHUNK LEAP」 |
| `dim_source` | 150×18 | 源维度切换（`RadioGroupWidget` 或 `WheelPickerWidget`） |
| `dim_target` | 150×18 | 目标维度切换 |
| `mode_tabs` | 180×18 | 模式：区块 / 实体（`RadioGroupWidget`） |
| `map_canvas` | 自适应（左侧主体，`weight=1`） | 地图画布视口（**唯一 scissor 区域**） |
| `zoom_slider` | 120×10 | 缩放滑条（`SeekBarWidget`） |
| `zoom_label` | 56×12 | `1.00x` / `0.25x` |
| `cursor_coord` | 160×12 | 光标处区块坐标 + 生物群系名 |
| `load_progress` | 200×10 | 瓦片/区块加载进度条（`ProgressBarWidget`） |
| `preload_progress` | 200×10 | 传送目的地预加载进度（决定「确认交换」是否可用） |
| `info_panel` | 260×`MATCH_PARENT` | 右侧信息与校验面板 |
| `sel_source_box` | 244×64 | 源选区卡片 |
| `sel_target_box` | 244×64 | 目标选区卡片 |
| `validate_box` | 244×48 | 校验结果（等额/形状/重叠/上限/预加载） |
| `cost_box` | 244×24 | 消耗预估 |
| `execute` | 244×22 | 确认交换 / 传送（`ButtonWidget`，未通过校验时禁用） |
| `entity_list` | 244×`weight=1` | 实体模式下的实体列表（`ScrollPanelWidget`） |
| `entity_filter` | 244×18 | 实体搜索/筛选（`TextBoxWidget`） |
| `legend` | `MATCH_PARENT × 14` | 图例与底部快捷键提示 |
| `close` | 16×16 | 关闭按钮 |

**整体布局示意**

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│ 区块跃迁 CHUNK LEAP                [源 主世界 ▾]  [目标 下界 ▾]   [区块|实体]        [×] │
├────────────────────────────────────────────────────────────┬─────────────────────────────┤
│                                                            │  选区                        │
│    ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·           │  源   主世界 (48,-16)  4×4   │
│    ·   · ┌───────────────────────┐  ·   ·   ·   ·           │       16 区块 · 形状 4×4     │
│    ·   · │  源选区  4×4          │  ·   ·   ·   ·           │  目标 下界  (0,0)      4×4   │
│    ·   · │  ▣ ▣ ▣ ▣              │  ·   ·   ●   ·           │       16 区块 · 形状 4×4     │
│    ·   · │  ▣ ▣ ▣ ▣              │  ·   ·   ·   ·           │ ───────────────────────────  │
│    ·   · └───────────────────────┘  ·   ●   ·   ·           │  校验  ✓等额 ✓形状 ✓已预载  │
│    ·   ●   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·           │  消耗  164 CP                │
│    ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·           │  [      确认交换      ]      │
│                                                            │  预加载 ▓▓▓▓▓▓▓▓░░  96%      │
│    zoom 1.00x   (48,-16) 平原   实体 23   加载 512/512      │  [跳转玩家] [清空]           │
├────────────────────────────────────────────────────────────┴─────────────────────────────┤
│ 拖拽=框选  空格/中键拖拽=平移  滚轮=缩放  1/2=源/目标  Tab=区块/实体  Enter=执行  Esc=关闭 │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 视觉语言（按 `.agents/skills/refresh-academy-ui` 强制规则）

- 结构优先：几何 → 线条层级 → 透明层级 → 局部对比 → 模糊隔离 → **至多一个语义强调色**。
- 面板：`BlendQuadWidget(alpha≈0.12)` 作结构底 + `DataTerminalTheme.border` 细线；不使用不透明卡片、不使用渐变卡片。
- 强调色：`DataTerminalTheme.TELEPORT_ACCENT`（`0xFFC77DFF` 紫）用于选区边界与选中态；`TELEPORT_SELECTED`（`0x557B3FA1`）用于选区填充。
- 网格：细线 `0x0FFFFFFF`，每 8 区块的粗线 `0x20FFFFFF`（对应 vanilla 区块网格惯例）。
- 文字：8 逻辑 px 基准 MSDF（`LabelWidget`），层级靠透明度/字距/对齐而非多字号。
- 模糊：只对**地图视口背后的世界**做模糊；前景网格/线框/文字保持锐利；不叠加多层模糊。
- 动画只做反馈：视图 pan/zoom 缓动（`ClientUtil.animationFactor`）、选区出现/消失淡入、加载进度条呼吸；无持续装饰动画。

### 3.4 地图画布渲染管线

**核心策略：把每个区块烘焙成一张小位图，再整块绘制 —— 而非逐格填充。**

- 数据：每个区块 = `detail × detail` 的 ARGB 字节（默认 `detail = 8` → 64 B/chunk，可切换 4/16）。
- 客户端把 `detail×detail` 瓦片**打包进一张动态图集**：
  - `ChunkTileAtlas`：单张 `1024×1024` RGBA 纹理，格位 `16×16` px（含 1px 内边距防渗色）→ 可容纳 `64×64 = 4096` 个瓦片，覆盖 64×64 区块的整屏视野绰绰有余。
  - 复用 MSDF 图集的 `SkylineAllocator` 思路做矩形分配；上传走 `UiEnvironment.createDynamicTextureSource(identifier, bytes)`（`MinecraftUiEnvironment` 已实现 `DynamicTexture` + `textureManager.register`）。
  - 脏瓦片只重写对应的 `16×16` 子区域，不重建整张纹理。
- 绘制：**每个可见区块 1 个带 UV 的纹理四边形**（`ImageWidget` 同族的 `PosTexColorRectDrawCommand` / 直接 `ImageDrawCommand`）。屏幕最多可见区块数约 `(屏宽/16)×(屏高/16)`，1080p 下 ≈ 4800 个四边，但**同一纹理 + 同一 pipeline + 同一 scissor**，由 `BatchProcessor` 合并成**极少数 draw call**。
  - 关键约束（已核验 `BatchProcessor.BatchState.shouldBreakBatch`）：批次在 pipeline、纹理集（`resourceKey`）或 `scissorRect` 变化时被打断。因此：**整张地图只用 1 个外层 scissor、1 张瓦片图集、1 个 pipeline**；禁止逐格 scissor。
- 视口裁剪：在**区块坐标空间**做可见矩形迭代（`PrecisionEditorGeometry` + 可见区块矩形），只提交可见区块，并对图集外/未加载区块提交统一的「未加载」纯色占位。
- 缩放档位与 LOD：缩放 < 0.5x 时降级为 detail 4 的粗瓦片（服务端按请求档位生成），避免低倍率下无谓的高精度带宽。
- 网格与选区覆盖层：在瓦片之上按 drawOrder 分层提交细线/粗线、选区填充与 1px 边界、hover 高亮；这些也是少量四边，不破坏批次。

### 3.5 实体标记

- **不做逐实体模型渲染**（`UiContext` 命令栈无实体渲染管线；`EntityRenderDispatcher` 无法批量进图集）。
- 采用**分类图标 + 批量四边形**：
  - `R.textures.gui.map_marker.{hostile, passive, neutral, player, item, projectile}` 6 张 8×8 图标（风格对齐既有 `assets/academy/textures/gui/icon/*`）。
  - 服务端只为每个实体下发「分类枚举 + 区块坐标 + 可选实体类型 id」；客户端按分类选图标，聚合成少量批次。
  - 同一区块内实体过多时按分类合并为一个「×N」徽标（`LabelWidget`），避免标记风暴。
- **单实体预览**：hover 或选中实体时，在 `extractRenderState`（vanilla 通道）调用
  `InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x1,y1,x2,y2, scale, yOffset, mouseX, mouseY, entity)`
  渲染 1 个实体模型预览（与 `DarkmatterCreationScreen.extractCreaturePreview` 完全同法，含 `previewEntity.setId(Integer.MIN_VALUE + 1)` 稳定 id 处理）。
  预览实体由客户端用「实体类型 id + 空 NBT」本地构造，**不依赖实体真实存在**。

### 3.6 交互状态机

`ChunkMapInputController`（客户端，`ChunkLeapScreen` 持有）：

```
enum DragMode { NONE, PAN, MARQUEE, ENTITY_MARQUEE }
```

| 输入 | 行为 |
| --- | --- |
| 左键按下（无修饰）+ 拖拽 | `MARQUEE`：框选区块矩形（区块模式）或框选实体（实体模式） |
| 空格按住 + 左键拖拽，或中键拖拽，或右键拖拽 | `PAN`：平移 `panX/panY` |
| 滚轮 | `zoomAt(mouseX, mouseY, ±0.1)`，围绕光标缩放（复用 `PrecisionEditorGeometry.zoomAt`）；同时触发瓦片精度重估 |
| 左键单击（无拖拽，区块模式） | 切换该区块的选中态（增量选择） |
| 左键单击（实体模式） | 选中该实体（单选）；再次点击取消 |
| 右键单击 | 清空当前选区 |
| 拖动中 | 实时更新选区矩形与预览；状态栏实时显示区块数与形状 |

- `MARQUEE` 需要 `SELECTION_DRAG_THRESHOLD = 3.0` 的位移阈值区分「点击」与「拖拽」（对齐 `ModularProgramScreen`）。
- 选区超限（> `max_region_chunks`）时边框转 `DataTerminalTheme.DANGER`，并在校验面板说明。
- 平移/缩放后**去抖 150 ms** 再发视图请求（§4.3），避免拖动过程中请求风暴。

### 3.7 信息面板、校验与提交

校验项（全部满足才启用「确认交换」）：

| 校验 | 规则 | 提示 |
| --- | --- | --- |
| 等额 | `|源| == |目标|` | 需要等额区块 |
| 形状 | 源/目标选区均为**轴对齐矩形**且 `宽×高` 相同 | 形状不一致 |
| 不重叠 | 同维度时两矩形不相交 | 选区重叠 |
| 上限 | `|源| ≤ max_region_chunks` | 超出上限 |
| 已加载 | 两选区全部区块已达到 `FULL` 且瓦片已收到 | 正在加载 x/y |
| 预加载 | 目的地（含玩家落点）预加载完成 | 预加载中 |
| 余额 | 本地预估 CP ≤ 可用 CP，且技能已学习/启用 | CP 不足 / 未学习 |

- 实体模式：面板替换为目标坐标输入（`TextBoxWidget`，格式 `x y z`）+ 「传送到此」按钮；地图上左键选点即为目标位置；校验项变为「目标区块已加载」。
- `confirm_swap = true` 时，「确认交换」先弹二次确认（同一 `UiScreen` 内的 `BlendQuadWidget` 覆盖层，不引入新 Screen）。
- 提交后立即关闭地图（或保持打开但显示进度，推荐**关闭并显示 VFX**，见 §7.4）。

---

## 4. 网络协议（Misaka）

### 4.1 包清单

所有包沿用仓库现有 Misaka 模式：`PacketType` 在 `PacketTypes` 注册；`@PacketTarget(ThreadType.CLIENT|SERVER)`；`StreamCodec` 用 `StreamCodec.composite` 或手写 `StreamCodec.of`（变长列表参照 `LocationTeleport.MarksSyncPacket` / `AreaTeleportSelect.SyncPacket`）；处理器类 `Client`/`Server` 内 `@SubscribePacket static void handle(...)`，在 `initClient`/`initServer` 里 `NETWORK_MANAGER.register(...)`。

**C2S（客户端 → 服务端）**

| PacketType 名 | 类 | 字段 | 说明 |
| --- | --- | --- | --- |
| `chunk_leap_view_request` | `ViewRequestPacket` | `dimension(ResourceKey<Level>)`, `minChunkX/Z`, `maxChunkX/Z`(矩形), `detail(byte)`, `epoch(int)` | 申请/更新某维度的地图视图；服务端按此建立/收敛租约 |
| `chunk_leap_view_release` | `ViewReleasePacket` | `dimension` | 离开该维度页/关闭地图时释放租约 |
| `chunk_leap_swap` | `SwapRequestPacket` | `dimA`, `minA`, `sizeA(w,h)`, `dimB`, `minB`, `sizeB(w,h)`, `opId(UUID)` | 等额区块交换请求（跨维度允许） |
| `chunk_leap_entity_tp` | `EntityTeleportPacket` | `entityId(varInt)`, `dimension`, `BlockPos target`, `opId(UUID)` | 将指定实体传送到目标位置 |
| `chunk_leap_cancel` | `CancelPacket` | `opId(UUID)` | 取消进行中的交换（若尚未进入不可中断阶段） |

**S2C（服务端 → 客户端）**

| PacketType 名 | 类 | 字段 | 说明 |
| --- | --- | --- | --- |
| `chunk_leap_tiles` | `MapTilesPacket` | `dimension`, `epoch(int)`, `List<TileEntry>`；`TileEntry = (long chunkKey, byte flags, byte[] colors)` | 批量瓦片；`flags` 位：已加载/生成中/失效 |
| `chunk_leap_entities` | `MapEntitiesPacket` | `dimension`, `List<Marker>`；`Marker = (varInt entityId, byte category, varInt typeId, int blockX, int blockZ)` | 可见区域实体标记（限频） |
| `chunk_leap_view_status` | `ViewStatusPacket` | `dimension`, `loaded(int)`, `total(int)` | 加载进度（驱动进度条） |
| `chunk_leap_swap_result` | `SwapResultPacket` | `opId`, `success(bool)`, `reasonKey`, `List<(dim, chunkKey)> affected` | 交换结果 + 需要失效的瓦片集合 |
| `chunk_leap_swap_progress` | `SwapProgressPacket` | `opId`, `done`, `total` | 大区域交换的分步进度 |
| `chunk_leap_teleport_result` | `TeleportResultPacket` | `success`, `reasonKey`, `dimension`, `BlockPos` | 实体传送结果 |
| `chunk_leap_target_ready` | `TargetReadyPacket` | `dimension`, `minChunkX/Z`, `ready(bool)` | 目的地预加载完成（解锁「确认交换」） |

### 4.2 codec 要点

- **瓦片编码**：`flags(byte) + detail×detail` 字节；`detail` 随 `MapTilesPacket` 头统一给出，避免每瓦片重复。颜色为 `MapColor` 复合后的 RGB → 单字节索引或 3 字节 RGB 直存（推荐 3 字节直存，省去调色板同步；8×8×3 = 192 B/chunk，64 瓦片 ≈ 12 KB/包）。
- **批量上限**：`MapTilesPacket` 每包 ≤ 64 个瓦片；`MapEntitiesPacket` 每包 ≤ 256 个标记，超出分行。
- **维度键**：使用 `ResourceKey<Level>` 的 `ResourceLocation` 流编解码（`ResourceLocation.STREAM_CODEC` / `ByteBufCodecs` 组合），不要自定义维度 id 映射。
- **`opId`**：`UUID`，用于把异步结果与请求配对并抑制过期回包（客户端丢弃未知/已关闭的 `opId`）。
- **解码防御**：所有列表长度、选区尺寸、`detail`、坐标范围在解码时**夹取**（参照 `LocationTeleport.MarksSyncPacket` 的做法），拒绝退化/恶意包。

### 4.3 请求节流与增量

- **视图请求去抖**：pan/zoom 停止 150 ms 后才发 `ViewRequestPacket`。
- **增量**：客户端保存「上次已请求矩形」，只发**差集**（expand 的部分）与精度档位变化；`epoch` 用于服务端识别新会话（新 `epoch` 时重建租约，旧租约立即释放）。
- **优先级由近到远**：服务端把瓦片任务按「距视图中心距离」排序，保证中心先可见。
- **在途上限**：客户端同时最多 2 个未完成的视图请求；服务端每玩家每 tick 生成的瓦片数有预算（§5.4）。
- **实体包限频**：`entity_refresh_ticks`（默认 10 tick）一包；仅在可见区域或选中实体变化时提前推送。

---

## 5. 服务端：地图数据服务

### 5.1 视图租约与区块加载（"自行加载"）

新增 `ChunkMapViewService`（单例，`ServerTickEvent.Post` 驱动）+ 通用化 `ChunkTicketLeaseManager`。

**为什么不直接用现有 `TeleportChunkForceManager`**：它用 `setChunkForced`（会 `getChunk` 同步阻塞）且**每 lease 硬上限 64 个区块**（`forceRegion` 第 41 行 `keys.size() < 64`），无法覆盖地图视野；且 `TicketType.FORCED` 带 `FLAG_PERSIST=1`，会把地图探索结果写进磁盘票据。因此：

1. 保留 `TeleportChunkForceManager` 现有 API（`LocationTeleport` 等仍在使用），在其之上增加**基于 Ticket 的通用租约管理器**：

```java
public final class ChunkTicketLeaseManager {
    // 自建 TicketType：只加载、不模拟、不持久化
    public static final DeferredRegister<TicketType> TICKET_TYPES =
            DeferredRegister.create(BuiltInRegistries.TICKET_TYPE, AcademyCraft.MOD_ID);
    public static final DeferredHolder<TicketType, TicketType> MAP_VIEW =
            TICKET_TYPES.register("chunk_leap_view",
                    () -> new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
    //                                        ↑ 无超时（显式释放）  ↑ doesLoad=true, 不模拟/不持久化

    /** 申请矩形区域；level = FULL（可读取方块），不进入实体 tick。 */
    public static Lease acquire(ServerLevel level, String owner, ChunkRect rect, int maxChunks);

    /** 返回整批加载完成的 future（绝不阻塞主线程）。 */
    public static CompletableFuture<Void> acquireAndLoad(ServerLevel level, String owner,
                                                        ChunkRect rect, int maxChunks);

    public static void release(String owner);
    public static void releaseOwner(Object ownerKey);   // 玩家登出/关屏
}
```

2. 加载语义（已核验）：
   - `ServerChunkCache.addTicketWithRadius(TicketType, ChunkPos, int)`（public）
   - `ServerChunkCache.addTicketAndLoadWithRadius(TicketType, ChunkPos, int)`（public，返回 `CompletableFuture<?>`，**异步**）
   - Ticket 等级取 `ChunkLevel.byStatus(FullChunkStatus.FULL)`，即「已完整生成、可读取方块、但不 tick 实体」——正是地图需要且最省的档位。
3. **不会向客户端灌真实地形**：`ChunkMap.isChunkTracked(player, x, z)` 要求 `player.getChunkTrackingView().contains(...)`；地图视野通常远大于玩家视距，因此被地图加载的远处区块**不会**触发 `markChunkPendingToSend`，玩家不会被真实区块包淹没——地图数据完全走我们自己的轻量瓦片包。
4. 租约边界与生命周期：
   - 每玩家每维度至多 1 个租约；切维度页时 `release` 旧租约；
   - `view_radius_chunks` 上限 48，`max_region_chunks` 上限 1024，硬夹取；
   - 关闭地图（`onClose`）/玩家登出（`PlayerLoggedOutEvent`）/玩家换维度时释放；
   - `ServerStoppingEvent` 全量释放（对齐现有 `TeleportChunkForceManager.onServerStopping`）；
   - 每个 tick 只推进有限数量的 ticket 申请，避免一次性申请上千区块造成卡顿；已申请集合去重。

### 5.2 瓦片生成

`MapTileBuilder`（服务端）：

```
for (x, z) in chunk:
    height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)   // 已提供，缺失时自动 prime
    state  = level.getBlockState(pos(x, height, z))
    if state.getFluidState() 非空 且 顶层是空气 → 用流体自身颜色
    color  = state.getMapColor(level, pos).calculateARGBColor(...)  // 或 col 字段
```

- 采样分辨率由请求的 `detail` 决定（4/8/16），并对每个采样点在 chunk 内做 `Heightmap` 查询；`Heightmap` 缺失时 vanilla 会自动 `primeHeightmaps`（已核验 `ChunkAccess.getHeight`）。
- 顶层取「非空气最高方块」；水下取流体色并做深度压暗（近似 vanilla 地图观感）。
- 瓦片缓存：`MapTileCache` 键 `(level.dimension(), chunkKey)`，值 `byte[] + version`。
  - 失效来源：① 区块交换（§6）；② P3 阶段的方块变更（可监听 `BlockEvent.BreakEvent`/`BlockEvent.EntityPlaceEvent` 标记脏区块，或按 epoch 定期失效）；
  - 失效后仅重发该瓦片，不整屏刷新。
- 生成预算：`MapTileBuildScheduler` 每 tick 至多 `N`（默认 64）个瓦片、并按毫秒预算（`Util.getMillis()` + `AcademyProfiler.zone("academy.chunk_leap.tile")`）提前让出，防止抢占 tick。
- 未加载区块：若 `auto_load_map = true` 则由租约触发加载并在就绪后按需生成；否则直接回「未加载」flags 占位。

### 5.3 实体索引

`MapEntityIndex`（服务端，每玩家每维度限频刷新）：

```java
var aabb = new AABB(minX << 4, level.getMinY(), minZ << 4, (maxX + 1) << 4, level.getMaxY(), (maxZ + 1) << 4);
var list = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> !(e instanceof ServerPlayer) || includePlayers);
```

- 分类映射：`category ∈ {HOSTILE, PASSIVE, NEUTRAL, PLAYER, ITEM, PROJECTILE}`，由 `MobCategory`/`EntityType` 判断；仅下发分类 + 类型 id（用于图标与本地预览），不下发完整实体数据。
- 坐标下发**区块内相对偏移**或绝对方块坐标（推荐绝对方块坐标的 varint 差分编码，行内按 x 排序后差分，压缩率好）。
- 限频由 `entity_refresh_ticks` 控制；如果玩家正把地图打开在某维度页，只刷新该维度。

### 5.4 速率与预算

| 资源 | 预算 |
| --- | --- |
| 每 tick 瓦片生成 | 64 个 / 毫秒预算提前让出 |
| 每 tick 新区块加载申请 | 32 个（租约推进队列） |
| 每包瓦片数 | 64 |
| 实体包频率 | 每 `entity_refresh_ticks`（默认 10 tick） |
| 单玩家单维度租约 | 1 |
| 租约区块总数上限 | `max_region_chunks`（默认 256，硬上限 1024） |

---

## 6. 服务端：区块交换

### 6.1 语义定义

- 交换对象：**等额的轴对齐矩形区块集合**，`源.宽×高 == 目标.宽×高`。
- 允许跨维度；同一维度时两矩形**禁止相交**。
- 交换内容：区块内的**方块、生物群系、方块实体、实体、计划刻（尽力）、高度图**。玩家默认随区块位移（可关）。
- 禁止条件：任一区块包含**已加载的玩家且 `move_players_with_chunks=false`**（避免把人留在原地导致掉入虚空/卡墙）；任一区块含水/岩浆等流体的跨界连带（首期不做流体修正，交换后由 vanilla 流体 tick 自行收敛）；超出体积上限。
- 越界/未加载：由 §5.1 的租约在提交前把两侧全部加载到 `FULL`；未就绪则拒绝并返回 `TargetReadyPacket(ready=false)`。

### 6.2 交换核心算法

**关键事实（已核验）**：
- `ChunkAccess.getSections()` 返回的**就是内部数组**（非副本），可直接写索引；
- `LevelChunkSection` 自包含（方块 `PalettedContainer` + 生物群系 `PalettedContainerRO` + 4 个计数），有 `copy()` 与 `write(FriendlyByteBuf)/read(FriendlyByteBuf)`；
- 因此**绝不能**用 `setBlockState` 逐块搬 65536 个方块——那会引发 65536 次邻居更新、光照重算与客户端包风暴。

**方案 A：section 指针交换（推荐，同维度且两侧段数一致）**

```java
LevelChunkSection[] sa = chunkA.getSections();
LevelChunkSection[] sb = chunkB.getSections();
// 前置校验：levelA.getSectionsCount() == levelB.getSectionsCount()
for (int i = 0; i < sa.length; i++) { var t = sa[i]; sa[i] = sb[i]; sb[i] = t; }
```

复杂度 `O(段数)`（24 或 16 次引用交换），零拷贝、零序列化。

**方案 B：按绝对 Y 配对 + 字节回环交换内容（跨维度/段数不同）**

主世界（minY=-64，24 段）与下界（minY=0，16 段）段数不同，指针交换会把「地表」错位。改为按**绝对断面 Y** 配对，用 `FriendlyByteBuf` 回环交换 section 内容：

```java
// 对两侧共有的绝对 Y 段：交换内容（含方块、生物群系、计数）
var buf = new FriendlyByteBuf(Unpooled.buffer(1024));
sectionA.write(buf);
sectionA.read(otherBufOfB);   // B 的内容写入 A
sectionB.read(buf);           // A 的旧内容写入 B
// 仅一侧存在的断面：整段迁移（copy() 后写入对方对应绝对 Y 的段），空侧填空段
```

- `read` 会从流中恢复 `nonEmptyBlockCount`/`fluidCount` 与生物群系（内部 `recreate()` 后 `read`），已核验不会丢状态。
- 代价：每对 section 约几十 KB 的**瞬态堆外缓冲**，一个 24 段的区块约 1–2 MB 级别的瞬时分配，远低于逐块方案的代价；且可复用同一个 `FriendlyByteBuf` 复用/Pooled 化。
- 生物群系随方块一起交换——这是「区块交换」的期望语义，需在文档中明确。

**方案 C（不采用）**：逐块 `setBlockState(pos, otherState, BULK_UPDATE_FLAGS)`。放弃理由：性能（`O(方块数 × 邻居更新)`）、光照、包量、以及 `BlockStructureSnapshot` 的 4096 方块上限（`src/main/java/org/academy/api/common/structure/BlockStructureSnapshot.java:27`）根本装不下一个区块。

### 6.3 必须重建的附属状态（**最容易出错的部分**）

交换 section 只是第一步。以下每一项都要处理，否则会出现光照错误、幽灵方块实体、实体丢失或存档损坏：

| 状态 | 处理 | 依据 / 备注 |
| --- | --- | --- |
| 方块实体（BE） | 两侧先 `saveWithFullMetadata(registryAccess)` 收集 `(相对坐标, NBT)` → 清空 `blockEntities` 与 ticker → 交换后按新绝对坐标 `BlockEntity.loadStatic(newPos, state, tag, registries)` 重建 → `LevelChunk.addAndRegisterBlockEntity(be)` | `LevelChunk.clearAllBlockEntities()` / `addAndRegisterBlockEntity()` 均为 public |
| 方块实体 ticker | 由 `addAndRegisterBlockEntity` 内部重建（`updateBlockEntityTicker`） | `LevelChunk.java:399、714` |
| 游戏事件监听器（幽匿等） | 重建 BE 时重新 `addGameEventListener`；`getListenerRegistry(sectionY)` 需清理旧注册 | `LevelChunk.addGameEventListener`（private，需 AT 或走 `addAndRegisterBlockEntity`） |
| 实体 | 交换前枚举两侧区块内实体（按 `entity.blockPosition()` 归属）→ 交换方块后按 delta 迁移 → `TeleportSync.teleportInstantly(entity, destLevel, newPos)` | `EntityGetter.getEntities(except, aabb, sel)`；跨维度由该 helper 处理 `teleportTo` + 同步包 |
| 玩家 | 若玩家所在区块被交换且开关开启：在方块落地后，用 `TeleportSync.teleportInstantly(player, destLevel, oldPos + delta, yaw, pitch)` 随区块位移 | 同维度即纯位置包（无缝）；跨维度走 §7 |
| 计划刻（方块/流体） | 尽力重定位：`((LevelChunkTicks<Block>) chunk.getBlockTicks()).getAll()` 读出 → 按 delta 重建 `ScheduledTick` → `schedule` 到对方 → 源侧 `removeIf(t -> true)` | `LevelChunkTicks.getAll()/schedule/removeIf` public，但 `ChunkAccess.getBlockTicks()` 返回 `TickContainerAccess`，需**向下转型或加 AT**；P1 可先整体清空并标注 |
| 高度图 | `Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Types.class))` 重算 | 或逐列 `chunk.setHeightmap(type, raw)`；推荐重算 |
| 天空光列缓存 | `chunk.initializeLightSources()`（内部 `skyLightSources.fillFrom(this)`） | `ChunkAccess.java:483` |
| 光照 | `chunk.setLightCorrect(false)` → `lightEngine.retainData(pos, true)` → `lightEngine.setLightEnabled(pos, false)` → `setLightEnabled(pos, true)` → 对自身与 4 邻居 `propagateLightSources(pos)` | `LevelLightEngine.setLightEnabled/retainData/propagateLightSources/updateSectionStatus` 均 public；`setLightEnabled(false→true)` 是「丢弃并重算该区块光照」的标准廉价手段 |
| 客户端可见性 | 向 `chunkMap.getPlayers(chunkPos, false)` 发送新的 `ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null)`（**整区块重发**，不做逐块包） | `ChunkMap.getPlayers(ChunkPos, boolean)` public；`PlayerChunkSender.sendChunk` 同款构造 |
| 存档 | `chunk.markUnsaved()`；两侧区块都会被 vanilla autosave 正常写入 | 方块数据本身走原版区块存档，无需自定义持久化 |
| POI（村民/工作站等） | P2/P3：同维度可用 `PoiManager` 的 section 重扫 API 重建；跨维度需在目标维度重扫。首期如不做，需在文档标注「村民工作站绑定可能失效」并提供开关 | 明确的已知限制 |
| 结构引用（`structures`） | 交换后区块的 structure start/reference 指向旧内容。建议**清空被交换区块的 structure 数据**并标注（避免错误的结构后续行为） | `ChunkAccess` 结构访问器 |
| NeoForge 区块附件 | 视为「位置属性」保留不动；如需随内容走，按需在 P3 交换 | `AttachmentHolder` |
| `hasChangedSections` / 脏块集合 | 因为整区块重发，无需逐个 `ChunkHolder.blockChanged`；为防止后续陈旧逐块包，可显式清空 | `ChunkHolder.java:118-215` |

> 一个务实的简化：对**同一对区块**，把「section 交换 + BE 重建 + 计划刻 + 实体迁移 + 光照 + 重发」放在**同一个 tick 内原子完成**（见 §6.5），把需要 `O(1)` 之外工作的部分（实体搬运的包发送、光照的 `runLightUpdates`）交给 vanilla 在后续 tick 自然摊销。

### 6.4 实体与玩家随区块迁移

- 枚举：`level.getEntities(null, chunkAABB, e -> e.blockPosition() 落在该区块)`；**排除玩家**单独处理。
- 迁移：目标坐标为 `newPos = oldPos - 源原点 + 目标原点`（跨维度时 X/Z 按目标原点平移，Y 不变或按 y 夹取到目标维度高度范围）。
- 载具/乘客：以根实体为单位，先处理 `getRootVehicle()`，乘客随之（`TeleportSync.teleportInstantly` 会影响整个骑乘链；需显式遍历 `getSelfAndPassengers` 校验）。
- 跨维度：`TeleportSync.teleportInstantly(entity, destLevel, newPos)` 已处理 `entity.teleportTo(level, ...)` 并给观察者发 `InstantTeleportSyncPacket`（`TeleportSync.java:47-108`），**直接复用，不要另写**。
- 玩家：最后处理，且在 §6.3 的方块落地之后；`TeleportSync` 传 `yaw/pitch` 并保留视角（`preserveViewRotation=true` 版本）。

### 6.5 事务、时间切片与崩溃日志

- `ChunkSwapOperation`：一次交换的完整状态（请求、两侧配对表、阶段、已处理索引、受影响区块集合、`opId`）。
- 阶段：`VALIDATE → LOAD → APPLY(逐对, 原子) → RELIGHT/RESEND → ENTITIES/PLAYERS → DONE`。
- **时间切片**：`APPLY` 按**区块对**分片，`ChunkSwapService.onServerTick` 每次取预算内的若干对（以毫秒预算为准，默认 2 ms/对上限），每完成一批发 `SwapProgressPacket`。因为单对是原子的，玩家永远不会看到「只换了一半的区块对」。
- **崩溃安全**：`ChunkSwapJournalSavedData`（`SavedDataType`，存于 `server.overworld().getDataStorage()`，编码参照 `SpatialStorageSavedData`）：
  - 进入 `APPLY` 前写入 `{opId, dimA, dimB, pairs, phase}`；`DONE` 后删除。
  - 服务器启动时若发现未完成记录：对涉及的区块强制 `markUnsaved + 重光照 + 整区块重发`，并把两侧的原始 NBT 快照（若在 P3 阶段引入）用于回滚；P1 只做「检测 + 重光照 + 重发 + 控制台告警」，并在文档中明确取舍。
- **回滚**：`VALIDATE/LOAD` 阶段失败无需回滚（未改世界）；`APPLY` 之后失败按「继续完成 + 报错」而非回滚，因为半交换状态在语义上比「中断」更糟；若必须回滚，同一算法再执行一次反向交换即可（交换是自逆操作），这是该设计的一个优点。

### 6.6 客户端失效

- `SwapResultPacket.affected` 携带受影响区块集合；客户端 `ChunkMapClientState.invalidateTiles(dim, chunks)` 清缓存并重新请求这些瓦片。
- 若玩家自身被随区块传送：客户端会先收到 `ClientboundPlayerPositionPacket`（同维度）或 respawn（跨维度），再收到失效与瓦片重发；地图重开后即为新布局。

---

## 7. 无缝跨维传送（预加载）

### 7.1 为什么「卡」：已核验的原版流程

| 场景 | 原版行为 | 是否无缝 |
| --- | --- | --- |
| **同维度**传送 | `ServerPlayer.teleport` 同维度提前返回，只发 `ClientboundPlayerPositionPacket`（`ServerPlayer.java:1144-1151`） | ✅ 天然无缝，**不重建 ClientLevel** |
| **跨维度**传送 | 发 `ClientboundRespawnPacket`（`ServerPlayer.java:1155`）→ `setServerLevel` → `PlayerList.sendLevelInfo` 发 `LEVEL_CHUNKS_LOAD_START` → 客户端 `handleRespawn` 重建 `ClientLevel` → `startWaitingForNewLevel` → `LevelLoadingScreen`，`LevelLoadTracker` 在 `isLevelReady()` 后才关闭（`LEVEL_LOAD_CLOSE_DELAY_MS = 500`，30s 超时） | ⚠️ 必然出现加载界面 |

因此「无需进入加载界面」的可行实现是：**保留握手、去掉画面**。绝不能不发生 `ServerboundPlayerLoadedPacket`——否则服务端 `ServerGamePacketListenerImpl.hasClientLoaded()` 保持 false，玩家会被拒绝移动/交互。

### 7.2 预加载（把等待挪到地图打开期间）

1. 地图打开、玩家在地图上浏览目标区域时，§5.1 的视图租约**已经**把目标区块加载到 `FULL` 并完成生成/落盘；
2. 提交前，`ChunkTicketLeaseManager.acquireAndLoad(...)` 对**目的地矩形 + 玩家落点区块 + 其 3×3 邻域**做一次显式异步加载，用 `CompletableFuture` 汇总；
3. `CompletableFuture` 完成后服务端发 `TargetReadyPacket`，GUI 的 `preload_progress` 到 100% 才启用「确认交换」；
4. 由于区块已在内存且 `FULL`，`PlayerChunkSender.sendNextChunks` 只需推送**网络传输**，不再有生成/加载耗时；客户端落地时地形基本已到。

> 关键：**绝不在主线程 `join()`**。`ServerChunkCache.getChunk(x, z, ChunkStatus.FULL, true)` 在非主线程会阻塞等待主线程（`ServerChunkCache.java:148-172`），地图服务必须走 `addTicketAndLoadWithRadius` / `getChunkFuture` 的异步路径。

### 7.3 隐藏加载界面（NeoForge 官方事件，无需 Mixin）

```java
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ChunkLeapTransition {
    @SubscribeEvent
    public static void onRegisterTransitionScreens(RegisterDimensionTransitionScreenEvent event) {
        if (!ChunkLeap.Client.CONFIG.seamlessTransition()) return;
        event.registerIncomingEffect(Level.OVERWORLD, SeamlessLoadingScreen::new);
        event.registerIncomingEffect(Level.NETHER,    SeamlessLoadingScreen::new);
        event.registerIncomingEffect(Level.END,       SeamlessLoadingScreen::new);
    }
}

/** 不绘制原版加载画面；改为绘制本模组的传送 VFX，把 500ms 窗口变成「有意为之」的演出。 */
public final class SeamlessLoadingScreen extends LevelLoadingScreen {
    public SeamlessLoadingScreen(LevelLoadTracker tracker, LevelLoadingScreen.Reason reason) { super(tracker, reason); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) { /* 紫色跃迁涡旋 + 技能名 */ }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) { /* 不绘制原版背景 */ }
    @Override public boolean isPauseScreen() { return false; }
}
```

- 已核验 API：`net/neoforged/neoforge/client/event/RegisterDimensionTransitionScreenEvent` 提供 `registerConditionalEffect(to, from, factory)` / `registerIncomingEffect(dim, factory)` / `registerOutgoingEffect(dim, factory)`；`ReceivingLevelScreenFactory { LevelLoadingScreen create(LevelLoadTracker, LevelLoadingScreen.Reason); }`（NeoForge 26.2.0.70 源）。
- **为什么不直接 Mixin 取消**：取消 `ClientPacketListener.startWaitingForNewLevel` 会绕过 `LevelLoadTracker`，导致 `ServerboundPlayerLoadedPacket` 永不发送，服务端拒绝玩家操作。用事件替换画面是**保留握手、只改观感**，风险最低且不需 Mixin。
- **兜底（仅当实测仍有明显白屏/停顿）**：再考虑
  `@Mixin(LevelLoadTracker.class)` 在 `isLevelReady()` 提前返回 true，并在客户端主动发送 `ServerboundPlayerLoadedPacket`；
  或 `@Mixin(ClientPacketListener.class)` 精确跳过 `setScreenAndShow`。**列为 P3 可选，不作为首选**，因为破坏了原版状态机的不变量。

### 7.4 端到端时序

```
[玩家] Alt+C → 打开地图
        │
        ├─ C2S ViewRequest(源维度, 目的维度) ───────────────► 服务端
        │                                                     ├─ ChunkTicketLeaseManager.acquireAndLoad  → 加载至 FULL
        │                                                     ├─ MapTileBuilder 分片生成瓦片
        │  ◄── S2C MapTiles / MapEntities / ViewStatus ───────┤
        │  地图显示；选区框选；校验全绿
        │
        ├─ 点击「确认交换」（此时目的地区块已在内存）
        │  C2S SwapRequest(opId) ────────────────────────────► 服务端
        │                                                     ├─ 校验 + 计费(executeActive)
        │                                                     ├─ SwapJournal 写入
        │                                                     ├─ APPLY：逐对原子（section/BE/刻/实体）
        │  ◄── S2C SwapProgress ──────────────────────────────┤
        │                                                     ├─ 光照 / 整区块重发
        │                                                     ├─ 实体迁移（TeleportSync）
        │                                                     └─ 玩家随区块位移（TeleportSync）
        │  ◄── 同维度：ClientboundPlayerPositionPacket ────────┤  ← 无缝，无界面
        │  ◄── 跨维度：ClientboundRespawnPacket → LevelLoadTracker
        │        + SeamlessLoadingScreen（不可见/仅 VFX，≥500ms）
        │  ◄── S2C SwapResult(affected) ──────────────────────┤
        │  地图失效受影响瓦片并重取；释放租约
```

---

## 8. 工具类清单

### 8.1 新增（服务端）

| 类 | 职责 | 关键 API |
| --- | --- | --- |
| `ChunkLeap` | 技能定义 | `Skill`, `executeActive` |
| `ChunkMapViewService` | 视图租约生命周期、瓦片/实体推送调度、登出清理 | `ServerTickEvent.Post`, `PlayerLoggedOutEvent` |
| `ChunkTicketLeaseManager` | 通用 ticket 租约（跨维度、>64 区块、异步） | `ServerChunkCache.addTicketWithRadius/addTicketAndLoadWithRadius`, `TicketType`, `ChunkLevel.byStatus` |
| `MapTileBuilder` | 8×8/16×16 瓦片采样与编码 | `ChunkAccess.getHeight(Heightmap.Types, x, z)`, `BlockState.getMapColor(...)` |
| `MapTileBuildScheduler` | 每 tick 预算内的瓦片生成队列 | `Util.getMillis()`, `AcademyProfiler` |
| `MapTileCache` | `(dim, chunkKey) → (byte[], version)` 缓存与失效 | — |
| `MapEntityIndex` | 可见区实体分类与编码 | `EntityGetter.getEntitiesOfClass(...)` |
| `ChunkSwapService` | 交换任务调度、时间切片、进度上报、日志恢复 | `ServerTickEvent.Post` |
| `ChunkSwapOperation` | 单次交换的事务状态机 | — |
| `ChunkRegionSwap` | 单对区块的原子交换（方案 A/B、附属状态重建） | §6.2/§6.3 |
| `ChunkRelightUtil` | 光照与天空光列重建 | `LevelLightEngine.setLightEnabled/retainData/propagateLightSources`, `ChunkAccess.initializeLightSources`, `Heightmap.primeHeightmaps` |
| `ChunkResender` | 整区块包重发 | `ChunkMap.getPlayers(ChunkPos, boolean)`, `ClientboundLevelChunkWithLightPacket` |
| `ChunkEntityTransfer` | 实体/玩家随区块迁移 | `TeleportSync.teleportInstantly`, `Entity.getSelfAndPassengers` |
| `ChunkSwapJournalSavedData` | 崩溃恢复日志 | `SavedDataType`, `Codec`（参照 `SpatialStorageSavedData`） |
| `ChunkKeyCodec` | 区块坐标/矩形编解码与夹取（双端复用） | `ChunkPos`, `StreamCodec` |
| `ChunkLeapCost` | 计费/预估单一实现（双端复用） | — |

### 8.2 新增（客户端）

| 类 | 职责 |
| --- | --- |
| `ChunkLeapScreen` | `UiScreen`，布局 + 画布 + 输入 + 面板 |
| `ChunkMapClientState` | 每维度瓦片表、实体标记、epoch、待请求集合、LRU 淘汰 |
| `ChunkTileAtlas` | 动态瓦片图集打包与子区域上传（`SkylineAllocator` 思路 + `UiEnvironment.createDynamicTextureSource`） |
| `ChunkMapRenderer` | 可见区块矩形迭代、瓦片四边、网格、选区、标记、坐标/生物群系读数 |
| `ChunkMapInputController` | pan/zoom/marquee/entity-pick 状态机 |
| `ChunkMapHud` | 底部队列条/进度（如需地图外轻量提示） |
| `ChunkLeapConfig` | `extends KeyBindingConfig` + 设置模块 |
| `ChunkLeapTransition` | 注册 `SeamlessLoadingScreen` |
| `SeamlessLoadingScreen` | 不可见/仅 VFX 的 `LevelLoadingScreen` 子类 |
| `TeleportPreloadTracker` | 目的地预加载进度与「可执行」门控 |

### 8.3 直接复用（不要重写）

| 能力 | 复用 |
| --- | --- |
| 平移/缩放/框选几何 | `PrecisionEditorGeometry`（`screenToGraph/graphToScreen/zoomAt/selectionBounds`） |
| 画布绘制与视口裁剪范式 | `ModularProgramScreen`（`renderCanvas`/`renderCanvasGrid`/`SelectionDrag`） |
| 实体框选与投影范式 | `WideAreaInterferenceScreen`（`projectBounds`/`selectionBounds`/`dragMode`） |
| 传送执行与同步 | `TeleportSync.teleportInstantly(...)` |
| 目标区块预加载 | `TeleportChunkForceManager.forceChunk/forceRegion`（保留兼容） |
| 面板配色与绘制 | `DataTerminalTheme`（`TELEPORT_ACCENT`/`TELEPORT_SELECTED`/`panel/border/button`） |
| 结构面板背景 | `BlendQuadWidget` |
| 布局加载与调试 | `SerializedUiLayout` + `SerializedUiDebugHost`（自动获得 ImGui 布局编辑器） |
| 文案/本地化 | `Component.translatable` + `L10n` |
| 性能计时 | `AcademyProfiler` |
| 持久化范式 | `SpatialStorageSavedData` 的 `SavedDataType` + `Codec` |

---

## 9. 性能优化汇总

1. **交换复杂度**：`O(段数)` 引用交换（方案 A）或按绝对 Y 的字节回环（方案 B），**彻底避免** `O(方块数)` 的 `setBlockState`。
2. **交换不产生逐块包**：整区块重发（每区块 1 个 `ClientboundLevelChunkWithLightPacket`），而非每方块 1 个 `ClientboundBlockUpdatePacket`。
3. **交换时间切片**：按区块对分片 + 毫秒预算，逐 tick 推进并上报进度，玩家感知为「逐步生效」而非一次性冻结。
4. **地图瓦片**：单张图集 + 单 pipeline + 单 scissor → 可见区块整体合并为极少数 draw call；脏瓦片只重写子区域。
5. **带宽**：增量视图请求（只发差集）、去抖 150 ms、每包 ≤64 瓦片、实体包限频、低倍率自动降精度（LOD）。
6. **服务端生成预算**：每 tick 瓦片数 + 毫秒双预算，`AcademyProfiler` 分区计时，超预算立即让出。
7. **加载异步化**：只走 `addTicketAndLoadWithRadius`/`getChunkFuture`，**绝不主线程 join**；租约申请也分 tick 推进。
8. **不污染客户端视距**：租约加载的远处区块因 `ChunkTrackingView` 不含其坐标，不会触发真实地形下发（`ChunkMap.isChunkTracked` 已核验），地图数据与真实地形互不干扰。
9. **光照摊销**：`setLightEnabled(false→true)` 丢弃并重算，`runLightUpdates` 由 vanilla 在后续 tick 摊销，不在交换 tick 内阻塞。
10. **租约生命周期严格**：关屏/登出/换维度/停服即释放；客户端瓦片表 LRU 淘汰；服务端瓦片缓存带版本失效。
11. **内存边界**：`max_region_chunks`（默认 256，硬上限 1024）、`view_radius_chunks`（默认 24，上限 48）双重夹取，杜绝「选满世界」把服务器拖垮。
12. **计费与体积联动**：CP 随体积线性增长，天然抑制滥用；本地预估与服务端核算共用 `ChunkLeapCost`，避免不一致。

---

## 10. 关键常量

| 常量 | 值 | 位置 |
| --- | --- | --- |
| 技能 id | `academy:chunk_leap` | `SkillNames` |
| 推荐等级 / IF / 基础 CP / 迭代 | `LEVEL5` / `100_000` / `200` / `20 tick` | `ChunkLeap` |
| 计费公式 | `100 + 4×区块数 + 30×实体数` | `ChunkLeapCost` |
| 全局键 | `Alt+C↓` | `ChunkLeapConfig` |
| 视图请求去抖 | `150 ms` | `ChunkMapInputController` |
| 瓦片精度 | `4 / 8 / 16`（默认 8） | `ChunkLeapConfig.tile_detail` |
| 单包瓦片上限 | `64` | `MapTilesPacket` |
| 每 tick 瓦片生成 | `64` + 毫秒预算 | `MapTileBuildScheduler` |
| 租约上限 | `view_radius_chunks ≤ 48`；`max_region_chunks ≤ 1024` | `ChunkTicketLeaseManager` |
| 实体刷新 | `10 tick` | `ChunkLeapConfig.entity_refresh_ticks` |
| 交换分片预算 | `2 ms/对`（可调） | `ChunkSwapService` |
| 预加载邻域 | 目的地 + 玩家落点 3×3 区块 | `TeleportPreloadTracker` |

---

## 11. 测试与验证计划

**单元测试（JUnit 5，`src/test/java`，与生产包路径对齐）**

- `ChunkLeapCostTest`：体积/实体数与 CP 的边界、夹取、双端一致性。
- `ChunkKeyCodecTest`：矩形编解码、非法/越界输入夹取、跨维度维度键往返。
- `MapTileBuilderTest`：给定方块/高度图 → 期望瓦片字节；流体/空气/缺失高度图分支。
- `ChunkRegionSwapTest`（可用 `GameTests` 风格，参考 `DamagePolicyGameTests`）：同维度两区块交换后方块、BE、实体、高度图、光照一致性；交换两次恢复原状（自逆性）。
- `ChunkSwapJournalTest`：写入/读取/完成清理；中断检测。
- `TileAtlasPackingTest`：分配/释放/复用与防渗色内边距。
- `ChunkLeapTransitionTest`：事件注册条件与开关联动。

**手动验证（`runClientDev`）**

1. 单机打开地图：缩放/拖动流畅；跨维度页切换正确；实体标记与预览正确。
2. 同维度 4×4 区块交换：区块外观、方块实体（箱子内容/熔炉）、实体、玩家随区块一并移动；交换两次恢复。
3. 跨维度交换（主世界 ↔ 下界）：段数差异下的绝对 Y 配对正确；无方块实体丢失；光照无异常光斑。
4. 跨维度玩家随区块传送：不出现原版「正在加载地形」画面；仅出现本模组 VFX；落地后地形完整、无虚空坠落。
5. 边界：区域超出上限、两矩形重叠、目的地未加载、CP 不足 → 校验文案正确且按钮禁用。
6. 多人：第三方视角观察交换前后区块与实体；被交换区块内的其他玩家行为。
7. 性能：`AcademyProfiler` 观察瓦片生成/交换/光照分区耗时；交换 16×16 区块时服务器 MSPT 不出现尖峰。
8. 崩溃恢复：交换中途强杀服务器 → 重启后不出现半区块或光照永久错误。

**构建校验**：`./gradlew test -DisDev=true`，随后 `./gradlew build -DisDev=true` 与 `-DisDev=false`。

---

## 12. 风险与已知限制

| 风险/限制 | 影响 | 应对 |
| --- | --- | --- |
| 计划刻需将 `TickContainerAccess` 向下转型或加 AT | 未处理则区块内计划刻丢失 | P1 清空并标注；P3 加 AT 精确重定位 |
| POI（村民/工作站）跨维度不能自然迁移 | 工作站绑定可能失效 | 文档标注 + 设置开关；P3 视需要重建 |
| 结构引用无法随区块走 | 结构后续行为可能异常 | 交换后清空被交换区块 structure 数据并标注 |
| 幽匿等 `GameEventListener` 注册需清理 | 可能残留监听器 | 重建 BE 时统一处理；必要时 AT |
| 跨维度必有 ≥500ms 的 `LevelLoadTracker` 窗口 | 无法做到「零帧」 | 用本模组 VFX 覆盖该窗口；预加载消除地形等待；P3 评估 Mixin 兜底 |
| 跨维度每玩家保持异地租约 | 服务器内存/CPU 成本 | 严格半径/总量上限 + 关屏即释放 |
| 交换大幅改变地形 | 可能把玩家/建筑置于危险位置 | 交换后对标位于目标区块的玩家不做「安全落点」修正（保持随区块位移语义），但提供 `confirm_swap` 二次确认 |
| 资源包/图标工作量 | 6 张标记图标 + 1 张技能图标 + 布局 JSON | 按 `refresh-academy-ui` 规则手工制作，尺寸对齐既有 2× 纹理折半惯例 |

---

## 13. 附录：关键 API 依据（已核验）

| 结论 | 依据 |
| --- | --- |
| `getSections()` 返回内部数组，可直接交换引用 | `ChunkAccess.java:160-166`（`return this.sections;`） |
| `LevelChunkSection` 自包含且可安全换内容 | `LevelChunkSection.java:12-41`（states/biomes/4 计数）、`copy()`、`write/read(FriendlyByteBuf)` |
| `PalettedContainer` 不去重共享状态 | `PalettedContainer.java:295 copy()`；`LevelChunkSection` 私有拷贝构造调用 `states.copy()` |
| 区块加载异步 API | `ServerChunkCache.java:148`（`getChunk` 非主线程会 join）、`:221 getChunkFuture`、`:508 addTicketWithRadius`、`:512 addTicketAndLoadWithRadius` |
| Ticket 语义 | `TicketType.java:12-30`（`NO_TIMEOUT`、`FLAG_PERSIST/LOADING/SIMULATION`） |
| 主线程阻塞风险 | `ServerChunkCache.java:150`（`CompletableFuture.supplyAsync(..., mainThreadProcessor).join()`） |
| `setChunkForced` 会同步加载 | `ServerLevel.java:1528-1535` |
| 远处区块不会下发客户端 | `ChunkMap.java:227-231 isChunkTracked`、`:1132-1141 applyChunkTrackingView` |
| 整区块重发路径 | `PlayerChunkSender.java:76-87 sendChunk`、`ChunkMap.java:1146 getPlayers(ChunkPos, boolean)` |
| 逐块广播仅用于小改动 | `ChunkHolder.java:174-218 broadcastChanges` |
| 光照重建 API | `LevelLightEngine.java:62 updateSectionStatus`、`:73 setLightEnabled`、`:84 propagateLightSources`、`:136 retainData` |
| 天空光列缓存 | `ChunkAccess.java:483 initializeLightSources` |
| BE 重建 | `LevelChunk.java:399 addAndRegisterBlockEntity`、`:685 clearAllBlockEntities`、`:714 updateBlockEntityTicker` |
| 计划刻可读取/重排 | `LevelChunkTicks.java:53 schedule`、`:71 removeIf`、`:83 getAll` |
| 高度图 | `ChunkAccess.java:176-196 getOrCreateHeightmapUnprimed/getHeight`、`Heightmap.primeHeightmaps` |
| 实体枚举 | `EntityGetter.java:19-24 getEntities/getEntitiesOfClass` |
| 传送同步复用点 | `TeleportSync.java:47-108`；`InstantTeleportSyncPacket.java` |
| 同维度无缝 | `ServerPlayer.java:1144-1151` |
| 跨维度触发加载界面 | `ServerPlayer.java:1155` → `PlayerList.sendLevelInfo` → `ClientPacketListener.handleRespawn:1250-1298` → `startWaitingForNewLevel:1634-1645` → `LevelLoadTracker`（`LEVEL_LOAD_CLOSE_DELAY_MS=500`） |
| 隐藏加载界面的官方入口 | `net/neoforged/neoforge/client/event/RegisterDimensionTransitionScreenEvent`、`DimensionTransitionScreenManager`（NeoForge 26.2.0.70 源） |
| 现有租约管理器限制 | `TeleportChunkForceManager.java:41`（64 区块上限）、`:55`（`setChunkForced`） |
| 布局 schema | `assets/academy/ui/layout/location_teleport.json`、`WidgetSerializer`（`FORMAT_VERSION=2`） |
| 批次打断条件 | `BatchProcessor.BatchState.shouldBreakBatch`（pipeline/resourceKey/scissor 变化） |
| 动态纹理注册 | `MinecraftUiEnvironment.createDynamicTextureSource`（`DynamicTexture` + `textureManager.register`） |
| 实体 GUI 预览 | `DarkmatterCreationScreen.extractCreaturePreview` → `InventoryScreen.extractEntityInInventoryFollowsMouse` |
| 持久化范式 | `SpatialStorageSavedData.CODEC/TYPE`、`computeIfAbsent(server.overworld().getDataStorage())` |

---

## 14. 实施记录（P1）

### 14.1 已落地的文件

**服务端（`org.academy.internal.common.ability.teleport`）**

| 文件 | 职责 |
| --- | --- |
| `ChunkLeapRegion` | 维度的轴对齐区块矩形：编解码（解码时夹取）、形状/相交/偏移运算 |
| `ChunkLeapCost` | 计费单一实现（`100 + 4×区块数 + 30×实体数`），饱和运算，双端复用 |
| `ChunkLeapTickets` | 自建 `TicketType`（仅 `FLAG_LOADING`，不持久化、不模拟） |
| `ChunkTicketLeaseManager` | 引用计数的区块加载租约；`acquireAndLoad` 走异步 future，绝不阻塞主线程 |
| `MapTileBuilder` | 每区块 1 texel 采样（4 列取平均 + 高度明暗） |
| `ChunkMapViewService` | 视图租约生命周期、按 tick 预算的瓦片推送、限频实体标记、登出/停服清理 |
| `ChunkSwapService` | 校验（纯函数 `validateRegions` 与含服务端查维度的 `validate`）、计费、提交 |
| `ChunkRegionSwap` | 单对区块的原子交换：区块段引用交换、方块实体重定位、实体/玩家位移、高度图/天光列/光照、整区块重发 |
| `ChunkLeapPackets` | 全部 5 个 C2S + 5 个 S2C 包与编解码、`Client`/`Server` 处理器 |
| `ChunkMapClientState` | 客户端按维度的 texel 表、实体标记、epoch 过滤、结果队列 |
| `skills/lv5/ChunkLeap` | 技能定义（L5 / 100k / 基础 100 CP / 迭代 20）、`Alt+C` 按键、`executeSwap`/`executeEntityTeleport` 公开入口 |

**客户端**

| 文件 | 职责 |
| --- | --- |
| `client/gui/screen/ChunkLeapScreen` | 全屏地图：JSON 布局 + 即时绘制外壳、缩放/拖动/框选/实体选取、校验与确认面板 |
| `client/ability/teleport/ChunkMapTexture` | 单张 256×256 动态纹理，1 texel = 1 区块，整屏 1 个四边形 |
| `client/ability/teleport/ChunkMapViewClient` | 视图请求去抖 150 ms、增量请求、半径/上限夹取 |
| `client/ability/teleport/ChunkLeapClientConfig` | 持久化设置 + `SkillSettingsRegistry` 模块 |
| `internal/gui/map/ChunkMapRenderer` | 可见区裁剪、瓦片、网格、选区、实体标记与命中测试；`Geometry` 为可测纯数学 |
| `internal/gui/map/ChunkMapSelection` | 源/目标选区与实体选取状态 |

**资源与文档**：`ui/layout/chunk_leap.json`、技能图标、`en_us`/`zh_cn` 各 59 条文案、技能矩阵与前置关系文档。

### 14.2 与设计的偏差

| 设计 | 实际实现 | 原因 |
| --- | --- | --- |
| 动态图集 + 每区块 16×16 瓦片 + 服务端下发 `detail×detail` 色块 | **单张动态纹理，1 texel = 1 区块**，服务端下发每区块 1 个 ARGB | 一个 texel 已承载地图全部信息（该区块的表面方块颜色）；每区块一个 quad 会把批次打断，反而不如整屏 1 个 quad |
| 客户端提交矩形 + 服务端逐区块推送 | 同上，但服务端**一次性推一块矩形补丁**（含未加载占位） | 减少包数且天然对齐，客户端只需覆盖式写入 |
| 交换时保留计划刻 | **P1 未实现**：交换后区块内的计划刻随 section 交换而残留 | `ChunkAccess.getBlockTicks()` 返回 `TickContainerAccess`，精确重排需向下转型或 AT；已在技能文档与矩阵中标注 |
| 交换后 POI 重建 | **P1 未实现**（已知限制） | 见 §12；村民工作站绑定在跨维度或大范围交换后可能失效 |
| 崩溃恢复日志 | **P1 未实现**（P3） | 交换为自逆操作，同一算法再执行一次即可回滚；P1 依赖这一点 |

### 14.3 验证

- **单元测试**（`./gradlew test -DisDev=true`，全量通过）：`ChunkLeapRegionTest`、`ChunkLeapCostTest`、`ChunkSwapServiceValidationTest`、`ChunkLeapPacketsCodecTest`、`ChunkRegionSwapDeltaTest`、`ChunkMapRendererGeometryTest`。
  - 其中 `ChunkLeapCostTest` 在实现过程中真的抓到一个 bug：负数与饱和乘法未处理会让超大交换的计费回绕为负（变成退款），已修正为饱和运算。
  - `ChunkLeapPacketsCodecTest` 断言编解码后缓冲恰好读空，防止字段增减导致的流错位。
  - 测试用 `ResourceKey.create(Registries.DIMENSION, …)` 构造维度键，而不是引用 `Level.OVERWORLD`，否则裸 JUnit 会因初始化 `Level` 而失败。
- **运行时验证**：`./gradlew runGameTestServer -DisDev=true` 中的 `academy:chunk_swap_restores_world` 通过——在真实世界中植入钻石块 + 装苹果的箱子，交换后验证方块与**方块实体及其内容**都随区块迁移，再交换一次验证世界恢复原状。
  - 该 GameTest 需要直接交换并立即断言；若在植入与交换之间插入延迟，远端强制加载区块会被卸载，`LevelChunk` 句柄失效导致交换静默无效。
  - 另观察到：刚被强制加载的区块在同 tick 内没有实体可见性，向其中生成的实体会被接受但永不被跟踪——因此实体随区块位移的路径改由 `ChunkRegionSwapDeltaTest` 覆盖位移映射，运行时实体路径留待 P2 在正常加载的区块上验证。
- **构建**：`./gradlew clean build -DisDev=true` 与 `-DisDev=false` 均成功。
- 已知的既有失败（与本改动无关，在干净 HEAD 上同样失败）：`academy:shield_magnetic_resistance_damage`、`academy:mentalout_impression_riding_flight`、`academy:time_player_tick_scaling`。

---

## 15. 实施记录（P2）

### 15.1 新增与改动

| 文件 | 变更 |
| --- | --- |
| `ChunkVerticalBand`（新） | 两区块可交换的绝对方块 Y 区间：`full(level)` 同维整列、`overlap(a, b)` 跨维取交集；全为纯函数，可单测 |
| `ChunkRegionSwap` | 抽出共享流水线 `run(...)`；新增 `applyAcrossLevels(...)` 跨维路径与 `SectionExchange` 策略（同维指针交换 / 跨维段字节回环）；方块实体与实体采集按 band 过滤 |
| `ChunkSwapService` | `validateRegions` 允许跨维度；`validate` 增加「两维度无重叠高度则拒绝」；按维度相同与否分派两种交换 |
| `ChunkLeapPackets` | 新增 `PreloadStatusPacket`（S2C）；`ViewRequestPacket` 增加 `entityRefreshTicks` 字段 |
| `ChunkLeapTransition`（新） | 在 `RegisterDimensionTransitionScreenEvent` 注册按维度的工厂，仅在跃迁归因窗口内替换加载画面；`withinWindow(...)` 为可测纯函数 |
| `ChunkLeapLoadingScreen`（新） | 不绘制地形背景、仅一行文案与细分隔线的 `LevelLoadingScreen` 子类 |
| `ChunkLeapClientConfig` | 新增 `seamlessTransition` 开关；`entityRefreshTicks` 真正接入视图请求并展示在设置中 |
| `ChunkLeapScreen` | 移除客户端「跨维度不可用」限制；新增待执行时的目的地预加载进度条；提交跨维交换时开启归因窗口，收到结果后关闭 |
| `ChunkMapViewService` | 接受并在服务端夹取客户端请求的实体刷新间隔（5–40 tick） |
| 语言文件 | 两种语言各新增 7 条（预加载/准备/提交文案、`no_vertical_overlap`、`transition`、`seamless`、`side.preload`），并删除 2 条已失效的「P2 阶段未开放」文案 |

### 15.2 跨维度交换的语义

- **不是整列交换**：主世界 Y −64..319（24 段）与下界 Y 0..127（16 段）布局不同，段对象无法跨维度共享。交换只覆盖两者的**绝对 Y 交集**，带外区块段保持原样。
- **字节回环**：交集内的每对区块段用 `FriendlyByteBuf` 写出后互相读回（段对象留在各自维度），随后调用 `recalcBlockCounts()`——线上格式只携带方块与流体计数，不重算会让随机刻计数沿用旧值。
- **仍为自逆**：band 只取决于两个维度的高度范围而非区块内容，因此第二次交换使用同一 band，能完整还原，包括未被触及的带外区块段。
- **拒绝而非静默空操作**：若两个维度高度范围完全不相交，校验返回 `chunk_leap.reason.no_vertical_overlap`，而不是执行一次什么都不换的交换。

### 15.3 跨维度「无加载界面」的实际能力边界

原版跨维度传送必然经过 `ClientboundRespawnPacket` → 重建 `ClientLevel` → `LevelLoadTracker`，而 `LevelLoadTracker` 又必须在 `isLevelReady()` 后才关闭并发送 `ServerboundPlayerLoadedPacket`；服务端在收到该通知前会拒绝玩家移动。因此**取消这套状态机是不可行的**——用 Mixin 跳过它会让玩家卡在被拒绝操作的状态里。

本阶段的实现选择了保留握手、只替换画面：`ChunkLeapTransition` 用 NeoForge 官方事件把加载画面换成不绘制地形背景的 `ChunkLeapLoadingScreen`，仅显示一行「区块跃迁中…」与细分隔线，上一帧世界画面因此得以保留。**这消除了地形加载等待与视觉突兀，但 respawn 路径本身的约 500 ms 窗口无法压缩到零**——把它记为能力边界而非缺陷。

归因窗口（15 s）确保只有真正由本次跃迁引发的维度切换会被替换画面；归因在收到交换结果时立即清除，因此紧接着的传送门/指令换维仍显示原版界面。窗口判定已单测覆盖（含系统时钟回拨的情况）。

### 15.4 验证

- **单元测试**（全量通过）：新增 `ChunkVerticalBandTest`（重叠带对称性、空交集、段坐标、自逆前提）、`ChunkLeapTransitionWindowTest`（未武装/窗口内/过期/时钟回拨）；扩展 `ChunkLeapPacketsCodecTest`（新增 `entityRefreshTicks` 与 `PreloadStatusPacket` 往返），并更新 `ChunkSwapServiceValidationTest` 以反映跨维度现已放行（含「跨维仍需等额且形状一致」）。
- **运行时验证**（`runGameTestServer`）：`academy:chunk_swap_restores_world` 与 `academy:chunk_swap_section_byte_round_trip` 均通过。后者在真实世界中植入一块绿宝石块，对所在区块段做 write/read 往返并校验方块未变——这正是跨维路径依赖的序列化原语，因其需要真实 `RegistryAccess` 与调色板工厂，故放在 GameTest 而非单元测试。
- **构建**：`-DisDev=true` 与 `-DisDev=false` 均成功。
- 仍在失败的三项 GameTest（`shield_magnetic_resistance_damage`、`mentalout_impression_riding_flight`、`time_player_tick_scaling`）在干净 HEAD 上同样失败，与本改动无关。

### 15.5 与设计的偏差

| 设计 | 实际实现 | 原因 |
| --- | --- | --- |
| 跨维度交换整列（方案 B 全段迁移/填空段） | 只交换**绝对 Y 交集**，带外段不动 | 全列迁移需要为不存在的高度范围造空段并可能丢弃异维地形；交集方案语义更清晰、天然自逆、且不需凭空生成地形 |
| 用 `BlockEntity.loadStatic` 原文坐标 | 按 `delta` 平移后重建，并用 band 过滤 | 跨维时方块落在另一维度的新坐标，必须按目标坐标重建 |
| 跨维实体搬运需在正常加载区块上验证 | 位移映射由 `ChunkRegionSwapDeltaTest` 单测覆盖；`applyAcrossLevels` 的实体采集按 band 过滤 | 与 P1 相同的测试框架限制：刚强制加载的区块在同 tick 内无实体可见性，无法稳定构造该场景 |

---

## 16. 实施记录（V3：实机反馈修复）

实机 `runClientDev` 验证暴露 5 类问题，本轮全部处理。根因与修法：

| 反馈 | 根因 | 修复 |
| --- | --- | --- |
| 框选永远从左上角起始、选到超远区块并卡死 | `Screen.mouseDragged(event, dx, dy)` 的两个 double 是**每帧位移增量**，`event.x()/y()` 才是当前鼠标位置；误当坐标用导致选区终点永远算在画布左上附近，起点又是真实点击位 → 区域横跨半个世界 → 预加载上千区块 + 单 tick 完成全部交换 | 正确消费 `event.x()/y()` 与 `dx/dy`；选区在拖拽中经 `ChunkLeapRegion.clampArea` 以配置上限（默认 256）实时收敛，锚点角固定，超限时红框提示 |
| 平移缩放后地图画面丢失 | 同一 dx/dy 误用使平移飞出窗口；且整屏 quad 的 UV 未裁剪到纹理窗口，采样越界后拖影 | 平移改用增量；`renderMap` 把可见区块区间与纹理窗口求交，仅绘制交集，其余为占位底色 |
| 显示卡顿 | 每收到一个瓦片包就全量重建 256² 纹理（6.5 万次查找/包） | 瓦片增量写入 + 上传节流 150 ms；全量重建仅在窗口重锚/换维度时发生 |
| 打开地图即重负载 | 视图租约一次性添加数百 distance-manager 票据 | `ChunkTicketLeaseManager` 拆为两条路径：视图租约按 tick 预算（加 48/删 96）逐步物化，硬上限 576 区块；交换预加载保持立即路径但上限 1024。交换本身也改为按 tick 4 ms 预算分对执行 |
| 无法切换维度 / 无法选目标区 / 无法切实体模式 | 维度切换 UI 从未实现；1/2/Tab 仅键盘且不可见 | 顶栏新增 `‹ 维度 ›` 循环按钮（`[`/`]` 键同效，来自 `ClientPacketListener.levels()`）、`区块|实体` 模式按钮、`源|目标` 页签按钮——全部可点击，不再依赖键盘 |
| GUI 丑 / 按键提示溢出 / 右侧占比过大 / 精度低 | 面板固定 268px、页脚单行长文本、1 texel/区块采样 | 面板宽 `clamp(28%, 150, 210)` 随窗口自适应；页脚两行短句；每区块 8×8 texel（每 2×2 方块一采样，接近 FTB Chunks 观感），纹理 512²，服务端仅发送已加载区块（未加载零开销） |

### 16.1 精度与带宽的新契约

`TilesPacket` 改为逐区块瓦片：每 chunk 携带 `detail×detail` 个 ARGB（detail=8），**未加载区块不入包**。每 tick 48 区块 × 64 采样 = 3072 次高度图/方块状态查询，另有 4 ms 硬预算兜底；每包 ≤32 区块 ≈ 6 KB。

### 16.2 验证

- 单元测试全量通过：新增 `clampArea` 三个用例（失控拖拽收敛到上限、锚点角固定在原角、小区域不动）；`TilesPacket` 编解码测试改为逐区块瓦片断言。
- `runGameTestServer` 两个 chunk_swap 测试两轮均通过；其余失败项（shield/time/music/mentalout）为已知的既有不稳定项，其中 `time_tick_event_compatibility` 复跑即过，属时序敏感 flake。
- dev 与 release 两个构建变体均成功。

### 16.3 自查发现的额外缺陷（比原始反馈更严重）

V3 修完用户报告的 5 项后，我对新代码做了一轮自查，发现两个仍会导致「地图卡顿/内容丢失」的真缺陷——它们与用户报告的症状同源，但根因不同：

**缺陷 A：纹理窗口小于可视区域，导致逐帧重锚。**
原纹理 512²、detail 8 → 窗口仅 64 区块/轴；而 1688 宽画布在 zoom 1.0 时可见约 106 区块。于是 `ensureAnchorCoversView()` **每一帧**都判定需要重锚 → `reanchor()` 清空整张纹理 + `resyncAll()` 部分重填 → 画面持续闪烁、内容时有时无、并伴随明显卡顿。这正是「随着缩放等调动地图画面丢失」的根因，我在 V3 第一轮**没有**发现它。

修法：纹理改为 1024²（窗口 128 区块/轴，4 MB，比 2048² 的 16 MB 更合适），并给覆盖判定加 16 区块滞回（`coversWithMargin`），使重锚只在平移约一个margin后才发生。

**缺陷 B：zoom 范围与加载能力不一致。**
客户端以 16px/区块绘制，普通画布在 zoom 1.0 下要显示约 80–106 区块，但请求上限只有 24–48 区块。结果地图**永远只加载可视区的一小部分**，其余是空白——用户看到的「显示不完整」。

修法：把 zoom 的下限改为「可加载区域正好填满视口」的缩放值，并以该值为进入时的默认缩放；缩放范围内只能放大（换取细节），不会再露出未加载地形。`F` 键回到该基准视图。数值上已校验：任意 viewRadius（12/16/20）× 任意画布宽度（1280/1688/2328）下，视口所需区块数 ≤ 请求上限，且窗口覆盖始终成立。

**另外两处：**
- `ChunkMapClientState.addListener(this::onDataChanged)` / `removeListener(this::onDataChanged)` 是**两个不同的方法引用对象**，移除无效 → 每次开关地图都泄漏一个监听器，反复开关会累积。改为持有字段引用。
- 全量重同步原为逐 texel `setPixel`（单次可达数万次调用）→ 新增 `setChunk` 批量写入，把边界运算从「每 texel」降到「每区块」。

### 16.4 自查方法

不依赖实机，用三处**数值不变量**核对（均在构建前用脚本验算）：

1. 视口所需区块数 ≤ 客户端请求上限（保证不出现未加载空白）。
2. 视口所需区块数 + 2×margin ≤ 纹理窗口（保证不逐帧重锚）。
3. 请求上限的方形面积 ≤ 面积上限（保证侧向与面积两条约束互不矛盾，40×40=1600 恰等于 `MAX_REQUEST_CHUNKS`）。

这三条把「画面丢失/卡顿/空白」这一类问题变成了可在编译前证伪的算术，而不是只能靠实机观察的现象。

---

## 17. 实施记录（V4：跨维度加载 / 刷新 / 卡死修复）

### 17.1 三个缺陷的根因与修法

**I1 跨维度地图不加载（或极慢）。** 根因是 `ChunkMapViewService.rasterise` 对 `pending` 队列**只消费一次**：取出一个区块，若此刻尚未生成完成就**直接丢弃**，且没有任何重试。玩家所在维度区块早已加载，所以同维正常；任何其他维度页面的区块初始都未加载，于是几乎所有瓦片都被丢掉 → 地图长期空白。修法：新增 `awaiting` 重试表，未就绪的区块按 tick 重试（每 tick 64 次检查），超时 200 tick 后放弃，避免恶意请求永久占用队列。

**I2 交换后地图不刷新。** 根因在 `ChunkMapViewClient.tick`：`invalidate()` 只置 `viewDirty`，但随后 `clamped.equals(lastRequested)` 判断仍然命中，请求被去重丢弃。修法：区分「强制刷新」与「去重」，强制刷新绕过矩形相等判断；服务端在交换完成后调用 `ChunkMapViewService.invalidateChunks`，只对**实际变动的区块**重新栅格化，且只针对当前正在看该维度的会话。

**I3 跨维度传送卡死。** 根因是交换流水线**行内**执行派生状态重建：`Heightmap.primeHeightmaps` 会为每列从世界顶部 (Y=319) 向下扫描，单区块约 6.8 万次方块读取，天光列 `initializeLightSources` 再扫一遍，光照 `setLightEnabled` 触发 `runLightUpdates()`——而后者**一次处理全部待处理节点，没有时间上限**。256 区块的跨维交换意味着数秒的服务器停顿。修法：交换流水线只做区块段交换与实体搬运（廉价、原子），把「高度图 / 天光列 / 光照 / 整区块重发」**移出**到 `ChunkSwapService` 的 `REFRESH_QUEUE`，每 tick 6 区块 + 3ms 预算逐步排空；高度图只重新 prime 该区块**本来就追踪的类型**，不再无条件 prime 全部 7 种。

### 17.2 加载范围收紧（按玩家设置）

新增 `boundByViewDistance`：地图页的加载矩形被**玩家自身视距**限制（`ServerPlayer.requestedViewDistance()`），下限 16 区块以保证小视距下仍可用。这样一个玩家打开地图页不可能把任意大的区域钉在内存里。测试覆盖「不放大请求」「按比例」「小视距仍可用」三类。

### 17.3 坐标跳转与加载进度

- 侧栏新增坐标输入框：输入 `x z` 回车即把地图中心跳到该位置（`syncAnchor` 重锚并重新请求）。这是到达远距离位置的唯一实用途径——地图只加载中心附近的有限区域，靠拖拽过去需要极长时间。
- 地图视口顶部新增加载横幅（进度条 + 百分比 + 已加载/总数），仅在区域未填满时显示。此前跨维空白被误判为「坏了」，正是缺少这个指示。

### 17.4 关于 I4「上帝视角查看目标」——未按 3D 方案实现，原因如下

我调研了仓库既有的相机/远程视图能力并核对了 26.2 的客户端区块机制，得出**该方案不可行**的结论：

1. **相机分离本身是现成的**：`MixinCamera` 已有 `@Invoker setPosition/setRotation`，`WideAreaInterferenceClientState` 展示了完整的 RTS 相机（含远裁剪面与雾距扩展）。渲染也是**以相机为中心**的（`LevelRenderer.repositionCamera`、`SectionOcclusionGraph.initializeQueueForFullUpdate(camera.blockPos)`），所以相机放到哪里就渲染哪里的区块。
2. **但客户端根本不会保留远程区块**。`ClientChunkCache.Storage.inRange` 以 `viewCenterX/Z` 为中心、半径 `max(2, serverViewRange)+3`，**超出即丢弃该区块包**并打警告；而 `viewCenter` 只由服务端 `ClientboundSetChunkCacheCenterPacket` 更新，该包又只在 `ChunkMap.applyChunkTrackingView` 里随玩家追踪视图变化发出。
3. **让服务端移动客户端缓存中心 = 放弃玩家自己的区块**。`applyChunkTrackingView` 在发送新中心的同时，用 `ChunkTrackingView.difference` 对旧矩形调用 `dropChunk` → `ClientboundForgetLevelChunkPacket`。也就是说「把缓存中心搬到目标位置」会**卸载玩家脚下的区块**。
4. **跨维度还要求客户端持有第二个 `ClientLevel`**，而这只能通过 respawn 流程创建——即必须经过加载界面。这与「无缝」目标直接冲突。

因此，把「上帝视角看真实 3D 地形」做进这个技能，只能以牺牲玩家周围世界或引入加载界面为代价，属于我无法在无实机确认下安全交付的改动。

**代之以实际可用的等价能力（已实现）**：`ChunkLeapInspector` + 悬停详情面板。服务端**读取自己手上的真实区块**，返回该区块的表层方块、表层 Y、生物群系、实体数量与前 5 种方块构成；客户端悬停即请求（按区块去重 + 250ms 限频）、结果缓存、面板显示。它适用于**任意维度**（不需要客户端拥有该维度的 `ClientLevel`），读数是真实方块数据而非采样色块，且单次只加载被悬停的那一个区块。就「确认目标位置到底是什么样」这一实际目的而言，它给出的是可据以决策的信息；若你确实需要完整 3D 视角，请告诉我，那需要接受上述代价之一（我倾向的方案是：临时把玩家置于旁观者模式并正常传送过去，也就是用原版机制换取真实视野，但会有加载界面）。

---

## 18. 实施记录（V5：选择模型重构与显示修复）

### 18.1 选择模型从「矩形」改为「区块集合」

原来的源/目标都是矩形，导致三处体验问题：单击必须配合微拖才能选中单块、无法选中不相连的区块、目标必须手动再框一次。现在引入 `ChunkLeapSelection`：

- **存的是偏移列表**（相对集合自身左上角），不是矩形。任意形状（含 L 形、离散块）都能表达。
- **单击即选中**：`setRectangle(anchor, anchor)` 使单击立即得到一个 1×1 选区；继续拖动才扩展成矩形。锚点角在裁剪时保持不动，选区不会在光标下跳变。
- **Ctrl+单击增减**：`toggle(chunk)` 逐块编辑，用于挑选不相连的区块。
- **目标由源派生**：`source.anchoredAt(dim, origin)` 复用同一份偏移，因此目标与源**在构造上**就等大同形——「数量不等」「形状不符」从此不可能出现，这两个校验分支与对应文案一并删除；服务端按**偏移下标**配对（源第 i 块 ↔ 目标第 i 块）。
- 线格式随之改变：`SwapRequestPacket` 只传一份偏移列表 + 源/目标两个锚点，而不是两个矩形。

顺带修掉一个真实缺陷：`MAX_SWAP_CHUNKS` 与容器上限 `MAX_CHUNKS` 原本都是 1024，而构造时会截断到容器上限，导致「超过上限」校验**永远不可能触发**。现已把玩法上限下调为 512（低于线格式上限 1024），使上限成为真正可执行的约束，并加了断言防止二者再次相等。

### 18.2 显示与交互

- **实体标记改为小圆点**（新增 `marker_dot.png`，按分类染色 + 深色描边保证在亮地形上可读）。选中实体在圆点外侧绘制**方框括角**，悬停时绘制淡色括角。
- **实体名称本地化**：悬停圆点显示 `EntityType.getDescription()` 的本地化名，侧栏列出「已选中：<名称>」与目标坐标。
- **实体传送文案独立**：新增 `chunk_leap.status.entity_requested` 等键，实体模式不再复用区块交换的提示语。
- **实体传送精度**：点击位置经 `blockXAt/blockZAt` 反投影为**精确方块坐标**（此前固定取区块边界 +8），服务端再用与地图采样同一个 `visibleSurfaceY` 解析该列真实地表，因此落点与地图显示一致。

### 18.3 下界等「封闭天花板」维度

问题：下界顶部由基岩封闭，`WORLD_SURFACE` 高度图返回的永远是基岩顶，于是地图显示为一整片平的基岩，看不到下面的真实地形。

修法：`groundUnderCeiling` 用**「薄层 + 其下有空气」**判定天花板，而不是「位于建筑高度上限」或「下方有空气」。前者不依赖维度高度（下界顶棚并不在建筑上限），后者会把普通地表下的洞穴误判为顶棚。判定为顶棚后向下搜索第一个实心块，即为被封住的真实地面。该函数是纯函数，已用 6 个用例覆盖（含「洞穴不得误判」与「搜索深度有界」）。

### 18.4 虚空区块残留上一维度样式（用户报告）

两个叠加原因：
1. `sampleChunk` 用返回值同时表达「未加载」和「已加载但全是空气」——虚空区块因此被当作未加载，进入重试队列**永远不发送**，该位置保留上一维度的像素。
2. 切换维度只在窗口移动时才清空纹理；窗口未移动时旧像素原样留在共享纹理上。

修法：`sampleChunk` 改为只表示「是否已加载」，空区块仍发送（每 texel 用 `EMPTY_RGB` 表示）；`switchDimension` 无条件清空纹理。

### 18.5 关于「预览实际目标画面」

用户要求一个按钮预览真实目标区域。**完整的 3D 视角仍然不可行**，原因与 §17.4 相同（`ClientChunkCache.Storage.inRange` 会丢弃远程区块包，移动缓存中心会卸载玩家脚下区块，跨维度还需第二个 `ClientLevel`）。

因此实现为**方块级预览**：`ChunkLeapPreview` 以 1 texel = 1 方块读取目标中心 64×64 方块的**真实地表**（同样的顶棚感知采样），侧栏新增「预览目标区域」按钮，点开后在地图视口上方叠加显示该图像并可点击关闭。相对地图的每 2×2 方块 1 像素，这是 16 倍细节，足以辨认建筑轮廓与方块构成；且中心区块未加载时会明确提示「无法预览」，而不是显示空白。

### 18.6 验证

- 单元测试全量通过。新增 `ChunkLeapSelectionTest`（归一化、重锚复用偏移、去重、越限丢弃、包围盒与集合不等价）与 `MapTileBuilderRoofTest`（顶棚识别、洞穴不误判、搜索深度有界）。
- 更新 `ChunkSwapServiceValidationTest`（超限改为在两级上限之间取值，并断言两级上限不相等）与 `ChunkLeapPacketsCodecTest`（线格式改为偏移列表 + 双锚点，并断言派生目标与源同形）。
- `runGameTestServer` 的 2 个 chunk_swap 测试通过；其余失败项为既有不稳定项。
- dev / release 两个构建变体均成功。

### 18.7 尚未验证

所有视觉与手感（圆点大小、括角观感、预览可读性、单击选中是否顺手、下界地图是否正确显示地形）均需实机确认；我无法自行观察画面。其中**下界地形**与**虚空残留**两项是本轮修复中最值得优先复核的。

---

## 19. 实施记录（V6：三处严重缺陷根因修复）

本轮全部先定位根因再改，含一次我自己的误判（见 19.4）。

### 19.1 目标选取模式点击一次就跳回源模式

**根因**：放置目标的代码里写了 `placingTarget = false;`，注释还写着「模式结束，下次点击回到编辑源」——这是我上一轮的设计误判。放完即退出，导致每次微调目标都要重新按 `2`。

**修法**：放置后保持 `placingTarget` 为真，可连续点击更新目标位置。仅在玩家主动切换时退出（点「源」按钮、按 `1`、切换页签）。

### 19.2 跨维度传送直接失效

两个叠加根因：

**（a）协议层不传目标维度。** `commit()` 把**当前显示维度**同时当作源与目标维度（`SwapRequestPacket` 的两个维度参数都是 `dimension`），`source.dimension()` 因此恒等于 `target.dimension()`，跨维度在协议层不可能成立；且切页后源坐标会被错误解读到新维度。根因是 `ChunkMapSelection` 没有记录两侧各自的维度。

修法：`ChunkMapSelection` 增加 `sourceDimension` 与 `targetDimension`，`setTargetOrigin(dimension, origin)` 记录目标维度，提交与校验改用 `source.dimension()` / `target.dimension()`；渲染时每侧只在**自己所属的页面**绘制。附带明确了语义：在另一维度编辑源集合等于在该维度重新开始选择（清空旧的源与目标）。

**（b）预加载租约互相释放。** `acquireAndLoad` 内部第一步是 `release(owner)`，而源与目标**共用同一个 owner**，于是第二次调用会释放第一次刚申请到的源区块。「同维度时区块本来就在内存里」掩盖了它；跨维度时源区块在提交前被卸载，`performSwap` 里 `getChunkNow` 返回 null 后按设计**静默跳过**，表现为「跨维度传送完全没反应」。

修法：两侧使用独立 owner（`...:a` / `...:b`），并把释放集中到 `releasePreloadLeases(player)`（原先 6 处零散释放只释放共享 id，会泄漏两侧票据）。

### 19.3 实体传送落点 XZ 完全无效、固定落到区块右下

**根因**：标记坐标换算写成 `blockX / 16.0 + 0.5`，而正确形式是 `(blockX + 0.5) / 16.0`。前者把标记整体向右下平移**半个区块（7.5 方块）**——与「总是落在区块右下顶点」的现象完全对应（误差恒定、方向固定）。同一个错误出现在绘制与命中判定两处，因此点圆点时命中判定也偏了。

修法：两处换算改为 `(block + 0.5) / 16.0`。

同时修正落点高度：客户端只上报考点击的方块列（Y 传 0），服务端原先直接用该 Y，导致实体被放到世界底部再由安全搜索向上找，看起来仍在错误位置。现改为 `landingPosition(...)` 用 `MapTileBuilder.visibleSurfaceY` —— 与地图栅格**同一个**顶棚感知解析器 —— 取该列真实地表 +1，保证落点与地图显示一致（含下界顶棚之下）。

### 19.4 一次我自己的误判（记录以免重犯）

我一度认为反投影公式 `16.0 / chunkSize` 写反了，改成 `chunkSize / 16.0` 并「验证通过」——但那份验证脚本里我把两个函数名标反，读错了输出。随后写正规单测立刻暴露：`zoom 0.5` 时把 80 格算成 20 格。`chunkSize` 是**每区块像素数**，所以像素→方块应为 `16 / chunkSize`，**原式才是对的**，已回退。

教训与此前一致：这类换算不能靠手写副本「验证」，必须让**生产代码本身**参与断言。故新增 `ChunkMapProjectionTest`：跨 0.25/0.5/1/2/3/8 六档缩放做往返断言，并**显式断言两种写法在 1× 之外会不同**——这条断言的作用是防止「只在默认缩放下测试」再次漏掉同类错误；另有标记换算的专项回归用例。

### 19.5 验证

- 单元测试全量通过（新增投影往返 6 用例 + 标记换算回归）。
- `runGameTestServer` 的 2 个 chunk_swap 测试通过；其余失败项为既有不稳定项。
- dev / release 构建均成功。
- **仍需实机确认**：跨维度交换是否真的生效、实体是否落在点击位置、目标模式能否连续点击。这三项正是本轮修复目标，但我无法自行观察画面。

---

## 20. 实施记录（V7：顶棚判定重写、精度提升、详情视图、确认逻辑）

### 20.1 顶棚判定重写（修复下界遮挡 + 平原变灰棕）

**旧实现有两个方向的错误**，且互为镜像：

- **下界仍被遮挡**：用「薄层(<8格) 且其下有空气」逐列判断顶棚。但下界顶棚常与下界岩**直接相连**，厚度超过 8 格就被判为「不是顶棚」，于是直接渲染基岩（图中那片灰）。
- **平原显示成灰棕**：同一启发式在地表下方几格内**有洞穴时会误判**。我用真实列剖面复现确认：草地顶层 64、泥土 61–63、60 是洞穴空气 → 旧逻辑返回 59（洞穴顶的石头）而非 64（草地）。这就是灰棕色主题的来源。

**新实现改为区块级平面检测**（`MapTileBuilder.ceilingY`），需同时满足三条：

1. 该 Y 上 **≥90%** 采样列为实心（流体不算实心，避免把海面当顶棚）；
2. 该 Y 距该区块最高块 **≤4 格**——这是与洞穴的关键区分：头顶盖必然紧贴列顶（基岩在建筑上限），而洞穴总在更深处；
3. 其下 **平均空洞 ≥12 格**——顶棚掩盖的是大空间，而地表下紧邻的一两层是土壤；平坦地形在「实心水平面 + 下方小间隙」这两点上与顶棚相似，只有空洞厚度能区分。

定位到真实顶棚后，`resolveColumnY` 先穿过顶棚及**所有附着其上的岩层**，再穿越空洞，落到玩家真正站立的地面。整个判定不依赖维度、高度和材质，符合 VoxelMap 对洞穴/封闭维度做通用适配的思路（其 `MapProcessor` 同样以「射线向下找到可站立面」而非固定高度解决该问题）。

### 20.2 地图精度与真实感

- **精度**：`DETAIL` 8 → **16**，即 **1 texel = 1 方块**（此前每 2×2 方块才 1 像素）。纹理相应 1024² → **2048²** 以维持 128 区块/轴的覆盖窗口（16 MB VRAM）。
- **真实感**：新增**坡度阴影**（与西/北邻块比较高度，形成西北向一致光照，使台地、悬崖边缘显现轮廓，而不是纯色块）与**海拔阴影**、**流体压暗**。
- 详情视图与地图**共用同一套采样**（`MapTileBuilder.sampleArea` 与 `sampleChunk` 同源），避免两处再次对下界产生分歧。

### 20.3 详情视图（替代原预览按钮）

按要求移除「预览目标区域」按钮，改为：**鼠标悬停任意区块后按 `C`**，地图视口直接切换为该区块的 **1:1 实际画面**（64×64 方块，复用真实方块数据）。再按 `C` 或点击任意处返回地图。视图固定在进入时的所在区块，不随光标移动。

### 20.4 Enter / Esc 二次确认失效

**根因（键盘专属，按钮路径正常）**：`Enter` 分支**无条件**执行 `confirmTicks = 200`，从不检查是否已处于确认状态，因此第二次按 Enter 只会把确认重新武装，**永远无法通过键盘完成**；`Esc` 分支无条件 `onClose()`，从不取消已武装的确认，所以确认提示无法用 Esc 撤销。

修法：`Enter` 改为与按钮一致的两段式（已武装则执行，否则武装或直接执行）；`Esc` **优先取消**已武装的确认，仅在无待确认时才关闭界面；在空白处点击同样取消确认。

### 20.5 验证

- 单元测试全量通过。重写 `MapTileBuilderRoofTest` 针对两个纯函数（`isRoofPlane` / `resolveColumnY`），其中**明确覆盖本轮新发现的洞穴误判**（`solid=16/16, void=1, depth=0` 必须判为非顶棚），以及「附着岩层需一并穿过」。
- `runGameTestServer` 的 2 个 chunk_swap 测试通过；其余失败项为既有不稳定项。
- dev / release 构建均成功。

### 20.6 仍未验证

下界是否真正显示地形、平原颜色是否正确、1:1 画面的观感与性能、`C` 键与确认逻辑的手感，均需实机确认。**其中 20.1 的两个方向都需要重点复核**：我在单元层面只能证明判定规则自洽，无法证明它在真实世界的基岩层分布下必然正确。

---

## 21. 实施记录（V8：光照即时刷新、真实上帝视角）

### 21.1 传送后两边区块全黑

**根因**：光照被放进了**预算队列**。`setLightEnabled(pos, false)` 是**立即丢弃**该区块光照数据的操作，而重算排在「每 tick 6 个区块」的队列里。交换 256 区块（两侧共 512）需要约 86 tick ≈ **4.3 秒**才能轮到后面那些区块 —— 在这段时间里，它们的旧光照已被丢弃、新光照尚未生成，于是渲染为**完全黑暗**。

我此前把「光照刷新」与「heightmap 重算」当成同一类昂贵操作一起推迟了，这是判断错误：

| 操作 | 真实成本 | 能否推迟 |
| --- | --- | --- |
| heightmap 重算 | 每列从世界顶部向下扫，约 6.5 万次方块读取/区块 | **可以** —— 只影响生成/地图逻辑，不影响看到的画面 |
| 光照丢弃+重算标记 | 簿记 + 一次重建请求，光照引擎自行分 tick 摊销 | **不可以** —— 丢弃即刻生效，画面立刻变黑 |

**修法**：拆成两个方法。`refreshLighting` 与方块搬运在**同一 tick 内**完成（含天光列 `initializeLightSources`）；`refreshHeightmapsAndResend` 仍留在预算队列。重发也在交换时立即执行一次，队列里的重发用于 heightmap 更新后同步。

### 21.2 C 键改为真实上帝视角

之前理解错了：把 C 做成了「放大后的栅格图」。要求是**玩家正常看到的样子**，即真实 3D 渲染。

我先复核了 P2 阶段判定「不可行」的那条约束，并发现**结论需要修正**：

- `ClientChunkCache.Storage.inRange` 确实会丢弃窗口外的区块包 —— 这点没变；
- 但关键细节是 `getIndex` **只按绝对坐标取模**，不使用窗口中心。因此 `updateViewCenter`（由 `ClientboundSetChunkCacheCenterPacket` 驱动）**只移动中心、不搬迁数据、不驱逐任何区块**。玩家自己的区块仍留在原槽位，中心移回即可再次读到。

所以存在一条**无需 mixin**的可行路径：服务端把客户端的缓存窗口中心移到目标，直接推送目标区域的真实区块包；客户端分离相机俯视该处。离开时把中心移回玩家并重发其周边（因为远处目标可能与玩家附近的区块共享模组槽位）。

**实现**：
- `ChunkLeapGodView`：客户端相机状态（俯视 78°、高 48 格、扩展远裁剪面）。
- `MixinCamera` 新增一个注入，与既有 RTS 相机同样在 `alignWithEntity` 之后覆盖相机位置与朝向 —— 真实渲染器于是绘制目标区块。
- `ChunkLeapGodViewClient`：收到服务端就绪通知后才升起相机；**跨维度直接拒绝**（客户端只持有一个 `ClientLevel`，别的维度的地形无处安放），而不是画出错误的世界。
- `ChunkSwapService.onGodViewRequest`：按玩家视距（上限 8 区块）加载并推送目标区域，随后回报就绪。
- 屏幕在上帝视角下**不绘制任何 UI**（也不做背景模糊），因为真实世界本身就是内容；只留一条提示条。屏幕保持打开以继续接收 `C`/`Esc`；`Esc` 逐层退出（上帝视角 → 二次确认 → 关闭）。
- 移除了整条已废弃的栅格预览链路（包、客户端缓存、处理函数、`ChunkLeapPreview`）。

### 21.3 验证与遗留

- 单元测试全量通过（顶棚/投影/选择等既有用例）；`MapTileBuilderRoofTest` 覆盖上轮的洞穴误判。
- `runGameTestServer` 的 2 个 chunk_swap 测试通过；其余为既有不稳定项。
- dev / release 构建均成功。
- **仍需实机确认**：传送后是否不再全黑（21.1）、上帝视角是否显示真实地形而非栅格图（21.2）。上帝视角这一改动涉及客户端缓存窗口与相机覆盖，属本轮风险最高处 —— 若出现「玩家周围地形消失」或「切回后一块区域空白」，说明窗口中心恢复与重发需要进一步收紧，请把现象告诉我。

---

## 22. 实施记录（V9：幽灵方块根因、F5/T 键、实体画像）

### 22.1 幽灵方块（未加载区块交换后新方块不可见，需退出重进）

**根因在我上一轮加入的上帝视角退出逻辑**，不是交换本身。

`endGodView` 里我发送了一圈 `ClientboundForgetLevelChunkPacket`（以玩家为中心、半径约 9，共约 361 个区块）来「归还槽位」。客户端对每个包调用 `ClientChunkCache.drop()` **删除该区块**；而服务端仍认为这些区块处于追踪状态（`ChunkTrackingView` 未变），因此**永远不会再发一次** —— 被删掉的正是玩家脚下的地形。之后只有发生方块更新才会单独补发那一格，所以表现为「只有方块更新才能看见」，且必须重进世界才彻底恢复。用户报告的现象与此完全一致。

**关键机制**：`getIndex` 只按绝对坐标取模，不使用窗口中心，所以移动中心本身不搬迁、不驱逐任何区块；真正造成破坏的是**主动 Forget**。

**修法**：
- 记录每次上帝视角实际流式推送的区域（`StreamedView`：维度 + 最小区块 + 边长），退出时**只 Forget 该区域**，绝不触碰玩家周围。
- 恢复窗口中心后，重发玩家周边（半径 6）以修复「远处目标与玩家附近区块共享模组槽位」造成覆盖。
- 玩家登出时清理记录（`forgetStreamedView`），避免记录泄漏。

这也修正了上一轮文档中「离开时把中心移回玩家并重发其周边」这一处不完整的设计 —— 当时漏掉了「Forget 必须只针对流式区域」这个前提。

### 22.2 F5 刷新当前维度地图缓存

新增 `ChunkMapClientState.invalidateDimension`：丢弃该维度的瓦片与待应用补丁，随后 `newEpoch` + 清空纹理 + 重锚，使下一次请求从真实方块重建。用于地形在视野外发生变化（他人操作、交换、或上帝视角覆盖了缓存槽位）后手动恢复一致。

### 22.3 T 键传送（FTB 风格）

悬停地图任意位置按 `T`，把自己传送到**光标所指的方块列**。客户端只上报列坐标，服务端：
1. 用 `visibleSurfaceY` 解析该列真实地表（含下界顶棚之下）；
2. 经 `TeleportSafety.findSafe` 选一个无碰撞落点，因此点悬崖或墙里也不会把人埋进方块；
3. 先加载 3×3 邻域再判定（安全搜索需要周边方块存在），随后计费并传送。

交换进行中会拒绝（避免落到即将改变的区块上）。

### 22.4 实体画像与着色

- **玩家用皮肤正面头像**：标记新增 `playerId`（仅玩家携带）。客户端经 `ClientConnection.getPlayerInfo(uuid).getSkin().body().texturePath()` 取皮肤，绘制 8×8 的正面区域（u=8,v=8），并叠加帽层（u=40,v=8）以正确合成头发与饰品 —— 与游戏内渲染方式一致。
- **非玩家降级为圆点**：这是**有据可依的降级**而非省略 —— 生物头部纹理是模型相关的，无法按实体类型寻址，因此只有玩家能取到缩略画像。圆点按** disposition 着色**：敌对红、中立黄、友善绿。
- **自身蓝色描边**：本机玩家额外绘制蓝色括角，一眼定位「我在哪」。

### 22.5 验证

- 单元测试全量通过；`runGameTestServer` 的 2 个 chunk_swap 测试通过（其余为既有不稳定项）；dev / release 构建均成功。
- 清理了随预览按钮一并失效的 2 个死键，并补入本轮新键；两种语言键数与顺序保持一致。
- **仍需实机确认**：幽灵方块是否彻底消失（尤其是使用过上帝视角之后）、T 的落点是否与光标一致、玩家头像是否正常显示与合成。22.1 的修复依赖「只 Forget 流式区域」这一前提，若仍有残留请告知具体触发路径。

---

## 23. 实施记录（V10：随区块传送后光照丢失、上帝视角观感与操作）

### 23.1 玩家随区块传送后周围方块渲染全丢（需退出重进）

**根因是「光照重建的广播只对当时正在追踪该区块的玩家发出，且之后清空待发过滤器」这一原版机制与我的执行顺序冲突。**

`ChunkHolder.broadcastChanges` 的流程是：若存在待发的天空光/方块光过滤位，就 `playerProvider.getPlayers(pos, true)` 取**当前追踪该区块的玩家**发送 `ClientboundLightUpdatePacket`，然后**无条件清空过滤器**。而我的交换在**搬运方块的同一 tick** 就 `refreshLighting`（重新 `setLightEnabled` 触发重建）；此时被携带的玩家**还没被传送到目的地**，也就还没开始追踪那些区块。等他在下一 tick 抵达时，光更新早已广播并清空 —— 该玩家**永久错过**这次光照，客户端保留上一帧的光照数据直到重登。

同维度传送尤其明显：客户端区块列表没有变化（不需重发地形），因此**没有任何后续机制去补光照**，表现为「周围方块渲染全部丢失」。

**修法**：交换管线把**被携带的玩家**列在 `Result` 中返回（`Result.playersMoved()`），由 `ChunkSwapService` 在交换完成后为他们安排**分两批、各延迟 20 tick 的整区块重发**（`CarrierResync`）。理由有二：

1. 光照引擎的重建是**跨多个 tick 摊销**的，过早发送仍可能抓到重建中的中间态，所以需要等待；
2. 整区块包（`ClientboundLevelChunkWithLightPacket`）同时携带当前光照并使客户端**把该区块每个 section 标脏重绘**，一次性修复光照与渲染状态，而不必依赖精确的光更新包。

带上「等待 + 两次」是因为单次发送可能恰好落在重建中间。

### 23.2 上帝视角采用 F1 的隐藏逻辑

按 F1 的实际行为实现（`Hud.toggle()` 驱动的 `isHudHidden`）：
- `ChunkLeapGodView.enter` 记录玩家原本的 HUD 状态后隐藏，`leave` **按原样归还**（玩家本来就隐藏则不改变）—— 借用而非接管；
- 第一人称手持物沿用仓库既有做法（`MixinGameRenderer` 中与心念入侵相同的 `renderItemInHand` 取消点）；
- 自身模型通过 `MixinEntityRenderDispatcher.shouldRender` 屏蔽，与心念感知隐藏同一注入点。

### 23.3 地图与上帝视角的 WASD / 滚轮

- **地图**：WASD 平移视图（每次 4 区块），滚轮缩放不变。
- **上帝视角**：WASD 移动相机焦点（屏幕坐标方向，与朝向无关，因此在俯视时仍符合直觉），滚轮**调整相机高度**（6–320 格）而非视场角 —— 正俯视时「远近」才是改变可见范围的那个量。
- 聚焦点仍在已流式区域**之内**时，服务端**不重发区块**（避免每次按键喷出数百个区块包），只回传新的地表高度；移出区域才重新流式传输。
- WASD 在 `keyPressed` 最前处理，确保优先于同名的普通快捷键。

### 23.4 验证

- 单元测试全量通过；`runGameTestServer` 的 2 个 chunk_swap 测试通过（其余为既有不稳定项）；dev / release 构建均成功。
- 已确认 `MixinCamera` / `MixinEntityRenderDispatcher` / `MixinGameRenderer` 均在 `academy.mixins.json` 的 client 列表中，新类已进入构建产物。
- 键文案同步更新，两种语言键数与顺序一致。
- **仍需实机确认**：23.1 是否真正消除了「传送后需重进」；以及上帝视角的隐藏与 WASD/滚轮手感。23.1 的修复依赖「延迟整区块重发能把光照补齐」这一推断，若仍有残留，请告知是**同维度**还是**跨维度**、以及传送距离 —— 这能区分是光照时序还是区块追踪本身的问题。

---

## 24. 实施记录（V11：上帝视角槽位覆盖致渲染丢失、方向反向）

### 24.1 退出上帝视角后玩家周围渲染永久消失

**根因是我上一轮「只 Forget 流式区域」的做法仍不充分，且漏掉了客户端缓存的核心机制。**

`ClientChunkCache` 是一个固定尺寸的**槽位网格**：槽位下标 = `floorMod(chunkZ, V) * V + floorMod(chunkX, V)`，其中 `V = 缓存半径 * 2 + 1`。因此**相距 V 的整数倍的区块会落在同一个槽位**。上帝视角把远处区块流式写进这些槽位时，**会覆盖玩家自身区块所占的槽位**。

关键在 `ClientChunkCache.isValidChunk`：

```java
ChunkPos pos = chunk.getPos();
return pos.x() == x && pos.z() == z;   // 槽位里的区块必须坐标匹配
```

被覆盖的槽位里存的是远处区块，**坐标不匹配 → 该槽位对玩家位置直接失效 → 地形消失**。而服务端仍认为这些区块处于追踪状态，**已追踪的区块永远不会被重发**，因此必须退出重进 —— 与报告完全一致。

**修法（记录 + 精确修复，不再 Forget）**：
1. `StreamedView` 记录**客户端缓存半径**与**本会话写入过的所有槽位下标**（跨多次平移累积并集 —— 早期矩形也可能占用了现在要紧的槽位）。
2. 退出时先把缓存中心交还玩家，再**只重发那些确实被写过的槽位**中属于玩家的区块。仅考虑写过的槽位，避免「平移一次就全量重发」；不 Forget 任何东西（Forget 会删掉合法区块，而残留的远处区块只是坐标不匹配、不会被绘制）。

**另外修正了一个我上轮引入的破坏性做法**：我曾发送 `ClientboundSetChunkCacheRadiusPacket` 来把缓存半径固定成已知值。但该包会调用 `updateViewRadius` **重建更小的存储数组**，导致玩家一部分区块被静默丢弃 —— 这是同类的破坏。实际不必如此：客户端的缓存半径来自**登录包**里的服务端视距，服务端本就能算出精确值（`min(requestedViewDistance, serverViewDistance)` 再 `max(2, ·) + 3`），因此改为**只计算、不发送**。

> 过程中我还犯了一个工具性错误：用 `substring(a, b)` 做替换时 `b < a`，JS 会自动交换两参数，导致整个 `ChunkSwapService.java` 被截断（该文件未纳入版本控制，无法用 git 恢复）。已按外部所需的 API 面完整重建并通过编译。

### 24.2 上帝视角 WASD 与滚轮全部反向

**根因是坐标系符号弄错，不是手感问题。** 相机 yaw = 0 时**朝向 +Z**（南），因此在屏幕上方对应 +Z 逐渐增大、屏幕右方对应 -X。而我写成了「W → Z 减小、D → X 增大」，**前后、左右各自反向**。

修法：`targetX -= strafe`、`targetZ += forward`（W 向 +Z / 屏幕上，D 向 -X / 屏幕右）。

滚轮同理：地图是「上滚 = 拉近 = 放大」，而上帝视角里「拉近」等于**降低相机高度**（离地面越近，看得越细），我却写成了上滚升高，因此反向。改为 `adjustHeight(scrollY < 0)`，与地图手感统一。

> 地图本身的 WASD 与滚轮未动 —— 它的方向原已正确，只有上帝视角需要翻转。

### 24.3 验证

- 单元测试全量通过；`runGameTestServer` 的 2 个 chunk_swap 测试通过（其余为既有不稳定项）；dev / release 构建均成功。
- **仍需实机确认**：24.1 需重点验证「上帝视角内多次平移后退出」是否仍会丢渲染 —— 这是槽位并集记录的关键路径；24.2 请确认 WASD 四向与滚轮升降是否符合直觉（若仍有一轴反向，说明我对 yaw=0 的屏幕映射推断需要再校正）。

---

## 25. 实施记录（V12：上帝视角相机高度不应随地形起伏）

### 25.1 根因

`ChunkLeapGodView` 把相机位置写成 `targetY + height`，而 `targetY` 来自服务端每次回报的**该处地表高度**。WASD 平移会重新请求并回调 `enter(x, y, z)`，于是每移动一次，相机的绝对高度就被重置为新位置的地表高度 —— 相机**跟着地形上下起伏**。

**顺带发现同一函数的第二个缺陷**：`enter()` 每次都把高度重置为 `DEFAULT_HEIGHT`。因此平移不仅使相机随地形升降，还会**抹掉玩家用滚轮调好的缩放** —— 两个症状同源。

### 25.2 修法：区分「进入」与「平移」

把「高度参考」与「相对高度」拆开，并让两条路径各司其职：

- `entrySurfaceY`：**仅在进入时**由该处地表确定，之后平移不再改变；
- `altitude`：相对高度，滚轮调整，平移不改变；
- **`enter(x, y, z)`**：新进入（地图上按 C）。设定参考高度、重置 altitude 与俯角、接管 HUD。
- **`refocus(x, z)`**：平移。**只改水平坐标**，显式**丢弃**服务端为新列回报的 y —— 那正是导致相机骑在地形上的值。

相机位置改为 `entrySurfaceY + altitude`，因此在整段会话里保持**恒定的绝对高度**，除非玩家主动用滚轮改变。客户端据 `ChunkLeapGodView.isActive()` 判断该走 `refocus` 还是 `enter`：已激活时即为平移。

顺带把屏幕的平移请求从「用相机坐标」改为 `focusX()/focusZ()`，语义更准确（请求的是水平聚焦点，与相机海拔无关）。

### 25.3 验证

- 单元测试全量通过；`runGameTestServer` 的 2 个 chunk_swap 测试通过（其余为既有不稳定项）；dev / release 构建均成功。
- 已确认旧字段 `targetY` 与 `height()` 无残留引用。
- **仍需实机确认**：跨越明显起伏的地形平移时相机是否保持同一绝对高度；以及滚轮调好的高度在多次平移后是否仍然保持。
