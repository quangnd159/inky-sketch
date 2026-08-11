package dev.inkysketch.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity implements LayerDialogs.Host {
    private static final float[] WIDTHS = {2.5f, 5f, 9f};
    private static final int[] TONES = {0xFF000000, 0xFF444444, 0xFF888888, 0xFFBBBBBB};
    private static final String[] TONE_NAMES = {"Black", "Dark", "Gray", "Light"};

    private final List<Button> brushButtons = new ArrayList<>();
    private final List<Button> widthButtons = new ArrayList<>();
    private final List<Button> toneButtons = new ArrayList<>();
    private FrameLayout root;
    private SurfaceView surface;
    private LinearLayout topBar;
    private LinearLayout toolbar;
    private TextView status;
    private Button eraserButton;
    private Button undoButton;
    private Button redoButton;
    private Button layersButton;
    private ProjectPersistence persistence;
    private EditorController controller;
    private RawInkSession rawInk;
    private LayerDialogs layerDialogs;
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
        layerDialogs = new LayerDialogs(this, this);
        controller.addListener(snapshot -> {
            latest = snapshot;
            rawInk.updateEditorState(snapshot.state);
            updateControls();
        });
        bindSurface();
    }

    private void buildInterface() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        surface = new SurfaceView(this);
        surface.setBackgroundColor(Color.WHITE);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(18), dp(8), dp(18), dp(8));
        topBar.setBackgroundColor(Color.WHITE);
        TextView title = new TextView(this);
        title.setText("Inky Sketch");
        title.setTextColor(Color.BLACK);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        topBar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));
        status = new TextView(this);
        status.setTextColor(Color.BLACK);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        topBar.addView(status, new LinearLayout.LayoutParams(-2, dp(44)));
        root.addView(topBar, new FrameLayout.LayoutParams(-1, dp(60), Gravity.TOP));
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setGravity(Gravity.CENTER);
        toolbar.setPadding(dp(6), dp(5), dp(6), dp(5));
        toolbar.setBackgroundColor(Color.WHITE);
        LinearLayout tools = row();
        brushButtons.add(button(tools, "Pen", () -> ui(EditorCommand.tool(EditorState.Tool.PEN))));
        brushButtons.add(button(tools, "Pencil", () -> ui(EditorCommand.tool(EditorState.Tool.PENCIL))));
        brushButtons.add(button(tools, "Marker", () -> ui(EditorCommand.tool(EditorState.Tool.MARKER))));
        eraserButton = button(tools, "Erase", () -> ui(EditorCommand.tool(EditorState.Tool.ERASER)));
        divider(tools);
        for (int index = 0; index < WIDTHS.length; index++) {
            float width = WIDTHS[index];
            Button button = button(tools, String.valueOf(index + 1), () -> ui(EditorCommand.width(width)));
            button.getLayoutParams().width = dp(46);
            widthButtons.add(button);
        }
        LinearLayout actions = row();
        for (int index = 0; index < TONES.length; index++) {
            int tone = TONES[index];
            toneButtons.add(button(actions, TONE_NAMES[index], () -> ui(EditorCommand.tone(tone))));
        }
        divider(actions);
        undoButton = button(actions, "Undo", () -> ui(EditorCommand.undo()));
        redoButton = button(actions, "Redo", () -> ui(EditorCommand.redo()));
        layersButton = button(actions, "Layers", this::openLayers);
        root.addView(toolbar, new FrameLayout.LayoutParams(-1, dp(110), Gravity.BOTTOM));
        setContentView(root);
    }

    private void bindSurface() {
        surface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                controller.requestRender(RenderRequest.full(RenderRequest.Reason.SURFACE));
                surface.post(() -> {
                    try { rawInk.surfaceCreated(); }
                    catch (RuntimeException error) { status.setText("Raw ink unavailable"); }
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

    private void openLayers() {
        rawInk.openPanel(layerDialogs::showLayers, root::invalidate);
    }

    @Override public EditorSnapshot snapshot() { return controller.snapshot(); }
    @Override public void command(EditorCommand command) { controller.dispatch(command); root.invalidate(); }
    @Override public void panelClosed() {
        rawInk.performUiAction(() -> controller.dispatch(EditorCommand.panel(EditorState.Panel.NONE)), root::invalidate);
    }

    private void updateControls() {
        if (latest == null) return;
        EditorState state = latest.state;
        EditorState.Tool[] tools = {EditorState.Tool.PEN, EditorState.Tool.PENCIL, EditorState.Tool.MARKER};
        for (int i = 0; i < brushButtons.size(); i++) style(brushButtons.get(i), state.activeTool == tools[i]);
        style(eraserButton, state.activeTool == EditorState.Tool.ERASER);
        for (int i = 0; i < widthButtons.size(); i++) style(widthButtons.get(i), state.width == WIDTHS[i]);
        for (int i = 0; i < toneButtons.size(); i++) styleTone(toneButtons.get(i), TONES[i], state.tone == TONES[i]);
        undoButton.setEnabled(state.canUndo);
        redoButton.setEnabled(state.canRedo);
        style(undoButton, false);
        style(redoButton, false);
        style(layersButton, state.panel != EditorState.Panel.NONE);
        InkDocument.Layer layer = latest.document().selectedLayer();
        status.setText(layer.name + (layer.visible ? " · saved locally" : " · hidden"));
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

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        toolbar.addView(row, new LinearLayout.LayoutParams(-1, dp(50)));
        return row;
    }

    private Button button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setOnClickListener(view -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-2, dp(46)));
        return button;
    }

    private void divider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(2), dp(30));
        params.setMargins(dp(6), 0, dp(6), 0);
        parent.addView(divider, params);
    }

    private void style(Button button, boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? Color.BLACK : Color.WHITE);
        background.setStroke(dp(2), Color.BLACK);
        button.setTextColor(selected ? Color.WHITE : Color.BLACK);
        button.setBackground(background);
    }

    private void styleTone(Button button, int tone, boolean selected) {
        style(button, selected);
        button.setText((selected ? "✓ " : "") + TONE_NAMES[indexOfTone(tone)]);
    }

    private int indexOfTone(int tone) {
        for (int i = 0; i < TONES.length; i++) if (TONES[i] == tone) return i;
        return 0;
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

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class AndroidMainThread implements EditorController.MainThread {
        private final Handler handler = new Handler(Looper.getMainLooper());
        @Override public boolean isMainThread() { return Looper.myLooper() == Looper.getMainLooper(); }
        @Override public void post(Runnable runnable) { handler.post(runnable); }
    }
}
