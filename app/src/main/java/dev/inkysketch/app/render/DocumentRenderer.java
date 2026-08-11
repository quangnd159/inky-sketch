package dev.inkysketch.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

final class DocumentRenderer {
    interface Target {
        boolean begin(RenderRequest request);
        void draw(String layerId, InkDocument.Stroke stroke);
        void end();
    }

    private final Target target;

    DocumentRenderer(Target target) {
        this.target = target;
    }

    static DocumentRenderer forSurface(SurfaceView surface, float density) {
        return new DocumentRenderer(new SurfaceTarget(surface, density));
    }

    void render(EditorSnapshot snapshot, RenderRequest request) {
        if (!target.begin(request)) return;
        try {
            for (InkDocument.Layer layer : snapshot.document().layers()) {
                if (!layer.visible) continue;
                for (InkDocument.Stroke stroke : layer.strokes) target.draw(layer.id, stroke);
            }
        } finally {
            target.end();
        }
    }

    private static final class SurfaceTarget implements Target {
        private final SurfaceView surface;
        private final float density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Canvas canvas;

        SurfaceTarget(SurfaceView surface, float density) {
            this.surface = surface;
            this.density = density;
        }

        @Override public boolean begin(RenderRequest request) {
            SurfaceHolder holder = surface.getHolder();
            if (holder == null) return false;
            canvas = holder.lockCanvas();
            if (canvas == null) return false;
            canvas.drawColor(Color.WHITE);
            return true;
        }

        @Override public void draw(String layerId, InkDocument.Stroke stroke) {
            if (stroke.points.isEmpty()) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(stroke.brush == InkDocument.Brush.MARKER ? Paint.Cap.SQUARE : Paint.Cap.ROUND);
            InkDocument.Point first = stroke.points.get(0);
            if (stroke.points.size() == 1) {
                float pressure = pressure(first.pressure, stroke.brush);
                paint.setColor(color(stroke.color, stroke.brush, pressure));
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(first.x * canvas.getWidth(), first.y * canvas.getHeight(),
                        density * stroke.width * width(stroke.brush, pressure) / 2f, paint);
                return;
            }
            InkDocument.Point previous = first;
            for (int index = 1; index < stroke.points.size(); index++) {
                InkDocument.Point point = stroke.points.get(index);
                float pressure = pressure((previous.pressure + point.pressure) / 2f, stroke.brush);
                paint.setColor(color(stroke.color, stroke.brush, pressure));
                paint.setStrokeWidth(density * stroke.width * width(stroke.brush, pressure));
                canvas.drawLine(previous.x * canvas.getWidth(), previous.y * canvas.getHeight(),
                        point.x * canvas.getWidth(), point.y * canvas.getHeight(), paint);
                previous = point;
            }
        }

        @Override public void end() {
            if (canvas != null) surface.getHolder().unlockCanvasAndPost(canvas);
            canvas = null;
        }

        private static float pressure(float pressure, InkDocument.Brush brush) {
            float normalized = pressure <= 0f ? 0.5f : pressure;
            if (brush == InkDocument.Brush.PENCIL) return 0.2f + normalized * 0.8f;
            if (brush == InkDocument.Brush.MARKER) return 0.55f + normalized * 0.45f;
            return normalized;
        }

        private static float width(InkDocument.Brush brush, float pressure) {
            if (brush == InkDocument.Brush.PENCIL) return 0.45f + pressure * 0.65f;
            if (brush == InkDocument.Brush.MARKER) return 1.6f + pressure * 0.45f;
            return 0.5f + pressure * 0.8f;
        }

        private static int color(int color, InkDocument.Brush brush, float pressure) {
            float strength = brush == InkDocument.Brush.PENCIL ? 0.38f + pressure * 0.48f
                    : brush == InkDocument.Brush.MARKER ? 0.58f + pressure * 0.3f
                    : 0.82f + pressure * 0.18f;
            return Color.rgb(
                    Math.round(255 - (255 - Color.red(color)) * strength),
                    Math.round(255 - (255 - Color.green(color)) * strength),
                    Math.round(255 - (255 - Color.blue(color)) * strength));
        }
    }
}
