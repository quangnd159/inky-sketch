package dev.inkysketch.app;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProjectStoreTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void missingProjectSavesAtomicallyAndKeepsLastGoodBackup() throws Exception {
        File directory = temporary.newFolder("project");
        ProjectStore store = new ProjectStore(directory);
        assertEquals(ProjectStore.LoadState.MISSING, store.load().state);

        List<String> callbacks = Collections.synchronizedList(new ArrayList<>());
        store.save(named("First"), 1, (generation, success) -> callbacks.add(generation + ":" + success));
        assertTrue(store.flush(2000));
        store.save(named("Second"), 2, (generation, success) -> callbacks.add(generation + ":" + success));
        assertTrue(store.flush(2000));
        store.close();

        assertEquals(Arrays.asList("1:true", "2:true"), callbacks);
        assertEquals("Second", readDocument(new File(directory, "canvas.json")).selectedLayer().name);
        assertEquals("First", readDocument(new File(directory, "canvas.json.bak")).selectedLayer().name);
        assertFalse(new File(directory, "canvas.json.new").exists());
    }

    @Test public void corruptPrimaryRecoversBackupWithoutReplacingItWithCorruptBytes() throws Exception {
        File directory = temporary.newFolder("recovery");
        write(new File(directory, "canvas.json"), "not json");
        write(new File(directory, "canvas.json.bak"), named("Last good").toJson().toString());
        ProjectStore store = new ProjectStore(directory);

        ProjectStore.LoadResult result = store.load();
        assertEquals(ProjectStore.LoadState.RECOVERED_BACKUP, result.state);
        assertEquals("Last good", result.document.selectedLayer().name);
        store.save(named("Recovered edit"), 1, (generation, success) -> assertTrue(success));
        assertTrue(store.flush(2000));
        store.close();

        assertEquals("Recovered edit", readDocument(new File(directory, "canvas.json")).selectedLayer().name);
        assertEquals("Last good", readDocument(new File(directory, "canvas.json.bak")).selectedLayer().name);
    }

    @Test public void unsupportedNewerPrimaryIsReadOnlyAndNeverOverwritten() throws Exception {
        File directory = temporary.newFolder("newer");
        File primary = new File(directory, "canvas.json");
        String newer = "{\"version\":999,\"future\":true}";
        write(primary, newer);
        ProjectStore store = new ProjectStore(directory);

        ProjectStore.LoadResult result = store.load();
        assertEquals(ProjectStore.LoadState.UNSUPPORTED_NEWER, result.state);
        assertFalse(result.writable);
        AtomicBoolean callback = new AtomicBoolean(true);
        store.save(named("Must not save"), 1, (generation, success) -> callback.set(success));
        assertFalse(callback.get());
        assertTrue(store.flush(10));
        store.close();
        assertEquals(newer, new String(Files.readAllBytes(primary.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void corruptProjectWithoutRecoveryIsReadOnly() throws Exception {
        File directory = temporary.newFolder("corrupt");
        File primary = new File(directory, "canvas.json");
        write(primary, "broken");
        ProjectStore store = new ProjectStore(directory);
        assertEquals(ProjectStore.LoadState.CORRUPT, store.load().state);

        store.save(named("Blank replacement"), 1, (generation, success) -> assertFalse(success));
        store.close();
        assertEquals("broken", new String(Files.readAllBytes(primary.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void interruptedCommitLeavesPrimaryIntactAndReportsFailure() throws Exception {
        File directory = temporary.newFolder("interrupted");
        write(new File(directory, "canvas.json"), named("Original").toJson().toString());
        ProjectStore store = new ProjectStore(directory, Executors.newSingleThreadExecutor(),
                generation -> { throw new Exception("simulated interruption"); });
        assertEquals(ProjectStore.LoadState.LOADED, store.load().state);
        AtomicBoolean success = new AtomicBoolean(true);

        store.save(named("Replacement"), 4, (generation, saved) -> success.set(saved));
        assertTrue(store.flush(2000));
        store.close();
        assertFalse(success.get());
        assertEquals("Original", readDocument(new File(directory, "canvas.json")).selectedLayer().name);
    }

    @Test public void activeWriteKeepsOnlyLatestPendingSnapshotAndLatestCallback() throws Exception {
        File directory = temporary.newFolder("coalescing");
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<Long> writes = Collections.synchronizedList(new ArrayList<>());
        ProjectStore store = new ProjectStore(directory, Executors.newSingleThreadExecutor(), generation -> {
            writes.add(generation);
            if (generation == 1L) {
                firstEntered.countDown();
                if (!releaseFirst.await(2, TimeUnit.SECONDS)) throw new Exception("test timeout");
            }
        });
        store.load();
        List<Long> callbacks = Collections.synchronizedList(new ArrayList<>());

        store.save(named("One"), 1, (generation, success) -> callbacks.add(generation));
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
        store.save(named("Two"), 2, (generation, success) -> callbacks.add(generation));
        store.save(named("Three"), 3, (generation, success) -> callbacks.add(generation));
        releaseFirst.countDown();
        assertTrue(store.flush(2000));
        store.close();

        assertEquals(Arrays.asList(1L, 3L), writes);
        assertEquals(Collections.singletonList(3L), callbacks);
        assertEquals("Three", readDocument(new File(directory, "canvas.json")).selectedLayer().name);
    }

    @Test public void flushHonorsItsBoundAndCloseDoesNotWaitForWriter() throws Exception {
        File directory = temporary.newFolder("bounded");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ProjectStore store = new ProjectStore(directory, Executors.newSingleThreadExecutor(), generation -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
        });
        store.load();
        store.save(named("Slow"), 1, (generation, success) -> {});
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        long beforeFlush = System.nanoTime();
        assertFalse(store.flush(20));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beforeFlush) < 500);
        long beforeClose = System.nanoTime();
        store.close();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beforeClose) < 500);
        release.countDown();
    }

    private static InkDocument named(String name) {
        InkDocument document = new InkDocument();
        document.renameSelectedLayer(name);
        return document;
    }

    private static InkDocument readDocument(File file) throws Exception {
        return InkDocument.fromJson(new JSONObject(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
    }

    private static void write(File file, String value) throws Exception {
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }
}
