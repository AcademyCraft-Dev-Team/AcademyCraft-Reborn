[🇺🇸 English](README.md) | [🇨🇳 简体中文](README.zh-CN.md)

# AcademyCraft-Reborn

一个关于学园都市的 Minecraft 模组，适用于 NeoForge。

项目目前处于早期开发阶段，无法进行游玩，欢迎各位开发者参与开发。

## 下载与社区

* **最新构建**: [GitHub Actions](https://github.com/AcademyCraft-Dev-Team/AcademyCraft-Reborn/actions)
* **社区 (QQ 群)**: `217327418`

## 开发者指南
推荐 IntelliJ IDEA，并且需要 JetBrains Runtime 25。

Gradle 启动默认关闭增强类重定义。需要 IDE 的结构性热替换时，显式使用：

```bash
./gradlew runClientDev -DisDev=true -PacademyEnhancedHotSwap=true
```

此选项用于开发调试。本机 JBR 25.0.3 曾出现间歇性原生崩溃，对照结果和适用边界见[启动配置验证记录](docs/structure_runtime_launch_2026_10_26.zh-CN.md)。

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
