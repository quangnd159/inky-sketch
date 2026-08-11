package dev.inkysketch.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** Committed stroke renderer driven solely by the stored preset definition. */
final class BrushRenderer {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 1; i < stroke.points.size(); i++) {
            InkDocument.Point point = stroke.points.get(i);
            float pressure = (previous.pressure + point.pressure) / 2f;
            paint.setStrokeWidth(density * stroke.width * preset.width(pressure));
            paint.setColor(tinted(stroke.color, preset.value(pressure)));
            canvas.drawLine(previous.x * canvas.getWidth(), previous.y * canvas.getHeight(),
                    point.x * canvas.getWidth(), point.y * canvas.getHeight(), paint);
            previous = point;
        }
    }

    private static int tinted(int color, float value) {
        return Color.rgb(Math.round(255 - (255 - Color.red(color)) * value),
                Math.round(255 - (255 - Color.green(color)) * value),
                Math.round(255 - (255 - Color.blue(color)) * value));
    }
}
