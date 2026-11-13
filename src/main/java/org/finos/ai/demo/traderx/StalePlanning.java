package org.finos.ai.demo.traderx;
import static org.finos.ai.demo.traderx.TraderXWorkflowService.*;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
@Component
public class StalePlanning extends TraderXPlanningSupport {
  public record OrderAction(String orderId, String action) {}
  public record StaleReview(String requester, String reason, Integer accountId, Integer ageMinutes, List<OrderAction> actions) {}

  public StalePlanning(TraderXClient client,ObjectMapper json) { super(client,json); }
  public Map<String,Object> stale(StaleReview r, boolean submitting) {
    if (r == null) throw bad("Order-review request is required");
    JsonNode account = validateAccount(r.requester(),r.accountId());
    if (r.ageMinutes() == null || r.ageMinutes() < 0 || r.ageMinutes() > 525600)
      throw bad("Order age must be between 0 and 525600 minutes");
    Map<String,JsonNode> candidates = new LinkedHashMap<>();
    List<Map<String,Object>> rows = new ArrayList<>();
    Instant now = Instant.now();
    for (JsonNode order : client.orders(r.accountId())) {
      if (order.path("accountId").asInt() != r.accountId() || !open(order)) continue;
      Instant created;
      try { created = Instant.parse(order.path("createdAt").asText()); }
      catch (RuntimeException ex) { throw bad("TraderX returned an invalid order creation timestamp"); }
      long age = Duration.between(created,now).toMinutes();
      if (age < r.ageMinutes()) continue;
      String id = order.path("orderId").asText(); candidates.put(id,order);
      Map<String,Object> row = new LinkedHashMap<>();
      row.put("order",order); row.put("ageMinutes",age); row.put("action","KEEP");
      try { row.put("quote",client.quote(order.path("security").asText())); }
      catch (RuntimeException ex) { row.put("quoteError","Current quote unavailable; order snapshot remains available"); }
      rows.add(row);
    }
    if (submitting) {
      if (r.actions() == null || r.actions().isEmpty() || r.actions().size() > 50) throw bad("Choose 1–50 orders to review");
      Set<String> ids = new HashSet<>();
      List<Map<String,Object>> selected = new ArrayList<>();
      for (OrderAction action : r.actions()) {
        if (action == null || !Set.of("KEEP","CANCEL").contains(Objects.toString(action.action(),""))) throw bad("Each order needs Keep or Cancel");
        if (!ids.add(action.orderId())) throw bad("Duplicate order selection");
        if (!candidates.containsKey(action.orderId())) throw bad("An order is no longer open, old enough, or in this account; scan again");
        var row = new LinkedHashMap<>(rows.stream().filter(x -> ((JsonNode)x.get("order")).path("orderId").asText().equals(action.orderId())).findFirst().orElseThrow());
        row.put("action",action.action()); selected.add(row);
      }
      rows = selected;
    }
    return Map.of("accountId",r.accountId(),"accountName",account.path("displayName").asText(),
        "ageMinutes",r.ageMinutes(),"orders",rows,"snapshotAt",now.toString());
  }

}
