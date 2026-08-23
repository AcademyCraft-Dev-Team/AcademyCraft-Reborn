[🇨🇳 简体中文](README.zh-CN.md) | [🇺🇸 English](README.md)

# AcademyCraft-Reborn

A Minecraft mod about Academy City, for NeoForge.

The project is currently in the early development stage and is not playable. Developers are welcome to participate in the development.

## Downloads & Community

* **Latest Builds**: [GitHub Actions](https://github.com/AcademyCraft-Dev-Team/AcademyCraft-Reborn/actions)
* **Community (QQ Group)**: `217327418`

## For Developers
We recommend IntelliJ IDEA, and it requires JetBrains Runtime 25.

Current design, skill, command, and acceptance documents are listed in the [documentation index](docs/README.md).

### If you need to use ClientDevWithRenderDoc to run the game:
```bash
./gradlew setupRenderDoc
```

### How to build?

```bash
./gradlew build
```

### VFX / Shader Graph editor

AcademyCraft includes a Unity-like visual Shader Graph + VFX Graph editor (desktop tool) with
an in-game runtime. See [docs/vfx-graph/USER_GUIDE.md](docs/vfx-graph/USER_GUIDE.md) for usage,
or the full roadmap at [docs/vfx-graph/EDITOR_ROADMAP.md](docs/vfx-graph/EDITOR_ROADMAP.md).

Run the editor (requires a display):

```bash
./gradlew graphEditor
```

In-game, spawn a graph effect via `/academy vfx spawn <graph> [x y z]` (e.g. `demo_burst`).

## License
This project is licensed under the **GPL-3.0**.
