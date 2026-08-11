package dev.inkysketch.app;

final class RawInkSession implements RawInkAdapter.Listener {
    enum State { CLOSED, READY, DRAWING_ENABLED, UI_SUSPENDED }

    interface PresentationScheduler {
        void afterPresentation(Runnable continuation);
    }

    interface GestureSink {
        void onStroke(CompletedStroke stroke);
        void onErase(CompletedErase erase);
    }

    private final RawInkAdapter adapter;
    private final EditorController.MainThread mainThread;
    private final PresentationScheduler scheduler;
    private final GestureSink gestureSink;
    private State state = State.CLOSED;
    private RawInkAdapter.Config config;
    private boolean resumed;
    private boolean panelOpen;

    RawInkSession(RawInkAdapter adapter, EditorController.MainThread mainThread,
            PresentationScheduler scheduler, GestureSink gestureSink) {
        this.adapter = adapter;
        this.mainThread = mainThread;
        this.scheduler = scheduler;
        this.gestureSink = gestureSink;
        adapter.setListener(this);
    }

    State state() { return state; }

    void updateEditorState(EditorState editorState) {
        assertMainThread();
        RawInkAdapter.Config next = new RawInkAdapter.Config(editorState);
        panelOpen = editorState.panel != EditorState.Panel.NONE;
        boolean brushChanged = !next.sameBrush(config);
        config = next;
        if (state == State.CLOSED) return;
        if (brushChanged) adapter.configure(config);
        reconcile();
    }

    void surfaceCreated() {
        assertMainThread();
        if (state != State.CLOSED) return;
        adapter.open();
        state = State.READY;
        if (config != null) adapter.configure(config);
        reconcile();
    }

    void surfaceDestroyed() {
        assertMainThread();
        close();
    }

    void resume() {
        assertMainThread();
        if (resumed) return;
        resumed = true;
        reconcile();
    }

    void pause() {
        assertMainThread();
        if (!resumed) return;
        resumed = false;
        if (state == State.DRAWING_ENABLED) {
            adapter.disableDrawing();
            state = State.READY;
        }
    }

    void performUiAction(Runnable mutation, Runnable invalidate) {
        assertMainThread();
        suspend();
        mutation.run();
        invalidate.run();
        if (!panelOpen) schedulePresentationComplete();
    }

    void openPanel(Runnable mutation, Runnable invalidate) {
        assertMainThread();
        suspend();
        panelOpen = true;
        mutation.run();
        invalidate.run();
        state = State.UI_SUSPENDED;
    }

    void close() {
        assertMainThread();
        if (state == State.CLOSED) return;
        adapter.disableDrawing();
        adapter.close();
        state = State.CLOSED;
    }

    @Override public void onCompletedStroke(CompletedStroke stroke) {
        marshalGesture(() -> gestureSink.onStroke(stroke));
    }

    @Override public void onCompletedErase(CompletedErase erase) {
        marshalGesture(() -> gestureSink.onErase(erase));
    }

    private void marshalGesture(Runnable gesture) {
        Runnable work = () -> {
            suspend();
            gesture.run();
            schedulePresentationComplete();
        };
        if (mainThread.isMainThread()) work.run();
        else mainThread.post(work);
    }

    private void suspend() {
        if (state == State.DRAWING_ENABLED) adapter.disableDrawing();
        if (state != State.CLOSED) state = State.UI_SUSPENDED;
    }

    private void schedulePresentationComplete() {
        scheduler.afterPresentation(() -> {
            assertMainThread();
            if (state == State.UI_SUSPENDED && !panelOpen) state = State.READY;
            reconcile();
        });
    }

    private void reconcile() {
        if (state == State.CLOSED) return;
        boolean eligible = resumed && config != null && config.eligible && !panelOpen;
        if (eligible && state == State.READY) {
            adapter.enableDrawing();
            state = State.DRAWING_ENABLED;
        } else if (!eligible && state == State.DRAWING_ENABLED) {
            adapter.disableDrawing();
            state = panelOpen ? State.UI_SUSPENDED : State.READY;
        } else if (panelOpen) {
            state = State.UI_SUSPENDED;
        }
    }

    private void assertMainThread() {
        if (!mainThread.isMainThread()) throw new IllegalStateException("Raw ink session must run on main thread");
    }
}
