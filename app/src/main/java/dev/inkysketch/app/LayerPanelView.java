package dev.inkysketch.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class LayerPanelView {
    private static Drawable.ConstantState PANEL_BACKGROUND;
    interface Host {
        void command(EditorCommand command);
        void closePanel();
    }

    private final Context context;
    private final Host host;
    private final LinearLayout panel;
    private final LinearLayout content;
    private EditorSnapshot snapshot;

    LayerPanelView(Context context, FrameLayout root, Host host) {
        this.context = context;
        this.host = host;
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(6), dp(8), dp(6));
        panel.setBackground(panelBackground());
        panel.setVisibility(View.GONE);
        ScrollView scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        int screenDp = Math.round(context.getResources().getDisplayMetrics().widthPixels
                / context.getResources().getDisplayMetrics().density);
        int width = dp(EditorChromeSpec.layerPanelWidthDp(screenDp));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, -1, Gravity.END);
        params.topMargin = dp(56);
        params.bottomMargin = dp(EditorChromeSpec.DOCK_HEIGHT_DP);
        root.addView(panel, params);
    }

    void render(EditorSnapshot next) {
        snapshot = next;
        EditorState.Panel mode = next.state.panel;
        boolean layers = mode == EditorState.Panel.LAYERS
                || mode == EditorState.Panel.LAYER_RENAME
                || mode == EditorState.Panel.LAYER_CLEAR_CONFIRM
                || mode == EditorState.Panel.LAYER_DELETE_CONFIRM;
        if (!layers) {
            panel.setVisibility(View.GONE);
            return;
        }
        rebuild(mode);
        panel.setVisibility(View.VISIBLE);
    }

    private void rebuild(EditorState.Panel mode) {
        content.removeAllViews();
        InkDocument document = snapshot.document();
        InkDocument.Layer selected = document.selectedLayer();
        content.addView(label("Layers · top first"), full(32));
        for (int i = document.layers().size() - 1; i >= 0; i--) {
            InkDocument.Layer layer = document.layers().get(i);
            String text = (layer.id.equals(selected.id) ? "✓ " : "  ")
                    + layer.name + " · " + layer.strokes.size();
            LinearLayout layerRow = horizontal();
            BinaryButton row = BinaryButton.create(context, text,
                    view -> host.command(EditorCommand.selectLayer(layer.id)));
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setSelectedState(layer.id.equals(selected.id));
            layerRow.addView(row, new LinearLayout.LayoutParams(0, dp(48), 1f));
            BinaryButton visibility = BinaryButton.create(context, layer.visible ? "Hide" : "Show", view -> {
                host.command(EditorCommand.selectLayer(layer.id));
                host.command(EditorCommand.toggleLayerVisibility());
            });
            layerRow.addView(visibility, new LinearLayout.LayoutParams(dp(64), dp(48)));
            content.addView(layerRow, full(48));
        }
        if (mode == EditorState.Panel.LAYER_RENAME) showRename(selected);
        else if (mode == EditorState.Panel.LAYER_CLEAR_CONFIRM) showConfirmation(selected, true);
        else if (mode == EditorState.Panel.LAYER_DELETE_CONFIRM) showConfirmation(selected, false);
        else showActions(selected);
    }

    private void showActions(InkDocument.Layer selected) {
        LinearLayout row = horizontal();
        add(row, "Add", () -> host.command(EditorCommand.addLayer()));
        add(row, selected.visible ? "Hide" : "Show",
                () -> host.command(EditorCommand.toggleLayerVisibility()));
        add(row, "Up", () -> host.command(EditorCommand.moveLayer(1)));
        add(row, "Down", () -> host.command(EditorCommand.moveLayer(-1)));
        content.addView(row, full(48));
        LinearLayout edit = horizontal();
        add(edit, "Rename", () -> host.command(EditorCommand.panel(EditorState.Panel.LAYER_RENAME)));
        add(edit, "Clear", () -> host.command(EditorCommand.panel(EditorState.Panel.LAYER_CLEAR_CONFIRM)));
        add(edit, "Delete", () -> host.command(EditorCommand.panel(EditorState.Panel.LAYER_DELETE_CONFIRM)));
        add(edit, "Done", host::closePanel);
        content.addView(edit, full(48));
    }

    private void showRename(InkDocument.Layer selected) {
        content.addView(label("Rename " + selected.name), full(32));
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setText(selected.name);
        input.selectAll();
        content.addView(input, full(48));
        LinearLayout actions = horizontal();
        add(actions, "Rename", () -> {
            host.command(EditorCommand.renameLayer(input.getText().toString()));
            host.command(EditorCommand.panel(EditorState.Panel.LAYERS));
        });
        add(actions, "Cancel", () -> host.command(EditorCommand.panel(EditorState.Panel.LAYERS)));
        content.addView(actions, full(48));
    }

    private void showConfirmation(InkDocument.Layer selected, boolean clear) {
        String verb = clear ? "Clear" : "Delete";
        content.addView(label(verb + " " + selected.name + "?"), full(32));
        content.addView(label(clear ? "Only this layer will be cleared. You can undo afterward."
                : "This layer and its marks will be removed. You can undo afterward."), full(32));
        LinearLayout actions = horizontal();
        add(actions, verb, () -> {
            host.command(clear ? EditorCommand.clearLayer() : EditorCommand.deleteLayer());
            host.command(EditorCommand.panel(EditorState.Panel.LAYERS));
        });
        add(actions, "Cancel", () -> host.command(EditorCommand.panel(EditorState.Panel.LAYERS)));
        content.addView(actions, full(48));
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private void add(LinearLayout row, String text, Runnable action) {
        BinaryButton button = BinaryButton.create(context, text, view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1f));
    }

    private TextView label(String value) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextColor(Color.BLACK);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private LinearLayout.LayoutParams full(int height) {
        return new LinearLayout.LayoutParams(-1, dp(height));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private Drawable panelBackground() {
        if (PANEL_BACKGROUND == null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.WHITE);
            drawable.setStroke(dp(2), Color.BLACK);
            PANEL_BACKGROUND = drawable.getConstantState();
        }
        return PANEL_BACKGROUND.newDrawable(context.getResources());
    }
}
