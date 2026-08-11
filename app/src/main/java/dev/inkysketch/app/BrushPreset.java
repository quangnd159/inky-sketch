package dev.inkysketch.app;

import android.graphics.Paint;

/** Immutable, versioned definition shared by committed and BOOX raw ink. */
final class BrushPreset {
    enum Curve { CONSTANT, LINEAR, SOFT, FIRM, S_CURVE, MARKER }

    final String id;
    final String name;
    final int version;
    final float minWidth;
    final float maxWidth;
    final float minValue;
    final float maxValue;
    final float smoothing;
    final Paint.Cap cap;
    final Paint.Join join;
    final int previewWidth;
    final Curve curve;

    BrushPreset(String id, String name, int version, float minWidth, float maxWidth, float minValue,
            float maxValue, float smoothing, Paint.Cap cap, Paint.Join join, int previewWidth,
            Curve curve) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.minWidth = minWidth;
        this.maxWidth = maxWidth;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.smoothing = smoothing;
        this.cap = cap;
        this.join = join;
        this.previewWidth = previewWidth;
        this.curve = curve;
    }

    float width(float pressure) { return minWidth + (maxWidth - minWidth) * curve(pressure); }
    float value(float pressure) { return minValue + (maxValue - minValue) * curve(pressure); }

    float curve(float pressure) {
        float normalized = Math.max(0f, Math.min(1f, pressure <= 0f ? .5f : pressure));
        switch (curve) {
            case CONSTANT: return .5f;
            case SOFT: return (float) Math.sqrt(normalized);
            case FIRM: return normalized * normalized;
            case S_CURVE: return normalized * normalized * (3f - 2f * normalized);
            case MARKER: return .75f + .25f * normalized;
            default: return smoothing == 1f ? normalized
                    : (float) Math.pow(normalized, smoothing);
        }
    }
}
