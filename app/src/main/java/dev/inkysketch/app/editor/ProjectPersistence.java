package dev.inkysketch.app;

import android.content.Context;

final class ProjectPersistence implements EditorController.Persistence {
    private final ProjectStore store;

    ProjectPersistence(Context context) {
        store = new ProjectStore(context);
    }

    InkDocument load() { return store.load(); }
    @Override public void save(InkDocument document, long generation, Callback callback) {
        store.save(document, generation, callback::onComplete);
    }
    @Override public void close() { store.close(); }
}
