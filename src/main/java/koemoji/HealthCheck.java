package koemoji;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Docker HEALTHCHECK entry point: exit 0 only if /health answers 200. */
public final class HealthCheck {
  public static void main(String[] a) throws Exception {
    int port = Config.fromEnv().healthPort();
    var res = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build(), HttpResponse.BodyHandlers.ofString());
    System.exit(res.statusCode() == 200 ? 0 : 1);
  }
}
