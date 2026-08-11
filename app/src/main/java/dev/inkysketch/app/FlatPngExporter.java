package dev.inkysketch.app;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Small, deterministic, Android-independent flattened renderer and PNG encoder. */
final class FlatPngExporter {
    private static final byte[] SIGNATURE = {
            (byte) 137, 80, 78, 71, 13, 10, 26, 10
    };

    private FlatPngExporter() {}

    static void write(InkDocument document, int width, int height, float density,
            OutputStream output) throws IOException {
        if (width < 1 || height < 1 || (long) width * height > 64_000_000L) {
            throw new IOException("Invalid export dimensions");
        }
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, 0xFFFFFFFF);
        for (InkDocument.Layer layer : document.layers()) {
            if (!layer.visible) continue;
            for (InkDocument.Stroke stroke : layer.strokes) {
                renderStroke(pixels, width, height, density, stroke);
            }
        }
        writePng(pixels, width, height, output);
    }

    private static void renderStroke(int[] pixels, int width, int height, float density,
            InkDocument.Stroke stroke) {
        if (stroke.points.isEmpty()) return;
        BrushPreset preset = BrushCatalog.get(stroke.presetId);
        InkDocument.Point previous = stroke.points.get(0);
        if (stroke.points.size() == 1) {
            stamp(pixels, width, height, previous.x * width, previous.y * height,
                    radius(density, stroke.width, preset, previous.pressure),
                    tinted(stroke.color, preset.value(previous.pressure)));
            return;
        }
        for (int index = 1; index < stroke.points.size(); index++) {
            InkDocument.Point point = stroke.points.get(index);
            renderSegment(pixels, width, height, density, stroke, preset, previous, point);
            previous = point;
        }
    }

    private static void renderSegment(int[] pixels, int width, int height, float density,
            InkDocument.Stroke stroke, BrushPreset preset, InkDocument.Point start,
            InkDocument.Point end) {
        float ax = start.x * width;
        float ay = start.y * height;
        float bx = end.x * width;
        float by = end.y * height;
        float distance = (float) Math.hypot(bx - ax, by - ay);
        int steps = Math.max(1, (int) Math.ceil(distance / .75f));
        for (int step = 0; step <= steps; step++) {
            float t = step / (float) steps;
            float pressure = start.pressure + (end.pressure - start.pressure) * t;
            stamp(pixels, width, height, ax + (bx - ax) * t, ay + (by - ay) * t,
                    radius(density, stroke.width, preset, pressure),
                    tinted(stroke.color, preset.value(pressure)));
        }
    }

    private static float radius(float density, float width, BrushPreset preset, float pressure) {
        return Math.max(.5f, density * width * preset.width(pressure) / 2f);
    }

    private static void stamp(int[] pixels, int width, int height, float cx, float cy,
            float radius, int color) {
        int left = Math.max(0, (int) Math.floor(cx - radius));
        int top = Math.max(0, (int) Math.floor(cy - radius));
        int right = Math.min(width - 1, (int) Math.ceil(cx + radius));
        int bottom = Math.min(height - 1, (int) Math.ceil(cy + radius));
        float squared = radius * radius;
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                float dx = x + .5f - cx;
                float dy = y + .5f - cy;
                if (dx * dx + dy * dy <= squared) pixels[y * width + x] = color;
            }
        }
    }

    private static int tinted(int color, float value) {
        int red = Math.round(255 - (255 - ((color >> 16) & 255)) * value);
        int green = Math.round(255 - (255 - ((color >> 8) & 255)) * value);
        int blue = Math.round(255 - (255 - (color & 255)) * value);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void writePng(int[] pixels, int width, int height, OutputStream output)
            throws IOException {
        DataOutputStream data = new DataOutputStream(output);
        data.write(SIGNATURE);
        ByteArrayOutputStream header = new ByteArrayOutputStream(13);
        DataOutputStream headerData = new DataOutputStream(header);
        headerData.writeInt(width);
        headerData.writeInt(height);
        headerData.writeByte(8);
        headerData.writeByte(6);
        headerData.writeByte(0);
        headerData.writeByte(0);
        headerData.writeByte(0);
        writeChunk(data, "IHDR", header.toByteArray());

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream rows = new DeflaterOutputStream(compressed, deflater)) {
            for (int y = 0; y < height; y++) {
                rows.write(0);
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[y * width + x];
                    rows.write((pixel >> 16) & 255);
                    rows.write((pixel >> 8) & 255);
                    rows.write(pixel & 255);
                    rows.write((pixel >>> 24) & 255);
                }
            }
        }
        writeChunk(data, "IDAT", compressed.toByteArray());
        writeChunk(data, "IEND", new byte[0]);
        data.flush();
    }

    private static void writeChunk(DataOutputStream output, String type, byte[] payload)
            throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        output.writeInt(payload.length);
        output.write(typeBytes);
        output.write(payload);
        CRC32 checksum = new CRC32();
        checksum.update(typeBytes);
        checksum.update(payload);
        output.writeInt((int) checksum.getValue());
    }
}
