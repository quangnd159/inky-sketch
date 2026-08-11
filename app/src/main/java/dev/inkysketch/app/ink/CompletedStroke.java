package dev.inkysketch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CompletedStroke {
    final InkDocument.Brush brush;
    final String presetId;
    final float width;
    final int tone;
    final List<InkDocument.Point> points;

    CompletedStroke(InkDocument.Brush brush, float width, int tone, List<InkDocument.Point> points) {
        this(brush, BrushCatalog.legacy(brush), width, tone, points);
    }

    CompletedStroke(InkDocument.Brush brush, String presetId, float width, int tone,
            List<InkDocument.Point> points) {
        this.brush = brush;
        this.presetId = BrushCatalog.get(presetId).id;
        this.width = width;
        this.tone = tone;
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
    }
}
