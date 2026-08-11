package dev.inkysketch.app;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class DocumentExportTest {
    @Test public void exportFormatsHaveStableSafNamesAndMimeTypes() {
        assertEquals("image/png", ExportFormat.PNG.mimeType);
        assertTrue(ExportFormat.PNG.suggestedName.endsWith(".png"));
        assertEquals("application/vnd.inkysketch+json", ExportFormat.NATIVE.mimeType);
        assertTrue(ExportFormat.NATIVE.suggestedName.endsWith(".inky"));
    }

    @Test public void nativeExportRoundTripsLayersAndRejectsMalformedInput() throws Exception {
        InkDocument document = new InkDocument();
        document.renameSelectedLayer("Sketch");
        document.addStroke(stroke(.2f, .3f, 0xFF444444));
        document.addLayer();
        document.renameSelectedLayer("Ink");
        document.toggleSelectedLayerVisibility();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DocumentExport.writeNative(document, output);

        InkDocument restored = DocumentExport.readNative(output.toByteArray());
        assertEquals(2, restored.layers().size());
        assertEquals("Ink", restored.selectedLayer().name);
        assertTrue(!restored.selectedLayer().visible);
        try {
            DocumentExport.readNative("not json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fail("Malformed native files must not produce a blank document");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Invalid"));
        }
    }

    @Test public void pngHasExactDimensionsAndVisibleLayerOrder() throws Exception {
        InkDocument document = new InkDocument();
        document.addStroke(stroke(.5f, .5f, 0xFF000000));
        document.addLayer();
        document.addStroke(stroke(.5f, .5f, 0xFFFFFFFF));

        DecodedPng covered = render(document, 32, 24);
        assertEquals(32, covered.width);
        assertEquals(24, covered.height);
        assertEquals(0xFFFFFFFF, covered.pixel(16, 12));
        assertEquals(0xFFFFFFFF, covered.pixel(0, 0));

        document.toggleSelectedLayerVisibility();
        DecodedPng revealed = render(document, 32, 24);
        assertNotEquals(0xFFFFFFFF, revealed.pixel(16, 12));
    }

    private static InkDocument.Stroke stroke(float x, float y, int color) {
        return new InkDocument.Stroke(InkDocument.Brush.PEN, 8f, color,
                Arrays.asList(new InkDocument.Point(x, y, 1f, 1L)));
    }

    private static DecodedPng render(InkDocument document, int width, int height) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DocumentExport.writePng(document.copy(), width, height, 1f, output);
        return DecodedPng.read(output.toByteArray());
    }

    private static final class DecodedPng {
        final int width;
        final int height;
        final byte[] rows;

        DecodedPng(int width, int height, byte[] rows) {
            this.width = width;
            this.height = height;
            this.rows = rows;
        }

        int pixel(int x, int y) {
            int offset = y * (1 + width * 4) + 1 + x * 4;
            return (rows[offset + 3] & 255) << 24 | (rows[offset] & 255) << 16
                    | (rows[offset + 1] & 255) << 8 | rows[offset + 2] & 255;
        }

        static DecodedPng read(byte[] png) throws Exception {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(png));
            byte[] signature = new byte[8];
            input.readFully(signature);
            assertEquals(137, signature[0] & 255);
            int width = 0;
            int height = 0;
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            while (input.available() > 0) {
                int length = input.readInt();
                byte[] typeBytes = new byte[4];
                input.readFully(typeBytes);
                String type = new String(typeBytes, java.nio.charset.StandardCharsets.US_ASCII);
                byte[] payload = new byte[length];
                input.readFully(payload);
                input.readInt();
                if ("IHDR".equals(type)) {
                    DataInputStream header = new DataInputStream(new ByteArrayInputStream(payload));
                    width = header.readInt();
                    height = header.readInt();
                } else if ("IDAT".equals(type)) {
                    compressed.write(payload);
                } else if ("IEND".equals(type)) {
                    break;
                }
            }
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            try (InflaterInputStream inflated = new InflaterInputStream(
                    new ByteArrayInputStream(compressed.toByteArray()))) {
                byte[] buffer = new byte[1024];
                for (int count; (count = inflated.read(buffer)) >= 0; ) decoded.write(buffer, 0, count);
            }
            assertEquals(height * (1 + width * 4), decoded.size());
            return new DecodedPng(width, height, decoded.toByteArray());
        }
    }
}
