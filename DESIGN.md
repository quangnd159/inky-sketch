# Inky Sketch design contract

## Visual system

Application chrome is strictly 1-bit: black `#000000` and white `#FFFFFF`. Do not use alpha, translucent overlays, gradients, blur, animation, ripple effects, shadows, or elevation. Grayscale values belong to artwork only and must never be the sole signal for control state.

Every selected control has redundant state: a black/white fill inversion plus a persistent shape, border, label, or icon change. Selection must remain obvious through ghosting, glare, and reduced contrast.

Interactive targets are at least 48 dp in both dimensions. A 2 dp black keyline defines tool groups, dialogs, panels, and focused boundaries. Layout should favor stable large regions over floating surfaces or transient decoration.

## Interaction and refresh

- Stylus-down begins raw input without waiting for a UI refresh.
- Raw point-move callbacks allocate nothing and perform no persistence, view mutation, history mutation, or canvas repaint.
- The Onyx surface owns the live stroke or eraser preview. The document changes once at pen-up.
- Avoid full-screen refreshes during a stroke. Refresh the smallest stable region after the committed operation.
- Keep tool and layer panels spatially stable. Opening or closing a panel must not animate or shift the drawing underneath it.
- Coalesce non-urgent chrome refreshes. Use a deliberate full refresh only when accumulated ghosting harms state recognition.
- A panel must remain operable with touch as well as the stylus, without hover, long-press discovery, or timed gestures.

## Artwork and chrome separation

The document may use black, dark gray, light gray, and white. Chrome remains black and white even when previewing a grayscale brush: identify the artwork value with a labeled or patterned 1-bit swatch. Never infer command state by sampling rendered artwork.

## Physical-device matrix

Every UI or input change must be checked on physical hardware before release:

| Device class | Input checks | Display checks |
| --- | --- | --- |
| BOOX phone-size e-ink | Stylus and finger targets, edge reach, rotation | Selected state, panel keylines, ghosting |
| BOOX tablet-size e-ink | Raw pen latency, pressure, palm behavior | Canvas/panel refresh boundaries, grayscale separation |
| Conventional Android LCD/OLED | Touch fallback, lifecycle, rotation | Geometry and accessibility contrast only |

At minimum, release notes record the BOOX model, Android/firmware version, orientation, brush/eraser coverage, layer-panel coverage, autosave recovery check, and whether a manual full refresh was needed.
