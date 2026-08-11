package dev.inkysketch.app;

import android.graphics.Paint;

/** Immutable, versioned definition shared by committed and BOOX raw ink. */
final class BrushPreset {
    final String id;
    final int version;
    final float minWidth;
    final float maxWidth;
    final float minValue;
    final float maxValue;
    final float smoothing;
    final Paint.Cap cap;
    final Paint.Join join;
    final int previewWidth;

    BrushPreset(String id, int version, float minWidth, float maxWidth, float minValue,
            float maxValue, float smoothing, Paint.Cap cap, Paint.Join join, int previewWidth) {
        this.id = id;
        this.version = version;
        this.minWidth = minWidth;
        this.maxWidth = maxWidth;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.smoothing = smoothing;
        this.cap = cap;
        this.join = join;
        this.previewWidth = previewWidth;
    }

    float width(float pressure) { return minWidth + (maxWidth - minWidth) * curve(pressure); }
    float value(float pressure) { return minValue + (maxValue - minValue) * curve(pressure); }

    private float curve(float pressure) {
        float normalized = Math.max(0f, Math.min(1f, pressure <= 0f ? .5f : pressure));
        return smoothing == 1f ? normalized : (float) Math.pow(normalized, smoothing);
    }
}
