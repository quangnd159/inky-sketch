package dev.inkysketch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CompletedStroke {
    final InkDocument.Brush brush;
    final float width;
    final int tone;
    final List<InkDocument.Point> points;

    CompletedStroke(InkDocument.Brush brush, float width, int tone, List<InkDocument.Point> points) {
        this.brush = brush;
        this.width = width;
        this.tone = tone;
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
    }
}
