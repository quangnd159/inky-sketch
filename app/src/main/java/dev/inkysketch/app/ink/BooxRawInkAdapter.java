package dev.inkysketch.app;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.view.View;

import com.onyx.android.sdk.api.device.epd.EpdController;
import com.onyx.android.sdk.data.note.TouchPoint;
import com.onyx.android.sdk.pen.RawInputCallback;
import com.onyx.android.sdk.pen.TouchHelper;
import com.onyx.android.sdk.pen.data.TouchPointList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BooxRawInkAdapter implements RawInkAdapter {
    private static final float ERASER_RADIUS_DP = 22f;
    private final SurfaceView surface;
    private final View topBar;
    private final View toolbar;
    private final float density;
    private Listener listener;
    private TouchHelper helper;
    private Config config;

    BooxRawInkAdapter(SurfaceView surface, View topBar, View toolbar, float density) {
        this.surface = surface;
        this.topBar = topBar;
        this.toolbar = toolbar;
        this.density = density;
    }

    @Override public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override public void open() {
        if (helper != null) return;
        try {
            helper = TouchHelper.create(surface, callback);
            Rect limit = new Rect();
            surface.getLocalVisibleRect(limit);
            List<Rect> excluded = new ArrayList<>(2);
            excluded.add(relativeRect(topBar));
            excluded.add(relativeRect(toolbar));
            helper.setLimitRect(limit, excluded).openRawDrawing();
            helper.setRawDrawingRenderEnabled(true);
            if (config != null) configure(config);
        } catch (Throwable error) {
            helper = null;
            throw new IllegalStateException("Unable to open BOOX raw ink", error);
        }
    }

    @Override public void configure(Config config) {
        this.config = config;
        if (helper == null) return;
        float factor = config.tool == EditorState.Tool.PENCIL ? 0.8f
                : config.tool == EditorState.Tool.MARKER ? 1.8f : 1f;
        helper.setStrokeWidth(config.width * density * factor)
                .setStrokeColor(config.tone)
                .setStrokeStyle("pencil".equals(config.presetId)
                        ? TouchHelper.STROKE_STYLE_PENCIL : TouchHelper.STROKE_STYLE_FOUNTAIN);
        helper.setBrushRawDrawingEnabled(config.tool != EditorState.Tool.ERASER);
        helper.setEraserRawDrawingEnabled(config.tool == EditorState.Tool.ERASER, 0);
    }

    @Override public void enableDrawing() {
        if (helper != null) helper.setRawDrawingEnabled(true);
    }

    @Override public void disableDrawing() {
        if (helper != null) helper.setRawDrawingEnabled(false);
    }

    @Override public void requestPartialRefresh(RenderRequest.DirtyRect dirtyRect) {
        // Full committed rendering remains the correctness path; vendor refreshes stay isolated here.
    }

    @Override public void close() {
        if (helper == null) return;
        try {
            helper.closeRawDrawing();
        } finally {
            helper = null;
        }
    }

    private Rect relativeRect(View child) {
        int[] parentLocation = new int[2];
        int[] childLocation = new int[2];
        surface.getLocationOnScreen(parentLocation);
        child.getLocationOnScreen(childLocation);
        Rect rect = new Rect();
        child.getLocalVisibleRect(rect);
        rect.offset(childLocation[0] - parentLocation[0], childLocation[1] - parentLocation[1]);
        return rect;
    }

    private List<InkDocument.Point> normalize(List<TouchPoint> rawPoints) {
        if (rawPoints == null || rawPoints.isEmpty()) return Collections.emptyList();
        int width = Math.max(1, surface.getWidth());
        int height = Math.max(1, surface.getHeight());
        float maxPressure = EpdController.getMaxTouchPressure();
        if (maxPressure <= 0f) maxPressure = 1f;
        long now = SystemClock.uptimeMillis();
        List<InkDocument.Point> normalized = new ArrayList<>(rawPoints.size());
        for (TouchPoint point : rawPoints) {
            normalized.add(new InkDocument.Point(point.getX() / width, point.getY() / height,
                    point.getPressure() / maxPressure, now));
        }
        return Collections.unmodifiableList(normalized);
    }

    private InkDocument.Brush brush() {
        if (config != null && config.tool == EditorState.Tool.PENCIL) return InkDocument.Brush.PENCIL;
        if (config != null && config.tool == EditorState.Tool.MARKER) return InkDocument.Brush.MARKER;
        return InkDocument.Brush.PEN;
    }

    private final RawInputCallback callback = new RawInputCallback() {
        @Override public void onBeginRawDrawing(boolean stylus, TouchPoint point) {}
        @Override public void onEndRawDrawing(boolean stylus, TouchPoint point) {}

        @Override public void onRawDrawingTouchPointMoveReceived(TouchPoint point) {
            // Allocation-free hot path: Onyx owns the live preview.
        }

        @Override public void onRawDrawingTouchPointListReceived(TouchPointList points) {
            if (listener == null || config == null) return;
            List<InkDocument.Point> normalized = normalize(points.getPoints());
            if (!normalized.isEmpty()) listener.onCompletedStroke(
                    new CompletedStroke(brush(), config.width, config.tone, normalized));
        }

        @Override public void onBeginRawErasing(boolean stylus, TouchPoint point) {}
        @Override public void onEndRawErasing(boolean stylus, TouchPoint point) {}

        @Override public void onRawErasingTouchPointMoveReceived(TouchPoint point) {
            // Allocation-free hot path: Onyx owns the live eraser preview.
        }

        @Override public void onRawErasingTouchPointListReceived(TouchPointList points) {
            if (listener == null) return;
            List<InkDocument.Point> normalized = normalize(points.getPoints());
            if (!normalized.isEmpty()) listener.onCompletedErase(new CompletedErase(normalized,
                    ERASER_RADIUS_DP * density, Math.max(1, surface.getWidth()),
                    Math.max(1, surface.getHeight())));
        }
    };
}
