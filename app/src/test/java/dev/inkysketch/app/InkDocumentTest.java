package dev.inkysketch.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InkDocumentTest {
    @Test
    public void segmentEraserSplitsInsteadOfDeletingStroke() {
        InkDocument document = new InkDocument();
        document.addStroke(new InkDocument.Stroke(
                InkDocument.Brush.PEN,
                5f,
                0xFF000000,
                Arrays.asList(point(0.1f, 0.5f), point(0.4f, 0.5f), point(0.6f, 0.5f), point(0.9f, 0.5f))
        ));

        assertTrue(document.eraseAt(0.5f, 0.5f, 30f, 1000, 1000));
        assertEquals(3, document.selectedLayer().strokes.size());
        for (InkDocument.Stroke fragment : document.selectedLayer().strokes) {
            assertTrue(fragment.points.size() > 1);
            assertEquals(InkDocument.Brush.PEN, fragment.brush);
            assertEquals(3f, fragment.width, 0f);
        }
        assertEquals(3, document.selectedLayer().strokes.get(0).points.size());
        assertEquals(3, document.selectedLayer().strokes.get(1).points.size());
    }

    @Test public void sparseEraserSegmentCutsSparseStrokeAndPreservesMetadata() {
        InkDocument document = new InkDocument();
        document.addStroke(new InkDocument.Stroke("affected", InkDocument.Brush.MARKER, "marker.v1", 7,
                9f, 0xFF555555, Arrays.asList(point(.1f, .5f), point(.9f, .5f))));
        document.addStroke(new InkDocument.Stroke("untouched", InkDocument.Brush.PEN, "fountain.v1", 3,
                4f, 0xFF000000, Arrays.asList(point(.1f, .1f), point(.9f, .1f))));
        assertTrue(document.eraseGesture(Arrays.asList(point(.5f, .2f), point(.5f, .8f)), 25f, 1000, 1000));
        assertEquals(3, document.selectedLayer().strokes.size());
        InkDocument.Stroke untouched = document.selectedLayer().strokes.get(2);
        assertEquals("untouched", untouched.id);
        for (int i = 0; i < 2; i++) {
            InkDocument.Stroke fragment = document.selectedLayer().strokes.get(i);
            assertEquals("marker.v1", fragment.presetId);
            assertEquals(7, fragment.presetVersion);
            assertEquals(9f, fragment.width, 0f);
            assertEquals(0xFF555555, fragment.color);
        }
    }

    @Test public void dotAndRepeatedCrossingsAreErasedOnce() {
        InkDocument document = new InkDocument();
        document.addStroke(new InkDocument.Stroke(InkDocument.Brush.PEN, 3f, 0,
                Arrays.asList(point(.5f, .5f))));
        document.addStroke(new InkDocument.Stroke(InkDocument.Brush.PEN, 3f, 0,
                Arrays.asList(point(.1f,.5f), point(.9f,.5f), point(.1f,.5f))));
        assertTrue(document.eraseGesture(Arrays.asList(point(.5f,.5f), point(.5f,.5f)), 20f, 1000, 1000));
        assertEquals(2, document.selectedLayer().strokes.size());
    }

    @Test public void boundsRejectTenThousandDistantStrokesBeforeGeometry() {
        List<InkDocument.Stroke> strokes = new ArrayList<>();
        for (int i = 0; i < 10000; i++) strokes.add(new InkDocument.Stroke("far-" + i,
                InkDocument.Brush.PEN, 2f, 0, Arrays.asList(point(.9f,.9f), point(1f,1f))));
        SegmentEraser.Result result = SegmentEraser.erase(strokes,
                Arrays.asList(point(.1f,.1f)), 10f, 1000, 1000);
        assertFalse(result.changed);
        assertEquals(0, result.candidates);
        assertEquals(10000, result.rejected);
    }

    @Test
    public void layersCopyAndRestoreAsIndependentHistorySnapshots() {
        InkDocument document = new InkDocument();
        document.renameSelectedLayer("Sketch");
        InkDocument before = document.copy();

        document.addLayer();
        document.renameSelectedLayer("Ink");
        document.toggleSelectedLayerVisibility();
        assertEquals(2, document.layers().size());
        assertFalse(document.selectedLayer().visible);

        document.replaceWith(before);
        assertEquals(1, document.layers().size());
        assertEquals("Sketch", document.selectedLayer().name);
        assertTrue(document.selectedLayer().visible);
    }

    @Test
    public void legacyFlatCanvasMigratesToLayeredVersion() throws Exception {
        JSONObject legacy = new JSONObject();
        legacy.put("version", 1);
        JSONObject stroke = new JSONObject();
        stroke.put("width", 5);
        stroke.put("points", new JSONArray().put(new JSONArray().put(0.25).put(0.75).put(0.6).put(1)));
        legacy.put("strokes", new JSONArray().put(stroke));

        InkDocument migrated = InkDocument.fromJson(legacy);

        assertEquals("Imported canvas", migrated.selectedLayer().name);
        assertEquals(1, migrated.selectedLayer().strokes.size());
        assertEquals(InkDocument.Brush.PEN, migrated.selectedLayer().strokes.get(0).brush);
        assertEquals(InkDocument.FORMAT_VERSION, migrated.toJson().getInt("version"));
    }

    private static InkDocument.Point point(float x, float y) {
        return new InkDocument.Point(x, y, 0.5f, 1L);
    }
}
