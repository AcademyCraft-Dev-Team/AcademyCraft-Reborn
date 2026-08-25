[🇺🇸 English](README.md) | [🇨🇳 简体中文](README.zh-CN.md)

# AcademyCraft-Reborn

一个关于学园都市的 Minecraft 模组，适用于 NeoForge。

项目目前处于早期开发阶段，无法进行游玩，欢迎各位开发者参与开发。

## 下载与社区

* **最新构建**: [GitHub Actions](https://github.com/AcademyCraft-Dev-Team/AcademyCraft-Reborn/actions)
* **社区 (QQ 群)**: `217327418`

## 开发者指南
推荐 IntelliJ IDEA，并且需要 JetBrains Runtime 25。

现行设计、技能、命令和验收文档参见 [文档索引](docs/README.md)；完整命令说明参见 [AcademyCraft 命令参考](docs/COMMANDS.zh-CN.md)。

### 图形化 Shader / VFX 编辑器

AcademyCraft 内置类 Unity 的图形化 Shader Graph + VFX Graph 编辑器（桌面工具）与游戏内图运行时。
使用说明见 [docs/vfx-graph/USER_GUIDE.md](docs/vfx-graph/USER_GUIDE.md)，完整路线图见
[docs/vfx-graph/EDITOR_ROADMAP.md](docs/vfx-graph/EDITOR_ROADMAP.md)。

启动编辑器（需要显示环境）：

```bash
./gradlew graphEditor
```

游戏内可用 `/academy vfx spawn <graph> [x y z]` 命令 spawn 图效果（如 `demo_burst`）。

### 如果你需要使用 ClientDevWithRenderDoc 运行游戏
```bash
./gradlew setupRenderDoc
```

### 如何构建？

```bash
./gradlew build
```

## 许可证
本项目属于 **GPL-3.0** 许可证。
