package org.finos.ai.demo.traderx;

import java.time.Instant;
import java.util.*;
import org.finos.fluxnova.bpm.engine.delegate.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ArrayNode;

/** One remote action per async BPMN job: commit its receipt before advancing to the next action. */
public abstract class TraderXBatchExecution implements JavaDelegate {
  protected final TraderXClient client;
  protected final ObjectMapper json;
  public TraderXBatchExecution(TraderXClient client,ObjectMapper json) { this.client = client; this.json = json; }
  @Override public void execute(DelegateExecution e) {
    if (!Boolean.TRUE.equals(e.getVariable("approved")) || e.getVariable("reviewer") == null)
      throw new IllegalStateException("A recorded human approval is required before any TraderX action");
    int index = ((Number)e.getVariable("batchIndex")).intValue();
    int size = ((Number)e.getVariable("batchSize")).intValue();
    if (index >= size) return;
    // Do not let a manual job retry replay a recorded ambiguous write.
    if ("NEEDS_RECONCILIATION".equals(e.getVariable("status"))) return;
    JsonNode plan = json.readTree(TraderXWorkflowService.readJson(e.getVariable("planJson")));
    ArrayNode results = (ArrayNode)json.readTree(TraderXWorkflowService.readJson(e.getVariable("resultsJson")));
    JsonNode item = plan.path(itemsKey()).get(index);
    Map<String,Object> receipt = new LinkedHashMap<>();
    receipt.put("index",index); receipt.put("at",Instant.now().toString());
    receipt.put("instruction",item);
    Attempt attempt=new Attempt();
    e.setVariable("externalSuccess",false);
    try {
      int accountId = plan.path("accountId").asInt();
      client.account(accountId);
      TraderXWorkflowService.requireMembership(client,accountId,(String)e.getVariable("requester"));
      executeItem(e,item,plan,receipt,attempt);
      results.add(json.valueToTree(receipt));
      e.setVariable("resultsJson",TraderXWorkflowService.storeJson(results.toString()));
      e.setVariable("batchIndex",index+1); e.setVariable("externalSuccess",true);
      e.setVariable("status",index+1 == size ? completedStatus() : "EXECUTING");
      e.setVariable("executedAt",Instant.now().toString());
    } catch (RuntimeException ex) {
      receipt.put("outcome",attempt.writeAttempted ? "UNKNOWN" : "BLOCKED");
      receipt.put("error",Objects.toString(ex.getMessage(),"TraderX action failed"));
      results.add(json.valueToTree(receipt)); e.setVariable("resultsJson",TraderXWorkflowService.storeJson(results.toString()));
      e.setVariable("status","NEEDS_RECONCILIATION"); e.setVariable("failedStep",attempt.step);
      e.setVariable("executionError",receipt.get("error")); e.setVariable("failedAt",Instant.now().toString());
    }
  }
  protected static class Attempt { String step="Revalidate account and approved instruction"; boolean writeAttempted; }
  protected abstract String itemsKey();
  protected abstract String completedStatus();
  protected abstract void executeItem(DelegateExecution e, JsonNode item,JsonNode plan,Map<String,Object> receipt,Attempt attempt);

}
