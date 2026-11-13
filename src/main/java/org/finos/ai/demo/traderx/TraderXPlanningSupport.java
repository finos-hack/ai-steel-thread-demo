package org.finos.ai.demo.traderx;
import static org.finos.ai.demo.traderx.TraderXWorkflowService.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
public class TraderXPlanningSupport {
  protected final TraderXClient client;
  protected final ObjectMapper json;
  public TraderXPlanningSupport(TraderXClient client,ObjectMapper json) { this.client=client; this.json=json; }
  JsonNode validateAccount(String requester, Integer accountId) {
    String person = text(requester,"Requester",80); client.person(person);
    if (accountId == null || accountId <= 0) throw bad("Choose an account");
    requireMembership(client,accountId,person);
    return client.account(accountId);
  }
  static boolean open(JsonNode order) {
    return Set.of("NEW","PARTIALLY_FILLED").contains(order.path("status").asText()) && order.path("remainingQuantity").asInt() > 0;
  }
  static void requireNoOpenOrders(TraderXClient client,int accountId) {
    for (JsonNode order : client.orders(accountId)) if (open(order))
      throw bad("This account has open orders. Review or finish them before creating a rebalance basket");
  }
  static Map<String,Integer> holdings(JsonNode positions) {
    Map<String,Integer> holdings = new TreeMap<>();
    for (JsonNode p : positions) {
      double raw = p.path("quantity").asDouble();
      if (!Double.isFinite(raw) || raw < 0 || raw != Math.floor(raw) || raw > Integer.MAX_VALUE)
        throw bad("Rebalancing currently supports whole-share, long-only portfolios");
      if (raw > 0) holdings.merge(p.path("security").asText(),(int)raw,Integer::sum);
    }
    return holdings;
  }
}
