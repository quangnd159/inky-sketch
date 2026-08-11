package dev.inkysketch.app;

interface RawInkAdapter {
    final class Config {
        final EditorState.Tool tool;
        final String presetId;
        final float width;
        final int tone;
        final boolean eligible;

        Config(EditorState state) {
            tool = state.activeTool;
            presetId = state.activePresetId;
            width = state.width;
            tone = state.tone;
            eligible = state.rawEligible;
        }

        boolean sameBrush(Config other) {
            return other != null && tool == other.tool && presetId.equals(other.presetId)
                    && Float.compare(width, other.width) == 0 && tone == other.tone;
        }
    }

    interface Listener {
        void onCompletedStroke(CompletedStroke stroke);
        void onCompletedErase(CompletedErase erase);
    }

    void setListener(Listener listener);
    void open();
    void configure(Config config);
    void enableDrawing();
    void disableDrawing();
    void requestPartialRefresh(RenderRequest.DirtyRect dirtyRect);
    void close();
}
