package org.finos.ai.demo.traderx;
import static org.finos.ai.demo.traderx.TraderXWorkflowService.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
@Component
public class RebalanceWorkflow implements TraderXWorkflowDefinition {
  public static final String KEY="TraderX_PortfolioRebalance";
  private final RebalancePlanning planning;
  public RebalanceWorkflow(RebalancePlanning planning) { this.planning=planning; }

  public String kind() { return "rebalance"; }
  public String processKey() { return KEY; }
  public String title(Map<String,Object> v) { return "Rebalance · "+v.get("accountName")+" · "+v.get("batchSize") + " orders"; }
  public String batchItemsKey() { return "legs"; }
  public Map<String,Object> prepare(JsonNode input) {
    var r=new tools.jackson.databind.ObjectMapper().treeToValue(input,RebalancePlanning.Rebalance.class);
    return TraderXPlanVariables.of(planning.rebalance(r,true),"legs");
  }
}
