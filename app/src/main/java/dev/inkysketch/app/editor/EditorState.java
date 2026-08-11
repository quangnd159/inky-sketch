package dev.inkysketch.app;

import java.util.Objects;

final class EditorState {
    enum Tool { PEN, PENCIL, MARKER, ERASER }
    enum Panel { NONE, LAYERS, LAYER_ACTIONS, RENAME, CONFIRM_CLEAR, CONFIRM_DELETE }
    enum SaveState { CLEAN, QUEUED }

    final Tool activeTool;
    final String activePresetId;
    final float width;
    final int tone;
    final String selectedLayerId;
    final boolean canUndo;
    final boolean canRedo;
    final boolean rawEligible;
    final Panel panel;
    final SaveState saveState;
    final long saveGeneration;

    EditorState(Tool activeTool, String activePresetId, float width, int tone,
            String selectedLayerId, boolean canUndo, boolean canRedo, boolean rawEligible,
            Panel panel, SaveState saveState, long saveGeneration) {
        this.activeTool = Objects.requireNonNull(activeTool);
        this.activePresetId = Objects.requireNonNull(activePresetId);
        this.width = width;
        this.tone = tone;
        this.selectedLayerId = Objects.requireNonNull(selectedLayerId);
        this.canUndo = canUndo;
        this.canRedo = canRedo;
        this.rawEligible = rawEligible;
        this.panel = Objects.requireNonNull(panel);
        this.saveState = Objects.requireNonNull(saveState);
        this.saveGeneration = saveGeneration;
    }
}
