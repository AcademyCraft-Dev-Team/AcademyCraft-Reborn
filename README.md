[🇨🇳 简体中文](README.zh-CN.md) | [🇺🇸 English](README.md)

# AcademyCraft-Reborn

A Minecraft mod about Academy City, for NeoForge.

The project is currently in the early development stage and is not playable. Developers are welcome to participate in the development.

## Downloads & Community

* **Latest Builds**: [GitHub Actions](https://github.com/AcademyCraft-Dev-Team/AcademyCraft-Reborn/actions)
* **Community (QQ Group)**: `217327418`

## For Developers
We recommend IntelliJ IDEA, and it requires JetBrains Runtime 25.

Enhanced class redefinition is disabled by default for Gradle runs. Enable structural IDE HotSwap explicitly when needed:

```bash
./gradlew runClientDev -DisDev=true -PacademyEnhancedHotSwap=true
```

This option is for development debugging. Intermittent native crashes were observed with the local JBR 25.0.3; see the [launch validation record](docs/structure_runtime_launch_2026_10_26.zh-CN.md) for the comparisons and their limits.

### If you need to use ClientDevWithRenderDoc to run the game:
```bash
./gradlew setupRenderDoc
```

### How to build?

```bash
./gradlew build
```

## License
This project is licensed under the **GPL-3.0**.
