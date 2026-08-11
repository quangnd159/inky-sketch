# Inky Sketch contributor rules

## Required verification

Use Java 17 and run these exact commands before opening or updating a pull request:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The combined CI command is `./gradlew testDebugUnitTest lintDebug assembleDebug`. Inspect the resulting APK with `aapt dump permissions app/build/outputs/apk/debug/app-debug.apk`; it must contain no `uses-permission` entry. Do not bypass wrapper validation or substitute a system Gradle installation.

## Raw callback rules

Code reached from raw stylus point-move callbacks must not allocate objects, write files, update Android views, mutate history, repaint the canvas, log per-point data, or perform work whose cost scales beyond the incoming point. The Onyx `TouchHelper` surface owns the live preview. Commit one document operation at pen-up, then hand an immutable snapshot to the single background writer.

## Layer rules

Layer mutations must remain undoable and preserve a valid selected layer. Rendering follows stored layer order and skips hidden layers. Erasing splits only intersected stroke segments and must not silently delete unaffected content. Document-format changes require migration coverage alongside the existing v1-to-layered migration.

## Repository boundary

This repository is the sole source repository for Inky Sketch. Never push, tag, publish, rewrite, or otherwise write to the Inkflow repository or its releases. The Android `applicationId` is permanently historical for installed-app upgrade and data continuity; do not change it. Source packages and the Gradle namespace use `dev.inkysketch.app`.

## Release rules

Normal validation runs with read-only repository contents permission on pull requests and `main`. Releases run only from a reviewed `v*` tag through `.github/workflows/release.yml`; only that workflow has `contents: write`. Before tagging, update `versionCode` and `versionName`, pass all required verification, inspect the no-permissions output, and record the physical-device matrix from `DESIGN.md`. Never create a release from an unreviewed branch, publish from a local workstation, force-move a release tag, or use automation to write unrelated repository state.
