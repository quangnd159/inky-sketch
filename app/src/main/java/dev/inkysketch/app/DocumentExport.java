package dev.inkysketch.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class DocumentExport {
    private static final int MAX_NATIVE_BYTES = 64 * 1024 * 1024;

    private DocumentExport() {}

    static void writeNative(InkDocument document, OutputStream output) throws IOException {
        try {
            output.write(document.toJson().toString().getBytes(StandardCharsets.UTF_8));
        } catch (JSONException error) {
            throw new IOException("Could not encode Inky Sketch document", error);
        }
    }

    static InkDocument readNative(byte[] encoded) throws IOException {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_NATIVE_BYTES) {
            throw new IOException("Invalid Inky Sketch document size");
        }
        try {
            return InkDocument.fromJson(new JSONObject(new String(encoded, StandardCharsets.UTF_8)));
        } catch (JSONException | RuntimeException error) {
            throw new IOException("Invalid Inky Sketch document", error);
        }
    }

    static void writePng(InkDocument document, int width, int height, float density,
            OutputStream output) throws IOException {
        FlatPngExporter.write(document, width, height, density, output);
    }
}
