package dev.inkysketch.app;

final class EditorSnapshot {
    private final InkDocument document;
    final EditorState state;

    EditorSnapshot(InkDocument document, EditorState state) {
        this.document = document.copy();
        this.state = state;
    }

    InkDocument document() {
        return document;
    }
}
