package dev.inkysketch.app;

import java.util.ArrayDeque;
import java.util.Deque;

final class History {
    private final int limit;
    private final Deque<InkDocument> undo = new ArrayDeque<>();
    private final Deque<InkDocument> redo = new ArrayDeque<>();

    History(int limit) {
        this.limit = limit;
    }

    void record(InkDocument before) {
        undo.addLast(before.copy());
        while (undo.size() > limit) undo.pollFirst();
        redo.clear();
    }

    InkDocument undo(InkDocument current) {
        InkDocument previous = undo.pollLast();
        if (previous == null) return null;
        redo.addLast(current.copy());
        return previous;
    }

    InkDocument redo(InkDocument current) {
        InkDocument next = redo.pollLast();
        if (next == null) return null;
        undo.addLast(current.copy());
        return next;
    }

    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }
    int undoSize() { return undo.size(); }
}
