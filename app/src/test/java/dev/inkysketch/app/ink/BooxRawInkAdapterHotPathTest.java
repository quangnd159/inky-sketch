package dev.inkysketch.app;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BooxRawInkAdapterHotPathTest {
    @Test public void rawMoveCallbacksRemainCommentOnlyNoOps() throws Exception {
        Path sourcePath = Paths.get("src/main/java/dev/inkysketch/app/ink/BooxRawInkAdapter.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Paths.get("app/src/main/java/dev/inkysketch/app/ink/BooxRawInkAdapter.java");
        }
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        assertNoWork(body(source, "onRawDrawingTouchPointMoveReceived"));
        assertNoWork(body(source, "onRawErasingTouchPointMoveReceived"));
    }

    private static String body(String source, String method) {
        int start = source.indexOf(method);
        assertTrue(method + " must exist", start >= 0);
        int open = source.indexOf('{', start);
        int close = source.indexOf('}', open);
        return source.substring(open + 1, close).replaceAll("(?s)/\\*.*?\\*/|//.*?(\\R|$)", "").trim();
    }

    private static void assertNoWork(String body) {
        assertFalse("move callback must be empty but was: " + body, body.length() > 0);
    }
}
