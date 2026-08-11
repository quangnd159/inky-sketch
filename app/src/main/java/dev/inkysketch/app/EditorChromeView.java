package dev.inkysketch.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class EditorChromeView {
    interface Host {
        void command(EditorCommand command);
        void openPanel(EditorState.Panel panel);
        void closePanel();
        void retrySave();
        void fullRefresh();
        void export(ExportFormat format);
    }

    private final Context context;
    private final Host host;
    private final FrameLayout root;
    private final LinearLayout topBar;
    private final LinearLayout dock;
    private final LinearLayout rack;
    private final TextView status;
    private final BinaryButton refresh;
    private final List<BinaryButton> toolButtons = new ArrayList<>();
    private final List<BinaryButton> presetButtons = new ArrayList<>();
    private final List<BinaryButton> widthButtons = new ArrayList<>();
    private final List<BinaryButton> toneButtons = new ArrayList<>();
    private BinaryButton eraser;
    private BinaryButton undo;
    private BinaryButton redo;
    private BinaryButton tone;
    private BinaryButton layers;
    private BinaryButton export;

    EditorChromeView(Context context, FrameLayout root, Host host) {
        this.context = context;
        this.root = root;
        this.host = host;
        topBar = buildTopBar();
        refresh = (BinaryButton) topBar.getChildAt(2);
        status = (TextView) topBar.getChildAt(1);
        dock = buildDock();
        rack = new LinearLayout(context);
        rack.setOrientation(LinearLayout.VERTICAL);
        rack.setPadding(dp(8), dp(8), dp(8), dp(8));
        rack.setBackgroundColor(Color.WHITE);
        rack.setVisibility(View.GONE);
        FrameLayout.LayoutParams rackParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        rackParams.bottomMargin = dp(EditorChromeSpec.DOCK_HEIGHT_DP);
        root.addView(rack, rackParams);
    }

    View topBar() { return topBar; }
    View dock() { return dock; }

    void render(EditorSnapshot snapshot) {
        EditorState state = snapshot.state;
        toolButtons.get(0).setSelectedState(state.activeTool == EditorState.Tool.PEN);
        toolButtons.get(1).setSelectedState(state.activeTool == EditorState.Tool.PENCIL);
        toolButtons.get(2).setSelectedState(state.activeTool == EditorState.Tool.MARKER);
        eraser.setSelectedState(state.activeTool == EditorState.Tool.ERASER);
        undo.setEnabled(state.canUndo);
        redo.setEnabled(state.canRedo);
        tone.setSelectedState(state.panel == EditorState.Panel.TONE_RACK);
        layers.setSelectedState(state.panel == EditorState.Panel.LAYERS
                || state.panel == EditorState.Panel.LAYER_RENAME
                || state.panel == EditorState.Panel.LAYER_CLEAR_CONFIRM
                || state.panel == EditorState.Panel.LAYER_DELETE_CONFIRM);
        export.setSelectedState(state.panel == EditorState.Panel.EXPORT);

        InkDocument.Layer selected = snapshot.document().selectedLayer();
        String save = state.saveState == EditorState.SaveState.SAVING ? "Saving…"
                : state.saveState == EditorState.SaveState.FAILED ? "Save failed · Retry"
                : "Saved";
        status.setText(selected.name + (selected.visible ? " · " : " · Hidden · ") + save);
        status.setOnClickListener(state.saveState == EditorState.SaveState.FAILED
                ? view -> host.retrySave() : null);

        if (state.panel == EditorState.Panel.BRUSH_RACK) showBrushRack(state);
        else if (state.panel == EditorState.Panel.TONE_RACK) showToneRack(state);
        else if (state.panel == EditorState.Panel.EXPORT) showExportRack();
        else rack.setVisibility(View.GONE);
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(2), dp(8), dp(2));
        bar.setBackgroundColor(Color.WHITE);
        TextView title = new TextView(context);
        title.setText("Inky Sketch");
        title.setTextColor(Color.BLACK);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1f));
        TextView saveStatus = new TextView(context);
        saveStatus.setTextColor(Color.BLACK);
        saveStatus.setTextSize(12);
        saveStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        saveStatus.setMinHeight(dp(48));
        saveStatus.setPadding(dp(8), 0, dp(8), 0);
        bar.addView(saveStatus, new LinearLayout.LayoutParams(-2, dp(52)));
        BinaryButton refreshButton = BinaryButton.create(context, "Refresh", view -> host.fullRefresh());
        bar.addView(refreshButton, new LinearLayout.LayoutParams(dp(72), dp(48)));
        export = BinaryButton.create(context, "Export",
                view -> host.openPanel(EditorState.Panel.EXPORT));
        bar.addView(export, new LinearLayout.LayoutParams(dp(72), dp(48)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP);
        root.addView(bar, params);
        return bar;
    }

    private LinearLayout buildDock() {
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        bar.setBackgroundColor(Color.WHITE);
        toolButtons.add(dockButton(bar, "Inker", view -> selectOrRack(EditorState.Tool.PEN)));
        toolButtons.add(dockButton(bar, "Pencil", view -> selectOrRack(EditorState.Tool.PENCIL)));
        toolButtons.add(dockButton(bar, "Marker", view -> selectOrRack(EditorState.Tool.MARKER)));
        eraser = dockButton(bar, "Eraser", view -> host.command(EditorCommand.tool(EditorState.Tool.ERASER)));
        undo = dockButton(bar, "Undo", view -> host.command(EditorCommand.undo()));
        redo = dockButton(bar, "Redo", view -> host.command(EditorCommand.redo()));
        tone = dockButton(bar, "Tone", view -> host.openPanel(EditorState.Panel.TONE_RACK));
        layers = dockButton(bar, "Layers", view -> host.openPanel(EditorState.Panel.LAYERS));
        root.addView(bar, new FrameLayout.LayoutParams(-1, dp(EditorChromeSpec.DOCK_HEIGHT_DP), Gravity.BOTTOM));
        return bar;
    }

    private BinaryButton dockButton(LinearLayout parent, String label, View.OnClickListener listener) {
        BinaryButton button = BinaryButton.create(context, label, listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
        params.setMargins(dp(1), 0, dp(1), 0);
        parent.addView(button, params);
        return button;
    }

    private void selectOrRack(EditorState.Tool tool) {
        EditorState.Tool current = tool == EditorState.Tool.PEN ? EditorState.Tool.PEN
                : tool == EditorState.Tool.PENCIL ? EditorState.Tool.PENCIL : EditorState.Tool.MARKER;
        // A selected family opens its rack; switching family remains a single-tap action.
        BinaryButton button = toolButtons.get(current == EditorState.Tool.PEN ? 0
                : current == EditorState.Tool.PENCIL ? 1 : 2);
        if (button.selectedState()) host.openPanel(EditorState.Panel.BRUSH_RACK);
        else host.command(EditorCommand.tool(tool));
    }

    private void showBrushRack(EditorState state) {
        rack.removeAllViews();
        presetButtons.clear();
        widthButtons.clear();
        TextView heading = heading("Brushes · choose a preset and width");
        rack.addView(heading, new LinearLayout.LayoutParams(-1, dp(36)));
        LinearLayout presets = row();
        for (int i = 0; i < EditorChromeSpec.PRESET_IDS.length; i++) {
            String id = EditorChromeSpec.PRESET_IDS[i];
            BinaryButton button = BinaryButton.create(context, EditorChromeSpec.PRESET_LABELS[i],
                    view -> host.command(EditorCommand.preset(id)));
            button.setSelectedState(id.equals(state.activePresetId));
            presets.addView(button, weighted());
            presetButtons.add(button);
        }
        rack.addView(presets, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout widths = row();
        for (int i = 0; i < EditorChromeSpec.WIDTHS.length; i++) {
            float value = EditorChromeSpec.WIDTHS[i];
            BinaryButton button = BinaryButton.create(context, "Width " + (i + 1),
                    view -> host.command(EditorCommand.width(value)));
            button.setSelectedState(Float.compare(value, state.width) == 0);
            widths.addView(button, weighted());
            widthButtons.add(button);
        }
        BinaryButton done = BinaryButton.create(context, "Done", view -> host.closePanel());
        widths.addView(done, weighted());
        rack.addView(widths, new LinearLayout.LayoutParams(-1, dp(52)));
        rack.setVisibility(View.VISIBLE);
    }

    private void showToneRack(EditorState state) {
        rack.removeAllViews();
        toneButtons.clear();
        rack.addView(heading("Artwork tone · chrome stays black and white"),
                new LinearLayout.LayoutParams(-1, dp(36)));
        LinearLayout tones = row();
        for (int i = 0; i < EditorChromeSpec.TONES.length; i++) {
            int value = EditorChromeSpec.TONES[i];
            BinaryButton button = BinaryButton.create(context,
                    EditorChromeSpec.TONE_LABELS[i] + " · " + toneName(i),
                    view -> host.command(EditorCommand.tone(value)));
            button.setSelectedState(value == state.tone);
            tones.addView(button, weighted());
            toneButtons.add(button);
        }
        BinaryButton done = BinaryButton.create(context, "Done", view -> host.closePanel());
        tones.addView(done, weighted());
        rack.addView(tones, new LinearLayout.LayoutParams(-1, dp(52)));
        rack.setVisibility(View.VISIBLE);
    }

    private void showExportRack() {
        rack.removeAllViews();
        rack.addView(heading("Export a copy · your editable canvas stays safely autosaved"),
                new LinearLayout.LayoutParams(-1, dp(36)));
        LinearLayout choices = row();
        BinaryButton png = BinaryButton.create(context, "PNG image",
                view -> host.export(ExportFormat.PNG));
        BinaryButton nativeDocument = BinaryButton.create(context, "Inky project",
                view -> host.export(ExportFormat.NATIVE));
        BinaryButton cancel = BinaryButton.create(context, "Cancel", view -> host.closePanel());
        choices.addView(png, weighted());
        choices.addView(nativeDocument, weighted());
        choices.addView(cancel, weighted());
        rack.addView(choices, new LinearLayout.LayoutParams(-1, dp(52)));
        rack.setVisibility(View.VISIBLE);
    }

    private TextView heading(String value) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextColor(Color.BLACK);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(4), 0, 0, 0);
        return text;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(1), 0, dp(1), 0);
        return params;
    }

    private static String toneName(int index) {
        return new String[]{"Black", "Dark", "Mid", "Light"}[index];
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
