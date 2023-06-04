package com.jaoafa.chatwatcher.lib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;

public class FileDeleteProcessor extends TimerTask {
    public static void register(Timer timer) {
        timer.schedule(new FileDeleteProcessor(), 0, 1000);
    }

    @Override
    public void run() {
        try {
            try (Stream<Path> paths = Files.walk(PathEnums.Recorded.toPath())) {
                // 10分過ぎたファイルを削除
                for (Path path : paths.filter(path -> !Files.isDirectory(path) && System.currentTimeMillis() - path.toFile().lastModified() >= 600000).toList()) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
