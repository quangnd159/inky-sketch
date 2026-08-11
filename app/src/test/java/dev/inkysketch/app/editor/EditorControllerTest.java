package dev.inkysketch.app;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class EditorControllerTest {
    @Test public void noOpDoesNotEnterHistoryAndUndoRedoStayOrdered() {
        Fixture fixture = new Fixture();
        assertFalse(fixture.controller.dispatch(EditorCommand.renameLayer("Layer 1")));
        assertFalse(fixture.controller.dispatch(EditorCommand.undo()));
        assertTrue(fixture.controller.dispatch(EditorCommand.stroke(stroke(InkDocument.Brush.PEN))));
        assertEquals(1, fixture.controller.snapshot().document().selectedLayer().strokes.size());
        assertTrue(fixture.controller.snapshot().state.canUndo);
        assertTrue(fixture.controller.dispatch(EditorCommand.undo()));
        assertTrue(fixture.controller.snapshot().document().selectedLayer().strokes.isEmpty());
        assertTrue(fixture.controller.snapshot().state.canRedo);
        assertTrue(fixture.controller.dispatch(EditorCommand.redo()));
        assertEquals(1, fixture.controller.snapshot().document().selectedLayer().strokes.size());
    }

    @Test public void hiddenLayerRejectsStrokeAndDisablesRawEligibility() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.controller.dispatch(EditorCommand.toggleLayerVisibility()));
        assertFalse(fixture.controller.snapshot().state.rawEligible);
        int saves = fixture.persistence.saves;
        int renders = fixture.target.beginCount;
        assertFalse(fixture.controller.dispatch(EditorCommand.stroke(stroke(InkDocument.Brush.PENCIL))));
        assertEquals(saves, fixture.persistence.saves);
        assertEquals(renders, fixture.target.beginCount);
    }

    @Test public void offMainCommandsMarshalInSubmissionOrder() {
        Fixture fixture = new Fixture();
        fixture.main.main = false;
        fixture.controller.dispatchFromAnyThread(EditorCommand.tone(0xFF888888));
        fixture.controller.dispatchFromAnyThread(EditorCommand.width(9f));
        assertEquals(2, fixture.main.queue.size());
        assertEquals(0xFF000000, fixture.controller.snapshot().state.tone);
        fixture.main.main = true;
        fixture.main.queue.remove(0).run();
        assertEquals(0xFF888888, fixture.controller.snapshot().state.tone);
        assertEquals(5f, fixture.controller.snapshot().state.width, 0f);
        fixture.main.queue.remove(0).run();
        assertEquals(9f, fixture.controller.snapshot().state.width, 0f);
    }

    @Test public void eraseUndoAndVisibilityEachRequestExactlyOneRender() {
        Fixture fixture = new Fixture();
        fixture.controller.dispatch(EditorCommand.stroke(stroke(InkDocument.Brush.MARKER)));
        fixture.target.beginCount = 0;
        CompletedErase erase = new CompletedErase(Arrays.asList(point(0.1f, 0.2f)), 30f, 1000, 1000);
        assertTrue(fixture.controller.dispatch(EditorCommand.erase(erase)));
        assertEquals(1, fixture.target.beginCount);
        fixture.target.beginCount = 0;
        assertTrue(fixture.controller.dispatch(EditorCommand.undo()));
        assertEquals(1, fixture.target.beginCount);
        fixture.target.beginCount = 0;
        assertTrue(fixture.controller.dispatch(EditorCommand.toggleLayerVisibility()));
        assertEquals(1, fixture.target.beginCount);
    }

    @Test public void saveTransitionsAndRetryAreVisibleToChrome() {
        Fixture fixture = new Fixture();
        fixture.controller.dispatch(EditorCommand.stroke(stroke(InkDocument.Brush.PEN)));
        assertEquals(EditorState.SaveState.SAVING, fixture.controller.snapshot().state.saveState);
        fixture.persistence.complete(true);
        assertEquals(EditorState.SaveState.SAVED, fixture.controller.snapshot().state.saveState);
        fixture.controller.dispatch(EditorCommand.renameLayer("Draft"));
        fixture.persistence.complete(false);
        assertEquals(EditorState.SaveState.FAILED, fixture.controller.snapshot().state.saveState);
        assertTrue(fixture.controller.dispatch(EditorCommand.retrySave()));
        assertEquals(EditorState.SaveState.SAVING, fixture.controller.snapshot().state.saveState);
        fixture.persistence.complete(true);
        assertEquals(EditorState.SaveState.SAVED, fixture.controller.snapshot().state.saveState);
    }

    private static CompletedStroke stroke(InkDocument.Brush brush) {
        return new CompletedStroke(brush, 5f, 0xFF000000,
                Arrays.asList(point(0.1f, 0.2f), point(0.4f, 0.5f)));
    }

    private static InkDocument.Point point(float x, float y) {
        return new InkDocument.Point(x, y, 0.5f, 1L);
    }

    private static final class Fixture {
        final FakeMainThread main = new FakeMainThread();
        final FakePersistence persistence = new FakePersistence();
        final RecordingTarget target = new RecordingTarget();
        final EditorController controller = new EditorController(main, new InkDocument(), persistence,
                new DocumentRenderer(target));
    }

    static final class FakeMainThread implements EditorController.MainThread {
        boolean main = true;
        final List<Runnable> queue = new ArrayList<>();
        @Override public boolean isMainThread() { return main; }
        @Override public void post(Runnable runnable) { queue.add(runnable); }
    }

    static final class FakePersistence implements EditorController.Persistence {
        int saves;
        long generation;
        Callback callback;
        @Override public void save(InkDocument document, long generation, Callback callback) {
            saves++;
            this.generation = generation;
            this.callback = callback;
        }
        void complete(boolean success) { callback.onComplete(generation, success); }
        @Override public void close() {}
    }

    static final class RecordingTarget implements DocumentRenderer.Target {
        int beginCount;
        @Override public boolean begin(RenderRequest request) { beginCount++; return true; }
        @Override public void draw(String layerId, InkDocument.Stroke stroke) {}
        @Override public void end() {}
    }
}
