package org.finos.ai.demo.traderx;
import static org.finos.ai.demo.traderx.TraderXWorkflowService.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
@Component
public class StaleWorkflow implements TraderXWorkflowDefinition {
  public static final String KEY="TraderX_StaleOrderReview";
  private final StalePlanning planning;
  public StaleWorkflow(StalePlanning planning) { this.planning=planning; }

  public String kind() { return "stale"; }
  public String processKey() { return KEY; }
  public String title(Map<String,Object> v) { return "Order review · "+v.get("accountName")+" · "+v.get("batchSize")+" decisions"; }
  public String batchItemsKey() { return "orders"; }
  public Map<String,Object> prepare(JsonNode input) {
    var r=new tools.jackson.databind.ObjectMapper().treeToValue(input,StalePlanning.StaleReview.class);
    return TraderXPlanVariables.of(planning.stale(r,true),"orders");
  }
}
