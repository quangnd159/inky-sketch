package dev.inkysketch.app;

final class EditorChromeSpec {
    static final int DOCK_HEIGHT_DP = 60;
    static final int MIN_TARGET_DP = 48;
    static final int DOCK_ITEM_COUNT = 8;
    static final String[] DOCK_LABELS = {
            "Inker", "Pencil", "Marker", "Eraser", "Undo", "Redo", "Tone", "Layers"
    };
    static final String[] PRESET_IDS = {
            "fineliner", "fountain", "ballpoint", "hb_pencil", "soft_pencil", "marker"
    };
    static final String[] PRESET_LABELS = {
            "Fineliner", "Fountain", "Ballpoint", "HB Pencil", "Soft Pencil", "Marker"
    };
    static final float[] WIDTHS = {2.5f, 5f, 9f};
    static final int[] TONES = {0xFF000000, 0xFF444444, 0xFF888888, 0xFFBBBBBB};
    static final String[] TONE_LABELS = {"K", "D", "M", "L"};

    private EditorChromeSpec() {}

    static int dockCellWidthDp(int availableWidthDp) {
        return availableWidthDp / DOCK_ITEM_COUNT;
    }
}
