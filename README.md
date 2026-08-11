# Inky Sketch

Inky Sketch is a tiny, offline, e-ink-first drawing app for Android and BOOX. Open the app and sketch immediately: there is no account, cloud service, setup flow, or permission prompt.

The editor provides pressure-aware pen, pencil, and marker brushes, a four-tone palette, three brush sizes, a pixel/segment eraser, layers, undo and redo, automatic local saving, and permission-free export. Live BOOX pen input uses Onyx's raw pen pipeline so ink can appear ahead of Android's normal compositor.

## Privacy and storage

Inky Sketch requests no Android permissions and makes no network connection at runtime. The current drawing is stored in app-private storage with atomic replacement and a last-good recovery backup.

## Export

Tap **Export** and choose a flattened PNG image or an editable `.inky` project. Android's
system document picker chooses the destination, so Inky Sketch never requests broad storage
access. Hidden layers are omitted from PNG output; `.inky` retains the complete layer stack.

## Build

Use Java 17 and the checked-in Gradle wrapper:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Android SDK 35 is required. The build retrieves Onyx Pen SDK 1.5.4 from the BOOX Maven repository.

Tagged releases are built and signed only in GitHub Actions. Release signing material is
provided through repository secrets; keystores and passwords must never be committed.
The workflow verifies the signature, legacy package ID, version, and zero-permission
contract before publishing the APK and its SHA-256 checksum as a GitHub prerelease.

## Performance contract

Raw point-move callbacks perform no allocations, persistence, UI updates, or canvas repainting. Onyx renders the live stroke or eraser preview. At pen-up, Inky Sketch commits one document operation and schedules an immutable snapshot on a single background writer.

## BOOX display guidance

For final inspection, use the BOOX HD/highest-clarity refresh mode and tap Refresh after
editing. Fast mode reduces latency for active sketching but can leave more ghosting and less
crisp UI edges; switch back to highest clarity when reviewing layers or finished artwork.

See [PRODUCT.md](PRODUCT.md) for product scope, [DESIGN.md](DESIGN.md) for the e-ink interface contract, and [AGENTS.md](AGENTS.md) for contributor rules.
