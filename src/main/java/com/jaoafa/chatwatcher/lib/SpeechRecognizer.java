package com.jaoafa.chatwatcher.lib;

import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpeechRecognizer extends Thread {
    Path path;

    public SpeechRecognizer(Path path) {
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
                        System.out.println("[SpeechRecognizer] Error: " + obj.getString("error"));
                        return;
                    }
                    result = new Result(obj.getString("text"), obj.has("confidence") ? Math.floor(obj.getDouble("confidence") * 100) : null);
                }
            }

            System.out.println("[SpeechRecognizer] " + result.text + " (" + result.confidence + ")");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    record Result(String text, Double confidence) {
    }
}
