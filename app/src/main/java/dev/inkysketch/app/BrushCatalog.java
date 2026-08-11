package dev.inkysketch.app;

import android.graphics.Paint;

final class BrushCatalog {
    static final String FINELINER = "fineliner.v1";
    static final String FOUNTAIN = "fountain.v1";
    static final String BALLPOINT = "ballpoint.v1";
    static final String PENCIL_HB = "pencil-hb.v1";
    static final String PENCIL_SOFT = "pencil-soft.v1";
    static final String MARKER = "marker.v1";

    private static final BrushPreset FINELINER_PRESET = preset(FINELINER, "Fineliner",
            .72f, .78f, .96f, 1f, 2, BrushPreset.Curve.CONSTANT);
    private static final BrushPreset FOUNTAIN_PRESET = preset(FOUNTAIN, "Fountain pen",
            .42f, 1.45f, .86f, 1f, 3, BrushPreset.Curve.S_CURVE);
    private static final BrushPreset BALLPOINT_PRESET = preset(BALLPOINT, "Ballpoint",
            .62f, 1.08f, .76f, .98f, 3, BrushPreset.Curve.LINEAR);
    private static final BrushPreset PENCIL_HB_PRESET = preset(PENCIL_HB, "HB pencil",
            .48f, 1.22f, .38f, .84f, 3, BrushPreset.Curve.FIRM);
    private static final BrushPreset PENCIL_SOFT_PRESET = preset(PENCIL_SOFT, "Soft pencil",
            .78f, 1.72f, .18f, .74f, 4, BrushPreset.Curve.SOFT);
    private static final BrushPreset MARKER_PRESET = preset(MARKER, "Marker",
            1.7f, 2.15f, .62f, .88f, 5, BrushPreset.Curve.MARKER);

    private BrushCatalog() {}

    static BrushPreset get(String id) {
        if (FINELINER.equals(id)) return FINELINER_PRESET;
        if (BALLPOINT.equals(id)) return BALLPOINT_PRESET;
        if (PENCIL_HB.equals(id)) return PENCIL_HB_PRESET;
        if (PENCIL_SOFT.equals(id)) return PENCIL_SOFT_PRESET;
        if (MARKER.equals(id)) return MARKER_PRESET;
        return FOUNTAIN_PRESET;
    }

    static boolean isKnown(String id) {
        return FINELINER.equals(id) || FOUNTAIN.equals(id) || BALLPOINT.equals(id)
                || PENCIL_HB.equals(id) || PENCIL_SOFT.equals(id) || MARKER.equals(id);
    }

    private static BrushPreset preset(String id, String name, float minWidth, float maxWidth,
            float minValue, float maxValue, int previewWidth, BrushPreset.Curve curve) {
        return new BrushPreset(id, name, 1, minWidth, maxWidth, minValue, maxValue, 1f,
                MARKER.equals(id) ? Paint.Cap.SQUARE : Paint.Cap.ROUND,
                Paint.Join.ROUND, previewWidth, curve);
    }

    static String legacy(InkDocument.Brush brush) {
        return brush == InkDocument.Brush.PENCIL ? PENCIL_HB
                : brush == InkDocument.Brush.MARKER ? MARKER : FOUNTAIN;
    }

    static InkDocument.Brush brush(String id) {
        return id != null && id.startsWith("pencil-") ? InkDocument.Brush.PENCIL
                : MARKER.equals(id) ? InkDocument.Brush.MARKER : InkDocument.Brush.PEN;
    }

    static boolean rawPencil(String id) { return id != null && id.startsWith("pencil-"); }
}
