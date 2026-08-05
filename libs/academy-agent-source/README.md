# AcademyAgent source backup

This directory preserves the former embedded Academy Java agent and its main-mod integration
sources for future optional add-on development. Nothing below this directory is compiled,
loaded, or packaged by the main AcademyCraft project.

Contents:

- `academy-agent/`: the standalone `premain`/`agentmain` project and dynamic attach launcher.
- `integration/`: the former main-mod bootstrap, handoff, instrumentation, klass-pointer
  transformer, and fixed dispatch player classes.

The active implementation no longer depends on these sources. An add-on based on this backup
must supply its own build, version checks, and compatibility contract with the main mod.
