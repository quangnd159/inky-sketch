package dev.inkysketch.app;

enum ExportFormat {
    PNG("image/png", ".png", "Inky Sketch.png"),
    NATIVE("application/vnd.inkysketch+json", ".inky", "Inky Sketch.inky");

    final String mimeType;
    final String extension;
    final String suggestedName;

    ExportFormat(String mimeType, String extension, String suggestedName) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.suggestedName = suggestedName;
    }
}
