package dev.inkysketch.app;

final class RenderRequest {
    enum Reason { SURFACE, STROKE, ERASE, LAYER, UNDO, REDO }

    static final class DirtyRect {
        final float left;
        final float top;
        final float right;
        final float bottom;

        DirtyRect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    final Reason reason;
    final DirtyRect dirtyRect;

    private RenderRequest(Reason reason, DirtyRect dirtyRect) {
        this.reason = reason;
        this.dirtyRect = dirtyRect;
    }

    static RenderRequest full(Reason reason) { return new RenderRequest(reason, null); }
}
