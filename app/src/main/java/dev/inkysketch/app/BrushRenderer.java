package dev.inkysketch.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/** Committed stroke renderer driven solely by the stored preset definition. */
final class BrushRenderer {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path segment = new Path();

    void draw(Canvas canvas, float density, InkDocument.Stroke stroke) {
        if (stroke.points.isEmpty()) return;
        BrushPreset preset = BrushCatalog.get(stroke.presetId);
        paint.setStrokeCap(preset.cap);
        paint.setStrokeJoin(preset.join);
        InkDocument.Point previous = stroke.points.get(0);
        if (stroke.points.size() == 1) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(tinted(stroke.color, preset.value(previous.pressure)));
            canvas.drawCircle(previous.x * canvas.getWidth(), previous.y * canvas.getHeight(),
                    density * stroke.width * preset.width(previous.pressure) / 2f, paint);
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        drawJoin(canvas, density, stroke, preset, previous);
        for (int i = 1; i < stroke.points.size(); i++) {
            InkDocument.Point point = stroke.points.get(i);
            float pressure = (previous.pressure + point.pressure) / 2f;
            paint.setColor(tinted(stroke.color, preset.value(pressure)));
            drawTaperedSegment(canvas, density, stroke, preset, previous, point);
            drawJoin(canvas, density, stroke, preset, point);
            previous = point;
        }
    }

    private void drawTaperedSegment(Canvas canvas, float density, InkDocument.Stroke stroke,
            BrushPreset preset, InkDocument.Point from, InkDocument.Point to) {
        float x1 = from.x * canvas.getWidth();
        float y1 = from.y * canvas.getHeight();
        float x2 = to.x * canvas.getWidth();
        float y2 = to.y * canvas.getHeight();
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < .01f) return;
        float normalX = -dy / length;
        float normalY = dx / length;
        float r1 = density * stroke.width * preset.width(from.pressure) / 2f;
        float r2 = density * stroke.width * preset.width(to.pressure) / 2f;
        segment.reset();
        segment.moveTo(x1 + normalX * r1, y1 + normalY * r1);
        segment.lineTo(x2 + normalX * r2, y2 + normalY * r2);
        segment.lineTo(x2 - normalX * r2, y2 - normalY * r2);
        segment.lineTo(x1 - normalX * r1, y1 - normalY * r1);
        segment.close();
        canvas.drawPath(segment, paint);
    }

    private void drawJoin(Canvas canvas, float density, InkDocument.Stroke stroke,
            BrushPreset preset, InkDocument.Point point) {
        paint.setColor(tinted(stroke.color, preset.value(point.pressure)));
        float radius = density * stroke.width * preset.width(point.pressure) / 2f;
        canvas.drawCircle(point.x * canvas.getWidth(), point.y * canvas.getHeight(), radius, paint);
    }

    private static int tinted(int color, float value) {
        return Color.rgb(Math.round(255 - (255 - Color.red(color)) * value),
                Math.round(255 - (255 - Color.green(color)) * value),
                Math.round(255 - (255 - Color.blue(color)) * value));
    }
}
