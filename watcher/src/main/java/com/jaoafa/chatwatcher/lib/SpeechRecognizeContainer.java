package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SpeechRecognizeContainer {
    public static String dockerPath = "docker";
    private static final Map<String, Integer> containers = new HashMap<>();

    /**
     * 文字起こし用コンテナを立ち上げる
     *
     * @param guild Guild
     * @param member Member
     */
    public static void up(Guild guild, Member member) {
        System.out.println("[" + member.getUser().getAsTag() + "] up");
        User user = member.getUser();
        String roomId = guild.getId() + "-" + user.getId();

        UserCache.set(user);

        // 既にコンテナが立ち上がってる -> 落とす
        if (containers.containsKey(guild.getId())) {
            down(guild, member);
        }

        // コンテナを立ち上げる
        int containerId = getContainerId();

        // 上げる
        try {
            containerUp(containerId, roomId);
        } catch (Exception e) {
            System.out.println("[" + member.getUser().getAsTag() + "] up - failed: " + e.getClass().getName() + " -> " + e.getMessage());
            return;
        }
        containers.put(roomId, containerId);
        System.out.println("[" + member.getUser().getAsTag() + "] up - done");
    }

    /**
     * 文字起こし用コンテナを落とす
     *
     * @param guild Guild
     * @param member Member
     */
    public static void down(Guild guild, Member member) {
        System.out.println("[" + member.getUser().getAsTag() + "] down");
        User user = member.getUser();
        String id = guild.getId() + "-" + user.getId();

        UserCache.set(user);

        if (!containers.containsKey(id)) {
            // 既にコンテナが落ちてる
            System.out.println("[" + member.getUser().getAsTag() + "] down - already down");
            return;
        }
        // 落とす
        int containerId = containers.get(id);

        try {
            containerDown(containerId);
        } catch (Exception e) {
            System.out.println("[" + member.getUser().getAsTag() + "] down - failed: " + e.getClass().getName() + " -> " + e.getMessage());
            return;
        }
        containers.remove(id);
        System.out.println("[" + member.getUser().getAsTag() + "] up - done");
    }

    private static void containerUp(int containerId, String roomId) throws RuntimeException, IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        String[] command = {
            dockerPath,
            "run",
            "-d",
            "--rm",
            "--name",
            "chatwatcher-recognizer-" + containerId,
            "-e",
            "CHATWATCHER_ROOM_ID=" + roomId,
            "-e",
            "DISPLAY=" + (System.getenv("RECOGNIZER_DISPLAY") != null ? System.getenv("RECOGNIZER_DISPLAY") : ":99"),
            "-e",
            "RECOGNIZER_HEADLESS=false",
            "--net=chatwatcher-network",
            "-it",
            "ghcr.io/jaoafa/chatwatcher-recognizer"
        };
        pb.command("/bin/sh", "-c", String.join(" ", command));
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        p.waitFor(30, TimeUnit.SECONDS);
        if (p.exitValue() != 0) {
            throw new RuntimeException("Failed to start container (exit code: " + p.exitValue() + ")");
        }
    }

    private static void containerDown(int containerId) throws RuntimeException, IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        String[] command = {
            dockerPath,
            "stop",
            "chatwatcher-recognizer-" + containerId,
        };
        pb.command(command);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        p.waitFor(30, TimeUnit.SECONDS);
        if (p.exitValue() != 0) {
            throw new RuntimeException("Failed to stop container (exit code: " + p.exitValue() + ")");
        }
    }

    public static void downAll(Guild guild){
        containers.forEach((key, value) -> {
            if(guild != null && !key.startsWith(guild.getId())){
                return;
            }
            try {
                containerDown(value);
            } catch (IOException | InterruptedException e) {
                System.out.println("[SpeechRecognizeContainer] Failed to down container: " + value);
            }
        });
    }

    private static int getContainerId() {
        // コンテナ番号は 0 ～ 100 まで
        int containerId = 0;
        while (containers.containsValue(containerId)) {
            containerId++;
        }
        if(containerId > 100) {
            throw new RuntimeException("Too many containers");
        }
        return containerId;
    }
}
