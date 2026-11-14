package org.finos.ai.demo.traderx;
import static org.finos.ai.demo.traderx.TraderXWorkflowService.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
@Component
public class AccountWorkflow implements TraderXWorkflowDefinition {
  public static final String KEY="TraderX_AccountOpening";
  private final TraderXClient client;
  public AccountWorkflow(TraderXClient client) { this.client=client; }
  public record Request(String requester, String reason, Integer accountId, String security,
      String side, Integer quantity, String displayName, String owner, Double limitPrice) {}

  public String kind() { return "account"; }
  public String processKey() { return KEY; }
  public String title(Map<String,Object> v) { return v.get("displayName")+" · "+v.get("owner"); }
  public Map<String,Object> prepare(JsonNode input) {
    var r=new tools.jackson.databind.ObjectMapper().treeToValue(input,Request.class);
    String requester=TraderXWorkflowService.text(r.requester(),"Requester",80);
    client.person(requester);
    Map<String,Object> v=new LinkedHashMap<>();

      String name = text(r.displayName(), "Account name", 100);
      String owner = text(r.owner(), "Account owner", 80);
      client.person(owner);
      requireUniqueName(client, name);
      v.put("displayName", name); v.put("owner", owner);

    return v;
  }
}
