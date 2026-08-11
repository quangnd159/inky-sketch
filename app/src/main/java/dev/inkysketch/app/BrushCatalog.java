package dev.inkysketch.app;

import android.graphics.Paint;

final class BrushCatalog {
    static final String FINELINER = "fineliner.v1";
    static final String FOUNTAIN = "fountain.v1";
    static final String BALLPOINT = "ballpoint.v1";
    static final String PENCIL_HB = "pencil-hb.v1";
    static final String PENCIL_SOFT = "pencil-soft.v1";
    static final String MARKER = "marker.v1";

    private BrushCatalog() {}

    static BrushPreset get(String id) {
        if (FINELINER.equals(id)) return new BrushPreset(FINELINER, 1, .55f, .9f, .92f, 1f, 1f,
                Paint.Cap.ROUND, Paint.Join.ROUND, 2);
        if (BALLPOINT.equals(id)) return new BrushPreset(BALLPOINT, 1, .65f, 1.15f, .7f, .98f, .8f,
                Paint.Cap.ROUND, Paint.Join.ROUND, 3);
        if (PENCIL_HB.equals(id)) return new BrushPreset(PENCIL_HB, 1, .55f, 1.25f, .35f, .82f, .75f,
                Paint.Cap.ROUND, Paint.Join.ROUND, 3);
        if (PENCIL_SOFT.equals(id)) return new BrushPreset(PENCIL_SOFT, 1, .85f, 1.65f, .2f, .72f, .65f,
                Paint.Cap.ROUND, Paint.Join.ROUND, 4);
        if (MARKER.equals(id)) return new BrushPreset(MARKER, 1, 1.65f, 2.1f, .58f, .88f, 1f,
                Paint.Cap.SQUARE, Paint.Join.ROUND, 5);
        return new BrushPreset(FOUNTAIN, 1, .5f, 1.3f, .82f, 1f, .9f,
                Paint.Cap.ROUND, Paint.Join.ROUND, 3);
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
