package dev.inkysketch.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Small UI-only preference store; document persistence remains independently atomic. */
final class EditorPreferences {
    private static final String PRESET = "brush_preset_v1";
    private static final String WIDTH = "brush_width_v1";
    private static final String TONE = "brush_tone_v1";
    private final SharedPreferences preferences;

    EditorPreferences(Context context) {
        preferences = context.getSharedPreferences("editor_preferences", Context.MODE_PRIVATE);
    }

    void restore(EditorController controller) {
        String preset = preferences.getString(PRESET, BrushCatalog.FOUNTAIN);
        if (!BrushCatalog.isKnown(preset)) preset = BrushCatalog.FOUNTAIN;
        float width = Math.max(1f, Math.min(12f, preferences.getFloat(WIDTH, 5f)));
        int tone = preferences.getInt(TONE, 0xFF000000);
        controller.dispatch(EditorCommand.preset(preset));
        controller.dispatch(EditorCommand.width(width));
        controller.dispatch(EditorCommand.tone(tone));
    }

    void save(EditorState state) {
        if (state.activeTool == EditorState.Tool.ERASER) return;
        preferences.edit()
                .putString(PRESET, state.activePresetId)
                .putFloat(WIDTH, state.width)
                .putInt(TONE, state.tone)
                .apply();
    }
}
