package dev.inkysketch.app;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class EditorChromeSpecTest {
    @Test public void dockFitsSixHundredAndEightFortyDpScreens() {
        assertEquals(8, EditorChromeSpec.DOCK_ITEM_COUNT);
        assertEquals(48, EditorChromeSpec.MIN_TARGET_DP);
        assertEquals(75, EditorChromeSpec.dockCellWidthDp(600));
        assertEquals(105, EditorChromeSpec.dockCellWidthDp(840));
        assertEquals(8, EditorChromeSpec.DOCK_LABELS.length);
    }

    @Test public void chromeOffersOnlyFourArtworkTones() {
        assertEquals(4, EditorChromeSpec.TONES.length);
        assertEquals(4, EditorChromeSpec.TONE_LABELS.length);
    }

    @Test public void rightPanelIsBoundedAndHasEveryLayerOperation() throws Exception {
        assertEquals(270, EditorChromeSpec.layerPanelWidthDp(600));
        assertEquals(320, EditorChromeSpec.layerPanelWidthDp(840));
        String source = source("LayerPanelView.java");
        assertTrue(source.contains("Gravity.END"));
        assertTrue(source.contains("EditorCommand.addLayer()"));
        assertTrue(source.contains("renameLayer"));
        assertTrue(source.contains("moveLayer(1)"));
        assertTrue(source.contains("toggleLayerVisibility"));
        assertTrue(source.contains("clearLayer"));
        assertTrue(source.contains("deleteLayer"));
    }

    @Test public void chromeAvoidsFadesRipplesElevationAndAnimations() throws Exception {
        String source = source("BinaryButton.java");
        assertTrue(source.contains("setAlpha(1f)"));
        assertFalse(source.contains("RippleDrawable"));
        assertFalse(source.contains("setElevation"));
        assertFalse(source.contains("Animator"));
        assertFalse(source.contains("setAlpha(0."));
    }

    @Test public void manualRefreshUsesTheRawInkSuspensionPath() throws Exception {
        String source = source("MainActivity.java");
        int refresh = source.indexOf("void fullRefresh()");
        int nextMethod = source.indexOf("@Override", refresh + 1);
        String body = source.substring(refresh, nextMethod);
        assertTrue(body.contains("rawInk.performUiAction"));
        assertTrue(body.contains("requestRender"));
    }

    private static String source(String name) throws Exception {
        File file = new File("src/main/java/dev/inkysketch/app/" + name);
        if (!file.exists()) file = new File("app/src/main/java/dev/inkysketch/app/" + name);
        return new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8);
    }
}
