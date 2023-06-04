package com.jaoafa.chatwatcher.lib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileDeleteProcessor extends Thread {
    private final Path path;
    private final Thread googleThread;
    private final Thread voskThread;
    private final Thread whisperThread;

    public FileDeleteProcessor(Path path, Thread googleThread, Thread voskThread, Thread whisperThread) {
        this.path = path;
        this.googleThread = googleThread;
        this.voskThread = voskThread;
        this.whisperThread = whisperThread;
    }

    @Override
    public void run() {
        try {
            while (googleThread.isAlive() || voskThread.isAlive() || whisperThread.isAlive()) {
                Thread.sleep(100);
            }

            Files.deleteIfExists(this.path);
        } catch (IOException | InterruptedException e) {
            Utils.println("❌ Failed to delete file: %s".formatted(this.path.toAbsolutePath().toString()));
        }
    }
}
