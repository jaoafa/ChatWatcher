package com.jaoafa.chatwatcher.lib;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class UserAudioStreamMonitor extends TimerTask {
    public static void register(Timer timer) {
        timer.schedule(new UserAudioStreamMonitor(), 0, 100);
    }

    @Override
    public void run() {
        try {
            for (Map.Entry<String, UserAudioStream> entry : UserAudioStream.getStreams().entrySet()) {
                new UserAudioStreamProcessor(entry.getKey(), entry.getValue()).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}