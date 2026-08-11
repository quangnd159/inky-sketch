package dev.inkysketch.app;

final class EditorCommand {
    enum Type {
        SET_TOOL, SET_WIDTH, SET_TONE, SELECT_LAYER, SET_PANEL,
        ADD_STROKE, ERASE, ADD_LAYER, RENAME_LAYER, MOVE_LAYER,
        TOGGLE_LAYER_VISIBILITY, CLEAR_LAYER, DELETE_LAYER, UNDO, REDO
    }

    final Type type;
    final Object value;

    private EditorCommand(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    static EditorCommand tool(EditorState.Tool value) { return new EditorCommand(Type.SET_TOOL, value); }
    static EditorCommand width(float value) { return new EditorCommand(Type.SET_WIDTH, value); }
    static EditorCommand tone(int value) { return new EditorCommand(Type.SET_TONE, value); }
    static EditorCommand selectLayer(String id) { return new EditorCommand(Type.SELECT_LAYER, id); }
    static EditorCommand panel(EditorState.Panel panel) { return new EditorCommand(Type.SET_PANEL, panel); }
    static EditorCommand stroke(CompletedStroke stroke) { return new EditorCommand(Type.ADD_STROKE, stroke); }
    static EditorCommand erase(CompletedErase erase) { return new EditorCommand(Type.ERASE, erase); }
    static EditorCommand addLayer() { return new EditorCommand(Type.ADD_LAYER, null); }
    static EditorCommand renameLayer(String name) { return new EditorCommand(Type.RENAME_LAYER, name); }
    static EditorCommand moveLayer(int delta) { return new EditorCommand(Type.MOVE_LAYER, delta); }
    static EditorCommand toggleLayerVisibility() { return new EditorCommand(Type.TOGGLE_LAYER_VISIBILITY, null); }
    static EditorCommand clearLayer() { return new EditorCommand(Type.CLEAR_LAYER, null); }
    static EditorCommand deleteLayer() { return new EditorCommand(Type.DELETE_LAYER, null); }
    static EditorCommand undo() { return new EditorCommand(Type.UNDO, null); }
    static EditorCommand redo() { return new EditorCommand(Type.REDO, null); }
}
