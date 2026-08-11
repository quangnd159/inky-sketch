package dev.inkysketch.app;

import android.graphics.Canvas;
import android.graphics.Color;
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
        private final BrushRenderer brushes = new BrushRenderer();
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
            brushes.draw(canvas, density, stroke);
        }

        @Override public void end() {
            if (canvas != null) surface.getHolder().unlockCanvasAndPost(canvas);
            canvas = null;
        }

    }
}
