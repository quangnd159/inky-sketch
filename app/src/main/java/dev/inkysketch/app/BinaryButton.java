package dev.inkysketch.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/** A deliberately boring, high-contrast control that survives low-contrast e-ink modes. */
@SuppressLint("AppCompatCustomView")
final class BinaryButton extends TextView {
    private static final Map<String, Drawable.ConstantState> BACKGROUNDS = new HashMap<>();
    private String label = "";
    private boolean selectedState;

    BinaryButton(Context context) { this(context, null); }

    BinaryButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.CENTER);
        setTextSize(12);
        setTextColor(Color.BLACK);
        setMinWidth(dp(48));
        setMinHeight(dp(48));
        setPadding(dp(6), 0, dp(6), 0);
        setAllCaps(false);
        setStateListAnimator(null);
        setSoundEffectsEnabled(false);
        refreshStyle();
    }

    void setLabel(String value) {
        label = value;
        refreshText();
    }

    String label() { return label; }

    void setSelectedState(boolean selected) {
        if (selectedState == selected) return;
        selectedState = selected;
        refreshStyle();
    }

    boolean selectedState() { return selectedState; }

    @Override public void setEnabled(boolean enabled) {
        if (isEnabled() == enabled) return;
        super.setEnabled(enabled);
        refreshStyle();
    }

    private void refreshStyle() {
        boolean enabled = isEnabled();
        setTextColor(selectedState && enabled ? Color.WHITE : Color.BLACK);
        setBackground(cachedBackground(selectedState && enabled, !enabled));
        setAlpha(1f);
        refreshText();
    }

    private void refreshText() {
        String prefix = selectedState && isEnabled() ? "✓ " : !isEnabled() ? "× " : "";
        setText(prefix + label);
    }

    private Drawable cachedBackground(boolean selected, boolean disabled) {
        String key = dp(2) + ":" + selected + ":" + disabled;
        Drawable.ConstantState state;
        synchronized (BACKGROUNDS) {
            state = BACKGROUNDS.get(key);
            if (state == null) {
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setColor(selected ? Color.BLACK : Color.WHITE);
                if (disabled) drawable.setStroke(dp(2), Color.BLACK, dp(4), dp(3));
                else drawable.setStroke(dp(2), Color.BLACK);
                state = drawable.getConstantState();
                BACKGROUNDS.put(key, state);
            }
        }
        return state == null ? null : state.newDrawable(getResources());
    }

    static BinaryButton create(Context context, String label, View.OnClickListener listener) {
        BinaryButton button = new BinaryButton(context);
        button.setLabel(label);
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
