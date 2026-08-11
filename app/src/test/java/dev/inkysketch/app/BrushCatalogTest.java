package dev.inkysketch.app;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class BrushCatalogTest {
    @Test public void presetIdsAndDefinitionsArePinned() {
        String[] ids = { BrushCatalog.FINELINER, BrushCatalog.FOUNTAIN,
                BrushCatalog.BALLPOINT, BrushCatalog.PENCIL_HB,
                BrushCatalog.PENCIL_SOFT, BrushCatalog.MARKER };
        Set<String> names = new HashSet<>();
        for (String id : ids) {
            BrushPreset preset = BrushCatalog.get(id);
            assertEquals(id, preset.id);
            assertEquals(1, preset.version);
            assertTrue(names.add(preset.name));
            assertSame(preset, BrushCatalog.get(id));
        }
        assertEquals(6, new HashSet<>(Arrays.asList(ids)).size());
    }

    @Test public void sixPressureResponsesRemainDistinct() {
        float pressure = .36f;
        Set<Integer> quantized = new HashSet<>();
        for (String id : Arrays.asList(BrushCatalog.FINELINER, BrushCatalog.FOUNTAIN,
                BrushCatalog.BALLPOINT, BrushCatalog.PENCIL_HB,
                BrushCatalog.PENCIL_SOFT, BrushCatalog.MARKER)) {
            quantized.add(Math.round(BrushCatalog.get(id).curve(pressure) * 1000f));
        }
        assertEquals(6, quantized.size());
        assertNotEquals(BrushCatalog.get(BrushCatalog.PENCIL_HB).width(.2f),
                BrushCatalog.get(BrushCatalog.PENCIL_HB).width(.8f), .001f);
    }

    @Test public void unknownPresetFallsBackWithoutMutatingPinnedCatalog() {
        assertEquals(BrushCatalog.FOUNTAIN, BrushCatalog.get("future.v9").id);
        assertTrue(!BrushCatalog.isKnown("future.v9"));
    }
}
