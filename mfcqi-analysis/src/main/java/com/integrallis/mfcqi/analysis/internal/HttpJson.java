package com.integrallis.mfcqi.analysis.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integrallis.mfcqi.analysis.LLMException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Tiny shared helper for the three HTTP-based LLM providers. */
public final class HttpJson {

  public static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient client;

  public HttpJson(int timeoutSeconds) {
    this.client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
  }

  /** POST a JSON body and return the parsed JSON response. */
  public JsonNode post(String url, Map<String, String> headers, Object body, int timeoutSeconds) {
    try {
      String json = MAPPER.writeValueAsString(body);
      HttpRequest.Builder b =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(timeoutSeconds))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json));
      headers.forEach(b::header);
      HttpResponse<String> response = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new LLMException(
            "HTTP " + response.statusCode() + " from " + url + ": " + response.body());
      }
      return MAPPER.readTree(response.body());
    } catch (IOException e) {
      throw new LLMException("HTTP I/O failure calling " + url, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LLMException("Interrupted calling " + url, e);
    }
  }

  private HttpJson() {
    this.client = HttpClient.newHttpClient();
  }
}
