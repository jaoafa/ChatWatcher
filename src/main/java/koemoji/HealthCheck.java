package koemoji;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Docker HEALTHCHECK entry point: exit 0 if /health answers 200, or if the health server is disabled (HEALTH_PORT <= 0). */
public final class HealthCheck {
  public static void main(String[] a) throws Exception {
    System.exit(check(Config.fromEnv().healthPort()));
  }

  /** Exit code for the given HEALTH_PORT; 0 (the health server is disabled) means there is nothing to check. */
  static int check(int port) throws Exception {
    if (port <= 0) return 0;
    var res = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build(), HttpResponse.BodyHandlers.ofString());
    return res.statusCode() == 200 ? 0 : 1;
  }
}
