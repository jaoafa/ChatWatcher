package com.jaoafa.chatwatcher.lib;

import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReportMap implements Runnable {
    public static void register(ScheduledExecutorService executorService) {
        executorService.scheduleAtFixedRate(new ReportMap(), 0, 3, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        System.out.println(Arrays.toString(UserAudioStream.getStreams().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toArray()));
    }
}
