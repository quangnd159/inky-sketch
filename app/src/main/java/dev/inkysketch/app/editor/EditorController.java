package dev.inkysketch.app;

import java.util.ArrayList;
import java.util.List;

final class EditorController {
    interface MainThread {
        boolean isMainThread();
        void post(Runnable runnable);
    }

    interface Persistence {
        interface Callback { void onComplete(long generation, boolean success); }
        void save(InkDocument document, long generation, Callback callback);
        void close();
    }

    interface Listener {
        void onEditorChanged(EditorSnapshot snapshot);
    }

    private static final int HISTORY_LIMIT = 50;
    private final MainThread mainThread;
    private final Persistence persistence;
    private final DocumentRenderer renderer;
    private final History history = new History(HISTORY_LIMIT);
    private final List<Listener> listeners = new ArrayList<>();
    private InkDocument document;
    private EditorState.Tool tool = EditorState.Tool.PEN;
    private String presetId = "fountain.v1";
    private float width = 5f;
    private int tone = 0xFF000000;
    private EditorState.Panel panel = EditorState.Panel.NONE;
    private EditorState.SaveState saveState = EditorState.SaveState.SAVED;
    private long saveGeneration;

    EditorController(MainThread mainThread, InkDocument document, Persistence persistence,
            DocumentRenderer renderer) {
        this.mainThread = mainThread;
        this.document = document;
        this.persistence = persistence;
        this.renderer = renderer;
    }

    void addListener(Listener listener) {
        assertMainThread();
        listeners.add(listener);
        listener.onEditorChanged(snapshot());
    }

    boolean dispatch(EditorCommand command) {
        assertMainThread();
        boolean changed;
        switch (command.type) {
            case SET_TOOL:
                changed = setTool((EditorState.Tool) command.value);
                break;
            case SET_PRESET:
                changed = setPreset((String) command.value);
                break;
            case SET_WIDTH:
                changed = setWidth((Float) command.value);
                break;
            case SET_TONE:
                changed = setTone((Integer) command.value);
                break;
            case SELECT_LAYER:
                changed = selectLayer((String) command.value);
                break;
            case SET_PANEL:
                changed = setPanel((EditorState.Panel) command.value);
                break;
            case UNDO:
                changed = restoreHistory(true);
                break;
            case REDO:
                changed = restoreHistory(false);
                break;
            case RETRY_SAVE:
                changed = retrySave();
                break;
            default:
                changed = mutateDocument(command);
                break;
        }
        if (changed) notifyListeners();
        return changed;
    }

    void dispatchFromAnyThread(EditorCommand command) {
        if (mainThread.isMainThread()) dispatch(command);
        else mainThread.post(() -> dispatch(command));
    }

    EditorSnapshot snapshot() {
        return new EditorSnapshot(document, state());
    }

    void requestRender(RenderRequest request) {
        assertMainThread();
        renderer.render(snapshot(), request);
    }

    void onPause() {
        assertMainThread();
        if (saveState == EditorState.SaveState.FAILED) queueSave();
    }

    void close() {
        assertMainThread();
        persistence.close();
    }

    private boolean setTool(EditorState.Tool selected) {
        String selectedPreset = selected == EditorState.Tool.PEN ? "fountain.v1"
                : selected == EditorState.Tool.PENCIL ? "pencil-hb.v1"
                : selected == EditorState.Tool.MARKER ? "marker.v1" : "eraser";
        if (tool == selected && presetId.equals(selectedPreset)) return false;
        tool = selected;
        presetId = selectedPreset;
        return true;
    }

    private boolean setPreset(String selected) {
        EditorState.Tool selectedTool;
        if ("pencil-hb.v1".equals(selected) || "pencil-soft.v1".equals(selected)) {
            selectedTool = EditorState.Tool.PENCIL;
        } else if ("marker.v1".equals(selected)) {
            selectedTool = EditorState.Tool.MARKER;
        } else {
            selectedTool = EditorState.Tool.PEN;
        }
        if (presetId.equals(selected) && tool == selectedTool) return false;
        presetId = selected;
        tool = selectedTool;
        return true;
    }

    private boolean setWidth(float selected) {
        boolean changed = Float.compare(width, selected) != 0 || tool == EditorState.Tool.ERASER;
        width = selected;
        if (tool == EditorState.Tool.ERASER) { tool = EditorState.Tool.PEN; presetId = "fountain.v1"; }
        return changed;
    }

