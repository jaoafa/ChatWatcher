package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SpeechRecognizer extends Thread {
    private final Guild guild;
    private final User user;
    Path path;
    String type;

    public SpeechRecognizer(Guild guild, User user, Path path, String type) {
        this.guild = guild;
        this.user = user;
        this.path = path;
        this.type = type;
    }

    @Override
    public void run() {
        String hostname =
                System.getenv(type.toUpperCase(Locale.ROOT) + "_API_HOST") != null ?
                        System.getenv(type.toUpperCase(Locale.ROOT) + "_API_HOST") :
                        System.getenv("API_HOST") != null ?
                                System.getenv("API_HOST") :
                                "localhost";
        String port =
                System.getenv(type.toUpperCase(Locale.ROOT) + "_API_PORT") != null ?
                        System.getenv(type.toUpperCase(Locale.ROOT) + "_API_PORT") :
                        System.getenv("API_PORT") != null ?
                                System.getenv("API_PORT") :
                                "8080";
        String url = "http://" + hostname + ":" + port + "/recognize-" + type;
        String result;
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .callTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();
            RequestBody requestBody = RequestBody.create(
                    new JSONObject()
                            .put("path", path.toAbsolutePath().toString())
                            .toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );
            Request request = new Request.Builder().url(url).post(requestBody).build();
            try (Response response = client.newCall(request).execute()) {
                try (ResponseBody body = response.body()) {
                    result = body.string();
                }
            }

            Utils.println("✅ %s recognized!: %s".formatted(type, result));

            ServerManager.Server server = ServerManager.getServer(guild);
            if (server == null) {
                return;
            }

            if (isJSONObject(result)) {
                JSONObject obj = new JSONObject(result);
                if (obj.has("text")) {
                    if (obj.getString("text").length() == 0) {
                        return;
                    }
                    for (MessageChannel channel : server.getMessageChannels(this.type)) {
                        channel.sendMessage("`%s`: `%s`".formatted(user.getAsTag(), obj.getString("text").replaceAll(" ", ""))).queue();
                    }
                    return;
                }

                if (obj.has("alternative")) {
                    String googleResultText = parseGoogleResult(obj);
                    if (googleResultText != null) {
                        for (MessageChannel channel : server.getMessageChannels(this.type)) {
                            channel.sendMessage("`%s`: %s".formatted(user.getAsTag(), googleResultText)).queue();
                        }
                    }
                    return;
                }
            }

            if (result.equals("[]")) {
                return;
            }

            for (MessageChannel channel : server.getMessageChannels(this.type)) {
                channel.sendMessage("`%s`: `%s`".formatted(user.getAsTag(), result.replaceAll(" ", ""))).queue();
            }
        } catch (IOException e) {
            Utils.println("⚠️ %s error: %s %s".formatted(type, e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    private boolean isJSONObject(String json) {
        try {
            new JSONObject(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String parseGoogleResult(JSONObject googleResult) {
        if (!googleResult.has("alternative")) {
            return null;
        }

        JSONArray alternative = googleResult.getJSONArray("alternative");
        if (alternative.length() == 0) {
            return null;
        }

        JSONObject bestHypothesis = alternative.getJSONObject(0);
        if (bestHypothesis.has("confidence")) {
            return "`%s` (%s)".formatted(bestHypothesis.getString("transcript"), "%.2f%%".formatted(Math.floor(bestHypothesis.getDouble("confidence") * 100)));
        } else {
            return "`%s`".formatted(bestHypothesis.getString("transcript"));
        }
    }
}
