package dev.inkysketch.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

final class LayerDialogs {
    interface Host {
        EditorSnapshot snapshot();
        void command(EditorCommand command);
        void panelClosed();
    }

    private final Activity activity;
    private final Host host;
    private AlertDialog active;

    LayerDialogs(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    void showLayers() {
        host.command(EditorCommand.panel(EditorState.Panel.LAYERS));
        EditorSnapshot snapshot = host.snapshot();
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(4));
        InkDocument document = snapshot.document();
        InkDocument.Layer selected = document.selectedLayer();
        for (int index = document.layers().size() - 1; index >= 0; index--) {
            InkDocument.Layer layer = document.layers().get(index);
            Button row = button((layer.id.equals(selected.id) ? "▶ " : "   ")
                    + (layer.visible ? "● " : "○ ") + layer.name + "  ·  " + layer.strokes.size());
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setOnClickListener(view -> {
                host.command(EditorCommand.selectLayer(layer.id));
                replace(this::showLayers);
            });
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Layers · top first")
                .setView(scroll)
                .setPositiveButton("Done", null)
                .setNeutralButton("Add", null)
                .setNegativeButton("Edit", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                host.command(EditorCommand.addLayer());
                replace(this::showLayers);
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> replace(this::showActions));
        });
        show(dialog);
    }

    private void showActions() {
        host.command(EditorCommand.panel(EditorState.Panel.LAYER_ACTIONS));
        InkDocument.Layer layer = host.snapshot().document().selectedLayer();
        String[] actions = {"Rename", "Move up", "Move down", layer.visible ? "Hide" : "Show", "Clear", "Delete"};
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(layer.name)
                .setItems(actions, (ignored, which) -> {
                    if (which == 0) replace(this::showRename);
                    else if (which == 1) runAndReturn(EditorCommand.moveLayer(1));
                    else if (which == 2) runAndReturn(EditorCommand.moveLayer(-1));
                    else if (which == 3) runAndReturn(EditorCommand.toggleLayerVisibility());
                    else if (which == 4) replace(this::showClearConfirmation);
                    else replace(this::showDeleteConfirmation);
                })
                .setNegativeButton("Back", (ignored, which) -> replace(this::showLayers))
                .create();
        show(dialog);
    }

    private void showRename() {
        host.command(EditorCommand.panel(EditorState.Panel.RENAME));
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(host.snapshot().document().selectedLayer().name);
        input.selectAll();
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Rename layer")
                .setView(input)
                .setNegativeButton("Cancel", (ignored, which) -> replace(this::showActions))
                .setPositiveButton("Rename", (ignored, which) -> {
                    host.command(EditorCommand.renameLayer(input.getText().toString()));
                    replace(this::showLayers);
                })
                .create();
        show(dialog);
    }

    private void showClearConfirmation() {
        host.command(EditorCommand.panel(EditorState.Panel.CONFIRM_CLEAR));
        String name = host.snapshot().document().selectedLayer().name;
        show(new AlertDialog.Builder(activity)
                .setTitle("Clear " + name + "?")
                .setMessage("Only this layer will be cleared. You can undo afterward.")
                .setNegativeButton("Cancel", (ignored, which) -> replace(this::showActions))
                .setPositiveButton("Clear", (ignored, which) -> runAndReturn(EditorCommand.clearLayer()))
                .create());
    }

    private void showDeleteConfirmation() {
        host.command(EditorCommand.panel(EditorState.Panel.CONFIRM_DELETE));
        InkDocument document = host.snapshot().document();
        show(new AlertDialog.Builder(activity)
                .setTitle("Delete " + document.selectedLayer().name + "?")
                .setMessage(document.layers().size() == 1
                        ? "The only layer will be cleared. You can undo afterward."
                        : "The layer and its marks will be removed. You can undo afterward.")
                .setNegativeButton("Cancel", (ignored, which) -> replace(this::showActions))
                .setPositiveButton("Delete", (ignored, which) -> runAndReturn(EditorCommand.deleteLayer()))
                .create());
    }

    private void runAndReturn(EditorCommand command) {
        host.command(command);
        replace(this::showLayers);
    }

    private void show(AlertDialog dialog) {
        active = dialog;
        dialog.setOnDismissListener(this::onDismissed);
        dialog.show();
    }

    private void replace(Runnable next) {
        AlertDialog previous = active;
        active = null;
        if (previous != null) previous.dismiss();
        next.run();
    }

    private void onDismissed(DialogInterface dialog) {
        if (dialog != active) return;
        active = null;
        host.panelClosed();
    }

    private Button button(String text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
