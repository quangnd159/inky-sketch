package dev.inkysketch.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class ProjectStore {
    interface SaveListener { void onComplete(long generation, boolean success); }
    interface WriteInterceptor { void beforeCommit(long generation) throws Exception; }

    enum LoadState {
        LOADED,
        MISSING,
        CORRUPT,
        RECOVERED_BACKUP,
        UNSUPPORTED_NEWER
    }

    static final class LoadResult {
        final InkDocument document;
        final LoadState state;
        final boolean writable;

        LoadResult(InkDocument document, LoadState state, boolean writable) {
            this.document = document;
            this.state = state;
            this.writable = writable;
        }
    }

    private enum ReadState { MISSING, VALID, CORRUPT, UNSUPPORTED }

    private static final class ReadResult {
        final ReadState state;
        final InkDocument document;

        ReadResult(ReadState state, InkDocument document) {
            this.state = state;
            this.document = document;
        }
    }

    private static final class PendingSave {
        final InkDocument snapshot;
        final long generation;
        final SaveListener listener;

        PendingSave(InkDocument snapshot, long generation, SaveListener listener) {
            this.snapshot = snapshot;
            this.generation = generation;
            this.listener = listener;
        }
    }

    private static final String FILE_NAME = "canvas.json";
    private static final String BACKUP_NAME = "canvas.json.bak";
    private final File directory;
    private final ExecutorService writer;
    private final WriteInterceptor interceptor;
    private PendingSave pending;
    private boolean active;
    private boolean closed;
    private boolean writesAllowed;
    private long latestGeneration = Long.MIN_VALUE;

    ProjectStore(Context context) {
        this(new File(context.getFilesDir(), "projects/default"),
                Executors.newSingleThreadExecutor(), generation -> {});
    }

    ProjectStore(File directory) {
        this(directory, Executors.newSingleThreadExecutor(), generation -> {});
    }

    ProjectStore(File directory, ExecutorService writer, WriteInterceptor interceptor) {
        this.directory = directory;
        this.writer = writer;
        this.interceptor = interceptor;
    }

    LoadResult load() {
        ReadResult primary = read(new File(directory, FILE_NAME));
        if (primary.state == ReadState.VALID) return loaded(primary.document, LoadState.LOADED, true);
        if (primary.state == ReadState.UNSUPPORTED) {
            return loaded(new InkDocument(), LoadState.UNSUPPORTED_NEWER, false);
        }

        ReadResult backup = read(new File(directory, BACKUP_NAME));
        if (backup.state == ReadState.VALID) {
            return loaded(backup.document, LoadState.RECOVERED_BACKUP, true);
        }
        if (backup.state == ReadState.UNSUPPORTED) {
            return loaded(new InkDocument(), LoadState.UNSUPPORTED_NEWER, false);
        }
        if (primary.state == ReadState.MISSING && backup.state == ReadState.MISSING) {
            return loaded(new InkDocument(), LoadState.MISSING, true);
        }
        return loaded(new InkDocument(), LoadState.CORRUPT, false);
    }

    private synchronized LoadResult loaded(InkDocument document, LoadState state, boolean writable) {
        writesAllowed = writable;
        return new LoadResult(document, state, writable);
    }

    private ReadResult read(File file) {
        if (!file.isFile()) return new ReadResult(ReadState.MISSING, null);
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            int version = json.optInt("version", -1);
            if (version > InkDocument.FORMAT_VERSION) {
                return new ReadResult(ReadState.UNSUPPORTED, null);
            }
            return new ReadResult(ReadState.VALID, InkDocument.fromJson(json));
        } catch (Exception ignored) {
            return new ReadResult(ReadState.CORRUPT, null);
        }
    }

    void save(InkDocument document, long generation, SaveListener listener) {
        synchronized (this) {
            if (generation < latestGeneration) return;
            latestGeneration = generation;
            if (!writesAllowed || closed) {
                listener.onComplete(generation, false);
            } else {
                pending = new PendingSave(document.copy(), generation, listener);
                if (!active) {
                    active = true;
                    writer.execute(this::drainWrites);
                }
            }
        }
    }

    private void drainWrites() {
        while (true) {
            PendingSave current;
            synchronized (this) {
                current = pending;
                pending = null;
            }
            boolean success = writeSnapshot(current.snapshot, current.generation);
            synchronized (this) {
                if (pending != null) continue;
                active = false;
                notifyAll();
                if (current.generation == latestGeneration) {
                    current.listener.onComplete(current.generation, success);
                }
            }
            return;
        }
    }

    synchronized boolean flush(long timeoutMillis) {
        long remainingNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        long deadline = System.nanoTime() + remainingNanos;
        while (active || pending != null) {
            if (remainingNanos <= 0L) return false;
            try {
                TimeUnit.NANOSECONDS.timedWait(this, remainingNanos);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
            remainingNanos = deadline - System.nanoTime();
        }
        return true;
    }

    void close() {
        synchronized (this) { closed = true; }
        writer.shutdown();
    }

    private boolean writeSnapshot(InkDocument snapshot, long generation) {
        File temporary = new File(directory, FILE_NAME + ".new");
        File target = new File(directory, FILE_NAME);
        File backup = new File(directory, BACKUP_NAME);
        File backupTemporary = new File(directory, BACKUP_NAME + ".new");
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Unable to create project folder");
            }
            byte[] bytes = (snapshot.toJson().toString() + "\n").getBytes(StandardCharsets.UTF_8);
            writeAndSync(temporary, bytes);
            interceptor.beforeCommit(generation);

            if (read(target).state == ReadState.VALID) {
                Files.copy(target.toPath(), backupTemporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
                sync(backupTemporary);
                atomicReplace(backupTemporary, backup);
            }
            atomicReplace(temporary, target);
            return true;
        } catch (Exception ignored) {
            try { Files.deleteIfExists(temporary.toPath()); } catch (IOException ignoredAgain) {}
            try { Files.deleteIfExists(backupTemporary.toPath()); } catch (IOException ignoredAgain) {}
            return false;
        }
    }

    private static void writeAndSync(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static void sync(File file) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.getFD().sync();
        }
    }

    private static void atomicReplace(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
