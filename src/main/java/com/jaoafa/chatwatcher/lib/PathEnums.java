package com.jaoafa.chatwatcher.lib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public enum PathEnums {
    Config("/data/config.json", PathType.FILE),
    Servers("/data/servers.json", PathType.FILE),
    Model("/data/model", PathType.DIRECTORY);

    private final Path directory;
    private final PathType pathType;

    PathEnums(String root, PathType pathType) {
        String env = System.getenv("PATH_" + this.name().toUpperCase(Locale.ROOT));
        this.directory = Path.of(env != null ? env : root);
        this.pathType = pathType;
    }

    public Path toPath() {
        return this.directory;
    }

    @SuppressWarnings("unused")
    public Path getPath(String name) {
        if (this.pathType != PathType.DIRECTORY) {
            throw new IllegalArgumentException("This path is not directory.");
        }
        if (!Files.exists(this.directory)) {
            try {
                Files.createDirectories(this.directory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return this.directory.resolve(name);
    }

    enum PathType {
        DIRECTORY,
        FILE
    }
}
