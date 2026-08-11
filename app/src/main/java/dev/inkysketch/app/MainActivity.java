package dev.inkysketch.app;

import android.app.Activity;
import android.graphics.Color;
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

public final class MainActivity extends Activity {
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

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        enterImmersiveMode();
        buildInterface();
        float density = getResources().getDisplayMetrics().density;
        persistence = new ProjectPersistence(this);
        DocumentRenderer renderer = DocumentRenderer.forSurface(surface, density);
        AndroidMainThread mainThread = new AndroidMainThread();
        controller = new EditorController(mainThread, persistence.load(), persistence, renderer);
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
    @Override protected void onDestroy() { rawInk.close(); controller.close(); super.onDestroy(); }

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