    private boolean setTone(int selected) {
        boolean changed = tone != selected || tool == EditorState.Tool.ERASER;
        tone = selected;
        if (tool == EditorState.Tool.ERASER) { tool = EditorState.Tool.PEN; presetId = "fountain.v1"; }
        return changed;
    }

    private boolean selectLayer(String id) {
        if (document.selectedLayer().id.equals(id) || !document.selectLayer(id)) return false;
        queueSave();
        return true;
    }

    private boolean setPanel(EditorState.Panel selected) {
        if (panel == selected) return false;
        panel = selected;
        return true;
    }

    private boolean mutateDocument(EditorCommand command) {
        InkDocument before = document.copy();
        boolean changed;
        RenderRequest.Reason reason = RenderRequest.Reason.LAYER;
        switch (command.type) {
            case ADD_STROKE:
                CompletedStroke completed = (CompletedStroke) command.value;
                changed = document.selectedLayer().visible && !completed.points.isEmpty();
                if (changed) document.addStroke(new InkDocument.Stroke(java.util.UUID.randomUUID().toString(),
                        completed.brush, completed.presetId, 1, completed.width, completed.tone, completed.points));
                reason = RenderRequest.Reason.STROKE;
                break;
            case ERASE:
                CompletedErase erase = (CompletedErase) command.value;
                changed = false;
                if (document.selectedLayer().visible) {
                    for (InkDocument.Point point : erase.points) {
                        changed |= document.eraseAt(point.x, point.y, erase.radiusPixels,
                                erase.viewportWidth, erase.viewportHeight);
                    }
                }
                reason = RenderRequest.Reason.ERASE;
                break;
            case ADD_LAYER:
                document.addLayer();
                changed = true;
                break;
            case RENAME_LAYER:
                String name = ((String) command.value).trim();
                changed = !name.isEmpty() && !name.equals(document.selectedLayer().name);
                if (changed) document.renameSelectedLayer(name);
                break;
            case MOVE_LAYER:
                changed = document.moveSelectedLayer((Integer) command.value);
                break;
            case TOGGLE_LAYER_VISIBILITY:
                document.toggleSelectedLayerVisibility();
                changed = true;
                break;
            case CLEAR_LAYER:
                changed = document.clearSelectedLayer();
                break;
            case DELETE_LAYER:
                changed = document.layers().size() > 1 || !document.selectedLayerIsEmpty();
                if (changed) document.deleteSelectedLayer();
                break;
            default:
                throw new IllegalArgumentException("Not a document command: " + command.type);
        }
        if (!changed) return false;
        history.record(before);
        queueSave();
        renderer.render(snapshot(), RenderRequest.full(reason));
        return true;
    }

    private boolean restoreHistory(boolean undo) {
        InkDocument replacement = undo ? history.undo(document) : history.redo(document);
        if (replacement == null) return false;
        document.replaceWith(replacement);
        queueSave();
        renderer.render(snapshot(), RenderRequest.full(
                undo ? RenderRequest.Reason.UNDO : RenderRequest.Reason.REDO));
        return true;
    }

    private void queueSave() {
        saveGeneration++;
        saveState = EditorState.SaveState.SAVING;
        long generation = saveGeneration;
        persistence.save(document, generation, this::onSaveComplete);
    }

    private boolean retrySave() {
        if (saveState != EditorState.SaveState.FAILED) return false;
        queueSave();
        return true;
    }

    private void onSaveComplete(long generation, boolean success) {
        Runnable completion = () -> {
            if (generation != saveGeneration) return;
            saveState = success ? EditorState.SaveState.SAVED : EditorState.SaveState.FAILED;
            notifyListeners();
        };
        if (mainThread.isMainThread()) completion.run();
        else mainThread.post(completion);
    }

    private EditorState state() {
        InkDocument.Layer selected = document.selectedLayer();
        boolean eligible = selected.visible && panel == EditorState.Panel.NONE;
        return new EditorState(tool, presetId, width, tone, selected.id,
                history.canUndo(), history.canRedo(), eligible, panel, saveState, saveGeneration);
    }

    private void notifyListeners() {
        EditorSnapshot snapshot = snapshot();
        for (Listener listener : new ArrayList<>(listeners)) listener.onEditorChanged(snapshot);
    }

    private void assertMainThread() {
        if (!mainThread.isMainThread()) throw new IllegalStateException("Editor dispatch must run on the main thread");
    }

}
