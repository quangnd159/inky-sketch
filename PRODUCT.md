# Inky Sketch product contract

## Target user

Inky Sketch is for a BOOX or Android e-ink owner who wants a dependable scratchpad for handwriting, diagrams, and small drawings. The primary user values instant access, legible monochrome controls, low pen latency, and ownership of a local drawing more than a broad creative suite.

## Core promise

Open and sketch. Launching the app goes directly to the canvas with the last local drawing ready. Drawing, erasing, layer editing, undo, redo, and saving all work offline. The app has no account, telemetry, cloud dependency, advertising, runtime network use, or requested Android permissions.

## Tiny tool set

- Pressure-aware pen, pencil, and marker brushes.
- Black, dark gray, light gray, and white drawing values.
- Three explicit brush sizes.
- A pixel/segment eraser that preserves unaffected stroke segments.
- A small layer stack with add, select, rename, reorder, visibility, clear, and delete.
- Undo and redo for marks, erasing, and layer mutations.
- Automatic app-private saving with last-good backup recovery.

The tool set stays intentionally small. A new control must earn its permanent canvas space and remain usable without onboarding.

## Non-goals

- Accounts, sharing, collaboration, cloud sync, galleries, or social features.
- Import/export systems, file browsers, or storage permission requests.
- Infinite canvases, page management, templates, or presentation tools.
- Color illustration, photo editing, text layout, vector authoring, or desktop parity.
- Brush marketplaces, plug-ins, scripting, AI generation, or asset libraries.
- Animated polish that delays input or causes unnecessary e-ink refreshes.

These are product boundaries, not a backlog. Any proposal that crosses one requires an explicit product decision before implementation.
