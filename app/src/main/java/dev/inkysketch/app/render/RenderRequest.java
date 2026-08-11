package dev.inkysketch.app;

final class RenderRequest {
    enum Reason { SURFACE, STROKE, ERASE, LAYER, UNDO, REDO }

    static final class DirtyRect {
        final float left;
        final float top;
        final float right;
        final float bottom;

        DirtyRect(float left, float top, float right, float bottom) {
            this.left = clamp(Math.min(left, right));
            this.top = clamp(Math.min(top, bottom));
            this.right = clamp(Math.max(left, right));
            this.bottom = clamp(Math.max(top, bottom));
        }

        boolean intersects(InkDocument.Stroke stroke) {
            if (stroke.points.isEmpty()) return false;
            float minX = 1f, minY = 1f, maxX = 0f, maxY = 0f;
            for (InkDocument.Point point : stroke.points) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }
            // Normalized conservative pad covers the widest supported brush on compact screens.
            float pad = Math.min(.1f, .01f + stroke.width * .005f);
            return maxX + pad >= left && minX - pad <= right
                    && maxY + pad >= top && minY - pad <= bottom;
        }

        private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    }

    final Reason reason;
    final DirtyRect dirtyRect;

    private RenderRequest(Reason reason, DirtyRect dirtyRect) {
        this.reason = reason;
        this.dirtyRect = dirtyRect;
    }

    static RenderRequest full(Reason reason) { return new RenderRequest(reason, null); }
    static RenderRequest dirty(Reason reason, DirtyRect rect) { return new RenderRequest(reason, rect); }

    static RenderRequest stroke(InkDocument.Stroke stroke) {
        float left = 1f, top = 1f, right = 0f, bottom = 0f;
        for (InkDocument.Point point : stroke.points) {
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        float margin = Math.min(.05f, .006f + stroke.width * .002f);
        return dirty(Reason.STROKE,
                new DirtyRect(left - margin, top - margin, right + margin, bottom + margin));
    }
}
