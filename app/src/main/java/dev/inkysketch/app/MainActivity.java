package dev.inkysketch.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int EXPORT_PNG = 4101;
    private static final int EXPORT_NATIVE = 4102;
    private FrameLayout root;
    private SurfaceView surface;
    private LinearLayout topBar;
    private LinearLayout toolbar;
    private ProjectPersistence persistence;
    private EditorController controller;
    private RawInkSession rawInk;
    private EditorChromeView chrome;
    private LayerPanelView layerPanel;
    private EditorSnapshot latest;
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();
    private EditorSnapshot pendingExport;
    private int pendingWidth;
    private int pendingHeight;
    private float exportDensity;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        enterImmersiveMode();
        buildInterface();
        float density = getResources().getDisplayMetrics().density;
        persistence = new ProjectPersistence(this);
        DocumentRenderer renderer = DocumentRenderer.forSurface(surface, density);
        AndroidMainThread mainThread = new AndroidMainThread();
        InkDocument document = persistence.load();
        controller = new EditorController(mainThread, document, persistence, renderer,
                persistence.isWritable());
        BooxRawInkAdapter adapter = new BooxRawInkAdapter(surface, topBar, toolbar, density);
        rawInk = new RawInkSession(adapter, mainThread, this::afterPresentation,
                new RawInkSession.GestureSink() {
                    @Override public void onStroke(CompletedStroke stroke) {
                        controller.dispatch(EditorCommand.stroke(stroke));
                    }
                    @Override public void onErase(CompletedErase erase) {
                        controller.dispatch(EditorCommand.erase(erase));
                    }
                });
        controller.addListener(snapshot -> {
            latest = snapshot;
            rawInk.updateEditorState(snapshot.state);
            chrome.render(snapshot);
            layerPanel.render(snapshot);
        });
        bindSurface();
    }

    private void buildInterface() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        surface = new SurfaceView(this);
        surface.setBackgroundColor(Color.WHITE);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        chrome = new EditorChromeView(this, root, new EditorChromeView.Host() {
            @Override public void command(EditorCommand command) { ui(command); }
            @Override public void openPanel(EditorState.Panel panel) { openPanel(panel); }
            @Override public void closePanel() { closePanel(); }
            @Override public void retrySave() { ui(EditorCommand.retrySave()); }
            @Override public void fullRefresh() { rawInk.performUiAction(
                    () -> controller.requestRender(RenderRequest.full(RenderRequest.Reason.SURFACE)),
                    root::invalidate); }
            @Override public void export(ExportFormat format) { beginExport(format); }
        });
        topBar = (LinearLayout) chrome.topBar();
        toolbar = (LinearLayout) chrome.dock();
        layerPanel = new LayerPanelView(this, root, new LayerPanelView.Host() {
            @Override public void command(EditorCommand command) { ui(command); }
            @Override public void closePanel() { closePanel(); }
        });
        setContentView(root);
    }

    private void bindSurface() {
        surface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                controller.requestRender(RenderRequest.full(RenderRequest.Reason.SURFACE));
                surface.post(() -> {
                    try { rawInk.surfaceCreated(); }
                    catch (RuntimeException error) { root.invalidate(); }
                });
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                controller.requestRender(RenderRequest.full(RenderRequest.Reason.SURFACE));
            }
            @Override public void surfaceDestroyed(SurfaceHolder holder) { rawInk.surfaceDestroyed(); }
        });
    }

    private void ui(EditorCommand command) {
        rawInk.performUiAction(() -> controller.dispatch(command), root::invalidate);
    }

    private void openPanel(EditorState.Panel panel) {
        rawInk.openPanel(() -> controller.dispatch(EditorCommand.panel(panel)), root::invalidate);
    }

    private void closePanel() {
        rawInk.performUiAction(() -> controller.dispatch(EditorCommand.panel(EditorState.Panel.NONE)), root::invalidate);
    }

    private void beginExport(ExportFormat format) {
        pendingExport = latest;
        pendingWidth = Math.max(1, surface.getWidth());
        pendingHeight = Math.max(1, surface.getHeight());
        exportDensity = getResources().getDisplayMetrics().density;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(format.mimeType)
                .putExtra(Intent.EXTRA_TITLE, format.suggestedName);
        startActivityForResult(intent, format == ExportFormat.PNG ? EXPORT_PNG : EXPORT_NATIVE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_PNG && requestCode != EXPORT_NATIVE) return;
        Uri destination = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (destination == null || pendingExport == null) {
            finishExport(false, false);
            return;
        }
        EditorSnapshot snapshot = pendingExport;
        ExportFormat format = requestCode == EXPORT_PNG ? ExportFormat.PNG : ExportFormat.NATIVE;
        exportExecutor.execute(() -> writeExport(destination, format, snapshot));
    }

    private void writeExport(Uri destination, ExportFormat format, EditorSnapshot snapshot) {
        boolean success = false;
        try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) throw new IOException("Document provider returned no stream");
            if (format == ExportFormat.PNG) {
                DocumentExport.writePng(snapshot.document(), pendingWidth, pendingHeight,
                        exportDensity, output);
            } else {
                DocumentExport.writeNative(snapshot.document(), output);
            }
            success = true;
        } catch (IOException | RuntimeException ignored) {
            // The canvas remains untouched; report the provider failure in the e-ink UI.
        }
        boolean completed = success;
        root.post(() -> finishExport(completed, true));
    }

    private void finishExport(boolean success, boolean attempted) {
        pendingExport = null;
        closePanel();
        if (attempted) Toast.makeText(this, success ? "Exported" : "Export failed",
                Toast.LENGTH_SHORT).show();
    }

    private void afterPresentation(Runnable continuation) {
        root.post(() -> {
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (!observer.isAlive()) { root.post(continuation); return; }
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                    root.post(continuation);
                    return true;
                }
            });
            root.invalidate();
        });
    }

    @Override protected void onResume() { super.onResume(); enterImmersiveMode(); rawInk.resume(); }
    @Override protected void onPause() { rawInk.pause(); controller.onPause(); super.onPause(); }
    @Override protected void onDestroy() {
        rawInk.close();
        controller.close();
        exportExecutor.shutdown();
        super.onDestroy();
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private static final class AndroidMainThread implements EditorController.MainThread {
        private final Handler handler = new Handler(Looper.getMainLooper());
        @Override public boolean isMainThread() { return Looper.myLooper() == Looper.getMainLooper(); }
        @Override public void post(Runnable runnable) { handler.post(runnable); }
    }
}
