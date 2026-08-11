package dev.inkysketch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CompletedErase {
    final List<InkDocument.Point> points;
    final float radiusPixels;
    final int viewportWidth;
    final int viewportHeight;

    CompletedErase(List<InkDocument.Point> points, float radiusPixels, int viewportWidth, int viewportHeight) {
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
        this.radiusPixels = radiusPixels;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }
}
