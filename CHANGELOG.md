# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses versions of the form `<mod version>-<dev|release>` produced by
`./gradlew build -DisDev=<true|false>`.

## [Unreleased]

### Changed

- Gametest suites (`*GameTests`) and the client-side smoke validators now live in the
  `gametest` source set (`src/gametest/java`) instead of `src/main/java`. They are
  compiled as part of `check`, still loaded by development runs and `gameTestServer`
  through the moddev mod folder, and are no longer part of the shipped jar.
- `src/test` can see the `gametest` source set output, because the teleport tests pin
  behaviour against `ChunkSwapGameTests`.
- `.gitignore` no longer hides `.py` and `.txt` files repository-wide; both extensions
  are ignored only under the local-only trees `validation/`, `tools/` and `docs/`.

### Removed

- The local scratch directory `.tmp/` was moved out of the repository tree.

### Notes

- The structure smoke validators under `internal/common/structure/physics/validation`
  and `SableStructureSmoke` / `SableStructureRetentionSmoke` stay in `src/main/java`:
  `SableStructureBridge` loads them from the `academy.sable.smoke` system property, so
  they are runtime diagnostics rather than test-only code.

## [0.0.4-alpha] - 2026-10-18

### Added

- First changelog entry for the Minecraft 26.2 / NeoForge 26.2 line.
