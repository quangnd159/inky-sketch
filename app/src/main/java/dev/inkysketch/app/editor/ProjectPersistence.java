package dev.inkysketch.app;

import android.content.Context;

final class ProjectPersistence implements EditorController.Persistence {
    private final ProjectStore store;
    private ProjectStore.LoadResult loadResult;

    ProjectPersistence(Context context) {
        store = new ProjectStore(context);
    }

    InkDocument load() {
        loadResult = store.load();
        return loadResult.document;
    }

    ProjectStore.LoadState loadState() {
        return loadResult == null ? ProjectStore.LoadState.MISSING : loadResult.state;
    }

    boolean isWritable() { return loadResult == null || loadResult.writable; }

    @Override public void save(InkDocument document, long generation, Callback callback) {
        store.save(document, generation, callback::onComplete);
    }
    @Override public boolean flush(long timeoutMillis) { return store.flush(timeoutMillis); }
    @Override public void close() { store.close(); }
}
