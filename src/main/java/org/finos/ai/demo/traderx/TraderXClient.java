package org.finos.ai.demo.traderx;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Only fixed TraderX routes are exposed; callers cannot supply arbitrary URLs. */
@Component
public class TraderXClient {
  private final String baseUrl;
  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

  public TraderXClient(@Value("${traderx.base-url:http://localhost:18080}") String baseUrl,
      ObjectMapper json) {
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.json = json;
  }

  public JsonNode accounts() { return get("/account-service/account/"); }
  public JsonNode account(int id) { return get("/account-service/account/" + id); }
  public JsonNode memberships() { return get("/account-service/accountuser/"); }
  public JsonNode securities() { return get("/reference-data/stocks"); }
  public JsonNode security(String ticker) { return get("/reference-data/stocks/" + encode(ticker)); }
  public JsonNode quote(String ticker) { return get("/price-publisher/prices/" + encode(ticker)); }
  public JsonNode people(String search) {
    return get("/people-service/People/GetMatchingPeople?SearchText=" + encode(search) + "&Take=50").path("people");
  }
  public JsonNode person(String username) {
    return get("/people-service/People/GetPerson?LogonId=" + encode(username));
  }
  public JsonNode trades(int accountId) { return get("/position-service/trades/" + accountId); }
  public JsonNode createAccount(String displayName) {
    // Never send an ID: TraderX POST is an upsert if an ID is supplied.
    return post("/account-service/account/", Map.of("displayName", displayName));
  }
  public JsonNode linkOwner(int accountId, String username) {
    return post("/account-service/accountuser/", Map.of("accountId", accountId, "username", username));
  }
  public JsonNode positions(int accountId) { return get("/position-service/positions/" + accountId); }
  public JsonNode orders(int accountId) { return get("/order-matcher/orders?status=open&accountId=" + accountId); }
  public JsonNode cancelOrder(String orderId) { return post("/order-matcher/orders/" + encode(orderId) + "/cancel", Map.of()); }
  public JsonNode order(String orderId) { return get("/order-matcher/orders/" + encode(orderId)); }
  public JsonNode submitTrade(int accountId, String ticker, String side, int quantity, double limitPrice) {
    return post("/order-matcher/orders", Map.of("accountId", accountId,
        "security", ticker, "side", side, "quantity", quantity, "limitPrice", limitPrice));
  }
  private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
  private JsonNode get(String path) { return request("GET", path, null); }
  private JsonNode post(String path, Object body) { return request("POST", path, body); }
  private JsonNode request(String method, String path, Object body) {
    try {
      var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10))
          .header("Accept", "application/json");
      if (body != null) request.header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalStateException("TraderX " + method + " " + path.split("\\?")[0]
            + " returned HTTP " + response.statusCode());
      return json.readTree(response.body());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("TraderX request interrupted; outcome may be unknown", ex);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("TraderX unavailable or timed out; outcome may be unknown", ex);
    }
  }
}
