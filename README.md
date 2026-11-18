# WorldPanel

NeoForge mod for Minecraft 1.21.1.

The current code wraps vanilla inventory and container screens in a non-pausing screen, forwards movement keys, and uses a centered menu hit area to route mouse input. Camera movement is forwarded when the pointer is outside that area.

## Build
Requires Java 21. Run `gradle build`.

GitHub Actions builds the mod and uploads the JAR as a workflow artifact.

## Validation status
The repository contains an initial implementation, not a verified release. The build and in-game behavior still need to be checked on Minecraft 1.21.1. EMI/JEI compatibility, unusual modded container layouts, and Android/Zalith behavior are not yet confirmed.
