package dev.inkysketch.app;

import android.content.Context;

final class ProjectPersistence implements EditorController.Persistence {
    private final ProjectStore store;

    ProjectPersistence(Context context) {
        store = new ProjectStore(context);
    }

    InkDocument load() { return store.load(); }
    @Override public void save(InkDocument document) { store.save(document); }
    @Override public void close() { store.close(); }
}
