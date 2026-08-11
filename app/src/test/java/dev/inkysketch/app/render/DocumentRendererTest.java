package dev.inkysketch.app;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class DocumentRendererTest {
    @Test public void rendersVisibleLayersBottomToTopAndPreservesBrush() {
        InkDocument document = new InkDocument();
        String lower = document.selectedLayer().id;
        document.addStroke(stroke(InkDocument.Brush.PENCIL));
        InkDocument.Layer upper = document.addLayer();
        document.addStroke(stroke(InkDocument.Brush.MARKER));
        RecordingTarget target = new RecordingTarget();

        new DocumentRenderer(target).render(snapshot(document),
                RenderRequest.full(RenderRequest.Reason.SURFACE));

        assertEquals(Arrays.asList(lower + ":PENCIL", upper.id + ":MARKER"), target.draws);
    }

    @Test public void hiddenLayersDoNotReachRenderer() {
        InkDocument document = new InkDocument();
        document.addStroke(stroke(InkDocument.Brush.PEN));
        document.toggleSelectedLayerVisibility();
        RecordingTarget target = new RecordingTarget();
        new DocumentRenderer(target).render(snapshot(document),
                RenderRequest.full(RenderRequest.Reason.LAYER));
        assertEquals(0, target.draws.size());
    }

    @Test public void dirtyRenderRecomposesOnlyIntersectingVisibleInk() {
        InkDocument document = new InkDocument();
        document.addStroke(strokeAt("near", .2f, .2f));
        document.addStroke(strokeAt("far", .8f, .8f));
        RecordingTarget target = new RecordingTarget();

        new DocumentRenderer(target).render(snapshot(document), RenderRequest.dirty(
                RenderRequest.Reason.STROKE, new RenderRequest.DirtyRect(.1f, .1f, .3f, .3f)));

        assertEquals(1, target.draws.size());
        assertEquals("near", target.strokeIds.get(0));
    }

    private static EditorSnapshot snapshot(InkDocument document) {
        return new EditorSnapshot(document, new EditorState(EditorState.Tool.PEN, "fountain", 5f,
                0xFF000000, document.selectedLayer().id, false, false, true,
                EditorState.Panel.NONE, EditorState.SaveState.SAVED, 0));
    }

    private static InkDocument.Stroke stroke(InkDocument.Brush brush) {
        return new InkDocument.Stroke(brush, 5f, 0xFF000000,
                Arrays.asList(new InkDocument.Point(0.1f, 0.2f, 0.5f, 1L)));
    }

    private static InkDocument.Stroke strokeAt(String id, float x, float y) {
        return new InkDocument.Stroke(id, InkDocument.Brush.PEN, BrushCatalog.FOUNTAIN, 1,
                5f, 0xFF000000, Arrays.asList(new InkDocument.Point(x, y, .5f, 1L)));
    }

    private static final class RecordingTarget implements DocumentRenderer.Target {
        final List<String> draws = new ArrayList<>();
        final List<String> strokeIds = new ArrayList<>();
        @Override public boolean begin(RenderRequest request) { return true; }
        @Override public void draw(String layerId, InkDocument.Stroke stroke) {
            draws.add(layerId + ":" + stroke.brush);
            strokeIds.add(stroke.id);
        }
        @Override public void end() {}
    }
}
