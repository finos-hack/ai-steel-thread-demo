package org.finos.ai.demo.traderx;
import static org.finos.ai.demo.traderx.TraderXWorkflowService.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
@Component
public class TradeWorkflow implements TraderXWorkflowDefinition {
  public static final String KEY="TraderX_TradeApproval";
  private final TraderXClient client;
  public TradeWorkflow(TraderXClient client) { this.client=client; }
  public record Request(String requester, String reason, Integer accountId, String security,
      String side, Integer quantity, String displayName, String owner, Double limitPrice) {}

  public String kind() { return "trade"; }
  public String processKey() { return KEY; }
  public String title(Map<String,Object> v) { return v.get("side")+" "+v.get("quantity")+" "+v.get("security")+" · "+v.get("accountName"); }
  public Map<String,Object> prepare(JsonNode input) {
    var r=new tools.jackson.databind.ObjectMapper().treeToValue(input,Request.class);
    String requester=TraderXWorkflowService.text(r.requester(),"Requester",80);
    client.person(requester);
    Map<String,Object> v=new LinkedHashMap<>();

      if (r.accountId() == null || r.accountId() <= 0) throw bad("Choose an account");
      if (r.quantity() == null || r.quantity() < 1 || r.quantity() > 10000)
        throw bad("Quantity must be between 1 and 10000");
      if (!Set.of("Buy", "Sell").contains(Objects.toString(r.side(), ""))) throw bad("Choose Buy or Sell");
      if (r.limitPrice() == null || !Double.isFinite(r.limitPrice()) || r.limitPrice() <= 0 || r.limitPrice() > 1000000)
        throw bad("Limit price must be positive and at most 1000000");
      v.put("limitPrice", r.limitPrice());
      String ticker = ticker(r.security());
      JsonNode account = client.account(r.accountId());
      requireMembership(client, r.accountId(), requester);
      client.security(ticker);
      JsonNode quote = client.quote(ticker);
      if (quote.path("price").asDouble() <= 0) throw bad("A positive market quote is required");
      v.put("accountId", r.accountId());
      v.put("accountName", account.path("displayName").asText());
      v.put("security", ticker); v.put("side", r.side()); v.put("quantity", r.quantity());
      v.put("quotePrice", quote.path("price").asDouble());
      v.put("quoteAsOf", quote.path("asOf").asText());
      v.put("requestReference", "steel-" + UUID.randomUUID());

    return v;
  }
}
