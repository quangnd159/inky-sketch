package dev.inkysketch.app;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RawInkSessionTest {
    @Test public void surfacePauseAndRecreateTransitionsAreIdempotent() {
        Fixture fixture = new Fixture();
        fixture.session.updateEditorState(state(EditorState.Tool.PEN, EditorState.Panel.NONE, true));
        fixture.session.resume();
        fixture.session.surfaceCreated();
        assertEquals(RawInkSession.State.DRAWING_ENABLED, fixture.session.state());
        assertEquals(list("open", "configure:fountain", "enable"), fixture.adapter.log);
        fixture.session.pause();
        fixture.session.pause();
        assertEquals(RawInkSession.State.READY, fixture.session.state());
        fixture.session.surfaceDestroyed();
        fixture.session.surfaceDestroyed();
        fixture.session.surfaceCreated();
        assertEquals(list("open", "configure:fountain", "enable", "disable", "disable", "close",
                "open", "configure:fountain"), fixture.adapter.log);
    }

    @Test public void toolMutationDisablesBeforeMutationAndEnablesAfterPresentation() {
        Fixture fixture = new Fixture();
        fixture.openEnabled();
        fixture.adapter.log.clear();
        fixture.session.performUiAction(() -> {
            fixture.adapter.log.add("mutation");
            fixture.session.updateEditorState(state(EditorState.Tool.PENCIL, EditorState.Panel.NONE, true));
        }, () -> fixture.adapter.log.add("invalidate"));
        assertEquals(list("disable", "mutation", "configure:pencil", "invalidate"), fixture.adapter.log);
        assertEquals(RawInkSession.State.UI_SUSPENDED, fixture.session.state());
        fixture.scheduler.present();
        assertEquals("enable", fixture.adapter.log.get(fixture.adapter.log.size() - 1));
    }

    @Test public void panelAndHiddenLayerNeverEnableUntilEligiblePresentation() {
        Fixture fixture = new Fixture();
        fixture.openEnabled();
        fixture.adapter.log.clear();
        fixture.session.openPanel(() -> fixture.session.updateEditorState(
                state(EditorState.Tool.PEN, EditorState.Panel.LAYERS, false)), () -> {});
        assertEquals(RawInkSession.State.UI_SUSPENDED, fixture.session.state());
        assertEquals(list("disable"), fixture.adapter.log);
        assertFalse(fixture.scheduler.hasPending());
        fixture.session.performUiAction(() -> fixture.session.updateEditorState(
                state(EditorState.Tool.PEN, EditorState.Panel.NONE, false)), () -> {});
        fixture.scheduler.present();
        assertEquals(RawInkSession.State.READY, fixture.session.state());
        assertFalse(fixture.adapter.log.contains("enable"));
    }

    @Test public void completedGestureMarshalsToMainBeforePresentationResume() {
        Fixture fixture = new Fixture();
        fixture.openEnabled();
        fixture.main.main = false;
        fixture.adapter.emitStroke();
        assertEquals(0, fixture.strokeCount);
        assertEquals(1, fixture.main.queue.size());
        fixture.main.main = true;
        fixture.main.queue.remove(0).run();
        assertEquals(1, fixture.strokeCount);
        assertEquals(RawInkSession.State.UI_SUSPENDED, fixture.session.state());
        fixture.scheduler.present();
        assertEquals(RawInkSession.State.DRAWING_ENABLED, fixture.session.state());
    }

    private static EditorState state(EditorState.Tool tool, EditorState.Panel panel, boolean eligible) {
        return new EditorState(tool, tool == EditorState.Tool.PENCIL ? "pencil" : "fountain", 5f,
                0xFF000000, "layer", false, false, eligible, panel, EditorState.SaveState.SAVED, 0);
    }

    private static List<String> list(String... values) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, values);
        return result;
    }

    private static final class Fixture {
        final FakeMain main = new FakeMain();
        final FakeScheduler scheduler = new FakeScheduler();
        final FakeAdapter adapter = new FakeAdapter();
        int strokeCount;
        final RawInkSession session = new RawInkSession(adapter, main, scheduler,
                new RawInkSession.GestureSink() {
                    @Override public void onStroke(CompletedStroke stroke) { strokeCount++; }
                    @Override public void onErase(CompletedErase erase) {}
                });

        void openEnabled() {
            session.updateEditorState(state(EditorState.Tool.PEN, EditorState.Panel.NONE, true));
            session.resume();
            session.surfaceCreated();
        }
    }

    private static final class FakeMain implements EditorController.MainThread {
        boolean main = true;
        final List<Runnable> queue = new ArrayList<>();
        @Override public boolean isMainThread() { return main; }
        @Override public void post(Runnable runnable) { queue.add(runnable); }
    }

    private static final class FakeScheduler implements RawInkSession.PresentationScheduler {
        Runnable pending;
        @Override public void afterPresentation(Runnable continuation) { pending = continuation; }
        boolean hasPending() { return pending != null; }
        void present() { Runnable next = pending; pending = null; next.run(); }
    }

    private static final class FakeAdapter implements RawInkAdapter {
        final List<String> log = new ArrayList<>();
        Listener listener;
        @Override public void setListener(Listener listener) { this.listener = listener; }
        @Override public void open() { log.add("open"); }
        @Override public void configure(Config config) { log.add("configure:" + config.presetId); }
        @Override public void enableDrawing() { log.add("enable"); }
        @Override public void disableDrawing() { log.add("disable"); }
        @Override public void requestPartialRefresh(RenderRequest.DirtyRect dirtyRect) {}
        @Override public void close() { log.add("close"); }
        void emitStroke() {
            listener.onCompletedStroke(new CompletedStroke(InkDocument.Brush.PEN, 5f, 0xFF000000,
                    Collections.singletonList(new InkDocument.Point(0.1f, 0.1f, 0.5f, 1L))));
        }
    }
}
