package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpeechRecognizer extends Thread {
    private final Guild guild;
    private final User user;
    Path path;

    public SpeechRecognizer(Guild guild, User user, Path path) {
        this.guild = guild;
        this.user = user;
        this.path = path;
    }

    @Override
    public void run() {
        String hostname = System.getenv("API_HOST") != null ? System.getenv("API_HOST") : "localhost";
        String port = System.getenv("API_PORT") != null ? System.getenv("API_PORT") : "8080";
        String url = "http://" + hostname + ":" + port + "/recognize";
        Result result;
        try {
            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = RequestBody.create(
                new JSONObject()
                    .put("path", path.toAbsolutePath().toString())
                    .toString(),
                MediaType.parse("application/json; charset=utf-8")
            );
            Request request = new Request.Builder().url(url).post(requestBody).build();
            try (Response response = client.newCall(request).execute()) {
                try (ResponseBody body = response.body()) {
                    Files.deleteIfExists(path);
                    if (body == null) {
                        return;
                    }
                    JSONObject obj = new JSONObject(body.string());
                    if (obj.has("error")) {
                        Utils.println("❌ %s failed to recognize: %s".formatted(user.getAsTag(), obj.getString("error")));
                        return;
                    }
                    result = new Result(obj.getString("text"), obj.has("confidence") ? Math.floor(obj.getDouble("confidence") * 100) : null);
                }
            }

            Utils.println("✅ %s recognized!: %s (%s%%)".formatted(user.getAsTag(), result.text, result.confidence));

            ServerManager.Server server = ServerManager.getServer(guild);
            if (server == null) {
                return;
            }
            for (TextChannel channel : server.getTextChannels()) {
                channel.sendMessage("`%s`: `%s` (%.2f%%)".formatted(user.getAsTag(), result.text, result.confidence)).queue();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    record Result(String text, Double confidence) {
    }
}
