package com.jaoafa.chatwatcher.lib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public enum PathEnums {
    UserAudioStreams("user-audio-streams"),
    Recorded("recorded");

    private final Path directory;

    PathEnums(String root) {
        String env = System.getenv("PATH_" + this.name().toUpperCase(Locale.ROOT));
        this.directory = Path.of(env != null ? env : root);
    }

    public Path getPath(String name) {
        if (!Files.exists(this.directory)) {
            try {
                Files.createDirectories(this.directory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return this.directory.resolve(name);
    }
}
